/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.filter;

import com.wztwzt.ae2_qof.apgport.recipe.RecipeEntry;

/**
 * 按配方表 ID (机器类型) 过滤
 */
public class RecipeMapFilter implements IRecipeFilter {

    private final String targetRecipeMapId;

    public RecipeMapFilter(String targetRecipeMapId) {
        this.targetRecipeMapId = targetRecipeMapId;
    }

    @Override
    public boolean matches(RecipeEntry recipe) {
        if (targetRecipeMapId == null || targetRecipeMapId.isEmpty()) {
            return true; // 空过滤 = 不过滤
        }
        return targetRecipeMapId.equals(recipe.recipeMapId);
    }

    @Override
    public String getDescription() {
        return "配方表: " + (targetRecipeMapId != null ? targetRecipeMapId : "全部");
    }

    public String getTargetRecipeMapId() {
        return targetRecipeMapId;
    }
}
