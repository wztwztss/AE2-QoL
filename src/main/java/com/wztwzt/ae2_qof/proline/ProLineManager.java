package com.wztwzt.ae2_qof.proline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldServer;

import com.wztwzt.ae2_qof.MyMod;

/**
 * 产线控制面：所有"改定义"的操作都在这里原子落地（设计 §十 任务 1）。
 *
 * <p>
 * 纪律（v6）：每个操作 = ①SU 门（缺依赖整体拒绝）→ ②团队/互斥校验 → ③改数据 + markDirty。
 * 任何失败分支都要有日志或聊天反馈，不做静默兜底（坑 #6/#19）。
 */
public final class ProLineManager {

    /** 打开过的活跃产线（FIFO 上限 16，O7/§八）：lineId → 最近点击时间（ms）。 */
    private static final Map<String, Long> ACTIVE_LINES = new ConcurrentHashMap<>();
    /** NEI/导入目标会话（O3/A6 拍板）：playerUUID → [lineId, stepId(插到它后面可空)]。 */
    private static final Map<UUID, String[]> IMPORT_TARGETS = new ConcurrentHashMap<>();
    /** 机器运行态缓存（采样轮写入，GUI 快照读取）：lineId → (stepId → [running, bound])。 */
    private static final Map<String, Map<String, int[]>> RUNNING = new ConcurrentHashMap<>();

    private ProLineManager() {}

    // ===== 活跃产线（采样对象选择，§八：只采样"被打开过界面的活跃产线"）=====
    public static void touchActive(String lineId) {
        if (lineId == null || lineId.isEmpty()) return;
        ACTIVE_LINES.put(lineId, System.currentTimeMillis());
        while (ACTIVE_LINES.size() > 16) {
            String oldest = null;
            long min = Long.MAX_VALUE;
            for (Map.Entry<String, Long> e : ACTIVE_LINES.entrySet()) {
                if (e.getValue() < min) {
                    min = e.getValue();
                    oldest = e.getKey();
                }
            }
            if (oldest == null || oldest.equals(lineId)) {
                if (oldest != null && !oldest.equals(lineId)) ACTIVE_LINES.remove(oldest);
                break;
            }
            ACTIVE_LINES.remove(oldest);
        }
    }

    /** 活跃产线 id 列表（采样轮拿走最高优先的 ≤16 条）。 */
    public static List<String> activeLineIds() {
        return new ArrayList<>(ACTIVE_LINES.keySet());
    }

    // ===== NEI / 导入目标会话（O3）=====
    public static void setImportTarget(UUID playerId, String lineId, String afterStepId) {
        IMPORT_TARGETS.put(playerId, new String[] { lineId, afterStepId == null ? "" : afterStepId });
    }

    public static String[] getImportTarget(UUID playerId) {
        return IMPORT_TARGETS.get(playerId);
    }

    // ===== 机器运行态（采样轮写入）=====
    public static void putRunState(String lineId, Map<String, int[]> perStep) {
        RUNNING.put(lineId, perStep);
    }

    public static int[] runState(String lineId, String stepId) {
        Map<String, int[]> m = RUNNING.get(lineId);
        if (m == null) return null;
        return m.get(stepId);
    }

    // ===== 产线生命周期 =====

    /**
     * 新建产线：SU 门 + 无团队拒建（O6）+ 团队上限（O7）+ 锁定 networkId 与终端锚点（O2）。
     * dim/x/y/z = 创建时打开产线页的那台终端。
     */
    public static ProductionLine createLine(EntityPlayer player, String name, String networkId, int dim, int x, int y,
        int z) {
        if (!ProLineGate.isPresent()) {
            send(player, "未检测到 ServerUtilities，产线功能不可用");
            return null;
        }
        Object team = ProLineGate.teamOfPlayer(player);
        if (team == null) {
            send(player, "你没有团队（用 ServerUtilities 建队后再来）——产线按团队归属");
            return null;
        }
        ProLineData data = dataOf(player);
        if (data == null) return null;
        int same = 0;
        for (ProductionLine l : data.lines) {
            if (l.ownerUuid != null && l.ownerUuid.equals(String.valueOf(player.getUniqueID()))) same++;
        }
        if (same >= ProductionLine.MAX_LINES_PER_TEAM) {
            send(player, "产线数量已达上限（" + ProductionLine.MAX_LINES_PER_TEAM + " 条/团队）");
            return null;
        }
        ProductionLine line = new ProductionLine();
        line.name = safe(name, "新产线");
        line.ownerUuid = String.valueOf(player.getUniqueID());
        line.networkId = networkId == null ? "" : networkId; // O2：创建时刻锁定（可为空=无口径，列显示"—"）
        line.terminalDim = dim;
        line.terminalX = x;
        line.terminalY = y;
        line.terminalZ = z;
        data.lines.add(line);
        data.markDirty();
        MyMod.LOG.info("[AE2QoL] 新建产线 {}（网络口径={}，终端锚点 D{} [{} {} {}]）", line.id, line.networkId, dim, x, y, z);
        return line;
    }

