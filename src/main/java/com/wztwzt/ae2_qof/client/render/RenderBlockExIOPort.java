package com.wztwzt.ae2_qof.client.render;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import appeng.block.storage.BlockIOPort;
import appeng.client.render.blocks.RenderIOPort;

/**
 * 强化版 IO 端口渲染器：复用原版方块图标，保持兼容性。
 * <p>
 * AE2 1050 起父类 {@code BlockIOPort.getRenderer()} 的返回类型收窄为具体的
 * {@code RenderIOPort}（977 时代为 {@code BaseBlockRender<?,?>}）。本类因此改继承
 * {@code RenderIOPort}，以便继续作为该方块的渲染器。
 * <p>
 * 但渲染路径仍走 {@code BaseBlockRender} 的通用实现（与 977 时代本类的行为一致），
 * 不套用 {@code RenderIOPort} 的 IO 端口专用着色逻辑，避免改变既有外观。
 */
public class RenderBlockExIOPort extends RenderIOPort {

    public RenderBlockExIOPort() {
        // AE2 1050 的 RenderIOPort 只保留无参构造（内部 super(false, 0)）。
        // 其余保持与本类 977 时代一致的渲染行为：走通用 renderInWorld。
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
