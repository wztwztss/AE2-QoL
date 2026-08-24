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
import com.science.gtnl.common.gui.modularui.SuperCraftingInputHatchMEGui;
import com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME;
import com.wztwzt.ae2_qof.api.ISmartDoublingMedium;

import appeng.api.networking.crafting.ICraftingProvider;
import gregtech.api.modularui2.GTGuiTextures;

/**
 * 在 GTNL「超级样板输入总成 (ME)」（{@code SuperCraftingInputHatchMEGui}，meta 21504 流体版 /
 * 21505 纯物品版）的 ModularUI 底部按钮行追加智能倍增开关。
 * <p>
 * 该机器 {@code extends MTEHatchInputBus} 且实现 {@link ICraftingProvider}，
 * {@link MixinMTEHatchInputBus}（挂基类）已为其提供倍增能力与 NBT 持久化；
 * CPU 侧经 ISmartDoublingMedium 能力接口自动走 GT pushPattern N× 分支。
 * 此前唯一缺口是 GUI 无开关可勾选——本 mixin 补齐。
 * 开关状态通过 {@link BooleanSyncValue} 双向同步并写回仓 NBT（随存盘持久化）。
 * 构造器末尾捕获仓引用（对继承字段不做 @Shadow）。
 * GTNL 为运行时可选依赖（compileOnly），缺失时 mixin 静默跳过。
 * 均为 GTNH 模组类，编译名即运行时名，无需 remap。
 */
@Mixin(SuperCraftingInputHatchMEGui.class)
public abstract class MixinSuperCraftingInputHatchMEGui {

    @Unique
    private SuperCraftingInputHatchME ae2qol$machine;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void ae2qol$captureMachine(SuperCraftingInputHatchME hatch, CallbackInfo ci) {
        this.ae2qol$machine = hatch;
    }

    @Inject(method = "createBottomLeftCornerFlow", at = @At("RETURN"), remap = false)
    private void ae2qol$addSmartDoublingButton(ModularPanel panel, PanelSyncManager syncManager,
        CallbackInfoReturnable<Flow> cir) {
        if (!(this.ae2qol$machine instanceof ICraftingProvider)) {
            return;
        }
        cir.getReturnValue()
            .child(ae2qol$createSmartDoublingToggle(syncManager));
    }

    @Unique
    private ToggleButton ae2qol$createSmartDoublingToggle(PanelSyncManager syncManager) {
        BooleanSyncValue smartDoublingSync = new BooleanSyncValue(
            () -> ((ISmartDoublingMedium) (Object) this.ae2qol$machine).isSmartDoublingEnabled(),
            val -> ((ISmartDoublingMedium) (Object) this.ae2qol$machine).setSmartDoubling(val)).allowC2S();

        ToggleButton btn = new ToggleButton().value(smartDoublingSync)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_PATTERN_OPTIMIZE);
        // langLines 先把字面 \n 替换为真实换行再按行拆分——split("\\n") 正则语义匹配真实 LF，
        // 拆不开 lang 里的字面 \n（与 GT/PH mixin 同款修复）。
        btn.addTooltipLine(com.wztwzt.ae2_qof.client.gui.TooltipTextButton.langLines("gui.ae2_qof.smart_doubling"));
        for (String line : com.wztwzt.ae2_qof.client.gui.TooltipTextButton.langLines("gui.ae2_qof.smart_doubling.hint")
            .split("\n")) {
            btn.addTooltipLine(line);
        }
        return btn;
    }
}
