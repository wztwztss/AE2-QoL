package com.wztwzt.ae2_qof.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * 客户端缓存 AE2 网络库存数据（物品 + 流体）。
 * 由 GuiMEMonitorable.postUpdate 的 Mixin 写入，NEI 渲染时读取。
 * 流体以 FluidStack 的 NBT（"FluidStack" compound）承载在 ItemFluidPacket 等物品上，按键为流体名。
 * 注意：流体判定必须按物品类名（ae2fc ItemFluidPacket）+ NBT，绝不能按 itemDamage 查 FluidRegistry——
 * FluidRegistry 的 ID 是注册序（水=0、岩浆=1…），damage 命中即误判，导致随机物品显示流体量。
 */
public final class NetworkInventoryCache {

    private static final Map<Long, CacheEntry> cache = new HashMap<Long, CacheEntry>();
    private static final Map<String, CacheEntry> fluidCache = new HashMap<String, CacheEntry>();
    private static final Map<Long, String> fluidItemMap = new HashMap<Long, String>();
    private static long lastUpdateTick = 0;

    private NetworkInventoryCache() {}

    public static void clear() {
        cache.clear();
        fluidCache.clear();
        fluidItemMap.clear();
        lastUpdateTick = 0;
    }

    /**
     * 写入一条 AE 物品数据（从 postUpdate 调用）。
     * stackSize == 0 表示该物品已从网络移除。
     */
    public static void put(int itemId, int damage, int count, boolean craftable, long stackSize) {
        long key = key(itemId, damage);
        if (stackSize <= 0) {
            cache.remove(key);
        } else {
            cache.put(key, new CacheEntry(stackSize, craftable));
        }
        lastUpdateTick = System.currentTimeMillis();
    }

    /**
     * 写入一条 AE 流体数据（从 postUpdate 调用）。
     * stackSize == 0 表示该流体已从网络移除。
     */
    public static void putFluid(String fluidName, long stackSize, boolean craftable) {
        if (stackSize <= 0) {
            fluidCache.remove(fluidName);
        } else {
            fluidCache.put(fluidName, new CacheEntry(stackSize, craftable));
        }
        lastUpdateTick = System.currentTimeMillis();
    }

    /**
     * 清除全部缓存（终端关闭时调用）。
     */
    public static void invalidate() {
        cache.clear();
        fluidCache.clear();
        fluidItemMap.clear();
    }

    /**
     * 查询某 ItemStack 在 AE2 网络中的数量，返回 -1 表示无数据。
     * 仅 ae2fc 纯流体 packet（类名识别 + NBT 读流体）返回流体量（mB）；
     * 桶/单元等容器物品一律按普通物品返回其在网络中的容器数量（AE 里没有则不显示）。
     */
    public static long getCount(ItemStack stack) {
        Fluid stackFluid = getFluid(stack);
        if (stackFluid != null) {
            CacheEntry entry = fluidCache.get(stackFluid.getName());
            return entry != null ? entry.count : -1;
        }
        CacheEntry entry = cache.get(key(stack));
        return entry != null ? entry.count : -1;
    }

    /**
     * 注册一个流体方块物品 → 流体名的映射（在 postUpdate 中从 IAEFluidStack 调用）。
     * 用于让 getFluidStack 识别纯流体方块物品（水/岩浆/模组流体方块）。
     */
    public static void registerFluidItem(String fluidName, int itemId, int damage) {
        fluidItemMap.put(key(itemId, damage), fluidName);
    }

    /**
     * 查询某 ItemStack 是否可合成，无数据返回 false。
     * 仅 ae2fc 纯流体 packet 走流体缓存；其余按普通物品查物品缓存。
     */
    public static boolean isCraftable(ItemStack stack) {
        Fluid stackFluid = getFluid(stack);
        if (stackFluid != null) {
            CacheEntry entry = fluidCache.get(stackFluid.getName());
            return entry != null && entry.craftable;
        }
        CacheEntry entry = cache.get(key(stack));
        return entry != null && entry.craftable;
    }

    public static boolean hasData() {
        return !cache.isEmpty() || !fluidCache.isEmpty();
    }

    public static long getLastUpdateTick() {
        return lastUpdateTick;
    }

    /**
     * 判定某物品是否携带可识别的流体：
     * 1) ae2fc 纯流体 packet：按物品类名识别（不 import ae2fc，保持模组独立），流体从 NBT "FluidStack" 复合标签读取；
     * 2) 已注册的流体方块物品（fluidItemMap 反查）；
     * 其余一律返回 null，按普通物品处理。
     * 切勿用 itemDamage 查 FluidRegistry——damage 是物品元数据，与流体注册 ID 无对应关系。
     */
    private static Fluid getFluid(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        if (isAe2fcFluidPacket(stack)) {
            FluidStack packetFluid = readPacketFluid(stack);
            return packetFluid != null ? packetFluid.getFluid() : null;
        }
        String fluidName = fluidItemMap.get(key(stack));
        return fluidName != null ? FluidRegistry.getFluid(fluidName) : null;
    }

    private static boolean isAe2fcFluidPacket(ItemStack stack) {
        try {
            String className = stack.getItem()
                .getClass()
                .getName();
            return className.equals("com.glodblock.github.common.item.ItemFluidPacket");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static FluidStack readPacketFluid(ItemStack stack) {
        try {
            if (!stack.hasTagCompound()) {
                return null;
            }
            NBTTagCompound tag = stack.getTagCompound()
                .getCompoundTag("FluidStack");
            if (tag == null || tag.hasNoTags()) {
                return null;
            }
            return FluidStack.loadFluidStackFromNBT(tag);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 从 ItemStack 提取 FluidStack（仅用于 tooltip 显示流体名）。
     * 仅 ae2fc 纯流体 packet 与已注册流体方块物品返回对应流体；容器物品返回 null。
     */
    public static FluidStack getFluidStack(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        Fluid stackFluid = getFluid(stack);
        if (stackFluid != null) {
            return new FluidStack(stackFluid, FluidContainerRegistry.BUCKET_VOLUME);
        }
        return null;
    }

    private static long key(ItemStack stack) {
        return key(Item.getIdFromItem(stack.getItem()), stack.getItemDamage());
    }

    private static long key(int itemId, int damage) {
        return ((long) itemId << 32) | (damage & 0xFFFFFFFFL);
    }

    private static class CacheEntry {

        final long count;
        final boolean craftable;

        CacheEntry(long count, boolean craftable) {
            this.count = count;
            this.craftable = craftable;
        }
    }
}
