# GTNL Steam Elevator（蒸汽楼梯）提速 / 并行 / 批处理 —— 可配置化设计稿

| 项 | 值 |
|---|---|
| 状态 | **已实施（3.29.0 → 3.30.0 → 3.32.0-fix5）**。实施记录见 `CHANGELOG.md` 决策记录 (85)(86)(88)(95)：`gtnl/SteamElevatorTuning.java` + 6 个 `mixin/gt/MixinSteam*Tuning.java`；当前产物 `build/libs/AE2-QoL-3.32.0-fix5.jar`（1,919,432 B，SHA256 `540BC30362F6BE827A2BBE46F4A174EC5774186F61B188A0BD4CA5CE9B00C125`，已部署）。**3.32.0-fix5 追加第 6 类（屠宰场）**，见 §5.5 与 §7。本文件继续作为**设计稿/数值表/兼容性分析**的单一来源；文内"未实施"字样仅指该段写作时的状态。 |
| 载体 | **AE2-QoL（`ae2_qof`）用 mixin 覆写**，不改 GTNL 源码 |
| 取证基线 | `reference_src_290b3\GT-Not-Leisure-dev-290\GT-Not-Leisure-dev-290`（本文行号标 `[dev-290]`；无 `.git`，commit 不可比） |
| **编译基线（实测）** | **`libs\sciencenotleisure-0.2.7-dev-290.jar`**（原名 `sciencenotleisure-0.2.7-pre3-dev-290.jar`，3.32.0-fix5 改名去掉 `-pre3` 以通过 CI 的"无 prerelease 依赖"检查；`mcmod.info`: `version=0.2.7-pre3-dev-290.37+082483e468`，23,283,878 B，2026-09-20）——**同为 dev-290 线，且是比运行 jar 更新的构建** |
| 行号标注 `[pre3]` | 指 `reference_src_290b3\GT-Not-Leisure-0.2.7-pre3` 源码快照（**其真实版本串无法从树内读出**：`gradle.properties` 里是构建期 token `GRADLETOKEN_VERSION`，见 §0.2） |
| **运行基线（实测）** | **`【私货】sciencenotleisure-0.2.7-rc2-dev-290.14+c9614667ef.jar`**（16,879,392 B，`mcmod.info`: `modid=sciencenotleisure`, `version=0.2.7-rc2-dev-290.14+c9614667ef`, `mcversion=1.7.10`）——**属 dev-290 线，与取证树同线** |
| 实例其它相关产物 | `【私货】gtnotgood-1.4.0.jar`、`gregtech-5.09.54.133.jar`（GT5U） |
| 目标模组 | GT-Not-Leisure，modid **`sciencenotleisure`**（`ScienceNotLeisure.java:32/58` `[dev-290]`） |
| 目标机器 | Steam Elevator（MTE 21155，"Steam Stairway"） |
| 本次范围 | **6 个类 / 9+ 个物品**：SteamOreProcessorModule、SteamApiaryModule、SteamGreenhouseModule、SteamBeeBreedingModule、SteamOilDrillModule I/II/III、**SteamEntityCrusherModule（3.32.0-fix5 追加）** |
| 明确不动 | SteamBeaconModule、SteamMonsterRepellentModule、SteamFlightModule、SteamWeatherModule（4 类） |

> **口径冲突（必须保留可见）**：需求方原始要求"对外发布 + 保持平衡 + 默认值=现值"，随后要求"批处理默认开启（方便自用）"。二者不可兼得：批处理开启 ≈ 变相加速，装上即比现状快。本文按"**默认开启**"实现，并提供 `moduleBatchFollowsMachineButton` 逃生阀（默认 `false`）与 README 明示义务；对外发布推荐改 `true`（见 §5）。

---

## 0. 运行产物实测（本机实例，`javap` 逐个验证）

**为什么要做这一步**：mixin 是**对着运行时加载的 jar** 解析注入点的；参考源码只决定"编译器能否接受 `@Mixin(value = X.class)`"与"描述符怎么写"。因此下面的签名清单是"能否实施"的硬证据。

实例：`E:\wzt\MC\PL genmulu\GT_New_Horizons_2.9.0-beta-3_Java_17-26\.minecraft\mods`

| 注入目标 | 运行 jar 中实测到的签名 | 结果 |
|---|---|---|
| 5 个模块 + Elevator + Base 的类是否存在 | `com.science.gtnl.common.machine.multiblock.module.steamElevator.{SteamOreProcessorModule, SteamApiaryModule, SteamGreenhouseModule, SteamBeeBreedingModule, SteamOilDrillModule, SteamElevator, SteamElevatorModuleBase}` | ✅ 同包同名 |
| 5 个模块 + Elevator 的 `checkProcessing` | `public gregtech.api.recipe.check.CheckRecipeResult checkProcessing();` | ✅ 描述符一致 |
| Base 的缓冲与消耗 | `public long steamBufferSize`（+ Lombok `getSteamBufferSize()/setSteamBufferSize(long)`）、`public boolean onRunningTick(net.minecraft.item.ItemStack)`、`public boolean tryConsumeSteam(int)`、`public long maxEUStore()`、`public int getTierRecipes()` | ✅ |
| OreProcessor 的并行与时长 | `public int getMaxParallelRecipes()`、`private static int getRecipeTickTime(...ProcessingMode)` | ✅ |
| Greenhouse 的专属取值 | `public long getIndustrialFarmEUt()`、`public boolean supportsSteamOC()` | ✅ |
| Elevator | `public void onPostTick(IGregTechTileEntity, long)`、`public long maxEUStore()`、`public boolean supportsSteamOC()` | ✅ |
| GT5U（`gregtech-5.09.54.133.jar`）状态字段 | `public int mMaxProgresstime`、`public net.minecraft.item.ItemStack[] mOutputItems`、`public net.minecraftforge.fml...FluidStack[] mOutputFluids` | ✅ |
| GT5U 电力字段 | `public long lEUt`（声明在 `MTEExtendedPowerMultiBlockBase`，**不在** `MTEMultiBlockBase`） | ✅ |
| GT5U 批处理接口 | `protected boolean batchMode`、`public boolean isBatchModeEnabled()`、`public void setBatchMode(boolean)`、`protected int getMaxBatchSize()` | ✅ |

由此得到的两条**已验证**结论（原为设计选择，现为实测通过）：

1. `moduleBatchFollowsMachineButton` 走 **public 的 `isBatchModeEnabled()`**（而非 `@Shadow` 那个 `protected batchMode`）是可行且更稳的路径。
2. `processingLogic` 在运行产物里是 **`protected final`** ⇒ 将来若确需 `@Shadow` 它，**必须带 `@Final`**；本设计刻意不 shadow 它，正是为了避开这条。

### 0.1 编译/运行两 jar 的关系（**已实测，推翻早期口径**）

早期按项目记忆把编译基线记成"GTNL 0.2.7-pre3（旧线）"，实测后修正为：

| 角色 | 实体 | 版本串 | 构建日期 |
|---|---|---|---|
| 编译（AE2-QoL `libs/`） | `sciencenotleisure-0.2.7-pre3-dev-290.jar` | `0.2.7-pre3-dev-290.37+082483e468` | 2026-09-20 |
| 运行（实例 `mods/`） | `【私货】sciencenotleisure-0.2.7-rc2-dev-290.14+c9614667ef.jar` | `0.2.7-rc2-dev-290.14+c9614667ef` | — |

