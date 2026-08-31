package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;

import com.wztwzt.ae2_qof.hatch.AE2MaintenanceHatchUniversal;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class MixinMultiblockParallel {

    @Shadow
    public java.util.ArrayList<MTEHatchMaintenance> mMaintenanceHatches;

    @Inject(method = "setupProcessingLogic", at = @At("RETURN"))
    private void ae2qol(ProcessingLogic logic, CallbackInfo ci) {
        for (MTEHatchMaintenance hatch : mMaintenanceHatches) {
            if (hatch instanceof AE2MaintenanceHatchUniversal uh) {
                applyParameters(logic, uh);
                return;
            }
        }
    }

    private void applyParameters(ProcessingLogic logic, AE2MaintenanceHatchUniversal uh) {
        int parallel = uh.getEffectiveParallel();
        if (parallel > 1) {
            logic.setMaxParallel(parallel);
        }
        double speedBoost = uh.getEffectiveSpeedBoost();
        if (speedBoost != 1.0) {
            logic.setSpeedBonus(speedBoost);
        }
    }
}
