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
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.FluidDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCover;
import com.wztwzt.ae2_qof.cover.stockmonitor.ThresholdMode;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.WirelessAeConnector;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.StockMonitorActionPacket;
import com.wztwzt.ae2_qof.terminal.CoverRegistry.CoverEntry;

import appeng.api.config.LevelType;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.parts.automation.PartLevelEmitter;
import cpw.mods.fml.common.network.ByteBufUtils;

/**
 * 库存统计终端 GUI（第五版）：左侧竖排页签分两页（发信器 / 库存覆盖板）+ 行首目标图标 + 编辑子面板。
 *
 * <h2>为什么第一/二版只剩两行标题（3.20.3 根因，勿重蹈）</h2>
 * 控件建在 {@code if (isServer)} 之后，而 MUI2 的 {@code buildUI} 在**双端各构建一次**、
 * **渲染的是客户端那棵树** ⇒ 客户端上连接状态/连接按钮/两个列表全都不存在。
 * 现在的铁律：**结构双侧一致，服务端专有数据一律经 SyncValue/同步包下发**。
 *
 * <h2>两条范式来源</h2>
 * <ul>
 * <li>结构双端一致 + {@code *SyncValue}：本模组库存检测覆盖板 GUI（用户实测可用）；</li>
 * <li>变长列表 {@code GenericListSyncHandler} + {@code DynamicSyncedWidget} + {@code ListWidget}：
 * Nexus 自己的 {@code WirelessSelectionPanel}。</li>
 * </ul>
 *
 * <h2>第四版（3.21.0）新增</h2>
 * <ul>
 * <li>行内布局：左名称 + 右中文数值 + 两个小按钮【高亮】【传送】；</li>
 * <li>高亮/传送：按钮是**客户端**回调，只把坐标发给服务端，由
 * {@link StockMonitorActionPacket} 重新解析目标并做会话/权限校验；</li>
 * <li>列表收集失败只在首次记 WARN：静默兜底会让"列表为空"无法区分"确实没有"与"枚举出错"。</li>
 * </ul>
 *
 * <h2>第五版（3.31.0）新增：为什么必须改，以及为什么这样改</h2>
 * <ol>
 * <li><b>专用服上目标名是英文</b>（用户实测：同一存档单机中文、上服英文，且 AE2 自己的界面同服仍是中文）。
 * 根因是**名称在服务端就被定稿成字符串**：发信器走 {@code IAEStack.getDisplayName()} →
 * {@code Platform.getItemDisplayName()} → MC {@code ItemStack.getDisplayName()} → {@code StatCollector}，
 * 流体走 {@code Fluid.getLocalizedName()} → {@code StatCollector}（两者都已在实例 jar 字节码里核对），
 * 而专用服的语言表只有 en_US；覆盖板侧更彻底——注册表只存了
 * {@code CoverEntry.targetName} 这个**字符串**。修法：行快照改传**本体**
 * （物品栈 / 流体注册名），名称与图标一律由**客户端**求值。绝不能改成发 {@code IAEStack}：
 * {@code AEItemStack.getDisplayName()} 会把服务端算出的名字**缓存进 AEItemDef**。</li>
 * <li><b>行首图标</b>：{@code ItemDrawable} / {@code FluidDrawable}（真流体贴图）。
 * 注意 {@code GuiDraw.drawItem} 会按 {@code w/16, h/16} 缩放，所以图标控件必须正好 16×16，
 * 行高靠 {@code Flow.crossAxisAlignment(CENTER)} 居中。</li>
 * <li><b>拆两页 + 行高 24</b>：左侧竖排页签照抄自适应电网终端
 * （{@code PagedWidget.Controller} + {@code PageButton.tab(GuiTextures.TAB_LEFT, …)}），
 * 页面选择存进终端 NBT（{@code StockMonitorTerminal#getUiPage()}）实现"记住上次选择"。</li>
 * <li><b>槽位口径</b>：覆盖板行摘要取"第一个非空槽"，编辑面板按**同一个槽位**写入
 * （老实现是"显示第一个非空槽、却改 slot 0"，多槽覆盖板会出现"看的是 A、改的是 B"）。</li>
 * </ol>
 */
public class StockMonitorTerminalGui {

    // ===== 尺寸（3.31.0：拆两页后按 380×252 重排；左页签 40px 宽）=====
    private static final int PANEL_W = 380;
    private static final int PANEL_H = 252;
    private static final int TAB_W = 40;
    private static final int TAB_H = 28;
    private static final int CONTENT_X = TAB_W;
    private static final int CONTENT_W = PANEL_W - CONTENT_X - 4;
    /** 表头（标题/连接状态/连接按钮）占 16px，页签与页面从它下面开始 */
    private static final int PAGE_Y = 26;
    private static final int PAGE_H = PANEL_H - PAGE_Y - 6;
    private static final int LIST_W = CONTENT_W - 8;
    /** 8 行 × 24px：面板不滚动，这个高度在 1080p + GUI 缩放 4（可用 480×270）下不会被裁 */
    private static final int LIST_H = 192;
    private static final int ROW_H = 24;
    /**
     * 行宽必须与 {@link RowList} 的视口宽度一致（= {@code LIST_W - 8}）。
     * 写成 {@code LIST_W} 会让行比视口宽 8px ⇒ 最右侧的【传送】按钮被裁掉一截。
     * 宽度预算：图标 16 + 名称 {@link #NAME_W} + 数值 {@link #VALUE_W} + 两个按钮 30×2 + 4 个 2px 间距 = {@code ROW_W}。
     */
    private static final int ROW_W = LIST_W - 8;
    /** 必须 16×16：GuiDraw.drawItem 按 w/16、h/16 缩放，给非正方形会把图标拉变形 */
    private static final int ICON_SIZE = 16;
    private static final int NAME_W = 146;
    private static final int VALUE_W = 90;
    private static final int BTN_W = 30;
    private static final int EDIT_PANEL_W = 240;
    private static final int EDIT_PANEL_H = 190;

    /**
     * 列表重算节流（tick）。getter 每个 tick 都会被 {@code detectAndSendChanges} 调用，
     * 而覆盖板列表要遍历注册表并逐项 {@code getTileEntity}，发信器列表要读 AE 配置项；
     * 1 秒重算一次对这种"集中查看"界面足够。
     */
    private static final long LIST_REFRESH_INTERVAL = 20L;

    // 当前选中的条目：按玩家 UUID 隔离（P1-016）。只在**服务端**有意义；
    // 客户端界面不读它们——显示值由下面的 SyncValue 从服务端推送。
    private static final Map<UUID, CoverEntry> SELECTED_COVERS = new ConcurrentHashMap<>();
    private static final Map<UUID, PartLevelEmitter> SELECTED_EMITTERS = new ConcurrentHashMap<>();
    /**
     * 3.31.0：选中项对应的**行号**。编辑子面板要靠它从"客户端已同步的行快照"里取本体，
     * 从而在客户端解析中文名（服务端字符串在专用服上是英文）。
     */
    private static final Map<UUID, Integer> SELECTED_COVER_INDEX = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SELECTED_EMITTER_INDEX = new ConcurrentHashMap<>();

