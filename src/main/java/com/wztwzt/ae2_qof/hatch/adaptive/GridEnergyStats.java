package com.wztwzt.ae2_qof.hatch.adaptive;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 3.35.0-fix23（用户实测：监控页统计超 9.2E 被"截断"）：统计核心从 long **饱和口径**升级为
 * **BigInteger 精确口径**。
 *
 * <p>
 * fix11 的饱和加法在南端解决了"绕回负数"，但用户电网量级（单 tick 增幅 1E18 级）下总量会很快
 * 越过 {@code Long.MAX_VALUE} 并**永远停在该封顶**——мотitor 页"总输入/总输出/变化/预测"从此无法
 * 看到真实值。本类全面 BigInteger 化：
 * <ul>
 * <li>总量/100-tick 窗口和/历史快照全部 {@link BigInteger} 精确累加；</li>
 * <li>数据源换成 {@code tick(BigInteger)}（调用方从已经 BigInteger 化的电网余额直取，
 * 不再经过 {@code getGridEULong} 的 9.22E 钳制）；</li>
 * <li>存档写**新字符串键**（十进制精确）并兼容读旧 long 键；同时把旧键按钳制值写回——
 * 降级到旧版本也不会崩。</li>
 * </ul>
 */
public class GridEnergyStats {

    private BigInteger totalInput = BigInteger.ZERO;
    private BigInteger totalOutput = BigInteger.ZERO;
    private BigInteger lastEU = null;
    private boolean initialized;
    private final BigInteger[] inputBuffer = new BigInteger[100];
    private final BigInteger[] outputBuffer = new BigInteger[100];
    private int bufferIndex;
    private BigInteger bufferSumInput = BigInteger.ZERO;
    private BigInteger bufferSumOutput = BigInteger.ZERO;

    private static final int WINDOW_10M = 12000;
    private static final int WINDOW_1H = 72000;
    private BigInteger gridEU_10min_ago = BigInteger.ZERO;
    private BigInteger gridEU_1h_ago = BigInteger.ZERO;
    private long snapshotTick;

    private static final BigInteger ZERO = BigInteger.ZERO;

    public GridEnergyStats() {
        for (int i = 0; i < inputBuffer.length; i++) {
            inputBuffer[i] = ZERO;
            outputBuffer[i] = ZERO;
        }
    }

