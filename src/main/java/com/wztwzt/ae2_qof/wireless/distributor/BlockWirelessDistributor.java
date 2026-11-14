package com.wztwzt.ae2_qof.wireless.distributor;

import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.MyMod;

import appeng.block.AEBaseTileBlock;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;

/**
 * ME 无线网络分配器方块（GT ME 仓室）。
 *
 * <p>
 * 右键打开列表界面；Shift+右键扫描并自动连接半径内所有可用的 GT ME 仓室。
 * 只连接“尚未接入任何 ME 网络”的 GT 仓室，避免把别人的网络并进来。
 */
public class BlockWirelessDistributor extends AEBaseTileBlock {

    // 142：原 130 与 ItemSmartWildcardPattern 的旧编辑器冲突，导致右键分配器打不开界面。
    public static final int GUI_ID = 142;

    public BlockWirelessDistributor() {
        super(Material.iron);
        setBlockName("ae2qol.wireless_distributor");
        setBlockTextureName("appliedenergistics2:BlockWireless");
        setHardness(3.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 2);
        setTileEntity(TileWirelessDistributor.class);
    }

    @Override
    public void setTileEntity(final Class<? extends TileEntity> clazz) {
        AEBaseTile.registerTileItem(clazz, new BlockStackSrc(this, 0, ActivityState.Enabled));
        super.setTileEntity(clazz);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, player, stack);
        if (player instanceof EntityPlayer) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileWirelessDistributor) {
                ((TileWirelessDistributor) tile).setOwner(((EntityPlayer) player).getUniqueID());
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileWirelessDistributor)) return false;
        TileWirelessDistributor distributor = (TileWirelessDistributor) tile;
        if (world.isRemote) return true;

        if (!distributor.isOwner(player)) {
            player.addChatMessage(
                new net.minecraft.util.ChatComponentTranslation("gui.ae2_qof.wireless_distributor.not_owner"));
            return true;
        }
        if (player.isSneaking()) {
            distributor.alert(player);
            return true;
        }
        FMLNetworkHandler.openGui(player, MyMod.instance, GUI_ID, world, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileWirelessDistributor) {
            ((TileWirelessDistributor) tile).onBlockBroken();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> lines, boolean advanced) {
        for (int i = 1; i <= 4; i++) {
            String key = "tile.ae2qol.wireless_distributor.tooltip." + i;
            String line = StatCollector.translateToLocal(key);
            if (line != null && !line.isEmpty() && !line.equals(key)) {
                lines.add(EnumChatFormatting.GRAY + line);
            }
        }
        lines.add(EnumChatFormatting.DARK_GRAY + "ae2qof");
    }

    public ItemStack stack() {
        return new ItemStack(this, 1);
    }
}
