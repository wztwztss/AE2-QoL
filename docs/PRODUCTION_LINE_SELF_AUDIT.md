# 产线管理器 自审报告（AE2-QoL 4.1.0 · 持续迭代文档）

> 规则：不启动游戏（静态验证：构建/字节码/脚本化审计/代码审读），允许联网查资料，禁止删除文件，不能破坏其他功能。每轮自审后把「已修」与「待修」更新到本文件，并在末尾追加日志。

## 本轮已修（2026-10-16，第 1 轮）

1. **材质缺失**：新增 `src/main/resources/assets/ae2_qof/textures/items/proline_card.png`（16×16，_window 材质）。此前 `setTextureName("ae2_qof:proline_card")` 指向不存在的文件，游戏内会显示紫黑棋盘。`unzip -l` 已核对入包。
2. **lang 键对齐审计**：
   - `ae2_qof.threads.tab.threads` 只有 en_US 有 zh_CN 缺 → zh 已补（线程页 lang 是历史残留，保留键）。
   - `ae2_qof.wireless.bind.success` 同键双值 → 拆两次：旧的 "已绑定频道: %s" 保留给带 `%s` 格式调用（ItemWirelessConnector），并新增 `ae2_qof.wireless.bind.network.success`（无线能量输入/输出端子两处引用已同步改 key），避免**文件后置键覆盖前键而丢失 %s 格式**的真 bug。
   - `ae2_qof.proline.*` 中英键匹配通过（含 `.bound`、`.no_output`、`.sum.in/out/cum`、`.run_tag`、`.step.type/out` 扩展；新增 zh 补齐）。
3. **ProLinePage 内部硬编码中文 → lang 键**（server 侧硬编码字符串在专用服不减义）：
   - 总输入/总输出行（`输入/输出/累计`）、`在产` 标签、`类型:` / `主产物:` tooltip、`无产物`、`（已绑）` 全部改走 StatCollector 与 lang。
4. **ProLineImporter 多形态 start**：
   - `start`: JsonArray / **旧对象 map (`{"海晶酸":-80000}`)** 双形态都处理；未解析项进 errors（不静默丢）。
   - 修复 `!obj.has("steps")` 导致 start 被顶掉的 bug（一页 JSON 同含 steps/start 时 start 会被跳过）。
   - `jPrimitiveLong` 拆出，数字包装/字符串 double 都覆盖。
5. **静默 catch 逐一核查**（坑 #19 - 不留日志会变“静默失效”）：
   - `ProLineSampler` 两处（锚定终端 TE / 绑定机器 TE 读）改为 `ServerTerminalHelper.warnOnce`；
   - ProLineGate 的两个探针由三态日志承接（isPresent=false 时必然毫打 INFO）；
   - ProLinePage.ItemProductionLineCard 的静默 catch 均有 fallback 显示或聊天反馈，不产生“无日志”的关键失效，不改动。

## 本轮已修（2026-10-16，第 2 轮：泄漏审计）

1. **ProLinePage 关闭 清理**：addCloseListener 里清 `SELECTED_LINE / LAST_STEP / IMPORT_STAGED / IMPORT_FEEDBACK / DEL_ARM / DEL_ARM_LINE`（按玩家 UUID 匹配），不再长驻。
2. **ProLineSampler 长期驻留 key GC**：每 10 秒对照活跃产线集合，将不在列表里的 lineId 从 `BACKOFF / streak` 移除，防长跑积累。

## 本轮已修（2026-10-16，第 3 轮：可发现性/文档同步）

1. **绑定卡挂主创造标签**：`ItemProductionLineCard` 构造器补 `setCreativeTab(AE2QoLCreativeTab.INSTANCE)`——未挂创造页时只能 `/give` 或 NEI 假槽搜到，生存模式不可发现。
2. **绑定卡生存配方**：4 张纸 + 1 铁锭的 shaped（`GameRegistry.addShapedRecipe`，失败仅 WARN 不崩溃）。
3. **README 版本同步**：`README.md` / `README.en.md` 版本行与安装文件名 3.35.0-fix7 → 4.1.0（`mcmod.info`/`gradle.properties` 已在 6c20bc6 同步）。

### 审计备注（不改动，属文档化）
- MixinCraftingCPUCluster 的 `@Shadow` 混淆映射提示为**存量警告**（AE2 上游字段）历轮存在，与本功能无关，不修（修法是升 AE2 依赖/改 mixin 目标，超出本功能范围）。
- `Special:Export` 批量抓取链路已验证到 502/403 为站点故障，恢复后可直接用 `tools/wiki/presets_extract.mjs`/`xml_to_presets.mjs` 扩产线库。

## 本轮已修（2026-10-16，第 4 轮：数据保真 + 聊天 lang 化 + lang 去重）

