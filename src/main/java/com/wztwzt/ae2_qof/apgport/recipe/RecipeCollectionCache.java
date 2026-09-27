/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.recipe;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Small synchronized cache for recipe collection results.
 */
final class RecipeCollectionCache<K, V> {

    private final Map<K, V> cache = new HashMap<K, V>();

    synchronized V getOrCompute(K key, Supplier<V> supplier) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        V value = supplier.get();
        cache.put(key, value);
        return value;
    }

    synchronized void clear() {
        cache.clear();
    }
}
