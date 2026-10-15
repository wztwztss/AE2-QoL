package com.wztwzt.ae2_qof.gtnl;

import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.science.gtnl.common.machine.multiMachineBase.SteamMultiMachineBase;
import com.wztwzt.ae2_qof.MyMod;

/**
 * GTNL 蒸汽楼梯（Steam Elevator）模块的**调速 + 批处理**可配置实现（3.29.0）。
 *
 * <p>
 * 需求口径（用户拍板）：
 * <ul>
 * <li>单份配方时长压到 **1 tick**（{@code singleDurationMode=FIXED_TICKS}）；</li>
 * <li>开批处理：一次操作合并 **n 份**（默认 128），时长 = n × 单份时长 ⇒ 每 6.4 s 完成 128 次配方；</li>
 * <li>蒸汽 **不联动**（{@code steamFollowsSpeed=false}）：蒸汽/t 与改动前完全相同；</li>
 * <li>输入不足时**自动降级**（能供几份就跑几份），不卡机、不空转。</li>
 * </ul>
 *
 * <p>
 * <b>为什么批处理不放大蒸汽</b>：GT 自己的批处理是先按 pre-batch 并行算 EUt，之后才加份数并**只拉伸时长**
 * （{@code GTNLParallelHelper:655-656 / :658-675}）。本实现照抄该语义 ⇒ 每份配方的蒸汽随单份时长一起下降。
 *
 * <p>
 * <b>为什么输入补偿用"逐份尝试"而不是"先算余量"</b>：这些模块的重写版 {@code checkProcessing()} 各自用不同方式
 * 取料（有的直接改 {@code ItemStack.stackSize}、有的直接改 {@code FluidStack.amount}、有的走 {@code depleteInput}），
 * 且我们**没有** forestry / cropsnh / fastutil 的编译依赖，无法可靠地"预先计算可负担份数"。
 * 「逐份尝试消耗、成功一份算一份」既规避了全部外部类型，又天然实现了用户要的自动降级。
 *
 * <p>
 * <b>已知偏差（相对设计文档）</b>：OilDrill 的输出容量夹紧默认**关闭**（{@code oilDrillOutputClamp=false}）。
 * 原因：ME 流体输出仓在 {@code mOutputHatches} 里的容量语义不可靠，硬夹紧会把 ME 玩家的批处理**静默降成 n=1**。
 * 需要严格保护仓容时可在配置里打开。
 */
public final class SteamElevatorTuning {

