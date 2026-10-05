package com.wztwzt.ae2_qof.hatch.thread;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.WorldServer;

import com.wztwzt.ae2_qof.network.ThreadStatusPacket;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * 3.25.0（提交 2/2）：把线程状态**降频**推给"正在跟踪该机器区块"的玩家，供 WAILA 显示。
 *
 * <p>只做两件事：按 {@link #INTERVAL} 节流（默认每 10 tick 一次），然后把汇总 + **最慢的 N 条**线程
 * 打进 {@link ThreadStatusPacket} 发给跟踪者。不在包里的线程（超过 8 条）在 WAILA 里折叠为计数，
 * 与线框稿一致。发送对象与区块包一致（{@code isPlayerWatchingChunk}），因此不会给看不见的人发流量。
 */
public final class ThreadStatusBroadcaster {

    /** 降频：每 N tick 发一次（10 tick ≈ 0.5 秒，WAILA 观感足够）。 */
    public static final int INTERVAL = 10;

    private ThreadStatusBroadcaster() {}

    /**
     * G6：**中央 server tick 驱动**的入口 —— 对所有已启用的引擎各发一次。
     * 节流交给调用方（ticker 每 10 tick 调一次）；这里把每台机器的计数清零后走原路径，避免重复实现发送逻辑。
     */
    public static void broadcastAll() {
        for (Ae2qolThreadEngine engine : Ae2qolThreadEngine.allEngines()) {
            if (engine == null || !engine.isEnabled()) continue;
            MTEMultiBlockBase machine = engine.machine();
            if (machine == null) continue;
            engine.broadcastCounter = 0;
            maybeBroadcast(machine, engine);
        }
    }

    /** 由 {@code MixinMTEMultiBlockBase} 在"计算"与"每 tick 推进"两处调用；内部自行节流。 */
    public static void maybeBroadcast(MTEMultiBlockBase machine, Ae2qolThreadEngine engine) {
        if (machine == null || engine == null || !engine.isEnabled()) return;
        if (engine.broadcastCounter++ % INTERVAL != 0) return;

        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        if (base == null) return;
        if (!(base.getWorld() instanceof WorldServer server)) return;

        ThreadStatusPacket packet = new ThreadStatusPacket();
        packet.dim = server.provider.dimensionId;
        packet.x = base.getXCoord();
        packet.y = base.getYCoord();
        packet.z = base.getZCoord();
        packet.active = engine.activeCount();
        packet.total = engine.threadCount();
        packet.parallelSum = engine.totalParallel();
        packet.powerSum = engine.totalEutPerTick();

        // 只带最慢的若干条（剩余最多的排前面）：常态显示 2 条、潜行展开最多 8 条（线框稿口径）
        List<Ae2qolThreadEngine.Row> rows = new ArrayList<>(engine.activeRows());
        rows.sort(Comparator.comparingInt((Ae2qolThreadEngine.Row r) -> r.remain).reversed());
        int n = Math.min(ThreadStatusPacket.MAX_ROWS, rows.size());
        packet.rowIndex = new int[n];
        packet.rowState = new int[n];
        packet.rowPercent = new int[n];
        packet.rowRemain = new int[n];
        packet.rowParallel = new int[n];
        packet.rowName = new String[n];
        for (int i = 0; i < n; i++) {
            Ae2qolThreadEngine.Row row = rows.get(i);
            packet.rowIndex[i] = row.index;
            packet.rowState[i] = row.state;
            packet.rowPercent[i] = row.percent;
            packet.rowRemain[i] = row.remain;
            packet.rowParallel[i] = row.parallel;
            // G4：WAILA 那一行也遵循"物品优先、否则流体名"，纯流体配方不再显示成 "-"
            packet.rowName[i] = row.icon != null ? row.icon.getDisplayName()
                : (row.fluid != null ? row.fluid.getLocalizedName() : "");
        }

        PlayerManager playerManager = server.getPlayerManager();
        int chunkX = packet.x >> 4;
        int chunkZ = packet.z >> 4;
        for (Object o : server.playerEntities) {
            if (!(o instanceof EntityPlayerMP player)) continue;
            if (playerManager.isPlayerWatchingChunk(player, chunkX, chunkZ)) {
                com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL.sendTo(packet, player);
            }
        }
    }
}
