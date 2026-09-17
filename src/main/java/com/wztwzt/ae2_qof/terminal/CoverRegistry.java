package com.wztwzt.ae2_qof.terminal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCover;
import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCoverData;
import com.wztwzt.ae2_qof.cover.stockmonitor.ThresholdMode;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/**
 * 全局覆盖板注册表（WorldSavedData）。
 * 记录所有已安装的库存检测覆盖板的位置和配置摘要，供统计终端集中查看/修改。
 * 安装/配置变更 upsert，拆卸删除，区块未加载标记失联。
 */
public class CoverRegistry extends WorldSavedData {

    public static final String DATA_ID = "ae2_qof_cover_registry";

    private final Map<String, CoverEntry> entries = new HashMap<>();

    public CoverRegistry(String name) {
        super(name);
    }

    public CoverRegistry() {
        this(DATA_ID);
    }

    /**
     * 获取（或创建）全局注册表实例。
     *
     * P1-007：历史实现使用 world.perWorldStorage，同一存档的不同维度会各存一份，
     * 导致跨维度终端看不到其他维度的覆盖板。现统一存到主世界（维度 0）的
     * loadItemData；并从旧 per-dimension NBT 做一次性迁移，避免老存档丢数据。
     */
    public static CoverRegistry get(net.minecraft.world.World world) {
        net.minecraft.world.World overworld = world;
        if (world != null && world.provider != null && world.provider.dimensionId != 0) {
            net.minecraft.world.World dim0 = DimensionManager.getWorld(0);
            if (dim0 != null) overworld = dim0;
        }
        if (overworld == null) {
            return new CoverRegistry();
        }
        CoverRegistry data = (CoverRegistry) overworld.loadItemData(CoverRegistry.class, DATA_ID);
        boolean created = false;
        if (data == null) {
            data = new CoverRegistry();
            created = true;
        }
        // 旧数据迁移：把各已加载维度的 perWorldStorage 旧注册表合并进来（只做增量，不覆盖已有条目）。
        if (world != null) {
            migrateFromLegacy(world, data);
        }
        if (created) {
            overworld.setItemData(DATA_ID, data);
        }
        return data;
    }

    /** 从旧的 per-dimension 存储合并数据（老存档兼容，幂等）。 */
    private static void migrateFromLegacy(net.minecraft.world.World world, CoverRegistry target) {
        try {
            CoverRegistry legacy = (CoverRegistry) world.perWorldStorage.loadData(CoverRegistry.class, DATA_ID);
            if (legacy == null || legacy == target) return;
            boolean changed = false;
            for (CoverEntry e : legacy.getAll()) {
                String k = key(e.dim, e.x, e.y, e.z, e.side);
                if (!target.entries.containsKey(k)) {
                    target.entries.put(k, e);
                    changed = true;
                }
            }
            if (changed) target.markDirty();
        } catch (Throwable ignored) {}
    }

    /**
     * P1-008：按世界实际状态刷新在线标记。
     * 区块未加载 → 标记离线但保留条目；区块已加载且覆盖板已被拆除/机器已毁 → 直接移除条目。
     * 只统计一个维度，统计终端列出前对所有维度调用一次。
     */
    public void refreshOnlineStatus() {
        boolean changed = false;
        java.util.Iterator<CoverEntry> it = entries.values().iterator();
        while (it.hasNext()) {
            CoverEntry e = it.next();
            net.minecraft.world.World w = DimensionManager.getWorld(e.dim);
            if (w == null || !w.blockExists(e.x, e.y, e.z)) {
                // 区块未加载：只标离线，不删除数据
                if (e.online) {
                    e.online = false;
                    changed = true;
                }
                continue;
            }
            boolean present = isCoverPresent(w, e);
            if (!present) {
                it.remove();
                changed = true;
            } else if (!e.online) {
                e.online = true;
                changed = true;
            }
        }
        if (changed) markDirty();
    }

