package com.wztwzt.ae2_qof.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.wztwzt.ae2_qof.MyMod;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IMEInventoryHandler;
import appeng.core.features.AEFeature;
import appeng.items.AEBaseInfiniteCell;
import appeng.items.contents.CellUpgrades;
import appeng.me.storage.CreativeCellInventory;
import appeng.me.storage.FluidCellInventoryHandler;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 无限水岩浆磁盘。
 * 放入 ME 驱动器后提供无限的水与岩浆（每个类型 Long 上限数量）。
 * 工作原理：复用 977 {@link CreativeCellInventory} —— 构造时读取 {@link #getConfigAEInventory} 中预置的
 * 水桶/岩浆桶并转换为流体，数量置为 2^52-1（≈4.5e15），达到近乎无限的效果。
 */
public class ItemInfinityWaterLavaCell extends AEBaseInfiniteCell {

    public ItemInfinityWaterLavaCell() {
        this.setMaxStackSize(1);
        this.setUnlocalizedName("infinity_water_lava_cell");
        this.setTextureName("ae2_qof:infinity_water_lava_cell");
        this.setFeature(java.util.EnumSet.of(AEFeature.StorageCells));
        this.setCreativeTab(com.wztwzt.ae2_qof.AE2QoLCreativeTab.INSTANCE);
    }

    public ItemInfinityWaterLavaCell register() {
        GameRegistry.registerItem(this, "infinity_water_lava_cell", MyMod.MODID);
        return this;
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return 2;
    }

    @Override
    public double getIdleDrain(ItemStack is) {
        return 2000.0;
    }

    @Override
    public boolean isEditable(ItemStack is) {
        return true;
    }

    @Override
    public appeng.api.storage.data.IAEStackType<?> getStackType() {
        return appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
    }

    @Override
    public IAEStackInventory getConfigAEInventory(ItemStack is) {
        return new InfinityWaterLavaConfig();
    }

    @Override
    public net.minecraft.inventory.IInventory getUpgradesInventory(ItemStack is) {
        return new CellUpgrades(is, 0);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        final String fz = Platform.openNbtData(is)
            .getString("FuzzyMode");
        try {
            return FuzzyMode.valueOf(fz);
        } catch (final Throwable t) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        Platform.openNbtData(is)
            .setString("FuzzyMode", fzMode.name());
    }

    @Override
    public IMEInventoryHandler getCellInventory(ItemStack o) {
        return new FluidCellInventoryHandler(new CreativeCellInventory(o));
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, EntityPlayer player, List<String> lines,
        boolean displayMoreInfo) {
        lines.add(net.minecraft.util.StatCollector.translateToLocal("item.infinity_water_lava_cell.tooltip"));
        lines.add(net.minecraft.util.StatCollector.translateToLocal("item.infinity_water_lava_cell.tooltip.usage"));
        lines.add(net.minecraft.util.EnumChatFormatting.DARK_GRAY + "ae2qof");
        super.addCheckedInformation(stack, player, lines, displayMoreInfo);
    }

    /** 预置水桶 + 岩浆桶的配置库存，供 CreativeCellInventory 构造时转换为无限流体。 */
    private static class InfinityWaterLavaConfig extends IAEStackInventory {

        InfinityWaterLavaConfig() {
            super(null, 2);
            FluidStack water = null;
            FluidStack lava = null;
            try {
                water = new FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("water"), 1000);
                lava = new FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("lava"), 1000);
            } catch (Throwable ignored) {}
            if (water != null) {
                this.putAEStackInSlot(0, appeng.util.item.AEFluidStack.create(water));
            }
            if (lava != null) {
                this.putAEStackInSlot(1, appeng.util.item.AEFluidStack.create(lava));
            }
        }

        @Override
        public void markDirty() {}
    }
}
