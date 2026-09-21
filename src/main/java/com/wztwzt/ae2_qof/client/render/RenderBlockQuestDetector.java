package com.wztwzt.ae2_qof.client.render;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import appeng.block.storage.BlockIOPort;
import appeng.client.render.blocks.RenderIOPort;

/**
 * ME 任务检测器渲染器：复用 AE2 机器方块图标管线（与强化 IO 端口同模式），保持兼容性。
 * <p>
 * AE2 1050 起父类 {@code BlockIOPort.getRenderer()} 的返回类型收窄为具体的
 * {@code RenderIOPort}（977 时代为 {@code BaseBlockRender<?,?>}）。本类因此改继承
 * {@code RenderIOPort}，以便继续作为该方块的渲染器。
 * <p>
 * {@code TileQuestDetector} 继承 {@code AENetworkTile} 而非 {@code TileIOPort}，
 * 不能套用 {@code RenderIOPort} 的 IO 端口专用渲染逻辑，故此处显式走
 * {@code BaseBlockRender} 的通用渲染路径（与 977 时代本类的行为完全一致）。
 */
public class RenderBlockQuestDetector extends RenderIOPort {

    public RenderBlockQuestDetector() {
        // AE2 1050 的 RenderIOPort 只保留无参构造（内部 super(false, 0)）。
        super();
    }

    @Override
    public boolean renderInWorld(final BlockIOPort block, final IBlockAccess world, final int x, final int y,
        final int z, final RenderBlocks renderer) {
        this.preRenderInWorld(block, world, x, y, z, renderer);
        final boolean rendered = renderer.renderStandardBlock(block, x, y, z);
        this.postRenderInWorld(renderer);
        return rendered;
    }
}
