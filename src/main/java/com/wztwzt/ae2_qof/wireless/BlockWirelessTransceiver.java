package com.wztwzt.ae2_qof.wireless;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.client.render.RenderBlockTransceiver;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockWirelessTransceiver extends Block {

    public static final int GUI_ID = 1;

    public BlockWirelessTransceiver() {
        super(Material.iron);
        setBlockName("wireless_transceiver");
        setBlockTextureName(MyMod.MODID + ":wireless_transceiver");
        setHardness(3.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 2);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new TileWirelessTransceiver();
    }

    @Override
    public int getRenderType() {
        return RenderBlockTransceiver.RENDER_ID;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.blockIcon = register.registerIcon(MyMod.MODID + ":wireless_transceiver");
        RenderBlockTransceiver.INSTANCE.registerIcons(register);
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        if (player.isSneaking() && player.getHeldItem() != null
            && player.getHeldItem()
                .getItem() instanceof ItemWirelessConnector) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileWirelessTransceiver) {
            FMLNetworkHandler.openGui(player, MyMod.instance, GUI_ID, world, x, y, z);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileWirelessTransceiver) {
            TileWirelessTransceiver twt = (TileWirelessTransceiver) te;
            twt.destroyWirelessConnection();
            twt.removeAllBlockLinks();
            String freq = twt.getFrequency();
            if (twt.isMode() && freq != null && !freq.isEmpty()) {
                WirelessData.instance()
                    .unregister(freq, world);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
        world.removeTileEntity(x, y, z);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileWirelessTransceiver && placer instanceof EntityPlayer) {
            UUID uuid = ((EntityPlayer) placer).getUniqueID();
            ((TileWirelessTransceiver) te).savePlacer(uuid);
        }
    }
}
