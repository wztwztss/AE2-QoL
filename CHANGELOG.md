# AE2 QoL - Changelog

> 当前版本：3.1.1 | 适配：GTNH 2.9.0-beta-1 | 依赖：AE2 `rv3-beta-977-GTNH`，ae2fc `1.5.88-gtnh`

---

# 功能总览

> 状态图例：✅ 可用 ｜ ⚠️ 已失效（需修复） ｜ 🕐 规划中

| 功能 | 核心类 | 状态 |
|---|---|---|
| NEI 样板上传/撤回/交换（样板终端 GUI 内 4 个按钮） | `client/event/GuiUploadButtonHandler` + `network/UploadPatternPacket`/`RecallPatternPacket`/`SwapPatternPacket` | ✅ 可用 |
| Provider 列表请求 + 三策略自动上传 + 选择界面 | `network/RequestProvidersListPacket` + `network/ProvidersListS2CPacket` + `client/gui/GuiProviderSelect` | ✅ 可用 |
| 配方映射持久化（内置 47+ 条 GT 配方池映射） | `util/RecipeNameUtil` + `common/RecipeMapNameConfig` + `client/ClientRecipeNameUtil` | ✅ 可用 |
| **NEI 配方页 AE 角标**（编码样板图标 + 数量角标） | `mixin/nei/MixinNEIRecipeWidget` | ✅ 可用（3.1.0 改为注入 `draw(II)V`，功能恢复） |
| NEI 书签面板数量/可合成角标 | `mixin/nei/MixinPanelWidgetDraw` + `client/NetworkInventoryDrawHandler` | ✅ 可用 |
| NEI 悬浮 tooltip（青色存量 + 绿色可合成；流体容器直接显示 `mB` 流体量） | `client/nei/NetworkTooltipHandler` | ✅ 可用 |
| NEI Shift+左键提取 / 中键合成下单 | `mixin/nei/MixinPanelWidgetClick` + `network/ExtractItemPacket`/`RequestCraftingPacket`/`CraftingResponsePacket` | ✅ 可用（3.0.1 修复服务器踢出） |
| 合成完成通知（屏幕横幅 + 音效） | `mixin/ae/MixinCraftingCPUCluster` + `network/CraftingCompletePacket` + `client/render/CraftingNotificationOverlay` | ✅ 可用 |
| 合成重新规划（Replan） | `mixin/ae/MixinGuiCraftConfirm` + `network/ReplanPacket` + `util/Replanner` | ✅ 可用 |
| 强化 IO 端口（方块 + 传输倍率） | `block/BlockExIOPort` + `tile/TileExIOPort` + `mixin/ae/MixinTileIOPort` | ✅ 可用 |
| 无限水岩浆磁盘 | `item/ItemInfinityWaterLavaCell` | ✅ 可用 |
| 无线收发器 + 无线连接器（跨维度 + 直连绑定） | `wireless/` 包全套 | ✅ 可用 |
| 石英切割刀 Shift+右键复制方块/AE部件/GT机器名 | `client/event/KnifeNameCopyHandler` | ✅ 可用 |
| F 键将鼠标下物品名填入终端搜索框 | `client/event/KeyInputHandler` | ✅ 可用 |
| 叠加层开关 `/apu-overlay` + GUI OV 按钮 | `client/CommandOverlay` + `client/OverlayConfig` | ✅ 可用 |
| **F：样板 + 接口双页面二合一终端** | —（3.0.0 调研后搁置） | 🕐 规划中，待重新发布（详见文末） |

# 已知风险登记表

> 等级：🔴 高（可能崩溃/丢物/踢人） ｜ 🟡 中（潜在） ｜ 🟢 低。
> 状态：❌ 未修复 ｜ ✅ 已修复 ｜ ⏳ 已兜底（防踢但不防业务异常）。
> 本表用于每次改动后快速回归检查，避免修复引发恶劣问题。

| # | 风险 | 位置 | 等级 | 状态 |
|---|---|---|---|---|
| 1 | 合成通知 `(EntityPlayerMP)` 强转：假玩家/非 MP 玩家发起合成 → `ClassCastException` 打崩服务端线程 | `mixin/ae/MixinCraftingCPUCluster.java:90` | 🔴 | ✅ |
| 2 | 长合成任务期间玩家下线 → `completeJob` 遍历已断线玩家并 `sendTo` → NPE + 内存滞留 | `mixin/ae/MixinCraftingCPUCluster.java:74-90` | 🔴 | ✅ |
| 3 | 提取物品丢失：先 `MODULATE` 从网络扣物品，再塞背包；背包满/部分失败时**剩余物品凭空消失** | `network/ServerTerminalHelper.java:145-156` | 🔴 | ✅ |
| 4 | 样板撤回无所有权校验：共享网络可**窃取/清空**其他玩家接口中的样板 | `network/RecallPatternPacket.java` | 🔴 | ✅ |
| 5 | 全部 C2S 包在 **Netty IO 线程**执行，未 `addScheduledTask` 归队 → 并发访问 grid/container 与 tick 竞争（CME/状态不一致） | 所有 C2S Handler | 🔴 | ✅ |
| 6 | NEI 配方页 AE 角标注入点失效，功能不工作 | `mixin/nei/MixinNEIRecipeWidget.java:39` | 🟡 | ✅ |
| 7 | 样板上传无所有权校验：共享网络可向任意 provider 植入垃圾样板 | `network/UploadPatternPacket.java` | 🟡 | ✅ |
| 8 | GT `RecipeMap.ALL_RECIPE_MAPS` 全量反射扫描无速率限制 → 恶意连发卡服 | `network/RequestProvidersListPacket.java:146` | 🟡 | ✅ |
| 9 | IO 端口传输倍率 `itemsToMove *= rate` 极端配置下 long 溢出（配置上限 Integer.MAX_VALUE） | `mixin/ae/MixinTileIOPort.java:21` | 🟡 | ✅ |
| 10 | 收发器动作无权限校验 + 无 try/catch：可对任意坐标 Tile 发动作，异常踢人 | `network/WirelessActionPacket.java` | 🟡 | ✅ |
| 11 | 合成下单无全局 try/catch：服务端异常直接踢人 | `network/RequestCraftingPacket.java` | 🟡 | ✅ |
| 12 | 提取包无全局 try/catch + 恶意负数 count | `network/ExtractItemPacket.java` | 🟡 | ✅ |
| 13 | 交换样板反射字段 null → NPE；改库后未 `saveChanges` → 输出槽显示与真实内容不同步 | `network/SwapPatternPacket.java` | 🟡 | ✅ |
| 14 | 日志刷屏：流体缓存逐条 `LOG.info` / 书签每次传输 `System.out.println` | `mixin/nei/MixinGuiMEMonitorable.java` / `mixin/nei/MixinDefaultOverlayHandler.java` | 🟢 | ✅ |
| 15 | 所有自定义包 `fromBytes` 解码异常 → FML 断连踢玩家 | 全部 `network/*Packet` | 🔴 | ✅ 已兜底（3.0.1 全包 try-catch） |
| 16 | `CraftingResponsePacket` ItemStack 序列化字节错位 → `DecoderException` 踢人 | `network/CraftingResponsePacket.java` | 🔴 | ✅ 已修复（3.0.1 改 String 传输） |
| 17 | Replan 点击 `lists.clear()` 误清 map 导致 NPE 崩溃 | `util/Replanner.java` | 🔴 | ✅ 已修复（3.0.1） |
| 18 | 合成通知/IO 端口 mixin 在 `server` 列表单机不注入 → 功能无效 | `mixins.ae2_auto_pattern_upload.json` | 🟡 | ✅ 已修复（3.0.1 移入公共列表） |

# 回滚指南

| 目标版本 | 使用 jar | 说明 |
|---|---|---|
| 3.1.1（当前） | `build/libs/AE2-QoL-3.1.1.jar` | 修复汉化乱码 |
| 3.1.0 | `build/libs/AE2-QoL-3.1.0.jar` | 全量安全加固（14 项风险修复） |
| 3.0.2 | `build/libs/AE2-QoL-3.0.2.jar` | 含流体直接显示 + 刷屏日志清理 |
| 3.0.0 | `build/libs/AE2-QoL-3.0.0.jar` | 功能最全（无限磁盘/角标/通知/Replan/IO端口），但含 B/C 已知崩溃问题 |
| 2.14.1 | `build/libs/AE2-QoL-2.14.1.jar` | 稳定基线（无线直连完整版），无 3.x 新功能 |

回退步骤：删除测试包 `mods/AE2-QoL-<旧版本>.jar`，复制目标 jar 为 `mods/AE2-QoL-<目标版本>.jar`，重启客户端。
依赖固定：AE2 `rv3-beta-977-GTNH`、ae2fc `1.5.88-gtnh`、NEI `2.8.19-GTNH`。

---

## 3.1.1 - 修复汉化文件乱码

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **汉化文件乱码**：`zh_CN.lang` 曾被双重编码损坏（UTF-8 字节被误按 GBK 解码后再存回 UTF-8），导致全部中文值变乱码、多条条目被挤到同一行、全角标点（`。？：（）`）丢失。已按最初提交 `f288457` 的完好原文重建，恢复全部 67 条中文翻译（添加/刷新/删除、映射、无线收发器/连接器、频道绑定/断开、tooltip 等），并按新 key 前缀 `ae2_qof` 对齐
- **一致性校验**：确认 `en_US` 与 `zh_CN` 的 67 个 key 集合完全一致，代码中引用的全部 key 均存在于语言文件

---

## 3.1.0 - 全量安全加固（14 项已知风险修复）

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **A（合成通知强转崩溃）**：`MixinCraftingCPUCluster` 合成通知增加 `instanceof EntityPlayerMP` 判断后再强转，假玩家/非 MP 玩家发起合成不再 `ClassCastException` 打崩服务端线程（风险 #1）
- **A（下线玩家合成通知）**：长合成任务期间玩家下线时跳过已断线玩家（`playerNetServerHandler == null`），不再 `sendTo` NPE + 内存滞留（风险 #2）
- **A（提取丢物）**：`ServerTerminalHelper` 提取物品改为先 `SIMULATE` 计算、背包容量预检，再按上限实际提取；放入失败/背包满时剩余物品经 `injectItems` 归还网络，杜绝凭空消失（风险 #3）
- **A（样板上传/撤回权限）**：`RecallPatternPacket`（EXTRACT）与 `UploadPatternPacket`（INJECT）增加 `ISecurityGrid` 所有权校验；无安全站的网络默认放行（风险 #4/#7）
- **A（C2S 归队服务端线程）**：全部 8 个 C2S 包处理改用 GTNHLib `ServerThreadUtil` 归队到服务端 tick 线程，不再于 Netty IO 线程并发访问 grid/container（风险 #5）
- **C（NEI 配方页 AE 角标修复）**：注入点改为 NEI 2.8.19-GTNH 实际存在的 `draw(II)V`，经 `RecipeHandlerRef` 取配方栈绘制样板图标与数量角标，功能恢复（风险 #6）
- **C（Provider 列表限流）**：`RequestProvidersListPacket` 全量反射扫描 GT 配方池增加 3 秒/玩家冷却 + 结果缓存，防恶意连发卡服（风险 #8）
- **C（IO 端口溢出）**：`MixinTileIOPort` 传输倍率乘法改为溢出安全计算（风险 #9）
- **C（无线动作加固）**：`WirelessActionPacket` 全局 try/catch + 仅允许操作玩家当前打开 GUI 的收发器（风险 #10）
- **C（合成/提取包加固）**：`RequestCraftingPacket`、`ExtractItemPacket` 全局 try/catch + 恶意负数 count 钳制（风险 #11/#12）
- **C（样板交换加固）**：`SwapPatternPacket` 反射字段异常兜底 + 改库后 `saveChanges` 持久化（风险 #13）
- **C（日志清理）**：移除 NEI 书签传输的 `System.out.println`（风险 #14）

### 技术说明

