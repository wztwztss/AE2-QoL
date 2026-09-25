package com.wztwzt.ae2_qof.mixin.ae;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizon.gtnhlib.event.PickBlockEvent;
import com.wztwzt.ae2_qof.network.ServerTerminalHelper;

import appeng.client.ClientHelper;

/**
 * fix53-diag：定位「AE2 的 Pick Block 事件路径为何没有真正接管」（仅记录，不改变任何行为）。
 * <p>
 * GTNHLib 的 {@code MixinMinecraft_PickBlockTrap} 会在原版中键取物前 post 一个 {@link PickBlockEvent}，
 * AE2 的 {@link ClientHelper#onPickBlockEvent} 在其中判断是否接管；其条件是
 * <b>非创造模式</b>且<b>两键相等</b>。因此这里把当时的两个条件值都记录下来：
 * <ul>
 * <li>创造模式 = true 时 AE2 **故意**不接管（交给原版中键复制方块），这本身就能解释"完全没反应"；</li>
 * <li>{@code CLIENT-EVENT-END} 用来确认该方法本身没有抛异常。</li>
 * </ul>
 */
@Mixin(value = ClientHelper.class, remap = false)
public abstract class MixinClientHelperPickBlock {

    @Inject(method = "onPickBlockEvent", at = @At("HEAD"), remap = false)
    private void ae2qol$diagPickBlockEvent(PickBlockEvent event, CallbackInfo ci) {
        try {
            final Minecraft mc = Minecraft.getMinecraft();
            ServerTerminalHelper.diagOnce(
                "CLIENT-EVENT",
                "客户端收到 GTNHLib PickBlockEvent；创造模式=" + mc.thePlayer.capabilities.isCreativeMode
                    + "，isRemote=" + mc.theWorld.isRemote
                    + "（AE2 仅在**非创造**且两键相等时才接管）");
        } catch (Throwable t) {
            ServerTerminalHelper.diagOnce("CLIENT-EVENT", "客户端收到 PickBlockEvent（读取模式信息失败: " + t + "）");
        }
    }

    @Inject(method = "onPickBlockEvent", at = @At("RETURN"), remap = false)
    private void ae2qol$diagPickBlockEventEnd(PickBlockEvent event, CallbackInfo ci) {
        ServerTerminalHelper.diagOnce("CLIENT-EVENT-END", "onPickBlockEvent 正常返回（自身未抛异常）");
    }
}
