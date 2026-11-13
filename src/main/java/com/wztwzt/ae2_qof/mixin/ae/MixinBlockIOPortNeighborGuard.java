package com.wztwzt.ae2_qof.mixin.ae;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.block.storage.BlockIOPort;
import appeng.tile.storage.TileIOPort;

/**
 * 守卫 AE2 {@link BlockIOPort} 的邻居更新回调，避免非 IO 端口实体被强转。
 * <p>
 * 背景：{@code BlockQuestDetector extends BlockIOPort}（复用 AE2 机器渲染管线），但它的方块实体是
 * {@code TileQuestDetector extends AENetworkTile}，不是 {@code TileIOPort}。AE2 的
 * {@code BlockIOPort.func_149695_a}（即 {@code onNeighborBlockChange}）是 {@code public final}，
 * 内部先 {@code getTileEntity(...)} 再 {@code checkcast TileIOPort}，因此任何邻居方块更新落到
 * 任务检测器上都会抛出 {@code ClassCastException}，打崩服务端 tick。子类无法覆写 final 方法，
 * 只能在这里按实际实体类型放行。
 * <p>
 * 只在实体不是 {@code TileIOPort} 时取消原方法：普通 IO 端口（含继承 {@code TileIOPort} 的
 * {@code TileExIOPort}）行为完全不变；这也覆盖其它模组可能存在的 {@code BlockIOPort} 子类。
 */
@Mixin(value = BlockIOPort.class, remap = false)
public abstract class MixinBlockIOPortNeighborGuard {

    @Inject(method = "func_149695_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2qol$skipNonIOPortNeighborUpdate(final World world, final int x, final int y, final int z,
        final Block block, final CallbackInfo ci) {
        if (!(world.getTileEntity(x, y, z) instanceof TileIOPort)) {
            ci.cancel();
        }
    }
}
