# 产线聚合器核心算法详细拆解（伪代码）

> 供OpenCode开发时直接对照实现。所有算法基于GT5 1.7.10多方块机器标准模式+用户确认的设计规则。
> 参考：GT5 MTEDistillationTower / MTELargeChemicalReactor / MTEAssemblyline 实现模式。

## 一、整体架构

```
┌─────────────────────────────────────────────────────────┐
│                    MTEProductionLineAggregator           │
│                    (多方块机器主体)                        │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ 输入总线×N   │  │ 输出总线×N   │  │ 能量仓×N      │  │
│  │ (物品输入)    │  │ (物品输出)    │  │ (EU存储)      │  │
│  └──────────────┘  └──────────────┘  └───────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ 输入仓×N     │  │ 输出仓×N     │  │ 控制器仓×1    │  │
│  │ (流体输入)    │  │ (流体输出)    │  │ (激活/NBT)    │  │
│  └──────────────┘  └──────────────┘  └───────────────┘  │
│                                                          │
│  核心逻辑：checkProcessing() → 匹配配方 → 消耗输入 →     │
│           等待duration → completeProcessing() → 产出输出  │
└─────────────────────────────────────────────────────────┘
```

## 二、核心算法流程

### 2.1 结构验证（checkStructure）

```
function checkStructure():
    // 5×5×5立方体，控制器在正面中心
    // 遍历5×5×5范围内的所有方块
    
    casingType = null  // 外壳材料类型（决定电压等级）
    controllerFound = false
    inputBusCount = 0
    outputBusCount = 0
    inputHatchCount = 0
    outputHatchCount = 0
    energyHatchCount = 0
    
    for x in -2..2:
        for y in -2..2:
            for z in -2..2:
                block = getBlockAt(controllerX+x, controllerY+y, controllerZ+z)
                
                if isController(block):
                    controllerFound = true
                    continue
                
                if isCasing(block):
                    if casingType == null:
                        casingType = getCasingTier(block)  // 铜/钢/铝/不锈钢/钛/钨钢/铱/氖/锇
                    else if casingType != getCasingTier(block):
                        return false  // 外壳等级不一致
                    continue
                
                if isInputBus(block):
                    inputBusCount++
                    mInputBuses.add(block)
                    continue
                
                if isOutputBus(block):
                    outputBusCount++
                    mOutputBuses.add(block)
                    continue
                
                if isInputHatch(block):
                    inputHatchCount++
                    mInputHatches.add(block)
                    continue
                
                if isOutputHatch(block):
                    outputHatchCount++
                    mOutputHatches.add(block)
                    continue
                
                if isEnergyHatch(block):
                    energyHatchCount++
                    mEnergyHatches.add(block)
                    continue
                
                if isControllerHatch(block):  // MTEHatchProductionLineController
                    mController = block
                    continue
                
                // 内部3×3×3必须是空气或可替换方块
                if x in -1..1 and y in -1..1 and z in -1..1:
                    if not isAir(block) and not isReplaceable(block):
                        return false
                else:
                    // 外壳层必须是外壳或功能方块
                    return false
    
    // 最低要求
    if not controllerFound: return false
    if mController == null: return false
    if inputBusCount == 0 and inputHatchCount == 0: return false
    if outputBusCount == 0 and outputHatchCount == 0: return false
    if energyHatchCount == 0: return false
    
    // 记录外壳等级
    mCasingTier = casingType
    mMachineVoltageTier = casingTierToVoltageTier(casingType)
    // 铜=1(LV), 钢=2(MV), 铝=3(HV), 不锈钢=4(EV), 钛=5(IV),
    // 钨钢=6(LuV), 铱=7(ZPM), 氖=8(UV), 锇=9(UHV)
    
    return true
```

### 2.2 配方匹配（checkProcessing核心）

