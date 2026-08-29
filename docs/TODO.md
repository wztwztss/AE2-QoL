# 待实施项目

> 本文档记录已调研完毕、等待排期实施的特性。
> 每条目实施时按仓库规范独立 commit（代码 + docs + CHANGELOG + README + 版本号同 commit）。

---

## 待验证

- [ ] **自动上传记忆功能**（v3.16.1 调试日志已加，待游戏内验证）
  - 配方名映射 (`RecipeNameUtil`)、记忆供应器 (`ClientState`)、工作台配方映射 (`RecipeMapNameConfig`)
  - 已注释掉高频调试日志，需要时取消注释排查

## 代码质量改进（2026-08-28 检查）

- [ ] **P1 清理模板注释残留**：`MyMod.java`、`CommonProxy.java`、`AEInfinityCell.java`、`ClientProxy.java` 中的 `(Remove if not needed)` 注释
- [ ] **P2 补全 mixin_notes.md**：当前仅登记 2/23 个 Mixin，需补全剩余 21 个的注入点和功能说明
- [ ] **P4 switch 语法迁移**：10 处旧式 `case X: ... break;` 语法，需确认 Jabel 是否支持 `case X ->` 语法后再迁移
- **P3 接口 I 前缀**：保持现状（与 AE2 API 风格一致），不修改

## 已完成（v3.10.1 ~ v3.16.1）

| 版本 | 特性 | 完成日期 |
|------|------|----------|
| v3.10.1 | 展示条定位 hotfix | 2026-08-24 |
| v3.11.0 | ME 任务检测器 | 2026-08-24 |
| v3.12.0 | 吞并 AEInfinityCell | 2026-08-24 |
| v3.13.0 | 无限磁盘 tooltip 统计增强 | 2026-08-24 |
| v3.14.0 | 原生 pin 置顶行 + 科学计数法 + GuideNH | 2026-08-24 |
| v3.15.0 | 通知横幅 AE2 原生样式 + pin 开关 | 2026-08-25 |
| v3.16.0 | 万能维护仓（维护仓可用，维护绕过待完善） | 2026-08-29 |
| v3.16.1 | TST 兼容 + Quest Detector 崩溃修复 + 检测修复 | 2026-08-29 |

---

## 待完善

- [ ] **万能维护仓 — 维护绕过**：MixinMTEMultiBlockBase 已从 mixins.json 移除（`after:gregtech` 导致 TST 崩溃），维护绕过功能暂未生效。需要找到不改变加载顺序的实现方式

---

## [待实施] 智能配方仓 — v3.17.0

### 需求

仿 GT-Shanhai 的"星律样板供料系统"：配方类型过滤+虚拟电路+非消耗输入+卡死检测。

### 已确认决策

- 物品名 `AE2PatternBufferHatchSmart`，显示名"智能配方仓"
- GT 仓室形态，实现 `ICraftingMedium` 接口
- 每个槽位精确 `RecipeMap` 过滤
- 虚拟电路缓存（WeakReference），无需物理物品
- 非消耗输入支持催化剂/模具
- 槽位级卡死检测，超时通知玩家（默认5分钟）

### 槽位结构

| 槽位 | 功能 | 数量 |
|------|------|------|
| 样板槽 | 存放 AE2 样板 | 4 |
| 配方类型过滤槽 | 决定允许的配方类型 | 4 |
| 虚拟电路槽 | 绑定电路配置，执行时自动应用 | 4 |
| 非消耗输入槽 | 存放催化剂/模具 | 4 |

### 文件清单

| 文件 | 功能 | 行数 |
|------|------|------|
| `AE2PatternBufferHatchSmart.java` | 主仓室类 | ~600 |
| `PatternBufferSlotData.java` | 槽位数据存储 | ~150 |
| `PatternBufferStuckDetector.java` | 卡死检测和通知 | ~150 |
| `VirtualCircuitCache.java` | 虚拟电路缓存 | ~100 |
| `MixinCraftingCPUCluster.java` | 支持非消耗输入 | ~150 |
| assets(texture/lang) | 贴图和语言文件 | ~20 |

### 虚拟电路生命周期

1. **绑定**：玩家右击虚拟电路槽 → 选择电路物品 → 绑定到槽位
2. **编码**：样板编码器读取配置 → 写入样板 NBT
3. **执行**：AE2 合成 CPU 推送样板 → 从缓存获取电路 → 设置到 GT 机器
4. **回收**：合成完成 → 电路配置保留在缓存 → 下次复用

### 验证清单

1. 编译通过
2. 游戏内加载，物品可见
3. 配方类型过滤生效
4. 虚拟电路绑定和执行正常
5. 非消耗输入无需物理物品
6. 卡死检测超时警告

---

## [待实施] 产线聚合器 — v3.18.0

### 需求

仿 GTL 的一步产线系统：将复杂多步骤产线合并为单一方块处理。

### 已确认决策

- 物品名 `MTEProductionLineAggregator`，显示名"产线聚合器"
- GT 多方块机器，继承 `MTEMultiBlockBase`
- 可扩展结构（最小 3x3x3）
- JSON 配方数据包（兼容 GTNH Calculator 导出格式）
- 机器消耗系统 + 电压等级限制

### 机器消耗系统

1. 打开控制器 GUI → 显示所需机器列表
2. 玩家在对应槽位插入机器
3. 点击"激活配方" → 机器被消耗，配方解锁
4. 双重限制：机器消耗 + 电压等级

### JSON 配方格式

```json
{
  "id": "petrochemical_light_fuel",
  "name": "石油工业/轻燃油",
  "requiredMachines": [
    { "id": "gtceu:distillation_tower", "name": "蒸馏塔", "count": 1, "consumed": true }
  ],
  "steps": [
    { "machine": "gtceu:distillation_tower", "inputs": {...}, "outputs": {...} }
  ],
  "euPerTick": 128, "duration": 400, "voltageTier": 1
}
```

### 文件清单

| 文件 | 功能 | 行数 |
|------|------|------|
| `MTEProductionLineAggregator.java` | 主多方块类 | ~600 |
| `MTEHatchProductionLineController.java` | 控制器仓+GUI | ~300 |
| `MTEHatchRecipeStorage.java` | 配方仓 | ~150 |
| `ProductionLineRecipeLoader.java` | JSON 配方加载器 | ~250 |
| `ProductionLineRecipeMaps.java` | RecipeMap 注册 | ~50 |
| `ProductionLineRecipe.java` | 配方数据结构 | ~150 |
| `MachineConsumptionManager.java` | 机器消耗管理 | ~200 |
| `gui/ProductionLineControllerGUI.java` | GUI 界面 | ~300 |

### 已完成代码（半成品，需重构）

`productionline/` 包下已有初版代码，注册/配方加载/GUI 框架已搭建，但未接入 CommonProxy 注册流程。

### 验证清单

1. 编译通过
2. 可扩展多方块结构搭建（3x3x3 / 5x5x5）
3. JSON 配方加载
4. 机器消耗 GUI 正常
5. 电压门槛机制生效
6. 配方执行产出正确
