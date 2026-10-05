package com.wztwzt.ae2_qof.hatch.thread;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.wztwzt.ae2_qof.hatch.AE2MaintenanceHatchUniversal;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;

/**
 * 3.25.0「线程引擎」：让一台多方块**在同一 tick 内跑 N 条互相独立的线程**，每条线程各自匹配配方、
 * 各自计时、各自扣料与落地产物（用户口径：TST 式；只匹配到一个配方时就是 N 份相同配方，每份各有自己的并行）。
 *
 * <h2>为什么必须把状态放在机器之外</h2>
 * GT 的机器只有**一套**进度字段（{@code mProgresstime/mMaxProgresstime/mEUt/mOutputItems}，见
 * {@code MTEMultiBlockBase.runMachine()} 与 {@code checkProcessing()}），所以"N 条线程各自进度"只能由本引擎
 * 在机器之外维护；机器那一套字段被我们当成"外壳（envelope）"：时长 = 最长线程的剩余、耗电 = 活跃线程求和、
 * 产物留空（产物由 {@code MixinMTEMultiBlockBase} 在每条线程完成时用 GT 自己的
 * {@code addItemOutputs/addFluidOutputs} 落地，避免与 {@code mOutputItems} 重复结算）。
 *
 * <h2>本类只做"数据 + 策略"</h2>
 * 所有需要碰 GT 内部成员的动作（调用原始 {@code doCheckRecipe()}、读 {@code processingLogic}、
 * 落地产物、改 {@code mEUt/mMaxProgresstime}）都在 {@code MixinMTEMultiBlockBase} 里做，
 * 这样本类不依赖任何 GT 内部可见性，也方便 UI（维护仓「线程」页 / WAILA）直接读快照。
 *
 * <h2>开关（用户拍板）</h2>
 * **以"该机器装了我们的万能维护仓、且线程数 &gt; 1"为唯一开关**：不满足时本引擎不接管，机器完全走 GT 原生行为，
 * 因此随时可逆、也便于二分排查。
 */
public final class Ae2qolThreadEngine {

    /** 线程数硬上限（与维护仓 THREAD_MAX 顶部一致）。 */
    public static final int MAX_THREADS = 64;

    // 线程状态（UI 文案见 lang：ae2_qof.threads.state.*）
    public static final int ST_IDLE = 0;
    public static final int ST_RUNNING = 1;
    public static final int ST_STARVED = 2;
    public static final int ST_POWER = 3;
    public static final int ST_OUTPUT_FULL = 4;
    public static final int ST_OFF = 5;

    /** 每台机器一份引擎：弱引用键，机器被回收/区块卸载后自动消失。 */
    private static final Map<MTEMultiBlockBase, Ae2qolThreadEngine> ENGINES = new WeakHashMap<>();

    /**
     * 维护仓 → 主机 的反查表。
     * <p>为什么需要：线程状态挂在**主机**（{@code MTEMultiBlockBase}）上，而设置线程数的界面在**维护仓**上，
     * 维护仓在 GT 里没有指向主机的稳定后向引用（{@code MTEMultiBlockBase.mMaintenanceHatches} 是单向的）。
     * 于是在建引擎时顺手记一条反查（主机侧的 {@code doCheckRecipe/incrementProgressTime} 注入会周期性建到），
     * GUI/WAILA 就能从维护仓找到主机。
     */
    private static final Map<MTEHatchMaintenance, MTEMultiBlockBase> HATCH_TO_MACHINE = new WeakHashMap<>();

    public static Ae2qolThreadEngine get(MTEMultiBlockBase machine) {
        Ae2qolThreadEngine engine;
        synchronized (ENGINES) {
            engine = ENGINES.computeIfAbsent(machine, Ae2qolThreadEngine::new);
        }
        // 反查表：顺带建立（维护仓可能被换掉，所以每次 get 都刷新一遍）
        AE2MaintenanceHatchUniversal hatch = engine.hatch();
        if (hatch != null) {
            synchronized (HATCH_TO_MACHINE) {
                HATCH_TO_MACHINE.put(hatch, machine);
            }
        }
        return engine;
    }

