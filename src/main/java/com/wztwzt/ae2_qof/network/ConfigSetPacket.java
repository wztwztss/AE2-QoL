package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;

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
                final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                if (player == null) {
                    return null;
                }
                final String key = message.key;
                final String value = message.value;
                if (key == null || value == null) {
                    sendResult(player, "", false, "无效的配置请求");
                    return null;
                }
                // NEI 开关是客户端本地渲染偏好，不再接受 C2S 改写服务端文件。
                if ("nei_overlay_enabled".equals(key)) {
                    sendResult(player, key, false, "NEI 开关是客户端本地设置，请用配置页或 OV 按钮修改");
                    return null;
                }
                // 仅 OP（等级 2）可改服务端配置，防非授权玩家篡改。
                if (!player.canCommandSenderUseCommand(2, "ae2qof")) {
                    sendResult(player, key, false, "需要 OP 权限等级 2");
                    return null;
                }
                ServerTerminalHelper.scheduleServerTask(() -> {
                    try {
                        if (Config.applySetting(key, value)) {
                            ModNetwork.CHANNEL.sendToAll(
                                new ConfigUpdatePacket(
                                    Config.exIOPortTransferContentsRate,
                                    Config.smartDoublingMaxRounds,
                                    Config.neiOverlayEnabled));
                            sendResult(player, key, true, "");
                        } else {
                            String reason = Config.getLastWriteError();
                            if (reason == null || reason.isEmpty()) {
                                reason = "值无效或超出范围";
                            }
                            sendResult(player, key, false, reason);
                            MyMod.LOG.warn("[AE2QoL] Rejected config change " + key + "=" + value + " (" + reason + ")");
                        }
                    } catch (Throwable t) {
                        sendResult(player, key, false, "服务端应用配置失败");
                        MyMod.LOG.error("Config set failed", t);
                    }
                });
            } catch (Throwable t) {
                MyMod.LOG.error("Config set failed", t);
            }
            return null;
        }

        private static void sendResult(EntityPlayerMP player, String key, boolean success, String message) {
            try {
                ModNetwork.CHANNEL.sendTo(new ConfigSetResultPacket(key, success, message), player);
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] Failed to send config result: " + t.getMessage());
            }
        }
    }
}
