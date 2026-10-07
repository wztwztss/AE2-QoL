package com.wztwzt.ae2_qof.mixin.ph;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.ph.PhCircuitWrap;

import reobf.proghatches.gt.metatileentity.PatternDualInputHatch;

/**
 * 3.25.0-fix20：**样板"被放进 PH 仓"那一刻**，把输入里的 GT 编程电路改写为 PH:编程器电路。
 *
 * <h2>为什么挂这里（而不是改别的模组的终端）</h2>
 * 用户要的是"任何终端写进来的样板都能自动适配 PH 编程工具箱"（例如 GT-Not-Good 的 FC Ultra Terminal
 * 三合一终端，它不会自己做这个转换）。挂在**宿主侧**有两个好处：
 * <ol>
 * <li>**与终端无关**：不管样板是哪个模组/哪个终端放进来的，都在这里统一处理；</li>
 * <li>**不依赖对方内部结构**：不需要反射改他们的报文/物品类，对方升级也不会把我们的实现搞坏。</li>
 * </ol>
 * 挂钩点是 PH 自己唯一的样板写入口（javap/源码确证：{@code PatternDualInputHatch.java} L239-240
 * {@code public void setInventorySlotContents(int index, ItemStack stack) { pattern[index] = stack; }}）。
 *
 * <h2>为什么注入在 RETURN 而不是 HEAD + cancel</h2>
 * 让 PH 的原实现（以及它可能做的记账）先照常跑完，我们再**把落在数组里的那个引用换成改写副本**，
 * 不打断它的任何行为 —— 比 cancel 更保守。
 *
 * <h2>为什么只处理新插入的</h2>
 * 用户口径："只要新插入的，不动旧样板"。因此**不做读档期扫描**：已经躺在仓里的旧样板保持原样，
 * 需要时玩家重新放一次即可（或自行用 PH 编程工具箱转写）。
 *
 * <p>PH 为运行时可选依赖（compileOnly + {@code required:false}）：未装 PH 时本混入不会被应用。
 */
@Mixin(value = PatternDualInputHatch.class, remap = false)
public abstract class MixinPatternDualInputHatchProgrammingCircuit {

    @Inject(method = "setInventorySlotContents", at = @At("RETURN"), remap = false)
    private void ae2qol$wrapProgrammingCircuitsOnInsert(int index, ItemStack stack, CallbackInfo ci) {
        try {
            ItemStack wrapped = PhCircuitWrap.wrapPlainPattern(stack);
            if (wrapped == stack) return; // 无需改写（非样板/通配样板/没有 GT 电路）
            MixinPatternDualInputHatchAccess access = (MixinPatternDualInputHatchAccess) (Object) this;
            ItemStack[] slots = access.getAe2qolPattern();
            if (slots == null) return;
            if (index < 0 || index >= slots.length) return;
            slots[index] = wrapped;
        } catch (Throwable t) {
            // 不静默：改写失败不影响样板正常放入
            com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 放入 PH 仓时的编程器电路改写失败（该张保持原样）", t);
        }
    }
}
