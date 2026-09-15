package com.wztwzt.ae2_qof.cover.stockmonitor.ae;

import java.util.UUID;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGrid;

public final class AeConnector {

    public static final int CHANNEL_NONE = 0;
    public static final int CHANNEL_WIRELESS = 1;
    public static final int CHANNEL_NEIGHBOR = 2;

    private AeConnector() {}

    public static GridResult getGrid(String networkId, World world, int machineX, int machineY, int machineZ,
        ForgeDirection coverSide) {
        if (world == null) return new GridResult(null, CHANNEL_NONE);

        // 通道 A：无线（已绑定且 Nexus 可用）
        if (networkId != null && !networkId.isEmpty() && WirelessAeConnector.isNexusAvailable()) {
            try {
                UUID uuid = UUID.fromString(networkId);
                IGrid grid = WirelessAeConnector.getGridForNetwork(uuid, world);
                if (grid != null) return new GridResult(grid, CHANNEL_WIRELESS);
            } catch (IllegalArgumentException ignored) {}
        }

        // 通道 B：邻接直连
        IGrid grid = NeighborAeConnector.findGrid(world, machineX, machineY, machineZ, coverSide);
        if (grid != null) return new GridResult(grid, CHANNEL_NEIGHBOR);

        return new GridResult(null, CHANNEL_NONE);
    }

    public static final class GridResult {

        public final IGrid grid;
        public final int channel;

        public GridResult(IGrid grid, int channel) {
            this.grid = grid;
            this.channel = channel;
        }
    }
}
