package com.wztwzt.ae2_qof.mixin.ae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.wztwzt.ae2_qof.network.ServerTerminalHelper;

import appeng.client.KeyBindHandler;

/**
 * fix53-diag：**客户端**「世界中键取物」链路埋点（仅记录，不改变任何行为）。
 * <p>
 * 为什么需要它：服务端的 {@code PacketPickBlock} 埋点只能证明"包没到"，
 * 证明不了"客户端到底卡在哪一步"。本类把客户端这一侧的两处关键判定记录下来：
 * <ul>
 * <li>{@code arePickBlockBindsEqual()} —— AE2 的 Pick Block 键是否与原版「选取方块」同键
 * （两条互补路径分别要求「不等」与「相等」，这是本例真正的开关）；</li>
 * <li>{@code handlePickBlock()} —— AE2 是否真的决定发包（返回 true 即已发出）。</li>
 * </ul>
 */
@Mixin(value = KeyBindHandler.class, remap = false)
public abstract class MixinKeyBindHandler {

    @Inject(method = "arePickBlockBindsEqual", at = @At("RETURN"), remap = false)
    private static void ae2qol$diagBindsEqual(CallbackInfoReturnable<Boolean> cir) {
        ServerTerminalHelper.diagOnce(
            "CLIENT-BINDS",
            "AE2 Pick Block 是否与原版「选取方块」同键 = " + cir.getReturnValue() + "（false 时两条路径都不接管）");
    }

    @Inject(method = "handlePickBlock", at = @At("RETURN"), remap = false)
    private static void ae2qol$diagHandlePickBlock(CallbackInfoReturnable<Boolean> cir) {
        ServerTerminalHelper.diagOnce(
            "CLIENT-HANDLE",
            "AE2 取物处理返回 " + cir.getReturnValue() + "（true = 已向服务端发出 PacketPickBlock）");
    }
}
