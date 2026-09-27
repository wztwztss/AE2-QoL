/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.gtnewhorizons.modularui.api.ModularUITextures;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.Text;
import com.gtnewhorizons.modularui.api.drawable.shapes.Rectangle;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.gtnewhorizons.modularui.common.widget.textfield.TextFieldWidget;

import com.wztwzt.ae2_qof.wildport.compat.GTCompat;
import com.wztwzt.ae2_qof.wildport.compat.NechSearchCompat;
import com.wztwzt.ae2_qof.wildport.crafting.WildcardPatternEntry;
import com.wztwzt.ae2_qof.wildport.crafting.WildcardPatternGenerator;
import com.wztwzt.ae2_qof.wildport.item.WildcardPatternConfig;
import com.wztwzt.ae2_qof.wildport.item.WildcardPatternState;
import com.wztwzt.ae2_qof.wildport.network.MessageUpdateWildcardConfig;
import com.wztwzt.ae2_qof.wildport.network.WildcardNetwork;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

public final class WildcardPatternWindow {

    private static final int GUI_WIDTH = 452;
    private static final int GUI_HEIGHT = 292;
    // 3.34.0：**撤掉** 3.33.0 的底部「内置电路」带 —— 电路改为独立页（用户拍板），窗口高度回到 292。
    private static final int RULE_ROWS = 9;
    /** 3.34.0：不消耗物品页每页行数（与排除页同一套分页习惯）。 */
    private static final int NON_CONSUMED_LINES = 8;
    private static final int PREVIEW_LINES = 12;
    private static final int DEDUPE_LINES = 4;
    private static final int EXCLUDE_LINES = 9;
    private static final int ENTRY_TEXT_WIDTH = 50;
    private static final int ENTRY_MODE_X = ENTRY_TEXT_WIDTH + 3;
    private static final int ENTRY_MODE_WIDTH = 34;
    private static final int ENTRY_AMOUNT_X = ENTRY_MODE_X + ENTRY_MODE_WIDTH + 3;
    private static final int ENTRY_AMOUNT_WIDTH = 40;

    private static final int BACKGROUND_COLOR = 0xFFF0F0F0;
    private static final int PANEL_COLOR = 0xF2C6C6C6;
    private static final int PANEL_LINE_DARK = 0xFF4E4E4E;
    private static final int PANEL_LINE_LIGHT = 0xFFE5E5E5;
    private static final int FIELD_COLOR = 0xFF4A4D50;
    private static final int FIELD_LINE_DARK = 0xFF3A3D40;
    private static final int FIELD_LINE_LIGHT = 0xFFE8E8E8;
    private static final int FIELD_TEXT_COLOR = 0xFFFFFFFF;
    private static final int BUTTON_TEXT_COLOR = 0xFF222222;
    private static final int PANEL_SHADOW_COLOR = 0x22000000;
    private static final int CARD_FILL_COLOR = 0x66FFFFFF;
    private static final int CARD_SHADOW_COLOR = 0x16000000;

    private WildcardPatternWindow() {}

    /**
     * 当前打开的 Wild 窗口（3.34.0）。
     *
     * <p>为什么需要：NEI 加号必须**就地**把推导结果写进这个窗口的内存态。旧实现只发了个包 + 聊天栏
     * "关掉重开"，于是 (a) 界面当场看不到任何变化，(b) 窗口内存态还是旧的，用户随后保存/关闭时
     * 用旧内存态把刚写进去的规则**覆盖成 0 条**（3.33.0 日志指纹：14:57:30 写回 rules=1 → 14:57:32
     * 窗口保存触发拉回得到"规则 0 条"）。
     *
     * <p>只在客户端登记（服务端线程同样会构建窗口；SP 下两者同 JVM，不隔离就会互相覆盖）。
     */
    private static WindowState ACTIVE_STATE;
    private static ModularWindow ACTIVE_WINDOW;

    public static ModularWindow createWindow(UIBuildContext buildContext, EntityPlayer player, int slot) {
        WindowState state = new WindowState(player, slot);
        ModularWindow.Builder builder = ModularWindow.builder(GUI_WIDTH, GUI_HEIGHT);
        builder.setBackground(ModularUITextures.VANILLA_BACKGROUND);

        addHeader(builder, state);
        addPageTabs(builder, state);
        addMainPage(builder, state);
        addPreviewPage(builder, state);
        addExcludePage(builder, state);
        addDedupePage(builder, state);
        // 3.34.0：电路与不消耗物品各自成页（用户拍板），页签在顶部右侧
        addCircuitPage(builder, state);
        addNonConsumedPage(builder, state);

        ModularWindow window = builder.build();
        if (player != null && player.worldObj != null && player.worldObj.isRemote) {
            ACTIVE_STATE = state;
            ACTIVE_WINDOW = window;
        }
        return window;
    }

