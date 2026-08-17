package com.wztwzt.ae2_qof.mixin.gt;

import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.wztwzt.ae2_qof.api.ISmartDoublingMedium;

import appeng.api.networking.crafting.ICraftingProvider;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.hatch.MTEHatchCraftingInputMEGui;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;

/**
 * 在 GT 样板输入仓（{@code MTEHatchCraftingInputMEGui}，样板输入总成(ME) 2714 / 样板输入总线(ME) 2715）
 * 的 ModularUI 底部按钮行追加智能倍增开关。机器恒为实现 {@link ICraftingProvider} 的
 * {@code MTEHatchCraftingInputME}，开关状态通过 {@link BooleanSyncValue} 双向同步并写回仓 NBT。
 * 不 @Shadow 继承链上的 machine 字段（旧版 Mixin 对继承字段支持不稳），改在构造器末尾捕获仓引用。
 * GT 为运行时可选依赖（compileOnly + 配置级 required=false），缺失时 mixin 静默跳过。
 */
@Mixin(MTEHatchCraftingInputMEGui.class)
public abstract class MixinMTEHatchCraftingInputMEGui {

    @Unique
    private MTEHatchCraftingInputME ae2qol$machine;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void ae2qol$captureMachine(MTEHatchCraftingInputME hatch, CallbackInfo ci) {
        this.ae2qol$machine = hatch;
    }

    @Inject(method = "createBottomLeftCornerFlow", at = @At("RETURN"), remap = false)
    private void ae2qol$addSmartDoublingButton(ModularPanel panel, PanelSyncManager syncManager,
            CallbackInfoReturnable<Flow> cir) {
        if (!(this.ae2qol$machine instanceof ICraftingProvider)) {
            return;
        }
        cir.getReturnValue().child(ae2qol$createSmartDoublingToggle(syncManager));
    }

    @Unique
    private ToggleButton ae2qol$createSmartDoublingToggle(PanelSyncManager syncManager) {
        BooleanSyncValue smartDoublingSync = new BooleanSyncValue(
                () -> ((ISmartDoublingMedium) (Object) this.ae2qol$machine).isSmartDoublingEnabled(),
                val -> ((ISmartDoublingMedium) (Object) this.ae2qol$machine).setSmartDoubling(val)).allowC2S();

        ToggleButton btn = new ToggleButton().value(smartDoublingSync)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_PATTERN_OPTIMIZE);
        btn.addTooltipLine(StatCollector.translateToLocal("gui.ae2_qof.smart_doubling"));
        // lang 中的 \n 不会被 ModularUI 自动拆行，手动按 \n 拆成多行。
        for (String line : StatCollector.translateToLocal("gui.ae2_qof.smart_doubling.hint").split("\\n")) {
            btn.addTooltipLine(line);
        }
        return btn;
    }
}