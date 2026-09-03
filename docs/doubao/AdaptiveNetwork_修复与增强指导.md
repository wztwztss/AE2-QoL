# 自适应电网：Bug 修复 + 补充增强指导

> 适用项目：AE2-QoL-1.7.10-GTNH（GTNH 2.9.0 环境）
> 参考实现：`reference_src/GT-Not-Leisure-dev-290`（GTNL EnergyMonitor）
> 文档定位：**逐步可直接照做**的改造指导，不代改代码。
> 行号基于本文件撰写时核对的实际源码。

---

## 0. 已核实的依赖 / API（可直接使用，无需再加依赖）

| API | 位置 | 状态 |
|---|---|---|
| `gregtech.common.misc.spaceprojects.SpaceProjectManager` | `libs/gregtech-5.09.52.594.jar`（compileOnly 已配） | ✅ 存在 |
| `gregtech.api.util.GTUtility.getColoredTierNameFromTier(byte)` | 同上 | ✅ 存在（返回带颜色码字符串） |
| `gregtech.api.util.GTUtility.scientificFormat(java.math.BigInteger)` | 同上 | ✅ 存在 |
| `appeng.client.render.highlighter.BlockPosHighlighter.highlightBlocks(EntityPlayer, List<DimensionalCoord>, String, String, String)` | AE2 rv3-beta-977-GTNH（implementation 已配） | ✅ 存在 |
| `appeng.api.util.DimensionalCoord` | AE2 | ✅ 存在 |
| `com.cleanroommc.modularui.drawable.ItemDrawable` | `libs/modularui2-2.3.73-1.7.10.jar` | ✅ 存在 |
| GTNL 参考类（EnergyMonitor*） | `libs/sciencenotleisure-0.2.7-pre1-dev-290.jar`（compileOnly） | ✅ 存在，仅参考不引用 |

> ⚠️ GT jar 为多版本 jar，类在 `META-INF/versions/17/...` 下，按 Java 17 编译目标正常解析即可。

---

## 一、Bug 修复（P0，正确性）

### 1.1 平均速率单位错误（1h / 10min，显示大 20 倍）

**位置**：`src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetTerminal.java`
- L722（1h 行）：`long avgRate = Math.abs(change) / 3600L;`
- L732（10min 行）：`long avgRate = Math.abs(change) / 600L;`

**原因**：`change` 是跨窗口的能量变化量：
- `change1h` = 当前 EU − 72000 tick 前 EU（72000 tick = 1 小时）
- `change10m` = 当前 EU − 12000 tick 前 EU

要得到 **EU/t**，必须除以 **tick 数**：`72000` / `12000`。当前 `÷3600`、`÷600` 得到的是 **EU/秒**（72000 tick = 3600 秒），比真实 EU/t 大 **20 倍**。

**修复**：

```java
// L722（1h）：÷3600 → ÷72000
long avgRate = Math.abs(change) / 72000L;

// L732（10m）：÷600 → ÷12000
long avgRate = Math.abs(change) / 12000L;
```

**验证**：稳定负载下，1h 行显示值应约为瞬时值的量级（而不是 20 倍）。`GridEnergyStats.getAvgOutputRate1h/10min()`（内部 `-change / WINDOW_*`）本就正确，可复用其口径，但注意它只在负变化时非 0，用于"预计耗尽"；GUI 显示行请保持用绝对值自算。

---

### 1.2 瞬时输入/输出速率未除以窗口（显示大 100 倍）

**位置**：`src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/GridEnergyStats.java` L88-94

**原因**：`bufferSumInput` / `bufferSumOutput` 是最近 **100 个 tick 的累计变化量**（不是单 tick 速率）。GUI（`AdaptiveNetTerminal` L757/L764）直接把它标成 `EU/t` 显示，稳定输入 X EU/t 时显示约 100X。

**修复**（推荐在 stats 层一次修好，Waila/其他调用方也受益）：

```java
public long getInstantInputRate() {
    return bufferSumInput / 100;   // 平均到每 tick（EU/t）
}

public long getInstantOutputRate() {
    return bufferSumOutput / 100;
}
```

> 备选：保留原方法另加 `getInstantInputRatePerTick()`，在 GUI 层除以 100。若选备选，`AdaptiveNetTerminal` L757-767 的 `instantInputSync`/`instantOutputSync` 注入值也要改为新方法。

---

### 1.3 `GridEnergyWorldData` 未接线（设计有、实现漏）

