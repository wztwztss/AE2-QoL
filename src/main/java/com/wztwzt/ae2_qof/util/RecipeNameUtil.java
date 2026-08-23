package com.wztwzt.ae2_qof.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import net.minecraft.util.StatCollector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.Loader;

/**
 * Recipe name mapping utility for 1.7.10.
 * Client-only NEI methods have been moved to ClientRecipeNameUtil.
 */
public final class RecipeNameUtil {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public static final Pattern CAMEL_CASE_SPLITTER = Pattern.compile("(?<!^)([A-Z])");

    private static final Map<String, String> RAW_MAPPINGS = new HashMap<String, String>();
    private static final Map<String, String> LOOKUP_MAPPINGS = new HashMap<String, String>();

    private static Path CONFIG_FILE;

    private static String lastRecipeName = null;
    private static String lastRawRecipeId = null;

    static {
        try {
            Path configDir = Loader.instance()
                .getConfigDir()
                .toPath();
            CONFIG_FILE = configDir.resolve("ae2_qof")
                .resolve("recipe_names.json");
            loadMappings();
        } catch (Throwable t) {
            MyMod.LOG.warn("[APU] RecipeNameUtil init failed: " + t.getMessage());
        }
    }

    private RecipeNameUtil() {}

    public static synchronized void setLastRecipeName(String name) {
        lastRecipeName = name;
    }

    public static synchronized void setLastRawRecipeId(String rawId) {
        lastRawRecipeId = rawId;
    }

    public static synchronized String getLastRecipeName() {
        return lastRecipeName;
    }

    public static synchronized String getLastRawRecipeId() {
        return lastRawRecipeId;
    }

    public static synchronized void clearLastRecipeName() {
        lastRecipeName = null;
        lastRawRecipeId = null;
    }

    public static synchronized boolean addOrUpdateMapping(String key, String value) {
        if (key == null || key.trim()
            .isEmpty()
            || value == null
            || value.trim()
                .isEmpty()) {
            return false;
        }
        RAW_MAPPINGS.put(key.trim(), value.trim());
        LOOKUP_MAPPINGS.put(normalizeKey(key), value.trim());
        saveMappings();
        com.wztwzt.ae2_qof.common.RecipeMapNameConfig.reload();
        return true;
    }

