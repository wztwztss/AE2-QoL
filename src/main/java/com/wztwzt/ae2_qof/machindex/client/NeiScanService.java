package com.wztwzt.ae2_qof.machindex.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.machindex.common.IndexConfig;
import com.wztwzt.ae2_qof.machindex.common.IndexModel;
import com.wztwzt.ae2_qof.machindex.common.MachineIndexCache;
import com.wztwzt.ae2_qof.machindex.common.ModSourceResolver;
import com.wztwzt.ae2_qof.machindex.common.VoltageResolver;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeCatalysts;
import gregtech.nei.GTNEIDefaultHandler;

/**
 * NEI 扫描服务 —— <b>全项目唯一 import {@code codechicken.nei.*} 的类</b>。
 *
 * <p>
 * <b>隔离纪律</b>：本类只允许在 {@code Loader.isModLoaded("NotEnoughItems")} 守卫通过后加载/调用；
 * 专用服务器没有 NEI 类，一旦在服务端加载就会 {@code NoClassDefFoundError}。
 * 所以调用方（{@link com.wztwzt.ae2_qof.machindex.MachIndexPlugin}）必须把类引用放在守卫之后。
 *
 * <p>
 * <b>为什么必须同时遍历 handler 列表与催化剂表：</b>
 * {@code RecipeCatalysts.getPositionedRecipeCatalystMap()} 只给「recipeId → 催化剂堆」，
 * <b>没有 handler 实例</b>；而本索引要的「类型显示名」「GTNEIDefaultHandler.getRecipeMap()」
 * 都必须是实例。因此这里建两层：
 * <ol>
 * <li>handler 索引：遍历 {@code GuiCraftingRecipe.craftinghandlers} + {@code GuiUsageRecipe.usagehandlers}，
 * 按 {@code RecipeCatalysts.getRecipeID(handler)} 归组；</li>
 * <li>催化剂表：{@code getPositionedRecipeCatalystMap()} 快照。</li>
 * </ol>
 * 两者求并集：只在 handler 侧出现 → 标 {@code noCatalyst}；只在催化剂侧出现 → 保留（用它自己的
 * recipeId 当类型，催化剂堆照样解析），两边都缺的类型不存在。
 *
 * <p>
 * <b>不碰 {@code TemplateRecipeHandler.arecipes}</b>：那是懒加载（打开对应 GUI 才填充），
 * 离线读会触发不必要的配方计算且结果不可靠。本索引只要类型元数据 + 催化剂列表，
 * 电压则走 {@link VoltageResolver}（配方电压兜底时才读 GT 的 RecipeMap 静态列表）。
 */
public final class NeiScanService {

    private NeiScanService() {}

    /** 扫描阶段计时（供统计日志）。 */
    public static final class Timings {

        public long snapshotMs;
        public long resolveMs;
        public long totalMs;
    }

