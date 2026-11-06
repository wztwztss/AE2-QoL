package com.wztwzt.ae2_qof.machindex.server;

import net.minecraft.entity.player.EntityPlayerMP;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 进服自愈：首个玩家登录后延迟几 tick 做一次任务对账。
 *
 * <p>
 * <b>为什么要延迟</b>：BQ 的数据库在世界加载阶段读盘；玩家登录那一刻可能还没读完，
 * 直接写会丢改动。这里延后 {@link #DELAY_TICKS} tick，给 BQ 的 {@code SaveLoadHandler} 留出时间。
 *
 * <p>
 * <b>为什么只做一次</b>：索引内容是静态的（要重扫得用命令），每次登录都全量对账纯属浪费；
 * 但「BQ 清库 / 换存档」需要自愈，所以每次服务端启动后的首个登录仍会跑一次。
 */
public final class JoinRepairHandler {

    /** 延迟 tick 数：足够 BQ 完成读盘，又不至于让玩家等太久。 */
    private static final int DELAY_TICKS = 60;

    private int countdown = -1;
    private boolean doneThisServerRun = false;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        if (doneThisServerRun) return;
        if (event.player.worldObj == null || event.player.worldObj.isRemote) return;
        // 只对第 0 维度（主世界）的第一个玩家触发：BQ 数据库是全局的，多维度重复触发无意义
        if (event.player.worldObj.provider.dimensionId != 0) return;
        countdown = DELAY_TICKS;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (countdown < 0) return;
        if (--countdown > 0) return;
        countdown = -1;
        doneThisServerRun = true;
        try {
            MachIndexServerBridge.repairFromCache("join");
        } catch (Throwable t) {
            // 自愈失败不该影响玩家进服：记日志，下次登录再来
            doneThisServerRun = false;
            MyMod.LOG.error("[AE2QoL] machindex 进服自愈失败（下次登录重试）", t);
        }
    }

    /** 服务端停止时重置（同一 JVM 里重开存档要能再触发）。 */
    public void reset() {
        countdown = -1;
        doneThisServerRun = false;
    }
}
