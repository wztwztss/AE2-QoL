## 2026-08-23 - 全代码库深度审查报告（仅登记问题，无代码变更）

> 作者：wztwzt | 审查时间：2026-08-23 | 版本基线：3.6.0
> 范围：`src/main/java` 全部约 120 个 Java 源文件 + `mixins.ae2_qof.json`；重点为**前十三个功能的实现完整性**、服务端 tick 与 Mixin 注入正确性、客户端渲染热路径性能、网络包安全。
> 方法：三路并行静态审查（① 客户端渲染/NEI 叠加层；② 服务端 Mixin/智能倍增/强化 IO 端口；③ 网络包安全 + 十三功能逐项核对），关键结论均经人工二次复核源码确认。

### 一、十三功能核对结论

**13 项功能全部存在完整代码路径（GUI 按钮 / Mixin / 网络包 / 服务端处理器四环齐备），无缺失项：**

| # | 功能 | 结论 | 关键证据链 |
|---|---|---|---|
| 1 | NEI 样板上传/撤回/交换 | ✅ 完整 | `GuiUploadButtonHandler` 四按钮 + `UploadPatternPacket`/`RecallPatternPacket`/`SwapPatternPacket` + 三策略选择 `ProvidersListS2CPacket` |
| 2 | NEI 取物品/合成下单 | ✅ 完整 | `MixinPanelWidgetClick` + `ExtractItemPacket`（SIMULATE 预检+归还防丢物）/`RequestCraftingPacket` 双路径 |
| 3 | NEI tooltip 存量/可合成 | ✅ 完整 | `NetworkTooltipHandler` + `NetworkInventoryCache`（按 item+damage 键，忽略普通 NBT 差异） |
| 4 | NEI 书签数量叠加 | ✅ 完整 | `MixinPanelWidgetDraw`（PanelWidget.draw TAIL）+ `NetworkInventoryDrawHandler` |
| 5 | 合成完成通知 | ✅ 完整（含缺陷 #46） | `submitJob` 捕获发起者 + `completeJob` 校验密钥后发包 + `CraftingNotificationOverlay` 绘制 |
| 6 | 合成重新规划 Replan | ✅ 完整 | `MixinGuiCraftConfirm` + `ReplanPacket` + `Replanner`（对已完成模拟的 job 重提 beginCraftingJob） |
| 7 | 强化 IO 端口 | ✅ 完整 | `TileExIOPort` + `MixinTileIOPort` @ModifyVariable（long 溢出钳制 + 热加载） |
| 8 | 无限水岩浆磁盘 | ✅ 完整 | `ItemInfinityWaterLavaCell`（AEBaseInfiniteCell + 配方 `"wbw"," "," "` 与 README 一致） |
| 9 | 无线收发器+连接器 | ✅ 完整（含缺陷 #53） | wireless/ 整包；「跨维度」仅收发器链路成立，连接器绑定实际拒绝跨维度 |
| 10 | 石英切割刀复制名称 | ✅ 完整 | `KnifeNameCopyHandler`（HIGHEST 右键事件 + GT 单方块/多方块名解析 + 剪贴板） |
| 11 | F 键搜索填充 | ✅ 完整 | `KeyInputHandler`（NEI IContainerInputHandler，appeng./ae2fc GUI 判定 + 反射定位搜索框） |
| 12 | NEI 叠加层开关 | ⚠️ 含缺陷 #48 | `CommandOverlay` 注册位置导致专用服务器无命令；OV 多人权威归属混乱 |
| 13 | 智能倍增 | ✅ 完整（含缺陷 #44/#51/#58） | 安全边界全对齐 README；注入接管/批量记账/溢出钳制均在 |

**Mixin 注入正确性评估（对照 rv3-beta-977 dev jar 逐一实证）**：@Shadow 字段与方法、@Inject 目标（`executeCrafting` HEAD+cancellable、`submitJob` RETURN、`handleCraftBranchFailure`/`completeJob` TAIL、`DualityInterface.writeToNBT/readFromNBT` TAIL、`ContainerInterface.<init>` RETURN）、反射目标（`TaskProgress.value`/`consumeCraftSession()`/`finalOutput` 私有内部类/`CraftingCpuDiagnostics`/静态 `getServerTick()` 等）**全部命中，零 @Overwrite/@Redirect**；智能路径反射失败与执行异常均有安全降级回原版。HEAD 注入 + cancel 替代 @Overwrite 保留了原方法字节码骨架，PH `MixinInstantComplete` 等其它 mod 注入不受破坏。

### 二、新增风险登记摘要（#44-#58，详见下方登记表）

| 编号 | 摘要 | 等级 |
|---|---|---|
| #44 | 智能倍增 PH 介质 `pushPatternMulti` 返回 0 回退单发时按 N 轮记账（实际仅交 1 轮材料）→ 少产出 + 白扣功率 + 任务假完成 | 🔴 |
| #46 | `CraftingCompletePacket`(S2C) 未切客户端主线程，Netty IO 线程写非线程安全队列 | 🔴 |
| #45 | `RequestProvidersListPacket` 数组长度无上界 → 恶意包 OOM DoS（另有 3 个 S2C 同类） | 🟡 |
| #47 | 无线高亮开关服务端读客户端静态字段 → 专用服务器只能开不能关 | 🟡 |
| #48 | `/apu-overlay` 专用服务器不可用 + OV 开关多人权威归属混乱 | 🟡 |
| #49 | `NetworkInventoryCache.invalidate()` 从未被调用 → 缓存永不过期显示陈旧数据 | 🟡 |
| #50 | 无线全局频道无归属权，任何玩家可删他人频道 | 🟡 |
| #51 | 智能倍增容量二分固定 31 轮 do-while 每次推送重跑 + 全网电力探测 O(P) | 🟢 |
| #52 | 渲染热路径浪费：每帧 new RenderItem / tooltip 流体识别×3 遍×每槽位 / 格式化无 memo | 🟢 |
| #53 | README 功能 9「跨维度」与实现不符（连接器拒绝跨维度死分支） | 🟢 |
| #54 | 8 个文件残留 `System.out.println` 调试日志 | 🟢 |
| #55 | 死代码遗留（lastProviderName/getLastUpdateTick/BUTTON_HALVE_ID/Replanner 等） | 🟢 |
| #56 | `docs/MOD_MAP.md` 为空模板，违反文档驱动规范 | 🟢 |
| #57 | `ProvidersListS2CPacket` 无应用层尺寸预算，超大网络静默失效 | 🟢 |
| #58 | 智能倍增部分提前 return 分支疑似遗漏 `parallelismProvider` 回写（静态审查发现，待复核） | 🟢 |

### 三、修复优先级建议

- **P0（发布前必修）**：#44 记账缺陷、#46 线程归队
- **P1**：#45/#47/#48/#49/#50（安全与专用服正确性）
- **P2**：#51/#52（大网络性能）
- **P3（卫生整改）**：#53/#54/#55/#56/#57/#58

---

## 3.6.0 - 二合一终端面板体验升级批次

> 作者：wztwzt | 更新时间：2026-08-22

### 新增：样板回读（二次编辑）

- 把编码好的样板放回 OUT 槽，自动解码回填面板格子（输入/输出/模式/替代开关一并恢复），无需重新拖放配方
- 数据链路：`ICraftingPatternDetails` 主路径 + GT 终极样板 NBT（`in`/`out` AE2 栈格式）兜底读取
- 流体输入还原为 GT 展示物品（tooltip 带温度/状态），与 NEI 填充时的格子表示一致；`ItemFluidDrop` 包装同样识别
- 样板内 `apu:recipeMap` 保留，回读后再编码/上传仍可识别供应器映射

### 新增：编辑快照持久化

- 关闭终端 GUI 时自动保存面板全部格子（合成 3×3 + 扩展输入 32 + 输出 32）与合成模式到方块 NBT；重新打开完整恢复
- 合成模式经 `@GuiSync(0)` 同步客户端，GUI 背景与按钮布局即时正确
- 存档兼容：纯新增 NBT 字段（`apuSavedGrid` / `apuSavedMode`），旧存档无字段按默认空面板处理

### 新增：PH 编程工具箱 MK.II 适配

- 二合一终端 NEI 转写时，自动把配方中 `stackSize==0` 的不消耗催化剂替换为对应编程电路；工具箱处于兜底模式且配方无催化剂时追加归零电路
- 反射调用 PH API（`holding()` / `addEmptyProgCiruit()` / `ItemProgrammingCircuit.wrap`），PH 未安装时自动跳过、零影响
- 补齐与原生样板终端的能力差异：原生 hook 位于 NEE `GregTech5RecipeProcessor`，二合一终端走自研直通链路绕过了该注入点

### 新增：装配矩阵上传按钮（AM）

- 面板新增 `AM` 按钮（仅合成模式且 GTNL 已安装时显示）：点击将编码好的样板上传至网络中 GTNL 装配矩阵样板库
- 行为对齐 GTNL 原生样板终端按钮：OUT 槽为空先编码；仅接受普通合成样板；矩阵已有相同输出时提示并返还空白样板；否则插入第一个有空位的矩阵并清空 OUT 槽
- 全程反射访问 GTNL，模组未安装时按钮隐藏

### 修复：流体解析严格匹配（「气态氧变液态氧」根因）

- 根因：`findFluidByName()` 旧模糊匹配用 `contains` 双向包含——`"liquidoxygen".contains("oxygen")` 为真，HashMap 无序遍历导致气态氧被随机解析成液态氧写入样板 NBT
- 修复：改为精确匹配 → 完全相等 → 唯一前缀匹配 → 多候选放弃（宁可不解析也不写错流体）
- 附带：识别 ae2fc `ItemFluidDrop` 物品（NBT `Fluid` 键）参与流体解析，重编码不再退化为普通物品序列化

### 修复：数量编辑与物品消失

- 移除数量编辑 999 硬上限；超大输入 clamp 到 `Integer.MAX_VALUE` 防溢出
- **输出格禁止中键编辑**：编辑后 `getAndUpdateOutput()` 配方重算会覆盖/清空输出格（物品消失根因之一），客户端+服务端双重拦截
- `setStackSize` 后补 `slot.onSlotChanged()`
- 中键编辑覆盖层移至背包区域居中显示，交互改为模态弹窗（点击覆盖层以外关闭并吞掉点击，防止误触下方槽位）；标题「编辑数量:」/「重命名:」
- Shift+点加号=乘法、Shift+点减号=除法（右键减号同为除法），独立 ÷ 按钮移除

### 修复：Shift+滚轮 OreDict 替换循环推进

- 原实现向上滚永远跳第 0 个候选、向下滚跳最后一个；改为基于当前物品在候选序列中的索引循环推进
- 新增候选查询包（`RequestReplaceCandidatesPacket` / `ReplaceCandidatesPacket`）供预览扩展使用

### 其他

- `TileMergedTerminal` 实现 `IPowerChannelState`（对齐 TileExIOPort 模式），WAILA 等可正确读取供电/频道状态
- 面板按钮布局调整：上传 `↑`/召回 `←` 贴紧编码按钮两侧，交换 `⇄` 移入处理模式右上 AE 按钮区（合成模式隐藏），`OV` 固定右下

---
# AE2 QoL - Changelog

