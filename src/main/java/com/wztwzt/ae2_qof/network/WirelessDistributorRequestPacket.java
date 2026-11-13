package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

import com.wztwzt.ae2_qof.wireless.distributor.ContainerWirelessDistributor;
import com.wztwzt.ae2_qof.wireless.distributor.TileWirelessDistributor;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端：请求刷新无线分配器列表（打开 GUI、切换筛选、动作完成后用）。
 */
public class WirelessDistributorRequestPacket implements IMessage {

    private int x;
    private int y;
    private int z;

    public WirelessDistributorRequestPacket() {}

    public WirelessDistributorRequestPacket(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
        } catch (Throwable t) {
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<WirelessDistributorRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(WirelessDistributorRequestPacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            ServerTerminalHelper.scheduleServerTask(() -> handleServer(player, msg));
            return null;
        }

        private void handleServer(EntityPlayerMP player, WirelessDistributorRequestPacket msg) {
            if (player.openContainer == null) return;
            if (!(player.openContainer instanceof ContainerWirelessDistributor)) return;
            if (!player.worldObj.blockExists(msg.x, msg.y, msg.z)) return;
            TileEntity tile = player.worldObj.getTileEntity(msg.x, msg.y, msg.z);
            if (!(tile instanceof TileWirelessDistributor)) return;
            ContainerWirelessDistributor container = (ContainerWirelessDistributor) player.openContainer;
            TileWirelessDistributor distributor = (TileWirelessDistributor) tile;
            if (container.tile != distributor) return;
            if (!distributor.isOwner(player)) return;
            distributor.sendList(player);
        }
    }
}
