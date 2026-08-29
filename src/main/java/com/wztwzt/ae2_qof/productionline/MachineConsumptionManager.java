package com.wztwzt.ae2_qof.productionline;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;

/**
 * 机器消耗管理器
 * 跟踪哪些配方的机器已被消耗
 */
public class MachineConsumptionManager extends WorldSavedData {

    private static final String DATA_NAME = "ae2_qof_MachineConsumption";
    
    /** 已消耗的配方ID列表 */
    private final Map<String, Boolean> consumedRecipes = new HashMap<>();

    public MachineConsumptionManager(String name) {
        super(name);
    }

    /**
     * 检查配方的机器是否已消耗
     */
    public boolean isMachineConsumed(String recipeId) {
        return consumedRecipes.getOrDefault(recipeId, false);
    }

    /**
     * 标记配方的机器已消耗
     */
    public void markMachineConsumed(String recipeId) {
        consumedRecipes.put(recipeId, true);
        markDirty();
    }

    /**
     * 清除配方的机器消耗记录
     */
    public void clearMachineConsumption(String recipeId) {
        consumedRecipes.remove(recipeId);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        consumedRecipes.clear();
        
        NBTTagList list = nbt.getTagList("ConsumedRecipes", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            String recipeId = tag.getString("RecipeId");
            boolean consumed = tag.getBoolean("Consumed");
            consumedRecipes.put(recipeId, consumed);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        
        for (Map.Entry<String, Boolean> entry : consumedRecipes.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("RecipeId", entry.getKey());
            tag.setBoolean("Consumed", entry.getValue());
            list.appendTag(tag);
        }
        
        nbt.setTag("ConsumedRecipes", list);
    }

    /**
     * 获取或创建实例
     */
    public static MachineConsumptionManager get(net.minecraft.world.World world) {
        MachineConsumptionManager manager = (MachineConsumptionManager) world.mapStorage.loadData(
            MachineConsumptionManager.class, DATA_NAME);
        
        if (manager == null) {
            manager = new MachineConsumptionManager(DATA_NAME);
            world.mapStorage.setData(DATA_NAME, manager);
        }
        
        return manager;
    }
}
