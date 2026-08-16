package com.gali.ae2_auto_pattern_upload.client;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import cpw.mods.fml.common.Loader;

public final class OverlayConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    private static final Path SETTINGS_FILE;

    static {
        SETTINGS_FILE = Loader.instance()
            .getConfigDir()
            .toPath()
            .resolve("ae2_auto_pattern_upload/settings.json");
    }

    private OverlayConfig() {}

    public static boolean isEnabled() {
        try {
            if (!Files.exists(SETTINGS_FILE)) {
                return true;
            }
            try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(SETTINGS_FILE),
                StandardCharsets.UTF_8)) {
                JsonElement elem = GSON.fromJson(reader, JsonElement.class);
                if (elem != null && elem.isJsonObject()) {
                    JsonElement val = elem.getAsJsonObject()
                        .get("nei_overlay_enabled");
                    if (val != null && val.isJsonPrimitive()) {
                        return val.getAsBoolean();
                    }
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    @SuppressWarnings("unused")
    public static void setEnabled(boolean enabled) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("nei_overlay_enabled", enabled);
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(SETTINGS_FILE),
                StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            System.out.println("[APU] Failed to save settings.json: " + e.getMessage());
        }
    }

    static {
        if (!Files.exists(SETTINGS_FILE)) {
            setEnabled(true);
        }
    }
}
