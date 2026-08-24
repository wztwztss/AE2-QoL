package com.wztwzt.ae2_qof.network;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 二合一终端网络空白样板数量回传（S2C，按需发送）。
 * <p>
 * 空白样板槽显示网络内空白样板的总数量（与其它编码终端共享、动态变化），
 * 数量变化时由服务端 detectAndSendChanges 推送一次。
 * Handler 仅作分发：客户端真逻辑在 ClientProxy.handleMergedTerminalBlankCount（#74）。
 */
public class MergedTerminalBlankCountPacket implements IMessage {

    public long count;

    public MergedTerminalBlankCountPacket() {
        this.count = 0;
    }

    public MergedTerminalBlankCountPacket(long count) {
        this.count = count;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.count = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.count);
    }

    public static class Handler implements IMessageHandler<MergedTerminalBlankCountPacket, IMessage> {

        @Override
        public IMessage onMessage(MergedTerminalBlankCountPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                MyMod.proxy.handleMergedTerminalBlankCount(message);
            }
            return null;
        }
    }
}
