# AE2 QoL fix41 全功能代码调查与优化审查

> 日期：2026-09-18。性质：只研究、静态审查与版本匹配复核，不实施修复。
> 源码基线：`master` / `ffe946ae18b8287731f27863dc9018991a1d8ef1`，`3.19.0-fix41`。
> 本报告中的“确认”指代码控制流或匹配版本 API 可以证明；**不代表已经进入游戏复现**。

## 1. 结论摘要

本轮按现有测试脚本的 **F1–F22 全部功能**建立代码映射，检查功能入口、网络处理、存储/能量记账、关键 Mixin、GUI 同步和生命周期。源码清单共 **203 个 Java 文件、33,849 行**，已完整取得当前快照并进行结构检索；深度阅读集中在各功能的关键链路，**不是宣称 203 个文件每一行都经过形式验证**。

建议下一轮优先顺序：

1. **存档与资源守恒**：无限元件保存/迁移失败（A01）；GT 跨配方扣料与输出合并（A02、A03）；无线 EU 缓冲与余额记账（A04）；智能倍增跨供应器缓冲复用（A05）。
2. **功能恢复与权限边界**：F22 双端 UI、AE2 节点遍历、远程编辑鉴权（A06–A08）；无线链路遍历时修改集合（A09）。
3. **数据正确性与规模稳定性**：供应器列表协议预算、缓存和过滤（A11、A12）；滚轮替换（A13）；NBT 身份、绑定持久化、统计口径（A14–A17）。
4. **性能及维护性**：流体库存查询、注册表写脏、反射适配、UI 分页、日志与版本约束。先测量，再优化，不应以大改架构代替上述正确性修复。

没有修改源码、资源、依赖、运行实例或 Git 提交。没有执行本轮 Gradle 编译、游戏启动、单人/专用服实测或性能压测；历史构建与玩家测试结果仅作线索。仓库唯一预期新增文件是本报告。

## 2. 先确认环境，避免用其他版本误判

### 2.1 实际构建与运行差异

| 项目 | 当前构建/源码 | 检查到的测试实例 | 审查意义 |
|---|---|---|---|
| Minecraft / Forge / mappings | 1.7.10 / 10.13.4.1614 / stable 12 | GTNH 2.9.0-beta-1 的 1.7.10 实例 | 不能套用现代 Forge、Capability、NeoForge 生命周期 |
| Java | `/e/java17/bin/java`：17.0.19；项目启用 Jabel，现代语法目标 JVM 8 | 实例为 Java 17–25 配置体系，存在 lwjgl3ify | `var`、模式匹配等语法不是天然编译错误；运行 JDK 与字节码目标要分开 |
| 本模组 | **3.19.0-fix41** | **3.19.0-fix39** | 旧实例表现不能证明 fix41 已生效或仍有相同错误 |
| AE2 Unofficial | **rv3-beta-977-GTNH** | **rv3-beta-977-GTNH** | 本轮依照 977，不以 997/master 的实现代替 |
| GregTech | 本地 JAR **5.09.52.594** | **5.09.52.594** | ProcessingLogic、ParallelHelper、无线 EU 管理器已用该 JAR 核对 |
| AE2FluidCraft | 1.5.88 | 1.5.88 | 原生流体、展示物品与流体样板转换仍需按这一代 API 处理 |
| ModularUI2 | **2.3.73-1.7.10** | **2.3.73-1.7.10**，另有 MUI1 1.3.4 | 本地参考源码 2.3.87 不是证明 2.3.73 行为的依据 |
| NEI | **2.8.19-GTNH** | **2.8.101-GTNH** | 点击、tooltip、渲染注入必须覆盖两个版本的差异 |
| NotEnoughEnergistics | 本地 1.7.14 | 1.7.30 | 不能只看旧编译接口宣称新实例所有 GUI 路径兼容 |
| GT Not Leisure | 本地 0.2.7-pre1-dev-290 | 0.2.7-pre2 | 超级样板输入总成的 Mixin/UI 需要运行版本复测 |
| Programmable Hatches / Nexus | 0.2.0p8 / 1.0.2 | 0.2.0p8 / 1.0.2 | PH 多推送与 Nexus 绑定不要混用其他分支契约 |
| BetterQuesting | 3.8.70-GTNH | 本轮关键契约按 3.8.70 匹配源码复核 | consume 任务的处理见排除项 |
| 加载环境 | 项目 Mixin 配置 | unimixins 0.3.1、lwjgl3ify 3.0.23 | 不得重新引入已移除的 RFB childDelegations 干预 |

构建证据：`gradle.properties`、`dependencies.gradle`、`build.gradle.kts`、`src/main/resources/mixins.ae2_qof.json`、`src/main/resources/mcmod.info`。运行版本来自实例 `mods` 文件清单，不等同于本轮启动日志或实际运行时类加载证明。

### 2.2 本轮实际使用的版本匹配证据

- AE2：`E:/wzt/MC/modcreater/reference_src/Applied-Energistics-2-Unofficial-rv3-beta-977-GTNH/src/main/java`。
  - 核对 `IMachineSet`、`AEFluidStack`、`ContainerPatternTerm`、`SlotRestrictedInput`、`ContainerCraftConfirm`、`GuiMEMonitorable` 等。
