package com.wztwzt.ae2_qof.merged;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.wireless.BlockWirelessTransceiver;
import com.wztwzt.ae2_qof.wireless.TileWirelessTransceiver;
import com.wztwzt.ae2_qof.wireless.WirelessGuiHandler;

import cpw.mods.fml.common.network.IGuiHandler;

/**
 * 总 GUI 处理器：合并二合一终端的 Gui ID 与原有无线收发器 Gui ID。
 */
public class MergedGuiHandler implements IGuiHandler {

    private final WirelessGuiHandler wirelessGuiHandler = new WirelessGuiHandler();

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == BlockMergedTerminal.GUI_ID) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileMergedTerminal) {
                return new ContainerMergedTerminal(player.inventory, (TileMergedTerminal) te);
            }
            return null;
        }
        if (ID == BlockWirelessTransceiver.GUI_ID) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileWirelessTransceiver) {
                return wirelessGuiHandler.getServerGuiElement(ID, player, world, x, y, z);
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == BlockMergedTerminal.GUI_ID) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileMergedTerminal) {
                return new GuiMergedTerminal(player.inventory, (TileMergedTerminal) te);
            }
            return null;
        }
        if (ID == BlockWirelessTransceiver.GUI_ID) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileWirelessTransceiver) {
                return wirelessGuiHandler.getClientGuiElement(ID, player, world, x, y, z);
            }
        }
        return null;
    }
}
