package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public class ProvidersListS2CPacket implements IMessage {

    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_BYTES = 32000;

    public List<Long> ids;
    public List<String> names;
    public List<Integer> emptySlots;
    /** 供应器样板槽总数（空槽 + 已占用），用于界面显示「空闲/总数」。 */
    public List<Integer> totalSlots;
    /** 供应器的稳定位置标识（fix41），用于上传时可靠命中，避免内存地址过期后传丢。 */
    public List<String> locationKeys;
    /** 供应器图标（可选）：服务端尽力提供，超包或取不到时为空列表。 */
    public List<ItemStack> icons;
    public String recipeMap;
    public boolean forceGui;

    public ProvidersListS2CPacket() {
        this.ids = new ArrayList<Long>();
        this.names = new ArrayList<String>();
        this.emptySlots = new ArrayList<Integer>();
        this.totalSlots = new ArrayList<Integer>();
        this.locationKeys = new ArrayList<String>();
        this.icons = new ArrayList<ItemStack>();
        this.recipeMap = null;
        this.forceGui = false;
    }

    public ProvidersListS2CPacket(List<Long> ids, List<String> names, List<Integer> emptySlots, String recipeMap,
        boolean forceGui) {
        this(ids, names, emptySlots, new ArrayList<Integer>(), new ArrayList<String>(), new ArrayList<ItemStack>(),
            recipeMap, forceGui);
    }

    public ProvidersListS2CPacket(List<Long> ids, List<String> names, List<Integer> emptySlots,
        List<Integer> totalSlots, List<String> locationKeys, List<ItemStack> icons, String recipeMap,
        boolean forceGui) {
        this.ids = ids;
        this.names = names;
        this.emptySlots = emptySlots;
        this.totalSlots = totalSlots == null ? new ArrayList<Integer>() : totalSlots;
        this.locationKeys = locationKeys == null ? new ArrayList<String>() : locationKeys;
        this.icons = icons == null ? new ArrayList<ItemStack>() : icons;
        this.recipeMap = recipeMap;
        this.forceGui = forceGui;
        fitToBudget();
    }

    /** Measure the actual wire representation, including keys, counts and compressed icon NBT. */
    private void fitToBudget() {
        if (ids == null) ids = new ArrayList<>();
        if (names == null) names = new ArrayList<>();
        if (emptySlots == null) emptySlots = new ArrayList<>();
        if (totalSlots == null) totalSlots = new ArrayList<>();
        if (locationKeys == null) locationKeys = new ArrayList<>();
        if (recipeMap != null && utf8Length(recipeMap) > MAX_BYTES - 12) {
            recipeMap = null;
            forceGui = true;
        }
        int used = 4 + 4 + 1 + 1 + (recipeMap == null ? 0 : 2 + utf8Length(recipeMap));
        List<Long> nextIds = new ArrayList<>();
        List<String> nextNames = new ArrayList<>();
        List<Integer> nextEmpty = new ArrayList<>();
        List<Integer> nextTotal = new ArrayList<>();
        List<String> nextKeys = new ArrayList<>();
        List<ItemStack> nextIcons = new ArrayList<>();
        int count = Math.min(ids.size(), Math.min(names.size(), emptySlots.size()));
        for (int i = 0; i < count && nextIds.size() < MAX_ENTRIES; i++) {
            String name = names.get(i) == null ? "" : names.get(i);
            String key = i < locationKeys.size() && locationKeys.get(i) != null ? locationKeys.get(i) : "";
            int size = 8 + 2 + utf8Length(name) + 4 + 4 + 2 + utf8Length(key);
            if (size > MAX_BYTES - used || ids.get(i) == null || emptySlots.get(i) == null) continue;
            used += size;
            nextIds.add(ids.get(i));
            nextNames.add(name);
            nextEmpty.add(emptySlots.get(i));
            nextTotal.add(i < totalSlots.size() && totalSlots.get(i) != null ? totalSlots.get(i) : emptySlots.get(i));
            nextKeys.add(key);
            nextIcons.add(icons != null && i < icons.size() ? icons.get(i) : null);
        }
        if (nextIds.size() < ids.size()) forceGui = true;
        // Optional icons must never crowd out the actual provider list.
        ByteBuf encodedIcons = io.netty.buffer.Unpooled.buffer();
        try {
            for (ItemStack icon : nextIcons) {
                ByteBufUtils.writeItemStack(encodedIcons, icon);
                if (encodedIcons.readableBytes() > MAX_BYTES - used) {
                    nextIcons.clear();
                    break;
                }
            }
        } catch (RuntimeException e) {
            nextIcons.clear();
        } finally {
            encodedIcons.release();
        }
        ids = nextIds;
        names = nextNames;
        emptySlots = nextEmpty;
        totalSlots = nextTotal;
        locationKeys = nextKeys;
        icons = nextIcons;
    }

    private static int utf8Length(String text) {
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int size = buf.readInt();
            // 恶意包防护：预分配容量钳制，防止 new ArrayList<>(巨量) OOM（#45），超界按空列表处理
            if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("provider count");
            ids = new ArrayList<Long>(size);
            names = new ArrayList<String>(size);
            emptySlots = new ArrayList<Integer>(size);
            totalSlots = new ArrayList<Integer>(size);
            locationKeys = new ArrayList<String>(size);

            for (int i = 0; i < size; i++) {
                ids.add(buf.readLong());
                names.add(readString(buf));
                emptySlots.add(buf.readInt());
                totalSlots.add(buf.readInt());
                locationKeys.add(readString(buf));
            }

            int iconCount = buf.readInt();
            if (iconCount != 0 && iconCount != size) throw new IllegalArgumentException("icon count");
            icons = new ArrayList<ItemStack>(iconCount);
            for (int i = 0; i < iconCount; i++) {
                icons.add(ByteBufUtils.readItemStack(buf));
            }

            boolean hasRecipeMap = buf.readBoolean();
            recipeMap = hasRecipeMap ? readString(buf) : null;
            forceGui = buf.readBoolean();
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            ids = new ArrayList<Long>();
            names = new ArrayList<String>();
            emptySlots = new ArrayList<Integer>();
            totalSlots = new ArrayList<Integer>();
            locationKeys = new ArrayList<String>();
            icons = new ArrayList<ItemStack>();
            recipeMap = null;
            forceGui = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        fitToBudget();
        buf.writeInt(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            buf.writeLong(ids.get(i));
            writeString(buf, names.get(i));
            buf.writeInt(emptySlots.get(i));
            buf.writeInt(i < totalSlots.size() ? totalSlots.get(i) : emptySlots.get(i));
            writeString(buf, i < locationKeys.size() ? locationKeys.get(i) : "");
        }

        List<ItemStack> safeIcons = icons == null ? new ArrayList<ItemStack>() : icons;
        buf.writeInt(safeIcons.size());
        for (ItemStack stack : safeIcons) {
            ByteBufUtils.writeItemStack(buf, stack);
        }

        buf.writeBoolean(recipeMap != null);
        if (recipeMap != null) {
            writeString(buf, recipeMap);
        }
        buf.writeBoolean(forceGui);
    }

    private void writeString(ByteBuf buf, String str) {
        byte[] bytes = (str == null ? "" : str).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 32767) throw new IllegalArgumentException("string too long");
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        if (len > 32767 || len > buf.readableBytes()) throw new IllegalArgumentException("string length");
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<ProvidersListS2CPacket, IMessage> {

        @Override
        public IMessage onMessage(ProvidersListS2CPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                MyMod.proxy.handleProvidersList(message);
            }
            return null;
        }
    }
}
