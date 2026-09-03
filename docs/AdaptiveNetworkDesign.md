# 自适配电网系统 (Adaptive Network) 完整设计文档

## 一、系统总览

自适配电网系统是 AE2-QoL 模组的核心功能之一，允许玩家将分散在基地各处的 GT 仓室（能源仓、动力仓、激光源仓、激光靶仓）统一纳入一个逻辑网络中管理。网络通过一个 **终端方块 (AdaptiveNetTerminal)** 作为主控节点，配合 **网络数据闪存 (ItemNetworkDataStick)** 实现仓室与终端的绑定。所有仓室共享同一个 GT 无线 EU 池 (`gregtech.common.misc.WirelessNetworkManager`)。

### 1.1 核心目标

- **一键切换全基地电压等级**：在终端 GUI 中设置目标电压，所有已绑定仓室自动同步
- **电网能量监控**：实时显示 GT 无线电网 EU 总量、10分钟/1小时变化量、平均消耗速率、预计耗尽时间
- **子仓列表监控**：列出网络中所有已绑定仓室，支持坐标显示、高亮、传送
- **闪存配置转移**：通过闪存将终端配置（owner+frequency）复制到另一终端或绑定到仓室

### 1.2 架构概览

```
┌──────────────────────────────────────────────────────────────┐
│                    AdaptiveNetworkManager                     │
│            static Map<"UUID:freq", AdaptiveNetwork>          │
├──────────────────────────────────────────────────────────────┤
│  AdaptiveNetwork(owner, frequency)                           │
│  ├─ terminal: AdaptiveNetTerminal (唯一)                     │
│  ├─ helpers: Set<AdaptiveHatchHelper> (已绑定仓室)           │
│  ├─ stats: GridEnergyStats (环形缓冲+快照)                   │
│  ├─ voltageTier / hatchTiers[] / hatchAmps[]                 │
│  └─ hatchListDirty: boolean                                  │
├──────────────────────────────────────────────────────────────┤
│  Hatch 单元:                                                 │
│  ├─ AdaptiveNetHatch (能源仓, ENERGY)                        │
│  ├─ AdaptiveNetDynamoHatch (动力仓, DYNAMO)                  │
│  ├─ AdaptiveNetLaserHatch (激光源仓, LASER_SOURCE)           │
│  └─ AdaptiveNetLaserTargetHatch (激光靶仓, LASER_TARGET)     │
│  每个 hatch 内含 AdaptiveHatchHelper                         │
├──────────────────────────────────────────────────────────────┤
│  GT 无线 EU 池 (gregtech.common.misc.WirelessNetworkManager)  │
│  ├─ addEUToGlobalEnergyMap(owner, BigInteger delta)           │
│  └─ getUserEU(owner) → BigInteger                            │
└──────────────────────────────────────────────────────────────┘
```

---

## 二、ID 分配 (32100-32199)

| ID | 类名 | 说明 |
|----|------|------|
| 32100 | AdaptiveNetTerminal | 自适应电网终端（主控） |
| 32102 | AdaptiveNetHatch | 能源仓 (ENERGY) |
| 32103 | AdaptiveNetLaserHatch | 激光源仓 (LASER_SOURCE) |
| 32104 | AdaptiveNetDynamoHatch | 动力仓 (DYNAMO) |
| 32105 | AdaptiveNetLaserTargetHatch | 激光靶仓 (LASER_TARGET) |

---

## 三、HatchType 枚举

```java
public enum HatchType {
    DYNAMO(0, 1, 1),        // slotIndex=0, defaultAmps=1, defaultTier=1(LV)
    ENERGY(1, 1, 1),        // slotIndex=1, defaultAmps=1, defaultTier=1(LV)
    LASER_SOURCE(2, 256, 6),// slotIndex=2, defaultAmps=256, defaultTier=6(LuV)
    LASER_TARGET(3, 256, 6); // slotIndex=3, defaultAmps=256, defaultTier=6(LuV)
}
```

- `VOLTAGE_NAMES[]`: ULV, LV, MV, HV, EV, IV, LuV, ZPM, UV, UHV, UEV, UIV, UMV, UXV, MAX (共15级)
- `isValidMTEType(MetaTileEntity)`: 验证放入终端槽位的 MTE 物品是否匹配仓室类型
  - DYNAMO: `maxEUOutput() > 0`
  - ENERGY: `maxEUInput() > 0`
  - LASER_SOURCE: `maxEUInput() > 0`
  - LASER_TARGET: `maxEUOutput() > 0`

---

## 四、AdaptiveNetworkManager (全局管理器)

**文件**: `AdaptiveNetworkManager.java` (101行)

### 4.1 数据结构

```java
private static final Map<String, AdaptiveNetwork> networks = new HashMap<>();
```

- Key: `UUID.toString() + ":" + frequency` (如 `"550e8400-e29b-41d4-a716-446655440000:42"`)
- Value: `AdaptiveNetwork` 实例

### 4.2 核心 API