- GT：只读执行 `/e/java17/bin/javap -c -p -classpath libs/gregtech-5.09.52.594.jar ...`，核对 `ProcessingLogic.process/createParallelHelper`、`ParallelHelper` 消耗行为、`WirelessNetworkManager.addEUToGlobalEnergyMap` 和基类设置入口。
- MUI2：只读反汇编 `libs/modularui2-2.3.73-1.7.10.jar` 的 `GuiManager`；服务端开 GUI 与客户端开 GUI 都调用 factory 的 `createPanel` 并收集同步值。
- BQ：`E:/wzt/MC/modcreater/reference_src/BetterQuesting-3.8.70-GTNH`；核对 `TaskRetrieval.retrieveItems`、`TaskFluid.retrieveItems` 与 `Detector` 的 NBT 比较。
- 已阅读的项目约束与历史材料：根目录说明及 `docs/GTNH-代码风格.md`、`docs/GTNH-构建与代码参考.md`、`docs/GTNH-开发指南.md`、`docs/GTNH-迁移移植指南.md`、`docs/GTNH-FAQ.md`、`docs/mixin_notes.md`、`docs/MOD_MAP.md`、`docs/SINGLEPLAYER_TEST_SCRIPT.md`、`docs/STATIC_AUDIT_ISSUES.md`、`docs/AGENT_CHECKPOINT.md`。
- 新版或 master 参考树仅作为定位线索；本轮不以其行为替代精确版本证据，也没有为凑外部资料使用不相干的新版本网页。

本地 JAR SHA-256：

```text
libs/gregtech-5.09.52.594.jar
08ff758e377c926d76e39b018340fd639408cbd7e2f5fffd2538a46df945c1fa

libs/modularui2-2.3.73-1.7.10.jar
4bdd6db8eb48dc54aaa936a53129d49a5fed3a72aa861e8925236b6377bc8233
```

### 2.3 适用于本项目的审查规则

- 保留 FML `SimpleNetworkWrapper`、`ByteBufUtils`、旧 `ItemStack.stackSize`、`ForgeDirection`、GT MTE、AE2 Grid/Part/Container 等正确的 1.7.10 体系，不建议迁移现代版本 API。
- 世界、容器、网络库存变更归队服务端线程；已有 `ServerTerminalHelper.scheduleServerTask` 不能被“简化”为 Netty 回调直接写世界。
- GUI 上的范围限制不等于 C2S 服务端校验；权限应针对本次操作的目标、玩家与当前会话。
- `SIMULATE` 只作模拟；真实扣料、写库存、能量扣账与产物/等待量必须守恒。饱和截断可以用于显示，不能静默损失真实物品或流体。
- MUI2 双端构建与同步 ID 是契约；服务端回调不意味着服务端独自构建整个可见控件树。
- HEAD + cancel 保留上游字节码不等于保留其他模组注入的运行效果；取消之后，下游注入可能不会执行。
- 不根据原版 S3F 或其他 Forge 版本直接断言本实例“任何负载必限 32767”。本报告 A11 证明的是**代码自身的 32000 字节预算算法与实际编码不一致**，最终 FML/GTNH 传输上限另测。

## 3. 全功能覆盖矩阵

本报告源码路径缩写：`Q/` = `src/main/java/com/wztwzt/ae2_qof/`，`I/` = `src/main/java/cn/dancingsnow/aeinfinitycell/`。各项是本轮入口及关键路径审查结果，不是游戏测试通过表。

