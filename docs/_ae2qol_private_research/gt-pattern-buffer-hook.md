# GT 样板输入仓（MTEHatchCraftingInputME）接管蓝图 —— 3.22.0 M1 剩余

> 来源：子代理只读取证（GT 源码 `reference_src_290b3/GT5-Unofficial-5.09.54.133` + 运行期 jar 字节码 + 参考实现 `WildcardPatternforGTNH`）。
> 简称：`GT:行号` = `gregtech/common/tileentities/machines/MTEHatchCraftingInputME.java`；`ProcLogic:行号` = `gregtech/api/logic/ProcessingLogic.java`。
> 用途：实现「一张通配样板 → N 张具体样板」在 GT 样板仓上的索引与推送接管。

## 一、核心结论

1. **必须子类化 `PatternSlot`**：`protected final ICraftingPatternDetails patternDetails`（GT:142-143）是 final，且
   `patternDetailsPatternSlotMap` 是 `Map<ICraftingPatternDetails, PatternSlot>`（GT:476-477，N 个 key → 1 个槽位）
   ⇒ 只能子类化并覆写 `getPatternDetails()`（GT:247-250，唯一 details 出口）。
   已证伪的替代方案：一个 details 代表整组（模板 details 只含一个候选，其余配方永远匹配不到）、
   只改 `pushPattern`（我们无法影响 AE2 注册哪些 key）、改写 `mInventory` 里的样板物品（GT:857-860 会同步回存档，污染玩家槽位）。
2. **完全不需要反射**：GT 已内置正确的缓存失效钩子 —— 覆写 `shouldBeCached()` 返回 `false`
   （`IDualInputInventoryWithPattern` 的 default 方法；`ProcLogic:149-151` 提前放行、不写缓存）。
   参考实现反射的 `processingLogics` 字段在 5.09.54.133 **不存在**（全树零命中）⇒ 那段是永久空转的死代码，**不要照抄**。
3. **槽位对象只在两处创建**：`onPatternChange` 的 `new PatternSlot<>(newItem, this)`（GT:1097）与
   `loadNBTData` 的 `new PatternSlot<>(pattern, patternSlotNBT, this)`（GT:819）。没有第三处。

## 二、四处注入点

| 注入点 | 位置 | 要做什么 |
|---|---|---|
| `onPatternChange(int, ItemStack)` | GT:1075-1103，`@At("RETURN")` | 若该槽是通配样板 → 把 `internalInventory[index]` 包成子类、清掉该槽旧 key、把**每个**展开 details `put(details, slot)` |
| `loadNBTData(NBTTagCompound)` | GT:809-861，`@At("RETURN")` | 同上，遍历 0–35（样板槽位）。**这是重载/区块重载后不失效的必要条件**：GT 在 841 会 `clear()` 整个映射，843-846 只写回模板 key |
| `provideCrafting(ICraftingProviderHelper)` | GT:1223-1239，`@At("HEAD")` + cancel | 复现 `isActive()` 检查（GT:1225）；通配槽 → 每个展开 details `craftingTracker.addCraftingOption((ICraftingProvider) this, details)`；非通配槽 → 沿用原逻辑（含 `details == null` 的 WARN，GT:1230-1235）。**注入体不得抛异常**（抛了会中断 AE2 的整轮重建，见下） |
| `pushPattern(ICraftingPatternDetails, InventoryCrafting)` | GT:1241-1265，`@At("HEAD")` | 用 `patternDetails.getPattern()` / 槽位缓存的展开列表反查（**不要重跑展开、不要依赖任何“生成 ID”**）；命中 → `put(details, slot)` 后**放行原方法**（GT 自己会做 `isActive`/`isAllowedToWork`/`MEInventoryCrafting` 校验、`insertItemsAndFluids`、`justHadNewItems = true`）；未命中 → WARN + `return false`（**不能放行**：GT:1258 未判空，会 NPE） |

## 三、派生状态处置

