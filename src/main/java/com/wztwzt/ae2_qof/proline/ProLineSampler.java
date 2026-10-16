package com.wztwzt.ae2_qof.proline;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.proline.ProductionLine.DequeSample;
import com.wztwzt.ae2_qof.terminal.StockMonitorTerminal;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Δ 采样服务（设计 §6.1；A3/B6 拍板落法）。
 *
 * <h2>口径（v6 安装在案）</h2>
 * <ul>
 * <li><b>10s/轮</b>，只采"打开过界面的活跃产线"（{@link ProLineManager#activeLineIds}，≤16）；</li>
 * <li><b>规格不允许逐物品查询（A3）</b>：每条活跃线一轮做<b>一次网络全表快照</b>：
 * 物品 = {@code 注册名@meta}，流体 = {@code fluid:名}，之后按键匹配；
 * 线的网络来源 = <b>创建时刻锚定的终端 TE</b>（O2：锁定锚点，不随"哪台终端打开"漂移；
 * 终端解析就是 {@link StockMonitorTerminal#resolveGrid()}），网络不在 shops 显示"—"；</li>
 * <li><b>读存量是 long</b>；BigInteger 只用于累计与平均；</li>
 * <li>Δ>0 计输出、Δ<0 计输入；样本进环形 180 点（30 分钟）；失败连 3 次退避 5 分钟（B6，warnOnce 去重）。</li>
 * <li>铁则：采样<b>只读</b>，绝不写 GT/AE；AeStockReader 的静默 catch 不复用——失败必打日志（坑 #6）。</li>
 * </ul>
 */
public final class ProLineSampler {

    private static final long INTERVAL_MS = 10_000L;
    private static final long LINE_BUDGET_MS = 3L;
    private static final long BACKOFF_MS = 5 * 60_000L;
    private static final int MAX_MACHINES_PER_ROUND = 128;

    private static long nextRound = 0L;
    private static final Map<String, Long> BACKOFF = new HashMap<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return; // 只在 END 处理一次
        long now = System.currentTimeMillis();
        if (now < nextRound) return;
        nextRound = now + INTERVAL_MS;

        WorldServer w = ProLineData.anyLoadedServerWorld();
        if (w == null) return;
        ProLineData data = ProLineData.get(w);
        int machinesThisRound = 0;

        java.util.List<String> active = ProLineManager.activeLineIds();
        // 长期驻留审计：非活跃/已删除产线的退避与连败表立即清理（防多轮记忆无界增长）
        for (String stale : BACKOFF.keySet()
            .toArray(new String[0])) {
            if (!active.contains(stale)) {
                BACKOFF.remove(stale);
                streak.remove(stale);
            }
        }
        for (String lineId : active) {
            Long ban = BACKOFF.get(lineId);
            if (ban != null) {
                if (now < ban) continue;
                BACKOFF.remove(lineId);
            }
            ProductionLine line = data.findLine(lineId);
            if (line == null) continue; // 已被删：活跃表下一轮由 GUI 触碰自愈

            long t0 = System.nanoTime() / 1_000_000L;
            boolean ok = false;
            try {
                ok = sampleStock(line, now);
                if (ok && machinesThisRound < MAX_MACHINES_PER_ROUND) {
                    machinesThisRound += sampleMachines(line, now);
                }
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 产线 {} 采样异常（B6 退避计数 +1）", lineId, t);
            }
            long took = System.nanoTime() / 1_000_000L - t0;
            if (ok && took <= LINE_BUDGET_MS) {
                streak.remove(lineId); // 正常：清零连败
            } else {
                markFail(lineId, now, took);
            }
        }
    }

    private final Map<String, Integer> streak = new HashMap<>();

    private void markFail(String lineId, long now, long took) {
        int n = streak.merge(lineId, 1, Integer::sum);
        if (n >= 3) {
            BACKOFF.put(lineId, now + BACKOFF_MS);
            streak.remove(lineId);
            com.wztwzt.ae2_qof.network.ServerTerminalHelper.warnOnce(
                "proline:sampler-misbehaving",
                "产线 " + lineId
                    + " 连续失败 "
                    + n
                    + " 次（"
                    + (took > LINE_BUDGET_MS ? "耗时 " + took + "ms 超预算" : "异常")
                    + "）⇒ 暂停采样 5 分钟");
        }
    }

    /** 累计与样本环形缓存；返回 false = 本线本轮网络不可用（不累计，但机器读数照常）。 */
    private boolean sampleStock(ProductionLine line, long nowMs) {
        // 网络来源 = 创建时锚定的终端 TE（O2 · v1 落法）
        WorldServer world = DimensionManager.getWorld(line.terminalDim);
        TileEntity te = null;
        if (world != null && world.blockExists(line.terminalX, line.terminalY, line.terminalZ)) {
            try {
                te = world.getTileEntity(line.terminalX, line.terminalY, line.terminalZ);
            } catch (Throwable t) {
                com.wztwzt.ae2_qof.network.ServerTerminalHelper
                    .warnOnce("proline:terminal-te-read", "锚定终端 TE 读取失败（可能与区块卸载竞态有关）：" + t);
            }
        }
        IGrid grid = null;
        // 终端是 MTE（挂在 IGregTechTileEntity 上），不是 TileEntity 本体——先取 MTE 再判型
        if (te instanceof IGregTechTileEntity
            && ((IGregTechTileEntity) te).getMetaTileEntity() instanceof StockMonitorTerminal) {
            try {
                grid = ((StockMonitorTerminal) ((IGregTechTileEntity) te).getMetaTileEntity()).resolveGrid();
            } catch (Throwable t) {
                MyMod.LOG.info("[AE2QoL] 产线 {} 锚定终端网格解析失败", line.id, t);
            }
        }
        if (grid == null) return false; // 显示"—"口径
        IStorageGrid storage = grid.getCache(IStorageGrid.class);
        if (storage == null) return false;

        // 一次全表快照 → key 表（A3 核心）
        Map<String, Long> stocks = new HashMap<>();
        try {
            for (appeng.api.storage.data.IAEItemStack is : storage.getItemInventory()
                .getStorageList()) {
                if (is == null || is.getItemStack() == null) continue;
                stocks.put(ItemRow.keyOf(is.getItemStack()), is.getStackSize());
            }
            for (appeng.api.storage.data.IAEFluidStack fs : storage.getFluidInventory()
                .getStorageList()) {
                if (fs == null || fs.getFluidStack() == null
                    || fs.getFluidStack()
                        .getFluid() == null)
                    continue;
                stocks.put(
                    "fluid:" + fs.getFluidStack()
                        .getFluid()
                        .getName(),
                    fs.getStackSize());
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 产线 {} AE 快照失败", line.id, t);
            return false;
        }

        // 采样目标 = 汇总口径 key 集（v1 不分 branch，branch chips 只过滤视图——已知偏差登记在效果说明）
        Map<String, long[]> targets = ProLineSummary.externalKeys(line);
        for (Map.Entry<String, long[]> e : targets.entrySet()) {
            String key = e.getKey();
            long stock = stocks.containsKey(key) ? stocks.get(key) : 0L;
            DequeSample dq = line.samples.get(key);
            if (dq == null) {
                dq = new DequeSample();
                line.samples.put(key, dq);
            }
            if (!dq.points.isEmpty()) {
                long prev = dq.points.get(dq.points.size() - 1)[1];
                long delta = stock - prev;
                line.addCumulative(key, delta);
            }
            dq.push(nowMs, stock);
        }
        return true;
    }

    /** 机器运行态读数（只读，坑 #22 的反例在这里用不到——不查 grid，直接按坐标读 TE）。 */
    private int sampleMachines(ProductionLine line, long nowMs) {
        Map<String, int[]> perStep = new LinkedHashMap<>();
        int scanned = 0;
        for (ProductionStep s : line.steps) {
            int running = 0;
            for (MachineRef ref : s.machines) {
                if (scanned >= MAX_MACHINES_PER_ROUND) continue;
                scanned++;
                WorldServer world = DimensionManager.getWorld(ref.dim);
                if (world == null || !world.blockExists(ref.x, ref.y, ref.z)) continue;
                TileEntity te = null;
                try {
                    te = world.getTileEntity(ref.x, ref.y, ref.z);
                } catch (Throwable t) {
                    com.wztwzt.ae2_qof.network.ServerTerminalHelper
                        .warnOnce("proline:machine-te-read", "绑定机器 TE 读取失败（可能与区块卸载竞态有关）：" + t);
                }
                if (te instanceof IGregTechTileEntity) {
                    ref.lastSeenEpochMs = nowMs;
                    gregtech.api.interfaces.metatileentity.IMetaTileEntity mte = ((IGregTechTileEntity) te)
                        .getMetaTileEntity();
                    if (mte instanceof MTEMultiBlockBase && ((MTEMultiBlockBase) mte).mMaxProgresstime > 0) {
                        running++;
                    }
                    // 真暂停自愈（2026-10-16 拍板）：机器复活/换新后把停机位按线的暂停口径重新压上
                    // （这一条是采样循环里唯一允许的写，且只在"该步确被暂停"时执行）
                    if (line.isStepPausedByLineOrStep(s)
                        && te instanceof gregtech.api.metatileentity.BaseMetaTileEntity) {
                        ((gregtech.api.metatileentity.BaseMetaTileEntity) te).disableWorking();
                    }
                }
            }
            perStep.put(s.id, new int[] { running, s.machines.size() });
        }
        ProLineManager.putRunState(line.id, perStep);
        return scanned;
    }

    /** tick 事件在 cpw 总线（1.7.10 ServerTickEvent 所属）。 */
    public static void register() {
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new ProLineSampler());
    }
}
