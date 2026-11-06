package com.wztwzt.ae2_qof.machindex.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聚合器：把「类型 × 机器」扁平结果聚合成**待写入的 BQ 任务**。
 *
 * <p>
 * <b>任务粒度（用户 2026-10-27 拍板 Q4）</b>：同一个「配方类型 + 主电压档」下的所有机器
 * **合并成一个任务**，任务内逐台列出。
 * 例：{@code [HV] 洗矿机 ×3} —— 一个任务里有 LV/MV/HV 之外的 HV 档三台洗矿机变体。
 *
 * <p>
 * 为什么要合并：GTNH 里同一功能常有大量同档变体（如各电压的单方块机器），
 * 若一台一个任务会瞬间撑出几千条，玩家在任务书里根本翻不动；
 * 合并后同功能同电压只有一条，DESC/任务物品列表里再展开具体机器。
 *
 * <p>
 * <b>为什么任务 ID 不含坐标</b>：布局（x/y）会随机器增减而变，
 * 若坐标进 ID，改一次布局就会让整卷 ID 变化 ⇒ 玩家已读/已完成状态全丢。
 * 所以 ID 只由「typeId + 主电压档」决定，坐标写进 signature 用于变更检测。
 */
public final class QuestAggregator {

    private QuestAggregator() {}

    /**
     * 聚合入口。
     *
     * @param machines 已解析完电压与来源的机器清单
     * @param types    类型清单（提供 displayName）
     * @return 待写入的任务定义（已排序、已定坐标、已算签名）
     */
    public static List<IndexModel.QuestDefinition> aggregate(List<IndexModel.MachineEntry> machines,
        List<IndexModel.RecipeTypeIndex> types) {
        // typeId → 组名 / 章节（用户 2026-10-29：先并同系列，剩下的按「干的活」并）
        Map<String, String> typeGroup = new LinkedHashMap<>();
        Map<String, String> typeChapter = new LinkedHashMap<>();
        for (IndexModel.RecipeTypeIndex t : types) {
            String dn = t.displayName == null || t.displayName.isEmpty() ? t.typeId : t.displayName;
            MachineGrouping.Group g = MachineGrouping.resolve(t.typeId, dn);
            typeGroup.put(t.typeId, g.name);
            typeChapter.put(t.typeId, g.chapter);
        }

        // 1) 闸门：只保留 UHV 及以上（+ 少数未知电压但确属高档的），见 MachineFilter
        // 2) 按「任务组」分桶：一台机器可能服务多个组的类型 ⇒ 各组各出现一次
        Map<String, List<IndexModel.MachineEntry>> buckets = new LinkedHashMap<>();
        Map<String, String> bucketChapter = new LinkedHashMap<>();
        for (IndexModel.MachineEntry machine : machines) {
            if (!MachineFilter.accept(machine)) continue;

            Set<String> groups = new LinkedHashSet<>();
            if (machine.typeIds.isEmpty()) {
                groups.add("其他");
            } else {
                for (String typeId : machine.typeIds) {
                    groups.add(typeGroup.containsKey(typeId) ? typeGroup.get(typeId) : "其他");
                }
            }
            for (String g : groups) {
                buckets.computeIfAbsent(g, k -> new ArrayList<>())
                    .add(machine);
                // 章节以组名反查为准（组名唯一且稳定）
                bucketChapter.put(g, MachineGrouping.chapterOf(g));
            }
        }

        // 3) 每个任务组 → 一个 QuestDefinition；章节只有「加工」「生产」两个
        List<IndexModel.QuestDefinition> out = new ArrayList<>();
        for (Map.Entry<String, List<IndexModel.MachineEntry>> e : buckets.entrySet()) {
            String groupName = e.getKey();
            List<IndexModel.MachineEntry> group = e.getValue();

            // 组内按「合成电压从高到低」排（用户拍板）；同电压按身份键稳定排序。
            // 未知电压（-1）经 sortKey 映射后自然落到末尾。
            group.sort(
                Comparator.comparingInt((IndexModel.MachineEntry m) -> -VoltageResolver.sortKey(m.mainTier))
                    .thenComparing(IndexModel.MachineEntry::identityKey));

            int tier = group.get(0).mainTier;
            String chapter = bucketChapter.containsKey(groupName) ? bucketChapter.get(groupName)
                : MachineGrouping.chapterOf(groupName);

            IndexModel.QuestDefinition q = new IndexModel.QuestDefinition();
            // groupKey 复用为「章节标签」，BqQuestWriter 据此分卷
            q.groupKey = chapter;
            q.typeId = groupName;
            q.typeDisplayName = groupName;
            q.categoryLabel = groupName;
            q.mainTier = tier;
            q.machines.addAll(group);
            // 图标 = 组内最高档机器（排序后的首台）
            q.icon = group.get(0).displayStack;
            q.stableKey = buildStableKey(groupName, tier);
            q.title = buildTitle(q);
            q.desc = buildDesc(q);
            out.add(q);
        }

        // 4) 排序：章节 → 二级类别 → 电压降 → 类型名（决定章内文字目录的顺序）
        out.sort(
            Comparator.comparing((IndexModel.QuestDefinition q) -> safe(q.groupKey))
                .thenComparing(q -> safe(q.categoryLabel))
                .thenComparingInt(q -> -VoltageResolver.sortKey(q.mainTier))
                .thenComparing(q -> safe(q.typeDisplayName)));

        // 5) 布局（矩阵）
        layout(out);

        // 6) 签名（含坐标，所以布局变化也能被检出）
        for (IndexModel.QuestDefinition q : out) {
            q.signature = buildSignature(q);
        }
        return out;
    }

