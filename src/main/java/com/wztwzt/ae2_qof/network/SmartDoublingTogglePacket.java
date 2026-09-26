package com.wztwzt.ae2_qof.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.api.ISmartDoublingContainer;
import com.wztwzt.ae2_qof.api.ISmartDoublingMedium;

import appeng.api.networking.crafting.ICraftingProvider;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * C2S：智能倍增开关。三种模式：
 * <ul>
 * <li><b>容器模式</b>（旧路径）：写回当前打开的 {@link ISmartDoublingContainer}（AE2 ME 接口用）；</li>
 * <li><b>坐标设置模式</b>（3.21.3 新增）：按机器坐标在**服务端**定位 MTE → 写入开关 → 持久化；</li>
 * <li><b>坐标查询模式</b>：只请求服务端把当前真值回给客户端（打开界面时对齐显示用）。</li>
 * </ul>
 *
 * <h2>为什么必须新增坐标模式（3.21.3 实机根因）</h2>
 * GT/GTNL/PH 三处开关都建在**仅客户端的 mixin** 里（见 {@code mixins.ae2_qof.json} 的 {@code client} 段），
 * 而它们原先用 {@code BooleanSyncValue(...).allowC2S()} 直接写机器字段——这条写入要成立，必须
 * **服务端存在同名同步处理器**（MUI2 面板双端各构建一次才会注册）。专用服务端没有那段注入，
 * 于是写入被 MUI2 丢弃：`ModularNetworkSide.receivePacket`（`activeScreens` 查不到 → 直接 return）
 * 与 `ModularSyncManager.receiveWidgetUpdate`（`psm == null` 且面板名在历史上出现过 → 注释原文
 * "we simply discard the packet silently"）两条分支**都不写日志**。
 * 结果：服务端机器开关恒 false → CPU `hasSmartDoublingTask` 返回 false → **静默走原版一次一轮**；
 * 单人之所以正常，是因为 SP 是同一个客户端 JVM，client 段 mixin 照样变换了该类，
 * 集成服务端调用面板构建方法时跑的就是注入后版本。
 *
 * <p>改成"客户端只报坐标、服务端重新解析并写入 + 回读权威值"后，**与 MUI2 面板双端构建行为完全解耦**，
 * 两个分支都成立。服务端写入前会校验目标确实是 {@link ISmartDoublingMedium}，写后打日志
 * （不再有静默失败）。
 */
public class SmartDoublingTogglePacket implements IMessage {

    private static final int MODE_CONTAINER = 0;
    private static final int MODE_SET = 1;
    private static final int MODE_QUERY = 2;

    private int mode;
    private boolean enabled;
    private int dim;
    private int x;
    private int y;
    private int z;

    public SmartDoublingTogglePacket() {}

    /** 旧路径：写当前打开的容器（AE2 ME 接口）。 */
    public SmartDoublingTogglePacket(boolean enabled) {
        this.mode = MODE_CONTAINER;
        this.enabled = enabled;
    }