    /** 删除整线（O4：双击确认由 GUI 层做，这里的聊天确认是第二道）。 */
    public static boolean deleteLine(EntityPlayer player, String lineId, boolean confirmedByChat) {
        if (!ProLineGate.isPresent()) return false;
        ProLineData data = dataOf(player);
        ProductionLine line = data == null ? null : data.findLine(lineId);
        if (line == null || !canEdit(player, line)) return false;
        // 删线前把全部绑定机器恢复可工作（真暂停语义下的安全网）
        for (ProductionStep s : line.steps) {
            for (MachineRef ref : s.machines) applyWorkAllowed(ref, true);
        }
        data.lines.remove(line);
        data.markDirty();
        RUNNING.remove(lineId);
        ACTIVE_LINES.remove(lineId);
        if (!confirmedByChat) MyMod.LOG.info("[AE2QoL] 产线 {} 已删除（GUI 二次确认）", lineId);
        send(player, "已删除产线：" + line.name);
        return true;
    }

    public static boolean renameLine(EntityPlayer player, String lineId, String name) {
        return editLineMeta(player, lineId, name);
    }

    private static boolean editLineMeta(EntityPlayer player, String lineId, String name) {
        ProLineData data = dataOf(player);
        ProductionLine line = data == null ? null : data.findLine(lineId);
        if (line == null || !canEdit(player, line)) return false;
        line.name = safe(name, line.name);
        data.markDirty();
        return true;
    }

    // ===== 步骤操作（A1：全部按 stepId）=====

    public static ProductionStep appendStep(EntityPlayer player, String lineId, ProductionStep step) {
        ProLineData data = dataOf(player);
        ProductionLine line = data == null ? null : data.findLine(lineId);
        if (line == null || step == null || !canEdit(player, line)) return null;
        if (line.steps.size() >= ProductionLine.MAX_STEPS) {
            send(player, "步骤数已达上限（" + ProductionLine.MAX_STEPS + "）");
            return null;
        }
        line.steps.add(step);
        data.markDirty();
        return step;
    }

    public static boolean removeStep(EntityPlayer player, String lineId, String stepId) {
        ProLineData data = dataOf(player);
        ProductionLine line = data == null ? null : data.findLine(lineId);
        if (line == null || !canEdit(player, line)) return false;
        ProductionStep s = line.findStep(stepId);
        if (s == null) return false;
        line.steps.remove(s);
        line.pausedStepIds.remove(stepId);
        data.markDirty();
        return true;
    }

    /** 上下移一步（delta=-1 前移 / +1 后移）。 */
    public static boolean moveStep(EntityPlayer player, String lineId, String stepId, int delta) {
        ProLineData data = dataOf(player);
        ProductionLine line = data == null ? null : data.findLine(lineId);
        if (line == null || !canEdit(player, line)) return false;
        int idx = indexOfStep(line, stepId);
        if (idx < 0) return false;
        int to = idx + delta;
        if (to < 0 || to >= line.steps.size()) return false;
        ProductionStep s = line.steps.remove(idx);
        line.steps.add(to, s);
        data.markDirty();
        return true;
    }

    /**
     * 暂停/恢复（2026-10-16 用户拍板：**真暂停**——写 GT 机器本体 disableWorking/enableWorking，
     * 取代设计 §七 的"软暂停铁则"）。
     */
    public static boolean setStepPaused(EntityPlayer player, String lineId, String stepId, boolean paused) {
        ProLineData data = dataOf(player);
        ProductionLine line = data == null ? null : data.findLine(lineId);
        if (line == null || !canEdit(player, line)) return false;
        ProductionStep step = line.findStep(stepId);
        if (step == null) return false;
        if (paused) line.pausedStepIds.add(stepId);
        else line.pausedStepIds.remove(stepId);
        data.markDirty();
        applyPauseToStep(line, step);
        return true;
    }

    public static boolean setLinePaused(EntityPlayer player, String lineId, boolean paused) {
        ProLineData data = dataOf(player);
        ProductionLine line = data == null ? null : data.findLine(lineId);
        if (line == null || !canEdit(player, line)) return false;
        line.linePaused = paused;
        data.markDirty();
        for (ProductionStep s : line.steps) applyPauseToStep(line, s);
        return true;
    }

    /** 按线当前口径（整线或步骤暂停）把停机状态写到该步全部绑定机器。 */
    public static void applyPauseToStep(ProductionLine line, ProductionStep step) {
        boolean paused = line.isStepPausedByLineOrStep(step);
        for (MachineRef ref : step.machines) applyWorkAllowed(ref, !paused);
    }