    private static boolean isCoverPresent(net.minecraft.world.World w, CoverEntry e) {
        try {
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(e.x, e.y, e.z);
            if (!(te instanceof gregtech.api.interfaces.tileentity.ICoverable)) return false;
            net.minecraftforge.common.util.ForgeDirection side =
                net.minecraftforge.common.util.ForgeDirection.getOrientation(e.side);
            return ((gregtech.api.interfaces.tileentity.ICoverable) te).getCoverAtSide(side) instanceof StockMonitorCover;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 生成唯一键 */
    private static String key(int dim, int x, int y, int z, int side) {
        return dim + ":" + x + ":" + y + ":" + z + ":" + side;
    }

    /** 注册或更新覆盖板 */
    public void upsert(int dim, int x, int y, int z, int side, StockMonitorCoverData data) {
        String k = key(dim, x, y, z, side);
        CoverEntry entry = entries.get(k);
        if (entry == null) {
            entry = new CoverEntry(dim, x, y, z, side);
            entries.put(k, entry);
        }
        entry.updateFrom(data);
        entry.online = true;
        markDirty();
    }

    /** 移除覆盖板 */
    public void remove(int dim, int x, int y, int z, int side) {
        if (entries.remove(key(dim, x, y, z, side)) != null) {
            markDirty();
        }
    }

    /** 标记失联（区块未加载） */
    public void markOffline(int dim, int x, int y, int z, int side) {
        CoverEntry entry = entries.get(key(dim, x, y, z, side));
        if (entry != null && entry.online) {
            entry.online = false;
            markDirty();
        }
    }

    /** 获取所有条目 */
    public Collection<CoverEntry> getAll() {
        return entries.values();
    }

    /** 按 networkId 过滤（统计终端只显示同网络的覆盖板） */
    public Collection<CoverEntry> getByNetwork(String networkId) {
        java.util.List<CoverEntry> result = new java.util.ArrayList<>();
        for (CoverEntry e : entries.values()) {
            if (networkId == null || networkId.isEmpty() || networkId.equals(e.networkId)) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        entries.clear();
        NBTTagList list = nbt.getTagList("entries", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            CoverEntry entry = CoverEntry.readFromNBT(tag);
            if (entry != null) {
                entries.put(key(entry.dim, entry.x, entry.y, entry.z, entry.side), entry);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (CoverEntry entry : entries.values()) {
            list.appendTag(entry.writeToNBT());
        }
        nbt.setTag("entries", list);
    }

    /** 单个覆盖板的位置和配置摘要 */
    public static class CoverEntry {
        public final int dim;
        public final int x;
        public final int y;
        public final int z;
        public final int side;

        public String networkId = "";
        public String targetName = "";
        public boolean targetIsFluid = false;
        public long threshold = 0;
        public int modeOrdinal = 0;
        public int activeSlotCount = 0;
        public boolean online = true;

        public CoverEntry(int dim, int x, int y, int z, int side) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
        }

        public void updateFrom(StockMonitorCoverData data) {
            this.networkId = data.getNetworkId();
            this.modeOrdinal = data.getMode().ordinal();
            this.activeSlotCount = data.getActiveSlotCount();
            // 取第一个有效槽位作为摘要
            boolean found = false;
            for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
                StockMonitorCoverData.MonitorSlot slot = data.getSlot(i);
                if (slot.monitorTarget != null) {
                    this.targetName = slot.monitorTarget.getDisplayName();
                    this.targetIsFluid = slot.monitorTarget instanceof IAEFluidStack;
                    this.threshold = slot.threshold;
                    found = true;
                    break;
                }
            }
            if (!found) {
                this.targetName = "";
                this.targetIsFluid = false;
                this.threshold = 0;
            }
        }

        public NBTTagCompound writeToNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("dim", dim);
            tag.setInteger("x", x);
            tag.setInteger("y", y);
            tag.setInteger("z", z);
            tag.setInteger("side", side);
            tag.setString("networkId", networkId);
            tag.setString("targetName", targetName);
            tag.setBoolean("targetIsFluid", targetIsFluid);
            tag.setLong("threshold", threshold);
            tag.setInteger("modeOrdinal", modeOrdinal);
            tag.setInteger("activeSlotCount", activeSlotCount);
            tag.setBoolean("online", online);
            return tag;
        }

        public static CoverEntry readFromNBT(NBTTagCompound tag) {
            if (!tag.hasKey("dim")) return null;
            CoverEntry e = new CoverEntry(
                tag.getInteger("dim"), tag.getInteger("x"), tag.getInteger("y"),
                tag.getInteger("z"), tag.getInteger("side"));
            e.networkId = tag.getString("networkId");
            e.targetName = tag.getString("targetName");
            e.targetIsFluid = tag.getBoolean("targetIsFluid");
            e.threshold = tag.getLong("threshold");
            e.modeOrdinal = tag.getInteger("modeOrdinal");
            e.activeSlotCount = tag.getInteger("activeSlotCount");
            e.online = tag.getBoolean("online");
            return e;
        }
    }
}
