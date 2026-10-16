package com.wztwzt.ae2_qof.proline;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 产线绑定卡（设计 §5.2）：手持 + Shift+右键机器 = 把该机器绑定到 NBT 里指向的（产线，步骤）。
 *
 * <p>
 * NBT 字段（v6 A1）：{@code lineId} + {@code stepId}（稳定 id，不用下标）。
 * 交互全部在服务端 {@code onItemUse} 落地（客户端只负责写入 NBT 与提示），
 * 校验链 = §5.2 的四条（SU 门 / 产线团队 / MTEMultiBlockBase / 互斥）——
 * 注：A2 拍板后 binding 会另做候选面板，这里保留"精细兜底"入口。
 */
public class ItemProductionLineCard extends Item {

    public ItemProductionLineCard() {
        setUnlocalizedName("ae2_qof.proline_card");
        setTextureName("ae2_qof:proline_card");
        setMaxStackSize(1);
        setHasSubtypes(false);
        // 泄漏/可发现性审计：不挂创造标签的话只能在 NEI 搜到——挂到主创造页保持可发现性
        setCreativeTab(com.wztwzt.ae2_qof.AE2QoLCreativeTab.INSTANCE);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, 0));
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote || player == null) return true;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("PLLine")) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED
                        + net.minecraft.util.StatCollector.translateToLocal("ae2_qof.proline.card.no_target")));
            return true;
        }
        String lineId = tag.getString("PLLine");
        String stepId = tag.getString("PLStep");

        // 机器必须是 GT 多方块（§5.2 校验 2）
        boolean ok = false;
        try {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity) {
                gregtech.api.interfaces.metatileentity.IMetaTileEntity mte = ((gregtech.api.interfaces.tileentity.IGregTechTileEntity) te)
                    .getMetaTileEntity();
                ok = mte instanceof gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
            }
        } catch (Throwable ignored) {}
        if (!ok) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED
                        + net.minecraft.util.StatCollector.translateToLocal("ae2_qof.proline.card.not_gt")));
            return true;
        }

        // §5.2 校验 3：产线团队须包含该机器所在声明区（目标区块团队拆走时拒）
        MachineRef ref = new MachineRef(world.provider.dimensionId, x, y, z, System.currentTimeMillis());
        if (ProLineGate.isPresent()) {
            Object chunkTeam = ProLineGate.teamOfChunk(world.provider.dimensionId, x >> 4, z >> 4);
            Object playerTeam = ProLineGate.teamOfPlayer(resolveMp(player));
            if (chunkTeam != null && playerTeam != null && chunkTeam != playerTeam) {
                player.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + net.minecraft.util.StatCollector
                            .translateToLocal("ae2_qof.proline.card.chunk_not_team")));
                return true;
            }
        }

        boolean done = ProLineManager.bindMachine(resolveMp(player), lineId, stepId, ref);
        player.addChatMessage(
            new ChatComponentText(
                done ? EnumChatFormatting.GREEN
                    + net.minecraft.util.StatCollector.translateToLocal("ae2_qof.proline.card.ok")
                    : EnumChatFormatting.RED
                        + net.minecraft.util.StatCollector.translateToLocal("ae2_qof.proline.card.fail")));
        return true;
    }

    /** 1.7.10 onItemUse 里的 player 就是 EntityPlayerMP（服务端分支），直接安全下转型。 */
    private static EntityPlayer resolveMp(EntityPlayer player) {
        return player;
    }

    /** GUI“写入绑定模式”：把当前选中的（产线, 步骤）写进手里的卡。 */
    public static void programCard(ItemStack card, String lineId, String stepId) {
        if (card == null || card.getItem() instanceof ItemProductionLineCard == false) return;
        NBTTagCompound tag = card.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            card.setTagCompound(tag);
        }
        tag.setString("PLLine", lineId);
        tag.setString("PLStep", stepId);
    }
}
