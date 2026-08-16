package com.gali.ae2_auto_pattern_upload.util;

import java.lang.reflect.Field;
import java.util.concurrent.Future;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.crafting.v2.CraftingJobV2;
import appeng.me.cache.CraftingGridCache;

/**
 * 合成重新规划：对已经完成的模拟任务，重新请求一次 job 并挂回容器。
 */
public final class Replanner {

    private static Field resultField;

    private Replanner() {}

    private static ICraftingJob getJob(ContainerCraftConfirm c) {
        try {
            if (resultField == null) {
                resultField = ContainerCraftConfirm.class.getDeclaredField("result");
                resultField.setAccessible(true);
            }
            return (ICraftingJob) resultField.get(c);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 清空 IItemList（977 运行时为 IAEStackList，其内部是 Map<IAEStackType, IItemList>，逐层清空内层列表）。
     */
    @SuppressWarnings("unchecked")
    public static void clearIItemList(appeng.api.storage.data.IItemList<?> list) {
        try {
            if (list == null) {
                return;
            }
            Field listsField = appeng.util.item.IAEStackList.class.getDeclaredField("lists");
            listsField.setAccessible(true);
            java.util.Map<Object, Object> lists = (java.util.Map<Object, Object>) listsField.get(list);
            if (lists == null) {
                return;
            }
            for (Object value : lists.values()) {
                if (value != null) {
                    clearList(value);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void clearList(Object inner) {
        try {
            if (inner instanceof java.util.List) {
                ((java.util.List<?>) inner).clear();
                return;
            }
            java.lang.reflect.Method clear = inner.getClass()
                .getMethod("clear");
            if (clear != null) {
                clear.invoke(inner);
            }
        } catch (Throwable ignored) {}
    }

    public static boolean replan(EntityPlayer player, ContainerCraftConfirm c) {
        ICraftingJob job = getJob(c);
        if (job instanceof CraftingJobV2 jobV2 && jobV2.isDone()) {
            c.simulation = true;
            c.bytesUsed = 0;
        } else {
            return false;
        }
        Object target = c.getTarget();
        if (target instanceof IGridHost gh) {
            final IGridNode gn = gh.getGridNode(ForgeDirection.UNKNOWN);
            if (gn == null) {
                return false;
            }
            final IGrid g = gn.getGrid();
            if (g == null || c.getItemToCraft() == null) {
                return false;
            }

            Future<ICraftingJob> futureJob = null;
            try {
                final ICraftingGrid cg = g.getCache(ICraftingGrid.class);
                if (cg instanceof CraftingGridCache cgc) {
                    futureJob = cgc.beginCraftingJob(c.getWorld(), g, c.getActionSource(), c.getItemToCraft(), null);
                }
                if (player.openContainer instanceof ContainerCraftConfirm ccc) {
                    ccc.setJob(futureJob);
                    ccc.detectAndSendChanges();
                }
                return true;
            } catch (Throwable e) {
                if (futureJob != null) {
                    futureJob.cancel(true);
                }
            }
        }
        return false;
    }
}
