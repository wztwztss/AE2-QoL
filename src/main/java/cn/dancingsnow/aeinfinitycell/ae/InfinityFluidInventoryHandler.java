package cn.dancingsnow.aeinfinitycell.ae;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;
import cn.dancingsnow.aeinfinitycell.storage.CellCount;
import cn.dancingsnow.aeinfinitycell.storage.FluidStackKey;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord;

public final class InfinityFluidInventoryHandler extends AbstractInfinityInventoryHandler<IAEFluidStack> {

    public InfinityFluidInventoryHandler(ItemStack cellStack, ISaveProvider saveProvider) {
        super(
            cellStack,
            saveProvider,
            AEFluidStackType.FLUID_STACK_TYPE,
            StorageChannel.FLUIDS,
            ICellCacheRegistry.TYPE.FLUID);
    }

    @Override
    protected void add(InfinityCellRecord record, IAEFluidStack input, long amount) {
        record.addFluid(FluidStackKey.from(input.getFluidStack()), amount);
    }

    @Override
    protected long extract(InfinityCellRecord record, IAEFluidStack request, long amount, boolean modulate) {
        FluidStackKey key = FluidStackKey.from(request.getFluidStack());
        long available = record.getFluidAmount(key);
        long extracted = Math.min(available, amount);
        if (modulate && extracted > 0L) {
            record.removeFluid(key, extracted);
        }
        return extracted;
    }

    @Override
    protected long amount(InfinityCellRecord record, IAEFluidStack request) {
        return record.getFluidAmount(FluidStackKey.from(request.getFluidStack()));
    }

    @Override
    protected void addAvailable(InfinityCellRecord record, IItemList<IAEFluidStack> out) {
        for (Map.Entry<FluidStackKey, CellCount> entry : record.getFluidsView()
            .entrySet()) {
            // 数量直接取 entry 的值：与 getFluidAmount 同口径（CellCount.longValue 超限钳到 long），少一次 map 查找
            long aeAmount = entry.getValue()
                .longValue();
            FluidStack stack = entry.getKey()
                .toStack(aeAmount);
            if (stack == null) {
                continue;
            }
            IAEFluidStack aeStack = AEFluidStack.create(stack);
            if (aeStack != null) {
                aeStack.setStackSize(aeAmount);
                out.addStorage(aeStack);
            }
        }
    }

    @Override
    protected long usedTypes(InfinityCellRecord record) {
        return record.getUsedFluidTypes();
    }
}
