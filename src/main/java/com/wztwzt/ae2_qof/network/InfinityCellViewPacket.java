package com.wztwzt.ae2_qof.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import cn.dancingsnow.aeinfinitycell.ServerWorldAccess;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellDataAccess;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellStorage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

/**
 * 无限元件 NEI 预览请求（C2S，3.19.0-fix24）。
 *
 * <p>无限元件内容存服务端世界存档（{@link InfinityCellDataAccess} 在专用服客户端恒为 null），
 * 因此 NEI 的 U 键查看在联机时拿不到任何数据。此包按 storageId 向服务端索取
 * 「每个通道前 N 条」的预览快照，由 {@link InfinityCellViewResponsePacket} 回传。
 */
public class InfinityCellViewPacket implements IMessage {

    private String storageId;

    public InfinityCellViewPacket() {}

    public InfinityCellViewPacket(UUID id) {
        this.storageId = id == null ? "" : id.toString();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.storageId = InfinityCellStatsPacket.readString(buf);
        } catch (Throwable t) {
            this.storageId = "";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        InfinityCellStatsPacket.writeString(buf, storageId == null ? "" : storageId);
    }

    public static class Handler implements IMessageHandler<InfinityCellViewPacket, IMessage> {

        @Override
        public IMessage onMessage(final InfinityCellViewPacket message, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // 归队到服务端主线程：世界存档数据不允许在 Netty IO 线程访问
            ServerTerminalHelper.scheduleServerTask(() -> {
                UUID id = null;
                try {
                    id = UUID.fromString(message.storageId);
                } catch (Throwable bad) {
                    return;
                }
                try {
                    // 只回传磁盘上已真实存在的元件，避免随机 UUID 触发缓存膨胀（P1-017）
                    if (!InfinityCellStorage.getInstance()
                        .hasCellFile(id)) {
                        ModNetwork.CHANNEL.sendTo(new InfinityCellViewResponsePacket(id, null), player);
                        return;
                    }
                    InfinityCellRecord record = InfinityCellDataAccess.getOrCreate(id, ServerWorldAccess.getServerWorld());
                    ModNetwork.CHANNEL.sendTo(
                        new InfinityCellViewResponsePacket(
                            id,
                            InfinityCellViewResponsePacket.encode(
                                record,
                                cn.dancingsnow.aeinfinitycell.Config.neiPreviewEntriesPerChannel)),
                        player);
                } catch (Throwable ignored) {}
            });
            return null;
        }
    }
}
