package com.wztwzt.ae2_qof.mixin.ph;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardExpander;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardState;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import reobf.proghatches.gt.metatileentity.PatternDualInputHatch;

/**
 * 智能通配样板在 **PH 家族**（ProgrammableHatches）上的索引期展开（3.22.0 M1 剩余）。
 *
 * <h2>为什么是这一族先做</h2>
 * 取证（{@code docs/research/ph-gtnl-hatch-hook.md}）确认两件事：
 * <ol>
 * <li>PH 的 {@code pushPattern(ICraftingPatternDetails, InventoryCrafting)} **完全不读 details 参数**
 * （运行期 jar 反汇编里 {@code aload_1} 出现 0 次）⇒ 我们**不需要**任何「展开 details → 槽位」反查，
 * 只要把展开出来的 details 注册进网格即可推得动；</li>
 * <li>PH 的配方缓存**本来就默认关闭**（{@code INeoDualInputInventory.shouldBeCached()==false}）
 * ⇒ 也不需要像 GT 那样子类化槽位。</li>
 * </ol>
 *
 * <h2>为什么注入在 RETURN 而不是 HEAD</h2>
 * 原方法自己会做 {@code if (!isActive()) return;}，我们在 TAIL 补注册、并**自己复现该守卫**
 * （{@code @At("RETURN")} 在原方法提前 return 时同样会触发，不能依赖它）。
 *
 * <h2>为什么只读、不改 PH 的缓存数组</h2>
 * PH 的 {@code patternItemCache}/{@code patternDetailCache} 是它自己的身份缓存，
 * 而我们的 MK.III 子类会**重建**这两个数组（其源码 143-144）并还会遍历它们做黑名单
 * ⇒ 展开结果一律不写进去，只交给 {@code craftingTracker}（AE2 自己维护 details→medium 映射）。
 *
 * <h2>覆盖范围</h2>
 * 注入在 {@code PatternDualInputHatch} 上 ⇒ 自动覆盖 PH 总成 22069 / MK.II 22179 以及
 * **我们的 MK.III（32108，直接继承本类且未覆写 provideCrafting）**。按 {@code pattern.length} 迭代
 * 以兼容 MK.III 的 144 槽。
 *
 * <p>PH 为运行时可选依赖（compileOnly + required=false）；未装 PH 时本 mixin 不会被应用。
 */
@Mixin(value = PatternDualInputHatch.class, remap = false)
public abstract class MixinPatternDualInputHatchWildcard {

    /** PH 自己声明的 {@code public boolean isActive()}（javap 确证：PatternDualInputHatch 直接声明该方法）。 */
    @org.spongepowered.asm.mixin.Shadow
    public abstract boolean isActive();

    @Inject(method = "provideCrafting", at = @At("RETURN"), remap = false)
    private void ae2qol$registerWildcardExpansions(ICraftingProviderHelper craftingTracker, CallbackInfo ci) {
        try {
            final MixinPatternDualInputHatchAccess self = (MixinPatternDualInputHatchAccess) (Object) this;
            final ItemStack[] slots = self.getAe2qolPattern();
            if (slots == null || slots.length == 0) return;

            // 复现原方法的 isActive 守卫：未连网/无电时不注册（RETURN 注入对提前 return 也会触发）
            if (!this.isActive()) return;

            World world = null;
            if (this instanceof IMetaTileEntity mte && mte.getBaseMetaTileEntity() != null) {
                world = mte.getBaseMetaTileEntity()
                    .getWorld();
            }

            int wildcardSlots = 0;
            int registered = 0;
            int truncated = 0;
            for (ItemStack slot : slots) {
                if (slot == null || !SmartWildcardState.isSmartWildcard(slot)) continue;
                wildcardSlots++;
                SmartWildcardExpander.Result result = SmartWildcardExpander.expand(slot, world);
                if (result.truncated) truncated++;
                if (result.isEmpty()) {
                    // 空状态必须可解释（本项目原则）：把原因打进日志
                    MyMod.LOG.warn("[AE2QoL] PH 仓的智能通配样板未展开出任何样板：{}", result.describe());
                    continue;
                }
                for (ItemStack concrete : result.patterns) {
                    if (concrete == null || concrete.getItem() == null) continue;
                    if (!(concrete.getItem() instanceof ICraftingPatternItem patternItem)) continue;
                    ICraftingPatternDetails details = patternItem.getPatternForItem(concrete, world);
                    if (details == null) continue;
                    craftingTracker.addCraftingOption((ICraftingProvider) (Object) this, details);
                    registered++;
                }
            }
            if (wildcardSlots > 0) {
                MyMod.LOG.info(
                    "[AE2QoL] PH 通配样板注册：通配槽={} 注册 details={} 截断={} 上限={}",
                    wildcardSlots,
                    registered,
                    truncated,
                    Config.smartWildcardExpandCap);
            }
        } catch (Throwable t) {
            // 不静默、也不 cancel：本轮跳过，原版样板照常工作
            MyMod.LOG.warn("[AE2QoL] PH 通配样板注册失败（本轮跳过，不影响原版样板）", t);
        }
    }
}
