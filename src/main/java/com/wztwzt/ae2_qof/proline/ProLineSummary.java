package com.wztwzt.ae2_qof.proline;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 汇总器（设计 §6.2）。
 *
 * <p>
 * 口径（v6 A4 安装在案）：
 * <ul>
 * <li><b>总输入</b> = 各步骤 inputs 中"没有任何步骤 outputs 提供"的（=内部链扣之外的外购物料）；
 * <li><b>总输出</b> = 各步骤 outputs 中"没有任何步骤 inputs 消耗"的（=末端产物）；</li>
 * <li>v1 简化：口径计算<b>不分 branch</b>（branch chips 只过滤视图），多分支产线的"被屏蔽分支"
 * 会进入口径——这与设计 §6.2 的"Branch 路线敏感"是<b>已知偏差</b>，登记在实现效果说明里，
 * 4.2.0 再补分支切换重算；</li>
 * <li>累计计数器独立于口径（权威在 {@link ProductionLine#addCumulative}），历史不回溯。</li>
 * </ul>
 */
public final class ProLineSummary {

    private ProLineSummary() {}

    /** 外购物 key 集（总输入）。 */
    public static Set<String> externalInputs(ProductionLine line) {
        Set<String> out = new HashSet<>();
        if (line == null) return out;
        for (ProductionStep s : line.steps) for (ItemRow r : s.inputs) {
            if (!isProducedInternally(line, r.key)) out.add(r.key);
        }
        return out;
    }

    /** 末端产物 key 集（总输出）。 */
    public static Set<String> externalOutputs(ProductionLine line) {
        Set<String> out = new HashSet<>();
        if (line == null) return out;
        for (ProductionStep s : line.steps) for (ItemRow r : s.outputs) {
            if (!isConsumedInternally(line, r.key)) out.add(r.key);
        }
        return out;
    }

    /** 采样要盯的 key 集 = 总输入 ∪ 总输出。 */
    public static Map<String, long[]> externalKeys(ProductionLine line) {
        Map<String, long[]> map = new LinkedHashMap<>();
        for (String k : externalInputs(line)) map.put(k, new long[] { 1, 0 });
        for (String k : externalOutputs(line)) map.put(k, new long[] { 0, 1 });
        return map;
    }

    private static boolean isProducedInternally(ProductionLine line, String key) {
        for (ProductionStep s : line.steps) if (s.produces(key)) return true;
        return false;
    }

    private static boolean isConsumedInternally(ProductionLine line, String key) {
        for (ProductionStep s : line.steps) for (ItemRow r : s.inputs) if (r.key.equals(key)) return true;
        return false;
    }

    /** 平均/小时 = 累计 × 3600000 ÷ max(1h, now−created)（§2.3/§6.2 统一口径，含 1 小时护栏）。 */
    public static BigInteger avgPerHour(BigInteger cumulative, long createdAtMs, long nowMs) {
        if (cumulative == null) return BigInteger.ZERO;
        long elapsed = nowMs - createdAtMs;
        if (elapsed < 3_600_000L) elapsed = 3_600_000L;
        return cumulative.multiply(BigInteger.valueOf(3_600_000L))
            .divide(BigInteger.valueOf(Math.max(1L, elapsed)));
    }

    /** 简写格式（与 AdaptiveNetTerminal.formatKMG 同口径；独立实现避免抽公共类漏改风险）。 */
    public static String kmg(BigInteger v) {
        if (v == null) return "0";
        boolean neg = v.signum() < 0;
        String s;
        BigInteger abs = v.abs();
        if (abs.compareTo(BigInteger.valueOf(1_000_000_000_000L)) >= 0) {
            s = fmt(abs, 12) + "T";
        } else if (abs.compareTo(BigInteger.valueOf(1_000_000_000L)) >= 0) {
            s = fmt(abs, 9) + "G";
        } else if (abs.compareTo(BigInteger.valueOf(1_000_000L)) >= 0) {
            s = fmt(abs, 6) + "M";
        } else if (abs.compareTo(BigInteger.valueOf(1_000L)) >= 0) {
            s = fmt(abs, 3) + "K";
        } else {
            s = abs.toString();
        }
        return neg ? "-" + s : s;
    }

    /** 纯整数位移流感：来自 3.35.0-fix23 的 BigInteger 统计先例。 */
    private static String fmt(BigInteger abs, int mag) {
        BigInteger head = abs.divide(BigInteger.TEN.pow(mag));
        BigInteger rem = abs.mod(BigInteger.TEN.pow(mag));
        BigInteger scale = BigInteger.TEN.pow(mag - 1);
        double frac = rem.doubleValue() / scale.doubleValue() / 10.0;
        return head + String.format(".%1d", Math.round(frac * 10));
    }

    /** 步骤可用度徽标判据（§4.2 字段 6）：-1 已暂停 / 0 空闲 / 1 部分在产 / 2 在产。 */
    public static int badgeOf(ProductionLine line, ProductionStep s, int[] runState) {
        if (line.isStepPausedByLineOrStep(s)) return -1;
        if (runState == null) return 0;
        int running = runState[0];
        int bound = runState[1];
        if (bound <= 0) return 0;
        if (running <= 0) return 0;
        return running >= bound ? 2 : 1;
    }
}
