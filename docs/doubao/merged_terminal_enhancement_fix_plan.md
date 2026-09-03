# 二合一终端 增强修复方案

> 项目：AE2-QoL（GTNH 1.7.10）
> 对象：MergedTerminal（样板 + 接口二合一终端，含方块 / 线缆部件 / 无线三形态）
> 参考：AE2Things-1.2.15-pre2（仅作机制参考，**不引入其任何类、不建立运行时依赖**）
> 日期：2026-09-03
> 状态：方案稿，尚未改动任何代码

---

## 0. 结论摘要

| 项 | 类型 | 工作量 | 建议 |
|---|---|---|---|
| 面板材质可被资源包替换 | 修复（必做） | 极小 | ✅ 先做 |
| combine 合并 / 流体优先 | 增强（可选） | 小 | ⏳ 可选 |
| 接口条目高亮 / 重命名 / 翻倍 | 增强（可选） | 中 | ⏳ 可选 |
| GT 数据棒链接 | 增强（可选） | 小 | ⭕ 锦上添花 |
| refiller 自动补空白样板 | — | — | ❌ 已排除 |
| 样板修改器注入/提取 | — | — | ❌ 已排除 |
| 物品终端浏览面板 | — | — | ❌ 已排除 |

---

## 1. 修复项：面板材质可被资源包替换

### 1.1 问题

AE2Things 的样板面板背景 `pattern.png / pattern3.png` 引用 `ae2thing` 命名空间，资源包覆盖
`assets/ae2thing/textures/gui/widget/pattern.png` 即可换肤；而本模组使用的是从 AE2Things 拷贝、
放进 `ae2_qof` 命名空间的复制图，导致**市面上针对 AE2Things 面板的材质包对本模组无效**。

### 1.2 根因（源码定位）

- `merged/GuiMergedTerminal.java` **38–43 行**：
  ```java
  private static final ResourceLocation BG_CRAFT = new ResourceLocation("ae2_qof", "textures/gui/widget/pattern3.png");
  private static final ResourceLocation BG_PROCESS = new ResourceLocation("ae2_qof", "textures/gui/widget/pattern.png");
  ```
- `merged/GuiMergedTerminal.java` **117–125 行**（drawBG）：
  ```java
  this.mc.getTextureManager().bindTexture(crafting ? BG_CRAFT : BG_PROCESS);
  this.drawTexturedModalRect(offsetX + 209, offsetY, 0, 0, 133, 93);        // 合成模式上区
  this.drawTexturedModalRect(offsetX + 209, offsetY, 0, 93, 133, 93);       // 处理模式上区
  this.drawTexturedModalRect(offsetX + 209, offsetY + 93, 133, 0, 40, 77);  // 输入/输出中区
  this.drawTexturedModalRect(offsetX + 209, offsetY + 170, 173, 0, 32, 32); // 角标
  ```
- 对比 AE2Things：`PatternPanel.getBackground()` 返回相对路径，`IAEBasePanel.bindTextureBack()`
  拼 `AE2Thing.MODID + "textures/" + file`，即 `ae2thing:textures/...`。

### 1.3 方案（独立模组，推荐）

> 本模组完全独立、不依赖 AE2Things，因此**不**采用"运行时引用 ae2thing 资源"的方案。

1. **抽取纹理常量**：把面板背景的 `ResourceLocation` 收拢到一个常量类（如
   `merged/GuiMergedTerminal` 内的静态常量，或独立的 `MergedTextures`），domain 与路径集中管理，
   便于后续维护与文档引用。
2. **保留标准资源路径**：维持 `ae2_qof:textures/gui/widget/pattern.png` / `pattern3.png`
   （已是标准 `ResourceLocation`，任何资源包按此路径覆盖即可生效，无需改加载机制）。
3. **文档化换肤约定**：在 guidenh 指南（`merged_terminal.md`）补充一条：
   > 材质包自定义面板：覆盖 `assets/ae2_qof/textures/gui/widget/pattern.png` 与
   > `pattern3.png` 即可换肤。

### 1.4 注意事项（防错位）

- 背景图必须保持 **256×256**，否则 `drawTexturedModalRect` 的 UV 裁剪坐标会错位。
- 裁剪区域与槽位孔位绑定（见 1.2 代码），重绘材质包时槽位孔位不得移动。
- 面板绘制区域固定在 `offsetX + 209`，不随 GUI 宽度缩放。

---

## 2. 增强项（可选，按工作量排序）

### 2.1 combine 合并 / prioritize 流体优先（小）

**功能**：面板上增加两个开关——`combine`（把同种输入合并为一格显示）与 `prioritize`（物品/流体排序优先）。

**参考实现**（AE2Things）：
- `client/gui/container/ContainerWirelessDualInterfaceTerminal.java`
  - `combine`(91)、`prioritize`(99) 字段
  - `BooleanSyncHandler` 客户端-服务端布尔同步（103–147 行）
  - `fluidMonitor.setMonitor(storageGrid.getFluidInventory(), storageGrid.getItemInventory())`(174)
  - `processItemList()` 排序钩子（`fluidMonitor.processItemList()`）
- `client/gui/widget/PatternPanel.java`：`fluidPrioritizedEnabled/DisabledBtn`、`combineEnable/DisableBtn`（106–111 行）

**依赖**：无外部 API。核心是布尔同步 + 排序钩子。

**在本项目落地要点**：
1. `merged/PatternContainer.java` 增加 `combine / prioritize` 字段 + 客户端同步（可参照现有
   `craftingMode / substitute / beSubstitute` 的同步方式）。
