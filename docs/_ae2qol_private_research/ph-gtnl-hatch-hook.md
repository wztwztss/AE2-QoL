# PH / GTNL 样板机器接管蓝图 —— 3.22.0 M1 剩余（第二、三族）

> 来源：子代理只读取证（PH 源码 `reference_src_290b3/Programmable-Hatches-Mod-0.2.0p24-beta` + PH/GTNL 运行期 jar 反汇编）。
> 简称：`PH:行号` = `reobf/proghatches/gt/metatileentity/PatternDualInputHatch.java`（及同目录父类）；`GTNL:行号` = `com/science/gtnl/common/machine/hatch/SuperCraftingInputHatchME.java`。
> 用途：把「一张通配样板 → N 张具体样板」接到 PH 家族与 GTNL 家族。

## 一、三族对照（决定各自实现路线）

| 家族 | 样板槽结构 | 注册点 | pushPattern 需要 details→槽位反查？ | 工厂配方缓存 |
|---|---|---|---|---|
| **PH** `PatternDualInputHatch`（含 MK.III 32108） | **平行数组**：`ItemStack[] pattern`(36) + `patternItemCache` + `patternDetailCache` + `int[] multiplier`（PH:350/889/890/442） | `provideCrafting` PH:1087-1126（一槽一 details :1121-1122，带**引用相等**身份缓存 :1099） | **不需要**（`pushPattern` PH:673-733 不读 details 参数，`aload_1` 计数 0） | **已默认关闭**（`INeoDualInputInventory.shouldBeCached()==false`） |
| **GTNL** `SuperCraftingInputHatchME`（21504/21505，同一类） | `PatternSlot[] internalInventory`（**360** 槽）+ **public** `Map patternDetailsPatternSlotMap`（GTNL:141/159） | `provideCrafting` GTNL:929-944 | **需要**，未命中 = **NPE 且穿透到 CPU tick** | **必须关**（GTNL 的 PatternSlot 未覆写 `shouldBeCached()` ⇒ 继承默认 true） |
| **GT** `MTEHatchCraftingInputME`（2714/2715，已实现） | `PatternSlot[]` + **private** Map | GT:1223-1239 | 需要，未命中 = NPE | 需要（子类覆写 `shouldBeCached()==false`） |

## 二、PH 家族（推荐先做：最简单、且自动覆盖我们的 MK.III）

1. **路线**：Mixin `PatternDualInputHatch.provideCrafting`，注入点用 **TAIL/RETURN**（不要在 HEAD 抢，否则会改变 `isActive()` 守卫与身份缓存语义）。
2. 取 `pattern` 数组（本仓已有 `mixin/ph/MixinPatternDualInputHatchAccess` 的 accessor/`@Invoker("onPatternChange")`，见其 :37-57），**按 `pattern.length` 迭代**以兼容 MK.III 的 144（MK.III 通过 accessor 把 4 个数组换成 144）。
3. 对 `SmartWildcardState.isSmartWildcard(slot)` 为真的槽位调用 `SmartWildcardExpander.expand(slot, world)`，把每个展开产物用 `((ICraftingPatternItem) expanded.getItem()).getPatternForItem(expanded, world)` 转 details，再 `craftingTracker.addCraftingOption(this, details)`。
4. **不要**把展开 details 写进 `patternDetailCache`：MK.III 会重建该数组（其源码 :143-144），PH 的 `blacklist()` 也会遍历它 ⇒ 自己维护 `@Unique` side map（key = 槽位下标 + 源 ItemStack 身份 + `SmartWildcardState.revision`）。
5. **无需**任何 details→槽位反查，也**无需**处理 `shouldBeCached()`（PH 缓冲本来就是 false）。
6. 注意：`PatternDualInputHatchInventoryMappingSlave` 有同形的 `provideCrafting`（PH:1094-1124，同为身份缓存）——若「样板输入总成（映射从属）」也要支持，需一并处理。
7. 失效点：PH 的身份缓存按**引用相等**判变化（PH:1099）⇒ 「同一 ItemStack 对象原地改 NBT」（revision 变、对象不变）会被当成没变 ⇒ side map 的失效条件必须比较 **revision**；另外 `optimize()`（PH:1436-1493）会替换 `pattern[]` 里的样板物品并 `onPatternChange()+refresh()`（:1474/:1483-1484），side map 必须能在其后失效。