```
function checkProcessing():
    // 每tick调用一次（GT5多方块标准）
    
    // 1. 检查结构是否有效
    if not checkMachine():
        return NO_RECIPE
    
    // 2. 如果当前有正在进行的配方，继续
    if mCurrentRecipe != null and mProgress < mMaxProgress:
        return SUCCESSFUL  // 继续当前配方
    
    // 3. 收集所有输入
    inputItems = collectAllInputItems()      // 从所有mInputBuses收集
    inputFluids = collectAllInputFluids()    // 从所有mInputHatches收集
    
    if inputItems.isEmpty() and inputFluids.isEmpty():
        return NO_RECIPE
    
    // 4. 遍历所有已加载配方，寻找匹配
    for recipe in ProductionLineRecipeLoader.getAllRecipes():
        
        // 4a. 检查电压等级
        if recipe.minVoltageTier > mMachineVoltageTier:
            continue  // 外壳等级不够
        
        // 4b. 检查是否已激活（机器消耗激活）
        if not mController.isRecipeActivated(recipe.id):
            continue  // 未激活的配方不能使用
        
        // 4c. 检查输入是否匹配（含催化剂）
        if not matchesInputs(recipe, inputItems, inputFluids):
            continue
        
        // 4d. 检查输出空间
        if not hasOutputSpace(recipe):
            continue  // 输出总线/仓满了
        
        // 5. 找到匹配配方，开始处理
        mCurrentRecipe = recipe
        mMaxProgress = recipe.duration
        mProgress = 0
        mEUt = recipe.euPerTick
        
        // 6. 消耗输入（催化剂不消耗）
        consumeInputs(recipe, inputItems, inputFluids)
        
        return SUCCESSFUL
    
    return NO_RECIPE
```

### 2.3 输入匹配逻辑（含催化剂）

```
function matchesInputs(recipe, inputItems, inputFluids):
    // 分离消耗性输入和催化剂
    consumableItems = []
    catalystItems = []
    consumableFluids = []
    catalystFluids = []
    
    for itemInput in recipe.itemInputs:
        if isCatalyst(itemInput, recipe):
            catalystItems.add(itemInput)
        else:
            consumableItems.add(itemInput)
    
    for fluidInput in recipe.fluidInputs:
        if isCatalyst(fluidInput, recipe):
            catalystFluids.add(fluidInput)
        else:
            consumableFluids.add(fluidInput)
    
    // 检查消耗性物品：输入必须包含足够数量
    for required in consumableItems:
        found = findItem(inputItems, required.item, required.meta)
        if found == null or found.count < required.count:
            return false
    
    // 检查消耗性流体：输入必须包含足够量
    for required in consumableFluids:
        found = findFluid(inputFluids, required.fluid)
        if found == null or found.amount < required.amount:
            return false
    
    // 检查催化剂物品：只检查存在，不检查数量（或检查>=1）
    for catalyst in catalystItems:
        found = findItem(inputItems, catalyst.item, catalyst.meta)
        if found == null or found.count < 1:
            return false  // 催化剂必须存在
    
    // 检查催化剂流体：只检查存在
    for catalyst in catalystFluids:
        found = findFluid(inputFluids, catalyst.fluid)
        if found == null or found.amount < 1:
            return false
    
    return true

function isCatalyst(input, recipe):
    // 催化剂定义：输入和输出中相同item+meta（或fluid）且数量相同
    for output in recipe.itemOutputs:
        if input.item == output.item and input.meta == output.meta 
           and input.count == output.count:
            return true
    for output in recipe.fluidOutputs:
        if input.fluid == output.fluid and input.amount == output.amount:
            return true
    return false
```

### 2.4 输入消耗逻辑

```
function consumeInputs(recipe, inputItems, inputFluids):
    for itemInput in recipe.itemInputs:
        if isCatalyst(itemInput, recipe):
            continue  // 催化剂不消耗
        
        // 从输入总线中消耗物品
        remaining = itemInput.count
        for bus in mInputBuses:
            for slot in 0..bus.getSizeInventory()-1:
                stack = bus.getStackInSlot(slot)
                if stack != null and matches(stack, itemInput):
                    consume = min(remaining, stack.stackSize)
                    stack.stackSize -= consume
                    remaining -= consume
                    if stack.stackSize <= 0:
                        bus.setInventorySlotContents(slot, null)
                    if remaining <= 0:
                        break
            if remaining <= 0:
                break
    
    for fluidInput in recipe.fluidInputs:
        if isCatalyst(fluidInput, recipe):
            continue  // 催化剂不消耗
        
        // 从输入仓中消耗流体
        remaining = fluidInput.amount
        for hatch in mInputHatches:
            drained = hatch.drain(remaining, false)  // 先模拟
            if drained != null and drained.amount > 0:
                hatch.drain(drained.amount, true)  // 实际消耗
                remaining -= drained.amount
            if remaining <= 0:
                break
```

### 2.5 进度推进与能量消耗（onPostTick）

```
function onPostTick(world, x, y, z):
    if not isActive():
        return
    
    if mCurrentRecipe == null:
        return
    
    // 1. 检查能量是否足够
    requiredEnergy = mEUt
    totalStored = getTotalStoredEU()
    
    if totalStored < requiredEnergy:
        // 能量不足，暂停（不推进进度，但也不重置）
        return
    
    // 2. 消耗能量
    consumeEnergy(requiredEnergy)
    
    // 3. 推进进度
    mProgress++
    
    // 4. 检查是否完成
    if mProgress >= mMaxProgress:
        completeProcessing()
```