1. **start[] 期望边界不再丢**：`ProductionLine.startExpected`（raw 名→数量，正=外部投入/负=期望产出）三形态全收（数组对象/旧对象 map/字符串 token），NBT 键 `SExp` 持久化，汇总页首行显示 `期望边界(start) ...`（4.2.0 名称解析后自动变成"达标/未达标"对照）——修复海晶酸线（旧 map）start 语义被当作"未解析错误丢掉"的数据保真 bug。
2. **ProLineManager 聊天消息 lang 化**（`chat.needs_su/needs_team/max_lines/max_steps/deleted/machine_conflict`），`send()` 前缀 `[产线]` 也走键；绑定卡 5 条聊天同样键值化（`card.no_target/not_gt/chunk_not_team/ok/fail`）。zh/en 两侧 724↔724 无重复。
3. **lang 去重工具**：新增 `tools/wiki/dedup_proline_lang.mjs`（en 删除中文值行、zh 删除英文值行，proline 域），执行后 zh/en 各 724 键 0 重复 ✓。
4. 修复 introduce 变量名冲突（`sl`/`sExp`）与 `EnumChatFormatting` 缺失 import。

## 本轮已修（2026-10-16，第 5 轮：GuideNH 兼容 / 资产静态审计 / 指南文清修）

1. **GuideNH 兼容性核对**：全仓只在一处引用 GuideNH（`GuideNHIntegration.register()` 只挂入口，不注册页面，安全）。但 **guide md 只有 23 项功能、头部却写 21，且缺产线页**→新增 `guidenh/_zh_cn/proline.md` + `_en_us/proline.md`（frontmatter `item_ids: [ae2_qof:proline_card]`，无 GT ICON 报错风险——它是**普通物品**，`icon` 字段可省缺以 item_ids 首个为准：核对无误）；`index.md` 两侧头部 (21)/(21) → **(24)** 并追加第 24 行；en 侧同样追至 24。
2. **资产静态扫描**：
   - `presets.json` 7 条均名唯一（无重名/无超长），`JSON.parse` 7/7 通过；
   - lang 键名长度/空格/大小写 0 违例；`gradle.properties:modVersion == mcmod.info.version == 4.1.0`（@1 处稳定）。
3. **docs/PRODUCTION_LINE_MANAGER.md 指南同清**：「奇怪 +++ 」残留、"选中会有绿色边框"不实际描述改为与实施一致、importer 的三形态/ignore groups 明确、添加 `期望边界(start)` 说明。

### 实施备注
- GuideNH icon 命名模板 `(guidenh page.md frontmatter icon:)` 不写 GT 机器注册名也 OK —— proline.md 首选物品 `ae2_qof:proline_card` 是**普通 item**（坑 #20 中的 "gt:blockmachines:ID" 不适用）。
- en index 需要脚本替换核实（21→24 + 第 24 行追加），zh 索引手改核对一致。

## 待后续轮次处理（留档）

- 产线 Manager 的聊天提示（`send()`）有硬编码中文，应走 StatCollector（**建议 4.2.0**，不影响 UI 主路径，也非玩家功能）。
- 预置产线加载走 `jar:/assets/ae2_qof/proline/presets.json`；**运行时热加**（玩家自制包）需要命令/数据包支持（4.2.0 候选）。
- `ProLineData.isDirty` 字段为占位（父类已提供 `markDirty`），后续轮次统一清理（不影响行为）。
- `docs/PRODUCTION_LINE_MANAGER.md` 的快速开始章节仍写「绑定卡」，实际 **候选面板点选**也可以绑定（文档已加"（对非面板方式绑定）"限定，可再精炼）。
- 4.2.0 项：中文→注册键解析器（Waila/lang 匹配），让预置线的 AE 存量与累计列全量点亮。

## 审计流水

### 轮次 1（2026-10-16）
- 构建 `BUILD=0`（离线）；`jar:assets/ae2_qof/textures/items/proline_card.png` 与 `jar:assets/ae2_qof/proline/presets.json` 入包核对通过；7/7 预置 JSON 均可解析。
- lang 审计：zh/en 各 706 键、重复 0/0、唯一 1 对齐。
- 静默 catch 清单 7 处稽核，补日志 2 处，其余属“探测失败有 fallback 显示或三态日志承接”。

### 轮次 2（2026-10-16）
- 构建 `BUILD=0`（离线）。全搜索禁删除一致（git log --diff-filter=D 为 0）。
- UI 关闭清理 + 采样 Map GC 两处改动，均已提交 `20b2fa4`。

### 轮次 3（2026-10-16）
- 构建 `BUILD=0`（离线），`presets: 7` 入 jar 核对。
- 提交 `65134ba`：卡创造标签 + 生存配方 + README 4.1.0。
- `git log --diff-filter=D --since=本日 | wc -l = 0`（无删除复核）。