    /**
     * NEI 加号（3.34.0）：把推导结果**就地**写进当前 Wild 窗口的 9 行并立即持久化。
     *
     * @return true = 本窗口接收并处理了（调用方不要再走"发个包让用户关掉重开"的旧路径）
     */
    public static boolean applyDerivedFromNei(
        ModularWindow window,
        com.wztwzt.ae2_qof.client.SmartWildcardRecipeDeriver.Result derived) {
        if (window == null || derived == null || !derived.ok) return false;
        WindowState state = ACTIVE_STATE;
        if (state == null || ACTIVE_WINDOW != window) return false;
        try {
            ItemStack held = state.getHeldStack();
            if (!com.wztwzt.ae2_qof.wildcard.SmartWildcardGate.isOurs(held)) {
                com.wztwzt.ae2_qof.MyMod.LOG
                    .warn("[AE2QoL] NEI 加号：槽位 {} 里不是我们的通配样板，未写入", state.slot);
                return false;
            }
            // ① 借用桥的 NBT 往返把"我们的状态"翻成"Wild 的 entry 列表"，再整页替换界面内存行
            ItemStack tmp = held.copy();
            com.wztwzt.ae2_qof.wildcard.SmartWildcardState ours = derived.state;
            ours.write(tmp);
            NBTTagCompound tmpTag = tmp.getTagCompound();
            if (tmpTag != null) {
                if (derived.templateIn != null && !derived.templateIn.isEmpty()) {
                    tmpTag.setTag("in", com.wztwzt.ae2_qof.network.SmartWildcardRulesPacket.buildList(derived.templateIn));
                    tmpTag.setBoolean("crafting", false);
                }
                if (derived.templateOut != null && !derived.templateOut.isEmpty()) {
                    tmpTag.setTag("out", com.wztwzt.ae2_qof.network.SmartWildcardRulesPacket.buildList(derived.templateOut));
                }
            }
            com.wztwzt.ae2_qof.wildport.bridge.WildcardBridge.pushToWild(tmp, ours);

            List<WildcardPatternEntry> ins = new ArrayList<>(WildcardPatternState.getInputEntries(tmp));
            List<WildcardPatternEntry> outs = new ArrayList<>(WildcardPatternState.getOutputEntries(tmp));
            ensureSize(ins, RULE_ROWS);
            ensureSize(outs, RULE_ROWS);
            state.setRows(ins, outs);

            // ② 立刻持久化：规则 + 原生 in/out 走既有包（**带槽位**，避免写到背包里另一张样板）
            com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL.sendToServer(
                new com.wztwzt.ae2_qof.network.SmartWildcardRulesPacket(
                    ours,
                    derived.templateIn,
                    derived.templateOut,
                    state.slot));
            // ③ Wild 自己的键也写一遍（服务端侧由 MessageUpdateWildcardConfig 应用并同步我们的子树）
            state.save();
            com.wztwzt.ae2_qof.MyMod.LOG.info(
                "[AE2QoL] NEI 加号：已就地把推导结果写进 Wild 窗口（槽位 {}，{}）",
                state.slot,
                derived.summary);
            state.chat("\u00a7a[AE2QoL] \u5df2\u6309 NEI \u914d\u65b9\u586b\u5165\u754c\u9762\u5e76\u5199\u56de\u6837\u677f");
            return true;
        } catch (Throwable t) {
            com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] NEI 加号就地写入失败（回退到旧路径）", t);
            return false;
        }
    }

    /**
     * 3.34.0：**电路页**（用户拍板：电路从底部带改为独立页；1~24 按 4 列 × 6 行重排）。
     *
     * <p>数据仍写进**我们自己的**子树（{@code SmartWildcardState.circuit}），走既有
     * {@code SmartWildcardRulesPacket}（带槽位）整包发回服务端，**不新增包、不新增通道**。
     * 点完当场高亮（按钮背景 supplier 读实时状态）——不再是"关掉重开才能看到"。
     */
    private static void addCircuitPage(ModularWindow.Builder builder, WindowState state) {
        addCircuitPanel(builder, state, 8, 28, 436, 262);
        addCircuitSeparator(builder, state, 18, 55, 406, 2);

        TextWidget title = new TextWidget("");
        title.setPos(18, 38);
        title.setStringSupplier(() -> EnumChatFormatting.BLACK + tr("gui.wildcardpattern.circuit_title") + "："
            + tr("gui.wildcardpattern.circuit_current")
            + " = "
            + (state.currentCircuit() >= 1 ? String.valueOf(state.currentCircuit())
                : tr("gui.wildcardpattern.circuit_inherit")));
        addCircuitWidget(builder, state, title);

        TextWidget tip1 = new TextWidget("");
        tip1.setPos(18, 68);
        tip1.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.circuit_tip1"));
        addCircuitWidget(builder, state, tip1);

        TextWidget tip2 = new TextWidget("");
        tip2.setPos(18, 81);
        tip2.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.circuit_tip2"));
        addCircuitWidget(builder, state, tip2);

        // 1~24：4 列 × 6 行（用户选定），按钮 90×22，列距 96，行距 26，起点 (18,104)
        for (int i = 1; i <= 24; i++) {
            final int circuit = i;
            ButtonWidget btn = button(
                () -> buttonBackground(
                    state.currentCircuit() == circuit,
                    String.valueOf(circuit),
                    state.currentCircuit() == circuit ? 0xFF1B4E8A : BUTTON_TEXT_COLOR));
            btn.setPos(18 + ((i - 1) % 4) * 96, 104 + ((i - 1) / 4) * 26);
            btn.setSize(90, 22);
            btn.setOnClick((clickData, widget) -> applyCircuit(state, circuit));
            addCircuitWidget(builder, state, btn);
        }

        ButtonWidget clear = button("gui.wildcardpattern.circuit_clear");
        clear.setPos(18, 264);
        clear.setSize(120, 18);
        clear.setOnClick((clickData, widget) -> applyCircuit(state, -1));
        addCircuitWidget(builder, state, clear);

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(382, 264);
        back.setSize(68, 18);
        back.setOnClick((clickData, widget) -> state.closeExtraPages());
        addCircuitWidget(builder, state, back);
    }

    /**
     * 3.34.0：**不消耗物品页**（用户拍板：NEI 拖入 + 手持加入 + 逐行删除 + 即时写回，全部都要）。
     *
     * <p>拖动接入用的是搬进来的 Wild 自带控件 {@code WildcardFilterDropTextField}（它本就是为
     * GTNH-MUI 窗口写的 NEI 拖放接收器）；本模组另一个 {@code SmartWildcardNeiDragHandler} 只服务
     * Cleanroom MUI2 的那个编辑器，对本窗口**不生效**，所以不能复用。
     */
    private static void addNonConsumedPage(ModularWindow.Builder builder, WindowState state) {
        addNonConsumedPanel(builder, state, 8, 28, 436, 262);
        addNonConsumedSeparator(builder, state, 18, 55, 406, 2);

        TextWidget title = new TextWidget("");
        title.setPos(18, 38);
        title.setStringSupplier(
            () -> EnumChatFormatting.BLACK + tr("gui.wildcardpattern.nonconsumed_title") + "（"
                + state.getNonConsumedCount()
                + "）");
        addNonConsumedWidget(builder, state, title);

        TextWidget tip1 = new TextWidget("");
        tip1.setPos(18, 68);
        tip1.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.nonconsumed_tip1"));
        addNonConsumedWidget(builder, state, tip1);

        ButtonWidget addHeld = button("gui.wildcardpattern.nonconsumed_add_held");
        addHeld.setPos(18, 92);
        addHeld.setSize(110, 18);
        addHeld.setOnClick((clickData, widget) -> state.addHeldAsNonConsumed());
        addNonConsumedWidget(builder, state, addHeld);

        // NEI 拖入区：用搬运进来的 Wild 自带**物品**拖放框（WildcardEntryDropTextField 收的就是 ItemStack；
        // WildcardFilterDropTextField 是筛选框、只收 String，不能用在这里）。拖进来即刻记为不消耗。
        TextFieldWidget drop = new WildcardEntryDropTextField(stack -> state.addNonConsumed(stack));
        drop.setSynced(false, false);
        drop.setGetter(() -> tr("gui.wildcardpattern.nonconsumed_drop_hint"));
        drop.setSetter(value -> {});
        drop.setText(tr("gui.wildcardpattern.nonconsumed_drop_hint"));
        drop.setTextColor(FIELD_TEXT_COLOR);
        drop.setBackground(WildcardPatternWindow::fieldBackground);
        drop.setTextAlignment(Alignment.CenterLeft);
        drop.setMaxLength(64);
        drop.setPos(134, 92);
        drop.setSize(220, 18);
        addNonConsumedWidget(builder, state, drop);

        // 8 行/页：图标 + 名称 + 删
        for (int i = 0; i < NON_CONSUMED_LINES; i++) {
            final int lineIndex = i;
            int rowY = 132 + i * 19;

            DrawableWidget icon = new DrawableWidget();
            icon.setPos(18, rowY);
            icon.setSize(16, 16);
            icon.setDrawable(
                new com.gtnewhorizons.modularui.api.drawable.ItemDrawable(
                    () -> state.getNonConsumedRow(lineIndex)));
            addNonConsumedWidget(builder, state, icon);

            TextWidget name = new TextWidget("");
            name.setPos(40, rowY + 4);
            name.setScale(0.85f);
            name.setStringSupplier(() -> {
                ItemStack stack = state.getNonConsumedRow(lineIndex);
                return stack == null ? ""
                    : EnumChatFormatting.BLACK + trim(
                        stack.getDisplayName() + (stack.stackSize > 1 ? " x" + stack.stackSize : ""),
                        40);
            });
            addNonConsumedWidget(builder, state, name);

            ButtonWidget remove = button("gui.wildcardpattern.nonconsumed_remove");
            remove.setPos(382, rowY);
            remove.setSize(44, 16);
            addNonConsumedWidget(builder, state, remove);
            // 注意：addNonConsumedWidget 会设置"页码谓词"，这里必须**在它之后**把行存在性判据与页码一起写全，
            // 否则后者会覆盖前者（Widget.setEnabled 只保留最后一个谓词）。
            remove.setEnabled(widget -> state.nonConsumedPage && state.getNonConsumedRow(lineIndex) != null);
            remove.setOnClick((clickData, widget) -> state.removeNonConsumedAt(lineIndex));
        }

        ButtonWidget prev = button("<");
        prev.setPos(18, 252);
        prev.setSize(34, 17);
        prev.setOnClick((clickData, widget) -> {
            if (state.nonConsumedPageIndex > 0) state.nonConsumedPageIndex--;
        });
        addNonConsumedWidget(builder, state, prev);

        TextWidget page = new TextWidget("");
        page.setPos(58, 256);
        page.setStringSupplier(() -> EnumChatFormatting.BLACK + StatCollector
            .translateToLocalFormatted("gui.wildcardpattern.page", state.nonConsumedPageIndex + 1, state.getNonConsumedPageCount()));
        addNonConsumedWidget(builder, state, page);

        ButtonWidget next = button(">");
        next.setPos(110, 252);
        next.setSize(34, 17);
        next.setOnClick((clickData, widget) -> {
            if (state.nonConsumedPageIndex + 1 < state.getNonConsumedPageCount()) state.nonConsumedPageIndex++;
        });
        addNonConsumedWidget(builder, state, next);

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(382, 252);
        back.setSize(68, 17);
        back.setOnClick((clickData, widget) -> state.closeExtraPages());
        addNonConsumedWidget(builder, state, back);
    }

    /** 顶部右侧页签条（用户拍板：不放主页底部，改放窗口顶部右侧三个小按钮）。 */
    private static void addPageTabs(ModularWindow.Builder builder, WindowState state) {
        ButtonWidget home = button(
            () -> buttonBackground(!state.circuitPage && !state.nonConsumedPage, tr("gui.wildcardpattern.tab_home"), BUTTON_TEXT_COLOR));
        home.setPos(298, 5);
        home.setSize(46, 16);
        home.setOnClick((clickData, widget) -> state.closeExtraPages());
        builder.widget(home);

        ButtonWidget circuit = button(
            () -> buttonBackground(state.circuitPage, tr("gui.wildcardpattern.tab_circuit"), BUTTON_TEXT_COLOR));
        circuit.setPos(348, 5);
        circuit.setSize(46, 16);
        circuit.setOnClick((clickData, widget) -> state.openCircuitPage());
        builder.widget(circuit);

        ButtonWidget nonConsumed = button(
            () -> buttonBackground(state.nonConsumedPage, tr("gui.wildcardpattern.tab_nonconsumed"), BUTTON_TEXT_COLOR));
        nonConsumed.setPos(398, 5);
        nonConsumed.setSize(52, 16);
        nonConsumed.setOnClick((clickData, widget) -> state.openNonConsumedPage());
        builder.widget(nonConsumed);
    }

    /**
     * 电路页点号：写进我们的子树并**立刻**发回服务端（唯一写路径见 {@code WindowState.pushOurState}）。
     * 页面上的"当前 = N"与选中高亮都读窗口内的缓存值，写完立即更新 ⇒ 当场可见。
     */
    private static void applyCircuit(WindowState state, int circuit) {
        state.applyCircuitValue(circuit);
    }

    private static void addHeader(ModularWindow.Builder builder, WindowState state) {
        TextWidget title = new TextWidget("");
        title.setPos(10, 9);
        title.setScale(0.95f);
        title.setStringSupplier(() -> EnumChatFormatting.BLACK + "" + EnumChatFormatting.BOLD
            + tr(state.circuitPage ? "gui.wildcardpattern.circuit_title"
                : state.nonConsumedPage ? "gui.wildcardpattern.nonconsumed_title"
                    : state.dedupePage
                        ? "gui.wildcardpattern.dedupe_page"
                        : state.excludePage
                            ? "gui.wildcardpattern.exclude_page"
                            : state.previewPage ? "gui.wildcardpattern.preview_page" : "gui.wildcardpattern.title"));
        builder.widget(title);

        TextWidget hint = new TextWidget("");
        // 3.34.0：宽度收到 160 —— 右上角要留给三个页签按钮（298 / 348 / 398）
        hint.setPos(132, 11);
        hint.setSize(160, 10);
        hint.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY
            + tr(state.circuitPage ? "gui.wildcardpattern.circuit_hint"
                : state.nonConsumedPage ? "gui.wildcardpattern.nonconsumed_hint"
                    : state.dedupePage
                        ? "gui.wildcardpattern.dedupe_hint"
                        : state.excludePage
                            ? "gui.wildcardpattern.exclude_hint"
                            : state.previewPage ? "gui.wildcardpattern.preview_hint" : "gui.wildcardpattern.drag_hint"));
        builder.widget(hint);
    }

    private static void addMainPage(ModularWindow.Builder builder, WindowState state) {
        addPanel(builder, state, 8, 28, 436, 215, false);
        addRulesHeader(builder, state, 12, 40);
        addSeparator(builder, state, 12, 53, 424, 2, false);

        List<EntryCellRefs> refs = new ArrayList<>();
        for (int row = 0; row < RULE_ROWS; row++) {
            addRuleRow(builder, state, refs, row, 12, 58 + row * 17);
        }
        addSeparator(builder, state, 12, 213, 424, 2, false);

        addGlobalExclude(builder, state, 8, 248);
        addRuleExcludeEditor(builder, state, 164, 248);

        ButtonWidget clearAll = button("gui.wildcardpattern.clear");
        clearAll.setPos(306, 248);
        clearAll.setSize(68, 18);
        clearAll.setOnClick((clickData, widget) -> {
            clearEntries(state.inputs);
            clearEntries(state.outputs);
            state.globalExclude = "";
            clearStrings(state.ruleIncludes);
            clearStrings(state.ruleExcludes);
            for (EntryCellRefs ref : refs) {
                ref.clear();
            }
            state.refreshActivePage();
        });
        addMainWidget(builder, state, clearAll);

        ButtonWidget dedupe = button("gui.wildcardpattern.dedupe");
        dedupe.setPos(382, 248);
        dedupe.setSize(68, 18);
        dedupe.setOnClick((clickData, widget) -> state.openDedupe());
        addMainWidget(builder, state, dedupe);

        ButtonWidget previewAll = button("gui.wildcardpattern.preview_all");
        previewAll.setPos(306, 270);
        previewAll.setSize(68, 18);
        previewAll.setOnClick((clickData, widget) -> state.openPreview(-1));
        addMainWidget(builder, state, previewAll);

        ButtonWidget save = button("gui.wildcardpattern.save");
        save.setPos(382, 270);
        save.setSize(68, 18);
        save.setOnClick((clickData, widget) -> {
            state.save();
            widget.getWindow().closeWindow();
        });
        addMainWidget(builder, state, save);
    }

    private static void addRulesHeader(ModularWindow.Builder builder, WindowState state, int x, int y) {
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + "#", x + 4, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_input"), x + 26, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_mode"), x + 82, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_amount"), x + 114, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_output"), x + 158, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_mode"), x + 214, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.col_amount"), x + 246, y);
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.actions"), x + 302, y);
    }

    private static void addRuleRow(
        ModularWindow.Builder builder,
        WindowState state,
        List<EntryCellRefs> refs,
        int row,
        int x,
        int y) {
        addMainText(builder, state, EnumChatFormatting.DARK_GRAY + String.valueOf(row + 1), x + 4, y + 4);

        EntryCellRefs input = addEntryCell(builder, state, state.inputs, row, x + 22, y);
        EntryCellRefs output = addEntryCell(builder, state, state.outputs, row, x + 154, y);
        refs.add(input);
        refs.add(output);
        // 3.34.0：把行内控件引用也登记到 state —— NEI 加号要"就地刷新"这 9 行（构建期 setText 只写一次）
        state.rowRefs.add(input);
        state.rowRefs.add(output);

        TextWidget arrow = new TextWidget(EnumChatFormatting.BLACK + ">");
        arrow.setPos(x + 146, y + 4);
        addMainWidget(builder, state, arrow);

        ButtonWidget preview = button("gui.wildcardpattern.preview_short");
        preview.setPos(x + 292, y);
        preview.setSize(22, 15);
        preview.setOnClick((clickData, widget) -> state.openPreview(row));
        addMainWidget(builder, state, preview);

        ButtonWidget filter = button(
            () -> buttonBackground(
                state.selectedRule == row,
                tr("gui.wildcardpattern.filter_short"),
                state.selectedRule == row ? 0xFF1B4E8A : BUTTON_TEXT_COLOR));
        filter.setPos(x + 318, y);
        filter.setSize(22, 15);
        filter.setOnClick((clickData, widget) -> {
            state.selectedRule = row;
            state.refreshActivePage();
        });
        addMainWidget(builder, state, filter);

        ButtonWidget multiply = button("gui.wildcardpattern.multiply_short");
        multiply.setPos(x + 344, y);
        multiply.setSize(28, 15);
        multiply.setOnClick((clickData, widget) -> {
            if (clickData.mouseButton == 1) {
                state.divideRule(row);
            } else {
                state.multiplyRule(row);
            }
            input.updateAmount();
            output.updateAmount();
        });
        addMainWidget(builder, state, multiply);

        ButtonWidget clear = button("gui.wildcardpattern.clear_short");
        clear.setPos(x + 376, y);
        clear.setSize(28, 15);
        clear.setOnClick((clickData, widget) -> {
            state.inputs.set(row, WildcardPatternEntry.fromStack(null));
            state.outputs.set(row, WildcardPatternEntry.fromStack(null));
            state.ruleIncludes.set(row, "");
            state.ruleExcludes.set(row, "");
            input.clear();
            output.clear();
            state.refreshActivePage();
        });
        addMainWidget(builder, state, clear);
    }

    private static EntryCellRefs addEntryCell(
        ModularWindow.Builder builder,
        WindowState state,
        List<WildcardPatternEntry> entries,
        int index,
        int x,
        int y) {
        final boolean[] suppressNextSetter = { false };
        final WildcardEntryDropTextField[] textRef = new WildcardEntryDropTextField[1];
        WildcardEntryDropTextField text = new WildcardEntryDropTextField(stack -> {
            WildcardPatternEntry next = WildcardPatternEntry.fromStack(stack);
            if (next.canOreDict()) {
                next.convertToOreDict();
            } else {
                next.convertToItem();
            }
            entries.set(index, next);
            state.refreshActivePage();
            if (textRef[0] != null) {
                suppressNextSetter[0] = true;
                textRef[0].setText(trim(next.getLabel(), 11));
                textRef[0].markForUpdate();
            }
        });
        textRef[0] = text;
        text.setSynced(false, false);
        text.setSetter(value -> {
            if (suppressNextSetter[0]) {
                suppressNextSetter[0] = false;
                return;
            }
            WildcardPatternEntry entry = entries.get(index);
            if (!entry.isOreDict() && entry.isEmpty() && WildcardPatternEntry.looksLikeOreDictPattern(value)) {
                entry.convertToOreDict();
            }
            if (entry.isOreDict()) {
                entry.setOreNameOrPrefix(value);
            } else {
                entry.setMatcher(value);
            }
            state.refreshActivePage();
        });
        text.setText(entries.get(index).isEmpty() ? "" : trim(entries.get(index).getLabel(), 11));
        text.setTextColor(FIELD_TEXT_COLOR);
        text.setBackground(WildcardPatternWindow::fieldBackground);
        text.setTextAlignment(Alignment.CenterLeft);
        text.setMaxLength(80);
        text.setPos(x, y);
        text.setSize(ENTRY_TEXT_WIDTH, 16);
        addMainWidget(builder, state, text);

        ButtonWidget mode = button(
            () -> buttonBackground(
                entries.get(index).isOreDict(),
                tr(entries.get(index).isOreDict() ? "gui.wildcardpattern.mode_oredict" : "gui.wildcardpattern.mode_name"),
                BUTTON_TEXT_COLOR));
        mode.setPos(x + ENTRY_MODE_X, y);
        mode.setSize(ENTRY_MODE_WIDTH, 16);
        mode.setOnClick((clickData, widget) -> {
            if (clickData.mouseButton == 0 && !clickData.doubleClick) {
                switchEntryMode(entries, index, text, state, suppressNextSetter);
            }
        });
        addMainWidget(builder, state, mode);

        TextFieldWidget amount = new TextFieldWidget();
        amount.setSynced(false, false);
        amount.setSetter(value -> {
            entries.get(index).setAmount(parseAmount(value));
            state.refreshActivePage();
        });
        amount.setText(formatAmount(entries.get(index).getAmountLong()));
        amount.setTextColor(FIELD_TEXT_COLOR);
        amount.setBackground(WildcardPatternWindow::fieldBackground);
        amount.setTextAlignment(Alignment.Center);
        amount.setMaxLength(8);
        amount.setPos(x + ENTRY_AMOUNT_X, y);
        amount.setSize(ENTRY_AMOUNT_WIDTH, 16);
        addMainWidget(builder, state, amount);

        return new EntryCellRefs(text, amount, () -> formatAmount(entries.get(index).getAmountLong()));
    }

    private static void switchEntryMode(
        List<WildcardPatternEntry> entries,
        int index,
        WildcardEntryDropTextField text,
        WindowState state,
        boolean[] suppressNextSetter) {
        WildcardPatternEntry entry = entries.get(index);
        if (entry.isOreDict()) {
            entry.convertToItem();
        } else {
            entry.convertToOreDict();
        }
        suppressNextSetter[0] = true;
        text.setText(trim(entry.getLabel(), 11));
        text.markForUpdate();
        state.refreshActivePage();
    }

    private static void addGlobalExclude(ModularWindow.Builder builder, WindowState state, int x, int y) {
        addPanel(builder, state, x, y, 148, 40, false);
        addMainText(builder, state, EnumChatFormatting.BLACK + tr("gui.wildcardpattern.global_exclude"), x + 12, y + 10);
        TextWidget summary = new TextWidget("");
        summary.setPos(x + 12, y + 24);
        summary.setScale(0.72f);
        summary.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + trim(state.getExcludeSummary(-1), 24));
        addMainWidget(builder, state, summary);

        ButtonWidget edit = button("gui.wildcardpattern.exclude_short");
        edit.setPos(x + 96, y + 8);
        edit.setSize(42, 18);
        edit.setOnClick((clickData, widget) -> state.openExcludeEditor(-1));
        addMainWidget(builder, state, edit);
    }

    private static void addRuleExcludeEditor(ModularWindow.Builder builder, WindowState state, int x, int y) {
        addPanel(builder, state, x, y, 138, 40, false);
        TextWidget label = new TextWidget("");
        label.setPos(x + 8, y + 10);
        label.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted("gui.wildcardpattern.rule_exclude", state.selectedRule + 1));
        addMainWidget(builder, state, label);
        TextWidget summary = new TextWidget("");
        summary.setPos(x + 8, y + 24);
        summary.setScale(0.72f);
        summary.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + trim(state.getExcludeSummary(state.selectedRule), 23));
        addMainWidget(builder, state, summary);

        ButtonWidget edit = button("gui.wildcardpattern.exclude_short");
        edit.setPos(x + 88, y + 8);
        edit.setSize(42, 18);
        edit.setOnClick((clickData, widget) -> state.openExcludeEditor(state.selectedRule));
        addMainWidget(builder, state, edit);
    }

    private static void addPreviewPage(ModularWindow.Builder builder, WindowState state) {
        addPanel(builder, state, 8, 28, 436, 262, true);
        addSeparator(builder, state, 18, 81, 406, 2, true);

        TextWidget source = new TextWidget("");
        source.setPos(18, 38);
        source.setStringSupplier(() -> EnumChatFormatting.BLACK + state.getPreviewTitle());
        addPreviewWidget(builder, state, source);

        addPreviewTextField(
            builder,
            state,
            EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.search"),
            () -> state.previewSearch,
            value -> {
                state.previewSearch = value == null ? "" : value;
                state.previewPageIndex = 0;
                state.refreshActivePage();
            },
            192,
            34,
            240,
            true);

        if (state.previewRule >= 0) {
            addPreviewTextField(
                builder,
                state,
                EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.include_short"),
                () -> state.ruleIncludes.get(state.previewRule),
                value -> {
                    state.ruleIncludes.set(state.previewRule, value == null ? "" : value);
                    state.previewPageIndex = 0;
                    state.refreshActivePage();
                },
                18,
                58,
                206,
                true);

            ButtonWidget editExclude = button("gui.wildcardpattern.exclude_short");
            editExclude.setPos(384, 58);
            editExclude.setSize(48, 18);
            editExclude.setOnClick((clickData, widget) -> state.openExcludeEditor(state.previewRule));
            addPreviewWidget(builder, state, editExclude);

            TextWidget excludeSummary = new TextWidget("");
            excludeSummary.setPos(226, 62);
            excludeSummary.setScale(0.72f);
            excludeSummary.setStringSupplier(
                () -> EnumChatFormatting.DARK_GRAY
                    + tr("gui.wildcardpattern.exclude_short")
                    + ": "
                    + trim(state.getExcludeSummary(state.previewRule), 18));
            addPreviewWidget(builder, state, excludeSummary);
        }

        for (int i = 0; i < PREVIEW_LINES; i++) {
            final int lineIndex = i;
            TextWidget line = new TextWidget("");
            line.setPos(18, 88 + i * 14);
            line.setStringSupplier(() -> {
                PreviewRow row = state.getPreviewRow(lineIndex);
                return row == null ? "" : EnumChatFormatting.DARK_GRAY + row.line;
            });
            addPreviewWidget(builder, state, line);

            ButtonWidget exclude = button("gui.wildcardpattern.exclude_short");
            exclude.setPos(388, 86 + i * 14);
            exclude.setSize(38, 12);
            exclude.setOnClick((clickData, widget) -> state.excludePreviewRow(lineIndex));
            addPreviewWidget(builder, state, exclude);
        }

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(18, 264);
        back.setSize(64, 17);
        back.setOnClick((clickData, widget) -> state.previewPage = false);
        addPreviewWidget(builder, state, back);

        ButtonWidget prev = button("<");
        prev.setPos(182, 264);
        prev.setSize(34, 17);
        prev.setOnClick((clickData, widget) -> {
            if (state.previewPageIndex > 0) {
                state.previewPageIndex--;
            }
        });
        addPreviewWidget(builder, state, prev);

        TextWidget page = new TextWidget("");
        page.setPos(228, 268);
        page.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted(
                "gui.wildcardpattern.page",
                Integer.valueOf(state.previewPageIndex + 1),
                Integer.valueOf(Math.max(1, state.getPreviewPageCount()))));
        addPreviewWidget(builder, state, page);

        ButtonWidget next = button(">");
        next.setPos(304, 264);
        next.setSize(34, 17);
        next.setOnClick((clickData, widget) -> {
            if (state.previewPageIndex + 1 < state.getPreviewPageCount()) {
                state.previewPageIndex++;
            }
        });
        addPreviewWidget(builder, state, next);
    }

    private static void addExcludePage(ModularWindow.Builder builder, WindowState state) {
        addExcludePanel(builder, state, 8, 28, 436, 262);
        addExcludeSeparator(builder, state, 18, 55, 406, 2);

        TextWidget title = new TextWidget("");
        title.setPos(18, 38);
        title.setStringSupplier(() -> EnumChatFormatting.BLACK + state.getExcludeTitle());
        addExcludeWidget(builder, state, title);

        TextWidget tip1 = new TextWidget("");
        tip1.setPos(18, 68);
        tip1.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.exclude_tip1"));
        addExcludeWidget(builder, state, tip1);

        TextWidget tip2 = new TextWidget("");
        tip2.setPos(18, 81);
        tip2.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.exclude_tip2"));
        addExcludeWidget(builder, state, tip2);

        TextFieldWidget field = new WildcardFilterDropTextField(value -> state.excludeDraft = value == null ? "" : value)
            .setOnEnter(state::addCurrentExcludeDraft);
        field.setSynced(false, false);
        field.setGetter(() -> state.excludeDraft);
        field.setSetter(value -> state.excludeDraft = value == null ? "" : value);
        field.setText(state.excludeDraft);
        field.setTextColor(FIELD_TEXT_COLOR);
        field.setBackground(WildcardPatternWindow::fieldBackground);
        field.setTextAlignment(Alignment.CenterLeft);
        field.setMaxLength(256);
        field.setPos(18, 104);
        field.setSize(330, 18);
        addExcludeWidget(builder, state, field);

        ButtonWidget add = button("+");
        add.setPos(356, 104);
        add.setSize(68, 18);
        add.setOnClick((clickData, widget) -> state.addCurrentExcludeDraft());
        addExcludeWidget(builder, state, add);

        TextWidget current = new TextWidget("");
        current.setPos(18, 132);
        current.setScale(0.78f);
        current.setStringSupplier(() -> EnumChatFormatting.BLACK + tr("gui.wildcardpattern.exclude_current"));
        addExcludeWidget(builder, state, current);

        for (int i = 0; i < EXCLUDE_LINES; i++) {
            final int lineIndex = i;
            TextWidget line = new TextWidget("");
            line.setPos(18, 144 + i * 14);
            line.setScale(0.72f);
            line.setStringSupplier(() -> {
                String token = state.getCurrentExcludeToken(lineIndex);
                return token.isEmpty() ? "" : EnumChatFormatting.DARK_GRAY + "- " + trimMiddle(token, 44);
            });
            addExcludeWidget(builder, state, line);

            ButtonWidget delete = button("X");
            delete.setPos(404, 142 + i * 14);
            delete.setSize(20, 12);
            delete.setOnClick((clickData, widget) -> state.removeCurrentExcludeToken(lineIndex));
            addExcludeWidget(builder, state, delete);
        }

        ButtonWidget prev = button("<");
        prev.setPos(18, 264);
        prev.setSize(34, 17);
        prev.setOnClick((clickData, widget) -> {
            if (state.excludePageIndex > 0) {
                state.excludePageIndex--;
            }
        });
        addExcludeWidget(builder, state, prev);

        TextWidget page = new TextWidget("");
        page.setPos(62, 268);
        page.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted(
                "gui.wildcardpattern.page",
                Integer.valueOf(state.excludePageIndex + 1),
                Integer.valueOf(Math.max(1, state.getExcludePageCount()))));
        addExcludeWidget(builder, state, page);

        ButtonWidget next = button(">");
        next.setPos(138, 264);
        next.setSize(34, 17);
        next.setOnClick((clickData, widget) -> {
            if (state.excludePageIndex + 1 < state.getExcludePageCount()) {
                state.excludePageIndex++;
            }
        });
        addExcludeWidget(builder, state, next);

        ButtonWidget clear = button("gui.wildcardpattern.clear");
        clear.setPos(286, 264);
        clear.setSize(64, 17);
        clear.setOnClick((clickData, widget) -> {
            state.excludeDraft = "";
            state.setCurrentExcludeValue("");
        });
        addExcludeWidget(builder, state, clear);

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(360, 264);
        back.setSize(64, 17);
        back.setOnClick((clickData, widget) -> state.closeExcludeEditor());
        addExcludeWidget(builder, state, back);
    }

    private static void addDedupePage(ModularWindow.Builder builder, WindowState state) {
        addDedupePanel(builder, state, 8, 28, 436, 262);
        addDedupeSeparator(builder, state, 18, 55, 406, 2);

        TextWidget title = new TextWidget("");
        title.setPos(18, 38);
        title.setStringSupplier(() -> EnumChatFormatting.BLACK + tr("gui.wildcardpattern.dedupe_page"));
        addDedupeWidget(builder, state, title);

        TextWidget hint = new TextWidget("");
        hint.setPos(18, 64);
        hint.setStringSupplier(() -> EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.dedupe_hint"));
        addDedupeWidget(builder, state, hint);

        addDedupeTextField(
            builder,
            state,
            EnumChatFormatting.DARK_GRAY + tr("gui.wildcardpattern.search"),
            () -> state.dedupeSearch,
            value -> {
                state.dedupeSearch = value == null ? "" : value;
                state.dedupePageIndex = 0;
                state.rebuildDedupe();
            },
            18,
            76,
            250);

        for (int i = 0; i < DEDUPE_LINES; i++) {
            final int lineIndex = i;
            int rowY = 98 + i * 39;
            addDedupeCard(builder, state, 14, rowY - 2, 418, 33);

            TextWidget rule = new TextWidget("");
            rule.setPos(18, rowY + 2);
            rule.setStringSupplier(() -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                return row != null
                    ? row.getTopColor() + "R" + (row.rule + 1)
                    : "";
            });
            addDedupeWidget(builder, state, rule);

            TextWidget inputOre = new TextWidget("");
            inputOre.setPos(42, rowY + 2);
            inputOre.setScale(0.74f);
            inputOre.setStringSupplier(() -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                return row != null
                    ? row.getTopColor() + trimMiddle(row.getInputLine(), 26)
                    : "";
            });
            addDedupeWidget(builder, state, inputOre);

            TextWidget topArrow = new TextWidget(EnumChatFormatting.BLACK + "->");
            topArrow.setPos(174, rowY + 2);
            addDedupeWidget(builder, state, topArrow);

            TextWidget outputOre = new TextWidget("");
            outputOre.setPos(194, rowY + 2);
            outputOre.setScale(0.74f);
            outputOre.setStringSupplier(() -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                return row != null
                    ? row.getTopColor() + trimMiddle(row.getOutputLine(), 26)
                    : "";
            });
            addDedupeWidget(builder, state, outputOre);

            TextWidget inputChoice = new TextWidget("");
            inputChoice.setPos(42, rowY + 17);
            inputChoice.setScale(0.7f);
            inputChoice.setStringSupplier(() -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                return row != null
                    ? row.getDetailColor() + trimMiddle(row.getInputChoiceLine(state), 28)
                    : "";
            });
            addDedupeWidget(builder, state, inputChoice);

            TextWidget bottomArrow = new TextWidget(EnumChatFormatting.DARK_GRAY + "->");
            bottomArrow.setPos(174, rowY + 17);
            addDedupeWidget(builder, state, bottomArrow);

            TextWidget outputChoice = new TextWidget("");
            outputChoice.setPos(194, rowY + 17);
            outputChoice.setScale(0.7f);
            outputChoice.setStringSupplier(() -> {
                DedupeRow row = state.getDedupeRow(lineIndex);
                return row != null
                    ? row.getDetailColor() + trimMiddle(row.getOutputChoiceLine(state), 28)
                    : "";
            });
            addDedupeWidget(builder, state, outputChoice);

            ButtonWidget cycleInput = button("gui.wildcardpattern.dedupe_input");
            cycleInput.setPos(322, rowY + 3);
            cycleInput.setSize(50, 18);
            cycleInput.setOnClick((clickData, widget) -> {
                int absolute = state.dedupePageIndex * DEDUPE_LINES + lineIndex;
                if (absolute < state.dedupeRows.size()) {
                    state.cycleDedupeChoice(state.dedupeRows.get(absolute).inputOreName);
                }
            });
            cycleInput.setEnabled(widget -> state.dedupePage
                && state.getDedupeRow(lineIndex) != null
                && state.getDedupeRow(lineIndex).inputDuplicate);
            addDedupeWidget(builder, state, cycleInput);

            ButtonWidget cycleOutput = button("gui.wildcardpattern.dedupe_output");
            cycleOutput.setPos(378, rowY + 3);
            cycleOutput.setSize(50, 18);
            cycleOutput.setOnClick((clickData, widget) -> {
                int absolute = state.dedupePageIndex * DEDUPE_LINES + lineIndex;
                if (absolute < state.dedupeRows.size()) {
                    state.cycleDedupeChoice(state.dedupeRows.get(absolute).outputOreName);
                }
            });
            cycleOutput.setEnabled(widget -> state.dedupePage
                && state.getDedupeRow(lineIndex) != null
                && state.getDedupeRow(lineIndex).outputDuplicate);
            addDedupeWidget(builder, state, cycleOutput);
        }

        ButtonWidget back = button("gui.wildcardpattern.back");
        back.setPos(18, 264);
        back.setSize(64, 17);
        back.setOnClick((clickData, widget) -> state.dedupePage = false);
        addDedupeWidget(builder, state, back);

        ButtonWidget prev = button("<");
        prev.setPos(182, 264);
        prev.setSize(34, 17);
        prev.setOnClick((clickData, widget) -> {
            if (state.dedupePageIndex > 0) {
                state.dedupePageIndex--;
            }
        });
        addDedupeWidget(builder, state, prev);

        TextWidget page = new TextWidget("");
        page.setPos(228, 268);
        page.setStringSupplier(() -> EnumChatFormatting.BLACK
            + StatCollector.translateToLocalFormatted(
                "gui.wildcardpattern.page",
                Integer.valueOf(state.dedupePageIndex + 1),
                Integer.valueOf(Math.max(1, state.getDedupePageCount()))));
        addDedupeWidget(builder, state, page);

        ButtonWidget next = button(">");
        next.setPos(304, 264);
        next.setSize(34, 17);
        next.setOnClick((clickData, widget) -> {
            if (state.dedupePageIndex + 1 < state.getDedupePageCount()) {
                state.dedupePageIndex++;
            }
        });
        addDedupeWidget(builder, state, next);
    }

    private static void addDedupeTextField(
        ModularWindow.Builder builder,
        WindowState state,
        String label,
        java.util.function.Supplier<String> getter,
        java.util.function.Consumer<String> setter,
        int x,
        int y,
        int width) {
        TextWidget labelText = new TextWidget(label);
        labelText.setPos(x, y + 4);
        addDedupeWidget(builder, state, labelText);

        TextFieldWidget field = new WildcardFilterDropTextField(setter);
        field.setSynced(false, false);
        field.setText(getter.get());
        field.setSetter(setter);
        field.setTextColor(FIELD_TEXT_COLOR);
        field.setBackground(WildcardPatternWindow::fieldBackground);
        field.setTextAlignment(Alignment.CenterLeft);
        field.setMaxLength(256);
        field.setPos(x + 44, y);
        field.setSize(width - 44, 18);
        addDedupeWidget(builder, state, field);
    }

    private static void addPreviewTextField(
        ModularWindow.Builder builder,
        WindowState state,
        String label,
        java.util.function.Supplier<String> getter,
        java.util.function.Consumer<String> setter,
        int x,
        int y,
        int width,
        boolean previewPage) {
        TextWidget labelText = new TextWidget(label);
        labelText.setPos(x, y + 4);
        addPageWidget(builder, state, labelText, previewPage);

        TextFieldWidget field = new WildcardFilterDropTextField(setter);
        field.setSynced(false, false);
        field.setText(getter.get());
        field.setSetter(setter);
        field.setTextColor(FIELD_TEXT_COLOR);
        field.setBackground(WildcardPatternWindow::fieldBackground);
        field.setTextAlignment(Alignment.CenterLeft);
        field.setMaxLength(256);
        field.setPos(x + 44, y);
        // 空出 20px 给右侧的"改"按钮（MUI1 自绘界面里系统输入法无法启用 ⇒ 见 GuiTextInputDialog）
        field.setSize(width - 44 - 20, 18);
        addPageWidget(builder, state, field, previewPage);

        // 「改」按钮：打开**原版输入框**对话框，中文可正常输入/粘贴；回车写回本框并触发原 setter。
        ButtonWidget editButton = button("改");
        editButton.setPos(x + width - 18, y);
        editButton.setSize(18, 18);
        editButton.setOnClick((clickData, widget) -> {
            try {
                net.minecraft.client.Minecraft.getMinecraft()
                    .displayGuiScreen(
                        new com.wztwzt.ae2_qof.client.gui.GuiTextInputDialog(
                            label,
                            getter.get(),
                            value -> {
                                String v = value == null ? "" : value;
                                setter.accept(v);
                                field.setText(v);
                                field.markForUpdate();
                            }));
            } catch (Throwable t) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 打开文本输入对话框失败", t);
            }
        });
        addPageWidget(builder, state, editButton, previewPage);
    }

    private static ButtonWidget button(String text) {
        return button(() -> buttonBackground(false, resolveButtonText(text), BUTTON_TEXT_COLOR));
    }

    private static ButtonWidget button(Supplier<IDrawable[]> backgroundSupplier) {
        ButtonWidget button = new AnimatedButtonWidget();
        button.setSynced(false, false);
        button.setBackground(backgroundSupplier::get);
        button.setPlayClickSound(true);
        return button;
    }

    private static IDrawable[] buttonBackground(boolean active, String text, int textColor) {
        if (active) {
            return new IDrawable[] { ModularUITextures.VANILLA_BUTTON_NORMAL,
                new Rectangle().setColor(0x223E6FB0),
                new Text(text).color(textColor).alignment(Alignment.Center) };
        }
        return new IDrawable[] { ModularUITextures.VANILLA_BUTTON_NORMAL,
            new Text(text).color(textColor).alignment(Alignment.Center) };
    }

    private static IDrawable[] fieldBackground() {
        return new IDrawable[] { WildcardPatternWindow::drawInsetField };
    }

    private static String resolveButtonText(String text) {
        return text != null && text.contains(".") ? tr(text) : text;
    }

    private static void addPanel(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height,
        boolean previewPage) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 2, y + 2);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(PANEL_SHADOW_COLOR));
        addPageWidget(builder, state, shadow, previewPage);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawInsetPanel);
        addPageWidget(builder, state, border, previewPage);

        DrawableWidget fill = new DrawableWidget();
        fill.setPos(x + 3, y + 3);
        fill.setSize(width - 6, height - 6);
        fill.setDrawable(new Rectangle().setColor(PANEL_COLOR));
        addPageWidget(builder, state, fill, previewPage);
    }

    private static void addSeparator(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height,
        boolean previewPage) {
        DrawableWidget dark = new DrawableWidget();
        dark.setPos(x, y);
        dark.setSize(width, Math.max(1, height / 2));
        dark.setDrawable(new Rectangle().setColor(PANEL_LINE_DARK));
        addPageWidget(builder, state, dark, previewPage);

        DrawableWidget light = new DrawableWidget();
        light.setPos(x, y + Math.max(1, height / 2));
        light.setSize(width, Math.max(1, height - Math.max(1, height / 2)));
        light.setDrawable(new Rectangle().setColor(PANEL_LINE_LIGHT));
        addPageWidget(builder, state, light, previewPage);
    }

    private static void drawInsetField(float x, float y, float width, float height, float partialTicks) {
        drawRect(x, y, width, height, FIELD_LINE_DARK, partialTicks);
        drawRect(x + 2, y + 2, Math.max(0, width - 4), Math.max(0, height - 4), FIELD_COLOR, partialTicks);
        drawRect(x, y + height - 2, width, 2, FIELD_LINE_LIGHT, partialTicks);
        drawRect(x + width - 2, y, 2, height, FIELD_LINE_LIGHT, partialTicks);
        drawRect(x + 1, y + height - 3, Math.max(0, width - 3), 1, 0xFFBFC0C0, partialTicks);
    }

    private static void drawInsetPanel(float x, float y, float width, float height, float partialTicks) {
        drawRect(x, y, width, height, PANEL_LINE_LIGHT, partialTicks);
        drawRect(x, y, width, 2, PANEL_LINE_DARK, partialTicks);
        drawRect(x, y, 2, height, PANEL_LINE_DARK, partialTicks);
        drawRect(x + 2, y + 2, Math.max(0, width - 4), Math.max(0, height - 4), 0xFF9A9A9A, partialTicks);
        drawRect(x + 3, y + 3, Math.max(0, width - 6), Math.max(0, height - 6), PANEL_COLOR, partialTicks);
    }

    private static void drawDedupeCard(float x, float y, float width, float height, float partialTicks) {
        drawRect(x, y, width, height, PANEL_LINE_LIGHT, partialTicks);
        drawRect(x, y, width, 1, 0xFF8B8B8B, partialTicks);
        drawRect(x, y, 1, height, 0xFF8B8B8B, partialTicks);
        drawRect(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2), CARD_FILL_COLOR, partialTicks);
    }

    private static void drawRect(float x, float y, float width, float height, int color, float partialTicks) {
        if (width <= 0 || height <= 0) {
            return;
        }
        new Rectangle().setColor(color).draw(x, y, width, height, partialTicks);
    }

    private static void addMainText(ModularWindow.Builder builder, WindowState state, String text, int x, int y) {
        TextWidget widget = new TextWidget(text);
        widget.setPos(x, y);
        addMainWidget(builder, state, widget);
    }

    private static void addPreviewText(ModularWindow.Builder builder, WindowState state, String text, int x, int y) {
        TextWidget widget = new TextWidget(text);
        widget.setPos(x, y);
        addPreviewWidget(builder, state, widget);
    }

    private static void addMainWidget(ModularWindow.Builder builder, WindowState state, Widget widget) {
        addPageWidget(builder, state, widget, false);
    }

    private static void addPreviewWidget(ModularWindow.Builder builder, WindowState state, Widget widget) {
        addPageWidget(builder, state, widget, true);
    }

    private static void addDedupeWidget(ModularWindow.Builder builder, WindowState state, Widget widget) {
        widget.setEnabled(w -> state.dedupePage);
        builder.widget(widget);
    }

    private static void addExcludeWidget(ModularWindow.Builder builder, WindowState state, Widget widget) {
        widget.setEnabled(w -> state.excludePage);
        builder.widget(widget);
    }

    /** 3.34.0：电路页控件（页签谓词与既有三页完全同一套机制 ⇒ 两端控件树一致）。 */
    private static void addCircuitWidget(ModularWindow.Builder builder, WindowState state, Widget widget) {
        widget.setEnabled(w -> state.circuitPage);
        builder.widget(widget);
    }

    /** 3.34.0：不消耗物品页控件。 */
    private static void addNonConsumedWidget(ModularWindow.Builder builder, WindowState state, Widget widget) {
        widget.setEnabled(w -> state.nonConsumedPage);
        builder.widget(widget);
    }

    private static void addCircuitPanel(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 2, y + 2);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(PANEL_SHADOW_COLOR));
        addCircuitWidget(builder, state, shadow);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawInsetPanel);
        addCircuitWidget(builder, state, border);

        DrawableWidget fill = new DrawableWidget();
        fill.setPos(x + 3, y + 3);
        fill.setSize(width - 6, height - 6);
        fill.setDrawable(new Rectangle().setColor(PANEL_COLOR));
        addCircuitWidget(builder, state, fill);
    }

    private static void addCircuitSeparator(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget dark = new DrawableWidget();
        dark.setPos(x, y);
        dark.setSize(width, Math.max(1, height / 2));
        dark.setDrawable(new Rectangle().setColor(PANEL_LINE_DARK));
        addCircuitWidget(builder, state, dark);

        DrawableWidget light = new DrawableWidget();
        light.setPos(x, y + Math.max(1, height / 2));
        light.setSize(width, Math.max(1, height - Math.max(1, height / 2)));
        light.setDrawable(new Rectangle().setColor(PANEL_LINE_LIGHT));
        addCircuitWidget(builder, state, light);
    }

    private static void addNonConsumedPanel(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 2, y + 2);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(PANEL_SHADOW_COLOR));
        addNonConsumedWidget(builder, state, shadow);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawInsetPanel);
        addNonConsumedWidget(builder, state, border);

        DrawableWidget fill = new DrawableWidget();
        fill.setPos(x + 3, y + 3);
        fill.setSize(width - 6, height - 6);
        fill.setDrawable(new Rectangle().setColor(PANEL_COLOR));
        addNonConsumedWidget(builder, state, fill);
    }

    private static void addNonConsumedSeparator(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget dark = new DrawableWidget();
        dark.setPos(x, y);
        dark.setSize(width, Math.max(1, height / 2));
        dark.setDrawable(new Rectangle().setColor(PANEL_LINE_DARK));
        addNonConsumedWidget(builder, state, dark);

        DrawableWidget light = new DrawableWidget();
        light.setPos(x, y + Math.max(1, height / 2));
        light.setSize(width, Math.max(1, height - Math.max(1, height / 2)));
        light.setDrawable(new Rectangle().setColor(PANEL_LINE_LIGHT));
        addNonConsumedWidget(builder, state, light);
    }

    private static void addDedupePanel(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 2, y + 2);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(PANEL_SHADOW_COLOR));
        addDedupeWidget(builder, state, shadow);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawInsetPanel);
        addDedupeWidget(builder, state, border);

        DrawableWidget fill = new DrawableWidget();
        fill.setPos(x + 3, y + 3);
        fill.setSize(width - 6, height - 6);
        fill.setDrawable(new Rectangle().setColor(PANEL_COLOR));
        addDedupeWidget(builder, state, fill);
    }

    private static void addDedupeCard(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 1, y + 1);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(CARD_SHADOW_COLOR));
        addDedupeWidget(builder, state, shadow);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawDedupeCard);
        addDedupeWidget(builder, state, border);
    }

    private static void addExcludePanel(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget shadow = new DrawableWidget();
        shadow.setPos(x + 2, y + 2);
        shadow.setSize(width, height);
        shadow.setDrawable(new Rectangle().setColor(PANEL_SHADOW_COLOR));
        addExcludeWidget(builder, state, shadow);

        DrawableWidget border = new DrawableWidget();
        border.setPos(x, y);
        border.setSize(width, height);
        border.setDrawable(WildcardPatternWindow::drawInsetPanel);
        addExcludeWidget(builder, state, border);

        DrawableWidget fill = new DrawableWidget();
        fill.setPos(x + 3, y + 3);
        fill.setSize(width - 6, height - 6);
        fill.setDrawable(new Rectangle().setColor(PANEL_COLOR));
        addExcludeWidget(builder, state, fill);
    }

    private static void addDedupeSeparator(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget dark = new DrawableWidget();
        dark.setPos(x, y);
        dark.setSize(width, Math.max(1, height / 2));
        dark.setDrawable(new Rectangle().setColor(PANEL_LINE_DARK));
        addDedupeWidget(builder, state, dark);

        DrawableWidget light = new DrawableWidget();
        light.setPos(x, y + Math.max(1, height / 2));
        light.setSize(width, Math.max(1, height - Math.max(1, height / 2)));
        light.setDrawable(new Rectangle().setColor(PANEL_LINE_LIGHT));
        addDedupeWidget(builder, state, light);
    }

    private static void addExcludeSeparator(
        ModularWindow.Builder builder,
        WindowState state,
        int x,
        int y,
        int width,
        int height) {
        DrawableWidget dark = new DrawableWidget();
        dark.setPos(x, y);
        dark.setSize(width, Math.max(1, height / 2));
        dark.setDrawable(new Rectangle().setColor(PANEL_LINE_DARK));
        addExcludeWidget(builder, state, dark);

        DrawableWidget light = new DrawableWidget();
        light.setPos(x, y + Math.max(1, height / 2));
        light.setSize(width, Math.max(1, height - Math.max(1, height / 2)));
        light.setDrawable(new Rectangle().setColor(PANEL_LINE_LIGHT));
        addExcludeWidget(builder, state, light);
    }

    private static void addPageWidget(ModularWindow.Builder builder, WindowState state, Widget widget, boolean previewPage) {
        // 3.34.0：新增两页后，主/预览页也必须让位（否则它们会在电路页/不消耗页上一起画出来）
        widget.setEnabled(
            w -> !state.dedupePage && !state.excludePage
                && !state.circuitPage
                && !state.nonConsumedPage
                && state.previewPage == previewPage);
        builder.widget(widget);
    }

    private static String summarize(ItemStack inputStack, ItemStack outputStack) {
        return trim(formatPreviewStack(inputStack) + " -> " + formatPreviewStack(outputStack), 64);
    }

    private static String formatPreviewStack(ItemStack stack) {
        if (stack == null) {
            return "-";
        }
        return formatStackSize(Math.max(1, stack.stackSize)) + "x" + stack.getDisplayName();
    }

    private static String formatPreviewLine(String line, int duplicateOutputCount) {
        if (duplicateOutputCount <= 1) {
            return line == null ? "" : line;
        }
        return trim(
            tr("gui.wildcardpattern.duplicate_output") + " x" + duplicateOutputCount + " " + (line == null ? "" : line),
            72);
    }

    private static Map<String, Integer> countOutputIdentities(List<PreviewRow> rows) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        if (rows == null) {
            return counts;
        }
        for (PreviewRow row : rows) {
            if (row == null || row.outputKey.isEmpty()) {
                continue;
            }
            Integer count = counts.get(row.outputKey);
            counts.put(row.outputKey, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
        return counts;
    }

    private static int getDuplicateCount(Map<String, Integer> counts, String outputKey) {
        if (counts == null || outputKey == null || outputKey.isEmpty()) {
            return 0;
        }
        Integer count = counts.get(outputKey);
        return count == null ? 0 : count.intValue();
    }

    private static void sortDuplicateOutputsFirst(List<PreviewRow> rows) {
        rows.sort((left, right) -> Boolean.compare(right.hasDuplicateOutput(), left.hasDuplicateOutput()));
    }

    private static void sortDuplicateDedupeRowsFirst(List<DedupeRow> rows) {
        rows.sort((left, right) -> Boolean.compare(right.hasDuplicateOutput(), left.hasDuplicateOutput()));
    }

    private static String getOutputIdentity(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        String itemName = String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
        if (itemName == null || itemName.isEmpty() || "null".equals(itemName)) {
            itemName = stack.getItem().getClass().getName();
        }
        NBTTagCompound tag = stack.getTagCompound();
        return itemName + "@" + stack.getItemDamage() + "x" + Math.max(1, stack.stackSize)
            + "#" + (tag == null ? "" : Integer.toHexString(tag.hashCode()));
    }

    private static String getOutputExcludeToken(ItemStack outputStack, String materialName) {
        String outputOre = getAssociatedOreNameStatic(outputStack);
        if (outputOre != null && !outputOre.isEmpty()) {
            return outputOre;
        }
        return materialName == null ? "" : materialName;
    }

    private static String getAssociatedOreNameStatic(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemData association = GTOreDictUnificator.getAssociation(stack);
        if (association != null && association.hasValidPrefixMaterialData()) {
            return getPrefixName(association.mPrefix) + association.mMaterial.mMaterial.mName;
        }
        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds == null || oreIds.length == 0) {
            return null;
        }
        for (int oreId : oreIds) {
            String oreName = OreDictionary.getOreName(oreId);
            if (oreName == null || oreName.isEmpty()) continue;
            for (gregtech.api.enums.OrePrefixes prefix : GTCompat.orePrefixes()) {
                String prefixName = getPrefixName(prefix);
                if (!prefixName.isEmpty() && oreName.regionMatches(true, 0, prefixName, 0, prefixName.length())) {
                    return oreName;
                }
            }
        }
        return null;
    }

    private static String formatStackSize(long amount) {
        if (amount >= 100_000_000L) {
            return formatCompact(amount, 1_000_000_000L, "g");
        }
        if (amount >= 1_000_000L) {
            return formatCompact(amount, 1_000_000L, "m");
        }
        if (amount >= 1_000L) {
            return formatCompact(amount, 1_000L, "k");
        }
        return String.valueOf(Math.max(0L, amount));
    }

    private static void ensureSize(List<WildcardPatternEntry> entries, int size) {
        while (entries.size() < size) {
            entries.add(WildcardPatternEntry.fromStack(null));
        }
        while (entries.size() > size) {
            entries.remove(entries.size() - 1);
        }
    }

    private static void clearEntries(List<WildcardPatternEntry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            entries.set(i, WildcardPatternEntry.fromStack(null));
        }
    }

    private static void clearStrings(List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            values.set(i, "");
        }
    }

    private static String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String trimMiddle(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max || max <= 3) {
            return trim(text, max);
        }
        int head = (max - 3) / 2;
        int tail = max - 3 - head;
        return text.substring(0, head) + "..." + text.substring(text.length() - tail);
    }

    private static String formatAmount(long amount) {
        if (amount >= 100_000_000) {
            return formatCompact(amount, 1_000_000_000L, "g");
        }
        if (amount >= 1_000_000) {
            return formatCompact(amount, 1_000_000, "m");
        }
        if (amount >= 1_000) {
            return formatCompact(amount, 1_000, "k");
        }
        return String.valueOf(Math.max(1, amount));
    }

    private static String formatCompact(long amount, long unit, String suffix) {
        if (amount % unit == 0) {
            return (amount / unit) + suffix;
        }
        long scaled = Math.round((double) amount * 10D / unit);
        if (scaled % 10 == 0) {
            return (scaled / 10) + suffix;
        }
        return (scaled / 10) + "." + (scaled % 10) + suffix;
    }

    private static long parseAmount(String value) {
        String token = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (token.isEmpty()) {
            return 1;
        }
        long multiplier = 1L;
        if (token.endsWith("k")) {
            multiplier = 1_000L;
            token = token.substring(0, token.length() - 1).trim();
        } else if (token.endsWith("m")) {
            multiplier = 1_000_000L;
            token = token.substring(0, token.length() - 1).trim();
        } else if (token.endsWith("g")) {
            multiplier = 1_000_000_000L;
            token = token.substring(0, token.length() - 1).trim();
        }
        try {
            double parsed = Double.parseDouble(token);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return 1;
            }
            long result = Math.round(parsed * multiplier);
            return Math.max(1L, Math.min(WildcardPatternEntry.MAX_AMOUNT, result));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String getPrefixName(gregtech.api.enums.OrePrefixes prefix) {
        if (prefix == null) {
            return "";
        }
        try {
            return (String) prefix.getClass().getMethod("getName").invoke(prefix);
        } catch (Exception ignored) {}
        try {
            return (String) prefix.getClass().getMethod("name").invoke(prefix);
        } catch (Exception ignored) {}
        return prefix.toString();
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static final class EntryCellRefs {
        private final TextFieldWidget textField;
        private final TextFieldWidget amountField;
        private final java.util.function.Supplier<String> amountSupplier;

        private EntryCellRefs(
            TextFieldWidget textField,
            TextFieldWidget amountField,
            java.util.function.Supplier<String> amountSupplier) {
            this.textField = textField;
            this.amountField = amountField;
            this.amountSupplier = amountSupplier;
        }

        private void clear() {
            this.textField.setText("");
            this.amountField.setText("1");
            this.textField.markForUpdate();
            this.amountField.markForUpdate();
        }

        private void updateAmount() {
            this.amountField.setText(this.amountSupplier.get());
            this.amountField.markForUpdate();
        }

        /** 3.34.0：把某个 entry 的显示刷到控件上（NEI 加号就地刷新整页时逐格调用）。 */
        private void refresh(WildcardPatternEntry entry) {
            this.textField.setText(entry == null || entry.isEmpty() ? "" : trim(entry.getLabel(), 11));
            this.amountField.setText(entry == null ? "1" : formatAmount(entry.getAmountLong()));
            this.textField.markForUpdate();
            this.amountField.markForUpdate();
        }
    }

    private static final class AnimatedButtonWidget extends ButtonWidget {

        @Override
        public IDrawable[] getBackground() {
            IDrawable[] base = super.getBackground();
            if (!isHovering()) {
                return base;
            }
            IDrawable highlight = new Rectangle().setColor(0x332A62A5);
            if (base == null || base.length == 0) {
                return new IDrawable[] { highlight };
            }
            IDrawable[] result = new IDrawable[base.length + 1];
            if (base.length > 0) {
                result[0] = base[0];
            }
            result[1] = highlight;
            for (int index = 1; index < base.length; index++) {
                result[index + 1] = base[index];
            }
            return result;
        }
    }

    private static final class DedupeRow {
        private final int rule;
        private final String materialName;
        private final String inputOreName;
        private final String outputOreName;
        private final boolean inputDuplicate;
        private final boolean outputDuplicate;
        private final int duplicateOutputCount;

        private DedupeRow(
            int rule,
            String materialName,
            String inputOreName,
            String outputOreName,
            boolean inputDuplicate,
            boolean outputDuplicate) {
            this(rule, materialName, inputOreName, outputOreName, inputDuplicate, outputDuplicate, 0);
        }

        private DedupeRow(
            int rule,
            String materialName,
            String inputOreName,
            String outputOreName,
            boolean inputDuplicate,
            boolean outputDuplicate,
            int duplicateOutputCount) {
            this.rule = rule;
            this.materialName = materialName == null ? "" : materialName;
            this.inputOreName = inputOreName;
            this.outputOreName = outputOreName;
            this.inputDuplicate = inputDuplicate;
            this.outputDuplicate = outputDuplicate;
            this.duplicateOutputCount = duplicateOutputCount;
        }

        private String getLine() {
            return "R" + (this.rule + 1) + " "
                + label(this.inputOreName)
                + " -> "
                + label(this.outputOreName);
        }

        private String getInputLine() {
            return label(this.inputOreName);
        }

        private String getOutputLine() {
            return label(this.outputOreName);
        }

        private String getChoiceLine(WindowState state) {
            return label(state.getSelectedDedupeLabel(this.inputOreName))
                + " -> "
                + label(state.getSelectedDedupeLabel(this.outputOreName));
        }

        private String getInputChoiceLine(WindowState state) {
            return state.getSelectedDedupeLabel(this.inputOreName);
        }

        private String getOutputChoiceLine(WindowState state) {
            return state.getSelectedDedupeLabel(this.outputOreName);
        }

        private boolean hasDuplicateOutput() {
            return this.duplicateOutputCount > 1;
        }

        private EnumChatFormatting getTopColor() {
            return hasDuplicateOutput() ? EnumChatFormatting.RED : EnumChatFormatting.BLACK;
        }

        private EnumChatFormatting getDetailColor() {
            return hasDuplicateOutput() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY;
        }

        private boolean matches(WindowState state, String search) {
            return NechSearchCompat.matches(getLine(), search)
                || NechSearchCompat.matches(getChoiceLine(state), search)
                || NechSearchCompat.matches(getDuplicateLine(), search)
                || NechSearchCompat.matches(this.materialName, search);
        }

        private String getDuplicateLine() {
            if (!hasDuplicateOutput()) {
                return "";
            }
            return "R" + (this.rule + 1) + " "
                + tr("gui.wildcardpattern.duplicate_output")
                + " x"
                + Math.max(2, this.duplicateOutputCount)
                + " "
                + label(this.outputOreName);
        }

        private static String label(String value) {
            return value == null || value.isEmpty() ? "-" : value;
        }
    }

    private static final class PreviewRow {
        private final int rule;
        private final String materialName;
        private final String excludeToken;
        private final String outputExcludeToken;
        private final String line;
        private final String outputKey;
        private final String outputLabel;
        private final int duplicateOutputCount;

        private PreviewRow(
            int rule,
            String materialName,
            String excludeToken,
            String outputExcludeToken,
            String line,
            String outputKey,
            String outputLabel,
            int duplicateOutputCount) {
            this.rule = rule;
            this.materialName = materialName == null ? "" : materialName;
            this.excludeToken = excludeToken == null ? "" : excludeToken;
            this.outputExcludeToken = outputExcludeToken == null ? "" : outputExcludeToken;
            this.line = line == null ? "" : line;
            this.outputKey = outputKey == null ? "" : outputKey;
            this.outputLabel = outputLabel == null ? "" : outputLabel;
            this.duplicateOutputCount = duplicateOutputCount;
        }

        private boolean hasDuplicateOutput() {
            return this.duplicateOutputCount > 1;
        }
    }

    private static final class AsyncPreviewResult {
        private final int generation;
        private final List<PreviewRow> rows;

        private AsyncPreviewResult(int generation, List<PreviewRow> rows) {
            this.generation = generation;
            this.rows = rows == null ? java.util.Collections.<PreviewRow>emptyList() : rows;
        }
    }

    private static final class WindowState {
        private final EntityPlayer player;
        private final int slot;
        private final List<WildcardPatternEntry> inputs;
        private final List<WildcardPatternEntry> outputs;
        private final List<String> ruleIncludes;
        private final List<String> ruleExcludes;
        private final List<PreviewRow> previewRows = new ArrayList<>();
        private final java.util.Map<String, ItemStack> preferredOreStacks = new java.util.LinkedHashMap<>();
        private final List<DedupeRow> dedupeRows = new ArrayList<>();
        private volatile AsyncPreviewResult asyncPreviewResult = null;
        private Thread asyncPreviewThread = null;
        private volatile int asyncPreviewGeneration;

        private String globalExclude;
        private String previewSearch = "";
        private String dedupeSearch = "";
        private String excludeDraft = "";
        private boolean previewPage;
        private boolean dedupePage;
        private boolean excludePage;
        /** 3.34.0：两个新页（用户拍板：电路与不消耗物品各自成页）。 */
        private boolean circuitPage;
        private boolean nonConsumedPage;
        private int nonConsumedPageIndex;
        /** 不消耗物品页的本地镜像（来源＝我们的子树；每次增删立即整包写回服务端）。 */
        private final List<ItemStack> nonConsumed = new ArrayList<>();
        /** 懒同步只尝试一次（每帧的 supplier 都会读状态，不能每帧都去 pull NBT）。 */
        private boolean pulledOursOnce;
        /** 电路页显示的当前值（缓存，避免每帧解析 NBT；进页与写入后刷新）。 */
        private int cachedCircuit = Integer.MIN_VALUE;
        private boolean excludeReturnPreview;
        private int selectedRule;
        private int previewRule = -1;
        private int excludeRule = -1;
        private int previewPageIndex;
        private int dedupePageIndex;
        private int excludePageIndex;
        /** 3.34.0：9 行 × 2（输入/输出）的控件引用，供 NEI 加号就地刷新（构建期 setText 只生效一次）。 */
        private final List<EntryCellRefs> rowRefs = new ArrayList<>();

        private WindowState(EntityPlayer player, int slot) {
            this.player = player;
            this.slot = slot;
            ItemStack stack = getHeldStack();
            if (stack != null) {
                WildcardPatternGenerator.markAsWildcard(stack);
                this.inputs = new ArrayList<>(WildcardPatternState.getInputEntries(stack));
                this.outputs = new ArrayList<>(WildcardPatternState.getOutputEntries(stack));
                this.globalExclude = WildcardPatternConfig.getGlobalExcludeMaterials(stack);
                this.ruleIncludes = new ArrayList<>(WildcardPatternConfig.getRuleIncludeList(stack, RULE_ROWS));
                this.ruleExcludes = new ArrayList<>(WildcardPatternConfig.getRuleExcludeList(stack, RULE_ROWS));
                for (String oreName : WildcardPatternConfig.getPreferredOreNames(stack)) {
                    ItemStack preferred = WildcardPatternConfig.getPreferredOreStack(stack, oreName);
                    if (preferred != null) {
                        this.preferredOreStacks.put(oreName, preferred);
                    }
                }
            } else {
                this.inputs = new ArrayList<>();
                this.outputs = new ArrayList<>();
                this.globalExclude = "";
                this.ruleIncludes = new ArrayList<>();
                this.ruleExcludes = new ArrayList<>();
            }
            ensureSize(this.inputs, RULE_ROWS);
            ensureSize(this.outputs, RULE_ROWS);
            while (this.ruleIncludes.size() < RULE_ROWS) this.ruleIncludes.add("");
            while (this.ruleExcludes.size() < RULE_ROWS) this.ruleExcludes.add("");

            // 3.34.0：不消耗物品页的本地镜像取自我们的子树（需要时懒同步一次；失败按空列表并留痕）
            try {
                com.wztwzt.ae2_qof.wildcard.SmartWildcardState ours = materializeOurs(stack);
                if (ours != null) this.nonConsumed.addAll(ours.nonConsumed);
            } catch (Throwable t) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 读取样板的不消耗物品列表失败（按空列表显示）", t);
            }
        }

        // ================= 3.34.0：两个新页（电路 / 不消耗物品）=================

        /** 切到电路页（同时收掉其它页 —— 任一时刻只有一个页面在画）。 */
        private void openCircuitPage() {
            this.previewPage = false;
            this.excludePage = false;
            this.dedupePage = false;
            this.nonConsumedPage = false;
            this.circuitPage = true;
            refreshCircuitCache();
            refreshActivePage();
        }

        /** 切到不消耗物品页。 */
        private void openNonConsumedPage() {
            this.previewPage = false;
            this.excludePage = false;
            this.dedupePage = false;
            this.circuitPage = false;
            this.nonConsumedPage = true;
            if (this.nonConsumedPageIndex >= getNonConsumedPageCount()) {
                this.nonConsumedPageIndex = Math.max(0, getNonConsumedPageCount() - 1);
            }
            refreshActivePage();
        }

        /** 回主页（页签「主页」与两页的「返回」共用）。 */
        private void closeExtraPages() {
            this.circuitPage = false;
            this.nonConsumedPage = false;
            refreshActivePage();
        }

        /** 当前电路（读缓存；未初始化时先刷新一次）。 */
        private int currentCircuit() {
            if (this.cachedCircuit == Integer.MIN_VALUE) refreshCircuitCache();
            return this.cachedCircuit;
        }

        /** 重新读取"这张样板自带的电路"到缓存（进电路页时调用一次，不在渲染循环里解析 NBT）。 */
        private void refreshCircuitCache() {
            try {
                com.wztwzt.ae2_qof.wildcard.SmartWildcardState ours = materializeOurs(getHeldStack());
                this.cachedCircuit = ours == null ? -1 : ours.circuit;
            } catch (Throwable t) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 电路页：读取当前电路失败（按未设置显示）", t);
                this.cachedCircuit = -1;
            }
        }

        /** 电路页点号：写回服务端并立即更新本地缓存（界面当场变化）。 */
        private void applyCircuitValue(int circuit) {
            if (!pushOurState(s -> s.circuit = circuit, "电路页")) return;
            this.cachedCircuit = circuit;
            chat("\u00a7a[AE2QoL] \u5185\u7f6e\u7535\u8def = " + (circuit >= 1 ? circuit : "继承"));
        }

        /**
         * 取出"我们自己的状态"：没有子树时**懒同步一次**（玩家可能只在 Wild 界面里配过）。
         *
         * <p>为什么这里也要兜底：电路页/不消耗页的写回是"读—改—整包写"，读不到就发空包 ⇒ 会把服务端
         * 已有的规则覆盖成 0 条（3.33.0 实测到的规则被抹掉就是这个机理）。
         */
        private com.wztwzt.ae2_qof.wildcard.SmartWildcardState materializeOurs(ItemStack stack) {
            if (!com.wztwzt.ae2_qof.wildcard.SmartWildcardGate.isOurs(stack)) return null;
            com.wztwzt.ae2_qof.wildcard.SmartWildcardState ours = com.wztwzt.ae2_qof.wildcard.SmartWildcardState
                .of(stack);
            if (ours == null && !this.pulledOursOnce) {
                this.pulledOursOnce = true;
                try {
                    com.wztwzt.ae2_qof.wildport.bridge.WildcardBridge.pullFromWild(stack);
                } catch (Throwable t) {
                    com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 窗口内懒同步失败（按未配置处理）", t);
                }
                ours = com.wztwzt.ae2_qof.wildcard.SmartWildcardState.of(stack);
            }
            return ours;
        }

        /** 唯一写路径：就地改我们的状态 + **立刻**整包发回服务端（带槽位）。返回是否成功。 */
        private boolean pushOurState(
            java.util.function.Consumer<com.wztwzt.ae2_qof.wildcard.SmartWildcardState> mutator,
            String why) {
            try {
                ItemStack held = getHeldStack();
                if (!com.wztwzt.ae2_qof.wildcard.SmartWildcardGate.isOurs(held)) {
                    com.wztwzt.ae2_qof.MyMod.LOG
                        .warn("[AE2QoL] {}：槽位 {} 里不是通配样板，未写入（item={}）", why, this.slot, held);
                    return false;
                }
                com.wztwzt.ae2_qof.wildcard.SmartWildcardState s = materializeOurs(held);
                if (s == null) s = new com.wztwzt.ae2_qof.wildcard.SmartWildcardState();
                mutator.accept(s);
                com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL.sendToServer(
                    new com.wztwzt.ae2_qof.network.SmartWildcardRulesPacket(s, this.slot));
                com.wztwzt.ae2_qof.MyMod.LOG.info(
                    "[AE2QoL] {}：已提交（槽位 {}，规则 {} 条、不消耗 {} 项、电路 {}）",
                    why,
                    this.slot,
                    s.rules.size(),
                    s.nonConsumed.size(),
                    s.circuit);
                return true;
            } catch (Throwable t) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] " + why + "：写入失败", t);
                return false;
            }
        }

        private int getNonConsumedCount() {
            return this.nonConsumed.size();
        }

        private int getNonConsumedPageCount() {
            return Math.max(1, (this.nonConsumed.size() + NON_CONSUMED_LINES - 1) / NON_CONSUMED_LINES);
        }

        private ItemStack getNonConsumedRow(int lineIndex) {
            int absolute = this.nonConsumedPageIndex * NON_CONSUMED_LINES + lineIndex;
            return absolute >= 0 && absolute < this.nonConsumed.size() ? this.nonConsumed.get(absolute) : null;
        }

        /** 手持物品记为不消耗（按钮入口）。 */
        private void addHeldAsNonConsumed() {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                ItemStack held = mc == null || mc.thePlayer == null ? null : mc.thePlayer.getCurrentEquippedItem();
                if (held == null) {
                    chat("\u00a7c[AE2QoL] \u624b\u4e0a\u6ca1\u6709\u7269\u54c1");
                    return;
                }
                addNonConsumed(held);
            } catch (Throwable t) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 记为不消耗物品失败", t);
            }
        }

        /** 记为"合成时不消耗"（NEI 拖入区与"加入手持"共用），**立即**写回服务端。 */
        private void addNonConsumed(ItemStack stack) {
            if (stack == null || stack.getItem() == null) return;
            ItemStack mark = stack.copy();
            mark.stackSize = 1;
            for (ItemStack existing : this.nonConsumed) {
                if (existing != null && existing.getItem() == mark.getItem()
                    && existing.getItemDamage() == mark.getItemDamage()
                    && ItemStack.areItemStackTagsEqual(existing, mark)) {
                    chat("\u00a7e[AE2QoL] \u5df2\u5728\u5217\u8868\u4e2d\uff1a" + mark.getDisplayName());
                    return;
                }
            }
            this.nonConsumed.add(mark);
            if (this.nonConsumedPageIndex >= getNonConsumedPageCount()) {
                this.nonConsumedPageIndex = getNonConsumedPageCount() - 1;
            }
            if (pushOurState(s -> {
                s.nonConsumed.clear();
                s.nonConsumed.addAll(this.nonConsumed);
            }, "不消耗物品页（加入）")) {
                chat("\u00a7a[AE2QoL] \u5df2\u8bb0\u4e3a\u4e0d\u6d88\u8017\uff1a" + mark.getDisplayName());
            } else {
                // 写回失败就地回滚，避免界面与服务端不一致（本项目原则：不允许静默不一致）
                this.nonConsumed.remove(this.nonConsumed.size() - 1);
            }
        }

        /** 删除第 lineIndex 行（当前页内）的不消耗物品，**立即**写回服务端。 */
        private void removeNonConsumedAt(int lineIndex) {
            int absolute = this.nonConsumedPageIndex * NON_CONSUMED_LINES + lineIndex;
            if (absolute < 0 || absolute >= this.nonConsumed.size()) return;
            ItemStack removed = this.nonConsumed.remove(absolute);
            if (!pushOurState(s -> {
                s.nonConsumed.clear();
                s.nonConsumed.addAll(this.nonConsumed);
            }, "不消耗物品页（删除）")) {
                this.nonConsumed.add(absolute, removed); // 失败回滚
                return;
            }
            if (this.nonConsumedPageIndex >= getNonConsumedPageCount()) {
                this.nonConsumedPageIndex = Math.max(0, getNonConsumedPageCount() - 1);
            }
            chat("\u00a7a[AE2QoL] \u5df2\u79fb\u9664\uff1a" + (removed == null ? "?" : removed.getDisplayName()));
        }

        /**
         * 3.34.0：整页替换 9 行（用户拍板："加号按推导结果整页替换"），并就地刷新控件。
         *
         * <p>为什么必须刷新控件：窗口的行文本是**构建期** {@code setText} 写死的，只改内存态不会重绘。
         */
        private void setRows(List<WildcardPatternEntry> newInputs, List<WildcardPatternEntry> newOutputs) {
            for (int i = 0; i < RULE_ROWS; i++) {
                this.inputs.set(i, i < newInputs.size() ? newInputs.get(i) : WildcardPatternEntry.fromStack(null));
                this.outputs.set(i, i < newOutputs.size() ? newOutputs.get(i) : WildcardPatternEntry.fromStack(null));
                this.ruleIncludes.set(i, "");
                this.ruleExcludes.set(i, "");
            }
            refreshRuleRows();
            refreshActivePage();
            this.cachedCircuit = Integer.MIN_VALUE; // 行被整页替换后，电路缓存一并作废（下次进电路页重新读）
        }

        /** 把 9 行的显示刷成当前内存态（输入/输出文本 + 数量）。 */
        private void refreshRuleRows() {
            for (int row = 0; row < RULE_ROWS; row++) {
                int inputRef = row * 2;
                if (inputRef + 1 < this.rowRefs.size()) {
                    this.rowRefs.get(inputRef)
                        .refresh(this.inputs.get(row));
                    this.rowRefs.get(inputRef + 1)
                        .refresh(this.outputs.get(row));
                }
            }
        }

        /** 聊天栏回执（就地刷新后给用户一条明确反馈，避免"点了没反应"的观感）。 */
        private void chat(String message) {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                if (mc != null && mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(message));
                }
            } catch (Throwable t) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 聊天栏回执失败", t);
            }
        }

        private void openPreview(int rule) {
            this.dedupePage = false;
            this.excludePage = false;
            this.circuitPage = false;
            this.nonConsumedPage = false;
            this.previewRule = rule;
            this.previewPageIndex = 0;
            this.previewPage = true;
            rebuildPreview();
        }

        private void openExcludeEditor(int rule) {
            this.excludeReturnPreview = this.previewPage;
            this.previewPage = false;
            this.dedupePage = false;
            this.circuitPage = false;
            this.nonConsumedPage = false;
            this.excludeRule = rule;
            this.excludeDraft = "";
            this.excludePageIndex = 0;
            this.excludePage = true;
        }

        private void closeExcludeEditor() {
            this.excludePage = false;
            if (this.excludeReturnPreview) {
                this.previewPage = true;
                rebuildPreview();
            }
            this.excludeReturnPreview = false;
        }

        private void openDedupe() {
            this.previewPage = false;
            this.excludePage = false;
            this.circuitPage = false;
            this.nonConsumedPage = false;
            this.dedupePage = true;
            this.dedupePageIndex = 0;
            rebuildDedupe();
        }

        private String getExcludeTitle() {
            if (this.excludeRule >= 0) {
                return StatCollector.translateToLocalFormatted("gui.wildcardpattern.rule_exclude", this.excludeRule + 1);
            }
            return tr("gui.wildcardpattern.global_exclude");
        }

        private String getCurrentExcludeValue() {
            return this.excludeRule >= 0 ? this.ruleExcludes.get(this.excludeRule) : this.globalExclude;
        }

        private void setCurrentExcludeValue(String value) {
            String next = value == null ? "" : value;
            if (this.excludeRule >= 0) {
                this.ruleExcludes.set(this.excludeRule, next);
            } else {
                this.globalExclude = next;
            }
            if (next.trim().isEmpty()) {
                this.excludePageIndex = 0;
            }
            refreshActivePage();
        }

        private String getExcludeSummary(int rule) {
            String value = rule >= 0 ? this.ruleExcludes.get(rule) : this.globalExclude;
            if (value == null || value.trim().isEmpty()) {
                return tr("gui.wildcardpattern.exclude_empty");
            }
            return value;
        }

        private List<String> getCurrentExcludeTokens() {
            List<String> tokens = new ArrayList<>();
            String value = getCurrentExcludeValue();
            if (value == null || value.trim().isEmpty()) {
                return tokens;
            }
            for (String token : value.split("[,;\\s]+")) {
                String trimmed = token == null ? "" : token.trim();
                if (!trimmed.isEmpty()) {
                    tokens.add(trimmed);
                }
            }
            return tokens;
        }

        private String getCurrentExcludeToken(int lineIndex) {
            int absolute = this.excludePageIndex * EXCLUDE_LINES + lineIndex;
            List<String> tokens = getCurrentExcludeTokens();
            return absolute >= 0 && absolute < tokens.size() ? tokens.get(absolute) : "";
        }

        private void addCurrentExcludeDraft() {
            addCurrentExcludeToken(this.excludeDraft);
            this.excludeDraft = "";
        }

        private void addCurrentExcludeToken(String value) {
            String token = value == null ? "" : value.trim();
            if (token.isEmpty()) {
                return;
            }
            List<String> tokens = getCurrentExcludeTokens();
            for (String existing : tokens) {
                if (existing.equalsIgnoreCase(token)) {
                    return;
                }
            }
            tokens.add(token);
            setCurrentExcludeValue(String.join(" ", tokens));
            this.excludePageIndex = Math.max(0, getExcludePageCount() - 1);
        }

        private void removeCurrentExcludeToken(int lineIndex) {
            int absolute = this.excludePageIndex * EXCLUDE_LINES + lineIndex;
            List<String> tokens = getCurrentExcludeTokens();
            if (absolute < 0 || absolute >= tokens.size()) {
                return;
            }
            tokens.remove(absolute);
            setCurrentExcludeValue(String.join(" ", tokens));
            if (this.excludePageIndex >= getExcludePageCount()) {
                this.excludePageIndex = Math.max(0, getExcludePageCount() - 1);
            }
        }

        private String getPreviewTitle() {
            if (this.previewRule >= 0) {
                return StatCollector.translateToLocalFormatted("gui.wildcardpattern.preview_rule", this.previewRule + 1);
            }
            return tr("gui.wildcardpattern.preview_all");
        }

        private int getPreviewPageCount() {
            return Math.max(1, (this.previewRows.size() + PREVIEW_LINES - 1) / PREVIEW_LINES);
        }

        private int getDedupePageCount() {
            return Math.max(1, (this.dedupeRows.size() + DEDUPE_LINES - 1) / DEDUPE_LINES);
        }

        private int getExcludePageCount() {
            return Math.max(1, (this.getCurrentExcludeTokens().size() + EXCLUDE_LINES - 1) / EXCLUDE_LINES);
        }

        private void multiplyRule(int rule) {
            scaleRule(rule, true);
        }

        private void divideRule(int rule) {
            scaleRule(rule, false);
        }

        private void scaleRule(int rule, boolean multiplying) {
            if (rule < 0 || rule >= this.inputs.size() || rule >= this.outputs.size()) {
                return;
            }
            scaleEntry(this.inputs.get(rule), multiplying);
            scaleEntry(this.outputs.get(rule), multiplying);
            refreshActivePage();
        }

        private boolean canMultiplyRule(int rule) {
            return canMultiplyEntry(this.inputs.get(rule)) && canMultiplyEntry(this.outputs.get(rule));
        }

        private boolean canMultiplyEntry(WildcardPatternEntry entry) {
            return entry == null || entry.isEmpty() || entry.getAmountLong() <= WildcardPatternEntry.MAX_AMOUNT / 2L;
        }

        private static void scaleEntry(WildcardPatternEntry entry, boolean multiplying) {
            if (entry == null || entry.isEmpty()) {
                return;
            }
            if (multiplying) {
                entry.multiplyAmount(2);
            } else {
                entry.divideAmount(2);
            }
        }

        private void rebuildPreview() {
            this.previewRows.clear();
            final int generation = ++this.asyncPreviewGeneration;
            // Cancel any in-progress background build
            Thread prev = this.asyncPreviewThread;
            if (prev != null) {
                prev.interrupt();
                this.asyncPreviewThread = null;
            }
            this.asyncPreviewResult = null;

            // Build preview ItemStacks synchronously on main thread (requires player inventory access)
            final int ruleSnapshot = this.previewRule;
            final String filterSnapshot = this.previewSearch == null ? "" : this.previewSearch.trim();
            final java.util.LinkedHashMap<Integer, ItemStack> stacks = new java.util.LinkedHashMap<>();
            if (ruleSnapshot >= 0) {
                ItemStack s = buildStack(ruleSnapshot);
                if (s != null) stacks.put(ruleSnapshot, s);
            } else {
                for (int r = 0; r < RULE_ROWS; r++) {
                    ItemStack s = buildStack(r);
                    if (s != null) stacks.put(r, s);
                }
            }

            if (stacks.isEmpty()) {
                return;
            }

            // Perform expensive pattern generation on a background thread
            Thread thread = new Thread(() -> {
                List<PreviewRow> rawRows = new ArrayList<>();
                for (java.util.Map.Entry<Integer, ItemStack> entry : stacks.entrySet()) {
                    if (Thread.currentThread().isInterrupted()) return;
                    int rule = entry.getKey();
                    List<WildcardPatternGenerator.GeneratedPattern> patterns =
                        WildcardPatternGenerator.generateRulePreviewPatterns(entry.getValue(), rule);
                    for (WildcardPatternGenerator.GeneratedPattern pattern : patterns) {
                        if (Thread.currentThread().isInterrupted()) return;
                        String materialName = pattern.materialName;
                        String excludeToken = getPreviewExcludeToken(pattern.inputStack, pattern.outputStack, materialName);
                        rawRows.add(
                            new PreviewRow(
                                rule,
                                materialName,
                                excludeToken,
                                getOutputExcludeToken(pattern.outputStack, materialName),
                                "R" + (rule + 1) + " " + summarize(pattern.inputStack, pattern.outputStack),
                                getOutputIdentity(pattern.outputStack),
                                formatPreviewStack(pattern.outputStack),
                                0));
                    }
                }
                Map<String, Integer> outputCounts = countOutputIdentities(rawRows);
                List<PreviewRow> result = new ArrayList<>();
                for (PreviewRow row : rawRows) {
                    int duplicateCount = getDuplicateCount(outputCounts, row.outputKey);
                    String line = formatPreviewLine(row.line, duplicateCount);
                    PreviewRow displayRow = new PreviewRow(
                        row.rule,
                        row.materialName,
                        row.excludeToken,
                        row.outputExcludeToken,
                        line,
                        row.outputKey,
                        row.outputLabel,
                        duplicateCount);
                    if (!filterSnapshot.isEmpty()
                        && !NechSearchCompat.matches(displayRow.line, filterSnapshot)
                        && !NechSearchCompat.matches(displayRow.materialName, filterSnapshot)
                        && !NechSearchCompat.matches(displayRow.excludeToken, filterSnapshot)
                        && !NechSearchCompat.matches(displayRow.outputExcludeToken, filterSnapshot)
                        && !NechSearchCompat.matches(displayRow.outputLabel, filterSnapshot)) {
                        continue;
                    }
                    result.add(displayRow);
                }
                sortDuplicateOutputsFirst(result);
                if (!Thread.currentThread().isInterrupted() && generation == this.asyncPreviewGeneration) {
                    this.asyncPreviewResult = new AsyncPreviewResult(generation, result);
                }
            }, "WildcardPreviewBuild");
            thread.setDaemon(true);
            thread.start();
            this.asyncPreviewThread = thread;
        }

        private void rebuildDedupe() {
            this.dedupeRows.clear();
            ItemStack preview = buildStack(null);
            if (preview == null) {
                return;
            }
            List<DedupeRow> rows = new ArrayList<>();
            Map<String, Integer> duplicateOutputs = collectDuplicateOutputCounts(preview);
            for (int rule = 0; rule < RULE_ROWS; rule++) {
                collectDuplicateRows(preview, rows, rule, duplicateOutputs);
            }
            sortDuplicateDedupeRowsFirst(rows);
            String filter = this.dedupeSearch == null ? "" : this.dedupeSearch.trim();
            for (DedupeRow row : rows) {
                if (filter.isEmpty() || row.matches(this, filter)) {
                    this.dedupeRows.add(row);
                }
            }
            if (this.dedupePageIndex >= getDedupePageCount()) {
                this.dedupePageIndex = Math.max(0, getDedupePageCount() - 1);
            }
        }

        private void refreshActivePage() {
            if (this.dedupePage) {
                rebuildDedupe();
            } else if (this.previewPage) {
                rebuildPreview();
            }
        }

        private PreviewRow getPreviewRow(int lineIndex) {
            // Apply background result when ready — called from string supplier each render frame
            AsyncPreviewResult pending = this.asyncPreviewResult;
            if (pending != null) {
                this.asyncPreviewResult = null;
                if (pending.generation == this.asyncPreviewGeneration) {
                    this.previewRows.clear();
                    this.previewRows.addAll(pending.rows);
                    if (this.previewPageIndex >= getPreviewPageCount()) {
                        this.previewPageIndex = Math.max(0, getPreviewPageCount() - 1);
                    }
                }
            }
            int absolute = this.previewPageIndex * PREVIEW_LINES + lineIndex;
            return absolute >= 0 && absolute < this.previewRows.size() ? this.previewRows.get(absolute) : null;
        }

        private DedupeRow getDedupeRow(int lineIndex) {
            int absolute = this.dedupePageIndex * DEDUPE_LINES + lineIndex;
            return absolute >= 0 && absolute < this.dedupeRows.size() ? this.dedupeRows.get(absolute) : null;
        }

        private Map<String, Integer> collectDuplicateOutputCounts(ItemStack preview) {
            List<PreviewRow> generatedRows = new ArrayList<>();
            for (int rule = 0; rule < RULE_ROWS; rule++) {
                for (WildcardPatternGenerator.GeneratedPattern pattern :
                    WildcardPatternGenerator.generateRulePreviewPatterns(preview, rule)) {
                    String outputKey = getOutputIdentity(pattern.outputStack);
                    if (outputKey.isEmpty()) {
                        continue;
                    }
                    generatedRows.add(new PreviewRow(rule, pattern.materialName, "", "", "", outputKey, "", 0));
                }
            }
            Map<String, Integer> outputCounts = countOutputIdentities(generatedRows);
            Map<String, Integer> duplicateOutputs = new java.util.LinkedHashMap<>();
            for (PreviewRow row : generatedRows) {
                int duplicateCount = getDuplicateCount(outputCounts, row.outputKey);
                if (duplicateCount <= 1) {
                    continue;
                }
                String key = getDedupeDuplicateKey(row.rule, row.materialName);
                Integer current = duplicateOutputs.get(key);
                if (current == null || duplicateCount > current.intValue()) {
                    duplicateOutputs.put(key, Integer.valueOf(duplicateCount));
                }
            }
            return duplicateOutputs;
        }

        private void collectDuplicateRows(
            ItemStack preview,
            List<DedupeRow> rows,
            int rule,
            Map<String, Integer> duplicateOutputs) {
            for (String materialName : WildcardPatternGenerator.getCandidateMaterials(preview, rule)) {
                String inputOreName = getOreName(this.inputs.get(rule), materialName);
                String outputOreName = getOreName(this.outputs.get(rule), materialName);
                boolean inputDuplicate = hasDuplicateOptions(inputOreName);
                boolean outputDuplicate = hasDuplicateOptions(outputOreName);
                if (!inputDuplicate && !outputDuplicate) {
                    continue;
                }
                int duplicateCount = getDuplicateCount(duplicateOutputs, getDedupeDuplicateKey(rule, materialName));
                rows.add(
                    new DedupeRow(
                        rule,
                        materialName,
                        inputOreName,
                        outputOreName,
                        inputDuplicate,
                        outputDuplicate,
                        duplicateCount));
            }
        }

        private String getDedupeDuplicateKey(int rule, String materialName) {
            return rule + "|" + (materialName == null ? "" : materialName);
        }

        private String getOreName(WildcardPatternEntry entry, String materialName) {
            if (entry == null || entry.isEmpty()) {
                return null;
            }
            ItemStack displayStack = entry.getDisplayStack();
            if (displayStack == null) {
                return null;
            }
            ItemData association = GTOreDictUnificator.getAssociation(displayStack);
            if (association != null && association.hasValidPrefixMaterialData()) {
                return getPrefixName(association.mPrefix) + materialName;
            }
            // Fallback for GT++ items not registered in the GT5 unificator
            int[] oreIds = OreDictionary.getOreIDs(displayStack);
            if (oreIds == null || oreIds.length == 0) {
                return null;
            }
            String bestPrefix = null;
            int bestPrefixLen = 0;
            for (int oreId : oreIds) {
                String oreName = OreDictionary.getOreName(oreId);
                if (oreName == null || oreName.isEmpty()) continue;
                for (gregtech.api.enums.OrePrefixes prefix : GTCompat.orePrefixes()) {
                    String prefixName = getPrefixName(prefix);
                    if (!prefixName.isEmpty()
                        && oreName.regionMatches(true, 0, prefixName, 0, prefixName.length())
                        && prefixName.length() > bestPrefixLen) {
                        bestPrefix = prefixName;
                        bestPrefixLen = prefixName.length();
                    }
                }
            }
            return bestPrefix == null ? null : bestPrefix + materialName;
        }

        private boolean hasDuplicateOptions(String oreName) {
            return oreName != null && OreDictionary.getOres(oreName) != null && OreDictionary.getOres(oreName).size() > 1;
        }

        private String getSelectedDedupeLabel(String oreName) {
            if (oreName == null || oreName.isEmpty()) {
                return "";
            }
            java.util.List<ItemStack> options = OreDictionary.getOres(oreName);
            if (options == null || options.isEmpty()) {
                return tr("gui.wildcardpattern.preview_empty");
            }
            ItemStack current = this.preferredOreStacks.get(oreName);
            ItemStack target = current != null ? current : getDefaultDedupeChoice(options);
            return formatDedupeChoice(target);
        }

        private void cycleDedupeChoice(String oreName) {
            if (oreName == null || oreName.isEmpty()) {
                return;
            }
            java.util.List<ItemStack> options = OreDictionary.getOres(oreName);
            if (options == null || options.isEmpty()) {
                return;
            }
            ItemStack current = this.preferredOreStacks.get(oreName);
            int nextIndex = 0;
            if (current != null) {
                for (int index = 0; index < options.size(); index++) {
                    if (OreDictionary.itemMatches(options.get(index), current, false)) {
                        nextIndex = (index + 1) % options.size();
                        break;
                    }
                }
            } else {
                ItemStack defaultChoice = getDefaultDedupeChoice(options);
                for (int index = 0; index < options.size(); index++) {
                    if (OreDictionary.itemMatches(options.get(index), defaultChoice, false)) {
                        nextIndex = (index + 1) % options.size();
                        break;
                    }
                }
            }
            ItemStack next = options.get(nextIndex);
            if (next != null) {
                this.preferredOreStacks.put(oreName, next.copy());
            }
            rebuildPreview();
            rebuildDedupe();
        }

        private String formatDedupeChoice(ItemStack stack) {
            if (stack == null) {
                return "";
            }
            GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(stack.getItem());
            String mod = id == null || id.modId == null || id.modId.isEmpty() ? "unknown" : id.modId;
            return "[" + mod + "] " + stack.getDisplayName();
        }

        private ItemStack getDefaultDedupeChoice(java.util.List<ItemStack> options) {
            for (ItemStack option : options) {
                if (option == null || option.getItem() == null) {
                    continue;
                }
                GameRegistry.UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor(option.getItem());
                if (id != null && "gregtech".equalsIgnoreCase(id.modId)) {
                    return option;
                }
            }
            return options.get(0);
        }

        private void excludePreviewRow(int lineIndex) {
            PreviewRow row = getPreviewRow(lineIndex);
            if (row == null) {
                return;
            }
            String token = row.hasDuplicateOutput() && !row.outputExcludeToken.isEmpty()
                ? row.outputExcludeToken
                : row.excludeToken.isEmpty() ? row.materialName : row.excludeToken;
            appendRuleExclude(row.rule, token);
            rebuildPreview();
        }


        private String getPreviewExcludeToken(ItemStack inputStack, ItemStack outputStack, String materialName) {
            String inputOre = getAssociatedOreName(inputStack);
            if (inputOre != null && !inputOre.isEmpty()) {
                return inputOre;
            }
            String outputOre = getAssociatedOreName(outputStack);
            if (outputOre != null && !outputOre.isEmpty()) {
                return outputOre;
            }
            return materialName == null ? "" : materialName;
        }

        private String getAssociatedOreName(ItemStack stack) {
            if (stack == null) {
                return null;
            }
            ItemData association = GTOreDictUnificator.getAssociation(stack);
            if (association != null && association.hasValidPrefixMaterialData()) {
                return getPrefixName(association.mPrefix) + association.mMaterial.mMaterial.mName;
            }
            // Fallback for GT++ items not registered in the GT5 unificator
            int[] oreIds = OreDictionary.getOreIDs(stack);
            if (oreIds == null || oreIds.length == 0) {
                return null;
            }
            for (int oreId : oreIds) {
                String oreName = OreDictionary.getOreName(oreId);
                if (oreName == null || oreName.isEmpty()) continue;
                for (gregtech.api.enums.OrePrefixes prefix : GTCompat.orePrefixes()) {
                    String prefixName = getPrefixName(prefix);
                    if (!prefixName.isEmpty() && oreName.regionMatches(true, 0, prefixName, 0, prefixName.length())) {
                        return oreName;
                    }
                }
            }
            return null;
        }

        private void appendRuleExclude(int rule, String value) {
            if (rule < 0 || rule >= this.ruleExcludes.size()) {
                return;
            }
            String token = value == null ? "" : value.trim();
            if (token.isEmpty()) {
                return;
            }
            List<String> tokens = new ArrayList<>();
            String current = this.ruleExcludes.get(rule);
            if (current != null && !current.trim().isEmpty()) {
                for (String part : current.split("[,;\\s]+")) {
                    String trimmed = part == null ? "" : part.trim();
                    if (!trimmed.isEmpty()) {
                        tokens.add(trimmed);
                    }
                }
            }
            for (String existing : tokens) {
                if (existing.equalsIgnoreCase(token)) {
                    return;
                }
            }
            tokens.add(token);
            this.ruleExcludes.set(rule, String.join(" ", tokens));
        }


        private void save() {
            ItemStack preview = buildStack(null);
            ItemStack held = getHeldStack();
            if (preview == null || held == null) {
                return;
            }
            WildcardPatternState.setExpandedPatternCount(preview, WildcardPatternGenerator.countPreviewPatterns(preview));
            WildcardPatternGenerator.markAsWildcard(held);
            WildcardPatternState.applyConfig(held, WildcardPatternState.exportConfig(preview));
            WildcardNetwork.CHANNEL.sendToServer(new MessageUpdateWildcardConfig(this.slot, WildcardPatternState.exportConfig(preview)));
        }

        private ItemStack buildStack(Integer onlyRule) {
            ItemStack held = getHeldStack();
            if (held == null) {
                return null;
            }
            ItemStack preview = held.copy();
            WildcardPatternGenerator.markAsWildcard(preview);

            if (onlyRule == null) {
                WildcardPatternState.setInputEntries(preview, this.inputs);
                WildcardPatternState.setOutputEntries(preview, this.outputs);
            } else {
                List<WildcardPatternEntry> inputs = new ArrayList<>();
                List<WildcardPatternEntry> outputs = new ArrayList<>();
                for (int i = 0; i < RULE_ROWS; i++) {
                    inputs.add(i == onlyRule.intValue() ? this.inputs.get(i) : WildcardPatternEntry.fromStack(null));
                    outputs.add(i == onlyRule.intValue() ? this.outputs.get(i) : WildcardPatternEntry.fromStack(null));
                }
                WildcardPatternState.setInputEntries(preview, inputs);
                WildcardPatternState.setOutputEntries(preview, outputs);
            }

            WildcardPatternConfig.apply(preview, this.globalExclude, this.ruleIncludes, this.ruleExcludes);
            for (java.util.Map.Entry<String, ItemStack> entry : this.preferredOreStacks.entrySet()) {
                WildcardPatternConfig.setPreferredOreStack(preview, entry.getKey(), entry.getValue());
            }
            return preview;
        }

        private ItemStack getHeldStack() {
            return this.player.inventory.getStackInSlot(this.slot);
        }
    }
}
