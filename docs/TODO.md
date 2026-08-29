# 待实施项目

> 本文档记录已调研完毕、等待排期实施的特性。
> 每条目实施时按仓库规范独立 commit（代码 + docs + CHANGELOG + README + 版本号同 commit）。

---

## 实施顺序与状态

1. ~~展示条定位 hotfix — v3.10.1~~ ✅ 已实施（2026-08-24）
2. ~~ME 任务检测器 — v3.11.0~~ ✅ 已实施（2026-08-24）
3. ~~吞并 AEInfinityCell — v3.12.0~~ ✅ 已实施（2026-08-24）
4. ~~无限磁盘 tooltip 统计增强 — v3.13.0~~ ✅ 已实施（2026-08-24）
5. ~~展示条重做为原生 pin 置顶行 + 科学计数法修复 + GuideNH 指南修复 — v3.14.0~~ ✅ 已实施（2026-08-24）
6. ~~通知横幅对齐 AE2 原生样式（含耗时）+ pin 开关修复与总开关 — v3.15.0~~ ✅ 已实施（2026-08-25，待游戏内验证）
7. ~~二合一终端接入 pin 置顶行（自定义布局，3.16.0）~~ ❌ 已废弃（用户决策：不需要）
8. ~~万能维护仓（多功能仓室）— v3.16.0~~ ✅ 已实施（2026-08-28）
9. [待实施] 智能配方仓（配方类型过滤样板缓冲）— v3.17.0
10. [待实施] 产线聚合器（统一多方块产线处理）— v3.18.0

---

## [待实施] 展示条定位 hotfix — v3.10.1

### 问题
RecentCraftedOverlay 产物展示条画在网格中间而非第一行。
根因：`locateRow()` 遍历 `inventorySlots` 找最小 yDisplayPosition 行，但 GTNH rv3 终端网络物品网格是
`VirtualMEMonitorableSlot` 虚拟槽（不在 inventorySlots），定位到了错误行。

### 方案（公式已从源码核实：GuiMEMonitorable.java:396-398）
- 第一行物品格坐标：`x = guiLeft + offsetRepoX`；`y = guiTop + offsetRepoY + pinsRows*18`
- `pinsRows = rows - monitorableSlots.length / perRow`
- MixinGuiMEMonitorable 新增 duck 接口（@Shadow offsetRepoX/offsetRepoY/perRow/rows/monitorableSlots），
  RecentCraftedOverlay.locateRow() 优先走 duck 接口取值，非标准终端保留现有兜底。

---

## [待实施] ME 任务检测器（BetterQuesting 联动）— 目标版本 3.11.0

### 需求来源
仿 ME_Quests_Detector (MC 1.20.1) 与 AE2UEL-GTQT (MC 1.12.2)，为 GTNH 提供「ME 网络物品自动完成 BQ 任务」能力。

### 已确认决策
- 形态：独立方块设备「ME 任务检测器」（不做合成完成联动）；
- 任务范围：仅检索型任务（consume=false）；消耗型明确跳过（官方 `TaskRetrieval.retrieveItems` 开头自带 consume 守卫）；
- 绑定：放置者 UUID（破坏重放重绑）；`ParticipantInfo` 自动兼容 BQ party 队伍共享进度；
- 附加要求（不可落下）：WAILA/JADE 显示正常、方块/物品 tooltip、Guide-NH 条目。

### 技术要点（已调研验证，源码 reference_src\BetterQuesting-3.8.70-GTNH，bq_standard 已合并核心）
- 检测入口：`IItemTask.retrieveItems(pInfo, quest, ItemStack[])` / `IFluidTask.retrieveFluids(...)`（GTNH fork 特供只读钩子）；
- 官方先例：`TileObservationStation.updateEntity()`（20tick 扫描 → `QUEST_DB.filterKeys(pInfo.getSharedQuests())` → 逐任务调用）；
- API 入口：`QuestingAPI.getAPI(ApiReference.QUEST_DB)`；
- 网络库存：`grid.getCache(IStorageGrid.class).getItemInventory().getStorageList()`；流体走 ae2fc（已是 implementation 依赖）；
- 注册模式：照 `BlockExIOPort` 先例 extends AE2 `BlockIOPort`/`TileIOPort` 白嫖 grid proxy 与电源管理，`CommonProxy` 注册；
- 性能：任务需求缓存（约 10s 重建，矿辞 OreDictionary 展开），对候选键 findFuzzy 收集实际存在物品后再调 retrieveItems，
  避免大网络全量直传；
