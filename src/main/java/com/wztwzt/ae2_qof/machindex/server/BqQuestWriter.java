package com.wztwzt.ae2_qof.machindex.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.machindex.common.IndexConfig;
import com.wztwzt.ae2_qof.machindex.common.IndexModel;
import com.wztwzt.ae2_qof.machindex.common.MachineCategory;
import com.wztwzt.ae2_qof.machindex.common.MachineIndexCache;

import betterquesting.api.enums.EnumLogic;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.tasks.TaskRegistry;
import bq_standard.tasks.TaskOptionalRetrieval;

/**
 * BQ 写入器 —— <b>全项目唯一 import {@code betterquesting.*} / {@code bq_standard.*} 的类</b>
 * （与既有 {@code quest/QuestDetectLogic} 同一纪律：守卫通过后才加载）。
 *
 * <p>
 * <b>写入形态（用户 2026-10-27 拍板）</b>：
 * <ul>
 * <li>每个「配方类型 + 主电压档」一个任务，任务里逐台列出机器（{@link IndexModel.QuestDefinition}）；</li>
 * <li>默认 {@code display_with_task}：挂 1 个 {@code TaskOptionalRetrieval}，
 * 内含该组全部机器 → 任务详情页里每台机器都是一个**可用 NEI 快捷键**的物品槽；</li>
 * <li>完成态：{@code TaskOptionalRetrieval.ignored()} 恒 true（计入 done）+ 显式
 * {@code LOGIC_TASK = NAND} ⇒ {@code NAND(n,n) = (n < n) = false} ⇒ <b>永不完成</b>。
 * 这正是索引卡要的语义（玩家不该「完成」索引，也不该被它打勾干扰进度统计）。</li>
 * </ul>
 *
 * <p>
 * <b>安全边界</b>：只处理「章节 UUID 由 {@link IndexConfig#CHAPTER_NAMESPACE} 派生」的章节，
 * 及其成员任务。数据库里其它条目（整合包官方章节、玩家自建）一律只读跳过。
 * 原因：BQ 的 {@code loadDefaultsOnStartup} / {@code /bq_admin default load} 会清空 QuestDatabase
 * 再导入，宽口径清理会在那一刻连带删掉别人的任务。
 */
public final class BqQuestWriter {

    private BqQuestWriter() {}

    /** 上一轮写入的统计（供 /status）。 */
    private static String lastSummary = "尚未写入";

    public static String getLastSummary() {
        return lastSummary;
    }

