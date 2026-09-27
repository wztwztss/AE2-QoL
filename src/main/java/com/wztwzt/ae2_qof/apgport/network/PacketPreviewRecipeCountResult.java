/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.network;

import com.wztwzt.ae2_qof.MyMod;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.apgport.gui.GuiPatternGenStatusBridge;
import com.wztwzt.ae2_qof.apgport.util.I18nUtil;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server -> Client: preview count result from the persisted recipe cache.
 */
public class PacketPreviewRecipeCountResult implements IMessage {

    private boolean cacheValid;
    private String requestedKeyword;
    private int matchedMapCount;
    private int totalLoadedCount;
    private int totalFilteredCount;

    public PacketPreviewRecipeCountResult() {}

    public PacketPreviewRecipeCountResult(boolean cacheValid, String requestedKeyword, int matchedMapCount,
        int totalLoadedCount, int totalFilteredCount) {
        this.cacheValid = cacheValid;
        this.requestedKeyword = requestedKeyword;
        this.matchedMapCount = matchedMapCount;
        this.totalLoadedCount = totalLoadedCount;
        this.totalFilteredCount = totalFilteredCount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        cacheValid = buf.readBoolean();
        requestedKeyword = ByteBufUtils.readUTF8String(buf);
        matchedMapCount = buf.readInt();
        totalLoadedCount = buf.readInt();
        totalFilteredCount = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(cacheValid);
        ByteBufUtils.writeUTF8String(buf, requestedKeyword != null ? requestedKeyword : "");
        buf.writeInt(matchedMapCount);
        buf.writeInt(totalLoadedCount);
        buf.writeInt(totalFilteredCount);
    }

    public static class Handler implements IMessageHandler<PacketPreviewRecipeCountResult, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketPreviewRecipeCountResult message, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    if (!message.cacheValid) {
                        String text = EnumChatFormatting.RED
                            + I18nUtil.tr("MyMod.msg.cache.missing_or_invalid");
                        GuiPatternGenStatusBridge.setStatus(text);
                        if (Minecraft.getMinecraft().thePlayer != null) {
                            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(text));
                        }
                        return;
                    }

                    if (message.matchedMapCount <= 0) {
                        GuiPatternGenStatusBridge.setStatus(
                            EnumChatFormatting.RED
                                + I18nUtil.tr("MyMod.msg.generate.no_matching_map", message.requestedKeyword));
                        return;
                    }

                    GuiPatternGenStatusBridge.setStatus(
                        I18nUtil.tr(
                            "MyMod.gui.pattern_gen.status.filter_result",
                            message.totalLoadedCount,
                            message.totalFilteredCount));
                });
            return null;
        }
    }
}
