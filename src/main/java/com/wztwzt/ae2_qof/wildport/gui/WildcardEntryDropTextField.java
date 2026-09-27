/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.gui;

import java.util.function.Consumer;

import com.gtnewhorizons.modularui.api.widget.IDragAndDropHandler;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;

import net.minecraft.item.ItemStack;

public class WildcardEntryDropTextField extends TextFieldWidget implements IDragAndDropHandler {

    private final Consumer<ItemStack> dropHandler;

    public WildcardEntryDropTextField(Consumer<ItemStack> dropHandler) {
        this.dropHandler = dropHandler;
    }

    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        if (draggedStack == null || this.dropHandler == null) {
            return false;
        }
        ItemStack copy = draggedStack.copy();
        copy.stackSize = 1;
        this.dropHandler.accept(copy);
        markForUpdate();
        return true;
    }
}
