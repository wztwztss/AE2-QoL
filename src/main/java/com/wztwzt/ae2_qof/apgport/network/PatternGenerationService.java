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
            return false;
        }

        List<ItemStack> patterns = PatternEncoder.encodeBatch(recipes);
        if (patterns.isEmpty()) {
            send(player, EnumChatFormatting.YELLOW, "MyMod.msg.pattern.no_valid_after_encode");
            return false;
        }

        int requiredCount = patterns.size();
        ItemStack blankPattern = InventoryUtil.getBlankPattern();
        if (!consumeBlankPatterns(player, requiredCount, blankPattern)) {
            return false;
        }

        UUID uuid = player.getUniqueID();
        if (!PatternStorage.save(uuid, patterns, source)) {
            send(player, EnumChatFormatting.RED, "MyMod.msg.pattern.storage_write_failed");
            return false;
        }
        send(player, EnumChatFormatting.GREEN, "MyMod.msg.pattern.generated_and_consumed", requiredCount);
        send(player, EnumChatFormatting.GRAY, "MyMod.msg.pattern.stored_hint");
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
                "MyMod.msg.pattern.insufficient_blank_pattern",
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
