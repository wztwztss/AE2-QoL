/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.network;

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

    /**
     * 3.33.0-fix1：解码期长度上界（防恶意包打爆堆）。
     * <p>服务端每批冲突组数由 {@code ForgeConfig.MAX_CONFLICT_BATCH_SIZE = 64} 封顶，
     * 正常客户端单包最多 64 个下标；360 留 5 倍余量，只拦恶意/损坏的包。
     * <p>为什么必须在解码期拦：{@code fromBytes} 跑在 Netty 解码阶段，早于
     * {@code Handler.onMessage} 的会话判定 —— 旧代码按包内声明的 int 无界
     * {@code new int[len]}，恶意客户端无需任何会话/界面即可让服务端分配
     * 最大 2GB（len≈5e8）乃至 OOM。与同通道 {@code PacketGeneratePatterns} 等包
     * 的既有夹紧口径一致。
     */
    private static final int MAX_SELECTED_INDICES = 360;

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
        // 3.33.0-fix1：长度越界（负数或超上限）按空数组处理，不再按声明值分配
        if (len < 0 || len > MAX_SELECTED_INDICES) {
            selectedIndices = new int[0];
            return;
        }
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