    /** 以 BigInteger 精确记一笔（deltas 来自调用方的精确电网余额）。 */
    public void tick(BigInteger currentEU) {
        if (currentEU == null) currentEU = ZERO;
        if (!initialized) {
            lastEU = currentEU;
            gridEU_10min_ago = currentEU;
            gridEU_1h_ago = currentEU;
            initialized = true;
            return;
        }
        BigInteger delta = currentEU.subtract(lastEU);
        int sig = delta.signum();
        if (sig > 0) {
            totalInput = totalInput.add(delta);
            bufferSumInput = bufferSumInput.subtract(inputBuffer[bufferIndex])
                .add(delta);
            inputBuffer[bufferIndex] = delta;
            bufferSumOutput = bufferSumOutput.subtract(outputBuffer[bufferIndex]);
            outputBuffer[bufferIndex] = ZERO;
        } else if (sig < 0) {
            BigInteger mag = delta.negate();
            totalOutput = totalOutput.add(mag);
            bufferSumOutput = bufferSumOutput.subtract(outputBuffer[bufferIndex])
                .add(mag);
            outputBuffer[bufferIndex] = mag;
            bufferSumInput = bufferSumInput.subtract(inputBuffer[bufferIndex]);
            inputBuffer[bufferIndex] = ZERO;
        } else {
            bufferSumInput = bufferSumInput.subtract(inputBuffer[bufferIndex]);
            inputBuffer[bufferIndex] = ZERO;
            bufferSumOutput = bufferSumOutput.subtract(outputBuffer[bufferIndex]);
            outputBuffer[bufferIndex] = ZERO;
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

    public BigInteger getTotalInput() {
        return totalInput;
    }

    public BigInteger getTotalOutput() {
        return totalOutput;
    }

    public BigInteger getNetChange() {
        return totalOutput.subtract(totalInput);
    }

    public BigInteger getInstantInputRate() {
        return bufferSumInput.divide(BigInteger.valueOf(100));
    }

    public BigInteger getInstantOutputRate() {
        return bufferSumOutput.divide(BigInteger.valueOf(100));
    }

    public BigInteger getChange1h(BigInteger currentEU) {
        if (!initialized) return ZERO;
        if (currentEU == null) currentEU = ZERO;
        return currentEU.subtract(gridEU_1h_ago);
    }

    public BigInteger getChange10min(BigInteger currentEU) {
        if (!initialized) return ZERO;
        if (currentEU == null) currentEU = ZERO;
        return currentEU.subtract(gridEU_10min_ago);
    }

    /** 1h 窗口的平均输出速率（只在净下降时为正，向下取整）。 */
    public BigInteger getAvgOutputRate1h(BigInteger currentEU) {
        BigInteger change = getChange1h(currentEU);
        if (change.signum() >= 0) return ZERO;
        BigInteger rate = change.negate()
            .divide(BigInteger.valueOf(WINDOW_1H));
        return rate.signum() < 1 ? ZERO : rate;
    }

    /** 10 分钟窗口的平均输出速率（只在净下降时为正）。 */
    public BigInteger getAvgOutputRate10min(BigInteger currentEU) {
        BigInteger change = getChange10min(currentEU);
        if (change.signum() >= 0) return ZERO;
        BigInteger rate = change.negate()
            .divide(BigInteger.valueOf(WINDOW_10M));
        return rate.signum() < 1 ? ZERO : rate;
    }

    public void saveNBT(NBTTagCompound nbt) {
        // 3.35.0-fix23：新键 = 十进制精确串；旧 long 键继续写（钳制值）——
        // 旧版本模组读新存档不崩（拿到封顶值，行为与旧版一致）
        nbt.setString("gridTotalInX", totalInput.toString());
        nbt.setString("gridTotalOutX", totalOutput.toString());
        nbt.setString("gridEU10mX", gridEU_10min_ago.toString());
        nbt.setString("gridEU1hX", gridEU_1h_ago.toString());
        nbt.setLong(
            "gridTotalIn",
            totalInput.min(bi(Long.MAX_VALUE))
                .longValueExact());
        nbt.setLong(
            "gridTotalOut",
            totalOutput.min(bi(Long.MAX_VALUE))
                .longValueExact());
        nbt.setLong(
            "gridEU10m",
            gridEU_10min_ago.min(bi(Long.MAX_VALUE))
                .longValueExact());
        nbt.setLong(
            "gridEU1h",
            gridEU_1h_ago.min(bi(Long.MAX_VALUE))
                .longValueExact());
        nbt.setLong("snapTick", snapshotTick);
    }

    public void loadNBT(NBTTagCompound nbt) {
        totalInput = readBig(nbt, "gridTotalInX", "gridTotalIn");
        totalOutput = readBig(nbt, "gridTotalOutX", "gridTotalOut");
        gridEU_10min_ago = readBig(nbt, "gridEU10mX", "gridEU10m");
        gridEU_1h_ago = readBig(nbt, "gridEU1hX", "gridEU1h");
        snapshotTick = nbt.getLong("snapTick");
        initialized = false;
    }

    private static BigInteger bi(long v) {
        return BigInteger.valueOf(v);
    }

    /** 读新字符串键；缺失/解析失败回退旧 long 键（老存档兼容）。 */
    private static BigInteger readBig(NBTTagCompound nbt, String newKey, String legacyKey) {
        if (nbt.hasKey(newKey, 8)) {
            try {
                return new BigInteger(
                    nbt.getString(newKey)
                        .trim());
            } catch (RuntimeException bad) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 统计存档键 {} 不是合法十进制串，回退旧键 {}", newKey, bad);
            }
        }
        return bi(nbt.getLong(legacyKey));
    }

    public void reset() {
        totalInput = ZERO;
        totalOutput = ZERO;
        lastEU = null;
        initialized = false;
        for (int i = 0; i < 100; i++) {
            inputBuffer[i] = ZERO;
            outputBuffer[i] = ZERO;
        }
        bufferIndex = 0;
        bufferSumInput = ZERO;
        bufferSumOutput = ZERO;
        gridEU_10min_ago = ZERO;
        gridEU_1h_ago = ZERO;
        snapshotTick = 0;
    }
}
