/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.wztwzt.ae2_qof.apgport.storage.CacheStatistics;
import com.wztwzt.ae2_qof.apgport.storage.RecipeCacheService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client -> Server: request recipe cache creation or refresh.
 */
public class PacketCreateCache implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketCreateCache, IMessage> {

        @Override
        public IMessage onMessage(PacketCreateCache message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            boolean started = RecipeCacheService.createOrRefreshCache(new RecipeCacheService.ProgressNotifier() {

                @Override
                public void onProgress(String progressMessage, int current, int total) {
                    NetworkHandler.INSTANCE.sendTo(
                        new PacketCacheProgress(PacketCacheProgress.STAGE_PROGRESS, progressMessage, current, total),
                        player);
                }

                @Override
                public void onComplete(CacheStatistics statistics) {
                    NetworkHandler.INSTANCE.sendTo(new PacketCacheStatistics(statistics), player);
                }

                @Override
                public void onError(String message) {
                    NetworkHandler.INSTANCE
                        .sendTo(new PacketCacheProgress(PacketCacheProgress.STAGE_ERROR, message, 0, 0), player);
                }
            });

            if (started) {
                NetworkHandler.INSTANCE
                    .sendTo(new PacketCacheProgress(PacketCacheProgress.STAGE_STARTED, "", 0, 0), player);
            } else {
                NetworkHandler.INSTANCE
                    .sendTo(new PacketCacheProgress(PacketCacheProgress.STAGE_ALREADY_RUNNING, "", 0, 0), player);
            }
            return null;
        }
    }
}
