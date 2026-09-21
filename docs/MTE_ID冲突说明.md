# MTE ID 冲突说明（已解决：AE2-QoL 退让号段）

> 状态：**已解决**（fix48）。AE2-QoL 主动让出被占用的号段，并附带旧存档自动迁移。
> 适用基线：GTNH **2.9.0-beta-3**（本文件与 `docs/dumps/metatileentity.csv` 均基于 b3 导出）。

## 冲突双方
- **AE2-QoL**（本 mod）
- **fissionevolved** 0.1.1（私货 mod，`com.shenfnx.fissionevolved`）

## 冲突详情

两个 mod 使用了相同的 MetaTileEntity ID，导致 AE2-QoL 的两个终端注册失败。
fissionevolved 的默认值硬编码在其 `Config.java` 中（`fissionReactorControllerId = 32100`、
`colossalFissionReactorControllerId = 32101`），并非偶然占用。

| MTE ID | AE2-QoL 原注册目标 | fissionevolved 占用者 |
|--------|--------------------|------------------------|
| 32100 | AdaptiveNetTerminal（自适应电网终端） | `fissionevolved.fission_reactor_controller` |
| 32101 | StockMonitorTerminal（库存统计终端） | `fissionevolved.colossal_fission_reactor_controller` |

## 日志输出
```
[Client thread/INFO] [STDERR/ae2_qof]: [AE2QoL] StockMonitorTerminal registration FAILED: java.lang.IllegalArgumentException: MetaTileEntity id 32101 is already occupied! Existing MTE is fissionevolved.colossal_fission_reactor_controller(com.shenfnx.fissionevolved.MTEColossalFissionReactor).
[Client thread/INFO] [STDERR/ae2_qof]: [AE2QoL] AdaptiveNetTerminal registration FAILED: java.lang.IllegalArgumentException: MetaTileEntity id 32100 is already occupied! Existing MTE is fissionevolved.fission_reactor_controller(com.shenfnx.fissionevolved.MTEFissionReactor).
```

## 处理方式：AE2-QoL 主动退让

保留 fissionevolved，AE2-QoL 改用经 **290b3 全表核对为空闲**的相邻号段：

| 用途 | 旧 ID | 新 ID | 依据 |
|------|-------|-------|------|
| 自适应电网终端 AdaptiveNetTerminal | 32100 | **32106** | b3 `metatileentity.csv` 中 32106 未被任何 mod 占用 |
| 库存统计终端 StockMonitorTerminal | 32101 | **32107** | 同上，32107 亦空闲 |

选 32106/32107 而非更远处号段的理由：与既有 32102/32103/32104/32105（自适应四仓）、
32110/32111（无线电网两终端）连成 32102～32111 一整块，便于后续维护；
同时避开 GTNL 源码中写死的 `32301~32331`、`32350~32377` 检查区间。

## 旧存档自动迁移（fix48 关键部分）

GT 存档只记录 MTE 的**数字 ID**（NBT 键 `mID`），不记录类名。若直接换号，
旧存档中已摆放的终端会被当成 fissionevolved 的机器加载，其配置
（网络频率 `ae2qolNF`、电压等级 `ae2qolVT`、四个子仓配对表 `ae2qolHT*/ae2qolHA*`）
将在下次保存时丢失——表现为「终端消失、频率丢失、全基地子仓解绑」。

因此 fix48 增加了存档读取期自动迁移：

- 注入点：`BaseMetaTileEntity.setInitialValuesAsNBT(NBTTagCompound, short)` 的 `HEAD`；
- 判定条件：NBT 中 `mID` 为旧的 32100/32101，**且**携带本模组专属键
  （`ae2qolNO` / `ae2qolNF` / `ae2qolVT` 之一）；
- 动作：把 `mID` 改写为新号（32106/32107）后交回原逻辑，配置数据原样保留。

fissionevolved 写的 NBT 键为 `Fission*` 前缀，与本模组的 `ae2qol*` 前缀互不相交，
因此**不会误迁移它的机器**；其他模组若占用这两个号同样不受影响。

## 影响面核实（基于实际存档逐块解析）

| 存档 | 32100 旧终端 | 32101 旧终端 | 终端**物品** |
|------|--------------|--------------|--------------|
| b3 `World` | 1 台（主世界 49,29,44） | 0 | 无 |
| b3 `新的世界` | 0 | 0 | 无 |
| b1 `world(1)` | 1 台 | 0 | 无 |

结论：迁移面为极少数已摆放终端；两个存档中均**不存在**携带旧 ID 的终端物品，
故无需处理物品栏/箱子/AE 存储中的残留。

## 不改动项

32000（万能维护仓）、32102～32105（自适应四仓）、32110/32111（无线电网两终端）
在 290b3 中注册正常，本次一律不动。
