package com.wztwzt.ae2_qof.cover.stockmonitor.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCover;
import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCoverData;
import com.wztwzt.ae2_qof.cover.stockmonitor.ThresholdMode;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.AeConnector;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.WirelessAeConnector;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.StockMonitorWirelessEndpoint;

import gregtech.api.modularui2.CoverGuiData;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;

public class StockMonitorCoverGui extends CoverBaseGui<StockMonitorCover> {

    private static final int PANEL_W = 200;

    public StockMonitorCoverGui(StockMonitorCover cover) {
        super(cover);
    }

    @Override
    protected String getGuiId() {
        return "cover.stock_monitor";
    }

    @Override
    protected boolean doesBindPlayerInventory() {
        return true;
    }

    @Override
    public void addUIWidgets(PanelSyncManager syncManager, Flow column, CoverGuiData guiData) {
        final StockMonitorCoverData coverData = cover.getCoverData();

        // 9 个槽位的 threshold SyncValue（C2S 双向同步）
        final LongSyncValue[] thresholdSyncs = new LongSyncValue[StockMonitorCoverData.MAX_SLOTS];
        for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
            final int idx = i;
            thresholdSyncs[i] = new LongSyncValue(
                () -> coverData.getSlot(idx).threshold,
                v -> coverData.getSlot(idx).threshold = v
            ).allowC2S();
            syncManager.syncValue("threshold_" + i, thresholdSyncs[i]);
        }

        // 模式 SyncValue（全局，C2S）
        final IntSyncValue modeSync = new IntSyncValue(
            () -> coverData.getMode().ordinal(),
            ordinal -> coverData.setMode(ThresholdMode.fromOrdinal(ordinal)))
            .allowC2S();
        syncManager.syncValue("mode", modeSync);

        // 运行态（S2C）
        final IntSyncValue channelSync = new IntSyncValue(coverData::getLastChannel);
        final BooleanSyncValue shouldWorkSync = new BooleanSyncValue(coverData::isLastShouldWork);
        syncManager.syncValue("channel", channelSync);
        syncManager.syncValue("should_work", shouldWorkSync);

        // ===== 网络选择子面板 =====
        IPanelHandler networkPanel = syncManager.syncedPanel(
            "network_select",
            true,
            (panelSyncManager, panelHandler) -> {
                if (WirelessAeConnector.isNexusAvailable()) {
                    StockMonitorWirelessEndpoint endpoint = new StockMonitorWirelessEndpoint(cover);
                    return cn.dancingsnow.ae_wireless_nexus.gui.WirelessSelectionPanel
                        .build("ae2qol_net_select", endpoint, panelSyncManager.getPlayer(),
                            panelSyncManager, true);
                }
                return NetworkSelectPanel.build(panelSyncManager, cover, panelHandler);
            });

