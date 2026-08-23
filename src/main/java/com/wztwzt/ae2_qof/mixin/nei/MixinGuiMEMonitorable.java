package com.wztwzt.ae2_qof.mixin.nei;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.client.NetworkInventoryCache;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiMEMonitorable;

@Mixin(value = GuiMEMonitorable.class, remap = false)
public abstract class MixinGuiMEMonitorable {

    @Inject(method = "postUpdate", at = @At("HEAD"))
    private void ae2AutoPatternUpload$cacheNetworkData(List<IAEStack<?>> list, CallbackInfo ci) {
        try {
            for (IAEStack<?> stack : list) {
                if (stack instanceof IAEItemStack itemStack) {
                    net.minecraft.item.ItemStack mcStack = itemStack.getItemStack();
                    if (mcStack != null) {
                        NetworkInventoryCache.put(
                            net.minecraft.item.Item.getIdFromItem(mcStack.getItem()),
                            mcStack.getItemDamage(),
                            (int) Math.min(itemStack.getStackSize(), Integer.MAX_VALUE),
                            itemStack.isCraftable(),
                            itemStack.getStackSize());
                    }
                } else if (stack instanceof IAEFluidStack fluidStack) {
                    if (fluidStack.getFluid() != null) {
                        String fluidName = fluidStack.getFluid()
                            .getName();
                        long size = fluidStack.getStackSize();
                        boolean craftable = fluidStack.isCraftable();
                        NetworkInventoryCache.putFluid(fluidName, size, craftable);

                        // 反向注册：流体方块物品 → 流体名（让 getFluidStack 识别纯流体方块）
                        net.minecraft.block.Block block = fluidStack.getFluid()
                            .getBlock();
                        if (block != null) {
                            net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(block);
                            if (item != null) {
                                NetworkInventoryCache
                                    .registerFluidItem(fluidName, net.minecraft.item.Item.getIdFromItem(item), 0);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
