/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.gui;

import com.wztwzt.ae2_qof.wildport.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

public class ContainerWildcardPattern extends Container {

    private final int slot;

    public ContainerWildcardPattern(int slot) {
        this.slot = slot;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        ItemStack stack = player.inventory.getStackInSlot(this.slot);
        return stack != null && stack.getItem() == ModItems.wildcardPattern;
    }
}
