/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样。
 * 注意：本类的物品注册入口（register 之类）我们**不会调用** —— 我们的物品仍是 ItemSmartWildcardPattern，
 * 这里只保留窗口所需的静态物品引用与工具方法，避免重复注册物品。
 */
package com.wztwzt.ae2_qof.wildport;

import com.wztwzt.ae2_qof.wildport.item.ItemCompositeWildcardPattern;
import com.wztwzt.ae2_qof.wildport.item.ItemWildcardPattern;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public final class ModItems {

    public static Item wildcardPattern;
    public static Item compositeWildcardPattern;

    private ModItems() {}

    public static void init() {
        wildcardPattern = new ItemWildcardPattern()
            .setUnlocalizedName("wildcard_pattern")
            .setTextureName("wildcardpattern:wildcard_pattern")
            .setCreativeTab(CreativeTabs.tabMisc);
        compositeWildcardPattern = new ItemCompositeWildcardPattern()
            .setUnlocalizedName("composite_wildcard_pattern")
            .setTextureName("wildcardpattern:wildcard_pattern")
            .setCreativeTab(CreativeTabs.tabMisc);

        GameRegistry.registerItem(wildcardPattern, "wildcard_pattern");
        GameRegistry.registerItem(compositeWildcardPattern, "composite_wildcard_pattern");
    }
}
