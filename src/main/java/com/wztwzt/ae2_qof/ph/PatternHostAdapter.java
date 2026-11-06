package com.wztwzt.ae2_qof.ph;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.wztwzt.ae2_qof.mixin.ph.MixinPatternDualInputHatchAccess;
import com.wztwzt.ae2_qof.mixin.ph.MixinPatternDualInputHatchMappingSlaveAccess;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import reobf.proghatches.gt.metatileentity.PatternDualInputHatch;
import reobf.proghatches.gt.metatileentity.PatternDualInputHatchInventoryMappingSlave;

/**
 * 样板总成的统一读写适配层（4.3.0）。
 *
 * <h2>背景：PH 家族其实有两大支系</h2>
 * <ul>
 * <li><b>主流支系</b>：{@code PatternDualInputHatch} 及其全部子类 —— PH 自带的三种变体
 * （原版总成 / MK.II / 仅物品）、{@code SuperPatternDualInputHatch}（超级总成，继承字段，
 * {@code setPattern} 写的就是父类 {@code pattern[]}）、以及本模组的 MK.III / MK.IV。
 * 它们共享同一组字段与 {@code onPatternChange()}，一个 accessor 全覆盖。</li>
 * <li><b>从属支系</b>：{@code PatternDualInputHatchInventoryMappingSlave} —— 独立类
 * （不继承主流支系），但样板四件套字段同名同义，有自己的 {@code onPatternChange()}。</li>
 * </ul>
 *
 * <h2>纪律</h2>
 * 本类引用了 PH 类型，**只能**在 {@code PhIntegration} 的 PH 守卫通过之后被加载
 * （与 {@code ItemPatternClipboard} / MK.III / MK.IV 同一套纪律）。
 *
 * <p>
 * 写入统一收尾：清 itemCache / detailCache → {@code onPatternChange()}（置 needPatternSync，
 * 下次机器更新时重新向 AE 注册样板）→ TE {@code markDirty()}（保证存盘）→
 * {@code PatternClientSync} 下发区块跟踪者（样板窗立即刷新）。refresh() 由调用方按需追加。
 */
public abstract class PatternHostAdapter {

    private PatternHostAdapter() {}

    /** 绑定到某个具体 MTE 的读写句柄。 */
    public interface Host {

        /** 机器显示名（日志/提示用）。 */
        String hostName();

        /** 读全部样板槽（可能与 multiplier 等长）。 */
        ItemStack[] readPatterns();

        /** 读每槽倍率（无则长度 0）。 */
        int[] readMultiplier();

        /**
         * 整机写入样板 + 倍率（覆盖语义，数组长度即槽位数）。
         * 实现负责清缓存、发变更通知、markDirty 与客户端同步。
         */
        void writeAll(ItemStack[] patterns, int[] multiplier);
    }

    /** 该 MTE 是否被本适配层支持。 */
    public abstract boolean matches(Object mte);

    /** 包装成读写句柄；调用方需先用 {@link #matches} 判定。 */
    public abstract Host bind(Object mte);

    /** 人类可读的支系名（GUI/日志用）。 */
    public abstract String familyName();

    // ===================== 主流支系：PatternDualInputHatch 全家族 =====================

    public static final PatternHostAdapter DUAL_INPUT = new PatternHostAdapter() {

        @Override
        public boolean matches(Object mte) {
            return mte instanceof PatternDualInputHatch;
        }

        @Override
        public Host bind(Object mte) {
            final PatternDualInputHatch machine = (PatternDualInputHatch) mte;
            final MixinPatternDualInputHatchAccess acc = (MixinPatternDualInputHatchAccess) machine;
            return new Host() {

                @Override
                public String hostName() {
                    return machine.getMetaName();
                }

                @Override
                public ItemStack[] readPatterns() {
                    return acc.getAe2qolPattern();
                }

                @Override
                public int[] readMultiplier() {
                    return acc.getAe2qolMultiplier();
                }

                @Override
                public void writeAll(ItemStack[] patterns, int[] multiplier) {
                    acc.setAe2qolPattern(patterns);
                    acc.setAe2qolMultiplier(multiplier);
                    acc.setAe2qolPatternItemCache(new ItemStack[patterns.length]);
                    acc.setAe2qolPatternDetailCache(
                        new appeng.api.networking.crafting.ICraftingPatternDetails[patterns.length]);
                    acc.invokeAe2qolOnPatternChange();
                    machine.refresh();
                    markDirtyAndSync(machine.getBaseMetaTileEntity(), machine.getMetaName());
                }
            };
        }

        @Override
        public String familyName() {
            return "PatternDualInputHatch";
        }
    };

