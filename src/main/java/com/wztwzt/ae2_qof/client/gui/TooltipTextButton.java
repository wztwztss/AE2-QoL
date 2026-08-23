package com.wztwzt.ae2_qof.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import appeng.client.gui.widgets.ITooltip;

/**
 * 带悬停说明的文字按钮：实现 AE2 {@link ITooltip}，
 * 由 AEBaseGui.drawScreen 的按钮 tooltip 机制自动渲染（无需手写 hover 检测）。
 * 文案取自 lang 键（支持 \n 多行）。
 */
public class TooltipTextButton extends GuiButton implements ITooltip {

    private final String tooltipKey;

    public TooltipTextButton(int id, int x, int y, int width, int height, String displayText, String tooltipKey) {
        super(id, x, y, width, height, displayText);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public String getMessage() {
        return I18n.format(this.tooltipKey);
    }

    @Override
    public int xPos() {
        return this.xPosition;
    }

    @Override
    public int yPos() {
        return this.yPosition;
    }

    @Override
    public int getWidth() {
        return getButtonWidth();
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    /**
     * 非 AE2 基类（如原版 GuiContainer 子类）的手动 tooltip 辅助：
     * 返回鼠标命中的第一个可见按钮（未命中返回 null）。
     * 绘制请在 GuiScreen 子类内部用 this.drawHoveringText(...) 完成（protected 可见性）。
     */
    public static TooltipTextButton findHovered(java.util.List buttonList, int mouseX, int mouseY) {
        for (Object o : buttonList) {
            if (o instanceof TooltipTextButton btn && btn.visible
                && btn.enabled
                && mouseX >= btn.xPosition
                && mouseX < btn.xPosition + btn.getButtonWidth()
                && mouseY >= btn.yPosition
                && mouseY < btn.yPosition + btn.height) {
                return btn;
            }
        }
        return null;
    }
}
