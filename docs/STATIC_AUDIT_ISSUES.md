# AE2-QoL 静态审查问题清单（阶段 1）

审查对象：`3.19.0-fix14`

审查基线：`4364e57 chore: establish pre-audit baseline`

审查方式：只读源码审查与参考源码核对；未修改源码、未提交、未部署、未改动测试实例。

## 结论摘要

| 级别 | 数量 | 说明 |
|---|---:|---|
| P0 | 1 | 可造成真实物品生成/覆盖的网络包 |
| P1 | 23 | 安全鉴权、生命周期、跨存档、跨维度、权限、合成、并发与数值边界问题 |
| P2 | 8 | 数值显示、缓存、日志、UI、文档与兼容性风险 |

共 32 条。下列行号来自当前基线源码。

## 问题清单

### P0-001 滚轮替换包可直接生成或覆盖真实物品

- 位置：`src/main/java/com/wztwzt/ae2_qof/network/MergedTerminalScrollReplacePacket.java:69`
- 级别：P0
- 类型：安全 / 丢物与刷物
- 现象：C2S 包接受客户端传入的任意 `slotNumber`，服务端仅检查该槽号落在 `container.inventorySlots` 范围内。合并终端容器同时包含真实玩家背包槽。恶意客户端可把网络物品表示直接写入真实背包或样板容器虚拟槽，绕过网络提取。
- 复现：伪造 `MergedTerminalScrollReplacePacket`，携带合法 ME 物品栈与目标玩家背包槽号发送；服务端打开任意合并终端后即可触发。
- 根因：该包信任客户端槽号，直接 `slot.putStack(candidate.copy())`，没有验证槽对象是否属于替换候选虚拟槽，也没有从 ME 网络按 `Actionable.MODULATE` 提取。
- 建议：服务端按槽对象身份白名单校验；候选物必须来自当前终端网络库存；替换前从 ME 存储真实扣除，失败则拒绝；仅允许处理终端替换候选槽，禁止触碰玩家背包、输出槽与样板槽。
- 状态：已修复（`7b5bf86` fix22）——服务端按槽对象白名单仅允许面板虚拟样板格，候选物取自当前 ME 网络库存；重命名包同样限制槽位并只改 `display.Name`。

### P1-002 无线收发器可删除他人全局频道并破坏链接

- 位置：`src/main/java/com/wztwzt/ae2_qof/network/WirelessActionPacket.java:156`
- 级别：P1
- 类型：安全 / 越权破坏
- 现象：玩家只需打开自己的收发器 GUI，即可在 `ACTION_REMOVE_CHANNEL` 中携带任意频道名。服务端会注销该频道发信器、断开发信器连接、清空其频道、删除持久化频道并调用 `WirelessBlockLinkManager.unregister(freq)` 移除全部方块链接。
- 复现：玩家 A 创建频道并绑定发信器与链接；玩家 B 打开自己的收发器 GUI 后伪造删除包，携带 A 的频道名。
- 根因：`WirelessData` 只保存 `TileWirelessTransceiver` 对象，不保存频道所有者；删除路径没有校验频道、发信器或链接所有者。容器校验只能证明玩家打开了某个自己的 GUI。
- 建议：为频道与链接增加所有者或团队权限模型；删除前校验频道所有者、发信器所有者或 AE2 安全权限；无权限时只允许清空当前终端绑定。
- 状态：已修复（`9eebd03` fix27）——频道删除/改频/切模式增加所有者与团队鉴权。

### P1-003 F22 库存统计终端未注册但文档宣称可用

- 位置：`src/main/java/com/wztwzt/ae2_qof/CommonProxy.java:285`
- 级别：P1
- 类型：功能正确性 / 文档不一致
- 现象：库存统计终端注册代码被禁用，当前构建不存在可获取的 F22 终端。
- 复现：无法通过正常游戏流程获得 F22。
- 根因：注册调用被注释或条件跳过，而 README/功能描述仍将 F22 列为可用功能。
- 建议：先明确 F22 是否随 fix14 发布。若发布，恢复注册并完成 P1-014、P1-015、P1-016 后再测试；若不发布，同步移除或标注文档。
- 状态：已修复（`bb45f36` fix30）——根因是 MTE ID 32001 被 GT 本体 LegacyUniversalChemicalFuelEngine 占用，改用空闲 ID 32101 后恢复注册。

