package com.wztwzt.ae2_qof.network;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import cn.dancingsnow.aeinfinitycell.nei.InfinityCellViewPreview;
import cn.dancingsnow.aeinfinitycell.nei.InfinityCellViewPreview.Channel;
import cn.dancingsnow.aeinfinitycell.nei.InfinityCellViewPreview.Entry;
import cn.dancingsnow.aeinfinitycell.nei.InfinityCellViewPreview.Page;
import cn.dancingsnow.aeinfinitycell.storage.EssentiaStackKey;
import cn.dancingsnow.aeinfinitycell.storage.FluidStackKey;
import cn.dancingsnow.aeinfinitycell.storage.InfinityCellRecord;
import cn.dancingsnow.aeinfinitycell.storage.ItemStackKey;

import com.wztwzt.ae2_qof.client.InfinityCellViewCache;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 无限元件 NEI 预览应答（S2C，3.19.0-fix24）：pages 为 null 表示该 id 在服务端无数据。
 *
 * <p>负载为「通道分页 + 每通道前 N 条」的裁剪快照，不是整份存档；配合编码预算上限，
 * 避免 1.7.10 S3F 自定义负载长度（short ≤ 32767 字节）溢出导致整包丢弃。
 */
public class InfinityCellViewResponsePacket implements IMessage {

    /** 编码预算：留出 S3F 长度前缀与其余包头的余量。 */
    private static final int MAX_PAYLOAD_BYTES = 28000;

    private String storageId;
    private List<Page> pages;

    public InfinityCellViewResponsePacket() {}

    public InfinityCellViewResponsePacket(UUID id, List<Page> pages) {
        this.storageId = id == null ? "" : id.toString();
        this.pages = pages;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.storageId = InfinityCellStatsPacket.readString(buf);
            if (!buf.readBoolean()) {
                this.pages = null;
                return;
            }
            int pageCount = buf.readUnsignedByte();
            List<Page> decoded = new ArrayList<Page>(pageCount);
            for (int p = 0; p < pageCount; p++) {
                Channel channel = Channel.values()[buf.readUnsignedByte()];
                long totalTypes = buf.readLong();
                int entryCount = buf.readUnsignedShort();
                List<Entry<?>> entries = new ArrayList<Entry<?>>(entryCount);
                for (int e = 0; e < entryCount; e++) {
                    boolean hasKey = buf.readBoolean();
                    NBTTagCompound keyTag = hasKey ? ByteBufUtils.readTag(buf) : null;
                    BigInteger amount = new BigInteger(readString(buf));
                    Entry<?> entry = decodeEntry(channel, keyTag, amount);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
                decoded.add(InfinityCellViewPreview.page(channel, entries, totalTypes));
            }
            this.pages = decoded;
        } catch (Throwable t) {
            this.pages = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        InfinityCellStatsPacket.writeString(buf, storageId == null ? "" : storageId);
        if (pages == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        int pageCount = Math.min(pages.size(), 255);
        buf.writeByte(pageCount);
        for (int p = 0; p < pageCount; p++) {
            Page page = pages.get(p);
            buf.writeByte(page.getChannel()
                .ordinal());
            buf.writeLong(page.getTotalTypes());
            List<? extends Entry<?>> entries = page.getEntries();
            int entryCount = Math.min(entries.size(), 65535);
            buf.writeShort(entryCount);
            for (int e = 0; e < entryCount; e++) {
                Entry<?> entry = entries.get(e);
                NBTTagCompound keyTag = encodeKey(page.getChannel(), entry);
                buf.writeBoolean(keyTag != null);
                if (keyTag != null) {
                    ByteBufUtils.writeTag(buf, keyTag);
                }
                writeString(buf, entry.getAmount() == null ? "0" : entry.getAmount().toString());
            }
        }
    }

    private static NBTTagCompound encodeKey(Channel channel, Entry<?> entry) {
        Object key = entry.getKey();
        long amount = entry.getStackSize();
        if (key instanceof ItemStackKey itemKey) {
            return itemKey.writeToNBT(amount);
        }
        if (key instanceof FluidStackKey fluidKey) {
            return fluidKey.writeToNBT(amount);
        }
        if (key instanceof EssentiaStackKey essentiaKey) {
            return essentiaKey.writeToNBT(amount);
        }
        return null;
    }

    private static Entry<?> decodeEntry(Channel channel, NBTTagCompound keyTag, BigInteger amount) {
        if (channel == Channel.EU) {
            return InfinityCellViewPreview.entry(null, amount);
        }
        if (keyTag == null) {
            return null;
        }
        if (channel == Channel.ITEMS) {
            return InfinityCellViewPreview.entry(ItemStackKey.readFromNBT(keyTag), amount);
        }
        if (channel == Channel.FLUIDS) {
            return InfinityCellViewPreview.entry(FluidStackKey.readFromNBT(keyTag), amount);
        }
        if (channel == Channel.ESSENTIA) {
            return InfinityCellViewPreview.entry(EssentiaStackKey.readFromNBT(keyTag), amount);
        }
        return null;
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 服务端裁剪编码：按通道取预览分页，超出字节预算的条目直接截断。
     */
    public static List<Page> encode(InfinityCellRecord record, int perChannelLimit) {
        if (record == null) {
            return null;
        }
        List<Page> all = InfinityCellViewPreview.pages(record, perChannelLimit);
        if (all.isEmpty()) {
            return Collections.emptyList();
        }

        // 近似预算：条目 NBT 与数量字符串按 UTF-16 粗略估算，超界即停
        int used = 16;
        List<Page> trimmed = new ArrayList<Page>(all.size());
        for (Page page : all) {
            List<Entry<?>> kept = new ArrayList<Entry<?>>();
            for (Entry<?> entry : page.getEntries()) {
                NBTTagCompound keyTag = encodeKey(page.getChannel(), entry);
                int cost = 8 + (keyTag != null ? keyTag.toString()
                    .length() * 2 : 0) + (entry.getAmount() == null ? 0 : entry.getAmount()
                        .toString()
                        .length() * 2);
                if (used + cost > MAX_PAYLOAD_BYTES) {
                    break;
                }
                used += cost;
                kept.add(entry);
            }
            if (!kept.isEmpty()) {
                trimmed.add(InfinityCellViewPreview.page(page.getChannel(), kept, page.getTotalTypes()));
            }
            if (used >= MAX_PAYLOAD_BYTES) {
                break;
            }
        }
        return trimmed;
    }

    public static class Handler implements IMessageHandler<InfinityCellViewResponsePacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(InfinityCellViewResponsePacket message, MessageContext ctx) {
            try {
                InfinityCellViewCache.put(UUID.fromString(message.storageId), message.pages);
            } catch (Throwable ignored) {}
            return null;
        }
    }
}
