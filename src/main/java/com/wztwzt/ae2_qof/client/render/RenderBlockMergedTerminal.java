package com.wztwzt.ae2_qof.client.render;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import com.wztwzt.ae2_qof.merged.BlockMergedTerminal;
import com.wztwzt.ae2_qof.merged.TileMergedTerminal;

import appeng.client.render.BaseBlockRender;

/**
 * 样板与接口二合一终端方块渲染器：复用方块图标的标准立方体渲染。
 */
public class RenderBlockMergedTerminal extends BaseBlockRender<BlockMergedTerminal, TileMergedTerminal> {

    public RenderBlockMergedTerminal() {
        super(false, 20);
    }

    @Override
    public boolean renderInWorld(final BlockMergedTerminal block, final IBlockAccess world, final int x, final int y,
        final int z, final RenderBlocks renderer) {
        return super.renderInWorld(block, world, x, y, z, renderer);
    }
}