> 当前版本：3.6.0 | 适配：GTNH 2.9.0-beta-1 | 依赖：AE2 `rv3-beta-977-GTNH`，ae2fc `1.5.88-gtnh`

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
| **智能倍增（Smart Doubling）**：ME 接口/样板输入机复选框 + CPU 一次性推送 N 轮（默认不限 0=不限，可配） | `api/ISmartDoublingMedium` + `mixin/ae/MixinDualityInterface` + `mixin/ae/MixinCraftingCPUCluster` + `mixin/ae/MixinGuiInterface`/`MixinContainerInterface` + `mixin/gt/MixinMTEHatchInputBus` | ✅ 可用（3.2.0；3.3.2 兼容 GTNotLeisure 超级接口；3.3.3 支持 GT/SNL/PH 样板输入机；3.3.5 修复实测失效；3.3.6 默认 0=不限；3.3.7 批量记账/功率 O(1) 修复大订单卡死） |
| 统一配置文件 `settings.json` + 热加载 + OP 命令 `/ae2qof reload` + 游戏内配置 GUI（含范围显示 + 名字映射热编辑） | `Config` + `CommandAe2QoL` + `client/gui/ConfigGuiFactory`/`GuiConfigScreen` + `network/ConfigSetPacket`/`ConfigUpdatePacket` | ✅ 可用（3.3.0；3.3.6 新增 GUI 页面；3.3.7 范围显示 + 映射编辑） |
| **F：样板 + 接口二合一终端**（独立有线方块） | `merged/GuiMergedTerminal` + `ContainerMergedTerminal` + `PatternContainer` + `BlockMergedTerminal`/`TileMergedTerminal` + `client/event/MergedTerminalPanelHandler` + `client/gui/MergedPanelLayout` + `network/MergedTerminalActionPacket`/`MergedTerminalResultPacket` + `api/IMergedPatternTerminal` | ✅ 可用（3.4.0 起；3.5.0 改为独立有线方块 + 原生 AE2Things 风格面板，移除两个 mixin；3.5.1 修复 openContext NPE 崩溃/NEI 返回错位/处理样板改终极样板/网络拉空白样板/NEI「+」与 `↓` 读回/隐藏 NEI 面板）；**3.6.0 新增：样板回读二次编辑、编辑快照持久化、PH 编程工具箱适配、装配矩阵上传按钮（AM）、流体解析严格匹配修复、数量上限移除与输出格禁编、覆盖层模态化 |

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
| 19 | 流体误判：`FluidRegistry.getFluid(itemDamage)` 把 damage 命中流体 ID 的物品（damage=0→水）误判为流体 → 随机物品显示 mB 量 | `client/NetworkInventoryCache.java`（3.0.2 引入，3.1.2 修复） | 🟡 | ✅ 已修复（3.1.2 改类名+NBT 识别，见 3.1.2 条目） |
| 20 | 智能倍增 `@Overwrite executeCrafting` 全量重写 CPU 主循环：移植偏差导致合成丢物/倍率错账；N× 放大 long 溢出 | `mixin/ae/MixinCraftingCPUCluster.java` | 🔴 | ✅ 已修复（3.2.0 逐行移植 + N==1 走原版路径；反射失败安全降级；3.3.5 改 GTLCore 式单次推送，功率/原料不足按可提取轮数钳制 N，异常回退原版） |
| 21 | 智能倍增 `pushPattern` 只返回成功布尔，无实际轮数反馈：部分提取/缓冲时 CPU 与接口记账不一致 → 超产或漏产 | `mixin/ae/MixinCraftingCPUCluster.java` + `MixinDualityInterface.getMaxMultiplier` | 🟡 | ✅ 已修复（3.3.5 GT/PH 按实际推送轮数记账；PH 走 `pushPatternMulti` 返回实际轮数，GT 单次 `pushPattern` 收 N× 一次记账 N 轮） |
| 22 | 配置文件热加载：`settings.json` 语法错误 / 值越界 / 编辑中途被读取 → 解析失败或字段不一致 | `Config.reload` + `ensureFresh`（3.3.0 引入） | 🟢 | ⏳ 已兜底（解析失败保留上次生效值；数值越界 clamp 回默认；mtime 校验限流 1 秒一次） |
| 23 | mixin 冲突：`@Overwrite executeCrafting` 整体替换方法体 → 其它模组（ProgrammableHatches `MixinInstantComplete`）对同一方法的 `@Inject/INVOKE` 找不到注入点崩溃 | `mixin/ae/MixinCraftingCPUCluster.java`（3.2.0 引入 @Overwrite） | 🔴 | ✅ 已修复（3.3.1 改 `@Inject(HEAD)+cancel`，保留原方法字节码结构；仅在存在智能倍增任务时接管 tick） |
| 24 | `@GuiSync(19)` 与 GTNotLeisure `ContainerSuperInterface` 的 `@GuiSync(19) sidelessMode` 同 id 冲突 → `DataSynchronization.collectFields` 遍历类层级发现重复 key 抛 `IllegalStateException` 崩溃 | `mixin/ae/MixinContainerInterface.java`（3.2.0 引入 @GuiSync(19)） | 🔴 | ✅ 已修复（3.3.2 改 `@GuiSync(30)`，高于 AE2 链内 18 且不与 GTNL 19 冲突） |
| 25 | `MixinMTEHatchInputBus` 应用于 GT 输入仓全族（普通总线/补货输入仓/样板输入仓），注入的 NBT 保存/加载方法对全类族生效 | `mixin/gt/MixinMTEHatchInputBus.java`（3.3.3 引入） | 🟢 | ⏳ 已兜底（`instanceof ICraftingProvider` 门控，仅样板输入机启用；开关默认关闭；NBT key `ae2qolSmartDoubling` 带独立前缀不冲突） |
| 26 | GT/PH/GTNL ModularUI 与 GUI 按钮注入点偏移：GTNL 超级二合一接口方块/面板两形态布局相差 18px；PH 面板布局不同 → 按钮不显示或遮住翻页 | `mixin/gt/MixinMTEHatchCraftingInputMEGui.java` + `mixin/gt/MixinDualInputHatchUI.java` + `mixin/ae/MixinGuiSuperDualInterface.java`（3.3.3 引入） | 🟢 | ⏳ 已兜底（GTNL 按 host 形态动态计算偏移；注入点经 javap 逐一验证存在；配置级 `required=false`，注入失败仅警告不崩溃） |
| 27 | 自动上传把 GT/PH 样板输入机当普通 `IInventory`（GT `IMetaTileEntity extends ISidedInventory`），通用库存排样板槽之前 → 编码样板误投进原料缓存槽，多方块收不到配方 | `network/UploadPatternPacket.java` + `network/RequestProvidersListPacket.java` + `network/RecallPatternPacket.java`（3.3.4 修复） | 🟢 | ✅ 已修复（3.3.4 改用 `IInterfaceViewable.getPatterns()` 优先定位专属样板槽；GT/PH 写入后 `markDirty` 持久化，`setInventorySlotContents` 触发机器内部重建与网络同步） |
| 28 | 智能倍增（GT/PH 样板输入机）实测失效：功率门槛 `sum*effectiveN` 不足即 `continue` 跳过介质（无回退）→ CPU 永不推送；`getExtractItems` 严格全量匹配导致 N 静默降为 1 → 勾选后完全无效 | `mixin/ae/MixinCraftingCPUCluster.java`（3.3.3 引入） | 🔴 | ✅ 已修复（3.3.5 改 GTLCore 式：原料不足按 SIMULATE 可提取轮数钳制 N、功率不足逐轮下调 N、N==1 走原版路径、`onExecuteCrafting` try/catch 异常回退原版不拖死 CPU） |
| 29 | 游戏内配置 C2S 包无权限校验：任何玩家可改 `settings.json`（改 `io_port_rate` 刷倍率/改倍增上限） | `network/ConfigSetPacket.java`（3.3.6 引入） | 🟡 | ✅ 已修复（服务端 `canCommandSenderUseCommand(2, "ae2qof")` OP 校验 + key/范围白名单校验，非 OP 直接丢弃） |
| 30 | 智能倍增 0=不限后 `N` 可能等于剩余全部轮数：`remaining` 为 long 但推送侧用 int，>2^31 时 `(int)` 强转溢出为负 → 死循环/错账 | `mixin/ae/MixinCraftingCPUCluster.java` `ae2qol$smartMultiplier`（3.3.6 引入） | 🔴 | ✅ 已修复（`cap = (int) Math.min(cap, Math.min(remaining, Integer.MAX_VALUE))` 封顶；GT 仓 `getMaxMultiplier` 返回 `Integer.MAX_VALUE`，`roundSize*mid` 为 long ≤2^62 不溢出） |
| 31 | 智能倍增 0=不限且下单 10000+ 轮时 `ae2qol$accountSmartPush` 逐轮记账：大 N 下单有可见卡顿 | `mixin/ae/MixinCraftingCPUCluster.java`（3.3.6 行为变更） | 🟢 | ✅ 已修复（3.3.7 批量记账 + 功率钳制 O(1) + int 溢出钳制） |
| 32 | 批量记账后 `waitingFor` 单栈数量可超 `Integer.MAX_VALUE`（long 承载，语义等价） | `mixin/ae/MixinCraftingCPUCluster.java`（3.3.7） | 🟢 | ⏳ 已兜底（long 算术，无溢出；AE 内部以 long 承载堆叠） |
| 33 | O(1) 功率钳制与逐轮递减存在 <0.01 AE 的舍入差 | `mixin/ae/MixinCraftingCPUCluster.java`（3.3.7） | 🟢 | ⏳ 已兜底（保留原版 `requiredPower - 0.01` 兜底判断，行为一致） |
| 34 | `ClientState.removeRememberedProvider` 为新增客户端方法，仅本地生效 | `client/ClientState.java`（3.3.7） | 🟢 | ✅（映射本就仅客户端使用） |
| 35 | 配置页两页切换 `initGui` 重建控件，字段值需保留 | `client/gui/GuiConfigScreen.java`（3.3.7） | 🟢 | ✅ 已核对（TextField 对象复用，跨页不清空） |
| 36 | 二合一终端面板混入依赖 `@Shadow` MCP 名运行时解析（GTNH 去混淆环境正常；若未来 SRG 重映射则面板失效） | `mixin/ae/MixinGuiInterfaceTerminal.java`（3.4.0） | 🟢 | ✅ 已移除（3.5.0 删除该 mixin，改为直接继承 `GuiInterfaceTerminal`，不再依赖 `@Shadow`） |
| 37 | `InterfaceTerminalList` 为私有内部类 → 面板按钮/槽点击需绕过 masterList 手动分发；面板区域与视图口右缘重叠 | `merged/GuiMergedTerminal.java`（3.4.0 起） | 🟢 | ✅ 已兜底（3.5.0 直接 override `mouseClicked`，`isInPanel` 判定优先于列表项；按钮 id 940-953 高于列表项；重叠区面板优先，用户反馈后调整） |
| 38 | 二合一终端上传：编码槽为空或 NBT 无 `apu:recipeMap` 且无后备时静默返回，无提示 | `client/event/MergedTerminalPanelHandler.java` `handleUpload`（3.4.0） | 🟢 | ⏳ 已兜底（不抛异常；编码后上传走 recipeMap 分支；升级走 ICraftingPatternDetails 降级） |
| 39 | NEI 配方填充（`MixinGuiRecipe`）依赖 NEI 配方页 GUI 内部结构，NEI 版本升级可能失效（仅影响 `N` 按钮） | `mixin/nei/MixinGuiRecipe.java`（3.4.0） | 🟡 | ⏳ 已兜底（`required=false` + try/catch，失败仅 N 按钮无效，其余面板功能正常） |
| 40 | 二合一终端编码/清空/×2/模式包无服务端权限校验，但仅作用于玩家自身打开的容器 | `network/MergedTerminalActionPacket.java`（3.4.0） | 🟢 | ⏳ 已兜底（操作限于玩家 own container，无网络侧越权面） |
| 41 | 面板悬垂区绘制采用 `xSize=1000` 放大法：槽位命中依赖 `GuiContainer.getSlotAtPosition` 使用 `guiLeft/guiTop` 字段（不随 xSize 重算），已验证不破坏槽点击 | `merged/GuiMergedTerminal.java` `drawScreen`（3.5.0） | 🟢 | ✅ 已兜底（javap 核对 `func_146978_c`/`getSlotAtPosition` 用字段坐标；`initGui` 按 xSize=209 计算 guiLeft，命中逻辑不受影响） |
| 42 | 面板按钮/滚动条/页码为客户端静态字段，仅随 GUI 打开重置；多容器/多窗口切换时由每帧 `reposition` 从客户端容器刷新覆盖 | `client/event/MergedTerminalPanelHandler.java`（3.5.0） | 🟢 | ✅ 已兜底（drawFG 每帧以 `pc.isCraftingMode()/isInverted()/getActivePage()` 重刷静态，状态不串窗口） |
| 43 | `GuiTabButton` 图标渲染需 `RenderItem`：反射读 `GuiScreen.itemRender`（protected static），失败回退 `new RenderItem()` | `client/event/MergedTerminalPanelHandler.java` `getRenderItem`（3.5.0） | 🟢 | ✅ 已兜底（try/catch + 回退，反射失败仅 tab 图标缺失，不影响按钮功能） |
| 44 | 智能倍增 PH 介质记账缺陷：`useMulti && effectiveN>1` 时按 **1 轮量**提取材料（target 不乘 N），若 `pushPatternMulti` 返回 `accepted==0`（介质忙/缓冲满）则落到下方 `pushPattern` 单发回退分支；该分支因 `effectiveN>1` 走 GT 倍增记账 → 实际只交付 1 轮材料却**扣 N 轮功率、executedTasks+=N、taskValue-=N** → 合成少产出 N-1 轮、白扣功率、任务提前假完成（材料未丢，留在网络存储，但订单数量错误） | `mixin/ae/MixinCraftingCPUCluster.java:764-839`（回退分支判定应为 `!useMulti && effectiveN>1`；useMulti 回退时须走原版逐轮记账） | 🔴 | ❌ 未修复（2026-08-23 审查发现） |
| 45 | 网络包 OOM DoS：`readItemStackArray` 直接 `new ItemStack[buf.readInt()]` 无上界钳制——恶意 C2S 包 len=2^31-1 触发瞬时巨量分配（分配先于读取发生，外层 catch(Throwable) 接不住已打爆的堆压力）；同类 S2C 预分配 `ArrayList<>(readInt())` 见 `ProvidersListS2CPacket` / `WirelessHighlightPacket` / `WirelessChannelSyncPacket`（低危：服务端→自己客户端）。修复：四处解码长度钳制（镜像 `MergedTerminalActionPacket` 超界归零风格）——配方数组 ≤64、供应器/高亮列表 ≤1024、频道列表 ≤256，超界按空容器处理，合法包不受影响 | `network/RequestProvidersListPacket.java:107-114` 等 4 文件 | 🟡 | ✅ 已修复（2026-08-23） |
| 46 | `CraftingCompletePacket`(S2C) Handler 未切客户端主线程：Netty IO 线程直接向非线程安全 `ArrayDeque`（CraftingNotificationOverlay.events）add，渲染线程并发 poll/draw → 数据竞争偶发崩溃/渲染异常；为全部 S2C 包中唯一漏归队者（其余均已 `func_152344_a`）。修复：Handler 业务逻辑包进 `mc.func_152344_a` 归队主线程，镜像项目内其余 S2C 包既有模式 | `network/CraftingCompletePacket.java:56-65` | 🔴 | ✅ 已修复（2026-08-23） |
| 47 | 无线高亮开关失效于专用服务器：`handleToggleHighlight` 在**服务端**读客户端静态字段 `ClientState.highlightEnabled`（仅客户端 WirelessHighlightPacket.Handler 写入），专用 JVM 恒 false → 高亮只能开不能关（单机同 JVM 共享静态字段才碰巧正常）。修复：目标状态由包参数携带——客户端发送 `ACTION_TOGGLE_HIGHLIGHT` 时传 `!ClientState.highlightEnabled`（modeValue 字段在该动作中原本空闲），服务端改用 `msg.modeValue`，无状态、专用服/单机行为一致 | `network/WirelessActionPacket.java:266` + `wireless/gui/GuiWireless.java:339` | 🟡 | ✅ 已修复（2026-08-23） |
| 48 | NEI 叠加层开关双问题：① `/apu-overlay` 注册于 `ClientProxy.serverStarting` → 仅单人/局域网主机存在命令，**专用服务器不可用**（只能用 OV 按钮）；② 多人时 GUI OV 按钮写本地 settings.json，会被登录时 `ConfigUpdatePacket` 的服务端值覆盖 → 开关权威归属混乱（本地 vs 服务端二义性）。修复（按决策仅修②+README 注明①）：NEI 叠加层为纯客户端渲染功能，登录同步不再覆盖客户端本地 `nei_overlay_enabled`——`Config.applyAll` 去掉 overlay 参数（包字段保留作协议兼容），README 注明 `/apu-overlay` 仅单机/局域网可用、专用服用 OV 按钮、开关为纯客户端本地设置 | `network/ConfigUpdatePacket.java` + `Config.java applyAll` + `README.md` | 🟡 | ✅ 已修复（2026-08-23） |
| 49 | 库存缓存永不过期：`NetworkInventoryCache.invalidate()` 定义后**从未被任何代码调用**，终端关闭后缓存不失效 → tooltip/书签角标可能长期显示陈旧的存量与可合成状态。曾接入 `onGuiClosed` 清缓存，**实测回退**：用户依赖"打开一次终端后缓存持续显示"的使用模式（数据源仅终端 GUI 打开期间的 postUpdate 推送），关闭即清直接破坏该体验。结论：维持缓存不过期（3.6.0 原行为），`invalidate()` 保留供未来"佩戴实时推送"功能使用 | `client/NetworkInventoryCache.java:68` + `mixin/nei/MixinGuiMEMonitorable.java` | 🟡 | ⏪ 已回退，维持原行为（2026-08-23 用户反馈） |
| 50 | 无线全局频道无归属权：任何玩家打开任意收发器 GUI 即可 `ACTION_REMOVE_CHANNEL` 删除他人全局频道并强制拆除发送端连接（共享服务器干扰向量）；频道应有创建者归属或 OP 保护 | `network/WirelessActionPacket.java` `handleRemoveChannel` | 🟡 | ❌ 未修复（2026-08-23 审查发现） |
| 51 | 智能倍增探测开销：ME 接口容量二分探测固定 ~31 轮 do-while 且每次推送重跑；全网电力探测 `extractAEPower(MAX_VALUE, SIMULATE)` 为 O(P) 网格遍历；大网络高频推送时有放大效应。修复：① 容量探测改"指数扩张 + 区间二分"——先倍增 probe 找失败点再在小区间二分，常见小容量场景从 31 次降到个位数次 `simulateAddStack`（顺带修正原实现未实测 1 轮的边界，机器满仓时准确回退逐轮）；② 功率探测查询上界改为 `sum × min(effectiveN, 4096)`——AE2 `simulateExtract` 凑够即停，有限查询通常 O(1)，电量充足时视为"电不是瓶颈"，单次推送封顶 4096 轮、剩余下一 tick 继续 | `mixin/ae/MixinDualityInterface.java:122-133` + `mixin/ae/MixinCraftingCPUCluster.java:690-706` | 🟢 | ✅ 已修复（2026-08-23） |
| 52 | 客户端渲染热路径浪费（叠加层全开 + 大网络时有可感知 GC 抖动）。修复 ①②③：① 合成通知横幅 `new RenderItem()` 每帧分配 → 静态缓存复用（dev 字段名 itemRender 与 SRG 名 field_146296_j 都尝试——原 MergedTerminalPanelHandler 同类实现在生产环境因只试 dev 名实际仍每帧 new）；② tooltip 路径同一 ItemStack 流体识别/NBT 解析 3 遍 → `NetworkInventoryCache.query()` 单次合并返回 count/craftable/fluid；③ `CountFormatter.format` 加单槽记忆化（仅渲染线程调用，无并发）。④ 无线高亮 Tessellator 批次合并**暂缓**：高亮方块数量通常个位数、独立提交开销可忽略，方案收益不抵复杂度 | `client/render/CraftingNotificationOverlay.java:103` + `client/nei/NetworkTooltipHandler.java` + `util/CountFormatter.java` + `client/NetworkInventoryCache.java` | 🟢 | ✅ 已修复（④暂缓）（2026-08-23） |
| 53 | README 功能 9 称无线连接器「支持跨维度」，审查判定实现拒绝跨维度绑定。**复核结论：审查误判，跨维度功能实际已完整支持，无需修改**——① L119 拒绝分支不可达（玩家只能右键自己所在维度的方块，te.getWorldObj() 与 player.worldObj 恒同维度）；② 各维度分别绑定后由 `WirelessBlockLinkManager.processAll` 按 `link.dimension` 用 `DimensionManager.getWorld` 跨维度取 World 建链；③ AE2 `GridConnection` 构造无维度校验（与收发器对同款机制）；④ 存档经主世界 `WirelessWorldData` 持久化 + `restoreFromWorldData` 启动恢复。已给死分支加注释标记 | `wireless/ItemWirelessConnector.java:119` + `README.md` 功能 9 | 🟢 | ✅ 已复核无问题（2026-08-23） |
| 54 | 调试日志残留：8 个文件仍有 `System.out.println`（ClientState / PatternContainer / MixinDefaultOverlayHandler / MixinRecipeHandlerRef / ProvidersListS2CPacket / RecallPatternPacket / SwapPatternPacket / RecipeMapDetector）；3.0.2 曾称清理完毕实际未清净；另多处 handler 用 `t.printStackTrace()` 应换 logger | 8 个源文件 | 🟢 | ❌ 未修复（2026-08-23 审查发现） |
| 55 | 死代码遗留（按仓库规范仅标记暂不删除）：`ClientState.lastProviderName` 字段及 `clear()`、`NetworkInventoryCache.getLastUpdateTick()` 与 put 冗余 count 参数、BUTTON_HALVE_ID 死分支、Replanner 吞异常路径等 | `client/ClientState.java` 等多处 | 🟢 | ❌ 未修复（2026-08-23 审查发现） |
| 56 | `docs/MOD_MAP.md` 为空模板（功能↔源码映射缺失、Mixin 列表未登记），违反文档驱动开发规范 §5.1；新开发者无法按图索骥 | `docs/MOD_MAP.md` | 🟢 | ❌ 未修复（2026-08-23 审查发现） |
| 57 | S2C 无应用层尺寸预算：超大网络供应器列表（ids+names+emptySlots 三列表）可超 1.7.10 自定义负载 ≈32KB 上限 → 发送侧失败、上传选择界面静默无响应 | `network/ProvidersListS2CPacket.java` | 🟢 | ❌ 未修复（2026-08-23 审查发现） |
| 58 | 智能倍增部分提前 return 分支疑似遗漏 `parallelismProvider.put(details, mediumListCheck)` 回写 → 并行度信息丢失致下 tick 重探测（轻微性能损耗；静态审查标记，修 #44 时一并复核） | `mixin/ae/MixinCraftingCPUCluster.java:790-793` 附近 | 🟢 | ❌ 待复核（2026-08-23 审查发现） |
| 59 | 二合一终端编码产出坏样板：`encodeItemPattern` 对未填满的输出列把 null 槽写成**空 NBT compound** → `UltimatePatternHelper` 解码后 `getAEOutputs()` 含 null → `CraftingGridCache.setPatternsFromCraftingMethods:340 out.copy()` NPE。PH 仓（22179）每 tick `postMEPatternChange` 重扫即每 tick NPE 刷屏 + **AE2 合成缓存重建被中断** → CPU 永远收不到样板。原生终端 `getOutputs()` 跳过空槽故从不触发。修复：编码循环过滤 null/空槽（对齐原生语义） | `merged/PatternContainer.java` `encodeItemPattern`（3.4.0 引入） | 🔴 | ✅ 已修复（2026-08-23 实测 22179 每 tick NPE 后定位） |
| 60 | 二合一终端上传静默失效：`UploadPatternPacket.resolveTerminal/resolveOutputSlot` 仅支持原生 `ContainerPatternTerm(Ex)`，无合并终端分支 → 服务端解析返回 null 直接 return，上传从未执行；撤回因依赖 lastProviderId 连带失效。修复：resolveTerminal 委托 `ContainerTerminalResolver`（已覆盖三种终端），resolveOutputSlot 增加 `IMergedPatternTerminal.getMergedEncodedSlot()` 分支 | `network/UploadPatternPacket.java:146-170`（3.4.0 引入） | 🔴 | ✅ 已修复（2026-08-23） |

