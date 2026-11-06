package com.wztwzt.ae2_qof.machindex.server;

import java.io.File;
import java.util.List;

import net.minecraft.server.MinecraftServer;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.machindex.common.IndexModel;
import com.wztwzt.ae2_qof.machindex.common.MachineIndexCache;

import cpw.mods.fml.common.Loader;

/**
 * 客户端 → 服务端的桥（**不 import BQ 类型**，只做「谁在什么时机调用写库」的路由）。
 *
 * <p>
 * <b>为什么需要这层</b>（方案里补齐的结构性缺口）：NEI 的扫描数据只存在于客户端
 * （专用服务器没有 NEI 类），而 BQ 写库是服务端权威。因此扫描完成后必须跨这道边界：
 * <ul>
 * <li><b>单机</b>（集成服务器与客户端同 JVM）：直接把内存结果交给 {@link BqQuestWriter}；</li>
 * <li><b>专用服</b>：客户端只把结果写进 {@code config/ae2_qof/machindex/cache.dat}
 * （服务端能读到同一份文件），服务端在玩家进服时按缓存对账。</li>
 * </ul>
 *
 * <p>
 * 这里刻意**不做自定义网络包**：缓存文件本身就是跨端传输介质（硬约束 10 允许内部缓存），
 * 少一条网络链路就少一类「专用服拿不到数据」的静默故障；且客户端与服务端共用同一份路径时，
 * 单机与专用服走的是同一条服务端写库代码，测试覆盖也一致。
 */
public final class MachIndexServerBridge {

    private MachIndexServerBridge() {}

    /** 客户端扫描完成后调用（客户端侧；单机同 JVM 时顺带触发服务端写库）。 */
    public static void onClientScanFinished(IndexModel.ScanResult result) {
        onClientScanFinished(result, "", false);
    }

