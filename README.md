# AE2 QoL

> **简体中文** | [English](README.en.md)

为 **Minecraft 1.7.10 / GT New Horizons** 开发的 AE2 效率增强模组：NEI 样板上传、库存与合成状态提示、二合一终端、无线 AE 连接，以及 GT 仓室、电网与库存监控工具。

**作者：wztwzt · 当前源码版本：3.21.3 · 对照整合包：GTNH 2.9.0-beta-3**

本仓库用于个人存档，暂不开放分发。来源、署名与许可记录见 [CREDITS.md](CREDITS.md)。功能说明不代表所有兼容组合均已通过实机测试。

## 本版变化：3.21.3（修「智能倍增在服务器上不生效」）

- **根因**：GT/GTNL/PH 三处智能倍增开关建在**仅客户端**的 mixin 里（`mixins.ae2_qof.json` 的 `client` 段），
  而它们的 `BooleanSyncValue(...).allowC2S()` 要求**服务端存在同名同步处理器**才会被接受。
  专用服务端没有这段注入 ⇒ 客户端写入被 MUI2 丢弃（两条丢弃分支都不写日志）⇒ 服务端机器开关恒 false
  ⇒ CPU 静默走原版一次一轮。单人正常是因为 SP 是同一客户端 JVM，client 段 mixin 照样变换了该类。
- **修复**：开关改为「客户端只报**机器坐标** → 服务端重新解析 MTE、校验 `ISmartDoublingMedium`
  → 写入开关并 `markDirty()` → 回一份**服务端权威状态**给客户端对齐显示」，与 MUI2 面板双端构建行为完全解耦；
  打开界面时会主动拉一次真值，重登后显示与服务端一致。
- **诊断**：服务端应用开关时记一行 INFO（机器类型/坐标/是否样板介质）；若 CPU 发现"玩家刚开启过、
  但服务端看到的仍是关"，记一条 WARN —— 这类"勾了却不生效"以后可定性，不再静默。
- 未改动：CPU 侧推送/记账逻辑、NBT 键名、单人行为、ME 接口的既有容器路径（那条本来就能在服务端工作）。

## 本版变化：3.21.2（修行内名称空白 + 发信器数量改不动）

- **名称整列空白**：MUI2 的 `ButtonWidget` 继承 `SingleChildWidget`，其 `child()` 会**先 dispose 旧子控件**，
  所以"名称 + 数值"两个子控件时**名称被直接扔掉**。改为名称/数值各用一个按钮、文字走
  **`overlay(IKey)`**（3.20.4 已验证能渲染文字的路径）。
- **发信器数量改不动**：装进按钮里的文本会**吞掉点击**（本仓 PH 的 `NonInteractiveText` 就是为这个坑而写），
  导致"点行打开编辑"失效、子面板没有选中目标 ⇒ 自然改不了。现在两个文字按钮各自绑定点击、没有子控件遮挡。
- **行内类型显示"未知"**：`LevelType` 的常量实际是 **`ITEM_LEVEL` / `ENERGY_LEVEL`**
  （没有 `ITEM`/`FLUID`/`ENERGY`，已用实例 jar `javap` 核对），3.21.0 用等值比较必然落到 unknown；
  改为前缀匹配（`ITEM*`→物品、`FLUID*`→流体、`ENERGY*`→能量）。

## 本版变化：3.21.1（覆盖板列表只看本终端网络）

- **覆盖板列表改为只列本终端所连网络**（按 `CoverRegistry.getByNetwork(终端 networkId)`；
  终端走邻接直连、没绑网络时仍列全部）。若过滤后列表为空，会明确写出
  「本终端所连网络下没有覆盖板（另有 N 个属于其他网络，已隐藏）」，而不是留一片空白。

- **新增：每行两个动作按钮【高亮】【传送】**（照抄自适应电网终端的分工）：
  点【高亮】在目标方块上画**发光框 10 秒**（渲染器自带"非同维度跳过"，跨维度时给提示）；
  点【传送】把玩家送到目标方块**旁/上的可站立格**，**支持跨维度**（沿用自适应终端那份覆写了
  `placeInPortal` 的自定义 `Teleporter`，避开"找/建下界门"的老 bug）。
  两个按钮都是**客户端回调**，只把坐标交给服务端；服务端重新解析目标（覆盖板要真的贴在那个方块上、
  发信器要求该方块是 AE 主机）并做**会话 + AE 网络 BUILD 权限**双重校验后才执行。
