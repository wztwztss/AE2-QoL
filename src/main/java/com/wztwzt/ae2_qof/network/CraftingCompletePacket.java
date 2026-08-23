package com.wztwzt.ae2_qof.network;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.client.render.CraftingNotificationOverlay;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * S2C: 合成完成通知。服务端在 CPU 完成订单时向发起玩家发送。
 */
public class CraftingCompletePacket implements IMessage {

    private ItemStack stack;
    private long amount;

    public CraftingCompletePacket() {}

    public CraftingCompletePacket(ItemStack stack, long amount) {
        this.stack = stack;
        this.amount = amount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.amount = buf.readLong();
            boolean hasStack = buf.readBoolean();
            if (hasStack) {
                this.stack = ByteBufUtils.readItemStack(buf);
            }
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            this.amount = 0;
            this.stack = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(amount);
        buf.writeBoolean(stack != null);
        if (stack != null) {
            ByteBufUtils.writeItemStack(buf, stack);
        }
    }

    public static class Handler implements IMessageHandler<CraftingCompletePacket, IMessage> {

        @Override
        public IMessage onMessage(final CraftingCompletePacket message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) {
                return null;
            }
            // Netty IO 线程不得直接写 CraftingNotificationOverlay 的非线程安全 ArrayDeque，
            // 必须归队到客户端主线程（与其余 S2C 包一致）。
            mc.func_152344_a(new Runnable() {

                @Override
                public void run() {
                    if (message.stack != null) {
                        CraftingNotificationOverlay.INSTANCE.add(message.stack, message.amount);
                    }
                }
            });
            return null;
        }
    }
}
