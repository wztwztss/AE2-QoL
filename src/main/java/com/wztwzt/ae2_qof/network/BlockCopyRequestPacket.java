package com.wztwzt.ae2_qof.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.blockcopy.BlockCopyService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端：请求复制方块完整 NBT（3.22.0-feat1，创造模式 Ctrl+中键）。
 *
 * <h2>为什么要有"两步确认"</h2>
 * 用户口径：**敏感方块（AE2 网络节点/存储元件、GT 多方块控制器）先弹确认**再复制，
 * 且确认形态定为**聊天栏可点击/可再次触发**（不引入新界面）。
 * 这里采用最省事又安全的一种：第一次请求敏感方块 ⇒ 聊天栏提示
 * 「该方块为敏感方块……10 秒内**再次 Ctrl+中键 同一点**即确认」；同一坐标 10 秒内第二次请求即视为确认。
 * 好处：不需要新注册命令、不需要新窗口；误触只会得到一条提示。
 *
 * <h2>线程纪律</h2>
 * 包处理器跑在网络线程 ⇒ 一切世界/背包操作都经
 * {@link ServerTerminalHelper#scheduleServerTask(Runnable)} 归队服务端 tick 线程（项目坑位 #3）。
 */
public class BlockCopyRequestPacket implements IMessage {

    /** 确认窗口：10 秒。 */
    private static final long CONFIRM_WINDOW_MS = 10_000L;

    /** 每玩家的"待确认"记录：key = 玩家名，value = [dim,x,y,z,时间戳]。 */
    private static final Map<String, long[]> PENDING = new ConcurrentHashMap<>();

    private int dim;
    private int x;
    private int y;
    private int z;

    public BlockCopyRequestPacket() {}

    public BlockCopyRequestPacket(int dim, int x, int y, int z) {
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<BlockCopyRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(BlockCopyRequestPacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            ServerTerminalHelper.scheduleServerTask(() -> handle(player, msg));
            return null;
        }

        private void handle(EntityPlayerMP player, BlockCopyRequestPacket msg) {
            try {
                if (player.worldObj == null || player.worldObj.provider.dimensionId != msg.dim) {
                    MyMod.LOG.info(
                        "[AE2QoL] 方块复制：维度不匹配，忽略（玩家 {} / 包 {}）",
                        player.worldObj == null ? "?" : player.worldObj.provider.dimensionId,
                        msg.dim);
                    return;
                }
                String key = player.getCommandSenderName();
                long now = System.currentTimeMillis();
                long[] pending = PENDING.get(key);
                boolean confirmed = pending != null && pending[0] == msg.dim
                    && pending[1] == msg.x
                    && pending[2] == msg.y
                    && pending[3] == msg.z
                    && now - pending[4] <= CONFIRM_WINDOW_MS;

                BlockCopyService.Result result = BlockCopyService.copyBlock(
                    player,
                    player.worldObj,
                    msg.x,
                    msg.y,
                    msg.z,
                    confirmed);

                if (result.needsConfirm) {
                    PENDING.put(key, new long[] { msg.dim, msg.x, msg.y, msg.z, now });
                    BlockCopyService.tell(
                        player,
                        result.message + EnumChatFormatting.RESET
                            + "（10 秒内再次 Ctrl+中键 同一点即确认）",
                        null,
                        null);
                    MyMod.LOG.info(
                        "[AE2QoL] 方块复制：敏感方块待确认（玩家 {} @ {},{} ,{}）",
                        key,
                        msg.x,
                        msg.y,
                        msg.z);
                    return;
                }

                PENDING.remove(key);
                if (!result.ok && result.message != null) {
                    BlockCopyService.tell(player, EnumChatFormatting.RED + result.message, null, null);
                }
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 方块复制请求处理失败（已记日志）", t);
            }
        }
    }
}
