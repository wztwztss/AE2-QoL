package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveHatchHelper;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetwork;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetworkManager;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveTeamHelper;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class HatchActionPacket implements IMessage {

    public static final int ACTION_HIGHLIGHT = 0;
    public static final int ACTION_TELEPORT = 1;

    private static final java.util.List<long[]> pendingClears = java.util.Collections
        .synchronizedList(new java.util.ArrayList<long[]>());

    public static void scheduleClear(EntityPlayerMP player, int clearTick) {
        pendingClears.add(new long[] { player.getEntityId(), clearTick });
    }

    public static void tickPendingClears() {
        if (pendingClears.isEmpty()) return;
        int currentTick = MinecraftServer.getServer()
            .getTickCounter();
        java.util.Iterator<long[]> it = pendingClears.iterator();
        while (it.hasNext()) {
            long[] entry = it.next();
            if (currentTick >= (int) entry[1]) {
                it.remove();
                EntityPlayerMP player = findPlayer((int) entry[0]);
                if (player != null) {
                    ModNetwork.CHANNEL
                        .sendTo(new WirelessHighlightPacket(new java.util.ArrayList<int[]>(), false), player);
                }
            }
        }
    }

    private static EntityPlayerMP findPlayer(int entityId) {
        for (Object obj : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) obj;
            if (p.getEntityId() == entityId) return p;
        }
        return null;
    }

    private int action;
    private String hatchOwner;
    private int hatchFrequency;
    /**
     * 3.32.0：目标改成**坐标**寻址。
     *
     * <p>
     * 旧实现用"该仓在 {@code network.getAllHelpers()} 里的下标"，而那个列表每次调用都会新建，
     * 注册/注销（包括列表重建时的陈旧清理）都会改变顺序 ⇒ 列表刷新后点【定位】**会跳到别的仓**。
     * 改成坐标后，服务端按坐标重新校验方块真实存在，再执行。
     */
    private int targetDim;
    private int targetX;
    private int targetY;
    private int targetZ;
    /** 高亮颜色类型（0 动力/1 能源/2 激光源/3 激光靶）。 */
    private int colorType;

    public HatchActionPacket() {}

    public HatchActionPacket(int action, String hatchOwner, int hatchFrequency, int targetDim, int targetX, int targetY,
        int targetZ, int colorType) {
        this.action = action;
        this.hatchOwner = hatchOwner;
        this.hatchFrequency = hatchFrequency;
        this.targetDim = targetDim;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.colorType = colorType;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.action = buf.readInt();
            boolean hasOwner = buf.readBoolean();
            this.hatchOwner = hasOwner ? readString(buf) : null;
            this.hatchFrequency = buf.readInt();
            this.targetDim = buf.readInt();
            this.targetX = buf.readInt();
            this.targetY = buf.readInt();
            this.targetZ = buf.readInt();
            this.colorType = buf.readInt();
        } catch (Throwable t) {
            this.action = -1;
            this.hatchOwner = null;
            this.hatchFrequency = 0;
            this.targetDim = 0;
            this.targetX = 0;
            this.targetY = 0;
            this.targetZ = 0;
            this.colorType = 0;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeBoolean(hatchOwner != null);
        if (hatchOwner != null) {
            writeString(buf, hatchOwner);
        }
        buf.writeInt(hatchFrequency);
        buf.writeInt(targetDim);
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
        buf.writeInt(colorType);
    }

    private void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private String readString(ByteBuf buf) {
        int len = buf.readShort();
        if (len < 0 || len > 1024) return "";
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<HatchActionPacket, IMessage> {

        @Override
        public IMessage onMessage(HatchActionPacket msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            ServerTerminalHelper.scheduleServerTask(() -> handleServer(player, msg));
            return null;
        }

        private void handleServer(EntityPlayerMP player, HatchActionPacket msg) {
            try {
                if (player.openContainer == null) return;

                UUID uuid;
                try {
                    uuid = UUID.fromString(msg.hatchOwner);
                } catch (IllegalArgumentException e) {
                    return;
                }

                AdaptiveNetwork network = AdaptiveNetworkManager.getNetwork(uuid, msg.hatchFrequency);
                if (network == null) return;

                // P1-011 鉴权：本包必须来自「当前正打开该终端 GUI」的玩家，
                // 否则任意玩家都能凭构造包读取/高亮他人团队的设备坐标。
                // 该终端在 buildUI 时把打开者登记为 activeViewer，服务端据此确认会话。
                if (!network.getActiveViewers()
                    .contains(player.getUniqueID())) {
                    return;
                }

                // 高亮与传送同源：团队归属始终是 networkOwner（终端所有者），
                // 与“谁在查看”无关，因此两条路径用同一套成员校验。
                if (!AdaptiveTeamHelper.isMemberOf(player.getUniqueID(), uuid)) {
                    player.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            net.minecraft.util.EnumChatFormatting.RED + "无权限：不属于该电网团队"));
                    return;
                }

                java.util.List<AdaptiveHatchHelper> helpers = network.getAllHelpers();
                if (helpers.isEmpty()) return;

                int x = msg.targetX;
                int y = msg.targetY;
                int z = msg.targetZ;
                int dim = msg.targetDim;

                WorldServer world = MinecraftServer.getServer()
                    .worldServerForDimension(dim);
                if (world == null) return;
                // 3.32.0：服务端按坐标重新校验（不再信任客户端给的下标），与库存统计终端同款做法
                if (!world.blockExists(x, y, z)) return;
                if (!(world.getTileEntity(x, y, z) instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity))
                    return;

                switch (msg.action) {
                    case ACTION_HIGHLIGHT:
                        handleHighlight(player, x, y, z, dim, msg.colorType);
                        break;
                    case ACTION_TELEPORT:
                        if (AdaptiveTeamHelper.isMemberOf(player.getUniqueID(), uuid)) {
                            handleTeleport(player, x, y, z, dim);
                        } else {
                            player.addChatMessage(
                                new net.minecraft.util.ChatComponentText(
                                    net.minecraft.util.EnumChatFormatting.RED + "无传送权限：不属于该电网团队"));
                        }
                        break;
                    default:
                        break;
                }
            } catch (Throwable t) {
                MyMod.LOG.error("Hatch action failed", t);
            }
        }

        private void handleHighlight(EntityPlayerMP player, int x, int y, int z, int dim, int hatchType) {
            java.util.List<int[]> positions = new ArrayList<>(
                Collections.singletonList(new int[] { dim, x, y, z, hatchType }));
            ModNetwork.CHANNEL.sendTo(new WirelessHighlightPacket(positions, true), player);
            scheduleClear(
                player,
                MinecraftServer.getServer()
                    .getTickCounter() + 200);
        }

        /**
         * 传送到目标（3.32.0 起目标是**主机**坐标）。
         *
         * <p>
         * 主机是一整座多方块结构，"坐标上方一格"很可能还在机壳里 ⇒ 沿用库存统计终端那份
         * "找可站立落点"的逻辑（正上方 + 四邻的上方，要求脚/头两格空气、脚下实心）；
         * 找不到就提示并取消，而不是把人塞进墙里。
         */
        private void handleTeleport(EntityPlayerMP player, int x, int y, int z, int dim) {
            WorldServer world = MinecraftServer.getServer()
                .worldServerForDimension(dim);
            if (world == null) return;
            double[] dest = findSafeSpot(world, x, y, z);
            if (dest == null) {
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        net.minecraft.util.EnumChatFormatting.RED + "目标附近找不到安全落点，传送已取消"));
                return;
            }
            if (player.dimension == dim) {
                player.setPositionAndUpdate(dest[0], dest[1], dest[2]);
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        net.minecraft.util.EnumChatFormatting.GREEN + "传送到 ["
                            + x
                            + ", "
                            + y
                            + ", "
                            + z
                            + "] dim:"
                            + dim));
                return;
            }
            MinecraftServer server = MinecraftServer.getServer();
            final double dx = dest[0];
            final double dy = dest[1];
            final double dz = dest[2];
            server.getConfigurationManager()
                .transferPlayerToDimension(player, dim, new net.minecraft.world.Teleporter(world) {

                    @Override
                    public void placeInPortal(net.minecraft.entity.Entity entity, double px, double py, double pz,
                        float yaw) {
                        entity.setLocationAndAngles(dx, dy, dz, entity.rotationYaw, entity.rotationPitch);
                        entity.motionX = 0;
                        entity.motionY = 0;
                        entity.motionZ = 0;
                    }

                    @Override
                    public boolean placeInExistingPortal(net.minecraft.entity.Entity entity, double px, double py,
                        double pz, float yaw) {
                        placeInPortal(entity, px, py, pz, yaw);
                        return true;
                    }

                    @Override
                    public boolean makePortal(net.minecraft.entity.Entity entity) {
                        return true;
                    }
                });
            player.addChatMessage(
                new net.minecraft.util.ChatComponentText(
                    net.minecraft.util.EnumChatFormatting.GREEN + "传送到 [" + x + ", " + y + ", " + z + "] dim:" + dim));
        }

        private double[] findSafeSpot(WorldServer world, int x, int y, int z) {
            int[][] offsets = { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int[] o : offsets) {
                int cx = x + o[0];
                int cz = z + o[1];
                for (int cy = y + 1; cy <= y + 2; cy++) {
                    if (isStandable(world, cx, cy, cz)) {
                        return new double[] { cx + 0.5D, cy, cz + 0.5D };
                    }
                }
            }
            return null;
        }

        private boolean isStandable(WorldServer world, int x, int y, int z) {
            try {
                return world.isAirBlock(x, y, z) && world.isAirBlock(x, y + 1, z) && !world.isAirBlock(x, y - 1, z);
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
