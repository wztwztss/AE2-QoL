package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayer;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S: 游戏内配置页面提交某项配置修改。
 * 服务端校验 key/取值范围与 OP 权限（等级 2，与 /ae2qof 一致）后应用，并广播 ConfigUpdatePacket 给所有客户端。
 */
public class ConfigSetPacket implements IMessage {

    private String key;
    private String value;

    public ConfigSetPacket() {}

    public ConfigSetPacket(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int klen = buf.readShort();
            byte[] kbytes = new byte[klen];
            buf.readBytes(kbytes);
            key = new String(kbytes, java.nio.charset.StandardCharsets.UTF_8);
            int vlen = buf.readShort();
            byte[] vbytes = new byte[vlen];
            buf.readBytes(vbytes);
            value = new String(vbytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            key = null;
            value = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] kbytes = key == null ? new byte[0] : key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] vbytes = value == null ? new byte[0] : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(kbytes.length);
        buf.writeBytes(kbytes);
        buf.writeShort(vbytes.length);
        buf.writeBytes(vbytes);
    }

    public static class Handler implements IMessageHandler<ConfigSetPacket, IMessage> {

        @Override
        public IMessage onMessage(ConfigSetPacket message, MessageContext ctx) {
            try {
                final EntityPlayer player = ctx.getServerHandler().playerEntity;
                if (player == null || message.key == null || message.value == null) {
                    return null;
                }
                // 仅 OP（等级 2）可改配置，防非授权玩家篡改。
                if (!player.canCommandSenderUseCommand(2, "ae2qof")) {
                    return null;
                }
                final String key = message.key;
                final String value = message.value;
                ServerTerminalHelper.scheduleServerTask(() -> {
                    try {
                        if (Config.applySetting(key, value)) {
                            ModNetwork.CHANNEL.sendToAll(new ConfigUpdatePacket(
                                Config.exIOPortTransferContentsRate,
                                Config.smartDoublingMaxRounds,
                                Config.neiOverlayEnabled));
                        } else {
                            MyMod.LOG.warn("[AE2QoL] Rejected config change " + key + "=" + value);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return null;
        }
    }
}
