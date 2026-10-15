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

import com.wztwzt.ae2_qof.util.ItemIdentity;

/**
 * 客户端缓存 AE2 网络库存数据（物品 + 流体）。
 * 由 GuiMEMonitorable.postUpdate 的 Mixin 写入，NEI 渲染时读取。
 * 流体以三种方式承载：ae2fc ItemFluidPacket（NBT "FluidStack" 复合标签）、GT Display_Fluid
 * （ItemFluidDisplay，damage 即流体注册 ID）、流体方块物品（fluidItemMap 反查）。
 * 注意：流体判定必须按物品类名限定，绝不能对所有物品按 itemDamage 查 FluidRegistry——
 * FluidRegistry 的 ID 是注册序（水=0、岩浆=1…），damage 命中即误判，导致随机物品显示流体量。
 */
public final class NetworkInventoryCache {

    private static final Map<ItemIdentity, CacheEntry> cache = new HashMap<>();
    private static final Map<String, CacheEntry> fluidCache = new HashMap<String, CacheEntry>();
    private static final Map<Long, String> fluidItemMap = new HashMap<Long, String>();
    private static long lastUpdateTick = 0;

    /**
     * 数据有效期（#49 最终方案）：关闭终端后保留 5 分钟供 NEI 配方查询，
     * 超时自动失效。终端 GUI 开启期间 postUpdate 持续刷新 lastUpdateTick，
     * 永不过期。注意进 NEI 配方界面（GuiRecipe）底层也会关闭终端容器，
     * 因此不能用"GUI 关闭即清"，只能靠时间窗区分"查配方"与"彻底离开"。
     */
    private static final long STALE_MS = 5 * 60 * 1000L;

    private NetworkInventoryCache() {}

    public static void clear() {
        cache.clear();
        fluidCache.clear();
        fluidItemMap.clear();
        lastUpdateTick = 0;
    }

    /**
     * 写入一条 AE 物品数据（从 postUpdate 调用）。
     * stackSize == 0 表示该物品已从网络移除——但 AE2 同步列表中
     * 「仅有样板、网络无存量」的物品正是 stackSize=0 + craftable=true 的形式，
     * 必须保留（count=0），否则 NEI 面板中键下单（isCraftable）永远拦截（3.10.0 修复）。
     * 仅 stackSize<=0 且不可合成才真正移除。
     */
    public static void put(ItemStack stack, boolean craftable, long stackSize) {
        ItemIdentity key = ItemIdentity.of(stack);
        if (key == null) return;
        if (stackSize <= 0 && !craftable) {
            cache.remove(key);
        } else {
            cache.put(key, new CacheEntry(Math.max(0, stackSize), craftable));
        }
        lastUpdateTick = System.currentTimeMillis();
    }

