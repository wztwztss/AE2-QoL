package com.wztwzt.ae2_qof.client.render;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import com.wztwzt.ae2_qof.block.BlockQuestDetector;
import com.wztwzt.ae2_qof.tile.TileQuestDetector;

import appeng.client.render.BaseBlockRender;

/**
 * ME 任务检测器渲染器：复用 AE2 机器方块图标管线（与强化 IO 端口同模式），保持兼容性。
 */
public class RenderBlockQuestDetector extends BaseBlockRender<BlockQuestDetector, TileQuestDetector> {

    public RenderBlockQuestDetector() {
        super(false, 20);
    }

    @Override
    public boolean renderInWorld(final BlockQuestDetector block, final IBlockAccess world, final int x, final int y,
        final int z, final RenderBlocks renderer) {
        return super.renderInWorld(block, world, x, y, z, renderer);
    }
}
