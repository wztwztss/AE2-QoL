package com.wztwzt.ae2_qof.mixin.nei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.client.NetworkInventoryDrawHandler;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemsGrid;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.PanelWidget;

@Mixin(value = PanelWidget.class, remap = false)
public abstract class MixinPanelWidgetDraw {

    @Inject(method = "draw(II)V", at = @At("TAIL"))
    private void ae2AutoPatternUpload$overlayAfterPanelDraw(int mousex, int mousey, CallbackInfo ci) {
        try {
            PanelWidget<?> self = (PanelWidget<?>) (Object) this;

            int panelX = self.x;
            int screenWidth = Minecraft.getMinecraft().currentScreen.width;
            if (panelX >= screenWidth / 2) return;

            ItemsGrid<?, ?> grid = self.getGrid();
            if (grid == null) return;

            List<?> mask = grid.getMask();
            if (mask == null || mask.isEmpty()) return;

            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            try {
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                for (Object obj : mask) {
                    ItemsGridSlot slot = (ItemsGridSlot) obj;
                    ItemStack item = slot.getItemStack();
                    if (item == null) continue;

                    Rectangle4i rect = grid.getSlotRect(slot.slotIndex);
                    if (rect == null) continue;

                    NetworkInventoryDrawHandler.drawInventoryOverlay(rect.x, rect.y, item, fr);
                }
            } finally {
                GL11.glPopAttrib();
            }
        } catch (Throwable ignored) {}
    }
}
