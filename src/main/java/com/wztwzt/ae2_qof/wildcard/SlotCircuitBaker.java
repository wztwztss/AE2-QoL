package com.wztwzt.ae2_qof.wildcard;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.wztwzt.ae2_qof.MyMod;

import gregtech.api.util.GTUtility;

/**
 * 把「本格电路」烧进具体样板的 `in` 列表（4.0.0）。
 *
 * <h2>为什么这样做，而不是继续写机器的虚拟电路槽</h2>
 * 旧做法是在索引期把样板自带的电路号写进**机器唯一**的虚拟电路槽 ⇒ 同舱两张样板**后写者覆盖前者**
 * （用户实测："第一张是 1 号电路、第二张是 2 号电路，后放进去的会覆盖前面的"）。
 * 改成"把电路作为**该格样板自己的输入**"后，机器在 push 这一格时就从输入总线看到这一格的电路，
 * **不再有全局争抢**；而且这是 GTNH 原生"样板带电路"机制，GT / GTNL / PH 三族天然通用。
 *
 * <h2>两个必须守住的点</h2>
 * <ol>
 * <li><b>绝不改动传入的 ItemStack</b>：展开结果带 LRU 缓存（{@code SmartWildcardExpander.CACHE}），
 * 原地改写会让"换了本格电路再重建"读到被污染的对象 ⇒ 本方法一律**先 {@code copy()} 再改**；</li>
 * <li><b>电路号非法/为空时不折腾样板</b>：{@code circuit < 1} 直接原样返回，交给上层按"继承"处理。</li>
 * </ol>
 */
public final class SlotCircuitBaker {

    private SlotCircuitBaker() {}

    /**
     * 返回"带上该电路号"的样板副本；{@code circuit < 1} 或异常时返回原对象（不静默吞错，异常必记日志）。
     */
    public static ItemStack bake(ItemStack concrete, int circuit) {
        if (concrete == null || circuit < 1) return concrete;
        try {
            ItemStack copy = concrete.copy();
            NBTTagCompound tag = copy.getTagCompound();
            if (tag == null) {
                MyMod.LOG.warn("[AE2QoL] 具体样板没有 NBT，无法写入本格电路（已跳过）");
                return concrete;
            }
            ItemStack circuitStack = GTUtility.getIntegratedCircuit(circuit);
            if (circuitStack == null) {
                MyMod.LOG.warn("[AE2QoL] 构造电路物品失败（circuit={}），本格电路未写入", circuit);
                return concrete;
            }
            NBTTagList in = tag.getTagList("in", Constants.NBT.TAG_COMPOUND);
            NBTTagList newIn = new NBTTagList();
            boolean replaced = false;
            for (int i = 0; i < in.tagCount(); i++) {
                NBTTagCompound slot = (NBTTagCompound) in.getCompoundTagAt(i)
                    .copy();
                ItemStack stack = ItemStack.loadItemStackFromNBT(slot);
                if (!replaced && isCircuit(stack)) {
                    newIn.appendTag(circuitTag(circuitStack));
                    replaced = true;
                    continue;
                }
                newIn.appendTag(slot);
            }
            if (!replaced) {
                // 模板里本来没有电路（推导器刻意不带电路，见 SmartWildcardRecipeDeriver）⇒ 这里补一条，
                // 这一格的配方因此能按本格电路匹配，而其它格各带各自的电路，互不干扰。
                newIn.appendTag(circuitTag(circuitStack));
            }
            tag.setTag("in", newIn);
            return copy;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 写入本格电路失败（circuit=" + circuit + "，该张按原样使用）", t);
            return concrete;
        }
    }

    private static NBTTagCompound circuitTag(ItemStack circuitStack) {
        NBTTagCompound slot = new NBTTagCompound();
        circuitStack.writeToNBT(slot);
        slot.setInteger("Count", 1);
        slot.setLong("Cnt", 1);
        return slot;
    }

    /** GT 编程电路的识别口径与 {@code SmartWildcardCircuit.readMachineCircuit} 一致：未本地化名以 {@code gt.integrated_circuit} 开头。 */
    public static boolean isCircuit(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String unlocalized = stack.getItem()
            .getUnlocalizedName();
        return unlocalized != null && unlocalized.startsWith("gt.integrated_circuit");
    }
}
