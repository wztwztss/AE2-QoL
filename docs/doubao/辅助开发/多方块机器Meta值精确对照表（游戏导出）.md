# 多方块机器 Meta 值精确对照表（游戏导出数据）

> 数据来源：`docs/dumps/metatileentity.csv`（GTNH 2.9.0-beta-1 游戏内导出）
> 所有 GT5 机器都在 `gregtech:gt.blockmachines`（item ID 3005）下，通过 meta 值区分。
> 此表给出每个多方块机器的精确 meta 值和类名，可直接用于代码中 `new ItemStack(GTBlocks.blockMachines, 1, meta)` 或 `ItemList.Machine_Multi_XXX.get(1)`。

## 一、核心化工机器（产线聚合器 requiredMachines 最常用）

| 机器名称 | meta值 | 类名 | 备注 |
|---------|--------|------|------|
| 大型化学反应釜 | **1169** | `MTELargeChemicalReactor` | 最常用，产线聚合器大部分配方用这个 |
| 蒸馏塔 | **1126** | `MTEDistillationTower` | 石油/化工分离 |
| 进阶蒸馏塔 | **31021** | `MTEAdvDistillationTower` | GT++进阶版 |
| 巨型蒸馏塔 | **15516** | `MTEMegaDistillationTower` | 巨型版 |
| 工业电解机 | **15514** | `MTEIndustrialElectrolyzer` | 新版（推荐） |
| 工业电解机(旧) | 796 | `MTEIndustrialElectrolyzerLegacy` | 旧版，不推荐 |
| 工业搅拌机 | **15566** | `MTEIndustrialMixer` | 新版（推荐） |
| 工业搅拌机(旧) | 811 | `MTEIndustrialMixerLegacy` | 旧版 |
| 工业离心机 | **15512** | `MTEIndustrialCentrifuge` | 新版（推荐） |
| 工业离心机(旧) | 790 | `MTEIndustrialCentrifugeLegacy` | 旧版 |
| 工业热力离心机 | **15538** | `MTEIndustrialThermalCentrifuge` | 新版（推荐） |
| 工业热力离心机(旧) | 849 | `MTEIndustrialThermalCentrifugeLegacy` | 旧版 |
| 石油裂化机 | **1160** | `MTEOilCracker` | 石油裂化 |
| 工业化学浴 | **15551** | `MTEIndustrialChemicalBath` | 化学浴 |
| 工业浮选机 | **15560** | `MTEFrothFlotationCell` | 矿物浮选 |
| 工业浮选机(旧) | 31028 | `MTEFrothFlotationCellLegacy` | 旧版 |
| 煮解池 | **10500** | `MTEDigester` | gtnhlanth，生物/稀土 |
| 化工厂 | **998** | `MTEChemicalPlant` | GT++化工厂 |
| 化工厂(GT++) | 21032 | `ChemicalPlant` | 另一版化工厂 |
| 石化工厂 | 21023 | `PetrochemicalPlant` | 石化专用 |

## 二、高温/热处理机器

| 机器名称 | meta值 | 类名 | 备注 |
|---------|--------|------|------|
| 工业高炉(电炉) | **1000** | `MTEElectricBlastFurnace` | EBF，最常用高炉 |
| 电力工业高炉 | 21090 | `ElectricBlastFurnace` | 另一版EBF |
| 砖高炉 | 140 | `MTEBrickedBlastFurnace` | 砖高炉 |
| 砖高炉(GT++) | 21028 | `BrickedBlastFurnace` | 另一版 |
| 巨型工业高炉 | 12730 | `MTEMegaBlastFurnaceLegacy` | 巨型版(旧) |
| 巨型工业高炉(新) | 21027 | `MegaBlastFurnace` | 巨型版(新) |
| 工业熔炉 | 1003 | `MTEMultiFurnace` | 多炉 |
| 真空冷冻机 | **1002** | `MTEVacuumFreezer` | 常用 |
| 真空冷冻机(旧) | 910 | `MTEIndustrialVacuumFreezerLegacy` | 旧版 |
| 内爆压缩机 | **1001** | `MTEImplosionCompressor` | 常用 |
| 进阶内爆压缩机 | **15547** | `MTEAdvImplosionCompressor` | 新版进阶 |
| 进阶内爆压缩机(旧) | 964 | `MTEAdvImplosionCompressorLegacy` | 旧版 |
| 电动内爆压缩机 | 15563 | `MTEElectricImplosionCompressor` | 电动版 |
| 工业高压釜 | **687** | `MTEMultiAutoclave` | 高压釜 |
| 工业激光雕刻机 | **3004** | `MTEIndustrialLaserEngraver` | 激光雕刻 |
| 工业提取机 | **3010** | `MTEIndustrialExtractor` | 提取 |
| 工业电磁分离机 | **358** | `MTEIndustrialElectromagneticSeparator` | 电磁分离 |
| 流体固化器 | **368** | `MTEMassSolidifier` | 大规模固化 |

## 三、装配/组装机器

