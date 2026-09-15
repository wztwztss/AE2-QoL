package com.wztwzt.ae2_qof.cover.stockmonitor.ae;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import appeng.api.networking.IGridNode;

import cn.dancingsnow.ae_wireless_nexus.network.TileWirelessControllerRef;
import cn.dancingsnow.ae_wireless_nexus.network.WirelessBindableEndpoint;
import cn.dancingsnow.ae_wireless_nexus.network.WirelessLeaseStatus;

import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCover;
import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCoverData;

/**
 * 将库存检测覆盖板包装为 Nexus WirelessBindableEndpoint，
 * 以便复用 Nexus 原生无线网络选择 UI（WirelessSelectionPanel）。
 * 不调用 registerEndpoint，不占用无线频道，仅用于网络选择/绑定。
 */
public class StockMonitorWirelessEndpoint implements WirelessBindableEndpoint {

    private final StockMonitorCover cover;
    private int priority = 0;

    public StockMonitorWirelessEndpoint(StockMonitorCover cover) {
        this.cover = cover;
    }

    // ===== WirelessEndpoint =====

    @Override
    public UUID getTargetNetworkId() {
        String id = cover.getCoverData().getNetworkId();
        if (id == null || id.isEmpty()) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public int getWirelessPriority() {
        return priority;
    }

    @Override
    public int getRequestedChannels() {
        return 0; // 覆盖板不占用无线频道
    }

    @Override
    public int getBindingPlayerId() {
        return -1;
    }

    @Override
    public String getStableEndpointKey() {
        TileEntity te = cover.getTile() instanceof TileEntity ? (TileEntity) cover.getTile() : null;
        if (te == null) return "ae2qol_stock_monitor_unknown";
        return "ae2qol_stock_monitor_" + te.getWorldObj().provider.dimensionId + "_"
            + te.xCoord + "_" + te.yCoord + "_" + te.zCoord;
    }

    @Override
    public IGridNode getWirelessGridNode() {
        return null; // 覆盖板无独立网格节点
    }

    @Override
    public boolean isWirelessEndpointValid() {
        return cover.getTile() != null;
    }

    @Override
    public void setWirelessLease(WirelessLeaseStatus status, TileWirelessControllerRef controller) {
        // 不租频道，空实现
    }

    // ===== WirelessBindableEndpoint =====

    @Override
    public World getEndpointWorld() {
        TileEntity te = cover.getTile() instanceof TileEntity ? (TileEntity) cover.getTile() : null;
        return te == null ? null : te.getWorldObj();
    }

    @Override
    public IChatComponent getEndpointDisplayName() {
        return new ChatComponentText("库存检测覆盖板");
    }

    @Override
    public WirelessLeaseStatus getWirelessLeaseStatus() {
        return getTargetNetworkId() != null ? WirelessLeaseStatus.CONNECTED : WirelessLeaseStatus.UNBOUND;
    }

    @Override
    public void bindToNetwork(UUID networkId, EntityPlayer player) {
        StockMonitorCoverData data = cover.getCoverData();
        if (networkId != null) {
            data.setNetworkId(networkId.toString());
        }
    }

    @Override
    public void unbindFromNetwork() {
        cover.getCoverData().setNetworkId("");
    }

    @Override
    public void setWirelessPriority(int priority) {
        this.priority = priority;
    }
}
