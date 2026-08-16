# CREDITS — 代码与材质来源

本模组（ae2_qof，作者：wztwzt）在开发中参考了大量其他模组的代码与材质。以下列出主要来源，便于追溯与遵守各原模组的许可证要求。

---

## 一、代码参考来源

### 1. Applied Energistics 2 GTNH（`appeng.*`）— 核心依赖
- **用途**：AE 网络交互、存储、网格、合成逻辑几乎全部直接调用 AE2 的 API 与内部类。
- **覆盖包**：`appeng.api.*`、`appeng.me.*`（网格缓存/节点）、`appeng.tile.*`（Tile 基类）、`appeng.client.gui`、`appeng.container`、`appeng.util.item` 等。
- **无限水岩浆磁盘**：直接继承 `appeng.items.AEBaseInfiniteCell`，复用 `appeng.me.storage.CreativeCellInventory` / `FluidCellInventoryHandler`（参考 GTNH 版 AE2 的 `CreativeCellInventory` 机制，代码注释注明"复制 977 `CreativeCellInventory`"）。
- **强化 IO 端口**：`BlockExIOPort` / `TileExIOPort` 继承 `appeng.block.storage` 等 AE2 基类。

### 2. AE-Wireless-Transceiver — 无线收发器功能
- **用途**：无线收发器方块/终端/连接器整套实现（`wireless/BlockWirelessTransceiver`、`TileWirelessTransceiver`、`ItemWirelessConnector`、`GuiWireless`、`RenderBlockTransceiver`）。
- 作者：小飘（mynamexiaopiao）。已通过 B 站联系作者确认借用许可。

### 3. AE2FluidCraft-Rework（ae2fc，`com.glodblock.github`）— 流体功能
- **用途**：直接使用 `com.glodblock.github.common.item.ItemFluidEncodedPattern`（流体样板），用于 `RecallPatternPacket` / `UploadPatternPacket`。
- 纯流体识别（`ItemFluidPacket` damage 编码流体 ID）基于 ae2fc 的实现。

### 4. AE2Things（`com.asdflj.ae2thing`）— 强化/创意物品参考
- **用途**：`ex_io_port` 贴图、无限流体磁盘、创意单元等概念的参考来源。

### 5. NotEnoughItems / NEI（`codechicken.nei.*`）— NEI 角标与 GUI
- **用途**：NEI 配方页 AE 角标、tooltip 注入（mixin 修改 NEI 的 `GuiNEIRecipeWidget` 等）。

### 6. Waila（`mcp.mobius.waila`）— 高亮显示集成
- **用途**：无线收发器等方块的高亮显示支持。

### 7. 其他参考模组
- `GT-Not-Leisure`、`GT5-Unofficial`、`GTLCore`、`Programmable-Hatches`、`ExtendedAE_Plus`、`ExampleMod1.7.10` 等——开发时参考 GT 版本兼容与通用模组写法。

---

## 二、材质（贴图）来源

| 本项目贴图 | 来源模组 | 说明 |
|---|---|---|
| `blocks/wireless_transceiver*.png`（含 light/top/bottom） | AE-Wireless-Transceiver | 同名 `block/wireless_transceiver*.png`（已获作者许可；贴图作者：麦淇淋） |
| `textures/de.png` / `de1.png` / `widgets.png` | AE-Wireless-Transceiver | 同名 `assets/aewireless/textures/`（已获作者许可；贴图作者：麦淇淋） |
| `guis/states.png` | AE2 / AE-Wireless | AE2 原生 `guis/states.png` |
| `gui/wireless.png` | AE2 原生 | `guis/wireless.png` |
| `items/wireless_connect.png` / `wireless_destroy.png` / `slot/wireless_connect.png` | AE-Wireless-Transceiver | 同名（已获作者许可；贴图作者：麦淇淋） |
| `blocks/ex_io_port*.png` | AE2Things | 同名 `blocks/ex_io_port*.png` |
| `items/infinity_water_lava_cell.png` | AE2Things / AE2（自绘简化） | 概念对应 AE2Things `infinity_fluid_cell.png` |
| `logo.png` | 自绘 | 模组图标 |
| `textures/gui/widgets.png`（运行时引用） | Minecraft 原版 | `CraftingNotificationOverlay.java` 引用 |