- **UI 优化**：行内改为「左名称（左对齐）· 右中文数值」；类型与模式显示**中文**（物品/流体/能量、高于/低于）；
  悬停整行显示完整名称 + `D维度 [x, y, z]`（覆盖板另显示所在面与在线状态）；标题配色微调；
  列表仍可滚动且无行数上限。
- **汉化修正**：物品名补上 `gt.blockmachines.stock_monitor_terminal.name`（原来缺键，NEI 里显示英文
  `Stock Monitor Terminal`），同时覆写 `getLocalName()` 双保险；行内缩写与 `Close`、`Type:`、`(unset)`、
  `Emitter` 等硬编码英文全部改为语言键。
- 顺带：终端每 tick 推进"高亮自动清除"队列——**没装自适应电网终端的存档里高亮也会按时消失**。

- **3.20.3 修复：库存统计终端（32107）GUI 打开后只有两行标题**（「库存检测覆盖板」「AE 标准发信器」），
  没有列表、没有「连接 AE」、也没有任何数值输入框，终端读不到库存——**自始至终如此**。
  根因有两条，第二条是隐藏的：
  1. 控件被写在 `if (isServer)` 之后，而 **MUI2 的面板双端各构建一次、渲染的是客户端那棵树**，
     客户端恒为 false ⇒ 连接状态/连接按钮/两个列表在客户端**根本不存在**；
  2. 发信器枚举写错了 AE2 API：`Grid.getMachines()` 返回的是 **`IGridNode` 集合**
     （`IMachineSet extends IReadOnlyCollection<IGridNode>`），旧代码直接对节点做
     `instanceof PartLevelEmitter`，**永远匹配不到** ⇒ 列表恒为空。
  现在结构双端一致、变长列表经 `GenericListSyncHandler` + `DynamicSyncedWidget` 按服务端快照渲染
  （与 Nexus 自己的网络选择面板同款范式）、编辑值全部经 SyncValue 双向同步，
  并补上了 Nexus 缺失时的回退选择面板、取消了列表"只显示 5 行"的上限（改为可滚动列出全部）。

- **3.20.2 修正**：游戏内指南（GuideNH）有 4 个页面的图标一直显示不出来（日志里 4 条
  `Couldn't find icon item ae2_qof:...`）。原因是那些页面的 `icon:` / `item_ids:` 写的是**凭想象拼的名字**，
  而这些机器其实是 **GT 机器**——真实注册名是 `gregtech:gt.blockmachines` 加 meta（即 MTE ID），
  例如万能维护仓 = `gregtech:gt.blockmachines:32000`。本次把 5 个页面（含一个不报错的：AE2 切割刀页，
  真实名是 `appliedenergistics2:item.ToolCertusQuartzCuttingKnife`）中英各一份全部改成真实注册名，
  并把所有页面的 ID 与本模组注册名做了全量对照审计。

- **3.20.1 修正**：3.20.0 里可选依赖守卫把 PH 的 **modid** 误写成它的**包名前缀**（`proghatches`），
  于是守卫恒为 false——物品从未注册、NEI 与创造页都找不到，而且**一条日志都没有**。
  现已改为真实 modid `programmablehatches`，并追加「关键类可解析」第二道判据与「跳过」日志
  （此后无论走哪条分支都会打印一行，可直接定性）。

- **新增物品：编程样板输入总成 MK.III**（`Programmable Crafting Input Buffer MK.III`）——
  ProgrammableHatches「编程样板输入总成」的**扩容克隆版**：样板槽从 36 提到 **144**（4 倍），
  样板窗改为 **9 列 × 9 可见行的可滚动网格**（滚动覆盖全部 16 行），窗口停靠位置按屏幕尺寸裁剪。
  输入结构与 MK.II 一致（每缓冲 32 物品 + 32 流体，24 个彼此隔离的缓冲）。
- **只在安装了 ProgrammableHatches 时存在**：PH 类型全部隔离在 `ph/PhIntegration.register()` 的方法体里，
  入口第一句就是 `Loader.isModLoaded("proghatches")`。未装 PH 时该物品不注册、不进创造页，
  也不会加载任何 PH 类型（与 GuideNH 集成同一套做法）。
