package com.wztwzt.ae2_qof.cover.stockmonitor.ae;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

public final class AeStockReader {

    private AeStockReader() {}

    public static long readStock(IGrid grid, IAEStack<?> target) {
        if (grid == null || target == null) return 0;
        try {
            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null) return 0;

            if (target instanceof IAEItemStack) {
                IAEItemStack result = storage.getItemInventory().getStorageList()
                    .findPrecise((IAEItemStack) target);
                return result == null ? 0 : result.getStackSize();
            } else if (target instanceof IAEFluidStack) {
                // 注意：不能用 findPrecise((IAEFluidStack) target)——AE2UEL 的 AEFluidStack.equals
                // 对 AEFluidStack 参数比较的是 fluid 实例引用（==）和 tagCompound 引用（==），
                // 新建的 target 的 Fluid 实例可能与网络里存储的不是同一个对象引用，导致匹配不到。
                // 改用遍历 + getFluidID() 比较（流体注册 ID 是全局唯一的）。
                IAEFluidStack targetFluid = (IAEFluidStack) target;
                int targetId = targetFluid.getFluid().getID();
                for (IAEFluidStack entry : storage.getFluidInventory().getStorageList()) {
                    if (entry != null && entry.getFluid().getID() == targetId) {
                        return entry.getStackSize();
                    }
                }
                return 0;
            }
        } catch (Throwable ignored) {}
        return 0;
    }
}
