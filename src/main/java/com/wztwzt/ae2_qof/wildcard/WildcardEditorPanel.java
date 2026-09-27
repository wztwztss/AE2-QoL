package com.wztwzt.ae2_qof.wildcard;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.SmartWildcardRulesPacket;

/**
 * 智能通配样板的 MUI2 配置界面（3.23.0；3.23.1 按用户反馈重做布局与配色）。
 *
 * <h2>这次修了什么（都有截图证据）</h2>
 * <ol>
 * <li><b>内容只画出标题/表头</b>：子 {@code Flow} 没有显式尺寸 ⇒ MUI2 按 0 高布局，9 行规则与后续内容全部不见。
 * 现在**每个容器与每个子控件都写了 size**，并用 {@link ScrollWidget} 承载超高的内容（可滚动）。</li>
 * <li><b>字色不清</b>：此前在浅色面板上用了 {@code §7}/{@code §b} 等颜色码，深灰字几乎看不清。
 * 现在全部去掉颜色码，沿用 MUI2 默认的深色文字（与参考模组"浅底深字"一致），只在按钮标签上保留最简文字。</li>
 * <li><b>按钮不全</b>：现在每行固定四个动作（预 / 筛 / x2 / 清），底部有 全部预览 / 保存，
 * 黑名单区有 添加 / 清空 / 逐行删除，另有 电路 与 试算。</li>
 * </ol>
 *
 * <h2>不变的行为约定</h2>
 * 匹配串用 {@code ore:ingot*} / {@code name:*锭} 前缀自解释模式；输出留空 = 沿用模板输出自动配对；
 * 保存走既有 C2S 包由**服务端**写入样板 NBT；所有非法输入都按默认值处理并在日志留痕。
 */
public final class WildcardEditorPanel {

    /** 规则行数（与参考模版一致）。 */
    public static final int ROWS = 9;
    /** 行高（含文本字段）。 */
    private static final int ROW_H = 14;
    /** 预览最多列出的行数（其余在聊天栏与日志计数里）。 */
    private static final int PREVIEW_ROWS = 6;

