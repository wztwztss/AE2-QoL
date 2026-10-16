package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.generator.SmartPatternGenerator;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 「批量生成样板」请求包（C2S，3.23.0）。
 *
 * <h2>为什么在服务端生成</h2>
 * 配方来自 GT 的 {@code RecipeMap}（服务端权威数据），生成结果又是真实物品 ⇒ 必须服务端执行；
 * 客户端只负责收集界面上的参数并发包。
 *
 * <h2>产物去向</h2>
 * 优先进玩家背包（放不下就掉在脚下并提示），并在**聊天栏**给出汇总：
 * 扫描多少、产出多少、跳过流体多少、被过滤多少、是否触顶截断 —— 每个数字都可见（不许静默）。
 */
public class SmartPatternGenPacket implements IMessage {

    /**
     * 3.34.0-fix3（审查 P1-4）：生成数量服务端上限。
     * <p>
     * 客户端 UI 只护下界（≤0 → 512），旧链路对上界照单全收：一个
     * {@code maxPatterns=Integer.MAX_VALUE} 的包会让服务端对命中 RecipeMap 的**全部**配方
     * 逐条编码样板 —— 主线程冻结、内存峰值、装不下的 {@code entityDropItem} 成实体洪水。
     * 4096 远超正常单次需求（正常按几万分批），只截恶意/误输入。
     */
    public static final int MAX_PATTERNS = 4096;

    /**
     * 3.34.0-fix3：同玩家节流窗口（毫秒）。同参数重复请求在该窗口内只处理第一次 ——
     * 防止"按住按钮狂发包"把服务端反复拖进编码循环。与 apgport 的
     * {@code PatternGenerationRequestGate} 同思路（那类是包私有，跨包不可见，这里自建一份）。
     */
    private static final long DUPLICATE_WINDOW_MS = 5000L;
    private static final java.util.Map<java.util.UUID, long[]> LAST_REQUEST = new java.util.HashMap<>();

    /** 配方设置：RecipeMap id 或其子串。 */
    private String mapKeyword = "";
    private String blacklistInput = "";
    private String blacklistOutput = "";
    private String requireInputOre = "";
    private String requireOutputOre = "";
    private String requireNonConsumed = "";
    private int maxPatterns = 512;
    /** 电压等级上限（0=ULV…；-1 = 不限）。 */
    private int maxTier = -1;
    /** 替换规则（源矿辞=目标矿辞，多条用 ; 分隔）。 */
    private String replacements = "";
    /** true = 只统计不产出（「预览数量」）。 */
    private boolean dryRun;

    public SmartPatternGenPacket() {}

    public SmartPatternGenPacket(String mapKeyword, String blacklistInput, String blacklistOutput,
        String requireInputOre, String requireOutputOre, String requireNonConsumed, int maxPatterns) {
        this(
            mapKeyword,
            blacklistInput,
            blacklistOutput,
            requireInputOre,
            requireOutputOre,
            requireNonConsumed,
            maxPatterns,
            -1,
            "",
            false);
    }

    public SmartPatternGenPacket(String mapKeyword, String blacklistInput, String blacklistOutput,
        String requireInputOre, String requireOutputOre, String requireNonConsumed, int maxPatterns, int maxTier) {
        this(
            mapKeyword,
            blacklistInput,
            blacklistOutput,
            requireInputOre,
            requireOutputOre,
            requireNonConsumed,
            maxPatterns,
            maxTier,
            "",
            false);
    }

