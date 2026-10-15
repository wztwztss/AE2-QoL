package cn.dancingsnow.aeinfinitycell.storage;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import thaumicenergistics.common.storage.AEEssentiaStack;

/**
 * 单个无限存储元件的后端记录（世界存档 data/AEInfinityCell/&lt;uuid&gt;.dat）。
 * <p>
 * 3.27.0 起并入上游 AE2InfinityCell 1.0.5 的计数模型：四个字段的类型都是 {@link CellCount}
 * （long/BigInteger 自适应）。**这不是风格选择**——Apeiron 的 InfinityCellRecordBigMixin 用
 * {@code @Shadow @Final} 直接引用这四个字段，CellCountBigMixin 又以 {@link CellCount} 为注入目标；
 * 字段名、擦除后描述符与 final 修饰必须与上游逐字一致，否则本类会因 mixin APPLY 失败而永久无法加载
 * （2026-10-08 崩溃的根因）。因此不要把这些字段改回 BigInteger。
 * <p>
 * 同时保留本项目自己的便捷 API（{@link #clampAmount}、{@link #getStoredItemUnits} 等、
 * {@link #getEUAmountExact}、{@code remove*}/{@code add* BigInteger} 重载），避免已并入的调用点
 * （NEI 预览、统计包、IO 端口多通道）跟着改一遍。
 * <p>
 * 磁盘格式与 1.0.4 基线保持兼容：条目数量与 EU 都写成 NBTTagString（{@code amount}/{@code eu}），
 * 升级与回退都能原样读出。
 */
public final class InfinityCellRecord {

    private static final String KEY_ITEMS = "items";
    private static final String KEY_FLUIDS = "fluids";
    private static final String KEY_ESSENTIA = "essentia";
    private static final String KEY_EU = "eu";
    private static final String KEY_AMOUNT = "amount";

    private static final BigInteger BIG_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final Map<ItemStackKey, CellCount> items = new LinkedHashMap<>();
    private final Map<FluidStackKey, CellCount> fluids = new LinkedHashMap<>();
    private final Map<EssentiaStackKey, CellCount> essentia = new LinkedHashMap<>();
    private final CellCount eu = new CellCount();

    /**
     * 3.35.0-fix1（审查 P1-9）：记录级锁。
     * <p>账本（三个 LinkedHashMap）会被两个线程同时触碰：服务端 tick 线程（AE2 存储
     * MODULATE 插入/抽取）与客户端 NEI 线程（单机 U 键预览直读 {@code getItemsView}）。
     * 旧实现零同步 ⇒ 遍历期间服务端插入/删除即 {@code ConcurrentModificationException}，
     * 或读到半更新状态。{@code InfinityCellStorage} 的 synchronized 罩不到记录内部
     * （getOrCreate 返回引用后锁即释放）。
     * <p>改法：全部触碰 map 的公共方法挂本锁；对外视图改为**快照拷贝**（见
     * {@code getItemsView}），消费方拿到的不再是可以被并发改动的活账本。
     */
    private final Object lock = new Object();

    // ==================== 读取数量 ====================

    public long getItemAmount(ItemStackKey key) {
        synchronized (lock) {
            return amount(items, key);
        }
    }

    public long getFluidAmount(FluidStackKey key) {
        synchronized (lock) {
            return amount(fluids, key);
        }
    }

    public long getEssentiaAmount(EssentiaStackKey key) {
        synchronized (lock) {
            return amount(essentia, key);
        }
    }

    public long getEUAmount() {
        return eu.longValue();
    }

    /** 上游 1.0.5 的访问器；NEI 预览与 Apeiron 的预览 mixin 都按这个形状取 EU 计数。 */
    public CellCount getEUCount() {
        return eu;
    }

    /** 本项目 API：不经 long 钳制的 EU 精确值。 */
    public BigInteger getEUAmountExact() {
        return eu.toBigInteger();
    }

    /**
     * 3.25.0-fix18（性能）：把"枚举时手里已经有的 BigInteger"按与 {@link #getItemAmount} **完全同一口径**
     * 夹到 long。保留它是为了不破坏既有的对外形状；枚举热路径现在直接走 {@link CellCount#longValue()}。
     */
    public long clampAmount(BigInteger amount) {
        return clampToLong(amount);
    }

    // ==================== 写入 ====================

    public void addItem(ItemStackKey key, long amount) {
        synchronized (lock) {
            add(items, key, amount);
        }
    }

    public void addItem(ItemStackKey key, BigInteger amount) {
        synchronized (lock) {
            add(items, key, amount);
        }
    }

    public void addFluid(FluidStackKey key, long amount) {
        synchronized (lock) {
            add(fluids, key, amount);
        }
    }

    public void addFluid(FluidStackKey key, BigInteger amount) {
        synchronized (lock) {
            add(fluids, key, amount);
        }
    }

