package com.wztwzt.ae2_qof.compat;

import com.wztwzt.ae2_qof.MyMod;

/**
 * TenkeiTech 自带模板输入总线的可选集成探测。
 *
 * <p>
 * 目标机器是 {@code com.tenkeitech.machine.MTEHatchAutoTemplateInputME} 及其两个 final 子类
 * （线缆机 / 卷板机；固定模板-挤压与流体固化直接使用基类）。三台共用同一个 GUI 基类，
 * 因此只需按“基类实例”判定一次即可覆盖全部。
 *
 * <p>
 * 按项目坑位第 18/19 条：不猜 modid、不依赖包名，直接做“关键类可解析 + isInstance”判定；
 * 未检测到时打一条 INFO，避免“默认开关没挂上”表现为无痕迹的静默失效。
 */
public final class TenkeiTechIntegration {

    /** TenkeiTech 自带模板输入总线基类（1.1.x 运行包已核实）。 */
    private static final String AUTO_TEMPLATE_HATCH_CLASS = "com.tenkeitech.machine.MTEHatchAutoTemplateInputME";

    private static boolean resolved;
    private static Class<?> autoTemplateHatchClass;

    private TenkeiTechIntegration() {}

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            autoTemplateHatchClass = Class
                .forName(AUTO_TEMPLATE_HATCH_CLASS, false, TenkeiTechIntegration.class.getClassLoader());
        } catch (Throwable ignored) {
            autoTemplateHatchClass = null;
        }
        if (autoTemplateHatchClass != null) {
            MyMod.LOG.info("[AE2QoL] 检测到 TenkeiTech 自带模板输入总线（{}）：智能倍增默认开启，关闭开关时回退其原生批量", AUTO_TEMPLATE_HATCH_CLASS);
        } else {
            MyMod.LOG.info("[AE2QoL] 未检测到 TenkeiTech 自带模板输入总线（{}）：相关默认开关与原生批量回退均不生效", AUTO_TEMPLATE_HATCH_CLASS);
        }
    }

    /**
     * @param machine 任意 MTE 实例
     * @return 是否为 TenkeiTech 的自带模板输入总线（含两个 final 子类）
     */
    public static boolean isAutoTemplateHatch(Object machine) {
        resolve();
        return autoTemplateHatchClass != null && autoTemplateHatchClass.isInstance(machine);
    }

    public static boolean isAvailable() {
        resolve();
        return autoTemplateHatchClass != null;
    }
}