    private WildcardEditorPanel() {}

    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager syncManager) {
        ModularPanel panel = new ModularPanel("ae2qol_wildcard_editor").size(346, 244);

        ItemStack stack = data.getUsedItemStack();
        SmartWildcardState state = SmartWildcardState.of(stack);
        if (state == null) state = new SmartWildcardState();
        final SmartWildcardState base = state;
        final List<String> blacklist = new ArrayList<>(base.blacklist);

        TextFieldWidget[] inFields = new TextFieldWidget[ROWS];
        TextFieldWidget[] inAmounts = new TextFieldWidget[ROWS];
        TextFieldWidget[] outFields = new TextFieldWidget[ROWS];
        TextFieldWidget[] outAmounts = new TextFieldWidget[ROWS];

        // 内容高度：标题(12)+写法(12)+表头(12)+9行*14+电路(14)+黑名单(14)+黑名单条目(≤6*12)
        //           +预览标题(12)+预览条目(≤6*12)+底部按钮(16)+提示(12)
        int previewNameCount = 0;
        List<String> previewNames = new ArrayList<>();
        SmartWildcardExpander.Result preview = null;
        try {
            if (stack != null) {
                ItemStack previewSource = stack.copy();
                base.write(previewSource);
                preview = SmartWildcardExpander.expand(previewSource, data.getWorld());
                for (ItemStack pattern : preview.patterns) {
                    if (previewNames.size() >= PREVIEW_ROWS) break;
                    String name = firstInputName(pattern);
                    if (name != null) previewNames.add(name);
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 编辑器预览计算失败", t);
        }
        previewNameCount = previewNames.size();

        int blackShown = Math.min(blacklist.size(), PREVIEW_ROWS);
        int contentH = 12 + 12 + 12 + ROWS * ROW_H + 16 + 16 + blackShown * 12 + 12 + previewNameCount * 12 + 18 + 12 + 8;

        Flow content = Flow.column()
            .childPadding(2)
            .size(330, contentH);

        // ===== 标题与写法说明（默认深色字，浅底清晰）=====
        content.child(new TextWidget<>(IKey.str("智能通配样板 · 规则（可手写；也可在 NEI 里按加号自动填入）")).size(320, 12));
        content.child(new TextWidget<>(IKey.str("写法：ore:ingot* = 矿辞模式，name:*锭 = 显示名模式；输出留空 = 自动配对")).size(320, 12));

        // ===== 表头 =====
        Flow header = Flow.row()
            .childPadding(2)
            .size(320, 12);
        header.child(new TextWidget<>(IKey.str("#")).size(10, 12))
            .child(new TextWidget<>(IKey.str("输入匹配")).size(76, 12))
            .child(new TextWidget<>(IKey.str("数量")).size(24, 12))
            .child(new TextWidget<>(IKey.str("→")).size(8, 12))
            .child(new TextWidget<>(IKey.str("输出匹配")).size(76, 12))
            .child(new TextWidget<>(IKey.str("数量")).size(24, 12))
            .child(new TextWidget<>(IKey.str("操作")).size(88, 12));
        content.child(header);

        // ===== 9 行规则 =====
        for (int i = 0; i < ROWS; i++) {
            SmartWildcardState.Rule rule = i < base.rules.size() ? base.rules.get(i) : null;

            TextFieldWidget inField = new TextFieldWidget().setMaxLength(64)
                .size(76, 12);
            inField.setText(rule == null ? "" : formatMatcher(rule.oreDictMode, rule.matcher));
            TextFieldWidget inAmount = new TextFieldWidget().setMaxLength(9)
                .size(24, 12);
            inAmount.setText(rule == null ? "1" : String.valueOf(Math.max(1L, rule.amount)));
            TextFieldWidget outField = new TextFieldWidget().setMaxLength(64)
                .size(76, 12);
            outField.setText(rule == null ? "" : formatMatcher(rule.outOreDictMode, rule.outMatcher));
            TextFieldWidget outAmount = new TextFieldWidget().setMaxLength(9)
                .size(24, 12);
            outAmount.setText(rule == null || rule.outAmount <= 0 ? "" : String.valueOf(rule.outAmount));

            inFields[i] = inField;
            inAmounts[i] = inAmount;
            outFields[i] = outField;
            outAmounts[i] = outAmount;

            final int index = i;
            ButtonWidget<?> previewRow = new ButtonWidget<>().size(20, 12)
                .overlay(IKey.str("预"))
                .tooltip(t -> t.addLine(IKey.str("只按这一行试算，结果打进聊天栏（不写回）")))
                .onMouseTapped(ctx -> {
                    if (!data.isClient()) return true;
                    ae2qol$chat(
                        rowPreview(
                            stack,
                            inFields[index].getText(),
                            inAmounts[index].getText(),
                            outFields[index].getText(),
                            outAmounts[index].getText()));
                    return true;
                });
            ButtonWidget<?> excludeRow = new ButtonWidget<>().size(20, 12)
                .overlay(IKey.str("筛"))
                .tooltip(t -> t.addLine(IKey.str("把这一行的输入匹配串加入黑名单（保存后生效）")))
                .onMouseTapped(ctx -> {
                    String token = stripModePrefix(inFields[index].getText());
                    if (!token.isEmpty() && blacklist.add(token)) {
                        MyMod.LOG.info("[AE2QoL] 第 {} 行已加入黑名单：{}（保存后生效）", index + 1, token);
                    }
                    return true;
                });
            ButtonWidget<?> double2 = new ButtonWidget<>().size(22, 12)
                .overlay(IKey.str("x2"))
                .tooltip(t -> t.addLine(IKey.str("这一行的输入/输出数量翻倍")))
                .onMouseTapped(ctx -> {
                    inAmounts[index].setText(String.valueOf(parseLong(inAmounts[index].getText(), 1L) * 2L));
                    long outNow = parseLong(outAmounts[index].getText(), parseLong(inAmounts[index].getText(), 1L));
                    outAmounts[index].setText(String.valueOf(outNow * 2L));
                    return true;
                });
            ButtonWidget<?> clear = new ButtonWidget<>().size(22, 12)
                .overlay(IKey.str("清"))
                .tooltip(t -> t.addLine(IKey.str("清空这一行（点保存才写回）")))
                .onMouseTapped(ctx -> {
                    inFields[index].setText("");
                    inAmounts[index].setText("1");
                    outFields[index].setText("");
                    outAmounts[index].setText("");
                    return true;
                });

            Flow row = Flow.row()
                .childPadding(2)
                .size(320, ROW_H);
            row.child(new TextWidget<>(IKey.str(String.valueOf(i + 1))).size(10, 12))
                .child(inField)
                .child(inAmount)
                .child(new TextWidget<>(IKey.str("→")).size(8, 12))
                .child(outField)
                .child(outAmount)
                .child(previewRow)
                .child(excludeRow)
                .child(double2)
                .child(clear);
            content.child(row);
        }

        // ===== 内置电路 =====
        TextFieldWidget circuitField = new TextFieldWidget().setMaxLength(3)
            .size(30, 12);
        circuitField.setText(base.circuit >= 1 ? String.valueOf(base.circuit) : "");
        Flow circuitRow = Flow.row()
            .childPadding(2)
            .size(320, 14);
        circuitRow.child(new TextWidget<>(IKey.str("内置电路 1~24（留空 = 继承槽位/整机）")).size(180, 12))
            .child(circuitField);
        content.child(circuitRow);

        // ===== 总排除（黑名单）=====
        TextFieldWidget blacklistField = new TextFieldWidget().setMaxLength(64)
            .size(150, 12);
        Flow blackRow = Flow.row()
            .childPadding(2)
            .size(320, 14);
        blackRow.child(new TextWidget<>(IKey.str("总排除")).size(50, 12))
            .child(blacklistField)
            .child(new ButtonWidget<>().size(28, 12)
                .overlay(IKey.str("加"))
                .tooltip(t -> t.addLine(IKey.str("加入黑名单（支持 * 与 ?）")))
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
            .child(new ButtonWidget<>().size(28, 12)
                .overlay(IKey.str("清空"))
                .tooltip(t -> t.addLine(IKey.str("清空整个黑名单")))
                .onMouseTapped(ctx -> {
                    blacklist.clear();
                    return true;
                }))
            .child(new TextWidget<>(IKey.str("现有 " + blacklist.size() + " 项")).size(60, 12));
        content.child(blackRow);

        // 黑名单条目（逐行删除；按内容删，不按下标）
        int shownBlack = 0;
        for (String token : new ArrayList<>(blacklist)) {
            if (shownBlack >= PREVIEW_ROWS) break;
            final String entry = token;
            Flow entryLine = Flow.row()
                .childPadding(2)
                .size(320, 12);
            entryLine.child(new TextWidget<>(IKey.str("- " + entry)).size(260, 12))
                .child(new ButtonWidget<>().size(28, 12)
                    .overlay(IKey.str("删"))
                    .tooltip(t -> t.addLine(IKey.str("从黑名单移除（保存后生效）")))
                    .onMouseTapped(ctx -> {
                        if (blacklist.remove(entry)) {
                            MyMod.LOG.info("[AE2QoL] 黑名单移除：{}（保存后生效）", entry);
                        }
                        return true;
                    }));
            content.child(entryLine);
            shownBlack++;
        }

        // ===== 覆盖预览 =====
        content.child(new TextWidget<>(IKey.str("覆盖预览（点「排除」把该候选加入黑名单，保存后生效）")).size(320, 12));
        for (String name : previewNames) {
            final String token = name;
            Flow line = Flow.row()
                .childPadding(2)
                .size(320, 12);
            line.child(new TextWidget<>(IKey.str("- " + token)).size(260, 12))
                .child(new ButtonWidget<>().size(28, 12)
                    .overlay(IKey.str("排除"))
                    .tooltip(t -> t.addLine(IKey.str("把「" + token + "」加入黑名单")))
                    .onMouseTapped(ctx -> {
                        if (blacklist.add(token)) {
                            MyMod.LOG.info("[AE2QoL] 预览排除：已加入黑名单 {}（保存后生效）", token);
                        }
                        return true;
                    }));
            content.child(line);
        }
        if (preview != null) {
            if (preview.patterns.isEmpty()) {
                content.child(new TextWidget<>(IKey.str("当前没有覆盖任何候选：" + preview.describe())).size(320, 12));
            } else if (preview.patterns.size() > previewNameCount) {
                content.child(
                    new TextWidget<>(IKey.str("… 另有 " + (preview.patterns.size() - previewNameCount) + " 项（点「全部预览」打到聊天栏）"))
                        .size(320, 12));
            }
        }

        // ===== 底部按钮：全部预览 / 保存 =====
        Flow bottomRow = Flow.row()
            .childPadding(2)
            .size(320, 16);
        bottomRow.child(new ButtonWidget<>().size(70, 14)
            .overlay(IKey.str("全部预览"))
            .tooltip(t -> t.addLine(IKey.str("把完整候选列表与计数打到聊天栏")))
            .onMouseTapped(ctx -> {
                if (!data.isClient()) return true;
                ae2qol$chat(fullPreview(stack, base));
                return true;
            }))
            .child(new ButtonWidget<>().size(70, 14)
                .overlay(IKey.str("保存"))
                .tooltip(t -> t.addLine(IKey.str("把上面 9 行与黑名单/电路写进这张样板（服务端写入）")))
                .onMouseTapped(ctx -> {
                    if (!data.isClient()) return true;
                    saveRules(stack, base, blacklist, circuitField, inFields, inAmounts, outFields, outAmounts);
                    return true;
                }));
        content.child(bottomRow);
        content.child(new TextWidget<>(IKey.str("结果也会写进日志（生成/推导/写回都可追溯）")).size(320, 12));

        // ===== 放进可滚动容器：内容超高也不会被裁掉 =====
        ScrollWidget<?> scroll = new ScrollWidget<>(new VerticalScrollData());
        scroll.size(336, 238);
        scroll.getScrollArea()
            .getScrollY()
            .setScrollSize(contentH);
        scroll.child(content);

        panel.child(scroll);
        return panel;
    }

    /** 保存：客户端解析整张表 → 既有 C2S 包 → 服务端写 NBT。 */
    private static void saveRules(ItemStack stack, SmartWildcardState base, List<String> blacklist,
        TextFieldWidget circuitField, TextFieldWidget[] inFields, TextFieldWidget[] inAmounts,
        TextFieldWidget[] outFields, TextFieldWidget[] outAmounts) {
        try {
            SmartWildcardState edited = new SmartWildcardState();
            String circuitText = circuitField.getText() == null ? ""
                : circuitField.getText()
                    .trim();
            if (circuitText.isEmpty()) {
                edited.circuit = base.circuit >= 1 ? base.circuit : -1;
            } else {
                long parsed = parseLong(circuitText, -1L);
                if (parsed >= 1 && parsed <= 24) {
                    edited.circuit = (int) parsed;
                } else {
                    edited.circuit = base.circuit;
                    MyMod.LOG.warn("[AE2QoL] 编辑器里的电路号非法（应为 1~24）：{}，保持原值 {}", circuitText, base.circuit);
                }
            }
            edited.blacklist.addAll(blacklist);
            edited.whitelist.addAll(base.whitelist);
            edited.nonConsumed.addAll(base.nonConsumed);
            int used = 0;
            for (int i = 0; i < ROWS; i++) {
                String raw = inFields[i].getText();
                if (raw == null || raw.trim()
                    .isEmpty()) continue;
                boolean oreMode = !raw.trim()
                    .startsWith("name:");
                String rawOut = outFields[i].getText();
                edited.rules.add(
                    new SmartWildcardState.Rule(
                        used,
                        oreMode,
                        stripModePrefix(raw),
                        Math.max(1L, parseLong(inAmounts[i].getText(), 1L)),
                        rawOut == null ? "" : stripModePrefix(rawOut),
                        rawOut == null || !rawOut.trim()
                            .startsWith("name:"),
                        Math.max(0L, parseLong(outAmounts[i].getText(), 0L))));
                used++;
            }
            ModNetwork.CHANNEL.sendToServer(new SmartWildcardRulesPacket(edited));
            MyMod.LOG.info("[AE2QoL] 通配样板编辑器已提交规则：rules={}（手写）", edited.rules.size());
            ae2qol$chat("已提交保存：rules=" + edited.rules.size() + "，等待服务端写回（见日志）");
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 通配样板编辑器保存失败", t);
            ae2qol$chat("保存失败：" + t);
        }
    }

    /** 全部预览：把完整候选列表（名前 20 个）+ 计数打到聊天栏。 */
    private static String fullPreview(ItemStack stack, SmartWildcardState base) {
        try {
            if (stack == null) return "没有可用的样板物品";
            ItemStack temp = stack.copy();
            base.write(temp);
            SmartWildcardExpander.Result result = SmartWildcardExpander
                .expand(temp, net.minecraft.client.Minecraft.getMinecraft().theWorld);
            StringBuilder sb = new StringBuilder("[AE2QoL] ").append(result.describe());
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

    /** 只按某一行试算（不写回），返回一行聊天文本。 */
    private static String rowPreview(ItemStack baseStack, String inText, String inAmount, String outText,
        String outAmount) {
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
                    !raw.startsWith("name:"),
                    stripModePrefix(raw),
                    Math.max(1L, parseLong(inAmount, 1L)),
                    outText == null ? "" : stripModePrefix(outText),
                    outText == null || !outText.trim()
                        .startsWith("name:"),
                    Math.max(0L, parseLong(outAmount, 0L))));
            state.write(temp);
            SmartWildcardExpander.Result result = SmartWildcardExpander
                .expand(temp, net.minecraft.client.Minecraft.getMinecraft().theWorld);
            StringBuilder sb = new StringBuilder("[AE2QoL] 试算 ").append(result.describe());
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

    /** 聊天栏回执（仅客户端；调用点都在 data.isClient() 守卫内）。 */
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

    /** 取一张"展开出来的具体样板"的第一个输入物品的显示名（用于预览行与排除词）。 */
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

    /** 把规则里的匹配串还原成字段文本（带模式前缀，自解释）。 */
    private static String formatMatcher(boolean oreDictMode, String matcher) {
        if (matcher == null || matcher.isEmpty()) return "";
        return (oreDictMode ? "ore:" : "name:") + matcher;
    }

    private static String stripModePrefix(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("ore:")) return s.substring(4)
            .trim();
        if (s.startsWith("name:")) return s.substring(5)
            .trim();
        return s;
    }

    private static long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(text == null ? "" : text.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
