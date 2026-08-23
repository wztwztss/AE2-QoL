package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.wztwzt.ae2_qof.merged.ContainerMergedTerminal;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 二合一终端面板：请求指定槽位 Shift+滚轮 替换候选列表（C2S）。
 * 服务端查 ME 网络 OreDict 同类物品后经 ReplaceCandidatesPacket 回传，供 tooltip 预览。
 */
public class RequestReplaceCandidatesPacket implements IMessage {

    private int slotNumber;

    public RequestReplaceCandidatesPacket() {
        this.slotNumber = -1;
    }

    public RequestReplaceCandidatesPacket(int slotNumber) {
        this.slotNumber = slotNumber;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.slotNumber = buf.readInt();
        } catch (Throwable t) {
            this.slotNumber = -1;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.slotNumber);
    }

    public static class Handler implements IMessageHandler<RequestReplaceCandidatesPacket, IMessage> {

        @Override
        public IMessage onMessage(RequestReplaceCandidatesPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    if (!(player.openContainer instanceof ContainerMergedTerminal cmt)) return;
                    java.util.List<net.minecraft.item.ItemStack> candidates = MergedTerminalScrollReplacePacket
                        .findAlternatives(cmt, message.slotNumber);
                    // 计算当前物品在候选序列中的位置（预览"下一个"需要）
                    int idx = -1;
                    if (message.slotNumber >= 0 && message.slotNumber < cmt.inventorySlots.size()) {
                        net.minecraft.item.ItemStack cur = cmt.inventorySlots.get(message.slotNumber)
                            .getStack();
                        if (cur != null) {
                            for (int i = 0; i < candidates.size(); i++) {
                                if (net.minecraft.item.ItemStack.areItemStacksEqual(candidates.get(i), cur)) {
                                    idx = i;
                                    break;
                                }
                            }
                        }
                    }
                    ModNetwork.CHANNEL.sendTo(new ReplaceCandidatesPacket(candidates, idx), player);
                } catch (Throwable ignored) {}
            });
            return null;
        }
    }
}
