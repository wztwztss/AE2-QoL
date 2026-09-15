package com.wztwzt.ae2_qof.cover.stockmonitor.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ScrollingTextWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCover;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.WirelessAeConnector;

public final class NetworkSelectPanel {

    private static final int CARD_H = 28;
    private static final int PANEL_W = 200;
    private static final int PANEL_H = 160;

    private NetworkSelectPanel() {}

    public static ModularPanel build(PanelSyncManager panelSyncManager, StockMonitorCover cover,
        com.cleanroommc.modularui.api.IPanelHandler panelHandler) {
        EntityPlayer player = panelSyncManager.getPlayer();
        World world = null;
        if (cover.getTile() instanceof TileEntity) {
            world = ((TileEntity) cover.getTile()).getWorldObj();
        }

        List<NetworkEntry> entries = fetchNetworks(world, player, cover);

        Flow list = Flow.column().widthRel(1F).childPadding(2);

        if (entries.isEmpty()) {
            list.child(
                new TextWidget<>(IKey.lang("ae2_qof.gui.stock_monitor.no_networks"))
                    .widthRel(1F)
                    .height(CARD_H)
                    .textAlign(Alignment.CenterLeft)
                    .color(0xFFAEBABC));
        } else {
            for (NetworkEntry entry : entries) {
                final UUID networkId = entry.id;
                final boolean online = entry.online;
                InteractionSyncHandler select = panelSyncManager.getOrCreateSyncHandler(
                    "net_sel_" + entry.id,
                    InteractionSyncHandler.class,
                    () -> new InteractionSyncHandler().setOnMousePressed(mouse -> {
                        if (!online) {
                            // B8：离线网络不可绑定，给玩家提示
                            EntityPlayer p = panelSyncManager.getPlayer();
                            if (p != null) {
                                p.addChatMessage(new net.minecraft.util.ChatComponentText(
                                    EnumChatFormatting.RED + StatCollector
                                        .translateToLocal("ae2_qof.gui.stock_monitor.network_offline")));
                            }
                            return;
                        }
                        cover.getCoverData().setNetworkId(networkId.toString());
                        // B8：绑定成功后关闭面板，给出明确完成反馈
                        panelHandler.closePanel();
                    }));
                list.child(networkCard(entry, select));
            }
        }

        ModularPanel panel = ModularPanel.defaultPanel("network_select", PANEL_W, PANEL_H)
            .child(
                Flow.column()
                    .full()
                    .padding(6)
                    .childPadding(4)
                    .child(
                        new TextWidget<>(IKey.lang("ae2_qof.gui.stock_monitor.select_network"))
                            .widthRel(1F)
                            .textAlign(Alignment.Center)
                            .marginBottom(2))
                    .child(list)
                    .child(
                        Flow.row()
                            .widthRel(1F)
                            .height(14)
                            .childPadding(4)
                            .child(
                                new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GRAY
                                    + StatCollector.translateToLocal("ae2_qof.gui.stock_monitor.network_count")
                                    + " " + entries.size())).expanded())
                            .child(
                                new ButtonWidget<>()
                                    .size(40, 14)
                                    .background(GuiTextures.BUTTON_CLEAN)
                                    .overlay(IKey.lang("ae2_qof.gui.stock_monitor.close"))
                                    .onMousePressed(event -> {
                                        panelHandler.closePanel();
                                        return true;
                                    }))));
        return panel;
    }

    private static IWidget networkCard(NetworkEntry entry, InteractionSyncHandler select) {
        return new ButtonWidget<>()
            .widthRel(1F)
            .height(CARD_H)
            .syncHandler(select)
            .child(
                Flow.column()
                    .full()
                    .padding(4)
                    .child(
                        new ScrollingTextWidget(IKey.str(entry.name))
                            .widthRel(1F)
                            .height(10)
                            .color(entry.selected ? 0xFF42D3CF : 0xFFF0F6F7))
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> {
                            EnumChatFormatting c = entry.online ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY;
                            String k = entry.online ? "ae2_qof.gui.stock_monitor.network_online"
                                : "ae2_qof.gui.stock_monitor.network_offline";
                            return c + StatCollector.translateToLocal(k);
                        })).widthRel(1F)
                            .scale(0.8F)
                            .color(entry.selected ? 0xFF8FE4E1 : 0xFFAEBABC)));
    }

    private static List<NetworkEntry> fetchNetworks(World world, EntityPlayer player, StockMonitorCover cover) {
        if (world == null || player == null) return Collections.emptyList();
        if (!WirelessAeConnector.isNexusAvailable()) return Collections.emptyList();

        List<?> records = WirelessAeConnector.getVisibleNetworks(world, player);
        String currentId = cover.getCoverData().getNetworkId();
        List<NetworkEntry> entries = new ArrayList<>();

        for (Object record : records) {
            UUID id = WirelessAeConnector.getRecordId(record);
            String name = WirelessAeConnector.getRecordName(record);
            boolean online = WirelessAeConnector.isRecordOnline(record);
            if (id != null) {
                entries.add(new NetworkEntry(id, name, online, id.toString().equals(currentId)));
            }
        }
        return entries;
    }

    private static final class NetworkEntry {

        final UUID id;
        final String name;
        final boolean online;
        final boolean selected;

        NetworkEntry(UUID id, String name, boolean online, boolean selected) {
            this.id = id;
            this.name = name;
            this.online = online;
            this.selected = selected;
        }
    }
}
