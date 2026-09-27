package com.wztwzt.ae2_qof.blockcopy;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.MyMod;

/**
 * 创造模式「Ctrl+中键 复制方块完整 NBT」的服务端核心（3.22.0-feat1，见 CHANGELOG）。
 *
 * <h2>定稿口径（用户拍板）</h2>
 * <ul>
 * <li>仅**创造模式**生效；触发方式为创造模式下 **Ctrl+中键** 点方块；</li>
 * <li>产物 = **原方块物品** + 我们的专用子键 {@link #NBT_ROOT}，内含方块注册名/meta、
 * **TileEntity 的完整 {@code writeToNBT} 数据**、字节数、来源维度与坐标、时间戳；</li>
 * <li>快照**上限 {@link #MAX_SNAPSHOT_BYTES}（512 KB）**，超限**拒绝**并在聊天栏告知 + 记日志（不静默）；</li>
 * <li>**敏感方块**（AE2 网络节点/存储元件、GT 多方块控制器）先经聊天栏**可点击确认**再复制；</li>
 * <li>**普通右键放置**带快照的物品 ⇒ 放下后立刻把快照写回新 TE，并**清掉物品上的快照**（防重复使用）。</li>
 * </ul>
 *
 * <h2>线程纪律</h2>
 * 本类的所有方法都**必须在服务端 tick 线程**执行（包处理器在网络线程，须先
 * {@code MinecraftServer.addScheduledTask} 归队，见项目坑位 #3）。
 */
public final class BlockCopyService {

    private BlockCopyService() {}

    /** 物品上承载快照的专用子键名。 */
    public static final String NBT_ROOT = "ae2qol_blockcopy";
    /** 子键内的字段名。 */
    public static final String KEY_BLOCK = "block";
    public static final String KEY_META = "meta";
    public static final String KEY_TE = "te";
    public static final String KEY_BYTES = "bytes";
    public static final String KEY_DIM = "dim";
    public static final String KEY_X = "x";
    public static final String KEY_Y = "y";
    public static final String KEY_Z = "z";
    public static final String KEY_TIME = "time";

    /** 快照上限：512 KB（用户定）。 */
    public static final int MAX_SNAPSHOT_BYTES = 512 * 1024;

    /** 复制结果。 */
    public static final class Result {

        public final boolean ok;
        public final boolean needsConfirm;
        public final String message;

        private Result(boolean ok, boolean needsConfirm, String message) {
            this.ok = ok;
            this.needsConfirm = needsConfirm;
            this.message = message;
        }
    }

    /**
     * 判断该方块是否属于"敏感方块"（复制前需确认）。
     *
     * <p>依据：AE2 侧凡实现 {@code appeng.api.networking.IGridHost} 的 TE（线缆/总线/节点/存储元件等），
     * 复制会出现"第二个同 ID 节点"；GT 侧 {@code BaseMetaTileEntity} 且其 MTE 为多方块控制器
     * （{@code MTEMultiBlockBase}）时同理。
     */
    public static boolean isSensitive(TileEntity te) {
        if (te == null) return false;
        try {
            if (te instanceof appeng.api.networking.IGridHost) return true;
        } catch (Throwable ignored) {
            // AE2 一定在场（本模组依赖它），这里只是防御式写法
        }
        try {
            if (te instanceof gregtech.api.metatileentity.BaseMetaTileEntity) {
                // 注意：返回类型是 gregtech.api.interfaces.metatileentity.IMetaTileEntity
                gregtech.api.interfaces.metatileentity.IMetaTileEntity mte = ((gregtech.api.metatileentity.BaseMetaTileEntity) te)
                    .getMetaTileEntity();
                if (mte instanceof gregtech.api.metatileentity.implementations.MTEMultiBlockBase) return true;
            }
        } catch (Throwable ignored) {
            // 同上
        }
        return false;
    }

