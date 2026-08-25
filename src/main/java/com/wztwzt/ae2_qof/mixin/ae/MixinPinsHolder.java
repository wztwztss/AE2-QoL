package com.wztwzt.ae2_qof.mixin.ae;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.config.PinsRows;
import appeng.items.contents.PinsHolder;

/**
 * 3.14.0：合成产物 pin 行默认开启。
 * PinsHolder.getCraftingPinsRows 对「从未设置过的玩家」默认返回 DISABLED，
 * 导致原生 pin 置顶行功能默认不可见。此处把 computeIfAbsent 的缺省值改为 ONE：
 * <ul>
 * <li>首次使用即拥有 1 行 crafting pin（下单产物自动置顶）；</li>
 * <li>玩家在终端设置中手动选择 DISABLED 时，setPinsRows 会显式 put(DISABLED)，
 *     computeIfAbsent 不再走缺省值——尊重用户关闭的选择。</li>
 * </ul>
 */
@Mixin(value = PinsHolder.class, remap = false)
public abstract class MixinPinsHolder {

    @Redirect(
        method = "getCraftingPinsRows",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/HashMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
        remap = false)
    private Object ae2qol$defaultOneRow(HashMap<UUID, PinsRows> map, Object key, Function<?, ?> mapping) {
        // settings.json 总开关关闭时不自动开启（玩家仍可在终端设置手动选行数）
        if (!com.wztwzt.ae2_qof.Config.pinRowEnabled) {
            return map.computeIfAbsent((UUID) key, k -> PinsRows.DISABLED);
        }
        return map.computeIfAbsent((UUID) key, k -> PinsRows.ONE);
    }
}
