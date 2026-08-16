package com.wztwzt.ae2_qof.network;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.parts.AEBasePart;
import appeng.tile.inventory.IAEStackInventory;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class SwapPatternPacket implements IMessage {

    private List<IAEStack<?>> slotStacks;

    public SwapPatternPacket() {}

    public SwapPatternPacket(List<IAEStack<?>> slotStacks) {
        this.slotStacks = slotStacks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int count = buf.readInt();
            if (count < 0) {
                slotStacks = null;
                return;
            }
            slotStacks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                boolean has = buf.readBoolean();
                if (!has) {
                    slotStacks.add(null);
                    continue;
                }
                int type = buf.readByte();
                switch (type) {
                    case 1:
                        slotStacks.add(
                            AEApi.instance()
                                .storage()
                                .createItemStack(ByteBufUtils.readItemStack(buf)));
                        break;
                    case 2:
                        FluidStack fs = FluidStack.loadFluidStackFromNBT(ByteBufUtils.readTag(buf));
                        if (fs != null) {
                            slotStacks.add(
                                AEApi.instance()
                                    .storage()
                                    .createFluidStack(fs));
                        } else {
                            slotStacks.add(null);
                        }
                        break;
                    default:
                        slotStacks.add(null);
                }
            }
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            slotStacks = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (slotStacks == null) {
            buf.writeInt(-1);
            return;
        }
        buf.writeInt(slotStacks.size());
        for (IAEStack<?> stack : slotStacks) {
            if (stack == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                if (stack instanceof IAEItemStack ais) {
                    buf.writeByte(1);
                    ByteBufUtils.writeItemStack(buf, ais.getItemStack());
                } else if (stack instanceof IAEFluidStack afs) {
                    FluidStack fs = afs.getFluidStack();
                    if (fs != null) {
                        buf.writeByte(2);
                        ByteBufUtils.writeTag(buf, fs.writeToNBT(new NBTTagCompound()));
                    } else {
                        buf.writeByte(0);
                    }
                } else {
                    buf.writeByte(0);
                }
            }
        }
    }

    public static class Handler implements IMessageHandler<SwapPatternPacket, IMessage> {

        @Override
        public IMessage onMessage(SwapPatternPacket message, MessageContext ctx) {
            if (ctx.side == cpw.mods.fml.relauncher.Side.SERVER) {
                return handleServer(message, ctx);
            }
            return handleClient(message);
        }

        private IMessage handleServer(SwapPatternPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // 归队到服务端 tick 线程执行，避免 Netty IO 线程并发访问 container/inventory
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    SwapPatternPacket result = doServerSwap(player);
                    if (result != null) {
                        ModNetwork.CHANNEL.sendTo(result, player);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
            return null;
        }

        private SwapPatternPacket doServerSwap(EntityPlayerMP player) {
            Container container = player.openContainer;
            IAEStackInventory outputs = resolveOutputs(container);
            if (outputs == null) {
                System.out.println("[APU] Swap: outputs is null on server");
                return null;
            }

            int size = outputs.getSizeInventory();
            if (size < 2) return null;

            ArrayList<Integer> nonEmptyIndices = new ArrayList<>();
            ArrayList<IAEStack<?>> nonEmptyStacks = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                IAEStack<?> stack = outputs.getAEStackInSlot(i);
                if (stack != null) {
                    nonEmptyIndices.add(i);
                    nonEmptyStacks.add(stack);
                }
            }

            if (nonEmptyStacks.size() < 2) return null;

            IAEStack<?> first = nonEmptyStacks.get(0);
            for (int i = 0; i < nonEmptyStacks.size() - 1; i++) {
                nonEmptyStacks.set(i, nonEmptyStacks.get(i + 1));
            }
            nonEmptyStacks.set(nonEmptyStacks.size() - 1, first);

            for (int i = 0; i < nonEmptyIndices.size(); i++) {
                outputs.putAEStackInSlot(nonEmptyIndices.get(i), nonEmptyStacks.get(i));
            }
            outputs.markDirty();

            // 持久化到终端，避免输出槽显示与真实内容不同步
            try {
                IActionHost host = resolveTerminal(container);
                if (host instanceof AEBasePart part) {
                    part.saveChanges();
                }
            } catch (Throwable ignored) {}

            List<IAEStack<?>> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                result.add(outputs.getAEStackInSlot(i));
            }
            System.out.println("[APU] Swap: sending " + size + " slots back, nonEmpty=" + nonEmptyIndices.size());
            return new SwapPatternPacket(result);
        }

        private IMessage handleClient(SwapPatternPacket message) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) {
                System.out.println("[APU] Swap C: minecraft is null");
                return null;
            }
            // 归队到客户端主线程执行，避免 Netty IO 线程操作容器
            mc.func_152344_a(() -> {
                try {
                    applyClientSwap(message);
                } catch (Throwable e) {
                    System.out.println(
                        "[APU] Swap C: ERROR " + e.getClass()
                            .getSimpleName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
            return null;
        }

        private void applyClientSwap(SwapPatternPacket message) {
            if (message.slotStacks == null) {
                System.out.println("[APU] Swap C: slotStacks is null");
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) {
                System.out.println("[APU] Swap C: thePlayer is null");
                return;
            }
            Container container = mc.thePlayer.openContainer;
            if (container == null) {
                System.out.println("[APU] Swap C: container is null");
                return;
            }

            System.out.println(
                "[APU] Swap C: received " + message.slotStacks.size()
                    + " slots, container="
                    + container.getClass()
                        .getSimpleName());

            IAEStack<?>[] outputSlots = null;
            IAEStackInventory clientOutputs = null;

            if (container instanceof ContainerPatternTerm pt) {
                outputSlots = pt.outputSlotsClient;
                try {
                    Field outputsField = ContainerPatternTerm.class.getDeclaredField("outputs");
                    outputsField.setAccessible(true);
                    clientOutputs = (IAEStackInventory) outputsField.get(pt);
                } catch (Throwable ignored) {}
            } else if (container instanceof ContainerPatternTermEx pte) {
                outputSlots = pte.outputSlotsClient;
                try {
                    Field outputsField = ContainerPatternTermEx.class.getDeclaredField("outputs");
                    outputsField.setAccessible(true);
                    clientOutputs = (IAEStackInventory) outputsField.get(pte);
                } catch (Throwable ignored) {}
            }

            if (outputSlots == null) {
                System.out.println("[APU] Swap C: outputSlotsClient is null");
                return;
            }

            for (int i = 0; i < Math.min(message.slotStacks.size(), outputSlots.length); i++) {
                IAEStack<?> aeStack = message.slotStacks.get(i);
                outputSlots[i] = aeStack;
                if (clientOutputs != null) {
                    clientOutputs.putAEStackInSlot(i, aeStack);
                }
            }

            System.out.println(
                "[APU] Swap C: updated " + message.slotStacks.size()
                    + " slots, clientOutputs="
                    + (clientOutputs != null));
        }

        private IActionHost resolveTerminal(Container container) {
            try {
                if (container instanceof ContainerPatternTerm pt) {
                    return (IActionHost) pt.getPatternTerminal();
                }
                if (container instanceof ContainerPatternTermEx pte) {
                    return (IActionHost) pte.getPatternTerminal();
                }
            } catch (Exception e) {
                System.out.println("[APU] Swap resolveTerminal FAILED: " + e.getMessage());
            }
            return null;
        }

        private IAEStackInventory resolveOutputs(Container container) {
            try {
                if (container instanceof ContainerPatternTerm pt) {
                    Field field = ContainerPatternTerm.class.getDeclaredField("outputs");
                    field.setAccessible(true);
                    return (IAEStackInventory) field.get(pt);
                }
                if (container instanceof ContainerPatternTermEx pte) {
                    Field field = ContainerPatternTermEx.class.getDeclaredField("outputs");
                    field.setAccessible(true);
                    return (IAEStackInventory) field.get(pte);
                }
            } catch (Exception e) {
                System.out.println("[APU] Swap resolveOutputs FAILED: " + e.getMessage());
            }
            return null;
        }
    }
}
