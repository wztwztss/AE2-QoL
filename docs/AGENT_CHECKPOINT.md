# AGENT_CHECKPOINT 跨智能体接力状态文档

**放置位置**：`docs/AGENT_CHECKPOINT.md`（原模板要求放根目录，应项目方归类要求改放 `docs/`，根目录副本保留）
**适用范围**：OpenCode / Codex / ShunCode 等所有 AI 编程智能体
**强制铁则**：任何智能体启动工作，第一步必须读取本文档；任务中断、切换智能体、阶段性完成时，必须记录自身使用的工具平台与底层模型，并更新全部进度信息。

---

## 一、当前会话元数据（每次启动工作必须先填写）

|项目|填写内容|
|---|---|
|工具平台|Codex|
|底层大模型|GPT-5（Codex 桌面端，Azure 托管推理）|
|工作分支|master（本地开发，基线 commit `4364e57`；阶段 3 完成至 `a748ec3` fix38）|
|启动时间|2026-09-17 15:38（阶段 3 收尾于 22:45）|
|本次会话目标|完成阶段 3 全部可修复缺陷（fix22~fix38），统一版本号与文档，产出 3.19.0-fix38 发布 JAR，并更新交接文档供下一智能体接手实测。|

---

## 二、项目总目标

GTNH 2.9.0-beta-1（Minecraft 1.7.10 Forge + Java 17/25）环境下的 AE2 附属功能模组 **AE2 QoL**（modId `ae2_qof`，版本 `3.19.0-fix14`）的交付与质量整改：在兼容原生 AE2（AE2UEL rv3-beta-997-GTNH 量级）与格雷科技本体/GTNL/PH 机制的前提下，完成 F1~F22 全部功能的质量检测、缺陷定位与修复，最终产出一个可稳定运行于单机与专用服的发布版本（含合并终端三形态、NEI 样板自动上传、合成完成通知、智能倍增、自适应电网、库存检测覆盖板等）。当前阶段以「先审计、再修复、逐条提交」为推进方式。

---

## 三、全局已完成清单

> 按完成时间倒序排列，均标注产出文件路径。

- [x] 2026-09-17 | 工具：Codex | 模型：GPT-5：完成阶段 3 全部可修复项（fix22~fix38，共 17 个提交）：P0-001、P1-002~P1-024、P2-025、P2-026、P2-030、P1-031、P1-032 已修复或部分处理；统一版本号为 `3.19.0-fix38`，补齐 CHANGELOG/README，同步根目录 mixin 配置；完整构建 `gradlew build --offline` 通过，产物 `build/libs/AE2-QoL-3.19.0-fix38.jar`（1,096,116 字节）
- [x] 2026-09-17 | 工具：Codex | 模型：GPT-5：F22 库存统计终端恢复可用：定位到真正根因是 MTE ID 32001 被 GT 本体 LegacyUniversalChemicalFuelEngine 占用，改用空闲 ID 32101（提交 `bb45f36`）
- [x] 2026-09-17 | 工具：Codex | 模型：GPT-5：F1 样板自动上传重构：目标名加维度坐标后缀、按样板可接纳性过滤、工作台配方独立分支；上传选择界面参考 GTNH-ECO 重做为图标卡片列表（提交 `ca4120f`、`c0f2f21`）
- [x] 2026-09-17 | 工具：Codex | 模型：GPT-5：建立跨智能体接力状态文档（本次），把质检进度、约束、待办与已知坑位固化为可交接检查点，对应文件：`docs/AGENT_CHECKPOINT.md`
- [x] 2026-09-17 | 工具：Codex | 模型：GPT-5：验证当前基线（`4364e57`）可编译通过（`gradlew compileJava` 与 `gradlew build` 均 `BUILD SUCCESSFUL`，使用 `E:\java17` + `--offline`），结论记录于本文档第十节
- [x] 2026-09-16 | 工具：Codex | 模型：GPT-5：完成阶段 2 单机实测脚本与用户实测结果回填（F1~F22 结果总表、逐项日志初判），对应文件：`docs/SINGLEPLAYER_TEST_SCRIPT.md`
- [x] 2026-09-16 | 工具：Codex | 模型：GPT-5：完成阶段 1 静态审查，输出 32 条问题清单（P0×1、P1×23、P2×8，含位置/级别/现象/复现/根因/建议/修复顺序），对应文件：`docs/STATIC_AUDIT_ISSUES.md`
- [x] 2026-09-15 | 工具：Codex | 模型：GPT-5：建立审查基线提交（未修改源码，仅固化工作树状态），对应提交：`4364e57 chore: establish pre-audit baseline`
- [x] 2026-09-07 | 工具：OpenCode | 模型：CommandCode GPT-6 Astra（历史会话）：修复合并终端流体编码为物品的 bug（`isFluidItem()` 增加 `ItemFluidDrop` 检测），对应文件：`src/main/java/com/wztwzt/ae2_qof/merged/PatternContainer.java`（提交 `9f95206`）

