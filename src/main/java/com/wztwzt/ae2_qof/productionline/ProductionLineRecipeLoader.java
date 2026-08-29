package com.wztwzt.ae2_qof.productionline;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.util.GTUtility;

/**
 * 产线配方加载器
 * 从JSON文件加载产线配方
 */
public class ProductionLineRecipeLoader {

    private static final String RECIPE_DIR = "config/ae2_qof/production_lines/";
    private static final Gson GSON = new Gson();
    
    /** 已加载的配方 */
    private static final Map<String, ProductionLineRecipe> loadedRecipes = new HashMap<>();

    /**
     * 加载所有产线配方
     */
    public static void loadRecipes() {
        File dir = new File(RECIPE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            createDefaultRecipe(dir);
        }
        
        loadedRecipes.clear();
        
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try {
                    ProductionLineRecipe recipe = loadRecipe(file);
                    if (recipe != null) {
                        loadedRecipes.put(recipe.getId(), recipe);
                        registerRecipe(recipe);
                    }
                } catch (Exception e) {
                    System.err.println("[ProductionLineAggregator] Failed to load recipe: " + file.getName());
                    e.printStackTrace();
                }
            }
        }
        
        System.out.println("[ProductionLineAggregator] Loaded " + loadedRecipes.size() + " production line recipes");
    }

    /**
     * 从文件加载配方
     */
    private static ProductionLineRecipe loadRecipe(File file) {
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            
            String id = json.get("id").getAsString();
            String name = json.get("name").getAsString();
            String category = json.has("category") ? json.get("category").getAsString() : "general";
            String description = json.has("description") ? json.get("description").getAsString() : "";
            int minVoltageTier = json.has("minVoltageTier") ? json.get("minVoltageTier").getAsInt() : 0;
            
            List<ProductionLineRecipe.RequiredMachine> requiredMachines = new ArrayList<>();
            if (json.has("requiredMachines")) {
                JsonArray machinesArray = json.getAsJsonArray("requiredMachines");
                for (JsonElement element : machinesArray) {
                    JsonObject machineJson = element.getAsJsonObject();
                    String machineId = machineJson.get("id").getAsString();
                    String machineName = machineJson.get("name").getAsString();
                    int count = machineJson.has("count") ? machineJson.get("count").getAsInt() : 1;
                    boolean consumed = machineJson.has("consumed") ? machineJson.get("consumed").getAsBoolean() : true;
                    requiredMachines.add(new ProductionLineRecipe.RequiredMachine(machineId, machineName, count, consumed));
                }
            }
            
            List<ItemStack> itemOutputs = new ArrayList<>();
            if (json.has("finalOutputs")) {
                JsonObject outputs = json.getAsJsonObject("finalOutputs");
                if (outputs.has("items")) {
                    JsonArray itemsArray = outputs.getAsJsonArray("items");
                    for (JsonElement element : itemsArray) {
                        JsonObject itemJson = element.getAsJsonObject();
                        ItemStack stack = parseItemStack(itemJson);
                        if (stack != null) {
                            itemOutputs.add(stack);
                        }
                    }
                }
            }
            
            List<FluidStack> fluidOutputs = new ArrayList<>();
            if (json.has("finalOutputs")) {
                JsonObject outputs = json.getAsJsonObject("finalOutputs");
                if (outputs.has("fluids")) {
                    JsonArray fluidsArray = outputs.getAsJsonArray("fluids");
                    for (JsonElement element : fluidsArray) {
                        JsonObject fluidJson = element.getAsJsonObject();
                        FluidStack stack = parseFluidStack(fluidJson);
                        if (stack != null) {
                            fluidOutputs.add(stack);
                        }
                    }
                }
            }
            
            int euPerTick = json.has("euPerTick") ? json.get("euPerTick").getAsInt() : 30;
            int duration = json.has("duration") ? json.get("duration").getAsInt() : 200;
            int voltageTier = json.has("voltageTier") ? json.get("voltageTier").getAsInt() : 0;
            int parallel = json.has("parallel") ? json.get("parallel").getAsInt() : 1;
            
            return new ProductionLineRecipe(id, name, category, description, minVoltageTier,
                requiredMachines, itemOutputs, fluidOutputs, euPerTick, duration, voltageTier, parallel);
                
        } catch (Exception e) {
            System.err.println("[ProductionLineAggregator] Error parsing recipe file: " + file.getName());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 解析物品堆
     */
    private static ItemStack parseItemStack(JsonObject json) {
        try {
            String itemId = json.get("item").getAsString();
            int count = json.has("count") ? json.get("count").getAsInt() : 1;
            int meta = json.has("meta") ? json.get("meta").getAsInt() : 0;
            
            // 解析 "modId:name" 格式
            String[] parts = itemId.split(":");
            if (parts.length == 2) {
                Item item = GameRegistry.findItem(parts[0], parts[1]);
                if (item != null) {
                    return new ItemStack(item, count, meta);
                }
            }
        } catch (Exception e) {
            System.err.println("[ProductionLineAggregator] Failed to parse item: " + json);
        }
        return null;
    }

    /**
     * 解析流体堆
     */
    private static FluidStack parseFluidStack(JsonObject json) {
        try {
            String fluidName = json.get("fluid").getAsString();
            int amount = json.has("amount") ? json.get("amount").getAsInt() : 1000;
            
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (fluid != null) {
                return new FluidStack(fluid, amount);
            }
        } catch (Exception e) {
            System.err.println("[ProductionLineAggregator] Failed to parse fluid: " + json);
        }
        return null;
    }

    /**
     * 注册配方到RecipeMap
     */
    private static void registerRecipe(ProductionLineRecipe recipe) {
        // TODO: 注册到ProductionLineRecipeMaps.productionLineRecipes
        // 需要构建GTRecipe对象
    }

    /**
     * 创建默认配方
     */
    private static void createDefaultRecipe(File dir) {
        // 创建示例配方文件
        // 石油工业/轻燃油
        String defaultRecipe = "{\n" +
            "  \"type\": \"ae2_qof:production_line\",\n" +
            "  \"id\": \"petrochemical_light_fuel\",\n" +
            "  \"name\": \"石油工业/轻燃油\",\n" +
            "  \"category\": \"petrochemical\",\n" +
            "  \"description\": \"将原油转化为轻燃油的完整产线\",\n" +
            "  \"minVoltageTier\": 1,\n" +
            "  \"requiredMachines\": [\n" +
            "    {\n" +
            "      \"id\": \"gtceu:distillation_tower\",\n" +
            "      \"name\": \"蒸馏塔\",\n" +
            "      \"count\": 1,\n" +
            "      \"consumed\": true\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"gtceu:chemical_reactor\",\n" +
            "      \"name\": \"化学反应釜\",\n" +
            "      \"count\": 2,\n" +
            "      \"consumed\": true\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"gtceu:cracker\",\n" +
            "      \"name\": \"裂解机\",\n" +
            "      \"count\": 1,\n" +
            "      \"consumed\": true\n" +
            "    }\n" +
            "  ],\n" +
            "  \"steps\": [\n" +
            "    {\n" +
            "      \"name\": \"原油蒸馏\",\n" +
            "      \"machine\": \"gtceu:distillation_tower\",\n" +
            "      \"inputs\": {\n" +
            "        \"items\": [],\n" +
            "        \"fluids\": [\n" +
            "          {\"fluid\": \"gtceu:oil\", \"amount\": 1000},\n" +
            "          {\"fluid\": \"gtceu:steam\", \"amount\": 1000}\n" +
            "        ]\n" +
            "      },\n" +
            "      \"outputs\": {\n" +
            "        \"items\": [],\n" +
            "        \"fluids\": [\n" +
            "          {\"fluid\": \"gtceu:light_fuel\", \"amount\": 60},\n" +
            "          {\"fluid\": \"gtceu:heavy_fuel\", \"amount\": 40}\n" +
            "        ]\n" +
            "      }\n" +
            "    }\n" +
            "  ],\n" +
            "  \"finalOutputs\": {\n" +
            "    \"items\": [\n" +
            "      {\"item\": \"gtceu:sulfur_dust\", \"count\": 4}\n" +
            "    ],\n" +
            "    \"fluids\": [\n" +
            "      {\"fluid\": \"gtceu:light_fuel\", \"amount\": 12000},\n" +
            "      {\"fluid\": \"gtceu:heavy_fuel\", \"amount\": 800}\n" +
            "    ]\n" +
            "  },\n" +
            "  \"euPerTick\": 128,\n" +
            "  \"duration\": 400,\n" +
            "  \"voltageTier\": 1,\n" +
            "  \"parallel\": 1\n" +
            "}";
        
        try {
            java.io.FileWriter writer = new java.io.FileWriter(new File(dir, "petrochemical_light_fuel.json"));
            writer.write(defaultRecipe);
            writer.close();
        } catch (Exception e) {
            System.err.println("[ProductionLineAggregator] Failed to create default recipe");
            e.printStackTrace();
        }
    }

    /**
     * 获取已加载的配方
     */
    public static ProductionLineRecipe getRecipe(String id) {
        return loadedRecipes.get(id);
    }

    /**
     * 获取所有已加载的配方
     */
    public static Map<String, ProductionLineRecipe> getAllRecipes() {
        return loadedRecipes;
    }
}