- 本环境编译用的 RFG recompiled Minecraft 中 `MinecraftServer`/`Minecraft` 均无 MCP 名 `addScheduledTask`（1.7.10 Forge 运行时亦无该方法），故服务端线程归队改用 GTNHLib `ServerThreadUtil.addScheduledTask`（其 `MixinMinecraftServer` 每 tick 排空任务队列，AE2 依赖自带 GTNHLib）；客户端 `SwapPatternPacket` 归队使用 SRG 名 `func_152344_a`

---

## 3.0.2 - 流体直接显示（fix）

> 作者：wztwzt | 更新时间：2026-08-15

### 修复

- **I（流体直接显示）**：NEI 角标与 tooltip 中，桶/单元等流体容器物品不再以"容器数量"显示，而是**直接显示流体本身的量**（mB）。`NetworkInventoryCache.getCount`/`isCraftable` 改为：对能识别出流体的容器物品，优先查询流体缓存返回流体量；只有流体缓存无数据时才回退容器物品缓存
- **I（纯流体识别）**：ae2fc `ItemFluidPacket` 的 damage 值直接编码流体注册 ID（`FluidRegistry.getFluid(damage)`）。`getFluidStack` 新增第五条识别路径——通过 damage 查 `FluidRegistry`，使水/岩浆/氢气/氧气等纯流体在 NEI 角标和 tooltip 中正确显示流体量
- **I（容器与流体分离）**：**只有 ae2fc 纯流体 packet（damage 编码流体）才显示流体量（mB）**；水桶/单元等容器物品一律按普通物品返回其在网络中的**容器数量**（AE 里真的有该容器才显示，不再把容器当流体显示总量）
- **I（流体 tooltip 标识）**：流体容器 tooltip 追加流体本地名，格式从 `4.5P AE` 变为 `4.5P mB 蒸馏水`，可合成状态不变
- **刷屏日志清理**：移除 `NetworkInventoryCache.getFluidStack` 两条 `LOG.info`、`getCount` 一条 `LOG.info`、`MixinGuiMEMonitorable` 一条 `Cached fluid` `LOG.info`——之前每次渲染/hover 都打印，严重刷屏（对应风险表 #14 部分修复）

---

## 3.0.1 - 崩溃修复 + 数值扩展 + 流体角标 + 合成表

> 作者：wztwzt | 更新时间：2026-08-15

### 修复

- **H（服务器 Shift+左键提取踢出）**：修复在服务器上 shift+左键从 NEI 面板取 AE 物品时客户端被踢（`DecoderException: readerIndex(11) + length(2) exceeds writerIndex(12)`）。`CraftingResponsePacket` 不再序列化 `ItemStack`（损坏物品/带 NBT 物品在部分场景下字节不对称导致解码越界），改为仅传物品名称字符串；同时给**所有**自定义网络包的 `fromBytes` 加防御性 try-catch，任何解码异常只丢弃该包、不再导致玩家断连
- **C（Replan 崩溃）**：修复点击"重新规划"时因 `Replanner.clearIItemList` 误调用 `lists.clear()` 清空 `IAEStackList` 内部 map 导致的 NPE 崩溃（`GuiCraftConfirm.drawListFG` → `findPrecise`）。现仅清空各类型子表，map 结构保留
- **B（合成通知未生效）**：`MixinCraftingCPUCluster` 与 `MixinTileIOPort` 原位于 mixin json 的 `server` 列表，单机（integrated server）下 MixinBooter 不会应用该列表，导致两处逻辑均未注入。已移入公共 `mixins` 列表并加 `"target": "@env(DEFAULT)"`——顺带修复了 IO 端口倍率从未生效的问题
- **D（强化 IO 端口外观/数值）**：复制 AE2 原生 IO 端口贴图（`ex_io_port`/`ex_io_portBottom`/`ex_io_portSide`），补充 lang 名称（强化 IO 端口/Enhanced IO Port），默认传输倍率 256 → 1024

### 改进

- **H（提取/合成反馈提示）**：shift+左键提取与中键合成现在会在聊天栏给出明确结果提示（成功/无库存/不可合成/背包已满），不再静默
- **E（科学计数法扩展）**：统一新增 `CountFormatter` 工具（K/M/G/T/P/E），替换 NEI 配方角标、书签角标、tooltip 三处仅到 G 的格式化；覆盖 Long 全范围
- **E（流体支持）**：`NetworkInventoryCache` 新增流体缓存（按键为流体名）；`MixinGuiMEMonitorable` 处理 `IAEFluidStack`；tooltip/书签/配方角标对携带 `FluidStack` NBT 的物品（如 ae2fc `ItemFluidPacket`）自动回退查询流体数量
- **G（合成表）**：新增本 mod 方块/物品的原版工作台合成配方——强化 IO 端口、无线收发器、无线连接器；无限水岩浆磁盘：水桶 + 岩浆桶左右放置（中间及其他格为空）→ 无限水岩浆磁盘

---

## 3.0.0 - 无限水岩浆磁盘 + NEI 配方界面 AE 角标 + 合成通知/重规划/强化IO端口

> 作者：wztwzt | 更新时间：2026-08-14

### 新功能

- **无限水岩浆磁盘**：新增物品 `infinity_water_lava_cell`（无限水与岩浆磁盘）。放入 ME 驱动器后提供无限的水与岩浆（每种数量 ≈ 2^52-1 ≈ 4.5e15）。复用 977 原生 `CreativeCellInventory`：构造时读取预置的水桶/岩浆桶配置并转换为流体，数量置为 Long 上限级别，达到近乎无限的效果。idleDrain 2000，可编辑，2 种类型
- **NEI 配方界面 AE 角标**：任意 NEI 配方页（合成/机器配方）的每个物品格上叠加显示 AE 网络数据——可合成物品叠加编码样板小图标（0.4 缩放），有存量的物品在角落显示数量角标（0.6 缩放，K/M/G 格式化）。数据来自 AE 终端 `postUpdate` 填充的 `NetworkInventoryCache`，受 `/apu-overlay` 开关（settings.json `nei_overlay_enabled`）控制。注入点 `NEIRecipeWidget.drawItem` TAIL，覆盖任意配方界面，不依赖 NEI 的 itemPresenceOverlay 配置
- **合成完成通知**：AE 合成 CPU 完成一次合成任务时，客户端屏幕中央弹出通知横幅（物品图标 + 数量 + "合成完成"），自动淡出
- **合成重新规划（Replan）**：服务端指令/客户端请求可对指定合成任务进行重新规划（清空原流程重新规划配方分配）
- **强化版 IO 端口**：新增 `ex_io_port` 方块，继承 AE2 IO 端口行为，支持基于频道状态的增强模式；传输内容量按倍率放大（默认 256 倍，可配置）
- **NEI 配方 tooltip 悬浮提示**：NEI 物品面板悬浮显示 AE 网络数量/可合成信息

### 关键决策

- **F（无线双接口终端）**：调研确认 AE2 977 已原生实现接口终端（`PartInterfaceTerminal`/`WirelessInterfaceTerminalGuiObject`），ae2fc 1.5.88 自带 `ItemWirelessInterfaceTerminal` 可直接打开 977 原生接口终端——该功能已被原生覆盖，放弃约 6000 行完整移植，改为上述 NEI 配方界面 AE 角标增量增强
- **G（创造流体磁盘）**：ae2fc 已自带 `ItemCreativeFluidStorageCell`（无限存储）——但用户实际需要的是"内置水/岩浆的无限供应磁盘"，故实现本 mod 的无限水岩浆磁盘
- **H（AdvItemRepo 线程化刷新）**：977 `ItemRepo` 已重构（`IAEStack<?>` 泛型、无 `dsp` 字段），参考 `AdvItemRepo` 依赖 AE2Things 自有接口无法直接移植，经确认跳过（977 GTNH 分支已做性能优化）

### 新增文件

- `item/ItemInfinityWaterLavaCell.java` — 无限水岩浆磁盘（复用 977 `CreativeCellInventory` + ae2fc `Util.getAEFluidFromItem`）
- `mixin/nei/MixinNEIRecipeWidget.java` — NEI 配方界面 AE 角标
- `network/CraftingCompletePacket.java` — 合成完成 S2C 通知包
- `client/overlay/CraftingNotificationOverlay.java` — 合成完成屏幕通知
- `network/ReplanPacket.java` + `util/Replanner.java` — 合成重新规划
- `block/BlockExIOPort.java` + `tile/TileExIOPort.java` + `render/RenderBlockExIOPort.java` — 强化版 IO 端口
- `client/NetworkTooltipHandler.java` — NEI 配方 tooltip

### 修改文件

- `CommonProxy.java` — 注册 `ItemInfinityWaterLavaCell`、`BlockExIOPort`、`TileExIOPort`
- `mixin/nei/MixinGuiMEMonitorable.java` — 缓存 AE 网络数据（数量 + 可合成）到 `NetworkInventoryCache`
- `mixin/ae/MixinCraftingCPUCluster.java` — 合成完成事件捕获
- `mixin/ae/MixinTileIOPort.java` — 强化 IO 端口倍率
- `mixins.ae2_auto_pattern_upload.json` — 注册新 mixin
- `lang/zh_CN.lang` + `lang/en_US.lang` — 新物品/提示翻译
- `textures/items/infinity_water_lava_cell.png` — 新磁盘贴图
- `gradle.properties` — modVersion = 3.0.0
- `mcmod.info` — version = 3.0.0
- `CHANGELOG.md` — 新增 3.0.0 条目

---

## 2.14.1 - Bug修复：记住供应器丢失 + 绑定消息中文 + WAILA频道数修正 + 书签去反射

> 作者：wztwzt | 更新时间：2026-08-13

### Bug修复

- **记住的供应器丢失（映射中文名消失）**：`ClientState.rememberedProviders` 声明在 `static {}` 块之后，`loadRemembered()` 在静态初始化时执行导致 `rememberedProviders` 为 null，NPE 被吞掉后每次启动都加载失败。将 Map 声明移到 static 块之前，重启后 `remembered_providers.json`（配方池→供应器中文名映射）正常恢复，"自动上传匹配上次供应器"策略重新生效
- **绑定消息服务端英文**：`ItemWirelessConnector` 13 处服务端聊天消息用 `StatCollector.translateToLocal(Formatted)` 在服务端（en_US）翻译成英文。全部改为 `ChatComponentTranslation` + `ChatStyle` 颜色，客户端按中文 locale 渲染
- **WAILA 频道数上限**：`updateChannelCounts()` 原显示 `32 - receiverUsed`，改为 `computeMaxChannels()`——遍历收发器物理连接对端节点（cast `appeng.me.GridNode` 取 `getMaxChannels()`），取最小值为可分发频道上限。普通线缆接入显示 8，密集线缆 32，控制器 MAX，无物理连接回退 32
- **NEI 书签优先级去反射**：`getBookmarkPriorities()` 不再反射访问 `BookmarkGrid.bookmarkItems` 私有字段，改用公开 API `grid.getBookmarkItem(i)` 循环读取（越界返回 null），并输出 `[APU] Bookmark priorities: N` 日志便于确认

### 修改文件

- `client/ClientState.java` — `rememberedProviders` 声明移到 static 块之前
- `wireless/ItemWirelessConnector.java` — 13 处服务端消息改 `ChatComponentTranslation` + `ChatStyle`
- `wireless/WirelessLinkManager.java` — 新增 `computeMaxChannels()` 取物理连接对端最小容量
- `mixin/nei/MixinDefaultOverlayHandler.java` — 书签优先级改用公开 API + 日志
- `gradle.properties` — modVersion = 2.14.1
- `mcmod.info` — version = 2.14.1
- `CHANGELOG.md` — 新增 2.14.1 条目

---

## 2.14.0 - Bug修复：书签优先级恢复 + NEI 中键冲突 + 无线收发器全面修复

> 作者：wztwzt | 更新时间：2026-08-13

### Bug修复