---

## 四、当前进行中

- 任务标题：阶段 3 —— 按优先级分批修复 `docs/STATIC_AUDIT_ISSUES.md` 中的缺陷，并逐条回填实测结果
- 整体进度：约 92%（阶段 1 静态审查 100%；阶段 2 脚本与实测回填 100%；阶段 3 代码修复已完成 fix22~fix38 共 17 个提交，32 条审查问题中 31 条已给出修复结论（其中 P2-027、P2-028 为部分处理），仅 P2-029 需 GTNL/PH 裁剪环境实测定级）
- 涉及文件：
  - 审计与实测：`docs/STATIC_AUDIT_ISSUES.md`、`docs/SINGLEPLAYER_TEST_SCRIPT.md`
  - 本阶段已修改：网络包鉴权（`network/WirelessActionPacket.java`、`network/HatchActionPacket.java`、`network/MergedTerminalScrollReplacePacket.java`、`network/MergedTerminalRenamePacket.java`、`network/InfinityCellStatsPacket.java`、`network/RequestProvidersListPacket.java`、`network/HatchListSyncPacket.java`）、库存统计终端与覆盖板注册表（`CommonProxy.java`、`terminal/StockMonitorTerminal.java`、`terminal/StockMonitorTerminalGui.java`、`terminal/CoverRegistry.java`、`cover/stockmonitor/StockMonitorCover.java`）、合并终端上传 UI（`client/gui/GuiProviderSelect.java`）、智能倍增（`mixin/ae/MixinCraftingCPUCluster.java`）、跨配方并行（`mixin/gt/MixinProcessingLogicSpeed.java`）、自适应电网（`hatch/adaptive/`）、配置（`Config.java`）、生命周期（`MyMod.java`、`ClientProxy.java`）
- 当前卡点 / 问题：
  1. 测试实例 JAR 尚未部署（约束要求每次部署单独征求用户同意），因此本轮修复全部为「静态审查 + 编译验证 + 逻辑复核」结论，需用户复测确认；
  2. F15 样板回读卡顿、F10 无线连接概率断开、F5/F10 的 UI 观感属实机体验问题，需用户复测反馈后再动手；
  3. P2-029（GTNL/PH 可选依赖裁剪场景）需要专门的裁剪环境启动验证，当前环境无法覆盖。
- 下一步最小动作：请用户批准部署 `build/libs/AE2-QoL-3.19.0-fix38.jar` 并按 `docs/SINGLEPLAYER_TEST_SCRIPT.md` 复测 F1/F4/F6/F14/F22 等条目；根据复测结果决定是否继续微调 F15/F10。

---

---

## 五、待办任务队列（优先级从高到低）

> 阶段 3 已全部处理完毕：下列任务 1~12 均在 fix22~fix38 中完成或明确标注保留原因；
> 任务 13~15 属「需实机复测/需专门环境」的遗留项，交由下一轮实测决定。

### 已完成（对应提交见 `docs/STATIC_AUDIT_ISSUES.md` 状态列）

- [x] 任务 1（P0-001）滚轮替换包刷物漏洞 —— `7b5bf86` fix22
- [x] 任务 2（P1-010）合并终端重命名越权 —— `7b5bf86` fix22
- [x] 任务 3（P1-002/P1-011）网络包鉴权 —— `9eebd03` fix27、`5dbee32` fix28
- [x] 任务 4（F1）样板上传目标选择与工作台配方识别 —— `ca4120f` fix29、`c0f2f21` fix31
- [x] 任务 5（F14）智能倍增服务端生效 —— `0d2f500` fix23，通知与溢出补充修复见 `2b0790e` fix35
- [x] 任务 6（F6）合成完成通知 —— `0d2f500` fix23，覆盖逻辑补强见 `2b0790e` fix35
- [x] 任务 7（F4）NEI 悬浮提示 —— `2a71072` fix24
- [x] 任务 8（F17）无限元件 U 键查看 —— `ecdeb6c` fix26
- [x] 任务 9（F22）库存统计终端恢复注册 —— `bb45f36` fix30
- [x] 任务 10（P1-007/P1-008/P1-009）持久化与迁移 —— `ed9b907` fix33、`2b0790e` fix35
- [x] 任务 11（P1-018/P1-019/P1-020/P2-025）合成与数值边界 —— `2b0790e` fix35、`e7a5702` fix36
- [x] 任务 12（P1-012/P1-013/P1-021/P1-031/P1-032）生命周期与并发 —— `186f82e` fix34、`a748ec3` fix38、`ed9b907` fix33