### 2.6 产物输出（completeProcessing）

```
function completeProcessing():
    recipe = mCurrentRecipe
    
    // 1. 输出物品到输出总线
    for itemOutput in recipe.itemOutputs:
        remaining = itemOutput.count
        for bus in mOutputBuses:
            for slot in 0..bus.getSizeInventory()-1:
                stack = bus.getStackInSlot(slot)
                if stack == null:
                    // 空槽位，直接放入
                    put = min(remaining, itemOutput.maxStackSize)
                    bus.setInventorySlotContents(slot, new ItemStack(itemOutput.item, put, itemOutput.meta))
                    remaining -= put
                else if matches(stack, itemOutput) and stack.stackSize < stack.getMaxStackSize():
                    // 已有相同物品，叠加
                    put = min(remaining, stack.getMaxStackSize() - stack.stackSize)
                    stack.stackSize += put
                    remaining -= put
                if remaining <= 0:
                    break
            if remaining <= 0:
                break
        
        if remaining > 0:
            // 输出总线满了，不重置配方，等待有空间
            // 这是GT5标准行为：输出满时机器卡住
            return
    
    // 2. 输出流体到输出仓
    for fluidOutput in recipe.fluidOutputs:
        remaining = fluidOutput.amount
        for hatch in mOutputHatches:
            filled = hatch.fill(new FluidStack(fluidOutput.fluid, remaining), false)  // 先模拟
            if filled > 0:
                hatch.fill(new FluidStack(fluidOutput.fluid, filled), true)  // 实际填充
                remaining -= filled
            if remaining <= 0:
                break
        
        if remaining > 0:
            // 输出仓满了
            return
    
    // 3. 全部输出成功，重置当前配方
    mCurrentRecipe = null
    mProgress = 0
    mMaxProgress = 0
    mEUt = 0
```

## 三、机器消耗激活逻辑

### 3.1 控制器仓GUI流程

```
用户右键控制器仓 → 打开GUI
    ↓
GUI显示：
  - 当前结构外壳等级
  - 所有可用配方列表（按等级过滤）
  - 每个配方显示：名称、所需机器列表、已激活/未激活
    ↓
用户选择一个未激活配方 → 显示所需机器列表
    ↓
用户在机器槽位中放入对应的机器物品（多方块机器控制器）
    ↓
点击"激活"按钮
    ↓
检查：所有槽位中的机器是否匹配配方的requiredMachines
    ↓
匹配成功 → 消耗所有机器物品 → 将recipeId加入activatedRecipes列表
         → 保存NBT → 刷新GUI显示"已激活"
匹配失败 → 提示"机器不匹配"
```

### 3.2 激活状态存储

```
// MTEHatchProductionLineController.java
private List<String> activatedRecipes = new ArrayList<>();

function saveNBT(nbt):
    nbt.setString("SelectedRecipe", selectedRecipeId)
    nbt.setInteger("ActivatedCount", activatedRecipes.size())
    for i in 0..activatedRecipes.size()-1:
        nbt.setString("ActivatedRecipe" + i, activatedRecipes.get(i))

function loadNBT(nbt):
    selectedRecipeId = nbt.getString("SelectedRecipe")
    count = nbt.getInteger("ActivatedCount")
    activatedRecipes.clear()
    for i in 0..count-1:
        activatedRecipes.add(nbt.getString("ActivatedRecipe" + i))

function isRecipeActivated(recipeId):
    return activatedRecipes.contains(recipeId)

function activateRecipe(recipeId):
    if not activatedRecipes.contains(recipeId):
        activatedRecipes.add(recipeId)
        markDirty()  // 保存NBT
```

## 四、与GT5 RecipeMap系统的集成

### 4.1 两种方案对比

| 方案 | 优点 | 缺点 |
|------|------|------|
| A. 注册到GT RecipeMap | NEI自动显示配方、标准超频逻辑 | 不支持催化剂（输入不消耗）、maxIO限制 |
| B. 完全自定义匹配 | 支持催化剂、无maxIO限制 | NEI需要手动集成、超频逻辑自己写 |

### 4.2 推荐方案：混合模式

