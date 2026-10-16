package com.wztwzt.ae2_qof.proline;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 产线（设计 §2.1 ProductionLine）。
 *
 * <h2>v6 关键拍板落点</h2>
 * <ul>
 * <li><b>networkId（O2）</b>：创建时刻锁定的 AE 网络；采样、展示、累计全部只认它。</li>
 * <li><b>pausedStepIds（A1）</b>：软暂停按 stepId 存，不按下标。</li>
 * <li><b>cumulative（A4）</b>：每物品 {输入, 输出} 两个 BigInteger 累计——
 * 计数器不随汇总口径重置（历史污染是明示取舍）；采样轮实时累加，写盘节流 60s。</li>
 * <li><b>速率环形缓冲（B7）</b>：内存态，不进 NBT，重启即丢。</li>
 * </ul>
 */
public final class ProductionLine {

    /** 每团队产线上限（O7：对齐采样节流口径 16 条）。 */
    public static final int MAX_LINES_PER_TEAM = 16;
    /** 每条产线步骤上限（防 NBT 无界增长）。 */
    public static final int MAX_STEPS = 64;

    public String id;
    public String name;
    /** 创建者所属 ServerUtilities 团队的成员标识（我们存**玩家 UUID 串**，团队解析交给 ProLineGate）。 */
    public String ownerUuid;
    /** 锁定的 AE 网络 id（创建者终端绑定网络的 id；仅展示口径，采样的网络锚点看下面四项）。 */
    public String networkId;
    /**
     * O2 锁定锚点的落法（v1）：创建时刻的终端 TE 坐标。
     * 每轮采样按这个坐标找到那台终端再取它的 grid——口径跟随**创建时刻的那台终端**，
     * 不随后续"谁打开界面"漂移。终端被拆走 → 本线显示"（口径失效）"。
     */
    public int terminalDim;
    public int terminalX;
    public int terminalY;
    public int terminalZ;
    public long createdAtMs;
    public final List<ProductionStep> steps = new ArrayList<>();
    public boolean linePaused;
    /** 软暂停步骤（按 id）。 */
    public final Set<String> pausedStepIds = new LinkedHashSet<>();

    /**
     * 累计计数：key → [累计输入, 累计输出]（BigInteger 与采样口径无关的加法安全）。
     * 每个（产线, 物品）一对——分类（总输入/总输出）由汇总器在展示时依据"主链无上游/无下游"判定。
     */
    public final Map<String, BigInteger[]> cumulative = new LinkedHashMap<>();

    /** 速率环形缓冲：key → 最近样本。10s 一个样本，环形上限 180 点=30 分钟。内存态。 */
    public final transient Map<String, DequeSample> samples = new LinkedHashMap<>();

    public ProductionLine() {
        this.id = newId();
        this.createdAtMs = System.currentTimeMillis();
    }

    public ProductionLine(String forcedId, long createdAt) {
        this.id = forcedId;
        this.createdAtMs = createdAt;
    }