- 门控：`@TileEvent(TICK)` 内 totalWorldTime % 20；断电/断 channel（node.isActive()）/BQ 编辑模式跳过；
- 依赖坐标：`com.github.GTNewHorizons:BetterQuesting:3.8.70-GTNH:dev`（nexus 已确认存在，与测试环境 jar 一致）；
- 安全：BQ 未安装时功能静默停用（modid `betterquesting` 守卫 + 检测逻辑独立类隔离，#74 教训）。

### 文件清单（预估）
dependencies.gradle(+1 行)、CommonProxy(注册)、block/BlockQuestDetector、tile/TileQuestDetector、
client/render/RenderBlockQuestDetector、common/quest/QuestDetectLogic(核心逻辑)、
assets(blockstate/model/texture/lang zh+en)、config(开关+扫描间隔)、WAILA/JADE provider、Guide-NH entry、
docs 四件套(CHANGELOG/docs/CHANGELOG/README 双语)。

---

## [待实施] 吞并 AEInfinityCell — 目标版本 3.12.0

### 需求来源与结论
用户希望直接吞并 aeinfinitycell-1.0.4（dancing snow 作，MIT 许可），全部功能纳入本模组。
可行性已确认：主源码 ~2000 行、资产仅 1 贴图+2 lang、无方块无 GUI、无自带配方。MIT 允许自由合并。

### 已确认决策
- **命名策略**：modid `aeinfinitycell`、包名 `cn.dancingsnow.aeinfinitycell`、存储路径全部不变 →
  卸旧装新后背包磁盘与世界存档数据零迁移无缝接管；
- **jar 形态**：双 @Mod 入口同 jar（`ae2_qof` + `aeinfinitycell`），mcmod.info 双条目；
  与原 mod 严格互斥（同 modid 共存必触发 DuplicateModsFoundException）；
- **AppEU 剔除**：用户环境无 appeu，其 2 个集成类不搬，CommonProxy 反射加载点改 warn+静默跳过，日后需要再补；
- 保留 ThaumicEnergistics 源质通道（环境在用 TC4+TE）、Avaritia halo 渲染接口依赖（IHaloRenderItem）。

### 搬运清单
| 内容 | 说明 |
|---|---|
| src/main/java/cn/dancingsnow/aeinfinitycell | 全部 ~23 类原样搬入（排除 integration/appeu/） |
| 物品 Infinity Storage Cell | UUID NBT 引用，内容存世界存档 per-cell 文件，复制共享同一库存 |
| AE2 ICellHandler 注册 | 物品/流体/Essentia 三通道 |
| TileDriveMixin（GTNH LateMixin） | drive 多通道 handler 全挂载，mixins.aeinfinitycell(.late).json 一并搬运注册 |
| 存储后端 | InfinityCellStorage per-cell 文件 IO + LegacySavedData 迁移 + WorldSave 持久化钩子合并进我们生命周期 |
| NEI 分页查看器 | InfinityCellViewHandler/Preview/Config 四通道分页 |
| 资产 | 贴图 + zh/en lang |
| Config | synchronizeConfiguration 流程 |

### 依赖调整（dependencies.gradle）
+ `com.github.GTNewHorizons:ThaumicEnergistics:<nexus 版本>:dev`（对应环境 thaumicenergistics-1.7.53-GTNH）
+ Avaritia / EternalSingularity dev 坐标（nexus 核对版本）
GregTech compileOnly 已有；appeu 不引入。