**位置**：`src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/GridEnergyWorldData.java`（实现完整但全项目无调用）；`AdaptiveNetwork.java` L20 `private final GridEnergyStats stats = new GridEnergyStats();`

**问题**：电网统计从不从 WorldSavedData 载入、也不写回。**服务器重启 / 世界重载后，`totalInput`/`totalOutput`、10min/1h 快照、瞬时缓冲全部清零**，与设计文档 §4/§6.3"通过 mapStorage 管理"不符。

**修复步骤**：

**① `AdaptiveNetwork.java`：去掉 `final`，加载入/保存方法**

```java
// 原：private final GridEnergyStats stats = new GridEnergyStats();
private GridEnergyStats stats = new GridEnergyStats();

/** 挂接持久化 stats（key = "owner:freq"）。直接在网络生命周期内调用一次。 */
public void initStats(net.minecraft.world.World world) {
    if (world == null) return;
    GridEnergyStats persisted = GridEnergyWorldData.get(world)
        .getOrCreateStats(owner.toString() + ":" + frequency);
    this.stats = persisted;          // 直接持有持久化实例，读写天然一致
}

/** 周期调用，标记需要写盘（写盘由 MC 调度）。 */
public void saveStats(net.minecraft.world.World world) {
    if (world == null) return;
    GridEnergyWorldData.get(world).markDirty();
}
```

**② 终端接入（`AdaptiveNetTerminal.java`）**：

```java
// onFirstTick（服务端分支，registerTerminal 之后）：
AdaptiveNetwork network = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
if (network != null) network.initStats(aBase.getWorld());

// onPreTick（服务端分支内、tickStats 之后）加周期写盘：
if (network != null) {
    if (aTick % 12000 == 0) network.saveStats(aBase.getWorld());   // 每 10 分钟 markDirty 一次
}
```

**③ 仓室侧**：四个仓室 `onFirstTick` 服务端分支 `registerHatch` 前后同样补一次 `network.initStats(aBase.getWorld())`（网络可能是仓室先于终端创建）。

> ⚠️ `GridEnergyWorldData.get(world)` 依赖 `world.mapStorage`，仅服务端有意义；客户端分支不要调用。
> ⚠️ `AdaptiveNetwork.stats` 被 `tickStats`/`getStats` 使用，换成持久化实例后无需改其他代码。

---

### 1.4 电压切换后子仓列表不刷新

**位置**：`AdaptiveNetwork.java` L55-58 `setVoltageTier`（内部调 `updateAllHelpers()` 但不置 dirty）；列表同步只发生在 `isHatchListDirty()` 时（`AdaptiveNetTerminal.onPreTick`）。

**问题**：在设置 Tab 更换仓室物品（改变电压/安培）后，`updateAllHelpers` 已更新每个 helper 的 tier/amps，但**列表不刷新**，子仓 Tab 的 `(X.XA Tier) EU/t` 停留在旧值，直到有仓室加入/移除。

**修复**（配置变化的三个入口都置 dirty）：

```java
public void setVoltageTier(int voltageTier) {
    this.voltageTier = Math.max(0, Math.min(voltageTier, 15));
    updateAllHelpers();
    markHatchListDirty();   // 新增
}

public void setHatchTier(HatchType type, int tier) {
    hatchTiers[type.slotIndex] = Math.max(0, Math.min(tier, 15));
    markHatchListDirty();   // 新增
}

public void setHatchAmps(HatchType type, int amps) {
    hatchAmps[type.slotIndex] = Math.max(1, amps);
    markHatchListDirty();   // 新增
}
```

---

## 二、补充增强（六项）

### 2.1 真实负载 EU/t + V/A 显示

**现状**：`AdaptiveNetTerminal.sendHatchListSync`（L146-173）里 `eut = V[tier] × amps`，是**额定配置值**，不反映机器是否真的在耗电/发电。列表无法回答"这个仓现在实际走了多少电"。

**目标**：每仓室显示**实际流量 EU/t**（保留 V/A 额定显示）。

**① `AdaptiveHatchHelper.java` 新增字段（服务端维护）**：

```java
private long realFlowEUt;

public long getRealFlowEUt() { return realFlowEUt; }

/** v = 本次采样得到的 EU/t；可选 EMA 平滑（推荐，防抖动） */
public void setRealFlowEUt(long v) {
    this.realFlowEUt = (realFlowEUt * 3 + Math.max(0, v)) / 4;
}
```

