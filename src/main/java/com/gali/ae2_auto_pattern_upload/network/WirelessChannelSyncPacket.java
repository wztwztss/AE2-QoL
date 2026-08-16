package com.gali.ae2_auto_pattern_upload.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.gali.ae2_auto_pattern_upload.wireless.gui.GuiWireless;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class WirelessChannelSyncPacket implements IMessage {

    private List<String> channels;

    public WirelessChannelSyncPacket() {
        this.channels = new ArrayList<String>();
    }

    public WirelessChannelSyncPacket(List<String> channels) {
        this.channels = channels;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int size = buf.readInt();
            channels = new ArrayList<String>(size);
            for (int i = 0; i < size; i++) {
                int len = buf.readShort();
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                channels.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            channels = new ArrayList<String>();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(channels.size());
        for (String ch : channels) {
            byte[] bytes = ch.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    public static class Handler implements IMessageHandler<WirelessChannelSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(final WirelessChannelSyncPacket message, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(new Runnable() {

                    @Override
                    public void run() {
                        net.minecraft.client.gui.GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                        if (screen instanceof GuiWireless) {
                            ((GuiWireless) screen).syncChannelList(message.channels);
                        }
                    }
                });
            return null;
        }
    }
}
