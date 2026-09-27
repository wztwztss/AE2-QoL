/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.filter;

import java.util.ArrayList;
import java.util.List;

import com.wztwzt.ae2_qof.apgport.recipe.RecipeEntry;

/**
 * 组合过滤器 — 所有子过滤器必须同时满足 (AND)
 */
public class CompositeFilter implements IRecipeFilter {

    private final List<IRecipeFilter> filters = new ArrayList<>();

    public CompositeFilter() {}

    public void addFilter(IRecipeFilter filter) {
        if (filter != null) {
            filters.add(filter);
        }
    }

    public void clearFilters() {
        filters.clear();
    }

    public List<IRecipeFilter> getFilters() {
        return filters;
    }

    @Override
    public boolean matches(RecipeEntry recipe) {
        for (IRecipeFilter filter : filters) {
            if (!filter.matches(recipe)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getDescription() {
        if (filters.isEmpty()) return "无过滤条件";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filters.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(
                filters.get(i)
                    .getDescription());
        }
        return sb.toString();
    }
}
