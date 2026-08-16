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
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            WirelessTerminalGuiObject terminal = ServerTerminalHelper.resolveTerminal(player);
            if (terminal == null) {
                return new CraftingResponsePacket((byte) 2, itemName(message.targetStack));
            }

            IGrid grid = terminal.getGrid();
            if (grid == null) {
                return new CraftingResponsePacket((byte) 2, itemName(message.targetStack));
            }

            ICraftingGrid cg = grid.getCache(ICraftingGrid.class);
            if (cg == null) {
                return new CraftingResponsePacket((byte) 2, itemName(message.targetStack));
            }

            IAEItemStack target = AEItemStack.create(message.targetStack);
            if (target == null) {
                return new CraftingResponsePacket((byte) 2, itemName(message.targetStack));
            }

            // 检查是否有 pattern 可以合成该物品
            ImmutableCollection<ICraftingPatternDetails> patterns = cg.getCraftingFor(target, null, 0, player.worldObj);
            if (patterns == null || patterns.isEmpty()) {
                return new CraftingResponsePacket((byte) 2, itemName(message.targetStack));
            }

            int slotIndex = ServerTerminalHelper.findTerminalSlot(player);
            if (slotIndex < 0) {
                return new CraftingResponsePacket((byte) 2, itemName(message.targetStack));
            }

            try {
                Platform.openGUI(player, null, null, GuiBridge.GUI_CRAFTING_AMOUNT, slotIndex);

                if (player.openContainer instanceof ContainerCraftAmount cca) {
                    cca.setItemToCraft(target);
                    cca.detectAndSendChanges();
                    return new CraftingResponsePacket((byte) 0, itemName(message.targetStack));
                }
            } catch (Throwable e) {
                AELog.error("[APU] Failed to open craft amount GUI: {}", e.getMessage());
            }

            return new CraftingResponsePacket((byte) 2, itemName(message.targetStack));
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
