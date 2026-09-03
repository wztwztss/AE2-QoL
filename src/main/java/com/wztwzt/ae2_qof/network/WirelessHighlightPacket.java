package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

public class WirelessHighlightPacket implements IMessage {

    public List<int[]> positions;
    public boolean enable;

    public WirelessHighlightPacket() {
        this.positions = new ArrayList<int[]>();
        this.enable = false;
    }

    public WirelessHighlightPacket(List<int[]> positions, boolean enable) {
        this.positions = positions;
        this.enable = enable;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            enable = buf.readBoolean();
            int size = buf.readInt();
            if (size < 0 || size > 1024) {
                size = 0;
            }
            positions = new ArrayList<int[]>(size);
            for (int i = 0; i < size; i++) {
                int dim = buf.readInt();
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                int type = buf.readByte();
                positions.add(new int[] { dim, x, y, z, type });
            }
        } catch (Throwable t) {
            positions = new ArrayList<int[]>();
            enable = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(enable);
        buf.writeInt(positions.size());
        for (int[] pos : positions) {
            buf.writeInt(pos[0]);
            buf.writeInt(pos[1]);
            buf.writeInt(pos[2]);
            buf.writeInt(pos[3]);
            buf.writeByte(pos.length > 4 ? pos[4] : 0);
        }
    }

    public static class Handler implements IMessageHandler<WirelessHighlightPacket, IMessage> {

        @Override
        public IMessage onMessage(WirelessHighlightPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                MyMod.proxy.handleWirelessHighlight(message);
            }
            return null;
        }
    }
}