    public void addEssentia(EssentiaStackKey key, long amount) {
        synchronized (lock) {
            add(essentia, key, amount);
        }
    }

    public void addEssentia(EssentiaStackKey key, BigInteger amount) {
        synchronized (lock) {
            add(essentia, key, amount);
        }
    }

    public void addEU(long amount) {
        synchronized (lock) {
            eu.add(amount);
        }
    }

    public void addEU(BigInteger amount) {
        synchronized (lock) {
            if (amount != null && amount.signum() > 0) {
                eu.add(CellCount.parse(amount.toString()));
            }
        }
    }

    // ==================== 取出 ====================

    /**
     * 取出至多 requested 的数量并返回实际取出量；modulate 为 false 时只模拟不修改存储。
     */
    public long extractItem(ItemStackKey key, long requested, boolean modulate) {
        synchronized (lock) {
            return extract(items, key, requested, modulate);
        }
    }

    public long extractFluid(FluidStackKey key, long requested, boolean modulate) {
        synchronized (lock) {
            return extract(fluids, key, requested, modulate);
        }
    }

    public long extractEssentia(EssentiaStackKey key, long requested, boolean modulate) {
        synchronized (lock) {
            return extract(essentia, key, requested, modulate);
        }
    }

    public long extractEU(long requested, boolean modulate) {
        synchronized (lock) {
            if (requested <= 0L) {
                return 0L;
            }
            long extracted = Math.min(eu.longValue(), requested);
            if (modulate && extracted > 0L) {
                eu.extract(extracted);
            }
            return extracted;
        }
    }

    /** 本项目 API（等价于 {@code extractX(key, requested, true)}）。 */
    public long removeItem(ItemStackKey key, long requested) {
        return extractItem(key, requested, true);
    }

    public long removeFluid(FluidStackKey key, long requested) {
        return extractFluid(key, requested, true);
    }

    public long removeEssentia(EssentiaStackKey key, long requested) {
        return extractEssentia(key, requested, true);
    }

    public long removeEU(long requested) {
        return extractEU(requested, true);
    }

    // ==================== 视图 ====================

