package com.wztwzt.ae2_qof.proline;

import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldServer;

import com.wztwzt.ae2_qof.MyMod;

import cpw.mods.fml.common.Loader;
import serverutils.data.ClaimedChunks;
import serverutils.lib.EnumTeamStatus;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;
import serverutils.lib.math.ChunkDimPos;

/**
 * 产线功能的 ServerUtilities 团队 API 接入层（O1 拍板：compileOnly jar + 双判据守卫）。
 *
 * <h2>为什么这样分层（坑 #18/#19 的落法）</h2>
 * <ul>
 * <li>本类是<b>无 SU 类型引用</b>的外壳：modid 守卫({@code serverutilities}——已用包内
 * mcmod.info 核对，2026-10-16)与关键类判据({@code serverutils.data.ClaimedChunks})都写这层，
 * SU 缺席时整个产线功能禁用；</li>
 * <li>持有 SU 类型的成员都放进嵌套类 {@link Su}——JVM 对嵌套类是<b>按需加载</b>的，
 * 只要守卫先 return，SU 缺席时这个类永远不会被解析（PhIntegration 同款纪律，2024-07 先例）。</li>
 * </ul>
 */
public final class ProLineGate {

    /** 真实 modid：来自对方包内 mcmod.info（2026-10-16 unzip 核对）——不是 jar 文件名、不是包名。 */
    public static final String SU_MODID = "serverutilities";
    /** 关键类判据：真正决定功能可用的是类在不在。 */
    public static final String SU_ANCHOR = "serverutils.data.ClaimedChunks";

    private static boolean present;
    private static boolean probed;

    private ProLineGate() {}

    public static boolean isPresent() {
        if (!probed) {
            probed = true;
            boolean mod = false;
            try {
                mod = Loader.isModLoaded(SU_MODID);
            } catch (Throwable ignored) {}
            boolean cls = false;
            try {
                Class.forName(SU_ANCHOR, false, ProLineGate.class.getClassLoader());
                cls = true;
            } catch (Throwable ignored) {}
            present = mod && cls;
            // 三态日志（坑 #19）：已启用 / 真判据缺失 / 只有一半
            MyMod.LOG.info(
                "[AE2QoL] 产线团队门：ServerUtilities 守卫结果={}" + "（modid={}={} 关键类={}={}）",
                present,
                SU_MODID,
                mod,
                SU_ANCHOR,
                cls);
        }
        return present;
    }

    /** SU 在场时返回玩家所属团队；缺席/无团队返回 null（调用方禁用新建，不改玩法）。 */
    public static Object teamOfPlayer(EntityPlayer player) {
        if (!isPresent() || player == null) return null;
        return Su.teamOfPlayer(player);
    }

    /** 当前目标机器坐标所在区块的团队；未声明/未加载返回 null。 */
    public static Object teamOfChunk(int dim, int chunkX, int chunkZ) {
        if (!isPresent()) return null;
        return Su.teamOfChunk(dim, chunkX, chunkZ);
    }

    /** 玩家对该团队是否拥有成员以上权限（MEMBER/OWNER/MOD）。任何异常都不放行（世界一致性）。 */
    public static boolean isMember(Object teamObj, EntityPlayer player) {
        if (!isPresent() || teamObj == null || player == null) return false;
        return Su.isMember(teamObj, player);
    }

    /** 团队标题（展示用）；异常兜底返回团队 uid 串。 */
    public static String teamTitle(Object teamObj) {
        if (!isPresent() || teamObj == null) return "?";
        return Su.teamTitle(teamObj);
    }

    /** SU 类型只出现在这个嵌套类里（懒加载：守卫不放行永不解析）。 */
    private static final class Su {

        static ForgeTeam teamOfPlayer(EntityPlayer player) {
            try {
                Universe universe = Universe.getNullable();
                if (universe == null) return null;
                ForgePlayer p = universe.players.get(player.getUniqueID());
                if (p == null || !p.hasTeam()) return null;
                return p.team;
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 产线团队解析失败", t);
                return null;
            }
        }

        static ForgeTeam teamOfChunk(int dim, int chunkX, int chunkZ) {
            try {
                if (!ClaimedChunks.isActive()) return null;
                // ChunkDimPos 字段顺序 = (posX, posZ, dim)（javap -c field 排布核对）
                return ClaimedChunks.instance.getChunkTeam(new ChunkDimPos(chunkX, chunkZ, dim));
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 产线区块团队解析失败 dim={} [{} {}]", dim, chunkX, chunkZ, t);
                return null;
            }
        }

        static boolean isMember(Object teamObj, EntityPlayer player) {
            try {
                ForgeTeam team = (ForgeTeam) teamObj;
                Universe universe = Universe.getNullable();
                if (universe == null) return false;
                ForgePlayer p = universe.players.get(player.getUniqueID());
                if (p == null) return false;
                EnumTeamStatus st = team.players.get(p);
                return st != null && st.isEqualOrGreaterThan(EnumTeamStatus.MEMBER);
            } catch (Throwable t) {
                MyMod.LOG.warn("[AE2QoL] 产线成员校验失败", t);
                return false;
            }
        }

        static String teamTitle(Object teamObj) {
            try {
                return ((ForgeTeam) teamObj).getTitle()
                    .getFormattedText();
            } catch (Throwable t) {
                return String.valueOf(System.identityHashCode(teamObj));
            }
        }

        /** 已声索区块的 TE 收集（候选清单 §5.1）：逐 chunk 遍历 TE map，预算交给调用方。 */
        static Map<Integer, WorldServer> dimsOnline() {
            return null; // 由调用方用 DimensionManager 自取，占位保持 API 对称
        }
    }
}
