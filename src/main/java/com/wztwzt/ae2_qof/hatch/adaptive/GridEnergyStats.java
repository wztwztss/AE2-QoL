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

    private static final int WINDOW_10M = 600;
    private static final int WINDOW_1H = 3600;
    private long gridEU_10min_ago;
    private long gridEU_1h_ago;
    private long snapshotTick;

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
            totalInput += delta;
            bufferSumInput -= inputBuffer[bufferIndex];
            inputBuffer[bufferIndex] = delta;
            bufferSumInput += delta;
            bufferSumOutput -= outputBuffer[bufferIndex];
            outputBuffer[bufferIndex] = 0;
        } else if (delta < 0) {
            totalOutput += -delta;
            bufferSumOutput -= outputBuffer[bufferIndex];
            outputBuffer[bufferIndex] = -delta;
            bufferSumOutput += -delta;
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
        return bufferSumInput;
    }

    public long getInstantOutputRate() {
        return bufferSumOutput;
    }

    public long getChange1h(long currentEU) {
        return currentEU - gridEU_1h_ago;
    }

    public long getChange10min(long currentEU) {
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
