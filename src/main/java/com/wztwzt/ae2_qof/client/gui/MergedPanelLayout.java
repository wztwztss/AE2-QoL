package com.wztwzt.ae2_qof.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;

/**
 * 二合一终端面板的几何布局计算。面板常驻接口终端 GUI 右上角，
 * 处理模式下网格随样板内容自动扩展（最多 27 输入 / 9 输出）。
 * <p>
 * 每次绘制/点击时由 GUI mixin 重新计算，槽与按钮按需重定位（隐藏的槽/按钮移到屏幕外）。
 */
public final class MergedPanelLayout {

    public static final int PANEL_X = IMergedPatternTerminal.PANEL_X;
    public static final int PANEL_Y = IMergedPatternTerminal.PANEL_Y;
    public static final int SLOT = IMergedPatternTerminal.SLOT_SIZE;
    public static final int BTN = 16;

    private MergedPanelLayout() {}

    /** 统计库存中最后一个非空槽的下标 + 1，至少为 1 */
    private static int countActive(net.minecraft.inventory.IInventory inv, int max) {
        int last = -1;
        for (int i = 0; i < max; i++) {
            if (inv.getStackInSlot(i) != null) {
                last = i;
            }
        }
        return last + 1;
    }

    public static Layout compute(GuiContainer gui, boolean crafting) {
        Layout l = new Layout();
        if (gui == null || !(gui.inventorySlots instanceof IMergedPatternTerminal merged)) {
            l.visible = false;
            return l;
        }
        l.visible = true;

        int activeInputs;
        int activeOutputs;
        if (crafting) {
            activeInputs = 9;
            activeOutputs = 1;
        } else {
            activeInputs = countActive(merged.getMergedInputInv(), IMergedPatternTerminal.INPUT_MAX);
            activeOutputs = countActive(merged.getMergedOutputInv(), IMergedPatternTerminal.OUTPUT_MAX);
        }
        l.activeInputs = activeInputs;
        l.activeOutputs = activeOutputs;
        l.crafting = crafting;

        int inputRows = (activeInputs + 2) / 3;
        int outputRows = (activeOutputs + 2) / 3;
        int outBaseY = PANEL_Y + inputRows * SLOT + 4;
        int blankY = outBaseY + outputRows * SLOT + 4;

        l.outBaseY = outBaseY;
        l.blankY = blankY;

        l.resultX = PANEL_X;
        l.resultY = outBaseY;

        l.blankX = PANEL_X;
        l.blankY2 = blankY;
        l.encodedX = PANEL_X + SLOT;
        l.encodedY = blankY;

        int btnY1 = blankY + SLOT + 4;
        l.btnRow1Y = btnY1;
        l.btnRow2Y = btnY1 + BTN;
        l.btnRow3Y = btnY1 + BTN * 2;
        l.btnRow4Y = btnY1 + BTN * 3;

        l.machineNameY = l.btnRow4Y + BTN + 8;

        l.panelLeft = PANEL_X - 3;
        l.panelTop = PANEL_Y - 3;
        l.panelRight = PANEL_X + 3 * SLOT + 3;
        l.panelBottom = l.machineNameY + 11;
        return l;
    }

    public static int panelXFor(int col) {
        return PANEL_X + col * SLOT;
    }

    public static int gridYFor(int row) {
        return PANEL_Y + row * SLOT;
    }

    public static boolean isInPanel(Layout l, int x, int y) {
        return l.visible && x >= l.panelLeft && x <= l.panelRight && y >= l.panelTop && y <= l.panelBottom;
    }

    public static class Layout {

        public boolean visible = false;
        public boolean crafting;
        public int activeInputs;
        public int activeOutputs;
        public int outBaseY;
        public int blankY;
        public int resultX;
        public int resultY;
        public int blankX;
        public int blankY2;
        public int encodedX;
        public int encodedY;
        public int btnRow1Y;
        public int btnRow2Y;
        public int btnRow3Y;
        public int btnRow4Y;
        public int machineNameY;
        public int panelLeft;
        public int panelTop;
        public int panelRight;
        public int panelBottom;
    }
}