| 方法 | 说明 |
|------|------|
| `getOrCreateNetwork(owner, freq)` | 查找或创建网络 |
| `getNetwork(owner, freq)` | 查找网络（不创建） |
| `removeNetwork(owner, freq)` | 移除并 destroy 网络 |
| `registerTerminal(terminal)` | 将终端绑定到网络（仅当网络无终端时） |
| `unregisterTerminal(terminal)` | 解绑终端，若网络为空则删除 |
| `registerHatch(helper)` | 将仓室绑定到网络 |
| `unregisterHatch(helper)` | 解绑仓室，若网络为空则删除 |
| `updateAllHatches(owner, freq)` | 通知所有仓室同步电压 |
| `migrateHatches(oldOwner, oldFreq, newOwner, newFreq)` | 迁移仓室到新频率 |

### 4.3 网络生命周期

```
放置 Terminal → onFirstTick → registerTerminal → getOrCreateNetwork
放置 Hatch    → onFirstTick → registerHatch    → getOrCreateNetwork → addHelper
                                                      ↓
                                    网络开始收集 stats，同步电压到所有 helper
                                                      ↓
破坏 Terminal → onRemoval → unregisterTerminal → network.isEmpty() → removeNetwork → destroy()
破坏 Hatch    → onRemoval → unregisterHatch    → network.isEmpty() → removeNetwork → destroy()
```

---

## 五、AdaptiveNetTerminal (终端主控)

**文件**: `AdaptiveNetTerminal.java` (1025行)

### 5.1 类继承

```
MTEHatch → AbstractMTETileEntity → ... → IGregTechTileEntity
```

### 5.2 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `networkOwner` | UUID | 网络所有者 |
| `networkFrequency` | int | 网络频率 |
| `autoReconnect` | boolean | 自动重连开关 |
| `displayMode` | int | 显示模式 0=标准, 1=科学记数, 2=KMG |
| `currentVoltageTier` | int | 目标电压等级 |
| `hatchTiers[4]` | int[] | 各类型仓室的电压等级 |
| `hatchAmps[4]` | int[] | 各类型仓室的安培数 |

### 5.3 NBT 持久化

```
ae2qolNO: UUID string (owner)
ae2qolNF: int (frequency)
ae2qolVT: int (voltageTier)
ae2qolAR: boolean (autoReconnect)
ae2qolHT0-3: int (hatchTiers per slot)
ae2qolHA0-3: int (hatchAmps per slot)
```

### 5.4 Tick 逻辑 (`onPreTick`)

```
每20 tick (1秒):
  └─ updateParamsFromSlots(): 扫描4个库存槽位，验证MTE类型，提取tier/amps，更新网络

每次 tick (终端存在时):
  ├─ network.tickStats(gridEU): 更新电网统计
  ├─ if hatchListDirty → sendHatchListSync(): 发送子仓列表同步包
  └─ HatchActionPacket.tickPendingClears(): 清理过期高亮
```

### 5.5 GUI 架构 (5个Tab)

使用 ModularUI2 的 `PagedWidget` + `PagedWidget.Controller` + `PageButton`:

| Tab | 索引 | 方法 | 内容 |
|-----|------|------|------|
| 状态 | 0 | `buildStatusTab()` | Owner UUID、频率、各仓室 tier/amps/loaded 数量 |
| 设置 | 1 | `buildSettingsTab()` | 4个物品槽位，放入64个同类型仓室物品配置 |
| 频率 | 2 | `buildFrequencyTab()` | 频率输入框 (IntSyncValue) |
| 监控 | 3 | `buildMonitorTab()` | 电网能量监控（详见第六节） |
| 子仓 | 4 | `buildHatchListTab()` | 子仓列表（详见第七节） |

### 5.6 SyncValue 注册

```java
syncManager.syncValue("wFr", frequencySync);    // 频率
syncManager.syncValue("wVT", voltageTierSync);   // 电压等级
syncManager.syncValue("wTI", totalInputSync);    // 累计输入
syncManager.syncValue("wTO", totalOutputSync);   // 累计输出
syncManager.syncValue("wII", instantInputSync);  // 瞬时输入
syncManager.syncValue("wIO", instantOutputSync); // 瞬时输出
syncManager.syncValue("wGE", gridEUSync);        // 电网EU总量
syncManager.syncValue("wC1", change1hSync);      // 1h变化量
syncManager.syncValue("wCM", change10mSync);     // 10min变化量
syncManager.syncValue("wAO", avgOut10mSync);     // 10min平均消耗
syncManager.syncValue("wHT0-3", hatchTierSyncs);// 各仓室tier
syncManager.syncValue("wHA0-3", hatchAmpSyncs);  // 各仓室amps
syncManager.syncValue("wHC0-3", hatchCountSyncs);// 各仓室loaded数量
```

### 5.7 闪存交互

```
右键持有 ItemNetworkDataStick:
  ├─ 有数据 → 读取 owner+freq 到终端，迁移仓室，重新注册
  └─ 无数据 → 写入终端 owner+freq 到闪存
```

---

## 六、电网监控 (Grid Energy Monitor) — Tab 3 "监"

### 6.1 数据源

电网能量数据来自 GT 无线 EU 池:
```java
BigInteger gridEU = WirelessNetworkManager.getUserEU(networkOwner);
```

所有已绑定仓室通过各自的 `onPreTick`/`onPostTick` 向此池充放电:
- **Energy Hatch**: 从池中取电 → `addEUToGlobalEnergyMap(owner, -consumed)`
- **Dynamo Hatch**: 向池中充电 → `addEUToGlobalEnergyMap(owner, +stored)`
- **Laser Source**: 从池中取电 (×2倍)
- **Laser Target**: 向池中充电 (×2倍)

