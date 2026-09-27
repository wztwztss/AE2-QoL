package com.wztwzt.ae2_qof.wildcard;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.MyMod;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;

/**
 * 具体样板 → {@code ICraftingPatternDetails} 的**统一解码入口**（3.43.0）。
 *
 * <h2>为什么不能直接用 API 的 {@code getPatternForItem}</h2>
 * 本工作区同时装着两套通配样板模组，而**原版 WildcardPattern 模组给 AE2 的
 * {@code appeng.items.misc.ItemEncodedPattern.getPatternForItem} 打了注入**：
 * <pre>
 * private void wildcardpattern$useLightweightPatternDetails(ItemStack stack, World world,
 *         CallbackInfoReturnable&lt;ICraftingPatternDetails&gt; cir) {
 *     if (!WildcardPatternGenerator.isWildcardPattern(stack)) return;   // 判据：tag.getBoolean("WildcardPattern")
 *     ... cir.setReturnValue(&lt;轻量预览 details&gt;);                        // 输出恒为模板的代表输出
 * }
 * </pre>
 * 我们的 {@code ItemSmartWildcardPattern extends ItemEncodedPattern}，而具体样板又克隆了通配样板本体的整份 NBT
 * ⇒ 该注入会把我们的**具体样板**也认成通配样板，返回"预览 details"（输出全是那一个模板物品）。
 * 实测证据（3.42.0-diag 配对打印）：
 * <pre>
 * 配对样本（GT）：concrete@1967ead7 … ‖ details=WildcardPreviewPatternDetails … ‖ details.getOutputs()[0]=铁板
 * </pre>
 *
 * <h2>本类的两条路径</h2>
 * <ol>
 * <li><b>本模组物品</b>：直接 {@code new appeng.helpers.PatternHelper(stack, world)}——这是 AE2 自己的真身构造器
 * （`getPatternForItem` 内部也只是 `new PatternHelper(...)`），**不经过任何注入**，实测无内部缓存、不回写 NBT。
 * 这条路径同时救回**已经存进机器、NBT 里还留着旧标记的历史具体样板**（3.43.0 之前生成的那些）。</li>
 * <li><b>其它物品</b>：照旧走 {@code ICraftingPatternItem.getPatternForItem}——AE2FC 等自定义样板物品有各自的
 * details 实现（如流体样板），绝不能一律替换成 {@code PatternHelper}。</li>
 * </ol>
 */
public final class SmartWildcardDecoder {

    private SmartWildcardDecoder() {}

    /** 解码一张样板；失败返回 null（调用方各自决定如何留痕，本方法只在"直连失败"时记 WARN）。 */
    public static ICraftingPatternDetails decode(ItemStack stack, World world) {
        if (stack == null || stack.getItem() == null) return null;
        boolean ours = stack.getItem() instanceof ItemSmartWildcardPattern;
        if (ours) {
            try {
                return new appeng.helpers.PatternHelper(stack, world);
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 直连 PatternHelper 解码失败，回退 API 路径（可能有第三方注入）", t);
            }
        }
        if (stack.getItem() instanceof ICraftingPatternItem item) {
            return item.getPatternForItem(stack, world);
        }
        return null;
    }
}
