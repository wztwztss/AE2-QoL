# Mixin 笔记

> AE2‑QoL‑1.7.10‑GTNH
> 记录所有Mixin注入点、风险、修改历史、已知副作用。

> 重要提醒
> 1. MC1.7.10 SRG名称容易变动，修改前核对方法签名。
> 2. 修改后jar放入测试环境，查看mixin.log确认注入状态。
> 3. 参考其他模组Mixin实现：`E:\wzt\MC\modcreater\reference_src_290b3`（旧的 `reference_src_290b1_已过期` 已废弃）。

> 当前基线：GTNH **2.9.0-beta-3** / MC 1.7.10 / 版本 `3.19.0-fix49`。
> 清单与 `src/main/resources/mixins.ae2_qof.json` 逐条对齐（通用 13 条 + client 16 条 = 29 条）。

---

## Mixin 清单

### 智能倍增 (Smart Doubling) - 10个Mixin

| Mixin 类路径 | 目标类 | 注入点 | 风险/说明 |
|---|---|---|---|
| `mixin/ae/MixinDualityInterface.java` | `appeng.helpers.DualityInterface` | `writeToNBT` TAIL; `readFromNBT` TAIL; 实现 `ISmartDoublingMedium` | 为 ME 接口实现智能倍增能力。`getMaxMultiplier` 使用指数扩张+二分法探测最大可推送轮数。仅对非craftable、非流体、非假合成、非阻塞模式生效。开关通过NBT持久化。 |
| `mixin/ae/MixinContainerInterface.java` | `appeng.container.implementations.ContainerInterface` | `<init>` RETURN; `@GuiSync(30)` 字段 | ME 接口容器增加智能倍增同步字段。同步id 30避免与AE2原有0/1/3~18和GTNL的19冲突。实现 `ISmartDoublingContainer`。 |
| `mixin/ae/MixinCraftingCPUCluster.java` | `appeng.me.cluster.implementations.CraftingCPUCluster` | `submitJob` RETURN; `handleCraftBranchFailure` TAIL; `completeJob` TAIL; `executeCrafting` HEAD (cancellable) | 核心mixin：(1) 合成完成通知：捕获玩家提交任务信息，完成后发送CraftingCompletePacket；(2) 智能倍增：在executeCrafting HEAD注入，当存在启用智能倍增的介质且剩余轮数>1时接管整个tick。通过反射访问私有内部类。 |
| `mixin/ae/MixinGuiInterface.java` | `appeng.client.gui.implementations.GuiInterface` | `addButtons` TAIL; `actionPerformed` TAIL; `drawFG` TAIL | 在原版ME接口GUI左侧按钮列末尾追加"智能倍增"开关按钮。点击时发送SmartDoublingTogglePacket。 |
| `mixin/ae/MixinGuiSuperInterface.java` | `com.science.gtnl.client.gui.GuiSuperInterface` | `addButtons` TAIL; `func_146284_a` TAIL; `drawFG` TAIL | 在GTNL超级接口GUI追加智能倍增开关。仅当容器实现ISmartDoublingContainer时显示。GTNL为运行时可选依赖。 |
| `mixin/ae/MixinGuiSuperDualInterface.java` | `com.science.gtnl.client.gui.GuiSuperDualInterface` | `addButtons` TAIL; `func_146284_a` TAIL; `drawFG` TAIL | 在GTNL超级二合一ME接口GUI追加智能倍增开关。方块形态多一个sidelessMode按钮，按钮列整体下移18像素。 |
| `mixin/gt/MixinMTEHatchInputBus.java` | `gregtech.api.metatileentity.implementations.MTEHatchInputBus` | `saveNBTData` TAIL; `loadNBTData` TAIL; 实现 `ISmartDoublingMedium` | 为GT系样板输入仓实现智能倍增能力。仅当机器实现ICraftingProvider时倍增生效。getMaxMultiplier直接返回配置上限（GT仓缓冲无上限）。 |
| `mixin/gt/MixinMTEHatchCraftingInputMEGui.java` | `gregtech.common.gui.modularui.hatch.MTEHatchCraftingInputMEGui` | `<init>` TAIL; `createBottomLeftCornerFlow` RETURN | 在GT样板输入仓GUI（2714/2715）底部按钮行追加智能倍增开关。通过BooleanSyncValue双向同步。 |
| `mixin/gt/MixinSuperCraftingInputHatchMEGui.java` | `com.science.gtnl.common.gui.modularui.SuperCraftingInputHatchMEGui` | `<init>` TAIL; `createBottomLeftCornerFlow` RETURN | 在GTNL超级样板输入总成(21504/21505)底部按钮行追加智能倍增开关。GTNL为运行时可选依赖。 |
| `mixin/gt/MixinDualInputHatchUI.java` | `reobf.proghatches.gt.metatileentity.DualInputHatch` | `populateUI` RETURN | 在ProgrammableHatches的DualInputHatch（22130/22179）UI追加智能倍增开关。仅对实现ICraftingProvider的子类显示。PH为运行时可选依赖。 |

