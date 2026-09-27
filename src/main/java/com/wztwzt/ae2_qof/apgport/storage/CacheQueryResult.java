/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.storage;

import java.util.Collections;
import java.util.List;

import com.wztwzt.ae2_qof.apgport.recipe.RecipeEntry;

/**
 * Query result returned by the recipe cache service.
 */
public class CacheQueryResult {

    public static final String SOURCE_DISK = "DISK";

    public final boolean cacheValid;
    public final String failureReason;
    public final List<String> matchedMapIds;
    public final List<RecipeEntry> recipes;
    public final int totalLoadedCount;
    public final int totalFilteredCount;
    public final String cacheSource;
    public final List<String> warnings;

    private CacheQueryResult(boolean cacheValid, String failureReason, List<String> matchedMapIds,
        List<RecipeEntry> recipes, int totalLoadedCount, int totalFilteredCount, String cacheSource,
        List<String> warnings) {
        this.cacheValid = cacheValid;
        this.failureReason = failureReason;
        this.matchedMapIds = matchedMapIds;
        this.recipes = recipes;
        this.totalLoadedCount = totalLoadedCount;
        this.totalFilteredCount = totalFilteredCount;
        this.cacheSource = cacheSource;
        this.warnings = warnings;
    }

    public static CacheQueryResult invalid(String failureReason) {
        return new CacheQueryResult(
            false,
            failureReason,
            Collections.<String>emptyList(),
            Collections.<RecipeEntry>emptyList(),
            0,
            0,
            SOURCE_DISK,
            Collections.<String>emptyList());
    }

    public static CacheQueryResult valid(List<String> matchedMapIds, List<RecipeEntry> recipes, int totalLoadedCount,
        int totalFilteredCount) {
        return new CacheQueryResult(
            true,
            "",
            matchedMapIds,
            recipes,
            totalLoadedCount,
            totalFilteredCount,
            SOURCE_DISK,
            Collections.<String>emptyList());
    }
}