    /**
     * 写入一条 AE 流体数据（从 postUpdate 调用）。
     * stackSize == 0 表示该流体已从网络移除——craftable 流体同理保留（见 {@link #put}）。
     */
    public static void putFluid(String fluidName, long stackSize, boolean craftable) {
        if (stackSize <= 0 && !craftable) {
            fluidCache.remove(fluidName);
        } else {
            fluidCache.put(fluidName, new CacheEntry(Math.max(0, stackSize), craftable));
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
        CacheEntry entry = cache.get(ItemIdentity.of(stack));
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
        CacheEntry entry = cache.get(ItemIdentity.of(stack));
        return entry != null && entry.craftable;
    }

    /**
     * 一次查询同时返回 count / craftable / fluid（#52）：
     * tooltip 路径原先 getCount + isCraftable + getFluidStack 各自独立做一遍流体识别
     * （类名字符串比较 + NBT 解析），同一 ItemStack 每帧最多重复 3 遍，现合并为单次。
     * fluid 为 null 表示按普通物品处理；count 为 -1 表示网络中无数据。
     */
    public static QueryResult query(ItemStack stack) {
        Fluid f = getFluid(stack);
        if (f != null) {
            CacheEntry entry = fluidCache.get(f.getName());
            return new QueryResult(entry != null ? entry.count : -1, entry != null && entry.craftable, f);
        }
        CacheEntry entry = stack == null ? null : cache.get(ItemIdentity.of(stack));
        return new QueryResult(entry != null ? entry.count : -1, entry != null && entry.craftable, null);
    }

    public static final class QueryResult {

        public final long count;
        public final boolean craftable;
        public final Fluid fluid;

        QueryResult(long count, boolean craftable, Fluid fluid) {
            this.count = count;
            this.craftable = craftable;
            this.fluid = fluid;
        }
    }

    public static boolean hasData() {
        // 时间窗过期（#49）：关闭终端超过 STALE_MS 后视为无数据，
        // tooltip/书签角标统一走本闸门，一处生效。
        if (cache.isEmpty() && fluidCache.isEmpty()) {
            return false;
        }
        return System.currentTimeMillis() - lastUpdateTick <= STALE_MS;
    }

    public static long getLastUpdateTick() {
        return lastUpdateTick;
    }

    /**
     * 判定某物品是否携带可识别的流体：
     * 1) ae2fc 纯流体 packet：按物品类名识别（不 import ae2fc，保持模组独立），流体从 NBT "FluidStack" 复合标签读取；
     * 2) GT Display_Fluid（ItemFluidDisplay）：按物品类名识别，damage 值即流体注册 ID——这是 GT 在 NEI 配方中
     * 展示流体的唯一表示，只对该物品按 damage 查 FluidRegistry，绝不作用到任意物品上（3.1.2 误修导致 GT 流体
     * 显示丢失，3.3.3 恢复）；
     * 3) 已注册的流体方块物品（fluidItemMap 反查）。
     * 其余一律返回 null，按普通物品处理。
     * 切勿对所有物品按 itemDamage 查 FluidRegistry——damage 是物品元数据，与流体注册 ID 无对应关系。
     */
    private static Fluid getFluid(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        if (isAe2fcFluidPacket(stack)) {
            FluidStack packetFluid = readPacketFluid(stack);
            return packetFluid != null ? packetFluid.getFluid() : null;
        }
        if (isGtFluidDisplay(stack)) {
            // 3.35.0-fix9（用户实测恶劣 bug）：**稳定键优先** —— GT display 物品的
            // meta = Fluid.getID()（动态分配，模组表变化即漂移），按 meta 解读会让
            // NEI tooltip 在模组更新后张冠李戴。GT 物品 NBT 里的 mFluidMaterialName
            // 是跨版本稳定的 GT 材料名，优先按它解析；无 NBT 老数据才走 meta 兜底。
            FluidStack stable = readGtDisplayStable(stack);
            if (stable != null) return stable.getFluid();
            return FluidRegistry.getFluid(stack.getItemDamage());
        }
        String fluidName = fluidItemMap.get(key(stack));
        return fluidName != null ? FluidRegistry.getFluid(fluidName) : null;
    }

    /**
     * 3.35.0-fix9：按 GT 写入物品 NBT 的 {@code mFluidMaterialName}（GT 材料名，
     * 字节码 GTUtility.getFluidDisplayStack offset 127-148 实证为写入方）解析流体。
     * 名称解析用精确匹配（含 "fluid." 前缀剥离），**绝不做模糊 contains**——
     * 注册表遍历顺序不定，模糊匹配正是"随机错流体"的另一个来源。
     */
    private static FluidStack readGtDisplayStable(ItemStack stack) {
        if (!stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey("mFluidMaterialName")) return null;
        String matName = tag.getString("mFluidMaterialName");
        if (matName == null || matName.isEmpty()) return null;
        Fluid fluid = FluidRegistry.getFluid(matName);
        if (fluid == null && matName.startsWith("fluid.")) {
            fluid = FluidRegistry.getFluid(matName.substring(6));
        }
        if (fluid == null) {
            String lower = matName.toLowerCase();
            for (Fluid registered : FluidRegistry.getRegisteredFluids()
                .values()) {
                if (registered.getName() != null && registered.getName()
                    .toLowerCase()
                    .equals(lower)) {
                    fluid = registered;
                    break;
                }
            }
        }
        if (fluid == null) return null;
        int amount = tag.getInteger("mFluidDisplayAmount");
        if (amount <= 0) amount = 1000;
        return new FluidStack(fluid, amount);
    }

    private static boolean isGtFluidDisplay(ItemStack stack) {
        try {
            String className = stack.getItem()
                .getClass()
                .getName();
            return className.equals("gregtech.common.items.ItemFluidDisplay");
        } catch (Throwable ignored) {
            return false;
        }
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
