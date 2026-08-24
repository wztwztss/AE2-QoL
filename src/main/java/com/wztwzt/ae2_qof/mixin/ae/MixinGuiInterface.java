package com.wztwzt.ae2_qof.mixin.ae;

import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.api.ISmartDoublingContainer;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.SmartDoublingTogglePacket;

import appeng.client.gui.implementations.GuiInterface;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerUpgradeable;

/**
 * 在 ME 接口 GUI 左侧按钮列末尾（guiTop + 170）追加智能倍增开关，处理点击并随
 * drawFG 刷新状态。
 */
@Mixin(GuiInterface.class)
public abstract class MixinGuiInterface extends GuiUpgradeable {

    public MixinGuiInterface(ContainerUpgradeable cvb) {
        super(cvb);
    }

    @Unique
    private GuiToggleButton smartDoublingBtn;

    @Inject(method = "addButtons", at = @At("TAIL"), remap = false)
    private void ae2qol$addSmartDoublingButton(CallbackInfo ci) {
        this.smartDoublingBtn = new GuiToggleButton(
            this.guiLeft - 18,
            this.guiTop + 170,
            178,
            194,
            "gui.ae2_qof.smart_doubling",
            // 预翻译并把字面 \n 转真换行：不依赖各 AE2/GTNL 版本对 hint 的内部处理方式，
            // 保证多行显示行为一致
            com.wztwzt.ae2_qof.client.gui.TooltipTextButton.langLines("gui.ae2_qof.smart_doubling.hint"));
        this.buttonList.add(this.smartDoublingBtn);
    }

    @Inject(method = "actionPerformed", at = @At("TAIL"), remap = true)
    private void ae2qol$onSmartDoublingButton(GuiButton btn, CallbackInfo ci) {
        if (btn == this.smartDoublingBtn) {
            final boolean next = !((ISmartDoublingContainer) this.cvb).getSmartDoubling();
            ModNetwork.CHANNEL.sendToServer(new SmartDoublingTogglePacket(next));
        }
    }

    @Inject(method = "drawFG", at = @At("TAIL"), remap = false)
    private void ae2qol$updateSmartDoublingButton(int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.smartDoublingBtn != null) {
            this.smartDoublingBtn.setState(((ISmartDoublingContainer) this.cvb).getSmartDoubling());
        }
    }
}
