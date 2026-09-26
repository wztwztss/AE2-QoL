package com.wztwzt.ae2_qof.wildcard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.oredict.OreDictionary;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.MyMod;

/**
 * 智能通配样板的展开器（3.22.0 M1）。
 *
 * <h2>它到底做什么</h2>
 * 把“一张带规则的通配样板”展开成 **N 张各自合法的普通 AE2 加工样板**：逐候选克隆模板物品、
 * 改写原生 {@code in}/{@code out} NBT、剥掉本模组的规则子树。AE2 与 GT 机器**永远只看到普通样板**，
 * 这就是参考实现能被所有总成接受、且机器认账的原因（取证见
 * {@code docs/research/wildcardpattern-forensics.md}）。
 *
 * <h2>材料配对算法（本模组实现）</h2>
 * 模板形如 {@code 1×ingotIron -> 1×plateIron}，规则形如 {@code #0 ore:ingot*}：
 * <ol>
 * <li>从规则匹配到的矿辞名（如 {@code ingotIron}/{@code ingotCopper}/…）里取出**材料名**（前缀之后的部分）；</li>
 * <li>输出侧前缀由模板输出的矿辞名（{@code plateIron}）去掉模板材料名（{@code Iron}）得到（{@code plate}）；</li>
 * <li>对每个材料 M：输入取 {@code ingotM} 的候选物品、输出取 {@code plateM} 的候选物品；
 * **对侧矿辞不存在就跳过该材料**（并计数，供界面显示“因对侧缺失跳过 N 个”）。</li>
 * </ol>
 * 多规则时取各规则材料集的**交集**（与参考实现 {@code input.retainAll(output)} 同思路）。
 *
 * <h2>与参考实现的三处刻意差异</h2>
 * <ul>
 * <li><b>有上限</b>：{@link Config#smartWildcardExpandCap}（默认 512）——参考实现在服务端网络钩子主路径上
 * 无界展开，是明确的卡服点；本实现超限**截断并记 WARN**，绝不静默；</li>
 * <li><b>有缓存</b>：按「模板+规则+上限」的 NBT 快照做 LRU，避免每次重建索引都重算；</li>
 * <li><b>去重优先 gregtech</b>：同一矿辞多个来源时默认保留 gregtech 物品（与参考实现一致，但可预测）。</li>
 * </ul>
 */
public final class SmartWildcardExpander {

    /** 展开结果（不可变）。 */
    public static final class Result {

        public final List<ItemStack> patterns;
        public final int matchedCandidates;
        public final int skippedMissingCounterpart;
        public final boolean truncated;
        /** 未产生任何结果时的原因（用于界面/日志，绝不静默）。 */
        public final String reason;

        Result(List<ItemStack> patterns, int matchedCandidates, int skippedMissingCounterpart, boolean truncated,
            String reason) {
            this.patterns = Collections.unmodifiableList(patterns);
            this.matchedCandidates = matchedCandidates;
            this.skippedMissingCounterpart = skippedMissingCounterpart;
            this.truncated = truncated;
            this.reason = reason;
        }

        public boolean isEmpty() {
            return patterns.isEmpty();
        }

        public String describe() {
            return "produced=" + patterns.size()
                + " matched="
                + matchedCandidates
                + " skippedMissingCounterpart="
                + skippedMissingCounterpart
                + (truncated ? " TRUNCATED" : "")
                + (reason == null ? "" : " reason=" + reason);
        }
    }

    /** LRU 缓存：key = 模板+规则+上限的 NBT 快照。容量刻意小（样板数量远小于此）。 */
    private static final int CACHE_CAPACITY = 64;

