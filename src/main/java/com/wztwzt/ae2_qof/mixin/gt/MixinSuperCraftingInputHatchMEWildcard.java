package com.wztwzt.ae2_qof.mixin.gt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME;
import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardGtnlPatternSlot;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardState;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;

/**
 * GTNL 超级样板总成（{@code SuperCraftingInputHatchME}，MTE 21504/21505 —— **同一个类**，两处一处注入即够）
 * 的索引期展开接管（3.22.0）。
 *
 * <h2>为什么必须处理，且比 GT 更凶</h2>
 * GTNL 的 {@code pushPattern} 是 {@code patternDetailsPatternSlotMap.get(details).insertItemsAndFluids(table)}，
 * **没有任何判空**（GTNL:957-958）⇒ 某个展开 details 没写进映射就是 **NPE**，而且调用点
 * {@code CraftingCPUCluster.executeCrafting} 不在 try 内 ⇒ 异常穿透到 CPU tick（表现为服务器 tick 崩，不是“没反应”）。
 * 所以本类第二处注入是**保命**用的：宁可返回 false + WARN，也绝不让它 NPE。
 *
 * <h2>四处注入</h2>
 * <ol>
 * <li>{@code provideCrafting} HEAD + cancel：通配槽展开成 N 张逐个注册 + 逐条写映射（与 GT 版同构，两阶段先收集后注册）；</li>
 * <li>{@code pushPattern} HEAD：反查缺失时 WARN + 返回 false（防 NPE）；命中则放行原方法；</li>
 * <li>{@code onPatternChange} RETURN：GTNL 在此重建槽位并只写一条映射（:825/:827）⇒ 重包 + 重注册；</li>
 * <li>{@code loadNBTData} RETURN：GTNL 在此 {@code clear()} 整个映射后按槽重建（:500-506）⇒ 同上。</li>
 * </ol>
 *
 * <p>per-slot 记账一律用**数组下标**：GTNL 的 {@code loadNBTData} 有既有缺陷（:475/:479 用 NBT 列表序号当 slotIndex），
 * 用 {@code slot.slotIndex} 会错位。
 */
@Mixin(value = SuperCraftingInputHatchME.class, remap = false)
public abstract class MixinSuperCraftingInputHatchMEWildcard {

    /** public 字段（javap 查实，非 final）。 */
    @Shadow
    public SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME>[] internalInventory;

    @Shadow
    public Map<ICraftingPatternDetails, SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME>> patternDetailsPatternSlotMap;

    @Shadow
    public abstract boolean isActive();

    @Unique
    private final Map<Integer, SmartWildcardGtnlPatternSlot> ae2qol$wildcards = new HashMap<>();

    @Unique
    private long ae2qol$lastRegisterLogTick = Long.MIN_VALUE;

    // ================= ① 索引期展开与注册 =================

    @Inject(method = "provideCrafting", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2qol$provideCrafting(ICraftingProviderHelper craftingTracker, CallbackInfo ci) {
        if (this.internalInventory == null || this.patternDetailsPatternSlotMap == null) return;
        if (!this.isActive()) {
            ci.cancel();
            return;
        }
        try {
            final List<SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME>> plainSlots = new ArrayList<>();
            final List<SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME>> wildcardSlots = new ArrayList<>();
            for (int i = 0; i < this.internalInventory.length; i++) {
                SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME> slot = this.internalInventory[i];
                if (slot == null) {
                    this.ae2qol$wildcards.remove(i);
                    continue;
                }
                if (slot instanceof SmartWildcardGtnlPatternSlot) {
                    wildcardSlots.add(slot);
                    continue;
                }
                if (this.ae2qol$isWildcardItem(slot)) {
                    SmartWildcardGtnlPatternSlot wrapped =
                        new SmartWildcardGtnlPatternSlot(slot, (SuperCraftingInputHatchME) (Object) this, i);
                    this.internalInventory[i] = wrapped;
                    this.ae2qol$wildcards.put(i, wrapped);
                    wildcardSlots.add(wrapped);
                    MyMod.LOG.info("[AE2QoL] GTNL 样板总成发现通配样板并展开：slot={} {}", i, wrapped.expandSummary());
                    continue;
                }
                plainSlots.add(slot);
            }

            int registered = 0;
            for (SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME> slot : plainSlots) {
                ICraftingPatternDetails details = slot.getPatternDetails();
                if (details == null) continue; // GTNL 自己会打 warn，这里不重复
                craftingTracker.addCraftingOption((ICraftingProvider) (Object) this, details);
            }
            for (SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME> slot : wildcardSlots) {
                SmartWildcardGtnlPatternSlot wildcardSlot = (SmartWildcardGtnlPatternSlot) slot;
                for (ICraftingPatternDetails details : wildcardSlot.expandedDetails()) {
                    // 逐条写映射：未命中会 NPE（GTNL:957-958）
                    this.patternDetailsPatternSlotMap.put(details, wildcardSlot);
                    craftingTracker.addCraftingOption((ICraftingProvider) (Object) this, details);
                    registered++;
                }
                if (wildcardSlot.expandedDetails()
                    .isEmpty()) {
                    MyMod.LOG.warn("[AE2QoL] GTNL 通配槽位未注册任何样板：{}", wildcardSlot.expandSummary());
                }
            }
            if (!wildcardSlots.isEmpty()) {
                long now = System.currentTimeMillis();
                if (now - this.ae2qol$lastRegisterLogTick > 15000L) {
                    this.ae2qol$lastRegisterLogTick = now;
                    MyMod.LOG.info(
                        "[AE2QoL] GTNL 通配样板注册：通配槽={} 注册 details={} 映射总数={} 上限={}",
                        wildcardSlots.size(),
                        registered,
                        this.patternDetailsPatternSlotMap.size(),
                        Config.smartWildcardExpandCap);
                }
            }
            ci.cancel();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] GTNL 通配样板注册失败，已回退原版 provideCrafting（只注册模板样板）", t);
        }
    }

