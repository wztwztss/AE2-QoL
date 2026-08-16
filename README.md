# AE2 QoL

> [English](README.en.md) | **简体中文**

**为 GTNH 打造的 AE2 效率增强模组**：把 NEI 配方一键推送进 AE 样板终端、从 NEI 面板直接提取 AE 网络物品、查看每个物品在 AE 网络中的存量与可合成状态、无线传输 AE 网络等。

适配：GTNH 2.9.0-beta-1（Minecraft 1.7.10）| 当前版本：**3.0.2** | 作者：wztwzt

---

## 📦 安装

1. 将 `AE2-QoL-3.0.2.jar` 放入 `.minecraft/mods/`
2. 确认已安装依赖：AE2（`rv3-beta-977-GTNH`）、ae2fc（`1.5.88-gtnh`）、NotEnoughItems（NEI）
3. 启动游戏。配置会生成在 `config/` 下

**配置文件**（均在 `config/ae2_qof/` 目录）：

| 文件 | 作用 |
|---|---|
| `settings.json` | NEI 叠加层开关（`nei_overlay_enabled`，也可用 `/apu-overlay` 命令或样板终端内 OV 按钮切换） |
| `remembered_providers.json` | 记住的"配方 → 供应器"映射，用于自动上传（可自行编辑，格式为配方名→供应器名） |
| `recipe_names.json` | 用户配方映射表（内置 GTNH 47+ 条默认映射，随 jar 打包） |

---

## ✨ 功能总览

### 1. NEI 样板上传 / 撤回 / 交换（样板终端 GUI 内 4 个按钮）

在 **AE2 标准/扩展样板终端** 界面右上角出现 4 个按钮：**↑ 上传**（把当前样板输出槽中的编码样板自动上传到网络中的接口/总成）、**← 撤回**（从网络取回最后一个匹配的编码样板）、**⇄ 交换**（交换主产物/副产物位置）、**OV**（开关 NEI 配方页/书签的 AE 叠加层显示）。

上传时自动按配方匹配供应器（三策略：唯一供应器直接传 → 记住的供应器 → 打开选择界面手动选）。支持标准编码样板、终极编码样板、ae2fc 流体编码样板。

### 2. NEI 面板取物品 / 合成下单

- **Shift + 左键** 点击 NEI 物品面板：从 AE 网络取出一组该物品到背包（自动放入）；无存量但可合成则跳转合成
- **鼠标中键** 点击 NEI 物品面板：打开 AE2 合成数量确认界面（需要网络中有该物品的合成配方）

操作结果会在聊天栏提示：成功 / 未找到 / 不可合成 / 背包已满。

### 3. NEI 物品悬浮提示（tooltip）

- **青色数量**：该物品在 AE 网络中的存量（支持 K/M/G/T/P/E 科学计数）
- **绿色 +Craft**：该物品当前可通过 AE 网络合成
- **流体直接显示**：桶、流体单元等容器物品直接显示流体本身的数量（如 `4.5P mB 蒸馏水`），而非容器个数

### 4. NEI 书签面板数量叠加

NEI 左侧书签面板中的物品右下角会叠加显示网络存量 / 可合成标记。

### 5. 合成完成通知

AE 合成 CPU 完成一次合成任务时，屏幕右上角弹出**合成完成横幅**（物品图标 + 数量），自动淡出。需要背包中持有对应的网络密钥（无线终端/有线终端）。

### 6. 合成重新规划（Replan）

在 AE2 **合成确认界面** 中多一个 **重新规划** 按钮：点击后对当前模拟的合成任务重新分配一次流程。

### 7. 强化 IO 端口（`ex_io_port`）

外观与 AE2 原生一致，但**每次传输的物品数量放大 1024 倍**（可在 `config/<mod>.cfg` 中 `exIOPortTransferContentsRate` 调整）。合成配方：`[铁][玻璃][铁] / [红石][钻石][红石] / [铁][玻璃][铁]`。

### 8. 无限水与岩浆磁盘

放入 ME 驱动器后提供**近乎无限的水与岩浆**（每种 ≈ 4.5×10^15）。合成配方：水桶 + 岩浆桶左右放置，中间及两侧格留空。

### 9. 无线收发器 + 无线连接器

- **无线收发器**（方块）：把一块 ME 网络的物品/流体接入无线频道；右键打开 GUI 设置频道与模式（发送端/接收端）
- **无线连接器**（工具）：把 ME 设备（接口、终端、机器等）绑定到无线频道，实现远程无线直连

