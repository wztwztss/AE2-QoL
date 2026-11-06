package com.wztwzt.ae2_qof.machindex.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.wztwzt.ae2_qof.MyMod;

import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.common.blocks.ItemMachines;

/**
 * 电压解析器（只依赖通用类型，不引用 NEI/BQ）。
 *
 * <h2>口径（用户 2026-10-27 拍板 Q2，4.5.0-fix2 修正实现）</h2>
 * 多方块按「<b>合成该机器所需的最低电压</b>」算。这里有一个 4.5.0 实测踩到的关键教训：
 *
 * <p>
 * <b>GT 的高压机器几乎不用原版工作台</b>。装配机（{@code RecipeMaps.assemblerRecipes}）、
 * 装配线（{@code assemblylineVisualRecipes}）、电路装配机等才是主途径。
 * 4.5.0 只反查了原版 {@link CraftingManager}，于是「枢纽卫星工厂 MkII」（真值 UXV，
 * 走 {@code assemblerRecipes} + {@code TierEU.RECIPE_UXV}）反查失败 ⇒ 退回配方表最低电压
 * ⇒ 被压成 LV。因此本版<b>把 GT 装配类配方表也纳入反查</b>。
 *
 * <h2>为什么不再用「RecipeMap 最小 mEUt」给万能工厂定档</h2>
 * 「枢纽卫星工厂」这类机器一台挂几十张配方表（{@code getFactoryRecipeMaps()}），
 * 其 {@code getRecipeMap()} 由原型 {@code machineMode} 决定——而原型恒为 0。
 * 用「某张表的最小 mEUt」立即被拉到 LV。所以多表机器的 recipe_voltage 直接判为不可信。
 */
public final class VoltageResolver {

    private VoltageResolver() {}

    /** 电路矿辞 → 电压档（自建映射，不新增 bartworks 依赖）。 */
    private static final Map<String, Integer> CIRCUIT_TIERS = new HashMap<>();

    static {
        CIRCUIT_TIERS.put("circuitPrimitive", 0);
        CIRCUIT_TIERS.put("circuitBasic", 1);
        CIRCUIT_TIERS.put("circuitGood", 2);
        CIRCUIT_TIERS.put("circuitAdvanced", 3);
        CIRCUIT_TIERS.put("circuitData", 4);
        CIRCUIT_TIERS.put("circuitElite", 5);
        CIRCUIT_TIERS.put("circuitMaster", 6);
        CIRCUIT_TIERS.put("circuitUltimate", 7);
        CIRCUIT_TIERS.put("circuitSuperconductor", 8);
        CIRCUIT_TIERS.put("circuitInfinite", 9);
        CIRCUIT_TIERS.put("circuitBio", 10);
        CIRCUIT_TIERS.put("circuitNano", 11);
        CIRCUIT_TIERS.put("circuitOptical", 11);
        CIRCUIT_TIERS.put("circuitPiko", 12);
        CIRCUIT_TIERS.put("circuitExotic", 12);
        CIRCUIT_TIERS.put("circuitQuantum", 13);
        CIRCUIT_TIERS.put("circuitCosmic", 13);
        CIRCUIT_TIERS.put("circuitTranscendent", 14);
    }

    /** 原版工作台索引：物品 → 配方（惰性建一次）。 */
    private static Map<Item, List<IRecipe>> vanillaRecipeIndex;

    /**
     * GT 装配类配方索引：产出物品 → 档位。
     *
     * <p>
     * 只索引装配类表（装配机/装配线/电路装配机/部件装配机），不对全部 RecipeMap 建索引：
     * GT 有上千张表、几十万条配方，全量索引会让进世界明显卡顿；而装配类恰好是「造机器」的主途径。
     */
    private static Map<Item, List<GtTier>> gtRecipeIndex;

    /**
     * GT 装配类配方索引里的一条记录：**必须带 meta**。
     *
     * <p>
     * 血泪教训：所有 GT 机器方块都是**同一个 Item**（{@code gregtech:gt.blockmachines}），
     * 只有 meta 不同。早先只按 Item 建键、不带 meta ⇒ 查一台机器会命中全部上万条机器配方，
     * 再取最低档 ⇒ **571 台多方块全部被标成 ULV(0)**。
     * 因此这里必须 (meta, tier) 成对存，查询时精确比对 meta。
     */
    private static final class GtTier {

        final int meta;
        final int tier;

        GtTier(int meta, int tier) {
            this.meta = meta;
            this.tier = tier;
        }
    }

