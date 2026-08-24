package com.wztwzt.ae2_qof.client.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.wztwzt.ae2_qof.network.ExtractItemPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.util.CountFormatter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 合成完成产物展示条（3.10.0）：标准 ME 终端第一行（搜索框下方）临时展示最近完成的
 * 合成产物，模仿标记区视觉——覆盖第一行格子区域，每条保留 60 秒后消失（最后 5 秒
 * 底色与数量文字渐隐，物品图标常显）。数据与右上角横幅同源（CraftingCompletePacket），
 * 两者并存：横幅即时提醒，本条持续展示。
 * <p>
 * 交互：点击格子提取一组到背包（复用 ExtractItemPacket 链路，失败走既有聊天提示）；
 * 悬停显示物品名与数量。同物品新完成时合并数量并刷新保留时间；满 9 格顶掉最旧。
 * 仅标准 {@code GuiMEMonitorable} 显示——子类（二合一/接口/无线终端等）由 mixin 侧
 * 按 {@code getClass()==GuiMEMonitorable.class} 精确排除。
 * 纯客户端，零网络/服务端改动。
 */
@SideOnly(Side.CLIENT)
public final class RecentCraftedOverlay {

    public static final RecentCraftedOverlay INSTANCE = new RecentCraftedOverlay();

    private static final int SLOTS = 9;
    private static final long LINGER_MS = 60_000L;
    private static final long FADE_MS = 5_000L;

    private final List<Entry> entries = new ArrayList<>(); // 尾部 = 最新
    private final Minecraft mc = Minecraft.getMinecraft();

    /** draw 时缓存的悬停条目（供 mixin 宿主绘制 tooltip）。 */
    private Entry hovered;
    /** draw/handleClick 时定位的第一行槽位坐标（容器相对坐标）。 */
    private int[] rowXs = new int[0];
    private int rowY = -1;

    private RecentCraftedOverlay() {}

    private static final class Entry {

        ItemStack stack;
        long amount;
        long addTimeMs;
    }

    public void add(ItemStack stack, long amount) {
        if (stack == null) return;
        // 诊断日志：每次合成完成一条，低频，便于定位"展示条不出现"类反馈
        com.wztwzt.ae2_qof.MyMod.LOG
            .info("[RecentCrafted] add: {} x{}", stack.getDisplayName(), amount);
        long now = System.currentTimeMillis();
        expire(now);
        // 同物品合并：数量累加并刷新保留时间
        for (Entry e : entries) {
            if (e.stack.isItemEqual(stack)) {
                e.amount += amount;
                e.addTimeMs = now;
                return;
            }
        }
        while (entries.size() >= SLOTS) {
            entries.remove(0); // 顶掉最旧
        }
        Entry e = new Entry();
        e.stack = stack.copy();
        e.amount = amount;
        e.addTimeMs = now;
        entries.add(e);
    }

    public void clear() {
        entries.clear();
        hovered = null;
    }

    /** draw 后可查：当前悬停格子的物品（无则 null）。 */
    public ItemStack getHoveredStack() {
        return hovered != null ? hovered.stack : null;
    }

    /** draw 后可查：当前悬停条目的数量。 */
    public long getHoveredAmount() {
        return hovered != null ? hovered.amount : 0;
    }

