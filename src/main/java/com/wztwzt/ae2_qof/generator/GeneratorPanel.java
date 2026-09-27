package com.wztwzt.ae2_qof.generator;

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
import com.wztwzt.ae2_qof.network.SmartPatternGenPacket;

/**
 * 「批量样板生成器」的 MUI2 界面（3.23.0）。
 *
 * <p>功能面对照参考模组 AE2PatternGen 的生成器界面（配方设置 / 过滤器 / 生成），
 * 但实现为本仓库自己的 MUI2 面板、复用我们的写回与日志口径。
 *
 * <p>过滤器全部"留空即不启用"，匹配串支持 {@code *} 与 {@code ?}，可同时匹配显示名与矿辞名。
 * 点「生成」后由服务端扫描 RecipeMap 并产出样板（结果计数进聊天栏与日志），
 * 因此界面本身不需要显示进度。
 */
public final class GeneratorPanel {

    private GeneratorPanel() {}

    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager syncManager) {
        ModularPanel panel = new ModularPanel("ae2qol_pattern_generator").size(300, 190);

        // 3.20.0-fix44：关键字框支持"输入机器名片段 → Tab 循环候选"，并配 [对照表] 与提示行
        final String[] mapHint = { "输机器名片段（中文/英文/关键字皆可）后按 Tab 循环候选；或点右侧 [对照表] 查表" };
        TextFieldWidget mapField = new RecipeMapKeywordField(hint -> mapHint[0] = hint).size(150, 12);
        TextFieldWidget inBlack = new TextFieldWidget().setMaxLength(64)
            .size(180, 12);
        TextFieldWidget outBlack = new TextFieldWidget().setMaxLength(64)
            .size(180, 12);
        TextFieldWidget inOre = new TextFieldWidget().setMaxLength(64)
            .size(180, 12);
        TextFieldWidget outOre = new TextFieldWidget().setMaxLength(64)
            .size(180, 12);
        TextFieldWidget ncItem = new TextFieldWidget().setMaxLength(64)
            .size(180, 12);
        TextFieldWidget capField = new TextFieldWidget().setMaxLength(6)
            .size(50, 12);
        capField.setText("512");
        // 电压等级上限：0=ULV、1=LV、2=MV、3=HV、4=EV…；留空 = 不限
        TextFieldWidget tierField = new TextFieldWidget().setMaxLength(2)
            .size(30, 12);
        tierField.setText("");
        // 替换规则：源矿辞=目标矿辞，多条用 ; 分隔（在编码样板时替换输入/输出，数量不变）
        TextFieldWidget replField = new TextFieldWidget().setMaxLength(200)
            .size(180, 12);

        Flow column = Flow.column()
            .childPadding(3)
            .size(292, 170);
        column.child(new TextWidget<>(IKey.str("批量样板生成器（按 GT RecipeMap 生成具体样板）")).size(290, 10));
        column.child(new TextWidget<>(IKey.str("配方设置：输机器名片段（中文/英文/关键字）后按 Tab 循环候选")).size(290, 10));
        column.child(row("配 方  Map", mapField));
        // [对照表] 按钮 + 动态提示行（Tab 循环时显示"当前候选 x/y：中文 · 英文 · 关键字"）
        Flow mapExtra = Flow.row()
            .childPadding(3)
            .size(288, 16);
        mapExtra.child(new ButtonWidget<>().size(60, 14)
            .overlay(IKey.str("对照表"))
            .tooltip(t -> {
                t.addLine(IKey.str("列出全部 RecipeMap：中文名 | 英文名 | 关键字"));
                t.addLine(IKey.str("点整行 = 填入关键字并返回"));
            })
            .onMouseTapped(ctx -> {
                if (!data.isClient()) return true;
                try {
                    net.minecraft.client.Minecraft.getMinecraft()
                        .displayGuiScreen(
                            new com.wztwzt.ae2_qof.client.gui.GuiRecipeMapTable(
                                ((RecipeMapKeywordField) mapField)::pickFromTable));
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 打开 RecipeMap 对照表失败", t);
                }
                return true;
            }));
        mapExtra.child(IKey.dynamic(() -> mapHint[0]).asWidget());
        column.child(mapExtra);
        column.child(new TextWidget<>(IKey.str("过滤器（留空 = 不启用；支持 * 与 ?；可匹配显示名或矿辞）")).size(290, 10));
        column.child(row("输入排除", inBlack));
        column.child(row("输出排除", outBlack));
        column.child(row("输入矿辞", inOre));
        column.child(row("输出矿辞", outOre));
        column.child(row("NC 物品", ncItem));
        column.child(row("替换规则", replField));
        column.child(
            new TextWidget<>(IKey.str("替换规则写法：源矿辞=目标矿辞，多条用 ; 分隔，如 dustCopper=dustTin")).size(290, 10));
        Flow capRow = Flow.row()
            .childPadding(3)
            .size(288, 20);
        capRow.child(new TextWidget<>(IKey.str("数量上限")).size(56, 12))
            .child(capField)
            .child(new TextWidget<>(IKey.str("电压等级")).size(56, 12))
            .child(tierField)
            .child(new ButtonWidget<>().size(80, 14)
                .overlay(IKey.str("生成样板"))
                .tooltip(t -> {
                    t.addLine(IKey.str("服务端扫描该 RecipeMap 并按过滤器生成样板"));
                    t.addLine(IKey.str("产物进背包；放不下会掉在脚下"));
                })
                .onMouseTapped(ctx -> {
                    if (!data.isClient()) return true;
                    try {
                        int cap = 512;
                        try {
                            cap = Integer.parseInt(
                                capField.getText()
                                    .trim());
                        } catch (Throwable ignored) {
                            // 非法上限按默认值处理，并在日志里留痕（不静默）
                            MyMod.LOG.warn(
                                "[AE2QoL] 生成器数量上限非法，按默认 512 处理：{}",
                                capField.getText());
                        }
                        ModNetwork.CHANNEL.sendToServer(
                            new SmartPatternGenPacket(
                                mapField.getText(),
                                inBlack.getText(),
                                outBlack.getText(),
                                inOre.getText(),
                                outOre.getText(),
                                ncItem.getText(),
                                cap,
                                parseTier(tierField.getText()),
                                replField.getText(),
                                false));
                        MyMod.LOG.info(
                            "[AE2QoL] 已发送样板生成请求：map={} cap={} inBlack={} outBlack={} inOre={} outOre={} nc={}",
                            mapField.getText(),
                            cap,
                            inBlack.getText(),
                            outBlack.getText(),
                            inOre.getText(),
                            outOre.getText(),
                            ncItem.getText());
                    } catch (Throwable t) {
                        MyMod.LOG.warn("[AE2QoL] 发送样板生成请求失败", t);
                    }
                    return true;
                }));
        column.child(capRow);
        // 「预览数量」：同一套参数只统计不产出（对应参考模组的预览数量）
        column.child(new ButtonWidget<>().size(90, 14)
            .overlay(IKey.str("预览数量"))
            .tooltip(t -> t.addLine(IKey.str("按当前参数只统计：会产出多少、跳过多少（不产生任何物品）")))
            .onMouseTapped(ctx -> {
                if (!data.isClient()) return true;
                try {
                    int cap = parseCap(capField.getText());
                    ModNetwork.CHANNEL.sendToServer(
                        new SmartPatternGenPacket(
                            mapField.getText(),
                            inBlack.getText(),
                            outBlack.getText(),
                            inOre.getText(),
                            outOre.getText(),
                            ncItem.getText(),
                            cap,
                            parseTier(tierField.getText()),
                            replField.getText(),
                            true));
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 发送预览数量请求失败", t);
                }
                return true;
            }));
        column.child(
            new TextWidget<>(IKey.str("结果会打在聊天栏与日志：seen/produced/skippedFluid/filtered/truncated")).size(290, 10));

        panel.child(column);
        return panel;
    }

    /** 解析数量上限：空/非法/<=0 按默认 512，并记日志（与生成按钮同口径，不静默）。 */
    private static int parseCap(String text) {
        try {
            String s = text == null ? "" : text.trim();
            if (s.isEmpty()) return 512;
            int v = Integer.parseInt(s);
            if (v <= 0) {
                MyMod.LOG.warn("[AE2QoL] 数量上限应 > 0，输入 {} 已按默认 512 处理", s);
                return 512;
            }
            return v;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 数量上限非法，已按默认 512 处理：{}", text);
            return 512;
        }
    }

    /** 解析电压等级输入：空串 = 不限（-1）；非法或越界同样按不限处理并记日志（不静默）。 */
    private static int parseTier(String text) {
        try {
            String s = text == null ? "" : text.trim();
            if (s.isEmpty()) return -1;
            int v = Integer.parseInt(s);
            if (v < 0 || v > 14) {
                MyMod.LOG.warn("[AE2QoL] 电压等级应在 0~14（0=ULV，1=LV…），输入 {} 已按不限处理", s);
                return -1;
            }
            return v;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 电压等级输入非法，已按不限处理：{}", text);
            return -1;
        }
    }

    private static Flow row(String label, TextFieldWidget field) {
        Flow flow = Flow.row()
            .childPadding(3)
            .size(288, 18);
        flow.child(new TextWidget<>(IKey.str(label)).size(60, 14))
            .child(field.size(180, 14));
        return flow;
    }
}