| 功能 | 主要入口/链路 | 本轮结论与优化方向 |
|---|---|---|
| F1 NEI 样板上传/撤回/交换/OV | `Q/client/gui/GuiProviderSelect.java`；`Q/network/{RequestProvidersListPacket,ProvidersListS2CPacket,UploadPatternPacket,RecallPatternPacket,SwapPatternPacket}.java`；`Q/util/ProviderLocator.java`；`Q/ClientProxy.java` | 稳定位置键、上传失败回执、专属样板库存值得保留。发现 A11/A12；撤回仍依赖会话内 ID，后续可统一目标定位协议，不能凭 ID 持久保存 |
| F2 NEI 取物/下单 | `Q/mixin/nei/MixinPanelWidgetClick.java` → `Q/network/{ExtractItemPacket,RequestCraftingPacket,ServerTerminalHelper}.java` | 已有服务端归队、精确库存查询、模拟容量和提取。建议统一无线终端解析结果与槽位，不反复独立选择；退款必须检查实际剩余，不以 catch 静默兜底证明守恒 |
| F3 合成 pin 行 | `Q/mixin/ae/MixinPinsHolder.java`；`Q/mixin/nei/MixinGuiMEMonitorable.java` | 使用 977 原生 pin，不必另造覆盖层。保留用户 DISABLED；以真实行列/配置上限回归自动扩展，不把硬编码 27 格立即断言为当前必现错误 |
| F4 NEI tooltip | `Q/client/nei/NetworkTooltipHandler.java`；`Q/mixin/nei/MixinNEIRecipeWidget.java`；`Q/client/NetworkInventoryCache.java` | A14：NBT 身份不足。与 chromatictooltipscompat、NEI 运行版本的取消顺序要实测；不能重复报告历史“无效果”已被复现 |
| F5 NEI 数量叠加 | `Q/client/NetworkInventoryDrawHandler.java`；`Q/mixin/nei/MixinPanelWidgetDraw.java` | 与 F4 共用 A14。建议把数量、可合成状态、单位分离绘制，减少每帧 NBT/字符串工作，压测高缩放和大书签页 |
| F6 合成完成通知 | `Q/mixin/ae/MixinCraftingCPUCluster.java:155-247` → `Q/network/CraftingCompletePacket.java`、客户端处理 | 已见“直接通知下单玩家”回退，不能再声称必须携带无线终端。建议用 UUID/生命周期管理代替长持有玩家实体，验证退出、取消、失败、CPU 重载；流体产物当前捕获路径不等同于物品路径 |
| F7 重新规划 | `Q/network/ReplanPacket.java` → `Q/util/Replanner.java` → 977 `ContainerCraftConfirm` | 只对已完成 CraftingJobV2 开始新 Future。建议验证启动新 job 失败时旧 UI 状态恢复、重复点击和关闭取消；反射失败应有一次性可诊断提示 |
| F8 强化 IO 端口 | `Q/tile/TileExIOPort.java`；`Q/mixin/ae/MixinTileIOPort.java` | 倍率乘法已有 long 溢出保护且只作用于强化类；无需按现代 API 重写。吞吐上限/每 tick 预算需压测，极高倍率不等于低 tick 开销 |
| F9 无限水岩浆磁盘 | `Q/item/ItemInfinityWaterLavaCell.java` | 复用 977 CreativeCellInventory/原生流体通道；不按“凭空产水”报漏洞，这是设计功能。建议澄清“Long 上限”与实际 creative 展示量的注释差异、编辑语义与待机耗能 |
| F10 无线收发器/连接器 | `Q/wireless/{TileWirelessTransceiver,ItemWirelessConnector,WirelessData,WirelessWorldData,WirelessLinkManager}.java`；`Q/wireless/link/WirelessBlockLinkManager.java` | A09/A10：集合修改、调度放大与临时离线被删除。权限方面需覆盖物品右键路径而不只检查 GUI 包；区分卸载、拆除、换频、断连 |
| F11 石英刀复制名称 | `Q/client/event/KnifeNameCopyHandler.java` | 检查到名称解析、刀显示名与剪贴板路径。建议缓存可选反射，使用翻译键；客户端刀名不自动等于服务端持久化，回归一次库存同步后的显示 |
| F12 F 键搜索 | `Q/client/event/KeyInputHandler.java` | 已避免搜索框有焦点时吞 F。可改为可配置 KeyBinding；缓存按 GUI 类解析的搜索字段，保留输入框打字与 NEI 快捷键兼容 |
| F13 叠加层开关 | `Q/client/OverlayConfig.java`；`Q/mixin/nei/MixinGuiOverlayButton.java`；`Q/Config.java`；配置网络包 | 区分本地视觉偏好和服务端玩法设置。建议合并配置来源、原子保存、坏 JSON 不推进成功状态；不因存在 JSON 热加载而全盘替换配置系统 |
| F14 智能倍增 | `Q/mixin/ae/{MixinCraftingCPUCluster,MixinDualityInterface,MixinContainerInterface}.java`；GT/GTNL/PH GUI Mixin；`Q/network/SmartDoublingTogglePacket.java` | A05：跨介质复用缓冲与局部倍数分离。批量记账/原料、电量钳制值得保留；异常时直接回退原版和外部注入兼容属于高风险复测点 |
| F15 二合一终端三形态 | `Q/merged/{ContainerMergedTerminal,PatternContainer,GuiMergedTerminal,MergedGuiHandler}.java`；part/wireless 子包；MergedTerminal 系列网络包 | A13：滚轮候选循环。保留虚拟槽限制、编码输出槽 1 张上限、矩阵递归保护。建议统一流体展示/编码数量转换，做“大于 int 的展示→编辑→编码”往返测试，不靠饱和掩盖真实数值错误 |
| F16 ME 任务检测 | `Q/tile/TileQuestDetector.java` → `Q/quest/QuestDetectLogic.java` → BQ 检索接口 | A15：真实库存候选去重丢失 NBT 变体。已有按需求查询和离线缓存清理；BQ 消耗任务跳过已复核，不能误报免费提交 consume 任务 |
| F17 无限存储元件 | `I/ae/`、`I/storage/`、`I/nei/`；`Q/network/InfinityCell*Packet.java`；客户端预览缓存 | A01：失败写盘与迁移提交。BigInteger 真值、AE long 展示分层应保留；服务端预览路径已存在，不能照抄旧 U 键缺陷为本轮复现 |
| F18 万能维护仓 | `Q/hatch/AE2MaintenanceHatchUniversal.java`；`Q/mixin/gt/{MixinMTEMultiBlockBase,MixinProcessingLogicSpeed}.java` | A02/A03/A19。全局关闭维护是现实现的明确语义，是否改成“装仓才生效”属于产品决策，不宜擅改；GT MTE 注册保持 init 阶段 |
| F19 GT 无线 EU | `Q/hatch/wireless/{WirelessEnergyInputTerminal,WirelessEnergyOutputTerminal}.java`；`Q/item/ItemNetworkDataStick.java` | A04/A16：能量记账和重载改绑。UI 双端读 GT 全局表、WAILA 超大 BigInteger 转 long 也应检查，但本轮未据此声称专用服必崩 |
| F20 自适应电网 | `Q/hatch/adaptive/`；`Q/network/{HatchActionPacket,HatchListSyncPacket}.java` | A04/A17：共享余额/缓冲守恒和统计口径。团队校验、activeViewer、卸载清理有现成机制；列表 index 后续改稳定坐标/类型标识，避免刷新后指向改变 |
| F21 库存覆盖板 | `Q/cover/stockmonitor/` 与 `ae/`、`gui/` | A18：流体全表扫描；每 10 tick 全局 upsert 可按变化写脏。OR、多槽、断网停机为现有语义；需区分用户手动停机与覆盖板控制，不擅改产品行为 |
| F22 库存统计终端 | `Q/terminal/{StockMonitorTerminal,StockMonitorTerminalGui,CoverRegistry}.java` | A06/A07/A08 是核心；另有固定前 5 项、仅编辑 cover 槽 0、选择状态未清理等完整性问题，见优化章节 |

额外基础设施已纳入：初始化/注册与 MTE ID、可选集成加载、Mixin 目标及取消影响、服务端调度、配置与命令、缓存清理、NBT 与网络编解码、双端资源/指南映射。贴图美术、所有翻译键呈现、第三方全部机器子类与完整玩家操作组合未逐一实测。

## 4. 具体问题与建议

级别：**P1** = 资源/存档守恒、权限、崩溃或关键功能阻断；**P2** = 明显功能错误、持久化/显示错误、规模稳定性；**P3** = 性能与维护性机会。证据标签：**S** = 当前源码静态证据；**V** = 已用匹配依赖复核；**R** = 具体运行触发、影响或可利用性仍需复测。

### A01 · P1 · F17 保存失败会丢失重试状态，迁移可能删除尚未成功转换的旧数据【S】

位置：`I/storage/InfinityCellStorage.java:47-65,78-100`；`I/storage/InfinityCellDataAccess.java:50-69`；`I/CommonProxy.java:63-69`。