    public static synchronized int removeMappingsByCnValue(String cnValue) {
        if (cnValue == null || cnValue.trim()
            .isEmpty()) {
            return 0;
        }
        String target = cnValue.trim();
        int removed = 0;
        Iterator<Map.Entry<String, String>> iterator = RAW_MAPPINGS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (Objects.equals(entry.getValue(), target)) {
                iterator.remove();
                LOOKUP_MAPPINGS.remove(normalizeKey(entry.getKey()));
                removed++;
            }
        }
        if (removed > 0) {
            saveMappings();
            com.wztwzt.ae2_qof.common.RecipeMapNameConfig.reload();
        }
        return removed;
    }

    /**
     * 按 key 删除单条映射（供游戏内配置页面编辑列表选中项使用）。
     *
     * @param key 配方 key
     * @return 是否确实删除了该项
     */
    public static synchronized boolean removeMappingByKey(String key) {
        if (key == null || key.trim()
            .isEmpty()) {
            return false;
        }
        String k = key.trim();
        if (RAW_MAPPINGS.containsKey(k)) {
            RAW_MAPPINGS.remove(k);
            LOOKUP_MAPPINGS.remove(normalizeKey(k));
            saveMappings();
            com.wztwzt.ae2_qof.common.RecipeMapNameConfig.reload();
            return true;
        }
        return false;
    }

    public static synchronized void reloadMappings() {
        loadMappings();
    }

    public static synchronized Map<String, String> getMappingsView() {
        return Collections.unmodifiableMap(RAW_MAPPINGS);
    }

    public static synchronized Map<String, String> getLookupMappingsView() {
        return Collections.unmodifiableMap(LOOKUP_MAPPINGS);
    }

    /**
     * Map a raw string to a user-defined mapping. Used by ClientRecipeNameUtil.
     */
    public static String mapStringToMapping(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            return null;
        }
        String normalized = normalizeKey(raw);
        String mapped = LOOKUP_MAPPINGS.get(normalized);
        if (mapped != null && !mapped.isEmpty()) {
            return mapped;
        }
        return null;
    }

    private static synchronized void loadMappings() {
        RAW_MAPPINGS.clear();
        LOOKUP_MAPPINGS.clear();

        if (CONFIG_FILE == null) {
            return;
        }

        loadBuiltinDefaults();

        if (!Files.exists(CONFIG_FILE)) {
            writeTemplate();
        }

        try (InputStreamReader reader = new InputStreamReader(
            Files.newInputStream(CONFIG_FILE),
            StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.trim()
                    .isEmpty()) {
                    continue;
                }
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    String mapped = value.getAsString();
                    if (mapped != null && !mapped.trim()
                        .isEmpty()) {
                        RAW_MAPPINGS.put(key.trim(), mapped.trim());
                        LOOKUP_MAPPINGS.put(normalizeKey(key), mapped.trim());
                    }
                }
            }
        } catch (IOException e) {
            MyMod.LOG.warn(StatCollector.translateToLocalFormatted("ae2_qof.error.read_mappings", e.getMessage()));
        }
        MyMod.LOG.info("[APU] Loaded " + RAW_MAPPINGS.size() + " recipe mappings from " + CONFIG_FILE);
    }

    /**
     * Load built-in default mappings from the jar resource.
     */
    private static void loadBuiltinDefaults() {
        try {
            java.io.InputStream is = RecipeNameUtil.class.getResourceAsStream("/apu/recipe_type_names.json");
            if (is == null) {
                return;
            }
            try {
                InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                reader.close();
                if (obj != null) {
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        String key = entry.getKey();
                        if (key == null || key.trim()
                            .isEmpty()) {
                            continue;
                        }
                        JsonElement value = entry.getValue();
                        if (value != null && value.isJsonPrimitive()) {
                            String mapped = value.getAsString();
                            if (mapped != null && !mapped.trim()
                                .isEmpty()) {
                                RAW_MAPPINGS.put(key.trim(), mapped.trim());
                                LOOKUP_MAPPINGS.put(normalizeKey(key), mapped.trim());
                            }
                        }
                    }
                    MyMod.LOG.info(
                        "[APU] Loaded " + obj.entrySet()
                            .size() + " built-in default recipe mappings");
                }
            } finally {
                is.close();
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[APU] Failed to load built-in recipe mappings: " + t.getMessage());
        }
    }

    private static void writeTemplate() {
        JsonObject template = new JsonObject();

        try {
            Path parent = CONFIG_FILE.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(CONFIG_FILE),
                StandardCharsets.UTF_8)) {
                writer.write(GSON.toJson(template));
            }
        } catch (IOException e) {
            MyMod.LOG.warn(StatCollector.translateToLocalFormatted("ae2_qof.error.create_template", e.getMessage()));
        }
    }

    private static void saveMappings() {
        if (CONFIG_FILE == null) {
            return;
        }
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, String> entry : RAW_MAPPINGS.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        try {
            Path parent = CONFIG_FILE.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(CONFIG_FILE),
                StandardCharsets.UTF_8)) {
                writer.write(GSON.toJson(obj));
            }
        } catch (IOException e) {
            MyMod.LOG.warn(StatCollector.translateToLocalFormatted("ae2_qof.error.write_mappings", e.getMessage()));
        }
    }

    public static String mapCategoryUidToSearchKey(String categoryUid) {
        if (categoryUid == null || categoryUid.isEmpty()) {
            return null;
        }
        String normalized = categoryUid.trim()
            .toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        int dot = normalized.indexOf('.');
        String path;
        if (colon >= 0) {
            path = normalized.substring(colon + 1);
        } else if (dot >= 0) {
            path = normalized.substring(dot + 1);
        } else {
            path = normalized;
        }
        String mapped = LOOKUP_MAPPINGS.get(path);
        if (mapped != null && !mapped.isEmpty()) {
            return mapped;
        }
        return toDisplayString(path);
    }

    /**
     * 枚举当前环境中全部 GT 配方池 UID（如 gt.recipe.compressor、gtpp.recipe.multiblockrockbreaker）。
     * 与自动上传的 scanRecipeMaps 同源：反射 {@code gregtech.api.recipe.RecipeMap.ALL_RECIPE_MAPS} 的
     * {@code unlocalizedName}。用于游戏内配置页「配方参考」子页，能覆盖整合包内的全部机器类型。
     * GT 缺失或反射失败时返回空列表。
     *
     * @return 去重排序后的配方池 UID 列表
     */
    public static synchronized List<String> getAllRecipeMapUids() {
        java.util.Set<String> uids = new java.util.TreeSet<String>();
        try {
            Class<?> recipeMapClass = Class.forName("gregtech.api.recipe.RecipeMap");
            java.lang.reflect.Field allMapsField = recipeMapClass.getField("ALL_RECIPE_MAPS");
            java.util.Map<?, ?> allMaps = (java.util.Map<?, ?>) allMapsField.get(null);
            if (allMaps == null) {
                return Collections.emptyList();
            }
            java.lang.reflect.Field nameField = recipeMapClass.getField("unlocalizedName");
            for (Object map : allMaps.values()) {
                try {
                    String name = (String) nameField.get(map);
                    if (name != null && !name.trim()
                        .isEmpty()) {
                        uids.add(name.trim());
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[APU] Failed to enumerate GT recipe maps: " + t.getMessage());
        }
        return new ArrayList<String>(uids);
    }

    public static String deriveSearchKeyFromClassName(Object recipeObj) {
        if (recipeObj == null) {
            return null;
        }
        try {
            String simpleName = recipeObj.getClass()
                .getSimpleName();
            String packageName = recipeObj.getClass()
                .getPackage()
                .getName()
                .toLowerCase(Locale.ROOT);

            String token = CAMEL_CASE_SPLITTER.matcher(simpleName)
                .replaceAll(" $1")
                .replace("_", " ")
                .replace("-", " ")
                .trim()
                .toLowerCase(Locale.ROOT);

            token = token.replace(" recipe", "")
                .replace(" handler", "")
                .trim();

            String namespace = null;
            if (packageName.contains("gregtech")) {
                namespace = "gregtech";
            } else if (packageName.contains("gtceu")) {
                namespace = "gtceu";
            } else if (packageName.contains("thermal")) {
                namespace = "thermal";
            } else if (packageName.contains("botania")) {
                namespace = "botania";
            } else if (packageName.contains("immersive")) {
                namespace = "immersive";
            }

            if (namespace != null && !token.isEmpty()) {
                return namespace + " " + token;
            }
            if (!token.isEmpty()) {
                return token;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    static String normalizeKey(String key) {
        String normalized = key.trim()
            .toLowerCase(Locale.ROOT)
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(':', ' ')
            .replaceAll("\\s+", " ")
            .trim();
        return normalized;
    }

    static String toDisplayString(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace('_', ' ')
            .replace('-', ' ')
            .replace('.', ' ')
            .replace(':', ' ');
        cleaned = CAMEL_CASE_SPLITTER.matcher(cleaned)
            .replaceAll(" $1");
        cleaned = cleaned.replaceAll("\\s+", " ")
            .trim();
        return cleaned;
    }
}
