package com.wztwzt.ae2_qof.mixin.gtng;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 3.25.0-fix24：访问 GT-Not-Good 转写载荷的 **inputs 数组**（它是 {@code private final}）。
 *
 * <p>为什么需要：我们要在"写样板那一刻"把载荷里的**零尺寸电路/铸模幻影**换成
 * **PH:编程器电路**，而 {@code RecipeTransferPayload} 只暴露了 {@code getInput(int)}（只读），
 * 没有 setter ⇒ 只能通过访问器拿到那个数组**原地改元素**（数组引用本身是 final，元素可写）。
 *
 * <p>GT-Not-Good 为运行时软依赖（compileOnly + mixin 配置 {@code required:false}）。
 */
@Mixin(targets = "com.xyp.gtnotgood.ae2thing.quickterminal.RecipeTransferPayload", remap = false)
public interface MixinRecipeTransferPayload {

    /** 直接拿到载荷的 inputs 数组（元素可原地替换）。 */
    @Accessor("inputs")
    appeng.api.storage.data.IAEStack<?>[] ae2qol$inputs();
}