- `saveAll` 先复制 dirty，再 `dirty.clear()`，随后逐项保存；`saveToDisk` 捕获 IOException 只记录日志，没有返回失败或重新置脏。
- 临时磁盘失败后，无新变更的记录不再重试。停止服务器随后清空 cache，未落盘的内存状态消失。
- `migrateLegacy` 只要 `migrated > 0`，就调用不返回成功状态的 `saveAll()`，继而 `deleteLegacyFile()`。不能证明所有新文件写成功，便删除旧格式数据文件。
- 读盘异常也返回新空记录；若后续当作正常空元件使用并保存，有覆盖原数据的风险。已有 `safeWrite` 应保留，但它不能代替失败状态管理。

建议：逐记录成功确认后清 dirty；失败保持可重试；迁移建立完成清单并验证所有目标记录后才备份/清理旧文件；区分“不存在”和“读取失败”，后者进入可见错误/只读状态。停止阶段保留失败信息，不静默声明已保存。

回归：只读目录/磁盘写失败、部分 UUID 转换失败、损坏单文件、重启恢复；对照每个通道 BigInteger 真值。**本轮没有对真实存档制造故障。**

### A02 · P1 · F18 跨配方替代了原生校验链，扣料后还可能返回失败【S+V，后果 R】

位置：`Q/mixin/gt/MixinProcessingLogicSpeed.java:89-108,125-173,197-232`。

GT 5.09.52.594 的 `process()` 会求值 parallel/EU/speed suppliers、处理配方锁与双输入缓存，并经 `validateAndCalculateRecipe` 调用 `validateRecipe`、helper、`applyRecipe`。当前 threads > 1 在 HEAD 直接 setReturnValue，改走自写路径，未等价保留这些步骤；也未在每个 recipe 前调用原生 `validateRecipe`。

匹配 JAR 证明 `createParallelHelper` 默认设置 `setConsumption(true)`，helper build 会调用输入消费函数。当前 `Arrays.copyOf(ItemStack[]/FluidStack[])` 是浅复制：只是数组新建，元素仍共享。build 后仍有总功率、时长/聚合溢出等失败出口，已消耗资源没有事务回滚。catch 返回 NO_RECIPE 后外层已取消原方法，注释所说“回退原流程”并未发生。

建议：先明确必须支持哪些机器/配方锁/催化剂/批处理契约，再选择有限兼容或受控适配；使用共享的事务工作集试算多配方，所有校验与总输出容量通过后再一次提交真实扣料。**不能只把浅复制换成深复制**：若不提交真实消耗，会产生免费产物。

回归：有专属验证条件的机器、锁定配方、催化剂、双输入缓存、EU/speed supplier、失败注入、部分配方成功后整体失败。每次比较输入与输出账本。

### A03 · P1 · F18 聚合输出在合并前按类型数截断，饱和也会丢真值【S】

位置：同文件 `175-193,236-257`。

列表大小达到 machine output limit 后就 break，尚未检查新输出能否合入已有同类型。例如输出上限为 1，配方 A 已输出 X，配方 B 也输出 X；B 的 X 在合并前就被跳过。不同种类超上限时也直接丢弃，而不是作为整体输出保护/减少并行的条件。合并单栈超过 int 后再饱和至 INT_MAX，同样丢失超出数量。

建议：先按物品/NBT、流体/tag 精确聚合为长整型工作结果，再按机器契约拆分/限制并行；容量不足应在消费输入之前拒绝或降并行，不把 `break` 或饱和当“安全保护”。helper 对单配方的输出保护不证明多个配方聚合后仍放得下。

回归：同输出、不同输出、混合物品/流体、多个配方共享剩余输出容量、数量临近 int 上限。

### A04 · P1 · F19/F20 本地缓冲未预扣共享余额，扣账失败被忽略【S+V，实际机器影响 R】

位置：`Q/hatch/adaptive/AdaptiveNetHatch.java:108-125`；`AdaptiveNetLaserHatch.java:109-126`；`Q/hatch/wireless/WirelessEnergyInputTerminal.java:124-140`。

自适应输入/激光源仓：读取全局余额 gridEU，把 `min(halfStore, currentStored + gridEU)` 写入本地，但不同时扣减全局余额。空载时同一份余额可在多次 4-tick 更新中重复填入缓冲。之后只有检测到本地减少才补扣。

无线输入终端虽是“镜像余额”而不是同样的累加逻辑，但多终端也可能同时持有同一份未预扣余额。GT 匹配 JAR 明确表明 `addEUToGlobalEnergyMap(uuid, negative)` 在结果为负时返回 false 且不修改余额；上述调用均忽略返回值。因此没有代码级的欠款、预留或失败停机保证。

建议：采用明确的“成功从全局扣款后才充入本地”的所有权模型，或实现真正的余额预留/结算协议；失败不得仍交付能量。退款、解绑、拆除、重载、跨团队改绑也必须纳入同一账本。不要用简单增大缓冲或提高频率掩盖不守恒。

回归：少量余额、两个输入仓同时工作、空载多 tick 后再加载、绑定期间改换账户、余额归零、保存重启。验证：全局变化 + 本地变化 + 实际消费/发电守恒，而不是只看机器还在转。

> **【决策记录 2026-09-18 · 用户否决 A04 修复方向，本 P1 保持开放】**
> 用户明确要求：**所有舱室共享无线电网的全部能量，实时从电网扣除消耗的能量**——即 3.18.0 已提交版（commit c52da57）的「镜像余额」语义，与 A04 建议的「先扣电网、本地缓存再使用」预付所有权模型**直接冲突**。
> 按用户要求执行方案 A：无线EU输入侧（`WirelessEnergyInputTerminal` / `AdaptiveNetHatch` / `AdaptiveNetLaserHatch`）恢复镜像实时逻辑（本地缓冲免费镜像电网余额、消耗后实时补扣），移除预付机制（`prepaidBuffer`、`fill()`、拆除/改绑 `deposit()`）；输出侧保留 `deposit()`（安全等价于原直接调用）。
> **取舍结论：用户真实需求优先于本 P1 守恒修复，A04 缺陷暂不关闭。** 后续如需同时满足「全共享」与守恒，应走方案 B——机器实际取电时直接从电网实时扣款（重写取电路径），而非恢复预付缓存。绑定持久化修复（`bindingInitialized` / `markDirty` / NBT `removeTag`）保留不受影响。

