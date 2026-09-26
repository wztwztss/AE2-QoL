package com.wztwzt.ae2_qof.terminal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.network.ByteBufUtils;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import appeng.api.config.LevelType;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.parts.automation.PartLevelEmitter;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCover;
import com.wztwzt.ae2_qof.cover.stockmonitor.ThresholdMode;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.NeighborAeConnector;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.WirelessAeConnector;
import com.wztwzt.ae2_qof.terminal.CoverRegistry.CoverEntry;

/**
 * 库存统计终端 GUI（第三版）：发信器列表 + 覆盖板列表 + 完整编辑子面板 + Nexus 连接。
 *
 * <h2>为什么要有第三版（3.20.3 根因记录）</h2>
 * 第一/二版把控件建在 {@code if (isServer)} 之后，并把选中项存在**服务端静态表**里。
 * 但 MUI2 的 {@code buildUI} 在**双端各构建一次**面板，而**渲染的是客户端那棵树**：
 * 客户端 {@code worldObj.isRemote == true} ⇒ 连接状态、连接按钮、两个列表**全都不存在**，
 * 只剩标题与两个区块标题（现象：打开终端只有两行字、没有输入框、读不到库存、看不到 Nexus 面板）。
 *
 * <h2>第三版遵循的两条已验证范式</h2>
 * <ul>
 * <li><b>结构双端一致 + 实时数据走 SyncValue</b> —— 与本模组库存检测覆盖板 GUI 完全同款
 * （那份 GUI 用户实测可用：控件无条件构建，阈值/模式/运行态全部经 SyncValue 双向同步）；</li>
 * <li><b>变长列表用 GenericListSyncHandler + DynamicSyncedWidget</b> ——
 * 与 Nexus 自己的 {@code WirelessSelectionPanel} 完全同款（服务端 {@code getter} 求值 →
 * 值以快照形式 S2C → 客户端按快照重建控件；行点击经
 * {@code getOrCreateSyncHandler} 注册的 InteractionSyncHandler 回到服务端执行）。</li>
 * </ul>
 *
 * <p>行数据是**不可变快照**（只含显示所需字段），并且实现了值语义的 equals/hashCode：
 * 否则 {@code detectAndSendChanges} 会每 tick 都判定"变了"而持续发包。
 *
 * <p>列表收集失败时只在**首次**记一条 WARN：静默兜底会让"列表为空"无法区分
 * "确实没有发信器/覆盖板"与"枚举抛异常"（skill 坑位 6）。
 */
public class StockMonitorTerminalGui {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 244;
    private static final int LIST_W = PANEL_W - 16;
    private static final int LIST_H = 74;
    private static final int ROW_H = 14;
    private static final int EDIT_PANEL_W = 220;
    private static final int EDIT_PANEL_H = 170;

    /**
     * 列表重算节流（tick）。getter 每个 tick 都会被 {@code detectAndSendChanges} 调用，
     * 而覆盖板列表要遍历注册表并逐项 {@code getTileEntity}，发信器列表要读 AE 配置项；
     * 1 秒重算一次对这种"集中查看"界面足够，且避免了每 tick 的世界查询与 AE 读取。
     */
    private static final long LIST_REFRESH_INTERVAL = 20L;

    // 当前选中的条目：按玩家 UUID 隔离，避免多名玩家打开同一终端时互相串目标（P1-016）。
    // 注意：这两张表只在**服务端**有意义；客户端界面**不读**它们（第三版的关键修正），
    // 选中的内容由下面的 SyncValue 从服务端推送。
    private static final Map<UUID, CoverEntry> SELECTED_COVERS = new ConcurrentHashMap<>();
    private static final Map<UUID, PartLevelEmitter> SELECTED_EMITTERS = new ConcurrentHashMap<>();

    private static CoverEntry getSelectedCover(EntityPlayer player) {
        return player == null ? null : SELECTED_COVERS.get(player.getUniqueID());
    }

    private static void setSelectedCover(EntityPlayer player, CoverEntry entry) {
        if (player != null) SELECTED_COVERS.put(player.getUniqueID(), entry);
    }

