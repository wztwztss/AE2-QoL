package com.wztwzt.ae2_qof.proline;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wztwzt.ae2_qof.MyMod;

/**
 * huijiwiki RecipeCalculator JSON 导入器（设计 §2.2/§4.6，A5 拍板的落法）。
 *
 * <h2>输入约定</h2>
 * <ul>
 * <li>物品以 {@code key} 串承载（{@code modid:name[@meta]} 或 {@code fluid:名}）——
 * <b>名称→key 的解析发生在客户端</b>（专用服语言表是英文，中文解析必须用客户端语言表，A2/A5）；
 * 客户端解析失败的条目会把 {@code name} 原样保留、key 留空——导入并不静默丢弃，逐项标红；</li>
 * <li>type 同理：解析成功时客户端写入 {@code machineType=<稳定键>}；解析不了保留原文本（显示用）；</li>
 * <li>branch/hierarchy 原样收录（§2.2）；start[] 进不了任何步骤，直接并进"总输入"清单（作为一行纯计数）。</li>
 * </ul>
 */
public final class ProLineImporter {

    public static final class Result {

        public final ProductionLine line;
        public final List<String> errors = new ArrayList<>();

        Result(ProductionLine line) {
            this.line = line;
        }
    }

    /** wiki amount 缺省=1 的步骤要标"理论量缺省（按 1）"（§十一开放 3）——放在 steps 的 name 后缀太吵，改在导入报告里集中提示。 */
    public static List<String> defaultAmountKeys = new ArrayList<>();

