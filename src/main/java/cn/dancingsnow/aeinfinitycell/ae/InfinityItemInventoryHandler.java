package cn.dancingsnow.aeinfinitycell.ae;

import java.util.Map;

import net.minecraft.item.ItemStack;

import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import cn.dancingsnow.aeinfinitycell.storage.CellCount;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord;
import cn.dancingsnow.aeinfinitycell.storage.ItemStackKey;

public final class InfinityItemInventoryHandler extends AbstractInfinityInventoryHandler<IAEItemStack> {

    public InfinityItemInventoryHandler(ItemStack cellStack, ISaveProvider saveProvider) {
        super(
            cellStack,
            saveProvider,
            AEItemStackType.ITEM_STACK_TYPE,
            StorageChannel.ITEMS,
            ICellCacheRegistry.TYPE.ITEM);
    }

    @Override
    protected boolean add(InfinityCellRecord record, IAEItemStack input, long amount) {
        ItemStackKey key = key(input);
        if (key != null) {
            record.addItem(key, amount);
            return true;
        }
        // 3.35.0-fix2（审查 P1-11）：识别失败 ⇒ 返回 false 让上层把物品**原样退回**（AE2 契约：
        // 返回剩余量即"未接收"），并把"识别失败"记成限频 WARN —— 旧实现什么都不存却让 AE2
        // 按"全部接收"删源物品 = 静默吞物品，且零日志。
        ae2qol$warnKeyFailure(input);
        return false;
    }

    /** 3.35.0-fix2：限频警告（同一物品只报一次，进程内去重，防刷屏）。 */
    private static final java.util.Set<String> WARNED_KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void ae2qol$warnKeyFailure(IAEItemStack input) {
        try {
            net.minecraft.item.ItemStack mc = input.getItemStack();
            String id = mc != null && mc.getItem() != null
                ? net.minecraft.item.Item.itemRegistry.getNameForObject(mc.getItem())
                    .toString()
                : "null";
            if (WARNED_KEYS.add(id)) {
                cn.dancingsnow.aeinfinitycell.AEInfinityCell.LOG.warn(
                    "[AE2QoL] 无限元件拒收无法解析注册身份的物品：{}（原样退回；该物品只会警告一次）",
                    id);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected long extract(InfinityCellRecord record, IAEItemStack request, long amount, boolean modulate) {
        ItemStackKey key = key(request);
        if (key == null) {
            return 0L;
        }
        long available = record.getItemAmount(key);
        long extracted = Math.min(available, amount);
        if (modulate && extracted > 0L) {
            record.removeItem(key, extracted);
        }
        return extracted;
    }

    @Override
    protected long amount(InfinityCellRecord record, IAEItemStack request) {
        ItemStackKey key = key(request);
        return key == null ? 0L : record.getItemAmount(key);
    }

    /**
     * 3.25.0-fix18（性能）：每个键缓存一个**原型 AE 栈**，枚举时只 `copy() + setStackSize()`。
     * <p>
     * 原实现每枚举一个物品都要 `ItemStackKey.toStack()`（→ `new GameRegistry.UniqueIdentifier`
     * 拆字符串 + `GameRegistry.findItem` 注册表反查 + `new ItemStack` + **NBT 深拷贝**）
     * 再 `AEItemStack.create(stack)`（→ `OreHelper.isOre` + `AESharedNBT` 共享表查询 + `AEItemDef` 查表）。
     * 热点火焰图实测这条链合计约 5.7%。
     * <p>
     * 现在这整条链**每个键只付一次**：以后每次枚举就是一次 `copy()`（AE 内部只拷字段，无注册表/NBT 查询）
     * + 设置数量。值里缓存 `null` 作哨兵，表示"该键解析不出物品"（避免每次枚举都重试失败的注册表反查）。
     * <p>
     * ⚠️ 交给 AE 的**永远是 copy**，原型本身绝不外传（AE 会改 stackSize 等字段）。
     */
    private final Map<ItemStackKey, IAEItemStack> prototypes = new java.util.HashMap<>();

    @Override
    protected void addAvailable(InfinityCellRecord record, IItemList<IAEItemStack> out) {
        for (Map.Entry<ItemStackKey, CellCount> entry : record.getItemsView()
            .entrySet()) {
            ItemStackKey key = entry.getKey();
            IAEItemStack prototype = prototypes.get(key);
            if (prototype == null) {
                if (prototypes.containsKey(key)) {
                    continue; // 已知解析不出（物品被移除/未注册）⇒ 直接跳过，不再重试
                }
                ItemStack stack = key.toStack(1L);
                prototype = stack == null ? null : AEItemStack.create(stack);
                prototypes.put(key, prototype);
                if (prototype == null) {
                    continue;
                }
            }
            // 数量直接取 entry 的值（原实现又对**正在遍历的同一个 map** 调了一次 getItemAmount(key)，
            // 白付一次哈希查找 + hashCode 重算）。CellCount.longValue() 与 getItemAmount 同一口径：超限钳到 long
            long aeAmount = entry.getValue()
                .longValue();
            IAEItemStack aeStack = prototype.copy();
            aeStack.setStackSize(aeAmount);
            out.addStorage(aeStack);
        }

        // 3.35.0-fix15（审查 P2-7）：枚举结束后按当前账本收缩原型缓存。
        // 旧实现只增不减 —— 元件里存过又取空的物品，其原型（ItemStackKey + IAEItemStack + NBT）
        // 会永久留在 map 里，长期反复存删不同物品时内存只涨不消。
        ae2qol$prunePrototypes(record);
    }

    /**
     * 3.35.0-fix15（审查 P2-7）：原型缓存收缩。
     * <p>
     * 只在"缓存明显大于账本"时才动作（{@code size > 账本条目数 + 余量}）——判断是 O(1) 的
     * {@code items.size()}，所以正常枚举零额外开销；真正清理时才付一次
     * {@code getItemsView()} 的快照拷贝（fix1 起它返回副本）与 {@code retainAll}。
     * <p>
     * 清理掉的是**已不在账本里**的键（含"解析不出物品"的 null 哨兵）——它们不可能再被枚举命中，
     * 留着只是占内存。仍在账本里的键（哪怕哨兵）一律保留，避免反复重试失败的注册表反查。
     */
    private static final int PROTOTYPE_PRUNE_SLACK = 64;

    private void ae2qol$prunePrototypes(InfinityCellRecord record) {
        long used = record.getUsedItemTypes();
        if (prototypes.size() <= used + PROTOTYPE_PRUNE_SLACK) {
            return;
        }
        prototypes.keySet()
            .retainAll(
                record.getItemsView()
                    .keySet());
    }

    @Override
    protected long usedTypes(InfinityCellRecord record) {
        return record.getUsedItemTypes();
    }

    private static ItemStackKey key(IAEItemStack stack) {
        try {
            return ItemStackKey.from(stack.getItemStack());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