    /** 从维护仓找主机（GUI 用；找不到返回 null ⇒ 界面显示 0/未运行）。 */
    public static MTEMultiBlockBase machineOf(MTEHatchMaintenance hatch) {
        synchronized (HATCH_TO_MACHINE) {
            return HATCH_TO_MACHINE.get(hatch);
        }
    }

    /** 从维护仓找引擎（GUI 用；可能为 null）。 */
    public static Ae2qolThreadEngine forHatch(MTEHatchMaintenance hatch) {
        MTEMultiBlockBase machine = machineOf(hatch);
        return machine == null ? null : peek(machine);
    }

    /**
     * G6（3.25.0-fix4）：中央 ticker 用 —— 取所有引擎的**快照**（防并发修改）。
     * <p>为什么需要：NH-Utilities 等加速机制会**跳过 {@code updateEntity}**，
     * 挂在"机器 tick"上的广播会稀疏甚至停发；改由 server tick 统一驱动才稳。
     */
    public static java.util.List<Ae2qolThreadEngine> allEngines() {
        synchronized (ENGINES) {
            return new java.util.ArrayList<>(ENGINES.values());
        }
    }

    /** 该引擎对应的机器（中央 ticker 发送时需要）。 */
    public MTEMultiBlockBase machine() {
        return machine;
    }

    /** 只在已经存在时取（tick 热路径用，避免为不需要的机器建表）。 */
    public static Ae2qolThreadEngine peek(MTEMultiBlockBase machine) {
        synchronized (ENGINES) {
            return ENGINES.get(machine);
        }
    }

    /** 一条线程的状态。 */
    public static final class Slot {

        public int state = ST_IDLE;
        /** 本次配方的总时长（tick）与剩余。 */
        public int total;
        public int remain;
        /** 该线程本次的并行数（用户要求：每条线程各有自己的并行）。 */
        public int parallel;
        /** 该线程每 tick 耗电（正数，写回 mEUt 时取负）。 */
        public int eutPerTick;
        /** 显示用：主产物图标。 */
        public ItemStack icon;
        /** 完成时要落地的产物（由 GT 自己的 helper 算出，引擎不自行发明）。 */
        public ItemStack[] items;
        public net.minecraftforge.fluids.FluidStack[] fluids;

        public boolean isRunning() {
            return state == ST_RUNNING && remain > 0;
        }

        public int percent() {
            if (total <= 0) return 0;
            int done = total - Math.max(0, remain);
            return Math.max(0, Math.min(100, (int) ((long) done * 100 / total)));
        }

        /** 跨包也会用（注入层在 mixin 包里）⇒ 必须 public。 */
        public void clearRun() {
            state = ST_IDLE;
            total = 0;
            remain = 0;
            parallel = 0;
            eutPerTick = 0;
            icon = null;
            items = null;
            fluids = null;
        }
    }

    private final MTEMultiBlockBase machine;
    private final Slot[] slots = new Slot[MAX_THREADS];

    /** "正在由引擎回调 GT 原逻辑"标志：防止递归进入引擎（见 MixinMTEMultiBlockBase 的 HEAD 注入）。 */
    private boolean computing;

    /** 降频广播计数器（每 10 tick 发一次 WAILA 用的线程状态小包，见 ThreadStatusBroadcaster）。 */
    public int broadcastCounter;

    /**
     * 线程起步的**轮转游标**（3.25.0-fix6）。
     * <p>为什么需要：越早的"空闲槽补位"是"从 0 找第一个空闲槽、失败就 break"，
     * 于是一个**持续失败**的槽（例如刚完成那条线程的槽）会把后面所有空闲槽永远挡在门外
     * ⇒ 用户实测"设了 16 线程却只跑 1 条"。改成轮转后，每 tick 从上次的下一个槽继续试，
     * 坏槽最多只消耗一次尝试，不会阻塞其余线程。
     */
    public int startCursor;

    private Ae2qolThreadEngine(MTEMultiBlockBase machine) {
        this.machine = machine;
        for (int i = 0; i < MAX_THREADS; i++) {
            slots[i] = new Slot();
        }
    }

    // ===================== 开关与线程数 =====================

