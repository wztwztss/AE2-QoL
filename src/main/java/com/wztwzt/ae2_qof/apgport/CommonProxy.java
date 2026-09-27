/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.proxy;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import com.wztwzt.ae2_qof.apgport.config.ForgeConfig;
import com.wztwzt.ae2_qof.apgport.item.ModItems;
import com.wztwzt.ae2_qof.apgport.network.NetworkHandler;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ForgeConfig.load(event.getSuggestedConfigurationFile());
        ModItems.init();
        NetworkHandler.init();
    }

    public void init(FMLInitializationEvent event, Object modInstance) {
        cpw.mods.fml.common.network.NetworkRegistry.INSTANCE
            .registerGuiHandler(modInstance, new com.wztwzt.ae2_qof.apgport.gui.GuiHandler());

        // 注册 AE2 无线处理器 (用于安全终端绑定)
        try {
            appeng.api.AEApi.instance()
                .registries()
                .wireless()
                .registerWirelessHandler((appeng.api.features.IWirelessTermHandler) ModItems.itemPatternGenerator);
        } catch (Throwable e) {
            cpw.mods.fml.common.FMLLog.warning("[AE2PatternGen] 无法注册 AE2 无线处理器: %s", e.getMessage());
        }

        cpw.mods.fml.common.registry.GameRegistry.addShapedRecipe(
            new net.minecraft.item.ItemStack(ModItems.itemPatternGenerator),
            "ABA",
            "BCB",
            "ABA",
            'A',
            new net.minecraft.item.ItemStack(
                cpw.mods.fml.common.registry.GameRegistry.findItem("gregtech", "gt.metaitem.01"),
                1,
                32653),
            'B',
            new net.minecraft.item.ItemStack(
                cpw.mods.fml.common.registry.GameRegistry.findItem("appliedenergistics2", "item.ItemMultiMaterial"),
                1,
                52),
            'C',
            new net.minecraft.item.ItemStack(
                cpw.mods.fml.common.registry.GameRegistry.findItem("appliedenergistics2", "item.ItemMultiPart"),
                1,
                340));
    }

    public void closeCurrentScreen() {
        // Dedicated server: no GUI screen exists.
    }

    public void openPatternDetailScreen(EntityPlayer player, int index, List<String> inputs, List<String> outputs) {
        // Dedicated server: no GUI screen exists.
    }

    public void openPatternStorageScreen(EntityPlayer player) {
        // Dedicated server: no GUI screen exists.
    }
}
