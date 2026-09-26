package com.wztwzt.ae2_qof.wildcard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

/**
 * 智能通配样板的数据模型（3.22.0）。
 *
 * <h2>路线来源</h2>
 * 参考实现（Wildcard Pattern 1.1.0，取证报告见 {@code docs/research/wildcardpattern-forensics.md}）
 * 证明了一件事：**让 AE2/机器接受“一张样板覆盖一类配方”的唯一省事办法是“展开”** ——
 * 在宿主建样板索引时，把一条规则展开成 N 张各自合法的普通样板（逐候选克隆 + 改写原生
 * {@code in}/{@code out} NBT）。本模组沿用这条路线，但数据模型按本仓原则重做：
 *
 * <ul>
 * <li><b>模板留在原生 NBT</b>：物品本身仍是一张**合法的 AE2 编码样板**（原生 {@code in}/{@code out} 即模板，
 * 也就是用户从 NEI 导出的那一条配方）。本类的规则只描述“把模板里第 i 个输入换成什么通配条件”，
 * 因此既能随时重新推导展开结果，也不会出现参考实现那种“未配置时 in/out 被删掉、原生逻辑解析失败”的副作用。</li>
 * <li><b>黑白名单每张样板独立</b>（用户 2026-09-26 决定）：白名单非空时只允许命中者；黑名单永远优先；
 * 手动排除的候选直接写进黑名单。</li>
 * <li><b>电路/不消耗物品可以随样板走</b>（用户决定优先级：**样板自带 &gt; 槽位设置 &gt; 整机电路**）：
 * 这里存的是**选择**（电路号 / 物品列表），不是要合成出来的电路物品。</li>
 * <li><b>带修订号</b>：{@link #revision} 参与展开缓存键与跨端校验，避免“改完不生效”和“两端静默不一致”。</li>
 * </ul>
 */
public final class SmartWildcardState {

    /** 本模组数据在样板 NBT 里的根键（与原生 in/out 平级，互不干扰）。 */
    public static final String KEY_ROOT = "ae2qolSmartWildcard";

    private static final String KEY_RULES = "Rules";
    private static final String KEY_BLACKLIST = "Blacklist";
    private static final String KEY_WHITELIST = "Whitelist";
    private static final String KEY_CIRCUIT = "Circuit";
    private static final String KEY_NON_CONSUMED = "NonConsumed";
    private static final String KEY_REVISION = "Revision";

    /** 规则：把模板输入列表里第 {@link #slot} 个输入替换为匹配条件。 */
    public static final class Rule {

        private static final String KEY_SLOT = "Slot";
        private static final String KEY_MODE = "OreDict";
        private static final String KEY_MATCHER = "Matcher";
        private static final String KEY_AMOUNT = "Amount";

        /** 模板输入列表下标（0 起）。 */
        public int slot;
        /** true=矿辞模式（ingot*），false=显示名模式（*锭）。 */
        public boolean oreDictMode = true;
        /** 匹配串，允许 * 与 ? 通配。 */
        public String matcher = "";
        /** 模板里该输入的每轮数量（用于实例化候选时的数量计算）。 */
        public long amount = 1L;

        public Rule() {}

        public Rule(int slot, boolean oreDictMode, String matcher, long amount) {
            this.slot = slot;
            this.oreDictMode = oreDictMode;
            this.matcher = matcher == null ? "" : matcher;
            this.amount = amount;
        }

        public Rule copy() {
            return new Rule(slot, oreDictMode, matcher, amount);
        }

        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger(KEY_SLOT, slot);
            tag.setBoolean(KEY_MODE, oreDictMode);
            tag.setString(KEY_MATCHER, matcher);
            tag.setLong(KEY_AMOUNT, amount);
            return tag;
        }

