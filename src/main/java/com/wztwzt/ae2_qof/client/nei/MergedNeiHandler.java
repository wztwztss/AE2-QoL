package com.wztwzt.ae2_qof.client.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.wztwzt.ae2_qof.client.gui.MergedPanelLayout;
import com.wztwzt.ae2_qof.merged.GuiMergedTerminal;

import codechicken.nei.api.INEIGuiAdapter;

/**
 * 隐藏合并终端右侧样板面板占据区域的 NEI 物品栏格子，避免物品面板与面板重叠。
 * <p>
 * NEI 2.8 的 ItemsGrid 会为每个物品格调用已注册 handler 的 hideItemPanelSlot(gui, x, y, w, h)
 * （屏幕坐标），返回 true 的格子留空。AE2 自带 NEIGuiHandler 只转发 GuiMEMonitorable 系，
 * 我们的 GUI 继承 GuiInterfaceTerminal → AEBaseGui，故需自行注册。
 */
public class MergedNeiHandler extends INEIGuiAdapter {

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        if (gui instanceof GuiMergedTerminal gmt) {
            int rx = gmt.getGuiLeft() + MergedPanelLayout.PANEL_X;
            int ry = gmt.getGuiTop() + MergedPanelLayout.PANEL_Y;
            int rw = rx + MergedPanelLayout.PANEL_W;
            int rh = ry + MergedPanelLayout.PANEL_BOTTOM;
            return x < rw && x + w > rx && y < rh && y + h > ry;
        }
        return false;
    }
}