### NEI 增强/覆盖层 - 6个Mixin

| Mixin 类路径 | 目标类 | 注入点 | 风险/说明 |
|---|---|---|---|
| `mixin/nei/MixinGuiRecipe.java` | `codechicken.nei.recipe.GuiRecipe` (remap=false) | `updateScreen` HEAD | 每次屏幕更新时捕获玩家当前浏览的NEI配方，存储到NeiRecipeCapture，供合并终端"一键填充"按钮读取。 |
| `mixin/nei/MixinGuiOverlayButton.java` | `codechicken.nei.recipe.GuiOverlayButton` (remap=false) | `updateEnabled` TAIL; `overlayRecipe` HEAD (cancellable); `canFillCraftingGrid` HEAD (cancellable) | 合并终端增强NEI"+"按钮：(1) 每帧强制按钮可用；(2) 点击时无条件直传填充样板面板；(3) canFillCraftingGrid对合并终端始终返回true。 |
| `mixin/nei/MixinDefaultOverlayHandler.java` | `codechicken.nei.recipe.DefaultOverlayHandler` (remap=false) | `transferRecipe` HEAD (cancellable); `@Overwrite` assignIngredients | 两大功能：(1) 合并终端NEI直传：拦截transferRecipe直传填充样板面板；(2) 书签优先级分配：重写assignIngredients加入NEI书签优先级加分。 |
| `mixin/nei/MixinPanelWidgetClick.java` | `codechicken.nei.PanelWidget` (remap=false) | `handleClick` HEAD (cancellable) | NEI面板点击快捷操作：(1) Shift+左键从AE2网络取出一组物品；(2) 中键打开AE2合成确认界面。与AE2Things兼容。 |
| `mixin/nei/MixinNEIRecipeWidget.java` | `codechicken.nei.recipe.NEIRecipeWidget` (remap=false) | `draw` TAIL | 在NEI配方界面每个物品格上叠加显示：可合成物品->编码样板小图标；有库存物品->数量角标。受OverlayConfig开关控制。 |
| `mixin/nei/MixinPanelWidgetDraw.java` | `codechicken.nei.PanelWidget` (remap=false) | `draw` TAIL | 在NEI物品面板（左半屏）每个格子上叠加AE2网络库存信息（数量角标、可合成标记）。仅对左侧面板生效。 |

### 网络库存缓存/合成通知 - 2个Mixin

| Mixin 类路径 | 目标类 | 注入点 | 风险/说明 |
|---|---|---|---|
| `mixin/nei/MixinGuiMEMonitorable.java` | `appeng.client.gui.implementations.GuiMEMonitorable` (remap=false) | `postUpdate` HEAD; `setPinsRows` TAIL; `setAEPins` TAIL | 两个功能：(1) 网络库存缓存：在终端postUpdate时缓存网络物品/流体数据到NetworkInventoryCache；(2) 合成pin行自动扩展：当pin产物种类超过当前crafting行容量时自动提升可见行数。仅标准终端：getClass()==GuiMEMonitorable.class精确排除子类。 |
| `mixin/ae/MixinCraftingCPUCluster.java` | `appeng.me.cluster.implementations.CraftingCPUCluster` | `submitJob` RETURN; `completeJob` TAIL | 合成完成通知：捕获玩家提交任务时的信息（玩家、输出物品、网络密钥），任务完成后扫描玩家背包找到匹配网络密钥的INetworkEncodable，向玩家发送CraftingCompletePacket。 |

### 自动样板上传辅助 - 2个Mixin

| Mixin 类路径 | 目标类 | 注入点 | 风险/说明 |
|---|---|---|---|
| `mixin/nei/MixinDefaultOverlayHandler.java` | `codechicken.nei.recipe.DefaultOverlayHandler` (remap=false) | `transferRecipe` HEAD | 拦截配方传输，捕获配方名和GT配方池ID，供自动样板上传使用。 |
| `mixin/nei/MixinRecipeHandlerRef.java` | `codechicken.nei.recipe.RecipeHandlerRef` (remap=false) | `fillCraftingGrid` HEAD; `craft` HEAD | 在NEI配方引用的填充/合成操作前，捕获当前配方的handler名称和GT配方池ID，存储到ClientRecipeNameUtil。 |

### 其他 QoL - 3个Mixin

