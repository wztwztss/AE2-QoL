package com.wztwzt.ae2_qof.client;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.Loader;

public class ClientState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public static final Map<String, String> rememberedProviders = new HashMap<String, String>();

    private static final Path REMEMBERED_FILE;

    static {
        Path configDir = Loader.instance()
            .getConfigDir()
            .toPath();
        REMEMBERED_FILE = configDir.resolve("ae2_qof/remembered_providers.json");
        loadRemembered();
    }

    public static String lastProviderName = null;
    public static long lastProviderId = 0;
    public static String lastRecipeMap = null;

    public static String pendingRecipeMap = null;

    /** NEI 配方处理器的中文名称（getRecipeName），供搜索框自动填入兜底 */
    public static String pendingRecipeCnName = null;

    /** 二合一终端编码成功后回传的机器中文名（服务端→客户端即时反馈） */
    public static volatile String mergedMachineName = null;

    /** 二合一终端网络内空白样板总数量（服务端→客户端推送，用于空白槽数量角标） */
    public static volatile long mergedBlankCount = 0;

    /** 二合一终端 Shift+滚轮 替换候选列表（服务端→客户端回传，tooltip 预览用） */
    public static volatile java.util.List<net.minecraft.item.ItemStack> replaceCandidates = null;

    /** 候选列表中当前槽内物品的位置（-1 表示不在其中或未知） */
    public static volatile int replaceCurrentIndex = -1;

    public static boolean highlightEnabled = false;
    public static java.util.List<int[]> highlightPositions = new java.util.ArrayList<int[]>();

    public static java.util.List<int[]> adaptiveHighlightPositions = new java.util.ArrayList<int[]>();
    public static long adaptiveHighlightExpiryTick = 0;

    public static volatile com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache hatchListCache = null;

    public static void clear() {
        lastProviderName = null;
        lastProviderId = 0;
        lastRecipeMap = null;
        pendingRecipeCnName = null;
    }

    public static void set(String name, long id) {
        lastProviderName = name;
        lastProviderId = id;
    }

    public static void rememberProvider(String recipeMap, String providerName) {
        if (recipeMap != null && providerName != null) {
            rememberedProviders.put(recipeMap, providerName);
            // MyMod.LOG.info("[APU] rememberProvider: recipeMap='{}', providerName='{}', total={}",
            //     recipeMap, providerName, rememberedProviders.size());
            saveRemembered();
        } else {
            // MyMod.LOG.warn("[APU] rememberProvider: null args, recipeMap='{}', providerName='{}'",
            //     recipeMap, providerName);
        }
    }

    public static String getRememberedProviderName(String recipeMap) {
        String result = recipeMap != null ? rememberedProviders.get(recipeMap) : null;
        // MyMod.LOG.info("[APU] getRememberedProviderName: recipeMap='{}', result='{}', allKeys={}",
        //     recipeMap, result, rememberedProviders.keySet());
        return result;
    }

    /**
     * 删除"配方 → 供应器名"映射（供游戏内配置页面热修改）。
     *
     * @param recipeMap 配方名 key
     * @return 是否确实删除了某项
     */
    public static synchronized boolean removeRememberedProvider(String recipeMap) {
        if (recipeMap == null || recipeMap.trim()
            .isEmpty()) {
            return false;
        }
        String key = recipeMap.trim();
        if (rememberedProviders.remove(key) != null) {
            saveRemembered();
            return true;
        }
        return false;
    }

    /**
     * 按供应器名删除映射（供游戏内配置页面按值删除使用）。
     *
     * @param providerName 供应器名
     * @return 删除条数
     */
    public static synchronized int removeRememberedProvidersByValue(String providerName) {
        if (providerName == null || providerName.trim()
            .isEmpty()) {
            return 0;
        }
        String target = providerName.trim();
        int removed = 0;
        Iterator<Map.Entry<String, String>> it = rememberedProviders.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> e = it.next();
            if (Objects.equals(e.getValue(), target)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            saveRemembered();
        }
        return removed;
    }

    private static void saveRemembered() {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, String> entry : rememberedProviders.entrySet()) {
                root.addProperty(entry.getKey(), entry.getValue());
            }
            Files.createDirectories(REMEMBERED_FILE.getParent());
            try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(REMEMBERED_FILE),
                StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            MyMod.LOG.warn("Failed to save remembered providers", e);
        }
    }

    private static void loadRemembered() {
        try {
            if (!Files.exists(REMEMBERED_FILE)) {
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(REMEMBERED_FILE),
                StandardCharsets.UTF_8)) {
                JsonElement element = GSON.fromJson(reader, JsonElement.class);
                if (element != null && element.isJsonObject()) {
                    JsonObject root = element.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                        if (entry.getValue() != null && entry.getValue()
                            .isJsonPrimitive()) {
                            rememberedProviders.put(
                                entry.getKey(),
                                entry.getValue()
                                    .getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            MyMod.LOG.warn("Failed to load remembered providers", e);
        }
    }
}
