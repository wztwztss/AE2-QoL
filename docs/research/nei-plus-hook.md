# NEI 加号 → 通配推导 接管蓝图 —— 3.22.0 M2

> 来源：子代理只读取证（NEI 2.8.130-GTNH 运行期 jar 反汇编 + AE2/GT 参考源码 + 本仓现有 NEI mixin）。
> 术语更正：AE2 rv3-beta-1050 **没有** `ContainerPatternTerminal`/`GuiPatternTerminal`；真名是
> `appeng.container.implementations.ContainerPatternTerm` 与 `appeng.client.gui.implementations.GuiPatternTerm`。

## 一、三条决定性事实（决定 M2 架构）

1. **GT 处理配方在样板终端上“+ 号是灰的”**：AE2 只给样板终端注册了 `"crafting"` 的 overlay
   （AE2 `NEI.java:178` `registerGuiOverlay(GuiPatternTerm.class,"crafting",…)`、`:183` 注册 handler），
   而 GT 的 `GTNEIDefaultHandler.getOverlayIdentifier()` 返回 `recipeMap.unlocalizedName`（GT:394-397）
   ⇒ `hasOverlay=false` ⇒ `canFillCraftingGrid=false` ⇒ **按钮禁用、点不动**。
   ⇒ 要支持卷板机这类 GT 处理配方，必须**主动放宽** `canFillCraftingGrid()` 与 `updateEnabled()`
   （本仓已有这两个注入点：`mixin/nei/MixinGuiOverlayButton.java:50`、`:89`）。
2. **样板终端的输入/输出格不是真实 Container 槽**：`ContainerPatternTerm implements IVirtualSlotSource`，
   只有 3 个真实槽（`craftSlot`/`patternSlotIN`/`patternSlotOUT`），输入输出是 `IAEStackInventory` + `PacketVirtualSlot`
   ⇒ **任何走 NEI window-click / `DefaultOverlayHandler` 的填充对样板终端静默无效**。
   ⇒ M2 必须走「自定义 C2S 包 + 服务端写 NBT」，照抄本仓 `MergedTerminalActionPacket` 的分工链
   （客户端推导 → C2S → `ServerTerminalHelper.scheduleServerTask` → 服务端改 ItemStack → `detectAndSendChanges`）。
3. **没有可照抄的参考实现**：Wildcard Pattern 全仓**没有** NEI 集成（规则是手写的）；AE2PatternGen 是在**服务端读 RecipeMap**
   生成样板，也不走加号 ⇒ M2 的“从 NEI 自动推导”是本模组新增价值。

## 二、最小注入落点（推荐顺序）

1. **主落点**：`MixinGuiOverlayButton.overlayRecipe(Z)V` HEAD（本仓已有，`:57`）——把门从
   `instanceof GuiMergedTerminal` 扩到「样板终端集合（`GuiPatternTerm`/`GuiPatternTermEx`/`GuiMergedTerminal`）且功能开启」，
   命中则跑「推导规则 → 确认窗」并 `ci.cancel()`。它是**两分支（crafting 走 overlayRecipe / 其它走 transferRecipe）之前唯一统一的点**。
2. 配套放宽：`canFillCraftingGrid()Z` HEAD（`:89`）与 `updateEnabled()V` TAIL（`:50`）——否则 GT 处理配方的加号是灰的（见事实 1）。
3. 兜底：`MixinDefaultOverlayHandler.transferRecipe` HEAD（`:40`，覆盖 DefaultOverlayHandler 那条分支）。
4. 本仓已有但**未 cancel** 的更贴近数据的落点：`mixin/nei/MixinRecipeHandlerRef.java:24`
   （`fillCraftingGrid` HEAD）——但它拦不到鼠标拖拽/R 键等入口。
5. **不改原版行为的纪律**：不命中就 `return`（不 cancel）；原版填充的保留方式见“待拍板”。

## 三、“加号那一刻”客户端能拿到什么（全部 javap 已核）

`GuiOverlayButton.firstGui`（public，指向真实容器界面）→ `firstGui.inventorySlots`（目标 `Container`）；
`((GuiRecipeButton)this).handlerRef`（public final `RecipeHandlerRef`）→ `.handler`（`IRecipeHandler`）、`.recipeIndex`（int）；
`handler.getIngredientStacks(i)` / `getOtherStacks(i)` / `getResultStack(i)` → `PositionedStack`
（`.item` 当前物品、`.items` 候选数组、`.relx/.rely` 坐标）；`handler.getOverlayIdentifier()` 即配方类型
（`"crafting"` 或 GT 的 `recipeMap.unlocalizedName`）。

## 四、电路与其他硬事实（含对我 M1 代码的修正）

- 电路物品：`ItemList.Circuit_Integrated` / `gregtech.common.items.ItemIntegratedCircuit`；**电路号 = `getItemDamage()`**；
  **上限 24**（`ItemIntegratedCircuit.java:63`，M3 计划里写的 1~32 是错的，必须改 1~24）；
  生成用 `GTUtility.getIntegratedCircuit(int)`（`GTUtility.java:2678-2680`）。
