package com.wztwzt.ae2_qof.wildcard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.science.gtnl.common.machine.hatch.SuperCraftingInputHatchME;
import com.wztwzt.ae2_qof.MyMod;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;

/**
 * GTNL 超级样板总成（{@code SuperCraftingInputHatchME}，MTE 21504/21505）的通配槽位（3.22.0）。
 *
 * <h2>与 GT 版的差异（都由 javap 查实，不是猜）</h2>
 * <ul>
 * <li>GTNL 的 {@code PatternSlot} 是它**自己复制的一份**（与 GT 的同类不同物），
 * {@code patternDetails} 是 {@code public final}、{@code itemInventory}/{@code fluidInventory} 也是 **public** ⇒ 子类可直接用；</li>
 * <li>它**没有**覆写 {@code shouldBeCached()} ⇒ 继承 GT 默认的 **true**（这是必须子类化的原因：配方缓存以**槽位对象**为 key，
 * 一槽挂 N 个 details 时只会缓存“模板那一份”，机器会用错配方）；</li>
 * <li>它提供了 {@code writeToNBT(NBTTagCompound)} ⇒ 包槽位时用「它的 NBT + 它的 NBT 构造器」恢复缓冲内容，
 * 不依赖任何字段可见性。</li>
 * </ul>
 */
public class SmartWildcardGtnlPatternSlot extends SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME> {

    private final List<ICraftingPatternDetails> expanded = new ArrayList<>();
    private String expandSummary = "";
    /** 4.0.0：本槽所在机器（不依赖父类字段名，构造时自己存一份）。 */
    private final SuperCraftingInputHatchME ae2qol$parent;
    /** 4.0.0：本槽下标（按格设置要用；由 mixin 包裹时登记）。 */
    private int ae2qol$slotIndex = -1;

    public void ae2qol$setSlotIndex(int index) {
        this.ae2qol$slotIndex = index;
    }

    /**
     * 取本格最终生效的电路号（4.0.0，口径与 GT 侧一致）：样板自带电路**自动填入本格**，
     * 本格值（含玩家手改）优先；都没有返回 -1（不烧电路，交给整机设置）。
     */
    private int ae2qol$resolveSlotCircuit() {
        try {
            int patternCircuit = -1;
            SmartWildcardState state = SmartWildcardState.of(this.pattern);
            if (state != null) patternCircuit = state.circuit;
            if (this.ae2qol$parent instanceof ISlotSettingsHolder holder) {
                SlotSettingsStore store = holder.ae2qol$slotSettings();
                store.autoFillCircuitFromPattern(this.ae2qol$slotIndex, patternCircuit);
                return store.effectiveCircuit(this.ae2qol$slotIndex, patternCircuit);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 读取本格电路（GTNL）失败（按继承处理）", t);
        }
        return -1;
    }

    public SmartWildcardGtnlPatternSlot(ItemStack pattern, SuperCraftingInputHatchME parent, int index) {
        super(pattern, parent, index);
        this.ae2qol$parent = parent;
        this.ae2qol$slotIndex = index;
    }

    /** 由 GTNL 已有槽位包一个：把它的 NBT 交给它的构造器，库存/流体原样恢复。 */
    public SmartWildcardGtnlPatternSlot(SuperCraftingInputHatchME.PatternSlot<SuperCraftingInputHatchME> original,
        SuperCraftingInputHatchME parent, int index) {
        super(
            ((com.wztwzt.ae2_qof.mixin.gt.MixinGtnlPatternSlotAccess) (Object) original).getAe2qolSlotPattern(),
            nbtOf(original),
            parent,
            index);
        this.ae2qol$parent = parent;
        this.ae2qol$slotIndex = index;
    }

    private static NBTTagCompound nbtOf(SuperCraftingInputHatchME.PatternSlot<?> original) {
        try {
            return original.writeToNBT(new NBTTagCompound());
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 读取 GTNL 槽位 NBT 失败（该槽按空缓冲处理）", t);
            return new NBTTagCompound();
        }
    }

    /** 重新展开本槽；返回展开出的 details（为空时已写 WARN + 原因）。 */
    public List<ICraftingPatternDetails> rebuild(World world) {
        this.expanded.clear();
        try {
            SmartWildcardExpander.Result result = SmartWildcardExpander.expand(this.pattern, world);
            this.expandSummary = result.describe();
            // 4.0.0：本格电路（样板自带自动填入本格；不再写机器全局电路槽）
            int effectiveCircuit = ae2qol$resolveSlotCircuit();
            for (ItemStack concrete : result.patterns) {
                if (concrete == null || concrete.getItem() == null) continue;
                if (!(concrete.getItem() instanceof ICraftingPatternItem)) continue;
                // 把本格电路烧进这一张的 in 列表（bake 内部先 copy，保护展开缓存）
                ItemStack baked = SlotCircuitBaker.bake(concrete, effectiveCircuit);
                ICraftingPatternDetails details = SmartWildcardDecoder.decode(baked, world);
                if (details != null) {
                    // 3.42.0-diag：配对打印（同上）
                    com.wztwzt.ae2_qof.wildcard.SmartWildcardDiag.logDecodePair("GTNL", baked, details);
                    this.expanded.add(details);
                }
            }
            if (this.expanded.isEmpty()) {
                MyMod.LOG.warn("[AE2QoL] GTNL 通配槽位展开为空：{}", this.expandSummary);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] GTNL 通配槽位展开失败（该槽按模板单张处理）", t);
        }
        return this.expanded;
    }

    public List<ICraftingPatternDetails> expandedDetails() {
        return Collections.unmodifiableList(this.expanded);
    }

    public String expandSummary() {
        return this.expandSummary;
    }

    @Override
    public ICraftingPatternDetails getPatternDetails() {
        if (!this.expanded.isEmpty()) return this.expanded.get(0);
        return super.getPatternDetails();
    }

    /** 关掉该槽的配方缓存（GTNL 未覆写 ⇒ 默认 true；consumer 见 GTNLProcessingLogic:155-176）。 */
    @Override
    public boolean shouldBeCached() {
        return false;
    }
}
