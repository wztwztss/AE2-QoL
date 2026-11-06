package com.wztwzt.ae2_qof.machindex.common;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 任务分组：把「配方类型」收敛成任务条目。
 *
 * <h2>用户 2026-10-29 定下的口径</h2>
 * <b>先并同系列，剩下的按「干的活」并。</b>
 * <ul>
 * <li><b>系列</b>：名字同属一个机器家族（纳米铸造厂 12 个、生态圈 4 个、束流源室+束流靶室…），
 * 合并成一条 —— 因为它们本来就是「同一台机器的不同模式」；</li>
 * <li><b>干的活</b>：剩下的按功能类别（研磨/粉碎、切割、发电…）合并；</li>
 * <li><b>章节</b>：只有两个 —— 加工（把输入变成输出的工序）与 生产（持续产能/资源产出）。</li>
 * </ul>
 *
 * <h2>为什么不按名字相似度自动合并</h2>
 * 试过按「去数字/罗马数字后同名」合并：333 只降到 305，几乎没效果（合金炉×3 那种太少）。
 * 真正的压缩来自「按功能合并」，所以系列只用于**少数确实成家族**的机器，用显式关键字表控制，
 * 避免「工业坩埚 / 工业炼焦」这种被误并。
 */
public final class MachineGrouping {

    private MachineGrouping() {}

    // ===================== 章节 =====================

    public static final String CHAPTER_PROCESSING = "加工";
    public static final String CHAPTER_PRODUCTION = "生产";

    // ===================== 系列（先并） =====================

    /** 系列关键字 → {组名, 章节}。命中即归该组，不再看功能类别。 */
    private static final Map<String, String[]> SERIES = new LinkedHashMap<>();

    static {
        SERIES.put("纳米铸造厂", new String[] { "纳米铸造厂", CHAPTER_PROCESSING });
        SERIES.put("生态圈", new String[] { "生态圈", CHAPTER_PRODUCTION });
        SERIES.put("合金炉", new String[] { "合金炉", CHAPTER_PROCESSING });
        SERIES.put("束流系列", new String[] { "束流", CHAPTER_PROCESSING });
        SERIES.put("研磨机", new String[] { "研磨机", CHAPTER_PROCESSING });
        SERIES.put("锻造锤", new String[] { "锻造锤", CHAPTER_PROCESSING });
        SERIES.put("恒星锻炉", new String[] { "恒星锻炉", CHAPTER_PROCESSING });
        SERIES.put("压模机", new String[] { "压模机", CHAPTER_PROCESSING });
        SERIES.put("流体固化器", new String[] { "流体固化器", CHAPTER_PROCESSING });
        SERIES.put("流体提取机", new String[] { "流体提取机", CHAPTER_PRODUCTION });
        SERIES.put("电弧炉", new String[] { "电弧炉", CHAPTER_PROCESSING });
        SERIES.put("魔力灌注", new String[] { "魔力灌注", CHAPTER_PRODUCTION });
        SERIES.put("太空采矿", new String[] { "太空采矿", CHAPTER_PRODUCTION });
        SERIES.put("奇迹顶点", new String[] { "奇迹顶点", CHAPTER_PRODUCTION });
        SERIES.put("合金冶炼炉", new String[] { "合金冶炼炉", CHAPTER_PROCESSING });
        SERIES.put("稀土综合处理", new String[] { "稀土综合处理", CHAPTER_PROCESSING });
    }

    // ===================== 功能类别（后并） =====================

    /** 加工章的类别（其余归生产章）。 */
    private static final Set<String> PROCESSING_CATEGORIES = new LinkedHashSet<>(
        Arrays.asList(
            "研磨/粉碎",
            "卷板/弯曲",
            "压模/成型",
            "压缩",
            "切割",
            "洗矿/分选",
            "离心",
            "电解",
            "化学反应",
            "蒸馏",
            "加热/熔炼/低温",
            "组装/装配",
            "线材/拉丝",
            "极性/磁化",
            "车床/加工",
            "扫描/研究",
            "包装/装罐"));

    /**
     * 「未命中规则」的细化类别（用户要求把「其他」这个杂物堆拆开）。
     * 按显示名关键字匹配，命中即用该组名。
     */
    private static final Map<String, String[]> FALLBACK_SPLIT = new LinkedHashMap<>();