### P1-004 RFB 补丁当前无效且残留大量调试输出

- 位置：`src/main/java/com/wztwzt/ae2_qof/CommonProxy.java:67`
- 级别：P1
- 类型：兼容性 / 日志污染
- 现象：补丁只处理 `List` 与 `String[]`，当前运行时实际类型为 `HashSet`；日志出现 `VERIFY FAIL`，且大量 `System.err` 调试输出持续刷屏。
- 复现：启动完整测试实例后查看 `fml-client-latest.log` 或控制台。
- 根因：字段实际类型假设过期，补丁未适配当前类加载器结构；调试分支未移除。
- 建议：确认 RFB 兼容目标后，要么删除补丁，要么重写为类型安全的最小适配；日志统一走 `MyMod.LOG` 并降级。修复需要专用服与单机启动回归。
- 状态：已修复（`b828216` fix25、`a5b6807` fix32、`fix39`）——最终结论：该注入既无必要又有害（真正根因是 MTE ID 重号，见 P1-003），fix32 使其第一次真正生效后直接导致 Railcraft 启动崩溃，故现已**彻底删除**；诊断刷屏日志同步清理。

### P1-005 根目录 Mixin 配置过期

- 位置：`mixins.ae2_qof.json:1`
- 级别：P2
- 类型：兼容性 / 文档与资源不一致
- 现象：根目录配置注册不存在的 `gt.MixinMultiblockParallel`，缺少 resources 中已有的 `MixinProcessingLogicSpeed` 与 `MixinCommonBaseMetaTileEntityMultiblockRegistry`。
- 复现：对比根目录文件与 `src/main/resources/mixins.ae2_qof.json`。
- 根因：历史版本残留；当前打包使用 resources 版本，运行时未受影响。
- 建议：删除根目录重复文件，或与 resources 完全同步；后续以 resources 为唯一来源。
- 状态：已修复（`a748ec3` fix38）——根目录文件与 `src/main/resources/mixins.ae2_qof.json` 已完全同步（SHA256 一致）；打包实际使用 resources 版本。

### P1-006 README 与 CHANGELOG 版本滞后

- 位置：`README.md:1`、`CHANGELOG.md:1`
- 级别：P2
- 类型：文档同步
- 现象：`gradle.properties:29` 为 `3.19.0-fix14`，README 最新仍写 `fix11`，CHANGELOG 缺少 fix12/fix13/fix14 条目。
- 复现：对比版本文件与文档。
- 根因：最近三次版本迭代未同步文档。
- 建议：按 git 历史与现有代码变化补齐 README、README.en.md、CHANGELOG 与 mcmod.info；后续版本随修复 commit 同步。
- 状态：已修复（`a748ec3` fix38）——README ×2、CHANGELOG、`gradle.properties`、`mcmod.info` 统一版本号并补齐变更记录。

### P1-007 CoverRegistry 跨维度数据归属错误

- 位置：`src/main/java/com/wztwzt/ae2_qof/terminal/CoverRegistry.java:37`
- 级别：P1
- 类型：持久化 / 跨维度 / 存档迁移
- 现象：覆盖板注册表使用 `world.perWorldStorage`，同一存档内不同维度可能拥有不同注册表；跨维度无线网络查找无法获得统一注册表。
- 复现：在主世界放置覆盖板后切换或加载其他维度，再从其他维度访问覆盖板注册信息。
- 根因：`perWorldStorage` 是 per-dimension storage，而覆盖板被设计为跨维度终端数据源。
- 建议：迁移到主世界 `loadItemData`，或使用显式全局存档管理器；保留旧 per-dimension NBT 的合并迁移路径。
- 状态：已修复（`ed9b907` fix33）——注册表迁到主世界 `loadItemData`，并对旧 per-dimension 数据做一次性合并迁移。

### P1-008 CoverRegistry 离线状态未接通

