package com.wztwzt.ae2_qof.util;

import java.math.BigInteger;

import org.lwjgl.input.Keyboard;

/**
 * 大数格式化（3.13.0，无限磁盘 tooltip 用）：数量必然巨大，禁止裸数字直出。
 * <ul>
 * <li>默认：字母单位链 K→M→B→T→Qa→Qi→Sx→Sp→Oc→No→Dc（如 12.34M）</li>
 * <li>按住 Ctrl：科学计数法（如 1.23×10^7）</li>
 * </ul>
 */
public final class BigNumFormatter {

    private static final String[] UNITS = { "", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc" };
    private static final BigInteger THOUSAND = BigInteger.valueOf(1000);

    private BigNumFormatter() {}

    /** 默认字母单位格式（<1000 直出整数）。 */
    public static String format(BigInteger value) {
        if (value == null) return "0";
        if (value.signum() < 0) return "-" + format(value.negate());
        if (value.compareTo(THOUSAND) < 0) return value.toString();
        int unit = 0;
        BigInteger scaled = value;
        while (scaled.compareTo(THOUSAND) >= 0 && unit < UNITS.length - 1) {
            scaled = scaled.divide(THOUSAND);
            unit++;
        }
        // 小数部分取自原值，避免大整数除法精度损失
        double frac = value.doubleValue() / Math.pow(1000, unit);
        return trim(frac) + UNITS[unit];
    }

    public static String format(long value) {
        return format(BigInteger.valueOf(value));
    }

    /** 科学计数法（Ctrl 按下时使用）；<1000 时退回普通格式。指数取十进制位数，尾数保留 2 位。 */
    public static String formatSci(BigInteger value) {
        if (value == null) return "0";
        if (value.signum() < 0) return "-" + formatSci(value.negate());
        if (value.compareTo(THOUSAND) < 0) return value.toString();
        // 十进制位数（bitLength 是二进制位数，曾导致指数错乱、尾数恒 0.00）
        int exp = value.toString()
            .length() - 1;
        double mantissa = value.doubleValue() / Math.pow(10, exp); // double 相对精度 ~1e-16，尾数足够
        if (mantissa >= 10) {
            mantissa /= 10;
            exp += 1;
        }
        return String.format("%.2f×10^%d", mantissa, exp);
    }

    public static String formatSci(long value) {
        return formatSci(BigInteger.valueOf(value));
    }

    /** 当前是否按住 Ctrl（tooltip 内动态切换显示风格）。 */
    public static boolean isCtrlDown() {
        try {
            return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String trim(double d) {
        if (d >= 100) return String.format("%.0f", d);
        if (d >= 10) return String.format("%.1f", d);
        return String.format("%.2f", d);
    }
}
