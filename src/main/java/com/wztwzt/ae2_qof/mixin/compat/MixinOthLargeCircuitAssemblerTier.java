package com.wztwzt.ae2_qof.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveTierAdapter;

import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;

/**
 * OTH Technology（123Technology，{@code othtech}）「大型电路装配机」的电压封顶修正。
 *
 * <p>
 * 原实现（实例 {@code OTHTechnology-2.2.1.1.jar} 的 {@code javap -p -c}）：
 *
 * <pre>
 * private int checkEnergyHatchTier();
 *   36: getfield  MTEHatchEnergy.mTier:B      // 注册档
 *   83: getfield  MTEHatch.mTier:B            // exotic 仓
 *
 * checkMachine(): machineLimitedVoltage = GTValues.V[checkEnergyHatchTier()]   // 结构检查时缓存
 * setProcessingLogicPower(): logic.setAvailableVoltage(machineLimitedVoltage)
 * </pre>
 *
 * 于是自适应仓接高等级电网时，这台机器仍按注册档（IV）给并行逻辑的可用电压。
 * 只改读取来源；{@code OTEMegaEBFGTpp.checkMachine} 里的玻璃等级校验属结构规则，不动。
 */
@Mixin(targets = "com.newmaa.othtech.machine.OTELargeCircuitAssembler", remap = false)
public class MixinOthLargeCircuitAssemblerTier {

    /** GETFIELD 操作码。 */
    private static final int OPCODE_GETFIELD = 180;

    @Redirect(
        method = "checkEnergyHatchTier",
        at = @At(
            value = "FIELD",
            target = "Lgregtech/api/metatileentity/implementations/MTEHatchEnergy;mTier:B",
            opcode = OPCODE_GETFIELD),
        remap = false,
        require = 0)
    private byte ae2qof$othEnergyHatchTier(MTEHatchEnergy hatch) {
        return AdaptiveTierAdapter.effectiveTier(hatch, "OTH/大型电路装配机");
    }

    @Redirect(
        method = "checkEnergyHatchTier",
        at = @At(
            value = "FIELD",
            target = "Lgregtech/api/metatileentity/implementations/MTEHatch;mTier:B",
            opcode = OPCODE_GETFIELD),
        remap = false,
        require = 0)
    private byte ae2qof$othExoticHatchTier(MTEHatch hatch) {
        return AdaptiveTierAdapter.effectiveTier(hatch, "OTH/大型电路装配机(exotic)");
    }
}
