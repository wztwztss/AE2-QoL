package com.wztwzt.ae2_qof.machindex;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.machindex.common.IndexConfig;
import com.wztwzt.ae2_qof.machindex.common.IndexModel;
import com.wztwzt.ae2_qof.machindex.common.MachineIndexCache;
import com.wztwzt.ae2_qof.machindex.common.VoltageResolver;
import com.wztwzt.ae2_qof.machindex.server.BqQuestWriter;
import com.wztwzt.ae2_qof.machindex.server.MachIndexServerBridge;

/**
 * {@code /ae2qol machindex <rebuild|status|validate|clear_cache|clear>} 子命令。
 *
 * <p>
 * 挂到既有 {@code CommandAe2QoL} 下（用户拍板 Q5），复用它的权限校验；本类只负责解析参数与输出。
 *
 * <p>
 * 命令语义：
 * <ul>
 * <li>{@code status}：缓存键/条目统计/上次写入结果（**只读**）；</li>
 * <li>{@code validate}：只跑对账（读缓存 → 写库），不重扫；</li>
 * <li>{@code rebuild}：清缓存 + 重置一次性守卫 + 立即重扫（仅客户端；服务端走 GT-only 回退）；</li>
 * <li>{@code clear_cache}：删缓存文件（不动作务书）；</li>
 * <li>{@code clear}：删除我们生成的**分卷与任务**（只动自家命名空间）。</li>
 * </ul>
 */
public final class MachIndexCommand {

    private MachIndexCommand() {}

    public static final String USAGE = "/ae2qol machindex <status|validate|rebuild|export|clear_cache|clear>";

