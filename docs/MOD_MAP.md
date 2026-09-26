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
| ↑ 页面 `icon:`/`item_ids:` 的写法 | 只能写**真实注册名**：`modid:name`；GT 机器写 `gregtech:gt.blockmachines:<MTE ID>`（需覆盖 NBT 时再接 `:{SNBT}`）。机器内部名**不是**物品名 —— 3.20.2 修过 5 页共 10 个文件的这类错误（见 CHANGELOG 记录 (24)） |
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
| 库存统计终端 F22（GT 单方块，ID 32107；fix48 由 32101 让位而来） | `src/main/java/com/wztwzt/ae2_qof/terminal/StockMonitorTerminal.java`（方块/注册）+ `terminal/StockMonitorTerminalGui.java`（MUI2 UI，双端构建与授权见审查 A06–A08）。**3.20.3 重写**：控件结构双端一致（去掉 `if (isServer)`）、两个变长列表经 `GenericListSyncHandler`+`DynamicSyncedWidget` 按服务端快照渲染成可滚动 `ListWidget`、编辑值全走 SyncValue（权限校验在服务端 setter）、发信器枚举改走 `node.getMachine()`。坑位见 skill 第 21/22 条与 CHANGELOG 记录 (25) |
| 库存统计终端：Nexus 缺失时的回退网络选择面板（3.20.3 新增） | `src/main/java/com/wztwzt/ae2_qof/terminal/StockMonitorTerminalNetworkPanel.java`（与覆盖板的 `cover/stockmonitor/gui/NetworkSelectPanel` 是兄弟实现；刻意不改覆盖板那份，保持"不触碰已确认可用代码"的边界） |
| 库存统计终端：行内【高亮】【传送】请求包（3.21.0 新增） | `src/main/java/com/wztwzt/ae2_qof/network/StockMonitorActionPacket.java`（C2S 只发坐标；服务端 tick 线程内重新解析目标 + 会话/`isActiveViewer` + AE BUILD 权限校验后执行；高亮复用 `WirelessHighlightPacket` 200 tick 自动清除，传送复用 `HatchActionPacket` 那份匿名 `Teleporter`） |
| 库存统计终端：覆盖板列表范围（3.21.1） | 在 `StockMonitorTerminalGui.CoverScan` 内：只列 `CoverRegistry.getByNetwork(终端 networkId)` 的覆盖板；被过滤数量经 `IntSyncValue` S2C，空列表时显示"另有 N 个属于其他网络" |
| 智能通配样板（3.22.0 已交付） | `wildcard/SmartWildcardState.java`（规则/黑白名单/自带电路/自带不消耗物品/修订号，模板留在原生 in/out）+ `wildcard/SmartWildcardExpander.java`（索引期展开：逐候选克隆模板并改写 in/out，带 512 上限 + LRU 缓存 + 超限 WARN）+ `wildcard/ItemSmartWildcardPattern.java`（继承 AE2 原版 `ItemEncodedPattern`，所有总成天然接受）+ `mixin/ae/MixinDualityInterface` 的 `addToCraftingList` 注入 + `/ae2qof wildcard [矿辞前缀]` 自测命令。**M2（NEI 加号推导 + 可视化界面）与 M3（每槽电路）未交付**；调研报告见 `docs/research/wildcardpattern-forensics.md` |
| 智能倍增：单次推送上限配置（3.21.4） | `Config.smartDoublingPushCap`（键 `smart_doubling_push_cap`，默认 4096）→ 被 `mixin/ae/MixinCraftingCPUCluster` 的功率/推送钳制读取；可在 `settings.json`、`client/gui/GuiConfigScreen`（Mods → AE2 QoL → Config）、`/ae2qof status` 查看与修改 |
| 智能倍增：开关的跨端写入与回读（3.21.3 重构） | `network/SmartDoublingTogglePacket.java`（C2S：容器 / **坐标设置** / **坐标查询** 三模式；服务端按坐标重定位 MTE 并校验 `ISmartDoublingMedium`）+ `network/SmartDoublingStatePacket.java`（**新增** S2C：服务端权威值写回客户端机器对象，供界面显示）。原因：三处开关 mixin 在 `client` 段，`BooleanSyncValue.allowC2S()` 依赖服务端存在同名同步处理器，专用服务端上会被 MUI2 静默丢弃（详见 CHANGELOG 记录 (29)） |
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

---

## 编程样板输入总成 MK.III（3.20.0 新增 · ProgrammableHatches 可选依赖）

> 目标：把 PH「编程样板输入总成」（`hatch.input.buffered.me`，MTE 22069）扩容克隆成一台新机器，
> 样板槽 36 → **144**，仅在安装 PH 时存在。**不改动 PH 本体**。

| 类别 | 文件路径 | 说明 |
|---|---|---|
| MTE 主体 | `src/main/java/com/wztwzt/ae2_qof/ph/MTEPatternCraftingBufferMKIII.java` | 继承 PH `PatternDualInputHatch`；MTE ID **32108**，内部名 `ae2qof.hatch.input.buffered.me.mkiii`；`page()=2`、`rows()=16`、`rowSize()=9`；覆写 `getStackForm`/`getMachineCraftingIcon`（模板实例 base 为 null 会 NPE）、`loadNBTData`（补齐被 PH 缩回 36 的倍率数组）、`newMetaEntity`（返回自己的 `Inst`）、`createPatternWindow2`（9 列可滚动样板窗） |
| 窗口部件 | `src/main/java/com/wztwzt/ae2_qof/ph/PatternWindowWidgets.java` | PH 三个包私有内部部件（`DragTab`/`PanelDragForwarder`/`NonInteractiveText`）与两个 private 按钮工厂的等价副本（跨包无法复用，只用 MUI2 公开 API） |
| 可选依赖入口 | `src/main/java/com/wztwzt/ae2_qof/ph/PhIntegration.java` | `Loader.isModLoaded("proghatches")` 守卫 → 注册 MTE + 工作台配方 + `InterfaceTerminalRegistry.register(Inst.class)`；`mkiiiStack` 供创造页使用 |
| Mixin | `src/main/java/com/wztwzt/ae2_qof/mixin/ph/MixinPatternDualInputHatchAccess.java` | 接口式 accessor：读写 PH 的 `pattern`/`multiplier`/`patternItemCache`/`patternDetailCache`；`@Invoker` `onPatternChange()` / `refundAll()`（均在公共 `mixins` 列表） |
| 注册调用点 | `src/main/java/com/wztwzt/ae2_qof/CommonProxy.java` → `init` 末尾 | `PhIntegration.register()` |
| 创造页 | `src/main/java/com/wztwzt/ae2_qof/AE2QoLCreativeTab.java` → `displayAllReleventItems` | GT 机器走 `sBlockMachines` meta 值，`setCreativeTab` 管不到，需显式追加 |
| 语言文件 | `assets/ae2_qof/lang/zh_CN.lang` + `en_US.lang` | `gt.blockmachines.ae2qof.hatch.input.buffered.me.mkiii.name/.tooltip/.desc*`（显示名走 GT 的 `getLocalNameKey()`） |

**与既有功能的接口**：样板上传/撤回（`network/UploadPatternPacket`、`RecallPatternPacket`）、
供应器定位（`util/ProviderLocator`）、二合一终端（`merged/ContainerMergedTerminal`）全部按
`IInterfaceViewable.rows()*rowSize()` 取容量 ⇒ 144 槽自动生效，**这些文件本轮未改动**。
