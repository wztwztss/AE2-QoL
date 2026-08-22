package com.wztwzt.ae2_qof.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.inventory.Slot;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.wztwzt.ae2_qof.network.MergedTerminalSetStackPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;

/**
 * 二合一终端面板槽数量编辑弹窗：中键点击已填充的输入/输出槽后弹出，
 * 输入目标数量（0 表示清空该槽），确定后发送 {@link MergedTerminalSetStackPacket}。
 */
public class GuiStackSizeDialog extends GuiScreen {

    private static final int BUTTON_OK = 0;
    private static final int BUTTON_CANCEL = 1;

    private final GuiScreen parent;
    private final Slot slot;
    private GuiTextField textField;

    public GuiStackSizeDialog(GuiScreen parent, Slot slot) {
        this.parent = parent;
        this.slot = slot;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        Keyboard.enableRepeatEvents(true);

        this.textField = new GuiTextField(this.fontRendererObj, this.width / 2 - 60, this.height / 2 - 40, 120, 18);
        this.textField.setMaxStringLength(6);
        this.textField.setFocused(true);
        int current = 1;
        try {
            if (this.slot != null && this.slot.getHasStack() && this.slot.getStack() != null) {
                current = this.slot.getStack().stackSize;
            }
        } catch (Throwable ignored) {}
        this.textField.setText(String.valueOf(current));

        int centerY = this.height / 2;
        this.buttonList.add(new GuiButton(BUTTON_OK, this.width / 2 - 60, centerY, 58, 20, translate("gui.ok")));
        this.buttonList.add(new GuiButton(BUTTON_CANCEL, this.width / 2 + 2, centerY, 58, 20, translate("gui.cancel")));
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) return;
        if (button.id == BUTTON_OK) {
            apply();
        } else {
            close();
        }
    }

    private void apply() {
        int size = 1;
        try {
            size = Integer.parseInt(this.textField.getText()
                .trim());
        } catch (Throwable ignored) {}
        if (size < 0) size = 0;
        if (size > 999) size = 999;
        int slotNumber = this.slot != null ? this.slot.slotNumber : -1;
        if (slotNumber >= 0) {
            ModNetwork.CHANNEL.sendToServer(new MergedTerminalSetStackPacket(slotNumber, size));
        }
        close();
    }

    private void close() {
        if (this.mc != null) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { // ESC
            close();
            return;
        }
        if (keyCode == 28 || keyCode == 156) { // Enter
            apply();
            return;
        }
        if (this.textField != null) {
            this.textField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Throwable ignored) {}
        if (this.textField != null) {
            this.textField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void updateScreen() {
        if (this.textField != null) {
            this.textField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        String title = translate("gui.ae2_qof.set_quantity");
        this.fontRendererObj.drawStringWithShadow(
            title,
            this.width / 2 - this.fontRendererObj.getStringWidth(title) / 2,
            this.height / 2 - 62,
            0xFFFFFF);
        if (this.textField != null) {
            this.textField.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private String translate(String key) {
        return StatCollector.translateToLocal(key);
    }
}