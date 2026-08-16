package com.wztwzt.ae2_qof.api;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/**
 * 智能倍增（Smart Doubling）能力接口。
 * <p>
 * 由 {@code ICraftingMedium}（ME 接口）实现：合成 CPU 向该介质推送样板时，可一次性送入
 * 多轮（N 倍）原料，让接口在多个 tick 内连续向相邻机器供料，从而减少 CPU 与网格之间的
 * 往返开销与功耗。介质自身不感知倍增，仅按每轮一份的样板语义照常供料。
 */
public interface ISmartDoublingMedium {

    /**
     * @return 是否启用了智能倍增（界面开关）。
     */
    boolean isSmartDoublingEnabled();

    /**
     * 设置智能倍增开关并立即持久化（NBT）。
     *
     * @param enabled 是否启用
     */
    void setSmartDoubling(boolean enabled);

    /**
     * 返回该介质当前对给定样板可安全承受的最大倍增轮数。
     * <p>
     * 返回 1 表示不启用倍增（逐轮推送）。实现方应基于相邻机器的当前剩余容量给出
     * 保守估计：宁可偏小（少推几轮），不可偏大造成物品滞留。
     *
     * @param details 待推送的样板
     * @return 最大轮数，至少为 1
     */
    int getMaxMultiplier(ICraftingPatternDetails details);
}