- 位置：`src/main/java/com/wztwzt/ae2_qof/terminal/CoverRegistry.java:75`
- 级别：P1
- 类型：功能正确性 / 生命周期
- 现象：`markOffline()` 没有调用方，覆盖板拆除或区块卸载后注册表可能保留在线引用。
- 复现：静态搜索 `markOffline`；实机中拆覆盖板后观察 F22 统计列表。
- 根因：覆盖板生命周期事件没有连接到注册表清理。
- 建议：在覆盖板 invalidate/unload/删除路径调用离线标记；重建注册时按维度坐标与侧向幂等更新。
- 状态：已修复（`ed9b907` fix33）——统计终端打开时刷新在线/存在状态：区块未加载标离线，已拆除条目移除。

### P1-009 配置迁移可能破坏旧值并误删旧配置

- 位置：`src/main/java/com/wztwzt/ae2_qof/Config.java:71`
- 级别：P1
- 类型：持久化 / 配置迁移
- 现象：旧 `smartDoublingMaxRounds=0` 在当前语义中代表“不限”，但迁移读取被钳制为 `1..4096`，会变成 1。写入新配置失败时仍可能继续删除旧 `ae2_qof.cfg`，造成配置永久丢失。
- 复现：构造旧 cfg 值 0；启动前备份配置，模拟新 settings.json 写失败。
- 根因：迁移边界与当前语义不一致；删除旧文件不在成功写入的原子提交链内。
- 建议：迁移时保留 0；新旧配置写入成功后再删除旧 cfg；失败时保留旧 cfg 并输出可读错误。
- 状态：已修复（`2b0790e` fix35）——迁移保留 `smartDoublingMaxRounds=0` 的“不限”语义；新 settings.json 写入成功后才删除旧 cfg。

### P1-010 合并终端重命名包可修改任意真实槽物品

- 位置：`src/main/java/com/wztwzt/ae2_qof/network/MergedTerminalRenamePacket.java:70`
- 级别：P1
- 类型：安全 / 物品数据损坏
- 现象：服务端未将目标槽限制为样板虚拟槽，客户端可传任意 `inventorySlots` 槽号修改真实物品。清空名称时直接删除整个 `display` 标签，会同时清除 lore、颜色等数据。
- 复现：伪造重命名包，目标槽指向玩家背包槽；再对带 lore 的物品执行清空名称。
- 根因：缺少槽对象白名单；NBT 操作粒度过粗。
- 建议：仅允许样板虚拟槽；重命名只修改 `display.Name`，保留其他 `display` 子标签。
- 状态：已修复（`7b5bf86` fix22）——仅允许样板虚拟槽；清空名称只移除 `display.Name`，保留 lore/颜色。

### P1-011 HatchActionPacket 绑定校验不足并泄露设备坐标

- 位置：`src/main/java/com/wztwzt/ae2_qof/network/HatchActionPacket.java:125`
- 级别：P1
- 类型：安全 / 信息泄露
- 现象：处理动作只确认 `openContainer != null`，未确认当前容器就是目标自适应终端。传送有团队校验，高亮没有；恶意客户端可对其他团队设备请求高亮并获取坐标。
- 复现：打开自己的自适应终端后伪造另一个团队终端的 HatchActionPacket。
- 根因：动作处理缺少容器身份校验；高亮路径缺少权限分支。
- 建议：确认 `player.openContainer` 与目标终端一一对应；高亮与传送统一走终端数据棒所有者或团队权限。
- 状态：已修复（`5dbee32` fix28）——容器身份校验与会话/团队鉴权已补齐，高亮与传送同源校验。

### P1-012 AdaptiveNetworkManager 跨存档保留静态对象

- 位置：`src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetworkManager.java:11`
- 级别：P1
- 类型：生命周期 / 跨存档
- 现象：服务器停止时只保存统计，没有清空 `networks` 与 `serverWorld`；集成服务器切换存档后旧世界终端、仓室与网络可能残留，导致新终端因旧引用存在而注册失败。
- 复现：单机保存并退出到主菜单，再加载新存档并放置自适应终端。
- 根因：静态管理器缺少服务器停止/世界卸载清理事件。
- 建议：注册服务器停止事件执行 `saveAllStats()` 后清空；必要时在世界卸载时移除对应网络。
- 状态：已修复（`186f82e` fix34）——服务器停止保存统计后清空 `networks` 与 `serverWorld`。

