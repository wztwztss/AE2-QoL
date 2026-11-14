package com.wztwzt.ae2_qof.ph;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.mixin.ph.MixinPatternDualInputHatchAccess;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 样板总成拆除（4.5.0-fix18）：MK.III / MK.IV GUI 的「拆除」按钮服务端逻辑。
 *
 * <h2>语义（用户拍板）</h2>
 * 点击后机器直接拆除：**机器本体（含完整 NBT）+ 内部样板（patternSlots 原样保留在 NBT 里，
 * 不掉出来）一起进玩家背包**；改名（customName）同样随 NBT 保留，并且物品名直接显示改名后的名字。
 *
 * <h2>为什么 NBT 能原样复活</h2>
 * - 拆解：{@code BaseMetaTileEntity.writeToNBT} → {@code saveMetaTileNBT} →
 * {@code MetaTileEntity.saveNBTData}（MK.III 的 PatternSlotPersistence.save + PH 的 customName/
 * multiplier 都在这条链上，CommonBaseMetaTileEntity :302-331 字节码级对称）；
 * - 放置：GT {@code ItemMachines.placeBlockAt}（reference :254-320）对物品 NBT 调
 * {@code setInitialValuesAsNBT} → {@code loadMetaTileNBT} —— 与存档读档同一条路径，幂等，
 * 不需要本模组做任何放置侧改动，也不会产生幽灵样板。
 *
 * <h2>ME 网络侧</h2>
 * {@code world.setBlockToAir} 走 GT 正常 break 流程：{@code onBlockDestroyed} 会 refundAll +
 * 样板掉落 —— 但内部样板此时已经 0 堆叠（pattern[i] 在 writeToNBT 后我们不清理！）——
 * 所以拆解顺序必须是：先 writeToNBT → 再把 pattern[] 置空（防止 onBlockDestroyed 又掉一份）
 * → 最后清方块。这是防复制的核心顺序。
 */
public final class PatternBufferDismantle {

    private PatternBufferDismantle() {}

    /**
     * 服务端拆除一台 MK.III / MK.IV（或任何 GT 机器，当前只开放给这两台）。
     *
     * @return true = 拆除成功；false = 条件不满足（背包满等），机器保留
     */
    public static boolean dismantle(EntityPlayer player, TileEntity te) {
        if (player == null || te == null || player.worldObj == null || player.worldObj.isRemote) return false;
        if (!(te instanceof IGregTechTileEntity gtTE)) return false;

        final World world = player.worldObj;
        final int x = te.xCoord, y = te.yCoord, z = te.zCoord;
        final IMetaTileEntity mte = gtTE.getMetaTileEntity();
        if (mte == null) return false;

        // 仅开放给本模组两台总成（类型判定走模板类，Inst 是其内部类）。
        if (!(mte instanceof MTEPatternCraftingBufferMKIII) && !(mte instanceof MTEPatternCraftingBufferMKIV)) {
            return false;
        }

        final int metaId = gtTE.getMetaTileID();

        // 1. 序列化完整 TE NBT（含 Inventory / patternSlots / multiplier / customName / 朝向 / ME proxy）。
        // BaseMetaTileEntity.writeToNBT → saveMetaTileNBT → MTE.saveNBTData，与放置侧
        // setInitialValuesAsNBT → loadMetaTileNBT 完全对称，因此这份 NBT 重放即完整复活。
        final NBTTagCompound teNbt = new NBTTagCompound();
        te.writeToNBT(teNbt);

        // 2. 组装机器物品（meta 对应 MK.III=32108 / MK.IV=32109），NBT 整包挂上。
        final ItemStack machine = new ItemStack(GregTechAPI.sBlockMachines, 1, metaId);
        machine.setTagCompound(teNbt);
        // 改名 → 原版 display.Name（1.7.10 getDisplayName 优先读它，GT 物品显示链不读 NBT）。
        stampCustomName(machine, te);

        // 3. 先入包：背包满时**什么都还没动**，机器与样板原样留在世界里（零丢失）。
        if (!player.inventory.addItemStackToInventory(machine)) {
            MyMod.LOG.info("[AE2QoL] 拆除中止：玩家背包已满 @ [{},{},{}]", x, y, z);
            return false;
        }

        // 4. 防复制：清空 TE 侧会被 GT breakBlock 掉落/PH onBlockDestroyed 退款的全部内容
        // —— 这些内容已经完整存在于物品 NBT 里（Inventory 标签 + patternSlots），
        // 不清就会与物品 NBT 形成双份（掉一地样板 + 缓冲材料，同时物品里还带一份）。
        clearTileContents(mte, gtTE);

        // 5. 清方块：此时 TE 已空，GT breakBlock 的掉落循环与 PH 的 refundAll 都无事可做。
        world.setBlockToAir(x, y, z);
        world.removeTileEntity(x, y, z);

        // 6. 关掉玩家正开着的这台机器的 GUI —— 面板里的槽位/syncHandler 全都指向已销毁的 TE，
        // 不关会留在客户端对空气操作（GT 自己的多块 GUI 也是这么收尾的：
        // MTEMultiBlockBaseGui :404 在机器消失后 player.closeScreen()）。
        player.closeScreen();

        MyMod.LOG.info(
            "[AE2QoL] 样板总成已拆除（NBT 完整保留，样板随机器入包）@ d{} [{},{},{}] 玩家={} meta={}",
            player.dimension,
            x,
            y,
            z,
            player.getCommandSenderName(),
            metaId);
        return true;
    }

