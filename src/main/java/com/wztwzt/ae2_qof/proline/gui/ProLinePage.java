package com.wztwzt.ae2_qof.proline.gui;

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
import com.wztwzt.ae2_qof.proline.ProLinePresets;
import com.wztwzt.ae2_qof.proline.ProLineSummary;
import com.wztwzt.ae2_qof.proline.ProductionLine;
import com.wztwzt.ae2_qof.proline.ProductionStep;
import com.wztwzt.ae2_qof.terminal.StockMonitorTerminal;

import cpw.mods.fml.common.network.ByteBufUtils;

/**
 * 「产线」页（库存统计终端第三页），布局照 wiki「配方一览」：分支 chip + 层级分段 + 步骤行 + 汇总。
 *
 * <h2>四条硬纪律（对应此前三个 P0 崩溃的根因）</h2>
 * <ol>
 * <li><b>buildUI 期不碰 {@code syncManager.getPlayer()}</b>：container 未建立会 NPE ⇒「终端右键打不开」。
 * 句柄在 buildPage 直接 syncedPanel 建好，getPlayer 只在 builder lambda / 点击回调里用。</li>
 * <li><b>DynamicSync 子树内 handler 必须 {@code getOrCreateSyncHandler(唯一键)}</b>：否则
 * IllegalStateException ⇒「点按钮被踢出 GUI」。统一走 {@link #click}。</li>
 * <li><b>行宽 ≤ 视口</b>：超出 1px 右侧按钮就被裁 ⇒「按钮基本都没有」。{@link #ROW_W}=LIST_W-8。</li>
 * <li><b>列表 getter 1 秒节流</b>：每 tick 调用，不节流会持续发包。</li>
 * </ol>
 */
public final class ProLinePage {

    // ===== 布局（终端 380×252，第三页 CONTENT）=====
    private static final int CONTENT_W = 336;
    private static final int PAGE_H = 220;
    private static final int LIST_W = CONTENT_W - 8; // 328 视口
    private static final int ROW_W = LIST_W - 8; // 320 行宽

    private static final int ROW_H = 22;
    private static final int ICON = 16;
    private static final int SEQ_W = 14;
    private static final int NAME_W = 148;
    private static final int VALUE_W = 82;
    private static final int BTN_W = 26;
    // 预算 16+14+148+82+26 + 2*5 间距 = 296 ≤ 320 ✓

    private static final int TOPBAR_H = 18;
    private static final int CHIP_H = 16;
    private static final int SUMM_H = 30;
    private static final int SEG_H = 13;
    private static final int LINE_LIST_H = 40;
    private static final int STEP_AREA_H = PAGE_H - TOPBAR_H - LINE_LIST_H - CHIP_H - SUMM_H - 10;

    private static final int PANEL_W2 = 300;
    private static final int PANEL_H2 = 150;
    private static final long REFRESH_MS = 1000L;
    private static final String ALL_BRANCHES = "\u0000ALL";

    // ===== per-player 会话状态 =====
    private static final Map<UUID, String> SELECTED_LINE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> SELECTED_STEP = new ConcurrentHashMap<>();
    private static final Map<UUID, String> BRANCH_FILTER = new ConcurrentHashMap<>();
    private static final Map<UUID, String> IMPORT_STAGED = new ConcurrentHashMap<>();
    private static final Map<UUID, String> IMPORT_FEEDBACK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DEL_ARM = new ConcurrentHashMap<>();
    private static final Map<UUID, String> DEL_ARM_LINE = new ConcurrentHashMap<>();

    private ProLinePage() {}

