/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.network;

import com.wztwzt.ae2_qof.MyMod;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.apgport.recipe.RecipeEntry;
import com.wztwzt.ae2_qof.apgport.util.I18nUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client -> Server: 批量提交冲突选择结果。
 */
public class PacketResolveConflictsBatch implements IMessage {

    public int expectedStartIndex;
    public boolean cancel;
    public int[] selectedIndices;

    public PacketResolveConflictsBatch() {}

    public PacketResolveConflictsBatch(int expectedStartIndex, boolean cancel, int[] selectedIndices) {
        this.expectedStartIndex = expectedStartIndex;
        this.cancel = cancel;
        this.selectedIndices = selectedIndices;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        expectedStartIndex = buf.readInt();
        cancel = buf.readBoolean();
        int len = buf.readInt();
        selectedIndices = new int[len];
        for (int i = 0; i < len; i++) {
            selectedIndices[i] = buf.readInt();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(expectedStartIndex);
        buf.writeBoolean(cancel);
        int[] safe = selectedIndices != null ? selectedIndices : new int[0];
        buf.writeInt(safe.length);
        for (int idx : safe) {
            buf.writeInt(idx);
        }
    }

    public static class Handler implements IMessageHandler<PacketResolveConflictsBatch, IMessage> {

        @Override
        public IMessage onMessage(PacketResolveConflictsBatch message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            UUID uuid = player.getUniqueID();
            ConflictSession session = ConflictSession.get(uuid);
            if (session == null) return null;

            int serverStartIndex = ConflictResolutionService.currentServerStartIndex(session);

            if (message.cancel) {
                // Ignore stale close/cancel packets from an outdated client window.
                if (message.expectedStartIndex > 0 && message.expectedStartIndex != serverStartIndex) {
                    return null;
                }
                ConflictSession.stop(uuid);
                send(player, EnumChatFormatting.YELLOW, "ae2patterngen.msg.conflict.cancelled");
                return null;
            }

            if (message.expectedStartIndex > 0 && message.expectedStartIndex != serverStartIndex) {
                send(player, EnumChatFormatting.RED, "ae2patterngen.msg.conflict.session_updated");
                sendCurrentBatch(player, session);
                return null;
            }

            if (message.selectedIndices == null || message.selectedIndices.length == 0) {
                send(player, EnumChatFormatting.RED, "ae2patterngen.msg.conflict.no_valid_selection");
                sendCurrentBatch(player, session);
                return null;
            }

            for (int selectedIndex : message.selectedIndices) {
                if (session.isComplete()) break;

                java.util.List<RecipeEntry> currentRecipes = session.getCurrentRecipes();
                if (currentRecipes == null || currentRecipes.isEmpty()) {
                    send(player, EnumChatFormatting.RED, "ae2patterngen.msg.conflict.session_empty_group");
                    ConflictSession.stop(uuid);
                    return null;
                }

                if (selectedIndex < 0 || selectedIndex >= currentRecipes.size()) {
                    send(
                        player,
                        EnumChatFormatting.RED,
                        "ae2patterngen.msg.conflict.invalid_batch_selection",
                        selectedIndex);
                    sendCurrentBatch(player, session);
                    return null;
                }

                session.select(selectedIndex);
            }

            if (session.isComplete()) {
                ConflictResolutionService.finalizeSession(player, session);
                ConflictSession.stop(uuid);
            } else {
                sendCurrentBatch(player, session);
            }

            return null;
        }

        private void sendCurrentBatch(EntityPlayerMP player, ConflictSession session) {
            ConflictResolutionService.sendCurrentBatch(player, session);
        }

        private void send(EntityPlayerMP player, EnumChatFormatting color, String key, Object... args) {
            player.addChatMessage(new ChatComponentText(color + I18nUtil.tr(key, args)));
        }
    }
}