| Mixin 类路径 | 目标类 | 注入点 | 风险/说明 |
|---|---|---|---|
| `mixin/TileDriveMixin.java` | `appeng.tile.storage.TileDrive` (remap=false) | `updateState` RETURN | 为AE2 Infinity Cell提供兼容。在Drive更新状态后，遍历所有槽位，对ItemInfinityStorageCell实例将其handler附加到cellsMap。 |
| `mixin/ae/MixinTileIOPort.java` | `appeng.tile.storage.TileIOPort` | `transferContents` HEAD (ModifyVariable) | 强化版IO端口(TileExIOPort)的传输倍率。当目标为TileExIOPort实例时，根据配置将每次传输物品数量乘以配置倍率，带溢出保护。 |
| `mixin/ae/MixinPinsHolder.java` | `appeng.items.contents.PinsHolder` (remap=false) | `getCraftingPinsRows` (Redirect) | 合成产物pin行默认开启。原版对"从未设置过的玩家"默认返回DISABLED，此处改为ONE（当配置pinRowEnabled开启时）。 |

### Accessor - 1个Mixin

| Mixin 类路径 | 目标类 | 注入点 | 风险/说明 |
|---|---|---|---|
| `mixin/GuiContainerAccessor.java` | `net.minecraft.client.gui.inventory.GuiContainer` | 无注入，纯Accessor | 暴露GuiContainer私有字段guiLeft、guiTop、ySize，供其他mixin读取GUI布局坐标。 |

### 万能维护仓 - 1个Mixin

| Mixin 类路径 | 目标类 | 注入点 | 风险/说明 |
|---|---|---|---|
| `mixin/gt/MixinMTEMultiBlockBase.java` | `gregtech.api.metatileentity.implementations.MTEMultiBlockBase` | `@Overwrite shouldCheckMaintenance()` | **风险：@Overwrite**。重写整个方法返回 false，使所有多方块机器永远无维护问题。非 SRG 方法（`remap = false`），GTNH 环境下方法名稳定。若其他模组也 @Overwrite 此方法会冲突（目前未发现）。构造函数与 loadNBTData 中 `if (!shouldCheckMaintenance()) fixAllIssues()` 自动修复所有标志位。 |

### 上传取物 / 材质 / GT 注册与迁移 - 6个Mixin

| Mixin 类路径 | 目标类 | 注入点 | 风险/说明 |
|---|---|---|---|
| `mixin/ae/MixinPacketPickBlock.java` | `appeng.core.sync.packets.PacketPickBlock` | `serverPacketData` HEAD (cancellable)，`remap = false` | fix47：世界里中键取物的兜底。判定顺序刻意做成"能不管就不管"——解析不出物品 / 背包已有 / 无可用无线终端 / 网络还有存量 一律放行原版，仅「无存量 + 有样板」才接管并 `cancel()`。被点物品经反射读私有 `pickedBlock`，取不到就放行。整个注入体 try/catch 包裹，异常一律退化为放行。 |
| `mixin/client/MixinTextureMap.java` | `net.minecraft.client.renderer.texture.TextureMap` | `registerIcons` / `func_110573_f` TAIL，`remap = false` | fix45/fix46 v7 材质方案 B：**每次**调用都补注册 25 张 v7 路径，仅 `textureType == 0` 生效；图标回填 `ModTextures.registerBaked`。**不可改成"只注册一次"**——`registerIcons()` 开头会 `mapRegisteredSprites.clear()`，启动期会多次执行，只注册一次会让真正装载的那一轮没有这些路径，sprite 尺寸 0×0 → 机器透明。反射解析 MCP/SRG 两套成员名。 |
| `mixin/gt/MixinBaseMetaTileEntityIdMigration.java` | `gregtech.api.metatileentity.BaseMetaTileEntity` | `setInitialValuesAsNBT(Lnet/minecraft/nbt/NBTTagCompound;S)V` HEAD，`remap = false` | fix48：旧存档 MTE ID 迁移。仅当 `mID` 为 32100/32101 **且** NBT 含本模组专属键（`ae2qolNO`/`ae2qolNF`/`ae2qolVT`）时改写为 32106/32107；fissionevolved 的 `Fission*` 键不会误判。方法名按完整描述符写，避免重载歧义。 |
| `mixin/gt/MixinProcessingLogicSpeed.java` | `gregtech.api.recipe.ProcessingLogic` | `process` HEAD (cancellable)，`remap = false` | 仅当 `machine instanceof MTEMultiBlockBase` 时接管；跨配方并行与 EU/时长/输出合并的饱和判界。**风险点见审查 A02/A03/A19**：绕过了原生 `validateRecipe`/`applyRecipe` 链，降回 1 或拆仓后 supplier 覆盖未显式恢复。 |
| `mixin/gt/MixinCommonBaseMetaTileEntityMultiblockRegistry.java` | `gregtech.api.metatileentity.CommonBaseMetaTileEntity` | `handleFirstTick` TAIL，`remap = false` | 多方块主机识别注册表：首 tick 登记，供上传目标识别多方块主机（fix14 起）。仅对 `IGregTechTileEntity` 生效。 |
| `mixin/ae/MixinGuiCraftConfirm.java` | `appeng.client.gui.crafting.GuiCraftConfirm` | `initGui` TAIL、`actionPerformed` HEAD(cancellable)、`drawFG` HEAD | 合成确认界面的提交/产物捕获与按钮；`drawFG` 分支用 try/catch 兜底（见 P2-027 的静默捕获策略）。 |