    /**
     * 客户端扫描完成后调用（带 handler 列表哈希与「是否多人」标志）。
     *
     * <p>
     * handler 哈希由客户端扫描器算出后传进来 —— 本类**不直接引用** {@code NeiScanService}，
     * 否则服务端加载本类时会把只有客户端才有的 NEI 类一起拖进来。
     *
     * <p>
     * {@code multiplayer} 同理由客户端（{@code ScanPipeline}）算好传入 —— 本类被双端共用，
     * 里面不能出现 {@code net.minecraft.client.*}。
     *
     * @param handlerHash 客户端 handler 列表的确定性哈希（服务端调用时传空）
     * @param multiplayer true = 连的是专用服务器（要发包同步）；false = 单机（同 JVM 直接写）
     */
    public static void onClientScanFinished(IndexModel.ScanResult result, String handlerHash, boolean multiplayer) {
        if (result == null) return;
        try {
            File configDir = configDir();
            if (configDir == null) {
                MyMod.LOG.warn("[AE2QoL] machindex 无法定位配置目录，跳过缓存写入");
                return;
            }
            MachineIndexCache.CacheFile cache = new MachineIndexCache.CacheFile();
            cache.generatedAt = System.currentTimeMillis();
            cache.result.types.addAll(result.types);
            cache.result.machines.addAll(result.machines);
            cache.result.quests.addAll(result.quests);
            cache.result.stats.addAll(result.stats);

            MachineIndexCache.CacheKey key = MachineIndexCache.buildKey(handlerHash);
            cache.key.mcVersion = key.mcVersion;
            cache.key.modListHash = key.modListHash;
            cache.key.handlerListHash = key.handlerListHash;
            cache.key.configHash = key.configHash;
            cache.key.schema = key.schema;

            if (MachineIndexCache.save(configDir, cache)) {
                MyMod.LOG.info(
                    "[AE2QoL] machindex 缓存已写入：" + MachineIndexCache.cacheFile(configDir)
                        .getAbsolutePath() + "（任务 " + cache.result.quests.size() + " 条）");
            }

            // 单机：服务端在同 JVM 内 ⇒ 直接写库（否则要等玩家重新进服才生效，体验很差）
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && isIntegratedServer(server)) {
                // 反射调用 BqQuestWriter，绝不在本类里留下对它的**直接**引用：
                // 本类会被客户端与服务端共用，而 BqQuestWriter 含 BQ 类型；未装 BQ 的环境里
                // 任何一次「解析到该常量池条目」都可能变成 NoClassDefFoundError。
                invokeWriter(cache.result, "clientScan");
            } else if (multiplayer) {
                // 专用服务器：客户端与服务端是两个 JVM，本地写库只改了客户端的内存，
                // 服务端任务库完全没动（这就是「单机有、服务器没有」的根因）。
                // 必须把结果分包发给服务端，由服务端 BqQuestWriter 写库。
                sendToServer(cache.result);
            }
        } catch (Throwable t) {
            MyMod.LOG.error("[AE2QoL] machindex 扫描结果落地失败", t);
        }
    }

    /**
     * 客户端 → 服务端发送索引（分片）。仅客户端调用。
     *
     * <p>
     * <b>注意</b>：本类被双端共用，所以这里<b>不能</b>出现 {@code net.minecraft.client.*}
     * 任何类型（专用服加载即 NoClassDefFoundError，本项目已踩过同类坑）。
     * 「是否多人」由客户端侧（{@code ScanPipeline}）算好传进来。
     */
    private static void sendToServer(IndexModel.ScanResult result) {
        try {
            java.util.List<com.wztwzt.ae2_qof.machindex.network.MachIndexSyncPacket> packets = com.wztwzt.ae2_qof.machindex.network.MachIndexSyncPacket
                .split(result);
            if (packets.isEmpty()) {
                MyMod.LOG.warn("[AE2QoL] machindex 无可同步的分片（序列化失败或索引过大）");
                return;
            }
            for (com.wztwzt.ae2_qof.machindex.network.MachIndexSyncPacket p : packets) {
                com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL.sendToServer(p);
            }
            MyMod.LOG.info("[AE2QoL] machindex 已向服务端发送索引（" + packets.size() + " 片），等待服务端写库");
        } catch (Throwable t) {
            MyMod.LOG.error("[AE2QoL] machindex 向服务端发送索引失败", t);
        }
    }

    /** 服务端收到客户端索引后写库（由 {@code MachIndexSyncPacket.Handler} 调用，服务端侧）。 */
    public static void applyOnServer(IndexModel.ScanResult result, String trigger) {
        if (result == null) return;
        invokeWriter(result, trigger);
    }

    /**
     * 反射调用 {@code BqQuestWriter.apply(ScanResult)}。
     *
     * <p>
     * 用反射是为了让本类（客户端也会加载）在字节码层面**不出现** BQ 类型/写入器类型，
     * 从而保证「未装 BQ」的整合包不会因为这类间接引用而崩。已经过 javap 核对：
     * 本类常量池里不再有 {@code BqQuestWriter}。
     */
    private static void invokeWriter(IndexModel.ScanResult result, String trigger) {
        try {
            Class<?> writer = Class.forName(
                "com.wztwzt.ae2_qof.machindex.server.BqQuestWriter",
                true,
                MachIndexServerBridge.class.getClassLoader());
            writer.getMethod("apply", IndexModel.ScanResult.class)
                .invoke(null, result);
        } catch (Throwable t) {
            MyMod.LOG.error("[AE2QoL] machindex 写库调用失败（trigger=" + trigger + "）", t);
        }
    }

    /**
     * 服务端进服时的对账入口（**服务端侧**）。
     *
     * @param trigger 触发来源（日志用，如 "join" / "command"）
     */
    public static void repairFromCache(String trigger) {
        if (!Loader.isModLoaded("betterquesting")) {
            MyMod.LOG.info("[AE2QoL] machindex 未检测到 BetterQuesting，跳过任务写入（trigger=" + trigger + "）");
            return;
        }
        File configDir = configDir();
        if (configDir == null) return;
        MachineIndexCache.CacheFile cache = MachineIndexCache.load(configDir);
        if (cache == null) {
            // B 轨兜底（用户 2026-10-29 拍板 C = A+B 双轨）：
            // 服务端独跑 / 没有客户端同步过时，直接在服务端枚举 GT 注册表自行建索引。
            // 覆盖范围只到 GT 注册表里的多方块（第三方自注册的 MTE 会漏），所以它是兜底而非主路径。
            MyMod.LOG.info("[AE2QoL] machindex 无可用缓存 → 启用服务端兜底索引（trigger=" + trigger + "）");
            IndexModel.ScanResult fallback = ServerSideIndexBuilder.build();
            if (fallback == null || fallback.quests.isEmpty()) {
                MyMod.LOG.info("[AE2QoL] machindex 服务端兜底无产出，跳过任务写入（trigger=" + trigger + "）");
                return;
            }
            invokeWriter(fallback, trigger + ":serverFallback");
            return;
        }
        invokeWriter(cache.result, trigger);
    }

    /** 配置目录（客户端与服务端同路径语义：{@code config/}）。 */
    public static File configDir() {
        try {
            return Loader.instance()
                .getConfigDir();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 是否集成服务器（单机）。 */
    private static boolean isIntegratedServer(MinecraftServer server) {
        try {
            return server.isSinglePlayer();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 供命令用：读缓存里的任务统计。 */
    public static List<String> describeCache() {
        java.util.List<String> out = new java.util.ArrayList<>();
        File configDir = configDir();
        if (configDir == null) {
            out.add("配置目录不可用");
            return out;
        }
        MachineIndexCache.CacheFile cache = MachineIndexCache.load(configDir);
        if (cache == null) {
            out.add("无缓存");
            return out;
        }
        out.add("缓存键=" + cache.key.describe());
        out.add(
            "生成时间="
                + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(cache.generatedAt)));
        out.add("类型=" + cache.result.types.size());
        out.add("机器=" + cache.result.machines.size());
        out.add("任务=" + cache.result.quests.size());
        return out;
    }
}
