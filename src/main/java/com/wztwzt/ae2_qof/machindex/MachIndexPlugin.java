package com.wztwzt.ae2_qof.machindex;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.machindex.common.IndexConfig;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * machindex 总开关：负责「什么时候注册事件 / 什么时候触发扫描」。
 *
 * <p>
 * <b>隔离纪律（硬性）</b>：
 * <ul>
 * <li>{@code codechicken.nei.*} 只出现在 {@code machindex/client/NeiScanService} 与
 * 本类内部的事件监听（{@code @SideOnly(CLIENT)}，且只在 {@link #NEI_MODID} 守卫通过后才注册）；</li>
 * <li>{@code betterquesting.*} / {@code bq_standard.*} 只出现在 {@code machindex/server/BqQuestWriter}，
 * 同样守卫 {@link #BQ_MODID}。</li>
 * </ul>
 * 未装 NEI → 扫描禁用（服务端仍可按旧缓存对账）；未装 BQ → 整个写库禁用。
 * <b>两条守卫的跳过分支都必须留日志</b>——否则「功能不生效」与「守卫判错」在日志里无法区分
 * （本项目的 2026-09-26 教训）。
 */
public final class MachIndexPlugin {

    /** NEI 的真实 modid（不是包名）。 */
    public static final String NEI_MODID = "NotEnoughItems";

    /** BetterQuesting 的真实 modid。 */
    public static final String BQ_MODID = "betterquesting";

    private MachIndexPlugin() {}

    /** 是否装 NEI。 */
    public static boolean hasNei() {
        try {
            return Loader.isModLoaded(NEI_MODID);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 是否装 BQ（modid 判定 + 关键类可解析，两道判据）。 */
    public static boolean hasBq() {
        try {
            if (!Loader.isModLoaded(BQ_MODID)) return false;
            Class.forName("betterquesting.questing.QuestDatabase", false, MachIndexPlugin.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 初始化（init 阶段调用一次）。
     *
     * @param side 当前侧（客户端才注册 NEI 事件订阅）
     */
    public static void init(Side side) {
        if (!IndexConfig.enabled) {
            MyMod.LOG.info("[AE2QoL] machindex 已禁用（配置 enabled=false），不注册任何事件/命令");
            return;
        }
        MyMod.LOG.info("[AE2QoL] machindex 初始化：NEI=" + hasNei() + ", BetterQuesting=" + hasBq() + ", side=" + side);

        if (side == Side.CLIENT) {
            if (hasNei()) {
                registerClientNeiHook();
            } else {
                MyMod.LOG.info("[AE2QoL] machindex 未检测到 NEI（modid=" + NEI_MODID + "），扫描功能不可用");
            }
        }
        if (!hasBq()) {
            MyMod.LOG.info("[AE2QoL] machindex 未检测到 BetterQuesting（modid=" + BQ_MODID + "），任务写入不可用（扫描与缓存仍可工作）");
        }
    }

    /**
     * 注册 NEI 事件监听。
     *
     * <p>
     * 这里刻意把监听写成**匿名内部类**而不是具名类：具名类会被同包的其它代码提前解析，
     * 而它的 {@code @SubscribeEvent} 方法参数是 NEI 的类 —— 未装 NEI 的环境下加载即炸。
     * 匿名类属于本方法体，只在守卫通过后才被引用，隔离最彻底。
     */
    @SideOnly(Side.CLIENT)
    private static void registerClientNeiHook() {
        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new NeiConfigsLoadedListener());
            MyMod.LOG.info("[AE2QoL] machindex 已注册 NEIConfigsLoadedEvent 监听（进世界后自动扫描）");
        } catch (Throwable t) {
            MyMod.LOG.error("[AE2QoL] machindex 注册 NEI 监听失败", t);
        }
    }

    /** 供命令/诊断展示状态。 */
    public static String describeAvailability() {
        return "NEI=" + hasNei() + ", BQ=" + hasBq() + ", enabled=" + IndexConfig.enabled;
    }

    /**
     * NEI 配置加载完成事件监听（客户端）。
     *
     * <p>
     * <b>线程</b>：该事件由 NEI 的加载线程 post（{@code NEIClientConfig$25 extends Thread}），
     * 所以这里**不能直接扫**，必须经 {@code ScanPipeline} 切回主线程。
     */
    @SideOnly(Side.CLIENT)
    public static final class NeiConfigsLoadedListener {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void onNeiConfigsLoaded(codechicken.nei.event.NEIConfigsLoadedEvent event) {
            try {
                MyMod.LOG.info(
                    "[AE2QoL] machindex 收到 NEIConfigsLoadedEvent（线程=" + Thread.currentThread()
                        .getName() + "）→ 调度主线程扫描");
                com.wztwzt.ae2_qof.machindex.client.ScanPipeline.triggerScan(false, false);
            } catch (Throwable t) {
                MyMod.LOG.error("[AE2QoL] machindex NEI 事件处理异常", t);
            }
        }
    }
}