```
// 注册到RecipeMap用于NEI显示，但实际处理用自定义逻辑
ProductionLineRecipeMaps.productionLineRecipes = 
    RecipeMapBuilder.of("gt.recipe.production_line_aggregator")
        .maxIO(24, 24, 16, 16)
        .minInputs(0, 0)
        .build()

// 在ProductionLineRecipeLoader中：
// 1. 将每个配方注册为GTRecipe（用于NEI显示）
// 2. 同时保存到自定义列表（用于实际匹配）
// 3. 催化剂在GTRecipe中标记为特殊输入（如用NBT或specialValue）

// 在MTEProductionLineAggregator中：
// 1. 重写checkProcessing()，使用自定义匹配逻辑
// 2. 不调用GT5默认的RecipeMap.findRecipe()
// 3. 这样可以正确处理催化剂
```

### 4.3 NEI显示

```
// GT5的RecipeMap自动在NEI中显示配方
// 只要注册了GTRecipe，NEI就会自动显示
// 催化剂在NEI中显示为普通输入，但实际不消耗
// 可以在配方描述中注明"催化剂：不消耗"
```

## 五、能量计算与超频

### 5.1 基础能量

```
// 配方JSON中的euPerTick是基础能耗
// duration是基础时长

// 示例：氢产线
euPerTick = 30  // LV级（V[LV]=32, RECIPE_LV=30）
duration = 2000 // 100秒（20tick=1秒）

// 总能耗 = euPerTick × duration = 30 × 2000 = 60000 EU
```

### 5.2 超频逻辑

```
// GT5标准超频：每提升一个电压等级，能耗×4，时长÷4
// 但产线聚合器的配方已经是合并后的，建议：
// - 不允许超频（固定euPerTick和duration）
// - 或者：外壳等级高于配方等级时，按标准超频

function calculateOverclock(recipe, machineTier):
    if machineTier <= recipe.minVoltageTier:
        return recipe.euPerTick, recipe.duration  // 不超频
    
    tiersAbove = machineTier - recipe.minVoltageTier
    eut = recipe.euPerTick
    duration = recipe.duration
    
    for i in 0..tiersAbove-1:
        eut = eut * 4
        duration = duration / 4
        if duration < 1:
            duration = 1
    
    return eut, duration
```

### 5.3 能量仓要求

```
// 能量仓必须能提供足够的EU/t
// 单个能量仓的最大输入取决于其等级
// 建议：至少需要与配方等级匹配的能量仓

// 示例：IV级配方需要8192 EU/t
// 单个IV能量仓最大输入=8192 EU/t（刚好）
// 建议放2个能量仓以确保稳定
```

## 六、并行处理

### 6.1 并行逻辑

```
// 用户要求"输出槽无限大"，但GT5的并行需要输入也足够
// 建议：默认不并行，或通过配置开启

function calculateParallel(recipe, inputItems, inputFluids):
    if not ProductionLineConfig.enableParallel:
        return 1
    
    maxParallel = ProductionLineConfig.maxParallel  // 默认64
    
    // 计算输入能支持多少倍并行
    itemParallel = Integer.MAX_VALUE
    for itemInput in recipe.itemInputs:
        if isCatalyst(itemInput, recipe):
            continue
        total = countTotalItem(inputItems, itemInput)
        itemParallel = min(itemParallel, total / itemInput.count)
    
    fluidParallel = Integer.MAX_VALUE
    for fluidInput in recipe.fluidInputs:
        if isCatalyst(fluidInput, recipe):
            continue
        total = countTotalFluid(inputFluids, fluidInput)
        fluidParallel = min(fluidParallel, total / fluidInput.amount)
    
    return min(maxParallel, itemParallel, fluidParallel)
```

### 6.2 并行时的输出

```
// 并行N倍时：
// - 消耗N倍输入
// - 产出N倍输出
// - 能耗 = euPerTick（不变，GT5并行不增加能耗）
// - 时长 = duration（不变）

// 注意：并行可能导致输出总线/仓溢出
// 需要在completeProcessing中检查输出空间
```

## 七、NBT状态管理

### 7.1 机器主体NBT

```
// MTEProductionLineAggregator保存：
nbt.setString("CurrentRecipe", mCurrentRecipe != null ? mCurrentRecipe.id : "")
nbt.setInteger("Progress", mProgress)
nbt.setInteger("MaxProgress", mMaxProgress)
nbt.setInteger("EUt", mEUt)
nbt.setInteger("CasingTier", mCasingTier)
nbt.setInteger("MachineVoltageTier", mMachineVoltageTier)
```

### 7.2 控制器仓NBT

```
// MTEHatchProductionLineController保存：
nbt.setString("SelectedRecipe", selectedRecipeId)
nbt.setInteger("ActivatedCount", activatedRecipes.size())
nbt.setString("ActivatedRecipe0", "...")
nbt.setString("ActivatedRecipe1", "...")
// ...
```

