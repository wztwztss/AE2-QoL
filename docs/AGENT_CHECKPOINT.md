# AGENT_CHECKPOINT 跨智能体接力状态文档

**放置位置**：`docs/AGENT_CHECKPOINT.md`（原模板要求放根目录，应项目方归类要求改放 `docs/`；**根目录不存在副本**——2026-09-25 核实，以 `docs/` 下这一份为唯一来源）
**适用范围**：DeepSeek Harness（DSH）/ OpenCode / Codex / ShunCode 等所有 AI 编程智能体
**强制铁则**：任何智能体启动工作，第一步必须读取本文档；任务中断、切换智能体、阶段性完成时，必须记录自身使用的工具平台与底层模型，并更新全部进度信息。
**更高的第一优先**：先加载 skill **`ae2qol-workflow`**（`.dsh/skills/ae2qol-workflow/SKILL.md`）——它规定了本项目的工作流程（先确认问题到 99% → 口令 → 只读源码取证 → 口令 → 才可改文件），并汇总了构建/部署/文档义务与已踩坑位。本文档负责**状态与进度**，该 skill 负责**流程与纪律**。

---

## 一、当前会话元数据（每次启动工作必须先填写）

|项目|填写内容|
|---|---|
|工具平台|DeepSeek Harness（DSH Web GUI）|
|模型信息|DeepSeek-V4.1-Flash|
|工作分支|master；本轮起点 `5f89641`（3.23.1 交付并部署），工作树干净、与 origin/master 同步（未推送）；本轮产出 **3.23.2**|
|启动时间|2026-09-28（Asia/Shanghai，回退/删除轮：**3.23.2**，紧接用户对 3.23.1「复制方块的结果完全不对、箱子里的东西不能一起复制」的反馈）|
|本次会话目标|**按用户要求先只读调查、再删除自研的「创造模式 Ctrl+中键复制方块完整 NBT」= 3.23.2**。用户反馈：「你这获取的东西完全不是正确的，箱子里放东西也不能一起复制，mc原版就有中键复制，只是让你加上nbt，这个功能好像原版就有，你调查一下，如果有的话就删除吧」。**只读取证结论**（证据 = RFG 反编译源码 + 实例 jar 字节码）：① **原版/Forge 1.7.10 没有这个能力**——`Minecraft.func_147112_ai()`（middleClickMouse）本体已被 Forge 掏空，只剩 `ForgeHooks.onPickBlock(...)` + 创造模式槽位同步；`ForgeHooks.onPickBlock` 无 Ctrl 分支、无 TileEntity 写入；`ItemBlock.placeBlockAt` 无 `BlockEntityTag` 还原；MC+Forge 共 1833 个 `.java` 里字符串 `BlockEntityTag` 出现 **0 次**（该机制 1.8 才有）；② **本整合包私货 SNL 已自带完整同款**——`MixinMinecraft.onBeforePickBlock`（`@Inject(method="func_147112_ai", at=HEAD, cancellable=true)`）→ `ClientUtils.onBeforePickBlock` 按 Ctrl 分流 → `onPickBlockNBTRange` → C2S `GetTileEntityNBTRequestPacket` → S2C `TileEntityNBTPacket.apply()` 生成带 `BlockEntityTag` + `(+NBT)` lore 的物品（**仅创造模式**入快捷栏），放置由 SNL 的 `MixinForgeHook.preOnPlaceItemIntoWorldRewrite` 写回；③ Hodgepodge `modernPickBlock=true` **只做生存模式选取、无 NBT**（排除）。**我们的实现为什么错**：Ctrl 分支挂在 Forge `InputEvent.MouseInputEvent`（**不可取消**）上，`return true` 只跳过自家 AE2 取物补发，**拦不住 SNL**，两条链路同时写同一个快捷栏格 ⇒ 用户看到的错乱。**处理**：`git revert --no-commit 5f89641 0bcc12d`（**-552 行**：删 `blockcopy/BlockCopyService.java`、`network/BlockCopyRequestPacket.java`、`mixin/mc/MixinItemBlockOnItemUse.java`、`ModNetwork` 包注册、`PickBlockCompatHandler` 的 Ctrl 分支；**保留 fix54 的 `ae2qol$sendPickBlockIfApplicable()`**），版本 `3.22.0-fix53` → **`3.23.2`**（`gradle.properties` + `mcmod.info` 两处条目），文档同 commit：CHANGELOG 记录 (50)、`MOD_MAP.md` 已废弃方案行、`mixin_notes.md` 已知风险第 9 条、README/README.en 本版变化。产物 `build/libs/AE2-QoL-3.23.2.jar`（**1,796,713 B**，SHA256 `54423272EA385938FF88EB21927A7442271D46FCCFC266E800FC1CA8BB027A8D`），清 `build\classes|tmp\mixins|libs` 后全量构建 `BUILD SUCCESSFUL`（无管道取码 `EXIT=0`），产物内三个类与 mixin json/refmap 里的相关条目**均已消失**。**待用户实测 4 项**（CHANGELOG (50) §七）。**已于 2026-09-28 部署到实例**（旧 3.23.1 移入 `_ae2qol_jar_backup`，实例内 SHA256 与本地一致，`mods` 内仅一份；重启后生效）。**同日追加（只读）**：Wireless Nexus 许可审计（实测 **LGPL-3.0**，非 MIT）+ 吞并可行性审计，全部只写文档、未动代码 ⇒ 见 CHANGELOG 记录 (51)、`docs/DESIGN_wireless_nexus_merge_audit.md`、`docs/THIRD_PARTY_NOTES.md` §六。**再追加**：用户报「服务器上打不开 Wild/生成器界面」⇒ 依服务端日志定位为**服务端加载到客户端类**（`SideTransformer` 拒绝 `GuiScreen`）⇒ **3.23.2-fix1** 修复并部署本地实例，见 CHANGELOG 记录 (52)。|
|上一轮会话目标（3.20.0-fix38 轮，历史）|**修 `输出种类=1` 的真正上游 = 3.20.0-fix38**。用户实测 3.20.0-fix37 报"完全没修，一模一样"。日志取证：**加载的确实是 3.20.0-fix37**、`输出种类=1` 依旧，而 3.20.0-fix37 新加的"输出槽没找到可替换项"WARN **一次都没出现（计数 0）**——这条反证说明**那段代码根本没执行**：替换逻辑整体在 `if (outStack != null)` 内，而 `outStack` 只在 `outputPrefix` 非空时求值。**根因 J**：`templateOutputPrefix` 用"模板输出**材料名** == 模板输入材料名"选前缀，而 GT 板材**同时注册 `plateIron` 与 `plateAnyIron`**，取到后者时材料名被解析成 `AnyIron` ⇒ 判等失败 ⇒ 返回 null ⇒ `outputPrefix` 空 ⇒ 输出永不改写 ⇒ 几百张全保留模板输出（铁板）。修复：`collectOutputPrefixCandidates` 从模板输出槽**全部矿辞名**收集前缀候选（`plateIron`→`plate`，`plateAnyIron` 兜底切 `plate`），逐材料挑**第一个"前缀+材料"在矿辞表真实存在**的（`plateAnyCopper` 不存在则回落 `plateCopper`）；候选为空时 `logNoOutputPrefixOnce` WARN；`displayOutputPrefix`（窗口输出行）改用同一套；新增 `logSampleOnce` 每 JVM 3 条 `展开样本：material=… prefix=… in=… out=… 产出out=…` 自证。产物 `build/libs/AE2-QoL-3.20.0-fix38.jar`（1,766,805 B，SHA256 `FCA5FECA…`）。**待实测 4 项**（CHANGELOG 记录 (43) 第四节）|
|上一轮（历史）|工具：DeepSeek Harness｜模型：DeepSeek-V4.1-Flash：3.20.0-fix37（输出槽替换判据），`6e5cf04` 已部署；用户实测"完全没修"。|
|上一轮会话目标（3.20.0-fix37 轮）|**修「几百张具体样板全部输出同一块铁板」= 3.20.0-fix37**。用户按上轮要求用 3.20.0-fix36 在三台机器各复现一次后退出游戏。日志一击命中：三族注册行全部 **`输出种类=1 样本=[铁板, 铁板, 铁板]`**，AE 回读 **`AE 合成表条目=388；抽样 3 条命中 3 条`** ⇒ AE **并没有挡我们**（上轮怀疑的"注册侧/网格"方向被排除），是**我们的数据错**。**根因 I**：`SmartWildcardExpander.buildConcretePattern` 替换输出槽的判据写成了 `oreInfo(模板输出).material.equals(候选材料)` —— 模板输出的材料名恒为 `Iron`，于是**只有候选恰好是铁时才替换**，其余几百张**全部沿用模板输出（铁板）**；这也解释了 GT 样板仓"能看到全部样板、能下单但不合成"（AE 按各材料算计划、机器收到的却全是铁板 ⇒ `insertItemsAndFluids` 走不通 ⇒ GT 记 `SOMETHING_STUCK` 返回 false ⇒ 任务卡住）。修复：新增 `matchesOutputPrefix(stack, prefix)`（该槽的某个矿辞名以本规则输出前缀开头，如 `plate` 命中 `plateIron`/`plateAnyIron`）只替换第一个命中槽、副产物保持模板原样、未命中限频 WARN（不静默）；3.20.0-fix36 的诊断保留但改为只在限频触发时才算。产物 `build/libs/AE2-QoL-3.20.0-fix37.jar`（1,764,914 B，SHA256 `D4EAC917…`）。**待实测 4 项**（CHANGELOG 记录 (42) 第四节）|
|上一轮（历史）|工具：DeepSeek Harness｜模型：DeepSeek-V4.1-Flash：3.20.0-fix36（诊断包：输出种类数 + AE 合成表回读），`5408941` 已部署；用户复现后交付日志。|
|上一轮会话目标（3.20.0-fix35 轮）|**修「两套通配模组互斥（都只剩一张）」= 3.20.0-fix35**。用户实测 3.20.0-fix34 后报"现在是都只能识别到一个，之前原版可以正常识别全部锭出板"。**先确认好消息**：3.20.0-fix34 日志 `produced=512 matched=4393`／`produced=396` ⇒ 我们自己的展开**已经成功**（3.20.0-fix32 矿辞前缀＋3.20.0-fix33 下标对齐＋3.20.0-fix34 匹配函数三处全生效）。**回归真因**：用户无意中跑出 A/B/A 对照 —— 3.20.0-fix32（我们总是 `ci.cancel()`）两边都只剩一张 → 3.20.0-fix33（我们展开失败⇒未接管）**原版恢复全部** → 3.20.0-fix34（展开修好⇒又接管）两边又都只剩一张 ⇒ **`CallbackInfo.cancel()` 是共享标志：我们 cancel 掉 `provideCrafting`，同机原版 WildcardPattern 的展开会被一并跳过**。3.20.0-fix33 那次"互不干扰"只是碰巧（因为没触发接管），机制并未修对。修复：GT/GTNL/AE2 ME 接口三处**只追加、永不 cancel**（去掉 `cancellable` 与全部 `ci.cancel()`；不再替对方注册普通槽位）；顺带修两个**从未打印过**的限频日志（哨兵 `Long.MIN_VALUE` ⇒ `now-last` 溢出成负 ⇒ 条件永不成立，与检查点记过的 `RowCache` 哨兵坑同类），初值改 `0L`。字节码核对：三个 mixin 的 `CallbackInfo.cancel` 次数**均为 0**。产物 `build/libs/AE2-QoL-3.20.0-fix35.jar`（1,759,116 B，SHA256 `4F80BEBD…`）。**待实测 4 项**（CHANGELOG 记录 (41) 第四节）|
|上一轮（历史）|工具：DeepSeek Harness｜模型：DeepSeek-V4.1-Flash：3.20.0-fix34（通配匹配恒 false 的根因），`a6f911d` 已部署；用户实测后报"两套模组都只剩一张铁板"。|
|上一轮会话目标（3.20.0-fix34 轮）|**修"通配匹配恒为 false" —— 本模组通配样板"从未成功过"的最后一个根因 = 3.20.0-fix34**。用户实测 3.20.0-fix33（自行部署）反馈：识别到了，但**只有原版模组的样板能展开**，我们那张"只能识别到铁板一个"（NEI 按铁板转移 ⇒ 只登记模板一张）。日志把范围收窄：`规则槽位越界` **0 条**、`模板重建后已重排规则槽位` 1 条（3.20.0-fix33 下标对齐生效）、展开时规则状态**完全正确**（`规则 slot=0 mode=矿辞 matcher='ingot*' outMatcher='plate*' amount=1`），但 `matched=0 reason=no-material-matched` ×24。**根因 H**：`SmartWildcardState.matches` 把**整个正则串** `toLowerCase()`，把 `Pattern.quote` 的 `\Q…\E` 压成 `\q…\e` ⇒ `PatternSyntaxException: Illegal/unsupported escape sequence`，而 `catch` **静默返回 false** ⇒ **所有**通配匹配恒 false：① 展开器材料枚举恒空（产出 0 ⇒ 回退模板 ⇒ "只能识别铁板一个"）；② `acceptsCandidate` 的黑/白名单恒 false；③ `excludedByRule` 的规则级排除恒 false。已用最小 Java 用例实证（`%TEMP%\ae2qol-regex-probe`：修复前 `matches(ingot*,ingotIron)=false`，修复后 true）。修复：逐字符显式转义元字符 + `Pattern.CASE_INSENSITIVE`，异常记 WARN（不静默）。产物 `build/libs/AE2-QoL-3.20.0-fix34.jar`（1,759,863 B，SHA256 `A96D4C3A…`）。**待实测 4 项**（CHANGELOG 记录 (40) 第四节）|
|上一轮（历史）|工具：DeepSeek Harness｜模型：DeepSeek-V4.1-Flash：3.20.0-fix33（下标对齐 + 与两套模组互不干扰），`5be1d26`；用户自行部署后报"只有原版能展开、我们只认铁板一张"。|
|上一轮会话目标（3.20.0-fix33 轮）|**修 3.20.0-fix32 实机暴露的下标错位与两模组互扰 = 3.20.0-fix33**。用户实测反馈：加号只填输入不填输出、放进去仍按样板自己合成、**原版 wildcardpattern 的样板放同一总成也只识别到一个**。日志（3.20.0-fix32 会话）证据：`规则槽位越界：slot=1 inSize=1 matcher=ingot*`、`展开失败诊断：材料交集为空`、`produced=0 reason=no-material-matched`（16 条）、推送多为"输入 1 条、输出 0 条"。**根因 F**：机器侧 `rule.slot`＝模板输入下标（模板 `[电路,铁锭]` ⇒ slot=1），而桥旧实现**按"有规则的条目"排行**，电路行从未进界面 ⇒ 行号≠模板下标；自愈又从"只有规则行"重建模板 ⇒ `in=1` ⇒ slot=1 越界 ⇒ 规则被丢 ⇒ 材料集空 ⇒ 产出 0 ⇒ 机器回退模板；输出条目也落不到正确的行；`pullFromWild` 还用"已收集规则数"当 slot ⇒ 每保存一次漂移一次。**根因 G-3**：javap 实证原版 WildcardPattern 的 `MTEHatchCraftingInputMEMixin` 同样在 `provideCrafting` HEAD + `ci.cancel()` 全量接管，并对**共享的** `patternDetailsPatternSlotMap` 做 `removeIf` 清理；我们旧实现**无条件 cancel** 会把对方的展开压成一张。修复：① 桥改为**以模板行为单位**（界面行=模板槽位，非规则行占位空行保行号；输出行按该行自己的模板输出推 `plate*`；`pullFromWild` 用行号当 slot；模板重建后 `remapRuleSlots` 重排并留日志）；② GT/GTNL 处理器**先扫描**，本机没有我们的已配置样板时**完全不介入**（＋外来样板限频诊断）；③ 两处 `pushPattern` 守卫加**反向自愈**（共享映射被第三方清掉时补回并放行）。产物 `build/libs/AE2-QoL-3.20.0-fix33.jar`（1,759,609 B，SHA256 `D7D18D82…`）。**待实测 5 项**（CHANGELOG 记录 (39) 第四节）|
|更早一轮（3.20.0-fix32）|工具：DeepSeek Harness｜模型：DeepSeek-V4.1-Flash：3.20.0-fix32（矿辞前缀本地化键 + 原生模板被删 + 输出行），`a2043f3` 已部署；用户实测后报"加号只填输入、输出不填、两套模组的样板都只识别一个"。|
|本次会话目标|**修 3.20.0-fix31 实机暴露的两个更深根因 + 一处界面差距 = 3.20.0-fix32**。用户实测给了 4 张截图并要求「你自己也去查一下日志吧」。日志计数给出决定性证据：`GT 通配槽位展开为空…reason=template-in-out-missing` **23 次**（⇒ 3.20.0-fix31 的判据/接线**已生效**，卡在展开产出 0）、`NEI 加号：已就地把推导结果写进 Wild 窗口（槽位 7…）`＋`写回成功（指定槽位）：slot=7 rules=1`（⇒ 3.20.0-fix31 的加号修复**已生效**）。三个根因：**E-1** `OrePrefixes.getOreprefixKey()` 返回的是 GT **本地化键**（javap 实证常量池含 `gt.oreprefix.`，值形如 `gt.oreprefix.ingot`）⇒ 规则被写成 `gt.oreprefix.ingot*`（用户截图里那串 `gt.orepr...`），**永不匹配矿辞名**；三处调用点（推导器 / 展开器 `oreInfo` / MUI2 编辑器拖入）全中。**E-2** 搬进来的 Wild 代码在首次初始化时 `tag.removeTag("in"/"out")`（参考实现自己的数据模型），而本模组展开器以原生 `in`/`out` 当模板 ⇒ 模板被删 ⇒ 产出恒 0 ⇒ 回退注册模板那一张 = 用户看到的「AE 直接按这个样板自己的合成」。**E-3** 输出行永远空白（`pushToWild` 用 `rule.outMatcher`，推导器不填）。修复：① 新增 `SmartWildcardExpander.oreDictPrefixOf`（矿辞名减材料名反推前缀，与 `matcherLiteralPrefix` 互逆）并统一三处；② `cleanupLegacyPatternSlots` 对我们的样板不再删（原版 mod 行为不变）＋新增 `WildcardBridge.ensureNativeTemplate` 模板自愈（四个机器入口 + push/pull + AE 真身），**存量坏样板免重配**；③ `displayOutputPrefix` + `derivedOutputMatcher` 让输出行显示 `plate*`。产物 `build/libs/AE2-QoL-3.20.0-fix32.jar`（1,756,835 B，SHA256 `0F3699B9…`）。**待实测 5 项**（见 CHANGELOG 记录 (38) 第五节）|
|上一轮（历史）|工具：DeepSeek Harness｜模型：DeepSeek-V4.1-Flash：3.20.0-fix31（通配样板三类静默失效 + 加号写回 + 两页签 + mui 手势），`d0f4950` 已部署，用户实测后报「矿辞串是 gt.orepr…、放进去 AE 按样板自己合成」。|
|上一轮会话目标（3.20.0-fix31 轮）|**修用户实测 3.20.0-fix30 报的三条 + 一条我侦察到的静默失效 = 3.20.0-fix31**：① 有规则的通配样板放进**任何**总成都不被识别（GT / GTNL / PH 22069 / MK.II / 我们的 MK.III 全试）；② Wild 窗口里 NEI 加号无效（配方不落界面）；③ 电路与不消耗物品**没有独立页**（用户拍板：电路改独立页 4 列×6 行 + 撤掉 3.20.0-fix30 底部电路带；不消耗物品复用自带 NEI 拖入控件并写自己的 `NonConsumed`）；④ `mui.MixinItemSlotWildcardGesture` 注入失败（Shift+中键电路选择器从未生效）。流程：加载 skill → 只读侦察（实例 jar SHA256 / 日志计数 / 原版 mod 字节码）→ **三轮 `ask_user_question` 把问题确认到 99%（用户拍板 16 项）** → 方案确认 → 实施 → 构建 → 文档 → 提交/部署。**根因**：C = ①GT/GTNL 的槽位 `rebuild()` **全仓零调用者**（结构性，与玩家数据无关）+ ②四处入口判据要求"已有我们的 NBT"而该子树只在 Wild 窗口保存时写入（实测 `pullFromWild` 全场 2 次且都是"规则 0 条"），且 3.20.0-fix29 的懒同步写在 `expand()` 内部 ⇒ 对"缺 NBT"永远不可达，判否分支还**静默**；A = 加号写到"背包里第一张样板"而非窗口那张 + 界面不刷新 + 窗口旧内存态在保存时把刚写入的规则覆盖成 0；D = 回调类型写成 `CallbackInfo`，而 `ItemSlot.onMousePressed` 返回 `Interactable$Result`。产物 `build/libs/AE2-QoL-3.20.0-fix31.jar`（1,753,773 B，SHA256 `EB5FBC22…`），字节码已核对（`rebuild` 调用点、`SmartWildcardGate` 调用、`CallbackInfoReturnable`）。**待用户实测 6 项**（见 CHANGELOG 记录 (37) 第五节）|
|更早一轮（3.20.0-fix28～3.20.0-fix30）|工具：DeepSeek Harness｜模型：DeepSeek-V4.1-Flash：3.20.0-fix28～3.20.0-fix30（GUI handler 顶掉、写回判据、底部电路带），`e12ae06` 已部署到 b3 实例；用户实测后报本轮三条缺陷。|
|上一轮启动时间（历史）|2026-09-26（Asia/Shanghai，新功能轮：编程样板输入总成 MK.III）|
|上一轮会话目标（历史，3.20.x 轮）|**新增「编程样板输入总成 MK.III」**：ProgrammableHatches「编程样板输入总成」（MTE 22069）的扩容克隆版，样板槽 36 → **144**，样板窗改成 9 列 × 9 可见行的可滚动网格，只在装了 PH 时存在。严格按用户协议推进：先反复提问确认需求（7 项产品决策全部由用户拍板）→ 只读取证（PH 源码 + 实例 jar 字节码 + AE2/GT/MUI2 三层 API）→ 用户说「确认方案，开始修改」后才动代码。**3.20.0 首次实测失败**（可选依赖守卫把 PH 的 modid 误写成包名前缀 `proghatches`，物品从未注册且无日志），已定位并修复为 **3.20.0-fix1**（真实 modid + 关键类判据 + 三条分支日志），产物 `build/libs/AE2-QoL-3.20.0-fix1.jar`（SHA256 `D0F00177…`）**已部署到 b3 实例**（mods 内仅一份）。**已实测通过（用户：「样板确实扩充了没问题」）；日志证据见 CHANGELOG 记录 (23) 第六节。**随后按用户「你正常修就行，修完推」又完成 **3.20.0-fix2**：修 GuideNH 指南页 5 页 × 中英的 `icon:`/`item_ids:` 错误（GT 机器真实注册名是 `gregtech:gt.blockmachines:<MTE ID>`，原写成了 `ae2_qof:<机器名>`），并做了全量对照审计；产物 `build/libs/AE2-QoL-3.20.0-fix2.jar`（SHA256 `3B7189EF…`）。**3.20.0-fix2 未部署到实例**（纯资源修正）。随后用户报「库存统计终端打开无法连接 AE / 没有 nexus 的连接 UI / 无法实时修改」→ 完成 **3.20.0-fix3**：终端 GUI 双端构建重写（`GenericListSyncHandler`+`DynamicSyncedWidget` 快照渲染）、发信器枚举 API 修正（`node.getMachine()`）、列表改可滚动并取消 5 行上限、编辑值全走 SyncValue、新增 Nexus 缺失回退面板；产物 `build/libs/AE2-QoL-3.20.0-fix3.jar`（SHA256 `85BB3AE7…`）+ **3.20.0-fix4**（同版本行为不变，仅给两个列表收集器补"首次失败 WARN"，让"列表为空"与"枚举出错"可区分；SHA256 `1AF11606…`）；**3.20.0-fix4 已部署到实例（mods 内仅一份）**。随后按用户「优化 UI + 加高亮与传送（参照自适应电网终端）+ 修汉化」完成 **3.20.0-fix5**：行内【高亮】【传送】两按钮（客户端回调发坐标、服务端重新解析并做会话+BUILD 双鉴权）、高亮 10 秒自动清除、传送支持跨维度与安全落点、物品名与行内缩写全部汉化；产物 `build/libs/AE2-QoL-3.20.0-fix5.jar`（SHA256 `5590DCC0…`）；**3.20.0-fix5 待游戏内验收 5 项**。随后用户拍板「覆盖板列表只看本终端所连网络」→ 完成 **3.20.0-fix6**（改用 `CoverRegistry.getByNetwork(terminal.getNetworkId())`；被过滤数量经 SyncValue 下发，空列表时显示"另有 N 个属于其他网络"；扫描收拢进 `CoverScan` 共用一次遍历）；产物 `build/libs/AE2-QoL-3.20.0-fix6.jar`（SHA256 `74BBBA2A…`）。用户实机截图又报两处：**行内名称整列空白 + 发信器数量改不动** → **3.20.0-fix7** 修复（① MUI2 `ButtonWidget extends SingleChildWidget`，`child()` 会 dispose 旧子控件 ⇒ 名称被数值挤掉，改走 `overlay(IKey)`；② 按钮内文本吞点击（PH `NonInteractiveText` 同坑）⇒ 点行打不开编辑；③ `LevelType` 常量实为 `ITEM_LEVEL`/`ENERGY_LEVEL` ⇒ 类型恒显示"未知"，改前缀匹配）；产物 `build/libs/AE2-QoL-3.20.0-fix7.jar`（SHA256 `09BDD665…`）。随后用户报「智能倍增在**云上专用服务端**不生效（开关能勾住、合成仍一次一轮），同一 jar 单人正常」→ 完成 **3.20.0-fix8**：根因是三处开关 mixin 在 `mixins.ae2_qof.json` 的 **client 段**，而 `BooleanSyncValue.allowC2S()` 要求服务端存在同名同步处理器 ⇒ 专用服务端上写入被 MUI2 静默丢弃（两条丢弃分支都不打日志）⇒ 服务端开关恒 false ⇒ CPU 静默回退一次一轮；单人正常是因为同一客户端 JVM 里 client 段 mixin 也变换了该类。修复：改为「客户端只报坐标 → 服务端重定位并校验后写入 + S2C 权威回读」，并补上应用/未生效诊断；产物 `build/libs/AE2-QoL-3.20.0-fix8.jar`（SHA256 `B5A4E791…`），**已被用户实测确认生效**（服务端日志出现「智能倍增开关 = true …（样板介质=true）」）。用户随后反馈"倍增有效果但不是一键全发、而是几万几万一发" ⇒ 取证为**设计上限**（功率钳制显式封顶 4096 轮，#51 的 O(P) 探测与 #73 的客户端淹没都要求分批）⇒ 完成 **3.20.0-fix9**：`Config.smartDoublingPushCap`（键 `smart_doubling_push_cap`，默认 4096，热加载 + 配置页 + `/ae2qof status`），并把 3.20.0-fix8 诊断"关闭开关未清期望登记"的误报修掉；产物 `build/libs/AE2-QoL-3.20.0-fix9.jar`（SHA256 `8DB5EBBC…`）。随后用户提出**新需求：一个比现有两个参考模组更好用的通配样板**（NEI 加号自动推导 + 每槽电路）→ 调研 + 需求确认 12 项后进入 **3.20.0-fix10**，**M1 已完成并提交**（数据模型/展开器/物品/AE2 接口接管/自测命令 + `smart_wildcard_expand_cap`），M2（NEI 加号 + 可视化）与 M3（每槽电路）**未交付**。|
|上一轮（更早）|工具：DeepSeek Harness｜模型：DeepSeek-V4.1-Flash：fix50/51/52+54 三问题定位与修复，正式版 `3.19.0-fix54` 已部署到 b3 实例并推送到 `origin/master`。|

