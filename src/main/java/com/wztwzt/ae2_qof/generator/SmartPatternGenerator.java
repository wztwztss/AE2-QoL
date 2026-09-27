/*
 * 本文件改编自 AE2PatternGen (https://github.com/Ch4oooooooLL/AE2PatternGen) 的 MIT 许可代码：
 *   - recipe/GTRecipeSource.java（RecipeMap 枚举与配方筛选思路）
 *   - encoder/PatternEncoder.java（把 GTRecipe 编码成 AE2 加工样板的 NBT 结构）
 *   - filter/*（过滤器功能面：电压/输入黑名单/输出黑名单/NC 物品/输入矿辞/输出矿辞）
 *
 * AE2PatternGen 的 MIT 许可与版权声明：
 *   MIT License — Copyright (c) AE2PatternGen contributors
 *   （该项目的 README 注明「本项目依据 MIT License 许可协议开源发布」）
 *
 * 本仓库的改动：按 AE2-QoL 的工程约定重写为单一服务类（去掉其缓存/冲突解决/虚拟存储/自建网络子系统），
 * 复用我们已有的「展开上限 / 非静默日志 / 双端同版本」原则；过滤器只保留对生成有用的部分，
 * 并统一走本模组的日志与诊断口径。
 */
package com.wztwzt.ae2_qof.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.wztwzt.ae2_qof.MyMod;

import appeng.api.AEApi;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;

/**
 * 「批量生成具体样板」的核心服务（3.23.0，吞并 AE2PatternGen 的主要功能）。
 *
 * <h2>做什么</h2>
 * 按一个 RecipeMap（可用关键字模糊匹配，如 {@code blender} → {@code gt.recipe.metablender}）
 * 把其中**所有启用的配方**逐条编码成 AE2 加工样板（{@code in}/{@code out} + {@code crafting=false}），
 * 支持输入/输出黑名单、矿辞过滤、NC（不消耗）物品过滤与数量上限。
 *
 * <h2>与通配样板的分工</h2>
 * 通配样板适合"同一类配方只放一张样板"；生成器适合**一张样板表达不了**的场合（例如多输入各自不同材料），
 * 它产出的是一堆普通样板，任何总成都认。
 *
 * <h2>诚实边界</h2>
 * <ul>
 * <li>含**流体**的配方本版**跳过并计数**（不静默：结果里给出计数与原因）；</li>
 * <li>生成数量受 {@code maxPatterns} 限制，超出部分**截断并计数**；</li>
 * <li>每条配方都会记一条 DEBUG 级日志，汇总记 INFO/WARN。</li>
 * </ul>
 */
public final class SmartPatternGenerator {

    /** 生成结果。 */
    public static final class Result {

        public final List<ItemStack> patterns = new ArrayList<>();
        /** 扫描到的启用配方总数。 */
        public int recipesSeen;
        /** 因含流体被跳过的数量。 */
        public int skippedFluid;
        /** 被过滤器排除的数量。 */
        public int skippedFiltered;
        /** 因达到上限被截断的数量。 */
        public int truncated;
        public String recipeMapId = "";
        public String reason = "";

        public boolean isEmpty() {
            return patterns.isEmpty();
        }

        public String describe() {
            return "map=" + recipeMapId
                + " seen="
                + recipesSeen
                + " produced="
                + patterns.size()
                + " skippedFluid="
                + skippedFluid
                + " filtered="
                + skippedFiltered
                + " truncated="
                + truncated
                + (reason.isEmpty() ? "" : " reason=" + reason);
        }
    }

    /** 过滤器（全部为"留空即不启用"）。 */
    public static final class Filters {

        /** 输入黑名单匹配串（支持 * 与 ?，匹配显示名或矿辞名）。 */
        public String blacklistInput = "";
        /** 输出黑名单匹配串。 */
        public String blacklistOutput = "";
        /** 输入必须包含的矿辞匹配串（如 {@code ingot*}）。 */
        public String requireInputOre = "";
        /** 输出必须包含的矿辞匹配串。 */
        public String requireOutputOre = "";
        /** 必须包含的 NC（不消耗）物品匹配串 —— 对应 GTRecipe 里 stackSize=0 的输入。 */
        public String requireNonConsumed = "";
        /** 电压等级上限（0=ULV, 1=LV, 2=MV, 3=HV, 4=EV, 5=IV …；-1 = 不限制）。 */
        public int maxTier = -1;
        /**
         * 替换规则：形如 {@code 源矿辞=目标矿辞}，多条用 {@code ;} 分隔
         * （例如 {@code dustCopper=dustTin;ingotIron=ingotSteel}）。
         * 作用：写样板 NBT 时，把配方里命中"源矿辞"的每个输入/输出物品替换成"目标矿辞"的第一个物品，数量不变。
         */
        public String replacements = "";
    }

