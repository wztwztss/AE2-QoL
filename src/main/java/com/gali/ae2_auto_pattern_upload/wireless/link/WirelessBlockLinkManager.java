package com.gali.ae2_auto_pattern_upload.wireless.link;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.gali.ae2_auto_pattern_upload.MyMod;
import com.gali.ae2_auto_pattern_upload.wireless.TileWirelessTransceiver;
import com.gali.ae2_auto_pattern_upload.wireless.WirelessData;

import appeng.api.AEApi;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.tile.networking.TileCableBus;

public class WirelessBlockLinkManager {

    private static final WirelessBlockLinkManager INSTANCE = new WirelessBlockLinkManager();

    private final Map<String, List<WirelessBlockLinkData>> blockLinks = new HashMap<String, List<WirelessBlockLinkData>>();
    private final Map<String, IGridConnection> activeConnections = new HashMap<String, IGridConnection>();

    private int tickCounter = 0;

    private WirelessBlockLinkManager() {}

    public static WirelessBlockLinkManager instance() {
        return INSTANCE;
    }

    public void register(String freq, WirelessBlockLinkData data) {
        if (freq == null || freq.isEmpty() || data == null) return;
        List<WirelessBlockLinkData> list = blockLinks.get(freq);
        if (list == null) {
            list = new ArrayList<WirelessBlockLinkData>();
            blockLinks.put(freq, list);
        }
        for (WirelessBlockLinkData existing : list) {
            if (existing.getPositionKey()
                .equals(data.getPositionKey())) {
                return;
            }
        }
        list.add(data);
    }

    public void unregister(String freq) {
        if (freq == null || freq.isEmpty()) return;
        disconnectAll(freq);
        blockLinks.remove(freq);
    }

    public void unregister(String freq, String positionKey) {
        if (freq == null || freq.isEmpty() || positionKey == null) return;
        List<WirelessBlockLinkData> list = blockLinks.get(freq);
        if (list == null) return;
        Iterator<WirelessBlockLinkData> it = list.iterator();
        while (it.hasNext()) {
            WirelessBlockLinkData data = it.next();
            if (data.getPositionKey()
                .equals(positionKey)) {
                disconnect(freq + ":" + positionKey);
                it.remove();
                break;
            }
        }
        if (list.isEmpty()) {
            blockLinks.remove(freq);
        }
    }

    public List<WirelessBlockLinkData> getLinks(String freq) {
        List<WirelessBlockLinkData> list = blockLinks.get(freq);
        return list != null ? new ArrayList<WirelessBlockLinkData>(list) : new ArrayList<WirelessBlockLinkData>();
    }

    public List<WirelessBlockLinkData> getAllLinks() {
        List<WirelessBlockLinkData> result = new ArrayList<WirelessBlockLinkData>();
        for (List<WirelessBlockLinkData> list : blockLinks.values()) {
            result.addAll(list);
        }
        return result;
    }

    public List<WirelessBlockLinkData> getLinksByOwner(UUID uuid) {
        List<WirelessBlockLinkData> result = new ArrayList<WirelessBlockLinkData>();
        for (List<WirelessBlockLinkData> list : blockLinks.values()) {
            for (WirelessBlockLinkData data : list) {
                if (data.ownerUuid != null && data.ownerUuid.equals(uuid)) {
                    result.add(data);
                }
            }
        }
        return result;
    }

