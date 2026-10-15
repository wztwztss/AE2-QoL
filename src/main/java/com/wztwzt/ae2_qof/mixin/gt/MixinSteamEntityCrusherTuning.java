package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.machine.multiblock.module.steamElevator.SteamEntityCrusherModule;
import com.wztwzt.ae2_qof.gtnl.SteamElevatorTuning;

import gregtech.api.recipe.check.CheckRecipeResult;

/**
 * Steam Entity Crusher Module（蒸汽屠宰场 / 气动刷怪笼）的调速 + 批处理后处理（3.30.0）。
 *
 * <p>本模块与另外 4 个的不同点：它**走 GT 原生 processingLogic**（{@code setupProcessingLogic} →
 * {@code doCheckRecipe} → {@code postCheckRecipe}），因此：
 * <ul>
 * <li>GT 原生批处理/并行机制对它是**生效**的（机器自身批处理开关打开 + 单份时长 &lt; 128 tick 时）；
 *     为避免"GT 已放大、我们又放大"的**双重叠加**，机器自身批处理开启时本处理器直接放行。</li>
 * <li>输入是 **0 数量的刷怪笼**（配方 {@code GTUtility.copyAmount(0, spawner)}，不消耗）⇒
 *     并行/批处理**不需要任何输入补偿**，是纯倍率。</li>
 * </ul>
 *
 * <p>上游自己的"双倍产出"判定（2% + 0.5%/同款刷怪笼，上限 34%）在 RETURN 之前已经掷过一次；
 * 本处理器把该结果 ×total（即 n 份共享同一次判定——均值不变、方差变大，与其余模块一致）。
 */
@Mixin(value = SteamEntityCrusherModule.class, remap = false)
public abstract class MixinSteamEntityCrusherTuning {

    @Unique
    public boolean ae2qof$steamTuningApplied() {
        return true;
    }

    @Inject(method = "checkProcessing", at = @At("RETURN"), require = 0)
    private void ae2qof$tuneEntityCrusher(CallbackInfoReturnable<CheckRecipeResult> cir) {
        SteamElevatorTuning.ensureFresh();
        CheckRecipeResult result = cir.getReturnValue();
        if (result == null || !result.wasSuccessful()) return;
        if (!SteamElevatorTuning.enabled) return;

        SteamEntityCrusherModule self = (SteamEntityCrusherModule) (Object) this;
        if (!SteamElevatorTuning.machineBatchGateOk(self.isBatchModeEnabled())) return;
        // 防双重叠加：机器自身的批处理开关打开时，GT 原生批处理已经放大过产出，我们不再叠加
        if (self.isBatchModeEnabled()) {
            SteamElevatorTuning.infoOnce(
                "ec:skip",
                "[AE2QoL] SteamElevator 屠宰场模块：机器自身批处理已开启，本模组不再叠加（避免双重放大）");
            return;
        }

        int goal = SteamElevatorTuning.goalSingleTicks(self.mMaxProgresstime);
        if (goal <= 0) return;

        int n = SteamElevatorTuning.byTimeBatchCount(goal);
        if (!SteamElevatorTuning.meetsBatchFloor(n)) {
            return;
        }

        // 刷怪笼是 0 数量输入（不消耗）⇒ 无需输入补偿，纯倍率
        int p = SteamElevatorTuning.effectiveEntityCrusherParallel();
        int total = n * p;

        self.mMaxProgresstime = SteamElevatorTuning.finalDuration(goal, n);
        long signed = self.lEUt;
        long magnitude = Math.abs(signed);
        if (magnitude > 0) {
            long tuned = SteamElevatorTuning.steamPerTick(magnitude);
            self.lEUt = signed < 0 ? -tuned : tuned;
        }
        self.mOutputItems = SteamElevatorTuning.multiplyItems(self.mOutputItems, total);
        self.mOutputFluids = SteamElevatorTuning.multiplyFluids(self.mOutputFluids, total);

        if (total > 1) {
            SteamElevatorTuning.infoOnce(
                "ec:n",
                "[AE2QoL] SteamElevator 屠宰场模块批处理生效: 每操作 " + total + " 份（并行 " + p + "）/ "
                    + self.mMaxProgresstime
                    + " tick");
        }
    }
}
