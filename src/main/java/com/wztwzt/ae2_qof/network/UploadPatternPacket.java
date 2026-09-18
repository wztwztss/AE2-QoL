package com.wztwzt.ae2_qof.network;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.glodblock.github.common.item.ItemFluidEncodedPattern;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.util.ProviderLocator;

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
    /** 目标供应器的稳定位置标识（fix41），形如 D0:-432,63,-2791；为空时退化为只按 ID 匹配。 */
    private String locationKey;

    public UploadPatternPacket() {}

    public UploadPatternPacket(long providerId) {
        this(providerId, null);
    }

    public UploadPatternPacket(long providerId, String locationKey) {
        this.providerId = providerId;
        this.locationKey = locationKey;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.providerId = buf.readLong();
            boolean hasKey = buf.readBoolean();
            this.locationKey = hasKey ? readString(buf) : null;
        } catch (Throwable t) {
            this.providerId = 0;
            this.locationKey = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.providerId);
        boolean hasKey = this.locationKey != null && !this.locationKey.isEmpty();
        buf.writeBoolean(hasKey);
        if (hasKey) {
            writeString(buf, this.locationKey);
        }
    }

    private void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private String readString(ByteBuf buf) {
        int len = buf.readShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
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

                net.minecraft.inventory.Slot outputSlot = resolveOutputSlot(container);
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
                    notify(player, "ae2_qof.info.upload_no_permission");
                    return;
                }

                // fix41：优先按稳定位置（维度+坐标）定位，内存地址 ID 只作兜底；
                // 区块重载 / 服务器重启后 ID 会失效，坐标不会，避免「点了没反应」。
                ICraftingProvider target = com.wztwzt.ae2_qof.util.ProviderLocator.find(grid,
                    message.locationKey, message.providerId);
                if (target == null) {
                    MyMod.LOG.info(
                        "[Upload] server: provider not found in grid, id={}, key={}",
                        message.providerId,
                        message.locationKey);
                    notify(player, "ae2_qof.info.upload_target_missing");
                    return;
                }

                boolean placedInProvider = insertPatternIntoProvider(target, encodedPattern.copy());
                if (!placedInProvider) {
                    // fix41：写入失败不再静默，明确告诉玩家原因（槽满 / 该机不收这类样板）
                    MyMod.LOG.info(
                        "[Upload] server: insert rejected by {}",
                        target.getClass()
                            .getSimpleName());
                    notify(player, "ae2_qof.info.upload_rejected");
                }
                if (placedInProvider) {
                    MyMod.LOG.info(
                        "[Upload] pattern inserted into provider {}",
                        target.getClass()
                            .getSimpleName());
                    outputSlot.putStack(null);
                    // 外部写入样板不会触发接口终端的增量推送，调度打开中的合并终端容器全量刷新，
                    // 否则列表要重开 GUI 才能看到新样板
                    if (container instanceof com.wztwzt.ae2_qof.merged.ContainerMergedTerminal cmt) {
                        cmt.scheduleFullUpdate();
                    }
                    if (terminal instanceof AEBasePart part) {
                        part.saveChanges();
                    }
                }
            } catch (Throwable t) {
                MyMod.LOG.error("Upload pattern failed", t);
            }
        }

        /** fix41：把失败原因回执给客户端，避免玩家只看到「点了没反应」。 */
        private void notify(EntityPlayerMP player, String messageKey) {
            try {
                ModNetwork.CHANNEL.sendTo(new UploadFeedbackPacket(messageKey), player);
            } catch (Throwable ignored) {}
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
            // 统一解析：原生样板终端 / 扩展样板终端 / 二合一接口终端（反射 anchor）
            return com.wztwzt.ae2_qof.util.ContainerTerminalResolver.resolveTerminal(container);
        }

        private net.minecraft.inventory.Slot resolveOutputSlot(Container container) {
            if (container instanceof com.wztwzt.ae2_qof.api.IMergedPatternTerminal merged) {
                return merged.getMergedEncodedSlot();
            }
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

            // 未实现 IInterfaceViewable 的提供器（未适配的非常规/未来机器）一律拒绝写入原始
            // IInventory，否则样板会被误投进原料缓存槽（#27 回归）。确需支持时再显式白名单。
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
    }
}
