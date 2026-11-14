package com.wztwzt.ae2_qof;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

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
 * —— 受 AE2 功率探测（凑够即停的遍历，超额查询会走遍全部储能设备）与物品更新量限制，
 * 默认 4096 是审计项 #51（O(P) 探测）与 #73（大订单客户端被海量更新淹没）修复时定下的安全值；
 * 服务器算力充裕时可调大，代价是每 tick 的提取量与物品更新量线性上升。
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

    /**
     * 智能通配样板单张最大展开候选数（热加载字段，3.22.0）。
     *
     * <p>
     * 为什么必须有上限：通配样板是在**宿主建样板索引时**把规则展开成 N 张各自合法的普通样板
     * （逐候选克隆 + 改写原生 in/out NBT），这条路径由网络钩子同步触发，且每个候选一份完整样板对象。
     * 参考实现（Wildcard Pattern）在这条路径上无上界展开，是明确的卡服点；
     * 本模组改为「有上限 + 超限可见 + 记日志」，绝不做静默截断。
     */
    public static volatile int smartWildcardExpandCap = 512;

    /** NEI 叠加层开关（热加载字段）。 */
    public static volatile boolean neiOverlayEnabled = true;

    /**
     * NEI 转移后自动编码并尝试上传（热加载字段，默认 false）。
     *
     * <p>
     * 开启后：NEI「+」把配方填入二合一终端后，服务端立即编码；客户端在编码产物同步到输出槽后
     * 调用现有上传管线。上传仍是原有语义：唯一候选/持久映射命中自动放置，多候选打开选择界面。
     * 默认关闭，避免玩家只想填配方时被自动编码/上传打断。
     */
    public static volatile boolean neiAutoUploadEnabled = false;

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

    /**
     * 库存检测覆盖板真正读取 AE2 网络的间隔（tick，热加载字段，性能修复版）。
     * 每次读取会把整个 ME 网络的存储列表物化一遍（大网络开销极高），默认 20t（1 秒）
     * 比旧版每 10t×每槽一次降低一个数量级；其余 tick 直接使用缓存值。
     * 覆盖板 NBT 的 ReadInterval>0 时按覆盖板自身值生效。
     */
    public static volatile int stockMonitorReadIntervalTicks = 20;

    /**
     * 库存检测覆盖板 GUI 打开时的读取间隔（tick，热加载字段）。
     * GUI 打开时（服务端检测到 MUI sync supplier 正在被轮询）取 min(常规间隔, 本值)。
     */
    public static volatile int stockMonitorGuiIntervalTicks = 5;

    /**
     * ME 任务检测器的检测间隔（tick，热加载字段，性能修复版）。
     * 每轮检测要全量快照网络库存并喂给 BQ 检索任务，默认 80t（4 秒）；
     * 任务完成反馈最多延迟一个周期，数值本身不变。
     */
    public static volatile int questDetectIntervalTicks = 80;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private static final long FRESH_INTERVAL_MS = 1000L;

    private static Path SETTINGS_FILE;

    /** 最近一次解析 settings.json 的错误；供 /ae2qof status 暴露“改了但没加载”的原因。 */
    private static volatile String lastReloadError = "";

    /** 最近一次写入 settings.json 的错误；与解析错误分开，避免“写失败”被误判成“值没变”。 */
    private static volatile String lastWriteError = "";

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
                int oldRounds = readLegacyCfgInt(
                    configFile,
                    "smartDoublingMaxRounds",
                    smartDoublingMaxRounds,
                    0,
                    Integer.MAX_VALUE);
                // P1-009：只有新 settings.json 确实写入成功，才允许删除旧 cfg，否则保留旧文件供下次重试。
                boolean migrated = writeFile(oldRate, oldRounds, true, pinRowEnabled, stockMonitorPresets, v7Textures);
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
     * 当前配置文件的绝对路径；未初始化时返回提示串。用于状态命令和 issue 诊断，
     * 避免玩家把旧 config/ae2_qof.cfg 当成现行配置文件。
     */
    public static String getSettingsPath() {
        return SETTINGS_FILE == null ? "<not initialized>"
            : SETTINGS_FILE.toAbsolutePath()
                .toString();
    }

    /** 最近一次 settings.json 解析失败原因；空串表示最近一次加载成功。 */
    public static String getLastReloadError() {
        return lastReloadError;
    }

    /** 最近一次 settings.json 写入失败原因；空串表示最近一次写入成功。 */
    public static String getLastWriteError() {
        return lastWriteError;
    }

    /**
     * 立即重新读取配置文件（同时供 {@code /ae2qof reload} 命令调用）。
     */
    public static synchronized void reload() {
        if (SETTINGS_FILE == null) {
            lastReloadError = "settings.json 未初始化";
            return;
        }
        int io = exIOPortTransferContentsRate;
        int rounds = smartDoublingMaxRounds;
        int pushCap = smartDoublingPushCap;
        int wildcardCap = smartWildcardExpandCap;
        boolean overlay = neiOverlayEnabled;
        boolean autoUpload = neiAutoUploadEnabled;
        boolean pinRow = pinRowEnabled;
        String presets = stockMonitorPresets;
        String v7 = v7Textures;
        int smRead = stockMonitorReadIntervalTicks;
        int smGui = stockMonitorGuiIntervalTicks;
        int quest = questDetectIntervalTicks;
        try {
            if (!Files.exists(SETTINGS_FILE)) {
                boolean ok = writeFile(io, rounds, overlay, pinRow, presets, v7);
                if (ok) {
                    lastReloadError = "";
                } else {
                    lastReloadError = lastWriteError.isEmpty() ? "settings.json 缺失且默认写入失败" : lastWriteError;
                    lastLoadedMtime = currentMtime();
                }
                return;
            }
            String text = readSettingsText();
            JsonElement elem = GSON.fromJson(text, JsonElement.class);
            if (elem == null || !elem.isJsonObject()) {
                throw new IllegalStateException("root is not a JSON object");
            }
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
            value = obj.get("smart_wildcard_expand_cap");
            if (value != null && value.isJsonPrimitive()) {
                wildcardCap = clamp(value.getAsInt(), 1, Integer.MAX_VALUE, wildcardCap);
            }
            value = obj.get("nei_overlay_enabled");
            if (value != null && value.isJsonPrimitive()) {
                overlay = value.getAsBoolean();
            }
            value = obj.get("nei_auto_upload_enabled");
            if (value != null && value.isJsonPrimitive()) {
                autoUpload = value.getAsBoolean();
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
            value = obj.get("stock_monitor_read_interval");
            if (value != null && value.isJsonPrimitive()) {
                smRead = clamp(value.getAsInt(), 5, 1200, smRead);
            }
            value = obj.get("stock_monitor_gui_interval");
            if (value != null && value.isJsonPrimitive()) {
                smGui = clamp(value.getAsInt(), 2, 40, smGui);
            }
            value = obj.get("quest_detect_interval");
            if (value != null && value.isJsonPrimitive()) {
                quest = clamp(value.getAsInt(), 20, 1200, quest);
            }
            // 只有整份文件解析成功后才提交，避免坏字段导致“前面的字段已生效、后面的还是旧值”。
            exIOPortTransferContentsRate = io;
            smartDoublingMaxRounds = rounds;
            smartDoublingPushCap = pushCap;
            smartWildcardExpandCap = wildcardCap;
            neiOverlayEnabled = overlay;
            neiAutoUploadEnabled = autoUpload;
            pinRowEnabled = pinRow;
            stockMonitorPresets = presets;
            v7Textures = v7;
            stockMonitorReadIntervalTicks = smRead;
            stockMonitorGuiIntervalTicks = smGui;
            questDetectIntervalTicks = quest;
            // 同步到贴图工具：auto/on → 0/1 自动判定与强制开，off → -1 强制关闭
            com.wztwzt.ae2_qof.util.ModTextures.forceMode = "off".equals(v7) ? -1 : ("on".equals(v7) ? 1 : 0);
            lastReloadError = "";
            lastWriteError = "";
            lastLoadedMtime = currentMtime();
        } catch (Throwable t) {
            lastReloadError = t.getMessage() == null ? t.toString() : t.getMessage();
            MyMod.LOG.warn("[AE2QoL] Failed to parse settings.json (" + SETTINGS_FILE + "): " + lastReloadError);
            lastLoadedMtime = currentMtime();
        }
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
     * 保存 NEI 叠加层开关：只改 settings.json 的这一个键，保留文件里的其它字段和未知键。
     *
     * @param enabled 是否启用
     * @return 是否成功写入；失败时内存值不改变，供 GUI 显示错误
     */
    public static synchronized boolean setNeiOverlayEnabled(boolean enabled) {
        Map<String, Object> updates = new LinkedHashMap<String, Object>();
        updates.put("nei_overlay_enabled", Boolean.valueOf(enabled));
        if (!writeSettings(updates, true)) {
            return false;
        }
        neiOverlayEnabled = enabled;
        return true;
    }

    /**
     * 按 key/value 应用配置（供游戏内配置 GUI / 服务端 C2S 处理复用）：
     * 先校验并读取当前文件、只改目标键并原子写回；写入成功后才更新内存字段。
     *
     * @param key   配置键（io_port_rate / smart_doubling_max_rounds / nei_overlay_enabled 等）
     * @param value 新值
     * @return 是否成功应用；写入失败或值非法时返回 false，内存值保持不变
     */
    public static synchronized boolean applySetting(String key, String value) {
        if (key == null || value == null) {
            return false;
        }
        // 清掉上一次写入错误，避免“值非法”时把旧错误当成本次原因回给客户端。
        lastWriteError = "";
        String trimmed = value.trim();
        Object newValue;
        try {
            switch (key) {
                case "io_port_rate":
                    newValue = parseIntInRange(trimmed, 1, Integer.MAX_VALUE);
                    break;
                case "smart_doubling_max_rounds":
                    newValue = parseIntInRange(trimmed, 0, Integer.MAX_VALUE);
                    break;
                case "smart_doubling_push_cap":
                    newValue = parseIntInRange(trimmed, 1, Integer.MAX_VALUE);
                    break;
                case "smart_wildcard_expand_cap":
                    newValue = parseIntInRange(trimmed, 1, Integer.MAX_VALUE);
                    break;
                case "nei_overlay_enabled":
                    newValue = parseBooleanStrict(trimmed);
                    break;
                case "nei_auto_upload_enabled":
                    newValue = parseBooleanStrict(trimmed);
                    break;
                case "pin_row_enabled":
                    newValue = parseBooleanStrict(trimmed);
                    break;
                case "stock_monitor_read_interval":
                    newValue = parseIntInRange(trimmed, 5, 1200);
                    break;
                case "stock_monitor_gui_interval":
                    newValue = parseIntInRange(trimmed, 2, 40);
                    break;
                case "quest_detect_interval":
                    newValue = parseIntInRange(trimmed, 20, 1200);
                    break;
                default:
                    return false;
            }
        } catch (Throwable t) {
            return false;
        }
        if (newValue == null) {
            return false;
        }
        Map<String, Object> updates = new LinkedHashMap<String, Object>();
        updates.put(key, newValue);
        if (!writeSettings(updates, true)) {
            return false;
        }
        switch (key) {
            case "io_port_rate":
                exIOPortTransferContentsRate = ((Integer) newValue).intValue();
                break;
            case "smart_doubling_max_rounds":
                smartDoublingMaxRounds = ((Integer) newValue).intValue();
                break;
            case "smart_doubling_push_cap":
                smartDoublingPushCap = ((Integer) newValue).intValue();
                break;
            case "smart_wildcard_expand_cap":
                smartWildcardExpandCap = ((Integer) newValue).intValue();
                break;
            case "nei_overlay_enabled":
                neiOverlayEnabled = ((Boolean) newValue).booleanValue();
                break;
            case "nei_auto_upload_enabled":
                neiAutoUploadEnabled = ((Boolean) newValue).booleanValue();
                break;
            case "pin_row_enabled":
                pinRowEnabled = ((Boolean) newValue).booleanValue();
                break;
            case "stock_monitor_read_interval":
                stockMonitorReadIntervalTicks = ((Integer) newValue).intValue();
                break;
            case "stock_monitor_gui_interval":
                stockMonitorGuiIntervalTicks = ((Integer) newValue).intValue();
                break;
            case "quest_detect_interval":
                questDetectIntervalTicks = ((Integer) newValue).intValue();
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * 全量应用服务端同步的配置（供 S2C 登录同步复用）：只更新 io/rounds 两个键，
     * 保留文件里的客户端本地 NEI 开关、auto-upload 等其它字段；写入成功后才改内存。
     *
     * @param io     io_port_rate
     * @param rounds smart_doubling_max_rounds
     * @return 是否写入成功
     */
    public static synchronized boolean applyAll(int io, int rounds) {
        int newIo = clamp(io, 1, Integer.MAX_VALUE, exIOPortTransferContentsRate);
        int newRounds = clamp(rounds, 0, Integer.MAX_VALUE, smartDoublingMaxRounds);
        Map<String, Object> updates = new LinkedHashMap<String, Object>();
        updates.put("io_port_rate", Integer.valueOf(newIo));
        updates.put("smart_doubling_max_rounds", Integer.valueOf(newRounds));
        if (!writeSettings(updates, true)) {
            return false;
        }
        exIOPortTransferContentsRate = newIo;
        smartDoublingMaxRounds = newRounds;
        return true;
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

    private static Integer parseIntInRange(String value, int min, int max) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= min && parsed <= max ? Integer.valueOf(parsed) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Boolean parseBooleanStrict(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** 读取 settings.json 的文本并剥离 BOM；UTF-8 优先，兼容 Windows 记事本另存的 GBK/ANSI。 */
    private static String readSettingsText() throws IOException {
        byte[] bytes = Files.readAllBytes(SETTINGS_FILE);
        int offset = 0;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            offset = 3;
        }
        byte[] body = offset == 0 ? bytes : Arrays.copyOfRange(bytes, offset, bytes.length);
        String text;
        try {
            text = decodeStrict(body, StandardCharsets.UTF_8);
        } catch (CharacterCodingException utf8Error) {
            try {
                text = decodeStrict(body, Charset.forName("GBK"));
            } catch (Throwable gbkError) {
                throw new IOException("settings.json 既不是 UTF-8 也不是 GBK", utf8Error);
            }
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text;
    }

    private static String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
    }

    /** 内存中的完整设置对象；仅用于 settings.json 缺失时生成新文件。 */
    private static JsonObject currentSettingsObject() {
        JsonObject root = new JsonObject();
        root.addProperty("io_port_rate", exIOPortTransferContentsRate);
        root.addProperty("smart_doubling_max_rounds", smartDoublingMaxRounds);
        root.addProperty("smart_doubling_push_cap", smartDoublingPushCap);
        root.addProperty("smart_wildcard_expand_cap", smartWildcardExpandCap);
        root.addProperty("nei_overlay_enabled", neiOverlayEnabled);
        root.addProperty("nei_auto_upload_enabled", neiAutoUploadEnabled);
        root.addProperty("pin_row_enabled", pinRowEnabled);
        root.addProperty("stock_monitor_presets", stockMonitorPresets);
        root.addProperty("v7_textures", v7Textures);
        root.addProperty("stock_monitor_read_interval", stockMonitorReadIntervalTicks);
        root.addProperty("stock_monitor_gui_interval", stockMonitorGuiIntervalTicks);
        root.addProperty("quest_detect_interval", questDetectIntervalTicks);
        return root;
    }

    private static void setJsonValue(JsonObject root, String key, Object value) {
        if (value instanceof Boolean) {
            root.addProperty(key, (Boolean) value);
        } else if (value instanceof Number) {
            root.addProperty(key, (Number) value);
        } else {
            root.addProperty(key, String.valueOf(value));
        }
    }

    /**
     * 读取当前文件（保留未知键和未修改字段），只更新指定键，再写临时文件并原子替换。
     * 失败时不动原文件，也不更新加载时间戳；调用方据此决定是否提交内存值。
     */
    private static boolean writeSettings(Map<String, Object> updates, boolean preserveExisting) {
        if (SETTINGS_FILE == null) {
            lastWriteError = "settings.json 未初始化";
            return false;
        }
        Path tmp = SETTINGS_FILE.resolveSibling(
            SETTINGS_FILE.getFileName()
                .toString() + ".tmp");
        try {
            JsonObject root;
            if (preserveExisting) {
                if (Files.exists(SETTINGS_FILE)) {
                    JsonElement elem = GSON.fromJson(readSettingsText(), JsonElement.class);
                    if (elem == null || !elem.isJsonObject()) {
                        throw new IllegalStateException("root is not a JSON object");
                    }
                    root = elem.getAsJsonObject();
                } else {
                    root = currentSettingsObject();
                }
            } else {
                root = new JsonObject();
            }
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                setJsonValue(root, entry.getKey(), entry.getValue());
            }
            Path parent = SETTINGS_FILE.toAbsolutePath()
                .getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.write(
                tmp,
                GSON.toJson(root)
                    .getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            try {
                Files.move(tmp, SETTINGS_FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, SETTINGS_FILE, StandardCopyOption.REPLACE_EXISTING);
            } catch (FileAlreadyExistsException e) {
                Files.move(tmp, SETTINGS_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            lastWriteError = "";
            lastReloadError = "";
            lastLoadedMtime = currentMtime();
            return true;
        } catch (Throwable t) {
            lastWriteError = t.getMessage() == null ? t.toString() : t.getMessage();
            MyMod.LOG.warn("[AE2QoL] Failed to write settings.json (" + SETTINGS_FILE + "): " + lastWriteError);
            return false;
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 全量写入 settings.json（仅用于迁移/首次生成），保留旧调用点签名。
     *
     * @return 是否写入成功（P1-009：迁移逻辑依赖该返回值决定是否删除旧 cfg）
     */
    private static boolean writeFile(int ioRate, int rounds, boolean overlay, boolean pinRow, String presets,
        String v7) {
        // 3.21.4：新增字段（smart_doubling_push_cap）走重载，既有 5 处调用点的签名保持不变，降低回归面。
        return writeFile(ioRate, rounds, overlay, pinRow, presets, v7, smartDoublingPushCap);
    }

    private static boolean writeFile(int ioRate, int rounds, boolean overlay, boolean pinRow, String presets, String v7,
        int pushCap) {
        // 3.22.0：同样用委托，新增字段（smart_wildcard_expand_cap）从静态字段取值，调用点保持不动。
        return writeFile(ioRate, rounds, overlay, pinRow, presets, v7, pushCap, smartWildcardExpandCap);
    }

    private static boolean writeFile(int ioRate, int rounds, boolean overlay, boolean pinRow, String presets, String v7,
        int pushCap, int wildcardCap) {
        // 性能修复版：节流字段同样从静态字段取值，既有调用点签名不动。
        return writeFile(
            ioRate,
            rounds,
            overlay,
            pinRow,
            presets,
            v7,
            pushCap,
            wildcardCap,
            stockMonitorReadIntervalTicks,
            stockMonitorGuiIntervalTicks,
            questDetectIntervalTicks);
    }

    private static boolean writeFile(int ioRate, int rounds, boolean overlay, boolean pinRow, String presets, String v7,
        int pushCap, int wildcardCap, int smRead, int smGui, int quest) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("io_port_rate", Integer.valueOf(ioRate));
        values.put("smart_doubling_max_rounds", Integer.valueOf(rounds));
        values.put("smart_doubling_push_cap", Integer.valueOf(pushCap));
        values.put("smart_wildcard_expand_cap", Integer.valueOf(wildcardCap));
        values.put("nei_overlay_enabled", Boolean.valueOf(overlay));
        values.put("nei_auto_upload_enabled", Boolean.valueOf(neiAutoUploadEnabled));
        values.put("pin_row_enabled", Boolean.valueOf(pinRow));
        values.put("stock_monitor_presets", presets);
        values.put("v7_textures", v7);
        values.put("stock_monitor_read_interval", Integer.valueOf(smRead));
        values.put("stock_monitor_gui_interval", Integer.valueOf(smGui));
        values.put("quest_detect_interval", Integer.valueOf(quest));
        return writeSettings(values, false);
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
