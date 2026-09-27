/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * OreDictionary helper methods with defensive bounds checks.
 */
public final class OreDictUtil {

    private OreDictUtil() {}

    public static String[] getOreNamesSafe(ItemStack stack) {
        if (stack == null) return new String[0];
        try {
            int[] oreIds = OreDictionary.getOreIDs(stack);
            return getOreNamesSafe(oreIds, OreDictionary.getOreNames());
        } catch (RuntimeException ignored) {
            return new String[0];
        }
    }

    static String[] getOreNamesSafe(int[] oreIds, String[] oreNamesById) {
        if (oreIds == null || oreIds.length == 0 || oreNamesById == null || oreNamesById.length == 0) {
            return new String[0];
        }

        List<String> names = new ArrayList<>(oreIds.length);
        for (int oreId : oreIds) {
            if (oreId < 0 || oreId >= oreNamesById.length) {
                continue;
            }

            String oreName = oreNamesById[oreId];
            if (oreName != null && !oreName.isEmpty()) {
                names.add(oreName);
            }
        }

        return names.toArray(new String[0]);
    }
}
