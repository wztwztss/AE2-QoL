package com.wztwzt.ae2_qof.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.inventory.InventoryCrafting;

import com.wztwzt.ae2_qof.MyMod;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.me.cache.CraftingGridCache;

/**
 * 反射桥接 TenkeiTech 1.1.x 注入到 {@code CraftingCPUCluster} 的原生批量实现。
 *
 * <p>
 * 背景：TenkeiTech 的多轮发配是 vanilla {@code executeCrafting} 方法体内的 {@code @Redirect}
 * （{@code tenkeitech$pushPatternBatch}）。AE2-QoL 的智能倍增在 HEAD 处 cancel 整个 tick，
 * 方法体因此不再执行——当玩家关闭这三台的开关时，若不主动调用对方实现，
 * 它们就只剩“一次一轮”。本桥只在该开关显式关闭时使用，用来真正恢复它本身的机制。
 *
 * <p>
 * 依赖判定按“方法名后缀 + 完整参数描述符”匹配（不硬绑方法全名），并缓存反射结果；
 * 找不到时只打一次日志并返回 {@link #UNAVAILABLE}，由调用方降级为原版单轮推送。
 *
 * <p>
 * 返回值约定：
 * <ul>
 * <li>{@code > 0}：原生实现已接收，值为它本轮实际发配的轮数（factor）；</li>
 * <li>{@code 0}：已尝试但被拒绝（或调用中抛异常），调用方按原版语义本轮不再重试；</li>
 * <li>{@link #UNAVAILABLE}：对方实现不存在，调用方退回单轮 {@code pushPattern}。</li>
 * </ul>
 */
public final class TenkeiTechNativeBatchBridge {

    /** 不可用：调用方应退回原版单轮推送。 */
    public static final long UNAVAILABLE = -1L;

    private static final String METHOD_NAME_SUFFIX = "$pushPatternBatch";
    private static final String FACTOR_FIELD_SUFFIX = "$batchFactor";

    private static boolean resolved;
    private static Method pushPatternBatch;
    private static Field batchFactor;
    private static boolean missingLogged;
    private static boolean invokeFailureLogged;
    private static boolean successLogged;

    private TenkeiTechNativeBatchBridge() {}

    /**
     * 尝试调用 TenkeiTech 的原生批量发配。
     *
     * @param cluster            {@code CraftingCPUCluster} 实例（this）
     * @param medium             样板介质（必须是 TenkeiTech 自带模板仓）
     * @param details            样板
     * @param craftingTable      仅含 1 轮原料的 ME 合成桌（对方实现会自行放大）
     * @param energyGrid         能量网
     * @param craftingGridCache  合成网格缓存
     * @return 见类注释的返回值约定
     */
    public static long tryPushNativeBatch(Object cluster, ICraftingMedium medium, ICraftingPatternDetails details,
        InventoryCrafting craftingTable, IEnergyGrid energyGrid, CraftingGridCache craftingGridCache) {
        resolve(cluster);
        if (pushPatternBatch == null) {
            if (!missingLogged) {
                missingLogged = true;
                MyMod.LOG.info(
                    "[AE2QoL] 未找到 TenkeiTech 原生批量方法（*{}），三台自带模板仓关闭开关时降级为单轮推送",
                    METHOD_NAME_SUFFIX);
            }
            return UNAVAILABLE;
        }
        try {
            final Object accepted = pushPatternBatch
                .invoke(cluster, medium, details, craftingTable, energyGrid, craftingGridCache);
            if (!(accepted instanceof Boolean) || !((Boolean) accepted).booleanValue()) {
                return 0L;
            }
            long factor = 1L;
            if (batchFactor != null) {
                factor = batchFactor.getLong(cluster);
                if (factor <= 1L) {
                    factor = 1L;
                }
            }
            // 成功路径留一条一次性痕迹：否则“关闭开关是否真的回退到对方批量”只能靠肉眼数物品。
            if (factor > 1L && !successLogged) {
                successLogged = true;
                MyMod.LOG.info(
                    "[AE2QoL] 已回退到 TenkeiTech 原生批量（{}）：本轮 factor={}",
                    medium.getClass()
                        .getSimpleName(),
                    factor);
            }
            return factor;
        } catch (Throwable t) {
            // 对方在拒绝路径里已自行还原合成桌并退回额外原料；此处不再重试，避免重复推送。
            if (!invokeFailureLogged) {
                invokeFailureLogged = true;
                MyMod.LOG.warn("[AE2QoL] 调用 TenkeiTech 原生批量失败，本轮按拒绝处理（不重试）", t);
            }
            return 0L;
        }
    }

    private static void resolve(Object cluster) {
        if (resolved) {
            return;
        }
        resolved = true;
        if (cluster == null) {
            return;
        }
        final Class<?> type = cluster.getClass();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName()
                .endsWith(METHOD_NAME_SUFFIX)) {
                continue;
            }
            final Class<?>[] params = method.getParameterTypes();
            if (params.length != 5 || params[0] != ICraftingMedium.class
                || params[1] != ICraftingPatternDetails.class
                || params[2] != InventoryCrafting.class
                || params[3] != IEnergyGrid.class
                || params[4] != CraftingGridCache.class) {
                continue;
            }
            try {
                method.setAccessible(true);
            } catch (Throwable ignored) {}
            pushPatternBatch = method;
            break;
        }
        if (pushPatternBatch == null) {
            return;
        }
        for (Field field : type.getDeclaredFields()) {
            if (!field.getName()
                .endsWith(FACTOR_FIELD_SUFFIX) || field.getType() != long.class) {
                continue;
            }
            try {
                field.setAccessible(true);
            } catch (Throwable ignored) {}
            batchFactor = field;
            break;
        }
    }
}
