package com.wztwzt.ae2_qof.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import appeng.client.gui.widgets.ITooltip;

/**
 * 带悬停说明的文字按钮：实现 AE2 {@link ITooltip}，
 * 由 AEBaseGui.drawScreen 的按钮 tooltip 机制自动渲染（无需手写 hover 检测）。
 * 文案取自 lang 键。
 * <p>
 * - 多行：lang 中的 {@code \n} 是字面反斜杠+n（MC 不转义），此处统一替换为真实换行符；
 *   AE2 drawTooltip / 手动绘制路径按 "\n" split 即可分行。
 * - 延迟显示：悬停满 {@link #HOVER_DELAY_MS} 毫秒才显示（{@link #shouldRenderTooltip}），
 *   由宿主 GUI 在渲染前调用闸门判定。
 */
public class TooltipTextButton extends GuiButton implements ITooltip {

    /** 悬停多少毫秒后才显示 tooltip。 */
    public static final long HOVER_DELAY_MS = 1000L;

    /**
     * 取 lang 文本并把字面 {@code \n} 统一替换为真实换行符。
     * 供本按钮与其他按钮 tooltip（如 AE2 GuiToggleButton 的 hint 实参）共用。
     */
    public static String langLines(String key) {
        return I18n.format(key)
            .replace("\\n", "\n");
    }

    private final String tooltipKey;

    /** 当前悬停起始时间；-1 = 未在悬停。 */
    private long hoverStartMs = -1L;

    public TooltipTextButton(int id, int x, int y, int width, int height, String displayText, String tooltipKey) {
        super(id, x, y, width, height, displayText);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public String getMessage() {
        return langLines(this.tooltipKey);
    }

    /**
     * tooltip 显示闸门：鼠标在本按钮上持续满 {@link #HOVER_DELAY_MS} 才返回 true；
     * 离开按钮即重置计时。由宿主每帧调用。
     */
    public boolean shouldRenderTooltip(int mouseX, int mouseY) {
        final boolean inside = this.visible
            && this.enabled
            && mouseX >= this.xPosition
            && mouseX < this.xPosition + getButtonWidth()
            && mouseY >= this.yPosition
            && mouseY < this.yPosition + this.height;
        if (!inside) {
            this.hoverStartMs = -1L;
            return false;
        }
        final long now = Minecraft.getSystemTime();
        if (this.hoverStartMs < 0) {
            this.hoverStartMs = now;
        }
        return now - this.hoverStartMs >= HOVER_DELAY_MS;
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
