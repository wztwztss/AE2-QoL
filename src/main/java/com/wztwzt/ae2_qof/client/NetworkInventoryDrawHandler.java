package com.wztwzt.ae2_qof.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.wztwzt.ae2_qof.util.CountFormatter;

public class NetworkInventoryDrawHandler {

    public static void drawInventoryOverlay(int x, int y, ItemStack stack, FontRenderer fr) {
        if (!OverlayConfig.isEnabled() || !NetworkInventoryCache.hasData() || stack == null) {
            return;
        }

        long count = NetworkInventoryCache.getCount(stack);
        boolean craftable = NetworkInventoryCache.isCraftable(stack);

        if (count <= 0 && !craftable) {
            return;
        }

        int slotSize = 18;

        if (craftable) {
            GL11.glPushMatrix();
            GL11.glScalef(0.6f, 0.6f, 1.0f);
            String mark = "+";
            int scaledX = (int) ((x + 1) / 0.6f);
            int scaledY = (int) ((y + 1) / 0.6f);
            fr.drawStringWithShadow(mark, scaledX, scaledY, 0x55FF55);
            GL11.glPopMatrix();
        }

        if (count > 0) {
            GL11.glPushMatrix();
            GL11.glScalef(0.6f, 0.6f, 1.0f);
            String label = CountFormatter.format(count);
            int scaledX = (int) ((x + 1) / 0.6f);
            int scaledY = (int) ((y + slotSize - 5) / 0.6f);
            fr.drawStringWithShadow(label, scaledX, scaledY, 0xAAFFFF);
            GL11.glPopMatrix();
        }
    }
}
