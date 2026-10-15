package cn.dancingsnow.aeinfinitycell.ae;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import cn.dancingsnow.aeinfinitycell.ServerWorldAccess;
import cn.dancingsnow.aeinfinitycell.item.ItemInfinityStorageCell;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord;

public abstract class AbstractInfinityInventoryHandler<T extends IAEStack<T>>
    implements IMEInventoryHandler<T>, ICellCacheRegistry {

    private final ItemStack cellStack;
    private final ISaveProvider saveProvider;
    private final IAEStackType<T> stackType;
    private final StorageChannel channel;
    private final TYPE cellType;

    protected AbstractInfinityInventoryHandler(ItemStack cellStack, ISaveProvider saveProvider,
        IAEStackType<T> stackType, StorageChannel channel, TYPE cellType) {
        this.cellStack = cellStack;
        this.saveProvider = saveProvider;
        this.stackType = stackType;
        this.channel = channel;
        this.cellType = cellType;
    }

    @Override
    public final T injectItems(T input, Actionable type, BaseActionSource src) {
        if (input == null || input.getStackSize() <= 0L) {
            return null;
        }

        InfinityCellRecord record = record();
        if (record == null) {
            return input;
        }

        if (type == Actionable.MODULATE) {
            boolean stored = add(record, input, input.getStackSize());
            if (!stored) {
                // 3.35.0-fix2（审查 P1-11）：子类拒收（识别不出 key 等）⇒ 把物品**原样退回**
                // 而不是返回 null 让 AE2 按"全部接收"删源物品 —— 旧行为 = 静默吞物品。
                return input;
            }
            markChanged();
        }
        return null;
    }

    @Override
    public final T extractItems(T request, Actionable mode, BaseActionSource src) {
        if (request == null || request.getStackSize() <= 0L) {
            return null;
        }

        InfinityCellRecord record = record();
        if (record == null) {
            return null;
        }

        long extracted = extract(record, request, request.getStackSize(), mode == Actionable.MODULATE);
        if (extracted <= 0L) {
            return null;
        }

        if (mode == Actionable.MODULATE) {
            markChanged();
        }
        return copyWithSize(request, extracted);
    }

    @Override
    public final IItemList<T> getAvailableItems(IItemList<T> out, int iteration) {
        InfinityCellRecord record = record();
        if (record != null) {
            addAvailable(record, out);
        }
        return out;
    }

    @Override
    public final T getAvailableItem(T request, int iteration) {
        if (request == null) {
            return null;
        }

        InfinityCellRecord record = record();
        if (record == null) {
            return null;
        }

        long amount = amount(record, request);
        return amount <= 0L ? null : copyWithSize(request, amount);
    }

    @Override
    public final StorageChannel getChannel() {
        return channel;
    }

    @Override
    public final IAEStackType<?> getStackType() {
        return stackType;
    }

    @Override
    public final AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public final boolean isPrioritized(T input) {
        return false;
    }

    @Override
    public final boolean canAccept(T input) {
        return input != null;
    }

    @Override
    public final int getPriority() {
        return 0;
    }

    @Override
    public final int getSlot() {
        return 0;
    }

    @Override
    public final boolean validForPass(int i) {
        return true;
    }

    @Override
    public final IMEInventory<T> getInternal() {
        return this;
    }

    @Override
    public final boolean canGetInv() {
        return true;
    }

    @Override
    public final long getTotalBytes() {
        return Long.MAX_VALUE;
    }

    @Override
    public final long getFreeBytes() {
        return Long.MAX_VALUE;
    }

    @Override
    public final long getUsedBytes() {
        return 0L;
    }

    @Override
    public final long getTotalTypes() {
        return Long.MAX_VALUE;
    }

    @Override
    public final long getFreeTypes() {
        return Long.MAX_VALUE;
    }

    @Override
    public final long getUsedTypes() {
        InfinityCellRecord record = record();
        return record == null ? 0L : usedTypes(record);
    }

    @Override
    public final int getCellStatus() {
        return getUsedTypes() == 0L ? 1 : 2;
    }

    @Override
    public final StorageChannel getStorageChannel() {
        return channel;
    }

    @Override
    public final TYPE getCellType() {
        return cellType;
    }

    protected final T copyWithSize(T stack, long amount) {
        T copy = stack.copy();
        copy.setStackSize(amount);
        return copy;
    }

    public final boolean isForCellStack(ItemStack stack) {
        return this.cellStack == stack;
    }

    /**
     * 3.27.0：上游 1.0.5 的访问器。IO 端口多通道 mixin（轮询实现）在 matches/shouldMove 注入里靠它
     * 判断"该元件是否所有通道都已搬空"，缺少它会让上游 mixin 无法解析；Apeiron 的
     * InfinityInventoryBigMixin 也作用在同一目标类上。
     */
    public final ItemStack getCellStack() {
        return cellStack;
    }

    protected final InfinityCellRecord record() {
        World world = ServerWorldAccess.getServerWorld();
        return ItemInfinityStorageCell.getRecord(cellStack, world);
    }

    protected final void markChanged() {
        World world = ServerWorldAccess.getServerWorld();
        ItemInfinityStorageCell.markDirty(cellStack, world);
        if (saveProvider != null) {
            saveProvider.saveChanges(this);
        }
    }

    /**
     * 3.35.0-fix2（审查 P1-11）：签名从 void 改为 boolean ——
     * 返回 {@code true} = 已存入（或可安全忽略）；{@code false} = **拒收**（调用方必须把
     * input 原样退回给 AE2，否则 AE2 会按"全部接收"删除源物品 = 静默吞物品）。
     */
    protected abstract boolean add(InfinityCellRecord record, T input, long amount);

    protected abstract long extract(InfinityCellRecord record, T request, long amount, boolean modulate);

    protected abstract long amount(InfinityCellRecord record, T request);

    protected abstract void addAvailable(InfinityCellRecord record, IItemList<T> out);

    protected abstract long usedTypes(InfinityCellRecord record);
}
