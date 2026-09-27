/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.filter;

import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.apgport.recipe.RecipeEntry;

/**
 * 黑名单过滤器 — 如果配方包含匹配项，则拒绝。
 */
public class BlacklistFilter implements IRecipeFilter {

    private final String keyword;
    private final boolean checkInputs;
    private final boolean checkOutputs;
    private final ExplicitStackMatcher matcher;

    public BlacklistFilter(String keyword, boolean checkInputs, boolean checkOutputs) {
        this(keyword, checkInputs, checkOutputs, new ExplicitStackMatcher.StackMatchCache());
    }

    BlacklistFilter(String keyword, boolean checkInputs, boolean checkOutputs,
        ExplicitStackMatcher.StackMatchCache stackMatchCache) {
        this.keyword = keyword;
        this.checkInputs = checkInputs;
        this.checkOutputs = checkOutputs;
        this.matcher = new ExplicitStackMatcher(keyword, stackMatchCache);
    }

    @Override
    public boolean matches(RecipeEntry recipe) {
        if (matcher.isDisabled()) {
            return true;
        }

        if (checkInputs && containsMatch(recipe.inputs)) {
            return false;
        }

        if (checkOutputs && containsMatch(recipe.outputs)) {
            return false;
        }

        return true;
    }

    private boolean containsMatch(ItemStack[] stacks) {
        if (stacks == null || stacks.length == 0) {
            return false;
        }

        for (ItemStack stack : stacks) {
            if (matcher.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getDescription() {
        return "黑名单(" + (checkInputs ? "入" : "") + (checkOutputs ? "出" : "") + "): " + keyword;
    }
}
