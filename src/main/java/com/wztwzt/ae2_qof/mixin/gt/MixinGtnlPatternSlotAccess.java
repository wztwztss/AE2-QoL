package com.wztwzt.ae2_qof.mixin.gt;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME;

/**
 * GTNL 样板槽 {@code SuperCraftingInputHatchME$PatternSlot} 的字段访问器（3.22.0）。
 *
 * <p>为什么需要：槽位里的样板 ItemStack（{@code pattern}）没出现在公开 API 上，而我们包槽位时必须以它为
 * 构造参数；GTNL 的 {@code equals/hashCode} 也是按它与 {@code patternItemId} 定义的（GTNL:1384-1395）。
 * 用接口式 accessor 混入内部类后强转读取（与 GT 侧 {@code MixinPatternSlotAccess} 同一手法），**只读不改**。
 */
@Mixin(value = SuperCraftingInputHatchME.PatternSlot.class, remap = false)
public interface MixinGtnlPatternSlotAccess {

    @Accessor("pattern")
    ItemStack getAe2qolSlotPattern();
}
