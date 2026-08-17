package com.wztwzt.ae2_qof;

import net.minecraft.command.CommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import com.wztwzt.ae2_qof.client.CommandOverlay;
import com.wztwzt.ae2_qof.client.event.GuiUploadButtonHandler;
import com.wztwzt.ae2_qof.client.event.KeyInputHandler;
import com.wztwzt.ae2_qof.client.event.KnifeNameCopyHandler;
import com.wztwzt.ae2_qof.client.event.MergedTerminalPanelHandler;
import com.wztwzt.ae2_qof.client.render.CraftingNotificationOverlay;
import com.wztwzt.ae2_qof.client.render.RenderBlockTransceiver;
import com.wztwzt.ae2_qof.client.render.WirelessHighlightRenderer;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        RenderingRegistry.registerBlockHandler(RenderBlockTransceiver.INSTANCE);
        GuiUploadButtonHandler.register();
        MergedTerminalPanelHandler.register();
        KeyInputHandler.register();
        KnifeNameCopyHandler.register();
        MinecraftForge.EVENT_BUS.register(WirelessHighlightRenderer.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new ClientRenderEventHandler());
        try {
            codechicken.nei.guihook.GuiContainerManager
                .addTooltipHandler(new com.wztwzt.ae2_qof.client.nei.NetworkTooltipHandler());
        } catch (Throwable ignored) {}
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {
        super.serverStarting(event);
        ((CommandHandler) event.getServer()
            .getCommandManager()).registerCommand(new CommandOverlay());
    }

    @SideOnly(Side.CLIENT)
    public static class ClientRenderEventHandler {

        @SubscribeEvent
        public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
            if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
                CraftingNotificationOverlay.INSTANCE.draw();
            }
        }
    }
}