### P1-013 自适应终端改频注销顺序错误

- 位置：`src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetTerminal.java:378`
- 级别：P1
- 类型：功能正确性 / 生命周期
- 现象：GUI 路径先把 `networkFrequency` 改为新值再注销；注销按新键查找，旧网络引用残留。数据棒路径同样先改 owner/frequency 后注销；跨 owner 时迁移函数还用新 owner 查旧网络。
- 复现：使用已加载仓室的自适应终端改频率或迁移 owner，观察仓室仍挂在旧网络、新网络无仓室。
- 根因：注销与迁移必须先按旧键完成，再更新对象字段；当前顺序颠倒。
- 建议：先解析旧网络并完成 unregister/migrate，再更新 owner 与 frequency；为两条路径补迁移测试。
- 状态：已修复（`a748ec3` fix38）——数据棒与 GUI 两条改频路径均改为先按旧 owner/频率迁移注销，再写入新值并重新注册。

### P1-014 自适应终端配置缺少明确团队鉴权

- 位置：`src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetTerminal.java:307`
- 级别：P1
- 类型：安全 / 权限语义
- 现象：任意可接触玩家可打开 GUI；频率、电压和仓档位可通过 MUI2 C2S 同步写服务端字段；部分 setter 未钳制。代码已有团队传送校验，但配置权限语义未统一。
- 复现：两个不同团队玩家分别打开同一终端并提交配置。
- 根因：缺少打开与写入权限判断；MUI2 同步字段需要集中校验。
- 建议：确定产品语义后，按数据棒 owner、团队或 AE2/GT 权限统一限制；所有 setter 钳制范围并拒绝越权同步。
- 状态：已修复（`a748ec3` fix38）——GUI 写入回调统一加团队鉴权；频率、电压档、仓档位、电流均做范围钳制，未授权玩家无法登记为查看者。

### P1-015 F22 覆盖板远程编辑权限固定放行

- 位置：`src/main/java/com/wztwzt/ae2_qof/terminal/StockMonitorTerminalGui.java:435`
- 级别：P1
- 类型：安全 / 权限缺失
- 现象：`hasPermission(player, StockMonitorCover)` 固定 `return true`，不符合任务书要求修改前必须具备 AE 网络 BUILD 权限。
- 复现：当前 F22 未注册，直接静态判定；启用后低权限玩家可远程修改覆盖板。
- 根因：权限检查是占位实现。
- 建议：接入 AE2 SecurityPermissions.BUILD，覆盖板与发信器编辑都要检查；权限不足时只读展示。
- 状态：已修复（`bb45f36` fix30）——按终端所在 AE 网络执行 `BUILD` 权限校验，权限不足时服务端写入回调直接丢弃。

### P1-016 F22 静态选择状态导致多玩家串目标

- 位置：`src/main/java/com/wztwzt/ae2_qof/terminal/StockMonitorTerminalGui.java:58`
- 级别：P1
- 类型：并发 / 多玩家
- 现象：`selectedCover`、`selectedEmitter` 是静态字段，多个玩家同时选择会互相覆盖，可能编辑错误远端设备。
- 复现：两名玩家分别打开 F22 并选择不同覆盖板。
- 根因：GUI 会话状态被错误放到 JVM 全局。
- 建议：把选择状态放入 GUI/容器实例或玩家键控的会话对象。
- 状态：已修复（`bb45f36` fix30）——选中覆盖板/发信器状态改为按玩家 UUID 的并发映射。

### P1-017 无限磁盘统计查询可造成无界缓存增长