- **获取方式**：工作台有序合成（原总成 ×1 居中 + 4× 大师电路 + 4× 高级电路），
  同时出现在 AE2 QoL 创造标签页。
- **与本模组既有功能自动打通**：样板上传 / 撤回 / 接口终端都按 `IInterfaceViewable` 的
  `rows()*rowSize()` 取容量，144 槽无需改动那三处代码；内部类已注册进 AE2 的
  `InterfaceTerminalRegistry`（AE2 是按**精确类名**查表的，漏注册会导致接口终端与本模组样板终端都看不见它）。
- **存档兼容**：NBT 键与原版总成完全一致，两件物品可以互相拆装；把 >36 个样板换回原版总成时，
  第 37 格之后取不出（不会丢，数据仍在 NBT 里）。
- **技术要点**：PH 的容量**写死在 4 个数组长度里**（`javap -c` 可见它的两个构造器各 4 次 `bipush 36`），
  本版用一个接口式 accessor mixin（`mixin/ph/MixinPatternDualInputHatchAccess`）把这 4 个数组换成 144，
  **不改动 PH 本体**——PH 自己的总成 / MK.II / 仅物品版仍是 36 槽。

### 上一版 3.19.0-fix54（含 fix52 的最终修复）

- **「世界里键取物」在 GTNH 2.9.0-beta-3 实测通过**。根因不在本模组，也不在 AE2：
  整合包内的 **sciencenotleisure（SNL）** 在原版 `Minecraft.middleClickMouse()` 的 HEAD 注入并取消它
  （`ClientUtils.onBeforePickBlock` 在准星未瞄到实体时，跑完自己的 1000 格远程取物后无条件返回 `true`），
  于是 GTNHLib 的 `PickBlockEvent` 不会发出，**AE2 永远不会发送 `PacketPickBlock`**——
  挂在服务端包处理器上的兜底逻辑（fix52）因此从未被执行。
  现在由新增的 `client/PickBlockCompatHandler` **另取触发点**（Forge `InputEvent.MouseInputEvent`，
  与 SNL、与原版方法均无关），在条件与 AE2 `handlePickBlock()` 完全对齐的基础上补发该包；
  并**额外预检「身上有无线终端」**，避免没有终端的玩家每次中键都被服务端刷一条
  `PickBlockTerminalNotFound` 提示。
  **实测证据**：无存量 + 有样板 → 弹出「要合成多少个」（日志 `branch E` → `G7 界面已打开`）；
  物品到手后再按中键 → 正确放行原版（`branch B`）。
- 同版一并把 fix52 的网络线程归队、存量判定兜底修正纳入正式版；**诊断埋点已全部剥离**
  （失败分支收敛为「只记一次 WARN」，正常放行不记日志）。

### 上一版 fix51

- **修复「无限磁盘里的流体无法通过 IO 端口转移」**：AE2 的 IO 端口对每个元件**只取第一个**匹配的存储通道
  （`TileIOPort.getInv` 取到就 `break`），而本模组的无限磁盘是物品 + 流体多通道存储，
  于是流体通道从不进入搬运循环——物品照常搬运，所以不容易察觉。
  现在会对该磁盘**补搬其余通道**，物品与流体都能转出/转入；普通物品元件、ae2fc 流体元件
  与其它模组元件的行为完全不变（单通道元件立即跳过）。
  **需要实测确认**：流体转出与转入双向都通过、物品通道不受影响、普通流体元件对照一致。

### 上一版 fix50

- **修复万能维护仓「电路板槽放不进任何物品」**：GT 5.09.54 起为 MTE 新接通了一条物品校验链
  （`MTEItemStackHandler.isItemValid` → `MTEHatchMaintenance.func_94041_b` → `IsAutoMaintenanceInput(...)`），
  本仓因为恒以 `aAuto=false` 构造，该链对槽位 0 恒为 `false`，于是 MUI2 槽位控件拒绝一切物品
  （5.09.52 时该链尚未接通，所以 b1 上能放）。现只对电路板槽放行各电压电路板，其余行为不变。
  **需要实测确认**：各电压电路板可放入/取出、GUI 中 `max` 随电压变化、存读档保留、非电路板物品仍被拒。

### 上一版 fix49