### 仍需实机或专门环境（下一轮）

- [ ] 任务 13（F5/F10 UI 与无线连接稳定性）书签面板数量/可合成显示、无线收发器 UI 观感、无线连接概率断开：需用户复测确认具体触发条件
- [ ] 任务 14（F15）合并终端样板回读卡顿、需重开 GUI 才刷新：需用户在新 JAR 上复测并给出复现步骤
- [ ] 任务 15（P2-029）GTNL/PH 可选依赖裁剪场景 Mixin 验证：需专门的裁剪环境启动验证

---
## 六、项目固定约束（所有智能体必须遵守，不得修改）

1. 运行环境：Minecraft 1.7.10 Forge（本工程实际使用 Java 17 工具链 + Jabel 现代语法，编译目标仍为 Java 8 字节码）
2. 权限边界：仅允许读写本项目目录内文件；`reference_src` 为只读参考，禁止复制其源码入库；测试实例 `E:\wzt\MC\PL genmulu\GT_New_Horizons_2.9.0-beta-1_Java_17-25(1)` 严格只读
3. 代码规范：遵循原有代码风格，不擅自大规模重构旧代码
4. 兼容要求：不破坏 GTNH 原版机制，兼容对应版本的 AE2、格雷科技本体
5. 安全规则：禁止自动执行删除文件操作，删除操作必须人工确认；部署 JAR 到测试实例前必须单独征得用户同意
6. 构建要求：修改代码后必须通过 gradle 编译验证，无报错（构建方式见第八节）
7. 版本对齐：所有配方、参数、数值与 GTNH 官方设定保持一致
8. 提交约束：一个修复一个 commit；代码、文档、版本号变更放在同一 commit；不得删除 4 个未跟踪的 `build_compile*.log` 历史日志

---

## 七、已否决方案（避免重复踩坑）

- 方案名称：为 F22 库存统计终端在 `init` 阶段用 `try-catch` 防御层 + RFB `childDelegations` 注入绕过启动崩溃
  - 否决原因：已尝试 5 轮（槽位调整、catch 防御、诊断输出、RFB childDelegations 注入、`try-catch` 兜底）均未解决；崩溃本质是 init 阶段 Log4j/RFB 类加载链路二次失败，异常被掩盖，继续加固只会掩盖根因，需先拿到 crash-report。
- 方案名称：样板上传按「机器中文名」在网络内自动匹配目标供应器
  - 否决原因：同名机器多台时命中不确定，实测已出现「传错目标」；工作台合成类配方写的是 `crafting` 标记，无法据此反查 GT 配方池，属于设计缺口，不能靠补映射表解决。
- 方案名称：合并终端 `Shift+滚轮替换` 直接 `slot.putStack(candidate)` 完成替换
  - 否决原因：服务端信任客户端槽号且不从 ME 网络真实扣除，可被伪造包写入玩家真实背包槽，形成刷物漏洞，必须改为槽对象白名单 + 网络扣除。

---

## 八、关键知识笔记

**构建与验证**

- 构建命令（PowerShell）：先 `$env:JAVA_HOME='E:\java17'`、`$env:GRADLE_USER_HOME='C:\Users\29357\.gradle'`，再执行 `.\gradlew.bat build -x spotlessJavaCheck -x spotlessCheck --offline`；仅快速编译用 `compileJava`。
- 历史构建日志 `build_compile.log`/`build_compile4.log`（2026-09-15/16 生成）曾报 `StockMonitorTerminal 未实现 ISidedInventory 的 canInsertItem(int,ItemStack,int) / closeInventory()`。2026-09-17 用当前基线源码复核时，`compileJava` 与 `build` 均 `BUILD SUCCESSFUL`（Gradle 按内容哈希判定已编译成功），说明该历史报错不对应当前源码状态；若后续再次出现同类报错，应以新日志为准重新定位，不要直接套用旧结论。
- 测试实例只读；部署新 JAR 需单独批准。产物目录 `build/libs/`，当前存在 `AE2-QoL-3.19.0-fix13.jar`、`AE2-QoL-3.19.0-fix14.jar`、`AE2-QoL-3.19.0-fix14-dev.jar`、`AE2-QoL-3.19.0-fix14-sources.jar`。

