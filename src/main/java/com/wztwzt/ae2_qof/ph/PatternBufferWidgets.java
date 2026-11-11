package com.wztwzt.ae2_qof.ph;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 样板总成 GUI 的按钮工厂（4.5.0-fix18「拆除」）。
 *
 * <p>为什么与 {@link PatternWindowWidgets} 分开：那个类照抄 PH 的内部类，注释里写明了
 * 「只用 MUI2 公开 API，PH 升级时复核一次」；拆除是纯本模组功能，不该混进那份复制品里，
 * 免得以后核对 PH API 时被本模组逻辑干扰。
 */
public final class PatternBufferWidgets {

    private PatternBufferWidgets() {}

    /** 待确认拆除：UUID → 超时时刻（世界时间 ms）。10 秒内再点一次才真拆。 */
    private static final Map<UUID, Long> PENDING_DISMANTLE = new ConcurrentHashMap<UUID, Long>();

    private static final long CONFIRM_WINDOW_MS = 10_000L;

    /**
     * 拆除按钮的同步 handler：**只在服务端执行**（{@code syncManager.isClient()} 守卫，
     * 与 {@code PatternClipboardGui} 的清空按钮同一套写法）。
     *
     * <p>二次确认：样板总成里可能躺着上百个样板（MK.IV 360 格），误点代价高。第一次点击
     * 只发一条黄字提示并记 10 秒；10 秒内再点才执行。超时或已拆则重新计时。
     */
    public static InteractionSyncHandler makeDismantleHandler(PanelSyncManager syncManager, String key,
        java.util.function.Supplier<IGregTechTileEntity> teSupplier) {
        final InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(data -> {
            if (syncManager.isClient()) return;

            final EntityPlayer player = syncManager.getPlayer();
            if (player == null) return;

            final IGregTechTileEntity te = teSupplier.get();
            if (!(te instanceof net.minecraft.tileentity.TileEntity tile) || tile.isInvalid()) return;

            final UUID id = player.getUniqueID();
            final long now = System.currentTimeMillis();
            final Long pending = PENDING_DISMANTLE.get(id);

            if (pending == null || now > pending.longValue()) {
                PENDING_DISMANTLE.put(id, Long.valueOf(now + CONFIRM_WINDOW_MS));
                player.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                            "ae2_qof.gui.pattern_buffer.dismantle.confirm")));
                return;
            }

            PENDING_DISMANTLE.remove(id);
            // 线程安全：MUI2 的 C2S 回调（PacketSyncHandler.executeServer → ReceiveWidgetUpdate →
            // readOnServer）在 **Netty 线程**上执行，而拆除要做 setBlockToAir / removeTileEntity /
            // 写玩家背包 —— 这些都是主线程独占操作（本项目 2026-09 踩过同类坑：AE2 包处理器在
            // 网络线程里改容器无效）。PH 自己的 refund 按钮没归队（只动 NBT + spawn 实体，
            // 影响面小），我们动的是方块级生命周期，按仓库既有先例
            // （ServerTerminalHelper.scheduleServerTask → GTNHLib ServerThreadUtil）归队主线程。
            // 归队后必须重新校验 TE：排队的这几个 tick 里玩家可能已经手动拆掉/破坏这台机器。
            final int tx = tile.xCoord, ty = tile.yCoord, tz = tile.zCoord;
            final int dim = player.dimension;
            com.gtnewhorizon.gtnhlib.util.ServerThreadUtil.addScheduledTask(() -> {
                if (player.worldObj == null || player.worldObj.provider.dimensionId != dim) return;
                final net.minecraft.tileentity.TileEntity target = player.worldObj.getTileEntity(tx, ty, tz);
                if (!(target instanceof IGregTechTileEntity) || target.isInvalid()) return;
                PatternBufferDismantle.dismantle(player, target);
            });
        });
        syncManager.syncValue(key, handler);
        return handler;
    }
}
