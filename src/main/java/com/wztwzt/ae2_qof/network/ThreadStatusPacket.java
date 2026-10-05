package com.wztwzt.ae2_qof.network;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.world.World;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 3.25.0（提交 2/2）：线程状态的**降频 S2C 小包** —— 只为 WAILA 服务。
 *
 * <h2>为什么必须单独做一条通道</h2>
 * WAILA 读的是**客户端那份 TE**，而 GT 5.09.54.133 没有任何"服务端改字段后通知客户端"的入口
 * （`issueClientUpdate` 不存在、`issueTileUpdate()` 是空实现、`BaseMetaTileEntity` 不实现 `getDescriptionPacket`；
 * 见 3.24.0-fix2 的取证）。维护仓界面那条路走 MUI2 自己的同步（只对**打开界面**的玩家），
 * 而 WAILA 是**不打开界面**也要看 ⇒ 只能自己发。
 *
 * <h2>刻意做小</h2>
 * 每 10 tick 一次、只发给**正在跟踪该区块**的玩家、只带汇总 + 最多 8 条活跃线程的紧凑字段
 * （不含 ItemStack，只带显示名），因此流量是几百字节级；客户端按 `dim:x:y:z` 存一张小表，
 * 超过 {@link #EXPIRE_MS} 未更新即视为失效（机器被拆/停转/区块卸载后自动不再显示）。
 */
public class ThreadStatusPacket implements IMessage {

    /** 客户端快照有效期（毫秒）。发送侧 10 tick ≈ 500ms 一次，留 4 倍余量。 */
    private static final long EXPIRE_MS = 2000L;

    /** 单包最多带几条线程明细（与 WAILA 潜行展开上限一致）。 */
    public static final int MAX_ROWS = 8;

    public int dim;
    public int x;
    public int y;
    public int z;

    /** 汇总：活跃数 / 线程总数 / 总并行 / 总耗电 EU/t。 */
    public int active;
    public int total;
    public long parallelSum;
    public long powerSum;

    /** 明细（按下标对齐；长度 ≤ {@link #MAX_ROWS}）。 */
    public int[] rowIndex = new int[0];
    public int[] rowState = new int[0];
    public int[] rowPercent = new int[0];
    public int[] rowRemain = new int[0];
    public int[] rowParallel = new int[0];
    public String[] rowName = new String[0];

    public ThreadStatusPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        active = buf.readInt();
        total = buf.readInt();
        parallelSum = buf.readLong();
        powerSum = buf.readLong();
        int n = Math.min(MAX_ROWS, buf.readInt());
        rowIndex = new int[n];
        rowState = new int[n];
        rowPercent = new int[n];
        rowRemain = new int[n];
        rowParallel = new int[n];
        rowName = new String[n];
        for (int i = 0; i < n; i++) {
            rowIndex[i] = buf.readInt();
            rowState[i] = buf.readInt();
            rowPercent[i] = buf.readInt();
            rowRemain[i] = buf.readInt();
            rowParallel[i] = buf.readInt();
            rowName[i] = ByteBufUtils.readUTF8String(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(active);
        buf.writeInt(total);
        buf.writeLong(parallelSum);
        buf.writeLong(powerSum);
        int n = Math.min(MAX_ROWS, rowIndex.length);
        buf.writeInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeInt(rowIndex[i]);
            buf.writeInt(rowState[i]);
            buf.writeInt(rowPercent[i]);
            buf.writeInt(rowRemain[i]);
            buf.writeInt(rowParallel[i]);
            ByteBufUtils.writeUTF8String(buf, rowName[i] == null ? "" : rowName[i]);
        }
    }

    // ===================== 客户端小表 =====================

    private static final Map<String, Entry> CLIENT = new HashMap<>();

    private static final class Entry {

        final ThreadStatusPacket packet;
        final long stamp;

        Entry(ThreadStatusPacket packet, long stamp) {
            this.packet = packet;
            this.stamp = stamp;
        }
    }

    private static String key(int dim, int x, int y, int z) {
        return dim + ":" + x + ":" + y + ":" + z;
    }

    /** 客户端收到包：存进小表（IMessageHandler 在客户端主线程被调用）。 */
    public static void accept(ThreadStatusPacket packet) {
        synchronized (CLIENT) {
            long now = System.currentTimeMillis();
            CLIENT.put(key(packet.dim, packet.x, packet.y, packet.z), new Entry(packet, now));
            // 顺手清理过期项：机器被拆/停转/离开视距后不会再发，靠这里失效
            for (Iterator<Map.Entry<String, Entry>> it = CLIENT.entrySet()
                .iterator(); it.hasNext();) {
                if (now - it.next()
                    .getValue().stamp > EXPIRE_MS) {
                    it.remove();
                }
            }
        }
    }

    /** 客户端查询：WAILA 渲染时按坐标取（取不到返回 null ⇒ 不追加任何行）。 */
    public static ThreadStatusPacket lookup(World world, int x, int y, int z) {
        if (world == null) return null;
        int dim = world.provider == null ? 0 : world.provider.dimensionId;
        synchronized (CLIENT) {
            Entry entry = CLIENT.get(key(dim, x, y, z));
            if (entry == null) return null;
            if (System.currentTimeMillis() - entry.stamp > EXPIRE_MS) {
                CLIENT.remove(key(dim, x, y, z));
                return null;
            }
            return entry.packet;
        }
    }

    public static class Handler implements IMessageHandler<ThreadStatusPacket, IMessage> {

        @Override
        public IMessage onMessage(ThreadStatusPacket message, MessageContext ctx) {
            // 客户端主线程：只写本地小表，不碰世界数据（因此无需 scheduleServerTask）
            accept(message);
            return null;
        }
    }
}