    // ===================== 标题 / 描述 =====================

    private static String buildTitle(IndexModel.QuestDefinition q) {
        // 标题 = 配方类型名（+机器数）；电压明细在 DESC 里逐台列，避免「以最高档代替全部」
        int n = q.machines.size();
        return q.typeDisplayName + (n > 1 ? " ×" + n : "");
    }

    /**
     * 描述（多行）。内容 = 类别 + 配方类型 + 每台多方块主机的合成电压（已按电压降序）+ 尾注。
     *
     * <p>
     * <b>BQ 的 DESC 是纯文本字符串，支持 {@code \n} 换行与 {@code §} 颜色码</b>——
     * 所以「任务书里写字」完全可行（用户问过这一点）。这里把每台机器都写进 DESC，
     * 是因为任务里虽然挂了 items（可滚动），但玩家常常只看描述；两者一致才不会错位。
     *
     * <p>
     * <b>电压口径写在描述里</b>（用户确认）：显示的是「<b>合成该机器所需电压</b>」，
     * 不是它工作时消耗/产出的电压 —— 两者对同一台机器可能差好几档。
     */
    private static String buildDesc(IndexModel.QuestDefinition q) {
        StringBuilder sb = new StringBuilder();
        sb.append("§7所属章节：§f")
            .append(q.groupKey)
            .append("　§7类别：§f")
            .append(q.categoryLabel)
            .append('\n');
        sb.append("§7配方类型：§f")
            .append(q.typeDisplayName)
            .append('\n');
        sb.append("§7类型 ID：§8")
            .append(q.typeId)
            .append('\n');
        sb.append("§7主机数：§f")
            .append(q.machines.size())
            .append(" §7（仅多方块主机）")
            .append('\n');
        sb.append("§7电压口径：§f合成该机器所需电压（非工作电压），从高到低")
            .append('\n');
        sb.append("§7—— 主机列表 ——§r");

        for (IndexModel.MachineEntry m : q.machines) {
            sb.append('\n');
            String tierText = m.mainTier == IndexModel.TIER_UNKNOWN ? "?" : VoltageResolver.tierName(m.mainTier);
            sb.append("§e· §f")
                .append(safe(m.displayName));
            sb.append("  §b[")
                .append(tierText)
                .append(']');
            sb.append("  §7")
                .append(ModSourceResolver.formatSource(m));
            if (m.note != null && !m.note.isEmpty()) {
                sb.append(" §8(")
                    .append(m.note)
                    .append(')');
            }
        }

        sb.append('\n');
        sb.append("§8索引条目，自动生成；请勿删除（删了下次进服会被重建）。§r");
        return sb.toString();
    }

    private static String voltageSourceText(String source) {
        switch (source) {
            case "machine_tier":
                return "机器档位";
            case "crafting_recipe":
                return "合成所需电压";
            case "recipe_voltage":
                return "配方最低电压";
            default:
                return source;
        }
    }

    // ===================== 布局 =====================

    /** 条目边长（像素）。BQ 的 {@code QuestLineEntry} 默认 24×24。 */
    private static final int CELL = 24;

    /**
     * 网格步长（像素）= 条目边长 + 留白 24。
     *
     * <p>
     * 为什么不是紧贴 24：BQ 会给任务框画边框与选中高亮，紧贴时相邻框的边框会压在下一个框上，
     * 看起来像「粘在一起」。留 24 像素（一个框宽）视觉上才分得开。
     */
    private static final int STEP = CELL + 24;

    /**
     * 每行的任务数 —— 横向矩阵的列数。
     *
     * <p>
     * 用户 2026-10-29 反馈「做成矩阵，别老是一个长竖条」：旧布局是「竖着填满 14 行再开右栏」，
     * 48 条任务正好排成 14 行 × 4 列的细长竖条，横屏下极难看。
     * 改成**行优先**：从左往右填满 {@code COLUMNS} 个再换行 ⇒ 48 条 = 8 行 × 6 列，接近正方形。
     *
     * <p>
     * 6 列的依据：加工 26 条 → 5 行（末行 2 个）、生产 22 条 → 4 行（末行 4 个），
     * 画布约 288×240 像素，BQ 默认缩放下无需横向滚动。
     */
    private static final int COLUMNS = 6;