结论：
1. 两者**同属 dev-290 线**，是同一分支的两个构建（`.37` 编译 / `.14` 运行），**不是"老版本 pre3 vs 新版本 dev-290"**。
2. 抽样三个类（OreProcessor / Apiary / ElevatorModuleBase）在**两个 jar 里逐一 javap 得到的成员签名集完全一致**（含 `checkProcessing()`、`getMaxParallelRecipes()`、`getRecipeTickTime(ProcessingMode)`、`steamBufferSize`、`maxEUStore()`、`onRunningTick`、`tryConsumeSteam`、`getTierRecipes()`）。
3. ⇒ 描述符不会漂移，`@Inject(RETURN)` 可按名匹配；原"跨版本组合"担忧基本消解。
4. ⚠️ **注意构建号方向**：**编译构建（.37）比运行构建（.14）更新** ⇒ 编译期可见、运行期可能不存在的成员是潜在坑。本设计的对策正是**只引用在"运行 jar"里逐一验证过的成员**（§0 表），实施时不得引用仅编译 jar 有的新 API。
5. 可选优化（需另行批准）：把 `libs/` 的编译 jar 换成与运行一致的 `.14`，从根上消除"编译期有、运行期无"这类风险；代价是要顺带检查 AE2-QoL 现有 GTNL 相关代码是否引用了 `.14` 之后才新增的成员。

### 0.2 仍未验证的边界（诚实登记）

- 运行 jar 的 commit `c9614667ef` 与本地 `dev-290` 快照**是否同一 commit 无法比对**：快照无 `.git`，`gradle.properties` 里的版本是构建期 token（`GRADLETOKEN_VERSION`），没有静态版本串。
- 两份源码快照（`...-0.2.7-pre3` / `...-dev-290`）的**版本串同样读不出**；但由 §0.1 的 jar 证据可知 GTNL 的版本串形如 `<base>-dev-290.N+<hash>`（`base` 可能是 `pre3` 或 `rc2`），故这两份树**很可能都是 dev-290 线的快照**——若是，则 §7.1 的 diff 正是"同一分支的小更新"，比"跨大版本"更具参考性。
- 影响面：**仅限"非机制细节"**（某个 tooltip、某个 `ProcessingMode` 分支等）。既然注入点与字段都已验证，风险低，且由 R1（读运行期值）+ R3（自检/指纹）覆盖。
- 若需彻底消除：从上游取 `c9614667ef` 的源码替换取证树，或对目标类做逐成员全量比对。

---

## 1. 现状取证（只读结论）

### 1.1 能量链

```
蒸汽（4 种，效率 1 / 10 / 50 / 1000 EU per L）
  │  Elevator.onRunningTick → consumeBestAvailableSteam()               每 tick   [dev-290] SteamElevator.java:199-202, 299-327
  ▼
控制器 EU 缓冲 = 256,000,000                                            [dev-290] :663-666
  │  每 20 tick 分配：perModuleEnergy = getEUVar() / 模块数              [dev-290] :157-185
  ▼
模块 EU 缓冲 = steamBufferSize = 640,000 × 2^tier                       [dev-290] SteamElevatorModuleBase.java:49/55
  │  module.onRunningTick: aSteamVal = (-lEUt × 10000) / max(1000, mEfficiency)   每 tick   :133-146
  ▼
模块扣自己的缓冲（tryConsumeSteam；严格 `>`）；见底 → POWER_LOSS          :159-165
```

推论：
1. **模块的"蒸汽容量"就是它的 EU 缓冲**（`getTotalSteamCapacityLong()`→`maxEUStore()`，`:148-156`）。
2. **模块自带的蒸汽仓不参与供能**（无任何路径把仓内蒸汽转 EU），只有控制器给模块充能。
3. 普通蒸汽下 **1 EU = 1 L**；过热/致密超临界/压缩时分别 ÷10 / ÷50 / ÷1000。
4. 绝大多数模块把 `mEfficiency` 写死 10000 ⇒ **蒸汽/t = |lEUt|**。

### 1.2 蒸汽类型与可用输入仓

| 类型 | EU/L | 无线网络可用 | 证据 |
|---|---|---|---|
| Steam | 1 | ✅ | `SteamTypes.java:21` `[dev-290]` |
| Superheated Steam | 10 | ✅ | `:22-23` |
| Dense Supercritical Steam | 50 | ✅ | `:24` |
| Compressed Steam | 1000 | ❌ | `:25-26` |

| 仓 | MTE ID | 容量 | 限制 |
|---|---|---|---|
| GT++ Steam Hatch | 31040 | 64,000 L | 只锁普通蒸汽，**只允许 1 个**（`GregtechCustomHatches.java:137-146`、`MTESteamMultiBlockBase.java:328-335/586-590`） |
| GTNL Big Steam Input Hatch | 22518 | 4,096,000 L | 4 种蒸汽，只允许 1 个（`MachineLoader.java:1918-1927`、`SteamMultiMachineBase.java:1681-1685`） |
| GTNL Pipeless Steam Hatch | 22576 | 8,000,000 L | 从团队池补满，按 efficiency 扣池（`MachineLoader.java:2063-2069`、`WirelessSteamEnergyHatch.java:51-58/179-199`） |
| GTNL Pipeless **Jetstream** Hatch | 22578 | Integer.MAX_VALUE | **不在结构登记的 hatchId 列表**（只登记 22576）；但结构校验走 `mAdder`（`HatchElementBuilder.java:542-549`），手工放置仍可能被接受 → [需实测] |

### 1.3 本次 5 个类的关键数值

| 类 | tier | 缓冲 EU(=L) | 时长(tick) | 蒸汽/t | 并行 | 批处理是否有效 |
|---|---|---|---|---|---|---|
| SteamOreProcessorModule | 8 | 163,840,000 | 20~640（7 种模式） | `128 × 2^电路 × 并行` | `8 × 2^电路`（≤512） | ❌ 手写 `checkProcessing` |
| SteamApiaryModule | 6 | 40,960,000 | 6000 | `2048 × 槽位`（槽 8~128） | 1（"并行"=蜂槽数） | ❌ |
| SteamGreenhouseModule | 5 | 20,480,000 | 100 | 8192 | 1 | ❌ |
| SteamBeeBreedingModule | 8 | 163,840,000 | 12000 | 32768 | 1 | ❌ |
| SteamOilDrillModule I/II/III | 2/3/4 | 2.56M / 5.12M / 10.24M | `1200/(tier-1)` = 1200/600/400 | `VP[tier]` = 120/480/1920 | 1 | ❌ |

关键行号（`[pre3]` = AE2-QoL 编译基线；`[dev-290]` = 取证树）：

| 目标 | `[pre3]` | `[dev-290]` |
|---|---|---|
| `MAX_PARA = 8`（**final static，编译期内联**） | SteamOreProcessorModule.java:58 | :58 |
| `RECIPE_EUT = 128` | :59 | :59 |
| OreProcessor `getMaxParallelRecipes()` | :110-111 | :110-112 |
| OreProcessor 内部 `maxParallel` / 水 / 润滑剂 | :143 / :167-168 / :267-268 | :143 / :167-168 / :267-268 |
| OreProcessor `mMaxProgresstime` / `lEUt` | :273 / :274-277 | :273 / :274-277 |
| OreProcessor `getRecipeTickTime()` | :734 | :735-745 |
| Apiary `lEUt` / `mMaxProgresstime` | :356 / :359 | :356 / :359 |
| Apiary 槽位 `8 << min(4, clamp(oc))` | :178 | :178 |
| Apiary 蜂王浆/槽 = 40 | :333 | :333 |
| Apiary `BASE_DROP_ACCELERATION = 3200` | :117 | :117 |
| Greenhouse `getIndustrialFarmEUt()` | :604 | :604-606 |
| Greenhouse `supportsSteamOC()=false` | :634 | :634-636 |
| BeeBreeding `lEUt = -V[6]` / `mMaxProgresstime = 12000` | :72 / :75 | :72 / :75 |
| BeeBreeding 蜂王浆 `64 × 2` | :66-67 | :66-67 |
| OilDrill `lEUt = VP[mTier]` / 时长 | :60 / :61 | :60 / :61 |
| 模块基类 `steamBufferSize = 640000 * (1L << aTier)` | :49/:55 | :49/:55 |
| 模块基类消耗公式 / `tryConsumeSteam` | :136-146 / :159-165 | :133-146 / :159-165 |
| 模块基类 `maxEUStore()` / `getTierRecipes()=14` | :201-203 / :205-208 | :201-203 / :205-208 |
| Elevator `aTick % 20` / `maxEUStore()=256000000` | SteamElevator.java:157 / :664 | :158 / :665 |
| Elevator `supportsSteamOC()=false` | :462 | :462-465 |

