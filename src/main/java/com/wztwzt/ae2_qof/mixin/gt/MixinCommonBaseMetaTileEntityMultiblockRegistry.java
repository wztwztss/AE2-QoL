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
 * 注入 {@link CommonBaseMetaTileEntity#handleFirstTick(boolean)}（注册）
 * 与 {@link CommonBaseMetaTileEntity#onUnload()}（注销）。
 * 所有多方块主机加载时自动注册到 {@link MultiblockRegistry}，卸载时自动移除。
 * <p>
 * 3.35.0-fix7（审查 P1-16）：旧实现只注册不注销——{@code unregister} 全仓零调用、
 * {@code serverStopping} 也不清，静态强引用（helper↔World 互相持有）导致旧世界的
 * 多方块跨存档滞留内存。GT 的 {@code onUnload()} 在方块被拆/区块卸载时都会走到
 * （字节码已核：{@code CommonBaseMetaTileEntity.onUnload} 非 final、可注入），
 * 在这里补注销是幂等的（从 Set 移除不存在的元素无副作用）。
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

    /** 3.35.0-fix7：机器卸载（被拆/区块卸载）时从登记表移除——堵住"只进不出"的静态泄漏。 */
    @Inject(method = "onUnload", at = @At("HEAD"))
    private void ae2qol$unregisterMultiblock(CallbackInfo ci) {
        if (this instanceof IGregTechTileEntity gregTechTileEntity) {
            IMetaTileEntity mte = gregTechTileEntity.getMetaTileEntity();
            if (mte instanceof MTEMultiBlockBase multiblock) {
                MultiblockRegistry.unregister(multiblock);
            }
        }
    }
}