---

## 二、项目总目标

GTNH 2.9.0-beta-3（Minecraft 1.7.10 Forge + Java 17/25）环境下的 AE2 附属功能模组 **AE2 QoL**（modId `ae2_qof`，版本 `3.19.0-fix49`）的交付与质量整改：在兼容原生 AE2（本次已核对的 rv3-beta-1050-GTNH）与格雷科技本体（5.09.54.133）/GTNL/PH 机制的前提下，完成 F1~F22 全部功能的质量检测、缺陷定位与修复，最终产出一个可稳定运行于单机与专用服的发布版本（含合并终端三形态、NEI 样板自动上传、合成完成通知、智能倍增、自适应电网、库存检测覆盖板等）。当前阶段以「先审计、再修复、逐条提交」为推进方式。

---

## 三、全局已完成清单

> 按完成时间倒序排列，均标注产出文件路径。历史结论保留原貌，不等于本版验证结果。

**3.20.0-fix38 交付（输出前缀改"存在性驱动" = `输出种类=1` 的真正上游，2026-09-27）**：3.20.0-fix37 实测"完全没修"驱动。
日志反证：3.20.0-fix37 已加载、`输出种类=1` 依旧，而新版 WARN"输出槽没找到可替换项"计数 **0** ⇒ 那段代码从未执行
（替换逻辑在 `if (outStack != null)` 内，而 `outStack` 需要 `outputPrefix` 非空）。根因 J：`templateOutputPrefix`
用材料名判等，GT 板材的 `plateAnyIron` 使材料名解析成 `AnyIron` ⇒ 判等失败 ⇒ 前缀 null ⇒ 输出永不改写。
修复：`collectOutputPrefixCandidates` 收集前缀候选（全部矿辞名 + 首个大写字母兜底）+ 逐材料按
"前缀+材料是否真实存在"选择（`plateAnyCopper` 不存在则回落 `plateCopper`）；候选为空 WARN；窗口输出行同源修复；
`logSampleOnce` 自证三行。构建 `BUILD SUCCESSFUL`（`EXIT=0`）；产物 `build/libs/AE2-QoL-3.20.0-fix38.jar`
（1,766,805 B，SHA256 `FCA5FECA…`）。**待实测 4 项**（CHANGELOG (43)）。

**3.20.0-fix37 交付（修「几百张具体样板全部输出同一块铁板」= 整条通配链的真凶，2026-09-27）**：3.20.0-fix36 实测驱动。
诊断日志一击命中：三族注册行全部 `输出种类=1 样本=[铁板,…]`；AE 回读 `AE 合成表条目=388；命中 3/3`
⇒ AE 没挡我们，是**我们的数据错**。根因：`SmartWildcardExpander.buildConcretePattern` 的输出槽替换判据
写成 `oreInfo(模板输出).material.equals(候选材料)`（模板输出材料名恒为 `Iron`）⇒ 只有铁候选被改写，
其余几百张沿用模板输出（铁板）⇒ AE 里只有铁板可合成；GT 样板仓"能下单但不合成"同源
（AE 计划与实际推入材料对不上 ⇒ `insertItemsAndFluids` 失败 ⇒ GT 记 `SOMETHING_STUCK`）。
修复：`matchesOutputPrefix`（矿辞名前缀匹配）只替换第一个命中槽 + 未命中限频 WARN；诊断保留但限频。
构建 `BUILD SUCCESSFUL`（`EXIT=0`）；产物 `build/libs/AE2-QoL-3.20.0-fix37.jar`（1,764,914 B，SHA256 `D4EAC917…`）。
**待实测 4 项**（CHANGELOG 记录 (42)）。

**3.20.0-fix35 交付（不再 cancel 原方法 = 修回"两套通配模组互斥"，2026-09-27）**：3.20.0-fix34 实测驱动。
**好消息**：3.20.0-fix34 日志 `produced=512 matched=4393`／`produced=396` ⇒ 我们自己的展开**已经成功**。
**回归真因**：用户的 A/B/A 对照（3.20.0-fix32 总是 cancel ⇒ 两边都剩一张；3.20.0-fix33 我们未接管 ⇒ 原版恢复全部；
3.20.0-fix34 又接管 ⇒ 两边又都剩一张）证明 **`CallbackInfo.cancel()` 是共享标志**：cancel 掉 `provideCrafting`
会让同机原版 WildcardPattern 的展开一并被跳过（javap 亦证其处理器与 GT 本体同链）。修复：GT/GTNL/
AE2 ME 接口三处**只追加、永不 cancel**；顺带修两个从未打印过的限频诊断（`Long.MIN_VALUE` 哨兵溢出）。
构建 `BUILD SUCCESSFUL`（`EXIT=0`）；产物 `build/libs/AE2-QoL-3.20.0-fix35.jar`（1,759,116 B，SHA256 `4F80BEBD…`）；
字节码核对三个 mixin 的 `CallbackInfo.cancel` 计数均为 0。**待实测 4 项**（CHANGELOG 记录 (41)）。

**3.20.0-fix34 交付（通配匹配恒为 false = "从未成功过"的最后一个根因，2026-09-27）**：3.20.0-fix33 实测驱动。用户反馈
"只有原版能展开、我们只认铁板一张"；日志收窄到：`规则槽位越界` 0 条、展开时规则状态完全正确
（`matcher='ingot*' outMatcher='plate*' slot=0`）但 `matched=0 reason=no-material-matched` ×24。
根因：`SmartWildcardState.matches` 把**整个正则串** `toLowerCase()` ⇒ `Pattern.quote` 的 `\Q…\E` 变
`\q…\e` ⇒ `PatternSyntaxException` 被 `catch` **静默吞成 false** ⇒ 所有通配匹配恒 false（展开器枚举不到
材料、黑名单/白名单、规则级排除全失效）。已用最小 Java 用例实证并复核修复后语义
（`ingot*`↔`ingotIron` true、`plate*`↔`plateIron` true、`plateDouble*`↔`plateDoubleIron` true）。
修复：逐字符显式转义元字符 + `CASE_INSENSITIVE` + 异常记 WARN。构建 `BUILD SUCCESSFUL`（`EXIT=0`）；
产物 `build/libs/AE2-QoL-3.20.0-fix34.jar`（1,759,863 B，SHA256 `A96D4C3A…`）。**待实测 4 项**（CHANGELOG (40)）。

**3.20.0-fix33 交付（界面行/规则槽/模板下标对齐 + 与两套通配模组互不干扰，2026-09-27）**：3.20.0-fix32 实测反馈驱动。
**先确认 3.20.0-fix32 已生效**：`NEI 推导规则明细：#0 ore:ingot*`（矿辞前缀修好）、
`通配样板模板自愈：已从 Wild 的行数据重建原生模板（in=1 out=1）`（自愈能跑）。
**根因 F（核心）**：界面行 / 规则槽 / 模板输入下标**三套编号错位** —— `rule.slot` 是**模板输入下标**
（模板 `[电路,铁锭]` ⇒ slot=1），而 `pushToWild` 旧实现**按"有规则的条目"推行**（电路行从未进界面），
自愈又从"只有规则行"重建模板 ⇒ `in=1` ⇒ `slot=1` **越界被丢**（日志：`规则槽位越界`、`材料交集为空`、
`produced=0` ×16），输出条目也落不到正确的行；`pullFromWild` 还用"已收集规则数"当 slot ⇒ 每保存一次漂移一次
（日志"规则 2 条"）。**根因 G-3**：javap 实证原版 WildcardPattern 的 `MTEHatchCraftingInputMEMixin`
同样 HEAD + `ci.cancel()` 全量接管 `provideCrafting`，并对**共享的** `patternDetailsPatternSlotMap` 做
`removeIf` 清理 ⇒ 我们旧实现的无条件 cancel 会把对方的展开一起压成一张（用户实测吻合）。
修复：① 桥**以模板行为单位**（界面行=模板槽位、非规则行占位空行保行号、输出行按该行模板输出推 `plate*`、
`pullFromWild` 用行号当 slot、`remapRuleSlots` 重建后重排并记 INFO）；② GT/GTNL 处理器**先扫描**，
本机没有我们的已配置样板时**完全不介入**（＋`ae2qol$isForeignWildcard` 限频诊断）；③ 两处 `pushPattern`
守卫加**反向自愈**（第三方清掉共享映射时补回并放行，而不是拒收）。构建 `BUILD SUCCESSFUL`（`EXIT=0`）；
产物 `build/libs/AE2-QoL-3.20.0-fix33.jar`（1,759,609 B，SHA256 `D7D18D82…`）；字节码核对通过；包内版本 3.20.0-fix33。
**待实测 5 项**（CHANGELOG 记录 (39) 第四节）。

**3.20.0-fix32 交付（通配样板终于能展开：矿辞前缀取错 + 原生模板被删，2026-09-27）**：3.20.0-fix31 实测反馈驱动。
**先确认 3.20.0-fix31 已生效**：日志 `reason=template-in-out-missing` 23 次 ⇒ 判据与 `rebuild(world)` 接线通了；
`NEI 加号：已就地把推导结果写进 Wild 窗口（槽位 7…）` ＋ `写回成功（指定槽位）：slot=7 rules=1` ⇒
加号目标槽位与就地刷新通了。剩三个根因：**E-1** `OrePrefixes.getOreprefixKey()` 返回的是 GT **本地化键**
（javap 实证：实现里 `getDefaultLocalNameFormatForItem().toLowerCase().replace(" ","_").replace("%material","material")`，
常量池含 `gt.oreprefix.`）⇒ 规则被写成 `gt.oreprefix.ingot*`（界面里那串 `gt.orepr...`），
**永不匹配任何矿辞名**（矿辞名 `ingotIron`）；三处调用点全中（`SmartWildcardRecipeDeriver:138`、
`SmartWildcardExpander.oreInfo:454`、`WildcardEditorPanel:832`）。**E-2** `wildport/item/WildcardPatternState`
的 `cleanupLegacyPatternSlots` 在首次初始化时 `tag.removeTag("in"/"out")`（参考实现的数据模型），
而本模组展开器**以原生 in/out 当模板** ⇒ 模板被删 ⇒ 产出恒 0 ⇒ 回退注册模板那一张
＝「AE 直接按这个样板自己的合成」。**E-3** 输出行永远空白。修复：① 新增
`SmartWildcardExpander.oreDictPrefixOf(stack, material)`（矿辞名减材料名反推前缀，与 `matcherLiteralPrefix` 互逆，
多段前缀也正确）并统一三处，`getOreprefixKey()` 只在"明显非本地化键"时兜底 + WARN；推导器补
`NEI 推导规则明细` 日志；② `cleanupLegacyPatternSlots` 对**我们的**样板不再删 in/out（原版 mod 物品行为不变）
＋新增 `WildcardBridge.ensureNativeTemplate` **模板自愈**（`importPatternList→fromPatternSlot→fromStack`
保留了原始 stack，已实证），调用点＝`expand()`（四个机器入口共用）/`pushToWild`/`pullFromWild`/
`MixinDualityInterface`（对真身自愈后再复制）⇒ **存量坏样板免重配**；③ `displayOutputPrefix` +
`derivedOutputMatcher` 让输出行显示 `plate*`。构建 `BUILD SUCCESSFUL`（无管道取码 `EXIT=0`）；
产物 `build/libs/AE2-QoL-3.20.0-fix32.jar`（1,756,835 B，SHA256 `0F3699B9…`）；字节码核对：
`oreDictPrefixOf` 入包且被 `oreInfo` 调用、`SmartWildcardExpander` 内只剩被判据挡住的 `getOreprefixKey`、
`ensureNativeTemplate`/`derivedOutputMatcher` 在包内、包内版本 3.20.0-fix32。**待实测 5 项**
（CHANGELOG 记录 (38) 第五节）。**诚实边界**：本版起展开路径才第一次真正跑到"产出 N 张"。

**3.20.0-fix31 交付（用户实测三条报障 + 一条静默失效，2026-09-27）**：① **问题 C（核心）**：有规则的通配样板放进
GT 样板输入仓 / GTNL 超级总成 / PH 22069 / MK.II / 我们的 MK.III **全部不识别**（样板放得进槽位、机器无动作、无提示，
AE 合成监控看不到展开配方，用户确认**从未成功过**）。两个叠加根因：**C-1** `SmartWildcardPatternSlot.rebuild(World)`
与 `SmartWildcardGtnlPatternSlot.rebuild(World)` **全仓零调用者**（`grep rebuild` 只有定义）⇒ `expanded` 恒空
⇒ GT/GTNL 即使判据通过也注册 0 条；**C-2** 四个入口判据都是 `isSmartWildcard`（要求 NBT 里已有我们的子树
`ae2qolSmartWildcard`），而该子树只有 `pullFromWild`（Wild 窗口保存）与规则包两条写入路径 —— 实测
`pushToWild` 24 次、`pullFromWild` **2 次且两次都是"规则 0 条"**、`展开前懒同步` **0 次**；3.20.0-fix29 的懒同步写在
`SmartWildcardExpander.expand()` 内部，而 `expand()` 只有判据通过后才会被调用 ⇒ **对自己要救的场景永远不可达**；
且 GT/GTNL 的判否分支**完全静默**。修复：新建 `wildcard/SmartWildcardGate`（物品实例 → 缺 NBT 先懒同步 →
至少一条规则，判否必留限频 WARN）、GT/GTNL 两处包装点（`provideCrafting` + 重包路径）**补 `rebuild(world)`**、
展开为空时**退回注册模板那一张**（AE2 侧由 `ci.cancel()` 改为放行原生）。② **问题 A**：Wild 窗口 NEI 加号无效。
根因三条：写目标错（`SmartWildcardRulesPacket` 写"背包里第一张"而非窗口那张，日志指纹 revision `2→3→1`）、
界面不刷新（构建期读 NBT）、窗口旧内存态在保存时把刚写入的规则**覆盖成 0**（日志：14:57:30 写回 rules=1 →
14:57:32 拉回"规则 0 条"）。修复：包**带目标槽位**、`WildcardPatternWindow.applyDerivedFromNei` **就地整页替换
9 行 + 刷新控件 + 立即持久化**、`MixinGuiOverlayButton` 的 GTNH-MUI 分支**只认当前主窗口就是 Wild 窗口**
（不再误伤生成器窗口）、新增窗口级唯一写路径 `pushOurState`（读—改—立刻写回，失败就地回滚）。
③ **问题 B**：撤掉 3.20.0-fix30 底部电路带（高度回 292）；顶部右侧三页签（主页/电路/不消耗）；**电路页** 1~24 按
**4 列 × 6 行** + 清除（继承），点号当场生效+当场高亮；**不消耗物品页** = 复用 Wild 自带物品拖放框
（`WildcardEntryDropTextField`；本模组 `SmartWildcardNeiDragHandler` 只服务 MUI2 编辑器，对 GTNH-MUI 窗口不生效）
+ 加入手持 + 8 行/页（`ItemDrawable(supplier)` 图标 + 名称）+ 逐行删 + 分页，增删立即写回。④ **问题 D**：
`mui.MixinItemSlotWildcardGesture` 回调类型写成 `CallbackInfo`，而 javap 实证 `ItemSlot.onMousePressed(int)`
返回 `Interactable$Result` ⇒ 注入抛 `CallbackInfoReturnable is required` 被 UniMixins 吞成一条 WARN ⇒
**该手势从 3.20.0-fix10 起从未生效**；改为 `CallbackInfoReturnable<Interactable.Result>`（仍不 `setReturnValue`）。
构建 `BUILD SUCCESSFUL`（无管道取码 `EXIT=0`）；产物 `build/libs/AE2-QoL-3.20.0-fix31.jar`（1,753,773 B，
SHA256 `EB5FBC22…`）；字节码核对：两处 GT/GTNL mixin 各 2 次 `rebuild(World)` 调用、四个入口均调
`SmartWildcardGate.isConfiguredWildcard`、mui mixin 回调为 `CallbackInfoReturnable`、`SmartWildcardGate.class`
入包、包内版本 3.20.0-fix31。**待用户实测 6 项**（CHANGELOG 记录 (37) 第五节）。**遗留**：3.20.0-fix26～3.20.0-fix30 的
CHANGELOG/README 章节缺失（本轮未追写）；`CHANGELOG.md` 末尾与 `zh_CN.lang` 尾部有历史编码损坏（乱码）待单独修。