- **NEI 书签优先级恢复**：`MixinDefaultOverlayHandler` 的 `@Overwrite assignIngredients()` 返回值从 `ArrayList` 改为 `List`，与运行时 NEI 方法描述符精确匹配。此前返回值类型参与 JVM 方法描述符导致 Mixin 找不到目标，整个覆盖被静默跳过，书签优先级从未生效
- **NEI 中键合成冲突**：LWJGL 按钮 0=左键/1=右键/2=中键。原代码用 `button == 1` 拦截的是右键，中键落回 NEI 原生拖出逻辑。改为 `button == 2`，仅在物品可合成时拦截，不可合成仍走原生拖出
- **无线收发器方块消失**：`securityBreak()` 原实现直接 `removeTileEntity` + `setBlockToAir`，AE2 在方块接入受保护网络时调用导致方块被销毁。改为仅断开连接、注销频道并标记未连接，不再销毁方块
- **发送端状态不同步**：`WirelessLinkManager` 此前只对接收端调用 `setConnected(true)`，发送端 `isConnected` 永不为 true。新增 `markConnected()` 统一同步两端连接、频道数和方块更新
- **服务器收发器连不上**：块卸载时 `onChunkUnload` 置空 `gridNode` 但不注销，残留失效发送端阻塞重注册。发送端分支现在检测 map 条目为 null/失效/无 gridNode 时强制重注册；接收端分支额外校验 `sender.getGridNode(null) != null`
- **WAILA 频道数恒为 0**：原实现客户端读取服务端专用静态 `WirelessData.getAllFrequencies()`。改为实现 `getNBTData()` 在服务端计算频道数写入 NBT，`getWailaBody` 从 accessor NBT 读取
- **高亮线不可见**：`WirelessHighlightRenderer` alpha 从 0.3–0.45 提升至 0.55–0.70，新增 `GL11.glLineWidth(3.0F)`，包围盒外扩 ±0.06，绘制结束恢复线宽

### 修改文件

- `mixin/nei/MixinDefaultOverlayHandler.java` — @Overwrite 返回值 `ArrayList` → `List`
- `mixin/nei/MixinPanelWidgetClick.java` — 中键按钮值 `button == 1` → `button == 2`
- `wireless/TileWirelessTransceiver.java` — `securityBreak()` 不再销毁方块
- `wireless/WirelessLinkManager.java` — 新增 `markConnected()` 同步两端；发送端失效重注册；接收端 gridNode 校验
- `wireless/TransceiverWailaProvider.java` — `getNBTData()` 服务端计算频道数
- `client/render/WirelessHighlightRenderer.java` — alpha/线宽/包围盒增强
- `gradle.properties` — modVersion = 2.14.0
- `mcmod.info` — version = 2.14.0
- `CHANGELOG.md` — 新增 2.14.0 条目

---

## 2.13.0 - NEI Shift+左键取出物品 + 中键合成下单 + 书签优先级

> 作者：wztwzt | 更新时间：2026-08-12

### 新功能

- **NEI Shift+左键取出物品**：在任意 GUI 中，Shift+左键点击 NEI 书签面板中的物品，从 AE2 网络取出一组物品到背包。若网络中无该物品但可合成，自动跳转合成下单流程
- **NEI 中键合成下单**：在任意 GUI 中，中键点击 NEI 书签面板中可合成的物品，打开 AE2 原版合成确认界面（ContainerCraftAmount）。需要玩家身上携带无线终端且在范围内
- **NEI 书签优先级**：NEI 配方传输时，书签中的物品在矿辞替代选择中获得优先加成（书签列表越靠前优先级越高）

### 兼容性

- 与 AE2Things 兼容：检测 `cir.getReturnValue()` 避免冲突；在 AE2 终端 GUI 中使用 AE2 原版 `PacketInventoryAction`，非 AE2 GUI 中使用自定义数据包

### 技术实现

- MixinPanelWidgetClick：注入 `PanelWidget.handleClick()` HEAD，拦截 Shift+左键和中键点击
- ExtractItemPacket（C2S）：客户端发送目标物品 → 服务端通过无线终端从 AE2 网络提取到背包
- RequestCraftingPacket（C2S）：客户端发送目标物品 → 服务端通过 `Platform.openGUI` 打开 ContainerCraftAmount
- CraftingResponsePacket（S2C）：服务端发送操作结果 → 客户端聊天栏提示
- ServerTerminalHelper：服务端无线终端解析工具，从玩家背包（含 Baubles）查找无线终端
- MixinDefaultOverlayHandler @Overwrite：`assignIngredients()` 添加书签优先级加成

### 新增文件

- `network/ServerTerminalHelper.java` — 无线终端解析工具
- `network/ExtractItemPacket.java` — 取出物品 C2S 数据包
- `network/RequestCraftingPacket.java` — 合成下单 C2S 数据包
- `network/CraftingResponsePacket.java` — 操作结果 S2C 数据包
- `mixin/nei/MixinPanelWidgetClick.java` — NEI 面板点击处理

### 修改文件

- `mixin/nei/MixinDefaultOverlayHandler.java` — @Overwrite `assignIngredients()` 添加书签优先级
- `network/ModNetwork.java` — 注册 3 个新数据包
- `mixins.ae2_auto_pattern_upload.json` — 注册 MixinPanelWidgetClick
- `gradle.properties` — modVersion = 2.13.0
- `CHANGELOG.md` — 新增 2.13.0 条目

---

## 2.12.1 - Bug修复：样板交换支持流体产物 + 只轮转非空槽位

> 作者：wztwzt | 更新时间：2026-08-11

### Bug修复

- **流体产物序列化丢失**：交换后服务端将 `IAEStack<?>` 转为 `ItemStack` 发送给客户端，`IAEFluidStack`（流体）因 `instanceof IAEItemStack` 判定失败被丢弃为 null，导致流体产物在客户端显示为空。改用 NBT 序列化（`FluidStack.writeToNBT` / `loadFluidStackFromNBT`），同时支持 `IAEItemStack` 和 `IAEFluidStack`
- **空槽位参与轮转**：旧逻辑对所有槽位做循环左移，空槽位"吸收"了物品导致显示错位。改为只收集非空槽位轮转，空槽位保持不动。例如 `[空单元, 流体, 空]` → `[流体, 空单元, 空]` 而非 `[流体, 空, 空单元]`

### 修改文件

- `SwapPatternPacket.java` — 序列化从 `List<ItemStack>` 改为 `List<IAEStack<?>>`，type byte 区分物品(1)/流体(2)；交换逻辑改为只轮转非空槽位；客户端直接使用 `IAEStack<?>` 不再经 `ItemStack` 中转

---

## 2.12.0 - 改名 AE2 QoL + 样板交换实时同步 + NEI 叠加层按钮

> 作者：wztwzt | 更新时间：2026-08-11

### 改名

- **模组名称**：从 "AE2 Auto Pattern Upload" 改为 "AE2 QoL"，反映功能范围扩大（样板管理 + 无线访问 + NEI 增强）
- **导出文件名**：从 `ae2_auto_pattern_upload-x.x.x.jar` 改为 `AE2-QoL-x.x.x.jar`

### Bug修复

- **样板交换按钮实时同步**：交换后服务端立即发送 S2C 同步包，客户端收到后更新 `outputSlotsClient`，无需等待编码即可看到产物变化
- **循环交换逻辑**：2个产物直接互换；3+个产物循环左移（123→231→312→123）
- **移除客户端 nonNullCount 预检**：`outputSlotsClient` 在服务端同步前可能全为 null，不再拦截交换请求

### 新功能

- **NEI 叠加层切换按钮**：在样板终端 GUI 新增 OV/-- 按钮（位于 ⇄ 交换按钮右侧），点击切换 NEI 书签面板物品数量/可合成标记的显示/隐藏，替代 `/apu-overlay` 命令

### 按钮布局

```
          OV(996) ←(998)
  ⇄(997)  ↑(999)
         编码按钮
```

### 修改文件

- `SwapPatternPacket.java` — 重写：C2S 触发交换 + S2C 携带新值同步；ByteBufUtils 序列化；2=互换 / 3+=循环左移
- `ModNetwork.java` — SwapPatternPacket 注册为双向（SERVER + CLIENT）
- `GuiUploadButtonHandler.java` — 移除 `countOutputSlots()`；新增 OVERLAY_ID 按钮
- `MyMod.java` — `@Mod` name 改为 "AE2 QoL"
- `mcmod.info` — name/description/version 更新
- `gradle.properties` — modName = AE2 QoL, modVersion = 2.12.0, customArchiveBaseName = AE2-QoL
- `settings.gradle.kts` — rootProject.name = "AE2-QoL"

---

## 2.11.1-fix1 - Bug修复：样板交换按钮读取错误槽位

> 作者：wztwzt | 更新时间：2026-08-11

### 问题

交换按钮点击无效果。`getPatternFromOutputSlot()` 读取 `patternSlotOUT`（编码后样板输出槽），但编辑模式下产出物在 `outputs` 虚拟显示槽（`IAEStackInventory`），`patternSlotOUT` 为空导致 `patternStack=null`。

### 修复

- **SwapPatternPacket**：改为通过反射读取 `outputs`（`IAEStackInventory`），用 `getAEStackInSlot` / `putAEStackInSlot` 交换第0和第1个槽位，调用 `markDirty()` 同步
- **客户端校验**：改为检查 `outputSlotsClient`（公开字段）中非null元素数量 ≥ 2

### 修改文件

- `SwapPatternPacket.java` — 交换 `outputs` IAEStackInventory 而非 pattern NBT
- `GuiUploadButtonHandler.java` — 客户端校验改为 `countOutputSlots()`

---

## 2.11.1 - Bug修复：样板交换按钮逻辑修正 + 位置调整

> 作者：wztwzt | 更新时间：2026-08-10

### Bug修复

- **交换逻辑修正**：从"交换 `in`/`out` 整个列表"改为"交换 `out` 列表内第0和第1个元素"，正确实现主副产物交换
- **按钮位置调整**：从上传按钮左侧移到上传按钮正上方
- **客户端校验**：点击前检查 `out` 列表至少有 2 个元素

### 修改文件

- `SwapPatternPacket.java` — 交换逻辑改为 `outTag.func_150304_a(0, out1)` / `func_150304_a(1, out0)`
- `GuiUploadButtonHandler.java` — 按钮位置 `swapBtnY = uploadBtnY - btnSize - 2`；客户端校验改为检查 `out` 标签

---

## 2.11.0 - 新功能：样板主副产物交换按钮

> 作者：wztwzt | 更新时间：2026-08-10

### 新功能

- **样板交换按钮**：在 AE2 样板终端 GUI 新增 ⇄ 交换按钮（位于 ↑上传 按钮左侧），点击后交换当前样板的输入/输出物品
- 支持 `ContainerPatternTerm` 和 `ContainerPatternTermEx`（ExtendedAE 扩展样板终端）
- 需要输出槽中已有编码样板，且样板包含 `in`/`out` 标签

### 按钮布局

```
  ⇄(997)  ↑(999)  ←(998)
         编码按钮
```

### 新增文件

- `SwapPatternPacket.java` — 客户端→服务端，交换样板 NBT 的 `in`/`out` 标签

### 修改文件

- `ModNetwork.java` — 注册 `SwapPatternPacket`
- `GuiUploadButtonHandler.java` — 添加交换按钮 + 点击事件处理

---

## 2.10.0-fix11 - Bug修复：绑定逻辑不生效（C08数据包未发送）

> 作者：wztwzt | 更新时间：2026-08-09

### 问题

fix10 的绑定逻辑仍然不生效。客户端 `onItemUseFirst` 对绑定目标返回 `true`，导致客户端 `PlayerControllerMP.onPlayerRightClick` 在行371直接 `return true`，**C08PacketPlayerBlockPlacement 永远不会发送**（行389被跳过）。服务端从未收到右键数据包，`activateBlockOrUseItem` 从未被调用，服务端的绑定逻辑为死代码。

### 修复

- **客户端 `onItemUseFirst` 返回 `false`**：让C08数据包正常发送到服务端，服务端正常执行绑定逻辑

### 修改文件

- `ItemWirelessConnector.java` — 客户端 `onItemUseFirst` 返回 `false`

---

## 2.10.0-fix10 - Bug修复：绑定器shift+右键收发器失效 + 绑定逻辑架构修复

> 作者：wztwzt | 更新时间：2026-08-09

### 问题

- **shift+右键收发器绑定频道失效**：fix9新增的 `onItemUseFirst` 返回 `true` 会同时跳过 `onBlockActivated` **和** `onItemUse`，导致绑定逻辑永远不执行
- **ME接口/GT舱室方块链接失效**：同样原因，`onItemUse` 中的方块链接逻辑也无法执行

