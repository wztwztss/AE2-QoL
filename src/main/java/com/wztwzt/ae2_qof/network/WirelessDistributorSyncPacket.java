package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 服务端 → 客户端：无线分配器的状态与可连接 GT ME 仓室列表。
 *
 * <p>
 * 只传坐标、颜色、状态和展示名；不传 ItemStack（避免专用服务器把客户端语言表
 * 当成服务端语言表，也避免伪造 NBT 撑大包体）。
 */
public class WirelessDistributorSyncPacket implements IMessage {

    public static final byte STATUS_AVAILABLE = 0;
    public static final byte STATUS_LINKED = 1;
    public static final byte STATUS_EXTERNAL = 2;
    public static final byte STATUS_UNAVAILABLE = 3;

    public static final int MAX_ENTRIES = 256;

    public static class Entry {

        public final String name;
        public final int dimension;
        public final int x;
        public final int y;
        public final int z;
        public final int color;
        public final byte status;

        public Entry(String name, int dimension, int x, int y, int z, int color, byte status) {
            this.name = name;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.status = status;
        }
    }

    private int x;
    private int y;
    private int z;
    private int color;
    private int radius;
    private int linkedCount;
    private int usedChannels;
    private int maxChannels;
    private boolean powered;
    private List<Entry> entries;

    public WirelessDistributorSyncPacket() {
        this.entries = new ArrayList<Entry>();
    }

    public WirelessDistributorSyncPacket(int x, int y, int z, int color, int radius, int linkedCount, int usedChannels,
        int maxChannels, boolean powered, List<Entry> entries) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.radius = radius;
        this.linkedCount = linkedCount;
        this.usedChannels = usedChannels;
        this.maxChannels = maxChannels;
        this.powered = powered;
        this.entries = entries != null ? entries : new ArrayList<Entry>();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
            this.color = buf.readInt();
            this.radius = buf.readInt();
            this.linkedCount = buf.readInt();
            this.usedChannels = buf.readInt();
            this.maxChannels = buf.readInt();
            this.powered = buf.readBoolean();
            int count = buf.readUnsignedShort();
            if (count > MAX_ENTRIES) count = MAX_ENTRIES;
            this.entries = new ArrayList<Entry>(count);
            for (int i = 0; i < count; i++) {
                String name = ByteBufUtils.readUTF8String(buf);
                int dimension = buf.readInt();
                int ex = buf.readInt();
                int ey = buf.readInt();
                int ez = buf.readInt();
                int eColor = buf.readInt();
                byte status = buf.readByte();
                this.entries.add(new Entry(name, dimension, ex, ey, ez, eColor, status));
            }
        } catch (Throwable t) {
            this.entries = new ArrayList<Entry>();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(color);
        buf.writeInt(radius);
        buf.writeInt(linkedCount);
        buf.writeInt(usedChannels);
        buf.writeInt(maxChannels);
        buf.writeBoolean(powered);
        int count = entries != null ? Math.min(entries.size(), MAX_ENTRIES) : 0;
        buf.writeShort(count);
        if (entries != null) {
            for (int i = 0; i < count; i++) {
                Entry entry = entries.get(i);
                String name = entry.name != null ? entry.name : "";
                if (name.length() > 128) name = name.substring(0, 128);
                ByteBufUtils.writeUTF8String(buf, name);
                buf.writeInt(entry.dimension);
                buf.writeInt(entry.x);
                buf.writeInt(entry.y);
                buf.writeInt(entry.z);
                buf.writeInt(entry.color);
                buf.writeByte(entry.status);
            }
        }
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getColor() {
        return color;
    }

    public int getRadius() {
        return radius;
    }

    public int getLinkedCount() {
        return linkedCount;
    }

    public int getUsedChannels() {
        return usedChannels;
    }

    public int getMaxChannels() {
        return maxChannels;
    }

    public boolean isPowered() {
        return powered;
    }

    public List<Entry> getEntries() {
        return entries != null ? entries : new ArrayList<Entry>();
    }

    public static class Handler implements IMessageHandler<WirelessDistributorSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(WirelessDistributorSyncPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                MyMod.proxy.handleWirelessDistributorSync(message);
            }
            return null;
        }
    }
}