    /**
     * 执行一次全量扫描（**必须在主线程调用**：会触碰 NEI 的静态集合与 ItemStack）。
     *
     * @return 扫描结果（含 types/machines/stats）；失败返回 null
     */
    public static IndexModel.ScanResult scan(Timings timings) {
        long t0 = System.currentTimeMillis();
        IndexModel.ScanResult result = new IndexModel.ScanResult();
        try {
            // ---------- 1) 快照 ----------
            long s0 = System.currentTimeMillis();
            List<IRecipeHandler> handlers = snapshotHandlers();
            timings.snapshotMs = System.currentTimeMillis() - s0;

            // ---------- 2) 类型索引 ----------
            Map<String, TypeAccumulator> typeAcc = buildTypeIndex(handlers);

            // ---------- 3) 收集催化剂 → 机器 ----------
            long r0 = System.currentTimeMillis();
            Map<String, IndexModel.MachineEntry> machinesByIdentity = new LinkedHashMap<>();
            int catalystCount = 0;
            int resolvedCount = 0;
            int typeWithCatalyst = 0;
            int typeWithoutCatalyst = 0;

            for (TypeAccumulator acc : typeAcc.values()) {
                String typeId = acc.typeId;
                // **必须按 handler 实例取**：NEI 2.8.155 的
                // RecipeCatalysts.getPositionedRecipeCatalystMap() 是**空实现**（直接 return new HashMap），
                // 照它遍历会得到 0 台机器（4.5.0 首次实测：类型 537、机器 0）。
                // getRecipeCatalysts(IRecipeHandler) → getRecipeID(handler) → 私有 recipeCatalystMap，
                // 才是真正持数据的公开入口。
                List<PositionedStack> stacks = new ArrayList<>();
                for (String handlerId : acc.handlerIds) {
                    IRecipeHandler handler = acc.handlersById.get(handlerId);
                    if (handler == null) continue;
                    try {
                        List<PositionedStack> one = RecipeCatalysts.getRecipeCatalysts(handler);
                        if (one != null && !one.isEmpty()) stacks.addAll(one);
                    } catch (Throwable t) {
                        // 单个 handler 取催化剂失败不中断整轮（硬约束：单点失败只计数跳过）
                        if (IndexConfig.debugLog) {
                            MyMod.LOG.warn("[AE2QoL] machindex 取催化剂失败：" + handlerId + " → " + t);
                        }
                    }
                }
                if (stacks.isEmpty()) {
                    typeWithoutCatalyst++;
                    continue;
                }
                typeWithCatalyst++;

                for (PositionedStack ps : stacks) {
                    if (ps == null) continue;
                    for (ItemStack stack : psItemStacks(ps)) {
                        catalystCount++;
                        IndexModel.MachineEntry entry = machinesByIdentity.get(identityOf(stack));
                        if (entry == null) {
                            entry = buildEntry(stack);
                            machinesByIdentity.put(entry.identityKey(), entry);
                        }
                        if (!entry.typeIds.contains(typeId)) entry.typeIds.add(typeId);
                        resolvedCount++;
                    }
                }
            }
            MyMod.LOG.info(
                "[AE2QoL] machindex 催化剂遍历：有催化剂的类型=" + typeWithCatalyst
                    + "，无催化剂的类型="
                    + typeWithoutCatalyst
                    + "，催化剂条目="
                    + catalystCount);

            // ---------- 4) 组装 ----------
            for (TypeAccumulator acc : typeAcc.values()) {
                result.types.add(acc.toModel());
            }
            result.machines.addAll(machinesByIdentity.values());
            for (IndexModel.MachineEntry m : result.machines) {
                if (!m.typeIds.isEmpty()) continue;
                m.note = VoltageResolver.appendNote(m.note, "无归属类型");
            }

            timings.resolveMs = System.currentTimeMillis() - r0;
            timings.totalMs = System.currentTimeMillis() - t0;

            if (IndexConfig.debugLog) {
                MyMod.LOG.info(
                    "[AE2QoL] machindex 扫描明细：handler=" + handlers.size()
                        + " 催化剂条目="
                        + catalystCount
                        + " 归属次数="
                        + resolvedCount
                        + " 去重机器="
                        + result.machines.size()
                        + " 类型="
                        + result.types.size());
            }
            return result;
        } catch (Throwable t) {
            // 整轮失败：不写缓存（下次启动重试），但必须留痕，否则「索引没生成」将无从判断
            MyMod.LOG.error("[AE2QoL] machindex 扫描失败（本轮不写缓存，下次启动重试）", t);
            return null;
        }
    }

