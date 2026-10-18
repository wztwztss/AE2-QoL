package com.wztwzt.ae2_qof.proline;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;

/**
 * 产线持久化（设计 §2.3，B2 拍板）：标准 WorldSavedData 落 {@code <world>/data/*.dat}。
 *
 * <h2>为什么必须固定主世界（4.1.0-fix5 修的真 bug）</h2>
 * {@code WorldSavedData} 由每个 {@code WorldServer.mapStorage} 各自持有；原先用
 * {@code anyLoadedServerWorld()}（取"第一个已加载维度"）会**随玩家所在维度变化**——
 * 在下界/末地/空间站打开产线页时拿到的是另一个 mapStorage，于是：
 * <ul>
 * <li>新建的产线只写进那个维度的 dat；回主世界就"看不见"了（**存档分裂**）；</li>
 * <li>采样器与 GUI 可能同时读两个不同实例，累计/运行态互相覆盖。</li>
 * </ul>
 * 现在统一走 {@link #world()} = {@code DimensionManager.getWorld(0)}（主世界恒存在），
 * 全部读写都落同一个 dat。
 */
public class ProLineData extends WorldSavedData {

    public static final String SAVE_KEY = "AE2QoLProductionLines";
    public static final int SCHEMA_VERSION = 2;

    public int schemaVersion = SCHEMA_VERSION;
    public final List<ProductionLine> lines = new ArrayList<>();

    @SuppressWarnings("unused")
    public ProLineData(String name) {
        super(SAVE_KEY);
    }

    public ProLineData() {
        super(SAVE_KEY);
    }

    /**
     * 唯一权威世界 = 主世界（维度 0）。{@code WorldSavedData} 每维一份，必须固定一处，
     * 否则玩家换维度就会看到"另一份产线列表"。
     */
    public static WorldServer world() {
        WorldServer w = net.minecraftforge.common.DimensionManager.getWorld(0);
        if (w != null) return w;
        // 兜底：极端情况下主世界还没加载（正常不会），退回任意已加载维度避免 NPE
        for (int i : net.minecraftforge.common.DimensionManager.getIDs()) {
            WorldServer x = net.minecraftforge.common.DimensionManager.getWorld(i);
            if (x != null) return x;
        }
        return null;
    }

    /** @deprecated 语义有坑（随维度漂移），仅为兼容旧调用保留；新代码一律用 {@link #world()}。 */
    @Deprecated
    public static WorldServer anyLoadedServerWorld() {
        return world();
    }

    /** 服务端取数（懒加载）：第一次访问才 loadData，之后走 mapStorage 缓存。 */
    public static ProLineData get() {
        WorldServer w = world();
        return w == null ? null : get(w);
    }

    public static ProLineData get(WorldServer world) {
        if (world == null) return null;
        ProLineData data = (ProLineData) world.mapStorage.loadData(ProLineData.class, SAVE_KEY);
        if (data == null) {
            data = new ProLineData();
            world.mapStorage.setData(SAVE_KEY, data);
        }
        return data;
    }

    public static ProLineData find(WorldServer world) {
        return world == null ? null : (ProLineData) world.mapStorage.loadData(ProLineData.class, SAVE_KEY);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        lines.clear();
        schemaVersion = tag.hasKey("Schema") ? tag.getInteger("Schema") : 0;
        if (schemaVersion > SCHEMA_VERSION) {
            schemaVersion = SCHEMA_VERSION;
            markDirty();
            return;
        }
        NBTTagList list = tag.getTagList("Lines", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound lt = list.getCompoundTagAt(i);
            if (lt.hasKey("Id")) {
                try {
                    lines.add(ProductionLine.readNbt(lt));
                } catch (Throwable t) {
                    com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 产线存档条目损坏已跳过（index=" + i + "）", t);
                }
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("Schema", SCHEMA_VERSION);
        NBTTagList list = new NBTTagList();
        for (ProductionLine line : lines) list.appendTag(line.writeNbt(new NBTTagCompound()));
        tag.setTag("Lines", list);
    }

    public ProductionLine findLine(String lineId) {
        if (lineId == null) return null;
        for (ProductionLine l : lines) if (l.id.equals(lineId)) return l;
        return null;
    }
}
