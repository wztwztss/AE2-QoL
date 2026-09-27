package com.wztwzt.ae2_qof.generator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.wztwzt.ae2_qof.MyMod;

import gregtech.api.recipe.RecipeMap;

/**
 * RecipeMap 的**显示名**与**候选/对照表**数据源（3.20.0-fix44）。
 *
 * <h2>为什么需要它</h2>
 * GT 的 {@code RecipeMap} 只暴露 {@code public final String unlocalizedName}（如 {@code gt.recipe.rolling}），
 * **没有显示名 getter**。玩家现在要手打技术关键字才能选机器（"输入机器 id 编码太麻烦了"）⇒
 * 本类提供："中文名 / 英文名 / 关键字"三元组，供**Tab 循环补全**与**对照表页**共用。
 *
 * <h2>名字从哪来（诚实说明）</h2>
 * <ul>
 * <li><b>中文名</b>：{@link StatCollector#translateToLocal(String, Object...)} —— 走客户端当前语言
 * （用户客户端是中文 ⇒ 直接得到中文名）；查不到时回落关键字尾巴（并记一次日志）；</li>
 * <li><b>英文名</b>：从资源包读 {@code gregtech:lang/en_US.lang}（GTNH 会用自己资源包覆盖 GT 的同名文件）
 * 解析出的键值表，**只读一次并缓存**；读不到时回落"关键字尾巴转成 Title Case"（例如
 * {@code electricimplosioncompressor} → {@code Electric Implosion Compressor}）——GT jar 里这类键很少，
 * 所以这条回落是常态而不是异常，**首次回落会记一条 INFO 说明**（本项目不许静默降级）。</li>
 * </ul>
 *
 * <p>所有对外方法都不抛异常；枚举 RecipeMap 失败时返回空表并记 WARN。
 */
public final class RecipeMapNames {

    private RecipeMapNames() {}

    /** 一条对照表记录（三元组）。 */
    public static final class Entry {

        public final String id;
        public final String zh;
        public final String en;

        Entry(String id, String zh, String en) {
            this.id = id;
            this.zh = zh;
            this.en = en;
        }
    }

    private static Map<String, String> englishNames;
    private static boolean englishFallbackLogged;

    /** 取英文名（读不到资源时回落"尾巴转 Title Case"）。 */
    public static String englishName(String unlocalizedName) {
        if (unlocalizedName == null) return "";
        Map<String, String> map = englishMap();
        String value = map.get(unlocalizedName);
        if (value != null && !value.isEmpty()) return value;
        if (!englishFallbackLogged) {
            englishFallbackLogged = true;
            MyMod.LOG.info(
                "[AE2QoL] 未能从资源包读到 {} 的英文名，改用关键字生成（例：{} → {}）——此提示只记一次",
                unlocalizedName,
                unlocalizedName,
                tailToTitleCase(unlocalizedName));
        }
        return tailToTitleCase(unlocalizedName);
    }

    /** 取中文名（当前语言；查不到时回落英文名）。 */
    public static String chineseName(String unlocalizedName) {
        if (unlocalizedName == null) return "";
        try {
            String zh = StatCollector.translateToLocal(unlocalizedName);
            if (zh != null && !zh.isEmpty() && !zh.equals(unlocalizedName)) return zh;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 读取 RecipeMap 中文名失败（回落英文名）", t);
        }
        return englishName(unlocalizedName);
    }

    /** 全部 RecipeMap 的三元组，按中文名（相同再按关键字）排序。 */
    public static List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        try {
            for (Map.Entry<String, RecipeMap<?>> entry : RecipeMap.ALL_RECIPE_MAPS.entrySet()) {
                String id = entry.getKey();
                if (id == null || id.isEmpty()) continue;
                list.add(new Entry(id, chineseName(id), englishName(id)));
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 枚举 RecipeMap 失败（对照表为空）", t);
        }
        Collections.sort(list, (a, b) -> {
            int byZh = String.valueOf(a.zh)
                .compareToIgnoreCase(String.valueOf(b.zh));
            return byZh != 0 ? byZh : a.id.compareToIgnoreCase(b.id);
        });
        return list;
    }

    /**
     * 按片段匹配候选（**关键字 / 中文名 / 英文名 三者任一**，不区分大小写子串）。
     *
     * <p>排序：完全相等 &gt; 前缀命中 &gt; 子串命中；同级按对照表顺序（即中文名序）。
     * 片段为空 ⇒ 返回整表的 id 顺序（Tab 循环用）。
     */
    public static List<String> matchIds(String fragment) {
        List<Entry> table = entries();
        String needle = fragment == null ? "" : fragment.trim()
            .toLowerCase();
        List<String> exact = new ArrayList<>();
        List<String> prefix = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (Entry e : table) {
            if (needle.isEmpty()) {
                contains.add(e.id);
                continue;
            }
            String id = e.id.toLowerCase();
            String zh = e.zh == null ? "" : e.zh.toLowerCase();
            String en = e.en == null ? "" : e.en.toLowerCase();
            if (id.equals(needle) || zh.equals(needle) || en.equals(needle)) {
                exact.add(e.id);
            } else if (id.startsWith(needle) || zh.startsWith(needle) || en.startsWith(needle)) {
                prefix.add(e.id);
            } else if (id.contains(needle) || zh.contains(needle) || en.contains(needle)) {
                contains.add(e.id);
            }
        }
        List<String> out = new ArrayList<>(exact.size() + prefix.size() + contains.size());
        out.addAll(exact);
        out.addAll(prefix);
        out.addAll(contains);
        return out;
    }

    /** 取关键字尾巴并转 Title Case（{@code gt.recipe.electricimplosioncompressor} → {@code Electric Implosion Compressor}）。 */
    static String tailToTitleCase(String unlocalizedName) {
        if (unlocalizedName == null) return "";
        String tail = unlocalizedName;
        int dot = tail.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < tail.length()) tail = tail.substring(dot + 1);
        StringBuilder sb = new StringBuilder(tail.length() + 8);
        boolean upper = true;
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (c == '_' || c == '.' || c == '-') {
                sb.append(' ');
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    /** 只读一次的 en_US 键值表（{@code gregtech:lang/en_US.lang}）。 */
    private static synchronized Map<String, String> englishMap() {
        if (englishNames != null) return englishNames;
        Map<String, String> map = new HashMap<>();
        try {
            IResource resource = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(new ResourceLocation("gregtech", "lang/en_US.lang"));
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq)
                        .trim();
                    if (!key.startsWith("gt.recipe.")) continue;
                    map.put(key, line.substring(eq + 1)
                        .trim());
                }
            }
            MyMod.LOG.info("[AE2QoL] 已载入 {} 条 RecipeMap 英文名（gregtech:lang/en_US.lang）", map.size());
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 读取 gregtech:lang/en_US.lang 失败（英文名改用关键字生成）", t);
        }
        englishNames = map;
        return englishNames;
    }
}
