/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.item;

import java.util.ArrayList;
import java.util.List;

import com.wztwzt.ae2_qof.wildport.crafting.WildcardPatternEntry;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

public final class WildcardPatternState {

    private static final String KEY_INPUT_COMPONENTS = "WildcardInputComponents";
    private static final String KEY_OUTPUT_COMPONENTS = "WildcardOutputComponents";
    private static final String KEY_EXPANDED_PATTERN_COUNT = "WildcardExpandedPatternCount";
    private static final String KEY_SELECTED_MATERIAL = "WildcardSelectedMaterial";
    private static final String KEY_GENERATED_PATTERN_ID = "WildcardGeneratedPatternId";

    private WildcardPatternState() {}

    public static void ensureInitialized(ItemStack stack) {
        if (stack == null) {
            return;
        }

        NBTTagCompound tag = getOrCreateTag(stack);
        if (!tag.hasKey(KEY_INPUT_COMPONENTS, NBT.TAG_LIST)) {
            tag.setTag(KEY_INPUT_COMPONENTS, importPatternList(tag.getTagList("in", NBT.TAG_COMPOUND)));
        }
        if (!tag.hasKey(KEY_OUTPUT_COMPONENTS, NBT.TAG_LIST)) {
            tag.setTag(KEY_OUTPUT_COMPONENTS, importPatternList(tag.getTagList("out", NBT.TAG_COMPOUND)));
        }
        cleanupLegacyPatternSlots(stack, tag);
    }

    public static void initializeFromPattern(ItemStack stack) {
        ensureInitialized(stack);
    }

    public static List<WildcardPatternEntry> getInputEntries(ItemStack stack) {
        return getEntries(stack, KEY_INPUT_COMPONENTS);
    }

    public static List<WildcardPatternEntry> getOutputEntries(ItemStack stack) {
        return getEntries(stack, KEY_OUTPUT_COMPONENTS);
    }

    public static void setInputEntries(ItemStack stack, List<WildcardPatternEntry> entries) {
        getOrCreateTag(stack).setTag(KEY_INPUT_COMPONENTS, writeEntries(entries));
    }

    public static void setOutputEntries(ItemStack stack, List<WildcardPatternEntry> entries) {
        getOrCreateTag(stack).setTag(KEY_OUTPUT_COMPONENTS, writeEntries(entries));
    }

    public static int getExpandedPatternCount(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return tag == null || !tag.hasKey(KEY_EXPANDED_PATTERN_COUNT) ? 0 : Math.max(0, tag.getInteger(KEY_EXPANDED_PATTERN_COUNT));
    }

    public static void setExpandedPatternCount(ItemStack stack, int count) {
        if (stack == null) {
            return;
        }
        getOrCreateTag(stack).setInteger(KEY_EXPANDED_PATTERN_COUNT, Math.max(0, count));
    }

    public static void applyBitModification(ItemStack stack, int bitMultiplier) {
        if (stack == null || bitMultiplier == 0) {
            return;
        }

        int factor = 1 << Math.min(30, Math.abs(bitMultiplier));
        List<WildcardPatternEntry> inputs = getInputEntries(stack);
        List<WildcardPatternEntry> outputs = getOutputEntries(stack);
        for (WildcardPatternEntry entry : inputs) {
            applyFactor(entry, factor, bitMultiplier < 0);
        }
        for (WildcardPatternEntry entry : outputs) {
            applyFactor(entry, factor, bitMultiplier < 0);
        }
        setInputEntries(stack, inputs);
        setOutputEntries(stack, outputs);
    }

    public static int getMaxBitMultiplier(ItemStack stack) {
        return getMaxBitModification(stack, false);
    }

    public static int getMaxBitDivider(ItemStack stack) {
        return getMaxBitModification(stack, true);
    }

    public static NBTTagCompound exportConfig(ItemStack stack) {
        initializeFromPattern(stack);
        NBTTagCompound exported = new NBTTagCompound();
        NBTTagCompound source = getOrCreateTag(stack);
        exported.setTag(KEY_INPUT_COMPONENTS, source.getTagList(KEY_INPUT_COMPONENTS, NBT.TAG_COMPOUND).copy());
        exported.setTag(KEY_OUTPUT_COMPONENTS, source.getTagList(KEY_OUTPUT_COMPONENTS, NBT.TAG_COMPOUND).copy());
        copyIfPresent(source, exported, "WildcardGlobalExcludeMaterials");
        copyIfPresent(source, exported, "WildcardRuleIncludeMaterials");
        copyIfPresent(source, exported, "WildcardRuleExcludeMaterials");
        copyIfPresent(source, exported, "WildcardOreDictPreferences");
        copyIfPresent(source, exported, KEY_EXPANDED_PATTERN_COUNT);
        copyIfPresent(source, exported, KEY_SELECTED_MATERIAL);
        return exported;
    }

