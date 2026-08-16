package com.wztwzt.ae2_qof.mixin.ae;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.ReplanPacket;
import com.wztwzt.ae2_qof.util.Replanner;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.widgets.GuiAeButton;

/**
 * 合成确认界面加入 replan 按钮：任务开始后可将按钮切换为 replan，重新规划订单。
 */
@Mixin(GuiCraftConfirm.class)
public abstract class MixinGuiCraftConfirm extends AEBaseGui {

    @Shadow(remap = false)
    private GuiAeButton start;

    @Shadow(remap = false)
    @Final
    private IItemList<IAEStack<?>> storage;
    @Shadow(remap = false)
    @Final
    private IItemList<IAEStack<?>> pending;
    @Shadow(remap = false)
    @Final
    private IItemList<IAEStack<?>> missing;
    @Shadow(remap = false)
    @Final
    private List<IAEStack<?>> visual;

    private GuiAeButton replan = null;
    private boolean clickStart = false;

    public MixinGuiCraftConfirm(Container container) {
        super(container);
    }

    @Inject(method = "actionPerformed", at = @At(value = "HEAD"), cancellable = true, remap = true)
    private void ae2qol$actionPerformed(GuiButton btn, CallbackInfo ci) {
        if (btn == start) {
            clickStart = true;
        } else if (btn == replan) {
            clickStart = false;
            start.enabled = false;
            replan.visible = false;
            Replanner.clearIItemList(storage);
            Replanner.clearIItemList(pending);
            Replanner.clearIItemList(missing);
            this.visual.clear();
            ModNetwork.CHANNEL.sendToServer(new ReplanPacket());
        }
    }

    @Inject(method = "initGui", at = @At("TAIL"), remap = true)
    public void ae2qol$initGui(CallbackInfo ci) {
        this.buttonList.add(
            replan = new GuiAeButton(
                0,
                start.xPosition,
                start.yPosition,
                start.width,
                start.height,
                I18n.format("gui.ae2qol.replan"),
                ""));
        this.replan.visible = false;
    }

    @Inject(method = "drawFG", at = @At("HEAD"), remap = false)
    public void ae2qol$drawFG(CallbackInfo ci) {
        try {
            if (clickStart || !start.enabled) {
                replan.visible = true;
                start.visible = false;
            } else {
                replan.visible = false;
                start.visible = true;
            }
        } catch (Exception ignored) {}
    }
}
