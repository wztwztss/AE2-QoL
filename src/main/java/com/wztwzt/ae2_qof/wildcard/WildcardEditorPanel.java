package com.wztwzt.ae2_qof.wildcard;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.SmartWildcardRulesPacket;

/**
 * 智能通配样板的 MUI2 配置界面（3.23.2：按用户敲定的设计稿重做为四页签）。
 *
 * <h2>页面结构（用户已确认）</h2>
 * <ol>
 * <li><b>规则</b>：9 行 ×（宽输入框 + 模式切换按钮 + 数量 + → + 宽输出框 + 模式切换按钮 + 数量 + 预/筛/x2/清），
 * 行高 20、控件间距 4，底部 全部预览 / 清空本页 / 重新载入 / 保存；</li>
 * <li><b>覆盖预览</b>：搜索 + 翻页 + 逐行「排除」+ 中文计数说明；</li>
 * <li><b>排除</b>：总排除 + 规则级排除 + 不消耗物品；</li>
 * <li><b>电路</b>：1~24 网格 + 优先级说明。</li>
 * </ol>
 *
 * <h2>页签怎么做的（MUI2 没有 TabWidget）</h2>
 * 用「4 个页签按钮 + 4 个页面容器」，页面容器用 {@code setEnabledIf} 按当前页号显隐：
 * 谓词是逐帧求值的，所以点页签即刻切换，且两端构建的**控件树完全一致**（只是启用状态不同），
 * 满足 MUI2 "两端树必须一致" 的要求。
 *
 * <h2>配色</h2>
 * 不再使用任何 {@code §} 颜色码：MUI2 面板是浅底，用默认深色文字最清晰（用户反馈过深灰字看不清）。
 */
public final class WildcardEditorPanel {

    /** 规则行数（与参考模版一致）。 */
    public static final int ROWS = 9;
    /** 行高与间距（不挤）。 */
    private static final int ROW_H = 20;
    private static final int GAP = 4;
    /** 预览每页条数。 */
    private static final int PAGE_SIZE = 8;

    /** 当前页签（客户端本地状态；控件树两端一致，仅启用状态不同）。 */
    /** 预览页一次最多构建多少行（再多的候选请用「全部预览」看聊天栏）。 */
    private static final int PREVIEW_LIST_MAX = 64;

    private static int PAGE = 0;
    /** 预览页的搜索词与页码（客户端本地）。 */
    private static String PREVIEW_SEARCH = "";
    private static int PREVIEW_PAGE = 0;
    /** 排除页当前编辑的规则号（1~9；0 = 总排除）。 */
    private static int EXCLUDE_RULE = 0;

