/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.storage;

/**
 * Summary information for a built recipe cache.
 */
public class CacheStatistics {

    public static final CacheStatistics EMPTY = new CacheStatistics(false, 0, 0, 0, 0L, 0L, 0L);

    public final boolean available;
    public final int totalRecipeCount;
    public final int totalRecipeMaps;
    public final int totalModCount;
    public final long directoryBytes;
    public final long createdAt;
    public final long lastUpdated;

    public CacheStatistics(boolean available, int totalRecipeCount, int totalRecipeMaps, int totalModCount,
        long directoryBytes, long createdAt, long lastUpdated) {
        this.available = available;
        this.totalRecipeCount = totalRecipeCount;
        this.totalRecipeMaps = totalRecipeMaps;
        this.totalModCount = totalModCount;
        this.directoryBytes = directoryBytes;
        this.createdAt = createdAt;
        this.lastUpdated = lastUpdated;
    }
}
