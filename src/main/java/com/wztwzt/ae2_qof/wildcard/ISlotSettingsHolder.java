package com.wztwzt.ae2_qof.wildcard;

/**
 * 宿主机器实现本接口后即可提供"按样板格的独立设置"（4.0.0 新增）。
 *
 * <p>由三个 mixin 各自给宿主挂 {@code @Unique} 字段并注入该机器自己的 {@code saveNBTData/loadNBTData}
 * 持久化：
 * <ul>
 * <li>{@code mixin/gt/MixinMTEHatchCraftingInputMESlotSettings}（GT 2714/2715）</li>
 * <li>{@code mixin/gt/MixinSuperCraftingInputHatchMESlotSettings}（GTNL 21504/21505）</li>
 * <li>{@code mixin/ph/MixinPatternDualInputHatchSlotSettings}（PH 22069 / MK.II 22179 / 本模组 MK.III 32108）</li>
 * </ul>
 * 网络包与 GUI 通过 {@code instanceof ISlotSettingsHolder} 取用，避免为每个宿主写一套分支。
 */
public interface ISlotSettingsHolder {

    /** 本机器的按格设置容器（惰性创建，永不返回 null）。 */
    SlotSettingsStore ae2qol$slotSettings();
}