**3.20.0-fix11 交付（智能通配样板界面重做 + 批量样板生成器，2026-09-26）**：用户实测 3.20.0-fix10 后明确指出界面「完全不行、完全不可用」（控件少、空白多、文字重叠、**无手动编辑能力**）⇒ 方向改为**吞并 WildcardPatternforGTNH 与 AE2PatternGen 并优化**。① **MUI2 编辑器**：物品实现 `IGuiHolder<PlayerInventoryGuiData>`，右键经 `PlayerInventoryGuiFactory.openFromMainHand` 打开（带容器屏 = MUI2 `GuiContainerWrapper`，**NEI 加号仍可用**）；面板含 **9 行规则表**（匹配串用 `ore:`/`name:` 前缀自解释模式；每行 清/x2）、**内置电路 1~24**（空 = 继承，非法值保持原值并 WARN）、**总排除黑名单**（加/清）、**覆盖预览**（列候选 + 逐行「排除」；空结果给原因）、**保存**（客户端解析 → 既有 C2S 包 → 服务端写 NBT；新增"MUI2 容器下写主手样板"分支）。② **NEI 加号重接到 MUI2 宿主**：推导规则与模板后**立即写回**，聊天栏与日志回执。③ **规则模型扩展**：`Rule` 增加输出侧 `outMatcher/outOreDictMode/outAmount`，展开器优先采用规则输出前缀。④ **批量样板生成器**：新物品（青色）+ MUI2 界面 + `SmartPatternGenPacket`；核心 `generator/SmartPatternGenerator` **改编自 AE2PatternGen（MIT，文件头保留其版权与许可声明）**，含 RecipeMap 枚举与模糊匹配、输入/输出黑名单与矿辞、NC 物品过滤、上限截断与流体跳过计数。⑤ **版权合规**：审计确认未复制参考模组代码；移除仓库内 AE2 贴图改为运行时按名引用；`LICENSE` 版权人改为 `wztwzt`；调研笔记移出仓库并加 `.gitignore`；新增 `docs/THIRD_PARTY_NOTES.md`。**未完成**：规则级排除页与预览页的搜索/翻页、最终验收清单。构建 `BUILD SUCCESSFUL`，产物 `AE2-QoL-3.20.0-fix11.jar`。
**3.20.0-fix12 → 3.20.0-fix15 交付（通配样板界面按用户设计稿重做为四页签，2026-09-26）**：用户实测 3.20.0-fix11 后指出四条问题（界面内容不渲染 / 字色不清 / 按钮缺失 / 聊天栏刷 M1 自测信息），并明确要求**先出设计稿、敲定后再改代码** —— 已照做（线框稿 → 用户选定：**四页签** / **模式用切换按钮** / **NEI 拖入与不消耗物品都要**）。① 布局根因：子 `Flow` **没有显式 size** ⇒ MUI2 按 0 高布局，只有写了尺寸的标题/提示/表头被画出来；现已处处写 size。② 删除全部 `§` 颜色码（浅色面板用默认深色字）。③ 补齐每行 `预/筛/x2/清` 与底部 `全部预览/清空本页/重新载入/保存`。④ 删掉 `ItemSmartWildcardPattern.onItemRightClick` 里的 M1 聊天摘要。⑤ 两个物品覆写 `createScreen(data, panel)` 传 `MyMod.MODID`（消除 MUI2「未来会崩」警告）。⑥ **四页签**：MUI2 无 `TabWidget` ⇒ 「页签按钮 + 四个页面容器 + `setEnabledIf(页号谓词)`」，谓词逐帧求值 ⇒ 点页签即时切换、两端控件树一致。⑦ **NEI 拖入**：新增 `client/nei/SmartWildcardNeiDragHandler`（`INEIGuiHandler.handleDragNDrop` + `ModularGuiContext.getHovered()`），拖到输入/输出匹配框按 GT 权威前缀写 `<前缀>*` 并切矿辞模式（无矿辞写显示名切名称模式），拖到「加（拖入）」记入不消耗物品，未命中不消费且记 INFO。⑧ **规则级排除**：`Rule.excludes` 落 NBT `Excludes`，展开器按规则剔除（矿辞名或材料名匹配，`*`/`?` 支持），**总排除优先**。⑨ 修 `reason=no-material-matched`：无通配符的规则按**精确匹配**保留、不参与材料推导（否则材料交集被推成空集）。⑩ 排除页做实（总排除逐行删 / 规则 1~9 各自排除 / 不消耗物品 手持加入+拖入+逐行删）且**保存一并写回**。构建 `BUILD SUCCESSFUL`，产物 `AE2-QoL-3.20.0-fix12/2/3.jar` 分别部署（`mods` 内恒为一份且 SHA256 一致）；**待用户实测**。
**3.20.0-fix17 → 3.20.0-fix19 交付（界面整窗移植 WildcardPatternforGTNH，2026-09-26）**：用户指令「直接把 wild 模组的复制过来，在此基础改」⇒ 路线 = **整窗搬运** Wild 的界面子系统（它用另一套 MUI `com.gtnewhorizons.modularui`，与我们 Cleanroom MUI2 不通用）+ **双向桥**接我们自己的数据模型/展开器（机器侧行为不变）。① 3.20.0-fix17 先交付三个真 bug：流体幻影判据（javap 证据：`IFluidAlternativeStack` 被 `GTNEIDefaultHandler$FixedPositionedStack` 实现 ⇒ 对每个原料都成立，改判 `ItemFluidDisplay`）、展开失败逐规则诊断、NEI 拖入坐标命中测试（`getHovered()` 在帧外恒 null）。② GTNH-MUI 作为编译基线（`libs/modularui-1.3.4.jar`，SHA256 `9221B07C…`，取自实例；`compileOnly(project.files(...))`）。③ 机械搬运 25 文件 / 7,870 行（复制→改包名 `wildport`→加 MIT 来源声明→编译；依赖顺序靠编译器收敛：缺 `ModItems` ⇒ 缺 `MyMod.GUI_*` ⇒ 收口 `WildportIds`）。④ 部署前扫掉两条隐患：同名网络通道（`NetworkRegistry` 重名**直接抛异常、启动崩溃** ⇒ 改 `_wild` 后缀）、保存守卫 `getItem() != ModItems.wildcardPattern` 会**静默丢弃**我们的物品（⇒ 两种都接受 + 拒绝记 WARN）。⑤ NBT 键核实零重叠（它写根键、我们写 `ae2qolSmartWildcard` 子树）。⑥ 双向桥 `WildcardBridge` + 入口切换（右键先推再 `openGui(GUI_WILDCARD_PATTERN, 槽位)`；保存由它的包转 `pullFromWild`）。⑦ 3.20.0-fix19 自检发现 `pushToWild` 只在服务端执行 ⇒ 客户端旧 NBT ⇒ **新界面会是空的** ⇒ 改两侧都推。构建 `BUILD SUCCESSFUL`、`jar tf` 确认 `wildport/` 全量入包、3.20.0-fix17/3.20.0-fix18/3.20.0-fix19 各自部署（mods 恒一份、SHA256 一致）。**移植只到「编译通过 + 数据流按其源码接对」这一层 —— 界面尚未在游戏内验证，待用户实测。**
**3.20.0-fix23 → 3.20.0-fix27 交付（生成器界面整窗移植 AE2PatternGen + 通配样板双入口，2026-09-26）**：用户指令「另一个生成样板的 ui 按 AE2PatternGen-1.5 做，也直接先复制过来，再改再优化」＋「把你后几项打×也完成了」。① **取证**：AE2PatternGen 的界面同为 GTNH-MUI（声明 `ModularUI:1.3.1`，与已放进 `libs/` 的 `modularui-1.3.4.jar` 同一套）⇒ 可照搬；核对了其 GUI 清单、GUI id（**101 生成器 / 102 存储**）与窗口工厂 `GuiPatternGen.createWindow(buildContext, 手持物品)`。② **整树搬运**：`apgport/` **65 文件 / 9,983 行**（gui / filter / recipe / encoder / storage / network / config / util / command），每份文件头保留 **MIT** 来源声明；`ApgStubs` 收口其 proxy 三处调用（关闭屏幕实现、存储/详情面板记 WARN）；**又拦下一个启动崩溃级缺陷**：其 `NetworkHandler` 通道名原为 `MyMod.MODID`，与我们的 `ModNetwork` 重名 ⇒ 改 `_apg` 独立通道。③ **接线**：`CommonProxy` 注册其 `GuiHandler`（101/102）并调用 `NetworkHandler.init()`（成功/失败都留日志）；生成器物品右键打开其窗口。④ **行为改成我们的**：`PatternGenerationService.generateAndStore` 不再**消耗 AE2 空白样板**、不再写**虚拟仓储**，产物**进背包**（放不下掉脚下），聊天栏与日志给全量计数。⑤ **通配样板双入口**：直接右键 = Wild 界面；**Shift+右键** = 四页签 MUI2 编辑器（**内置电路 1~24**、**不消耗物品**）——旧编辑器不再是"无入口死代码"。⑥ **写回反馈**：写回我们的 NBT 后 `pushToWild` + 聊天提示"关掉重开即可看到"。⑦ **文档同步**：CHANGELOG 记录(36)、MOD_MAP（apgport 逐类清单 + 三入口）、THIRD_PARTY_NOTES（AE2PatternGen 由"核心改编"扩写为"核心改编 + 整窗搬运"台账）、README 中英（并修掉两处**停在 3.20.0-fix9** 的版本失真）、指南页中英各两页（生成器界面来源 + 通配双入口）。⑧ 版本 3.20.0-fix20→3.20.0-fix27 逐步部署，`mods` 恒一份、SHA256 一致；`jar tf` 确认 `wildport/`、`apgport/` 与四份指南资源入包。**待用户实测**：生成器产物进背包且不扣空白样板、Shift+右键的电路/不消耗物品、Wild 界面的加号与保存回执。
- [x] 2026-09-26 | 工具：DeepSeek Harness（DSH Web GUI）| 模型：DeepSeek-V4.1-Flash：**3.20.0-fix10 完整交付（智能通配样板，2026-09-26）**：① 物品与数据模型（`wildcard/ItemSmartWildcardPattern`（配方=AE2 空白样板）、`SmartWildcardState`、`SmartWildcardExpander`：索引期展开 + `smart_wildcard_expand_cap`（默认 512）+ LRU 缓存 + 超限 WARN）；② **三族样板仓接管，全部零反射**（AE2 `DualityInterface.addToCraftingList`；GT `MTEHatchCraftingInputME` 与 GTNL `SuperCraftingInputHatchME`：`PatternSlot` 子类 + 内部类 accessor + 四处注入，用 `shouldBeCached()→false` 取代参考实现那个字段已不存在的死反射；PH `PatternDualInputHatch` 一处注入即覆盖 PH 22069/MK.II/我们 MK.III）；③ M2（`ContainerSmartWildcard` + 四页 `GuiSmartWildcard` + `SmartWildcardRecipeDeriver` + `SmartWildcardRulesPacket` 服务端写回 + `MixinGuiOverlayButton` 三处接管，加号在 GT 处理配方上也可点）；④ M3（`SmartWildcardCircuit` 走 `IConfigurationCircuitSupport.getCircuitSlot()` + `GTUtility.getIntegratedCircuit()`，**绕开 MUI2 编译边界**；三族索引期自动写入，优先级 样板自带 > 槽位 > 整机）。**未实现**：MUI2 槽位上的 Shift+中键手势（等价路径 = 右键样板的「电路」页）。构建 `BUILD SUCCESSFUL`，产物 `AE2-QoL-3.20.0-fix10.jar`（1,266,252 字节，SHA256 `8942AF5C…`）。
- [x] 2026-09-26 | 工具：DeepSeek Harness（DSH Web GUI）| 模型：DeepSeek-V4.1-Flash：**3.20.0-fix10 M1：智能通配样板（进行中）**。
  1. 前置调研：两个参考模组（AE2PatternGen = 批量产具体样板；Wildcard Pattern = 单张通配样板）源码级取证，
     报告归档 `docs/research/wildcardpattern-forensics.md`；**关键更正**：参考实现是“索引期展开成 N 张普通样板”
     而非动态匹配，并需配套 GT `PatternSlot` 子类化 / `DualityInterface.addToCraftingList` 截胡 / `PatternMultiplierHelper` 处理。
  2. 与用户确认需求 12 项（新物品、一张盖一类、NEI 加号一步到位、矿辞+配方模板双判定、每张样板独立黑白名单、
     电路优先级“样板自带 > 槽位 > 整机”、覆盖五种总成、最小闭环优先、展开上限 512 可配）。
  3. M1 交付：`wildcard/SmartWildcardState`（规则/黑白名单/自带电路/自带不消耗物品/修订号，模板留原生 in/out）、
     `wildcard/SmartWildcardExpander`（材料配对展开 + 上限 + LRU 缓存 + 超限 WARN）、
     `wildcard/ItemSmartWildcardPattern`（继承 AE2 `ItemEncodedPattern`）、`MixinDualityInterface.addToCraftingList` 注入、
     `/ae2qof wildcard [矿辞前缀]` 自测命令、`Config.smart_wildcard_expand_cap`（默认 512）。
  4. 验证：`BUILD SUCCESSFUL`（`GRADLE_EXIT=0`）；`wildcard/` 下 7 个类入包；编译期修了两处（long→int 收窄、局部变量重名）。
  5. **未交付**：GT 样板仓接管、GTNL 超级总成接管、M2（可视化界面 + NEI 加号）、M3（每槽电路）、M4（文档/指南/版本收口）。
  6. 续做记录：**PH 族接管已完成**（`mixin/ph/MixinPatternDualInputHatchWildcard`，注入 PH 基类 ⇒ 同时覆盖 PH 22069/MK.II 22179 与我们的 MK.III 32108；`@Shadow public abstract boolean isActive()` 走 PH 自己声明的方法），并把 `SmartWildcardExpander` 取前缀改用 GT `OrePrefixes.detectPrefix`；同时同步了根目录与 src 两份 mixin 配置。编译均通过，未部署。
- [x] 2026-09-26 | 工具：DeepSeek Harness（DSH Web GUI）| 模型：DeepSeek-V4.1-Flash：**3.20.0-fix9：智能倍增单次推送上限改可配 + 修 3.20.0-fix8 诊断误报**（用户实测确认 3.20.0-fix8 已生效，随后反馈"几万几万一发"）。
  1. 定性：`MixinCraftingCPUCluster` 的功率钳制**显式封顶 4096 轮**（注释原文），是 #51（O(P) 探测）与
     #73（1T 订单客户端被淹没）的修复产物 ⇒ "几万几万"= 4096 × 样板每轮产出 ⇒ **设计如此**，不改分批语义。
  2. 新增 `Config.smartDoublingPushCap`（键 `smart_doubling_push_cap`，默认 4096，1..MAX）：javadoc/读/写/
     `applySetting` 全通；写文件用**重载**保持既有 5 处调用签名不变；CPU 侧硬编码 4096 改为读该字段。
  3. GUI 配置页新增输入框（含范围标签）、`/ae2qof status` 输出、`joinStatus` 改 varargs。
  4. 修误报：`SET(false)` 时清除"期望开启"登记（否则关闭后 5 分钟内 CPU 仍会误报"服务端开关仍为 false"，实测出现过）。
  5. 指南两语言同步（`smart_doubling.md` 配置表 + 两 `index.md` 各一行）。
  6. 验证：`BUILD SUCCESSFUL`（`GRADLE_EXIT=0`）；`AE2-QoL-3.20.0-fix9.jar`（1192155 字节，SHA256 `8DB5EBBC…`）；
     入包核对 `Config`/`GuiConfigScreen`/`CommandAe2QoL`/`SmartDoublingTogglePacket` 均在，版本号 3.20.0-fix9。
- [x] 2026-09-26 | 工具：DeepSeek Harness（DSH Web GUI）| 模型：DeepSeek-V4.1-Flash：**3.20.0-fix8：修「智能倍增在专用服务器上不生效」**（用户：云服务器上开关能勾住、合成仍一次一轮；同 jar 单人正常）。
  1. 阶段 0 逐条排除：服务端同版本/配置 0/其它功能正常/开关显示保持 ⇒ 收窄到"服务端看到的开关是 false"。
  2. 阶段 1 先证伪两条：`javap -v` 扫全链**无客户端类引用**（推翻 NoClassDefFoundError 说）；反射目标
     `finalOutput/diagnostics/tasks/workableTasks/getServerTick/TaskProgress.*` **全部命中**且无 `@SideOnly`
     （推翻"反射失败降级"说）。
  3. 真根因：三处开关 mixin（GTNL/GT/PH）在 **client 段**，其 `BooleanSyncValue.allowC2S()` 要求服务端
     **存在同名同步处理器**（MUI2 面板双端各构建一次才注册）；专用服务端没有该注入 ⇒ 写入被丢弃
     （`PanelSyncManager.receiveWidgetUpdate` 的 WARN 分支，以及两条**完全静默**的更早分支——
     与"服务端日志里找不到相关行"吻合）⇒ 服务端 `ae2qol$smartDoubling` 恒 false ⇒ `hasSmartDoublingTask`
     返回 false ⇒ 静默原版一次一轮。单人正常 = 同一客户端 JVM 里该 mixin 也变换了类。
  4. 修复：`SmartDoublingTogglePacket` 扩为容器/**坐标设置**/**坐标查询**三模式（服务端按坐标重定位 MTE、
     校验 `ISmartDoublingMedium`、写入 + `markDirty`）；新增 `SmartDoublingStatePacket`（S2C 权威回读，
     写回客户端机器对象供界面显示）；三处客户端 mixin 去掉 `allowC2S` 改发坐标包 + 打开时查询一次；
     补"应用 INFO / 目标非法 WARN / CPU 侧期望窗口 WARN"三层诊断（正常零噪声）。
  5. 验证：`BUILD SUCCESSFUL`（`GRADLE_EXIT=0`）；`AE2-QoL-3.20.0-fix8.jar`（1190908 字节，SHA256 `B5A4E791…`）；
     新包类与四个 mixin 均已入包。待服务器验收 4 项（日志有"开关 = true"行 / 重启后仍勾住 / 一次进 N 轮材料 / 单人回归）。
- [x] 2026-09-26 | 工具：DeepSeek Harness（DSH Web GUI）| 模型：DeepSeek-V4.1-Flash：**3.20.0-fix7：修行内名称空白 + 发信器数量改不动**（用户实机截图报障）。
  1. 取证顺序（值得复用）：先用 `javap -c` 反编译**自己构建的** `EmitterRow` 证明 `write/read` 对称、
     `ByteBufUtils` 成对 ⇒ **先排除传输层**；再读 MUI2 源码。
  2. 根因①：`ButtonWidget extends SingleChildWidget`，`SingleChildWidget.child()` 会
     `this.child.dispose()` 后替换 ⇒ 按钮里"名称 + 数值"两个子控件时**名称被扔掉** ⇒ 名称列空白。
  3. 根因②：按钮内的文本会吞点击（本仓 `ph.PatternWindowWidgets.NonInteractiveText` 同坑）⇒
     点行打不开编辑 ⇒ 未选中 ⇒ 数量改不动。
  4. 根因③：`appeng.api.config.LevelType` 常量是 `ITEM_LEVEL`/`ENERGY_LEVEL`（无 `ITEM`/`FLUID`/`ENERGY`）
     ⇒ `typeLabel` 等值比较恒落 unknown ⇒ 永远"未知"（3.20.0-fix4 用首字母才没暴露）。
  5. 修复：行改为「名称按钮 + 数值按钮 + 高亮 + 传送」，文字全走 `overlay(IKey)`（已验证路径且不挡点击），
     各自独立 `InteractionSyncHandler`；`typeLabel` 改前缀匹配。
  6. 验证：`BUILD SUCCESSFUL`（`GRADLE_EXIT=0`）；`AE2-QoL-3.20.0-fix7.jar`（1183439 字节，SHA256 `09BDD665…`）。
- [x] 2026-09-26 | 工具：DeepSeek Harness（DSH Web GUI）| 模型：DeepSeek-V4.1-Flash：**3.20.0-fix6：覆盖板列表改为只看本终端所连网络**（用户拍板，记录 (26) 遗留项）。
  1. 改动：`CoverRegistry.getAll()` → `getByNetwork(terminal.getNetworkId())`（与该方法注释的设计意图对齐；
     终端未绑网络时 `getByNetwork("")` 仍返回全部，此路径行为不变）。
  2. 新增 `IntSyncValue`（`sm_terminal_covers_hidden`）：被过滤数量 S2C，空列表显示
     「本终端所连网络下没有覆盖板（另有 N 个属于其他网络，已隐藏）」，避免"覆盖板不见了"无法定性。
  3. 扫描逻辑收拢进 `CoverScan` 内部类（行与隐藏计数共用一次扫描 + 20 tick 节流）。
  4. 验证：`BUILD SUCCESSFUL`（`GRADLE_EXIT=0`）；产物 `AE2-QoL-3.20.0-fix6.jar`（1183287 字节，SHA256 `74BBBA2A…`）。
     待验收 3 项：只显示本网络 / 空列表带数字说明 / 邻接直连仍显示全部。
- [x] 2026-09-26 | 工具：DeepSeek Harness（DSH Web GUI）| 模型：DeepSeek-V4.1-Flash：**3.20.0-fix5：库存统计终端 —— 行内高亮/传送 + UI 优化 + 汉化**（用户：「再优化一下 ui，增加一个高亮和传送，可以参考我们的自适配能源终端的高亮和传送，然后汉化有点问题」）。
  1. 阶段 0 侦察：`gt.blockmachines.stock_monitor_terminal.name` 在 zh/en **都缺**（自适应终端有、所以它是中文）；
     自适应终端的高亮/传送语义（C2S 包 → 服务端会话/团队鉴权 → `WirelessHighlightPacket` + 200 tick 清除；
     传送用匿名 `Teleporter`）；文档里两条老坑（`keyBindSneak` 判 Shift 恒 false、`new Teleporter(world)` 找/建下界门）。
  2. 用户拍板 6 项：方块级高亮 10 秒 / 跨维度传送 + 安全落点 / 沿用团队+会话鉴权 / 行内两个独立按钮 /
     UI 四项（左对齐、中文类型与模式、悬停 tooltip、标题配色）/ 汉化三类。
  3. 实现：新增 `network/StockMonitorActionPacket`（只收坐标，服务端重新解析目标真实性 + 会话 + BUILD 权限后才执行）；
     终端本体加会话集合与 `resolveGrid`/`hasBuildPermission`（从 GUI 下移，供网络包复用）+ `getLocalName()` 覆写 +
     `onPostTick` 推进高亮清除队列（没装自适应终端时高亮也会按时消失）；GUI 重写行布局与中文标签；lang 新增 26 键。
  4. 编译期修正：`ThresholdMode` 常量实为 `BELOW_THRESHOLD_RUN`/`ABOVE_THRESHOLD_RUN`（先猜错、编译立刻报出）。
  5. 验证：`BUILD SUCCESSFUL`（`GRADLE_EXIT=0`）；产物 `AE2-QoL-3.20.0-fix5.jar`（1180817 字节，SHA256 `5590DCC0…`）；
     新包类与 GUI 内部类均已入包。**待游戏内验收 5 项**。
  6. 未决策项：覆盖板列表仍列全服所有覆盖板（`getAll()`），与 `getByNetwork()` 注释"只显示同网络"不一致；
     本次未改显示范围，等用户拍板。
- [x] 2026-09-26 | 工具：DeepSeek Harness（DSH Web GUI）| 模型：DeepSeek-V4.1-Flash：**3.20.0-fix3：修库存统计终端（32107）GUI 只有两行标题、读不到库存**（用户报「打开无法连接 AE / 没有 nexus 的连接 UI / 连不上 AE / 无法实时修改」）。
  1. 阶段 0 定性：坏的**只有终端**（覆盖板自身 GUI 用户确认可用）；终端**自始至终不能用**；Nexus 1.0.2 已装且自身可用、
     编译依赖与实例 jar 逐字节一致、`WirelessSelectionPanel.build` 签名与设计一致、日志无任何异常。
  2. 根因 1：控件建在 `if (isServer)` 之后，而 **MUI2 面板双端各构建一次、渲染的是客户端那棵树** ⇒ 客户端上
     连接状态/连接按钮/两个列表全都不存在（只剩标题与两个区块标题，与用户现象逐字对应）。
  3. 根因 2（隐藏）：`Grid.getMachines()` 返回的是 **IGridNode** 集合
     （`IMachineSet extends IReadOnlyCollection<IGridNode>`，用实例 AE2 jar 核对），
     旧代码对节点做 `instanceof PartLevelEmitter` 永不匹配 ⇒ 发信器列表恒空。
  4. 附带静态自查：`RowCache` 以 `Long.MIN_VALUE` 作哨兵会让 `now - lastTick` 溢出成负 ⇒ 首次调用永不重算。
  5. 修复：结构双端一致 + `GenericListSyncHandler`/`DynamicSyncedWidget` 快照渲染（范式分别取自**覆盖板 GUI**
     与 **Nexus 自身面板**）+ 可滚动列表（取消 5 行上限）+ 编辑值全走 SyncValue（权限校验在服务端 setter）+
     新增 Nexus 缺失回退面板 `StockMonitorTerminalNetworkPanel`；**未触碰覆盖板任何文件**。
  6. 验证：`BUILD SUCCESSFUL`（无管道取码 `GRADLE_EXIT=0`）；产物 `AE2-QoL-3.20.0-fix3.jar`
     （1167679 字节，SHA256 `85BB3AE7…`）；新类与内部类均已入包。**待游戏内验收 4 项**（见 CHANGELOG 记录 (25) 第四节）。
  7. 教训已写入 skill 第 21/22 条（MUI2 双端构建；AE2 `getMachines` 返回节点 + 精确类名查表）。
- [x] 2026-09-26 | 工具：DeepSeek Harness（DSH Web GUI）| 模型：DeepSeek-V4.1-Flash：**3.20.0-fix2：修 GuideNH 指南页图标/物品 ID 写错（5 页 × 中英 = 10 个文件，纯资源修正）**。
  1. 现象：启动日志 4 条 `[GuideNH] [NavigationUtil] Couldn't find icon item ae2_qof:...`
     （万能维护仓 / 自适应电网 / 无线 EU 电网 / 库存统计终端 各一条），指南页图标空白。
  2. 根因：`icon:`/`item_ids:` 写的是凭想象拼的名字；这些机器是 **GT 机器**，真实注册名是
     `gregtech:gt.blockmachines` + meta（即 MTE ID）⇒ `ae2_qof:<机器名>` 不存在。
     解析链核实：`Frontmatter.parseIconEntryString` → `IdUtils.parseItemRef`（`modid:name:meta` + 可选 `:{SNBT}`）
     → `NavigationUtil.resolveItemStack`（`Item.itemRegistry.getObject`）。
  3. 修正：32000 / 32102–32107 / 32110 / 32111 全部改用 `gregtech:gt.blockmachines:<id>`；
     并修了**不报错**的第 5 页（AE2 切割刀 → `appliedenergistics2:item.ToolCertusQuartzCuttingKnife`，
     依据 `ItemFeatureHandler` 的 `"item." + name` 与 `FeatureNameExtractor` 的 Quartz→CertusQuartz 替换）。
  4. 全量审计：16 页 × 2 语言的**所有** `icon`/`item_ids` 与本模组注册名逐一对照；解包 grep 确认包内已无旧 ID。
  5. 产物 `build/libs/AE2-QoL-3.20.0-fix2.jar`（1152731 字节，SHA256 `3B7189EF…`），构建 exit 0。
  6. 待办：游戏内确认那 4 条 ERROR 消失；**3.20.0-fix2 未部署**（纯资源修正，需换 jar + 重启才生效）。
