package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public class WirelessChannelSyncPacket implements IMessage {

    public List<String> channels;

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
            // 恶意包防护：预分配容量钳制，防止 new ArrayList<>(巨量) OOM（#45），超界按空列表处理
            if (size < 0 || size > 256) {
                size = 0;
            }
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
        public IMessage onMessage(WirelessChannelSyncPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                MyMod.proxy.handleWirelessChannelSync(message);
            }
            return null;
        }
    }
}
