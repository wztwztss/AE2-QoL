package com.wztwzt.ae2_qof.wireless.distributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.WirelessDistributorSyncPacket;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.exceptions.FailedConnection;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.tiles.IColorableTile;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkChannelChanged;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.GridNode;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.util.Platform;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * ME 无线网络分配器（GT ME 仓室）。
 *
 * <p>
 * 只接受 GT 的 {@link BaseMetaTileEntity} 作为连接目标；把半径内**尚未接入任何 ME 网络**的
 * GT ME 仓室通过 AE2 的 {@link IGridConnection} 接到本方块所在的 ME 网络。目标是“未激活”的
 * 代理，避免把已经属于别人网络的仓室强行并网。
 *
 * <p>
 * 与上游 AE2Things 的差异：
 * <ul>
 * <li>颜色改为 Tile 字段（仍实现 {@link IColorableTile}），不再依赖 17 个物品子类型；
 * 这样不会因为服务端/客户端 metadata 不一致而串色。</li>
 * <li>扫描不再放在 {@code IGridTickable} 里自动全连；Shift+右键和 GUI 扫描/绑定都由玩家显式触发，
 * 每次最多连接 {@link #MAX_SIZE} 条。</li>
 * <li>连接目标增加了 owner 校验：GT 机器有 owner 时，只允许分配器 owner 连接。</li>
 * </ul>
 */
public class TileWirelessDistributor extends AENetworkTile implements IColorableTile, IPowerChannelState {

    public static final int MAX_SIZE = 40;
    public static final int MAX_SYNC_ENTRIES = 256;

    private static final int CHUNK_CHECK_INTERVAL = 20;

    private AEColor color = AEColor.Transparent;
    private int radiusOrdinal = Radius.TIER_2.ordinal();
    private UUID owner;
    private boolean powered;
    private int usedChannels;
    private boolean scanning;
    private int tickCounter;

    private final List<GridConnectionWrapper> connectionWrappers = new ArrayList<GridConnectionWrapper>();
    /** 从世界 NBT 读到、但目标区块尚未加载的连接坐标；区块加载后由 tick 重试。 */
    private final List<DimensionalCoord> pendingCoords = new ArrayList<DimensionalCoord>();

    public enum Radius {

        TIER_1(1),
        TIER_2(3);

        private final int chunks;

        Radius(int chunks) {
            this.chunks = chunks;
        }

        public int getChunks() {
            return chunks;
        }

        public Radius next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String getLabel() {
            return chunks + "x" + chunks;
        }
    }

    public static class GridConnectionWrapper {

        public final DimensionalCoord coord;
        public final IGridConnection connection;

        public GridConnectionWrapper(DimensionalCoord coord, IGridConnection connection) {
            this.coord = coord;
            this.connection = connection;
        }
    }

    public TileWirelessDistributor() {
        super();
        this.getProxy()
            .setFlags(GridFlags.DENSE_CAPACITY);
        this.getProxy()
            .setColor(AEColor.Transparent);
    }

    // ===== 基础状态 =====

    public AEColor getColor() {
        return color;
    }

    public void setColor(AEColor newColor) {
        if (newColor == null) newColor = AEColor.Transparent;
        if (this.color == newColor) return;
        this.color = newColor;
        this.getProxy()
            .setColor(newColor);
        try {
            IGridNode node = this.getProxy()
                .getNode();
            if (node != null) {
                node.updateState();
            }
        } catch (Throwable ignored) {}
        this.pruneInvalidLinks();
        this.markDirty();
        this.markForUpdate();
    }

    public Radius getRadius() {
        return Radius.values()[radiusOrdinal >= 0 && radiusOrdinal < Radius.values().length ? radiusOrdinal
            : Radius.TIER_2.ordinal()];
    }

    public void toggleRadius() {
        this.radiusOrdinal = this.getRadius()
            .next()
            .ordinal();
        this.markDirty();
        this.markForUpdate();
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        this.markDirty();
    }

    public boolean isOwner(EntityPlayer player) {
        if (player == null) return false;
        if (owner == null) {
            owner = player.getUniqueID();
            markDirty();
            return true;
        }
        return owner.equals(player.getUniqueID());
    }

    public boolean isPowered() {
        return powered;
    }

    @Override
    public boolean isActive() {
        return powered;
    }

    public int getUsedChannels() {
        return usedChannels;
    }

    public boolean isScanning() {
        return scanning;
    }

    public int getConnectionCount() {
        return connectionWrappers.size();
    }

    public int getMaxChannels() {
        try {
            IGridNode node = this.getProxy()
                .getNode();
            if (node instanceof GridNode) {
                return ((GridNode) node).getMaxChannels();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    // ===== 生命周期 =====

    @Override
    public void onReady() {
        super.onReady();
        if (Platform.isServer()) {
            this.restorePendingLinks();
        }
    }

    @Override
    public void onChunkUnload() {
        this.destroyConnections();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        this.destroyConnections();
        super.invalidate();
    }

    public void onBlockBroken() {
        this.destroyConnections();
        this.pendingCoords.clear();
    }

    private void destroyConnections() {
        for (GridConnectionWrapper wrapper : connectionWrappers) {
            // 连接对象是运行时资源，区块卸载/方块失效时必须销毁；但坐标要保留到 pending，
            // 否则随后的 WORLD_NBT_WRITE 会写出空 connections，重载后永久丢失连接。
            if (!isPending(wrapper.coord)) {
                pendingCoords.add(wrapper.coord);
            }
            try {
                wrapper.connection.destroy();
            } catch (Throwable ignored) {}
        }
        connectionWrappers.clear();
    }

    private boolean isPending(DimensionalCoord coord) {
        for (DimensionalCoord pending : pendingCoords) {
            if (pending.equals(coord)) return true;
        }
        return false;
    }

    @MENetworkEventSubscribe
    public void onPowerStatusChange(MENetworkPowerStatusChange event) {
        updatePowerState();
    }

    @MENetworkEventSubscribe
    public void onBootingStatusChange(MENetworkBootingStatusChange event) {
        updatePowerState();
    }

    @MENetworkEventSubscribe
    public void onChannelsChanged(MENetworkChannelsChanged event) {
        pruneInvalidLinks();
        updatePowerState();
    }

    @MENetworkEventSubscribe
    public void onChannelChanged(MENetworkChannelChanged event) {
        pruneInvalidLinks();
    }

    private void updatePowerState() {
        boolean state = false;
        try {
            state = getProxy().isActive() && getProxy().getEnergy()
                .extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.0001D;
        } catch (GridAccessException ignored) {}
        if (state != powered) {
            powered = state;
            markForUpdate();
        }
    }

    @TileEvent(TileEventType.TICK)
    public void onDistributorTick() {
        if (worldObj == null || worldObj.isRemote) return;
        tickCounter++;
        if (tickCounter % CHUNK_CHECK_INTERVAL != 0) return;
        this.restorePendingLinks();
        this.pruneInvalidLinks();
        this.updateUsedChannels();
        if (tickCounter % (CHUNK_CHECK_INTERVAL * 5) == 0) {
            this.updatePowerState();
        }
    }

    // ===== 扫描与连接 =====

    /**
     * Shift+右键：扫描并自动连接半径内所有可用的 GT ME 仓室。
     *
     * @return 本次新增连接数；-1 表示分配器未接入可用的 ME 网络。
     */
    public int scanAndLink() {
        if (!this.getProxy()
            .isActive()) {
            return -1;
        }
        this.scanning = true;
        List<BaseMetaTileEntity> targets = this.getAllAvailableTileEntity(this::canLink);
        int linked = 0;
        for (BaseMetaTileEntity target : targets) {
            if (connectionWrappers.size() >= MAX_SIZE) break;
            if (this.linkTarget(target) == null) {
                linked++;
            }
        }
        this.scanning = false;
        this.updateUsedChannels();
        this.markDirty();
        this.markForUpdate();
        return linked;
    }

    public void alert(EntityPlayer player) {
        if (!this.isOwner(player)) {
            player.addChatMessage(new ChatComponentTranslation("gui.ae2_qof.wireless_distributor.not_owner"));
            return;
        }
        int count = this.scanAndLink();
        if (count < 0) {
            player.addChatMessage(new ChatComponentTranslation("gui.ae2_qof.wireless_distributor.no_grid"));
        } else {
            player.addChatMessage(
                new ChatComponentTranslation("gui.ae2_qof.wireless_distributor.scan_result", count, MAX_SIZE));
        }
    }

    /**
     * 连接指定坐标的目标。
     *
     * @return null 表示成功；否则返回失败原因 lang key。
     */
    public String doLink(EntityPlayer player, DimensionalCoord coord) {
        if (coord == null) return "gui.ae2_qof.wireless_distributor.fail.invalid_target";
        if (coord.getDimension() != worldObj.provider.dimensionId) {
            return "gui.ae2_qof.wireless_distributor.fail.dimension";
        }
        if (!this.isWithinRadius(coord.x, coord.z)) {
            return "gui.ae2_qof.wireless_distributor.fail.out_of_range";
        }
        TileEntity tile = worldObj.getTileEntity(coord.x, coord.y, coord.z);
        if (!(tile instanceof BaseMetaTileEntity)) {
            return "gui.ae2_qof.wireless_distributor.fail.invalid_target";
        }
        BaseMetaTileEntity target = (BaseMetaTileEntity) tile;
        if (this.isLinked(coord)) return null;
        if (connectionWrappers.size() >= MAX_SIZE) {
            return "gui.ae2_qof.wireless_distributor.fail.max";
        }
        if (!this.canLink(target)) {
            return "gui.ae2_qof.wireless_distributor.fail.unavailable";
        }
        return this.linkTarget(target);
    }

    public boolean doUnlink(DimensionalCoord coord) {
        if (coord == null) return false;
        Iterator<GridConnectionWrapper> iterator = connectionWrappers.iterator();
        while (iterator.hasNext()) {
            GridConnectionWrapper wrapper = iterator.next();
            if (wrapper.coord.equals(coord)) {
                try {
                    wrapper.connection.destroy();
                } catch (Throwable ignored) {}
                iterator.remove();
                this.updateUsedChannels();
                this.markDirty();
                this.markForUpdate();
                return true;
            }
        }
        return false;
    }

    private String linkTarget(BaseMetaTileEntity target) {
        if (target == null || target.getProxy() == null
            || !target.getProxy()
                .isReady()
            || target.getProxy()
                .isActive()) {
            return "gui.ae2_qof.wireless_distributor.fail.unavailable";
        }
        if (!isSameColor(
            target.getProxy()
                .getColor(),
            this.getColor())) {
            return "gui.ae2_qof.wireless_distributor.fail.color";
        }
        UUID targetOwner = target.getOwnerUuid();
        if (targetOwner != null && owner != null && !targetOwner.equals(owner)) {
            return "gui.ae2_qof.wireless_distributor.fail.owner";
        }
        IGridNode selfNode = this.getProxy()
            .getNode();
        IGridNode targetNode = target.getProxy()
            .getNode();
        if (selfNode == null || targetNode == null) {
            return "gui.ae2_qof.wireless_distributor.fail.unavailable";
        }
        try {
            IGridConnection connection = AEApi.instance()
                .createGridConnection(selfNode, targetNode);
            connectionWrappers.add(
                new GridConnectionWrapper(
                    new DimensionalCoord(worldObj, target.xCoord, target.yCoord, target.zCoord),
                    connection));
            this.updateUsedChannels();
            this.markDirty();
            this.markForUpdate();
            return null;
        } catch (FailedConnection e) {
            return "gui.ae2_qof.wireless_distributor.fail.failed";
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 无线分配器连接失败：{}", target.getLocation(), t);
            return "gui.ae2_qof.wireless_distributor.fail.failed";
        }
    }

    private boolean canLink(BaseMetaTileEntity target) {
        if (target == null || target.getProxy() == null
            || !target.getProxy()
                .isReady()
            || target.getProxy()
                .isActive()) {
            return false;
        }
        if (!isSameColor(
            target.getProxy()
                .getColor(),
            this.getColor())) {
            return false;
        }
        UUID targetOwner = target.getOwnerUuid();
        return targetOwner == null || owner == null || targetOwner.equals(owner);
    }

    private boolean isLinked(DimensionalCoord coord) {
        for (GridConnectionWrapper wrapper : connectionWrappers) {
            if (wrapper.coord.equals(coord)) return true;
        }
        return false;
    }

    private static boolean isSameColor(AEColor a, AEColor b) {
        if (a == null || b == null) return false;
        return a == AEColor.Transparent || b == AEColor.Transparent || a == b;
    }

    private boolean isWithinRadius(int x, int z) {
        int centerChunkX = this.xCoord >> 4;
        int centerChunkZ = this.zCoord >> 4;
        int radius = (this.getRadius()
            .getChunks() - 1) / 2;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return Math.abs(chunkX - centerChunkX) <= radius && Math.abs(chunkZ - centerChunkZ) <= radius;
    }

    private List<Chunk> getChunks() {
        List<Chunk> chunks = new ArrayList<Chunk>();
        Chunk center = worldObj.getChunkFromBlockCoords(this.xCoord, this.zCoord);
        if (center != null) chunks.add(center);
        int centerChunkX = this.xCoord >> 4;
        int centerChunkZ = this.zCoord >> 4;
        int radius = (this.getRadius()
            .getChunks() - 1) / 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                if (!worldObj.getChunkProvider()
                    .chunkExists(cx, cz)) {
                    continue;
                }
                Chunk chunk = worldObj.getChunkFromChunkCoords(cx, cz);
                if (chunk != null) chunks.add(chunk);
            }
        }
        return chunks;
    }

    private List<BaseMetaTileEntity> getAllAvailableTileEntity(
        java.util.function.Predicate<BaseMetaTileEntity> filter) {
        List<BaseMetaTileEntity> result = new ArrayList<BaseMetaTileEntity>();
        for (Chunk chunk : this.getChunks()) {
            for (Object obj : chunk.chunkTileEntityMap.values()) {
                if (!(obj instanceof BaseMetaTileEntity)) continue;
                BaseMetaTileEntity tile = (BaseMetaTileEntity) obj;
                if (tile.getProxy() == null) continue;
                try {
                    if (filter.test(tile)) result.add(tile);
                } catch (Throwable ignored) {}
            }
        }
        Collections.sort(result, new Comparator<BaseMetaTileEntity>() {

            @Override
            public int compare(BaseMetaTileEntity a, BaseMetaTileEntity b) {
                String an = displayName(a);
                String bn = displayName(b);
                int cmp = an.compareToIgnoreCase(bn);
                if (cmp != 0) return cmp;
                if (a.xCoord != b.xCoord) return a.xCoord - b.xCoord;
                if (a.yCoord != b.yCoord) return a.yCoord - b.yCoord;
                return a.zCoord - b.zCoord;
            }
        });
        return result;
    }

    private static String displayName(BaseMetaTileEntity tile) {
        try {
            String custom = tile.hasCustomName() ? tile.getCustomName() : null;
            if (custom != null && !custom.isEmpty()) return custom;
            String local = tile.getLocalName();
            return local != null && !local.isEmpty() ? local : "GT Machine";
        } catch (Throwable ignored) {
            return "GT Machine";
        }
    }

    // ===== 同步给 GUI =====

    public List<WirelessDistributorSyncPacket.Entry> buildEntries() {
        this.updateUsedChannels();
        List<WirelessDistributorSyncPacket.Entry> entries = new ArrayList<WirelessDistributorSyncPacket.Entry>();
        for (BaseMetaTileEntity target : this.getAllAvailableTileEntity(tile -> true)) {
            try {
                DimensionalCoord coord = new DimensionalCoord(worldObj, target.xCoord, target.yCoord, target.zCoord);
                byte status;
                if (this.isLinked(coord)) {
                    status = WirelessDistributorSyncPacket.STATUS_LINKED;
                } else if (target.getProxy() != null && target.getProxy()
                    .isActive()) {
                        status = WirelessDistributorSyncPacket.STATUS_EXTERNAL;
                    } else if (this.canLink(target)) {
                        status = WirelessDistributorSyncPacket.STATUS_AVAILABLE;
                    } else {
                        status = WirelessDistributorSyncPacket.STATUS_UNAVAILABLE;
                    }
                AEColor targetColor = target.getProxy() != null ? target.getProxy()
                    .getColor() : AEColor.Transparent;
                entries.add(
                    new WirelessDistributorSyncPacket.Entry(
                        displayName(target),
                        coord.getDimension(),
                        target.xCoord,
                        target.yCoord,
                        target.zCoord,
                        targetColor != null ? targetColor.ordinal() : AEColor.Transparent.ordinal(),
                        status));
                if (entries.size() >= MAX_SYNC_ENTRIES) break;
            } catch (Throwable ignored) {}
        }
        return entries;
    }

    public void sendList(EntityPlayerMP player) {
        if (player == null) return;
        ModNetwork.CHANNEL.sendTo(
            new WirelessDistributorSyncPacket(
                this.xCoord,
                this.yCoord,
                this.zCoord,
                this.color.ordinal(),
                this.getRadius()
                    .ordinal(),
                this.getConnectionCount(),
                this.usedChannels,
                this.getMaxChannels(),
                this.powered,
                this.buildEntries()),
            player);
    }

    public void updateUsedChannels() {
        int used = 0;
        if (this.getProxy()
            .isActive()) {
            for (GridConnectionWrapper wrapper : connectionWrappers) {
                try {
                    used += wrapper.connection.getUsedChannels();
                } catch (Throwable ignored) {}
            }
        }
        this.usedChannels = used;
    }

    private void pruneInvalidLinks() {
        if (worldObj == null || worldObj.isRemote) return;
        boolean changed = false;
        Iterator<GridConnectionWrapper> iterator = connectionWrappers.iterator();
        while (iterator.hasNext()) {
            GridConnectionWrapper wrapper = iterator.next();
            DimensionalCoord coord = wrapper.coord;
            if (coord.getDimension() != worldObj.provider.dimensionId || !worldObj.getChunkProvider()
                .chunkExists(coord.x >> 4, coord.z >> 4)) {
                continue;
            }
            TileEntity tile = worldObj.getTileEntity(coord.x, coord.y, coord.z);
            if (!(tile instanceof BaseMetaTileEntity)) {
                try {
                    wrapper.connection.destroy();
                } catch (Throwable ignored) {}
                iterator.remove();
                changed = true;
                continue;
            }
            BaseMetaTileEntity target = (BaseMetaTileEntity) tile;
            if (target.getProxy() == null || !isSameColor(
                target.getProxy()
                    .getColor(),
                this.getColor())) {
                try {
                    wrapper.connection.destroy();
                } catch (Throwable ignored) {}
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            this.updateUsedChannels();
            this.markDirty();
            this.markForUpdate();
        }
    }

    private void restorePendingLinks() {
        if (worldObj == null || worldObj.isRemote || pendingCoords.isEmpty()) return;
        Iterator<DimensionalCoord> iterator = pendingCoords.iterator();
        while (iterator.hasNext()) {
            DimensionalCoord coord = iterator.next();
            if (coord.getDimension() != worldObj.provider.dimensionId) {
                iterator.remove();
                continue;
            }
            if (!worldObj.getChunkProvider()
                .chunkExists(coord.x >> 4, coord.z >> 4)) {
                continue;
            }
            TileEntity tile = worldObj.getTileEntity(coord.x, coord.y, coord.z);
            if (!(tile instanceof BaseMetaTileEntity)) {
                iterator.remove();
                continue;
            }
            BaseMetaTileEntity target = (BaseMetaTileEntity) tile;
            if (this.isLinked(coord)) {
                iterator.remove();
                continue;
            }
            if (this.canLink(target) && this.linkTarget(target) == null) {
                iterator.remove();
            }
        }
    }

    // ===== NBT =====

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readDistributorNBT(NBTTagCompound data) {
        this.color = AEColor.fromOrdinal(data.getInteger("color"));
        this.radiusOrdinal = data.getInteger("radius");
        this.getProxy()
            .setColor(this.color);
        String ownerString = data.getString("owner");
        if (!ownerString.isEmpty()) {
            try {
                this.owner = UUID.fromString(ownerString);
            } catch (IllegalArgumentException ignored) {}
        }
        this.pendingCoords.clear();
        NBTTagList list = data.getTagList("connections", 10);
        for (int i = 0; i < Math.min(list.tagCount(), MAX_SIZE); i++) {
            try {
                this.pendingCoords.add(DimensionalCoord.readFromNBT(list.getCompoundTagAt(i)));
            } catch (Throwable ignored) {}
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public NBTTagCompound writeDistributorNBT(NBTTagCompound data) {
        data.setInteger("color", this.color.ordinal());
        data.setInteger("radius", this.radiusOrdinal);
        data.setString("owner", this.owner != null ? this.owner.toString() : "");
        NBTTagList list = new NBTTagList();
        List<DimensionalCoord> allCoords = new ArrayList<DimensionalCoord>();
        for (GridConnectionWrapper wrapper : connectionWrappers) {
            if (!containsCoord(allCoords, wrapper.coord)) {
                allCoords.add(wrapper.coord);
            }
        }
        for (DimensionalCoord pending : pendingCoords) {
            if (!containsCoord(allCoords, pending)) {
                allCoords.add(pending);
            }
        }
        for (DimensionalCoord coord : allCoords) {
            NBTTagCompound tag = new NBTTagCompound();
            coord.writeToNBT(tag);
            list.appendTag(tag);
        }
        data.setTag("connections", list);
        return data;
    }

    private static boolean containsCoord(List<DimensionalCoord> coords, DimensionalCoord target) {
        for (DimensionalCoord coord : coords) {
            if (coord.equals(target)) return true;
        }
        return false;
    }

    // ===== 颜色 =====

    @Override
    public boolean recolourBlock(ForgeDirection side, AEColor newColor, EntityPlayer who) {
        if (this.getColor() == newColor) return false;
        this.setColor(newColor);
        this.markDirty();
        return true;
    }
}
