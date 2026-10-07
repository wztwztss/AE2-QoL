package cn.dancingsnow.aeinfinitycell.ae;

import java.math.BigInteger;
import java.util.Map;

import net.minecraft.item.ItemStack;

import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
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
    protected void add(InfinityCellRecord record, IAEItemStack input, long amount) {
        ItemStackKey key = key(input);
        if (key != null) {
            record.addItem(key, amount);
        }
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
     * <p>原实现每枚举一个物品都要 `ItemStackKey.toStack()`（→ `new GameRegistry.UniqueIdentifier`
     * 拆字符串 + `GameRegistry.findItem` 注册表反查 + `new ItemStack` + **NBT 深拷贝**）
     * 再 `AEItemStack.create(stack)`（→ `OreHelper.isOre` + `AESharedNBT` 共享表查询 + `AEItemDef` 查表）。
     * 热点火焰图实测这条链合计约 5.7%。
     * <p>现在这整条链**每个键只付一次**：以后每次枚举就是一次 `copy()`（AE 内部只拷字段，无注册表/NBT 查询）
     * + 设置数量。值里缓存 `null` 作哨兵，表示"该键解析不出物品"（避免每次枚举都重试失败的注册表反查）。
     * <p>⚠️ 交给 AE 的**永远是 copy**，原型本身绝不外传（AE 会改 stackSize 等字段）。
     */
    private final Map<ItemStackKey, IAEItemStack> prototypes = new java.util.HashMap<>();

    @Override
    protected void addAvailable(InfinityCellRecord record, IItemList<IAEItemStack> out) {
        for (Map.Entry<ItemStackKey, BigInteger> entry : record.getItemsView()
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
            // 白付一次哈希查找 + hashCode 重算）
            long aeAmount = record.clampAmount(entry.getValue());
            IAEItemStack aeStack = prototype.copy();
            aeStack.setStackSize(aeAmount);
            out.addStorage(aeStack);
        }
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