    /** 找到该机器里我们的万能维护仓（没有就说明线程功能不适用）。 */
    public AE2MaintenanceHatchUniversal hatch() {
        for (MTEHatchMaintenance hatch : machine.mMaintenanceHatches) {
            if (hatch instanceof AE2MaintenanceHatchUniversal universal) {
                return universal;
            }
        }
        return null;
    }

    /** 用户拍板：装了我们的维护仓且线程 &gt; 1 才启用。 */
    public boolean isEnabled() {
        AE2MaintenanceHatchUniversal hatch = hatch();
        return hatch != null && hatch.getEffectiveThreads() > 1;
    }

    /** 本次要维护的线程数（实时读维护仓，玩家改了立即生效）。 */
    public int threadCount() {
        AE2MaintenanceHatchUniversal hatch = hatch();
        if (hatch == null) return 1;
        return Math.max(1, Math.min(MAX_THREADS, hatch.getEffectiveThreads()));
    }

    public Slot slot(int index) {
        return slots[Math.max(0, Math.min(MAX_THREADS - 1, index))];
    }

    // ===================== 计算期开关 =====================

    public boolean isComputing() {
        return computing;
    }

    public void beginComputing() {
        computing = true;
    }

    public void endComputing() {
        computing = false;
    }

    // ===================== 汇总（UI / WAILA / 电力策略共用） =====================

    public int activeCount() {
        int n = 0;
        for (int i = 0; i < threadCount(); i++) {
            if (slots[i].isRunning()) n++;
        }
        return n;
    }

    /** 活跃线程里最长的剩余时间：用作外壳时长（保证机器在所有线程跑完前不会结算）。 */
    public int maxRemain() {
        int max = 0;
        for (int i = 0; i < threadCount(); i++) {
            if (slots[i].isRunning()) max = Math.max(max, slots[i].remain);
        }
        return max;
    }

    /** 活跃线程每 tick 总耗电（正数）。 */
    public long totalEutPerTick() {
        long sum = 0;
        for (int i = 0; i < threadCount(); i++) {
            if (slots[i].isRunning()) sum += slots[i].eutPerTick;
        }
        return sum;
    }

    public long totalParallel() {
        long sum = 0;
        for (int i = 0; i < threadCount(); i++) {
            if (slots[i].isRunning()) sum += slots[i].parallel;
        }
        return sum;
    }

    public int countState(int state) {
        int n = 0;
        for (int i = 0; i < threadCount(); i++) {
            if (slots[i].state == state) n++;
        }
        return n;
    }

    /**
     * 用户口径：**电力不够时自动降线程**（而不是整台停机）。
     * 从尾部开始把装不下的活跃线程降级为 {@link #ST_POWER}；返回被降级的条数。
     */
    public int applyPowerLimit(long availableEU) {
        int dropped = 0;
        int count = threadCount();
        while (true) {
            long need = totalEutPerTick();
            if (need <= availableEU) break;
            int victim = -1;
            for (int i = count - 1; i >= 0; i--) {
                if (slots[i].isRunning()) {
                    victim = i;
                    break;
                }
            }
            if (victim < 0) break;
            Slot s = slots[victim];
            s.state = ST_POWER;
            s.remain = 0;
            s.parallel = 0;
            s.eutPerTick = 0;
            dropped++;
        }
        return dropped;
    }

    // ================= 3.25.0-fix12：电力预算闸（替换上面用错数据源的旧逻辑） =================

    /** 上一次记下的"能源仓能力 EU/t"（`getMaxInputEu()`）。 */
    public long lastBudget = -1;
    /** 是否已因电力不足暂停"起步新线程"（直到预算变大才解除）。 */
    public boolean powerBlocked = false;
    /** 触发暂停时的预算值（预算变大即解除，避免每 tick 反复起/降造成机器状态闪"电力不足"）。 */
    public long blockedAtBudget = -1;

