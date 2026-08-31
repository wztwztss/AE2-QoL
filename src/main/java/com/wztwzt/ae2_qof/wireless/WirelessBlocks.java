package com.wztwzt.ae2_qof.wireless;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;

public class WirelessBlocks {

    public static Block blockWirelessTransceiver;
    public static ItemWirelessConnector itemWirelessConnector;

    public static void preInit() {
        blockWirelessTransceiver = new BlockWirelessTransceiver();
        blockWirelessTransceiver.setCreativeTab(com.wztwzt.ae2_qof.AE2QoLCreativeTab.INSTANCE);
        GameRegistry.registerBlock(blockWirelessTransceiver, ItemBlockTransceiver.class, "wireless_transceiver");
        GameRegistry.registerTileEntity(TileWirelessTransceiver.class, "wireless_transceiverTile");

        itemWirelessConnector = new ItemWirelessConnector();
        GameRegistry.registerItem(itemWirelessConnector, "wireless_connect");
    }
}