### A05 · P1 · F14 拒绝推送后复用缓冲，但倍数与能耗局部变量重置【S，触发 R】

位置：`Q/mixin/ae/MixinCraftingCPUCluster.java:632-683,752-804,806-864,985-991`。

`craftingInventory` 在一轮供应器搜索间复用；`sum=0`、`effectiveN=1`、`useMulti=false` 却在每个 medium 迭代重新初始化。仅当 buffer 为 null 才重新计算这些元数据。第一家取好 N 轮材料却拒绝后，第二家收到已有缓冲，但元数据已变成 1 轮/0 能耗；GT/普通/PH 路径混用时可导致材料量、推送契约和等待产物账目不一致。

建议：把 buffer 与生成它的轮数、每轮能耗、输入契约、来源 medium 类型作为一个不可分离的计划；切换到不兼容 medium 前归还并重建，不裸复用库存对象。PH 返回 accepted 也应校验与请求上限一致。

另一个风险是 `513-533` 捕获倍增异常后继续原版：异常可能发生在已扣料/已接受推送之后，不能保证“安全回退”。只有无副作用阶段可以回退；已执行阶段应明确完成/回滚/中止，而不是重新执行。

回归：两个相同样板供应器，第一家 isBusy=false 但 pushPattern=false，第二家接收；GT→GT、GT→普通接口、PH→GT、PH 拒绝回退，以及异常发生在真实提取/推送之后。

### A06 · P1 · F22 服务端专属构建导致双端 UI/同步树不一致【S+V，具体症状 R】

位置：`Q/terminal/StockMonitorTerminalGui.java:82-155,281-301,375-378,432`。

连接按钮及其 InteractionSyncHandler、发信器列表、覆盖板列表仅 `isServer` 时创建。编辑子面板依赖静态选择表和服务端定位；客户端没有对应选择状态时提前返回，后续 syncValue 不注册。

MUI2 **2.3.73** 的 GuiManager 已核对：客户端也运行 createPanel/collectSyncValues，并不是把服务端 Widget 树序列化成客户端 UI。故控件和同步注册不对称是确定问题；表现可能是缺少按钮/条目、同步异常或子面板无法使用，具体日志与是否崩溃需实测。集成服同 JVM 静态字段可能掩盖部分问题，不能代替独立客户端/专用服验证。

建议：双端创建固定结构和同步项；服务端提供 DTO 列表、状态和稳定选择 ID；客户端只绘制同步数据，服务端执行目标查找与写入。动态子面板也必须遵循同一同步契约。

### A07 · P2 · F22 把 IMachineSet 元素误当作 PartLevelEmitter【S+V】

位置：`Q/terminal/StockMonitorTerminalGui.java:213-232`。

977 的 `IMachineSet` 是 `IReadOnlyCollection<IGridNode>`。当前遍历 Object 并 `instanceof PartLevelEmitter`，实际得到的是节点，判断不会命中。应先从 IGridNode 取 machine，再检查 emitter；其他供应器扫描代码已正确使用这种模式，可参考而不是根据新版 API 猜测。

回归：同网多个发信器、无发信器、拆除重装和网络分裂；明确是否还需要纳入其他类型的发信器。

### A08 · P1 · F22 全局覆盖板列表与“仅验证终端网络”不相配【S，权限可利用性 R】

位置：同文件 `240-275,299-317,468-476`。

列表取全局 CoverRegistry 的全部条目，没有按目标网络/所有者筛选。写回权限检查针对 terminal 所在网络而非目标 cover；无终端网或异常时返回 true。`canEdit` 在子面板构建时缓存，之后写入不重新计算。注释“终端没连网时覆盖板无法被别的网络操作”不能由当前全局列表保证。

这是目标授权模型缺口，不等于本轮已经构造了可用越权包；A06 还可能让正常 GUI 路径先失效。**修好 UI 后更不能遗漏这个潜在授权问题。**

建议：服务端按目标覆盖板的网络/归属策略校验，每次写入重新确认玩家、会话、位置、覆盖板实例及权限；无网络要定义清楚本地访问与远程访问规则，异常不可默认允许远程写。列表也应遵守同一可见性范围。

### A09 · P1 · F10 foreach 中直接删除 HashMap 条目【S】

位置：`Q/wireless/link/WirelessBlockLinkManager.java:161,239-241`。

遍历 `blockLinks.entrySet()` 时，内部遇到 links 空就 `blockLinks.remove(freq)`；多个频率且后续仍有迭代时可触发 ConcurrentModificationException。不是多线程竞态，单线程也能发生。上层 Tile update 链路未在此处恢复。

建议：外层也使用 Iterator.remove，或收集待删 key 后统一删除；区分内存连接清理和持久化绑定删除。回归多个频道、一个目标移除使某频道列表归零、随后频道仍存在。

### A10 · P2 · F10 全局扫描被每台 Tile 调用，且离线/加载判断顺序不稳【S，区块行为 R】

位置：`Q/wireless/TileWirelessTransceiver.java:295-305`；`Q/wireless/link/WirelessBlockLinkManager.java:156-205`。

- 每台收发器每 5 tick 调一次全局 processAll，管理器又用全局计数“每 5 次调用扫描”。并非固定每 25 个服务器 tick；收发器多时同一 tick 可反复扫全表，近似随收发器数×链接数放大。
- 目标维度暂时不可用时直接从内存 links 移除；无法区分暂时卸载和用户删除。持久化记录仍可能存在，重建依赖后续加载事件。
- 先 getTileEntity，再检查 chunkExists；检查不能保护先前读取。是否强制加载取决于 1.7.10 世界实现，但判断顺序本身需要调整。