    /**
     * 执行复制（**服务端 tick 线程**）。
     *
     * @param confirmed 敏感方块是否已经过确认（false 时遇到敏感方块会返回 {@code needsConfirm}）
     */
    public static Result copyBlock(EntityPlayerMP player, World world, int x, int y, int z, boolean confirmed) {
        if (player == null || world == null) return new Result(false, false, "无效的玩家或世界");
        if (!player.capabilities.isCreativeMode) {
            MyMod.LOG.info("[AE2QoL] 方块复制：非创造模式，忽略（玩家={}）", player.getCommandSenderName());
            return new Result(false, false, "仅创造模式可用");
        }
        Block block = world.getBlock(x, y, z);
        if (block == null || block.isAir(world, x, y, z)) {
            return new Result(false, false, "该位置没有方块");
        }
        int meta = world.getBlockMetadata(x, y, z);
        TileEntity te = world.getTileEntity(x, y, z);

        if (te != null && isSensitive(te) && !confirmed) {
            return new Result(false, true, "该方块为敏感方块（AE2 网络节点/存储元件 或 GT 多方块控制器）");
        }

        NBTTagCompound root = new NBTTagCompound();
        root.setString(KEY_BLOCK, Block.blockRegistry.getNameForObject(block));
        root.setInteger(KEY_META, meta);
        root.setInteger(KEY_DIM, world.provider.dimensionId);
        root.setInteger(KEY_X, x);
        root.setInteger(KEY_Y, y);
        root.setInteger(KEY_Z, z);
        root.setLong(KEY_TIME, System.currentTimeMillis());
        if (te != null) {
            NBTTagCompound teTag = new NBTTagCompound();
            try {
                te.writeToNBT(teTag);
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 方块复制：读取 TileEntity NBT 失败（{} @ {},{},{}）", te.getClass().getName(), x, y, z, t);
                return new Result(false, false, "读取该方块的数据失败（已记日志）");
            }
            int size = measure(teTag);
            if (size > MAX_SNAPSHOT_BYTES) {
                MyMod.LOG.warn(
                    "[AE2QoL] 方块复制：拒绝，NBT {} 字节 > 上限 {} 字节（{} @ {},{},{}）",
                    size,
                    MAX_SNAPSHOT_BYTES,
                    te.getClass().getName(),
                    x,
                    y,
                    z);
                return new Result(false, false, "该方块数据过大（" + (size / 1024) + " KB / 上限 512 KB），已拒绝");
            }
            root.setTag(KEY_TE, teTag);
            root.setInteger(KEY_BYTES, size);
        } else {
            root.setInteger(KEY_BYTES, 0);
        }

        ItemStack out = new ItemStack(block, 1, meta);
        if (out.getTagCompound() == null) out.setTagCompound(new NBTTagCompound());
        out.getTagCompound()
            .setTag(NBT_ROOT, root);

        boolean added = false;
        try {
            added = player.inventory.addItemStackToInventory(out);
            if (!added) player.dropPlayerItemWithRandomChoice(out, false);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 方块复制：放入背包失败", t);
            return new Result(false, false, "放入背包失败（已记日志）");
        }
        MyMod.LOG.info(
            "[AE2QoL] 方块复制成功：{}:{} @ {},{},{}（TE 数据 {} 字节，敏感={}，{}）",
            Block.blockRegistry.getNameForObject(block),
            meta,
            x,
            y,
            z,
            te == null ? 0 : root.getInteger(KEY_BYTES),
            te != null && isSensitive(te),
            added ? "进背包" : "掉在脚下");
        return new Result(true, false, null);
    }

    /** 该物品是否带我们的方块快照。 */
    public static boolean hasSnapshot(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey(NBT_ROOT);
    }

    /** 读取快照子键（可能为 null）。 */
    public static NBTTagCompound getSnapshot(ItemStack stack) {
        if (!hasSnapshot(stack)) return null;
        return stack.getTagCompound()
            .getCompoundTag(NBT_ROOT);
    }

    /** 清掉物品上的快照（还原后调用，防重复使用）。 */
    public static void clearSnapshot(ItemStack stack) {
        if (!hasSnapshot(stack)) return;
        stack.getTagCompound()
            .removeTag(NBT_ROOT);
        if (stack.getTagCompound()
            .hasNoTags()) {
            stack.setTagCompound(null);
        }
    }

    /**
     * 把快照写回刚放下的方块（**服务端 tick 线程**）。
     *
     * @return 是否成功写回
     */
    public static boolean applySnapshot(EntityPlayer player, World world, int x, int y, int z, ItemStack source) {
        NBTTagCompound snap = getSnapshot(source);
        if (snap == null) return false;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) {
            MyMod.LOG.info("[AE2QoL] 方块还原：目标位置没有 TileEntity（{} @ {},{},{}），仅保留物品", x, y, z);
            return false;
        }
        if (!snap.hasKey(KEY_TE)) {
            MyMod.LOG.info("[AE2QoL] 方块还原：快照不含 TE 数据（普通方块），无需写回");
            return false;
        }
        try {
            te.readFromNBT(snap.getCompoundTag(KEY_TE));
            te.markDirty();
            world.markBlockForUpdate(x, y, z);
            MyMod.LOG.info(
                "[AE2QoL] 方块还原成功：{} @ {},{},{}（写回 {} 字节，来源 {} {}）",
                te.getClass()
                    .getName(),
                x,
                y,
                z,
                snap.getInteger(KEY_BYTES),
                snap.getInteger(KEY_DIM),
                snap.getInteger(KEY_X) + "," + snap.getInteger(KEY_Y) + "," + snap.getInteger(KEY_Z));
            return true;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 方块还原：写回 TE NBT 失败（已记日志）", t);
            return false;
        }
    }

    /** 给玩家发一条聊天提示（含可选的可点击命令）。 */
    public static void tell(EntityPlayerMP player, String text, String commandLabel, String command) {
        if (player == null) return;
        ChatComponentText msg = new ChatComponentText(EnumChatFormatting.AQUA + "[AE2-QoL] " + EnumChatFormatting.RESET + text);
        if (command != null) {
            net.minecraft.util.ChatStyle style = new net.minecraft.util.ChatStyle();
            style.setChatClickEvent(
                new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.RUN_COMMAND, command));
            style.setColor(EnumChatFormatting.GREEN);
            msg.appendSibling(new ChatComponentText(" " + commandLabel).setChatStyle(style));
        }
        player.addChatMessage(msg);
    }

    /** 量 NBT 的序列化字节数（用于上限判断）。 */
    private static int measure(NBTTagCompound tag) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            net.minecraft.nbt.CompressedStreamTools.write(tag, new DataOutputStream(baos));
            return baos.size();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 方块复制：量 NBT 体积失败，按超限处理", t);
            return Integer.MAX_VALUE;
        }
    }
}