- [x] 2026-09-26 | 工具：DeepSeek Harness（DSH Web GUI）| 模型：DeepSeek-V4.1-Flash：**新增「编程样板输入总成 MK.III」（3.20.0，ProgrammableHatches 可选依赖，144 样板槽）——代码完成、构建通过、产物自检通过；游戏内验收待用户配合，部署尚未进行**。
  1. **需求确认（7 项产品决策全部由用户拍板）**：144 样板槽；样板窗为 9 列 × 9 可见行的**可滚动网格**（覆盖 16 行）；
     屏幕基线 1920×1080 + GUI 缩放 4；输入结构与 MK.II 一致（每缓冲 32 物品 + 32 流体，24 个隔离缓冲）；
     配方 + 进 AE2 QoL 创造标签页；中英名「编程样板输入总成 MK.III」；不做 NBT 迁移；MTE ID **32108**；版本 **3.20.0**。
  2. **只读取证结论**：实例 `programmablehatches-0.2.0p24.jar` 与 `libs/` 编译依赖 SHA256 完全一致（`77470645…`）；
     PH 的容量**写死在 4 个数组长度里**（`javap -c`：两个构造器各 4 次 `bipush 36`，类内循环全走 `pattern.length`）⇒ 换数组＝换容量；
     AE2 的 `InterfaceTerminalRegistry` 与 `Grid.getMachines(Class)` 按**精确类名**查表 ⇒ 必须注册 `Inst.class`；
     AE2 `GuiInterfaceTerminal.VIEW_WIDTH=174` ⇒ 每行最多 9 格、行数不限（条目逐行做可见性判断，故 16 行可滚到底）；
     MUI2 **2.3.88（编译）与 2.3.91（运行）**的 `widget.ScrollWidget` / `scroll.VerticalScrollData` 同名同包；
     运行时 Mixin（UniMixins 0.3.1）含 `AccessorType.FIELD_SETTER`。
  3. **实现**：新增 `ph/MTEPatternCraftingBufferMKIII`、`ph/PatternWindowWidgets`、`ph/PhIntegration`、
     `mixin/ph/MixinPatternDualInputHatchAccess`；改动 `CommonProxy`（init 调用注册）、`AE2QoLCreativeTab`（创造页追加）、
     `mixins.ae2_qof.json` **两份**（通用 14 + client 16 = 30）、中英 lang、版本号两处。**未改动 PH 本体**
     （PH 自己的总成 22069 / MK.II 22179 / 仅物品版仍是 36 槽）。
  4. **本轮坑位**：PH 的 `loadNBTData` 有 `if (multiplier.length < 36) multiplier = new int[36];`，新机器首次读档必缩回 36
     ⇒ 必须 `super` 之后重新补齐 4 个数组；`pattern` 只能「长度不符才重建 + `System.arraycopy` 搬运」；
     `newMetaEntity` 必须返回我们自己的 `Inst`（照抄 PH 会退回 36 槽）；`getStackForm` 必须覆写（模板实例 base 为 null 会 NPE，
     与库存统计终端同一个坑）；`ItemDrawable` 在 `com.cleanroommc.modularui.drawable`（不是 `api.drawable`，首次编译即报此处）。
  5. **验证（已完成部分）**：构建 `BUILD SUCCESSFUL`（exit 0、无管道取码）；产物 `build/libs/AE2-QoL-3.20.0.jar`
     1152357 字节 / SHA256 `F85883A8CF09ED073C44415797089396D2B0210A5636965C16E2C202EAA6EFDD`；
     新类（4 + `$Inst` + `$1` + 3 个窗口部件）全部入包；**PH/MUI2/GT 的类未被打包**；包内 `mixins.ae2_qof.json` 含新条目
     （解包到临时目录核对）；字节码核对 `PhIntegration.register()` 首条指令即 `Loader.isModLoaded`、MTE 经 `invokeinterface` 走 accessor。
  6. **3.20.0 实测失败 → 3.20.0-fix1 修正**（用户报「没找到这个物品」）：3.20.0 里可选依赖守卫把 PH 的 **modid**
     误写成它的**包名前缀** `proghatches`（真实值是 `programmablehatches`），守卫恒假 ⇒ 物品从未注册且**无任何日志**。
     证据：日志有 `[AE2QoL] GuideNH guide registered`（init 确实跑过）、无「已注册」也无「注册失败」（卡在守卫第一句）、
     `mixin/programmablehatches: Mixing ph.MixinPatternDualInputHatchAccess ... into ...PatternDualInputHatch`（**mixin 应用成功**，
     排除 mixin 问题）。修复：真实 modid + 「关键类可解析」第二道判据 + 三条分支各打一行日志。
     产物 `build/libs/AE2-QoL-3.20.0-fix1.jar`（SHA256 `D0F00177…`）已部署，有缺陷的 3.20.0 改名 `-modid-bug` 入备份。
     教训已写入 skill 第 18/19 条（可选依赖只能取 `@Mod`/`mcmod.info` 的真实 modid；跳过分支必须留日志）。
  7. **实测通过**（用户实机，2026-09-26）：「样板确实扩充了没问题」。日志证据：`ae2_qof(AE2 QoL:3.20.0-fix1)`、
     `Mixing ph.MixinPatternDualInputHatchAccess ... into ...PatternDualInputHatch`、
     **`[AE2QoL] PH 编程样板输入总成 MK.III 已注册：id=32108，样板槽=144（16 行 × 9 列）`**、物品中文名正常；
     全日志无新类异常。未逐项复测的 4 项见 `CHANGELOG.md` 记录 (23) 第六节（不声明未取证项为已验证）。
- [x] 2026-09-25 | 工具：DeepSeek Harness | 模型：DeepSeek-V4.1-Flash：**用户报障三问题定位与修复（fix50 / fix51 / fix52），全部代码完成并构建验证，均待实机实测**。
  1. **fix50｜万能维护仓电路板槽放不进任何物品**：根因是 GT 5.09.54 新接通 MTE 物品校验链
     （`MTEItemStackHandler.isItemValid` → `MTEHatchMaintenance.func_94041_b` → `IsAutoMaintenanceInput`），
     本仓恒以 `aAuto=false` 构造导致槽位 0 对一切物品返回 `false`（5.09.52 无此链，故 b1 可用）。
     修复：`hatch/AE2MaintenanceHatchUniversal.java` 新增 `func_94041_b` 覆写（**SRG 名**），
     只对电路板槽放行各电压电路板。产物 `build/libs/AE2-QoL-3.19.0-fix50.jar`，commit `cfc146a`。
  2. **fix51｜IO 端口搬不出无限磁盘的流体**：根因在 **AE2 上游** `TileIOPort.getInv`——
     它在 `AEStackTypeRegistry.getAllTypes()` 里取到第一个匹配通道就 `break`，
     多通道元件（内置无限磁盘）因此只搬一个通道；单通道流体元件的唯一匹配恰好是流体，所以能搬。
     修复：`mixin/ae/MixinTileIOPort.java` 新增 `tickingRequest` RETURN 注入，按相同顺序枚举通道、
     跳过索引 0，对剩余通道用 AE2 自身 `transferContents` 补搬（**反射**，因其返回私有内部类）。
     commit `5300e61`。
  3. **fix52｜世界里键无存量+有样板不弹下单页**：根因是 **AE2 包处理器运行在网络线程**，
     在其中替换 `player.openContainer`／开界面不生效；NEI 面板中键路径一直归队服务端 tick 线程
     （实测可用），两条路径共用同一开界面方法，差异只有线程。修复：世界中键也归队 + 不再 `cancel()`；
     并把 `hasNetworkStock` 的异常兜底由「当作有存量」改为「当作无存量」+ 警告，消除静默失效。
     产物 `build/libs/AE2-QoL-3.19.0-fix52.jar`。
  **三个问题的根因链、证据、影响面与验证步骤见根目录 `CHANGELOG.md` 的 (13)(14)(15) 三节。**

- [x] 2026-09-25 | 工具：DeepSeek Harness | 模型：DeepSeek-V4.1-Flash：**接力文档与仓库真实状态对齐（仅文档，未动业务代码）**。
  背景：本文档长期停留在 fix49 开工前——写"未提交、未推送"、起点 `ffe946a`、基线 beta-1；
  实测 `git log` 显示 `63153ed fix47`、`d783448 fix48`、`223c8c5 fix49` **均已提交且与 origin/master 同步**，
  工作树干净，`build/libs/AE2-QoL-3.19.0-fix49.jar` 已产出，`libs/` 与 `dependencies.gradle` 均已是 b3 版本。
  更正项：①第一节元数据改为 DSH / DeepSeek-V4.1-Flash、起点 `223c8c5`；
  ②第二节基线改 `2.9.0-beta-3` / `3.19.0-fix49`；③第四节把 fix47/48/49 从"未提交"改为"已提交"，只保留真实未完成的实测项；
  ④第六节第 8 条的 `build_compile*.log` 约束按"已随 fix47 清理"的事实改写；
  ⑤第六节第 2 条的测试实例路径补上 b3 实例 `GT_New_Horizons_2.9.0-beta-3_Java_17-26`（fix48 后的基线实例），旧 b1 实例保留原样。
  同步修正的其它文档见本文档第九节末尾清单。
  **重要事实（如实登记，不重写历史）**：fix42~fix46 的各项改动（Tooltip 单入口、覆盖板堆叠 64、
  v7 材质方案 B、fix46 透明修复）**没有各自独立 commit**，而是与 fix47 一起并入 `63153ed`，
  与"一个功能/bug 一个 commit、代码与文档与版本号同 commit"的约定不符；本轮不拆分既有提交。

- [x] 2026-09-20 | 工具：Codex | 模型：GPT-5：**fix48 MTE ID 让位 fissionevolved + 旧存档自动迁移 + 基线切 290b3**。
- [x] 2026-09-21 | 工具：Codex | 模型：GPT-5：**fix49 依赖全量对齐 290b3 实机版本**。
  背景：fix48 只切了文档基线，编译依赖仍是 beta-1 时代（AE2 977、GT 5.09.52、NEI 2.8.19 等），
  属"能跑但未对齐"。本轮将 13 项依赖全部升到 b3 实机版本（AE2→1050、GT→5.09.54.133、
  NEI→2.8.130、AE2FC→1.5.106、ModularUI2→2.3.88、GTNL→pre3、ProgrammableHatches→p24、
  GuideNH→1.3.29、NotEnoughEnergistics→1.7.41、BetterQuesting→3.8.84、ThaumicEnergistics→1.7.60、Avaritia→1.99；
  StructureLib 本就一致），本地 jar 放入 `libs/` 并更新 `dependencies.gradle`。
  **升级后编译期暴露 6 处真实 API 断裂并全部修复**：
  ① AE2 1050 将 `BlockIOPort.getRenderer()` 返回类型收窄为 `RenderIOPort` →
  `RenderBlockExIOPort`/`RenderBlockQuestDetector` 改继承 `RenderIOPort`，
  但渲染仍走 `BaseBlockRender` 通用路径保持既有外观（注意 `TileQuestDetector` 继承
  `AENetworkTile` 而非 `TileIOPort`，不可套用 IO 端口专用逻辑）；
  ② AE2 1050 移除 `ContainerPatternTerm.outputSlotsClient` →
  `ClientProxy.applyClientSwap` 改为反射取私有 `outputs` 后直接 `putAEStackInSlot`；
  ③ AE2 1050 将 `IInterfaceViewable.getNameSuffix()` 由 `String` 改为 `IChatComponent` →
  `ContainerMergedTerminal` 新增 `serializeSuffix`（对齐 AE2 `ContainerInterfaceTerminal` 做法）。
  验证：compileJava/build（Java17、offline）均 SUCCESS；`gradlew dependencies` 确认解析到 b3 版本；
  `Unable to locate obfuscation mapping` 警告数与升级前一致（27 条，既有现象）。
  版本同步 `3.19.0-fix49`。

  b3 日志实据：`MetaTileEntity id 32101/32100 is already occupied!`，占用者为 fissionevolved
  的裂变反应堆控制器/终极宇宙毁灭发电机控制器（其 `Config.java` 默认值即 32100/32101）。
  两个终端在 b3 的 `metatileentity.csv`（4606 条）中查无，即从未注册成功。
  处理：自适应电网终端 32100→**32106**、库存统计终端 32101→**32107**；
  依据 b3 全表核对空闲，且与 32102~32105、32110/32111 连成整块，避开 GTNL 写死的 32301~32331/32350~32377。
  **关键**：GT 存档只存 MTE 数字 ID，直接换号会让旧存档终端被 fission 读取并丢失配置
  （`ae2qolNF` 频率/`ae2qolVT` 电压/`ae2qolHT*`+`ae2qolHA*` 配对表 → 表现为终端消失、频率丢失、全基地子仓解绑）。
  故新增 `mixin/gt/MixinBaseMetaTileEntityIdMigration`：注入 `setInitialValuesAsNBT` HEAD，
  仅当 `mID` 为 32100/32101 **且** NBT 含本模组专属键（`ae2qolNO`/`ae2qolNF`/`ae2qolVT`）时改写为新号；
  fission 写的是 `Fission*` 前缀，互不相交，不会误迁移。
  影响面以逐区块 NBT 解析核实：b3 `World` 有 1 台 32100（主世界 49,29,44）、0 台 32101；
  两存档均无携带旧 ID 的终端**物品**，无需处理物品栏/箱子/AE 残留。
  完整构建通过；解包核对新 Mixin 已入包且 Minecraft 成员引用已重混淆（`func_74762_e`/`func_74768_a`/`func_74764_b`）；
  迁移前已备份存档 `World_bak_before_id_migration`（445 MB）。版本同步 `3.19.0-fix48`，基线切到 `2.9.0-beta-3`。

- [x] 2026-09-20 | 工具：Codex | 模型：GPT-5：**fix47 世界中键取物「可合成即打开下单页」**。
  用户需求：世界里对着方块按中键时，网络没存量但有合成样板 → 打开 AE2「要合成多少个」界面。
  新增 `mixin/ae/MixinPacketPickBlock`（注入 `PacketPickBlock.serverPacketData` HEAD，`remap=false`），
  判定顺序「能不管就不管」：解析不出物品/背包已有/无可用无线终端/网络还有存量 一律放行原版，
  仅「没存量 + 有样板」才接管并 `cancel()`；被点物品经反射读 `pickedBlock` 私有字段。
  开界面逻辑抽为 `ServerTerminalHelper.openCraftAmountIfCraftable(...)`，与 NEI 中键下单 `RequestCraftingPacket` 共用；
  另加 `ServerTerminalHelper.hasNetworkStock(...)`（只 SIMULATE、不经耗电判定）。
  终端查找顺序对齐 AE2 原版（Baubles 优先）+ 饰品栏槽位换算 `100012 + i`，
  开界面槽位取已解析终端自身的 `getInventorySlot()`，保证校验与操作是同一终端；
  已在该界面时不重复打开（防连点清空已填数量）。
  已核对 FML `FMLEventChannel.fireRead` 直接 post 事件，与原版同上下文，故不额外归队线程。
  完整构建通过；解包产物核对新 Mixin 已入包且 Minecraft 成员引用已重混淆
  （`field_71071_by`/`field_70462_a`/`func_77969_a`/`func_77970_a`）。版本同步 `3.19.0-fix47`。

- [x] 2026-09-19 | 工具：Codex | 模型：GPT-5：**fix46 修复方案 B「装包后机器透明」**。
  用户部署 fix45 + 完整资源包后机器变透明。日志证据：25 张 `getIcon HIT ... UV=[0.0,0.0]-[0.0,0.0]`，
  即 sprite 从未装订进图集（尺寸 0×0）。根因是 `MixinTextureMap` 的「只注册一次」静态开关——
  `registerIcons()` 每次都会先 clear 清单，而方块图集启动期会多次执行该方法，
  导致真正装载的那一轮没有这 25 条。修复：删除该开关改为每次补注册；`ModTextures` 增加退化 UV
  兜底（全 0 回退 GT 机箱），兜底图标多级取值保证不为 null。
  版本同步 `3.19.0-fix46`，完整构建通过。资源包本身已验证无误（25 张 16×16 全不透明）。

- [x] 2026-09-19 | 工具：Codex | 模型：GPT-5：**fix45 材质方案 B 落地 + NEI 捕获修复 + 维护仓回归修复**。
  ① 新增 `client/MixinTextureMap`，在原版方块图集 `registerIcons()` 尾部补注册 25 张 v7 路径，并把图集图标回填到 `ModTextures.registerBaked(...)`；
  ② `ModTextures` 渲染端优先读取 Mixin 回填图标，旧 stitch 事件通道只保留兼容；反射解析 MCP/SRG 成员名，降低生产环境静默挂空风险；
  ③ `AE2MaintenanceHatchUniversal` 删除无条件 `OVERLAY_AUTOMAINTENANCE` 覆层，恢复 GT 发光/启用状态逻辑；v7 关闭或资源不可达时完全回退 GT 默认；
  ④ `MixinGuiRecipe` 同时注入 MCP 名 `updateScreen` 与 SRG 名 `func_73876_c`，修复生产环境 NEI 当前配方捕获挂空；
  ⑤ 版本号与 `mcmod.info` 同步 `3.19.0-fix45`，完整离线构建通过。
  产物：`build/libs/AE2-QoL-3.19.0-fix45.jar`（1,119,638 字节，2026-09-19 14:32）。
  配套资源包：完整 25 张 `AE2QoL-v7-resourcepack.zip`；旧 overlay-test 包不再是方案 B 判断依据。
  待用户实测：加包/不加包、`v7_textures=auto/on/off`、维护仓发光与启用状态、8 台分面贴图。**未提交、未推送；未部署。**

- [x] 2026-09-19 | 工具：CloseCode（DoCode Platform）：**v7 材质 8 轮排查定案 + 万能维护仓首成功**。
  ① 定位根因：GT `registerIcon` 对自定义新路径**恒返回 missingno**（25 张不同贴图 UV 完全相同，UV 宽 0.0039≈1/16px@4096 的证据），换命名空间/换注册时机均无效；
  ② 确认唯一可行机制 = 复用 GT 已有路径 + 资源包**同名覆盖**（对标 Modernity-GTNH）；
  ③ 万能维护仓改用覆层方案（`builder().addIcon().extFacing().build()`），**v7 图案首次成功渲染**；
  ④ 新增 `util/ModTextures`（重写为 StitchedContainer）、`client/render/V7TextureStitchHandler`、删除死代码 `ClientTextureRegistry`；`Config` 新增 `v7_textures`（auto/on/off）；`CommonProxy.postInit` 接入资源判定；
  ⑤ 产出 `docs/v7-材质方案结论档案.md`（8 条已证伪路径 + 唯一可行机制 + 环境坑位）。
  产物：`build/libs/AE2-QoL-3.19.0-fix44.jar`（1,117,273 B）、`AE2QoL-v7-overlay-test.zip`（2,733 B）。
  遗留：面位待实测确认；7 台机器未铺；方案岔路 A/B 待用户决策。**未提交、未推送。**

- [x] 2026-09-18 | Arena.ai Agent Mode / ShunCode MCP：**fix43 库存检测覆盖板堆叠 1 → 64**。本轮业务变更仅 `ItemStockMonitorCover.java` 一行；Java17 构建、发布 JAR 构造器常量/元数据检查通过；主版本、README、根日志与交接已同步。未部署、未游戏实测、未提交/推送。

- [x] 2026-09-18 | 工具：Arena.ai Agent Mode / ShunCode MCP：**fix42 Tooltip 单入口修复与文档交接完成**。只修改业务类 `client/nei/NetworkTooltipHandler.java`，移除通用入口追加及既有 1 秒去重状态。Java17 离线构建成功，59 项隔离断言通过，JAR 字节码与元数据检查完成；根 CHANGELOG、中英文 README、MOD_MAP 与本交接同步更新。产物 `build/libs/AE2-QoL-3.19.0-fix42.jar`，1,104,409 字节。**未部署、未游戏实测、未提交/推送。** 详见第十节。
- [x] 2026-09-18 | 工具：Arena.ai Agent Mode：完成 fix41 全功能静态审查与精确版本 Tooltip 调查；产出 `docs/mcp-full-function-audit-fix41.md`、`docs/mcp-tooltip-duplicate-investigation.md`。前者 A01–A19 保持开放，本轮仅按已批准的 Tooltip 方案实施。

- [x] 2026-09-18 | 工具：Codex | 模型：GPT-5：**自动上传可靠性三项修复**（`fix41`）：① 新增 `util/ProviderLocator`，供应器定位从「内存地址 ID」改为「维度+坐标+朝向」稳定标识，并随列表下发、上传时回传，解决区块重载/服务器重启后 ID 失效导致的静默失败；② 新增 `network/UploadFeedbackPacket`，目标丢失/写入被拒/无权限三种失败在聊天栏明确提示；③ 「能否接收样板」从只看第一个空槽改为遍历全部空槽，修正分类型样板槽的误判。产物 `build/libs/AE2-QoL-3.19.0-fix41.jar`（1,104,660 字节）
- [x] 2026-09-17 | 工具：Codex | 模型：GPT-5：**重做样板上传选择界面**（`fix40`）：修正 fix31 照抄 GTNH-ECO 时把映射输入框与 添加/删除/刷新/取消 按钮挤在同一行导致的互相遮挡；改为 标题/搜索/列表/操作/配方映射 五区纵向排版 + 窗口自适应（宽 280~400、行数 1~7），补上本模组自己的「配方 -> 目标机器名」映射区，新增「用选中机器」按钮一键填入选中机器名，删除支持按配方 ID 精确删单条，取消不再误改终端搜索框，上传记忆改用与自动上传查询一致的 key。产物 `build/libs/AE2-QoL-3.19.0-fix40.jar`
- [x] 2026-09-17 | 工具：Codex | 模型：GPT-5：**紧急修复 fix38 启动崩溃**：定位到崩溃由 fix32「修好」的 RFB `childDelegations` 注入引起（该补丁在 fix14 中因类型判断错误实际失效，修复后反而让 `net.minecraftforge.*` 绕过 RFB 的 ExtensibleEnumTransformer，导致 Railcraft 枚举扩展失败）；现已彻底删除该注入，发布 `3.19.0-fix39`
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

> 状态说明（2026-09-25 核对）：fix47、fix48、fix49 的代码**均已提交并推送**
> （`63153ed` / `d783448` / `223c8c5`），工作树干净。本节条目保留"待用户实测"性质——
> **提交不等于游戏内验收通过**。

### 4.1a **根因 L（已确认并已修，2026-09-27，3.20.0-fix40）**：原版 WildcardPattern 的 mixin 截胡我们具体样板的解码

证据（3.20.0-fix39 配对打印）：
```
配对样本（GT）：concrete@1967ead7 out=out-empty ‖ details=WildcardPreviewPatternDetails
                details.getPattern()@3dd13aa2 out=out-empty 与concrete同一对象=false ‖ details.getOutputs()[0]=铁板
```
⇒ `getPatternForItem(concrete)` 返回的**不是 AE2 的 `PatternHelper`**，而是原版模组的"轻量预览 details"
（输出恒为模板的代表输出＝铁板）。

原版 mixin（javap 实证 `com.myname.wildcardpattern.mixin.ItemEncodedPatternMixin`）：
```java
private void wildcardpattern$useLightweightPatternDetails(ItemStack stack, World world,
        CallbackInfoReturnable<ICraftingPatternDetails> cir) {
    if (!WildcardPatternGenerator.isWildcardPattern(stack)) return;
    ... cir.setReturnValue(<轻量预览 details>);
}
// 判据：stack.getItem()==ModItems.wildcardPattern
//    || CompositeWildcardPatternGenerator.isCompositeWildcardPattern(stack)   // 键 CompositeWildcardPattern
//    || tag.getBoolean("WildcardPattern")                                     // 键 WildcardPattern
// 同一 mixin 还截胡 getOutput(ItemStack) / getOutput(IAEStack) / tooltip
```
**为什么我们的具体样板会命中**：搬入的端口代码会给样板打 `WildcardPatternGenerator.markAsWildcard(stack)`
（键 `WildcardPattern=true`；调用点 `MessageUpdateWildcardConfig:72`、`WildcardPatternWindow:1934/2768/2779`、
`ItemWildcardPattern` 多处），而 `SmartWildcardExpander.buildConcretePattern` 只剥掉**我们自己**的子树
`ae2qolSmartWildcard`，**原版那个键随整份 NBT 被复制进每张具体样板** ⇒ 原版 mixin 命中。

后果（与用户全部症状吻合）：三族 `注册 details=396` 但 `输出种类=1`（全铁板）；AE 终端只认铁板；
GT 2714「能下单但不合成」（注册进去的是预览 details，不是真样板）；原版模组自己的样板正常（它自洽）。

**修复思路（三部分，3.20.0-fix40 已实施 ✅）**：
1. ✅ `buildConcretePattern` 里**剥掉原版标记键**（`WildcardPattern`、`CompositeWildcardPattern`）——
   新 `WildcardPatternGenerator.clearWildcardMarker(stack)`（连带 composite 版）；样板**本体**保留
   （Wild 窗口桥接需要）；
2. ✅ 新增 `wildcard/SmartWildcardDecoder.decode(stack, world)`：本模组物品直连
   `new appeng.helpers.PatternHelper(...)`，绕开注入；其它物品仍走 API（AE2FC 等）；
   四处调用点全部改到该入口（`SmartWildcardPatternSlot`、`SmartWildcardGtnlPatternSlot`、
   `MixinPatternDualInputHatchWildcard`、`MixinDualityInterface`）；
3. ✅ 复核 `markAsWildcard` 调用点：全部作用于样板**本体**（`MessageUpdateWildcardConfig`、
   `WildcardPatternWindow`、`ItemWildcardPattern`、端口生成器自身），具体样板不经过 ⇒ 无需改动，
   已在注释中写明口径。
产物：`build/libs/AE2-QoL-3.20.0-fix40.jar`（1,770,171 B，SHA256 `F606CFE4…`）。
**待实测 4 项**：① `输出种类`=396、样本出现不同材料；② AE 各板可合成可下单；③ 下单能真正制作、
材料能正常退回（"点总成退回无东西、只有取消才返回"应随之消失）；④ 原版模组识别行为不变。
**不在本次范围**：原版模组的样板在总成里"能识别但不能合成"（手动电路也不行）——它自己那条链的行为。

**本轮静态排除的其他可能**：AE2 `PatternHelper` 不回写 NBT；`ItemStack.copy()` 深拷贝 NBT；
`getPatternForItem` 无静态缓存；`ItemStack` 不共享 NBT；电路（用户实测停用自动写＋手动设置仍不合成）。

