/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.gui;

import com.wztwzt.ae2_qof.wildport.WildportIds;

import com.wztwzt.ae2_qof.MyMod;


import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class WildcardGuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        // 3.23.2-fix1：服务端**不再构建窗口**。
        // 为什么：窗口类（WildcardPatternWindow / CompositeWildcardPatternWindow）里含**客户端专用**代码
        // （fix43 起「改」按钮用的是原版输入框对话框：Minecraft.getMinecraft().displayGuiScreen(new GuiTextInputDialog(...))），
        // 专用服务器上加载这些类会被 Forge 的 SideTransformer 拒绝：
        //   java.lang.NoClassDefFoundError: net/minecraft/client/gui/GuiScreen
        //   Caused by: Attempted to load class bdw for invalid side SERVER
        // 异常发生在 FML 的 NetworkRegistry.getRemoteGuiContainer（取服务端容器那一步）⇒ 容器取不到
        // ⇒ 开窗包 S2DOpenWindow 根本不发 ⇒ 客户端「右键完全没反应」（单机是 CLIENT 侧，所以一直正常）。
        // 这两个界面没有任何槽位、也不用 MUI1 同步，故服务端只给一个空的 MUI1 容器；
        // 窗口本体仍旧只在 getClientGuiElement（客户端）里构建。详见 merged/ServerSafeModularContainer。
        if (id == WildportIds.GUI_WILDCARD_PATTERN || id == WildportIds.GUI_COMPOSITE_WILDCARD_PATTERN) {
            return com.wztwzt.ae2_qof.merged.ServerSafeModularContainer.slotless(player);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == WildportIds.GUI_WILDCARD_PATTERN) {
            com.gtnewhorizons.modularui.api.screen.UIBuildContext buildContext =
                new com.gtnewhorizons.modularui.api.screen.UIBuildContext(player);
            com.gtnewhorizons.modularui.api.screen.ModularUIContext context =
                new com.gtnewhorizons.modularui.api.screen.ModularUIContext(buildContext, () -> {});
            com.gtnewhorizons.modularui.api.screen.ModularWindow window =
                WildcardPatternWindow.createWindow(buildContext, player, x);
            return new com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui(
                new com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer(context, window));
        }
        if (id == WildportIds.GUI_COMPOSITE_WILDCARD_PATTERN) {
            com.gtnewhorizons.modularui.api.screen.UIBuildContext buildContext =
                new com.gtnewhorizons.modularui.api.screen.UIBuildContext(player);
            com.gtnewhorizons.modularui.api.screen.ModularUIContext context =
                new com.gtnewhorizons.modularui.api.screen.ModularUIContext(buildContext, () -> {});
            com.gtnewhorizons.modularui.api.screen.ModularWindow window =
                CompositeWildcardPatternWindow.createWindow(buildContext, player, x);
            return new com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui(
                new com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer(context, window));
        }
        return null;
    }
}