### 轮次 4（2026-10-16）
- 构建 `BUILD=0`；zh/en lang 724↔724、proline 域去重 0；jar `presets.json` 7 条 ✔；无删除复核 0。
- 提交 `3d71f86`（startExpected 持久化 + 聊天 lang 化 + 去重工具 + 变量名修正）。

### 轮次 5（2026-10-16）
- 构建 `BUILD=0`；`assets/ae2_qof/guidenh/{_zh_cn,_en_us}/{index.md,proline.md}` 入 jar 核对通过。
- 提交 `01d6c46`（GuideNH proline 页双语 + index 24 项）。

### 轮次 6（2026-10-16）：**零新增发现**
静态复检覆盖：
- 数据流断点（sampler 定时/GC/存档 load+set+markDirty/startExpected 持久键）5 项全部命中预期路径；
- ProLinePage→ProLineManager 调用面 **10/10 全部接线**；ProLinePage 内 `StatCollector` 15 处、硬编码 CJK 字符串 0 处；
- 7 条预置产线的结构体检（steps=8~49、branch/branches 三形态、start arr/map 双形态）全部在 `MAX_STEPS=64` 内且解析器覆盖；
- Guide md 36 个双语文件：title 有 36/36，链接 0 破损；
- `javap` 产物字节码：`ProductionLine.startExpected/readNbt/writeNbt`、`ProLineManager.applyPauseToStep/applyWorkAllowed/runState/touchActive` 成员全部到位，与 src 一致；
- 无删除复核仍为 0。

> 从本轮起进入"双零"判定轮：若下一轮同样零新增发现，满足 objective 的收尾条件。

### 轮次 7（2026-10-16）：**零新增发现（连续第 2 轮）→ 收尾**
- `spotlessCheck` 因 4 个 proline 文件格式违例（ItemProductionLineCard / ProLineManager / ProLineSampler / gui.ProLinePage）FAIL → `spotlessApply`（logic-neutral 41+/27-）→ 复检 `spotlessCheck=0`；提交 `81f31cb`。
- **lang 键双向绑定完整**：代码引用 20/20 全部存在于 zh/en（此前 `badge/step/sum/candidates` 是初版脚本误报——它们是键前缀非完整键）。
- jar 收尾：`build/libs/AE2-QoL-4.1.0.jar` SHA256 `2f6f2bd59620a6ba1112a52a7041dfdf8aee4d0c842e6360dcb169af9f014725`（CI 干净，无逻辑变化提交）。

### 结论（objective 达成）

**连续两轮零新增（轮 6 / 轮 7）**，静态检测、构建、spotless、javap、jar/resource/指南/审计文档六面全绿；**无删除操作全程 0 次**；git 工作树干净。剩余留档为 4.2.0 扩展项（名称解析器 / groups 语义 / NEI 按钮捕获៛ doc 层 config.generation 扩展），不阻塞本 objective。

> 提醒：本文件只做主记录，后续轮次请在末尾追加（保持上轮快照 + 新发现），便于跨会话追溯。

## 实测回归第 1 轮（2026-10-17，用户首次进游戏）

- **P0 回归**：库存统计终端右键打不开。实例日志确诊：`StockMonitorTerminal.buildUI FAILED: NPE: ModularContainer.getPlayer() because "this.container" is null`。根因 = 产线页在 **buildUI 阶段**经 `PANEL_CACHE.computeIfAbsent(sm.getPlayer(), …)` 建 5 个面板句柄；MUI2 `container` 在 buildUI 时尚未建立。
- **修法**（`4.1.0-fix1`）：句柄改在 `buildPage` 内 `syncManager.syncedPanel(...)` 直接创建（与 1/2 页签的 cover_edit/net_select 同款证人模式），`getPlayer()` 只存在于面板打开期 builder/点击回调；附带恢复被脚本误删的 `buildPresetsPanel/buildPresetList`；CHANGELOG 顶行日期更新（3.32.0-fix4 → 4.1.0-fix1，报告 P1-5）。
- 构建 `BUILD=0` + spotlessApply；jar SHA256 `15be70be…6a656`；tag `4.1.0-fix1` 已推；Release 已自动发布并核对（body = `.changelogs/4.1.0-fix1.md`，三资产齐）。
- gpt6.1sol 报告其余项去向：P0-1 验收清单文件 / P1-3 未解析徽标 / P1-4 分支口径提示 / P2 清洗项 → 并入 4.2.0 与实测记录留档（多数与 4.2.0 移交项重合）。
- **待用户复测**：同一存档，右键终端 → 第 3 页「产线」应正常打开；先前给出 A/B/C/D 测试单继续用。
