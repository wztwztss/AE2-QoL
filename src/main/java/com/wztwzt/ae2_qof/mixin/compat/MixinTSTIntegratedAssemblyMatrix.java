package com.wztwzt.ae2_qof.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveTierAdapter;

import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;

/**
 * TST「集成装配矩阵」({@code IntegratedAssemblyMatrix}，配方池显示名「巨型装配线」) 的电压封顶修正。
 *
 * <p>
 * 原实现（实例 {@code TwistSpaceTechnology-0.8.1.jar} 的 {@code javap -p -c}）：
 *
 * <pre>
 * public void checkEnergyLimitation();
 *   33: getfield  MTEHatchEnergy.mTier:B      // 注册档，自适应仓恒为 5(IV)
 *   75: getfield  MTEHatch.mTier:B            // 同上（exotic 仓）
 *   92: putfield  maxVoltage:J                // 结构检查时缓存
 * ...
 * validateRecipe: if (recipe.mEUt > maxVoltage) → insufficientVoltage
 * </pre>
 *
 * 于是自适应仓接 UEV 电网、真实供电压为 {@code V[10]}，它仍按 {@code V[5]=8192} 封顶配方 ⇒
 * 显示「电压不足」。本 Mixin 把两处字段读取换成 {@link AdaptiveTierAdapter#effectiveTier}：
 * 普通仓读到的值与原字段完全一致（{@code getInputTier() == mTier}），自适应仓则拿到网络档位。
 *
 * <p>
 * 只改<b>读取来源</b>，不动 TST 的缓存时机与其余逻辑；结构门槛（{@code TST_BigBroArray} 的玻璃等级校验）
 * 本轮刻意不动——那是放置/结构规则，应随注册档而非电网波动。
 *
 * <p>
 * {@code require = 0}：TST 未安装或换了版本时本注入静默跳过而非崩溃；是否真的生效看日志
 * 「电压档位适配生效：TST/集成装配矩阵」。
 */
@Mixin(targets = "com.Nxer.TwistSpaceTechnology.common.machine.TST_IntegratedAssemblyMatrix", remap = false)
public class MixinTSTIntegratedAssemblyMatrix {

    /** GETFIELD 操作码（避免为了一个常量引入 ASM 依赖）。 */
    private static final int OPCODE_GETFIELD = 180;

    @Redirect(
        method = "checkEnergyLimitation",
        at = @At(
            value = "FIELD",
            target = "Lgregtech/api/metatileentity/implementations/MTEHatchEnergy;mTier:B",
            opcode = OPCODE_GETFIELD),
        remap = false,
        require = 0)
    private byte ae2qof$energyHatchTier(MTEHatchEnergy hatch) {
        return AdaptiveTierAdapter.effectiveTier(hatch, "TST/集成装配矩阵");
    }

    @Redirect(
        method = "checkEnergyLimitation",
        at = @At(
            value = "FIELD",
            target = "Lgregtech/api/metatileentity/implementations/MTEHatch;mTier:B",
            opcode = OPCODE_GETFIELD),
        remap = false,
        require = 0)
    private byte ae2qof$exoticHatchTier(MTEHatch hatch) {
        return AdaptiveTierAdapter.effectiveTier(hatch, "TST/集成装配矩阵(exotic)");
    }
}
