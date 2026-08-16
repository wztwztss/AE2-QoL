package com.gali.ae2_auto_pattern_upload.client;

import com.gali.ae2_auto_pattern_upload.util.RecipeNameUtil;

import codechicken.nei.recipe.IRecipeHandler;

/**
 * Client-only recipe name utility, separated from RecipeNameUtil to avoid
 * loading NEI classes on the server side.
 */
public final class ClientRecipeNameUtil {

    private ClientRecipeNameUtil() {}

    public static void captureFromRecipeHandler(IRecipeHandler handler) {
        if (handler == null) {
            return;
        }
        String rawId = safeOverlayIdentifier(handler);
        if (rawId != null && !rawId.isEmpty()) {
            RecipeNameUtil.setLastRawRecipeId(rawId);
        } else {
            try {
                String recipeName = handler.getRecipeName();
                if (recipeName != null && !recipeName.trim()
                    .isEmpty()) {
                    RecipeNameUtil.setLastRawRecipeId(recipeName.trim());
                }
            } catch (Throwable ignored) {}
        }

        String keyword = mapRecipeHandlerToSearchKey(handler);
        if (keyword != null && !keyword.isEmpty()) {
            RecipeNameUtil.setLastRecipeName(keyword);
        }
    }

    public static String mapRecipeHandlerToSearchKey(IRecipeHandler handler) {
        if (handler == null) {
            return null;
        }
        try {
            String overlayId = safeOverlayIdentifier(handler);
            if (overlayId != null) {
                String mapped = RecipeNameUtil.mapStringToMapping(overlayId);
                if (mapped != null) {
                    return mapped;
                }
                return toDisplayString(overlayId);
            }
        } catch (Throwable ignored) {}

        try {
            String recipeName = handler.getRecipeName();
            if (recipeName != null && !recipeName.trim()
                .isEmpty()) {
                String mapped = RecipeNameUtil.mapStringToMapping(recipeName);
                if (mapped != null) {
                    return mapped;
                }
                return recipeName.trim();
            }
        } catch (Throwable ignored) {}

        return toDisplayString(
            handler.getClass()
                .getSimpleName());
    }

    private static String safeOverlayIdentifier(IRecipeHandler handler) {
        try {
            String id = handler.getOverlayIdentifier();
            if (id != null && !id.trim()
                .isEmpty()) {
                return id;
            }
        } catch (Throwable ignored) {}
        try {
            String tabName = handler.getRecipeTabName();
            if (tabName != null && !tabName.trim()
                .isEmpty()) {
                return tabName;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String toDisplayString(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace('_', ' ')
            .replace('-', ' ')
            .replace('.', ' ')
            .replace(':', ' ');
        cleaned = RecipeNameUtil.CAMEL_CASE_SPLITTER.matcher(cleaned)
            .replaceAll(" $1");
        cleaned = cleaned.replaceAll("\\s+", " ")
            .trim();
        return cleaned;
    }
}
