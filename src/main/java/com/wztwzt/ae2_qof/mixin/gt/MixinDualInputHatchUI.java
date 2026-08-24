package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.wztwzt.ae2_qof.api.ISmartDoublingMedium;

import appeng.api.networking.crafting.ICraftingProvider;
import gregtech.api.modularui2.GTGuiTextures;
import reobf.proghatches.gt.metatileentity.DualInputHatch;

/**
 * 在 ProgrammableHatches 的 {@code DualInputHatch.populateUI}（编程样板输入总线 22130 /
 * 编程样板输入总成 MK.II 22179 由其子类 {@code PatternDualInputHatch} 继承）UI 左侧按钮列追加
 * 智能倍增开关。仅对实现 {@link ICraftingProvider} 的机器（即 {@code PatternDualInputHatch}）显示，
 * 普通 DualInputHatch 子类不显示。开关经 {@link BooleanSyncValue} 双向同步并写回仓 NBT。
 * PH 为运行时可选依赖（compileOnly + 配置级 required=false），缺失时 mixin 静默跳过。
 */
@Mixin(DualInputHatch.class)
public abstract class MixinDualInputHatchUI {

    @Inject(method = "populateUI", at = @At("RETURN"), remap = false)
    private void ae2qol$addSmartDoublingButton(ModularPanel builder, PosGuiData data, PanelSyncManager syncManager,
        UISettings uiSettings, CallbackInfo ci) {
        if (!(this instanceof ICraftingProvider)) {
            return;
        }
        BooleanSyncValue smartDoublingSync = new BooleanSyncValue(
            () -> ((ISmartDoublingMedium) (Object) this).isSmartDoublingEnabled(),
            val -> ((ISmartDoublingMedium) (Object) this).setSmartDoubling(val)).allowC2S();

        builder.child(ae2qol$createSmartDoublingToggle(smartDoublingSync));
    }

    @Unique
    private ToggleButton ae2qol$createSmartDoublingToggle(BooleanSyncValue smartDoublingSync) {
        ToggleButton btn = new ToggleButton().value(smartDoublingSync)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_PATTERN_OPTIMIZE)
            .pos(7, 62);
        // langLines 先把字面 \n 替换为真实换行再按行拆分——split("\\n") 正则语义匹配真实 LF，
        // 拆不开 lang 里的字面 \n（历史 bug，与 GT 样板输入机 mixin 同款修复）。
        btn.addTooltipLine(com.wztwzt.ae2_qof.client.gui.TooltipTextButton.langLines("gui.ae2_qof.smart_doubling"));
        for (String line : com.wztwzt.ae2_qof.client.gui.TooltipTextButton.langLines("gui.ae2_qof.smart_doubling.hint")
            .split("\n")) {
            btn.addTooltipLine(line);
        }
        return btn;
    }
}