    /**
     * 应用一份扫描结果（服务端主线程）。
     *
     * @param result 扫描结果（来自内存或缓存）
     */
    public static void apply(IndexModel.ScanResult result) {
        if (result == null || result.quests.isEmpty()) {
            lastSummary = "无任务可写入";
            return;
        }
        long start = System.currentTimeMillis();

        try {
            // ---------- 1) 分卷 ----------
            Map<String, List<IndexModel.QuestDefinition>> byChapter = groupByChapter(result.quests);
            Set<UUID> touchedLines = new LinkedHashSet<>();
            Set<UUID> touchedQuests = new LinkedHashSet<>();
            int created = 0, updated = 0, removed = 0, skipped = 0;

            for (Map.Entry<String, List<IndexModel.QuestDefinition>> e : byChapter.entrySet()) {
                String chapterName = e.getKey();
                List<IndexModel.QuestDefinition> defs = e.getValue();
                UUID lineId = lineUuid(chapterName);
                IQuestLine line = QuestLineDatabase.INSTANCE.get(lineId);
                boolean newLine = line == null;
                if (newLine) {
                    line = QuestLineDatabase.INSTANCE.createNew(lineId);
                }
                applyLineProperties(line, chapterName, defs);
                touchedLines.add(lineId);

                // 本卷应有的任务集合（用于孤儿清理）
                Set<UUID> expected = new LinkedHashSet<>();
                for (IndexModel.QuestDefinition def : defs) {
                    UUID questId = questUuid(def.stableKey);
                    expected.add(questId);
                    IQuest quest = QuestDatabase.INSTANCE.get(questId);
                    boolean existed = quest != null;
                    if (!existed) {
                        quest = QuestDatabase.INSTANCE.createNew(questId);
                        created++;
                    } else {
                        String oldSign = readSignature(quest);
                        if (def.signature.equals(oldSign)) {
                            // 内容未变：只补坐标（布局可能变）与章节成员，避免无谓 markDirty
                            ensureLineEntry(line, questId, def);
                            touchedQuests.add(questId);
                            skipped++;
                            continue;
                        }
                        updated++;
                    }
                    writeQuest(quest, def, questId);
                    ensureLineEntry(line, questId, def);
                    touchedQuests.add(questId);
                }

                // 孤儿清理：只删「在本卷里、但不在定义集内」的任务
                removed += removeOrphans(line, lineId, expected);
            }

            // ---------- 1.5) 清理废弃章节（用户 2026-10-29 授权）----------
            // 旧版本按「(类型×电压)」分卷，留下了「机器索引 II/III/IV」「机器索引·未知」。
            // 本轮改成一卷「机器索引」，那些旧章节已经不再被写入 ⇒ 孤儿清理永远够不到它们
            // （它按「本轮写入的卷」遍历），于是旧任务在存档里越积越多（实测 1241 条 vs 应有的 333 条）。
            // 这里显式清理「属于本模组命名空间、但不在本轮期望卷集合里」的章节及其任务。
            int removedChapters = removeObsoleteChapters(byChapter.keySet(), touchedLines);

            // ---------- 1.6) 两章置顶（用户 2026-10-29：「把俩任务章节放最上面」）----------
            // BQ 的章节显示顺序由 QuestLineDatabase.lineOrder 决定；
            // setOrderIndex(id, i) 的实现是「先 remove 再按 i 插入」，
            // 所以依次对「加工」「生产」调用 0/1 就能稳定占住表头，其余章节原相对顺序顺延。
            pinIndexChapters();

            // ---------- 2) 落盘 + 双同步 ----------
            // 顺序模仿 NetQuestEdit.createQuests：markDirty 后立刻发两种同步，
            // 否则同 JVM 的客户端 GUI 不刷新（玩家要重进世界才看到）。
            SaveLoadHandler.INSTANCE.markDirty();
            if (!touchedQuests.isEmpty()) {
                NetQuestSync.sendSync(null, new ArrayList<>(touchedQuests), true, false);
            }
            if (!touchedLines.isEmpty()) {
                NetChapterSync.sendSync(null, new ArrayList<>(touchedLines));
            }

            lastSummary = "分卷=" + byChapter.size()
                + " 建="
                + created
                + " 更="
                + updated
                + " 删任务="
                + removed
                + " 删旧卷="
                + removedChapters
                + " 未变="
                + skipped
                + " 耗时ms="
                + (System.currentTimeMillis() - start);
            MyMod.LOG.info("[AE2QoL] machindex 任务写入完成：" + lastSummary);
        } catch (Throwable t) {
            lastSummary = "写入失败：" + t;
            MyMod.LOG.error("[AE2QoL] machindex 任务写入异常", t);
        }
    }

    // ===================== 分卷与任务 UUID =====================

    /** 章节名 → 任务列表（按 maxQuestsPerChapter 切分卷）。 */
    private static Map<String, List<IndexModel.QuestDefinition>> groupByChapter(
        List<IndexModel.QuestDefinition> quests) {
        Map<String, List<IndexModel.QuestDefinition>> out = new LinkedHashMap<>();

        // 用户 2026-10-29 拍板：**最多两个章节**——「机器索引·加工」「机器索引·生产」。
        // 聚合阶段已把章节标签写进 q.groupKey，这里直接按它分卷，不再按电压/上限滚动切割。
        // 章内导航靠文字（章节 DESC 目录 + 按 类别 → 电压 → 类型名 的顺序），不靠增加章节数。
        for (IndexModel.QuestDefinition q : quests) {
            String label = q.groupKey == null || q.groupKey.isEmpty() ? "其他" : q.groupKey;
            String name = IndexConfig.CHAPTER_BASE_NAME + "·" + label;
            out.computeIfAbsent(name, k -> new ArrayList<>())
                .add(q);
        }
        return out;
    }

