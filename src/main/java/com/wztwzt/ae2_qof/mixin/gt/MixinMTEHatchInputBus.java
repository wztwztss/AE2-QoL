package com.wztwzt.ae2_qof.mixin.gt;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.api.ISmartDoublingMedium;
import com.wztwzt.ae2_qof.compat.TenkeiTechIntegration;

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

    /**
     * 三态开关：0=未显式设置（按机器类型取默认）、1=显式开、2=显式关。
     * <p>
     * 4.5.0-fix19 起 TenkeiTech 三台自带模板输入仓默认开启智能倍增（B1 决策）；
     * 其余 GT 仓在未设置时保持原来的默认关闭。用三态而不是布尔，是为了让
     * “默认开”不被旧存档里无条件写入的 {@code ae2qolSmartDoubling=false} 钉死。
     */
    @Unique
    private byte ae2qol$smartDoublingState;

    @Override
    public boolean isSmartDoublingEnabled() {
        if (!(this instanceof ICraftingProvider)) {
            return false;
        }
        if (this.ae2qol$smartDoublingState == 1) {
            return true;
        }
        if (this.ae2qol$smartDoublingState == 2) {
            return false;
        }
        return TenkeiTechIntegration.isAutoTemplateHatch(this);
    }

    @Override
    public void setSmartDoubling(boolean enabled) {
        this.ae2qol$smartDoublingState = (byte) (enabled ? 1 : 2);
    }

    @Override
    public int getMaxMultiplier(ICraftingPatternDetails details) {
        Config.ensureFresh();
        // 0 = 不限：GT 仓缓冲无上限，交由 CPU 侧按剩余轮数裁。
        return Config.smartDoublingMaxRounds <= 0 ? Integer.MAX_VALUE : Math.max(1, Config.smartDoublingMaxRounds);
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"), remap = false)
    private void ae2qol$saveSmartDoubling(NBTTagCompound data, CallbackInfo ci) {
        if (this.ae2qol$smartDoublingState == 0) {
            // 未显式拨动过：不写旧键，避免把“默认开”的机器在下次载入时钉成关闭。
            data.removeTag("ae2qolSmartDoubling");
            data.removeTag("ae2qolSmartDoublingExplicit");
            return;
        }
        data.setBoolean("ae2qolSmartDoubling", this.ae2qol$smartDoublingState == 1);
        data.setBoolean("ae2qolSmartDoublingExplicit", true);
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"), remap = false)
    private void ae2qol$loadSmartDoubling(NBTTagCompound data, CallbackInfo ci) {
        if (data.getBoolean("ae2qolSmartDoublingExplicit")) {
            this.ae2qol$smartDoublingState = (byte) (data.getBoolean("ae2qolSmartDoubling") ? 1 : 2);
            return;
        }
        if (!data.hasKey("ae2qolSmartDoubling")) {
            this.ae2qol$smartDoublingState = 0;
            return;
        }
        // 旧存档只有无条件写入的 ae2qolSmartDoubling：非默认开的 GT 仓继续按历史值兼容；
        // TenkeiTech 三台以前没有开关、该值恒为 false，不能当成“玩家关过”，否则默认开失效。
        this.ae2qol$smartDoublingState = TenkeiTechIntegration.isAutoTemplateHatch(this) ? (byte) 0
            : (byte) (data.getBoolean("ae2qolSmartDoubling") ? 1 : 2);
    }
}