    private static PartLevelEmitter getSelectedEmitter(EntityPlayer player) {
        return player == null ? null : SELECTED_EMITTERS.get(player.getUniqueID());
    }

    private static void setSelectedEmitter(EntityPlayer player, PartLevelEmitter emitter) {
        if (player != null) SELECTED_EMITTERS.put(player.getUniqueID(), emitter);
    }

    public static ModularPanel build(StockMonitorTerminal terminal, PosGuiData guiData,
            PanelSyncManager syncManager, UISettings uiSettings) {
        ModularPanel panel = ModularPanel.defaultPanel("stock_monitor_terminal", PANEL_W, PANEL_H);
        EntityPlayer player = guiData.getPlayer();

        // ===== 子面板（编辑 + 网络选择）=====
        IPanelHandler coverEditPanel = syncManager.syncedPanel(
            "cover_edit", true,
            (panelSyncManager, panelHandler) -> buildCoverEditPanel(terminal, panelSyncManager, panelHandler));

        IPanelHandler emitterEditPanel = syncManager.syncedPanel(
            "emitter_edit", true,
            (panelSyncManager, panelHandler) -> buildEmitterEditPanel(panelSyncManager, panelHandler));

        IPanelHandler netSelectPanel = syncManager.syncedPanel(
            "net_select", true,
            (panelSyncManager, panelHandler) -> {
                if (WirelessAeConnector.isNexusAvailable()) {
                    StockMonitorTerminalWirelessEndpoint endpoint =
                        new StockMonitorTerminalWirelessEndpoint(terminal);
                    return cn.dancingsnow.ae_wireless_nexus.gui.WirelessSelectionPanel
                        .build("ae2qol_terminal_net_select", endpoint, panelSyncManager.getPlayer(),
                            panelSyncManager, true);
                }
                // Nexus 不可用：回退到自定义选择面板（与覆盖板 GUI 同样的回退策略）
                return StockMonitorTerminalNetworkPanel.build(panelSyncManager, terminal, panelHandler);
            });

        // ===== 连接状态（S2C）+ 连接按钮（点击在服务端执行，打开 synced 子面板）=====
        BooleanSyncValue boundSync = new BooleanSyncValue(terminal::isBound);
        syncManager.syncValue("sm_terminal_bound", boundSync);

        InteractionSyncHandler connectAction = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            // 该回调在服务端执行；打开的是 synced 子面板（与覆盖板 GUI 同款可用路径）。
            // Nexus 是否可用由子面板 builder 自己决定：可用→Nexus 原生面板，不可用→回退面板。
            netSelectPanel.openPanel();
        });
        syncManager.syncValue("sm_terminal_connect", connectAction);

        Flow column = Flow.column().coverChildren().childPadding(2).top(6).left(8);

        column.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.stock_monitor.title")).size(LIST_W, 14));

        column.child(
            Flow.row().coverChildren().childPadding(4)
                .child(
                    new TextWidget<>(
                        IKey.dynamic(
                            () -> boundSync.getValue()
                                ? StatCollector.translateToLocal("ae2_qof.terminal.status.bound")
                                : StatCollector.translateToLocal("ae2_qof.terminal.status.unbound"))).size(180, 14))
                .child(
                    new ButtonWidget<>().size(80, 14)
                        .background(GuiTextures.BUTTON_CLEAN)
                        .overlay(
                            IKey.dynamic(
                                () -> StatCollector.translateToLocal(
                                    boundSync.getValue() ? "ae2_qof.gui.stock_monitor.switch_network"
                                        : "ae2_qof.gui.stock_monitor.connect")))
                        .syncHandler(connectAction)));

        // ===== 发信器列表（服务端 getter → S2C 快照 → 客户端重建控件）=====
        column.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.emitters")).size(LIST_W, 12).color(0xFF0066CC));

        final RowCache<EmitterRow> emitterCache = new RowCache<>();
        GenericListSyncHandler<EmitterRow> emitterRows = GenericListSyncHandler.<EmitterRow>builder()
            .getter(() -> emitterCache.get(terminal.getBaseMetaTileEntity(), () -> collectEmitterRows(terminal)))
            .serializer(EmitterRow::write)
            .deserializer(EmitterRow::read)
            .immutableCopy()
            .build();
        syncManager.syncValue("sm_terminal_emitters", emitterRows);

        DynamicLinkedSyncHandler<GenericListSyncHandler<EmitterRow>> emitterListHandler =
            new DynamicLinkedSyncHandler<>(emitterRows).widgetProvider(
                (dynamicSyncManager, value) -> buildEmitterList(value.getValue(), dynamicSyncManager, player,
                    emitterEditPanel));
        syncManager.syncValue("sm_terminal_emitters_dyn", emitterListHandler);

        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, LIST_H)
                .initialChild(buildEmitterList(emitterRows.getValue(), syncManager, player, emitterEditPanel))
                .syncHandler(emitterListHandler));

        column.child(new TextWidget<>(IKey.str("")).size(LIST_W, 4));

        // ===== 覆盖板列表 =====
        column.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.covers")).size(LIST_W, 12).color(0xFF009933));

        final RowCache<CoverRow> coverCache = new RowCache<>();
        GenericListSyncHandler<CoverRow> coverRows = GenericListSyncHandler.<CoverRow>builder()
            .getter(() -> coverCache.get(terminal.getBaseMetaTileEntity(), () -> collectCoverRows(terminal)))
            .serializer(CoverRow::write)
            .deserializer(CoverRow::read)
            .immutableCopy()
            .build();
        syncManager.syncValue("sm_terminal_covers", coverRows);

        DynamicLinkedSyncHandler<GenericListSyncHandler<CoverRow>> coverListHandler =
            new DynamicLinkedSyncHandler<>(coverRows).widgetProvider(
                (dynamicSyncManager, value) -> buildCoverList(value.getValue(), dynamicSyncManager, player,
                    coverEditPanel));
        syncManager.syncValue("sm_terminal_covers_dyn", coverListHandler);

        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, LIST_H)
                .initialChild(buildCoverList(coverRows.getValue(), syncManager, player, coverEditPanel))
                .syncHandler(coverListHandler));

        panel.child(column);
        return panel;
    }

    // ===== 发信器列表 =====

    /** 在服务端枚举终端当前生效网络上的 AE2 标准发信器，生成只含显示字段的快照行。 */
    private static List<EmitterRow> collectEmitterRows(StockMonitorTerminal terminal) {
        List<EmitterRow> rows = new ArrayList<>();
        try {
            IGrid grid = resolveTerminalGrid(terminal);
            if (grid == null) return rows;

            // 注意两件事（都是"列表永远为空"的真实根因）：
            // 1) AE2 的 Grid.getMachines(Class) 是**精确类名**查表（与接口终端同一个坑），
            //    直接传 PartLevelEmitter 会漏掉任何子类 ⇒ 这里按已注册的机器类过滤 instanceof；
            // 2) Grid.getMachines(...) 返回的是 **IGridNode** 集合
            //    （IMachineSet extends IReadOnlyCollection<IGridNode>，已用实例 AE2 jar 核对），
            //    机器必须从 node.getMachine() 取——旧实现直接对 node 做 instanceof，永远匹配不到。
            List<PartLevelEmitter> emitters = new ArrayList<>();
            for (Class<? extends IGridHost> machineClass : grid.getMachinesClasses()) {
                if (!PartLevelEmitter.class.isAssignableFrom(machineClass)) continue;
                for (IGridNode node : grid.getMachines(machineClass)) {
                    Object machine = node == null ? null : node.getMachine();
                    if (machine instanceof PartLevelEmitter) {
                        emitters.add((PartLevelEmitter) machine);
                    }
                }
            }

            // 排序保证行序在两侧一致且稳定（客户端列表就是服务端快照，行号一一对应）
            emitters.sort(
                Comparator.comparing(StockMonitorTerminalGui::getEmitterLabel)
                    .thenComparing(e -> e.getConfigManager() == null ? "" : String.valueOf(e.getConfigManager())));

            for (int i = 0; i < emitters.size(); i++) {
                PartLevelEmitter emitter = emitters.get(i);
                rows.add(new EmitterRow(i, getEmitterLabel(emitter), levelTypeName(emitter),
                    emitter.getReportingValue(), emitter));
            }
        } catch (Throwable t) {
            // 静默兜底会掩盖故障：首次失败记一条 WARN，"列表为空"才能与"枚举出错"区分开。
            ae2qol$warnCollectFailure("发信器", t);
        }
        return rows;
    }

    private static boolean warnedEmitterCollect = false;
    private static boolean warnedCoverCollect = false;

    private static synchronized void ae2qol$warnCollectFailure(String what, Throwable t) {
        if ("发信器".equals(what)) {
            if (warnedEmitterCollect) return;
            warnedEmitterCollect = true;
        } else {
            if (warnedCoverCollect) return;
            warnedCoverCollect = true;
        }
        MyMod.LOG.warn("[StockMonitor] " + what + "列表枚举失败（同类失败只记一次）", t);
    }

    private static IWidget buildEmitterList(List<EmitterRow> rows, PanelSyncManager syncManager, EntityPlayer player,
            IPanelHandler editPanel) {
        RowList list = new RowList();
        if (rows == null || rows.isEmpty()) {
            list.child(
                new TextWidget<>(IKey.lang("ae2_qof.terminal.no_emitters")).color(0xFFAAAAAA).size(LIST_W - 8, 12));
            return list;
        }
        for (EmitterRow row : rows) {
            InteractionSyncHandler select = syncManager.getOrCreateSyncHandler(
                "sm_terminal_emitter_row_" + row.index,
                InteractionSyncHandler.class,
                () -> new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    // 该回调在服务端执行；row 是服务端行的实例，携带真实 AE 部件引用。
                    // 客户端行的 ref 为 null（快照不含对象），因此这里天然是服务端分支。
                    if (row.emitter == null) return;
                    setSelectedEmitter(player, row.emitter);
                    editPanel.openPanel();
                }));
            list.child(
                new ButtonWidget<>().size(LIST_W - 8, ROW_H)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(
                        IKey.str(
                            row.label + "  [" + row.typeShort() + ":" + formatNumber(row.threshold) + "]"))
                    .syncHandler(select));
        }
        return list;
    }

    // ===== 覆盖板列表 =====

    /** 在服务端汇总覆盖板注册表（含一次在线状态刷新），生成快照行。 */
    private static List<CoverRow> collectCoverRows(StockMonitorTerminal terminal) {
        List<CoverRow> rows = new ArrayList<>();
        try {
            TileEntity te = (TileEntity) terminal.getBaseMetaTileEntity();
            if (te == null) return rows;
            CoverRegistry registry = CoverRegistry.get(te.getWorldObj());
            registry.refreshOnlineStatus(); // 只有服务端需要，且只在重算时执行
            Collection<CoverEntry> covers = registry.getAll();

            List<CoverEntry> sorted = new ArrayList<>(covers);
            sorted.sort(
                Comparator.comparingInt((CoverEntry e) -> e.dim)
                    .thenComparingInt(e -> e.x)
                    .thenComparingInt(e -> e.y)
                    .thenComparingInt(e -> e.z)
                    .thenComparingInt(e -> e.side));

            for (int i = 0; i < sorted.size(); i++) {
                CoverEntry entry = sorted.get(i);
                rows.add(new CoverRow(i, entry.dim, entry.x, entry.y, entry.z, entry.side,
                    entry.targetName, entry.online, entry.modeOrdinal, entry.threshold, entry));
            }
        } catch (Throwable t) {
            ae2qol$warnCollectFailure("覆盖板", t);
        }
        return rows;
    }

    private static IWidget buildCoverList(List<CoverRow> rows, PanelSyncManager syncManager, EntityPlayer player,
            IPanelHandler editPanel) {
        RowList list = new RowList();
        if (rows == null || rows.isEmpty()) {
            list.child(
                new TextWidget<>(IKey.lang("ae2_qof.terminal.no_covers")).color(0xFFAAAAAA).size(LIST_W - 8, 12));
            return list;
        }
        for (CoverRow row : rows) {
            InteractionSyncHandler select = syncManager.getOrCreateSyncHandler(
                "sm_terminal_cover_row_" + row.index,
                InteractionSyncHandler.class,
                () -> new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    if (row.entry == null) return;
                    setSelectedCover(player, row.entry);
                    editPanel.openPanel();
                }));
            String label = row.targetName.isEmpty() ? "(unset)" : row.targetName;
            if (!row.online) label = EnumChatFormatting.GRAY + label + " [OFF]";
            String modeName = ThresholdMode.values()[clampMode(row.modeOrdinal)].name();
            list.child(
                new ButtonWidget<>().size(LIST_W - 8, ROW_H)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(
                        IKey.str(label + "  [" + modeName.substring(0, 3) + ":" + formatNumber(row.threshold) + "]"))
                    .syncHandler(select));
        }
        return list;
    }

    // ===== 覆盖板编辑子面板 =====

    /**
     * 编辑子面板：**结构双端一致**，所有显示值来自服务端 getter 的 SyncValue，
     * 所有写入都在服务端 setter 里做权限校验后落盘（P1-015 语义不变）。
     * 未选中时不再提前 return（那会让客户端永远看不到控件），而是显示一行提示。
     */
    private static ModularPanel buildCoverEditPanel(StockMonitorTerminal terminal,
            PanelSyncManager syncManager, IPanelHandler panelHandler) {
        ModularPanel edit = ModularPanel.defaultPanel("cover_edit_panel", EDIT_PANEL_W, EDIT_PANEL_H);

        StringSyncValue targetSync = new StringSyncValue(() -> {
            CoverEntry entry = getSelectedCover(syncManager.getPlayer());
            if (entry == null) return "";
            return entry.targetName.isEmpty() ? "(unset)" : entry.targetName;
        });

        StringSyncValue positionSync = new StringSyncValue(() -> {
            CoverEntry entry = getSelectedCover(syncManager.getPlayer());
            if (entry == null) return "";
            return "Dim:" + entry.dim + " X:" + entry.x + " Y:" + entry.y + " Z:" + entry.z;
        });

        LongSyncValue thresholdSync = new LongSyncValue(
            () -> {
                StockMonitorCover cover = selectedCover(syncManager);
                return cover == null ? 0L : cover.getCoverData().getSlot(0).threshold;
            },
            value -> {
                StockMonitorCover cover = selectedCover(syncManager);
                if (cover == null) return;
                if (!hasPermission(syncManager.getPlayer(), terminal)) return;
                cover.getCoverData().getSlot(0).threshold = value;
                cover.markCoverDirty();
            }).allowC2S();

        IntSyncValue modeSync = new IntSyncValue(
            () -> {
                StockMonitorCover cover = selectedCover(syncManager);
                return cover == null ? 0 : cover.getCoverData().getMode().ordinal();
            },
            value -> {
                StockMonitorCover cover = selectedCover(syncManager);
                if (cover == null) return;
                if (!hasPermission(syncManager.getPlayer(), terminal)) return;
                cover.getCoverData().setMode(ThresholdMode.fromOrdinal(value));
                cover.markCoverDirty();
            }).allowC2S();

        syncManager.syncValue("sm_cover_edit_target", targetSync);
        syncManager.syncValue("sm_cover_edit_pos", positionSync);
        syncManager.syncValue("sm_cover_edit_threshold", thresholdSync);
        syncManager.syncValue("sm_cover_edit_mode", modeSync);

        edit.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> targetSync.getValue().isEmpty()
                        ? EnumChatFormatting.RED + StatCollector.translateToLocal("ae2_qof.terminal.none_selected")
                        : targetSync.getValue())).pos(10, 8).size(EDIT_PANEL_W - 20, 14));

        edit.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.threshold")).pos(10, 32).size(60, 12));
        edit.child(
            new TextFieldWidget().numbersLong(0, Long.MAX_VALUE)
                .pos(75, 30)
                .size(EDIT_PANEL_W - 85, 16)
                .value(thresholdSync));

        edit.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.mode")).pos(10, 58).size(60, 12));
        String[] modeNames = { "BELOW", "ABOVE" };
        for (int i = 0; i < modeNames.length; i++) {
            final int mi = i;
            InteractionSyncHandler modeBtn = new InteractionSyncHandler().setOnMousePressed(mouse -> modeSync.setValue(mi));
            edit.child(
                new ButtonWidget<>().pos(75 + i * 70, 56).size(65, 16)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.str(modeNames[i]))
                    .syncHandler(modeBtn));
        }

        edit.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GRAY + positionSync.getValue()))
                .pos(10, 82)
                .size(EDIT_PANEL_W - 20, 10));

        InteractionSyncHandler closeBtn = new InteractionSyncHandler()
            .setOnMousePressed(mouse -> panelHandler.closePanel());
        edit.child(
            new ButtonWidget<>().pos(10, 110).size(60, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.str("Close"))
                .syncHandler(closeBtn));

        return edit;
    }

    /** 服务端解析"当前选中的覆盖板"实体（区块未加载时返回 null）。 */
    private static StockMonitorCover selectedCover(PanelSyncManager syncManager) {
        CoverEntry entry = getSelectedCover(syncManager.getPlayer());
        return entry == null ? null : locateCover(entry);
    }

    // ===== 发信器编辑子面板 =====

    private static ModularPanel buildEmitterEditPanel(PanelSyncManager syncManager, IPanelHandler panelHandler) {
        ModularPanel edit = ModularPanel.defaultPanel("emitter_edit_panel", EDIT_PANEL_W, EDIT_PANEL_H);

        StringSyncValue labelSync = new StringSyncValue(() -> {
            PartLevelEmitter emitter = getSelectedEmitter(syncManager.getPlayer());
            return emitter == null ? "" : getEmitterLabel(emitter);
        });

        StringSyncValue typeSync = new StringSyncValue(() -> {
            PartLevelEmitter emitter = getSelectedEmitter(syncManager.getPlayer());
            return emitter == null ? "" : levelTypeName(emitter);
        });

        LongSyncValue thresholdSync = new LongSyncValue(
            () -> {
                PartLevelEmitter emitter = getSelectedEmitter(syncManager.getPlayer());
                return emitter == null ? 0L : emitter.getReportingValue();
            },
            value -> {
                PartLevelEmitter emitter = getSelectedEmitter(syncManager.getPlayer());
                if (emitter == null) return;
                if (!hasPermission(syncManager.getPlayer(), emitter)) return;
                emitter.setReportingValue(value);
            }).allowC2S();

        syncManager.syncValue("sm_emitter_edit_label", labelSync);
        syncManager.syncValue("sm_emitter_edit_type", typeSync);
        syncManager.syncValue("sm_emitter_edit_threshold", thresholdSync);

        edit.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> labelSync.getValue().isEmpty()
                        ? EnumChatFormatting.RED + StatCollector.translateToLocal("ae2_qof.terminal.none_selected")
                        : labelSync.getValue())).pos(10, 8).size(EDIT_PANEL_W - 20, 14));

        edit.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GRAY + "Type: " + typeSync.getValue()))
                .pos(10, 28)
                .size(EDIT_PANEL_W - 20, 12));

        edit.child(new TextWidget<>(IKey.lang("ae2_qof.terminal.threshold")).pos(10, 52).size(60, 12));
        edit.child(
            new TextFieldWidget().numbersLong(0, Long.MAX_VALUE)
                .pos(75, 50)
                .size(EDIT_PANEL_W - 85, 16)
                .value(thresholdSync));

        // 能量型发信器阈值只读提示（与设计一致：仅提示，不做限制）
        edit.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> "ENERGY".equals(typeSync.getValue())
                        ? EnumChatFormatting.YELLOW + StatCollector.translateToLocal("ae2_qof.terminal.energy_readonly")
                        : "")).pos(10, 72)
                            .size(EDIT_PANEL_W - 20, 10));

        InteractionSyncHandler closeBtn = new InteractionSyncHandler()
            .setOnMousePressed(mouse -> panelHandler.closePanel());
        edit.child(
            new ButtonWidget<>().pos(10, 100).size(60, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.str("Close"))
                .syncHandler(closeBtn));

        return edit;
    }

    // ===== 工具方法 =====

    /** 可滚动行列表容器（ListWidget 的子类，避免通配符泛型在链式调用里推断困难）。 */
    private static final class RowList extends ListWidget<IWidget, RowList> {

        RowList() {
            size(LIST_W - 8, LIST_H);
        }
    }

    /**
     * 列表重算缓存：getter 每 tick 被调用，这里按 {@link #LIST_REFRESH_INTERVAL} 节流，
     * 既避免每 tick 遍历注册表/读 AE 配置，也保证 {@code detectAndSendChanges} 在无变化时不发包。
     */
    private static final class RowCache<T> {

        /**
         * 初值必须让它"立刻认为过期"，但不能用 {@code Long.MIN_VALUE}：
         * {@code now - Long.MIN_VALUE} 会**溢出成负数**，于是首次调用会被判定为"还没到间隔"
         * 而永远返回空列表（列表就永远不出内容）。用 -间隔 起步即可让第一次调用立即重算。
         */
        private long lastTick = -LIST_REFRESH_INTERVAL;
        private List<T> rows = Collections.emptyList();

        List<T> get(IGregTechTileEntity base, java.util.function.Supplier<List<T>> compute) {
            long now = 0L;
            try {
                if (base != null && base.getWorld() != null) now = base.getWorld().getTotalWorldTime();
            } catch (Throwable ignored) {}
            if (now - lastTick < LIST_REFRESH_INTERVAL) return rows;
            lastTick = now;
            List<T> fresh = compute.get();
            rows = fresh == null ? Collections.emptyList() : fresh;
            return rows;
        }
    }

    /** 发信器行快照：只有显示字段 + 服务端临时持有的部件引用（不参与序列化与等价性）。 */
    static final class EmitterRow {

        final int index;
        final String label;
        final String typeName;
        final long threshold;
        /** 仅服务端非 null（getter 在服务端生成），客户端快照反序列化后为 null。 */
        final transient PartLevelEmitter emitter;

        EmitterRow(int index, String label, String typeName, long threshold, PartLevelEmitter emitter) {
            this.index = index;
            this.label = label;
            this.typeName = typeName;
            this.threshold = threshold;
            this.emitter = emitter;
        }

        String typeShort() {
            return typeName.isEmpty() ? "?" : typeName.substring(0, 1);
        }

        static void write(PacketBuffer buf, EmitterRow row) throws IOException {
            buf.writeInt(row.index);
            ByteBufUtils.writeUTF8String(buf, row.label);
            ByteBufUtils.writeUTF8String(buf, row.typeName);
            buf.writeLong(row.threshold);
        }

        static EmitterRow read(PacketBuffer buf) throws IOException {
            return new EmitterRow(buf.readInt(), ByteBufUtils.readUTF8String(buf), ByteBufUtils.readUTF8String(buf),
                buf.readLong(), null);
        }

        // 值语义 equals/hashCode：列表内容不变时不应触发同步与重建
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EmitterRow)) return false;
            EmitterRow r = (EmitterRow) o;
            return index == r.index && threshold == r.threshold
                && label.equals(r.label)
                && typeName.equals(r.typeName);
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(index);
            result = 31 * result + label.hashCode();
            result = 31 * result + typeName.hashCode();
            result = 31 * result + Long.hashCode(threshold);
            return result;
        }
    }

    /** 覆盖板行快照：位置即身份，其余为显示摘要。 */
    static final class CoverRow {

        final int index;
        final int dim;
        final int x;
        final int y;
        final int z;
        final int side;
        final String targetName;
        final boolean online;
        final int modeOrdinal;
        final long threshold;
        /** 仅服务端非 null。 */
        final transient CoverEntry entry;

        CoverRow(int index, int dim, int x, int y, int z, int side, String targetName, boolean online,
                int modeOrdinal, long threshold, CoverEntry entry) {
            this.index = index;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
            this.targetName = targetName;
            this.online = online;
            this.modeOrdinal = modeOrdinal;
            this.threshold = threshold;
            this.entry = entry;
        }

        static void write(PacketBuffer buf, CoverRow row) throws IOException {
            buf.writeInt(row.index);
            buf.writeInt(row.dim);
            buf.writeInt(row.x);
            buf.writeInt(row.y);
            buf.writeInt(row.z);
            buf.writeInt(row.side);
            ByteBufUtils.writeUTF8String(buf, row.targetName);
            buf.writeBoolean(row.online);
            buf.writeInt(row.modeOrdinal);
            buf.writeLong(row.threshold);
        }

        static CoverRow read(PacketBuffer buf) throws IOException {
            int index = buf.readInt();
            int dim = buf.readInt();
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            int side = buf.readInt();
            String targetName = ByteBufUtils.readUTF8String(buf);
            boolean online = buf.readBoolean();
            int modeOrdinal = buf.readInt();
            long threshold = buf.readLong();
            return new CoverRow(index, dim, x, y, z, side, targetName, online, modeOrdinal, threshold, null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CoverRow)) return false;
            CoverRow r = (CoverRow) o;
            return index == r.index && dim == r.dim && x == r.x && y == r.y && z == r.z && side == r.side
                && online == r.online
                && modeOrdinal == r.modeOrdinal
                && threshold == r.threshold
                && targetName.equals(r.targetName);
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(index);
            result = 31 * result + Integer.hashCode(dim);
            result = 31 * result + Integer.hashCode(x);
            result = 31 * result + Integer.hashCode(y);
            result = 31 * result + Integer.hashCode(z);
            result = 31 * result + Integer.hashCode(side);
            result = 31 * result + targetName.hashCode();
            result = 31 * result + Boolean.hashCode(online);
            result = 31 * result + Integer.hashCode(modeOrdinal);
            result = 31 * result + Long.hashCode(threshold);
            return result;
        }
    }

    private static int clampMode(int ordinal) {
        int max = ThresholdMode.values().length - 1;
        return ordinal < 0 ? 0 : (ordinal > max ? max : ordinal);
    }

    private static String levelTypeName(PartLevelEmitter emitter) {
        try {
            Object type = emitter.getConfigManager().getSetting(Settings.LEVEL_TYPE);
            if (type instanceof LevelType) return ((LevelType) type).name();
        } catch (Throwable ignored) {}
        return "";
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

    /**
     * P1-015：覆盖板远程编辑权限。覆盖板本身不是 AE2 设备，因此沿用终端的连网结果：
     * 终端绑定/邻接的 AE2 网络存在时，必须拥有该网络 BUILD 权限才能远程改配置；
     * 终端完全没有连网时不做拦截（此时覆盖板也无法被别的网络操作）。
     */
    private static boolean hasPermission(EntityPlayer player, StockMonitorTerminal terminal) {
        IGrid grid = resolveTerminalGrid(terminal);
        if (grid == null) return true;
        try {
            ISecurityGrid security = grid.getCache(ISecurityGrid.class);
            if (security == null || !security.isAvailable()) return true;
            return security.hasPermission(player, SecurityPermissions.BUILD);
        } catch (Throwable t) {
            return true;
        }
    }

    /** 解析终端当前生效的 AE2 网络：优先 Nexus 无线绑定，其次邻接连接。 */
    static IGrid resolveTerminalGrid(StockMonitorTerminal terminal) {
        try {
            TileEntity te = (TileEntity) terminal.getBaseMetaTileEntity();
            if (te == null) return null;
            if (terminal.isBound() && WirelessAeConnector.isNexusAvailable()) {
                try {
                    UUID netId = UUID.fromString(terminal.getNetworkId());
                    IGrid grid = WirelessAeConnector.getGridForNetwork(netId, te.getWorldObj());
                    if (grid != null) return grid;
                } catch (IllegalArgumentException ignored) {}
            }
            return NeighborAeConnector.findGrid(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord,
                ForgeDirection.UNKNOWN);
        } catch (Throwable t) {
            return null;
        }
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
