package com.gali.ae2_auto_pattern_upload.wireless;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;

public class WirelessBlocks {

    public static Block blockWirelessTransceiver;
    public static ItemWirelessConnector itemWirelessConnector;

    public static void preInit() {
        blockWirelessTransceiver = new BlockWirelessTransceiver();
        GameRegistry.registerBlock(blockWirelessTransceiver, ItemBlockTransceiver.class, "wireless_transceiver");
        GameRegistry.registerTileEntity(TileWirelessTransceiver.class, "wireless_transceiverTile");

        itemWirelessConnector = new ItemWirelessConnector();
        GameRegistry.registerItem(itemWirelessConnector, "wireless_connect");
    }
}
