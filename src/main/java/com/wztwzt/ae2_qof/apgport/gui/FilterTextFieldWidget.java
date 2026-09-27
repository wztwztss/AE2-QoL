/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.gui;

import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularui.api.widget.IDragAndDropHandler;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;

class FilterTextFieldWidget extends TextFieldWidget implements IDragAndDropHandler {

    private final Function<ItemStack, String> stackFormatter;
    private final Function<ItemStack, ExplicitFilterDropFormatter.DropChoices> dropChoicesBuilder;
    private Consumer<ExplicitFilterDropFormatter.DropChoices> dropChoicesListener;

    FilterTextFieldWidget() {
        this(ExplicitFilterDropFormatter::format, ExplicitFilterDropFormatter::buildChoices);
    }

    FilterTextFieldWidget(Function<ItemStack, String> stackFormatter) {
        this(stackFormatter, null);
    }

    private FilterTextFieldWidget(Function<ItemStack, String> stackFormatter,
        Function<ItemStack, ExplicitFilterDropFormatter.DropChoices> dropChoicesBuilder) {
        this.stackFormatter = stackFormatter != null ? stackFormatter : ExplicitFilterDropFormatter::format;
        this.dropChoicesBuilder = dropChoicesBuilder;
    }

    FilterTextFieldWidget setDropChoicesListener(
        Consumer<ExplicitFilterDropFormatter.DropChoices> dropChoicesListener) {
        this.dropChoicesListener = dropChoicesListener;
        return this;
    }

    void applyDropChoice(ExplicitFilterDropFormatter.DropChoice choice) {
        if (choice == null) {
            return;
        }
        setText(choice.getToken());
        markForUpdate();
    }

    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        if (draggedStack == null) {
            return false;
        }

        ExplicitFilterDropFormatter.DropChoices choices = dropChoicesBuilder != null
            ? dropChoicesBuilder.apply(draggedStack)
            : null;
        String formatted = choices != null ? choices.getDefaultToken() : stackFormatter.apply(draggedStack);
        if (formatted == null || formatted.trim()
            .isEmpty()) {
            return false;
        }

        setText(formatted);
        markForUpdate();
        if (dropChoicesListener != null) {
            dropChoicesListener.accept(
                choices != null && !choices.isEmpty() ? choices : ExplicitFilterDropFormatter.singleChoice(formatted));
        }
        return true;
    }
}