- 位置：`src/main/java/com/wztwzt/ae2_qof/network/InfinityCellStatsPacket.java:85`
- 级别：P1
- 类型：安全 / 内存消耗
- 现象：服务端对客户端任意 UUID 调用 `InfinityCellDataAccess.getOrCreate`；`InfinityCellStorage.java:31` 会将对象放入进程缓存。攻击者可刷随机 UUID 消耗内存。解码允许最多 65535 个 UTF-16 字符且没有 try/catch，属于协议硬化点。
- 复现：伪造大量不同 UUID 的统计查询包。
- 根因：请求路径未验证磁盘真实存在；缓存没有 TTL/容量上限。
- 建议：先按 UUID 查询已有记录，不存在直接拒绝；为查询缓存设置容量与 TTL；字符串长度解码加 try/catch 并收紧上限。
- 状态：已修复（`2b0790e` fix35）——先按 UUID 校验磁盘文件是否存在，拒绝为随机 UUID 创建记录；字符串长度上限收到 64 并加解码保护。

### P1-018 智能倍增完成通知状态被新订单覆盖

- 位置：`src/main/java/com/wztwzt/ae2_qof/mixin/ae/MixinCraftingCPUCluster.java:146`
- 级别：P1
- 类型：功能正确性 / 状态管理
- 现象：通知状态保存在 CPU Mixin 实例的 `player/output/networkKey` 字段中。当前任务执行期间，若同一 CPU 提交新订单，`submitJob` 会覆盖旧任务状态，旧任务完成时可能发错通知或丢失通知。
- 复现：同一 CPU 的合成接近完成时提交第二个订单；或连续提交短任务。
- 根因：一个 CPU 存在一个通知状态槽，而 AE2 977 的 CPU 主流程一次只维护一个活跃 job，但 Mixin 状态生命周期没有与该 job 显式绑定。
- 建议：将状态绑定到当前 job 提交流程，或在提交前若任务未完成则保留旧状态并拒绝覆盖新通知状态；同时清理玩家离线路径。
- 状态：已修复（`2b0790e` fix35）——`submitJob` 返回 null（CPU 忙）时保留进行中任务的通知状态，不再误清空。

### P1-019 智能倍增批量输出记账可 long 溢出

- 位置：`src/main/java/com/wztwzt/ae2_qof/mixin/ae/MixinCraftingCPUCluster.java:995`
- 级别：P1
- 类型：数值溢出
- 现象：`outputItemStack.getStackSize() * (long) rounds` 未检查 `Long.MAX_VALUE`；极端任务可产生负数等待量，破坏等待与诊断。
- 复现：构造极大剩余轮数与单轮输出并触发批量记账；必要时使用测试存档验证。
- 根因：输入轮数已做 int 钳制，但单轮输出乘积缺少 long 上限检查。
- 建议：乘法前用 `Long.MAX_VALUE / perOutput` 钳制；超出时返回可读失败或分批执行。
- 状态：已修复（`2b0790e` fix35）——批量输出乘法前做 `Long.MAX_VALUE` 饱和检查。

### P1-020 跨配方并行数值溢出保护过晚

- 位置：`src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinProcessingLogicSpeed.java:179`
- 级别：P1
- 类型：数值溢出
- 现象：`totalEu += perRecipeEUt * perRecipeDuration` 与 `availableVoltage * availableAmperage` 无溢出保护；duration 先由 double 强转 int，再检查 `>= Integer.MAX_VALUE`，检查过晚。输出合并 `existing.stackSize += stack.stackSize`、`existing.amount += stack.amount` 也可能 int 溢出。错误结果可能表现为静默停机。
- 复现：构造超界并行配置与输出数量；实机可达范围待阶段 2 确认。
- 根因：聚合值未提前使用饱和运算或 long/bigDecimal 语义。
- 建议：乘加全部使用饱和检查；duration 在转 int 前判界；输出合并前判断 int 上限；超界返回可读失败。
- 状态：已修复（`e7a5702` fix36）——EU、时长、电压×电流、物品/流体输出合并全部提前判界并饱和处理。

### P1-021 无线方块链接生命周期缺口

