package com.wztwzt.ae2_qof.mixin.client;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.network.ServerTerminalHelper;

/**
 * fix53-diag：确认「原版中键取物例程 `middleClickMouse()`」到底有没有被走到（**仅记录，不改变行为**）。
 * <p>
 * 为什么需要它：GTNHLib 的 `MixinMinecraft_PickBlockTrap` 就挂在 `middleClickMouse()` 的 HEAD，
 * 它负责 post `PickBlockEvent`——AE2 的世界中键取物全靠这个事件。
 * 因此本标记与 `CLIENT-EVENT` 组合起来即可判定失败环节：
 * <ul>
 * <li>两个都没有 ⇒ 中键**没走到**取物例程（输入被吞 / 模式不对）；</li>
 * <li>只有 ENTER、没有 CLIENT-EVENT ⇒ 例程进来了，但事件没被 AE2 收到（被更高优先级处理器消费 / 事件未分发）；</li>
 * <li>ENTER + END、没有 CLIENT-EVENT ⇒ 例程正常走完却没触发事件（钩子本身的问题）。</li>
 * </ul>
 * 方法名同时给出 MCP 名与 SRG 名（本整合包运行时用 SRG 名），与 `MixinTextureMap` 同一写法。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftPickBlockDiag {

    @Inject(method = { "middleClickMouse", "func_147112_ai" }, at = @At("HEAD"), remap = false)
    private void ae2qol$diagMiddleClickEnter(CallbackInfo ci) {
        ServerTerminalHelper.diagOnce("VANILLA-MIDDLE-ENTER", "原版 middleClickMouse() 被调用（中键走到了取物例程）");
    }

    @Inject(method = { "middleClickMouse", "func_147112_ai" }, at = @At("RETURN"), remap = false)
    private void ae2qol$diagMiddleClickEnd(CallbackInfo ci) {
        ServerTerminalHelper.diagOnce("VANILLA-MIDDLE-END", "middleClickMouse() 正常走完（未被其它模组取消）");
    }
}
