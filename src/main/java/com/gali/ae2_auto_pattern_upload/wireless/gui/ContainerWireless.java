package com.gali.ae2_auto_pattern_upload.wireless.gui;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;

import com.gali.ae2_auto_pattern_upload.network.ModNetwork;
import com.gali.ae2_auto_pattern_upload.network.WirelessChannelSyncPacket;
import com.gali.ae2_auto_pattern_upload.wireless.TileWirelessTransceiver;
import com.gali.ae2_auto_pattern_upload.wireless.WirelessData;

public class ContainerWireless extends Container {

    public final TileWirelessTransceiver tile;
    private boolean needsChannelSync = true;

    public ContainerWireless(EntityPlayer player, TileEntity te) {
        this.tile = (TileWirelessTransceiver) te;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile != null && !tile.isInvalid()
            && tile.getWorldObj() != null
            && player.getDistanceSq(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5) <= 64.0;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (needsChannelSync) {
            needsChannelSync = false;
            List<String> channels = WirelessData.instance()
                .getAllFrequencies();
            for (Object obj : crafters) {
                if (obj instanceof EntityPlayerMP) {
                    ModNetwork.CHANNEL.sendTo(new WirelessChannelSyncPacket(channels), (EntityPlayerMP) obj);
                }
            }
        }
    }

    public void sendChannelSync() {
        if (tile.getWorldObj() != null && !tile.getWorldObj().isRemote) {
            List<String> channels = WirelessData.instance()
                .getAllFrequencies();
            for (Object obj : crafters) {
                if (obj instanceof EntityPlayerMP) {
                    ModNetwork.CHANNEL.sendTo(new WirelessChannelSyncPacket(channels), (EntityPlayerMP) obj);
                }
            }
        }
    }

    public TileWirelessTransceiver getTile() {
        return tile;
    }
}