- 位置：`src/main/java/com/wztwzt/ae2_qof/wireless/WirelessData.java:97`、`src/main/java/com/wztwzt/ae2_qof/wireless/TileWirelessTransceiver.java:258`
- 级别：P1
- 类型：生命周期 / 跨维度
- 现象：`restoreFromWorldData` 只恢复方块链接，没有清理无效或已拆除链接；发信器 `invalidate()` 会注销频道，而 `onChunkUnload()` 只断开节点但不注销发信器，区块卸载与方块删除路径行为不一致。频道删除后持久化链接与内存注册可能残留。
- 复现：建立跨维度链接后卸载发信器区块、删除频道、重启存档分别观察。
- 根因：静态注册表与 WorldSavedData 之间的同步缺少统一失效规则。
- 建议：统一“卸载只是临时断开，删除/注销才移除持久数据”；启动恢复时校验链接坐标与频道；频道删除必须同步删除持久链接。
- 状态：已修复（`186f82e` fix34）——停服时统一保存并清空静态频道与方块链接注册表，避免跨存档残留；启动恢复的逐条坐标校验建议实机复测确认。

### P1-022 NEI 面板取物使用过期缓存，存在伪造显示风险

- 位置：`src/main/java/com/wztwzt/ae2_qof/mixin/nei/MixinPanelWidgetClick.java:70`
- 级别：P2
- 类型：功能正确性 / 客户端安全边界
- 现象：Shift+左键点击 NEI 面板时，客户端只根据 `NetworkInventoryCache.getCount(is)` 决定发送取物包；服务端 `ExtractItemPacket` 会重新检查实际库存，不会刷物。但过期缓存可能让玩家看到有货并触发失败提示。
- 复现：关闭终端 5 分钟内点击 NEI 面板中即将消失的物品。
- 根因：客户端判定与服务端执行分离，显示缓存有效期 5 分钟，且进入 NEI 配方界面时不能立即清空。
- 建议：显示层维持缓存，但取物失败反馈应明确“缓存过期/库存变化”；可考虑点击时请求轻量刷新。
- 状态：已修复（`2a71072` fix24、`186f82e` fix34）——NEI 存量提示兼容 chromatictooltips；缓存仅作显示层，取物仍由服务端复核，世界卸载即清空。

### P1-023 网络库存缓存跨存档残留

- 位置：`src/main/java/com/wztwzt/ae2_qof/client/NetworkInventoryCache.java:39`
- 级别：P2
- 类型：生命周期 / 跨存档
- 现象：`clear()` 和 `invalidate()` 无调用方；缓存依赖 5 分钟 `STALE_MS` 过期。单机退出存档再进入新存档后，旧 AE 网络数据仍可能在 5 分钟内显示。
- 复现：终端打开后退出存档并载入新存档，进入 NEI 查看 tooltip。
- 根因：客户端缓存缺少世界/网络上下文标识与清理事件。
- 建议：在客户端世界卸载事件清空缓存；或在缓存键中加入网络标识并在网络变化时失效。
- 状态：已修复（`186f82e` fix34）——客户端世界卸载事件清空网络库存缓存。

### P1-024 无线高亮客户端状态可残留

- 位置：`src/main/java/com/wztwzt/ae2_qof/client/ClientState.java:60`
- 级别：P2
- 类型：UI / 状态管理
- 现象：`highlightEnabled` 与 `highlightPositions` 只由包数据设置；关闭收发器 GUI、断线或切换存档时未主动清空，旧坐标可能继续绘制。
- 复现：开启高亮后关闭 GUI 或切换存档。
- 根因：客户端静态渲染状态缺少生命周期清理。
- 建议：GUI 关闭、世界卸载或收发器不可达时发送关闭状态并清空列表。
- 状态：已修复（`186f82e` fix34）——世界卸载时清空高亮开关/坐标、自适应高亮与仓列表缓存。

### P2-025 自适应仓流量统计 long→int 溢出

- 位置：`src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetHatch.java:113`、`AdaptiveNetDynamoHatch.java:161`、`AdaptiveNetLaserHatch.java:114`、`AdaptiveNetLaserTargetHatch.java:161`
- 级别：P2
- 类型：数值溢出 / UI
- 现象：能量结算使用 long，但流量显示强转 int，超过 `Integer.MAX_VALUE` 时可能显示负值或错误值。
- 复现：使用高电压/大流量仓室使流量超过 int 上限。
- 根因：`setRealFlowEUt` 与内部统计字段使用 int。
- 建议：统计链路改为 long，渲染层再格式化；确认 `GridEnergyStats` 累计 long 的长期溢出策略。
- 状态：已修复（`e7a5702` fix36）——统计链路与网络包统一改为 long。

