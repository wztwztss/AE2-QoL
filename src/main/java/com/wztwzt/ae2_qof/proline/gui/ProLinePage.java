package com.wztwzt.ae2_qof.proline.gui;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.proline.ItemProductionLineCard;
import com.wztwzt.ae2_qof.proline.ItemRow;
import com.wztwzt.ae2_qof.proline.MachineRef;
import com.wztwzt.ae2_qof.proline.ProLineData;
import com.wztwzt.ae2_qof.proline.ProLineGate;
import com.wztwzt.ae2_qof.proline.ProLineImporter;
import com.wztwzt.ae2_qof.proline.ProLineManager;
import com.wztwzt.ae2_qof.proline.ProLineSummary;
import com.wztwzt.ae2_qof.proline.ProductionLine;
import com.wztwzt.ae2_qof.proline.ProductionStep;
import com.wztwzt.ae2_qof.terminal.StockMonitorTerminal;

import cpw.mods.fml.common.network.ByteBufUtils;
import serverutils.data.ClaimedChunks;
import serverutils.lib.math.ChunkDimPos;

/**
 * 「产线」页（库存统计终端第三页，设计 §4）。
 *
 * <h2>为什么这样构（全部沿用已实测范式，坑 #21/#24/#23 的落法）</h2>
 * <ul>
 * <li>结构<b>双端一致</b>、服务端数据一律 SyncValue 下发（fix31/fix32 实测可用路径）；</li>
 * <li>变长列表 = {@code GenericListSyncHandler + DynamicLinkedSyncHandler + ListWidget 子类}，
 * 行快照带<b>值语义 equals</b>（否则 detectAndSendChanges 每 tick 判"变了"⇒ 持续发包）；</li>
 * <li>选中/目标状态放<b>服务端 per-player Map</b>（P1-016 语义）；</li>
 * <li>getter 有 1s 节流（列表 getter 每 tick 被调，fix31 的 RowCache 同理）；</li>
 * <li>所有写操作走 {@code InteractionSyncHandler}（回调在服务端执行），不做客户端写。</li>
 * </ul>
 */
public final class ProLinePage {

    // ===== 尺寸（与终端面板 380×252 / 第三页 CONTENT 对齐）=====
    private static final int CONTENT_W = 336; // 380 - 40(页签) - 4
    private static final int PAGE_H = 220; // PAGE_H = 252-26-6
    private static final int LIST_W = CONTENT_W - 8;
    private static final int LINE_LIST_H = 44;
    private static final int STEP_LIST_H = 96;
    private static final int SUMM_LIST_H = 40;
    private static final int ROW_H = 22;
    private static final int ROW_W = LIST_W - 8;
    private static final int ICON = 16;
    private static final int NAME_W = 150;
    private static final int VALUE_W = 84;
    private static final int BTN_W = 28;
    private static final int PANEL_W2 = 300;
    private static final int PANEL_H2 = 150;
    private static final long REFRESH_MS = 1000L;

    /** 服务端 per-player 状态：选中的产线（O3 的"导入目标"也由它派生）。 */
    private static final Map<UUID, String> SELECTED_LINE = new ConcurrentHashMap<>();
    /** 服务端 per-player 状态：最近点过动作的步骤（步骤小面板/候选面板/写卡用）。 */
    private static final Map<UUID, String> LAST_STEP = new ConcurrentHashMap<>();
    /** 导入会话（A5/O8 的 v1 落法：单段 ≤28KB，客户端预粘贴、服务端权威解析）。 */
    private static final Map<UUID, String> IMPORT_STAGED = new ConcurrentHashMap<>();
    private static final Map<UUID, String> IMPORT_FEEDBACK = new ConcurrentHashMap<>();

    private ProLinePage() {}

    private static ProductionLine lineOf(EntityPlayer player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return null;
        String id = SELECTED_LINE.get(player.getUniqueID());
        if (id == null) return null;
        WorldServer w = ProLineData.anyLoadedServerWorld();
        return w == null ? null
            : ProLineData.get(w)
                .findLine(id);
    }

    private static ProductionStep stepOf(EntityPlayer player) {
        ProductionLine line = lineOf(player);
        String stepId = LAST_STEP.get(player == null ? null : player.getUniqueID());
        return line == null || stepId == null ? null : line.findStep(stepId);
    }

