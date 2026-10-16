package com.wztwzt.ae2_qof.hatch.adaptive;

import net.minecraft.nbt.NBTTagCompound;

public class GridEnergyStats {

    private long totalInput;
    private long totalOutput;
    private long lastEU;
    private boolean initialized;
    private final long[] inputBuffer = new long[100];
    private final long[] outputBuffer = new long[100];
    private int bufferIndex;
    private long bufferSumInput;
    private long bufferSumOutput;

    private static final int WINDOW_10M = 12000;
    private static final int WINDOW_1H = 72000;
    private long gridEU_10min_ago;
    private long gridEU_1h_ago;
    private long snapshotTick;

    /**
     * 3.35.0-fix11（审查 P2-2）：饱和加法 —— 用户电网量级下单 tick 增幅可达 1E18，
     * 累计量与 100-tick 窗口和都会越过 long 上限绕回负数（显示成乱码/负数）。
     * 统计口径下"封顶在 Long.MAX_VALUE"远好于"绕回"，且小数值场合与普通加法完全一致。
     */
    private static long saturatedAdd(long a, long b) {
        long r = a + b;
        // 溢出判定：符号与两个加数都不同（正+正得负 / 负+负得正）
        if (((a ^ r) & (b ^ r)) < 0) {
            return b > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return r;
    }

    public GridEnergyStats() {
        totalInput = 0;
        totalOutput = 0;
        lastEU = 0;
        initialized = false;
        bufferIndex = 0;
        bufferSumInput = 0;
        bufferSumOutput = 0;
        gridEU_10min_ago = 0;
        gridEU_1h_ago = 0;
        snapshotTick = 0;
    }

    public void tick(long currentEU) {
        if (!initialized) {
            lastEU = currentEU;
            gridEU_10min_ago = currentEU;
            gridEU_1h_ago = currentEU;
            initialized = true;
            return;
        }
        long delta = currentEU - lastEU;
        if (delta > 0) {
            totalInput = saturatedAdd(totalInput, delta);
            bufferSumInput -= inputBuffer[bufferIndex];
            inputBuffer[bufferIndex] = delta;
            bufferSumInput = saturatedAdd(bufferSumInput, delta);
            bufferSumOutput -= outputBuffer[bufferIndex];
            outputBuffer[bufferIndex] = 0;
        } else if (delta < 0) {
            totalOutput = saturatedAdd(totalOutput, -delta);
            bufferSumOutput -= outputBuffer[bufferIndex];
            outputBuffer[bufferIndex] = -delta;
            bufferSumOutput = saturatedAdd(bufferSumOutput, -delta);
            bufferSumInput -= inputBuffer[bufferIndex];
            inputBuffer[bufferIndex] = 0;
        } else {
            bufferSumInput -= inputBuffer[bufferIndex];
            inputBuffer[bufferIndex] = 0;
            bufferSumOutput -= outputBuffer[bufferIndex];
            outputBuffer[bufferIndex] = 0;
        }
        lastEU = currentEU;
        bufferIndex = (bufferIndex + 1) % 100;

        snapshotTick++;
        if (snapshotTick % WINDOW_10M == 0) {
            gridEU_10min_ago = currentEU;
        }
        if (snapshotTick % WINDOW_1H == 0) {
            gridEU_1h_ago = currentEU;
        }
    }

    public long getTotalInput() {
        return totalInput;
    }

    public long getTotalOutput() {
        return totalOutput;
    }

    public long getNetChange() {
        return totalOutput - totalInput;
    }

    public long getInstantInputRate() {
        return bufferSumInput / 100;
    }

    public long getInstantOutputRate() {
        return bufferSumOutput / 100;
    }

    public long getChange1h(long currentEU) {
        if (!initialized) return 0;
        return currentEU - gridEU_1h_ago;
    }

    public long getChange10min(long currentEU) {
        if (!initialized) return 0;
        return currentEU - gridEU_10min_ago;
    }

    public long getAvgOutputRate1h(long currentEU) {
        long change = getChange1h(currentEU);
        if (change >= 0) return 0;
        long rate = -change / WINDOW_1H;
        return rate < 1 ? 0 : rate;
    }

    public long getAvgOutputRate10min(long currentEU) {
        long change = getChange10min(currentEU);
        if (change >= 0) return 0;
        long rate = -change / WINDOW_10M;
        return rate < 1 ? 0 : rate;
    }

    public void saveNBT(NBTTagCompound nbt) {
        nbt.setLong("gridTotalIn", totalInput);
        nbt.setLong("gridTotalOut", totalOutput);
        nbt.setLong("gridEU10m", gridEU_10min_ago);
        nbt.setLong("gridEU1h", gridEU_1h_ago);
        nbt.setLong("snapTick", snapshotTick);
    }

    public void loadNBT(NBTTagCompound nbt) {
        totalInput = nbt.getLong("gridTotalIn");
        totalOutput = nbt.getLong("gridTotalOut");
        gridEU_10min_ago = nbt.getLong("gridEU10m");
        gridEU_1h_ago = nbt.getLong("gridEU1h");
        snapshotTick = nbt.getLong("snapTick");
        initialized = false;
    }

    public void reset() {
        totalInput = 0;
        totalOutput = 0;
        lastEU = 0;
        initialized = false;
        for (int i = 0; i < 100; i++) {
            inputBuffer[i] = 0;
            outputBuffer[i] = 0;
        }
        bufferIndex = 0;
        bufferSumInput = 0;
        bufferSumOutput = 0;
        gridEU_10min_ago = 0;
        gridEU_1h_ago = 0;
        snapshotTick = 0;
    }
}