    // ================= ② pushPattern 保命守卫 =================

    @Inject(method = "pushPattern", at = @At("HEAD"), remap = false)
    private void ae2qol$guardPushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table,
        CallbackInfoReturnable<Boolean> cir) {
        try {
            if (patternDetails == null) return;
            if (this.patternDetailsPatternSlotMap.get(patternDetails) != null) return;
            MyMod.LOG.warn("[AE2QoL] GTNL pushPattern 反查失败：details 不在映射中，已返回 false（否则 GTNL 会 NPE 并打崩 CPU tick）");
            cir.setReturnValue(false);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] GTNL pushPattern 反查检查异常（放行原方法）", t);
        }
    }

    // ================= ③④ 槽位重建后重包与重注册 =================

    @Inject(method = "onPatternChange", at = @At("RETURN"), remap = false)
    private void ae2qol$afterPatternChange(int index, ItemStack newItem, CallbackInfo ci) {
        this.ae2qol$wrapSlot(index);
    }

    @Inject(method = "loadNBTData", at = @At("RETURN"), remap = false)
    private void ae2qol$afterLoadNBT(NBTTagCompound nbt, CallbackInfo ci) {
        if (this.internalInventory == null) return;
        for (int i = 0; i < this.internalInventory.length; i++) {
            this.ae2qol$wrapSlot(i);
        }
    }

    @Unique
    private void ae2qol$wrapSlot(int index) {
        try {
            if (this.internalInventory == null || index < 0 || index >= this.internalInventory.length) return;
            SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME> slot = this.internalInventory[index];
            if (slot == null) {
                this.ae2qol$wildcards.remove(index);
                return;
            }
            if (slot instanceof SmartWildcardGtnlPatternSlot wildcardSlot) {
                for (ICraftingPatternDetails details : wildcardSlot.expandedDetails()) {
                    this.patternDetailsPatternSlotMap.put(details, wildcardSlot);
                }
                return;
            }
            if (!this.ae2qol$isWildcardItem(slot)) {
                this.ae2qol$wildcards.remove(index);
                return;
            }
            SmartWildcardGtnlPatternSlot wrapped =
                new SmartWildcardGtnlPatternSlot(slot, (SuperCraftingInputHatchME) (Object) this, index);
            this.internalInventory[index] = wrapped;
            this.ae2qol$wildcards.put(index, wrapped);
            for (ICraftingPatternDetails details : wrapped.expandedDetails()) {
                this.patternDetailsPatternSlotMap.put(details, wrapped);
            }
            MyMod.LOG.info(
                "[AE2QoL] GTNL 通配样板槽位已重建：slot={} details={} {}",
                index,
                wrapped.expandedDetails()
                    .size(),
                wrapped.expandSummary());
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 重建 GTNL 通配槽位失败：slot=" + index, t);
        }
    }

    @Unique
    private boolean ae2qol$isWildcardItem(SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME> slot) {
        try {
            ItemStack pattern = ((MixinGtnlPatternSlotAccess) (Object) slot).getAe2qolSlotPattern();
            return pattern != null && pattern.getItem() != null && SmartWildcardState.isSmartWildcard(pattern);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 读取 GTNL 槽位样板失败（按非通配处理）", t);
            return false;
        }
    }
}
