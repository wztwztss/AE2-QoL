package com.wztwzt.ae2_qof.util;

import java.math.BigInteger;
import java.util.UUID;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.misc.WirelessNetworkManager;

/**
 * Server-thread transfers. Deposit-only helper: moves local EU into the shared wireless grid.
 * The input side (wireless input terminal / adaptive input hatches) uses the 3.18.0
 * mirror-accounting model (local buffer mirrors grid balance, consumption deducted in real time),
 * so no prepaid fill logic lives here anymore. See docs/mcp-full-function-audit-fix41.md A04 note.
 */
public final class WirelessEnergyTransfer {
    private WirelessEnergyTransfer() {}

    public static long deposit(IGregTechTileEntity tile, UUID owner) {
        if (owner == null || !tile.isServerSide()) return 0;
        long before = tile.getStoredEU();
        if (before <= 0) return 0;
        tile.decreaseStoredEnergyUnits(before, false);
        long removed = Math.max(0, before - tile.getStoredEU());
        if (removed == 0) return 0;
        if (!WirelessNetworkManager.addEUToGlobalEnergyMap(owner, removed)) {
            tile.increaseStoredEnergyUnits(removed, false);
            return 0;
        }
        tile.markDirty();
        return removed;
    }
}
