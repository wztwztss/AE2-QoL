package com.wztwzt.ae2_qof.mixin.nei;

import static codechicken.nei.guihook.GuiContainerManager.drawItem;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.client.NetworkInventoryCache;
import com.wztwzt.ae2_qof.client.OverlayConfig;
import com.wztwzt.ae2_qof.util.CountFormatter;

import appeng.api.AEApi;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeHandlerRef;

/**
 * 在 NEI 配方界面（任意配方页）的每个物品格上叠加：
 * 1. 该物品在 AE 网络中可合成 -> 编码样板小图标；
 * 2. 该物品在 AE 网络中有存量 -> 数量角标。
 * 数据来源 {@link NetworkInventoryCache}（由 AE 终端 postUpdate 填充）。
 * <p>
 * 注意：NEI 2.8.19-GTNH 的 NEIRecipeWidget 没有 drawItem(PositionedStack,...) 方法，
 * 旧注入点无效，改为在 draw(int,int) 末尾叠加，直接遍历真实绘制的物品格。
 */
@Mixin(value = NEIRecipeWidget.class, remap = false)
public abstract class MixinNEIRecipeWidget {

    private static final ItemStack PATTERN = AEApi.instance()
        .definitions()
        .items()
        .encodedPattern()
        .maybeStack(1)
        .orNull();

    @Shadow(remap = false)
    protected RecipeHandlerRef handlerRef;

    @Inject(method = "draw(II)V", at = @At("TAIL"), remap = false)
    private void ae2qol$overlayRecipeSlots(int mousex, int mousey, CallbackInfo ci) {
        try {
            if (!OverlayConfig.isEnabled() || !NetworkInventoryCache.hasData()) {
                return;
            }
            if (handlerRef == null || handlerRef.handler == null) {
                return;
            }

            int index = handlerRef.recipeIndex;
            List<PositionedStack> stacks = new ArrayList<PositionedStack>();
            try {
                List<PositionedStack> ing = handlerRef.handler.getIngredientStacks(index);
                if (ing != null) {
                    stacks.addAll(ing);
                }
            } catch (Throwable ignored) {}
            try {
                List<PositionedStack> other = handlerRef.handler.getOtherStacks(index);
                if (other != null) {
                    stacks.addAll(other);
                }
            } catch (Throwable ignored) {}
            try {
                PositionedStack result = handlerRef.handler.getResultStack(index);
                if (result != null) {
                    stacks.add(result);
                }
            } catch (Throwable ignored) {}

            for (PositionedStack stack : stacks) {
                if (stack == null) {
                    continue;
                }
                ItemStack item = stack.item;
                if (item == null || item.getItem() == null) {
                    continue;
                }
                long count = NetworkInventoryCache.getCount(item);
                boolean craftable = NetworkInventoryCache.isCraftable(item);
                if (count <= 0 && !craftable) {
                    continue;
                }

                GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                if (craftable && PATTERN != null) {
                    drawItem(stack.relx + 11, stack.rely + 1, PATTERN);
                }

                if (count > 0) {
                    FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                    String label = CountFormatter.format(count);
                    fr.drawStringWithShadow(label, stack.relx + 1, stack.rely + 13, 0xAAFFFF);
                }

                GL11.glPopAttrib();
            }
        } catch (Throwable ignored) {}
    }
}
