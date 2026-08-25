package com.wztwzt.ae2_qof.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import cn.dancingsnow.aeinfinitycell.ServerWorldAccess;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellDataAccess;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

/**
 * 无限磁盘统计请求（C2S，3.13.0）：悬停 tooltip 时客户端按 storageId 请求存储统计。
 * 内容存服务端世界存档（InfinityCellDataAccess 客户端恒 null），必须经服务端汇总回传。
 */
public class InfinityCellStatsPacket implements IMessage {

    private String storageId;

    public InfinityCellStatsPacket() {}

    public InfinityCellStatsPacket(UUID id) {
        this.storageId = id == null ? "" : id.toString();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = readString(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, storageId == null ? "" : storageId);
    }

    static void writeString(ByteBuf buf, String s) {
        buf.writeShort(s.length());
        for (int i = 0; i < s.length(); i++) {
            buf.writeChar(s.charAt(i));
        }
    }

    static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(buf.readChar());
        }
        return sb.toString();
    }

    /** 统计布局：[物品类型, 物品量, 流体类型, 流体量, 源质类型, 源质量, EU类型, EU量]。 */
    static long[] collectStats(InfinityCellRecord record) {
        long[] s = new long[8];
        s[0] = record.getUsedItemTypes();
        s[1] = record.getStoredItemUnits();
        s[2] = record.getUsedFluidTypes();
        s[3] = record.getStoredFluidUnits();
        s[4] = record.getUsedEssentiaTypes();
        s[5] = record.getStoredEssentiaUnits();
        s[6] = record.getUsedEUTypes();
        s[7] = record.getStoredEUUnits();
        return s;
    }

    public static class Handler implements IMessageHandler<InfinityCellStatsPacket, IMessage> {

        @Override
        public IMessage onMessage(final InfinityCellStatsPacket message, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // 归队到服务端主线程：世界存档数据不允许在 Netty IO 线程访问
            com.wztwzt.ae2_qof.network.ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    UUID id;
                    try {
                        id = UUID.fromString(message.storageId);
                    } catch (IllegalArgumentException bad) {
                        return;
                    }
                    InfinityCellRecord record = InfinityCellDataAccess.getOrCreate(id, ServerWorldAccess.getServerWorld());
                    if (record == null) {
                        ModNetwork.CHANNEL.sendTo(new InfinityCellStatsResponsePacket(id, null), player);
                        return;
                    }
                    ModNetwork.CHANNEL.sendTo(new InfinityCellStatsResponsePacket(id, collectStats(record)), player);
                } catch (Throwable ignored) {}
            });
            return null;
        }
    }
}