    /**
     * 3.35.0-fix1：返回**锁内快照拷贝**而非活视图。旧实现 {@code unmodifiableMap(items)}
     * 只是挡写——消费方（AE2 addAvailable 枚举 / NEI 预览）仍握着活账本引用，
     * 客户端线程遍历时服务端插入/删除即 CME。拷贝成本 O(n)，仅在枚举路径发生（可接受）。
     */
    public Map<ItemStackKey, CellCount> getItemsView() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new java.util.LinkedHashMap<>(items));
        }
    }

    public Map<FluidStackKey, CellCount> getFluidsView() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fluids));
        }
    }

    public Map<EssentiaStackKey, CellCount> getEssentiaView() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new java.util.LinkedHashMap<>(essentia));
        }
    }

    // ==================== 计数 ====================

    public long getUsedItemTypes() {
        synchronized (lock) {
            return items.size();
        }
    }

    public long getUsedFluidTypes() {
        synchronized (lock) {
            return fluids.size();
        }
    }

    public long getUsedEssentiaTypes() {
        synchronized (lock) {
            return essentia.size();
        }
    }

    public long getUsedEUTypes() {
        return eu.isPositive() ? 1L : 0L;
    }

    public long getStoredItemUnits() {
        synchronized (lock) {
            return sum(items);
        }
    }

    public long getStoredFluidUnits() {
        synchronized (lock) {
            return sum(fluids);
        }
    }

    public long getStoredEssentiaUnits() {
        synchronized (lock) {
            return sum(essentia);
        }
    }

    public long getStoredEUUnits() {
        synchronized (lock) {
            return eu.longValue();
        }
    }

    // ==================== 序列化 ====================

    public NBTTagCompound writeToNBT() {
        synchronized (lock) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag(KEY_ITEMS, writeEntries(items, ItemStackKey::writeToNBT));
            tag.setTag(KEY_FLUIDS, writeEntries(fluids, FluidStackKey::writeToNBT));
            tag.setTag(KEY_ESSENTIA, writeEntries(essentia, EssentiaStackKey::writeToNBT));
            tag.setString(KEY_EU, eu.toString());
            return tag;
        }
    }

    public void readFromNBT(NBTTagCompound tag) {
        synchronized (lock) {
            items.clear();
            fluids.clear();
            essentia.clear();
            eu.clear();
            if (tag.hasKey(KEY_EU, 8)) {
                eu.add(CellCount.parse(tag.getString(KEY_EU)));
            }
            // 3.35.0-fix3：被丢弃的条目（amount 非 String 的旧格式行）留痕——
            // "载入 N / 共 M 条" 形式，M > N 即说明发生了丢弃，日志上直接可见。
            int loadedItems = readEntries(tag.getTagList(KEY_ITEMS, 10), items, ItemStackKey::readFromNBT);
            int loadedFluids = readEntries(tag.getTagList(KEY_FLUIDS, 10), fluids, FluidStackKey::readFromNBT);
            int loadedEssentia = readEntries(tag.getTagList(KEY_ESSENTIA, 10), essentia, EssentiaStackKey::readFromNBT);
            logSkippedIfNeeded("items", loadedItems, tag.getTagList(KEY_ITEMS, 10).tagCount());
            logSkippedIfNeeded("fluids", loadedFluids, tag.getTagList(KEY_FLUIDS, 10).tagCount());
            logSkippedIfNeeded("essentia", loadedEssentia, tag.getTagList(KEY_ESSENTIA, 10).tagCount());
        }
    }

    /** 3.35.0-fix3：载入数 < 总数时 WARN（含被忽略条数）——把静默丢弃变成可见事件。 */
    private static void logSkippedIfNeeded(String channel, int loaded, int total) {
        if (loaded < total) {
            cn.dancingsnow.aeinfinitycell.AEInfinityCell.LOG.warn(
                "[AE2QoL] 无限元件读档：{} 通道 {} 条中只有 {} 条载入，{} 条因数量字段为旧格式（long）被忽略"
                    + "——这些内容无法恢复，重新存入即可",
                channel,
                total,
                loaded,
                total - loaded);
        }
    }

    public ItemStack createItemStack(ItemStackKey key, long amount) {
        return key.toStack(amount);
    }

    public FluidStack createFluidStack(FluidStackKey key, long amount) {
        return key.toStack(amount);
    }

    public AEEssentiaStack createEssentiaStack(EssentiaStackKey key, long amount) {
        return key.toStack(amount);
    }

    private static <K> NBTTagList writeEntries(Map<K, CellCount> entries, EntryWriter<K> writer) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<K, CellCount> entry : entries.entrySet()) {
            CellCount count = entry.getValue();
            if (count.isPositive()) {
                NBTTagCompound tag = writer.write(entry.getKey(), count.longValue());
                tag.setString(KEY_AMOUNT, count.toString());
                list.appendTag(tag);
            }
        }
        return list;
    }

    /**
     * 3.35.0-fix3（审查 P1-12）：读档兼容性丢弃改为**可见**。
     * 旧格式（数量字段是 long 而非 String）无法表示超限数量，丢弃语义保留（上游 1.0.5 单元测试
     * {@code legacyLongAmountsAreIgnored} 把它写成预期），但"扔了几个"必须留下痕迹 ——
     * 否则"数据丢了"与"本来就没有"在日志上无法区分，而迁移链随后还会把源文件归档封存。
     *
     * @return 实际载入的条目数；调用方对比 {@code list.tagCount()} 即得被忽略数。
     */
    private static <K> int readEntries(NBTTagList list, Map<K, CellCount> entries, KeyReader<K> reader) {
        int loaded = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            // 旧格式用 long 保存数量，无法表示超限数量，迁移时直接忽略（数量统计见返回值）
            if (!entry.hasKey(KEY_AMOUNT, 8)) {
                continue;
            }
            CellCount count = CellCount.parse(entry.getString(KEY_AMOUNT));
            if (count.isPositive()) {
                entries.put(reader.read(entry), count);
                loaded++;
            }
        }
        return loaded;
    }

    private static <K> void add(Map<K, CellCount> map, K key, long amount) {
        if (amount <= 0L) {
            return;
        }
        map.computeIfAbsent(key, k -> new CellCount())
            .add(amount);
    }

    private static <K> void add(Map<K, CellCount> map, K key, BigInteger amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        map.computeIfAbsent(key, k -> new CellCount())
            .add(CellCount.parse(amount.toString()));
    }

    private static <K> long extract(Map<K, CellCount> map, K key, long requested, boolean modulate) {
        if (requested <= 0L) {
            return 0L;
        }
        CellCount current = map.get(key);
        if (current == null) {
            return 0L;
        }
        long extracted = Math.min(current.longValue(), requested);
        if (modulate && extracted > 0L) {
            current.extract(extracted);
            if (current.isZero()) {
                map.remove(key);
            }
        }
        return extracted;
    }

    private static <K> long amount(Map<K, CellCount> map, K key) {
        CellCount current = map.get(key);
        return current == null ? 0L : current.longValue();
    }

    private static long sum(Map<?, CellCount> map) {
        CellCount total = new CellCount();
        for (CellCount count : map.values()) {
            total.add(count);
        }
        return total.longValue();
    }

    private static long clampToLong(BigInteger amount) {
        if (amount.compareTo(BIG_LONG_MAX) > 0) {
            return Long.MAX_VALUE;
        }
        if (amount.signum() < 0) {
            return 0L;
        }
        return amount.longValue();
    }

    private interface EntryWriter<K> {

        NBTTagCompound write(K key, long amount);
    }

    private interface KeyReader<K> {

        K read(NBTTagCompound tag);
    }
}