    public static Result parse(String json) {
        defaultAmountKeys.clear();
        ProductionLine line = new ProductionLine();
        Result result = new Result(line);
        try {
            // 玩家会原样粘贴 wiki 模板：剥掉 {{RecipeCalculator|...}} 模板头尾与 <!-- --> 注释（huijiwiki 实测形态）
            json = sanitize(json);
            JsonElement root = new JsonParser().parse(json);
            if (!root.isJsonObject()) {
                result.errors.add("顶层必须是 JSON 对象");
                return result;
            }
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("name") && obj.get("name")
                .isJsonPrimitive()) {
                line.name = obj.get("name")
                    .getAsString();
            }
            // ---- huijiwiki RecipeCalculator 原始格式（设计 §2.2：recipeQuery/branches/hierarchy/start/env）----
            // 名称→key 的解析：单人（集成服端）走客户端语言表可解析中文；专用服解析不了会逐条报"未解析"。
            if (obj.has("recipeQuery") && obj.get("recipeQuery")
                .isJsonArray()) {
                JsonArray steps = obj.getAsJsonArray("recipeQuery");
                for (JsonElement el : steps) {
                    if (!el.isJsonObject()) continue;
                    JsonObject st = el.getAsJsonObject();
                    ProductionStep ps = new ProductionStep();
                    ps.name = jString(st, "name");
                    ps.machineType = jString(st, "type");
                    ps.branch = jStringOrFirst(st, "branch");
                    ps.hierarchy = jStringOrFirst(st, "hierarchy");
                    fillRows(tokenArray(st.get("input")), ps.inputs, result);
                    fillRows(tokenArray(st.get("output")), ps.outputs, result);
                    line.steps.add(ps);
                }
            }
            if (obj.has("start") && obj.get("start")
                .isJsonArray() && !obj.has("steps")) {
                JsonArray start = obj.getAsJsonArray("start");
                for (JsonElement el : start) {
                    if (el.isJsonPrimitive()) {
                        // 形如 "粉碎的独居石矿石 64"：最后一个空格分隔的整数为数量，缺省 1
                        String token = el.getAsString()
                            .trim();
                        String name = token;
                        long amount = 1L;
                        int sp = token.lastIndexOf(' ');
                        if (sp > 0) {
                            try {
                                amount = Long.parseLong(token.substring(sp + 1));
                                name = token.substring(0, sp);
                            } catch (NumberFormatException ignored) {}
                        }
                        ProductionStep ps = ensureStartStep(line);
                        ps.inputs.add(new ItemRow("", amount, false));
                        ps.inputs.get(ps.inputs.size() - 1).key = "";
                        result.errors.add("start 名称未解析成注册键：" + name + "（数量 " + amount + "）");
                    } else if (el.isJsonObject()) {
                        JsonObject st = el.getAsJsonObject();
                        if (st.has("name")) {
                            long amount = st.has("amount") ? jLong(st, "amount") : 1L;
                            result.errors.add("start 名称未解析成注册键：" + jString(st, "name") + "（数量 " + amount + "）");
                        } else {
                            fillRows(
                                st.has("input") ? st.get("input") : new JsonArray(),
                                ensureStartStep(line).inputs,
                                result);
                        }
                    }
                }
            }
            if (obj.has("env") && obj.get("env")
                .isJsonArray()) {
                // 循环介质：v1 记进"起点记账"步骤（参与口径采样；无告警逻辑故无需豁免标记）
                for (JsonElement el : obj.getAsJsonArray("env")) {
                    if (!el.isJsonPrimitive()) continue;
                    ProductionStep ps = ensureStartStep(line);
                    ps.inputs.add(new ItemRow("", 0L, false));
                    result.errors.add("env 循环介质未解析成注册键：" + el.getAsString());
                }
            }
            if (obj.has("steps") && obj.get("steps")
                .isJsonArray()) {
                // 内部精简格式（客户端打包后的产物）：steps[{id?,name,type,branch,hier,in:[{key,amount,fluid}],out:[…]}]
                JsonArray steps = obj.getAsJsonArray("steps");
                for (JsonElement el : steps) {
                    if (!el.isJsonObject()) continue;
                    JsonObject st = el.getAsJsonObject();
                    ProductionStep ps = new ProductionStep();
                    ps.name = jString(st, "name");
                    ps.machineType = jString(st, "type");
                    ps.branch = jString(st, "branch");
                    ps.hierarchy = jString(st, "hier");
                    fillRows(st.get("in"), ps.inputs, result);
                    fillRows(st.get("out"), ps.outputs, result);
                    line.steps.add(ps);
                }
            }
            if (obj.has("start") && obj.get("start")
                .isJsonArray()) {
                // start[] 记号条目：key 有解析好的就带进去（没有则记未解析——不静默）
                JsonArray start = obj.getAsJsonArray("start");
                for (JsonElement el : start) {
                    JsonObject st = el.isJsonObject() ? el.getAsJsonObject() : null;
                    String key = st == null ? "" : jString(st, "key");
                    long amount = st != null && st.has("amount") ? jLong(st, "amount") : 1L;
                    boolean fluid = key.startsWith("fluid:");
                    if (key.isEmpty()) {
                        result.errors.add("start[] 有条目没有解析成注册键：" + (st == null ? el.toString() : el.toString()));
                        continue;
                    }
                    // start 条目挂到一个专门的记账步骤里（不绑机器，仅参与总输入采样口径）
                    ProductionStep ps = ensureStartStep(line);
                    ps.inputs.add(new ItemRow(key, amount, fluid));
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] wiki JSON 解析失败", t);
            result.errors.add("解析异常：" + t);
        }
        return result;
    }

    private static ProductionStep ensureStartStep(ProductionLine line) {
        for (ProductionStep s : line.steps) {
            if ("（起点记账）".equals(s.name)) return s;
        }
        ProductionStep ps = new ProductionStep();
        ps.name = "（起点记账）";
        line.steps.add(ps);
        return ps;
    }

