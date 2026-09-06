package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;

import com.wztwzt.ae2_qof.hatch.AE2MaintenanceHatchUniversal;

@Mixin(value = ProcessingLogic.class, remap = false)
public abstract class MixinProcessingLogicSpeed {

    @Shadow
    protected IVoidable machine;

    @Shadow
    protected double speedBoost;

    @Shadow
    protected int maxParallel;

    @Inject(method = "process", at = @At(value = "INVOKE", target = "Lgregtech/api/logic/ProcessingLogic;prepareCatalyst([Lnet/minecraft/item/ItemStack;)[Lnet/minecraft/item/ItemStack;", shift = At.Shift.BEFORE))
    private void ae2qol$applyHatchParams(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (machine instanceof MTEMultiBlockBase) {
            MTEMultiBlockBase multi = (MTEMultiBlockBase) machine;
            for (MTEHatchMaintenance hatch : multi.mMaintenanceHatches) {
                if (hatch instanceof AE2MaintenanceHatchUniversal) {
                    AE2MaintenanceHatchUniversal uh = (AE2MaintenanceHatchUniversal) hatch;
                    int parallel = uh.getEffectiveParallel();
                    if (parallel > 1) {
                        maxParallel = parallel;
                    }
                    double speed = uh.getEffectiveSpeedBoost();
                    if (speed != 1.0) {
                        speedBoost = speed;
                    }
                    return;
                }
            }
        }
    }
}
