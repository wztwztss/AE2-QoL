package com.wztwzt.ae2_qof.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.IRecipeHandler;

/**
 * 捕获并提取当前 NEI 配方视图（GuiRecipe）中的输入/输出，供二合一终端"一键填充"使用。
 * 玩家在 NEI 配方页浏览配方后，面板 NEI 按钮读取最近浏览的配方填充编码格。
 */
public final class NeiRecipeCapture {

    private static volatile IRecipeHandler lastHandler;
    private static volatile int lastRecipeIndex = -1;

    private NeiRecipeCapture() {}

    /** 每次 GuiRecipe.updateScreen 调用，记录当前浏览的配方 handler 与页码 */
    public static void captureFromGui(GuiRecipe<?> gui) {
        try {
            IRecipeHandler handler = gui.getHandler();
            List<Integer> indices = gui.getRecipeIndices();
            if (handler == null || indices == null || indices.isEmpty()) {
                return;
            }
            int page = Math.max(0, Math.min(gui.page, indices.size() - 1));
            lastHandler = handler;
            lastRecipeIndex = indices.get(page);
            captureGTRecipeMap(handler);
        } catch (Throwable ignored) {}
    }

    public static boolean hasCapturedRecipe() {
        return lastHandler != null && lastRecipeIndex >= 0;
    }

    /**
     * 提取最近浏览配方的输入/输出，并判定合成模式。
     */
    public static RecipeData extractCurrentRecipe() {
        RecipeData data = new RecipeData();
        if (!hasCapturedRecipe()) {
            return data;
        }
        extractFrom(lastHandler, lastRecipeIndex, data);
        return data;
    }

    /** 从指定配方 handler 的指定配方页提取输入/输出（供 NEI 覆盖层「+」直传使用）。 */
    public static RecipeData extractFrom(IRecipeHandler handler, int recipeIndex) {
        RecipeData data = new RecipeData();
        if (handler == null || recipeIndex < 0) {
            return data;
        }
        extractFrom(handler, recipeIndex, data);
        return data;
    }

    private static void extractFrom(IRecipeHandler handler, int recipeIndex, RecipeData data) {
        try {
            List<ItemStack> ins = new ArrayList<>();
            List<ItemStack> outs = new ArrayList<>();

            List<PositionedStack> ingredients = handler.getIngredientStacks(recipeIndex);
            if (ingredients != null) {
                for (PositionedStack ps : ingredients) {
                    ItemStack item = pickStack(ps);
                    if (item != null) {
                        ins.add(item.copy());
                    }
                }
            }

            PositionedStack result = handler.getResultStack(recipeIndex);
            if (result != null) {
                ItemStack item = pickStack(result);
                if (item != null) {
                    outs.add(item.copy());
                }
            }

            List<PositionedStack> other = handler.getOtherStacks(recipeIndex);
            if (other != null) {
                for (PositionedStack ps : other) {
                    ItemStack item = pickStack(ps);
                    if (item != null) {
                        outs.add(item.copy());
                    }
                }
            }

            if (ins.size() > 27) {
                ins.subList(27, ins.size())
                    .clear();
            }
            if (outs.size() > 9) {
                outs.subList(9, outs.size())
                    .clear();
            }

            data.inputs = ins.toArray(new ItemStack[0]);
            data.outputs = outs.toArray(new ItemStack[0]);
            data.crafting = isCraftingRecipe(data.inputs, data.outputs);
            data.valid = data.inputs.length > 0 && data.outputs.length > 0;
        } catch (Throwable t) {
            data.valid = false;
        }
    }

    private static ItemStack pickStack(PositionedStack ps) {
        if (ps == null) {
            return null;
        }
        if (ps.item != null) {
            return ps.item;
        }
        if (ps.items != null && ps.items.length > 0) {
            return ps.items[0];
        }
        return null;
    }

    /** 3x3 可摆放且 CraftingManager 命中 → 合成模式；否则处理模式（GT 机器配方等） */
    private static boolean isCraftingRecipe(ItemStack[] inputs, ItemStack[] outputs) {
        if (outputs == null || outputs.length == 0 || outputs[0] == null) {
            return false;
        }
        if (inputs == null || inputs.length > 9) {
            return false;
        }
        try {
            final InventoryCrafting ic = new InventoryCrafting(new Container() {

                @Override
                public boolean canInteractWith(EntityPlayer p) {
                    return false;
                }
            }, 3, 3);
            for (int i = 0; i < inputs.length; i++) {
                ic.setInventorySlotContents(i, inputs[i]);
            }
            ItemStack res = CraftingManager.getInstance()
                .findMatchingRecipe(ic, null);
            if (res == null) {
                return false;
            }
            return res.getItem() == outputs[0].getItem()
                && (res.getItemDamage() == outputs[0].getItemDamage()
                    || outputs[0].getItemDamage() == OreDictionary.WILDCARD_VALUE);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void captureGTRecipeMap(IRecipeHandler handler) {
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
                    String mapName = (String) recipeMap.getClass()
                        .getField("unlocalizedName")
                        .get(recipeMap);
                    if (mapName != null && !mapName.isEmpty()) {
                        ClientState.pendingRecipeMap = mapName;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static class RecipeData {

        public ItemStack[] inputs = new ItemStack[0];
        public ItemStack[] outputs = new ItemStack[0];
        public boolean crafting = false;
        public boolean valid = false;
    }
}