        // 连接按钮
        InteractionSyncHandler connectAction = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (WirelessAeConnector.isNexusAvailable()) {
                networkPanel.openPanel();
            } else {
                net.minecraft.entity.player.EntityPlayer player = syncManager.getPlayer();
                if (player != null) {
                    player.addChatMessage(new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.YELLOW + StatCollector
                            .translateToLocal("ae2_qof.gui.stock_monitor.nexus_not_installed")));
                }
            }
        });
        syncManager.syncValue("connect", connectAction);

        // ===== 更多槽位子面板 =====
        IPanelHandler moreSlotsPanel = syncManager.syncedPanel(
            "more_slots",
            true,
            (panelSyncManager, panelHandler) -> {
                Flow panelColumn = Flow.column().coverChildren().childPadding(3);

                // 标题 + 返回按钮
                panelColumn.child(
                    Flow.row().coverChildren().childPadding(4)
                        .child(new TextWidget<>(IKey.lang("ae2_qof.gui.stock_monitor.more_slots"))
                            .size(120, 14))
                        .child(new ButtonWidget<>()
                            .size(50, 14)
                            .background(GuiTextures.BUTTON_CLEAN)
                            .overlay(IKey.lang("ae2_qof.gui.stock_monitor.back"))
                            .onMousePressed(e -> {
                                panelHandler.closePanel();
                                return true;
                            })));

                // 槽位 1-8
                for (int i = 1; i < StockMonitorCoverData.MAX_SLOTS; i++) {
                    panelColumn.child(slotRow(i, thresholdSyncs[i], coverData));
                }

                return com.cleanroommc.modularui.screen.ModularPanel.defaultPanel(
                    "more_slots_panel", PANEL_W, 240)
                    .child(Flow.column().full().padding(6).childPadding(3).child(panelColumn));
            });

        // ===== 主面板内容 =====

        // 连接状态 + 连接按钮
        column.child(
            Flow.row().coverChildren().childPadding(4)
                .child(connectionStatusWidget(channelSync))
                .child(new ButtonWidget<>()
                    .size(80, 16)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.lang("ae2_qof.gui.stock_monitor.connect"))
                    .syncHandler(connectAction)));

        // 槽位 0（主检测槽）
        column.child(slotRow(0, thresholdSyncs[0], coverData));

        // 快捷数量按钮（作用于槽位 0）
        column.child(presetButtonsRow(thresholdSyncs[0]));

        // 模式切换（全局）
        column.child(
            Flow.row().coverChildren().childPadding(4)
                .child(new TextWidget<>(IKey.lang("ae2_qof.gui.stock_monitor.mode")).size(60, 14))
                .child(modeToggleButton(modeSync)));

        // 更多按钮
        column.child(
            new ButtonWidget<>()
                .size(PANEL_W, 16)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.gui.stock_monitor.more"))
                .onMousePressed(e -> {
                    moreSlotsPanel.openPanel();
                    return true;
                }));

        // 分隔线
        column.child(new TextWidget<>(IKey.str("")).size(PANEL_W, 2));

        // 当前库存（所有有效槽位）
        column.child(stockDisplayWidget(coverData));

        // 机器状态
        column.child(machineStatusWidget(shouldWorkSync));

        // 断开连接
        InteractionSyncHandler disconnectAction = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (channelSync.getValue() == AeConnector.CHANNEL_NEIGHBOR) {
                net.minecraft.entity.player.EntityPlayer player = syncManager.getPlayer();
                if (player != null) {
                    player.addChatMessage(new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.YELLOW + StatCollector
                            .translateToLocal("ae2_qof.gui.stock_monitor.cannot_disconnect_neighbor")));
                }
                return;
            }
            coverData.setNetworkId("");
        });
        syncManager.syncValue("disconnect", disconnectAction);
        column.child(disconnectButton(disconnectAction));
    }

    /** 单个槽位行：phantom slot + 阈值输入 + 物品名 */
    private Flow slotRow(int slotIndex, LongSyncValue thresholdSync, StockMonitorCoverData coverData) {
        return Flow.row().coverChildren().childPadding(4)
            .child(new TextWidget<>(IKey.str("#" + (slotIndex + 1))).size(16, 18))
            .child(new StockMonitorPhantomSlot().slot(new ModularSlot(cover.getPhantomSlot(), slotIndex)))
            .child(new TextFieldWidget()
                .size(80, 16)
                .numbersLong(0, Long.MAX_VALUE)
                .value(thresholdSync))
            .child(slotTargetNameWidget(coverData, slotIndex));
    }

    private TextWidget<?> slotTargetNameWidget(StockMonitorCoverData coverData, int slotIndex) {
        return new TextWidget<>(IKey.dynamic(() -> {
            StockMonitorCoverData.MonitorSlot slot = coverData.getSlot(slotIndex);
            if (slot.monitorTarget != null) {
                return EnumChatFormatting.WHITE + slot.monitorTarget.getDisplayName();
            }
            return EnumChatFormatting.GRAY + StatCollector
                .translateToLocal("ae2_qof.gui.stock_monitor.no_target");
        })).size(70, 14);
    }

    private TextWidget<?> connectionStatusWidget(IntSyncValue channelSync) {
        return new TextWidget<>(IKey.dynamic(() -> {
            int channel = channelSync.getValue();
            String key;
            EnumChatFormatting color;
            switch (channel) {
                case AeConnector.CHANNEL_WIRELESS:
                    key = "ae2_qof.gui.stock_monitor.connected_wireless";
                    color = EnumChatFormatting.GREEN;
                    break;
                case AeConnector.CHANNEL_NEIGHBOR:
                    key = "ae2_qof.gui.stock_monitor.connected_neighbor";
                    color = EnumChatFormatting.YELLOW;
                    break;
                default:
                    key = "ae2_qof.gui.stock_monitor.disconnected";
                    color = EnumChatFormatting.RED;
                    break;
            }
            return color + "\u25cf " + StatCollector.translateToLocal(key);
        })).size(110, 14);
    }

    private Flow presetButtonsRow(LongSyncValue thresholdSync) {
        Flow row = Flow.row().coverChildren().childPadding(2);
        String[] presets = Config.stockMonitorPresets.split(";");
        for (String preset : presets) {
            String[] parts = preset.split("=", 2);
            if (parts.length != 2) continue;
            String label = parts[0].trim();
            long value;
            try {
                value = Long.parseLong(parts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (value < 0) continue;
            final long presetValue = value;
            row.child(new ButtonWidget<>()
                .size(40, 14)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.str(label))
                .onMousePressed(event -> {
                    thresholdSync.setLongValue(presetValue);
                    return true;
                }));
        }
        return row;
    }

    private ButtonWidget<?> modeToggleButton(IntSyncValue modeSync) {
        return new ButtonWidget<>()
            .size(120, 14)
            .background(GuiTextures.BUTTON_CLEAN)
            .overlay(IKey.dynamic(() -> {
                int mode = modeSync.getValue();
                String key = mode == ThresholdMode.BELOW_THRESHOLD_RUN.ordinal()
                    ? "ae2_qof.gui.stock_monitor.mode_below"
                    : "ae2_qof.gui.stock_monitor.mode_above";
                return StatCollector.translateToLocal(key);
            }))
            .onMousePressed(event -> {
                int current = modeSync.getValue();
                modeSync.setValue(current == ThresholdMode.BELOW_THRESHOLD_RUN.ordinal()
                    ? ThresholdMode.ABOVE_THRESHOLD_RUN.ordinal()
                    : ThresholdMode.BELOW_THRESHOLD_RUN.ordinal());
                return true;
            });
    }

    /** 显示所有有效槽位的库存 */
    private TextWidget<?> stockDisplayWidget(StockMonitorCoverData coverData) {
        return new TextWidget<>(IKey.dynamic(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(EnumChatFormatting.AQUA)
                .append(StatCollector.translateToLocal("ae2_qof.gui.stock_monitor.current_stock"))
                .append("\n");
            boolean any = false;
            for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
                StockMonitorCoverData.MonitorSlot slot = coverData.getSlot(i);
                if (slot.monitorTarget == null) continue;
                any = true;
                sb.append(EnumChatFormatting.WHITE)
                    .append("#").append(i + 1).append(" ")
                    .append(slot.monitorTarget.getDisplayName())
                    .append(": ").append(slot.lastStock)
                    .append(" / ").append(slot.threshold)
                    .append("\n");
            }
            if (!any) {
                sb.append(EnumChatFormatting.GRAY)
                    .append(StatCollector.translateToLocal("ae2_qof.gui.stock_monitor.no_target"));
            }
            return sb.toString();
        })).size(PANEL_W, 80);
    }

    private TextWidget<?> machineStatusWidget(BooleanSyncValue shouldWorkSync) {
        return new TextWidget<>(IKey.dynamic(() -> {
            boolean shouldWork = shouldWorkSync.getValue();
            String key = shouldWork ? "ae2_qof.gui.stock_monitor.machine_running"
                : "ae2_qof.gui.stock_monitor.machine_stopped";
            EnumChatFormatting color = shouldWork ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
            return color + StatCollector.translateToLocal(key);
        })).size(PANEL_W, 14);
    }

    private ButtonWidget<?> disconnectButton(InteractionSyncHandler disconnectAction) {
        return new ButtonWidget<>()
            .size(80, 16)
            .background(GuiTextures.BUTTON_CLEAN)
            .overlay(IKey.lang("ae2_qof.gui.stock_monitor.disconnect"))
            .syncHandler(disconnectAction);
    }
}
