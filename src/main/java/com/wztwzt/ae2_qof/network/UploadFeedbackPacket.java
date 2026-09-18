package com.wztwzt.ae2_qof.network;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 样板上传结果回执（S2C）。
 *
 * <p>背景（fix41）：上传失败时旧实现只写服务端日志，玩家在游戏里看不到任何反馈，
 * 「目标机器被拆了 / 槽位满了 / 没有注入权限」全都表现为「点了没反应」。
 * 这里把失败原因回传给客户端，由 {@code CommonProxy#handleUploadFeedback} 显示到聊天栏。
 */
public class UploadFeedbackPacket implements IMessage {

    /** 语言键（如 {@code ae2_qof.info.upload_target_missing}），客户端再翻译成当前语言。 */
    public String messageKey;

    public UploadFeedbackPacket() {
        this.messageKey = null;
    }

    public UploadFeedbackPacket(String messageKey) {
        this.messageKey = messageKey;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int len = buf.readShort();
            if (len <= 0 || len > 512) {
                this.messageKey = null;
                return;
            }
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            this.messageKey = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            this.messageKey = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        String s = this.messageKey == null ? "" : this.messageKey;
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 512) {
            bytes = new byte[0];
        }
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<UploadFeedbackPacket, IMessage> {

        @Override
        public IMessage onMessage(UploadFeedbackPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                MyMod.proxy.handleUploadFeedback(message);
            }
            return null;
        }
    }
}