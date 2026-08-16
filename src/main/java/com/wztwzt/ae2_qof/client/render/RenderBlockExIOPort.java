package com.wztwzt.ae2_qof.client.render;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import com.wztwzt.ae2_qof.block.BlockExIOPort;
import com.wztwzt.ae2_qof.tile.TileExIOPort;

import appeng.client.render.BaseBlockRender;

/**
 * 强化版 IO 端口渲染器：复用原版方块图标，保持兼容性。
 */
public class RenderBlockExIOPort extends BaseBlockRender<BlockExIOPort, TileExIOPort> {

    public RenderBlockExIOPort() {
        super(false, 20);
    }

    @Override
    public boolean renderInWorld(final BlockExIOPort block, final IBlockAccess world, final int x, final int y,
        final int z, final RenderBlocks renderer) {
        return super.renderInWorld(block, world, x, y, z, renderer);
    }
}
