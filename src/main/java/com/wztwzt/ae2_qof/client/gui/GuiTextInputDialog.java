package com.wztwzt.ae2_qof.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import com.wztwzt.ae2_qof.MyMod;

/**
 * 「原生输入对话框」（待定版本 fixes IME 缺口）：用一个**原版 {@link GuiTextField}** 让玩家输入任意文本
 * （含中文），回车或"确定"后把结果交给回调。
 *
 * <h2>为什么需要它</h2>
 * 我们的通配样板窗口是 GTNH-ModularUI（MUI1）自绘界面，在这类自绘界面里**系统输入法（IME）无法启用**
 * （用户实测：开着该界面时"切换不了中文输入法"；同一整合包里别的模组用原版输入框的搜索栏却能打中文）。
 * 排查已排除"我们拦字符"：{@code TextFieldHandler.test(...)} 只在设了正则/长度上限时才拒绝，而我们的框
 * 没设正则；MUI1 支持 Ctrl+V 粘贴（走剪贴板、不需要 IME）也是旁证。
 *
 * <h2>做法</h2>
 * 不改宿主窗口布局，只在每个文本框旁加一个"改"按钮，点开本对话框（原版输入框 ⇒ 中文可打），
 * 回车/确定写回原控件并触发原来的 setter（列表即时刷新）。Esc/取消 = 不改。
 *
 * <p>配色与文字遵守用户偏好：浅底深字、不使用 § 颜色码。
 */
public class GuiTextInputDialog extends GuiScreen {

    private final String title;
    private final String initial;
    private final Consumer<String> onConfirm;
    private GuiTextField field;

    public GuiTextInputDialog(String title, String initial, Consumer<String> onConfirm) {
        this.title = title == null ? "" : title;
        this.initial = initial == null ? "" : initial;
        this.onConfirm = onConfirm;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int cx = this.width / 2;
        int cy = this.height / 2;
        int fieldWidth = Math.min(320, this.width - 80);
        this.field = new GuiTextField(this.fontRendererObj, cx - fieldWidth / 2, cy - 10, fieldWidth, 20);
        this.field.setMaxStringLength(256);
        this.field.setText(this.initial);
        this.field.setFocused(true);
        this.buttonList.add(new GuiButton(1, cx - 100, cy + 20, 90, 20, "确定"));
        this.buttonList.add(new GuiButton(2, cx + 10, cy + 20, 90, 20, "取消"));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        try {
            if (keyCode == 1) { // Esc
                this.mc.displayGuiScreen(null);
                return;
            }
            if (keyCode == 28 || keyCode == 156) { // Enter / 小键盘回车
                confirm();
                return;
            }
            // 交给原版输入框：IME 提交的中文字符正是从这里（typedChar）进来
            if (this.field != null && this.field.textboxKeyTyped(typedChar, keyCode)) return;
            super.keyTyped(typedChar, keyCode);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 文本输入对话框按键处理失败", t);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) confirm();
        else this.mc.displayGuiScreen(null);
    }

    private void confirm() {
        String value = this.field == null ? this.initial : this.field.getText();
        try {
            if (this.onConfirm != null) this.onConfirm.accept(value);
            MyMod.LOG.info("[AE2QoL] 文本输入对话框已写回：长度={} 内容={}", value == null ? 0 : value.length(), value);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 文本输入对话框写回失败", t);
        }
        this.mc.displayGuiScreen(null);
    }

    @Override
    public void updateScreen() {
        if (this.field != null) this.field.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.drawCenteredString(this.fontRendererObj, this.title, cx, cy - 40, 0x202020);
        // 提示：这里用的是原版输入框，可正常切换/使用中文输入法
        this.drawCenteredString(this.fontRendererObj, "此处可输入中文（原版输入框）；回车=确定，Esc=取消", cx, cy - 26, 0x505050);
        if (this.field != null) {
            // 浅底深字
            drawRect(
                this.field.xPosition - 2,
                this.field.yPosition - 2,
                this.field.xPosition + this.field.width + 2,
                this.field.yPosition + this.field.height + 2,
                0xF2F2F2);
            this.field.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
