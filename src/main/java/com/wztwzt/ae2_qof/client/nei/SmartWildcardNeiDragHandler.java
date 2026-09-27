package com.wztwzt.ae2_qof.client.nei;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wildcard.WildcardEditorPanel;

import codechicken.nei.VisiblityData;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;

/**
 * NEI → 智能通配样板编辑器 的**拖放接入**（3.23.2）。
 *
 * <h2>为什么用 NEI 自己的接口</h2>
 * NEI 2.8 给 GUI 的官方扩展点就是 {@link INEIGuiHandler#handleDragNDrop}；参考模组那套 MUI
 * （{@code com.gtnewhorizons.modularui}）里的 {@code IDragAndDropHandler} 也只是**转发这个事件**。
 * 本模组用的 Cleanroom MUI2 没有接收外部拖放的接口，但提供
 * {@code ModularGuiContext.getHovered()} —— 于是组合起来就能实现「拖到哪个框就写进哪个框」。
 *
 * <h2>行为</h2>
 * 拖放物品到编辑器的输入/输出匹配框上：有矿辞 ⇒ 用 GT 权威前缀写成 {@code <前缀>*} 并切到矿辞模式；
 * 无矿辞 ⇒ 写显示名并切到名称模式。未命中匹配框时不消费事件（NEI 走它自己的默认行为），
 * 并且**记一条 INFO 说明为何没消费**（本项目的"不许静默"原则）。
 */
public class SmartWildcardNeiDragHandler implements INEIGuiHandler {

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return Collections.emptyList();
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack draggedStack, int button) {
        try {
            if (draggedStack == null || draggedStack.getItem() == null) return false;
            if (!ModularScreen.isActive(MyMod.MODID, "ae2qol_wildcard_editor")) return false;
            ModularScreen screen = ModularScreen.getCurrent();
            if (screen == null) return false;
            Object hovered = screen.getContext()
                .getHovered();
            boolean consumed = WildcardEditorPanel.applyDropToHovered(hovered, mouseX, mouseY, draggedStack);
            if (!consumed) {
                MyMod.LOG.info(
                    "[AE2QoL] NEI 拖入未消费（没有落在匹配框上）：hovered={} mouse=({}, {})",
                    hovered == null ? "null"
                        : hovered.getClass()
                            .getSimpleName(),
                    mouseX,
                    mouseY);
            }
            return consumed;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] NEI 拖入处理异常", t);
            return false;
        }
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        return false;
    }
}
