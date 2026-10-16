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

    private static void fillRows(JsonElement rowsEl, List<ItemRow> into, Result result) {
        if (rowsEl == null || !rowsEl.isJsonArray()) return;
        for (JsonElement el : rowsEl.getAsJsonArray()) {
            JsonObject r = el.isJsonObject() ? el.getAsJsonObject() : null;
            if (r == null) continue;
            String key = jString(r, "key");
            String raw = jString(r, "raw");
            long amount = r.has("amount") ? jLong(r, "amount") : 1L;
            if (r.has("amount")) {} else {
                defaultAmountKeys.add(raw == null || raw.isEmpty() ? key : raw);
            }
            boolean fluid = key.startsWith("fluid:");
            if (key.isEmpty()) {
                result.errors.add("未解析成注册键：" + (raw.isEmpty() ? "<空>" : raw));
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
