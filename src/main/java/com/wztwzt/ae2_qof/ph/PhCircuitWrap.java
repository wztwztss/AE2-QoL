package com.wztwzt.ae2_qof.ph;

import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardState;

/**
 * 3.25.0-fix20「适配 PH 编程工具箱」的共用工具（宿主侧，**软依赖**）。
 *
 * <h2>要解决的问题（用户实测）</h2>
 * GT-Not-Good（`gtnotgood`，含 FC Ultra Terminal 三合一终端）等**第三方样板终端**在把样板放进
 * PH 家族总成（ProgrammableHatches）时，样板输入里的 **GT 编程电路**不会被换成
 * **PH:编程器电路**（{@code ItemProgrammingCircuit}）⇒ PH 的"编程器模式"拿不到目标，
 * 表现为**无法自动添加编程电路**。
 *
 * <h2>做法（宿主侧改写，与 4.1.0 的通配样板路径互补）</h2>
 * 在 PH 仓**接收样板**那一刻（{@code PatternDualInputHatch.setInventorySlotContents}）把样板输入里的
 * GT 编程电路替换成"记录该电路"的编程器电路条目；AE 之后会按样板去取编程器电路
 * （由 PH 的 {@code ProgrammingCircuitProvider} 按需生成），送进总成后被 PH 自己识别并消耗，
 * 把目标写进对应缓冲的虚拟槽 ⇒ 不再需要 AE 备货 GT 电路。
 *
 * <h2>边界（刻意不做的事）</h2>
 * <ul>
 * <li>**只识别 GT 编程电路**（未本地化名以 {@code gt.integrated_circuit} 开头）。AE 样板里没有"不消耗物品"
 * 这个标记，第三方终端写进来的普通物品**无法判断**是否不消耗 ⇒ 那部分仍需玩家用 PH 编程工具箱手动转写；</li>
 * <li>**不动我们的通配样板**：它们走 4.1.0 的索引期展开路径（{@code MixinPatternDualInputHatchWildcard}）；</li>
 * <li>**不碰已经存在的样板**（不改读档结果）：只在样板**被放进 PH 仓**时改写，符合用户口径
 * "只要新插入的，不动旧样板"。</li>
 * </ul>
 *
 * <p>PH 是运行时可选依赖（compileOnly + mixin 配置 {@code required:false}）：未装 PH 时本类不会被加载，
 * 调用方（PH 专属混入）也不会被应用。
 */
public final class PhCircuitWrap {

    private PhCircuitWrap() {}

    /** 首次改写只打一条 INFO（避免刷屏）；记录最后一次改写条数便于诊断。 */
    private static boolean loggedOnce = false;

    /**
     * 把样板输入里的 GT 编程电路换成 PH:编程器电路。
     *
     * @param patternIn 即将被放进 PH 仓的样板（可为 null / 非样板物品）
     * @return **原对象**（无需改写，或发生任何异常时）或**改写后的副本**
     */
    public static ItemStack wrapPlainPattern(ItemStack patternIn) {
        try {
            if (patternIn == null || patternIn.getItem() == null) return patternIn;
            if (!(patternIn.getItem() instanceof appeng.api.implementations.ICraftingPatternItem)) {
                return patternIn;
            }
            // 我们的通配样板：交给 4.1.0 的索引期展开路径处理，这里绝不插手。
            // 用**纯判据** SmartWildcardState.isSmartWildcard（只读 NBT），刻意不用 SmartWildcardGate 的统一门
            // —— 后者在缺我们 NBT 时会**懒同步写入**，在"放样板"这条路径上产生玩家可见的副作用。
            if (SmartWildcardState.isSmartWildcard(patternIn)) {
                return patternIn;
            }
            NBTTagCompound tag = patternIn.getTagCompound();
            if (tag == null) return patternIn;
            NBTTagList in = tag.getTagList("in", Constants.NBT.TAG_COMPOUND);
            if (in.tagCount() <= 0) return patternIn;

            // 先扫一遍：有没有"需要被换掉"的 GT 电路？没有就原样返回（零分配、零副作用）
            boolean needsWrap = false;
            for (int i = 0; i < in.tagCount() && !needsWrap; i++) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(in.getCompoundTagAt(i));
                if (stack != null && isGtCircuit(stack)) {
                    needsWrap = true;
                }
            }
            if (!needsWrap) return patternIn;

            ItemStack copy = patternIn.copy();
            NBTTagCompound copyTag = copy.getTagCompound();
            if (copyTag == null) return patternIn;
            NBTTagList newIn = new NBTTagList();
            int wrapped = 0;
            for (int i = 0; i < in.tagCount(); i++) {
                NBTTagCompound entry = (NBTTagCompound) in.getCompoundTagAt(i)
                    .copy();
                ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
                if (stack != null && isProgrammingCircuit(stack)) {
                    // 已经是编程器电路（例如玩家用 PH 编程工具箱转写过）：原样保留，绝不二次包装
                    newIn.appendTag(entry);
                    continue;
                }
                if (stack != null && isGtCircuit(stack)) {
                    newIn.appendTag(programmingCircuitTag(stack));
                    wrapped++;
                    continue;
                }
                newIn.appendTag(entry);
            }
            if (wrapped <= 0) return patternIn;
            copyTag.setTag("in", newIn);
            logOnce(wrapped);
            return copy;
        } catch (Throwable t) {
            // 不静默：报错并原样放行（样板照常可用）
            MyMod.LOG.warn("[AE2QoL] PH 编程器电路改写失败（该张样板保持原样）", t);
            return patternIn;
        }
    }

    /** GT 编程电路的识别口径与 {@code SmartWildcardCircuit} 一致：未本地化名以 {@code gt.integrated_circuit} 开头。 */
    public static boolean isGtCircuit(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String unlocalized = stack.getItem()
            .getUnlocalizedName();
        return unlocalized != null && unlocalized.startsWith("gt.integrated_circuit");
    }

    /** 是否已经是 PH 的编程器电路（这类条目一律原样保留）。 */
    public static boolean isProgrammingCircuit(ItemStack stack) {
        if (stack == null) return false;
        try {
            Optional<?> circuit = reobf.proghatches.item.ItemProgrammingCircuit.getCircuit(stack);
            return circuit != null && circuit.isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 造一条"记录该目标"的编程器电路 NBT 条目（写进样板的 in 列表）。 */
    public static NBTTagCompound programmingCircuitTag(ItemStack target) {
        ItemStack wrapped = reobf.proghatches.item.ItemProgrammingCircuit.wrap(target.copy());
        NBTTagCompound slotTag = new NBTTagCompound();
        wrapped.writeToNBT(slotTag);
        slotTag.setInteger("Count", 1);
        slotTag.setLong("Cnt", 1);
        return slotTag;
    }

    private static void logOnce(int wrapped) {
        if (loggedOnce) return;
        loggedOnce = true;
        MyMod.LOG.info(
            "[AE2QoL] 已把放进 PH 仓的样板里的 GT 编程电路改写为 PH:编程器电路（本张改写 {} 条）——"
                + "AE 将按需取编程器电路，PH 总成收到后写进虚拟槽（此后同类改写不再重复记录）",
            wrapped);
    }
}
