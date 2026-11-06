package com.wztwzt.ae2_qof.machindex.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 机器过滤：决定「哪些机器进入任务书」。
 *
 * <h2>两道闸门（用户 2026-10-29 拍板）</h2>
 * <ol>
 * <li><b>电压下限</b>：只保留合成电压 ≥ {@link #MIN_TIER}（UHV=9）的多方块主机。
 * 用户原话「把 UHV 之下的机器删掉」—— 低档机器是整合包早期内容，写进索引只会让列表变长；</li>
 * <li><b>未知电压白名单</b>：电压解析不出来（{@code mainTier < 0}）的机器**默认保留**，
 * 但用户挑出的 16 台低档/蒸汽机器按 {@code registry:meta} 显式排除。</li>
 * </ol>
 *
 * <h2>为什么未知默认保留而不是默认删除</h2>
 * 未知＝**解析缺口**，不等于「低档」。实测这 54 台里既有不可视之触、兰波顿工厂、
 * 特大等离子涡轮这类终局机器，也有地热锅炉、焦炉这类低压机器。
 * 若一律删，高档机器会静默消失且没人发现；所以默认保留，低档的由人工点名剔除。
 *
 * <p>
 * 两道闸门都可逆：改 {@link #MIN_TIER} 或 {@link #UNKNOWN_EXCLUDE} 即可，不用重做分类。
 */
public final class MachineFilter {

    private MachineFilter() {}

    /** 电压下限：9 = UHV（GT 档位 0=ULV … 9=UHV … 14=MAX）。 */
    public static final int MIN_TIER = 9;

    /** 是否保留「电压未知」的机器（默认保留，见类注释）。 */
    public static final boolean KEEP_UNKNOWN = true;

    /**
     * 用户点名的「电压未知、但确实属于低档/蒸汽」的排除清单，格式 {@code registryName:meta}。
     *
     * <p>
     * 这些机器读不出电压（不在装配机配方里），但从机器名可确认是低压蒸汽段内容，
     * 与「只留 UHV 及以上」冲突，因此显式剔除。以后想加回来，删掉对应行即可。
     */
    private static final Set<String> UNKNOWN_EXCLUDE = new HashSet<>(
        Arrays.asList(
            // 加固砖高炉 / 焦炉 / 熔炉组 / 地热锅炉 —— 蒸汽与 LV 段
            "gregtech:gt.blockmachines:14856",
            "gregtech:gt.blockmachines:236",
            "gregtech:gt.blockmachines:21218",
            "gregtech:gt.blockmachines:875",
            "gregtech:gt.blockmachines:14852",
            // 洗矿厂系列（蒸汽/LV）
            "gregtech:gt.blockmachines:31082",
            "gregtech:gt.blockmachines:21068",
            "gregtech:gt.blockmachines:850",
            // 原木拟生场 ×2、藻类农场、大气离心机
            "gregtech:gt.blockmachines:836",
            "gregtech:gt.blockmachines:15541",
            "gregtech:gt.blockmachines:997",
            "gregtech:gt.blockmachines:14806",
            // 空气压缩机、蒸汽流体钻井、自驱式蒸汽压缩机
            "gregtech:gt.blockmachines:14805",
            "gregtech:gt.blockmachines:14800",
            "gregtech:gt.blockmachines:14855",
            // 自动售货机（装饰/物流，不属于机器产线）
            "gregtech:gt.blockmachines:2741"));

    /**
     * 这台机器是否进入索引。
     *
     * @param machine 已解析完电压的机器
     */
    public static boolean accept(IndexModel.MachineEntry machine) {
        if (machine == null) return false;
        // 只收多方块主机（单方块是「每电压档一台」的廉价变体，早已被剔除）
        if (!machine.isMultiblock) return false;

        if (machine.mainTier == IndexModel.TIER_UNKNOWN) {
            if (!KEEP_UNKNOWN) return false;
            String key = machine.registryName + ":" + machine.meta;
            return !UNKNOWN_EXCLUDE.contains(key);
        }
        return machine.mainTier >= MIN_TIER;
    }

    /** 某个 {@code registry:meta} 是否在排除清单里（供命令/诊断）。 */
    public static boolean isExcluded(String registryName, int meta) {
        return UNKNOWN_EXCLUDE.contains(registryName + ":" + meta);
    }
}
