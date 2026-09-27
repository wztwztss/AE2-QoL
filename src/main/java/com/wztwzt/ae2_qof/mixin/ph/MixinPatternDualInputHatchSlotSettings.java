package com.wztwzt.ae2_qof.mixin.ph;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.MyMod;

import reobf.proghatches.gt.metatileentity.PatternDualInputHatch;

/**
 * PH 家族的"按样板格独立设置"宿主（4.0.0 新增）：PH 22069 直接命中；
 * PH MK.II 22179 与本模组 MK.III 32108（继承 PH 基类）**自动继承**本 mixin 挂上的字段与方法。
 *
 * <p>为什么单独开一个 mixin 而不是塞进 {@link MixinPatternDualInputHatchAccess}：
 * 那个是 **interface** mixin（只用 {@code @Accessor}），接口里挂不了 {@code @Unique} 字段与构造级注入。
 *
 * <p>持久化方式与 GT/GTNL 一致：注入 PH 自己声明的 {@code saveNBTData/loadNBTData(NBTTagCompound)}
 * （javap 已确证两者都存在），键名 {@code ae2qolSlotMeta}。
 *
 * <p>PH 未安装时：与既有 {@code MixinPatternDualInputHatchAccess} 同样的处理约定 ——
 * 本配置 {@code "required": false}，且 PH 类型的引用只出现在 mixin 标注与方法体里，
 * 类不会加载 ⇒ 既不生效也不报错。
 */
@SuppressWarnings("MixinAnnotationTarget")
@Mixin(value = PatternDualInputHatch.class, remap = false)
public abstract class MixinPatternDualInputHatchSlotSettings
    implements com.wztwzt.ae2_qof.wildcard.ISlotSettingsHolder {

    @Unique
    private com.wztwzt.ae2_qof.wildcard.SlotSettingsStore ae2qol$slotSettings;

    @Override
    public com.wztwzt.ae2_qof.wildcard.SlotSettingsStore ae2qol$slotSettings() {
        if (this.ae2qol$slotSettings == null) {
            this.ae2qol$slotSettings = new com.wztwzt.ae2_qof.wildcard.SlotSettingsStore();
        }
        return this.ae2qol$slotSettings;
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"), remap = false)
    private void ae2qol$saveSlotSettings(NBTTagCompound tag, CallbackInfo ci) {
        try {
            tag.setTag(
                com.wztwzt.ae2_qof.wildcard.SlotSettingsStore.NBT_KEY,
                ae2qol$slotSettings().save());
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 保存按格设置（PH）失败", t);
        }
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"), remap = false)
    private void ae2qol$loadSlotSettings(NBTTagCompound tag, CallbackInfo ci) {
        try {
            this.ae2qol$slotSettings = new com.wztwzt.ae2_qof.wildcard.SlotSettingsStore();
            this.ae2qol$slotSettings.load(
                tag.getCompoundTag(com.wztwzt.ae2_qof.wildcard.SlotSettingsStore.NBT_KEY));
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 读取按格设置（PH）失败", t);
        }
    }
}
