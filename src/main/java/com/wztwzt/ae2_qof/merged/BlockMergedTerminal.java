package com.wztwzt.ae2_qof.merged;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.client.render.RenderBlockMergedTerminal;

import appeng.block.AEBaseTileBlock;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 样板与接口二合一终端方块（有线）。材质直接复用 AE2 的接口方块。
 */
public class BlockMergedTerminal extends AEBaseTileBlock {

    public static final int GUI_ID = 100;

    public BlockMergedTerminal() {
        super(Material.iron);
        setBlockName("ae2qol.merged_terminal");
        setBlockTextureName("appliedenergistics2:BlockInterface");
        setHardness(3.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 2);
        setTileEntity(TileMergedTerminal.class);
    }

    @Override
    public void setTileEntity(final Class<? extends TileEntity> clazz) {
        AEBaseTile.registerTileItem(clazz, new BlockStackSrc(this, 0, ActivityState.Enabled));
        super.setTileEntity(clazz);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected RenderBlockMergedTerminal getRenderer() {
        return new RenderBlockMergedTerminal();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        if (player.isSneaking()) return false;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileMergedTerminal) {
            FMLNetworkHandler.openGui(player, MyMod.instance, GUI_ID, world, x, y, z);
            return true;
        }
        return false;
    }
}