    private static CoverEntry getSelectedCover(EntityPlayer player) {
        return player == null ? null : SELECTED_COVERS.get(player.getUniqueID());
    }

    private static void setSelectedCover(EntityPlayer player, CoverEntry entry, int index) {
        if (player == null) return;
        SELECTED_COVERS.put(player.getUniqueID(), entry);
        SELECTED_COVER_INDEX.put(player.getUniqueID(), index);
    }

    private static PartLevelEmitter getSelectedEmitter(EntityPlayer player) {
        return player == null ? null : SELECTED_EMITTERS.get(player.getUniqueID());
    }

    private static void setSelectedEmitter(EntityPlayer player, PartLevelEmitter emitter, int index) {
        if (player == null) return;
        SELECTED_EMITTERS.put(player.getUniqueID(), emitter);
        SELECTED_EMITTER_INDEX.put(player.getUniqueID(), index);
    }

    private static int selectedCoverIndex(EntityPlayer player) {
        Integer i = player == null ? null : SELECTED_COVER_INDEX.get(player.getUniqueID());
        return i == null ? -1 : i;
    }

    private static int selectedEmitterIndex(EntityPlayer player) {
        Integer i = player == null ? null : SELECTED_EMITTER_INDEX.get(player.getUniqueID());
        return i == null ? -1 : i;
    }

