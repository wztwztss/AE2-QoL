# 产线管理器 自审报告（AE2-QoL 4.1.0 · 持续迭代文档）

> 规则：不启动游戏（静态验证：构建/字节码/脚本化审计/代码审读），允许联网查资料，禁止删除文件，不能破坏其他功能。每轮自审后把「已修」与「待修」更新到本文件，并在末尾追加日志。

## 本轮已修（2026-10-16，第 1 轮）

1. **材质缺失**：新增 `src/main/resources/assets/ae2_qof/textures/items/proline_card.png`（16×16，_window 材质）。此前 `setTextureName("ae2_qof:proline_card")` 指向不存在的文件，游戏内会显示紫黑棋盘。
2. **lang 键对齐审计**：
   - `ae2_qof.threads.tab.threads` 只有 en_US 有 zh_CN 缺 → zh 已补（线程页 lang 是历史残留，保留键）。
   - `ae2_qof.wireless.bind.success` 同键双值 → 拆两次：旧的 "已绑定频道: %s" 保留给带 `%s` 格式调用（ItemWirelessConnector），并新增 `ae2_qof.wireless.bind.network.success`（ tok 无线能量输入/输出端子两处引用已同步改 key），避免**文件后置键覆盖前键而丢失 %s 格式**的真 bug。
   - `ae2_qof.proline.*` 中英 66↔66 键匹配通过（含 `.bound`、`.no_output`、`.sum.in/out/cum`、`.run_tag`、`.step.type/out` 扩展）。
3. **Prod 内部硬编码中文 → lang 键**（server 侧硬编码字符串在专用服不减义）：
   - 总输入/总输出行（`输入/输出/累计`）、`在产` 标签、`类型:` / `主产物:` tooltip、`无产物`、`（已绑）` 全部改走 StatCollector 与 lang。
4. **ProLineImporter 多形态 start**：
   - `start`: JsonArray / **旧对象 map (`{"海晶酸":-80000}`)** 双形态都处理；未解析项进 errors（不静默丢）。
   - 修复 `!obj.has("steps")` 导致 start 被顶掉的 bug（一页 JSON 同含 steps/start 时 start 会被跳过）。
   - `jPrimitiveLong` 拆出，数字包装/字符串 double 都覆盖。

## 待后续轮次处理（留档）

- 产线 Manager 的聊天提示（`send()`）有硬编码中文，应走 StatCollector（**建议 4.2.0**，不影响 UI 主路径，也非玩家功能）。
- 预置产线加载走 `jar:/assets/ae2_qof/proline/presets.json`；如果将来要在**运行时**热加（玩家自制包），需要加命令/数据包支持（4.2.0 候选）。
- `ProLinePage.PANEL_CACHE` per-player key 不会清理；长时间运行会多几个条目（对象小，可忽略；如需清理可在 PanelCloseListener 里 remove）。
- `ProLineData.isDirty` 字段是占位（父类 WorldSavedData 已 provided `markDirty`）；可在后续轮次统一删除（不影响行为）。
- 玩家名在 Manager 的 log（`player.getCommandSenderName()` 已使用），但 `createLine(...)` 仍接受可选参数 `dim,x,y,z` 终端锚点；确认 UI 侧传的终端世界坐标是**当前打开终端的那个方块**（默认页 3 手动触发点，符合 h3 `StockMonitorTerminalGui#build` 上下文）。
- `docs/PRODUCTION_LINE_MANAGER.md` 的快速开始章节仍写「绑定卡**必须**」，但实际 **候选面板点选**也可以绑定（文档已加"（对非面板方式绑定）"限定，可再精炼）。

## 审计流水

### 轮次 1（2026-10-16）
- 构建 `BUILD=0`（离线）。
- lang 审计：`ae2_qof` 域 zh/en 705/706→补齐后 706/706，重复 0/0。
- 材质：新增绑定卡 16×16 透明 PNG；`unzip -p jar assets/ae2_qof/textures/items/proline_card.png` 入包核对（下次轮补充做 jar 级 list）。

> 提醒：本文件只做一次性记录，后续轮次请在末尾追加（保持上轮快照 + 新发现），便于跨会话追溯。
