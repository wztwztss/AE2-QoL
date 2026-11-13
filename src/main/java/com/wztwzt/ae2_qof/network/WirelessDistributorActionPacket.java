package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;

import com.wztwzt.ae2_qof.wireless.distributor.ContainerWirelessDistributor;
import com.wztwzt.ae2_qof.wireless.distributor.TileWirelessDistributor;

import appeng.api.util.DimensionalCoord;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端：无线分配器的绑定/解绑/扫描/半径/颜色动作。
 *
 * <p>
 * 所有目标坐标都由服务端重新取 TileEntity 校验，不信任客户端给的下标；动作只允许来自
 * 当前正打开该分配器 GUI 的玩家。
 */
public class WirelessDistributorActionPacket implements IMessage {

    public static final int ACTION_LINK = 0;
    public static final int ACTION_UNLINK = 1;
    public static final int ACTION_SCAN = 2;
    public static final int ACTION_RANGE = 3;
    public static final int ACTION_COLOR = 4;

    private int action;
    private int x;
    private int y;
    private int z;
    private int targetX;
    private int targetY;
    private int targetZ;

    public WirelessDistributorActionPacket() {}

    public WirelessDistributorActionPacket(int action, int x, int y, int z, int targetX, int targetY, int targetZ) {
        this.action = action;
        this.x = x;
        this.y = y;
        this.z = z;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
    }

    public WirelessDistributorActionPacket(int action, int x, int y, int z) {
        this(action, x, y, z, 0, 0, 0);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.action = buf.readInt();
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
            this.targetX = buf.readInt();
            this.targetY = buf.readInt();
            this.targetZ = buf.readInt();
        } catch (Throwable t) {
            this.action = -1;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
    }

    public static class Handler implements IMessageHandler<WirelessDistributorActionPacket, IMessage> {

        @Override
        public IMessage onMessage(WirelessDistributorActionPacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            ServerTerminalHelper.scheduleServerTask(() -> handleServer(player, msg));
            return null;
        }

        private void handleServer(EntityPlayerMP player, WirelessDistributorActionPacket msg) {
            try {
                if (msg.action < 0 || player.openContainer == null) return;
                if (!(player.openContainer instanceof ContainerWirelessDistributor)) return;
                if (!player.worldObj.blockExists(msg.x, msg.y, msg.z)) return;
                TileEntity tile = player.worldObj.getTileEntity(msg.x, msg.y, msg.z);
                if (!(tile instanceof TileWirelessDistributor)) return;
                ContainerWirelessDistributor container = (ContainerWirelessDistributor) player.openContainer;
                TileWirelessDistributor distributor = (TileWirelessDistributor) tile;
                if (container.tile != distributor) return;
                if (!distributor.isOwner(player)) {
                    player.addChatMessage(new ChatComponentTranslation("gui.ae2_qof.wireless_distributor.not_owner"));
                    return;
                }

                switch (msg.action) {
                    case ACTION_LINK: {
                        DimensionalCoord coord = new DimensionalCoord(
                            player.worldObj,
                            msg.targetX,
                            msg.targetY,
                            msg.targetZ);
                        String error = distributor.doLink(player, coord);
                        if (error == null) {
                            player.addChatMessage(
                                new ChatComponentTranslation("gui.ae2_qof.wireless_distributor.link.success"));
                        } else {
                            player.addChatMessage(new ChatComponentTranslation(error));
                        }
                        break;
                    }
                    case ACTION_UNLINK: {
                        DimensionalCoord coord = new DimensionalCoord(
                            player.worldObj,
                            msg.targetX,
                            msg.targetY,
                            msg.targetZ);
                        if (!distributor.doUnlink(coord)) {
                            player.addChatMessage(
                                new ChatComponentTranslation("gui.ae2_qof.wireless_distributor.unlink.none"));
                        }
                        break;
                    }
                    case ACTION_SCAN: {
                        int count = distributor.scanAndLink();
                        if (count < 0) {
                            player.addChatMessage(
                                new ChatComponentTranslation("gui.ae2_qof.wireless_distributor.no_grid"));
                        } else {
                            player.addChatMessage(
                                new ChatComponentTranslation(
                                    "gui.ae2_qof.wireless_distributor.scan_result",
                                    count,
                                    TileWirelessDistributor.MAX_SIZE));
                        }
                        break;
                    }
                    case ACTION_RANGE:
                        distributor.toggleRadius();
                        break;
                    case ACTION_COLOR:
                        distributor.setColor(
                            appeng.api.util.AEColor.fromOrdinal(
                                (distributor.getColor()
                                    .ordinal() + 1) % appeng.api.util.AEColor.values().length));
                        break;
                    default:
                        return;
                }
                distributor.sendList(player);
            } catch (Throwable t) {
                com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] 无线分配器动作失败", t);
            }
        }
    }
}