### 6.2 GridEnergyStats (环形缓冲统计)

**文件**: `GridEnergyStats.java` (153行)

#### 核心数据结构

```java
// 累计总量
private long totalInput;    // 累计输入 EU
private long totalOutput;   // 累计输出 EU

// 瞬时速率 (环形缓冲, 100 tick = 5秒窗口)
private final long[] inputBuffer = new long[100];   // 每tick的输入量
private final long[] outputBuffer = new long[100];  // 每tick的输出量
private int bufferIndex;                            // 当前写入位置
private long bufferSumInput;                        // 缓冲区输入总和
private long bufferSumOutput;                       // 缓冲区输出总和

// 快照窗口
private static final int WINDOW_10M = 12000;  // 10分钟 = 12000 tick
private static final int WINDOW_1H = 72000;   // 1小时 = 72000 tick
private long gridEU_10min_ago;  // 10分钟前的电网EU快照
private long gridEU_1h_ago;     // 1小时前的电网EU快照
private long snapshotTick;      // 快照计数器

private boolean initialized;   // 首次tick标记
private long lastEU;           // 上一tick的电网EU值
```

#### Tick 逻辑

```java
public void tick(long currentEU) {
    if (!initialized) {
        lastEU = currentEU;
        gridEU_10min_ago = currentEU;
        gridEU_1h_ago = currentEU;
        initialized = true;
        return;
    }
    long delta = currentEU - lastEU;
    if (delta > 0) {
        totalInput += delta;
        bufferSumInput -= inputBuffer[bufferIndex];
        inputBuffer[bufferIndex] = delta;
        bufferSumInput += delta;
        // outputBuffer 清零
    } else if (delta < 0) {
        totalOutput += -delta;
        bufferSumOutput -= outputBuffer[bufferIndex];
        outputBuffer[bufferIndex] = -delta;
        bufferSumOutput += -delta;
        // inputBuffer 清零
    } else {
        // 无变化，清零当前位置
    }
    lastEU = currentEU;
    bufferIndex = (bufferIndex + 1) % 100;

    snapshotTick++;
    if (snapshotTick % WINDOW_10M == 0) gridEU_10min_ago = currentEU;
    if (snapshotTick % WINDOW_1H == 0)  gridEU_1h_ago = currentEU;
}
```

**设计要点**:
- 环形缓冲区大小 100，每次 tick 覆盖最老数据，实现 O(1) 滑动窗口
- `bufferSumInput/Output` 是整个缓冲区（5秒）的累计值，即 **瞬时速率 EU/t**
- 快照每 12000 tick (10分钟) / 72000 tick (1小时) 记录一次电网 EU 值

#### 计算方法

| 指标 | 公式 | 说明 |
|------|------|------|
| 瞬时输入速率 | `bufferSumInput` | 最近5秒内每tick平均输入 |
| 瞬时输出速率 | `bufferSumOutput` | 最近5秒内每tick平均输出 |
| 1h变化量 | `currentEU - gridEU_1h_ago` | 正=充入，负=消耗 |
| 10min变化量 | `currentEU - gridEU_10min_ago` | 同上 |
| 10min平均消耗 | `-change10m / 12000` | EU/t (仅负值有效) |
| 1h平均消耗 | `-change1h / 72000` | EU/t (仅负值有效) |
| 预计耗尽时间 | `gridEU / avgOutputRate10min` (ticks) | 除以20得秒，除以3600得小时 |

**重要**: 预计耗尽时间公式为 `gridEU / avgOut`，其中 `avgOut` 已经是 EU/tick，所以直接得到 tick 数，**不需要 ×20**。

#### NBT 持久化

```
gridTotalIn: long (累计输入)
gridTotalOut: long (累计输出)
gridEU10m: long (10分钟前快照)
gridEU1h: long (1小时前快照)
snapTick: long (快照计数器)
```

### 6.3 GridEnergyWorldData (世界存档)

**文件**: `GridEnergyWorldData.java` (64行)

- 继承 `WorldSavedData`
- 存储 `HashMap<String, GridEnergyStats>` (key 为 `"UUID:frequency"`)
- 通过 `world.mapStorage.loadData/setData` 管理
- 数据名: `"ae2qol_grid_energy"`

### 6.4 Monitor Tab UI (buildMonitorTab)

GUI 布局 (330×260):

```
┌─ 电网能量监控 ──── [标准/科学/KMG] ─┐
│                                      │
│  电网能量: 1,234,567,890 EU          │
│  1h变化: -456,789,012 EU (126.9 EU/t (1.0A EV))│
│  10min变化: -78,901 EU (131.5 EU/t (1.0A EV))  │
│  预计耗尽: 2y 3月 5d 12h 30m         │
│                                      │
│  瞬时输入: 0 EU/t                    │
│  瞬时输出: 131 EU/t (1.0A EV)       │
│                                      │
│  活动: +0 EU/t / -131 EU/t          │
└──────────────────────────────────────┘
```

#### 三种显示模式