    // ===== 页面装配入口（StockMonitorTerminalGui.build 的第三个 addPage 调这里）=====
    public static IWidget buildPage(StockMonitorTerminal terminal, PanelSyncManager syncManager, EntityPlayer player) {
        Flow column = Flow.column()
            .size(CONTENT_W, PAGE_H)
            .childPadding(2);

        // ---- 产线列表 ----
        GenericListSyncHandler<LineRow> lines = GenericListSyncHandler.<LineRow>builder()
            .getter(cached(() -> collectLineRows()))
            .serializer(LineRow::write)
            .deserializer(LineRow::read)
            .immutableCopy()
            .build();
        syncManager.syncValue("proline_lines", lines);

        // ---- 步骤列表（getter 捕获 session 的 syncManager：按当前会话玩家取选中线）----
        GenericListSyncHandler<StepRow> steps = GenericListSyncHandler.<StepRow>builder()
            .getter(cached(() -> collectStepRows(syncManager.getPlayer())))
            .serializer(StepRow::write)
            .deserializer(StepRow::read)
            .immutableCopy()
            .build();
        DynamicLinkedSyncHandler<GenericListSyncHandler<StepRow>> stepsDyn = new DynamicLinkedSyncHandler<>(steps)
            .widgetProvider(
                (dm, v) -> buildStepList(
                    v.getValue(),
                    syncManager,
                    stepPanelOf(syncManager, terminal),
                    candidatesPanelOf(syncManager, terminal)));
        syncManager.syncValue("proline_steps_dyn", stepsDyn);

        // ---- 汇总行 ----
        GenericListSyncHandler<String> summary = GenericListSyncHandler.<String>builder()
            .getter(cached(() -> collectSummaryRows(syncManager.getPlayer())))
            .serializer((buf, s) -> ByteBufUtils.writeUTF8String(buf, s))
            .deserializer(buf -> ByteBufUtils.readUTF8String(buf))
            .immutableCopy()
            .build();
        DynamicLinkedSyncHandler<GenericListSyncHandler<String>> summaryDyn = new DynamicLinkedSyncHandler<>(summary)
            .widgetProvider((dm, v) -> buildTextList(v.getValue(), 0xFF303030, 11));
        syncManager.syncValue("proline_summary_dyn", summaryDyn);

        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, LINE_LIST_H)
                .initialChild(buildLineList(lines.getValue(), syncManager))
                .syncHandler(
                    new DynamicLinkedSyncHandler<>(lines)
                        .widgetProvider((dm, v) -> buildLineList(v.getValue(), syncManager))));

        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, STEP_LIST_H)
                .initialChild(
                    buildStepList(
                        steps.getValue(),
                        syncManager,
                        stepPanelOf(syncManager, terminal),
                        candidatesPanelOf(syncManager, terminal)))
                .syncHandler(stepsDyn));

        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, SUMM_LIST_H)
                .initialChild(buildTextList(summary.getValue(), 0xFF303030, 11))
                .syncHandler(summaryDyn));

        // ---- 顶栏 ----
        IPanelHandler importPanel = importPanelOf(syncManager, terminal);
        Flow bar = Flow.row()
            .size(LIST_W, 18)
            .childPadding(4);
        bar.child(
            new ButtonWidget<>().size(70, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.new"))
                .syncHandler(clickServer(syncManager, pm -> {
                    TileEntity base = terminalBase(terminal);
                    ProLineManager.createLine(
                        pm.getPlayer(),
                        "产线 " + (System.currentTimeMillis() % 1000),
                        terminalNetworkId(terminal),
                        base == null ? 0 : base.getWorldObj().provider.dimensionId,
                        base == null ? 0 : base.xCoord,
                        base == null ? 0 : base.yCoord,
                        base == null ? 0 : base.zCoord);
                })));
        bar.child(
            new ButtonWidget<>().size(70, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.import"))
                .syncHandler(clickServer(syncManager, pm -> importPanel.openPanel())));
        bar.child(
            new TextWidget<>(IKey.lang("ae2_qof.proline.hint")).size(LIST_W - 152, 18)
                .color(0xFF888888));
        column.child(bar);
        return column;
    }

    // ===== 面板句柄（懒建：syncedPanel 的 builder 在打开时才执行）=====
    private static final Map<String, IPanelHandler> PANEL_CACHE = new ConcurrentHashMap<>();

    private static IPanelHandler stepPanelOf(PanelSyncManager sm, StockMonitorTerminal terminal) {
        return PANEL_CACHE.computeIfAbsent(
            sm.getPlayer()
                .getUniqueID() + ":step",
            k -> sm.syncedPanel("proline_step_ui", true, (pm, ph) -> buildStepPanel(pm, ph)));
    }

    private static IPanelHandler candidatesPanelOf(PanelSyncManager sm, StockMonitorTerminal terminal) {
        return PANEL_CACHE.computeIfAbsent(
            sm.getPlayer()
                .getUniqueID() + ":cand",
            k -> sm.syncedPanel("proline_candidates", true, (pm, ph) -> buildCandidatesPanel(pm, ph)));
    }

    private static IPanelHandler importPanelOf(PanelSyncManager sm, StockMonitorTerminal terminal) {
        return PANEL_CACHE.computeIfAbsent(
            sm.getPlayer()
                .getUniqueID() + ":import",
            k -> sm.syncedPanel("proline_import", true, (pm, ph) -> buildImportPanel(pm, ph, terminal)));
    }

    private static TileEntity terminalBase(StockMonitorTerminal terminal) {
        try {
            return (TileEntity) terminal.getBaseMetaTileEntity();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String terminalNetworkId(StockMonitorTerminal terminal) {
        try {
            return terminal.getNetworkId();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 简单 1s 节流的 Supplier（列表 getter 每 tick 被调；fix31 RowCache 同理，时间源换墙钟避免 world 时钟差）。 */
    private static <T> java.util.function.Supplier<List<T>> cached(java.util.function.Supplier<List<T>> compute) {
        return new java.util.function.Supplier<List<T>>() {

            private long last = -REFRESH_MS;
            private List<T> value = java.util.Collections.emptyList();

            @Override
            public List<T> get() {
                long now = System.currentTimeMillis();
                if (now - last < REFRESH_MS) return value;
                last = now;
                List<T> fresh;
                try {
                    fresh = compute.get();
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 产线列表收集异常", t);
                    fresh = value;
                }
                value = fresh == null ? java.util.Collections.<T>emptyList() : fresh;
                return value;
            }
        };
    }

    /** 服务端回调：syncManager 在构建期捕获（回调在服务端执行，getPlayer 即会话玩家）。 */
    private static InteractionSyncHandler clickServer(final PanelSyncManager syncManager,
        java.util.function.Consumer<PanelSyncManager> action) {
        return new InteractionSyncHandler().setOnMousePressed(mouse -> action.accept(syncManager));
    }

    // ===== 列表收集（服务端）=====

    private static List<LineRow> collectLineRows() {
        List<LineRow> out = new ArrayList<>();
        WorldServer w = ProLineData.anyLoadedServerWorld();
        if (w == null) return out;
        int idx = 0;
        for (ProductionLine l : ProLineData.get(w).lines) {
            out.add(new LineRow(idx++, l));
        }
        return out;
    }

    private static List<StepRow> collectStepRows(EntityPlayer sessionPlayer) {
        ProductionLine line = lineOf(sessionPlayer);
        List<StepRow> out = new ArrayList<>();
        if (line == null) return out;
        for (int i = 0; i < line.steps.size(); i++) {
            out.add(StepRow.of(i, line, line.steps.get(i)));
        }
        return out;
    }

    private static List<String> collectSummaryRows(EntityPlayer sessionPlayer) {
        ProductionLine line = lineOf(sessionPlayer);
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        out.add(
            EnumChatFormatting.GRAY + line.name
                + (line.linePaused ? EnumChatFormatting.DARK_PURPLE + "〔整线已暂停〕" : "")
                + (line.effectiveNetworkId()
                    .isEmpty() ? EnumChatFormatting.RED + "〔口径：—〕" : ""));
        for (String key : ProLineSummary.externalInputs(line)) {
            out.add(sumRow("输入", key, line, 0));
        }
        for (String key : ProLineSummary.externalOutputs(line)) {
            out.add(sumRow("输出", key, line, 1));
        }
        return out;
    }

    private static String sumRow(String side, String key, ProductionLine line, int cumIdx) {
        BigInteger[] twin = line.cumulative.get(key);
        BigInteger cum = twin == null ? BigInteger.ZERO : twin[cumIdx];
        Double rate = line.samples.get(key) == null ? null
            : line.samples.get(key)
                .recentRatePerSec();
        long perHour = rate == null || rate.isNaN() ? 0L : (long) (rate * 3600.0);
        return side + " "
            + displayKey(key)
            + "  累计 "
            + ProLineSummary.kmg(cum)
            + "  "
            + (rate == null ? "" : "≈" + ProLineSummary.kmg(BigInteger.valueOf(perHour)) + "/h");
    }

    private static String displayKey(String key) {
        if (key == null) return "?";
        if (key.startsWith("fluid:")) return "(流体)" + key.substring(6);
        int at = key.lastIndexOf('@');
        return at > 0 ? key.substring(0, at) : key;
    }

    // ===== 行构建 =====

    private static IWidget buildLineList(List<LineRow> rows, PanelSyncManager syncManager) {
        PlList list = new PlList(LIST_W, LINE_LIST_H);
        if (rows == null || rows.isEmpty()) {
            list.child(
                new TextWidget<>(IKey.lang("ae2_qof.proline.no_lines")).color(0xFFAAAAAA)
                    .size(LIST_W - 8, 12));
            return list;
        }
        for (LineRow row : rows) list.child(lineRowWidget(syncManager, row));
        return list;
    }

    private static IWidget lineRowWidget(final PanelSyncManager syncManager, final LineRow row) {
        String label = row.name + (row.pausedLine ? EnumChatFormatting.DARK_PURPLE + "〔暂停〕" : "")
            + EnumChatFormatting.GRAY
            + "（"
            + row.stepCount
            + "步 "
            + row.running
            + "/"
            + row.bound
            + "在产）";
        return new ButtonWidget<>().size(ROW_W, ROW_H)
            .background(GuiTextures.BUTTON_CLEAN)
            .overlay(IKey.str(label))
            .tooltip(
                t -> t.addLine(IKey.str(label))
                    .addLine(IKey.str(row.networkFlag)))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {
                // 服务端：选中该线 + 计入"活跃产线"（采样对象）
                SELECTED_LINE.put(
                    syncManager.getPlayer()
                        .getUniqueID(),
                    row.lineId);
                ProLineManager.touchActive(row.lineId);
            }));
    }

    private static IWidget buildStepList(List<StepRow> rows, PanelSyncManager syncManager, IPanelHandler stepPanel,
        IPanelHandler candidatesPanel) {
        PlList list = new PlList(LIST_W, STEP_LIST_H);
        if (rows == null || rows.isEmpty()) {
            list.child(
                new TextWidget<>(IKey.lang("ae2_qof.proline.select_line_first")).color(0xFFAAAAAA)
                    .size(LIST_W - 8, 12));
            return list;
        }
        for (StepRow row : rows) {
            list.child(stepRowWidget(syncManager, row, stepPanel, candidatesPanel));
        }
        return list;
    }

    private static IWidget stepRowWidget(final PanelSyncManager syncManager, final StepRow row, IPanelHandler stepPanel,
        IPanelHandler candidatesPanel) {
        String badge = EnumChatFormatting.BOLD + StatCollector.translateToLocal("ae2_qof.proline.badge." + row.badge);
        Flow r = Flow.row()
            .size(ROW_W, ROW_H)
            .childPadding(2)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        r.child(iconWidget(row.icon));
        r.child(
            new ButtonWidget<>().size(NAME_W, ROW_H)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.str((row.seq + 1) + " · " + row.name))
                .tooltip(
                    t -> t.addLine(IKey.str(row.name))
                        .addLine(IKey.str("类型: " + row.type))
                        .addLine(IKey.str(badge))
                        .addLine(IKey.str("主产物: " + row.outText)))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    LAST_STEP.put(
                        syncManager.getPlayer()
                            .getUniqueID(),
                        row.stepId);
                    stepPanel.openPanel();
                })));
        r.child(
            new ButtonWidget<>().size(VALUE_W, ROW_H)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.str(row.runText + " · " + row.outText))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    LAST_STEP.put(
                        syncManager.getPlayer()
                            .getUniqueID(),
                        row.stepId);
                    candidatesPanel.openPanel();
                })));
        r.child(
            new ButtonWidget<>().size(BTN_W, ROW_H)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.del"))
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.proline.del")))
                .syncHandler(
                    clickServer(
                        syncManager,
                        pm -> { ProLineManager.removeStep(pm.getPlayer(), row.lineId, row.stepId); })));
        return r;
    }

    private static IWidget buildTextList(List<String> rows, int color, int rowH) {
        PlList list = new PlList(LIST_W, rowH * 4);
        if (rows != null) {
            for (String s : rows) {
                list.child(
                    new TextWidget<>(IKey.str(s)).size(LIST_W - 8, rowH)
                        .color(color));
            }
        }
        return list;
    }

    /** 行首图标：物品优先（ItemDrawable 16×16，坑 #23），没有就留空位。 */
    private static IWidget iconWidget(ItemStack item) {
        if (item != null && item.getItem() != null) {
            return new ItemDrawable(item).asWidget()
                .size(ICON, ICON);
        }
        Widget<?> empty = new Widget<>().size(ICON, ICON);
        return empty;
    }

    // ===== 子面板：产线元数据（重命名 / 删除整线 / 整线暂停）=====

    private static ModularPanel buildStepPanel(PanelSyncManager syncManager, IPanelHandler panelHandler) {
        ModularPanel edit = ModularPanel.defaultPanel("proline_step_panel", PANEL_W2, PANEL_H2);
        BooleanSyncValue pausedSync = new BooleanSyncValue(() -> {
            ProductionStep s = stepOf(syncManager.getPlayer());
            ProductionLine l = lineOf(syncManager.getPlayer());
            return l != null && s != null && l.pausedStepIds.contains(s.id);
        }, value -> {
            ProductionStep s = stepOf(syncManager.getPlayer());
            ProductionLine l = lineOf(syncManager.getPlayer());
            if (l != null && s != null) {
                ProLineManager.setStepPaused(syncManager.getPlayer(), l.id, s.id, value);
            }
        }).allowC2S();
        syncManager.syncValue("proline_step_paused", pausedSync);

        edit.child(new TextWidget<>(IKey.dynamic(() -> {
            ProductionLine l = lineOf(syncManager.getPlayer());
            ProductionStep s = stepOf(syncManager.getPlayer());
            if (l == null || s == null)
                return EnumChatFormatting.RED + StatCollector.translateToLocal("ae2_qof.proline.none_selected");
            return l.name + " · " + s.name + EnumChatFormatting.GRAY + "  type=" + s.machineType;
        })).pos(10, 8)
            .size(PANEL_W2 - 20, 24));

        edit.child(
            new ButtonWidget<>().pos(10, 38)
                .size(120, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.btn_pause_toggle"))
                .syncHandler(clickServer(syncManager, pm -> pausedSync.setValue(!pausedSync.getValue()))));
        edit.child(
            new ButtonWidget<>().pos(136, 38)
                .size(150, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.btn_write_card"))
                .syncHandler(clickServer(syncManager, pm -> {
                    ProductionLine l = lineOf(pm.getPlayer());
                    ProductionStep s = stepOf(pm.getPlayer());
                    if (l != null && s != null) {
                        ItemStack hand = pm.getPlayer()
                            .getHeldItem();
                        ItemProductionLineCard.programCard(hand, l.id, s.id);
                    }
                })));
        InteractionSyncHandler closeBtn = new InteractionSyncHandler()
            .setOnMousePressed(mouse -> panelHandler.closePanel());
        edit.child(
            new ButtonWidget<>().pos(10, 64)
                .size(60, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.gui.stock_monitor.close"))
                .syncHandler(closeBtn));
        return edit;
    }

    // ===== 子面板：候选绑定（§5.1，A2/B5：点选绑定 + 50ms 预算截断）=====

    private static ModularPanel buildCandidatesPanel(PanelSyncManager syncManager, IPanelHandler panelHandler) {
        ModularPanel edit = ModularPanel.defaultPanel("proline_candidates_panel", 320, 200);
        GenericListSyncHandler<CandidateRow> rows = GenericListSyncHandler.<CandidateRow>builder()
            .getter(cached(() -> scanCandidates(syncManager)))
            .serializer(CandidateRow::write)
            .deserializer(CandidateRow::read)
            .immutableCopy()
            .build();
        DynamicLinkedSyncHandler<GenericListSyncHandler<CandidateRow>> dyn = new DynamicLinkedSyncHandler<>(rows)
            .widgetProvider((dm, v) -> buildCandidateList(v.getValue(), syncManager));
        syncManager.syncValue("proline_candidates_dyn", dyn);

        edit.child(
            new TextWidget<>(IKey.lang("ae2_qof.proline.candidates.title")).pos(8, 6)
                .size(300, 12));
        edit.child(
            new DynamicSyncedWidget<>().size(300, 148)
                .initialChild(buildCandidateList(rows.getValue(), syncManager))
                .syncHandler(dyn));
        InteractionSyncHandler closeBtn = new InteractionSyncHandler()
            .setOnMousePressed(mouse -> panelHandler.closePanel());
        edit.child(
            new ButtonWidget<>().pos(8, 168)
                .size(60, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.gui.stock_monitor.close"))
                .syncHandler(closeBtn));
        return edit;
    }

    private static IWidget buildCandidateList(List<CandidateRow> rows, PanelSyncManager syncManager) {
        PlList list = new PlList(300, 148);
        if (rows == null || rows.isEmpty()) {
            list.child(new TextWidget<>(IKey.lang("ae2_qof.proline.candidates.empty")).size(290, 12));
            return list;
        }
        for (final CandidateRow row : rows) {
            // A2：名称在客户端解析（mteId → GT 机器物品栈 → 显示名）；服务端 label 仅作兜底
            String label = null;
            try {
                Object blk = gregtech.api.GregTechAPI.sBlockMachines;
                if (blk instanceof net.minecraft.block.Block) {
                    net.minecraft.item.ItemStack is = new net.minecraft.item.ItemStack(
                        (net.minecraft.block.Block) blk,
                        1,
                        row.mteId);
                    if (is.getItem() != null) {
                        String n = is.getDisplayName();
                        if (n != null && !n.isEmpty()) label = n;
                    }
                }
            } catch (Throwable ignored) {}
            String candLabel = (label == null ? row.label : label) + EnumChatFormatting.GRAY
                + " D"
                + row.dim
                + " ["
                + row.x
                + ","
                + row.y
                + ","
                + row.z
                + "]"
                + (row.bound ? EnumChatFormatting.AQUA + " （已绑）" : "");
            list.child(
                new ButtonWidget<>().size(290, 20)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.str(candLabel))
                    .tooltip(
                        t -> t.addLine(IKey.str(candLabel))
                            .addLine(IKey.str(row.tip)))
                    .syncHandler(clickServer(syncManager, sm -> {
                        EntityPlayer p = sm.getPlayer();
                        ProductionLine l = lineOf(p);
                        ProductionStep s = stepOf(p);
                        if (l != null && s != null) {
                            MachineRef ref = new MachineRef(row.dim, row.x, row.y, row.z, System.currentTimeMillis());
                            if (row.bound) {
                                ProLineManager.unbindMachine(p, l.id, s.id, ref);
                            } else {
                                ProLineManager.bindMachine(p, l.id, s.id, ref);
                            }
                        }
                    })));
        }
        return list;
    }

    /** 候选扫描（§5.1 v1 落法）：团队声明区块内已加载 GT 多方块；50ms 预算截断 + 行数护栏（B5）。 */
    private static List<CandidateRow> scanCandidates(PanelSyncManager syncManager) {
        List<CandidateRow> out = new ArrayList<>();
        EntityPlayer player = syncManager.getPlayer();
        if (player == null || player.worldObj.isRemote || !ProLineGate.isPresent()) return out;
        Object myTeam = ProLineGate.teamOfPlayer(player);
        if (myTeam == null) return out;
        WorldServer any = ProLineData.anyLoadedServerWorld();
        ProLineData data = any == null ? null : ProLineData.get(any);
        if (data == null) return out;

        long t0 = System.nanoTime();
        int rows = 0;
        try {
            for (ChunkDimPos pos : new ArrayList<>(ClaimedChunks.instance.getAllClaimedPositions())) {
                if (System.nanoTime() - t0 > 50_000_000L) break; // B5：50ms 预算
                if (ProLineGate.teamOfChunk(pos.dim, pos.posX, pos.posZ) != myTeam) continue;
                WorldServer world = DimensionManager.getWorld(pos.dim);
                if (world == null) continue;
                net.minecraft.world.chunk.Chunk chunk = world.getChunkFromChunkCoords(pos.posX, pos.posZ);
                for (Object teo : new ArrayList<Object>(chunk.chunkTileEntityMap.values())) {
                    if (!(teo instanceof TileEntity)) continue;
                    TileEntity te = (TileEntity) teo;
                    if (!(te instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity)) continue;
                    gregtech.api.interfaces.metatileentity.IMetaTileEntity mte = ((gregtech.api.interfaces.tileentity.IGregTechTileEntity) te)
                        .getMetaTileEntity();
                    if (!(mte instanceof gregtech.api.metatileentity.implementations.MTEMultiBlockBase)) continue;
                    gregtech.api.metatileentity.implementations.MTEMultiBlockBase mmb = (gregtech.api.metatileentity.implementations.MTEMultiBlockBase) mte;
                    if (rows++ > 64) break;
                    MachineRef probe = new MachineRef(pos.dim, te.xCoord, te.yCoord, te.zCoord, 0L);
                    boolean bound = isBoundAnywhere(data, probe);
                    // A2：不传服务端定稿的名称串——只传 mteId 与坐标，名称/图标由客户端自己解析
                    out.add(
                        new CandidateRow(
                            bound ? EnumChatFormatting.AQUA + "（已绑）" : "",
                            "点击绑定/解绑到当前步骤",
                            bound,
                            pos.dim,
                            te.xCoord,
                            te.yCoord,
                            te.zCoord,
                            mmb.getBaseMetaTileEntity()
                                .getMetaTileID()));
                }
                if (rows > 64) break;
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 产线候选扫描异常", t);
        }
        return out;
    }

    private static boolean isBoundAnywhere(ProLineData data, MachineRef ref) {
        for (ProductionLine l : data.lines) {
            for (ProductionStep s : l.steps) if (s.machines.contains(ref)) return true;
        }
        return false;
    }

    // ===== 子面板：wiki JSON 导入（A5/O8 v1：单段 ≤28KB；名称解析失败=标红不静默）=====

    private static ModularPanel buildImportPanel(PanelSyncManager syncManager, IPanelHandler panelHandler,
        StockMonitorTerminal terminal) {
        ModularPanel edit = ModularPanel.defaultPanel("proline_import_panel", 340, 170);
        StringSyncValue jsonSync = new StringSyncValue(() -> "", value -> {
            UUID id = syncManager.getPlayer() == null ? null
                : syncManager.getPlayer()
                    .getUniqueID();
            if (id != null) IMPORT_STAGED.put(id, value);
        }).allowC2S();
        syncManager.syncValue("proline_import_json", jsonSync);

        edit.child(
            new TextWidget<>(IKey.lang("ae2_qof.proline.import.title")).pos(8, 6)
                .size(320, 12));
        edit.child(
            new TextFieldWidget().pos(8, 22)
                .size(320, 40)
                .value(jsonSync));
        edit.child(
            new ButtonWidget<>().pos(8, 68)
                .size(90, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.import.go"))
                .syncHandler(clickServer(syncManager, pm -> runImport(pm, terminal))));
        edit.child(new TextWidget<>(IKey.dynamic(() -> {
            UUID id = syncManager.getPlayer() == null ? null
                : syncManager.getPlayer()
                    .getUniqueID();
            return id == null ? "" : String.valueOf(IMPORT_FEEDBACK.get(id));
        })).pos(8, 92)
            .size(320, 70));
        InteractionSyncHandler closeBtn = new InteractionSyncHandler()
            .setOnMousePressed(mouse -> panelHandler.closePanel());
        edit.child(
            new ButtonWidget<>().pos(8, 148)
                .size(60, 16)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.gui.stock_monitor.close"))
                .syncHandler(closeBtn));
        return edit;
    }

    private static void runImport(PanelSyncManager pm, StockMonitorTerminal terminal) {
        EntityPlayer player = pm.getPlayer();
        if (player == null) return;
        String json = IMPORT_STAGED.get(player.getUniqueID());
        if (json == null || json.isEmpty()) {
            IMPORT_FEEDBACK.put(player.getUniqueID(), "先粘贴 JSON（单段 ≤28KB，客户端文本框里 Ctrl+V）");
            return;
        }
        if (json.length() > 28_000) {
            IMPORT_FEEDBACK.put(player.getUniqueID(), "超长（" + json.length() + " 字符 > 28000）：请拆分产线后再导入");
            return;
        }
        ProLineImporter.Result r = ProLineImporter.parse(json);
        StringBuilder feedback = new StringBuilder();
        if (r.line.steps.isEmpty()) {
            feedback.append("没有解析出任何步骤；");
        }
        if (!r.errors.isEmpty()) {
            feedback.append("未解析项 ")
                .append(r.errors.size())
                .append(" 条（首条：")
                .append(r.errors.get(0))
                .append("）；");
        }
        if (r.line.steps.isEmpty()) {
            IMPORT_FEEDBACK.put(player.getUniqueID(), feedback.toString());
            return;
        }
        TileEntity base = terminalBase(terminal);
        ProductionLine created = ProLineManager.createLine(
            player,
            r.line.name,
            terminalNetworkId(terminal),
            base == null ? 0 : base.getWorldObj().provider.dimensionId,
            base == null ? 0 : base.xCoord,
            base == null ? 0 : base.yCoord,
            base == null ? 0 : base.zCoord);
        if (created == null) {
            IMPORT_FEEDBACK.put(player.getUniqueID(), feedback + "建线失败（团队门/上限，见聊天与日志）");
            return;
        }
        int n = 0;
        for (ProductionStep s : r.line.steps) {
            ProductionStep copy = new ProductionStep();
            copy.name = s.name;
            copy.machineType = s.machineType;
            copy.branch = s.branch;
            copy.hierarchy = s.hierarchy;
            copy.inputs.addAll(s.inputs);
            copy.outputs.addAll(s.outputs);
            if (ProLineManager.appendStep(player, created.id, copy) != null) n++;
        }
        IMPORT_FEEDBACK.put(
            player.getUniqueID(),
            feedback + "已创建【" + created.name + "】并导入 " + n + "/" + r.line.steps.size() + " 步");
        MyMod.LOG.info("[AE2QoL] 产线导入：{} 步（未解析 {} 条）", n, r.errors.size());
    }

    // ===== 数据结构：行快照（值语义 equals，fix31 范式）=====

    /** 可滚动行列表容器（ListWidget 子类，避免通配符泛型链式推断困难——fix31 同款）。 */
    private static final class PlList extends ListWidget<IWidget, PlList> {

        PlList(int w, int h) {
            size(w, h);
        }
    }

    static final class LineRow {

        // 非终态：read() 侧按字段回填（值语义 equals 保证节流不误发）
        int index;
        String lineId;
        String name;
        int stepCount;
        int running;
        int bound;
        boolean pausedLine;
        String networkFlag;

        LineRow(int index, ProductionLine l) {
            this.index = index;
            this.lineId = l.id;
            this.name = l.name;
            this.stepCount = l.steps.size();
            int run = 0;
            int bnd = 0;
            for (ProductionStep s : l.steps) {
                int[] st = ProLineManager.runState(l.id, s.id);
                run += st == null ? 0 : st[0];
                bnd += st == null ? s.machines.size() : st[1];
            }
            this.running = run;
            this.bound = bnd;
            this.pausedLine = l.linePaused;
            this.networkFlag = l.effectiveNetworkId()
                .isEmpty() ? "口径：—（终端未连 AE）" : "口径：创建时终端（" + l.effectiveNetworkId() + "）";
        }

        static void write(PacketBuffer buf, LineRow r) throws IOException {
            buf.writeInt(r.index);
            ByteBufUtils.writeUTF8String(buf, r.lineId);
            ByteBufUtils.writeUTF8String(buf, r.name);
            buf.writeInt(r.stepCount);
            buf.writeInt(r.running);
            buf.writeInt(r.bound);
            buf.writeBoolean(r.pausedLine);
            ByteBufUtils.writeUTF8String(buf, r.networkFlag);
        }

        static LineRow read(PacketBuffer buf) throws IOException {
            LineRow r = new LineRow(buf.readInt(), new ProductionLine());
            r.lineId = ByteBufUtils.readUTF8String(buf);
            r.name = ByteBufUtils.readUTF8String(buf);
            r.stepCount = buf.readInt();
            r.running = buf.readInt();
            r.bound = buf.readInt();
            r.pausedLine = buf.readBoolean();
            r.networkFlag = ByteBufUtils.readUTF8String(buf);
            return r;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LineRow)) return false;
            LineRow r = (LineRow) o;
            return index == r.index && lineId.equals(r.lineId)
                && name.equals(r.name)
                && stepCount == r.stepCount
                && running == r.running
                && bound == r.bound
                && pausedLine == r.pausedLine;
        }

        @Override
        public int hashCode() {
            return (index * 31 + stepCount) * 31 + running;
        }
    }

    static final class StepRow {

        final int seq;
        final String lineId;
        final String stepId;
        final String name;
        final String type;
        final String runText;
        final String outText;
        final int badge;
        /** 主产物 icon（客户端渲染；服务端解析、快照下传）。 */
        final ItemStack icon;

        static StepRow of(int seq, ProductionLine l, ProductionStep s) {
            int[] st = ProLineManager.runState(l.id, s.id);
            int running = st == null ? 0 : st[0];
            int bound = st == null ? s.machines.size() : st[1];
            int badge = ProLineSummary.badgeOf(l, s, st);
            ItemRow main = s.mainOutput();
            String outText = main == null ? "无产物" : "×" + main.amount;
            return new StepRow(
                seq,
                l.id,
                s.id,
                s.name == null ? "?" : s.name,
                s.machineType == null ? "" : s.machineType,
                running + "/" + bound + " 在产",
                outText,
                badge,
                mainIcon(main));
        }

        private static ItemStack mainIcon(ItemRow main) {
            if (main == null || main.isFluid || main.key.isEmpty()) return null;
            int at = main.key.lastIndexOf('@');
            String id = at > 0 ? main.key.substring(0, at) : main.key;
            int meta = at > 0 ? Integer.parseInt(main.key.substring(at + 1)) : 0;
            Object item = net.minecraft.item.Item.itemRegistry.getObject(id);
            return item instanceof net.minecraft.item.Item ? new ItemStack((net.minecraft.item.Item) item, 1, meta)
                : null;
        }

        StepRow(int seq, String lineId, String stepId, String name, String type, String runText, String outText,
            int badge, ItemStack icon) {
            this.seq = seq;
            this.lineId = lineId;
            this.stepId = stepId;
            this.name = name;
            this.type = type;
            this.runText = runText;
            this.outText = outText;
            this.badge = badge;
            this.icon = icon;
        }

        static void write(PacketBuffer buf, StepRow r) throws IOException {
            buf.writeInt(r.seq);
            ByteBufUtils.writeUTF8String(buf, r.lineId);
            ByteBufUtils.writeUTF8String(buf, r.stepId);
            ByteBufUtils.writeUTF8String(buf, r.name);
            ByteBufUtils.writeUTF8String(buf, r.type);
            ByteBufUtils.writeUTF8String(buf, r.runText);
            ByteBufUtils.writeUTF8String(buf, r.outText);
            buf.writeInt(r.badge);
            ByteBufUtils.writeItemStack(buf, r.icon);
        }

        static StepRow read(PacketBuffer buf) throws IOException {
            return new StepRow(
                buf.readInt(),
                ByteBufUtils.readUTF8String(buf),
                ByteBufUtils.readUTF8String(buf),
                ByteBufUtils.readUTF8String(buf),
                ByteBufUtils.readUTF8String(buf),
                ByteBufUtils.readUTF8String(buf),
                ByteBufUtils.readUTF8String(buf),
                buf.readInt(),
                ByteBufUtils.readItemStack(buf));
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StepRow)) return false;
            StepRow r = (StepRow) o;
            return seq == r.seq && badge == r.badge
                && stepId.equals(r.stepId)
                && name.equals(r.name)
                && type.equals(r.type)
                && runText.equals(r.runText)
                && outText.equals(r.outText);
        }

        @Override
        public int hashCode() {
            return (seq * 31 + badge) * 31 + name.hashCode();
        }
    }

    static final class CandidateRow {

        final String label;
        final String tip;
        final boolean bound;
        final int dim;
        final int x;
        final int y;
        final int z;
        /** GT 多方块的 MTE id（= 机器号）：客户端用它组出物品栈本土化名与图标（A2：名称一律客户端解析）。 */
        final int mteId;

        CandidateRow(String label, String tip, boolean bound, int dim, int x, int y, int z, int mteId) {
            this.label = label;
            this.tip = tip;
            this.bound = bound;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.mteId = mteId;
        }

        static void write(PacketBuffer buf, CandidateRow r) throws IOException {
            ByteBufUtils.writeUTF8String(buf, r.label);
            ByteBufUtils.writeUTF8String(buf, r.tip);
            buf.writeBoolean(r.bound);
            buf.writeInt(r.dim);
            buf.writeInt(r.x);
            buf.writeInt(r.y);
            buf.writeInt(r.z);
            buf.writeInt(r.mteId);
        }

        static CandidateRow read(PacketBuffer buf) throws IOException {
            return new CandidateRow(
                ByteBufUtils.readUTF8String(buf),
                ByteBufUtils.readUTF8String(buf),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt());
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CandidateRow)) return false;
            CandidateRow r = (CandidateRow) o;
            return bound == r.bound && label.equals(r.label);
        }

        @Override
        public int hashCode() {
            return label.hashCode() * 31 + (bound ? 1 : 0);
        }
    }
}
