package com.wztwzt.ae2_qof.mixin.gt;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.api.ISmartDoublingMedium;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/**
 * 为 GT 系样板输入仓（{@code MTEHatchInputBus} 及子类：GT {@code MTEHatchCraftingInputME}、
 * GTNotLeisure {@code SuperDualInputHatchME}、ProgrammableHatches {@code PatternDualInputHatch}）实现
 * {@link ISmartDoublingMedium}。
 * <p>
 * 仅当机器实现 AE2 {@link ICraftingProvider}（即 CPU 会向它 pushPattern 的样板介质）时倍增生效，
 * 普通输入仓 / 补货型 ME 仓 / 仅存样板的 PatternProvider 自动被排除。
 * 这些仓的内部缓冲：GT 仓无上限（pushPattern 恒成功）、PH 仓 isBusy 时拒绝但可收大堆栈，
 * 无法像 ME 接口那样对相邻机器 simulateAddStack 估算容量，故 getMaxMultiplier 直接返回配置上限，
 * 由 CPU 侧剩余轮数与原料可取性再裁剪。开关持久化到仓的 NBT。
 * <p>
 * GT/PH 为运行时可选依赖（compileOnly + 配置级 required=false），缺失时 mixin 静默跳过。
 * 均为 GTNH 模组类，编译名即运行时名，无需 remap。
 */
@Mixin(MTEHatchInputBus.class)
public abstract class MixinMTEHatchInputBus implements ISmartDoublingMedium {

    @Unique
    private boolean ae2qol$smartDoubling;

    @Override
    public boolean isSmartDoublingEnabled() {
        return this instanceof ICraftingProvider && this.ae2qol$smartDoubling;
    }

    public void setSmartDoubling(boolean enabled) {
        this.ae2qol$smartDoubling = enabled;
    }

    @Override
    public int getMaxMultiplier(ICraftingPatternDetails details) {
        Config.ensureFresh();
        return Math.max(1, Config.smartDoublingMaxRounds);
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"), remap = false)
    private void ae2qol$saveSmartDoubling(NBTTagCompound data, CallbackInfo ci) {
        data.setBoolean("ae2qolSmartDoubling", this.ae2qol$smartDoubling);
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"), remap = false)
    private void ae2qol$loadSmartDoubling(NBTTagCompound data, CallbackInfo ci) {
        this.ae2qol$smartDoubling = data.getBoolean("ae2qolSmartDoubling");
    }
}