| 模式 | 格式 | 示例 |
|------|------|------|
| 标准 (0) | 千分位分隔 | `1,234,567,890` |
| 科学记数 (1) | `m.nn×10^n` | `1.23e9` |
| KMG (2) | 后缀缩写 | `1.23G` |

切换按钮: `ButtonWidget.onMousePressed` → `displayMode = (displayMode + 1) % 3`

#### formatAmpTier 方法

将 EU/t 速率转换为 "(X.XA TIER)" 格式:
```java
private static String formatAmpTier(long euPerTick) {
    for (int tier = 14; tier >= 0; tier--) {
        if (euPerTick >= V[tier]) {
            double amps = (double) euPerTick / V[tier];
            return String.format("(%.1fA %s)", amps, HatchType.getTierName(tier));
        }
    }
    return "";
}
```

#### formatDuration 方法

将 tick 数转换为可读时间:
```java
// 分级: 年 → 月 → 天 → 时 → 分 → 秒
long years = seconds / 31536000L;
long months = (seconds % 31536000L) / 2592000L;
long days = (seconds % 2592000L) / 86400L;
long hours = (seconds % 86400L) / 3600L;
long minutes = (seconds % 3600L) / 60L;
long seconds = seconds % 60L;
```

---

## 七、子仓列表监控 (Hatch List) — Tab 4 "列"

### 7.1 数据流程

```
Server 侧:                                    Client 侧:
                                             
Terminal.onPreTick                            HatchListSyncPacket
  │ (每20 tick, 或 hatchListDirty)              │
  ▼                                             ▼
sendHatchListSync()                            ClientProxy.handleHatchListSync()
  │                                              │
  ├─ stale entry pruning (世界检查)              ├─ ClientState.hatchListCache = buildCache()
  ├─ 遍历 helpers                                │
  │   ├─ 检测 TileEntity 存活                     ▼
  │   └─ 构建 HatchEntry 列表                   HatchListCache (sorted by EU/t desc)
  └─ 发送 HatchListSyncPacket to player          │
                                                 └─ buildHatchListTab() 渲染列表
```

### 7.2 sendHatchListSync 详细逻辑

```java
private void sendHatchListSync(IGregTechTileEntity aBase, AdaptiveNetwork network) {
    List<AdaptiveHatchHelper> helpers = network.getAllHelpers();
    
    // 1. Stale entry pruning (过期清理)
    List<AdaptiveHatchHelper> stale = new ArrayList<>();
    for (AdaptiveHatchHelper h : helpers) {
        WorldServer ws = MinecraftServer.getServer().worldServerForDimension(h.getDim());
        if (ws == null) { stale.add(h); continue; }
        TileEntity te = ws.getTileEntity(h.getX(), h.getY(), h.getZ());
        if (te == null) stale.add(h);
    }
    for (AdaptiveHatchHelper s : stale) {
        AdaptiveNetworkManager.unregisterHatch(s);
    }
    if (!stale.isEmpty()) helpers = network.getAllHelpers();
    
    // 2. 构建 HatchEntry 列表
    List<HatchEntry> entries = new ArrayList<>();
    int globalIndex = 0, totalIn = 0, totalOut = 0;
    for (AdaptiveHatchHelper h : helpers) {
        int tier = h.getCurrentVoltageTier();
        int amps = h.getCurrentAmps();
        HatchType ht = h.getHatchType();
        int eut = 0;
        
        if (ht == HatchType.ENERGY)        { eut = V[tier] * amps; totalIn++; }
        else if (ht == HatchType.LASER_SOURCE) { eut = V[tier] * 2 * amps; totalIn++; }
        else if (ht == HatchType.DYNAMO)    { eut = V[tier] * amps; totalOut++; }
        else if (ht == HatchType.LASER_TARGET) { eut = V[tier] * 2 * amps; totalOut++; }
        
        entries.add(new HatchEntry(h.getCachedName(), h.getCachedMetaId(), 
            eut, tier, amps, ht.ordinal(), globalIndex,
            h.getX(), h.getY(), h.getZ(), h.getDim()));
        globalIndex++;
    }
    
    // 3. 发送到 owner 玩家
    HatchListSyncPacket packet = new HatchListSyncPacket(
        networkOwner, networkFrequency, helpers.size(), entries, totalIn, totalOut);
    // 查找在线的 owner 玩家
    EntityPlayerMP player = findOwnerPlayer(aBase.getWorld(), networkOwner);
    if (player != null) ModNetwork.CHANNEL.sendTo(packet, player);
}
```

### 7.3 过期清理机制 (Stale Entry Pruning)

**问题**: 仓室被破坏后 `onRemoval()` 应调用 `unregisterHatch()`，但某些情况（如世界卸载再加载、mod bug）可能导致 helper 残留。

**解决方案**: 在每次 `sendHatchListSync` 时，遍历所有 helper，检查对应的 TileEntity 是否仍存在于世界中:
```java
WorldServer ws = MinecraftServer.getServer().worldServerForDimension(h.getDim());
TileEntity te = ws.getTileEntity(h.getX(), h.getY(), h.getZ());
if (te == null) stale.add(h);  // TileEntity 已消失
```

**注意**: 此操作会遍历世界区块，但只在 `hatchListDirty=true` 时执行，且网络中仓室数量通常有限 (<100)，性能影响可控。

### 7.4 HatchListCache (客户端缓存)

