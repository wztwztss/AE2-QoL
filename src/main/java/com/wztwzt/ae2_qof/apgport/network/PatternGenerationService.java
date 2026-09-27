/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.network;

import com.wztwzt.ae2_qof.MyMod;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.apgport.encoder.PatternEncoder;
import com.wztwzt.ae2_qof.apgport.recipe.RecipeEntry;
import com.wztwzt.ae2_qof.apgport.storage.PatternStorage;
import com.wztwzt.ae2_qof.apgport.util.AE2Util;
import com.wztwzt.ae2_qof.apgport.util.I18nUtil;
import com.wztwzt.ae2_qof.apgport.util.InventoryUtil;

/**
 * 统一封装最终生成流程: 编码 -> 扣除空白样板 -> 写入虚拟仓储。
 */
public final class PatternGenerationService {

    private PatternGenerationService() {}

    public static boolean generateAndStore(EntityPlayerMP player, String source, List<RecipeEntry> recipes) {
        if (player == null || recipes == null || recipes.isEmpty()) {
            send(player, EnumChatFormatting.YELLOW, "ae2patterngen.msg.pattern.no_valid_after_encode");
            return false;
        }

        // ===== 3.29.0（AE2-QoL 的适配改动，文件头已注明来源）=====
        // 搬运来的是它的原行为：**扣掉 AE2 空白样板** + 写入它自己的**虚拟仓储**（PatternStorage），
        // 产物并不直接给玩家。本模组要的是"产物仍进背包"，所以这里改成：
        //   ① 编码仍用搬运来的 PatternEncoder（与我们 SmartPatternGenerator 的编码语义一致：
        //      in/out + crafting=false + 同时写 Count/Cnt）；
        //   ② **不再消耗空白样板**（我们的生成器一直是不消耗的，行为保持一致）；
        //   ③ 产物**直接进背包**，放不下掉在脚下；
        //   ④ 聊天栏 + 日志给出**全量计数**（本项目铁则：不许静默）。
        List<ItemStack> patterns = PatternEncoder.encodeBatch(recipes);
        if (patterns.isEmpty()) {
            send(player, EnumChatFormatting.YELLOW, "ae2patterngen.msg.pattern.no_valid_after_encode");
            return false;
        }

        int stored = 0;
        int dropped = 0;
        for (ItemStack pattern : patterns) {
            if (pattern == null) continue;
            if (player.inventory.addItemStackToInventory(pattern)) {
                stored++;
            } else {
                player.entityDropItem(pattern, 0.0F);
                dropped++;
            }
        }
        player.inventory.markDirty();
        com.wztwzt.ae2_qof.MyMod.LOG.info(
            "[AE2QoL] apgport 生成完成：player={} source={} 编码={} 进背包={} 掉脚下={}（未消耗空白样板）",
            player.getCommandSenderName(),
            source,
            patterns.size(),
            stored,
            dropped);
        player.addChatMessage(
            new net.minecraft.util.ChatComponentText(
                "\u00a7a[AE2QoL] \u5df2\u751f\u6210 " + stored
                    + " \u5f20\u6837\u677f"
                    + (dropped > 0 ? "\uff08\u80cc\u5305\u6ee1\uff0c" + dropped + " \u5f20\u6389\u5728\u811a\u4e0b\uff09" : "")
                    + "\uff08\u672a\u6d88\u8017\u7a7a\u767d\u6837\u677f\uff09"));
        return true;
    }

    private static boolean consumeBlankPatterns(EntityPlayerMP player, int requiredCount, ItemStack blankPattern) {
        boolean consumed = AE2Util.tryWirelessConsume(player, requiredCount, blankPattern);
        if (consumed) {
            return true;
        }

        if (!InventoryUtil.consumeItem(player, blankPattern, requiredCount)) {
            int currentHas = InventoryUtil.countItem(player, blankPattern);
            send(
                player,
                EnumChatFormatting.RED,
                "ae2patterngen.msg.pattern.insufficient_blank_pattern",
                requiredCount,
                currentHas);
            return false;
        }

        return true;
    }

    private static void send(EntityPlayerMP player, EnumChatFormatting color, String key, Object... args) {
        player.addChatMessage(new ChatComponentText(color + I18nUtil.tr(key, args)));
    }
}
