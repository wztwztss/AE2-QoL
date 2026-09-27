/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Defensive ItemStack helpers for recipe data that may contain wildcard metadata.
 */
public final class ItemStackUtil {

    private ItemStackUtil() {}

    public static String getSafeDisplayName(ItemStack stack) {
        if (stack == null) return "";

        Item item = stack.getItem();
        if (item == null) return "";

        try {
            String displayName = stack.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
        } catch (RuntimeException ignored) {
            // Some wildcard-meta recipe stacks throw when resolving translated display names.
        }

        Object registryName = Item.itemRegistry.getNameForObject(item);
        if (registryName != null) {
            return registryName.toString() + ":" + stack.getItemDamage();
        }

        int itemId = Item.getIdFromItem(item);
        return itemId >= 0 ? "[" + itemId + ":" + stack.getItemDamage() + "]" : "";
    }
}
