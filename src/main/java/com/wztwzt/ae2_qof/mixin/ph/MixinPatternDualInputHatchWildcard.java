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

    /**
     * 找这张样板在本机器的**样板格下标**（4.0.0 按格设置要用）。
     *
     * <p>先用引用相等（同一张样板被重复放置时不会串格），再退化为 {@code equals}；找不到返回 -1
     * （调用方会按"继承"处理，不烧电路）。失败记日志，不静默。
     */
    @org.spongepowered.asm.mixin.Unique
    private int ae2qol$slotIndexOf(ItemStack pattern) {
        try {
            ItemStack[] array = ((MixinPatternDualInputHatchAccess) (Object) this).getAe2qolPattern();
            if (array == null) return -1;
            for (int i = 0; i < array.length; i++) {
                if (array[i] == pattern) return i;
            }
            for (int i = 0; i < array.length; i++) {
                if (array[i] != null && array[i].equals(pattern)) return i;
            }
            MyMod.LOG.warn("[AE2QoL] PH 仓取样板下标失败（该格按继承处理电路）");
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] PH 仓取样板下标异常（该格按继承处理电路）", t);
        }
        return -1;
    }

    /** 3.40.0：注册/诊断日志限频（初值 0，别用 Long.MIN_VALUE —— now-last 会溢出成负数）。 */
    @org.spongepowered.asm.mixin.Unique
    private long ae2qol$lastRegisterLogTick = 0L;

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
            final java.util.List<ICraftingPatternDetails> allDetails = new java.util.ArrayList<>();
            for (ItemStack slot : slots) {
                // 3.34.0：判据改用统一门（物品实例 → 缺我们 NBT 时先懒同步 → 至少一条规则）。
                // 旧实现直接用 isSmartWildcard（要求已有我们的 NBT）且判否完全静默 —— 玩家只在 Wild 界面里
                // 配过的样板会被跳过，表现为"机器完全不动且日志无痕"（3.33.0 实测根因之一）。
                if (!com.wztwzt.ae2_qof.wildcard.SmartWildcardGate
                    .isConfiguredWildcard(slot, "PH 编程样板总成")) continue;
                wildcardSlots++;
                SmartWildcardExpander.Result result = SmartWildcardExpander.expand(slot, world);
                if (result.truncated) truncated++;
                // 4.0.0：**不再写机器全局电路槽**（旧 M3 行为会互相覆盖：同舱两张样板时后索引者覆盖前者）。
                // 改为按用户口径 —— 样板自带电路自动填入本格（这里只有样板这一层，故直接取本格设置；
                // 玩家在"格设置"弹窗里手改的值由 SlotSettingsStore.effectiveCircuit 优先返回），
                // 再把该号烧进这一格的每张具体样板 in 列表。
                int slotCircuit = -1;
                try {
                    if (((Object) this) instanceof com.wztwzt.ae2_qof.wildcard.ISlotSettingsHolder holder) {
                        com.wztwzt.ae2_qof.wildcard.SlotSettingsStore store = holder.ae2qol$slotSettings();
                        int patternCircuit = -1;
                        SmartWildcardState wildcardState = SmartWildcardState.of(slot);
                        if (wildcardState != null) patternCircuit = wildcardState.circuit;
                        // PH 的一次 provideCrafting 会遍历全部样板格；这里用"该样板自己的下标"作为格号，
                        // 取不到时退回 -1（不烧电路，交回整机设置）。
                        int slotIndex = ae2qol$slotIndexOf(slot);
                        store.autoFillCircuitFromPattern(slotIndex, patternCircuit);
                        slotCircuit = store.effectiveCircuit(slotIndex, patternCircuit);
                    }
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 读取 PH 仓本格电路失败（按继承处理）", t);
                }
                if (result.isEmpty()) {
                    // 空状态必须可解释（本项目原则）：把原因打进日志
                    MyMod.LOG.warn("[AE2QoL] PH 仓的智能通配样板未展开出任何样板：{}", result.describe());
                    continue;
                }
                for (ItemStack concrete : result.patterns) {
                    if (concrete == null || concrete.getItem() == null) continue;
                    if (!(concrete.getItem() instanceof ICraftingPatternItem patternItem)) continue;
                    // 把本格电路烧进这一张的 in 列表（bake 内部先 copy，保护展开缓存）
                    ItemStack baked = com.wztwzt.ae2_qof.wildcard.SlotCircuitBaker.bake(concrete, slotCircuit);
                    ICraftingPatternDetails details = com.wztwzt.ae2_qof.wildcard.SmartWildcardDecoder
                        .decode(baked, world);
                    if (details == null) continue;
                    // 3.42.0-diag：配对打印（concrete 的 out / details 自带 pattern 的 out / details.getOutputs()[0]）
                    com.wztwzt.ae2_qof.wildcard.SmartWildcardDiag.logDecodePair("PH", baked, details);
                    craftingTracker.addCraftingOption((ICraftingProvider) (Object) this, details);
                    allDetails.add(details);
                    registered++;
                }
            }
            if (wildcardSlots > 0) {
                // 3.39.0-diag（3.40.0 起只在限频日志触发时才做）：注册后回读 AE2 合成表 + 输出种类数
                long now = System.currentTimeMillis();
                if (now - this.ae2qol$lastRegisterLogTick > 15000L) {
                    this.ae2qol$lastRegisterLogTick = now;
                    com.wztwzt.ae2_qof.wildcard.SmartWildcardDiag
                        .scheduleAeReadBack((ICraftingProvider) (Object) this, allDetails, "PH 通配样板");
                    MyMod.LOG.info(
                        "[AE2QoL] PH 通配样板注册（只追加，未 cancel）：通配槽={} 注册 details={} 截断={} 上限={} {}",
                        wildcardSlots,
                        registered,
                        truncated,
                        Config.smartWildcardExpandCap,
                        com.wztwzt.ae2_qof.wildcard.SmartWildcardDiag.describe(allDetails, 3));
                }
            }
        } catch (Throwable t) {
            // 不静默、也不 cancel：本轮跳过，原版样板照常工作
            MyMod.LOG.warn("[AE2QoL] PH 通配样板注册失败（本轮跳过，不影响原版样板）", t);
        }
    }
}
