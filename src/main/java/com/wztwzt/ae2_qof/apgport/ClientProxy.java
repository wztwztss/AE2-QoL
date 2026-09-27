/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.proxy;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.wztwzt.ae2_qof.apgport.gui.GuiPatternDetail;
import com.wztwzt.ae2_qof.apgport.gui.GuiPatternStorage;
import com.gtnewhorizons.modularui.api.screen.ModularUIContext;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui;
import com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(cpw.mods.fml.common.event.FMLInitializationEvent event, Object modInstance) {
        super.init(event, modInstance);
    }

    @Override
    public void closeCurrentScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.closeScreen();
        }
    }

    @Override
    public void openPatternDetailScreen(EntityPlayer player, int index, List<String> inputs, List<String> outputs) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer uiPlayer = player != null ? player : mc.thePlayer;
        if (uiPlayer == null) {
            return;
        }

        if (mc.thePlayer != null) {
            mc.thePlayer.closeScreen();
        }

        UIBuildContext buildContext = new UIBuildContext(uiPlayer);
        ModularUIContext muiContext = new ModularUIContext(buildContext, () -> {});
        ModularWindow detailWindow = GuiPatternDetail.createWindow(buildContext, index, inputs, outputs);
        mc.displayGuiScreen(new ModularGui(new ModularUIContainer(muiContext, detailWindow)));
    }

    @Override
    public void openPatternStorageScreen(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer uiPlayer = player != null ? player : mc.thePlayer;
        if (uiPlayer == null) {
            return;
        }

        if (mc.thePlayer != null) {
            mc.thePlayer.closeScreen();
        }

        UIBuildContext buildContext = new UIBuildContext(uiPlayer);
        ModularUIContext muiContext = new ModularUIContext(buildContext, () -> {});
        ModularWindow storageWindow = GuiPatternStorage.createWindow(buildContext, uiPlayer);
        mc.displayGuiScreen(new ModularGui(new ModularUIContainer(muiContext, storageWindow)));
    }
}