    private void expire(long now) {
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            if (now - it.next().addTimeMs > LINGER_MS) {
                it.remove();
            }
        }
    }

    /** 定位终端第一行（yDisplayPosition 最小的槽位行），按 x 排序取前 9 格。 */
    private boolean locateRow(GuiContainer host) {
        try {
            int minY = Integer.MAX_VALUE;
            for (Object o : host.inventorySlots.inventorySlots) {
                Slot s = (Slot) o;
                if (s.yDisplayPosition < minY) minY = s.yDisplayPosition;
            }
            if (minY == Integer.MAX_VALUE) return false;
            ArrayList<Integer> xs = new ArrayList<>();
            for (Object o : host.inventorySlots.inventorySlots) {
                Slot s = (Slot) o;
                if (s.yDisplayPosition == minY) xs.add(s.xDisplayPosition);
            }
            java.util.Collections.sort(xs);
            rowXs = new int[Math.min(SLOTS, xs.size())];
            for (int i = 0; i < rowXs.length; i++) {
                rowXs[i] = xs.get(i);
            }
            rowY = minY;
            return rowXs.length > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private int slotX(int i, int guiLeft) {
        return guiLeft + rowXs[i];
    }

    private int slotY(int guiTop) {
        return guiTop + rowY;
    }

    private boolean inSlot(int i, int guiLeft, int guiTop, int mx, int my) {
        int x = slotX(i, guiLeft);
        int y = slotY(guiTop);
        return mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17;
    }

    /**
     * 渲染展示条（宿主 drawScreen TAIL 调用）。最新产物显示在最左格。
     */
    public void draw(GuiContainer host, int guiLeft, int guiTop, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        expire(now);
        hovered = null;
        if (entries.isEmpty()) return;
        if (!locateRow(host)) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        try {
            int shown = Math.min(SLOTS, entries.size());
            for (int i = 0; i < shown; i++) {
                Entry e = entries.get(entries.size() - 1 - i); // 最新在最左
                int x = slotX(i, guiLeft);
                int y = slotY(guiTop);
                float remain = LINGER_MS - (now - e.addTimeMs);
                float fade = remain >= FADE_MS ? 1f : Math.max(0.15f, remain / FADE_MS);

                // 半透明底 + 边框（渐隐）
                int alpha = (int) (185 * fade);
                Gui.drawRect(x - 1, y - 1, x + 17, y + 17, (alpha << 24) | 0x0A0A14);
                Gui.drawRect(x - 1, y - 1, x + 17, y, (alpha << 24) | 0x8B8B9E);
                Gui.drawRect(x - 1, y + 16, x + 17, y + 17, (alpha << 24) | 0x8B8B9E);
                Gui.drawRect(x - 1, y, x, y + 16, (alpha << 24) | 0x8B8B9E);
                Gui.drawRect(x + 16, y, x + 17, y + 16, (alpha << 24) | 0x8B8B9E);

                // 物品图标（不参与渐隐，保持可辨识）
                RenderItem ri = CraftingNotificationOverlay.getRenderItem();
                RenderHelper.enableGUIStandardItemLighting();
                try {
                    ri.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), e.stack, x, y);
                } finally {
                    RenderHelper.disableStandardItemLighting();
                }

                // 数量（右下角，渐隐）
                if (e.amount > 1) {
                    String label = CountFormatter.format(e.amount);
                    GL11.glDisable(GL11.GL_LIGHTING);
                    GL11.glEnable(GL11.GL_BLEND);
                    mc.fontRenderer.drawStringWithShadow(
                        label,
                        x + 17 - mc.fontRenderer.getStringWidth(label),
                        y + 9,
                        (alpha << 24) | 0xFFFFFF);
                }

                if (inSlot(i, guiLeft, guiTop, mouseX, mouseY)) {
                    hovered = e;
                }
            }
        } catch (Throwable ignored) {
            // 渲染异常不得影响终端本身
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * 点击提取（宿主 mouseClicked HEAD 调用）：命中产物格子时发提取包并返回 true（吞掉点击，
     * 阻止穿透到被覆盖的第一行槽位）。
     */
    public boolean handleClick(GuiContainer host, int guiLeft, int guiTop, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        expire(now);
        if (entries.isEmpty()) return false;
        if (!locateRow(host)) return false;
        try {
            int shown = Math.min(SLOTS, entries.size());
            for (int i = 0; i < shown; i++) {
                if (!inSlot(i, guiLeft, guiTop, mouseX, mouseY)) continue;
                Entry e = entries.get(entries.size() - 1 - i);
                ModNetwork.CHANNEL
                    .sendToServer(new ExtractItemPacket(e.stack, e.stack.getMaxStackSize()));
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
