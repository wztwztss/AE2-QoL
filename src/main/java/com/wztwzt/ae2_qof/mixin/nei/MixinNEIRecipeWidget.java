package com.wztwzt.ae2_qof.mixin.nei;

import static codechicken.nei.guihook.GuiContainerManager.drawItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.client.NetworkInventoryCache;
import com.wztwzt.ae2_qof.client.OverlayConfig;
import com.wztwzt.ae2_qof.util.CountFormatter;

import appeng.api.AEApi;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.NEIRecipeWidget;

/**
 * 在 NEI 配方界面（任意配方页）的每个物品格上叠加：
 * 1. 该物品在 AE 网络中可合成 -> 编码样板小图标；
 * 2. 该物品在 AE 网络中有存量 -> 数量角标。
 * 数据来源 {@link NetworkInventoryCache}（由 AE 终端 postUpdate 填充）。
 */
@Mixin(value = NEIRecipeWidget.class, remap = false)
public abstract class MixinNEIRecipeWidget {

    private static final ItemStack PATTERN = AEApi.instance()
        .definitions()
        .items()
        .encodedPattern()
        .maybeStack(1)
        .orNull();

    @Inject(method = "drawItem(Lcodechicken/nei/PositionedStack;IIIZ)V", at = @At("TAIL"))
    private void ae2AutoPatternUpload$overlayRecipeSlots(PositionedStack stack, int mousex, int mousey, int yshift,
        boolean doOverlay, CallbackInfo ci) {
        try {
            if (!OverlayConfig.isEnabled() || !NetworkInventoryCache.hasData()) {
                return;
            }
            ItemStack item = stack.item;
            if (item == null) {
                return;
            }
            long count = NetworkInventoryCache.getCount(item);
            boolean craftable = NetworkInventoryCache.isCraftable(item);
            if (count <= 0 && !craftable) {
                return;
            }

            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            if (craftable && PATTERN != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(0, 0, 200f);
                GL11.glScalef(0.4f, 0.4f, 0.4f);
                drawItem((int) ((stack.relx + 10) * 2.5), (int) (stack.rely * 2.5), PATTERN);
                GL11.glTranslatef(0, 0, -200f);
                GL11.glPopMatrix();
            }

            if (count > 0) {
                FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                GL11.glPushMatrix();
                GL11.glTranslatef(0, 0, 200f);
                GL11.glScalef(0.6f, 0.6f, 1.0f);
                String label = CountFormatter.format(count);
                int scaledX = (int) ((stack.relx + 1) / 0.6f);
                int scaledY = (int) ((stack.rely + 13) / 0.6f);
                fr.drawStringWithShadow(label, scaledX, scaledY, 0xAAFFFF);
                GL11.glTranslatef(0, 0, -200f);
                GL11.glPopMatrix();
            }

            GL11.glPopAttrib();
        } catch (Throwable ignored) {}
    }
}