### 4.1 3.20.0-fix38 待游戏内验收（2026-09-27，最新）

- ① 日志出现 **3 条** `展开样本：material=… prefix=plate in=… out=… 产出out=…`，且三行的 `material/产出out` **各不相同**；
- ② 注册行变成 **`输出种类=396`**（不再是 1）；
- ③ AE 终端里各种板材可合成、可下单；GT 样板仓下单后能真正合成；
- ④ 若出现 `展开时找不到任何可用的输出前缀`（WARN），把那行发我（它自带模板输出的全部矿辞名）。

### 4.2b 暂存设计稿（**不实施**，等 bug 主线验收后开工）

- **`docs/DESIGN_per-slot-catalyst-circuit.md`**（2026-09-27 记录，用户提出）：
  **移除样板侧的「电路 / 不消耗物品」设置，改为"每个样板格各自一个电路槽 + 一个催化剂槽"**，
  覆盖 GT 2714/2715、GTNL 21504/21505、PH 22069、PH MK.II 22179、本模组 MK.III 32108 五处宿主；
  样板只管"配方覆盖范围"。文档内含：动机与新证据、数据模型（机器 NBT `ae2qolSlotMeta` 按槽位对齐）、
  GUI 线框稿、同步/鉴权、移除范围与兼容口径、验收标准、**5 个待用户拍板的问题**、暂存说明。
  ⇒ **本轮不动代码**；实施前先出线框稿敲定。
- 触发它的新实测（2026-09-27，用户）：**把通配样板放进 AE2 原版样板供应器、贴到输入总线，
  可以正常发配物品材料；电路手动设置时完全正常** ⇒ 说明
  ① 我们的展开与 AE 注册这条链本身是通的；② 机器侧接受我们的具体样板；
  ③ **"电路由谁设置"是决定成败的变量**（样板自带 + M3 自动写虚拟槽 vs 玩家手动设置）。

### 4.2 3.20.0-fix37 待游戏内验收（已被 3.20.0-fix38 覆盖，保留供追溯）

- ① 日志注册行变成 **`输出种类=396`**（不再是 1），样本里能看到**不同材料**的板；
- ② AE 终端里**各种板材都可合成、能下单**；
- ③ GT 样板仓里下单后**机器真的开始合成**（不再卡在 `SOMETHING_STUCK`）；
- ④ 回归：原版模组样板行为不变；黑名单/总排除/规则级排除从 3.20.0-fix34 起生效（若发现材料被排除先查旧排除条目）。

### 4.3 3.20.0-fix35 待游戏内验收（已被 3.20.0-fix37 覆盖，保留供追溯）

- ① **同一总成**里两套模组的样板各放一张 ⇒ **两张都能展开出全部锭→板配方**（不再只认铁板）；
- ② 日志出现 `GT 通配样板注册（只追加拿，未 cancel）：通配槽=1 注册 details=N 本机含原版样板=M …` 且 `N>0`；
- ③ 我们的样板：AE 里能看到整批展开样板、能按任意材料接单；
- ④ 回归：原版模组单独使用时行为不变。

### 4.2 3.20.0-fix34 待游戏内验收（已被 3.20.0-fix35 覆盖，保留供追溯）

- ① 放进总成后日志出现 **`GT 通配样板注册：通配槽=1 注册 details=N`** 且 **N>0**，以及
  `GT 样板仓发现通配样板并展开：slot=… produced=N …`；
- ② AE 合成监控/样板列表能看到**整批**展开出的样板（不再只有铁板那一张），机器能按任意材料接单；
- ③ 回归：黑名单/总排除/规则级排除**从本版起才真正生效** —— 若发现某些材料被排除，
  先看该样板里是否有旧排除条目（那是它们应有的效果）；
- ④ 两套模组各放一张在同一总成仍都能展开（3.20.0-fix33 的互不干扰保持）。

### 4.2 3.20.0-fix33 待游戏内验收（已被 3.20.0-fix34 覆盖，保留供追溯）

- ① 加号后窗口**同一行**：输入 `ingot*`、输出 `plate*`；
- ② 放进 GT 样板输入总成：日志出现 **`GT 通配样板注册：通配槽=1 注册 details=N`** 且 **N>0**，机器能接单；
  不再出现 `规则槽位越界` / `材料交集为空`；
- ③ 旧样板：`模板自愈…` 后若触发重排，应看到 `模板重建后已重排规则槽位`；
- ④ **两套模组各放一张**在同一总成：我们的能展开，**原版 mod 的也照常展开**（互不干扰）；
- ⑤ 仍未识别时，请提供该总成的日志段（已埋好"外来样板识别"打点）。

### 4.2 3.20.0-fix32 待游戏内验收（已被 3.20.0-fix33 覆盖，保留供追溯）

- ① Wild 窗口里按 NEI 加号：**输入行出现 `ingot*`、输出行出现 `plate*`**（不再是 `gt.orepr...`）；
- ② 日志出现 `NEI 推导规则明细：#0 ore:ingot* / …`；
- ③ 样板放进 GT 样板输入总成（以及 GTNL / PH 22069 / MK.II / MK.III）：日志应出现
  **`GT 通配样板注册：通配槽=1 注册 details=N 映射总数=…`** 且 **N>0**（不再只有 `展开为空`），机器能接单；
- ④ **存量旧样板**（in/out 已被旧版删掉的）应出现 `通配样板模板自愈：已从 Wild 的行数据重建原生模板`，
  随后同样能展开（不必重配）；
- ⑤ 对照组：原版 WildcardPattern 模组的样板在原版总成里仍正常；未配置的通配样板仍按模板工作。

### 4.2 3.20.0-fix31 待游戏内验收（已被 3.20.0-fix32 覆盖，保留供追溯）

- ① Wild 窗口（右键样板）里按 NEI 加号 ⇒ **9 行当场出现配方**（整页替换），关掉重开仍在；
- ② 电路页签：点号**当场高亮**、「清除（继承）」可用；
- ③ 不消耗物品页签：NEI 拖入 / 加入手持 / 逐行删 均立即生效，8 行分页正常；
- ④ 配好的样板放进 GT 样板输入仓 / GTNL 超级总成 / PH 22069 / MK.II / MK.III **能接单**；
  日志应出现 `发现通配样板并展开：slot=… produced=N …` 与 `通配样板注册：… details=N`；
- ⑤ 对照组：**原版 WildcardPattern 1.1.0 的样板在原版总成里仍正常工作**（实例内两套模组同改一批类，硬约束）；
- ⑥ 未配置的通配样板 / 普通 AE2 样板仍按模板工作（不变量）。
- **下一轮候选**：3.20.0-fix26～3.20.0-fix30 的 CHANGELOG/README 章节补写；`CHANGELOG.md` 与 `zh_CN.lang`
  的历史乱码段按 UTF-8 重写；确认 `gui.wildcardpattern.*` 其余键是否也搬进本模组资源
  （现在依赖实例内原版 WildcardPattern mod 的 lang 文件）。

### 4.-2 用户报障三问题（2026-09-25）

**最新状态（第二轮实测，2026-09-25 晚）**

- **fix50 —— ✅ 通过**（commit `cfc146a`）。用户实测"完全修好了"。
- **fix51 —— ✅ 通过（仅 EMPTY 模式）**（commit `5300e61`）。
  `fix53-diag` 日志给出运行期实证：`IO-PICK` = 端口选中 **item** 通道；
  `IO-MOVE` = `OperationMode=EMPTY, FullnessMode=EMPTY, 被选中通道还有内容=true, 其余通道还有内容=true`。
  用户实测**流体被抽干、元件随后正常弹走**（搬空之后才判完成 = 预期行为）。
  ⚠️ **边界**：`FullnessMode=HALF` 时 `matches()` 无条件搬走元件，本修复不成立；若要支持 HALF，
  需追加"对本模组多通道元件改为全通道完成才搬"的 `shouldMove` 覆写（设计已备，未实施）。
  仍待补测：`FILL` 转入方向、普通流体元件对照行为。
- **fix52 —— 根因在客户端，且最终定位到整合包模组冲突**（commit `47654ce`）。
  诊断版在服务端包处理器埋了 A~G 全分支日志，**整场会话一条都没有** ⇒
  `PacketPickBlock.serverPacketData` 从未被调用 ⇒ **客户端根本没发包**。
  **第一层**：AE2 `ActionKey.PICK_BLOCK` 默认 `Keyboard.KEY_NONE`（未绑定）；两条互补路径分别要求
  「该键被按下」或「该键 == 原版 `key.pickItem`」。已把 `options.txt` 的
  `key_key.pick_block.desc` 由 `0` 改为 `-98`（备份 `options.txt.bak-before-pickblock-key`）。
  **第二层（最终根因，纯字节码取证）**：即便如此 `CLIENT-EVENT`（GTNHLib `PickBlockEvent` 到达 AE2）
  仍然一次都不出现，而用户的对照组证明"中键有反应"。查证：整合包内
  **`sciencenotleisure`（SNL 0.2.7-pre3）** 在 `Minecraft.middleClickMouse()`（SRG `func_147112_ai`）
  的 HEAD 注入并 `ci.cancel()`——`ClientUtils.onBeforePickBlock` 在"准星没瞄到实体"时执行完自己的
  1000 格远程取物后**无条件 return true** ⇒ 原版取物例程被整个取消 ⇒
  **`PickBlockEvent` 不会发出** ⇒ AE2 永不发 `PacketPickBlock` ⇒ fix52 永不执行。
  SNL 无配置开关；用户对照组看到的"快捷栏跳格"是 **SNL 的**行为而非原版。
  **修复（fix54，方案 A，已实施待验证）**：新增 `client/PickBlockCompatHandler`，监听 Forge
  `InputEvent.MouseInputEvent`（FML 总线，与 SNL/原版方法无关），条件对齐 AE2
  `handlePickBlock()` 并**额外预检"身上有无线终端"**（否则服务端会刷 `PickBlockTerminalNotFound`），
  自行补发 `PacketPickBlock`，交由 fix52 的服务端兜底接管。**待一次性验证**：
  生存模式下对"网络无存量 + 有样板"的方块按中键应弹出「要合成多少个」。
- 实例产物：`build/libs/AE2-QoL-3.19.0-fix54.jar`（**正式版**；`fix54-diag` 的埋点已全部剥离，
  失败分支收敛为「只记一次 WARN」，正常放行不记日志；诊断用的三份 Mixin 及其配置条目已删除，
  `mixins.ae2_qof.json` 回到通用 13 + client 16 = 29 条）。
  **用户实机验证**：日志 `branch E`（已排入开界面任务）→ `branch G7`（界面已打开，样板数=1）；
  物品到手后再按中键走 `branch B`（正确放行原版）。用户确认"完美，可以使用了"。
- **另查明两件与本模组无关的事**：① "进不去存档/新建世界也不行" = **内存不足**
  （实例 `-Xms8192m -Xmx9192m`，15.6 GB 机器上只剩 0.79 GB 可用 → 换页；用户重启电脑后正常进入世界）；
  ② 21:18:44 的 `StackOverflowError` = 纯原版 `TileEntityChest` ↔ 区块加载递归（跨区块边界的箱子）。
- 仍待补测：fix50 的「`max` 随电压变化」与「非电路板仍被拒」。

> 以下是**第一轮**实测记录（当时结论：1 通过 / 2 未达成），保留原貌；其 fix51/fix52 结论已被上方取代。

- **fix50｜万能维护仓电路板槽全拒（GT 5.09.54 新增校验链）—— ✅ 实测通过**（commit `cfc146a`）。
- **fix51｜IO 端口搬不出无限磁盘流体 —— ❌ 未达成**（commit `5300e61`）。
  实测：元件被端口**自动**从输入半区搬到输出半区，流体只搬一点点就停；普通流体元件对照正常。
  **源码级根因（本轮查实）**：`TileIOPort.moveSlot` 把元件从输入半区搬进输出半区，
  是否搬走由 `shouldMove → matches` 决定——`FullnessMode.HALF` 直接搬、`EMPTY` 模式则"**被选通道**为空就搬"，
  而端口对多通道元件只选中**第一个**通道（物品）。磁盘只装流体时物品通道为空 ⇒ 立刻判"已完成"并搬走。
  **fix51 的介入点（循环结束后补搬）形状错误**：判定发生在补搬之前。
- **fix52｜世界里键无存量+有样板不弹下单页 —— ❌ 未通过**（commit `47654ce`）。
  实测：完全无动静；背包确定无该物品；终端内该条目数量为 0。
  日志证据：fix52 已加载、两个 Mixin 均注入、**三条新增警告计数全为 0**（无异常 ⇒ 静默走掉）。
  因 NEI 中键同款归队路径实测可用，"若走到排任务那一行本应弹界面"，故命中的是更早的**静默分支**。
- 当前待复现产物：**`build/libs/AE2-QoL-3.19.0-fix53-diag.jar`（诊断版，仅埋点、不改行为，非发布）**。
  **未部署**，部署需单独授权；部署后必须完全重启游戏。
- 根因、证据链、影响面与用法：见 `CHANGELOG.md` 的 (13)(14)(15)(16) 四节。
- 复现后要看的日志行：`[AE2QoL][diag] branch X:`（X∈A~G，定位问题 2）、
  `IO-PICK` / `IO-MOVE`（定位问题 1，后者直接给出"其余通道还有内容=…"）。
- 仍待补测：fix51 的**转入（FILL）方向**；fix50 的「`max` 随电压变化」与「非电路板仍被拒」。
- 已知未测项：fix51 的补搬**不参与**原版「搬空后弹元件到输出口」判定，且每个剩余通道按与主循环相同的
  初始预算处理（不递减原循环配额）——这两条是刻意的实现边界，已在 CHANGELOG 登记。

### 4.-1 MTE ID 让位与旧存档迁移（fix48，**代码已提交 `d783448`，待用户实测**）

- 背景：b3 引入 fissionevolved 后占用 32100/32101，本模组两个终端注册失败。
- 决策（用户选定）：**保留 fission，AE2-QoL 退让号段**。
  自适应电网终端 32100→**32106**；库存统计终端 32101→**32107**。
- 迁移：`mixin/gt/MixinBaseMetaTileEntityIdMigration` 在存档读取入口把「旧号 + 本模组专属键」
  的机器改写为新号，配置零丢失；fission 的 `Fission*` 键不会误判。
- 影响面：仅 b3 `World` 中 1 台已摆放终端（主世界 49,29,44）；无终端物品残留。
- 实测要点：进入该存档后确认终端仍在原位、网络频率与电压等级保持、四个子仓仍处于绑定状态；
  日志应出现 `[AE2QoL] migrated legacy terminal in save data: MTE id 32100 -> 32106`。
- 备份：`saves/World_bak_before_id_migration`（445 MB），异常时可整体回滚。
- **已提交并推送（`d783448`）；未部署。部署前必须单独征得用户同意。**

### 4.0 世界中键取物「可合成即打开下单页」（fix47，**代码已提交 `63153ed`，待用户实测**）

- 需求（用户已确认方案）：世界里对着方块按中键时——
  背包已有该物品 / 网络有存量 → 保持 AE2 原生；**网络没存量但有合成样板 → 打开「要合成多少个」界面**；
  两者都没有 → 保持原生无反应。只打开界面，不代点合成。
- 实现：`mixin/ae/MixinPacketPickBlock.java`（注入 `PacketPickBlock.serverPacketData` HEAD，
  `remap=false`，方法名用完整描述符）+ `ServerTerminalHelper` 新增
  `openCraftAmountIfCraftable(...)` / `hasNetworkStock(...)`；`RequestCraftingPacket` 改为共用前者。
- 判定顺序「能不管就不管」，异常一律退化为放行原版；被点物品经反射读 `pickedBlock` 私有字段。
- 已核对 FML `FMLEventChannel.fireRead` 直接 post 事件，与原版取物同上下文，故不额外归队线程。
- 当前版本 `3.19.0-fix49`；产物 `build/libs/AE2-QoL-3.19.0-fix49.jar`。
- 待用户实测（与 4.1 的材质项一起跑）：分别验证「网络有存量（原版取物）」「无存量+有样板（弹下单页）」
  「都没有（无反应）」三种情况，并确认终端放背包/饰品栏都可用。
- **已提交并推送（`63153ed`）；未部署。部署前必须单独征得用户同意。**

### 4.1 v7 材质接入（fix45/fix46，**代码已提交 `63153ed`，待用户实测**）

- 任务：给 8 台机器（万能维护仓 + 自适应电网 5 仓 + 无线 EU 两终端）换 v7 材质。
- **用户已选定方案 B；fix45 完成实现，fix46 修掉"装包后机器透明"。**
  - `MixinTextureMap` 在**每次**原版方块图集 `registerIcons()` 尾部补注册 25 张 v7 路径
    （fix46 删除"只注册一次"静态开关——`registerIcons()` 开头会 clear 清单，且启动期会多次执行）；
  - 注册成功后把图集图标回填到 `ModTextures.registerBaked(...)`，渲染端优先读取；
    `getMaxU()/getMaxV()` 全 0（sprite 未装订）时回退 GT 机箱，兜底图标多级取值保证非 null；
  - 万能维护仓已删除 R2 无条件覆层，恢复 GT 发光/启用状态逻辑；
  - `v7_textures=off` 或资源不可达时强制回退 GT 默认外观。
- **必读档案**：`docs/v7-材质方案结论档案.md` —— 含方案 B 实施章节、fix46 透明根因与行为矩阵。
- 用户应使用完整 25 张资源包 `AE2QoL-v7-resourcepack.zip` 并置顶；旧 overlay-test 包不再是判断依据。
- 判读要点：`[AE2QoL-TEX] getIcon HIT` 的 UV 应**互不相同且非 0**；全 0 = 未进图集，全部相同 = missingno。
- **已提交并推送（`63153ed`）；本轮产物未部署，部署前必须单独征得用户同意。**

### 4.2 稳定性整改（2026-09-18 起，暂停中）

- 2026-09-18 用户已明确授权全面整改此前审查的问题；旧日志中“只授权 Tooltip/堆叠，不处理 A01–A19”是历史边界，已被本轮新授权取代。
- 基线 `223c8c5`（fix49），工作树干净、与 origin/master 同步。
- 顺序：存档/资源安全 → 专用服同步与权限 → 无线生命周期 → 协议与 NBT/统计；每批修改后验证，最终回填真实结果。
- 当前在核对调用契约、编写修复；**尚未完成，不是已验收稳定版**。
- 测试实例仍只读；未部署、未游戏实测。
- 补充（2026-09-25 核实）：`util/ItemIdentity` 已落地并用于 `NetworkInventoryCache`（A14）、
  `QuestDetectLogic` 候选去重（A15）与 `MergedTerminalScrollReplacePacket` 候选环（A13），
  方向与审查报告 A13/A14/A15 的建议一致；但 `docs/mcp-full-function-audit-fix41.md` 的状态列**尚未回填、也未实测**，
  不得据代码改动宣称已关闭。

> 说明：4.-1 / 4.0 / 4.1 三项代码均已提交，进入用户实测阶段；4.2 未推进。

---

## 五、待办任务队列（优先级从高到低）

### ★ 当前（3.25.0 线程任务 · 提交 1/2 已完成待提交，2026-10-02）

- 口径（Stage 0 拍板）：TST 式 N 条线程各自匹配/计时/扣料/产物；单配方时跑 N 份相同配方、每份各有并行；沿用维护仓线程字段（上限 64）；
  以"装了我们的维护仓且线程>1"为开关；**电力不够自动降线程**；替掉旧跨配方合并；**必须能看见每条线程进度**。
- 取证结论：GT 机器只有一套进度/耗电/产物字段；各家族共享入口 = `doCheckRecipe()`（GT 原生 `checkProcessing():1103`；TST `HephaestusAtelier:512` 等）
  与 `incrementProgressTime()`/`addItemOutputs`/`addFluidOutputs` ⇒ 拦截点就定这两处。
- 已实现（构建通过）：`hatch/thread/Ae2qolThreadEngine`（64 槽状态 + 汇总 + 缺电降级策略 + 维护仓→主机反查 + `Row` 快照的 PacketBuffer 序列化）；
  `MixinMTEMultiBlockBase`（`doCheckRecipe` HEAD 接管并循环调 GT 原始实现；`incrementProgressTime` HEAD 推进/落地/补配方；外壳写入）；
  `MixinProcessingLogicSpeed` 旧接管分支摘除（标 `@Deprecated` 待清理）；维护仓新增「线程」页（汇总+表头+活跃列表+空闲折叠，MUI2 实时同步）；
  中英 lang 各 +21 条 `ae2_qof.threads.*`；版本 3.25.0。
- 产物：`build/libs/AE2-QoL-3.25.0.jar`（1,838,048 B，SHA256 `838865CEF407596EB1857CCF336789F90C9B02B6EC6940ED5AC1CEA3A519AFD0`，包内 mcmod.info 两条目均为 3.25.0）。
- [ ] 提交 1/2（引擎 + 线程页）后 **待用户实测**：① 线程页能看到逐条进度在跑；② 线程=1/拆仓 ⇒ 回原生行为；
      ③ 单输入 ⇒ N 条跑同一配方、混放 ⇒ 错峰不同配方；④ 拉低电力 ⇒ 活跃数下降 + 显示"缺电降级"且不停机；
      ⑤ 与 GT 原生对照：同配方下总产出/总耗电≈N 倍且**不凭空多出/吞掉物品**。
- [ ] **提交 2/2 = WAILA**：常态 1 行汇总 + 最慢 2 条，潜行展开最多 8 条；需新增每 10 tick 的降频 S2C 小包 + 客户端按坐标小表（卸载清理）。
- [ ] **未解决（另开一轮）**：P2「样板网格第一列显示不全」（已排除"左滚动条"猜测；等局部放大图）。
- [ ] 旧 `ae2qol$crossRecipeProcess` 死代码清理。

### ★ 当前（3.24.0-fix2 轮，2026-10-02）

- 用户实测反馈：**剪切/粘贴后机器的样板窗不立即刷新**（关窗重开也没用，必须走开再回来）；AE2 接口终端/下单侧一直即时正确。
  同轮另报：**样板网格第一列显示不全**（未解决，见下）。
- 根因（实证）：窗里格子读的是**客户端自己 TE 的 `pattern[]`**（`MTEPatternCraftingBufferMKIII:422-424` 把
  `new ItemStackHandler(acc.getAe2qolPattern())` 交给 `ModularSlot`；`Arrays.asList(array)` 绑数组对象）；
  剪贴板只改服务端数组 + 通知 AE；**GT 5.09.54.133 没有下发入口**（实例 jar 字节码：无 `issueClientUpdate`、
  `issueTileUpdate()` 是 `Code: 0: return` 空实现、`BaseMetaTileEntity` 不实现 `getDescriptionPacket`）
  ⇒ 客户端只能靠**区块包**更新。
- 修法（用户选"方案 A"）：新增 `ph/PatternClientSync`（原版 `S35PacketUpdateTileEntity` 只发给**跟踪该区块**的玩家）；
  `PatternSlotPersistence.load` 改**原地写入**（槽数一致时不换数组）；剪切/粘贴后各下发一次（复制不改机器，故不下发）。
- 产物 `build/libs/AE2-QoL-3.24.0-fix2.jar`（**1,821,856 B**，
  SHA256 `04804B36C311F99400E6D7E3695291CA9D9F74201969209E8C618B4B1C813BA2`）；构建 `BUILD SUCCESSFUL`；
  字节码核对 `new S35PacketUpdateTileEntity(IIIILNBTTagCompound;)`。
- [ ] **待用户实测**：剪切 → 不关窗不走开 → 打开窗应为空；粘贴同理；复制不应改机器；
      若仍不刷新，看日志 `[AE2QoL] 样板同步：… 已下发给 N 名…`，**N=0 即"区块跟踪者"判定没命中**（需改半径广播）。
- [ ] **未解决（另开一轮）**：样板网格**第一列显示不全**。已量清几何（面板 168 / 滚动区 `size(162,162)`@`pos(3,3)` / 内容 162），
      并用字节码**推翻**"左滚动条压第一列"（`new VerticalScrollData()` → `axisStart=false` ⇒ 滚动条在**右**侧）；
      用户说红框那条竖条是**两个 UI 之间的背景** ⇒ 仍缺一张**局部放大图**才能定案，故本轮未改。

### ★ 当前（3.24.0-fix1 轮，2026-10-02）

