package com.wztwzt.ae2_qof.network;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.glodblock.github.common.item.ItemFluidEncodedPattern;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.IInterfaceHost;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class RecallPatternPacket implements IMessage {

    private long providerId;

    public RecallPatternPacket() {}

    public RecallPatternPacket(long providerId) {
        this.providerId = providerId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.providerId = buf.readLong();
        } catch (Throwable t) {
            this.providerId = 0;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.providerId);
    }

    public static class Handler implements IMessageHandler<RecallPatternPacket, IMessage> {

        @Override
        public IMessage onMessage(RecallPatternPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }

            Container container = player.openContainer;
            SlotRestrictedInput outputSlot = resolveOutputSlot(container);
            if (outputSlot == null) {
                System.out.println("[APU] Recall: outputSlot is null");
                return null;
            }

            // 只能在输出槽为空时撤回
            if (outputSlot.getStack() != null && outputSlot.getStack().stackSize > 0) {
                System.out.println("[APU] Recall: outputSlot not empty, stack=" + outputSlot.getStack());
                return null;
            }

            try {
                IActionHost terminal = resolveTerminal(container);
                if (terminal == null) {
                    System.out.println("[APU] Recall: terminal is null");
                    return null;
                }

                IGridNode node = terminal.getActionableNode();
                if (node == null) {
                    System.out.println("[APU] Recall: node is null");
                    return null;
                }

                IGrid grid = node.getGrid();
                if (grid == null) {
                    System.out.println("[APU] Recall: grid is null");
                    return null;
                }

                System.out.println("[APU] Recall: searching providerId=" + message.providerId);
                IInventory provider = findProviderInventory(grid, message.providerId);
                if (provider == null) {
                    System.out.println("[APU] Recall: provider not found for id=" + message.providerId);
                    return null;
                }
                System.out.println("[APU] Recall: provider found, size=" + provider.getSizeInventory());

                // 从后往前搜索，找到最后一个编码样板
                ItemStack recalled = null;
                for (int i = provider.getSizeInventory() - 1; i >= 0; i--) {
                    ItemStack slot = provider.getStackInSlot(i);
                    if (slot != null && slot.stackSize > 0 && isEncodedPattern(slot)) {
                        recalled = slot.copy();
                        provider.setInventorySlotContents(i, null);
                        provider.markDirty();
                        System.out.println("[APU] Recall: found pattern at slot " + i + ": " + slot.getDisplayName());
                        break;
                    }
                }

                if (recalled != null) {
                    // 清除 apu:recipeMap，避免重新编码后继承旧配方池
                    if (recalled.getTagCompound() != null) {
                        recalled.getTagCompound()
                            .removeTag("apu:recipeMap");
                    }
                    outputSlot.putStack(recalled);
                    System.out.println("[APU] Recall: success, placed in output slot");
                } else {
                    System.out.println("[APU] Recall: no encoded pattern found in provider");
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

            return null;
        }

        private IInventory findProviderInventory(IGrid grid, long providerId) {
            for (Class<? extends IGridHost> hostClass : grid.getMachinesClasses()) {
                if (!ICraftingProvider.class.isAssignableFrom(hostClass)) {
                    continue;
                }
                IMachineSet machines = grid.getMachines(hostClass);
                if (machines == null) {
                    continue;
                }
                for (IGridNode machineNode : machines) {
                    if (machineNode == null) {
                        continue;
                    }
                    Object machine = machineNode.getMachine();
                    if (!(machine instanceof ICraftingProvider)) {
                        continue;
                    }
                    if (System.identityHashCode(machine) != providerId) {
                        continue;
                    }
                    if (machine instanceof IInterfaceHost) {
                        IInventory patterns = ((IInterfaceHost) machine).getPatterns();
                        if (patterns != null) {
                            return patterns;
                        }
                    }
                    if (machine instanceof IInventory) {
                        return (IInventory) machine;
                    }
                }
            }
            return null;
        }

        private boolean isEncodedPattern(ItemStack stack) {
            if (stack == null) {
                return false;
            }
            if (AEApi.instance()
                .definitions()
                .items()
                .encodedPattern()
                .isSameAs(stack)) {
                return true;
            }
            if (AEApi.instance()
                .definitions()
                .items()
                .encodedUltimatePattern()
                .isSameAs(stack)) {
                return true;
            }
            return stack.getItem() instanceof ItemFluidEncodedPattern;
        }

        private IActionHost resolveTerminal(Container container) {
            if (container instanceof ContainerPatternTerm term) {
                return (IActionHost) term.getPatternTerminal();
            }
            if (container instanceof ContainerPatternTermEx termEx) {
                return (IActionHost) termEx.getPatternTerminal();
            }
            return null;
        }

        private SlotRestrictedInput resolveOutputSlot(Container container) {
            try {
                if (container instanceof ContainerPatternTerm term) {
                    Field field = ContainerPatternTerm.class.getDeclaredField("patternSlotOUT");
                    field.setAccessible(true);
                    return (SlotRestrictedInput) field.get(term);
                }
                if (container instanceof ContainerPatternTermEx termEx) {
                    Field field = ContainerPatternTermEx.class.getDeclaredField("patternSlotOUT");
                    field.setAccessible(true);
                    return (SlotRestrictedInput) field.get(termEx);
                }
            } catch (Exception ignored) {}
            return null;
        }
    }
}
