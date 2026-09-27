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

        TextFieldWidget[] inFields = new TextFieldWidget[ROWS];
        TextFieldWidget[] inAmounts = new TextFieldWidget[ROWS];
        TextFieldWidget[] outFields = new TextFieldWidget[ROWS];
        TextFieldWidget[] outAmounts = new TextFieldWidget[ROWS];
        final boolean[] inOre = new boolean[ROWS];
        final boolean[] outOre = new boolean[ROWS];
        for (int i = 0; i < ROWS; i++) {
            SmartWildcardState.Rule rule = i < base.rules.size() ? base.rules.get(i) : null;
            inOre[i] = rule == null || rule.oreDictMode;
            outOre[i] = rule == null || rule.outOreDictMode;
        }

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

        // ===================== 页面 2：覆盖预览（骨架，下一轮补搜索/翻页） =====================
        Flow pagePreview = Flow.column()
            .childPadding(GAP)
            .size(352, 226)
            .setEnabledIf(w -> PAGE == 1);
        pagePreview.child(new TextWidget<>(IKey.str("覆盖预览（搜索 / 翻页 / 逐行排除，下一轮补齐）")).size(348, 10));
        pagePreview.child(new TextWidget<>(IKey.str("当前计数见聊天栏「全部预览」或日志")).size(348, 10));

        // ===================== 页面 3：排除（骨架） =====================
        Flow pageExclude = Flow.column()
            .childPadding(GAP)
            .size(352, 226)
            .setEnabledIf(w -> PAGE == 2);
        pageExclude.child(new TextWidget<>(IKey.str("排除（总排除 / 规则级排除 / 不消耗物品，下一轮补齐）")).size(348, 10));

        TextFieldWidget blacklistField = new TextFieldWidget().setMaxLength(64)
            .size(180, 16);
        Flow blackRow = Flow.row()
            .childPadding(GAP)
            .size(348, 18);
        blackRow.child(new TextWidget<>(IKey.str("总排除")).size(50, 16))
            .child(blacklistField)
            .child(new ButtonWidget<>().size(30, 16)
                .overlay(IKey.str("加"))
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
            .child(new TextWidget<>(IKey.str("现有 " + blacklist.size() + " 项")).size(70, 16));
        pageExclude.child(blackRow);

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
    private static void saveRules(SmartWildcardState base, List<String> blacklist, TextFieldWidget[] inFields,
        TextFieldWidget[] inAmounts, TextFieldWidget[] outFields, TextFieldWidget[] outAmounts, boolean[] inOre,
        boolean[] outOre, int baseCircuit) {
        try {
            SmartWildcardState edited = new SmartWildcardState();
            edited.circuit = lastCircuit != null ? lastCircuit[0] : baseCircuit;
            edited.blacklist.addAll(blacklist);
            edited.whitelist.addAll(base.whitelist);
            edited.nonConsumed.addAll(base.nonConsumed);
            int used = 0;
            for (int i = 0; i < ROWS; i++) {
                String raw = inFields[i].getText();
                if (raw == null || raw.trim()
                    .isEmpty()) continue;
                edited.rules.add(
                    new SmartWildcardState.Rule(
                        used,
                        inOre[i],
                        stripModePrefix(raw),
                        Math.max(1L, parseLong(inAmounts[i].getText(), 1L)),
                        outFields[i].getText() == null ? "" : stripModePrefix(outFields[i].getText()),
                        outOre[i],
                        Math.max(0L, parseLong(outAmounts[i].getText(), 0L))));
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
