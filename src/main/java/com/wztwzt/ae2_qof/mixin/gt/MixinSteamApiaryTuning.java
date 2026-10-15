package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.machine.multiblock.module.steamElevator.SteamApiaryModule;
import com.wztwzt.ae2_qof.gtnl.SteamElevatorTuning;

import gregtech.api.recipe.check.CheckRecipeResult;

/**
 * Steam Apiary Module 的调速 + 批处理后处理（3.29.0）。
 *
 * <p>
 * 上游一个 6000 tick 周期里最多吃 {@code mStorage.size() * 40} 个蜂王浆，并把产出按"吃饱程度"加成 ×1~3。
 * 因此额外 k 份就要再吃 k 份同样的蜂王浆；本实现**逐份实扣**，扣不满就停在能供上的份数（自动降级）。
 * 这样"产出 ×n"与"蜂王浆 ×n"始终配平，不会出现白送产出的情况。
 *
 * <p>
 * 仅在该模块真正处于"运行模式"时生效：输入/输出模式会置 {@code lEUt = 0}，此时直接放行不处理
 * （否则会把上一周期的 {@code mOutputItems} 重复放大）。
 */
@Mixin(value = SteamApiaryModule.class, remap = false)
public abstract class MixinSteamApiaryTuning {

    @Unique
    public boolean ae2qof$steamTuningApplied() {
        return true;
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), require = 0)
    private void ae2qof$tuneApiary(CallbackInfoReturnable<CheckRecipeResult> cir) {
        SteamElevatorTuning.ensureFresh();
        CheckRecipeResult result = cir.getReturnValue();
        if (result == null || !result.wasSuccessful()) return;
        if (!SteamElevatorTuning.enabled) return;

        SteamApiaryModule self = (SteamApiaryModule) (Object) this;
        if (!SteamElevatorTuning.machineBatchGateOk(self.isBatchModeEnabled())) return;
        // 输入/输出模式：lEUt=0 且产出是"转移"而非"生产"，不参与批处理
        if (Math.abs(self.lEUt) == 0L) return;

        int goal = SteamElevatorTuning.goalSingleTicks(self.mMaxProgresstime);
        if (goal <= 0) return;

        int wanted = SteamElevatorTuning.byTimeBatchCount(goal);
        int slots = Math.max(0, self.mMaxSlots);
        int perBatch = slots * SteamElevatorTuning.apiaryRoyalJellyPerSlot;
        int extra = 0;
        if (perBatch <= 0) {
            // 槽位为 0 或配置把蜂王浆设为免费：无输入可核算 ⇒ 不做批处理（宁可保守，也不白送产出）
            extra = 0;
        } else {
            // 一次性取列表 + 批量实扣（每份必须能凑齐才扣，扣不满就停在能供上的份数）
            extra = SteamElevatorTuning.consumeJellyBatches(self, perBatch, wanted - 1);
        }
        int n = 1 + extra;
        if (!SteamElevatorTuning.meetsBatchFloor(n)) {
            if (wanted > 1) {
                SteamElevatorTuning.warnOnce(
                    "ap:floor",
                    "[AE2QoL] SteamElevator 蜂箱模块只够 " + n
                        + " 份（抗卡顿门槛 "
                        + SteamElevatorTuning.minBatchesToApplyTuning
                        + "）：为**避免配方检查频率上升**已保持上游行为，补足蜂王浆后自动启用");
            }
            return;
        }

        // 并行 P：每 tick 处理 P 份 ⇒ 产出 ×(n×P)，蜂王浆同样按 (n×P − 1) 份实扣（严格配平）
        int total = n;
        int p = SteamElevatorTuning.effectiveParallel();
        if (p > 1 && perBatch > 0) {
            total = n + SteamElevatorTuning.consumeJellyBatches(self, perBatch, n * p - n);
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
                "ap:n",
                "[AE2QoL] SteamElevator 蜂箱模块批处理生效: 每操作 " + total
                    + " 份（并行 "
                    + p
                    + "）/ "
                    + self.mMaxProgresstime
                    + " tick，蜂王浆 "
                    + (total * perBatch)
                    + " 个");
        } else if (wanted > 1) {
            SteamElevatorTuning.warnOnce(
                "ap:short",
                "[AE2QoL] SteamElevator 蜂箱模块蜂王浆不足，已降级为 1 份/操作（需 " + perBatch + " 个/份；补足后自动恢复 " + wanted + " 份）");
        }
    }
}