    /**
     * 布局：**行优先的矩阵** —— 从左到右填满 {@link #COLUMNS} 个后换到下一行。
     *
     * <p>
     * <b>历史教训（三版踩过）</b>：
     * <ol>
     * <li>4.5.0 把「格数」当像素（{@code x = 列*2}），24 像素的条目叠成一条竖线；</li>
     * <li>4.5.0-fix2 改成像素网格，但仍「按类型分段、段高累加」⇒ 章节纵向过长；</li>
     * <li>4.5.0-fix6 改成「固定 14 行、竖向填满再开右栏」⇒ 条目少时变成细长竖条（用户实测反馈）。</li>
     * </ol>
     * 本版用行优先矩阵，条目数变化时宽高比保持接近正方。
     *
     * <p>
     * <b>必须幂等</b>：{@code col = i % COLUMNS}、{@code row = i / COLUMNS} 直接算，
     * 不用累加偏移。进服自愈与 rebuild 都会重跑本函数，累加写法会在二次运行时整体偏移。
     *
     * <p>
     * 为什么不依赖 BQ 自动布局：{@code QuestLine} 只有「UUID → (x,y,size)」映射，没有整理能力；
     * 玩家打开章节看到的就是这张画布，必须由生成方排好。
     */
    private static void layout(List<IndexModel.QuestDefinition> quests) {
        for (int i = 0; i < quests.size(); i++) {
            IndexModel.QuestDefinition q = quests.get(i);
            // 用 i 直接算坐标 ⇒ 不同 i 必然落在不同格（不会出现 BQ 静默叠放）
            q.x = (i % COLUMNS) * STEP;
            q.y = (i / COLUMNS) * STEP;
        }
    }

    // ===================== 稳定 ID 与签名 =====================

    /**
     * 任务 UUID 的派生键：只有「类型」（2026-10-27 起不再含电压档）。
     *
     * <p>
     * 一个配方类型一条任务（用户拍板），组内机器按电压排序展示；机器增减只改内容，不改身份。
     * {@code tier} 参数保留是为了调用点不必改签名，实际不参与派生。
     */
    public static String buildStableKey(String typeId, int tier) {
        return typeId;
    }

    /** 内容哈希：标题 + 描述 + 图标身份 + 机器身份集 + 坐标。 */
    public static String buildSignature(IndexModel.QuestDefinition q) {
        StringBuilder sb = new StringBuilder();
        sb.append(q.title)
            .append('\u0001');
        sb.append(q.desc)
            .append('\u0001');
        if (q.icon != null) {
            sb.append(net.minecraft.item.Item.getIdFromItem(q.icon.getItem()))
                .append(':')
                .append(q.icon.getItemDamage())
                .append(':')
                .append(q.icon.stackTagCompound == null ? "" : q.icon.stackTagCompound.hashCode());
        }
        sb.append('\u0001');
        for (IndexModel.MachineEntry m : q.machines) {
            sb.append(m.identityKey())
                .append('/');
        }
        sb.append('\u0001')
            .append(q.x)
            .append(',')
            .append(q.y);
        return MachineIndexCache.sha256(sb.toString());
    }

    // ===================== 工具 =====================

    /** 空安全：显示名可能为 null（未本地化的第三方物品），排序键不能是 null。 */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    // ===================== 统计 =====================

    /** 结构化统计（写日志 + /machindex status 共用同一份内容）。 */
    public static List<String> buildStats(IndexModel.ScanResult result, long elapsedMs) {
        List<String> out = new ArrayList<>();
        out.add("配方类型数=" + result.types.size());
        out.add("机器主机数=" + result.machines.size());
        out.add("任务数=" + result.quests.size());

        Map<String, Integer> modCount = new LinkedHashMap<>();
        int unknownTier = 0;
        int unknownMod = 0;
        int multiblock = 0;
        Map<Integer, Integer> tierHist = new LinkedHashMap<>();
        for (IndexModel.MachineEntry m : result.machines) {
            modCount.merge(m.modId, 1, Integer::sum);
            if (m.mainTier == IndexModel.TIER_UNKNOWN) unknownTier++;
            if (ModSourceResolver.UNKNOWN_MOD.equals(m.modId)) unknownMod++;
            if (m.isMultiblock) multiblock++;
            tierHist.merge(m.mainTier, 1, Integer::sum);
        }
        out.add("来源模组数=" + modCount.size());
        out.add("多方块数=" + multiblock);
        out.add("UNKNOWN 电压数=" + unknownTier);
        out.add("UNKNOWN_MOD 数=" + unknownMod);
        out.add("耗时ms=" + elapsedMs);

        StringBuilder hist = new StringBuilder("电压分布=");
        List<Integer> tiers = new ArrayList<>(tierHist.keySet());
        Collections.sort(tiers);
        for (Integer t : tiers) {
            hist.append(VoltageResolver.tierName(t))
                .append(':')
                .append(tierHist.get(t))
                .append(' ');
        }
        out.add(
            hist.toString()
                .trim());
        return out;
    }
}
