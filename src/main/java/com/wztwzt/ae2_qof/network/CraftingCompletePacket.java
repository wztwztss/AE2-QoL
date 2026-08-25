package com.wztwzt.ae2_qof.network;

import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * S2C: 合成完成通知。服务端在 CPU 完成订单时向发起玩家发送。
 * Handler 仅作分发：客户端真逻辑在 ClientProxy.handleCraftingComplete（#74）。
 */
public class CraftingCompletePacket implements IMessage {

    public ItemStack stack;
    public long amount;
    /** 任务耗时（毫秒，3.15.0）：用于通知横幅显示「耗时 HH:mm:ss」。 */
    public long elapsedTimeMillis;

    public CraftingCompletePacket() {}

    public CraftingCompletePacket(ItemStack stack, long amount, long elapsedTimeMillis) {
        this.stack = stack;
        this.amount = amount;
        this.elapsedTimeMillis = elapsedTimeMillis;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.amount = buf.readLong();
            this.elapsedTimeMillis = buf.readLong();
            boolean hasStack = buf.readBoolean();
            if (hasStack) {
                this.stack = ByteBufUtils.readItemStack(buf);
            }
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            this.amount = 0;
            this.elapsedTimeMillis = 0;
            this.stack = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(amount);
        buf.writeLong(elapsedTimeMillis);
        buf.writeBoolean(stack != null);
        if (stack != null) {
            ByteBufUtils.writeItemStack(buf, stack);
        }
    }

    public static class Handler implements IMessageHandler<CraftingCompletePacket, IMessage> {

        @Override
        public IMessage onMessage(CraftingCompletePacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                MyMod.proxy.handleCraftingComplete(message);
            }
            return null;
        }
    }
}