### 法律合规动作（MIT 义务）
CREDITS.md 注明无限磁盘部分源自 dancing snow/AE2InfinityCell 及来源仓库；jar 内随附原 MIT 许可文本副本。

### 验证清单（老存档实测）
卸旧 jar → 装新 jar → 备份存档后进档：
1. 背包/drive 中磁盘完好（注册名映射零迁移）；
2. 磁盘内容无损读取（per-cell 文件原样解析）；
3. drive 多通道挂载正常（TileDriveMixin 生效，物品+流体+源质同盘可见）；
4. 复制磁盘共享同一后端库存；
5. NEI U 键四通道分页预览正常；
6. 世界保存/重启后数据完好；
7. 双 mod 在专用服正常加载（#74 回归检查）。

---

## [待实施] 吞并后：无限磁盘 tooltip 统计增强

> 吞并完成后实施——`ItemInfinityStorageCell.addInformation` 已是自家代码，无需再 mixin 外部 mod。

### 问题回顾
数据仍在服务端世界存档（InfinityCellDataAccess 客户端返回 null），客户端 tooltip 拿不到统计。

### 方案
- C2S/S2C 同步包对：悬停查缓存（key=storageId，TTL 2s）未命中→请求→服务端读 record 汇总→回包渲染；
- 数量序列化用数字字符串防 BigInteger 溢出；
- 渲染布局（顺序固定）：总计行(`∞ Bytes | 共 N 类/M 件 ≈N KB`) → 物品行 → 流体行 → （神秘/EU 行）→ 灰字引导；
- 大数格式化 BigNumFormatter：内部全程 BigInteger；默认字母单位链 K→M→B→T→Qa→Qi…（如 12.34M）；
  **按住 Ctrl** 切科学计数法（如 1.23×10⁷）；字节估算跟每行末尾（折算公式照抄 AE2 CellInventoryHandler，流体/EU 公式实施时核对）；
- NEI U 键页专用服修复仍不在范围（二期）。

---

## [待实施] 万能维护仓（多功能仓室）— 目标版本 3.16.0

### 需求来源
仿 GT-Shanhai 的"终焉聚合枢纽"，为 GTNH 提供「维护绕过+无限能源+并行控制」的多功能仓室。
在 GTNH 2.9.0-beta-1 / MC1.7.10 环境中重新设计实现。

### 已确认决策
- **命名策略**：物品名 `AE2MaintenanceHatchUniversal`，内部ID `ae2_qof.hatch.maintenance.universal`，显示名"万能维护仓"
- **形态**：GT仓室形态，继承`MTEHatchMaintenance`
- **模块系统**：使用GT电路板（LV到MAX共15级）作为模块，决定并行数
- **维护绕过**：Mixin注入`checkMaintenance`和`causeMaintenanceIssue`，永远无问题
- **能源**：自定义`NotifiableEnergyContainer`子类，可配置电压等级（0-15级）
- **命名风格**：功能描述风格，部分自创（保留GT电路板等已有物品名）

### 技术要点（已调研验证，源码 reference_src\GT5-Unofficial-master）
- **维护绕过**：Mixin `MTEMultiBlockBase.checkMaintenance()` → 检测到万能维护仓时直接`fixAllIssues()`
- **GT维护系统**：GT5U维护仓是工具标志位的临时缓冲区，`checkMaintenance()`从维护仓拷贝到主机
- **电路板映射**：物品槽存储GT电路板，通过`ItemDamage`或NBT读取等级，并行数=4^等级
- **能源容器**：继承`NotifiableEnergyContainer`，重写`getEnergyCapacity()`返回`Long.MAX_VALUE`
- **电压等级**：配置项0-15级，对应GT的`GTValues.V[energyTier]`

### 电路板→并行数映射表

