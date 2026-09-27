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

    /** 3.40.0：注册/诊断日志限频（初值 0，别用 Long.MIN_VALUE —— now-last 会溢出成负数）。 */
    @org.spongepowered.asm.mixin.Unique
    private long ae2qol$lastRegisterLogTick = 0L;

    // ================= 4.1.0：把样板里的电路 / 不消耗物品转换成 PH:编程器电路 =================

    @org.spongepowered.asm.mixin.Unique
    private long ae2qol$lastWrapLogTick = 0L;
    @org.spongepowered.asm.mixin.Unique
    private int ae2qol$lastWrapCount = -1;

    /**
     * **4.1.0 核心改动**：把一张具体样板输入里的「GT 编程电路」与「样板不消耗物品」换成
     * **PH:编程器电路**（{@code ItemProgrammingCircuit.wrap(目标)}）。
     *
     * <h2>为什么这样做（用户口径）</h2>
     * MK.III 就是 PH（可编程仓室）原版样板总成的**扩槽版**：界面与"编程器模式"都是 PH 自己的，
     * 我们一行都不用改；要改的只是"我们通配样板喂进去的东西"：
     * <ul>
     * <li>GT 编程电路 → 编程器电路（记录那号电路）：AE 会按样板去取编程器电路（PH 的
     * {@code ProgrammingCircuitProvider} 按需生成），送进 PH 家族总成后被 PH **自己识别并消耗**，
     * 把记录的目标写进该缓冲的虚拟槽 ⇒ **AE 不再需要备货 GT 电路，也不会在总成里残留电路**；</li>
     * <li>不消耗物品（铸模/模头/透镜…）→ 同样换成记录该物品的编程器电路，由 PH 写进虚拟槽；</li>
     * <li>样板自带电路号存在但输入里没有电路项时，**补一条**编程器电路（编辑器里设的电路照样生效）。</li>
     * </ul>
     * 只对 PH 家族宿主生效（本 mixin 只作用于 PH 的类）——GT 2714 / GTNL 21504 没有编程器模式，不参与。
     *
     * <h2>为什么先 copy</h2>
     * 展开结果带 LRU 缓存（{@code SmartWildcardExpander.CACHE}），原地改写会污染缓存 ⇒ 一律先
     * {@code copy()} 再改；异常时返回原对象并记日志（不静默）。
     */
    @org.spongepowered.asm.mixin.Unique
    private ItemStack ae2qol$wrapProgrammingCircuits(ItemStack concrete, ItemStack pattern) {
        try {
            SmartWildcardState state = SmartWildcardState.of(pattern);
            if (state == null) return concrete;
            java.util.List<ItemStack> nonConsumed = state.nonConsumed;
            boolean hasCircuitTarget = state.circuit >= 1;
            if (!hasCircuitTarget && (nonConsumed == null || nonConsumed.isEmpty())) return concrete;

            ItemStack copy = concrete.copy();
            net.minecraft.nbt.NBTTagCompound tag = copy.getTagCompound();
            if (tag == null) return concrete;
            net.minecraft.nbt.NBTTagList in = tag
                .getTagList("in", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
            net.minecraft.nbt.NBTTagList newIn = new net.minecraft.nbt.NBTTagList();
            boolean circuitWrapped = false;
            int nonConsumedWrapped = 0;
            for (int i = 0; i < in.tagCount(); i++) {
                net.minecraft.nbt.NBTTagCompound entry = (net.minecraft.nbt.NBTTagCompound) in.getCompoundTagAt(i)
                    .copy();
                ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
                if (stack != null && reobf.proghatches.item.ItemProgrammingCircuit.getCircuit(stack)
                    .isPresent()) {
                    // 已经是编程器电路（例如用户用 PH 编程工具箱转写过的样板）：原样保留
                    newIn.appendTag(entry);
                    continue;
                }
                if (ae2qol$isGtCircuit(stack)) {
                    newIn.appendTag(ae2qol$programmingCircuitTag(stack));
                    circuitWrapped = true;
                    continue;
                }
                if (ae2qol$isNonConsumed(stack, nonConsumed)) {
                    newIn.appendTag(ae2qol$programmingCircuitTag(stack));
                    nonConsumedWrapped++;
                    continue;
                }
                newIn.appendTag(entry);
            }
            // 样板自带电路号在、但输入里没有电路项 ⇒ 补一条编程器电路（记录该号 GT 电路）
            if (hasCircuitTarget && !circuitWrapped) {
                ItemStack target = gregtech.api.util.GTUtility.getIntegratedCircuit(state.circuit);
                if (target != null) {
                    newIn.appendTag(ae2qol$programmingCircuitTag(target));
                    circuitWrapped = true;
                }
            }
            // 不消耗物品里还有输入列表中没有的 ⇒ 也补编程器电路
            if (nonConsumed != null) {
                for (ItemStack item : nonConsumed) {
                    if (item == null || item.getItem() == null) continue;
                    if (ae2qol$inListContains(in, item)) continue;
                    newIn.appendTag(ae2qol$programmingCircuitTag(item));
                    nonConsumedWrapped++;
                }
            }
            if (!circuitWrapped && nonConsumedWrapped == 0) return concrete;
            tag.setTag("in", newIn);
            ae2qol$logWrap(circuitWrapped, nonConsumedWrapped);
            return copy;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 转换 PH 编程器电路失败（该张保持原样）", t);
            return concrete;
        }
    }

    /** GT 编程电路的识别口径与 {@code SmartWildcardCircuit} 一致：未本地化名以 {@code gt.integrated_circuit} 开头。 */
    @org.spongepowered.asm.mixin.Unique
    private static boolean ae2qol$isGtCircuit(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String unlocalized = stack.getItem()
            .getUnlocalizedName();
        return unlocalized != null && unlocalized.startsWith("gt.integrated_circuit");
    }

    /** 该输入项是否在"样板不消耗物品"清单里（按 物品+damage+NBT 指纹，忽略数量）。 */
    @org.spongepowered.asm.mixin.Unique
    private static boolean ae2qol$isNonConsumed(ItemStack stack, java.util.List<ItemStack> nonConsumed) {
        if (stack == null || nonConsumed == null) return false;
        for (ItemStack item : nonConsumed) {
            if (item == null || item.getItem() == null) continue;
            if (item.getItem() != stack.getItem()) continue;
            if (item.getItemDamage() != stack.getItemDamage()) continue;
            if (ItemStack.areItemStackTagsEqual(item, stack)) return true;
        }
        return false;
    }

    /** 输入列表里是否已经有这种物品（用于决定要不要补一条编程器电路）。 */
    @org.spongepowered.asm.mixin.Unique
    private static boolean ae2qol$inListContains(net.minecraft.nbt.NBTTagList in, ItemStack item) {
        for (int i = 0; i < in.tagCount(); i++) {
            ItemStack stack = ItemStack.loadItemStackFromNBT(in.getCompoundTagAt(i));
            if (stack == null || stack.getItem() == null) continue;
            if (stack.getItem() != item.getItem()) continue;
            if (stack.getItemDamage() != item.getItemDamage()) continue;
            if (ItemStack.areItemStackTagsEqual(stack, item)) return true;
        }
        return false;
    }

    /** 造一条"记录该目标"的编程器电路的 NBT 条目（写进样板的 in 列表）。 */
    @org.spongepowered.asm.mixin.Unique
    private static net.minecraft.nbt.NBTTagCompound ae2qol$programmingCircuitTag(ItemStack target) {
        ItemStack wrapped = reobf.proghatches.item.ItemProgrammingCircuit.wrap(target.copy());
        net.minecraft.nbt.NBTTagCompound slotTag = new net.minecraft.nbt.NBTTagCompound();
        wrapped.writeToNBT(slotTag);
        slotTag.setInteger("Count", 1);
        slotTag.setLong("Cnt", 1);
        return slotTag;
    }

    /** 限频日志（15 秒或变化时打一次）：换了多少条、其中电路几条。 */
    @org.spongepowered.asm.mixin.Unique
    private void ae2qol$logWrap(boolean circuitWrapped, int nonConsumedWrapped) {
        try {
            long now = System.currentTimeMillis();
            int total = (circuitWrapped ? 1 : 0) + nonConsumedWrapped;
            if (total == this.ae2qol$lastWrapCount && now - this.ae2qol$lastWrapLogTick < 15000L) return;
            this.ae2qol$lastWrapLogTick = now;
            this.ae2qol$lastWrapCount = total;
            MyMod.LOG.info(
                "[AE2QoL] 已把具体样板输入里的电路/不消耗物品转换为 PH:编程器电路：电路={} 不消耗={}（AE 将按需取编程器电路，PH 总成收到后写进虚拟槽）",
                circuitWrapped,
                nonConsumedWrapped);
        } catch (Throwable ignored) {
            // 日志失败不影响主流程
        }
    }

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
                // 4.1.0：**不再写机器全局电路槽**（用户口径：我们彻底不管电路）。
                // 电路改由 PH 的"编程器电路"机制处理：见下面 ae2qol$wrapProgrammingCircuits(...)。
                if (result.isEmpty()) {
                    // 空状态必须可解释（本项目原则）：把原因打进日志
                    MyMod.LOG.warn("[AE2QoL] PH 仓的智能通配样板未展开出任何样板：{}", result.describe());
                    continue;
                }
                for (ItemStack concrete : result.patterns) {
                    if (concrete == null || concrete.getItem() == null) continue;
                    if (!(concrete.getItem() instanceof ICraftingPatternItem patternItem)) continue;
                    // 4.1.0 **核心改动**：把这一张具体样板输入里的
                    //   ① GT 编程电路 ② 样板"不消耗物品"
                    // 换成 **PH:编程器电路（记录对应目标）**：这样 AE 会按样板去取编程器电路（由 PH 的
                    // 编程器电路提供器按需生成），送进 PH 家族总成（含我们的 MK.III）后由 PH 自己识别并
                    // 消耗掉、把记录的目标写进对应缓冲的虚拟槽 ⇒ 不再需要 AE 备货 GT 电路、也不会残留电路。
                    ItemStack baked = ae2qol$wrapProgrammingCircuits(concrete, slot);
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
