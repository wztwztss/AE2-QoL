/*
 * 本文件搬运自 WildcardPatternforGTNH 1.7.10-1.1.0（作者 com.myname.wildcardpattern，MIT 许可；
 * 用户已授权在保留声明的前提下搬运并优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.wildport；本模组内部引用指向 wildport 包；
 * WildcardPatternMod 的引用改为本模组的 MyMod。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.wildport.network;
import com.wztwzt.ae2_qof.wildport.crafting.WildcardPatternGenerator;
import com.wztwzt.ae2_qof.wildport.ModItems;
import com.wztwzt.ae2_qof.wildport.item.WildcardPatternState;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class MessageUpdateWildcardConfig implements IMessage {

    private int slot;
    private NBTTagCompound config;

    public MessageUpdateWildcardConfig() {}

    public MessageUpdateWildcardConfig(int slot, NBTTagCompound config) {
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

    public static class Handler implements IMessageHandler<MessageUpdateWildcardConfig, IMessage> {

        @Override
        public IMessage onMessage(MessageUpdateWildcardConfig message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (message.slot < 0 || message.slot >= player.inventory.mainInventory.length) {
                return null;
            }

            ItemStack stack = player.inventory.getStackInSlot(message.slot);
            // 3.24.x 适配：我们的物品不是它的 ModItems.wildcardPattern ⇒ 两种都接受。
            // 否则用我们的样板打开这个界面、点保存会被这里**静默丢弃**（本项目铁则：不许静默）。
            boolean ours = com.wztwzt.ae2_qof.wildcard.SmartWildcardState.isSmartWildcard(stack);
            if (stack == null || (!ours && stack.getItem() != ModItems.wildcardPattern)) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn(
                    "[AE2QoL] Wild 界面保存被拒绝：槽位 {} 不是通配样板（item={}）",
                    message.slot,
                    stack == null ? "null" : stack.getItem());
                return null;
            }

            WildcardPatternGenerator.markAsWildcard(stack);
            WildcardPatternState.applyConfig(stack, message.config);
            // 3.24.x：把 Wild 的配置拉回我们的模型 —— 机器侧（样板总成接管 / 索引期展开）走的是
            // SmartWildcardExpander + 我们的子树，必须在这里同步，否则界面上改了机器侧看不到。
            if (ours) {
                com.wztwzt.ae2_qof.wildport.bridge.WildcardBridge.pullFromWild(stack);
            }
            player.inventory.markDirty();
            return null;
        }
    }
}
