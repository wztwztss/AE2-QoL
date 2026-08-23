package com.wztwzt.ae2_qof.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;

/**
 * 通过反射扫描 GT 全量配方池（RecipeMap.ALL_RECIPE_MAPS），从输入/输出反查匹配的配方池 unlocalizedName。
 * 带每玩家冷却缓存，避免全量反射扫描被恶意连发卡服。供 auto-upload 与二合一终端编码共用。
 */
public final class RecipeMapDetector {

    /** 每个玩家全量反射扫描 GT 配方池的冷却时间（毫秒），防恶意连发卡服 */
    private static final long SCAN_COOLDOWN_MS = 3000L;
    private static final ConcurrentHashMap<String, Long> LAST_SCAN_TIMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> LAST_RECIPE_MAPS = new ConcurrentHashMap<>();

    private RecipeMapDetector() {}

    /**
     * 检测 inputs/outputs 命中的 GT 配方池 unlocalizedName，未命中返回 null。
     *
     * @param playerKey 玩家唯一键用于冷却缓存，可传 null 禁用缓存
     */
    public static String detectRecipeMap(ItemStack[] inputs, ItemStack[] outputs, String playerKey) {
        if (playerKey != null) {
            long now = System.currentTimeMillis();
            Long last = LAST_SCAN_TIMES.get(playerKey);
            if (last != null && now - last < SCAN_COOLDOWN_MS) {
                // 冷却期内复用上次结果，避免全量反射扫描
                return LAST_RECIPE_MAPS.get(playerKey);
            }
            LAST_SCAN_TIMES.put(playerKey, now);
        }
        String detected = scanRecipeMaps(inputs, outputs);
        if (playerKey != null) {
            LAST_RECIPE_MAPS.put(playerKey, detected);
        }
        return detected;
    }

    public static String scanRecipeMaps(ItemStack[] inputs, ItemStack[] outputs) {
        if (inputs == null || inputs.length == 0) {
            return null;
        }
        boolean hasUserOutputs = outputs != null;
        String firstInputMatch = null;

        try {
            // 通过反射获取 RecipeMap.ALL_RECIPE_MAPS
            Class<?> recipeMapClass = Class.forName("gregtech.api.recipe.RecipeMap");

            Field allMapsField = recipeMapClass.getField("ALL_RECIPE_MAPS");
            Map<?, ?> allMaps = (Map<?, ?>) allMapsField.get(null);

            // 获取 findRecipeQuery() 方法
            Method findRecipeQueryMethod = recipeMapClass.getMethod("findRecipeQuery");
            // 获取 FindRecipeQuery 的 items() 和 find() 方法
            Class<?> queryClass = Class.forName("gregtech.api.recipe.FindRecipeQuery");
            Method itemsMethod = queryClass.getMethod("items", ItemStack[].class);
            Method findMethod = queryClass.getMethod("find");
            Field recipeOutputsField = null;
            try {
                Class<?> gtRecipeClass = Class.forName("gregtech.api.objects.GT_Recipe");
                recipeOutputsField = gtRecipeClass.getField("mOutputs");
            } catch (Throwable ignored) {}

            for (Object map : allMaps.values()) {
                try {
                    // 获取配方池名字用于日志
                    Field nameField = recipeMapClass.getField("unlocalizedName");
                    String mapName = (String) nameField.get(map);

                    Object query = findRecipeQueryMethod.invoke(map);
                    query = itemsMethod.invoke(query, (Object) inputs);
                    Object recipe = findMethod.invoke(query);

                    if (recipe != null) {
                        // 同一输入物品常存在于多个配方池（如钢锭同时是高炉/电解机的输入），
                        // 仅凭输入反查会随 HashMap 遍历顺序随机命中。用用户提供的输出物校验：
                        // 输入+输出都匹配 → 确定命中；仅输入匹配 → 记为候选，全部扫完后兜底返回。
                        if (!hasUserOutputs || recipeOutputsField == null
                            || recipeMatchesOutputs(recipe, recipeOutputsField, outputs)) {
                            return mapName;
                        }
                        if (firstInputMatch == null) {
                            firstInputMatch = mapName;
                        }
                    }
                } catch (Throwable t) {
                    // 单个配方池查找失败，继续下一个
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("detectRecipeMap error: {}", t.getMessage());
        }
        return firstInputMatch;
    }

    /** 校验 GT 配方（GT_Recipe.mOutputs）的输出是否包含用户面板上填写的任一输出物品 */
    private static boolean recipeMatchesOutputs(Object recipe, Field recipeOutputsField, ItemStack[] userOutputs) {
        try {
            ItemStack[] recipeOutputs = (ItemStack[]) recipeOutputsField.get(recipe);
            if (recipeOutputs == null) return false;
            for (ItemStack uo : userOutputs) {
                if (uo == null || uo.stackSize <= 0) continue;
                for (ItemStack ro : recipeOutputs) {
                    if (ro != null && ro.getItem() == uo.getItem()
                        && (!uo.getHasSubtypes() || ro.getItemDamage() == uo.getItemDamage())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable t) {
            // 反射失败时放行，保持仅按输入匹配的旧行为
            return true;
        }
    }
}