- 检测判据（GT 自己用的）：`stack.getUnlocalizedName().startsWith("gt.integrated_circuit")`（`MTEMultiBlockBase.java:2019-2021`）。
- 电路在 NEI 配方里就是**普通输入 PositionedStack，没有任何特殊标记** ⇒ 按显示名/矿辞去匹配它必然出错，必须按上面判据识别并从规则里剔除。
- **`getResultStack()` 对 GT 恒为 null**（`GTNEIDefaultHandler.java:942-945`）⇒ 输出全在 `getOtherStacks()`（:947-950）。
- **流体幻影物品**：GT 用 `GTUtility.getFluidDisplayStack(...)` 把流体塞进输入/输出，类型是
  `FixedPositionedStack implements IFluidAlternativeStack`（`GTNEIDefaultHandler.java:571-605`）⇒ **必须跳过**，否则会拿流体占位物品去推导矿辞。
- GT↔RecipeMap 桥（无需反射）：`GTNEIDefaultHandler implements … { protected final RecipeMap<?> recipeMap; public RecipeMap<?> getRecipeMap(); }`（:95/:150-151），
  且 `getOverlayIdentifier()` == 默认分类下的 `recipeMap.unlocalizedName`（`RecipeCategory.java:61-66`）——本仓 `NeiRecipeCapture.java:179-183` 已在用这条。

## 五⚠️ 对我 M1 已提交代码的修正（缺陷）

`SmartWildcardExpander.oreInfo(ItemStack)` 用“**第一个大写字母**”切分矿辞名（`ingotIron` → `ingot`/`Iron`）——
这对 `dustSmallIron`、`plateDoubleIron`、`crushedPurifiedIron` 会切成 `dust`/`SmallIron` ✗（材料名错 ⇒ 展开出错误候选，且只表现为“候选少/不对”）。
**必须换成 GT 权威 API**：`OrePrefixes.getOrePrefix(String)`（`OrePrefixes.java:2837-2851`，按 `VALUES` 最长前缀匹配，还带特例修正）、
`OrePrefixes.detectPrefix(ItemStack): List<ParsedOreDictName>`（:2910-2938，带 ThreadLocal 缓存），
`ParsedOreDictName{public final OrePrefixes prefix; public final String material;}`（:2862-2871）。
参考模组同款：`WildcardPatternforGTNH/.../compat/GTCompat.java:14-60`（含 GTNH 2.9 把 `OrePrefixes` 从 enum 改成 class+VALUES 的兼容处理）。

## 六、网络与双端分工（照抄本仓）

1. 客户端推导（NEI 数据只在客户端）→ 2. 确认窗（纯客户端）→ 3. 新 C2S 包（照抄 `MergedTerminalActionPacket` 结构：Action + 规则数组 + 黑白名单 + circuit + nonConsumed + recipeMap，`fromBytes` 防御性解码）→
4. 服务端 `ServerTerminalHelper.scheduleServerTask`（**必须归队 tick 线程**）→ 5. 校验 `player.openContainer` 属于样板终端 →
6. 取目标样板 ItemStack（`patternSlotOUT`，本仓已有反射先例 `network/UploadPatternPacket.java:218-224`）→
7. `SmartWildcardState.writeAndBumpRevision(stack)` + `SmartWildcardExpander.clearCache()` + `detectAndSendChanges()`。
   **不要只在客户端写**：会被服务端槽位同步覆盖，且展开读的是服务端权威 NBT。

## 七、风险与诊断点（摘要）

- R1 挂在 window-click/DefaultOverlayHandler 上 ⇒ 样板终端静默无效；R2 GT 处理配方加号是灰的（必须先放宽 canFill/updateEnabled）；
  R3 本仓 `NeiRecipeCapture.extractFrom` 顶层 `catch(Throwable){valid=false}` 与 `captureFromGui` 的 `catch(ignored)` ⇒ 推导失败无声无息；
  R4 前缀切分错误（见§五）；R5 流体幻影；R6 GT 的 `getResultStack()` 恒 null；R9 `MixinDefaultOverlayHandler` 的 `@Overwrite assignIngredients` 与 NEI 升级冲突。
- 诊断点：D1 `overlayRecipe` HEAD 打点（firstGui 类名 + handler 类名 + overlayIdentifier + recipeIndex）——第一优先，区分“注入没生效”与“配方类型没匹配”；
  D2 `getOverlayHandler` 返回 null 分支（记录 hasOverlay/canUseSmartOverlay）；D3 `fillCraftingGrid` 走哪条分支；D4 推导器出口统计（输入数/有矿辞/无矿辞/跳过流体/电路号/recipeMap/rules.size）；
  D5 确认按钮发包；D6 服务端入口（openContainer 类名 + 是否取到样板）；D7 直接复用 `SmartWildcardExpander.Result.describe()`。

## 八、待用户拍板（M2 开工前）

1. **原版填充如何共存**：(a) 加号=通配推导、**Shift+加号=原版填充**（推荐，`overlayRecipe(boolean shift)` 的 shift 形参现成）；
   (b) 只有槽里放着我们的通配样板时才接管；(c) 两者都要。
2. **是否允许点亮按钮**：GT 处理配方的加号今天是灰的；不放宽就点不动（用户的卷板机场景走不通）⇒ 需要用户同意改变这个原版行为。
3. 模型扩展：无矿辞兜底需要把 `(recipeMap, circuit)` 记进 `SmartWildcardState`（M1 的 `Rule` 只有 slot/oreDictMode/matcher/amount）——是扩展 `Rule` 还是复用 `circuit` 字段承载 recipeMap，待定。