        static Rule read(NBTTagCompound tag) {
            Rule rule = new Rule();
            rule.slot = tag.getInteger(KEY_SLOT);
            rule.oreDictMode = !tag.hasKey(KEY_MODE) || tag.getBoolean(KEY_MODE);
            rule.matcher = tag.getString(KEY_MATCHER);
            rule.amount = tag.hasKey(KEY_AMOUNT) ? tag.getLong(KEY_AMOUNT) : 1L;
            return rule;
        }
    }

    /** 展开规则（按模板输入下标）。 */
    public final List<Rule> rules = new ArrayList<>();
    /** 黑名单：命中的候选被排除；手动排除也写这里。 */
    public final List<String> blacklist = new ArrayList<>();
    /** 白名单：非空时只允许命中者（仍受黑名单约束）。 */
    public final List<String> whitelist = new ArrayList<>();
    /** 样板自带的电路号；{@code -1} 表示“本样板不指定”（交给槽位设置/整机电路）。 */
    public int circuit = -1;
    /** 样板自带的“其他不消耗物品”（铸模/模头/透镜等）；每项 stackSize 无意义，仅作标记。 */
    public final List<ItemStack> nonConsumed = new ArrayList<>();
    /** 修订号：每次写回都 +1，用于展开缓存键与跨端校验。 */
    public int revision = 0;

    /** 是否为带本模组数据的通配样板（只看标记，不校验规则是否完整）。 */
    public static boolean isSmartWildcard(ItemStack stack) {
        return stack != null && stack.hasTagCompound() && stack.getTagCompound().hasKey(KEY_ROOT, Constants.NBT.TAG_COMPOUND);
    }

    /** 读取指定物品上的数据；不是本模组的样板时返回 {@code null}（调用方负责打日志，不静默）。 */
    public static SmartWildcardState of(ItemStack stack) {
        if (!isSmartWildcard(stack)) return null;
        SmartWildcardState state = new SmartWildcardState();
        NBTTagCompound root = stack.getTagCompound().getCompoundTag(KEY_ROOT);
        state.revision = root.getInteger(KEY_REVISION);

        NBTTagList ruleList = root.getTagList(KEY_RULES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < ruleList.tagCount(); i++) {
            state.rules.add(Rule.read(ruleList.getCompoundTagAt(i)));
        }
        readStringList(root, KEY_BLACKLIST, state.blacklist);
        readStringList(root, KEY_WHITELIST, state.whitelist);

        state.circuit = root.hasKey(KEY_CIRCUIT) ? root.getInteger(KEY_CIRCUIT) : -1;

        NBTTagList ncList = root.getTagList(KEY_NON_CONSUMED, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < ncList.tagCount(); i++) {
            ItemStack item = ItemStack.loadItemStackFromNBT(ncList.getCompoundTagAt(i));
            if (item != null) state.nonConsumed.add(item);
        }
        return state;
    }

    /** 写回指定物品（不自动 +1 修订号，调用方决定语义）。 */
    public void write(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        NBTTagCompound root = new NBTTagCompound();

        NBTTagList ruleList = new NBTTagList();
        for (Rule rule : rules) {
            if (rule == null || rule.matcher == null || rule.matcher.isEmpty()) continue;
            ruleList.appendTag(rule.write());
        }
        root.setTag(KEY_RULES, ruleList);
        root.setTag(KEY_BLACKLIST, writeStringList(blacklist));
        root.setTag(KEY_WHITELIST, writeStringList(whitelist));
        root.setInteger(KEY_CIRCUIT, circuit);

        NBTTagList ncList = new NBTTagList();
        for (ItemStack item : nonConsumed) {
            if (item == null) continue;
            NBTTagCompound itemTag = new NBTTagCompound();
            item.writeToNBT(itemTag);
            ncList.appendTag(itemTag);
        }
        root.setTag(KEY_NON_CONSUMED, ncList);
        root.setInteger(KEY_REVISION, revision);

        tag.setTag(KEY_ROOT, root);
    }

    /** 写回并 +1 修订号（任何“用户改过配置”的路径都该用它，保证缓存失效）。 */
    public void writeAndBumpRevision(ItemStack stack) {
        revision++;
        write(stack);
    }

    /** 该样板是否已配置出至少一条规则。 */
    public boolean isConfigured() {
        for (Rule rule : rules) {
            if (rule != null && rule.matcher != null && !rule.matcher.isEmpty()) return true;
        }
        return false;
    }

    /**
     * 候选是否被允许（黑名单优先，其次白名单）。
     *
     * @param tokens 该候选的全部可匹配词（材料名/显示名/矿辞名），任一命中即算命中
     */
    public boolean acceptsCandidate(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return false;
        for (String token : tokens) {
            if (matchesAny(blacklist, token)) return false;
        }
        if (whitelist.isEmpty()) return true;
        for (String token : tokens) {
            if (matchesAny(whitelist, token)) return true;
        }
        return false;
    }

    /** 把一条排除词加入黑名单（可视化“手动排除”与文本框共用）；返回是否真的新增。 */
    public boolean addBlacklist(String token) {
        if (token == null) return false;
        String trimmed = token.trim();
        if (trimmed.isEmpty() || blacklist.contains(trimmed)) return false;
        blacklist.add(trimmed);
        return true;
    }

    /** 从黑名单移除（可视化编辑用）；返回是否真的移除。 */
    public boolean removeBlacklist(String token) {
        return token != null && blacklist.remove(token.trim());
    }

    private static boolean matchesAny(List<String> patterns, String token) {
        if (patterns == null || token == null) return false;
        for (String pattern : patterns) {
            if (matches(pattern, token)) return true;
        }
        return false;
    }

    /** 支持 {@code *} 与 {@code ?} 的通配匹配（不分大小写，全串匹配）。 */
    public static boolean matches(String pattern, String token) {
        if (pattern == null || pattern.isEmpty() || token == null) return false;
        StringBuilder regex = new StringBuilder(pattern.length() + 8);
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') regex.append(".*");
            else if (c == '?') regex.append('.');
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        try {
            return token.toLowerCase()
                .matches(regex.toString().toLowerCase());
        } catch (Throwable t) {
            return false;
        }
    }

    private static void readStringList(NBTTagCompound root, String key, List<String> out) {
        NBTTagList list = root.getTagList(key, Constants.NBT.TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            String value = list.getStringTagAt(i);
            if (value != null && !value.trim().isEmpty()) out.add(value.trim());
        }
    }

    private static NBTTagList writeStringList(List<String> values) {
        NBTTagList list = new NBTTagList();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) list.appendTag(new NBTTagString(value.trim()));
            }
        }
        return list;
    }

    /** 只读视图，供诊断与展示使用。 */
    public List<Rule> rulesView() {
        return Collections.unmodifiableList(rules);
    }
}
