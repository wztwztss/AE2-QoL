package com.wztwzt.ae2_qof.mixin.gt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * 注入 {@link MTEMultiBlockBase#shouldCheckMaintenance()} 使其始终返回 {@code false}。
 */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
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
