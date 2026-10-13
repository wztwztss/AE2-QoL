package com.wztwzt.ae2_qof.hatch.adaptive;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;

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
    /**
     * TecTech 的"多安培能源仓"基类（3.32.0-fix2）。
     * 它 **只 extends {@code MTEHatch}**，不是 {@code MTEHatchEnergy}，所以只能按类名判定；
     * 沿继承链比对这一个名字即可覆盖 {@code MTEHatchEnergyMulti} / {@code MTEHatchWirelessMulti} /
     * {@code MTEHatchEnergyTunnel}（后两者都是它的子类）。
     */
    private static final String TECTECH_ENERGY_MULTI = "tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti";

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
     * 3.32.0-fix2：身份判据改成**按真实类别**（继承关系），而不是"只认我们自己那四个类"。
     *
     * <h2>为什么要回退 3.32.0 的"只认四个类"</h2>
     * 3.32.0 把四个槽位收紧成只接受本模组自己的四个类，结果 GT/TecTech 的能源仓
     * **在放置那一刻就被 {@code isItemValidForSlot} 拒绝**（用户实测："任何能源仓都放不进去了"）——
     * 而用户本来就是用 GT/TecTech 的仓来告诉终端"这一类仓的规格"。真正要修的只是**取值**
     * （见 {@code AdaptiveNetTerminal.updateParamsFromSlots} 与 {@link AdaptiveHostIndex#ratedAmps}），
     * 不是把整类挡在门外。
     *
     * <h2>判定表（继承关系已用实例 GT jar 的 javap 核对）</h2>
     * <ul>
     * <li><b>DYNAMO</b>：{@code instanceof MTEHatchDynamo} —— 覆盖本模组动力仓、GT 分级动力仓(30–39)、
     * TecTech {@code MTEHatchDynamoMulti} / {@code MTEHatchDynamoTunnel}（后者也是 MTEHatchDynamo 子类）。</li>
     * <li><b>ENERGY</b>：{@code instanceof MTEHatchEnergy} —— 覆盖本模组能源仓、GT 分级能源仓(40–49)、
     * GT 无线能源仓（{@code MTEWirelessEnergy extends MTEHatchEnergy}）；再加 TecTech 的能源族
     * （它们只 extends {@code MTEHatch}，**不是** MTEHatchEnergy ⇒ 按真实类名沿继承链判定：
     * {@code MTEHatchEnergyMulti} / {@code MTEHatchWirelessMulti} / {@code MTEHatchEnergyTunnel}）。</li>
     * <li><b>LASER_SOURCE</b>：本模组激光仓 + TecTech {@code MTEHatchDynamoTunnel}。</li>
     * <li><b>LASER_TARGET</b>：本模组激光靶仓 + TecTech {@code MTEHatchEnergyTunnel}。</li>
     * </ul>
     * <p>机械外壳仍被拒（{@code MTEBasicHull extends MTEBasicTank}，既不是 MTEHatchEnergy 也不是
     * MTEHatchDynamo，类名也不在兼容表里）——旧版的洞正是它靠 {@code maxEUInput()=V[mTier]>0} 混进来。
     */
    public boolean isValidMTEType(MetaTileEntity mte) {
        if (mte == null) return false;
        switch (this) {
            case DYNAMO:
                return mte instanceof MTEHatchDynamo;
            case ENERGY:
                return mte instanceof MTEHatchEnergy || hasClassNamed(mte, TECTECH_ENERGY_MULTI);
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
