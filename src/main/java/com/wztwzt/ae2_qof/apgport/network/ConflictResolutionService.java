/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.wztwzt.ae2_qof.apgport.config.ForgeConfig;
import com.wztwzt.ae2_qof.apgport.recipe.RecipeEntry;

/**
 * 冲突会话服务: 统一批次下发、最终结果收敛与生成触发。
 */
public final class ConflictResolutionService {

    private ConflictResolutionService() {}

    public static int currentServerStartIndex(ConflictSession session) {
        return session.getCurrentIndex() + 1;
    }

    public static void sendCurrentBatch(EntityPlayerMP player, ConflictSession session) {
        PacketRecipeConflictBatch batchPacket = PacketRecipeConflictBatch
            .fromSession(session, ForgeConfig.getConflictBatchSize());
        NetworkHandler.INSTANCE.sendTo(batchPacket, player);
    }

    public static List<RecipeEntry> collectFinalRecipes(ConflictSession session) {
        List<RecipeEntry> finalRecipes = new ArrayList<>(session.nonConflictingRecipes);
        for (String key : session.groupKeys) {
            Integer index = session.selections.get(key);
            if (index != null) {
                finalRecipes.add(
                    session.conflictGroups.get(key)
                        .get(index));
            }
        }
        return finalRecipes;
    }

    public static void finalizeSession(EntityPlayerMP player, ConflictSession session) {
        List<RecipeEntry> finalRecipes = collectFinalRecipes(session);
        PatternGenerationService.generateAndStore(player, session.recipeMapId, finalRecipes);
    }
}
