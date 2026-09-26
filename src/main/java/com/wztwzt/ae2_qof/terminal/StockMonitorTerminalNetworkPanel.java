package com.wztwzt.ae2_qof.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IPanelHandler;
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

import com.wztwzt.ae2_qof.cover.stockmonitor.ae.WirelessAeConnector;

/**
 * 库存统计终端的**回退**网络选择面板：Nexus 不可用（未安装或反射初始化失败）时使用。
 *
 * <p>与覆盖板的 {@code cover/stockmonitor/gui/NetworkSelectPanel} 是**兄弟实现**：
 * 两者结构相同、数据来源相同（`WirelessAeConnector.getVisibleNetworks`），只是绑定目标不同
 * （这里写回 {@link StockMonitorTerminal#setNetworkId(String)}）。
 *
 * <p>为什么复制而不是复用：覆盖板的那个面板签名绑死了 {@code StockMonitorCover}
 * （要拿 {@code cover.getTile()} 与 {@code cover.getCoverData().setNetworkId}）。
 * 与其改动一个**用户已确认可用**的 GUI 去做泛化，不如把这点重复留在终端侧，
 * 保持"本次改动不触碰覆盖板"的边界。若将来覆盖板面板改版，这里需要一起复核。
 */
public final class StockMonitorTerminalNetworkPanel {

    private static final int CARD_H = 28;
    private static final int PANEL_W = 200;
    private static final int PANEL_H = 160;

    private StockMonitorTerminalNetworkPanel() {}

    public static ModularPanel build(PanelSyncManager panelSyncManager, StockMonitorTerminal terminal,
            IPanelHandler panelHandler) {
        EntityPlayer player = panelSyncManager.getPlayer();
        World world = null;
        try {
            TileEntity te = (TileEntity) terminal.getBaseMetaTileEntity();
            if (te != null) world = te.getWorldObj();
        } catch (Throwable ignored) {}

        List<NetworkEntry> entries = fetchNetworks(world, player, terminal);

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
                    "sm_terminal_net_sel_" + entry.id,
                    InteractionSyncHandler.class,
                    () -> new InteractionSyncHandler().setOnMousePressed(mouse -> {
                        if (!online) {
                            EntityPlayer p = panelSyncManager.getPlayer();
                            if (p != null) {
                                p.addChatMessage(new net.minecraft.util.ChatComponentText(
                                    EnumChatFormatting.RED + StatCollector
                                        .translateToLocal("ae2_qof.gui.stock_monitor.network_offline")));
                            }
                            return;
                        }
                        // 服务端执行：写回终端绑定并关闭面板（与 Nexus 面板的绑定语义一致）
                        terminal.setNetworkId(networkId.toString());
                        panelHandler.closePanel();
                    }));
                list.child(networkCard(entry, select));
            }
        }

        return ModularPanel.defaultPanel("ae2qol_terminal_network_select", PANEL_W, PANEL_H)
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

    private static List<NetworkEntry> fetchNetworks(World world, EntityPlayer player, StockMonitorTerminal terminal) {
        if (world == null || player == null) return Collections.emptyList();
        if (!WirelessAeConnector.isNexusAvailable()) return Collections.emptyList();

        List<?> records = WirelessAeConnector.getVisibleNetworks(world, player);
        String currentId = terminal.getNetworkId();
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