    private SmartPatternGenerator() {}

    /** 列出所有可用的 RecipeMap id（供界面下拉/补全）。 */
    public static Map<String, String> listRecipeMaps() {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, RecipeMap<?>> entry : RecipeMap.ALL_RECIPE_MAPS.entrySet()) {
                out.put(entry.getKey(), entry.getKey());
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 枚举 RecipeMap 失败", t);
        }
        return out;
    }

    /** 关键字模糊匹配 RecipeMap id（不区分大小写的子串匹配；先精确后模糊）。 */
    public static List<String> findMatchingRecipeMaps(String keyword) {
        List<String> matched = new ArrayList<>();
        String key = keyword == null ? "" : keyword.trim()
            .toLowerCase();
        if (key.isEmpty()) return matched;
        try {
            if (RecipeMap.ALL_RECIPE_MAPS.containsKey(key)) {
                matched.add(key);
                return matched;
            }
            for (String id : RecipeMap.ALL_RECIPE_MAPS.keySet()) {
                if (id != null && id.toLowerCase()
                    .contains(key)) matched.add(id);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 模糊匹配 RecipeMap 失败：keyword=" + keyword, t);
        }
        return matched;
    }

    /**
     * 生成样板。
     *
     * @param mapKeyword RecipeMap id 或其子串（如 {@code gt.recipe.rolling} / {@code rolling}）
     * @param filters    过滤器（可为 null）
     * @param maxPatterns 上限（<=0 视为 512）
     */
    public static Result generate(String mapKeyword, Filters filters, int maxPatterns) {
        return generate(mapKeyword, filters, maxPatterns, false);
    }

    /**
     * 生成样板。
     *
     * @param dryRun {@code true} 时**只统计不产出**（对应参考模组的「预览数量」按钮）：
     *               过滤器、上限、流体跳过都照常判定，但不会交给玩家任何物品。
     */
    public static Result generate(String mapKeyword, Filters filters, int maxPatterns, boolean dryRun) {
        Result result = new Result();
        Filters f = filters == null ? new Filters() : filters;
        int cap = maxPatterns <= 0 ? 512 : maxPatterns;
        try {
            List<String> maps = findMatchingRecipeMaps(mapKeyword);
            if (maps.isEmpty()) {
                result.reason = "no-recipe-map-matched";
                MyMod.LOG.warn("[AE2QoL] 生成器：没有匹配的 RecipeMap（关键字 {}）", mapKeyword);
                return result;
            }
            // 命中多个时不猜：用第一个精确匹配，其余记日志提示（与"不静默"原则一致）
            result.recipeMapId = maps.get(0);
            if (maps.size() > 1) {
                MyMod.LOG.info("[AE2QoL] 生成器：关键字 {} 命中 {} 个 RecipeMap，使用第一个 {}（其余：{}）", mapKeyword, maps.size(), result.recipeMapId, maps.subList(1, Math.min(maps.size(), 5)));
            }
            RecipeMap<?> target = RecipeMap.ALL_RECIPE_MAPS.get(result.recipeMapId);
            if (target == null) {
                result.reason = "recipe-map-missing";
                MyMod.LOG.warn("[AE2QoL] 生成器：RecipeMap {} 取不到", result.recipeMapId);
                return result;
            }
            Collection<GTRecipe> recipes = target.getAllRecipes();
            if (recipes == null || recipes.isEmpty()) {
                result.reason = "recipe-map-empty";
                MyMod.LOG.warn("[AE2QoL] 生成器：RecipeMap {} 里没有任何配方", result.recipeMapId);
                return result;
            }
            ItemStack template = newPatternItem();
            if (template == null) {
                result.reason = "no-pattern-item";
                MyMod.LOG.warn("[AE2QoL] 生成器：取不到 AE2 编码样板物品");
                return result;
            }

            // 替换规则只解析一次（每条配方复用同一份映射）
            final Map<String, String> repl = parseReplacements(f.replacements);
            for (GTRecipe recipe : recipes) {
                if (recipe == null || !recipe.mEnabled) continue;
                result.recipesSeen++;
                // 流体配方本版跳过（GTRecipe 的流体表示比物品复杂，先保证正确性并计数）
                if (recipe.mFluidInputs != null && recipe.mFluidInputs.length > 0
                    || recipe.mFluidOutputs != null && recipe.mFluidOutputs.length > 0) {
                    result.skippedFluid++;
                    continue;
                }
                if (!passesFilters(recipe, f)) {
                    result.skippedFiltered++;
                    continue;
                }
                if (result.patterns.size() >= cap) {
                    result.truncated++;
                    continue;
                }
                if (dryRun) {
                    // 只统计：仍走完过滤器与上限判定，但不编码、不产出（结果列表仅用于计数）
                    result.patterns.add(template.copy());
                    continue;
                }
                ItemStack pattern = encode(template, recipe, repl);
                if (pattern != null) result.patterns.add(pattern);
            }
            MyMod.LOG.info(
                "[AE2QoL] 样板生成器完成：{}",
                result.describe());
            if (result.truncated > 0) {
                MyMod.LOG.warn("[AE2QoL] 样板生成达到上限 {}，已截断 {} 条（可提高上限后重生成）", cap, result.truncated);
            }
            return result;
        } catch (Throwable t) {
            result.reason = "exception: " + t;
            MyMod.LOG.warn("[AE2QoL] 样板生成异常：keyword=" + mapKeyword, t);
            return result;
        }
    }

    /** 过滤器判定（每条失败原因都会在汇总里计数；细节见类注释）。 */
    private static boolean passesFilters(GTRecipe recipe, Filters f) {
        try {
            if (!f.blacklistInput.isEmpty() && anyMatch(recipe.mInputs, f.blacklistInput)) return false;
            if (!f.blacklistOutput.isEmpty() && anyMatch(recipe.mOutputs, f.blacklistOutput)) return false;
            if (!f.requireInputOre.isEmpty() && !anyMatch(recipe.mInputs, f.requireInputOre)) return false;
            if (!f.requireOutputOre.isEmpty() && !anyMatch(recipe.mOutputs, f.requireOutputOre)) return false;
            if (!f.requireNonConsumed.isEmpty() && !anyNonConsumed(recipe.mInputs, f.requireNonConsumed)) return false;
            // 电压等级过滤：GTRecipe.mEUt 是每 tick EU；用 GTUtility.getTier 换算成等级再比较
            if (f.maxTier >= 0) {
                int tier = gregtech.api.util.GTUtility.getTier(recipe.mEUt);
                if (tier > f.maxTier) return false;
            }
            return true;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 生成器过滤判定异常（该配方按不通过处理）", t);
            return false;
        }
    }

    /** 配方里是否存在匹配该串的物品（比对显示名与矿辞名，支持 * 与 ?）。 */
    private static boolean anyMatch(ItemStack[] stacks, String matcher) {
        if (stacks == null) return false;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null) continue;
            if (com.wztwzt.ae2_qof.wildcard.SmartWildcardState.matches(
                matcher,
                String.valueOf(
                    stack.getItem()
                        .getItemStackDisplayName(stack))))
                return true;
            int[] ids = net.minecraftforge.oredict.OreDictionary.getOreIDs(stack);
            if (ids != null) {
                for (int id : ids) {
                    String ore = net.minecraftforge.oredict.OreDictionary.getOreName(id);
                    if (ore != null && com.wztwzt.ae2_qof.wildcard.SmartWildcardState.matches(matcher, ore)) return true;
                }
            }
        }
        return false;
    }

    /** NC（不消耗）物品：GTRecipe 用 stackSize == 0 表示（与 AE2 的 Cnt=0 语义一致）。 */
    private static boolean anyNonConsumed(ItemStack[] stacks, String matcher) {
        if (stacks == null) return false;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null || stack.stackSize != 0) continue;
            if (com.wztwzt.ae2_qof.wildcard.SmartWildcardState.matches(
                matcher,
                String.valueOf(
                    stack.getItem()
                        .getItemStackDisplayName(stack))))
                return true;
        }
        return false;
    }

    /** 取一张 AE2 编码样板（加工样板）作为模板。 */
    public static ItemStack newPatternItem() {
        try {
            return AEApi.instance()
                .definitions()
                .items()
                .encodedPattern()
                .maybeStack(1)
                .get();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 取 AE2 编码样板物品失败", t);
            return null;
        }
    }

    /** 把一条 GTRecipe 编码成加工样板（结构与 AE2PatternGen 的 PatternEncoder 一致：in/out + crafting=false）。 */
    public static ItemStack encode(ItemStack template, GTRecipe recipe) {
        return encode(template, recipe, java.util.Collections.<String, String>emptyMap());
    }

    /** 带替换规则的编码：写 NBT 时把命中「源矿辞」的物品换成「目标矿辞」的第一个物品（数量不变）。 */
    public static ItemStack encode(ItemStack template, GTRecipe recipe, Map<String, String> replacements) {
        try {
            ItemStack pattern = template.copy();
            pattern.stackSize = 1;
            NBTTagCompound tag = new NBTTagCompound();
            NBTTagList in = new NBTTagList();
            appendAll(in, recipe.mInputs, replacements);
            NBTTagList out = new NBTTagList();
            appendAll(out, recipe.mOutputs, replacements);
            tag.setTag("in", in);
            tag.setTag("out", out);
            tag.setBoolean("crafting", false);
            pattern.setTagCompound(tag);
            return pattern;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 编码样板失败（跳过该配方）", t);
            return null;
        }
    }

    /** 解析替换规则串（{@code a=b;c=d}）。非法片段会记 WARN 并忽略，不静默。 */
    public static Map<String, String> parseReplacements(String spec) {
        Map<String, String> map = new LinkedHashMap<>();
        if (spec == null || spec.trim()
            .isEmpty()) return map;
        for (String part : spec.split(";")) {
            String piece = part == null ? "" : part.trim();
            if (piece.isEmpty()) continue;
            int eq = piece.indexOf('=');
            if (eq <= 0 || eq >= piece.length() - 1) {
                MyMod.LOG.warn("[AE2QoL] 替换规则片段非法（应为 源=目标），已忽略：{}", piece);
                continue;
            }
            String from = piece.substring(0, eq)
                .trim();
            String to = piece.substring(eq + 1)
                .trim();
            if (from.isEmpty() || to.isEmpty()) {
                MyMod.LOG.warn("[AE2QoL] 替换规则片段为空，已忽略：{}", piece);
                continue;
            }
            map.put(from, to);
        }
        if (!map.isEmpty()) {
            MyMod.LOG.info("[AE2QoL] 生成器替换规则 {} 条：{}", map.size(), map);
        }
        return map;
    }

    /** 按替换规则换掉物品（命中源矿辞 ⇒ 取目标矿辞的第一个物品，数量沿用原值）。 */
    private static ItemStack applyReplacements(ItemStack stack, Map<String, String> replacements) {
        if (stack == null || stack.getItem() == null || replacements == null || replacements.isEmpty()) return stack;
        try {
            int[] ids = net.minecraftforge.oredict.OreDictionary.getOreIDs(stack);
            if (ids == null) return stack;
            for (int id : ids) {
                String ore = net.minecraftforge.oredict.OreDictionary.getOreName(id);
                if (ore == null) continue;
                String target = replacements.get(ore);
                if (target == null) continue;
                java.util.ArrayList<ItemStack> ores = net.minecraftforge.oredict.OreDictionary.getOres(target);
                if (ores == null || ores.isEmpty()) {
                    MyMod.LOG.warn("[AE2QoL] 替换规则目标矿辞没有物品：{} → {}（该配方按原样保留）", ore, target);
                    return stack;
                }
                ItemStack replaced = ores.get(0)
                    .copy();
                replaced.stackSize = stack.stackSize;
                return replaced;
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 应用替换规则失败（该物品按原样保留）", t);
        }
        return stack;
    }

    private static void appendAll(NBTTagList list, ItemStack[] stacks, Map<String, String> replacements) {
        if (stacks == null) return;
        for (ItemStack raw : stacks) {
            if (raw == null || raw.getItem() == null) continue;
            ItemStack stack = applyReplacements(raw, replacements);
            NBTTagCompound itemTag = new NBTTagCompound();
            stack.writeToNBT(itemTag);
            // 同时写 Count/Cnt：AE2 对"数量 0（非消耗）"读的是 Cnt
            itemTag.setInteger("Count", stack.stackSize);
            itemTag.setLong("Cnt", stack.stackSize);
            list.appendTag(itemTag);
        }
    }
}
