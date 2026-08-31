package com.wztwzt.ae2_qof.wireless;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

public class ItemBlockTransceiver extends ItemBlock {

    public ItemBlockTransceiver(Block block) {
        super(block);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("tile.wireless_transceiver.tooltip.1"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tile.wireless_transceiver.tooltip.2"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tile.wireless_transceiver.tooltip.3"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tile.wireless_transceiver.tooltip.4"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tile.wireless_transceiver.tooltip.5"));
        list.add(EnumChatFormatting.DARK_GRAY + "ae2qof");
    }
}