    public void removeLinksByPosition(int dim, int x, int y, int z) {
        String posKey = dim + ":" + x + ":" + y + ":" + z;
        Iterator<Map.Entry<String, List<WirelessBlockLinkData>>> it = blockLinks.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<WirelessBlockLinkData>> entry = it.next();
            String freq = entry.getKey();
            Iterator<WirelessBlockLinkData> linkIt = entry.getValue()
                .iterator();
            while (linkIt.hasNext()) {
                WirelessBlockLinkData data = linkIt.next();
                if (data.getPositionKey()
                    .equals(posKey)) {
                    disconnect(freq + ":" + posKey);
                    linkIt.remove();
                }
            }
            if (entry.getValue()
                .isEmpty()) {
                it.remove();
            }
        }
    }

    private void disconnect(String connKey) {
        IGridConnection conn = activeConnections.remove(connKey);
        if (conn != null) {
            try {
                conn.destroy();
            } catch (Throwable ignored) {}
        }
    }

    private void disconnectAll(String freq) {
        Iterator<Map.Entry<String, IGridConnection>> it = activeConnections.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<String, IGridConnection> entry = it.next();
            if (entry.getKey()
                .startsWith(freq + ":")) {
                try {
                    entry.getValue()
                        .destroy();
                } catch (Throwable ignored) {}
                it.remove();
            }
        }
    }

    public void processAll() {
        tickCounter++;
        if (tickCounter < 5) return;
        tickCounter = 0;

        for (Map.Entry<String, List<WirelessBlockLinkData>> entry : blockLinks.entrySet()) {
            String freq = entry.getKey();
            List<WirelessBlockLinkData> links = entry.getValue();

            TileWirelessTransceiver sender = WirelessData.instance()
                .getSender(freq);
            if (sender == null || sender.isInvalid()) {
                disconnectAll(freq);
                continue;
            }

            IGridNode senderNode = sender.getGridNode(ForgeDirection.UNKNOWN);
            if (senderNode == null) {
                disconnectAll(freq);
                continue;
            }

            Iterator<WirelessBlockLinkData> linkIt = links.iterator();
            while (linkIt.hasNext()) {
                WirelessBlockLinkData data = linkIt.next();
                String connKey = freq + ":" + data.getPositionKey();

                World targetWorld = getWorld(data.dimension);
                if (targetWorld == null) {
                    disconnect(connKey);
                    linkIt.remove();
                    continue;
                }

                TileEntity te = targetWorld.getTileEntity(data.x, data.y, data.z);
                if (te == null || te.isInvalid()) {
                    disconnect(connKey);
                    linkIt.remove();
                    continue;
                }

                if (!targetWorld.getChunkProvider()
                    .chunkExists(data.x >> 4, data.z >> 4)) {
                    continue;
                }

                IGridNode targetNode = getGridNode(te, data.direction);
                if (targetNode == null) {
                    disconnect(connKey);
                    linkIt.remove();
                    continue;
                }

                if (activeConnections.containsKey(connKey)) {
                    IGridConnection existing = activeConnections.get(connKey);
                    try {
                        IGridNode otherSide = existing.getOtherSide(targetNode);
                        if (otherSide == senderNode) {
                            continue;
                        }
                    } catch (Throwable ignored) {}
                    disconnect(connKey);
                }

                boolean found = false;
                for (IGridConnection conn : targetNode.getConnections()) {
                    if (conn.getOtherSide(targetNode) == senderNode) {
                        activeConnections.put(connKey, conn);
                        found = true;
                        break;
                    }
                }
                if (found) continue;

                try {
                    IGridConnection newConn = AEApi.instance()
                        .createGridConnection(senderNode, targetNode);
                    activeConnections.put(connKey, newConn);
                } catch (Throwable e) {
                    MyMod.LOG.warn("[APU] Failed to create block link for channel " + freq + ": " + e.getMessage());
                }
            }

            if (links.isEmpty()) {
                blockLinks.remove(freq);
            }
        }
    }

    private IGridNode getGridNode(TileEntity te, int direction) {
        if (te instanceof TileCableBus) {
            ForgeDirection dir = ForgeDirection.getOrientation(direction);
            IGridNode node = ((TileCableBus) te).getGridNode(dir);
            if (node != null) return node;
            IPart part = ((TileCableBus) te).getPart(dir);
            if (part != null) {
                node = part.getExternalFacingNode();
                if (node != null) return node;
            }
            return null;
        }

        if (te instanceof IGridHost) {
            IGridHost host = (IGridHost) te;
            IGridNode node = host.getGridNode(ForgeDirection.getOrientation(direction));
            if (node != null) return node;
            node = host.getGridNode(ForgeDirection.UNKNOWN);
            if (node != null) return node;
            for (int i = 0; i < 6; i++) {
                node = host.getGridNode(ForgeDirection.getOrientation(i));
                if (node != null) return node;
            }
        }

        return null;
    }

    private World getWorld(int dimensionId) {
        if (dimensionId == 0) {
            return DimensionManager.getWorld(0);
        }
        return DimensionManager.getWorld(dimensionId);
    }

    public void clear() {
        for (String connKey : new ArrayList<String>(activeConnections.keySet())) {
            try {
                activeConnections.get(connKey)
                    .destroy();
            } catch (Throwable ignored) {}
        }
        blockLinks.clear();
        activeConnections.clear();
    }
}
