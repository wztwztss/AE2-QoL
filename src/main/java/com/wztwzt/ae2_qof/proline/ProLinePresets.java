package com.wztwzt.ae2_qof.proline;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wztwzt.ae2_qof.MyMod;

/**
 * 预置产线（2026-10-16 用户拍板："直接预制，不导入"）。
 *
 * <p>
 * 数据源 = `assets/ae2_qof/proline/presets.json`（由 `tools/wiki/presets_extract.mjs`
 * 从 huijiwiki 模板页提取打包）。玩家在终端产线页【预置产线】点选即建线，
 * 之后仍可 NEI 导入/编辑器修改。解析路径与 wiki 粘贴导入完全同源（ProLineImporter）。
 */
public final class ProLinePresets {

    public static final class Preset {

        public String name;
        public String json;
    }

    private static volatile List<Preset> cache;

    private ProLinePresets() {}

    public static List<Preset> all() {
        List<Preset> c = cache;
        if (c != null) return c;
        synchronized (ProLinePresets.class) {
            if (cache != null) return cache;
            List<Preset> list = new ArrayList<>();
            try {
                java.io.InputStream in = ProLinePresets.class
                    .getResourceAsStream("/assets/ae2_qof/proline/presets.json");
                if (in != null) {
                    JsonElement root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
                    if (root.isJsonObject() && root.getAsJsonObject()
                        .has("presets")) {
                        JsonArray arr = root.getAsJsonObject()
                            .getAsJsonArray("presets");
                        for (JsonElement el : arr) {
                            if (!el.isJsonObject()) continue;
                            JsonObject o = el.getAsJsonObject();
                            Preset p = new Preset();
                            p.name = o.has("name") ? o.get("name")
                                .getAsString() : "未命名产线";
                            p.json = o.has("json") ? o.get("json")
                                .getAsString() : "";
                            list.add(p);
                        }
                    }
                }
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 预置产线加载失败", t);
            }
            cache = Collections.unmodifiableList(list);
            return cache;
        }
    }

    /** 点选一条预置 → 建线 + 导入全部步骤（口径锚点取当时打开产线页的终端）。 */
    public static boolean create(EntityPlayer player, String presetName, String networkId, int dim, int x, int y,
        int z) {
        Preset hit = null;
        for (Preset p : all()) {
            if (p.name.equals(presetName)) {
                hit = p;
                break;
            }
        }
        if (hit == null) {
            chat(
                player,
                EnumChatFormatting.RED,
                net.minecraft.util.StatCollector
                    .translateToLocalFormatted("ae2_qof.proline.preset.missing", presetName));
            return false;
        }
        ProLineImporter.Result r = ProLineImporter.parse(hit.json);
        if (r.line.steps.isEmpty()) {
            chat(
                player,
                EnumChatFormatting.RED,
                net.minecraft.util.StatCollector
                    .translateToLocalFormatted("ae2_qof.proline.preset.nostep", presetName));
            return false;
        }
        ProductionLine created = ProLineManager.createLine(
            player,
            r.line.name == null || r.line.name.isEmpty() ? presetName : r.line.name,
            networkId,
            dim,
            x,
            y,
            z);
        if (created == null) return false;
        // 4.1.0-fix6：wiki 的 start[] 期望边界必须一并迁移（原先丢了）
        created.startExpected.putAll(r.line.startExpected);
        int n = 0;
        for (ProductionStep src : r.line.steps) {
            ProductionStep copy = new ProductionStep();
            copy.name = src.name;
            copy.machineType = src.machineType;
            copy.branch = src.branch;
            copy.hierarchy = src.hierarchy;
            copy.inputs.addAll(src.inputs);
            copy.outputs.addAll(src.outputs);
            if (ProLineManager.appendStep(player, created.id, copy) != null) n++;
        }
        chat(
            player,
            EnumChatFormatting.GREEN,
            net.minecraft.util.StatCollector
                .translateToLocalFormatted("ae2_qof.proline.preset.ok", created.name, n, r.errors.size()));
        for (String e : r.errors) {
            MyMod.LOG.info("[AE2QoL] 预置 {} 未解析项：{}", presetName, e);
        }
        return true;
    }

    private static void chat(EntityPlayer player, EnumChatFormatting color, String msg) {
        if (player != null) {
            player.addChatMessage(new ChatComponentText(color + "[产线] " + msg));
        }
    }
}
