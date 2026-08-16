package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.google.common.collect.ImmutableCollection;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.AELog;
import appeng.core.sync.GuiBridge;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S: 非 AE2 终端 GUI 中的合成下单请求。
 * 中键点击 NEI 面板可合成物品时发送。
 */
public class RequestCraftingPacket implements IMessage {

    private ItemStack targetStack;

    public RequestCraftingPacket() {}

    public RequestCraftingPacket(ItemStack targetStack) {
        this.targetStack = targetStack;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.targetStack = ByteBufUtils.readItemStack(buf);
        } catch (Throwable t) {
            // 防御性解码：任何异常都不得导致玩家断连
            this.targetStack = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeItemStack(buf, targetStack);
    }

    public static class Handler implements IMessageHandler<RequestCraftingPacket, IMessage> {

        @Override
        public IMessage onMessage(RequestCraftingPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // 归队到服务端 tick 线程执行，避免 Netty IO 线程并发访问 grid/container
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    handleMessage(player, message);
                } catch (Throwable e) {
                    AELog.error("[APU] Failed to process craft request: {}", e.getMessage());
                    sendResponse(player, CraftingResponsePacket.RESULT_NOT_CRAFTABLE, itemName(message.targetStack));
                }
            });
            return null;
        }

        private void handleMessage(EntityPlayerMP player, RequestCraftingPacket message) {
            WirelessTerminalGuiObject terminal = ServerTerminalHelper.resolveTerminal(player);
            if (terminal == null) {
                sendResponse(player, CraftingResponsePacket.RESULT_NOT_CRAFTABLE, itemName(message.targetStack));
                return;
            }

            IGrid grid = terminal.getGrid();
            if (grid == null) {
                sendResponse(player, CraftingResponsePacket.RESULT_NOT_CRAFTABLE, itemName(message.targetStack));
                return;
            }

            ICraftingGrid cg = grid.getCache(ICraftingGrid.class);
            if (cg == null) {
                sendResponse(player, CraftingResponsePacket.RESULT_NOT_CRAFTABLE, itemName(message.targetStack));
                return;
            }

            IAEItemStack target = AEItemStack.create(message.targetStack);
            if (target == null) {
                sendResponse(player, CraftingResponsePacket.RESULT_NOT_CRAFTABLE, itemName(message.targetStack));
                return;
            }

            // 检查是否有 pattern 可以合成该物品
            ImmutableCollection<ICraftingPatternDetails> patterns = cg.getCraftingFor(target, null, 0, player.worldObj);
            if (patterns == null || patterns.isEmpty()) {
                sendResponse(player, CraftingResponsePacket.RESULT_NOT_CRAFTABLE, itemName(message.targetStack));
                return;
            }

            int slotIndex = ServerTerminalHelper.findTerminalSlot(player);
            if (slotIndex < 0) {
                sendResponse(player, CraftingResponsePacket.RESULT_NOT_CRAFTABLE, itemName(message.targetStack));
                return;
            }

            Platform.openGUI(player, null, null, GuiBridge.GUI_CRAFTING_AMOUNT, slotIndex);

            if (player.openContainer instanceof ContainerCraftAmount cca) {
                cca.setItemToCraft(target);
                cca.detectAndSendChanges();
                sendResponse(player, CraftingResponsePacket.RESULT_SUCCESS, itemName(message.targetStack));
                return;
            }

            sendResponse(player, CraftingResponsePacket.RESULT_NOT_CRAFTABLE, itemName(message.targetStack));
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