    // ------------------------------------------------------------------ 配置（热加载） --------------------------
    /** 总开关：false 时完全等同上游行为。 */
    public static volatile boolean enabled = true;
    /** 单份时长来源：VANILLA（上游原值）/ MULTIPLIER（乘 speedMultiplier）/ FIXED_TICKS（固定为 fixedSingleDurationTicks）。 */
    public static volatile String singleDurationMode = "FIXED_TICKS";
    /** FIXED_TICKS 模式下的单份时长（tick）。1 ⇒ 配合 batchSize=128 得到"6.4 s 一次 128 份"。 */
    public static volatile int fixedSingleDurationTicks = 1;
    /** MULTIPLIER 模式下的时长倍率（<1 更快）。 */
    public static volatile double speedMultiplier = 1.0D;
    /** true ⇒ 蒸汽/t 自动 ×(1/速度)；false ⇒ 蒸汽/t 不随速度变化（本需求选 false）。 */
    public static volatile boolean steamFollowsSpeed = false;
    /** 额外蒸汽倍率（1.0 = 不额外收费）。 */
    public static volatile double steamCostMultiplier = 1.0D;
    /** 批处理总开关。 */
    public static volatile boolean batchEnabled = true;
    /** true ⇒ 额外要求机器自身的批处理按钮处于开启状态（对外发布平衡档）。 */
    public static volatile boolean batchFollowsMachineButton = false;
    /** 一次操作最多合并的份数（GT 原生上限 128）。 */
    public static volatile int batchSize = 128;
    /** 一次操作的时长上限（tick）；GT 原生同值 128。 */
    public static volatile int maxBatchDurationTicks = 128;
    /** SteamApiaryModule：每槽每份消耗的蜂王浆（上游值 40）。 */
    public static volatile int apiaryRoyalJellyPerSlot = 40;
    /** SteamBeeBreedingModule：每份消耗的蜂王浆（上游值 128 = 2×64）。 */
    public static volatile int beeBreedingRoyalJellyPerBatch = 128;
    /** SteamGreenhouseModule：每份消耗的水（mB）；仅用于"每份尝试扣减"。 */
    public static volatile int greenhouseWaterPerCycle = 16000;
    /** OilDrill：是否按输出仓余量夹紧份数（默认关闭，理由见类注释）。 */
    public static volatile boolean oilDrillOutputClamp = false;
    /**
     * **抗卡顿闸门**：只有"能凑够这么多份"时才应用调速/批处理；凑不够就**完全不改**（保持上游时长、蒸汽、产出）。
     *
     * <p>
     * 为什么必须有它（需求方口径：*"我最初就是为了减少加速火把带来的卡顿"*）：
     * 本方案靠"**合并操作**"降卡顿——一次操作 n 份 ⇒ `checkProcessing` 频率 = 20/n 次/秒。
     * 若输入供不上而降级到 n=1，就会退化成**每 tick 一次配方检查**（Apiary 上游是每 6000 tick 一次），
     * 比改前**更卡**。因此"宁可不提速，也不提高检查频率"。
     * 想让供料不足时仍提速，把本值调小（例如 2）；想更保守就调大（例如 32）。
     */
    public static volatile int minBatchesToApplyTuning = 8;
    /**
     * **每 tick 处理的份数（并行 P）**，默认 1（= 与 3.29.0 首版完全一致）。
     *
     * <p>
     * 为什么它是唯一能提高"每秒产出"的旋钮：单份时长已被压到 1 tick，一次操作 n 份 / n tick
     * ⇒ 每秒配方数 = 1 配方/tick = 20 份/秒，**这是天花板**；把 {@code batchSize} 调大只会让操作变长
     * （每 51 s 交 1024 份），每秒产出不变。只有 P 能让每个 tick 处理 P 份 ⇒ 每秒 20×P 份。
     *
     * <p>
     * 代价：产出 ×n×P 的同时，输入也**严格按 n×P 份扣**（抽油无输入，故是纯加速）。
     * 抗卡顿闸门仍按**操作时长 n**（= 检查频率 20/n）判定，**不**按 n×P —— 并行不改变检查频率。
     */
    public static volatile int moduleMaxParallel = 1;
    /**
     * **抽油模块专用的并行覆盖值**（0 = 跟随 {@link #moduleMaxParallel}）。
     *
     * <p>
     * 为什么给它单独的键且默认 8：抽油**没有任何输入**，并行是纯加速（不消耗材料）。
     * 需求方口径 =「抽油拉高、其余留 1」，并指定抽油默认值 **8**（≈640,000 L/s 原油）；
     * 把它做成代码默认值，就**不需要改配置文件**。其余 4 类要 ×P 就会 ×P 吃材料，故仍保持 1。
     */
    public static volatile int oilDrillMaxParallel = 8;
    /**
     * **屠宰场模块（气动刷怪笼）专用的并行 P**，默认 8。
     *
     * <p>
     * 它的输入是 **0 数量的刷怪笼**（配方 `GTUtility.copyAmount(0, spawner)`，不消耗）⇒ 并行是纯倍率、
     * 无需输入补偿。注意它与 GT 原生 processingLogic 交互，机器自身批处理开启时本模组会**放行不叠加**
     * （防双重放大，见 MixinSteamEntityCrusherTuning）。
     */
    public static volatile int entityCrusherMaxParallel = 8;

    // ------------------------------------------------------------------ 运行时状态 --------------------------
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    private static Path configFile;
    private static long lastMtime = -1L;
    private static long lastCheck = 0L;

    private static final long FRESH_INTERVAL_MS = 1000L;
    private static final String MODID_GTNL = "sciencenotleisure";
    private static final String PKG = "com.science.gtnl.common.machine.multiblock.module.steamElevator.";
    /** mixin 给目标类添加的标记方法名（注入自检用）。 */
    public static final String MARKER_METHOD = "ae2qof$steamTuningApplied";

    private static final String[] TARGET_CLASSES = { PKG + "SteamBeeBreedingModule", PKG + "SteamOilDrillModule",
        PKG + "SteamApiaryModule", PKG + "SteamGreenhouseModule", PKG + "SteamOreProcessorModule" };
    /** 数值指纹（二级）：类名 → 上游单份时长；仅用于"上游数值变了"的可见提示，不做拦截。 */
    private static final Map<String, Integer> EXPECTED_SINGLE = new HashMap<>();
    static {
        // 不硬编码到逻辑里（时长一律运行期读回），这里只用于"上游数值变了"的可见提示
        EXPECTED_SINGLE.put(PKG + "SteamBeeBreedingModule", 12000);
        EXPECTED_SINGLE.put(PKG + "SteamApiaryModule", 6000);
        EXPECTED_SINGLE.put(PKG + "SteamGreenhouseModule", 100);
    }

    private static final Map<String, Boolean> OBSERVED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> WARNED = new ConcurrentHashMap<>();

    private SteamElevatorTuning() {}

    // ------------------------------------------------------------------ 初始化与热加载 --------------------------