---

## 2. 载体设计（mixin，不改 GTNL 源码）

### 2.1 为什么不能往 GTNL 的 `MainConfig` 里加配置

1. GTNL 用的是 GTNHLib `@Config` 反射式配置（`MainConfig.java:18-19`），**嵌套静态类**结构（`:205-229`）。Mixin 无法新增嵌套类，只能往既有类里加字段——而 `@Config` 的字段发现发生在配置加载期，与 mixin 应用顺序耦合，**行为不可预测**。
2. 更重要的是版本面：往别人的配置类里塞字段，等于把"我们对 GTNL 的假设"写进对方的序列化面，GTNL 升级即可能冲突。
3. **结论**：新配置一律挂 **AE2-QoL 自己**。建议**独立配置文件**（例如 `config/ae2_qof/gtnl_steam_elevator.json`）而不是塞进 `Config.settings.json`——理由见 §2.2。

### 2.2 配置落点（建议）

AE2-QoL 现有 `Config` 是**手写 JSON**（`settings.json`，`writeFile(...)` 参数化写入 + `reload()` 解析，`Config.java:95-133/138+`）。往里加 ~20 个键要改 `writeFile` 的长参数列表与解析分支，**回归面大**。

建议新增小类：`com.wztwzt.ae2_qof.gtnl.SteamElevatorTuning`，自读 `config/ae2_qof/gtnl_steam_elevator.json`：
- 默认文件不存在时**不创建**，全部取默认值（= 兼容既有安装）；
- 只在 GTNL 存在时才注册/加载（`Loader.isModLoaded("sciencenotleisure")`）；
- 所有数值在**读取处夹紧**（防呆 + 防手改坏）；
- 变更加热加载（与 `Config` 的 1s 轮询风格一致）以免每次改都要重启。

### 2.3 混入手段的选择（**关键**）

| 手段 | 适用 | 风险 |
|---|---|---|
| **A. `@Inject(at = @At("RETURN"))` 后处理**（推荐） | 在 `checkProcessing()` 返回后修正 `mMaxProgresstime` / `lEUt` / 产出堆叠 | 需要方法内局部变量时拿不到 → 用"方法外可见状态"按上界夹紧（见 §2.5） |
| B. `@ModifyConstant` 改字面量 | 如 `aTick % 20` | **版本漂移 = 注入失败**；`require=1` 则崩、`require=0` 则静默失效（本工作区已踩过坑 5/6） |
| C. `@Overwrite` 整方法替换 | 想完全掌控逻辑 | 静默丢失上游修复；每次 GTNL 升级都要人工比对；**不建议大面积使用** |
| D. 改 `final static` 常量 | ❌ **不可行** | `MAX_PARA` 在 `[pre3]` 是 `public static final int`（:58）⇒ **编译期内联**，运行期改不动 |

因此设计纪律：
1. **倍率一律作用在运行期算出的实例状态/返回值上**，不碰常量；
2. 常量类注入只在**无法回避**时使用（本文只有 Elevator 的 `aTick % 20`），且 `require = 1`（**宁可崩，不可静默**），并在 CHANGELOG 记录该注入点；
3. 每个注入器都要在"生效/未生效"两条路径打日志。

### 2.4 可选依赖守卫

- AE2-QoL 现有 GTNL mixin 的先例：`@Mixin(value = SuperCraftingInputHatchME.class, remap = false)`（`MixinSuperCraftingInputHatchMEWildcard.java:51`），配置级 `"required": false`（`mixins.ae2_qof.json:2`），注释明确"GTNL 为运行时可选依赖（compileOnly），缺失时 mixin 静默跳过"。
- 本设计沿用：新 mixin **放在 common 段**（`mixins.ae2_qof.json` 的 `mixins` 数组，服务端也要生效，**不要**放 `client`）。
- **不做版本白名单**（需求方定案）：改为**注入自检 + 双级行为指纹**（见 §7.4 R3），效果等价于白名单但更强——不依赖版本号，能同时发现"版本号没变但代码变了"和"版本号变了但代码没变"两种情况。
  - 背景：类缺失时 mixin 会跳过（受 `"required": false` 保护），但**方法名/描述符不匹配时的行为由 `require` 决定**；本方案统一 `require = 0` ⇒ 永不崩，代价是必须自己把"失效"变得可见。

### 2.5 批处理算法（与 GT 原生语义对齐）

GT 原生批处理语义（`GTNLParallelHelper.java:39/658-675`）：一次操作多跑若干份，**时长按比例拉长但封顶**（`MAX_BATCH_MODE_TICK_TIME = 128`），蒸汽/输入/产出按份数放大。

本设计把这套语义搬到"手写 `checkProcessing()` 的模块"上：

> **R1（升级兼容硬规则）**：`singleDurationTicks` **不得硬编码**（不能查表写 6000/10000/12000），必须在 `@Inject(RETURN)` 处**读回模块自己刚写入的值**（`int single = self.mMaxProgresstime;`）。这样上游把 6000 改成 3000 时本方案自动跟随，无需改代码。见 §7。

```java
// 建议放在 SteamElevatorTuning（纯静态工具，不依赖 mixin）
static int batchCount(int singleDurationTicks, int affordableByInputs) {
    if (!cfg.enableModuleBatchProcessing) return 1;
    if (cfg.moduleBatchFollowsMachineButton && !/* 机器自身 batchMode */false) return 1;   // 见 §3 注
    int size = clamp(cfg.moduleBatchSize, 1, 128);
    int cap  = clamp(cfg.maxBatchDurationTicks, 1, 512);
    if (singleDurationTicks <= 0 || singleDurationTicks >= cap) return 1;   // 对齐 GT：单份时长≥封顶就不批处理
    int byTime  = Math.max(1, cap / singleDurationTicks);
    int byInput = Math.max(1, affordableByInputs);
    // 注意：这里**不得**并入 moduleMaxParallel。GT 的语义是 maxParallel × batchModifier（并行预算是被批处理放大的，
    // 不是被它限制的，见 GTNLParallelHelper:476-478）；把 moduleMaxParallel(=1) 并进来会让批处理永远 n=1。
    return clamp(Math.min(Math.min(size, byTime), byInput), 1, size);
}
```

> **§2.5 注（批处理的真实性质与适用范围 —— 2026-10 修正）**
> 1. **GT 语义的批处理不是"提速"，是"降开销"**：份数 ×n 的同时时长按比例拉长（`durationMultiplier = 1 + extra/current`，GTNLParallelHelper:673），每 tick 吞吐不变；收益来自减少配方检查/开停机次数。
> 2. **单份时长 ≥ `maxBatchDurationTicks` 的模块根本不触发批处理**（GT 原生判据 `calculator.getDuration() < MAX_BATCH_MODE_TICK_TIME`，GTNLParallelHelper:659）。本设计的 5 个模块里：OreProcessor 除模式 6 外 200~640 t、Apiary 6000 t、BeeBreeding 12000 t、OilDrill 400/600/1200 t **全部不触发**；只有 Greenhouse（100 t，最多 1 份）与 OreProcessor 模式 6（20 t，最多 6 份）会触发。
> 3. 因此"**整体提速**"这个目标**不能靠批处理实现**，只能靠 `moduleSpeedMultiplier`（见 §5.1 效果表）。

