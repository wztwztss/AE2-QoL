# MOD_MAP 代码映射表
> AE2‑QoL‑1.7.10‑GTNH
> 记录功能与源码、Mixin文件的对应关系，方便快速定位代码。
> 路径：项目内部使用**相对根目录路径**；外部参考代码填写完整绝对路径，当前有效参考目录为 `E:\wzt\MC\modcreater\reference_src_290b3`（旧的 `reference_src_290b1_已过期` 已废弃，禁止复制其源码入库）

## AE2 QoL 主逻辑
| 功能简述 | 文件路径 |
|---|---|
| **通配样板的电路/不消耗物品 → PH:编程器电路（3.20.0-fix42，只对 PH 家族）** | `mixin/ph/MixinPatternDualInputHatchWildcard.ae2qol$wrapProgrammingCircuits(concrete, pattern)`：在 PH 家族宿主（22069 / MK.II / 我们的 MK.III 32108）注册展开结果前，把该张具体样板 `in` 列表里的 ① **GT 编程电路**（未本地化名以 `gt.integrated_circuit` 开头）② **样板"不消耗物品"清单里的物品** 换成 `reobf.proghatches.item.ItemProgrammingCircuit.wrap(目标)`；样板自带**电路号**存在但输入里没有电路项时**补一条**，不消耗物品里没进输入列表的也**补一条**；**已经是编程器电路的条目原样保留**（避免二次包装）。转换**先 `copy()` 再改**（保护 `SmartWildcardExpander` 的 LRU 缓存），异常原样返回 + 记 WARN；限额 INFO：`已把具体样板输入里的电路/不消耗物品转换为 PH:编程器电路：电路=… 不消耗=…`。**为什么**：AE 会按样板去取编程器电路（PH 的 `ProgrammingCircuitProvider` 按需生成），送进 PH 家族总成后被 PH 自己移除并把记录的目标写进**该缓冲的虚拟槽** ⇒ **AE 不必备货 GT 电路、总成里不残留电路**。**只对 PH 家族**：GT 2714 / GTNL 21504 无编程器模式，不参与。**我们彻底不再自动写任何机器的电路槽**（3.20.0-fix41 的 `SlotCircuitBaker`/`SmartWildcardCircuit.apply` 调用均已删除） |
| **已废弃方案（3.20.0-fix41，勿再用）** | 「每格独立电路槽 + 9 催化位 / 中键弹窗」整套已在 **3.20.0-fix42 删除**（见 CHANGELOG (46) 与提交 `48863ca`、`033d22a`）：`SlotSettings*`、`SlotSettingsAccess`、`SlotCircuitBaker`、`GuiSlotSettings`、`SlotSettingsPacket/SlotSettingsRequestPacket/SlotSettingsSyncPacket`、`mixin/ph/MixinPatternDualInputHatchSlotSettings`、`mixin/mui/MixinItemSlotWildcardGesture`。**弃用原因**：把电路烧成样板真实输入会让 AE 必须备货 GT 电路、且合成后残留在总成里。设计稿全文保留在 `docs/DESIGN_per-slot-catalyst-circuit.md`（存档） |
| **已废弃方案（3.23.0/3.23.1，勿再用）** | 自研「创造模式 Ctrl+中键复制方块完整 NBT」整套已在 **3.23.2 删除**（`git revert 5f89641 0bcc12d`，-552 行）：`blockcopy/BlockCopyService.java`、`network/BlockCopyRequestPacket.java`、`mixin/mc/MixinItemBlockOnItemUse.java`、`ModNetwork` 的包注册、`client/PickBlockCompatHandler` 的 `ae2qol$tryCopyBlockWithNbt()` 分支。**弃用原因**：① 原版/Forge 1.7.10 **本来就没有**这个功能（`Minecraft.middleClickMouse` 只剩 `ForgeHooks.onPickBlock`，无 Ctrl/NBT 分支；`ItemBlock.placeBlockAt` 无 `BlockEntityTag` 还原），但**本整合包私货 SNL 已自带完整同款**（Ctrl+中键 = 1000 格远程取物 + TE 全部数据，物品带 `(+NBT)` lore，放置写回），重复实现；② Forge `InputEvent.MouseInputEvent` **不可取消**，我们的分支拦不住 SNL 的 `middleClickMouse`，两条链路同时写快捷栏 ⇒ 用户实测「拿到的东西完全不对、箱子里的东西没带过来」。**结论：中键取物（含 NBT）在本整合包归 SNL，不要自研也不要抢注入点。** |
| **根因 L 修复：具体样板必须抹掉原版标记 + 统一解码入口（3.20.0-fix40，关键）** | ① `wildport/crafting/WildcardPatternGenerator.clearWildcardMarker(stack)`（连带 `CompositeWildcardPatternGenerator.clearCompositeWildcardMarker`）：`SmartWildcardExpander.buildConcretePattern` 在剥掉本模组子树后**必须再调用它** —— 克隆样板会连整份 NBT 一起复制，而原版 WildcardPattern 的 `ItemEncodedPatternMixin` 注入 `ItemEncodedPattern.getPatternForItem` HEAD，判据正是 `tag.getBoolean("WildcardPattern")`/`CompositeWildcardPattern`，命中就 `cir.setReturnValue(<轻量预览 details>)`（输出恒为模板的代表输出）⇒ 三族 `注册 details=396` 但 `输出种类=1`、AE 只认铁板。**样板本体不要调用本方法**（Wild 窗口桥接与预览依赖该标记）。② 新增 `wildcard/SmartWildcardDecoder.decode(stack, world)`：**本模组物品直连 `new appeng.helpers.PatternHelper(...)`**（AE2 `getPatternForItem` 内部那条真身路径，不经注入、无缓存、不回写 NBT），其它物品仍走 `ICraftingPatternItem.getPatternForItem`（AE2FC 等各有 details 实现）。四处调用点：`SmartWildcardPatternSlot`（GT）、`SmartWildcardGtnlPatternSlot`（GTNL）、`MixinPatternDualInputHatchWildcard`（PH/MK.III）、`MixinDualityInterface`（AE2 ME 接口）——这条同时救回机器里已存的历史具体样板 |
| **原版模组的三个 NBT 标记键（跨模组互操作须知）** | `WildcardPattern`（单体）、`CompositeWildcardPattern`（复合）、`WildcardGeneratedPatternId`（生成的样板 id）。原版的 `WildcardPatternGenerator.isWildcardPattern(stack)` = `stack.getItem()==其物品 || tag.getBoolean("CompositeWildcardPattern") || tag.getBoolean("WildcardPattern")`；命中者会被它的 `ItemEncodedPatternMixin` 接手 `getPatternForItem`/`getOutput`/tooltip。**因此"具体样板"必须不带这些键**（3.20.0-fix40 已修），只有通配样板本体才该带 |
| **输出前缀 = 存在性驱动（3.20.0-fix38 修正，上游根因）** | `wildcard/SmartWildcardExpander`：新增 `collectOutputPrefixCandidates(templateOut, templateMaterial, out)` —— 从模板每个输出槽的**全部矿辞名**收集前缀候选（以模板材料名结尾者取头：`plateIron`→`plate`；并兜底按首个大写字母切：`plateAnyIron`→`plate`），规则里显式写的输出匹配优先；`doExpand` 逐材料挑**第一个"前缀+材料"在矿辞表真实存在**的（`plateAnyCopper` 不存在则回落 `plateCopper`）。**旧实现** `templateOutputPrefix` 用"材料名判等"，遇到 GT 的 `plateAnyIron`（材料名被解析成 `AnyIron`）必然返回 null ⇒ `outputPrefix` 空 ⇒ 整个输出改写段（`if (outStack != null)`）**从不执行** ⇒ 几百张具体样板全部保留模板输出 ⇒ `输出种类=1`。候选为空时 `logNoOutputPrefixOnce` 打 WARN（可见降级）；`displayOutputPrefix`（窗口输出行）改用同一套候选；`logSampleOnce` 每 JVM 打 3 条 `展开样本：material=… prefix=… in=… out=… 产出out=… concrete@<id>` 自证 |
| **具体样板的输出槽替换判据（3.20.0-fix37 修正）** | `wildcard/SmartWildcardExpander.buildConcretePattern` + 新增 `matchesOutputPrefix(stack, prefix)`：只替换"某个矿辞名以本规则输出前缀开头"的那个输出槽（`plate` 命中 `plateIron`/`plateAnyIron`），只替换第一个命中项，副产物保持模板原样，未命中时限频 WARN（`logOutputSlotMissOnce`）。**旧实现**写的是 `oreInfo(模板输出).material.equals(候选材料)` —— 模板输出的材料名恒为 `Iron` ⇒ **只有铁候选被改写，其余几百张全部沿用模板输出（铁板）** |
| **通配诊断（3.20.0-fix36 起）** | `wildcard/SmartWildcardDiag.java`：`describe(details, n)` 打印**输出种类数 + 前 n 个样本**（含 craftable/priority）；`scheduleAeReadBack(machine, details, family)` 在注册后下一 tick **回读 AE2 合成表**（`ICraftingGrid.getCraftingPatterns()` 条目数、抽样输出是否收录、介质类名；取网格复用"机器自身节点 → 邻接"两级兜底）。三族注册处各调用一次，且**只在限频日志触发时才算**（避免网格重建热路径遍历几百条）。正是这两项把 3.20.0-fix37 的根因一次锁定 |
| **只追加、永不 cancel（3.20.0-fix35，关键约束）** | `mixin/gt/MixinMTEHatchCraftingInputMEWildcard.java`、`mixin/gt/MixinSuperCraftingInputHatchMEWildcard.java`、`mixin/ae/MixinDualityInterface.java`：三处注入**都去掉了 `cancellable`/`ci.cancel()`**，只把展开出的 details 追加进 `craftingTracker` 并逐条写 `patternDetailsPatternSlotMap`，普通槽位一律留给原方法与对方模组。**为什么**：`CallbackInfo.cancel()` 是**共享标志** —— 我们 cancel 掉 `provideCrafting`，同机原版 WildcardPattern 的展开会被一并跳过（用户 A/B/A 实测：3.20.0-fix32 两边都只剩一张 → 3.20.0-fix33 我们没接管时原版恢复 → 3.20.0-fix34 我们又接管后两边又都只剩一张）。代价：原方法会为我们的槽位再登记"模板那一张"（与展开出的同材料那张重复，无害）。另：两个 mixin 的限频哨兵由 `Long.MIN_VALUE` 改 `0L`（旧值会让 `now - last` 溢出成负数 ⇒ 注册日志从未打印） |
| **通配匹配函数（3.20.0-fix34 修正，本模组"从未成功过"的最后一个根因）** | `src/main/java/com/wztwzt/ae2_qof/wildcard/SmartWildcardState.java` 的 `matches(pattern, token)`：旧实现把**整个正则串** `toLowerCase()`，把 `Pattern.quote` 的 `\Q…\E` 压成 `\q…\e` ⇒ 抛 `PatternSyntaxException` 且被 `catch` **静默吞成 false** ⇒ 展开器材料枚举恒空（`no-material-matched` ⇒ 产出 0）、黑名单/白名单/规则级排除**全部失效**。现改为逐字符显式转义元字符 + `Pattern.CASE_INSENSITIVE`，异常记 WARN（不静默）。最小 Java 用例复核：`matches("ingot*","ingotIron")` 修复前 false / 修复后 true |
| **界面行 = 模板行（3.20.0-fix33，下标对齐）** | `wildport/bridge/WildcardBridge.java`：`pushToWild` 遍历**模板输入槽位**（`max(9, templateIn/Out 行数)`），有规则且 `slot==row` 用规则的匹配串、其余行（电路等）产出**占位空行**保住行号；输出行按**同一行自己的模板输出**推前缀（`derivedOutputMatcherForRow` → `plate*`）。`pullFromWild` **用行号当 `rule.slot`**（旧实现用"已收集规则数"，会每保存一次漂移一次）。`ensureNativeTemplate` 重建模板后 `remapRuleSlots` 按"该行之前有 stack 的行数"重排槽位并记 INFO |
| **两套通配模组互不干扰（3.20.0-fix33）** | `mixin/gt/MixinMTEHatchCraftingInputMEWildcard.java` 与 `MixinSuperCraftingInputHatchMEWildcard.java`：**先扫描**，本机没有"我们的已配置通配样板"时**完全不介入**（不注册、不 cancel），交回 GT/GTNL 本体与原版 WildcardPattern 模组（两侧都在同一方法上 HEAD+cancel 完全接管，旧实现无条件 cancel 会把对方压成一张）；`ae2qol$isForeignWildcard` + 限频日志说明"检测到原版样板 ⇒ 不介入"。**反向自愈**：两个 `pushPattern` 守卫在共享映射被第三方 `removeIf` 清掉时，先自查 `ae2qol$wildcards` 的展开结果，命中就补回映射并**放行原方法**（旧实现直接 return false ⇒ 我们的样板被永久拒收） |
| **矿辞前缀的唯一正确来源（3.20.0-fix32）** | `src/main/java/com/wztwzt/ae2_qof/wildcard/SmartWildcardExpander.java` 的 **`public static String oreDictPrefixOf(ItemStack, String material)`**：从真实矿辞名反推前缀（`ingotIron` − `Iron` ⇒ `ingot`）。**为什么必须这样**：`OrePrefixes.getOreprefixKey()` 返回的是**本地化键**（javap 实证其常量池含 `gt.oreprefix.`，值形如 `gt.oreprefix.ingot`），拼成 `gt.oreprefix.ingot*` 后**永不匹配矿辞名** ⇒ 展开产出恒 0。三处调用点统一：`client/SmartWildcardRecipeDeriver`（NEI 加号）、`SmartWildcardExpander.oreInfo`（输出前缀推导）、`wildcard/WildcardEditorPanel`（NEI 拖入）；`getOreprefixKey()` 仅作"明显非本地化键"时的兜底并留 WARN。另有 `orePrefixOfStack(ItemStack)` 供桥按行取输出前缀；推导器有 `NEI 推导规则明细` 日志（#slot 模式:匹配串） |
| **模板自愈（3.20.0-fix32）** | `src/main/java/com/wztwzt/ae2_qof/wildport/bridge/WildcardBridge.java` 的 `ensureNativeTemplate(ItemStack)`：原生 `in`/`out` 缺失时，从 Wild 行数据（`WildcardInputComponents/OutputComponents`，经 `fromPatternSlot→fromStack` 保留的 `Stack`）重建并写回，每张样板一次日志。调用点：`SmartWildcardExpander.expand()`（四个机器入口共用）、`pushToWild`、`pullFromWild`、`MixinDualityInterface`（对**真身**自愈后再复制）。**配套**：`wildport/item/WildcardPatternState.cleanupLegacyPatternSlots` 对我们的样板**不再删** `in`/`out`（原版 mod 物品行为不变） |
| **输出行显示前缀（3.20.0-fix32）** | `SmartWildcardExpander.displayOutputPrefix(stack, state)`（复用展开器自己的 `templateMaterialName` + `templateOutputPrefix`，单一来源）+ `WildcardBridge.derivedOutputMatcher(...)`：规则未指定输出时显示 `plate*`，保证界面与展开行为一致 |
| **机器侧统一判据 + 懒同步（3.20.0-fix31）** | `src/main/java/com/wztwzt/ae2_qof/wildcard/SmartWildcardGate.java`：四个入口共用。判据链＝**物品实例**（`isOurs`，不看 NBT）→ 缺我们 NBT 时**先懒同步**（`WildcardBridge.pullFromWild`，只读 Wild 键）→ `isConfigured()` 至少一条规则；判否**必留限频 WARN**（区分"不是我们的物品"/"没配置"/"懒同步失败"）。**为什么必须独立成类**：3.20.0-fix29 的懒同步写在 `SmartWildcardExpander.expand()` 内部，而 `expand()` 只有判据通过后才会被调用 ⇒ 对"缺 NBT"的样板永远不可达（3.20.0-fix30 实测：`展开前懒同步` 命中 0 次） |
| **GT/GTNL 槽位展开接线（3.20.0-fix31 修复）** | `mixin/gt/MixinMTEHatchCraftingInputMEWildcard.java` 与 `mixin/gt/MixinSuperCraftingInputHatchMEWildcard.java`：包装槽位后**必须调用** `SmartWildcardPatternSlot/GtnlPatternSlot.rebuild(world)`（此前全仓零调用者 ⇒ `expanded` 恒空 ⇒ 注册 0 条）；展开为空时**退回注册模板那一张**（不静默降级为"什么也不注册"） |
| **Wild 窗口两个新页（3.20.0-fix31）** | `wildport/gui/WildcardPatternWindow.java`：撤掉 3.20.0-fix30 的底部电路带（高度回 292）；顶部右侧三个页签（主页/电路/不消耗）；**电路页** 1~24 按 4 列 × 6 行 + 清除（继承）；**不消耗物品页** = NEI 拖入（复用 Wild 自带 `WildcardEntryDropTextField`）+ 加入手持 + 8 行/页（图标 `ItemDrawable(supplier)` + 名称）+ 逐行删 + 分页；两页各自挂 `setEnabled(页号谓词)`；`applyDerivedFromNei(window, derived)` 供 NEI 加号**就地整页替换 9 行并立即写回**；`WindowState.pushOurState(...)` 是唯一写路径（读—改—立刻整包写回，失败就地回滚） |
| 批量样板生成器界面（3.20.0-fix23，整窗搬运 AE2PatternGen） | `src/main/java/com/wztwzt/ae2_qof/apgport/`（65 文件 / 9,983 行，每份文件头保留 MIT 来源声明）：`gui/GuiPatternGen.java`（440，主窗：配方设置/过滤器/排除规则/替换规则/构建缓存/预览数量/生成样板）、`gui/GuiRecipePicker.java`（678，配方选择）、`gui/GuiPatternStorage.java`（274）与 `gui/GuiPatternDetail.java`（114）（存储/详情面板，其导航经 `ApgStubs` 暂未接线并记 WARN）、`gui/GuiComboBox.java`（177）、`gui/ExplicitFilterDropFormatter.java`（199）、`gui/FilterTextFieldWidget.java`（68）、`gui/FilterDragChoiceButtonWidget.java`（20）、`gui/GuiHandler.java`（70；GUI id **101 生成器 / 102 存储**）、`network/NetworkHandler.java`（通道 **`_apg`**，避免与 ModNetwork/WildcardNetwork 重名）、`filter/*`（9 个）、`recipe/GTRecipeSource.java`（363）、`encoder/PatternEncoder.java`（166）、`storage/*`（PatternStorage 436 / RecipeCacheStorage 539 / RecipeCacheService 407 / ModVersionHelper 330）、`config/*`、`util/*`、`command/CommandPatternGen.java`、`ApgStubs.java`（proxy 三处调用的收口） |
| 生成器物品右键入口（3.20.0-fix23） | `src/main/java/com/wztwzt/ae2_qof/generator/ItemSmartPatternGenerator.java`（右键 `openGui(101)` → 上面那套界面；其窗口工厂从**手持物品**读配置）+ `CommonProxy` 中注册其 `GuiHandler` 与 `NetworkHandler.init()` |
| 通配样板**双入口**（3.20.0-fix24） | `src/main/java/com/wztwzt/ae2_qof/wildcard/ItemSmartWildcardPattern.java`：直接右键 → Wild 界面（`wildport`，先 `WildcardBridge.pushToWild` 再 `openGui(GUI_WILDCARD_PATTERN)`）；**Shift+右键** → 我们原有的四页签 MUI2 编辑器（`wildcard/WildcardEditorPanel.java`，含**内置电路 1~24** 与**不消耗物品**页） |
| 加号/写回（3.20.0-fix25 → **3.20.0-fix31 改为就地生效**） | `src/main/java/com/wztwzt/ae2_qof/network/SmartWildcardRulesPacket.java`：**新增目标槽位**（`-1` = 旧行为），服务端优先写"窗口那张样板"、找不到才退回背包搜索（+ WARN）；`mixin/nei/MixinGuiOverlayButton.java` 的 GTNH-MUI 分支只认"当前主窗口就是 Wild 窗口"，命中即调用 `WildcardPatternWindow.applyDerivedFromNei` **就地刷新界面**（不再"关掉重开"），非 Wild 的 GTNH-MUI 窗口不再被误当成通配编辑器 |
| 智能倍增（核心逻辑） | `src/main/java/com/wztwzt/ae2_qof/mixin/ae/MixinCraftingCPUCluster.java` |
| 智能倍增（GT 仓最大轮数） | `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinMTEHatchInputBus.java` |
| 智能倍增（UI 开关） | `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinMTEHatchCraftingInputMEGui.java` |
| 智能倍增（GTNL 超级样板输入总成 GUI 开关，21504/21505） | `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinSuperCraftingInputHatchMEGui.java` |
| 智能倍增（配置） | `src/main/java/com/wztwzt/ae2_qof/Config.java` → `smartDoublingMaxRounds` |
| 智能倍增（C2S 开关包） | `src/main/java/com/wztwzt/ae2_qof/network/SmartDoublingTogglePacket.java` |
| 强化 IO 端口 | `src/main/java/com/wztwzt/ae2_qof/tile/TileExIOPort.java` |
| 强化 IO 端口（Mixin） | `src/main/java/com/wztwzt/ae2_qof/mixin/ae/MixinTileIOPort.java` |
| 库存检测覆盖板（主类） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/StockMonitorCover.java` |
| 库存检测覆盖板（数据类） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/StockMonitorCoverData.java` |
| 库存检测覆盖板（物品） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ItemStockMonitorCover.java` |
| 库存检测覆盖板（阈值模式枚举） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ThresholdMode.java` |
| 库存检测覆盖板（MUI2 GUI） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/gui/StockMonitorCoverGui.java` |
| 库存检测覆盖板（AE连接门面） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ae/AeConnector.java` |
| 库存检测覆盖板（无线通道-全反射） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ae/WirelessAeConnector.java` |
| 库存检测覆盖板（邻接直连通道） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ae/NeighborAeConnector.java` |
| 库存检测覆盖板（库存查询） | `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ae/AeStockReader.java` |
| 库存检测覆盖板（绑定命令） | `src/main/java/com/wztwzt/ae2_qof/CommandAe2QoL.java` → `smbind` |
| 二合一终端（GUI） | `src/main/java/com/wztwzt/ae2_qof/merged/GuiMergedTerminal.java` |
| 二合一终端（容器） | `src/main/java/com/wztwzt/ae2_qof/merged/ContainerMergedTerminal.java` |
| 二合一终端（方块） | `src/main/java/com/wztwzt/ae2_qof/merged/TileMergedTerminal.java` |
| 二合一终端（宿主统一接口，方块/部件/无线三形态） | `src/main/java/com/wztwzt/ae2_qof/api/IMergedTerminalHost.java` |
| 二合一终端面板（线缆部件形态） | `src/main/java/com/wztwzt/ae2_qof/merged/part/PartMergedTerminal.java` + `part/ItemPartMergedTerminal.java` |
| 无线二合一终端（手持形态） | `src/main/java/com/wztwzt/ae2_qof/merged/wireless/ItemWirelessMergedTerminal.java` + `wireless/WirelessMergedGuiObject.java` |
| 三形态 GUI 分发（ID 100 方块 / 110+side 部件 / 120 无线） | `src/main/java/com/wztwzt/ae2_qof/merged/MergedGuiHandler.java` |
| 按钮 tooltip 工具（ITooltip 文字按钮） | `src/main/java/com/wztwzt/ae2_qof/client/gui/TooltipTextButton.java` |
| GuideNH 游戏内指南（Markdown 资源，零代码集成） | `src/main/resources/assets/ae2_qof/guidenh/_zh_cn/*.md` + `_en_us/*.md` |
| ↑ 页面 `icon:`/`item_ids:` 的写法 | 只能写**真实注册名**：`modid:name`；GT 机器写 `gregtech:gt.blockmachines:<MTE ID>`（需覆盖 NBT 时再接 `:{SNBT}`）。机器内部名**不是**物品名 —— 3.20.0-fix2 修过 5 页共 10 个文件的这类错误（见 CHANGELOG 记录 (24)） |
| 二合一终端（样板编码/上传） | `src/main/java/com/wztwzt/ae2_qof/merged/PatternContainer.java` |
| 上传/撤回/交换网络包 | `src/main/java/com/wztwzt/ae2_qof/network/UploadPatternPacket.java` / `RecallPatternPacket.java` / `SwapPatternPacket.java` |
| NEI Tooltip 文字（fix42 单入口） | `src/main/java/com/wztwzt/ae2_qof/client/nei/NetworkTooltipHandler.java` → `handleItemTooltip`；`handleTooltip` 透传；在 `ClientProxy.init` 注册 |
| NEI 叠加层（缓存） | `src/main/java/com/wztwzt/ae2_qof/client/NetworkInventoryCache.java` |
| NEI 叠加层（渲染） | `src/main/java/com/wztwzt/ae2_qof/client/NetworkInventoryDrawHandler.java` |
| 合成通知覆盖层 | `src/main/java/com/wztwzt/ae2_qof/client/render/CraftingNotificationOverlay.java` |
| 合成完成产物展示条（终端第一行 60s） | `src/main/java/com/wztwzt/ae2_qof/client/render/RecentCraftedOverlay.java` + `mixin/nei/MixinGuiMEMonitorable.java`（drawScreen/mouseClicked 注入） |
| 无限水岩浆磁盘 | `src/main/java/com/wztwzt/ae2_qof/item/ItemInfinityWaterLavaCell.java` |
| 无线收发器+连接器 | `src/main/java/com/wztwzt/ae2_qof/wireless/` 整包 |
| F 键搜索填充（F12） | `src/main/java/com/wztwzt/ae2_qof/client/event/KeyInputHandler.java`（按键处理）；`mixin/nei/MixinGuiRecipe.java` 只负责捕获当前 NEI 配方，两者不是同一功能 |
| 石英切割刀复制名称（F11） | `src/main/java/com/wztwzt/ae2_qof/client/event/KnifeNameCopyHandler.java` |
| 上传按钮注入 | `src/main/java/com/wztwzt/ae2_qof/client/event/GuiUploadButtonHandler.java` |
| 合并终端面板事件 | `src/main/java/com/wztwzt/ae2_qof/client/event/MergedTerminalPanelHandler.java` |
| 世界里键取物的客户端触发补丁（fix54） | `src/main/java/com/wztwzt/ae2_qof/client/PickBlockCompatHandler.java`（在 `ClientProxy.init()` 注册）。**为什么需要**：整合包内 `sciencenotleisure` 会在原版 `Minecraft.middleClickMouse()` 的 HEAD 取消它，GTNHLib 的 `PickBlockEvent` 不发出 ⇒ AE2 永不发 `PacketPickBlock` ⇒ `mixin/ae/MixinPacketPickBlock.java` 的兜底永不执行。本类改从 Forge `InputEvent.MouseInputEvent` 取触发点，条件对齐 AE2 `handlePickBlock()`，并预检「身上有无线终端」以免服务端刷 `PickBlockTerminalNotFound`。<br>**3.23.2 起：本类里的 `ae2qol$tryCopyBlockWithNbt()`（创造模式 Ctrl+中键复制方块 NBT）已删除**——该功能整合包 SNL 自带（`ClientUtils.onBeforePickBlock` 的 Ctrl 分支 → `onPickBlockNBTRange` → `TileEntityNBTPacket.apply()` 生成带 `BlockEntityTag` 的物品，放置由 SNL 的 `MixinForgeHook` 写回），我们的分支拦不住 SNL 的 `middleClickMouse` 却会**抢同一个快捷栏格**，导致结果错乱。**保留的是 fix54 的 `ae2qol$sendPickBlockIfApplicable()`**（AE2 世界中键取物补发，独立需求）。 |
| **专用服务器上「物品右键开不了界面」的修复（3.23.2-fix1，关键）** | 两个搬运界面（Wild 通配窗口、批量样板生成器）的 `getServerGuiElement` 原本**也**调用 `WildcardPatternWindow.createWindow` / `CompositeWildcardPatternWindow.createWindow` / `GuiPatternGen.createWindow` / `GuiPatternStorage.createWindow`；这些窗口类里含**客户端专用**代码（fix43 起「改」按钮 = `Minecraft.getMinecraft().displayGuiScreen(new GuiTextInputDialog(...))`），专用服务器上被 Forge `SideTransformer` 拒绝加载（`NoClassDefFoundError: net/minecraft/client/gui/GuiScreen` / `Attempted to load class bdw for invalid side SERVER`）⇒ FML 取不到容器 ⇒ 开窗包不发 ⇒ 客户端「右键完全没反应」（单机是 CLIENT 侧故正常）。**修法**：新增 `src/main/java/com/wztwzt/ae2_qof/merged/ServerSafeModularContainer.java`（服务端专用的**空窗口 + 无缝槽 MUI1 容器**；这两个界面零槽位、不用 MUI1 同步，故槽位集合一致），两个 handler 的 `getServerGuiElement` 改为只返回它，**窗口本体只在 `getClientGuiElement` 里构建**。先例：GT 自带 `gtPlusPlus/core/handler/GuiHandler.getServerGuiElement` 同样只 new 纯容器 |
| 库存统计终端 F22（GT 单方块，ID 32107；fix48 由 32101 让位而来） | `src/main/java/com/wztwzt/ae2_qof/terminal/StockMonitorTerminal.java`（方块/注册）+ `terminal/StockMonitorTerminalGui.java`（MUI2 UI，双端构建与授权见审查 A06–A08）。**3.20.0-fix3 重写**：控件结构双端一致（去掉 `if (isServer)`）、两个变长列表经 `GenericListSyncHandler`+`DynamicSyncedWidget` 按服务端快照渲染成可滚动 `ListWidget`、编辑值全走 SyncValue（权限校验在服务端 setter）、发信器枚举改走 `node.getMachine()`。坑位见 skill 第 21/22 条与 CHANGELOG 记录 (25) |
| 库存统计终端：Nexus 缺失时的回退网络选择面板（3.20.0-fix3 新增） | `src/main/java/com/wztwzt/ae2_qof/terminal/StockMonitorTerminalNetworkPanel.java`（与覆盖板的 `cover/stockmonitor/gui/NetworkSelectPanel` 是兄弟实现；刻意不改覆盖板那份，保持"不触碰已确认可用代码"的边界） |
| 库存统计终端：行内【高亮】【传送】请求包（3.20.0-fix5 新增） | `src/main/java/com/wztwzt/ae2_qof/network/StockMonitorActionPacket.java`（C2S 只发坐标；服务端 tick 线程内重新解析目标 + 会话/`isActiveViewer` + AE BUILD 权限校验后执行；高亮复用 `WirelessHighlightPacket` 200 tick 自动清除，传送复用 `HatchActionPacket` 那份匿名 `Teleporter`） |
| 库存统计终端：覆盖板列表范围（3.20.0-fix6） | 在 `StockMonitorTerminalGui.CoverScan` 内：只列 `CoverRegistry.getByNetwork(终端 networkId)` 的覆盖板；被过滤数量经 `IntSyncValue` S2C，空列表时显示"另有 N 个属于其他网络" |
| 批量样板生成器（3.20.0-fix11） | `generator/SmartPatternGenerator.java`（RecipeMap 枚举与模糊匹配 + 过滤器 + GTRecipe→样板 NBT 编码；**改编自 AE2PatternGen 的 MIT 代码，文件头保留其声明**）+ `generator/ItemSmartPatternGenerator.java`（物品与配方）+ `generator/GeneratorPanel.java`（MUI2 界面）+ `network/SmartPatternGenPacket.java`（C2S 生成请求，服务端扫描并把产物放进背包/脚下） |
| Wild 界面子系统（3.25.x，整窗搬运） | `wildport/` 包 25 个文件 / 7,870 行：`gui/WildcardPatternWindow.java`(2143) 与 `CompositeWildcardPatternWindow.java`(1871) 主窗、`gui/WildcardGuiHandler.java`(GUI handler)、`gui/ContainerWildcardPattern.java`、三个拖入控件(`WildcardEntryDropButton`/`WildcardEntryDropTextField`/`WildcardFilterDropTextField`)、`item/WildcardPatternConfig.java`(415)/`WildcardPatternState.java`(223)/`CompositeWildcardPatternState.java`(277)、`crafting/WildcardPatternEntry.java`(975)/`WildcardPatternGenerator.java`(451)/`CompositeWildcardPatternGenerator.java`(382)/`WildcardPatternDetails.java`/`WildcardPreviewPatternDetails.java`/`CompositeWildcardRecipe.java`、`network/WildcardNetwork.java + 2 个配置包`、`compat/GTCompat.java + NechSearchCompat.java`、`ModItems.java`、`WildportIds.java`。**全部搬运自 WildcardPatternforGTNH（MIT，文件头保留声明）**；物品注册入口不调用 |
| Wild↔我们的 双向桥（3.25.x） | `wildport/bridge/WildcardBridge.java`：`pushToWild`（我们的 9 行规则/总排除/规则级排除 → Wild 的根键，走它的 `fromNbt` NBT 往返）/ `pullFromWild`（Wild 保存后 → 我们的 `ae2qolSmartWildcard` 子树 + `writeAndBumpRevision` + 清展开器缓存）；键零重叠已核实 |
| 通配样板编辑器（3.20.0-fix13+，MUI2 四页签） | `wildcard/WildcardEditorPanel.java`（四页签：规则 / 覆盖预览 / 排除与不消耗物品 / 电路；页签=`setEnabledIf(页号谓词)`；NEI 拖放落点表 `DROP_TARGETS` 与 `DROP_APPEND_TARGETS`；结果说明中文化 `describeZh`/`reasonZh`）+ `client/nei/SmartWildcardNeiDragHandler.java`（`INEIGuiHandler.handleDragNDrop` → `ModularGuiContext.getHovered()` → 写匹配框或记入不消耗物品）+ `wildcard/SmartWildcardExpander.java`（规则级排除 `Rule.excludes`；无通配符的规则按精确匹配处理、不参与材料推导） |
| 智能通配样板（3.20.0-fix10 已交付） | `wildcard/SmartWildcardState.java`（规则/黑白名单/自带电路/自带不消耗物品/修订号，模板留在原生 in/out）+ `wildcard/SmartWildcardExpander.java`（索引期展开：逐候选克隆模板并改写 in/out，带 512 上限 + LRU 缓存 + 超限 WARN）+ `wildcard/ItemSmartWildcardPattern.java`（继承 AE2 原版 `ItemEncodedPattern`，所有总成天然接受）+ `mixin/ae/MixinDualityInterface` 的 `addToCraftingList` 注入 + `/ae2qof wildcard [矿辞前缀]` 自测命令。**M2（NEI 加号推导 + 可视化界面）与 M3（每槽电路）未交付**；调研报告见 `docs/research/wildcardpattern-forensics.md` |
| 智能倍增：单次推送上限配置（3.20.0-fix9） | `Config.smartDoublingPushCap`（键 `smart_doubling_push_cap`，默认 4096）→ 被 `mixin/ae/MixinCraftingCPUCluster` 的功率/推送钳制读取；可在 `settings.json`、`client/gui/GuiConfigScreen`（Mods → AE2 QoL → Config）、`/ae2qof status` 查看与修改 |
| 智能倍增：开关的跨端写入与回读（3.20.0-fix8 重构） | `network/SmartDoublingTogglePacket.java`（C2S：容器 / **坐标设置** / **坐标查询** 三模式；服务端按坐标重定位 MTE 并校验 `ISmartDoublingMedium`）+ `network/SmartDoublingStatePacket.java`（**新增** S2C：服务端权威值写回客户端机器对象，供界面显示）。原因：三处开关 mixin 在 `client` 段，`BooleanSyncValue.allowC2S()` 依赖服务端存在同名同步处理器，专用服务端上会被 MUI2 静默丢弃（详见 CHANGELOG 记录 (29)） |
| 库存统计终端无线端点 | `src/main/java/com/wztwzt/ae2_qof/terminal/StockMonitorTerminalWirelessEndpoint.java` |
| ME 任务检测器 F16（方块） | `src/main/java/com/wztwzt/ae2_qof/tile/TileQuestDetector.java` |
| ME 任务检测器 F16（BQ 检索逻辑） | `src/main/java/com/wztwzt/ae2_qof/quest/QuestDetectLogic.java`（NBT 候选去重用 `util/ItemIdentity`，见审查 A15） |
| 无限存储元件 F17（并入的 aeinfinitycell） | `src/main/java/cn/dancingsnow/aeinfinitycell/` 整包 + `network/InfinityCell*Packet.java`（保存/迁移风险见审查 A01） |
| 自适应电网统计口径 | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/GridEnergyStats.java`（净增/净减语义，见审查 A17） |
| 配方池检测工具 | `src/main/java/com/wztwzt/ae2_qof/util/RecipeMapDetector.java` |
| 终端容器解析工具 | `src/main/java/com/wztwzt/ae2_qof/util/ContainerTerminalResolver.java` |
| 重规划 | `src/main/java/com/wztwzt/ae2_qof/util/Replanner.java` |
| v7 机器贴图工具（**方案 B**：`MixinTextureMap` 在图集 `registerIcons()` 尾部补注册 + 图标回填；`forceMode` 三态开关） | `src/main/java/com/wztwzt/ae2_qof/util/ModTextures.java` — 8 机器面级 `getTexture` 均以其就绪判定门控；`forceMode` 由 `Config.v7Textures`（`v7_textures`）驱动；退化 UV（全 0）回退 GT 机箱 |
| v7 贴图启用判定时机（postInit 钩子） | `src/main/java/com/wztwzt/ae2_qof/CommonProxy.java` → `postInit()` 调 `ModTextures.allowResourceCheck()`；**不可提前到 init**——init 阶段资源可能处于 reload 中间态会误判为可用 |
| 精确物品身份键（NBT 敏感，数量不入键） | `src/main/java/com/wztwzt/ae2_qof/util/ItemIdentity.java` — 用于 `client/NetworkInventoryCache`（审查 A14 的 NBT 变体覆盖）、`quest/QuestDetectLogic` 候选去重（A15）、`network/MergedTerminalScrollReplacePacket` 候选环（A13） |
| 无线 EU 存款助手（仅 deposit；输入侧为镜像余额语义） | `src/main/java/com/wztwzt/ae2_qof/util/WirelessEnergyTransfer.java` — 对应审查报告 A04 的用户决策（保留 3.18.0 镜像实时模型） |
| 万能维护仓（主类） | `src/main/java/com/wztwzt/ae2_qof/hatch/AE2MaintenanceHatchUniversal.java` |
| 万能维护仓（Mixin） | `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinMTEMultiBlockBase.java` |
| 万能维护仓（注册） | `src/main/java/com/wztwzt/ae2_qof/CommonProxy.java` → `init()` — **必须在init阶段注册**，preInit时GT的sPreloadStarted为false会抛IllegalAccessError |
| 万能维护仓（电路板槽校验，fix50） | `src/main/java/com/wztwzt/ae2_qof/hatch/AE2MaintenanceHatchUniversal.java` → `func_94041_b(int,ItemStack)`（`IInventory.isItemValidForSlot` 的 **SRG 名**，因编译用的 GT jar 未反混淆）；只放行 `circuitLevelOf()` 认得的各电压电路板。背景：GT 5.09.54 新接通 `MTEItemStackHandler.isItemValid → MTEHatchMaintenance.func_94041_b → IsAutoMaintenanceInput`，本仓 `aAuto=false` 导致槽位 0 全拒 |
| 自适应电网终端（ID 32106，5-tab PagedWidget UI；fix48 由 32100 让位而来） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetTerminal.java` |
| 自适应电网输入仓（ID 32102） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetHatch.java` |
| 自适应电网激光源仓（ID 32103） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetLaserHatch.java` |
| 自适应电网动力仓（ID 32104） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetDynamoHatch.java` |
| 自适应电网激光靶仓（ID 32105） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetLaserTargetHatch.java` |
| 自适应电网管理器（仓注册/迁移） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetworkManager.java` |
| 自适应电网网络（4类型仓集合） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveNetwork.java` |
| 自适应仓组合Helper（绑定/NBT/电压） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/AdaptiveHatchHelper.java` |
| 仓类型枚举（DYNAMO/ENERGY/LASER_SOURCE/LASER_TARGET） | `src/main/java/com/wztwzt/ae2_qof/hatch/adaptive/HatchType.java` |
| 无线能源输入终端 | `src/main/java/com/wztwzt/ae2_qof/hatch/wireless/WirelessEnergyInputTerminal.java` |
| 无线能源输出终端（ID 32110 / 输入 32111） | `src/main/java/com/wztwzt/ae2_qof/hatch/wireless/WirelessEnergyOutputTerminal.java` |
| 网络数据棒（自适应电网配置读写） | `src/main/java/com/wztwzt/ae2_qof/item/ItemNetworkDataStick.java` |
| 配置与热加载 | `src/main/java/com/wztwzt/ae2_qof/Config.java`（`smartDoublingMaxRounds`、`ioPortRate`、`v7Textures` 等；`ensureFresh()` 已 synchronized，见 P1-031） |
| 命令入口 | `src/main/java/com/wztwzt/ae2_qof/CommandAe2QoL.java`（`/ae2qof reload|status`、覆盖板 `smbind`） |

## Mixin列表

> 与 `src/main/resources/mixins.ae2_qof.json`（及根目录同内容副本）逐条对齐：通用段 13 条、client 段 16 条，共 **29 条**。
> 配置列统一为 `mixins.ae2_qof.json`，下表不再重复填写。

| 段 | Mixin类路径 | 目标类 | 注入点/备注 |
|---|---|---|---|
| 通用 | `mixin/ae/MixinCraftingCPUCluster.java` | `appeng.me.cluster.implementations.CraftingCPUCluster` | `submitJob` RETURN、`completeJob` TAIL、`executeCrafting` HEAD(cancellable)；合成通知 + 智能倍增；`knownBusyMediums` 冷却 |
| 通用 | `mixin/ae/MixinTileIOPort.java` | `appeng.tile.storage.TileIOPort` | `transferContents` HEAD（@ModifyVariable）强化 IO 倍率；**fix51 追加** `tickingRequest` RETURN 注入 `ae2qol$fanOutExtraChannels`——对无限磁盘逐通道补搬。背景：AE2 的 `getInv` 每元件只取**第一个**匹配通道并 `break`，多通道元件因此只搬一个通道。仅放行 `ItemInfinityStorageCell`，单通道元件立即跳过 |
| 通用 | `mixin/ae/MixinDualityInterface.java` | `appeng.helpers.DualityInterface` | `writeToNBT`/`readFromNBT` TAIL；实现 `ISmartDoublingMedium` |
| 通用 | `mixin/ae/MixinContainerInterface.java` | `appeng.container.implementations.ContainerInterface` | `<init>` RETURN；`@GuiSync(30)` 倍增同步字段（避开 AE2 0/1/3~18 与 GTNL 19） |
| 通用 | `mixin/ae/MixinPinsHolder.java` | `appeng.items.contents.PinsHolder` | `getCraftingPinsRows` Redirect；pin 行默认行为 |
| 通用 | `mixin/ae/MixinPacketPickBlock.java` | `appeng.core.sync.packets.PacketPickBlock` | `serverPacketData` HEAD，`remap = false`（**fix52 起不再 cancellable**）；仅「无存量 + 有样板」时接管——判定同步完成，**开界面归队服务端 tick 线程**（`ServerTerminalHelper.scheduleServerTask`），因为 AE2 包处理器运行在网络线程上；其余一律放行原版 |
| 通用 | `mixin/gt/MixinMTEHatchInputBus.java` | `gregtech.common.tileentities.hatches.crafting.MTEHatchInputBus` | `saveNBTData`/`loadNBTData` TAIL；`getMaxMultiplier` 返回配置上限 |
| 通用 | `mixin/gt/MixinMTEMultiBlockBase.java` | `gregtech.api.metatileentity.implementations.MTEMultiBlockBase` | **`@Overwrite shouldCheckMaintenance` 返回 false（全局维护绕过，风险已知）** |
| 通用 | `mixin/gt/MixinCommonBaseMetaTileEntityMultiblockRegistry.java` | `gregtech.api.metatileentity.CommonBaseMetaTileEntity` | `handleFirstTick` TAIL；多方块主机识别注册表（供上传目标识别多方块主机） |
| 通用 | `mixin/gt/MixinProcessingLogicSpeed.java` | `gregtech.api.recipe.ProcessingLogic`（GT 跨配方并行） | threads>1 时接管 `process()`；EU/时长/输出合并的饱和判界 |
| 通用 | `mixin/gt/MixinBaseMetaTileEntityIdMigration.java` | `gregtech.api.metatileentity.BaseMetaTileEntity` | `setInitialValuesAsNBT` HEAD，`remap=false`；**fix48** 旧存档 32100/32101 → 32106/32107（仅当 NBT 带 `ae2qol*` 专属键） |
| 通用 | `mixin/TileDriveMixin.java` | `appeng.tile.storage.TileDrive` | `updateState` RETURN，`remap=false`；Infinity Cell 挂载 cellsMap |
| 通用 | `mixin/client/MixinTextureMap.java` | `net.minecraft.client.renderer.texture.TextureMap` | **每次** `registerIcons()`/`func_110573_f()` 尾部补注册 25 张 v7 路径（仅 `textureType == 0`）；图标回填 `ModTextures.registerBaked` |
| client | `mixin/nei/MixinRecipeHandlerRef.java` | `codechicken.nei.recipe.RecipeHandlerRef` | `fillCraftingGrid`/`craft` HEAD，`remap=false`；捕获配方 handler 名与 GT 配方池 ID |
| client | `mixin/nei/MixinDefaultOverlayHandler.java` | `codechicken.nei.recipe.DefaultOverlayHandler` | `transferRecipe` HEAD(cancellable) + `@Overwrite assignIngredients`；合并终端 NEI 直传 + 书签优先级 |
| client | `mixin/nei/MixinGuiOverlayButton.java` | `codechicken.nei.recipe.GuiOverlayButton` | `updateEnabled` TAIL、`overlayRecipe`/`canFillCraftingGrid` HEAD |
| client | `mixin/nei/MixinGuiMEMonitorable.java` | `appeng.client.gui.implementations.GuiMEMonitorable` | `postUpdate` HEAD、`setPinsRows`/`setAEPins` TAIL；库存缓存 + pin 行自动扩展（精确排除子类） |
| client | `mixin/nei/MixinPanelWidgetDraw.java` | `codechicken.nei.PanelWidget` | `draw` TAIL；左侧面板库存角标 |
| client | `mixin/nei/MixinNEIRecipeWidget.java` | `codechicken.nei.recipe.NEIRecipeWidget` | `draw` TAIL；配方格库存/可合成角标（不追加 Tooltip 文字） |
| client | `mixin/nei/MixinPanelWidgetClick.java` | `codechicken.nei.PanelWidget` | `handleClick` HEAD；Shift+左键取物、中键下单 |
| client | `mixin/nei/MixinGuiRecipe.java` | `codechicken.nei.recipe.GuiRecipe` | `updateScreen` + SRG `func_73876_c` 双注入（fix45 补生产环境挂空）；捕获当前浏览配方 |
| client | `mixin/ae/MixinGuiCraftConfirm.java` | `appeng.client.gui.crafting.GuiCraftConfirm` | 合成提交/产物捕获 |
| client | `mixin/ae/MixinGuiInterface.java` | `appeng.client.gui.implementations.GuiInterface` | `addButtons`/`actionPerformed`/`drawFG` TAIL；智能倍增开关按钮 |
| client | `mixin/ae/MixinGuiSuperInterface.java` | `com.science.gtnl.client.gui.GuiSuperInterface` | 同上；GTNL 可选依赖 |
| client | `mixin/ae/MixinGuiSuperDualInterface.java` | `com.science.gtnl.client.gui.GuiSuperDualInterface` | 同上；方块形态多一个 sidelessMode 按钮 |
| client | `mixin/gt/MixinMTEHatchCraftingInputMEGui.java` | `gregtech.common.gui.modularui.hatch.MTEHatchCraftingInputMEGui` | `<init>` TAIL、`createBottomLeftCornerFlow` RETURN；倍增开关 |
| client | `mixin/gt/MixinSuperCraftingInputHatchMEGui.java` | `com.science.gtnl.common.gui.modularui.SuperCraftingInputHatchMEGui` | 同上；GTNL 21504/21505 |
| client | `mixin/gt/MixinDualInputHatchUI.java` | `reobf.proghatches.gt.metatileentity.DualInputHatch` | `populateUI` RETURN；PH 可选依赖 |
| client | `mixin/GuiContainerAccessor.java` | `net.minecraft.client.gui.inventory.GuiContainer` | 纯 Accessor：`guiLeft`/`guiTop`/`ySize` |

---

## 编程样板输入总成 MK.III / MK.IV（3.20.0 新增 MK.III；**3.24.0 新增 MK.IV** · ProgrammableHatches 可选依赖）

> 目标：把 PH「编程样板输入总成」（`hatch.input.buffered.me`，MTE 22069）扩容克隆成新机器，
> 样板槽 36 → **144（MK.III）** / 36 → **360（MK.IV）**，仅在安装 PH 时存在。**不改动 PH 本体**。
>
> **3.24.0 的两件事**：① 新增 **MK.IV**（360 槽，MTE ID **32109**，配方 = MK.III ×1 + 大师/高级电路；
> MK.III 保持 144 与 32108 不动）；② 修掉**两台机器"样板不进存档"**的既有 bug：
> PH 只在它自己的内部类 `PatternDualInputHatch$Inst` 里读写 `patternSlots`/`multiplier`，
> 而我们的方块实体直接继承 `PatternDualInputHatch`、**不在那条链上** ⇒ 修复前重进世界样板全丢。
> 修法见下面新增的 `PatternSlotPersistence` 行。

| 类别 | 文件路径 | 说明 |
|---|---|---|
| MTE 主体 | `src/main/java/com/wztwzt/ae2_qof/ph/MTEPatternCraftingBufferMKIII.java` | 继承 PH `PatternDualInputHatch`；MTE ID **32108**，内部名 `ae2qof.hatch.input.buffered.me.mkiii`；`page()=2`、`rows()=16`、`rowSize()=9`；覆写 `getStackForm`/`getMachineCraftingIcon`（模板实例 base 为 null 会 NPE）、`loadNBTData`（补齐被 PH 缩回 36 的倍率数组）、`newMetaEntity`（返回自己的 `Inst`）、`createPatternWindow2`（9 列可滚动样板窗） |
| 窗口部件 | `src/main/java/com/wztwzt/ae2_qof/ph/PatternWindowWidgets.java` | PH 三个包私有内部部件（`DragTab`/`PanelDragForwarder`/`NonInteractiveText`）与两个 private 按钮工厂的等价副本（跨包无法复用，只用 MUI2 公开 API） |
| 可选依赖入口 | `src/main/java/com/wztwzt/ae2_qof/ph/PhIntegration.java` | `Loader.isModLoaded("proghatches")` 守卫 → 注册 MTE + 工作台配方 + `InterfaceTerminalRegistry.register(Inst.class)`；`mkiiiStack` 供创造页使用 |
| Mixin | `src/main/java/com/wztwzt/ae2_qof/mixin/ph/MixinPatternDualInputHatchAccess.java` | 接口式 accessor：读写 PH 的 `pattern`/`multiplier`/`patternItemCache`/`patternDetailCache`；`@Invoker` `onPatternChange()` / `refundAll()`（均在公共 `mixins` 列表） |
| 注册调用点 | `src/main/java/com/wztwzt/ae2_qof/CommonProxy.java` → `init` 末尾 | `PhIntegration.register()` |
| 创造页 | `src/main/java/com/wztwzt/ae2_qof/AE2QoLCreativeTab.java` → `displayAllReleventItems` | GT 机器走 `sBlockMachines` meta 值，`setCreativeTab` 管不到，需显式追加 |
| 语言文件 | `assets/ae2_qof/lang/zh_CN.lang` + `en_US.lang` | `gt.blockmachines.ae2qof.hatch.input.buffered.me.mkiii.name/.tooltip/.desc*`（显示名走 GT 的 `getLocalNameKey()`） |
| **MK.IV 主体（3.24.0）** | `src/main/java/com/wztwzt/ae2_qof/ph/MTEPatternCraftingBufferMKIV.java` | **MK.III 的复制品**，只改 `PATTERN_SLOTS = 360`（`TOTAL_ROWS` 自动 40）、`MTE_ID = 32109`、内部名 `ae2qof.hatch.input.buffered.me.mkiv`。**为什么复制而不是继承**：`PATTERN_SLOTS` 是 `public static final int` 且被私有 `ae2qol$ensureSlots()` 直接引用（编译期内联 + 私有方法只认本类常量）⇒ 子类覆盖常量无效 |
| **样板/倍率存读档（3.24.0 修复）** | `src/main/java/com/wztwzt/ae2_qof/ph/PatternSlotPersistence.java` | 键名与语义**完全照 PH**：`patternSlots`（`i0..iN`）+ `multiplier`（最小 1）+ `customName`/`additionalConnection`/`restrictToInt`/`allowopt`/`normalopt`/`saved` + `getProxy().writeToNBT/readFromNBT`。读档按**目标槽位**重建数组 ⇒ 144 旧档能被 360 新机器安全读入。MK.III / MK.IV 的 `loadNBTData`/`saveNBTData` 都调它。**3.24.0-fix2 起：槽位数一致时原地写入（不替换数组对象）**，以便已打开的窗口（MUI2 `ItemStackHandler` 内部是 `Arrays.asList(数组)`）立刻看到新值 |
| **样板同步下发（3.24.0-fix2）** | `src/main/java/com/wztwzt/ae2_qof/ph/PatternClientSync.java` | 剪切/粘贴后把机器的**整份 TE NBT**用原版 `S35PacketUpdateTileEntity` 直推给**正在跟踪该区块**的玩家（`PlayerManager.isPlayerWatchingChunk`）；客户端走 `onDataPacket → readFromNBT`，**与区块重载同一条应用路径**。**根因**：窗里格子读的是客户端 TE 的 `pattern[]`，而 GT 5.09.54.133 无 `issueClientUpdate`、`issueTileUpdate()` 是空实现、不实现 `getDescriptionPacket` ⇒ 服务端改数组没有任何下发通道 |
| **线程引擎（3.25.0）** | `src/main/java/com/wztwzt/ae2_qof/hatch/thread/Ae2qolThreadEngine.java` + `mixin/gt/MixinMTEMultiBlockBase.java` | N 条线程各自进度/并行/耗电/产物，状态放机器之外（引擎，`WeakHashMap`）；拦截 `doCheckRecipe()`（计算，循环调 GT 原始实现，每条线程各自消耗输入）+ `incrementProgressTime()`（推进；完成时用 GT 自己的 `addItemOutputs/addFluidOutputs` 落地，并立刻补下一个配方）；外壳 = 时长最长线程剩余、耗电求和、产物留空；缺电时**降线程不停机**；开关 = 该机器装了 `AE2MaintenanceHatchUniversal` 且线程>1。**边界**：完全自研配方循环、不走 `doCheckRecipe` 的机器（如 PH `IngredientDistributor`）不生效 |
| **线程 UI（3.25.0 提交 1/2）** | `src/main/java/com/wztwzt/ae2_qof/hatch/AE2MaintenanceHatchUniversal.java`（`buildThreadsPage`） | 维护仓界面分「参数 / 线程」两页（右侧标签条）；线程页 = 汇总（活跃 x/N · 总并行 · 总耗电）+ 表头 + 只列活跃线程的 `DynamicSyncedWidget`+`ListWidget` 列表（编号/产物图标/进度条+剩余t/并行/状态）+ 空闲与缺电降级折叠行；数据用 `IntSyncValue/LongSyncValue` + `GenericListSyncHandler`（**PacketBuffer 序列化**）实时同步 |
| **线程 WAILA（3.25.0 提交 2/2；3.25.0-fix4 G5 修签名）** | `network/ThreadStatusPacket.java` + `mixin/gt/MixinBaseMetaTileEntityWaila.java` | WAILA 读的是**客户端**那份 TE，而 GT 无下发入口 ⇒ 自建**降频 S2C 小包**（只发跟踪该区块的玩家，只带汇总 + 剩余最多的 8 条明细与产物名）；客户端按 `dim:x:y:z` 存小表、**2 秒未更新即失效**。注入点在 **`BaseMetaTileEntity.getWailaBody` 的 RETURN**（GT 的 `GregtechTEWailaDataProvider` 正是调它）⇒ 覆盖所有 GT 机器；**不引用 waila 类型**：处理器**零参数** + MixinExtras `@Local(argsOnly = true, index = 1)` 取 tip 列表（早前"只写前两个参数"会被判 `Invalid descriptor` 导致整个混入失效）。**两份 `mixins.ae2_qof.json` 必须同时登记** |
| **线程广播（3.25.0-fix4 G6）** | `hatch/thread/ThreadStatusTicker.java` + `hatch/thread/ThreadStatusBroadcaster.java` | 广播改由**中央 `TickEvent.ServerTickEvent`** 每 10 tick 驱动（`CommonProxy` 注册到 FML 总线），遍历 `Ae2qolThreadEngine.allEngines()` 发送；**原因**：NH-Utilities 等加速机制对普通 GT 多方块走"直接跳 `mProgresstime` + 跳过 `updateEntity`"的快路径，挂在机器 tick 上的广播会稀疏/停发。原 `MixinMTEMultiBlockBase` 里的两处广播调用已移除（避免双倍流量） |
| **维护仓「速度」字段语义（3.25.0-fix7 定性）** | `hatch/AE2MaintenanceHatchUniversal.getEffectiveSpeedBoost()` + `mixin/gt/MixinProcessingLogicSpeed` | 值 = **`1.0 − 速度/100`**，经 `ProcessingLogic L584 .setDurationModifier(...)` 进入 GT，最终 `OverclockCalculator` 里 **`duration = 原时长 × modifier`**。⇒ 它**不是倍率**而是**时长百分比修正**：`0` 原速 / `50` = 2 倍速 / `100` = 最短 / 负数 = 减速；上限按等级 `{0,0,2,5,8,13,18,25,33,41,51,62,73,86,100,100}`。**EU/t 不变**（`durationModifier` 不进 EU 公式）⇒ 加速同时**总耗电等比减少**。用户决定界面保持不动 |
| **P1 并行设定诊断（3.25.0-fix4）** | `mixin/gt/MixinProcessingLogicSpeed.java` | 一次性 `process()` RETURN 注入，打印「维护仓并行设定值 vs 本次实测并行值」：**实测已定性**——蒸馏塔 1000→64000（×64 批处理）、溶解罐 4096→106（被上限压低）、设定=1 时不覆盖（用机器自身并行）⇒ **设定一直生效** |
| **样板剪贴板（3.24.0）** | `src/main/java/com/wztwzt/ae2_qof/item/ItemPatternClipboard.java` | 右键空中切换模式（复制/粘贴/剪切）、潜行+右键空中看状态、右键机器执行；**只在 MK.III/MK.IV 上生效**；**全部逻辑在服务端、不新增网络包**（样板数据不过网，避开单包上限）；剪贴板存玩家 `PlayerPersisted` 的 `ae2qolPatternClipboard`（跨维度、退出重进都在）；剪切 = 先确认写入剪贴板再清空源 |
| **MK.IV / 剪贴板的注册点** | `src/main/java/com/wztwzt/ae2_qof/ph/PhIntegration.java` | 同一 PH 守卫内：MK.IV 构造 + 配方（X = MK.III）+ `InterfaceTerminalRegistry.register(MKIV.Inst.class)` + `mkivStack`；剪贴板物品也在此注册（引用 PH 类型，必须"守卫之后再加载"） |

**外观约定（3.24.0-fix1 登记，非缺陷）**：MK.III 与 MK.IV **都没有专属贴图** —— 两者都继承 PH 基类的
`getTexturesActive/getTexturesInactive`（`BlockIcons.OVERLAY_ME_CRAFTING_INPUT_BUFFER`），因此与 PH 原版机器外观相同，
只能靠**显示名 / tooltip** 区分；要专属外观需新增 16×16 素材（未做）。
「样板剪贴板」物品按名引用 AE2 编码样板贴图 + **紫色染色 `0xC77DFF`**（`getColorFromItemStack`；
通配样板 = 绿 `0x5CE65C`、批量生成器 = 青 `0x5CE6E6`）。

**与既有功能的接口**：样板上传/撤回（`network/UploadPatternPacket`、`RecallPatternPacket`）、
供应器定位（`util/ProviderLocator`）、二合一终端（`merged/ContainerMergedTerminal`）全部按
`IInterfaceViewable.rows()*rowSize()` 取容量 ⇒ 144 槽自动生效，**这些文件本轮未改动**。