**② 取电仓（ENERGY / LASER_SOURCE）**：`AdaptiveNetHatch.onPreTick`、`AdaptiveNetLaserHatch.onPreTick`（两处逻辑相同，每 4 tick 运行一次）。

```java
// 现有：if (diff > 0) { aBase.increaseStoredEnergyUnits(diff, false); }
if (diff > 0) {
    aBase.increaseStoredEnergyUnits(diff, false);
    helper.setRealFlowEUt(diff / 4);   // 新增：每4tick采样 ÷4 = EU/t
} else {
    helper.setRealFlowEUt(0);          // 新增
}
```

**③ 发电仓（DYNAMO / LASER_TARGET）**：`AdaptiveNetDynamoHatch.transferEU`、`AdaptiveNetLaserTargetHatch.transferEU`（两处逻辑相同，每 4 tick 转移一次）。

```java
// 现有：WirelessNetworkManager.addEUToGlobalEnergyMap(owner, BigInteger.valueOf(stored));
WirelessNetworkManager.addEUToGlobalEnergyMap(owner, BigInteger.valueOf(stored));
helper.setRealFlowEUt(stored / 4);   // 新增
```

> ⚠️ `transferEU` 在 `stored <= 0 || !helper.isBound()` 时提前 return，该路径不会更新 realFlowEUt（保留旧值）。建议在 return 前补 `helper.setRealFlowEUt(0)`（未绑定时），已绑定时存量周期性归零可接受。

**④ `HatchListCache.HatchEntry` 增加 `realEUt`**：

```java
public static class HatchEntry {
    ...
    public final int realEUt;   // 新增
    public HatchEntry(String name, short metaId, int eut, int tier, int amps,
                      int hatchType, int index, int x, int y, int z, int dim,
                      int realEUt) {   // 新增尾参
        ...
        this.realEUt = realEUt;
    }
}
```

**⑤ `AdaptiveNetTerminal.sendHatchListSync`**：构造 entry 时传 `(int) h.getRealFlowEUt()`（L168-171 加尾参）。

**⑥ `HatchListSyncPacket` 序列化**：`toBytes`/`fromBytes` 各加一个 `buf.writeInt(e.realEUt)` / `buf.readInt()`（在 `eut` 之后，注意两端顺序一致）。

**⑦ GUI 行显示（`buildHatchListTab`，L811-824）**：行尾追加实际流量：

```java
String realStr = entry.realEUt > 0
    ? " 实际 " + formatEU(entry.realEUt, displayMode) + " EU/t"
    : "";
return ... + EnumChatFormatting.GREEN + eutStr
     + EnumChatFormatting.YELLOW + realStr;
```

**效果示例**：`[E] 自适应电网取电仓 (4.0A IV) 额定 1,310,720 EU/t 实际 523,980 EU/t`。V/A 显示本身已有（`(%.1fA %s)` + tier 名），无需重复加。

---

### 2.2 团队聚合（SpaceProjectManager）

**现状**：电网 key 为 `"UUID:freq"`，owner 是**单人 UUID**（终端 `onFirstTick` 取 `aBase.getOwnerUuid()`），团队多人无法共享同一电网。

**目标**：接入 GT 的 `SpaceProjectManager` 团队系统，团队内所有玩家归属到 **leader 的无线池/电网**（对齐 GTNL `WirelessTeam`）。

**① 新增 `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveTeamHelper.java`**（对齐 GTNL `WirelessTeam.resolveContext`）：

```java
package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import gregtech.common.misc.spaceprojects.SpaceProjectManager;

public final class AdaptiveTeamHelper {

    private AdaptiveTeamHelper() {}

    /** 返回玩家所在团队的 leader；无团队时返回自己。 */
    public static UUID resolveLeader(UUID viewerUuid) {
        if (viewerUuid == null) return null;
        SpaceProjectManager.checkOrCreateTeam(viewerUuid);
        return SpaceProjectManager.getLeader(viewerUuid);
    }

    /** 返回团队全部成员（含 leader）。 */
    public static Set<UUID> resolveMembers(UUID viewerUuid) {
        Set<UUID> resolved = new HashSet<>();
        if (viewerUuid == null) return resolved;
        SpaceProjectManager.checkOrCreateTeam(viewerUuid);
        UUID leader = SpaceProjectManager.getLeader(viewerUuid);
        Collection<UUID> members = SpaceProjectManager.getTeamMembers(leader);
        if (members != null) resolved.addAll(members);
        resolved.add(leader);
        return resolved;
    }

    /** player 是否属于 leader 所在团队。 */
    public static boolean isMemberOf(UUID playerUuid, UUID leaderUuid) {
        if (playerUuid == null || leaderUuid == null) return false;
        return playerUuid.equals(leaderUuid) || resolveMembers(playerUuid).contains(leaderUuid);
    }
}
```

