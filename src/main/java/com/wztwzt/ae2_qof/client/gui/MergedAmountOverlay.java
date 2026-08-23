package com.wztwzt.ae2_qof.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import appeng.client.gui.widgets.MEGuiTextField;
import appeng.util.calculators.Calculator;

public class MergedAmountOverlay {

    private static final ResourceLocation BG_TEXTURE = new ResourceLocation(
        "appliedenergistics2",
        "textures/guis/patternMulti.png");

    private boolean active;
    private int slotNumber;
    private boolean setRequested;
    private int guiLeft;
    private int guiTop;
    private int ox, oy;

    private MEGuiTextField amountTextField;
    private GuiButton setBtn;
    private GuiButton[] plusBtns = new GuiButton[4];
    private GuiButton[] minusBtns = new GuiButton[4];

    public boolean isSetRequested() {
        return setRequested;
    }

    public boolean isActive() {
        return active;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    public void open(int slotNumber, int currentSize, int guiLeft, int guiTop, int ySize) {
        this.active = true;
        this.slotNumber = slotNumber;
        this.setRequested = false;
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        // 覆盖层居中放在玩家背包区域上方（盖住背包无妨）
        this.ox = (209 - 176) / 2;
        this.oy = ySize - 98;
        rebuildWidgets();
        this.amountTextField.setText(String.valueOf(currentSize));
        this.amountTextField.setCursorPositionEnd();
        this.amountTextField.setSelectionPos(0);
        this.amountTextField.setFocused(true);
    }

    public void close() {
        this.active = false;
        this.slotNumber = -1;
        this.setRequested = false;
        if (this.amountTextField != null) this.amountTextField.setFocused(false);
    }

    public int getAmount() {
        if (amountTextField == null) return 0;
        try {
            double value = Calculator.conversion(amountTextField.getText());
            if (Double.isNaN(value)) return 0;
            double rounded = Math.round(value);
            // 防超大输入溢出 int
            if (rounded > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            return (int) Math.max(0, rounded);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void rebuildWidgets() {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int[] plusX = { 20, 48, 82, 120 };
        int[] widths = { 22, 28, 32, 38 };
        int[] vals = { 1, 10, 100, 1000 };

        for (int i = 0; i < 4; i++) {
            plusBtns[i] = new GuiButton(100 + i, ox + plusX[i], oy + 26, widths[i], 20, "+" + vals[i]);
        }

        amountTextField = new MEGuiTextField(73, 12);
        amountTextField.x = ox + 48;
        amountTextField.y = oy + 55;
        amountTextField.setMaxStringLength(16);
        amountTextField.setFocused(true);

        setBtn = new GuiButton(104, ox + 128, oy + 51, 38, 20, "Set");

        for (int i = 0; i < 4; i++) {
            minusBtns[i] = new GuiButton(106 + i, ox + plusX[i], oy + 75, widths[i], 20, "-" + vals[i]);
        }
    }

    public void draw(int mouseX, int mouseY) {
        if (!active) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;
        int gx = mouseX - guiLeft;
        int gy = mouseY - guiTop;

        // 槽位物品在深度测试+zLevel=100下渲染，覆盖层必须关掉深度测试才能压在物品之上
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager()
            .bindTexture(BG_TEXTURE);
        drawTexturedModalRect(ox, oy, 0, 0, 176, 100);

        fr.drawStringWithShadow("\u7f16\u8f91\u6570\u91cf:", ox + 12, oy + 8, 0xFFE0E0E0);

        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        String[] plusLabels = shift ? new String[] { "x2", "x8", "x64", "x512" }
            : new String[] { "+1", "+10", "+100", "+1000" };
        String[] minusLabels = shift ? new String[] { "/2", "/8", "/64", "/512" }
            : new String[] { "-1", "-10", "-100", "-1000" };

        for (int i = 0; i < 4; i++) {
            plusBtns[i].displayString = plusLabels[i];
            plusBtns[i].drawButton(mc, gx, gy);
        }

        amountTextField.drawTextBox();
        setBtn.drawButton(mc, gx, gy);

        for (int i = 0; i < 4; i++) {
            minusBtns[i].displayString = minusLabels[i];
            minusBtns[i].drawButton(mc, gx, gy);
        }

        try {
            double val = Calculator.conversion(amountTextField.getText());
            if (!Double.isNaN(val) && val > 0) {
                long rv = (long) Math.round(val);
                String fmt;
                if (rv >= 1000000) fmt = "= " + String.format("%.1fM", rv / 1000000.0);
                else if (rv >= 1000) fmt = "= " + String.format("%.1fK", rv / 1000.0);
                else fmt = "= " + rv;
                fr.drawStringWithShadow(fmt, ox + 50, oy + 93, 0xFFAAAAAA);
            }
        } catch (Throwable ignored) {}
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!active) return false;
        int gx = mouseX - guiLeft;
        int gy = mouseY - guiTop;
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        for (int i = 0; i < 4; i++) {
            if (plusBtns[i].mousePressed(Minecraft.getMinecraft(), gx, gy)) {
                Minecraft.getMinecraft().thePlayer.playSound("random.click", 1.0F, 1.0F);
                if (shift) {
                    int[] mulVals = { 2, 8, 64, 512 };
                    multiplyAmount(mulVals[i]);
                } else {
                    int[] addVals = { 1, 10, 100, 1000 };
                    addAmount(addVals[i]);
                }
                return true;
            }
        }

        for (int i = 0; i < 4; i++) {
            if (minusBtns[i].mousePressed(Minecraft.getMinecraft(), gx, gy)) {
                Minecraft.getMinecraft().thePlayer.playSound("random.click", 1.0F, 1.0F);
                if (shift) {
                    int[] divVals = { 2, 8, 64, 512 };
                    divideAmount(divVals[i]);
                } else {
                    int[] subVals = { 1, 10, 100, 1000 };
                    addAmount(-subVals[i]);
                }
                return true;
            }
        }

        if (setBtn.mousePressed(Minecraft.getMinecraft(), gx, gy)) {
            Minecraft.getMinecraft().thePlayer.playSound("random.click", 1.0F, 1.0F);
            setRequested = true;
            return true;
        }

        if (mouseButton == 0) {
            amountTextField.mouseClicked(gx, gy, mouseButton);
        }
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!active) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            return true;
        }
        amountTextField.textboxKeyTyped(typedChar, keyCode);
        return true;
    }

    public void updateScreen() {}

    private void addAmount(int delta) {
        long current = getCurrentAmount();
        if (current == 1 && delta > 1) current = 0;
        current += delta;
        if (current < 1) current = 1;
        amountTextField.setText(Long.toString(current));
        amountTextField.setCursorPositionEnd();
    }

    private void multiplyAmount(int factor) {
        long current = getCurrentAmount();
        if (current < 1) current = 1;
        current *= factor;
        amountTextField.setText(Long.toString(current));
        amountTextField.setCursorPositionEnd();
    }

    private void divideAmount(int divisor) {
        long current = getCurrentAmount();
        if (current < 1) current = 1;
        current = Math.max(1, current / divisor);
        amountTextField.setText(Long.toString(current));
        amountTextField.setCursorPositionEnd();
    }

    private long getCurrentAmount() {
        try {
            double value = Calculator.conversion(amountTextField.getText());
            if (Double.isNaN(value) || value <= 0) return 0;
            return (long) Math.round(value);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {
        float f = 1.0F / 256.0F;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + h, 0, u * f, (v + h) * f);
        tess.addVertexWithUV(x + w, y + h, 0, (u + w) * f, (v + h) * f);
        tess.addVertexWithUV(x + w, y, 0, (u + w) * f, v * f);
        tess.addVertexWithUV(x, y, 0, u * f, v * f);
        tess.draw();
    }
}
