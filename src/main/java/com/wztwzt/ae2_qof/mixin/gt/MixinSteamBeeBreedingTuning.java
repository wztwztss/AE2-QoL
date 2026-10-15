package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.machine.multiblock.module.steamElevator.SteamBeeBreedingModule;
import com.wztwzt.ae2_qof.gtnl.SteamElevatorTuning;

import gregtech.api.recipe.check.CheckRecipeResult;

/**
 * Steam Bee Breeding Module 的调速 + 批处理后处理（3.29.0）。
 *
 * <p>
 * 做法与理由见 {@link SteamElevatorTuning} 的类注释。本类只做三件事：
 * 把单份时长按配置压到 1 tick、把成功率化的"额外份数"按**逐份实扣蜂王浆**确定、并把产出按份数放大。
 *
 * <p>
 * 注意：上游一次操作吃 2×64=128 个蜂王浆，因此额外 k 份就要再吃 k×128 个；不够就停在能供上的份数
 * （= 需求方选定的"自动降级"）。若 forestry 反射不可用，则**不做批处理**（防止白送产出）。
 */
@Mixin(value = SteamBeeBreedingModule.class, remap = false)
public abstract class MixinSteamBeeBreedingTuning {

    /** 注入自检标记：存在 ⇔ 本 mixin 已被应用（见 SteamElevatorTuning.selfCheck）。 */
    @Unique
    public boolean ae2qof$steamTuningApplied() {
        return true;
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), require = 0)
    private void ae2qof$tuneBeeBreeding(CallbackInfoReturnable<CheckRecipeResult> cir) {
        SteamElevatorTuning.ensureFresh();
        CheckRecipeResult result = cir.getReturnValue();
        if (result == null || !result.wasSuccessful()) return;
        if (!SteamElevatorTuning.enabled) return;

        SteamBeeBreedingModule self = (SteamBeeBreedingModule) (Object) this;
        if (!SteamElevatorTuning.machineBatchGateOk(self.isBatchModeEnabled())) return;

        int goal = SteamElevatorTuning.goalSingleTicks(self.mMaxProgresstime);
        if (goal <= 0) return;

        int wanted = SteamElevatorTuning.byTimeBatchCount(goal);
        int perBatch = SteamElevatorTuning.beeBreedingRoyalJellyPerBatch;
        int extra = 0;
        if (perBatch <= 0) {
            extra = wanted - 1;
        } else if (SteamElevatorTuning.royalJellyProto() != null) {
            while (extra < wanted - 1 && SteamElevatorTuning.tryConsumeOneBatchJelly(self, perBatch)) {
                extra++;
            }
        }
        int n = 1 + extra;
        if (!SteamElevatorTuning.meetsBatchFloor(n)) {
            if (wanted > 1) {
                SteamElevatorTuning.warnOnce(
                    "bb:floor",
                    "[AE2QoL] SteamElevator 育种模块只够 " + n
                        + " 份（抗卡顿门槛 "
                        + SteamElevatorTuning.minBatchesToApplyTuning
                        + "）：为**避免配方检查频率上升**已保持上游行为，补足供料后自动启用");
            }
            return;
        }

        // 并行 P：每 tick 处理 P 份 ⇒ 产出 ×(n×P)，输入同样按 (n×P − 1) 份实扣（严格配平；扣不满就按实得份数产出）
        int total = n;
        int p = SteamElevatorTuning.effectiveParallel();
        if (p > 1) {
            int want = n * p - n;
            total = n + (perBatch > 0 ? SteamElevatorTuning.consumeJellyBatchesDeplete(self, perBatch, want) : want);
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
                "bb:n",
                "[AE2QoL] SteamElevator 育种模块批处理生效: 每操作 " + total
                    + " 份（并行 "
                    + p
                    + "）/ "
                    + self.mMaxProgresstime
                    + " tick");
        } else if (wanted > 1) {
            SteamElevatorTuning.warnOnce(
                "bb:short",
                "[AE2QoL] SteamElevator 育种模块输入不足，已降级为 1 份/操作（蜂王浆需 " + perBatch + " 个/份；补足后自动恢复 " + wanted + " 份）");
        }
    }
}
