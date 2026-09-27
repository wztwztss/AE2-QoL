package com.wztwzt.ae2_qof.wildport.bridge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardExpander;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardState;
import com.wztwzt.ae2_qof.wildport.crafting.WildcardPatternEntry;
import com.wztwzt.ae2_qof.wildport.item.WildcardPatternConfig;
import com.wztwzt.ae2_qof.wildport.item.WildcardPatternState;

/**
 * 我们的数据模型 ⇄ 搬运进来的 Wild 子系统 的**双向桥**（3.24.x）。
 *
 * <h2>为什么需要桥</h2>
 * 界面（{@code wildport.gui.WildcardPatternWindow}）读写的是 Wild 自己的 NBT 键，而**机器侧**（样板总成接管、
 * 索引期展开）读的是我们的 {@link SmartWildcardState}（存在物品 NBT 的子树 {@code ae2qolSmartWildcard} 下）。
 * 两边键名零重叠（已核对），所以桥只要在**打开时**把我们的状态推进 Wild 的键、在**它保存后**把 Wild 的键拉回
 * 我们的子树即可 —— 机器侧继续走我们自己的 {@link SmartWildcardExpander}，不受影响。
 *
 * <h2>为什么用 NBT 往返而不是字段</h2>
 * Wild 的 {@code WildcardPatternEntry} 字段是私有的，但它自带 {@code toNbt()/fromNbt()} 且键名已知
 * （{@code Mode}/{@code Matcher}/{@code Amount}/{@code Stack}/{@code Display}）。用 NBT 往返既不用反射、
 * 也不依赖它的字段可见性，是这里最稳的接法。
 */
public final class WildcardBridge {

    /** 我们模板里的输入/输出键（与 AE2 编码样板一致）。 */
    private static final String KEY_IN = "in";
    private static final String KEY_OUT = "out";
    private static final int TAG_COMPOUND = 10;

    private WildcardBridge() {}

    /**
     * 我们的状态 → Wild 的键（**服务端**在打开界面前调用）。
     *
     * <p>写入内容：每行的输入/输出 entry（模式/匹配串/数量 + 用于显示的模板堆）、总排除串、
     * 每条规则的包含/排除列表。任一行失败都会记 WARN 并跳过该行（不静默、也不整体失败）。
     */
    public static void pushToWild(ItemStack stack, SmartWildcardState state) {
        if (stack == null || state == null) return;
        try {
            NBTTagCompound root = stack.getTagCompound();
            if (root == null) {
                root = new NBTTagCompound();
                stack.setTagCompound(root);
            }
            NBTTagList inTemplate = root.getTagList(KEY_IN, TAG_COMPOUND);
            NBTTagList outTemplate = root.getTagList(KEY_OUT, TAG_COMPOUND);

            List<WildcardPatternEntry> inputs = new ArrayList<>();
            List<WildcardPatternEntry> outputs = new ArrayList<>();
            List<String> ruleIncludes = new ArrayList<>();
            List<String> ruleExcludes = new ArrayList<>();

            for (int i = 0; i < SmartWildcardEditorRows(); i++) {
                SmartWildcardState.Rule rule = i < state.rules.size() ? state.rules.get(i) : null;
                if (rule != null) {
                    WildcardPatternEntry in = entry(rule, rule.matcher, rule.oreDictMode, rule.amount, inTemplate,
                        rule.slot);
                    if (in != null) inputs.add(in);
                    WildcardPatternEntry out = entry(rule, rule.outMatcher, rule.outOreDictMode,
                        rule.outAmount > 0 ? rule.outAmount : rule.amount, outTemplate,
                        rule.slot < outTemplate.tagCount() ? rule.slot : -1);
                    if (out != null) outputs.add(out);
                    // 包含留空（我们的模型没有"必须包含"这一级），排除用我们的规则级排除
                    ruleIncludes.add("");
                    ruleExcludes.add(join(rule.excludes));
                } else {
                    ruleIncludes.add("");
                    ruleExcludes.add("");
                }
            }

            WildcardPatternState.setInputEntries(stack, inputs);
            WildcardPatternState.setOutputEntries(stack, outputs);
            WildcardPatternConfig.apply(stack, join(state.blacklist), ruleIncludes, ruleExcludes);
            MyMod.LOG.info(
                "[AE2QoL] 桥：我们的状态已推给 Wild 界面（输入 {} 条、输出 {} 条、总排除 {} 项、规则级排除 {} 组）",
                inputs.size(),
                outputs.size(),
                state.blacklist.size(),
                ruleExcludes.size());
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 桥（我们的 → Wild）失败：界面将看不到已配置内容", t);
        }
    }

