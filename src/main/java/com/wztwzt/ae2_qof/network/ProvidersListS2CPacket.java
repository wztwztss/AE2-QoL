package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public class ProvidersListS2CPacket implements IMessage {

    public List<Long> ids;
    public List<String> names;
    public List<Integer> emptySlots;
    public String recipeMap;
    public boolean forceGui;

    public ProvidersListS2CPacket() {
        this.ids = new ArrayList<Long>();
        this.names = new ArrayList<String>();
        this.emptySlots = new ArrayList<Integer>();
        this.recipeMap = null;
        this.forceGui = false;
    }

    public ProvidersListS2CPacket(List<Long> ids, List<String> names, List<Integer> emptySlots, String recipeMap,
        boolean forceGui) {
        this.ids = ids;
        this.names = names;
        this.emptySlots = emptySlots;
        this.recipeMap = recipeMap;
        this.forceGui = forceGui;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int size = buf.readInt();
            // 恶意包防护：预分配容量钳制，防止 new ArrayList<>(巨量) OOM（#45），超界按空列表处理
            if (size < 0 || size > 1024) {
                size = 0;
            }
            ids = new ArrayList<Long>(size);
            names = new ArrayList<String>(size);
            emptySlots = new ArrayList<Integer>(size);

            for (int i = 0; i < size; i++) {
                ids.add(buf.readLong());
                names.add(readString(buf));
                emptySlots.add(buf.readInt());
            }

            boolean hasRecipeMap = buf.readBoolean();
            recipeMap = hasRecipeMap ? readString(buf) : null;
            forceGui = buf.readBoolean();
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            ids = new ArrayList<Long>();
            names = new ArrayList<String>();
            emptySlots = new ArrayList<Integer>();
            recipeMap = null;
            forceGui = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            buf.writeLong(ids.get(i));
            writeString(buf, names.get(i));
            buf.writeInt(emptySlots.get(i));
        }

        buf.writeBoolean(recipeMap != null);
        if (recipeMap != null) {
            writeString(buf, recipeMap);
        }
        buf.writeBoolean(forceGui);
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
