/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.filter;

import com.wztwzt.ae2_qof.apgport.recipe.RecipeEntry;

/**
 * 电压等级过滤器
 */
public class TierFilter implements IRecipeFilter {

    private final int targetTier;

    /**
     * @param targetTier 目标电压等级 (-1=Any, 0=ULV, 1=LV...)
     */
    public TierFilter(int targetTier) {
        this.targetTier = targetTier;
    }

    @Override
    public boolean matches(RecipeEntry recipe) {
        if (targetTier < 0) return true; // Any

        // 仅保留完全匹配所选电压等级的配方，避免重复生成低等级或无法处理高等级
        return getTier(recipe.euPerTick) == targetTier;
    }

    @Override
    public String getDescription() {
        return "Tier=" + targetTier;
    }

    private int getTier(long euPerTick) {
        if (euPerTick <= 0) return -1;
        long threshold = 8;
        int tier = 0;
        while (euPerTick > threshold) {
            threshold *= 4;
            tier++;
        }
        return tier;
    }
}
