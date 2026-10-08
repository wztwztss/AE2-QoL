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

    // ==================== 读取数量 ====================

    public long getItemAmount(ItemStackKey key) {
        return amount(items, key);
    }

    public long getFluidAmount(FluidStackKey key) {
        return amount(fluids, key);
    }

    public long getEssentiaAmount(EssentiaStackKey key) {
        return amount(essentia, key);
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
        add(items, key, amount);
    }

    public void addItem(ItemStackKey key, BigInteger amount) {
        add(items, key, amount);
    }

    public void addFluid(FluidStackKey key, long amount) {
        add(fluids, key, amount);
    }

    public void addFluid(FluidStackKey key, BigInteger amount) {
        add(fluids, key, amount);
    }

    public void addEssentia(EssentiaStackKey key, long amount) {
        add(essentia, key, amount);
    }

    public void addEssentia(EssentiaStackKey key, BigInteger amount) {
        add(essentia, key, amount);
    }

    public void addEU(long amount) {
        eu.add(amount);
    }

    public void addEU(BigInteger amount) {
        if (amount != null && amount.signum() > 0) {
            eu.add(CellCount.parse(amount.toString()));
        }
    }

    // ==================== 取出 ====================

    /**
     * 取出至多 requested 的数量并返回实际取出量；modulate 为 false 时只模拟不修改存储。
     */
    public long extractItem(ItemStackKey key, long requested, boolean modulate) {
        return extract(items, key, requested, modulate);
    }

    public long extractFluid(FluidStackKey key, long requested, boolean modulate) {
        return extract(fluids, key, requested, modulate);
    }

    public long extractEssentia(EssentiaStackKey key, long requested, boolean modulate) {
        return extract(essentia, key, requested, modulate);
    }

    public long extractEU(long requested, boolean modulate) {
        if (requested <= 0L) {
            return 0L;
        }
        long extracted = Math.min(eu.longValue(), requested);
        if (modulate && extracted > 0L) {
            eu.extract(extracted);
        }
        return extracted;
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

    public Map<ItemStackKey, CellCount> getItemsView() {
        return Collections.unmodifiableMap(items);
    }

    public Map<FluidStackKey, CellCount> getFluidsView() {
        return Collections.unmodifiableMap(fluids);
    }

    public Map<EssentiaStackKey, CellCount> getEssentiaView() {
        return Collections.unmodifiableMap(essentia);
    }

    // ==================== 计数 ====================

    public long getUsedItemTypes() {
        return items.size();
    }

    public long getUsedFluidTypes() {
        return fluids.size();
    }

    public long getUsedEssentiaTypes() {
        return essentia.size();
    }

    public long getUsedEUTypes() {
        return eu.isPositive() ? 1L : 0L;
    }

    public long getStoredItemUnits() {
        return sum(items);
    }

    public long getStoredFluidUnits() {
        return sum(fluids);
    }

    public long getStoredEssentiaUnits() {
        return sum(essentia);
    }

    public long getStoredEUUnits() {
        return eu.longValue();
    }

    // ==================== 序列化 ====================

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(KEY_ITEMS, writeEntries(items, ItemStackKey::writeToNBT));
        tag.setTag(KEY_FLUIDS, writeEntries(fluids, FluidStackKey::writeToNBT));
        tag.setTag(KEY_ESSENTIA, writeEntries(essentia, EssentiaStackKey::writeToNBT));
        tag.setString(KEY_EU, eu.toString());
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        items.clear();
        fluids.clear();
        essentia.clear();
        eu.clear();
        if (tag.hasKey(KEY_EU, 8)) {
            eu.add(CellCount.parse(tag.getString(KEY_EU)));
        }
        readEntries(tag.getTagList(KEY_ITEMS, 10), items, ItemStackKey::readFromNBT);
        readEntries(tag.getTagList(KEY_FLUIDS, 10), fluids, FluidStackKey::readFromNBT);
        readEntries(tag.getTagList(KEY_ESSENTIA, 10), essentia, EssentiaStackKey::readFromNBT);
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

    private static <K> void readEntries(NBTTagList list, Map<K, CellCount> entries, KeyReader<K> reader) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            // 旧格式用 long 保存数量，无法表示超限数量，迁移时直接忽略
            if (!entry.hasKey(KEY_AMOUNT, 8)) {
                continue;
            }
            CellCount count = CellCount.parse(entry.getString(KEY_AMOUNT));
            if (count.isPositive()) {
                entries.put(reader.read(entry), count);
            }
        }
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
