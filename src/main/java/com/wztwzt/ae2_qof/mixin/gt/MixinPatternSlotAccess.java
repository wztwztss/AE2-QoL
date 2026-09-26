package com.wztwzt.ae2_qof.mixin.gt;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;

/**
 * GT 样板槽 {@code MTEHatchCraftingInputME$PatternSlot} 的字段访问器（3.22.0）。
 *
 * <p>为什么需要它：槽位的 {@code pattern}/{@code itemInventory}/{@code fluidInventory} 都是
 * {@code protected final}，而我们的接管 mixin 挂在**外层类** {@code MTEHatchCraftingInputME} 上，
 * 不是 {@code PatternSlot} 的子类 ⇒ 读不到这些字段，也不能跨内部类 @Shadow。
 * 用接口式 accessor 混入目标内部类后，外层代码强转即可读取（与 PH 侧
 * {@code MixinPatternDualInputHatchAccess} 同一手法）。
 *
 * <p>只读：本模组**从不改写** GT 槽位的任何字段（{@code pattern} 若被改写会在
 * {@code loadNBTData} 末尾同步回 {@code mInventory[i]}，污染玩家槽位，GT:857-860）。
 */
@Mixin(value = MTEHatchCraftingInputME.PatternSlot.class, remap = false)
public interface MixinPatternSlotAccess {

    @Accessor("pattern")
    ItemStack getAe2qolSlotPattern();
}