- **依赖全量对齐 GTNH 2.9.0-beta-3 实机版本**：AE2 `rv3-beta-1050`、GT `5.09.54.133`、NEI `2.8.130`、
  AE2FC `1.5.106`、ModularUI2 `2.3.88`、GTNL `0.2.7-pre3`、ProgrammableHatches `0.2.0p24`、
  GuideNH `1.3.29`、NotEnoughEnergistics `1.7.41`、BetterQuesting `3.8.84`、ThaumicEnergistics `1.7.60`、Avaritia `1.99`。
  升级在编译期暴露并修复了 6 处 API 断裂（IO/检测器渲染基类、样板终端输出槽同步、接口后缀类型）。
  **旧依赖下"能跑"不等于已对齐，本轮才是真正的 b3 适配。**
- **fix48 · MTE ID 让位 + 旧存档自动迁移**：b3 新增的 fissionevolved 占用 32100/32101，本模组终端
  改为自适应电网 **32106**、库存统计 **32107**；新增存档读取期自动迁移，已摆放终端的频率/电压/子仓配对不丢失。
- **fix47 · 世界中键取物**：对着世界方块按中键时，背包与网络都没有该物品、但存在合成样板 → 直接打开
  「要合成多少个」界面；网络有存量仍按原生取物，两者都没有则维持原生无反应。
- **fix46 · 修复装材质包后机器透明**：根因是图集"只注册一次"开关与 `registerIcons()` 的 clear/重建语义冲突，
  已改为每次补注册并增加退化 UV 兜底。
- **fix45/fix44 · v7 材质接入（方案 B）**：用 Mixin 在原版方块图集 `registerIcons()` 尾部补注册 25 张 v7 路径，
  保留 8 台机器的 FRONT/TOP/SIDE 分面；不装材质包时完全回退 GT 默认外观。
- **fix43 · 库存检测覆盖板堆叠上限 1 → 64**，不改检测阈值、配置 NBT 或安装逻辑。

### 更早：fix42 Tooltip 修复

- 修复针对当前 Chromatic Tooltips 调用链的 AE 数量 / `Craft` 重复追加：通用 `handleTooltip` 透传，只由 `handleItemTooltip` 生成网络信息行。
- 移除工作区原有的“一秒内相同文本不再显示”方案，避免不同物品数量相同、快速切换时被跨提示抑制。
- **不改库存查询、流体识别、缓存有效期、数量格式、网络协议、Mixin 和依赖。** 原生 NEI 与当前 Chromatic 桥接共用同一入口。
- fix41 起客户端与服务端**必须同版本**（上传相关网络包字段有变化）。

原因、版本证据见 [调查方案](docs/mcp-tooltip-duplicate-investigation.md)；各轮构建、制品、验证范围及待实测项见 [交接文档](docs/AGENT_CHECKPOINT.md)。**构建成功不等于游戏实测通过；本轮不自动部署。**

## 安装与升级

1. 关闭游戏/服务器，备份**完整世界存档和配置**，保留可回退的旧 JAR。无限存储元件的数据在世界存档内，不能只备份物品 NBT。
2. 在与本项目匹配的 GTNH 环境中，用 `AE2-QoL-3.21.3.jar` 替换旧的 AE2 QoL JAR，不要同时保留多个版本。
3. **客户端与服务端使用同一版本**。fix41 改过上传相关网络包，不能只升级一端。
4. 本 JAR 已并入 `aeinfinitycell`（内置元数据版本仍为 `1.0.4-ae2qol`）；不要与原独立 AE2 Infinity Cell JAR 同时安装。迁移前备份，在副本存档中验证旧元件。
5. 首次启动后检查日志、配置生成与 Mixin 加载，再在测试存档中验证所用功能。开发协作中，向测试实例部署仍须单独授权。

### 环境与依赖

本项目使用 **GTNH 分支 API**，不是任意 Forge 1.7.10 装上 AE2/NEI 就能保证运行。完整声明见 [dependencies.gradle](dependencies.gradle) 与 [gradle.properties](gradle.properties)；`compileOnly` 不等于相关集成已经过缺模组启动验证。

