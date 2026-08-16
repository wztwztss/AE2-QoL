package com.gali.ae2_auto_pattern_upload.wireless;

import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;

import com.gali.ae2_auto_pattern_upload.wireless.link.WirelessBlockLinkData;
import com.gali.ae2_auto_pattern_upload.wireless.link.WirelessBlockLinkManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class WirelessBlockEventListener {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = event.world;
        if (world == null || world.isRemote) return;

        int x = event.x;
        int y = event.y;
        int z = event.z;
        int dim = world.provider.dimensionId;

        String posKey = dim + ":" + x + ":" + y + ":" + z;

        for (WirelessBlockLinkData link : WirelessBlockLinkManager.instance()
            .getAllLinks()) {
            if (posKey.equals(link.getPositionKey())) {
                WirelessBlockLinkManager.instance()
                    .unregister(link.frequency);
                WirelessWorldData.get(world)
                    .removeBlockLink(link.frequency);
                return;
            }
        }
    }
}
