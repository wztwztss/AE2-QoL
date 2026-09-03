# 产线聚合器 OpenCode 开发指南

> 本文档为 OpenCode 辅助开发准备，包含完整的文件改动清单、GT5 API 用法、GTL 参考算法摘要和常见坑。
> 配套文档：`产线聚合器设计方案.md`（v1.3，需求权威来源）、`产线工艺.md`（v1.28，24条配方配平数据）

---

## 一、项目结构与文件清单

### 1.1 现有文件（需修改）

```
src/main/java/com/wztwzt/ae2_qof/productionline/
├── MTEProductionLineAggregator.java      # 主多方块机器（32120），核心逻辑，大量TODO
├── MTEHatchProductionLineController.java # 控制器仓（32121），激活逻辑，NBT已有基础
├── ProductionLineRecipe.java             # 配方数据类，缺 inputs/outputs 字段
├── ProductionLineRecipeLoader.java       # JSON解析+注册，registerRecipe 是 TODO
├── ProductionLineRecipeMaps.java         # RecipeMap定义，maxIO需放宽
├── ProductionLineConfig.java             # 配置，基本可用
├── MachineConsumptionManager.java        # 全局WorldSavedData，与"每结构独立"冲突，需废弃
└── gui/
    └── ProductionLineControllerGUI.java  # 控制器仓GUI，激活界面需适配
```

### 1.2 需新增文件

```
src/main/java/com/wztwzt/ae2_qof/productionline/
└── (可能需要) ProductionLineRecipeCategory.java  # NEI分类（如RecipeMap自动处理则不需要）

src/main/resources/assets/ae2_qof/
├── lang/
│   ├── en_US.lang  # 英文本地化
│   └── zh_CN.lang  # 中文本地化
└── textures/blocks/
    ├── production_line_aggregator.png       # 主机器材质（可先用现有机器占位）
    └── production_line_controller.png       # 控制器仓材质

config/ae2_qof/production_lines/    # JSON配方目录（运行时生成或打包进resources）
├── hydrogen.json
├── light_fuel.json
└── ... (24条)
```

### 1.3 需修改的注册文件

- `CommonProxy.java` 或机器注册类：注册 MTEProductionLineAggregator(32120) 和 MTEHatchProductionLineController(32121)
- 查找现有模组中其他 MTE 机器的注册位置，按相同模式注册

---

## 二、核心实现要点

### 2.1 MTEProductionLineAggregator（主机器）

**继承关系**：参考 GT5 中 `GT_MetaTileEntity_BasicMachine` 或多方块机器基类。建议参考 `gregtech.api.metatileentity.implementations.GT_MetaTileEntity_MultiBlockBase`。

**必须实现的方法**：

```java
// 结构校验：5×5×5立方体
@Override
public boolean checkStructure() {
    // 扫描5×5×5范围
    // 外壳：GT标准机器外壳（分等级，LV/MV/HV/EV/IV/LuV）
    // 控制器：本方块（32120），位置建议正面中心
    // 能量仓：至少1个，决定实际电压
    // 输入总线/输入仓：至少1个
    // 输出总线/输出仓：至少1个（接ME输出总线）
    // 控制器仓（32121）：可选，用于激活配方
}

// 机器等级校验：外壳最低等级 >= 配方 minVoltageTier
@Override
public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, int aX, int aY, int aZ) {
    // 1. checkStructure()
    // 2. 扫描外壳，取最低等级作为 machineTier
    // 3. 能量仓电压 >= 配方 euPerTick（超频逻辑）
    // 4. 返回是否可运行
}

// 配方匹配：纯GT风格，放入输入自动匹配
@Override
public GT_Recipe checkRecipe(ItemStack[] aInputs, FluidStack[] aFluids) {
    // 从 ProductionLineRecipeMaps.productionLineRecipes 查找匹配配方
    // 匹配逻辑：输入物品包含配方所有inputs.items，输入流体包含配方所有inputs.fluids
    // 催化剂处理：inputs和outputs中amount相同的物品，只检查存在性，不消耗
}

// 执行配方
@Override
public void processRecipe() {
    // 1. 消耗非催化剂输入
    // 2. 催化剂保留（不消耗）
    // 3. 产出全部outputs（物品+流体）
    // 4. 超频计算：按能量仓电压 overclock
}
```