> **#59 存量坏样板清理指引**：编码修复仅防新增。若网络中已插入坏样板（症状：22179 每 tick NPE），需打开该 PH 编程样板输入总成 GUI，手动取出坏样板销毁后重新编码上传。
| 61 | 处理配方池反查不准：「钢锭高炉配方被识别成电解机」。同一输入物品常存在于多个 GT 配方池，服务端 `RecipeMapDetector` 按 HashMap 无序遍历仅凭输入反查会随机命中；且编码时服务端反查结果**优先于** NEI 转写捕获的精确 `pendingRecipeMap`（顺序颠倒）。修复：① 编码时 pendingRecipeMap 优先、反查兜底；② 反查命中后用用户填写的输出物与 `GT_Recipe.mOutputs` 校验，输入+输出双匹配才确定，仅输入匹配降为候选 | `merged/PatternContainer.java` `encodeItemPattern` + `util/RecipeMapDetector.java` | 🟡 | ✅ 已修复（2026-08-23 用户实测反馈） |
| 62 | 二合一终端上传↑按钮左侧 3px 点击盲区：按钮容器坐标 [206,218] 横跨面板判定区左边界（x≥209），越界部分点击走原生逻辑静默失效。修复：面板按钮命中检测移出 isInPanel 判定，按 id 白名单（940-955）独立分发，不误抢 AE 原生按钮 | `merged/GuiMergedTerminal.java` `mouseClicked` + `client/event/MergedTerminalPanelHandler.java` `isPanelButton` | 🟢 | ✅ 已修复（2026-08-23） |
| 63 | 上传链路零日志：#54 清理时把上传/撤回全链路的调试输出一并删除且未留 logger 记录，故障排查无迹可循（本次"点击没反应"即无法定位）。修复：关键分支补 `MyMod.LOG.info("[Upload] ...")`——客户端 handleUpload 各 return 分支/策略选择、服务端容器解析失败/供应器查找失败/写入成功各一条，单次点击最多数条不刷屏 | `client/event/MergedTerminalPanelHandler.java` + `network/RequestProvidersListPacket.java` + `network/UploadPatternPacket.java` + `network/ProvidersListS2CPacket.java` | 🟢 | ✅ 已修复（2026-08-23） |
| 64 | **二合一终端服务端终端解析永久失败（#60 的真正根因）**：`ContainerTerminalResolver.resolveTerminal` 对合并终端分支反射读取 AE2 `ContainerInterfaceTerminal.anchor` 字段——但 3.5.0 重构后 `ContainerMergedTerminal` 是独立 `AEBaseContainer` 子类（自有 `anchor` 字段，类型 `IInterfaceTerminal extends IActionHost`），不再继承原生容器 → NoSuchFieldException 被吞 → 返回 null。诊断日志实测：`[Upload] server: terminal resolve failed for ContainerMergedTerminal`。**上传/撤回/供应器列表请求三条链路的服务端环节全部因此静默失败**。修复：反射改为沿容器类层级查找自有 `anchor` 字段（findDeclaredField 逐级向上），命中后强转 IActionHost | `util/ContainerTerminalResolver.java`（3.4.0 引入错误目标，3.5.0 重构后必然失败） | 🔴 | ✅ 已修复（2026-08-23 日志定位） |
| 65 | 操作链路诊断日志大检查：撤回（[Recall] 服务端 8 个分支+结果）、编码（[Encode] 面板空/非样板拦截/空白样板不足/成功含 recipeMap）、交换（[Swap] 输出槽缺失/少于2格）全部补齐 logger 日志，与 [Upload] 统一前缀便于 grep 排查 | `network/RecallPatternPacket.java` + `merged/PatternContainer.java` + `network/SwapPatternPacket.java` | 🟢 | ✅ 已补齐（2026-08-23） |
| 66 | 面板槽点击取物时而无效：自发 `windowClick`（vanilla C07 包）在服务端 `Container.slotClick` 对 SlotFake 系假槽行为不完整，且无客户端本地预测——服务端拒绝时 GUI 永不变化，表现为"点不掉、拖动才消失"。修复：镜像原生 `AEBaseGui.handleClickOrDragFakeSlot`——改发 `PacketClickOrDragFakeSlot`（含 NEI 幽灵物品支持，复用父类 getStackFromHand）+ 客户端本地 putStack 预测。不能落 super 是因为 GuiInterfaceTerminal.mouseClicked 的 masterList 判定会吞掉面板悬垂区点击 | `merged/GuiMergedTerminal.java` `mouseClicked`（3.5.0 引入 windowClick 方案） | 🔴 | ✅ 已修复（2026-08-23 用户实测反馈） |
| 67 | 上传成功后接口列表不实时更新：外部代码写入 provider 样板不会触发接口终端增量推送（原生仅 GUI 内操作走 syncIfaceSlot），需重开 GUI 才能看到。修复：UploadPatternPacket 写入成功后对打开中的合并终端容器调 `scheduleFullUpdate()`（forceNextUpdate 机制与原生一致，下 tick 全量 updateList 推送） | `merged/ContainerMergedTerminal.java` + `network/UploadPatternPacket.java` | 🟡 | ❌ 升级为 #68 |
| 68 | #67 的 scheduleFullUpdate 无效根因：`updateList()` 移植自原生但**缺失样板内容对比**——tracked 分支仅对比名字/在线/可见性/尺寸/优先级，从不比较样板槽内容 → forceNextUpdate 跑完判定"无变化"返回 null 不发包。原生同款限制（外部写入本就不实时）。修复：InvTracker 增加 slotCache 快照 + hasContentChanged() 逐槽对比；updateList 加 checkContents 参数，仅在调度刷新时对比内容并生成 overwrite 条目（常规 tick 不做对比避免逐槽开销） | `merged/ContainerMergedTerminal.java` updateList + InvTracker | 🔴 | ✅ 已修复（2026-08-23） |
| 69 | 新增：面板槽位滚轮调数量。悬停面板输入格滚动滚轮：上滚 +1、下滚 -1、**最小保持 1**（清空用左键取出或中键设 0；输出格禁改）；Shift+滚轮保留 OreDict 替换循环 | `merged/GuiMergedTerminal.java` `mouseWheelEvent` | 🟢 | ✅ 已实现（2026-08-23 用户需求，下限按反馈调整） |
| 70 | 撤回后接口列表不实时更新（#68 的撤回侧遗漏）：RecallPatternPacket 取走样板后未调度刷新。修复：撤回成功且玩家打开合并终端时同样调用 scheduleFullUpdate() | `network/RecallPatternPacket.java` | 🟡 | ✅ 已修复（2026-08-23 用户实测反馈） |
| 71 | 智能倍增 PH 介质记账缺陷（审查登记 #44）：`useMulti` 时按 **1 轮量**提取材料，若 `pushPatternMulti` 返回 `accepted==0`（介质忙/缓冲满）回退 `pushPattern` 单发成功后，因 `effectiveN>1` 走 GT 倍增分支 → 实际只交付 1 轮材料却**扣 N 轮功率、executedTasks+=N、taskValue-=N** → 合成少产出 N-1 轮、白扣 (N-1)×sum 功率、任务提前假完成。修复：倍增记账判定改为 `!useMulti && effectiveN>1`，useMulti 回退走原版逐轮路径（按实际交付的 1 轮记账） | `mixin/ae/MixinCraftingCPUCluster.java` executeCrafting 倍增回退分支 | 🔴 | ✅ 已修复（2026-08-23） |
| 72 | 审查登记 #58 复核结论：智能倍增所有退出路径（3 处提前 return、break 跳出后方法尾部 L941）均已有 `parallelismProvider.put` 回写，静态审查疑虑不成立，无需修改 | `mixin/ae/MixinCraftingCPUCluster.java` | 🟢 | ✅ 已复核无问题（2026-08-23） |
| 73 | 智能倍增大订单（如 1T）客户端无响应：GT `MTEHatchCraftingInputME.isBusy()` 始终返回 false → `knownBusyMediums` 永远不被填充 → do-while 循环每 tick 重复推送 `effectiveN` 轮（可达 Integer.MAX_VALUE），ME 网络每 tick 大额 extractItems + postChange → 客户端被海量物品更新淹没。修复：① `ae2qol$executeCraftingSmart` 开头 `knownBusyMediums.clear()` 重置跨 tick 残留；② GT/PH 路径倍增推送成功后 `knownBusyMediums.add(medium)` 冷却，防止同 tick 重复推送 | `mixin/ae/MixinCraftingCPUCluster.java` executeCraftingSmart + GT/PH 推送分支 | 🔴 | ✅ 已修复（2026-08-23） |