- 由来：用户追问「你材质，和本地化键都做了吗，tooltips 写了吗」⇒ 逐项自查后确认 **本地化键 ✅（中/英各 25 条）**、
  **tooltip ✅（MK.IV 机器 tooltip + 3 行 desc；剪贴板三行用法 + 当前模式）**，但**材质有缺口**：
  ① 剪贴板物品**漏了染色**（会显示成未染色的 AE2 编码样板，与真样板无法区分）；
  ② MK.III/MK.IV **没有专属贴图**（外观继承 PH 基类覆盖层，三台机器长相一致）。
- 用户决定：**只补剪贴板染色**，MK.IV 外观不改（登记为外观约定）。
- 已做：`ItemPatternClipboard.getColorFromItemStack` → **紫 `0xC77DFF`**；lang 中/英 `clipboard.tooltip.0` 加 `§d` 标记与三色说明；
  版本 → **`3.24.0-fix1`**；CHANGELOG 记录 (54)、MOD_MAP 外观约定、README×2。
- 产物：`build/libs/AE2-QoL-3.24.0-fix1.jar`（**1,819,553 B**，
  SHA256 `72C21052A3DC465C9FE5E5BF1469ADA69BE965CF6A87C03339D602AEF0028AD6`），构建 `BUILD SUCCESSFUL`（`EXIT=0`）。
- **坑位复现**：用源码方法名 grep 1.7.10 编译产物查不到方法 —— 产物里是 **SRG 名**
  （`getColorFromItemStack`→`func_82790_a`、`addInformation`→`func_77624_a`、`onItemRightClick`→`func_77659_a`、
  `onItemUse`→`func_77648_a`）。核对要用 SRG 名或看 `-c` 的 `ldc` 常量。
- [ ] 部署（待确认游戏未运行）＋ 用户实测：剪贴板图标应为**紫色**，与绿（通配样板）/青（生成器）区分。

### ★ 当前（3.24.0 轮，2026-10-02）

- [x] **新增 MK.IV（360 样板槽）**：`ph/MTEPatternCraftingBufferMKIV.java` = MK.III 的复制品，只改
      `PATTERN_SLOTS=360`（`TOTAL_ROWS` 自动 40）、`MTE_ID=32109`、内部名 `…me.mkiv`；MK.III 保持 144/32108 不动。
      注册在 `PhIntegration`（同一 PH 守卫），配方 **X = MK.III ×1** + 大师/高级电路，并把 `MKIV.Inst` 注册进
      AE2 接口终端注册表；创造页追加 `mkivStack`。
- [x] **修既有 bug：MK.III/MK.IV「样板不进存档」**。根因：PH 只在 `PatternDualInputHatch$Inst` 里读写
      `patternSlots`/`multiplier`，我们的方块实体是**跨包子类、不在那条链上** ⇒ 重进世界样板全丢。
      修法：新增 `ph/PatternSlotPersistence`（PH 键名兼容）+ 给 `MixinPatternDualInputHatchAccess` 加 5 个
      `@Accessor`（customName/additionalConnection/allowopt/normalopt/saved）与 1 个 `@Invoker`
      （updateValidGridProxySides）；两台机器的 `loadNBTData`/`saveNBTData` 都接上。
- [x] **新增「样板剪贴板」物品**（`item/ItemPatternClipboard.java`，只在装 PH 时注册）：右键空中切模式
      （复制/粘贴/剪切）、潜行+右键空中看状态、右键机器执行；只对 MK.III/MK.IV 生效；**全部服务端逻辑、不新增网络包**
      （样板数据不过网）；剪贴板存玩家 `PlayerPersisted`（跨维度、退出重进都在）；剪切 = 先写入剪贴板成功再清空源。
- [x] 构建 `BUILD SUCCESSFUL`（无管道取码 `EXIT=0`）+ 产物核对（新类入包；`javap -constants` 实证
      `MTE_ID=32109`/`PATTERN_SLOTS=360`/`TOTAL_ROWS=40`；两台机器均有 save/load；包内 lang 含新键）。
      产物 `build/libs/AE2-QoL-3.24.0.jar`（**1,819,391 B**，
      SHA256 `42756A0AA120D07504B989D008DA1FA6CDA66B5A44A3E6D521AC6DC997ACC4AF`，含更新后的指南页）。版本 → `3.24.0`。
- [ ] **待用户实测**：① 创造页能看到 MK.IV、用 MK.III×1+电路能合成；② MK.IV 装到多方块上，AE2 接口终端与
      本模组终端都能看见它、样板窗 9×9 可滚动 40 行；③ 剪贴板「复制 → 粘贴」把 144 张样板与倍率搬到 MK.IV；
      ④ **存档修复验收**：MK.III 与 MK.IV 各放几张样板 → 退出 → 重进 ⇒ 两台都还在；
      ⑤ 对非我们机器右键剪贴板应提示"目标不是本模组的样板总成"且无改动。
- [ ] **部署**：等用户完全退出游戏后部署本地实例（服务端那份需用户自己上传）。

### ★ 当前（3.23.2-fix1 轮，2026-09-28）

- [x] **修「专用服务器上右键打不开 Wild 窗口 / 批量样板生成器界面」**。根因（**服务端日志实证**）：这两个界面是搬运来的
      **MUI1** 窗口，其 `getServerGuiElement`（服务端那一半）**也调用 `createWindow(...)`**，而窗口类里含客户端专用代码
      （fix43 起「改」按钮 = `Minecraft.getMinecraft().displayGuiScreen(new GuiTextInputDialog(...))`）⇒ 专用服务器上
      Forge `SideTransformer` 拒绝加载客户端类（`NoClassDefFoundError: net/minecraft/client/gui/GuiScreen` /
      `Attempted to load class bdw for invalid side SERVER`）⇒ FML 取不到容器 ⇒ **开窗包不发** ⇒ 客户端「完全没反应」；
      单机是 CLIENT 侧故正常；其它界面走 MUI2/原版容器故正常。
- [x] 修法（方案 A）：新增 `merged/ServerSafeModularContainer.slotless(player)`（空 `ModularWindow` + 零槽位 `ModularUIContainer`，
      异常记 WARN 不静默）；`wildport/gui/WildcardGuiHandler` 与 `apgport/gui/GuiHandler` 的 `getServerGuiElement` 只返回它，
      **窗口只在 `getClientGuiElement` 构建**；顺手降级 `[AE2PatternGen] …Side=SERVER/CLIENT` 两条 INFO 为 DEBUG，
      并删除 `ItemNetworkDataStick.hasData` 热路径调试日志（实测单个会话 4146 条）。
- [x] 构建 `BUILD SUCCESSFUL`（无管道取码 `EXIT=0`）+ **产物字节码核对**（服务端方法只剩 `slotless`/`FMLLog.fine`，
      无 `createWindow`/窗口类）。产物 `build/libs/AE2-QoL-3.23.2-fix1.jar`（**1,797,885 B**，
      SHA256 `FB5BBC5DAF05660CF0FFF7458A6212439D4CD421A4B5C9E2D085852D48A9501F`）。版本 → `3.23.2-fix1`。
- [ ] **待用户实测（必须在专用服务器上）**：① 右键两个物品都能开窗；② 服务端日志不再有 `NoClassDefFoundError: GuiScreen`；
      ③ Wild「改」→确认→能重开、生成器对照表选行→能回生成器；④ 单机与其它界面不受影响；⑤ `hasData`/`[AE2PatternGen]` INFO 噪声归零。
      **服务端 jar 需用户自行上传**（托管机我只部署了本地实例）。
- [ ] 回退方式：`git revert <本提交>`；若服务器上仍失败，看服务端是否有
      `[AE2QoL] 服务端无槽位容器创建失败：…`（那时改用纯原版 `Container` 的 B 方案）。

### ★ 当前（3.23.2 轮，2026-09-28）

- [x] 只读取证「原版/整合包是否已有带 NBT 的中键取物」⇒ **整合包 SNL 自带**（详见 CHANGELOG 记录 (50)、`docs/mixin_notes.md` 已知风险第 9 条）。
- [x] `git revert` 删除 3.23.0/3.23.1 自研实现（-552 行），**保留 fix54 的 AE2 取物补发**；版本 → `3.23.2`；全量构建 + 产物核对通过。
- [x] **已部署 3.23.2 到 `GT_New_Horizons_2.9.0-beta-3_Java_17-26` 实例**（2026-09-28；前置进程判据未命中 → 旧 `AE2-QoL-3.23.1.jar` 移入 `_ae2qol_jar_backup\prev-AE2-QoL-3.23.1.jar` → 复制新 jar → SHA256 `54423272…` 一致 → `mods` 内只剩 **1** 份）。
- [ ] **待用户实测 4 项**：① 创造模式 Ctrl+中键点"装了东西的箱子" ⇒ 物品 lore 多 `(+NBT)` 一行；② 放下 ⇒ 内容原样回来（SNL 写回）；③ 普通中键行为不变（远程取物、**无** NBT）；④ 生存模式 fix54 的 AE2 取物补发仍正常（弹「要合成多少个」）。
- [ ] **文档债（本轮未清，如实登记）**：fix44–fix53（对照表 / 中文输入对话框 / UI 修整 / Wild 重开）与 3.23.0/3.23.1 **没有**逐条 CHANGELOG 记录，README 也停在 fix43 之前；需要时单独一轮补齐。
- [x] **2026-09-28 追加（只读审计，未动代码）**：Wireless Nexus 1.0.2 的版权实测为 **LGPL-3.0**（非 MIT；上游 `LICENSE` 交叉验证；`resources/LICENSE` 是未填写的 MIT 占位；上游无 `LICENSE-template`），
      吞并可行性审计已完成并落成文档：`docs/DESIGN_wireless_nexus_merge_audit.md` + `docs/THIRD_PARTY_NOTES.md` §六 + `CREDITS.md` 第 8 条 + CHANGELOG 记录 (51)。**未吞并、未构建、版本仍 3.23.2**。
- [ ] **待用户决定（吞并 Wireless Nexus）**：收益 = 少一个 jar；成本 = LGPL-3.0 §4 合规四件套 + 4 个硬坑（同名 modid 双注册 / 存档契约与两参 `registerBlock` 的域 / 本项目缺 late mixin 注册面 / 两个同目标类 mixin 共存）。
      用户已选：动机=减少一个 mod 文件、保留原 modid `ae_wireless_nexus`、以后可能公开分发。若实施，版本按"加功能"→ **3.24.0**，并须先让用户删除实例里的独立 `【私货】ae_wireless_nexus-1.0.2.jar`。
- [ ] 未推送：`git push` 待用户指示。

### ★ 当前主线：v7 材质接入（fix45/fix46，已提交，待实测）

**已完成**
- [x] 定位 8 轮紫黑根因：`registerIcon` 对新路径恒返回 missingno（UV 证据）。
- [x] 用户选定方案 B；实现 `MixinTextureMap` 图集尾部补注册，并打通 `ModTextures.registerBaked` 渲染链。
- [x] 万能维护仓从方案 R2 回归 GT 状态贴图；v7 生效时使用自有分面贴图。
- [x] 产出并更新 `docs/v7-材质方案结论档案.md`（8 条已证伪路径 + 方案 B 实施档案 + 行为矩阵）。
- [x] fix46 修掉"装包后机器透明"：删除"只注册一次"开关 + 退化 UV 兜底 + 非 null 兜底图标。
- [x] fix45/fix46 完整离线构建通过，版本号已随 fix47~fix49 迭代到 `3.19.0-fix49` 并提交（`63153ed`）。

**待办（按优先级）**
- [ ] **P0 用户实测方案 B**：部署 fix49 后，先不装完整包确认 8 台全为 GT 原样；再置顶完整包确认 FRONT/TOP/SIDE 分面正确。
- [ ] **P0 用户实测开关**：`v7_textures=auto/on/off` 三态分别重启验证，`off` 必须完全回退。
- [ ] **P0 用户实测维护仓**：确认发光层、启用/停用状态恢复；GT 原版维护仓不再被连带改外观。
- [ ] **P0 用户实测 fix47/fix48/fix49**：世界中键取物三态（见 4.0）；旧存档终端迁移（见 4.-1）；
      b3 依赖升级后 6 处 API 断裂（`RenderBlockExIOPort`/`RenderQuestDetector`、`applyClientSwap`、`getNameSuffix`）的实际表现。
- [ ] **P1 日志判读**：`[AE2QoL-TEX] getIcon HIT` 的 UV 互不相同且非 0 为成功；全 0 = 未进图集，完全相同 = missingno。
- [ ] **P1 方案 B 异常处理**：若 Angelica 或资源重载导致不生效，按日志决定是否需要追加 reload 后重注册逻辑。
- [ ] **P2 清理无效路径代码**：用户实测通过后移除 `V7TextureStitchHandler` 死代码，并把 `STITCHED` 旧通道降级或删除。
- [ ] **P2 诊断日志清理**：`ModTextures` 中的 `HIT_LOGED`/`MISS_LOGED`/`[AE2QoL-TEX]` 输出待收尾时移除或降级。
- [x] **P2 文档收尾（2026-09-25 完成）**：已更新 `docs/MOD_MAP.md`、`docs/mixin_notes.md`、
      `README.md`/`README.en.md`（本版变化 + 依赖对照表）、`docs/GTNH-构建与代码参考.md`、`docs/GTNH-迁移移植指南.md`。
      剩余可选：用户实测通过后再把 `MOD_MAP` 中"方案 P 遗留"字样与实测结论对齐。

**已知约束（勿重复踩坑）**
- 构建必须 `JAVA_HOME=E:\java17`（系统默认 Java 25 会失败）。
- 打包资源包必须用 `jar cfM`，**禁用 PowerShell `Compress-Archive`**（产出反斜杠条目 → 资源全 MISSING）。
- 1.7.10 资源包列表**越靠后优先级越低**，我们的包必须置顶。
- 部署 jar 前**先删旧 jar**，新旧不能共存。

### 本轮稳定性整改（暂停）

- [ ] 按 A01–A19 整改并建立可重复回归证据。
- [ ] 构建与制品检查；专用服真实验收单独记录，未测项不标通过。

### 上一轮 fix43

- [x] 堆叠上限改为 64，Java17 构建并回填结果。
- [ ] 另行获批部署后实测普通/相同 NBT 堆叠、不同配置不混堆及安装拆卸。

### 上一轮 fix42

- [x] 完成单入口修复、Java17 构建、隔离回归、文档同步并回填真实结果。
- [ ] 用户批准部署后，在真实客户端验证物品、流体、快速切换与原生/Chromatic Tooltip。
- [ ] 后续修复仍按问题逐项确认；不得因本次授权顺带处理 A01–A19。

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
2. 权限边界：仅允许读写本项目目录内文件；`reference_src` 为只读参考（当前有效目录 `E:\wzt\MC\modcreater\reference_src_290b3`，旧的 `reference_src_290b1_已过期` 已废弃），禁止复制其源码入库；测试实例严格只读——当前基线实例 `E:\wzt\MC\PL genmulu\GT_New_Horizons_2.9.0-beta-3_Java_17-26`（fix48 起），历史实例 `E:\wzt\MC\PL genmulu\GT_New_Horizons_2.9.0-beta-1_Java_17-25(1)`
3. 代码规范：遵循原有代码风格，不擅自大规模重构旧代码
4. 兼容要求：不破坏 GTNH 原版机制，兼容对应版本的 AE2、格雷科技本体
5. 安全规则：禁止自动执行删除文件操作，删除操作必须人工确认；部署 JAR 到测试实例前必须单独征得用户同意
6. 构建要求：修改代码后必须通过 gradle 编译验证，无报错（构建方式见第八节）
7. 版本对齐：所有配方、参数、数值与 GTNH 官方设定保持一致
8. 提交约束：一个修复一个 commit；代码、文档、版本号变更放在同一 commit。历史遗留的 `build_compile*.log` 已于 fix47（`63153ed`）随"清理确认无用的残留文件"一并移除并入库，**不再作为保留项**；如需重新生成构建日志，按需放临时目录，不要入库。

---

## 七、已否决方案（避免重复踩坑）

- Tooltip 按“最近一次文本 + 1 秒时间窗”全局去重：会跨物品/界面抑制相同文本，不标识一次渲染；本轮移除。这是静态风险，不冒称已在游戏复现。
- Tooltip 只在 `currentTip` 上做 contains 检查：当前 Compat 的通用与物品路径使用独立列表，合并前看不到另一列表中的重复行；不作为根因修复。
- 只读 Compat 上游 main 就添加另一种流体适配：本实例 1.0.31 实际走 `GTUtility.getFluidDisplayStack`；必须以已安装版本为准。

- 方案名称：为 F22 库存统计终端在 `init` 阶段用 `try-catch` 防御层 + RFB `childDelegations` 注入绕过启动崩溃
  - 否决原因：已尝试 5 轮（槽位调整、catch 防御、诊断输出、RFB childDelegations 注入、`try-catch` 兜底）均未解决；崩溃本质是 init 阶段 Log4j/RFB 类加载链路二次失败，异常被掩盖，继续加固只会掩盖根因，需先拿到 crash-report。
- 方案名称：样板上传**只**按「机器中文名」在网络内自动匹配目标供应器（作为唯一判据）
  - 否决原因：同名机器多台时命中不确定，实测已出现「传错目标」；工作台合成类配方写的是 `crafting` 标记，无法据此反查 GT 配方池。
  - 现行做法（fix40/fix41）：机器名只用于「记住玩家上次选过的目标」这一层，目标判据本身改为「维度 + 坐标 + 部件朝向」稳定 key（`util/ProviderLocator`），并配合上传失败回执。若要做到 GTNH-ECO 那种首次即命中，需另做基于配方池反查的配对，见「关键知识笔记」中的对照分析。
- 方案名称：用**代码注册自定义贴图路径**来给 8 台机器换 v7 材质（共 8 轮尝试）
  - 否决原因：GT `registerIcon` 对运行时新造路径**恒返回 missingno 占位贴图**，游戏内必紫黑。
  - 已证伪的 8 条路：① `GTCustomBlockIconContainer`（GT 队列）② vanilla `TextureStitchEvent.Pre` 静态注册 ③ 实例注册+机箱兜底 ④ 资源可达性检查 ⑤ 换命名空间 `gregtech:blocks/ae2qol/` ⑥ 换时机到当前 atlas ⑦ 条件开关 `isReady()` ⑧ 覆层漏写 `.extFacing()`。
  - 决定性证据：25 张不同贴图注册后 **UV 完全相同**（`[0.17187744,0.5273462]-[0.1757788,0.53124756]`，宽 0.0039 ≈ 16px@4096）= 同一个 missingno 格子；且 missingno **非 null、名字正确、16×16**，极具欺骗性。
  - **当时的现行做法**：复用 GT 已有路径 + 资源包**同名覆盖**（对标 Modernity-GTNH，它不注册、零时序问题）。
    注意：该结论随后被**方案 B 取代**——fix45/fix46 证明「在方块图集 `registerIcons()` 阶段补注册自定义路径」可行，
    与原结论的"直接调 `registerIcon` 对新路径恒 missingno"并不矛盾（是注册时机不同）。详见 `docs/v7-材质方案结论档案.md` 第一、六节。
- 方案名称：把 v7 的 FRONT/TOP/SIDE 三张图分别塞进 `getTexturesActive` 返回的 `ITexture[]` 三个元素
  - 否决原因：GT 的 `getTexturesActive` 返回值是**叠加在同一批面上的多层覆层**，不是「第 1 个给正面、第 2 个给顶面」；三张叠在一起显示错误。
  - 现行做法：覆层**不分面**，一张图由 `extFacing()` 决定朝向，整机统一。
- 方案名称：用 `TextureFactory.of(IIconContainer)` 构建 GT 覆层
  - 否决原因：丢失朝向信息（缺 `.extFacing()`），覆层被贴到错误的面。必须与 GT 自身写法一致：`TextureFactory.builder().addIcon(...).extFacing().build()`。

- 方案名称：合并终端 `Shift+滚轮替换` 直接 `slot.putStack(candidate)` 完成替换
  - 否决原因：服务端信任客户端槽号且不从 ME 网络真实扣除，可被伪造包写入玩家真实背包槽，形成刷物漏洞，必须改为槽对象白名单 + 网络扣除。

---

## 八、关键知识笔记

**回调约定（fix42 起，当前仍有效）**

- `handleTooltip` 原样返回；`handleItemTooltip` 是唯一追加入口；无时间/文本全局状态，无 Chromatic 存在标志。
- 当前 Compat 1.0.31 的流体上下文转换为 GT 展示物品，进入已有 `NetworkInventoryCache.query`。真实桶/单元仍按物品计数，不能为了“支持流体”改此语义。
- `buildNetworkLine` 与接手工作区版本逐字对比未改；缓存与格式化文件 SHA256 未变。F4 文本不是 `MixinNEIRecipeWidget` 生成；该 Mixin 只绘制角标。

**构建与验证**

- 当前 MCP/Git Bash 命令：`env JAVA_HOME=/e/java17 GRADLE_USER_HOME=C:/Users/29357/.gradle ./gradlew build --offline -x spotlessJavaCheck -x spotlessCheck`。新构建结果见第十节。
- 构建命令（PowerShell）：先 `$env:JAVA_HOME='E:\java17'`、`$env:GRADLE_USER_HOME='C:\Users\29357\.gradle'`，再执行 `.\gradlew.bat build -x spotlessJavaCheck -x spotlessCheck --offline`；仅快速编译用 `compileJava`。
- 历史构建日志 `build_compile.log`/`build_compile4.log`（2026-09-15/16 生成）曾报 `StockMonitorTerminal 未实现 ISidedInventory 的 canInsertItem(int,ItemStack,int) / closeInventory()`。2026-09-17 用当前基线源码复核时，`compileJava` 与 `build` 均 `BUILD SUCCESSFUL`（Gradle 按内容哈希判定已编译成功），说明该历史报错不对应当前源码状态；若后续再次出现同类报错，应以新日志为准重新定位，不要直接套用旧结论。
- 测试实例只读；部署新 JAR 需单独批准。产物目录 `build/libs/`，当前产物为 `AE2-QoL-3.19.0-fix49.jar`（另有 `-dev`/`-sources` 变体，**不要部署**）。历史上 fix13/fix14/fix38/fix39 等旧产物已不在目录中，不要再引用旧产物名。

**版本与文档状态（2026-09-25 核实）**

- 版本号全项目统一为 `3.19.0-fix49`：`gradle.properties`、`src/main/resources/mcmod.info`、`README.md`、`README.en.md`、`CHANGELOG.md` 一致。
- 根目录 `mixins.ae2_qof.json` 与 `src/main/resources/mixins.ae2_qof.json` SHA256 完全一致（打包实际使用 resources 版本）。
- 发布产物：`build/libs/AE2-QoL-3.19.0-fix49.jar`。
- 当前源码规模：`src/main/java` 下 **210 个 Java 文件**（fix41 审查快照为 203）。
- 当前 Mixin 清单（`mixins.ae2_qof.json` 共 **29 条**）：通用 13 条（含 `ae.MixinPacketPickBlock`、`gt.MixinBaseMetaTileEntityIdMigration`、`client.MixinTextureMap`）、client 16 条；详见 `docs/MOD_MAP.md` 与 `docs/mixin_notes.md`。

**关键机制结论**
- **RFB `childDelegations` 注入已彻底删除（fix39）**：该补丁最初为绕开被误判的 RFB 类加载问题而写，但旧实现只处理 `List`/`String[]`、真实字段是 `HashSet`，因此从未生效。修复类型判断让它真正生效后，`net.minecraftforge.*` 被委托给子类加载器，绕过 RFB 的 `ExtensibleEnumTransformer`，导致 lwjgl3ify 枚举扩展失效、Railcraft `GeodePopulator` 静态初始化抛 `was not made extensible` 崩溃。**任何智能体不得再次加入该注入**；F22 崩溃的真实根因是 MTE ID 重号（见上）。

- F1 上传策略：strategy1 唯一供应器直传 → strategy2 按「记住的机器名（含 `@D维度 x,y,z` 后缀）」匹配 → strategy3 手动选；工作台类配方强制 `apu:recipeMap=crafting` 走独立分支，不再预填「合成」关键词；供应器列表按样板可接纳性过滤，同名机器聚合为一张卡片并固定指向空槽最多的那台。

