package com.wztwzt.ae2_qof.mixin.ph;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import reobf.proghatches.gt.metatileentity.PatternDualInputHatchInventoryMappingSlave;

/**
 * 「库存映射从属样板总成」{@code PatternDualInputHatchInventoryMappingSlave} 的 accessor（4.3.0）。
 *
 * <p>
 * 它是 PH 家族里**唯一不继承** {@code PatternDualInputHatch} 的样板总成（独立类，
 * 直接实现 {@code MTETieredMachineBlock}），但样板存储四件套的字段名与语义完全一致：
 * {@code pattern[]} / {@code multiplier[]} / {@code patternItemCache[]} / {@code patternDetailCache[]}，
 * 变更通知同样是私有的 {@code onPatternChange()}（置 {@code needPatternSync}），
 * 刷新入口 {@code refresh()} 则是 public。
 *
 * <p>
 * 样板剪贴板（{@code ItemPatternClipboard}）经
 * {@link com.wztwzt.ae2_qof.ph.PatternHostAdapter} 统一读写全家族机器，
 * 本 mixin 只为这台从属机补上与 {@link MixinPatternDualInputHatchAccess} 同等的能力，
 * **不改动 PH 自身任何行为**。未安装 PH 时目标类不加载，本 mixin 不会被应用。
 */
@SuppressWarnings("MixinAnnotationTarget")
@Mixin(value = PatternDualInputHatchInventoryMappingSlave.class, remap = false)
public interface MixinPatternDualInputHatchMappingSlaveAccess {

    @Accessor("pattern")
    ItemStack[] getAe2qolPattern();

    @Accessor("pattern")
    void setAe2qolPattern(ItemStack[] value);

    @Accessor("patternItemCache")
    void setAe2qolPatternItemCache(ItemStack[] value);

    @Accessor("patternDetailCache")
    void setAe2qolPatternDetailCache(ICraftingPatternDetails[] value);

    @Accessor("multiplier")
    int[] getAe2qolMultiplier();

    @Accessor("multiplier")
    void setAe2qolMultiplier(int[] value);

    /** 置 {@code needPatternSync}，让机器在下次更新时把样板重新注册给 AE。 */
    @Invoker("onPatternChange")
    void invokeAe2qolOnPatternChange();
}
