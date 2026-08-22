package com.wztwzt.ae2_qof.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import appeng.client.gui.widgets.MEGuiTextField;

public class MergedRenameOverlay {

    private static final ResourceLocation BG_TEXTURE = new ResourceLocation(
        "appliedenergistics2", "textures/guis/renamer.png");

    private boolean active;
    private int slotNumber;
    private int guiLeft;
    private int guiTop;
    private int ox, oy;

    private MEGuiTextField nameField;
    private boolean renameRequested;

    public boolean isActive() { return active; }
    public boolean isRenameRequested() { return renameRequested; }
    public int getSlotNumber() { return slotNumber; }
    public String getNewName() { return nameField != null ? nameField.getText() : ""; }

    public void open(int slotNumber, String currentName, int guiLeft, int guiTop, int ySize) {
        this.active = true;
        this.slotNumber = slotNumber;
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.renameRequested = false;
        // 覆盖层放在玩家背包区域上方（盖住背包无妨）
        this.ox = (209 - 176) / 2;
        this.oy = ySize - 98;
        rebuildWidgets();
        this.nameField.setText(currentName != null ? currentName : "");
        this.nameField.setCursorPositionEnd();
        this.nameField.setSelectionPos(0);
        this.nameField.setFocused(true);
    }

    public void close() {
        this.active = false;
        this.slotNumber = -1;
        this.renameRequested = false;
        if (this.nameField != null) this.nameField.setFocused(false);
    }

    private void rebuildWidgets() {
        nameField = new MEGuiTextField(152, 12);
        nameField.x = ox + 12;
        nameField.y = oy + 35;
        nameField.setMaxStringLength(64);
        nameField.setFocused(true);
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
        mc.getTextureManager().bindTexture(BG_TEXTURE);
        drawTexturedModalRect(ox, oy, 0, 0, 176, 100);

        String title = "\u91cd\u547d\u540d:";
        fr.drawStringWithShadow(title, ox + 12, oy + 8, 0xFFE0E0E0);

        nameField.drawTextBox();
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!active) return false;
        int gx = mouseX - guiLeft;
        int gy = mouseY - guiTop;
        if (mouseButton == 0) {
            nameField.mouseClicked(gx, gy, mouseButton);
        }
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        if (!active) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) { close(); return true; }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            renameRequested = true;
            return true;
        }
        nameField.textboxKeyTyped(typedChar, keyCode);
        return true;
    }

    public void updateScreen() {
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