    /** RecipeMap 最小电压档缓存。 */
    private static final Map<String, Integer> RECIPE_MAP_MIN_TIER = new HashMap<>();

    // ===================== 对外入口 =====================

    /** 解析一台机器的电压，写进 {@code entry}。 */
    public static void resolve(ItemStack stack, IndexModel.MachineEntry entry) {
        if (stack == null) {
            entry.mainTier = IndexModel.TIER_UNKNOWN;
            entry.voltageSource = "unknown";
            entry.note = appendNote(entry.note, "催化剂为空");
            return;
        }

        MetaTileEntity mte = resolveMte(stack);
        boolean multiblock = mte instanceof MTEMultiBlockBase;
        entry.isMultiblock = multiblock;

        // ---- 1) 单方块分档机器：直接读 mTier（最可靠，不看配方）----
        if (mte instanceof MTETieredMachineBlock) {
            int tier = ((MTETieredMachineBlock) mte).mTier;
            entry.mainTier = tier;
            entry.allTiers.add(tier);
            entry.voltageSource = "machine_tier";
            return;
        }

        // ---- 2) 反查合成配方：工作台 + GT 装配类（多方块主口径，用户 Q2）----
        int craftingTier = resolveByCrafting(stack);
        if (craftingTier != IndexModel.TIER_UNKNOWN) {
            entry.mainTier = craftingTier;
            entry.allTiers.add(craftingTier);
            entry.voltageSource = "crafting_recipe";
            return;
        }

        // ---- 3) 非 Tiered 的 GT 机器：maxEUInput 兜底 ----
        long maxEu = safeMaxEuInput(mte);
        if (maxEu > 0) {
            int tier = GTUtility.getTier(maxEu);
            if (tier >= 0 && tier <= 14) {
                entry.mainTier = tier;
                entry.allTiers.add(tier);
                entry.voltageSource = "machine_tier";
                return;
            }
        }

        // ---- 4) recipe_voltage：仅单表机器可信 ----
        int recipeTier = safeRecipeVoltage(mte);
        if (recipeTier != IndexModel.TIER_UNKNOWN) {
            entry.mainTier = recipeTier;
            entry.allTiers.add(recipeTier);
            entry.voltageSource = "recipe_voltage";
            entry.note = appendNote(entry.note, "无合成表，退回配方电压口径");
            return;
        }

        entry.mainTier = IndexModel.TIER_UNKNOWN;
        entry.voltageSource = "unknown";
        entry.note = appendNote(entry.note, multiblock ? "多方块：工作台/装配类配方均未命中，且无可信配方表" : "非 GT 机器 / 无合成表");
    }

