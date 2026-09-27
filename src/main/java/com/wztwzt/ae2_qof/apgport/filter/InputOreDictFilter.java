/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.filter;

import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.apgport.recipe.RecipeEntry;

/**
 * 按输入物品的统一显式筛选语法进行匹配。
 */
public class InputOreDictFilter implements IRecipeFilter {

    private final String matchSource;
    private final ExplicitStackMatcher matcher;

    public InputOreDictFilter(String matchSource) {
        this(matchSource, new ExplicitStackMatcher.StackMatchCache());
    }

    InputOreDictFilter(String matchSource, ExplicitStackMatcher.StackMatchCache stackMatchCache) {
        this.matchSource = matchSource;
        this.matcher = new ExplicitStackMatcher(matchSource, stackMatchCache);
    }

    @Override
    public boolean matches(RecipeEntry recipe) {
        if (matcher.isDisabled()) {
            return true;
        }

        for (ItemStack input : recipe.inputs) {
            if (input != null && matcher.matches(input)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getDescription() {
        return "输入筛选: " + matchSource;
    }

    public String getRegexPattern() {
        return matchSource;
    }
}