    private WildcardEditorPanel() {}

    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager syncManager) {
        ModularPanel panel = new ModularPanel("ae2qol_wildcard_editor").size(360, 250);

        ItemStack stack = data.getUsedItemStack();
        SmartWildcardState state = SmartWildcardState.of(stack);
        if (state == null) state = new SmartWildcardState();
        final SmartWildcardState base = state;
        final List<String> blacklist = new ArrayList<>(base.blacklist);
        // 规则级排除与不消耗物品的工作副本：必须在**页签栏之前**声明（保存按钮的 lambda 要用到它们）
        final List<List<String>> ruleExcludes = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            ruleExcludes.add(
                i < base.rules.size() ? new ArrayList<>(base.rules.get(i).excludes) : new ArrayList<>());
        }
        // 不消耗物品在数据模型里是 List<ItemStack>（NBT 存真实物品），所以这里也用 ItemStack，
        // 加入方式 = NEI/背包拖到「加」按钮上，或「手持加入」；不再用文本框输名字（那会与模型不符）。
        final List<ItemStack> nonConsumed = new ArrayList<>();
        nonConsumed.addAll(base.nonConsumed);

        TextFieldWidget[] inFields = new TextFieldWidget[ROWS];
        TextFieldWidget[] inAmounts = new TextFieldWidget[ROWS];
        TextFieldWidget[] outFields = new TextFieldWidget[ROWS];
        TextFieldWidget[] outAmounts = new TextFieldWidget[ROWS];
        final boolean[] inOre = IN_ORE;
        final boolean[] outOre = OUT_ORE;
        java.util.Arrays.fill(IN_ORE, true);
        java.util.Arrays.fill(OUT_ORE, true);
        for (int i = 0; i < ROWS; i++) {
            SmartWildcardState.Rule rule = i < base.rules.size() ? base.rules.get(i) : null;
            IN_ORE[i] = rule == null || rule.oreDictMode;
            OUT_ORE[i] = rule == null || rule.outOreDictMode;
        }
        // 重建 NEI 拖放落点表（面板每次打开都重建，旧的控件已随界面销毁）
        DROP_TARGETS.clear();

        // ===================== 预览数据（构建期算一次，供预览页与计数说明用） =====================
        List<String> candidateNames = new ArrayList<>();
        SmartWildcardExpander.Result preview = null;
        try {
            if (stack != null) {
                ItemStack previewSource = stack.copy();
                base.write(previewSource);
                preview = SmartWildcardExpander.expand(previewSource, data.getWorld());
                for (ItemStack pattern : preview.patterns) {
                    if (candidateNames.size() >= PREVIEW_LIST_MAX) break;
                    String name = firstInputName(pattern);
                    if (name != null) candidateNames.add(name);
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 编辑器预览计算失败", t);
        }
        final String previewSummary = preview == null ? "预览不可用（见日志）" : describeZh(preview);
        final boolean previewTruncatedList = preview != null && preview.patterns.size() > candidateNames.size();

        // ===================== 页签栏 =====================
        Flow tabBar = Flow.row()
            .childPadding(GAP)
            .size(348, 18);
        String[] tabNames = { "规则", "覆盖预览", "排除", "电路" };
        for (int t = 0; t < tabNames.length; t++) {
            final int page = t;
            tabBar.child(new ButtonWidget<>().size(80, 16)
                .overlay(IKey.str(tabNames[t]))
                .onMouseTapped(ctx -> {
                    PAGE = page;
                    return true;
                }));
        }
        tabBar.child(new ButtonWidget<>().size(52, 16)
            .overlay(IKey.str("保存"))
            .tooltip(tip -> tip.addLine(IKey.str("把这四个页面的内容写进样板（服务端写入）")))
            .onMouseTapped(ctx -> {
                if (!data.isClient()) return true;
                saveRules(
                    base,
                    blacklist,
                    ruleExcludes,
                    nonConsumed,
                    inFields,
                    inAmounts,
                    outFields,
                    outAmounts,
                    inOre,
                    outOre,
                    base.circuit);
                return true;
            }));
        panel.child(tabBar);

        // ===================== 页面 1：规则 =====================
        Flow pageRules = Flow.column()
            .childPadding(GAP)
            .size(352, 226)
            .setEnabledIf(w -> PAGE == 0);
        pageRules.child(
            new TextWidget<>(IKey.str("输入匹配支持 ore:ingot* （矿辞）或 name:*锭 （显示名）；输出留空 = 自动配对")).size(348, 10));

        for (int i = 0; i < ROWS; i++) {
            SmartWildcardState.Rule rule = i < base.rules.size() ? base.rules.get(i) : null;

            final int index = i;
            TextFieldWidget inField = new TextFieldWidget().setMaxLength(64)
                .size(96, 16);
            inField.setText(rule == null ? "" : matchText(rule.matcher));
            TextFieldWidget inAmount = new TextFieldWidget().setMaxLength(9)
                .size(26, 16);
            inAmount.setText(rule == null ? "1" : String.valueOf(Math.max(1L, rule.amount)));
            TextFieldWidget outField = new TextFieldWidget().setMaxLength(64)
                .size(96, 16);
            outField.setText(rule == null ? "" : matchText(rule.outMatcher));
            TextFieldWidget outAmount = new TextFieldWidget().setMaxLength(9)
                .size(26, 16);
            outAmount.setText(rule == null || rule.outAmount <= 0 ? "" : String.valueOf(rule.outAmount));

            inFields[i] = inField;
            inAmounts[i] = inAmount;
            outFields[i] = outField;
            outAmounts[i] = outAmount;
            // 登记为 NEI 拖放落点：{行号, 侧}（侧 0=输入 1=输出）
            DROP_TARGETS.put(inField, new int[] { i, 0 });
            DROP_TARGETS.put(outField, new int[] { i, 1 });

            BooleanSyncValue inMode = new BooleanSyncValue(() -> inOre[index], v -> inOre[index] = v);
            BooleanSyncValue outMode = new BooleanSyncValue(() -> outOre[index], v -> outOre[index] = v);
            syncManager.syncValue("wc_in_mode_" + index, inMode);
            syncManager.syncValue("wc_out_mode_" + index, outMode);

            Flow row = Flow.row()
                .childPadding(GAP)
                .size(348, ROW_H);
            row.child(new TextWidget<>(IKey.str(String.valueOf(i + 1))).size(12, 16))
                .child(inField)
                .child(new ToggleButton().value(inMode)
                    .size(16, 16)
                    .tooltip(tip -> {
                        tip.addLine(IKey.str("输入模式：开 = 矿辞，关 = 显示名"));
                        tip.addLine(IKey.str("打开时匹配串按矿辞名比较（如 ingot*）"));
                    }))
                .child(inAmount)
                .child(new TextWidget<>(IKey.str("→")).size(10, 16))
                .child(outField)
                .child(new ToggleButton().value(outMode)
                    .size(16, 16)
                    .tooltip(tip -> tip.addLine(IKey.str("输出模式：开 = 矿辞，关 = 显示名"))))
                .child(outAmount)
                .child(new ButtonWidget<>().size(20, 16)
                    .overlay(IKey.str("预"))
                    .tooltip(tip -> tip.addLine(IKey.str("只按这一行试算（结果进聊天栏，不写回）")))
                    .onMouseTapped(ctx -> {
                        if (!data.isClient()) return true;
                        ae2qol$chat(
                            rowPreview(
                                stack,
                                inFields[index].getText(),
                                inAmounts[index].getText(),
                                inOre[index],
                                outFields[index].getText(),
                                outAmounts[index].getText(),
                                outOre[index]));
                        return true;
                    }))
                .child(new ButtonWidget<>().size(20, 16)
                    .overlay(IKey.str("筛"))
                    .tooltip(tip -> tip.addLine(IKey.str("把这一行的输入匹配串加入总排除（保存后生效）")))
                    .onMouseTapped(ctx -> {
                        String token = stripModePrefix(inFields[index].getText());
                        if (!token.isEmpty() && blacklist.add(token)) {
                            MyMod.LOG.info("[AE2QoL] 第 {} 行加入总排除：{}（保存后生效）", index + 1, token);
                        }
                        return true;
                    }))
                .child(new ButtonWidget<>().size(24, 16)
                    .overlay(IKey.str("x2"))
                    .onMouseTapped(ctx -> {
                        inAmounts[index].setText(String.valueOf(parseLong(inAmounts[index].getText(), 1L) * 2L));
                        long outNow = parseLong(outAmounts[index].getText(), parseLong(inAmounts[index].getText(), 1L));
                        outAmounts[index].setText(String.valueOf(outNow * 2L));
                        return true;
                    }))
                .child(new ButtonWidget<>().size(24, 16)
                    .overlay(IKey.str("清"))
                    .onMouseTapped(ctx -> {
                        inFields[index].setText("");
                        inAmounts[index].setText("1");
                        outFields[index].setText("");
                        outAmounts[index].setText("");
                        return true;
                    }));
            pageRules.child(row);
        }

        Flow rulesBottom = Flow.row()
            .childPadding(GAP)
            .size(348, 18);
        rulesBottom.child(new ButtonWidget<>().size(60, 16)
            .overlay(IKey.str("全部预览"))
            .tooltip(tip -> tip.addLine(IKey.str("把完整候选与计数打到聊天栏")))
            .onMouseTapped(ctx -> {
                if (!data.isClient()) return true;
                ae2qol$chat(fullPreview(stack, base));
                return true;
            }))
            .child(new ButtonWidget<>().size(60, 16)
                .overlay(IKey.str("清空本页"))
                .onMouseTapped(ctx -> {
                    for (int i = 0; i < ROWS; i++) {
                        inFields[i].setText("");
                        inAmounts[i].setText("1");
                        outFields[i].setText("");
                        outAmounts[i].setText("");
                    }
                    return true;
                }))
            .child(new ButtonWidget<>().size(60, 16)
                .overlay(IKey.str("重新载入"))
                .tooltip(tip -> {
                    tip.addLine(IKey.str("放弃未保存的修改，重新从样板读取"));
                    tip.addLine(IKey.str("MUI2 面板不能就地重建，所以这会关闭界面：再右键打开即可看到最新内容"));
                })
                .onMouseTapped(ctx -> {
                    if (!data.isClient()) return true;
                    MyMod.LOG.info("[AE2QoL] 编辑器「重新载入」：关闭界面，请再次右键样板打开以读取最新内容");
                    ae2qol$chat("已放弃未保存修改：请再右键样板打开界面");
                    net.minecraft.client.Minecraft.getMinecraft()
                        .displayGuiScreen(null);
                    return true;
                }));
        pageRules.child(rulesBottom);

        // ===================== 页面 2：覆盖预览（搜索 + 翻页 + 逐行排除） =====================
        // MUI2 面板不能在原地重建，所以「搜索」和「翻页」都用 setEnabledIf 的**逐帧谓词**：
        // 谓词在求值时读取搜索框的当前文本与 PREVIEW_PAGE，因此点按钮/打字即刻生效。
        Flow pagePreview = Flow.column()
            .childPadding(GAP)
            .size(352, 226)
            .setEnabledIf(w -> PAGE == 1);

        final TextFieldWidget searchField = new TextFieldWidget().setMaxLength(48)
            .size(180, 16);
        Flow previewTop = Flow.row()
            .childPadding(GAP)
            .size(348, 18);
        previewTop.child(searchField)
            .child(new ButtonWidget<>().size(40, 16)
                .overlay(IKey.str("上一页"))
                .onMouseTapped(ctx -> {
                    if (PREVIEW_PAGE > 0) PREVIEW_PAGE--;
                    return true;
                }))
            .child(new ButtonWidget<>().size(40, 16)
                .overlay(IKey.str("下一页"))
                .onMouseTapped(ctx -> {
                    PREVIEW_PAGE++;
                    return true;
                }))
            .child(new TextWidget<>(IKey.str("搜索：显示名/矿辞包含即可")).size(120, 16));
        pagePreview.child(previewTop);
        pagePreview.child(new TextWidget<>(IKey.str(previewSummary)).size(348, 10));

        final int totalPages = Math.max(1, (candidateNames.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        pagePreview.child(
            new TextWidget<>(
                IKey.str("共 " + candidateNames.size() + " 个候选，每页 " + PAGE_SIZE + " 个，共 " + totalPages + " 页"
                    + (previewTruncatedList ? "（列表只列前 " + PREVIEW_LIST_MAX + " 个，其余见「全部预览」与日志）" : "")))
                .size(348, 10));

        if (candidateNames.isEmpty()) {
            pagePreview.child(
                new TextWidget<>(IKey.str("当前没有覆盖任何候选 —— 上方已给出原因（常见：规则为空，或匹配串没有通配符）"))
                    .size(348, 10));
        } else {
            for (int idx = 0; idx < candidateNames.size(); idx++) {
                final String candidateName = candidateNames.get(idx);
                final int candidateIndex = idx;
                Flow line = Flow.row()
                    .childPadding(GAP)
                    .size(348, 14);
                line.child(new TextWidget<>(IKey.str(candidateName)).size(280, 12))
                    .child(new ButtonWidget<>().size(28, 12)
                        .overlay(IKey.str("排除"))
                        .tooltip(tip -> tip.addLine(IKey.str("把「" + candidateName + "」加入总排除（保存后生效）")))
                        .onMouseTapped(ctx -> {
                            if (blacklist.add(candidateName)) {
                                MyMod.LOG.info("[AE2QoL] 预览排除：已加入黑名单 {}（保存后生效）", candidateName);
                            }
                            return true;
                        }));
                // 搜索过滤（读搜索框当前文本）+ 分页（读 PREVIEW_PAGE）
                line.setEnabledIf(
                    w -> ae2qol$matchSearch(
                        searchField.getText(),
                        candidateName) && candidateIndex / PAGE_SIZE == PREVIEW_PAGE);
                pagePreview.child(line);
            }
        }

        // ===================== 页面 3：排除（总排除 / 规则级排除 / 不消耗物品） =====================
        Flow pageExclude = Flow.column()
            .childPadding(GAP)
            .size(352, 226)
            .setEnabledIf(w -> PAGE == 2);

        // 工作副本 ruleExcludes / nonConsumed 已在方法开头声明（保存按钮的 lambda 要用）

        // ---- A. 总排除（全局黑名单，所有规则共用）----
        pageExclude.child(new TextWidget<>(IKey.str("总排除：所有规则共用（优先于规则级排除）")).size(348, 10));
        TextFieldWidget blacklistField = new TextFieldWidget().setMaxLength(64)
            .size(170, 16);
        Flow blackRow = Flow.row()
            .childPadding(GAP)
            .size(348, 18);
        blackRow.child(blacklistField)
            .child(new ButtonWidget<>().size(60, 16)
                .overlay(IKey.str("加"))
                .tooltip(tip -> {
                    tip.addLine(IKey.str("加入总排除（支持 * 与 ?）"));
                    tip.addLine(IKey.str("也可以把 NEI/背包里的物品直接拖到本按钮上"));
                })
                .onMouseTapped(ctx -> {
                    String v = blacklistField.getText();
                    if (v != null && !v.trim()
                        .isEmpty() && !blacklist.contains(
                            v.trim())) {
                        blacklist.add(
                            v.trim());
                        blacklistField.setText("");
                    }
                    return true;
                }))
            .child(new ButtonWidget<>().size(40, 16)
                .overlay(IKey.str("清空"))
                .onMouseTapped(ctx -> {
                    blacklist.clear();
                    return true;
                }))
            .child(new TextWidget<>(IKey.str("现有 " + blacklist.size() + " 项")).size(60, 16));
        pageExclude.child(blackRow);
        int shownBlack = 0;
        for (String token : new ArrayList<>(blacklist)) {
            if (shownBlack >= 6) break;
            final String entry = token;
            Flow entryLine = Flow.row()
                .childPadding(GAP)
                .size(348, 14);
            entryLine.child(new TextWidget<>(IKey.str("- " + entry)).size(280, 12))
                .child(new ButtonWidget<>().size(28, 12)
                    .overlay(IKey.str("删"))
                    .onMouseTapped(ctx -> {
                        blacklist.remove(entry);
                        MyMod.LOG.info("[AE2QoL] 总排除移除：{}（保存后生效）", entry);
                        return true;
                    }));
            pageExclude.child(entryLine);
            shownBlack++;
        }
        if (blacklist.size() > shownBlack) {
            pageExclude.child(new TextWidget<>(IKey.str("… 另有 " + (blacklist.size() - shownBlack) + " 项")).size(348, 10));
        }

        // ---- B. 规则级排除（选中哪条规则就编辑哪条）----
        pageExclude.child(new TextWidget<>(IKey.str("规则级排除：只对该条规则生效（点上面的规则号切换）")).size(348, 10));
        Flow ruleSelector = Flow.row()
            .childPadding(GAP)
            .size(348, 18);
        final int[] selected = { EXCLUDE_RULE };
        Flow[] ruleBars = new Flow[ROWS + 1];
        for (int r = -1; r < ROWS; r++) {
            final int ruleNo = r + 1; // 0 = 总排除（不在此编辑）；1..9 = 规则号
            if (ruleNo == 0) continue;
            ruleSelector.child(new ButtonWidget<>().size(24, 16)
                .overlay(IKey.str(String.valueOf(ruleNo)))
                .onMouseTapped(ctx -> {
                    EXCLUDE_RULE = ruleNo;
                    selected[0] = ruleNo;
                    return true;
                }));
        }
        pageExclude.child(ruleSelector);
        TextFieldWidget ruleExcludeField = new TextFieldWidget().setMaxLength(64)
            .size(170, 16);
        pageExclude.child(new TextWidget<>(IKey.str("当前编辑：规则 1~9（点上方数字切换；条目显示在下方）")).size(348, 10));
        pageExclude.child(new TextWidget<>(IKey.str("提示：规则级排除同样支持 * 与 ?，例如在 ingot* 规则里写 Aluminium")).size(348, 10));
        for (int i = 0; i < ROWS; i++) {
            final int ruleIdx = i;
            Flow bar = Flow.row()
                .childPadding(GAP)
                .size(348, 18);
            bar.child(new TextWidget<>(IKey.str("规则 " + (i + 1))).size(46, 16))
                .child(ruleExcludeField)
                .child(new ButtonWidget<>().size(40, 16)
                    .overlay(IKey.str("加"))
                    .onMouseTapped(ctx -> {
                        int target = Math.max(1, EXCLUDE_RULE) - 1;
                        String v = ruleExcludeField.getText();
                        if (v != null && !v.trim()
                            .isEmpty()) {
                            List<String> list = ruleExcludes.get(target);
                            if (!list.contains(
                                v.trim())) {
                                list.add(
                                    v.trim());
                                MyMod.LOG
                                    .info("[AE2QoL] 规则 {} 排除加入：{}（保存后生效）", target + 1, v.trim());
                            }
                            ruleExcludeField.setText("");
                        }
                        return true;
                    }))
                .child(new ButtonWidget<>().size(40, 16)
                    .overlay(IKey.str("清空"))
                    .onMouseTapped(ctx -> {
                        ruleExcludes.get(ruleIdx)
                            .clear();
                        return true;
                    }))
                .child(new TextWidget<>(IKey.str("共 " + ruleExcludes.get(i).size() + " 项")).size(50, 16));
            // 只显示当前选中规则的这一行（谓词逐帧求值）
            bar.setEnabledIf(w -> EXCLUDE_RULE == ruleIdx + 1);
            pageExclude.child(bar);
        }

        // ---- C. 不消耗物品（铸模/模头/透镜等；按显示名记录，NEI/背包可拖入）----
        pageExclude.child(new TextWidget<>(IKey.str("不消耗物品：把物品拖到「加」按钮上，或用「手持加入」")).size(348, 10));
        TextFieldWidget ncField = new TextFieldWidget().setMaxLength(64)
            .size(0, 0);
        Flow ncRow = Flow.row()
            .childPadding(GAP)
            .size(348, 18);
        ncRow.child(new ButtonWidget<>().size(66, 16)
            .overlay(IKey.str("加（拖入）"))
            .tooltip(tip -> tip.addLine(IKey.str("把 NEI/背包里的物品拖到本按钮上即可加入")))
            .onMouseTapped(ctx -> {
                MyMod.LOG.info("[AE2QoL] 不消耗物品：「加」按钮被点击（加入方式是把物品拖到它上面，或用「手持加入」）");
                return true;
            }))
            .child(new ButtonWidget<>().size(66, 16)
                .overlay(IKey.str("手持加入"))
                .tooltip(tip -> tip.addLine(IKey.str("把主手物品记为不消耗（铸模/模头/透镜等）")))
                .onMouseTapped(ctx -> {
                    if (!data.isClient()) return true;
                    try {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        ItemStack held = mc.thePlayer == null ? null : mc.thePlayer.getCurrentEquippedItem();
                        if (held == null) {
                            MyMod.LOG.warn("[AE2QoL] 不消耗物品：主手为空，未加入");
                            ae2qol$chat("主手没有物品");
                        } else {
                            nonConsumed.add(held.copy());
                            MyMod.LOG.info(
                                "[AE2QoL] 不消耗物品加入（手持）：{}（保存后生效）",
                                held.getDisplayName());
                            ae2qol$chat("已加入不消耗物品：" + held.getDisplayName() + "（点保存写回）");
                        }
                    } catch (Throwable t) {
                        MyMod.LOG.warn("[AE2QoL] 不消耗物品加入失败", t);
                    }
                    return true;
                }))
            .child(new ButtonWidget<>().size(40, 16)
                .overlay(IKey.str("清空"))
                .onMouseTapped(ctx -> {
                    nonConsumed.clear();
                    return true;
                }))
            .child(new TextWidget<>(IKey.str("现有 " + nonConsumed.size() + " 项")).size(60, 16));
        pageExclude.child(ncRow);
        int shownNc = 0;
        for (ItemStack item : new ArrayList<>(nonConsumed)) {
            if (shownNc >= 6) break;
            final ItemStack entry = item;
            String label = item == null || item.getItem() == null ? "(空)" : item.getDisplayName();
            Flow entryLine = Flow.row()
                .childPadding(GAP)
                .size(348, 14);
            entryLine.child(new TextWidget<>(IKey.str("- " + label)).size(280, 12))
                .child(new ButtonWidget<>().size(28, 12)
                    .overlay(IKey.str("删"))
                    .onMouseTapped(ctx -> {
                        nonConsumed.remove(entry);
                        MyMod.LOG.info("[AE2QoL] 不消耗物品移除：{}（保存后生效）", label);
                        return true;
                    }));
            pageExclude.child(entryLine);
            shownNc++;
        }

        // ===================== 页面 4：电路 =====================
        Flow pageCircuit = Flow.column()
            .childPadding(GAP)
            .size(352, 226)
            .setEnabledIf(w -> PAGE == 3);
        pageCircuit.child(new TextWidget<>(IKey.str("内置编程电路（点选；优先级：样板自带 > 槽位 > 整机）")).size(348, 10));
        final int[] circuit = { base.circuit };
        for (int rowIdx = 0; rowIdx < 3; rowIdx++) {
            Flow line = Flow.row()
                .childPadding(GAP)
                .size(348, 20);
            for (int col = 0; col < 8; col++) {
                final int number = rowIdx * 8 + col + 1;
                line.child(new ButtonWidget<>().size(30, 18)
                    .overlay(IKey.str(String.valueOf(number)))
                    .onMouseTapped(ctx -> {
                        circuit[0] = number;
                        MyMod.LOG.info("[AE2QoL] 编辑器选择电路 {}（点保存写回）", number);
                        return true;
                    }));
            }
            pageCircuit.child(line);
        }
        pageCircuit.child(new ButtonWidget<>().size(80, 18)
            .overlay(IKey.str("清除（继承）"))
            .onMouseTapped(ctx -> {
                circuit[0] = -1;
                return true;
            }));

        // ===================== 组装：滚动容器 + 四个页面 =====================
        Flow pages = Flow.column()
            .childPadding(0)
            .size(352, 230);
        pages.child(pageRules)
            .child(pagePreview)
            .child(pageExclude)
            .child(pageCircuit);

        ScrollWidget<?> scroll = new ScrollWidget<>(new VerticalScrollData());
        scroll.size(352, 226);
        scroll.getScrollArea()
            .getScrollY()
            .setScrollSize(230);
        scroll.child(pages);
        panel.child(scroll);

        // 每帧把电路页的选择同步回保存用的引用（简单起见：保存时读 lastCircuit）
        lastCircuit = circuit;
        return panel;
    }

    /** 电路页当前选择（保存时读取；由 build 注入）。 */
    private static int[] lastCircuit;

    /** 保存：客户端解析四页内容 → 既有 C2S 包 → 服务端写 NBT。 */
    private static void saveRules(SmartWildcardState base, List<String> blacklist, List<List<String>> ruleExcludes,
        List<ItemStack> nonConsumed, TextFieldWidget[] inFields, TextFieldWidget[] inAmounts,
        TextFieldWidget[] outFields, TextFieldWidget[] outAmounts, boolean[] inOre, boolean[] outOre,
        int baseCircuit) {
        try {
            SmartWildcardState edited = new SmartWildcardState();
            edited.circuit = lastCircuit != null ? lastCircuit[0] : baseCircuit;
            edited.blacklist.addAll(blacklist);
            edited.whitelist.addAll(base.whitelist);
            // 不消耗物品用界面上的工作副本（含「手持加入」新加的），不再直接抄原值
            edited.nonConsumed.addAll(nonConsumed);
            int used = 0;
            for (int i = 0; i < ROWS; i++) {
                String raw = inFields[i].getText();
                if (raw == null || raw.trim()
                    .isEmpty()) continue;
                SmartWildcardState.Rule saved = new SmartWildcardState.Rule(
                    used,
                    inOre[i],
                    stripModePrefix(raw),
                    Math.max(1L, parseLong(inAmounts[i].getText(), 1L)),
                    outFields[i].getText() == null ? "" : stripModePrefix(outFields[i].getText()),
                    outOre[i],
                    Math.max(0L, parseLong(outAmounts[i].getText(), 0L)));
                // 3.23.2：规则级排除（按行号对应）一并写回，否则"能编辑却不保存"
                saved.excludes.addAll(ruleExcludes.get(i));
                edited.rules.add(saved);
                used++;
            }
            ModNetwork.CHANNEL.sendToServer(new SmartWildcardRulesPacket(edited));
            MyMod.LOG.info("[AE2QoL] 通配样板编辑器已提交：rules={} circuit={}", edited.rules.size(), edited.circuit);
            ae2qol$chat("已提交保存：rules=" + edited.rules.size() + "，电路=" + edited.circuit + "（见日志确认写回）");
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 编辑器保存失败", t);
            ae2qol$chat("保存失败：" + t);
        }
    }

    /** 全部预览：完整候选（前 20 个）+ 计数进聊天栏。 */
    private static String fullPreview(ItemStack stack, SmartWildcardState base) {
        try {
            if (stack == null) return "没有可用的样板物品";
            ItemStack temp = stack.copy();
            base.write(temp);
            SmartWildcardExpander.Result result = SmartWildcardExpander
                .expand(temp, net.minecraft.client.Minecraft.getMinecraft().theWorld);
            StringBuilder sb = new StringBuilder("[AE2QoL] ").append(describeZh(result));
            int shown = 0;
            for (ItemStack pattern : result.patterns) {
                if (shown >= 20) break;
                String name = firstInputName(pattern);
                if (name == null) continue;
                sb.append(" | ")
                    .append(name);
                shown++;
            }
            return sb.toString();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 全部预览失败", t);
            return "全部预览失败：" + t;
        }
    }

    /** 单行试算。 */
    private static String rowPreview(ItemStack baseStack, String inText, String inAmount, boolean inOre,
        String outText, String outAmount, boolean outOre) {
        try {
            if (baseStack == null) return "没有可用的样板物品";
            String raw = inText == null ? "" : inText.trim();
            if (raw.isEmpty()) return "这一行还没有输入匹配串";
            ItemStack temp = baseStack.copy();
            temp.stackSize = 1;
            SmartWildcardState state = new SmartWildcardState();
            state.rules.add(
                new SmartWildcardState.Rule(
                    0,
                    inOre,
                    stripModePrefix(raw),
                    Math.max(1L, parseLong(inAmount, 1L)),
                    outText == null ? "" : stripModePrefix(outText),
                    outOre,
                    Math.max(0L, parseLong(outAmount, 0L))));
            state.write(temp);
            SmartWildcardExpander.Result result = SmartWildcardExpander
                .expand(temp, net.minecraft.client.Minecraft.getMinecraft().theWorld);
            StringBuilder sb = new StringBuilder("[AE2QoL] 试算 ").append(describeZh(result));
            int shown = 0;
            for (ItemStack pattern : result.patterns) {
                if (shown >= 5) break;
                String name = firstInputName(pattern);
                if (name == null) continue;
                sb.append(" | ")
                    .append(name);
                shown++;
            }
            return sb.toString();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 单行试算失败", t);
            return "试算失败：" + t;
        }
    }

    /** 把结果翻成中文可读说明（用户反馈过只看英文 reason 看不懂）。 */
    public static String describeZh(SmartWildcardExpander.Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("产出 ")
            .append(result.patterns.size())
            .append(" 张，命中候选 ")
            .append(result.matchedCandidates)
            .append(" 个");
        if (result.skippedMissingCounterpart > 0) sb.append("，因对侧矿辞缺失跳过 ").append(result.skippedMissingCounterpart);
        if (result.truncated) sb.append("，已达展开上限被截断（可调大 smart_wildcard_expand_cap）");
        if (result.reason != null && !result.reason.isEmpty()) {
            sb.append("；原因：")
                .append(reasonZh(result.reason));
        }
        return sb.toString();
    }

    /** 结果原因码 → 中文说明。 */
    private static String reasonZh(String reason) {
        if (reason.startsWith("no-material-matched"))
            return "输入匹配串没有通配符（或推不出材料名），只能按精确匹配处理";
        if (reason.startsWith("no-rules")) return "这张样板还没有任何规则";
        if (reason.startsWith("not-a-smart-wildcard")) return "这不是智能通配样板";
        if (reason.startsWith("no-template-nbt")) return "样板里没有模板配方（请先用 NEI 加号写入）";
        if (reason.startsWith("template-in-out-missing")) return "模板缺少输入或输出（请先用 NEI 加号写入）";
        return reason;
    }

    private static String matchText(String matcher) {
        return matcher == null ? "" : matcher;
    }

    private static String stripModePrefix(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("ore:")) return s.substring(4)
            .trim();
        if (s.startsWith("name:")) return s.substring(5)
            .trim();
        return s;
    }

    /** 搜索匹配：搜索词为空则全通过；否则显示名/矿辞包含即命中（不区分大小写）。 */
    private static boolean ae2qol$matchSearch(String query, String candidateName) {
        String q = query == null ? "" : query.trim()
            .toLowerCase();
        if (q.isEmpty()) return true;
        return candidateName != null && candidateName.toLowerCase()
            .contains(q);
    }

    /** 输入/输出模式（跨页签共享；NEI 拖入时会按物品自动切换）。 */
    private static final boolean[] IN_ORE = new boolean[ROWS];
    private static final boolean[] OUT_ORE = new boolean[ROWS];
    /** NEI 拖放落点：匹配框控件 → {行号, 侧(0=输入,1=输出)}（每次打开面板重建）。 */
    private static final java.util.Map<Object, int[]> DROP_TARGETS = new java.util.HashMap<>();

    /**
     * NEI 把物品拖到某个匹配框上时调用（由 {@code SmartWildcardNeiDragHandler} 转发）。
     *
     * <p>写入规则：物品有矿辞 ⇒ 取 GT 权威的矿辞前缀写成 {@code <前缀>*} 并把该行切到**矿辞模式**；
     * 没有矿辞 ⇒ 写成显示名并把该行切到**名称模式**。返回是否消费了这次拖放（未消费则 NEI 继续走默认行为）。
     */
    public static boolean applyDropToHovered(Object hoveredWidget, ItemStack stack) {
        try {
            if (hoveredWidget == null || stack == null || stack.getItem() == null) return false;
            int[] pos = DROP_TARGETS.get(hoveredWidget);
            if (pos == null) return false;
            if (!(hoveredWidget instanceof TextFieldWidget field)) return false;

            String orePrefix = null;
            try {
                java.util.List<gregtech.api.enums.OrePrefixes.ParsedOreDictName> parsed =
                    gregtech.api.enums.OrePrefixes.detectPrefix(stack);
                if (parsed != null) {
                    for (gregtech.api.enums.OrePrefixes.ParsedOreDictName name : parsed) {
                        if (name == null || name.prefix == null) continue;
                        String key = name.prefix.getOreprefixKey();
                        if (key != null && !key.isEmpty()) {
                            orePrefix = key;
                            break;
                        }
                    }
                }
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] NEI 拖入：读取矿辞前缀失败，改按显示名处理", t);
            }

            boolean oreMode = orePrefix != null;
            String matcher = oreMode ? orePrefix + "*"
                : String.valueOf(
                    stack.getItem()
                        .getItemStackDisplayName(stack));
            field.setText(matcher);
            if (pos[1] == 0) {
                IN_ORE[pos[0]] = oreMode;
            } else {
                OUT_ORE[pos[0]] = oreMode;
            }
            MyMod.LOG.info(
                "[AE2QoL] NEI 拖入：第 {} 行{} ← {}（{} 模式，匹配串 {}）",
                pos[0] + 1,
                pos[1] == 0 ? "输入" : "输出",
                stack.getItem()
                    .getItemStackDisplayName(stack),
                oreMode ? "矿辞" : "显示名",
                matcher);
            return true;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] NEI 拖入处理失败", t);
            return false;
        }
    }

    /** 聊天栏回执（仅客户端）。 */
    private static void ae2qol$chat(String message) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(message));
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 聊天栏回执失败", t);
        }
    }

    /** 取展开产物的第一个输入物品显示名（预览与排除词用）。 */
    private static String firstInputName(ItemStack patternStack) {
        try {
            if (patternStack == null || patternStack.getTagCompound() == null) return null;
            net.minecraft.nbt.NBTTagList in = patternStack.getTagCompound()
                .getTagList("in", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
            if (in.tagCount() == 0) return null;
            ItemStack first = ItemStack.loadItemStackFromNBT(in.getCompoundTagAt(0));
            if (first == null || first.getItem() == null) return null;
            return String.valueOf(
                first.getItem()
                    .getItemStackDisplayName(first));
        } catch (Throwable t) {
            return null;
        }
    }

    private static long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(text == null ? "" : text.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