建议：唯一服务端 tick 调度全局管理器，按拓扑变更重连；先检查维度/区块是否已加载，临时离线断开 active connection 但保留绑定，明确重试与持久化更新规则。

### A11 · P2 · F1 供应器包预算未累计且未覆盖 fix41 新字段【S】

位置：`Q/network/RequestProvidersListPacket.java:305-353,571-583`；`ProvidersListS2CPacket.java:113-139`。

贪心筛选里检查 `budget + add` 后只 keep.add，没有 `budget += add`；每条单独能放下即可全留下，不能控制总量。估算 entryBytesNoIcon 还没有计算实际写出的 totalSlots、locationKey 长度与内容；图标是启发式而非最终编码值。发送端还没有与接收端 size<=1024 的约束统一。

建议：对最终 wire format 做真实或严格上界测量，逐项累计并设置条目上限；大列表分页。所有可空字符串在编码前归一化，避免 locationKey 回退 null 导致 getBytes 异常。不要将“留了 700 多字节余量”当正确性证明。

回归：大量供应器、长中文名称、长坐标、带复杂 NBT 图标、缺失位置键，直接校验最终 ByteBuf 字节数和双端解码条目数。真实传输上限按该实例 Forge/FML 另行测定。

### A12 · P2 · F1 声称存在的供应器缓存没有写入，空可接纳集合仍回退全量【S】

位置：`RequestProvidersListPacket.java:36-71,218-290,585-611`。

本文件只有 PROVIDER_CACHE.get、clear 与清理迭代，没有 put/new CachedProviders 调用，故 1 秒缓存没有真正生效。另一方面，只有 accept 非空才替换为过滤后的列表；没有任何机器可接收时反而保留原始候选。客户端只按“有空槽”可能自动挑中不支持该样板的供应器，最终上传会拒绝，但列表和自动选择语义不一致。库存统计回退原料 IInventory 也不等于实际上传支持。

建议：若缓存拓扑，空槽/接纳性仍在动作前实时校验；决定合适的失效机制后再补缓存，不缓存错误许可。过滤应返回空候选和原因，不能以“全量列表”充当“无可接收”。不要移除最终上传端的安全检查。

### A13 · P2 · F15 滚轮替换排除了当前项，再试图定位当前项【S】

位置：`Q/network/MergedTerminalScrollReplacePacket.java:79-105,128-165`。

findAlternatives 排除与 current 相等的栈，调用端却从该列表寻找 current 的下标再 +/-1。常见等数量情形下 index=-1，正向总取首候选，三种以上替代物容易在前两项之间往返，不能完整循环；数量也参与相等判断，进一步使行为不稳定。

建议：构建包含当前类型的稳定排序候选环，或明确维护游标；候选身份采用物品/meta/NBT，不把 ghost 数量当类型身份。只替换虚拟槽的安全检查已存在，**不重复报告旧版实物复制问题，也不建议滚轮为 ghost 槽扣真实库存**。

回归：3–5 个矿辞候选、正反滚动、当前项不在库存、数量不同、跨页、空候选。

### A14 · P2 · F4/F5 NEI 库存缓存按 id/meta 覆盖 NBT 变体【S】

位置：`Q/client/NetworkInventoryCache.java:53-59,90-119,242-248`；`Q/mixin/nei/MixinGuiMEMonitorable.java:43-55`。

物品缓存 key 只有 itemId 和 damage；同 id/meta 不同 NBT 的计数与 craftable 状态互相覆盖，不是正确求和。一个变体的零量删除还会清除另一变体的记录。结果可误导 tooltip、数量以及客户端是否尝试下单。服务端有精确提取校验，不能据此说能取出错误 NBT 物品。

建议：使用与 977 AE 物品身份等价的不可变键，明确 NBT 敏感与泛化显示各自语义；增量 update 必须能单独新增/删除每个变体。避免每帧把完整 NBT 转字符串作键。

### A15 · P2 · F16 真实候选去重丢失同 id/meta 不同 NBT 的库存【S+V】

位置：`Q/quest/QuestDetectLogic.java:200-225`。

需求键和 gatherAvailable 的 dedupe 都用 item+damage；后者会在多个真实 NBT 变体中只保留第一个。BQ 3.8.70 Detector 在 `ignoreNBT=false` 时进行 tag 比较，因此任务要求的变体可能在进入 BQ 过滤前就被丢掉；即使任务忽略 NBT，只保留一个变体也可能少算总量。

建议：区分“查询候选去重”与“真实库存去重”。真实候选保留精确身份/数量，再由 BQ 的 ignoreNBT、partialMatch、矿辞规则判断；保留上限但加入分页/分批避免永久饿死后面的需求。

排除误判：同版本 TaskRetrieval/TaskFluid.retrieveItems 开头均会跳过 consume，因此本轮不认定此处能免费提交消耗任务。

### A16 · P2 · F19 NBT 恢复的绑定在 onFirstTick 被放置者覆盖【S】

位置：`Q/hatch/wireless/WirelessEnergyInputTerminal.java:113-120,342-357`；`WirelessEnergyOutputTerminal.java:100-107` 及其 save/loadNBTData。

NBT 读取恢复 ownerUuid，但 onFirstTick 无条件 `ownerUuid = aBase.getOwnerUuid()`。数据棒绑定到其他账户后重载，会改回底座所有者。解绑状态也无法仅凭 null 和“新方块待自动绑定”区分。

建议：持久化显式绑定状态，首次放置默认绑定与 NBT 恢复分开；账户切换同时处理 A04 的未结算缓冲，不仅修改 UUID。回归 A 放置、绑定 B、卸载/重进、解绑后重进。

### A17 · P2 · F20 “输入/输出”和时间窗口统计不是所标称语义【S】

位置：`Q/hatch/adaptive/GridEnergyStats.java:35-73,76-117,128-134`。

通过余额 delta 正负推断总输入/总输出，无法识别同 tick 发电与消费相抵；不能当作真实双向吞吐量。10 分钟/1 小时快照按周期覆盖，不是滚动窗口，刷新边界变化突然归零；用该变化除以完整窗口还会低估周期初段。重载时 initialized=false，首次 tick 覆盖此前窗口基线。

