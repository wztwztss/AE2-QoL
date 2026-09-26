package com.wztwzt.ae2_qof.wildcard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.wztwzt.ae2_qof.MyMod;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;

/**
 * GT 样板输入仓（{@code MTEHatchCraftingInputME}）的**通配槽位**：一个槽位对应 N 个展开出来的具体样板。
 *
 * <h2>① 为什么必须子类化（有据可查，不是偏好）</h2>
 * GT 的 {@code PatternSlot} 把 details 存成 {@code protected final ICraftingPatternDetails patternDetails}
 * （GT 源码 142-143），而 {@code patternDetailsPatternSlotMap} 是 {@code Map<details, 槽位>}（476-477）——
 * N 个展开 details 必须各自作为 key 指回**同一个槽位对象**，只能覆写它唯一的 details 出口
 * {@code getPatternDetails()}（247-250，被 provideCrafting:1229 / loadNBTData:843 / onPatternChange:1099 一致使用）。
 * 已否掉的替代方案与依据：
 * <ul>
 * <li>「一个 details 代表整组」：槽位构造期 details 由原生 in/out 唯一决定（156-159），其余候选永远匹配不到；</li>
 * <li>「只在 pushPattern 里换 details」：AE2 注册哪些 key 由 provideCrafting 决定（1237），我们无权改；</li>
 * <li>「把玩家槽位物品换成具体样板」：loadNBTData 末尾会把 {@code internalInventory[i].pattern} 同步回
 * {@code mInventory[i]}（857-860），会污染存档。</li>
 * </ul>
 *
 * <h2>② 为什么不反射清缓存</h2>
 * 参考实现反射清 {@code processingLogics}，而该字段在 GT 5.09.54.133 **已不存在**（全树零命中）⇒ 那段是永久空转的死代码。
 * GT 自带正确钩子：{@code ProcessingLogic.tryCachePossibleRecipesFromPattern} 开头
 * {@code if (!inv.shouldBeCached()) return true;}（ProcLogic:149-151）⇒ **覆写返回 false 即永不写缓存**，
 * 这同时解决「一槽挂 N details 时缓存只会留模板那一份」的问题（缓存 key 是槽位对象，GT ProcLogic:84）。
 *
 * <h2>③ 为什么不改 GT 的 patternItemCache / patternDetailCache</h2>
 * 那是 GT 自己的身份缓存（onPatternChange:1119-1122 写、provideCrafting:1099 读），
 * 我们写进去会被它的引用比较逻辑覆盖；展开结果只作为本类的私有列表存在，并按需交给
 * {@code patternDetailsPatternSlotMap}（由 mixin 负责），互不干扰。
 */
public class SmartWildcardPatternSlot extends MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> {

    /** 本槽展开出来的全部具体样板（顺序稳定，供反查与对账）。 */
    private final List<ICraftingPatternDetails> expanded = new ArrayList<>();
    /** 最近一次展开的可读摘要（空结果时进日志，符合本项目「空状态必须可解释」原则）。 */
    private String expandSummary = "";

    public SmartWildcardPatternSlot(ItemStack pattern, MTEHatchCraftingInputME parent) {
        super(pattern, parent);
    }

    /**
     * 由 GT 已有槽位包一个我们自己的槽位：**只搬运已推入的物品/流体**，不改 GT 的任何字段。
     *
     * <p>为什么必须搬：{@code pushPattern} 把 AE2 推来的东西写进槽位自己的
     * {@code itemInventory}/{@code fluidInventory}（GT:422-439），而机器读的是 {@code internalInventory[]}
     * 里那个槽位对象 ⇒ 换槽位时不搬运，读档后缓冲里已有的物品会“消失”。
     * 依据：{@code getItemInputs()}/{@code getFluidInputs()} 都是 {@code toArray} **拷贝**（GT:236-245），
     * 搬进子类自己的 list 不会与原槽位共享、不会重复计算。
     */
    public SmartWildcardPatternSlot(MTEHatchCraftingInputME.PatternSlot<MTEHatchCraftingInputME> original,
        MTEHatchCraftingInputME parent) {
        super(
            ((com.wztwzt.ae2_qof.mixin.gt.MixinPatternSlotAccess) (Object) original).getAe2qolSlotPattern(),
            parent);
        try {
            for (ItemStack item : original.getItemInputs()) {
                if (item != null) this.itemInventory.add(item);
            }
            net.minecraftforge.fluids.FluidStack[] fluids = original.getFluidInputs();
            if (fluids != null) {
                for (net.minecraftforge.fluids.FluidStack fluid : fluids) {
                    if (fluid != null) this.fluidInventory.add(fluid);
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 搬运 GT 槽位库存失败（该槽按空缓冲处理）", t);
        }
    }

    /**
     * 重新展开本槽（样板 NBT 变化、或读档重建时调用）。
     *
     * @return 展开出来的 details 列表（可能为空；为空时已写 WARN + 原因）
     */
    public List<ICraftingPatternDetails> rebuild(World world) {
        this.expanded.clear();
        try {
            SmartWildcardExpander.Result result = SmartWildcardExpander.expand(this.pattern, world);
            this.expandSummary = result.describe();
            for (ItemStack concrete : result.patterns) {
                if (concrete == null || concrete.getItem() == null) continue;
                if (!(concrete.getItem() instanceof ICraftingPatternItem) && concrete.getItem() != null) {
                    // 展开产物由本模组物品的原生解码路径转 details；不是 ICraftingPatternItem 说明产物被改坏了
                    MyMod.LOG.warn(
                        "[AE2QoL] GT 通配槽位展开产物不是编码样板，已跳过：{}",
                        concrete.getItem()
                            .getClass()
                            .getName());
                    continue;
                }
                ICraftingPatternDetails details =
                    ((ICraftingPatternItem) concrete.getItem()).getPatternForItem(concrete, world);
                if (details != null) this.expanded.add(details);
            }
            if (this.expanded.isEmpty()) {
                MyMod.LOG.warn("[AE2QoL] GT 通配槽位展开为空：{}", this.expandSummary);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] GT 通配槽位展开失败（该槽按模板单张处理）", t);
        }
        return this.expanded;
    }

    /** 展开结果（只读）。 */
    public List<ICraftingPatternDetails> expandedDetails() {
        return Collections.unmodifiableList(this.expanded);
    }

    /** 最近一次展开摘要（诊断用）。 */
    public String expandSummary() {
        return this.expandSummary;
    }

    /**
     * details 出口：有展开结果就给第一个（保证 GT 自己的非通配路径拿到非 null，避免 hasChanged 的 NPE，GT:195-200）；
     * 没有就退回父类（即模板本身）。
     */
    @Override
    public ICraftingPatternDetails getPatternDetails() {
        if (!this.expanded.isEmpty()) return this.expanded.get(0);
        return super.getPatternDetails();
    }

    /**
     * 永不进 GT 的配方缓存。
     *
     * <p>依据：{@code IDualInputInventoryWithPattern.shouldBeCached()} 是 default 方法，
     * {@code ProcessingLogic:149-151} 见到 false 就放行、不写 {@code dualInvWithPatternToRecipeCache}
     * ⇒ 既避免「只缓存模板那一份」的错配方，又**不需要任何反射**。
     */
    @Override
    public boolean shouldBeCached() {
        return false;
    }
}
