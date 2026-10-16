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

> 提醒：本文件只做主记录，后续轮次请在末尾追加（保持上轮快照 + 新发现），便于跨会话追溯。