# 回滚指南

| 目标版本 | 使用 jar | 说明 |
|---|---|---|
| 3.6.0（当前） | `build/libs/AE2-QoL-3.6.0.jar` | 面板体验升级：样板回读二次编辑 / 编辑快照持久化 / PH 编程工具箱适配 / 装配矩阵上传按钮 / 流体解析严格匹配修复 / 数量上限移除与输出格禁编 |
| 3.5.1 | `build/libs/AE2-QoL-3.5.1.jar` | 二合一终端修复：openContext NPE 崩溃 / NEI 返回错位 / 处理样板改终极样板 / 网络拉空白样板 / NEI「+」填充与 `↓` 读回 / 隐藏 NEI 面板 |
| 3.5.0 | `build/libs/AE2-QoL-3.5.0.jar` | F 模块改为独立有线方块「样板与接口终端」+ 原生 AE2Things 风格面板（4×4×2 页 + 滚动条 + 反转），移除两个 mixin |
| 3.4.0 | `build/libs/AE2-QoL-3.4.0.jar` | 样板 + 接口二合一终端（F 模块）+ 配置页「配方参考」子页 |
| 3.3.5 | `build/libs/AE2-QoL-3.3.5.jar` | 修复智能倍增实测失效：功率/原料不足按轮数钳制 N、PH 走 pushPatternMulti、异常回退原版 |
| 3.3.4 | `build/libs/AE2-QoL-3.3.4.jar` | 修复自动上传把样板误投进 GT/PH 样板输入机原料缓存槽 |
| 3.3.3 | `build/libs/AE2-QoL-3.3.3.jar` | 样板输入机（GT/SNL/PH）智能倍增 + 流体显示回归验证 |
| 3.3.2 | `build/libs/AE2-QoL-3.3.2.jar` | 修复与 GTNotLeisure 的同步 id 冲突崩溃 + 超级接口智能倍增 |
| 3.3.1 | `build/libs/AE2-QoL-3.3.1.jar` | 修复与 ProgrammableHatches 的 mixin 冲突崩溃 |
| 3.3.0 | `build/libs/AE2-QoL-3.3.0.jar` | 统一配置文件 + 热加载 + `/ae2qof` OP 命令 |
| 3.2.0 | `build/libs/AE2-QoL-3.2.0.jar` | 智能倍增（Smart Doubling） |
| 3.1.2 | `build/libs/AE2-QoL-3.1.2.jar` | 修复流体误判显示 bug |
| 3.1.1 | `build/libs/AE2-QoL-3.1.1.jar` | 修复汉化乱码 |
| 3.1.0 | `build/libs/AE2-QoL-3.1.0.jar` | 全量安全加固（14 项风险修复） |
| 3.0.2 | `build/libs/AE2-QoL-3.0.2.jar` | 含流体直接显示 + 刷屏日志清理 |
| 3.0.0 | `build/libs/AE2-QoL-3.0.0.jar` | 功能最全（无限磁盘/角标/通知/Replan/IO端口），但含 B/C 已知崩溃问题 |
| 2.14.1 | `build/libs/AE2-QoL-2.14.1.jar` | 稳定基线（无线直连完整版），无 3.x 新功能 |

回退步骤：删除测试包 `mods/AE2-QoL-<旧版本>.jar`，复制目标 jar 为 `mods/AE2-QoL-<目标版本>.jar`，重启客户端。
依赖固定：AE2 `rv3-beta-977-GTNH`、ae2fc `1.5.88-gtnh`、NEI `2.8.19-GTNH`。

---

## 3.5.1 - 二合一终端修复批次

> 作者：wztwzt | 更新时间：2026-08-19

### 修复：openContext 空指针崩溃（`PacketInventoryAction`/`PacketSwitchGuis` 等）

