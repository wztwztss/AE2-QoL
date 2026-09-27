/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.crafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.PatternHelper;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class WildcardPatternDetails implements ICraftingPatternDetails {

    private final PatternHelper delegate;

    public WildcardPatternDetails(ItemStack stack, World world) {
        this.delegate = new PatternHelper(stack, world);
    }

    @Override
    public ItemStack getPattern() {
        return this.delegate.getPattern();
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
        return this.delegate.isValidItemForSlot(slotIndex, itemStack, world);
    }

    @Override
    public boolean isCraftable() {
        return this.delegate.isCraftable();
    }

    @Override
    public IAEItemStack[] getInputs() {
        return this.delegate.getInputs();
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return this.delegate.getCondensedInputs();
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return this.delegate.getCondensedOutputs();
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return this.delegate.getOutputs();
    }

    @Override
    public boolean canSubstitute() {
        return this.delegate.canSubstitute();
    }

    @Override
    public boolean canBeSubstitute() {
        return this.delegate.canBeSubstitute();
    }

    @Override
    public ItemStack getOutput(InventoryCrafting craftingInv, World world) {
        return this.delegate.getOutput(craftingInv, world);
    }

    @Override
    public int getPriority() {
        return this.delegate.getPriority();
    }

    @Override
    public void setPriority(int priority) {
        this.delegate.setPriority(priority);
    }

    @Override
    public int hashCode() {
        ItemStack pattern = this.getPattern();
        int result = pattern.getItem() != null ? System.identityHashCode(pattern.getItem()) : 0;
        result = 31 * result + pattern.getItemDamage();
        result = 31 * result + WildcardPatternGenerator.getPatternIdentity(pattern).hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WildcardPatternDetails other)) {
            return false;
        }
        return arePatternStacksEqual(this.getPattern(), other.getPattern());
    }

    private static boolean arePatternStacksEqual(ItemStack left, ItemStack right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.getItem() != right.getItem() || left.getItemDamage() != right.getItemDamage()) {
            return false;
        }
        String leftId = WildcardPatternGenerator.getGeneratedPatternId(left);
        String rightId = WildcardPatternGenerator.getGeneratedPatternId(right);
        if (!leftId.isEmpty() || !rightId.isEmpty()) {
            return leftId.equals(rightId);
        }
        NBTTagCompound leftTag = left.getTagCompound();
        NBTTagCompound rightTag = right.getTagCompound();
        if (leftTag == rightTag) {
            return true;
        }
        if (leftTag == null || rightTag == null) {
            return false;
        }
        return leftTag.equals(rightTag);
    }
}
