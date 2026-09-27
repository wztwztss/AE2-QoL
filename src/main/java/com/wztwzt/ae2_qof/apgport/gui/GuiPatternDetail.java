/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.gui;

import com.wztwzt.ae2_qof.MyMod;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.apgport.network.NetworkHandler;
import com.wztwzt.ae2_qof.apgport.network.PacketStorageAction;
import com.wztwzt.ae2_qof.apgport.util.I18nUtil;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.Scrollable;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

public class GuiPatternDetail {

    private static final int GUI_W = 260;
    private static final int GUI_H = 220;

    public static ModularWindow createWindow(UIBuildContext buildContext, int patternIndex, List<String> inputNames,
        List<String> outputNames) {
        ModularWindow.Builder builder = ModularWindow.builder(GUI_W, GUI_H);
        builder.setBackground(com.gtnewhorizons.modularui.api.ModularUITextures.VANILLA_BACKGROUND);

        TextWidget titleText = new TextWidget(
            EnumChatFormatting.BOLD + I18nUtil.tr("ae2patterngen.gui.pattern_detail.title", patternIndex + 1));
        titleText.setScale(1.2f);
        titleText.setSize(GUI_W - 16, 20);
        titleText.setPos(8, 8);
        builder.widget(titleText);

        Scrollable scrollable = new Scrollable().setVerticalScroll();
        scrollable.setPos(8, 24);
        scrollable.setSize(GUI_W - 16, GUI_H - 24 - 32);

        int y = 0;
        TextWidget inTitle = new TextWidget(
            EnumChatFormatting.BOLD + I18nUtil.tr("ae2patterngen.gui.pattern_detail.input.title", inputNames.size()));
        inTitle.setPos(4, y);
        scrollable.widget(inTitle);
        y += 12;

        if (inputNames.isEmpty()) {
            TextWidget emptyIn = new TextWidget(EnumChatFormatting.GRAY + I18nUtil.tr("ae2patterngen.gui.common.none"));
            emptyIn.setPos(8, y);
            scrollable.widget(emptyIn);
            y += 12;
        } else {
            for (String name : inputNames) {
                TextWidget row = new TextWidget(EnumChatFormatting.GRAY + "• " + EnumChatFormatting.WHITE + name);
                row.setPos(8, y);
                scrollable.widget(row);
                y += 12;
            }
        }
        y += 8;

        TextWidget outTitle = new TextWidget(
            EnumChatFormatting.BOLD + I18nUtil.tr("ae2patterngen.gui.pattern_detail.output.title", outputNames.size()));
        outTitle.setPos(4, y);
        scrollable.widget(outTitle);
        y += 12;

        if (outputNames.isEmpty()) {
            TextWidget emptyOut = new TextWidget(
                EnumChatFormatting.GRAY + I18nUtil.tr("ae2patterngen.gui.common.none"));
            emptyOut.setPos(8, y);
            scrollable.widget(emptyOut);
            y += 12;
        } else {
            for (String name : outputNames) {
                TextWidget row = new TextWidget(EnumChatFormatting.GREEN + "▶ " + EnumChatFormatting.WHITE + name);
                row.setPos(8, y);
                scrollable.widget(row);
                y += 12;
            }
        }
        builder.widget(scrollable);

        int btnW = 90;
        int btnH = 20;
        int btnY = GUI_H - 28;

        ButtonWidget btnDelete = new ButtonWidget();
        btnDelete.setPos(GUI_W / 2 - btnW - 4, btnY);
        btnDelete.setSize(btnW, btnH);
        btnDelete.setBackground(com.gtnewhorizons.modularui.api.ModularUITextures.VANILLA_BUTTON_NORMAL);
        TextWidget btnDelText = new TextWidget(I18nUtil.tr("ae2patterngen.gui.pattern_detail.button.delete"));
        btnDelText.setPos(GUI_W / 2 - btnW - 4 + 16, btnY + 6);
        btnDelete.setOnClick((cd, w) -> {
            NetworkHandler.INSTANCE
                .sendToServer(new PacketStorageAction(PacketStorageAction.ACTION_DELETE, patternIndex));
            com.wztwzt.ae2_qof.apgport.ApgStubs.openPatternStorageScreen(Minecraft.getMinecraft().thePlayer);
        });
        builder.widget(btnDelete);
        builder.widget(btnDelText);

        ButtonWidget btnBack = new ButtonWidget();
        btnBack.setPos(GUI_W / 2 + 4, btnY);
        btnBack.setSize(btnW, btnH);
        btnBack.setBackground(com.gtnewhorizons.modularui.api.ModularUITextures.VANILLA_BUTTON_NORMAL);
        TextWidget btnBackText = new TextWidget(I18nUtil.tr("ae2patterngen.gui.common.back"));
        btnBackText.setPos(GUI_W / 2 + 4 + 32, btnY + 6);
        btnBack.setOnClick(
            (cd, w) -> { com.wztwzt.ae2_qof.apgport.ApgStubs.openPatternStorageScreen(Minecraft.getMinecraft().thePlayer); });
        builder.widget(btnBack);
        builder.widget(btnBackText);

        return builder.build();
    }
}