### 根因

Forge 1.7.10 调用链：`onItemUseFirst → onBlockActivated → onItemUse`，`onItemUseFirst` 返回 `true` 会跳过**全部后续调用**，包括 `onItemUse` 中的绑定逻辑

### 修复

- **绑定逻辑前移**：将全部绑定逻辑（收发器绑定、方块链接、解绑等）从 `onItemUse` 移入 `onItemUseFirst` 服务端分支，`onItemUseFirst` 返回 `true` 时直接执行绑定
- **`onItemUse` 简化**：仅保留 `return true` 兜底（非绑定目标不会到达此处）
- **`doesSneakBypassUse` → `false`**：潜行时不再绕过 `onBlockActivated`，避免误开非绑定目标GUI

### 修改文件

- `ItemWirelessConnector.java` — `onItemUseFirst` 包含全部绑定逻辑；`onItemUse` 简化为 `return true`；`doesSneakBypassUse` → `false`

---

## 2.10.0-fix9 - Bug修复：绑定器无法绑定GT舱室/ME接口 + GUI拦截

> 作者：wztwzt | 更新时间：2026-08-09

### Bug修复

- **绑定器拦截GUI**：新增 `onItemUseFirst()` 方法，在 `Block.onBlockActivated` 之前执行，阻止ME接口/舱室的GUI被打开
- **GT ME舱室/ME接口绑定**：所有 `instanceof IGridHost` 检查改为 `instanceof TileCableBus || instanceof IGridHost`，支持独立方块和线缆部件两种形式
- **doesSneakBypassUse**：改为返回 `true`，让潜行时也走 `onItemUseFirst` 钩子
- **onItemUse 返回值**：所有分支统一返回 `true`，确保不触发 `onBlockActivated`

### 修改文件

- `ItemWirelessConnector.java` — 新增 `onItemUseFirst()`；`isBindingTarget()` 统一判断；`TileCableBus || IGridHost`；`doesSneakBypassUse` → true

---

## 2.10.0-fix8 - Bug修复：物品栏渲染崩溃 + 绑定器UI拦截

> 作者：wztwzt | 更新时间：2026-08-09

### Bug修复

- **物品栏渲染崩溃修复**：`renderInventoryBlock` 改回 Tessellator 方式，去掉 `addTranslation` 偏移；不再调用 `renderStandardBlock`（物品栏渲染时 `IBlockAccess` 为 null 导致 NPE）
- **绑定器UI拦截**：绑定器未绑定频率时右键 IGridHost 方块（如ME接口），返回 `true` 阻止 `onBlockActivated` 打开方块GUI，并提示"请先绑定频道"

### 修改文件

- `RenderBlockTransceiver.java` — `renderInventoryBlock` 改用 Tessellator + `renderFace*` 直接传 icon
- `ItemWirelessConnector.java` — 未绑定频率时对 IGridHost 方块返回 `true` 阻止 GUI 打开

---

## 2.10.0-fix7 - Bug修复：材质偏移 + 频道切换消失 + 绑定器取消 + 高亮无效 + 绑定器适配

> 作者：wztwzt | 更新时间：2026-08-09

### Bug修复

- **材质偏移修复**：`renderInventoryBlock` 改用 `renderer.renderStandardBlock(block, 0, 0, 0)` + `setOverrideBlockTexture(iconBase)`，修复物品栏显示错误纹理的问题
- **切换频道消失修复**：`handleSetFrequency` 改用 `unregister(oldFreq)` 只删除内存 senderMap，不再从 `WirelessWorldData` 持久化中删除频道
- **绑定器取消绑定修复**：`WirelessWorldData` 新增 `removeBlockLink(freq, posKey)` 方法精确按位置删除；绑定器调用带位置参数的版本
- **高亮无效修复**：`WirelessHighlightRenderer` 改注册到 `MinecraftForge.EVENT_BUS`；事件改为 `RenderWorldLastEvent`；颜色改为红色 (255,0,0)
- **绑定器适配增强**：`getGridNodeFromTE` 策略调整 — TileCableBus 先尝试 `part.getGridNode(dir)`，再 `part.getExternalFacingNode()`；IGridHost 先指定面 → UNKNOWN → 6方向遍历

### 修改文件

- `RenderBlockTransceiver.java` — `renderInventoryBlock` 改用 `renderStandardBlock`
- `WirelessActionPacket.java` — `handleSetFrequency` 改用 `unregister(oldFreq)` 不删持久化
- `WirelessWorldData.java` — 新增 `removeBlockLink(freq, posKey)` 方法
- `ItemWirelessConnector.java` — 绑定器调用精确删除；`getGridNodeFromTE` 策略调整
- `ClientProxy.java` — 高亮渲染器改注册到 `MinecraftForge.EVENT_BUS`
- `WirelessHighlightRenderer.java` — 事件改为 `RenderWorldLastEvent`；颜色改为红色
- `WirelessBlockLinkManager.java` — `getGridNode` 同步调整

---

## 2.10.0-fix6 - Bug修复：物品栏图标 + 频道创建 + 绑定器多设备 + 舱室适配

> 作者：wztwzt | 更新时间：2026-08-09

### Bug修复

- **物品栏图标修复**：`renderInventoryBlock` 添加 Tessellator 管理（`startDrawingQuads`/`draw`），修复ISBRH渲染不提交顶点导致图标不显示的问题
- **频道创建修复**：`handleAddChannel` 只注册全局频道到 `WirelessWorldData`，不再覆盖收发器频率；用户从列表点击选择要连接的频率
- **绑定器多设备支持**：`WirelessBlockLinkManager` 从 `Map<String, WirelessBlockLinkData>` 改为 `Map<String, List<WirelessBlockLinkData>>`，同一频率可绑定多个设备
- **绑定器响应速度**：`processAll()` 处理间隔从 20 tick 缩短到 5 tick（0.25秒）
- **绑定器舱室适配**：`getGridNodeFromTE` 先尝试 `UNKNOWN` 方向，失败后遍历6个方向，适配所有 `IGridHost` 设备

### 修改文件

- `RenderBlockTransceiver.java` — `renderInventoryBlock` 添加 Tessellator 管理和居中平移
- `WirelessActionPacket.java` — `handleAddChannel` 改为 `addGlobalChannel`；`handleRemoveChannel` 支持删除指定频道
- `WirelessData.java` — 新增 `addGlobalChannel` 方法；`getAllFrequencies` 从 `WirelessWorldData` 读取；`isFrequencyTaken` 检查持久化数据
- `WirelessBlockLinkManager.java` — 改为 `Map<String, List<WirelessBlockLinkData>>`；`register` 追加不替换；`unregister(freq, positionKey)` 按位置删除；连接键改为 `freq:positionKey`
- `ItemWirelessConnector.java` — `getGridNodeFromTE` 遍历6个方向；绑定器按位置匹配切换绑定
- `WirelessWorldData.java` — `addBlockLink` 按位置去重，支持同频率多设备
- `TileWirelessTransceiver.java` — `updateEntity` 处理间隔缩短到 5 tick

---

## 2.10.0-fix5 - Bug修复：收发器不占频道 + WAILA频道数 + 严格单向 + UI改进

> 作者：wztwzt | 更新时间：2026-08-09

### Bug修复

- **收发器不占用频道**：移除 `REQUIRE_CHANNEL` 标志，收发器作为纯桥梁不消耗AE2频道
- **WAILA频道数显示**：连接后显示实际传输频道数/可用频道数（如 `3/22`），未连接时显示全局频率总数
- **UI按钮右移修复**：按钮宽度55→53，起始位置8→7，消除右侧超出GUI边界
- **绑定器右键机器**：`doesSneakBypassUse` 改为 `false`，防止潜行时绕过绑定逻辑打开机器UI
- **严格单向连接**：添加 `originalSenderPos` 验证，发送端→接收端方向锁定，互换角色后不自动连接
- **UI状态同步**：`setConnected()`/`setPaused()` 添加 `markBlockForUpdate()`，修复GUI显示"未连接"但实际已连接的问题
- **频道名无限制**：移除32频道名上限，支持任意数量频道名
- **频道列表滚动**：添加鼠标滚轮滚动和拖动滚动条，超出显示范围时自动启用

### 修改文件

- `TileWirelessTransceiver.java` — 移除REQUIRE_CHANNEL；添加usedChannels/maxChannels/originalSenderPos字段；setConnected/setPaused加markBlockForUpdate
- `WirelessLinkManager.java` — 验证发送端身份（originalSenderPos）；更新频道计数
- `WirelessActionPacket.java` — 移除32限制；模式切换时存储发送端位置
- `TransceiverWailaProvider.java` — 显示实际传输频道数/可用频道数
- `ItemWirelessConnector.java` — doesSneakBypassUse改为false
- `GuiWireless.java` — 按钮宽度调整；鼠标滚轮+拖动滚动条

---

## 2.10.0-fix4 - Bug修复：物品栏图标 + 绑定器右键 + 无线频道传输 + UI布局

> 作者：wztwzt | 更新时间：2026-08-08

### Bug修复

- **物品栏图标修复**：`BlockWirelessTransceiver.registerBlockIcons()` 设置 `this.blockIcon`，修复物品栏显示缺失材质的问题
- **绑定器右键机器修复**：`ItemWirelessConnector.onItemUse()` 客户端返回 `true`，阻止 `onBlockActivated` 干扰绑定逻辑
- **无线频道传输修复**：`blockProxy.getFlags()` 添加 `GridFlags.REQUIRE_CHANNEL`，与AE2原版 `TileWireless` 一致；创建 `GridConnection` 后调用 `receiverNode.updateState()` 确保节点加入网格
- **UI文字重叠修复**：调整标题和标签 Y 坐标，消除重叠
- **按钮拆分**：Mode 切换按钮拆分为"发送端"（蓝色）和"接收端"（黄色）两个独立按钮
- **接收端颜色**：接收端模式颜色从红色改为黄色

### 修改文件

- `BlockWirelessTransceiver.java` — `registerBlockIcons` 设置 `this.blockIcon`
- `ItemWirelessConnector.java` — `onItemUse` 客户端返回 `true`
- `TileWirelessTransceiver.java` — `blockProxy.getFlags()` 添加 `REQUIRE_CHANNEL`
- `WirelessLinkManager.java` — 创建连接后调用 `receiverNode.updateState()`
- `GuiWireless.java` — 标题Y坐标调整、按钮拆分（Sender/Receiver）、接收端黄色

---

## 2.10.0-fix3 - Bug修复 + 功能增强

> 作者：wztwzt | 更新时间：2026-08-08

### Bug修复

- **绑定器shift+右键修复**：手持绑定器shift+右键收发器不再打开GUI，改为正确绑定频道
- **频道列表同步**：修复客户端频道列表为空导致创建第二个频道时第一个消失的问题
- **断开按钮修复**：点击断开后不再立即重连，添加暂停状态；添加频道/切换模式时自动恢复连接
- **输入限制修复**：移除数字限制，支持输入中文/英文频道名
- **GUI自动聚焦修复**：打开GUI时输入框不再自动获取焦点
- **StackOverflowError修复**：修复validate()中markDirty()导致的无限递归崩溃

### 材质修复

- 方块材质从32x32四象限精灵图裁剪为16x16单WiFi图标
- 物品栏图标正常显示

### 新功能

- **最大32频道限制**：与ME控制器单面频道数一致
- **WAILA频道数显示**：显示"频道数: X/32"，暂停状态显示"已暂停"
- **高亮连接频道按钮**：点击后高亮显示当前频道连接的所有方块边框

### 新增文件

- `network/WirelessChannelSyncPacket.java` — 服务端→客户端频道列表同步包
- `network/WirelessHighlightPacket.java` — 高亮位置同步包
- `client/render/WirelessHighlightRenderer.java` — 方块高亮渲染器（RenderWorldLastEvent）

### 修改文件

