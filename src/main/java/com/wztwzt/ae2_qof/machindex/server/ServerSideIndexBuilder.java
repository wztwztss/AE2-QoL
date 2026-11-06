package com.wztwzt.ae2_qof.machindex.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.machindex.common.IndexModel;
import com.wztwzt.ae2_qof.machindex.common.MachineCategory;
import com.wztwzt.ae2_qof.machindex.common.ModSourceResolver;
import com.wztwzt.ae2_qof.machindex.common.QuestAggregator;
import com.wztwzt.ae2_qof.machindex.common.VoltageResolver;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;

/**
 * 服务端兜底索引（「B 轨」：不依赖 NEI 自建索引）。
 *
 * <h2>为什么需要它</h2>
 * NEI 只在客户端加载，专用服务器没有 {@code RecipeCatalysts} / {@code IRecipeHandler}。
 * 正常情况下客户端扫完会通过 {@code MachIndexSyncPacket} 把结果发给服务端；
 * 但如果<b>服务端独跑</b>（没有任何客户端连上来、或客户端没装本模组），
 * 就没人发数据 —— 于是用本类在服务端直接枚举 GT 注册表兜底。
 *
 * <h2>覆盖范围与代价</h2>
 * 只能枚举 <b>GT 注册表里的多方块</b>（{@code GregTechAPI.METATILEENTITIES}），
 * 第三方模组（GTNG/Apeiron 等）自己注册的 MTE 若不在 GT 表里就会漏 —— 这是 B 轨的已知边界，
 * 所以它是「兜底」而不是主路径。
 *
 * <h2>分类来源</h2>
 * 服务端拿不到 NEI 的催化剂表，但有 {@code MTEMultiBlockBase.getRecipeMap()}
 * （GTNG 之类还会暴露 {@code getAvailableRecipeMaps()}，用反射取）；把这些配方表名
 * 交给 {@link MachineCategory} 归类，覆盖面足够。
 */
public final class ServerSideIndexBuilder {

    private ServerSideIndexBuilder() {}

    /**
     * 在服务端构建一份索引结果。
     *
     * @return 扫描结果；失败返回 null
     */
    public static IndexModel.ScanResult build() {
        long start = System.currentTimeMillis();
        IndexModel.ScanResult result = new IndexModel.ScanResult();
        try {
            IMetaTileEntity[] all = GregTechAPI.METATILEENTITIES;
            if (all == null) {
                MyMod.LOG.warn("[AE2QoL] machindex 服务端兜底：METATILEENTITIES 不可读");
                return null;
            }

            Map<String, IndexModel.MachineEntry> byIdentity = new LinkedHashMap<>();
            Set<String> typeIds = new LinkedHashSet<>();
            int scanned = 0;

            for (IMetaTileEntity raw : all) {
                if (!(raw instanceof MTEMultiBlockBase)) continue;
                MTEMultiBlockBase mb = (MTEMultiBlockBase) raw;
                scanned++;

                ItemStack stack;
                try {
                    stack = mb.getStackForm(1);
                } catch (Throwable t) {
                    continue;
                }
                if (stack == null) continue;

                IndexModel.MachineEntry e = new IndexModel.MachineEntry();
                e.registryName = registryNameOf(stack);
                e.meta = stack.getItemDamage();
                e.nbtHash = "";
                e.displayStack = stack.copy();
                e.displayName = VoltageResolver.displayNameOf(stack);
                e.isMultiblock = true;

                ModSourceResolver.resolve(stack, e);
                VoltageResolver.resolve(stack, e);

                // 分类依据：该机器暴露的配方表名（GT 基类只有一张；GTNG 等有 getAvailableRecipeMaps）
                for (String mapName : recipeMapNames(mb)) {
                    e.typeIds.add(mapName);
                    typeIds.add(mapName);
                }
                if (e.typeIds.isEmpty()) {
                    e.note = VoltageResolver.appendNote(e.note, "服务端兜底：无可用配方表");
                }

                byIdentity.putIfAbsent(e.identityKey(), e);
            }

            // 类型清单：服务端有 en_US 语言文件，StatCollector 可用
            for (String typeId : typeIds) {
                IndexModel.RecipeTypeIndex t = new IndexModel.RecipeTypeIndex();
                t.typeId = typeId;
                String localized = net.minecraft.util.StatCollector.translateToLocal(typeId);
                t.displayName = localized == null || localized.isEmpty() || localized.equals(typeId) ? typeId
                    : localized;
                result.types.add(t);
            }

            result.machines.addAll(byIdentity.values());
            result.quests.addAll(QuestAggregator.aggregate(result.machines, result.types));
            result.stats.addAll(QuestAggregator.buildStats(result, System.currentTimeMillis() - start));

            StringBuilder sb = new StringBuilder();
            for (String s : result.stats) {
                if (sb.length() > 0) sb.append("；");
                sb.append(s);
            }
            MyMod.LOG.info("[AE2QoL] machindex 服务端兜底索引完成（多方块扫描 " + scanned + "）：" + sb);
            return result;
        } catch (Throwable t) {
            MyMod.LOG.error("[AE2QoL] machindex 服务端兜底索引失败", t);
            return null;
        }
    }

    /** 取该多方块暴露的配方表名（GT 基类一张；有 getAvailableRecipeMaps 的取全部）。 */
    private static Collection<String> recipeMapNames(MTEMultiBlockBase mb) {
        List<String> out = new ArrayList<>();
        try {
            java.lang.reflect.Method m = mb.getClass()
                .getMethod("getAvailableRecipeMaps");
            Object raw = m.invoke(mb);
            if (raw instanceof Collection) {
                for (Object o : (Collection<?>) raw) {
                    if (o instanceof RecipeMap) {
                        String n = ((RecipeMap<?>) o).unlocalizedName;
                        if (n != null && !n.isEmpty()) out.add(n);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 没有该方法 = 单一配方表，走下面的 getRecipeMap()
        }
        if (out.isEmpty()) {
            try {
                RecipeMap<?> map = mb.getRecipeMap();
                if (map != null && map.unlocalizedName != null) out.add(map.unlocalizedName);
            } catch (Throwable ignored) {
                // 取不到就不加类型，条目仍保留（归「其他」）
            }
        }
        return out;
    }

    private static String registryNameOf(ItemStack stack) {
        try {
            String n = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
            if (n != null) return n;
        } catch (Throwable ignored) {
            // 回落
        }
        return stack.getItem()
            .getUnlocalizedName();
    }

    /** 该机器归到哪个大类（供命令/诊断）。 */
    public static String categoryOf(String typeId, String displayName) {
        return MachineCategory.resolve(typeId, displayName);
    }
}
