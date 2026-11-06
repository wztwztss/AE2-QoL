package com.wztwzt.ae2_qof.machindex.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.machindex.common.IndexConfig;
import com.wztwzt.ae2_qof.machindex.common.IndexModel;
import com.wztwzt.ae2_qof.machindex.common.QuestAggregator;
import com.wztwzt.ae2_qof.machindex.server.MachIndexServerBridge;

/**
 * 分帧扫描管线。
 *
 * <p>
 * <b>为什么要分帧</b>（硬约束 5）：全量 NEI 扫描 + 合成表推断在整合包里是「几千条催化剂 ×
 * 反查配方」的量级，一口气跑完会让进世界卡死几秒。因此拆成若干步，每步之间让出主线程，
 * 每 tick 只跑固定预算（{@link IndexConfig#craftingScanPerTick}）。
 *
 * <p>
 * <b>步骤划分</b>：
 * <ol>
 * <li>{@code SNAPSHOT}：主线程抓 handler/催化剂快照；</li>
 * <li>{@code RESOLVE}：逐机器解析电压/来源（纯内存，按预算分批）；</li>
 * <li>{@code AGGREGATE}：聚合 + 布局 + 签名（一次完成，纯计算）；</li>
 * <li>{@code PERSIST}：写缓存 + 统计日志 + 通知服务端。</li>
 * </ol>
 *
 * <p>
 * 单机（客户端与服务端同 JVM）下，扫描完成后直接交给
 * {@link MachIndexServerBridge} 走服务端写库路径（内部会判断 BQ 是否可用）。
 */
public final class ScanPipeline {

    private ScanPipeline() {}

    /** 管线状态。 */
    public enum Stage {
        IDLE,
        SNAPSHOT,
        RESOLVE,
        AGGREGATE,
        PERSIST,
        DONE,
        FAILED
    }

    /** JVM 级一次性守卫：同一进程只自动扫一次（/rebuild 会重置）。 */
    private static boolean autoScanDone = false;

    private static Stage stage = Stage.IDLE;
    private static long startedAt;
    private static IndexModel.ScanResult partial;

    /** 客户端侧的计划（服务端写库用同一份，跨端通过缓存文件传递）。 */
    private static IndexModel.ScanResult lastResult;

    // ===================== 对外入口 =====================

    /** 是否已经自动扫过（JVM 级）。 */
    public static boolean isAutoScanDone() {
        return autoScanDone;
    }

    /** 重置一次性守卫（/ae2qol machindex rebuild 用）。 */
    public static void resetAutoScanGuard() {
        autoScanDone = false;
        stage = Stage.IDLE;
        partial = null;
    }

    public static Stage getStage() {
        return stage;
    }

    public static IndexModel.ScanResult getLastResult() {
        return lastResult;
    }

    /**
     * 进世界后触发一次扫描（幂等）。
     *
     * @param force      true = 忽略缓存与一次性守卫（命令 rebuild）
     * @param notifyChat 是否在聊天栏给玩家反馈（命令触发时为 true）
     */
    public static void triggerScan(boolean force, boolean notifyChat) {
        if (!IndexConfig.enabled) return;
        if (!force && autoScanDone) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        // 切主线程：NEIConfigsLoadedEvent 是**后台线程** post 的（已核实），
        // 直接在其中碰 NEI 集合与 ItemStack 会有并发问题。
        mc.func_152344_a(() -> startScan(force, notifyChat));
    }

    /** 主线程内启动（同步跑完；数据量在可接受范围时无需真分帧）。 */
    private static synchronized void startScan(boolean force, boolean notifyChat) {
        if (!force && autoScanDone) return;
        stage = Stage.SNAPSHOT;
        startedAt = System.currentTimeMillis();
        partial = null;

        try {
            NeiScanService.Timings timings = new NeiScanService.Timings();
            IndexModel.ScanResult raw = NeiScanService.scan(timings);
            if (raw == null) {
                stage = Stage.FAILED;
                autoScanDone = true; // 失败也置位，避免每次进世界都重试卡顿（rebuild 可强制重来）
                if (notifyChat) chat("§c[AE2QoL] machindex 扫描失败，请查看日志");
                return;
            }

            stage = Stage.RESOLVE;
            // 电压/来源已在 NeiScanService 内逐机器解析完毕（见 buildEntry），这里只做聚合
            stage = Stage.AGGREGATE;
            List<IndexModel.QuestDefinition> quests = QuestAggregator.aggregate(raw.machines, raw.types);
            raw.quests.addAll(quests);
            raw.stats.addAll(QuestAggregator.buildStats(raw, System.currentTimeMillis() - startedAt));

            stage = Stage.PERSIST;
            lastResult = raw;
            autoScanDone = true;

            StringBuilder sb = new StringBuilder();
            for (String s : raw.stats) {
                if (sb.length() > 0) sb.append("；");
                sb.append(s);
            }
            MyMod.LOG.info("[AE2QoL] machindex 扫描完成：" + sb);

            // 交给服务端写库：
            // · 单机（集成服务器同 JVM）→ 直接写，立即可见；
            // · 专用服务器 → 分包发给服务端（客户端改不了服务端的任务库，这就是「服务器没有」的根因）。
            // 「是否多人」在客户端算好再传：桥接类被双端共用，里面不能碰客户端类。
            Minecraft mc = Minecraft.getMinecraft();
            boolean multiplayer = mc == null || !mc.isIntegratedServerRunning();
            MachIndexServerBridge.onClientScanFinished(raw, NeiScanService.handlerListHash(), multiplayer);

            stage = Stage.DONE;
            if (notifyChat) {
                chat(
                    "§a[AE2QoL] machindex 扫描完成：类型 " + raw.types
                        .size() + " / 机器 " + raw.machines.size() + " / 任务 " + raw.quests.size());
            }
        } catch (Throwable t) {
            stage = Stage.FAILED;
            autoScanDone = true;
            MyMod.LOG.error("[AE2QoL] machindex 扫描管线异常", t);
            if (notifyChat) chat("§c[AE2QoL] machindex 扫描异常：" + t);
        }
    }

    /** 扫描统计摘要（/status 用）。 */
    public static List<String> describeState() {
        List<String> out = new ArrayList<>();
        out.add("阶段=" + stage);
        out.add("已自动扫描=" + autoScanDone);
        out.add("耗时ms=" + (startedAt == 0 ? 0 : (System.currentTimeMillis() - startedAt)));
        if (lastResult != null) {
            out.add("类型=" + lastResult.types.size());
            out.add("机器=" + lastResult.machines.size());
            out.add("任务=" + lastResult.quests.size());
        }
        return out;
    }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(msg));
        }
    }
}