    public static String newId() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 12);
    }

    public ProductionStep findStep(String stepId) {
        if (stepId == null || stepId.isEmpty()) return null;
        for (ProductionStep s : steps) if (s.id.equals(stepId)) return s;
        return null;
    }

    public String effectiveNetworkId() {
        return networkId == null ? "" : networkId;
    }

    public boolean isStepPausedByLineOrStep(ProductionStep s) {
        return linePaused || pausedStepIds.contains(s.id) || s.softPaused;
    }

    /** 供 GUI/采样用：当前生效的停用判定（整线或该步被软暂停）。 */
    public boolean isActiveStep(ProductionStep s) {
        return !isStepPausedByLineOrStep(s);
    }

    public void addCumulative(String key, long delta) {
        if (delta == 0) return;
        BigInteger[] twin = cumulative.get(key);
        if (twin == null) {
            twin = new BigInteger[] { BigInteger.ZERO, BigInteger.ZERO };
            cumulative.put(key, twin);
        }
        twin[delta < 0 ? 0 : 1] = twin[delta < 0 ? 0 : 1].add(BigInteger.valueOf(Math.abs(delta)));
    }

    public NBTTagCompound writeNbt(NBTTagCompound tag) {
        tag.setString("Id", id);
        tag.setString("Name", name == null ? "" : name);
        tag.setString("Owner", ownerUuid == null ? "" : ownerUuid);
        tag.setString("Net", effectiveNetworkId());
        tag.setInteger("TDim", terminalDim);
        tag.setInteger("TX", terminalX);
        tag.setInteger("TY", terminalY);
        tag.setInteger("TZ", terminalZ);
        tag.setLong("Created", createdAtMs);
        tag.setBoolean("Paused", linePaused);
        NBTTagList sl = new NBTTagList();
        for (ProductionStep s : steps) sl.appendTag(s.writeNbt(new NBTTagCompound()));
        tag.setTag("Steps", sl);
        StringBuilder paused = new StringBuilder();
        for (String pid : pausedStepIds) {
            if (paused.length() > 0) paused.append(',');
            paused.append(pid);
        }
        tag.setString("PausedIds", paused.toString());
        NBTTagList cl = new NBTTagList();
        for (Map.Entry<String, BigInteger[]> e : cumulative.entrySet()) {
            NBTTagCompound ct = new NBTTagCompound();
            ct.setString("Key", e.getKey());
            ct.setString("In", e.getValue()[0].toString());
            ct.setString("Out", e.getValue()[1].toString());
            cl.appendTag(ct);
        }
        tag.setTag("Cumu", cl);
        return tag;
    }

    /** 存档案读法（读到的字段一律兜底，坏条目跳过并计数，不抛异常）。 */
    public static ProductionLine readNbt(NBTTagCompound tag) {
        ProductionLine line = new ProductionLine(
            tag.getString("Id"),
            tag.hasKey("Created") ? tag.getLong("Created") : System.currentTimeMillis());
        line.name = tag.getString("Name");
        line.ownerUuid = tag.getString("Owner");
        line.networkId = tag.getString("Net");
        if (tag.hasKey("TDim")) {
            line.terminalDim = tag.getInteger("TDim");
            line.terminalX = tag.getInteger("TX");
            line.terminalY = tag.getInteger("TY");
            line.terminalZ = tag.getInteger("TZ");
        }
        line.linePaused = tag.getBoolean("Paused");
        line.steps.clear();
        NBTTagList sl = tag.getTagList("Steps", 10);
        for (int i = 0; i < sl.tagCount(); i++) {
            NBTTagCompound st = sl.getCompoundTagAt(i);
            if (st.hasKey("Id")) line.steps.add(ProductionStep.readNbt(st));
        }
        line.pausedStepIds.clear();
        for (String pid : tag.getString("PausedIds")
            .split(",")) {
            if (pid.length() > 0) line.pausedStepIds.add(pid);
        }
        line.cumulative.clear();
        NBTTagList cl = tag.getTagList("Cumu", 10);
        for (int i = 0; i < cl.tagCount(); i++) {
            NBTTagCompound ct = cl.getCompoundTagAt(i);
            if (ct.hasKey("Key")) {
                try {
                    line.cumulative.put(
                        ct.getString("Key"),
                        new BigInteger[] { new BigInteger(ct.getString("In")), new BigInteger(ct.getString("Out")) });
                } catch (NumberFormatException ignored) {}
            }
        }
        return line;
    }

    /** 速率样本环形 deque：容量满丢最老。 */
    public static final class DequeSample {

        public static final int CAP = 180;
        public final List<long[]> points = new ArrayList<>(); // [epochMs, stockLong]

        public void push(long t, long stock) {
            if (points.size() >= CAP) points.remove(0);
            points.add(new long[] { t, stock });
        }

        /** 最近窗口速率 = (首尾 stock 差)/(时间差)；样本数 ≥3 才有读数。 */
        public double recentRatePerSec() {
            int n = points.size();
            if (n < 3) return Double.NaN;
            long[] first = points.get(0);
            long[] last = points.get(n - 1);
            long dt = (last[0] - first[0]) / 1000;
            if (dt <= 0) return Double.NaN;
            return (double) (last[1] - first[1]) / dt;
        }
    }

    /** 团队成员资格临时视图（供 GUI/校验用；由 ProLineGate 写入）。 */
    public final transient Set<String> viewerUuids = new HashSet<>();
}
