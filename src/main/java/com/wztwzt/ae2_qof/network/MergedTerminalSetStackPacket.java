package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 二合一终端面板：中键点击已填充槽后设置精确数量（C2S）。
 * 客户端弹出数量输入框确认后发送，服务端按槽号定位并修改对应输入/输出格的数量。
 */
public class MergedTerminalSetStackPacket implements IMessage {

    private int slotNumber;
    private int newSize;

    public MergedTerminalSetStackPacket() {
        this.slotNumber = -1;
        this.newSize = 1;
    }

    public MergedTerminalSetStackPacket(int slotNumber, int newSize) {
        this.slotNumber = slotNumber;
        this.newSize = newSize;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.slotNumber = buf.readInt();
            this.newSize = buf.readInt();
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            this.slotNumber = -1;
            this.newSize = 1;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.slotNumber);
        buf.writeInt(this.newSize);
    }

    public static class Handler implements IMessageHandler<MergedTerminalSetStackPacket, IMessage> {

        @Override
        public IMessage onMessage(MergedTerminalSetStackPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    Container container = player.openContainer;
                    if (container instanceof IMergedPatternTerminal merged) {
                        merged.mergedSetStackSize(message.slotNumber, message.newSize);
                    }
                } catch (Throwable t) {
                    MyMod.LOG.error("Merged terminal set stack size failed", t);
                }
            });
            return null;
        }
    }
}
