# GT-Shanhai 模组技术分析文档

> 基于源码版本 3.0.2，深入代码结构与实现方式的完整技术文档

---

## 目录

1. [整体架构](#1-整体架构)
2. [原始终焉引擎系统](#2-原始终焉引擎系统)
3. [无线供电系统](#3-无线供电系统)
4. [星律样板供料系统](#4-星律样板供料系统)
5. [虚拟物品供应系统](#5-虚拟物品供应系统)
6. [终焉聚合枢纽（多功能方块）](#6-终焉聚合枢纽多功能方块)
7. [商店与数字货币系统](#7-商店与数字货币系统)
8. [配方修改系统](#8-配方修改系统)
9. [AE2 量子合成系统](#9-ae2-量子合成系统)
10. [命令系统](#10-命令系统)
11. [已注册配方类型](#11-已注册配方类型)

---

## 1. 整体架构

### 1.1 项目结构

```
com.dishanhai.gt_shanhai
├── api/                    # 对外暴露的 API 层
│   ├── recipe/             # 配方类型定义
│   ├── ae/ae2/             # AE2 存储与虚拟单元
│   ├── machine/            # 机器接口（维护绕过、产出倍率等）
│   ├── ModuleLevelCondition.java  # 模块等级配方条件
│   ├── DShanhaiRecipeModifierAPI.java  # 配方修改引擎（1847行）
│   └── TooltipEffectAPI.java  # 特效 tooltip
├── client/                 # 客户端渲染与 GUI
├── command/                # 中英文双命令树（1480行）
├── common/
│   ├── ae2/                # AE2 量子合成 CPU 系统
│   ├── block/              # 方块定义
│   ├── item/               # 物品系统（47个文件，含虚拟物品、SDA、样板）
│   ├── machine/            # 所有多方块机器定义
│   │   ├── primordial/     # 原初引擎与模块（30+模块）
│   │   ├── part/           # 仓室/部件（维护仓、超频仓等）
│   │   ├── wave/           # 波动相关机器
│   │   ├── nebula/         # 星云虹吸
│   │   ├── spacetime/      # 时空波动矩阵
│   │   └── worldline_cracking/  # 世界线裂解
│   ├── misc/               # 杂项功能
│   ├── recipe/             # 配方缓存与数据包
│   └── shop/               # 商店与货币系统（35个文件）
├── config/                 # 配置系统（50+可配置项）
├── integration/            # 外部模组集成
├── jei/                    # JEI 集成
├── mixin/                  # Mixin 注入
├── network/                # 网络数据包（51个文件）
└── util/                   # 工具类
```

### 1.2 核心设计模式

| 模式 | 说明 |
|------|------|
| BigInteger 全场 | 能源值、货币余额、AE2 存储量全部用 BigInteger，支持真正无限规模 |
| 服务端权威 + 客户端快照 | 钱跟人不跟物，客户端用乐观更新保证 UI 响应 |
| Mixin 密集钩入 | 深度修改 GTCEu 配方逻辑、AE2 行为、无线电力注册 |
| Provider 解耦模式 | 商店 AE 网络和无线电力都用注册机制，新机器类型无需改核心代码 |
| 持久化规则 + 内存缓存 | 规则持久化到 JSON，启动时加载到并发集合，同时应用于 JEI 显示和运行时 |
| WeakReference 注册表 | 所有全局注册表使用弱引用，机器卸载时自动回收内存 |
| ThreadLocal 上下文传播 | 编码时的上下文通过 ThreadLocal 传递穿越静态 AE2 API 调用 |

---

## 2. 原始终焉引擎系统

### 2.1 引擎主机 (`PrimordialOmegaEngineMachine`)

**源码位置**: `common/machine/primordial/PrimordialOmegaEngineMachine.java`（494行）

187 层巨构多方块结构，实现 `IModularMachineHost` 接口。

**核心属性**:
- `getMaxVoltage()` = `Long.MAX_VALUE`
- `getMaxParallel()` = `Long.MAX_VALUE`（无限）
- `getAdditionalThread()` = `Integer.MAX_VALUE`

**模块快照化（Copy-on-Write）**:
```java
// 避免每 tick 数千次集合拷贝和锁争用
private volatile Set<IModularMachineModule<...>> moduleSnapshot = Collections.emptySet();

// 每 tick 一次的模块等级聚合（下标 = 模块等级，值 = 该等级堆叠总数）
private volatile long[] moduleLevelCounts = new long[32];
```

**球体渲染风格切换**（鸿蒙微型宇宙 / 中子星），通过 `@DescSynced` 字段同步到客户端。

### 2.2 模块基类 (`PrimordialOmegaEngineModuleBase`)

**源码位置**: `common/machine/primordial/PrimordialOmegaEngineModuleBase.java`（985行）

每个模块自带三个物品槽：

| 物品槽 | 字段 | 功能 |
|--------|------|------|
| 物质模块槽 | `moduleSlot` | 放入 17 种物质模块之一，决定等级（1~17） |
| 线程倍率槽 | `threadBoostSlot` | 放入倍率物品（如寰宇并行超限器 = Long.MAX_VALUE） |
| 额外挂载槽 | `extraMountSlots`（3格） | 暗能量倍增器/湮灭核心/超稳态黑洞种子 |

**物质模块等级表**（17 级）:
```java
// 静态映射：物品ID → 等级
MODULE_LEVELS = {
    "dishanhai:wzrm"              → 1  入门
    "dishanhai:wzjc"              → 2  基础
    "dishanhai:wzcz1"             → 3  物质推演
    "dishanhai:wzxc"              → 4  虚像
    "dishanhai:wzsb"              → 5  嬗变
    "dishanhai:wzax"              → 6  暗星
    "dishanhai:wzcz2"             → 7  物质重组
    "dishanhai:wzqs"              → 8  虚数跃迁
    "dishanhai:wzgl"              → 9  归零
    "dishanhai:wzhy"              → 10 巅峰
    "dishanhai:wzsw"              → 11 升维
    "dishanhai:wzcx"              → 12 超限
    "dishanhai:wzdf"              → 13 混沌
    "dishanhai:wzyh"              → 14 永恒
    "dishanhai:wzcz3"             → 15 物质创造
    "dishanhai:reality_anchor_module" → 16 现实锚点
    "dishanhai:create_mk"         → 17 创始现实修改
}
```

**额外挂载效果**:
- 暗能量倍增器：配方 EU 消耗 ×0.5
- 湮灭核心：配方耗时 ×0.1，但有 1% 产物湮灭风险（模块等级 ≥ 15 时风险归零）
- 超稳态黑洞种子：输出端可写时吞噬溢出产物

**自动连接主机机制**:
```java
private boolean tryConnectToHost() {
    BlockPos[] positions = AntichristPosHelper.INSTANCE
        .calculatePossibleHostPositions(getPos(), getFrontFacing());
    for (BlockPos pos : positions) {
        // 检查目标方块是否为已成型的引擎主机
        if (machine instanceof PrimordialOmegaEngineMachine engine && engine.isFormed()) {
            engine.addModule(this);
            setHost(engine);
            return true;
        }
    }
    return false;
}
```

### 2.3 配方逻辑 (`PrimordialModuleRecipeLogic`)

**源码位置**: `common/machine/primordial/PrimordialModuleRecipeLogic.java`（646行）

**模块等级门控**:
```java
// IdentityHashMap 缓存，避免每次都查静态注册表
private final IdentityHashMap<GTRecipe, Long> moduleConditionTrueCache;
private final IdentityHashMap<GTRecipe, CachedModuleConditionFailure> moduleConditionFalseCache;

private boolean checkModuleCondition(GTRecipe recipe) {
    // 1. 优先从静态注册表按完整配方 ID 精确查询
    List<ModuleLevelCondition> staticReqs = ModuleLevelCondition.getRequirements(recipeId);
    if (staticReqs != null) {
        for (var mlc : staticReqs) {
            if (!mlc.checkModuleLevel(machine)) {
                return false; // 模块等级不足
            }
        }
        return true;
    }
    // 2. 回退：检查 recipe.conditions
    // ...
}
```

**独立并行计算**（每个配方独立吃满自己的上限，互不挤占）:
```java
@Override
protected ParallelData calculateParallels() {
    Set<GTRecipe> recipes = lookupRecipeIterator();
    long totalParallel = getTotalParallelLimit();
    long[] parallels = new long[recipes.size()];
    int index = 0;
    for (GTRecipe recipe : recipes) {
        MatchableScaledRecipe matchable = findMaxMatchableScaledRecipe(recipe, totalParallel);
        if (matchable != null) {
            parallels[index] = matchable.parallel;
            index++;
        }
    }
    // 每个配方独立计算，不共享并行池
    return RecipeCalculationHelper.INSTANCE.getFinalParallelData(...);
}
```

**二分搜索最优并行**:
```java
static long findHighestMatchableParallel(long requestedParallel, LongPredicate canMatch) {
    if (canMatch.test(requestedParallel)) return requestedParallel;
    // 二分法找最大可匹配并行数
    long low = 1L, high = requestedParallel - 2L, best = 0L;
    while (low <= high) {
        long middle = low + ((high - low) >>> 1);
        if (canMatch.test(middle)) { best = middle; low = middle + 1; }
        else { high = middle - 1; }
    }
    return best;
}
```

### 2.4 已注册的原初模块（约 30+）

| 模块名 | 源码文件 | 配方类型 |
|--------|---------|---------|
| 质能核心 | `PrimordialMassEnergyCore.java` | 纯 Tick 驱动发电（不走配方系统） |
| 薪火裂解窑 | `PrimordialFlameCrackingKiln.java` | 热解/裂化/土高炉/熔岩炉/焦炉（5合1） |
| 生物核心 | `PrimordialBiologicalCore.java` | 生物模拟/温室/培养缸/浮游选矿 |
| 混沌蜉蝣炉 | `PrimordialChaoticEphemeral...java` | 蜉蝣选矿/湿法研磨/天基矿/集成矿/虚空采矿 |
| 因果编织矩阵 | `PrimordialCausalWeavingMatrix.java` | 原初因果编织 |
| 奇点反演核心 | `PrimordialSingularityInversionCore.java` | 原初奇点反演 |
| 世界碎片采集器 | `PrimordialWorldFragmentsCollector.java` | 世界碎片采集 |
| 装配线模块 | `PrimordialAssemblyLineModule.java` | 装配线/电路装配线/组件装配线 |
| 临界处理模块 | `PrimordialCriticalProcessingModule.java` | 极限工业加工 |
| 多维爆破核心 | `PrimordialMultidimensionalImplosionCore.java` | 维度间爆破合成 |
| 超临界物质核心 | `PrimordialSupercriticalMatter...java` | 超高能态物质制造 |
| 宇宙反应堆 | `PrimordialCosmicReactor.java` | 宇宙级能源生产 |
| 分子裂隙核心 | `PrimordialMolecularRiftCore.java` | 分子级精细加工 |
| 天穹装配核心 | `PrimordialTianqiongAssemblyCore.java` | 天文级零件组装 |
| 永恒冶炼炉 | `PrimordialEternalSmeltingFurnace.java` | 无损物质冶炼 |
| 物质重组核心 | `PrimordialMatterRecombinatorCore.java` | 原初物质重组 |
| 分歧发生器 | `PrimordialDivergenceGenerator.java` | 世线震荡/星际物质/原初能量 |
| 物质铸造机 | `PrimordialMatterCaster.java` | 光子分离/物质模块铸造/物质锻造 |
| 反熵冷凝核心 | `PrimordialAntiEntropyCondensationCore.java` | 反熵冷凝/真空冷冻/等离子冷凝 |
| 深渊精炼厂 | `PrimordialAbyssalRefinery.java` | 7合1流体处理 |
| 宇宙起源核心 | `PrimordialCosmicOriginCore.java` | 6合1宇宙资源 |
| 六源核心 | `PrimordialSixfoldResourceCore.java` | 6合1资源采集 |
| 万象增殖核心 | `PrimordialMyriadProliferationCore.java` | 万象衍生配方 |
| 太虚熔炉 | `TaixuSmeltingFurnace.java` | 太虚冶炼 |
| 世界线裂解中枢 | `WorldlineCrackingHubMachine.java` | 世界线操作 |

---

## 3. 无线供电系统

### 3.1 发电：质能核心 (`PrimordialMassEnergyCore`)

**源码位置**: `common/machine/primordial/PrimordialMassEnergyCore.java`（377行）

纯 Tick 驱动，不走配方系统。

**产电公式**:
```
EU/t = 当前并行值 × EU_PER_TIER_UNIT (1,048,576 = 2²⁰)
```

**物质模块并行值映射**:
| 物品 | 并行值 | EU/t |
|------|--------|------|
| `wzrm` | 128 | 134,217,728 |
| `wzjc` | 256 | 268,435,456 |
| `wzsb` | 2,048 | 2,147,483,648 |
| `wzgl` | 524,288 | 549,755,813,888 |
| `wzyh` | 2,147,483,647 | ~2.25×10¹⁵ |
| `wzcz3` | 4,611,686,018,427,387,903 | ~4.84×10²⁴ |
| `create_mk` | Long.MAX_VALUE | ~1.07×10²⁵ |

### 3.2 三种分配模式

```java
public static final int MODE_GRID_ONLY = 0;       // 100% 写入无线电网
public static final int MODE_HALF_BROADCAST = 1;   // 50% 广播 + 50% 电网（默认）
public static final int MODE_BROADCAST_ONLY = 2;   // 100% 广播，余量回流电网
```

**广播机制**:
```java
private long broadcastEuToTerminals(long euToBroadcast) {
    // 1. 从 DShanhaiWirelessPowerTerminalSavedData 获取所有已注册终端
    List<TerminalEntry> terminals = DShanhaiWirelessPowerTerminalSavedData.get(level).getAll();
    // 2. 均分广播预算
    long sharePerTerminal = euToBroadcast / terminals.size();
    // 3. 对每个终端跨维度解析 BlockPos
    for (TerminalEntry entry : terminals) {
        Level targetLevel = getServer().getLevel(entry.dimension());
        BlockEntity be = targetLevel.getBlockEntity(entry.pos());
        // 4. 查询 IEnergyContainer 的输入电压和电流
        long voltage = energyContainer.getInputVoltage();
        long amps = Math.min(sharePerTerminal / voltage, energyContainer.getInputAmperage());
        // 5. 注入能量
        energyContainer.acceptEnergyFromNetwork(voltage, amps);
    }
    // 6. 返回未使用的余量（回流电网）
    return unspentRemainder;
}
```

**关键保证**: 在所有三种模式下，广播目标无法吸收的 EU **始终**写入无线电网，零损耗。

### 3.3 自动注册 (`DShanhaiWirelessPowerTerminalSavedData`)

通过 `DShanhaiAutoPowerRegistryMixin` 注入到 `MetaMachineBlockEntity`：

```java
// 任何 GTCEu 机器加载到世界时自动注册
// 无需放置特殊方块
public class DShanhaiWirelessPowerTerminalSavedData extends SavedData {
    // 存储：(维度字符串, BlockPos) → 注册条目
    private final LinkedHashMap<String, TerminalEntry> terminals;
    
    // 全局实例（keyed by overworld DataStorage）
    public static DShanhaiWirelessPowerTerminalSavedData get(ServerLevel level) {
        return level.getServer().overworld()
            .getDataStorage()
            .computeIfAbsent(DShanhaiWirelessPowerTerminalSavedData::new, "gt_shanhai_wireless_power_terminals");
    }
}
```

---

## 4. 星律样板供料系统

**源码位置**: `common/item/RecipeTypePatternBufferPartMachine.java`（1940行）+ 相关25个类

### 4.1 系统概述

星律样板供料是 GTLCore ME 样板缓冲器的增强版，增加了配方类型过滤、虚拟样板支持、跨维度供料能力。

```
GT 配方
  → 编码 (ShanhaiPatternEncoder / VirtualPatternEncodingHelper)
    → 存储 (RecipeTypePatternBufferPartMachine, 支持虚拟目标)
      → AE2 网络供料 (pushPattern + 虚拟输入处理)
        → 宿主机器执行 (配方类型过滤 + 产出倍率重写)
          → 卡死检测 & 告警 (StellarPatternStuckWatch/Notifier)
```

### 4.2 样板编码阶段

**`ShanhaiPatternEncoder.encode()`** 处理流程:

```java
public ItemStack encode(GTRecipe recipe, ...) {
    for (Content content : recipe.inputs.get(Items)) {
        if (isNonConsumable(content)) {
            // 不消耗物品 → 包装为虚拟提供器
            ItemStack provider = VirtualItemProviderHelper.createBoundProvider(content);
            // 在样板中编码为 1 个虚拟提供器（真实目标在 NBT 中）
            patternInputs.add(new AmountItem(provider, 1));
        } else if (IntCircuitBehaviour.isIntegratedCircuit(content)) {
            // 编程电路 → 保持原样
            patternInputs.add(content);
        } else {
            // 消耗物品 → 正常编码
            patternInputs.add(content);
        }
    }
    // 写入 authoritative 标签，防止未来被启发式重新推断覆盖
    PatternRecipeTypeHelper.writeAuthoritativeRecipeType(patternStack, recipe);
}
```

**虚拟电路处理**:
- 电路不包装为虚拟提供器（保持自身身份）
- 通过 `VirtualPatternBufferSlotState.setVirtualCircuit()` / `getVirtualCircuit()` 缓存
- 执行时由 `SlotCacheManager` 检索，机器无需物理物品即可获得正确电路配置

### 4.3 样板存储 & 虚拟目标注册

**`VirtualPatternBufferSlotState`** — 弱引用键的全局注册表:

```java
// 全局注册表：WeakReference<库存实例> → 虚拟目标映射
private static final List<Entry> VIRTUAL_TARGETS;

public static void addVirtualTarget(Object2LongOpenHashMap<AEKey> inventory, AEKey key, long amount) {
    // 在槽位库存中注册虚拟目标
    // 虚拟目标从合并视图中减去，防止重复计数
}

public static void stripVirtualTargets(Object2LongOpenHashMap<AEKey> inventory, 
                                        Predicate<AEKey> keep) {
    // keep 谓词：PatternNotConsumableFilter::isKeyNotConsumableForActiveRecipe
    // 保留催化剂虚拟目标，移除已消耗的虚拟目标
}
```

### 4.4 AE2 供料 (pushPattern)

```java
// RecipeTypePatternBufferPartMachine.pushPattern()
@Override
public PushResult pushPattern(...) {
    // 1. 验证配方类型与宿主机器匹配
    if (!gtShanhai$slotAllowsRecipe(slot, recipe)) {
        return PushResult.FAIL;
    }
    
    // 2. 处理虚拟输入
    VirtualPatternEncodingHelper.pushPatternInputsIncludingVirtual(
        pattern, machine, virtualTargetSink -> {
            // 虚拟输入不推送，仅报告目标存在性
        });
    
    // 3. 推送消费性输入
    for (Content input : recipe.inputs) {
        if (!isPresenceInput(input)) {
            machine.getCapability(Items).ifPresent(h -> h.handleRecipeInput(input));
        }
    }
    
    // 4. 启动卡死检测
    StellarPatternStuckWatch.schedule(slot, timeoutTicks);
    
    return PushResult.SUCCESS;
}
```

### 4.5 跨维度/无限范围供料

**跨维度**: 通过 AE2 的 P2P 隧道/量子桥实现跨维度网络连接

**无限范围**: AE2 的 CPU 调度逻辑不关心距离，自动分发到网络上任意位置的样板缓冲器

**卡死告警**:
```java
// StellarPatternStuckNotifier.notifyStuck()
public void notifyStuck(ServerPlayer aePlayer) {
    // 向 500 格内所有玩家发送聊天告警
    for (ServerPlayer p : level.players()) {
        if (p.blockPosition().distSqr(pos) < 250000) {
            p.sendSystemMessage(stuckWarning);
        }
    }
    // 向 AE 下单玩家发送可点击传送命令
    aePlayer.sendSystemMessage(Component.literal(
        String.format("[点击传送] /shanhai stellar_tp %s %d %d %d",
            dimension, pos.getX(), pos.getY(), pos.getZ())
    ).withStyle(ChatFormatting.AQUA));
}
```

### 4.6 配方类型验证

```java
// 每个槽位的配方类型标签
String[] patternRecipeTypeIds; // 从样板 NBT 中读取

// 验证槽位是否允许该配方
public boolean gtShanhai$slotAllowsRecipe(int slot, GTRecipe recipe) {
    String recipeTypeId = recipe.recipeType.registryName.toString();
    // 1. 直接匹配
    for (String allowed : patternRecipeTypeIds) {
        if (allowed.equals(recipeTypeId)) return true;
    }
    // 2. 共享搜索集匹配
    return RecipeTypeSharedSearchSets.isSharedWithAny(recipe.recipeType, selectedTypes);
}
```

### 4.7 关键类职责表

| 类 | 职责 |
|----|------|
| `RecipeTypePatternBufferPartMachine` | 核心星律样板缓冲器（1940行），配方类型过滤 |
| `VirtualPatternEncodingHelper` | 虚拟样板编码大脑（1712行），样板重写 |
| `RecipeTypePatternSearchHelper` | 配方收集、虚拟供料预算、激活协调器（1215行） |
| `VirtualPatternBufferSlotState` | 弱引用键虚拟目标注册表 |
| `ShanhaiPatternEncoder` | GT 配方 → AE2 样板编码 |
| `ShanhaiPatternModifier` | 批量缩放样板数量 |
| `PatternBufferClipboard` | 样板缓冲器的复制/剪切/粘贴操作 |
| `AdvancedPatternBoxBehavior` | 增强样板包装箱（可配置容量） |
| `WildcardPatternBridge` | 反射桥接通配符样板模组 |
| `RecipeTypeSharedSearchSets` | 可配置配方类型等价组 |
| `StellarPatternStuckWatch` | 定时卡死检测监控器 |
| `StellarPatternStuckNotifier` | 聊天告警发送器（含传送链接） |

---

## 5. 虚拟物品供应系统

### 5.1 核心问题

GTCEu 配方区分**消耗输入**（被消耗）和**不消耗输入**（催化剂/模具，使用后保留），但 AE2 的样板编码和合成 CPU 把所有输入一视同仁当作消耗品处理。

虚拟物品提供器解决这个阻抗失配：允许样板编码"虚拟"（仅存在性）输入，检查 AE2 网络中的存在性而不实际提取物品。

### 5.2 完整生命周期

**创建 & 绑定** (`VirtualItemProviderItem`):
```java
// 右手持提供器 + 另一只手手持目标物品 → 右键绑定
public InteractionResultHolder<ItemStack> use(...) {
    ItemStack providerStack = player.getItemInHand(hand);
    ItemStack targetStack = player.getItemInHand(otherHand);
    if (!targetStack.isEmpty()) {
        VirtualItemProviderHelper.bindTarget(providerStack, targetStack);
        return InteractionResultHolder.sidedSuccess(providerStack, level.isClientSide());
    }
    // Shift 右键 → 清除绑定
    if (player.isShiftKeyDown()) {
        VirtualItemProviderHelper.clearTarget(providerStack);
    }
}
```

**NBT 结构**（三代兼容）:
```
CompoundTag {
  "targetItem": CompoundTag    // 当前格式：完整序列化 ItemStack
  "m": "namespace"             // GTO 格式：命名空间
  "n": "path"                  // GTO 格式：路径
  "t": CompoundTag             // GTO 格式：NBT 标签
  "marked": boolean            // 是否为编码标记
  "targetCircuit": CompoundTag // 旧格式（已弃用）
}
```

**目标解析** (`VirtualItemProviderHelper.getTarget()`):
```java
public static ItemStack getTarget(ItemStack provider) {
    // 1. 尝试当前格式
    if (nbt.contains("targetItem")) {
        return ItemStack.of(nbt.getCompound("targetItem"));
    }
    // 2. 尝试 GTO 格式
    if (nbt.contains("m") && nbt.contains("n")) {
        ResourceLocation id = new ResourceLocation(nbt.getString("m"), nbt.getString("n"));
        ItemStack target = new ItemStack(ForgeRegistries.ITEMS.getValue(id));
        if (nbt.contains("t")) target.setTag(nbt.getCompound("t"));
        return target;
    }
    // 3. 尝试旧格式
    // ...
}
```

### 5.3 AE2 合成规划阶段

**`VirtualCraftingInitialItemExtractor.extract()`** 替代标准初始物品提取:

```java
public long[] extract(IStorageGrid storage, CraftingPlan plan, ...) {
    // 1. 收集存在性需求
    Map<AEKey, Long> presenceReqs = new HashMap<>();
    for (CraftingJobNode node : plan.getNodes()) {
        for (Input input : node.getPattern().getInputs()) {
            if (input instanceof PresenceInput presence) {
                // 存在性物品不求和，取单次最大需求量
                presenceReqs.merge(presence.getKey(), presence.getAmount(), Math::max);
            }
        }
    }
    
    // 2. 收集可消耗需求
    Map<AEKey, Long> consumableReqs = collectConsumableRequirements(plan);
    
    // 3. 仅提取可消耗物品到 CPU
    for (Map.Entry<AEKey, Long> entry : consumableReqs.entrySet()) {
        storage.extract(entry.getKey(), entry.getValue(), Actionable.MODULATE, src);
    }
    
    // 4. 注册存在性状态
    VirtualCraftingPresenceState.begin(cpuInventory, presenceReqs);
}
```

**`VirtualCraftingPresenceState`** — 全局存在性追踪:
```java
public class VirtualCraftingPresenceState {
    // 全局注册表：WeakReference<ICraftingInventory> → 存在性映射
    private static final List<Entry> ENTRIES;
    
    public static boolean hasPresence(ICraftingInventory inv, AEKey key, long amount) {
        for (Entry entry : ENTRIES) {
            if (entry.inventory.get() == inv) {
                long available = entry.externalPresence.getOrDefault(key, 0L);
                if (available >= amount) return true;
                // 不足部分回退到网络查询
                return inv.extract(key, amount - available, Actionable.SIMULATE, null) >= amount - available;
            }
        }
        return false;
    }
}
```

### 5.4 样板执行阶段

**`VirtualCraftingPatternInputExtractor.extract()`**:

```java
public long extract(AEKey key, long amount, ...) {
    // 检查是否为存在性输入
    if (VirtualPatternEncodingHelper.isPresenceInput(currentPattern, slotIndex)) {
        // 存在性输入 → 检查是否存在
        if (!VirtualCraftingPresenceState.hasPresence(externalInv, key, amount)) {
            // 不存在 → 回滚所有已提取的输入
            rollbackAllExtracted();
            return 0; // 失败
        }
        // 存在 → 跳过提取
        return amount;
    }
    
    // 消耗性输入 → 正常提取
    return externalInv.extract(key, amount, Actionable.MODULATE, src);
}
```

### 5.5 槽位虚拟身份层

**`VirtualPatternBufferSlotState`**:
```java
// 全局注册表
private static final List<Entry> VIRTUAL_TARGETS;
private static final List<CircuitEntry> VIRTUAL_CIRCUITS;

// Entry = WeakReference<库存实例> + 虚拟目标映射
record Entry(WeakReference<Object2LongOpenHashMap<AEKey>> inventoryRef,
             Object2LongOpenHashMap<AEKey> virtualTargets) {}

// 添加虚拟目标
public static void addVirtualTarget(Object2LongOpenHashMap<AEKey> inv, AEKey key, long amt) {
    for (Entry entry : VIRTUAL_TARGETS) {
        if (entry.inventoryRef.get() == inv) {
            entry.virtualTargets.put(key, entry.virtualTargets.getOrDefault(key, 0L) + amt);
            return;
        }
    }
    // 新建条目
    VIRTUAL_TARGETS.add(new Entry(new WeakReference<>(inv), new Object2LongOpenHashMap<>()));
}

// 剥离虚拟目标（保留催化剂）
public static void stripVirtualTargets(Object2LongOpenHashMap<AEKey> inv, 
                                        Predicate<AEKey> keep) {
    for (Entry entry : VIRTUAL_TARGETS) {
        if (entry.inventoryRef.get() == inv) {
            for (var it = entry.virtualTargets.object2LongEntrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (!keep.test(e.getKey())) {
                    // 从实际库存中减去虚拟量
                    inv.put(e.getKey(), inv.getOrDefault(e.getKey(), 0L) - e.getLongValue());
                    it.remove();
                }
            }
        }
    }
}
```

### 5.6 非消耗过滤器 (`PatternNotConsumableFilter`)

```java
// 保护催化剂不被消耗
private static final ThreadLocal<GTRecipe> ACTIVE_RECIPE = new ThreadLocal<>();

public static boolean isKeyNotConsumableForActiveRecipe(AEKey key) {
    GTRecipe recipe = ACTIVE_RECIPE.get();
    if (recipe == null) return false;
    // 检查 key 是否为 chance == 0 的输入
    for (var input : recipe.inputs.get(Items)) {
        if (input.chance == 0 && matchesKey(input, key)) {
            return true;
        }
    }
    return false;
}
```

### 5.7 两种操作模式

| 模式 | 配置值 | 行为 |
|------|--------|------|
| `AE_TARGET_CHECK` | 默认 | AE 下单检查网络中的真实目标物，执行时解成目标物镜像 |
| `SUPPLY_MACHINE` | 备选 | AE 下单检查同网络虚拟物品供应机槽内的目标物 |

---

## 6. 终焉聚合枢纽（多功能方块）

**源码位置**: `common/machine/part/DShanhaiMaintenanceHatchMachine.java`
**物品**: `DShanhaiMaintenanceHatchItem.java` — 显示名"终焉聚合枢纽"

### 6.1 架构概览

一个方块同时实现 **9 个接口** + **2 个 trait**:

```
DShanhaiMaintenanceHatchMachine ("终焉聚合枢纽")
├── IMaintenanceMachine          → 接管宿主维护系统（永远无问题）
├── IMaintenanceBypassPart       → 电压/环境/研究绕过
├── IAutoConfigurationMaintenanceHatch → 配方时长乘数（0.001x ~ 10000x）
├── IParallelHatch               → 并行控制（1 ~ Long.MAX_VALUE）
├── IThreadModifierPart          → 跨配方线程（0 ~ Long.MAX_VALUE）
├── IDataAccessHatch             → 研究绕过
├── IDShanhaiBatchToggle         → 批量处理开关
├── IOutputMultiplierSource      → 产出倍率（1x ~ 5x，可解锁）
├── InfiniteCWUContainer (trait) → 无限 CWU 算力
├── NotifiableEnergyContainer    → 无限 EU 能源（可配置电压等级）
└── 4 个物品槽：
    ├── 模块槽     → 决定时长范围 + 最大并行
    ├── 星阵槽     → 决定线程数（星阵/世线信标）
    ├── 泪滴槽     → 解锁产出倍率（大反冲）
    └── 线程倍率槽 → 线程倍率 / 寰宇并行超限器
```

### 6.2 维护绕过

**`IMaintenanceMachine` 实现**:
```java
@Override
public AutoMaintenance getMaintenanceProblems() {
    return AutoMaintenance.NO_PROBLEMS; // 永远无问题
}

@Override
public boolean isFullAuto() {
    return true; // 始终全自动
}

@Override
public void modifyRecipe(GTRecipe recipe) {
    if (bypassVoltage) {
        // 移除 EU 和 CWU 能力 → 配方零能耗
        recipe.inputs.remove(EURecipeCapability.CAP);
        recipe.tickInputs.remove(EURecipeCapability.CAP);
        recipe.tickInputs.remove(CWURecipeCapability.CAP);
    }
    if (bypassTemperature) {
        // 移除温度要求（先 copy 避免污染原配方）
        recipe.data = recipe.data.copy();
        recipe.data.remove("ebf_temp");
        recipe.data.remove("blastFurnaceTemp");
    }
}
```

**概率绕过**（需放入"时间逆转协议"物品）:
```java
if (bypassChances) {
    for (var output : recipe.outputs.values()) {
        for (var content : output) {
            content.chance = 10000; // 100% 保证输出
        }
    }
    for (var input : recipe.inputs.values()) {
        for (var content : input) {
            content.chance = 0; // 输入永不消耗
        }
    }
}
```

### 6.3 无限能源

```java
// 自定义 NotifiableEnergyContainer
private class InfinityEnergyContainer extends NotifiableEnergyContainer {
    @Override
    public long getEnergyCapacity() { return Long.MAX_VALUE; }
    
    @Override
    public long getEnergyStored() { return Long.MAX_VALUE; }
    
    @Override
    public long getInputVoltage() {
        return energyTier == 0 ? 0 : GTValues.V[energyTier]; // 0-15 级
    }
    
    @Override
    public List<Long> handleRecipeInner(IO io, GTRecipe recipe, List<Long> left, ...) {
        if (bypassVoltage) return null; // 完全绕过 EU 消耗
        return super.handleRecipeInner(io, recipe, left, ...);
    }
}
```

### 6.4 并行控制（17 级模块驱动）

| 模块 | 最大并行 | 时长范围 |
|------|---------|---------|
| wzrm 入门 | 256 | 0.50-2.0x |
| wzjc 基础 | 1,024 | 0.35-5.0x |
| wzcz1 推演 | 2,048 | 0.20-10.0x |
| wzxc 虚像 | 4,096 | 0.16-30.0x |
| wzsb 嬗变 | 8,192 | 0.12-100.0x |
| wzax 暗星 | 12,288 | 0.10-300.0x |
| wzcz2 重组 | 16,384 | 0.08-1000.0x |
| wzqs 虚数 | 65,536 | 0.05-2000.0x |
|wahl 归零 | 524,288 | 0.03-3000.0x |
| wzhy 巅峰 | 1,048,576 | 0.025-3500.0x |
| wzsw 升维 | 2,097,152 | 0.02-4000.0x |
| wzcx 超限 | 268,435,456 | 0.015-5000.0x |
| wzdf 混沌 | 1,073,741,824 | 0.012-5500.0x |
| wzyh 永恒 | 2,147,483,647 | 0.01-6000.0x |
| wzcz3 物质创造 | ~4.6×10¹⁸ | 0.005-8000.0x |
| reality_anchor | ~6.9×10¹⁸ | 0.003-9000.0x |
| create_mk 创始 | Long.MAX_VALUE | 0.001-10000.0x |

### 6.5 寰宇并行超限器

放入线程倍率槽时，并行和线程同时变为 `Integer.MAX_VALUE`:
```java
public boolean hasParallelOverdriver() {
    var stack = threadBoostSlot.storage.getStackInSlot(0);
    var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
    return "dishanhai:universal_parallel_overdriver".equals(id.toString());
}

@Override
public int getCurrentParallel() {
    if (hasParallelOverdriver()) return Integer.MAX_VALUE;
    return (int) Math.min(currentParallel, maxParallel);
}
```

### 6.6 其他相关仓室

| 仓室 | 文件 | 功能 |
|------|------|------|
| `DShanhaiOverclockHatchMachine` | 独立文件 | 超频仓：除数 = 配方加速倍数 |
| `DShanhaiDivergenceEngineMachine` | 独立文件 | 太初分歧引擎：并行子(32×n) + 世线之种(8×n 线程) |
| `LogicalComputeHatchMachine` | 独立文件 | 无限 CWU 算力（可切换绕过开关） |
| `ProgrammableHatchPartMachine` | 独立文件 | 可编程仓：双用输入 + 配方类型过滤 + 虚拟物品支持 |
| `MEDiskHatchPartMachine` | 独立文件 | ME 磁盘仓室：108槽 AE2 存储集成 |
| `CosmicCleanGravityMaintenanceHatch` | 独立文件 | 宇宙洁净维护仓：宇宙级洁净室 + 批量处理 |
| `ReliableMEAsyncOutputPartMachine` | 独立文件 | 异步 ME 输出缓冲 |
| `StarRailMEOutputMatrixPartMachine` | 独立文件 | 星轨 ME 输出矩阵（128-512次/tick） |

---

## 7. 商店与数字货币系统

### 7.1 商店商品配置 (`ShopConfig`)

**源码位置**: `common/shop/ShopConfig.java`（574行）

JSON 驱动：`config/gt_shanhai/shop.json`

```json
{
  "entries": [
    {
      "goods": "minecraft:diamond",
      "count": 1,
      "currency": "dishanhai:dog_coins",
      "price": 4,
      "category": "矿物"
    }
  ]
}
```

**两级分类**: 主分类 + 子分类（"主/子"格式）

**快照发布**: `ShopCatalogSnapshot` 不可变快照 + `ShopCatalogManifestPacket` 广播

### 7.2 购买系统 (`ShopPurchase`)

**5 种奖励模式**:

| 模式 | 方法 | 说明 |
|------|------|------|
| CHOICE | `buyBulkChoice()` | 自选（独立随机数量，仿开箱） |
| RANDOM | `buyBulkRandom()` | 按权重加权随机 |
| ALL | `buyBulkAll()` | 一次性交付全部奖励项 |
| FTBQ | `buyBulkFtbq()` | 直接读取 FTB Quests 奖励表 |
| DEFAULT | `buyBulk()` | 固定商品 |

**分层交付**（防吞币不吞货）:
```java
public static String deliverItems(ServerPlayer player, ItemStack unit,
                                  BigInteger total, boolean aeMode, boolean backpackMode) {
    // 1. AE 网络注入（SIMULATE→MODULATE）
    if (aeMode && ShopAeNetwork.canInjectForPlayer(player, key, total.longValue())) {
        ShopAeNetwork.injectForPlayer(player, key, total.longValue());
        return "ae";
    }
    // 2. 打包超级磁盘阵列（超阈值时）
    if (total.compareTo(threshold) >= 0) {
        packAsSda(player, key, total);
        return "sda";
    }
    // 3. 进背包（兜底）
    long leftover = deliverToInventory(player, unit, total, backpackMode);
    // 4. 装不下的余量再打包 SDA
    if (leftover > 0) packAsSda(player, key, BigInteger.valueOf(leftover));
    return "inventory";
}
```

### 7.3 数字货币系统 (`WalletAccount`)

**双重账本设计**:

```java
public class WalletAccount {
    // 币种余额：各种实体币的数字余额
    Map<ResourceLocation, BigInteger> currencyBalances;
    
    // 星火余额：主要数字货币
    BigInteger digitalBalance;
    
    // 会员等级：-1=无, 0=铜, 1=银, 2=金
    int memberTier;
    
    // 银行存款
    BigInteger bankDeposit;
    long bankDepositLastMs;
    
    // 银行贷款
    BigInteger bankDebt;
    long bankDebtLastMs;
}
```

**银行利息计算** (`ShopBank`):
```java
public static BigInteger accrue(BigInteger principal, long rateBpPerHour, long elapsedMs) {
    long elapsedHours = elapsedMs / 3_600_000L;
    if (elapsedHours <= 0) return BigInteger.ZERO;
    return principal
        .multiply(BigInteger.valueOf(rateBpPerHour))
        .multiply(BigInteger.valueOf(elapsedHours))
        .divide(BigInteger.valueOf(10_000L));
}
```

**充值流程**:
```java
public static long deposit(ServerPlayer player) {
    // 1. 扫描背包中所有被商店接受的货币
    for (int i = 0; i < inv.getContainerSize(); i++) {
        ItemStack s = inv.getItem(i);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (accepted.contains(id)) {
            // 2. 按 1:1 吸入账户余额
            WalletAccountAPI.addCurrency(server, uuid, id, BigInteger.valueOf(s.getCount()));
            s.setCount(0); // 实体币被吸入
            total += s.getCount();
        }
    }
    return total;
}
```

### 7.4 商店 AE 网络集成 (`ShopAeNetwork`)

**Provider 模式解耦**:
```java
public class ShopAeNetwork {
    // 所有注册的 AE 网络提供者
    private static final CopyOnWriteArrayList<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
    
    public interface Provider extends IActionHost {
        boolean isOnline();
        boolean servesPlayer(ServerPlayer player);
        MEStorage storage();
        IGrid grid();
    }
    
    // 严格 SIMULATE→MODULATE 模式防丢物
    public static long injectForPlayer(ServerPlayer player, AEItemKey key, long amount) {
        long simulated = storage.extract(key, amount, Actionable.SIMULATE, src);
        if (simulated >= amount) {
            return storage.insert(key, amount, Actionable.MODULATE, src);
        }
        return 0; // 不够就不注入
    }
}
```

---

## 8. 配方修改系统

**源码位置**: `api/DShanhaiRecipeModifierAPI.java`（1847行）

### 8.1 三种修改规则

| 规则类型 | 持久化文件 | 功能 |
|---------|-----------|------|
| **Strip** (剥离) | `config/gt_shanhai/strip_rules.json` | 从配方输入/输出移除特定物品/流体 |
| **Replace** (替换) | `config/gt_shanhai/replace_rules.json` | 替换配方中的物品/流体 |
| **Delete** (删除) | `config/gt_shanhai/delete_rules.json` | 按正则删除配方 |

### 8.2 应用管线

```java
public GTRecipe apply(GTRecipe recipe) {
    // 1. KubeJS JS 修饰器（最高优先级）
    for (JSRecipeModifier modifier : jsModifiers) {
        recipe = modifier.modify(recipe);
    }
    // 2. 剥离规则
    for (StripEntry strip : stripRules) {
        if (strip.matches(recipe)) {
            strip.apply(recipe); // 从 inputs/outputs 中移除匹配项
        }
    }
    // 3. 替换规则
    for (ReplaceEntry replace : replaceRules) {
        if (replace.matches(recipe)) {
            replace.apply(recipe); // 就地替换
        }
    }
    return recipe;
}
```

### 8.3 配方查找模板系统

```java
// 首次访问时深拷贝所有原始配方
private static final Map<String, List<GTRecipe>> RECIPE_ORIGINALS = new HashMap<>();

public static void updateLookupRecipes() {
    // 1. 从原始模板恢复
    for (var entry : RECIPE_ORIGINALS.entrySet()) {
        List<GTRecipe> restored = deepCopy(entry.getValue());
        // 2. 重新应用所有剥离规则
        for (StripEntry strip : stripRules) {
            restored.forEach(strip::apply);
        }
        // 3. 替换注册表中的配方
        replaceRecipes(entry.getKey(), restored);
    }
}
```

### 8.4 模式缓存失效

```java
// 批量失效系统，防止级联缓存清除
public static void runPatternCacheInvalidationBatch(String reason, Runnable action) {
    PATTERN_CACHE_REVISION.incrementAndGet(); // 全局版本号递增
    action.run(); // 执行批量操作
    // 通知所有注册的模式缓存所有者
    for (WeakReference<Object> ref : patternCacheOwners) {
        Object owner = ref.get();
        if (owner != null) invalidatePatternCache(owner);
    }
}
```

### 8.5 预设系统

```
config/gt_shanhai/presets/
├── default.json
├── gregtech_recipes.json
└── ...
```

```java
// 保存预设
public static void savePreset(String name) {
    JsonObject preset = new JsonObject();
    preset.add("strip", serializeStripRules(stripRules));
    preset.add("replace", serializeReplaceRules(replaceRules));
    preset.add("delete", serializeDeleteRules(deleteRules));
    writeToFile("config/gt_shanhai/presets/" + name + ".json", preset);
}

// 加载预设
public static void loadPreset(String name, boolean replace) {
    if (replace) clearAll(); // 可选清空现有规则
    JsonObject preset = readFromFile("config/gt_shanhai/presets/" + name + ".json");
    stripRules.addAll(deserializeStripRules(preset.get("strip")));
    replaceRules.addAll(deserializeReplaceRules(preset.get("replace")));
    deleteRules.addAll(deserializeDeleteRules(preset.get("delete")));
}
```

---

## 9. AE2 量子合成系统

**源码位置**: `common/ae2/quantum/` 目录

### 9.1 核心类

| 类 | 职责 |
|----|------|
| `QuantumCraftingBlockEntity` | 扩展 `AENetworkBlockEntity`，多方块结构单元 |
| `QuantumCraftingCPUCluster` | 实现 `IAECluster`，管理 CPU 集群 |
| `QuantumCraftingCPU` | 实现 `ICraftingCPU`，带进度追踪 |
| `QuantumCraftingCPULogic` | 核心合成执行逻辑 |
| `QuantumCraftingCPUCalculator` | 多方块结构计算 |
| `QuantumElapsedTimeTracker` | 追踪已用时间 |
| `CraftingPlanOverflowDetector` | 合成计划溢出检测 |
| `CraftingRecursionDetector` | 无限递归检测 |

### 9.2 安全机制

```java
// 溢出检测
public class CraftingPlanOverflowDetector {
    public boolean isOverflow(CraftingPlan plan) {
        // 检测 Long 级别的数量溢出
        // GTL 体量下 AE2 的 long 乘法可能溢出
        return plan.getTotalEnergy() < 0 || plan.getParallelCount() < 0;
    }
}

// 递归检测
public class CraftingRecursionDetector {
    public boolean hasRecursion(CraftingJob job) {
        // 检测循环依赖链
        Set<AEKey> visited = new HashSet<>();
        return checkRecursion(job.getRootNode(), visited);
    }
}
```

---

## 10. 命令系统

**源码位置**: `common/command/DShanhaiCommands.java`（1480行）

双语言命令树：`/shanhai` + `/山海`

### 10.1 英文命令树

| 命令 | 权限 | 功能 |
|------|------|------|
| `shanhai recipe list` | OP 2 | 列出所有配方配置 |
| `shanhai recipe toggle <id>` | OP 2 | 切换配方启用/禁用 |
| `shanhai recipe strip <type> <item> [input\|output]` | OP 2 | 从配方中剥离物品 |
| `shanhai recipe remove <type> <item>` | OP 2 | 删除含指定物品的配方 |
| `shanhai recipe recover [type]` | OP 2 | 清除所有剥离/替换规则 |
| `shanhai recipe reload` | OP 2 | 从文件重载规则 |
| `shanhai recipe replacePattern <type> <regex> <pattern>` | OP 2 | 正则替换配方 ID |
| `shanhai recipe preset save/load/delete/list` | OP 2 | 预设管理 |
| `shanhai gt list` | OP 2 | 列出所有 GT 配方类型 |
| `shanhai gt query <type>` | OP 2 | 按类型查询配方 |
| `shanhai gt search <item>` | OP 2 | 按物品搜索配方 |
| `shanhai materials <machineId>` | OP 2 | 统计多方块材料 |
| `shanhai materials all` | OP 2 | 导出所有多方块材料到 Markdown |
| `shanhai sda export/list/remove` | OP 2 | SDA 导出管理 |
| `shanhai shop` | 无 | 打开商店（需手持钱包） |
| `shanhai shop bank/deposit/withdraw/borrow/repay` | 无 | 银行操作 |
| `shanhai cache stats/reset` | OP 2 | 运行期配方缓存诊断 |
| `shanhai stellar_tp <dim> <x> <y> <z>` | 无 | 传送到星律样板缓冲器 |

### 10.2 中文命令树

完整镜像：`/山海 配方 剥离/恢复/替换/重载/删除配置/删除ID/模式/预设` + `/山海 商店 授权/取消授权/作弊/编辑`

---

## 11. 已注册配方类型

**源码位置**: `api/recipe/DShanhaiRecipeTypes.java`（492行）

共 **35 种** 配方类型 + **36 种** 显示模式

### 11.1 核心配方类型

| 配方类型 | IO 配置 | 说明 |
|---------|---------|------|
| `spacetime_distortion` | 9i/6o 物品, 6i/5o 流体 | 时空扭曲处理 |
| `primordial_biological_core` | 6i/3o 物品, 3i/3o 流体 | 生物核心 |
| `primordial_matter_recombination` | 12i/3o 物品, 6i/3o 流体 | 物质重组 |
| `primordial_causal_weaving` | 12i/3o 物品, 6i/3o 流体 | 因果编织 |
| `primordial_singularity_inversion` | 12i/3o 物品, 6i/3o 流体 | 奇点反演 |
| `chaos_crafting` | 24i/24o 物品, 12i/12o 流体 | 混沌合成（最大 IO） |
| `nine_industrial` | 24i/24o 物品, 12i/12o 流体 | 大明科技聚合 |
| `coin_forge` | 9i/6o 物品, 6i/3o 流体 | 造币锻炉 |
| `matter_module_casting` | 15i/6o 物品, 6i/6o 流体 | 物质模块铸造 |

### 11.2 世界线配方

| 配方类型 | 说明 |
|---------|------|
| `worldline_probability_cracking` | 世界线概率裂解 |
| `worldline_matter_recurrence` | 世界线物质重现 |
| `worldline_sampling` | 世界线采样 |
| `worldline_cutting` | 世界线切割 |
| `worldline_oscillation_collection` | 世界线震荡收集 |
| `high_dimensional_fragment_cutting` | 高维碎片切割 |

### 11.3 黑洞配方

| 配方类型 | 说明 |
|---------|------|
| `black_hole_compressor` | 引力压缩 |
| `black_hole_neutronium_compressor` | 中子素压缩 |
| `black_hole_event_horizon_blast` | 事件视界爆破 |

### 11.4 引力波与星云配方

| 配方类型 | 说明 |
|---------|------|
| `gravitational_wave_production` | 引力波生产 |
| `gravitational_wave_consumption` | 引力波消费 |
| `nebula_siphoning` | 星云虹吸 |
| `interstellar_matter_absorption` | 星际物质吸收 |

---

> **文档版本**: 1.0  
> **基于源码版本**: GT-Shanhai 3.0.2  
> **分析日期**: 2026-08-28  
> **许可证**: LGPL 3.0
