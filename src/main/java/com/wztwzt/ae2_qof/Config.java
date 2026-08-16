package com.wztwzt.ae2_qof;

import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraftforge.common.config.Configuration;

/**
 * 统一玩家配置文件 {@code config/ae2_qof/settings.json}：
 * - io_port_rate：强化 IO 端口传输倍率（默认 1024，1..Integer.MAX_VALUE）
 * - smart_doubling_max_rounds：智能倍增最大轮数（默认 64，1..4096）
 * - nei_overlay_enabled：NEI 叠加层开关（默认 true）
 *
 * 支持热加载：直接编辑文件后约 1 秒内自动生效（服务端/单机均可），
 * 也可用 OP 命令 {@code /ae2qof reload} 立即重载。
 */
public class Config {

    /** 强化 IO 端口传输倍率（热加载字段）。 */
    public static volatile int exIOPortTransferContentsRate = 1024;

    /** 智能倍增最大轮数（热加载字段）。 */
    public static volatile int smartDoublingMaxRounds = 64;

    /** NEI 叠加层开关（热加载字段）。 */
    public static volatile boolean neiOverlayEnabled = true;

    public static final String greeting = "Hello World";

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
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
                int oldRounds = readLegacyCfgInt(
                    configFile,
                    "smartDoublingMaxRounds",
                    smartDoublingMaxRounds,
                    1,
                    4096);
                writeFile(oldRate, oldRounds, true);
                configFile.delete();
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
        boolean overlay = neiOverlayEnabled;
        try {
            if (!Files.exists(SETTINGS_FILE)) {
                writeFile(io, rounds, overlay);
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
                        rounds = clamp(value.getAsInt(), 1, 4096, rounds);
                    }
                    value = obj.get("nei_overlay_enabled");
                    if (value != null && value.isJsonPrimitive()) {
                        overlay = value.getAsBoolean();
                    }
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] Failed to parse settings.json: " + t.getMessage());
        }
        exIOPortTransferContentsRate = io;
        smartDoublingMaxRounds = rounds;
        neiOverlayEnabled = overlay;
        lastLoadedMtime = currentMtime();
    }

    /**
     * 热加载检查：每 1 秒最多校验一次文件修改时间，文件被外部修改则重新读取。
     * 在热路径（IO 端口传输、智能倍增计算）调用。
     */
    public static void ensureFresh() {
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
        writeFile(exIOPortTransferContentsRate, smartDoublingMaxRounds, enabled);
        lastLoadedMtime = currentMtime();
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

    private static void writeFile(int ioRate, int rounds, boolean overlay) {
        try {
            Path parent = SETTINGS_FILE.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            JsonObject root = new JsonObject();
            root.addProperty("io_port_rate", ioRate);
            root.addProperty("smart_doubling_max_rounds", rounds);
            root.addProperty("nei_overlay_enabled", overlay);
            try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(SETTINGS_FILE),
                StandardCharsets.UTF_8)) {
                writer.write(GSON.toJson(root));
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] Failed to write settings.json: " + t.getMessage());
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