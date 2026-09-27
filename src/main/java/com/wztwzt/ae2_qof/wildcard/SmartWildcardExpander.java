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
        if (wildcard != null && !SmartWildcardState.isSmartWildcard(wildcard)) {
            // 3.32.0：**展开前懒同步** —— 这张样板若只有搬运界面的键（说明玩家在 Wild 窗口里配过、保存过），
            // 而我们的子树还没写，就就地拉一次。用户实测"手动配好保存后机器识别不到" ⇒ 靠保存包那一步不可靠，
            // 这里补一条**读路径上的兜底**（拉不到就照旧按"不是通配样板"处理，并记日志，不静默）。
            try {
                if (com.wztwzt.ae2_qof.wildport.bridge.WildcardBridge.pullFromWild(wildcard)) {
                    MyMod.LOG.info("[AE2QoL] 展开前懒同步：已从 Wild 界面的键拉回我们的配置（该物品此前只有界面键）");
                }
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 展开前懒同步失败（按原逻辑继续）", t);
            }
        }
        if (wildcard == null || !SmartWildcardState.isSmartWildcard(wildcard)) {
            return new Result(new ArrayList<>(), 0, 0, false, "not-a-smart-wildcard");
        }
        // 3.35.0：**模板自愈**。搬进来的 Wild 代码会在首次初始化时把原生 in/out 删掉（它自己改用
        // WildcardInputComponents 存行数据），而本展开器以原生 in/out 当模板 ⇒ 3.34.0 实测
        // reason=template-in-out-missing 出现 23 次、产出恒 0。这里在展开前尝试从行数据重建一次
        // （存量样板一并救回，不需要玩家重配）。失败只记日志、继续按现有 NBT 走。
        try {
            com.wztwzt.ae2_qof.wildport.bridge.WildcardBridge.ensureNativeTemplate(wildcard);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 通配样板模板自愈失败（继续按现有 NBT 展开）", t);
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
            // 没有通配符的规则 = 精确匹配（本来就只命中一个矿辞）：
            // 它既推不出材料名（前缀就是整串），也不该参与材料交集 —— 否则会把整个展开推成空集，
            // 这正是用户实测到的 reason=no-material-matched。这类槽位保持模板原样。
            if (!hasWildcard(rule.matcher)) {
                MyMod.LOG.info(
                    "[AE2QoL] 规则为精确匹配（无通配符），该槽保持模板不变：slot={} matcher={}",
                    rule.slot,
                    rule.matcher);
                continue;
            }
            Set<String> ruleMaterials = new LinkedHashSet<>();
            String prefix = matcherLiteralPrefix(rule.matcher);
            for (String oreName : OreDictionary.getOreNames()) {
                if (oreName == null || !SmartWildcardState.matches(rule.matcher, oreName)) continue;
                String material = oreName.length() > prefix.length() ? oreName.substring(prefix.length()) : "";
                if (material.isEmpty()) continue;
                // 3.23.2 规则级排除：命中就**不进这条规则**的材料集（总排除在候选阶段另有一套，且优先）
                if (excludedByRule(rule, oreName, material)) continue;
                ruleMaterials.add(material);
            }
            if (firstPrefix == null) firstPrefix = prefix;
            materials = (materials == null) ? ruleMaterials : intersect(materials, ruleMaterials);
        }
        if (materials == null || materials.isEmpty()) {
            // 诊断（用户实测：3 条矿辞规则却 produced=0，光看 reason 无法定性）：
            // 把每条规则的 slot / 模式 / 匹配串 / 输出匹配 / 排除项逐条打出来，
            // 并区分"所有规则都被跳过"与"规则跑了但材料集为空"这两种完全不同的原因。
            if (materials == null) {
                MyMod.LOG.warn(
                    "[AE2QoL] 展开失败诊断：没有任何规则参与材料推导（无规则 / 匹配串为空 / 槽位越界 / 无通配符的精确匹配），rules={} inSize={}",
                    state.rules.size(),
                    templateIn.tagCount());
            } else {
                MyMod.LOG.warn("[AE2QoL] 展开失败诊断：材料交集为空（各规则匹配不到共同材料），逐条规则：");
            }
            for (SmartWildcardState.Rule rule : state.rulesView()) {
                if (rule == null) continue;
                MyMod.LOG.warn(
                    "[AE2QoL]   规则 slot={} mode={} matcher='{}' outMatcher='{}' amount={} excludes={}",
                    rule.slot,
                    rule.oreDictMode ? "矿辞" : "显示名",
                    rule.matcher,
                    rule.outMatcher,
                    rule.amount,
                    rule.excludes);
            }
            return new Result(new ArrayList<>(), 0, 0, false, "no-material-matched");
        }
        final String inputPrefix = firstPrefix == null ? "" : firstPrefix;

        // 2) 输出侧候选前缀
        String templateMaterial = templateMaterialName(templateIn, state);
        // 2) 输出侧候选前缀（3.41.0 **修正**）：
        //    旧实现用 templateOutputPrefix(templateOut, templateMaterial)，靠"模板输出槽的材料名 == 模板输入的材料名"
        //    来选前缀；而 GT 板材同时注册 plateIron 与 plateAnyIron，解析出的材料名可能是 AnyIron ⇒ 判等失败 ⇒
        //    返回 **null** ⇒ 下面"逐材料找输出"整段被跳过 ⇒ **几百张具体样板全部保留模板输出（铁板）**。
        //    这正是 3.39/3.40 两轮实测的 `输出种类=1`（而且 3.40.0 新加的"替换未命中 WARN"也不会触发，
        //    因为整段都没进）。现在改为**存在性驱动**：候选前缀 = 规则显式写的输出前缀 + 从每个模板输出槽的
        //    矿辞名里剥掉模板材料名/首个大写字母得到的头；逐材料挑第一个"前缀+材料"在矿辞表里真实存在的。
        java.util.LinkedHashSet<String> outPrefixCandidates = new java.util.LinkedHashSet<>();
        for (SmartWildcardState.Rule rule : state.rulesView()) {
            if (rule == null || rule.outMatcher == null || rule.outMatcher.isEmpty()) continue;
            String rulePrefix = matcherLiteralPrefix(rule.outMatcher);
            if (!rulePrefix.isEmpty()) {
                outPrefixCandidates.add(rulePrefix);
                break;
            }
        }
        collectOutputPrefixCandidates(templateOut, templateMaterial, outPrefixCandidates);
        if (outPrefixCandidates.isEmpty()) {
            logNoOutputPrefixOnce(state, templateOut, templateMaterial);
        }

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
            // 输出：在候选前缀里挑第一个"前缀+材料"真实存在的矿辞（plate + Copper ⇒ plateCopper）
            ItemStack outStack = null;
            String chosenPrefix = null;
            for (String candidatePrefix : outPrefixCandidates) {
                ItemStack candidate = firstOreStack(candidatePrefix + material);
                if (candidate != null) {
                    chosenPrefix = candidatePrefix;
                    outStack = candidate;
                    break;
                }
            }
            if (outStack == null) {
                skipped++;
                continue;
            }
            List<String> tokens = candidateTokens(material, inputPrefix, chosenPrefix, inStack, outStack);
            if (!state.acceptsCandidate(tokens)) continue;

            ItemStack concrete = buildConcretePattern(wildcard, templateIn, templateOut, state, material, chosenPrefix,
                inStack, outStack);
            if (concrete != null) {
                out.add(concrete);
                logSampleOnce(material, chosenPrefix, inStack, outStack, concrete);
            }
        }
        return new Result(out, materials.size(), skipped, truncated, null);
    }

    /**
     * 收集"随材料变化"的输出前缀候选（3.41.0）。
     *
     * <p>两条来源，按顺序加入（{@link java.util.LinkedHashSet} 保序、自动去重）：
     * <ol>
     * <li>矿辞名以**模板材料名**结尾 ⇒ 头即前缀（{@code plateIron} → {@code plate}）；</li>
     * <li>兜底：按"第一个大写字母"切（{@code plateAnyIron} → {@code plate}），
     * 这一步专门覆盖 GT 的 {@code *Any*} 双矿辞名，否则材料名会被算成 {@code AnyIron}。</li>
     * </ol>
     */
    private static void collectOutputPrefixCandidates(NBTTagList templateOut, String templateMaterial,
        java.util.Set<String> out) {
        for (int i = 0; i < templateOut.tagCount(); i++) {
            ItemStack stack = ItemStack.loadItemStackFromNBT(templateOut.getCompoundTagAt(i));
            if (stack == null || stack.getItem() == null) continue;
            int[] ids;
            try {
                ids = OreDictionary.getOreIDs(stack);
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 读取模板输出槽的矿辞失败（该槽跳过）", t);
                continue;
            }
            if (ids == null) continue;
            for (int id : ids) {
                String ore = OreDictionary.getOreName(id);
                if (ore == null || ore.isEmpty()) continue;
                if (templateMaterial != null && !templateMaterial.isEmpty()
                    && ore.length() > templateMaterial.length()
                    && ore.regionMatches(
                        true,
                        ore.length() - templateMaterial.length(),
                        templateMaterial,
                        0,
                        templateMaterial.length())) {
                    String head = ore.substring(0, ore.length() - templateMaterial.length());
                    if (!head.isEmpty()) out.add(head);
                }
                int cut = 0;
                while (cut < ore.length() && !Character.isUpperCase(ore.charAt(cut))) cut++;
                if (cut > 0 && cut < ore.length()) out.add(ore.substring(0, cut));
            }
        }
    }

    /** 候选前缀为空时明确留痕（此时产不出任何具体样板，机器会退回"只认模板那一张"）。 */
    private static final java.util.Set<String> NO_PREFIX_LOGGED = Collections
        .synchronizedSet(new LinkedHashSet<String>());

    private static void logNoOutputPrefixOnce(SmartWildcardState state, NBTTagList templateOut,
        String templateMaterial) {
        try {
            StringBuilder oreNames = new StringBuilder();
            for (int i = 0; i < templateOut.tagCount(); i++) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(templateOut.getCompoundTagAt(i));
                if (stack == null || stack.getItem() == null) continue;
                int[] ids = OreDictionary.getOreIDs(stack);
                if (ids == null) continue;
                for (int id : ids) {
                    if (oreNames.length() > 0) oreNames.append(", ");
                    oreNames.append(OreDictionary.getOreName(id));
                }
            }
            String key = "noprefix|" + templateMaterial + "|" + oreNames;
            if (!NO_PREFIX_LOGGED.add(key)) return;
            if (NO_PREFIX_LOGGED.size() > 64) NO_PREFIX_LOGGED.clear();
            MyMod.LOG.warn(
                "[AE2QoL] 展开时找不到任何可用的输出前缀（本机将只认模板那一张）：模板材料='{}' 模板输出矿辞=[{}] 规则数={}",
                templateMaterial,
                oreNames,
                state == null ? -1 : state.rules.size());
        } catch (Throwable t) {
            // 诊断失败不刷屏
        }
    }

    /** 展开样本诊断（每 JVM 只打 3 条）：直接给出"材料 → 选中的前缀 → 产出输出"。 */
    private static final java.util.concurrent.atomic.AtomicInteger SAMPLE_LOGGED =
        new java.util.concurrent.atomic.AtomicInteger();

    private static void logSampleOnce(String material, String chosenPrefix, ItemStack inStack, ItemStack outStack,
        ItemStack concrete) {
        try {
            if (SAMPLE_LOGGED.getAndIncrement() >= 3) return;
            MyMod.LOG.info(
                "[AE2QoL] 展开样本：material={} prefix={} in={} out={} 产出out={}",
                material,
                chosenPrefix,
                displayNameOf(inStack),
                displayNameOf(outStack),
                firstOutputNameOf(concrete));
        } catch (Throwable t) {
            // 诊断失败不刷屏
        }
    }

    private static String displayNameOf(ItemStack stack) {
        try {
            return stack == null || stack.getItem() == null ? "null"
                : String.valueOf(stack.getItem()
                    .getItemStackDisplayName(stack));
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 读具体样板自己的 out 列表第一个物品名（验证改写是否真的落进 NBT）。 */
    private static String firstOutputNameOf(ItemStack concrete) {
        try {
            NBTTagCompound tag = concrete == null ? null : concrete.getTagCompound();
            if (tag == null) return "no-tag";
            NBTTagList outList = tag.getTagList("out", Constants.NBT.TAG_COMPOUND);
            if (outList.tagCount() == 0) return "out-empty";
            return displayNameOf(ItemStack.loadItemStackFromNBT(outList.getCompoundTagAt(0)));
        } catch (Throwable t) {
            return "?";
        }
    }

    /**
     * 克隆通配样板并改写为“一张具体样板”。
     * 关键点：**剥掉本模组规则子树**，否则宿主重建索引时会再次展开（无限放大）。
     */
    private static ItemStack buildConcretePattern(ItemStack wildcard, NBTTagList templateIn, NBTTagList templateOut,
        SmartWildcardState state, String material, String outputPrefix, ItemStack inStack, ItemStack outStack) {
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
                // 精确匹配的规则不替换该槽（理由见 doExpand 第 1) 步的注释）
                boolean exactRule = rule != null && !hasWildcard(rule.matcher);
                long amount = rule == null ? readAmount(slot) : Math.max(1L, rule.amount);
                ItemStack stack = (rule == null || exactRule) ? ItemStack.loadItemStackFromNBT(slot) : inStack.copy();
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
                boolean replaced = false;
                for (int i = 0; i < templateOut.tagCount(); i++) {
                    NBTTagCompound slot = (NBTTagCompound) templateOut.getCompoundTagAt(i)
                        .copy();
                    ItemStack stack = ItemStack.loadItemStackFromNBT(slot);
                    if (stack == null) continue;
                    // 3.40.0 **修正（致命一行）**：只替换"矿辞前缀 == 本规则输出前缀（如 plate）"的那一个输出槽。
                    // 旧实现写的是 `info.material.equals(material)` —— 拿**候选材料**去比**模板输出的材料名**，
                    // 而模板输出的材料名恒为模板自己那个（例如 Iron）⇒ **只有候选材料恰好是 Iron 时才替换**，
                    // 其余几百张具体样板全部原样保留模板输出（铁板）。
                    // 诊断实证（3.39.0-diag）：三族一律 `输出种类=1 样本=[铁板, 铁板, 铁板]`，
                    // 于是 AE 里永远只有铁板可合成；GT 那台"能下单但不合成"也是同一根因
                    //（AE 计划里的材料与真正推入机器的材料对不上）。
                    OrePrefixInfo info = oreInfo(stack);
                    if (!replaced && outputPrefix != null
                        && !outputPrefix.isEmpty()
                        && matchesOutputPrefix(stack, outputPrefix)) {
                        stack = outStack.copy();
                        stack.stackSize = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, readAmount(slot)));
                        replaced = true;
                    }
                    NBTTagCompound slotTag = new NBTTagCompound();
                    stack.writeToNBT(slotTag);
                    slotTag.setInteger("Count", stack.stackSize);
                    slotTag.setLong("Cnt", stack.stackSize);
                    newOut.appendTag(slotTag);
                }
                if (!replaced) {
                    // 不许静默：替换槽没命中时必须能看出来（每张样板只记一次）
                    logOutputSlotMissOnce(material, outputPrefix, templateOut);
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

    /**
     * 该输出槽是否"随材料变化"：它的某个矿辞名以本规则的输出前缀开头（例如前缀 {@code plate} 命中
     * {@code plateIron} / {@code plateAnyIron}）。
     *
     * <p>为什么不用 {@link #oreInfo} 的单次解析：GT 板材往往同时注册 {@code plateIron} 与
     * {@code plateAnyIron} 两个矿辞名，单次解析取到哪个取决于注册顺序 ⇒ 前缀可能被算成 {@code plateAny}
     * 而与规则里的 {@code plate} 对不上（3.40.0 之前的判据更是彻底错：拿候选材料名去比模板输出的材料名）。
     */
    private static boolean matchesOutputPrefix(ItemStack stack, String outputPrefix) {
        if (stack == null || stack.getItem() == null || outputPrefix == null || outputPrefix.isEmpty()) return false;
        try {
            int[] ids = OreDictionary.getOreIDs(stack);
            if (ids != null) {
                for (int id : ids) {
                    String ore = OreDictionary.getOreName(id);
                    if (ore == null || ore.length() <= outputPrefix.length()) continue;
                    if (ore.regionMatches(true, 0, outputPrefix, 0, outputPrefix.length())) return true;
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 判断输出槽前缀失败（该槽按不替换处理）", t);
        }
        return false;
    }

    /** 替换槽未命中时的诊断（每张样板只记一次，避免热路径刷屏）。 */
    private static final Set<String> OUTPUT_MISS_LOGGED = Collections
        .synchronizedSet(new LinkedHashSet<String>());

    private static void logOutputSlotMissOnce(String material, String outputPrefix, NBTTagList templateOut) {
        try {
            String key = outputPrefix + "|" + material;
            if (!OUTPUT_MISS_LOGGED.add(key)) return;
            if (OUTPUT_MISS_LOGGED.size() > 64) OUTPUT_MISS_LOGGED.clear();
            StringBuilder slots = new StringBuilder();
            for (int i = 0; i < templateOut.tagCount(); i++) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(templateOut.getCompoundTagAt(i));
                OrePrefixInfo info = oreInfo(stack);
                if (slots.length() > 0) slots.append(", ");
                slots.append(info == null ? "?" : info.prefix + "/" + info.material);
            }
            MyMod.LOG.warn(
                "[AE2QoL] 具体样板的输出槽没找到可替换项：material={} outputPrefix='{}' 模板输出槽=[{}] ⇒ 该张仍沿用模板输出",
                material,
                outputPrefix,
                slots);
        } catch (Throwable t) {
            // 诊断本身失败就不刷屏了
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

    /**
     * 供桥/界面用：**直接从单个物品**反推矿辞前缀（与 {@link #oreInfo} 同口径）。
     *
     * <p>3.36.0 新增：桥要把"输出行"填成 {@code plate*}，必须按**该行自己的模板输出物品**取前缀
     * （而不是笼统用模板级推导），否则多输出的配方会把前缀填错。
     */
    public static String orePrefixOfStack(ItemStack stack) {
        OrePrefixInfo info = oreInfo(stack);
        return info == null ? null : info.prefix;
    }

    /**
     * 供**界面显示**用的输出侧矿辞前缀（3.35.0，用户要求"输出行也要看得见，像 {@code plate*}"）。
     *
     * <p>刻意复用展开器自己的模板推导（{@link #templateMaterialName} + {@link #templateOutputPrefix}），
     * 保证界面显示的就是展开时真正会用的那个前缀，而不是另写一套算法导致"显示与行为不一致"。
     *
     * @return 例如 {@code plate}；模板缺失或推不出时返回 null（调用方负责显示原文/留痕）
     */
    public static String displayOutputPrefix(ItemStack wildcard, SmartWildcardState state) {
        try {
            if (wildcard == null || state == null) return null;
            NBTTagCompound tag = wildcard.getTagCompound();
            if (tag == null) return null;
            NBTTagList templateIn = tag.getTagList("in", Constants.NBT.TAG_COMPOUND);
            NBTTagList templateOut = tag.getTagList("out", Constants.NBT.TAG_COMPOUND);
            if (templateIn.tagCount() == 0 || templateOut.tagCount() == 0) return null;
            // 3.41.0：与展开时同一套"存在性驱动"候选（旧实现走 templateOutputPrefix，
            // 遇到 GT 的 plateAnyIron 会返回 null ⇒ 窗口输出行空白，即用户报的"输出不填充"）
            java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
            for (SmartWildcardState.Rule rule : state.rulesView()) {
                if (rule == null || rule.outMatcher == null || rule.outMatcher.isEmpty()) continue;
                String rulePrefix = matcherLiteralPrefix(rule.outMatcher);
                if (!rulePrefix.isEmpty()) {
                    candidates.add(rulePrefix);
                    break;
                }
            }
            collectOutputPrefixCandidates(templateOut, templateMaterialName(templateIn, state), candidates);
            for (String candidate : candidates) {
                if (candidate != null && !candidate.isEmpty()) return candidate;
            }
            return null;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 推导输出侧显示前缀失败（输出行留空）", t);
            return null;
        }
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

    /**
     * 从**真实矿辞名**反推前缀：矿辞 {@code ingotIron} + 材料名 {@code Iron} ⇒ 前缀 {@code ingot}。
     *
     * <p><b>3.35.0 修正（本轮致命 bug）</b>：GT 的 {@code OrePrefixes.getOreprefixKey()} 返回的是
     * **本地化键**而不是矿辞前缀 —— javap 实证它的实现是
     * {@code getDefaultLocalNameFormatForItem(m).toLowerCase().replace(" ","_").replace("%material","material")}，
     * 而 {@code OrePrefixes} 的常量池里正是字符串 {@code gt.oreprefix.} ⇒ 返回 {@code gt.oreprefix.ingot}。
     * 拿它拼规则会得到 {@code gt.oreprefix.ingot*}，**永远匹配不上任何矿辞名**（矿辞名形如 {@code ingotIron}）⇒
     * 展开产出恒为 0。3.34.0 实机表现完全吻合：界面行显示 {@code gt.orepr...}，机器侧一律
     * {@code produced=0}。本方法改用「矿辞名去掉材料名后缀」这一**构造上自洽**的口径（与
     * {@link #matcherLiteralPrefix} 正好互逆），因此多段前缀（{@code crushedPurifiedIron}、
     * {@code plateDoubleIron}）也能切对。
     *
     * @param material {@code OrePrefixes.detectPrefix} 给出的材料名（如 {@code Iron}）；空则返回 null
     * @return 矿辞前缀（如 {@code ingot}）；取不到返回 null（调用方负责留痕，不静默）
     */
    public static String oreDictPrefixOf(ItemStack stack, String material) {
        if (stack == null || stack.getItem() == null || material == null || material.isEmpty()) return null;
        try {
            int[] ids = OreDictionary.getOreIDs(stack);
            if (ids != null) {
                for (int id : ids) {
                    String oreName = OreDictionary.getOreName(id);
                    if (oreName == null || oreName.length() <= material.length()) continue;
                    int cut = oreName.length() - material.length();
                    if (oreName.regionMatches(true, cut, material, 0, material.length())) {
                        String head = oreName.substring(0, cut);
                        if (!head.isEmpty()) return head;
                    }
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 从矿辞名反推前缀失败（该物品按无矿辞处理）", t);
        }
        return null;
    }

    /** 只打一次的"GT 前缀 API 陷阱"提示（避免热路径刷屏）。 */
    private static volatile boolean prefixApiPitfallLogged;

    /**
     * 兜底：只有在 {@code getOreprefixKey()} 明显**不是**本地化键（不以 {@code gt.} 开头、不含 {@code .}）时才采用。
     * 命中陷阱时打一条 WARN（本项目原则：不许静默），然后交回调用方走别的兜底。
     */
    private static String legacyPrefixKeyOrNull(gregtech.api.enums.OrePrefixes prefix) {
        try {
            String key = prefix.getOreprefixKey();
            if (key == null || key.isEmpty()) return null;
            if (key.startsWith("gt.") || key.indexOf('.') >= 0) {
                if (!prefixApiPitfallLogged) {
                    prefixApiPitfallLogged = true;
                    MyMod.LOG.warn(
                        "[AE2QoL] OrePrefixes.getOreprefixKey() 返回的是本地化键（{}）而不是矿辞前缀，已忽略；"
                            + "矿辞前缀改由矿辞名反推",
                        key);
                }
                return null;
            }
            return key;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 取「矿辞前缀 + 材料名」。
     *
     * <p><b>3.22.0 修正</b>：原先按“第一个大写字母”切分矿辞名，对 {@code dustSmallIron}、
     * {@code plateDoubleIron}、{@code crushedPurifiedIron} 这类**多段前缀**会切错
     * （会切成 prefix={@code dust} + material={@code SmallIron}），表现只是“候选变少/不对”，很难查。
     * 现在优先用 GT 权威 API {@code OrePrefixes.detectPrefix(ItemStack)}（按 VALUES 最长前缀匹配 + 特例修正），
     * 前缀一律走 {@link #oreDictPrefixOf}（**3.35.0 起不再用 getOreprefixKey 当矿辞前缀**），
     * 失败才回退到老的切分法并记一条 WARN（不静默）。
     */
    private static OrePrefixInfo oreInfo(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        try {
            java.util.List<gregtech.api.enums.OrePrefixes.ParsedOreDictName> parsed =
                gregtech.api.enums.OrePrefixes.detectPrefix(stack);
            if (parsed != null) {
                for (gregtech.api.enums.OrePrefixes.ParsedOreDictName name : parsed) {
                    if (name == null || name.prefix == null || name.material == null || name.material.isEmpty()) continue;
                    String key = oreDictPrefixOf(stack, name.material);
                    if (key == null || key.isEmpty()) key = legacyPrefixKeyOrNull(name.prefix);
                    if (key == null || key.isEmpty()) continue;
                    return new OrePrefixInfo(key, name.material);
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] OrePrefixes.detectPrefix 失败，回退矿辞名切分", t);
        }
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

    /**
     * 规则级排除判定（3.23.2）：排除串可与**完整矿辞名**或**材料名**比较，两者都支持 {@code *} 与 {@code ?}。
     * 例如规则 `ingot*` 上写 `Aluminium` 就只排除铝；写 `ingotAluminium` 也同样排除。
     * 总排除（全局黑名单）在候选判定阶段另有一套，且**优先于**这里。
     */
    private static boolean excludedByRule(SmartWildcardState.Rule rule, String oreName, String material) {
        if (rule == null || rule.excludes.isEmpty()) return false;
        for (String ex : rule.excludes) {
            if (ex == null || ex.isEmpty()) continue;
            if (SmartWildcardState.matches(ex, oreName) || SmartWildcardState.matches(ex, material)) return true;
        }
        return false;
    }

    /** 匹配串里是否含通配符（{@code *} 或 {@code ?}）；没有则视为精确匹配。 */
    private static boolean hasWildcard(String matcher) {
        return matcher != null && (matcher.indexOf('*') >= 0 || matcher.indexOf('?') >= 0);
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