    /**
     * Wild 的键 → 我们的状态（**服务端**在它保存后调用；由 {@code MessageUpdateWildcardConfig} 的处理器转发）。
     *
     * <p>只覆盖我们模型里有的四类信息（规则、总排除、规则级排除、电路保持原值），
     * 写完 {@code writeAndBumpRevision} 并清展开器缓存，让机器侧立刻看到新配置。
     */
    public static boolean pullFromWild(ItemStack stack) {
        if (stack == null || !SmartWildcardState.isSmartWildcard(stack)) return false;
        try {
            SmartWildcardState current = SmartWildcardState.of(stack);
            SmartWildcardState next = new SmartWildcardState();
            if (current != null) {
                next.circuit = current.circuit;
                next.whitelist.addAll(current.whitelist);
                next.nonConsumed.addAll(current.nonConsumed);
            }
            // 总排除
            String global = WildcardPatternConfig.getGlobalExcludeMaterials(stack);
            if (global != null && !global.trim()
                .isEmpty()) {
                for (String token : global.split("[,;\\s]+")) {
                    String tk = token == null ? "" : token.trim();
                    if (!tk.isEmpty() && !next.blacklist.contains(tk)) next.blacklist.add(tk);
                }
            }
            // 规则级排除
            List<String> ruleExcludes = WildcardPatternConfig.getRuleExcludeList(stack, SmartWildcardEditorRows());

            // 规则本体（输入/输出各 9 行，按顺序配对）
            List<WildcardPatternEntry> inputs = WildcardPatternState.getInputEntries(stack);
            List<WildcardPatternEntry> outputs = WildcardPatternState.getOutputEntries(stack);
            int rows = Math.max(inputs == null ? 0 : inputs.size(), outputs == null ? 0 : outputs.size());
            for (int i = 0; i < rows && i < SmartWildcardEditorRows(); i++) {
                WildcardPatternEntry in = inputs != null && i < inputs.size() ? inputs.get(i) : null;
                WildcardPatternEntry out = outputs != null && i < outputs.size() ? outputs.get(i) : null;
                NBTTagCompound inTag = in == null ? null : in.toNbt();
                NBTTagCompound outTag = out == null ? null : out.toNbt();
                String inMatcher = inTag == null ? "" : inTag.getString("Matcher");
                String outMatcher = outTag == null ? "" : outTag.getString("Matcher");
                if (inMatcher.isEmpty() && outMatcher.isEmpty()) continue;
                SmartWildcardState.Rule rule = new SmartWildcardState.Rule(
                    next.rules.size(),
                    inTag == null || inTag.getBoolean("Mode"),
                    inMatcher,
                    inTag == null ? 1L : Math.max(1L, inTag.getLong("Amount")),
                    outMatcher,
                    outTag == null || outTag.getBoolean("Mode"),
                    outTag == null ? 0L : Math.max(0L, outTag.getLong("Amount")));
                if (ruleExcludes != null && i < ruleExcludes.size()) {
                    String ex = ruleExcludes.get(i);
                    if (ex != null && !ex.trim()
                        .isEmpty()) {
                        for (String token : ex.split("[,;\\s]+")) {
                            String tk = token == null ? "" : token.trim();
                            if (!tk.isEmpty()) rule.excludes.add(tk);
                        }
                    }
                }
                next.rules.add(rule);
            }

            next.writeAndBumpRevision(stack);
            SmartWildcardExpander.clearCache();
            MyMod.LOG.info(
                "[AE2QoL] 桥：已把 Wild 界面的配置拉回我们的模型（规则 {} 条、总排除 {} 项、电路 {}）",
                next.rules.size(),
                next.blacklist.size(),
                next.circuit);
            return true;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 桥（Wild → 我们的）失败：本次界面修改未写回我们的模型", t);
            return false;
        }
    }

    /** 行数（与编辑器一致：9）。写成方法是为了让这里的数字只有一处来源。 */
    private static int SmartWildcardEditorRows() {
        return com.wztwzt.ae2_qof.wildcard.WildcardEditorPanel.ROWS;
    }

    private static WildcardPatternEntry entry(SmartWildcardState.Rule rule, String matcher, boolean oreMode,
        long amount, NBTTagList template, int templateIndex) {
        try {
            if (matcher == null || matcher.isEmpty()) return null;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("Mode", oreMode);
            tag.setString("Matcher", matcher);
            tag.setLong("Amount", Math.max(1L, amount));
            if (template != null && templateIndex >= 0 && templateIndex < template.tagCount()) {
                tag.setTag("Stack", template.getCompoundTagAt(templateIndex));
                tag.setTag("Display", template.getCompoundTagAt(templateIndex));
            }
            WildcardPatternEntry entry = WildcardPatternEntry.fromNbt(tag);
            if (entry == null) {
                MyMod.LOG.warn("[AE2QoL] 桥：第 {} 行的 entry 构造失败（界面将缺这一行）", rule.slot + 1);
            }
            return entry;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 桥：构造 entry 异常（跳过该行）", t);
            return null;
        }
    }

    private static String join(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (s == null || s.trim()
                .isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(s.trim());
        }
        return sb.toString();
    }
}
