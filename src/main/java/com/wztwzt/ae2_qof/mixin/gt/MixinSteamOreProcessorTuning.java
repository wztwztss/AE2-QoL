package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.machine.multiblock.module.steamElevator.SteamOreProcessorModule;
import com.wztwzt.ae2_qof.gtnl.SteamElevatorTuning;

import gregtech.api.recipe.check.CheckRecipeResult;

/**
 * Steam Ore Processing Module 的调速 + 批处理后处理（3.29.0）。
 *
 * <p>上游一次操作处理 {@code currentParallelism}（= MAX_PARA × 2^电路，再按水/润滑剂/矿石夹紧）个矿石，
 * 并把它们从输入里**扣掉**（直接改 {@code ItemStack.stackSize}）。因此额外 k 份就要再扣 k × P 个矿石；
 * 本实现逐份实扣，扣不满就停在能供上的份数（自动降级）。
 *
 * <p><b>水/润滑剂不做额外扣减</b>：上游的 {@code depleteInput(FluidStack, boolean)} 在 GTNL 蒸汽机器里被重写为
 * **只遍历蒸汽输入仓**（`SteamMultiMachineBase:670-715`），而水/润滑剂不可能放进被锁定的蒸汽仓 ⇒ 上游本来
 * 就**没有真正扣过**这两种流体（只把它们当作"并行上限条件"来读）。本实现保持该上游行为，不做额外扣减，
 * 以免偏离上游语义；如需真正收费，属另一项改动。
 */
@Mixin(value = SteamOreProcessorModule.class, remap = false)
public abstract class MixinSteamOreProcessorTuning {

    @Unique
    public boolean ae2qof$steamTuningApplied() {
        return true;
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), require = 0)
    private void ae2qof$tuneOreProcessor(CallbackInfoReturnable<CheckRecipeResult> cir) {
        SteamElevatorTuning.ensureFresh();
        CheckRecipeResult result = cir.getReturnValue();
        if (result == null || !result.wasSuccessful()) return;
        if (!SteamElevatorTuning.enabled) return;

        SteamOreProcessorModule self = (SteamOreProcessorModule) (Object) this;
        if (!SteamElevatorTuning.machineBatchGateOk(self.isBatchModeEnabled())) return;

        int goal = SteamElevatorTuning.goalSingleTicks(self.mMaxProgresstime);
        if (goal <= 0) return;

        int wanted = SteamElevatorTuning.byTimeBatchCount(goal);
        int perBatch = Math.max(0, self.currentParallelism);
        int extra = 0;
        if (perBatch > 0) {
            // 一次性取列表 + 批量实扣（每份必须凑齐才扣；ALL_PROCESSABLE 反射句柄已缓存）
            extra = SteamElevatorTuning.consumeOreBatches(self, perBatch, wanted - 1);
        }
        int n = 1 + extra;
        if (!SteamElevatorTuning.meetsBatchFloor(n)) {
            if (wanted > 1) {
                SteamElevatorTuning.warnOnce(
                    "op:floor",
                    "[AE2QoL] SteamElevator 矿处模块只够 " + n + " 份（抗卡顿门槛 "
                        + SteamElevatorTuning.minBatchesToApplyTuning
                        + "）：为**避免配方检查频率上升**已保持上游行为，补足矿石后自动启用");
            }
            return;
        }

        // 并行 P：每 tick 处理 P 份 ⇒ 产出 ×(n×P)，矿石同样按 (n×P − 1) 份 × perBatch 实扣（严格配平）
        int total = n;
        int p = SteamElevatorTuning.effectiveParallel();
        if (p > 1 && perBatch > 0) {
            total = n + SteamElevatorTuning.consumeOreBatches(self, perBatch, n * p - n);
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
                "op:n",
                "[AE2QoL] SteamElevator 矿处模块批处理生效: 每操作 " + total + " 份 = 模块内部并行 " + perBatch + " × 批量并行 "
                    + p
                    + "，共 "
                    + (total * perBatch)
                    + " 个矿 / "
                    + self.mMaxProgresstime
                    + " tick");
        } else if (wanted > 1) {
            SteamElevatorTuning.warnOnce(
                "op:short",
                "[AE2QoL] SteamElevator 矿处模块矿石不足，已降级为 1 份/操作（每份需 " + perBatch + " 个矿；补足后自动恢复 "
                    + wanted
                    + " 份）");
        }
    }
}
