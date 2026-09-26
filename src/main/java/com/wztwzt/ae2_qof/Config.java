package com.wztwzt.ae2_qof;

import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraftforge.common.config.Configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 统一玩家配置文件 {@code config/ae2_qof/settings.json}：
 * - io_port_rate：强化 IO 端口传输倍率（默认 1024，1..Integer.MAX_VALUE）
 * - smart_doubling_max_rounds：智能倍增最大轮数（默认 0=不限，0..Integer.MAX_VALUE；0 表示一次发配剩余全部轮数）
 * - smart_doubling_push_cap：智能倍增**单次推送轮数上限**（默认 4096，1..Integer.MAX_VALUE）
 *   —— 受 AE2 功率探测（凑够即停的遍历，超额查询会走遍全部储能设备）与物品更新量限制，
 *   默认 4096 是审计项 #51（O(P) 探测）与 #73（大订单客户端被海量更新淹没）修复时定下的安全值；
 *   服务器算力充裕时可调大，代价是每 tick 的提取量与物品更新量线性上升。
 * - nei_overlay_enabled：NEI 叠加层开关（默认 true）
 *
 * 支持热加载：直接编辑文件后约 1 秒内自动生效（服务端/单机均可），
 * 也可用 OP 命令 {@code /ae2qof reload} 立即重载；游戏内可在「Mods → AE2 QoL → Config」页面修改。
 */
public class Config {

    /** 强化 IO 端口传输倍率（热加载字段）。 */
    public static volatile int exIOPortTransferContentsRate = 1024;

    /** 智能倍增最大轮数（热加载字段）：0 = 不限（一次发配剩余全部轮数）。 */
    public static volatile int smartDoublingMaxRounds = 0;

    /**
     * 智能倍增单次推送轮数上限（热加载字段，3.21.4）。
     * 受功率探测复杂度与物品更新量限制，默认 4096（#51/#73 修复时定下的安全值）；
     * 服务器算力充裕时可调大，但每 tick 开销会线性上升。
     */
    public static volatile int smartDoublingPushCap = 4096;

    /** NEI 叠加层开关（热加载字段）。 */
    public static volatile boolean neiOverlayEnabled = true;

    /**
     * 合成产物 pin 置顶行总开关（热加载字段，3.15.0）。
     * false 时终端 pin 行默认回退为关闭（玩家仍可在终端设置中手动开启）。
     */
    public static volatile boolean pinRowEnabled = true;

    /**
     * 库存检测覆盖板快捷数量预设（热加载字段）。
     * 格式：按钮文字=数值，分号分隔；内置值可增删改。
     * 数值必须 ≥0（负数直接忽略并打日志）。
     */
    public static volatile String stockMonitorPresets = "1万=10000;100万=1000000;10亿=1000000000;清零=0";

    /**
     * v7 机器贴图开关（热加载字段）。
     * auto=自动（默认，25 张 PNG 齐全才启用）、on=强制启用、off=强制关闭（必定回 GT 默认外观）。
     */
    public static volatile String v7Textures = "auto";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private static final long FRESH_INTERVAL_MS = 1000L;

    private static Path SETTINGS_FILE;

    private static long lastCheck = 0L;
    private static long lastLoadedMtime = -1L;

    private Config() {}