    /** ItemStack → GT 机器原型；不是 GT 机器返回 null。 */
    public static MetaTileEntity resolveMte(ItemStack stack) {
        try {
            Object mte = ItemMachines.getMetaTileEntity(stack);
            return mte instanceof MetaTileEntity ? (MetaTileEntity) mte : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String tierName(int tier) {
        if (tier < 0 || tier >= GTValues.VN.length) return "?";
        return GTValues.VN[tier];
    }

    public static int sortKey(int tier) {
        return tier == IndexModel.TIER_UNKNOWN ? Integer.MAX_VALUE / 2 : tier;
    }

    // ===================== 合成表反查（工作台 + GT 装配类） =====================

    /**
     * 反查「产出该机器堆」的配方，返回<b>最容易造出它</b>的电压档。
     *
     * <p>
     * 同一台机器可能有多个配方（工作台 / 装配机 / 装配线 / 廉价版），取<b>最低档</b>才是
     * 「最低可解锁电压」，与用户 Q2「合成所需的最低电压」一致。
     */
    public static int resolveByCrafting(ItemStack target) {
        if (!IndexConfig.enableCraftingInference || target == null) return IndexModel.TIER_UNKNOWN;

        int best = IndexModel.TIER_UNKNOWN;

        // ① 原版工作台
        Map<Item, List<IRecipe>> vIdx = ensureVanillaIndex();
        List<IRecipe> candidates = vIdx.get(target.getItem());
        if (candidates != null) {
            for (IRecipe recipe : candidates) {
                ItemStack out = safeOutput(recipe);
                if (!sameStack(out, target)) continue;
                int tier = recipeIngredientTier(extractInputs(recipe), 0);
                if (tier == IndexModel.TIER_UNKNOWN) continue;
                if (best == IndexModel.TIER_UNKNOWN || tier < best) best = tier;
            }
        }

        // ② GT 装配类配方表（高压机器的主途径；4.5.0 漏了这一整类）
        Map<Item, List<GtTier>> gIdx = ensureGtIndex();
        List<GtTier> gtCandidates = gIdx.get(target.getItem());
        if (gtCandidates != null) {
            for (GtTier c : gtCandidates) {
                // **必须比对 meta**：同一个 Item 的不同 meta 是不同机器，混在一起会互相污染电压
                if (c == null || c.tier == IndexModel.TIER_UNKNOWN) continue;
                if (c.meta != target.getItemDamage()) continue;
                if (best == IndexModel.TIER_UNKNOWN || c.tier < best) best = c.tier;
            }
        }

        return best;
    }

    private static ItemStack safeOutput(IRecipe recipe) {
        try {
            return recipe.getRecipeOutput();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 配方成分的最高档（= 凑齐成分所需的最低电压）。 */
    private static int recipeIngredientTier(List<Object> inputs, int depth) {
        int max = IndexModel.TIER_UNKNOWN;
        for (Object raw : inputs) {
            int t = ingredientTier(raw, depth);
            if (t != IndexModel.TIER_UNKNOWN && t > max) max = t;
        }
        return max;
    }

    private static int ingredientTier(Object raw, int depth) {
        if (raw == null) return IndexModel.TIER_UNKNOWN;

        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            if (list.isEmpty()) return IndexModel.TIER_UNKNOWN;
            Object first = list.get(0);
            if (first instanceof ItemStack) {
                ItemStack st = (ItemStack) first;
                int byName = circuitTierOf(st);
                if (byName != IndexModel.TIER_UNKNOWN) return byName;
                return stackTier(st, depth);
            }
            return IndexModel.TIER_UNKNOWN;
        }
        if (raw instanceof ItemStack) {
            ItemStack st = (ItemStack) raw;
            int byName = circuitTierOf(st);
            if (byName != IndexModel.TIER_UNKNOWN) return byName;
            return stackTier(st, depth);
        }
        return IndexModel.TIER_UNKNOWN;
    }

    private static int stackTier(ItemStack st, int depth) {
        if (st == null) return IndexModel.TIER_UNKNOWN;
        MetaTileEntity sub = resolveMte(st);
        if (sub instanceof MTETieredMachineBlock) {
            return ((MTETieredMachineBlock) sub).mTier;
        }
        if (sub != null && depth < 1) return IndexModel.TIER_UNKNOWN;
        return tierOfMetaBaseItem(st);
    }

    /**
     * 电池/充能件 / 机壳的档位。
     *
     * <p>
     * 用反射而不是直接调 {@code MetaBaseItem.getTier}：该类实现 {@code ic2.api.item.ISpecialElectricItem}，
     * 而本项目从不依赖 IC2 —— javac 解析成员签名时会读 IC2 类文件，直接编译期报
     * {@code cannot access ISpecialElectricItem}。
     */
    private static int tierOfMetaBaseItem(ItemStack st) {
        try {
            Object item = st.getItem();
            if (!(item instanceof gregtech.api.items.MetaBaseItem)) return IndexModel.TIER_UNKNOWN;
            java.lang.reflect.Method m = item.getClass()
                .getMethod("getTier", ItemStack.class);
            Object raw = m.invoke(item, st);
            if (!(raw instanceof Integer)) return IndexModel.TIER_UNKNOWN;
            int t = (Integer) raw;
            return t >= 0 && t <= 14 ? t : IndexModel.TIER_UNKNOWN;
        } catch (Throwable t) {
            return IndexModel.TIER_UNKNOWN;
        }
    }

    private static int circuitTierOf(ItemStack st) {
        try {
            int[] ids = OreDictionary.getOreIDs(st);
            int best = IndexModel.TIER_UNKNOWN;
            for (int id : ids) {
                Integer t = CIRCUIT_TIERS.get(OreDictionary.getOreName(id));
                if (t != null && (best == IndexModel.TIER_UNKNOWN || t < best)) best = t;
            }
            return best;
        } catch (Throwable t) {
            return IndexModel.TIER_UNKNOWN;
        }
    }

    private static List<Object> extractInputs(IRecipe recipe) {
        List<Object> out = new ArrayList<>();
        try {
            if (recipe instanceof ShapedOreRecipe) {
                Collections.addAll(out, ((ShapedOreRecipe) recipe).getInput());
            } else if (recipe instanceof ShapelessOreRecipe) {
                out.addAll(((ShapelessOreRecipe) recipe).getInput());
            } else if (recipe instanceof ShapedRecipes) {
                Collections.addAll(out, ((ShapedRecipes) recipe).recipeItems);
            } else if (recipe instanceof ShapelessRecipes) {
                out.addAll(((ShapelessRecipes) recipe).recipeItems);
            }
        } catch (Throwable t) {
            // 第三方 IRecipe 可能覆写 getInput() 抛异常：按「无成分」处理
        }
        return out;
    }

    /** 原版工作台索引（惰性一次）。 */
    private static synchronized Map<Item, List<IRecipe>> ensureVanillaIndex() {
        if (vanillaRecipeIndex != null) return vanillaRecipeIndex;
        long start = System.currentTimeMillis();
        Map<Item, List<IRecipe>> idx = new HashMap<>();
        int total = 0;
        try {
            @SuppressWarnings("unchecked")
            List<IRecipe> all = CraftingManager.getInstance()
                .getRecipeList();
            total = all.size();
            for (IRecipe r : all) {
                ItemStack out = safeOutput(r);
                if (out == null || out.getItem() == null) continue;
                idx.computeIfAbsent(out.getItem(), k -> new ArrayList<>())
                    .add(r);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 工作台配方索引构建失败：" + t);
        }
        vanillaRecipeIndex = idx;
        MyMod.LOG.info(
            "[AE2QoL] machindex 工作台配方索引：" + idx
                .size() + " 物品 / " + total + " 配方，耗时 " + (System.currentTimeMillis() - start) + "ms");
        return vanillaRecipeIndex;
    }

    /**
     * GT 装配类配方索引（惰性一次）。
     *
     * <p>
     * 档位取<b>配方成分</b>与<b>配方 mEUt</b> 两者的较高值：
     * 成分里的 UXV 电路/机壳代表「你得先有这个电压才能造」；
     * 配方 mEUt 代表「运行这一步至少要这个电」。取较高者更接近「解锁门槛」。
     */
    private static synchronized Map<Item, List<GtTier>> ensureGtIndex() {
        if (gtRecipeIndex != null) return gtRecipeIndex;
        long start = System.currentTimeMillis();
        Map<Item, List<GtTier>> idx = new HashMap<>();
        int maps = 0;
        int recipes = 0;

        // 只索引「造机器」的表；其余 GT 表留给 recipe_voltage 兜底。
        // 名字必须与 GT jar 字节码里的 unlocalizedName **逐字一致**（4.5.0-fix2 实测纠正）：
        // 装配机 = gt.recipe.assembler
        // 装配线 = gt.recipe.fakeAssemblylineProcess ← 不是 gt.recipe.assemblyline
        // 电路装配 = gt.recipe.circuitassembler
        // 芯片组装 = gt.recipe.nanochip.assemblymatrix
        // 「partsassembler」在 GT 里**不存在**，写上只会静默跳过（本次踩到）。
        String[] wanted = { "gt.recipe.assembler", "gt.recipe.fakeAssemblylineProcess", "gt.recipe.circuitassembler",
            "gt.recipe.nanochip.assemblymatrix" };

        for (String mapName : wanted) {
            try {
                RecipeMap<?> map = RecipeMap.ALL_RECIPE_MAPS.get(mapName);
                if (map == null) {
                    // 名字写错会静默跳过（4.5.0 就踩过「gt.recipe.assemblyline」不存在），
                    // 因此缺表必须 WARN——否则「电压推断全失败」在日志里毫无痕迹。
                    MyMod.LOG.warn("[AE2QoL] machindex GT 配方表未找到（名字可能写错）：" + mapName);
                    continue;
                }
                maps++;
                Collection<GTRecipe> all = map.getAllRecipes();
                MyMod.LOG.info("[AE2QoL] machindex GT 表 " + mapName + "：配方 " + all.size() + " 条");
                for (GTRecipe r : all) {
                    if (r == null || r.mOutputs == null) continue;
                    int byIngredients = gtRecipeIngredientTier(r);
                    int byEut = r.mEUt > 0 ? GTUtility.getTier(Math.max(1L, r.mEUt)) : IndexModel.TIER_UNKNOWN;
                    int tier = Math.max(byEut, byIngredients);
                    if (tier < 0 || tier > 14) tier = IndexModel.TIER_UNKNOWN;
                    if (tier == IndexModel.TIER_UNKNOWN) continue;
                    recipes++;
                    for (ItemStack out : r.mOutputs) {
                        if (out == null || out.getItem() == null) continue;
                        idx.computeIfAbsent(out.getItem(), k -> new ArrayList<>())
                            .add(new GtTier(out.getItemDamage(), tier));
                    }
                }
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] machindex 读取 GT 配方表失败：" + mapName + " → " + t);
            }
        }

        gtRecipeIndex = idx;
        MyMod.LOG.info(
            "[AE2QoL] machindex GT 装配类配方索引：" + maps
                + " 表 / "
                + recipes
                + " 配方 / "
                + idx.size()
                + " 输出物品，耗时 "
                + (System.currentTimeMillis() - start)
                + "ms");
        return gtRecipeIndex;
    }

    /** GT 配方成分最高档（GTRecipe.mInputs 是 ItemStack[]）。 */
    private static int gtRecipeIngredientTier(GTRecipe r) {
        int max = IndexModel.TIER_UNKNOWN;
        if (r.mInputs == null) return max;
        for (ItemStack st : r.mInputs) {
            if (st == null) continue;
            int byName = circuitTierOf(st);
            int t = byName != IndexModel.TIER_UNKNOWN ? byName : stackTier(st, 0);
            if (t != IndexModel.TIER_UNKNOWN && t > max) max = t;
        }
        return max;
    }

    // ===================== recipe_voltage 兜底 =====================

    private static int safeRecipeVoltage(MetaTileEntity mte) {
        if (!(mte instanceof MTEMultiBlockBase)) return IndexModel.TIER_UNKNOWN;
        try {
            MTEMultiBlockBase mb = (MTEMultiBlockBase) mte;
            // 多表机器（万能工厂）：getRecipeMap() 受原型 machineMode 影响，该值不可信
            if (hasMultipleRecipeMaps(mb)) return IndexModel.TIER_UNKNOWN;
            RecipeMap<?> map = mb.getRecipeMap();
            return map == null ? IndexModel.TIER_UNKNOWN : minTierOf(map);
        } catch (Throwable t) {
            return IndexModel.TIER_UNKNOWN;
        }
    }

    /** 该多方块是否挂多张配方表（GTNG 万能工厂之类）。 */
    private static boolean hasMultipleRecipeMaps(MTEMultiBlockBase mb) {
        try {
            java.lang.reflect.Method m = mb.getClass()
                .getMethod("getAvailableRecipeMaps");
            Object raw = m.invoke(mb);
            if (raw instanceof Collection) return ((Collection<?>) raw).size() > 1;
        } catch (Throwable ignored) {
            // 没有该方法 = 单一配方表机器
        }
        return false;
    }

    /** RecipeMap 全部配方最小 mEUt → 档位（带缓存）。 */
    public static int minTierOf(RecipeMap<?> map) {
        if (map == null) return IndexModel.TIER_UNKNOWN;
        String key = map.unlocalizedName;
        synchronized (RECIPE_MAP_MIN_TIER) {
            Integer cached = RECIPE_MAP_MIN_TIER.get(key);
            if (cached != null) return cached;
        }
        int minEu = Integer.MAX_VALUE;
        try {
            Collection<GTRecipe> recipes = map.getAllRecipes();
            for (GTRecipe r : recipes) {
                if (r == null || r.mEUt <= 0) continue;
                if (r.mEUt < minEu) minEu = r.mEUt;
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 读取配方表失败：" + key + " → " + t);
        }
        int tier = minEu == Integer.MAX_VALUE ? IndexModel.TIER_UNKNOWN : GTUtility.getTier(minEu);
        if (tier > 14) tier = IndexModel.TIER_UNKNOWN;
        synchronized (RECIPE_MAP_MIN_TIER) {
            RECIPE_MAP_MIN_TIER.put(key, tier);
        }
        return tier;
    }

    /** 清空缓存（rebuild 时调用）。 */
    public static synchronized void invalidateCaches() {
        vanillaRecipeIndex = null;
        gtRecipeIndex = null;
        synchronized (RECIPE_MAP_MIN_TIER) {
            RECIPE_MAP_MIN_TIER.clear();
        }
    }

    // ===================== 工具 =====================

    private static long safeMaxEuInput(MetaTileEntity mte) {
        if (mte == null) return 0;
        try {
            return mte.maxEUInput();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean sameStack(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() != b.getItem()) return false;
        return a.getItemDamage() == b.getItemDamage();
    }

    public static String appendNote(String base, String extra) {
        if (base == null || base.isEmpty()) return extra;
        return base + "；" + extra;
    }

    public static boolean isGtMachine(ItemStack stack) {
        return resolveMte(stack) != null;
    }

    public static String displayNameOf(ItemStack stack) {
        if (stack == null) return "?";
        try {
            String n = stack.getDisplayName();
            return n == null || n.isEmpty() ? "?" : n;
        } catch (Throwable t) {
            return "?";
        }
    }
}
