package com.wztwzt.ae2_qof.cover.stockmonitor.ae;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGrid;
import appeng.me.helpers.IGridProxyable;

public final class NeighborAeConnector {

    private NeighborAeConnector() {}

    public static IGrid findGrid(World world, int x, int y, int z, ForgeDirection coverSide) {
        if (world == null) return null;

        int[][] candidates = {
            { x, y, z },
            { x + 1, y, z }, { x - 1, y, z },
            { x, y + 1, z }, { x, y - 1, z },
            { x, y, z + 1 }, { x, y, z - 1 }
        };

        for (int[] pos : candidates) {
            TileEntity te = world.getTileEntity(pos[0], pos[1], pos[2]);
            if (te == null) continue;
            IGrid grid = tryGetGrid(te);
            if (grid != null) return grid;
        }
        return null;
    }

    private static IGrid tryGetGrid(TileEntity te) {
        if (te instanceof IGridProxyable) {
            try {
                IGridProxyable proxyable = (IGridProxyable) te;
                if (proxyable.getProxy() != null
                    && proxyable.getProxy().getNode() != null) {
                    return proxyable.getProxy().getNode().getGrid();
                }
            } catch (Throwable ignored) {}
        }
        if (te instanceof appeng.api.networking.IGridHost) {
            try {
                appeng.api.networking.IGridHost host = (appeng.api.networking.IGridHost) te;
                for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                    appeng.api.networking.IGridNode node = host.getGridNode(dir);
                    if (node != null) {
                        IGrid grid = node.getGrid();
                        if (grid != null) return grid;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