    /**
     * 初始化配置（mod preInit 时调用）。旧版 {@code config/ae2_qof.cfg} 存在时自动迁移数值并删除旧文件。
     *
     * @param configFile Forge 建议的配置文件（旧版 cfg 位置，用于迁移）
     */
    public static void synchronizeConfiguration(File configFile) {
        try {
            Path configDir = configFile.toPath()
                .getParent()
                .resolve("ae2_qof");
            SETTINGS_FILE = configDir.resolve("settings.json");
            if (!Files.exists(SETTINGS_FILE)) {
                int oldRate = readLegacyCfgInt(
                    configFile,
                    "exIOPortTransferContentsRate",
                    exIOPortTransferContentsRate,
                    1,
                    Integer.MAX_VALUE);
                // P1-009：0 在现行语义中表示“不限”，迁移时必须保留 0，不能被钳到 1。
                int oldRounds = readLegacyCfgInt(configFile, "smartDoublingMaxRounds", smartDoublingMaxRounds, 0, Integer.MAX_VALUE);
                // P1-009：只有新 settings.json 确实写入成功，才允许删除旧 cfg，否则保留旧文件供下次重试。
                boolean migrated = writeFile(
                    oldRate,
                    oldRounds,
                    true,
                    pinRowEnabled,
                    stockMonitorPresets,
                    v7Textures);
                if (migrated && !Files.exists(SETTINGS_FILE)) {
                    migrated = false;
                }
                if (migrated) {
                    if (!configFile.delete() && configFile.exists()) {
                        MyMod.LOG.warn("[AE2QoL] legacy cfg could not be deleted; keeping it: {}", configFile);
                    }
                } else {
                    MyMod.LOG.warn("[AE2QoL] settings.json migration failed; keeping legacy cfg: {}", configFile);
                }
            }
            reload();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] settings.json init failed: " + t.getMessage());
        }
    }

    /**
     * 立即重新读取配置文件（同时供 {@code /ae2qof reload} 命令调用）。
     */
    public static synchronized void reload() {
        if (SETTINGS_FILE == null) {
            return;
        }
        int io = exIOPortTransferContentsRate;
        int rounds = smartDoublingMaxRounds;
        int pushCap = smartDoublingPushCap;
        boolean overlay = neiOverlayEnabled;
        boolean pinRow = pinRowEnabled;
        String presets = stockMonitorPresets;
        String v7 = v7Textures;
        try {
            if (!Files.exists(SETTINGS_FILE)) {
                writeFile(io, rounds, overlay, pinRow, presets, v7);
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(SETTINGS_FILE),
                StandardCharsets.UTF_8)) {
                JsonElement elem = GSON.fromJson(reader, JsonElement.class);
                if (elem != null && elem.isJsonObject()) {
                    JsonObject obj = elem.getAsJsonObject();
                    JsonElement value;
                    value = obj.get("io_port_rate");
                    if (value != null && value.isJsonPrimitive()) {
                        io = clamp(value.getAsInt(), 1, Integer.MAX_VALUE, io);
                    }
                    value = obj.get("smart_doubling_max_rounds");
                    if (value != null && value.isJsonPrimitive()) {
                        rounds = clamp(value.getAsInt(), 0, Integer.MAX_VALUE, rounds);
                    }
                    value = obj.get("smart_doubling_push_cap");
                    if (value != null && value.isJsonPrimitive()) {
                        pushCap = clamp(value.getAsInt(), 1, Integer.MAX_VALUE, pushCap);
                    }
                    value = obj.get("nei_overlay_enabled");
                    if (value != null && value.isJsonPrimitive()) {
                        overlay = value.getAsBoolean();
                    }
                    value = obj.get("pin_row_enabled");
                    if (value != null && value.isJsonPrimitive()) {
                        pinRow = value.getAsBoolean();
                    }
                    value = obj.get("stock_monitor_presets");
                    if (value != null && value.isJsonPrimitive()) {
                        presets = value.getAsString();
                    }
                    value = obj.get("v7_textures");
                    if (value != null && value.isJsonPrimitive()) {
                        String raw = value.getAsString();
                        if ("auto".equals(raw) || "on".equals(raw) || "off".equals(raw)) {
                            v7 = raw;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] Failed to parse settings.json: " + t.getMessage());
        }
        exIOPortTransferContentsRate = io;
        smartDoublingMaxRounds = rounds;
        smartDoublingPushCap = pushCap;
        neiOverlayEnabled = overlay;
        pinRowEnabled = pinRow;
        stockMonitorPresets = presets;
        v7Textures = v7;
        // 同步到贴图工具：auto/on → 0/1 自动判定与强制开，off → -1 强制关闭
        com.wztwzt.ae2_qof.util.ModTextures.forceMode = "off".equals(v7) ? -1 : ("on".equals(v7) ? 1 : 0);
        lastLoadedMtime = currentMtime();
    }

    /**
     * 热加载检查：每 1 秒最多校验一次文件修改时间，文件被外部修改则重新读取。
     * 在热路径（IO 端口传输、智能倍增计算）调用。
     */
    public static synchronized void ensureFresh() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < FRESH_INTERVAL_MS) {
            return;
        }
        lastCheck = now;
        if (SETTINGS_FILE != null && Files.exists(SETTINGS_FILE)) {
            long mtime = currentMtime();
            if (mtime != lastLoadedMtime) {
                reload();
            }
        }
    }

    /**
     * 保存 NEI 叠加层开关（保留 settings.json 中其它字段）。
     *
     * @param enabled 是否启用
     */
    public static synchronized void setNeiOverlayEnabled(boolean enabled) {
        neiOverlayEnabled = enabled;
        if (SETTINGS_FILE == null) {
            return;
        }
        writeFile(
            exIOPortTransferContentsRate,
            smartDoublingMaxRounds,
            enabled,
            pinRowEnabled,
            stockMonitorPresets,
            v7Textures);
        lastLoadedMtime = currentMtime();
    }

    /**
     * 按 key/value 应用配置（供游戏内配置 GUI / 服务端 C2S 处理复用）：
     * 校验 key 与取值范围，成功则更新字段、写回 settings.json 并刷新加载时间戳。
     *
     * @param key   配置键（io_port_rate / smart_doubling_max_rounds / nei_overlay_enabled）
     * @param value 新值
     * @return 是否成功应用
     */
    public static synchronized boolean applySetting(String key, String value) {
        if (key == null || value == null) {
            return false;
        }
        try {
            switch (key) {
                case "io_port_rate":
                    exIOPortTransferContentsRate = clamp(
                        Integer.parseInt(value.trim()),
                        1,
                        Integer.MAX_VALUE,
                        exIOPortTransferContentsRate);
                    break;
                case "smart_doubling_max_rounds":
                    smartDoublingMaxRounds = clamp(
                        Integer.parseInt(value.trim()),
                        0,
                        Integer.MAX_VALUE,
                        smartDoublingMaxRounds);
                    break;
                case "smart_doubling_push_cap":
                    smartDoublingPushCap = clamp(
                        Integer.parseInt(value.trim()),
                        1,
                        Integer.MAX_VALUE,
                        smartDoublingPushCap);
                    break;
                case "nei_overlay_enabled":
                    neiOverlayEnabled = Boolean.parseBoolean(value.trim());
                    break;
                case "pin_row_enabled":
                    pinRowEnabled = Boolean.parseBoolean(value.trim());
                    break;
                default:
                    return false;
            }
        } catch (Throwable t) {
            return false;
        }
        if (SETTINGS_FILE != null) {
            writeFile(
                exIOPortTransferContentsRate,
                smartDoublingMaxRounds,
                neiOverlayEnabled,
                pinRowEnabled,
                stockMonitorPresets,
                v7Textures);
            lastLoadedMtime = currentMtime();
        }
        return true;
    }

    /**
     * 全量应用服务端同步的配置（供 S2C 登录同步复用）：校验后更新字段、写回 settings.json 并刷新加载时间戳。
     * nei_overlay_enabled 为纯客户端渲染开关，不随服务端同步覆盖，保留客户端本地值（#48）。
     *
     * @param io     io_port_rate
     * @param rounds smart_doubling_max_rounds
     */
    public static synchronized void applyAll(int io, int rounds) {
        exIOPortTransferContentsRate = clamp(io, 1, Integer.MAX_VALUE, exIOPortTransferContentsRate);
        smartDoublingMaxRounds = clamp(rounds, 0, Integer.MAX_VALUE, smartDoublingMaxRounds);
        if (SETTINGS_FILE != null) {
            writeFile(
                exIOPortTransferContentsRate,
                smartDoublingMaxRounds,
                neiOverlayEnabled,
                pinRowEnabled,
                stockMonitorPresets,
                v7Textures);
            lastLoadedMtime = currentMtime();
        }
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private static int readLegacyCfgInt(File cfg, String key, int def, int min, int max) {
        try {
            if (cfg == null || !cfg.exists()) {
                return def;
            }
            Configuration configuration = new Configuration(cfg);
            configuration.load();
            int value = configuration.getInt(key, Configuration.CATEGORY_GENERAL, def, min, max, "");
            configuration.save();
            return value;
        } catch (Throwable ignored) {
            return def;
        }
    }

    /**
     * 写入 settings.json。
     *
     * @return 是否写入成功（P1-009：迁移逻辑依赖该返回值决定是否删除旧 cfg）
     */
    private static boolean writeFile(int ioRate, int rounds, boolean overlay, boolean pinRow, String presets,
        String v7) {
        // 3.21.4：新增字段（smart_doubling_push_cap）走重载，既有 5 处调用点的签名保持不变，降低回归面。
        return writeFile(ioRate, rounds, overlay, pinRow, presets, v7, smartDoublingPushCap);
    }

    private static boolean writeFile(int ioRate, int rounds, boolean overlay, boolean pinRow, String presets,
        String v7, int pushCap) {
        try {
            Path parent = SETTINGS_FILE.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            JsonObject root = new JsonObject();
            root.addProperty("io_port_rate", ioRate);
            root.addProperty("smart_doubling_max_rounds", rounds);
            root.addProperty("smart_doubling_push_cap", pushCap);
            root.addProperty("nei_overlay_enabled", overlay);
            root.addProperty("pin_row_enabled", pinRow);
            root.addProperty("stock_monitor_presets", presets);
            root.addProperty("v7_textures", v7);
            try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(SETTINGS_FILE),
                StandardCharsets.UTF_8)) {
                writer.write(GSON.toJson(root));
            }
            return true;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] Failed to write settings.json: " + t.getMessage());
            return false;
        }
    }

    private static long currentMtime() {
        try {
            return Files.getLastModifiedTime(SETTINGS_FILE)
                .toMillis();
        } catch (Throwable ignored) {
            return -1L;
        }
    }
}
