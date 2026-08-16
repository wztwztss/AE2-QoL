package com.wztwzt.ae2_qof.network;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.glodblock.github.common.item.ItemFluidEncodedPattern;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.util.IInterfaceViewable;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.IInterfaceHost;
import appeng.parts.AEBasePart;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class UploadPatternPacket implements IMessage {

    private long providerId;

    public UploadPatternPacket() {}

    public UploadPatternPacket(long providerId) {
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

    public static class Handler implements IMessageHandler<UploadPatternPacket, IMessage> {

        @Override
        public IMessage onMessage(UploadPatternPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }

            // 归队到服务端 tick 线程执行，避免 Netty IO 线程并发访问 grid/container
            ServerTerminalHelper.scheduleServerTask(() -> handleMessage(player, message));
            return null;
        }

        private void handleMessage(EntityPlayerMP player, UploadPatternPacket message) {
            try {
                Container container = player.openContainer;
                IActionHost terminal = resolveTerminal(container);
                if (terminal == null) {
                    return;
                }

                SlotRestrictedInput outputSlot = resolveOutputSlot(container);
                if (outputSlot == null) {
                    return;
                }

                ItemStack encodedPattern = outputSlot.getStack();
                if (encodedPattern == null || encodedPattern.stackSize <= 0) {
                    return;
                }

                if (!isSupportedPattern(encodedPattern)) {
                    return;
                }

                IGridNode node = terminal.getActionableNode();
                if (node == null) {
                    return;
                }
                IGrid grid = node.getGrid();
                if (grid == null) {
                    return;
                }

                // 所有权校验：无安全站的网络默认放行，有安全站的共享网络仅允许有注入权限的玩家上传
                ISecurityGrid security = grid.getCache(ISecurityGrid.class);
                if (security != null && !security.hasPermission(player, SecurityPermissions.INJECT)) {
                    return;
                }

                ICraftingProvider target = findProvider(grid, message.providerId);
                if (target == null) {
                    return;
                }

                boolean placedInProvider = insertPatternIntoProvider(target, encodedPattern.copy());
                if (placedInProvider) {
                    outputSlot.putStack(null);
                    if (terminal instanceof AEBasePart part) {
                        part.saveChanges();
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        private boolean isSupportedPattern(ItemStack stack) {
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

        private ICraftingProvider findProvider(IGrid grid, long providerId) {
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
                    if (System.identityHashCode(machine) == providerId) {
                        return (ICraftingProvider) machine;
                    }
                }
            }
            return null;
        }

        private boolean insertPatternIntoProvider(ICraftingProvider provider, ItemStack pattern) {
            // 优先使用提供器自带的专属样板槽库存：
            // AE2 接口(IInterfaceHost)、GT 样板输入机(MTEHatchCraftingInputME)、ProgrammableHatches
            // 双口输入仓(PatternDualInputHatch) 都实现 appeng.api.util.IInterfaceViewable，
            // getPatterns() 返回的是样板专用库存；否则回落通用 IInventory 会把样板误投进原料缓存槽。
            if (provider instanceof IInterfaceViewable viewable) {
                IInventory patterns = viewable.getPatterns();
                if (patterns != null) {
                    int availableSlots = viewable.rows() * viewable.rowSize();
                    if (insertIntoPatternInventory(patterns, pattern, availableSlots)) {
                        markProviderDirty(provider);
                        return true;
                    }
                }
                return false;
            }

            if (provider instanceof IInventory inventory) {
                return insertIntoInventory(inventory, pattern);
            }

            return false;
        }

        private void markProviderDirty(ICraftingProvider provider) {
            if (provider instanceof IInterfaceHost host) {
                host.saveChanges();
            } else if (provider instanceof gregtech.api.metatileentity.MetaTileEntity mte) {
                // GT/PH 机器：setInventorySlotContents 已触发网络同步，这里仅标记 tile 以便 NBT 持久化
                mte.markDirty();
            }
        }

        private boolean insertIntoPatternInventory(IInventory patterns, ItemStack pattern, int maxSlots) {
            if (patterns == null) {
                return false;
            }

            int limit = Math.min(maxSlots, patterns.getSizeInventory());
            for (int i = 0; i < limit; i++) {
                ItemStack slot = patterns.getStackInSlot(i);
                if (slot == null || slot.stackSize <= 0) {
                    if (patterns.isItemValidForSlot(i, pattern)) {
                        ItemStack copy = pattern.copy();
                        copy.stackSize = 1;
                        patterns.setInventorySlotContents(i, copy);
                        patterns.markDirty();
                        return true;
                    }
                }
            }

            return false;
        }

        private boolean insertIntoInventory(IInventory inventory, ItemStack pattern) {
            if (inventory == null) {
                return false;
            }

            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack slot = inventory.getStackInSlot(i);
                if (slot == null || slot.stackSize <= 0) {
                    ItemStack copy = pattern.copy();
                    copy.stackSize = 1;
                    inventory.setInventorySlotContents(i, copy);
                    inventory.markDirty();
                    return true;
                }
            }

            return false;
        }
    }
}