- 根因：`PacketInventoryAction.serverPacketData` 对任何 `instanceof AEBaseContainer` 的 openContainer 无条件调用 `createPrimaryGui()`（`appeng/container/AEBaseContainer.java:1116` 的 `context.getTile()`），而合并终端从未调用 `setOpenContext(...)`（原生终端经 `GuiBridge.updateGui` 设置，我们走 IGuiHandler 打开故未设）→ `getOpenContext()` 为 null → NPE，网络握手致命错误导致退出
- 修复：`ContainerMergedTerminal` 构造函数补 `setOpenContext(new ContainerOpenContext(anchor))` + world/x/y/z/side（`ForgeDirection.UNKNOWN`）；`PrimaryGui.gui` 为 null 无害（`open()` 已空判）。中键对 NEI 面板可合成物品下单现会正常打开「合成数量」子界面

### 修复：查看 NEI 返回后界面整体偏左/主题丢失/空白样板「消失」

- 根因：`GuiMergedTerminal.drawScreen` 末尾临时放大 `xSize=1000`（让面板悬垂区参与 `GuiContainer` 行 361/507 的「点击出界」判定），从 NEI（`GuiRecipe`）返回触发 `displayGuiScreen`→`initGui()` 重新计算 `guiLeft=(width-1000)/2` → 巨负偏左
- 修复：覆写 `initGui()` 在 `super.initGui()` 前复位 `xSize=209`，保证任何重初始化使用正确尺寸；drawScreen 的放大技巧仅在绘制后到下一帧输入阶段生效，不再污染重新初始化

### 修复：处理模式样板编码产出终极样板 + 网络自动扣空白

- 根因：GTNH 原生 `ContainerPatternTerm.encode()`（`ContainerPatternTerm.java:305-311`）处理模式产出 `encodedUltimatePattern`，GT 机器仅识别终极样板；我们始终产出普通 `encodedPattern` → GT 机器不识别
- 修复：`PatternContainer.encodeItemPattern()` 按模式产出 `encodedPattern`（合成）/`encodedUltimatePattern`（处理）；`notPattern()` 增加终极样板判定（二次编码不拦截）；`patternSlotOUT` 类型 `ENCODED_PATTERN` 经继承天然接受终极样板
- 修复：空白样板槽为空时（网络有空白）`Platform.poweredExtraction` 自动扣取 1 张（能量用 `grid.getCache(IEnergyGrid.class)`，`getPowerSource()` 为 null 故直接用网格能量缓存）；`ContainerMergedTerminal.slotClick` 覆写镜像原生 `ContainerPatternTerm.slotClick`——空手左/右键点空空白槽拉取 1 张（`pickupStoredItems/splitStoredItems` 为 `ContainerMEMonitorable` 私有，改为内联 `poweredExtraction`）

### 新增：NEI 配方界面「+」覆盖层对合并终端生效 + 面板 `↓` 读回按钮

- `ClientProxy` 注册 `API.registerGuiOverlay(GuiMergedTerminal.class, "crafting", TerminalCraftingSlotFinder)` + `registerGuiOverlayHandler(..., DefaultOverlayHandler, "crafting")`（与 AE2 原生终端一致），使 NEI 配方页「+」对合并终端可见
- `MixinDefaultOverlayHandler.transferRecipe` HEAD 拦截：`gui instanceof GuiMergedTerminal` 时取消原逻辑（原逻辑对假槽 `FastTransferManager.clickSlot` 不适配本终端），改从 handler 提取配方（`NeiRecipeCapture.extractFrom`）→ 判定合成/处理 → 本地切模式 + 发 `MergedTerminalActionPacket.FILL`
- 面板顶部新增第 5 个 `↓` 按钮（`BUTTON_LOAD_ID=954`，y=57）：把 `patternSlotOUT` 已编码样板经 `ICraftingPatternDetails` 解码读回面板网格（`isCraftable()` 判模式 + FILL）

### 修复：NEI 物品面板不再覆盖终端右侧样板面板

- 根因：NEI 2.8 `ItemsGrid` 逐格调用已注册 `INEIGuiHandler.hideItemPanelSlot(gui,x,y,w,h)`（屏幕坐标）；AE2 自带 `NEIGuiHandler` 只转发 `GuiMEMonitorable` 系，我们的 GUI 继承 `GuiInterfaceTerminal→AEBaseGui` 不在转发范围
- 修复：新增 `client/nei/MergedNeiHandler`（extends `INEIGuiAdapter`），面板矩形 `[guiLeft+209, guiTop, 133, 202]` 与格子相交即隐藏；`ClientProxy` 注册 `API.registerNEIGuiHandler`

---

## 3.5.0 - F 模块重构：独立有线方块 + 原生 AE2Things 风格面板

> 作者：wztwzt | 更新时间：2026-08-18

### 变更：二合一终端改为独立有线方块「样板与接口终端」

- 原 3.4.0 通过 `MixinGuiInterfaceTerminal`/`MixinContainerInterfaceTerminal` 混入原生接口终端实现二合一；3.5.0 改为**独立有线方块**（`merged/BlockMergedTerminal` + `TileMergedTerminal` + `MergedGuiHandler` + `client/render/RenderBlockMergedTerminal`），合成配方 `[铁][玻璃][铁] / [红石][钻石][红石] / [铁][玻璃][铁]`，移除两个 mixin（不再依赖 `@Shadow` MCP 名解析）
- GUI/容器直接继承原生实现：`merged/GuiMergedTerminal extends GuiInterfaceTerminal`、`ContainerMergedTerminal extends AEBaseContainer`，面板逻辑内聚到 `merged/PatternContainer`（移植 AE2Things `PatternContainer`：4×4×2 页槽布局、`isSlotEnabled`/`offsetSlots`、`updateOrderOfOutputSlots`）
- 顶部保留上传(↑)/召回(←)/轮换(⇄)/OV 覆盖按钮（`GuiUploadButtonHandler` 同款样式，竖排放面板左上）

### 变更：面板改为 AE2Things 原生样式

- **原生控件**：`GuiImgButton`（编码/清空/×2/替代/备份替代/反转）、`GuiTabButton`（合成⇄处理 tab，`GuiText.CraftingPattern/ProcessingPattern`）、`GuiScrollbar`（处理模式翻页，`pattern.png` 纹理，非反转不显示）
- **处理模式 4×4×2 页网格**：输入 4 列 × 4 行 × 2 页（滚动条翻页），输出 4 列；**反转按钮**切换输入/输出列方向；合成模式为 3×3 + 结果槽
- **面板位置**：`offsetX+209` 绘制，槽显示 `y + 68` 与纹理孔位精确对齐（参照 AE2Things `PatternPanel` 布局常量）
- **删除**：`N`（NEI 配方填充）按钮与 NEI 联动不再作为面板按钮
- **交互**：面板槽点击走 `playerController.windowClick`（空光标）/ AE 拖拽放置（非空光标，`mouseClickMove` 完成）；滚动条点击与滚轮翻页；面板区域点击优先于接口列表
- **悬垂绘制**：`drawScreen` 用 `xSize=1000` 放大法绘制面板悬垂区，槽位命中依赖 `guiLeft/guiTop` 字段，已验证不破坏点击
- **状态同步**：`MergedTerminalActionPacket` 新增 `SET_INVERTED`/`SET_PAGE`（新增 `int value` 字段），面板模式/替代/反转/页码随包同步服务端容器

### 修复

- 修复 `en_US.lang` 中 `ae2qol.extract.success` 与上一 key 同行（缺换行）的文本丢失问题

### 风险登记（本版新增）

- 见「已知风险登记表」#41-#43：xSize 悬垂绘制槽位命中、面板客户端静态状态、`GuiTabButton` RenderItem 反射回退

---

## 3.4.0 - 样板 + 接口二合一终端（F 模块）

> 作者：wztwzt | 更新时间：2026-08-17

### 功能：改造原生接口终端为「样板 + 接口」二合一

- **思路**：原生 AE2 接口终端（`GuiInterfaceTerminal`/`ContainerInterfaceTerminal`）已经覆盖了接口/样板总成的浏览管理；样板终端（Pattern Terminal）则负责编码。F 模块直接把**样板编码面板**嵌入接口终端 GUI 右侧，实现「看接口 + 编样板」同一界面完成。因直接改造原生 GUI，**有线接口终端与 ae2fc 无线接口终端自动共用同一面板**，无需额外配置。
- **新增文件**：
  - `api/IMergedPatternTerminal.java`：面板契约 + 布局常量（`PANEL_X=149`/`PANEL_Y=56`/`SLOT_SIZE=18`/`INPUT_MAX=27`/`OUTPUT_MAX=9`）+ `mergedSwapOutputs()`。
  - `client/gui/MergedPanelLayout.java`：面板几何计算（activeInputs/activeOutputs、输出/结果/空白/编码行、4 行按钮、机器名行、`isInPanel`）。
  - `client/event/MergedTerminalPanelHandler.java`：按钮创建/摆位/标签刷新 + 全部动作分发（上传/召回/交换/NEI 填充/编码/清空/×2/模式/替代/备份替代/OV）。
  - `mixin/ae/MixinGuiInterfaceTerminal.java`：drawFG TAIL 画面板（GL 关 SCISSOR/DEPTH、半透明底+边框、槽格、机器名）+ mouseClicked HEAD 面板点击拦截（按钮直发 `onButtonClicked`，槽点击 windowClick）。
  - `mixin/ae/MixinContainerInterfaceTerminal.java`：容器侧槽布局/编码/填充/输出轮换 + 新增 `mergedSwapOutputs()`。
  - `mixin/nei/MixinGuiRecipe.java` + `client/NeiRecipeCapture.java`：从 NEI 当前配方页提取输入/输出/是否处理配方。
  - `util/ContainerTerminalResolver.java`：容器 → `IActionHost` 统一解析（含反射读 `ContainerInterfaceTerminal.anchor` 私有字段）。
  - `network/MergedTerminalActionPacket.java`（C2S：ENCODE/CLEAR/DOUBLE/SET_MODE/SET_SUBSTITUTE/SET_BE_SUBSTITUTE/FILL）、`network/MergedTerminalResultPacket.java`（S2C：机器名回显）。
- **网络包共用**：`RequestProvidersListPacket`/`RecallPatternPacket`/`SwapPatternPacket` 经 `ContainerTerminalResolver` 支持合并终端容器。
- **上传链路**：优先读已编码槽 NBT `apu:recipeMap` → `RequestProvidersListPacket(recipeMap, forceGui)` 三策略；无映射则降级 `ICraftingPatternDetails` 读输入/输出再请求。
- **点击拦截方案**：`InterfaceTerminalList` 为私有内部类无法 @Redirect → `@Inject mouseClicked HEAD + ci.cancel()` 手动分发（按钮 → `onButtonClicked`；槽 → `playerController.windowClick`），光标持有物品时的放置由 vanilla `mouseMovedOrUp` 完成；`findSlotAt` 复用 `GuiContainerAccessor.getGuiLeft()/getGuiTop()`。
- **@Shadow 结论**：本工程编译时 AP 对 @Shadow 报 "Cannot find target" 警告为常态（既有 mixin 同样如此且运行正常），refmap 基本为空 → 运行时类为 MCP 名，@Shadow 用正确 MCP 名即可；但 **@Shadow 不能用于继承自父类的 protected 方法**（`isPointInRegion`/`handleMouseClick` 失败），字段无此限制。
- **1.7.10 API 修正**（编译验证）：`CraftingManager.findMatchingRecipe(InventoryCrafting, World)` 返回 `ItemStack`；`maybeStack(int)` 返回 Guava `Optional` 用 `.orNull()`；`SoundHandler` 无 `playSoundEffect` → `mc.thePlayer.playSound("random.click", 1.0F, 1.0F)`。
- **状态同步**：客户端静态字段 `mergedCraftingMode/mergedSubstitute/mergedBeSubstitute`；InitGuiEvent.Post 打开终端时重置并清 `ClientState.mergedMachineName`；包携带状态。
- **已知接受项**：面板 x149..203 与玩家背包右侧 2 列 / 视图口右缘重叠；鼠标拖拽/双击未完整复刻（迭代 1 可接受）；`lastClickSlot/lastClickTime/lastClickButton/ignoreMouseUp/dragSplitting` 驱动部分已接入，完整复刻留待反馈。

### 新增：配置页「配方参考」子页