| 组件 | 当前编译依据（fix49） | 对照实例 |
|---|---|---|
| Minecraft / Forge | 1.7.10 / 10.13.4.1614；MCP stable 12 | GTNH 2.9.0-beta-3 |
| AE2 Unofficial | rv3-beta-1050-GTNH | 同左 |
| AE2FluidCraft-Rework | 1.5.106-gtnh | 同左 |
| GregTech | 5.09.54.133 | 同左 |
| ModularUI2 | 2.3.88-1.7.10 | 同左 |
| NEI | 2.8.130-GTNH | 同左 |
| NotEnoughEnergistics | 1.7.41 | 同左 |
| GT Not Leisure | 0.2.7-pre3-dev-290 | 同左 |
| Programmable Hatches / Wireless Nexus | 0.2.0p24 / 1.0.2 | 同左 |
| BetterQuesting | 3.8.84-GTNH | 任务检测接口按 3.8.70 审查，编译用 3.8.84 |
| GuideNH | 1.3.29 | 同左 |
| Thaumic Energistics | 1.7.60-GTNH | 同左 |
| Avaritia | 1.99 | 同左 |
| StructureLib | 1.4.42 | 同左 |

fix49 之前本表停留在 beta-1 时代（AE2 977 / GT 5.09.52.594 / NEI 2.8.19 / MUI2 2.3.73 / NEE 1.7.14 / GTNL pre1 / PH 0.2.0p8 / BQ 3.8.70），
属"能编译但未对齐 b3"；现已全量升级，升级过程暴露并修复了 6 处 API 断裂。

另涉及 CodeChickenLib、Thaumcraft、Eternal Singularity 等。Chromatic Tooltips 不是本模组依赖，而是 F4/F5 的对照环境（fix42 核对版本为 Chromatic 1.0.29 / Compat 1.0.31 / NEI 2.8.101）。构建使用 Java 17 / Jabel，目标字节码为 JVM 8；实际游戏 Java 要遵循所用整合包的 lwjgl3ify/启动器配置。

## 配置与管理

主配置目录：`config/ae2_qof/`。

| 文件 | 作用 |
|---|---|
| `settings.json` | 传输倍率、智能倍增上限、NEI 显示、pin 默认行为、覆盖板数量预设 |
| `remembered_providers.json` | 上传时记住的配方 → 供应器关系 |
| `recipe_names.json` | 用户配方名称/目标映射；与 JAR 内默认映射配合使用 |

`settings.json` 的现有字段：

| 键 | 默认值 | 含义 |
|---|---|---|
| `io_port_rate` | `1024` | 强化 IO 传输倍率，1–2147483647；高倍率不等于无性能开销 |
| `smart_doubling_max_rounds` | `0` | 0 表示不设配置上限；仍受材料、能量和介质能力约束 |
| `nei_overlay_enabled` | `true` | NEI 网络信息显示开关；客户端本地偏好 |
| `pin_row_enabled` | `true` | pin 行默认行为；终端原生 Pins Rows 设置仍有效 |
| `stock_monitor_presets` | `1万=10000;100万=1000000;10亿=1000000000;清零=0` | 覆盖板快捷数量按钮，格式为“文字=非负数”，分号分隔 |

- `settings.json` 由相关调用路径检查文件时间，检查间隔至少 1 秒；**不是所有 JSON 保存后都保证一秒内同步至所有客户端**。
- `/ae2qof reload` 重载设置与配方名称映射；`/ae2qof status` 查看设置。服务端管理命令要求权限等级 2，不应假设所有单人/局域网玩家都自动拥有权限。
- 游戏内：Mods → AE2 QoL → Config。全局玩法参数经服务端校验权限；NEI 显示开关属于本地设置，不要把所有字段当作同一种同步机制。
- NEI 显示推荐用终端 **OV** 按钮；也保留 `/apu-overlay` 入口。专用服场景优先使用 OV。
- 修改配置后用状态命令、实际界面和日志确认结果；坏 JSON 或缓存状态问题不能仅凭文件保存判定成功。

## 功能与使用

以下编号与 [F1–F22 测试脚本](docs/SINGLEPLAYER_TEST_SCRIPT.md) 对齐，方便反馈问题。功能覆盖仍保留；风险与未关闭问题见文末。

### F1 · 样板上传、撤回与交换

标准/扩展样板终端提供 **↑ 上传、← 撤回、⇄ 交换、OV**。支持标准、GT 终极与 ae2fc 流体编码样板。