建议：要么把 UI 明确命名为“观测余额净增/净减、距快照变化”，要么在实际转账点记录 gross 流量，并用带时间戳环形桶实现窗口。EU/t 需按实际采样间隔归一化；累计量也应考虑 long 溢出与显示截断。

### A18 · P3 · F21 流体读取可避免每槽全表扫描，勿误判 977 身份契约【S+V】

位置：`Q/cover/stockmonitor/ae/AeStockReader.java:23-34`；`StockMonitorCover.java:96-110,123-130`；`Q/terminal/CoverRegistry.java:137-146`。

每个有效流体槽遍历流体 storageList 按 fluidID 找第一项，9 槽、多覆盖板时成本随网络类型数增长；upsert 每次都 markDirty，即使登记内容不变也写脏。

977 AEFluidStack 构造取 FluidStack.getFluid，不能因“新建 FluidStack 对象”就认定 fluid 注册对象身份不同、findPrecise 必然失效。本轮也没有把当前路径“忽略流体 NBT”直接升格为确定库存错误：需要结合 977 本身的流体 tag 表达及 AE2FC 集成确认。

建议：以匹配版本的 findPrecise/availableItem 路径验证等价性，或单次采样建立本 tick 流体索引供多槽共享；保持断网、变网失效。Registry 只在变化时 markDirty，动态库存不要作为不必要的持久化高频字段。性能收益应以类型数×覆盖板数测量。

### A19 · P2 · F18 supplier 覆盖没有恢复策略，跨配方还跳过求值【S+V，机型相关 R】

位置：`Q/mixin/gt/MixinProcessingLogicSpeed.java:95-108`。

仅在 parallel>1、speed!=1 时替换 supplier，降回 1、拆仓后并不在该 Mixin 内恢复。某些上游/子类会重设 supplier，不能说所有机器必然残留；但长期复用 ProcessingLogic 的路径缺少显式所有权和恢复机制。更直接的问题是 threads>1 立即取消原 process，刚赋的 suppliers 不按原生入口求值，字段可能仍是旧值。

建议：每轮派生有效参数而不是永久改写机器原 supplier；记录、组合并恢复原契约，避免简单置 null 又破坏机器自身能力。测试从高档降到 1、拔电路、拆仓、切换线程数、切配方以及子类自定义 supplier。

## 5. 可优化但不应直接视作已证实缺陷的事项

### 5.1 F22 完整性与生命周期

- `StockMonitorTerminalGui` 两种列表各只显示前 5 条，其余只有数量提示；应采用真正滚动/分页和稳定条目 ID。
- 覆盖板已有 9 槽，而远程编辑只操作 slot 0；需明确选择槽位并同步模式、阈值、目标，而非 UI 暗示能编辑全部。
- SELECTED_COVERS/SELECTED_EMITTERS 两个静态 UUID map 未找到 remove/clear 路径。“下次覆盖”不会使历史玩家数量受在线人数约束，还会保留 emitter 对象引用。优先改为容器会话状态；否则关闭/离线/停服清理并避免存强世界引用。
- 先修 A06/A08，再添加搜索和更多编辑项，避免扩大未鉴权的功能面。

### 5.2 F1/F15 协议与模型

- 给新协议设清晰版本要求，fix41 客户端/服务端同升；目前新增字段和回执不能靠旧端“吞解码异常”获得兼容性。
- 将供应器 DTO、定位、可接纳性与编码预算共用化，避免多个 packet 分别维护字段长度/反射逻辑。
- GUI 列表 index、identityHashCode 是会话定位，不应当永久业务 ID；位置键仍应验证当前实例/网络，不能仅坐标相同就默认是同一机器。
- PatternContainer 的数量单位、展示栈与真实流体栈转换应集中。`491`、`517` 的 int 乘法及 `895` 的 long→int 转换值得边界测试；在确认 GT 展示栈数量语义前，不贸然删除乘数或按另一个版本修改 NBT。
- 编码流程先构造完整可用产物，再提交空白样板消耗；异常应可见且可回退，不能长期依赖“这里通常不会抛”。

### 5.3 Mixin、反射与可选依赖

- 智能倍增 1000 多行接管循环与上游 AE2 演进耦合很深。推荐最小差异跟踪与阶段契约测试，不推荐未经验证拆成大量新抽象。
- 可选 mod 的反射查找按 Class 缓存成功/失败，启动输出一次能力矩阵；正常缺失不要每帧/每 tick 打堆栈，异常失效也不要完全静默。
- 把“类可加载”“Mixin 成功匹配”“功能实际触发”分开验证。`require=0`/异常吞掉会让启动正常但功能失效。
- GTNL、NEI、NEE 的编译/实例版本差异先固定测试矩阵；没有用户许可不更新依赖。
- AppEU 集成类在吞并源码中缺失已有显式警告；“内部可存 EU 数字”不等于 AppEU 通道实际可用。按缺失功能记录，不视为已支持所有通道。

### 5.4 配置、缓存、日志、资源

- `Config.reload` 解析中途异常后仍可落部分值并更新 mtime；可采用完整临时快照校验后一次发布。读失败需保留上次好配置并告知玩家，写入用临时文件+替换。
- 性能缓存应规定键、数据有效范围、失效来源、大小上限与停服清理；不是所有 HashMap 都需要 ConcurrentHashMap，也不是有 TTL 判断就真的有清理或命中。
- 上传/配方反查等频繁 INFO 应降为可开关调试日志，失败保留目标、原因和会话标识，避免刷屏淹没真正问题。
- 格式化工具、中文/英文文案、GuideNH 与 MOD_MAP 做一次一致性校验。MOD_MAP 中部分历史类路径和通知/pin 说明与当前实现已不一致；本轮仅指出，不顺带改已有文档。

## 6. 已排除、降级或不应重复提出的判断

