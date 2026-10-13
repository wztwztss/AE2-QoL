package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.machine.multiblock.module.steamElevator.SteamOilDrillModule;
import com.wztwzt.ae2_qof.gtnl.SteamElevatorTuning;

import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.recipe.check.CheckRecipeResult;

/**
 * Steam Oil Drill Module（I/II/III）的调速 + 批处理后处理（3.29.0）。
 *
 * <p>本模块**没有输入**（只产地下流体），所以份数只受"时长上限"限制，必要时再受输出仓余量限制。
 *
 * <p><b>输出容量夹紧默认关闭</b>（{@code oilDrillOutputClamp}）：ME 流体输出仓在 {@code mOutputHatches} 里的
 * 容量语义不可靠，硬夹紧会把 ME 玩家的批处理静默降成 n=1（与需求目标相反）。需要在"小仓 + 不用 ME"的场景
 * 防止流体被 void 时，可显式打开该项。
 */
@Mixin(value = SteamOilDrillModule.class, remap = false)
public abstract class MixinSteamOilDrillTuning {

    @Unique
    public boolean ae2qof$steamTuningApplied() {
        return true;
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), require = 0)
    private void ae2qof$tuneOilDrill(CallbackInfoReturnable<CheckRecipeResult> cir) {
        SteamElevatorTuning.ensureFresh();
        CheckRecipeResult result = cir.getReturnValue();
        if (result == null || !result.wasSuccessful()) return;
        if (!SteamElevatorTuning.enabled) return;

        SteamOilDrillModule self = (SteamOilDrillModule) (Object) this;
        if (!SteamElevatorTuning.machineBatchGateOk(self.isBatchModeEnabled())) return;

        int goal = SteamElevatorTuning.goalSingleTicks(self.mMaxProgresstime);
        if (goal <= 0) return;

        int wanted = SteamElevatorTuning.byTimeBatchCount(goal);
        int n = wanted;
        if (!SteamElevatorTuning.meetsBatchFloor(n)) {
            return;
        }
        // 并行 P：抽油**没有输入** ⇒ 并行是纯加速（产出 ×(n×P)）；时长仍按 n 走，故检查频率不变
        // 抽油用专用键（默认 8），其余 4 类用全局键（默认 1）
        int p = SteamElevatorTuning.effectiveOilDrillParallel();
        int total = n * p;
        if (SteamElevatorTuning.oilDrillOutputClamp) {
            int clamped = clampByOutputSpace(self, total);
            if (clamped < total) {
                SteamElevatorTuning.warnOnce(
                    "od:space",
                    "[AE2QoL] SteamElevator 抽油模块输出仓余量不足，批次由 " + total + " 降为 " + clamped
                        + "（建议改用 ME 流体输出仓，或关闭 oilDrillOutputClamp）");
                total = clamped;
            }
        }

        self.mMaxProgresstime = SteamElevatorTuning.finalDuration(goal, n);
        long signed = self.lEUt;
        long magnitude = Math.abs(signed);
        if (magnitude > 0) {
            long tuned = SteamElevatorTuning.steamPerTick(magnitude);
            self.lEUt = signed < 0 ? -tuned : tuned;
        }
        self.mOutputFluids = SteamElevatorTuning.multiplyFluids(self.mOutputFluids, total);

        if (total > 1) {
            SteamElevatorTuning.infoOnce(
                "od:n",
                "[AE2QoL] SteamElevator 抽油模块批处理生效: 每操作 " + total + " 份（并行 " + p + "）/ "
                    + self.mMaxProgresstime
                    + " tick");
        }
    }

    /** 按非 ME 输出仓的剩余容量估算可承受份数（默认不调用）。 */
    private static int clampByOutputSpace(SteamOilDrillModule self, int wanted) {
        try {
            long perBatch = 0L;
            if (self.mOutputFluids != null) {
                for (net.minecraftforge.fluids.FluidStack fs : self.mOutputFluids) {
                    if (fs != null) perBatch += fs.amount;
                }
            }
            if (perBatch <= 0) return wanted;
            long space = 0L;
            for (MTEHatchOutput hatch : self.mOutputHatches) {
                net.minecraftforge.fluids.IFluidTank tank = (net.minecraftforge.fluids.IFluidTank) hatch;
                net.minecraftforge.fluids.FluidStack cur = tank.getFluid();
                space += Math.max(0, tank.getCapacity() - (cur == null ? 0 : cur.amount));
            }
            if (space <= 0) return 1;
            int fit = (int) (space / perBatch);
            return SteamElevatorTuning.clamp(fit, 1, wanted);
        } catch (Throwable t) {
            SteamElevatorTuning.warnOnce("od:clampfail", "[AE2QoL] SteamElevator 抽油模块容量夹紧失败，按原份数执行: " + t);
            return wanted;
        }
    }
}
