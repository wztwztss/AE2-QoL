package com.wztwzt.ae2_qof.terminal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCover;
import com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCoverData;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;

/**
 * 全局覆盖板注册表（WorldSavedData）。
 * 记录所有已安装的库存检测覆盖板的位置和配置摘要，供统计终端集中查看/修改。
 * 安装/配置变更 upsert，拆卸删除，区块未加载标记失联。
 */
public class CoverRegistry extends WorldSavedData {

    public static final String DATA_ID = "ae2_qof_cover_registry";

    private final Map<String, CoverEntry> entries = new HashMap<>();

    /** 性能修复版：每个维度只做一次 legacy 迁移合并（成功后记录；失败保留下次重试）。 */
    private transient java.util.Set<Integer> migratedLegacyDims = new java.util.HashSet<>();

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
        // 性能修复版：每个维度成功合并一次后记录维度号，之后 get() 不再反复扫 legacy 条目
        // （get() 在覆盖板每次读取周期都会被调用，旧实现每次全量重扫 legacy + 拼键字符串）。
        if (data.migratedLegacyDims == null) data.migratedLegacyDims = new java.util.HashSet<>();
        if (world != null && world.provider != null && !data.migratedLegacyDims.contains(world.provider.dimensionId)) {
            if (data.migrateFromLegacy(world, data)) {
                data.migratedLegacyDims.add(world.provider.dimensionId);
            }
        }
        if (created) {
            overworld.setItemData(DATA_ID, data);
        }
        return data;
    }

    /** 从旧的 per-dimension 存储合并数据（老存档兼容，幂等）。@return 是否成功（失败下次重试）。 */
    private boolean migrateFromLegacy(net.minecraft.world.World world, CoverRegistry target) {
        boolean changed = false;
        try {
            CoverRegistry legacy = (CoverRegistry) world.perWorldStorage.loadData(CoverRegistry.class, DATA_ID);
            if (legacy == null || legacy == target) return true;
            for (CoverEntry e : legacy.getAll()) {
                String k = key(e.dim, e.x, e.y, e.z, e.side);
                if (!target.entries.containsKey(k)) {
                    target.entries.put(k, e);
                    changed = true;
                }
            }
            if (changed) target.markDirty();
            return true;
        } catch (Throwable t) {
            // 3.35.0-fix22（审查 P2-17）：旧实现整块静默吞——迁移合并失败时 legacy 覆盖板数据
            // 没进新存储且零痕迹（老存档升级场景丢数据不可见）。补 WARN 留痕 + 保留原始异常。
            com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 覆盖板 legacy 迁移合并失败（数据未并入，下个周期重试）", t);
            return false;
        }
    }

    /**
     * P1-008：按世界实际状态刷新在线标记。
     * 区块未加载 → 标记离线但保留条目；区块已加载且覆盖板已被拆除/机器已毁 → 直接移除条目。
     * 只统计一个维度，统计终端列出前对所有维度调用一次。
     *
     * <p>
     * 3.31.0 追加：区块已加载时给"缺目标本体"的老条目补写一次（见 {@link CoverEntry#identityBackfillTried}）。
     */
    public void refreshOnlineStatus() {
        boolean changed = false;
        java.util.Iterator<CoverEntry> it = entries.values()
            .iterator();
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
            StockMonitorCover cover = findCover(w, e);
            if (cover == null) {
                it.remove();
                changed = true;
                continue;
            }
            if (!e.online) {
                e.online = true;
                changed = true;
            }
            // 3.31.0：老存档条目里只有服务端算出来的名字（专用服上是英文），没有本体 ⇒ 终端画不出图标。
            // 覆盖板本体此刻就在手上，补写一次即可。只在本次会话试一次，避免无本体可写的条目每次刷新都 markDirty。
            if (!e.identityBackfillTried) {
                e.identityBackfillTried = true;
                try {
                    StockMonitorCoverData data = cover.getCoverData();
                    if (data != null && data.hasAnyMonitorTarget() && !e.hasTargetIdentity()) {
                        e.updateFrom(data);
                        changed = true;
                    }
                } catch (Throwable t) {
                    // 静默兜底会掩盖故障：这里至少记一次，否则"图标一直不出现"无法定性
                    com.wztwzt.ae2_qof.MyMod.LOG.warn("[StockMonitor] 覆盖板目标本体补写失败 " + e.x + "," + e.y + "," + e.z, t);
                }
            }
        }
        if (changed) markDirty();
    }

    /** 取该条位置上真实存在的库存检测覆盖板；不存在/异常一律返回 null（调用方按"已拆除"处理）。 */
    private static StockMonitorCover findCover(net.minecraft.world.World w, CoverEntry e) {
        try {
            net.minecraft.tileentity.TileEntity te = w.getTileEntity(e.x, e.y, e.z);
            if (!(te instanceof gregtech.api.interfaces.tileentity.ICoverable)) return null;
            net.minecraftforge.common.util.ForgeDirection side = net.minecraftforge.common.util.ForgeDirection
                .getOrientation(e.side);
            Object cover = ((gregtech.api.interfaces.tileentity.ICoverable) te).getCoverAtSide(side);
            return cover instanceof StockMonitorCover ? (StockMonitorCover) cover : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 生成唯一键 */
    private static String key(int dim, int x, int y, int z, int side) {
        return dim + ":" + x + ":" + y + ":" + z + ":" + side;
    }

    /**
     * 注册或更新覆盖板。
     * 性能修复版：只在内容真正变化时 markDirty（调用方每个读取周期都会来一次，
     * 旧实现每次无条件 markDirty + 全量重建摘要 = 无意义的存档脏标记与对象churn）。
     */
    public void upsert(int dim, int x, int y, int z, int side, StockMonitorCoverData data) {
        String k = key(dim, x, y, z, side);
        CoverEntry entry = entries.get(k);
        if (entry == null) {
            entry = new CoverEntry(dim, x, y, z, side);
            entries.put(k, entry);
        }
        boolean changed = entry.updateFrom(data);
        if (!entry.online) {
            entry.online = true;
            changed = true;
        }
        if (changed) markDirty();
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
        /** 服务端算出来的目标名。3.31.0 起**只作老存档兜底**：新条目一律由客户端按本体自己解析名称。 */
        public String targetName = "";
        public boolean targetIsFluid = false;
        public long threshold = 0;
        public int modeOrdinal = 0;
        public int activeSlotCount = 0;
        public boolean online = true;

        /** 摘要取自哪个检测槽位（0..8）。3.31.0：终端编辑面板按这个槽位写入，避免"看的是 A、改的是 B"。 */
        public int slotIndex = 0;
        /** 目标本体：物品目标存物品栈（客户端拿它解析中文名 + 画图标）。流体目标为 null。 */
        public ItemStack targetItem = null;
        /** 目标本体：流体目标的流体注册名（客户端 FluidRegistry.getFluid(name) → 真流体贴图）。物品目标为空串。 */
        public String targetFluidName = "";
        /** 只在内存里用：本次会话是否已尝试补写本体（不持久化，避免每次刷新都 markDirty）。 */
        public transient boolean identityBackfillTried = false;

        public CoverEntry(int dim, int x, int y, int z, int side) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
        }

        /** 是否已经存下目标本体（老存档条目在补写之前为 false）。 */
        public boolean hasTargetIdentity() {
            return targetItem != null || (targetFluidName != null && !targetFluidName.isEmpty());
        }

        /**
         * 用覆盖板数据刷新摘要。@return 内容是否变化（不变则调用方不必 markDirty）。
         * 性能修复版：摘要的贵重部分（displayName 解析、ItemStack 拷贝）只在签名变化时重建。
         */
        public boolean updateFrom(StockMonitorCoverData data) {
            boolean changed = false;

            String newNetworkId = data.getNetworkId();
            int newModeOrdinal = data.getMode()
                .ordinal();
            int newActiveSlotCount = data.getActiveSlotCount();
            if (!java.util.Objects.equals(this.networkId, newNetworkId)) {
                this.networkId = newNetworkId;
                changed = true;
            }
            if (this.modeOrdinal != newModeOrdinal) {
                this.modeOrdinal = newModeOrdinal;
                changed = true;
            }
            if (this.activeSlotCount != newActiveSlotCount) {
                this.activeSlotCount = newActiveSlotCount;
                changed = true;
            }

            // 取第一个有效槽位作为摘要
            int newSlotIndex = 0;
            StockMonitorCoverData.MonitorSlot slot = null;
            for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
                StockMonitorCoverData.MonitorSlot s = data.getSlot(i);
                if (s.monitorTarget != null) {
                    slot = s;
                    newSlotIndex = i;
                    break;
                }
            }

            if (slot == null) {
                if (this.targetName.length() != 0 || this.targetIsFluid
                    || this.threshold != 0
                    || this.slotIndex != 0
                    || this.targetItem != null
                    || (this.targetFluidName != null && !this.targetFluidName.isEmpty())) {
                    this.targetName = "";
                    this.targetIsFluid = false;
                    this.threshold = 0;
                    this.slotIndex = 0;
                    this.targetItem = null;
                    this.targetFluidName = "";
                    changed = true;
                }
                return changed;
            }

            boolean newIsFluid = slot.monitorTarget instanceof IAEFluidStack;
            // 廉价签名：摘要槽位号 / 阈值 / 是否流体 / 目标本体等价。全一致 ⇒ 跳过重建。
            if (this.slotIndex != newSlotIndex || this.threshold != slot.threshold
                || this.targetIsFluid != newIsFluid
                || !sameTargetIdentity(slot)) {
                this.targetName = slot.monitorTarget.getDisplayName();
                this.targetIsFluid = newIsFluid;
                this.threshold = slot.threshold;
                this.slotIndex = newSlotIndex;
                // 3.31.0：连"本体"一起存下来。只存名字的话，专用服上目标名会一直是英文
                // （服务端语言表只有 en_US），而且终端根本没有东西可画图标。
                this.targetItem = null;
                this.targetFluidName = "";
                if (newIsFluid) {
                    net.minecraftforge.fluids.FluidStack fs = ((IAEFluidStack) slot.monitorTarget).getFluidStack();
                    if (fs != null && fs.getFluid() != null) {
                        this.targetFluidName = fs.getFluid()
                            .getName();
                    }
                } else if (slot.monitorTarget instanceof IAEItemStack) {
                    ItemStack is = ((IAEItemStack) slot.monitorTarget).getItemStack();
                    if (is != null && is.getItem() != null) {
                        this.targetItem = is.copy();
                        this.targetItem.stackSize = 1;
                    }
                }
                // 兜底：本体取不到（异常流体/第三方堆）时用覆盖板 GUI 里放的那个幻影物品当图标
                if (!hasTargetIdentity() && slot.phantomStack != null && slot.phantomStack.getItem() != null) {
                    this.targetItem = slot.phantomStack.copy();
                    this.targetItem.stackSize = 1;
                }
                changed = true;
            }
            return changed;
        }

        /** 摘要槽位的目标本体是否与已缓存的一致（避免每个读取周期重建 displayName / ItemStack 拷贝）。 */
        private boolean sameTargetIdentity(StockMonitorCoverData.MonitorSlot slot) {
            appeng.api.storage.data.IAEStack<?> t = slot.monitorTarget;
            if (t == null) return false;
            if (t instanceof IAEFluidStack) {
                if (this.targetIsFluid) {
                    net.minecraftforge.fluids.FluidStack fs = ((IAEFluidStack) t).getFluidStack();
                    String name = fs != null && fs.getFluid() != null ? fs.getFluid()
                        .getName() : "";
                    return name.equals(this.targetFluidName == null ? "" : this.targetFluidName);
                }
                return false;
            }
            if (this.targetIsFluid) return false;
            if (this.targetItem == null || this.targetItem.getItem() == null) return false;
            if (!(t instanceof IAEItemStack)) return false;
            ItemStack is = ((IAEItemStack) t).getItemStack();
            if (is == null || is.getItem() == null) return false;
            is = is.copy();
            is.stackSize = 1;
            return ItemStack.areItemStacksEqual(this.targetItem, is);
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
            // 3.31.0 追加字段（老存档没有这几个键，读到默认值即"待补写"）
            tag.setInteger("slotIndex", slotIndex);
            tag.setString("targetFluidName", targetFluidName == null ? "" : targetFluidName);
            if (targetItem != null && targetItem.getItem() != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                targetItem.writeToNBT(itemTag);
                tag.setTag("targetItem", itemTag);
            }
            return tag;
        }

        public static CoverEntry readFromNBT(NBTTagCompound tag) {
            if (!tag.hasKey("dim")) return null;
            CoverEntry e = new CoverEntry(
                tag.getInteger("dim"),
                tag.getInteger("x"),
                tag.getInteger("y"),
                tag.getInteger("z"),
                tag.getInteger("side"));
            e.networkId = tag.getString("networkId");
            e.targetName = tag.getString("targetName");
            e.targetIsFluid = tag.getBoolean("targetIsFluid");
            e.threshold = tag.getLong("threshold");
            e.modeOrdinal = tag.getInteger("modeOrdinal");
            e.activeSlotCount = tag.getInteger("activeSlotCount");
            e.online = tag.getBoolean("online");
            // 3.31.0 字段：老存档缺键 → slotIndex=0 / 流体名空 / 物品栈 null ⇒ 表现为"尚无本体，等待补写"
            e.slotIndex = tag.getInteger("slotIndex");
            e.targetFluidName = tag.getString("targetFluidName");
            if (tag.hasKey("targetItem")) {
                e.targetItem = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("targetItem"));
            }
            return e;
        }
    }
}
