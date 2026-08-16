package com.wztwzt.ae2_qof.wireless;

import com.wztwzt.ae2_qof.MyMod;

import appeng.api.AEApi;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.me.GridNode;

/**
 * Manages wireless grid connections between sender and receiver transceivers.
 * Uses AE2's public API to create connections.
 */
public class WirelessLinkManager {

    /**
     * Process a tile entity's wireless link.
     * Called periodically from TileWirelessTransceiver.updateEntity().
     */
    public static void process(TileWirelessTransceiver te) {
        if (te.getWorldObj() == null || te.getWorldObj().isRemote) return;
        if (te.isInvalid()) return;

        String freq = te.getFrequency();
        if (freq == null || freq.isEmpty()) {
            if (te.isConnected()) {
                te.destroyWirelessConnection();
            }
            return;
        }

        if (te.isPaused()) {
            return;
        }

        if (te.isMode()) {
            // Sender: ensure this transceiver is the registered sender for the frequency.
            // Re-register if the current entry is missing, invalid, or has no grid node
            // (e.g. stale entry left behind after a chunk unload).
            TileWirelessTransceiver current = WirelessData.instance()
                .getSender(freq);
            if (current == null || current == te || current.isInvalid() || current.getGridNode(null) == null) {
                WirelessData.instance()
                    .register(freq, te);
            }
        } else {
            // Receiver: look up sender and maintain connection
            TileWirelessTransceiver sender = WirelessData.instance()
                .getSender(freq);
            if (sender != null && sender != te && !sender.isInvalid() && sender.getGridNode(null) != null) {
                String senderPos = sender.getWorldObj().provider.dimensionId + ":"
                    + sender.xCoord
                    + ":"
                    + sender.yCoord
                    + ":"
                    + sender.zCoord;
                String expectedPos = te.getOriginalSenderPos();
                if (expectedPos != null && !expectedPos.isEmpty() && !expectedPos.equals(senderPos)) {
                    if (te.isConnected()) {
                        te.destroyWirelessConnection();
                    }
                    return;
                }
                IGridNode senderNode = sender.getGridNode(null);
                IGridNode receiverNode = te.getGridNode(null);
                if (senderNode != null && receiverNode != null) {
                    // Check if already connected to this specific sender
                    if (te.getWirelessConnection() != null) {
                        IGridConnection existing = te.getWirelessConnection();
                        // Verify the existing connection is still valid and points to the right sender
                        try {
                            IGridNode otherSide = existing.getOtherSide(receiverNode);
                            if (otherSide == senderNode) {
                                // Connection is good
                                markConnected(te, sender, senderNode, receiverNode, existing);
                                return;
                            }
                        } catch (Throwable ignored) {}
                        // Connection is stale or wrong sender, destroy it
                        te.destroyWirelessConnection();
                    }

                    // Check if any existing connection to this sender exists in the node's connections
                    for (IGridConnection conn : receiverNode.getConnections()) {
                        if (conn.getOtherSide(receiverNode) == senderNode) {
                            // Connection already exists in AE2's graph, just track it
                            markConnected(te, sender, senderNode, receiverNode, conn);
                            return;
                        }
                    }

                    // Create new connection using public API
                    try {
                        IGridConnection newConn = AEApi.instance()
                            .createGridConnection(senderNode, receiverNode);
                        te.setWirelessConnection(newConn);
                        receiverNode.updateState();
                        markConnected(te, sender, senderNode, receiverNode, newConn);
                    } catch (Throwable e) {
                        MyMod.LOG.warn(
                            "[APU] Failed to create wireless connection for channel " + freq + ": " + e.getMessage());
                        if (te.isConnected()) {
                            te.setConnected(false);
                        }
                    }
                }
            } else {
                // No valid sender found, disconnect
                if (te.isConnected()) {
                    te.destroyWirelessConnection();
                }
            }
        }
    }

    private static void markConnected(TileWirelessTransceiver receiver, TileWirelessTransceiver sender,
        IGridNode senderNode, IGridNode receiverNode, IGridConnection conn) {
        receiver.setWirelessConnection(conn);
        sender.setWirelessConnection(conn);
        if (!receiver.isConnected()) {
            receiver.setConnected(true);
        }
        if (!sender.isConnected()) {
            sender.setConnected(true);
        }
        updateChannelCounts(receiver, senderNode, receiverNode, conn);
        updateChannelCounts(sender, senderNode, receiverNode, conn);
    }

    private static void updateChannelCounts(TileWirelessTransceiver te, IGridNode senderNode, IGridNode receiverNode,
        IGridConnection conn) {
        try {
            int used = conn.getUsedChannels();
            te.setUsedChannels(used);
            te.setMaxChannels(computeMaxChannels(te, conn));
        } catch (Throwable ignored) {
            te.setUsedChannels(0);
            te.setMaxChannels(32);
        }
        if (te.getWorldObj() != null) {
            te.getWorldObj()
                .markBlockForUpdate(te.xCoord, te.yCoord, te.zCoord);
        }
    }

    /**
     * Computes the number of channels this transceiver can distribute over its physical cable.
     * Takes the minimum capacity over all physical connections (e.g. normal cable = 8, dense = 32,
     * controller = MAX). The wireless connection itself is excluded. Falls back to 32 when there
     * are no physical connections or the neighbor node is not a GridNode.
     */
    private static int computeMaxChannels(TileWirelessTransceiver te, IGridConnection wirelessConn) {
        IGridNode node = te.getGridNode(null);
        if (node == null) {
            return 32;
        }
        int max = 32;
        boolean foundPhysical = false;
        for (IGridConnection c : node.getConnections()) {
            if (c == wirelessConn) {
                continue;
            }
            try {
                IGridNode otherSide = c.getOtherSide(node);
                if (otherSide instanceof GridNode) {
                    int neighborMax = ((GridNode) otherSide).getMaxChannels();
                    if (!foundPhysical || neighborMax < max) {
                        max = neighborMax;
                    }
                    foundPhysical = true;
                }
            } catch (Throwable ignored) {}
        }
        return foundPhysical ? max : 32;
    }
}