> 说明：`getLeader` 返回 `UUID`，`getTeamMembers(leader)` 返回 `Collection<UUID>`（不含 leader），需手动 add leader。签名已由 GTNL 编译期验证。

**② 归属归一（关键）——推荐在 Manager 统一归一，一处生效**：

```java
// AdaptiveNetworkManager 内所有 owner 入口先 resolveLeader
public static AdaptiveNetwork getNetwork(UUID owner, int frequency) {
    UUID leader = AdaptiveTeamHelper.resolveLeader(owner);
    if (leader == null) return null;
    return networks.get(key(leader, frequency));
}

public static AdaptiveNetwork getOrCreateNetwork(UUID owner, int frequency) {
    UUID leader = AdaptiveTeamHelper.resolveLeader(owner);
    ...
}
// registerTerminal / registerHatch / migrateHatches 同理
```

**③ 终端归属归一**：`AdaptiveNetTerminal.onFirstTick` 改：

```java
networkOwner = AdaptiveTeamHelper.resolveLeader(aBase.getOwnerUuid());
```

**④ 传送权限**：`HatchActionPacket.handleServer` 的 `ACTION_TELEPORT` 分支（L160）：

```java
// 原：if (player.getUniqueID().equals(uuid))
if (AdaptiveTeamHelper.isMemberOf(player.getUniqueID(), uuid)) {
    handleTeleport(player, x, y, z);
}
```

> ⚠️ `resolveLeader` 每次调用 `checkOrCreateTeam` 有副效应（创建团队），与 GTNL 行为一致，可接受；若担心频繁调用，可在 Manager 内做短期缓存（如 1 秒级 Map<UUID,UUID>）。
> ⚠️ `ItemNetworkDataStick` 写入的 owner 建议也归一（可选）：写入时存 leader，读取即天然统一。
> ⚠️ 此改动影响绑定/归属语义，务必先在单人档验证不回归，再上团队场景。

---

### 2.3 快照式原子同步 + 节流

**现状**：监控数值用 ModularUI 逐字段 `LongSyncValue`/`IntSyncValue` 轮询（GUI 打开期间每 tick 同步）；`tickStats` 每 tick 调 `getUserEU`。列表用 `HatchListSyncPacket` 全量、仅 dirty 时发。

**目标**：监控数据打包成**单快照原子同步** + **内容指纹去重** + **采样节流**（参考 GTNL：快照 NBT 一次同步 + sameAs 去重 + `REFRESH_INTERVAL_TICKS=10`）。

**① 新增 `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/GridSnapshot.java`**（客户端缓存对象）：

```java
package com.wztwzt.ae2_qof.hatch.adaptive;

import java.math.BigInteger;

/** 电网监控快照（服务端构建 → 客户端原子缓存） */
public final class GridSnapshot {

    public final int version;          // 内容指纹，用于去重
    public final BigInteger gridEU;    // 当前无线池总量
    public final BigInteger totalInput;
    public final BigInteger totalOutput;
    public final long change1h;
    public final long change10m;
    public final long avgOut10m;       // 用于预计耗尽
    public final long instantIn;       // 已 ÷100
    public final long instantOut;

    public GridSnapshot(int version, BigInteger gridEU, BigInteger totalInput,
                        BigInteger totalOutput, long change1h, long change10m,
                        long avgOut10m, long instantIn, long instantOut) {
        this.version = version;
        this.gridEU = gridEU;
        this.totalInput = totalInput;
        this.totalOutput = totalOutput;
        this.change1h = change1h;
        this.change10m = change10m;
        this.avgOut10m = avgOut10m;
        this.instantIn = instantIn;
        this.instantOut = instantOut;
    }
}
```

**② 新增 `src/main/java/com/wztwzt/ae2_qof/network/GridSnapshotSyncPacket.java`**（S→C）：

- 字段与 `GridSnapshot` 一致；`BigInteger` 用 `buf.writeByteArray(v.toByteArray())` / `new BigInteger(buf.readByteArray())`。
- 解码用 `try/catch` 防御（对齐 `HatchListSyncPacket` L64 风格），异常时置空。
- Handler（`Side.CLIENT`）：`ClientState.gridSnapshot = message.build();`