- 反射 `gregtech.api.recipe.RecipeMap.ALL_RECIPE_MAPS` 的 `unlocalizedName` 枚举**当前整合包全部配方池 UID**（含 GT++/gtpp），经 `RecipeMapNameConfig.resolveSearchKeyword` 解析中文，支持按 UID/中文筛选；新增 `RecipeNameUtil.getAllRecipeMapUids()`。供玩家对照填写「记住的供应器」配方名。

### 风险登记（本版新增）

- 见「已知风险登记表」#36-#40：@Shadow MCP 名解析、列表点击 vs 面板重叠、上传无提示、NEI 捕获依赖、包无越权面。

---

## 3.3.7 - 性能修复 + tooltip 换行 + 配置页范围/映射编辑

> 作者：wztwzt | 更新时间：2026-08-17

### 性能：超大订单卡死修复（1T 量级）

- **根因 1**：功率钳制 `while (N>1 && 电不足) N--` 每次 `extractAEPower(SIMULATE)` 都是一次网格查询，N 达 2^31 量级时直接卡死。
- **根因 2**：`ae2qol$accountSmartPush` 逐轮循环执行 N 次 `postChange` + `waitingFor.add` + `postCraftingStatusChange`，同样 O(N)。
- **修复**：
  - 功率钳制改 **O(1)**：一次 `extractAEPower(Double.MAX_VALUE, SIMULATE)` 取可用电总量，`available/sum` 直接算出可负担最大轮数（收敛结果与原逐轮递减一致；`< sum - 0.01` 的兜底判断保留原版语义）。
  - 批量记账：`accountSmartPush` 改为每输出栈一次，按 `rounds` 缩放总量后各记账一次（`waitingFor` 为 `IItemList` 同物品自动合并，语义等价）；诊断会话按本次 push 消耗 **1 个**（与原版 `pushPattern` 一致，顺带修正原先逐轮多消耗会话的问题）。
  - 原料钳制加 **int 溢出钳制**：`perRound × N ≤ Integer.MAX_VALUE`，防止 GT 缓冲 ItemStack 数量为负导致合成错乱。

### 修复：PH / GT 舱室 tooltip `\n` 换行不生效

- 根因：lang 值中的 `\n` 是字面量（Minecraft lang 不转义），ModularUI `addTooltipLine` 不拆行。
- 修复：`MixinDualInputHatchUI` / `MixinMTEHatchCraftingInputMEGui` 按 `\n` split 逐行 `addTooltipLine`；同步更新 hint 文案为「默认 0=不限，可在配置页修改」（zh/en）。

### 新增：配置页显示可调范围 + 名字映射热编辑

- 配置页标签补范围：`io_port_rate`(1~2147483647)、`smart_doubling_max_rounds`(0=不限/1~2147483647)、`nei_overlay_enabled`(true/false)。
- 新增第二页「名字映射编辑」：
  - **配方名映射 `recipe_names.json`**：配方 key + 中文搜索词 输入框 + 添加/更新/删除（删除按中文搜索词）。
  - **记住的供应器 `remembered_providers.json`**：配方名 + 供应器名 输入框 + 添加/更新/删除。
  - 均为**客户端本地**即时生效并热写入文件（供 NEI 叠加层 / 自动上传使用），无需 OP。
  - **3.3.7 增补**：映射页改为「列表 + 编辑」——左右两个分类切换按钮，左侧滚动列表展示全部已有映射（点击行选中并回填编辑框），右侧编辑框 + 添加/更新、删除(选中=按 key)、删除(按值)；新增 `RecipeNameUtil.removeMappingByKey` 与 `ClientState.removeRememberedProvidersByValue`。
  - **3.3.7 再增补（布局 + 配方参考）**：修复各层文字重叠（标题/切换按钮/列表头/副标题/编辑区全部垂直分离，副标题用 0.8 倍缩放灰色小字居列表上方）；新增第三个子页「配方参考」——反射 `gregtech.api.recipe.RecipeMap.ALL_RECIPE_MAPS` 的 `unlocalizedName` 枚举**当前整合包全部配方池 UID**（含 GT++/gtpp），经 `RecipeMapNameConfig.resolveSearchKeyword` 解析出中文，支持按 UID/中文筛选，供玩家对照填入「记住的供应器」配方名；新增 `RecipeNameUtil.getAllRecipeMapUids()`。

### 风险登记（本版新增）

- `#32`：批量记账后 `waitingFor` 单栈数量可超 `Integer.MAX_VALUE`（long 承载，语义等价）→ 低。
- `#33`：O(1) 功率钳制与逐轮递减存在 <0.01 AE 的舍入差 → 兜底判断保留，行为一致。
- `#34`：`removeRememberedProvider` 新客户端方法仅本地生效 → 低。
- `#35`：配置页两页切换 `initGui` 重建控件，字段值保留 → 已核对。

---

## 3.3.6 - 智能倍增默认不限 + 游戏内配置页面

> 作者：wztwzt | 更新时间：2026-08-17

### 新增：游戏内配置页面

- 「Mods → AE2 QoL → Config」打开配置页（Forge 标准 `guiFactory` 入口，`IModGuiFactory`），可编辑 `io_port_rate` / `smart_doubling_max_rounds` / `nei_overlay_enabled` 并即时应用。
- 改动经 `ConfigSetPacket`（C2S）提交服务端：**OP 校验**（`canCommandSenderUseCommand(2)`）+ key/范围白名单校验，成功后写服务端 `settings.json` 并广播 `ConfigUpdatePacket`（S2C）同步给所有客户端（含本地写盘）。
- 玩家登录时服务端自动推送当前配置（`PlayerLoggedInEvent`，走 FML 总线），配置页面始终显示服务端真实值。
- 新增 `Config.applySetting(key,value)` / `Config.applyAll(io,rounds,overlay)`，与热加载/`/ae2qof reload` 共用同一套校验与写盘逻辑。

### 变更：`smart_doubling_max_rounds` 默认 0 = 不限

- 默认值 `64 → 0`，范围 `1..4096 → 0..Integer.MAX_VALUE`；`0` 表示一次发配**剩余全部轮数**。
- 三个取整点同步支持 0=不限：CPU `ae2qol$smartMultiplier`（0 时跳过配置钳制）、GT 输入仓 `getMaxMultiplier`（0→`Integer.MAX_VALUE`，GT 缓冲本就无上限）、ME 接口 `getMaxMultiplier`（二分上界放宽）。
- 防溢出：`remaining` 为 long，取 N 时以 `Math.min(remaining, Integer.MAX_VALUE)` 封顶，避免 `(int)` 强转负数。
- 实际效果：GT 仓一次全发（受功率/CPU 缓冲钳制）；PH 仓按缓冲空间自取；ME 接口按相邻机器容量上限发配（物理极限，无法字面全发）。
- 旧 `settings.json` 里显式写的 64 仍生效（尊重玩家设置）；仅「未配置」时默认 0。

### 风险登记（本版新增）

- `#29`：配置 C2S 包权限 → OP 校验已修复。
- `#30`：0=不限后 long→int 溢出 → 封顶已修复。
- `#31`：大 N 逐轮记账性能 → 已兜底（N≤1e5 毫秒级；极端配置才需关注）。

---

## 3.3.5 - 修复智能倍增（GT/PH 样板输入机）实测失效

> 作者：wztwzt | 更新时间：2026-08-17

### 背景

3.3.3 为 GT 样板输入总成/输入总线 (ME)（meta 2714/2715）与 ProgrammableHatches 双口输入仓（meta 22130/22179）新增智能倍增，但实测发现：**勾选后完全无效，且无法发配物品**（关闭后恢复原版逐轮推送）。关闭仅影响智能倍增，其余功能正常。

### 根因（两个独立 Bug）

- **Bug 1（无法发配）**：倍增分支的功率门槛写成 `eg.extractAEPower(sum * effectiveN, SIMULATE) < sum*effectiveN - 0.01 → continue`，无任何回退。`extractAEPower` 的 SIMULATE 只返回**部分可提取值**（`Math.min(可提取, 请求)`），一旦网络 AE 不足 N 轮总电，该介质被**永久跳过**，CPU 对整个任务零推送；原版只查 `sum`（1×），所以关闭即恢复
- **Bug 2（无效）**：原料探测用 `getExtractItems(N×, details)` 并要求**严格全量匹配**（候选堆大小 == 请求大小），缓冲稍差一点即判定放不下，`effectiveN` 被静默降为 1——看起来勾选了但实际还是逐轮推送

### 修复方案（对齐 GTLCore 的单次推送模型）

- **原料钳制**：改为 `inventory.extractItems(N×, SIMULATE)` 求每个输入实际可提取轮数，`N = min(N, 各输入可提取轮数)`，**允许部分提取**（不再严格全量匹配）；取不到任何材料时 N 自然降为 1
- **功率钳制**：`while (N>1 且功率不足 sum*N) N--`，N 降到 1 时与原版一样只查 `sum`——**不再整体跳过介质**
- **PH 双口输入仓**：走 `pushPatternMulti(details, ci(1×), N)`，由仓内缓冲空间自取轮数并返回**实际接受轮数** `m`，CPU 按 m 记账（一次推送 m 轮）
- **GT 及其它**：构造 N× 配方缓冲调 `pushPattern`，成功即记账 N 轮
- **记账**：按实际轮数一次性扣电、逐轮消耗诊断会话、逐轮追加 waitingFor/输出、`executedTasks += m`、`remainingOperations--`、任务剩余轮数 `-= m`；余量 ≤ 0 时按原版清理
- **防御**：`ae2qol$onExecuteCrafting` 全链路 try/catch，异常记日志且**不 cancel**——回退到原版 `executeCrafting` 接管本 tick，任何情况下不会拖死 CPU
- **结构不变**：保持 `@Inject(HEAD)+cancel`，保留原方法字节码，ProgrammableHatches `MixinInstantComplete` 的注入点不受影响

### 变更文件

- `mixin/ae/MixinCraftingCPUCluster.java` —— 倍增分支重写：原料/功率钳制、PH `pushPatternMulti` 路径、GT N× 单次推送、按实际轮数记账（`ae2qol$accountSmartPush`）、`onExecuteCrafting` try/catch 兜底

### 回归要点

- GT 2714/2715：任务一次推 N 轮、机器缓冲连续消耗、任务完成时 waitingFor 平衡、不超产不丢物
- PH 22130/22179：`pushPatternMulti` 返回轮数与缓冲一致、开启 PH `fastPatternDualInput` 配置（默认开）
- 材料不足 / 功率不足：按可提取轮数钳制 N，禁止再出现"整体跳过介质导致零推送"
- 关闭智能倍增后行为与 3.3.2 完全一致（走原版路径）

---

## 3.3.4 - 修复自动上传把样板误投进 GT/PH 样板输入机原料缓存槽

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **根因**：AE2 Auto Pattern Upload 自动上传/撤回把「样板输入机」当普通 `IInventory` 处理。GT 机器与 PH 机器（GT `MTEHatchCraftingInputME` meta 2714/2715、PH `PatternDualInputHatch` meta 22130/22179）经 GT `IMetaTileEntity extends ISidedInventory` 实现了 `IInventory`，而其通用库存（原料缓冲槽）排号在专属样板槽之前——自动上传遍历空槽时把编码样板投进了原料缓存槽，多方块收不到配方；样板槽只能手动打开 GUI 放置
- **修复方案**：上传 / 空位统计 / 撤回三处逻辑统一改为**优先使用提供器自带的专属样板槽库存** `appeng.api.util.IInterfaceViewable.getPatterns()`：
  - AE2 接口（`IInterfaceHost extends IInterfaceViewable`）、GT 样板输入机、PH 双口输入仓均实现 `IInterfaceViewable`，其 `getPatterns()`/`rows()`/`rowSize()` 指向样板专用区域
  - `UploadPatternPacket.insertPatternIntoProvider`：先走 `IInterfaceViewable` 分支写入样板槽；GT/PH 写入后调 `MetaTileEntity.markDirty()` 标记存档（`setInventorySlotContents` 已自动触发机器内部样板重建与 ME 网络同步）；AE2 接口仍走 `IInterfaceHost.saveChanges()`，行为不变
  - `RequestProvidersListPacket.estimateEmptySlots`：只统计样板槽空位，避免把原料缓存槽误报为可用空位导致自动选择投递目标
  - `RecallPatternPacket`：撤回限定在 `rows()*rowSize()` 样板区域，不再把缓存槽里的原料误当样板
