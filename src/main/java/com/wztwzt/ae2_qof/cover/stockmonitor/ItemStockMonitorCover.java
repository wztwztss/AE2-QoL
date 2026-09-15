package com.wztwzt.ae2_qof.cover.stockmonitor;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import com.wztwzt.ae2_qof.AE2QoLCreativeTab;

public class ItemStockMonitorCover extends Item {

    public ItemStockMonitorCover() {
        setUnlocalizedName("ae2_qof.stock_monitor_cover");
        setCreativeTab(AE2QoLCreativeTab.INSTANCE);
        setMaxStackSize(1);
    }

    /**
     * 运行时借用 GT 控制覆盖板（Cover_Controller, meta 730）的物品图标。
     * GT MetaGeneratedItem 的材质是程序化生成的，没有静态 png 可直接 setTextureName，
     * 因此在客户端动态获取 GT 物品的 IIcon。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        try {
            ItemStack controller = gregtech.api.enums.ItemList.Cover_Controller.get(1);
            if (controller != null && controller.getItem() != null) {
                IIcon icon = controller.getItem().getIconFromDamage(controller.getItemDamage());
                if (icon != null) return icon;
            }
        } catch (Throwable ignored) {
        }
        return super.getIconFromDamage(damage);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        for (int i = 1; i <= 3; i++) {
            String key = "item.ae2_qof.stock_monitor_cover.tooltip." + i;
            String line = StatCollector.translateToLocal(key);
            if (line != null && !line.isEmpty() && !line.equals(key)) {
                list.add(EnumChatFormatting.GRAY + line);
            }
        }
        list.add(EnumChatFormatting.DARK_GRAY + "ae2qof");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking() && stack.stackTagCompound != null) {
            stack.stackTagCompound.removeTag(StockMonitorCoverData.NBT_NETWORK_ID);
            stack.stackTagCompound.removeTag(StockMonitorCoverData.NBT_MODE);
            stack.stackTagCompound.removeTag(StockMonitorCoverData.NBT_SLOTS);
            // 向后兼容：旧版单槽键
            stack.stackTagCompound.removeTag("MonitorTarget");
            stack.stackTagCompound.removeTag("Threshold");
            stack.stackTagCompound.removeTag("PhantomItem");
            if (stack.stackTagCompound.hasNoTags()) {
                stack.stackTagCompound = null;
            }
            if (!world.isRemote) {
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.YELLOW + StatCollector
                            .translateToLocal("ae2_qof.cover.stock_monitor.config_cleared")));
            }
        }
        return stack;
    }
}