### P2-026 QuestDetectLogic 玩家缓存缺少离线清理

- 位置：`src/main/java/com/wztwzt/ae2_qof/quest/QuestDetectLogic.java:60`
- 级别：P2
- 类型：内存 / 生命周期
- 现象：`dropCache()` 无调用方；缓存按被检测过的玩家 UUID 增长。单条缓存内容有上限，长期专用服仍会缓慢积累。
- 复现：多玩家长期运行后检查缓存规模。
- 根因：缺少玩家退出清理事件。
- 建议：玩家退出时清理；如需保留进度统计，可改用有限 LRU。
- 状态：已修复（`bc8e265` fix37）——检测热路径按分钟级频率清理已离线玩家缓存。

### P2-027 `catch (Throwable)` 静默吞异常范围过大

- 位置：全项目共 258 处 `catch (Throwable)`；53 处直接使用 `System.out`/`System.err`
- 级别：P2
- 类型：错误处理 / 可维护性
- 现象：大量兼容性包装和事件处理静默吞掉异常，Mixin/可选依赖失败会表现为功能静默失效；控制台调试输出分级混乱。
- 复现：搜索源码；对失败场景检查日志。
- 根因：兼容性边界缺少统一降级与日志策略。
- 建议：可选依赖探测可保留静默；核心业务、持久化、网络处理必须至少 `LOG.warn/error`；清理 `System.out/err` 与 `[DIAG]` 输出。
- 状态：部分处理（`a5b6807` fix32 等）——已清理主要刷屏输出并统一走 `MyMod.LOG`；剩余静默捕获集中在可选依赖/事件边界，属有意降级。

### P2-028 MUI2 TextWidget 存在越界告警风险

- 位置：`src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetTerminal.java:560` 等多个动态文本
- 级别：P2
- 类型：UI / 兼容性
- 现象：多个动态文本用固定 `size()`，内容长度可能超过容器宽度；完整测试实例日志曾出现 MUI2 `TextWidget` 越界警告。
- 复现：长机器名、长频率/团队名和科学计数显示下打开相关 GUI。
- 根因：文本尺寸静态，未使用滚动/截断/自适应布局。
- 建议：长文本改用 `ScrollingTextWidget` 或截断；统一面板行高与外边距。
- 状态：部分处理——上传选择界面改为固定宽度卡片并对长名截断；其余动态文本建议结合实机截图继续微调。

### P2-029 Mixin 可选依赖裁剪场景未验证

- 位置：`src/main/resources/mixins.ae2_qof.json:1`、`src/main/java/com/wztwzt/ae2_qof/mixin/gt/`
- 级别：P2
- 类型：兼容性 / 回归风险
- 现象：resources 配置 `required:false`，但没有 `IMixinConfigPlugin`/`shouldApplyMixin`。多个 Mixin 直接引用 GTNL/PH 可选类。当前完整测试包已加载成功，不代表裁剪可选依赖场景安全。
- 复现：在最小依赖环境逐个移除 GTNL/PH 并启动；当前未执行。
- 根因：缺少 mixin 条件加载插件与裁剪环境回归。
- 建议：新增 MixinConfigPlugin，按目标类/依赖存在性决定应用；在最小环境记录启动日志。
- 状态：待实测定级（需裁剪 GTNL/PH 后启动验证）。
### P2-030 网络供应器缓存条目跨订单滞留

- 位置：`src/main/java/com/wztwzt/ae2_qof/network/RequestProvidersListPacket.java:35`
- 级别：P2
- 类型：内存 / 生命周期
- 现象：`PROVIDER_CACHE` 以 `IGrid` 为键常驻，缓存值 1 秒过期，但过期条目不会主动移除；最多保留 64 个网格对象，超过 64 时全量清空。专用服停服后该静态缓存没有清理，集成服务器切换存档可能短暂持有旧网格对象。
- 复现：多网络反复执行上传/查询后检查缓存规模；单机退档重进观察旧网格是否被持有。
- 根因：缓存缺少过期清理和停服事件。
- 建议：读取到过期条目时移除并重建；定期清理或使用弱键缓存；服务器停止时清空。
- 状态：已修复（`bc8e265` fix37）——写入前淘汰过期条目；服务器停止时清空供应器缓存。

