package com.wztwzt.ae2_qof.mixin.nei;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.client.NetworkInventoryCache;

import appeng.api.config.PinsRows;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IDisplayRepo;
import appeng.client.gui.implementations.GuiMEMonitorable;

/**
 * 3.14.0：合成产物 pin 置顶行增强。
 * <ul>
 * <li>删除 3.10.0 覆盖式展示条（drawScreen/mouseClicked 注入移除，遮挡与点击拦截不复存在），
 *     改用 GTNH rv3 原生 pin 系统（服务端 PinsHandler + PacketPinsUpdate + VirtualMEPinSlot 独立行渲染）；</li>
 * <li>自动扩展行数：pin 产物种类超过当前 crafting 行容量时，本地把可见 crafting 行数提升
 *     （上限 3 行），产物减少时自动回落；不回写玩家设置。</li>
 * </ul>
 */
@Mixin(value = GuiMEMonitorable.class, remap = false)
public abstract class MixinGuiMEMonitorable {

    // 注意：不可声明为 final——final shadow 字段的初始化器会被 Mixin 写入目标类构造器，
    // 把原生 repo 赋值覆盖为 null（3.14.0 首版即因此导致打开终端即崩）
    @Shadow(remap = false)
    private IDisplayRepo repo;

    @Shadow(remap = false)
    private PinsRows craftingPinsRows;

    @Shadow(remap = false)
    protected abstract void reinitalize();

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
     * 服务端行数设置应用后补偿：若当前 pin 占用需要更多行，则本地提升并重建。
     * （服务端值先落地，再按需上调；pin 减少时不补偿，自然回落到服务端值。）
     */
    @Inject(method = "setPinsRows", at = @At("TAIL"), remap = false)
    private void ae2qol$autoExpandAfterRowsSet(PinsRows craftingRows, PinsRows playerRows, CallbackInfo ci) {
        try {
            PinsRows needed = ae2qol$neededRows(craftingPinsRows);
            if (needed.ordinal() > craftingPinsRows.ordinal()) {
                craftingPinsRows = needed;
                reinitalize();
            }
        } catch (Throwable ignored) {}
    }

    /** 新 pin 数据到达（晚于行数包）：容量不足时提升行数并重建布局。 */
    @Inject(method = "setAEPins", at = @At("TAIL"), remap = false)
    private void ae2qol$autoExpandOnPins(IAEStack<?>[] pins, CallbackInfo ci) {
        try {
            PinsRows needed = ae2qol$neededRows(craftingPinsRows);
            if (needed.ordinal() > craftingPinsRows.ordinal()) {
                craftingPinsRows = needed;
                reinitalize();
            }
        } catch (Throwable ignored) {}
    }

    /** 按当前 crafting pin 区非空格数计算所需行数（上限 3 行，至少保持原值）。 */
    private PinsRows ae2qol$neededRows(PinsRows current) {
        int count = 0;
        for (int i = 0; i < 27; i++) {
            try {
                if (repo.getAEPin(i) != null) count++;
            } catch (Throwable ignored) {}
        }
        int rows = (count + 8) / 9;
        if (rows < 1) rows = 1;
        if (rows > 3) rows = 3;
        return rows > current.ordinal() ? PinsRows.values()[rows] : current;
    }
}
