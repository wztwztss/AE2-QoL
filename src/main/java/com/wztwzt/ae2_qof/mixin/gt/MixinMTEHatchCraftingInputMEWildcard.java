package com.wztwzt.ae2_qof.mixin.gt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardPatternSlot;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardState;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;

/**
 * GT 样板输入仓（{@code MTEHatchCraftingInputME}，MTE 2714/2715）的**索引期展开接管**（3.22.0）。
 *
 * <h2>为什么需要四处注入</h2>
 * <ol>
 * <li>{@code provideCrafting} HEAD + cancel：AE2 网格重建时逐个 provider 收样板（{@code CraftingGridCache.updatePatterns}
 * 先全量 clear 再重填）。通配槽要展开成 N 张具体样板逐个注册，并同步写 {@code patternDetailsPatternSlotMap}
 * （{@code pushPattern} 的消费处**不判空**，GT:1258-1259 ⇒ 缺 key 就是 NPE）。</li>
 * <li>{@code pushPattern} HEAD：兜底守卫。若某个 details 不在映射里，GT 会直接 NPE 并穿透到 CPU tick
 * （{@code CraftingCPUCluster.executeCrafting} 的调用点不在 try 内）⇒ 我们提前返回 false 并记 WARN。
 * 命中时**放行原方法**，让 GT 自己完成 isActive / isAllowedToWork / MEInventoryCrafting 校验、
 * {@code insertItemsAndFluids} 与 {@code justHadNewItems = true}（GT:1243-1263）。</li>
 * <li>{@code onPatternChange} RETURN：玩家换样板后 GT 会重建槽位并只写一条映射（GT:1097-1101）⇒ 重包 + 重注册。</li>
 * <li>{@code loadNBTData} RETURN：读档/区块重载后 GT 会 {@code clear()} 整个映射、只写回模板 key（GT:841-846）⇒ 同上。
 * 漏掉这一处表现为“放进去能用、重载后就不接单”，且当场无报错。</li>
 * </ol>
 *
 * <h2>刻意的取舍</h2>
 * <ul>
 * <li><b>两条注册路径不重复</b>：cancel 成功时由我们自己注册**全部**槽位（含非通配槽），失败则放行原方法并已记 WARN；
 * 收集阶段（可能抛异常的展开/解码）在整个注册动作**之前**完成，避免半途失败留下半截状态。</li>
 * <li><b>索引读取阶段只读</b>：不在 {@code provideCrafting} 里改 {@code internalInventory}（参考实现这么干了，
 * 会让“本轮遍历看到什么”取决于顺序）。</li>
 * <li><b>绝不改写槽位的 {@code pattern} 字段</b>：GT 在 {@code loadNBTData} 末尾会把它同步回 {@code mInventory[i]}
 * （GT:857-860），改了就是污染玩家槽位。</li>
 * </ul>
 */
@Mixin(value = MTEHatchCraftingInputME.class, remap = false)
public abstract class MixinMTEHatchCraftingInputMEWildcard {

    @Shadow
    @Final
    private MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME>[] internalInventory;

    @Shadow
    @Final
    private Map<ICraftingPatternDetails, MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME>> patternDetailsPatternSlotMap;

    /** javap 确证：该方法由 {@code MTEHatchCraftingInputME} 自己声明（父类均无）。 */
    @Shadow
    public abstract boolean isActive();

    /** 槽位下标 → 我们包装过的通配槽位（避免每轮重建；样板变化/读档时重包）。 */
    @Unique
    private final Map<Integer, SmartWildcardPatternSlot> ae2qol$wildcards = new HashMap<>();

    /** 日志限频：provideCrafting 在网格重建热路径上，不能每次都刷屏。 */
    @Unique
    private long ae2qol$lastRegisterLogTick = Long.MIN_VALUE;

    // ================= ① 索引期展开与注册 =================

