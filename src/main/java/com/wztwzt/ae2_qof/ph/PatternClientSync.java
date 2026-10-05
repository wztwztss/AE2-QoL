package com.wztwzt.ae2_qof.ph;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.wztwzt.ae2_qof.MyMod;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import reobf.proghatches.gt.metatileentity.PatternDualInputHatch;

/**
 * 3.24.0-fix2：把某台样板总成的 **TE 数据重新下发给"正在跟踪该区块"的玩家**。
 *
 * <h2>为什么需要（根因链，全部有证据）</h2>
 * <ol>
 * <li>样板窗里的格子读的是**客户端自己 TE 里的 {@code pattern[]}**：
 * {@code MTEPatternCraftingBufferMKIII.createPatternWindow2} 里 {@code new ItemStackHandler(acc.getAe2qolPattern())}
 * 交给 {@code ModularSlot} ⇒ 客户端渲染用的是客户端那份数组（{@code Arrays.asList(array)} 绑的是那个数组对象）。</li>
 * <li>样板剪贴板只在**服务端**改数组，然后调 {@code onPatternChange()}/{@code refresh()}（这两者只影响 AE 侧，
 * 所以 AE2 接口终端/下单侧一直是即时正确的）。</li>
 * <li>GT 5.09.54.133 **没有**给外部用的"通知客户端刷新本机数据"入口：实例 jar 字节码实证
 * {@code IGregTechTileEntity} 无 {@code issueClientUpdate}、{@code issueTileUpdate()} 是 default 空实现
 * （{@code Code: 0: return}）、{@code BaseMetaTileEntity} 也不实现 {@code getDescriptionPacket}。</li>
 * <li>⇒ 客户端那份数组只能靠**区块包**（1.7.10 的区块包内含 TE 的完整 NBT）更新，
 * 这就是"必须走开再回来才生效、关窗重开无效"的原因。</li>
 * </ol>
 *
 * <h2>做法</h2>
 * 手工构造**原版** {@code S35PacketUpdateTileEntity}（GT 与 PH 源码同样使用该类，实例 jar 内亦存在），
 * 内容是这台机器 {@code writeToNBT} 的结果，只发给**正在跟踪该区块**的玩家。
 * 客户端收到后会走 {@code TileEntity.onDataPacket → readFromNBT} —— **与区块重载完全同一条应用路径**
 * （GT 自己的 {@code IGregTechTileEntity} 注释就写着 {@code @see TileEntity#onDataPacket(NetworkManager, S35PacketUpdateTileEntity)}），
 * 因此不需要新网络包、不需要客户端处理器，也不会出现"自行解析 NBT"带来的语义偏差。
 *
 * <h2>为什么不是别的方案</h2>
 * <ul>
 * <li>{@code world.markBlockForUpdate}：只发方块变更，不带 TE 数据；</li>
 * <li>{@code issueTextureUpdate()}：只更新贴图数据，不含样板；</li>
 * <li>自建分片包：可行，但要额外写分片/缺片校验/客户端应用，收益只是省流量，属备选。</li>
 * </ul>
 *
 * <p>风险与边界：载荷是这台机器整份 TE NBT（含 {@code BUFFER_*} 与样板，与区块重载同内容）；
 * 由玩家手动动作触发、频率极低；异常一律吞掉并记 WARN，**绝不影响服务端数据本身**。
 */
public final class PatternClientSync {

    private PatternClientSync() {}

    /** 把该机器的 TE 数据推给它所在区块的跟踪者；失败只记日志。 */
    public static void notifyClients(PatternDualInputHatch machine) {
        try {
            if (machine == null) return;
            IGregTechTileEntity base = machine.getBaseMetaTileEntity();
            if (base == null) return;
            World world = base.getWorld();
            // 只在服务端发；客户端调用直接忽略（剪贴板本身也只在服务端执行）
            if (world == null || world.isRemote) return;
            if (!(world instanceof WorldServer)) return;

            int x = base.getXCoord();
            int y = base.getYCoord();
            int z = base.getZCoord();

            NBTTagCompound nbt = new NBTTagCompound();
            // base 的实际类型是 BaseMetaTileEntity（extends TileEntity）⇒ 直接写 TE NBT
            ((TileEntity) base).writeToNBT(nbt);

            S35PacketUpdateTileEntity packet = new S35PacketUpdateTileEntity(x, y, z, 1, nbt);

            WorldServer server = (WorldServer) world;
            PlayerManager playerManager = server.getPlayerManager();
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            int sent = 0;
            for (Object o : server.playerEntities) {
                if (!(o instanceof EntityPlayerMP player)) continue;
                // 只发给"看得见这个区块"的玩家：与区块包的下发对象一致，避免无谓流量
                if (playerManager.isPlayerWatchingChunk(player, chunkX, chunkZ)) {
                    player.playerNetServerHandler.sendPacket(packet);
                    sent++;
                }
            }
            MyMod.LOG.info(
                "[AE2QoL] 样板同步：{} @ {},{},{} 的 TE 数据已下发给 {} 名跟踪该区块的玩家",
                machine.getMetaName(),
                x,
                y,
                z,
                sent);
        } catch (Throwable t) {
            // 静默兜底会掩盖故障：这里必须留痕，但不改变服务端数据与业务流程
            MyMod.LOG.warn("[AE2QoL] 样板同步：通知客户端失败（服务端数据不受影响）", t);
        }
    }
}
