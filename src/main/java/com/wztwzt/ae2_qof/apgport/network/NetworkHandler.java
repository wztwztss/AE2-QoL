/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.network;

import com.wztwzt.ae2_qof.MyMod;


import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 网络通信管理
 */
public class NetworkHandler {

    // 注意：绝不能与我们的 ModNetwork.CHANNEL / WildcardNetwork.CHANNEL 同名 ——
    // NetworkRegistry 遇到重复通道名会直接抛异常（启动崩溃）。因此带 _apg 后缀独立成通道。
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE
        .newSimpleChannel(MyMod.MODID + "_apg");

    private static int packetId = 0;

    public static void init() {
        INSTANCE.registerMessage(PacketCreateCache.Handler.class, PacketCreateCache.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(
            PacketPreviewRecipeCount.Handler.class,
            PacketPreviewRecipeCount.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(
            PacketGeneratePatterns.Handler.class,
            PacketGeneratePatterns.class,
            packetId++,
            Side.SERVER);

        INSTANCE.registerMessage(PacketSaveFields.Handler.class, PacketSaveFields.class, packetId++, Side.SERVER);

        INSTANCE.registerMessage(PacketStorageAction.Handler.class, PacketStorageAction.class, packetId++, Side.SERVER);

        INSTANCE
            .registerMessage(PacketRecipeConflicts.Handler.class, PacketRecipeConflicts.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(
            PacketPreviewRecipeCountResult.Handler.class,
            PacketPreviewRecipeCountResult.class,
            packetId++,
            Side.CLIENT);
        INSTANCE.registerMessage(PacketCacheProgress.Handler.class, PacketCacheProgress.class, packetId++, Side.CLIENT);
        INSTANCE
            .registerMessage(PacketCacheStatistics.Handler.class, PacketCacheStatistics.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(
            PacketResolveConflicts.Handler.class,
            PacketResolveConflicts.class,
            packetId++,
            Side.SERVER);
        INSTANCE.registerMessage(
            PacketRecipeConflictBatch.Handler.class,
            PacketRecipeConflictBatch.class,
            packetId++,
            Side.CLIENT);
        INSTANCE.registerMessage(
            PacketResolveConflictsBatch.Handler.class,
            PacketResolveConflictsBatch.class,
            packetId++,
            Side.SERVER);
    }

    public static void sendToServer(PacketGeneratePatterns packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendSaveFieldsToServer(PacketSaveFields packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendStorageAction(PacketStorageAction packet) {
        INSTANCE.sendToServer(packet);
    }
}