| 电路板 | 等级 | 最大并行 | EU/t |
|--------|------|----------|------|
| LV电路板 | 1 | 4 | 32 |
| MV电路板 | 2 | 16 | 128 |
| HV电路板 | 3 | 64 | 512 |
| EV电路板 | 4 | 256 | 2,048 |
| IV电路板 | 5 | 1,024 | 8,192 |
| LuV电路板 | 6 | 4,096 | 32,768 |
| ZPM电路板 | 7 | 16,384 | 131,072 |
| UV电路板 | 8 | 65,536 | 524,288 |
| UHV电路板 | 9 | 262,144 | 2,097,152 |
| UEV电路板 | 10 | 1,048,576 | 8,388,608 |
| UIV电路板 | 11 | 4,194,304 | 33,554,432 |
| UMV电路板 | 12 | 16,777,216 | 134,217,728 |
| UXV电路板 | 13 | 67,108,864 | 536,870,912 |
| OpV电路板 | 14 | 268,435,456 | 2,147,483,648 |
| MAX电路板 | 15 | Long.MAX_VALUE | Long.MAX_VALUE |

### 文件清单（预估）
| 文件 | 功能 | 行数估算 |
|------|------|----------|
| `AE2MaintenanceHatchUniversal.java` | 主仓室类，继承`MTEHatchMaintenance` | ~300行 |
| `MixinMTEMultiBlockBase.java` | 注入`checkMaintenance`和`causeMaintenanceIssue` | ~50行 |
| `ModItems.java` | 注册物品 | ~10行 |
| assets(texture/lang) | 贴图和语言文件（zh+en） | ~20行 |

### 验证清单
1. 编译通过（`./gradlew build`）
2. 游戏内加载，万能维护仓物品可见
3. 放入GT多方块结构的维护仓位置
4. 测试维护绕过：机器永不产生维护问题
5. 测试并行控制：放入不同等级电路板，并行数正确变化
6. 测试能源：无限能源，可配置电压等级

---

## [待实施] 智能配方仓（配方类型过滤样板缓冲）— 目标版本 3.17.0

### 需求来源
仿 GT-Shanhai 的"星律样板供料系统"，为 GTNH 提供「配方类型过滤+虚拟电路+非消耗输入+卡死检测」的智能样板缓冲仓室。
在 GTNH 2.9.0-beta-1 / MC1.7.10 环境中重新设计实现。

### 已确认决策
- **命名策略**：物品名 `AE2PatternBufferHatchSmart`，内部ID `ae2_qof.hatch.pattern_buffer.smart`，显示名"智能配方仓"
- **形态**：GT仓室形态，实现`ICraftingMedium`接口
- **配方类型过滤**：精确`RecipeMap`匹配，每个槽位只允许特定配方类型
- **虚拟电路**：完整复刻，支持非消耗电路配置，无需物理物品即可获得正确电路配置
- **非消耗输入**：支持催化剂/模具，不需要物理物品
- **卡死检测**：槽位级计时，超时后通知玩家（默认5分钟）
- **命名风格**：功能描述风格，部分自创（保留GT电路板等已有物品名）

### 技术要点（已调研验证，源码 reference_src\Applied-Energistics-2-Unofficial-rv3-beta-997-GTNH）
- **ICraftingMedium实现**：`pushPattern()`方法接收AE2样板，验证配方类型后推送到GT机器
- **配方类型过滤**：每个槽位存储允许的`RecipeMap`列表，通过物品NBT或配置指定
- **虚拟电路缓存**：WeakReference模式，电路配置绑定到槽位，执行时自动应用
- **非消耗输入**：参考`MTEHatchNonConsumableBase`模式，实现`IMEMonitor`接口
- **卡死检测**：每个槽位独立计时器，`pushPattern`成功时重置，超时后警告
- **AE2 API差异**：rv3-beta-997-GTNH的合成规划逻辑与GT-Shanhai使用的高版本AE2有差异，需适配

### 虚拟电路完整生命周期
1. **绑定阶段**：玩家右击虚拟电路槽 → 选择电路物品 → 绑定到槽位 → 缓存电路配置
2. **编码阶段**：样板编码器读取虚拟电路配置 → 写入样板NBT → 存储到智能配方仓
3. **执行阶段**：AE2合成CPU推送样板 → 智能配方仓接收 → 从缓存获取电路配置 → 设置到GT机器 → 推送消费性输入
4. **回收阶段**：合成完成 → 电路配置保留在缓存 → 下次执行时复用

