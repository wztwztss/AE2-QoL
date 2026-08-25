package com.wztwzt.ae2_qof.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class ModNetwork {

    public static final String CHANNEL_ID = "ae2apu";
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_ID);

    private static int discriminator = 0;

    private ModNetwork() {}

    public static void registerPackets() {
        CHANNEL.registerMessage(
            RequestProvidersListPacket.Handler.class,
            RequestProvidersListPacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            ProvidersListS2CPacket.Handler.class,
            ProvidersListS2CPacket.class,
            discriminator++,
            Side.CLIENT);

        CHANNEL.registerMessage(
            UploadPatternPacket.Handler.class,
            UploadPatternPacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            RecallPatternPacket.Handler.class,
            RecallPatternPacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            WirelessActionPacket.Handler.class,
            WirelessActionPacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            WirelessChannelSyncPacket.Handler.class,
            WirelessChannelSyncPacket.class,
            discriminator++,
            Side.CLIENT);

        CHANNEL.registerMessage(
            WirelessHighlightPacket.Handler.class,
            WirelessHighlightPacket.class,
            discriminator++,
            Side.CLIENT);

        CHANNEL.registerMessage(SwapPatternPacket.Handler.class, SwapPatternPacket.class, discriminator++, Side.SERVER);

        CHANNEL.registerMessage(SwapPatternPacket.Handler.class, SwapPatternPacket.class, discriminator++, Side.CLIENT);

        CHANNEL.registerMessage(ExtractItemPacket.Handler.class, ExtractItemPacket.class, discriminator++, Side.SERVER);

        CHANNEL.registerMessage(
            RequestCraftingPacket.Handler.class,
            RequestCraftingPacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            CraftingResponsePacket.Handler.class,
            CraftingResponsePacket.class,
            discriminator++,
            Side.CLIENT);

        CHANNEL.registerMessage(
            CraftingCompletePacket.Handler.class,
            CraftingCompletePacket.class,
            discriminator++,
            Side.CLIENT);

        CHANNEL.registerMessage(ReplanPacket.Handler.class, ReplanPacket.class, discriminator++, Side.SERVER);

        CHANNEL.registerMessage(
            SmartDoublingTogglePacket.Handler.class,
            SmartDoublingTogglePacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(ConfigSetPacket.Handler.class, ConfigSetPacket.class, discriminator++, Side.SERVER);

        CHANNEL
            .registerMessage(ConfigUpdatePacket.Handler.class, ConfigUpdatePacket.class, discriminator++, Side.CLIENT);

        CHANNEL.registerMessage(
            MergedTerminalActionPacket.Handler.class,
            MergedTerminalActionPacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            MergedTerminalResultPacket.Handler.class,
            MergedTerminalResultPacket.class,
            discriminator++,
            Side.CLIENT);

        CHANNEL.registerMessage(
            MergedTerminalBlankCountPacket.Handler.class,
            MergedTerminalBlankCountPacket.class,
            discriminator++,
            Side.CLIENT);

        CHANNEL.registerMessage(
            MergedTerminalSetStackPacket.Handler.class,
            MergedTerminalSetStackPacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            MergedTerminalRenamePacket.Handler.class,
            MergedTerminalRenamePacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            MergedTerminalScrollReplacePacket.Handler.class,
            MergedTerminalScrollReplacePacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            MergedTerminalMatrixUploadPacket.Handler.class,
            MergedTerminalMatrixUploadPacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            RequestReplaceCandidatesPacket.Handler.class,
            RequestReplaceCandidatesPacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            ReplaceCandidatesPacket.Handler.class,
            ReplaceCandidatesPacket.class,
            discriminator++,
            Side.CLIENT);

        CHANNEL.registerMessage(
            InfinityCellStatsPacket.Handler.class,
            InfinityCellStatsPacket.class,
            discriminator++,
            Side.SERVER);

        CHANNEL.registerMessage(
            InfinityCellStatsResponsePacket.Handler.class,
            InfinityCellStatsResponsePacket.class,
            discriminator++,
            Side.CLIENT);
    }
}
