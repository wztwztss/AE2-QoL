# AE2 QoL

> **简体中文** | [English](README.en.md)

为 **Minecraft 1.7.10 / GT New Horizons** 开发的 AE2 效率增强模组：NEI 样板上传、库存与合成状态提示、二合一终端、无线 AE 连接，以及 GT 仓室、电网与库存监控工具。

**作者：wztwzt · 当前源码版本：3.19.0-fix48 · 对照整合包：GTNH 2.9.0-beta-3**

本仓库用于个人存档，暂不开放分发。来源、署名与许可记录见 [CREDITS.md](CREDITS.md)。功能说明不代表所有兼容组合均已通过实机测试。

## 本版变化：fix43

- 库存检测覆盖板物品堆叠上限由 **1 改为 64**；不改检测阈值、配置 NBT 或安装逻辑。带不同 NBT 配置的覆盖板仍不能混堆。
- 保留 fix42 的 Tooltip 修复；构建与验收状态见交接文档。

### 上一版 fix42：Tooltip 修复

- 修复针对当前 Chromatic Tooltips 调用链的 AE 数量 / `Craft` 重复追加：通用 `handleTooltip` 透传，只由 `handleItemTooltip` 生成网络信息行。
- 移除工作区原有的“一秒内相同文本不再显示”方案，避免不同物品数量相同、快速切换时被跨提示抑制。
- **不改库存查询、流体识别、缓存有效期、数量格式、网络协议、Mixin 和依赖。** 原生 NEI 与当前 Chromatic 桥接共用同一入口。
- 已核对的桥接版本：**Chromatic Tooltips 1.0.29 / Compat 1.0.31 / NEI 2.8.101-GTNH**。不据此承诺其他版本或所有 GUI 路径。

原因、版本证据见 [调查方案](docs/mcp-tooltip-duplicate-investigation.md)；本轮构建、制品、验证范围及待实测项见 [交接文档](docs/AGENT_CHECKPOINT.md)。**构建成功不等于游戏实测通过；本轮不自动部署。**

## 安装与升级

1. 关闭游戏/服务器，备份**完整世界存档和配置**，保留可回退的旧 JAR。无限存储元件的数据在世界存档内，不能只备份物品 NBT。
2. 在与本项目匹配的 GTNH 环境中，用 `AE2-QoL-3.19.0-fix48.jar` 替换旧的 AE2 QoL JAR，不要同时保留多个版本。
3. **客户端与服务端使用同一版本**。fix41 改过上传相关网络包，不能只升级一端。
4. 本 JAR 已并入 `aeinfinitycell`（内置元数据版本仍为 `1.0.4-ae2qol`）；不要与原独立 AE2 Infinity Cell JAR 同时安装。迁移前备份，在副本存档中验证旧元件。
5. 首次启动后检查日志、配置生成与 Mixin 加载，再在测试存档中验证所用功能。开发协作中，向测试实例部署仍须单独授权。

### 环境与依赖

本项目使用 **GTNH 分支 API**，不是任意 Forge 1.7.10 装上 AE2/NEI 就能保证运行。完整声明见 [dependencies.gradle](dependencies.gradle) 与 [gradle.properties](gradle.properties)；`compileOnly` 不等于相关集成已经过缺模组启动验证。

| 组件 | 当前编译依据 | 本轮对照实例 |
|---|---|---|
| Minecraft / Forge | 1.7.10 / 10.13.4.1614；MCP stable 12 | GTNH 2.9.0-beta-3 |
| AE2 Unofficial | rv3-beta-977-GTNH | 同左 |
| AE2FluidCraft-Rework | 1.5.88-gtnh | 同左 |
| GregTech | 5.09.52.594 | 同左 |
| ModularUI2 | 2.3.73-1.7.10 | 同左 |
| NEI | 2.8.19-GTNH | **2.8.101-GTNH** |
| NotEnoughEnergistics | 1.7.14 | **1.7.30** |
| GT Not Leisure | 0.2.7-pre1-dev-290 | **0.2.7-pre2** |
| Programmable Hatches / Wireless Nexus | 0.2.0p8 / 1.0.2 | 同左 |
| BetterQuesting | 3.8.70-GTNH | 任务检测接口按此版本审查 |

另涉及 CodeChickenLib、StructureLib、Thaumic Energistics、Thaumcraft、Avaritia、Eternal Singularity、GuideNH 等。Chromatic Tooltips 不是本次新增依赖。构建使用 Java 17 / Jabel，目标字节码为 JVM 8；实际游戏 Java 要遵循所用整合包的 lwjgl3ify/启动器配置。

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

[fix41 全功能审查](docs/mcp-full-function-audit-fix41.md) 的 A01–A19 是静态审查结论，不等于每项已在游戏复现，也**没有因 fix42 Tooltip 修复而关闭**。重点包括无限元件保存/迁移、跨配方守恒、无线 EU、智能倍增、库存终端、供应器列表预算与 NBT 身份等。

反馈请附：两端 JAR 版本、整合包与相关模组版本、GUI/物品或流体、操作步骤、预期/实际结果、日志与截图。Tooltip 回归需覆盖库存/可合成组合、快速切换、流体展示、普通容器以及原生 NEI/Chromatic 两条路径。

## 构建与开发

使用匹配依赖、仓库本地 `libs/` 和已有 Gradle 缓存。离线首次构建缺依赖时应补齐准确版本，不能为让构建通过随意升级依赖。

```powershell
$env:JAVA_HOME = 'E:\java17'
$env:GRADLE_USER_HOME = 'C:\Users\29357\.gradle'
.\gradlew.bat build --offline -x spotlessJavaCheck -x spotlessCheck
```

fix42 轮次 Java17 构建成功；独立脚本 [tooltip-fix42-regression.sh](docs/tooltip-fix42-regression.sh) 的 59 项断言通过（依赖桩，不是游戏集成测试）。可用 `JAVA_HOME=/e/java17 bash docs/tooltip-fix42-regression.sh` 复跑；Gradle `test` 本身为 `NO-SOURCE`。

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