- **供应器稳定定位（fix41，重要）**：供应器 ID 从 `System.identityHashCode`（内存地址派生值，随区块卸载重载、机器拆装、服务器重启失效）改为「维度 + 坐标 + 部件朝向」稳定 key，由 `util/ProviderLocator` 计算。该 key 随 `ProvidersListS2CPacket.locationKeys` 下发，上传时由 `UploadPatternPacket.locationKey` 回传；服务端 `ProviderLocator.find` 先按 key 匹配、失败再回退旧 ID。改网络包字段时必须客户端与服务端同时升级。
- **上传失败要有回执（fix41）**：新增 `network/UploadFeedbackPacket`（S2C）与语言键 `ae2_qof.info.upload_target_missing` / `upload_rejected` / `upload_no_permission`。以后新增服务端失败分支时，请一并调用上传处理器的 `notify`，不要只写日志。
- **样板可接纳性判断（fix41）**：`RequestProvidersListPacket.acceptsPattern` 与服务端实际写入都要遍历全部空槽（用 `isItemValidForSlot` 判断），不能只看第一个空槽——分类型样板槽会导致误判。
- **GUI 布局约束（fix40）**：`GuiProviderSelect` 采用 标题 / 搜索 / 列表 / 操作按钮 / 配方映射 五区纵向排版，各区域纵坐标由 `computeLayout()` 独立计算。新增控件必须走这套布局，不要再把多个控件放在同一个 Y 上（fix31 的控件互相遮挡即由此产生）。

**GTNH-ECO 配方识别与投递机制（2026-09-18 分析，供对照参考）**

参考源码（只读，禁止复制入库）：E:\wzt\MC\modcreater\GTNH-ECO-1.7.10-main，核心在 crafting/upload/ 下
PatternUploadTarget.java、PatternRecipeMatcher.java、PatternRouteKey.java、PatternUploadSession.java。

- **识别配方**：不依赖名字与映射表。把样板输入拆成物品/流体列表，逐个调用 GT 配方池的 findRecipeQuery().items(...).fluids(...).filter(输出匹配).find() 反查，命中即确定 recipeMap。
  关键细节：NEI 生成加工样板时会省略「虚拟电路」，因此先原样查一次，查不到再补电路号；matchesAnyIntegratedCircuit 会把电路号从 0 到上限逐个试，解决带电路配方的识别。
- **机器能力档案**：每台可收样板的机器都记录它能服务的 RecipeMap 与电路号。ME 接口贴着的机器通过 IInterfaceHost.getTargets() 遍历六面、读相邻 GT 机器的配方池得到；多方块样板输入仓则顺着 processingLogics 反查它服务的多方块。ECO 还会探测 ProgrammingCover（可编程覆盖板）。
- **匹配与投递**：先按 recipeMap 过滤，再按电路号过滤，再看有无空槽，最后按「是否来自真实机器 → 电路号吻合度 → 空槽数」排序取最优，全程无需玩家选择，首次即可命中。
- **我们与它的差距**：我们没有做「反查配方池 + 读机器张贴的配方池」这套配对，而是「玩家选一次 →
  按机器名记住」。因此首次遇到某配方且多台可收时仍会弹窗；选过一次之后可自动命中（fix40 已把
  记忆 key 对齐修正）。要做到全自动，需要新增机器侧配方池探测，属独立一轮的改动，尚未实施。

- **反面教材（fix31→fix40）**：照抄其它模组的界面外观、但未安排本模组自有控件的布局，会导致
  控件互相遮挡（fix31 把映射输入框与 添加/删除/刷新/取消 画在同一行）。参考外观可以，但必须
  按本模组实际功能重新排版并做窗口自适应。
- F6 通知条件（fix23 后）：`submitJob` 只要拿到返回值就记录下单玩家与产物；完成时若玩家背包没有绑定同网络的无线终端，退化为直接通知本人。`submitJob` 返回 null（CPU 忙）时保留进行中任务的通知状态（fix35）。
- F14 倍增条件：任务值 > 1、配方非 craftable、宿主实现 `ISmartDoublingMedium` 且开关已启用；`getMaxMultiplier` 有多个提前返回 1 的分支（未启用、craftable、流体接口、假合成、`BlockingMode != NONE`、`hasItemsToSend()`、无 adaptor）。
- F22 根因（重要，含 fix48 更新）：MTE ID **32001 曾被 GT 本体 LegacyUniversalChemicalFuelEngine 占用**（fix30 先改到 32101）；**b3 起 32101 又被 fissionevolved 占用**，故 fix48 再让位到 **32107**（配套自适应电网终端 32106）。可用 `docs/dumps/metatileentity.csv` 查任意 MTE ID 是否冲突。换号必须配套存档迁移（`MixinBaseMetaTileEntityIdMigration`），否则旧存档终端配置丢失。
- CoverRegistry：已统一存主世界 `loadItemData`（P1-007），旧 per-dimension 数据做一次性合并迁移；打开统计终端时刷新在线/存在状态（P1-008）。
- 线程与平台判定用 `Platform.isServer()/isClient()`；网络包统一走 `ModNetwork.CHANNEL`，服务端任务通过 `ServerTerminalHelper.scheduleServerTask` 回主线程。
- 生命周期：`MyMod.serverStopping` → `CommonProxy.serverStopping`，统一保存并清空自适应电网、无线频道/方块链接、供应器缓存；客户端 `WorldEvent.Unload` 清空高亮与 NEI 库存缓存。
**协作与环境注意**

- **工作流程已固化为 skill**：项目版 `ae2qol-workflow`（`.dsh/skills/ae2qol-workflow/SKILL.md`，随仓库走）；
  另在 `DSH_HOME\skills\evidence-first-workflow` 放了一份**与项目无关的通用版**，其它项目也会自动命中。
  两者同时命中时**以项目版为准**（项目版含本项目构建/实例/依赖与专属坑位）。
  修改流程时请同步这两份（通用版只放可迁移内容）。

- 当前（2026-09-25）会话平台为 **DeepSeek Harness（DSH）**，工具链为原生文件读写（read/edit/write）+ PowerShell；下一位接手者请按自己的平台重写本节，不要把旧平台的专用入口（PortableGit Bash、MCP `apply_patch`、`expected_versions`）当成必需步骤。
- 构建统一以 PowerShell 命令为准（见上），不再依赖 Git Bash 的 `env JAVA_HOME=...` 写法。
- fix41 审查快照为 203 个 Java 文件，按功能域审查，未做逐分支形式化证明；当前源码 210 个文件，新增部分（`ItemIdentity`、`WirelessEnergyTransfer`、`MixinTextureMap`、`MixinPacketPickBlock`、`MixinBaseMetaTileEntityIdMigration`）在 fix41 审查范围内**未被覆盖**。

---

## 九、历史会话操作日志

### 2026-10-02 · 3.25.0 提交 1/2：线程引擎 + 维护仓「线程」页

- Stage 0（4 轮提问）→ 用户拍板：沿用维护仓线程字段/上限 64/**每线程独立进度**/**第三方也要生效**/**缺电自动降线程**/替掉旧逻辑；
  另追加要求"**必须能看见每条线程的进度**"⇒ 先出线框稿（维护仓页 + WAILA），用户敲定后开工，**分两个提交：先 GUI，后 WAILA**。
- 取证：各家族共享 `doCheckRecipe()`（GT 原生 `checkProcessing():1103`；TST `HephaestusAtelier:512`、`GT_TileEntity_IndustrialMagicMatrix:559`）
  与 `incrementProgressTime()`；GT 原生一次只跑一个配方 ⇒ 线程只能在机器之外维护，机器那套字段当"外壳"。
- 实现：引擎（64 槽状态/汇总/缺电降级/维护仓→主机反查/`Row` PacketBuffer 序列化）+ 两个注入点 + 维护仓两页界面（MUI2 实时同步）+ 中英 lang。
- 踩坑（值得记）：MUI2 的 `GenericListSyncHandler` 序列化器是 **PacketBuffer** 形式（`static void write(PacketBuffer, T)` / `static T read(PacketBuffer)`），
  不是 NBT；`Row` 必须做值语义 `equals/hashCode`，否则每 tick 都判定"变了"而狂发包。另：`@Shadow` GT 的 `processingLogic` 是 **final** 字段，必须带 `@Final`。
- 版本 3.25.0；构建 `BUILD SUCCESSFUL`（`EXIT=0`）；产物 `AE2-QoL-3.25.0.jar`（1,838,052 B，SHA256 `3637B089…`）。

### 2026-10-02 · 3.24.0-fix2：修「剪切/粘贴后样板窗不立即刷新」（客户端 TE 副本没有下发通道）

- 用户实测：剪切/粘贴后机器的样板窗格子不刷新；**关窗重开无效**、**走开再回来才正确**；AE2 侧一直即时正确。
- 取证（只读）：① 窗里格子读客户端 TE 的 `pattern[]`（`MKIII:422-424` + `ModularSlot`，`Arrays.asList(array)` 绑数组对象）；
  ② 剪贴板只改服务端数组 + `onPatternChange()`/`refresh()`（只影响 AE）；③ 实例 GT jar 字节码：**无 `issueClientUpdate`**、
  `issueTileUpdate()` 是 `Code: 0: return`、`BaseMetaTileEntity` 不实现 `getDescriptionPacket` ⇒ 没有下发通道；
  ④ 1.7.10 区块包内含 TE 完整 NBT ⇒ 只有区块重载能刷新。
- 实现（用户选"方案 A"）：`ph/PatternClientSync`（原版 `S35PacketUpdateTileEntity` → 只发区块跟踪者）+
  `PatternSlotPersistence.load` 改原地写入 + 剪切/粘贴后各调一次；版本 `3.24.0-fix1` → **`3.24.0-fix2`**。
- 验证：`BUILD SUCCESSFUL`（`EXIT=0`）；`javap -c` 实证 `new S35PacketUpdateTileEntity(IIIILNBTTagCompound;)`；
  产物 `AE2-QoL-3.24.0-fix2.jar`（1,821,856 B，SHA256 `04804B36…`）。
- 同轮另一问题（**未解决**）：样板网格第一列显示不全 —— 已排除"左滚动条"猜测（字节码证明滚动条在右侧），
  等用户局部放大图再定案。**教训**：先用字节码把假设打掉，比在界面上盲改省事。

### 2026-10-02 · 3.24.0-fix1：样板剪贴板补图标染色（用户追问材质/本地化/tooltip）

- 用户追问后逐项自查：**本地化键 ✅**（3.24.0 新增中/英各 25 条，已在 jar 内核到）、**tooltip ✅**（MK.IV 机器
  tooltip + 3 行 desc；剪贴板 addInformation 三行用法 + 当前模式）、**材质 ❌ 缺口**（剪贴板漏染色；MK.III/MK.IV 无专属贴图）。
- 按用户选择只补剪贴板染色：`getColorFromItemStack` → 紫 `0xC77DFF`（绿/青已被通配样板、生成器占用），
  lang tooltip 首行加 `§d`；版本 `3.24.0` → `3.24.0-fix1`；MK.IV 外观不改并登记为外观约定。
- 验证：`BUILD SUCCESSFUL`；`javap -c` 实证 `func_82790_a`（SRG 名 = getColorFromItemStack）字节码为
  `ldc // int 13073919` = `0xC77DFF`；包内 mcmod.info 两条目与 lang 均已是新值。
- 教训：**核对 1.7.10 产物必须用 SRG 名**（或看 `ldc` 常量），按源码方法名 grep 会误判为"方法没编进去"。

### 2026-10-02 · 3.24.0：新增 MK.IV（360 槽）+ 修「样板不进存档」+ 样板剪贴板

- 工具平台：DeepSeek Harness（DSH Web GUI）｜模型：DeepSeek-V4.1-Flash
- 由来：用户先讨论 Apeiron 的功能对比（二合一输出总成、并行手法），最后定下"**自己做 MK.IV（360 槽）+ 用剪切/粘贴工具搬家**"，
  并要求**顺手修 MK.III 疑似不写档**。三条一起交付在 3.24.0。
- 关键取证（决定做法）：
  1. **PH 的存读档只在它自己的内部类 `PatternDualInputHatch$Inst`**（`PatternDualInputHatch.java:533/558`），
     我们的方块实体是跨包子类 ⇒ **从未保存 `pattern`/`multiplier`**（全仓与 GT 侧都没有别的写入者）⇒ 这就是"样板丢"的根因；
  2. PH 的键名/语义：`patternSlots` 的 `i0..iN`（`ItemStack.writeToNBT`）+ `multiplier`（**最小 1**）+
     `customName`/`additionalConnection`/`restrictToInt`/`allowopt`/`normalopt`/`saved` + `getProxy()` 读写；
     `allowopt` 在 PH 里**默认 true**（老档缺键时不能按 `getBoolean` 的 false 处理）；
  3. `PATTERN_SLOTS` 是 `public static final` 且被**私有** `ae2qol$ensureSlots()` 引用 ⇒ **继承覆盖常量无效**，只能复制类；
  4. `getProxy()` 是 `IGridProxyable` 的公开方法、`refresh()` 也是 public，但 `updateValidGridProxySides()` 是 private
     ⇒ 需要 `@Invoker`；5 个伴生字段是包私有/private ⇒ 需要 `@Accessor`。
- 实现要点（详见 CHANGELOG 记录 (53) 与 `docs/MOD_MAP.md` 的 MK.III/MK.IV 段）：
  `PatternSlotPersistence`（共享存读档）+ accessor 扩展 + MK.IV 类 + `PhIntegration` 注册（MK.IV 与剪贴板）+
  `AE2QoLCreativeTab` 追加 + 中英 lang + 指南/文档同步 + 版本 3.24.0。
- 验证：构建一次通过；`jar tf` 新类全在；`javap -constants` 实证 32109/360/40；两台机器均有 save/load；包内 lang 有新键。
- **未做/留给下一轮**：①②③（样板剪贴板只支持我们的机器，PH 自己的机器暂不支持）；④MK.III 的 144 那台
  上线前建议先备份世界（虽然本次已按 PH 键名补齐存读档，但属于"动过的已验证代码"）。

### 2026-09-28 · 3.23.2-fix1：修「专用服务器上右键打不开 Wild 窗口 / 批量生成器界面」

- 工具平台：DeepSeek Harness（DSH Web GUI）｜模型：DeepSeek-V4.1-Flash
- 流程：加载 skill → Stage 0（`ask_user_question` 两轮：服务器类型/版本一致性/现象/其它界面/单机对照/服务端日志可得性）
  → 用户给出**服务端日志**（一击命中根因，不需要猜）→ Stage 1 只读源码取证（两个 handler + `MergedGuiHandler` +
  窗口类的客户端引用计数 + MUI1 类常量池扫描 + GT/gtpp 先例）→ 方案选择（用户选 A）→ 实施 → 构建 → 产物字节码核对 → 文档 → 提交。
- 根因与修法见本文件「★ 当前（3.23.2-fix1 轮）」与 CHANGELOG 记录 (52)。
- **本次最有价值的三条可复用经验**：
  1. **专用服务器独有的失败，客户端日志会一条错都没有** —— 必须拿服务端日志；本轮初次只读客户端侧（有"Wild 界面将以空配置打开"
     的误导性线索）几乎要走偏。
  2. **`SideTransformer` 的拒绝发生在"第一次执行到引用客户端类的指令"**（不是类加载），所以异常栈指向**调用点**那一行、
     并且**没有**被调方法的帧 —— 这是判断"是加载被调用类出问题"的重要特征。
  3. **修法先找整合包自带先例**：GT 的 `gtPlusPlus/core/handler/GuiHandler.getServerGuiElement` 只 new 纯容器，
     证明"服务端不建 GUI"才是本项目环境的正确姿势。

### 2026-09-28（同日追加）· 只读审计：Wireless Nexus 的许可与吞并可行性（**未动代码**）

- 工具平台：DeepSeek Harness（DSH Web GUI）｜模型：DeepSeek-V4.1-Flash
- 用户问题：「`…\reference_src_290b3\Applied-Energistics-Wireless-Nexus-1.0.2` 看一下这个模组的版权，是什么，我们是否可以完整吞并这个模组」。
- **结论：版权 = LGPL-3.0（GNU LGPL v3）**。证据：参考树根 `LICENSE`（7,652 B，SHA256 `E3A994D8…`）+ 上游 `raw…/master/LICENSE`（HTTP 200）交叉验证一致；`src/main/resources/LICENSE`（也被打进 jar 根）只是**未填写**的 MIT 占位（`Copyright (c) [year] [fullname]`）；上游 `LICENSE-template` **HTTP 404**。⇒ 与同作者 `AE2InfinityCell`（当时确有 MIT 模板）不同，**本模组没有任何 MIT 依据**。
- 技术审计要点：39 个类 / 2,693 行；实例私货 jar 与上游源码**逐类一致**（只多构建期 `Tags`，未被改过）；`libs/ae_wireless_nexus-1.0.2.jar` 与实例那份**逐字节相同**（SHA256 `DD3E1AB0…`）；运行期依赖齐备（backhand 1.8.14 的 `BackhandUtils.getOffhandItem` 已 javap 确认；`+unimixins-all-1.7.10-0.3.1` 提供 `ILateMixinLoader`）；4 个硬坑 = ① 同名 modid 双注册（须删独立 mod）② 存档契约一字不改（方块 `wireless_controller`/`wireless_connector`、TileEntity `ae_wireless_nexus.*`、WorldSavedData 键 `ae_wireless_nexus.networks`，且 `registerBlock` 两参重载取"当前活跃 mod 容器"= 本项目坑位 #20）③ 本项目**此前没有 late mixin 注册面**（审计给了 A/B 两条路线）④ Nexus 的 `BaseMetaTileEntity` / `CommonMetaTileEntity` mixin 与我们既有 mixin **同目标类**，需逐方法比对共存。
- 本轮产出（**全部是文档，零代码改动**）：新增 `docs/DESIGN_wireless_nexus_merge_audit.md`；`docs/THIRD_PARTY_NOTES.md` 表格加行 + 新增 §六；`CREDITS.md` 新增第 8 条（原第 8「其他参考模组」顺延为第 9）；CHANGELOG 记录 (51)；本文件待办与会话日志。
- **无版本号变更、无构建、无部署**：产物仍是已部署的 `3.23.2`。

### 2026-09-28 · 3.23.2：删除自研「Ctrl+中键复制方块 NBT」（整合包 SNL 已自带同款）

- 工具平台：DeepSeek Harness（DSH Web GUI）｜模型：DeepSeek-V4.1-Flash
- 流程：加载 skill `ae2qol-workflow` → 只读取证（RFG 反编译源码 / 实例 272 个 jar 逐类扫常量池 / `javap -v -p -c`）→ `ask_user_question` 5 项拍板（删干净 / 版本 3.23.2 / 用 `git revert` / 构建提交后待用户退游戏再部署 / 写坑位记录）→ 实施 → 构建 → 产物核对 → 文档同步 → 提交。
- 关键取证手段与教训：
  1. **`findstr` 扫 jar 无效**（jar 条目是 deflate 压缩，常量池字符串根本搜不到）——本轮先据此得出"没有任何 jar 引用 `BlockEntityTag`"的**错误**结论；改用 `System.IO.Compression.ZipFile` 逐条解压 `.class` 后按 ASCII 找字符串，才拿到正确结果（SNL + Hodgepodge 命中）。
  2. 判断"谁注入了哪个方法"：`javap -v` 看 `@Inject(method=[...], at=..., cancellable=...)` 的注解常量池（SNL 用的是 **SRG 名 `func_147112_ai`**），再结合 `config/hodgepodge.cfg` 的开关；**不要**只凭"原版应该有"的直觉。
  3. 原版基线取证：把 `build/rfg/mcp_patched_ated_minecraft-sources.jar` 解到**临时目录**（**绝不落在工作区根目录**，坑位 #15）后再 grep；结论是 `BlockEntityTag` 全树 0 次。
- 结论与产物：见「一、当前会话元数据」的「本次会话目标」行与 CHANGELOG 记录 (50)。

### 2026-09-25（续） · 三问题实测回填 + 问题 1 根因查明 + 诊断构建

- 工具平台：DeepSeek Harness（DSH）｜模型：DeepSeek-V4.1-Flash
- 用户实测结果：**fix50 通过**；**fix51 未达成**（元件被自动弹到输出半区、只搬一点点）；**fix52 未通过**。

**本轮新增的查证手段与结论**

1. 读用户测试会话日志 `fml-client-3.log`：确认加载的是 `3.19.0-fix52`、两个 Mixin 均注入、
   本模组三条新增警告**计数全为 0** ⇒ 无异常，属**静默**失败。
2. 读 AE2 rv3-beta-1050 `TileIOPort` 源码 + `javap` 全字段：
   **IO 端口只有一个 `cells` 库存**，另有 `input[]`/`output[]` 索引数组 —— GUI 左右两组 6 格是
   同一库存的**输入/输出半区**；`moveSlot` 把元件搬进输出半区，`shouldMove → matches` 决定是否搬。
   `matches` 在 `FullnessMode.HALF` 下**总是搬**、在 `EMPTY` 模式下**只看被选通道是否为空**。
   ⇒ 多通道磁盘只装流体时，物品通道为空 → 立刻被判完成并搬走 ⇒ fix51 的介入点（循环后补搬）**形状错误**。
3. 二次纠正我自己的判断：此前据不完整的 `getInventoryByName`（只读了前 8 行）与过滤后的 `javap` 输出，
   误以为 IO 端口只有一个 6 格库存；本轮以**全字段 javap + 完整方法**纠正。

**本次交付（诊断版，仅埋点、不改行为）**

- `AE2-QoL-3.19.0-fix53-diag.jar`：问题 2 的静默分支 A~G 全部加一次性标记；
  问题 1 的 `IO-PICK`（端口选中了哪个通道）与 `IO-MOVE`（是否搬走 + 其余通道是否还有内容）加标记。
- 文档：新增 `CHANGELOG.md` (16) 节（实测回填 + 根因 + 埋点清单 + 用法）；本节与 4.-2 同步。

**是否编译通过**：是（无管道复核退出码 0；解包核对 `diagOnce` 与 5 个 `ae2qol$diag*/available/otherChannels` 成员已入包）。
**是否部署**：否（待用户单独授权）。
**是否提交/推送**：本地提交，未推送。

### 2026-09-25 · 用户报障三问题：源码定位 + 修复（fix50/fix51/fix52）

- 工具平台：DeepSeek Harness（DSH Web GUI）｜底层模型：DeepSeek-V4.1-Flash｜工作分支：master
- 会话方式：**先按用户要求反复提问确认问题（5 轮），得到"确认理解，开始读源码"后才读源码；
  给出修复思路并获"确认方案，开始修改"后才动代码。** 全程未做无证据的猜测性修改。

**调查手段（全部只读）**：`javap` 对比 GT 5.09.52.594 与 5.09.54.133 字节码、核对实例正在运行的
AE2 120 与 GT jar 的成员签名、读 `reference_src_290b3` 源码、读实例 `fml-client-latest.log`（13.8 MB）、
比对编译期 `libs/` 与运行期实例的依赖版本。

**关键教训（已写入 mixin_notes 与 CHANGELOG）**

1. **不要拿 `reference_src` 的源码当编译基线的真身**：本项目 `libs/gregtech-*.jar` 是**未反混淆**产物，
   MC 接口成员保留 **SRG 名**（`IInventory.isItemValidForSlot` 在 jar 里叫 `func_94041_b`）。
   第一次用 MCP 名去 `javap` 比对，得出"5.09.54 新增 `CommonMetaTileEntity.isItemValidForSlot`"——**错的**。
2. **AE2 的 IO 端口对每个元件只取一个存储通道**（`TileIOPort.getInv` 取到就 `break`）；
   `AEStackTypeRegistry.getAllTypes()` 是 HashMap 顺序，确定顺序的是 `getSortedTypes()`。
3. **AE2 的包处理器运行在网络线程上**（FML `FMLEventChannel` 的 `ServerCustomPacketEvent`）；
   在那里开界面／替换 `openContainer` 不生效。fix47 注释里"与原版同上下文故不需归队"是错误推理。
4. **静默兜底会掩盖故障**：`hasNetworkStock` 原先异常时返回 `true`（放行原版），
   一次异常就让功能永久静默失效且无痕迹——这是问题 2 难以定位的直接原因。

**本次修改文件**

- `hatch/AE2MaintenanceHatchUniversal.java`（fix50）、`mixin/ae/MixinTileIOPort.java`（fix51）、
  `mixin/ae/MixinPacketPickBlock.java` + `network/ServerTerminalHelper.java`（fix52）；
- 版本与文档：`gradle.properties`、`src/main/resources/mcmod.info`、`CHANGELOG.md`、
  `README.md`、`README.en.md`、`docs/MOD_MAP.md`、`docs/mixin_notes.md`、本文件。

**本次是否编译通过**：是。三次构建均 `BUILD SUCCESSFUL`，并用**无管道命令**复核进程退出码为 **0**；
产物逐次解包核对新增/改动的成员已入包且名称未被重混淆（`func_94041_b`、三个 `ae2qol$` 帮助方法）。
**是否部署**：否（按用户 D4 决定"三件都做完统一部署"，且部署需单独授权）。
**是否提交/推送**：已本地提交 4 个 commit（`8b6a5a7` 文档对齐、`cfc146a` fix50、`5300e61` fix51、本提交 fix52），**未推送**。