**版本与文档状态**

- 版本号全项目统一为 `3.19.0-fix38`：`gradle.properties`、`src/main/resources/mcmod.info`、`README.md`、`README.en.md`、`CHANGELOG.md` 五处一致。
- 根目录 `mixins.ae2_qof.json` 与 `src/main/resources/mixins.ae2_qof.json` SHA256 完全一致（打包实际使用 resources 版本）。
- 发布产物：`build/libs/AE2-QoL-3.19.0-fix38.jar`（1,096,116 字节）。

**关键机制结论**

- F1 上传策略：strategy1 唯一供应器直传 → strategy2 按「记住的机器名（含 `@D维度 x,y,z` 后缀）」匹配 → strategy3 手动选；工作台类配方强制 `apu:recipeMap=crafting` 走独立分支，不再预填「合成」关键词；供应器列表按样板可接纳性过滤，同名机器聚合为一张卡片并固定指向空槽最多的那台。
- F6 通知条件（fix23 后）：`submitJob` 只要拿到返回值就记录下单玩家与产物；完成时若玩家背包没有绑定同网络的无线终端，退化为直接通知本人。`submitJob` 返回 null（CPU 忙）时保留进行中任务的通知状态（fix35）。
- F14 倍增条件：任务值 > 1、配方非 craftable、宿主实现 `ISmartDoublingMedium` 且开关已启用；`getMaxMultiplier` 有多个提前返回 1 的分支（未启用、craftable、流体接口、假合成、`BlockingMode != NONE`、`hasItemsToSend()`、无 adaptor）。
- F22 根因（重要）：MTE ID **32001 已被 GT 本体 LegacyUniversalChemicalFuelEngine 占用**，构造期抛 `IllegalArgumentException`，被 Log4j/RFB 二次加载错误掩盖成「类加载崩溃」；现使用空闲 ID **32101**。可用 `docs/dumps/metatileentity.csv`（4488 条）查任意 MTE ID 是否冲突。
- CoverRegistry：已统一存主世界 `loadItemData`（P1-007），旧 per-dimension 数据做一次性合并迁移；打开统计终端时刷新在线/存在状态（P1-008）。
- 线程与平台判定用 `Platform.isServer()/isClient()`；网络包统一走 `ModNetwork.CHANNEL`，服务端任务通过 `ServerTerminalHelper.scheduleServerTask` 回主线程。
- 生命周期：`MyMod.serverStopping` → `CommonProxy.serverStopping`，统一保存并清空自适应电网、无线频道/方块链接、供应器缓存；客户端 `WorldEvent.Unload` 清空高亮与 NEI 库存缓存。
**协作与环境注意**

- Shell 为 PowerShell：无 `head`/`bash`，用 `Select-Object -First N`、`rg`；中文文件读取用 `Get-Content -LiteralPath ... -Encoding UTF8`。
- `apply_patch` 在本环境需通过 `codex.exe --codex-run-as-apply-patch` 传入 patch 文本，直接 heredoc 会失败。
- 本项目 198 个 Java 源文件，审计覆盖按功能域完成，未做逐分支形式化证明。

---

## 九、历史会话操作日志

> 按时间倒序排列。

