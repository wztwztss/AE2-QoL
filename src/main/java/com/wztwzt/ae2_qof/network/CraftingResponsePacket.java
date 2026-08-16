package com.wztwzt.ae2_qof.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * S2C: 操作结果通知。
 * 服务端在执行提取/合成操作后发送结果到客户端，客户端在聊天栏显示提示。
 * 仅传输物品名称字符串，避免 ItemStack 序列化在部分物品上导致字节错位。
 */
public class CraftingResponsePacket implements IMessage {

    public static final byte RESULT_SUCCESS = 0;
    public static final byte RESULT_NO_ITEMS = 1;
    public static final byte RESULT_NOT_CRAFTABLE = 2;
    public static final byte RESULT_INVENTORY_FULL = 3;

    private byte resultType;
    private String itemName;

    public CraftingResponsePacket() {}

    public CraftingResponsePacket(byte resultType, String itemName) {
        this.resultType = resultType;
        this.itemName = itemName != null ? itemName : "???";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.resultType = buf.readByte();
            this.itemName = ByteBufUtils.readUTF8String(buf);
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            this.resultType = 0;
            this.itemName = "???";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(resultType);
        ByteBufUtils.writeUTF8String(buf, itemName);
    }

    public static class Handler implements IMessageHandler<CraftingResponsePacket, IMessage> {

        @Override
        public IMessage onMessage(CraftingResponsePacket message, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer player = mc.thePlayer;
            if (player == null) return null;

            String itemName = message.itemName != null ? message.itemName : "???";

            switch (message.resultType) {
                case RESULT_SUCCESS:
                    player.addChatMessage(
                        new ChatComponentTranslation(
                            "ae2qol.extract.success",
                            EnumChatFormatting.GREEN + itemName + EnumChatFormatting.WHITE));
                    break;
                case RESULT_NO_ITEMS:
                    player.addChatMessage(
                        new ChatComponentTranslation(
                            "ae2qol.extract.no_items",
                            EnumChatFormatting.RED + itemName + EnumChatFormatting.WHITE));
                    break;
                case RESULT_NOT_CRAFTABLE:
                    player.addChatMessage(
                        new ChatComponentTranslation(
                            "ae2qol.extract.not_craftable",
                            EnumChatFormatting.RED + itemName + EnumChatFormatting.WHITE));
                    break;
                case RESULT_INVENTORY_FULL:
                    player.addChatMessage(
                        new ChatComponentTranslation(
                            "ae2qol.extract.inventory_full",
                            EnumChatFormatting.RED + itemName + EnumChatFormatting.WHITE));
                    break;
            }

            return null;
        }
    }
}