    /** handler 列表快照（复制一份：NEI 运行期会重载/追加 handler）。 */
    private static List<IRecipeHandler> snapshotHandlers() {
        List<IRecipeHandler> out = new ArrayList<>();
        try {
            if (GuiCraftingRecipe.craftinghandlers != null) {
                out.addAll(GuiCraftingRecipe.craftinghandlers);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 读取 craftinghandlers 失败：" + t);
        }
        try {
            if (GuiUsageRecipe.usagehandlers != null) {
                // 同一个 handler 通常同时在两张表里注册，去重靠后续按 recipeId 归组
                out.addAll(GuiUsageRecipe.usagehandlers);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 读取 usagehandlers 失败：" + t);
        }
        return out;
    }

    /**
     * <b>已废弃、勿再使用</b>：{@code RecipeCatalysts.getPositionedRecipeCatalystMap()}。
     *
     * <p>
     * 4.5.0 首次实测踩到：该方法在 NEI 2.8.155 里是**空实现**——
     * {@code javap -c} 只有 {@code new HashMap; areturn}，恒返回空表。
     * 照它遍历的后果是「配方类型 537、机器主机 0、任务 0」。
     * 正确入口是按 handler 实例调 {@link RecipeCatalysts#getRecipeCatalysts(IRecipeHandler)}。
     * 这些注释保留在这里，是为了防止后来者再"顺手用它"。
     */

    /** 按 recipeId 归组 handler；同时把催化剂表里出现过的 recipeId 也纳入（标 noCatalyst）。 */
    private static Map<String, TypeAccumulator> buildTypeIndex(List<IRecipeHandler> handlers) {
        Map<String, TypeAccumulator> out = new LinkedHashMap<>();
        for (IRecipeHandler handler : handlers) {
            if (handler == null) continue;
            String typeId;
            try {
                typeId = RecipeCatalysts.getRecipeID(handler);
            } catch (Throwable t) {
                continue;
            }
            if (typeId == null || typeId.isEmpty()) continue;

            TypeAccumulator acc = out.computeIfAbsent(typeId, TypeAccumulator::new);
            acc.addHandler(handler);
        }
        return out;
    }

    /** handler 的显示名：GT handler 优先翻译 RecipeMap 的本地化键，其余退回 getRecipeName/getRecipeTabName。 */
    private static String displayNameOf(IRecipeHandler handler) {
        try {
            if (handler instanceof GTNEIDefaultHandler) {
                gregtech.api.recipe.RecipeMap<?> map = ((GTNEIDefaultHandler) handler).getRecipeMap();
                if (map != null) {
                    String key = map.unlocalizedName;
                    String localized = StatCollector.translateToLocal(key);
                    if (localized != null && !localized.isEmpty() && !localized.equals(key)) {
                        return localized;
                    }
                    // GT 的 RecipeMap unlocalizedName 形如 gt.recipe.orewasher：去掉前缀再试一次
                    if (key.startsWith("gt.recipe.")) {
                        String shortKey = key.substring("gt.recipe.".length());
                        String s2 = StatCollector.translateToLocal("gt.recipe." + shortKey);
                        if (s2 != null && !s2.isEmpty() && !s2.equals("gt.recipe." + shortKey)) return s2;
                    }
                }
            }
            String name = handler.getRecipeName();
            if (name != null && !name.isEmpty()) return name;
            String tab = handler.getRecipeTabName();
            if (tab != null && !tab.isEmpty()) return tab;
        } catch (Throwable t) {
            // 第三方 handler 的 getRecipeName 可能抛异常：回落到 typeId（不丢类型）
        }
        return null;
    }

    /** PositionedStack 可能带多个候选（如矿辞），全都要收（硬约束：保留而非丢弃）。 */
    private static List<ItemStack> psItemStacks(PositionedStack ps) {
        List<ItemStack> out = new ArrayList<>();
        try {
            if (ps.items != null && ps.items.length > 0) {
                for (ItemStack s : ps.items) {
                    if (s != null) out.add(s);
                }
            } else if (ps.item != null) {
                out.add(ps.item);
            }
        } catch (Throwable t) {
            // 坏掉的 PositionedStack 直接跳过（已在调用侧计数）
        }
        return out;
    }

    /** 机器身份：registryName@meta#nbtHash（NBT 只取哈希，避免整份 NBT 参与字符串比较）。 */
    private static String identityOf(ItemStack stack) {
        return registryNameOf(stack) + "@" + stack.getItemDamage() + "#" + nbtHashOf(stack);
    }

    private static String registryNameOf(ItemStack stack) {
        try {
            net.minecraft.item.Item item = stack.getItem();
            String name = net.minecraft.item.Item.itemRegistry.getNameForObject(item);
            if (name != null) return name;
        } catch (Throwable t) {
            // 回落到 unlocalizedName
        }
        return stack.getItem()
            .getUnlocalizedName();
    }

    private static String nbtHashOf(ItemStack stack) {
        if (stack.stackTagCompound == null) return "";
        // NBT 的 hashCode 在 1.7.10 是稳定的（基于内容），足够做身份区分
        return Integer.toHexString(stack.stackTagCompound.hashCode());
    }

    /** 用催化剂堆建一个机器条目（解析电压/来源/显示名）。 */
    private static IndexModel.MachineEntry buildEntry(ItemStack stack) {
        IndexModel.MachineEntry entry = new IndexModel.MachineEntry();
        entry.registryName = registryNameOf(stack);
        entry.meta = stack.getItemDamage();
        entry.nbtHash = nbtHashOf(stack);
        entry.displayStack = stack.copy();
        entry.displayName = VoltageResolver.displayNameOf(stack);

        ModSourceResolver.resolve(stack, entry);
        VoltageResolver.resolve(stack, entry);
        return entry;
    }

    /** handler 列表的确定性哈希（进缓存键：handler 集合变了说明 NEI 插件集变了，应重扫）。 */
    public static String handlerListHash() {
        try {
            List<String> ids = new ArrayList<>();
            for (IRecipeHandler h : snapshotHandlers()) {
                if (h == null) continue;
                String id = h.getHandlerId();
                if (id != null && !id.isEmpty()) ids.add(id);
            }
            java.util.Collections.sort(ids);
            Set<String> uniq = new LinkedHashSet<>(ids);
            return MachineIndexCache.sha256(MachineIndexCache.join(new ArrayList<>(uniq), "\n"));
        } catch (Throwable t) {
            return "";
        }
    }

    /** 类型累积器（扫描期临时对象）。 */
    private static final class TypeAccumulator {

        final String typeId;
        final Set<String> handlerIds = new LinkedHashSet<>();
        /**
         * handlerId → handler 实例。
         *
         * <p>
         * 必须留实例：{@code RecipeCatalysts.getRecipeCatalysts(IRecipeHandler)} 只接受实例，
         * 而 NEI 没有给出「recipeId → 催化剂」的可用公开 Map（那个方法在 2.8.155 是空实现）。
         * 用一个类型可能对应多个 handler（同一 recipeId 的多个显示页），全部保留。
         */
        final Map<String, IRecipeHandler> handlersById = new LinkedHashMap<>();
        String handlerClass = "";
        String displayName;
        boolean noCatalyst = false;

        TypeAccumulator(String typeId) {
            this.typeId = typeId;
        }

        void addHandler(IRecipeHandler handler) {
            String id = null;
            try {
                id = handler.getHandlerId();
            } catch (Throwable ignored) {
                // handlerId 取不到时用类名兜底，保证实例仍能被后续取催化剂用到
            }
            if (id == null || id.isEmpty()) {
                id = handler.getClass()
                    .getName();
            }
            handlerIds.add(id);
            // 同一个 handlerId 只存第一个实例：NEI 对同 id 的 handler 只保留一份（注册时按 id 去重）
            handlersById.putIfAbsent(id, handler);

            if (handlerClass.isEmpty()) handlerClass = handler.getClass()
                .getName();
            if (displayName == null || displayName.isEmpty()) {
                String n = NeiScanService.displayNameOf(handler);
                if (n != null && !n.isEmpty()) displayName = n;
            }
        }

        IndexModel.RecipeTypeIndex toModel() {
            IndexModel.RecipeTypeIndex m = new IndexModel.RecipeTypeIndex();
            m.typeId = typeId;
            m.displayName = displayName == null || displayName.isEmpty() ? typeId : displayName;
            m.handlerClass = handlerClass;
            m.noCatalyst = noCatalyst;
            m.handlerIds.addAll(handlerIds);
            return m;
        }
    }

    /** 当前是否在主线程（扫描触碰 NEI 与 ItemStack，必须主线程）。 */
    public static boolean isClientThread() {
        return Minecraft.getMinecraft()
            .func_152345_ab();
    }

    /** 供诊断：统计 handler 表规模（不开扫描也能在日志里看到规模）。 */
    public static String describeHandlerCounts() {
        try {
            int c = GuiCraftingRecipe.craftinghandlers == null ? 0 : GuiCraftingRecipe.craftinghandlers.size();
            int u = GuiUsageRecipe.usagehandlers == null ? 0 : GuiUsageRecipe.usagehandlers.size();
            int cat = RecipeCatalysts.getPositionedRecipeCatalystMap() == null ? 0
                : RecipeCatalysts.getPositionedRecipeCatalystMap()
                    .size();
            return "craftinghandlers=" + c + ", usagehandlers=" + u + ", catalystTypes=" + cat;
        } catch (Throwable t) {
            return "unavailable: " + t;
        }
    }

    /** 清空（无状态，仅给调用方语义完整）。 */
    public static void reset() {
        // 本类无长驻状态；统计缓存都在 common 包（VoltageResolver.invalidateCaches）
    }

    /** 供调试：把 map 的键集合转成字符串。 */
    static String keysOf(Map<String, ?> map) {
        if (map == null) return "{}";
        return new HashMap<>(map).keySet()
            .toString();
    }
}
