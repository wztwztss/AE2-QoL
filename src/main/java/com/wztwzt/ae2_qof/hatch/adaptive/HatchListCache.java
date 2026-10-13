package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 3.32.0：列表口径从「一台仓一行」改成「**一台主机一行**」（用户口径：像 GTNL 能源监视器那样，
 * 看的是耗电的机器，而不是它身上插着的仓），同时保留「仓的额定/实测合计」两列。
 *
 * <p>为什么要改：旧列表一行是一台仓，名字取自仓的 {@code machineName}（而那个值又只在
 * 仓的 onFirstTick 里解析一次、失败就永久缓存成仓自己的名字 ⇒ 实测 481 行全叫「自适应电网仓」），
 * 右侧 EU/t 还是按网络配置**算出来的额定值**（{@code V[tier] × amps}），既不能回答"谁在吃电"，
 * 也不反映真实流量。现在：行=主机，主机名字/图标由客户端按 MTE id 本地化解析，
 * 主机耗电取 GT 自己的 {@code mEUt}（带符号），仓的额定与实测各自合计。
 */
public class HatchListCache {

    /** 命中的**仓**总数（与行数不是同一基数：行是主机数）。 */
    public final int totalCount;
    /** 主机行数。 */
    public final int hostCount;
    public final List<HatchEntry> entries;
    public final String inputCountText;
    public final String outputCountText;

    /**
     * 排序（3.32.0-fix3）：**先按实测流量降序**——真正在干活的机器排前面，待机的自然会沉到底部；
     * 实测相同再按 |主机 mEUt| 降序。
     *
     * <p>为什么不再按 mEUt 排：GT 的 {@code mEUt} 停机后**不清零**（残留上一次配方的值），
     * 用户实测到"最上面那台根本没工作却写着 -458,752,000 EU/t"。
     */
    public static final Comparator<HatchEntry> FLOW_DESC = (a, b) -> {
        int c = Long.compare(Math.abs(b.realFlowEUt), Math.abs(a.realFlowEUt));
        return c != 0 ? c : Long.compare(Math.abs(b.hostEut), Math.abs(a.hostEut));
    };

    public HatchListCache(int totalCount, int hostCount, List<HatchEntry> entries, String inputCountText,
                          String outputCountText) {
        entries.sort(FLOW_DESC);
        this.totalCount = totalCount;
        this.hostCount = hostCount;
        this.entries = Collections.unmodifiableList(entries);
        this.inputCountText = inputCountText;
        this.outputCountText = outputCountText;
    }

    /** 一行 = 一台主机（或"未识别主机"名下的一台仓）。 */
    public static class HatchEntry {

        /**
         * 客户端据此解析名称与画图标：**主机**物品的 MTE id；
         * 未识别主机时=该仓物品的 MTE id；-1 = 两者都取不到（客户端只显示占位文字）。
         */
        public final int displayMetaId;
        /** false = 未识别主机（不属于任何已注册多方块，或结构未成型）。 */
        public final boolean hostKnown;
        /**
         * 这台主机此刻是否真的在干活（GT {@code MTEMultiBlockBase.mMaxProgresstime > 0}）。
         * 3.32.0-fix3：**必须靠它决定要不要显示配方耗电**——GT 的 {@code mEUt} 停机后不清零，
         * 直接显示就会出现"没工作却写着 -458,752,000 EU/t"（用户实测）。
         */
        public final boolean running;
        /** 主机实时耗电 {@code MTEMultiBlockBase.mEUt}（带符号，消耗为负）；未识别主机为 0。**仅在 running 时有意义**。 */
        public final long hostEut;
        /** 本网络在这台主机上的仓数 / 其中输入类 / 输出类。 */
        public final int hatchCount;
        public final int inCount;
        public final int outCount;
        /** 各仓额定功率合计（{@code V[tier] × amps}，激光类 ×2）。 */
        public final long eut;
        /** 各仓实测流量合计。 */
        public final long realFlowEUt;
        public final int index;
        /** 主机坐标（未识别主机时=仓坐标）⇒【定位】按这对坐标寻址。 */
        public final int x, y, z, dim;
        public final String ownerName;
        /** 明细（tooltip）：每台仓的类型/档位/安培/额定/实测/坐标。 */
        public final List<Sub> subs;

        public HatchEntry(int displayMetaId, boolean hostKnown, boolean running, long hostEut, int hatchCount,
                          int inCount, int outCount, long eut, long realFlowEUt, int index, int x, int y, int z,
                          int dim, String ownerName, List<Sub> subs) {
            this.displayMetaId = displayMetaId;
            this.hostKnown = hostKnown;
            this.running = running;
            this.hostEut = hostEut;
            this.hatchCount = Math.max(0, hatchCount);
            this.inCount = Math.max(0, inCount);
            this.outCount = Math.max(0, outCount);
            this.eut = eut;
            this.realFlowEUt = realFlowEUt;
            this.index = index;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.ownerName = ownerName != null ? ownerName : "";
            this.subs = subs != null ? subs : Collections.<Sub>emptyList();
        }
    }

    /** 一行里的一台仓（tooltip 明细）。 */
    public static class Sub {

        /** 仓物品的 MTE id（客户端本地化名称 + 画图标）。 */
        public final int metaId;
        /** 0..3 = 本模组的四类；4 = TecTech 兼容激光仓。 */
        public final int hatchType;
        public final int tier;
        public final int amps;
        public final long eut;
        public final long realFlowEUt;
        public final int x, y, z, dim;
        public final boolean compat;

        public Sub(int metaId, int hatchType, int tier, int amps, long eut, long realFlowEUt, int x, int y, int z,
                   int dim, boolean compat) {
            this.metaId = metaId;
            this.hatchType = hatchType;
            this.tier = tier;
            this.amps = Math.max(1, amps);
            this.eut = eut;
            this.realFlowEUt = realFlowEUt;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.compat = compat;
        }
    }
}
