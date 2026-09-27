/*
 * 本文件改编自 WildcardPatternforGTNH 1.7.10-1.1.0 的 gui/WildcardEntryDropButton.java
 * （作者 com.myname.wildcardpattern，MIT 许可；用户已授权在保留声明的前提下搬运并优化）。
 *
 * 本仓库的改动：换成我们的包名与中文注释，其余逻辑保持一致（拖入即回调、单例数量、markForUpdate）。
 * 用途：作为「GTNH-MUI 编译基线」的自检类 —— 它足以证明 com.gtnewhorizons.modularui 的
 * IDragAndDropHandler / common.widget.ButtonWidget 在我们的构建与运行环境里都能解析。
 */
package com.wztwzt.ae2_qof.wildport.gui;

import java.util.function.Consumer;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularui.api.widget.IDragAndDropHandler;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;

/**
 * 可接收 NEI/背包拖入的按钮（GTNH-MUI）。
 *
 * <p>拖入时把物品**复制一份**（数量固定为 1）交给回调，避免调用方误改玩家手里的原件；
 * 回调返回后 {@code markForUpdate()} 让 MUI 立刻重绘。
 */
public class WildcardEntryDropButton extends ButtonWidget implements IDragAndDropHandler {

    private final Consumer<ItemStack> dropHandler;

    public WildcardEntryDropButton(Consumer<ItemStack> dropHandler) {
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
