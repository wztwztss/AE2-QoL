# MOD_MAP 代码映射表
> AE2‑QoL‑1.7.10‑GTNH
> 记录功能与源码、Mixin文件的对应关系，方便快速定位代码。
> 路径：项目内部使用**相对根目录路径**；外部参考代码填写完整绝对路径，当前有效参考目录为 `E:\wzt\MC\modcreater\reference_src_290b3`（旧的 `reference_src_290b1_已过期` 已废弃，禁止复制其源码入库）

## AE2 QoL 主逻辑
| 功能简述 | 文件路径 |
|---|---|
| 智能倍增（核心逻辑） | `src/main/java/com/wztwzt/ae2_qof/mixin/ae/MixinCraftingCPUCluster.java` |
| 智能倍增（GT 仓最大轮数） | `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinMTEHatchInputBus.java` |
| 智能倍增（UI 开关） | `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinMTEHatchCraftingInputMEGui.java` |
| 智能倍增（GTNL 超级样板输入总成 GUI 开关，21504/21505） | `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinSuperCraftingInputHatchMEGui.java` |
| 智能倍增（配置） | `src/main/java/com/wztwzt/ae2_qof/Config.java` → `smartDoublingMaxRounds` |
| 智能倍增（C2S 开关包） | `src/main/java/com/wztwzt/ae2_qof/network/SmartDoublingTogglePacket.java` |
| 强化 IO 端口 | `src/main/java/com/wztwzt/ae2_qof/tile/TileExIOPort.java` |
| 强化 IO 端口（Mixin） | `src/main/java/com/wztwzt/ae2_qof/mixin/ae/MixinTileIOPort.java` |
| 库存检测覆盖板（主类） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/StockMonitorCover.java` |
| 库存检测覆盖板（数据类） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/StockMonitorCoverData.java` |
| 库存检测覆盖板（物品） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ItemStockMonitorCover.java` |
| 库存检测覆盖板（阈值模式枚举） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ThresholdMode.java` |
| 库存检测覆盖板（MUI2 GUI） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/gui/StockMonitorCoverGui.java` |
| 库存检测覆盖板（AE连接门面） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ae/AeConnector.java` |
| 库存检测覆盖板（无线通道-全反射） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ae/WirelessAeConnector.java` |
| 库存检测覆盖板（邻接直连通道） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ae/NeighborAeConnector.java` |
| 库存检测覆盖板（库存查询） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ae/AeStockReader.java` |
| 库存检测覆盖板（绑定命令） | `src/main/java/com/wztwzt/ae2_qof/CommandAe2QoL.java` → `smbind` |
| 二合一终端（GUI） | `src/main/java/com/wztwzt/ae2_qof/merged/GuiMergedTerminal.java` |
| 二合一终端（容器） | `src/main/java/com/wztwzt/ae2_qof/merged/ContainerMergedTerminal.java` |
| 二合一终端（方块） | `src/main/java/com/wztwzt/ae2_qof/merged/TileMergedTerminal.java` |
| 二合一终端（宿主统一接口，方块/部件/无线三形态） | `src/main/java/com/wztwzt/ae2_qof/api/IMergedTerminalHost.java` |
| 二合一终端面板（线缆部件形态） | `src/main/java/com/wztwzt/ae2_qof/merged/part/PartMergedTerminal.java` + `part/ItemPartMergedTerminal.java` |
| 无线二合一终端（手持形态） | `src/main/java/com/wztwzt/ae2_qof/merged/wireless/ItemWirelessMergedTerminal.java` + `wireless/WirelessMergedGuiObject.java` |
| 三形态 GUI 分发（ID 100 方块 / 110+side 部件 / 120 无线） | `src/main/java/com/wztwzt/ae2_qof/merged/MergedGuiHandler.java` |
| 按钮 tooltip 工具（ITooltip 文字按钮） | `src/main/java/com/wztwzt/ae2_qof/client/gui/TooltipTextButton.java` |
| GuideNH 游戏内指南（Markdown 资源，零代码集成） | `src/main/resources/assets/ae2_qof/guidenh/_zh_cn/*.md` + `_en_us/*.md` |
| 二合一终端（样板编码/上传） | `src/main/java/com/wztwzt/ae2_qof/merged/PatternContainer.java` |
| 上传/撤回/交换网络包 | `src/main/java/com/wztwzt/ae2_qof/network/UploadPatternPacket.java` / `RecallPatternPacket.java` / `SwapPatternPacket.java` |
| NEI Tooltip 文字（fix42 单入口） | `src/main/java/com/wztwzt/ae2_qof/client/nei/NetworkTooltipHandler.java` → `handleItemTooltip`；`handleTooltip` 透传；在 `ClientProxy.init` 注册 |
| NEI 叠加层（缓存） | `src/main/java/com/wztwzt/ae2_qof/client/NetworkInventoryCache.java` |
| NEI 叠加层（渲染） | `src/main/java/com/wztwzt/ae2_qof/client/NetworkInventoryDrawHandler.java` |
| 合成通知覆盖层 | `src/main/java/com/wztwzt/ae2_qof/client/render/CraftingNotificationOverlay.java` |
| 合成完成产物展示条（终端第一行 60s） | `src/main/java/com/wztwzt/ae2_qof/client/render/RecentCraftedOverlay.java` + `mixin/nei/MixinGuiMEMonitorable.java`（drawScreen/mouseClicked 注入） |
| 无限水岩浆磁盘 | `src/main/java/com/wztwzt/ae2_qof/item/ItemInfinityWaterLavaCell.java` |
| 无线收发器+连接器 | `src/main/java/com/wztwzt/ae2_qof/wireless/` 整包 |
| F 键搜索填充（F12） | `src/main/java/com/wztwzt/ae2_qof/client/event/KeyInputHandler.java`（按键处理）；`mixin/nei/MixinGuiRecipe.java` 只负责捕获当前 NEI 配方，两者不是同一功能 |
| 石英切割刀复制名称（F11） | `src/main/java/com/wztwzt/ae2_qof/client/event/KnifeNameCopyHandler.java` |
| 上传按钮注入 | `src/main/java/com/wztwzt/ae2_qof/client/event/GuiUploadButtonHandler.java` |
| 合并终端面板事件 | `src/main/java/com/wztwzt/ae2_qof/client/event/MergedTerminalPanelHandler.java` |
| 世界里键取物的客户端触发补丁（fix54） | `src/main/java/com/wztwzt/ae2_qof/client/PickBlockCompatHandler.java`（在 `ClientProxy.init()` 注册）。**为什么需要**：整合包内 `sciencenotleisure` 会在原版 `Minecraft.middleClickMouse()` 的 HEAD 取消它，GTNHLib 的 `PickBlockEvent` 不发出 ⇒ AE2 永不发 `PacketPickBlock` ⇒ `mixin/ae/MixinPacketPickBlock.java` 的兜底永不执行。本类改从 Forge `InputEvent.MouseInputEvent` 取触发点，条件对齐 AE2 `handlePickBlock()`，并预检「身上有无线终端」以免服务端刷 `PickBlockTerminalNotFound` |
| 库存统计终端 F22（GT 单方块，ID 32107；fix48 由 32101 让位而来） | `src/main/java/com/wztwzt/ae2_qof/terminal/StockMonitorTerminal.java`（方块/注册）+ `terminal/StockMonitorTerminalGui.java`（MUI2 UI，双端构建与授权见审查 A06–A08） |
| 库存统计终端无线端点 | `src/main/java/com/wztwzt/ae2_qof/terminal/StockMonitorTerminalWirelessEndpoint.java` |
| ME 任务检测器 F16（方块） | `src/main/java/com/wztwzt/ae2_qof/tile/TileQuestDetector.java` |
| ME 任务检测器 F16（BQ 检索逻辑） | `src/main/java/com/wztwzt/ae2_qof/quest/QuestDetectLogic.java`（NBT 候选去重用 `util/ItemIdentity`，见审查 A15） |
| 无限存储元件 F17（并入的 aeinfinitycell） | `src/main/java/cn/dancingsnow/aeinfinitycell/` 整包 + `network/InfinityCell*Packet.java`（保存/迁移风险见审查 A01） |
| 自适应电网统计口径 | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/GridEnergyStats.java`（净增/净减语义，见审查 A17） |
| 配方池检测工具 | `src/main/java/com/wztwzt/ae2_qof/util/RecipeMapDetector.java` |
| 终端容器解析工具 | `src/main/java/com/wztwzt/ae2_qof/util/ContainerTerminalResolver.java` |
| 重规划 | `src/main/java/com/wztwzt/ae2_qof/util/Replanner.java` |
| v7 机器贴图工具（**方案 B**：`MixinTextureMap` 在图集 `registerIcons()` 尾部补注册 + 图标回填；`forceMode` 三态开关） | `src/main/java/com/wztwzt/ae2_qof/util/ModTextures.java` — 8 机器面级 `getTexture` 均以其就绪判定门控；`forceMode` 由 `Config.v7Textures`（`v7_textures`）驱动；退化 UV（全 0）回退 GT 机箱 |
| v7 贴图启用判定时机（postInit 钩子） | `src/main/java/com/wztwzt/ae2_qof/CommonProxy.java` → `postInit()` 调 `ModTextures.allowResourceCheck()`；**不可提前到 init**——init 阶段资源可能处于 reload 中间态会误判为可用 |
| 精确物品身份键（NBT 敏感，数量不入键） | `src/main/java/com/wztwzt/ae2_qof/util/ItemIdentity.java` — 用于 `client/NetworkInventoryCache`（审查 A14 的 NBT 变体覆盖）、`quest/QuestDetectLogic` 候选去重（A15）、`network/MergedTerminalScrollReplacePacket` 候选环（A13） |
| 无线 EU 存款助手（仅 deposit；输入侧为镜像余额语义） | `src/main/java/com/wztwzt/ae2_qof/util/WirelessEnergyTransfer.java` — 对应审查报告 A04 的用户决策（保留 3.18.0 镜像实时模型） |
| 万能维护仓（主类） | `src/main/java/com/wztwzt/ae2_qof/hatch/AE2MaintenanceHatchUniversal.java` |
| 万能维护仓（Mixin） | `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinMTEMultiBlockBase.java` |
| 万能维护仓（注册） | `src/main/java/com/wztwzt/ae2_qof/CommonProxy.java` → `init()` — **必须在init阶段注册**，preInit时GT的sPreloadStarted为false会抛IllegalAccessError |
| 万能维护仓（电路板槽校验，fix50） | `src/main/java/com/wztwzt/ae2_qof/hatch/AE2MaintenanceHatchUniversal.java` → `func_94041_b(int,ItemStack)`（`IInventory.isItemValidForSlot` 的 **SRG 名**，因编译用的 GT jar 未反混淆）；只放行 `circuitLevelOf()` 认得的各电压电路板。背景：GT 5.09.54 新接通 `MTEItemStackHandler.isItemValid → MTEHatchMaintenance.func_94041_b → IsAutoMaintenanceInput`，本仓 `aAuto=false` 导致槽位 0 全拒 |
| 自适应电网终端（ID 32106，5-tab PagedWidget UI；fix48 由 32100 让位而来） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetTerminal.java` |
| 自适应电网输入仓（ID 32102） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetHatch.java` |
| 自适应电网激光源仓（ID 32103） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetLaserHatch.java` |
| 自适应电网动力仓（ID 32104） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetDynamoHatch.java` |
| 自适应电网激光靶仓（ID 32105） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetLaserTargetHatch.java` |
| 自适应电网管理器（仓注册/迁移） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetworkManager.java` |
| 自适应电网网络（4类型仓集合） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetwork.java` |
| 自适应仓组合Helper（绑定/NBT/电压） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveHatchHelper.java` |
| 仓类型枚举（DYNAMO/ENERGY/LASER_SOURCE/LASER_TARGET） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/HatchType.java` |
| 无线能源输入终端 | `src/main/java/com/wztwzt/ae2_qof/hatch/wireless/WirelessEnergyInputTerminal.java` |
| 无线能源输出终端（ID 32110 / 输入 32111） | `src/main/java/com/wztwzt/ae2_qof/hatch/wireless/WirelessEnergyOutputTerminal.java` |
| 网络数据棒（自适应电网配置读写） | `src/main/java/com/wztwzt/ae2_qof/item/ItemNetworkDataStick.java` |
| 配置与热加载 | `src/main/java/com/wztwzt/ae2_qof/Config.java`（`smartDoublingMaxRounds`、`ioPortRate`、`v7Textures` 等；`ensureFresh()` 已 synchronized，见 P1-031） |
| 命令入口 | `src/main/java/com/wztwzt/ae2_qof/CommandAe2QoL.java`（`/ae2qof reload|status`、覆盖板 `smbind`） |

## Mixin列表

> 与 `src/main/resources/mixins.ae2_qof.json`（及根目录同内容副本）逐条对齐：通用段 13 条、client 段 16 条，共 **29 条**。
> 配置列统一为 `mixins.ae2_qof.json`，下表不再重复填写。

| 段 | Mixin类路径 | 目标类 | 注入点/备注 |
|---|---|---|---|
| 通用 | `mixin/ae/MixinCraftingCPUCluster.java` | `appeng.me.cluster.implementations.CraftingCPUCluster` | `submitJob` RETURN、`completeJob` TAIL、`executeCrafting` HEAD(cancellable)；合成通知 + 智能倍增；`knownBusyMediums` 冷却 |
| 通用 | `mixin/ae/MixinTileIOPort.java` | `appeng.tile.storage.TileIOPort` | `transferContents` HEAD（@ModifyVariable）强化 IO 倍率；**fix51 追加** `tickingRequest` RETURN 注入 `ae2qol$fanOutExtraChannels`——对无限磁盘逐通道补搬。背景：AE2 的 `getInv` 每元件只取**第一个**匹配通道并 `break`，多通道元件因此只搬一个通道。仅放行 `ItemInfinityStorageCell`，单通道元件立即跳过 |
| 通用 | `mixin/ae/MixinDualityInterface.java` | `appeng.helpers.DualityInterface` | `writeToNBT`/`readFromNBT` TAIL；实现 `ISmartDoublingMedium` |
| 通用 | `mixin/ae/MixinContainerInterface.java` | `appeng.container.implementations.ContainerInterface` | `<init>` RETURN；`@GuiSync(30)` 倍增同步字段（避开 AE2 0/1/3~18 与 GTNL 19） |
| 通用 | `mixin/ae/MixinPinsHolder.java` | `appeng.items.contents.PinsHolder` | `getCraftingPinsRows` Redirect；pin 行默认行为 |
| 通用 | `mixin/ae/MixinPacketPickBlock.java` | `appeng.core.sync.packets.PacketPickBlock` | `serverPacketData` HEAD，`remap = false`（**fix52 起不再 cancellable**）；仅「无存量 + 有样板」时接管——判定同步完成，**开界面归队服务端 tick 线程**（`ServerTerminalHelper.scheduleServerTask`），因为 AE2 包处理器运行在网络线程上；其余一律放行原版 |
| 通用 | `mixin/gt/MixinMTEHatchInputBus.java` | `gregtech.common.tileentities.hatches.crafting.MTEHatchInputBus` | `saveNBTData`/`loadNBTData` TAIL；`getMaxMultiplier` 返回配置上限 |
| 通用 | `mixin/gt/MixinMTEMultiBlockBase.java` | `gregtech.api.metatileentity.implementations.MTEMultiBlockBase` | **`@Overwrite shouldCheckMaintenance` 返回 false（全局维护绕过，风险已知）** |
| 通用 | `mixin/gt/MixinCommonBaseMetaTileEntityMultiblockRegistry.java` | `gregtech.api.metatileentity.CommonBaseMetaTileEntity` | `handleFirstTick` TAIL；多方块主机识别注册表（供上传目标识别多方块主机） |
| 通用 | `mixin/gt/MixinProcessingLogicSpeed.java` | `gregtech.api.recipe.ProcessingLogic`（GT 跨配方并行） | threads>1 时接管 `process()`；EU/时长/输出合并的饱和判界 |
| 通用 | `mixin/gt/MixinBaseMetaTileEntityIdMigration.java` | `gregtech.api.metatileentity.BaseMetaTileEntity` | `setInitialValuesAsNBT` HEAD，`remap=false`；**fix48** 旧存档 32100/32101 → 32106/32107（仅当 NBT 带 `ae2qol*` 专属键） |
| 通用 | `mixin/TileDriveMixin.java` | `appeng.tile.storage.TileDrive` | `updateState` RETURN，`remap=false`；Infinity Cell 挂载 cellsMap |
| 通用 | `mixin/client/MixinTextureMap.java` | `net.minecraft.client.renderer.texture.TextureMap` | **每次** `registerIcons()`/`func_110573_f()` 尾部补注册 25 张 v7 路径（仅 `textureType == 0`）；图标回填 `ModTextures.registerBaked` |
| client | `mixin/nei/MixinRecipeHandlerRef.java` | `codechicken.nei.recipe.RecipeHandlerRef` | `fillCraftingGrid`/`craft` HEAD，`remap=false`；捕获配方 handler 名与 GT 配方池 ID |
| client | `mixin/nei/MixinDefaultOverlayHandler.java` | `codechicken.nei.recipe.DefaultOverlayHandler` | `transferRecipe` HEAD(cancellable) + `@Overwrite assignIngredients`；合并终端 NEI 直传 + 书签优先级 |
| client | `mixin/nei/MixinGuiOverlayButton.java` | `codechicken.nei.recipe.GuiOverlayButton` | `updateEnabled` TAIL、`overlayRecipe`/`canFillCraftingGrid` HEAD |
| client | `mixin/nei/MixinGuiMEMonitorable.java` | `appeng.client.gui.implementations.GuiMEMonitorable` | `postUpdate` HEAD、`setPinsRows`/`setAEPins` TAIL；库存缓存 + pin 行自动扩展（精确排除子类） |
| client | `mixin/nei/MixinPanelWidgetDraw.java` | `codechicken.nei.PanelWidget` | `draw` TAIL；左侧面板库存角标 |
| client | `mixin/nei/MixinNEIRecipeWidget.java` | `codechicken.nei.recipe.NEIRecipeWidget` | `draw` TAIL；配方格库存/可合成角标（不追加 Tooltip 文字） |
| client | `mixin/nei/MixinPanelWidgetClick.java` | `codechicken.nei.PanelWidget` | `handleClick` HEAD；Shift+左键取物、中键下单 |
| client | `mixin/nei/MixinGuiRecipe.java` | `codechicken.nei.recipe.GuiRecipe` | `updateScreen` + SRG `func_73876_c` 双注入（fix45 补生产环境挂空）；捕获当前浏览配方 |
| client | `mixin/ae/MixinGuiCraftConfirm.java` | `appeng.client.gui.crafting.GuiCraftConfirm` | 合成提交/产物捕获 |
| client | `mixin/ae/MixinGuiInterface.java` | `appeng.client.gui.implementations.GuiInterface` | `addButtons`/`actionPerformed`/`drawFG` TAIL；智能倍增开关按钮 |
| client | `mixin/ae/MixinGuiSuperInterface.java` | `com.science.gtnl.client.gui.GuiSuperInterface` | 同上；GTNL 可选依赖 |
| client | `mixin/ae/MixinGuiSuperDualInterface.java` | `com.science.gtnl.client.gui.GuiSuperDualInterface` | 同上；方块形态多一个 sidelessMode 按钮 |
| client | `mixin/gt/MixinMTEHatchCraftingInputMEGui.java` | `gregtech.common.gui.modularui.hatch.MTEHatchCraftingInputMEGui` | `<init>` TAIL、`createBottomLeftCornerFlow` RETURN；倍增开关 |
| client | `mixin/gt/MixinSuperCraftingInputHatchMEGui.java` | `com.science.gtnl.common.gui.modularui.SuperCraftingInputHatchMEGui` | 同上；GTNL 21504/21505 |
| client | `mixin/gt/MixinDualInputHatchUI.java` | `reobf.proghatches.gt.metatileentity.DualInputHatch` | `populateUI` RETURN；PH 可选依赖 |
| client | `mixin/GuiContainerAccessor.java` | `net.minecraft.client.gui.inventory.GuiContainer` | 纯 Accessor：`guiLeft`/`guiTop`/`ySize` |