| 状态 | 位置 | 处置 |
|---|---|---|
| `patternDetailsPatternSlotMap` | GT:476 | **必须重建/补全**（`pushPattern` 消费处不判空，GT:1258） |
| `internalInventory[]` | GT:472 | **必须替换为子类槽位**（`getPatternDetails()` 是 final 字段唯一出口） |
| `justHadNewItems` | GT:480 | 我们若接管 `pushPattern` 且 cancel 才需自己置 true；**按上表放行原方法则不需要**。它被 `onPostTick`（GT:530-538）消费以触发配方重检 |
| AE2 `craftingMethods` / `craftableItems` / `emitableMediums` / `inputOnlyPatterns` / `craftableItemSubstitutes` | `CraftingGridCache.updatePatterns` | **自动**：该方法开头全量 clear 后重新 `provideCrafting`（字节码偏移 17/26/35/44/51 → 60 → 91） |
| `ProcessingLogic.dualInvWithPatternToRecipeCache` / `activeDualInv` | ProcLogic:84 / :76 | 由 `shouldBeCached()==false` 绕开（ProcLogic:149-151）⇒ 永不写缓存，也就不存在过期 |
| `processingLogics` | **不存在** | 不反射、不写兼容分支 |

## 四、SRG 与静默失效风险

- **SRG 陷阱**：运行时槽位变更钩子叫 `func_70299_a`（源码里的 `setInventorySlotContents` 在字节码中不存在）。
  我们注入 **`onPatternChange`**（MCP 名，javap 确证）即可 —— `func_70299_a`（GT:1405-1411）与 GUI 的 changeListener（GT:991）**两路都经过它**。
- `mixins.ae2_qof.json` 为 `"required": false` ⇒ 缺 GT 时整个 mixin 文件会被跳过 ⇒ **必须有一行启动自证日志**。
- `PatternSlot.hasChanged`（GT:195-200）在 `patternDetails == null` 时会 NPE（`null.equals`）；
  而 AE2 的 `getPatternForItem` 异常时**静默返回 null**（字节码异常表 `from 0 to 9 → aconst_null`）⇒ 我们解码展开产物时必须校验非 null 并记日志。
- 建议诊断点：① `provideCrafting` 入口（限频 INFO：注册了多少展开 details、命中几个通配槽、为何没展开）；
  ② `pushPattern` 入口（限频 INFO：反查命中/未命中）；③ `onPatternChange`/`loadNBTData` RETURN（事件型 INFO，带 `SmartWildcardExpander.Result.describe()`）；
  ④ 启动自证一行；⑤ `shouldBeCached()==false` 的生效证明（限频 INFO）。

## 五、范围（重要）

- 本蓝图**只覆盖 GT 的 `MTEHatchCraftingInputME`**（样板输入总成/总线 (ME) 2714/2715）；
  `MTEHatchCraftingInputSlave` 只是转发 `getMaster().inventories()`（其源码 169-173），主机修好即自动受益。
- **PH 的 `PatternDualInputHatch` 与 GTNL 的 `SuperCraftingInputHatchME` 都不在这条继承链上** ⇒ 需要**各自**取证与接管
  （第三个子代理正在做；PH 修好后我们的 MK.III（32108，PH 的克隆）应自动受益）。
- 客户端图标：GT 的 GUI 用 `patternItem.getOutput(stack)` 显示样板图标（GT:981-989）⇒ 通配样板放进去会一直显示**模板**输出材质。
  「是否改为显示当前生效候选 / 通配图标」属产品决策，待用户看到实物后再定。

## 六、性能约束（实现时必须遵守）

- 展开 N 项 = AE2 侧 N 个 `craftingMethods` key；36 槽 × 512 上限 ⇒ 最多 18432 个 key，且网格重建是**全量** clear+重填
  ⇒ 必须命中 `SmartWildcardExpander` 的 LRU 缓存（容量 64，key = 模板+规则+上限的 NBT 快照）。
- **索引读取阶段只读**：不要在 `provideCrafting` 里修改 `internalInventory`（参考实现这么干了，让本次遍历看到什么取决于顺序）。
- `loadNBTData` 末尾会把 `internalInventory[i].pattern` 写回 `mInventory[i]`（GT:857-860）⇒ 子类**绝不能改写 `pattern` 字段**。
