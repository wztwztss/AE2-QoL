package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.hatch.adaptive.MultiblockRegistry;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.CommonBaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * 注入 {@link CommonBaseMetaTileEntity#handleFirstTick(boolean)}，
 * 所有多方块主机加载时自动注册到 {@link MultiblockRegistry}。
 * <p>
 * 借鉴 GTNL MixinCommonMetaTileEntityEnergyMonitor。
 */
@Mixin(value = CommonBaseMetaTileEntity.class, remap = false)
public abstract class MixinCommonBaseMetaTileEntityMultiblockRegistry {

    @Inject(method = "handleFirstTick", at = @At("TAIL"))
    private void ae2qol$registerMultiblock(boolean isServerSide, CallbackInfo ci) {
        if (!(this instanceof IGregTechTileEntity gregTechTileEntity)) {
            return;
        }
        IMetaTileEntity mte = gregTechTileEntity.getMetaTileEntity();
        if (mte instanceof MTEMultiBlockBase multiblock) {
            MultiblockRegistry.register(multiblock);
        }
    }
}
