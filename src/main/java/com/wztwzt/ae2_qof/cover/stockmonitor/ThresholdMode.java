package com.wztwzt.ae2_qof.cover.stockmonitor;

public enum ThresholdMode {

    BELOW_THRESHOLD_RUN,
    ABOVE_THRESHOLD_RUN;

    public static ThresholdMode fromOrdinal(int ordinal) {
        ThresholdMode[] values = values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return BELOW_THRESHOLD_RUN;
    }

    public boolean shouldWork(long stockCount, long threshold) {
        switch (this) {
            case BELOW_THRESHOLD_RUN:
                return stockCount < threshold;
            case ABOVE_THRESHOLD_RUN:
                return stockCount >= threshold;
            default:
                return false;
        }
    }
}