    private static final Map<String, Result> CACHE = Collections
        .synchronizedMap(new LinkedHashMap<String, Result>(CACHE_CAPACITY, 0.75F, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) {
                return size() > CACHE_CAPACITY;
            }
        });

    /** 已经打过日志的 key（避免热路径刷屏；上限后清空重来）。 */
    private static final Set<String> LOGGED = Collections.synchronizedSet(new LinkedHashSet<String>());

    private SmartWildcardExpander() {}

    /**
     * 展开一张通配样板。
     *
     * @param wildcard 带 {@link SmartWildcardState} 数据的样板物品
     * @param world    用于解码模板（可为 null）
     * @return 展开结果（永不返回 null；无结果时 {@link Result#reason} 说明原因）
     */
    public static Result expand(ItemStack wildcard, World world) {
        if (wildcard == null || !SmartWildcardState.isSmartWildcard(wildcard)) {
            return new Result(new ArrayList<>(), 0, 0, false, "not-a-smart-wildcard");
        }
        SmartWildcardState state = SmartWildcardState.of(wildcard);
        if (state == null || !state.isConfigured()) {
            return new Result(new ArrayList<>(), 0, 0, false, "no-rules");
        }

        final int cap = Math.max(1, Config.smartWildcardExpandCap);
        final String cacheKey = buildCacheKey(wildcard, cap);
        Result cached = CACHE.get(cacheKey);
        if (cached != null) return cached;

        Result fresh = doExpand(wildcard, state, cap);
        CACHE.put(cacheKey, fresh);
        logOnce(cacheKey, fresh, cap);
        return fresh;
    }

    /** 清空缓存（配置变更、服务端停止时调用，避免旧配置残留）。 */
    public static void clearCache() {
        CACHE.clear();
        LOGGED.clear();
    }

    private static Result doExpand(ItemStack wildcard, SmartWildcardState state, int cap) {
        NBTTagCompound template = wildcard.getTagCompound();
        if (template == null) return new Result(new ArrayList<>(), 0, 0, false, "no-template-nbt");

        NBTTagList templateIn = template.getTagList("in", Constants.NBT.TAG_COMPOUND);
        NBTTagList templateOut = template.getTagList("out", Constants.NBT.TAG_COMPOUND);
        if (templateIn.tagCount() == 0 || templateOut.tagCount() == 0) {
            return new Result(new ArrayList<>(), 0, 0, false, "template-in-out-missing");
        }

        // 1) 逐规则求材料集（多规则取交集）
        Set<String> materials = null;
        String firstPrefix = null;
        for (SmartWildcardState.Rule rule : state.rulesView()) {
            if (rule == null || rule.matcher == null || rule.matcher.isEmpty()) continue;
            if (rule.slot < 0 || rule.slot >= templateIn.tagCount()) {
                MyMod.LOG.warn(
                    "[AE2QoL] 智能通配样板规则槽位越界，已忽略：slot={} inSize={} matcher={}",
                    rule.slot,
                    templateIn.tagCount(),
                    rule.matcher);
                continue;
            }
            Set<String> ruleMaterials = new LinkedHashSet<>();
            String prefix = matcherLiteralPrefix(rule.matcher);
            for (String oreName : OreDictionary.getOreNames()) {
                if (oreName == null || !SmartWildcardState.matches(rule.matcher, oreName)) continue;
                String material = oreName.length() > prefix.length() ? oreName.substring(prefix.length()) : "";
                if (!material.isEmpty()) ruleMaterials.add(material);
            }
            if (firstPrefix == null) firstPrefix = prefix;
            materials = (materials == null) ? ruleMaterials : intersect(materials, ruleMaterials);
        }
        if (materials == null || materials.isEmpty()) {
            return new Result(new ArrayList<>(), 0, 0, false, "no-material-matched");
        }
        final String inputPrefix = firstPrefix == null ? "" : firstPrefix;

        // 2) 输出侧前缀：模板输出的矿辞名去掉模板材料名（模板材料名由模板输入的矿辞推出）
        String templateMaterial = templateMaterialName(templateIn, state);
        String outputPrefix = templateOutputPrefix(templateOut, templateMaterial);

        // 3) 逐材料实例化
        List<ItemStack> out = new ArrayList<>();
        int skipped = 0;
        boolean truncated = false;
        for (String material : materials) {
            if (out.size() >= cap) {
                truncated = true;
                break;
            }
            ItemStack inStack = firstOreStack(inputPrefix + material);
            if (inStack == null) {
                skipped++;
                continue;
            }
            ItemStack outStack = null;
            if (outputPrefix != null && !outputPrefix.isEmpty()) {
                outStack = firstOreStack(outputPrefix + material);
                if (outStack == null) {
                    skipped++;
                    continue;
                }
            }
            List<String> tokens = candidateTokens(material, inputPrefix, outputPrefix, inStack, outStack);
            if (!state.acceptsCandidate(tokens)) continue;

            ItemStack concrete = buildConcretePattern(wildcard, templateIn, templateOut, state, material, inStack,
                outStack);
            if (concrete != null) out.add(concrete);
        }
        return new Result(out, materials.size(), skipped, truncated, null);
    }

    /**
     * 克隆通配样板并改写为“一张具体样板”。
     * 关键点：**剥掉本模组规则子树**，否则宿主重建索引时会再次展开（无限放大）。
     */
    private static ItemStack buildConcretePattern(ItemStack wildcard, NBTTagList templateIn, NBTTagList templateOut,
        SmartWildcardState state, String material, ItemStack inStack, ItemStack outStack) {
        try {
            ItemStack concrete = wildcard.copy();
            concrete.stackSize = 1;
            NBTTagCompound tag = concrete.getTagCompound();
            if (tag == null) return null;
            tag.removeTag(SmartWildcardState.KEY_ROOT);

            NBTTagList newIn = new NBTTagList();
            for (int i = 0; i < templateIn.tagCount(); i++) {
                NBTTagCompound slot = (NBTTagCompound) templateIn.getCompoundTagAt(i)
                    .copy();
                SmartWildcardState.Rule rule = ruleForSlot(state, i);
                long amount = rule == null ? readAmount(slot) : Math.max(1L, rule.amount);
                ItemStack stack = rule == null ? ItemStack.loadItemStackFromNBT(slot) : inStack.copy();
                if (stack == null) continue;
                stack.stackSize = (int) Math.min(Integer.MAX_VALUE, amount);
                NBTTagCompound slotTag = new NBTTagCompound();
                stack.writeToNBT(slotTag);
                // AE2 对“数量 0”的历史语义是 Cnt 字段，这里同时写 Count/Cnt（参考实现踩过的坑）
                slotTag.setInteger("Count", stack.stackSize);
                slotTag.setLong("Cnt", stack.stackSize);
                newIn.appendTag(slotTag);
            }
            tag.setTag("in", newIn);

            if (outStack != null) {
                NBTTagList newOut = new NBTTagList();
                for (int i = 0; i < templateOut.tagCount(); i++) {
                    NBTTagCompound slot = (NBTTagCompound) templateOut.getCompoundTagAt(i)
                        .copy();
                    ItemStack stack = ItemStack.loadItemStackFromNBT(slot);
                    if (stack == null) continue;
                    // 只替换“与模板材料同名”的那个输出槽，其余输出（副产物）原样保留
                    OrePrefixInfo info = oreInfo(stack);
                    if (info != null && info.material.equals(material)) {
                        stack = outStack.copy();
                        stack.stackSize = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, readAmount(slot)));
                    }
                    NBTTagCompound slotTag = new NBTTagCompound();
                    stack.writeToNBT(slotTag);
                    slotTag.setInteger("Count", stack.stackSize);
                    slotTag.setLong("Cnt", stack.stackSize);
                    newOut.appendTag(slotTag);
                }
                if (newOut.tagCount() > 0) tag.setTag("out", newOut);
            }

            tag.setBoolean("crafting", false);
            tag.setInteger("ae2qolGenerated", 1);
            return concrete;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 智能通配样板实例化失败：material=" + material, t);
            return null;
        }
    }

    private static SmartWildcardState.Rule ruleForSlot(SmartWildcardState state, int slot) {
        for (SmartWildcardState.Rule rule : state.rulesView()) {
            if (rule != null && rule.slot == slot && rule.matcher != null && !rule.matcher.isEmpty()) return rule;
        }
        return null;
    }

    private static long readAmount(NBTTagCompound slot) {
        if (slot.hasKey("Cnt")) return Math.max(1L, slot.getLong("Cnt"));
        if (slot.hasKey("Count")) return Math.max(1L, slot.getInteger("Count"));
        return 1L;
    }

    /** 输入侧模板材料名（由第一条规则的槽位对应的模板输入矿辞推出；拿不到返回 ""）。 */
    private static String templateMaterialName(NBTTagList templateIn, SmartWildcardState state) {
        for (SmartWildcardState.Rule rule : state.rulesView()) {
            if (rule == null || rule.slot < 0 || rule.slot >= templateIn.tagCount()) continue;
            ItemStack stack = ItemStack.loadItemStackFromNBT(templateIn.getCompoundTagAt(rule.slot));
            OrePrefixInfo info = oreInfo(stack);
            if (info != null) return info.material;
        }
        // 退化：模板输入本身没有矿辞时，用模板输出的材料名
        return "";
    }

    /** 输出侧前缀 = 模板输出矿辞名去掉模板材料名。 */
    private static String templateOutputPrefix(NBTTagList templateOut, String templateMaterial) {
        for (int i = 0; i < templateOut.tagCount(); i++) {
            ItemStack stack = ItemStack.loadItemStackFromNBT(templateOut.getCompoundTagAt(i));
            OrePrefixInfo info = oreInfo(stack);
            if (info == null) continue;
            if (!templateMaterial.isEmpty()) {
                if (info.material.equals(templateMaterial)) return info.prefix;
                continue;
            }
            return info.prefix;
        }
        return null;
    }

    private static List<String> candidateTokens(String material, String inputPrefix, String outputPrefix,
        ItemStack inStack, ItemStack outStack) {
        List<String> tokens = new ArrayList<>(6);
        tokens.add(material);
        tokens.add(inputPrefix + material);
        if (outputPrefix != null && !outputPrefix.isEmpty()) tokens.add(outputPrefix + material);
        if (inStack != null && inStack.getItem() != null) {
            tokens.add(
                String.valueOf(inStack.getItem()
                    .getItemStackDisplayName(inStack)));
        }
        if (outStack != null && outStack.getItem() != null) {
            tokens.add(
                String.valueOf(outStack.getItem()
                    .getItemStackDisplayName(outStack)));
        }
        return tokens;
    }

    /** 取某矿辞的首个候选物品（优先 gregtech，其次原样首个），数量归一为 1。 */
    private static ItemStack firstOreStack(String oreName) {
        try {
            ArrayList<ItemStack> ores = OreDictionary.getOres(oreName);
            if (ores == null || ores.isEmpty()) return null;
            ItemStack fallback = null;
            for (ItemStack stack : ores) {
                if (stack == null || stack.getItem() == null) continue;
                if (fallback == null) fallback = stack;
                // 去重优先 gregtech（与参考实现一致，但这里行为可预测：找不到就用第一个）
                if ("gregtech".equals(modIdOf(stack))) {
                    fallback = stack;
                    break;
                }
            }
            if (fallback == null) return null;
            ItemStack copy = fallback.copy();
            copy.stackSize = 1;
            return copy;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 读取矿辞失败：ore=" + oreName, t);
            return null;
        }
    }

    private static String modIdOf(ItemStack stack) {
        try {
            Object name = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
            if (name == null) return "";
            String full = name.toString();
            int idx = full.indexOf(':');
            return idx > 0 ? full.substring(0, idx) : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** 矿辞名拆成“前缀 + 材料名”：前缀 = 第一个大写字母之前的部分（ingotIron → ingot / Iron）。 */
    private static final class OrePrefixInfo {

        final String prefix;
        final String material;

        OrePrefixInfo(String prefix, String material) {
            this.prefix = prefix;
            this.material = material;
        }
    }

    private static OrePrefixInfo oreInfo(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        try {
            int[] ids = OreDictionary.getOreIDs(stack);
            if (ids == null || ids.length == 0) return null;
            String oreName = OreDictionary.getOreName(ids[0]);
            if (oreName == null || oreName.isEmpty()) return null;
            int split = 0;
            while (split < oreName.length() && !Character.isUpperCase(oreName.charAt(split))) split++;
            if (split == 0 || split >= oreName.length()) return null;
            return new OrePrefixInfo(oreName.substring(0, split), oreName.substring(split));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 匹配串里的字面前缀（`ingot*` → `ingot`；无通配符时按整串前缀处理）。 */
    private static String matcherLiteralPrefix(String matcher) {
        if (matcher == null) return "";
        int star = matcher.indexOf('*');
        int q = matcher.indexOf('?');
        int cut = star < 0 ? q : (q < 0 ? star : Math.min(star, q));
        return cut < 0 ? matcher : matcher.substring(0, cut);
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> result = new LinkedHashSet<>(a);
        result.retainAll(b);
        return result;
    }

    private static String buildCacheKey(ItemStack wildcard, int cap) {
        NBTTagCompound tag = wildcard.getTagCompound();
        return cap + "|" + (tag == null ? "null" : tag.toString());
    }

    private static void logOnce(String key, Result result, int cap) {
        try {
            if (LOGGED.contains(key)) return;
            if (LOGGED.size() > 512) LOGGED.clear();
            LOGGED.add(key);
            if (result.truncated) {
                MyMod.LOG.warn(
                    "[AE2QoL] 智能通配样板展开达到上限，已截断：cap={} {}（调大 smart_wildcard_expand_cap 可放宽）",
                    cap,
                    result.describe());
            } else {
                MyMod.LOG.info("[AE2QoL] 智能通配样板展开：{}", result.describe());
            }
        } catch (Throwable ignored) {}
    }
}