- `BlockWirelessTransceiver.java` — onBlockActivated检查绑定器
- `TileWirelessTransceiver.java` — 添加paused/highlightEnabled字段
- `WirelessLinkManager.java` — 检查paused状态
- `WirelessActionPacket.java` — 添加HIGHLIGHT/SYNC频道列表同步，32频道限制
- `ContainerWireless.java` — 频道列表同步
- `GuiWireless.java` — 输入限制修复、高亮按钮、暂停状态显示、频道列表同步
- `TransceiverWailaProvider.java` — 频道数/暂停状态显示
- `ClientState.java` — 高亮渲染状态
- `ClientProxy.java` — 注册高亮渲染器
- `ModNetwork.java` — 注册新数据包
- 语言文件 — 添加highlight/paused/channels_used翻译

---

## 2.10.0-fix2 - 材质渲染 + GUI/线缆修复 + Waila改进 + 消息补全

> 作者：wztwzt | 更新时间：2026-08-08

### 自定义 ISBRH 渲染器

**新增文件：**
- `client/render/RenderBlockTransceiver.java` — 自定义 `ISimpleBlockRenderingHandler`，从32x32精灵图中根据 TileEntity 状态选择正确的子纹理区域渲染每个面

**材质子区域映射：**
| 状态 | 32x32位置 | UV区域 |
|------|-----------|--------|
| 接收端·未连接 | 左上 | u=[min,mid] v=[min,mid] |
| 发送端·未连接 | 左下 | u=[min,mid] v=[mid,max] |
| 发送端·已连接 | 右下 | u=[mid,max] v=[mid,max] |
| 已连接(侧面) | `_light.png` 动画纹理 |
| 已连接(顶面) | `_light_top.png` 动画纹理 |
| 已连接(底面) | `_light_bottom.png` 动画纹理 |

### GUI/线缆修复

**修改文件：**
- `wireless/BlockWirelessTransceiver.java` — 添加 `hasTileEntity(int)` 返回 `true`（修复 GUI 打不开和线缆无法连接）；覆盖 `getRenderType()` 返回自定义渲染器 ID；覆盖 `registerBlockIcons()` 委托给 ISBRH；覆盖 `isOpaqueCube()` 返回 `true`
- `ClientProxy.java` — 注册 `RenderBlockTransceiver` 渲染器

### Waila 注册改进

**修改文件：**
- `CommonProxy.java` — Waila IMC 注册不再静默吞掉异常，改为 `LOG.error()` 记录错误；添加小写 `"waila"` 备用尝试

### 绑定器消息补全

**修改文件：**
- `wireless/ItemWirelessConnector.java` — 右键空气非潜行时提示"请先绑定频道"或"已绑定频道，请右键ME设备"；右键非支持方块时提示"目标方块不支持无线连接"
- `lang/zh_CN.lang` — 新增3个 key：`bind.fail.invalid_target`、`hint.bind_first`、`hint.use_device`
- `lang/en_US.lang` — 同步新增对应英文翻译

---

## 2.10.0-fix1 - 诊断修复：PreInit 崩溃诊断 + 依赖声明

> 作者：wztwzt | 更新时间：2026-08-08

### 依赖声明

**修改文件：**
- `MyMod.java` — `@Mod` 注解添加 `dependencies = "required-after:appliedenergistics2"`，确保 AE2 在本 mod 之前加载

### PreInit 诊断

**修改文件：**
- `CommonProxy.java` — `WirelessBlocks.preInit()` 包裹 try-catch，捕获异常同时通过 `LOG.error()` 和 `t.printStackTrace(System.err)` 双重输出
- `MyMod.java` — `ModNetwork.registerPackets()` 包裹 try-catch，同上双重输出

**改动：**
1. **依赖加载顺序修复**：添加 `required-after:appliedenergistics2` 确保 AE2 的类（IGridHost、IGridNode 等）在我们的 mod preInit 之前可用
2. **PreInit 崩溃诊断**：Log4j 的 `ThrowableProxy` 在 RfbSystemClassLoader 下无法加载 `net.minecraft.block.Block`，导致真正的 preInit 异常被完全遮蔽。通过 try-catch + `System.err` 绕过 Log4j，可捕获并输出真实异常堆栈

---

## 2.10.0 - 无线直连功能 + BlockContainer 崩溃修复

> 作者：wztwzt | 更新时间：2026-08-08

### BlockContainer 崩溃修复

**修改文件：**
- `wireless/BlockWirelessTransceiver.java` — `extends BlockContainer` 改为 `extends Block implements ITileEntityProvider`，绕过 Java 17 环境下 `BlockContainer` 类加载失败的问题；`breakBlock()` 手动调用 `world.removeTileEntity()`

### 无线直连任意 ME 设备

**新增文件：**
- `wireless/link/WirelessBlockLinkData.java` — 直连数据类（坐标、频道、UUID、维度、方向）
- `wireless/link/WirelessBlockLinkManager.java` — 直连管理器，负责扫描、连接、断开无线直连
- `wireless/WirelessBlockEventListener.java` — Forge 方块破坏事件监听，自动清理被破坏方块的无线连接
- `wireless/ItemBlockTransceiver.java` — 收发器物品方块，提供 tooltip 说明

**修改文件：**
- `wireless/ItemWirelessConnector.java` — 新增绑定器交互逻辑：右键 IGridHost 方块建立无线连接；右键已绑定设备解除连接；AE2 线缆部件识别（通过 `TileCableBus.getPart()` 获取特定面的部件）；跨维度限制提示；tooltip 说明
- `wireless/WirelessWorldData.java` — 新增 `wireless_block_links` NBT 存储，支持 block link 持久化
- `wireless/WirelessData.java` — 引用 `WirelessBlockLinkManager`，`unregister()` 时同步清理 block link
- `wireless/TileWirelessTransceiver.java` — `updateEntity()` 中调用 `WirelessBlockLinkManager.processAll()`；新增 `removeAllBlockLinks()` 方法
- `wireless/BlockWirelessTransceiver.java` — `breakBlock()` 中额外清理关联的 block links
- `wireless/WirelessBlocks.java` — 注册 `ItemBlockTransceiver` 替代默认 ItemBlock
- `CommonProxy.java` — 注册 `WirelessBlockEventListener` 事件监听
- `network/WirelessActionPacket.java` — 硬编码字符串替换为 `StatCollector` 翻译

### Tooltip + 汉化

**修改文件：**
- `lang/zh_CN.lang` — 新增 12 个 tooltip key + 5 个新功能 chat 消息 key
- `lang/en_US.lang` — 同步新增对应英文翻译

### 绑定器完整交互逻辑

| 操作 | 目标 | 行为 |
|------|------|------|
| Shift+右键 | 收发器 | 绑定频道到连接器 |
| Shift+右键 | 空气 | 清除绑定 |
| 右键 | 收发器 | 设置为接收端 |
| 右键 | IGridHost 方块 | 建立无线直连（仅同维度） |
| 右键 | 已绑定的 IGridHost | 解除无线直连 |

---

## 2.9.0 - 重大修复：映射持久化 + 无线收发器全面重构

> 作者：wztwzt | 更新时间：2026-08-08

### 映射持久化修复

**新增文件：**
- `client/ClientRecipeNameUtil.java` — 客户端专属 NEI 配方名工具，从 RecipeNameUtil 分离

**修改文件：**
- `util/RecipeNameUtil.java` — 移除 `import codechicken.nei.recipe.IRecipeHandler`；静态初始化 try-catch 防护；`writeTemplate()` 仅在文件不存在时创建；新增 `loadBuiltinDefaults()` 加载 jar 内置 47 条默认映射；`CONFIG_FILE` 保护 null 检查；公开 `CAMEL_CASE_SPLITTER` 和 `mapStringToMapping()` 供 ClientRecipeNameUtil 使用
- `common/RecipeMapNameConfig.java` — `reload()` 和 `resolveSearchKeyword()` 加 `synchronized`；改用 volatile Map 引用原子替换避免竞态
- `mixin/nei/MixinDefaultOverlayHandler.java` — 改用 `ClientRecipeNameUtil.captureFromRecipeHandler()`
- `mixin/nei/MixinRecipeHandlerRef.java` — 同上

**改动：**
1. **NEI 依赖分离**：`RecipeNameUtil` 不再 import 客户端专属的 `IRecipeHandler`，服务器端加载不会因缺少 NEI 类而崩溃（`ExceptionInInitializerError`）
2. **静态初始化防护**：`Loader.instance().getConfigDir()` 调用包裹在 try-catch 中，即使 `Loader` 未就绪也不会导致类初始化失败
3. **配置文件写入保护**：`writeTemplate()` 仅在文件不存在时创建空 `{}`，不会覆盖已有数据
4. **内置默认映射**：启动时从 `apu/recipe_type_names.json` 加载 47 条默认映射作为后备，用户自定义映射覆盖默认值
5. **竞态条件修复**：`RecipeMapNameConfig` 改用 volatile Map 引用原子替换，`reload()` 和 `resolveSearchKeyword()` 加 synchronized

### 无线收发器全面重构

**新增文件：**
- `network/WirelessActionPacket.java` — C2S 网络包，携带 action 枚举（添加频道/删除/切换模式/断开/设置频率）+ 频道名 + 模式值

**修改文件：**
- `wireless/TileWirelessTransceiver.java` — 新增 `wirelessConnection` 字段保存 IGridConnection 引用；`validate()` 自动注册发送端；`invalidate()`/`onChunkUnload()` 先销毁无线连接再注销；新增 `destroyWirelessConnection()` 方法；`updateEntity()` 节流到每 20 tick；null 频率保护
- `wireless/BlockWirelessTransceiver.java` — `breakBlock()` 调用 `twt.destroyWirelessConnection()` + `unregister(freq, world)`；移除冗余 `node.destroy()`（由 `invalidate()` 处理）
- `wireless/WirelessData.java` — `register()` 同步写入 WirelessWorldData；新增 `unregister(freq, world)` 从持久化中移除频道；新增 `saveToWorldData()` 方法
- `wireless/WirelessWorldData.java` — 修复 `getActiveChannels()` 返回防御性拷贝而非可变引用；`get()` 方法改用 `DimensionManager.getWorld(0)` 获取主世界；去重保护
- `wireless/WirelessLinkManager.java` — 完全重写：移除反射 `GridConnection` 构造器，改用 `AEApi.instance().createGridConnection()` 公开 API；连接追踪（`te.getWirelessConnection()` / `te.setWirelessConnection()`）；连接有效性验证；错误日志记录
- `wireless/gui/ContainerWireless.java` — 重写为标准三层架构：服务端逻辑 + `detectAndSendChanges()` 数据同步
- `wireless/gui/GuiWireless.java` — 全面重写：标准 MC GUI 尺寸（176×166）；白边框黑底（#161618）；左侧频道列表（单选，绿底白字）+ 输入框（仅数字）；右侧状态面板（频道/模式/连接状态，颜色编码）；底部 2×2 按钮区（添加/删除/模式/断开）；删除确认弹窗（模态遮罩）；所有操作通过 WirelessActionPacket 发送到服务端
- `wireless/ItemWirelessConnector.java` — 聊天消息全部汉化：`StatCollector.translateToLocalFormatted()` 替换硬编码英文；新增绑定失败提示（非发送端、无频道）
- `network/ModNetwork.java` — 注册 WirelessActionPacket（discriminator 4, SERVER）

**删除文件：** 无

**改动：**
1. **频道持久化**：WirelessData 注册/注销同步写入 WirelessWorldData（WorldSavedData），服务器重启后频道名不丢失
2. **自动恢复注册**：TileEntity 在 `validate()` 时自动检查并恢复发送端注册，无需手动干预
3. **连接追踪与销毁**：TileEntity 保存 IGridConnection 引用，发送端注销或接收端断开时正确调用 `connection.destroy()`，消除幻影连接
4. **公开 API 替代反射**：`AEApi.instance().createGridConnection(nodeA, nodeB)` 替代反射创建 `GridConnection`，更稳定且兼容性更好
5. **服务端 GUI 逻辑**：所有按钮操作通过 `WirelessActionPacket` 发送到服务端，Container 处理业务逻辑后同步数据回客户端
6. **GUI 重设计**：标准 MC GUI 布局，白边框黑底，频道列表 + 状态面板 + 按钮区，删除确认弹窗
7. **性能优化**：`updateEntity()` 从每 tick 处理改为每 20 tick（1秒）处理一次
8. **绑定器汉化**：所有聊天消息使用 lang key 翻译，支持中英文切换