| 机器名称 | meta值 | 类名 | 备注 |
|---------|--------|------|------|
| 装配线 | **1170** | `MTEAssemblyLine` | 标准装配线 |
| 部件装配线 | **32026** | `MTEComponentAssemblyLine` | 部件专用 |
| 电路装配线 | 12735 | `MTECircuitAssemblyLine` | 电路专用 |
| 进阶电路装配线 | 19067 | `TST_AdvCircuitAssemblyLine` | TST进阶版 |
| 大型装配线 | 21090 | `GrandAssemblyLine` | 巨型装配线 |

## 四、其他相关机器

| 机器名称 | meta值 | 类名 | 备注 |
|---------|--------|------|------|
| 工业精密车床 | 686 | `MTEMultiLathe` | 车床 |
| 多罐装机Pro | 360 | `MTEMultiCanner` | 罐装 |
| 固化舱I | 31781 | `MTEHatchSolidifier` | 固化舱 |
| 固化舱II | 31782 | `MTEHatchSolidifier` | 固化舱 |
| 固化舱III | 31783 | `MTEHatchSolidifier` | 固化舱 |
| 固化舱IV | 31784 | `MTEHatchSolidifier` | 固化舱 |
| 蒸汽熔炉 | 31087 | `MTESteamFurnaceMulti` | 蒸汽版 |

## 五、GTNH 各 mod 额外多方块机器

| 机器名称 | meta值 | 类名 | 来源mod |
|---------|--------|------|---------|
| 煮解池 | 10500 | `MTEDigester` | gtnhlanth |
| 化工厂 | 998 | `MTEChemicalPlant` | GT++ |
| 进阶蒸馏塔 | 31021 | `MTEAdvDistillationTower` | GT++ |
| 巨型石油裂化机 | 13367 | `MTEMegaOilCracker` | GT++ |
| 进阶巨型石油裂化机 | 19027 | `TST_AdvancedMegaOilCracker` | TST |
| 熔融高炉 | 19068 | `TST_SwelegfyrBlastFurnace` | TST |
| 大明科技 | 21920 | `NineIndustrialMultiMachine` | 123Technology |
| 奥兹工业高炉 | 25000 | `TCBlastFurnace` | 其他mod |
| IMBA EBF | 23543 | `OTEIMBABlastFurnace` | 其他mod |
| 阿拉善脉热分离厂 | 23584 | `OTEMegaThermalCentrifuge` | 其他mod |

## 六、requiredMachines 使用建议

1. **优先使用新版机器**（非Legacy）：工业电解机用15514、工业搅拌机用15566、工业离心机用15512、工业热力离心机用15538
2. **最常用的5台机器**：大型化学反应釜(1169)、蒸馏塔(1126)、工业高炉(1000)、工业电解机(15514)、工业搅拌机(15566)
3. **代码中获取ItemStack**：
   ```java
   // 方法1：通过meta值直接获取
   ItemStack machine = new ItemStack(GTBlocks.blockMachines, 1, 1169); // 大型化学反应釜
   
   // 方法2：通过ItemList枚举（推荐，更安全）
   ItemStack machine = ItemList.Machine_Multi_LargeChemicalReactor.get(1);
   ```
4. **ItemList枚举名与meta值对照**：
   - `Machine_Multi_LargeChemicalReactor` → 1169
   - `Machine_Multi_DistillationTower` → 1126
   - `Machine_Multi_BlastFurnace` → 1000
   - `Machine_Multi_IndustrialElectrolyzer` → 15514
   - `Machine_Multi_IndustrialMixer` → 15566
   - `Machine_Multi_IndustrialCentrifuge` → 15512
   - `Machine_Multi_OilCracker` → 1160
   - `Machine_Multi_VacuumFreezer` → 1002
   - `Machine_Multi_ImplosionCompressor` → 1001
   - `Machine_Multi_Autoclave` → 687
   - `Machine_Multi_IndustrialLaserEngraver` → 3004
   - `Machine_Multi_IndustrialExtractor` → 3010
   - `Machine_Multi_IndustrialElectromagneticSeparator` → 358
   - `Machine_Multi_Assemblyline` → 1170

## 七、注意事项

1. **Legacy vs 新版**：GT5有两套机器，旧版（Legacy，meta 790-964范围）和新版（meta 15512-15566范围）。GTNH中通常使用新版机器，NEI中显示的也是新版配方。
2. **GT++机器**：部分机器（如化工厂998、进阶蒸馏塔31021）是GT++添加的，类名前缀不同，ItemList枚举名可能不同。
3. **gtnhlanth机器**：煮解池(10500)是gtnhlanth添加的，不在GT5的ItemList中，需要通过meta值直接获取。
4. **meta值范围**：GT5标准机器meta值在1-32000范围，GT++/其他mod扩展机器meta值在10000-33000范围。
5. **产线聚合器激活检查**：requiredMachines中的机器只检查是否在同一AE网络内（或同一多方块结构内），不要求机器正在运行，只要求已放置且可访问。

---

*生成时间：2026-09-02 | 数据来源：docs/dumps/metatileentity.csv（游戏内导出，最权威）*
