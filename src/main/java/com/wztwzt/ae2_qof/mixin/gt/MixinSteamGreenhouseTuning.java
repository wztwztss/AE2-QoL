package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.machine.multiblock.module.steamElevator.SteamGreenhouseModule;
import com.wztwzt.ae2_qof.gtnl.SteamElevatorTuning;

import gregtech.api.recipe.check.CheckRecipeResult;

/**
 * Steam Greenhouse Module 的调速 + 批处理后处理（3.29.0）。
 *
 * <p>上游每个耕作周期消耗 {@code getWaterUsage()}（默认 16,000 mB）的水，方式是**直接对
 * {@code getStoredFluids()} 返回的 FluidStack.amount 做减法**（见 {@code IGreenHouse:555-578}）。
 * 本实现用同一手法逐份补水：额外 k 份就再扣 k × 16,000；扣不满就停在能供上的份数。
 *
 * <p>只认"名字含 water"的流体（水/蒸馏水等）。若使用高 hydration 效价的特殊流体，请把
 * {@code greenhouseWaterPerCycle} 调成等效值，或只用普通水供料。
 *
 * <p>输入/输出模式（{@code setLEUt(0)}）不参与批处理。
 */
@Mixin(value = SteamGreenhouseModule.class, remap = false)
public abstract class MixinSteamGreenhouseTuning {

    @Unique
    public boolean ae2qof$steamTuningApplied() {
        return true;
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), require = 0)
    private void ae2qof$tuneGreenhouse(CallbackInfoReturnable<CheckRecipeResult> cir) {
        SteamElevatorTuning.ensureFresh();
        CheckRecipeResult result = cir.getReturnValue();
        if (result == null || !result.wasSuccessful()) return;
        if (!SteamElevatorTuning.enabled) return;

        SteamGreenhouseModule self = (SteamGreenhouseModule) (Object) this;
        if (!SteamElevatorTuning.machineBatchGateOk(self.isBatchModeEnabled())) return;
        if (Math.abs(self.lEUt) == 0L) return; // 输入/输出模式

        int goal = SteamElevatorTuning.goalSingleTicks(self.mMaxProgresstime);
        if (goal <= 0) return;

        int wanted = SteamElevatorTuning.byTimeBatchCount(goal);
        int perBatch = Math.max(0, SteamElevatorTuning.greenhouseWaterPerCycle);
        int extra = 0;
        if (perBatch <= 0) {
            extra = wanted - 1;
        } else {
            while (extra < wanted - 1 && SteamElevatorTuning.tryConsumeOneBatchWater(self, perBatch)) {
                extra++;
            }
        }
        int n = 1 + extra;
        if (!SteamElevatorTuning.meetsBatchFloor(n)) {
            if (wanted > 1) {
                SteamElevatorTuning.warnOnce(
                    "gh:floor",
                    "[AE2QoL] SteamElevator 温室模块只够 " + n + " 份（抗卡顿门槛 "
                        + SteamElevatorTuning.minBatchesToApplyTuning
                        + "）：为**避免配方检查频率上升**已保持上游行为，补足水后自动启用");
            }
            return;
        }

        // 并行 P：每 tick 处理 P 份 ⇒ 产出 ×(n×P)，水同样按 (n×P − 1) 份实扣（严格配平；扣不满按实得份数产出）
        int total = n;
        int p = SteamElevatorTuning.effectiveParallel();
        if (p > 1) {
            int want = n * p - n;
            if (perBatch <= 0) {
                total = n + want;
            } else {
                int more = 0;
                while (more < want && SteamElevatorTuning.tryConsumeOneBatchWater(self, perBatch)) {
                    more++;
                }
                total = n + more;
            }
        }

        self.mMaxProgresstime = SteamElevatorTuning.finalDuration(goal, n);
        long signed = self.lEUt;
        long magnitude = Math.abs(signed);
        if (magnitude > 0) {
            long tuned = SteamElevatorTuning.steamPerTick(magnitude);
            self.lEUt = signed < 0 ? -tuned : tuned;
        }
        self.mOutputItems = SteamElevatorTuning.multiplyItems(self.mOutputItems, total);

        if (total > 1) {
            SteamElevatorTuning.infoOnce(
                "gh:n",
                "[AE2QoL] SteamElevator 温室模块批处理生效: 每操作 " + total + " 份（并行 " + p + "）/ "
                    + self.mMaxProgresstime
                    + " tick，水 "
                    + (total * perBatch)
                    + " mB");
        } else if (wanted > 1) {
            SteamElevatorTuning.warnOnce(
                "gh:short",
                "[AE2QoL] SteamElevator 温室模块水不足，已降级为 1 份/操作（需 " + perBatch + " mB/份；补足后自动恢复 "
                    + wanted
                    + " 份）");
        }
    }
}