---

## 2.8.0-fix1 - 修复：无线收发器 GUI 背景 + i18n + Waila + Java 1.7 兼容

> 作者：wztwzt | 更新时间：2026-08-07

**修改文件：**
- `wireless/gui/GuiWireless.java` — 背景改用 wireless.png（1024x1024 纹理 Tessellator 缩放），按钮/标题/模式/状态全部改为 `StatCollector.translateToLocal()`
- `wireless/TransceiverWailaProvider.java` — Waila body 改用 `StatCollector.translateToLocal()`，修复 Java 1.7 不支持的 `instanceof TileWirelessTransceiver tile` 语法（改为传统强转）
- `lang/en_US.lang` — 新增 wireless GUI/模式/状态/频道/按钮/连接器翻译键，修复 `item.wireless.connect.name` → `item.wireless_connect.name`
- `lang/zh_CN.lang` — 同步新增中文翻译

**改动：**
1. **GUI 背景修复**：`wireless.png` 纹理为 1024x1024，`drawTexturedModalRect` 只支持 256x256。改用 `Tessellator` 直接绘制缩放 UV，正确显示无线收发器背景
2. **全量 i18n**：GUI 标题、频道列表标签、模式/状态文本、按钮文字全部使用 `StatCollector.translateToLocal()`，支持中英文切换
3. **Waila i18n**：`TransceiverWailaProvider.getWailaBody()` 原先硬编码 "Mode: Sender" 等英文字符串，改为通过 lang key 翻译
4. **Java 1.7 兼容**：`instanceof TileWirelessTransceiver tile` 是 Java 16+ 语法，改为 `instanceof TileWirelessTransceiver` + 强转
5. **连接器 lang 修正**：`setUnlocalizedName("wireless_connect")` 对应 key 应为 `item.wireless_connect.name`（下划线），原先写成 `item.wireless.connect.name`（点号）

---

## 2.8.0-beta1 - 新功能：AE2 无线收发器（测试版）

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `wireless/TileWirelessTransceiver.java` — 无线收发器 TileEntity（IGridHost + IActionHost + IGridBlock）
- `wireless/BlockWirelessTransceiver.java` — 方块注册、GUI打开、breakBlock 注销
- `wireless/WirelessData.java` — 全局频道注册器（单例 Map）
- `wireless/WirelessLinkManager.java` — 收发连接逻辑（反射创建 GridConnection）
- `wireless/WirelessWorldData.java` — WorldSavedData 频道持久化
- `wireless/WirelessBlocks.java` — 方块/物品/实体注册
- `wireless/WirelessGuiHandler.java` — IGuiHandler 实现
- `wireless/ItemWirelessConnector.java` — 无线连接器手持工具
- `wireless/gui/ContainerWireless.java` — 无线收发器 Container
- `wireless/gui/GuiWireless.java` — 无线收发器 GUI（输入框 + 列表 + 按钮）
- 材质文件（textures/blocks, items, gui）

**修改文件：**
- `MyMod.java` — 添加 `@Mod.Instance`
- `CommonProxy.java` — preInit 注册 WirelessBlocks，init 注册 GuiHandler

**改动：**
1. **无线收发器方块**：接入 AE2 网络后可通过 GUI 设置频道和收发模式
2. **发送端/接收端匹配**：同一频道仅允许一个发送端；接收端通过反射创建 GridConnection 虚拟线缆
3. **频道持久化**：WorldSavedData 保存活跃频道列表
4. **无线连接器**：手持工具，绑定频道后右键方块即可单点接入
5. **GUI**：频道输入、添加/删除/模式切换/断开连接操作
6. **已知问题**：连接建立逻辑需要游戏内验证，目前为 beta 阶段

---

## 2.7.0-fix3 - 修复：混入目标改为 PanelWidget.draw()

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `mixin/nei/MixinPanelWidgetDraw.java` — 替代 `MixinItemsGridDraw`，混入 `PanelWidget.draw(II)` TAIL，通过 `self.x` 判断左侧/右侧
- `mixins.ae2_auto_pattern_upload.json` — `MixinItemsGridDraw` → `MixinPanelWidgetDraw`

**删除文件：**
- `mixin/nei/MixinItemsGridDraw.java` — `ItemsGrid` 级别无法获取 panel 的 x 坐标，区分不了左/右面板

**改动：**
1. **修复左侧判断**：`ItemsGrid` 的 `getSlotRect(0,0).x` 在不同子类中与 panel 位置不一致。改为从 `PanelWidget.x` 直接获取面板的屏幕 x 坐标，准确区分左（书签）/右（全物品）
2. **功能完好**：书签面板显示叠加层，全物品面板不显示，数字 0.6 缩放左置，GlScissor 保护，`/apu-overlay` 开关均保留

---

## 2.7.0-fix2 - 修复：仅书签面板 + 数字左置 + 配置文件开关

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `client/OverlayConfig.java` — 配置文件 `settings.json` 读写，每次渲染实时读取
- `client/CommandOverlay.java` — `/apu-overlay` 游戏内切换命令

**修改文件：**
- `mixin/nei/MixinItemsGridDraw.java` — 左侧面板判断（`getSlotRect(0,0).x < screenWidth/2`）、GlScissor 保护
- `client/NetworkInventoryDrawHandler.java` — 数字缩小（0.6x）、放到物品框左边、检查 `OverlayConfig`
- `client/ClientState.java` — 移除 `toggleOverlay()` 方法
- `client/event/KeyInputHandler.java` — 移除 O 键逻辑
- `ClientProxy.java` — 注册 `/apu-overlay` 命令
- `gradle.properties` — 版本号 2.7.0-fix2

**改动：**
1. **仅书签面板**：通过首个 slot 的 x 坐标判断是否为左侧面板，右侧全物品不显示
2. **文字缩放**：数字和 "+" 缩小为 0.6x，放在物品框左侧（不遮挡图标）
3. **GlScissor 禁用**：绘制前禁用裁剪，防止数字被面板边缘裁切
4. **游戏内开关**：`/apu-overlay` 命令切换，或修改 `config/ae2_auto_pattern_upload/settings.json` 中 `nei_overlay_enabled` 字段

---

## 2.7.0-fix1 - 修复：NEI 物品面板叠加显示（第二版）

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `mixin/nei/MixinItemsGridDraw.java` — 混入 `ItemsGrid.draw(II)` TAIL，在完整绘制链路结束后叠加 AE2 数据

**修改文件：**
- `client/NetworkInventoryDrawHandler.java` — 静态工具类，接收 x/y/ItemStack 绘制叠加层
- `mixins.ae2_auto_pattern_upload.json` — MixinItemsGridSlot → MixinItemsGridDraw
- `ClientProxy.java` — 撤销 v2.7.0 的 `addDrawHandler` 注册

**删除文件：**
- `mixin/nei/MixinItemsGridSlot.java` — 废弃方案（inner class @Mixin 不支持 @Inject）

**改动：**
1. **修复叠加不显示**：不再注入 inner class，改为注入 `ItemsGrid.draw()` TAIL。在 `afterDrawItems()` 执行完后、GL 状态正常时遍历 `getMask()` + `getSlotRect()` 画叠加层
2. **渲染方式**：右上角绿色 +（可合成），右下角青色数字（AE2 网络存量）
3. **GL 安全**：在 draw 完整结束后画文字，不受 slot 内部 GL 变换影响

---

## 2.7.0 - 新功能：NEI 网络库存叠加显示

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `client/NetworkInventoryCache.java` — 客户端缓存 AE2 网络库存数据（数量 + 可合成标识）
- `client/NetworkInventoryDrawHandler.java` — NEI 渲染处理器，在物品槽位上叠加显示 AE2 网络存量和可合成标识
- `mixin/nei/MixinGuiMEMonitorable.java` — 拦截 AE2 终端的 `postUpdate`，将网络库存数据写入缓存

**修改文件：**
- `ClientProxy.java` — 注册 `NetworkInventoryDrawHandler` 到 NEI
- `mixins.ae2_auto_pattern_upload.json` — 新增 `MixinGuiMEMonitorable`

**改动：**
1. **NEI 物品数量显示**：打开任意 AE2 终端（终端、样板终端、合成终端）时，NEI 物品栏叠加显示 AE2 网络中的物品数量
2. **可合成标识**：有合成配方的物品在右上角显示绿色 "+" 标识
3. **数量格式化**：自动格式化为 K/M/G 单位（如 1.2K、3.5M）
4. **仅终端打开时有效**：终端关闭后缓存自动失效，数据不再残留

---

## 2.6.0-fix4 - 清理：移除 Ctrl+编码自动上传

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/GuiUploadButtonHandler.java` — 移除 Ctrl+编码自动上传相关代码（onActionPerformedPost、onClientTick、ctrlEncodePending）

**改动：**
1. **移除 Ctrl+编码功能**：该功能不稳定，已移除
2. **保留其他功能**：NEI 配方池自动检测、记忆持久化、撤回修复均保留

---

## 2.6.0-fix3 - 修复：记忆持久化 + Ctrl编码改用事件方案

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/ClientState.java` — 新增 `rememberedProviders` 磁盘持久化（保存/加载到 `config/ae2_auto_pattern_upload/remembered_providers.json`）
- `client/event/GuiUploadButtonHandler.java` — Ctrl+编码自动上传改用 `ActionPerformedEvent.Post` + `TickEvent` 方案（移除 MixinGuiPatternTerm）
- `mixin/MixinGuiPatternTerm.java` — 已删除
- `mixins.ae2_auto_pattern_upload.json` — 移除 MixinGuiPatternTerm

**改动：**
1. **记忆持久化**：`rememberedProviders`（配方池→供应器名映射）保存到磁盘，重启游戏不丢失
2. **移除问题 Mixin**：`MixinGuiPatternTerm` 目标 AE2 类导致 `TileEntitySpecialRenderer` 加载崩溃，改用纯事件方案
3. **Ctrl+编码流程**：`ActionPerformedEvent.Post` 检测 Ctrl+编码 → 设标记 → `TickEvent` 等待输出槽出现样板 → 自动上传

---

## 2.6.0-fix2 - 优化：Shift+编码自动上传 + 删除 Shift 上传强制GUI

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `mixin/MixinGuiPatternTerm.java` — 新增：拦截编码按钮 Shift+点击，已记忆配方池时自动编码+上传
- `client/event/GuiUploadButtonHandler.java` — 删除 Shift+点击上传打开选择页面的功能，上传按钮始终直接上传
- `mixins.ae2_auto_pattern_upload.json` — 注册 MixinGuiPatternTerm

**改动：**
1. **Ctrl+编码自动上传**：当配方池已有记忆的供应器时，Ctrl+点击编码按钮 → 自动编码（放入输出槽）+ 自动上传，省去再点上传按钮
2. **删除 Shift 上传强制 GUI**：上传按钮不再检测 Shift 键，始终执行直接上传逻辑
3. **操作流程简化**：已记忆配方池的机器只需 Shift+编码即可完成全部操作

---

## 2.6.0-fix1 - 修复：撤回功能 + IInterfaceHost 支持 + 清除旧配方池

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `network/RecallPatternPacket.java` — 修复撤回时只匹配 `IInventory`，新增 `IInterfaceHost.getPatterns()` 匹配；撤回时清除样板上的 `apu:recipeMap` 标签
- `network/ProvidersListS2CPacket.java` — 添加上传成功日志
- `client/event/GuiUploadButtonHandler.java` — 添加撤回按钮点击日志

**改动：**
1. **修复 IInterfaceHost 撤回**：撤回时对 ME Interface 等 `IInterfaceHost` 类型的供应器，通过 `host.getPatterns()` 获取样板库存，不再要求 `machine instanceof IInventory`
2. **清除旧配方池标签**：撤回时清除样板上的 `apu:recipeMap` NBT 标签，避免重新编码后继承旧配方池导致搜索框显示错误
3. **添加调试日志**：撤回按钮点击、供应器搜索、样板匹配全流程日志

