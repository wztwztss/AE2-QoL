package com.wztwzt.ae2_qof.wildcard;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.ByteBufUtils;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * 「按格设置」的服务端/客户端共用小助手（4.0.0）：按坐标找机器、取容器、距离校验、9 格催化剂出入网。
 *
 * <p>为什么单独抽出来：三件包（写入 / 请求 / 回读）都要做同一件事——
 * "坐标 → {@link IGregTechTileEntity} → MTE → {@link ISlotSettingsHolder}"，
 * 复制三遍必然走样；且每个失败分支都要记日志（本项目不允许静默失败）。
 */
public final class SlotSettingsAccess {

    private SlotSettingsAccess() {}

    /** 允许操作的最大距离平方（8 格，与既有样板槽手势写回包同口径）。 */
    public static final double MAX_DISTANCE_SQ = 64.0D;

    /** 按坐标取 GT 机器；坐标处不是 GT 机器时返回 null 并记日志。 */
    public static IMetaTileEntity machineAt(EntityPlayer player, int x, int y, int z, String who) {
        TileEntity te = player.worldObj.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity gregTechTileEntity)) {
            MyMod.LOG.warn("[AE2QoL] {}：坐标 [{}, {}, {}] 处不是 GT 机器", who, x, y, z);
            return null;
        }
        IMetaTileEntity mte = gregTechTileEntity.getMetaTileEntity();
        if (mte == null) {
            MyMod.LOG.warn("[AE2QoL] {}：坐标 [{}, {}, {}] 处机器元数据为空", who, x, y, z);
        }
        return mte;
    }

    /** 距离校验（防止构造包远程改别人机器）。 */
    public static boolean near(EntityPlayer player, int x, int y, int z, String who) {
        if (player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > MAX_DISTANCE_SQ) {
            MyMod.LOG.warn(
                "[AE2QoL] {}被拒：玩家 {} 距机器 [{}, {}, {}] 过远",
                who,
                player.getCommandSenderName(),
                x,
                y,
                z);
            return false;
        }
        return true;
    }

    /** 机器若不支持"按格设置"（未挂宿主 mixin）就返回 null 并记日志。 */
    public static SlotSettingsStore storeOf(IMetaTileEntity mte, String who) {
        if (mte instanceof ISlotSettingsHolder holder) {
            return holder.ae2qol$slotSettings();
        }
        if (mte != null) {
            MyMod.LOG.warn(
                "[AE2QoL] {}：机器 {} 未挂按格设置宿主（该机型暂不支持中键格设置）",
                who,
                mte.getClass()
                    .getSimpleName());
        }
        return null;
    }

    /** 写 9 格催化剂（无 NBT 依赖，走 FML 的 ByteBufUtils）。 */
    public static void writeStacks(ByteBuf buf, ItemStack[] stacks) {
        buf.writeInt(stacks == null ? 0 : stacks.length);
        for (int i = 0; i < (stacks == null ? 0 : stacks.length); i++) {
            ByteBufUtils.writeItemStack(buf, stacks[i]);
        }
    }

    /** 读 9 格催化剂；长度不匹配时补 null（不抛异常，只记日志）。 */
    public static ItemStack[] readStacks(ByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > 64) {
            MyMod.LOG.warn("[AE2QoL] 催化剂位数量异常（{}），按空处理", size);
            return new ItemStack[SlotSettings.CATALYST_SLOTS];
        }
        ItemStack[] stacks = new ItemStack[SlotSettings.CATALYST_SLOTS];
        for (int i = 0; i < size; i++) {
            ItemStack stack = ByteBufUtils.readItemStack(buf);
            if (i < stacks.length) stacks[i] = stack;
        }
        return stacks;
    }
}
