package com.wztwzt.ae2_qof.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

import com.wztwzt.ae2_qof.network.MergedTerminalSetStackPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;

import appeng.util.calculators.Calculator;

public class GuiMergedAmount extends GuiScreen {

    private static final int BUTTON_SET = 0;
    private static final int BUTTON_CANCEL = 1;

    private final int slotNumber;
    private final int currentSize;
    private GuiTextField amountTextField;

    public GuiMergedAmount(int slotNumber, int currentSize) {
        this.slotNumber = slotNumber;
        this.currentSize = currentSize;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.amountTextField = new GuiTextField(this.fontRendererObj, cx - 36, cy - 20, 73, 18);
        this.amountTextField.setMaxStringLength(6);
        this.amountTextField.setText(String.valueOf(currentSize));
        this.amountTextField.setCursorPositionEnd();
        this.amountTextField.setFocused(true);
        this.buttonList.add(new GuiButton(BUTTON_SET, cx - 38, cy + 4, 76, 20, "Set"));
        this.buttonList
            .add(new GuiButton(BUTTON_CANCEL, cx - 38, cy + 28, 76, 20, StatCollector.translateToLocal("gui.cancel")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) return;
        if (button.id == BUTTON_SET) {
            apply();
        } else if (button.id == BUTTON_CANCEL) {
            close();
        }
    }

    private void apply() {
        int size = getAmount();
        if (size < 0) size = 0;
        if (size > 999) size = 999;
        if (slotNumber >= 0) {
            ModNetwork.CHANNEL.sendToServer(new MergedTerminalSetStackPacket(slotNumber, size));
        }
        close();
    }

    private int getAmount() {
        String text = this.amountTextField.getText();
        try {
            double value = Calculator.conversion(text);
            if (Double.isNaN(value)) return 0;
            return (int) Math.round(value);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void close() {
        if (this.mc != null) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            close();
            return;
        }
        if (keyCode == 28 || keyCode == 156) {
            apply();
            return;
        }
        if (this.amountTextField != null) {
            this.amountTextField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Throwable ignored) {}
        if (this.amountTextField != null) {
            this.amountTextField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void updateScreen() {
        if (this.amountTextField != null) {
            this.amountTextField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        String title = StatCollector.translateToLocal("gui.ae2_qof.set_quantity");
        this.fontRendererObj.drawStringWithShadow(
            title,
            this.width / 2 - this.fontRendererObj.getStringWidth(title) / 2,
            this.height / 2 - 42,
            0xFFE0E0E0);
        if (this.amountTextField != null) {
            this.amountTextField.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