---

## 2.6.0 - 功能：NEI 配方池自动检测

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `mixin/nei/MixinRecipeHandlerRef.java` — 增强 NEI hook，从 GT NEI Handler 提取 recipeMap.unlocalizedName
- `mixin/nei/MixinDefaultOverlayHandler.java` — 同上，从 DefaultOverlayHandler 提取 recipeMap
- `client/ClientState.java` — 新增 pendingRecipeMap 字段，存储 NEI 捕获的配方池 ID
- `client/event/GuiUploadButtonHandler.java` — 上传时优先使用 NBT/NEI 配方池，写入样板 NBT `apu:recipeMap`
- `network/RequestProvidersListPacket.java` — 新增 directRecipeMap 字段，客户端直接发送配方池 ID 跳过服务端检测

**改动：**
1. **NEI 混入提取配方池**：从 `gregtech.nei.GTNEIDefaultHandler` 的 `recipeMap.unlocalizedName` 字段直接读取配方池 ID（如 `gt.recipe.compressor`），不再依赖输入输出匹配
2. **样板 NBT 持久化**：上传时自动将 recipeMap 写入样板 NBT `apu:recipeMap`，后续上传可直接读取
3. **客户端直接发送配方池**：跳过服务端 328 个配方池的反射检测，减少延迟和误判
4. **fallback 兼容**：未从 NEI 捕获时仍使用旧的输入输出检测逻辑

---

## 2.5.7 - 清理：移除默认映射模板

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `util/RecipeNameUtil.java` — `writeTemplate()` 改为空 JSON

**改动：**
1. **移除 40+ 条预设映射**：默认模板改为空 `{}`，不再预填中文映射
2. **只保留手动添加的映射**：用户通过 GUI 添加的映射（如 `gt.recipe.fluidextractor → 流体提取机`）正常保留
3. **清理方式**：删除 `config/ae2_auto_pattern_upload/recipe_names.json` 重启即可

---

## 2.5.6 - 修复：预填 GT ID + Shift+点击强制选择 + 撤回修复

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/GuiUploadButtonHandler.java` — Shift 检测 + 传递 forceGui 标志
- `network/RequestProvidersListPacket.java` — 新增 forceGui 字段
- `network/ProvidersListS2CPacket.java` — 新增 forceGui 字段 + 重构上传策略
- `common/RecipeMapNameConfig.java` — 新增 extractMachineName 方法
- `client/ClientState.java` — 新增 rememberedProviders 映射记忆

**改动：**
1. **预填 GT 配方池 ID**：搜索框直接填入完整 ID（如 `gt.recipe.compressor`），不再用中文映射
2. **Shift+点击上传**：按住 Shift 点击上传按钮 → 跳过自动上传，强制打开选择页面
3. **撤回修复**：自动上传成功后正确设置 `ClientState.lastProviderId`
4. **Provider 记忆机制**：用户选择 Provider 后记住「配方池 → Provider 名字」，下次自动上传

---

## 2.5.5 - 修复：自动上传方案重构 — 预填搜索 + 记忆 Provider

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/ClientState.java` — 新增 `rememberedProviders` Map 存储配方池→Provider名字映射
- `network/ProvidersListS2CPacket.java` — 重写自动上传逻辑：先查记忆，再开搜索界面
- `client/gui/GuiProviderSelect.java` — 选择 Provider 后保存配方池→名字映射

**改动：**
1. **修复自动匹配永远失败的根本原因**：
   - 旧逻辑：用配方池关键字（如"压缩机"）匹配 Provider 名字 → Provider 名字是"ME 样板供应器"，永远匹配不上
   - 新逻辑：不再用配方池关键字匹配 Provider 名字
2. **引入 Provider 记忆机制**：
   - `ClientState.rememberedProviders`：Map<配方池ID, Provider名字>
   - 用户在搜索界面选择 Provider 后，自动记住「配方池 → Provider 名字」
   - 下次检测到相同配方池时，用记住的名字精确匹配 Provider → 唯一匹配则自动上传
3. **上传策略**：
   - 策略1：只有 1 个有效 Provider → 直接上传
   - 策略2：查已记住的 Provider 名字 → 精确匹配 → 唯一匹配则自动上传
   - 策略3：打开搜索界面，预填配方池关键字（如"压缩机"），用户手动选择
4. **搜索界面行为**：
   - 打开时搜索框预填配方池关键字（如"压缩机"），帮助用户快速找到相关机器
   - 用户手动选择 Provider 后记住，下次自动上传

---

## 2.5.3 - 修复：映射加载 bug + 调试日志

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `util/RecipeNameUtil.java` — 修复 `loadMappings()` 创建模板后不加载映射的 bug
- `common/RecipeMapNameConfig.java` — 添加调试日志
- `network/ProvidersListS2CPacket.java` — 添加调试日志

**改动：**
1. **修复首次加载映射不生效的 bug**：
   - 根本原因：`RecipeNameUtil.loadMappings()` 发现配置文件不存在时调用 `writeTemplate()` 创建模板，然后 `return` — 没有重新读取刚创建的文件
   - 结果：`RAW_MAPPINGS` 和 `LOOKUP_MAPPINGS` 始终为空，所有映射查找都失败
   - 修复：`writeTemplate()` 后不再 `return`，继续执行文件读取逻辑
2. **添加调试日志**（`[APU]` 前缀）：
   - `RecipeMapNameConfig.resolveSearchKeyword`：输出输入、缓存大小、匹配结果
   - `ProvidersListS2CPacket.Handler`：输出 recipeMap、resolvedKeyword、匹配的 provider

---

## 2.5.2 - 修复：统一映射系统 + 修复自动上传/撤回/手动映射

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `common/RecipeMapNameConfig.java` — 完全重写：从 RecipeNameUtil 统一读取映射
- `util/RecipeNameUtil.java` — 模板加入 GT 默认映射 + 添加/删除时同步重载 RecipeMapNameConfig

**改动：**
1. **修复两套映射系统互不通信的问题**：
   - 根本原因：`RecipeNameUtil`（GUI 手动映射）和 `RecipeMapNameConfig`（自动上传）各自管理独立的配置文件，互不通信
   - 旧方案：GUI 手动添加的映射只存在 `RecipeNameUtil`，自动上传的 `RecipeMapNameConfig` 完全不知道
   - 新方案：`RecipeMapNameConfig` 不再自己管理配置文件，改为从 `RecipeNameUtil.getMappingsView()` 读取映射
   - 配置文件统一为 `config/ae2_auto_pattern_upload/recipe_names.json`
2. **添加/删除映射时同步重载**：
   - `RecipeNameUtil.addOrUpdateMapping()` 和 `removeMappingsByCnValue()` 执行后调用 `RecipeMapNameConfig.reload()`
   - 保证手动添加的映射立即对自动上传生效
3. **模板加入 GT 默认映射**：
   - `RecipeNameUtil.writeTemplate()` 从 "example.crafting" 改为 40+ 种 GT 配方池默认映射
   - 首次加载自动生成完整默认配置
4. **RecipeMapNameConfig 重写**：
   - 不再自己管理 `config/apu/recipe_type_names.json`
   - 改为从 `RecipeNameUtil.getMappingsView()` 加载映射到内存缓存
   - 支持 `"compressor"` 和 `"gt.recipe.compressor"` 两种 key 格式

---

## 2.5.1 - 修复：配方池检测输出验证逻辑错误

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `network/RequestProvidersListPacket.java` — 删除有 bug 的输出验证逻辑

**改动：**
1. **修复配方池检测永远返回 null 的问题**：
   - 根本原因：`findRecipeQuery().items(inputs).find()` 找到的配方，其输出可能和样板输出不同（同输入多输出的配方，如编程电路不同导致输出不同）
   - 旧代码：用矿辞匹配验证输出 → 始终失败 → 返回 null → 无法匹配 Provider
   - 新代码：只检查输入匹配 → 成功返回配方池名字 → 触发配置文件关键字映射
2. **不影响配置文件方案**：删除输出验证让 `RecipeMapNameConfig` 的配置文件映射能正常工作

---

## 2.5.0 - 自动上传：配方池检测 + 配置文件关键字映射 + 搜索预填

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `network/RequestProvidersListPacket.java` — 服务端配方池检测 + 矿辞典输出匹配
- `network/ProvidersListS2CPacket.java` — 配方池关键字匹配改为使用 `RecipeMapNameConfig`
- `client/ClientState.java` — lastRecipeMap 字段
- `client/event/GuiUploadButtonHandler.java` — 读取样板内容发送给服务端
- `client/gui/GuiProviderSelect.java` — 搜索框预填 + 唯一匹配自动上传
- `common/RecipeMapNameConfig.java` — 新增：配置文件加载 + 配方池→关键字映射
- `resources/apu/recipe_type_names.json` — 新增：默认配置模板

**改动：**
1. **服务端配方池检测**（`RequestProvidersListPacket.Handler.detectRecipeMap()`）：
   - 客户端读取样板输入/输出 → 发送到服务端
   - 服务端通过反射调用 `RecipeMap.ALL_RECIPE_MAPS` 遍历 GT 配方池
   - 用 `findRecipeQuery().items(inputs).find()` 查找配方
   - 矿辞典输出匹配：用 `OreDictionary.getOreIDs()` 对比 AE2 样板输出与 GT 配方输出
2. **配置文件关键字映射**（`RecipeMapNameConfig`）：
   - 参考 ExtendedAE_Plus 方案，使用 `config/apu/recipe_type_names.json` 配置文件
   - 将硬编码的 30+ 种配方池映射改为可配置的 JSON 文件
   - 格式：`"assembler": "组装机"`, `"macerator": "粉碎机"`
   - 支持别名映射（无冒号的 key）和完整 ID 映射（带冒号的 key）
   - 首次加载自动创建默认配置
   - 支持单机和服务器：配置文件在各自的 `config/apu/` 目录
3. **搜索预填 + 唯一匹配自动上传**：
   - 打开 Provider 选择界面时，自动用 `RecipeMapNameConfig.resolveSearchKeyword()` 查找中文关键字
   - 如果过滤后只剩 1 个有效 Provider → 直接自动上传，不弹界面
   - 如果有多个匹配 → 弹出选择界面，搜索框已预填关键字
   - 示例：编码组装机配方 → 检测到 `assembler` → 查配置得"组装机" → 匹配"组装机" Provider → 自动上传
4. **网络协议变更**：
   - `RequestProvidersListPacket`：新增 `ItemStack[] recipeInputs` 和 `ItemStack[] recipeOutputs` 字段
   - `ProvidersListS2CPacket`：新增 `String recipeMap` 字段

---

## 2.4.1-fix6 - 多方块检测：修复 MTEMultiBlockBase 类名路径错误

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
1. **修复 `isGTMultiBlock()` 类名路径**（根本原因）：
   - 旧代码：`Class.forName("gregtech.api.metatileentity.MTEMultiBlockBase")` — 包路径缺少 `implementations`，永远抛出 `ClassNotFoundException`，导致所有 GT 多方块机器都被识别为单方块
   - 新代码：`Class.forName("gregtech.api.metatileentity.implementations.MTEMultiBlockBase")` — 正确路径
   - 影响：修复前所有多方块机器都走 `getGTSingleBlockName()` 分支，因此只能获取主机名（如"大型蒸汽洗矿机"），无法获取运行模式名（如"洗矿机"）

---

## 2.4.1-fix5 - 多方块模式名：修复接口 default 方法反射调用失败

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
1. **多方块模式名反射修复**（`getGTMultiBlockName()` + 新增 `findMethodInHierarchy()`）：
   - 旧逻辑：`obj.getClass().getMethod("getMachineModeKey")` — 只搜索类和父类，不搜索接口 default 方法，导致 `NoSuchMethodException`
   - 根因：`getMachineModeName()` 和 `getMachineModeKey()` 都是接口 `IControllerWithOptionalFeatures` 的 default 方法，Java 8 的 `Class.getMethod()` 无法找到接口 default 方法
   - 新逻辑：新增 `findMethodInHierarchy()` 方法，BFS 遍历整个继承层次（类→父类→接口→父接口），用 `getDeclaredMethod()` 在每一层查找
   - 查找顺序：先找 `getMachineModeKey()` → 翻译 key；找不到则找 `getMachineModeName()`（某些子类直接 override 返回已翻译名）