    public SmartPatternGenPacket(String mapKeyword, String blacklistInput, String blacklistOutput,
        String requireInputOre, String requireOutputOre, String requireNonConsumed, int maxPatterns, int maxTier,
        String replacements, boolean dryRun) {
        this.maxTier = maxTier;
        this.replacements = safe(replacements);
        this.dryRun = dryRun;
        this.mapKeyword = safe(mapKeyword);
        this.blacklistInput = safe(blacklistInput);
        this.blacklistOutput = safe(blacklistOutput);
        this.requireInputOre = safe(requireInputOre);
        this.requireOutputOre = safe(requireOutputOre);
        this.requireNonConsumed = safe(requireNonConsumed);
        this.maxPatterns = maxPatterns;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, mapKeyword);
        ByteBufUtils.writeUTF8String(buf, blacklistInput);
        ByteBufUtils.writeUTF8String(buf, blacklistOutput);
        ByteBufUtils.writeUTF8String(buf, requireInputOre);
        ByteBufUtils.writeUTF8String(buf, requireOutputOre);
        ByteBufUtils.writeUTF8String(buf, requireNonConsumed);
        buf.writeInt(maxPatterns);
        buf.writeInt(maxTier);
        ByteBufUtils.writeUTF8String(buf, replacements);
        buf.writeBoolean(dryRun);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            mapKeyword = ByteBufUtils.readUTF8String(buf);
            blacklistInput = ByteBufUtils.readUTF8String(buf);
            blacklistOutput = ByteBufUtils.readUTF8String(buf);
            requireInputOre = ByteBufUtils.readUTF8String(buf);
            requireOutputOre = ByteBufUtils.readUTF8String(buf);
            requireNonConsumed = ByteBufUtils.readUTF8String(buf);
            maxPatterns = buf.readInt();
            maxTier = buf.readInt();
            replacements = ByteBufUtils.readUTF8String(buf);
            dryRun = buf.readBoolean();
            // 3.34.0-fix3：解码期直接夹紧（客户端 UI 的校验不构成防护）
            if (maxPatterns > MAX_PATTERNS) maxPatterns = MAX_PATTERNS;
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 样板生成请求包解析失败（已忽略）", t);
        }
    }

    /** 3.34.0-fix3：同玩家 5 秒节流（防按住按钮狂发包）。 */
    private static synchronized boolean shouldProcess(java.util.UUID player, long nowMillis) {
        long[] last = LAST_REQUEST.get(player);
        if (last != null && nowMillis - last[0] < DUPLICATE_WINDOW_MS) {
            return false;
        }
        if (LAST_REQUEST.size() > 1024) {
            // 无界增长防护：满了先清过期，还满就整体重置（1024 个玩家在线时才可能触到）
            long cutoff = nowMillis - DUPLICATE_WINDOW_MS;
            LAST_REQUEST.entrySet()
                .removeIf(e -> e.getValue()[0] < cutoff);
            if (LAST_REQUEST.size() > 1024) LAST_REQUEST.clear();
        }
        LAST_REQUEST.put(player, new long[] { nowMillis });
        return true;
    }

    public static class Handler implements IMessageHandler<SmartPatternGenPacket, IMessage> {

        @Override
        public IMessage onMessage(final SmartPatternGenPacket message, final MessageContext ctx) {
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                    if (player == null) return;
                    // 3.34.0-fix3：同玩家 5 秒节流（所有模式统一——dryRun 也在窗口内，
                    // 防止"预览按钮"被脚本狂点等效绕过）
                    if (!SmartPatternGenPacket.shouldProcess(player.getUniqueID(), System.currentTimeMillis())) {
                        return;
                    }
                    SmartPatternGenerator.Filters filters = new SmartPatternGenerator.Filters();
                    filters.blacklistInput = message.blacklistInput;
                    filters.blacklistOutput = message.blacklistOutput;
                    filters.requireInputOre = message.requireInputOre;
                    filters.requireOutputOre = message.requireOutputOre;
                    filters.requireNonConsumed = message.requireNonConsumed;
                    filters.maxTier = message.maxTier;
                    filters.replacements = message.replacements;

                    SmartPatternGenerator.Result result = SmartPatternGenerator
                        .generate(message.mapKeyword, filters, message.maxPatterns, message.dryRun);
                    if (message.dryRun) {
                        // 预览数量：只报统计，不产出任何物品
                        player.addChatMessage(
                            new ChatComponentText(
                                (result.isEmpty() ? EnumChatFormatting.RED : EnumChatFormatting.YELLOW)
                                    + "[AE2QoL] \u9884\u89c8\u6570\u91cf\uff1a"
                                    + result.describe()));
                        return;
                    }
                    if (result.isEmpty()) {
                        player.addChatMessage(
                            new ChatComponentText(
                                EnumChatFormatting.RED + "[AE2QoL] \u672a\u751f\u6210\u4efb\u4f55\u6837\u677f\uff1a"
                                    + result.describe()));
                        return;
                    }
                    int stored = 0;
                    int dropped = 0;
                    for (ItemStack pattern : result.patterns) {
                        if (player.inventory.addItemStackToInventory(pattern)) {
                            stored++;
                        } else {
                            player.entityDropItem(pattern, 0.0F);
                            dropped++;
                        }
                    }
                    player.inventory.markDirty();
                    MyMod.LOG.info(
                        "[AE2QoL] 样板生成：player={} stored={} dropped={} {}",
                        player.getCommandSenderName(),
                        stored,
                        dropped,
                        result.describe());
                    player.addChatMessage(
                        new ChatComponentText(
                            EnumChatFormatting.GREEN + "[AE2QoL] \u5df2\u751f\u6210 "
                                + stored
                                + " \u5f20\u6837\u677f"
                                + (dropped > 0
                                    ? EnumChatFormatting.YELLOW + "\uff08\u80cc\u5305\u6ee1\uff0c"
                                        + dropped
                                        + " \u5f20\u6389\u5728\u811a\u4e0b\uff09"
                                    : "")
                                + EnumChatFormatting.GRAY
                                + " "
                                + result.describe()));
                } catch (Throwable t) {
                    MyMod.LOG.warn("[AE2QoL] 样板生成请求处理异常", t);
                }
            });
            return null;
        }
    }
}
