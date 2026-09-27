/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.recipe;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * 统一配方数据结构，可容纳 GT 配方和其他类型配方
 */
public class RecipeEntry {

    /** 配方来源类型: "gt", "vanilla", "nei" */
    public final String sourceType;

    /** 配方表 ID / 机器名称 (如 "gt.recipe.assembler") */
    public final String recipeMapId;

    /** 机器显示名称 */
    public final String machineDisplayName;

    /** 输入物品 */
    public final ItemStack[] inputs;

    /** 输出物品 */
    public final ItemStack[] outputs;

    /** 流体输入 */
    public final FluidStack[] fluidInputs;

    /** 流体输出 */
    public final FluidStack[] fluidOutputs;

    /** 不消耗的物品 (NC 物品) */
    public final ItemStack[] specialItems;

    /** 处理时间 (ticks) */
    public final int duration;

    /** EU/t */
    public final int euPerTick;

    public RecipeEntry(String sourceType, String recipeMapId, String machineDisplayName, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, ItemStack[] specialItems,
        int duration, int euPerTick) {
        this.sourceType = sourceType;
        this.recipeMapId = recipeMapId;
        this.machineDisplayName = machineDisplayName;
        this.inputs = inputs != null ? inputs : new ItemStack[0];
        this.outputs = outputs != null ? outputs : new ItemStack[0];
        this.fluidInputs = fluidInputs != null ? fluidInputs : new FluidStack[0];
        this.fluidOutputs = fluidOutputs != null ? fluidOutputs : new FluidStack[0];
        this.specialItems = specialItems != null ? specialItems : new ItemStack[0];
        this.duration = duration;
        this.euPerTick = euPerTick;
    }
}
