package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.terminal.StockMonitorTerminal;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * 库存统计终端 → 服务端的「高亮 / 传送」动作请求（3.21.0）。
 *
 * <p>刻意照抄 {@link HatchActionPacket} 的**分工与防护**，只是把"目标身份"换成坐标：
 * <ul>
 * <li>客户端只发**坐标**，不做任何判定；</li>
 * <li>服务端在 tick 线程里**重新解析目标**（覆盖板要真的贴在该方块上、发信器要求该方块是 AE 主机），
 * 校验 {@code blockExists}，再执行；</li>
 * <li>鉴权两层：① 会话——终端必须登记过这个玩家（GUI 打开时登记，等价自适应终端的 P1-011
 * {@code activeViewers}），② 权限——终端所连 AE 网络必须给该玩家 BUILD。</li>
 * </ul>
 *
 * <p>高亮复用 {@link WirelessHighlightPacket}（{@code {dim,x,y,z,colorType}}，
 * 渲染器 {@code WirelessHighlightRenderer} 自带"非同维度跳过"）与 {@link HatchActionPacket#scheduleClear}
 * 的 200 tick（10 秒）自动清除队列——该队列由 {@code StockMonitorTerminal.onPostTick} 每 tick 推进，
 * 所以**不装自适应电网终端时高亮也会按时消失**。
 *
 * <p>传送沿用自适应终端那份修好的 {@link Teleporter} 匿名实现（覆写 {@code placeInPortal} 直接落坐标、
 * {@code placeInExistingPortal}/{@code makePortal} 短路），避免 {@code new Teleporter(world)} 去找/建下界门；
 * 落点先找**可站立**的相邻格，找不到就拒绝并提示（用户确认要"安全落点"）。
 */
public class StockMonitorActionPacket implements IMessage {

    public static final int ACTION_HIGHLIGHT = 0;
    public static final int ACTION_TELEPORT = 1;

    public static final int KIND_COVER = 0;
    public static final int KIND_EMITTER = 1;

    /** 高亮持续 200 tick = 10 秒（与自适应终端一致，用户确认 10 秒）。 */
    private static final int HIGHLIGHT_TICKS = 200;

    private int action;
    private int kind;
    /** 终端自身位置：仅用于服务端定位终端做会话/权限校验。 */
    private int termDim;
    private int termX;
    private int termY;
    private int termZ;
    /** 目标方块坐标。 */
    private int dim;
    private int x;
    private int y;
    private int z;

    public StockMonitorActionPacket() {}

    public StockMonitorActionPacket(int action, int kind, int termDim, int termX, int termY, int termZ, int dim, int x,
        int y, int z) {
        this.action = action;
        this.kind = kind;
        this.termDim = termDim;
        this.termX = termX;
        this.termY = termY;
        this.termZ = termZ;
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.action = buf.readInt();
            this.kind = buf.readInt();
            this.termDim = buf.readInt();
            this.termX = buf.readInt();
            this.termY = buf.readInt();
            this.termZ = buf.readInt();
            this.dim = buf.readInt();
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
        } catch (Throwable t) {
            this.action = -1;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeInt(kind);
        buf.writeInt(termDim);
        buf.writeInt(termX);
        buf.writeInt(termY);
        buf.writeInt(termZ);
        buf.writeInt(dim);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<StockMonitorActionPacket, IMessage> {

        @Override
        public IMessage onMessage(StockMonitorActionPacket msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // AE2/网络线程 → 服务端 tick 线程（本项目铁则：不要在包线程里动世界/玩家）
            ServerTerminalHelper.scheduleServerTask(() -> handleServer(player, msg));
            return null;
        }

        private void handleServer(EntityPlayerMP player, StockMonitorActionPacket msg) {
            try {
                if (player.openContainer == null) return;

                StockMonitorTerminal terminal = findTerminal(msg.termDim, msg.termX, msg.termY, msg.termZ);
                if (terminal == null) return;

                // ① 会话校验：只有"当前正打开该终端界面"的玩家能触发（等价 P1-011）
                if (!terminal.isActiveViewer(player.getUniqueID())) {
                    chat(player, EnumChatFormatting.RED, "ae2_qof.terminal.session_invalid");
                    return;
                }

                // ② 权限校验：终端所连 AE 网络的 BUILD
                if (!terminal.hasBuildPermission(player)) {
                    chat(player, EnumChatFormatting.RED, "ae2_qof.terminal.teleport_no_perm");
                    return;
                }

                MinecraftServer server = MinecraftServer.getServer();
                if (server == null) return;
                WorldServer world = server.worldServerForDimension(msg.dim);
                if (world == null) {
                    chat(player, EnumChatFormatting.RED, "ae2_qof.terminal.dim_unreachable");
                    return;
                }
                if (!world.blockExists(msg.x, msg.y, msg.z)) {
                    chat(player, EnumChatFormatting.RED, "ae2_qof.terminal.target_missing");
                    return;
                }

                // ③ 目标真实性校验（服务端重新解析，不信任客户端"身份"）
                if (!targetLooksValid(world, msg.kind, msg.x, msg.y, msg.z)) {
                    chat(player, EnumChatFormatting.RED, "ae2_qof.terminal.target_missing");
                    return;
                }

                switch (msg.action) {
                    case ACTION_HIGHLIGHT:
                        handleHighlight(player, msg.kind, msg.dim, msg.x, msg.y, msg.z);
                        break;
                    case ACTION_TELEPORT:
                        handleTeleport(player, msg.dim, msg.x, msg.y, msg.z);
                        break;
                    default:
                        break;
                }
            } catch (Throwable t) {
                MyMod.LOG.error("[StockMonitor] 高亮/传送动作执行失败", t);
            }
        }

        private StockMonitorTerminal findTerminal(int dim, int x, int y, int z) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) return null;
            WorldServer world = server.worldServerForDimension(dim);
            if (world == null) return null;
            TileEntity te = world.getTileEntity(x, y, z);
            if (!(te instanceof IGregTechTileEntity)) return null;
            IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            return mte instanceof StockMonitorTerminal ? (StockMonitorTerminal) mte : null;
        }

        private boolean targetLooksValid(WorldServer world, int kind, int x, int y, int z) {
            try {
                if (kind == KIND_COVER) {
                    // 覆盖板：该方块任意一面确实贴着库存检测覆盖板
                    TileEntity te = world.getTileEntity(x, y, z);
                    if (!(te instanceof gregtech.api.interfaces.tileentity.ICoverable)) return false;
                    gregtech.api.interfaces.tileentity.ICoverable coverable =
                        (gregtech.api.interfaces.tileentity.ICoverable) te;
                    for (net.minecraftforge.common.util.ForgeDirection side : net.minecraftforge.common.util.ForgeDirection
                        .VALID_DIRECTIONS) {
                        if (coverable.getCoverAtSide(side) instanceof com.wztwzt.ae2_qof.cover.stockmonitor.StockMonitorCover) {
                            return true;
                        }
                    }
                    return false;
                }
                // 发信器：该方块必须是 AE 主机（线缆/总线等）
                return world.getTileEntity(x, y, z) instanceof appeng.api.networking.IGridHost;
            } catch (Throwable t) {
                return false;
            }
        }

        private void handleHighlight(EntityPlayerMP player, int kind, int dim, int x, int y, int z) {
            // 渲染器只画与玩家同维度的框（WirelessHighlightRenderer 显式跳过其它维度），
            // 所以跨维度时给一句提示而不是发一个永远看不见的包。
            if (player.dimension != dim) {
                chatFormatted(player, EnumChatFormatting.YELLOW, "ae2_qof.terminal.target_other_dim", dim);
                return;
            }
            // 颜色索引复用渲染器的 HATCH_COLORS：覆盖板=蓝(1)、发信器=紫(2)，便于区分
            int colorType = kind == KIND_COVER ? 1 : 2;
            List<int[]> positions = new ArrayList<>(Collections.singletonList(new int[] { dim, x, y, z, colorType }));
            ModNetwork.CHANNEL.sendTo(new WirelessHighlightPacket(positions, true), player);
            HatchActionPacket.scheduleClear(player, MinecraftServer.getServer().getTickCounter() + HIGHLIGHT_TICKS);
            chatFormatted(player, EnumChatFormatting.GREEN, "ae2_qof.terminal.highlighted", x, y, z);
        }

        private void handleTeleport(EntityPlayerMP player, int dim, int x, int y, int z) {
            MinecraftServer server = MinecraftServer.getServer();
            WorldServer targetWorld = server.worldServerForDimension(dim);
            if (targetWorld == null) {
                chat(player, EnumChatFormatting.RED, "ae2_qof.terminal.dim_unreachable");
                return;
            }
            final double[] dest = findSafeSpot(targetWorld, x, y, z);
            if (dest == null) {
                chat(player, EnumChatFormatting.RED, "ae2_qof.terminal.teleport_no_spot");
                return;
            }

            if (player.dimension == dim) {
                player.setPositionAndUpdate(dest[0], dest[1], dest[2]);
            } else {
                // 与自适应终端同款：匿名 Teleporter 直接落坐标，绕开"找/建下界门"的老 bug
                server.getConfigurationManager()
                    .transferPlayerToDimension(player, dim, new Teleporter(targetWorld) {

                        @Override
                        public void placeInPortal(Entity entity, double px, double py, double pz, float yaw) {
                            entity.setLocationAndAngles(dest[0], dest[1], dest[2], entity.rotationYaw,
                                entity.rotationPitch);
                            entity.motionX = 0;
                            entity.motionY = 0;
                            entity.motionZ = 0;
                        }

                        @Override
                        public boolean placeInExistingPortal(Entity entity, double px, double py, double pz,
                            float yaw) {
                            placeInPortal(entity, px, py, pz, yaw);
                            return true;
                        }

                        @Override
                        public boolean makePortal(Entity entity) {
                            return true;
                        }
                    });
            }
            chatFormatted(player, EnumChatFormatting.GREEN, "ae2_qof.terminal.teleported", x, y, z, dim);
        }

        /**
         * 在目标方块附近找**可站立**落点：先看正上方，再看四邻的上方；要求
         * "脚/头两格是空气、脚下是实心"。找不到就返回 null（调用方给提示并取消）。
         */
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

        private void chat(EntityPlayerMP player, EnumChatFormatting color, String langKey) {
            player.addChatMessage(new ChatComponentText(color + StatCollector.translateToLocal(langKey)));
        }

        private void chatFormatted(EntityPlayerMP player, EnumChatFormatting color, String langKey, Object... args) {
            player.addChatMessage(
                new ChatComponentText(color + StatCollector.translateToLocalFormatted(langKey, args)));
        }
    }
}
