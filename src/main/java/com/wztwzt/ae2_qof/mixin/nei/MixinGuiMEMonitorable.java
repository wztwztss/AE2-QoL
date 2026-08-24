package com.wztwzt.ae2_qof.mixin.nei;

import java.util.Arrays;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.wztwzt.ae2_qof.client.NetworkInventoryCache;
import com.wztwzt.ae2_qof.client.render.RecentCraftedOverlay;

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

    /**
     * 合成完成产物展示条（3.10.0）：仅标准 ME 终端显示——子类（合成/样板/接口/无线/
     * 二合一终端等）按 getClass 精确排除。drawScreen 是 MC override 方法，运行时 SRG 名，
     * 必须 remap=true 走 refmap；guiLeft/guiTop 经 GuiContainerAccessor 读取。
     */
    @Inject(method = "drawScreen", at = @At("TAIL"), remap = true)
    private void ae2qol$drawRecentCrafted(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (((Object) this).getClass() != GuiMEMonitorable.class) return;
        try {
            net.minecraft.client.gui.inventory.GuiContainer host = (net.minecraft.client.gui.inventory.GuiContainer) (Object) this;
            com.wztwzt.ae2_qof.mixin.GuiContainerAccessor acc = (com.wztwzt.ae2_qof.mixin.GuiContainerAccessor) host;
            RecentCraftedOverlay.INSTANCE.draw(host, acc.getGuiLeft(), acc.getGuiTop(), mouseX, mouseY);
            net.minecraft.item.ItemStack hover = RecentCraftedOverlay.INSTANCE.getHoveredStack();
            if (hover != null) {
                long amount = RecentCraftedOverlay.INSTANCE.getHoveredAmount();
                String label = hover.getDisplayName() + (amount > 1 ? " §7× " + amount : "");
                ((appeng.client.gui.AEBaseGui) host)
                    .drawHoveringText(Arrays.asList(label, "§8点击提取一组到背包"), mouseX, mouseY);
            }
        } catch (Throwable ignored) {}
    }

    /** 产物格子点击提取：命中时吞掉点击，阻止穿透到被覆盖的第一行槽位。 */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void ae2qol$clickRecentCrafted(int xCoord, int yCoord, int btn, CallbackInfo ci) {
        if (((Object) this).getClass() != GuiMEMonitorable.class) return;
        try {
            net.minecraft.client.gui.inventory.GuiContainer host = (net.minecraft.client.gui.inventory.GuiContainer) (Object) this;
            com.wztwzt.ae2_qof.mixin.GuiContainerAccessor acc = (com.wztwzt.ae2_qof.mixin.GuiContainerAccessor) host;
            if (RecentCraftedOverlay.INSTANCE.handleClick(host, acc.getGuiLeft(), acc.getGuiTop(), xCoord, yCoord)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {}
    }
}
