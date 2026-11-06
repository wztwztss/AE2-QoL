package com.wztwzt.ae2_qof.cover.stockmonitor.ae;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.NetworkItemList;

/**
 * AE2 网络库存读取（4.5.0-fix10 性能版：跨盖板共享物化快照）。
 *
 * <h2>这版解决什么</h2>
 * 4.2.0-fix2 的批量读取把「每槽各查一次」压成了「一次物化 + 哈希查找」，但每次
 * {@code readStocks} 仍然会对 {@code NetworkItemList} 调一次 {@code buildFinalItemList()}。
 * 实测（spark SsMNj7bsBU / ZEAsUk3fja）：
 * <ul>
 * <li>单个 ME 网络上挂了 44 块库存监控盖板，全部 20t 读一次 ⇒ 平均 2.2 次读取/tick；</li>
 * <li>每次读取都要重新物化整张表（约 2ms 常态、14ms 于慢 tick），摊下来
 * {@code StockMonitorCover.doCoverThings} 常态 4.5ms/tick；</li>
 * <li>真正的大头（44ms 的 {@code NetworkMonitor.getStorageList} 全量重建）由 AE2 懒缓存
 * 在「网络变化后第一次被读取」时触发，盖板只是第一个来要清单的人。</li>
 * </ul>
 * 本版把「物化结果」按 {@link IGrid} 缓存 {@value #CACHE_TTL_MS} 毫秒：同一网络上的 44 块盖板
 * 共享同一份物化表，一个刷新窗口内只物化一次。数据新鲜度仍 ≤ 一个刷新周期（默认 20t），
 * 与旧行为一致；GUI 打开时 5t 刷新，TTL 250ms ≈ 5t，不会读到过旧数据。
 *
 * <h2>不改什么</h2>
 * 不改变数值语义、不改网络结构、不引入监听器生命周期。缓存是纯时间 TTL，最坏情况是
 * 「网络刚变化 + 恰好命中缓存」时数字最多晚 250ms 更新——比现在每块盖板各自 1s 刷新更快。
 */
public final class AeStockReader {

    /** 物化快照的有效期（毫秒）。250ms ≈ 5 tick，与 GUI 打开时的 5t 刷新对齐。 */
    private static final long CACHE_TTL_MS = 250L;

    private static final class Snapshot {

        final long builtAt;
        final IItemList<IAEItemStack> items;
        final Map<Integer, Long> fluids;

        Snapshot(long builtAt, IItemList<IAEItemStack> items, Map<Integer, Long> fluids) {
            this.builtAt = builtAt;
            this.items = items;
            this.fluids = fluids;
        }
    }

    /** 按网络缓存物化结果。WeakHashMap：网络对象被回收后条目自动消失，不阻止卸载。 */
    private static final Map<IGrid, Snapshot> CACHE = new WeakHashMap<>();

    private AeStockReader() {}

    /** 单目标读取（保留旧入口，内部走批量实现）。 */
    public static long readStock(IGrid grid, IAEStack<?> target) {
        if (grid == null || target == null) return 0;
        long[] out = new long[1];
        readStocks(grid, java.util.Collections.singletonList(target), out);
        return out[0];
    }

    /**
     * 批量读取多个目标的网络存量，结果写入 {@code results[i]}（与 {@code targets[i]} 对应，无匹配为 0）。
     * 全部目标共用一次列表物化/遍历，调用方需保证 results.length >= targets.size()。
     */
    public static void readStocks(IGrid grid, List<? extends IAEStack<?>> targets, long[] results) {
        if (grid == null || targets == null || targets.isEmpty()) return;
        try {
            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null) return;

            boolean anyItem = false, anyFluid = false;
            for (IAEStack<?> t : targets) {
                if (t instanceof IAEItemStack) anyItem = true;
                else if (t instanceof IAEFluidStack) anyFluid = true;
            }

            Snapshot snapshot = snapshot(grid, storage, anyItem, anyFluid);
            if (snapshot == null) return;

            if (anyItem && snapshot.items != null) {
                IItemList<IAEItemStack> list = snapshot.items;
                for (int i = 0; i < targets.size(); i++) {
                    IAEStack<?> t = targets.get(i);
                    if (!(t instanceof IAEItemStack)) continue;
                    IAEItemStack result = list.findPrecise((IAEItemStack) t);
                    results[i] = result == null ? 0 : result.getStackSize();
                }
            }

            if (anyFluid && snapshot.fluids != null) {
                for (int i = 0; i < targets.size(); i++) {
                    IAEStack<?> t = targets.get(i);
                    if (!(t instanceof IAEFluidStack)) continue;
                    Long count = snapshot.fluids.get(
                        ((IAEFluidStack) t).getFluid()
                            .getID());
                    results[i] = count == null ? 0 : count;
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 取（或按 TTL 重建）该网络的物化快照。返回 null 表示本次不应写结果。 */
    private static Snapshot snapshot(IGrid grid, IStorageGrid storage, boolean needItems, boolean needFluids) {
        long now = System.currentTimeMillis();
        synchronized (CACHE) {
            Snapshot cached = CACHE.get(grid);
            if (cached != null && now - cached.builtAt <= CACHE_TTL_MS
                && (!needItems || cached.items != null)
                && (!needFluids || cached.fluids != null)) {
                return cached;
            }
        }

        IItemList<IAEItemStack> items = null;
        Map<Integer, Long> fluids = null;

        if (needItems) {
            // 物化一次（NetworkItemList.findPrecise 每次都会 buildFinalItemList 全表复制，
            // 这里显式物化一份普通列表，之后所有槽位都只是哈希查找）
            IItemList<IAEItemStack> list = storage.getItemInventory()
                .getStorageList();
            if (list instanceof NetworkItemList) {
                list = ((NetworkItemList<IAEItemStack>) list).buildFinalItemList();
            }
            items = list;
        }

        if (needFluids) {
            // 注意：不能用 findPrecise((IAEFluidStack) target)——AE2UEL 的 AEFluidStack.equals
            // 对 AEFluidStack 参数比较的是 fluid 实例引用（==）和 tagCompound 引用（==），
            // 新建的 target 的 Fluid 实例可能与网络里存储的不是同一个对象引用，导致匹配不到。
            // 这里把流体表压成 fluidId -> 数量，后续查表 O(1)。
            Map<Integer, Long> map = new HashMap<>();
            for (IAEFluidStack entry : storage.getFluidInventory()
                .getStorageList()) {
                if (entry == null || entry.getFluid() == null) continue;
                map.put(
                    entry.getFluid()
                        .getID(),
                    entry.getStackSize());
            }
            fluids = map;
        }

        Snapshot fresh = new Snapshot(now, items, fluids);
        synchronized (CACHE) {
            CACHE.put(grid, fresh);
        }
        return fresh;
    }

    /** 供调试/命令强制失效（例如 /ae2qol reload 或网络重建后）。 */
    public static void invalidate(IGrid grid) {
        synchronized (CACHE) {
            if (grid == null) CACHE.clear();
            else CACHE.remove(grid);
        }
    }
}