> AE-Wireless-Transceiver 相关贴图由美术 **麦淇淋（@麦淇淋）** 绘制，代码作者 **小飘（mynamexiaopiao）**；两者均已获作者许可借用。

---

## 三、各模组许可证（已于 GitHub 核实）

| 模组 | 仓库 | 许可证 | 对本模组的约束 |
|---|---|---|---|
| **AE2 (Applied-Energistics-2-Unofficial)** | `GTNewHorizons/Applied-Energistics-2-Unofficial` | **LGPL-3.0**；API **MIT**；**材质/模型 CC BY-NC-SA 3.0**；文本 CC0 | 代码可修改/分发，需保留版权声明并开放同许可；**材质非商业使用需署名、相同方式共享** |
| **AE2FluidCraft-Rework (ae2fc)** | `GTNewHorizons/AE2FluidCraft-Rework` | **LGPL-3.0** | 代码可修改/分发，需注明来源并开源修改部分 |
| **NotEnoughItems (NEI)** | `GTNewHorizons/NotEnoughItems` | 原代码 **MIT**；GTNH 修改 **LGPL-3.0** | 可用但请注明来源 |
| **AE2Things (asdflj)** | `asdflj/AE2Things` | **GPL-3.0** | 若沿用其代码/贴图，衍生作品须以 GPL-3.0 开源 |
| **AE-Wireless-Transceiver** | `mynamexiaopiao/AE-Wireless-Transceiver` | 仓库无 LICENSE（默认保留所有权利）⚠️ | **已通过 B 站联系作者获得借用许可**；发布时请署名致谢 |
| **Waila** | `ProfMobius` / `GTNewHorizons/waila` | **CC BY-NC-SA 4.0**（GTNH fork） | 非商业使用，需署名并相同方式共享 |

---

## 四、版权与合规说明

- **当前策略：仅存档、不开放分发**。本仓库仅用于保存历代版本与源码，供作者本人备份使用，不向他人开放自由下载、修改或再分发。在此前提下，沿用上述来源代码与贴图作为个人存档使用，版权风险大幅降低。
- **AE-Wireless-Transceiver（无线收发器）**：其仓库原本无许可证文件（默认保留所有权利）。代码作者（小飘 / mynamexiaopiao）已通过 B 站沟通确认同意借用其代码与贴图；贴图/美术作者（麦淇淋 / @麦淇淋）。发布或开放分发前请保留作者与美术署名，并保留本记录以备查证。
- **AE2Things（GPL-3.0）**：若沿用其 `ex_io_port` 贴图或代码并开放分发，衍生作品须以 GPL-3.0 开源；否则建议更换/重制。
- **AE2 材质（CC BY-NC-SA 3.0，非商业）**：`states.png`、`gui/wireless.png` 等 AE2 来源材质仅限非商业使用，需署名并相同方式共享。
- 建议在仓库 README 中明确声明：**本仓库仅供个人存档，暂不开放分发**，以规避来源模组的版权风险。
- 本清单基于 2026-08 在 GitHub 各仓库核实的信息，许可证可能变更，开放分发前请再次核对原仓库最新 LICENSE。

---

## 五、致谢

特别感谢以下作者与项目：
- **小飘（mynamexiaopiao / xiaopiao）** — AE-Wireless-Transceiver（无线收发器），并慷慨授权本模组借用其代码与材质
- **麦淇淋（@麦淇淋）** — AE-Wireless-Transceiver（无线收发器）贴图/美术作者，无线收发器方块、连接器及 GUI 材质均出自其手
- **GTNH 团队** — Applied-Energistics-2-Unofficial、NotEnoughItems、ae2fc 等
- **asdflj** — AE2Things
- **ProfMobius** — Waila
- 以及所有在开发中被参考的开源模组作者