```java
// 序列化要点
buf.writeInt(snap.version);
writeBI(buf, snap.gridEU);       // BigInteger 辅助方法
writeBI(buf, snap.totalInput);
writeBI(buf, snap.totalOutput);
buf.writeLong(snap.change1h);
buf.writeLong(snap.change10m);
buf.writeLong(snap.avgOut10m);
buf.writeLong(snap.instantIn);
buf.writeLong(snap.instantOut);
```

**③ `ClientState.java` 新增缓存**：

```java
public static volatile com.wztwzt.ae2_qof.hatch.adaptive.GridSnapshot gridSnapshot = null;
```

**④ 服务端节流 + 去重（`AdaptiveNetTerminal.onPreTick` L245-255 改造）**：

```java
if (aBase.isServerSide() && networkOwner != null) {
    AdaptiveNetwork network = AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency);
    if (network != null) {
        // 采样节流：每 10 tick 一次（对齐 GTNL REFRESH_INTERVAL_TICKS=10）
        if (aTick % 10 == 0) {
            java.math.BigInteger gridEU = gregtech.common.misc.WirelessNetworkManager.getUserEU(networkOwner);
            network.tickStats(gridEU);          // 不再 min(Long.MAX_VALUE)，见 2.5
            GridSnapshot snap = buildSnapshot(network, gridEU);
            int fp = fingerprint(snap);
            if (fp != lastFingerprint) {        // 内容指纹去重：无变化不推
                sendSnapshotToViewer(aBase, snap);
                lastFingerprint = fp;
            }
        }
        if (network.isHatchListDirty()) {
            network.clearHatchListDirty();
            sendHatchListSync(aBase, network);
        }
    }
}
```

- 私有辅助：`buildSnapshot`（从 network.getStats() 取值）、`fingerprint`（如 `gridEU.hashCode() ^ (int)change1h ^ ...`）、`sendSnapshotToViewer`（对齐现有 `sendHatchListSync` 的"找 owner 玩家 → `ModNetwork.CHANNEL.sendTo`"，L177-184）。
- 简单优先：每次发给 owner 玩家，客户端按 `version` 丢弃重复。**可选优化**：仅当 `player.openContainer` 为本终端 GUI 时才发（省流量）。

**⑤ GUI 改造（`buildMonitorTab` L694-787）**：把各 `TextWidget` 的取值来源从 sync 参数改为 `ClientState.gridSnapshot`：

```java
// 示例（gridEU 行，L713-718 改造）：
tab.child(new TextWidget<>(IKey.dynamic(() -> {
    com.wztwzt.ae2_qof.hatch.adaptive.GridSnapshot s =
        com.wztwzt.ae2_qof.client.ClientState.gridSnapshot;
    if (s == null) return EnumChatFormatting.GRAY + "---";
    String value = formatEU(s.gridEU, displayMode) + " EU";
    return EnumChatFormatting.AQUA + StatCollector.translateToLocal(
        "ae2_qof.gui.adaptive_terminal.monitor.grid_energy")
        + ": " + EnumChatFormatting.WHITE + value;
})).size(300, 14));
```

- 其余行（change1h/change10m/avgOut10m/instantIn/instantOut）同理从 `s` 读取。
- `buildMonitorTab` 的 `LongSyncValue`/`IntSyncValue` 参数可保留（兼容）或移除；若移除，注意调用处的参数列表同步删。

> 说明：列表仍走 `HatchListSyncPacket`（dirty 触发）不变；本次只把**监控数值**改为快照式。若需列表也原子化，可在 `GridSnapshotSyncPacket` 中加 `listVersion`，与 `sendHatchListSync` 联动（可选，P1）。

---

### 2.4 图标 / 彩色 tier / 无限滚动

**现状**：`buildHatchListTab`（L801-846）每行是纯文本（`[D]/[E]/[LS]/[LT]` 标签 + 名称 + amps/tier + EU/t）；最多渲染 50 行，超出显示 `...N more`。

**目标**：行内渲染**仓室物品图标** + **GT 彩色 tier 名** + **无限滚动**。

**① 图标（用已有 metaId 生成 ItemStack → ItemDrawable）**：