    @Inject(method = "provideCrafting", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2qol$provideCrafting(ICraftingProviderHelper craftingTracker, CallbackInfo ci) {
        if (this.internalInventory == null || this.patternDetailsPatternSlotMap == null) return; // 交给原方法
        if (!this.isActive()) {
            // 复现原方法守卫（GT:1225）：未激活时不注册任何东西
            ci.cancel();
            return;
        }
        try {
            // 阶段一：只收集，不做任何注册（展开/解码可能抛异常，避免半截状态）
            final List<MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME>> plainSlots = new ArrayList<>();
            final List<MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME>> wildcardSlots = new ArrayList<>();
            for (int i = 0; i < this.internalInventory.length; i++) {
                MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.internalInventory[i];
                if (slot == null) {
                    this.ae2qol$wildcards.remove(i);
                    continue;
                }
                if (slot instanceof SmartWildcardPatternSlot) {
                    wildcardSlots.add(slot);
                    continue;
                }
                if (this.ae2qol$isWildcardItem(slot)) {
                    // 包一层（搬运库存），并替换进数组 —— 只在这里替换，且替换后仍是同一槽位语义
                    SmartWildcardPatternSlot wrapped = new SmartWildcardPatternSlot(slot, (MTEHatchCraftingInputME) (Object) this);
                    this.internalInventory[i] = wrapped;
                    this.ae2qol$wildcards.put(i, wrapped);
                    wildcardSlots.add(wrapped);
                    MyMod.LOG.info("[AE2QoL] GT 样板仓发现通配样板并展开：slot={} {}", i, wrapped.expandSummary());
                    // M3：样板自带电路 → 写入本机虚拟电路槽（样板自带 > 槽位 > 整机；没有设置就**不动**机器）
                    this.ae2qol$applyPatternCircuit(wrapped, i);
                    continue;
                }
                plainSlots.add(slot);
            }

            // 阶段二：注册（非通配槽沿用原逻辑；通配槽逐个注册展开产物）
            int registered = 0;
            for (MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot : plainSlots) {
                ICraftingPatternDetails details = slot.getPatternDetails();
                if (details == null) {
                    MyMod.LOG.warn("[AE2QoL] GT 样板仓存在无法解析的样板槽（原版同样会告警）");
                    continue;
                }
                craftingTracker.addCraftingOption((ICraftingProvider) (Object) this, details);
            }
            for (MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot : wildcardSlots) {
                SmartWildcardPatternSlot wildcardSlot = (SmartWildcardPatternSlot) slot;
                for (ICraftingPatternDetails details : wildcardSlot.expandedDetails()) {
                    // 映射必须逐条写：pushPattern 反查靠它，缺 key 会 NPE（GT:1258-1259）
                    this.patternDetailsPatternSlotMap.put(details, wildcardSlot);
                    craftingTracker.addCraftingOption((ICraftingProvider) (Object) this, details);
                    registered++;
                }
                if (wildcardSlot.expandedDetails()
                    .isEmpty()) {
                    MyMod.LOG.warn("[AE2QoL] GT 通配槽位未注册任何样板：{}", wildcardSlot.expandSummary());
                }
            }
            if (!wildcardSlots.isEmpty()) {
                long now = System.currentTimeMillis();
                if (now - this.ae2qol$lastRegisterLogTick > 15000L) {
                    this.ae2qol$lastRegisterLogTick = now;
                    MyMod.LOG.info(
                        "[AE2QoL] GT 通配样板注册：通配槽={} 注册 details={} 映射总数={} 上限={}",
                        wildcardSlots.size(),
                        registered,
                        this.patternDetailsPatternSlotMap.size(),
                        Config.smartWildcardExpandCap);
                }
            }
            ci.cancel();
        } catch (Throwable t) {
            // 不 cancel：放行原方法（退化为“只注册模板那一张”），但绝不静默
            MyMod.LOG.warn("[AE2QoL] GT 通配样板注册失败，已回退原版 provideCrafting（只注册模板样板）", t);
        }
    }

    // ================= ② pushPattern 兜底守卫（防 GT 的 NPE） =================

    @Inject(method = "pushPattern", at = @At("HEAD"), remap = false)
    private void ae2qol$guardPushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table,
        CallbackInfoReturnable<Boolean> cir) {
        try {
            if (patternDetails == null) return;
            if (this.patternDetailsPatternSlotMap.get(patternDetails) != null) return; // 命中：放行原方法
            ItemStack pattern = null;
            try {
                pattern = patternDetails.getPattern();
            } catch (Throwable ignored) {
                // 取不到样板物品不影响结论，下面的日志里会体现为 unknown
            }
            MyMod.LOG.warn(
                "[AE2QoL] GT 样板仓 pushPattern 反查失败：details 不在映射中（pattern={}），已返回 false 以避免 GT 的 NPE",
                pattern == null ? "unknown" : String.valueOf(pattern.getItem()));
            cir.setReturnValue(false);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] pushPattern 反查检查异常（放行原方法）", t);
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

    /** 把某个槽位按需包成通配槽位，并把它的展开 details 逐条写进映射（幂等）。 */
    @Unique
    private void ae2qol$wrapSlot(int index) {
        try {
            if (this.internalInventory == null || index < 0 || index >= this.internalInventory.length) return;
            MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot = this.internalInventory[index];
            if (slot == null) {
                this.ae2qol$wildcards.remove(index);
                return;
            }
            if (slot instanceof SmartWildcardPatternSlot wildcardSlot) {
                for (ICraftingPatternDetails details : wildcardSlot.expandedDetails()) {
                    this.patternDetailsPatternSlotMap.put(details, wildcardSlot);
                }
                return;
            }
            if (!this.ae2qol$isWildcardItem(slot)) {
                this.ae2qol$wildcards.remove(index);
                return;
            }
            SmartWildcardPatternSlot wrapped = new SmartWildcardPatternSlot(slot, (MTEHatchCraftingInputME) (Object) this);
            this.internalInventory[index] = wrapped;
            this.ae2qol$wildcards.put(index, wrapped);
            for (ICraftingPatternDetails details : wrapped.expandedDetails()) {
                this.patternDetailsPatternSlotMap.put(details, wrapped);
            }
            MyMod.LOG.info(
                "[AE2QoL] GT 通配样板槽位已重建：slot={} details={} {}",
                index,
                wrapped.expandedDetails()
                    .size(),
                wrapped.expandSummary());
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 重建 GT 通配槽位失败：slot=" + index, t);
        }
    }

    /**
     * M3：把**样板自带的编程电路**写进本机虚拟电路槽。
     * 口径（用户确认）：优先级 样板自带 &gt; 槽位 &gt; 整机；槽位层尚未实现（传 -1）
     * ⇒ 这里只在“样板确实自带电路”时动手，否则**绝不动**机器原有电路（整机层由玩家自己设置）。
     */
    @Unique
    private void ae2qol$applyPatternCircuit(MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot,
        int index) {
        try {
            ItemStack pattern = ((MixinPatternSlotAccess) (Object) slot).getAe2qolSlotPattern();
            SmartWildcardState state = SmartWildcardState.of(pattern);
            if (state == null || state.circuit < 1) return;
            gregtech.api.interfaces.metatileentity.IMetaTileEntity mte =
                (gregtech.api.interfaces.metatileentity.IMetaTileEntity) (Object) this;
            int target = com.wztwzt.ae2_qof.wildcard.SmartWildcardCircuit
                .resolve(state.circuit, -1, com.wztwzt.ae2_qof.wildcard.SmartWildcardCircuit.readMachineCircuit(mte));
            if (target >= 1) {
                com.wztwzt.ae2_qof.wildcard.SmartWildcardCircuit
                    .apply(mte, target, "GT 样板输入仓 slot=" + index);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 写入 GT 仓内置电路失败：slot=" + index, t);
        }
    }

    /** 该槽位的样板物品是不是我们的通配样板（经内部类 accessor 读取，只读不改）。 */
    @Unique
    private boolean ae2qol$isWildcardItem(MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> slot) {        try {
            ItemStack pattern = ((MixinPatternSlotAccess) (Object) slot).getAe2qolSlotPattern();
            return pattern != null && pattern.getItem() != null && SmartWildcardState.isSmartWildcard(pattern);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 读取 GT 槽位样板失败（按非通配处理）", t);
            return false;
        }
    }
}
