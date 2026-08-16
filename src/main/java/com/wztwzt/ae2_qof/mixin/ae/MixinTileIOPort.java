package com.wztwzt.ae2_qof.mixin.ae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.tile.TileExIOPort;

import appeng.tile.storage.TileIOPort;

/**
 * 强化版 IO 端口传输倍率：对 ExIOPort 放大每次传输的物品数量。
 */
@Mixin(TileIOPort.class)
public abstract class MixinTileIOPort {

    @ModifyVariable(method = "transferContents", at = @At(value = "HEAD"), remap = false, ordinal = 0, argsOnly = true)
    private long ae2qol$transferContents(long itemsToMove) {
        if ((Object) this instanceof TileExIOPort) {
            int rate = Config.exIOPortTransferContentsRate;
            if (rate > 1 && itemsToMove > 0) {
                // 溢出保护：极端配置（Integer.MAX_VALUE）下乘积不超过 long 上界
                if (itemsToMove > Long.MAX_VALUE / rate) {
                    itemsToMove = Long.MAX_VALUE;
                } else {
                    itemsToMove *= rate;
                }
            }
        }
        return itemsToMove;
    }
}