    static {
        FALLBACK_SPLIT.put("太空/戴森球", new String[] { "戴森球", "坠星", "穿云", "恒星辐照", "人造恒星" });
        FALLBACK_SPLIT.put("核/反应堆", new String[] { "反应堆", "熔盐", "气冷", "中子", "嬗变", "钍" });
        FALLBACK_SPLIT.put("粒子/对撞/激光", new String[] { "对撞", "激光", "QFT", "概率毁灭", "晶胞" });
        FALLBACK_SPLIT.put(
            "超维度/奇点",
            new String[] { "奇点", "奇异物质", "凝聚态", "致密态", "物质重塑", "物质生成", "量子逆变", "狄拉克", "混沌", "光子掌控", "超维度", "蒸汽奇点" });
        FALLBACK_SPLIT.put("炼金/魔法/仪式", new String[] { "炼金", "灵阵", "血狱", "血魔", "精灵", "永夜", "供奉", "蓝狗", "搬屎" });
        FALLBACK_SPLIT.put(
            "净化/水处理/热交换",
            new String[] { "pH", "澄清", "絮凝", "脱气", "臭氧", "脱硫", "超净水", "快速冷却", "快速热交换", "超级热交换", "吊炸天" });
        FALLBACK_SPLIT.put("蒸汽系列", new String[] { "蒸汽" });
        FALLBACK_SPLIT.put(
            "矿石/粉体/铸造",
            new String[] { "矿石处理", "矿物处理", "稀土矿石", "稀土粉体", "蜂窝", "闪电处理", "坩埚", "脱水", "炼焦", "超泥土", "铸造塔", "湿法" });
        FALLBACK_SPLIT.put(
            "大型工厂综合体",
            new String[] { "中国石化", "唐山", "艾萨", "末影接口", "兰波顿", "制氨", "铂处理", "焦炭终结", "永恒格雷", "液压", "涂层", "集气室", "天气",
                "地狱接口", "作弊者", "细菌" });
        FALLBACK_SPLIT.put("信息/模拟", new String[] { "地下流体", "计算中心", "演算", "异星计算机", "神锻升级" });
    }

    /** 解析结果：组名 + 章节。 */
    public static final class Group {

        public final String name;
        public final String chapter;

        Group(String name, String chapter) {
            this.name = name;
            this.chapter = chapter;
        }
    }

    /**
     * 把一个配方类型归到任务组。
     *
     * @param typeId      配方类型 ID（{@code gt.recipe.xxx} 等）
     * @param displayName 类型本地化显示名
     */
    public static Group resolve(String typeId, String displayName) {
        String name = displayName == null ? "" : displayName;

        // ① 系列优先：多数系列本来就是「一台机器的不同模式」，合并最自然
        for (Map.Entry<String, String[]> e : SERIES.entrySet()) {
            if (name.contains(e.getValue()[0])) {
                return new Group(e.getKey(), e.getValue()[1]);
            }
        }

        // ② 功能类别
        String cat = MachineCategory.resolve(typeId, displayName);
        if (!MachineCategory.isOther(cat)) {
            return new Group(cat, PROCESSING_CATEGORIES.contains(cat) ? CHAPTER_PROCESSING : CHAPTER_PRODUCTION);
        }

        // ③ 「其他」再细分（用户要求消掉这个杂物堆）
        String lower = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String[]> e : FALLBACK_SPLIT.entrySet()) {
            for (String kw : e.getValue()) {
                if (lower.contains(kw.toLowerCase(Locale.ROOT))) {
                    return new Group(e.getKey(), CHAPTER_PRODUCTION);
                }
            }
        }
        return new Group("其他", CHAPTER_PRODUCTION);
    }

    /** 组 → 章节（供已知组名反查）。 */
    public static String chapterOf(String groupName) {
        for (Map.Entry<String, String[]> e : SERIES.entrySet()) {
            if (e.getKey()
                .equals(groupName)) return e.getValue()[1];
        }
        for (String k : FALLBACK_SPLIT.keySet()) {
            if (k.equals(groupName)) return CHAPTER_PRODUCTION;
        }
        return PROCESSING_CATEGORIES.contains(groupName) ? CHAPTER_PROCESSING : CHAPTER_PRODUCTION;
    }

    /** 全部已知组名（供命令/导出）。 */
    public static List<String> allGroups() {
        Set<String> out = new LinkedHashSet<>();
        out.addAll(SERIES.keySet());
        out.addAll(PROCESSING_CATEGORIES);
        out.addAll(FALLBACK_SPLIT.keySet());
        out.add("其他");
        return new java.util.ArrayList<>(out);
    }
}