### 槽位结构

| 槽位 | 功能 | 数量 |
|------|------|------|
| 样板槽 | 存放AE2样板 | 4个 |
| 配方类型过滤槽 | 存放`RecipeMap`对应的物品，决定允许的配方类型 | 4个 |
| 虚拟电路槽 | 绑定电路配置，执行时自动应用 | 4个 |
| 非消耗输入槽 | 存放催化剂/模具 | 4个 |

### 文件清单（预估）
| 文件 | 功能 | 行数估算 |
|------|------|----------|
| `AE2PatternBufferHatchSmart.java` | 主仓室类，实现`ICraftingMedium` | ~600行 |
| `PatternBufferSlotData.java` | 槽位数据存储（配方类型、物品、计时器） | ~150行 |
| `PatternBufferStuckDetector.java` | 卡死检测和通知 | ~150行 |
| `VirtualCircuitCache.java` | 虚拟电路缓存（WeakReference模式） | ~100行 |
| `MixinCraftingCPUCluster.java` | 扩展现有倍增逻辑，支持非消耗输入 | ~150行 |
| `ModItems.java` | 注册物品 | ~10行 |
| assets(texture/lang) | 贴图和语言文件（zh+en） | ~20行 |

### 验证清单
1. 编译通过（`./gradlew build`）
2. 游戏内加载，智能配方仓物品可见
3. 放入GT多方块结构的仓室位置
4. 测试配方类型过滤：仅允许指定配方类型的样板
5. 测试虚拟电路：电路配置绑定和执行时自动应用
6. 测试非消耗输入：催化剂/模具不需要物理物品
7. 测试卡死检测：超时后向附近玩家发送警告
8. 测试与万能维护仓的联动

---

## [待实施] 产线聚合器（统一多方块产线处理）— 目标版本 3.18.0

### 需求来源
仿GTL的一步产线系统，为GTNH提供「将复杂多步骤产线合并为单一方块处理」的能力。
在 GTNH 2.9.0-beta-1 / MC1.7.10 环境中重新设计实现。

### 已确认决策
- **命名策略**：物品名 `MTEProductionLineAggregator`，内部ID `ae2_qof.machine.production_line_aggregator`，显示名"产线聚合器"
- **形态**：GT多方块机器，继承`MTEMultiBlockBase`
- **可扩展性**：最小3x3x3，可扩展到更大结构
- **配方数据源**：JSON数据包（支持GTNH Calculator工具导出格式）
- **电压门槛**：机器消耗系统 + 电压等级限制

### 电压门槛机制（新设计）
1. **机器消耗系统**：
   - 新GUI页面显示产线所需所有机器
   - 玩家必须在对应槽位插入所有机器
   - 点击"激活配方"后机器被消耗
   - 消耗后配方永久解锁（受电压限制）

2. **双重限制**：
   - 机器消耗：必须提供产线所有机器
   - 电压等级：机器电压等级必须≥配方要求

### 可扩展多方块结构

#### 最小结构（3x3x3）
```
     [P]
   [C][H][C]
[P][H][M][H][P]
   [C][H][C]
     [P]

P = 生产线控制器仓
C = 配方仓（存储JSON配方）
H = 输入/输出仓（总线/仓室）
M = 主机方块
```

#### 扩展结构（5x5x5）
```
      [P][P][P]
    [C][C][H][C][C]
[P][C][C][H][C][C][P]
[P][H][H][M][H][H][P]
[P][C][C][H][C][C][P]
    [C][C][H][C][C]
      [P][P][P]
```

**扩展规则**：
- 每层扩展增加4个配方仓
- 配方仓数量决定可存储的配方数量
- 并行数=配方仓数量×2

### JSON配方格式（兼容GTNH Calculator导出）

