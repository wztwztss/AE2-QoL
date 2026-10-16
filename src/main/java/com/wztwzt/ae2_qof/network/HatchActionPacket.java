package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveHatchHelper;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetTerminal;
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

        /**
         * 3.35.0-fix16（审查 P2-14）：畸形 owner 告警的限频表（玩家 → 上次告警时刻）。
         * 限频是必须的——告警本身若不限频，伪造包仍可刷屏（只是从 ERROR 变成 WARN）。
         */
        private static final java.util.Map<UUID, Long> MALFORMED_WARN_AT = new java.util.concurrent.ConcurrentHashMap<UUID, Long>();
        private static final long MALFORMED_WARN_INTERVAL_MS = 60_000L;

        private static void ae2qol$warnMalformedOwner(EntityPlayerMP player, String owner) {
            try {
                long now = System.currentTimeMillis();
                Long last = MALFORMED_WARN_AT.get(player.getUniqueID());
                if (last != null && now - last < MALFORMED_WARN_INTERVAL_MS) {
                    return;
                }
                if (MALFORMED_WARN_AT.size() > 1024) {
                    MALFORMED_WARN_AT.clear(); // 有界：极端情况下不让限频表自身无界增长
                }
                MALFORMED_WARN_AT.put(player.getUniqueID(), now);
                MyMod.LOG.warn(
                    "[AE2QoL] 收到非法的自适应终端动作包（hatchOwner={}），已忽略；同一玩家 60 秒内只提示一次：player={}",
                    owner,
                    player.getCommandSenderName());
            } catch (Throwable ignored) {}
        }

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

                // 3.35.0-fix16（审查 P2-14）：hatchOwner 在协议里**允许为 null**
                // （toBytes 用 writeBoolean 标记有无，见 :121）⇒ 伪造包可送 null。
                // UUID.fromString(null) 抛的是 NullPointerException，而旧代码只 catch
                // IllegalArgumentException ⇒ NPE 逃到外层 catch (Throwable)，每个畸形包打一整条
                // ERROR 堆栈（可刷屏日志/撑爆磁盘）。现在就地拦下（null/空串/NPE/IAE 全覆盖），
                // 并留一条限频 WARN 让"有人在发畸形包"可见（符合"不许静默"）。
                if (msg.hatchOwner == null || msg.hatchOwner.isEmpty()) {
                    ae2qol$warnMalformedOwner(player, "<null/empty>");
                    return;
                }

                UUID uuid;
                try {
                    uuid = UUID.fromString(msg.hatchOwner);
                } catch (RuntimeException e) {
                    // RuntimeException 而非 IllegalArgumentException：NPE 也在此列（JVM 保证二者
                    // 之外的 UUID 解析异常不存在），避免再次漏网
                    ae2qol$warnMalformedOwner(player, msg.hatchOwner);
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

                // 3.34.0-fix1：目标归属校验（审查 P1-2）—— 高亮/传送的目标必须属于本电网。
                // 旧实现在"会话 + 团队"两道闸之后直接接受**任意** GT 方块实体坐标：
                // 恶意玩家拿自己的合法终端报一个别人家 AE 设备的坐标，就能跨维度绕墙进他人基地。
                // 现在目标必须满足其一：① 是本网络名单（network.getAllHelpers）里的仓坐标；
                // ② 是某个 AE 设备（IGridHost）且其电网 id 与终端所在电网相同（IGrid.getId() 是
                // AE2 1083 公开 API，同一网络的 Grid 单例共享同一 UUID）。
                if (!isTargetOwnedByNetwork(network, world, x, y, z)) {
                    MyMod.LOG.info(
                        "[AE2QoL] 高亮/传送目标不属于发起终端所在的电网，已拒绝：player={} target={},{},{} dim={}",
                        player.getCommandSenderName(),
                        x,
                        y,
                        z,
                        dim);
                    return;
                }

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

        /**
         * 3.34.0-fix1（审查 P1-2）：目标必须属于发起终端所在的电网。
         *
         * <p>
         * 两条放行路径（满足其一即可）：
         * <ol>
         * <li>目标是**本网络名单内的仓**：与 {@code network.getAllHelpers()} 的坐标逐一比对
         * （该名单由服务端维护，客户端伪造的坐标不可能混进来）；</li>
         * <li>目标是 AE 设备（{@code IGridHost}）且其 {@code IGrid.getId()} 与终端电网相同
         * —— 典型场景：TecTech 兼容激光仓挂在别人的 AE 主机上但已接入本网络。
         * {@code IGrid.getId()}（AE2 1083 公开 API，返回 Grid 单例的 UUID）是可靠的网格判等手段。</li>
         * </ol>
         *
         * <p>
         * 注意：这里不做区块加载（调用方已先做 {@code blockExists} 过滤，此处 TileEntity
         * 必然可取）；目标不是 IGridHost 也**不直接拒绝**——先走名单路径（情况①）。
         */
        private static boolean isTargetOwnedByNetwork(AdaptiveNetwork network, WorldServer world, int x, int y, int z) {
            // ① 本网络名单内的仓
            for (AdaptiveHatchHelper h : network.getAllHelpers()) {
                if (h.getDim() == world.provider.dimensionId && h.getX() == x && h.getY() == y && h.getZ() == z) {
                    return true;
                }
            }
            // ② AE 设备且同一电网
            try {
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof appeng.api.networking.IGridHost) {
                    appeng.api.networking.IGridNode node = ((appeng.api.networking.IGridHost) te)
                        .getGridNode(net.minecraftforge.common.util.ForgeDirection.UNKNOWN);
                    if (node != null && node.getGrid() != null) {
                        java.util.UUID targetGrid = node.getGrid()
                            .getId();
                        appeng.api.networking.IGrid ownGrid = resolveNetworkGrid(network);
                        if (ownGrid != null && targetGrid.equals(ownGrid.getId())) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 目标取不到网格 ⇒ 走不到放行路径，返回 false 由调用方拒绝
            }
            return false;
        }

        /**
         * 3.34.0-fix1：解析"终端所在电网"的 {@code IGrid}（供 {@link #isTargetOwnedByNetwork} 判等）。
         * 自适应终端实现了 AE2 的 {@code IGridProxyable}（宿主 TE 持有 proxy），从这里拿最稳。
         */
        private static appeng.api.networking.IGrid resolveNetworkGrid(AdaptiveNetwork network) {
            AdaptiveNetTerminal terminal = network.getTerminal();
            if (terminal == null) return null;
            try {
                net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) terminal
                    .getBaseMetaTileEntity();
                if (te instanceof appeng.me.helpers.IGridProxyable) {
                    appeng.api.networking.IGridNode node = ((appeng.me.helpers.IGridProxyable) te).getProxy()
                        .getNode();
                    if (node != null) return node.getGrid();
                }
            } catch (Throwable ignored) {}
            return null;
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
