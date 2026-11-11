package com.wztwzt.ae2_qof.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveTierAdapter;

import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;

/**
 * GTNL（{@code sciencenotleisure}）多方块基类 {@code MultiMachineBase} 的电压封顶修正。
 *
 * <p>
 * 原实现（实例 {@code sciencenotleisure-dev-290-*.jar} 的 {@code javap -p -c}）：
 *
 * <pre>
 * public int checkEnergyHatchTier();
 *   33: getfield  MTEHatchEnergy.mTier:B     // 注册档
 *   75: getfield  MTEHatch.mTier:B           // exotic 仓
 *
 * public void setupParameters();          // 结构检查时调用 ⇒ mEnergyHatchTier = checkEnergyHatchTier()
 * public long getMachineVoltageLimit();   // = GTValues.V[mEnergyHatchTier]
 * </pre>
 *
 * 缓存字段 {@code mEnergyHatchTier} 又被 GTNL 一批机器直接当电压上限用：
 * {@code PrecisionAssembler:331-332}、{@code EngravingLaserPlant:233-235}、
 * {@code IntegratedAssemblyFacility:294-296}、{@code NanoAssemblerMarkL:217-219}、
 * {@code EdenGarden:366-371}、{@code GrandAssemblyLine:219}。
 * 不换读取来源的话，自适应仓接高等级电网也只会按注册档（IV）封顶。
 *
 * <p>
 * 只改读取来源，不动 GTNL 的缓存时机；结构类校验
 * （{@code checkGlassEnergyHatchRequirement}、{@code PrecisionAssembler.checkHatch}、
 * {@code LargeEssentiaGenerator.checkHatch}）刻意不动——那些是玻璃/外壳等级规则，应随注册档走。
 */
@Mixin(targets = "com.science.gtnl.common.machine.multiMachineBase.MultiMachineBase", remap = false)
public class MixinGtnlEnergyHatchTier {

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
    private byte ae2qof$gtnlEnergyHatchTier(MTEHatchEnergy hatch) {
        return AdaptiveTierAdapter.effectiveTier(hatch, "GTNL/checkEnergyHatchTier");
    }

    @Redirect(
        method = "checkEnergyHatchTier",
        at = @At(
            value = "FIELD",
            target = "Lgregtech/api/metatileentity/implementations/MTEHatch;mTier:B",
            opcode = OPCODE_GETFIELD),
        remap = false,
        require = 0)
    private byte ae2qof$gtnlExoticHatchTier(MTEHatch hatch) {
        return AdaptiveTierAdapter.effectiveTier(hatch, "GTNL/checkEnergyHatchTier(exotic)");
    }
}
