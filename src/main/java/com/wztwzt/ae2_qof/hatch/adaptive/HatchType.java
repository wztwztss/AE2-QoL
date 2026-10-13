package com.wztwzt.ae2_qof.hatch.adaptive;

import gregtech.api.metatileentity.MetaTileEntity;

public enum HatchType {

    DYNAMO(0, 1, 1, "ae2_qof.gui.adaptive_terminal.dynamo_hatch"),
    ENERGY(1, 1, 1, "ae2_qof.gui.adaptive_terminal.energy_hatch"),
    LASER_SOURCE(2, 256, 6, "ae2_qof.gui.adaptive_terminal.laser_source"),
    LASER_TARGET(3, 256, 6, "ae2_qof.gui.adaptive_terminal.laser_target");

    public static final int COUNT = values().length;

    /** TecTech 的激光源仓（激光发射端）。GTNH 里由 TecTech 提供，**不是**本模组的类。 */
    private static final String TECTECH_LASER_SOURCE = "tectech.thing.metaTileEntity.hatch.MTEHatchDynamoTunnel";
    /** TecTech 的激光靶仓（激光接收端）。 */
    private static final String TECTECH_LASER_TARGET = "tectech.thing.metaTileEntity.hatch.MTEHatchEnergyTunnel";

    public static final String[] VOLTAGE_NAMES = {
        "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV", "MAX"
    };

    public final int slotIndex;
    public final int defaultAmps;
    public final int defaultTier;
    public final String translationKey;

    HatchType(int slotIndex, int defaultAmps, int defaultTier, String translationKey) {
        this.slotIndex = slotIndex;
        this.defaultAmps = defaultAmps;
        this.defaultTier = defaultTier;
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public static HatchType fromSlotIndex(int index) {
        for (HatchType type : values()) {
            if (type.slotIndex == index) return type;
        }
        return null;
    }

    public static String getTierName(int tier) {
        if (tier >= 0 && tier < VOLTAGE_NAMES.length) return VOLTAGE_NAMES[tier];
        return "?";
    }

    /**
     * 3.32.0：身份判据从"有输入/有输出"的**鸭子类型**改成**按类**。
     *
     * <p>旧实现是 {@code maxEUInput() > 0} / {@code maxEUOutput() > 0}，有两个致命后果
     * （用户实测「四个仓的检测问题」）：
     * <ol>
     * <li>ENERGY 与 LASER_SOURCE 判据**完全同值**、DYNAMO 与 LASER_TARGET 也完全同值 ⇒ 无法区分是哪一类；</li>
     * <li>任何 GT/TecTech 的能源仓、动力仓都满足判据 ⇒ 放进"放入 64 个自动配置"的槽位会被接受，
     * 而档位/安培又是从**那个外来仓**读出来的（见 {@code AdaptiveNetTerminal.updateParamsFromSlots}），
     * 于是把非本模组的仓的规格写进了本网络所有同类仓。</li>
     * </ol>
     * 现在只认本模组自己的四个类；激光两类额外兼容 TecTech 的两个类（用户口径：要把 TecTech 激光仓
     * **纳入统计与配置**）。
     */
    public boolean isValidMTEType(MetaTileEntity mte) {
        if (mte == null) return false;
        switch (this) {
            case DYNAMO:
                return mte instanceof AdaptiveNetDynamoHatch;
            case ENERGY:
                return mte instanceof AdaptiveNetHatch;
            case LASER_SOURCE:
                return mte instanceof AdaptiveNetLaserHatch || hasClassNamed(mte, TECTECH_LASER_SOURCE);
            case LASER_TARGET:
                return mte instanceof AdaptiveNetLaserTargetHatch || hasClassNamed(mte, TECTECH_LASER_TARGET);
            default:
                return false;
        }
    }

    /** 本槽位是"允许放进外来兼容仓"的槽位（只有激光两类）。 */
    public boolean acceptsForeignCompat() {
        return this == LASER_SOURCE || this == LASER_TARGET;
    }

    /**
     * 该 MTE 是否属于本槽位的**本模组**类（不含 TecTech 兼容项）。
     *
     * <p>3.32.0-fix1：本模组自己的仓是**可配置**的——它们的 {@code maxAmperesIn/Out()} 返回的是
     * {@code helper.getCurrentAmps()}（当前配置值），{@code maxEUInput()/maxEUOutput()} 也按配置档位算，
     * 拿"槽位里的那个原型实例"去读只会得到出厂初值（安培 1、档位 ULV），**不是**该类应有的额定。
     * 所以对它们一律取 {@link #defaultTier} / {@link #defaultAmps}（能源/动力 1A、激光 256A）。
     */
    public boolean isOwnClass(MetaTileEntity mte) {
        if (mte == null) return false;
        switch (this) {
            case DYNAMO:
                return mte instanceof AdaptiveNetDynamoHatch;
            case ENERGY:
                return mte instanceof AdaptiveNetHatch;
            case LASER_SOURCE:
                return mte instanceof AdaptiveNetLaserHatch;
            case LASER_TARGET:
                return mte instanceof AdaptiveNetLaserTargetHatch;
            default:
                return false;
        }
    }

    /** TecTech 激光源仓（沿继承链比对类名，避免对 TecTech 形成编译期依赖）。 */
    public static boolean isTecTechLaserSource(MetaTileEntity mte) {
        return hasClassNamed(mte, TECTECH_LASER_SOURCE);
    }

    /** TecTech 激光靶仓。 */
    public static boolean isTecTechLaserTarget(MetaTileEntity mte) {
        return hasClassNamed(mte, TECTECH_LASER_TARGET);
    }

    /**
     * 沿继承链比对**真实类名**。
     * 为什么不直接引用 TecTech 的类：它们是别的模组/内置模块的类，写成编译期依赖会让
     * "对方改名/不在"变成启动期类加载失败；按类名比对最坏只是不兼容（并会被调用方 WARN 出来）。
     */
    private static boolean hasClassNamed(MetaTileEntity mte, String className) {
        if (mte == null || className == null) return false;
        for (Class<?> c = mte.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (className.equals(c.getName())) return true;
        }
        return false;
    }
}