    /** 在 mod preInit 调用：定位 config/ae2_qof/gtnl_steam_elevator.json 并读入。 */
    public static void init(File configDirectory) {
        try {
            Path dir = configDirectory.toPath()
                .resolve("ae2_qof");
            Files.createDirectories(dir);
            configFile = dir.resolve("gtnl_steam_elevator.json");
            reload(true);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] SteamElevator 配置初始化失败，使用内置默认值: " + t);
        }
    }

    /** 每 tick 级调用一次也足够便宜；用文件 mtime 做热加载。 */
    public static void ensureFresh() {
        if (configFile == null) return;
        long now = System.currentTimeMillis();
        if (now - lastCheck < FRESH_INTERVAL_MS) return;
        lastCheck = now;
        try {
            long mtime = Files.exists(configFile) ? Files.getLastModifiedTime(configFile)
                .toMillis() : -1L;
            if (mtime != lastMtime) reload(false);
        } catch (Throwable t) {
            warnOnce("reload", "[AE2QoL] SteamElevator 配置热加载失败: " + t);
        }
    }

    private static synchronized void reload(boolean force) {
        try {
            if (!Files.exists(configFile)) {
                writeDefaults();
                lastMtime = -1L;
                return;
            }
            lastMtime = Files.getLastModifiedTime(configFile)
                .toMillis();
            JsonElement elem;
            try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(configFile),
                StandardCharsets.UTF_8)) {
                elem = GSON.fromJson(reader, JsonElement.class);
            }
            if (elem == null || !elem.isJsonObject()) return;
            JsonObject o = elem.getAsJsonObject();
            enabled = getBool(o, "enabled", enabled);
            singleDurationMode = getString(o, "singleDurationMode", singleDurationMode);
            fixedSingleDurationTicks = clamp(getInt(o, "fixedSingleDurationTicks", fixedSingleDurationTicks), 1, 4096);
            speedMultiplier = clamp(getDouble(o, "speedMultiplier", speedMultiplier), 0.0001D, 1000.0D);
            steamFollowsSpeed = getBool(o, "steamFollowsSpeed", steamFollowsSpeed);
            steamCostMultiplier = clamp(getDouble(o, "steamCostMultiplier", steamCostMultiplier), 0.0001D, 1000.0D);
            batchEnabled = getBool(o, "batchEnabled", batchEnabled);
            batchFollowsMachineButton = getBool(o, "batchFollowsMachineButton", batchFollowsMachineButton);
            batchSize = clamp(getInt(o, "batchSize", batchSize), 1, 4096);
            maxBatchDurationTicks = clamp(getInt(o, "maxBatchDurationTicks", maxBatchDurationTicks), 1, 4096);
            apiaryRoyalJellyPerSlot = clamp(getInt(o, "apiaryRoyalJellyPerSlot", apiaryRoyalJellyPerSlot), 0, 100000);
            beeBreedingRoyalJellyPerBatch = clamp(
                getInt(o, "beeBreedingRoyalJellyPerBatch", beeBreedingRoyalJellyPerBatch),
                0,
                1000000);
            greenhouseWaterPerCycle = clamp(
                getInt(o, "greenhouseWaterPerCycle", greenhouseWaterPerCycle),
                0,
                100000000);
            oilDrillOutputClamp = getBool(o, "oilDrillOutputClamp", oilDrillOutputClamp);
            minBatchesToApplyTuning = clamp(getInt(o, "minBatchesToApplyTuning", minBatchesToApplyTuning), 1, 4096);
            moduleMaxParallel = clamp(getInt(o, "moduleMaxParallel", moduleMaxParallel), 1, 1024);
            oilDrillMaxParallel = clamp(getInt(o, "oilDrillMaxParallel", oilDrillMaxParallel), 0, 1024);
            entityCrusherMaxParallel = clamp(getInt(o, "entityCrusherMaxParallel", entityCrusherMaxParallel), 0, 1024);
            if (force) {
                MyMod.LOG.info(
                    "[AE2QoL] SteamElevator 调速已加载: enabled={} mode={} fixedSingle={} batch={}x{} steamFollowsSpeed={}",
                    enabled,
                    singleDurationMode,
                    fixedSingleDurationTicks,
                    batchEnabled ? batchSize : 1,
                    maxBatchDurationTicks,
                    steamFollowsSpeed);
            }
            // 3.30.0-fix1：文件里缺少新增键时**补写回文件**。
            // 为什么必须补：加载器只读不写 ⇒ 用旧版生成过的 json 里看不到新键，用户会以为"这个键不能改"。
            // 补写用的是**当前已加载值**（等于用户原值），不会覆盖用户设置。
            if (hasMissingKeys(o)) {
                MyMod.LOG.info("[AE2QoL] SteamElevator 配置缺少新增键，已补写（值保持原样）: " + configFile);
                writeDefaults();
                lastMtime = Files.getLastModifiedTime(configFile)
                    .toMillis();
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] SteamElevator 配置读取异常，保留当前值: " + t);
        }
    }

    /** 配置里是否缺少任一"已知键"（缺则补写回文件，否则用户看不到/改不了该键）。 */
    private static boolean hasMissingKeys(JsonObject o) {
        String[] keys = { "enabled", "singleDurationMode", "fixedSingleDurationTicks", "speedMultiplier",
            "steamFollowsSpeed", "steamCostMultiplier", "batchEnabled", "batchFollowsMachineButton", "batchSize",
            "maxBatchDurationTicks", "apiaryRoyalJellyPerSlot", "beeBreedingRoyalJellyPerBatch",
            "greenhouseWaterPerCycle", "oilDrillOutputClamp", "minBatchesToApplyTuning", "moduleMaxParallel",
            "oilDrillMaxParallel", "entityCrusherMaxParallel" };
        for (String k : keys) {
            if (!o.has(k)) return true;
        }
        return false;
    }

    private static void writeDefaults() throws Exception {
        JsonObject o = new JsonObject();
        o.addProperty("_comment", "GTNL 蒸汽楼梯模块调速/批处理。默认 = 单份 1 tick + 批 128 + 蒸汽不联动；改 enabled=false 即完全回到上游行为。");
        o.addProperty("enabled", enabled);
        o.addProperty("singleDurationMode", singleDurationMode);
        o.addProperty("fixedSingleDurationTicks", fixedSingleDurationTicks);
        o.addProperty("speedMultiplier", speedMultiplier);
        o.addProperty("steamFollowsSpeed", steamFollowsSpeed);
        o.addProperty("steamCostMultiplier", steamCostMultiplier);
        o.addProperty("batchEnabled", batchEnabled);
        o.addProperty("batchFollowsMachineButton", batchFollowsMachineButton);
        o.addProperty("batchSize", batchSize);
        o.addProperty("maxBatchDurationTicks", maxBatchDurationTicks);
        o.addProperty("apiaryRoyalJellyPerSlot", apiaryRoyalJellyPerSlot);
        o.addProperty("beeBreedingRoyalJellyPerBatch", beeBreedingRoyalJellyPerBatch);
        o.addProperty("greenhouseWaterPerCycle", greenhouseWaterPerCycle);
        o.addProperty("oilDrillOutputClamp", oilDrillOutputClamp);
        o.addProperty("_comment_minBatches", "抗卡顿闸门：凑不够这么多份就完全不改（保持上游时长），避免退化成每 tick 一次配方检查");
        o.addProperty("minBatchesToApplyTuning", minBatchesToApplyTuning);
        o.addProperty(
            "_comment_moduleMaxParallel",
            "每 tick 处理的份数（并行 P），默认 1=与首版一致；这是唯一能提高每秒产出的旋钮（产出与材料都 ×P，抗卡顿闸门不受影响）");
        o.addProperty("moduleMaxParallel", moduleMaxParallel);
        o.addProperty(
            "_comment_oilDrillMaxParallel",
            "抽油模块专用并行（0=跟随 moduleMaxParallel）。抽油无输入，默认 8=纯加速；其余 4 类要 ×P 会 ×P 吃材料，故默认 1");
        o.addProperty("oilDrillMaxParallel", oilDrillMaxParallel);
        o.addProperty("entityCrusherMaxParallel", entityCrusherMaxParallel);
        try (OutputStreamWriter writer = new OutputStreamWriter(
            Files.newOutputStream(configFile),
            StandardCharsets.UTF_8)) {
            GSON.toJson(o, writer);
        }
        MyMod.LOG.info("[AE2QoL] SteamElevator 配置已生成默认文件: " + configFile);
    }

    // ------------------------------------------------------------------ 启动自检（R3） --------------------------

    /**
     * 注入自检 + 一级结构指纹 + 二级数值指纹的**启动期**部分。
     *
     * <p>
     * 为什么需要它：本模组已定案**不使用版本白名单**、注入器全部 {@code require=0}（永不崩）。
     * 代价是"注入失败"不再崩溃 ⇒ 必须由这里把"失效"变成**可见的日志**。
     * 判定手段是各 mixin 通过 {@code @Unique} 加在目标类上的标记方法 {@link #MARKER_METHOD}：
     * 它存在 ⇔ 该 mixin 真的被应用了。
     */
    public static void selfCheck() {
        if (!isGtnlLoaded()) {
            MyMod.LOG.info("[AE2QoL] SteamElevator 调速: 未检测到 GTNL({}), 已跳过（不影响游戏）", MODID_GTNL);
            return;
        }
        int ok = 0;
        StringBuilder failed = new StringBuilder();
        for (String cn : TARGET_CLASSES) {
            if (hasMarker(cn)) {
                ok++;
                continue;
            }
            if (failed.length() > 0) failed.append(", ");
            failed.append(cn.substring(cn.lastIndexOf('.') + 1));
        }
        if (ok == TARGET_CLASSES.length) {
            MyMod.LOG.info("[AE2QoL] SteamElevator 调速: 注入自检通过（{}/{} 个模块已生效）", ok, TARGET_CLASSES.length);
        } else {
            MyMod.LOG.error(
                "[AE2QoL] SteamElevator 调速: 注入自检失败 {}/{}，以下模块**不会**被调速（其余功能不受影响）: {}",
                ok,
                TARGET_CLASSES.length,
                failed);
        }
    }

    private static boolean hasMarker(String className) {
        try {
            Class<?> c = Class.forName(className);
            c.getDeclaredMethod(MARKER_METHOD);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isGtnlLoaded() {
        try {
            return cpw.mods.fml.common.Loader.isModLoaded(MODID_GTNL);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 核心计算 --------------------------

    /** 单份时长（tick）：0 表示"不调速"（等价禁用）。 */
    public static int goalSingleTicks(int vanillaSingle) {
        if (!enabled) return 0;
        int cap = clamp(maxBatchDurationTicks, 1, 4096);
        String mode = singleDurationMode == null ? "VANILLA" : singleDurationMode.toUpperCase();
        int v;
        if ("FIXED_TICKS".equals(mode)) {
            v = clamp(fixedSingleDurationTicks, 1, cap);
        } else if ("MULTIPLIER".equals(mode)) {
            v = Math.max(1, (int) Math.round(vanillaSingle * clamp(speedMultiplier, 0.0001D, 1000.0D)));
        } else {
            return 0; // VANILLA：不改时长
        }
        if (vanillaSingle > 0) fingerprint(vanillaSingle);
        return Math.min(v, cap);
    }

    /** 时长上限允许的最大份数（batchSize 与 cap/goal 取小；不含输入限制）。 */
    public static int byTimeBatchCount(int goalSingle) {
        if (!enabled || !batchEnabled || goalSingle <= 0) return 1;
        int cap = clamp(maxBatchDurationTicks, 1, 4096);
        if (goalSingle >= cap) return 1;
        int byTime = Math.max(1, cap / goalSingle);
        return Math.max(1, Math.min(clamp(batchSize, 1, 4096), byTime));
    }

    /** 最终时长：n==1 时不封顶（否则会把上游长时长配方压成 128t，属于意外加速）。 */
    public static int finalDuration(int goalSingle, int n) {
        int cap = clamp(maxBatchDurationTicks, 1, 4096);
        if (n <= 1) return Math.max(1, goalSingle);
        long total = (long) goalSingle * n;
        return (int) Math.max(1L, Math.min((long) cap, total));
    }

    /** 蒸汽/t（不放大份数，与 GT 原生批处理语义一致）。 */
    public static long steamPerTick(long absEut) {
        double s = clamp(steamCostMultiplier, 0.0001D, 1000.0D);
        double follow = steamFollowsSpeed ? (1.0D / clamp(speedMultiplier, 0.0001D, 1000.0D)) : 1.0D;
        return Math.max(1L, Math.round((double) absEut * s * follow));
    }

    /** 机器自身批处理按钮是否要求开启。 */
    public static boolean machineBatchGateOk(boolean machineBatchOn) {
        return !batchFollowsMachineButton || machineBatchOn;
    }

    /** 抗卡顿闸门：份数是否达到"值得应用调速"的门槛（见 {@link #minBatchesToApplyTuning}）。 */
    public static boolean meetsBatchFloor(int n) {
        return n >= clamp(minBatchesToApplyTuning, 1, 4096);
    }

    /** 生效的并行 P（关闭总开关时恒为 1 ⇒ 完全回上游）。 */
    public static int effectiveParallel() {
        if (!enabled) return 1;
        return clamp(moduleMaxParallel, 1, 1024);
    }

    /** 抽油模块生效的并行 P：{@link #oilDrillMaxParallel} 为 0 时跟随全局；关闭总开关时恒 1。 */
    public static int effectiveOilDrillParallel() {
        if (!enabled) return 1;
        int v = oilDrillMaxParallel > 0 ? oilDrillMaxParallel : moduleMaxParallel;
        return clamp(v, 1, 1024);
    }

    /** 屠宰场模块生效的并行 P：{@link #entityCrusherMaxParallel} 为 0 时跟随全局；关闭总开关时恒 1。 */
    public static int effectiveEntityCrusherParallel() {
        if (!enabled) return 1;
        int v = entityCrusherMaxParallel > 0 ? entityCrusherMaxParallel : moduleMaxParallel;
        return clamp(v, 1, 1024);
    }

    // ------------------------------------------------------------------ 产出放大 --------------------------

    /**
     * ItemStack[] × n。
     *
     * <p>
     * <b>不切叠（重要）</b>：GT 自己的并列产出就是"**一个超大堆**"——`ParallelHelper.addItemsLong:633-641`
     * 只在数量超过 {@code Integer.MAX_VALUE} 时才拆，而输出仓 `MTEHatchOutputBus.storePartial:185-216` 会
     * `stack.stackSize -= toInsert` 逐槽自行拆分。早先按 64 切叠会把 5 个堆变成**上万个 ItemStack**，
     * 反而放大输出路径开销（与"降卡顿"目标相反），故与 GT 保持一致。
     */
    public static ItemStack[] multiplyItems(ItemStack[] in, int n) {
        if (in == null || in.length == 0 || n <= 1) return in;
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) {
            ItemStack s = in[i];
            if (s == null) {
                out[i] = null;
                continue;
            }
            ItemStack copy = s.copy();
            long total = (long) s.stackSize * n;
            copy.stackSize = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
            out[i] = copy;
        }
        return out;
    }

    /** FluidStack[] × n。 */
    public static FluidStack[] multiplyFluids(FluidStack[] in, int n) {
        if (in == null || in.length == 0 || n <= 1) return in;
        FluidStack[] out = new FluidStack[in.length];
        for (int i = 0; i < in.length; i++) {
            FluidStack s = in[i];
            if (s == null) {
                out[i] = null;
                continue;
            }
            FluidStack copy = s.copy();
            long total = (long) s.amount * n;
            copy.amount = total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
            out[i] = copy;
        }
        return out;
    }

    // ------------------------------------------------------------------ 输入补偿工具 --------------------------

    /**
     * 逐份尝试消耗一份蜂王浆（{@code jellyPerBatch} 个），成功返回 true。
     *
     * <p>
     * 走 {@code depleteInput(ItemStack)}，但该 API 要求"单个槽位内数量足够"，因此按 ≤64 分块多次调用。
     */
    /**
     * 逐份尝试消耗一份蜂王浆（{@code jellyPerBatch} 个），成功返回 true。
     *
     * <p>
     * 走 {@code depleteInput(ItemStack)}，但该 API 要求"单个槽位内数量足够"，因此按 ≤64 分块多次调用。
     */
    public static boolean tryConsumeOneBatchJelly(SteamMultiMachineBase<?> machine, int jellyPerBatch) {
        if (jellyPerBatch <= 0) return true; // 配置为 0 ⇒ 视为不收费
        ItemStack proto = royalJellyProto();
        if (proto == null) return false;
        int remaining = jellyPerBatch;
        int chunk = Math.max(1, Math.min(64, proto.getMaxStackSize()));
        while (remaining > 0) {
            int take = Math.min(remaining, chunk);
            ItemStack req = proto.copy();
            req.stackSize = take;
            if (!machine.depleteInput(req)) return false;
            remaining -= take;
        }
        return true;
    }

    /** 逐份尝试消耗一份水（{@code waterPerBatch} mB）；直接改 FluidStack.amount，与上游温室取水方式一致。 */
    public static boolean tryConsumeOneBatchWater(SteamMultiMachineBase<?> machine, int waterPerBatch) {
        if (waterPerBatch <= 0) return true;
        int remaining = waterPerBatch;
        for (FluidStack fs : machine.getStoredFluids()) {
            if (remaining <= 0) break;
            if (fs == null || fs.amount <= 0) continue;
            if (!isWaterLike(fs)) continue;
            int take = Math.min(fs.amount, remaining);
            fs.amount -= take;
            remaining -= take;
        }
        if (remaining <= 0) {
            machine.updateSlots();
            return true;
        }
        // 没扣满：把已扣的还回去（避免"半份"状态）
        restoreWater(machine, waterPerBatch - remaining);
        return false;
    }

    private static void restoreWater(SteamMultiMachineBase<?> machine, int amount) {
        if (amount <= 0) return;
        for (FluidStack fs : machine.getStoredFluids()) {
            if (amount <= 0) break;
            if (fs == null || !isWaterLike(fs)) continue;
            int give = Math.min(amount, Integer.MAX_VALUE - fs.amount);
            fs.amount += give;
            amount -= give;
        }
    }

    /** 只认"水/蒸馏水"这类可作水用的流体（按流体名匹配，避免引入 CropsNH 依赖）。 */
    private static boolean isWaterLike(FluidStack fs) {
        String name = fs.getFluid() == null ? null
            : fs.getFluid()
                .getName();
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("water");
    }

    /**
     * 批量扣除 k 份矿石（每份 {@code oresPerBatch} 个），返回**真正扣成功的份数**。
     *
     * <p>
     * 性能：输入列表只取一次、ALL_PROCESSABLE 的反射句柄只解析一次（热路径，128 份/次操作）。
     * 语义：一份必须**能凑齐**才扣（先扫描、后消费），避免"扣了半份"的中间态。
     */
    public static int consumeOreBatches(SteamMultiMachineBase<?> machine, int oresPerBatch, int maxBatches) {
        if (oresPerBatch <= 0 || maxBatches <= 0) return 0;
        List<ItemStack> inputs = machine.getStoredInputs();
        int done = 0;
        for (int b = 0; b < maxBatches; b++) {
            int available = 0;
            for (ItemStack ore : inputs) {
                if (ore == null || ore.stackSize <= 0) continue;
                if (!isProcessableOre(ore)) continue;
                available += ore.stackSize;
                if (available >= oresPerBatch) break;
            }
            if (available < oresPerBatch) break;
            int remaining = oresPerBatch;
            for (ItemStack ore : inputs) {
                if (remaining <= 0) break;
                if (ore == null || ore.stackSize <= 0) continue;
                if (!isProcessableOre(ore)) continue;
                int take = Math.min(ore.stackSize, remaining);
                ore.stackSize -= take;
                remaining -= take;
            }
            done++;
        }
        if (done > 0) machine.updateSlots();
        return done;
    }

    /**
     * 批量扣除 k 份蜂王浆，每份走 {@code depleteInput}（育种模块与上游取料方式一致），返回成功的份数。
     */
    public static int consumeJellyBatchesDeplete(SteamMultiMachineBase<?> machine, int jellyPerBatch, int maxBatches) {
        if (jellyPerBatch <= 0) return Math.max(0, maxBatches);
        int done = 0;
        for (int b = 0; b < maxBatches; b++) {
            if (!tryConsumeOneBatchJelly(machine, jellyPerBatch)) break;
            done++;
        }
        return done;
    }

    /**
     * 批量扣除 k 份蜂王浆（每份 {@code jellyPerBatch} 个），返回真正扣成功的份数。
     *
     * <p>
     * 与上游 Apiary 的取料方式一致：直接改 {@code getStoredInputs()} 返回的 {@code ItemStack.stackSize}
     * （GTNL 的 {@code getAllStoredInputs()} 返回的是仓内真实对象引用）。列表只取一次。
     */
    public static int consumeJellyBatches(SteamMultiMachineBase<?> machine, int jellyPerBatch, int maxBatches) {
        if (jellyPerBatch <= 0 || maxBatches <= 0) return 0;
        ItemStack proto = royalJellyProto();
        if (proto == null) return 0;
        List<ItemStack> inputs = machine.getStoredInputs();
        int done = 0;
        for (int b = 0; b < maxBatches; b++) {
            int available = 0;
            for (ItemStack s : inputs) {
                if (s == null || s.stackSize <= 0) continue;
                if (!s.isItemEqual(proto)) continue;
                available += s.stackSize;
                if (available >= jellyPerBatch) break;
            }
            if (available < jellyPerBatch) break;
            int remaining = jellyPerBatch;
            for (ItemStack s : inputs) {
                if (remaining <= 0) break;
                if (s == null || s.stackSize <= 0) continue;
                if (!s.isItemEqual(proto)) continue;
                int take = Math.min(s.stackSize, remaining);
                s.stackSize -= take;
                remaining -= take;
            }
            done++;
        }
        if (done > 0) machine.updateSlots();
        return done;
    }

    /** 逐份尝试消耗一份矿石（{@code oresPerBatch} 个），只吃上游会吃的那些（ALL_PROCESSABLE）。 */
    public static boolean tryConsumeOneBatchOres(SteamMultiMachineBase<?> machine, int oresPerBatch) {
        if (oresPerBatch <= 0) return true;
        int remaining = oresPerBatch;
        for (ItemStack ore : machine.getStoredInputs()) {
            if (remaining <= 0) break;
            if (ore == null || ore.stackSize <= 0) continue;
            if (!isProcessableOre(ore)) continue;
            int take = Math.min(ore.stackSize, remaining);
            ore.stackSize -= take;
            remaining -= take;
        }
        if (remaining <= 0) {
            machine.updateSlots();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 反射桥（避免新增编译依赖） -------------

    private static ItemStack jellyProto;
    private static boolean jellyProtoResolved;
    private static Object oreSet;
    private static Method oreSetContains;
    private static boolean oreSetResolved;

    /** forestry 蜂王浆原型（Forestry 不在本模组编译类路径，故走反射）。 */
    public static ItemStack royalJellyProto() {
        if (jellyProtoResolved) return jellyProto;
        jellyProtoResolved = true;
        try {
            Class<?> plugin = Class.forName("forestry.plugins.PluginApiculture");
            Object items = plugin.getField("items")
                .get(null);
            Object royalJelly = items.getClass()
                .getField("royalJelly")
                .get(items);
            Method getItemStack = royalJelly.getClass()
                .getMethod("getItemStack", int.class);
            Object stack = getItemStack.invoke(royalJelly, 1);
            if (stack instanceof ItemStack) jellyProto = (ItemStack) stack;
        } catch (Throwable t) {
            warnOnce("jelly", "[AE2QoL] SteamElevator 调速: 无法解析 forestry 蜂王浆原型，蜂箱/育种模块将不做输入补偿（份数保持 1）: " + t);
        }
        if (jellyProto == null) return null;
        return jellyProto;
    }

    /** 上游"可处理矿石"判定（fastutil IntOpenHashSet 不在编译类路径，故走反射；**句柄缓存**，热路径只做一次 invoke）。 */
    public static boolean isProcessableOre(ItemStack ore) {
        try {
            if (oreSet == null && !oreSetResolved) {
                oreSetResolved = true;
                try {
                    Class<?> c = Class.forName(PKG + "SteamOreProcessorModule");
                    Field f = c.getField("ALL_PROCESSABLE");
                    Object set = f.get(null);
                    Method contains = set.getClass()
                        .getMethod("contains", int.class);
                    oreSet = set;
                    oreSetContains = contains;
                } catch (Throwable t) {
                    warnOnce("orefilter", "[AE2QoL] SteamElevator 调速: 矿石判定不可用，矿处模块将不做输入补偿（份数保持 1）: " + t);
                }
            }
            if (oreSet == null || oreSetContains == null) return false;
            int id = gregtech.api.util.GTUtility.stackToInt(ore);
            if (id == 0) return false;
            Object r = oreSetContains.invoke(oreSet, id);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            warnOnce("orefilter:invoke", "[AE2QoL] SteamElevator 调速: 矿石判定调用失败（本次按不可处理处理）: " + t);
            return false;
        }
    }

    /** 反射读 public int 字段（如 Apiary 的 mStorage.size()、OreProcessor 的 currentParallelism）。 */
    public static int readIntField(Object target, String field) {
        try {
            Field f = target.getClass()
                .getField(field);
            Object v = f.get(target);
            return v instanceof Integer ? (Integer) v : 0;
        } catch (Throwable t) {
            warnOnce("field:" + field, "[AE2QoL] SteamElevator 调速: 读取字段 " + field + " 失败: " + t);
            return 0;
        }
    }

    /** 反射读集合尺寸（Apiary 的 mStorage）。 */
    public static int readListSize(Object target, String field) {
        try {
            Field f = target.getClass()
                .getField(field);
            Object v = f.get(target);
            if (v instanceof List) return ((List<?>) v).size();
            return 0;
        } catch (Throwable t) {
            warnOnce("list:" + field, "[AE2QoL] SteamElevator 调速: 读取列表字段 " + field + " 失败: " + t);
            return 0;
        }
    }

    // ------------------------------------------------------------------ 日志（一次性） --------------------------

    /** 二级数值指纹：上游单份时长与内置期望不一致 ⇒ 只 WARN 一次，继续工作（因为时长是运行期读回的）。 */
    private static void fingerprint(int observed) {
        String key = Integer.toString(observed);
        if (OBSERVED.putIfAbsent(key, Boolean.TRUE) != null) return;
        for (Map.Entry<String, Integer> e : EXPECTED_SINGLE.entrySet()) {
            if (e.getValue() == observed) return;
        }
        if (observed == 12000 || observed == 6000
            || observed == 100
            || observed == 1200
            || observed == 600
            || observed == 400) {
            return;
        }
        warnOnce(
            "fp:" + observed,
            "[AE2QoL] SteamElevator 调速: 观测到未登记的上游单份时长 " + observed + " tick（可能是上游数值变化或新模块），已按运行期读回值处理，请复验");
    }

    public static void warnOnce(String key, String message) {
        if (WARNED.putIfAbsent(key, Boolean.TRUE) == null) {
            MyMod.LOG.warn(message);
        }
    }

    public static void infoOnce(String key, String message) {
        if (WARNED.putIfAbsent("i:" + key, Boolean.TRUE) == null) {
            MyMod.LOG.info(message);
        }
    }

    // ------------------------------------------------------------------ JSON 读取小工具 --------------------------

    private static boolean getBool(JsonObject o, String key, boolean def) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static int getInt(JsonObject o, String key, int def) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static double getDouble(JsonObject o, String key, double def) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static String getString(JsonObject o, String key, String def) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
