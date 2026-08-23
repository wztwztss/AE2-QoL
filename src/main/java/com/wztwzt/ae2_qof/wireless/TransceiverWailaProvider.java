package com.wztwzt.ae2_qof.wireless;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

public class TransceiverWailaProvider implements IWailaDataProvider {

    private static final int MAX_CHANNELS = 32;

    public static void register(IWailaRegistrar registrar) {
        TransceiverWailaProvider instance = new TransceiverWailaProvider();
        registrar.registerBodyProvider(instance, TileWirelessTransceiver.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return accessor.getStack();
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        TileEntity te = accessor.getTileEntity();
        if (te instanceof TileWirelessTransceiver) {
            TileWirelessTransceiver tile = (TileWirelessTransceiver) te;
            String modeStr = tile.isMode() ? StatCollector.translateToLocal("gui.ae2_qof.wireless.mode.sender")
                : StatCollector.translateToLocal("gui.ae2_qof.wireless.mode.receiver");
            currenttip.add(StatCollector.translateToLocal("gui.ae2_qof.wireless.mode") + ": " + modeStr);
            String freq = tile.getFrequency();
            currenttip.add(
                StatCollector.translateToLocal("gui.ae2_qof.wireless.freq") + ": "
                    + (freq.isEmpty() ? StatCollector.translateToLocal("gui.ae2_qof.wireless.none") : freq));
            if (tile.isConnected()) {
                int used = tile.getUsedChannels();
                int max = tile.getMaxChannels();
                currenttip.add(
                    EnumChatFormatting.AQUA + StatCollector
                        .translateToLocal("gui.ae2_qof.wireless.channels_used") + ": " + used + "/" + max);
            } else {
                int totalFreqs = accessor.getNBTData()
                    .getInteger("totalFreqs");
                currenttip.add(
                    EnumChatFormatting.AQUA + StatCollector.translateToLocal("gui.ae2_qof.wireless.channels_used")
                        + ": "
                        + totalFreqs);
            }
            if (tile.isPaused()) {
                currenttip.add(
                    EnumChatFormatting.GOLD + StatCollector.translateToLocal("gui.ae2_qof.wireless.status.paused"));
            } else {
                String statusStr = tile.isConnected()
                    ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gui.ae2_qof.wireless.status.connected")
                    : EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gui.ae2_qof.wireless.status.disconnected");
                currenttip.add(StatCollector.translateToLocal("gui.ae2_qof.wireless.status") + ": " + statusStr);
            }
        }
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (world != null && !world.isRemote) {
            tag.setInteger(
                "totalFreqs",
                WirelessData.instance()
                    .getAllFrequencies()
                    .size());
        }
        return tag;
    }
}
