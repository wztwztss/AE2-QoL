package com.wztwzt.ae2_qof.mixin.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.wztwzt.ae2_qof.client.NeiRecipeCapture;
import com.wztwzt.ae2_qof.merged.GuiMergedTerminal;

import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.RecipeHandlerRef;

/**
 * 合并终端：点击 NEI 配方「+」按钮直接填入面板编码格。
 * <p>
 * GTNH NEI 2.8.19 默认要求按住 Shift 才执行覆盖层填充（否则只画幽灵叠加层），且只对
 * 已注册 overlay 的配方类型生效。本注入在按钮点击时对合并终端无条件执行直传填充，
 * 不依赖 Shift、不依赖配方 identifier，合成/处理（GT 机器）配方均可。
 * <p>
 * 处理配方（GT 机器）的 overlay identifier 非 "crafting"，构造时 hasOverlay=false 导致
 * 按钮 disabled，GuiButton.mousePressed 因 enabled=false 直接失败 → 点击不会派发到
 * overlayRecipe。因此强制 enabled=true，并直接从按钮绑定的 RecipeHandlerRef 提取配方
 * （handler + recipeIndex），不依赖最近捕获。
 */
@Mixin(value = GuiOverlayButton.class, remap = false)
public abstract class MixinGuiOverlayButton {

    @Shadow(remap = false)
    public GuiContainer firstGui;

    @Unique
    private boolean ae2qol$inOverlayFill;

    /**
     * 强制合并终端的覆盖层按钮可用：处理配方 hasOverlay=false 会让 enabled=false，
     * 进而 mousePressed 失败导致点击不派发。置为 true 后点击正常进入 overlayRecipe。
     * <p>
     * NEI 2.8.101 中 enabled 只在 updateEnabled() 内被赋值，且每次配方点击/幽灵叠加层
     * 重建（GT 处理配方无 presence overlay，itemPresenceCache 为空会每帧重建）都会重新
     * 调 updateEnabled() 把 enabled 算回 false——因此必须在 updateEnabled() TAIL 强制覆盖，
     * 只改 setRequireShiftForOverlayRecipe 不够（会被下一帧 updateEnabled() 覆盖）。
     */
    @Inject(method = "updateEnabled()V", at = @At("TAIL"))
    private void ae2qol$forceEnabledForMergedTerminalPerFrame(CallbackInfo ci) {
        if (firstGui != null && firstGui instanceof GuiMergedTerminal) {
            ((net.minecraft.client.gui.GuiButton) (Object) this).enabled = true;
        }
    }

    @Inject(method = "overlayRecipe(Z)V", at = @At("HEAD"), cancellable = true)
    private void ae2qol$directFill(boolean shift, CallbackInfo ci) {
        if (ae2qol$inOverlayFill) {
            return;
        }
        if (firstGui == null || !(firstGui instanceof GuiMergedTerminal)) {
            return;
        }
        ae2qol$inOverlayFill = true;
        try {
            // 优先：直接从按钮绑定的配方提取填充（不依赖最近捕获，处理/合成均可靠）
            RecipeHandlerRef ref = ((GuiRecipeButton) (Object) this).handlerRef;
            if (ref != null && ref.handler != null && ref.recipeIndex >= 0) {
                if (NeiRecipeCapture.fillMergedTerminal(firstGui, ref.handler, ref.recipeIndex)) {
                    ci.cancel();
                    return;
                }
            }
            // 兜底：最近浏览捕获的配方
            if (NeiRecipeCapture.fillMergedTerminalFromCapture(firstGui)) {
                ci.cancel();
            }
        } finally {
            ae2qol$inOverlayFill = false;
        }
    }

    /**
     * 合并终端的「+」始终视为可填充：本模组直传填充不依赖 NEI 的 crafting 覆盖层检查，
     * 否则处理配方（GT 机器）的 overlay identifier 非 "crafting" 会被判定为不可填充，
     * 按钮置灰并显示「合成栏大小不匹配」。
     */
    @Inject(method = "canFillCraftingGrid()Z", at = @At("HEAD"), cancellable = true)
    private void ae2qol$alwaysFillableForMergedTerminal(CallbackInfoReturnable<Boolean> cir) {
        if (firstGui != null && firstGui instanceof GuiMergedTerminal) {
            cir.setReturnValue(true);
        }
    }
}