**文件**: `HatchListCache.java` (50行)

```java
public class HatchListCache {
    public final int totalCount;
    public final List<HatchEntry> entries;  // sorted by EU/t desc
    public final String inputCountText;
    public final String outputCountText;
    
    public static class HatchEntry {
        public final String name;      // 仓室显示名
        public final short metaId;     // GT meta ID
        public final int eut;          // EU/t (V[tier] * amps 或 V[tier] * 2 * amps)
        public final int tier;         // 电压等级
        public final int amps;         // 安培数
        public final int hatchType;    // 0=DYNAMO, 1=ENERGY, 2=LASER_SOURCE, 3=LASER_TARGET
        public final int index;        // 全局索引 (用于 action packet)
        public final int x, y, z, dim; // 世界坐标
    }
}
```

- 排序: `EU_T_DESC` — 按 EU/t 降序排列
- `Math.max(1, amps)` — 防止 amps=0 导致的除零

### 7.5 HatchListSyncPacket (网络协议)

**文件**: `HatchListSyncPacket.java`

#### 编码格式 (Server → Client)

```
boolean hasOwner
  └─ if true: UUID (long most + long least)
int frequency
int totalCount
int inputCount
int outputCount
short count (entries)
  └─ per entry:
      short index
      short metaId
      UTF8String name
      int eut
      byte tier
      short amps          ← 注意: 已从 byte 改为 short (防止256溢出)
      byte hatchType
      int x
      int y
      int z
      short dim
```

**关键修改历史**:
- 最初 `amps` 用 `writeByte`/`readUnsignedByte` (0-255)，但 LASER 类仓室 amps 可达 256+，导致溢出
- 已改为 `writeShort`/`readUnsignedShort` (0-65535)

### 7.6 HatchList Tab UI (buildHatchListTab)

```
┌─ 子仓列表 ──────────────────────────────┐
│ 总数: 48                                 │
│                                          │
│ [D] DynamoHatch LuV (4.0A LuV) 1.6GV/t  │
│ [E] EnergyHatch IV (2.0A IV) 2.0GV/t    │
│ [LS] LaserSourceHatch ZPM (6.0A ZPM) 5.0GV/t │
│ ...                                      │
│                                          │
│ 输入仓: 24  输出仓: 24                    │
└──────────────────────────────────────────┘
```

#### 每行按钮逻辑

```java
list.child(new ButtonWidget<>()
    .child(new TextWidget<>(IKey.dynamic(() -> {
        // 动态生成: [类型标签] 名称 (X.XA Tier) Y EU/t
    })))
    .tooltipBuilder(t -> {
        t.addLine("[x, y, z] dim:N");
        t.addLine("Left Click: Highlight | Shift+Click: Teleport");
    })
    .onMousePressed((event) -> {
        boolean shift = keyBindSneak.getIsKeyPressed();
        int action = shift ? ACTION_TELEPORT : ACTION_HIGHLIGHT;
        ModNetwork.CHANNEL.sendToServer(new HatchActionPacket(
            action, networkOwner.toString(), networkFrequency, entry.index));
        return true;
    }));
```

### 7.7 HatchActionPacket (交互协议)

**文件**: `HatchActionPacket.java`

#### 两种操作

| 操作 | 值 | 说明 |
|------|----|------|
| ACTION_HIGHLIGHT | 0 | 高亮显示仓室位置 (5秒) |
| ACTION_TELEPORT | 1 | 传送玩家到仓室位置 |

#### 编码格式 (Client → Server)

```
int action
boolean hasOwner
  └─ if true: UTF8String (owner UUID)
int frequency
int index (hatch index in network)
```

#### Server 处理逻辑

```java
// 1. 验证玩家打开的 GUI 属于该网络
// 2. 查找 AdaptiveNetwork
// 3. 通过 index 获取 helper
// 4. 检查 TileEntity 存在性
// 5. 执行操作:
//    HIGHLIGHT: 发送 WirelessHighlightPacket, 100 tick 后清除
//    TELEPORT: player.setPositionAndUpdate(x+0.5, y+1, z+0.5)
//              仅 owner 玩家可传送
```

#### 高亮清除机制

```java
private static final List<long[]> pendingClears = ...;
// [entityId, clearTick]

public static void scheduleClear(EntityPlayerMP player, int clearTick) {
    pendingClears.add(new long[]{ player.getEntityId(), clearTick });
}

// Terminal.onPreTick 中调用:
HatchActionPacket.tickPendingClears();
// 到期后发送 WirelessHighlightPacket(positions, false) 清除高亮
```

---

## 八、仓室实现

### 8.1 AdaptiveNetHatch (能源仓) — 273行

**继承**: `MTEHatchEnergy`

**核心逻辑** (`onPreTick`, 每4 tick):
```java
// 1. 从网络同步电压/安培
helper.setVoltageTier(network.getHatchTiers()[ht.slotIndex]);
helper.setAmps(network.getHatchAmps()[ht.slotIndex]);

// 2. 计算消耗量 (本地缓冲 EU 变化)
long consumed = lastStoredEU - currentStored;
if (consumed > 0) {
    WirelessNetworkManager.addEUToGlobalEnergyMap(owner, -consumed);
}

// 3. 从电网补电到本地缓冲
if (currentStored < halfStore) {
    long target = min(halfStore, currentStored + gridEU.longValue());
    aBase.increaseStoredEnergyUnits(target - currentStored, false);
}
```

