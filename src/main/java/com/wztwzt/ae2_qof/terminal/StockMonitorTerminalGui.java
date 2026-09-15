package com.wztwzt.ae2_qof.terminal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.parts.automation.PartLevelEmitter;
import appeng.api.config.LevelType;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCover;
import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCoverData;
import com.wztwzt.ae2_qof.cover.stockmonitor.ThresholdMode;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.NeighborAeConnector;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.WirelessAeConnector;
import com.wztwzt.ae2_qof.terminal.CoverRegistry.CoverEntry;

/**
 * 库存统计终端 GUI（第二版）：发信器列表 + 覆盖板列表 + 完整编辑子面板。
 */
public class StockMonitorTerminalGui {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 240;
    private static final int EDIT_PANEL_W = 220;
    private static final int EDIT_PANEL_H = 160;

    // 当前选中的条目（服务端变量，用于编辑子面板）
    private static CoverEntry selectedCover;
    private static PartLevelEmitter selectedEmitter;

    public static ModularPanel build(StockMonitorTerminal terminal, PosGuiData guiData,
            PanelSyncManager syncManager, UISettings uiSettings) {
        ModularPanel panel = ModularPanel.defaultPanel("stock_monitor_terminal", PANEL_W, PANEL_H);

        EntityPlayer player = guiData.getPlayer();
        boolean isServer = !player.worldObj.isRemote;

        // ===== 编辑子面板 =====
        IPanelHandler coverEditPanel = syncManager.syncedPanel(
            "cover_edit", true,
            (panelSyncManager, panelHandler) -> buildCoverEditPanel(terminal, panelSyncManager, panelHandler));

        IPanelHandler emitterEditPanel = syncManager.syncedPanel(
            "emitter_edit", true,
            (panelSyncManager, panelHandler) -> buildEmitterEditPanel(panelSyncManager, panelHandler));

        // ===== Nexus 无线网络选择子面板 =====
        IPanelHandler netSelectPanel = syncManager.syncedPanel(
            "net_select", true,
            (panelSyncManager, panelHandler) -> {
                StockMonitorTerminalWirelessEndpoint endpoint = new StockMonitorTerminalWirelessEndpoint(terminal);
                return cn.dancingsnow.ae_wireless_nexus.gui.WirelessSelectionPanel
                    .build("ae2qol_terminal_net_select", endpoint, panelSyncManager.getPlayer(),
                        panelSyncManager, true);
            });

        Flow column = Flow.column().coverChildren().childPadding(2).top(6).left(8);

        // 标题
        column.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.stock_monitor.title"))
            .size(PANEL_W - 16, 14));