2. 在面板渲染/库存排序入口插入 `processItemList()` 排序逻辑（按 prioritize 优先级排列物品/流体）。

---

### 2.2 接口条目 高亮 / 重命名 / 翻倍（中）

**功能**：接口列表每个条目支持——HIGHLIGHT（在世界中高亮该接口方块）、RENAME（重命名，Shift 触发）、
DOUBLE（翻倍该接口的样板数量）。

**参考实现**（AE2Things）：
- `client/gui/GuiBaseInterfaceWireless.java`：`masterList` 条目上的 `optionsButton`（HIGHLIGHT +
  Shift=RENAME）、`doubleButton`（DOUBLE）
- `network/CPacketRenamer.java`：`OPEN / GET_TEXT / SET_TEXT` 三动作 + `InventoryHandler.openGui(GuiType.RENAMER)`
- `inventory/item/IClickableInTerminal.java`：记住被点击的接口坐标

**依赖**：无外部 API，但需自研：
- 一个重命名 GUI（弹窗输入接口名称）
- 一个高亮网络包（客户端高亮目标方块世界坐标）

**在本项目落地要点**：
1. `merged/ContainerMergedTerminal.java` 增加"条目动作"处理（当前列表来自继承/移植的接口列表逻辑）。
2. 新增专用网络包（本模组已有 13+ 个 `MergedTerminal*Packet`，沿用该模式）。
3. 高亮复用 AE2 的 `WorldCoord`/粒子或高亮渲染方案。

---

### 2.3 GT 数据棒链接（锦上添花，可选）

**功能**：接口列表里对 GT 机器（`IInterfaceViewable`，如合成输入缓冲 CraftingInputBuffer）执行操作时，
若手持 GT 数据棒，则把该方块坐标写入数据棒，生成 `Crafting Input Buffer Link Data Stick`。

**参考实现**（AE2Things）：
- `client/gui/container/ContainerWirelessDualInterfaceTerminal.java#setStick(NBTTagCompound)`（429–443 行）
- `util/GTUtil.java#setDataStick(x,y,z,player,world)`：
  ```java
  tag.setString("type", "CraftingInputBuffer");
  tag.setInteger("x", x); tag.setInteger("y", y); tag.setInteger("z", z);
  dataStick.setTagCompound(tag);
  dataStick.setStackDisplayName("Crafting Input Buffer Link Data Stick (" + x + ", " + y + ", " + z + ")");
  ```
- 仅当 `Mods.isLegacyGt5Loaded() || Mods.isGt5UnofficialLoaded()` 时生效

**依赖**（GT 公开 API，GTNH 必装）：
- `gregtech.api.enums.ItemList.Tool_DataStick`（`isStackEqual` 判定）
- `gregtech.api.metatileentity.BaseMetaTileEntity`、`MTEHatch`
- `com.gtnewhorizon.gtnhlib`（如需沿用 AE2Things 的 `Util.DimensionalCoordSide` 风格，建议自建轻量类）

**在本项目落地要点**：
1. 条目动作里检测手持 `ItemList.Tool_DataStick`。
2. 通过反射或直接依赖 GT API 写 NBT（直接依赖 GT API 即可，GTNH 必装）。
3. 服务端同步刷新玩家手持物品（发包）。

---

## 3. 明确排除项（不再考虑）

| 项 | 原因 |
|---|---|
| refiller 自动补空白样板 | 用户明确不需要 |
| 样板修改器注入/提取 | 用户明确不需要（且涉及自研新物品，范围扩大） |
| 物品终端浏览面板（ItemPanel） | 用户明确不需要（需引入 MEMonitorable/ContainerMonitor 骨架，工程量最大） |

---

## 4. 架构改进建议（可选，非功能项）

AE2-QoL 当前**复制**了 `ContainerInterfaceTerminal` 的接口列表逻辑到 `ContainerMergedTerminal`；
AE2Things 采用**组合委托**（容器内部 `new ContainerInterfaceTerminal`）。建议后续重构时
改为组合委托，避免 AE2 原版升级接口终端后被迫同步维护复制代码。**风险提示**：改动容器骨架影响面大，
建议作为独立重构迭代，不与上述功能混做。

---

## 5. 实施优先级

1. **P0（必做）**：材质包换肤修复 —— 抽常量 + 文档化（约半天）
2. **P1（可选）**：combine / 流体优先（小）
3. **P1（可选）**：GT 数据棒链接（小）
4. **P2（可选）**：接口条目高亮 / 重命名 / 翻倍（中，需自研 GUI + 网络包）

---

## 附：关键参考位置速查（AE2Things-1.2.15-pre2）

| 主题 | 文件 |
|---|---|
| 面板背景绑定/绘制 | `client/gui/widget/PatternPanel.java`（drawBG 119–128；getBackground 93–100） |
| 面板背景路径拼接 | `client/gui/widget/IAEBasePanel.java`（bindTextureBack 17–22） |
| combine/prioritize 同步 | `client/gui/container/ContainerWirelessDualInterfaceTerminal.java`（91–147） |
| 接口条目操作 | `client/gui/GuiBaseInterfaceWireless.java` |
| 重命名 | `network/CPacketRenamer.java` + `inventory/gui/GuiType.RENAMER` |
| 数据棒 | `client/gui/container/ContainerWirelessDualInterfaceTerminal.java#setStick` + `util/GTUtil.java#setDataStick` |

---

*本方案仅基于源码阅读，未改动任何代码。所有改动前建议先对 `merged` 包做一次完整快照以便回退。*
