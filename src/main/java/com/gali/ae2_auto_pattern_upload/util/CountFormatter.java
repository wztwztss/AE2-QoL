package com.gali.ae2_auto_pattern_upload.util;

/**
 * 长整型数量的可读格式化：K/M/G/T/P/E 单位，覆盖 Long 全范围。
 */
public final class CountFormatter {

    private static final long[] LIMITS = { 1_000L, 1_000_000L, 1_000_000_000L, 1_000_000_000_000L,
        1_000_000_000_000_000L, 1_000_000_000_000_000_000L };

    private static final char[] SUFFIXES = { 'K', 'M', 'G', 'T', 'P', 'E' };

    private CountFormatter() {}

    public static String format(long count) {
        for (int i = LIMITS.length - 1; i >= 0; i--) {
            long limit = LIMITS[i];
            if (count >= limit) {
                double scaled = (double) count / limit;
                String s = String.format("%.1f%c", scaled, SUFFIXES[i]);
                if (s.endsWith(".0" + SUFFIXES[i])) {
                    return (count / limit) + String.valueOf(SUFFIXES[i]);
                }
                return s;
            }
        }
        return String.valueOf(count);
    }
}
