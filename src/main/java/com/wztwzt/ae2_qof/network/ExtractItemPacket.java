package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S: 从 AE2 网络中提取物品到玩家背包。
 * Shift+左键点击 NEI 面板物品时发送。
 */
public class ExtractItemPacket implements IMessage {

    private ItemStack targetStack;
    private long count;

    public ExtractItemPacket() {}

    public ExtractItemPacket(ItemStack targetStack, long count) {
        this.targetStack = targetStack;
        this.count = count;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.targetStack = ByteBufUtils.readItemStack(buf);
            this.count = buf.readLong();
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            this.targetStack = null;
            this.count = 0;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeItemStack(buf, targetStack);
        buf.writeLong(count);
    }

    public static class Handler implements IMessageHandler<ExtractItemPacket, IMessage> {

        @Override
        public IMessage onMessage(ExtractItemPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // 归队到服务端 tick 线程执行，避免 Netty IO 线程并发访问 grid/container
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    handleMessage(player, message);
                } catch (Throwable t) {
                    t.printStackTrace();
                    sendResponse(player, CraftingResponsePacket.RESULT_NO_ITEMS, itemName(message.targetStack));
                }
            });
            return null;
        }

        private void handleMessage(EntityPlayerMP player, ExtractItemPacket message) {
            // 恶意负数 count 钳制为 0，避免负数数量破坏库存
            long count = message.count < 0 ? 0 : message.count;
            if (message.targetStack == null || count <= 0) {
                sendResponse(player, CraftingResponsePacket.RESULT_NO_ITEMS, itemName(message.targetStack));
                return;
            }

            WirelessTerminalGuiObject terminal = ServerTerminalHelper.resolveTerminal(player);
            if (terminal == null) {
                sendResponse(player, CraftingResponsePacket.RESULT_NO_ITEMS, itemName(message.targetStack));
                return;
            }

            IAEItemStack target = AEItemStack.create(message.targetStack);
            if (target == null) {
                sendResponse(player, CraftingResponsePacket.RESULT_NO_ITEMS, itemName(message.targetStack));
                return;
            }

            IAEItemStack stored = terminal.getItemInventory()
                .getStorageList()
                .findPrecise(target);
            if (stored == null || stored.getStackSize() <= 0) {
                sendResponse(player, CraftingResponsePacket.RESULT_NO_ITEMS, itemName(message.targetStack));
                return;
            }

            long extractCount = Math.min(count, stored.getStackSize());
            boolean success = ServerTerminalHelper.extractItemToInventory(terminal, target, extractCount);

            sendResponse(
                player,
                success ? CraftingResponsePacket.RESULT_SUCCESS : CraftingResponsePacket.RESULT_INVENTORY_FULL,
                itemName(message.targetStack));
        }

        private static void sendResponse(EntityPlayerMP player, byte result, String itemName) {
            try {
                ModNetwork.CHANNEL.sendTo(new CraftingResponsePacket(result, itemName), player);
            } catch (Throwable ignored) {}
        }
    }

    private static String itemName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "???";
        try {
            return stack.getDisplayName();
        } catch (Throwable t) {
            return stack.getItem()
                .getUnlocalizedName();
        }
    }
}
