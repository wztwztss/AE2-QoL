package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * 全局多方块主机注册表。
 * <p>
 * 借鉴 GTNL EnergyMonitorRegistry：所有 {@link MTEMultiBlockBase} 在 handleFirstTick 时
 * 通过 Mixin 自动注册到此处。仓室识别机器时遍历注册表，按 center/radius 范围匹配，
 * 避免空间搜索在紧邻多方块场景下选错主机。
 */
public class MultiblockRegistry {

    private static final Set<MTEMultiBlockBase> REGISTRY = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private MultiblockRegistry() {}

    public static void register(MTEMultiBlockBase mte) {
        if (mte == null) {
            return;
        }
        IGregTechTileEntity base = mte.getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null || base.getWorld().isRemote) {
            return;
        }
        REGISTRY.add(mte);
    }

    public static void unregister(MTEMultiBlockBase mte) {
        if (mte == null) {
            return;
        }
        REGISTRY.remove(mte);
    }

    public static List<MTEMultiBlockBase> snapshot() {
        return new ArrayList<>(REGISTRY);
    }

    /**
     * 移除已失效的条目（base 为 null、世界已卸载、tile 已死亡）。
     */
    public static void cleanupInvalid() {
        REGISTRY.removeIf(mte -> {
            if (mte == null) {
                return true;
            }
            IGregTechTileEntity base = mte.getBaseMetaTileEntity();
            return base == null || base.getWorld() == null || base.isDead();
        });
    }

    /**
     * 3.35.0-fix7（审查 P1-16）：服务器停止时清空登记表。
     * 旧实现注册后从不移除（unregister 全仓零调用、这里也没有 clear）——静态强引用
     * （主机 ↔ World 互相持有）让旧世界的多方块跨存档滞留内存。由
     * {@code CommonProxy.serverStopping} 调用，与 onUnload 注销配合形成完整生命周期。
     */
    public static void clear() {
        REGISTRY.clear();
    }
}
