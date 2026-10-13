package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 3.32.0：载荷从「一台仓一行」改成「**一台主机一行 + 仓明细**」。
 *
 * <p>注意名称不在这里传：旧实现把服务端算好的名字（{@code ItemStack.getDisplayName()}）当字符串发下来，
 * 专用服务器上必然是英文（服务端语言表只有 en_US）。现在只传 **MTE id**，
 * 名称与图标一律由客户端本地解析 —— 与库存统计终端 3.31.0 的修法同源。
 */
public class HatchListSyncPacket implements IMessage {

    private UUID owner;
    private int frequency;
    /** 命中的仓总数（与 hostCount 不是同一基数）。 */
    private int totalCount;
    private int hostCount;
    private int inputCount;
    private int outputCount;
    private List<HatchListCache.HatchEntry> entries;

    public HatchListSyncPacket() {}

    public HatchListSyncPacket(UUID owner, int frequency, int totalCount, int hostCount,
                               List<HatchListCache.HatchEntry> entries,
                               int inputCount, int outputCount) {
        this.owner = owner;
        this.frequency = frequency;
        this.totalCount = totalCount;
        this.hostCount = hostCount;
        this.entries = entries;
        this.inputCount = inputCount;
        this.outputCount = outputCount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            boolean hasOwner = buf.readBoolean();
            this.owner = hasOwner ? readUUID(buf) : null;
            this.frequency = buf.readInt();
            this.totalCount = buf.readInt();
            this.hostCount = buf.readInt();
            this.inputCount = buf.readInt();
            this.outputCount = buf.readInt();

            int count = buf.readUnsignedShort();
            this.entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int displayMetaId = buf.readInt();
                boolean hostKnown = buf.readBoolean();
                boolean running = buf.readBoolean();
                long hostEut = buf.readLong();
                int hatchCount = buf.readUnsignedShort();
                int inCount = buf.readUnsignedShort();
                int outCount = buf.readUnsignedShort();
                long eut = buf.readLong();
                long realFlowEUt = buf.readLong();
                int index = buf.readUnsignedShort();
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                int dim = buf.readShort();
                String ownerName = ByteBufUtils.readUTF8String(buf);

                int subCount = buf.readUnsignedShort();
                List<HatchListCache.Sub> subs = new ArrayList<>(subCount);
                for (int s = 0; s < subCount; s++) {
                    subs.add(new HatchListCache.Sub(
                        buf.readInt(),
                        buf.readUnsignedByte(),
                        buf.readUnsignedByte(),
                        buf.readUnsignedShort(),
                        buf.readLong(),
                        buf.readLong(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readShort(),
                        buf.readBoolean()));
                }
                this.entries.add(new HatchListCache.HatchEntry(
                    displayMetaId, hostKnown, running, hostEut, hatchCount, inCount, outCount, eut, realFlowEUt, index,
                    x, y, z, dim, ownerName, subs));
            }
        } catch (Throwable t) {
            this.owner = null;
            this.frequency = 0;
            this.totalCount = 0;
            this.hostCount = 0;
            this.inputCount = 0;
            this.outputCount = 0;
            this.entries = new ArrayList<>();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(owner != null);
        if (owner != null) writeUUID(buf, owner);
        buf.writeInt(frequency);
        buf.writeInt(totalCount);
        buf.writeInt(hostCount);
        buf.writeInt(inputCount);
        buf.writeInt(outputCount);

        int count = entries != null ? entries.size() : 0;
        buf.writeShort(count);
        if (entries != null) {
            for (HatchListCache.HatchEntry e : entries) {
                buf.writeInt(e.displayMetaId);
                buf.writeBoolean(e.hostKnown);
                buf.writeBoolean(e.running);
                buf.writeLong(e.hostEut);
                buf.writeShort(e.hatchCount);
                buf.writeShort(e.inCount);
                buf.writeShort(e.outCount);
                buf.writeLong(e.eut);
                buf.writeLong(e.realFlowEUt);
                buf.writeShort(e.index);
                buf.writeInt(e.x);
                buf.writeInt(e.y);
                buf.writeInt(e.z);
                buf.writeShort(e.dim);
                ByteBufUtils.writeUTF8String(buf, e.ownerName != null ? e.ownerName : "");

                List<HatchListCache.Sub> subs = e.subs;
                buf.writeShort(subs.size());
                for (HatchListCache.Sub s : subs) {
                    buf.writeInt(s.metaId);
                    buf.writeByte(s.hatchType);
                    buf.writeByte(s.tier);
                    buf.writeShort(s.amps);
                    buf.writeLong(s.eut);
                    buf.writeLong(s.realFlowEUt);
                    buf.writeInt(s.x);
                    buf.writeInt(s.y);
                    buf.writeInt(s.z);
                    buf.writeShort(s.dim);
                    buf.writeBoolean(s.compat);
                }
            }
        }
    }

    public HatchListCache buildCache() {
        List<HatchListCache.HatchEntry> list = entries != null ? entries : new ArrayList<>();
        return new HatchListCache(totalCount, hostCount, new ArrayList<>(list),
            "输入仓: " + inputCount, "输出仓: " + outputCount);
    }

    private static void writeUUID(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUUID(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public static class Handler implements IMessageHandler<HatchListSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(HatchListSyncPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                com.wztwzt.ae2_qof.MyMod.proxy.handleHatchListSync(message);
            }
            return null;
        }
    }
}
