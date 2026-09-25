package com.wztwzt.ae2_qof.mixin.ae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizon.gtnhlib.event.PickBlockEvent;
import com.wztwzt.ae2_qof.network.ServerTerminalHelper;

import appeng.client.ClientHelper;

/**
 * fix53-diag：证明「AE2 的 Pick Block 事件路径到底有没有被触发」（仅记录，不改变任何行为）。
 * <p>
 * GTNHLib 的 {@code MixinMinecraft_PickBlockTrap} 会在原版中键取物前 post 一个 {@link PickBlockEvent}，
 * AE2 的 {@link ClientHelper#onPickBlockEvent} 在其中判断是否接管。
 * 只有当 AE2 的 Pick Block 键与原版「选取方块」**同键**时才会走到这里，
 * 因此本行是否出现，直接区分"事件路径生效"与"根本没进这条路径"。
 */
@Mixin(value = ClientHelper.class, remap = false)
public abstract class MixinClientHelperPickBlock {

    @Inject(method = "onPickBlockEvent", at = @At("HEAD"), remap = false)
    private void ae2qol$diagPickBlockEvent(PickBlockEvent event, CallbackInfo ci) {
        ServerTerminalHelper.diagOnce("CLIENT-EVENT", "客户端收到 GTNHLib PickBlockEvent（事件路径已生效）");
    }
}
