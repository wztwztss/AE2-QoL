package com.wztwzt.ae2_qof.wildcard;

import net.minecraft.item.ItemStack;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.GTUtility;

import com.wztwzt.ae2_qof.MyMod;

/**
 * 通配样板的「内置编程电路」写入器（3.22.0 M3）。
 *
 * <h2>为什么走机器的虚拟电路槽，而不是把电路放进样板输入</h2>
 * 用户方案（已确认）：电路按**样板槽/机器**设置，机器工作时自动读取；优先级
 * <b>样板自带 &gt; 槽位 &gt; 整机</b>。若同时把电路作为样板输入，GT 会从幽灵电路槽与样板输入各看到一份电路
 * ⇒ 配方匹配冲突。因此推导器已保证**样板模板里不含电路**（见 {@code SmartWildcardRecipeDeriver} 第 ③ 步）。
 *
 * <h2>为什么不用 GT 的 GhostCircuitItemStackHandler（这是被迫的，且理由要写清）</h2>
 * 该类的类型层次引用了 {@code com.gtnewhorizons.modularui.api.forge.IItemHandler}，而本仓 MUI2 是
 * compileOnly 且编译基线版本里没有这个包（编译期直接报「找不到 IItemHandler 的文件」）
 * ⇒ 服务端通用代码不能依赖它。改用 GT 自己声明、**只依赖 GT + MC** 的官方接口：
 * <ol>
 * <li>{@code IConfigurationCircuitSupport.getCircuitSlot()} / {@code allowSelectCircuit()} 定位电路槽；</li>
 * <li>{@code GTUtility.getIntegratedCircuit(int)} 造出电路物品（GT 官方生成方式，电路号在 itemDamage 上）；</li>
 * <li>写入 {@code getBaseMetaTileEntity().setInventorySlotContents(slot, stack)}（与玩家在幽灵槽里手选等效）。</li>
 * </ol>
 * 电路号上限是 {@code ItemIntegratedCircuit.MAX_CIRCUIT_NUMBER = 24}（不是 32）。
 *
 * <p>所有失败分支都记日志（本项目原则）；已经是目标值时不重复写（省掉无谓的方块更新）。
 */
public final class SmartWildcardCircuit {

    /** GT 的编程电路号上限（ItemIntegratedCircuit.MAX_CIRCUIT_NUMBER）。 */
    public static final int MAX_CIRCUIT = 24;

    private SmartWildcardCircuit() {}

    /** 读取该机器电路槽当前电路号；没有电路/不支持 → -1。 */
    public static int readMachineCircuit(IMetaTileEntity mte) {
        if (mte == null) return -1;
        try {
            if (!(mte instanceof gregtech.api.interfaces.IConfigurationCircuitSupport support)) return -1;
            if (!support.allowSelectCircuit()) return -1;
            if (mte.getBaseMetaTileEntity() == null) return -1;
            ItemStack stack = mte.getBaseMetaTileEntity()
                .getStackInSlot(support.getCircuitSlot());
            if (stack == null || stack.getItem() == null) return -1;
            String unlocalized = stack.getItem()
                .getUnlocalizedName();
            if (unlocalized == null || !unlocalized.startsWith("gt.integrated_circuit")) return -1;
            return stack.getItemDamage();
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 读取机器编程电路失败", t);
            return -1;
        }
    }

    /**
     * 把电路号写进指定机器的虚拟电路槽。
     *
     * @param mte     目标机器（GT 的 MTE；PH/GTNL 的样板仓同属 GT MTE 体系）
     * @param circuit 电路号（1~24）
     * @param where   诊断来源描述（例如 "GT 样板输入仓 slot=2"）
     * @return 是否生效（true 含“已经是这个值”）
     */
    public static boolean apply(IMetaTileEntity mte, int circuit, String where) {
        if (mte == null) {
            MyMod.LOG.warn("[AE2QoL] 写入编程电路失败：目标机器为空（{}）", where);
            return false;
        }
        if (circuit < 1 || circuit > MAX_CIRCUIT) {
            MyMod.LOG.warn("[AE2QoL] 编程电路号越界（应为 1~{}）：circuit={} @ {}", MAX_CIRCUIT, circuit, where);
            return false;
        }
        try {
            if (!(mte instanceof gregtech.api.interfaces.IConfigurationCircuitSupport support)) {
                MyMod.LOG.warn("[AE2QoL] 该机器不支持电路选择，无法写内置电路：{} @ {}", mte.getClass(), where);
                return false;
            }
            if (!support.allowSelectCircuit()) {
                MyMod.LOG.warn("[AE2QoL] 该机器当前不允许选择电路：{} @ {}", mte.getClass(), where);
                return false;
            }
            int current = readMachineCircuit(mte);
            if (current == circuit) return true; // 已是目标值：不重复写
            if (mte.getBaseMetaTileEntity() == null) {
                MyMod.LOG.warn("[AE2QoL] 写入编程电路失败：机器无方块实体（{}）", where);
                return false;
            }
            mte.getBaseMetaTileEntity()
                .setInventorySlotContents(support.getCircuitSlot(), GTUtility.getIntegratedCircuit(circuit));
            MyMod.LOG.info(
                "[AE2QoL] 通配样板内置电路已写入虚拟电路槽：circuit={}（原值 {}）@ {}",
                circuit,
                current,
                where);
            return true;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 写入编程电路异常：circuit=" + circuit + " @ " + where, t);
            return false;
        }
    }

    /**
     * 按「样板自带 &gt; 槽位 &gt; 整机」的优先级决定该用哪个电路号。
     *
     * @param patternCircuit 样板自带（{@code SmartWildcardState.circuit}，未设置传 -1）
     * @param slotCircuit    槽位设置（未设置传 -1）
     * @param machineCircuit 整机当前电路槽值（{@link #readMachineCircuit}，无电路传 -1）
     * @return 应生效的电路号；-1 表示三层都没设置（调用方**不要**动机器电路槽）
     */
    public static int resolve(int patternCircuit, int slotCircuit, int machineCircuit) {
        if (patternCircuit >= 1) return patternCircuit;
        if (slotCircuit >= 1) return slotCircuit;
        return machineCircuit >= 1 ? machineCircuit : -1;
    }
}