    private SmartDoublingTogglePacket(int mode, boolean enabled, int dim, int x, int y, int z) {
        this.mode = mode;
        this.enabled = enabled;
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * 客户端便捷方法：把开关真值写给服务端（按机器坐标）。
     * 位置拿不到（机器还没在世界里）时不发——服务端不会改，本地视觉仍会翻转，下次操作会再对齐。
     */
    public static void sendFromClient(boolean enabled, IMetaTileEntity mte) {
        int[] pos = posOf(mte);
        if (pos == null) return;
        ModNetwork.CHANNEL.sendToServer(new SmartDoublingTogglePacket(MODE_SET, enabled, pos[0], pos[1], pos[2], pos[3]));
    }

    /** 客户端便捷方法：打开界面时向服务端要一次真值，避免显示客户端内存里的旧值。 */
    public static void queryFromClient(IMetaTileEntity mte) {
        int[] pos = posOf(mte);
        if (pos == null) return;
        ModNetwork.CHANNEL.sendToServer(new SmartDoublingTogglePacket(MODE_QUERY, false, pos[0], pos[1], pos[2], pos[3]));
    }

    private static int[] posOf(IMetaTileEntity mte) {
        try {
            if (mte == null) return null;
            TileEntity base = (TileEntity) mte.getBaseMetaTileEntity();
            if (base == null || base.getWorldObj() == null) return null;
            return new int[] { base.getWorldObj().provider.dimensionId, base.xCoord, base.yCoord, base.zCoord };
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 「玩家刚把某位置的开关设为 true」的期望登记（位置 → 过期时间戳）。
     *
     * <p>3.21.3 诊断用：正常情况下服务端写入立即生效，CPU 看到的就是 true；
     * 若 CPU 仍看到 false（开关根本没写进服务端——本次要修的那类静默故障），
     * CPU 侧会据此打一条 WARN，把"勾了却不生效"变成可定性现象。
     * 只有在玩家主动开启过之后 5 分钟内才可能触发，正常运行零噪声。
     */
    private static final java.util.Map<String, Long> EXPECT_ENABLED = new java.util.concurrent.ConcurrentHashMap<>();

    private static String expectKey(int dim, int x, int y, int z) {
        return dim + ":" + x + ":" + y + ":" + z;
    }

    /** 登记"期望开启"（服务端 SET(true) 时调用）。 */
    public static void markExpectEnabled(int dim, int x, int y, int z) {
        try {
            EXPECT_ENABLED.put(expectKey(dim, x, y, z), System.currentTimeMillis() + 300_000L);
        } catch (Throwable ignored) {}
    }

    /** 该位置是否处于"刚被要求开启"的期望窗口内。 */
    public static boolean isExpectEnabled(int dim, int x, int y, int z) {
        Long until = EXPECT_ENABLED.get(expectKey(dim, x, y, z));
        return until != null && until > System.currentTimeMillis();
    }

    /** 清除"期望开启"登记（玩家关闭开关时调用，避免 CPU 侧误报）。 */
    public static void clearExpectEnabled(int dim, int x, int y, int z) {
        try {
            EXPECT_ENABLED.remove(expectKey(dim, x, y, z));
        } catch (Throwable ignored) {}
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.mode = buf.readInt();
            this.enabled = buf.readBoolean();
            this.dim = buf.readInt();
            this.x = buf.readInt();
            this.y = buf.readInt();
            this.z = buf.readInt();
        } catch (Throwable t) {
            this.mode = -1;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(mode);
        buf.writeBoolean(enabled);
        buf.writeInt(dim);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<SmartDoublingTogglePacket, IMessage> {

        @Override
        public IMessage onMessage(SmartDoublingTogglePacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    if (message.mode == MODE_CONTAINER) {
                        Container c = player.openContainer;
                        if (c instanceof ISmartDoublingContainer sdc) {
                            sdc.setSmartDoubling(message.enabled);
                        }
                        return;
                    }
                    applyAtCoords(player, message);
                } catch (Throwable t) {
                    MyMod.LOG.error("Smart doubling toggle failed", t);
                }
            });
            return null;
        }

        /**
         * 服务端权威路径：按坐标重新定位机器 → 校验能力接口 → 写入开关 → 回读真值给客户端。
         * 这一路径不依赖任何客户端注入，因此**专用服务端也成立**。
         */
        private static void applyAtCoords(EntityPlayerMP player, SmartDoublingTogglePacket msg) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) return;
            WorldServer world = server.worldServerForDimension(msg.dim);
            if (world == null) return;
            TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
            if (!(te instanceof IGregTechTileEntity)) {
                MyMod.LOG.warn(
                    "[AE2QoL] 智能倍增：坐标 d{} [{}, {}, {}] 上没有 GT 机器，请求已忽略（player={}）",
                    msg.dim, msg.x, msg.y, msg.z, player.getCommandSenderName());
                return;
            }
            IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            if (!(mte instanceof ISmartDoublingMedium sdm)) {
                MyMod.LOG.warn(
                    "[AE2QoL] 智能倍增：{} @ d{} [{}, {}, {}] 未实现 ISmartDoublingMedium，请求已忽略",
                    mte == null ? "null" : mte.getClass().getSimpleName(), msg.dim, msg.x, msg.y, msg.z);
                return;
            }

            if (msg.mode == MODE_SET) {
                sdm.setSmartDoubling(msg.enabled);
                if (msg.enabled) {
                    markExpectEnabled(msg.dim, msg.x, msg.y, msg.z);
                } else {
                    // 3.21.4：关闭开关时清掉"期望开启"登记，否则 CPU 侧会误报
                    // "刚要求开启但服务端仍为 false"（实测出现过这个误报）。
                    clearExpectEnabled(msg.dim, msg.x, msg.y, msg.z);
                }
                try {
                    te.markDirty();
                } catch (Throwable ignored) {}
                boolean isProvider = mte instanceof ICraftingProvider;
                MyMod.LOG.info(
                    "[AE2QoL] 智能倍增开关 = {} @ {} d{} [{}, {}, {}]（样板介质={}）player={}",
                    msg.enabled, mte.getClass().getSimpleName(), msg.dim, msg.x, msg.y, msg.z, isProvider,
                    player.getCommandSenderName());
                if (msg.enabled && !isProvider) {
                    MyMod.LOG.warn(
                        "[AE2QoL] {} 未实现 ICraftingProvider，isSmartDoublingEnabled() 会恒为 false，开关不会生效",
                        mte.getClass().getSimpleName());
                }
            }

            // 设置与查询都回一份服务端真值，客户端据此对齐显示（避免"客户端勾住、服务端没勾"的假象）
            ModNetwork.CHANNEL.sendTo(
                new SmartDoublingStatePacket(msg.dim, msg.x, msg.y, msg.z, sdm.isSmartDoublingEnabled()),
                player);
        }
    }
}