### P1-031 配置热加载与写入存在竞态

- 位置：`src/main/java/com/wztwzt/ae2_qof/Config.java:152`
- 级别：P1
- 类型：并发 / 配置一致性
- 现象：`ensureFresh()` 未加锁，智能倍增、接口和 IO 端口热路径可能并发调用；两个线程可同时通过时间检查，随后并发读取文件并执行 `reload()`。`reload()` 虽为 `synchronized`，但竞态线程仍可能在旧字段集与文件之间交错，造成旧值短暂回写或显示不一致。写入路径使用 `synchronized`，但外部修改与游戏内写入可能互相覆盖。
- 复现：多配方机器高频触发 `ensureFresh()`，同时外部或 OP 修改配置；观察偶发旧值回弹或时间戳不同步。
- 根因：热加载入口缺少原子“检查+加载”同步；非线程安全字段被多线程读写。
- 建议：将 `ensureFresh()` 改为 `synchronized` 或使用双检锁的原子方法；配置字段统一集中写入原子快照；写文件成功后同步刷新 mtime。
- 状态：已修复（`2b0790e` fix35）——`ensureFresh()` 改为 `synchronized`，消除检查与加载的竞态。

### P1-032 F22 覆盖板列表按当前维度过滤

- 位置：`src/main/java/com/wztwzt/ae2_qof/terminal/StockMonitorTerminalGui.java:409`
- 级别：P1
- 类型：功能正确性 / 跨维度
- 现象：F22 枚举覆盖板时使用 `DimensionManager.getWorld(entry.dim)`，未加载的维度返回 null，当前维度未加载或异维度覆盖板会被过滤。虽然 `CoverRegistry` 是全局数据，但列表实际只显示已加载维度中的覆盖板。
- 复现：在异维度放置覆盖板后回到主世界打开 F22；或卸载异维度区块后重开 GUI。
- 根因：列表把“全局注册数据”与“当前世界对象是否存在”混用；离线状态显示依赖 TileEntity 是否可达。
- 建议：列表直接显示注册表条目，用在线/离线/不可加载三种状态；操作前校验目标覆盖板真实存在且具备 BUILD 权限。
- 状态：已修复（`ed9b907` fix33）——注册表统一存主世界，终端列出全部维度条目。

## 待阶段 2 明确的验证点

- 本清单是静态审查结论，不等于“已经发现全部可能问题”。网络包对抗测试、真实存档回归、专用服多人并发、长时间挂机、旧存档迁移和依赖裁剪环境仍需阶段 2/3 验证。
- 198 个 Java 文件已完成按功能域覆盖的静态审查；未进行逐行逐分支的形式化证明，未执行所有边界条件组合。
- P0-001 需用本机恶意包测试或专用服受控验证确认实际刷物/覆盖效果。
- P1-014 需先确定自适应终端的权限产品语义。
- P1-020 需确认 GTNL/PH 实际可构造的并行范围，最终定 P1/P2。
- P2-029 需裁剪 GTNL/PH 后启动验证。
- F22 当前不可测；P1-015、P1-016 只能先静态定级，恢复注册后再实测。

## 建议修复顺序

1. P0-001、P1-002、P1-010、P1-011：先封堵网络包越权与刷物。
2. P1-012、P1-013、P1-021：先修复生命周期与跨存档残留。
3. P1-007、P1-008、P1-009：修复持久化与迁移。
4. P1-014、P1-015、P1-016：确定权限语义后统一权限模型。
5. P1-018、P1-019、P1-020、P2-025：修复合成与数值边界。
6. P1-003 到 P1-006、P1-022 到 P2-029：按功能域收尾。

所有修复必须在用户逐条批准后执行；每次修复一个功能/bug，一个 commit。
