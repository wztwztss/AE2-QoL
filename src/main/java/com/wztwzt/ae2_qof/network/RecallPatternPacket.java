package com.wztwzt.ae2_qof.network;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.glodblock.github.common.item.ItemFluidEncodedPattern;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;
import com.wztwzt.ae2_qof.util.ContainerTerminalResolver;

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
import appeng.container.slot.SlotRestrictedInput;
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
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }

            // 归队到服务端 tick 线程执行，避免 Netty IO 线程并发访问 grid/container
            ServerTerminalHelper.scheduleServerTask(() -> handleMessage(player, message));
            return null;
        }

        private void handleMessage(EntityPlayerMP player, RecallPatternPacket message) {
            try {
                Container container = player.openContainer;
                Slot outputSlot = resolveOutputSlot(container);
                if (outputSlot == null) {
                    MyMod.LOG.info(
                        "[Recall] no output slot for {}",
                        container != null ? container.getClass()
                            .getSimpleName() : "null container");
                    return;
                }

                // 只能在输出槽为空时撤回
                if (outputSlot.getStack() != null && outputSlot.getStack().stackSize > 0) {
                    MyMod.LOG.info("[Recall] output slot occupied, abort");
                    return;
                }

                IActionHost terminal = ContainerTerminalResolver.resolveTerminal(container);
                if (terminal == null) {
                    MyMod.LOG.info(
                        "[Recall] terminal resolve failed for {}",
                        container != null ? container.getClass()
                            .getSimpleName() : "null container");
                    return;
                }

                IGridNode node = terminal.getActionableNode();
                if (node == null) {
                    MyMod.LOG.info("[Recall] terminal node is null");
                    return;
                }

                IGrid grid = node.getGrid();
                if (grid == null) {
                    MyMod.LOG.info("[Recall] grid is null");
                    return;
                }

                // 所有权校验：无安全站的网络默认放行，有安全站的共享网络仅允许有对应权限的玩家操作
                ISecurityGrid security = grid.getCache(ISecurityGrid.class);
                if (security != null && !security.hasPermission(player, SecurityPermissions.EXTRACT)) {
                    MyMod.LOG.info("[Recall] no EXTRACT permission, denied");
                    return;
                }

                ICraftingProvider machine = findProvider(grid, message.providerId);
                if (machine == null) {
                    MyMod.LOG.info("[Recall] provider id={} not found in grid", message.providerId);
                    return;
                }
                IInventory provider = resolvePatternInventory(machine);
                if (provider == null) {
                    MyMod.LOG.info("[Recall] provider has no pattern inventory");
                    return;
                }
                // 只扫描专属样板槽区域（IInterfaceViewable 提供 rows*rowSize 上界），
                // 避免把 GT/PH 机器原料缓存误当作样板库存
                int scanLimit = resolvePatternLimit(machine, provider);

                // 从后往前搜索，找到最后一个编码样板
                ItemStack recalled = null;
                for (int i = scanLimit - 1; i >= 0; i--) {
                    ItemStack slot = provider.getStackInSlot(i);
                    if (slot != null && slot.stackSize > 0 && isEncodedPattern(slot)) {
                        recalled = slot.copy();
                        provider.setInventorySlotContents(i, null);
                        provider.markDirty();
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
                    // 外部取走样板不会触发接口终端增量推送，调度打开中的合并终端容器全量刷新
                    if (container instanceof com.wztwzt.ae2_qof.merged.ContainerMergedTerminal cmt) {
                        cmt.scheduleFullUpdate();
                    }
                    MyMod.LOG.info("[Recall] success from provider id={}", message.providerId);
                } else {
                    MyMod.LOG.info("[Recall] no encoded pattern in provider id={}", message.providerId);
                }
            } catch (Throwable t) {
                MyMod.LOG.error("Recall pattern failed", t);
            }
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

        private IInventory resolvePatternInventory(ICraftingProvider provider) {
            // 与 UploadPatternPacket 一致：优先取专属样板槽库存（IInterfaceViewable.getPatterns()）
            if (provider instanceof IInterfaceViewable viewable) {
                return viewable.getPatterns();
            }
            if (provider instanceof IInventory inv) {
                return inv;
            }
            return null;
        }

        private int resolvePatternLimit(ICraftingProvider provider, IInventory patterns) {
            if (provider instanceof IInterfaceViewable viewable) {
                return Math.min(viewable.rows() * viewable.rowSize(), patterns.getSizeInventory());
            }
            return patterns.getSizeInventory();
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

        private SlotRestrictedInput resolvePatternTermOutputSlot(Container container) {
            try {
                if (container instanceof appeng.container.implementations.ContainerPatternTerm term) {
                    Field field = appeng.container.implementations.ContainerPatternTerm.class
                        .getDeclaredField("patternSlotOUT");
                    field.setAccessible(true);
                    return (SlotRestrictedInput) field.get(term);
                }
                if (container instanceof appeng.container.implementations.ContainerPatternTermEx termEx) {
                    Field field = appeng.container.implementations.ContainerPatternTermEx.class
                        .getDeclaredField("patternSlotOUT");
                    field.setAccessible(true);
                    return (SlotRestrictedInput) field.get(termEx);
                }
            } catch (Exception ignored) {}
            return null;
        }

        private Slot resolveOutputSlot(Container container) {
            if (container instanceof IMergedPatternTerminal merged) {
                return merged.getMergedEncodedSlot();
            }
            return resolvePatternTermOutputSlot(container);
        }
    }
}