**关键参数**:
- `maxEUInput()`: `V[helper.getCurrentVoltageTier()]` (动态)
- `maxAmperesIn()`: `helper.getCurrentAmps()` (动态)
- `maxEUStore()`: `Long.MAX_VALUE / 2`

### 8.2 AdaptiveNetDynamoHatch (动力仓) — 255行

**继承**: `MTEHatchDynamo`

**核心逻辑** (`transferEU`, 每4 tick):
```java
long stored = aBase.getUniversalEnergyStored();
if (stored <= 0 || !helper.isBound()) return;
aBase.decreaseStoredEnergyUnits(stored, false);
WirelessNetworkManager.addEUToGlobalEnergyMap(owner, BigInteger.valueOf(stored));
```

**关键参数**:
- `maxEUOutput()`: `Long.MAX_VALUE / 2` (不限制，由amps控制)
- `maxAmperesOut()`: `helper.getCurrentAmps()` (动态)
- `maxEUStore()`: `Long.MAX_VALUE / 2`

### 8.3 AdaptiveNetLaserHatch / AdaptiveNetLaserTargetHatch

与 Energy/Dynamo 类似，但 EU/t 计算使用 `V[tier] * 2 * amps`（激光仓2倍系数）。

### 8.4 共同的 onFirstTick 逻辑

```java
helper.setPosition(x, y, z, dim);
MetaTileEntity mte = aBase.getMetaTileEntity();
ItemStack stack = mte.getStackForm(1L);
if (stack != null) {
    helper.setCachedInfo((short) stack.getItemDamage(), stack.getDisplayName());
} else {
    // getStackForm 返回 null (非标准仓室)
    helper.setCachedInfo((short) -1, 
        StatCollector.translateToLocal(helper.getHatchType().getTranslationKey()));
}
if (helper.isBound()) {
    AdaptiveNetworkManager.registerHatch(helper);
}
```

### 8.5 共同的 onRemoval 逻辑

```java
@Override
public void onRemoval() {
    if (helper.isBound()) {
        AdaptiveNetworkManager.unregisterHatch(helper);
    }
    super.onRemoval();
}
```

---

## 九、AdaptiveHatchHelper (仓室辅助类)

**文件**: `AdaptiveHatchHelper.java` (189行)

### 9.1 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `networkOwner` | UUID | 网络所有者 |
| `networkFrequency` | int | 网络频率 |
| `currentVoltageTier` | int | 当前电压等级 |
| `currentAmps` | int | 当前安培数 (≥1) |
| `hatchType` | HatchType | 仓室类型枚举 |
| `posX/Y/Z` | int | 世界坐标 |
| `posDim` | int | 维度 ID |
| `cachedMetaId` | short | GT meta ID 缓存 |
| `cachedName` | String | 显示名缓存 |

### 9.2 核心方法

| 方法 | 说明 |
|------|------|
| `bind(owner, freq)` | 绑定到网络 (先解绑旧的) |
| `unbind()` | 解绑并清零 |
| `setVoltageTier(tier)` | clamp 0-15 |
| `setAmps(amps)` | clamp ≥1 |
| `migrateTo(newOwner, newFreq)` | 迁移到新 owner+freq |
| `handleDataStickRightClick(player)` | 处理闪存右键 |

### 9.3 NBT 持久化

```
ae2qolNO: UUID string
ae2qolNF: int (frequency)
ae2qolVT: int (voltageTier)
ae2qolPX/Y/Z: int (position)
ae2qolPD: int (dimension)
ae2qolMI: short (metaId)
ae2qolMN: String (cachedName)
```

---

## 十、网络包 (Network Packets)

**文件**: `ModNetwork.java`

| 包名 | 方向 | discriminator | 用途 |
|------|------|---------------|------|
| HatchActionPacket | C→S | 26 | 高亮/传送仓室 |
| HatchListSyncPacket | S→C | 27 | 同步子仓列表 |

### 10.1 注册顺序

```java
CHANNEL.registerMessage(HatchActionPacket.Handler.class, HatchActionPacket.class, discriminator++, Side.SERVER);
CHANNEL.registerMessage(HatchListSyncPacket.Handler.class, HatchListSyncPacket.class, discriminator++, Side.CLIENT);
```

### 10.2 Client 处理

```java
// ClientProxy.java
@Override
public void handleHatchListSync(final HatchListSyncPacket message) {
    Minecraft.getMinecraft().func_152344_a(() -> {
        ClientState.hatchListCache = message.buildCache();
    });
}
```

### 10.3 ClientState

```java
public static volatile HatchListCache hatchListCache = null;
```

---

## 十一、参考材料详细说明

### 11.1 GTNL Energy Monitor (GT-Not-Leisure)

**来源**: `E:\wzt\MC\modcreater\reference_src\GT-Not-Leisure-dev-290`

这是 GTNL (GT-Not-Leisure) mod 中的能量监控器实现，**本系统大量参考了其架构设计**:

#### 参考了什么

