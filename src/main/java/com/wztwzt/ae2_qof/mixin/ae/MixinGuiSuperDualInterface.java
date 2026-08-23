package com.wztwzt.ae2_qof.mixin.ae;

import net.minecraft.client.gui.GuiButton;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.science.gtnl.client.gui.GuiSuperDualInterface;
import com.science.gtnl.common.block.blocks.tile.TileEntitySuperDualInterface;
import com.wztwzt.ae2_qof.api.ISmartDoublingContainer;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.SmartDoublingTogglePacket;

import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.helpers.IInterfaceHost;

/**
 * 在 GTNotLeisure 的超级二合一 ME 接口 GUI（{@code GuiSuperDualInterface}，extends GuiUpgradeable）
 * 左侧按钮列（guiTop + 134，位于 fuzzyMode 与翻页按钮之间）追加智能倍增开关，处理点击并随 drawFG
 * 刷新状态。其容器 {@code ContainerSuperInterface}（extends ContainerInterface）已由
 * {@link MixinContainerInterface} 提供 {@link ISmartDoublingContainer} 同步，物品侧 DualityInterface
 * 由 {@link MixinDualityInterface} 实现倍增，流体侧自动逐轮（N=1）。GTNL 为运行时可选依赖
 * （compileOnly + 配置级 required=false），类缺失时该 mixin 静默跳过。GTNL 发布包为 SRG 混淆，
 * vanilla 覆写 actionPerformed 的运行时名为 func_146284_a，故按该名注入且 remap=false。
 */
@Mixin(GuiSuperDualInterface.class)
public abstract class MixinGuiSuperDualInterface extends GuiUpgradeable {

    public MixinGuiSuperDualInterface(ContainerUpgradeable cvb) {
        super(cvb);
    }

    @Unique
    private GuiToggleButton smartDoublingBtn;

    @Shadow
    @Final
    private IInterfaceHost host;

    @Inject(method = "addButtons", at = @At("TAIL"), remap = false)
    private void ae2qol$addSmartDoublingButton(CallbackInfo ci) {
        if (this.cvb instanceof ISmartDoublingContainer) {
            // 方块形态多一个 sidelessMode 按钮，整列下移 18：方块 134、线缆面板 116（fuzzyMode 与翻页之间）。
            final int btnY = this.host instanceof TileEntitySuperDualInterface ? this.guiTop + 134 : this.guiTop + 116;
            this.smartDoublingBtn = new GuiToggleButton(
                this.guiLeft - 18,
                btnY,
                178,
                194,
                "gui.ae2_qof.smart_doubling",
                "gui.ae2_qof.smart_doubling.hint");
            this.buttonList.add(this.smartDoublingBtn);
        }
    }

    @Inject(method = "func_146284_a", at = @At("TAIL"), remap = false)
    private void ae2qol$onSmartDoublingButton(GuiButton btn, CallbackInfo ci) {
        if (this.smartDoublingBtn != null && btn == this.smartDoublingBtn
            && this.cvb instanceof ISmartDoublingContainer sdc) {
            final boolean next = !sdc.getSmartDoubling();
            ModNetwork.CHANNEL.sendToServer(new SmartDoublingTogglePacket(next));
        }
    }

    @Inject(method = "drawFG", at = @At("TAIL"), remap = false)
    private void ae2qol$updateSmartDoublingButton(int offsetX, int offsetY, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.smartDoublingBtn != null && this.cvb instanceof ISmartDoublingContainer sdc) {
            this.smartDoublingBtn.setState(sdc.getSmartDoubling());
        }
    }
}
