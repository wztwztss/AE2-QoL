/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.item;

import com.wztwzt.ae2_qof.wildport.WildportIds;

import com.wztwzt.ae2_qof.MyMod;

import java.util.List;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.items.misc.ItemEncodedPattern;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import com.wztwzt.ae2_qof.wildport.crafting.WildcardPatternGenerator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public class ItemWildcardPattern extends ItemEncodedPattern {

    public ItemWildcardPattern() {
        this.setMaxStackSize(1);
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        WildcardPatternGenerator.markAsWildcard(stack);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) {
        if (stack != null && stack.getTagCompound() == null) {
            WildcardPatternGenerator.markAsWildcard(stack);
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        WildcardPatternGenerator.markAsWildcard(stack);
        if (!world.isRemote) {
            player.openGui(
                MyMod.instance,
                WildportIds.GUI_WILDCARD_PATTERN,
                world,
                player.inventory.currentItem,
                0,
                0);
        }
        return stack;
    }

    @Override
    public boolean onItemUseFirst(
        ItemStack stack,
        EntityPlayer player,
        World world,
        int x,
        int y,
        int z,
        int side,
        float hitX,
        float hitY,
        float hitZ) {
        WildcardPatternGenerator.markAsWildcard(stack);
        return false;
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
        return WildcardPatternGenerator.getDetailsForItem(stack, world);
    }

    @Override
    public ItemStack getOutput(ItemStack item) {
        if (!WildcardPatternGenerator.isWildcardPattern(item)) {
            return super.getOutput(item);
        }
        return WildcardPatternGenerator.getOutputForItem(item, null);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(
        ItemStack stack,
        EntityPlayer player,
        List<String> lines,
        boolean advancedTooltips) {
        if (!WildcardPatternGenerator.isWildcardPattern(stack)) {
            WildcardPatternGenerator.markAsWildcard(stack);
        }
        lines.add(
            StatCollector.translateToLocalFormatted(
                "tooltip.wildcardpattern.expand_count",
                WildcardPatternState.getExpandedPatternCount(stack)));
        lines.add(StatCollector.translateToLocal("tooltip.wildcardpattern.usage"));
    }
}