2. **移除旧 `getGTStringMethod()` 的多方块调用**：改用新的层次结构查找方法

---

## 2.4.1-fix3 - 石英切割刀：GT前缀修正 + 多方块模式名翻译 + GuiOpenEvent拦截重命名界面

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
1. **单方块前缀修正**（`getGTSingleBlockName()`）：
   - 修正前缀列表：`"基础|进阶|高级|精密|优化|改良|增强"` → `"基础|进阶|精英|史诗|终极"`
   - 正则：`cleaned.replaceAll("^(基础|进阶|精英|史诗|终极)", "")`
2. **重命名界面拦截方案重写**（`onGuiOpen()` 新增方法）：
   - 旧方案：`PlayerInteractEvent.setCanceled(true)` — 无法阻止 AE2 的 `ToolQuartzCuttingKnife.onItemUse()` 在服务端通过 `Platform.openGUI()` 打开 `GuiRenamer`
   - 新方案：新增 `@SubscribeEvent` 监听 `net.minecraftforge.client.event.GuiOpenEvent`（Forge 客户端事件）
   - 拦截逻辑：检测 `event.gui` 类名是否为 `appeng.client.gui.implementations.GuiRenamer` → 检查玩家是否潜行 + 持有石英切割刀 → `event.setCanceled(true)` 阻止 GUI 打开
   - 新增 import：`net.minecraft.client.Minecraft`（获取客户端玩家）
   - 移除旧逻辑：`onPlayerInteract()` 中的 `event.setCanceled(true)` 已删除
3. **Javadoc 更新**：
   - `getGTSingleBlockName()` 注释前缀列表同步修正

---

## 2.4.1-fix2 - 石英切割刀：多方块名称+单方块简化+取消重命名界面

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
1. **多方块名称修正**：格式改为"运行模式名-主机名"（如"简易洗矿池-大型蒸汽洗矿机"）
   - 通过 `getMachineModeName()` 获取运行模式名（翻译 `GT5U.` 开头的 key）
   - 通过 `gt.blockmachines.<mName>.name` 获取主机名
2. **单方块名称简化**：去掉前缀和罗马数字，仅保留配方类型名
   - 去掉前缀："基础""进阶""高级""精密""优化""改良""增强"
   - 去掉尾部罗马数字：I~XII
   - 去掉尾部阿拉伯数字
   - 如"基础冲压机床 III" → "冲压机床"
3. **取消重命名界面**：`event.setCanceled(true)` + `@SubscribeEvent(priority = EventPriority.HIGHEST)` 确保最高优先级取消

---

## 2.4.1-fix1 - 石英切割刀：GT名称本地化修复

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
- `getCustomName()` 返回未本地化 key → 改用 `mName` 构造 `gt.blockmachines.<mName>.name` → `StatCollector.translateToLocal()` 翻译
- 单方块/多方块区分：`MTEMultiBlockBase.isInstance(mte)`
- 剪贴板复制 + 聊天 `(copied)` 标识

---

## 2.4.1 - 石英切割刀：剪贴板 + 单方块简化 + GT 名称修复

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
1. **剪贴板复制**：Shift+右键后名称同时写入系统剪贴板，聊天提示末尾加 `(copied)`
2. **GT 名称修复**：
   - `getCustomName()` 对 GT 单方块返回未本地化的 key（如 `extra_start_gt.recipe.wiremill...`）
   - 改为通过 MTE 的 `mName` 字段构造本地化 key `gt.blockmachines.<mName>.name`，再用 `StatCollector.translateToLocal()` 翻译
   - fallback：`IInventory.getInventoryName()` → `blockLocalizedName`
3. **单方块/多方块区分**：通过 `MTEMultiBlockBase.isInstance(mte)` 判断
   - 单方块：仅返回简短中文名（如"线材轧机"）
   - 多方块：附加配方映射名和模式名（如"蒸汽制造商 [配方名 - 模式名]"）
4. **修复重复 import**：移除 `PlayerInteractEvent` 重复导入

---

## 2.4.0 - 石英切割刀名称复制

> 作者：wztwzt（GTNH 适配）| 更新时间：2026-08-06

**新增文件：**
- `client/event/KnifeNameCopyHandler.java` — Forge `PlayerInteractEvent` 事件监听器

**修改文件：**
- `ClientProxy.java` — 添加 `KnifeNameCopyHandler.register()` 注册

**功能：**
- 持有石英切割刀（赛特斯/下界），Shift + 右键方块/AE部件 → 名称写入刀的显示名 + 聊天提示
- 取消默认右键行为（不打开重命名界面）

**目标检测：**
- AE2 线缆部件：`TileCableBus.getPart(side)` → `ICustomNameObject.getCustomName()`
- AE2 方块：`AEBaseTile` → `ICustomNameObject.getCustomName()`
- GT 机器：`ICustomNameObject.getCustomName()` + 反射获取 `getRecipeMap().unlocalizedName` + `getMachineModeName()`
- 其他方块：`block.getLocalizedName()`

**刀检测：** `className.contains("QuartzCuttingKnife")`，覆盖 `ToolCertusQuartzCuttingKnife` 和 `ToolNetherQuartzCuttingKnife`

**Bug 修复：** 事件必须注册到 `MinecraftForge.EVENT_BUS`（Forge 事件），而非 `FMLCommonHandler.instance().bus()`（FML 事件）

---

## 2.3.0 - 多人模式修复 + 名字匹配

> 作者：wztwzt | 更新时间：2026-08-06

**核心问题：** 2.2.x 的 `lastUploadedProviderId` 是 `UploadPatternPacket.Handler` 的 static 字段，单人模式共享 JVM 可用，多人模式下客户端和服务端是不同 JVM，客户端读到的值始终为 0。

**新增文件：**
- `client/ClientState.java` — 客户端状态持有类，存储 `lastProviderName`（String）和 `lastProviderId`（long）

**修改文件：**
- `ProvidersListS2CPacket.java` — 客户端 Handler 重写自动上传策略：
  1. 策略1：只有一个有空槽的 Provider → 直接上传
  2. 策略2：`ClientState.lastProviderName` 匹配 → 找同名且有空槽的 Provider → 唯一匹配则上传
  3. 都不满足 → 打开选择 GUI
- `GuiProviderSelect.java` — `handleSelect()` 中用户手动选择后调用 `ClientState.set(name, id)` 记住名字和 ID
- `UploadPatternPacket.java` — 移除 `Handler.lastUploadedProviderId` static 字段（服务端不再维护状态）
- `RecallPatternPacket.java` — 撤回成功后调用 `ClientState.clear()` 清除客户端记录
- `GuiUploadButtonHandler.java` — 撤回按钮读取 `ClientState.lastProviderId`（原 `UploadPatternPacket.Handler.lastUploadedProviderId`）

---

## 2.2.1 - 撤回清除记录

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `RecallPatternPacket.java` — 撤回成功后设置 `UploadPatternPacket.Handler.lastUploadedProviderId = 0`

**目的：** 误上传到错误 Provider 后，撤回 → 清除记录 → 下次弹选择框重新选

---

## 2.2.0 - 智能自动上传

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `ProvidersListS2CPacket.java` — 客户端 Handler 新增策略2：检查 `lastUploadedProviderId` 是否在有效列表中，是则直接上传

**逻辑：** `validIds.size() == 1` → 直接上传；`validIds.contains(lastId)` → 直接上传；否则弹 GUI

---

## 2.1.0 - 自动上传 + 撤回按钮

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `network/RecallPatternPacket.java` — 撤回协议，服务端从 Provider 反向扫描移除最后一个编码样板，放回输出槽
- `network/ProvidersListS2CPacket.java` — 服务端→客户端 Provider 列表推送协议

**修改文件：**
- `client/event/GuiUploadButtonHandler.java` — 新增撤回按钮 `BUTTON_RECALL_ID=998`（`←`），编码按钮右侧
- `network/RequestProvidersListPacket.java` — 服务端扫描网格所有 `ICraftingProvider`，使用 `ICustomNameObject` 解析名称
- `network/ModNetwork.java` — 注册 `ProvidersListS2CPacket`（discriminator 1, CLIENT）和 `RecallPatternPacket`（discriminator 3, SERVER）

**撤回逻辑：**
1. 服务端检查输出槽是否为空（只能在空时撤回）
2. 获取 Provider 的 `IInventory`，从后往前扫描找到最后一个编码样板
3. 移除并放入输出槽

**Bug 修复：** MC 1.7.10 的 `Container.inventorySlots` 不支持 for-each（编译器报错），移除客户端对该字段的遍历

---

## 2.0.1 - 依赖对齐 + 兼容性修复

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `dependencies.gradle` — AE2 从 `rv3-beta-691` 升级到 `rv3-beta-977-GTNH`，ae2fc 从 `1.4.115` 升级到 `1.5.88-gtnh`
- `network/RequestProvidersListPacket.java` — `resolveTerminal()` 从 `(PartPatternTerm) term.getPatternTerminal()` 改为 `(IActionHost) term.getPatternTerminal()`（修复 `NoSuchMethodError`）
- `network/UploadPatternPacket.java` — 同上修复 `resolveTerminal()`

**移除文件/引用：**
- 移除所有 `GuiFluidPatternTerminal`、`GuiFluidPatternTerminalEx`、`ContainerFluidPatternTerminal`、`ContainerFluidPatternTerminalEx`、`FCContainerEncodeTerminal`、`IItemPatternTerminal` 引用（修复 `NoClassDefFoundError`）

**新增功能：**
- `isSupportedPattern()` 新增 `encodedUltimatePattern()` 检查（支持终极编码样板）
- `resolveProviderName()` 优先检查 `ICustomNameObject`（GregTech 适配器兼容）

---

## 2.0.0 - 网络协议重构

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `network/ProvidersListS2CPacket.java` — 服务端扫描网格 Provider，发送 id/name/emptySlots 列表给客户端
- `client/gui/GuiProviderSelect.java` — 带搜索/翻页/映射功能的 Provider 选择 GUI
- `util/RecipeNameUtil.java` — 配方名到 Provider 名的映射工具

**修改文件：**
- `network/ModNetwork.java` — 注册 `ProvidersListS2CPacket`（discriminator 1）

**移除：** 所有 debug `System.out.println` 输出

---

## 1.2.4 - 兼容性修复

> 作者：wztwzt | 更新时间：2026-08-06

- 修复与 GTNH 2.9.0 的兼容性问题

---

## 1.2.0 - 初始版本

> 作者：wztwzt | 更新时间：2026-08-06

- 基础 AE2 样板上传功能
- 支持标准编码样板和 ae2fc 流体编码样板

---

## 附：F 功能规划记录（样板 + 接口双页面二合一终端）

> 状态：🕐 规划中 | 作者：wztwzt | 记录时间：2026-08-15

### 功能构想

将**样板编码**与**接口（Interface）管理**合并到同一个终端 GUI，通过页面切换（"样板页" / "接口页"）在一个窗口内完成两类操作，避免玩家在样板终端与接口终端之间反复切换。

### 历史决策

- **3.0.0 调研结论**：AE2 rv3-beta-977-GTNH 已原生实现接口终端（PartInterfaceTerminal / WirelessInterfaceTerminalGuiObject），ae2fc 1.5.88 自带 ItemWirelessInterfaceTerminal 可直接打开原生接口终端——该能力已被原生覆盖。当时放弃约 6000 行完整移植，改为 NEI 配方界面 AE 角标等增量增强。
- **本记录目的**：内容单独保留，作为后续重新发布 F 功能的任务起点。重新开发前需先与使用者对齐页面布局、"写样板 → 自动填机器名"联动、与原生接口终端的差异定位等需求。

### 重新开发时需确认的点

1. 双页面切换的交互方式（Tab / 侧边栏 / 按钮）
2. 样板页与接口页是否需要同步显示同一网络的数据
3. 与 AE2 977 原生接口终端的功能差异（避免重复造轮子）
4. 无线版（复用无线终端）是否纳入