    public static ModularPanel build(StockMonitorTerminal terminal, PosGuiData guiData, PanelSyncManager syncManager,
        UISettings uiSettings) {
        ModularPanel panel = ModularPanel.defaultPanel("stock_monitor_terminal", PANEL_W, PANEL_H);
        EntityPlayer player = guiData.getPlayer();

        // 3.21.0：会话登记（仅服务端）——高亮/传送包据此确认"当前正打开本终端"（等价 P1-011）
        try {
            if (player != null && !player.worldObj.isRemote) terminal.registerActiveViewer(player);
        } catch (Throwable ignored) {}

        // 3.35.0-fix14（审查 P2-5）：GUI 关闭时注销 viewer——activeViewers 只加不减会让
        // P1-011 的会话保护退化成"曾经打开过就行"。addCloseListener 是 MUI2 双端关闭路径
        //（PanelSyncManager.onClose），携带的 EntityPlayer 与登记侧同源。
        syncManager.addCloseListener(p -> {
            try {
                if (p != null && !p.worldObj.isRemote) terminal.unregisterActiveViewer(p);
            } catch (Throwable ignored) {}
        });

        // ===== 两个列表的同步处理器：必须先建，编辑子面板要用它们的客户端行快照 =====
        final RowCache<EmitterRow> emitterCache = new RowCache<>();
        GenericListSyncHandler<EmitterRow> emitterRows = GenericListSyncHandler.<EmitterRow>builder()
            .getter(() -> emitterCache.get(terminal.getBaseMetaTileEntity(), () -> collectEmitterRows(terminal)))
            .serializer(EmitterRow::write)
            .deserializer(EmitterRow::read)
            .immutableCopy()
            .build();
        syncManager.syncValue("sm_terminal_emitters", emitterRows);

        final CoverScan coverScan = new CoverScan();
        GenericListSyncHandler<CoverRow> coverRows = GenericListSyncHandler.<CoverRow>builder()
            .getter(() -> coverScan.rows(terminal))
            .serializer(CoverRow::write)
            .deserializer(CoverRow::read)
            .immutableCopy()
            .build();
        syncManager.syncValue("sm_terminal_covers", coverRows);

        // 3.21.1（用户决定）：只列**本终端所连网络**的覆盖板。
        // 被过滤掉的数量单独同步给客户端，让"列表为空"能解释清楚——否则"覆盖板怎么不见了"
        // 会变成无法定性的现象（本项目原则：不做无法定性的空状态）。
        IntSyncValue hiddenCoversSync = new IntSyncValue(() -> coverScan.hidden(terminal));
        syncManager.syncValue("sm_terminal_covers_hidden", hiddenCoversSync);

        // 3.31.0：选中行号（S2C，仅服务端写）。编辑子面板在客户端拿它去查行快照里的本体，
        // 这样"名称"永远不会走服务端字符串这条英文链路。
        IntSyncValue emitterSelSync = new IntSyncValue(() -> selectedEmitterIndex(player));
        syncManager.syncValue("sm_terminal_emitter_sel", emitterSelSync);
        IntSyncValue coverSelSync = new IntSyncValue(() -> selectedCoverIndex(player));
        syncManager.syncValue("sm_terminal_cover_sel", coverSelSync);

        // ===== 子面板（编辑 + 网络选择）=====
        IPanelHandler coverEditPanel = syncManager.syncedPanel(
            "cover_edit",
            true,
            (panelSyncManager, panelHandler) -> buildCoverEditPanel(
                terminal,
                panelSyncManager,
                panelHandler,
                coverRows,
                coverSelSync));

        IPanelHandler emitterEditPanel = syncManager.syncedPanel(
            "emitter_edit",
            true,
            (panelSyncManager,
                panelHandler) -> buildEmitterEditPanel(panelSyncManager, panelHandler, emitterRows, emitterSelSync));

        IPanelHandler netSelectPanel = syncManager.syncedPanel("net_select", true, (panelSyncManager, panelHandler) -> {
            if (WirelessAeConnector.isNexusAvailable()) {
                StockMonitorTerminalWirelessEndpoint endpoint = new StockMonitorTerminalWirelessEndpoint(terminal);
                return cn.dancingsnow.ae_wireless_nexus.gui.WirelessSelectionPanel.build(
                    "ae2qol_terminal_net_select",
                    endpoint,
                    panelSyncManager.getPlayer(),
                    panelSyncManager,
                    true);
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

        // ===== 两个变长列表：按快照重建控件（行内含本体，客户端自己解析名称/图标）=====
        DynamicLinkedSyncHandler<GenericListSyncHandler<EmitterRow>> emitterListHandler = new DynamicLinkedSyncHandler<>(
            emitterRows).widgetProvider(
                (dynamicSyncManager, value) -> buildEmitterList(
                    value.getValue(),
                    dynamicSyncManager,
                    player,
                    emitterEditPanel,
                    terminal));
        syncManager.syncValue("sm_terminal_emitters_dyn", emitterListHandler);

        DynamicLinkedSyncHandler<GenericListSyncHandler<CoverRow>> coverListHandler = new DynamicLinkedSyncHandler<>(
            coverRows).widgetProvider(
                (dynamicSyncManager, value) -> buildCoverList(
                    value.getValue(),
                    dynamicSyncManager,
                    player,
                    coverEditPanel,
                    terminal,
                    hiddenCoversSync));
        syncManager.syncValue("sm_terminal_covers_dyn", coverListHandler);

        // ===== 表头：标题 + 连接状态 + 连接/切换网络 =====
        Flow header = Flow.row()
            .pos(CONTENT_X, 6)
            .size(CONTENT_W, 16)
            .childPadding(2);
        header.child(
            new TextWidget<>(IKey.lang("ae2_qof.terminal.stock_monitor.title")).size(116, 16)
                .color(0xFF404040));
        header.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> boundSync.getValue() ? StatCollector.translateToLocal("ae2_qof.terminal.status.bound")
                        : StatCollector.translateToLocal("ae2_qof.terminal.status.unbound"))).size(112, 16));
        header.child(
            new ButtonWidget<>().size(80, 16)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocal(
                            boundSync.getValue() ? "ae2_qof.gui.stock_monitor.switch_network"
                                : "ae2_qof.gui.stock_monitor.connect")))
                .syncHandler(connectAction));
        panel.child(header);

        // ===== 左侧竖排页签 + 两页 =====
        // 3.31.0：页签选择存进终端 NBT（"记住上次选择"）。getter=服务端存量，setter=服务端写回，
        // allowC2S 让客户端点击能回传；越界在终端侧夹回，客户端传什么都改不坏存档。
        IntSyncValue pageSync = new IntSyncValue(terminal::getUiPage, terminal::setUiPage);
        pageSync.allowC2S();
        syncManager.syncValue("sm_terminal_page", pageSync);

        PagedWidget.Controller tabController = new PagedWidget.Controller();

        Flow tabStrip = Flow.column()
            .pos(0, 6)
            .width(TAB_W)
            .childPadding(0);
        tabStrip.child(pageTab(0, tabController, "ae2_qof.terminal.page.emitters"));
        tabStrip.child(pageTab(1, tabController, "ae2_qof.terminal.page.covers"));
        panel.child(tabStrip);

        PagedWidget<?> pages = new PagedWidget<>().controller(tabController)
            .pos(CONTENT_X, PAGE_Y)
            .size(CONTENT_W, PAGE_H)
            .initialPage(pageSync.getIntValue())
            .addPage(buildEmitterPage(emitterListHandler, emitterRows, syncManager, player, emitterEditPanel, terminal))
            .addPage(
                buildCoverPage(
                    coverListHandler,
                    coverRows,
                    syncManager,
                    player,
                    coverEditPanel,
                    terminal,
                    hiddenCoversSync));
        // 客户端点页签 → 回写服务端 NBT
        pages.onPageChange(i -> { if (pageSync.getIntValue() != i) pageSync.setValue(i); });
        // 服务端存量的同步值晚于控件构建到达客户端，这里补一次切页，否则首次打开"记住上次选择"不生效
        pageSync.setChangeListener(() -> {
            int stored = pageSync.getIntValue();
            if (pages.getCurrentPageIndex() != stored) pages.setPage(stored);
        });
        panel.child(pages);

        return panel;
    }

    // ===== 页面骨架 =====

    /** 左侧竖排页签按钮：照抄自适应电网终端（{@code PageButton} + {@code GuiTextures.TAB_LEFT}）。 */
    private static IWidget pageTab(int index, PagedWidget.Controller controller, String langKey) {
        // PageButton 没有文字 API，但它是 Widget ⇒ 用 overlay 把中文叠在页签贴图上（居中）
        return new PageButton(index, controller).tab(GuiTextures.TAB_LEFT, index == 0 ? -1 : 0)
            .overlay(
                IKey.lang(langKey)
                    .alignment(Alignment.Center)
                    .scale(0.8F))
            .tooltip(t -> t.addLine(IKey.lang(langKey)))
            .size(TAB_W, TAB_H);
    }

    private static IWidget buildEmitterPage(DynamicLinkedSyncHandler<GenericListSyncHandler<EmitterRow>> listHandler,
        GenericListSyncHandler<EmitterRow> rows, PanelSyncManager syncManager, EntityPlayer player,
        IPanelHandler editPanel, StockMonitorTerminal terminal) {
        Flow column = Flow.column()
            .size(CONTENT_W, PAGE_H)
            .childPadding(2);
        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, LIST_H)
                .initialChild(buildEmitterList(rows.getValue(), syncManager, player, editPanel, terminal))
                .syncHandler(listHandler));
        column.child(
            new TextWidget<>(IKey.lang("ae2_qof.terminal.hint")).size(LIST_W, 10)
                .color(0xFF888888));
        return column;
    }

    private static IWidget buildCoverPage(DynamicLinkedSyncHandler<GenericListSyncHandler<CoverRow>> listHandler,
        GenericListSyncHandler<CoverRow> rows, PanelSyncManager syncManager, EntityPlayer player,
        IPanelHandler editPanel, StockMonitorTerminal terminal, IntSyncValue hiddenCoversSync) {
        Flow column = Flow.column()
            .size(CONTENT_W, PAGE_H)
            .childPadding(2);
        column.child(
            new DynamicSyncedWidget<>().size(LIST_W, LIST_H)
                .initialChild(
                    buildCoverList(rows.getValue(), syncManager, player, editPanel, terminal, hiddenCoversSync))
                .syncHandler(listHandler));
        column.child(
            new TextWidget<>(IKey.lang("ae2_qof.terminal.hint")).size(LIST_W, 10)
                .color(0xFF888888));
        return column;
    }

    // ===== 发信器列表 =====

    /** 在服务端枚举终端当前生效网络上的 AE2 标准发信器，生成只含显示/定位字段的快照行。 */
    private static List<EmitterRow> collectEmitterRows(StockMonitorTerminal terminal) {
        List<EmitterRow> rows = new ArrayList<>();
        try {
            IGrid grid = terminal.resolveGrid();
            if (grid == null) return rows;

            // 注意两件事（都是"列表永远为空"的真实根因）：
            // 1) AE2 的 Grid.getMachines(Class) 是**精确类名**查表（与接口终端同一个坑），
            // 直接传 PartLevelEmitter 会漏掉任何子类 ⇒ 这里按已注册的机器类过滤 instanceof；
            // 2) Grid.getMachines(...) 返回的是 **IGridNode** 集合
            // （IMachineSet extends IReadOnlyCollection<IGridNode>，已用实例 AE2 jar 核对），
            // 机器必须从 node.getMachine() 取——旧实现直接对 node 做 instanceof，永远匹配不到。
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

            // 排序保证行序在两侧一致且稳定（客户端列表就是服务端快照，行号一一对应）。
            // 3.31.0：排序键换成**语言无关**的物品/流体标识——旧实现按服务端英文名排序，
            // 客户端显示中文时行序看着是乱的。
            emitters.sort(
                Comparator.comparing(StockMonitorTerminalGui::getEmitterSortKey)
                    .thenComparing(e -> e.getConfigManager() == null ? "" : String.valueOf(e.getConfigManager())));

            for (int i = 0; i < emitters.size(); i++) {
                PartLevelEmitter emitter = emitters.get(i);
                rows.add(EmitterRow.of(i, levelTypeName(emitter), emitter.getReportingValue(), emitter));
            }
        } catch (Throwable t) {
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
        IPanelHandler editPanel, StockMonitorTerminal terminal) {
        RowList list = new RowList();
        if (rows == null || rows.isEmpty()) {
            list.child(
                new TextWidget<>(IKey.lang("ae2_qof.terminal.no_emitters")).color(0xFFAAAAAA)
                    .size(LIST_W - 8, 12));
            return list;
        }
        for (EmitterRow row : rows) {
            // 名称在**客户端**解析（服务端算出来的名字在专用服上是英文，见类头注释）
            String name = targetLabel(row.icon, row.fluidName, "", "ae2_qof.terminal.emitter_unnamed");
            String value = typeLabel(row.typeName) + " · " + formatNumber(row.threshold);
            list.child(buildActionRow(syncManager, "sm_terminal_emitter_row", row.index, p -> {
                // 该回调在服务端执行；row 是服务端行的实例，携带真实 AE 部件引用。
                // 客户端行的 ref 为 null（快照不含对象），因此这里天然是服务端分支。
                if (row.emitter == null) return;
                setSelectedEmitter(p, row.emitter, row.index);
                editPanel.openPanel();
            },
                row.icon,
                fluidIcon(row.fluidName),
                name,
                value,
                row.hasPos() ? new int[] { row.dim, row.x, row.y, row.z } : null,
                terminal,
                StockMonitorActionPacket.KIND_EMITTER,
                java.util.Arrays.asList(
                    EnumChatFormatting.WHITE + name,
                    EnumChatFormatting.GRAY + StatCollector.translateToLocal("ae2_qof.terminal.type")
                        + ": "
                        + typeLabel(row.typeName),
                    EnumChatFormatting.GRAY + StatCollector.translateToLocal("ae2_qof.terminal.threshold")
                        + ": "
                        + formatNumber(row.threshold),
                    row.hasPos()
                        ? EnumChatFormatting.DARK_AQUA + "D"
                            + row.dim
                            + " ["
                            + row.x
                            + ", "
                            + row.y
                            + ", "
                            + row.z
                            + "]"
                        : EnumChatFormatting.DARK_GRAY
                            + StatCollector.translateToLocal("ae2_qof.terminal.no_coords"))));
        }
        return list;
    }

    // ===== 覆盖板列表 =====

    /**
     * 覆盖板列表扫描（服务端）：**只列本终端所连网络**的覆盖板（3.21.1 用户决定），
     * 并记录"因属于其它网络而被过滤掉"的数量，供空列表时给出可解释的提示。
     * 与 {@link RowCache} 同款按 {@link #LIST_REFRESH_INTERVAL} 节流（getter 每 tick 都会被调用）。
     */
    private static final class CoverScan {

        private long lastTick = -LIST_REFRESH_INTERVAL;
        private List<CoverRow> rows = Collections.emptyList();
        private int hidden = 0;

        List<CoverRow> rows(StockMonitorTerminal terminal) {
            refresh(terminal);
            return rows;
        }

        int hidden(StockMonitorTerminal terminal) {
            refresh(terminal);
            return hidden;
        }

        private void refresh(StockMonitorTerminal terminal) {
            long now = 0L;
            try {
                TileEntity te = (TileEntity) terminal.getBaseMetaTileEntity();
                if (te != null && te.getWorldObj() != null) now = te.getWorldObj()
                    .getTotalWorldTime();
            } catch (Throwable ignored) {}
            if (now - lastTick < LIST_REFRESH_INTERVAL) return;
            lastTick = now;

            List<CoverRow> fresh = new ArrayList<>();
            int hiddenCount = 0;
            try {
                TileEntity te = (TileEntity) terminal.getBaseMetaTileEntity();
                if (te == null) {
                    rows = fresh;
                    hidden = 0;
                    return;
                }
                CoverRegistry registry = CoverRegistry.get(te.getWorldObj());
                registry.refreshOnlineStatus(); // 只有服务端需要，且只在重算时执行

                int total = registry.getAll()
                    .size();
                // 只显示本终端所连网络：终端未绑定（邻接直连，networkId 为空）时 getByNetwork 返回全部
                Collection<CoverEntry> covers = registry.getByNetwork(terminal.getNetworkId());

                List<CoverEntry> sorted = new ArrayList<>(covers);
                sorted.sort(
                    Comparator.comparingInt((CoverEntry e) -> e.dim)
                        .thenComparingInt(e -> e.x)
                        .thenComparingInt(e -> e.y)
                        .thenComparingInt(e -> e.z)
                        .thenComparingInt(e -> e.side));

                for (int i = 0; i < sorted.size(); i++) {
                    CoverEntry entry = sorted.get(i);
                    fresh.add(
                        new CoverRow(
                            i,
                            entry.dim,
                            entry.x,
                            entry.y,
                            entry.z,
                            entry.side,
                            entry.targetName,
                            entry.targetItem,
                            entry.targetFluidName,
                            entry.slotIndex,
                            entry.online,
                            entry.modeOrdinal,
                            entry.threshold,
                            entry));
                }
                hiddenCount = Math.max(0, total - sorted.size());
            } catch (Throwable t) {
                ae2qol$warnCollectFailure("覆盖板", t);
            }
            rows = fresh;
            hidden = hiddenCount;
        }
    }

    private static IWidget buildCoverList(List<CoverRow> rows, PanelSyncManager syncManager, EntityPlayer player,
        IPanelHandler editPanel, StockMonitorTerminal terminal, IntSyncValue hiddenSync) {
        RowList list = new RowList();
        if (rows == null || rows.isEmpty()) {
            // 空列表要能说明原因：是"本网络下确实没有覆盖板"，还是"有但都属于别的网络（被过滤了）"
            int hidden = hiddenSync == null ? 0 : hiddenSync.getValue();
            if (hidden > 0) {
                list.child(
                    new TextWidget<>(IKey.lang("ae2_qof.terminal.no_covers_filtered", hidden)).color(0xFFAAAAAA)
                        .size(LIST_W - 8, 12));
            } else {
                list.child(
                    new TextWidget<>(IKey.lang("ae2_qof.terminal.no_covers")).color(0xFFAAAAAA)
                        .size(LIST_W - 8, 12));
            }
            return list;
        }
        for (CoverRow row : rows) {
            String name = targetLabel(row.icon, row.fluidName, row.targetName, "ae2_qof.terminal.unset");
            String value = modeLabel(row.modeOrdinal) + " · " + formatNumber(row.threshold);

            list.child(buildActionRow(syncManager, "sm_terminal_cover_row", row.index, p -> {
                if (row.entry == null) return;
                setSelectedCover(p, row.entry, row.index);
                editPanel.openPanel();
            },
                row.online ? row.icon : null,
                row.online ? fluidIcon(row.fluidName) : null,
                row.online ? name : EnumChatFormatting.GRAY + name + " [OFF]",
                value,
                new int[] { row.dim, row.x, row.y, row.z },
                terminal,
                StockMonitorActionPacket.KIND_COVER,
                java.util.Arrays.asList(
                    EnumChatFormatting.WHITE + name,
                    EnumChatFormatting.GRAY + StatCollector.translateToLocal("ae2_qof.terminal.mode")
                        + ": "
                        + modeLabel(row.modeOrdinal),
                    EnumChatFormatting.GRAY + StatCollector.translateToLocal("ae2_qof.terminal.threshold")
                        + ": "
                        + formatNumber(row.threshold),
                    EnumChatFormatting.DARK_AQUA + "D"
                        + row.dim
                        + " ["
                        + row.x
                        + ", "
                        + row.y
                        + ", "
                        + row.z
                        + "]"
                        + " 面"
                        + row.side,
                    row.online ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("ae2_qof.terminal.online")
                        : EnumChatFormatting.RED + StatCollector.translateToLocal("ae2_qof.terminal.offline"))));
        }
        return list;
    }

    // ===== 行构建（行首图标 / 名称 / 数值 / 高亮 + 传送）=====

    /**
     * 构建一行：行首图标 + 左「名称」按钮 + 右「数值」按钮（两者都能点开编辑子面板）+ 两个动作按钮。
     *
     * <h2>为什么用 overlay 而不是按钮的子 TextWidget（3.21.2 两个根因，连着踩）</h2>
     * <ol>
     * <li>MUI2 的 {@code ButtonWidget extends SingleChildWidget}，其 {@code child()} 源码是
     * 「新子控件会先把旧子控件 {@code dispose()} 再替换」——所以"名称 + 数值"两个子控件时
     * **名称被直接扔掉**，列表里只剩数值（现象：名称整列空白）；</li>
     * <li>装进按钮里的文本会**吞掉点击**（本仓 PH 的 {@code NonInteractiveText} 就是为这个坑写的），
     * 于是"点行编辑"失效、发信器数量改不动。</li>
     * </ol>
     * {@code overlay(IKey)} 是 3.20.4 实测能渲染文字的路径，且不参与点击命中，一举解决两者。
     * 名称与数值各用一个独立的 {@code InteractionSyncHandler}（同一 handler 实例绑两个控件的行为
     * 未经验证，不冒险）。
     */
    private static IWidget buildActionRow(PanelSyncManager syncManager, String keyPrefix, int index,
        java.util.function.Consumer<EntityPlayer> onOpen, ItemStack iconItem, FluidStack iconFluid, String name,
        String value, int[] pos, StockMonitorTerminal terminal, int kind, List<String> tooltipLines) {
        InteractionSyncHandler nameHandler = syncManager.getOrCreateSyncHandler(
            keyPrefix + "_name_" + index,
            InteractionSyncHandler.class,
            () -> new InteractionSyncHandler().setOnMousePressed(mouse -> onOpen.accept(syncManager.getPlayer())));
        InteractionSyncHandler valueHandler = syncManager.getOrCreateSyncHandler(
            keyPrefix + "_value_" + index,
            InteractionSyncHandler.class,
            () -> new InteractionSyncHandler().setOnMousePressed(mouse -> onOpen.accept(syncManager.getPlayer())));

        Flow row = Flow.row()
            .size(ROW_W, ROW_H)
            .childPadding(2)
            // 行高 24 > 图标 16：不居中的话图标会贴着行顶
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(iconWidget(iconItem, iconFluid))
            .child(
                new ButtonWidget<>().size(NAME_W, ROW_H)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.str(name))
                    .syncHandler(nameHandler))
            .child(
                new ButtonWidget<>().size(VALUE_W, ROW_H)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.str(value))
                    .syncHandler(valueHandler));

        if (pos != null) {
            row.child(
                actionButton(
                    terminal,
                    pos,
                    kind,
                    StockMonitorActionPacket.ACTION_HIGHLIGHT,
                    "ae2_qof.terminal.highlight"));
            row.child(
                actionButton(
                    terminal,
                    pos,
                    kind,
                    StockMonitorActionPacket.ACTION_TELEPORT,
                    "ae2_qof.terminal.teleport"));
        } else {
            row.child(new TextWidget<>(IKey.str("")).size(BTN_W, ROW_H));
            row.child(new TextWidget<>(IKey.str("")).size(BTN_W, ROW_H));
        }

        row.tooltipBuilder(t -> {
            for (String line : tooltipLines) t.addLine(IKey.str(line));
            t.addLine(IKey.str(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("ae2_qof.terminal.hint")));
        });
        return row;
    }

    /**
     * 行首图标：物品优先，其次**真流体贴图**（{@code FluidDrawable}），两者都没有就留空位保持列对齐。
     *
     * <p>
     * 为什么不拿 {@code IAEStack.getItemStackForNEI()} 代替流体图标：装了 AE2FC 时它返回的是
     * 流体包体/容器物品（{@code AEFluidStack#getItemStackForNEI} 里的 {@code Platform.isAE2FCLoaded} 分支），
     * 用户明确要"真流体贴图，不要容器图标"。
     */
    private static IWidget iconWidget(ItemStack item, FluidStack fluid) {
        if (item != null && item.getItem() != null) {
            return new ItemDrawable(item).asWidget()
                .size(ICON_SIZE, ICON_SIZE);
        }
        if (fluid != null && fluid.getFluid() != null) {
            return new FluidDrawable(fluid).asWidget()
                .size(ICON_SIZE, ICON_SIZE);
        }
        // 显式声明成 Widget<?>：Widget 是自引用泛型，直接把 new Widget<>() 当 IWidget 返回会推断失败
        Widget<?> empty = new Widget<>().size(ICON_SIZE, ICON_SIZE);
        return empty;
    }

    private static IWidget actionButton(StockMonitorTerminal terminal, int[] pos, int kind, int action,
        String langKey) {
        return new ButtonWidget<>().size(BTN_W, ROW_H)
            .background(GuiTextures.BUTTON_CLEAN)
            .child(
                new TextWidget<>(IKey.lang(langKey)).size(BTN_W, ROW_H)
                    .textAlign(Alignment.Center)
                    .scale(0.8F))
            .tooltip(t -> t.addLine(IKey.lang(langKey)))
            .onMousePressed(mouse -> {
                // 客户端回调：只报目标坐标，判定/鉴权/执行全在服务端（StockMonitorActionPacket）
                try {
                    TileEntity base = (TileEntity) terminal.getBaseMetaTileEntity();
                    if (base == null) return true;
                    ModNetwork.CHANNEL.sendToServer(
                        new StockMonitorActionPacket(
                            action,
                            kind,
                            base.getWorldObj().provider.dimensionId,
                            base.xCoord,
                            base.yCoord,
                            base.zCoord,
                            pos[0],
                            pos[1],
                            pos[2],
                            pos[3]));
                } catch (Throwable ignored) {}
                return true;
            });
    }

    // ===== 覆盖板编辑子面板 =====

    /**
     * 编辑子面板：**结构双端一致**，显示值来自客户端行快照（名称/图标）或服务端 getter（数值），
     * 所有写入都在服务端 setter 里做权限校验后落盘（P1-015 语义不变）。
     * 未选中/区块未加载时不再提前 return（那会让客户端永远看不到控件），而是显示提示。
     *
     * <p>
     * 3.31.0：名称改成 `IKey.dynamic` + 客户端行快照解析（原来走服务端 StringSyncValue，
     * 专用服上必然英文）；写入槽位改用行里带的 {@code slotIndex}（原写死 slot 0）。
     */
    private static ModularPanel buildCoverEditPanel(StockMonitorTerminal terminal, PanelSyncManager syncManager,
        IPanelHandler panelHandler, GenericListSyncHandler<CoverRow> coverRows, IntSyncValue selSync) {
        ModularPanel edit = ModularPanel.defaultPanel("cover_edit_panel", EDIT_PANEL_W, EDIT_PANEL_H);

        BooleanSyncValue readySync = new BooleanSyncValue(() -> selectedCover(syncManager) != null);

        StringSyncValue positionSync = new StringSyncValue(() -> {
            CoverEntry entry = getSelectedCover(syncManager.getPlayer());
            if (entry == null) return "";
            return "D" + entry.dim + " [" + entry.x + ", " + entry.y + ", " + entry.z + "] 面" + entry.side;
        });

        LongSyncValue thresholdSync = new LongSyncValue(() -> {
            StockMonitorCover cover = selectedCover(syncManager);
            if (cover == null) return 0L;
            return cover.getCoverData()
                .getSlot(selectedSlot(syncManager)).threshold;
        }, value -> {
            StockMonitorCover cover = selectedCover(syncManager);
            if (cover == null) return;
            if (!terminal.hasBuildPermission(syncManager.getPlayer())) return;
            cover.getCoverData()
                .getSlot(selectedSlot(syncManager)).threshold = value;
            cover.markCoverDirty();
        }).allowC2S();

        IntSyncValue modeSync = new IntSyncValue(() -> {
            StockMonitorCover cover = selectedCover(syncManager);
            return cover == null ? 0
                : cover.getCoverData()
                    .getMode()
                    .ordinal();
        }, value -> {
            StockMonitorCover cover = selectedCover(syncManager);
            if (cover == null) return;
            if (!terminal.hasBuildPermission(syncManager.getPlayer())) return;
            cover.getCoverData()
                .setMode(ThresholdMode.fromOrdinal(value));
            cover.markCoverDirty();
        }).allowC2S();

        syncManager.syncValue("sm_cover_edit_ready", readySync);
        syncManager.syncValue("sm_cover_edit_pos", positionSync);
        syncManager.syncValue("sm_cover_edit_threshold", thresholdSync);
        syncManager.syncValue("sm_cover_edit_mode", modeSync);

        edit.child(new TextWidget<>(IKey.dynamic(() -> {
            CoverRow row = rowAt(coverRows, selSync.getIntValue());
            if (row == null) {
                return EnumChatFormatting.RED + StatCollector.translateToLocal("ae2_qof.terminal.none_selected");
            }
            return targetLabel(row.icon, row.fluidName, row.targetName, "ae2_qof.terminal.unset");
        })).pos(10, 8)
            .size(EDIT_PANEL_W - 20, 14));

        edit.child(
            new TextWidget<>(IKey.lang("ae2_qof.terminal.threshold")).pos(10, 32)
                .size(60, 12));
        edit.child(
            new TextFieldWidget().numbersLong(0, Long.MAX_VALUE)
                .pos(75, 30)
                .size(EDIT_PANEL_W - 85, 16)
                .value(thresholdSync));

        edit.child(
            new TextWidget<>(IKey.lang("ae2_qof.terminal.mode")).pos(10, 58)
                .size(60, 12));
        String[] modeKeys = { "ae2_qof.terminal.mode.below", "ae2_qof.terminal.mode.above" };
        for (int i = 0; i < modeKeys.length; i++) {
            final int mi = i;
            InteractionSyncHandler modeBtn = new InteractionSyncHandler()
                .setOnMousePressed(mouse -> modeSync.setValue(mi));
            edit.child(
                new ButtonWidget<>().pos(75 + i * 70, 56)
                    .size(65, 16)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(IKey.lang(modeKeys[i]))
                    .syncHandler(modeBtn));
        }

        edit.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.GRAY + positionSync.getValue())).pos(10, 82)
                .size(EDIT_PANEL_W - 20, 10));

        // 覆盖板所在区块未加载时，上面的数值会是 0 且改不动——明确提示，避免误判为 bug
        edit.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> readySync.getValue() ? ""
                        : EnumChatFormatting.RED
                            + StatCollector.translateToLocal("ae2_qof.terminal.cover_unavailable"))).pos(10, 96)
                                .size(EDIT_PANEL_W - 20, 10));

        InteractionSyncHandler closeBtn = new InteractionSyncHandler()
            .setOnMousePressed(mouse -> panelHandler.closePanel());
        edit.child(
            new ButtonWidget<>().pos(10, 116)
                .size(60, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.gui.stock_monitor.close"))
                .syncHandler(closeBtn));

        return edit;
    }

    /** 服务端解析"当前选中的覆盖板"实体（区块未加载时返回 null）。 */
    private static StockMonitorCover selectedCover(PanelSyncManager syncManager) {
        CoverEntry entry = getSelectedCover(syncManager.getPlayer());
        return entry == null ? null : locateCover(entry);
    }

    /**
     * 当前选中覆盖板要写入的槽位。3.31.0：用行摘要带的 slotIndex，
     * 保证"行里显示哪个槽的阈值，编辑面板就改哪个槽"（老实现固定改 slot 0）。
     */
    private static int selectedSlot(PanelSyncManager syncManager) {
        CoverEntry entry = getSelectedCover(syncManager.getPlayer());
        return entry == null ? 0 : entry.slotIndex;
    }

    // ===== 发信器编辑子面板 =====

    private static ModularPanel buildEmitterEditPanel(PanelSyncManager syncManager, IPanelHandler panelHandler,
        GenericListSyncHandler<EmitterRow> emitterRows, IntSyncValue selSync) {
        ModularPanel edit = ModularPanel.defaultPanel("emitter_edit_panel", EDIT_PANEL_W, EDIT_PANEL_H);

        StringSyncValue typeSync = new StringSyncValue(() -> {
            PartLevelEmitter emitter = getSelectedEmitter(syncManager.getPlayer());
            return emitter == null ? "" : levelTypeName(emitter);
        });

        LongSyncValue thresholdSync = new LongSyncValue(() -> {
            PartLevelEmitter emitter = getSelectedEmitter(syncManager.getPlayer());
            return emitter == null ? 0L : emitter.getReportingValue();
        }, value -> {
            PartLevelEmitter emitter = getSelectedEmitter(syncManager.getPlayer());
            if (emitter == null) return;
            if (!hasPermission(syncManager.getPlayer(), emitter)) return;
            emitter.setReportingValue(value);
        }).allowC2S();

        syncManager.syncValue("sm_emitter_edit_type", typeSync);
        syncManager.syncValue("sm_emitter_edit_threshold", thresholdSync);

        edit.child(new TextWidget<>(IKey.dynamic(() -> {
            EmitterRow row = rowAt(emitterRows, selSync.getIntValue());
            if (row == null) {
                return EnumChatFormatting.RED + StatCollector.translateToLocal("ae2_qof.terminal.none_selected");
            }
            return targetLabel(row.icon, row.fluidName, "", "ae2_qof.terminal.emitter_unnamed");
        })).pos(10, 8)
            .size(EDIT_PANEL_W - 20, 14));

        edit.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + StatCollector.translateToLocal("ae2_qof.terminal.type")
                        + ": "
                        + typeLabel(typeSync.getValue()))).pos(10, 28)
                            .size(EDIT_PANEL_W - 20, 12));

        edit.child(
            new TextWidget<>(IKey.lang("ae2_qof.terminal.threshold")).pos(10, 52)
                .size(60, 12));
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
            new ButtonWidget<>().pos(10, 100)
                .size(60, 18)
                .background(GuiTextures.BUTTON_CLEAN)
                .overlay(IKey.lang("ae2_qof.gui.stock_monitor.close"))
                .syncHandler(closeBtn));

        return edit;
    }

    // ===== 工具方法 =====

    /**
     * 从客户端已同步的行快照里按行号取一行。编辑子面板用它来解析名称/图标 —— 这样名称永远
     * 由客户端求值，不会走"服务端算好再下发"的英文链路。
     */
    private static <T> T rowAt(GenericListSyncHandler<T> handler, int index) {
        try {
            List<T> rows = handler == null ? null : handler.getValue();
            if (rows == null || index < 0 || index >= rows.size()) return null;
            return rows.get(index);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 目标显示名：**一律在客户端解析**。三档降级，保证任何一档都不会显示成英文/空：
     * <ol>
     * <li>物品栈 → {@code ItemStack.getDisplayName()}（客户端语言表 → 中文）；</li>
     * <li>流体注册名 → {@code Fluid.getLocalizedName()}；</li>
     * <li>老存档条目只有服务端存下来的 {@code legacyFallback} 字符串（可能是英文）+ 兜底 lang 键。</li>
     * </ol>
     */
    private static String targetLabel(ItemStack item, String fluidName, String legacyFallback, String missingKey) {
        if (item != null && item.getItem() != null) {
            try {
                String n = item.getDisplayName();
                if (n != null && !n.isEmpty()) return n;
            } catch (Throwable ignored) {}
        }
        Fluid fluid = fluid(fluidName);
        if (fluid != null) {
            try {
                String n = fluid.getLocalizedName();
                if (n != null && !n.isEmpty()) return n;
            } catch (Throwable ignored) {}
        }
        if (legacyFallback != null && !legacyFallback.isEmpty()) return legacyFallback;
        return StatCollector.translateToLocal(missingKey);
    }

    /** 流体注册名 → 真流体图标用的 FluidStack（只画图标，数量取 1）。查不到返回 null（调用方留空位）。 */
    private static FluidStack fluidIcon(String fluidName) {
        Fluid fluid = fluid(fluidName);
        return fluid == null ? null : new FluidStack(fluid, 1);
    }

    private static Fluid fluid(String fluidName) {
        if (fluidName == null || fluidName.isEmpty()) return null;
        try {
            return FluidRegistry.getFluid(fluidName);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 行快照的**值语义**栈标识。
     *
     * <p>
     * 为什么必须有它：{@code ItemStack} 没有值语义的 equals/hashCode（用的是对象身份），
     * 直接参与行比较会让 {@code detectAndSendChanges} 每 tick 都判定"行变了"⇒ 持续发包，
     * 违反本项目的性能红线（列表 20 tick 节流 + 无变化不发包）。
     */
    private static String stackKey(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "";
        Object regName = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        return (regName == null ? "?" : regName.toString()) + ":"
            + stack.getItemDamage()
            + ":"
            + (tag == null ? "" : Integer.toHexString(tag.hashCode()));
    }

    /** 发信器排序键（语言无关，保证两侧行序一致且稳定）。 */
    private static String getEmitterSortKey(PartLevelEmitter emitter) {
        try {
            IAEStack<?> config = emitter.getAEInventoryByName(StorageName.CONFIG)
                .getAEStackInSlot(0);
            if (config instanceof IAEItemStack) {
                ItemStack is = ((IAEItemStack) config).getItemStack();
                return "0:" + stackKey(is);
            }
            if (config instanceof IAEFluidStack) {
                FluidStack fs = ((IAEFluidStack) config).getFluidStack();
                return "1:" + (fs == null || fs.getFluid() == null ? ""
                    : fs.getFluid()
                        .getName());
            }
        } catch (Throwable ignored) {}
        return "2:";
    }

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

        List<T> get(gregtech.api.interfaces.tileentity.IGregTechTileEntity base,
            java.util.function.Supplier<List<T>> compute) {
            long now = 0L;
            try {
                if (base != null && base.getWorld() != null) now = base.getWorld()
                    .getTotalWorldTime();
            } catch (Throwable ignored) {}
            if (now - lastTick < LIST_REFRESH_INTERVAL) return rows;
            lastTick = now;
            List<T> fresh = compute.get();
            rows = fresh == null ? Collections.emptyList() : fresh;
            return rows;
        }
    }

    /**
     * 发信器行快照：显示/定位字段 + **目标本体**（3.31.0）。
     * {@code icon}/{@code fluidName} 只用于客户端解析名称与画图标（服务端不再定稿名称）。
     */
    static final class EmitterRow {

        final int index;
        /** 目标物品本体（客户端解析名称 + 画图标）；无目标/能量型发信器为 null。 */
        final ItemStack icon;
        /** 目标为流体时的流体注册名（当前 LevelType 只有 ITEM/ENERGY，留作兼容与未来扩展）。 */
        final String fluidName;
        final String typeName;
        final long threshold;
        final boolean positioned;
        final int dim;
        final int x;
        final int y;
        final int z;
        /** 仅服务端非 null（getter 在服务端生成），客户端快照反序列化后为 null。 */
        final transient PartLevelEmitter emitter;

        private EmitterRow(int index, ItemStack icon, String fluidName, String typeName, long threshold,
            boolean positioned, int dim, int x, int y, int z, PartLevelEmitter emitter) {
            this.index = index;
            this.icon = icon;
            this.fluidName = fluidName == null ? "" : fluidName;
            this.typeName = typeName;
            this.threshold = threshold;
            this.positioned = positioned;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.emitter = emitter;
        }

        /** 服务端构造：解析目标本体（AE CONFIG 槽）+ 宿主方块坐标（拿不到就标记 unpositioned，行内不显示动作按钮）。 */
        static EmitterRow of(int index, String typeName, long threshold, PartLevelEmitter emitter) {
            int dim = 0, x = 0, y = 0, z = 0;
            boolean positioned = false;
            try {
                TileEntity host = emitter.getTile();
                if (host != null && host.getWorldObj() != null) {
                    dim = host.getWorldObj().provider.dimensionId;
                    x = host.xCoord;
                    y = host.yCoord;
                    z = host.zCoord;
                    positioned = true;
                }
            } catch (Throwable ignored) {}

            ItemStack icon = null;
            String fluidName = "";
            try {
                IAEStack<?> config = emitter.getAEInventoryByName(StorageName.CONFIG)
                    .getAEStackInSlot(0);
                if (config instanceof IAEItemStack) {
                    ItemStack is = ((IAEItemStack) config).getItemStack();
                    if (is != null && is.getItem() != null) {
                        icon = is.copy();
                        icon.stackSize = 1;
                    }
                } else if (config instanceof IAEFluidStack) {
                    FluidStack fs = ((IAEFluidStack) config).getFluidStack();
                    if (fs != null && fs.getFluid() != null) fluidName = fs.getFluid()
                        .getName();
                }
            } catch (Throwable ignored) {}
            return new EmitterRow(index, icon, fluidName, typeName, threshold, positioned, dim, x, y, z, emitter);
        }

        boolean hasPos() {
            return positioned;
        }

        String typeShort() {
            return typeName.isEmpty() ? "?" : typeName.substring(0, 1);
        }

        static void write(PacketBuffer buf, EmitterRow row) throws IOException {
            buf.writeInt(row.index);
            // 本体按 ItemStack 发（不用 IAEStack：它的 getDisplayName() 会把服务端算好的名字缓存进 AEItemDef）
            ByteBufUtils.writeItemStack(buf, row.icon);
            ByteBufUtils.writeUTF8String(buf, row.fluidName);
            ByteBufUtils.writeUTF8String(buf, row.typeName);
            buf.writeLong(row.threshold);
            buf.writeBoolean(row.positioned);
            buf.writeInt(row.dim);
            buf.writeInt(row.x);
            buf.writeInt(row.y);
            buf.writeInt(row.z);
        }

        static EmitterRow read(PacketBuffer buf) throws IOException {
            return new EmitterRow(
                buf.readInt(),
                ByteBufUtils.readItemStack(buf),
                ByteBufUtils.readUTF8String(buf),
                ByteBufUtils.readUTF8String(buf),
                buf.readLong(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                null);
        }

        // 值语义 equals/hashCode：列表内容不变时不应触发同步与重建。
        // 物品栈必须用 stackKey 比较（ItemStack 本身没有值语义 equals，会让节流短路失效）。
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EmitterRow)) return false;
            EmitterRow r = (EmitterRow) o;
            return index == r.index && threshold == r.threshold
                && positioned == r.positioned
                && dim == r.dim
                && x == r.x
                && y == r.y
                && z == r.z
                && fluidName.equals(r.fluidName)
                && typeName.equals(r.typeName)
                && stackKey(icon).equals(stackKey(r.icon));
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(index);
            result = 31 * result + stackKey(icon).hashCode();
            result = 31 * result + fluidName.hashCode();
            result = 31 * result + typeName.hashCode();
            result = 31 * result + Long.hashCode(threshold);
            result = 31 * result + Boolean.hashCode(positioned);
            result = 31 * result + Integer.hashCode(dim);
            result = 31 * result + Integer.hashCode(x);
            result = 31 * result + Integer.hashCode(y);
            result = 31 * result + Integer.hashCode(z);
            return result;
        }
    }

    /** 覆盖板行快照：位置即身份，其余为显示摘要 + 目标本体（3.31.0）+ 摘要槽位（3.31.0）。 */
    static final class CoverRow {

        final int index;
        final int dim;
        final int x;
        final int y;
        final int z;
        final int side;
        /** 服务端字符串，只作老存档兜底（新条目靠 icon/fluidName 在客户端解析）。 */
        final String targetName;
        final ItemStack icon;
        final String fluidName;
        /** 该摘要取自哪个检测槽位：编辑面板按它写入，避免"看的是 A、改的是 B"。 */
        final int slotIndex;
        final boolean online;
        final int modeOrdinal;
        final long threshold;
        /** 仅服务端非 null。 */
        final transient CoverEntry entry;

        CoverRow(int index, int dim, int x, int y, int z, int side, String targetName, ItemStack icon, String fluidName,
            int slotIndex, boolean online, int modeOrdinal, long threshold, CoverEntry entry) {
            this.index = index;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
            this.targetName = targetName == null ? "" : targetName;
            this.icon = icon;
            this.fluidName = fluidName == null ? "" : fluidName;
            this.slotIndex = slotIndex;
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
            ByteBufUtils.writeItemStack(buf, row.icon);
            ByteBufUtils.writeUTF8String(buf, row.fluidName);
            buf.writeInt(row.slotIndex);
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
            ItemStack icon = ByteBufUtils.readItemStack(buf);
            String fluidName = ByteBufUtils.readUTF8String(buf);
            int slotIndex = buf.readInt();
            boolean online = buf.readBoolean();
            int modeOrdinal = buf.readInt();
            long threshold = buf.readLong();
            return new CoverRow(
                index,
                dim,
                x,
                y,
                z,
                side,
                targetName,
                icon,
                fluidName,
                slotIndex,
                online,
                modeOrdinal,
                threshold,
                null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CoverRow)) return false;
            CoverRow r = (CoverRow) o;
            return index == r.index && dim == r.dim
                && x == r.x
                && y == r.y
                && z == r.z
                && side == r.side
                && slotIndex == r.slotIndex
                && online == r.online
                && modeOrdinal == r.modeOrdinal
                && threshold == r.threshold
                && targetName.equals(r.targetName)
                && fluidName.equals(r.fluidName)
                && stackKey(icon).equals(stackKey(r.icon));
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
            result = 31 * result + stackKey(icon).hashCode();
            result = 31 * result + fluidName.hashCode();
            result = 31 * result + Integer.hashCode(slotIndex);
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

    /**
     * 类型中文名（行内与编辑面板共用）。
     *
     * <p>
     * **必须用前缀匹配**：GTNH 这条 AE2 线上 `LevelType` 的常量是 {@code ITEM_LEVEL} /
     * {@code ENERGY_LEVEL}（根本没有 {@code ITEM}/{@code FLUID}/{@code ENERGY}），
     * 3.21.0 用等值比较 ⇒ 永远落到"未知"（已用实例 jar `javap` 核对）。
     */
    private static String typeLabel(String typeName) {
        if (typeName == null) return StatCollector.translateToLocal("ae2_qof.terminal.type.unknown");
        if (typeName.startsWith("ITEM")) return StatCollector.translateToLocal("ae2_qof.terminal.type.item");
        if (typeName.startsWith("FLUID")) return StatCollector.translateToLocal("ae2_qof.terminal.type.fluid");
        if (typeName.startsWith("ENERGY")) return StatCollector.translateToLocal("ae2_qof.terminal.type.energy");
        return StatCollector.translateToLocal("ae2_qof.terminal.type.unknown");
    }

    /** 阈值模式中文名（枚举常量是 BELOW_THRESHOLD_RUN / ABOVE_THRESHOLD_RUN，序数 0 / 1）。 */
    private static String modeLabel(int ordinal) {
        boolean below = ThresholdMode.fromOrdinal(ordinal) == ThresholdMode.BELOW_THRESHOLD_RUN;
        return StatCollector.translateToLocal(below ? "ae2_qof.terminal.mode.below" : "ae2_qof.terminal.mode.above");
    }

    private static String levelTypeName(PartLevelEmitter emitter) {
        try {
            Object type = emitter.getConfigManager()
                .getSetting(Settings.LEVEL_TYPE);
            if (type instanceof LevelType) return ((LevelType) type).name();
        } catch (Throwable ignored) {}
        return "";
    }

    private static StockMonitorCover locateCover(CoverEntry entry) {
        try {
            net.minecraft.world.World world = net.minecraftforge.common.DimensionManager.getWorld(entry.dim);
            if (world == null) return null;
            if (!world.blockExists(entry.x, entry.y, entry.z)) return null;
            TileEntity te = world.getTileEntity(entry.x, entry.y, entry.z);
            if (te instanceof gregtech.api.interfaces.tileentity.ICoverable) {
                gregtech.api.interfaces.tileentity.ICoverable coverable = (gregtech.api.interfaces.tileentity.ICoverable) te;
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

    private static String formatNumber(long n) {
        if (n >= 1e15) return String.format("%.1fP", n / 1e15);
        if (n >= 1e12) return String.format("%.1fT", n / 1e12);
        if (n >= 1e9) return String.format("%.1fG", n / 1e9);
        if (n >= 1e6) return String.format("%.1fM", n / 1e6);
        if (n >= 1e3) return String.format("%.1fK", n / 1e3);
        return String.valueOf(n);
    }
}