**5×5×5结构建议**：
```
层1（底层）：5×5 外壳
层2：5×5，中心空，四边外壳
层3：5×5，中心=控制器（32120），正面=控制器仓（32121），其余=外壳/能量仓/输入输出总线
层4：5×5，中心空，四边外壳
层5（顶层）：5×5 外壳
```

### 2.2 ProductionLineRecipeLoader（配方加载）

```java
public class ProductionLineRecipeLoader {
    // 启动时调用，扫描 config/ae2_qof/production_lines/*.json
    public static void loadRecipes() {
        // 1. 遍历JSON文件
        // 2. 解析为 ProductionLineRecipe 对象
        // 3. 调用 registerRecipe() 注册到 RecipeMap
    }

    private static void registerRecipe(ProductionLineRecipe recipe) {
        // 转换为 GT_Recipe
        // itemInputs: 配方所有inputs.items（含催化剂）
        // fluidInputs: 配方所有inputs.fluids
        // itemOutputs: 配方所有outputs.items
        // fluidOutputs: 配方所有outputs.fluids
        // 催化剂标记：用 GT_Recipe 的特殊机制或自定义字段标记
        // eut: recipe.euPerTick
        // duration: recipe.duration
        // 注册到 ProductionLineRecipeMaps.productionLineRecipes
    }
}
```

**催化剂识别逻辑**（在Loader或Recipe中实现）：
```java
// 遍历 inputs.items 和 outputs.items
// 如果某物品在两边都存在且 count 相同 → 标记为 catalyst
// 运行时：catalyst 只检查存在性，不从输入槽扣除
```

### 2.3 MTEHatchProductionLineController（控制器仓，激活逻辑）

```java
// NBT结构（已有基础）
NBTTagCompound nbt = new NBTTagCompound();
NBTTagList activated = new NBTTagList();
// 每个激活的配方：{ "id": "hydrogen", "activated": true }
nbt.setTag("activatedRecipes", activated);

// 激活流程（GUI按钮触发）
public void activateRecipe(String recipeId, ItemStack[] machineItems) {
    // 1. 检查 machineItems 是否匹配配方 requiredMachines
    // 2. 匹配成功 → 消耗 machineItems
    // 3. 写入 NBT activatedRecipes
    // 4. 标记为永久激活
}

// 配方运行前检查
public boolean isRecipeActivated(String recipeId) {
    return activatedRecipes.contains(recipeId);
}
```

**注意**：原 `MachineConsumptionManager`（全局 WorldSavedData）与"每结构独立"冲突，直接废弃，激活状态只存控制器仓 NBT。

### 2.4 ProductionLineRecipeMaps（RecipeMap）

```java
public static final GT_Recipe_When_Outputs_Are_The_Same productionLineRecipes = 
    new GT_Recipe_When_Outputs_Are_The_Same("生产流水线", null, 1, 
        MAX_ITEM_INPUTS, MAX_FLUID_INPUTS, 
        MAX_ITEM_OUTPUTS, MAX_FLUID_OUTPUTS, 
        0, 0, false);
```

**maxIO 上限确认**：GTNH 2.9.0 中 `GT_Recipe_When_Outputs_Are_The_Same` 的输出槽上限。铂处理/稀土处理输出可能超过默认(6,4,2,2)，需调研最大允许值。若不够，考虑：
- 方案A：用 `GT_Recipe_When_Outputs_Are_The_Same` 的大槽位版本
- 方案B：自定义 RecipeMap 子类，重写输出逻辑
- 方案C：部分副产物合并（不推荐，违反"输出全部产物"规则）

---

## 三、GT5 API 关键用法

### 3.1 机器注册

```java
// 参考其他 MTE 机器的注册方式
// 通常在 CommonProxy 或 GT_MetaTileEntity 初始化时
GT_MetaTileEntity.addMetaTileEntity(
    new MTEProductionLineAggregator(32120, "productionline.aggregator", "产线聚合器")
);
GT_MetaTileEntity.addMetaTileEntity(
    new MTEHatchProductionLineController(32121, "productionline.controller", "产线控制器仓")
);
```

### 3.2 RecipeMap 注册配方

