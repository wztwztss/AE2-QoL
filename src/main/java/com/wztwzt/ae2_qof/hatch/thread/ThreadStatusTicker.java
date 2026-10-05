package com.wztwzt.ae2_qof.hatch.thread;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 3.25.0-fix4（G6）：**由中央 server tick 驱动线程状态的降频广播**。
 *
 * <h2>为什么不能挂在机器 tick 上</h2>
 * 用户环境里的加速机制（NH-Utilities 的时间之瓶/加速火把）对普通 GT 多方块走的是"快路径"：
 * 直接把 {@code multiBlockBase.mProgresstime} 往前跳并 {@code return true} ⇒ **跳过该 TE 的 updateEntity**。
 * 我们原先把广播写在 {@code incrementProgressTime}（机器 tick 内）里，于是加速期间广播会稀疏甚至停发，
 * WAILA 那行就不稳/不出现。
 *
 * <p>现在改由这里统一驱动：每 {@link #INTERVAL} tick 遍历一次引擎并发包，与机器是否被加速无关。
 * 只对有引擎且已启用的机器发（{@link ThreadStatusBroadcaster#broadcastAll()} 内部再判一次）。
 */
public final class ThreadStatusTicker {

    /** 与广播器一致：每 10 tick 发一次（≈0.5 秒，WAILA 观感足够）。 */
    private static final int INTERVAL = ThreadStatusBroadcaster.INTERVAL;

    private int tickCounter;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter % INTERVAL != 0) return;
        try {
            ThreadStatusBroadcaster.broadcastAll();
        } catch (Throwable t) {
            // 诊断链路的异常不该影响主流程，但也绝不静默
            com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 线程状态广播（中央 tick）失败", t);
        }
    }
}
