package com.wztwzt.ae2_qof.hatch.adaptive;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;

import java.util.HashMap;

public class GridEnergyWorldData extends WorldSavedData {

    public static final String DATA_NAME = "ae2qol_grid_energy";

    private final HashMap<String, GridEnergyStats> statsMap;

    public GridEnergyWorldData(String name) {
        super(name);
        statsMap = new HashMap<>();
    }

    public static GridEnergyWorldData get(net.minecraft.world.World world) {
        GridEnergyWorldData data = (GridEnergyWorldData) world.mapStorage.loadData(GridEnergyWorldData.class, DATA_NAME);
        if (data == null) {
            data = new GridEnergyWorldData(DATA_NAME);
            world.mapStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("stats", 10);
        statsMap.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            String key = entry.getString("key");
            NBTTagCompound statsTag = entry.getCompoundTag("stats");
            GridEnergyStats stats = new GridEnergyStats();
            stats.loadNBT(statsTag);
            statsMap.put(key, stats);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (HashMap.Entry<String, GridEnergyStats> entry : statsMap.entrySet()) {
            NBTTagCompound compound = new NBTTagCompound();
            compound.setString("key", entry.getKey());
            NBTTagCompound statsTag = new NBTTagCompound();
            entry.getValue().saveNBT(statsTag);
            compound.setTag("stats", statsTag);
            list.appendTag(compound);
        }
        nbt.setTag("stats", list);
    }

    public GridEnergyStats getOrCreateStats(String key) {
        return statsMap.computeIfAbsent(key, k -> new GridEnergyStats());
    }

    public GridEnergyStats getStats(String key) {
        return statsMap.get(key);
    }

    public void setStats(String key, GridEnergyStats stats) {
        statsMap.put(key, stats);
    }
}
