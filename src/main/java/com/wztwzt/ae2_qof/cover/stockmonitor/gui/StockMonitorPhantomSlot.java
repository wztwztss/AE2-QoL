package com.wztwzt.ae2_qof.cover.stockmonitor.gui;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

/**
 * 库存检测覆盖板专用 Phantom 槽：
 * 1. 不显示物品数量（只显示标记图标，数量在下方"目标数量"输入框里设置）
 * 2. Shift+左键点击取消标记（清空槽）
 */
public class StockMonitorPhantomSlot extends PhantomItemSlot {

    @Override
    protected void drawSlotAmountText(int amount, String format) {
        // 不显示数量
    }

    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        MouseData mouseData = MouseData.create(mouseButton);
        // Shift+左键：清空标记
        if (mouseData.shift && mouseData.mouseButton == 0) {
            getSyncHandler().updateFromClient(null, 0);
            return Interactable.Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    @Override
    public StockMonitorPhantomSlot slot(ModularSlot slot) {
        return (StockMonitorPhantomSlot) super.slot(slot);
    }
}
