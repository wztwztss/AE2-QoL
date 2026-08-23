package com.wztwzt.ae2_qof.client.render;

import java.util.ArrayDeque;
import java.util.Queue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 合成完成通知：屏幕右上角滑入提示，显示合成物品图标与数量。
 */
@SideOnly(Side.CLIENT)
public class CraftingNotificationOverlay {

    public static final CraftingNotificationOverlay INSTANCE = new CraftingNotificationOverlay();

    private static final ResourceLocation BACKGROUND = new ResourceLocation("textures/gui/widgets.png");
    private static final int DELAY = 3000;
    private static final int FADE_IN = 1000;
    private static final int WIDTH = 140;
    private static final int TOP = 6;

    private final Queue<NotificationEntry> events = new ArrayDeque<>();
    private final Minecraft mc = Minecraft.getMinecraft();

    /** 复用的 RenderItem（#52）：优先取 GuiScreen.itemRender 静态实例，dev 名与 SRG 名都试；均失败才自建。 */
    private static RenderItem renderItem;

    private static RenderItem getRenderItem() {
        if (renderItem != null) {
            return renderItem;
        }
        for (String name : new String[] { "itemRender", "field_146296_j" }) {
            try {
                java.lang.reflect.Field f = net.minecraft.client.gui.GuiScreen.class.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(null);
                if (v instanceof RenderItem) {
                    renderItem = (RenderItem) v;
                    return renderItem;
                }
            } catch (Throwable ignored) {}
        }
        renderItem = new RenderItem();
        return renderItem;
    }

    private static final class NotificationEntry {

        private final ItemStack stack;
        private final long amount;

        NotificationEntry(ItemStack stack, long amount) {
            this.stack = stack;
            this.amount = amount;
        }
    }

    private NotificationEntry current;
    private long startTime;
    private long endTime;

    public void clear() {
        events.clear();
        current = null;
        startTime = 0;
        endTime = 0;
    }

    public void add(ItemStack stack, long amount) {
        events.add(new NotificationEntry(stack, amount));
    }

    public void draw() {
        if (current == null && !events.isEmpty()) {
            current = events.remove();
            startTime = System.currentTimeMillis();
            endTime = startTime + DELAY;
            EntityPlayer player = mc.thePlayer;
            if (player != null && player.worldObj != null) {
                player.worldObj.playSound(player.posX, player.posY, player.posZ, "random.levelup", 0.25f, 1.0f, false);
            }
        }
        if (current != null) {
            drawNotification(current);
        }
    }

    private void drawNotification(NotificationEntry event) {
        long currentTime = System.currentTimeMillis();
        if (currentTime > endTime) {
            current = null;
            startTime = 0;
            endTime = 0;
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int x;
        if (startTime + FADE_IN < currentTime) {
            x = resolution.getScaledWidth() - WIDTH;
            drawContent(event, x);
        } else {
            double percent = (double) (currentTime - startTime) / FADE_IN;
            x = resolution.getScaledWidth() - (int) (WIDTH * percent);
            drawContent(event, x);
        }
    }

    private void drawContent(NotificationEntry event, int x) {
        mc.getTextureManager()
            .bindTexture(BACKGROUND);
        Gui.func_146110_a(x - 26, 0, 0, 0, 156, 35, 256, 256);

        GL11.glPushMatrix();
        RenderItem itemRender = getRenderItem();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), event.stack, x - 18, TOP + 2);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GL11.glPopAttrib();
        GL11.glPopMatrix();

        mc.fontRenderer.drawStringWithShadow("Crafting Complete", x, TOP, 0xffffff);
        String amountText = event.amount > 1 ? "x" + event.amount + " " : "";
        mc.fontRenderer.drawStringWithShadow(
            amountText + event.stack.getDisplayName(),
            x,
            TOP + mc.fontRenderer.FONT_HEIGHT + 4,
            0xffffff);
    }
}
