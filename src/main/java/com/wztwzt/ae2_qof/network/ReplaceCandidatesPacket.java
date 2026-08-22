package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.wztwzt.ae2_qof.client.ClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

/**
 * 二合一终端面板：替换候选列表回传（S2C），客户端缓存供 tooltip 预览。
 * currentIndex 为当前槽内物品在候选序列中的位置（-1 表示不在其中）。
 */
public class ReplaceCandidatesPacket implements IMessage {

    private List<ItemStack> candidates;
    private int currentIndex;

    public ReplaceCandidatesPacket() {
        this.candidates = new ArrayList<ItemStack>();
        this.currentIndex = -1;
    }

    public ReplaceCandidatesPacket(List<ItemStack> candidates, int currentIndex) {
        this.candidates = candidates != null ? candidates : new ArrayList<ItemStack>();
        this.currentIndex = currentIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.candidates = new ArrayList<ItemStack>();
            this.currentIndex = buf.readInt();
            NBTTagCompound root = ByteBufUtils.readTag(buf);
            if (root == null) return;
            NBTTagList list = root.getTagList("l", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                ItemStack s = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
                if (s != null) this.candidates.add(s);
            }
        } catch (Throwable t) {
            this.candidates = new ArrayList<ItemStack>();
            this.currentIndex = -1;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // 防止包过大：最多回传16个候选
        int n = Math.min(this.candidates.size(), 16);
        buf.writeInt(this.currentIndex);
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < n; i++) {
            ItemStack s = this.candidates.get(i);
            if (s == null) continue;
            NBTTagCompound tag = new NBTTagCompound();
            s.writeToNBT(tag);
            list.appendTag(tag);
        }
        root.setTag("l", list);
        ByteBufUtils.writeTag(buf, root);
    }

    public static class Handler implements IMessageHandler<ReplaceCandidatesPacket, IMessage> {

        @Override
        public IMessage onMessage(ReplaceCandidatesPacket message, MessageContext ctx) {
            ClientState.replaceCandidates = message.candidates;
            ClientState.replaceCurrentIndex = message.currentIndex;
            return null;
        }
    }
}
