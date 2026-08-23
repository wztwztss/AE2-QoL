# MOD_MAP 代码映射表
> AE2‑QoL‑1.7.10‑GTNH
> 记录功能与源码、Mixin文件的对应关系，方便快速定位代码。
> 路径：项目内部使用**相对根目录路径**；外部参考代码填写完整绝对路径 `E:\wzt\MC\modcreater\reference_src\...`

## AE2 QoL 主逻辑
| 功能简述 | 文件路径 |
|---|---|
| 智能倍增（核心逻辑） | `src/main/java/com/wztwzt/ae2_qof/mixin/ae/MixinCraftingCPUCluster.java` |
| 智能倍增（GT 仓最大轮数） | `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinMTEHatchInputBus.java` |
| 智能倍增（UI 开关） | `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinMTEHatchCraftingInputMEGui.java` |
| 智能倍增（配置） | `src/main/java/com/wztwzt/ae2_qof/Config.java` → `smartDoublingMaxRounds` |
| 智能倍增（C2S 开关包） | `src/main/java/com/wztwzt/ae2_qof/network/SmartDoublingTogglePacket.java` |
| 强化 IO 端口 | `src/main/java/com/wztwzt/ae2_qof/tile/TileExIOPort.java` |
| 强化 IO 端口（Mixin） | `src/main/java/com/wztwzt/ae2_qof/mixin/ae/MixinTileIOPort.java` |
| 二合一终端（GUI） | `src/main/java/com/wztwzt/ae2_qof/merged/GuiMergedTerminal.java` |
| 二合一终端（容器） | `src/main/java/com/wztwzt/ae2_qof/merged/ContainerMergedTerminal.java` |
| 二合一终端（方块） | `src/main/java/com/wztwzt/ae2_qof/merged/TileMergedTerminal.java` |
| 二合一终端（宿主统一接口，方块/部件/无线三形态） | `src/main/java/com/wztwzt/ae2_qof/api/IMergedTerminalHost.java` |
| 二合一终端面板（线缆部件形态） | `src/main/java/com/wztwzt/ae2_qof/merged/part/PartMergedTerminal.java` + `part/ItemPartMergedTerminal.java` |
| 无线二合一终端（手持形态） | `src/main/java/com/wztwzt/ae2_qof/merged/wireless/ItemWirelessMergedTerminal.java` + `wireless/WirelessMergedGuiObject.java` |
| 三形态 GUI 分发（ID 100 方块 / 110+side 部件 / 120 无线） | `src/main/java/com/wztwzt/ae2_qof/merged/MergedGuiHandler.java` |
| 二合一终端（样板编码/上传） | `src/main/java/com/wztwzt/ae2_qof/merged/PatternContainer.java` |
| 上传/撤回/交换网络包 | `src/main/java/com/wztwzt/ae2_qof/network/UploadPatternPacket.java` / `RecallPatternPacket.java` / `SwapPatternPacket.java` |
| NEI 叠加层（缓存） | `src/main/java/com/wztwzt/ae2_qof/client/NetworkInventoryCache.java` |
| NEI 叠加层（渲染） | `src/main/java/com/wztwzt/ae2_qof/client/NetworkInventoryDrawHandler.java` |
| 合成通知覆盖层 | `src/main/java/com/wztwzt/ae2_qof/client/render/CraftingNotificationOverlay.java` |
| 无限水岩浆磁盘 | `src/main/java/com/wztwzt/ae2_qof/item/ItemInfinityWaterLavaCell.java` |
| 无线收发器+连接器 | `src/main/java/com/wztwzt/ae2_qof/wireless/` 整包 |
| F 键搜索填充 | `src/main/java/com/wztwzt/ae2_qof/mixin/nei/MixinGuiRecipe.java` (KeyInputHandler) |
| 配方池检测工具 | `src/main/java/com/wztwzt/ae2_qof/util/RecipeMapDetector.java` |
| 终端容器解析工具 | `src/main/java/com/wztwzt/ae2_qof/util/ContainerTerminalResolver.java` |
| 重规划 | `src/main/java/com/wztwzt/ae2_qof/util/Replanner.java` |

## Mixin列表
| 功能 | Mixin配置 | Mixin类路径 | 目标类 | 备注 |
|---|---|---|---|---|
| 智能倍增（CPU 主循环接管） | `mixins.ae2_qof.json` | `mixin/ae/MixinCraftingCPUCluster.java` | `appeng.crafting.CraftingCPUCluster` | HEAD+cancellable；`knownBusyMediums` 冷却防止重复推送 |
| 智能倍增（GT 仓 `getMaxMultiplier`） | `mixins.ae2_qof.json` | `mixin/gt/MixinMTEHatchInputBus.java` | `gregtech.common.tileentities.hatches.crafting.MTEHatchInputBus` | 返回 `Integer.MAX_VALUE`（不限） |
| 智能倍增（GT 仓 GUI 开关） | `mixins.ae2_qof.json` | `mixin/gt/MixinMTEHatchCraftingInputMEGui.java` | — | GT 样板输入机 GUI 添加倍增开关按钮 |
| 智能倍增（PH 仓开关） | `mixins.ae2_qof.json` | `mixin/gt/MixinDualInputHatchUI.java` | — | PH 仓 GUI 添加倍增开关按钮 |
| 强化 IO 端口 | `mixins.ae2_qof.json` | `mixin/ae/MixinTileIOPort.java` | `appeng.tile.misc.TileIOPort` | @ModifyVariable 倍率 |
| 合成提交/完成 | `mixins.ae2_qof.json` | `mixin/ae/MixinGuiCraftConfirm.java` | `appeng.client.gui.crafting.GuiCraftConfirm` | submitJob/completeJob |
| DualityInterface NBT | `mixins.ae2_qof.json` | `mixin/ae/MixinDualityInterface.java` | `appeng.helpers.DualityInterface` | writeToNBT/readFromNBT |
| ContainerInterface 初始化 | `mixins.ae2_qof.json` | `mixin/ae/MixinContainerInterface.java` | `appeng.container.AEBaseContainer` | @GuiSync(30) |
| NEI 叠加层按钮 | `mixins.ae2_qof.json` | `mixin/nei/MixinGuiOverlayButton.java` | — | NEI 叠加层开关 |
| NEI 样板点击上传 | `mixins.ae2_qof.json` | `mixin/nei/MixinPanelWidgetClick.java` | — | PanelWidget 点击拦截 |
| NEI 叠加层渲染 | `mixins.ae2_qof.json` | `mixin/nei/MixinPanelWidgetDraw.java` | — | 书签数量叠加 |
| NEI tooltip 存量 | `mixins.ae2_qof.json` | `mixin/nei/MixinNEIRecipeWidget.java` | — | tooltip 显示网络存量 |
