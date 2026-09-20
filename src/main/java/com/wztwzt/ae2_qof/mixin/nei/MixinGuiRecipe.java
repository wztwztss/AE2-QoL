package com.wztwzt.ae2_qof.mixin.nei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.client.NeiRecipeCapture;

import codechicken.nei.recipe.GuiRecipe;

/**
 * 捕获玩家当前浏览的 NEI 配方（handler + 页码），供二合一终端"一键填充"按钮读取。
 *
 * <p><b>3.19.0-fix45 修正（原先一直注入失败）</b>：旧写法只给了 MCP 开发名
 * {@code updateScreen}，而 {@code GuiRecipe} 继承自 {@code GuiContainer}，运行时该方法的
 * SRG 名是 {@code func_73876_c}。refmap 里没有这个条目（NEI 不是 Minecraft 类），
 * 于是 Mixin 就拿着字面量 "updateScreen" 去找，必然找不到，日志里表现为：</p>
 * <pre>
 * Mixin apply for mod ae2_qof failed ... nei.MixinGuiRecipe
 *   could not find any targets matching 'updateScreen' in codechicken/nei/recipe/GuiRecipe
 * </pre>
 * <p>该注入失败不会崩游戏，但会让「翻配方书 → 回合二终端点填充」这条路径读不到当前配方。
 * 现同时列出两个名字并显式 {@code remap = false}：生产环境命中 {@code func_73876_c}，
 * 开发环境命中 {@code updateScreen}，与 {@code MixinGuiSuperDualInterface} 处理
 * {@code func_146284_a} 的做法一致。</p>
 */
@Mixin(value = GuiRecipe.class, remap = false)
public abstract class MixinGuiRecipe {

    @Inject(method = { "updateScreen", "func_73876_c" }, at = @At("HEAD"), remap = false)
    private void ae2qol$captureCurrentRecipe(CallbackInfo ci) {
        GuiRecipe<?> gui = (GuiRecipe<?>) (Object) this;
        NeiRecipeCapture.captureFromGui(gui);
    }
}