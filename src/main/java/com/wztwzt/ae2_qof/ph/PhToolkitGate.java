package com.wztwzt.ae2_qof.ph;

import java.lang.reflect.Method;

import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;

/**
 * 3.25.0-fix22：PH「编程样板工具箱」的两个**客户端专用**判据的反射闸门（软依赖 + 不静默）。
 *
 * <h2>为什么必须反射 + 只能在客户端查</h2>
 * PH 的 {@code reobf.proghatches.item.ItemProgrammingToolkit} 里：
 * <pre>
 * L82: {@code @SideOnly(Side.CLIENT) public static boolean holding()            { return Math.abs(lastholdingtick - MyMod.ticker) <= 10; }}
 * L88: {@code @SideOnly(Side.CLIENT) public static boolean addEmptyProgCiruit() { return mode == 2; }}
 * </pre>
 * ⇒ 两个方法都带 {@code @SideOnly(CLIENT)}（专用服务端上**类/方法不存在**），
 * 所以：① 只能反射调用；② 只在客户端路径（NEI 转写）里用；③ 调用失败一律**当作"没开工具箱"**并记一次日志，
 * 绝不因为 PH 缺失/改版而把功能或游戏搞挂。
 *
 * <h2>语义（与 PH 自家终端一致）</h2>
 * <ul>
 * <li>{@link #holding()} = 工具箱在**最近 10 tick 内被激活/使用过**（不是"背包里放着"）；</li>
 * <li>{@link #addEmptyProgCircuit()} = 工具箱处于**兜底模式**（{@code mode == 2}）⇒ 配方没有电路时补一块归零电路。</li>
 * </ul>
 */
public final class PhToolkitGate {

    private PhToolkitGate() {}

    private static final String TOOLKIT_CLASS = "reobf.proghatches.item.ItemProgrammingToolkit";

    private static boolean initDone = false;
    private static boolean available = false;
    private static Method mHolding;
    private static Method mAddEmpty;
    private static boolean loggedMissing = false;

    private static synchronized void init() {
        if (initDone) return;
        initDone = true;
        try {
            Class<?> toolkit = Class.forName(TOOLKIT_CLASS, false, PhToolkitGate.class.getClassLoader());
            mHolding = toolkit.getMethod("holding");
            mAddEmpty = toolkit.getMethod("addEmptyProgCiruit");
            available = true;
        } catch (Throwable t) {
            available = false;
            MyMod.LOG.info(
                "[AE2QoL] 未检测到 PH 编程样板工具箱（{}）⇒ 转写时不做编程器电路注入（这不是错误，装了 PH 就会生效）",
                TOOLKIT_CLASS);
        }
    }

    /** 工具箱是否"刚被激活/手持"（PH 的 10 tick 语义）；取不到 ⇒ false。 */
    public static boolean holding() {
        init();
        if (!available) return false;
        try {
            Object r = mHolding.invoke(null);
            return r instanceof Boolean b && b;
        } catch (Throwable t) {
            warnOnce(t);
            return false;
        }
    }

    /** 工具箱是否处于兜底模式（mode == 2）；取不到 ⇒ false。 */
    public static boolean addEmptyProgCircuit() {
        init();
        if (!available) return false;
        try {
            Object r = mAddEmpty.invoke(null);
            return r instanceof Boolean b && b;
        } catch (Throwable t) {
            warnOnce(t);
            return false;
        }
    }

    /** 造一块"记录该目标"的编程器电路物品（{@code target == null} ⇒ 归零电路，PH 的兜底用法）。 */
    public static ItemStack wrapAsProgrammingCircuit(ItemStack target) {
        ItemStack copy = target == null ? null : target.copy();
        return PhCircuitWrap.wrapAsProgrammingCircuit(copy);
    }

    private static void warnOnce(Throwable t) {
        if (loggedMissing) return;
        loggedMissing = true;
        // 不静默：PH 在但判据调用失败 ⇒ 记一次，便于排查（每次转写都刷屏没有意义）
        MyMod.LOG.warn("[AE2QoL] 查询 PH 编程样板工具箱状态失败（本次按「未激活」处理）", t);
    }
}