- 目标选择：唯一候选 → 记忆映射 → 手动选择；**不是保证首次就能自动识别所有 GT 配方池**。
- 选择界面按标题、搜索/翻页、机器列表、操作区、配方映射分区；显示位置与空闲槽，支持双击上传和“用选中机器”填入映射。
- fix41 使用维度、坐标、部件朝向定位供应器，补充失败回执与全部空槽的接受性检查。位置标识不保证目标拆换后仍是同一台机器；目标变化时应重新确认。
- 默认配方名称映射和 NEI 名称用于辅助搜索；不把历史映射条数当作全部模组覆盖保证。

### F2 · NEI 取物与下单

在支持的终端/无线网络上下文中：**Shift+左键**尝试提取一组到背包；**中键**打开合成数量确认。有可用样板但零库存也可请求合成。权限、网络连通性与背包容量仍由服务端处理。

对着**世界里的方块**按中键（AE2 原生取物）时同样适用：背包与网络都有没该物品、但存在合成样板时，会直接打开「要合成多少个」界面；网络有存量则仍按原生取到手上，两者都没有则维持原生无反应。前提是身上带有已绑定且在范围内的无线终端。

### F3 · 合成产物 pin 行

合成产物钉选到终端顶部独立行，显示网络总存量；随条目数量扩展（现有设计最多 3 行）。原生终端设置 **Pins Rows** 可调整或关闭，另有 `pin_row_enabled` 默认行为开关；不是覆盖在物品网格上的旧式展示条。

### F4 · NEI 网络 Tooltip

青色数量表示缓存中的网络库存，使用 K/M/G/T/P/E 缩写；绿色 `+` 和 `Craft` 表示可合成。仅有可合成状态时也能显示。

- 普通物品按物品库存显示 `AE`；**GT 流体展示物品、ae2fc 纯流体展示对象**按当前识别逻辑显示 `mB` 与流体名。
- **真实桶、单元等容器物品仍按物品库存查询**，不一概改成容器所装流体库存。
- 当前 Chromatic Compat 的纯流体上下文先转为 GT 流体展示物品，再走同一回调；fix42 不新增流体适配器。
- 数据来自终端更新的客户端缓存，并非每次悬停向服务器实时查询。关闭开关、缓存过期/无数据、无库存且不可合成时不追加。

### F5 · NEI 数量叠加

在 NEI 面板、书签及相关配方显示中绘制库存/可合成角标，与 Tooltip 共用缓存。角标渲染和 F4 的文字追加是不同路径；fix42 只调整后者的入口。

### F6 · 合成完成通知

向记录的下单玩家展示 AE2 风格横幅、产物与耗时，配合音效和队列。无需每次手动关注 CPU；取消、离线、流体产物等边界仍应按实测结果判断。

### F7 · 重新规划

合成确认界面的 **Replan / 重新规划** 对当前任务重新计算计划；不是修改机器的真实配方或强制完成订单。

### F8 · 强化 IO 端口

`ex_io_port` 复用 AE2 IO 端口机制，传输量按 `io_port_rate` 放大，默认 1024。大容量迁移先备份，并观察服务器 tick 开销。

### F9 · 无限水与岩浆磁盘

放入 ME 驱动器后通过创意单元式机制提供水与岩浆，展示量为极大有限值。具体配方以本实例 NEI 为准；不要把显示量理解为普通存储元件的持久库存。

### F10 · 无线 AE 收发器与连接器

同频道发送/接收收发器连接 ME 网络；连接器 **Shift+右键发送端**绑定频道，右键支持的 ME 设备连接/解除。包含频道管理、跨维度与方块高亮。仍依赖端点加载、网络状态和权限，不能把跨维度支持理解为自动加载所有区块。

### F11 · 石英切割刀复制名称

手持石英切割刀 **Shift+右键**支持的方块、AE 部件或 GT 机器，把目标名称写入刀名并复制到剪贴板；名称解析与库存同步边界见审查报告。

### F12 · F 键填充搜索

在支持的 AE2/ae2fc 界面，悬停物品按 **F** 将其名称填入搜索框；搜索框正在输入时不应抢占普通打字。

### F13 · NEI 显示开关

终端 **OV** 控制网络信息显示并保存本地设置。与服务器玩法倍率分离；多人玩家各自选择显示偏好。

### F14 · 智能倍增

