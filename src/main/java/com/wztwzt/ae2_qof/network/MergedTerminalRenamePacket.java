package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 二合一终端面板：Shift+中键重命名面板槽物品（C2S）。
 */
public class MergedTerminalRenamePacket implements IMessage {

    private int slotNumber;
    private String newName;

    public MergedTerminalRenamePacket() {
        this.slotNumber = -1;
        this.newName = "";
    }

    public MergedTerminalRenamePacket(int slotNumber, String newName) {
        this.slotNumber = slotNumber;
        this.newName = newName != null ? newName : "";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.slotNumber = buf.readInt();
            int len = buf.readShort();
            if (len > 0 && len <= 256) {
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                this.newName = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                this.newName = "";
            }
        } catch (Throwable t) {
            this.slotNumber = -1;
            this.newName = "";
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.slotNumber);
        byte[] bytes = this.newName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    public static class Handler implements IMessageHandler<MergedTerminalRenamePacket, IMessage> {

        @Override
        public IMessage onMessage(MergedTerminalRenamePacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    Container container = player.openContainer;
                    if (container instanceof IMergedPatternTerminal merged) {
                        if (message.slotNumber < 0 || message.slotNumber >= container.inventorySlots.size()) return;
                        Slot slot = container.inventorySlots.get(message.slotNumber);
                        if (slot == null) return;
                        ItemStack stack = slot.getStack();
                        if (stack == null) return;
                        if (message.newName.isEmpty()) {
                            // 清除自定义名称
                            if (stack.stackTagCompound != null) {
                                stack.stackTagCompound.removeTag("display");
                            }
                        } else {
                            if (stack.stackTagCompound == null) {
                                stack.stackTagCompound = new NBTTagCompound();
                            }
                            NBTTagCompound display = stack.stackTagCompound.getCompoundTag("display");
                            display.setString("Name", message.newName);
                            stack.stackTagCompound.setTag("display", display);
                        }
                    }
                } catch (Throwable t) {
                    MyMod.LOG.error("Merged terminal rename failed", t);
                }
            });
            return null;
        }
    }
}