    // ===================== 从属支系：库存映射从属机 =====================

    public static final PatternHostAdapter MAPPING_SLAVE = new PatternHostAdapter() {

        @Override
        public boolean matches(Object mte) {
            return mte instanceof PatternDualInputHatchInventoryMappingSlave;
        }

        @Override
        public Host bind(Object mte) {
            final PatternDualInputHatchInventoryMappingSlave<?> machine = (PatternDualInputHatchInventoryMappingSlave<?>) mte;
            final MixinPatternDualInputHatchMappingSlaveAccess acc = (MixinPatternDualInputHatchMappingSlaveAccess) machine;
            return new Host() {

                @Override
                public String hostName() {
                    return machine.getMetaName();
                }

                @Override
                public ItemStack[] readPatterns() {
                    return acc.getAe2qolPattern();
                }

                @Override
                public int[] readMultiplier() {
                    return acc.getAe2qolMultiplier();
                }

                @Override
                public void writeAll(ItemStack[] patterns, int[] multiplier) {
                    acc.setAe2qolPattern(patterns);
                    acc.setAe2qolMultiplier(multiplier);
                    acc.setAe2qolPatternItemCache(new ItemStack[patterns.length]);
                    acc.setAe2qolPatternDetailCache(
                        new appeng.api.networking.crafting.ICraftingPatternDetails[patterns.length]);
                    acc.invokeAe2qolOnPatternChange();
                    machine.refresh();
                    markDirtyAndSync(machine.getBaseMetaTileEntity(), machine.getMetaName());
                }
            };
        }

        @Override
        public String familyName() {
            return "MappingSlave";
        }
    };

    /** 全部适配器（顺序即探测顺序）。 */
    public static final PatternHostAdapter[] ALL = { DUAL_INPUT, MAPPING_SLAVE };

    /**
     * 解析某个坐标上的方块实体为受支持的样板宿主；不支持返回 null。
     * 供剪贴板右键使用：GT 方块实体 → MTE → 逐适配器匹配。
     * <p>
     * 4.4.0 防御：bind 里的 accessor 强转在「mixin 未应用」时会抛 ClassCastException
     * （4.3.0 实发过：打包用的 src/main/resources/mixins.ae2_qof.json 漏了
     * MappingSlaveAccess 条目，右键从属机直接把玩家踢下线）。这里按「不受支持」降级并
     * WARN 留痕，绝不让一次失败的强转变成踢人。
     */
    public static Host resolveHost(TileEntity te) {
        if (!(te instanceof IGregTechTileEntity greg)) return null;
        Object mte = greg.getMetaTileEntity();
        if (mte == null) return null;
        for (PatternHostAdapter adapter : ALL) {
            if (!adapter.matches(mte)) continue;
            try {
                return adapter.bind(mte);
            } catch (Throwable t) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn(
                    "[AE2QoL] 样板宿主绑定失败（按不受支持处理）：family={} mte={}：{}",
                    adapter.familyName(),
                    mte.getClass()
                        .getName(),
                    t);
                return null;
            }
        }
        return null;
    }

    private static void markDirtyAndSync(IGregTechTileEntity base, String name) {
        try {
            if (base instanceof TileEntity tile) tile.markDirty();
            PatternClientSync.notifyClients(base, name);
        } catch (Throwable ignored) {}
    }
}
