package com.wztwzt.ae2_qof.network;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * S2C: 服务端向客户端同步当前配置（玩家登录时推送 + 配置修改后广播）。
 * Handler 仅作分发：客户端真逻辑在 ClientProxy.handleConfigUpdate（#74）。
 */
public class ConfigUpdatePacket implements IMessage {

    public int io;
    public int rounds;
    public boolean overlay;

    public ConfigUpdatePacket() {}

    public ConfigUpdatePacket(int io, int rounds, boolean overlay) {
        this.io = io;
        this.rounds = rounds;
        this.overlay = overlay;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            io = buf.readInt();
            rounds = buf.readInt();
            overlay = buf.readBoolean();
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            io = Config.exIOPortTransferContentsRate;
            rounds = Config.smartDoublingMaxRounds;
            overlay = Config.neiOverlayEnabled;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(io);
        buf.writeInt(rounds);
        buf.writeBoolean(overlay);
    }

    public static class Handler implements IMessageHandler<ConfigUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(ConfigUpdatePacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                MyMod.proxy.handleConfigUpdate(message);
            }
            return null;
        }
    }
}