    /**
     * 粘贴净处理（huijiwiki 实测形态，2026-10-16 用户提供独居石主线原文）：
     * ①剥 {@code <!-- -->}（wiki 注释会被 Gson 判错）；②若带 {@code {{RecipeCalculator|...}}}
     * 模板头，从 "recipeQuery"/"groups" 锚点回退到真 JSON 的根 '{'；③尾部模板 '} }}' 残留逐个剥到可解析。
     */
    public static String sanitize(String in) {
        if (in == null) return "";
        in = in.replaceAll("(?s)<!--.*?-->", "")
            .replaceAll("(?s)/\\*.*?\\*/", "");
        String probe = null;
        int a = in.indexOf("\"recipeQuery\"");
        int b = in.indexOf("\"groups\"");
        int anchor = Math.min(a == -1 ? Integer.MAX_VALUE : a, b == -1 ? Integer.MAX_VALUE : b);
        if (anchor != Integer.MAX_VALUE) {
            probe = in.substring(in.lastIndexOf('{', anchor));
        }
        if (probe != null) in = probe.trim();
        // 尾部模板残留：多剥一个 '}' 重试（最多 3 次）
        for (int i = 0; i < 3; i++) {
            try {
                new JsonParser().parse(in);
                return in;
            } catch (Throwable t) {
                int e = in.lastIndexOf('}');
                if (e > 0) in = in.substring(0, e);
                else return in;
            }
        }
        return in;
    }

    /** branch/hierarchy 在 wiki 里常是 ["名字"] 数组：取首元素；字符串形态直接用。 */
    private static String jStringOrFirst(JsonObject o, String k) {
        if (!o.has(k)) return "";
        JsonElement el = o.get(k);
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonArray() && el.getAsJsonArray()
            .size() > 0) {
            JsonElement f = el.getAsJsonArray()
                .get(0);
            if (f.isJsonPrimitive()) return f.getAsString();
        }
        return "";
    }

    /** wiki 的 input/output 可能是数组，也可能是逗号分隔串；统一折成 JSON 数组，元素为 "名字 数量" 记号。 */
    private static JsonArray tokenArray(JsonElement raw) {
        JsonArray arr = new JsonArray();
        if (raw == null) return arr;
        if (raw.isJsonArray()) return raw.getAsJsonArray();
        if (raw.isJsonPrimitive()) {
            for (String token : raw.getAsString()
                .split(",")) {
                if (!token.trim()
                    .isEmpty()) arr.add(new com.google.gson.JsonPrimitive(token.trim()));
            }
        }
        return arr;
    }

    private static void fillRows(JsonElement rowsEl, List<ItemRow> into, Result result) {
        if (rowsEl == null || !rowsEl.isJsonArray()) return;
        for (JsonElement el : rowsEl.getAsJsonArray()) {
            // 两种元素形态：{key,raw,amount}（客户端已解析）或 "名字 数量" 记号串（wiki 原始 token）
            JsonObject r = el.isJsonObject() ? el.getAsJsonObject() : null;
            String key = r == null ? "" : jString(r, "key");
            String raw = r == null ? (el.isJsonPrimitive() ? el.getAsString()
                .trim() : "") : jString(r, "raw");
            long amount = r != null && r.has("amount") ? jLong(r, "amount") : 1L;
            if (r == null && !raw.isEmpty()) {
                // 最后一个空格分隔的整数当数量（缺省 1）
                int sp = raw.lastIndexOf(' ');
                if (sp > 0) {
                    try {
                        amount = Long.parseLong(
                            raw.substring(sp + 1)
                                .trim());
                        raw = raw.substring(0, sp);
                    } catch (NumberFormatException ignored) {}
                }
            }
            boolean fluid = key.startsWith("fluid:");
            if (key.isEmpty()) {
                result.errors.add("未解析成注册键：" + (raw.isEmpty() ? "<空>" : raw));
            }
            if (r == null || !r.has("amount")) {
                defaultAmountKeys.add(raw);
            }
            into.add(new ItemRow(key, amount, fluid));
        }
    }

    private static String jString(JsonObject o, String k) {
        return o.has(k) && o.get(k)
            .isJsonPrimitive() ? o.get(k)
                .getAsString() : "";
    }

    private static long jLong(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k)
            .isJsonPrimitive()) return 1L;
        try {
            return Long.parseLong(
                o.get(k)
                    .getAsString());
        } catch (NumberFormatException numberFormatException) {
            try {
                return o.get(k)
                    .getAsLong();
            } catch (Throwable t) {
                return 1L;
            }
        }
    }

    public NBTTagCompound writeNbt(NBTTagCompound tag) {
        return tag;
    }

    public BigInteger unusedPlaceholder() {
        return BigInteger.ZERO;
    }
}