```java
import com.cleanroommc.modularui.drawable.ItemDrawable;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;

// 行内，渲染前：
net.minecraft.item.ItemStack iconStack = null;
if (entry.metaId >= 0 && entry.metaId < GregTechAPI.METATILEENTITIES.length
        && GregTechAPI.METATILEENTITIES[entry.metaId] instanceof MetaTileEntity) {
    iconStack = ((MetaTileEntity) GregTechAPI.METATILEENTITIES[entry.metaId]).getStackForm(1L);
}
ItemDrawable icon = iconStack != null
    ? new ItemDrawable(iconStack).size(16, 16)
    : com.cleanroommc.modularui.drawable.GuiTextures.BLANK;   // 兜底占位
```

- 行布局改为 `Flow.row()`：`[icon] [文本]`（文本仍用原 `IKey.dynamic(...)`）。
- `metaId == -1`（缓存失败）时用占位，不抛异常。

**② 彩色 tier**：替换 `HatchType.getTierName(entry.tier)`：

```java
import gregtech.api.util.GTUtility;
String tierName = GTUtility.getColoredTierNameFromTier((byte) entry.tier);
```

> 返回已含 `EnumChatFormatting` 颜色码的字符串，直接拼入行文本；原 `ampStr`/`eutStr` 不变。

**③ 无限滚动（二选一）**：

- **方案 1（推荐起步，改动最小）**：去掉 `MAX_HATCH_COORD_DISPLAY` 上限，`ListWidget` 全量渲染（本身可滚动），删除 L841-845 的 `...N more` 分支。列表通常几十条，性能可接受。
- **方案 2（对齐 GTNL load more）**：初始渲染 N（如 20）行，监听滚动到底追加。参考 GTNL `EnergyMonitorGui.MonitoringListWidget` 的 `visibleRowCount` 增量逻辑；ModularUI 需自建 Widget 监听 `VerticalScrollData` 的进度，成本较高，量级大再上。

---

### 2.5 BigInteger 精度

**现状**：`GridEnergyStats` 全程 `long`；`AdaptiveNetTerminal.onPreTick` L248 `gridEU.min(Long.MAX_VALUE).longValue()` 截断。无线池长期挂机可超 `Long.MAX_VALUE`（≈9.2e18），截断后监控失真。

**目标**：总量/当前值用 `BigInteger`，显示不截断（对齐 GTNL 全程 BigInteger）。

**① `GridEnergyStats.java` 改造**：

```java
private java.math.BigInteger totalInput = java.math.BigInteger.ZERO;
private java.math.BigInteger totalOutput = java.math.BigInteger.ZERO;
private java.math.BigInteger lastEU = java.math.BigInteger.ZERO;
private java.math.BigInteger gridEU_10min_ago = java.math.BigInteger.ZERO;
private java.math.BigInteger gridEU_1h_ago = java.math.BigInteger.ZERO;

public void tick(java.math.BigInteger currentEU) {
    if (!initialized) {
        lastEU = currentEU;
        gridEU_10min_ago = currentEU;
        gridEU_1h_ago = currentEU;
        initialized = true;
        return;
    }
    java.math.BigInteger delta = currentEU.subtract(lastEU);
    if (delta.signum() > 0) {
        totalInput = totalInput.add(delta);
        long d = delta.longValue();          // 单窗口增量远小于 long，安全进缓冲
        bufferSumInput -= inputBuffer[bufferIndex];
        inputBuffer[bufferIndex] = d;
        bufferSumInput += d;
        bufferSumOutput -= outputBuffer[bufferIndex];
        outputBuffer[bufferIndex] = 0;
    } else if (delta.signum() < 0) {
        totalOutput = totalOutput.add(delta.negate());
        long d = delta.negate().longValue();
        bufferSumOutput -= outputBuffer[bufferIndex];
        outputBuffer[bufferIndex] = d;
        bufferSumOutput += d;
        bufferSumInput -= inputBuffer[bufferIndex];
        inputBuffer[bufferIndex] = 0;
    } else {
        bufferSumInput -= inputBuffer[bufferIndex];
        inputBuffer[bufferIndex] = 0;
        bufferSumOutput -= outputBuffer[bufferIndex];
        outputBuffer[bufferIndex] = 0;
    }
    lastEU = currentEU;
    bufferIndex = (bufferIndex + 1) % 100;
    snapshotTick++;
    if (snapshotTick % WINDOW_10M == 0) gridEU_10min_ago = currentEU;
    if (snapshotTick % WINDOW_1H == 0) gridEU_1h_ago = currentEU;
}

// 变化量（窗口差值）转 long 供 GUI / packet：窗口内差远小于 long，安全
public long getChange1h(java.math.BigInteger currentEU) {
    if (!initialized) return 0;
    return currentEU.subtract(gridEU_1h_ago).longValue();
}
public long getChange10min(java.math.BigInteger currentEU) { ...同理... }

public java.math.BigInteger getTotalInput()  { return totalInput; }
public java.math.BigInteger getTotalOutput() { return totalOutput; }
```

