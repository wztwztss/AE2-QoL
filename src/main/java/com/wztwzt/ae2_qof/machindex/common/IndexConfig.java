package com.wztwzt.ae2_qof.machindex.common;

/**
 * machindex 的配置（字段级常量 + 运行时可变值）。
 *
 * <p>
 * 与 {@code ae2_qof} 主配置分开放：主配置 {@code Config} 是玩家可见的 UI 参数（并入它的
 * {@code settings.json} 与配置页），而本项是内部索引口径，改动频率低、误解成本高。
 * 但仍复用同一套「JSON + 硬编码默认值」风格，避免新增依赖。
 *
 * <p>
 * 默认值均已按用户 2026-10-27 拍板固定：
 * <ul>
 * <li>Q2：多方块按「合成该机器所需的最低电压」算（crafting_recipe 为主，recipe_voltage 兜底）；</li>
 * <li>Q3：{@code includeUnknown=true}，UNKNOWN 单独分卷；</li>
 * <li>Q4：single 单卷 + 同类型同电压的机器合并成一个任务；</li>
 * </ul>
 */
public final class IndexConfig {

    private IndexConfig() {}

    // ===================== 开关与范围 =====================

    /** 总开关：关掉后不注册事件/命令，零开销。 */
    public static boolean enabled = true;

    /** 进世界自动扫描（缓存有效时只校验任务结构）。 */
    public static boolean autoBuildOnWorldJoin = true;

    /** 使用缓存（缓存键命中则跳过 NEI 扫描）。 */
    public static boolean useCache = true;

    /** UNKNOWN 电压的机器是否建任务（默认 true——硬约束 6 要保留、且单独分卷）。 */
    public static boolean includeUnknown = true;

    // ===================== 任务书形态 =====================

    /** 单个任务的展示方式：display_with_task（挂 1 个 TaskOptionalRetrieval）/ plain（仅图标）。 */
    public static String questMode = "display_with_task";

    /**
     * 每卷最多任务数，超出滚动「机器索引 II/III…」。
     *
     * <p>
     * 2026-10-27 用户拍板「四页合成一页」：任务只含多方块主机、按类型合并后
     * 总量远小于 300，因此默认提到 2000 —— 正常整合包只会有一卷「机器索引」。
     */
    public static int maxQuestsPerChapter = 2000;

    /** 章节模式：single（单卷分卷）/ perType（每类型一章）。 */
    public static String chapterMode = "single";

    /** 电压排序：asc / desc。 */
    public static String voltageSort = "asc";

    /** 模组来源显示：id / name / both。 */
    public static String modNameDisplay = "both";

    // ===================== 推断与性能 =====================

    /** 是否启用「合成表电路/机壳 tier」推断（Q2 的主口径）。 */
    public static boolean enableCraftingInference = true;

    /** 合成表推断每 tick 处理多少条配方（分帧，避免启动卡顿）。 */
    public static int craftingScanPerTick = 500;

    /** 详细日志。 */
    public static boolean debugLog = false;

    /**
     * 缓存 schema 版本：口径/算法变化时递增，强制重扫。
     *
     * <p>
     * <b>2026-10-27 → 2</b>：电压口径从「只查原版工作台」改成「工作台 + GT 装配类配方表」，
     * 且布局从「格数」改成「像素网格」。
     *
     * <p>
     * <b>2026-10-27 → 3</b>：布局从「按类型分段、段高累加」改成「固定 14 行的分栏网格」，
     * 章节高度封顶、横向铺开（用户反馈「太长」）。
     *
     * <p>
     * <b>2026-10-27 → 4</b>：任务粒度重构——只保留多方块主机、一个配方类型一条任务
     * （不再按电压拆分）、组内按合成电压降序、每卷上限提到 2000（用户拍板「四页合一」）。
     *
     * <p>
     * 这些改动都<b>不影响缓存键的其它字段</b>，若不升 schema，老缓存会继续命中
     * ⇒ 用户看不到任何修复效果（实测已踩过这个坑）。凡改动扫描/聚合/布局算法，都必须递增此值。
     */
    public static final int CACHE_SCHEMA_VERSION = 8;

    /** 章节 UUID 命名空间（固定派生，重启/重扫不变）。 */
    public static final String CHAPTER_NAMESPACE = "ae2qof:machindex:chapter:v1:";

    /**
     * 任务 UUID 命名空间。
     *
     * <p>
     * <b>v1 → v2（2026-10-27）</b>：任务粒度从「(typeId, tier)」改成「typeId」。
     * 派生键变了，旧任务的 UUID 全部作废 —— v1 的旧任务靠孤儿清理删除
     * （它们不在新定义集里，apply() 会自动 remove），玩家已读状态随之重置，属预期。
     */
    public static final String QUEST_NAMESPACE = "ae2qof:machindex:quest:v5:";

    /**
     * 任务标题前缀，用于「只认自己生成的条目」这条安全边界。
     *
     * <p>
     * BQ 的 {@code loadDefaultsOnStartup} 或 {@code /bq_admin default load} 会清空 QuestDatabase
     * 再导入；如果按宽松口径反向清理，会在那一刻连带删掉整合包或玩家的任务。
     * 因此只处理「章节 UUID 在 {@link #CHAPTER_NAMESPACE} 命名空间下」且「任务属于已知章节」的条目，
     * 其余一律跳过并记日志。
     */
    public static final String OWN_NAMESPACE_PREFIX = "ae2qof:machindex:";

    /** 单卷名称（single 模式的第 1 卷；后续卷自动加罗马数字后缀）。 */
    public static final String CHAPTER_BASE_NAME = "机器索引";

    /** UNKNOWN 电压的独立分卷名。 */
    public static final String CHAPTER_UNKNOWN_NAME = "机器索引·未知";
}
