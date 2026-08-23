package com.wztwzt.ae2_qof.mixin.nei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.client.NeiRecipeCapture;

import codechicken.nei.recipe.GuiRecipe;

/**
 * 捕获玩家当前浏览的 NEI 配方（handler + 页码），供二合一终端"一键填充"按钮读取。
 */
@Mixin(value = GuiRecipe.class, remap = false)
public abstract class MixinGuiRecipe {

    @Inject(method = "updateScreen", at = @At("HEAD"))
    private void ae2qol$captureCurrentRecipe(CallbackInfo ci) {
        GuiRecipe<?> gui = (GuiRecipe<?>) (Object) this;
        NeiRecipeCapture.captureFromGui(gui);
    }
}