```java
GTValues.RA.stdBuilder()
    .itemInputs(itemStacks)
    .fluidInputs(fluidStacks)
    .itemOutputs(itemOutputs)
    .fluidOutputs(fluidOutputs)
    .eut(euPerTick)
    .duration(duration)
    .addTo(ProductionLineRecipeMaps.productionLineRecipes);
```

### 3.3 多方块机器结构校验

参考 `gregtech.api.metatileentity.implementations.GT_MetaTileEntity_MultiBlockBase`：
- `addEnergyInputToMachineList()` - 添加能量仓
- `addInputToMachineList()` - 添加输入总线
- `addOutputToMachineList()` - 添加输出总线
- `addMufflerToMachineList()` - 添加 muffler（可选）
- `addMaintenanceToMachineList()` - 添加维护仓（可选，GTNH风格建议加）

### 3.4 NBT 持久化

```java
// 保存
@Override
public void saveNBTData(NBTTagCompound aNBT) {
    aNBT.setTag("activatedRecipes", activatedRecipes);
}

// 读取
@Override
public void loadNBTData(NBTTagCompound aNBT) {
    activatedRecipes = aNBT.getTagList("activatedRecipes", 10);
}
```

### 3.5 ItemList 枚举引用（获取机器物品）

```java
// 多方块机器控制器在 ItemList 中的枚举名
// 用 ItemList.Machine_Multi_XXX.get(1) 获取 ItemStack
// 例：
ItemStack blastFurnace = ItemList.Machine_Multi_BlastFurnace.get(1);
ItemStack distillationTower = ItemList.Machine_Multi_DistillationTower.get(1);
ItemStack largeChemReactor = ItemList.Machine_Multi_LargeChemicalReactor.get(1);
```

**关键多方块机器的 ItemList 枚举名**（从源码确认）：
| 机器 | ItemList 枚举名 |
|---|---|
| 工业高炉 | Machine_Multi_BlastFurnace |
| 蒸馏塔 | Machine_Multi_DistillationTower |
| 大型化学反应釜 | Machine_Multi_LargeChemicalReactor |
| 真空冷冻机 | Machine_Multi_VacuumFreezer |
| 内爆压缩机 | Machine_Multi_ImplosionCompressor |
| 工业电解机 | (单方块，用 Machine_XX_Electrolyzer) |
| 工业离心机 | (单方块，用 Machine_XX_Centrifuge) |
| 工业搅拌机 | (单方块，用 Machine_XX_Mixer) |
| 高压釜 | Machine_Multi_Autoclave |
| 工业激光雕刻机 | Machine_Multi_IndustrialLaserEngraver |
| 工业提取机 | Machine_Multi_IndustrialExtractor |
| 装配线 | Machine_Assemblyline |
| 化学浴 | (单方块，用 chemicalBathRecipes) |
| 石油裂化机 | Machine_Multi_CrackingUnit |
| 溶解罐 | (gtnhlanth 添加，需查 LanthanidesRecipeMaps) |
| 工业筛选机 | (单方块，用 sifterRecipes) |
| 热力离心机 | (单方块，用 thermalCentrifugeRecipes) |
| 流体固化器 | Machine_Mass_Solidifier |
| 电磁分离机 | Machine_Multi_IndustrialElectromagneticSeparator |

> 注意：部分机器是 gtnhlanth/BartWorks/GoodGenerator 添加的，需从对应模组的 ItemList/WerkstoffLoader 获取。

---

## 四、GTL 一步产线核心算法摘要

GTL 参考代码在 `E:\wzt\MC\modcreater\reference_src\一步产线参考代码（GTL适配）`，是 KubeJS 脚本形式。核心逻辑：

### 4.1 配方定义方式
```javascript
// KubeJS 中通过事件注册自定义配方
// 每条产线定义：输入物品/流体 + 输出物品/流体 + 耗电 + 耗时
// 不记录中间步骤，直接合并为单条配方
```

### 4.2 多输入匹配
- 玩家放入全部输入（含中间料和催化剂）
- 机器按"输入包含配方所有需求"匹配
- 不要求输入槽精确等于配方，允许多余物品

### 4.3 催化剂处理
- GTL 中催化剂通常作为"不消耗的输入"处理
- 实现方式：配方运行后将催化剂物品放回输入槽
- 或：用 GT Recipe 的特殊标记字段

