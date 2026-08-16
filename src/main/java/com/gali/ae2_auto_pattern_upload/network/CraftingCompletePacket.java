package com.gali.ae2_auto_pattern_upload.network;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.gali.ae2_auto_pattern_upload.client.render.CraftingNotificationOverlay;

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
        public IMessage onMessage(CraftingCompletePacket message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) {
                return null;
            }
            if (message.stack != null) {
                CraftingNotificationOverlay.INSTANCE.add(message.stack, message.amount);
            }
            return null;
        }
    }
}
