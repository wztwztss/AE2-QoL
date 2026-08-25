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
7. [候选] 二合一终端接入 pin 置顶行（自定义布局，3.16.0）

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
