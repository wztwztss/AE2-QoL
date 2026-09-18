package com.wztwzt.ae2_qof.util;

import java.lang.reflect.Method;

import net.minecraft.inventory.IInventory;

import com.wztwzt.ae2_qof.MyMod;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.util.DimensionalCoord;

/**
 * 供应器（样板接收机）的稳定定位工具。
 *
 * <p>背景（fix41）：旧实现用 {@link System#identityHashCode} 作为供应器 ID。这个值只是对象在内存中的
 * 地址派生值，区块卸载重载、机器被拆装、服务器重启后都会变化。于是「在界面里选好机器 → 放一会儿 →
 * 再点上传」时，服务端按旧 ID 找不到目标，表现为<b>点了没有任何反应</b>（静默失败）。
 *
 * <p>这里改用「维度 + 坐标（+ 部件朝向）」这类稳定标识：同一台机器只要还在原地，无论对象是否被重建、
 * 服务器是否重启，算出来的 key 都一样。上传时先按 ID 快速命中，命中不了再按坐标兜底查找，
 * 两条路都失败才判定目标真的不在了。
 */
public final class ProviderLocator {

    private ProviderLocator() {}

    /**
     * 计算供应器的稳定位置标识。
     *
     * @param node   该供应器对应的 AE2 网络节点，可为 null
     * @param machine 供应器对象本身
     * @return 形如 {@code D0:-432,63,-2791} 的字符串；坐标取不到时返回 null（调用方回退到 ID 匹配）
     */
    public static String locationKey(IGridNode node, Object machine) {
        DimensionalCoord loc = null;
        try {
            if (node != null && node.getGridBlock() != null) {
                loc = node.getGridBlock()
                    .getLocation();
            }
        } catch (Throwable ignored) {}
        if (loc == null) {
            loc = reflectLocation(machine);
        }
        if (loc == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(32);
        sb.append('D')
            .append(loc.getDimension())
            .append(':')
            .append(loc.x)
            .append(',')
            .append(loc.y)
            .append(',')
            .append(loc.z);
        // 同一方块上的不同面是不同机器（例如贴在方块六个面的 ME 接口），把朝向一并计入
        int side = reflectSide(machine);
        if (side >= 0) {
            sb.append('#')
                .append(side);
        }
        return sb.toString();
    }

    /** 兜底：机器自身实现了 IInterfaceViewable#getLocation 时直接取。 */
    private static DimensionalCoord reflectLocation(Object machine) {
        if (machine == null) {
            return null;
        }
        try {
            if (machine instanceof appeng.api.util.IInterfaceViewable viewable) {
                return viewable.getLocation();
            }
        } catch (Throwable ignored) {}
        try {
            Method m = machine.getClass()
                .getMethod("getLocation");
            Object r = m.invoke(machine);
            if (r instanceof DimensionalCoord coord) {
                return coord;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 部件朝向（仅 AEBasePart 等有 getSide 的对象）；取不到返回 -1。 */
    private static int reflectSide(Object machine) {
        if (machine == null) {
            return -1;
        }
        try {
            Method m = machine.getClass()
                .getMethod("getSide");
            Object r = m.invoke(machine);
            if (r instanceof net.minecraftforge.common.util.ForgeDirection dir) {
                return dir.ordinal();
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /**
     * 在网格内查找目标供应器：先按稳定位置标识匹配，再按对象 ID 匹配。
     *
     * @param grid          终端所在的 AE2 网格
     * @param locationKey   稳定位置标识，可为 null
     * @param identityId    客户端记录的旧式 ID，可为 0
     * @return 命中的供应器；都找不到返回 null
     */
    public static ICraftingProvider find(IGrid grid, String locationKey, long identityId) {
        if (grid == null) {
            return null;
        }
        ICraftingProvider byId = null;
        boolean keyUsable = locationKey != null && !locationKey.isEmpty();
        for (Class<? extends IGridHost> hostClass : grid.getMachinesClasses()) {
            if (!ICraftingProvider.class.isAssignableFrom(hostClass)) {
                continue;
            }
            IMachineSet machines;
            try {
                machines = grid.getMachines(hostClass);
            } catch (Throwable t) {
                continue;
            }
            if (machines == null) {
                continue;
            }
            for (IGridNode machineNode : machines) {
                if (machineNode == null) {
                    continue;
                }
                Object machine = machineNode.getMachine();
                if (!(machine instanceof ICraftingProvider provider)) {
                    continue;
                }
                if (keyUsable) {
                    String key = locationKey(machineNode, machine);
                    if (locationKey.equals(key)) {
                        return provider;
                    }
                }
                if (byId == null && identityId != 0L && System.identityHashCode(machine) == identityId) {
                    byId = provider;
                }
            }
        }
        return byId;
    }

    /** 判断供应器是否实现了专属样板槽（可安全读写样板）。 */
    public static IInventory patternInventoryOf(ICraftingProvider provider) {
        try {
            if (provider instanceof appeng.api.util.IInterfaceViewable viewable) {
                return viewable.getPatterns();
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[Upload] getPatterns failed: {}", t.toString());
        }
        return null;
    }

    /** 供应器专属样板槽的数量上限；非 IInterfaceViewable 返回 0。 */
    public static int patternSlotLimitOf(ICraftingProvider provider) {
        try {
            if (provider instanceof appeng.api.util.IInterfaceViewable viewable) {
                int availableSlots = viewable.rows() * viewable.rowSize();
                IInventory inv = viewable.getPatterns();
                if (inv != null) {
                    return Math.min(availableSlots, inv.getSizeInventory());
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }
}