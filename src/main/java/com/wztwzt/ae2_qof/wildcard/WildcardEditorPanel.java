package com.wztwzt.ae2_qof.wildcard;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.SmartWildcardRulesPacket;

/**
 * 智能通配样板的 MUI2 配置界面（3.23.0）。
 *
 * <h2>这一版做了什么</h2>
 * <ul>
 * <li><b>9 行规则编辑表</b>（照参考模组的交互重写，不复制其代码）：每行
 * 「输入匹配 + 数量 → 输出匹配 + 数量」，可手写，也可由 NEI 加号一键填入；</li>
 * <li><b>模式的写法</b>：匹配串自带模式前缀 —— <code>ore:ingot*</code> 表示矿辞模式，
 * <code>name:*锭</code> 表示显示名模式（不写前缀按矿辞处理）。这样字段本身就是自解释的，
 * 不需要额外的模式开关，也不会出现"开关状态看不出来"的问题；</li>
 * <li><b>保存</b>：客户端解析整张表 → 组包 {@link SmartWildcardRulesPacket} 交服务端写入样板 NBT
 * （客户端不写 NBT；既有写回链路已带日志与校验）。</li>
 * </ul>
 *
 * <h2>面板在两侧都会被构建</h2>
 * MUI2 的 {@code buildUI} 服务端与客户端都会执行，因此本类**只用两侧都存在的 MUI2 类**；
 * 只有"点击后发包"这种纯客户端动作才用 {@code data.isClient()} 守卫。
 */
public final class WildcardEditorPanel {

    /** 规则行数（与参考模版一致）。 */
    public static final int ROWS = 9;

