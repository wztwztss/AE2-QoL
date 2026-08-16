package com.gali.ae2_auto_pattern_upload.mixin.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gali.ae2_auto_pattern_upload.client.NetworkInventoryCache;
import com.gali.ae2_auto_pattern_upload.network.ExtractItemPacket;
import com.gali.ae2_auto_pattern_upload.network.ModNetwork;
import com.gali.ae2_auto_pattern_upload.network.RequestCraftingPacket;

import appeng.client.gui.AEBaseGui;
import appeng.container.AEBaseContainer;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.util.item.AEItemStack;
import codechicken.nei.PanelWidget;

/**
 * 混入 NEI PanelWidget.handleClick()，拦截物品面板点击：
 * - Shift+左键：从 AE2 网络取出一组物品；无物品但可合成 → 跳转合成下单
 * - 中键：打开 AE2 合成确认界面
 *
 * 与 AE2Things 兼容：检测 cir.getReturnValue()，如果已被其他 mod 处理则跳过。
 */
@Mixin(value = PanelWidget.class, remap = false)
public abstract class MixinPanelWidgetClick {

    @Shadow(remap = false)
    public abstract ItemStack getStackMouseOver(int mousex, int mousey);

    @Inject(method = "handleClick", at = @At("HEAD"), cancellable = true)
    public void ae2QoL$handleClick(int mousex, int mousey, int button, CallbackInfoReturnable<Boolean> cir) {
        // AE2Things 兼容：如果已被其他 mod 处理，跳过
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;

        try {
            ItemStack is = this.getStackMouseOver(mousex, mousey);
            if (is == null) return;

            GuiScreen gui = Minecraft.getMinecraft().currentScreen;

            // Shift+左键: 取出物品
            if (button == 0 && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) {
                if (handleExtractItem(gui, is)) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            // 中键: 合成下单
            if (button == 2) {
                if (handleCraftRequest(gui, is)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean handleExtractItem(GuiScreen gui, ItemStack is) {
        long count = NetworkInventoryCache.getCount(is);
        if (count > 0) {
            // 有物品 → 取出一组
            ModNetwork.CHANNEL.sendToServer(new ExtractItemPacket(is, is.getMaxStackSize()));
            return true;
        } else if (NetworkInventoryCache.isCraftable(is)) {
            // 无物品但可合成 → 跳转合成下单
            return handleCraftRequest(gui, is);
        }
        return false;
    }

    private boolean handleCraftRequest(GuiScreen gui, ItemStack is) {
        if (!NetworkInventoryCache.isCraftable(is)) return false;

        // 检查是否在 AE2 终端 GUI 中
        if (gui instanceof AEBaseGui) {
            AEBaseGui g = (AEBaseGui) gui;
            if (g.inventorySlots instanceof AEBaseContainer) {
                AEBaseContainer c = (AEBaseContainer) g.inventorySlots;
                // 使用 AE2 原版方式：PacketInventoryAction
                c.setTargetStack(AEItemStack.create(is));
                NetworkHandler.instance.sendToServer(new PacketInventoryAction(InventoryAction.AUTO_CRAFT, 0, 0L));
                return true;
            }
        }
        // 非 AE2 终端 GUI → 使用我们的自定义包
        ModNetwork.CHANNEL.sendToServer(new RequestCraftingPacket(is));
        return true;
    }
}
