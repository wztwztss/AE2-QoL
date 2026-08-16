package com.wztwzt.ae2_qof.wireless;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import com.wztwzt.ae2_qof.wireless.link.WirelessBlockLinkData;

/**
 * Persists active wireless channel names and block links across server restarts.
 * Stored as WorldSavedData in the overworld.
 */
public class WirelessWorldData extends WorldSavedData {

    private static final String DATA_NAME = "ae2_wireless_channels";
    private final List<String> activeChannels = new ArrayList<String>();
    private final List<WirelessBlockLinkData> blockLinks = new ArrayList<WirelessBlockLinkData>();

    public WirelessWorldData() {
        super(DATA_NAME);
    }

    public WirelessWorldData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        activeChannels.clear();
        NBTTagList list = nbt.getTagList("channels", Constants.NBT.TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            String ch = list.getStringTagAt(i);
            if (ch != null && !ch.isEmpty() && !activeChannels.contains(ch)) {
                activeChannels.add(ch);
            }
        }

        blockLinks.clear();
        NBTTagList linkList = nbt.getTagList("wireless_block_links", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < linkList.tagCount(); i++) {
            NBTTagCompound tag = linkList.getCompoundTagAt(i);
            WirelessBlockLinkData data = WirelessBlockLinkData.readFromNBT(tag);
            if (data.frequency != null && !data.frequency.isEmpty()) {
                blockLinks.add(data);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (String channel : activeChannels) {
            list.appendTag(new NBTTagString(channel));
        }
        nbt.setTag("channels", list);

        NBTTagList linkList = new NBTTagList();
        for (WirelessBlockLinkData data : blockLinks) {
            linkList.appendTag(data.writeToNBT());
        }
        nbt.setTag("wireless_block_links", linkList);
    }

    public List<String> getActiveChannels() {
        return new ArrayList<String>(activeChannels);
    }

    public void addChannel(String channel) {
        if (channel == null || channel.isEmpty()) return;
        if (!activeChannels.contains(channel)) {
            activeChannels.add(channel);
            markDirty();
        }
    }

    public void removeChannel(String channel) {
        if (channel == null) return;
        if (activeChannels.remove(channel)) {
            markDirty();
        }
    }

    public boolean hasChannel(String channel) {
        return channel != null && activeChannels.contains(channel);
    }

    public List<WirelessBlockLinkData> getBlockLinks() {
        return new ArrayList<WirelessBlockLinkData>(blockLinks);
    }

    public void addBlockLink(WirelessBlockLinkData data) {
        if (data == null || data.frequency == null || data.frequency.isEmpty()) return;
        for (WirelessBlockLinkData existing : blockLinks) {
            if (existing.getPositionKey()
                .equals(data.getPositionKey())) {
                return;
            }
        }
        blockLinks.add(data);
        markDirty();
    }

    public void removeBlockLink(String frequency) {
        if (frequency == null) return;
        for (int i = blockLinks.size() - 1; i >= 0; i--) {
            if (frequency.equals(blockLinks.get(i).frequency)) {
                blockLinks.remove(i);
                markDirty();
                return;
            }
        }
    }

    public void removeBlockLink(String frequency, String positionKey) {
        if (frequency == null || positionKey == null) return;
        for (int i = blockLinks.size() - 1; i >= 0; i--) {
            WirelessBlockLinkData data = blockLinks.get(i);
            if (frequency.equals(data.frequency) && positionKey.equals(data.getPositionKey())) {
                blockLinks.remove(i);
                markDirty();
                return;
            }
        }
    }

    public void removeBlockLinksByPosition(int dim, int x, int y, int z) {
        String posKey = dim + ":" + x + ":" + y + ":" + z;
        boolean changed = false;
        for (int i = blockLinks.size() - 1; i >= 0; i--) {
            if (posKey.equals(
                blockLinks.get(i)
                    .getPositionKey())) {
                blockLinks.remove(i);
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    public static WirelessWorldData get(World world) {
        World overworld = world;
        if (world.provider != null && world.provider.dimensionId != 0) {
            World dim0 = DimensionManager.getWorld(0);
            if (dim0 != null) {
                overworld = dim0;
            }
        }
        WirelessWorldData data = (WirelessWorldData) overworld.loadItemData(WirelessWorldData.class, DATA_NAME);
        if (data == null) {
            data = new WirelessWorldData(DATA_NAME);
            overworld.setItemData(DATA_NAME, data);
        }
        return data;
    }
}