static int batchDuration(int singleDurationTicks, int n) {
    return Math.max(1, Math.min(clamp(cfg.maxBatchDurationTicks, 1, 512), singleDurationTicks * n));
}
```

倍率出口（**联动语义**）：

| 配置 | 作用 |
|---|---|
| `moduleSpeedMultiplier = m` | 时长 `× m`（`m < 1` = 更快） |
| `moduleSteamFollowsSpeed = true` | 蒸汽/t 自动 `× 1/m` ⇒ **每操作总蒸汽不变、峰值上升** |
| `moduleSteamCostMultiplier = s` | 再乘 `s`；`s > 1/m` 时总账变贵（想要"提速有代价"就调它，不必改代码） |

| m | 时长 | 蒸汽/t | 每操作总蒸汽 |
|---|---|---|---|
| 1.0（默认） | 原值 | 原值 | **原值** |
| 0.5 | ÷2 | ×2 | **不变** |
| 0.5 且 `s = 2` | ÷2 | ×4 | ×2 |

---

## 3. 技术方案：以 `@Inject(RETURN)` 实现（逐类）

统一做法（每个被点名类一个 mixin 类，`remap = false`）：

```java
@Mixin(value = SteamApiaryModule.class, remap = false)
public abstract class MixinSteamApiaryModuleTuning {

