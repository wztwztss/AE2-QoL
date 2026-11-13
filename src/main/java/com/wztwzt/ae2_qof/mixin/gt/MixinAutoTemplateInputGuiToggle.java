package com.wztwzt.ae2_qof.mixin.gt;

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
import com.wztwzt.ae2_qof.client.gui.TooltipTextButton;
import com.wztwzt.ae2_qof.compat.TenkeiTechIntegration;
import com.wztwzt.ae2_qof.network.SmartDoublingTogglePacket;

import appeng.api.networking.crafting.ICraftingProvider;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.singleblock.base.MTETieredMachineBlockBaseGui;

/**
 * 为 TenkeiTech 三台自带模板输入总线追加智能倍增开关。
 *
 * <p>
 * 三台的 GUI（{@code com.tenkeitech.gui.MTEAutoTemplateInputGui}）都继承
 * {@code MTEHatchBaseGui → MTEMachineWithFluidScreenBaseGui → MTETieredMachineBlockBaseGui}，
 * 自身没有覆写 {@code createBottomLeftCornerFlow}（1.1.2 字节码已核实），因此只需在 GT 基类
 * 的 RETURN 处按“机器类型是 {@code MTEHatchAutoTemplateInputME}”过滤追加一次，即可覆盖三台。
 *
 * <p>
 * 注入方式与 GT/GTNL 版一致：{@code BooleanSyncValue} + 坐标包写服务端 + 打开时拉一次真值；
 * 这样专用服务端也不会因 MUI2 双端构建差异而静默丢写。
 *
 * <p>
 * GT 为运行时可选依赖（compileOnly + 配置级 required=false）；本 mixin 只变换 GT 类，
 * 机器是否为 TenkeiTech 由 {@link TenkeiTechIntegration} 在运行时判定，未装 TenkeiTech 时
 * 开关不会出现在任何界面上，也不会有额外行为。
 */
@Mixin(MTETieredMachineBlockBaseGui.class)
public abstract class MixinAutoTemplateInputGuiToggle {

    /**
     * 构造器末尾捕获机器引用（不 @Shadow 泛型基类字段，避免擦除/继承字段的兼容问题）。
     * 该构造器在客户端与服务端都会执行，仅保存引用，无副作用。
     */
    @Unique
    private MTETieredMachineBlock ae2qol$machine;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void ae2qol$captureAutoTemplateMachine(MTETieredMachineBlock machine, CallbackInfo ci) {
        this.ae2qol$machine = machine;
    }

    @Inject(method = "createBottomLeftCornerFlow", at = @At("RETURN"), remap = false)
    private void ae2qol$addSmartDoublingToggle(ModularPanel panel, PanelSyncManager syncManager,
        CallbackInfoReturnable<Flow> cir) {
        if (!TenkeiTechIntegration.isAutoTemplateHatch(this.ae2qol$machine)) {
            return;
        }
        if (!(this.ae2qol$machine instanceof ICraftingProvider)) {
            return;
        }
        final Flow flow = cir.getReturnValue();
        if (flow == null) {
            return;
        }
        flow.child(ae2qol$createSmartDoublingToggle(syncManager));
    }

    @Unique
    private ToggleButton ae2qol$createSmartDoublingToggle(PanelSyncManager syncManager) {
        // 与 GT/GTNL 版同一套写法：本地先翻转 + 按坐标写服务端，打开界面时再拉服务端真值。
        final IMetaTileEntity mte = this.ae2qol$machine;
        final BooleanSyncValue smartDoublingSync = new BooleanSyncValue(
            () -> ((ISmartDoublingMedium) mte).isSmartDoublingEnabled(),
            val -> {
                ((ISmartDoublingMedium) mte).setSmartDoubling(val);
                SmartDoublingTogglePacket.sendFromClient(val, mte);
            });
        SmartDoublingTogglePacket.queryFromClient(mte);

        ToggleButton btn = new ToggleButton().value(smartDoublingSync)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_PATTERN_OPTIMIZE);
        btn.addTooltipLine(TooltipTextButton.langLines("gui.ae2_qof.smart_doubling"));
        for (String line : TooltipTextButton.langLines("gui.ae2_qof.smart_doubling.hint")
            .split("\n")) {
            btn.addTooltipLine(line);
        }
        return btn;
    }
}
