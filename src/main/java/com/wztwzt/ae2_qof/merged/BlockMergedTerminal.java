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

    /** 部件形态 GUI ID 基址：实际 ID = 基址 + ForgeDirection.ordinal()（编码面板朝向） */
    public static final int PART_GUI_BASE = 110;

    /** 手持无线形态 GUI ID；x 参数携带终端所在背包槽位号 */
    public static final int WIRELESS_GUI_ID = 120;

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

    @Override
    public void addInformation(final net.minecraft.item.ItemStack is,
        final net.minecraft.entity.player.EntityPlayer player, final java.util.List<String> lines,
        final boolean advancedItemTooltips) {
        for (int i = 1; i <= 3; i++) {
            String key = "tile.ae2qof.merged_terminal.tooltip." + i;
            String line = net.minecraft.util.StatCollector.translateToLocal(key);
            if (line != null && !line.isEmpty() && !line.equals(key)) {
                lines.add(net.minecraft.util.EnumChatFormatting.GRAY + line);
            }
        }
        lines.add(net.minecraft.util.EnumChatFormatting.DARK_GRAY + "ae2qof");
    }
}