在支持的 ME 接口、GT/GTNL 样板仓与 Programmable Hatches 介质启用后，尝试一次推送多轮加工材料。配置 0 只取消配置上限，材料、电量、缓冲和介质模拟仍限制实际轮数。

保留假合成、阻塞、滞留物品等回退条件。**多轮推料不等于机器并行或超频**；跨介质缓冲与记账仍有待处理的静态审查项，不作“绝不丢物/超产”的保证。

### F15 · 样板与接口二合一终端

方块、线缆面板和无线手持三种形态，共用样板编辑与接口列表：

- 合成 3×3 / 处理分页网格；编码、清空、倍率、替代/备份替代与反转。
- 上传、召回、主副产物交换、OV；满足条件时提供 GTNL 装配矩阵 AM 入口。
- 样板回读、编辑快照、GT/ae2fc 流体表示及 PH 编程工具箱联动。
- 中键数量编辑、Shift+中键命名等快捷操作；允许范围取决于槽位与编码表示，**不承诺所有数量无限大**。
- 无线形态通过 ME 安全终端绑定，支持跨维度，权限与绑定有效性仍须检查。

### F16 · ME 任务检测器

按绑定玩家/队伍的 BetterQuesting 进度检查 ME 库存，支持非消耗检索类任务；跳过消耗型提交，不把“网络中可见”当作消费。断网、玩家离线等情况受检测条件约束；NBT 变体与绑定持久化见审查报告。

### F17 · 无限存储元件

并入 dancing snow 的 AE2 Infinity Cell，提供物品、流体、源质存储；**物品保存 UUID，实际内容位于世界存档，复制元件会共享同一后端库存**。

可用 NEI `U` 查看分页内容，悬停显示统计，Ctrl 切换科学计数。AppEU 能量通道未包含。保存失败/迁移有开放审查项，升级与大规模迁移务必先备份；不承诺旧存档零风险迁移。

### F18 · 万能维护仓

提供无线 EU、维护相关行为与电路板并行映射。**当前维护绕过是全局 Mixin 行为，不是只有装了本仓的机器才受影响。** 无线 EU 需要账户已有能源；电路板映射、跨配方扣料与输出合并属于需重点回归的逻辑。

### F19 · GT 无线 EU

无线输出仓 **32110** 向无线 EU 账户供能；无线输入仓 **32111** 取能。与无线 AE 物品/流体网络是两套机制，不是凭空发电，也不等同于 Tesla Tower。

### F20 · 自适应电网

终端 **32106** 提供五页：状态、设置、频率、监控、子仓列表。配套输入仓 **32102**、激光源 **32103**、动力仓 **32104**、激光靶 **32105**。

网络数据棒 **Shift+右键终端**写入配置 → 右键仓室绑定 → 右键终端读取。支持团队网络、位置高亮/有权限的传送、主机名称识别和统计。监控值受采样与统计口径影响，不应作为资源守恒已被证明的依据。

### F21 · 库存检测覆盖板

通过邻接 AE 或 Nexus 无线绑定查询物品/流体，按阈值与模式输出控制信号；提供标记槽、快捷数量预设及 GUI 状态同步。适用安装面、机器控制行为与无线状态以实际环境为准。

### F22 · 库存统计终端

GT 单方块信息终端（ID **32107**），设计用于集中查看/编辑 AE 标准发信器与本模组库存覆盖板，支持阈值管理、状态查看和定位。**双端 UI、发信器枚举、远程编辑权限仍有开放审查项**，不能仅凭已注册或构建通过认定全部功能可用。

## 已知问题与验证边界

[fix41 全功能审查](docs/mcp-full-function-audit-fix41.md) 的 A01–A19 是静态审查结论，不等于每项已在游戏复现，也**没有因后续任何一轮修复而关闭**。重点包括无限元件保存/迁移、跨配方守恒、无线 EU、智能倍增、库存终端、供应器列表预算与 NBT 身份等。

fix44 之后的所有改动（v7 材质、世界中键取物、MTE ID 迁移、b3 依赖升级）**都只有"编译通过 + 制品核对"，没有游戏内验收记录**，未测项一律不视为通过。其中 A13/A14/A15 相关的代码方向已落地（`util/ItemIdentity` 精确物品身份），但审查报告状态列未回填，也未经实测。

