package com.wztwzt.ae2_qof.productionline;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * 产线配方数据结构
 * 存储从JSON解析的产线配方信息
 */
public class ProductionLineRecipe {

    /** 配方ID */
    private final String id;
    
    /** 配方名称 */
    private final String name;
    
    /** 配方分类 */
    private final String category;
    
    /** 配方描述 */
    private final String description;
    
    /** 最小电压等级要求 */
    private final int minVoltageTier;
    
    /** 所需机器列表 */
    private final List<RequiredMachine> requiredMachines;
    
    /** 最终物品输出 */
    private final List<ItemStack> itemOutputs;
    
    /** 最终流体输出 */
    private final List<FluidStack> fluidOutputs;
    
    /** EU/t消耗 */
    private final int euPerTick;
    
    /** 处理时间（tick） */
    private final int duration;
    
    /** 电压等级 */
    private final int voltageTier;
    
    /** 并行数 */
    private final int parallel;

    public ProductionLineRecipe(String id, String name, String category, String description,
                               int minVoltageTier, List<RequiredMachine> requiredMachines,
                               List<ItemStack> itemOutputs, List<FluidStack> fluidOutputs,
                               int euPerTick, int duration, int voltageTier, int parallel) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.minVoltageTier = minVoltageTier;
        this.requiredMachines = requiredMachines != null ? requiredMachines : new ArrayList<>();
        this.itemOutputs = itemOutputs != null ? itemOutputs : new ArrayList<>();
        this.fluidOutputs = fluidOutputs != null ? fluidOutputs : new ArrayList<>();
        this.euPerTick = euPerTick;
        this.duration = duration;
        this.voltageTier = voltageTier;
        this.parallel = parallel;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public int getMinVoltageTier() {
        return minVoltageTier;
    }

    public List<RequiredMachine> getRequiredMachines() {
        return requiredMachines;
    }

    public List<ItemStack> getItemOutputs() {
        return itemOutputs;
    }

    public List<FluidStack> getFluidOutputs() {
        return fluidOutputs;
    }

    public int getEuPerTick() {
        return euPerTick;
    }

    public int getDuration() {
        return duration;
    }

    public int getVoltageTier() {
        return voltageTier;
    }

    public int getParallel() {
        return parallel;
    }

    /**
     * 所需机器数据结构
     */
    public static class RequiredMachine {
        
        /** 机器ID（如 gtceu:distillation_tower） */
        private final String machineId;
        
        /** 机器名称 */
        private final String machineName;
        
        /** 所需数量 */
        private final int count;
        
        /** 是否被消耗 */
        private final boolean consumed;

        public RequiredMachine(String machineId, String machineName, int count, boolean consumed) {
            this.machineId = machineId;
            this.machineName = machineName;
            this.count = count;
            this.consumed = consumed;
        }

        public String getMachineId() {
            return machineId;
        }

        public String getMachineName() {
            return machineName;
        }

        public int getCount() {
            return count;
        }

        public boolean isConsumed() {
            return consumed;
        }
    }
}
