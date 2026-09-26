package com.wztwzt.ae2_qof.mixin.ph;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import reobf.proghatches.gt.metatileentity.PatternDualInputHatch;

/**
 * Programmable-Hatches 的 {@code PatternDualInputHatch} 把样板容量**写死在 4 个字段的数组长度里**：
 * {@code pattern} / {@code multiplier} / {@code patternItemCache} / {@code patternDetailCache} 全部是
 * {@code new ...[36]}（用 {@code javap -c} 可在它的两个构造器里各看到 4 次 {@code bipush 36}）。
 * 类内所有功能循环都按 {@code pattern.length} 走，所以「换掉这 4 个数组」就等于「换容量」。
 *
 * <p>本 accessor 只提供这个能力给本模组的子类
 * {@link com.wztwzt.ae2_qof.ph.MTEPatternCraftingBufferMKIII}，**不改动 PH 自身任何行为**：
 * PH 自己的三种变体（总成 22069 / MK.II 22179 / 仅物品 22130）依旧是 36 槽。
 *
 * <p>另外两个 {@code @Invoker} 是继承拿不到的 private 成员：
 * <ul>
 * <li>{@code onPatternChange()} —— 「样板变了」的唯一入口，样板窗的槽位 changeListener 必须调它，
 * 否则手动放进槽位的样板不会重新注册给 AE（表现为样板放了但不接单）；</li>
 * <li>{@code refundAll()} —— 样板窗「退货」按钮的实现（把内部缓冲里的物品/流体全退回 AE）。</li>
 * </ul>
 *
 * <p>PH 是运行时可选依赖（compileOnly + 配置级 required=false）。未安装 PH 时本 mixin 的目标类
 * 永远不会被加载，因此它既不会被应用也不会报错；本模组引用 PH 类型的代码全部隔离在
 * {@code PhIntegration.register()} 的方法体里，同样不会被加载。
 */
@SuppressWarnings("MixinAnnotationTarget")
@Mixin(value = PatternDualInputHatch.class, remap = false)
public interface MixinPatternDualInputHatchAccess {

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

    /** PH 的 {@code private void onPatternChange()}：标记样板已变更，触发重新向 AE 注册。 */
    @Invoker("onPatternChange")
    void invokeAe2qolOnPatternChange();

    /** PH 的 {@code private void refundAll() throws Exception}：把内部缓冲全部退回 AE。 */
    @Invoker("refundAll")
    void invokeAe2qolRefundAll() throws Exception;
}