- `saveNBT`/`loadNBT`：BigInteger 用 byte[]：

```java
nbt.setByteArray("gridTotalIn", totalInput.toByteArray());
// load: totalInput = new java.math.BigInteger(nbt.getByteArray("gridTotalIn"));
```

- `getInstantInputRate/OutputRate` 仍返回 `long`（L88-94 已按 1.2 修，内部 `bufferSum / 100`）。

**② 终端调用点**（`AdaptiveNetTerminal.onPreTick` L248-249）：

```java
java.math.BigInteger gridEU = gregtech.common.misc.WirelessNetworkManager.getUserEU(networkOwner);
network.tickStats(gridEU);   // 不再 min(Long.MAX_VALUE)
```

**③ 显示层**：`GridSnapshot` 的 `gridEU`/`totalInput`/`totalOutput` 已是 BigInteger（见 2.3）。GUI 增加 BigInteger 格式化：

```java
import gregtech.api.util.GTUtility;

private static String formatEU(java.math.BigInteger eu, int mode) {
    if (eu.signum() < 0) return "-" + formatEU(eu.negate(), mode);
    if (mode == 1) return GTUtility.scientificFormat(eu);   // 科学计数
    // mode 0 / 2：KMG 手写 BigInteger 除
    final String[] units = { "K", "M", "G", "T", "P", "E" };
    java.math.BigInteger div = java.math.BigInteger.valueOf(1000);
    int i = -1;
    java.math.BigInteger tmp = eu;
    while (tmp.compareTo(div) >= 0 && i < units.length - 1) {
        tmp = tmp.divide(div);
        i++;
    }
    return i < 0 ? eu.toString() : tmp + units[i];
}
```

- 四个仓室的私有 `formatEU(long)`（`AdaptiveNetHatch`/`Dynamo`/`Laser`/`LaserTarget` L214-222）也需补一个 BigInteger 重载，用于显示 `getUserEU` 完整值（去掉 `.min(...).longValue()` 截断）。

---

### 2.6 AE2 原生高亮

**现状**：高亮走自定义 `WirelessHighlightRenderer`（Tessellator 线框）+ `ClientState.adaptiveHighlightPositions`，由 `HatchActionPacket`（L172-176）发 `WirelessHighlightPacket`、100 tick 后 `scheduleClear` 发空列表清除。

**目标**：改用 AE2 `BlockPosHighlighter`（方块描边 + 悬浮文字，更专业、跨维稳定）。

**方案 A（推荐，改动小）：客户端接入点替换，服务端逻辑不动**

`WirelessHighlightPacket.Handler` → `MyMod.proxy.handleWirelessHighlight(message)`（`ClientProxy` 实现）改为：

```java
// ClientProxy.handleWirelessHighlight 内：
public void handleWirelessHighlight(com.wztwzt.ae2_qof.network.WirelessHighlightPacket message) {
    try {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        java.util.List<appeng.api.util.DimensionalCoord> coords = new java.util.ArrayList<>();
        for (int[] p : message.positions) {
            coords.add(new appeng.api.util.DimensionalCoord(mc.theWorld, p[1], p[2], p[3]));
        }
        appeng.client.render.highlighter.BlockPosHighlighter.highlightBlocks(
            mc.thePlayer, coords, "ae2qol_adaptive_net_highlight",
            "Adaptive Net", message.enable ? "" : "");
    } catch (Throwable t) {
        // 防御：AE2 不可用时静默降级，不影响其他功能
    }
}
```

- 空列表（`scheduleClear` 发的 `enable=false` 空包）会覆盖为无高亮 = 清除，**保留现有 100 tick 清除机制**，服务端 `HatchActionPacket` 无需改动。
- 停用自定义渲染：取消 `WirelessHighlightRenderer` 的注册/订阅，`ClientState.adaptiveHighlightPositions`/`adaptiveHighlightExpiryTick` 不再写入（可保留字段兼容）。