    @Inject(method = "checkProcessing", at = @At("RETURN"), require = 1)
    private void ae2qof$tuneCheckProcessing(CallbackInfoReturnable<CheckRecipeResult> cir) {
        // 只对"成功"结果生效，失败路径原样放行（避免把 NO_RECIPE 变成有产出）
        if (cir.getReturnValue() == null || !cir.getReturnValue().wasSuccessful()) return;
        Self self = (Self) (Object) this;
        if (!SteamElevatorTuning.enabled()) return;                 // GTNL 缺失/开关关 → 直接放行
        int single = self.mMaxProgresstime;                          // R1：读回"模块自己刚设的值"，绝不硬编码
        int n = SteamElevatorTuning.batchCount(single, affordableOf(self));
        self.mMaxProgresstime = SteamElevatorTuning.applyDuration(SteamElevatorTuning.batchDuration(single, n));
        // 批处理**不放大 lEUt**：对齐 GT 语义——GT 是先按 pre-batch 并行算 EUt（GTNLParallelHelper:655-656），
        // 之后才加 extra parallels 并**只拉伸时长**（:658-675 的 durationMultiplier）。写 `lEUt * n` 会多烧 n 倍蒸汽。
        self.lEUt = SteamElevatorTuning.applySteam(self.lEUt);       // lEUt 此时为负
        self.mOutputItems = SteamElevatorTuning.multiplyOutputs(self.mOutputItems, n);
        LOG.info("[AE2QoL] SteamElevator 批处理生效: {} n={} dur={} steam/t={}", ...);   // 防静默失效
    }
}
```

> 注：`moduleBatchFollowsMachineButton` 需要读机器自身的 `batchMode` 字段。`MTEMultiBlockBase.batchMode` 是 `protected`（`MTEMultiBlockBase.java:200`），**不能直接 @Shadow 到非继承类**；正确做法是通过公开 API：`isBatchModeEnabled()`（`MTEMultiBlockBase.java:3341-3342`）。若该方法在编译基线不存在，则以 `@Accessor`（AE2-QoL 已有 `MixinGtnlPatternSlotAccess` 这类访问器先例）读取字段。

### 3.1 逐类实现要点

| 类 | 时长来源（用于 batchDuration 的"单份时长"） | 蒸汽（lEUt） | 产出 ×n | 输入上界 `affordableByInputs` |
|---|---|---|---|---|
| SteamOreProcessorModule | `getRecipeTickTime(mode)`（调用前值不可见 → 用"返回后的 mMaxProgresstime 本位值"也可：先记 `origDur = mMaxProgresstime`） | `lEUt`（已含 128×2^电路×并行） | `mOutputItems` | `min(可处理矿石数, 润滑剂, 水/10)`，按同款 config 除数 |
| SteamApiaryModule | 6000（:359） | `-V[4]×mMaxSlots`（:356） | `mOutputItems`（掉率换算） | `可用蜂王浆 / (40×蜂数)` |
| SteamGreenhouseModule | 100（`IGreenHouse` CYCLE_DURATION） | `-8192`（:604） | `mOutputItems` | 种子位与水 |
| SteamBeeBreedingModule | 12000（:75） | `-V[6]`（:72） | 公主数量 ×n | `可用蜂王浆 / 128` |
| SteamOilDrillModule | `1200/(tier-1)`（:61） | `VP[tier]`（:60） | `mOutputFluids` ×n | **输出仓可容纳份数**（硬性夹紧，见下） |

**OilDrill 专属硬约束**：产出是流体，批处理 ×n 前必须用输出仓余量夹紧 `n`，否则出现"产出溢出静默丢弃"。该夹紧**不做成开关**。

### 3.2 独立于批处理的配置项落点

| 配置 | mixin 落点 | 手段 |
|---|---|---|
| `moduleBufferMultiplier` | `SteamElevatorModuleBase.maxEUStore()`（:201-203 `[dev-290]`） | `@Inject(RETURN, cancellable)` 乘倍率 |
| `elevatorBufferMultiplier` | `SteamElevator.maxEUStore()`（:664 `[pre3]`） | 同上 |
| `elevatorChargeIntervalTicks` | `SteamElevator.onPostTick` 的 `aTick % 20`（:157 `[pre3]`） | `@ModifyConstant(intValue=20)`，`require = 1` |
| `moduleMaxParallel` | 5 个类的 `getMaxParallelRecipes()`（只有 OreProcessor 覆写了；其余继承 GT 默认 1） | `@Inject(RETURN, cancellable)` |
| `maxRecipeOcCount` | `SteamMultiMachineBase.getMaxOcCount clamp`（:1677-1679）与 NBT 读侧（:562） | `@Inject(RETURN, cancellable)`；**NBT 读侧补 clamp** |
| `enableVoltageOverclock` / `maxVoltageOverclocks` | `SteamElevatorModuleBase.getMaxOverclocks()`（继承自 `SteamMultiMachineBase:452-455` 的 0） | `@Inject(RETURN, cancellable)`，**只在本体系的基类上注入**，不动 `SteamMultiMachineBase`（避免波及 30+ 台其他蒸汽机器） |

### 3.3 配置键清单（建议值）

| 键 | 类型 | 默认 | 范围 | 说明 |
|---|---|---|---|---|
| `moduleSpeedMultiplier` | double | 1.0 | [0.01, 100] | 时长倍率，<1 更快 |
| `moduleSteamFollowsSpeed` | boolean | true | — | 蒸汽/t 自动 ×1/m（总账持平） |
| `moduleSteamCostMultiplier` | double | 1.0 | [0.01, 100] | 额外蒸汽倍率（>1/m 才是"提速更贵"） |
| `moduleBufferMultiplier` | double | 1.0 | [0.1, 100] | 模块缓冲 |
| `elevatorBufferMultiplier` | double | 1.0 | [0.1, 100] | 控制器缓冲 |
| `elevatorChargeIntervalTicks` | int | 20 | [1, 200] | 分配周期 |
| `moduleMaxParallel` | int | 1 | [1, 1024] | 并行覆写 |
| `enableModuleBatchProcessing` | boolean | **true**（需求方选择） | — | 批处理总开关 |
| `moduleBatchFollowsMachineButton` | boolean | false | — | true = 额外要求机器自身批处理按钮（对外发布平衡档） |
| `moduleBatchSize` | int | 128 | [1, 128] | 每批份数上限（对齐 GT） |
| `maxBatchDurationTicks` | int | 128 | [1, 512] | 批次时长封顶（对齐 GT） |
| `enableVoltageOverclock` | boolean | false | — | 放开电压超频 |
| `maxVoltageOverclocks` | int | 0 | [0, 32] | 超频层数上限 |
| `maxRecipeOcCount` | int | 4 | [0, 20] | recipeOcCount 上限（含 NBT 夹紧） |
| `oreProcessorMaxParallelPerRecipe` | int | 8 | [1, 512] | **内外部两处必须同源**（:58/:110/:143） |
| `oreProcessorDistilledWaterPerOre` | int | 10 | [0, 1000] | 与并行夹紧同源（:167-168/:267-268） |
| `oreProcessorLubricantPerOre` | int | 1 | [0, 1000] | 同上 |
| `apiaryBaseSlots` | int | 8 | [1, 128] | :97/:178 |
| `apiaryRoyalJellyPerSlot` | int | 40 | [0, 1000] | :333 |
| `apiaryDropAcceleration` | double | 3200 | [1, 1e6] | :117 |
| `greenhouseCycleTicks` | int | 100 | [1, 72000] | `IGreenHouse` CYCLE_DURATION |
| `greenhouseWaterPerCycle` | int | 16000 | [0, 1e6] | GH:553-556 |
| `greenhouseOutputMultiplier` | double | 1.5 | [0.01, 100] | GH:618-626 |
| `beeBreedingCycleTicks` | int | 12000 | [1, 1e6] | :75 |
| `beeBreedingRoyalJellyCost` | int | 128 | [0, 1e6] | :66-67（**必须改成按数量扣**，现在是两次整叠 64） |
| `oilDrillBaseCycleTicks` | int | 1200 | [1, 1e6] | :61 |

---

## 4. 验收清单（实施后必须逐项跑）

1. **默认档（config 文件不存在）**：5 个模块的 `maxProgress` / `energyUsage` / 输出量与改动前一致。**例外**：`enableModuleBatchProcessing` 默认 true 会带来吞吐提升 —— 这正是 §5 的公开义务。
2. **平衡档**：`moduleBatchFollowsMachineButton = true` 且机器按钮未按 ⇒ 与改动前**逐位一致**（对外发布推荐）。
3. **配置生效性**：逐个开关单独开，用 Waila 的 `maxProgress` / `energyUsage` / `parallel` 三个读数交叉验证蒸汽账（蒸汽账必须"控制器池 + 模块缓冲"两处都看）。
4. **防静默失效**：每个注入器在"生效 / 未生效 / GTNL 缺失"三条路径各打一行日志；落地后第一件事是确认日志里出现的是哪一行。
5. **回归**：Beacon / MonsterRepellent / Flight / Weather / EntityCrusher 五类**行为零变化**（本次范围外）。
6. **回归（GT 侧）**：确认没有行为变化波及 `SteamMultiMachineBase` 的其他子类（本次**不**在该类上注入）。
7. **存档**：不新增 NBT 键；`maxRecipeOcCount` 只夹紧读取值，不改写已有存档字段。

---

## 5. 平衡影响与对外发布档

### 5.1 装完默认 vs 提速档：实际效果数值表（**回答"改完是什么效果"**）

**默认档（`moduleSpeedMultiplier = 1.0` + 批处理开 + 其余默认）= 游戏内几乎零可见变化**：
5 个目标模块的**时长、蒸汽/t、产出量、概率、材料消耗速率全部与改前一致**；唯一差异是 OreProcessor 模式 6 少了配方检查次数（§5.2）。"整体提速"**不会**因为默认配置而发生。

要真正提速，必须显式设 `moduleSpeedMultiplier = m`（时长 ×m，`m<1` 更快）：

| 模块 | 现状周期 | 蒸汽/t（现状） | m=0.5 周期 | 蒸汽/t（m=0.5） | m=0.25 周期 | 蒸汽/t（m=0.25） |
|---|---|---|---|---|---|---|
| OreProcessor 模式0（600 t） | 30 s | 128×2^c×P | 15 s | ×2 | 7.5 s | ×4 |
| OreProcessor 模式6（20 t） | 1 s | 128×2^c×P | 0.5 s | ×2 | 0.25 s | ×4 |
| Apiary（6000 t） | 300 s | 2048×槽 = 16,384 | 150 s | 32,768 | 75 s | 65,536 |
| Greenhouse（100 t） | 5 s | 8,192 | 2.5 s | 16,384 | 1.25 s | 32,768 |
| BeeBreeding（12000 t） | 600 s | 32,768 | 300 s | 65,536 | 150 s | 131,072 |
| OilDrill I（1200 t） | 60 s | 120 | 30 s | 240 | 15 s | 480 |
| OilDrill II（600 t） | 30 s | 480 | 15 s | 960 | 7.5 s | 1,920 |
| OilDrill III（400 t） | 20 s | 1,920 | 10 s | 3,840 | 5 s | 7,680 |

换算成"每秒蒸汽"（×20）与"缓冲能撑多久"（模块缓冲 ÷ L/s）：

| 模块 | 缓冲 EU(=L) | L/s（现状） | 缓冲可撑 | L/s（m=0.5） | 缓冲可撑 |
|---|---|---|---|---|---|
| OreProcessor（c=0, P=8） | 163,840,000 | 20,480 | ~8,000 s | 40,960 | ~4,000 s |
| OreProcessor（c=6, P=512） | 163,840,000 | 83,886,080 | **~2 s** | 167,772,160 | **~1 s** |
| Apiary（oc=0, 8 槽） | 40,960,000 | 327,680 | 125 s | 655,360 | 62.5 s |
| Greenhouse | 20,480,000 | 163,840 | 125 s | 327,680 | 62.5 s |
| BeeBreeding | 163,840,000 | 655,360 | 250 s | 1,310,720 | 125 s |
| OilDrill III | 10,240,000 | 38,400 | ~267 s | 76,800 | ~133 s |

三条推论：
1. **提速会等比压缩"缓冲续航"** ⇒ 提速档必须同时考虑 `moduleBufferMultiplier` / `elevatorBufferMultiplier` / `elevatorChargeIntervalTicks`，否则表现为"跑一阵就 POWER_LOSS 抖动"。
2. **材料消耗速率同步上升**（周期缩短、单周期消耗不变）：Apiary 蜂王浆 40/槽/周期、BeeBreeding 128/周期、Greenhouse 水 16,000/周期、OreProcessor 10 mB 蒸馏水 + 1 mB 润滑剂/矿 —— m=0.5 时这些**全部 ×2**。
3. **OreProcessor 是唯一会失控的**：它的电路缩放是指数的（`128 × 2^c × 8 × 2^c`），c=6 时现状已是 4.19M EU/t，提速后 ×2/×4 直接撞上"缓冲 1~2 秒见底"。**建议对 OreProcessor 单独限速**（或把电路上限纳入配置），否则它是这套改动里唯一的平衡炸弹。

蒸汽账的三种口径（同一 m）：

| 配置 | 时长 | 蒸汽/t | 每操作总蒸汽 | 效果 |
|---|---|---|---|---|
| `moduleSteamFollowsSpeed = true`（默认） | ×m | ×1/m | **不变** | 提速不额外费汽，但峰值上升 |
| 同上 + `moduleSteamCostMultiplier = 2` | ×m | ×2/m | ×2 | 提速且更贵（**推荐给"对外发布"**） |
| `moduleSteamFollowsSpeed = false` | ×m | ×1 | ×m | 提速且更省（白嫖档） |

### 5.2 批处理的实际效果（**很小，必须说清**）

| 模块 | 单份时长 | 是否触发 | n（默认 `moduleBatchSize=128`） | 实际效果 |
|---|---|---|---|---|
| OreProcessor 模式 0~5 | 200~640 t | ❌ ≥128 | 1 | 无 |
| OreProcessor 模式 6 | 20 t | ✅ | 最多 6 | **每 tick 吞吐不变**（时长 120 t、产出 6 份）；收益是配方检查/开停机次数 ÷6 |
| Apiary | 6000 t | ❌ | 1 | 无 |
| Greenhouse | 100 t | ✅ | 1（`floor(128/100)`） | 无（1 份） |
| BeeBreeding | 12000 t | ❌ | 1 | 无 |
| OilDrill I/II/III | 400~1200 t | ❌ | 1 | 无 |

⇒ **"批处理默认开启"在稳态产出上几乎没有可见影响**；它的价值是服务端开销（GT 的批处理会让配方检查"less aggressive"，MTEMultiBlockBase.java:776）与进度粒度。若需求方期望"打开就变快"，那必须走 `moduleSpeedMultiplier`，不能指望批处理。

### 5.3 需求方目标档：**单份 1 tick + 批 128 + 每操作固定 6.4 s**（2026-10 追加）

需求方原话目标："默认速度超频到 1 tick，然后开批处理，固定 6.4 s 一次，相当于每 6.4 s 完成 128 次配方"，蒸汽走 `moduleSteamFollowsSpeed = false`（更省），OreProcessor 不单独限速。

**机制形状**：单份时长 1 t（<128 ⇒ 触发批处理）→ `n = min(128, floor(128/1), 可负担输入) = 128`（受输入夹紧）→ 操作时长 `min(128, 1×128) = 128 t = 6.4 s` → 产出 128 份；**lEUt 不变**（§3 已修正）⇒ **蒸汽/t 与改前完全相同**，每 6.4 s 的蒸汽流量也不变（现状每秒流量本来就是 `EUt×20`）。

**由此得到的吞吐倍数 = 改前单份时长 ÷ 1 t**：

| 模块 | 改前单份 | 吞吐倍数 | 每 6.4 s 的产出（= 128 份） | 每 6.4 s 的材料消耗（= 128 份） |
|---|---|---|---|---|
| Apiary | 6000 t | **×6000** | 300 s 周期的产出 ×128 | 蜂王浆 `40×槽×128`（8 槽 = **40,960 个**） |
| BeeBreeding | 12000 t | **×12000** | **128 个** ignoble 公主 | 蜂王浆 `128×128` = **16,384 个** |
| Greenhouse | 100 t | **×100** | 5 s 的耕作产出 ×128 | 水 `16,000×128` = **2,048,000 L** |
| OilDrill I/II/III | 1200/600/400 t | **×1200/×600/×400** | 128 份钻井流体 | 无输入（只受**输出仓容量**限制） |
| OreProcessor（模式 0） | 600 t | **×600 × P**（P=8×2^c） | c=6 时 **65,536 份**矿石产物 | 水 `10×P×128`、润滑剂 `1×P×128`（c=6：水 655,360 mB） |

**三个硬瓶颈（不解决，目标数值会被自动降级或卡机）**：

| # | 瓶颈 | 具体后果 | 处理 |
|---|---|---|---|
| B1 | **输入供料** | `n` 被"可负担输入"夹紧：BeeBreeding 想要 128 份需要 **16,384 个蜂王浆 = 256 组**，而它现在只是从输入总线 `depleteInput(royalJelly 64)`；仓里只有 1 组 ⇒ n 会掉到 1，**"128 配方/6.4 s"直接不成立** | 必须改写取料逻辑（按数量扣），且实战必须走 **ME 输入仓/大缓存**；否则自动降级为"能供多少跑多少" |
| B2 | **输出溢出** | 一次操作产出 128 份（BeeBreeding 128 个公主、Apiary 数十万物品、OilDrill 数万 L 流体）⇒ 输出仓塞满时 `addOutput` 失败会**卡住机器**（GT 的 output-full 检查），OilDrill 流体还会**静默丢弃** | 必须 a) 输出容量**预检并夹紧 n**（OilDrill 已定为硬性不可配）；b) 实战必须 **ME 输出总线** |
| B3 | **单次结算尖峰**（不是"频率太高"） | 批处理**降低**了操作频率（128 份合并 ⇒ 每 6.4 s 一次操作，操作频率降到 **1/128**，这正是降卡顿的来源）；代价是**产出集中在结算那一 tick 爆发**（128 份一次性落地） | 保持 `moduleBatchSize=128`（符合"少跑机器"的目标）；产出走 **ME 输出总线**；若实测每 6.4 s 出现一次固定尖峰卡顿，再用 `moduleBatchSize` 下调（注意：下调会让操作频率等比上升，是与目标相反的方向，仅在尖峰无法忍受时使用） |

> **B3 附：批处理为什么能降卡顿（精确定义）**
> 减少次数的是：① 配方查找（`checkProcessing` → `findRecipeMatches`，OreProcessor 内部还要查 7 个 RecipeMap 并掷随机数）；② 机器开停机/状态切换序列；③ 产出结算。
> **不减少**的是：进度推进（`mProgresstime` 仍每 tick +1，但那是极廉价的自增）。
> 因此"单份 1 t + 批 128"这个档位**同时**实现了两个目标：**吞吐 ×(原时长)** 与 **操作频率 ÷128**。若只把单份压到 1 t 而**不开**批处理，机器会变成**每 tick 跑一次完整操作**（每秒 20 次 × 每个模块）——那才是真正的卡顿源。⇒ 对本需求而言，**批处理是必开项，不是可选项**。

**为支持该目标，配置需增加两个键**（原设计的 `moduleSpeedMultiplier` 无法表达"绝对 1 tick"：各模块时长不同，6000→1 需要 1/6000，而 Apiary 与其他模块的倍率又不通用）：

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `moduleSingleDurationMode` | enum(`VANILLA`,`MULTIPLIER`,`FIXED_TICKS`) | `VANILLA` | `FIXED_TICKS` = 忽略模块原时长，直接把单份时长设为下项 |
| `moduleFixedSingleDurationTicks` | int | 1 | `FIXED_TICKS` 模式下生效；取 1 ⇒ 配合 `maxBatchDurationTicks=128`、`moduleBatchSize=128` 得到"每 6.4 s 128 次配方" |

**注意与本档冲突的既有默认**：本档要求 `enableModuleBatchProcessing=true`（默认已是 true）、`moduleBatchFollowsMachineButton=false`（默认已是 false）、`moduleSpeedMultiplier=1.0` 且 `moduleSingleDurationMode=FIXED_TICKS`/`=1`。也就是说**默认档要从"零变化"改为"本档"**，这会让 §5.1 的"装完默认 = 几乎零可见变化"结论作废——需在 README 明确声明（这也是 §5 开头登记的那条口径冲突的最终形态）。

### 5.4 对外发布档建议

| 档 | 配置 | 效果 |
|---|---|---|
| 上游等价（零变化） | `moduleSingleDurationMode=VANILLA` + `moduleBatchFollowsMachineButton=true` | 与 GTNL 原生逐位一致 |
| 温和加速 | `moduleSpeedMultiplier=0.5` + `moduleSteamCostMultiplier=2` | 全模块 ×2 速、每操作总蒸汽 ×2 |
| 激进（自用） | `moduleSpeedMultiplier=0.25` + `moduleBufferMultiplier=4` | ×4 速，缓冲同步扩容 |

- ⚠️ **2026-10 更正**：批处理**不会**提高"每 tick 吞吐"（GT 语义只拉伸时长，见 §5.2；本处原写"每 tick 吞吐上升"是错的）。真正的吞吐提升来自 §5.3 的 `moduleSingleDurationMode=FIXED_TICKS`（把单份时长压到 1 t）。
- 平衡档推荐组合：`moduleBatchFollowsMachineButton = true` + 机器按钮默认不按（GT 原生默认 `getDefaultBatchMode() = false`，`MTESteamMultiBlockBase.java:466-469`）⇒ 开箱行为与上游一致，想加速的玩家自己按按钮。
- 服务器锁定原版语义：`moduleSingleDurationMode = VANILLA` + `moduleBatchFollowsMachineButton = true`。

---

### 5.5 抗卡顿闸门（**实施中加入**，需求方更正口径："最初就是为了减少加速火把带来的卡顿"）

**核心等式**：一次操作合并 n 份 ⇒ 该模块的 `checkProcessing` 频率 = **20/n 次/秒**（n = 每操作份数，操作时长 = n tick）。

| n | 操作时长 | checkProcessing 频率 | 与上游对比（Apiary 上游 = 每 6000 tick 一次） |
|---|---|---|---|
| 128（目标档） | 128 t = 6.4 s | 0.156 次/秒 | 约 47× 上游频率，但吞吐 ×6000 |
| 8（门槛） | 8 t | 2.5 次/秒 | 约 750× 上游频率 |
| 1（供料不足退化） | 1 t | **20 次/秒** | **约 120000× 上游频率 ⇒ 比改前更卡** |

⇒ **风险与对策**：本方案靠"合并操作"降卡顿；一旦供料不足而降级到 n=1，就会退化成"每 tick 一次配方检查"，
把"加速火把的卡顿"换成另一种卡顿。因此加入闸门 **`minBatchesToApplyTuning`（默认 8）**：
**凑不够 N 份就完全不改**（保持上游时长/蒸汽/产出），并 WARN 一次。
**设计原则：宁可不提速，也不提高检查频率。** 想让供料不足时也提速，把该值调小（如 2）。

**配套的两处开销修正（同一轮加入）**：
1. **产出超堆不切叠**：GT 自己的并列产出就是"一个超大堆"（`ParallelHelper.addItemsLong:633-641` 只在 >`Integer.MAX_VALUE` 时拆），
   输出仓 `MTEHatchOutputBus.storePartial:185-216` 逐槽自拆 ⇒ 早先按 64 切叠会把 5 个堆变成**上万个 ItemStack**，
   反而放大输出路径开销。已改为与 GT 一致。
2. **热路径反射缓存 + 批量扣减**：`ALL_PROCESSABLE` 反射句柄缓存；矿石/蜂王浆改「一次取列表 + 批量实扣」
   （`consumeOreBatches`/`consumeJellyBatches`，每份必须凑齐才扣）。

**给玩家的警告**：不要在这些模块上再叠加**加速火把/时间之瓶**。NH-Utilities 的加速实现是直接改写
`multiBlockBase.mProgresstime` 并 `return true` 跳过该 TE 的默认 `updateEntity`（见项目记忆"GT 加速机制根因"），
叠加后会把"每 6.4 s 一次操作 + 一次结算"重新变成高频操作与高频结算，等于把本改动省下的开销又加回去。

---

## 6. 风险登记

| 风险 | 说明 | 对策 |
|---|---|---|
| 版本漂移（结构性） | 取证树 `dev-290` 与编译基线 `0.2.7-pre3` 是两个树；类同名但行号/局部实现有差异（例：`MAX_PARA` 在 `[pre3]` 为 `final`） | 注入点全部按**方法名+描述符**写；`require = 0` + **注入自检 + 双级行为指纹**（§7.4 R3）让失效可见；CHANGELOG 记录 |
| 无版本白名单的残余风险 | 上游语义变化若**未被指纹覆盖**，可能注入成功但算错 | R4 运行时健全性断言 + 一级指纹（结构性）比对；残余风险由 config 总开关兜底（见 §7.4 注） |
| Mixin 静默失效 | `@ModifyConstant` / 方法名写错不一定崩 | 关键注入 `require = 1`；每个注入器打日志；产物用 `javap` 核对注入后的类 |
| `@Overwrite` 的后遗症 | 静默吞掉 GTNL 上游修复 | 本设计**不使用** `@Overwrite` |
| 手写模块的"批处理"语义边界 | Apiary 一次可能产出多份；BeeBreeding 一次可出多个公主 | 已用配置项显式化；产出 ×n 是设计决定，需在 README 写明 |
| OilDrill 流体溢出 | 批处理 ×n 可能超过输出仓容量 | 按仓容量硬性夹紧 `n`（不可配） |
| 波及面 | 误注入 `SteamMultiMachineBase` 会改变 30+ 台其他蒸汽机器 | 只在 `SteamElevatorModuleBase` / 5 个模块 / `SteamElevator` 上注入 |
| 参考源≠运行产物 | 本设计未用 `javap` 核对实例 jar（需求方明确放弃该步） | 所有钩子写成"读配置作用于现有运行期状态"；不假设任何字面常量 |

---

## 7. GTNL 升级兼容性（本设计的核心追问）

### 7.1 实证：这个包在上游是"活跃改动"的

同一仓库两棵树（`GT-Not-Leisure-0.2.7-pre3` vs `GT-Not-Leisure-dev-290`；按 §0.2 推测两者**都是 dev-290 线的快照**，因此本 diff 近似"同一分支的小更新"这一最常见场景）对同一批文件的差异：

| 文件 | pre3 行数 | dev-290 行数 | 差异行 | 占比 | 命中"机制 token"的差异行 |
|---|---|---|---|---|---|
| SteamOreProcessorModule | 839 | 840 | 35 | 2.1% | **0** |
| SteamApiaryModule | 1045 | 1045 | 40 | 1.9% | **0** |
| SteamGreenhouseModule | 725 | 727 | 82 | 5.6% | **0** |
| SteamBeeBreedingModule | 86 | 86 | 16 | 9.3% | **0** |
| SteamOilDrillModule | 93 | 97 | 24 | 12.4% | 4（**仅 tooltip 语言键重命名**） |
| SteamElevatorModuleBase | 221 | 221 | 6 | 1.4% | **0** |
| SteamElevator | 761 | 763 | 60 | 3.9% | **0** |

（"机制 token" = `checkProcessing` / `mMaxProgresstime` / `lEUt` / `mOutputItems` / `mOutputFluids` / `steamBufferSize` / `maxEUStore` / `getMaxParallelRecipes` / `getMaxOverclocks` / `getRecipeTickTime` / `MAX_PARA` / `RECIPE_EUT` / `getIndustrialFarmEUt` / `onRunningTick` / `supportsBatchMode` / `supportsSteamOC` / `class `）

结论：**上游改皮不改骨**；因此"更新后是否需要动我的代码"的判据可以收敛为一条：**只要 diff 不碰 `checkProcessing` 签名与那 4 个 GT5U 字段，就零维护。**

### 7.2 匹配机制（为什么行号漂移无害）

Mixin 在**类加载期**按 `方法名 + 描述符` 解析注入点，与行号、方法内语句顺序、类的其它部分无关。因此下列更新**自动适配，无需任何动作**：
- 行号整体位移、方法内重构、新增/删除其它方法或字段；
- 工具类改写、本地化键重命名、`SteamMultiMachineBase` 等父类内部变化；
- 时长/蒸汽数值被上游改动（R1 保证本方案跟随新值，只是"总账基数"变了，需要复测而非改码）。

### 7.3 失败模式矩阵（会崩 / 会静默 / 会算错）

| GTNL 更新做了什么 | `require = 1`（本工程 `mixins.ae2_qof.json` 的 `defaultRequire`，:50-52） | `require = 0` |
|---|---|---|
| 只改皮（行号/本地化/类内其它代码） | 无影响 | 无影响 |
| 改了 `checkProcessing` 内的算法/数值 | 注入成功，**倍率照叠**（需复测数值） | 同 |
| **上游自己改成走 `processingLogic` 或自带批处理** | 注入成功 → **重复放大（静默算错）** | 同 |
| 重命名 / 删除 / 改签名 / 改返回类型 | **启动期崩溃**（`InvalidInjectionException` → `MixinApplyError`） | **静默失效**（只剩一行 WARN） |
| 类被移包或改名 | **静默跳过**（受 `"required": false` 保护，:2） | 同 |
| `aTick % 20` 的常量 20 被替换/移走 | `@ModifyConstant` 失败 → **崩溃** | 静默失效 |
| GTNL 移除该机器 | 静默跳过 | 同 |

**必须澄清的误解**：`mixins.ae2_qof.json:2` 的 `"required": false` **只保护"目标类不存在"**（可选依赖缺失场景），**不保护"目标类存在但方法注入失败"** —— 后者由 `defaultRequire: 1` 决定，= 崩启动。

### 7.4 四条硬规则（把"会崩/会静默"压到可接受）

| 规则 | 内容 | 解决什么 |
|---|---|---|
| **R1 不硬编码 vanilla 数值** | `batchDuration` 的"单份时长"在 `RETURN` 处读回 `mMaxProgresstime`（§2.5/§3 已按此修正） | 上游改数值时自动跟随 |
| **R2 只依赖两类稳定契约** | ① GT5U 的 API 字段（`mMaxProgresstime`/`lEUt`/`mOutputItems`/`mOutputFluids`）；② GTNL 的**方法签名**。绝不依赖 GTNL 私有局部变量、常量、行序 | 上游重构不波及（也是"`MAX_PARA` 是 final 内联、改不动"这条约束的正面用法） |
| **R3 注入自检 + 双级行为指纹（不做版本白名单）** | **① 注入自检**：每个 mixin 通过给目标类**添加一个标记方法**（Mixin 支持给目标类加方法），mod 启动时反射 `Class.getDeclaredMethod("ae2qof$steamTuningApplied")` 是否存在 ⇒ 直接回答"我的注入到底落没落地"，无需跑机器。**② 一级（结构）指纹**：采样"方法存在性/返回类型/产出通道"（`checkProcessing` 存在且返回 `CheckRecipeResult`、`mOutputItems`/`mOutputFluids` 仍被赋值、`maxEUStore`/`steamBufferSize` 仍在）——不一致 ⇒ **自动停用该模块调整 + ERROR**。**③ 二级（数值）指纹**：采样时长/蒸汽基数（`mMaxProgresstime`、`lEUt`、`getMaxParallelRecipes()`）——因为 R1 让倍率自动跟随，不一致只 **WARN + 继续**，提示"上游数值已变，请复验" | 不依赖版本号；未知版本的失效变成**可见且安全**（功能停用但游戏能开），而不是崩或算错 |
| **R4 上游接管检测** | 在 `RETURN` 处做健全性断言（如"结果是否已被 batch/OC 机制放大过"）；命中即整体跳过并打**一次** ERROR（去重） | 防"静默算错"这一唯一无日志失效路径 |

> **§7.4 注（残余风险与兜底）**：R3/R4 都是**运行时**检测，覆盖不了"上游语义变了但既没过结构指纹、也没触发 R4 断言"的极端情形。因此：
> 1. 必须提供 **config 总开关**（`enabled = false` 一键回到上游行为）；
> 2. R4 的断言要写在**最容易被上游接管的路径**上（`checkProcessing` 结果是否已被放大），宁可误报停用，不可漏报算错；
> 3. CHANGELOG 中登记"已用 R3/R4 覆盖哪些更新形态、未覆盖哪些"。

### 7.5 升级后的 30 秒检查清单

1. 启动日志搜 `[AE2QoL] SteamElevator`：① **注入自检**结果（5 个标记方法是否都在）；② 一级/二级指纹比对结果（结构性一致？数值是否变化？）；③ 有无 `ERROR ... 已自动停用` / `WARN ... 数值已变` / Mixin 自身的 `InvalidInjectionException`。
2. 进游戏建一台该模块：Waila 的 `maxProgress` 是否被倍率改写、蒸汽/t 是否随联动变化。
3. `git diff` GTNL 新版本对 `steamElevator` 包的改动：**只看两个东西** —— `checkProcessing` 签名是否变、那 4 个 GT 字段是否还在。都没变 ⇒ 零动作。

### 7.6 与替代方案的维护成本对比

| 方案 | GTNL 升级后需要做什么 | 风险 |
|---|---|---|
| **mixin（本设计 + R1~R4）** | 通常零动作；白名单未命中 = 自动停用 + 日志 | 依赖方法签名稳定；靠 R3/R4 把风险变成"可见的停用" |
| GTNL fork 改源码 | **必须 rebase + 重编译**，否则整包停在旧 GTNL | 该包上游活跃（§7.1），合并冲突概率不低 |
| 提上游 PR | 一劳永逸 | 沟通成本；"批处理默认开启"这类口味未必被接受 |

### 7.7 关于"require 失败到底是崩还是 WARN"的对照实验 —— **需求方选择跳过**

`require` 失败（方法找不到）在"启动崩溃"与"仅 WARN 继续"之间的确切行为，随 Mixin 版本/配置有差异。原建议做一次 5 分钟对照实验（故意把 `method` 写错，观察 require=1 / require=0 两种表现），**需求方明确选择不跑实验、直接按设计写**。

由此产生的登记风险与缓解：
- 按标准 Mixin 行为，`require = 0` 的失败注入**不阻断启动**（只记 WARN）⇒ 本方案"永不崩"的假设成立；
- 一旦实测发现 `require = 0` 仍然会因**类级**错误（而非注入级错误）抛错，则退化路径为：把 5 个 mixin 拆到**独立 mixin 配置文件**（`mixins.ae2_qof.gtnl_steam.json`，`"required": false`），使该类错误不影响主配置；
- **注入自检（R3①）本身就是这个实验的替代品**：无论底层是崩还是 WARN，标记方法的存在性都能给出确定答案。

---

## 8. 决策记录（已拍板口径）

| 项 | 决定 |
|---|---|
| 载体 | AE2-QoL 用 mixin 覆写，GTNL 源码保持只读 |
| 范围 | OreProcessor / Apiary / Greenhouse / BeeBreeding / OilDrill I·II·III；其余 5 类不动 |
| 目标 | 整体提速 + 并行/批处理真正生效 |
| 蒸汽联动 | 联动开关默认开，同时保留独立蒸汽倍率 |
| 批处理默认 | **开启**（并登记"会提高吞吐"的公开义务；提供平衡档开关） |
| 基线 | 按参考源（0.2.7-pre3 / dev-290 双列），不核对实例 jar |
| 注入失败姿态 | **`require = 0`（永不崩）** + 日志自检；接受"失效不崩但可能静默"的代价，用 R3 自检补可见性 |
| 兼容性策略 | **不做版本白名单**；改为 **注入自检（标记方法）+ 双级行为指纹（结构/数值）**，不依赖版本号 |
| require 对照实验 | **跳过**（需求方选择直接按设计写）；风险与退化路径登记在 §7.7 |
| 输入不足时 | **自动降级**（n 夹紧到实际可负担份数，不卡机、不空转） |
| 批处理份数 | **保持 `moduleBatchSize = 128`**（需求方目标：减少操作频率以降低卡顿；下调 batchSize 会让操作频率等比上升，方向相反，仅作尖峰兜底） |
| 产物 | 本设计文档；代码实施需另行口令 |

---

## 9. 参考文件（只读取证）

- `reference_src_290b3\GT-Not-Leisure-dev-290\GT-Not-Leisure-dev-290\src\main\java\com\science\gtnl\common\machine\multiblock\module\steamElevator\`（12 个类）
- `reference_src_290b3\GT-Not-Leisure-0.2.7-pre3\...\steamElevator\`（同 12 个类，AE2-QoL 编译基线）
- `SteamMultiMachineBase.java` / `SteamTypes.java` / `GTNLOverclockCalculator.java` / `GTNLParallelHelper.java`（GTNL）
- `GT5-Unofficial-5.09.54.133\...\MTESteamMultiBlockBase.java`、`MTEMultiBlockBase.java`、`HatchElementBuilder.java`、`GTValues.java`
- AE2-QoL 侧：`mixins.ae2_qof.json`、`config/…/Config.java`、`mixin/gt/MixinSuperCraftingInputHatchMEWildcard.java`（GTNL 可选依赖 + `remap = false` 先例）
