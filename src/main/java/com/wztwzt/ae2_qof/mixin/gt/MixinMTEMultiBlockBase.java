package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * 注入 {@link MTEMultiBlockBase#shouldCheckMaintenance()} 使其始终返回 {@code false}。
 * <p>
 * GT5U 维护系统以该方法为统一守门：返回 {@code false} 时，构造函数与 loadNBTData 自动走
 * {@code fixAllIssues()} 路径（6 个标志位全部设为 true）；checkMaintenance / doRandomMaintenanceDamage
 * 均在首行短路返回。效率惩罚为 0（理想状态 - 修复状态 == 0）。
 * <p>
 * 此 Mixin 使所有继承 {@code MTEMultiBlockBase} 的多方块机器永远无维护问题，
 * 配合万能维护仓物品使用时，仓室仅作为占位符存在（不执行任何维护逻辑）。
 */
@Mixin(MTEMultiBlockBase.class)
public abstract class MixinMTEMultiBlockBase {

    /**
     * @author wztwzt
     * @return 始终返回 false，禁用所有维护检查
     * @reason 万能维护仓功能：全局禁用维护系统
     */
    @Overwrite(remap = false)
    public boolean shouldCheckMaintenance() {
        return false;
    }
}
