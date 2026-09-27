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

        TextFieldWidget mapField = new TextFieldWidget().setMaxLength(64)
            .size(180, 12);
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

        Flow column = Flow.column()
            .childPadding(3)
            .size(292, 170);
        column.child(new TextWidget<>(IKey.str("§b批量样板生成器（按 GT RecipeMap 生成具体样板）")).size(290, 10));
        column.child(new TextWidget<>(IKey.str("§7配方设置：RecipeMap id 或其片段，如 rolling / gt.recipe.rolling")).size(290, 10));
        column.child(row("§7配 方  Map", mapField));
        column.child(new TextWidget<>(IKey.str("§7过滤器（留空 = 不启用；支持 * 与 ?；可匹配显示名或矿辞）")).size(290, 10));
        column.child(row("§7输入排除", inBlack));
        column.child(row("§7输出排除", outBlack));
        column.child(row("§7输入矿辞", inOre));
        column.child(row("§7输出矿辞", outOre));
        column.child(row("§7NC 物品", ncItem));
        Flow capRow = Flow.row()
            .childPadding(3);
        capRow.child(new TextWidget<>(IKey.str("§7数量上限")).size(60, 12))
            .child(capField)
            .child(new ButtonWidget<>().size(80, 14)
                .overlay(IKey.str("§a生成样板"))
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
                                cap));
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
        column.child(
            new TextWidget<>(IKey.str("§8结果会打在聊天栏与日志：seen/produced/skippedFluid/filtered/truncated")).size(290, 10));

        panel.child(column);
        return panel;
    }

    private static Flow row(String label, TextFieldWidget field) {
        Flow flow = Flow.row()
            .childPadding(3);
        flow.child(new TextWidget<>(IKey.str(label)).size(60, 12))
            .child(field);
        return flow;
    }
}