- **影响范围**：GT「样板输入总成/总线 (ME)」（2714/2715）、ProgrammableHatches「编程样板输入总线」（22130）、「编程样板输入总成 MK.II」（22179）；AE2 ME 接口 / 超级接口原有自动上传行为不变

### 修改文件

- `network/UploadPatternPacket.java` —— `insertPatternIntoProvider` 改用 `IInterfaceViewable` 优先 + 新增 `markProviderDirty`（`IInterfaceHost`→`saveChanges`；`MetaTileEntity`→`markDirty`）
- `network/RequestProvidersListPacket.java` —— `estimateEmptySlots` 改用 `IInterfaceViewable` 统计样板空位
- `network/RecallPatternPacket.java` —— `findProviderInventory` 重构为 `findProvider` + `resolvePatternInventory`/`resolvePatternLimit`，撤回限定在样板槽区域

---

## 3.3.3 - 样板输入机（GT/SNL/PH）智能倍增 + 流体显示修复

> 作者：wztwzt | 更新时间：2026-08-16

### 新功能

- **GT 样板输入机（ME）智能倍增**：`MTEHatchCraftingInputME`（GT 机器「样板输入总成 (ME)」meta 2714 / 「样板输入总线 (ME)」meta 2715）GUI 左下角新增循环箭头开关按钮（ModularUI），勾选后合成 CPU 对挂在其上的样板**一次性推送多轮材料**，机器连做多轮不再逐轮补料
  - 通过 `mixin/gt/MixinMTEHatchInputBus` 为 GT 输入仓全族（`MTEHatchInputBus` 及子类）注入 `ISmartDoublingMedium` 实现，仅 `instanceof ICraftingProvider`（即样板输入机）生效，普通输入总线、补货输入仓、`MTEHatchPatternProvider` 不受影响
  - 开关状态写入机器 NBT（键 `ae2qolSmartDoubling`）随存档持久化，默认关闭
  - 每轮最大轮数沿用配置 `smart_doubling_max_rounds`（默认 64），CPU 侧剩余轮数与原料可取性会进一步裁剪
- **ProgrammableHatches 双口输入仓智能倍增**：`PatternDualInputHatch`（meta 22130 / 22179）同样支持，ModularUI 内 `(7, 62)` 位置新增开关按钮（`populateUI` TAIL 注入）
- **GTNotLeisure 超级二合一 ME 接口（SuperDualInterface）智能倍增**：方块与线缆面板两形态 GUI 左侧新增复选框（方块 `guiTop+134`、面板 `guiTop+116`，位于 fuzzyMode 与翻页之间，按 host 形态自适应偏移）；物品侧 `DualityInterface` 已由 `MixinDualityInterface` 覆盖、容器同步字段已具备，无需重复注入
- **流体显示回归验证**：3.1.2 流体误判修复（`isGtFluidDisplay` 类名识别）并入本次发布，回归确认 GT 流体容器在 NEI 角标与悬浮提示中仍按 `mB` 显示流体量
- **兼容性**：GT（`MTEHatchInputBus`/`MTEHatchCraftingInputME` 等）、ProgrammableHatches、ModularUI2、GTNotLeisure 均为可选依赖（compileOnly + mixin 配置级 `required=false`），任一缺失时对应注入静默跳过，不影响其余功能

### 修改文件

- `mixin/gt/MixinMTEHatchInputBus.java` —— **新增**：GT 输入仓族 `ISmartDoublingMedium` 实现 + NBT 持久化（`saveNBTData`/`loadNBTData` TAIL 注入，`remap=false`）
- `mixin/gt/MixinMTEHatchCraftingInputMEGui.java` —— **新增**：GT 样板输入机 ModularUI 左下角开关按钮（构造器 TAIL 捕获机器引用，规避继承字段 @Shadow 风险；`createBottomLeftCornerFlow` RETURN 注入）
- `mixin/gt/MixinDualInputHatchUI.java` —— **新增**：PH 双口输入仓 `DualInputHatch.populateUI` RETURN 注入开关按钮
- `mixin/ae/MixinGuiSuperDualInterface.java` —— **新增**：GTNL 超级二合一接口 GUI 智能倍增复选框（addButtons/`func_146284_a`/drawFG 三处 TAIL，`remap=false`，按 host 形态计算偏移）
- `mixins.ae2_qof.json` —— 公共列表新增 `gt.MixinMTEHatchInputBus`；客户端列表新增 `ae.MixinGuiSuperDualInterface`、`gt.MixinMTEHatchCraftingInputMEGui`、`gt.MixinDualInputHatchUI`
- `dependencies.gradle` —— 新增 `compileOnly`：GT（`libs/gregtech-5.09.52.594.jar`）、ModularUI2（`libs/modularui2-2.3.73-1.7.10.jar`）、ProgrammableHatches（`libs/programmablehatches-0.2.0p8.jar`）

---

## 3.3.2 - 修复与 GTNotLeisure 的同步字段冲突崩溃 + 超级接口智能倍增

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **崩溃根因**：智能倍增在 `ContainerInterface` 注入的同步字段用了 `@GuiSync(19)`，而 GTNotLeisure 的 `ContainerSuperInterface`（extends `ContainerInterface`）自己声明了 `@GuiSync(19) sidelessMode`。AE2 的 `DataSynchronization.collectFields` 会遍历整个类层级收集 `@GuiSync` 字段，发现同一个 sync id 被声明两次时直接抛 `IllegalStateException`，游戏崩溃。
- **修复方案**：同步 id 从 `@GuiSync(19)` 改为 `@GuiSync(30)`：
  - AE2 `ContainerInterface` 继承链已用 id：`ContainerUpgradeable`=0/1/5/6，`ContainerInterface`=3/4/7~18
  - GTNL `ContainerSuperInterface` 用 19，AE2 无其它子类占用 19~30
  - `@GuiSync(30)` 与两边都不冲突，`DataSynchronization` 不再抛异常，智能倍增同步功能不变

### 新功能

- **GTNotLeisure 超级接口智能倍增**：GTNL 超级接口（Super Interface，即样板总成）GUI 左侧新增「智能倍增」复选框（位于 fuzzyMode 与翻页按钮之间，`guiTop + 152`）。
  - GTNL 超级接口方块基于 AE2 `DualityInterface`（`TileEntitySuperInterface` 直接持真实 duality），本模组的 `MixinDualityInterface`/`MixinContainerInterface` 天然作用于其上，其容器自动获得同步字段与持久化
  - 新增 `MixinGuiSuperInterface` 注入 GTNL `GuiSuperInterface`（extends `GuiUpgradeable`，非 `GuiInterface`），复刻原版 ME 接口的复选框逻辑
  - 勾选后，挂在该超级接口上的样板同样由合成 CPU 一次性推送多轮材料
- **兼容性**：GTNotLeisure 为可选依赖（compileOnly + mixin 配置级 `required=false`），不安装时其余功能不受影响；GTNL 发布包为 SRG 混淆，`actionPerformed` 运行时名为 `func_146284_a`，注入按该名处理
- **ProgrammableHatches**：其样板合成器（`TileMolecularAssemblerInterface`）实现 `ICraftingMachine.acceptsPlans()`，本模组自动按单轮（N==1）处理，不超产

### 修改文件

- `mixin/ae/MixinContainerInterface.java` —— `@GuiSync(19)` → `@GuiSync(30)`（含 javadoc）
- `mixin/ae/MixinGuiSuperInterface.java` —— **新增**：GTNL 超级接口 GUI 智能倍增复选框（addButtons/drawFG/`func_146284_a` 三处 TAIL 注入）
- `mixins.ae2_qof.json` —— 客户端列表新增 `ae.MixinGuiSuperInterface`
- `dependencies.gradle` —— 新增 `compileOnly` GTNotLeisure（`libs/sciencenotleisure-0.2.7-pre1-dev-290.jar`）

---

## 3.3.1 - 修复与 ProgrammableHatches 的 mixin 冲突崩溃

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **崩溃根因**：智能倍增此前用 `@Overwrite` 整体重写 `CraftingCPUCluster.executeCrafting()`，替换了整个方法体。ProgrammableHatches（`programmablehatches-0.2.0p8.jar`）的 `eucrafting.MixinInstantComplete` 也要向同一方法 `@Inject`（`@At("INVOKE")`），因找不到注入点而崩溃。
- **修复方案**：`@Overwrite` → `@Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)` + `ci.cancel()`：
  - HEAD 注入不改动原方法字节码结构（INVOKE 指令原样保留），其它模组对同一方法的注入点仍可正常定位 → 不再崩溃
  - 仅当检测到**存在启用智能倍增的介质任务**（剩余轮数 > 1 且非 craftable）时才接管整个 tick（`ae2qol$hasSmartDoublingTask` 预扫描）
  - 无智能倍增任务时完全不接管，原版 executeCrafting（含其它模组的注入代码）原样执行
  - 接管时内部 N==1 分支仍与原版逐行等价，功能与 3.2.0 完全一致
- **兼容性**：智能倍增开启前/关闭后与 ProgrammableHatches 共存正常；开启期间该 tick 由本模组接管，PH 的注入代码该 tick 不执行（不影响其它 tick）。

### 修改文件

- `mixin/ae/MixinCraftingCPUCluster.java` —— `@Overwrite` 改 `@Inject(HEAD)+cancel` + 新增 `ae2qol$hasSmartDoublingTask` 预扫描 + 原循环体移入 `ae2qol$executeCraftingSmart`（逻辑不变）

---

## 3.3.0 - 统一配置文件 + 热加载 + OP 管理命令

> 作者：wztwzt | 更新时间：2026-08-16

### 新功能

- **统一配置文件** `config/ae2_qof/settings.json`（取代旧 `config/ae2_qof.cfg`）：
  - `io_port_rate`：强化 IO 端口传输倍率（默认 1024，1..Integer.MAX_VALUE）
  - `smart_doubling_max_rounds`：智能倍增最大轮数（默认 64，1..4096）
  - `nei_overlay_enabled`：NEI 叠加层开关（吸收原 `OverlayConfig` 同路径文件，避免覆盖其它字段）
- **热加载**：直接编辑 `settings.json` 保存后约 1 秒自动生效（单机/服务端均可，无需重启）。`MixinTileIOPort`、`MixinDualityInterface.getMaxMultiplier`、`MixinCraftingCPUCluster.executeCrafting` 均接入 mtime 限流校验。
- **OP 命令** `CommandAe2QoL`（`/ae2qof`，权限等级 2）：
  - `/ae2qof reload` —— 立即热重载 `settings.json` + `recipe_names.json`（含 `RecipeMapNameConfig` 缓存刷新）
  - `/ae2qof status` —— 显示当前生效配置值
  - 服务端需 OP；单机/局域网主机默认 OP 可直接使用；`/apu-overlay` 维持无需 OP
- **旧配置迁移**：首次启动检测到旧 `config/ae2_qof.cfg` 时自动把 `exIOPortTransferContentsRate`/`smartDoublingMaxRounds` 数值迁入 `settings.json`，并删除旧 cfg，玩家已有调优值不丢失。

### 修改文件

- `Config.java` —— 重写为 `settings.json` 管理器（JSON 读写 + 数值 clamp + mtime 热加载 + 旧 cfg 迁移）
- `client/OverlayConfig.java` —— 改为委托 `Config`（统一文件、保留其它字段）
- `mixin/ae/MixinTileIOPort.java` —— 传输前 `Config.ensureFresh()`
- `mixin/ae/MixinDualityInterface.java` / `mixin/ae/MixinCraftingCPUCluster.java` —— 计算前 `Config.ensureFresh()`
- `CommonProxy.java` —— `serverStarting` 注册 `CommandAe2QoL`
- `README.md` / `README.en.md` —— 新增智能倍增功能说明 + 配置文件/命令文档
- `gradle.properties` / `mcmod.info` —— 版本 3.3.0

### 新增文件

- `CommandAe2QoL.java` —— `/ae2qof` 管理命令

---

## 3.2.0 - 智能倍增（Smart Doubling）