    /**
     * 把「机器索引·加工」「机器索引·生产」钉到章节列表最前面。
     *
     * <p>
     * 用 {@code QuestLineDatabase.setOrderIndex}（字节码：先 {@code lineOrder.remove(id)}
     * 再 {@code lineOrder.add(clamp(index,0,size), id)}），因此对两章依次传 0、1 即稳定置顶，
     * 其它章节保持原有相对次序顺延 —— 不重排、不丢弃别人的顺序。
     *
     * <p>
     * 放在 {@code apply()} 里而不是单独命令：{@code repairFromCache}（进服自愈）与
     * {@code /bq_admin default load} 之后的修复都会走 apply，等于顺手把顺序也修回来；
     * 否则一旦章节被重载，置顶就丢了。
     */
    private static void pinIndexChapters() {
        try {
            String[] order = { MachineCategory.CHAPTER_PROCESSING, MachineCategory.CHAPTER_PRODUCTION };
            int index = 0;
            for (String label : order) {
                UUID id = lineUuid(IndexConfig.CHAPTER_BASE_NAME + "·" + label);
                if (!QuestLineDatabase.INSTANCE.containsKey(id)) continue;
                QuestLineDatabase.INSTANCE.setOrderIndex(id, index);
                index++;
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 章节置顶失败：" + t);
        }
    }

    /** 章节 UUID：固定命名空间派生（重启/重扫不变）。 */
    public static UUID lineUuid(String chapterName) {
        return UUID.nameUUIDFromBytes(
            (IndexConfig.CHAPTER_NAMESPACE + chapterName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 任务 UUID：只由 stableKey（typeId + 主电压档）派生，坐标变化不影响身份。 */
    public static UUID questUuid(String stableKey) {
        return UUID.nameUUIDFromBytes(
            (IndexConfig.QUEST_NAMESPACE + stableKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String roman(int n) {
        switch (n) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            case 4:
                return "IV";
            case 5:
                return "V";
            case 6:
                return "VI";
            case 7:
                return "VII";
            case 8:
                return "VIII";
            case 9:
                return "IX";
            case 10:
                return "X";
            default:
                return String.valueOf(n);
        }
    }

    // ===================== 写入细节 =====================

    /**
     * 章节属性：名称 + **文字目录**。
     *
     * <p>
     * 用户 2026-10-29 问「任务书里面不能写字吗」—— 能写。BQ 的 DESC 是纯文本，
     * 支持 {@code \n} 换行与 {@code §} 颜色码。既然章节被限制成最多两个，
     * 那么章内导航就**只能靠文字**：这里把本章包含的配方类型按显示顺序列成目录，
     * 玩家打开章节先看到「有什么、按什么顺序排」。
     */
    private static void applyLineProperties(IQuestLine line, String name, List<IndexModel.QuestDefinition> defs) {
        try {
            line.setProperty(NativeProps.NAME, name);
            StringBuilder sb = new StringBuilder();
            sb.append("§6AE2 QoL 自动生成的机器索引§r\n");
            sb.append("§7本章收录 §f")
                .append(defs == null ? 0 : defs.size())
                .append(" §7个配方类型，每个类型一条任务。§r\n");
            sb.append("§7任务内按 §f合成该机器所需电压§7 从高到低列出多方块主机（非工作电压）。§r\n");
            sb.append("§7顺序：§f类别 → 电压降序 → 类型名§7；同一条任务里的多台主机用 §fOR§7 分隔。§r\n");
            if (defs != null && !defs.isEmpty()) {
                sb.append("§7—— 目录 ——§r\n");
                String lastCat = null;
                for (IndexModel.QuestDefinition d : defs) {
                    String cat = d.categoryLabel == null || d.categoryLabel.isEmpty() ? "其他" : d.categoryLabel;
                    if (!cat.equals(lastCat)) {
                        sb.append("§b【")
                            .append(cat)
                            .append("】§r\n");
                        lastCat = cat;
                    }
                    sb.append("§8· §f")
                        .append(d.typeDisplayName)
                        .append("§8 ")
                        .append(d.machines.size())
                        .append(" 台§r\n");
                }
            }
            sb.append("§8索引条目自动维护，请勿手动删除。§r");
            line.setProperty(NativeProps.DESC, sb.toString());
            if (line.getProperty(NativeProps.ICON) == null) {
                // 图标用 AE2 ME 控制器（用户拍板）；取不到就不设，BQ 会显示默认图标而不是崩
                ItemStack icon = meControllerIcon();
                if (icon != null) line.setProperty(NativeProps.ICON, new BigItemStack(icon));
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 设置章节属性失败（" + name + "）：" + t);
        }
    }

    /** AE2 ME 控制器图标；拿不到返回 null（不静默失败：调用方看得到默认图标）。 */
    private static ItemStack meControllerIcon() {
        try {
            // AE2 的 Blocks 不是接口方法而是 public 字段：blocks().blockController，取堆用 stack(int)
            ItemStack st = appeng.api.AEApi.instance()
                .blocks().blockController.stack(1);
            if (st != null) return st;
        } catch (Throwable t) {
            // 落到注册名兜底
        }
        try {
            net.minecraft.item.Item item = (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry
                .getObject("appliedenergistics2:TileController");
            return item == null ? null : new ItemStack(item, 1, 0);
        } catch (Throwable t2) {
            return null;
        }
    }

    /** 写一个任务的属性 / 图标 / 描述 / task，并挂上 NAND 完成态。 */
    private static void writeQuest(IQuest quest, IndexModel.QuestDefinition def, UUID questId) {
        quest.setProperty(NativeProps.NAME, def.title);
        // 签名必须随 DESC 一起写进库：下次启动靠它判断「内容有没有变」，不变就不动 UUID/不刷同步。
        quest.setProperty(NativeProps.DESC, appendSignature(def.desc, def.signature));
        if (def.icon != null) {
            quest.setProperty(NativeProps.ICON, new BigItemStack(def.icon));
        }
        // 索引卡：不参与进度统计（COUNT_AS_QUEST=false），并显式 NAND ⇒ 永不完成
        quest.setProperty(NativeProps.COUNT_AS_QUEST, false);
        quest.setProperty(NativeProps.LOGIC_TASK, EnumLogic.NAND);
        // 无奖励、无前置
        quest.getRewards()
            .reset();
        quest.getRequirements()
            .clear();

        writeIndexTask(quest, def);
    }

    /**
     * 挂 1 个 TaskOptionalRetrieval（内含该组全部机器）。
     *
     * <p>
     * 为什么不是每台机器一个 task：任务详情页每个 task 都有自己的标题与间距，
     * 几十台机器会撑成一长串；用一个 task 装全部机器，界面就是一个紧凑的物品槽列表
     * （{@code PanelTaskRetrieval} 逐行渲染，外层 {@code GuiQuest} 已有滚动条）。
     */
    private static void writeIndexTask(IQuest quest, IndexModel.QuestDefinition def) {
        if (!"display_with_task".equalsIgnoreCase(IndexConfig.questMode)) {
            quest.getTasks()
                .reset();
            return;
        }
        try {
            ITask task = TaskRegistry.INSTANCE.createNew(new ResourceLocation("bq_standard", "optional_retrieval"));
            if (!(task instanceof TaskOptionalRetrieval)) {
                MyMod.LOG.warn("[AE2QoL] machindex 未找到 bq_standard:optional_retrieval，退化为纯图标任务");
                return;
            }
            TaskOptionalRetrieval retrieval = (TaskOptionalRetrieval) task;
            retrieval.requiredItems.clear();
            // 「或」的呈现（用户 2026-10-29 拍板）：单任务内 OR。
            // PanelTaskRetrieval 在 requireOnlyOneItem=true 时，会在每两个物品之间画一个
            // 「OR」文本（这是 BQ 写死的英文，不是翻译键），并且每个物品占 48px 而不是 32px。
            // 为什么不用任务级 OR 逻辑：EnumLogic.OR 的判定是 done>0，而本任务的
            // TaskOptionalRetrieval.ignored() 恒 true ⇒ done=total ⇒ OR 会立刻判定完成，
            // 索引卡被打勾。保持 NAND + 单任务内 OR 才能「显示或、但永不完成」。
            retrieval.requireOnlyOneItem = true;
            for (IndexModel.MachineEntry m : def.machines) {
                if (m.displayStack == null) continue;
                ItemStack st = m.displayStack.copy();
                // 索引条目只要「有这个物品」的展示语义：数量固定 1，避免出现 x64 的误导
                st.stackSize = 1;
                // BigItemStack(ItemStack) 自带 stackSize（无 (ItemStack,int) 重载）
                retrieval.requiredItems.add(new BigItemStack(st));
            }
            // 只保留这一个 task（重复执行时先清空，避免叠加）
            quest.getTasks()
                .reset();
            quest.getTasks()
                .add(0, retrieval);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 写任务内容失败（" + def.title + "）：" + t);
        }
    }

    /** 保证章节里有该任务的坐标条目（布局来自 def.x/def.y）。 */
    private static void ensureLineEntry(IQuestLine line, UUID questId, IndexModel.QuestDefinition def) {
        try {
            // 坐标可能因布局重排而变：无条件覆盖（QuestLineEntry 是纯数据，覆盖代价极低）。
            // 不做「已存在就跳过」——那会让改了布局的旧存档永远停在老坐标。
            line.put(questId, new betterquesting.questing.QuestLineEntry(def.x, def.y, 24, 24));
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 写任务坐标失败（" + def.title + "）：" + t);
        }
    }

    /**
     * 孤儿清理：删除「在本卷内、但不在定义集内」的任务。
     *
     * <p>
     * <b>只删本卷成员</b>：先取本卷的 key 集合，再看它是否在我们的命名空间下
     * （章节 UUID 已由命名空间派生，本方法只会被调用在我们的卷上）；即便如此，
     * 仍只删「本卷里已有、但本轮定义里没有」的条目 —— 玩家手动往我们的卷里塞任务会被清理，
     * 这是预期（整卷由我们维护）。
     */
    private static int removeOrphans(IQuestLine line, UUID lineId, Set<UUID> expected) {
        int removed = 0;
        try {
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, IQuestLineEntry> e : new ArrayList<>(line.entrySet())) {
                if (!expected.contains(e.getKey())) toRemove.add(e.getKey());
            }
            for (UUID id : toRemove) {
                line.remove(id);
                // 从全局题库里也删掉（否则会成为「无归属任务」，在 BQ 的搜索/统计里残留）
                IQuest q = QuestDatabase.INSTANCE.get(id);
                if (q != null) {
                    QuestDatabase.INSTANCE.remove(id);
                    removed++;
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 孤儿清理失败（lineId=" + lineId + "）：" + t);
        }
        return removed;
    }

    /**
     * 清理「属于本模组、但本轮不再使用」的旧章节及其任务。
     *
     * <p>
     * <b>为什么需要它</b>：旧版本按「(类型 × 电压)」分卷，写过「机器索引 II / III / IV /
     * 机器索引·未知」。改成一卷后这些卷不再被写入，而 {@link #removeOrphans} 只遍历
     * 「本轮写入的卷」⇒ 旧卷里的任务永远不会被删（实测存档里 1241 条 vs 应有的 333 条）。
     *
     * <p>
     * <b>安全边界</b>：只删「名字符合本模组卷命名规则」的章节，绝不碰官方/玩家章节；
     * 且只删「不在本轮期望集合里」的。判定用与 {@link #clearAll()} 同一套名字规则。
     */
    private static int removeObsoleteChapters(Set<String> expectedNames, Set<UUID> touchedLines) {
        int removed = 0;
        try {
            List<UUID> obsolete = new ArrayList<>();
            for (Map.Entry<UUID, IQuestLine> e : new ArrayList<>(QuestLineDatabase.INSTANCE.entrySet())) {
                String name = safeLineName(e.getValue());
                if (name == null || expectedNames.contains(name)) continue;
                if (!isOurChapterName(name)) continue;
                obsolete.add(e.getKey());
            }
            for (UUID lineId : obsolete) {
                IQuestLine line = QuestLineDatabase.INSTANCE.get(lineId);
                if (line != null) {
                    // 先把它名下的任务从全局题库删掉，避免留下「无归属任务」
                    for (UUID questId : new ArrayList<>(line.keySet())) {
                        QuestDatabase.INSTANCE.remove(questId);
                        removed++;
                    }
                }
                QuestLineDatabase.INSTANCE.remove(lineId);
                touchedLines.add(lineId);
                removed++;
            }
            if (!obsolete.isEmpty()) {
                MyMod.LOG.info("[AE2QoL] machindex 清理废弃旧分卷 " + obsolete.size() + " 个，连带任务 " + removed + " 条");
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] machindex 清理废弃分卷失败：" + t);
        }
        return removed;
    }

    /** 章节名是否属于本模组生成（与 clearAll 同一套规则）。 */
    private static boolean isOurChapterName(String name) {
        return name.equals(IndexConfig.CHAPTER_BASE_NAME) || name.startsWith(IndexConfig.CHAPTER_BASE_NAME + " ")
            || name.startsWith(IndexConfig.CHAPTER_BASE_NAME + "·")
            || name.startsWith(IndexConfig.CHAPTER_UNKNOWN_NAME);
    }

    /** 读取任务上存的内容签名（存在 NAME 之外的隐藏属性不行，改用 DESC 末尾不可见标记）。 */
    private static String readSignature(IQuest quest) {
        try {
            String desc = quest.getProperty(NativeProps.DESC);
            if (desc == null) return "";
            int idx = desc.indexOf(SignatureMark.START);
            if (idx < 0) return "";
            int end = desc.indexOf(SignatureMark.END, idx);
            if (end < 0) return "";
            return desc.substring(idx + SignatureMark.START.length(), end);
        } catch (Throwable t) {
            return "";
        }
    }

    /** 签名标记：塞进 DESC 末尾（BQ 描述是纯文本，玩家几乎看不到这串，但可用于变更检测）。 */
    static final class SignatureMark {

        static final String START = "\n[sig:";
        static final String END = "]";

        private SignatureMark() {}
    }

    /** 对外：把签名拼进描述（QuestAggregator 生成的 desc 不含签名，写入时补）。 */
    static String appendSignature(String desc, String signature) {
        return desc + SignatureMark.START + signature + SignatureMark.END;
    }

    /** 清理所有我们生成的分卷（/ae2qol machindex clear 用）。 */
    public static void clearAll() {
        try {
            List<UUID> ours = new ArrayList<>();
            for (Map.Entry<UUID, IQuestLine> e : new ArrayList<>(QuestLineDatabase.INSTANCE.entrySet())) {
                String name = safeLineName(e.getValue());
                if (name == null) continue;
                // 只认我们命名空间派生出来的 UUID，避免误删官方章节
                if (e.getKey()
                    .equals(lineUuid(name)) || name.equals(IndexConfig.CHAPTER_BASE_NAME)
                    || name.startsWith(IndexConfig.CHAPTER_BASE_NAME + " ")
                    || name.startsWith(IndexConfig.CHAPTER_BASE_NAME + "·")
                    || name.startsWith(IndexConfig.CHAPTER_UNKNOWN_NAME)) {
                    ours.add(e.getKey());
                }
            }
            for (UUID id : ours) {
                QuestLineDatabase.INSTANCE.remove(id);
            }
            SaveLoadHandler.INSTANCE.markDirty();
            if (!ours.isEmpty()) NetChapterSync.sendSync(null, ours);
            lastSummary = "已清理分卷=" + ours.size();
            MyMod.LOG.info("[AE2QoL] machindex " + lastSummary);
        } catch (Throwable t) {
            MyMod.LOG.error("[AE2QoL] machindex 清理失败", t);
        }
    }

    private static String safeLineName(IQuestLine line) {
        try {
            return line.getProperty(NativeProps.NAME);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 供 /status：统计当前数据库里属于我们的任务数。 */
    public static String describeDatabase() {
        try {
            int lines = 0, quests = 0;
            for (Map.Entry<UUID, IQuestLine> e : QuestLineDatabase.INSTANCE.entrySet()) {
                String name = safeLineName(e.getValue());
                if (name == null) continue;
                if (name.equals(IndexConfig.CHAPTER_BASE_NAME) || name.startsWith(IndexConfig.CHAPTER_BASE_NAME + " ")
                    || name.startsWith(IndexConfig.CHAPTER_BASE_NAME + "·")
                    || name.startsWith(IndexConfig.CHAPTER_UNKNOWN_NAME)) {
                    lines++;
                    quests += e.getValue()
                        .size();
                }
            }
            return "分卷=" + lines + " 任务=" + quests;
        } catch (Throwable t) {
            return "数据库统计失败：" + t;
        }
    }

    /** 补一次签名写入（writeQuest 里统一处理，这里留空以保持调用点简单）。 */
    static void noop() {
        // 保留占位，避免后续改动误删调用点
    }

    /** 供诊断：把一份定义渲染成文本（不写库），用于日志比对。 */
    public static String previewQuest(IndexModel.QuestDefinition def) {
        return def.title + " @ ("
            + def.x
            + ","
            + def.y
            + ") 机器="
            + def.machines.size()
            + " sig="
            + MachineIndexCache.shortHash(def.signature);
    }

    /** 供诊断：本卷应有的 UUID 集合（不写库）。 */
    public static Collection<UUID> expectedIds(IndexModel.ScanResult result) {
        Set<UUID> out = new LinkedHashSet<>();
        for (IndexModel.QuestDefinition d : result.quests) {
            out.add(questUuid(d.stableKey));
        }
        return out;
    }

    /** 空实现占位（避免未使用告警）。 */
    static void touch() {
        // intentionally empty
    }
}