```Plain Text
[2026-09-17 22:45] | 工具平台：Codex | 底层模型：GPT-5
- 本次完成内容：完成阶段 3 代码整改与文档收尾。共新增 fix22~fix38 共 17 个提交，覆盖：
  P0-001 刷物漏洞、P1-002/P1-010/P1-011 越权与坐标泄露、P1-003 F22 恢复注册（MTE ID 冲突根因）、
  P1-007/P1-008/P1-032 覆盖板注册表全局化与在线刷新、P1-009/P1-031 配置迁移与并发、
  P1-012/P1-021/P1-023/P1-024/P2-026/P2-030 跨存档生命周期与缓存清理、
  P1-013/P1-014 自适应终端改频顺序与配置鉴权、P1-017 无限磁盘查询硬化、
  P1-018/P1-019/P1-020/P2-025 合成通知与数值边界、
  F1 自动上传目标选择与 GTNH-ECO 风格上传 UI、F4/F6/F14/F17 功能修复、
  版本号统一为 3.19.0-fix38 并补齐 CHANGELOG/README、同步根目录 mixin 配置。
- 修改/新增文件：见本提交范围内的 src/main/java 多个文件 + README.md、README.en.md、CHANGELOG.md、
  gradle.properties、src/main/resources/mcmod.info、docs/STATIC_AUDIT_ISSUES.md、docs/AGENT_CHECKPOINT.md、
  docs/SINGLEPLAYER_TEST_SCRIPT.md
- 遗留问题/给下一个智能体的提示：
  1) 测试实例尚未部署 JAR，本轮全部为静态审查+编译验证结论，需用户复测确认；
  2) F15 样板回读卡顿、F10 无线连接概率断开、F5/F10 UI 观感需用户复测给出复现步骤；
  3) P2-029 需 GTNL/PH 裁剪环境验证；
  4) 一个修复一个 commit 的约束继续有效，部署 JAR 前必须单独征得用户同意。
- 本次是否编译通过：是（`gradlew build --offline -x spotlessCheck -x spotlessJavaCheck` → BUILD SUCCESSFUL，
  产物 build/libs/AE2-QoL-3.19.0-fix38.jar）
```
```Plain Text
[2026-09-17 15:51] | 工具平台：Codex | 底层模型：GPT-5
- 本次完成内容：按项目方要求建立跨智能体接力状态文档并放入 docs/；如实回填会话元数据、项目总目标、已完成清单、当前进度、待办队列、固定约束、已否决方案、关键知识笔记；复核基线（4364e57）编译状态，compileJava 与 build 均 BUILD SUCCESSFUL
- 修改/新增文件：docs/AGENT_CHECKPOINT.md（新增）
- 遗留问题/给下一个智能体的提示：源码尚未做任何修改，工作树只有未跟踪文档与历史构建日志；下一步从 P0-001（MergedTerminalScrollReplacePacket 刷物漏洞）开始修复，一个修复一个 commit；部署 JAR 前必须征得用户同意；F22 恢复注册前必须先拿到 crash-report
- 本次是否编译通过：是
```

```Plain Text
[2026-09-16 21:55] | 工具平台：Codex | 底层模型：GPT-5
- 本次完成内容：输出阶段 2 单机实测脚本（F1~F22 用例、通用规则、结果总表），并回填用户实测结果与逐项日志初判
- 修改/新增文件：docs/SINGLEPLAYER_TEST_SCRIPT.md（新增）
- 遗留问题/给下一个智能体的提示：F4/F6/F14/F17/F22 为失败项；F1 自动上传传错目标且工作台配方无法识别；需用户提供服务端日志与 crash-report 才能继续定位
- 本次是否编译通过：未编译（仅文档）
```

```Plain Text
[2026-09-16 21:11] | 工具平台：Codex | 底层模型：GPT-5
- 本次完成内容：完成阶段 1 静态审查，输出 32 条问题（P0×1、P1×23、P2×8），含位置、级别、类型、现象、复现、根因、建议与修复顺序
- 修改/新增文件：docs/STATIC_AUDIT_ISSUES.md（新增）
- 遗留问题/给下一个智能体的提示：清单为静态结论，网络包对抗、真实存档回归、专用服并发、旧存档迁移、依赖裁剪环境仍待阶段 2/3 验证；P0-001 需受控恶意包测试确认实际影响
- 本次是否编译通过：未编译（仅文档）
```

---

## 十、本次会话收尾检查清单（结束前必须逐项确认）

- [x] 已更新「全局已完成清单」
- [x] 已更新「当前进行中」状态
- [x] 已在「历史会话操作日志」追加完整记录，写明了工具平台和底层模型
- [x] 已填写给下一个智能体的交接提示
- [x] 代码已通过编译验证：2026-09-17 22:43 执行 `gradlew.bat build --offline -x spotlessCheck -x spotlessJavaCheck` → `BUILD SUCCESSFUL`，产物 `build/libs/AE2-QoL-3.19.0-fix38.jar`

---

### 使用说明

1. 模板原要求放项目根目录（`AGENT_CHECKPOINT 跨智能体接力状态文档.md`）、或另存为 `AGENT_CHECKPOINT.docx`；本文件按项目方最新要求放在 `docs/`。
2. 每次打开智能体干活前，先让 AI 读取本文档，填写「当前会话元数据」。
3. 每次结束工作或切换智能体前，让 AI 更新本文档全部进度内容，并追加操作日志。
4. 下一个智能体启动后自动读取本文档，通过日志和进度无缝承接任务。
