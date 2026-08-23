package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.wireless.TileWirelessTransceiver;
import com.wztwzt.ae2_qof.wireless.WirelessData;
import com.wztwzt.ae2_qof.wireless.WirelessWorldData;
import com.wztwzt.ae2_qof.wireless.gui.ContainerWireless;
import com.wztwzt.ae2_qof.wireless.link.WirelessBlockLinkManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client-to-server packet for wireless transceiver GUI actions.
 */
public class WirelessActionPacket implements IMessage {

    public static final int ACTION_ADD_CHANNEL = 0;
    public static final int ACTION_REMOVE_CHANNEL = 1;
    public static final int ACTION_SET_MODE = 2;
    public static final int ACTION_DISCONNECT = 3;
    public static final int ACTION_SET_FREQUENCY = 4;
    public static final int ACTION_TOGGLE_HIGHLIGHT = 5;

    private int action;
    private int x, y, z;
    private String channelName;
    private boolean modeValue;

    public WirelessActionPacket() {}

    public WirelessActionPacket(int action, int x, int y, int z, String channelName, boolean modeValue) {
        this.action = action;
        this.x = x;
        this.y = y;
        this.z = z;
        this.channelName = channelName;
        this.modeValue = modeValue;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.action = buf.readInt();
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
            boolean hasChannel = buf.readBoolean();
            this.channelName = hasChannel ? readString(buf) : null;
            this.modeValue = buf.readBoolean();
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            this.action = -1;
            this.channelName = null;
            this.modeValue = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeBoolean(channelName != null);
        if (channelName != null) {
            writeString(buf, channelName);
        }
        buf.writeBoolean(modeValue);
    }

