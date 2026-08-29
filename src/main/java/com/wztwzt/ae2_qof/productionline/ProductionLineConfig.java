package com.wztwzt.ae2_qof.productionline;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * 产线聚合器配置
 */
public class ProductionLineConfig {

    private static final String CONFIG_FILE = "config/ae2_qof/production_line_aggregator.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    /** 配方目录 */
    private static String recipeDir = "config/ae2_qof/production_lines/";
    
    /** 最大并行数 */
    private static int maxParallel = 64;
    
    /** 机器消耗倍率 */
    private static float machineConsumptionMultiplier = 1.5f;
    
    /** 启用调试模式 */
    private static boolean debugMode = false;

    /**
     * 加载配置
     */
    public static void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
            return;
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            
            if (json.has("recipeDir")) {
                recipeDir = json.get("recipeDir").getAsString();
            }
            if (json.has("maxParallel")) {
                maxParallel = json.get("maxParallel").getAsInt();
            }
            if (json.has("machineConsumptionMultiplier")) {
                machineConsumptionMultiplier = json.get("machineConsumptionMultiplier").getAsFloat();
            }
            if (json.has("debugMode")) {
                debugMode = json.get("debugMode").getAsBoolean();
            }
            
        } catch (Exception e) {
            System.err.println("[ProductionLineAggregator] Failed to load config: " + e.getMessage());
        }
    }

    /**
     * 创建默认配置
     */
    private static void createDefaultConfig(File configFile) {
        JsonObject json = new JsonObject();
        json.addProperty("recipeDir", recipeDir);
        json.addProperty("maxParallel", maxParallel);
        json.addProperty("machineConsumptionMultiplier", machineConsumptionMultiplier);
        json.addProperty("debugMode", debugMode);
        
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            System.err.println("[ProductionLineAggregator] Failed to create default config: " + e.getMessage());
        }
    }

    /**
     * 保存配置
     */
    public static void saveConfig() {
        JsonObject json = new JsonObject();
        json.addProperty("recipeDir", recipeDir);
        json.addProperty("maxParallel", maxParallel);
        json.addProperty("machineConsumptionMultiplier", machineConsumptionMultiplier);
        json.addProperty("debugMode", debugMode);
        
        try (FileWriter writer = new FileWriter(new File(CONFIG_FILE))) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            System.err.println("[ProductionLineAggregator] Failed to save config: " + e.getMessage());
        }
    }

    public static String getRecipeDir() {
        return recipeDir;
    }

    public static int getMaxParallel() {
        return maxParallel;
    }

    public static float getMachineConsumptionMultiplier() {
        return machineConsumptionMultiplier;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }
}
