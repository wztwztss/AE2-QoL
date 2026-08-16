package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.wztwzt.ae2_qof.client.ClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class WirelessHighlightPacket implements IMessage {

    private List<int[]> positions;
    private boolean enable;

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
            positions = new ArrayList<int[]>(size);
            for (int i = 0; i < size; i++) {
                int dim = buf.readInt();
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                positions.add(new int[] { dim, x, y, z });
            }
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
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
        }
    }

    public static class Handler implements IMessageHandler<WirelessHighlightPacket, IMessage> {

        @Override
        public IMessage onMessage(final WirelessHighlightPacket message, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(new Runnable() {

                    @Override
                    public void run() {
                        ClientState.highlightPositions = message.positions;
                        ClientState.highlightEnabled = message.enable;
                    }
                });
            return null;
        }
    }
}