    /** 处理子命令；返回是否已处理（未识别返回 false 让上层给用法）。 */
    public static boolean handle(ICommandSender sender, String[] args) {
        // args[0] = "machindex"，其余是子命令
        String sub = args.length >= 2 ? args[1].toLowerCase() : "status";
        switch (sub) {
            case "status":
                doStatus(sender);
                return true;
            case "validate":
                doValidate(sender);
                return true;
            case "rebuild":
                doRebuild(sender);
                return true;
            case "export":
                doExport(sender);
                return true;
            case "clear_cache":
                doClearCache(sender);
                return true;
            case "clear":
                doClearQuests(sender);
                return true;
            default:
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + USAGE));
                return true;
        }
    }

    // ===================== 子命令 =====================

    private static void doStatus(ICommandSender sender) {
        line(sender, EnumChatFormatting.AQUA + "===== machindex status =====");
        line(
            sender,
            "enabled=" + IndexConfig.enabled
                + ", autoBuild="
                + IndexConfig.autoBuildOnWorldJoin
                + ", useCache="
                + IndexConfig.useCache
                + ", includeUnknown="
                + IndexConfig.includeUnknown);
        line(
            sender,
            "questMode=" + IndexConfig.questMode
                + ", chapterMode="
                + IndexConfig.chapterMode
                + ", maxPerChapter="
                + IndexConfig.maxQuestsPerChapter);

        for (String s : MachIndexServerBridge.describeCache()) {
            line(sender, "缓存：" + s);
        }
        // BQ 相关统计必须再判一次：本命令类在未装 BQ 的环境里同样会被加载
        // （/ae2qol machindex status 是免 OP 的只读入口），直接调会 NoClassDefFoundError。
        if (com.wztwzt.ae2_qof.machindex.MachIndexPlugin.hasBq()) {
            line(sender, "题库：" + safe(() -> BqQuestWriter.describeDatabase()));
            line(sender, "上次写入：" + safe(BqQuestWriter::getLastSummary));
        } else {
            line(sender, EnumChatFormatting.YELLOW + "未装 BetterQuesting：无任务写入");
        }

        // 客户端侧运行时状态（专用服上这些读数不存在，故只在客户端有值）
        if (sender.getEntityWorld() != null && sender.getEntityWorld().isRemote) {
            for (String s : com.wztwzt.ae2_qof.machindex.client.ScanPipeline.describeState()) {
                line(sender, "扫描：" + s);
            }
        }
    }

    private static void doValidate(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, "ae2qol")) {
            // 权限不足时明确说，不许静默什么也不做
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "No permission"));
            return;
        }
        line(sender, EnumChatFormatting.AQUA + "machindex validate：按缓存对账……");
        try {
            MachIndexServerBridge.repairFromCache("command:validate");
            line(sender, EnumChatFormatting.GREEN + "对账完成：" + safe(BqQuestWriter::getLastSummary));
        } catch (Throwable t) {
            line(sender, EnumChatFormatting.RED + "对账失败：" + t);
            MyMod.LOG.error("[AE2QoL] machindex validate 失败", t);
        }
    }

    private static void doRebuild(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, "ae2qol")) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "No permission"));
            return;
        }
        line(sender, EnumChatFormatting.AQUA + "machindex rebuild：清缓存 + 重扫……");
        try {
            String handlerHash = "";
            if (sender.getEntityWorld() != null && sender.getEntityWorld().isRemote) {
                // 客户端：重置守卫后走完整扫描
                com.wztwzt.ae2_qof.machindex.client.ScanPipeline.resetAutoScanGuard();
                VoltageResolver.invalidateCaches();
                com.wztwzt.ae2_qof.machindex.client.ScanPipeline.triggerScan(true, true);
                line(sender, EnumChatFormatting.GREEN + "已在客户端发起重扫（结果会写缓存并触发写库）");
                return;
            }
            // 服务端：没有 NEI，只能按缓存对账（GT-only 回退留给后续版本）
            VoltageResolver.invalidateCaches();
            line(
                sender,
                EnumChatFormatting.YELLOW + "服务端无 NEI，无法重扫；请用客户端执行 /ae2qol machindex rebuild，"
                    + "或用 /ae2qol machindex validate 按现有缓存对账");
            MachIndexServerBridge.repairFromCache("command:rebuild");
            line(sender, "对账完成：" + safe(BqQuestWriter::getLastSummary));
        } catch (Throwable t) {
            line(sender, EnumChatFormatting.RED + "rebuild 失败：" + t);
            MyMod.LOG.error("[AE2QoL] machindex rebuild 失败", t);
        }
    }

    /**
     * {@code /ae2qol machindex export}：把「自动归类失败」的多方块机器导出成 JSON，供人工确认归类。
     *
     * <p>
     * 用户 2026-10-29 的选择是「先自动归类，只把分不准的导出给我确认」——
     * 所以这里只导 {@link MachineCategory#OTHER} 的条目，不导全量，避免几百条的噪音。
     *
     * <p>
     * 输出位置：{@code config/ae2_qof/machindex/unclassified.json}。
     * 字段：显示名 / 注册名 / meta / 合成电压档 / 服务过的配方类型 / 来源模组。
     * 用户可直接在文件里给某条加 {@code "category": "卷板/弯曲"}，我再据此补规则。
     */
    private static void doExport(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, "ae2qol")) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "No permission"));
            return;
        }
        java.io.File dir = MachIndexServerBridge.configDir();
        if (dir == null) {
            line(sender, EnumChatFormatting.RED + "配置目录不可用");
            return;
        }
        MachineIndexCache.CacheFile cache = MachineIndexCache.load(dir);
        if (cache == null) {
            line(sender, EnumChatFormatting.YELLOW + "无可用缓存：请先进世界扫描一次，或 /ae2qol machindex rebuild");
            return;
        }

        // typeId → 显示名（用于导出里给出人类可读的配方类型）
        java.util.Map<String, String> typeNames = new java.util.HashMap<>();
        for (IndexModel.RecipeTypeIndex t : cache.result.types) {
            typeNames.put(t.typeId, t.displayName == null || t.displayName.isEmpty() ? t.typeId : t.displayName);
        }

        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        int total = 0;
        for (IndexModel.MachineEntry m : cache.result.machines) {
            if (!m.isMultiblock) continue;
            boolean allOther = true;
            java.util.List<String> cats = new java.util.ArrayList<>();
            for (String typeId : m.typeIds) {
                String cat = com.wztwzt.ae2_qof.machindex.common.MachineCategory.resolve(typeId, typeNames.get(typeId));
                cats.add(cat);
                if (!com.wztwzt.ae2_qof.machindex.common.MachineCategory.isOther(cat)) allOther = false;
            }
            if (!allOther) continue;
            total++;

            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("displayName", m.displayName == null ? "" : m.displayName);
            o.addProperty("registryName", m.registryName);
            o.addProperty("meta", m.meta);
            o.addProperty("tier", m.mainTier);
            o.addProperty("tierName", com.wztwzt.ae2_qof.machindex.common.VoltageResolver.tierName(m.mainTier));
            o.addProperty("modId", m.modId);
            com.google.gson.JsonArray types = new com.google.gson.JsonArray();
            for (String typeId : m.typeIds) {
                com.google.gson.JsonObject t = new com.google.gson.JsonObject();
                t.addProperty("typeId", typeId);
                t.addProperty("displayName", typeNames.getOrDefault(typeId, typeId));
                t.addProperty(
                    "autoCategory",
                    com.wztwzt.ae2_qof.machindex.common.MachineCategory.resolve(typeId, typeNames.get(typeId)));
                types.add(t);
            }
            o.add("types", types);
            // 留一个空字段给用户手填，回导时我按它补规则
            o.addProperty("category", "");
            arr.add(o);
        }

        java.io.File out = new java.io.File(new java.io.File(dir, "ae2_qof/machindex"), "unclassified.json");
        try {
            java.io.File parent = out.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                line(sender, EnumChatFormatting.RED + "目录创建失败：" + parent);
                return;
            }
            try (java.io.Writer w = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(out),
                java.nio.charset.StandardCharsets.UTF_8)) {
                new com.google.gson.GsonBuilder().setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create()
                    .toJson(arr, w);
            }
            line(sender, EnumChatFormatting.GREEN + "已导出未归类多方块 " + total + " 台 → " + out.getAbsolutePath());
            line(sender, EnumChatFormatting.GRAY + "给条目填上 \"category\" 后发我，我据此补分类规则");
            MyMod.LOG.info("[AE2QoL] machindex 导出未归类机器 " + total + " 台 → " + out.getAbsolutePath());
        } catch (Throwable t) {
            line(sender, EnumChatFormatting.RED + "导出失败：" + t);
            MyMod.LOG.error("[AE2QoL] machindex export 失败", t);
        }
    }

    private static void doClearCache(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, "ae2qol")) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "No permission"));
            return;
        }
        java.io.File dir = MachIndexServerBridge.configDir();
        boolean ok = dir != null && MachineIndexCache.clear(dir);
        line(
            sender,
            (ok ? EnumChatFormatting.GREEN + "缓存已删除" : EnumChatFormatting.RED + "缓存删除失败")
                + "（任务书未改动，可用 validate 重新对账）");
    }

    private static void doClearQuests(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, "ae2qol")) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "No permission"));
            return;
        }
        if (!cpw.mods.fml.common.Loader.isModLoaded("betterquesting")) {
            line(sender, EnumChatFormatting.YELLOW + "未检测到 BetterQuesting，无需清理");
            return;
        }
        line(sender, EnumChatFormatting.AQUA + "machindex clear：删除本模组生成的分卷与任务……");
        try {
            BqQuestWriter.clearAll();
            line(sender, EnumChatFormatting.GREEN + safe(BqQuestWriter::getLastSummary));
        } catch (Throwable t) {
            line(sender, EnumChatFormatting.RED + "清理失败：" + t);
            MyMod.LOG.error("[AE2QoL] machindex clear 失败", t);
        }
    }

    // ===================== tab 补全 =====================

    public static List<String> tabComplete(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) {
            String prefix = args[1] == null ? "" : args[1].toLowerCase();
            for (String s : new String[] { "status", "validate", "rebuild", "clear_cache", "clear" }) {
                if (s.startsWith(prefix)) out.add(s);
            }
        }
        return out;
    }

    // ===================== 工具 =====================

    private static void line(ICommandSender sender, String msg) {
        sender.addChatMessage(new ChatComponentText(msg));
    }

    /** 包一层 try/catch：统计类调用失败绝不能把命令打崩。 */
    private static String safe(java.util.function.Supplier<String> fn) {
        try {
            String s = fn.get();
            return s == null ? "-" : s;
        } catch (Throwable t) {
            return "不可用：" + t;
        }
    }
}
