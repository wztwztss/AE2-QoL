package com.wztwzt.ae2_qof.wildcard;

import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.wztwzt.ae2_qof.MyMod;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

/**
 * 通配样板**诊断**工具（3.39.0-diag，只加日志、不改行为）。
 *
 * <h2>为什么需要它</h2>
 * 3.38.0 实测：三族（GT 样板仓 / GTNL 超级总成 / PH 系列含 MK.III）**都**注册了 396 条 details
 * （日志 `注册 details=396` 三族一致、`pushPattern 反查失败` 0 条），但用户看到的是：
 * GT 样板仓 → AE 终端里挂板全部可合成 ✅；GTNL / MK.III → **AE 里"根本没有样板"**（连下单都不行）。
 * 光看我们自己的日志已经无法区分下面两种完全不同的情况：
 * <ol>
 * <li><b>我们的数据不对</b>：那 396 条 details 的输出其实全一样（例如都还是模板那块铁板）
 * ⇒ AE 的合成表里只有一种输出；</li>
 * <li><b>我们的数据对、AE 没收录</b>：注册动作发生了，但 AE2 的合成表里查不到
 * ⇒ 问题在 AE2 侧 / 机器侧（介质、网格、时机）。</li>
 * </ol>
 * 因此本类做两件事：① 把注册的那批 details 的**输出种类数与样本**打出来；
 * ② **延迟回读 AE2 的合成表**（{@link ICraftingGrid#getCraftingPatterns()}），直接看 AE 收录了多少、
 * 抽样输出能不能查到。两者一对比，结论是二选一，不再靠猜。
 */
public final class SmartWildcardDiag {

    private SmartWildcardDiag() {}

    /** 描述一批 details 的输出：去重后的种类数 + 前 {@code sample} 个样本（含 craftable/priority）。 */
    public static String describe(List<ICraftingPatternDetails> details, int sample) {
        try {
            if (details == null || details.isEmpty()) return "输出种类=0（空）";
            java.util.LinkedHashSet<String> distinct = new java.util.LinkedHashSet<>();
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (ICraftingPatternDetails d : details) {
                if (d == null) continue;
                IAEItemStack[] outs = d.getOutputs();
                if (outs == null || outs.length == 0 || outs[0] == null) continue;
                String name = nameOf(outs[0]);
                distinct.add(name);
                if (shown < sample) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(name)
                        .append("(craftable=")
                        .append(d.isCraftable())
                        .append(",prio=")
                        .append(d.getPriority())
                        .append(')');
                    shown++;
                }
            }
            return "输出种类=" + distinct.size() + " 样本=[" + sb + "]";
        } catch (Throwable t) {
            return "输出描述失败: " + t;
        }
    }

    /**
     * 注册动作**之后**回读 AE2 的合成表，确认 AE 到底收录了多少、抽样输出能否查到。
     *
     * <p>刻意排到服务端 tick 队列（本模组既有手法）：AE2 的 `CraftingGridCache.updatePatterns()` 会在
     * 所有 provider 的 `provideCrafting` 返回之后才 `setPatternsFromCraftingMethods()`，同一 tick 内立即
     * 查询可能读到旧表。
     */
    public static void scheduleAeReadBack(Object machine, List<ICraftingPatternDetails> details, String family) {
        try {
            final List<ICraftingPatternDetails> snapshot = details == null ? null : new java.util.ArrayList<>(details);
            com.wztwzt.ae2_qof.network.ServerTerminalHelper.scheduleServerTask(() -> readBack(machine, snapshot, family));
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] {} 诊断：AE 回读排队失败", family, t);
        }
    }

    private static void readBack(Object machine, List<ICraftingPatternDetails> details, String family) {
        try {
            if (machine == null) return;
            TileEntity te = baseTileOf(machine);
            if (te == null) {
                MyMod.LOG.warn("[AE2QoL] {} 诊断：拿不到机器的 TileEntity，无法回读 AE", family);
                return;
            }
            IGrid grid = gridOf(te);
            if (grid == null) {
                MyMod.LOG.warn("[AE2QoL] {} 诊断：取不到 AE 网格（机器自身与邻接都没找到节点），无法回读 AE", family);
                return;
            }
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            if (crafting == null) {
                MyMod.LOG.warn("[AE2QoL] {} 诊断：网格里没有 ICraftingGrid 缓存", family);
                return;
            }
            java.util.Map<IAEItemStack, ?> table = crafting.getCraftingPatterns();
            int tableSize = table == null ? -1 : table.size();
            int checked = 0;
            int found = 0;
            StringBuilder sb = new StringBuilder();
            if (details != null) {
                for (ICraftingPatternDetails d : details) {
                    if (checked >= 3) break;
                    IAEItemStack[] outs = d == null ? null : d.getOutputs();
                    if (outs == null || outs.length == 0 || outs[0] == null) continue;
                    boolean hit = table != null && table.containsKey(outs[0]);
                    if (hit) found++;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(nameOf(outs[0]))
                        .append(hit ? "→AE已收录" : "→AE查不到");
                    checked++;
                }
            }
            MyMod.LOG.info(
                "[AE2QoL] {} 诊断：AE 合成表条目={}；抽样 {} 条，命中 {} 条 [{}]，介质={}",
                family,
                tableSize,
                checked,
                found,
                sb,
                machine.getClass()
                    .getSimpleName());
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] " + family + " 诊断：AE 回读异常", t);
        }
    }

    private static String nameOf(IAEItemStack stack) {
        try {
            net.minecraft.item.ItemStack is = stack.getItemStack();
            return is == null ? "?" : String.valueOf(is.getDisplayName());
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 机器本体 → 它的 TileEntity（GT MTE 的 getBaseMetaTileEntity；普通 TE 直接用）。 */
    private static TileEntity baseTileOf(Object machine) {
        if (machine instanceof TileEntity te) return te;
        try {
            if (machine instanceof gregtech.api.interfaces.metatileentity.IMetaTileEntity mte) {
                return (TileEntity) mte.getBaseMetaTileEntity();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 取机器所在网格：先问机器自己的节点，再找邻接（与库存终端同一套取网手法）。 */
    private static IGrid gridOf(TileEntity te) {
        try {
            if (te instanceof IGridHost host) {
                for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                    IGridNode node = host.getGridNode(dir);
                    if (node != null && node.getGrid() != null) return node.getGrid();
                }
            }
        } catch (Throwable ignored) {}
        try {
            return com.wztwzt.ae2_qof.cover.stockmonitor.ae.NeighborAeConnector
                .findGrid(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord, ForgeDirection.UNKNOWN);
        } catch (Throwable ignored) {}
        return null;
    }
}