### 2026-09-25 · 接力文档与仓库真实状态对齐（仅文档）

- 工具平台：DeepSeek Harness（DSH Web GUI）｜底层模型：DeepSeek-V4.1-Flash｜工作分支：master，起点 `223c8c5`，工作树干净
- 会话目标：人工核对"文档写的"与"仓库实际是的"之间的偏差，并一次性修正文档，不改业务代码。

**核对到的关键事实**

- `git log`：`63153ed fix47`、`d783448 fix48`、`223c8c5 fix49` 均已提交，`master` 与 `origin/master` 同步，工作树干净。
  本文档此前一直写"未提交、未推送"，属过期信息。
- `build/libs/` 仅有 fix49 三件产物；根目录 `build_compile*.log` 已不存在（随 fix47 清理并入库）。
- `libs/` 与 `dependencies.gradle` 已是 290b3 实机版本（AE2 1050、GT 5.09.54.133、NEI 2.8.130、AE2FC 1.5.106、
  MUI2 2.3.88、PH 0.2.0p24、GTNL 0.2.7-pre3-dev-290、NEE 1.7.41、BQ 3.8.84、TE 1.7.60、Avaritia 1.99）。
- `fix42~fix46` 的改动没有独立 commit，被并入 `63153ed fix47`；与"一个修复一个 commit"的约定不符，已如实登记。
- `docs/mcp-full-function-audit-fix41.md` 的 A13/A14/A15 方向已在代码落地（`util/ItemIdentity`），
  但报告状态列未回填、也未实测，本轮**不代为标记为已修复**。

**本次修改文件（全部为文档）**

- `docs/AGENT_CHECKPOINT.md`（本文件）：第一节元数据、第二节基线、第三节新增本条、
  第四节 fix47~fix49 提交状态、第五节待办与实测项、第六节约束第 2/8 条、第七节 v7 现行做法注记、
  第八节版本与文档状态/F22 根因/协作环境、使用说明。
- `README.md`、`README.en.md`：版本说明与依赖对照表。
- `docs/MOD_MAP.md`、`docs/mixin_notes.md`：v7 方案描述与完整 Mixin 清单。
- `docs/GTNH-构建与代码参考.md`、`docs/GTNH-迁移移植指南.md`：占位值与过期基线。
- `CHANGELOG.md`：追加本轮记录。

**本次是否编译通过**：未编译（仅文档，未改任何 `.java`）
**是否部署**：否
**是否提交/推送**：否（改动留在工作区，待用户确认后再决定是否提交）


### 2026-09-19 · v7 材质 8 轮排查定案与万能维护仓首成功

- 工具平台：CloseCode（DoCode Platform）｜底层模型：不记录｜工作分支：main（未提交，工作区脏）
- 会话目标：把用户手工制作的 v7 材质（8 台机器 × 3 面 + 共用底面）接入模组，要求「不加材质包 = 原样，加材质包 = v7」。

**本次完成内容**
- 完成 8 轮代码方案排查，定位根因并固化结论：GT `registerIcon` 对**运行时新造路径恒返回 missingno**。
  - 决定性证据：25 张不同贴图注册后 **UV 完全相同**（`[0.17187744,0.5273462]-[0.1757788,0.53124756]`，宽 0.0039 ≈ 16px@4096）。
  - 换命名空间（ae2_qof → gregtech:blocks/ae2qol）、换注册时机（GT 队列 → TextureStitchEvent.Pre 当前 atlas）**UV 一字不差**。
  - 破案点：用户提示「看看其他材质包怎么搞的」→ 对比 Modernity-GTNH，它**不注册**，只做 GT 已有路径的**同名覆盖**。
- 确认 GT 侧硬约束：`BlockIcons` 1845 个字段（921 OVERLAY + 206 CASING）**全部有主，无预留空位**；覆层**不支持分面**。
- 万能维护仓改用覆层方案（方案 R2），**v7 图案首次成功渲染**（用户确认「正面变了」）。
- 修复上一轮自己引入的 bug：批量脚本误删 8 个机器类的 `getTexture` 分支体（只剩空 if）。
- 修复 `extFacing()` 缺失：对照 GT 自身写法 `builder().addIcon().extFacing().build()` 重写。

**修改/新增文件**
- 新增 `src/main/java/com/wztwzt/ae2_qof/util/ModTextures.java`（`StitchedContainer`；`forceMode` 三态；`firstMissing` 诊断）
- 新增 `src/main/java/com/wztwzt/ae2_qof/client/render/V7TextureStitchHandler.java`
- 删除 `src/main/java/com/wztwzt/ae2_qof/client/render/ClientTextureRegistry.java`（死代码，用户已确认）
- 改 `hatch/AE2MaintenanceHatchUniversal.java`（覆层方案，已跑通）
- 改 `hatch/adaptive/*.java` 5 个 + `hatch/wireless/*.java` 2 个（方案 R 遗留的 front/top/side 调用，**已证无效**）
- 改 `Config.java`（新增 `v7_textures`）、`CommonProxy.java`（postInit 接入 + init 注册）
- 文档：`CHANGELOG.md`（决策记录 5/6 节）、`docs/v7-材质方案结论档案.md`（新建）、`docs/AGENT_CHECKPOINT.md`（本文件）

**遗留问题 / 给下一个智能体的提示**
- ⚠️ **先读 `docs/v7-材质方案结论档案.md`**，否则会重走 8 轮弯路。
- 待办优先级：① 用户实测确认面位 → ② 用户决策方案 A（覆层推到 8 台）/ B（Mixin 注入图集）→ ③ 另 7 台铺设 → ④ 清理无效代码与诊断日志 → ⑤ 文档与版本迭代。
- 关键坑位：构建必须 `JAVA_HOME=E:\java17`；资源包必须 `jar cfM` 打包（禁用 `Compress-Archive`）；1.7.10 资源包**置顶**才生效；部署前先删旧 jar。
- 副作用（用户已确认接受）：装包后 GT 原版维护仓也会变 v7（共用 `OVERLAY_AUTOMAINTENANCE`）。

**本次是否编译通过**：是（`gradlew build -x spotlessJavaCheck -x spotlessCheck`，BUILD SUCCESSFUL）
**是否部署**：是（jar → 实例 `mods/`；`AE2QoL-v7-overlay-test.zip` → 实例 `resourcepacks/`）
**是否提交/推送**：否
> 按时间倒序排列。

### 2026-09-18 · fix43 本轮完成记录

- 用户要求库存检测覆盖板堆叠数量改 64；工具平台 Arena.ai Agent Mode / ShunCode MCP。
- 业务修改只有 `cover/stockmonitor/ItemStockMonitorCover.java` 构造器一行，`setMaxStackSize(1)` → `setMaxStackSize(64)`；保持 NBT/检测/安装/拆卸/绑定逻辑不变。
- 同步 `gradle.properties`、`mcmod.info` 主版本为 fix43，以及根日志、双语 README 和本交接；内置 aeinfinitycell 版本不变。
- Java17 离线 build 成功，发布 JAR 字节码确认设置 64；本轮未跑游戏。详细命令、校验值和待测项见第十一节。
- 保留 fix42 Tooltip 修改（handler SHA256 仍为 `ce661d44e361edb5a1b8ba0bbbf5a42fc850aed1bd20a26732d2d156c856ddef`）及全部既有脏状态；没有自动部署、提交/推送。


### 2026-09-18 · fix42 本轮完成记录

- 工具平台：Arena.ai Agent Mode，经 ShunCode MCP；会话身份按第一节记录，不沿用旧会话的模型声明。
- 用户批准：实施已交付的 Tooltip 单入口方案，同时更新 docs 交接、根日志与 README；未授权扩大到 A01–A19，也未授权部署。
- 业务变更：仅 `src/main/java/com/wztwzt/ae2_qof/client/nei/NetworkTooltipHandler.java`；版本变更 `gradle.properties`、`src/main/resources/mcmod.info`，内置 aeinfinitycell 版本保持不变。
- 文档：`README.md`、`README.en.md` 全面重写，保留 F1–F22 与署名；更新 `CHANGELOG.md`、本文件、`docs/MOD_MAP.md`；新增可复跑的 `docs/tooltip-fix42-regression.sh`。
- 验证：Java 17.0.19 / Gradle 离线构建成功；隔离断言 59 项通过；制品元数据为 fix42，目标字节码 major 52。详见第十节。
- 构建日志：首次 Gradle 为 `BUILD SUCCESSFUL in 56s`，但命令日志管道后取退出码为空，外层 `exit` 返回 2；随后用无管道命令重新验证，**进程退出码 0 / BUILD SUCCESSFUL in 3s**。不把包装命令故障隐藏为一次无异常执行。
- 已知警告：首次编译有 27 条 Mixin mapping 警告，以及弃用/unchecked 提示；RFG 提示未来要求 Java21。本次未顺带改依赖、警告相关业务类或工具链。
- 收尾：历史删除/日志保留，暂存区未写入，HEAD 未变；未部署、未运行游戏、未提交/推送。下一位应先读下方游戏回归清单并取得部署许可。


```Plain Text
[2026-09-18 09:20] | 工具平台：Codex | 底层模型：GPT-5
- 本次完成内容：用户要求修复自动上传的三个隐患，并解释 GTNH-ECO 的配方识别与投递方式。
  一、稳定定位（fix41 核心）：旧实现用 System.identityHashCode 当供应器 ID，该值在区块卸载重载、
  机器拆装、服务器重启后都会变化，导致「选好机器放一会儿再点上传」时服务端按旧 ID 找不到目标、
  静默失败。新增 util/ProviderLocator，改用「维度 + 坐标 + 部件朝向」稳定标识；该标识随
  ProvidersListS2CPacket 下发到客户端，上传时由 UploadPatternPacket 回传；服务端先按坐标匹配，
  失败再回退旧 ID 匹配。策略 1/2/3 三条上传路径全部接入。
  二、失败回执：新增 network/UploadFeedbackPacket（S2C）与三个语言键（upload_target_missing /
  upload_rejected / upload_no_permission），目标丢失、写入被拒、无权限会在聊天栏提示，
  不再只写服务端日志让玩家以为「点了没反应」。
  三、样板槽判断：RequestProvidersListPacket.acceptsPattern 从「只看第一个空槽」改为遍历全部空槽，
  只要有一个空槽接受该样板即判定可接收，修正分类型样板槽的误判；与 UploadPatternPacket 写入条件一致。
- 修改/新增文件：src/main/java/com/wztwzt/ae2_qof/util/ProviderLocator.java（新增）、
  network/UploadFeedbackPacket.java（新增）、network/UploadPatternPacket.java、
  network/ProvidersListS2CPacket.java、network/ModNetwork.java、network/RequestProvidersListPacket.java、
  ClientProxy.java、CommonProxy.java、client/gui/GuiProviderSelect.java、
  assets/ae2_qof/lang/zh_CN.lang、assets/ae2_qof/lang/en_US.lang、CHANGELOG.md、README.md、
  README.en.md、gradle.properties、src/main/resources/mcmod.info、docs/AGENT_CHECKPOINT.md
- 验证：gradlew build --offline 通过，产物 build/libs/AE2-QoL-3.19.0-fix41.jar
- 遗留问题/给下一个智能体的提示：网络包字段有变化（UploadPatternPacket 增加 locationKey、
  ProvidersListS2CPacket 增加 locationKeys），客户端与服务端必须同时升级，不可只换一边。

[2026-09-17 23:40] | 工具平台：Codex | 底层模型：GPT-5
- 本次完成内容：处理用户对样板上传选择界面的反馈（附 ECO 与我们界面的对比截图）。
  用户指出：图1(ECO) 是我们模仿的对象，图2(我们) 排版劣质、且本模组自己的「配方映射」框没有被设计进去。
  定位：fix31 照抄 GTECO 卡片列表时，只安排了列表，把「映射名称」输入框与 添加/删除/刷新/取消
  四个按钮画在同一行（旧 initGui 里 navY+26 一组、navY 一组），互相遮挡，屏幕上表现为输入框被压成黑块。
  修复：重写 GuiProviderSelect 排版为五个纵向独立区域（标题 / 搜索+翻页 / 机器列表 / 上传+取消 / 配方映射区），
  各区域纵坐标独立计算；面板宽 280~400、行数 1~7 随窗口自适应；映射区含「配方 ID」「目标机器名」两个输入框
  与 添加/删除/刷新/用选中机器 四个按钮；新增一键填入选中机器名、按 key 精确删除、取消不改终端搜索框；
  上传记忆 key 与自动上传查询 key 对齐，保证下次真能命中。
- 修改/新增文件：src/main/java/com/wztwzt/ae2_qof/client/gui/GuiProviderSelect.java、
  src/main/resources/assets/ae2_qof/lang/zh_CN.lang、src/main/resources/assets/ae2_qof/lang/en_US.lang、
  CHANGELOG.md、README.md、README.en.md、gradle.properties、src/main/resources/mcmod.info、docs/AGENT_CHECKPOINT.md
- 验证：gradlew build --offline 通过，产物 build/libs/AE2-QoL-3.19.0-fix40.jar
- 遗留问题/给下一个智能体的提示：界面为静态排版复算 + 编译验证，观感需玩家实机确认；
  若仍觉得不好看，建议下一步按「列表加宽 / 映射区折叠」方向微调，而不是重新照抄其它模组。

[2026-09-17 23:05] | 工具平台：Codex | 底层模型：GPT-5
- 本次完成内容：处理用户反馈的 fix38 启动崩溃（crash-2026-09-17_22.53.09-client.txt）。
  定位：崩溃点虽是 Railcraft GeodePopulator，但根因是本轮 fix32 把历史上失效的
  RFB childDelegations 注入「修对」了。该注入在 fix14 中因只判断 List/String[]、
  而真实字段是 HashSet 而从未生效；修复后 net.minecraftforge.* 被委托给子类加载器，
  绕过 RFB 的 ExtensibleEnumTransformer，使 lwjgl3ify 的可扩展枚举改写失效，
  Railcraft 注册 PopulateChunkEvent$Populate$EventType 时抛
  "was not made extensible, add it to lwjgl3ify configs" 导致启动崩溃。
  F22 崩溃真实根因早已查明是 MTE ID 32001 重号（fix30），与 RFB 无关。
  处置：彻底删除该注入代码块，恢复 fix14 的类加载行为；版本号 3.19.0-fix38 → 3.19.0-fix39。
- 修改/新增文件：src/main/java/com/wztwzt/ae2_qof/CommonProxy.java、CHANGELOG.md、
  README.md、README.en.md、gradle.properties、src/main/resources/mcmod.info、
  docs/STATIC_AUDIT_ISSUES.md、docs/AGENT_CHECKPOINT.md
- 遗留问题/给下一个智能体的提示：
  1) 严禁再次加入 RFB childDelegations 注入，原因见「关键机制结论」；
  2) fix38 的其余修复保持不变，本次仅回退该注入；
  3) 部署需用户批准；F15/F10/F5 仍需用户复测反馈。
- 本次是否编译通过：是（gradlew build --offline → BUILD SUCCESSFUL，产物 AE2-QoL-3.19.0-fix39.jar）
```
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

## 十、fix42 历史收尾与验证结果（已填写）

> 后置说明（2026-09-25 补）：本节为 fix42 当时的记录，其中"未提交、未推送"仅描述当时状态；
> 该改动**后来随 fix47 一起并入提交 `63153ed`**，未独立成 commit。本节其余文字保持原貌。

### 10.1 完成检查

- [x] 单入口代码、版本与全局已完成列表已更新。
- [x] Java17 离线构建成功，进程退出码 0（无管道复核命令）。
- [x] 隔离回归 **59 项断言通过**，进程退出码 0。
- [x] 根 CHANGELOG、中英文 README、功能映射、历史操作日志和本节结果已填写。
- [x] 制品版本、SHA256、字节码与实际修改范围已检查。
- [x] 记录未验证项与后续动作；没有自动部署、提交、推送或删除历史文件。

### 10.2 构建和产物

| 项目 | 本轮真实结果 |
|---|---|
| Java | Microsoft OpenJDK 17.0.19，`E:/java17` |
| 命令 | `env JAVA_HOME=/e/java17 GRADLE_USER_HOME=C:/Users/29357/.gradle ./gradlew build --offline -x spotlessJavaCheck -x spotlessCheck` |
| 首次实际编译 | `BUILD SUCCESSFUL in 56s`，16 tasks：8 executed / 8 up-to-date；包装退出码问题见第九节 |
| 无管道复核 | `BUILD SUCCESSFUL in 3s`，16 tasks up-to-date，命令退出码 **0** |
| Checkstyle / Spotless | `checkstyleMain` 执行成功；**Spotless 两项按命令跳过** |
| Gradle test | `compileTestJava` / `test` 为 **NO-SOURCE**，不能称 Gradle 单元测试套件通过 |
| 发布 JAR | `build/libs/AE2-QoL-3.19.0-fix42.jar`，**1,104,409 字节** |
| JAR SHA256 | `75961bacd0e6085dcc78fff7476757c1a5926b41cccb7bebad1f6f37d6850739` |
| 元数据 | `ae2_qof = 3.19.0-fix42`；`aeinfinitycell = 1.0.4-ae2qol`（未改） |
| 目标字节码 | handler class major version **52（JVM 8）** |
| 字节码检查 | `handleTooltip` 只有返回传入列表；`handleItemTooltip` 唯一 List.add；无旧标志/去重静态字段 |
| 日志 | 根目录 `build_tooltip_fix42.log`、`build_tooltip_fix42_verify.log`、`build_tooltip_fix42_regression.log`；保留原四份 build_compile 日志 |
| Git | 基线/结束 HEAD `ffe946ae18b8287731f27863dc9018991a1d8ef1`；无本轮提交或暂存 |

不要部署 `-dev.jar` 或 `-sources.jar`。本次只是产出，未复制 JAR 到实例。

### 10.3 可重复的隔离回归

脚本：[tooltip-fix42-regression.sh](tooltip-fix42-regression.sh)。在项目根目录 Git Bash 执行：

```bash
JAVA_HOME=/e/java17 bash docs/tooltip-fix42-regression.sh
```

它编译**当前真实 handler 和 CountFormatter**，以小型桩替代 MC/Forge/NEI、配置和库存缓存依赖，使用 `javac --release 8`，输出至 `build/tooltip-fix42-regression/`。不添加 Gradle 依赖，不启动游戏，不触及实例。

本轮输出：`PASS: 59 isolated handler assertions (dependency stubs, not game integration)`。

覆盖：通用列表/名称/快捷键透传；库存、仅可合成、二者兼有、二者皆无；相同数量不同物品立即切换、同一物品连续提示；大数与流体文本格式；null/显示关闭/缓存无效的门控；模拟独立列表合并与原生空通用列表路径。

**限制**：59 是断言数，不是 59 个真实游戏场景。流体来自桩 QueryResult，不验证 GT/ae2fc 的实际识别；缓存无效是桩开关，不是等待真实 5 分钟；合并是调用链模型，不加载 Chromatic、Mixin 或 NEI GUI。

静态保留证据：`buildNetworkLine` 与接手版本一致；`NetworkInventoryCache.java` SHA256 为 `f5a000982a2ae9fd3d22a0af59456e531f74e40204d0d8ef7e6692eff6ff576a`，`CountFormatter.java` 为 `589fd0a3649bcf125b58008747b3da50d3c40ddf3fa723133d7a25450c329398`，修改前后相同。

### 10.4 尚未进行的真实游戏验收

以下**全部待测**，不是通过记录：

| 场景 | 预期与观察点 |
|---|---|
| 有库存 / 仅可合成 / 两者皆有 / 两者皆无 | 分别一行数量、一行 Craft、一行组合、不追加；物品标题与其他模组行保留 |
| 数量相同的 A/B 快速切换、同一物品持续悬停 | 每次有效提示仍有一条，不因一秒窗口丢行 |
| GT 流体展示、ae2fc 纯流体、Chromatic 流体上下文 | 识别原有流体与单位；最多一条网络行，含可合成组合 |
| 普通桶/单元与对应流体分别悬停 | 容器仍按物品计数，展示流体按 mB，不混淆 |
| AE2 / ae2fc / 二合一终端，NEI 面板 / 配方 / 书签 | 实际入口均有合理显示，F5 角标不受影响 |
| OV 关闭、重新开启、真实缓存过期 | 关闭/过期不追加，刷新缓存后恢复，不改原有有效期语义 |
| Chromatic Core1.0.29 + Compat1.0.31 + NEI2.8.101 | 目标实例中无双行、无丢行；确认日志加载 fix42 |
| 不带 Chromatic 的原生 NEI 独立测试环境 | 物品名称和单行网络信息正常，不改现有实例依赖来“顺手测试” |
| 单人 / 专用服双端一致版本 | 客户端 Tooltip 回归；本次未新增协议，仍须遵守 fix41 的双端一致要求 |

### 10.5 下一位的最小动作与回退边界

1. 先征得用户批准部署；确认实例路径、双端版本、完整存档/配置备份和旧 JAR 校验值。调查时实例仍为 fix41，不用旧截图证明 fix42。
2. 部署上表唯一发布 JAR，检查实际加载版本，再执行 10.4；把实测环境、步骤、截图/日志与通过/失败逐项回填，不仅写“已测试”。
3. 若失败，优先确认是重复、整行消失、缓存数据还是流体识别问题；不要直接恢复全局时间窗、增加 Mixin 或升级 Compat。
4. 如需回退，只在另行授权后恢复原 JAR；源码精确撤销本轮补丁，保留接手前脏状态，**不要使用整仓 reset/checkout 或恢复已删除文档来清工作区**。
5. A01–A19、旧版 F15/F10/F22 等问题另行审批、逐项处理。本次没有宣称解决它们。

---

## 十一、fix43 收尾与验证结果（已填写）

> 后置说明（2026-09-25 补）：本节记的 fix42/fix43 改动当时确实"未提交"；这些改动**后来随 fix47 一起并入提交 `63153ed`**，
> 并未各自独立成 commit。本节其余文字保持当时的原貌，不作为当前 git 状态依据。

- [x] 用户要求：库存检测覆盖板**物品堆叠上限为 64**；不是把监控阈值改为 64。
- [x] 代码、主版本、双语 README、根 CHANGELOG、完成清单和会话记录同步完成。
- [x] 构建命令：`env JAVA_HOME=/e/java17 GRADLE_USER_HOME=C:/Users/29357/.gradle ./gradlew build --offline -x spotlessJavaCheck -x spotlessCheck`，Java17，**BUILD SUCCESSFUL，退出码 0**。
- [x] 发布产物：`build/libs/AE2-QoL-3.19.0-fix43.jar`；日志：根目录 `build_cover_stack64_fix43.log`。
- [x] SHA256：`be4d6490698d4d4e931f6b93394e1d607e8d6a2d723aacdfebe1efd9c7ac2869`。
- [x] `javap -c` 检查发布 JAR 中覆盖板构造器：传入常量 **64** 设置堆叠上限；`mcmod.info` 主版本 fix43，内置 aeinfinitycell 仍为 `1.0.4-ae2qol`。
- [x] 本轮业务差异仅覆盖板类一行；先前 fix42 handler 内容未变。`git diff --check` 无格式错误，未写暂存区。
- 构建范围：Spotless 显式跳过；Gradle `test` 为 `NO-SOURCE`；既有 Mixin mapping 警告未顺带修复。不把 fix42 的 59 项断言算作本轮堆叠实测。
- **尚未部署和游戏实测**：另行批准后验证无 NBT/相同 NBT 的覆盖板可合至 64、超过 64 分堆、不同配置 NBT 不混堆、安装消耗一片、拆卸保留配置。保持原生 NBT 比较规则，不抹除配置以强行合堆；原有潜行右键清配置作用于手持堆栈，不修改此语义。
- 下一步：取得部署许可，双端使用匹配版本，在测试存档实测以上项目并回填结果；本次未提交/推送。历史文档删除和日志全部保留。

---

### 使用说明

1. 模板原要求放项目根目录（`AGENT_CHECKPOINT 跨智能体接力状态文档.md`）、或另存为 `AGENT_CHECKPOINT.docx`；本文件按项目方最新要求放在 `docs/`。
2. 每次打开智能体干活前，先让 AI 读取本文档，填写「当前会话元数据」。
3. 每次结束工作或切换智能体前，让 AI 更新本文档全部进度内容，并追加操作日志。
4. 下一个智能体启动后自动读取本文档，通过日志和进度无缝承接任务。