```json
{
  "type": "ae2_qof:production_line",
  "id": "petrochemical_light_fuel",
  "name": "石油工业/轻燃油",
  "category": "petrochemical",
  "description": "将原油转化为轻燃油的完整产线",
  "minVoltageTier": 1,
  "requiredMachines": [
    {
      "id": "gtceu:distillation_tower",
      "name": "蒸馏塔",
      "count": 1,
      "consumed": true
    },
    {
      "id": "gtceu:chemical_reactor",
      "name": "化学反应釜",
      "count": 2,
      "consumed": true
    },
    {
      "id": "gtceu:cracker",
      "name": "裂解机",
      "count": 1,
      "consumed": true
    }
  ],
  "steps": [
    {
      "name": "原油蒸馏",
      "machine": "gtceu:distillation_tower",
      "inputs": {
        "items": [],
        "fluids": [
          {"fluid": "gtceu:oil", "amount": 1000},
          {"fluid": "gtceu:steam", "amount": 1000}
        ]
      },
      "outputs": {
        "items": [],
        "fluids": [
          {"fluid": "gtceu:light_fuel", "amount": 60},
          {"fluid": "gtceu:heavy_fuel", "amount": 40}
        ]
      }
    }
  ],
  "finalOutputs": {
    "items": [
      {"item": "gtceu:sulfur_dust", "count": 4}
    ],
    "fluids": [
      {"fluid": "gtceu:desulfurized_light_fuel", "amount": 12000},
      {"fluid": "gtceu:heavy_fuel", "amount": 800}
    ]
  },
  "euPerTick": 128,
  "duration": 400,
  "voltageTier": 1,
  "parallel": 1
}
```

### 技术要点（已调研验证，源码 reference_src\GT5-Unofficial-master）
- **MTEMultiBlockBase继承**：参考GT5U多方块机器实现，继承`MTEMultiBlockBase`
- **RecipeMap注册**：使用`RecipeMapBuilder.of()`创建自定义RecipeMap
- **可扩展结构**：参考GT5U的结构验证逻辑，支持动态尺寸
- **JSON加载**：使用Gson解析JSON，参考GT5U的配方加载模式
- **机器消耗**：通过NBT持久化消耗记录，参考GT5U的维护系统
- **GUI系统**：使用GT5U的ModularUI系统，参考现有控制器GUI实现

### 机器消耗系统设计

#### GUI页面
```
+------------------------------------------+
| 产线聚合器 - 配方激活                      |
+------------------------------------------+
| 所需机器:                                 |
| [槽位1] 蒸馏塔 x1        [已插入]        |
| [槽位2] 化学反应釜 x2     [需插入]        |
| [槽位3] 裂解机 x1         [需插入]        |
+------------------------------------------+
| 当前电压等级: MV (2)                       |
| 配方要求电压: MV (2)                       |
| 状态: 电压满足, 机器不足                    |
+------------------------------------------+
| [激活配方]                                |
+------------------------------------------+
```

#### 激活流程
1. 玩家打开控制器GUI
2. 显示所需机器列表
3. 玩家在对应槽位插入机器
4. 点击"激活配方"
5. 机器被消耗，配方解锁
6. 配方可正常使用

