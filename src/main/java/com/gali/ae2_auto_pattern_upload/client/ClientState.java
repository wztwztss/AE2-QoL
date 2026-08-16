package com.gali.ae2_auto_pattern_upload.client;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
        REMEMBERED_FILE = configDir.resolve("ae2_auto_pattern_upload/remembered_providers.json");
        loadRemembered();
    }

    public static String lastProviderName = null;
    public static long lastProviderId = 0;
    public static String lastRecipeMap = null;

    public static String pendingRecipeMap = null;

    public static boolean highlightEnabled = false;
    public static java.util.List<int[]> highlightPositions = new java.util.ArrayList<int[]>();

    public static void clear() {
        lastProviderName = null;
        lastProviderId = 0;
        lastRecipeMap = null;
    }

    public static void set(String name, long id) {
        lastProviderName = name;
        lastProviderId = id;
    }

    public static void rememberProvider(String recipeMap, String providerName) {
        if (recipeMap != null && providerName != null) {
            rememberedProviders.put(recipeMap, providerName);
            saveRemembered();
        }
    }

    public static String getRememberedProviderName(String recipeMap) {
        return recipeMap != null ? rememberedProviders.get(recipeMap) : null;
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
            System.out.println("[APU] Failed to save remembered providers: " + e.getMessage());
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
            System.out.println("[APU] Loaded " + rememberedProviders.size() + " remembered providers");
        } catch (Exception e) {
            System.out.println("[APU] Failed to load remembered providers: " + e.getMessage());
        }
    }
}