1. **EnergyMonitorRegistry** (ConcurrentHashMap set + 自动注册 via Mixin)
   - 本系统改用 `AdaptiveNetworkManager` (HashMap<"UUID:freq", Network>)，因为需要按 owner+freq 分组
   - GTNL 是全局注册所有 MTE，本系统只追踪已绑定的仓室

2. **EnergyMonitorRowSnapshot** (单行数据快照)
   - 本系统的 `HatchListCache.HatchEntry` 参考了其结构
   - 去掉了 `iconStack`、`ownerName`、`wireless` 等不需要的字段
   - 增加了 `x, y, z, dim` 用于高亮/传送

3. **EnergyMonitorSummarySnapshot** (汇总数据)
   - 本系统的 `GridEnergyStats` 参考了其思路，但数据结构完全不同
   - GTNL 用 BigInteger，本系统用 long (因为 GT 无线 EU 池精度足够)
   - GTNL 有 wiredStored/wiredCapacity/wirelessStored，本系统只有 wireless

4. **EnergyMonitorFormatter** (格式化工具)
   - `formatBigInteger()` → 本系统的 `formatRegular()` (千分位)
   - `getVoltageTier(BigInteger)` → 本系统的 `formatAmpTier()` (EU/t → Amp Tier)
   - `formatAmps(BigInteger, int)` → 类似思路
   - `formatDuration(BigInteger)` → 本系统的 `formatDuration(long)` (tick → 可读时间)

5. **EnergyMonitorMode** (WIRED/WIRELESS/ALL)
   - 本系统的 `displayMode` (标准/科学/KMG) 参考了其三模式切换思路
   - 但切换的是数字格式，而非数据来源

6. **EnergyMonitorCategory** (BASIC_MACHINE/MULTIBLOCK/HATCH/COVER)
   - 本系统的 `HatchType` (DYNAMO/ENERGY/LASER_SOURCE/LASER_TARGET) 参考了分类思路
   - 但更简化，只有4种仓室类型

7. **Mixin 自动注册/注销**
   - GTNL 通过 `MixinBaseMetaTileEntity.invalidate/onUnload` 自动 unregister
   - 本系统不使用 Mixin，而是通过 `onRemoval()` 回调

#### 没有参考什么

- GTNL 的 `WirelessTeam` (SpaceProjectManager 团队系统) — 本系统用 UUID 直接绑定
- GTNL 的 `EnergyMonitor` 机器方块 (MTEBasicTank 子类) — 本系统用 AdaptiveNetTerminal (MTEHatch 子类)
- GTNL 的复杂多方块 EU/t 解析 (GrandAssemblyLine, AssemblerMatrix 等) — 本系统只需仓室级数据
- GTNL 的 BigInteger 能量计算 — 本系统用 long (GT 无线 EU 池用 BigInteger，但显示时截断到 long)

### 11.2 GTSimpleWirelessNetwork (截图参考)

**来源**: 用户提供的截图 (GTSimpleWirelessNetwork GUI)

这是 GT 原版的简单无线网络实现，**本系统参考了其用户界面设计**:

#### 参考了什么

1. **3种显示模式**: 模式1 (4A ZPM 格式) / 模式2 / 模式3
   - 本系统的 `displayMode` (标准/科学/KMG) 参考了多模式切换思路
   - `formatAmpTier()` 的 "(X.XA TIER)" 格式直接参考了截图中的 "(4A ZPM)" 格式

2. **GUI 布局**: 标签页 + 内容区的侧边 Tab 设计
   - 本系统使用 `PagedWidget` + `PageButton` 实现

3. **仓室列表**: 每行显示仓室名 + tier + amps + EU/t
   - 本系统 `buildHatchListTab()` 的每行格式参考了此设计

#### 没有参考什么

- GTSimpleWirelessNetwork 的 `EnergyMonitor` 方块本身
- 其网络同步协议 (本系统用自定义 packet)
- 其团队系统

### 11.3 ModularUI2 (UI 框架)

**版本**: 2.3.73-1.7.10 (本地 jar: `libs/modularui2-2.3.73-1.7.10.jar`)

#### 使用的组件

| 组件 | 用途 |
|------|------|
| `ModularPanel` | 主面板 |
| `PagedWidget` + `Controller` | Tab 页面切换 |
| `PageButton` | Tab 按钮 (不支持子组件) |
| `Flow.row()` / `Flow.column()` | 线性布局 |
| `TextWidget` | 文本显示 (IKey.dynamic 动态更新) |
| `ButtonWidget` | 可点击按钮 (hatch list 每行) |
| `ListWidget` | 可滚动列表 |
| `ItemSlot` + `ModularSlot` | 物品槽位 (settings tab) |
| `TextFieldWidget` | 文本输入 (frequency tab) |
| `LongSyncValue` / `IntSyncValue` | Server↔Client 数据同步 |
| `IKey.str()` / `IKey.lang()` / `IKey.dynamic()` | 文本提供者 |
| `GuiTextures.TAB_LEFT` | Tab 纹理 |

#### API 限制

- `PageButton` 不支持 `child()` / `addChildren()` — 无法在 Tab 按钮上添加文字标签
- `ListWidget` 有自己的滚动机制 — 不能嵌套在 `ScrollWidget` 中
- `TextWidget` 没有 `onMousePressed` — 需要用 `ButtonWidget` 包装
- `ByteBuf` 没有 `readUTF`/`writeUTF` — 需要用 `ByteBufUtils.readUTF8String()`