### 7.3 区块卸载/加载

```
// GT5的MetaTileEntity自动处理NBT保存/加载
// 只需要重写writeToNBT/loadFromNBT即可
// 区块卸载时进度保留，加载后继续
```

## 八、常见坑与解决方案

### 坑1：GT5 RecipeMap不支持催化剂
**问题**：GT5的GTRecipe所有输入都会被消耗，不支持"输入但不消耗"的催化剂。
**解决方案**：
- 方案A（推荐）：完全自定义checkProcessing()，不使用GT5默认的RecipeMap.findRecipe()
- 方案B：催化剂用NBT标记，在processRecipe中跳过消耗
- 方案C：催化剂不作为配方输入，在checkProcessing中额外检查存在性

### 坑2：maxIO限制
**问题**：RecipeMapBuilder.maxIO(int, int, int, int)的参数可能有上限。
**解决方案**：
- 先尝试maxIO(24, 24, 16, 16)
- 如果GT5有硬上限，改用完全自定义匹配（不走RecipeMap）
- 或者：将多个同类物品合并（如所有"粉"合并为一个输入）

### 坑3：输出总线/仓满时的行为
**问题**：如果输出满了，机器应该卡住还是丢失产物？
**解决方案**：GT5标准行为是卡住（不推进进度，不丢失产物）。在completeProcessing中检查输出空间，如果不够就不重置配方，等待有空间。

### 坑4：多方块结构验证
**问题**：5×5×5立方体的验证容易出错（控制器位置、外壳等级、功能方块位置）。
**解决方案**：参考GT5 MTEDistillationTower或MTEBlastFurnace的checkStructure()实现，使用相对坐标遍历。

### 坑5：能量消耗
**问题**：mEUt设置了但没有实际扣能量，机器会空跑。
**解决方案**：在onPostTick中调用getBaseMetaTileEntity().decreaseStoredEnergyUnits(mEUt, false)，或使用GT5标准的getEnergyInput()/decreaseStoredEnergy()方法。

### 坑6：物品匹配
**问题**：GT5物品匹配需要考虑OreDict、NBT、meta值。
**解决方案**：使用GT5的OrePrefixItem或ItemStack.isItemEqual()方法，不要自己写字符串比较。

### 坑7：流体匹配
**问题**：流体注册名可能有别名（如water vs distilled_water）。
**解决方案**：使用FluidRegistry.getFluid()获取流体对象，然后用FluidStack.isFluidEqual()比较，不要用字符串比较。

### 坑8：GUI打开方式
**问题**：MTEHatch的GUI打开方式与普通方块不同。
**解决方案**：参考项目中其他MTEHatch的实现（如MTEHatchAdaptiveNetwork），使用player.openGui()或ModularUI的返回方式。

### 坑9：NBT同步
**问题**：服务器端的激活状态需要同步到客户端显示。
**解决方案**：使用GT5的getDescriptionPacket()/onDataPacket()或Container的detectAndSendChanges()方法。

### 坑10：配方加载时机
**问题**：JSON加载太早会导致Material/Item未注册，太晚会导致NEI不显示。
**解决方案**：在FMLInitializationEvent中加载JSON（此时Material和Item已注册），在postInit中注册到RecipeMap（此时所有mod的配方已加载）。

## 九、关键代码位置参考

| 功能 | 参考文件 | 参考方法 |
|------|----------|----------|
| 多方块结构验证 | GT5 MTEDistillationTower.java | checkStructure() |
| 外壳等级限制 | GT5 MTEBlastFurnace.java | getCasingTier()/checkRecipe() |
| 输入输出总线 | GT5 MTEMultiBlockBase.java | mInputBuses/mOutputBuses |
| 能量消耗 | GT5 MTEMultiBlockBase.java | onPostTick()/getEnergyInput() |
| RecipeMap注册 | GT5 RecipeMaps.java | 所有静态RecipeMap定义 |
| GTRecipe构建 | GT5 GTRecipe.java | 构造函数/addRecipe() |
| 自定义Hatch | 项目 MTEHatchAdaptiveNetwork.java | 整个文件 |
| ModularUI GUI | 项目 AdaptiveNetTerminal.java | buildUI()/initWidgets() |
| NBT保存 | GT5 MetaTileEntity.java | writeToNBT/loadFromNBT |

---

*生成时间：2026-09-02 | 基于GT5 1.7.10多方块标准模式+用户确认的设计规则*