反馈请附：两端 JAR 版本、整合包与相关模组版本、GUI/物品或流体、操作步骤、预期/实际结果、日志与截图。Tooltip 回归需覆盖库存/可合成组合、快速切换、流体展示、普通容器以及原生 NEI/Chromatic 两条路径。

## 构建与开发

使用匹配依赖、仓库本地 `libs/` 和已有 Gradle 缓存。离线首次构建缺依赖时应补齐准确版本，不能为让构建通过随意升级依赖。

```powershell
$env:JAVA_HOME = 'E:\java17'
$env:GRADLE_USER_HOME = 'C:\Users\29357\.gradle'
.\gradlew.bat build --offline -x spotlessJavaCheck -x spotlessCheck
```

fix49 轮次 Java17 离线构建成功（`compileJava` 与 `build` 均 `BUILD SUCCESSFUL`，并已解包核对新 Mixin 入包与成员重混淆）；
fix42 的独立脚本 [tooltip-fix42-regression.sh](docs/tooltip-fix42-regression.sh) 59 项断言通过（依赖桩，不是游戏集成测试），可用 `JAVA_HOME=/e/java17 bash docs/tooltip-fix42-regression.sh` 复跑；Gradle `test` 本身为 `NO-SOURCE`。

其他环境调整路径后使用 `./gradlew`。这是本项目当前 Java17 验证命令，**显式跳过 Spotless，不代表格式检查已通过**。Jabel 允许现代语法并输出 JVM 8 字节码。构建输出在 `build/libs/`；测试是否实际执行及制品校验值以交接记录为准。

修改前先读 [交接状态](docs/AGENT_CHECKPOINT.md)、[开发指南](docs/GTNH-开发指南.md)、[构建与代码参考](docs/GTNH-构建与代码参考.md) 和 [代码风格](docs/GTNH-代码风格.md)。不要套用现代 Minecraft API；不要重新加入 RFB `childDelegations` 干预。

## 文档索引

| 文档 | 内容 |
|---|---|
| [CHANGELOG.md](CHANGELOG.md) | 根目录版本日志与历史修复记录 |
| [AGENT_CHECKPOINT.md](docs/AGENT_CHECKPOINT.md) | 最新实施状态、构建产物、未验证项与交接动作 |
| [Tooltip 调查方案](docs/mcp-tooltip-duplicate-investigation.md) | fix42 前的精确版本、回调链与方案依据 |
| [全功能审查](docs/mcp-full-function-audit-fix41.md) | F1–F22 映射、A01–A19 与风险优先级 |
| [MOD_MAP.md](docs/MOD_MAP.md) | 功能与入口定位；历史映射仍需对照源码 |
| [单机测试脚本](docs/SINGLEPLAYER_TEST_SCRIPT.md) | 历史 F1–F22 用例，不是本版通过证明 |
| [Mixin 笔记](docs/mixin_notes.md) | 注入与兼容性注意事项 |
| [CREDITS.md](CREDITS.md) | 代码、材质来源及许可记录 |

## 致谢与许可说明

本项目改编自 **GaLicn 的 [AE2-Auto-Pattern-Upload](https://github.com/GaLicn/AE2-Auto-Pattern-Upload/)**，保留对原上传与 F 键搜索实现的感谢。

- **GTNH 团队 / Applied Energistics 2、NEI、AE2FluidCraft**：核心 API、终端、流体与配方生态。
- **小飘（mynamexiaopiao）**：AE-Wireless-Transceiver 代码；**麦淇淋（@麦淇淋）**：无线方块、连接器与 GUI 美术。借用许可记录见 CREDITS。
- **dancing snow（DancingSnow0517）**：AE2 Infinity Cell；原 MIT 文本随 JAR 保留。
- **asdflj / AE2Things**：相关概念参考；强化 IO 贴图来源是 **AE2 原版 BlockIOPort**，不是 AE2Things。
- **Waila、GT5-Unofficial、GT-Not-Leisure、GTLCore、Programmable-Hatches、ExtendedAE_Plus、ExampleMod1.7.10** 及其他被参考项目。

AE2 来源材质包含非商业、署名与相同方式共享要求。个人存档声明不替代第三方许可；任何公开发布前须复核源码、材质、原作者授权及许可证，不能由本 README 推导出统一的再分发授权。
