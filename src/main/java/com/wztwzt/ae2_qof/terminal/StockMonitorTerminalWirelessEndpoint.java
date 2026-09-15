package com.wztwzt.ae2_qof.terminal;

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

/**
 * 将库存统计终端包装为 Nexus WirelessBindableEndpoint，
 * 以便复用 Nexus 原生无线网络选择 UI（WirelessSelectionPanel）。
 * 不调用 registerEndpoint，不占用无线频道，仅用于网络选择/绑定。
 */
public class StockMonitorTerminalWirelessEndpoint implements WirelessBindableEndpoint {

    private final StockMonitorTerminal terminal;
    private int priority = 0;

    public StockMonitorTerminalWirelessEndpoint(StockMonitorTerminal terminal) {
        this.terminal = terminal;
    }

    // ===== WirelessEndpoint =====

    @Override
    public UUID getTargetNetworkId() {
        String id = terminal.getNetworkId();
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
        return 0; // 终端不占用无线频道
    }

    @Override
    public int getBindingPlayerId() {
        return -1;
    }

    @Override
    public String getStableEndpointKey() {
        TileEntity te = (TileEntity) terminal.getBaseMetaTileEntity();
        if (te == null) return "ae2qol_stock_terminal_unknown";
        return "ae2qol_stock_terminal_" + te.getWorldObj().provider.dimensionId + "_"
            + te.xCoord + "_" + te.yCoord + "_" + te.zCoord;
    }

    @Override
    public IGridNode getWirelessGridNode() {
        return null; // 终端无独立网格节点
    }

    @Override
    public boolean isWirelessEndpointValid() {
        return terminal.getBaseMetaTileEntity() != null;
    }

    @Override
    public void setWirelessLease(WirelessLeaseStatus status, TileWirelessControllerRef controller) {
        // 不租频道，空实现
    }

    // ===== WirelessBindableEndpoint =====

    @Override
    public World getEndpointWorld() {
        TileEntity te = (TileEntity) terminal.getBaseMetaTileEntity();
        return te == null ? null : te.getWorldObj();
    }

    @Override
    public IChatComponent getEndpointDisplayName() {
        return new ChatComponentText("库存统计终端");
    }

    @Override
    public WirelessLeaseStatus getWirelessLeaseStatus() {
        return getTargetNetworkId() != null ? WirelessLeaseStatus.CONNECTED : WirelessLeaseStatus.UNBOUND;
    }

    @Override
    public void bindToNetwork(UUID networkId, EntityPlayer player) {
        if (networkId != null) {
            terminal.setNetworkId(networkId.toString());
        }
    }

    @Override
    public void unbindFromNetwork() {
        terminal.setNetworkId("");
    }

    @Override
    public void setWirelessPriority(int priority) {
        this.priority = priority;
    }
}
