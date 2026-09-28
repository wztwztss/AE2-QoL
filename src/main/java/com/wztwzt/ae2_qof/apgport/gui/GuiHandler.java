/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.apgport.item.ItemPatternGenerator;

import cpw.mods.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        // 3.23.2-fix1：服务端**不再构建窗口**。
        // 为什么：GuiPatternGen / GuiPatternStorage 里含**客户端专用**代码（「改」按钮的原版对话框
        // Minecraft.getMinecraft().displayGuiScreen(new GuiTextInputDialog(...))），专用服务器上加载它们会被
        // Forge 的 SideTransformer 拒绝（NoClassDefFoundError: net/minecraft/client/gui/GuiScreen /
        // Attempted to load class bdw for invalid side SERVER），异常发生在 FML 取服务端容器那一步
        // ⇒ 开窗包根本不发 ⇒ 客户端「右键完全没反应」。服务端只需一个无槽位容器（这两个界面零槽位、
        // 也不使用 MUI1 同步）；窗口本体只在 getClientGuiElement（客户端）里构建。
        // 详见 com.wztwzt.ae2_qof.merged.ServerSafeModularContainer。
        if (id == ItemPatternGenerator.GUI_ID || id == ItemPatternGenerator.GUI_ID_STORAGE) {
            // 每次右键都会走到这里，故只留 DEBUG（原先的 INFO 是无效刷屏）
            cpw.mods.fml.common.FMLLog.fine("[AE2PatternGen] getServerGuiElement ID=" + id + " Side=SERVER");
            return com.wztwzt.ae2_qof.merged.ServerSafeModularContainer.slotless(player);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        cpw.mods.fml.common.FMLLog.fine("[AE2PatternGen] getClientGuiElement ID=" + id + " Side=CLIENT");
        try {
            if (id == ItemPatternGenerator.GUI_ID) {
                com.gtnewhorizons.modularui.api.screen.UIBuildContext buildContext = new com.gtnewhorizons.modularui.api.screen.UIBuildContext(
                    player);
                com.gtnewhorizons.modularui.api.screen.ModularUIContext muiContext = new com.gtnewhorizons.modularui.api.screen.ModularUIContext(
                    buildContext,
                    () -> {});
                com.gtnewhorizons.modularui.api.screen.ModularWindow window = GuiPatternGen
                    .createWindow(buildContext, player.getCurrentEquippedItem());
                return new com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui(
                    new com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer(muiContext, window));
            }
            if (id == ItemPatternGenerator.GUI_ID_STORAGE) {
                com.gtnewhorizons.modularui.api.screen.UIBuildContext buildContext = new com.gtnewhorizons.modularui.api.screen.UIBuildContext(
                    player);
                com.gtnewhorizons.modularui.api.screen.ModularUIContext muiContext = new com.gtnewhorizons.modularui.api.screen.ModularUIContext(
                    buildContext,
                    () -> {});
                com.gtnewhorizons.modularui.api.screen.ModularWindow window = GuiPatternStorage
                    .createWindow(buildContext, player);
                return new com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui(
                    new com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer(muiContext, window));
            }
        } catch (Throwable t) {
            cpw.mods.fml.common.FMLLog.severe("[AE2PatternGen] Error creating client GUI element: " + t);
            t.printStackTrace();
        }
        return null;
    }
}
