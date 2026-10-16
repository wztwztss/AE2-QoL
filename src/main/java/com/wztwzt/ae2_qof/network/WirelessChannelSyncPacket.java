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
            // 3.35.0-fix13（审查 P2-4）：恶意包防护保留（负值/巨型预分配仍拒收），
            // 但"合法超限"不再静默清空——旧式 size>256 → size=0 会把客户端列表永久清空
            //（服务端频道数超 256 的合法场景被防攻击检查误伤，且无任何日志）。
            // 现在负值才置空；超限改为**读前 256 条并 WARN**（服务端已在 addChannel 拒绝第 257 个，
            // 正常不会出现；出现即说明存量旧档超限，显示前 256 条好过全部消失）。
            if (size < 0) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 频道同步包 size 为负（{}），按空列表处理", size);
                size = 0;
            } else if (size > 256) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn(
                    "[AE2QoL] 频道同步包含 {} 条（>256），仅载入前 256 条——请清理多余频道",
                    size);
                size = 256;
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
