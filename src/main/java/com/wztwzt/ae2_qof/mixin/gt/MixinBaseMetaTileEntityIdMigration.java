package com.wztwzt.ae2_qof.mixin.gt;

import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.MyMod;

import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * 旧存档 MetaTileEntity ID 自动迁移（fix48）。
 * <p>
 * 背景：本模组的「自适应电网终端」原用 MTE ID 32100、「库存统计终端」原用 32101。
 * 290b3 引入 fissionevolved 后，这两个号被它的「裂变反应堆控制器 / 终极宇宙毁灭发电机控制器」
 * 占用（其 Config 默认值即 32100/32101），导致我们这两个终端注册失败。
 * 现退让给 fissionevolved，改用全表核对过的空闲号段 32106 / 32107。
 * <p>
 * 问题：GT 存档只记录 MTE 的**数字 ID**（{@code mID}），不记录类名。直接换号会让旧存档里
 * 已经摆好的终端被当成 fission 的机器加载，其配置（网络频率、电压等级、四个子仓的配对表）
 * 将在下一次保存时被丢弃——对玩家而言就是「终端消失、频率丢失、全基地子仓解绑」。
 * <p>
 * 解法：在 GT 读取机器存档数据的入口 {@code setInitialValuesAsNBT} 的最前面判断——
 * 「编号是旧的 32100/32101」且「NBT 里带本模组专属键」的机器，判定为本模组的旧终端，
 * 把编号改写成新号后再交给原逻辑。这样方块、掉落物与物品形态都会统一使用新 ID，
 * 而终端的配置数据原封不动，对玩家完全无感。
 * <p>
 * 判定只认本模组专属键（{@code ae2qol*}），fissionevolved 写的是 {@code Fission*}，
 * 两者互不干扰，因此不会误迁移它的机器；其他模组占用这两个号的机器同样不受影响。
 * <p>
 * GT 为 GTNH 模组类，编译名即运行时名，故 {@code remap = false}。
 */
@Mixin(value = BaseMetaTileEntity.class, remap = false)
public abstract class MixinBaseMetaTileEntityIdMigration {

    /** 自适应电网终端的旧 ID（被 fissionevolved 占用，改用 32106）。 */
    @Unique
    private static final int AE2QOL_OLD_ADAPTIVE_TERMINAL_ID = 32100;
    /** 库存统计终端的旧 ID（被 fissionevolved 占用，改用 32107）。 */
    @Unique
    private static final int AE2QOL_OLD_STOCK_TERMINAL_ID = 32101;
    /** 自适应电网终端的新 ID（290b3 全表核对为空闲）。 */
    @Unique
    private static final int AE2QOL_NEW_ADAPTIVE_TERMINAL_ID = 32106;
    /** 库存统计终端的新 ID（290b3 全表核对为空闲）。 */
    @Unique
    private static final int AE2QOL_NEW_STOCK_TERMINAL_ID = 32107;

    /**
     * 存档读取路径：{@code readFromNBT} → {@code setInitialValuesAsNBT(aNBT, 0)}，
     * 其中 {@code mID} 取自 {@code aNBT.getInteger("mID")}。
     * 在方法最前面把旧编号改写掉，后续原逻辑就会按新编号创建机器。
     */
    @Inject(
        method = "setInitialValuesAsNBT(Lnet/minecraft/nbt/NBTTagCompound;S)V",
        at = @At("HEAD"),
        remap = false)
    private void ae2qol$migrateLegacyMachineId(NBTTagCompound nbt, short aID, CallbackInfo ci) {
        try {
            if (nbt == null) return;
            int storedId = nbt.getInteger("mID");
            int mapped = ae2qol$mapLegacyId(storedId);
            if (mapped == storedId) return;
            if (!ae2qol$isOwnMachine(nbt)) return;
            nbt.setInteger("mID", mapped);
            MyMod.LOG.info(
                "[AE2QoL] migrated legacy terminal in save data: MTE id {} -> {}",
                storedId,
                mapped);
        } catch (Throwable ignored) {
            // 迁移失败绝不能影响存档加载，静默放行由原逻辑处理
        }
    }

    /** 旧编号 → 新编号；不属于本模组旧编号时原样返回。 */
    @Unique
    private static int ae2qol$mapLegacyId(int id) {
        if (id == AE2QOL_OLD_ADAPTIVE_TERMINAL_ID) return AE2QOL_NEW_ADAPTIVE_TERMINAL_ID;
        if (id == AE2QOL_OLD_STOCK_TERMINAL_ID) return AE2QOL_NEW_STOCK_TERMINAL_ID;
        return id;
    }

    /**
     * 是否为「本模组写入过数据」的机器。
     * <p>
     * 本模组的自适应终端与配套子仓都会写出 {@code ae2qolNO}/{@code ae2qolNF}/{@code ae2qolVT}
     * 这组专属键；fissionevolved 写的是 {@code Fission*} 前缀，其他模组也不会用 {@code ae2qol} 前缀，
     * 因此该判定不会误伤别人的机器。
     */
    @Unique
    private static boolean ae2qol$isOwnMachine(NBTTagCompound nbt) {
        return nbt.hasKey("ae2qolNO") || nbt.hasKey("ae2qolNF") || nbt.hasKey("ae2qolVT");
    }
}