        // 连接状态 + 连接按钮
        if (isServer) {
            boolean bound = terminal.isBound();
            String statusText = bound ? (EnumChatFormatting.GREEN + "● 已连接无线网络")
                : (EnumChatFormatting.GRAY + "● 未连接网络");
            InteractionSyncHandler connectAction = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (WirelessAeConnector.isNexusAvailable()) {
                    netSelectPanel.openPanel();
                } else {
                    EntityPlayer p = syncManager.getPlayer();
                    if (p != null) {
                        p.addChatMessage(new net.minecraft.util.ChatComponentText(
                            EnumChatFormatting.YELLOW + "Nexus 未安装，终端仅支持邻接 AE2 网络"));
                    }
                }
            });
            syncManager.syncValue("terminal_connect", connectAction);

            column.child(Flow.row().coverChildren().childPadding(4)
                .child(new TextWidget<>(IKey.str(statusText)).size(140, 14))
                .child(new ButtonWidget<>().size(80, 14)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.str(bound ? "切换网络" : "连接 AE"))
                    .syncHandler(connectAction)));
        }

        // 发信器区域
        column.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.emitters"))
            .size(PANEL_W - 16, 12).color(0xFF0066CC));
        if (isServer) {
            buildEmitterList(column, terminal, player, syncManager, emitterEditPanel);
        }

        // 分隔
        column.child(new TextWidget<>(IKey.str("")).size(PANEL_W - 16, 4));

        // 覆盖板区域
        column.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.covers"))
            .size(PANEL_W - 16, 12).color(0xFF009933));
        if (isServer) {
            buildCoverList(column, terminal, player, syncManager, coverEditPanel);
        }

        panel.child(column);
        return panel;
    }

    // ===== 发信器列表 =====

    private static void buildEmitterList(Flow column, StockMonitorTerminal terminal,
            EntityPlayer player, PanelSyncManager syncManager, IPanelHandler editPanel) {
        List<PartLevelEmitter> emitters = findEmitters(terminal);

        if (emitters.isEmpty()) {
            column.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.no_emitters"))
                .color(0xFFAAAAAA).size(PANEL_W - 16, 12));
            return;
        }

        for (int i = 0; i < Math.min(emitters.size(), 5); i++) {
            final PartLevelEmitter emitter = emitters.get(i);
            final String label = getEmitterLabel(emitter);
            final long threshold = emitter.getReportingValue();
            final LevelType type = (LevelType) emitter.getConfigManager().getSetting(Settings.LEVEL_TYPE);

            InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                selectedEmitter = emitter;
                editPanel.openPanel();
            });

            column.child(new ButtonWidget<>().size(PANEL_W - 24, 14)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.str(label + "  [" + type.name().charAt(0) + ":" + formatNumber(threshold) + "]"))
                .syncHandler(handler));
        }

        if (emitters.size() > 5) {
            column.child(new TextWidget<>(IKey.str("... +" + (emitters.size() - 5)))
                .color(0xFFAAAAAA).size(PANEL_W - 16, 10));
        }
    }

    private static List<PartLevelEmitter> findEmitters(StockMonitorTerminal terminal) {
        List<PartLevelEmitter> result = new ArrayList<>();
        try {
            TileEntity te = (TileEntity) terminal.getBaseMetaTileEntity();
            if (te == null) return result;
            IGrid grid = null;

            // 优先：Nexus 无线网络
            if (terminal.isBound() && WirelessAeConnector.isNexusAvailable()) {
                try {
                    java.util.UUID netId = java.util.UUID.fromString(terminal.getNetworkId());
                    grid = WirelessAeConnector.getGridForNetwork(netId, te.getWorldObj());
                } catch (IllegalArgumentException ignored) {}
            }

            // 其次：邻接 AE2 网络
            if (grid == null) {
                grid = NeighborAeConnector.findGrid(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord,
                    ForgeDirection.UNKNOWN);
            }

            if (grid == null) return result;
            for (Object machine : grid.getMachines(PartLevelEmitter.class)) {
                if (machine instanceof PartLevelEmitter) {
                    result.add((PartLevelEmitter) machine);
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static String getEmitterLabel(PartLevelEmitter emitter) {
        try {
            IAEStack<?> config = emitter.getAEInventoryByName(StorageName.CONFIG).getAEStackInSlot(0);
            if (config != null) {
                return config.getDisplayName();
            }
        } catch (Throwable ignored) {}
        return "Emitter";
    }

    // ===== 覆盖板列表 =====

    private static void buildCoverList(Flow column, StockMonitorTerminal terminal,
            EntityPlayer player, PanelSyncManager syncManager, IPanelHandler editPanel) {
        TileEntity te = (TileEntity) terminal.getBaseMetaTileEntity();
        Collection<CoverEntry> covers = CoverRegistry.get(te.getWorldObj()).getAll();

        if (covers.isEmpty()) {
            column.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.no_covers"))
                .color(0xFFAAAAAA).size(PANEL_W - 16, 12));
            return;
        }

        int i = 0;
        for (CoverEntry entry : covers) {
            if (i >= 5) break;
            final CoverEntry fe = entry;
            String label = entry.targetName.isEmpty() ? "(unset)" : entry.targetName;
            if (!entry.online) label = EnumChatFormatting.GRAY + label + " [OFF]";
            ThresholdMode mode = ThresholdMode.values()[entry.modeOrdinal];

            InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                selectedCover = fe;
                editPanel.openPanel();
            });

            column.child(new ButtonWidget<>().size(PANEL_W - 24, 14)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.str(label + "  [" + mode.name().substring(0, 3) + ":" + formatNumber(entry.threshold) + "]"))
                .syncHandler(handler));
            i++;
        }

        if (covers.size() > 5) {
            column.child(new TextWidget<>(IKey.str("... +" + (covers.size() - 5)))
                .color(0xFFAAAAAA).size(PANEL_W - 16, 10));
        }
    }

    // ===== 覆盖板编辑子面板 =====

    private static ModularPanel buildCoverEditPanel(StockMonitorTerminal terminal,
            PanelSyncManager syncManager, IPanelHandler panelHandler) {
        ModularPanel edit = ModularPanel.defaultPanel("cover_edit_panel", EDIT_PANEL_W, EDIT_PANEL_H);
        EntityPlayer player = syncManager.getPlayer();

        if (selectedCover == null) {
            edit.child(new TextWidget<>(IKey.str("No cover selected")).pos(10, 10));
            return edit;
        }

        StockMonitorCover cover = locateCover(selectedCover);
        if (cover == null) {
            edit.child(new TextWidget<>(IKey.str(EnumChatFormatting.RED + "Cover not found"))
                .pos(10, 10));
            return edit;
        }

        if (!hasPermission(player, cover)) {
            edit.child(new TextWidget<>(IKey.str(EnumChatFormatting.RED + "No permission"))
                .pos(10, 10));
            return edit;
        }

        StockMonitorCoverData data = cover.getCoverData();
        final StockMonitorCover finalCover = cover;

        // 标题
        String targetName = selectedCover.targetName.isEmpty() ? "(unset)" : selectedCover.targetName;
        edit.child(new TextWidget<>(IKey.str(targetName)).pos(10, 8).size(EDIT_PANEL_W - 20, 14));

        // 阈值
        LongSyncValue thresholdSync = new LongSyncValue(
            () -> data.getSlot(0).threshold,
            v -> {
                data.getSlot(0).threshold = v;
                finalCover.markCoverDirty();
            }).allowC2S();
        syncManager.syncValue("cover_threshold", thresholdSync);

        edit.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.threshold"))
            .pos(10, 30).size(60, 12));
        edit.child(new TextFieldWidget().numbersLong(0, Long.MAX_VALUE)
            .pos(75, 28).size(EDIT_PANEL_W - 85, 16)
            .value(thresholdSync));

        // 模式
        IntSyncValue modeSync = new IntSyncValue(
            () -> data.getMode().ordinal(),
            v -> {
                data.setMode(ThresholdMode.values()[v]);
                finalCover.markCoverDirty();
            }).allowC2S();
        syncManager.syncValue("cover_mode", modeSync);

        edit.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.mode"))
            .pos(10, 55).size(60, 12));

        String[] modeNames = { "BELOW", "ABOVE" };
        for (int i = 0; i < modeNames.length; i++) {
            final int mi = i;
            InteractionSyncHandler modeBtn = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                modeSync.setValue(mi);
            });
            edit.child(new ButtonWidget<>().pos(75 + i * 70, 53).size(65, 16)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.str(modeNames[i]))
                .syncHandler(modeBtn));
        }

        // 位置信息
        edit.child(new TextWidget<>(IKey.str(
            "Dim:" + selectedCover.dim + " X:" + selectedCover.x + " Y:" + selectedCover.y + " Z:" + selectedCover.z))
            .pos(10, 80).color(0xFF999999).size(EDIT_PANEL_W - 20, 10));

        // 关闭按钮
        InteractionSyncHandler closeBtn = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            panelHandler.closePanel();
        });
        edit.child(new ButtonWidget<>().pos(10, 110).size(60, 18)
            .background(GuiTextures.BUTTON_CLEAN)
            .overlay(IKey.str("Close"))
            .syncHandler(closeBtn));

        return edit;
    }

    // ===== 发信器编辑子面板 =====

    private static ModularPanel buildEmitterEditPanel(PanelSyncManager syncManager, IPanelHandler panelHandler) {
        ModularPanel edit = ModularPanel.defaultPanel("emitter_edit_panel", EDIT_PANEL_W, 140);
        EntityPlayer player = syncManager.getPlayer();

        if (selectedEmitter == null) {
            edit.child(new TextWidget<>(IKey.str("No emitter selected")).pos(10, 10));
            return edit;
        }

        if (!hasPermission(player, selectedEmitter)) {
            edit.child(new TextWidget<>(IKey.str(EnumChatFormatting.RED + "No permission"))
                .pos(10, 10));
            return edit;
        }

        final PartLevelEmitter emitter = selectedEmitter;
        String label = getEmitterLabel(emitter);
        LevelType type = (LevelType) emitter.getConfigManager().getSetting(Settings.LEVEL_TYPE);

        // 标题
        edit.child(new TextWidget<>(IKey.str(label)).pos(10, 8).size(EDIT_PANEL_W - 20, 14));

        // 类型
        edit.child(new TextWidget<>(IKey.str("Type: " + type.name()))
            .pos(10, 28).color(0xFF666666).size(EDIT_PANEL_W - 20, 12));

        // 阈值
        LongSyncValue thresholdSync = new LongSyncValue(
            emitter::getReportingValue,
            v -> {
                emitter.setReportingValue(v);
            }).allowC2S();
        syncManager.syncValue("emitter_threshold", thresholdSync);

        edit.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.threshold"))
            .pos(10, 50).size(60, 12));
        edit.child(new TextFieldWidget().numbersLong(0, Long.MAX_VALUE)
            .pos(75, 48).size(EDIT_PANEL_W - 85, 16)
            .value(thresholdSync));

        // 能量类型只读提示
        if (type == LevelType.ENERGY_LEVEL) {
            edit.child(new TextWidget<>(IKey.str(EnumChatFormatting.YELLOW + "Energy level - read only"))
                .pos(10, 72).size(EDIT_PANEL_W - 20, 10));
        }

        // 关闭按钮
        InteractionSyncHandler closeBtn = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            panelHandler.closePanel();
        });
        edit.child(new ButtonWidget<>().pos(10, 95).size(60, 18)
            .background(GuiTextures.BUTTON_CLEAN)
            .overlay(IKey.str("Close"))
            .syncHandler(closeBtn));

        return edit;
    }

    // ===== 工具方法 =====

    private static StockMonitorCover locateCover(CoverEntry entry) {
        try {
            net.minecraft.world.World world = net.minecraftforge.common.DimensionManager.getWorld(entry.dim);
            if (world == null) return null;
            if (!world.blockExists(entry.x, entry.y, entry.z)) return null;
            TileEntity te = world.getTileEntity(entry.x, entry.y, entry.z);
            if (te instanceof gregtech.api.interfaces.tileentity.ICoverable) {
                gregtech.api.interfaces.tileentity.ICoverable coverable =
                    (gregtech.api.interfaces.tileentity.ICoverable) te;
                ForgeDirection side = ForgeDirection.values()[entry.side];
                Object cover = coverable.getCoverAtSide(side);
                if (cover instanceof StockMonitorCover) {
                    return (StockMonitorCover) cover;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean hasPermission(EntityPlayer player, IGridHost host) {
        try {
            IGridNode node = host.getGridNode(ForgeDirection.UNKNOWN);
            if (node == null) return true;
            IGrid grid = node.getGrid();
            if (grid == null) return true;
            ISecurityGrid security = grid.getCache(ISecurityGrid.class);
            if (security == null) return true;
            return security.hasPermission(player, SecurityPermissions.BUILD);
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean hasPermission(EntityPlayer player, StockMonitorCover cover) {
        // 覆盖板本身不连 AE2 网络，权限检查简化为总是允许
        // 后续可通过覆盖板的 networkId 关联网格做权限检查
        return true;
    }

    private static String formatNumber(long n) {
        if (n >= 1e15) return String.format("%.1fP", n / 1e15);
        if (n >= 1e12) return String.format("%.1fT", n / 1e12);
        if (n >= 1e9) return String.format("%.1fG", n / 1e9);
        if (n >= 1e6) return String.format("%.1fM", n / 1e6);
        if (n >= 1e3) return String.format("%.1fK", n / 1e3);
        return String.valueOf(n);
    }
}