    /**
     * 电力预算闸（每 tick 一次，**在推进/起步之前**调用）。
     * <p>旧实现的病根：用 {@code base.getStoredEU()}（机器内部缓存）当"可用电力"，而 GT 多方块
     * 平时根本不缓存 EU（每 tick 直接能源仓拉走）⇒ 恒为 0 ⇒ 每条线程一起步就被降级，
     * 且每 1~2 秒在"降级/恢复"间横跳，机器状态一直闪"电力不足"。
     * <p>现在：① 预算 = {@code getMaxInputEu()}（能源仓能提供的最大 EU/t，GT 自己的口径）；
     * ② **只在确有线程在跑且超预算时**才从尾部降级；③ 预算变好才解除封锁并让被暂停的槽重新参与。
     *
     * @return 本次因预算不足被降级的线程条数（供日志）
     */
    public int updatePowerBudget(long budget) {
        // 安全阀（fix12）：预算获取失败/为 0 时**不做任何电力干预** ——
        // 宁可让 GT 自己去报"电力不足"，也不能因为我们读不到能源仓就把线程全锁死。
        if (budget <= 0) {
            lastBudget = budget;
            return 0;
        }
        if (lastBudget < 0 || budget > lastBudget) {
            if (powerBlocked) {
                for (int i = 0; i < threadCount(); i++) {
                    if (slots[i].state == ST_POWER) {
                        slots[i].clearRun();
                        slots[i].state = ST_IDLE;
                    }
                }
                powerBlocked = false;
                blockedAtBudget = -1;
            }
        }
        lastBudget = budget;
        int dropped = 0;
        while (totalEutPerTick() > budget) {
            int victim = -1;
            for (int i = threadCount() - 1; i >= 0; i--) {
                if (slots[i].isRunning()) {
                    victim = i;
                    break;
                }
            }
            if (victim < 0) break;
            Slot s = slots[victim];
            s.state = ST_POWER;
            s.remain = 0;
            s.parallel = 0;
            s.eutPerTick = 0;
            dropped++;
        }
        return dropped;
    }

    /** 起步闸：预算已被吃满或已封锁时，本 tick 不再起步新线程（预算未知/为 0 时**不拦**）。 */
    public boolean powerAllowsMore(long budget) {
        if (budget <= 0) return true;
        if (powerBlocked) return false;
        long need = totalEutPerTick();
        return need > 0 && need < budget || need == 0 && budget > 0;
    }

    /** 刚起步的那条若把总量推过预算 ⇒ 把它退回暂停并封锁后续起步（直到预算变大）。 */
    public void noteOvershoot(long budget, int slotIndex) {
        if (budget <= 0) return; // 预算未知 ⇒ 不干预
        if (totalEutPerTick() <= budget) return;
        if (slotIndex >= 0 && slotIndex < slots.length) {
            Slot s = slots[slotIndex];
            s.state = ST_POWER;
            s.remain = 0;
            s.parallel = 0;
            s.eutPerTick = 0;
        }
        powerBlocked = true;
        blockedAtBudget = budget;
    }

    /** 维护仓被拆掉或线程数改回 1 时，清空全部线程状态（回到 GT 原生行为）。 */
    public void clearAll() {
        for (Slot slot : slots) {
            slot.clearRun();
        }
    }

    /** 只保留 0..threadCount-1 之外的线程状态（线程数被调小）。 */
    public void trimToThreadCount() {
        int count = threadCount();
        for (int i = count; i < MAX_THREADS; i++) {
            if (slots[i].state != ST_IDLE) slots[i].clearRun();
        }
    }

    // ===================== UI 快照 =====================

    /** 给 UI/WAILA 的一行（纯值对象，便于序列化/比较）。 */
    public static final class Row {

        public final int index;
        public final int state;
        public final int percent;
        public final int remain;
        public final int parallel;
        public final ItemStack icon;
        /**
         * G4（3.25.0-fix4）：**纯流体配方**用它画"真流体图标"（GUI 走 MUI2 的
         * {@code com.cleanroommc.modularui.drawable.FluidDrawable}）。
         * 口径：**物品优先**，没有物品输出才用流体；两者都没有就留空位。
         */
        public final net.minecraftforge.fluids.FluidStack fluid;

        /**
         * 维护仓里**设定的并行数**（3.25.0-fix7）：用于把并行口径讲清楚 ——
         * GT 的 {@code getCurrentParallels()}（= {@link #parallel}）是**实际执行数**，
         * 实测 = 设定值 × 该机器的批处理倍数（蒸馏塔：设定 1000 → 实际 64000，即 ×64；
         * 溶解罐：设定 4096 → 被输入/电力上限压到 106）。
         */
        public final int setting;