## 三、GTNL 家族（风险最高：未命中 = NPE 穿透 CPU tick）

1. **路线 A（推荐，最小、零反射）**：Mixin **AE2 的 `CraftingGridCache.addCraftingOption`**：若 `details.getPattern()` 是我们的通配样板 → 展开；当 `medium instanceof SuperCraftingInputHatchME` 时用其 **public** 的 `patternDetailsPatternSlotMap.get(原 details)` 取到既有槽位，对每个展开 details `put(expanded, 同一 slot 对象)`，并把 `expanded` 交给 AE2 注册（**必须防重入**：在 `addCraftingOption` 内再调它会递归）。
   - **必须复用同一个 slot 对象**：推入的物品落在 `slot.itemInventory`，机器读的是 `internalInventory[]` 里那个（GTNL:970-974）⇒ 另 `new` 一个替身槽位会让推入物消失。
2. 路线 B（按族注入，与 GT 同形）：Mixin `SuperCraftingInputHatchME.provideCrafting`(TAIL) + `onPatternChange`(RETURN, :799-832) + `loadNBTData`(RETURN, :469-516) —— 后两处分别 clear/覆盖 map（:500-506 / :827），漏掉就会「放进去能用、换一张就不接单」。
3. **配方缓存必须关**：子类化 GTNL 的 `PatternSlot` 覆写 `shouldBeCached()` 返回 false；要让机器用该子类，需在 `loadNBTData:479` 与 `onPatternChange:825` 的 `new PatternSlot<>(...)` 处 `@Redirect`。暂不做子类化时至少要在注册后调 `resetCraftingInputRecipeMap()`（GTNL:860-867）兜底，并接受「首轮缓存可能错误」。
4. **per-slot 键用数组下标，不要用 `slot.slotIndex`**：GTNL 的 `loadNBTData` 在 :479 传的是 NBT 列表序号（既有坑，稀疏保存时会错位）。
5. 诊断（GTNL 自己不会报，只会 NPE）：注册侧统计「每槽 map key 数」并与 AE2 注册数对账；未命中路径必须我们记 WARN 并 `return false`。

## 四、共同事实与取舍

- 三族**都**经过 AE2 的 `ICraftingProviderHelper.addCraftingOption(medium, details)`（PH:1122 / GTNL:942 / GT:1237；唯一实现 `CraftingGridCache.addCraftingOption`）⇒ 存在一个**共同注入点**；但 **GT 的 map 是 private** ⇒ 共同注入点无法为 GT 补反查 ⇒ **GT 必须走按族注入**。
- 「共同注入点」与「按族注入」**二选一**：同时上会把同一 details 的 medium 重复加进列表（`CraftingGridCache` 里是 `List.add` 而非 `put`）。
- 规模：每张通配最多 512 details 进 AE2 的 `craftingMethods`（还有各机器自己的 map）；GTNL 360 槽 × 512 = 184320 条，而 `updatePatterns()` 每轮全量 clear+重填 ⇒ **必须**per-slot 展开缓存（走 `SmartWildcardExpander` 的 LRU + 日志限频）。
- 版本漂移：GTNL 运行 jar 已与参考源码不一致（jar 的 `pushPattern` 多一个 `notifyWatchers()`）⇒ 任何按源码行号写死的注入都可能失效；实现前用 `javap -p -c` 复核目标方法里 `addCraftingOption` / `Map.get` / `new PatternSlot` 的偏移仍存在。
- PH 的 `onPatternChange` 是 **private**（PH:464）⇒ 只能通过已有的 `@Invoker` 访问（`mixin/ph/MixinPatternDualInputHatchAccess:55-57`）。
- GTNL 的 MUI1 手工窗口路径（GTNL:1071-1076）只清配方缓存、**不触发** `onPatternChange` ⇒ 别把它当成槽位变更的唯一入口。