### 文件清单（预估）
| 文件 | 功能 | 行数估算 |
|------|------|----------|
| `MTEProductionLineAggregator.java` | 主多方块类，继承`MTEMultiBlockBase` | ~600行 |
| `MTEHatchProductionLineController.java` | 生产线控制器仓，包含GUI | ~300行 |
| `MTEHatchRecipeStorage.java` | 配方仓，存储JSON配方 | ~150行 |
| `ProductionLineRecipeLoader.java` | JSON配方加载器 | ~250行 |
| `ProductionLineRecipeMaps.java` | RecipeMap注册 | ~50行 |
| `ProductionLineRecipe.java` | 配方数据结构 | ~150行 |
| `MachineConsumptionManager.java` | 机器消耗管理 | ~200行 |
| `ProductionLineConfig.java` | 配置文件处理 | ~50行 |
| `gui/ProductionLineControllerGUI.java` | 控制器GUI界面 | ~300行 |
| CommonProxy.java | 注册 | ~10行 |
| assets(texture/lang) | 贴图和语言文件（zh+en） | ~40行 |
| config/ae2_qof/production_lines/*.json | 配方数据包 | ~80行/配方 |

### 验证清单
1. 编译通过（`./gradlew build`）
2. 游戏内加载，产线聚合器物品可见
3. 可扩展多方块结构可正确搭建（3x3x3和5x5x5）
4. JSON配方可正确加载（支持GTNH Calculator格式）
5. NEI可查看产线配方
6. 机器消耗GUI正常显示
7. 机器可正确插入和消耗
8. 电压门槛机制生效
9. 配方可正常执行，产出正确
10. WAILA/JADE显示正确
11. 示例配方完整流程测试

### 已完成代码
- `ProductionLineRecipeMaps.java` - RecipeMap注册（~30行）
- `ProductionLineRecipe.java` - 配方数据结构（~150行）
- `MachineConsumptionManager.java` - 机器消耗管理（~100行）
- `ProductionLineRecipeLoader.java` - JSON配方加载器（~250行）
- `MTEProductionLineAggregator.java` - 主多方块类（~350行）
- `MTEHatchProductionLineController.java` - 控制器仓（~180行）
- `ProductionLineControllerGUI.java` - GUI容器（~200行）
- `ProductionLineConfig.java` - 配置文件（~100行）
- 示例配方：`petrochemical_light_fuel.json`

---

## 实施顺序

```
第一阶段: 万能维护仓（v3.16.0）
  Step 1: 创建AE2MaintenanceHatchUniversal主类
  Step 2: 实现维护绕过（Mixin注入）
  Step 3: 实现无限能源容器
  Step 4: 实现电路板→并行数映射
  Step 5: 注册物品和合成配方
  → 验证: 编译通过，游戏内加载，放入维护仓测试

第二阶段: 智能配方仓（v3.17.0）
  Step 1: 创建AE2PatternBufferHatchSmart主类
  Step 2: 实现ICraftingMedium接口
  Step 3: 实现配方类型过滤
  Step 4: 实现虚拟电路功能
  Step 5: 实现非消耗输入支持
  Step 6: 实现卡死检测
  Step 7: 注册物品和合成配方
  → 验证: 编译通过，游戏内加载，测试样板推送

第三阶段: 产线聚合器（v3.18.0）
  Step 1: 创建ProductionLineRecipeMaps注册RecipeMap
  Step 2: 创建MTEProductionLineAggregator主类
  Step 3: 实现可扩展多方块结构验证
  Step 4: 创建ProductionLineRecipe数据结构
  Step 5: 创建ProductionLineRecipeLoader加载器
  Step 6: 实现机器消耗系统
  Step 7: 创建控制器GUI界面
  Step 8: 实现电压门槛验证
  Step 9: 实现配方处理逻辑
  Step 10: 创建示例配方
  → 验证: 编译通过，游戏内加载，测试产线聚合器完整功能

第四阶段: 集成测试
  Step 1: 万能维护仓 + 智能配方仓联动测试
  Step 2: 产线聚合器与其他系统联动测试
  Step 3: 性能测试（大量样板并行处理）
  → 验证: 完整功能演示
```

### 风险评估

| 风险 | 级别 | 缓解措施 |
|------|------|----------|
| GT5U维护系统差异 | 低 | 方案B已验证可行 |
| AE2 rv3-beta-997 API差异 | 中 | 参考现有`MixinCraftingCPUCluster`实现 |
| 与其他模组冲突 | 中 | 使用`compileOnly`依赖 + `required=false` |
| 性能影响 | 低 | 槽位级计时器开销可忽略 |
| 虚拟电路兼容性 | 中 | 使用WeakReference模式，避免内存泄漏 |
| JSON解析异常 | 低 | 严格验证+错误日志+跳过无效配方 |
| 机器消耗平衡 | 中 | 通过配置文件调整消耗倍率 |
| 可扩展结构验证 | 中 | 参考GT5U现有结构验证逻辑 |
