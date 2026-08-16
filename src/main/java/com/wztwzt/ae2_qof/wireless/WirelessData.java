package com.wztwzt.ae2_qof.wireless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.World;

import com.wztwzt.ae2_qof.wireless.link.WirelessBlockLinkData;
import com.wztwzt.ae2_qof.wireless.link.WirelessBlockLinkManager;

/**
 * Global wireless channel registry with persistence via WirelessWorldData.
 * In-memory map holds sender TileEntity references for quick lookup.
 * Channel names are persisted to WorldSavedData on disk.
 */
public class WirelessData {

    private static final WirelessData INSTANCE = new WirelessData();

    private final Map<String, TileWirelessTransceiver> senderMap = new HashMap<String, TileWirelessTransceiver>();

    private WirelessData() {}

    public static WirelessData instance() {
        return INSTANCE;
    }

    public void register(String freq, TileWirelessTransceiver te) {
        if (freq == null || freq.isEmpty()) return;
        senderMap.put(freq, te);
        World world = te.getWorldObj();
        if (world != null && !world.isRemote) {
            WirelessWorldData.get(world)
                .addChannel(freq);
        }
    }

    public void unregister(String freq) {
        if (freq == null || freq.isEmpty()) return;
        senderMap.remove(freq);
    }

    /**
     * Unregister from a specific world's persistence.
     */
    public void unregister(String freq, World world) {
        if (freq == null || freq.isEmpty()) return;
        senderMap.remove(freq);
        if (world != null && !world.isRemote) {
            WirelessWorldData.get(world)
                .removeChannel(freq);
        }
        WirelessBlockLinkManager.instance()
            .unregister(freq);
    }

    public TileWirelessTransceiver getSender(String freq) {
        return senderMap.get(freq);
    }

    public List<String> getAllFrequencies() {
        World overworld = net.minecraftforge.common.DimensionManager.getWorld(0);
        if (overworld != null) {
            return WirelessWorldData.get(overworld)
                .getActiveChannels();
        }
        return new ArrayList<String>(senderMap.keySet());
    }

    /**
     * Register a global channel name without assigning to a sender.
     */
    public void addGlobalChannel(String freq) {
        if (freq == null || freq.isEmpty()) return;
        World overworld = net.minecraftforge.common.DimensionManager.getWorld(0);
        if (overworld != null) {
            WirelessWorldData.get(overworld)
                .addChannel(freq);
        }
    }

    public boolean isFrequencyTaken(String freq) {
        if (senderMap.containsKey(freq)) return true;
        World overworld = net.minecraftforge.common.DimensionManager.getWorld(0);
        if (overworld != null) {
            return WirelessWorldData.get(overworld)
                .hasChannel(freq);
        }
        return false;
    }

    /**
     * Restore registrations from WorldSavedData.
     */
    public void restoreFromWorldData(World world) {
        if (world == null || world.isRemote) return;
        WirelessWorldData wwd = WirelessWorldData.get(world);
        List<WirelessBlockLinkData> links = wwd.getBlockLinks();
        for (WirelessBlockLinkData link : links) {
            WirelessBlockLinkManager.instance()
                .register(link.frequency, link);
        }
    }

    public void clear() {
        senderMap.clear();
        WirelessBlockLinkManager.instance()
            .clear();
    }
}