> ⚠️ 动手前先读 `ClientProxy`，确认 `handleWirelessHighlight` 是否还服务其他功能（如 `ClientState.highlightPositions`/`highlightEnabled` 对应的另一套高亮）。若共用，请拆一个新 packet 或按用途分流，勿影响既有高亮。
> ⚠️ `BlockPosHighlighter.highlightBlocks` 第 3 参是唯一 id（同 id 覆盖），第 4/5 参是悬浮文字；悬浮文字换行可用 `\n`（AE2 内部按行拆分，需自行验证）。

---

## 三、实施顺序建议

| 步骤 | 内容 | 依赖 | 风险 |
|---|---|---|---|
| 1 | 1.1 平均速率单位 / 1.2 瞬时速率 / 1.4 列表刷新 | 无 | 低 |
| 2 | 2.1 真实负载 EU/t（helper + 仓室 + HatchEntry + packet + GUI） | 无 | 低 |
| 3 | 2.5 BigInteger（stats + 终端 + 显示） | 步骤 1 | 中（改动面广，需全编译） |
| 4 | 1.3 WorldData 接线 | 步骤 3（saveNBT 兼容 byte[]） | 中 |
| 5 | 2.3 快照式同步（新 packet + ClientState + GUI 取值切换） | 步骤 3/4 | 中 |
| 6 | 2.4 图标/彩色tier/无限滚动（GUI） | 步骤 2 | 低 |
| 7 | 2.6 AE2 高亮（客户端接入点） | 无 | 中（需读 ClientProxy 分流） |
| 8 | 2.2 团队聚合（归属归一，影响绑定语义） | 无 | **高，最后做，先单人回归** |

建议 1-4 作为一轮"正确性修复"合并提交；5-8 各自独立提交，方便回滚。

---

## 四、验证清单

- [ ] **编译**：`gradlew compileJava`（或 `build`）通过，重点检查 `GridEnergyStats` BigInteger 改型后所有调用点。
- [ ] **1.1/1.2 数值**：稳定负载下，1h 行、瞬时行的 EU/t 量级与预期一致（不再 20×/100×）。
- [ ] **1.3 持久化**：重启服务器后，totalIn/Out 与 10min/1h 快照不再清零。
- [ ] **1.4 列表刷新**：设置 Tab 换仓室物品后，子仓 Tab 的 tier/amps/EU-t 立即更新。
- [ ] **2.1 真实负载**：机器停机时实际显示 0 或低值；运行时接近额定但能反映真实流量；V/A 仍显示。
- [ ] **2.2 团队**：团队 A/B 玩家打开同一电网，B 能见 A 的仓室；非成员无法传送。
- [ ] **2.3 快照**：GUI 打开数值正常刷新；无变化时无多余网络包（可用日志验证）；数值原子一致（gridEU 与变化量同帧）。
- [ ] **2.4 图标/tier**：行内渲染出对应仓室物品图标；tier 名带 GT 颜色；滚动到底不再有 `...N more` 截断。
- [ ] **2.5 BigInteger**：构造超大无线池（测试指令塞 BigInteger）后，显示为科学计数/KMG，不截断失真。
- [ ] **2.6 高亮**：左键子仓行，AE2 方块描边 + 悬浮名出现；约 100 tick 后消失；传送仍正常。

---

## 五、参考文件索引

| 文件 | 用途 |
|---|---|
| `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetTerminal.java` | 终端：GUI、onPreTick、sendHatchListSync（改动主战场） |
| `.../AdaptiveNetwork.java` | 网络：电压/安培、dirty、WorldData 接线 |
| `.../GridEnergyStats.java` | 统计：瞬时/变化/总量（BigInteger + 单位修复） |
| `.../GridEnergyWorldData.java` | 已实现待接线的 WorldSavedData |
| `.../AdaptiveHatchHelper.java` | 仓室状态：新增 realFlowEUt |
| `.../AdaptiveNet{Hatch,DynamoHatch,LaserHatch,LaserTargetHatch}.java` | 仓室：真实流量采样点 |
| `.../HatchListCache.java`、`.../network/HatchListSyncPacket.java` | 列表数据 + 序列化（加 realEUt） |
| `.../network/HatchActionPacket.java` | 高亮/传送服务端处理（传送权限改团队） |
| `.../network/WirelessHighlightPacket.java` + `ClientProxy` | 高亮客户端接入（AE2 替换） |
| `.../client/ClientState.java` | 客户端缓存（新增 gridSnapshot） |
| `reference_src/GT-Not-Leisure-dev-290/.../monitor/*` | GTNL 参考：Collector/Formatter/Gui/WirelessTeam |
