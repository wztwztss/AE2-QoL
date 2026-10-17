package com.wztwzt.ae2_qof.proline;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 4.2.0：中文名→注册键解析器（**客户端侧**，因为专用服语言表是英文）。
 *
 * <h2>为什么放客户端</h2>
 * 1.7.10 专用服没有客户端语言表，`ItemStack.getDisplayName()` 返回英文本地化名。
 * 中文 wiki 名（如"粉碎的独居石矿石"）只有在**客户端**语言环境下才能匹配到物品。
 * 所以解析流程 = 客户端建映射 → 新 C2S 包发映射 → 服务端回填 ItemRow.key。
 *
 * <h2>匹配规则（按优先级）</h2>
 * <ol>
 * <li>display name **精确匹配**（去掉空格差异后忽略大小写）</li>
 * <li>display name **包含匹配**（raw 名包含在 display name 中，或反之——用于"热铪锭"匹配"热铪锭"等）</li>
 * <li>流体：display name 匹配（fluid.getDisplayName 或 fluid.getLocalizedName）</li>
 * </ol>
 *
 * <h2>使用方式</h2>
 * 客户端打开产线页时调用 {@link #resolveAndSend(String, ProductionLine)}：
 * 扫描该产线的所有空 key 条目 → 批量解析 → 有结果的打包发包。
 */
@SideOnly(Side.CLIENT)
public final class ProLineNameResolver {

    /** display name → key 缓存（懒建，一次全表扫描后复用）。 */
    private static Map<String, String> itemByName;
    private static Map<String, String> fluidByName;

    private ProLineNameResolver() {}

    /** 全表扫描建立 display name → key 映射（客户端调用一次后缓存）。 */
    private static void ensureIndex() {
        if (itemByName != null) return;
        itemByName = new HashMap<>();
        fluidByName = new HashMap<>();
        try {
            for (Object obj : Item.itemRegistry) {
                Item item = (Item) obj;
                ItemStack probe = new ItemStack(item, 1, 0);
                if (probe.getItem() == null) continue;
                // 对有子类型的物品扫描 meta 0..15（GT 多方块 meta 很多，但 display name 一样时只取第一个）
                String name = probe.getDisplayName();
                if (name != null && !name.isEmpty()) {
                    itemByName.put(normalize(name), ItemRow.keyOf(probe));
                }
            }
            for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
                if (fluid == null) continue;
                String name = fluid.getLocalizedName(new FluidStack(fluid, 1000));
                if (name != null && !name.isEmpty()) {
                    fluidByName.put(normalize(name), "fluid:" + fluid.getName());
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 名称解析索引构建异常", t);
        }
        MyMod.LOG.info("[AE2QoL] 名称解析索引：{} 物品 + {} 流体", itemByName.size(), fluidByName.size());
    }

    /** 归一化：去空格 + 小写（中文名里的全角空格等也算空格）。 */
    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace(" ", "")
            .replace("\u3000", "")
            .toLowerCase();
    }

    /**
     * 解析单个 raw 名 → key（精确优先，其次包含）。返回 "" = 未解析。
     */
    public static String resolve(String raw) {
        ensureIndex();
        if (raw == null || raw.isEmpty()) return "";
        String norm = normalize(raw);
        // 流体优先（流体名一般较短且不含 meta）
        String hit = fluidByName.get(norm);
        if (hit != null) return hit;
        hit = itemByName.get(norm);
        if (hit != null) return hit;
        // 包含匹配：raw 包含在 display name 里（如"独居石"匹配"粉碎的独居石矿石"）
        for (Map.Entry<String, String> e : itemByName.entrySet()) {
            if (e.getKey().contains(norm) && norm.length() >= 2) {
                return e.getValue();
            }
        }
        for (Map.Entry<String, String> e : fluidByName.entrySet()) {
            if (e.getKey().contains(norm) && norm.length() >= 2) {
                return e.getValue();
            }
        }
        return "";
    }

    /**
     * 客户端入口：产线页打开时扫描产线的所有空 key 条目 → 解析 → 发包。
     * 返回解析成功的 raw→key 对数（0 = 无需解析或无结果）。
     */
    public static int resolveAndSend(String lineId, ProductionLine line) {
        if (line == null || line.steps.isEmpty()) return 0;
        ensureIndex();
        // 收集所有待解析的 raw 名
        Set<String> pending = new HashSet<>();
        for (ProductionStep s : line.steps) {
            for (ItemRow r : s.inputs) {
                if (r.key.isEmpty() && r.raw != null && !r.raw.isEmpty()) pending.add(r.raw);
            }
            for (ItemRow r : s.outputs) {
                if (r.key.isEmpty() && r.raw != null && !r.raw.isEmpty()) pending.add(r.raw);
            }
        }
        for (String raw : line.startExpected.keySet()) {
            if (!raw.startsWith("fluid:") && !raw.contains(":")) pending.add(raw);
        }
        if (pending.isEmpty()) return 0;

        // 解析
        Map<String, String> resolved = new HashMap<>();
        for (String raw : pending) {
            String key = resolve(raw);
            if (!key.isEmpty()) resolved.put(raw, key);
        }
        if (resolved.isEmpty()) return 0;

        // 发包给服务端
        try {
            com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL.sendToServer(
                new com.wztwzt.ae2_qof.proline.network.ProLineResolvePacket(lineId, resolved));
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 名称解析发包失败", t);
            return 0;
        }
        return resolved.size();
    }
}