    /** 4.2.0：客户端名称解析器读当前选中产线。 */
    public static String getSelectedLineIdForResolve(UUID playerId) {
        return SELECTED_LINE.get(playerId);
    }

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
        String stepId = SELECTED_STEP.get(player == null ? null : player.getUniqueID());
        return line == null || stepId == null ? null : line.findStep(stepId);
    }

    // ============================================================
    // 页面装配
    // ============================================================

    public static IWidget buildPage(StockMonitorTerminal terminal, PanelSyncManager syncManager, EntityPlayer player) {
        try {
            final UUID pid = player == null ? null : player.getUniqueID();
            syncManager.addCloseListener(p -> {
                if (p == null || p.worldObj.isRemote) return;
                UUID id = p.getUniqueID();
                if (pid != null && !pid.equals(id)) return;
                SELECTED_LINE.remove(id);
                SELECTED_STEP.remove(id);
                BRANCH_FILTER.remove(id);
                IMPORT_STAGED.remove(id);
                IMPORT_FEEDBACK.remove(id);
                DEL_ARM.remove(id);
                DEL_ARM_LINE.remove(id);
            });
        } catch (Throwable ignored) {}

        // 面板句柄：buildUI 期创建（不触碰 getPlayer）
        IPanelHandler linePanel = syncManager.syncedPanel("proline_line_cfg", true, (pm, ph) -> buildLinePanel(pm, ph));
        IPanelHandler stepPanel = syncManager
            .syncedPanel("proline_step_ui", true, (pm, ph) -> buildStepPanel(pm, ph, terminal));
        IPanelHandler candPanel = syncManager
            .syncedPanel("proline_candidates", true, (pm, ph) -> buildCandidatesPanel(pm, ph));
        IPanelHandler presetPanel = syncManager
            .syncedPanel("proline_presets", true, (pm, ph) -> buildPresetsPanel(pm, ph, terminal));
        IPanelHandler importPanel = syncManager
            .syncedPanel("proline_import", true, (pm, ph) -> buildImportPanel(pm, ph, terminal));

        GenericListSyncHandler<LineRow> lines = GenericListSyncHandler.<LineRow>builder()
            .getter(cached(ProLinePage::collectLineRows))
            .serializer(LineRow::write)
            .deserializer(LineRow::read)
            .immutableCopy()
            .build();
        syncManager.syncValue("proline_lines", lines);

        GenericListSyncHandler<String> chips = GenericListSyncHandler.<String>builder()
            .getter(cached(() -> collectBranchChips(syncManager.getPlayer())))
            .serializer(ByteBufUtils::writeUTF8String)
            .deserializer(ByteBufUtils::readUTF8String)
            .immutableCopy()
            .build();
        syncManager.syncValue("proline_chips", chips);

        GenericListSyncHandler<StepRow> steps = GenericListSyncHandler.<StepRow>builder()
            .getter(cached(() -> collectStepRows(syncManager.getPlayer())))
            .serializer(StepRow::write)
            .deserializer(StepRow::read)
            .immutableCopy()
            .build();
        syncManager.syncValue("proline_steps", steps);

        GenericListSyncHandler<String> summary = GenericListSyncHandler.<String>builder()
            .getter(cached(() -> collectSummaryRows(syncManager.getPlayer())))
            .serializer(ByteBufUtils::writeUTF8String)
            .deserializer(ByteBufUtils::readUTF8String)
            .immutableCopy()
            .build();
        syncManager.syncValue("proline_summary", summary);

        Flow column = Flow.column()
            .size(CONTENT_W, PAGE_H)
            .childPadding(2);

        // ---- 顶栏 ----
        Flow bar = Flow.row()
            .size(LIST_W, TOPBAR_H)
            .childPadding(3)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        bar.child(
            new ButtonWidget<>().size(56, TOPBAR_H)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.new"))
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.proline.new")))
                .syncHandler(click(syncManager, "pl.new", pm -> {
                    TileEntity b = base(terminal);
                    ProLineManager.createLine(
                        pm.getPlayer(),
                        "产线 " + (System.currentTimeMillis() % 1000),
                        netId(terminal),
                        b == null ? 0 : b.getWorldObj().provider.dimensionId,
                        b == null ? 0 : b.xCoord,
                        b == null ? 0 : b.yCoord,
                        b == null ? 0 : b.zCoord);
                })));
        bar.child(
            new ButtonWidget<>().size(56, TOPBAR_H)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.presets"))
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.proline.presets.tip")))
                .syncHandler(click(syncManager, "pl.presets", pm -> presetPanel.openPanel())));
        bar.child(
            new ButtonWidget<>().size(56, TOPBAR_H)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.import"))
                .syncHandler(click(syncManager, "pl.import", pm -> importPanel.openPanel())));
        bar.child(
            new ButtonWidget<>().size(56, TOPBAR_H)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.btn_line"))
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.proline.btn_line_tip")))
                .syncHandler(click(syncManager, "pl.cfg", pm -> linePanel.openPanel())));
        bar.child(new TextWidget<>(IKey.dynamic(() -> {
            ProductionLine l = lineOf(syncManager.getPlayer());
            return l == null ? EnumChatFormatting.GRAY + StatCollector.translateToLocal("ae2_qof.proline.no_selection")
                : EnumChatFormatting.WHITE + "▸ " + l.name;
        })).size(LIST_W - 232, TOPBAR_H)
            .color(0xFFE0E0E0));
        column.child(bar);

        // ---- 产线列表 ----
        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, LINE_LIST_H)
                .initialChild(buildLineList(lines.getValue(), syncManager))
                .syncHandler(
                    new DynamicLinkedSyncHandler<>(lines)
                        .widgetProvider((dm, v) -> buildLineList(v.getValue(), syncManager))));

        // ---- 分支 chip ----
        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, CHIP_H)
                .initialChild(buildChipRow(chips.getValue(), syncManager))
                .syncHandler(
                    new DynamicLinkedSyncHandler<>(chips)
                        .widgetProvider((dm, v) -> buildChipRow(v.getValue(), syncManager))));

        // ---- 步骤区 ----
        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, STEP_AREA_H)
                .initialChild(buildStepArea(steps.getValue(), syncManager, stepPanel, candPanel))
                .syncHandler(
                    new DynamicLinkedSyncHandler<>(steps)
                        .widgetProvider((dm, v) -> buildStepArea(v.getValue(), syncManager, stepPanel, candPanel))));

        // ---- 汇总 ----
        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, SUMM_H)
                .initialChild(buildSummary(summary.getValue()))
                .syncHandler(
                    new DynamicLinkedSyncHandler<>(summary).widgetProvider((dm, v) -> buildSummary(v.getValue()))));

        if (player != null && player.worldObj.isRemote) {
            triggerClientResolve(
                player.getUniqueID()
                    .toString());
        }
        return column;
    }

    /** 4.2.0：客户端名称解析触发（@SideOnly 隔离，server 不加载 Minecraft 类）。 */
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private static void triggerClientResolve(String playerId) {
        new Thread(() -> {
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                return;
            }
            try {
                com.wztwzt.ae2_qof.proline.client.ProLineNameResolver.resolveAndSend(playerId, null);
            } catch (Throwable ignored) {}
        }, "proline-resolve").start();
    }

    // ============================================================
    // 交互 handler（唯一合法注册方式）
    // ============================================================

    private static InteractionSyncHandler click(final PanelSyncManager sm, String key,
        java.util.function.Consumer<PanelSyncManager> action) {
        return sm.getOrCreateSyncHandler(
            key,
            InteractionSyncHandler.class,
            () -> new InteractionSyncHandler().setOnMousePressed(mouse -> action.accept(sm)));
    }

    private static <T> java.util.function.Supplier<List<T>> cached(java.util.function.Supplier<List<T>> compute) {
        return new java.util.function.Supplier<List<T>>() {

            private long last = -REFRESH_MS;
            private List<T> value = java.util.Collections.emptyList();

            @Override
            public List<T> get() {
                long now = System.currentTimeMillis();
                if (now - last < REFRESH_MS) return value;
                last = now;
                try {
                    List<T> fresh = compute.get();
                    value = fresh == null ? java.util.Collections.<T>emptyList() : fresh;
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 产线页数据收集异常", t);
                }
                return value;
            }
        };
    }

    private static TileEntity base(StockMonitorTerminal t) {
        try {
            return (TileEntity) t.getBaseMetaTileEntity();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String netId(StockMonitorTerminal t) {
        try {
            return t.getNetworkId();
        } catch (Throwable e) {
            return "";
        }
    }

    // ============================================================
    // 数据收集（服务端）
    // ============================================================

    private static List<LineRow> collectLineRows() {
        List<LineRow> out = new ArrayList<>();
        WorldServer w = ProLineData.anyLoadedServerWorld();
        if (w == null) return out;
        for (ProductionLine l : ProLineData.get(w).lines) out.add(LineRow.of(l));
        return out;
    }

    private static List<String> collectBranchChips(EntityPlayer player) {
        List<String> out = new ArrayList<>();
        out.add(ALL_BRANCHES);
        ProductionLine line = lineOf(player);
        if (line == null) return out;
        for (ProductionStep s : line.steps) {
            String b = s.branch == null ? "" : s.branch.trim();
            if (!b.isEmpty() && !out.contains(b)) out.add(b);
        }
        return out;
    }

    private static List<StepRow> collectStepRows(EntityPlayer player) {
        List<StepRow> out = new ArrayList<>();
        ProductionLine line = lineOf(player);
        if (line == null) return out;
        String filter = player == null ? null : BRANCH_FILTER.get(player.getUniqueID());
        boolean all = filter == null || ALL_BRANCHES.equals(filter);
        String lastHier = null;
        int seq = 0;
        for (ProductionStep s : line.steps) {
            if (!all && !filter.equals(s.branch == null ? "" : s.branch.trim())) continue;
            seq++;
            String hier = s.hierarchy == null ? "" : s.hierarchy.trim();
            if (!hier.equals(lastHier)) {
                out.add(
                    StepRow
                        .segment(hier.isEmpty() ? StatCollector.translateToLocal("ae2_qof.proline.hier.main") : hier));
                lastHier = hier;
            }
            out.add(StepRow.of(seq, line, s));
        }
        return out;
    }

    private static List<String> collectSummaryRows(EntityPlayer player) {
        List<String> out = new ArrayList<>();
        ProductionLine line = lineOf(player);
        if (line == null) return out;
        out.add(
            EnumChatFormatting.GRAY + line.name
                + (line.linePaused
                    ? EnumChatFormatting.DARK_PURPLE + "〔"
                        + StatCollector.translateToLocal("ae2_qof.proline.line_paused")
                        + "〕"
                    : "")
                + (line.effectiveNetworkId()
                    .isEmpty()
                        ? EnumChatFormatting.RED + "〔"
                            + StatCollector.translateToLocal("ae2_qof.proline.scope_missing")
                            + "〕"
                        : ""));
        for (String key : ProLineSummary.externalInputs(line)) out.add(sumRow("in", key, line));
        for (String key : ProLineSummary.externalOutputs(line)) out.add(sumRow("out", key, line));
        return out;
    }

    private static String sumRow(String side, String key, ProductionLine line) {
        BigInteger[] twin = line.cumulative.get(key);
        BigInteger cum = twin == null ? BigInteger.ZERO : twin["in".equals(side) ? 0 : 1];
        Double rate = line.samples.get(key) == null ? null
            : line.samples.get(key)
                .recentRatePerSec();
        long perHour = rate == null || rate.isNaN() ? 0L : (long) (rate * 3600.0);
        return EnumChatFormatting.WHITE + StatCollector.translateToLocal("ae2_qof.proline.sum." + side)
            + " "
            + displayKey(key)
            + EnumChatFormatting.GRAY
            + " "
            + StatCollector.translateToLocal("ae2_qof.proline.sum.cum")
            + " "
            + ProLineSummary.kmg(cum)
            + (rate == null ? ""
                : EnumChatFormatting.DARK_AQUA + " ≈" + ProLineSummary.kmg(BigInteger.valueOf(perHour)) + "/h");
    }

    private static String displayKey(String key) {
        if (key == null) return "?";
        if (key.startsWith("fluid:")) return "(流体)" + key.substring(6);
        int at = key.lastIndexOf('@');
        return at > 0 ? key.substring(0, at) : key;
    }

    // ============================================================
    // 列表构建
    // ============================================================

    private static IWidget buildLineList(List<LineRow> rows, PanelSyncManager sm) {
        RowList list = new RowList(LINE_LIST_H);
        if (rows == null || rows.isEmpty()) {
            list.child(
                new TextWidget<>(IKey.lang("ae2_qof.proline.no_lines")).size(ROW_W, 12)
                    .color(0xFFCCCCCC));
            return list;
        }
        for (LineRow r : rows) {
            String label = (r.paused ? EnumChatFormatting.DARK_PURPLE + "⏸ " : EnumChatFormatting.GREEN + "▸ ")
                + EnumChatFormatting.WHITE
                + r.name
                + EnumChatFormatting.GRAY
                + "  "
                + r.stepCount
                + "步 "
                + r.running
                + "/"
                + r.bound;
            list.child(
                new ButtonWidget<>().size(ROW_W, ROW_H)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.str(label))
                    .tooltip(
                        t -> t.addLine(IKey.str(r.name))
                            .addLine(IKey.str(r.networkFlag)))
                    .syncHandler(click(sm, "pl.line." + r.lineId, pm -> {
                        SELECTED_LINE.put(
                            pm.getPlayer()
                                .getUniqueID(),
                            r.lineId);
                        BRANCH_FILTER.remove(
                            pm.getPlayer()
                                .getUniqueID());
                        ProLineManager.touchActive(r.lineId);
                    })));
        }
        return list;
    }

    private static IWidget buildChipRow(List<String> chips, PanelSyncManager sm) {
        Flow row = Flow.row()
            .size(LIST_W, CHIP_H)
            .childPadding(2)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        if (chips == null || chips.isEmpty()) return row;
        for (String chip : chips) {
            boolean isAll = ALL_BRANCHES.equals(chip);
            String label = isAll ? StatCollector.translateToLocal("ae2_qof.proline.branch.all") : chip;
            row.child(
                new ButtonWidget<>().size(Math.min(100, 10 + label.length() * 7), CHIP_H)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.str(label))
                    .tooltip(t -> t.addLine(IKey.str(label)))
                    .syncHandler(click(sm, "pl.chip." + (isAll ? "ALL" : chip), pm -> {
                        UUID id = pm.getPlayer()
                            .getUniqueID();
                        if (isAll) BRANCH_FILTER.remove(id);
                        else BRANCH_FILTER.put(id, chip);
                    })));
        }
        return row;
    }

    private static IWidget buildStepArea(List<StepRow> rows, PanelSyncManager sm, IPanelHandler stepPanel,
        IPanelHandler candPanel) {
        RowList list = new RowList(STEP_AREA_H);
        if (rows == null || rows.isEmpty()) {
            list.child(
                new TextWidget<>(IKey.lang("ae2_qof.proline.select_line_first")).size(ROW_W, 12)
                    .color(0xFFCCCCCC));
            return list;
        }
        for (StepRow r : rows) {
            if (r.segment) {
                list.child(
                    new TextWidget<>(IKey.str(EnumChatFormatting.AQUA + "▸ " + EnumChatFormatting.WHITE + r.name))
                        .size(ROW_W, SEG_H)
                        .color(0xFFE0E0E0));
                continue;
            }
            list.child(stepRow(r, sm, stepPanel, candPanel));
        }
        return list;
    }

    private static IWidget stepRow(StepRow r, PanelSyncManager sm, IPanelHandler stepPanel, IPanelHandler candPanel) {
        String badge = r.badge == -1 ? EnumChatFormatting.DARK_PURPLE + "⏸"
            : r.badge == 2 ? EnumChatFormatting.GREEN + "●"
                : r.badge == 1 ? EnumChatFormatting.YELLOW + "◐" : EnumChatFormatting.DARK_GRAY + "○";
        Flow row = Flow.row()
            .size(ROW_W, ROW_H)
            .childPadding(2)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        row.child(icon(r.icon));
        row.child(
            new TextWidget<>(IKey.str(EnumChatFormatting.DARK_GRAY + String.valueOf(r.seq))).size(SEQ_W, ROW_H)
                .color(0xFF999999));
        row.child(
            new ButtonWidget<>().size(NAME_W, ROW_H)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.str(r.name))
                .tooltip(
                    t -> t.addLine(IKey.str(r.name))
                        .addLine(IKey.str(StatCollector.translateToLocal("ae2_qof.proline.step.type") + r.type))
                        .addLine(IKey.str(StatCollector.translateToLocal("ae2_qof.proline.step.out") + r.outText))
                        .addLine(IKey.str(StatCollector.translateToLocal("ae2_qof.proline.step.branch") + r.branch)))
                .syncHandler(click(sm, "pl.step.open." + r.stepId, pm -> {
                    SELECTED_STEP.put(
                        pm.getPlayer()
                            .getUniqueID(),
                        r.stepId);
                    stepPanel.openPanel();
                })));
        row.child(
            new ButtonWidget<>().size(VALUE_W, ROW_H)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(
                    IKey.str(
                        badge + " " + EnumChatFormatting.WHITE + r.runText + EnumChatFormatting.GRAY + " " + r.outText))
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.proline.step.bind_tip")))
                .syncHandler(click(sm, "pl.step.cand." + r.stepId, pm -> {
                    SELECTED_STEP.put(
                        pm.getPlayer()
                            .getUniqueID(),
                        r.stepId);
                    candPanel.openPanel();
                })));
        row.child(
            new ButtonWidget<>().size(BTN_W, ROW_H)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.str(EnumChatFormatting.RED + "✕"))
                .tooltip(t -> t.addLine(IKey.lang("ae2_qof.proline.del")))
                .syncHandler(
                    click(
                        sm,
                        "pl.step.del." + r.stepId,
                        pm -> ProLineManager.removeStep(pm.getPlayer(), r.lineId, r.stepId))));
        return row;
    }

    private static IWidget buildSummary(List<String> rows) {
        RowList list = new RowList(SUMM_H);
        if (rows != null) {
            for (String s : rows) {
                list.child(
                    new TextWidget<>(IKey.str(s)).size(ROW_W, 10)
                        .color(0xFFE0E0E0));
            }
        }
        return list;
    }

    private static IWidget icon(ItemStack item) {
        if (item != null && item.getItem() != null) {
            return new ItemDrawable(item).asWidget()
                .size(ICON, ICON);
        }
        Widget<?> empty = new Widget<>().size(ICON, ICON);
        return empty;
    }

    // ============================================================
    // 子面板
    // ============================================================

    private static ModularPanel buildLinePanel(PanelSyncManager sm, IPanelHandler ph) {
        ModularPanel p = ModularPanel.defaultPanel("proline_line_cfg", PANEL_W2, PANEL_H2);
        StringSyncValue name = new StringSyncValue(() -> {
            ProductionLine l = lineOf(sm.getPlayer());
            return l == null ? "" : l.name;
        }, v -> {
            ProductionLine l = lineOf(sm.getPlayer());
            if (l != null) ProLineManager.renameLine(sm.getPlayer(), l.id, v);
        }).allowC2S();
        sm.syncValue("pl.cfg.name", name);
        BooleanSyncValue pause = new BooleanSyncValue(() -> {
            ProductionLine l = lineOf(sm.getPlayer());
            return l != null && l.linePaused;
        }, v -> {
            ProductionLine l = lineOf(sm.getPlayer());
            if (l != null) ProLineManager.setLinePaused(sm.getPlayer(), l.id, v);
        }).allowC2S();
        sm.syncValue("pl.cfg.pause", pause);

        p.child(
            new TextWidget<>(IKey.lang("ae2_qof.proline.btn_line")).pos(10, 8)
                .size(PANEL_W2 - 20, 12)
                .color(0xFFE0E0E0));
        p.child(
            new TextWidget<>(IKey.lang("ae2_qof.proline.name")).pos(10, 26)
                .size(50, 12));
        p.child(
            new TextFieldWidget().pos(62, 24)
                .size(PANEL_W2 - 72, 16)
                .value(name));
        p.child(
            new ButtonWidget<>().pos(10, 48)
                .size(120, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.btn_pause_line"))
                .syncHandler(click(sm, "pl.cfg.toggle", pm -> pause.setValue(!pause.getValue()))));
        p.child(
            new ButtonWidget<>().pos(136, 48)
                .size(150, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.btn_del_line"))
                .syncHandler(click(sm, "pl.cfg.del", pm -> {
                    ProductionLine l = lineOf(pm.getPlayer());
                    if (l == null) return;
                    UUID id = pm.getPlayer()
                        .getUniqueID();
                    long now = System.currentTimeMillis();
                    Long at = DEL_ARM.get(id);
                    boolean armed = at != null && now - at < 3000L && l.id.equals(DEL_ARM_LINE.get(id));
                    if (armed) {
                        DEL_ARM.remove(id);
                        DEL_ARM_LINE.remove(id);
                        ProLineManager.deleteLine(pm.getPlayer(), l.id, true);
                    } else {
                        DEL_ARM.put(id, now);
                        DEL_ARM_LINE.put(id, l.id);
                        pm.getPlayer()
                            .addChatMessage(
                                new net.minecraft.util.ChatComponentText(
                                    EnumChatFormatting.YELLOW
                                        + StatCollector.translateToLocal("ae2_qof.proline.del_confirm")));
                    }
                })));
        p.child(closeButton(sm, ph, 10, 120));
        return p;
    }

    private static ModularPanel buildStepPanel(PanelSyncManager sm, IPanelHandler ph, StockMonitorTerminal terminal) {
        ModularPanel p = ModularPanel.defaultPanel("proline_step_ui", PANEL_W2, PANEL_H2);
        BooleanSyncValue paused = new BooleanSyncValue(() -> {
            ProductionStep s = stepOf(sm.getPlayer());
            ProductionLine l = lineOf(sm.getPlayer());
            return l != null && s != null && l.pausedStepIds.contains(s.id);
        }, v -> {
            ProductionStep s = stepOf(sm.getPlayer());
            ProductionLine l = lineOf(sm.getPlayer());
            if (l != null && s != null) ProLineManager.setStepPaused(sm.getPlayer(), l.id, s.id, v);
        }).allowC2S();
        sm.syncValue("pl.step.paused", paused);

        p.child(new TextWidget<>(IKey.dynamic(() -> {
            ProductionLine l = lineOf(sm.getPlayer());
            ProductionStep s = stepOf(sm.getPlayer());
            if (l == null || s == null)
                return EnumChatFormatting.RED + StatCollector.translateToLocal("ae2_qof.proline.none_selected");
            return EnumChatFormatting.WHITE + l.name
                + EnumChatFormatting.GRAY
                + " ▸ "
                + EnumChatFormatting.WHITE
                + s.name;
        })).pos(10, 8)
            .size(PANEL_W2 - 20, 24)
            .color(0xFFE0E0E0));

        p.child(
            new ButtonWidget<>().pos(10, 36)
                .size(130, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.btn_pause_toggle"))
                .syncHandler(click(sm, "pl.step.toggle", pm -> paused.setValue(!paused.getValue()))));
        p.child(
            new ButtonWidget<>().pos(146, 36)
                .size(140, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.btn_write_card"))
                .syncHandler(click(sm, "pl.step.card", pm -> {
                    ProductionLine l = lineOf(pm.getPlayer());
                    ProductionStep s = stepOf(pm.getPlayer());
                    if (l != null && s != null) {
                        ItemStack hand = pm.getPlayer()
                            .getHeldItem();
                        ItemProductionLineCard.programCard(hand, l.id, s.id);
                    }
                })));
        p.child(
            new ButtonWidget<>().pos(10, 60)
                .size(70, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.terminal.highlight"))
                .syncHandler(click(sm, "pl.step.hl", pm -> sendAction(terminal, pm, 0))));
        p.child(
            new ButtonWidget<>().pos(86, 60)
                .size(70, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.terminal.teleport"))
                .syncHandler(click(sm, "pl.step.tp", pm -> sendAction(terminal, pm, 1))));
        p.child(closeButton(sm, ph, 10, 120));
        return p;
    }

    private static void sendAction(StockMonitorTerminal terminal, PanelSyncManager pm, int action) {
        try {
            ProductionLine l = lineOf(pm.getPlayer());
            ProductionStep s = stepOf(pm.getPlayer());
            if (l == null || s == null || s.machines.isEmpty()) return;
            MachineRef m = s.machines.get(0);
            TileEntity b = base(terminal);
            if (b == null) return;
            com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL.sendToServer(
                new com.wztwzt.ae2_qof.network.StockMonitorActionPacket(
                    action,
                    com.wztwzt.ae2_qof.network.StockMonitorActionPacket.KIND_EMITTER,
                    b.getWorldObj().provider.dimensionId,
                    b.xCoord,
                    b.yCoord,
                    b.zCoord,
                    m.dim,
                    m.x,
                    m.y,
                    m.z));
        } catch (Throwable ignored) {}
    }

    private static ModularPanel buildCandidatesPanel(PanelSyncManager sm, IPanelHandler ph) {
        ModularPanel p = ModularPanel.defaultPanel("proline_candidates", 320, 200);
        GenericListSyncHandler<CandRow> rows = GenericListSyncHandler.<CandRow>builder()
            .getter(cached(() -> scanCandidates(sm)))
            .serializer(CandRow::write)
            .deserializer(CandRow::read)
            .immutableCopy()
            .build();
        p.child(
            new TextWidget<>(IKey.lang("ae2_qof.proline.candidates.title")).pos(8, 6)
                .size(304, 12)
                .color(0xFFE0E0E0));
        p.child(
            new DynamicSyncedWidget<>().size(304, 150)
                .initialChild(buildCandList(rows.getValue(), sm))
                .syncHandler(
                    new DynamicLinkedSyncHandler<>(rows).widgetProvider((dm, v) -> buildCandList(v.getValue(), sm))));
        p.child(closeButton(sm, ph, 8, 168));
        return p;
    }

    private static IWidget buildCandList(List<CandRow> rows, PanelSyncManager sm) {
        RowList list = new RowList(150);
        if (rows == null || rows.isEmpty()) {
            list.child(
                new TextWidget<>(IKey.lang("ae2_qof.proline.candidates.empty")).size(296, 12)
                    .color(0xFFCCCCCC));
            return list;
        }
        for (CandRow r : rows) {
            String label = (r.bound ? EnumChatFormatting.AQUA + "✔ " : EnumChatFormatting.GRAY + "· ")
                + EnumChatFormatting.WHITE
                + r.label
                + EnumChatFormatting.DARK_GRAY
                + "  D"
                + r.dim
                + " ["
                + r.x
                + ","
                + r.y
                + ","
                + r.z
                + "]";
            list.child(
                new ButtonWidget<>().size(296, 20)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.str(label))
                    .tooltip(
                        t -> t.addLine(IKey.str(r.label))
                            .addLine(IKey.lang("ae2_qof.proline.candidates.click")))
                    .syncHandler(click(sm, "pl.cand." + r.dim + "." + r.x + "." + r.y + "." + r.z, pm -> {
                        ProductionLine l = lineOf(pm.getPlayer());
                        ProductionStep s = stepOf(pm.getPlayer());
                        if (l == null || s == null) return;
                        MachineRef ref = new MachineRef(r.dim, r.x, r.y, r.z, System.currentTimeMillis());
                        if (r.bound) ProLineManager.unbindMachine(pm.getPlayer(), l.id, s.id, ref);
                        else ProLineManager.bindMachine(pm.getPlayer(), l.id, s.id, ref);
                    })));
        }
        return list;
    }

    private static List<CandRow> scanCandidates(PanelSyncManager sm) {
        List<CandRow> out = new ArrayList<>();
        EntityPlayer player = sm.getPlayer();
        if (player == null || player.worldObj.isRemote || !ProLineGate.isPresent()) return out;
        Object myTeam = ProLineGate.teamOfPlayer(player);
        if (myTeam == null) return out;
        WorldServer any = ProLineData.anyLoadedServerWorld();
        ProLineData data = any == null ? null : ProLineData.get(any);
        if (data == null) return out;
        long t0 = System.nanoTime();
        int n = 0;
        try {
            for (serverutils.lib.math.ChunkDimPos pos : new ArrayList<>(
                serverutils.data.ClaimedChunks.instance.getAllClaimedPositions())) {
                if (System.nanoTime() - t0 > 50_000_000L) break;
                if (ProLineGate.teamOfChunk(pos.dim, pos.posX, pos.posZ) != myTeam) continue;
                WorldServer world = net.minecraftforge.common.DimensionManager.getWorld(pos.dim);
                if (world == null) continue;
                net.minecraft.world.chunk.Chunk chunk = world.getChunkFromChunkCoords(pos.posX, pos.posZ);
                for (Object teo : new ArrayList<Object>(chunk.chunkTileEntityMap.values())) {
                    if (!(teo instanceof TileEntity)) continue;
                    TileEntity te = (TileEntity) teo;
                    if (!(te instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity)) continue;
                    gregtech.api.interfaces.metatileentity.IMetaTileEntity mte = ((gregtech.api.interfaces.tileentity.IGregTechTileEntity) te)
                        .getMetaTileEntity();
                    if (!(mte instanceof gregtech.api.metatileentity.implementations.MTEMultiBlockBase)) continue;
                    if (n++ > 64) break;
                    MachineRef probe = new MachineRef(pos.dim, te.xCoord, te.yCoord, te.zCoord, 0L);
                    boolean bound = boundAnywhere(data, probe);
                    out.add(
                        new CandRow(
                            "MTE " + mte.getBaseMetaTileEntity()
                                .getMetaTileID(),
                            bound,
                            pos.dim,
                            te.xCoord,
                            te.yCoord,
                            te.zCoord));
                }
                if (n > 64) break;
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 候选扫描异常", t);
        }
        return out;
    }

    private static boolean boundAnywhere(ProLineData data, MachineRef ref) {
        for (ProductionLine l : data.lines) {
            for (ProductionStep s : l.steps) if (s.machines.contains(ref)) return true;
        }
        return false;
    }

    private static ModularPanel buildPresetsPanel(PanelSyncManager sm, IPanelHandler ph,
        StockMonitorTerminal terminal) {
        ModularPanel p = ModularPanel.defaultPanel("proline_presets", 320, 200);
        GenericListSyncHandler<String> rows = GenericListSyncHandler.<String>builder()
            .getter(cached(() -> {
                List<String> out = new ArrayList<>();
                for (ProLinePresets.Preset x : ProLinePresets.all()) out.add(x.name);
                return out;
            }))
            .serializer(ByteBufUtils::writeUTF8String)
            .deserializer(ByteBufUtils::readUTF8String)
            .immutableCopy()
            .build();
        p.child(
            new TextWidget<>(IKey.lang("ae2_qof.proline.presets.title")).pos(8, 6)
                .size(304, 12)
                .color(0xFFE0E0E0));
        p.child(
            new DynamicSyncedWidget<>().size(304, 150)
                .initialChild(buildPresetList(rows.getValue(), sm, terminal))
                .syncHandler(
                    new DynamicLinkedSyncHandler<>(rows)
                        .widgetProvider((dm, v) -> buildPresetList(v.getValue(), sm, terminal))));
        p.child(closeButton(sm, ph, 8, 168));
        return p;
    }

    private static IWidget buildPresetList(List<String> rows, PanelSyncManager sm, StockMonitorTerminal terminal) {
        RowList list = new RowList(150);
        if (rows == null || rows.isEmpty()) {
            list.child(
                new TextWidget<>(IKey.lang("ae2_qof.proline.presets.empty")).size(296, 12)
                    .color(0xFFCCCCCC));
            return list;
        }
        for (String name : rows) {
            list.child(
                new ButtonWidget<>().size(296, 20)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.str(EnumChatFormatting.WHITE + name))
                    .tooltip(t -> t.addLine(IKey.lang("ae2_qof.proline.presets.row_tip")))
                    .syncHandler(click(sm, "pl.preset." + name, pm -> {
                        TileEntity b = base(terminal);
                        ProLinePresets.create(
                            pm.getPlayer(),
                            name,
                            netId(terminal),
                            b == null ? 0 : b.getWorldObj().provider.dimensionId,
                            b == null ? 0 : b.xCoord,
                            b == null ? 0 : b.yCoord,
                            b == null ? 0 : b.zCoord);
                    })));
        }
        return list;
    }

    private static ModularPanel buildImportPanel(PanelSyncManager sm, IPanelHandler ph, StockMonitorTerminal terminal) {
        ModularPanel p = ModularPanel.defaultPanel("proline_import", 340, 170);
        StringSyncValue json = new StringSyncValue(() -> "", v -> {
            UUID id = sm.getPlayer() == null ? null
                : sm.getPlayer()
                    .getUniqueID();
            if (id != null) IMPORT_STAGED.put(id, v);
        }).allowC2S();
        sm.syncValue("pl.import.json", json);
        p.child(
            new TextWidget<>(IKey.lang("ae2_qof.proline.import.title")).pos(8, 6)
                .size(324, 12)
                .color(0xFFE0E0E0));
        p.child(
            new TextFieldWidget().pos(8, 22)
                .size(324, 40)
                .value(json));
        p.child(
            new ButtonWidget<>().pos(8, 68)
                .size(90, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.proline.import.go"))
                .syncHandler(click(sm, "pl.import.run", pm -> runImport(pm, terminal))));
        p.child(new TextWidget<>(IKey.dynamic(() -> {
            UUID id = sm.getPlayer() == null ? null
                : sm.getPlayer()
                    .getUniqueID();
            String f = id == null ? null : IMPORT_FEEDBACK.get(id);
            return f == null ? "" : f;
        })).pos(8, 92)
            .size(324, 60)
            .color(0xFFE0E0E0));
        p.child(closeButton(sm, ph, 8, 148));
        return p;
    }

    private static void runImport(PanelSyncManager pm, StockMonitorTerminal terminal) {
        EntityPlayer player = pm.getPlayer();
        if (player == null) return;
        String json = IMPORT_STAGED.get(player.getUniqueID());
        if (json == null || json.isEmpty()) {
            IMPORT_FEEDBACK.put(player.getUniqueID(), StatCollector.translateToLocal("ae2_qof.proline.import.empty"));
            return;
        }
        if (json.length() > 28_000) {
            IMPORT_FEEDBACK.put(
                player.getUniqueID(),
                StatCollector.translateToLocalFormatted("ae2_qof.proline.import.toolong", json.length()));
            return;
        }
        ProLineImporter.Result r = ProLineImporter.parse(json);
        if (r.line.steps.isEmpty()) {
            IMPORT_FEEDBACK.put(
                player.getUniqueID(),
                StatCollector.translateToLocal("ae2_qof.proline.import.nostep")
                    + (r.errors.isEmpty() ? "" : " / " + r.errors.get(0)));
            return;
        }
        TileEntity b = base(terminal);
        ProductionLine created = ProLineManager.createLine(
            player,
            r.line.name,
            netId(terminal),
            b == null ? 0 : b.getWorldObj().provider.dimensionId,
            b == null ? 0 : b.xCoord,
            b == null ? 0 : b.yCoord,
            b == null ? 0 : b.zCoord);
        if (created == null) {
            IMPORT_FEEDBACK.put(player.getUniqueID(), StatCollector.translateToLocal("ae2_qof.proline.import.fail"));
            return;
        }
        int n = 0;
        for (ProductionStep src : r.line.steps) {
            ProductionStep c = new ProductionStep();
            c.name = src.name;
            c.machineType = src.machineType;
            c.branch = src.branch;
            c.hierarchy = src.hierarchy;
            c.inputs.addAll(src.inputs);
            c.outputs.addAll(src.outputs);
            if (ProLineManager.appendStep(player, created.id, c) != null) n++;
        }
        IMPORT_FEEDBACK.put(
            player.getUniqueID(),
            StatCollector.translateToLocalFormatted("ae2_qof.proline.import.ok", created.name, n));
        MyMod.LOG.info("[AE2QoL] 产线导入：{} 步 / {} 未解析", n, r.errors.size());
    }

    private static IWidget closeButton(PanelSyncManager sm, IPanelHandler ph, int x, int y) {
        return new ButtonWidget<>().pos(x, y)
            .size(60, 18)
            .background(GuiTextures.BUTTON_CLEAN)
            .overlay(IKey.lang("ae2_qof.gui.stock_monitor.close"))
            .syncHandler(click(sm, "pl.close." + x + "." + y, pm -> ph.closePanel()));
    }

    // ============================================================
    // 行快照
    // ============================================================

    private static final class RowList extends ListWidget<IWidget, RowList> {

        RowList(int h) {
            size(LIST_W - 8, h);
        }
    }

    static final class LineRow {

        String lineId;
        String name;
        int stepCount;
        int running;
        int bound;
        boolean paused;
        String networkFlag;

        static LineRow of(ProductionLine l) {
            LineRow r = new LineRow();
            r.lineId = l.id;
            r.name = l.name;
            r.stepCount = l.steps.size();
            int run = 0, bnd = 0;
            for (ProductionStep s : l.steps) {
                int[] st = ProLineManager.runState(l.id, s.id);
                run += st == null ? 0 : st[0];
                bnd += st == null ? s.machines.size() : st[1];
            }
            r.running = run;
            r.bound = bnd;
            r.paused = l.linePaused;
            r.networkFlag = l.effectiveNetworkId()
                .isEmpty() ? StatCollector.translateToLocal("ae2_qof.proline.scope_missing")
                    : "AE " + l.effectiveNetworkId();
            return r;
        }

        static void write(PacketBuffer b, LineRow r) throws java.io.IOException {
            ByteBufUtils.writeUTF8String(b, r.lineId);
            ByteBufUtils.writeUTF8String(b, r.name);
            b.writeInt(r.stepCount);
            b.writeInt(r.running);
            b.writeInt(r.bound);
            b.writeBoolean(r.paused);
            ByteBufUtils.writeUTF8String(b, r.networkFlag);
        }

        static LineRow read(PacketBuffer b) throws java.io.IOException {
            LineRow r = new LineRow();
            r.lineId = ByteBufUtils.readUTF8String(b);
            r.name = ByteBufUtils.readUTF8String(b);
            r.stepCount = b.readInt();
            r.running = b.readInt();
            r.bound = b.readInt();
            r.paused = b.readBoolean();
            r.networkFlag = ByteBufUtils.readUTF8String(b);
            return r;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LineRow)) return false;
            LineRow x = (LineRow) o;
            return stepCount == x.stepCount && running == x.running
                && bound == x.bound
                && paused == x.paused
                && lineId.equals(x.lineId)
                && name.equals(x.name);
        }

        @Override
        public int hashCode() {
            return (lineId.hashCode() * 31 + running) * 31 + stepCount;
        }
    }

    static final class StepRow {

        boolean segment;
        int seq;
        String lineId;
        String stepId;
        String name;
        String type;
        String branch;
        String runText;
        String outText;
        int badge;
        ItemStack icon;

        static StepRow segment(String title) {
            StepRow r = new StepRow();
            r.segment = true;
            r.name = title;
            return r;
        }

        static StepRow of(int seq, ProductionLine l, ProductionStep s) {
            StepRow r = new StepRow();
            r.seq = seq;
            r.lineId = l.id;
            r.stepId = s.id;
            r.name = s.name == null ? "?" : s.name;
            r.type = s.machineType == null ? "" : s.machineType;
            r.branch = s.branch == null || s.branch.trim()
                .isEmpty() ? "-" : s.branch;
            int[] st = ProLineManager.runState(l.id, s.id);
            int running = st == null ? 0 : st[0];
            int bound = st == null ? s.machines.size() : st[1];
            r.badge = ProLineSummary.badgeOf(l, s, st);
            r.runText = running + "/" + bound;
            ItemRow main = s.mainOutput();
            r.outText = main == null ? "-" : "×" + main.amount;
            r.icon = mainIcon(main);
            return r;
        }

        private static ItemStack mainIcon(ItemRow main) {
            if (main == null || main.isFluid || main.key == null || main.key.isEmpty()) return null;
            int at = main.key.lastIndexOf('@');
            String id = at > 0 ? main.key.substring(0, at) : main.key;
            int meta = 0;
            try {
                meta = at > 0 ? Integer.parseInt(main.key.substring(at + 1)) : 0;
            } catch (NumberFormatException ignored) {}
            Object item = net.minecraft.item.Item.itemRegistry.getObject(id);
            return item instanceof net.minecraft.item.Item ? new ItemStack((net.minecraft.item.Item) item, 1, meta)
                : null;
        }

        static void write(PacketBuffer b, StepRow r) throws java.io.IOException {
            b.writeBoolean(r.segment);
            if (r.segment) {
                ByteBufUtils.writeUTF8String(b, r.name);
                return;
            }
            b.writeInt(r.seq);
            ByteBufUtils.writeUTF8String(b, r.lineId);
            ByteBufUtils.writeUTF8String(b, r.stepId);
            ByteBufUtils.writeUTF8String(b, r.name);
            ByteBufUtils.writeUTF8String(b, r.type);
            ByteBufUtils.writeUTF8String(b, r.branch);
            ByteBufUtils.writeUTF8String(b, r.runText);
            ByteBufUtils.writeUTF8String(b, r.outText);
            b.writeInt(r.badge);
            ByteBufUtils.writeItemStack(b, r.icon);
        }

        static StepRow read(PacketBuffer b) throws java.io.IOException {
            if (b.readBoolean()) return segment(ByteBufUtils.readUTF8String(b));
            StepRow r = new StepRow();
            r.seq = b.readInt();
            r.lineId = ByteBufUtils.readUTF8String(b);
            r.stepId = ByteBufUtils.readUTF8String(b);
            r.name = ByteBufUtils.readUTF8String(b);
            r.type = ByteBufUtils.readUTF8String(b);
            r.branch = ByteBufUtils.readUTF8String(b);
            r.runText = ByteBufUtils.readUTF8String(b);
            r.outText = ByteBufUtils.readUTF8String(b);
            r.badge = b.readInt();
            r.icon = ByteBufUtils.readItemStack(b);
            return r;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StepRow)) return false;
            StepRow x = (StepRow) o;
            if (segment != x.segment) return false;
            if (segment) return name.equals(x.name);
            return seq == x.seq && badge == x.badge
                && stepId.equals(x.stepId)
                && name.equals(x.name)
                && runText.equals(x.runText)
                && outText.equals(x.outText);
        }

        @Override
        public int hashCode() {
            return segment ? name.hashCode() : (stepId.hashCode() * 31 + seq) * 31 + badge;
        }
    }

    static final class CandRow {

        String label;
        boolean bound;
        int dim, x, y, z;

        CandRow(String label, boolean bound, int dim, int x, int y, int z) {
            this.label = label;
            this.bound = bound;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static void write(PacketBuffer b, CandRow r) throws java.io.IOException {
            ByteBufUtils.writeUTF8String(b, r.label);
            b.writeBoolean(r.bound);
            b.writeInt(r.dim);
            b.writeInt(r.x);
            b.writeInt(r.y);
            b.writeInt(r.z);
        }

        static CandRow read(PacketBuffer b) throws java.io.IOException {
            return new CandRow(
                ByteBufUtils.readUTF8String(b),
                b.readBoolean(),
                b.readInt(),
                b.readInt(),
                b.readInt(),
                b.readInt());
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CandRow)) return false;
            CandRow r = (CandRow) o;
            return bound == r.bound && dim == r.dim && x == r.x && y == r.y && z == r.z && label.equals(r.label);
        }

        @Override
        public int hashCode() {
            return ((dim * 31 + x) * 31 + y) * 31 + z;
        }
    }
}
