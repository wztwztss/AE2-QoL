package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.util.Replanner;

import appeng.container.implementations.ContainerCraftConfirm;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S: 请求对合成确认界面重新规划（replan）。
 */
public class ReplanPacket implements IMessage {

    public ReplanPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<ReplanPacket, IMessage> {

        @Override
        public IMessage onMessage(ReplanPacket message, MessageContext ctx) {
            final EntityPlayer player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }

            // 归队到服务端 tick 线程执行，避免 Netty IO 线程并发访问 container
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    Container c = player.openContainer;
                    if (c instanceof ContainerCraftConfirm ccc) {
                        Replanner.replan(player, ccc);
                    }
                } catch (Throwable t) {
                    MyMod.LOG.error("Replan failed", t);
                }
            });
            return null;
        }
    }
}
