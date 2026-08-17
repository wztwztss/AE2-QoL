package com.wztwzt.ae2_qof.network;

import net.minecraft.client.Minecraft;

import com.wztwzt.ae2_qof.Config;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * S2C: 服务端向客户端同步当前配置（玩家登录时推送 + 配置修改后广播）。
 */
public class ConfigUpdatePacket implements IMessage {

    private int io;
    private int rounds;
    private boolean overlay;

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
        public IMessage onMessage(final ConfigUpdatePacket message, MessageContext ctx) {
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.func_152344_a(new Runnable() {

                @Override
                public void run() {
                    try {
                        Config.applyAll(message.io, message.rounds, message.overlay);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });
            return null;
        }
    }
}