---

## 已知风险

> 记录容易踩坑、会和其他模组冲突的注入点

1. **`split("\\n")` 陷阱**：Java 正则中 `\n` 匹配真实换行符 LF，**不是**字面反斜杠+n。拆 lang 字面 `\n` 必须用 `TooltipTextButton.langLines(key)`（内部 `replace("\\n","\n")` 字面替换）后再 `split("\n")`。3.3.7~3.9.0 的三处 ModularUI mixin 曾因此换行从未生效（3.10.0 修复）。

2. **Mixin target 静默失效**：Mixin target 写错会静默失效，不崩溃但功能无效，必须以 mixin.log 为准。

3. **AE2部分类被GTNH修改**：不能直接照搬原版AE2的Mixin，需核对GTNH 2.9.0-beta-3（rv3-beta-1050）的实际类结构。已知 API 变动例：`BlockIOPort.getRenderer()` 返回类型收窄为 `RenderIOPort`、`ContainerPatternTerm.outputSlotsClient` 字段移除、`IInterfaceViewable.getNameSuffix()` 由 `String` 改为 `IChatComponent`（均在 fix49 处理）。

4. **可选依赖的Mixin**：GTNL、ProgrammableHatches 等为运行时可选依赖，目标类缺失时需静默跳过（resources 配置 `required:false`，但**没有** `IMixinConfigPlugin`，裁剪环境未验证，见 P2-029）。

5. **反射访问私有字段**：MixinCraftingCPUCluster 通过反射访问私有内部类（TaskProgress、finalOutput、CraftingCpuDiagnostics），需注意混淆映射。同类做法还有 `MixinPacketPickBlock` 读 `pickedBlock`、`MixinTextureMap` 解析图集成员、`ClientProxy.applyClientSwap` 反射读样板终端的私有 `outputs`。

6. **MTE 数字 ID 是独占资源**：ID 只写数字、不写类名，重号会让注册直接失败（启动期抛 `IllegalArgumentException`，且常被别的类加载错误掩盖）。新增/改号前必须查 `docs/dumps/metatileentity.csv`（b3 全表），且**换号必须配套 `MixinBaseMetaTileEntityIdMigration` 之类的存档迁移**，否则旧存档终端配置丢失。历史教训：32001（GT 本体占用）→ 32101（b3 fissionevolved 占用）→ 32107。

7. **图集"sprite 尺寸 0×0"= 没进图集**：`TextureMap` 的 `registerIcons()` 会先 clear 清单，且启动期会执行多次；任何"只注册一次"的开关都会让真正装载的那一轮清单缺失，渲染结果不是紫黑而是**透明**（UV 全 0），不要误判为资源包问题。

---

## 按功能域汇总

> 与 `mixins.ae2_qof.json` 对齐，共 **29 条**（部分类跨域复用，故分域计数之和大于 29）。

| 功能域 | 文件数 | 涉及文件 |
|--------|--------|----------|
| 智能倍增 (Smart Doubling) | 10 | MixinDualityInterface, MixinContainerInterface, MixinCraftingCPUCluster, MixinGuiInterface, MixinGuiSuperInterface, MixinGuiSuperDualInterface, MixinMTEHatchInputBus, MixinMTEHatchCraftingInputMEGui, MixinDualInputHatchUI, MixinSuperCraftingInputHatchMEGui |
| NEI 增强/覆盖层 | 6 | MixinGuiRecipe, MixinGuiOverlayButton, MixinDefaultOverlayHandler, MixinPanelWidgetClick, MixinNEIRecipeWidget, MixinPanelWidgetDraw |
| 网络库存缓存/合成通知 | 2 | MixinGuiMEMonitorable, MixinCraftingCPUCluster |
| 自动样板上传辅助 | 2 | MixinDefaultOverlayHandler, MixinRecipeHandlerRef |
| 其他 QoL | 3 | TileDriveMixin, MixinTileIOPort, MixinPinsHolder |
| Accessor | 1 | GuiContainerAccessor |
| 万能维护仓 | 1 | MixinMTEMultiBlockBase |
| 上传取物/材质/GT 注册与迁移 | 6 | MixinPacketPickBlock, MixinTextureMap, MixinBaseMetaTileEntityIdMigration, MixinProcessingLogicSpeed, MixinCommonBaseMetaTileEntityMultiblockRegistry, MixinGuiCraftConfirm |
