package com.gali.ae2_auto_pattern_upload.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * 客户端缓存 AE2 网络库存数据（物品 + 流体）。
 * 由 GuiMEMonitorable.postUpdate 的 Mixin 写入，NEI 渲染时读取。
 * 流体以 FluidStack 的 NBT（"FluidStack" compound）承载在 ItemFluidPacket 等物品上，按键为流体名。
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
     * 仅 ae2fc 纯流体 packet（damage 编码流体）返回流体量（mB）；
     * 桶/单元等容器物品一律按普通物品返回其在网络中的容器数量（AE 里没有则不显示）。
     */
    public static long getCount(ItemStack stack) {
        Fluid damageFluid = getFluidByDamage(stack);
        if (damageFluid != null) {
            CacheEntry entry = fluidCache.get(damageFluid.getName());
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
        Fluid damageFluid = getFluidByDamage(stack);
        if (damageFluid != null) {
            CacheEntry entry = fluidCache.get(damageFluid.getName());
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
     * 是否为 ae2fc 纯流体 packet：damage 值直接编码流体注册 ID（FluidRegistry.getFluid(damage)）。
     * 只有此类物品才被当作"流体"显示流体量；水桶/单元等容器不在此列。
     */
    public static boolean isFluidPacket(ItemStack stack) {
        return stack != null && FluidRegistry.getFluid(stack.getItemDamage()) != null;
    }

    private static Fluid getFluidByDamage(ItemStack stack) {
        return stack != null ? FluidRegistry.getFluid(stack.getItemDamage()) : null;
    }

    /**
     * 从 ItemStack 提取 FluidStack（仅用于 tooltip 显示流体名）。
     * 仅 ae2fc 纯流体 packet（damage 编码）返回对应流体；容器物品返回 null。
     */
    public static FluidStack getFluidStack(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        Fluid damageFluid = FluidRegistry.getFluid(stack.getItemDamage());
        if (damageFluid != null) {
            return new FluidStack(damageFluid, FluidContainerRegistry.BUCKET_VOLUME);
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