### 4.4 并行与超频
- 能量仓电压决定最大并行数
- 超频公式：电压每升一级，耗时÷4，耗电×2（GT标准超频）

---

## 五、常见坑与注意事项

### 5.1 GTNH 2.9.0 特有
- **Java 17-25**：编译用 Java 17，运行可用 Java 17-25
- **GT5-Unofficial**：不是 GTCEu，API 差异大，不要参考 GTCEu 代码
- **ModularUI2**：GUI 用 ModularUI2（本模组已用），不是 GT 原生 GUI
- **BartWorks/gtnhlanth/GoodGenerator**：很多材料和机器来自这些附属模组，需确认依赖

### 5.2 RecipeMap 限制
- `GT_Recipe_When_Outputs_Are_The_Same` 的 maxIO 有上限
- 输出物品/流体种类过多时可能超限
- 建议：先确认上限，铂处理（输出15+种）可能需要特殊处理

### 5.3 流体注册名
- GT5 流体注册名通常是材料名小写，如 `water`、`oil`、`hydrogen`
- 部分流体有 `_gt5u` 后缀（如盐酸 `hydrochloricacid_gt5u`）
- 从 `Materials.XXX.getFluid(amount)` 获取时，注册名是 `Materials.XXX.mUnlocalizedName` 小写

### 5.4 物品 meta 值
- `gregtech:gt.meta.dust` 的 meta 值对应材料在 Materials 枚举中的 ID
- `gregtech:gt.meta.item` 的 meta 值对应 ItemList 枚举中的 ordinal
- `gregtech:gt.blockmachines` 的 meta 值对应机器 ID（32120 等）
- **建议**：代码中用 `ItemList.XXX.get(count)` 或 `GTOreDictUnificator.get(OrePrefixes.dust, Materials.XXX, count)` 获取物品，不要硬编码 meta

### 5.5 多方块机器结构
- 5×5×5 结构中，控制器位置决定正面朝向
- 外壳等级检测：扫描结构中所有外壳方块，取最低等级
- 能量仓/输入输出总线可以放在任何外壳位置（替换外壳）

### 5.6 NBT 与世界保存
- 控制器仓的 NBT 在方块破坏时会丢失（除非用 ItemBlock 保存）
- 激活状态建议同时存 WorldSavedData 作为兜底（但用户要求每结构独立，以 NBT 为准）
- 多方块机器解散重建时，控制器仓 NBT 是否保留需测试

### 5.7 NEI 集成
- RecipeMap 注册后 NEI 自动显示
- 但催化剂（输入=输出）在 NEI 中可能显示异常（同时出现在输入和输出）
- 可能需要自定义 NEI 处理器隐藏催化剂的输出显示

---

## 六、实施顺序建议

1. **Phase 1 - 框架跑通**（氢产线验证）
   - 注册两个机器（32120/32121）
   - 实现 MTEProductionLineAggregator 基础结构（5×5×5）
   - 实现 ProductionLineRecipe + Loader（解析JSON→注册RecipeMap）
   - 写 hydrogen.json（最简单，1输入流体→2输出流体）
   - 验证：放水电解→产出氢+氧

2. **Phase 2 - 核心机制**
   - 催化剂处理（输入=输出不消耗）
   - 外壳等级限制（minVoltageTier）
   - 控制器仓激活逻辑（NBT持久化）
   - 输出槽放宽（maxIO）

3. **Phase 3 - 批量配方**
   - 补全24条JSON配方
   - 逐条测试 NEI 显示和运行

4. **Phase 4 - 完善**
   - GUI 适配（ModularUI2）
   - 材质/本地化
   - 崩溃修复和边界情况处理

---

## 七、编译与运行

```powershell
# 编译（Java 17）
$env:JAVA_HOME = "E:\java17"
.\gradlew.bat build -x spotlessJavaCheck -x spotlessCheck

# 测试
# 将 build/libs/*.jar 放入 GTNH 实例 mods 目录
# 实例路径：E:\wzt\MC\PL genmulu\GT_New_Horizons_2.9.0-beta-1_Java_17-25(1)\.minecraft
# 注意：实例只读，测试时复制到其他实例或用开发环境运行
```