    private WildcardEditorPanel() {}

    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager syncManager) {
        ModularPanel panel = new ModularPanel("ae2qol_wildcard_editor").size(340, 224);

        ItemStack stack = data.getUsedItemStack();
        SmartWildcardState state = SmartWildcardState.of(stack);
        if (state == null) state = new SmartWildcardState();
        // lambda 只能捕获有效 final ⇒ 取一个不变引用给下面的"保存"回调使用
        final SmartWildcardState base = state;

        TextFieldWidget[] inFields = new TextFieldWidget[ROWS];
        TextFieldWidget[] inAmounts = new TextFieldWidget[ROWS];
        TextFieldWidget[] outFields = new TextFieldWidget[ROWS];
        TextFieldWidget[] outAmounts = new TextFieldWidget[ROWS];

        Flow column = Flow.column()
            .childPadding(2)
            .size(330, 200);
        column.child(
            new TextWidget<>(IKey.str("§b智能通配样板 · 规则（可手写；也可在 NEI 里按加号自动填入）")).size(320, 10));
        column.child(new TextWidget<>(IKey.str("§7匹配串写法：§fore:ingot* §7= 矿辞模式，§fname:*锭 §7= 显示名模式")).size(320, 10));

        Flow header = Flow.row()
            .childPadding(2);
        header.child(new TextWidget<>(IKey.str("§7#")).size(12, 10))
            .child(new TextWidget<>(IKey.str("§7输入匹配")).size(78, 10))
            .child(new TextWidget<>(IKey.str("§7数量")).size(24, 10))
            .child(new TextWidget<>(IKey.str("§7→")).size(10, 10))
            .child(new TextWidget<>(IKey.str("§7输出匹配")).size(78, 10))
            .child(new TextWidget<>(IKey.str("§7数量")).size(24, 10))
            .child(new TextWidget<>(IKey.str("§7操作")).size(40, 10));
        column.child(header);

        for (int i = 0; i < ROWS; i++) {
            SmartWildcardState.Rule rule = i < state.rules.size() ? state.rules.get(i) : null;

            TextFieldWidget inField = new TextFieldWidget().setMaxLength(64)
                .size(78, 12);
            inField.setText(rule == null ? "" : formatMatcher(rule.oreDictMode, rule.matcher));

            TextFieldWidget inAmount = new TextFieldWidget().setMaxLength(9)
                .size(24, 12);
            inAmount.setText(rule == null ? "1" : String.valueOf(Math.max(1L, rule.amount)));

            TextFieldWidget outField = new TextFieldWidget().setMaxLength(64)
                .size(78, 12);
            outField.setText(rule == null ? "" : formatMatcher(rule.outOreDictMode, rule.outMatcher));

            TextFieldWidget outAmount = new TextFieldWidget().setMaxLength(9)
                .size(24, 12);
            outAmount.setText(rule == null || rule.outAmount <= 0 ? "" : String.valueOf(rule.outAmount));

            inFields[i] = inField;
            inAmounts[i] = inAmount;
            outFields[i] = outField;
            outAmounts[i] = outAmount;

            final int index = i;
            ButtonWidget<?> clear = new ButtonWidget<>().size(16, 12)
                .overlay(IKey.str("§c清"))
                .tooltip(t -> t.addLine(IKey.str("清空这一行（不会立即写回，点保存才生效）")))
                .onMouseTapped(ctx -> {
                    inFields[index].setText("");
                    inAmounts[index].setText("1");
                    outFields[index].setText("");
                    outAmounts[index].setText("");
                    return true;
                });
            ButtonWidget<?> double2 = new ButtonWidget<>().size(18, 12)
                .overlay(IKey.str("§ex2"))
                .tooltip(t -> t.addLine(IKey.str("把这一行的输入/输出数量翻倍")))
                .onMouseTapped(ctx -> {
                    inAmounts[index].setText(String.valueOf(parseLong(inAmounts[index].getText(), 1L) * 2L));
                    long outNow = parseLong(outAmounts[index].getText(), parseLong(inAmounts[index].getText(), 1L));
                    outAmounts[index].setText(String.valueOf(outNow * 2L));
                    return true;
                });

            Flow row = Flow.row()
                .childPadding(2);
            row.child(new TextWidget<>(IKey.str("§7" + (i + 1))).size(12, 12))
                .child(inField)
                .child(inAmount)
                .child(new TextWidget<>(IKey.str("§7→")).size(10, 12))
                .child(outField)
                .child(outAmount)
                .child(clear)
                .child(double2);
            column.child(row);
        }

        // ===== 电路号（1~24；留空 = 继承槽位/整机）=====
        TextFieldWidget circuitField = new TextFieldWidget().setMaxLength(3)
            .size(30, 12);
        circuitField.setText(base.circuit >= 1 ? String.valueOf(base.circuit) : "");
        Flow circuitRow = Flow.row()
            .childPadding(2);
        circuitRow.child(new TextWidget<>(IKey.str("§7内置电路 1~24（留空 = 继承）")).size(160, 12))
            .child(circuitField);
        column.child(circuitRow);

        // ===== 黑名单（总排除）=====
        final java.util.List<String> blacklist = new java.util.ArrayList<>(base.blacklist);
        TextFieldWidget blacklistField = new TextFieldWidget().setMaxLength(64)
            .size(140, 12);
        Flow blackRow = Flow.row()
            .childPadding(2);
        blackRow.child(new TextWidget<>(IKey.str("§7总排除（黑名单）")).size(90, 12))
            .child(blacklistField)
            .child(new ButtonWidget<>().size(28, 12)
                .overlay(IKey.str("§a加"))
                .tooltip(t -> t.addLine(IKey.str("把左边文本框的内容加入黑名单（支持 * 与 ?）")))
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
                .overlay(IKey.str("§c清"))
                .tooltip(t -> t.addLine(IKey.str("清空整个黑名单")))
                .onMouseTapped(ctx -> {
                    blacklist.clear();
                    return true;
                }))
            .child(new TextWidget<>(IKey.str("§7现有 " + blacklist.size() + " 项")).size(70, 12));
        column.child(blackRow);

        // 保存：客户端解析整张表 → 走既有 C2S 包由服务端写入样板 NBT
        ButtonWidget<?> save = new ButtonWidget<>().size(70, 14)
            .overlay(IKey.str("§a保存规则"))
            .tooltip(t -> t.addLine(IKey.str("把上面 9 行写进这张样板的 NBT（服务端写入）")))
            .onMouseTapped(ctx -> {
                if (!data.isClient()) return true; // 只在客户端点击时发包
                try {
                    SmartWildcardState edited = new SmartWildcardState();
                    // 电路：优先用界面里填的（1~24）；填了非法值就保持原样并记日志，不静默吞掉
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
                        String matcher = stripModePrefix(raw);
                        long amount = Math.max(1L, parseLong(inAmounts[i].getText(), 1L));
                        String rawOut = outFields[i].getText();
                        String outMatcher = rawOut == null ? "" : stripModePrefix(rawOut);
                        boolean outOreMode = rawOut == null || !rawOut.trim()
                            .startsWith("name:");
                        long outAmount = Math.max(0L, parseLong(outAmounts[i].getText(), 0L));
                        edited.rules
                            .add(new SmartWildcardState.Rule(used, oreMode, matcher, amount, outMatcher, outOreMode, outAmount));
                        used++;
                    }
                    ModNetwork.CHANNEL.sendToServer(new SmartWildcardRulesPacket(edited));
                    MyMod.LOG.info("[AE2QoL] 通配样板编辑器已提交规则：rules={}（手写）", edited.rules.size());
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 通配样板编辑器保存失败", t);
                }
                return true;
            });
        column.child(save);
        column.child(
            new TextWidget<>(IKey.str("§7提示：输出留空 = 沿用模板输出自动配对（同材质）；填了则按你写的输出匹配走。"))
                .size(320, 10));

        panel.child(column);
        return panel;
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