    private void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private String readString(ByteBuf buf) {
        int len = buf.readShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<WirelessActionPacket, IMessage> {

        @Override
        public IMessage onMessage(WirelessActionPacket msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // 归队到服务端 tick 线程执行，避免 Netty IO 线程并发访问 Tile/World
            ServerTerminalHelper.scheduleServerTask(() -> handleServer(player, msg));
            return null;
        }

        private void handleServer(EntityPlayerMP player, WirelessActionPacket msg) {
            try {
                TileEntity te = player.worldObj.getTileEntity(msg.x, msg.y, msg.z);
                if (!(te instanceof TileWirelessTransceiver twt)) return;

                // 权限校验：仅允许操作玩家自己当前打开 GUI 的收发器，防止对任意坐标 Tile 发动作
                if (!(player.openContainer instanceof ContainerWireless cw) || cw.tile != twt) {
                    return;
                }

                switch (msg.action) {
                    case ACTION_ADD_CHANNEL:
                        handleAddChannel(twt, msg, player);
                        break;
                    case ACTION_REMOVE_CHANNEL:
                        handleRemoveChannel(twt, msg, player);
                        break;
                    case ACTION_SET_MODE:
                        handleSetMode(twt, msg);
                        break;
                    case ACTION_DISCONNECT:
                        handleDisconnect(twt);
                        break;
                    case ACTION_SET_FREQUENCY:
                        handleSetFrequency(twt, msg);
                        break;
                    case ACTION_TOGGLE_HIGHLIGHT:
                        handleToggleHighlight(twt, msg, player);
                        break;
                    default:
                        return;
                }

                if (twt.getWorldObj() != null) {
                    twt.markDirty();
                    twt.getWorldObj()
                        .markBlockForUpdate(twt.xCoord, twt.yCoord, twt.zCoord);
                }
            } catch (Throwable t) {
                MyMod.LOG.error("Wireless action failed", t);
            }
        }

        private void handleAddChannel(TileWirelessTransceiver twt, WirelessActionPacket msg, EntityPlayerMP player) {
            if (msg.channelName == null || msg.channelName.trim()
                .isEmpty()) return;

            String channelName = msg.channelName.trim();
            WirelessData.instance()
                .addGlobalChannel(channelName);

            syncChannelsToClient(player);
        }

        private void handleRemoveChannel(TileWirelessTransceiver twt, WirelessActionPacket msg, EntityPlayerMP player) {
            String freqToRemove = msg.channelName;
            if (freqToRemove == null || freqToRemove.trim()
                .isEmpty()) {
                freqToRemove = twt.getFrequency();
            }

            if (freqToRemove != null && !freqToRemove.isEmpty()) {
                TileWirelessTransceiver sender = WirelessData.instance()
                    .getSender(freqToRemove);
                if (sender != null) {
                    WirelessData.instance()
                        .unregister(freqToRemove, sender.getWorldObj());
                    sender.destroyWirelessConnection();
                    sender.setFrequency("");
                    sender.setMode(false);
                    sender.setConnected(false);
                }
                WirelessWorldData.get(twt.getWorldObj())
                    .removeChannel(freqToRemove);
                WirelessBlockLinkManager.instance()
                    .unregister(freqToRemove);
            }

            if (twt.getFrequency() != null && twt.getFrequency()
                .equals(freqToRemove)) {
                twt.destroyWirelessConnection();
                twt.setFrequency("");
                twt.setMode(false);
                twt.setConnected(false);
            }

            syncChannelsToClient(player);
        }

        private void handleSetMode(TileWirelessTransceiver twt, WirelessActionPacket msg) {
            boolean newMode = msg.modeValue;
            String freq = twt.getFrequency();

            if (newMode && !twt.isMode()) {
                // Switching to sender: register, clear originalSenderPos
                twt.setOriginalSenderPos("");
                if (freq != null && !freq.isEmpty()) {
                    WirelessData.instance()
                        .register(freq, twt);
                }
                twt.setPaused(false);
            } else if (!newMode && twt.isMode()) {
                // Switching to receiver: unregister, store sender position, destroy connection
                if (freq != null && !freq.isEmpty()) {
                    WirelessData.instance()
                        .unregister(freq, twt.getWorldObj());
                    TileWirelessTransceiver sender = WirelessData.instance()
                        .getSender(freq);
                    if (sender != null && !sender.isInvalid()) {
                        String senderPos = sender.getWorldObj().provider.dimensionId + ":"
                            + sender.xCoord
                            + ":"
                            + sender.yCoord
                            + ":"
                            + sender.zCoord;
                        twt.setOriginalSenderPos(senderPos);
                    }
                }
                twt.destroyWirelessConnection();
                twt.setPaused(false);
            }

            twt.setMode(newMode);
        }

        private void handleDisconnect(TileWirelessTransceiver twt) {
            twt.destroyWirelessConnection();
            twt.setPaused(true);
        }

        private void handleSetFrequency(TileWirelessTransceiver twt, WirelessActionPacket msg) {
            if (msg.channelName == null) return;
            String newFreq = msg.channelName.trim();
            String oldFreq = twt.getFrequency();

            if (twt.isMode() && oldFreq != null && !oldFreq.isEmpty()) {
                WirelessData.instance()
                    .unregister(oldFreq);
            }

            twt.destroyWirelessConnection();
            twt.setFrequency(newFreq);
            twt.setOriginalSenderPos("");
            twt.setPaused(false);

            if (twt.isMode() && !newFreq.isEmpty()) {
                WirelessData.instance()
                    .register(newFreq, twt);
            }
        }

        private void syncChannelsToClient(EntityPlayerMP player) {
            ModNetwork.CHANNEL.sendTo(
                new WirelessChannelSyncPacket(
                    WirelessData.instance()
                        .getAllFrequencies()),
                player);
        }

        private void handleToggleHighlight(TileWirelessTransceiver twt, WirelessActionPacket msg,
            EntityPlayerMP player) {
            String freq = twt.getFrequency();
            if (freq == null || freq.isEmpty()) {
                ModNetwork.CHANNEL.sendTo(new WirelessHighlightPacket(new java.util.ArrayList<int[]>(), false), player);
                return;
            }
            // 目标状态由包参数携带（客户端发送时取反本地状态）：
            // 服务端 JVM 读不到客户端静态字段 ClientState.highlightEnabled，专用服上恒 false 导致只能开不能关（#47）
            boolean currentlyEnabled = msg.modeValue;
            if (currentlyEnabled) {
                ModNetwork.CHANNEL.sendTo(new WirelessHighlightPacket(new java.util.ArrayList<int[]>(), false), player);
            } else {
                java.util.List<com.wztwzt.ae2_qof.wireless.link.WirelessBlockLinkData> links = com.wztwzt.ae2_qof.wireless.link.WirelessBlockLinkManager
                    .instance()
                    .getAllLinks();
                java.util.List<int[]> positions = new java.util.ArrayList<int[]>();
                for (com.wztwzt.ae2_qof.wireless.link.WirelessBlockLinkData link : links) {
                    if (freq.equals(link.frequency)) {
                        positions.add(new int[] { link.dimension, link.x, link.y, link.z });
                    }
                }
                ModNetwork.CHANNEL.sendTo(new WirelessHighlightPacket(positions, true), player);
            }
        }
    }
}
