package com.wztwzt.ae2_qof.wireless.gui;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;

import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.WirelessChannelSyncPacket;
import com.wztwzt.ae2_qof.wireless.TileWirelessTransceiver;
import com.wztwzt.ae2_qof.wireless.WirelessData;

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