**使用流程**：放置两个收发器（一发一收，同一频道）→ 两块网络无线互通；**Shift+右键** 发送端用连接器绑定频道；**右键** 任意 ME 设备接入网络，再次右键解除。支持**跨维度**连接。收发器 GUI 支持添加/删除频道、切换模式、高亮显示已连接方块（红色边框）。

### 10. 石英切割刀复制名称

拿着 **石英切割刀**（Quartz Knife），在命名界面 **Shift+右键** 点击方块/AE 部件/GT 机器 → 自动把目标名称写入刀名并复制到剪贴板。

### 11. F 键搜索填充

在 AE2 / ae2fc 终端界面中，把鼠标悬浮在某物品上按 **F** 键 → 自动把该物品名称填入搜索框。

### 12. NEI 叠加层开关

- 命令：`/apu-overlay`（切换 NEI 配方页/书签叠加层显示）
- 或样板终端 GUI 中的 **OV** 按钮
- 持久化到 `config/ae2_qof/settings.json`

---

## 🕐 规划中

- **F：样板 + 接口双页面二合一终端**——把样板编码与接口管理合并到同一 GUI 双页面切换。3.0.0 曾因 AE2 原生接口终端已覆盖而搁置，后续版本可能重新发布。

---

## 📄 其他文档

| 文档 | 说明 |
|---|---|
| [更新日志](docs/CHANGELOG.md) | 用户向简洁更新日志（按版本） |
| [CHANGELOG.md](CHANGELOG.md) | 开发者向详细改动、已知风险登记、回滚指南 |
| [CREDITS.md](CREDITS.md) | 代码与材质来源、许可证核对 |

---

## 🙏 致谢

本模组由 [**AE2-Auto-Pattern-Upload**](https://github.com/GaLicn/AE2-Auto-Pattern-Upload/)（作者 GaLicn）改造而来，最初版本的上传与 F 键搜索功能直接取自原项目，并针对 GTNH 2.9.0 进行了适配，特此向原作者致谢。

开发中还参考了大量其他模组的代码与材质，主要来源如下（完整明细与许可证核对见 **`CREDITS.md`**）：

### 代码参考
- **Applied Energistics 2 GTNH**（`Applied-Energistics-2-Unofficial`）—— AE 网络、存储、网格、合成逻辑几乎全部直接调用其 API 与内部类；无限水岩浆磁盘继承 `AEBaseInfiniteCell`、复用 `CreativeCellInventory` 机制
- **AE-Wireless-Transceiver**（作者：小飘 / mynamexiaopiao）—— 无线收发器方块/终端/连接器整套实现（已通过 B 站联系作者确认借用许可）
- **AE2FluidCraft-Rework**（ae2fc）—— 流体样板（`ItemFluidEncodedPattern`）、纯流体识别
- **AE2Things**（asdflj）—— 强化 IO 端口、无限流体磁盘、创意单元等概念参考
- **NotEnoughItems**（NEI）—— NEI 配方页 AE 角标、tooltip 注入
- **Waila** —— 无线收发器等方块的高亮显示支持
- 其他参考：`GT-Not-Leisure`、`GT5-Unofficial`、`GTLCore`、`Programmable-Hatches`、`ExtendedAE_Plus`、`ExampleMod1.7.10`（GTNH 模板）等

### 材质参考
- **AE-Wireless-Transceiver**（作者：小飘 / mynamexiaopiao；贴图作者：麦淇淋 / @麦淇淋）—— 无线收发器方块贴图、`de.png` / `de1.png` / `widgets.png`、无线连接器贴图（已获作者许可）
- **Applied Energistics 2** —— `guis/states.png`、`gui/wireless.png` 等（CC BY-NC-SA 3.0，非商业使用）
- **AE2Things** —— `ex_io_port*.png` 强化 IO 端口贴图、无限流体磁盘概念
- **Minecraft 原版** —— `textures/gui/widgets.png`（运行时引用）
- `logo.png` 为自绘

### 版权与合规
本仓库**仅供个人存档，暂不开放分发**。沿用来源代码与贴图作个人备份使用，版权风险已大幅降低。开放分发前请务必核对原仓库最新 LICENSE（详见 `CREDITS.md` 中的许可证核对表）。

---

## 🛠 开发者信息

- 构建：`./gradlew build -x spotlessJava -x spotlessCheck`
- 开发者向详细改动、已知风险登记、回滚指南见 **`CHANGELOG.md`**