    /**
     * 清空 TE 侧所有会被「掉落 / 退款」逻辑再吐一份的内容。
     *
     * <p>
     * 两类来源：
     * <ul>
     * <li>GT {@code BlockMachines.breakBlock}（reference :473-506）：遍历 {@code getSizeInventory()}，
     * 把每个非空槽掉成 EntityItem —— 缓冲里的物品/流体桶就在其中；</li>
     * <li>PH {@code PatternDualInputHatch.onBlockDestroyed()}：把 {@code pattern[]} 全部 refundAll
     * 掉成 EntityItem —— 也就是「拆机器样板撒一地」的来源。</li>
     * </ul>
     * 两者内容都已经在物品 NBT 里（{@code Inventory} 标签 + {@code patternSlots}），
     * 因此这里必须清空，否则就是双份。
     */
    private static void clearTileContents(IMetaTileEntity mte, IGregTechTileEntity gtTE) {
        // pattern[]：PH 的样板数组（通过 accessor mixin 跨包写）。
        try {
            final MixinPatternDualInputHatchAccess acc = (MixinPatternDualInputHatchAccess) mte;
            final ItemStack[] patterns = acc.getAe2qolPattern();
            if (patterns != null) {
                for (int i = 0; i < patterns.length; i++) {
                    patterns[i] = null;
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 拆除：清空 pattern[] 失败（可能产生样板复制）", t);
        }

        // GT 库存（缓冲物品/流体容器）：直接置空真实库存数组。
        try {
            final ItemStack[] inv = mte.getRealInventory();
            if (inv != null) {
                for (int i = 0; i < inv.length; i++) {
                    inv[i] = null;
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 拆除：清空 GT 库存失败（可能产生缓冲物品复制）", t);
        }
    }

    /**
     * 物品名显示（用户拍板：直接显示改名的名字）。
     * GT {@code ItemMachines.getItemStackDisplayName} 只走模板 {@code getLocalName()}，
     * 不读物品 NBT；但 1.7.10 原版 {@code ItemStack.getDisplayName()} 优先读 NBT
     * {@code display.Name} —— 所以把 TE 的 customName 同步写进原版 display 标签即可显示，
     * 多余键对放置侧 {@code setInitialValuesAsNBT} 无害（只读已知键）。
     */
    public static String displayName(ItemStack stack, String fallback) {
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("display")) {
            final String dn = stack.getTagCompound()
                .getCompoundTag("display")
                .getString("Name");
            if (dn != null && !dn.isEmpty()) return dn;
        }
        return fallback;
    }

    /** 把 TE 的 customName 同步写进物品 NBT 的原版 display.Name 标签（writeToNBT 只写 customName 键，不写 display）。 */
    public static void stampCustomName(ItemStack stack, TileEntity te) {
        if (stack == null || te == null || !(te instanceof IGregTechTileEntity gtTE)) return;
        final IMetaTileEntity mte = gtTE.getMetaTileEntity();
        String name = null;
        if (mte instanceof appeng.helpers.ICustomNameObject cno) {
            name = cno.hasCustomName() ? cno.getCustomName() : null;
        }
        if (name == null || name.isEmpty()) return;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }
        if (!nbt.hasKey("display")) {
            final NBTTagCompound disp = new NBTTagCompound();
            disp.setString("Name", name);
            nbt.setTag("display", disp);
        }
    }
}
