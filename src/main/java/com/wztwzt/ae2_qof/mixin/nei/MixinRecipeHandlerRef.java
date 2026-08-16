package com.wztwzt.ae2_qof.mixin.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.wztwzt.ae2_qof.client.ClientRecipeNameUtil;
import com.wztwzt.ae2_qof.client.ClientState;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeHandlerRef;

@Mixin(value = RecipeHandlerRef.class, remap = false)
public abstract class MixinRecipeHandlerRef {

    @Shadow(remap = false)
    public IRecipeHandler handler;

    @Inject(method = "fillCraftingGrid(Lnet/minecraft/client/gui/inventory/GuiContainer;I)V", at = @At("HEAD"))
    private void ae2AutoPatternUpload$captureFromFill(GuiContainer gui, int multiplier, CallbackInfo ci) {
        ae2AutoPatternUpload$captureRecipeName();
    }

    @Inject(method = "craft(Lnet/minecraft/client/gui/inventory/GuiContainer;I)Z", at = @At("HEAD"))
    private void ae2AutoPatternUpload$captureFromCraft(GuiContainer gui, int multiplier,
        CallbackInfoReturnable<Boolean> cir) {
        ae2AutoPatternUpload$captureRecipeName();
    }

    private void ae2AutoPatternUpload$captureRecipeName() {
        if (this.handler != null) {
            ClientRecipeNameUtil.captureFromRecipeHandler(this.handler);
            // 尝试从 GT NEI Handler 提取配方池 ID
            captureGTRecipeMap(this.handler);
        }
    }

    /**
     * 尝试从 GTNEIDefaultHandler 提取 recipeMap.unlocalizedName
     */
    private void captureGTRecipeMap(IRecipeHandler handler) {
        try {
            // 检查是否是 GT 的 NEI Handler
            Class<?> handlerClass = handler.getClass();
            String className = handlerClass.getName();
            if (!className.contains("gregtech") && !className.contains("GTNEI")) {
                return;
            }

            // 尝试 getRecipeMap() 方法
            java.lang.reflect.Method getRecipeMapMethod = null;
            try {
                getRecipeMapMethod = handlerClass.getMethod("getRecipeMap");
            } catch (NoSuchMethodException e) {
                // 尝试父类
                Class<?> superClass = handlerClass.getSuperclass();
                while (superClass != null) {
                    try {
                        getRecipeMapMethod = superClass.getMethod("getRecipeMap");
                        break;
                    } catch (NoSuchMethodException ex) {
                        superClass = superClass.getSuperclass();
                    }
                }
            }

            if (getRecipeMapMethod != null) {
                Object recipeMap = getRecipeMapMethod.invoke(handler);
                if (recipeMap != null) {
                    // 获取 unlocalizedName 字段
                    java.lang.reflect.Field nameField = recipeMap.getClass()
                        .getField("unlocalizedName");
                    String mapName = (String) nameField.get(recipeMap);
                    if (mapName != null && !mapName.isEmpty()) {
                        ClientState.pendingRecipeMap = mapName;
                        System.out.println("[APU] Captured GT recipe map from NEI: " + mapName);
                        return;
                    }
                }
            }

            // 备选：尝试 recipeMap 字段
            try {
                java.lang.reflect.Field recipeMapField = handlerClass.getField("recipeMap");
                Object recipeMap = recipeMapField.get(handler);
                if (recipeMap != null) {
                    java.lang.reflect.Field nameField = recipeMap.getClass()
                        .getField("unlocalizedName");
                    String mapName = (String) nameField.get(recipeMap);
                    if (mapName != null && !mapName.isEmpty()) {
                        ClientState.pendingRecipeMap = mapName;
                        System.out.println("[APU] Captured GT recipe map from NEI field: " + mapName);
                        return;
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}
    }
}
