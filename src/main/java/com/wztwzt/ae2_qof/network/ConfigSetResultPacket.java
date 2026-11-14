package com.wztwzt.ae2_qof.network;

import java.nio.charset.StandardCharsets;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * S2C: 配置修改回执。修复旧版“客户端点应用后无条件显示成功、服务端静默拒绝”的问题。
 */
public class ConfigSetResultPacket implements IMessage {

    public String key = "";
    public boolean success;
    public String message = "";

    public ConfigSetResultPacket() {}

    public ConfigSetResultPacket(String key, boolean success, String message) {
        this.key = key == null ? "" : key;
        this.success = success;
        this.message = message == null ? "" : message;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            key = readString(buf);
            success = buf.readBoolean();
            message = readString(buf);
        } catch (Throwable t) {
            key = "";
            success = false;
            message = "配置回执解析失败";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, key);
        buf.writeBoolean(success);
        writeString(buf, message);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readShort();
        if (len <= 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<ConfigSetResultPacket, IMessage> {

        @Override
        public IMessage onMessage(ConfigSetResultPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                MyMod.proxy.handleConfigSetResult(message);
            }
            return null;
        }
    }
}
