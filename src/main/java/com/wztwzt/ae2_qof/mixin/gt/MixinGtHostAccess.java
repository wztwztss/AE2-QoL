package com.wztwzt.ae2_qof.mixin.gt;

import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * GT 样板输入仓**宿主类**的样板数组访问器（3.22.0 M3 手势）。
 *
 * <p>为什么需要：`internalInventory` 在 GT 里是 `private final`，而"Shift+中键在机器样板槽上改电路"
 * 的服务端写入包必须能按槽位索引取到那张样板（以确认它确实是我们的智能通配样板并改写其 NBT）。
 * 只读：本模组不会改写这个数组本身。
 */
@Mixin(value = MTEHatchCraftingInputME.class, remap = false)
public interface MixinGtHostAccess {

    @Accessor("internalInventory")
    MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME>[] getAe2qolInternalInventory();
}
