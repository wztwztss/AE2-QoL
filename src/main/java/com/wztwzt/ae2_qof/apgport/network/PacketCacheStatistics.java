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
import com.wztwzt.ae2_qof.apgport.storage.CacheStatistics;
import com.wztwzt.ae2_qof.apgport.util.I18nUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server -> Client: cache statistics after build completion.
 */
public class PacketCacheStatistics implements IMessage {

    private int totalRecipeCount;
    private int totalRecipeMaps;
    private int totalModCount;
    private long directoryBytes;

    public PacketCacheStatistics() {}

    public PacketCacheStatistics(CacheStatistics statistics) {
        this.totalRecipeCount = statistics.totalRecipeCount;
        this.totalRecipeMaps = statistics.totalRecipeMaps;
        this.totalModCount = statistics.totalModCount;
        this.directoryBytes = statistics.directoryBytes;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        totalRecipeCount = buf.readInt();
        totalRecipeMaps = buf.readInt();
        totalModCount = buf.readInt();
        directoryBytes = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(totalRecipeCount);
        buf.writeInt(totalRecipeMaps);
        buf.writeInt(totalModCount);
        buf.writeLong(directoryBytes);
    }

    public static class Handler implements IMessageHandler<PacketCacheStatistics, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketCacheStatistics message, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    String status = I18nUtil.tr(
                        "MyMod.gui.pattern_gen.status.cache_ready",
                        message.totalRecipeMaps,
                        message.totalRecipeCount);
                    GuiPatternGenStatusBridge.setStatus(status);

                    if (Minecraft.getMinecraft().thePlayer != null) {
                        Minecraft.getMinecraft().thePlayer.addChatMessage(
                            new ChatComponentText(
                                EnumChatFormatting.GREEN + I18nUtil.tr(
                                    "MyMod.msg.cache.statistics",
                                    message.totalRecipeMaps,
                                    message.totalRecipeCount,
                                    message.totalModCount,
                                    message.directoryBytes)));
                    }
                });
            return null;
        }
    }
}
