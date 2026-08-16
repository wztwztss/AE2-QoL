package com.gali.ae2_auto_pattern_upload.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gali.ae2_auto_pattern_upload.wireless.gui.ContainerWireless;
import com.gali.ae2_auto_pattern_upload.wireless.gui.GuiWireless;

import cpw.mods.fml.common.network.IGuiHandler;

public class WirelessGuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileWirelessTransceiver) {
            return new ContainerWireless(player, te);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileWirelessTransceiver) {
            return new GuiWireless(player, (TileWirelessTransceiver) te);
        }
        return null;
    }
}
