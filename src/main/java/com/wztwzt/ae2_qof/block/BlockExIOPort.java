package com.wztwzt.ae2_qof.block;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.wztwzt.ae2_qof.tile.TileExIOPort;

import appeng.block.storage.BlockIOPort;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;

/**
 * 强化版 IO 端口方块。
 */
public class BlockExIOPort extends BlockIOPort {

    public BlockExIOPort() {
        super();
        setBlockName("ae2qol.ex_io_port");
        setBlockTextureName("ae2_qof:ex_io_port");
        setFullBlock(true);
        setOpaque(true);
        setTileEntity(TileExIOPort.class);
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
    protected com.wztwzt.ae2_qof.client.render.RenderBlockExIOPort getRenderer() {
        return new com.wztwzt.ae2_qof.client.render.RenderBlockExIOPort();
    }

    public ItemStack stack() {
        return new ItemStack(this, 1);
    }
}