        public Row(int index, int state, int percent, int remain, int parallel, ItemStack icon,
            net.minecraftforge.fluids.FluidStack fluid, int setting) {
            this.index = index;
            this.state = state;
            this.percent = percent;
            this.remain = remain;
            this.parallel = parallel;
            this.icon = icon == null ? null : icon.copy();
            this.fluid = fluid == null ? null : fluid.copy();
            this.setting = setting;
        }

        /** 值语义：MUI2 列表同步器要求 equals/hashCode，否则每 tick 都会判定"变了"而狂发包。 */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Row other)) return false;
            return index == other.index && state == other.state
                && percent == other.percent
                && remain == other.remain
                && parallel == other.parallel
                && ItemStack.areItemStacksEqual(icon, other.icon)
                && (fluid == null ? other.fluid == null : fluid.isFluidEqual(other.fluid))
                && setting == other.setting;
        }

        @Override
        public int hashCode() {
            int h = index;
            h = 31 * h + state;
            h = 31 * h + percent;
            h = 31 * h + remain;
            h = 31 * h + parallel;
            h = 31 * h + (icon == null ? 0 : icon.getItemDamageForDisplay() ^ icon.stackSize);
            h = 31 * h + (fluid == null ? 0 : fluid.getFluid()
                .hashCode() ^ fluid.amount);
            h = 31 * h + setting;
            return h;
        }

        /**
         * 写给 MUI2 的 {@code GenericListSyncHandler}（服务端快照 → 客户端重建控件）。
         * 注意：这个同步器要的是 **PacketBuffer** 形式（与库存统计终端的 Row 写法一致），不是 NBT。
         */
        public static void write(net.minecraft.network.PacketBuffer buf, Row row) throws java.io.IOException {
            buf.writeInt(row.index);
            buf.writeInt(row.state);
            buf.writeInt(row.percent);
            buf.writeInt(row.remain);
            buf.writeInt(row.parallel);
            buf.writeBoolean(row.icon != null);
            if (row.icon != null) {
                buf.writeItemStackToBuffer(row.icon);
            }
            buf.writeBoolean(row.fluid != null);
            if (row.fluid != null) {
                buf.writeNBTTagCompoundToBuffer(row.fluid.writeToNBT(new NBTTagCompound()));
            }
            buf.writeInt(row.setting);
        }

        public static Row read(net.minecraft.network.PacketBuffer buf) throws java.io.IOException {
            int index = buf.readInt();
            int state = buf.readInt();
            int percent = buf.readInt();
            int remain = buf.readInt();
            int parallel = buf.readInt();
            ItemStack icon = buf.readBoolean() ? buf.readItemStackFromBuffer() : null;
            net.minecraftforge.fluids.FluidStack fluid = buf.readBoolean()
                ? net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(buf.readNBTTagCompoundFromBuffer())
                : null;
            int setting = buf.readInt();
            return new Row(index, state, percent, remain, parallel, icon, fluid, setting);
        }
    }

    /** 只列活跃线程（用户口径：空闲折叠成一行计数）。 */
    public List<Row> activeRows() {
        List<Row> rows = new ArrayList<>();
        int count = threadCount();
        for (int i = 0; i < count; i++) {
            Slot s = slots[i];
            if (s.isRunning() || s.state == ST_OUTPUT_FULL) {
                // G4：物品优先；纯流体配方（如蒸馏水）没有物品，就用第一个流体输出画图标
                net.minecraftforge.fluids.FluidStack fluid = null;
                if (s.icon == null && s.fluids != null && s.fluids.length > 0) {
                    fluid = s.fluids[0];
                }
                rows.add(
                    new Row(i + 1, s.state, s.percent(), s.remain, s.parallel, s.icon, fluid, parallelSetting()));
            }
        }
        return rows;
    }

    /** 维护仓里设定的并行数（GUI 用它把「设定 × 批处理 = 实际」讲清楚）。 */
    public int parallelSetting() {
        AE2MaintenanceHatchUniversal hatch = hatch();
        return hatch == null ? 0 : hatch.getEffectiveParallel();
    }
}
