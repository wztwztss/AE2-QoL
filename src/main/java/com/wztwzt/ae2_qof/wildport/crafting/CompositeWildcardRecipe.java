/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.crafting;

import java.util.List;

import appeng.api.AEApi;
import com.wztwzt.ae2_qof.wildport.ModItems;
import com.wztwzt.ae2_qof.wildport.item.WildcardPatternState;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

public class CompositeWildcardRecipe implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting inventory, World world) {
        return getCraftingResult(inventory) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        boolean foundBlankPattern = false;
        boolean foundEmptyWildcard = false;

        for (int index = 0; index < inventory.getSizeInventory(); index++) {
            ItemStack stack = inventory.getStackInSlot(index);
            if (stack == null) {
                continue;
            }
            if (isBlankPattern(stack) && !foundBlankPattern) {
                foundBlankPattern = true;
                continue;
            }
            if (stack.getItem() == ModItems.wildcardPattern && isEmptyWildcardPattern(stack) && !foundEmptyWildcard) {
                foundEmptyWildcard = true;
                continue;
            }
            return null;
        }

        if (!foundBlankPattern || !foundEmptyWildcard) {
            return null;
        }
        ItemStack result = new ItemStack(ModItems.compositeWildcardPattern);
        CompositeWildcardPatternGenerator.markAsCompositeWildcard(result);
        return result;
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(ModItems.compositeWildcardPattern);
    }

    private static boolean isBlankPattern(ItemStack stack) {
        ItemStack blankPattern = AEApi.instance().definitions().materials().blankPattern().maybeStack(1).orNull();
        return blankPattern != null && stack.getItem() == blankPattern.getItem() && stack.getItemDamage() == blankPattern.getItemDamage();
    }

    private static boolean isEmptyWildcardPattern(ItemStack stack) {
        if (stack == null || stack.getItem() != ModItems.wildcardPattern) {
            return false;
        }
        if (WildcardPatternGenerator.isGeneratedPattern(stack)) {
            return false;
        }
        List<WildcardPatternEntry> inputs = WildcardPatternState.getInputEntries(stack);
        List<WildcardPatternEntry> outputs = WildcardPatternState.getOutputEntries(stack);
        return entriesEmpty(inputs) && entriesEmpty(outputs);
    }

    private static boolean entriesEmpty(List<WildcardPatternEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return true;
        }
        for (WildcardPatternEntry entry : entries) {
            if (entry != null && !entry.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
