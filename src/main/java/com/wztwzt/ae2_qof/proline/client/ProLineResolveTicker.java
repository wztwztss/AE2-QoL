package com.wztwzt.ae2_qof.proline.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.wztwzt.ae2_qof.proline.ProductionLine;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * 4.2.0：客户端定时触发名称解析。
 *
 * <p>
 * 产线页打开时，客户端每 15 秒扫一次当前选中产线的未解析条目；
 * 有新的未解析名 → 调 {@link ProLineNameResolver#resolveAndSend} → 新包发服务端回填。
 * 防抖：同一批 raw 名在 60 秒内不重发（避免 packet 风暴）。
 *
 * <p>
 * 注册时机：CommonProxy 或 ClientProxy 初始化时（FML bus）。
 */
@cpw.mods.fml.relauncher.SideOnly(Side.CLIENT)
public final class ProLineResolveTicker {

    private static final long INTERVAL_MS = 15_000L;
    private static final long DEBOUNCE_MS = 60_000L;
    /** lineId → 上次发包时间。 */
    private static final java.util.Map<String, Long> LAST_SENT = new java.util.HashMap<>();
    private static long nextTick = 0L;
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new ProLineResolveTicker());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        if (now < nextTick) return;
        nextTick = now + INTERVAL_MS;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || player.worldObj == null || !player.worldObj.isRemote) return;
        // 只有打开产线页时才触发（MUI2 screen active 检查）
        if (!com.cleanroommc.modularui.screen.ModularScreen
            .isActive(com.wztwzt.ae2_qof.MyMod.MODID, "stock_monitor_terminal")) return;

        // 从静态表拿当前选中的 lineId（ProLinePage 写入）
        UUID playerId = player.getUniqueID();
        String lineId = getSelectedLineId(playerId);
        if (lineId == null || lineId.isEmpty()) return;

        Long lastSent = LAST_SENT.get(lineId);
        if (lastSent != null && now - lastSent < DEBOUNCE_MS) return;

        // 解析并发包
        int count = ProLineNameResolver.resolveAndSend(lineId, buildClientLine(lineId));
        if (count > 0) {
            LAST_SENT.put(lineId, now);
            com.wztwzt.ae2_qof.MyMod.LOG.info("[AE2QoL] 产线 {} 客户端解析发包：{} 条 raw→key 映射", lineId, count);
        }
    }

    /** 客户端侧构建临时 ProductionLine 用于解析（只有 steps/inputs/outputs 结构，无需服务端数据）。 */
    private ProductionLine buildClientLine(String lineId) {
        // 客户端没有 ProLineData——但名称解析只需要 raw 名列表。
        // 从 ProLinePage 的服务端 getter 无法在 client 调用……
        // 方案：从 ProLineResolvePacket 的视角，服务端需要知道哪些 raw 未解析。
        // 简化：客户端扫描同步下来的 StepRow 行（每行有 icon 但无 inputs）。
        // 实际上：ProLinePage 的 collectStepRows 是服务端 getter，客户端拿不到。
        // 最终方案：让客户端直接扫 jar 预置的 presets.json + startExpected raw 名。
        // 更简单：让服务端在产线行快照里附带未解析 raw 名列表，客户端据此解析。
        // 最最简单（v1）：客户端从 StepRow 的 name 反查 presets.json 里的 raw 名集合。
        // 以上都复杂——最实用的方案：**客户端不扫产线，改为扫全部 7 条预置的 raw 名**，
        // 一次解析全量 → 一次发包 → 服务端对所有产线匹配回填。覆盖面最广。
        ProductionLine tmp = new ProductionLine();
        tmp.id = lineId;
        try {
            com.google.gson.JsonElement root = new com.google.gson.JsonParser().parse(
                new java.io.InputStreamReader(
                    ProLineResolveTicker.class.getResourceAsStream("/assets/ae2_qof/proline/presets.json"),
                    java.nio.charset.StandardCharsets.UTF_8));
            if (root.isJsonObject() && root.getAsJsonObject()
                .has("presets")) {
                for (com.google.gson.JsonElement el : root.getAsJsonObject()
                    .getAsJsonArray("presets")) {
                    if (!el.isJsonObject()) continue;
                    com.google.gson.JsonObject o = el.getAsJsonObject();
                    if (!o.has("json")) continue;
                    com.google.gson.JsonElement inner = new com.google.gson.JsonParser().parse(
                        o.get("json")
                            .getAsString());
                    if (!inner.isJsonObject()) continue;
                    com.google.gson.JsonObject obj = inner.getAsJsonObject();
                    if (obj.has("recipeQuery") && obj.get("recipeQuery")
                        .isJsonArray()) {
                        for (com.google.gson.JsonElement q : obj.getAsJsonArray("recipeQuery")) {
                            if (!q.isJsonObject()) continue;
                            com.google.gson.JsonObject st = q.getAsJsonObject();
                            for (String field : new String[] { "input", "output" }) {
                                if (!st.has(field) || !st.get(field)
                                    .isJsonPrimitive()) continue;
                                for (String token : st.get(field)
                                    .getAsString()
                                    .split(",")) {
                                    token = token.trim();
                                    if (!token.isEmpty()) tmp.startExpected.put(token, 0L);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 预置 raw 名收集异常", t);
        }
        return tmp;
    }

    /** 从 ProLinePage 的 SELECTED_LINE 静态表读取当前选中的产线 id。 */
    private static String getSelectedLineId(UUID playerId) {
        return com.wztwzt.ae2_qof.proline.gui.ProLinePage.getSelectedLineIdForResolve(playerId);
    }
}
