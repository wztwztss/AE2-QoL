package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.wztwzt.ae2_qof.MyMod;

import gregtech.api.metatileentity.implementations.MTEHatch;

/**
 * 跨模组「能源仓档位」适配器：把第三方机器里对 {@code mTier} <b>字段</b>的读取，改写成对
 * {@link MTEHatch#getInputTier()} 的读取。
 *
 * <h2>为什么必须在这里改（证据）</h2>
 * <ul>
 * <li>实例 GT 5.09.54.265 的 {@code MTETieredMachineBlock} 里档位是
 * <b>{@code public final byte mTier}</b>（{@code javap -p} 实证）——{@code final} ⇒ 自适应仓
 * <b>没有任何合法手段</b>让这个字段跟着电网档位变（上一轮 fix15 只能覆写 accessor）。</li>
 * <li>但 TST / GTNL / OTH(123) 的一批多方块机器直接读这个字段：
 * 
 * <pre>
 * TST  TST_IntegratedAssemblyMatrix.checkEnergyLimitation()  → MTEHatchEnergy.mTier / MTEHatch.mTier
 * TST  TST_BigBroArray:452/458                               → 玻璃等级校验（结构门槛，本轮不动）
 * GTNL MultiMachineBase.checkEnergyHatchTier()               → 同上两个字段
 * OTH  OTELargeCircuitAssembler.checkEnergyHatchTier()       → 同上两个字段
 * </pre>
 * 
 * 读完还<b>缓存</b>成 {@code maxVoltage} / {@code mEnergyHatchTier} / {@code machineLimitedVoltage}，
 * 于是自适应仓无论接多高的电网，这些机器都只按注册档（IV）封顶配方电压 ⇒
 * 用户看到的「部分机器识别不到正确电压」。</li>
 * <li>普通能源仓不受影响：它们的 {@code getInputTier()} 就是 {@code mTier}，改写后行为完全一致。</li>
 * </ul>
 *
 * <h2>口径</h2>
 * 返回 {@code (byte) clamp(getInputTier(), 0, 15)}：与字段同类型，数值与 GT 自己的
 * {@code GTUtility.getTier(hatch.getBaseMetaTileEntity().getInputVoltage())} 同源
 * （自适应仓的 {@code getInputTier()} 已按 fix15 覆写为「已绑定 ⇒ 网络档位，否则 mTier」）。
 *
 * <p>
 * <b>可观测性</b>：每个注入站点第一次被调用时打一行 INFO（含首个档位读数与是否命中自适应仓）。
 * 若日志里<b>没有</b>这一行，说明 Mixin 没命中（对方模组换了类名/方法名），
 * 此时机器表现仍会是「只认 IV」——按日志判断，不要凭现象猜。
 */
public final class AdaptiveTierAdapter {

    /** 已经打过日志的站点，避免热路径刷屏。 */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private AdaptiveTierAdapter() {}

    /**
     * @param hatch 被读取 {@code mTier} 的仓（普通仓或自适应仓）
     * @param site  注入站点标识，用于日志定位
     * @return 该仓的<b>真实</b>档位（0..15）
     */
    public static byte effectiveTier(MTEHatch hatch, String site) {
        if (hatch == null) return 0;
        long tier = hatch.getInputTier();
        if (tier < 0L) tier = 0L;
        if (tier > 15L) tier = 15L;
        if (REPORTED.add(site)) {
            MyMod.LOG.info(
                "[AE2QoL] 电压档位适配生效：{} 改为读取 getInputTier()，首个读数 tier={}（自适应仓={}）",
                site,
                tier,
                hatch instanceof AdaptiveNetHatch);
        }
        return (byte) tier;
    }
}