> 作者：wztwzt | 更新时间：2026-08-16

### 新功能

- **智能倍增**：ME 接口（DualityInterface）新增「智能倍增」复选框。勾选后，CPU 对挂在接口上的样板一次性推送 **N 轮**材料，GT 机器连做 N 次，补料不再逐轮等待。
- N 计算：`N = min(剩余轮数, 配置上限 smartDoublingMaxRounds（默认 64）, 各输入槽可提取量/单轮量)`；接口侧 `getMaxMultiplier` 按面×输入用 `simulateAddStack` 二分探测机器最大可吞轮数，任一面/输入放不下则整体回退 N==1。
- 安全边界（N==1 与逐轮原版路径逐行等价）：
  - craftable 输入、假合成（fake crafting）、流体接口、阻塞模式（BLOCKING）、接口有滞留未推送物品、机器无任何面有 adaptor、GT `ICraftingMachine.acceptsPlans()` 机器 → 一律 N==1
  - 提取前全槽 SIMULATE 探测 N，提取中任一模槽部分提取 → 回退单轮路径（防丢物/超产）
- 能耗按 N×sum 记账；`value -= N`、`waitingFor` 累加 N× 产出、`executedTasks += N`；每轮 `consumeCraftSession()`，产出分批推送（接口次 tick 缓冲）。
- 私有内部类（`TaskProgress`/`finalOutput`/`CraftingCpuDiagnostics`）与 CPU 私有字段经**缓存反射**访问；反射失败自动降级为不启用智能倍增，不影响原版合成。

### 新增文件

- `api/ISmartDoublingMedium.java` — 介质接口（`isSmartDoublingEnabled`/`setSmartDoubling`/`getMaxMultiplier`）
- `api/ISmartDoublingContainer.java` — 容器接口（同步界面开关）
- `mixin/ae/MixinDualityInterface.java` — 实现介质接口；`writeToNBT`/`readFromNBT` 注入持久化开关；`getMaxMultiplier` 全安全边界探测
- `mixin/ae/MixinContainerInterface.java` — `@GuiSync(19)` 布尔开关 + 构造初始化
- `mixin/ae/MixinGuiInterface.java` — 复刻 `patternOptimization` 的 `GuiToggleButton` 复选框（icon 178/194）
- `network/SmartDoublingTogglePacket.java` — C2S 切换，归队 `ServerThreadUtil.addScheduledTask`

### 修改文件

- `mixin/ae/MixinCraftingCPUCluster.java` — `@Overwrite executeCrafting`（逐行移植 + 智能倍增 N 分支）
- `network/ModNetwork.java` — 注册 `SmartDoublingTogglePacket`
- `Config.java` — `smartDoublingMaxRounds = 64`（范围 1..4096）
- `lang/zh_CN.lang` + `lang/en_US.lang` — `gui.ae2_qof.smart_doubling` / `.hint`
- `mixins.ae2_qof.json` — 注册 `MixinDualityInterface`/`MixinContainerInterface`（公共列表）、`MixinGuiInterface`（client 列表）

### 使用说明

在 ME 接口的 GUI 左侧点击「智能倍增」复选框（图标为循环箭头），将该接口设为支持一次推送多轮的介质；机器只吃 N 轮时接口会缓冲补推。全局上限在 `config/ae2_qof.cfg` 的 `smartDoublingMaxRounds` 调整。

---

## 3.1.2 - 修复流体数量随机出现在物品上的显示 bug

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **J（流体误判）**：`NetworkInventoryCache` 用 `FluidRegistry.getFluid(itemDamage)` 判定"是否为流体"。Forge `FluidRegistry` 按注册序分配 ID（水=0、岩浆=1…），任何 damage 恰好命中流体 ID 的物品（最常见 damage=0 → 水）都被误判为流体，导致 NEI 配方角标/书签角标/tooltip 在**随机物品**上显示 `X mB 水/岩浆/…`（"随机"取决于网络缓存里当时有哪些流体），并干扰 NEI 面板 Shift+左键提取/中键合成的取出/合成判定
- **J（识别方式修正）**：ae2fc `ItemFluidPacket` 实际把流体编码在物品 NBT（`"FluidStack"` 复合标签 + `"Amount"` long），`newStack()` 从不写 damage——3.0.2"damage 编码流体 ID"假设错误（damage 仅用于 `getColorFromItemStack` 取渲染色）。现改为：① 按物品类名精确识别 ae2fc `ItemFluidPacket`（不 import ae2fc，保持模组独立）；② 流体直接从 NBT 读取；③ 流体方块物品走 `fluidItemMap` 反查（该 map 此前写了从未读，为死代码，现启用）；④ 其余物品一律按普通物品处理
- **J（回归确认）**：水/岩浆/蒸馏水等纯流体在 NEI 角标/tooltip 仍正确显示流体量（mB）；桶/单元等容器物品仍按容器数量显示；普通物品不再出现流体量

### 技术说明

- 消费方 `MixinNEIRecipeWidget`/`NetworkTooltipHandler`/`NetworkInventoryDrawHandler`/`MixinPanelWidgetClick` 无需改动，统一走修正后的 `getCount`/`isCraftable`/`getFluidStack`
- 对应风险表 #19；开发调研备忘见文末「附：开发调研记录（2026-08-16）」

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

---

## 附：开发调研记录（2026-08-16）

> 作者：wztwzt | 记录时间：2026-08-16

本节记录后续功能（智能倍增、F 模块）的前置调研结论，供后续会话直接接手。

### 1. 智能倍增（Smart Doubling）调研与实现计划

> 状态：✅ **已实现（3.2.0，2026-08-16）**，下文为开发前的调研与计划存档。

#### 需求

ME 接口的样板在 GT 机器上每次只推 1 轮材料，材料补料慢、不便于自动化。目标：让接口**一次性推送 N 轮**材料，机器连做 N 次（N 可配置，默认上限 64）。

#### 调研结论

- AE2 rv3-beta-977-GTNH 与 ae2fc **均没有**"扩展样板供应器（Extended Pattern Provider）"方块；rv3 的样板供应角色 = **ME 接口（`DualityInterface`，1896 行）** + ae2fc 流体接口
- GTNH 已内置 `PatternMultiplierHelper` + `doublePatterns`/`PatternOptimization`（位运算直接改样板 NBT，把 1 轮材料的样板"加倍"）——方向相反，用户**明确不要**这种改样板方案
- 合成执行流（`CraftingCPUCluster.executeCrafting`，每 tick 调用）：
  1. 对每个样板任务（`TaskProgress`，`value` = 剩余轮数）从 CPU 私有 `MECraftingInventory` 提取 **1 轮**构建 `MEInventoryCrafting`（槽位无上限——`setInventorySlotContents` 直接存 `IAEStack`，已验证可放 N× 材料）
  2. 经 `CraftingGridCache.getMediums(details)` 找所有非忙 `ICraftingMedium`
  3. 调 `pushPattern(details, table)`；成功 → `value--`，产出累加进 `waitingFor`（`IItemList`）；非阻塞模式下同 tick 继续循环
- **关键结论：多轮推送必须由 CPU 协同**——剩余轮数与预留材料都归 CPU 私有；供应器自己从网络存储自取会**超产**。且 `pushPattern` 返回 boolean，无法告知 CPU 实际消耗了几轮
- `DualityInterface.pushPattern` 把表推到相邻机器（`InventoryAdaptor.addStack`，放不下的部分缓冲进发送列表，次 tick 再推）

#### 用户决策

1. 载体 = **现有 ME 接口加复选框**（不新增方块）
2. F 模块**维持搁置**，先做智能倍增
3. 默认合并上限 **64 轮**

#### 实现计划（版本 3.2.0）

| 文件 | 内容 |
|---|---|
| `api/ISmartDoublingMedium.java`（新） | 接口：`boolean supportsSmartDoubling()` / `int getMaxMultiplier()` |
| `mixin/ae/MixinDualityInterface.java`（新） | implements 上述接口；`writeToNBT`/`readFromNBT` 注入持久化布尔 `smartDoubling`（ConfigManager 按 `Settings` 枚举建字段，**不能加新枚举**，必须走 data 复合标签）；`getMaxMultiplier`：阻塞模式或机器是 GT `ICraftingMachine.acceptsPlans()` 时返回 1 |
| `mixin/ae/MixinCraftingCPUCluster.java` | `@Overwrite executeCrafting`（逐行移植约 250 行）：建表处按 N× 提取材料；成功分支 `value -= effectiveN`、`waitingFor` 累加 N× 产出、能耗 N×sum；`N = min(剩余轮数, 机器容量估计, 配置上限 64, 防溢出)`，`effectiveN = min(各输入槽 提取量/单轮量)` |
| `mixin/ae/MixinGuiInterface.java`（新） | 复刻 `patternOptimization` 的 `GuiToggleButton` 复选框（smartDoubling 按钮） |
| `mixin/ae/MixinContainerInterface.java`（新） | 新增 `@GuiSync(n)` 布尔字段（`standardDetectAndSendChanges` 反射同步，mixin 加字段可行） |
| `network/SmartDoublingTogglePacket.java`（新） | C2S，归队 `ServerThreadUtil.addScheduledTask`（风险 #5 合规） |
| `Config.java` | `smartDoublingMaxRounds = 64` |
| `lang/zh_CN.lang` + `lang/en_US.lang` | `gui.ae2_qof.smart_doubling` 等 |
| `mixins.ae2_qof.json` | 注册新 mixin |

#### 风险（写代码时对照风险表）

- `@Overwrite executeCrafting` 是关键路径，必须全量回归原版合成行为
- GT `ICraftingMachine.acceptsPlans()` 机器与阻塞模式必须 N=1（否则机器可能吞掉多轮材料或行为异常）
- 流体量按 N× 放大，注意 long 溢出（`waitingFor` 累加 + 消耗扣减）

### 2. F 模块（样板 + 接口双页面二合一终端）调研更新

- 复核确认：AE2 977 原生已有**完整接口终端**（`GuiInterfaceTerminal` 1958 行自定义动态槽 GUI、`ContainerInterfaceTerminal`、`PartInterfaceTerminal`、无线版 `ItemWirelessInterfaceTerminal`/`WirelessInterfaceTerminalGuiObject`），以及 `PartPatternTerminal`/`PartPatternTerminalEx`/`GuiPatternTerm`/`GuiPatternTermEx` 样板终端
- 结论：**维持搁置**（原生已覆盖接口管理；F 的增量价值仅剩"单窗口双页 + 写样板自动填机器名"联动）。重开条件与 4 个确认点见上文「F 功能规划记录」
- 若后续重开，推荐直接扩展 AE2 原生 `GuiInterfaceTerminal`（注入其类添加页面切换 + 嵌入样板编码区），而非 6000 行移植

### 3. 流体显示 bug（3.1.2 修复）备忘

- 根因、识别方式修正、回归点见上文 3.1.2 条目；风险表 #19

---

## 附：后续工作入口（checklist）

> 供下一次会话直接接手，按序执行。

1. **智能倍增（3.2.0）**：✅ **已完成并发布**（`@Overwrite executeCrafting` 逐行移植 + 接口复选框 + 配置上限）。回归要点见风险表 #20/#21 与 3.2.0 条目
2. **F 模块**：维持搁置；若重开，先与使用者对齐上文 4 个确认点
3. **统一配置 + 热加载（3.3.0）**：✅ **已完成并发布**（`config/ae2_qof/settings.json` + `/ae2qof` OP 命令 + 旧 cfg 迁移）。改动面见 3.3.0 条目与风险表 #22
4. **mixin 冲突修复（3.3.1）**：✅ **已完成并发布**（`@Overwrite executeCrafting` → `@Inject(HEAD)+cancel` + 智能倍增任务预扫描）。见 3.3.1 条目与风险表 #23
5. **GTNotLeisure 兼容（3.3.2）**：✅ **已完成并发布**（`@GuiSync(19)`→`@GuiSync(30)` 修复同步 id 冲突崩溃 + `MixinGuiSuperInterface` 超级接口 GUI 复选框；PH 样板合成器按 `acceptsPlans` 单轮处理）。见 3.3.2 条目与风险表 #24
