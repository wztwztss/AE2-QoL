package com.wztwzt.ae2_qof.proline;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;

/**
 * 产线持久化（设计 §2.3，B2 拍板）：标准 WorldSavedData 落 {@code <world>/data/*.dat}。
 *
 * <p>
 * <b>schemaVersion（v6 新增）</b>：未来字段演进以此为迁移依据（本项目已有 MTE ID 迁移的前车之鉴，
 * 坑 #8）。读写各自兜底：坏条目跳过并计数，永不抛异常到存档链上。
 *
 * <p>
 * 全仓此前没有 WorldSavedData 先例（grep 为零），这里用的是 1.7.10 原版标准三件套：
 * 子类 + mapStorage.loadData + markDirty。
 */
public class ProLineData extends WorldSavedData {

    public static final String SAVE_KEY = "AE2QoLProductionLines";
    public static final int SCHEMA_VERSION = 1;

    public int schemaVersion = SCHEMA_VERSION;
    public final List<ProductionLine> lines = new ArrayList<>();

    @SuppressWarnings("unused")
    public ProLineData(String name) {
        super(SAVE_KEY);
    }

    public ProLineData() {
        super(SAVE_KEY);
    }

    /** 服务端取数（懒加载）：第一次访问才 loadData，之后走 mapStorage 缓存。 */
    public static ProLineData get(WorldServer world) {
        ProLineData data = (ProLineData) world.mapStorage.loadData(ProLineData.class, SAVE_KEY);
        if (data == null) {
            data = new ProLineData();
            world.mapStorage.setData(SAVE_KEY, data);
        }
        return data;
    }

    public static ProLineData find(WorldServer world) {
        return (ProLineData) world.mapStorage.loadData(ProLineData.class, SAVE_KEY);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        lines.clear();
        schemaVersion = tag.hasKey("Schema") ? tag.getInteger("Schema") : 0;
        if (schemaVersion > SCHEMA_VERSION) {
            // 未来版本写入：过大版本不解析（保平安），老数据照读
            schemaVersion = SCHEMA_VERSION;
            markDirty();
            return;
        }
        net.minecraft.nbt.NBTTagList list = tag.getTagList("Lines", 10);
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
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for (ProductionLine line : lines) list.appendTag(line.writeNbt(new NBTTagCompound()));
        tag.setTag("Lines", list);
    }

    public ProductionLine findLine(String lineId) {
        if (lineId == null) return null;
        for (ProductionLine l : lines) if (l.id.equals(lineId)) return l;
        return null;
    }

    /** 任意非空世界（懒加载兜底用）：1.7.10 Forge 的已加载维度枚举是 getIDs()。 */
    public static WorldServer anyLoadedServerWorld() {
        for (int i : net.minecraftforge.common.DimensionManager.getIDs()) {
            WorldServer w = net.minecraftforge.common.DimensionManager.getWorld(i);
            if (w != null) return w;
        }
        return null;
    }

    public boolean isDirty;
}
