package com.wztwzt.ae2_qof.machindex.common;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 来源模组解析（纯通用逻辑，不引用 NEI/BQ 类型）。
 *
 * <p>
 * 解析顺序：{@code GameRegistry.findUniqueIdentifierFor} 的 modId → {@code ModContainer.getName()}
 * → UNKNOWN_MOD。取不到时<b>仍保留条目</b>（硬约束 6：解析不出的一律保留并记录原因），
 * 只是把 {@code modId} 标成 {@link #UNKNOWN_MOD}。
 */
public final class ModSourceResolver {

    private ModSourceResolver() {}

    public static final String UNKNOWN_MOD = "UNKNOWN_MOD";

    /**
     * 解析并写进 {@code entry}。
     *
     * @param stack 催化剂堆
     * @param entry 目标条目
     */
    public static void resolve(ItemStack stack, IndexModel.MachineEntry entry) {
        if (stack == null || stack.getItem() == null) {
            entry.modId = UNKNOWN_MOD;
            entry.modName = "?";
            entry.note = VoltageResolver.appendNote(entry.note, "物品为空");
            return;
        }

        Item item = stack.getItem();
        String modId = null;
        try {
            // 返回类型是 GameRegistry.UniqueIdentifier（不是 String[]）；其 toString() 是 "modid:name"
            GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(item);
            if (uid != null) {
                modId = uid.modId;
            }
        } catch (Throwable t) {
            // findUniqueIdentifierFor 对部分 ItemBlock 会抛：按未解析处理，继续走兜底
        }

        if (modId == null || modId.isEmpty()) {
            // 兜底 1：方块物品按 Block 再找一次（ItemBlock 的注册名有时挂在 Block 上）
            try {
                net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(item);
                if (block != null && block != net.minecraft.init.Blocks.air) {
                    GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(block);
                    if (uid != null) modId = uid.modId;
                }
            } catch (Throwable t) {
                // 继续走下一步
            }
        }

        if (modId == null || modId.isEmpty()) {
            // 兜底 2：按注册名 "modid:name" 的前缀切（注册名一定带域前缀）
            try {
                String reg = net.minecraft.item.Item.itemRegistry.getNameForObject(item);
                if (reg != null && reg.indexOf(':') > 0) {
                    modId = reg.substring(0, reg.indexOf(':'));
                }
            } catch (Throwable t) {
                // 放弃解析
            }
        }

        if (modId == null || modId.isEmpty()) {
            entry.modId = UNKNOWN_MOD;
            entry.modName = "?";
            entry.note = VoltageResolver.appendNote(entry.note, "无法解析来源模组（Item=" + item.getUnlocalizedName() + "）");
            return;
        }

        entry.modId = modId;
        entry.modName = displayNameOfMod(modId);
    }

    /** modId → 人类可读模组名；取不到回落 modId 本身。 */
    public static String displayNameOfMod(String modId) {
        try {
            ModContainer mc = Loader.instance()
                .getIndexedModList()
                .get(modId);
            if (mc != null) {
                String name = mc.getName();
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Throwable t) {
            // 忽略：回落到 modId
        }
        return modId;
    }

    /** 按配置拼「来源」展示串。 */
    public static String formatSource(IndexModel.MachineEntry entry) {
        String mode = IndexConfig.modNameDisplay;
        if ("id".equals(mode)) return entry.modId;
        if ("name".equals(mode)) return entry.modName;
        if (entry.modName == null || entry.modName.equals(entry.modId)) return entry.modId;
        return entry.modName + " (" + entry.modId + ")";
    }
}