### 11.4 GregTech WirelessNetworkManager

**API** (GT 2.9.0-beta-1):

```java
// 充电 (delta 为正)
WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUUID, BigInteger.valueOf(eu));

// 放电 (delta 为负)
WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUUID, BigInteger.valueOf(-eu));

// 查询
BigInteger gridEU = WirelessNetworkManager.getUserEU(ownerUUID);
```

**注意**: `addEUToGlobalEnergyMap` 接受 `BigInteger delta`，正值增加、负值减少。`getUserEU` 返回 `BigInteger`。

### 11.5 GT Voltage Tiers

```java
GTValues.V[0]  = 8          // ULV
GTValues.V[1]  = 32         // LV
GTValues.V[2]  = 128        // MV
GTValues.V[3]  = 512        // HV
GTValues.V[4]  = 2048       // EV
GTValues.V[5]  = 8192       // IV
GTValues.V[6]  = 32768      // LuV
GTValues.V[7]  = 131072     // ZPM
GTValues.V[8]  = 524288     // UV
GTValues.V[9]  = 2097152    // UHV
GTValues.V[10] = 8388608    // UEV
GTValues.V[11] = 33554432   // UIV
GTValues.V[12] = 134217728  // UMV
GTValues.V[13] = 536870912  // UXV
GTValues.V[14] = 2147483647 // MAX (Integer.MAX_VALUE)
```

---

## 十二、已修复的 Bug 清单

| # | 问题 | 根因 | 修复 |
|---|------|------|------|
| 1 | 电网监控 WINDOW 常量错误 | `WINDOW_10M=600` (30秒), `WINDOW_1H=3600` (3分钟) | 改为 `12000` (10分钟), `72000` (1小时) |
| 2 | getChange1h/10min 返回0 | 未初始化时快照=0，导致差值=当前值（巨大数字） | 添加 `if (!initialized) return 0` 守卫 |
| 3 | 预计耗尽时间×20 | avgOut 已是 EU/tick，gridEU/avgOut 直接=ticks | 移除 `* 20L` |
| 4 | Flow.row() 内按钮无响应 | Flow.row() 拦截鼠标事件 | 改为单个 ButtonWidget |
| 5 | 列表底部被遮挡 | ListWidget 超出面板 | 移除 `coverChildren()`，高度 190→175 |
| 6 | getStackForm() 返回null崩溃 | 非标准MTE无StackForm | 添加 null 检查，fallback用翻译key |
| 7 | amps=256 溢出 | `writeByte` (0-255) | 改为 `writeShort` (0-65535) |
| 8 | DYNAMO/LT 无EU/t | sendHatchListSync 只计算ENERGY/LS | 添加DYNAMO/LASER_TARGET计算 |
| 9 | 仓室破坏后残留 | DynamoHatch/LaserTargetHatch 缺少 onRemoval | 两个类都添加 `onRemoval() → unregisterHatch()` |
| 10 | 月份显示为"mo" | formatDuration 用英文 | 改为 `"月 "` |
| 11 | setHatchAmps(0) | `Math.max(0, amps)` 允许0 | 改为 `Math.max(1, amps)` |

---

## 十三、文件清单

### 核心文件

| 文件 | 行数 | 职责 |
|------|------|------|
| `AdaptiveNetTerminal.java` | 1025 | 终端主控 + GUI + 闪存交互 + 子仓同步 |
| `GridEnergyStats.java` | 153 | 环形缓冲 + 快照 + NBT |
| `GridEnergyWorldData.java` | 64 | 世界存档持久化 |
| `AdaptiveHatchHelper.java` | 189 | 仓室辅助类 (位置/缓存/绑定/NBT) |
| `AdaptiveNetwork.java` | 167 | 网络数据模型 (helpers/stats/voltage) |
| `AdaptiveNetworkManager.java` | 101 | 全局网络管理器 (HashMap) |
| `HatchType.java` | 55 | 仓室类型枚举 + 电压名 |
| `HatchListCache.java` | 50 | 客户端子仓列表缓存 |

### 仓室实现

| 文件 | 行数 | 继承 |
|------|------|------|
| `AdaptiveNetHatch.java` | 273 | MTEHatchEnergy |
| `AdaptiveNetLaserHatch.java` | 273 | MTEHatchLaser |
| `AdaptiveNetDynamoHatch.java` | 255 | MTEHatchDynamo |
| `AdaptiveNetLaserTargetHatch.java` | 255 | MTEHatchDynamo (激光靶) |

### 网络包

| 文件 | 方向 | 用途 |
|------|------|------|
| `HatchListSyncPacket.java` | S→C | 子仓列表同步 |
| `HatchActionPacket.java` | C→S | 高亮/传送操作 |
| `ModNetwork.java` | — | 包注册 |

### 客户端

| 文件 | 用途 |
|------|------|
| `ClientProxy.java` | handleHatchListSync() |
| `ClientState.java` | hatchListCache 字段 |

### 语言文件

| 文件 | 更新内容 |
|------|----------|
| `lang/zh_CN.lang` | 全部自适应电网相关翻译 |
| `lang/en_US.lang` | 全部自适应电网相关翻译 |
