package com.wztwzt.ae2_qof.block;

import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.tile.TileQuestDetector;

import appeng.block.storage.BlockIOPort;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;

/**
 * 「ME 任务检测器」方块（3.11.0）：接入 ME 网络，放置时绑定放置者 UUID，
 * 周期将网络物品以只读方式喂给 BetterQuesting 检索型任务（不消耗）。
 * 外观复用 AE2 机器渲染管线（BlockIOPort 系），贴图独立。
 */
public class BlockQuestDetector extends BlockIOPort {

    public BlockQuestDetector() {
        super();
        setBlockName("ae2qol.quest_detector");
        setBlockTextureName("ae2_qof:quest_detector");
        setFullBlock(true);
        setOpaque(true);
        setTileEntity(TileQuestDetector.class);
    }

    public void setOpaque(boolean opaque) {
        this.isOpaque = opaque;
    }

    public void setFullBlock(boolean full) {
        this.isFullSize = full;
    }

    @Override
    public void setTileEntity(final Class<? extends TileEntity> clazz) {
        AEBaseTile.registerTileItem(clazz, new BlockStackSrc(this, 0, ActivityState.Enabled));
        super.setTileEntity(clazz);
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    protected com.wztwzt.ae2_qof.client.render.RenderBlockQuestDetector getRenderer() {
        return new com.wztwzt.ae2_qof.client.render.RenderBlockQuestDetector();
    }

    @Override
    public void onBlockPlacedBy(final World world, final int x, final int y, final int z,
        final EntityLivingBase placer, final ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (world.isRemote) return;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileQuestDetector && placer instanceof EntityPlayer) {
            ((TileQuestDetector) te).setOwner(
                ((EntityPlayer) placer).getGameProfile()
                    .getId());
        }
    }

    @Override
    public boolean onBlockActivated(final World world, final int x, final int y, final int z,
        final EntityPlayer player, final int side, final float hitX, final float hitY, final float hitZ) {
        if (world.isRemote) return true;
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileQuestDetector) {
            final TileQuestDetector qd = (TileQuestDetector) te;
            player.addChatMessage(new net.minecraft.util.ChatComponentText(
                "§7[QuestDetector] §fOwner: " + qd.getOwner()));
            return true;
        }
        return super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ);
    }

    @Override
    public void addInformation(final ItemStack is, final EntityPlayer player, final java.util.List<String> lines,
        final boolean advancedItemTooltips) {
        for (int i = 1; i <= 3; i++) {
            String key = "tile.ae2qol.quest_detector.tooltip." + i;
            String line = net.minecraft.util.StatCollector.translateToLocal(key);
            if (line != null && !line.isEmpty() && !line.equals(key)) {
                lines.add(net.minecraft.util.EnumChatFormatting.GRAY + line);
            }
        }
        lines.add(net.minecraft.util.EnumChatFormatting.DARK_GRAY + "ae2qof");
    }
}
