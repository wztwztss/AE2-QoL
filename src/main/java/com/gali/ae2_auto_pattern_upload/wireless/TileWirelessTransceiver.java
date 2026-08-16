package com.gali.ae2_auto_pattern_upload.wireless;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.gali.ae2_auto_pattern_upload.wireless.link.WirelessBlockLinkManager;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridNotification;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;

public class TileWirelessTransceiver extends TileEntity implements IGridHost, IActionHost {

    private boolean mode;
    private String frequency = "";
    private UUID ownerUUID;
    private boolean isConnected;
    private boolean initialized = false;
    private IGridNode gridNode;
    private IGridConnection wirelessConnection;
    private int tickCounter = 0;
    private boolean isValidating = false;
    private boolean paused = false;
    private int usedChannels = 0;
    private int maxChannels = 32;
    private String originalSenderPos = "";

    private final IGridBlock blockProxy = new IGridBlock() {

        @Override
        public double getIdlePowerUsage() {
            return 1.0;
        }

        @Override
        public EnumSet<GridFlags> getFlags() {
            return EnumSet.of(GridFlags.DENSE_CAPACITY);
        }

        @Override
        public boolean isWorldAccessible() {
            return true;
        }

        @Override
        public DimensionalCoord getLocation() {
            return new DimensionalCoord(worldObj, xCoord, yCoord, zCoord);
        }

        @Override
        public AEColor getGridColor() {
            return AEColor.Transparent;
        }

        @Override
        public void onGridNotification(GridNotification notification) {}

        @Override
        public void setNetworkStatus(IGrid grid, int channelsInUse) {}

        @Override
        public EnumSet<ForgeDirection> getConnectableSides() {
            return EnumSet.allOf(ForgeDirection.class);
        }

        @Override
        public IGridHost getMachine() {
            return TileWirelessTransceiver.this;
        }

        @Override
        public void gridChanged() {}

        @Override
        public ItemStack getMachineRepresentation() {
            return WirelessBlocks.blockWirelessTransceiver != null
                ? new ItemStack(WirelessBlocks.blockWirelessTransceiver)
                : null;
        }
    };

    public TileWirelessTransceiver() {
        this.mode = false;
        this.frequency = "";
        this.isConnected = false;
    }

    // ===== Getters/Setters =====
    public boolean isMode() {
        return mode;
    }

    public void setMode(boolean mode) {
        this.mode = mode;
        markDirty();
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency != null ? frequency : "";
        markDirty();
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID uuid) {
        this.ownerUUID = uuid;
        markDirty();
    }

    public void savePlacer(UUID uuid) {
        this.ownerUUID = uuid;
        markDirty();
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        this.isConnected = connected;
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public IGridConnection getWirelessConnection() {
        return wirelessConnection;
    }

    public void setWirelessConnection(IGridConnection conn) {
        this.wirelessConnection = conn;
    }

    public int getUsedChannels() {
        return usedChannels;
    }

    public void setUsedChannels(int channels) {
        this.usedChannels = channels;
    }

    public int getMaxChannels() {
        return maxChannels;
    }

    public void setMaxChannels(int channels) {
        this.maxChannels = channels;
    }

    public String getOriginalSenderPos() {
        return originalSenderPos;
    }

    public void setOriginalSenderPos(String pos) {
        this.originalSenderPos = pos;
    }

    public void destroyWirelessConnection() {
        if (wirelessConnection != null) {
            try {
                wirelessConnection.destroy();
            } catch (Throwable ignored) {}
            wirelessConnection = null;
        }
        if (isConnected) {
            isConnected = false;
            markDirty();
        }
    }

    // ===== IGridHost =====
    @Override
    public IGridNode getGridNode(ForgeDirection direction) {
        return gridNode;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.DENSE;
    }

    @Override
    public void securityBreak() {
        destroyWirelessConnection();
        if (mode && frequency != null && !frequency.isEmpty()) {
            WirelessData.instance()
                .unregister(frequency, worldObj);
        }
        setConnected(false);
    }

    // ===== IActionHost =====
    @Override
    public IGridNode getActionableNode() {
        return gridNode;
    }

    // ===== TileEntity lifecycle =====
    @Override
    public void validate() {
        super.validate();
        if (!worldObj.isRemote && !initialized && !isValidating) {
            isValidating = true;
            try {
                gridNode = AEApi.instance()
                    .createGridNode(blockProxy);
                if (gridNode != null) {
                    gridNode.updateState();
                }
                initialized = true;
                if (mode && frequency != null && !frequency.isEmpty()) {
                    WirelessData.instance()
                        .register(frequency, this);
                }
            } finally {
                isValidating = false;
            }
        }
    }

    @Override
    public void invalidate() {
        if (!worldObj.isRemote) {
            destroyWirelessConnection();
            if (mode && frequency != null && !frequency.isEmpty()) {
                WirelessData.instance()
                    .unregister(frequency, worldObj);
            }
            if (gridNode != null) {
                gridNode.destroy();
                gridNode = null;
            }
        }
        super.invalidate();
        initialized = false;
    }

    @Override
    public void onChunkUnload() {
        if (!worldObj.isRemote) {
            destroyWirelessConnection();
            if (gridNode != null) {
                gridNode.destroy();
                gridNode = null;
            }
        }
        super.onChunkUnload();
        initialized = false;
    }

    public void removeAllBlockLinks() {
        if (!worldObj.isRemote && mode && frequency != null && !frequency.isEmpty()) {
            WirelessBlockLinkManager.instance()
                .unregister(frequency);
        }
    }

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            tickCounter++;
            if (tickCounter >= 5) {
                tickCounter = 0;
                if (frequency != null && !frequency.isEmpty()) {
                    WirelessLinkManager.process(this);
                }
                WirelessBlockLinkManager.instance()
                    .processAll();
            }
        }
    }

    // ===== NBT =====
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("mode", mode);
        tag.setString("frequency", frequency != null ? frequency : "");
        tag.setBoolean("isConnected", isConnected);
        tag.setBoolean("paused", paused);
        tag.setInteger("usedChannels", usedChannels);
        tag.setInteger("maxChannels", maxChannels);
        tag.setString("originalSenderPos", originalSenderPos != null ? originalSenderPos : "");
        if (ownerUUID != null) {
            tag.setLong("uuidMost", ownerUUID.getMostSignificantBits());
            tag.setLong("uuidLeast", ownerUUID.getLeastSignificantBits());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        mode = tag.getBoolean("mode");
        frequency = tag.getString("frequency");
        if (frequency == null) frequency = "";
        isConnected = tag.getBoolean("isConnected");
        paused = tag.getBoolean("paused");
        usedChannels = tag.getInteger("usedChannels");
        maxChannels = tag.hasKey("maxChannels") ? tag.getInteger("maxChannels") : 32;
        originalSenderPos = tag.hasKey("originalSenderPos") ? tag.getString("originalSenderPos") : "";
        if (tag.hasKey("uuidMost") && tag.hasKey("uuidLeast")) {
            long most = tag.getLong("uuidMost");
            long least = tag.getLong("uuidLeast");
            ownerUUID = new UUID(most, least);
        }
    }

    // ===== Network sync =====
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }
}
