package com.wztwzt.ae2_qof.mixin.ae;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;
import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.client.event.MergedTerminalPanelHandler;
import com.wztwzt.ae2_qof.client.gui.MergedPanelLayout;
import com.wztwzt.ae2_qof.mixin.GuiContainerAccessor;

import appeng.client.gui.implementations.GuiInterfaceTerminal;

/**
 * 为接口终端 GUI 绘制右上角"样板编码面板"并拦截面板区域点击。
 * <p>
 * 面板覆盖 x149..203 区域；该区域 x<=184 段会被 {@code masterList.mouseClicked}
 * 吞掉，因此本 mixin 在 mouseClicked HEAD 自行分发面板按钮与面板槽点击。
 */
@Mixin(GuiInterfaceTerminal.class)
public abstract class MixinGuiInterfaceTerminal {

    @Shadow
    protected Minecraft mc;

    @Shadow
    protected FontRenderer fontRendererObj;

    @Shadow
    protected List<GuiButton> buttonList;

    @Shadow
    public Container inventorySlots;

    @Shadow
    protected Slot lastClickSlot;

    @Shadow
    protected long lastClickTime;

    @Shadow
    protected int lastClickButton;

    @Inject(method = "drawFG", at = @At("TAIL"), remap = false)
    private void ae2qol$drawMergedPanel(int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        try {
            if (!(this.inventorySlots instanceof IMergedPatternTerminal)) {
                return;
            }
            boolean crafting = MergedTerminalPanelHandler.mergedCraftingMode;
            MergedPanelLayout.Layout layout = MergedPanelLayout.compute((GuiContainer) (Object) this, crafting);
            if (!layout.visible) {
                return;
            }
            repositionPanel(layout);
            MergedTerminalPanelHandler.reposition(layout);

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            // 面板底
            drawFilledRect(layout.panelLeft, layout.panelTop, layout.panelRight, layout.panelBottom, 0xCC262626);
            // 边框
            drawFilledRect(layout.panelLeft, layout.panelTop, layout.panelRight, layout.panelTop + 1, 0xFF555555);
            drawFilledRect(layout.panelLeft, layout.panelBottom - 1, layout.panelRight, layout.panelBottom, 0xFF555555);
            drawFilledRect(layout.panelLeft, layout.panelTop, layout.panelLeft + 1, layout.panelBottom, 0xFF555555);
            drawFilledRect(layout.panelRight - 1, layout.panelTop, layout.panelRight, layout.panelBottom, 0xFF555555);

            // 槽底
            for (int i = 0; i < layout.activeInputs; i++) {
                drawSlotBg(MergedPanelLayout.panelXFor(i % IMergedPatternTerminal.INPUT_COLS), MergedPanelLayout.gridYFor(i / IMergedPatternTerminal.INPUT_COLS));
            }
            for (int j = 0; j < layout.activeOutputs; j++) {
                drawSlotBg(MergedPanelLayout.panelXFor(j % IMergedPatternTerminal.INPUT_COLS), layout.outBaseY + (j / IMergedPatternTerminal.INPUT_COLS) * IMergedPatternTerminal.SLOT_SIZE);
            }
            if (layout.crafting) {
                drawSlotBg(layout.resultX, layout.resultY);
            }
            drawSlotBg(layout.blankX, layout.blankY2);
            drawSlotBg(layout.encodedX, layout.encodedY);

            // 机器名反馈
            String name = ClientState.mergedMachineName;
            String text = (name != null && !name.isEmpty()) ? "\u673a\u5668: " + name : "\u673a\u5668: -";
            this.fontRendererObj.drawString(text, layout.panelLeft + 3, layout.machineNameY, 0xFFE0E0E0);
        } catch (Throwable ignored) {}
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), remap = false)
    private void ae2qol$mergedPanelMouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        try {
            if (!(this.inventorySlots instanceof IMergedPatternTerminal)) {
                return;
            }
            boolean crafting = MergedTerminalPanelHandler.mergedCraftingMode;
            MergedPanelLayout.Layout layout = MergedPanelLayout.compute((GuiContainer) (Object) this, crafting);
            if (!layout.visible || !MergedPanelLayout.isInPanel(layout, mouseX, mouseY)) {
                return;
            }
            repositionPanel(layout);

            if (mouseButton == 0) {
                for (Object o : this.buttonList) {
                    GuiButton b = (GuiButton) o;
                    if (b != null && b.mousePressed(this.mc, mouseX, mouseY)) {
                        this.mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
                        MergedTerminalPanelHandler.onButtonClicked((GuiContainer) (Object) this, b.id);
                        ci.cancel();
                        return;
                    }
                }
            }

            if (mouseButton == 0 || mouseButton == 1) {
                Slot slot = findSlotAt(mouseX, mouseY);
                int slotNum = slot != null ? slot.slotNumber : -999;
                // 光标为空时等价于 vanilla 单击/Shift 单击；光标持有物品时的放置由 vanilla mouseMovedOrUp 完成
                if (this.mc.thePlayer.inventory.getItemStack() == null) {
                    boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                    this.mc.playerController.windowClick(
                        this.inventorySlots.windowId,
                        slotNum,
                        mouseButton,
                        shift ? 1 : 0,
                        this.mc.thePlayer);
                }
                this.lastClickSlot = slot;
                this.lastClickTime = Minecraft.getSystemTime();
                this.lastClickButton = mouseButton;
                ci.cancel();
            }
        } catch (Throwable ignored) {}
    }

    private Slot findSlotAt(int x, int y) {
        GuiContainerAccessor acc = (GuiContainerAccessor) this;
        int gx = x - acc.getGuiLeft();
        int gy = y - acc.getGuiTop();
        for (Object o : this.inventorySlots.inventorySlots) {
            Slot slot = (Slot) o;
            if (gx >= slot.xDisplayPosition - 1 && gx < slot.xDisplayPosition + 17
                && gy >= slot.yDisplayPosition - 1 && gy < slot.yDisplayPosition + 17) {
                return slot;
            }
        }
        return null;
    }

    /** 按当前布局重摆面板槽（隐藏槽移到屏幕外），drawFG 与 mouseClicked 共用，保证点击坐标一致 */
    private void repositionPanel(MergedPanelLayout.Layout layout) {
        if (!(this.inventorySlots instanceof IMergedPatternTerminal merged)) {
            return;
        }
        int base = merged.getMergedSlotBase();
        List slotList = this.inventorySlots.inventorySlots;
        for (int i = 0; i < IMergedPatternTerminal.INPUT_MAX; i++) {
            Slot s = (Slot) slotList.get(base + i);
            boolean visible = i < layout.activeInputs;
            s.xDisplayPosition = visible ? MergedPanelLayout.panelXFor(i % IMergedPatternTerminal.INPUT_COLS) : -1000;
            s.yDisplayPosition = visible ? MergedPanelLayout.gridYFor(i / IMergedPatternTerminal.INPUT_COLS) : -1000;
        }
        for (int j = 0; j < IMergedPatternTerminal.OUTPUT_MAX; j++) {
            Slot s = (Slot) slotList.get(base + IMergedPatternTerminal.INPUT_MAX + j);
            boolean visible = !layout.crafting && j < layout.activeOutputs;
            s.xDisplayPosition = visible ? MergedPanelLayout.panelXFor(j % IMergedPatternTerminal.INPUT_COLS) : -1000;
            s.yDisplayPosition = visible ? layout.outBaseY + (j / IMergedPatternTerminal.INPUT_COLS) * IMergedPatternTerminal.SLOT_SIZE : -1000;
        }
        Slot result = (Slot) slotList.get(base + IMergedPatternTerminal.INPUT_MAX + IMergedPatternTerminal.OUTPUT_MAX);
        result.xDisplayPosition = layout.crafting ? layout.resultX : -1000;
        result.yDisplayPosition = layout.crafting ? layout.resultY : -1000;
        Slot blank = (Slot) slotList.get(base + IMergedPatternTerminal.INPUT_MAX + IMergedPatternTerminal.OUTPUT_MAX + 1);
        blank.xDisplayPosition = layout.blankX;
        blank.yDisplayPosition = layout.blankY2;
        Slot encoded = (Slot) slotList.get(base + IMergedPatternTerminal.INPUT_MAX + IMergedPatternTerminal.OUTPUT_MAX + 2);
        encoded.xDisplayPosition = layout.encodedX;
        encoded.yDisplayPosition = layout.encodedY;
    }

    private static void drawSlotBg(int x, int y) {
        drawFilledRect(x, y, x + IMergedPatternTerminal.SLOT_SIZE, y + IMergedPatternTerminal.SLOT_SIZE, 0x66333333);
        drawFilledRect(x, y, x + 1, y + IMergedPatternTerminal.SLOT_SIZE, 0x66444444);
        drawFilledRect(x + IMergedPatternTerminal.SLOT_SIZE - 1, y, x + IMergedPatternTerminal.SLOT_SIZE, y + IMergedPatternTerminal.SLOT_SIZE, 0x66222222);
        drawFilledRect(x, y, x + IMergedPatternTerminal.SLOT_SIZE, y + 1, 0x66444444);
        drawFilledRect(x, y + IMergedPatternTerminal.SLOT_SIZE - 1, x + IMergedPatternTerminal.SLOT_SIZE, y + IMergedPatternTerminal.SLOT_SIZE, 0x66222222);
    }

    private static void drawFilledRect(int x1, int y1, int x2, int y2, int argb) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        float a = (float) ((argb >> 24) & 255) / 255.0F;
        float r = (float) ((argb >> 16) & 255) / 255.0F;
        float g = (float) ((argb >> 8) & 255) / 255.0F;
        float b = (float) (argb & 255) / 255.0F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertex(x1, y2, 0.0);
        tess.addVertex(x2, y2, 0.0);
        tess.addVertex(x2, y1, 0.0);
        tess.addVertex(x1, y1, 0.0);
        tess.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}