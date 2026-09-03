package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wztwzt.ae2_qof.hatch.adaptive.HatchListCache;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public class HatchListSyncPacket implements IMessage {

    private UUID owner;
    private int frequency;
    private int totalCount;
    private int inputCount;
    private int outputCount;
    private List<HatchListCache.HatchEntry> entries;

    public HatchListSyncPacket() {}

    public HatchListSyncPacket(UUID owner, int frequency, int totalCount,
                               List<HatchListCache.HatchEntry> entries,
                               int inputCount, int outputCount) {
        this.owner = owner;
        this.frequency = frequency;
        this.totalCount = totalCount;
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
            this.inputCount = buf.readInt();
            this.outputCount = buf.readInt();

            int count = buf.readUnsignedShort();
            this.entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int index = buf.readUnsignedShort();
                short metaId = buf.readShort();
                short machineMetaId = buf.readShort();
                String name = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
                int eut = buf.readInt();
                int realFlowEUt = buf.readInt();
                int tier = buf.readUnsignedByte();
                int amps = buf.readUnsignedShort();
                int hatchType = buf.readUnsignedByte();
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                int dim = buf.readShort();
                String ownerName = cpw.mods.fml.common.network.ByteBufUtils.readUTF8String(buf);
                this.entries.add(new HatchListCache.HatchEntry(
                    name, metaId, machineMetaId, eut, realFlowEUt, tier, amps, hatchType, index, x, y, z, dim, ownerName));
            }
        } catch (Throwable t) {
            this.owner = null;
            this.frequency = 0;
            this.totalCount = 0;
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
        buf.writeInt(inputCount);
        buf.writeInt(outputCount);

        int count = entries != null ? entries.size() : 0;
        buf.writeShort(count);
        if (entries != null) {
            for (HatchListCache.HatchEntry e : entries) {
                buf.writeShort(e.index);
                buf.writeShort(e.metaId);
                buf.writeShort(e.machineMetaId);
                cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, e.name != null ? e.name : "");
                buf.writeInt(e.eut);
                buf.writeInt(e.realFlowEUt);
                buf.writeByte(e.tier);
                buf.writeShort(e.amps);
                buf.writeByte(e.hatchType);
                buf.writeInt(e.x);
                buf.writeInt(e.y);
                buf.writeInt(e.z);
                buf.writeShort(e.dim);
                cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(buf, e.ownerName != null ? e.ownerName : "");
            }
        }
    }

    public HatchListCache buildCache() {
        List<HatchListCache.HatchEntry> list = entries != null ? entries : new ArrayList<>();
        return new HatchListCache(totalCount, new ArrayList<>(list),
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
