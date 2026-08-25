package com.wztwzt.ae2_qof.network;

import java.util.UUID;

import com.wztwzt.ae2_qof.client.InfinityCellTooltipCache;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import io.netty.buffer.ByteBuf;

/**
 * 无限磁盘统计应答（S2C，3.13.0）：stats 为 null 表示该 id 在服务端无数据。
 */
public class InfinityCellStatsResponsePacket implements IMessage {

    private String storageId;
    private long[] stats;

    public InfinityCellStatsResponsePacket() {}

    public InfinityCellStatsResponsePacket(UUID id, long[] stats) {
        this.storageId = id == null ? "" : id.toString();
        this.stats = stats;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = InfinityCellStatsPacket.readString(buf);
        boolean has = buf.readBoolean();
        if (has) {
            stats = new long[8];
            for (int i = 0; i < 8; i++) {
                stats[i] = buf.readLong();
            }
        } else {
            stats = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        InfinityCellStatsPacket.writeString(buf, storageId == null ? "" : storageId);
        buf.writeBoolean(stats != null);
        if (stats != null) {
            for (int i = 0; i < 8; i++) {
                buf.writeLong(stats[i]);
            }
        }
    }

    public static class Handler implements IMessageHandler<InfinityCellStatsResponsePacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(InfinityCellStatsResponsePacket message, MessageContext ctx) {
            try {
                UUID id = UUID.fromString(message.storageId);
                InfinityCellTooltipCache.put(id, message.stats);
            } catch (Throwable ignored) {}
            return null;
        }
    }
}
