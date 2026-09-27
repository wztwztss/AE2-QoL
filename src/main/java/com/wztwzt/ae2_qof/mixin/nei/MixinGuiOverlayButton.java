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
        // 3.22.0/3.23.0：通配样板编辑器（MUI2 的带容器屏 GuiContainerWrapper）同样需要强制可用
        // （GT 处理配方的 overlay identifier 不是 crafting，NEI 会把按钮算成 disabled ⇒ 点击不会被派发）
        if (firstGui != null && (firstGui instanceof GuiMergedTerminal
            || firstGui instanceof com.cleanroommc.modularui.screen.GuiContainerWrapper)) {
            ((net.minecraft.client.gui.GuiButton) (Object) this).enabled = true;
        }
    }

    @Inject(method = "overlayRecipe(Z)V", at = @At("HEAD"), cancellable = true)
    private void ae2qol$directFill(boolean shift, CallbackInfo ci) {
        if (ae2qol$inOverlayFill) {
            return;
        }
        if (firstGui == null || !(firstGui instanceof GuiMergedTerminal)) {
            // 3.22.0：通配样板界面里的加号 = 「从当前 NEI 配方推导通配规则（含配方模板）」，
            // 不落 AE2 原版填充。推导结果交给界面，用户确认后由 C2S 包在服务端写入样板 NBT。
            // 3.23.0：通配样板编辑器（MUI2 带容器屏）里的加号 = 「按当前 NEI 配方推导规则与模板 + 立即写回样板」。
            // 说明：MUI2 面板是构建期生成的，不能像旧的自绘界面那样即时刷新 ⇒ 这里直接落库，
            // 并用聊天栏回执 + 日志让用户与维护者都能一眼确认结果（符合"不留静默"原则）。
            if (firstGui instanceof com.cleanroommc.modularui.screen.GuiContainerWrapper) {
                ae2qol$inOverlayFill = true;
                try {
                    RecipeHandlerRef ref = ((GuiRecipeButton) (Object) this).handlerRef;
                    if (ref != null && ref.handler != null && ref.recipeIndex >= 0) {
                        com.wztwzt.ae2_qof.client.SmartWildcardRecipeDeriver.Result derived =
                            com.wztwzt.ae2_qof.client.SmartWildcardRecipeDeriver.derive(
                                ref.handler,
                                ref.recipeIndex,
                                net.minecraft.client.Minecraft.getMinecraft().theWorld);
                        if (derived != null && derived.ok) {
                            com.wztwzt.ae2_qof.client.SmartWildcardClientState.setDerived(derived);
                            com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL.sendToServer(
                                new com.wztwzt.ae2_qof.network.SmartWildcardRulesPacket(
                                    derived.state,
                                    derived.templateIn,
                                    derived.templateOut));
                            com.wztwzt.ae2_qof.MyMod.LOG
                                .info("[AE2QoL] NEI 加号推导并写回通配样板：{}", derived.summary);
                            ae2qol$chat("\u00a7a[AE2QoL] \u5df2\u6309 NEI \u914d\u65b9\u5199\u5165\uff1a" + derived.summary);
                        } else {
                            com.wztwzt.ae2_qof.MyMod.LOG.warn(
                                "[AE2QoL] NEI 加号推导失败（未产生规则）：{}",
                                derived == null ? "null" : derived.reason);
                            ae2qol$chat(
                                "\u00a7c[AE2QoL] \u63a8\u5bfc\u5931\u8d25\uff1a"
                                    + (derived == null ? "null" : derived.reason));
                        }
                    } else {
                        com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 加号被按下但拿不到配方上下文（handlerRef 为空）");
                    }
                } catch (Throwable t) {
                    com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 通配编辑器加号处理异常", t);
                } finally {
                    ae2qol$inOverlayFill = false;
                }
                ci.cancel();
                return;
            }
            if (firstGui instanceof com.wztwzt.ae2_qof.client.gui.GuiSmartWildcard) {
                ae2qol$inOverlayFill = true;
                try {
                    RecipeHandlerRef ref = ((GuiRecipeButton) (Object) this).handlerRef;
                    if (ref != null && ref.handler != null && ref.recipeIndex >= 0) {
                        com.wztwzt.ae2_qof.client.SmartWildcardRecipeDeriver.Result derived =
                            com.wztwzt.ae2_qof.client.SmartWildcardRecipeDeriver.derive(
                                ref.handler,
                                ref.recipeIndex,
                                net.minecraft.client.Minecraft.getMinecraft().theWorld);
                        com.wztwzt.ae2_qof.client.SmartWildcardClientState.setDerived(derived);
                        if (derived.ok) {
                            com.wztwzt.ae2_qof.MyMod.LOG.info("[AE2QoL] NEI 加号推导通配规则：{}", derived.summary);
                        } else {
                            com.wztwzt.ae2_qof.MyMod.LOG
                                .warn("[AE2QoL] NEI 加号推导失败（未产生规则）：{}", derived.reason);
                        }
                        ((com.wztwzt.ae2_qof.client.gui.GuiSmartWildcard) firstGui).ae2qol$reloadDerived();
                    } else {
                        com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 加号被按下但拿不到配方上下文（handlerRef 为空）");
                    }
                } catch (Throwable t) {
                    com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 通配界面加号处理异常", t);
                } finally {
                    ae2qol$inOverlayFill = false;
                }
                ci.cancel();
            }
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
        if (firstGui != null && (firstGui instanceof GuiMergedTerminal
            || firstGui instanceof com.cleanroommc.modularui.screen.GuiContainerWrapper)) {
            cir.setReturnValue(true);
        }
    }

    /** 聊天栏回执（仅客户端）：加号推导/写回的结果反馈，避免"点了没反应"的观感。 */
    @org.spongepowered.asm.mixin.Unique
    private void ae2qol$chat(String message) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(message));
            }
        } catch (Throwable t) {
            com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 聊天栏回执失败", t);
        }
    }
}
