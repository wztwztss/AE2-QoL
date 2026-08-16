package com.gali.ae2_auto_pattern_upload.mixin.nei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gali.ae2_auto_pattern_upload.client.ClientRecipeNameUtil;
import com.gali.ae2_auto_pattern_upload.client.ClientState;

import codechicken.lib.inventory.InventoryUtils;
import codechicken.nei.ItemPanels;
import codechicken.nei.PositionedStack;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.recipe.DefaultOverlayHandler;
import codechicken.nei.recipe.DefaultOverlayHandler.DistributedIngred;
import codechicken.nei.recipe.DefaultOverlayHandler.IngredientDistribution;
import codechicken.nei.recipe.IRecipeHandler;

@Mixin(value = DefaultOverlayHandler.class, remap = false)
public abstract class MixinDefaultOverlayHandler {

    @Shadow(remap = false)
    protected abstract boolean canStack(ItemStack dst, ItemStack src);

    @Inject(
        method = "transferRecipe(Lnet/minecraft/client/gui/inventory/GuiContainer;Lcodechicken/nei/recipe/IRecipeHandler;II)I",
        at = @At("HEAD"))
    private void ae2AutoPatternUpload$captureRecipe(GuiContainer gui, IRecipeHandler handler, int recipeIndex,
        int multiplier, CallbackInfoReturnable<Integer> cir) {
        if (handler != null) {
            ClientRecipeNameUtil.captureFromRecipeHandler(handler);
            captureGTRecipeMap(handler);
        }
    }

    /**
     * @author wztwzt
     * @reason Add bookmark priority bonus to assignIngredients
     */
    @Overwrite
    protected List<IngredientDistribution> assignIngredients(List<PositionedStack> ingredients,
        List<DistributedIngred> ingredStacks) {
        List<ItemStack> bookmarks = getBookmarks();

        ArrayList<IngredientDistribution> assignedIngredients = new ArrayList<>();
        for (PositionedStack posstack : ingredients) {
            DistributedIngred biggestIngred = null;
            ItemStack permutation = null;
            int biggestSize = 0;
            for (ItemStack pstack : posstack.items) {
                for (DistributedIngred istack : ingredStacks) {
                    if (!canStack(pstack, istack.stack) || istack.invAmount - istack.distributed < pstack.stackSize
                        || istack.recipeAmount == 0
                        || pstack.stackSize == 0) continue;

                    int relsize = (istack.invAmount - istack.invAmount / istack.recipeAmount * istack.distributed)
                        / pstack.stackSize;

                    int bookmarkPriority = getBookmarkPriority(pstack, bookmarks);
                    if (bookmarkPriority != Integer.MAX_VALUE) {
                        relsize += 10000 - bookmarkPriority;
                    }

                    if (relsize > biggestSize) {
                        biggestSize = relsize;
                        biggestIngred = istack;
                        permutation = pstack;
                        break;
                    }
                }
            }

            if (biggestIngred == null) {
                biggestIngred = new DistributedIngred(posstack.item);
                permutation = InventoryUtils.copyStack(posstack.item, 0);
            }

            biggestIngred.distributed += permutation.stackSize;
            assignedIngredients.add(new IngredientDistribution(biggestIngred, permutation));
        }

        return assignedIngredients;
    }

    /**
     * Bookmark priority for a concrete recipe stack. Higher priority (smaller index) wins.
     * Matches on:
     * 1. exact (item, damage) pair;
     * 2. wildcard bookmark (damage 32767) of the same item;
     * 3. shared oreDict names (GT circuits etc.).
     */
    private static int getBookmarkPriority(ItemStack pstack, List<ItemStack> bookmarks) {
        if (pstack == null || pstack.getItem() == null) {
            return Integer.MAX_VALUE;
        }
        int[] pOreIds = OreDictionary.getOreIDs(pstack);
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < bookmarks.size(); i++) {
            ItemStack bookmark = bookmarks.get(i);
            if (bookmark == null || bookmark.getItem() == null || bookmark.getItem() != pstack.getItem()) {
                continue;
            }
            if (bookmark.getItemDamage() == pstack.getItemDamage()) {
                return i;
            }
            if (bookmark.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                best = Math.min(best, i);
            }
        }
        for (int oreId : pOreIds) {
            int i = bookmarkOreDictPriorities.getOrDefault(oreId, Integer.MAX_VALUE);
            best = Math.min(best, i);
        }
        return best;
    }

    private static final Map<Integer, Integer> bookmarkOreDictPriorities = new HashMap<>();

    private static List<ItemStack> getBookmarks() {
        bookmarkOreDictPriorities.clear();
        List<ItemStack> bookmarks = new ArrayList<>();
        try {
            BookmarkGrid grid = ItemPanels.bookmarkPanel.getGrid();
            for (int i = 0; i < 4096; i++) {
                BookmarkItem bookmark = grid.getBookmarkItem(i);
                if (bookmark == null) {
                    break;
                }
                ItemStack is = bookmark.itemStack;
                if (is == null || is.getItem() == null) {
                    continue;
                }
                bookmarks.add(is);
                for (int oreId : OreDictionary.getOreIDs(is)) {
                    if (!bookmarkOreDictPriorities.containsKey(oreId)) {
                        bookmarkOreDictPriorities.put(oreId, i);
                    }
                }
            }
        } catch (Throwable ignored) {}
        System.out.println("[APU] Bookmark priorities: " + bookmarks.size());
        return bookmarks;
    }

    private void captureGTRecipeMap(IRecipeHandler handler) {
        try {
            Class<?> handlerClass = handler.getClass();
            String className = handlerClass.getName();
            if (!className.contains("gregtech") && !className.contains("GTNEI")) {
                return;
            }

            java.lang.reflect.Method getRecipeMapMethod = null;
            try {
                getRecipeMapMethod = handlerClass.getMethod("getRecipeMap");
            } catch (NoSuchMethodException e) {
                Class<?> superClass = handlerClass.getSuperclass();
                while (superClass != null) {
                    try {
                        getRecipeMapMethod = superClass.getMethod("getRecipeMap");
                        break;
                    } catch (NoSuchMethodException ex) {
                        superClass = superClass.getSuperclass();
                    }
                }
            }

            if (getRecipeMapMethod != null) {
                Object recipeMap = getRecipeMapMethod.invoke(handler);
                if (recipeMap != null) {
                    java.lang.reflect.Field nameField = recipeMap.getClass()
                        .getField("unlocalizedName");
                    String mapName = (String) nameField.get(recipeMap);
                    if (mapName != null && !mapName.isEmpty()) {
                        ClientState.pendingRecipeMap = mapName;
                        System.out.println("[APU] Captured GT recipe map from NEI overlay: " + mapName);
                        return;
                    }
                }
            }

            try {
                java.lang.reflect.Field recipeMapField = handlerClass.getField("recipeMap");
                Object recipeMap = recipeMapField.get(handler);
                if (recipeMap != null) {
                    java.lang.reflect.Field nameField = recipeMap.getClass()
                        .getField("unlocalizedName");
                    String mapName = (String) nameField.get(recipeMap);
                    if (mapName != null && !mapName.isEmpty()) {
                        ClientState.pendingRecipeMap = mapName;
                        System.out.println("[APU] Captured GT recipe map from NEI overlay field: " + mapName);
                        return;
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}
    }
}
