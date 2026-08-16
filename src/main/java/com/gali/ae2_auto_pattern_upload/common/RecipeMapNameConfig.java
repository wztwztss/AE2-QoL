package com.gali.ae2_auto_pattern_upload.common;

import java.util.HashMap;
import java.util.Map;

import com.gali.ae2_auto_pattern_upload.util.RecipeNameUtil;

/**
 * Maps GT recipe map IDs to Chinese search keywords.
 * Mapping data is stored in RecipeNameUtil's config file.
 */
public final class RecipeMapNameConfig {

    private static volatile Map<String, String> CACHE = new HashMap<String, String>();

    private RecipeMapNameConfig() {}

    /**
     * Reload mappings from RecipeNameUtil into cache.
     */
    public static synchronized void reload() {
        Map<String, String> newCache = new HashMap<String, String>();
        Map<String, String> mappings = RecipeNameUtil.getMappingsView();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            newCache.put(key.toLowerCase(), value);
            String stripped = stripPrefix(key);
            if (stripped != null && !stripped.equals(key)) {
                newCache.put(stripped.toLowerCase(), value);
            }
        }
        CACHE = newCache;
    }

    /**
     * Resolve a Chinese search keyword for a recipe map name.
     * Supports formats like "gt.recipe.compressor", "compressor", etc.
     *
     * @param recipeMapName recipe map name
     * @return search keyword, or original name if not found
     */
    public static String resolveSearchKeyword(String recipeMapName) {
        if (recipeMapName == null || recipeMapName.trim()
            .isEmpty()) return null;

        Map<String, String> localCache = CACHE;
        if (localCache.isEmpty()) {
            reload();
            localCache = CACHE;
        }

        String trimmed = recipeMapName.trim();

        String result = localCache.get(trimmed.toLowerCase());
        if (result != null) {
            return result;
        }

        String stripped = stripPrefix(trimmed);
        if (stripped != null) {
            result = localCache.get(stripped.toLowerCase());
            if (result != null) {
                return result;
            }
        }

        int dotIdx = trimmed.lastIndexOf('.');
        if (dotIdx >= 0) {
            String lastPart = trimmed.substring(dotIdx + 1);
            result = localCache.get(lastPart.toLowerCase());
            if (result != null) {
                return result;
            }
        }

        return trimmed;
    }

    /**
     * Extract machine English name from recipe map ID.
     * e.g. "gt.recipe.compressor" -> "compressor"
     */
    public static String extractMachineName(String recipeMapName) {
        if (recipeMapName == null || recipeMapName.trim()
            .isEmpty()) return null;
        String trimmed = recipeMapName.trim();
        int dotIdx = trimmed.lastIndexOf('.');
        if (dotIdx >= 0) {
            return trimmed.substring(dotIdx + 1);
        }
        return trimmed;
    }

    /**
     * Check if a provider name matches a search keyword.
     */
    public static boolean matchesProviderName(String providerName, String searchKey) {
        if (providerName == null || searchKey == null) return false;
        return providerName.toLowerCase()
            .contains(searchKey.toLowerCase())
            || searchKey.toLowerCase()
                .contains(providerName.toLowerCase());
    }

    private static String stripPrefix(String key) {
        if (key == null) return null;
        int firstDot = key.indexOf('.');
        if (firstDot >= 0) {
            String afterFirst = key.substring(firstDot + 1);
            int secondDot = afterFirst.indexOf('.');
            if (secondDot >= 0) {
                return afterFirst.substring(secondDot + 1);
            }
        }
        return key;
    }
}
