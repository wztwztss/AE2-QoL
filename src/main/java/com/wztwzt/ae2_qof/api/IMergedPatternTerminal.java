package com.wztwzt.ae2_qof.api;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.tile.inventory.AppEngInternalInventory;

/**
 * 二合一终端（接口终端 + 样板编码面板）的容器契约。
 * <p>
 * 由 {@code MixinContainerInterfaceTerminal} 注入到 AE2 原生
 * {@code ContainerInterfaceTerminal}，有线接口终端与 ae2fc 无线接口终端共用同一容器/GUI，
 * 两端均通过该接口访问混合进来的库存与操作。
 */
public interface IMergedPatternTerminal {

    /** 处理模式输入/输出格上限（4×4 网格 × 2 页） */
    int INPUT_MAX = 32;
    int OUTPUT_MAX = 32;

    AppEngInternalInventory getMergedInputInv();

    AppEngInternalInventory getMergedOutputInv();

    AppEngInternalInventory getMergedResultInv();

    AppEngInternalInventory getMergedBlankInv();

    AppEngInternalInventory getMergedEncodedInv();

    Slot getMergedResultSlot();

    Slot getMergedBlankSlot();

    Slot getMergedEncodedSlot();

    /** 混合入的第一个槽号（玩家背包槽之后），用于槽位偏移计算 */
    int getMergedSlotBase();

    boolean isMergedCraftingMode();

    void setMergedCraftingMode(boolean crafting);

    boolean isMergedSubstitute();

    void setMergedSubstitute(boolean substitute);

    boolean isMergedBeSubstitute();

    void setMergedBeSubstitute(boolean beSubstitute);

    /** 是否反转输入/输出网格（AE 原生 4×4 面板布局） */
    boolean isMergedInverted();

    void setMergedInverted(boolean inverted);

    /** 处理模式当前激活页（0/1） */
    int getMergedActivePage();

    void setMergedActivePage(int page);

    /**
     * 服务端编码当前网格为一个已编码样板：消耗一张空白样板，写入 apu:recipeMap 并设置显示名为机器中文名。
     *
     * @return 检测到的机器中文名；无内容/无空白样板/已编码槽占用时返回 null
     */
    String mergedEncode();

    void mergedClear();

    void mergedDoubleStacks();

    /** 服务端用 NEI 配方填充输入/输出格 */
    void mergedFill(ItemStack[] inputs, ItemStack[] outputs, boolean crafting);

    /** 服务端每 tick 依据当前合成模式重算合成结果槽（仅合成模式有效） */
    void mergedRecomputeResult();

    /** 轮换输出格内容（最后一个非空输出移到最前），供面板 ⇄ 按钮使用 */
    void mergedSwapOutputs();
}
