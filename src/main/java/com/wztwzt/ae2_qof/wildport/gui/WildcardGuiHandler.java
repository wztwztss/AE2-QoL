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
        if (id == WildportIds.GUI_WILDCARD_PATTERN) {
            com.gtnewhorizons.modularui.api.screen.UIBuildContext buildContext =
                new com.gtnewhorizons.modularui.api.screen.UIBuildContext(player);
            com.gtnewhorizons.modularui.api.screen.ModularUIContext context =
                new com.gtnewhorizons.modularui.api.screen.ModularUIContext(buildContext, () -> {});
            com.gtnewhorizons.modularui.api.screen.ModularWindow window =
                WildcardPatternWindow.createWindow(buildContext, player, x);
            return new com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer(context, window);
        }
        if (id == WildportIds.GUI_COMPOSITE_WILDCARD_PATTERN) {
            com.gtnewhorizons.modularui.api.screen.UIBuildContext buildContext =
                new com.gtnewhorizons.modularui.api.screen.UIBuildContext(player);
            com.gtnewhorizons.modularui.api.screen.ModularUIContext context =
                new com.gtnewhorizons.modularui.api.screen.ModularUIContext(buildContext, () -> {});
            com.gtnewhorizons.modularui.api.screen.ModularWindow window =
                CompositeWildcardPatternWindow.createWindow(buildContext, player, x);
            return new com.gtnewhorizons.modularui.common.internal.wrapper.ModularUIContainer(context, window);
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
