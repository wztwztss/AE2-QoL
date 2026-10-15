/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.wztwzt.ae2_qof.wildport.ModItems;
import com.wztwzt.ae2_qof.wildport.crafting.CompositeWildcardPatternGenerator;
import com.wztwzt.ae2_qof.wildport.item.CompositeWildcardPatternState;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MessageUpdateCompositeWildcardConfig implements IMessage {

    private int slot;
    private NBTTagCompound config;

    public MessageUpdateCompositeWildcardConfig() {}

    public MessageUpdateCompositeWildcardConfig(int slot, NBTTagCompound config) {
        this.slot = slot;
        this.config = config;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.slot = buffer.readInt();
        this.config = ByteBufUtils.readTag(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.slot);
        ByteBufUtils.writeTag(buffer, this.config);
    }

    public static class Handler implements IMessageHandler<MessageUpdateCompositeWildcardConfig, IMessage> {

        @Override
        public IMessage onMessage(MessageUpdateCompositeWildcardConfig message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            // 3.34.0-fix7（审查 P1-8）：归队服务端 tick 线程（写背包物品 NBT）
            com.wztwzt.ae2_qof.network.ServerTerminalHelper.scheduleServerTask(
                () -> handleServerTask(player, message));
            return null;
        }

        /** 3.34.0-fix7：原 onMessage 正文（归队后执行）。 */
        private void handleServerTask(EntityPlayerMP player, MessageUpdateCompositeWildcardConfig message) {
            if (message.slot < 0 || message.slot >= player.inventory.mainInventory.length) {
                return;
            }

            ItemStack stack = player.inventory.getStackInSlot(message.slot);
            if (stack == null || stack.getItem() != ModItems.compositeWildcardPattern) {
                // 3.34.0-fix7：静默 return 违反"不许静默"铁则——补一条 WARN（与兄弟类对齐）
                com.wztwzt.ae2_qof.MyMod.LOG.warn(
                    "[AE2QoL] 复合通配配置写回被拒：槽位 {} 不是复合通配样板（item={}）",
                    message.slot,
                    stack == null ? "null" : stack.getItem());
                return;
            }

            CompositeWildcardPatternGenerator.markAsCompositeWildcard(stack);
            CompositeWildcardPatternState.applyConfig(stack, message.config);
            player.inventory.markDirty();
        }
    }
}