    public static void applyConfig(ItemStack stack, NBTTagCompound config) {
        if (stack == null || config == null) {
            return;
        }
        ensureInitialized(stack);
        NBTTagCompound tag = getOrCreateTag(stack);
        copyIfPresent(config, tag, KEY_INPUT_COMPONENTS);
        copyIfPresent(config, tag, KEY_OUTPUT_COMPONENTS);
        copyIfPresent(config, tag, "WildcardGlobalExcludeMaterials");
        copyIfPresent(config, tag, "WildcardRuleIncludeMaterials");
        copyIfPresent(config, tag, "WildcardRuleExcludeMaterials");
        copyIfPresent(config, tag, "WildcardOreDictPreferences");
        copyIfPresent(config, tag, KEY_EXPANDED_PATTERN_COUNT);
        copyIfPresent(config, tag, KEY_SELECTED_MATERIAL);
    }

    /**
     * 参考实现的"旧槽清理"：把原生 {@code in}/{@code out} 删掉，改用本类自己的组件列表。
     *
     * <p><b>3.35.0 修正（致命）</b>：**对我们的通配样板跳过这个删除**。本模组的展开器
     * （{@code SmartWildcardExpander.doExpand}）以原生 {@code in}/{@code out} 当模板来逐候选克隆，
     * 删掉就等于把模板扔掉 —— 3.34.0 实机证据：{@code reason=template-in-out-missing} 出现 **23 次**、
     * 展开产出恒 0，机器于是"直接按这个样板自己的合成"。
     *
     * <p>**刻意只对 {@code CommonProxy.smartWildcardPattern} 生效**：原版 WildcardPattern 模组的物品
     * 保持它原本的行为（用户明确要求两侧互不干扰；实例里两个模组同时装着）。
     */
    private static void cleanupLegacyPatternSlots(ItemStack stack, NBTTagCompound tag) {
        if (tag.hasKey(KEY_GENERATED_PATTERN_ID)) {
            return;
        }
        if (stack != null && stack.getItem() == com.wztwzt.ae2_qof.CommonProxy.smartWildcardPattern) {
            return; // 我们的样板：原生 in/out 是展开器的模板，绝不能删
        }
        tag.removeTag("in");
        tag.removeTag("out");
    }

    private static List<WildcardPatternEntry> getEntries(ItemStack stack, String key) {
        initializeFromPattern(stack);
        NBTTagList list = getOrCreateTag(stack).getTagList(key, NBT.TAG_COMPOUND);
        List<WildcardPatternEntry> result = new ArrayList<>();
        for (int index = 0; index < list.tagCount(); index++) {
            result.add(WildcardPatternEntry.fromNbt(list.getCompoundTagAt(index)));
        }
        return result;
    }

    private static NBTTagList importPatternList(NBTTagList source) {
        NBTTagList result = new NBTTagList();
        for (int index = 0; index < source.tagCount(); index++) {
            WildcardPatternEntry entry = WildcardPatternEntry.fromPatternSlot(source.getCompoundTagAt(index));
            result.appendTag(entry.toNbt());
        }
        return result;
    }

    private static NBTTagList writeEntries(List<WildcardPatternEntry> entries) {
        NBTTagList list = new NBTTagList();
        for (WildcardPatternEntry entry : entries) {
            list.appendTag(entry.toNbt());
        }
        return list;
    }

    private static void applyFactor(WildcardPatternEntry entry, int factor, boolean dividing) {
        if (entry == null || entry.isEmpty()) {
            return;
        }
        if (dividing) {
            entry.divideAmount(factor);
        } else {
            entry.multiplyAmount(factor);
        }
    }

    private static int getMaxBitModification(ItemStack stack, boolean dividing) {
        int result = 30;
        boolean found = false;
        for (WildcardPatternEntry entry : getInputEntries(stack)) {
            if (entry != null && !entry.isEmpty()) {
                result = Math.min(result, getMaxBits(entry.getAmountLong(), dividing));
                found = true;
            }
        }
        for (WildcardPatternEntry entry : getOutputEntries(stack)) {
            if (entry != null && !entry.isEmpty()) {
                result = Math.min(result, getMaxBits(entry.getAmountLong(), dividing));
                found = true;
            }
        }
        return found ? result : 0;
    }

    private static int getMaxBits(long amount, boolean dividing) {
        long value = Math.max(1L, amount);
        int bits = 0;
        if (dividing) {
            while ((value & 1) == 0) {
                value >>= 1;
                bits++;
            }
        } else {
            while (value > 0 && value <= WildcardPatternEntry.MAX_AMOUNT / 2L) {
                value <<= 1;
                bits++;
            }
        }
        return bits;
    }

    private static void copyIfPresent(NBTTagCompound source, NBTTagCompound target, String key) {
        if (source.hasKey(key)) {
            target.setTag(key, source.getTag(key).copy());
        }
    }

    private static NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }
}
