package com.wztwzt.ae2_qof.network;

import net.minecraft.client.Minecraft;

import com.wztwzt.ae2_qof.client.ClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 二合一终端编码成功后的机器中文名回传（S2C），用于面板即时反馈显示。
 */
public class MergedTerminalResultPacket implements IMessage {

    private String machineName;

    public MergedTerminalResultPacket() {
        this.machineName = "";
    }

    public MergedTerminalResultPacket(String machineName) {
        this.machineName = machineName != null ? machineName : "";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int len = buf.readInt();
            if (len < 0 || len > 4096) {
                this.machineName = "";
                return;
            }
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            this.machineName = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            this.machineName = "";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] bytes = this.machineName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<MergedTerminalResultPacket, IMessage> {

        @Override
        public IMessage onMessage(MergedTerminalResultPacket message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) {
                return null;
            }
            mc.func_152344_a(() -> ClientState.mergedMachineName = message.machineName);
            return null;
        }
    }
}