1. **“AE2 是 997，所以按 997 改”**：不成立，当前构建和实例清单均为 977。
2. **“1.7.10 必须 Java 7/8 源语法”**：不成立，本项目 Jabel 是明确配置。
3. **“F22 崩溃应再加 childDelegations”**：不应实施。当前已移除该干预；历史 MTE ID 冲突和加载器连锁崩溃不能混为一谈。
4. **“上传复制 1 张后清槽必吞整叠”**：暂不成立。977 ContainerPatternTerm 与本项目 PatternContainer 均把编码输出槽设为 1；只看到 ItemEncodedPattern 最大堆叠 64 不足以证明普通操作可触发。
5. **“滚轮/设栈包现在还能改真实库存槽复制物品”**：当前有虚拟槽限制；A13 是循环算法问题，不重复旧报告。
6. **“通知仍必须带同网络无线终端”**：当前捕获和通知链已有直接通知下单玩家回退。
7. **“无限元件预览任意 UUID 一定无限创建缓存”**：请求入口已有磁盘文件存在检查，旧路径已缓解；仍应研究合法元件持有权、请求频率和未落盘记录可见性，不宣称已复现 DoS。
8. **“任务检测可免费提交 consume 任务”**：匹配 BQ 接口主动拒绝 consume；A15 是 NBT 候选与计数问题。
9. **“覆盖板从不移除注册表”**：当前 onCoverRemoval 调用 remove，refreshOnlineStatus 也会区分卸载/拆除；不重复旧结论。
10. **“浅复制消除了输入消耗”**：错误。数组浅复制保留栈对象引用；简单改深复制又不提交扣料会制造另一个严重错误。
11. **“输出数量饱和就代表安全”**：仅用于显示才可这样说，真实产物/等待量应防止静默截断。
12. **“历史 BUILD SUCCESSFUL/用户通过等于本轮通过”**：不成立。本轮没有构建或游戏实测，fix39 实例也不能代表 fix41。

## 7. 后续回归计划（本轮未执行）

### 7.1 先准备可对照环境

- 复制测试存档，备份无限元件独立数据文件与旧迁移文件；不在正式服注入写盘故障。
- 客户端与专用服使用同一 fix41 构建，记录 JAR SHA-256 和完整依赖清单；先复现，再决定是否调整版本。
- 至少覆盖：独立客户端+专用服、集成服、普通玩家/第二团队/OP、区块卸载/跨维度、重载/退出重进。
- 对本轮未匹配字节码的第三方目标，获取精确版本源码/JAR 再验证，不拿 master 代替。

### 7.2 优先用例与验收不变量

| 优先级 | 场景 | 验收不变量 |
|---|---|---|
| 第一批 | A01 保存失败、部分迁移、损坏记录、重启 | 失败可见、可重试；旧数据不提前删除；BigInteger 存量不变 |
| 第一批 | A02/A03 多配方共享输入/输出、专属验证、溢出与容量不足 | 失败不扣真实料；成功总输入输出准确；配方锁与机器验证不绕过 |
| 第一批 | A04 低余额多仓、空载积累、解绑/改绑、重载 | 全局余额+本地余额+消费/发电总账守恒；无重复授权同一份 EU |
| 第一批 | A05 第一供应器拒绝、第二接受，混合 GT/PH/普通 | 真正投入轮数=实际接受轮数=等待输出轮数；能耗一致 |
| 第一批 | A06–A08 独立专用服 F22、两个团队、撤销权限 | 双端控件与 sync 对称；只看/改有权目标；权限撤销立即生效 |
| 第二批 | A09/A10 多频道、拆目标、卸载目标维度 | 无 CME；临时离线保留绑定；扫描频率不随收发器数无界增加 |
| 第二批 | A11/A12 大网络、无兼容供应器、目标拆装 | 包可解码且预算可验证；缓存真命中且最终操作重查；不误自动上传 |
| 第二批 | A13 三种以上替代物正反滚动 | 全部候选可达，ghost 数量/实物库存语义正确 |
| 第二批 | A14/A15 相同物品 meta、不同 NBT，各自增删 | tooltip、craftable 与 BQ 检索均匹配精确库存 |
| 第二批 | A16 数据棒绑定 B 后重载、解绑后重载 | 绑定状态不回到放置者；账户余额不串账 |
| 第三批 | A17/A18 时间窗口、同 tick 收支、很多覆盖板/流体类型 | 统计定义一致；UI 单位正确；tick 时间有可量化改善 |
| 全功能冒烟 | F1–F22、三终端形态、NEI tooltip/快捷键/指南 | 按现有单人脚本逐项记录版本、步骤、日志，不能只写“应该没问题” |

建议额外记录：每 tick/每次操作耗时、扫描条目数、缓存命中、包字节数、保存成功/失败 UUID、真实输入/输出数量与能量账本。优化验收以这些数据为依据，而不是仅比较代码行数。

## 8. 本轮执行边界与交付记录

- 执行：只读文件/搜索、源码清单与本地快照、Git 状态/版本查询、参考源码阅读、匹配 JAR 的 javap 与 SHA-256 查询、审查文档编写。
- 未执行：源代码修复、格式化、依赖升级、Gradle 构建、测试服务器启动、真实存档写入、mod 部署、Git add/commit/reset、恢复用户已有删除文件。
- 初始工作区已有 6 个 tracked 文档/资料删除，以及根目录未跟踪 checkpoint 和 `build_compile*.log`；本轮保留，不将它们当作本轮修改或擅自清理。
- 在写报告前复查，`git diff --stat -- src dependencies.gradle gradle.properties` 无输出，既有 dirty 状态未变。
- 本报告以当前代码为准，不把旧审计编号的“已修”注释当作正确性证据；A01–A19 是本轮独立编号，不自动覆盖历史测试结果。

**建议下一轮先确认修复范围，再按“保存/资源守恒 → F22 同步与授权 → 无线生命周期 → 列表/显示/性能”的顺序分批实施。此报告只提出方案，不构成已授权或已完成的修复。**
