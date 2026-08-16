package com.gali.ae2_auto_pattern_upload.mixin.ae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.gali.ae2_auto_pattern_upload.Config;
import com.gali.ae2_auto_pattern_upload.tile.TileExIOPort;

import appeng.tile.storage.TileIOPort;

/**
 * 强化版 IO 端口传输倍率：对 ExIOPort 放大每次传输的物品数量。
 */
@Mixin(TileIOPort.class)
public abstract class MixinTileIOPort {

    @ModifyVariable(method = "transferContents", at = @At(value = "HEAD"), remap = false, ordinal = 0, argsOnly = true)
    private long ae2qol$transferContents(long itemsToMove) {
        if ((Object) this instanceof TileExIOPort) {
            itemsToMove *= Config.exIOPortTransferContentsRate;
        }
        return itemsToMove;
    }
}
