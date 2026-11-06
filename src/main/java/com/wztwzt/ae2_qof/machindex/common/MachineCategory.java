package com.wztwzt.ae2_qof.machindex.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 功能大类映射：把 NEI 的 recipeTypeId 收敛成「卷板/研磨/发电」这类玩家能理解的大类。
 *
 * <h2>为什么需要它</h2>
 * GT 的 recipeTypeId 有四百多个（{@code gt.recipe.macerator}、{@code gt.recipe.metalbender}…），
 * 一个类型一条任务会有几百条，玩家根本翻不完。用户 2026-10-27 拍板「按类别合并：
 * 一个功能大类 = 一条任务」，所以这里把 typeId 归到约 30 个大类。
 *
 * <h2>匹配规则（按顺序，先命中先得）</h2>
 * 1. 先按 <b>typeId 后缀关键字</b>匹配（英文，稳定，不受本地化影响）；
 * 2. 再按 <b>显示名关键字</b>兜底（覆盖非 GT 模组的中文/英文名）；
 * 3. 都不命中 → {@link #OTHER}，并记进「待人工分类」清单（供 export 输出给用户确认）。
 *
 * <p>
 * 规则表刻意写成「类别 → 关键字数组」的纯数据，方便后续按用户反馈增删；
 * 不改代码结构就能扩类。
 */
public final class MachineCategory {

    private MachineCategory() {}

    /** 未归类（会导出给用户手工指定）。 */
    public static final String OTHER = "其他";

    /** 类别 → 关键字（小写；对 typeId 后缀与显示名同时匹配）。 */
    private static final Map<String, String[]> RULES = new LinkedHashMap<>();

    static {
        // 顺序有意义：越具体的越靠前（如「装配线」要在「组装」之前命中）
        RULES.put("研磨/粉碎", new String[] { "macerat", "oremill", "sifter", "grind", "研磨", "粉碎", "磨粉", "筛选" });
        RULES.put("卷板/弯曲", new String[] { "metalbender", "bend", "卷板", "弯曲", "折弯" });
        RULES.put("压模/成型", new String[] { "extrud", "hammer", "form", "压模", "成型", "锻造", "挤压", "冲压" });
        RULES.put("压缩", new String[] { "compress", "压缩" });
        RULES.put("切割", new String[] { "cuttingsaw", "cutting", "chisel", "切割", "锯", "凿" });
        RULES.put("洗矿", new String[] { "orewasher", "simplewasher", "wash", "洗矿", "清洗" });
        RULES.put("离心", new String[] { "centrifug", "离心" });
        RULES.put("电解", new String[] { "electrolyz", "电解" });
        RULES.put("化学反应", new String[] { "chemical", "reactor", "brew", "ferment", "mix", "化学反应", "酿造", "发酵", "混合" });
        RULES.put("蒸馏", new String[] { "distill", "coldtrap", "蒸馏", "冷凝" });
        RULES.put(
            "加热/熔炼",
            new String[] { "furnace", "heater", "pyro", "plasma", "solar", "alloysmelt", "cokeoven", "熔炼", "加热", "合金",
                "焦炉" });
        RULES.put("组装/装配", new String[] { "assembl", "nanochip", "组装", "装配" });
        RULES.put("线材/拉丝", new String[] { "wiremill", "wire", "线材", "拉丝" });
        RULES.put("极性/磁化", new String[] { "polariz", "极性", "磁化" });
        RULES.put("车床/加工", new String[] { "lathe", "车床" });
        RULES.put("扫描/研究", new String[] { "scanner", "scan", "research", "扫描", "研究" });
        RULES.put("包装", new String[] { "packag", "包装" });
        RULES.put("复制/增殖", new String[] { "massfab", "uuamplif", "replicat", "复制", "增殖" });
        RULES.put("聚变/核", new String[] { "fusion", "nuke", "reactor", "聚变", "核" });
        RULES.put("发电", new String[] { "generator", "turbine", "combustion", "fuel", "boiler", "发电", "涡轮", "燃烧" });
        RULES.put("种植/生物", new String[] { "algae", "fish", "crop", "bee", "种植", "养殖" });
        RULES.put("废料处理", new String[] { "recycl", "waste", "scrap", "回收", "废料" });
    }

    /**
     * 解析类别。
     *
     * @param typeId      NEI 的 recipeTypeId（如 {@code gt.recipe.macerator}）
     * @param displayName 类型显示名（本地化，可能为空）
     * @return 大类名；不命中返回 {@link #OTHER}
     */
    public static String resolve(String typeId, String displayName) {
        String haystack = normalize(typeId) + "\u0001" + normalize(displayName);
        for (Map.Entry<String, String[]> e : RULES.entrySet()) {
            for (String kw : e.getValue()) {
                if (haystack.contains(kw)) return e.getKey();
            }
        }
        return OTHER;
    }

    /** 是否命中「未归类」（需要人工指定）。 */
    public static boolean isOther(String category) {
        return category == null || OTHER.equals(category);
    }

    // ===================== 两章划分（用户 2026-10-29 拍板） =====================

    /** 章节一：加工（把输入变成输出）。 */
    public static final String CHAPTER_PROCESSING = "加工";

    /** 章节二：生产（持续产能 / 资源产出 / 特殊维度）。 */
    public static final String CHAPTER_PRODUCTION = "生产";

    /**
     * 二级类别 → 章节（用户拍板：**最多两个章节**，不再往下拆）。
     *
     * <p>
     * 分章语义：
     * <ul>
     * <li><b>加工</b>：改变物品/流体形态或成分的工序 —— 研磨、洗矿、离心、电解、化学反应、蒸馏、
     * 加热/熔炼/低温、卷板/弯曲、压模/成型、压缩、切割、组装/装配、线材、极性、车床、扫描、包装、装罐；</li>
     * <li><b>生产</b>：持续产能或产出资源 —— 发电、聚变/核/加速器、种植/生物、废料处理、太空/行星、
     * 复制/增殖、特殊/唯一、未归类。</li>
     * </ul>
     *
     * <p>
     * 为什么不做更多章节：用户明确否决了 28 章方案（「那不行，太多了」）。
     * 章内导航改用<b>文字</b>：章节 DESC 里写分类目录，任务按二级类别分组排序。
     */
    public static String chapterOf(String category) {
        if (category == null) return CHAPTER_PRODUCTION;
        switch (category) {
            case "研磨/粉碎":
            case "卷板/弯曲":
            case "压模/成型":
            case "压缩":
            case "切割":
            case "洗矿/分选":
            case "离心":
            case "电解":
            case "化学反应":
            case "蒸馏":
            case "加热/熔炼/低温":
            case "组装/装配":
            case "线材/拉丝":
            case "极性/磁化":
            case "车床/加工":
            case "扫描/研究":
            case "包装/装罐":
                return CHAPTER_PROCESSING;
            default:
                // 发电 / 聚变核加速器 / 种植生物 / 废料处理 / 太空行星 / 复制增殖 / 特殊 / 其他
                return CHAPTER_PRODUCTION;
        }
    }

    /** 全部已知类别（按定义顺序，供命令提示/导出）。 */
    public static List<String> allCategories() {
        List<String> out = new ArrayList<>(RULES.keySet());
        out.add(OTHER);
        return out;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