    /** 对单台机器写 GT 停机位（enable=复工）。区块未加载/TE 缺失直接跳过（自愈交采样轮重放）。 */
    public static void applyWorkAllowed(MachineRef ref, boolean enable) {
        try {
            WorldServer world = net.minecraftforge.common.DimensionManager.getWorld(ref.dim);
            if (world == null || !world.blockExists(ref.x, ref.y, ref.z)) return;
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(ref.x, ref.y, ref.z);
            if (te instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity
                && te instanceof gregtech.api.metatileentity.BaseMetaTileEntity) {
                gregtech.api.metatileentity.BaseMetaTileEntity base = (gregtech.api.metatileentity.BaseMetaTileEntity) te;
                if (enable) base.enableWorking();
                else base.disableWorking();
            }
        } catch (Throwable t) {
            com.wztwzt.ae2_qof.MyMod.LOG
                .warn("[AE2QoL] 产线真暂停写机器失败 D" + ref.dim + "[" + ref.x + "," + ref.y + "," + ref.z + "]", t);
        }
    }

    // ===== 机器绑定（§5：坐标身份 + 单机单线）=====

    /** 候选面点选绑定：跨线互斥校验 + 团队区块校验（§7 新增校验）。 */
    public static boolean bindMachine(EntityPlayer player, String lineId, String stepId, MachineRef ref) {
        ProLineData data = dataOf(player);
        ProductionLine line = data == null ? null : data.findLine(lineId);
        if (line == null || ref == null || !canEdit(player, line)) return false;
        ProductionStep step = line.findStep(stepId);
        if (step == null) return false;
        // 互斥：同一坐标已经是别的产线的机器 → 拒绝并指认（§5.1）
        for (ProductionLine other : data.findLine(lineId) != null ? data.lines : data.lines) {
            for (ProductionStep os : other.steps) {
                if (os.machines.contains(ref)) {
                    if (other == line) continue;
                    send(player, "该机器已在产线【" + other.name + " · " + os.name + "】里——一台机器只属一条产线");
                    return false;
                }
            }
        }
        if (!step.machines.contains(ref)) step.machines.add(ref);
        data.markDirty();
        // 新绑定的机器若挂在被暂停的步骤上，立刻停掉它（真暂停语义）
        applyWorkAllowed(ref, !line.isStepPausedByLineOrStep(step));
        return true;
    }

    public static boolean unbindMachine(EntityPlayer player, String lineId, String stepId, MachineRef ref) {
        ProLineData data = dataOf(player);
        ProductionLine line = data == null ? null : data.findLine(lineId);
        if (line == null || !canEdit(player, line)) return false;
        ProductionStep step = line.findStep(stepId);
        if (step == null) return false;
        boolean hit = step.machines.remove(ref);
        if (hit) {
            data.markDirty();
            // 解绑 = 机器归还自由身：恢复可工作（是否真的开机由机器自己的红石/维护逻辑决定）
            applyWorkAllowed(ref, true);
        }
        return hit;
    }

    /** 编辑校验：产线存在 + "创建者所在团队 = 玩家团队"（O6 简化口径：owner 记创建者 UUID；团队解散走只读孤儿态）。 */
    private static boolean canEdit(EntityPlayer player, ProductionLine line) {
        if (!ProLineGate.isPresent()) return false;
        Object team = ProLineGate.teamOfPlayer(player);
        if (team == null) return false; // 无团队一律可看不可改
        // 成员资格：产线队友（owner 或成员）——用 Gate 的成员判定走 owner 的 ForgePlayer team
        return ProLineGate.isMember(team, player) && sameTeamOwner(player, line);
    }

    private static boolean sameTeamOwner(EntityPlayer player, ProductionLine line) {
        try {
            Object ownerTeamNow = ProLineGate.teamOfPlayer(player);
            String ownerUuid = line.ownerUuid;
            java.util.UUID uuid = java.util.UUID.fromString(ownerUuid);
            serverutils.lib.data.Universe uni = serverutils.lib.data.Universe.getNullable();
            serverutils.lib.data.ForgePlayer owner = uni == null ? null : uni.players.get(uuid);
            Object ownerTeam = owner == null ? null : owner.team;
            return ownerTeam == ownerTeamNow && ownerTeam != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int indexOfStep(ProductionLine line, String stepId) {
        for (int i = 0; i < line.steps.size(); i++) {
            if (line.steps.get(i).id.equals(stepId)) return i;
        }
        return -1;
    }

    /** 会话世界取数：1.7.10 服务端分支的 worldObj 一定是 WorldServer；客户端/异常一律 null（调用方判空）。 */
    private static ProLineData dataOf(EntityPlayer player) {
        if (player == null || !(player.worldObj instanceof WorldServer)) return null;
        return ProLineData.get((WorldServer) player.worldObj);
    }

    private static String safe(String s, String fallback) {
        if (s == null) return fallback;
        s = s.trim();
        return s.isEmpty() ? fallback : s;
    }

    private static void send(EntityPlayer player, String msg) {
        if (player != null) {
            player.addChatMessage(new net.minecraft.util.ChatComponentText("[产线] " + msg));
        }
    }
}
