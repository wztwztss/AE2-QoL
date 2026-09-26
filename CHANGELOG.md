## 工作区决策记录 2026-09-26 (23) - **3.20.1**：修 modid 守卫写错导致 MK.III 完全静默（用户报「没找到这个物品」）

> 产物 `build/libs/AE2-QoL-3.20.1.jar`（1152678 字节，SHA256 `D0F0017755C22154604A09581ABA403931E374D8D30DECB91005D9C21CECCFEC`）。
> 3.20.0 功能内容正确但**物品实际从未注册**；本版是修正版，功能与 3.20.0 相同。

### 一、现象与证据链（全部来自实例日志，不猜）

| # | 证据 | 结论 |
|---|---|---|
| 1 | `fml-client-latest.log:16238` → `ae2_qof(AE2 QoL:3.20.0)` | 3.20.0 **确实被加载**了 |
| 2 | `:37656` → `[AE2QoL] GuideNH guide registered`（17:43:40） | 本模组的 `CommonProxy.init` **确实跑过**（不是没进初始化） |
| 3 | 全日志搜不到 `[AE2QoL] PH 编程样板输入总成 MK.III 已注册`，**也搜不到 `注册失败`** | 卡在 `PhIntegration.register()` 的**第一句守卫**：既没注册、也没抛异常 |
| 4 | `:37933` → `[mixin/programmablehatches]: Mixing ph.MixinPatternDualInputHatchAccess from mixins.ae2_qof.json into reobf.proghatches.gt.metatileentity.PatternDualInputHatch` | **accessor mixin 应用成功**，且全日志无任何 mixin 失败 ⇒ 排除 mixin |
| 5 | PH jar 内 `mcmod.info`（`"modid": "programmablehatches"`）+ PH 源码 `MyMod.MODID = "programmablehatches"` + 日志 `:16263` → `programmablehatches(ProgrammableHatches:0.2.0p24)` | 真实 modid 是 **`programmablehatches`**；我写的 `proghatches` 是它的**包名前缀**（`reobf.proghatches.*`，连 coremod 类名都是 `reobf.proghatches.main.asm.FMLPlugin`） |

### 二、根因

`PhIntegration.register()` 用 `Loader.isModLoaded("proghatches")` 做守卫 ⇒ **恒为 false** ⇒ 直接 `return`。
后果：物品不注册、不进 NEI 与创造页，而且**一条日志都不打**——用户看到的只有「找不到这个物品」。

### 三、修复（3.20.1）

1. `PH_MODID = "programmablehatches"`（取对方 `@Mod(modid = MyMod.MODID)` 的真实值）；
2. 守卫升级为**两道判据**：modid 命中 **且** `Class.forName("reobf.proghatches.gt.metatileentity.PatternDualInputHatch", false, ...)`
   能解析——真正决定「功能能不能跑」的是**类是否存在**，这样即使对方 modid 变更也不会误判；
3. 「跳过」分支补 `INFO` 日志：此后「未装 PH」「守卫判错」「注册失败」三种情况在日志里**可区分**；
4. `CommonProxy` 中对应注释同步更正（原注释里也写着错的 modid）。

### 四、教训（已写入 skill 第 18 条）

- **可选依赖判定只能取对方 `@Mod` / `mcmod.info` 里的 modid**，不能从包名、coremod 类名或 jar 文件名猜。
  本例最坑之处是「包名前缀恰好看起来像 modid」，而且全程无报错。
- **守卫的「跳过」分支也必须留日志**：否则「没装依赖」「守卫判错」「注册失败」在日志里无法区分。
- **部署后先看我们自己主动打的那行日志**：本次起无论走哪条分支都会有一行，可直接定性。

### 五、验证

- 构建 `BUILD SUCCESSFUL`（exit 0，无管道取码）；
- 字节码核对 `PhIntegration`：常量池同时含 `programmablehatches` 与 `reobf.proghatches.gt.metatileentity.PatternDualInputHatch`，
  `ae2qol$programmableHatchesPresent()` 依次调用 `Loader.isModLoaded` 与 `Class.forName`；
- 部署：有缺陷的 `【私货】AE2-QoL-3.20.0.jar` 改名 `【私货】AE2-QoL-3.20.0-modid-bug.jar` **移入备份（只移不删）**，
  新 jar `【私货】AE2-QoL-3.20.1.jar` 已就位，SHA256 与本地一致，mods 内仅 1 份。

### 六、实测结果（用户实机 · 2026-09-26）

**✅ 通过**：用户复测结论「**样板确实扩充了没问题**」。

日志证据（实例 `fml-client-latest.log`，修正版启动）：

| 行号 | 日志 | 说明 |
|---|---|---|
| 16238 | `ae2_qof(AE2 QoL:3.20.1)` | 修正版已加载 |
| 37664 | `[mixin/ae2_qof]: Mixing ph.MixinPatternDualInputHatchAccess ... into reobf.proghatches.gt.metatileentity.PatternDualInputHatch` | accessor mixin 应用成功 |
| **37679** | **`[AE2QoL] PH 编程样板输入总成 MK.III 已注册：id=32108，样板槽=144（16 行 × 9 列）`** | **注册成功**——正是修复前完全缺失的那一行 |
| 96133 | `成功将 ... [编程样板输入总成 MK.III] ... 给予 ...` | 物品存在，且**中文显示名生效**（GT `getLocalNameKey()` → `gt.blockmachines.<mName>.name` ✓） |

全日志**没有**任何 `MTEPatternCraftingBufferMKIII` / `PhIntegration` / `PatternWindowWidgets` 的异常或堆栈；
仅有的 `[GuideNH] Couldn't find icon item ae2_qof:...` 是既有问题，与本次无关。

**未逐项复测（如需可补测）**：记录 (22) 清单里的第 4 项「第 100 格倍率存读」、第 7 项「上传/撤回写到第 100 格」、
第 8 项「>36 种样板的订单」、第 9 项「换回原版总成后前 36 格仍在」。这几项共同依赖 `loadNBTData` 的数组补齐逻辑，
本轮未取到证据，按「不写推断」原则**不声明已验证**。

---

## 工作区决策记录 2026-09-26 (22) - **3.20.0**：新增「编程样板输入总成 MK.III」（ProgrammableHatches 可选依赖，144 样板槽）

> 产物 `build/libs/AE2-QoL-3.20.0.jar`（1152357 字节，SHA256 `F85883A8CF09ED073C44415797089396D2B0210A5636965C16E2C202EAA6EFDD`）。
> 本轮是**新增功能**，不是修 bug。PH 未安装时该物品**整体不存在**（不注册、不进创造页）。

### 一、需求（与用户逐项确认）

| 项 | 决定 |
|---|---|
| 样板槽 | **144**（原型 36 的 4 倍）⇒ AE2 接口终端 16 行 × 9 列 |
| 样板窗 | **可滚动网格**：9 列 × 9 可见行，滚动覆盖 16 行 |
| 屏幕基线 | 1920×1080 + GUI 缩放 4（约 480×270 逻辑像素），窗口停靠位置按屏幕裁剪 |
| 输入结构 | 与 **MK.II** 一致：每缓冲 32 物品 + 32 流体（`page()==2`），24 个隔离缓冲 |
| 获取 | 工作台配方 **+** 进 AE2 QoL 创造标签页 |
| 名称 | 编程样板输入总成 MK.III / Programmable Crafting Input Buffer MK.III |
| 存档兼容 | NBT 键与原版完全一致；>36 样板换回原版总成时取不出（用户确认可接受，**不做**迁移工具） |
| MTE ID | 32108（备选 32109，已按 290b3 全表核对：32xxx 仅本模组与 fissionevolved 32100/32101 占用） |
| 版本 | `3.19.0-fix54` → **`3.20.0`** |

### 二、为什么这样改能成（根因级证据）

1. PH 的容量**写死在数组长度里**：`PatternDualInputHatch` 的 `pattern` / `multiplier` / `patternItemCache` /
   `patternDetailCache` 四个字段都是 `new ...[36]`（`javap -c` 两个构造器里各 4 次 `bipush 36`），
   而类内所有功能循环都按 `pattern.length` 走 ⇒ **换数组 = 换容量**，无需触碰 PH 的任何逻辑。
2. 但 4 个字段是**包私有**，跨包子类读不到 ⇒ 新增接口式 accessor mixin
   `mixin/ph/MixinPatternDualInputHatchAccess`（`@Accessor` 读写 4 个字段 + `@Invoker` 两个 private 方法
   `onPatternChange()` / `refundAll()`）。可行性证据：运行时 Mixin（UniMixins 0.3.1）的
   `AccessorInfo$AccessorType` 含 `FIELD_SETTER`；本仓已有同类先例 `mixin/GuiContainerAccessor`。
3. 样板窗由 `protected ModularPanel createPatternWindow2(PanelSyncManager)` 构建，`populateUI` 虚调用它
   ⇒ 子类覆写即可重排网格（PH 原版固定 4 列 × 9 行 = 36 格）。
4. AE2 的 `InterfaceTerminalRegistry` 与 `Grid.getMachines(Class)` 都是**精确类名**查表
   ⇒ 必须把我们的内部类 `Inst.class` 注册进去，否则 AE2 接口终端与**本模组的样板终端**都看不见它。
5. 本模组的样板上传/撤回走 `IInterfaceViewable`（容量 = `rows()*rowSize()`）⇒ 只要乘积 = 144 就自动接管，
   `UploadPatternPacket` / `RecallPatternPacket` / `ProviderLocator` **三处零改动**。
6. 9 列的来历：AE2 `GuiInterfaceTerminal.VIEW_WIDTH = 174`，每行最多 9 格；行数不限
   （条目高度 `rows*18+1` 且逐行做可见性判断，16 行能滚到底）。
7. 编译/运行基线一致：实例 `【私货】programmablehatches-0.2.0p24.jar` 与 `libs/programmablehatches-0.2.0p24.jar`
   SHA256 完全相同（`77470645…`）；MUI2 编译 2.3.88 与实例运行 2.3.91 的
   `ScrollWidget` / `scroll.VerticalScrollData` **同名同包**。

### 三、新增与改动

**新增（4 个类，均在 `com.wztwzt.ae2_qof.ph` / `...mixin.ph`）**

| 文件 | 职责 |
|---|---|
| `ph/MTEPatternCraftingBufferMKIII.java` | 主体：144 槽数组替换、`rows/rowSize`、`newMetaEntity`、`loadNBTData` 补齐、样板窗覆写 |
| `ph/PatternWindowWidgets.java` | PH 三个包私有内部部件的等价副本 + 两个按钮工厂（跨包无法复用） |
| `ph/PhIntegration.java` | 可选依赖入口：`Loader.isModLoaded("proghatches")` 守卫 → 注册 MTE / 配方 / AE2 接口终端注册表 |
| `mixin/ph/MixinPatternDualInputHatchAccess.java` | 接口式 accessor/invoker（换数组 + 两个 private 方法） |

**改动**：`CommonProxy`（init 末尾调用 `PhIntegration.register()`）、`AE2QoLCreativeTab`（追加 MK.III 物品堆）、
`mixins.ae2_qof.json` **两份**（公共列表 +1 ⇒ 通用 14 + client 16 = 30）、中英 lang（名称/工具提示/描述）、
`gradle.properties` 与 `mcmod.info`（版本）。

### 四、关键坑位（本轮踩到或刻意规避）

1. **`loadNBTData` 会把倍率数组缩回 36**：PH 里有 `if (multiplier.length < 36) multiplier = new int[36];`，
   新机器首次读档必然命中 ⇒ 必须在 `super.loadNBTData()` 之后重新补齐 4 个数组，否则样板窗第 37 格起的倍率读写越界。
2. **`pattern` 不能无条件重建**（会丢光已放样板）：只在长度不符时重建 + `System.arraycopy` 搬运。
3. **`newMetaEntity` 必须返回我们自己的 `Inst`**：PH 的实现返回 PH 的 `PatternDualInputHatch.Inst`，
   照抄会让真实方块实体退回 36 槽。
4. **`getStackForm` 必须覆写**：GT 默认实现走 `getBaseMetaTileEntity().getMetaTileID()`，而模板实例在 init 阶段
   base 为 null ⇒ 注册配方时 NPE（与库存统计终端同一个坑）；`getMachineCraftingIcon` 一并覆写。
5. **`ItemDrawable` 的包名是 `com.cleanroommc.modularui.drawable`**，不是 `api.drawable`（首次编译即报此处）。
6. **显示名走 GT 的 `getLocalNameKey()`**：`gt.blockmachines.<mName>.name`，与「万能维护仓」完全同款；
   `getDescription()` 用 `.desc` / `.desc.N` 懒翻译（构造发生在 FML init，那时语言文件可能还没好）。
7. PH 的 `bufferNum` / `dirty` 是包私有：`bufferNum` 从构造参数自己存一份；`dirty` 不需要
   （`refundAll()` 自己会 `markDirty(); dirty = true;`）。
8. PH 的 `DualInputHatch.openGui` 有 `GTGuis.GLOBAL_SWITCH_MUI2 || hasBadge(player)` 前置条件
   ⇒ MK.III 的样板窗与 PH 自己的机器**同条件**生效（本机已满足，否则 PH 原版样板窗也用不了）。

### 五、验证与核对（已完成部分）

- 构建 `BUILD SUCCESSFUL`（exit 0，**无管道取码**）；
- 产物 `AE2-QoL-3.20.0.jar`：`ph/` 下 4 个类 + `MTEPatternCraftingBufferMKIII$Inst` + `$1`（匿名面板）+
  3 个窗口部件类 + `mixin/ph/MixinPatternDualInputHatchAccess.class` **全部入包**；
- **PH / MUI2 / GT 的类没有被打进我们的 jar**（`compileOnly` 生效，已用 `jar tf` 反查确认为空）；
- 包内 `mixins.ae2_qof.json` 含 `ph.MixinPatternDualInputHatchAccess`（解包到**临时目录**核对，未落工作区根）；
- 字节码核对：`PhIntegration.register()` 的首条指令就是 `Loader.isModLoaded("proghatches")`
  （PH 缺失时不会加载任何 PH 类型），`sipush 32108` 说明常量已内联；
  MTE 经 `invokeinterface` 调用 accessor 读写 PH 字段（`getAe2qolPattern`/`setAe2qolMultiplier`/…）。

### 六、待测项（游戏内验收，需用户配合）

1. 启动日志出现 `[AE2QoL] PH 编程样板输入总成 MK.III 已注册：id=32108，样板槽=144（16 行 × 9 列）`，且无 Mixin 报错；
2. NEI / AE2 QoL 创造页能找到该物品（中文名「编程样板输入总成 MK.III」）；
3. 装成多方块仓室 → 点加号打开样板窗：144 格可滚到最后一行，**第 144 格能放样板**；
4. 单独倍率页改第 100 格倍率 → 退档重进仍保留（验证 `loadNBTData` 补齐逻辑）；
5. 批量倍率 ×2 / =1 / ×N / =N 与退款按钮生效；
6. 放 >40 个样板后 ME 接口终端能看到这台机器，16 行条目能滚到底；
7. 本模组样板终端的「上传 / 撤回样板」对新机器生效（例如上传到第 100 格）；
8. 发一份需要 >36 种样板的订单，能正常接单出料；
9. 换回原版总成（22069）：前 36 格样板仍在（多的取不出，符合确认过的取舍）。

---

## 工作区决策记录 2026-09-25 (21) - **正式版 3.19.0-fix54**：三问题全部实测通过，诊断埋点已剥离

> 产物 `build/libs/AE2-QoL-3.19.0-fix54.jar`。fix50 / fix51 / fix52+fix54 全部经用户实机验证通过。

### 一、最终验证结果（用户实机）

| 问题 | 结论 | 证据 |
|---|---|---|
| 万能维护仓电路板槽全拒（fix50） | ✅ 通过 | 用户实测"完全修好了" |
| IO 端口搬不出无限磁盘流体（fix51） | ✅ 通过（转出方向；用户固定用 EMPTY 模式） | 流体被抽干、元件随后正常弹走 |
| 世界里键无存量+有样板不弹下单页（fix52+fix54） | ✅ 通过 | 日志 `branch E`（已排入开界面任务）→ `branch G7`（**界面已打开，样板数=1**）；物品到手后再按中键走 `branch B`（正确放行原版）。用户确认"完美，可以使用了" |

### 二、正式版相对 fix54-diag 的变化（**只删埋点，不动修复逻辑**）

1. `network/ServerTerminalHelper`：删除 `diagLogged` / `diagOnce` / `diagNeeded`，
   改为生产用的 **`warnOnce(branch, detail)`**——只有「本不该发生」的失败分支才记一次 WARN
   （任务排入失败、空参数、终端无 grid、无 craftingGrid、终端槽位非法、界面未打开、
   反射读不到 `pickedBlock`、`AEItemStack.create` 返回空）；
   **正常放行一律不记**（背包已有 / 无终端 / 有存量 / 无样板 / 已在下单界面内）。
2. `mixin/ae/MixinTileIOPort`：删除 IO 端口诊断段（`ae2qol$lastCell`/`ae2qol$lastInv` 字段、
   `getInv` RETURN 与 `shouldMove` HEAD 两个注入、及其两个辅助方法），
   **保留 fix51 的 `ae2qol$fanOutExtraChannels` 补搬逻辑**；同时清理随之失效的 import。
3. `mixin/ae/MixinPacketPickBlock`：A~E 分支日志按上述口径收敛（A/A2 → `warnOnce`，B/C/D/E 删除）。
4. 删除三份诊断专用 Mixin 及其配置条目：
   `mixin/ae/MixinKeyBindHandler`、`mixin/ae/MixinClientHelperPickBlock`、
   `mixin/client/MixinMinecraftPickBlockDiag`。
   `mixins.ae2_qof.json` 回到 **通用 13 + client 16 = 29 条**（两份副本 SHA256 一致）。
5. 版本 `3.19.0-fix54`（`gradle.properties` + `src/main/resources/mcmod.info`）。

### 三、验证与核对

- 构建 `BUILD SUCCESSFUL`（含 checkstyle，故 import 清理完备）；
- 产物内已无诊断 Mixin 类，`client/PickBlockCompatHandler.class`、`MixinPacketPickBlock.class`、
  `MixinTileIOPort.class` 均在；
- 源码中已无 `diagOnce` / `diagNeeded` / `diagLogged` / `[AE2QoL][diag]` 残留；
- 文档同步：README 双语（版本、本版变化段）、`docs/MOD_MAP.md`（新增 fix54 类）、
  `docs/mixin_notes.md`（基线版本）、`docs/AGENT_CHECKPOINT.md`。

### 四、顺带记一条操作教训（已写入 skill）

用 PowerShell 的 `Set-Content -Encoding UTF8` 改仓库文本文件会**加 BOM 并整文件重写**
（README 出现 197/191 行的噪声差异）。**仓库文本改动一律用 edit 工具**；
`gradle.properties` 与 `mcmod.info` 的 BOM 是更早一轮就已提交的（游戏读取正常，暂不清理）。

### 五、交付状态

- **已部署**到 b3 实例：`【私货】AE2-QoL-3.19.0-fix54.jar`，SHA256 `38845A04…`
  （与本地 `build/libs/AE2-QoL-3.19.0-fix54.jar` 逐字节一致），mods 内仅一份；
  旧的 `fix54-diag` 已移入 `_ae2qol_jar_backup`（未删除，可回退）。
- **已推送**到 `origin/master`（`223c8c5..59f1c11`，18 个 commit），工作树与远端同步。

---

## 工作区决策记录 2026-09-25 (20) - fix54：为问题 2 增加**客户端触发补丁**（方案 A，已部署待验证）

> 产物 `build/libs/AE2-QoL-3.19.0-fix54-diag.jar`（SHA256 `666B7AE1…`），已部署到 b3 实例。
> 本版 = fix53-diag 的全部埋点 **+ fix54 的实际修复**（埋点留到验证通过后再剥离）。

### 一、背景（见 (19) 的根因）

整合包内的 `sciencenotleisure` 在原版 `Minecraft.middleClickMouse()`（SRG `func_147112_ai`）的
HEAD 注入并 `ci.cancel()`，导致 GTNHLib 的 `PickBlockEvent` 不会发出、**AE2 永不发送
`PacketPickBlock`** ⇒ `MixinPacketPickBlock`（fix52）永远不执行。

### 二、实现：新增 `client/PickBlockCompatHandler`（客户端，非 Mixin）

- 监听 **Forge 的 `InputEvent.MouseInputEvent`**（FML 总线，与 AE2 自身
  `KeyBindHandler` 同一条总线、同一个事件类型）——**与 SNL、与原版方法完全无关**，
  因此不会受 SNL 的 `ci.cancel()` 影响；
- 中键按下的**边沿检测**（`Mouse.isButtonDown(2)` 状态变化），不依赖事件投递粒度；
- 前置条件逐条对齐 AE2 的 `KeyBindHandler.handlePickBlock()`：
  开着 GUI 不动作、创造模式交给原版、准星必须是方块、空气 / `getPickBlock` 为空都不发；
- **额外增加一条 AE2 没有的前置检查**：`PlayerInventoryUtil.getFirstWirelessTerminal(player) != null`。
  原因：AE2 服务端在没有终端时会 `addChatMessage(PlayerMessages.PickBlockTerminalNotFound)`
  （见 `PacketPickBlock.serverPacketData`），若不预检，没有终端的玩家**每次中键都会被刷一条提示**。
  这里直接复用 AE2 自己的方法，与其服务端口径（含饰品栏）完全一致；
- 通过 `NetworkHandler.instance.sendToServer(new PacketPickBlock(picked))` 补发，
  之后由已有的服务端兜底（fix52 → `ServerTerminalHelper.openCraftAmountIfCraftable`）接管；
- 任何异常只记一次 warn 并放弃本次，绝不影响原版与其它模组。

**为什么不会重复发包**：AE2 客户端只有两条发包路径——① GTNHLib 事件路径（被 SNL 挡死）；
② 鼠标事件路径，但要求"AE2 的 Pick Block 与原版选取方块**不相等**"。本模组推荐并采用的"两键相等"配置下，
②对**方块**永不触发，故方块取物包只可能来自本补丁。

### 三、注册位置

`ClientProxy.init()` 中紧接 `GuideNHIntegration.register()` 之后 `PickBlockCompatHandler.register()`。

### 四、验证状态

构建通过（`BUILD SUCCESSFUL`，checkstyle 通过），产物内含 `PickBlockCompatHandler.class`，
`mcmod.info` 版本为 `3.19.0-fix54-diag`；部署后实例与本地 SHA256 一致、mods 内仅一份。
**待用户一次性验证**：生存模式下对"网络无存量 + 有合成样板"的方块按中键 → 应弹出「要合成多少个」，
并在日志中看到 fix52 的服务端分支行（`branch E` 或 D/C/B）。

---

## 工作区决策记录 2026-09-25 (19) - 问题 2 最终根因：**SNL 在 `middleClickMouse()` HEAD 取消原版取物**，AE2 链路整条不执行

> 纯代码/字节码取证结论（用户已表示不再进游戏测试）。**尚未实施修复。**

### 一、事实链（全部有据）

1. 用户实测：**原版对照组"快捷栏会跳"**；但同一次会话的诊断日志里
   **`CLIENT-EVENT`（GTNHLib PickBlockEvent 到达 AE2）一次都没有**，只有 `CLIENT-BINDS`。
2. 我在客户端链路上的三个埋点**都已正确注入**（`Mixing ae.MixinClientHelperPickBlock … into
   appeng.client.ClientHelper`、`Mixing ae.MixinKeyBindHandler … into appeng.client.KeyBindHandler`），
   且运行时 AE2 字节码确认 `ClientHelper.onPickBlockEvent(com.gtnewhorizon.gtnhlib.event.PickBlockEvent)`
   存在、带 `@SubscribeEvent(priority = LOW)`，`ClientHelper` 也确实 `MinecraftForge.EVENT_BUS.register(this)`。
3. GTNHLib 的钩子本身无条件：`@Mixin(Minecraft)` + `@Inject(method=["func_147112_ai"], at=HEAD)`
   → `if (MinecraftForge.EVENT_BUS.post(new PickBlockEvent())) ci.cancel();`
   （`func_147112_ai` = **`middleClickMouse`**，映射表原文确认）。
4. **凶手**：`sciencenotleisure-0.2.7-pre3` 的
   `com.science.gtnl.mixins.early.minecraft.MixinMinecraft.onBeforePickBlock`：

   ```java
   @Inject(method = "func_147112_ai", at = @At("HEAD"), cancellable = true)   // = middleClickMouse
   private void onBeforePickBlock(CallbackInfo ci) {
       if (ClientUtils.onBeforePickBlock(this.field_71439_g, this.field_71441_e, false)) ci.cancel();
   }
   ```

   而 `ClientUtils.onBeforePickBlock` 的字节码为：

   ```java
   if (tryOpenMultiEssentiaJarBlock(world)) return true;
   if (tryHandlePickBlockHandler(player))  return true;
   boolean alt = Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
   if (!onPickEntity(player, 1000.0D, flag)) {         // 准星没瞄到实体
       if (alt) return onPickBlockNBTRange(...);
       onPickBlockRange(...);                          // SNL 自己的 1000 格远程取物
       return true;                                    // ← 无条件取消原版
   }
   return false;
   ```

   SNL 自带 `com.science.gtnl.common.packet.WirelessPickBlock`，即它**自己实现了一套远程取物**。

### 二、结论

- **中键点方块 ⇒ SNL 取消原版 `middleClickMouse()` ⇒ GTNHLib 的 `PickBlockEvent` 不会发出
  ⇒ AE2 永远不发 `PacketPickBlock` ⇒ 挂在 `PacketPickBlock.serverPacketData` 的 fix47/fix52
  永远不会被执行**（代码本身没错，是**触发源在本整合包里不存在**）。
- 用户看到的"快捷栏跳格"是 **SNL 的**行为，不是原版；21:53 那次 `CLIENT-EVENT` 能出现，
  是因为当时准星大概对着**实体**（SNL 走 `onPickEntity` 分支返回 false、未取消）。
- SNL **没有配置开关**（`MainConfig` 内无取物相关项），无法用配置关掉。

### 三、可选修复方向（待用户决定）

- **A（推荐）**：本模组在**客户端**另取触发点（Forge `InputEvent.MouseInputEvent`，与 SNL/原版方法无关），
  在"世界中 + 非创造 + 中键按下 + 准星为方块 + `getPickBlock` 非空"时**自行发送 `PacketPickBlock`**，
  由已有的服务端兜底逻辑接管。`KeyBindHandler.onMouseInput` 可用性受 SNL 无关；此路径不与 SNL 冲突，
  且因"两键相等"时 AE2 自身不会发方块取物包，**不会重复发送**。
- **B**：报告给整合包作者（SNL 与 AE2 的取物集成冲突）。
- **C**：什么都不做，仅在文档中登记该整合包冲突。

---

## 工作区决策记录 2026-09-25 (18) - fix53-diag 增补**客户端**埋点（仍为诊断版，非发布）

> 产物 `build/libs/AE2-QoL-3.19.0-fix53-diag.jar`（SHA256 `153D02CC…`），已部署到 b3 实例。

### 为什么还要加埋点

上一版埋点**全在服务端**，它只能证明"包没到服务端"，证明不了"客户端卡在哪一步"。
本轮把客户端这一侧的三处关键判定补上：

| 标记 | 位置 | 含义 |
|---|---|---|
| `CLIENT-EVENT` | `appeng.client.ClientHelper.onPickBlockEvent` HEAD | GTNHLib 的 `PickBlockEvent` 路径是否被触发（**只有两键相等时才会走到这里**） |
| `CLIENT-BINDS` | `appeng.client.KeyBindHandler.arePickBlockBindsEqual` RETURN | AE2 的 Pick Block 是否与原版「选取方块」同键 |
| `CLIENT-HANDLE` | `appeng.client.KeyBindHandler.handlePickBlock` RETURN | AE2 是否真的决定发包（true = 已发 `PacketPickBlock`） |

新增两个 Mixin：`mixin/ae/MixinKeyBindHandler`、`mixin/ae/MixinClientHelperPickBlock`，
登记在 `mixins.ae2_qof.json` 的 **client** 段；根副本与 `src/main/resources/` 副本已同步
（SHA256 均为 `90D7D733…`）。仍然**只记录、不改变任何行为**。

### 验证

- 构建 `BUILD SUCCESSFUL`，退出码 0；产物内两个新 Mixin 类与其注入方法已入包，
  jar 内 `mixins.ae2_qof.json` 已登记两者；
- 部署后实例 SHA256 与本地一致，mods 内只有一份 AE2-QoL。

### 顺带记一条操作教训（已写入 skill）

核对产物时我用了 `jar xf <产物.jar> mixins.ae2_qof.json`，**在工作区根目录解包**，
覆盖并随后移走了仓库里的同名根副本 ⇒ 等于删除了 `mixins.ae2_qof.json`。
已从 `src/main/resources/` 恢复并校验两份一致。
**以后核对产物只解到临时目录，或只用 `jar tf` 看清单。**

---

## 工作区决策记录 2026-09-25 (17) - 实测结果回填（第二轮）：fix50 ✅ / fix51 ✅ / fix52 根因查明于客户端键位

> 无代码变更（本轮只回填结论 + 改了一条用户侧配置）。产物仍为 `build/libs/AE2-QoL-3.19.0-fix53-diag.jar`。

### 一、fix50（万能维护仓电路板槽）—— ✅ 通过

用户实测"完全修好了"。`func_94041_b`（SRG 名）覆写路线成立。

### 二、fix51（IO 端口搬不出无限磁盘流体）—— ✅ 通过（转出方向）

诊断版日志给出运行期实证：

```
[21:24:16] [AE2QoL][diag] branch IO-PICK: 端口为该无限磁盘选中的通道 = item
[21:25:33] [AE2QoL][diag] branch IO-MOVE: OperationMode=EMPTY, FullnessMode=EMPTY, didWork=true,
           被选中通道还有内容=true, 其余通道还有内容=true
```

- "端口只取第一个匹配通道 = item"**被实机证实**，与 (14) 的源码推断一致；
- 该 tick 两个通道都还有内容 ⇒ 按 `matches` 的 `EMPTY` 规则此时**不该**搬走元件
  ⇒ 元件留在输入半区 ⇒ RETURN 注入的补搬有条件把其余通道搬完；
- **用户实测结果：流体被抽干，元件随后正常弹到输出半区** ✓ —— 这正是修复后的预期行为
  （**搬空之后**才判"完成"并弹出，而不是一开始就被误判）。

**仍待补测 / 已知边界**：
- `FILL`（转入）方向；以及"普通流体元件对照行为不变"。
- ⚠️ **`FullnessMode=HALF` 下本修复不成立**：`matches()` 在 `HALF` 时**无条件返回 true（总是搬）**，
  元件会在第一次搬运后就被弹到输出半区，流体仍搬不空。本次实测成功的是
  `OperationMode=EMPTY + FullnessMode=EMPTY` 组合（诊断行已记录该组合）。
  **用户决策（2026-09-25）**：该端口固定使用 `EMPTY`，**不需要**为 HALF 追加 `shouldMove` 覆写；
  因此该项仅作为已知边界登记，**不作为待办**。（若将来要用 HALF，再按上一轮修复思路实施。）

### 三、fix52（世界中键下单）—— 根因**不在服务端，而在客户端键位**

诊断版在服务端包处理器埋了 A~G 全分支日志，但整场会话**一条都没出现**
⇒ `PacketPickBlock.serverPacketData` **从未被调用** ⇒ **客户端根本没发包**。

读 AE2 源码 + 用户的 `options.txt` 定位到：

| 路径 | 触发条件 | 用户实际状态 |
|---|---|---|
| 鼠标事件路径 | `isKeyPressed(PICK_BLOCK) && !arePickBlockBindsEqual()` | AE2 键未绑定 ⇒ 永远 false ✗ |
| GTNHLib `PickBlockEvent` 路径 | `arePickBlockBindsEqual()` | `-98 ≠ 0` ⇒ 不接管 ✗ |

- AE2 `ActionKey.PICK_BLOCK` 默认 **`Keyboard.KEY_NONE`（未绑定）**；
- 用户 `options.txt`：`key_key.pickItem:-98`（原版中键）、**`key_key.pick_block.desc:0`**（AE2 未绑定）。

⇒ **两条互补路径同时失效，AE2 的世界中键取物在该环境下完全未激活**；
fix47 / fix52 的服务端实现因此从未被执行——这也是它一直"完全没反应"的真正原因。

**处理（用户侧配置，非代码）**：把 AE2 的 Pick Block 也绑定到中键（`-98`）。
1.7.10 的控件界面无法设置鼠标键，故直接改 `options.txt`：

```
key_key.pick_block.desc:-98      # 原为 0
```

改动前已备份 `options.txt.bak-before-pickblock-key`；与备份比对**仅 1 行不同、行数不变（354）**。

**待用户复测**：① AE2 原生取物是否恢复（对"有存量"的方块中键应从网络到手）；
② 再跑场景 A，日志中应出现 `[AE2QoL][diag] branch X:`。

### 四、本轮顺带查明但**与本模组无关**的两件事

1. **"进不去存档 / 新建世界也不行"的根因是内存，不是模组**：
   实例被设成 `-Xms8192m -Xmx9192m`，在 15.6 GB 机器上叠加 Windows 与后台程序后仅剩 0.79 GB 可用
   → 换页 → 世界载入走不完。此前 19:52/19:56 的两次 `OutOfMemoryError` 同源；
   崩溃报告里 `Memory: 10 MB / 8192 MB up to 8192 MB` 说明"堆满但活跃集极小"，
   属**同一客户端反复载入失败世界累积垃圾**，并非"世界真的需要 8 GB 以上"。
   用户重启电脑后顺利进入世界（本轮日志的 `IO-PICK` 由 `[Server thread]` 打印即为证据）。
2. **21:18:44 的 `StackOverflowError`**：递归环为纯原版
   `Chunk.addTileEntity → TileEntityChest.invalidate → checkForAdjacentChests → World.getBlock
   → 加载相邻区块 → Chunk.addTileEntity …`，即**跨区块边界的箱子**触发的区块加载递归，
   与内存无关、与本模组无关（栈帧中没有本模组）。若复现再单独排查。

---

## 工作区决策记录 2026-09-25 (16) - fix53-diag：实测结果回填 + 诊断构建（仅取证，不发布）

> **非发布版本**。产物 `build/libs/AE2-QoL-3.19.0-fix53-diag.jar`，用途只有一个：把两个仍未修好的问题
> 从「静默失败」变成「日志可判读」。本版**不改变任何行为**。

### 一、实测结果回填（fix50 / fix51 / fix52）

| 问题 | 修复 | 结论 |
|---|---|---|
| 3. 万能维护仓电路板槽 | fix50 | **通过**——用户实测"完全修好了"。SRG 名覆写路线得到验证 |
| 1. IO 端口搬不出无限磁盘流体 | fix51 | **未达成**：元件被端口**自动**从输入半区搬到输出半区；流体只搬了一点点就停；对照的普通流体元件正常 |
| 2. 世界里键无存量+有样板不弹下单页 | fix52 | **未通过**：完全无动静；背包确定没有该物品；终端内该条目数量为 0 |

日志证据（用户测试会话 `fml-client-3.log`，启动于 19:54）：
`ae2_qof(AE2 QoL:3.19.0-fix52)` 已加载；`MixinPacketPickBlock` 与 `MixinTileIOPort` **均注入成功**；
本模组三条新增警告**计数全部为 0** ⇒ 我们的代码**没有抛异常**，是**静默**走掉的。

> 推论（重要）：NEI 面板中键那条路径同样靠 `scheduleServerTask` 归队、且实测可用，
> 因此**若世界中键真的走到了"排任务"那一行，界面本应弹出**。它没弹 ⇒ 命中了更早的**静默分支**。
> 而源码无法区分运行期命中哪一条——这正是本轮要做诊断构建的原因。

### 二、问题 1 的根因（**源码级证据**，fix51 的介入点错了）

读 AE2 rv3-beta-1050 `appeng/tile/storage/TileIOPort.java` 得到：

- IO 端口只有**一个** `cells` 库存，另有 `input[]` / `output[]` 索引数组与
  `INPUT_SLOT_INDEX_*` / `OUTPUT_SLOT_INDEX_*` 常量；GUI 里左右两组 6 格是**同一库存的两个半区**。
- `moveSlot(int x)`（586-597 行）用 `WrapperInventoryRange(this, this.output, true)` 把槽 `x` 的物品
  搬进**输出半区**再清空 —— 即用户看到的「从左边一下跑到右边」。
- 是否搬走由 `shouldMove(...)`（572-584 行）决定，其主体是 `matches(...)`（599-625 行）：
  - `FullnessMode.HALF` → **直接 `return true`（总是搬）**；
  - `FullnessMode.EMPTY` + `OperationMode.EMPTY` → **`getAvailableStacks(src).isEmpty()` 就搬**；
  - 其中 `src` 是 `getInv` 选中的**那一个**通道（多通道元件只取第一个匹配，见 CHANGELOG (14)）。
- 另外 `shouldMove` 在 `inventory == null` 时也**直接返回 true**。

⇒ **根因确认**：我们的无限磁盘只装流体时，端口选中的**物品通道为空**，
`matches` 由此判定"元件已空/已完成" → 立刻 `moveSlot` 搬进输出半区 → 该槽位不再被循环处理
（循环只遍历输入半区），于是**流体只搬了一点点就被打断**。
这也解释了对照实验：普通流体元件只有一个通道，"唯一匹配"恰好就是流体，判定正确、能被抽干。

**结论：fix51 的介入点（在循环结束后补搬）形状错误——判定发生在补搬之前，补搬救不回来。**

### 三、本轮新增的诊断埋点（仅日志，不改判定）

`network/ServerTerminalHelper.java`
- 新增 `diagOnce(branch, detail)`：每个分支只记一次；
- `scheduleServerTask` 原本**完全静默**的 `catch` 改为记录（分支 `F`）；
- `openCraftAmountIfCraftable` 的每个出口加标记：`G0` 参数为空 / `G1` grid 为空 / `G2` craftingGrid 为空 /
  `G3` 样板集合为空 / `G4` 槽位非法 / `G5` openGUI 后容器类型不符 / `G6` 已在下单界面 / `G7` 界面已打开。

`mixin/ae/MixinPacketPickBlock.java`
- 四条静默放行分支加标记：`A` 解析不出被点物品 / `B` 背包已有 / `C` 未解析到可用无线终端 /
  `D` 判定为有存量；另加 `A2`（AEItemStack 创建失败）与 `E`（已排入开界面任务）。

`mixin/ae/MixinTileIOPort.java`
- `getInv` RETURN 记录"端口为该磁盘选中的通道"（`IO-PICK`）；
- `shouldMove` HEAD 记录该次判定（`IO-MOVE`）：`OperationMode`、`FullnessMode`、`didWork`、
  被选通道是否还有内容、**其余通道是否还有内容**；
- 新增两个同口径辅助：`ae2qol$availableStacks`、`ae2qol$otherChannelsHaveContent`。

### 四、验证

- 构建：`JAVA_HOME=E:\java17` + 离线 build → `BUILD SUCCESSFUL`，**无管道复核进程退出码 0**；
- 产物 `AE2-QoL-3.19.0-fix53-diag.jar`（1,131,588 字节）；解包核对：`diagOnce`、
  `ae2qol$diagRememberChannel`、`ae2qol$diagShouldMove`、`ae2qol$availableStacks`、
  `ae2qol$otherChannelsHaveContent` 与两个诊断字段均已入包。

### 五、用法与期望输出

1. 部署本诊断版并**完全重启游戏**（mods 仅启动时加载）；
2. 复现一次「世界里中键无存量+有样板的方块」→ 日志中应出现 `[AE2QoL][diag] branch X:`（X 为 A~G 之一）；
3. 复现一次「无限磁盘接 IO 端口、操作模式设为 EMPTY 抽流体」→ 日志中应出现 `IO-PICK` 与 `IO-MOVE`
   两行，其中 `IO-MOVE` 会直接给出"其余通道还有内容=…"；
4. 把 `fml-client-*.log` 回填后即可定案，随后删除埋点、交正式修复。

### 六、部署教训（本次实际踩到，已写入 skill）

首次部署本诊断版时**游戏仍在运行**，Windows 锁定了 `【私货】AE2-QoL-3.19.0-fix52.jar`，
`Move-Item` 失败而新 jar 已经复制进去 ⇒ **mods 里一度同时存在 fix52 与 fix53-diag 两份同名 mod**
（重复 modId，会导致启动异常）。已立即把新 jar 移出 mods 恢复一致状态
（暂存于 `…\_ae2qol_jar_staging\`），并把「**先确认进程退出，再移旧、再复制、再校验、再确认只剩一份**」
写进 `.dsh/skills/ae2qol-workflow/SKILL.md` 的部署章节。

---

## 工作区决策记录 2026-09-25 (15) - fix52：世界里键下单在网络线程上开界面，静默无效

> 未提交；产物 `build/libs/AE2-QoL-3.19.0-fix52.jar`。

### 现象

生存模式下，身上带着已绑定且在范围内的 ME 无线终端、背包与 ME 网络都没有该物品、
但网络里**确实存在**可合成它的样板时，对着世界里的方块按中键**完全没有反应**，
不弹「要合成多少个」。对照：对**有存量**的方块中键能正常取到手；
对**无存量且无样板**的方块中键与目标情形现象完全相同。

### 排查过程与证据（逐步排除）

| 假设 | 结论 | 依据 |
|---|---|---|
| 注入未生效 | **排除** | 运行日志 `Mixing ae.MixinPacketPickBlock … into … PacketPickBlock`，且注入体标注 `does use it's CallbackInfo` |
| 客户端不发包 | **排除** | AE2 1050 `KeyBindHandler` 只在 `capabilities.isCreativeMode` 时 `return false`；实测为生存模式，`PacketPickBlock` 无条件发出 |
| 原版路径被破坏 | **排除** | 「有存量」时能正常取到手 |
| 背包已有 → 放行 | **排除** | 背包里没有该物品 |
| 样板不在合成网格缓存（`patterns.isEmpty()`） | **排除** | **决定性 A/B**：同一位置、同一终端、同一物品，用 **NEI 左侧物品面板中键能正常弹出**下单页——两条路径共用 `resolveTerminal` + `openCraftAmountIfCraftable`，故样板、终端、网格缓存全部正常 |
| `hasNetworkStock` 误判为有存量 | 可能性低 | 已核对 `PlayerSource` 是纯数据类、`MEMonitorHandler.extractItems(SIMULATE)` 直接委托，空网络按 AE2 契约返回 `null` |

⇒ 排除后只剩**最后一步**：`openCraftAmountIfCraftable` 里
`Platform.openGUI(...)` 之后 `player.openContainer instanceof ContainerCraftAmount` 不成立，
即**界面没有真正打开**。

### 根因

两条路径唯一的**结构性差异是线程**：

- NEI 面板中键 → `network/RequestCraftingPacket.java` 第 53 行显式
  `ServerTerminalHelper.scheduleServerTask(...)`，注释写明「归队到服务端 tick 线程执行，
  避免 Netty IO 线程并发访问 grid/container」——**实测可用**；
- 世界中键 → 注入在 `PacketPickBlock.serverPacketData` 里**同步**执行同一个开界面方法。

AE2 的包处理器走的是 FML `FMLEventChannel` 的 `ServerCustomPacketEvent`
（`NetworkHandler.serverPacket` → `AppEngServerPacketHandler.onPacketData`），
该事件在**网络线程**上触发。在网络线程里替换 `player.openContainer`／打开界面不会生效，
于是整条兜底逻辑静默失败。

fix47 原先的注释写的「与原版同上下文，故不额外归队线程」是**错误推理**：
同上下文只说明不会额外引入并发问题，并不说明网络线程上可以开界面。该注释已一并更正。

### 修复

- `src/main/java/com/wztwzt/ae2_qof/mixin/ae/MixinPacketPickBlock.java`
  - 判定部分（解析被点物品、背包检查、终端解析、存量判定）仍在同步段完成，**保持廉价**；
  - 真正的开界面动作改为 `ServerTerminalHelper.scheduleServerTask(...)`，
    与 NEI 路径完全对齐；
  - **不再 `ci.cancel()`**：目标情形下原版自身必然无操作（网络无存量 → 提取结果为空 →
    落到 `PacketPickBlock.serverPacketData` 的收尾分支直接 `return`），
    因此「不拦截」与「拦截」等价，却不必在网络线程上改变原版流程；
    相应地移除了注入上的 `cancellable = true`（不再需要）。
- `src/main/java/com/wztwzt/ae2_qof/network/ServerTerminalHelper.java`
  - `hasNetworkStock` 的异常兜底由「按有存量返回 `true`」改为「按无存量返回 `false`」并记一条警告。
    原行为的问题：调用方据此**直接放行**，一次判定异常就会让「世界中键下单」**永久静默失效**
    且不留痕迹——这正是本问题此前难以定位的原因（参见审查 P2-027 对静默捕获的要求）。
    改为 `false` 后，调用方会继续走开界面流程，而界面**只在真的存在可用样板时才打开**
    （`openCraftAmountIfCraftable` 内部会再校验样板），最坏结果只是多弹一次无害的合成界面。

### 影响面与风险

| 项 | 结论 |
|---|---|
| 网络有存量时的原版取物 | 不受影响（仍在同步段提前放行） |
| NEI 面板中键、Shift+左键取物 | 不受影响（未改这两条路径） |
| 网络协议 | 未改动，无 fix41 那种双端同版本约束 |
| 行为差异 | 界面延后 1 个服务端 tick 出现；已保留「已在 `ContainerCraftAmount` 时不重复打开」的防连点逻辑 |
| 风险 | 若在服务端线程上开界面仍失败，最坏表现与本次修复前一致（无反应），届时需查 `Platform.openGUI` 在 1050 上的容器契约 |

### 验证（已完成）

- 构建：`$env:JAVA_HOME='E:\java17'` + `.\gradlew.bat build --offline -x spotlessJavaCheck -x spotlessCheck`
  → `BUILD SUCCESSFUL`，**进程退出码 0**。
- 产物：`build/libs/AE2-QoL-3.19.0-fix52.jar`。
- 元数据：`gradle.properties` 与 `src/main/resources/mcmod.info` 主版本均为 `3.19.0-fix52`。

### 待用户实测（未实测前不标通过）

1. 世界里中键「无存量 + 有样板」的方块 → 弹出 AE2 原生「要合成多少个」，**不代玩家点确认**；
2. 有存量 → 原样取物到手；都没有 → 原生无反应（两条均无回归）；
3. 连点中键不重复开界面、不清空已填数量；
4. 日志无 `[AE2QoL] pick-block craft-amount task failed` 与
   `[AE2QoL] hasNetworkStock check failed`。

---

## 工作区决策记录 2026-09-25 (14) - fix51：IO 端口搬不出无限磁盘的流体（AE2 每元件只取一个存储通道）

> 未提交；产物 `build/libs/AE2-QoL-3.19.0-fix51.jar`。

### 现象

无限磁盘（本模组内置的 AE2 Infinity Cell，**物品 + 流体 + 源质多通道**）里存有流体，接进 IO 端口后搬不出去：
**物品能搬、只有流体搬不动**；**强化 IO 端口与 AE2 原生 IO 端口都试过、都失败**；
同一时刻该磁盘的流体在 ME 网络里**可见且数量正确**；**对照**：普通的单通道流体元件放进
**原生** IO 端口，流体**能正常搬**。

### 根因（AE2 rv3-beta-1050 上游代码）

`appeng/tile/storage/TileIOPort.java` 第 488–502 行：

```java
private IMEInventory<?> getInv(final ItemStack is) {
    if (this.currentCell != is) {
        this.currentCell = is;
        this.cachedInventory = null;
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            IMEInventory<?> inventory = AEApi.instance().registries().cell().getCellInventory(is, null, type);
            if (inventory != null) {
                this.cachedInventory = inventory;
                break;                    // ← 只取「第一个匹配的通道」
            }
        }
    }
    return this.cachedInventory;
}
```

调用它的 `tickingRequest(IGridNode,int)`（第 357 行起）在 6 个槽位的循环里（393–428 行）
**对每个元件每 tick 只调用一次 `getInv`、只搬运一个通道**。
再叠加 `AEStackTypeRegistry.getAllTypes()` 返回的是 `registry.values()`（**HashMap 顺序**），
于是无限磁盘只会被选中一个通道（实机表现为物品），**流体通道从不进入搬运循环**。

### 证据链（与全部实测一一对应）

| 观测 | 解释 |
|---|---|
| 流体在 ME 网络可见、数量正确 | 元件注册进电网存储与「IO 端口直接读驱动器元件」是两套路径，前者正常 |
| 普通流体元件在**原生**端口能搬 | 单通道元件的「唯一匹配」恰好就是流体 → 该路径本身支持流体 |
| 无限磁盘物品能搬、流体不能 | 多通道元件只被选中一个通道 |
| 强化端口与原生端口都失败 | 失效点在被两者共用的 `TileIOPort.getInv`；本模组的 `MixinTileIOPort` 原先只放大了 `transferContents` 的 `itemsToMove`，没有改通道选择 |
| `TileExIOPort` 只是 `extends TileIOPort` 加供电渲染 | 强化端口不改变通道语义 |

### 修复

只改一个文件 `src/main/java/com/wztwzt/ae2_qof/mixin/ae/MixinTileIOPort.java`，
新增对 `tickingRequest` 的 `@At("RETURN")` 注入 `ae2qol$fanOutExtraChannels`：

1. 只在端口已上线（`getProxy().isActive()`）时继续——因为 `RETURN` 会在每个 return 前触发，
   包含「未供电 → IDLE」那条，此时取能源/存储会抛 `GridAccessException`；
2. 只处理插入的元件是 `ItemInfinityStorageCell`（本模组无限磁盘）的槽位，**单通道元件立即跳过**；
3. 按**与 `getInv` 完全相同的枚举顺序**取出该元件支持的通道列表，**跳过索引 0**
   （即原版已经处理过的那个通道），只补搬其余通道；
4. 每个剩余通道用 AE2 自己的 `transferContents` 搬运一次，方向沿用端口的
   `OperationMode`（`EMPTY` = 元件 → 网络，`FILL` = 网络 → 元件），预算沿用主循环口径；
5. 取能源/存储与预算计算**懒求值**，未插无限磁盘的普通 IO 端口每 tick 只多做几次廉价判断。

`transferContents` 用**反射**调用：它的返回类型是私有内部类 `TileIOPort$TransferResult`，
`@Shadow` / `@Invoker` 都必须声明完整签名，而源码无法引用那个私有内部类（方案阶段原写的
「用 @Shadow」经实施确认不可行，故改用反射；返回值不需要）。

### 影响面与风险

| 项 | 结论 |
|---|---|
| 普通物品元件 / ae2fc 流体元件 / 其它模组元件 | **完全不受影响**（单通道，走原路径） |
| 原生 IO 端口对其它模组的多通道元件 | 不受影响（闸门只放行本模组无限磁盘，符合用户确认的边界①） |
| 账目守恒 | 补搬仍走 AE2 自身的 `transferContents`（内部 `poweredInsert` / `extractItems` / 失败回注），未新增记账路径 |
| 已知差异 | 补搬不参与原版「搬空后把元件弹到输出口」（`shouldMove`）判定；每个剩余通道按与主循环相同的**初始**预算处理，不递减原循环配额 |
| 热路径日志 | 异常只记一条警告（静态去重），避免每 tick 刷屏 |
| 风险 | 反射靠方法名 + 描述符，写错会**运行期静默不生效**（不崩溃），已用实例 AE2 jar `javap` 核对签名为 `(IEnergySource, IMEInventory, IMEInventory, long)`；解析失败时会记录一条明确警告 |

### 验证（已完成）

- 构建：`$env:JAVA_HOME='E:\java17'` + `.\gradlew.bat build --offline -x spotlessJavaCheck -x spotlessCheck`
  → `BUILD SUCCESSFUL`，**进程退出码 0**（无管道复核）。
- 产物：`build/libs/AE2-QoL-3.19.0-fix51.jar`，1,127,880 字节。
- 解包核对：`MixinTileIOPort` 中 `ae2qol$fanOutExtraChannels`、`ae2qol$tickBudget`、
  `ae2qol$resolveTransferContents` 与两个静态缓存字段均已入包，名称未被重混淆改动。
- 签名依据：对**实例正在运行的** `appliedenergistics2-rv3-beta-1050-GTNH.jar` 执行 `javap`，
  确认 `TileIOPort.transferContents(IEnergySource, IMEInventory, IMEInventory, long)`、
  `IStorageMonitorable.getMEMonitor(IAEStackType)`、`AEStackTypeRegistry.getAllTypes()`、
  `IAEStackType.getAmountPerUnit()`、`OperationMode.EMPTY/FILL`、`Upgrades.SPEED/SUPERSPEED/SUPERLUMINALSPEED` 均存在。

### 待用户实测（未实测前不标通过）

1. 驱动器内**只插**该无限磁盘（同时存有物品与流体），IO 端口另一侧接储存元件；
2. **转出**：流体被搬出；**转入**：流体能被搬回（此前只测过转出，本轮必须双向都测）；
3. 同一批测试中**物品通道仍正常**，`io_port_rate` 倍率语义不变；
4. **对照**：普通流体元件行为与改动前完全一致；
5. 搬运前后两侧总数守恒，无丢物/刷物；日志无 `[AE2QoL] IO port multi-channel fan-out skipped`。

---

## 工作区决策记录 2026-09-25 (13) - fix50：万能维护仓电路板槽因 GT 5.09.54 新增校验链而全拒（回归修复）

> 未提交；产物 `build/libs/AE2-QoL-3.19.0-fix50.jar`。

### 现象

b3 实例上，万能维护仓（MTE 32000）的**电路板槽**放进不去东西：各电压电路板全部被拒；
槽位仍正常显示，**之前放进去的物品仍在槽内**；**新放置的万能维护仓同样放不进**（与存档无关）；
无任何异常日志。同一物品在 b1 实例（GT 5.09.52.594 + 本模组 fix47）上可以放入。

### 根因：GT 5.09.52 → 5.09.54 之间为 MTE 接通了一条新的物品校验链

用 `javap` 对比两版 GT 的字节码（**注意：jar 中的方法名是 SRG 名**，
`IInventory.isItemValidForSlot` 在 GT 产物里叫 `func_94041_b`，第一次比对时用 MCP 名去 grep 是无效的，
下表为修正后的结果）：

| 方法（jar 中的真实名字） | 5.09.52.594 | 5.09.54.133 |
|---|---|---|
| `MTEItemStackHandler.isItemValid(int,ItemStack)` | **不存在**（继承 MUI2 默认 `true`） | **新增** |
| `MTEHatchMaintenance.func_94041_b(int,ItemStack)` | **不存在** | **新增**（`IsAutoMaintenanceInput(stack) && super`） |
| `CommonMetaTileEntity.func_94041_b(int,ItemStack)` | 存在 | 存在 |

5.09.52 时 `MTEItemStackHandler` 只有 `getSlotLimit`，其 `isItemValid` 继承 MUI2 默认的 `true`，
因此 MUI2 槽位控件接受任何物品；5.09.54 起这条链被接通，末端对我们恒为 `false`：

1. 槽位控件：`AE2MaintenanceHatchUniversal.buildUI()` 用
   `new ModularSlot(inventoryHandler, CIRCUIT_SLOT)`（`CIRCUIT_SLOT = 0`）；
2. MUI2：`ItemSlotSH.isItemValid` → `ModularSlot.isItemValid` → `SlotItemHandler.isItemValid`
   → `ItemStackHandler.isItemValid`；
3. GT：`MetaTileEntity` 构造里 `inventoryHandler = new MTEItemStackHandler(mInventory, this)`，
   其 `isItemValid` = `mte.func_94041_b(...) || mte.isItemValidForPhantomSlot(...)`；
4. `MTEHatchMaintenance.func_94041_b`（5.09.54 新增）= `IsAutoMaintenanceInput(stack) && super...`；
   各电压电路板不是自动维护输入 → 前半段即 `false`；
5. `super`（`CommonMetaTileEntity.func_94041_b`）= `getBaseMetaTileEntity().isValidSlot(index)`，
   而 `MTEHatchMaintenance.isValidSlot` = `mAuto && GTMod.proxy.mAMHInteraction`；
   本仓两个构造函数都传 `aAuto = false` → 恒 `false`。

所以是**与物品类型无关的全拒**，与"任意物品都放不进、旧内容仍在、无异常日志"完全吻合。

### 修复

只改一个文件 `src/main/java/com/wztwzt/ae2_qof/hatch/AE2MaintenanceHatchUniversal.java`：

- 把 `getCircuitLevel()` 的物品判据抽成静态 `circuitLevelOf(ItemStack)`，让"槽位校验"与"档位读取"
  共用同一份判据，避免两处漂移；
- 新增 **`func_94041_b(int, ItemStack)`** 覆写（这是 `IInventory.isItemValidForSlot` 的 SRG 名）：
  **只对电路板槽放行本模组认得的各电压电路板**（`CIRCUIT_KEYS` 对应的 dreamcraft 电路物品），
  其余索引与物品一律交回 `super`。

> **方法名为什么是 SRG 名**：本项目编译依赖的 `libs/gregtech-*.jar` 是**未反混淆**的 GT 产物，
> MC 接口成员在其中保留 SRG 名。第一次按参考源码写成 MCP 名 `isItemValidForSlot` 时
> `compileJava` 直接报"找不到符号"（找不到可覆写的方法）——属**显式失败**，不会静默失效。
> 仓库里已有同类先例：`merged/GuiMergedTerminal.java:219` 的 `func_146977_a(Slot)`。

### 实施中纠正的一处判断（如实登记）

方案阶段曾用 MCP 名 `isItemValidForSlot` 去 `javap` 比对两版 GT，得出"5.09.54 新增
`CommonMetaTileEntity.isItemValidForSlot`"——**该结论错误**：jar 里该成员名为 `func_94041_b`，
且两版都有。修正后结论不变（新增的是 `MTEHatchMaintenance.func_94041_b` 与
`MTEItemStackHandler.isItemValid`），但**方法名与归属必须按 jar 字节码为准**，
不能拿 `reference_src` 的源码当作编译基线的真身。

### 为什么这样改能解决根因

被拒绝的位置正是第 4 步那条链的末端方法。在 `CIRCUIT_SLOT` 上返回 `true`，
等价于把该槽恢复到 5.09.52 的可用状态；其余索引仍走 `super`，GT 的自动维护仓语义、
其它槽位、以及管道自动化（本仓 `allowPutStack`/`allowPullStack` 恒 `false`）都不受影响。
该修复**与 MUI2 版本无关**（失效发生在 `isItemValid` 的返回值，不在 MUI2 控件行为）。

### 影响面与风险

- 只影响本模组这一个仓的槽位 0；GT 原版维护仓与其它机器完全不变。
- 代价：本覆写与 GT ≥ 5.09.54 的编译基线绑定（当前基线即 5.09.54.133）。若将来回退 GT 版本，
  会**编译失败**而不是静默失效。
- 若下游 `dreamcraft` 电路物品改名，`circuitLevelOf()` 与档位读取会同时失效（共用判据），
  不会出现"只坏一半"。

### 验证（已完成）

- 构建：`$env:JAVA_HOME='E:\java17'` + `.\gradlew.bat build --offline -x spotlessJavaCheck -x spotlessCheck`
  → `BUILD SUCCESSFUL`；**无管道复核进程退出码 0**（本项目的管道命令退出码不可信，按交接文档要求另跑一次）。
- 产物：`build/libs/AE2-QoL-3.19.0-fix50.jar`，1,124,645 字节。
- 解包核对：产物中 `com.wztwzt.ae2_qof.hatch.AE2MaintenanceHatchUniversal` 确实声明
  `public boolean func_94041_b(int, net.minecraft.item.ItemStack)`，**SRG 名未被重混淆改名**，
  运行期会正确覆盖 `MTEHatchMaintenance.func_94041_b`；同类的静态 `circuitLevelOf(ItemStack)` 亦已入包。
- 元数据：`gradle.properties` 与 `src/main/resources/mcmod.info` 主版本均为 `3.19.0-fix50`（内置 aeinfinitycell 仍为 `1.0.4-ae2qol`）。

### 待用户实测（未实测前不标通过）

1. 各电压电路板逐个放入 → 能放入；取出 → 能取出；
2. 放入后 GUI 里 `max` 值随电压变化（证明档位真的读到了）；
3. 存读档后槽内内容保留；
4. 放入**非**电路板物品 → 仍被拒绝（本次刻意保留的语义）；
5. 其它机器与 GT 原版维护仓无变化，无异常日志。

---

## 工作区决策记录 2026-09-25 (12) - 文档与仓库真实状态对齐（无代码变更）

> 未提交；本轮只改文档，未触碰任何 `.java`、资源或依赖。产物仍为 `build/libs/AE2-QoL-3.19.0-fix49.jar`。

### 为什么做这一轮

fix47 / fix48 / fix49 已经提交并推送（`63153ed` / `d783448` / `223c8c5`，`master` 与 `origin/master` 同步、工作树干净），
但文档层长期停在 fix49 开工前：交接文档写着"未提交、未推送"、起点 `ffe946a`、基线 beta-1，
README 的"本版变化"停在 fix43、依赖对照表还是 beta-1 时代。
下一个接手的智能体若照着这些文档干活，会得到错误的起点与错误的依赖认知。故本轮做一次纯文档对齐。

### 核实到的事实（据此改写文档）

| 项目 | 文档原写法 | 实测 |
|---|---|---|
| git 状态 | 未提交、未推送；HEAD `ffe946a` | `63153ed`/`d783448`/`223c8c5` 已提交并推送，工作树干净 |
| 产物 | `AE2-QoL-3.19.0-fix45/47.jar` 等 | `build/libs/` 只有 fix49 三件（含 `-dev`/`-sources`） |
| 依赖 | AE2 977 / GT 5.09.52.594 / NEI 2.8.19 / MUI2 2.3.73 / NEE 1.7.14 / GTNL pre1 / PH p2.0p8 / BQ 3.8.70 | 已全部升到 b3（AE2 1050 / GT 5.09.54.133 / NEI 2.8.130 / AE2FC 1.5.106 / MUI2 2.3.88 / NEE 1.7.41 / GTNL 0.2.7-pre3 / PH 0.2.0p24 / BQ 3.8.84 / TE 1.7.60 / Avaritia 1.99 / GuideNH 1.3.29） |
| Mixin 数量 | MOD_MAP 列 15 条、mixin_notes 列 22 条 | `mixins.ae2_qof.json` 实为 **29 条** |
| v7 材质 | "方案 P：GT 图标队列" | 实为**方案 B**：`MixinTextureMap` 在 `registerIcons()` 尾部补注册 |
| 历史日志 | 根目录 4 个 `build_compile*.log` 为保留项 | 已随 `63153ed` 清理并入库，不再存在 |

### 如实登记：fix42~fix46 没有独立 commit

fix42（Tooltip 单入口）、fix43（覆盖板堆叠 64）、fix44/fix45（v7 材质方案 B）、fix46（机器透明修复）
的业务改动**没有各自成 commit**，而是与 fix47 一起并入 `63153ed`。
这与项目约定"一个功能/bug 一个 commit、代码与文档与版本号放同一个 commit"不符。
本轮只做记录，**不重写历史、不拆分既有提交**；后续每轮请严格一次修复一个 commit。

### 本轮修改的文档

- `docs/AGENT_CHECKPOINT.md`：会话元数据（改为当前平台/模型、起点 `223c8c5`）、项目基线改 b3/fix49、
  新增本轮完成条目、第四节提交状态、第五节待办与实测项、第六节约束第 2/8 条、
  第七节 v7"现行做法"注记、第八节版本状态/F22 根因/协作环境、第九节本轮日志、第十/十一节后置说明、使用说明。
- `README.md` / `README.en.md`：`本版变化` 由 fix43 改为 fix49（并压缩列出 fix43~fix48 累积变更）；
  依赖对照表全量更新为 b3 版本；验证边界补充"fix44 之后无游戏内验收记录"。
- `docs/MOD_MAP.md`：v7 描述改方案 B；Mixin 表补全为 29 条；新增 `ItemIdentity`、
  `WirelessEnergyTransfer`、F22 终端、F16 任务检测器、F11 石英刀、F17 无限元件等定位行；
  修正 F12 的入口描述（`client/event/KeyInputHandler`，不是 `MixinGuiRecipe`）；外部参考目录改 `reference_src_290b3`。
- `docs/mixin_notes.md`：基线改 b3/rv3-beta-1050；新增"上传取物/材质/GT 注册与迁移"6 条清单；
  已知风险补充 API 变动、MTE 数字 ID 独占、图集"sprite 0×0 = 透明"三条；汇总表对齐 29 条。
- `docs/GTNH-构建与代码参考.md`：第五节"本项目配置"由模板占位值改为真实取值。
- `docs/GTNH-迁移移植指南.md`：Mixin 适配基线改 b3。
- `docs/v7-材质方案结论档案.md`：第十节加注——实例路径与基线已变（b3 实例），其余结论仍有效。

### 未做的事（明确边界）

- 未编译、未运行游戏、未部署、未提交、未推送；未改任何 `.java`/资源/依赖。
- **不改** fix41 全功能审查 A01–A19 的状态列：A13/A14/A15 虽有代码落地（`util/ItemIdentity`），
  但既未回填报告、也未实测，不得据此宣称已修复。
- 历史文档（`docs/mcp-full-function-audit-fix41.md`、`docs/mcp-tooltip-duplicate-investigation.md`、
  `docs/SINGLEPLAYER_TEST_SCRIPT.md`、`docs/v7-材质任务全程记录-2026-09-18.md`）按其记录的时点保留原貌。

---

## 工作区决策记录 2026-09-21 (11) - fix49：依赖全量对齐 GTNH 2.9.0-beta-3 实机版本

> 未提交、未推送；产物 `build/libs/AE2-QoL-3.19.0-fix49.jar`。

### 背景

fix48 已把文档基线切到 290b3，但**编译依赖仍停留在 beta-1 时代**——
此前只是"能跑"，并未真正对齐 b3 的 API。本轮把全部依赖升到 b3 实机版本，
让编译期就能暴露 API 断裂（而非等到游戏里才炸）。

### 依赖升级对照（13 项，12 项有变化）

| 依赖 | 原（b1 时代） | 现（b3 实机） |
|---|---|---|
| AE2 | rv3-beta-977 | **rv3-beta-1050** |
| AE2FluidCraft | 1.5.88-gtnh | **1.5.106-gtnh** |
| NEI | 2.8.19-GTNH | **2.8.130-GTNH** |
| GT 本体 | 5.09.52.594 | **5.09.54.133** |
| BetterQuesting | 3.8.70-GTNH | **3.8.84-GTNH** |
| ThaumicEnergistics | 1.7.53-GTNH | **1.7.60-GTNH** |
| Avaritia | 1.97 | **1.99** |
| GuideNH | 1.3.5 | **1.3.29** |
| NotEnoughEnergistics | 1.7.14 | **1.7.41** |
| GTNotLeisure | 0.2.7-pre1 | **0.2.7-pre3** |
| ModularUI2 | 2.3.73 | **2.3.88** |
| ProgrammableHatches | 0.2.0p8 | **0.2.0p24** |
| StructureLib | 1.4.42 | 1.4.42（本就一致） |

本地 jar 依赖已放入 `libs/` 并更新 `dependencies.gradle`；
Maven 依赖直接用缓存中的 b3 版本坐标。

### 升级后暴露并修复的 API 断裂（6 处，4 个文件）

这正是本轮升级的价值——这些断裂在旧依赖下是隐性的。

1. **`RenderBlockExIOPort` / `RenderBlockQuestDetector`**
   AE2 1050 把 `BlockIOPort.getRenderer()` 的返回类型由 `BaseBlockRender<?,?>` 收窄为
   具体的 `RenderIOPort`，原继承 `BaseBlockRender` 的写法不再合法。
   改为继承 `RenderIOPort`，但**渲染仍走 `BaseBlockRender` 通用路径**以保持既有外观。
   注意 `TileQuestDetector` 继承 `AENetworkTile` 而非 `TileIOPort`，
   不能套用 `RenderIOPort` 的 IO 端口专用逻辑（其内部会取 `TileIOPort.getUp()`）。

2. **`ClientProxy.applyClientSwap`**
   AE2 1050 移除了 `ContainerPatternTerm.outputSlotsClient` 数组字段，
   改由 `outputsSync`（`AEStackInventorySyncHandler`）统一同步。
   现改为：仍通过反射读取私有 `outputs` 拿到底层 `IAEStackInventory`，
   然后直接用 `putAEStackInSlot` 写入，槽位长度取自 `getSizeInventory()`。

3. **`ContainerMergedTerminal`（2 处）**
   AE2 1050 把 `IInterfaceViewable.getNameSuffix()` 的返回类型由 `String` 改为
   `IChatComponent`（为在客户端按本地语言翻译后缀）。
   新增私有 `serializeSuffix(IChatComponent)`，与 AE2 `ContainerInterfaceTerminal.serializeSuffix`
   行为一致（null 保持 null），序列化后再交给签名未变的
   `PacketInterfaceTerminalUpdate.addRenamedEntry/setSuffix`。

### 验证

- `gradlew compileJava` 与 `build`（Java 17、offline）均 `BUILD SUCCESSFUL`；
- `gradlew dependencies` 确认实际解析到 rv3-beta-1050 / 5.09.54.133 / 2.8.130 / 1.5.106 / 2.3.88 等 b3 版本；
- 与升级前成功构建日志比对，`Unable to locate obfuscation mapping` 警告数量一致（27 条），
  属于既有现象，非本轮引入。

---

## 工作区决策记录 2026-09-20 (10) - fix48：MTE ID 让位 fissionevolved + 旧存档自动迁移 + 适配 290b3

> 未提交、未推送；产物 `build/libs/AE2-QoL-3.19.0-fix48.jar`。
> 基线由 GTNH `2.9.0-beta-1` 切换为 **`2.9.0-beta-3`**。

### 问题（b3 日志实据）

```
[AE2QoL] StockMonitorTerminal registration FAILED: MetaTileEntity id 32101 is already occupied!
         Existing MTE is fissionevolved.colossal_fission_reactor_controller(com.shenfnx.fissionevolved.MTEColossalFissionReactor).
[AE2QoL] AdaptiveNetTerminal registration FAILED: MetaTileEntity id 32100 is already occupied!
         Existing MTE is fissionevolved.fission_reactor_controller(com.shenfnx.fissionevolved.MTEFissionReactor).
```

fissionevolved 0.1.1 的 `Config.java` 默认值即 `32100`/`32101`，属稳定占用。
两个终端因此完全无法注册（b3 `metatileentity.csv` 4606 条记录中查无此二者）。

### 处理：AE2-QoL 退让号段

| 用途 | 旧 ID | 新 ID |
|---|---|---|
| 自适应电网终端 AdaptiveNetTerminal | 32100 | **32106** |
| 库存统计终端 StockMonitorTerminal | 32101 | **32107** |

依据 b3 `docs/dumps/metatileentity.csv` 全表核对，32106/32107 均无占用；
并与既有 32102～32105、32110/32111 连成整块，同时避开 GTNL 写死的 32301~32331、32350~32377。

### 旧存档自动迁移（本次关键）

GT 存档只记录 MTE 数字 ID（NBT `mID`），不记类名。直接换号会让旧存档里已摆放的终端
被当成 fission 的机器加载，其配置（`ae2qolNF` 频率 / `ae2qolVT` 电压 / `ae2qolHT*`+`ae2qolHA*` 配对表）
将在下次保存时丢失——即「终端消失、频率丢失、全基地子仓解绑」。

新增 `mixin/gt/MixinBaseMetaTileEntityIdMigration.java`：

- 注入 `BaseMetaTileEntity.setInitialValuesAsNBT(NBTTagCompound, short)` 的 `HEAD`（`remap = false`）；
- 判定：`mID` 为 32100/32101 **且** NBT 含本模组专属键（`ae2qolNO`/`ae2qolNF`/`ae2qolVT`）；
- 动作：改写 `mID` 为新号后交回原逻辑，配置原样保留，对玩家无感。

fissionevolved 的 NBT 键为 `Fission*` 前缀，与本模组 `ae2qol*` 互不相交，不会误迁移。

### 存档影响面核实（逐区块解析 NBT）

| 存档 | 32100 | 32101 | 终端物品 |
|---|---|---|---|
| b3 `World` | 1 台（主世界 49,29,44） | 0 | 无 |
| b3 `新的世界` | 0 | 0 | 无 |
| b1 `world(1)` | 1 台 | 0 | 无 |

两存档均无携带旧 ID 的物品，故无需处理物品栏/箱子/AE 存储残留。

### 文档同步

- `docs/MTE_ID冲突说明.md` 重写为「已解决 + 新号段 + 迁移说明 + 影响面核实」；
- `README.md`/`README.en.md`：版本 fix48、基线 beta-3、终端 ID 32106/32107；
- `guidenh` 中英 `adaptive_grid.md` 的终端 ID 同步；
- 根目录与 `src/main/resources` 的 `mixins.ae2_qof.json` 同步注册新 Mixin。

### 验证

- `gradlew compileJava` + `build`（Java 17、offline）均 `BUILD SUCCESSFUL`；
- 解包产物核对：新 Mixin class 已入包，且 Minecraft 成员引用已重混淆
  （`func_74762_e`/`func_74768_a`/`func_74764_b`），运行期不会静默失效。
- 迁移前已备份存档至 `World_bak_before_id_migration`（445 MB）。

---

## 工作区决策记录 2026-09-20 (9) - fix47：世界中键取物的「可合成即打开下单页」

> 未提交、未推送；产物 `build/libs/AE2-QoL-3.19.0-fix47.jar`。

### 需求

对着**世界里的方块**按鼠标中键（AE2 原生 pick-block）时：

| 情况 | 改动前 | 改动后 |
|---|---|---|
| 背包里已有该物品 | 原版切槽 / 从 ME 补齐 | 不变 |
| 网络有存量 | 取到手上 | 不变 |
| 网络没存量、**有合成样板** | 完全没反应 | **打开「要合成多少个」界面** |
| 网络没存量、也没样板 | 完全没反应 | 不变 |

只打开数量输入界面，不会代为下单；玩家仍需自己填数量并点确认。

### 实现

- 新增 `mixin/ae/MixinPacketPickBlock.java`，注入 AE2 `PacketPickBlock.serverPacketData` 的 `HEAD`
  （`remap = false`，方法名按生产字节码写成完整描述符形式）。
- 判定顺序刻意做成「能不管就不管」，除目标情况外一律放行给原版：
  解析不出物品 → 放行；背包已有同物品 → 放行；没有可用无线终端 → 放行（保留原版「未找到终端」提示）；
  网络还有存量 → 放行；剩下「没存量 + 有样板」才接管并 `cancel()`。
- 被点物品取自 `PacketPickBlock.pickedBlock` 私有字段，用反射读取（取不到就放行原版），
  避免字段改名导致 Mixin 应用期硬失败。
- 开界面逻辑抽到 `ServerTerminalHelper.openCraftAmountIfCraftable(...)`，与既有 NEI 中键下单
  `RequestCraftingPacket` 共用同一段代码，消除重复实现。
- 新增 `ServerTerminalHelper.hasNetworkStock(...)`：只做 `SIMULATE` 读取、不经耗电判定，
  避免「终端没电」被误判成「网络没存量」而弹出合成界面。
- 终端查找顺序对齐 AE2 原版（先饰品栏 Baubles、再主背包），并把饰品栏槽位换算为 AE2 的
  虚拟索引 `100012 + i`；开界面用的槽位直接取「已解析终端自身的槽位」。
  这两点确保「校验样板的网络」与「界面里实际操作的网络」始终是同一个终端——
  玩家同时带两个绑定不同网络的终端时才不会错判。
- 已在 `gui.craftAmount` 中时直接返回成功、不重复打开：界面弹出有延迟，点击过快可能在
  界面出现前连发包，重复打开会清空玩家已填的数量。
- 整个注入体用 try/catch 包裹，任何异常都退化为「放行原版」，不会冒泡到网络线程。

### 不改动的部分

- 不改材质相关代码；不改 NEI 面板中键那条链路（`MixinPanelWidgetClick`）。
- 不自动替玩家点「开始合成」。

### 验证

- `gradlew compileJava` + `gradlew build`（Java 17、`--offline`）均 `BUILD SUCCESSFUL`。
- 已解包产物核对：新 Mixin class 已入包；反编译确认 Minecraft 成员引用已重混淆为
  `field_71071_by` / `field_70462_a` / `func_77969_a` / `func_77970_a`，运行期不会静默失效。
- 版本号、`mcmod.info`、README 同步 `3.19.0-fix47`。

### 线程说明（复核结论）

已查 FML 1.7.10 字节码：`FMLEventChannel.fireRead` → `EventBus.post(ServerCustomPacketEvent)`，
与 AE2 原版取物在同一调用上下文；本注入做的又是原版逻辑的子集（读背包、模拟取物、查样板、开界面），
因此不额外归队线程，行为与原版一致。

---

## 工作区决策记录 2026-09-19 (8) - fix46：修复方案 B「装包后机器变透明」

> 未提交、未推送；产物 `build/libs/AE2-QoL-3.19.0-fix46.jar`。

### 现象

用户部署 fix45 并启用完整 25 张资源包后，机器方块**变成透明（渲染不出来）**；
不装资源包时正常显示 GT 原版外观。

### 根因（实据）

用户 14:46 那次游戏日志中 25 张贴图全部输出：

```
[AE2QoL-TEX] getIcon HIT gregtech:blocks/ae2qol/universal_maintenance_hatch/TOP UV=[0.0,0.0]-[0.0,0.0]
```

UV 宽度与高度都是 0，说明拿到的是**从未被装订进图集**的空 sprite（尺寸 0×0），
渲染出来即为透明。资源包本身无问题：25 张 PNG 均为 16×16、全不透明、路径正确。

代码根因：`MixinTextureMap` 里用了「只注册一次」的静态开关。而 `TextureMap.registerIcons()`
开头会 `mapRegisteredSprites.clear()` 清空整张清单，且方块图集在启动期会经历多次
`registerIcons()`（构造器一次、首次 `loadTextureAtlas` 一次且 `skipFirst` 跳过装载、
之后每次 `refreshResources()` 再一次）。只注册一次 ⇒ 真正装载的那一轮清单里没有这 25 条 ⇒
sprite 永远是 0×0。

### 修复

- 删除 `ae2qol$v7Registered` 开关，改为**每次 `registerIcons()` 都补注册**，与
  `registerIcons()` 自身的 clear/重建语义对齐。
- `ModTextures` 增加退化 UV 兜底：`getMaxU()/getMaxV()` 均为 0 时判定 sprite 未装订，
  回退 GT 机箱而非返回空 sprite（避免再次出现透明）。
- 兜底图标改为多级安全取值（机箱 → 固体钢机箱 → 原版石头），保证任何情况下都不返回 null；
  1.7.10 的 GT 渲染器拿到 null icon 会提前 return 且不关闭 Tessellator，
  下一帧抛 `Already tesselating!` 崩溃。
- 版本号、`mcmod.info`、README 同步 `3.19.0-fix46`。

### 验证

- 完整离线构建 `BUILD SUCCESSFUL`。
- 待用户在游戏内实测：装完整资源包后 8 台机器应正常显示分面贴图；
  日志中 `getIcon HIT` 的 UV 应互不相同且非 0。

---

## 工作区决策记录 2026-09-19 (7) - fix45：材质方案 B 落地 + NEI 捕获修复

> 未提交、未推送；产物 `build/libs/AE2-QoL-3.19.0-fix45.jar`（1,119,638 B，14:32 构建）。

### 用户决策

- 材质选定方案 B：Mixin 注入原版方块图集，保留 FRONT/TOP/SIDE 分面。
- NEI 配方捕获挂空补丁必须修复。
- 维护仓因 R2 覆层丢发光层、开关无效，回归 GT 原有状态逻辑。

### 本轮改动

- 新增 `client/MixinTextureMap`：在方块图集 `registerIcons()` 尾部补注册 25 张 v7 路径；
  注册成功的图集图标回填到 `ModTextures.registerBaked(...)`。
- `ModTextures` 渲染端改为优先读取 Mixin 回填图标；旧 stitch 事件通道保留但不再使用。
- `AE2MaintenanceHatchUniversal` 删除无条件覆层，恢复 GT 发光/启用状态贴图；
  `v7_textures` 开启时使用自有分面贴图，关闭或资源不可达时完全回到 GT 默认。
- `MixinGuiRecipe` 同时注入 MCP 名 `updateScreen` 与运行时名 `func_73876_c`，
  修复生产环境 NEI 当前配方捕获注入挂空。
- 版本号与 `mcmod.info` 同步为 `3.19.0-fix45`。

### 预期行为

- 使用 `AE2QoL-v7-resourcepack.zip` 并置顶：8 台机器按三面分色渲染。
- 不使用该包：自动模式回退 GT 默认外观；`v7_textures=off` 强制回退。
- 不再覆盖 GT 原版维护仓共用覆层。

### 待用户实测

- 加包/不加包、`auto/on/off` 三种开关组合；
- 观察日志 `[AE2QoL-TEX] getIcon HIT` 的 UV 是否互不相同；
- 确认维护仓发光与启用状态是否恢复。

---

## 工作区决策记录 2026-09-19 (6) - 方案 R2：覆层同名覆盖（首次成功渲染 v7 图案，面位待修正）

> 未发布、未提交、未推送。用户选定：先只跑通万能维护仓一台。

### 实测结果（用户反馈）

- **装 `AE2QoL-v7-overlay-test.zip` 后，万能维护仓的图案变了** —— 这是 8 轮排查以来 **v7 素材首次真正渲染到机器上**，证明「覆层同名覆盖」机制**确实可行**。
- 问题：**只有正面变了，而且出现的面位不对（用户描述「这个正面应该是底面」）**。

### 根因（对照 GT 源码确认）

反编译 `MTEHatchMaintenance.getTexturesActive/Inactive`，GT 自己的写法是：

```java
TextureFactory.builder()
    .addIcon(Textures.BlockIcons.OVERLAY_AUTOMAINTENANCE)
    .extFacing()          // ← 关键：负责覆层的朝向/面位分配
    .build()
```

而本 mod 之前写的是 `TextureFactory.of(OVERLAY_AUTOMAINTENANCE)` —— **漏了 `.extFacing()`**，覆层朝向信息丢失 → 被贴到默认面（即用户看到的错误面位）。此外还**叠了三张覆层**（FRONT/TOP/SIDE），实际是三个覆层叠加。

### 本轮改动

- `hatch/AE2MaintenanceHatchUniversal.java`：
  - `getTexture(...)` 改为直接 `return super.getTexture(...)`（不注册任何新图标）。
  - 新增 `v7Overlays(aBaseTexture)`，按 GT 原写法构建：`builder().addIcon(OVERLAY_AUTOMAINTENANCE).extFacing().build()`，**只用一张图**。
  - `getTexturesActive`/`getTexturesInactive` 均委托 `v7Overlays`。
- `util/ModTextures.java` / `client/render/V7TextureStitchHandler.java`：本轮未再改动（方案 R 版保留，8 台机器的 `front/top/side/bottom` 调用仍在，但**对万能维护仓已不生效**）。
- 资源包新增 `AE2QoL-v7-overlay-test.zip`：覆盖 GT 覆层路径 `assets/gregtech/textures/blocks/iconsets/OVERLAY_AUTOMAINTENANCE{,_IDLE,_GLOW}.png`。

### 产物与部署

- `build/libs/AE2-QoL-3.19.0-fix44.jar`（1,117,273 B，11:00 构建），`BUILD SUCCESSFUL`；字节码核对 `builder→addIcon→extFacing→build` 调用链完整。
- `AE2QoL-v7-overlay-test.zip`（2,733 B，零反斜杠）。
- 两者均已部署到 GTNH 实例。

### ⚠️ 方案层面的重要结论（影响后续所有决策）

**GT 覆层机制不支持「分面」。** `OVERLAY_*` 是整体贴附、由 `extFacing()` 决定朝向，
不存在「这个覆层只管正面、那个只管顶面」的概念。

因此 v7 的 **FRONT / TOP / SIDE 三张不同设计，在覆层方案下无法分别呈现**。可选路径：

| 方案 | 效果 | 代价 |
|---|---|---|
| **A. 每台机器挑 1 张 v7 图做整机覆层** | 整机统一图案，最贴近 Modernity | 放弃 v7 的分面设计 |
| **B. Mixin 注入图集** | 保留分面 + 不影响 GT 其他机器 | 改动更深，需对 GTNH 2.9 + Angelica 调；这是之前提过的「方案 2」 |

### 已知副作用（用户此前已确认接受）

`OVERLAY_AUTOMAINTENANCE` 是 **GT 原版维护仓也在用**的覆层 —— 装包后
**GT 原版维护仓外观也会一起变成 v7**。不装包时两者都是 GT 原样，无差异。

### 下次进游戏判读

- **不装 overlay-test 包** → 万能维护仓应为 GT 原版外观（验证「不加包无影响」）。
- **装包 + 列表置顶** → 图案应贴在**正确的面**（对比上次的错误面位）。
- 需回报：**图案出现在哪一面？整机统一还是只有一面？** 据此决定走 A 还是 B。

---


## 工作区决策记录 2026-09-19 (5) - 方案 R：改挂 gregtech 命名空间 + ae2qol 子目录（对标 Modernity 做法）

> 未发布、未提交、未推送。用户选定方案 R：贴图放 `assets/gregtech/textures/blocks/ae2qol/<机器名>/`，代码用 gt 命名空间引用，只影响本 mod 这 8 台机器。

### 决策依据：对比其他材质包（用户建议「看看别的材质包怎么搞的」直接破案）

实测对比 Modernity-GTNH-2026-09-07（同环境正常显示）：

| | Modernity | 改前的我们 |
|---|---|---|
| 贴图位置 | `assets/**gregtech**/textures/blocks/basicmachines/<机器>/OVERLAY_*.png` | `assets/**ae2_qof**/textures/blocks/machine/*.png` |
| 覆盖对象 | **GT 已有、代码已引用的路径** | 全新路径，需注册 |
| 是否注册 | **不注册**，纯文件同名覆盖 | 需注册 → 撞 sBlockIcons 时序 → missingno |

已排除的三个候选原因（均有实测数据）：**不是图太多**（Modernity 有 4433 张 PNG、每机器 34 张）；**不是「一张图含六面」**（它同样每面独立 16×16）；**不是色彩格式**（它含 932 张 RGBA 真彩 16×16，同样正常）。

### 本轮改动

- `util/ModTextures.java`：
  - `BASE` 由 `ae2_qof:blocks/machine/` → **`gregtech:blocks/ae2qol/`**；新增 `F_FRONT/F_TOP/F_SIDE`（`/FRONT`、`/TOP`、`/SIDE`）与 `SHARED_BOTTOM`（`gregtech:blocks/ae2qol/_shared/BOTTOM`）。
  - **弃用 GT 队列通道**：移除 `GTCustomBlockIconContainer` 调用；`GtFallbackContainer` → **`StitchedContainer`**（只认当前 atlas 注册结果，取不到则兜底 GT 机箱图标，保证非 null）。
  - `init()` 改为仅预创建容器（不再入队）；面级方法改为 `front(name)/top(name)/side(name)/bottom()`；`firstMissing`/`checkAllResourcesReachable` 同步改到新路径结构。
- `client/render/V7TextureStitchHandler.java`：注册路径同步改为 `<机器名>/FRONT|TOP|SIDE` + `_shared/BOTTOM`。
- **8 个机器类**统一改调新 API（32 处替换 + 清理 8 行无用 `base` 变量）：改用 `ModTextures.front/top/side/bottom`，零 `ModTextures.tex(` 残留。

### 资源包结构（重建）

```
pack.mcmeta
assets/gregtech/textures/blocks/ae2qol/<8 个机器名>/FRONT.png | TOP.png | SIDE.png
assets/gregtech/textures/blocks/ae2qol/_shared/BOTTOM.png
```

- 产物：`AE2QoL-v7-resourcepack.zip`（17,887 B，已生成于交付目录），**zip 条目零反斜杠**（逐条解析中央目录核实），8×FRONT + 8×TOP + 8×SIDE + 1×BOTTOM + pack.mcmeta。
- mod jar：`build/libs/AE2-QoL-3.19.0-fix44.jar`（1,116,067 B，09:55），`BUILD SUCCESSFUL`；反编译确认常量已是 `gregtech:blocks/ae2qol/`、`StitchedContainer` 已取代 `GtFallbackContainer`。
- 部署：jar → 实例 `mods/`；资源包 → 实例 `resourcepacks/`（均 09:55）。

### 下次进游戏判读（本方案成否的判定点）

- `stitch(Pre) done: registered=25 failed=0` → 当前 atlas 注册成功（**前提：资源包已启用并拖到列表顶部**）。
- `getIcon STITCHED <path> UV=[...]` 且**各贴图 UV 互不相同** → **成功**（此前 25 张 UV 完全相同 = missingno 的紫黑症状应消失）。
- 若仍 `NOT-STITCHED` → 当前 atlas 上注册失败，需查资源包启用状态与事件触发。

---


## 工作区决策记录 2026-09-19 (4) - UV 实锤：GT 的 sBlockIcons atlas 早于资源包，改用当前活动 atlas

> 未发布、未提交、未推送。用户实测：`isReady()=true`、图标名/尺寸全对，但**仍紫黑**；UV 诊断给出决定性证据。

### 决定性证据（UV 全部相同）

```
casing_side                       UV=[0.17187744,0.5273462]-[0.1757788,0.53124756]
universal_maintenance_hatch       UV=[0.17187744,0.5273462]-[0.1757788,0.53124756]
universal_maintenance_hatch_top   UV=[0.17187744,0.5273462]-[0.1757788,0.53124756]
wireless_energy_input_top         UV=[0.17187744,0.5273462]-[0.1757788,0.53124756]
adaptive_net_terminal[_top|_side] UV=[0.17187744,0.5273462]-[0.1757788,0.53124756]
```

- **25 张不同的贴图，UV 完全相同** → 全部指向 atlas 中同一个格子。
- UV 宽度 `0.1757788 - 0.17187744 = 0.0039 ≈ 1/256`，为 16px / 4096px atlas 的单格尺寸。
- 即：`registerIcon` 返回的是 **missingno 占位 sprite**（缺失纹理），而非我们的贴图 → 渲染即紫黑。

### 根因（终于定位）

`GTCustomBlockIconContainer.run()` 注册进的是 `GregTechAPI.sBlockIcons`——**GT 在 `registerBlockIcons` 阶段绑定的那个 atlas 实例**，该阶段**早于资源包参与 atlas 重建**。本 mod 的 PNG 来自资源包，在那个旧 map 中不存在，`registerIcon` 一律返回 missingno。

这也解释了「GT++ 正常而本 mod 不行」：GT++ 的贴图在 mod jar 内、在 `registerBlockIcons` 之前已加载，走的同一个 map 且能找到；而资源包贴图晚于该阶段。

**同时解释了此前所有现象**：icon 非 null（拿到的是 missingno，非 null）、名字正确（注册名回填）、尺寸 16x16（missingno 也是 16x16）——所以此前「icon 正常」的判断是被 missingno 误导的。

### 本轮改动（最终修复）

- **新增** `client/render/V7TextureStitchHandler.java`：监听 `TextureStitchEvent.Pre`（方块图集，textureType==0），用**当前活动 atlas**（`event.map`）注册全部 25 张，写入 `ModTextures.STITCHED`。该 map 已纳入资源包，能拿到带正确 UV 的 sprite。
- `util/ModTextures.java`：
  - 新增 `STITCHED` map 与 `registerStitched`/`hasStitched`。
  - `GtFallbackContainer.getIcon()` 改为三段优先：**STITCHED（正确 UV）→ GT mIcon（保底非 null）→ GT 机箱图标**；诊断标签相应改为 `getIcon STITCHED` / `GT-ONLY` / `NULL`。
  - 类注释更新，写明「GT 队列单通道不可用」的原因。
- `CommonProxy.init()`：接入 `V7TextureStitchHandler.init()`。

### 产物与部署

- 产物：`build/libs/AE2-QoL-3.19.0-fix44.jar`（1,117,991 B，2026-09-19 09:40 构建）。
- 验证：`gradlew build` → **BUILD SUCCESSFUL**（16s），无 error；jar 内确认含 `V7TextureStitchHandler.class`。
- 部署：已覆盖复制到 GTNH 实例 `mods/`（1,117,991 B，09:40）。

### 下次进游戏判读

- `stitch(Pre) done: registered=25 failed=0` → 当前 atlas 注册成功（前提：资源包已启用）。
- `getIcon STITCHED <path> -> ... UV=[...]` 且**各贴图 UV 互不相同** → 修复成功，材质应正常显示。
- 若仍出现 `getIcon GT-ONLY ...` → 说明 STITCHED 为空（当前 atlas 上注册失败），需查资源包是否启用 / 事件是否触发。

---


## 工作区决策记录 2026-09-19 (3) - 资源包链路全通，紫黑收敛到 UV 层（UV 诊断）

> 未发布、未提交、未推送。用户实测：`isReady()=true`、25 张全可读、`getIcon HIT` 全部正常，**但仍紫黑**。

### 实测日志（决定性证据）

```
[09:25:51] resource check enabled (postInit reached)
[09:27:53] isReady()=true (25 png all reachable)                  ← 资源层完全通过
[09:27:53] getIcon HIT ae2_qof:blocks/machine/casing_side
             -> name=ae2_qof:blocks/machine/casing_side size=16x16 ← 图标名正确、尺寸正常
[09:27:55] getIcon HIT ae2_qof:blocks/machine/wireless_energy_input ...
[09:27:58] getIcon HIT ae2_qof:blocks/machine/universal_maintenance_hatch[_top|_side] ...
```

### 结论：三个前置环节全部排除

1. **资源可达性** —— `isReady()=true`，25 张 PNG 全部读到（含 PNG 魔数校验通过）。
2. **GT 队列注册** —— `GTCustomBlockIconContainer.run()` 字节码确认执行 `sBlockIcons.registerIcon(mIconName)` 并写入 `mIcon`；日志显示 `mIcon` **非 null**、`getIconName()` 返回正确路径、尺寸 **16x16**（非 0x0、非 missingno）。
3. **渲染取用** —— `getIcon HIT` 由 `Chunk Render Task Executor` 打印，说明 GT 渲染器确实取到了我们的 IIcon。

三项全通过而仍紫黑 → **问题只剩 UV 分配层**：IIcon 对象存在且数据正常，但其 `getMinU/getMaxU/getMinV/getMaxV` 未能指向 atlas 中的有效区域，渲染取到 atlas 空白/错误位置。这与第 4 轮「resource OK + getIcon HIT 16x16 仍紫黑」的观测一致，当时未能证实，现在链条已补齐。

### 本轮改动（纯诊断，未改逻辑）

- `util/ModTextures.java`：`getIcon()` 的命中日志增加 **UV 坐标输出**（`getMinU/getMaxU/getMinV/getMaxV`），用于证实/证伪 UV 未分配；新增 `HIT_LOGED` 去重集合。
- 产物：`build/libs/AE2-QoL-3.19.0-fix44.jar`（1,115,909 B，09:31 构建），`BUILD SUCCESSFUL`，已部署实例。

### 下次判读（决定最终修复方向）

- 若 UV 为 `0.0,0.0-0.0,0.0` 或全 0 区域 → **实锤 UV 未分配**。修复方向：不再依赖 `sBlockIcons`（GT 在 registerBlockIcons 阶段的 atlas bound），改为在 `TextureStitchEvent.Post` 之后注册，或直接用 `TextureAtlasSprite`/自持 `TextureMap`。
- 若 UV 是合理非零值（如 `0.125,0.25-0.1875,0.3125`）→ UV 正常，紫黑另有原因（需查 GT 渲染时的 GL 状态 / `renderFaceYNeg` 的 Tessellator 与纹理绑定），方向转向渲染管线。

---


## 工作区决策记录 2026-09-19 (2) - 实测：回退通过；「装包无变化」= isReady 缓存 false（fix44 二次修复）

> 未发布、未提交、未推送。用户实测：**不装包已恢复 GT 默认外观（回退验收通过）**；但启用资源包后机器外观无变化。

### 实测日志（`fml-client-latest.log`，修复前）

```
[09:02:52] init(): forceMode=auto, containers queued        ← 25 个 GT 容器全部入队
[09:03:20] resource check enabled (postInit reached)        ← postInit 钩子生效
[09:04:46] isReady()=false (all 25 png checked)             ← 进世界时判定为不可用
```

- `options.txt` 中 `resourcePacks` 已包含 `AE2QoL-v7-resourcepack.zip`（列于**最末位**）。
- 实例内该 zip 内容完好：`pack.mcmeta` 在、27 张 PNG 在。

### 根因

1. **`isReady()` 永久缓存 false（主因）**：旧实现用 `if (ready == null)` 判定后即永久缓存，**false 也被锁死**。资源包在**进入世界时会再次 reload**，而首次判定发生在资源尚未就绪的时刻（09:03:20 允许判定，09:04:46 进世界时才首次询问），一旦判成 false 就再不会重算 → 表现为「装了材质包也没变化」。
2. **资源包优先级最低（次要，待用户操作）**：`AE2QoL-v7-resourcepack.zip` 位于 `resourcePacks` 列表末尾，Minecraft 1.7.10 中列表越靠后优先级越低，会被 `Modernity-GTNH-*` 等包覆盖。

### 本轮改动

- `util/ModTextures.java`：
  - `isReady()` 改为 **只缓存 true**（`if (ready != null && ready)` 提前返回）；false 每次重查，代价三次 `getResource` 调用，可忽略。
  - 新增 `firstMissing()`：返回第一个不可读贴图路径，`isReady()=false` 时打印该路径，便于下次直接定位是「哪张读不到」还是「全都读不到」。
  - 更新 `isReady` javadoc，写明「只缓存 true」的原因。

### 产物与部署

- 产物：`build/libs/AE2-QoL-3.19.0-fix44.jar`（1,115,402 B，2026-09-19 09:08 构建）。
- 验证：`gradlew build --rerun-tasks` → **BUILD SUCCESSFUL**（31s），无 error。
- 部署：已覆盖复制到 GTNH 实例 `mods/`（1,115,402 B，09:08）。

### 下次进游戏判读

- **务必先把 `AE2QoL-v7-resourcepack.zip` 在 Resource Packs 界面拖到列表最顶部**（最高优先级），这步不做则大概率仍无变化。
- `isReady()=true (25 png all reachable)` → 判定成功，v7 应显示。
- `isReady()=false, first missing: ae2_qof:blocks/machine/xxx` → 按打印的路径定位是包未生效还是个别缺失。
- 若全部可读（true）但仍显示默认外观，则问题收敛到 **GT 图标队列入队时机**（`ModTextures.init()` 位于 FMLInitialization，`sGTBlockIconload` 可能已 flush），下一步把入队前移到 `preInit()` 末尾。

---



> 未发布、未提交、未推送。用户实测：**不加材质包紫黑，加了也紫黑**——即「回退到改材质之前」根本没生效。

### 根因（代码实锤 + 实例核对）

1. **`isReady()` 误判为 true（主因）**：旧实现只探 `MACHINES[0]`（universal_maintenance_hatch）一张 PNG，且在 `init()` 阶段探一次就永久缓存（`if (ready == null)`）。资源管理器处于 reload 中间态时可能拿到兜底资源而不抛异常，判定成「可达」→ 8 台机器全部走 v7 分支 → 实际 PNG 不可读 → **不论加不加材质包都紫黑**。
2. **实测核对结果**：
   - GTNH 实例 `resourcepacks` 内 `AE2QoL-v7-resourcepack.zip` **存在且完全正常**——25 张 PNG 路径正斜杠、`pack.mcmeta` 齐全，**此前「zip 反斜杠」的根因在本包上已不复现**；
   - 部署 jar 内 **0 张 machine PNG**（符合方案 P 设计）；
   - `config/ae2_qof/settings.json` 中并无 v7 相关开关，无法手动回退。

### 本轮改动

- `util/ModTextures.java`：
  - 新增 `forceMode`（0=auto / 1=强制开 / -1=强制关），`isReady()` 首先响应它；**强制关时必定返回 false，不依赖任何资源探测**——这是「彻底回到改材质之前」的可靠回退手段。
  - `isReady()` 自动模式改为**全部 25 张全可读才 true**（原：只探第 1 张）。
  - `checkResourceReachable()` 增加 **PNG 魔数校验**（前 4 字节须为 89 50 4E 47），杜绝读到兜底资源被判为可达。
  - 新增 `allowResourceCheck()`：判定时机推迟到 postInit 之后；**postInit 前一律返回 false 且不缓存**，避免把 reload 中间态锁死成错误结论。
  - 删除死字段 `ICONS`、死方法 `putIcon`/`iconCount`，`GtFallbackContainer` 简化为「GT mIcon → GT 机箱」单一兜底；`ServerSafeContainer.getIcon()` 返回 null。
- `CommonProxy.java`：`postInit()`（原空实现）接入 `ModTextures.allowResourceCheck()`；`init()` 移除对已删类的调用。
- `Config.java`：新增 `v7_textures` 配置项（auto/on/off，默认 auto，热加载），`reload()` 中同步到 `ModTextures.forceMode`；`writeFile` 增加 v7 参数并更新全部 4 个调用点。
- **删除死代码** `client/render/ClientTextureRegistry.java`（用户已确认）——`TextureStitchEvent` 通道早已不被 `ModTextures` 使用，仅剩诊断用途。

### 产物与部署

- 产物：`build/libs/AE2-QoL-3.19.0-fix44.jar`（1,115,207 B，2026-09-19 08:52 构建，version=3.19.0-fix44）。
- 验证：`gradlew compileJava --rerun-tasks` 与 `build --rerun-tasks` 均 **BUILD SUCCESSFUL**，无 error/warning；jar 内已无 `ClientTextureRegistry`。
- 部署：已复制到 GTNH 实例 `...\.minecraft\mods\`，**先删旧 jar 再复制，新旧不共存**（实例内现仅 `AE2-QoL-3.19.0-fix44.jar` 一个 AE2-QoL jar）。

### 待实测（下次进游戏判读 `[AE2QoL-TEX]`）

- **回退验证（优先）**：不启用任何 v7 资源包进游戏 → 8 台机器应恢复 GT 默认外观（这是「彻底改回去」的验收点）。
- `isReady()=true (all 25 png checked)` → 25 张齐全，启用 v7 分支。
- `isReady()=false` → 走 GT 默认外观（预期）。
- `resource check enabled (postInit reached)` → postInit 钩子生效。
- 若强制关：配置 `v7_textures` 改 `off`（或 `/ae2qof reload`）→ 必定回默认外观，不再紫黑。

### 仍未定位

- 若「强制开 + 资源包启用」下仍紫黑，则问题收敛到 **GT 图标队列注册时机**（`ModTextures.init()` 位于 FMLInitialization，此时 `sGTBlockIconload` 可能已 flush，入队等于白排）——下一步需把入队时机前移到 preInit 末尾。

---
## 工作区决策记录 2026-09-18 - 方案 P 终版：条件化贴图（装包才变、不装包原样）（fix44）



> 未发布、未提交、未推送。用户要求：彻底变回改材质之前；**加材质包才变，不加材质包维持原样**。

### 本轮改动（实现"资源包开关"语义）

- ModTextures 新增 isReady()：FMLInitialization 阶段用资源管理器检测 e2_qof:textures/blocks/machine/universal_maintenance_hatch.png 是否可达（缓存一次）；init() 仅在 ready 时预创建 GT 队列容器。
- 8 个机器 getTexture override 改为条件化：isReady() ? v7 贴图 : super.getTexture(...)——**未装资源包时走 MTEHatch 基类默认渲染（GT 机箱+覆层，与改材质前完全一致，不紫黑）**；装资源包时走 v7（GT 队列 + 资源包 PNG）。
- 2 个物品保持 AE2 原贴图（v7 前状态，git diff 为空）；物品无"缺贴图安全兜底"机制，不参与资源包切换（资源包内物品 PNG 暂不引用）。
- 资源包：AE2QoL-v7-resourcepack.zip（正斜杠路径、英文名，jar 工具打包；修复 Compress-Archive 反斜杠导致 resource MISSING 的根因），已部署游戏 resourcepacks。
- 产物：uild\libs\AE2-QoL-3.19.0-fix44.jar（1,117,548 B，21:21 构建，0 张 machine PNG，version=3.19.0-fix44），已覆盖复制到游戏 mods。

### 行为矩阵（最终语义）

| 状态 | 8 机器外观 | 崩溃风险 |
|---|---|---|
| 未启用资源包 | GT 默认外观（改材质前原样） | 无 |
| 启用资源包 | v7 材质（GT 队列 + 资源包 PNG） | 无（mIcon null 兜底机箱） |

### 日志判读（logs\fml-client-latest.log [AE2QoL-TEX]）

- isReady()=true + 	ex() created GT-queue container（启动阶段，非渲染线程）→ 装包生效，v7 显示。
- isReady()=false + init() skipped → 资源包未启用，机器原样（符合预期）。

---
## 工作区决策记录 2026-09-18 - 方案 P 实施：GT 队列引用恢复 + 资源包提供 PNG（fix44）

> 未发布、未提交、未推送。用户选定方案 P：恢复 8 机器面级 getTexture 引用（ae2_qof 路径），PNG 全部由资源包提供（优先级高于 mod jar）。

### 本轮改动

- 重建 util/ModTextures.java（修复#5 版）：客户端 	ex() 走 **GTCustomBlockIconContainer**（GT 官方图标队列，注册于 TextureStitchEvent.Pre 之前的 registerBlockIcons 阶段，sprite 正常参与 stitch/UV 分配，GT++ 同路径）；GtFallbackContainer 兜底（GT mIcon → Pre ICONS → GT 机箱图标）；服务端 ServerSafeContainer 安全。
- 重建 client/render/ClientTextureRegistry.java（实例注册 + 资源层可达性检查 + 全链路 [AE2QoL-TEX] 诊断）。
- 8 个机器类恢复面级 getTexture(IGregTechTileEntity, ...) override（ae2_qof:blocks/machine/<机器名>[,_top,_side] + casing_side 底面）；CommonProxy.init() 恢复客户端注册调用。
- **jar 不含 v7 PNG（0 张 machine 贴图）**——PNG 由资源包 AE2-QoL-v7-材质包.zip（已在游戏 resourcepacks 目录）提供。
- 产物：uild\libs\AE2-QoL-3.19.0-fix44.jar（1,116,169 B，21:01 构建，version=3.19.0-fix44），已覆盖复制到游戏 mods。

### 测试前提（重要）

**必须在游戏主菜单 Options → Resource Packs 启用 AE2-QoL-v7-材质包**（jar 内无 PNG，未启用资源包则贴图不可达 → 渲染兜底 GT 机箱、不紫黑不崩）。

### 重测判读（logs\fml-client-latest.log 的 [AE2QoL-TEX]）

- esource OK ae2_qof:textures/blocks/machine/xxx.png + 	ex() created GT-queue container + 方块显示 v7 → 方案 P 成功。
- esource MISSING → 资源包未启用/未生效，检查 Resource Packs 列表。
- getIcon MISS ... fallback GT casing → GT 队列 mIcon null 且 ICONS 空（注册未命中），游戏显示机箱贴图（安全），继续查注册时机。
- 仍紫黑且 getIcon HIT size=16x16 → UV/stitch 层问题与资源来源无关（代码方案终局失败），维持回退版。

---
## 工作区决策记录 2026-09-18 - v7 材质彻底回退 + 材质包方案落地（fix44 收尾）

> 未发布、未提交、未推送。用户拍板：放弃代码贴图方案，代码彻底改回，v7 材质改为资源包形式。

### 决策背景

修复 #1-#5 历程：v7 贴图在 GTNH 2.9 + Angelica 环境下经 5 轮代码方案均未显示成功——GTCustomBlockIconContainer 队列（#1/#5）、vanilla TextureStitchEvent 静态/实例注册（#2/#3/#4）。最终实测证据（#4 日志）：资源层可达（resource OK）、注册成功（iconCount=25）、渲染图标命中且数据正常（getIcon HIT size=16x16），但仍紫黑——sprite 疑似未被 GTNH 2.9 atlas stitch 纳入 UV 分配（Pre 事件注册时机晚于 stitch，GT 图标因注册于 registerBlockIcons 阶段而正常）。用户按预定后备方案拍板：**代码彻底改回 + 材质包**。

### 本轮改动（代码回退，git 精确还原）

- 8 个机器类 git checkout 还原（v7 面级 getTexture override 移除）：AE2MaintenanceHatchUniversal、AdaptiveNetTerminal/Hatch/LaserHatch/DynamoHatch/LaserTargetHatch、WirelessEnergyInput/OutputTerminal → 恢复 GT 默认渲染（机箱 aBaseTexture + OVERLAY 系）。
- 2 个物品 git checkout 还原：ItemWirelessMergedTerminal → setTextureName("appliedenergistics2:ToolWirelessTerminal")；ItemPartMergedTerminal → setTextureName("appliedenergistics2:ItemPart.Terminal")。
- 删除 util/ModTextures.java、client/render/ClientTextureRegistry.java；CommonProxy.init() 移除 v7 注册调用。
- 移除 src/main/resources 中 v7 新增贴图（blocks/machine 25 张 + items 2 张）——jar 内不再含 v7 PNG。
- 产物：uild\libs\AE2-QoL-3.19.0-fix44.jar（1,106,452 B，20:47 构建，验证无 ModTextures/ClientTextureRegistry/machine PNG、version=3.19.0-fix44），已覆盖复制到游戏 mods。

### 材质包交付

- AE2-QoL-v7-材质包.zip（16,356 B，27 张 PNG + pack.mcmeta pack_format=1）：ssets/ae2_qof/textures/blocks/machine/（25 张）+ items/merged_terminal_part.png、merged_terminal_wireless.png。
- 已复制到游戏 esourcepacks\ 目录 + 项目根目录。**注意**：当前回退代码不引用 ae2_qof 路径（GT 机器渲染走 GT 内置图标），资源包启用后不会立即改变 8 机器外观；其价值是**贴图资源与 mod 解耦**——如需恢复 v7 专属外观，恢复 8 机器面级 getTexture 引用（ae2_qof 路径）即可，PNG 全由资源包提供、无需重打包 mod。

### 后续可选路径（未执行，待用户决定）

- 方案 P：最小恢复——恢复 8 机器面级 getTexture（ae2_qof 路径）+ 保留资源包提供 PNG，利用资源包优先级高于 mod jar 的机制；若紫黑根因确在 jar 内 PNG 的 stitch 层而资源包 PNG 可正常加载，则可能成功（未验证）。
- 方案 Q：保持现状（GT 默认外观 + 资源包待命）。

---
## 工作区决策记录 2026-09-18 - v7 材质修复 #5：改走 GT 官方图标队列（UV/stitch 层定位）

> 未发布、未提交、未推送。修复 #4 实测：资源可达、注册成功、图标数据正常，但仍紫黑。

### 修复 #4 实测结果（用户 fml-client-latest.log，20:18-20:21）

- **资源层完全可达**：25 条 esource OK ae2_qof:textures/blocks/machine/xxx.png（getResource 读取成功）。
- **注册成功**：25 张 registered，iconCount=25。
- **渲染图标命中且数据正常**：getIcon HIT ... -> ae2_qof:... size=16x16（全部命中、尺寸 16x16 = sprite 数据正常，非 missingno 数据）。
- **结论（决定性）**：sprite 条目存在、PNG 数据加载正常，但渲染仍紫黑 → **sprite 未被 GTNH 2.9 的 atlas stitch 阶段纳入 UV 分配**（TextureStitchEvent.Pre 注册时机晚于 stitch，或 mixin 改写后 Pre 注册的 sprite 被跳过 stitch），渲染时取到 atlas 错误区域（左上角 missingno 附近）→ 紫黑。

### 本轮改动（修复 #5：GT 官方图标队列）

- util/ModTextures.java：客户端 	ex() 改用 **GTCustomBlockIconContainer.create(path)**（gregtech.client.iconContainers.blocks）——构造即入队 GregTechAPI.sGTBlockIconload，在 GT egisterBlockIcons 阶段（**TextureStitchEvent.Pre 之前**）由 sBlockIcons.registerIcon 注册，与 GT++（GTNH 2.9 正常显示）同路径；外层包 GtFallbackContainer：GT mIcon 为 null 时兜底到 Pre 注册 ICONS → GT 机箱图标，渲染永不为 null。
- 服务端：ServerSafeContainer（纯接口实现，无 gregtech.client 依赖，服务端安全）。
- 保留 ClientTextureRegistry Pre 事件双通道注册（幂等不冲突）+ 诊断日志。
- 产物：uild\libs\AE2-QoL-3.19.0-fix44.jar（1,132,722 B，20:28 构建），已覆盖复制到游戏 mods。

### 判读与后备

- 重启进游戏：	ex() created GT-queue container 出现 + 方块显示 v7 材质 → 修复成功。
- 若仍紫黑且 getIcon fallback ICONS/MISS 出现 → GT 队列也失败（sBlockIcons 注册后 sprite 仍 UV 坏），**执行用户定下的后备方案**：代码改回 GT 默认贴图（去 v7 override），v7 材质做成资源包覆盖同名路径。

---
## 工作区决策记录 2026-09-18 - v7 材质修复 #4：资源层可达性诊断（注册已成功但数据 missingno）

> 未发布、未提交、未推送。修复 #3 实测：不再崩溃（兜底生效），但仍全紫黑。

### 修复 #3 实测结果（用户上传 fml-client-latest.log，20:01-20:04）

- **实例注册生效**：TextureStitchEvent.Pre fired, textureType=0 + 25 行全部 egistered ae2_qof:blocks/machine/xxx -> ae2_qof:...（图标名正确、非 missingno 字符串）+ stitch complete, iconCount=25；Mipmap 生成与 blocks atlas 重载正常完成（6.165s）。
- **渲染图标命中**：getIcon MISS 0 条（无兜底触发）；4 台基地机器（adaptive_net_terminal、wireless_energy_input、universal_maintenance_hatch、casing_side 底面）的 	ex() created container 说明面级 getTexture override 确实被调用。
- **结论**：注册条目成功、getIcon 命中，但纹理数据仍紫黑 = **sprite 数据是 missingno（PNG 数据加载失败但 1.7.10/MCPatcher 静默不报错）**。PNG 源文件与 jar 内字节完全一致（377 B 逐字节比对）、16x16 RGBA 标准格式，构建未破坏。

### 本轮改动（修复 #4：定位资源层）

- ClientTextureRegistry.register()：注册前用 Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(domain, "textures/.../x.png")) 直接读 PNG 前 16 字节，打印 esource OK head=89504E47... 或 esource MISSING——一锤定音区分「资源层不可达」（TX Loader/资源包）与「atlas 解码层问题」；注册后打印 icon 尺寸（16x16 为正常数据，0x0/异常为 missingno）。
- ModTextures.IconContainer.getIcon()：命中时一次性打印 getIcon HIT <path> -> <iconName> size=WxH，确认渲染实际拿到的图标与数据。
- 产物：uild\libs\AE2-QoL-3.19.0-fix44.jar（1,131,708 B，20:13 构建），已覆盖复制到游戏 mods。

### 重测判读

- esource OK ... 89504E47 + getIcon HIT ... size=16x16 → 资源可读、图标正常，紫黑来自其他渲染层（查 GTRendererBlock/atlas 绑定），下一步换渲染通道（GT 队列 / 直接 TextureAtlasSprite）。
- esource MISSING → ae2_qof 资源在运行时不可达，换资源投放方式（复制进 GT 命名空间 / 资源包 / 改 TX Loader 配置）。

---
## 工作区决策记录 2026-09-18 - v7 材质修复 #3：实例注册 + GT 图标兜底 + 全链路诊断日志

> 未发布、未提交、未推送。修复 #2（vanilla TextureStitchEvent 静态类注册）实测仍全紫黑且崩溃，此轮改为实例注册 + 兜底。

### 决定性根因（实证，崩溃报告 + fml-client-latest.log 98756-98762 行）

- Caused by: NullPointerException: Cannot invoke "IIcon.func_94215_i()" because "this.baseIcon" is null at GTIconFlipped.getIconName:89 ← GTRenderedTexture.renderFaceYNeg:244 ← enderYNeg:152（Waila 触发渲染）。
- 即：8 机器面级 getTexture 返回的 ITexture 内部 IIconContainer.getIcon() 为 **null**，GT 渲染器对 null icon 提前 return 留下未关闭 Tessellator → 下一帧 enderSky 抛 IllegalStateException: Already tesselating! 崩溃。
- [AE2QoL-TEX] 注册诊断 **0 条输出**：修复 #2 用 MinecraftForge.EVENT_BUS.register(ClientTextureRegistry.class) 静态类注册，GTNH 2.9 + Angelica 环境下事件未命中（静态 @SubscribeEvent 注册不可靠）。
- 同时确认：CommonProxy.init() 的 isClient 分支正确编译进 jar（javap 反编译验证）；FMLInitializationEvent 正常发送（日志 29107/29109 行）；ae2_qof mod 正常加载；STDOUT 捕获机制正常（其他 mod 的 System.out 可见于日志）；GregTech.log 2744-2749 行 GT 自身图标注册流程（Setting up Icon Register → Block Icon Load Phase）执行正常。

### 本轮改动

- client/render/ClientTextureRegistry.java：静态类注册 → **单例实例注册**（MinecraftForge.EVENT_BUS.register(INSTANCE)，实例方法 @SubscribeEvent，1.7.10 最可靠路径）；注册失败 try/catch 不吞；补 [AE2QoL-TEX] 全链路日志（init 注册、事件触发、每图标注册、stitch 完成计数）。
- util/ModTextures.java：IconContainer.getIcon() 增加 **GT 机箱兜底**——ICONS 未命中时返回 Textures.BlockIcons.MACHINE_LV_SIDE.getIcon()（非 null），杜绝 null icon 渲染崩溃；首次未命中打印一次性诊断行；	ex() 首次创建容器打印诊断。
- 产物：uild\libs\AE2-QoL-3.19.0-fix44.jar（1,130,471 B，19:55 构建），验证 25 张贴图齐全、mcmod.info version=3.19.0-fix44、新 class 入包；已覆盖复制到游戏 mods（19:55:43，唯一 AE2-QoL jar）。

### 重测与判读指引

覆盖安装后进游戏，看 logs\fml-client-latest.log：
- 有 [AE2QoL-TEX] TextureStitchEvent.Pre fired + 25 行 egistered ae2_qof:blocks/machine/xxx -> ae2_qof:... → 注册成功、getIcon 命中、v7 材质正常显示。
- 有 Pre fired 但 egistered ... -> missingno → PNG 加载失败（资源层问题，查 TX Loader/MCPatcher）。
- 无 Pre fired、有 getIcon MISS ... -> fallback GT casing → 事件未命中，但游戏不再紫黑/崩溃（兜底显示 GT 机箱贴图），继续查事件注册时机。
- 无 Pre fired 也无 MISS → 8 机器 getTexture override 未被调用（渲染链路问题），查 GTRendererBlock 纹理数组来源。

---
## 工作区决策记录 2026-09-18 - v7 材质修复 #2：弃用 GTCustomBlockIconContainer，改 vanilla TextureStitchEvent 注册

> 未发布、未提交、未推送。用户两次实测均紫黑/粉黑缺失纹理（missingno），修复 #1（init 预创建 + 缓存）后仍全紫黑，此轮换注册通道并加诊断日志。

### 已排除项（实证）

- 贴图未进 jar / PNG 损坏：排除（25 张 machine + 2 张 items PNG 签名 PNG-OK、jar 内齐全）。
- 旧版/重复 jar 冲突：排除（mods 仅 AE2-QoL-3.19.0-fix44.jar，1,127,037 B = 修复版）。
- 图标加载队列 flush 异常：排除（fml-client-latest.log 19:15:54 Starting/Finished Block Icon Load Phase 无堆栈）。
- 渲染链路：已 javap 确认 BlockMachines.getIcon(IBlockAccess) 固定返回 MACHINE_LV_SIDE（兜底，非渲染主路径）；真实渲染走 GTRendererBlock（getRenderType=RENDER_ID）→ TE ITexturedTileEntity.getTexture(Block, ForgeDirection) → 转调 MTE 面级 getTexture（签名与我们的 override 一致）。

### 本轮改动

- util/ModTextures.java 重写：不再用 GTCustomBlockIconContainer（其 getTextureFile 返回具体资源而非 blocks atlas，路径语义在 GTNH 2.9 + Angelica/MCPatcher 环境下不可靠）；改为自实现 IIconContainer（getIcon 从注册表取、getTextureFile 返回 blocks atlas 位置，与 GT++ CustomIcon 一致）。
- 新增 client/render/ClientTextureRegistry.java（@SideOnly CLIENT）：监听 TextureStitchEvent.Pre（type 0 blocks atlas），注册 25 张贴图并打印 [AE2QoL-TEX] registered <path> -> <iconName> 诊断行；服务端安全（CommonProxy.init 仅客户端分支注册）。
- CommonProxy.init()：ModTextures.init() 预创建调用 → 改为客户端注册 ClientTextureRegistry。
- 产物：uild\libs\AE2-QoL-3.19.0-fix44.jar（1,129,369 B，19:34 构建），反编译确认无 GTCustomBlockIconContainer 依赖、25 PNG 在 jar 内。

### 重测指引

覆盖安装后进游戏：正常 → 8 机器六面显示 v7 材质；仍紫黑 → 看 logs\fml-client-latest.log 中 [AE2QoL-TEX] 行：
- 25 行均 -> ae2_qof:blocks/machine/xxx（注册成功）→ 问题在渲染/atlas 层，继续查 GTRendererBlock 纹理数组来源；
- 出现 -> missingno → PNG 未加载（TX Loader/MCPatcher 资源层问题），需换资源注入通道。

---

## 工作区决策记录 2026-09-18 - v7 材质包接入：8 GT 机器方块 + 2 AE 物品切到自定义贴图

> 未发布、未提交、未推送。按用户要求「根据这个更换吧」将项目从借用 GT 内置 BlockIcons / AE2 原版贴图切换为使用 v7 材质包。

### 改动

- 贴图复制：`docs\AE2-QoL-textures-v7 (1)\assets\ae2_qof\textures` 的 25 张 `blocks\machine` PNG（8 机器 × {主体,_side,_top} + casing_side）+ 2 张 `items` PNG → `src\main\resources\assets\ae2_qof\textures\` 对应目录。
- 新增 `util/ModTextures.java`：`tex/front/top/side/bottom` 便捷方法，基于 `GTCustomBlockIconContainer.create(String)` + `TextureFactory.of`（GTNH 官方自定义贴图姿势，javap 确认签名）。
- 8 个机器类新增**面级** `getTexture(IGregTechTileEntity, ForgeDirection side, ForgeDirection facing, int, boolean, boolean)` override（优先于原 getTexturesActive/Inactive）：正面=机器名主体图、UP=_top 图、DOWN=casing_side、四侧面=_side 图；原 getTexturesActive/Inactive 保留未动。
  - `AE2MaintenanceHatchUniversal` → universal_maintenance_hatch；`AdaptiveNetTerminal` → adaptive_net_terminal；`AdaptiveNetHatch` → adaptive_net_hatch；`AdaptiveNetLaserHatch` → adaptive_net_laser_hatch；`AdaptiveNetDynamoHatch` → adaptive_net_dynamo_hatch；`AdaptiveNetLaserTargetHatch` → adaptive_net_laser_target；`WirelessEnergyInputTerminal` → wireless_energy_input；`WirelessEnergyOutputTerminal` → wireless_energy_output。
- 2 个物品 `setTextureName` 切换：`ItemWirelessMergedTerminal` → `ae2_qof:items/merged_terminal_wireless`；`ItemPartMergedTerminal` → `ae2_qof:items/merged_terminal_part`（脱离 AE2 原版贴图）。

### 验证

- JDK21 `gradlew compileJava` → `BUILD SUCCESSFUL`；`processResources` 后 build 产物含全部 25+2 张新贴图；8 机器 + 2 物品改动类的 .class 均已生成。
- **未做游戏内渲染验证**（材质包 README 亦声明未做游戏内验证）：需启动客户端确认 8 方块六面贴图与 2 物品图标实际显示、GTCustomBlockIconContainer 图标注册正常；注意面级 getTexture 返回单层贴图后**不再叠加** GT 内置 OVERLAY（OVERLAY_SCREEN / OVERLAY_AUTOMAINTENANCE / OVERLAYS_ENERGY_*_WIRELESS），新贴图自带部件区，如有意保留叠加需后续调整。

---

## 工作区决策记录 2026-09-18 - 无线EU输入侧恢复「镜像实时共享」语义（方案A）

> 未发布、未提交、未推送。记录 A04(P1) 审计建议与用户真实需求冲突的取舍，供后续接续。

### 背景与冲突

- fix41 全功能审计 `docs/mcp-full-function-audit-fix41.md` A04(P1) 指出：无线输入侧「镜像余额」模式不守恒——本地缓冲免费镜像电网余额、只在消耗后补扣，多舱室可同时持有同一份未扣余额（双重花费），且 `addEUToGlobalEnergyMap(uuid, 负数)` 余额不足返回 false 被忽略，没有守恒保证。
- A04 建议改为「成功从全局扣款后才充入本地」的预付所有权模型（工作区曾据此实施：`WirelessEnergyTransfer.fill()` 先扣款再充本地，缓冲上限 4×V×A，拆除/改绑 `deposit()` 退还）。
- **冲突**：用户明确要求「所有舱室共享无线电网的全部能量，实时从电网扣除消耗的能量」——即 3.18.0 已提交版语义，与预付模型直接冲突。指南文档 `wireless_eu_grid.md` 亦写明「电网本身不储能、EU 实时传输」。

### 决策

按用户要求执行方案 A：恢复 3.18.0 镜像实时语义；**A04(P1) 缺陷保持开放**（用户需求优先，不关闭）。后续如需同时满足「全共享」与守恒，走方案 B：机器实际取电时直接从电网实时扣款（重写取电路径），而非恢复预付缓存。

### 改动

- `WirelessEnergyInputTerminal`：`onPreTick` 恢复镜像余额 + 消耗后实时补扣；移除 `prepaidBuffer`、`onRemoval`、改绑/解绑 `deposit()`。
- `AdaptiveNetHatch` / `AdaptiveNetLaserHatch`：`onPreTick` 恢复 3.18.0 镜像逻辑（现与已提交版零差异）。
- `WirelessEnergyTransfer`：移除预付 `fill()` / `bufferTarget()`，仅保留输出侧 `deposit()`。
- 保留绑定持久化修复（`bindingInitialized` / `markDirty` / NBT `removeTag`）与输出侧 deposit 安全回滚。

### 验证

- JDK21（`D:\zulu21.38.21-ca-jdk21.0.5-win_x64`）`gradlew compileJava` → `BUILD SUCCESSFUL in 1m`，27 条已知 mixin mapping 警告与基线一致；改动类的 .class 均已重新生成。
- 未做游戏实测、未提交/推送。

---

## 3.19.0-fix44 - v7 材质包接入 + 版本号发布

> 作者：wztwzt | 更新时间：2026-09-18 | 基于 fix43 工作区

- 8 个 GT 机器方块（万能维护仓、五个 AdaptiveNet 系列、无线EU输入/输出终端）从借用 GT 内置 BlockIcons 切换为 v7 材质包自定义贴图：新增 `util/ModTextures.java` + 各机器类面级 `getTexture` override（正/顶/底/侧分面），2 个 AE 物品（合并无线终端、合并终端部件）`setTextureName` 切到自有贴图，详见上方「工作区决策记录 2026-09-18 - v7 材质包接入」。
- 版本号两处同步提升：`gradle.properties` `modVersion` 与 `src\main\resources\mcmod.info` `version` → `3.19.0-fix44`。
- 验证：JDK21 `gradlew build -x spotlessCheck -x spotlessJavaCheck` 成功；产物 `build/libs/AE2-QoL-3.19.0-fix44.jar`（1,126,435 字节），jar 内 mcmod.info 版本为 fix44、含全部 25 张 blocks\machine 新贴图 + 2 张 items 贴图 + `ModTextures.class` + 8 个机器改动类。
- 未做游戏实测、未提交/推送；Spotless 全项目格式检查（76 个历史文件）与本次改动无关，构建时按惯例跳过。
- **19:07 首版实测崩溃修复**：Already tesselating!（Tessellator 未关闭）+ 材质缺失。根因：ModTextures.tex() 在渲染期才首次 GTCustomBlockIconContainer.create()，错过 GT 图标加载队列 flush（纹理拼接阶段），getIcon() 返回 null → 渲染成缺失纹理，且 GT 渲染器对 null icon 在 startDrawing 后提前 return 不 draw()，Tessellator 残留打开，下一帧渲染天空即崩。修复：ModTextures 改为静态预创建全部 25 张贴图容器 + 缓存，CommonProxy.init() 开头调用 ModTextures.init()（早于纹理拼接）；19:12 重建 AE2-QoL-3.19.0-fix44.jar（1,127,037 字节），反编译确认 init()/CACHE 已编译入包。待用户重测。
- 独立问题（非崩溃根因，后续处理）：mixins.ae2_qof.json 中 
ei.MixinGuiRecipe.captureCurrentRecipe 注入 GuiRecipe.updateScreen 失败（InvalidInjectionException，UniMixins WARN 容忍不崩），NEI 自动上传相关功能可能失效，需核对 NEI 2.8.101 的方法签名。

---

## 3.19.0-fix43 - 库存检测覆盖板堆叠上限 64

> 作者：wztwzt | 更新时间：2026-09-18 | 基于 fix42 工作区

- `ItemStockMonitorCover`：`setMaxStackSize(1)` → `setMaxStackSize(64)`。
- 仅调整物品堆叠上限，不改库存检测阈值、覆盖板配置 NBT、安装/拆卸或绑定逻辑；不同 NBT 配置仍不能混堆。
- 保留 fix42 Tooltip 修复，同步主版本、双语 README 和交接状态。
- 验证：Java17 离线 `build --offline -x spotlessJavaCheck -x spotlessCheck` 成功，退出码 0；发布 JAR 构造器字节码确认堆叠上限 64，元数据为 fix43。Spotless 跳过，Gradle test 为 NO-SOURCE；未部署、未游戏实测、未提交/推送。
- 产物：`build/libs/AE2-QoL-3.19.0-fix43.jar`；SHA256：`be4d6490698d4d4e931f6b93394e1d607e8d6a2d723aacdfebe1efd9c7ac2869`；日志 `build_cover_stack64_fix43.log`。堆叠/安装/拆卸待游戏验证，详见交接第十一节。

---

## 3.19.0-fix42 - NEI / Chromatic Tooltip 单入口修复与文档更新

> 作者：wztwzt | 更新时间：2026-09-18 | 基于 fix41 工作区 | 实施：Arena.ai Agent Mode / ShunCode MCP

### 原因与版本范围

已安装的 Chromatic Compat 1.0.31 先收集通用 `handleTooltip`，随后 Core 1.0.29 的 `ContextInfoEnricher` 经 Compat `NEIHandler` 在另一列表中调用 `handleItemTooltip`，最后合并两份列表。fix41 两条路径都追加网络行，因而重复。对照 NEI 为 2.8.101-GTNH；编译依赖仍为 2.8.19-GTNH。

原生 NEI 的非空通用列表会跳过正常物品提示路径，不能把“有两个回调”泛化成原生 NEI 必然重复。精确字节码和流体桥接依据见 [调查报告](docs/mcp-tooltip-duplicate-investigation.md)。

### 修复内容

1. `NetworkTooltipHandler.handleTooltip` 原样返回输入列表；只有 `handleItemTooltip` 追加网络信息。
2. 替换接手前已有但未部署的“一秒相同文本抑制”修改，移除时间戳、共享文本、Chromatic 存在标志和无用导入；不再跨物品抑制同样数量。
3. 保留 `buildNetworkLine`、缓存查询、开关、有效期、数量与 Craft 格式。当前 Compat 流体上下文转换为 GT 展示物品，继续由原缓存识别；真实桶/单元仍按物品库存计数。
4. 只改一个业务类；没有改 Mixin、依赖、网络协议、服务端逻辑或存档格式。主版本更新为 `3.19.0-fix42`，内置 aeinfinitycell 仍为 `1.0.4-ae2qol`。

### 文档与验证

- 完整刷新中英文 README：对齐 F1–F22、依赖版本、配置范围、流体/容器语义、开放审查项、构建限制和来源署名；修正英文错误日志链接及 IO 贴图归属。
- 更新 `docs/MOD_MAP.md` 的 Tooltip/角标入口与五页电网 UI；交接结果已写入 `docs/AGENT_CHECKPOINT.md`。
- 新增 `docs/tooltip-fix42-regression.sh`，用真实 handler / CountFormatter 与依赖桩运行 **59 项隔离断言，全部通过**。覆盖单入口、同数量快速切换、状态组合、流体文本格式、开关/无效缓存门控及列表合并模型；**不是 MC/NEI/Chromatic 集成测试**。
- Java **17.0.19** 离线构建：`./gradlew build --offline -x spotlessJavaCheck -x spotlessCheck` → `BUILD SUCCESSFUL`。首次实际编译 56s；命令管道后退出码取值出错，外层返回 2，随后无管道命令复核 3s 成功、退出码 **0**。
- `checkstyleMain` 通过；Spotless 显式跳过；Gradle `test` 为 `NO-SOURCE`。首次编译保留 27 条 Mixin mapping 警告及弃用/unchecked 提示，未顺带整改。
- 打包检查：`mcmod.info` 为 fix42；handler major version 52；通用回调字节码只返回列表；无旧去重字段。`git diff --check` 无差异格式错误。

### 制品与交付边界

- 发布 JAR：`build/libs/AE2-QoL-3.19.0-fix42.jar`（**1,104,409 字节**）。
- SHA256：`75961bacd0e6085dcc78fff7476757c1a5926b41cccb7bebad1f6f37d6850739`。
- 日志：根目录 `build_tooltip_fix42.log`、`build_tooltip_fix42_verify.log`、`build_tooltip_fix42_regression.log`；既有四份 build_compile 日志保留。
- **未部署、未启动游戏、未做单人/专用服真实 GUI 验收，未提交/推送。** 当前实例仍需另行授权升级；不要把构建/隔离断言当作实机通过。
- 待验收：原生/Chromatic、各终端/配方/书签、GT/ae2fc 流体、容器计数、快速切换及真实缓存过期。逐项预期见 [交接第十节](docs/AGENT_CHECKPOINT.md)。
- 不关闭 [fix41 全功能审查](docs/mcp-full-function-audit-fix41.md) 的 A01–A19；原有删除、未跟踪资料和工作区修改不批量清理。
- 回退必须另行确认：保留原 JAR/完整存档备份，精确撤销本轮变更，不整仓 reset，不恢复旧的一秒文本去重作为兼容方案。

---

## 3.19.0-fix41 - 自动上传可靠性：稳定定位目标 + 失败回执 + 样板槽逐个判断

> 作者：wztwzt | 更新时间：2026-09-18 | 基于 3.19.0-fix40

### 背景

fix40 重做了选择界面后，复查自动上传链路时发现三处会实际影响玩家的隐患。

### 修复

1. **目标定位改用稳定坐标（不再依赖内存地址）**
   - 旧实现用 `System.identityHashCode(machine)` 当供应器 ID。该值随对象重建变化：区块卸载重载、
     机器拆装、服务器重启后都会失效，表现为「选好机器后放一会儿再点上传，毫无反应」。
   - 新增 `util/ProviderLocator`，用「维度 + 坐标 + 部件朝向」作为稳定标识，随列表一起下发；
     上传时服务端先按坐标匹配，匹配不到再回退按旧 ID 匹配，两条都失败才判定目标已不存在。
   - 单供应器直传、记住目标自动上传、手动选择上传三条路径都会带上该标识。

2. **上传失败不再静默**
   - 新增 `network/UploadFeedbackPacket`（S2C）与三个语言键；目标丢失、目标拒绝写入（槽满/类型不接受）、
     无 INJECT 权限这三种情况会在聊天栏给出明确原因，不再只是写服务端日志。

3. **样板槽「能否接收」改为逐个空槽判断**
   - 旧实现只看第一个空槽：若某台机器的样板槽分类型（前段收物品样板、后段收流体样板），
     会把本来放得下的机器误判成「收不下」并从候选列表剔除。
   - 现在遍历全部空槽，只要有一个空槽接受该样板即判定可接收；服务端实际写入逻辑同步一致。
## 3.19.0-fix40 - 重做样板上传选择界面（分区排版 + 配方映射区）

> 作者：wztwzt | 更新时间：2026-09-17 | 基于 3.19.0-fix39

### 背景

fix31 照着 GTNH-ECO 把选择界面重做成「图标卡片列表」时，只抄了列表外观，没有安排本模组自己的
控件位置：配方映射输入框和「添加/删除/刷新/取消」四个按钮被画在同一行，实测互相遮挡（输入框被按钮
压住、按钮只剩黑块），并且自己的「配方 → 机器名映射」功能在界面上看不出来。玩家反馈“有点劣质，
还不如改回原来的”。

### 修复

- **重新分区排版**：面板自上而下固定为 标题栏 → 搜索栏 → 机器列表 → 操作按钮 → 配方映射区 → 底部提示，
  各区域纵坐标独立计算，不再出现控件互相覆盖。
- **窗口自适应**：面板宽度、可见行数随窗口尺寸调整（宽度 280~400，行数 3~7），小分辨率下优先保证
  底部映射区与按钮完整可见。
- **补上本模组自己的映射区**：新增「配方 -> 目标机器 映射」区，左侧输入配方 ID、右侧输入目标机器名，
  下方为 添加 / 删除 / 刷新 / 用选中机器 四个按钮。
- **新增「用选中机器」按钮**：一键把列表里选中的那台机器的展示名（含 `@D 维度 x,y,z` 后缀）填进
  目标机器名框，避免手打出错导致自动上传匹配不到。
- **打开界面时自动填入**：配方 ID 默认填本次配方，目标机器名在该配方已有映射或能对应到列表中的机器时
  自动填入；没有把握时留空，不写入错误内容。
- **删除更精确**：配方 ID 非空时按 key 精确删除单条映射，只填了机器名时沿用旧的按值删除。
- **上传记忆修正**：上传成功后记住的目标名改用与自动上传查询一致的 key，保证「记住的目标」下次真的命中。
- **取消不再误改终端搜索框**：点「取消」直接返回，只有真正上传后才同步终端搜索关键字。

- 交互补充：单击选中、双击（350ms 内）直接上传、滚轮翻页、右键搜索框清空。
## 3.19.0-fix39 - 紧急修复：移除 RFB childDelegations 注入（fix38 启动崩溃根因）

> 作者：wztwzt | 更新时间：2026-09-17 | 基于 3.19.0-fix38

### 现象

使用 3.19.0-fix38 启动时在 FML preInit 阶段崩溃：

```
java.lang.RuntimeException: Enum net.minecraftforge.event.terraingen.PopulateChunkEvent$Populate$EventType
was not made extensible, add it to lwjgl3ify configs.
    at me.eigenraven.lwjgl3ify.EnumHelper.addEnum(EnumHelper.java:141)
    at net.minecraftforge.common.util.EnumHelper.addEnum(EnumHelper.java)
    at mods.railcraft.common.worldgen.GeodePopulator.<clinit>(GeodePopulator.java:34)
```

### 根因

- fix12 曾把 F22「加入库存统计终端就崩溃」误判为 RFB 类加载问题，因此往 RFB 系统类加载器的
  `childDelegations` 集合里加入 `"net.minecraft"`，希望让 RFB 委托给 LaunchClassLoader。
- 但该代码实际从未生效：`childDelegations` 的真实类型是 `HashSet`，而旧实现只处理
  `List` 与 `String[]`，因此一直走空分支。fix14 日志中的 `[AE2QoL-RFB] VERIFY FAIL` 即为证据，
  游戏也因此一直能正常启动。
- fix32 在清理日志时顺带补上了 `Set` 分支，使这段补丁**第一次真正生效**。
- 加入 `"net.minecraft"` 前缀后，`net.minecraftforge.*` 同样被委托给子类加载器加载，
  绕过了 RFB 的 `ExtensibleEnumTransformer`，使 lwjgl3ify 的「可扩展枚举」改写失效，
  Railcraft 注册枚举时即抛异常并中断启动。
- F22 崩溃的真实根因（已在 fix30 查明）是 MetaTileEntity ID 32001 与 GT 本体重号，与 RFB 无关。
  该补丁既无必要，又有害。

### 修复

- 彻底删除 `CommonProxy.preInit` 中的 RFB `childDelegations` 注入代码块，恢复 fix14 的类加载行为。
- 保留说明性注释，记录误判历史与真实根因，避免后续智能体再次「修好」这段代码。

### 验证

- `gradlew build --offline -x spotlessCheck -x spotlessJavaCheck` → `BUILD SUCCESSFUL`
- 产物：`build/libs/AE2-QoL-3.19.0-fix39.jar`
## 3.19.0-fix38 - 质检整改：功能缺陷修复 + 安全加固 + 生命周期清理（阶段 3）

> 作者：wztwzt | 更新时间：2026-09-17 | 基于 3.19.0-fix14
>
> 本节汇总阶段 1 静态审查（32 条问题）与阶段 2 单机实测发现问题后的全部修复，
> 逐条对应 `docs/STATIC_AUDIT_ISSUES.md` 的编号，便于追溯。

### 安全（P0/P1）

- P0-001：合并终端 Shift+滚轮替换改为槽对象白名单，仅允许样板虚拟槽；候选物必须来自当前 ME 网络并经 `Actionable.MODULATE` 真实扣除，封堵「伪造槽号往玩家背包写物品」的刷物路径
- P1-010：合并终端重命名包同样限制为目标样板虚拟槽；清空名称只改 `display.Name`，保留 lore/颜色等 NBT
- P1-002：无线收发器删除频道/改频/切模式增加所有者与团队鉴权，禁止删除他人全局频道
- P1-011：自适应终端定位/传送/高亮增加会话与团队鉴权，避免跨团队设备坐标泄露
- P1-003/P1-015/P1-016：库存统计终端恢复注册（ID 冲突根因见下），覆盖板远程编辑按 AE 网络 `BUILD` 权限校验，选中条目按玩家 UUID 隔离

### F22 库存统计终端（P1-003/P1-032）

- 根因：MetaTileEntity ID **32001 已被 GT 本体 LegacyUniversalChemicalFuelEngine 占用**，构造期抛 `IllegalArgumentException`，被 Log4j/RFB 二次加载错误掩盖成「类加载崩溃」
- 改用空闲 ID **32101** 后 F22 可正常注册与获取
- 覆盖板注册表改为全局主世界存储（P1-007），并对旧 per-dimension 数据做一次性合并迁移；打开终端时刷新在线/存在状态（P1-008），列表不再按当前维度过滤（P1-032）

### F1 样板自动上传 / 上传 UI（用户实测重点）

- 供应器展示名追加 `@D维度 x,y,z` 后缀，同名机器可区分；按样板可接纳性过滤候选；工作台类配方走独立分支，优先复用上次记住的目标，找不到时列出全部可选目标，避免「随机乱传送」
- 上传选择界面参考 GTNH-ECO 重做为图标卡片列表：每行 = 机器图标 + 名称 + 「空闲/总数」槽位，支持鼠标滚轮翻页、搜索、回车确认、右侧滚动条；顶部显示配方类型与候选数量；同名机器聚合为一张卡片并固定指向空槽最多的那台
- 协议扩展总槽位与图标字段，并对包大小做预算保护（图标超预算时整包退化为不带图标发送，保证列表永远可达）

### 合成与智能倍增（P1-018/P1-019/P1-020）

- 合成完成通知放宽投递条件：没有安全终端/未携带绑定无线终端时退化为直接通知下单玩家，修复「合成完成却没有提示」
- 智能倍增：`submitJob` 被拒（CPU 忙）时不再清空正在运行任务的通知状态；批量输出记账乘法前做 `Long.MAX_VALUE` 饱和检查；跨配方并行的 EU、时长、电压×电流、输出合并全部提前判界，避免静默停机

### 生命周期与跨存档（P1-012/P1-021/P1-023/P1-024/P2-026/P2-030）

- 服务器/单机世界停止时保存并清空自适应电网静态网络、无线频道与方块链接、供应器列表缓存
- 客户端在世界卸载时清空无线高亮、自适应仓列表与 NEI 网络库存缓存
- 任务检测器定期清理离线玩家的需求键缓存

### 兼容性与工程（P1-004/P1-005/P1-006/P1-009/P1-017/P1-022/P1-031）

- RFB `childDelegations` 注入改为适配 Set 类型的最小实现，诊断刷屏降为 debug
- NEI 存量提示兼容 `chromatictooltipscompat`（其 HEAD 注入 cancel 会截断 tooltip）
- 无限磁盘统计查询拒绝磁盘上不存在的 UUID，字符串长度收紧且解码加保护
- 配置迁移保留 `smartDoublingMaxRounds=0` 的「不限」语义，且新 `settings.json` 写入成功后才删除旧 cfg
- `ensureFresh()` 加锁，消除配置热加载竞态
- 根目录 `mixins.ae2_qof.json` 与 `src/main/resources/` 版本完全同步（打包实际使用 resources 版本）
- 版本号统一：`gradle.properties`、`mcmod.info`、README ×2、CHANGELOG 全部为 `3.19.0-fix38`（本轮追加 fix38 的自适应终端改频顺序与配置鉴权修复）

### 日志清理（P1-004 附带）

- 清理网络数据棒、自适应终端、RFB 诊断的 info 级刷屏日志，统一降为 debug
## 3.19.0-fix38 - M6 库存统计终端（发信器+覆盖板集中管理 + Nexus 无线连接）

> 作者：wztwzt | 更新时间：2026-09-11 | 基于 3.19.0-fix10

### 新增：M6 库存统计终端（P1，文档 §12 设计落地）

- GT 单方块免电信息终端（ID 32001，继承 MTEHatch，0 库存槽，isElectric=false）
- 集中查看/修改 AE2 标准发信器（`PartLevelEmitter`）与本模组库存检测覆盖板
- GUI 双列表：上半部分发信器列表，下半部分覆盖板列表，点击条目弹出编辑子面板

### 新增：覆盖板全局注册表 CoverRegistry（WorldSavedData）

- `DATA_ID="ae2_qof_cover_registry"`，键格式 `dim:x:y:z:side`
- 存储覆盖板位置+配置摘要（networkId/targetName/targetIsFluid/threshold/modeOrdinal/online）
- `StockMonitorCover.doCoverThings` 每 10tick upsert，`onCoverRemoval` 移除
- 支持跨维度枚举，区块未加载时标记离线（灰色显示）

### 新增：Nexus 无线网络连接（与覆盖板完全相同的机制）

- 新建 `StockMonitorTerminalWirelessEndpoint`（实现 `WirelessBindableEndpoint`，14 方法，不占频道）
- GUI 顶部连接状态显示 + "连接 AE"按钮，弹出 Nexus 原生 `WirelessSelectionPanel`
- 终端存储 `networkId`（NBT 持久化），支持绑定/解绑/切换网络
- 发信器枚举双模式：优先 Nexus 无线（`WirelessAeConnector.getGridForNetwork`），回退邻接（`NeighborAeConnector.findGrid`）

### 新增：编辑子面板（MUI2 syncedPanel + SyncValue 双向同步）

- 覆盖板编辑：阈值输入框（`LongSyncValue.allowC2S`）+ 模式切换（BELOW/ABOVE，`IntSyncValue.allowC2S`）+ 位置信息 + 关闭按钮
- 发信器编辑：阈值输入框 + 类型显示（物品/流体/能量）+ 能量类型只读提示
- 修改自动写回服务端，覆盖板调用 `markCoverDirty()` 标记脏，发信器调用 `setReportingValue()`
- 发信器修改需 AE2 网络 `SecurityPermissions.BUILD` 权限拦截

### 修复：覆盖板模式切换不同步（fix10，P1）

- 根因：模式切换按钮 `onMousePressed` 是客户端-only，只改客户端内存，服务端 mode 不变，下次 GUI 刷新从服务端同步回来就变回"低于N开机"
- 修复：模式改用 MUI2 `IntSyncValue.allowC2S()`，点击时通过 `modeSync.setValue()` 修改，MUI2 自动推送到服务端并写回 `coverData.setMode()`

### 修复：维护仓跨配方纯流体配方 NPE（fix9，P0）

- 根因：纯流体配方 `helper.getItemOutputs()` 返回 null，`for (ItemStack out : null)` 抛 NPE，被 catch 吞掉返回 NO_RECIPE
- 日志定位：第二次 process() 输入 `fluid.oxygenx2147483647` → matched recipes=1 → build ok=true → 紧接着 NPE
- 修复：输出数组遍历前加 null 检查 + 元素 null 检查，去掉调试日志

### 修复：覆盖板流体库存读取为 0（fix7，P0，用户多次反馈"流体就识别不到"）

- 根因：AE2UEL `AEFluidStack.equals`（302-303行）对 `AEFluidStack` 参数比较的是 **Fluid 实例引用（==）和 NBT 引用（==）**，不是比较流体 ID 或 NBT 内容。新建的 target 的 Fluid 实例和 AE2 网络里存储的实例不是同一个对象引用 → `findPrecise` 永远匹配不到 → 返回 0
- 修复：`AeStockReader.readStock` 流体分支改用遍历 `getStorageList()` + `getFluid().getID()` 比较

### 修复：phantom 槽改进（fix7）

- 新建 `StockMonitorPhantomSlot`（继承 `PhantomItemSlot`），重写 `drawSlotAmountText` 为空（不显示数量）、重写 `onMousePressed` 检测 Shift+左键时清空标记
- 用户需求："只用标记，不用显示数量，shift 左键点击取消标记"

### 修复：覆盖板 GUI 实时刷新（fix6）

- `lastChannel/lastStock/lastShouldWork` 注册为 MUI2 `IntSyncValue/LongSyncValue/BooleanSyncValue`（S2C 自动推送），动态文本改读 SyncValue
- 断开按钮邻接连接时提示"邻接连接无法断开，请移除覆盖板"

### 工程：版本号升级 + 文档同步 + GuideNH

- `gradle.properties` + `mcmod.info`：`3.19.0-fix5` → `3.19.0-fix38`（两个文件同步）
- GuideNH 新增 `stock_monitor_terminal.md`（中英文），index.md 功能列表从 20 项更新为 21 项
- 构建产物：`build/libs/AE2-QoL-3.19.0-fix38.jar`（1,059,821 字节，编译通过）

### 变更文件

| 文件 | 内容 |
|---|---|
| `terminal/CoverRegistry.java`（新） | WorldSavedData 全局覆盖板注册表 |
| `terminal/StockMonitorTerminal.java`（新） | GT MTE 统计终端（免电，networkId NBT 读写，IInventory/ISidedInventory 全实现） |
| `terminal/StockMonitorTerminalGui.java`（新） | MUI2 GUI（发信器+覆盖板双列表 + 编辑子面板 + Nexus 连接） |
| `terminal/StockMonitorTerminalWirelessEndpoint.java`（新） | Nexus WirelessBindableEndpoint 包装 |
| `cover/stockmonitor/StockMonitorCover.java` | doCoverThings 加 upsert、onCoverRemoval 加 remove、新增 markCoverDirty() |
| `cover/stockmonitor/gui/StockMonitorCoverGui.java` | fix6 SyncValue 实时刷新 + fix10 模式切换 SyncValue |
| `cover/stockmonitor/gui/StockMonitorPhantomSlot.java`（新，fix7） | phantom 槽不显示数量 + Shift 左键取消 |
| `cover/stockmonitor/ae/AeStockReader.java`（fix7） | 流体分支遍历+getFluidID 比较 |
| `mixin/gt/MixinProcessingLogicSpeed.java`（fix9） | 输出数组 null 检查 |
| `CommonProxy.java` | 注册 StockMonitorTerminal（ID 32001）+ 合成配方 |
| `lang/zh_CN.lang` + `lang/en_US.lang` | 终端相关 14 个键 |
| `guidenh/_zh_cn/stock_monitor_terminal.md` + `_en_us/`（新） | GuideNH 页面 |
| `guidenh/_zh_cn/index.md` + `_en_us/index.md` | 功能列表 20→21 |
| `gradle.properties` + `mcmod.info` | 版本号 fix5→fix11 |

---

## 3.19.0-fix5 - 三大模块实测问题全量修复（维护仓线程/覆盖板流体与断开与Nexus原生UI/终端原生流体）

> 作者：wztwzt | 更新时间：2026-09-10 | 基于 3.19.0-fix4

### 修复：万能维护仓线程数增加后机器停止工作（P0）

- 问题：维护仓 GUI 把线程数设为 >1 后，机器完全停止工作（线程=1 时正常）
- 根因（`MixinProcessingLogicSpeed.ae2qol$crossRecipeProcess` 三处）：
  1. `prepareCatalyst(inputItems)` 结果只赋给局部变量 `items`，未写回 `this.inputItems`，导致 `createParallelHelper`/`findRecipeMatches` 仍用旧输入——对催化剂机器配方匹配失败 → NO_RECIPE
  2. 多配方遍历共用同一输入数组：`ParallelHelper.build()` 会原地消耗 `itemInputs`/`fluidInputs`，第一个 recipe 消耗后后续 recipe 用已耗尽输入 → 全部失败
  3. `catch (Throwable)` 静默吞异常，无法定位真正的运行时错误
- 修复：
  1. `this.inputItems = prepareCatalyst(this.inputItems)` 写回字段
  2. 每个 recipe 调用 `helper.setItemInputs(Arrays.copyOf(...))` / `setFluidInputs(Arrays.copyOf(...))` 使用输入副本
  3. catch 块改为 `MyMod.LOG.warn("[AE2-QoL] crossRecipeProcess failed", t)` 打印完整异常栈
- 变更文件：`mixin/gt/MixinProcessingLogicSpeed.java`

### 修复：库存检测覆盖板流体仍识别为物品（P1）

- 问题：物品检测正常，但流体目标仍被识别为物品，库存读数永远为 0
- 根因：`tryRecognizeFluidItem` 只识别三类（ae2fc ItemFluidPacket / ItemFluidDrop、GT ItemFluidDisplay），GT 流体单元、ae2fc 流体存储单元（FCBaseItemCell）等实现 `IFluidContainerItem` 接口的容器未覆盖
- 修复：
  1. 新增 `IFluidContainerItem` 接口识别：`((IFluidContainerItem) item).getFluid(stack)` 读取流体
  2. 新增通用 NBT 兜底：`"FluidStack"` 复合标签（FluidStack.writeToNBT 格式）+ `"Fluid"` 字符串键 + amount
- 变更文件：`cover/stockmonitor/StockMonitorCoverData.java`

### 修复：库存检测覆盖板连接网络后无法断开（P1）

- 问题：点"断开"按钮无效果，服务端仍保持绑定状态
- 根因：断开按钮用 `ButtonWidget.onMousePressed(event -> { coverData.setNetworkId(""); return true; })`——这是**客户端回调**，`setNetworkId("")` 只改了客户端 coverData，服务端未同步（对比连接按钮用 `InteractionSyncHandler` 双端执行）
- 修复：断开按钮改为 `InteractionSyncHandler`（`syncManager.syncValue("disconnect", ...)`），双端执行 `setNetworkId("")`
- 变更文件：`cover/stockmonitor/gui/StockMonitorCoverGui.java`

### 修复：库存检测覆盖板只能关机不能自动开机（P1，联动修复）

- 问题：库存高于阈值时能正常关机，但低于阈值后不会自动开机
- 根因：流体被识别为物品 → `AeStockReader.readStock` 读物品通道（流体数量=0 或物品通道里有"液态氧液滴"物品）→ count 永远高于阈值 → `shouldWork` 永远 false → 永远不触发 `enableWorking()`
- 修复：随"流体识别为物品"修复联动解决；流体正确识别后 count 读流体通道，低于阈值时 `shouldWork=true` → 自动开机
- 变更文件：随 `StockMonitorCoverData.java` 流体识别修复联动

### 增强：库存检测覆盖板网络选择 UI 替换为 Nexus 原生面板（P1）

- 问题：自定义网络选择面板只有网络名+在线状态+关闭按钮，缺少优先级、频道数（0/288）、断开按钮，与 Nexus 原生 UI 体验不一致
- 修复：
  1. 新增 `StockMonitorWirelessEndpoint` 类，实现 Nexus `WirelessBindableEndpoint` 接口（14 个方法：getTargetNetworkId/getWirelessPriority/getRequestedChannels/getBindingPlayerId/getStableEndpointKey/getWirelessGridNode/isWirelessEndpointValid/setWirelessLease + getEndpointWorld/getEndpointDisplayName/getWirelessLeaseStatus/bindToNetwork/unbindFromNetwork/setWirelessPriority），不调用 registerEndpoint，不占用无线频道，仅用于网络选择/绑定
  2. `StockMonitorCoverGui` 网络子面板 builder 改为：Nexus 可用时调用 `WirelessSelectionPanel.build("ae2qol_net_select", endpoint, player, syncManager, true)`（原生面板，含优先级输入框/频道数/断开按钮）；Nexus 不可用时回退自定义 `NetworkSelectPanel`
- 变更文件：`cover/stockmonitor/ae/StockMonitorWirelessEndpoint.java`（新增）、`cover/stockmonitor/gui/StockMonitorCoverGui.java`

### 修复：二合一终端不识别原生流体（P0，用户多次反馈）

- 问题：终端编码样板时把 1000L 液态氧编码成"1,000 液态氧液滴"物品（ae2fc ItemFluidDrop），而非原生流体，合成 CPU 无法识别为流体
- 根因（两处）：
  1. `PatternContainer.convertToAEStack` 流体分支用 `ItemFluidDrop.newAeStack(fs)` 返回 `IAEItemStack`（物品），而非 `IAEFluidStack`
  2. `FluidPatternDetails.writeToStack()` 内部用 `this.inputs`（legacy 物品数组）写 NBT，而 `setInputs` 会把 `IAEFluidStack` 经 `stackConvert` 转成 ItemFluidDrop 存入 legacy——即使 convertToAEStack 返回流体，writeToStack 仍会写成物品
- 修复：
  1. `convertToAEStack` 流体分支改返回 `AEFluidStack.create(fs)`（原生 `IAEFluidStack`）
  2. 绕过 `FluidPatternDetails.writeToStack()`，自己构造 NBT：用 `pattern.getCondensedAEInputs()`/`getCondensedAEOutputs()`（原生流体数组）+ `FluidPatternDetails.writeStackArray()` 写入 "in"/"out" 键，保留 combine/beSubstitute 字段
- 变更文件：`merged/PatternContainer.java`

### 工程：版本号升级 + 文档同步

- `gradle.properties`：`modVersion = 3.19.0-fix4` → `3.19.0-fix5`
- 三份文档同步更新：
  - `docs/AE库存检测覆盖板-开发指南.md` v0.6 → v0.7（新增 B10/B11/B12/B13 修复记录）
  - `docs/AE2-QoL流体识别问题调研与解决方案.md`（新增 fix5 终端原生流体彻底修复状态）
  - `docs/万能维护仓-并行速度与线程跨配方并行-方案.md` v1.1 → v1.2（新增 crossRecipeProcess 三处根因修复）
- 构建命令：`$env:JAVA_HOME="E:\java17"; .\gradlew.bat build --offline -x spotlessCheck -x spotlessJavaCheck`
- 构建产物：`build/libs/AE2-QoL-3.19.0-fix5.jar`（1,036,596 字节，编译通过）

### 变更文件

- `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinProcessingLogicSpeed.java`：crossRecipeProcess 补 prepareCatalyst 写回 + 输入副本 + catch 日志
- `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/StockMonitorCoverData.java`：流体识别扩展 IFluidContainerItem + 通用 NBT 兜底
- `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/gui/StockMonitorCoverGui.java`：断开按钮改 InteractionSyncHandler + Nexus 原生面板
- `src/main/java/com/wztwzt/ae2_qof/cover/stockmonitor/ae/StockMonitorWirelessEndpoint.java`：新增，实现 WirelessBindableEndpoint
- `src/main/java/com/wztwzt/ae2_qof/merged/PatternContainer.java`：convertToAEStack 改原生流体 + 绕过 writeToStack 自己写流体 NBT
- `gradle.properties`：版本号 fix4 → fix5
- `docs/AE库存检测覆盖板-开发指南.md`、`docs/AE2-QoL流体识别问题调研与解决方案.md`、`docs/万能维护仓-并行速度与线程跨配方并行-方案.md`

---

## 3.18.1-fix18 - 供应器选择界面搜索框中文修复 + .ai_cache 加入 gitignore

> 作者：wztwzt | 更新时间：2026-09-04 | 基于 3.18.1-fix17

### 修复：供应器选择界面搜索框中文输入

- 问题：供应器选择界面的搜索框无法正确输入/搜索中文名称，导致玩家难以通过中文名过滤供应器
- 修复：修复搜索框的中文输入处理，支持中文名称过滤
- 变更文件：供应器选择界面相关 GUI 代码

### 工程：.ai_cache 目录加入 gitignore

- 将 `.ai_cache/` 加入 `.gitignore`，避免开发缓存文件污染仓库
- 变更文件：`.gitignore`

### 变更文件

- 供应器选择界面 GUI 代码
- `.gitignore`

---

## 3.18.1-fix17 - 样板自动上传功能优化（中文名称识别 + 搜索框自动填入 + 完整映射表）

> 作者：wztwzt | 更新时间：2026-09-04 | 基于 3.18.1-fix16

### 增强：配方名称识别——NEI 中文名称兜底（方案A）

- 问题：`ClientRecipeNameUtil.mapRecipeHandlerToSearchKey()` 在映射表查不到 overlayId 时，直接返回 `toDisplayString(overlayId)`（英文 id 拆分），供应器选择界面显示 `gt recipe largechemicalreactor` 而非"大型化学反应釜"
- 修复：映射表查不到时，优先调用 `handler.getRecipeName()`（NEI 已提供中文名称），仍为空才 fallback 到英文拆分
- 变更文件：`client/ClientRecipeNameUtil.java`

### 增强：二合一终端搜索框自动填入（搜索框优化）

- 问题：`NeiRecipeCapture.fillMergedTerminal()` 中 `!resolved.equals(recipeMap)` 条件导致映射表查不到时搜索框留空，玩家从 NEI 转移配方后需手动输入机器名过滤
- 修复：
  - `ClientState` 新增 `pendingRecipeCnName` 字段，`extractFrom()` 中捕获 `handler.getRecipeName()` 中文名称
  - `fillMergedTerminal()` 中映射表查不到时用 `pendingRecipeCnName` 兜底，只要非空就填入搜索框
- 变更文件：`client/ClientState.java`、`client/NeiRecipeCapture.java`

### 增强：内置映射表从46条扩充至313条（方案B）

- 问题：内置 `recipe_type_names.json` 只有46条，覆盖率17.6%，大部分多方块机器（工业电解机、煮解池、化工厂、细菌培养缸等）显示英文 id
- 修复：用从 `handlers.json`（GTNH 2.9.0-beta-1 游戏内导出，494个NEI配方处理器）提取的313条完整映射表替换，覆盖 GT/GT++/GTNL/BartWorks/TST/gtnhlanth/GTNHCoreMod 等所有 mod
- 变更文件：`resources/apu/recipe_type_names.json`

### 变更文件

- `client/ClientRecipeNameUtil.java`：getRecipeName 中文兜底
- `client/ClientState.java`：新增 pendingRecipeCnName 字段
- `client/NeiRecipeCapture.java`：捕获中文名 + 搜索框兜底填入
- `resources/apu/recipe_type_names.json`：46条 → 313条

---

## 3.18.1-fix16 - 退出重进绑定丢失修复（P0）+ 服务器兼容性确认

> 作者：wztwzt | 更新时间：2026-09-04 | 基于 3.18.1-fix15

### 修复：退出存档重进后仓室显示"未绑定"（P0）

- 根因：`AdaptiveNetwork.destroy()` 中遍历所有 helpers 调用 `helper.unbind()`，把仓室的 `networkOwner` 设为 null。退出存档时，世界卸载触发所有仓室 `onRemoval` → `unregisterHatch`，最后一个仓室移除后网络为空 → `removeNetwork` → `destroy()` → 所有仓室 `networkOwner=null`。此时若 NBT 保存在 destroy 之后触发，`ae2qolNO` 不会被写入，重进后绑定丢失
- 修复：`destroy()` 只清理网络自身的 `helpers` 列表和 `terminal` 引用，**不修改仓室的绑定状态**。仓室的绑定信息由 NBT 持久化，重进后 `onFirstTick` 自动重新 `registerHatch`
- 变更文件：`hatch/adaptive/AdaptiveNetwork.java`

### 服务器兼容性确认

- `MultiblockRegistry`：register 检查 `world.isRemote`，只注册服务端主机 ✓
- `AdaptiveNetworkManager`：所有操作在 `isServerSide` 检查后执行 ✓
- 网络包（HatchAction/HatchListSync/WirelessHighlight）：均检查 `ctx.side`，服务端用 `scheduleServerTask` 调度 ✓
- 传送：服务端检查团队权限 + `worldServerForDimension` 获取目标世界 ✓
- 客户端类（ClientState/Renderer）：仅在 `ctx.side == CLIENT` 分支引用，服务器不会加载 ✓

---

## 3.18.1-fix15 - 机器识别简化（只认多方块主机，认不到显示仓室本身）

> 作者：wztwzt | 更新时间：2026-09-04 | 基于 3.18.1-fix14

### 修复：子仓列表仍显示输入总线名而非主机名

- 根因：fix14 注册表方案中 `findAttachedMachine` 检查 `mMachine`，但仓室 `onFirstTick` 可能早于主机的结构检查（checkMachine），此时主机 `mMachine=false`，注册表中的主机被跳过，回退到背面邻居（输入总线），显示"编程样板输入总成 MK.II"
- 修复：
  - 去掉 `mMachine` 检查：注册表中的主机均为 `MTEMultiBlockBase`，未成型主机的 center/radius 为默认值，不会误匹配
  - 去掉背面邻居单方块机器返回：不再显示输入总线/能量仓等附属方块名
  - 去掉兜底 4×4×4 搜索：认不到主机就返回 null
  - 返回 null 时，仓室 `onFirstTick` 自动用仓室本身的名字和图标（自适应能源仓/动力仓/激光仓等）
- 变更文件：`hatch/adaptive/AdaptiveHatchHelper.java`

---

## 3.18.1-fix14 - 多方块主机识别重构（全局注册表方案）

> 作者：wztwzt | 更新时间：2026-09-04 | 基于 3.18.1-fix13

### 重构：多方块主机识别——从空间搜索改为全局注册表

- 问题：fix13 的"背面半球两轮搜索"仍是空间搜索，当两个多方块结构紧挨着且主机在仓室背面半球内距离相近时，仍可能选错主机；17×17×17 遍历性能也较差
- 方案：借鉴 GTNL `EnergyMonitorRegistry`，改用全局注册表
  - 新建 `MultiblockRegistry`：全局 `Set<MTEMultiBlockBase>`，线程安全（ConcurrentHashMap 支撑）
  - 新建 `MixinCommonBaseMetaTileEntityMultiblockRegistry`：注入 `CommonBaseMetaTileEntity.handleFirstTick(boolean)` TAIL，所有多方块主机加载时自动注册
  - 重写 `findAttachedMachine`：
    1. **查注册表**：遍历所有已注册主机，过滤同维度 + `mMachine=true`，用 `center/radius` 判断仓室坐标是否在主机范围内，选最近的返回。紧邻多方块不会干扰——每个主机有自己的 center 和 radius，仓室坐标只落在一个主机范围内
    2. **背面邻居**：单独放地上的单方块机器（非自适应仓室、非多方块主机）直接返回，显示本身名字和图标
    3. **兜底**：周围 4×4×4 内找第一个非自适应仓室的 MTE
- `MTEEnhancedMultiBlockBase` 用 `getCenter()/getApproximateRadius()`；普通 `MTEMultiBlockBase` 用主机坐标 + 默认 radius=4
- 注册表每次查询前 `cleanupInvalid()` 自动清理已拆除/已失效的主机

### 变更文件

- 新增 `hatch/adaptive/MultiblockRegistry.java`：全局多方块主机注册表
- 新增 `mixin/gt/MixinCommonBaseMetaTileEntityMultiblockRegistry.java`：Mixin 自动注册
- 修改 `mixins.ae2_qof.json`：注册新 Mixin
- 修改 `hatch/adaptive/AdaptiveHatchHelper.java`：重写 findAttachedMachine

---

## 3.18.1-fix13 - 多方块主机识别增强（背面半球优先）

> 作者：wztwzt | 更新时间：2026-09-04 | 基于 3.18.1-fix12

### 增强：多方块主机识别——紧邻多方块场景下避免选错主机

- 问题：当两个多方块结构紧挨着时，17×17×17 全方向搜索可能选到旁边机器的主机（距离更近），而非仓室所属机器的主机
- 修复：两轮搜索
  - 第一轮：只搜索**背面半球**（主机位置与仓室背面方向的点积 > 0），即主机必须在仓室背面方向，排除正面/侧面的其他机器
  - 第二轮：若背面半球内未找到主机，全方向搜索兜底（兼容特殊结构/单方块机器）
- 对 `MTEEnhancedMultiBlockBase` 用 `center` 计算方向点积；对普通 `MTEMultiBlockBase` 用方块坐标计算
- 变更文件：`hatch/adaptive/AdaptiveHatchHelper.java`

---

## 3.18.1-fix12 - 机器识别修复 + 监控Tab遮挡修复 + 仓室GUI遮挡修复

> 作者：wztwzt | 更新时间：2026-09-04 | 基于 3.18.1-fix11

### 修复：子仓列表机器名识别错误（多方块主机识别）

- 根因：`findAttachedMachine` 第 1 步检查背面邻居时，只要是非自适应仓室的 GT MTE 就直接返回。当背面是输入总线/输出总线等附属方块时，直接返回了附属方块名（如"编程样板输入总成 MK.II"），未继续搜索多方块主机
- 修复：
  - 背面邻居是 `MTEMultiBlockBase` 且 `mMachine=true` 时才直接返回（背面即主机）
  - 背面邻居是普通单方块机器时，记为 `singleBlockCandidate`，不直接返回
  - 17×17×17 范围搜索多方块主机，找到则优先返回主机
  - 未找到主机时返回 `singleBlockCandidate`（单方块机器）
  - 最后兜底 9×9×9 搜索任意 MTE

### 修复：监控 Tab 底部被玩家背包遮挡

- 根因：4 个灰色区块标签（总览/能量变化/预测/瞬时速率）各占 10px+padding，内容总高约 227px，超出面板可见区域（约 200px），瞬时输出速率和能量活动被背包遮挡
- 修复：
  - 去掉 4 个灰色区块标签（分隔线已能区分区块，且灰色小字本身可读性差）
  - `childPadding` 4→2，进一步压缩行间距
  - 内容总高降至约 160px，全部行可见

### 修复：4个仓室 GUI 电网电量行被背包遮挡

- 根因：`Flow.column().childPadding(6).top(10)` 间距过大，电量行 y 坐标过低，与玩家背包第一行物品槽重叠，"EU" 后缀被遮挡
- 修复：`childPadding(6).top(10)` → `childPadding(2).top(6)`，整体上移 20px，电量行不再被遮挡

### 变更文件

- `hatch/adaptive/AdaptiveHatchHelper.java`：findAttachedMachine 多方块主机优先逻辑
- `hatch/adaptive/AdaptiveNetTerminal.java`：监控 Tab 去掉区块标签 + 压缩 padding
- `hatch/adaptive/AdaptiveNetHatch.java`：仓室 GUI 上移
- `hatch/adaptive/AdaptiveNetDynamoHatch.java`：同上
- `hatch/adaptive/AdaptiveNetLaserHatch.java`：同上
- `hatch/adaptive/AdaptiveNetLaserTargetHatch.java`：同上

---

## 3.18.1-fix11 - 传送修复 + UI全面美化 + 仓室电量单位修复

> 作者：wztwzt | 更新时间：2026-09-04 | 基于 3.18.1-fix10

### 修复：传送按钮完全不可用（P0）

- 根因：`AdaptiveNetTerminal` 用 `Minecraft.getMinecraft().gameSettings.keyBindSneak.getIsKeyPressed()` 检测 Shift，GUI 打开时 keyBind 状态不更新，永远返回 false，导致永远发高亮而非传送
- 修复：改用 ModularUI `event.isShiftKeyDown()`（与 GTNL EnergyMonitorGui 同款用法）

### 修复：跨维度传送位置错误

- 根因：`HatchActionPacket.handleTeleport` 用 `new Teleporter(targetWorld)` 默认传送器，会搜索/建造下界传送门，不放到指定坐标
- 修复：匿名类重写 `placeInPortal` 直接 setLocationAndAngles 到目标坐标，覆写 `placeInExistingPortal` 返回 false 强制不走现有传送门

### 新增：传送权限提示

- 非团队成员 Shift+点击"定位"按钮时，聊天提示红色"无传送权限：不属于该电网团队"，避免静默失败

### 美化：子仓列表 Tab 全面重构

- 行占满宽度（`CONTENT_W-8`）+ 左对齐，消除左侧大片空白
- 行高 18→20px，图标垂直居中，行间 1px 分隔线
- 行文本三段式布局：`[图标] 机器名(白色左对齐,超16字符截断)  EU/t(绿色右对齐)  tier(彩色右对齐)`
- 去掉 `[E]/[LS]/[D]/[LT]` 类型标签（图标已区分）
- 机器名从 tooltip 移到主行显示，tooltip 保留全名+坐标+容量+操作提示
- 传送+高亮合并为行末右侧"定位"按钮（42×18，BUTTON_CLEAN 背景），左键=高亮、Shift+左键=传送，热区明确可点
- 标题行合并标题（左）+ 总数（右），去掉单独的"绑定总数"行
- ListWidget 高度 170→148px，footer 前加分隔线，不再被背包遮挡

### 美化：监控 Tab 分区重构

- 数据分 4 区块：总览 / 能量变化 / 预测 / 瞬时速率，每区块灰色小字标签 + 分隔线
- 每行标签左对齐、数值右对齐（`Flow.row` + `textAlign(CenterRight)`），不再纯文字堆砌
- 变化率加 ↑/↓ 箭头（绿充/红放），直观显示充放电状态
- 耗空时间 >100 年简化为 `>100年（近似无穷）`，避免 672376年4月24天... 超长文本
- 计数按钮（常规/科学/KMG）移到标题行右侧，尺寸 64×14，统一样式

### 美化：状态 Tab

- 标题（左）+ 频率（右）合并为一行，节省空间

### 修复：4个仓室 GUI 电网电量缺少单位

- 根因：`formatEU(gridEU)` 返回 `"1.11E"`（KMG 格式 E=exa），但未拼接 `" EU"` 后缀，显示为 `"电网电量: 1.11E"` 看似被截断
- 修复：4 个仓室（能量仓/动力仓/激光源仓/激光靶仓）GUI 电网电量行加 `" EU"` 后缀

### 变更文件

- `network/HatchActionPacket.java`：自定义 Teleporter + 传送权限提示
- `hatch/adaptive/AdaptiveNetTerminal.java`：Shift 检测修复 + 列表Tab重构 + 监控Tab重构 + 状态Tab合并
- `hatch/adaptive/AdaptiveNetHatch.java`：电量加 EU 后缀
- `hatch/adaptive/AdaptiveNetDynamoHatch.java`：同上
- `hatch/adaptive/AdaptiveNetLaserHatch.java`：同上
- `hatch/adaptive/AdaptiveNetLaserTargetHatch.java`：同上

---

## 3.18.1-fix10 - 重复条目去重 + int溢出修复 + 文字溢出修复 + Footer截断修复

> 作者：wztwzt | 更新时间：2026-09-04 | 基于 3.18.1-fix9

### 修复：子仓列表重复条目

- 根因：chunk 卸载后重建 TileEntity，旧 helper 仍留在 network 的 LinkedHashSet 中，新 helper 重复注册
- 修复：`sendHatchListSync()` 新增按坐标去重（`LinkedHashSet<String>` 按 x,y,z,dim 去重），相同位置只保留一条

### 修复：Capacity 显示 -2,147,483,648（int 溢出）

- 根因：`eut` 字段为 `int`，`(int)(V[14] * amps)` 对 tier ≥14 溢出为 Integer.MIN_VALUE
- 修复：`eut` 全链路改为 `long`：
  - `HatchListCache.HatchEntry.eut` → `long`
  - `HatchListSyncPacket.toBytes/fromBytes` → `writeLong/readLong`
  - `sendHatchListSync()` 计算去掉 `(int)` 强转

### 修复：机器图标/名称错误（machineMetaId short 截断）

- 根因：`machineMetaId` 为 `short`，GT MetaTileEntity ID 可能超过 Short.MAX_VALUE (32767)
- 修复：`machineMetaId` 全链路改为 `int`：
  - `AdaptiveHatchHelper.machineMetaId` → `int`，`setMachineInfo` 参数 → `int`
  - `HatchListCache.HatchEntry.machineMetaId` → `int`
  - `HatchListSyncPacket` → `writeInt/readInt`
  - NBT 存储 → `setInteger/getInteger`
  - 4 个舱室 `onFirstTick` 去掉 `(short)` 强转

### 修复：Status/Monitor Tab 文字溢出

- 根因：`buildStatusTab`/`buildMonitorTab` 的 `Flow.column().coverChildren()` 不约束宽度，中文文字超出面板边界
- 修复：改为 `Flow.column().size(CONTENT_W, 0).childPadding(4)` 显式约束宽度

### 修复：子仓列表 Footer 被背包遮挡

- 根因：ListWidget 高度 188px 过大，footer 位置过低被玩家背包覆盖
- 修复：ListWidget 高度 188px → 170px

### 变更文件

- `hatch/adaptive/AdaptiveHatchHelper.java`：machineMetaId 改 int + NBT 改 setInteger/getInteger
- `hatch/adaptive/HatchListCache.java`：eut 改 long + machineMetaId 改 int
- `network/HatchListSyncPacket.java`：eut 改 writeLong/readLong + machineMetaId 改 writeInt/readInt
- `hatch/adaptive/AdaptiveNetTerminal.java`：sendHatchListSync 去重 + int 溢出修复 + Flow 宽度约束 + ListWidget 高度调整
- `hatch/adaptive/AdaptiveNetHatch.java`：onFirstTick 去掉 (short) 强转
- `hatch/adaptive/AdaptiveNetDynamoHatch.java`：同上
- `hatch/adaptive/AdaptiveNetLaserHatch.java`：同上
- `hatch/adaptive/AdaptiveNetLaserTargetHatch.java`：同上
- `hatch/adaptive/AdaptiveHatchHelper.java`：findAttachedMachine 重写（多方块控制器优先）

---

## 3.18.1-fix9 - GT数据棒链接 + 跨维度传送修复

> 作者：wztwzt | 更新时间：2026-09-03 | 基于 3.18.1-fix8

### 新增：GT 数据棒链接（MergedTerminal）

- 在二合一终端的接口列表中，手持 GT 数据棒点击接口条目
- 自动将接口方块坐标写入数据棒 NBT（type/x/y/z/dim）
- 数据棒显示名称更新为 "Crafting Input Buffer Link Data Stick (x, y, z)"
- 聊天提示链接成功

### 修复：跨维度传送

- `HatchActionPacket.handleTeleport` 修复传送后位置同步
- 传送成功后发送聊天提示（绿色文字显示目标坐标和维度）

### 变更文件

- `merged/ContainerMergedTerminal.java`：GT 数据棒链接逻辑
- `network/HatchActionPacket.java`：传送后聊天提示 + 位置同步修复

---

## 3.18.1-fix8 - 高亮增强 + 所有者显示 + 机器名/图标 + Footer修复

> 作者：wztwzt | 更新时间：2026-09-03 | 基于 3.18.1-fix7

### 修复：高亮增强（更容易发现目标仓室）

- 线宽：3px → 5px（更粗更醒目）
- Alpha 范围：0.40-0.70 → 0.60-1.0（更亮）
- Box 尺寸：1.06 → 1.12（更大覆盖范围）
- 高亮持续时间：5秒 → 10秒（更充裕的观察时间）

### 修复：Status Tab 所有者显示玩家名

- 新增 `ownerNameSync` (StringSyncValue) 同步玩家名到客户端
- 不再显示截断 UUID（如 "abc12345..."），改为显示在线玩家名（如 "wztwztwzt"）
- 离线时回退显示 UUID 前8位

### 修复：子仓列表显示所连接机器名 + 机器图标

- `AdaptiveHatchHelper` 新增 `machineMetaId` / `machineName` 字段
- 新增 `findAttachedMachine()` 方法：查找舱室背后的 GT 机器
  - 优先检查 `getBackFacing()` 方向的相邻方块
  - 回退搜索 8 格范围内的所有 GT 机器
- 所有 4 个舱室（Energy/Dynamo/Laser/LaserTarget）的 `onFirstTick` 改用 `findAttachedMachine`
- `HatchEntry` 新增 `machineMetaId` 字段（用于客户端图标渲染）
- 列表图标改用机器图标（`machineMetaId`）而非舱室图标（`metaId`）

### 修复：Footer "输入仓/输出仓" 遮挡修复

- 子仓列表高度：200px → 188px，确保底部统计行可见

### 变更文件

- `client/render/WirelessHighlightRenderer.java`：线宽/Alpha/Box 尺寸
- `network/HatchActionPacket.java`：高亮持续时间 100→200 tick
- `hatch/adaptive/AdaptiveHatchHelper.java`：+machineMetaId/machineName +findAttachedMachine
- `hatch/adaptive/AdaptiveNetHatch.java`：onFirstTick 改用 findAttachedMachine
- `hatch/adaptive/AdaptiveNetDynamoHatch.java`：同上
- `hatch/adaptive/AdaptiveNetLaserHatch.java`：同上
- `hatch/adaptive/AdaptiveNetLaserTargetHatch.java`：同上
- `hatch/adaptive/HatchListCache.java`：HatchEntry +machineMetaId
- `network/HatchListSyncPacket.java`：序列化 machineMetaId
- `hatch/adaptive/AdaptiveNetTerminal.java`：+ownerNameSync + 机器名/图标 + footer 修复

---

## 3.18.1-fix7 - owner同步 + 跨维度传送 + ownerName + 列表布局修复

> 作者：wztwzt | 更新时间：2026-09-03 | 基于 3.18.1-fix6

### 修复：客户端 owner/frequency 同步（高亮+传送前提）

- `buildUI` 新增 `StringSyncValue ownerSync` 显式同步 owner UUID 到客户端
- `buildHatchListTab` 签名加 `ownerSync` + `frequencySync` 参数
- 行点击回调改用 `ownerSync.getValue()` 而非 `networkOwner`（解决客户端 MTE 字段为 null 导致 packet 发不出去的问题）

### 新增：跨维度传送

- `HatchActionPacket.handleTeleport` 加 `dim` 参数
- 同维度：`setPositionAndUpdate`（原有逻辑）
- 跨维度：`transferPlayerToDimension` + `setPositionAndUpdate`

### 新增：仓室归属玩家名（ownerName）

- `HatchListCache.HatchEntry` 新增 `ownerName` 字段
- `HatchListSyncPacket` 协议新增 `ownerName` 序列化（UTF8String）
- 服务端 `sendHatchListSync` 解析 ownerName（在线用玩家名，离线用 UUID 前8位）
- 仓室列表 tooltip 新增归属行：`归属: PlayerName`

### 优化：子仓列表 Tab 布局修复

- 标题高度 16→14，列表高度 185→200，footer 不再被遮挡
- 整体布局：标题14 + 总数12 + 列表200 + footer12 = 238px（< 260px，留有余量）

### 变更文件

- `hatch/adaptive/AdaptiveNetTerminal.java`：ownerSync + ownerName + 布局修复
- `hatch/adaptive/HatchListCache.java`：HatchEntry.ownerName
- `network/HatchListSyncPacket.java`：ownerName 序列化
- `network/HatchActionPacket.java`：跨维度传送

---

## 3.18.1-fix6 - 团队归一 + 仓室图标 + UI 全面优化

> 作者：wztwzt | 更新时间：2026-09-03 | 基于 3.18.1-fix5

### 新增：SpaceProjectManager 团队归一

- 新增 `AdaptiveTeamHelper.java`：`resolveLeader(UUID)` / `resolveMembers(UUID)` / `isMemberOf(player, leader)`
- `AdaptiveNetworkManager` 所有 owner 入口（`getOrCreateNetwork`/`getNetwork`/`removeNetwork`/`registerTerminal`/`registerHatch`/`unregisterTerminal`/`unregisterHatch`/`updateAllHatches`/`migrateHatches`）均先 `resolveLeader` 归一
- `AdaptiveNetTerminal.onFirstTick`：`networkOwner = AdaptiveTeamHelper.resolveLeader(aBase.getOwnerUuid())`
- `HatchActionPacket` 传送权限：`player.getUniqueID().equals(uuid)` → `AdaptiveTeamHelper.isMemberOf(player.getUniqueID(), uuid)`
- `ItemNetworkDataStick.writeData`：写入时存 leader UUID，团队成员共享闪存

### 新增：仓室物品图标

- 仓室列表每行左侧渲染16x16仓室物品图标（`ItemDrawable`）
- 通过 `GregTechAPI.METATILEENTITIES[metaId].getStackForm(1L)` 获取 ItemStack
- `metaId == -1` 时显示空占位

### 优化：UI 全面优化

- **统一宽度**：所有 Tab 控件宽度统一为 `CONTENT_W` (330px)，修复 Monitor Tab 350px 溢出
- **分隔线**：每个 Tab 标题下方、各区块之间加1px分隔线（`separator()`）
- **UUID 截断**：Status Tab owner 显示截取前8位 + `...`
- **Settings Tab**：4个 slot 行之间加分隔线
- **Monitor Tab**：模式按钮加 `BUTTON_CLEAN` 背景
- **Hatch List Tab**：
  - 行布局改为 `[图标] [文本]`，图标16x16 + 4px间距
  - 名称移入 tooltip 第一行，主显示只显示 `[D] amps tier EU/t`
  - 列表高度从175增至185
  - footer 分隔符改为 ` | `
- **间距统一**：childPadding 统一为4px

### 变更文件

- 新增 `hatch/adaptive/AdaptiveTeamHelper.java`
- `hatch/adaptive/AdaptiveNetworkManager.java`：所有 owner 入口 resolveLeader
- `hatch/adaptive/AdaptiveNetTerminal.java`：onFirstTick 归一 + UI 全面重写 + 图标
- `network/HatchActionPacket.java`：传送权限改团队
- `item/ItemNetworkDataStick.java`：写入时存 leader

---

## 3.18.1-fix5 - 仓室高亮颜色区分

> 作者：wztwzt | 更新时间：2026-09-03 | 基于 3.18.1-fix4

### 优化：仓室高亮按类型着色

- `WirelessHighlightPacket` 协议每个位置新增 `type` 字段（byte），从 `{dim,x,y,z}` 扩展为 `{dim,x,y,z,type}`
- `HatchActionPacket.handleHighlight` 传递 `hatchType` 给高亮包
- `WirelessHighlightRenderer` 按仓室类型着色：
  - 0 动力仓 → 橙色 (255, 160, 0)
  - 1 能量仓 → 蓝色 (0, 160, 255)
  - 2 激光源 → 紫色 (180, 0, 255)
  - 3 激光目标 → 黄色 (255, 255, 0)
- 保留脉冲 alpha 效果（0.40~0.70）
- 无线收发器高亮兼容4元素数组，默认橙色

### 变更文件

- `network/WirelessHighlightPacket.java`：协议加 type 字段
- `network/HatchActionPacket.java`：传递 hatchType
- `client/render/WirelessHighlightRenderer.java`：按类型着色

---

## 3.18.1-fix4 - 多人共享电网支持

> 作者：wztwzt | 更新时间：2026-09-03 | 基于 3.18.1-fix3

### 新增：多人共享电网仓室列表同步

- `AdaptiveNetwork` 新增 `activeViewers` 集合，跟踪当前打开终端 GUI 的所有玩家 UUID
- `addViewer()` 注册观看者时自动标记 `hatchListDirty`，确保新观看者能收到同步
- `removeViewer()` 注销观看者
- `buildUI()` 中注册当前打开 GUI 的玩家为观看者
- `ModularPanel.onCloseAction()` 关闭 GUI 时自动注销观看者
- `sendHatchListSync()` 改为遍历 `network.getActiveViewers()` 发送给所有观看者，而非仅发送给 networkOwner
- 新增 `findPlayerByUUID()` 工具方法，通过 UUID 查找在线玩家

### 变更文件

- `hatch/adaptive/AdaptiveNetwork.java`：`activeViewers` 集合 + `addViewer/removeViewer/getActiveViewers`
- `hatch/adaptive/AdaptiveNetTerminal.java`：`buildUI` 注册/注销观看者 + `sendHatchListSync` 多播 + `findPlayerByUUID`

---

## 3.18.1-fix3 - 子仓 BigInteger 简化 + 仓室列表同步频率优化

> 作者：wztwzt | 更新时间：2026-09-03 | 基于 3.18.1-fix2

### 优化：子仓 BigInteger 简化

- `AdaptiveHatchHelper` 新增 `getGridEULong(UUID)` 和 `getGridEUBigInteger(UUID)` 静态工具方法，封装 BigInteger→long 转换
- 4 个子仓（能量/激光源/动力/激光目标）的 `addEUToGlobalEnergyMap` 调用改用 long 重载，移除 `BigInteger.valueOf()` 包装
- 4 个子仓 GUI 和 WAILA 中电网余额显示改用 `getGridEULong()`，移除冗余 BigInteger 转换
- 4 个子仓文件移除 `import java.math.BigInteger`（不再需要）
- Terminal 的 `tickStats` 和 `gridEUSync` 也改用 `getGridEULong()`
- 监控 Tab 总电量显示仍保留 BigInteger 路径（后期整合包可能超 long 上限）
- 无线终端 BigInteger 保持不变（终局手段可能超 long）

### 优化：仓室列表同步频率

- `AdaptiveNetTerminal.onPreTick` 新增 `syncTick` 计数器
- 仓室列表同步从每 tick 检查改为每 20 tick（1秒）检查一次 dirty 标记
- 减少网络数据包发送频率，降低网络流量

### 变更文件

- `hatch/adaptive/AdaptiveHatchHelper.java`：新增 `getGridEULong` / `getGridEUBigInteger` 工具方法
- `hatch/adaptive/AdaptiveNetHatch.java`：BigInteger→long 简化
- `hatch/adaptive/AdaptiveNetLaserHatch.java`：同上
- `hatch/adaptive/AdaptiveNetDynamoHatch.java`：同上
- `hatch/adaptive/AdaptiveNetLaserTargetHatch.java`：同上
- `hatch/adaptive/AdaptiveNetTerminal.java`：BigInteger 简化 + syncTick 计数器

---

## 3.18.1-fix2 - WorldData 持久化 + 真实负载 EU/t + 耗尽时间汉化 + 彩色 Tier

> 作者：wztwzt | 更新时间：2026-09-03 | 基于 3.18.1-fix1

### 新增：电网监控数据 WorldData 持久化（修复重启丢数据）

- `GridEnergyStats` 在 `AdaptiveNetwork` 中从 `final` 改为可替换，新增 `replaceStats()` 方法
- `GridEnergyWorldData` 新增 `setStats()` 方法，支持写入
- `AdaptiveNetworkManager` 新增 `serverWorld` 字段，`registerTerminal/registerHatch` 新增 `World` 参数重载
- 终端/仓室在 `onFirstTick` 中存储 `World` 引用，注册时传入 Manager
- 网络创建时自动从 `GridEnergyWorldData` 加载历史 stats
- `AdaptiveNetwork.tickStats()` 每 6000 tick（5分钟）自动保存到 WorldData
- `removeNetwork()` 和 `serverStopping()` 触发即时保存
- 服务端关闭时 `CommonProxy.serverStopping()` 调用 `saveAllStats()`

### 新增：仓室真实负载 EU/t 显示

- `AdaptiveHatchHelper` 新增 `realFlowEUt` 字段 + getter/setter
- 4 种仓室在 `onPostTick` 中计算实际 EU/t 流量：
  - 能量输入仓：`lastStoredEU - currentStored`（消耗量）
  - 激光源仓：同上
  - 动力仓：`stored`（发送量）
  - 激光目标仓：同上
- `HatchEntry` 新增 `realFlowEUt` 字段
- `HatchListSyncPacket` 协议新增 `realFlowEUt` 字段（readInt/writeInt）
- 仓室列表 Tab 主显示改为真实流量，tooltip 显示 Capacity + Flow 双行
- 排序改为按真实流量降序

### 优化：耗尽时间全中文显示

- `formatDuration()` 英文单位改为中文：`y→年`、`d→天`、`h→时`、`m→分`、`s→秒`
- `月` 保持不变（原本就是中文）
- 无限时间显示 `Infinite` → `∞`

### 变更文件

- `hatch/adaptive/AdaptiveNetworkManager.java`：World 持久化 + `saveStatsForKey` + `saveAllStats`
- `hatch/adaptive/AdaptiveNetwork.java`：`replaceStats` + `saveCounter` 定期保存
- `hatch/adaptive/GridEnergyWorldData.java`：`setStats()` 方法
- `hatch/adaptive/AdaptiveHatchHelper.java`：`realFlowEUt` 字段
- `hatch/adaptive/HatchListCache.java`：`HatchEntry.realFlowEUt` + 排序改为按真实流量
- `hatch/adaptive/AdaptiveNetTerminal.java`：`world` 字段 + `formatDuration` 汉化 + 真实流量显示
- `hatch/adaptive/AdaptiveNetHatch.java`：`world` 字段 + 真实流量计算
- `hatch/adaptive/AdaptiveNetLaserHatch.java`：同上
- `hatch/adaptive/AdaptiveNetDynamoHatch.java`：同上
- `hatch/adaptive/AdaptiveNetLaserTargetHatch.java`：同上
- `network/HatchListSyncPacket.java`：`realFlowEUt` 协议字段
- `CommonProxy.java`：`serverStopping` 中调用 `saveAllStats()`

---

## 3.18.1-fix1 - 自适应电网监控数据正确性修复 + 子仓列表优化

> 作者：wztwzt | 更新时间：2026-09-03 | 基于 3.18.0

### 修复：监控数据单位错误（审计核对）

- **1h 平均速率大 20 倍**：`AdaptiveNetTerminal` L722 `÷3600L` → `÷72000L`（应除以 tick 数而非秒数）
- **10min 平均速率大 20 倍**：同文件 L732 `÷600L` → `÷12000L`
- **瞬时速率大 100 倍**：`GridEnergyStats.getInstantInputRate()/getInstantOutputRate()` 返回 `bufferSumInput/Output`（100 tick 累计），现改为 `÷100` 输出真实 EU/t

### 修复：电压切换后子仓列表不刷新

- `AdaptiveNetwork.setVoltageTier()`/`setHatchTier()`/`setHatchAmps()` 在修改配置后未标记 `hatchListDirty`，导致子仓 Tab 的 tier/amps/EU-t 停留旧值
- 三个方法均添加 `markHatchListDirty()` 调用

### 优化：子仓列表去掉 50 行上限

- 移除 `MAX_HATCH_COORD_DISPLAY` (50) 硬编码上限和 `...N more` 截断提示
- `ListWidget` 全量渲染所有条目（本身支持滚动），通常几十条性能可接受

### 修复：电网监控时间窗口常量（此前轮次修复，本轮确认）

- `GridEnergyStats.WINDOW_10M` 600 → 12000（10 分钟 = 12000 tick）
- `GridEnergyStats.WINDOW_1H` 3600 → 72000（1 小时 = 72000 tick）
- 预计耗尽时间公式移除 `×20L`（avgOut 已是 EU/tick）

### 修复：ams 溢出 + 仓室破坏残留（此前轮次修复，本轮确认）

- `HatchListSyncPacket` amps 编码从 `writeByte` 改为 `writeShort`（防止 256A 溢出）
- `AdaptiveNetDynamoHatch`/`AdaptiveNetLaserTargetHatch` 添加 `onRemoval()` 调用 `unregisterHatch()`
- `AdaptiveNetwork.setHatchAmps`/`AdaptiveHatchHelper.setAmps` 钳制 `Math.max(1, amps)`
- 4 个仓室 `onFirstTick` 添加 `getStackForm()` null 检查（非标准 MTE 无 StackForm 时 fallback 用翻译 key）
- `formatDuration` 月份显示 `"mo "` → `"月 "`

### 变更文件

- `hatch/adaptive/AdaptiveNetTerminal.java`：÷72000/÷12000、去掉 50 行上限
- `hatch/adaptive/GridEnergyStats.java`：瞬时速率 ÷100
- `hatch/adaptive/AdaptiveNetwork.java`：三处 setter 加 markHatchListDirty

---

## 3.18.0 - 自适应电网系统大重构（4仓类型 + 4-tab UI + 权限指纹 + 自动迁移）

> 作者：wztwzt | 更新时间：2026-08-31 | 基于 3.17.1

### 修复：自适应终端 UI 三重Bug

- **Tab切换**：emoji `TextWidget` → `PageButton` + `GuiTextures.TAB_LEFT` + `PagedWidget.Controller`，只有当前tab内容可见
- **仓室等级检测**：`detectTierFromItemName(registryName)` → `GregTechAPI.METATILEENTITIES[damage].getInputTier()`，从GT MTE元数据读取等级
- **电流检测**：`HatchType.defaultAmps` → `mte.maxAmperesIn()`/`mte.maxAmperesOut()`，从GT MTE原型读取实际电流
- **客户端同步**：`hatchTiers[]`/`hatchAmps[]` 通过 `IntSyncValue` 同步到客户端，UI实时更新
- **空槽显示**：空槽显示"ULV 0V 0A"而非默认值
- **数据棒绑定**：普通右键终端+数据棒→读取数据棒频率并设置到终端
- **面板尺寸**：350×340→350×240，消除底部空白
- **Shift右键自动初始化**：终端未绑定时shift+右键数据棒，自动用玩家UUID初始化终端再写入
- **Tab tooltip中文**：主状态/自适配设置/频率设置
- **Tab偏移修复**：CONTENT_X从29改为32，tab和内容不再重叠
- **空槽文字颜色**：GRAY→YELLOW，在浅蓝面板上更清晰

### 修复：无线输入终端 UI

- 面板高度 280→340，参考表不再与背包重叠
- 所有文字统一白色，提高可读性
- 默认电压档位从LV(1)改为ULV(0)

### 数据棒tooltip完善

- 新增三条使用说明：Shift+右键终端写入、右键仓室绑定、右键终端读取

### 重构：自适应电网终端 4-tab UI

- 终端 GUI 重写为 **4 标签页** 布局，左栏 4 个图标按钮切换：
  - **主状态**（⚙）：显示频率、所有者、4 种仓的加载数量/安培/电压
  - **自适配设置**（⚡）：4 个仓插槽（动力仓/能源仓/激光源仓/激光靶仓），各放 64 个自动配置
  - **权限控制**（🔒）：指纹槽位，玩家 UUID 指纹权限
  - **频率设置**（📡）：宽幅文本输入框，范围 `[-2147483648, 2147483647]`
- 频率改变时自动触发**仓室迁移**：原同频段适配仓自动跟随新频率
- 5 个物品槽位（4 仓 + 1 指纹）

### 新增：自适应电网动力仓（ID 32104）

- 能量输出仓，继承 `MTEHatchDynamo`，1A 输出
- 每 4 tick 将存储 EU 推入无线电网（`WirelessNetworkManager.addEUToGlobalEnergyMap`）
- 使用 GT 内置无线 overlay 贴图

### 新增：自适应电网激光靶仓（ID 32105）

- 高功率能量输出仓，继承 `MTEHatchDynamo`，256A 输出，双倍电压
- 最大存储 `V[tier] × 16`

### 架构重构：组合模式（Composition）

- **删除** `AdaptiveNetHatchBase.java`（原继承 `MTEHatchEnergy`，无法同时支持动力仓/激光靶仓的 `MTEHatchDynamo` 父类）
- **新增** `AdaptiveHatchHelper.java`：提取通用绑定/NBT/电压同步/数据棒逻辑
- **新增** `HatchType.java`：4 种仓类型枚举 + 物品注册名关键字识别
- 各子仓持有 `AdaptiveHatchHelper` 实例，委托调用绑定方法
- `AdaptiveNetwork` 支持 4 种仓分别计数 + 迁移支持

### 修复

- 仓室安培数从物品注册名读取（不再硬编码默认值）
- 终端 `getSizeInventory` 改为 5（适配 5 槽位）
- `isUseableByPlayer` 支持指纹权限控制

### 材质

- 动力仓/激光靶仓使用 `OVERLAYS_ENERGY_OFF_WIRELESS`（静态无线 overlay）
- 能源仓/激光源仓使用 `OVERLAYS_ENERGY_ON_WIRELESS`（动画无线 overlay）

### 变更文件

- 新增 `hatch/adaptive/HatchType.java`、`AdaptiveHatchHelper.java`、`AdaptiveNetDynamoHatch.java`、`AdaptiveNetLaserTargetHatch.java`
- 删除 `hatch/adaptive/AdaptiveNetHatchBase.java`
- 重写 `AdaptiveNetTerminal.java`（4-tab UI + 权限 + 迁移）
- 重写 `AdaptiveNetHatch.java`、`AdaptiveNetLaserHatch.java`（适配 Helper）
- 重写 `AdaptiveNetwork.java`、`AdaptiveNetworkManager.java`（4 类型支持）
- 更新 `CommonProxy.java`（注册 2 个新仓 + 配方）
- 更新 `en_US.lang`、`zh_CN.lang`

---

## 3.17.1 - 修复与优化

> 作者：wztwzt | 更新时间：2026-08-31 | 基于 3.17.0

### 修复

- 修复自适应电网仓/激光仓无法绑定（子类覆盖 onRightclick 绕过了父类绑定逻辑）
- 修复自适应电网仓基类副手检测（MC 1.7.10 无副手系统）
- 无线电网输入终端 UI 字体颜色优化，增强可读性
- 无线电网输入终端 UI 布局对齐

### 优化

- 无线电网输入终端添加每档电压/电流对应的 EU/t 数值注释
- 万能维护仓删除电压调节功能（由能量仓决定）
- 万能维护仓删除线程调节功能（未实现）
- 万能维护仓速度调节上限统一：MAX 电路板 = 100%，其他按比例递减

### 材质

- 网络配置闪存使用 GTMAdvancedHatch 材质（带 10 帧闪烁动画）
- 无线电网输入/输出终端使用 GT 内置无线 overlay（带开机动画）
- 自适应电网仓/激光仓使用 GT 内置无线 overlay（带开机动画）
- 电网能源仓/动力仓使用 GT 内置无线 overlay
- 万能维护仓使用 GT 自动维护仓 overlay（带动画）

---

## 3.17.0 - GT 电网仓室系统（自适应电网 + 无线电网终端 + 电网仓）

> 作者：wztwzt | 更新时间：2026-08-30 | 基于 3.16.1

### 新增：自适应电网系统

- **自适应电网终端**（ID 32100）：主控方块，管理整套自适应电网网络
  - GUI 两个物品槽位：放入 64 个能源仓/激光仓自动设置电压/安培参数
  - Shift+右键闪存写入配置，右键打开 GUI
  - 一键切换全基地仓室电压等级
- **自适应电网仓**（ID 32102）：跟随终端自动同步电压等级的能源仓
- **自适应电网激光仓**（ID 32103）：激光版本，更大功率和容量
- **网络配置闪存**：复制/迁移网络配置，支持副手绑定

### 新增：无线电网终端

- **无线电网输入终端**（ID 32111）：超级能源仓，按钮式电压/电流选择
  - 电压：ULV ~ MAX（15档），点击即选
  - 电流：1A ~ 1GA（6档，64倍递增），点击即选
  - 自动安培计算，功率墙保护
- **无线电网输出终端**（ID 32110）：超级动力仓，无功率限制，全量注入电网

### 新增：普通电网仓

- **电网能源仓**（ID 32104）：无线仓升级版，直接共享电网电量
- **电网动力仓**（ID 32105）：无线仓升级版，输出电力注入电网

### 修复

- 修复 MetaTileEntity ID 冲突（32001/32003 与 GoodGenerator 冲突 → 改为 32100+）
- 修复自适应电网终端 GUI 崩溃（null UUID 检查）
- 修复无线终端 GUI 显示"未绑定"（UUID 同步问题）
- 修复 GUI 翻译 key 缺失（补全所有 ae2_qof.gui.* key）
- 修复 getDescription() key 与 lang 文件不匹配
- 修复 GUI 布局拥挤（增大面板、增加行间距）

### 后续计划

#### 中期（优先完成）

- 电网激光仓/大安培电网仓实现
- 自适应电网终端 GUI 按钮式电压/电流选择（当前为物品槽自动设置）
- 自定义材质（网络配置闪存、各仓室）
- WAILA 信息完善
- 万能维护仓维护绕过（MixinMTEMultiBlockBase 已移除，需找到不改变加载顺序的实现方式）

#### 代码质量改进

- P1 清理模板注释残留（MyMod.java、CommonProxy.java 等）
- P2 补全 mixin_notes.md（当前仅登记 2/23 个 Mixin）
- P4 switch 语法迁移（10 处旧式语法，需确认 Jabel 支持）

#### 待验证

- 自动上传记忆功能（v3.16.1 调试日志已加，待游戏内验证）

#### 远期

- **智能配方仓**（v3.19.0）：仿 GT-Shanhai 的"星律样板供料系统"
  - 配方类型过滤 + 虚拟电路 + 非消耗输入 + 卡死检测
  - 物品名 `AE2PatternBufferHatchSmart`，GT 仓室形态
  - 4 个样板槽 + 4 个配方类型过滤槽 + 4 个虚拟电路槽 + 4 个非消耗输入槽
- **产线聚合器**（v3.20.0）：仿 GTL 的一步产线系统
  - 将复杂多步骤产线合并为单一方块处理
  - JSON 配方数据包 + 机器消耗系统 + 电压等级限制
  - 物品名 `MTEProductionLineAggregator`，GT 多方块机器

### 变更文件

- 新增 `hatch/adaptive/` 包（AdaptiveNetworkManager, AdaptiveNetwork, AdaptiveNetTerminal, AdaptiveNetHatch, AdaptiveNetLaserHatch, AdaptiveNetHatchBase）
- 新增 `hatch/net/` 包（NetEnergyHatch, NetPowerHatch）
- 新增 `hatch/wireless/` 包（WirelessEnergyInputTerminal, WirelessEnergyOutputTerminal）
- 新增 `item/ItemNetworkDataStick.java`
- `CommonProxy.java`（注册所有新方块）
- `assets/ae2_qof/lang/zh_CN.lang` + `en_US.lang`（翻译）
- 版本号 `gradle.properties` → 3.17.0

---

## 3.16.1 - TST 兼容修复 + Quest Detector 稳定性

> 作者：wztwzt | 更新时间：2026-08-29 | 基于 3.16.0

### 修复：TST 极限合成配方崩溃（Critical）

- **现象**：加入 AE2-QoL 后 TST `ExtremeCraftRecipeHandler.initECRecipe` 崩溃（`IllegalArgumentException: null in argument`）
- **根因**：`@Mod dependencies` 中添加 `after:gregtech` 改变了 Forge mod 加载顺序，导致 TST 处理 Avaritia 极限合成配方时某些 GT 物品注册顺序异常
- **修复**：移除 `after:gregtech` 依赖声明（恢复为原始 `required-after:appliedenergistics2;after:guidenh`），MTE 注册维持在 `init()` 阶段（不依赖加载顺序）

### 修复：右键 Quest Detector 崩溃

- **现象**：右键 Quest Detector 方块触发 `ClassCastException: TileQuestDetector cannot be cast to TileIOPort`
- **根因**：`BlockQuestDetector extends BlockIOPort`，父类 `onActivated` 将 TileEntity 强转为 `TileIOPort`，但 `TileQuestDetector extends AENetworkTile` 不是 `TileIOPort` 子类
- **修复**：`BlockQuestDetector` 中 override `onBlockActivated`，`instanceof TileQuestDetector` 时直接处理，跳过父类逻辑

### 修复：Quest Detector 任务检测无效

- **现象**：检测器运行但无法匹配任何任务物品，日志显示 `rebuild keys failed: NoSuchMethodError`
- **根因**：`OreDictionary.getOres()` 展开某些矿辞时触发 `func_150895_a` 的 `NoSuchMethodError`，整个 key 缓存构建失败返回空列表
- **修复**：`collectRequiredKeys` 中将每个 `BigItemStack` 的处理包在 try-catch 内，单个物品矿辞展开失败不影响其余物品的 key 收集

### Mixin 变更

- 从 `mixins.ae2_qof.json` 移除 `gt.MixinMTEMultiBlockBase`（`after:gregtech` 导致 TST 崩溃，Mixin 已移除）
- **维护绕过暂未生效**：需要找到不改变加载顺序的实现方式

### 已知问题

- **自动上传记忆功能**：配方名映射/记忆供应器/工作台配方映射可能失效，调试日志已加（已注释），待游戏内验证

### 变更文件

- `MyMod.java`（移除 `after:gregtech` 依赖）
- `CommonProxy.java`（MTE 注册保持 init 阶段）
- `BlockQuestDetector.java`（+override `onBlockActivated`）
- `TileQuestDetector.java`（+详细诊断日志）
- `QuestDetectLogic.java`（矿辞展开 try-catch 容错）
- `mixins.ae2_qof.json`（-MixinMTEMultiBlockBase）

---

## 3.16.0 - 万能维护仓（维护绕过 + 无线能源 + 电路板并行映射）

> 作者：wztwzt | 更新时间：2026-08-29 | 基于 3.15.1

### 新增：万能维护仓

- 新物品「万能维护仓」：GT 仓室形态，放置于多方块结构维护槽位
- **维护绕过**：Mixin 注入 `MTEMultiBlockBase.shouldCheckMaintenance()` 返回 false，所有多方块机器永远无维护问题（注：Mixin 方案已移除，维护绕过暂未生效）
- **无线能源**：放置时绑定放置者 UUID，定期从全球无线电网拉取 EU 到本地存储（需提前注入 EU）
- **电路板并行映射**：插入 GT 电路板设置并行数，公式为 4^level（LV=4, MV=16, HV=64, ...）
- 配方：铁锭 ×4 + 玻璃 ×2 + 红石 ×2 + LV 电路板（3×3）

### 修复：万能维护仓注册失败（IllegalAccessError）

- **根因**：GT `CommonMetaTileEntity` 要求 MTE 注册必须在 init 阶段（Load Phase），preInit 时 `GregTechAPI.sPreloadStarted` 为 false 导致 `IllegalAccessError`
- **修复**：MTE 构造和合成表注册从 `preInit()` 移至 `init()`

### 变更文件

- 新增 `hatch/AE2MaintenanceHatchUniversal.java`（主仓室类）
- `CommonProxy.java`（注册仓室 + 配方，init 阶段注册）
- `assets/ae2_qof/lang/zh_CN.lang` + `en_US.lang`（翻译）
- 版本号 `gradle.properties` → 3.16.0

---

## 3.15.0 - 通知横幅对齐 AE2 原生样式（含耗时）+ pin 行开关修复与总开关

> 作者：wztwzt | 更新时间：2026-08-25 | 基于 3.14.0

### 重做：合成完成通知横幅（对齐 AE2 原生外观）

- 自绘横幅退役，改用 **AE2 原生 GuiNotification** 渲染——原版成就横幅贴图、滑入/滑出动画、
  队列展示，与原生「自动合成完成」通知外观完全一致
- 标题/描述复用 AE2 lang key，自动显示**中文**：「自动合成完成 / N 物品名, 耗时 HH:mm:ss」
- **新增耗时显示**：`CraftingCompletePacket` 携带任务耗时（取自 CPU `elapsedTime`），
  客户端按 `ETAFormat`（HH:mm:ss）格式化
- 触发保持**下单者自动接收**（无需像原生那样每次去 CPU 界面点关注）
- 删除 `client/render/CraftingNotificationOverlay.java`

### 修复：pin 置顶行开关失效（3.14.0 引入）

- 根因：自动扩展逻辑把行数下限钳到 1 行，用户在终端设置中选择 DISABLED 后仍被强制拉回 ONE
- 修复：`DISABLED` 状态下不再做任何自动提升——原生终端设置（齿轮 → Pins Rows）现在
  **真正可控**：选 DISABLED 完全关闭，选 N 则至少 N 行（自动扩展只在 >DISABLED 时生效）

### 新增：pin 置顶行总开关（settings.json）

- `pin_row_enabled`（默认 true，热加载，配置页/`/ae2qof` 可改）：关闭时 pin 行默认回退为关闭，
  玩家仍可在终端设置手动开启；`Config`/`MixinPinsHolder` 全链路接入

### 变更文件

- `mixin/nei/MixinGuiMEMonitorable.java`（DISABLED 短路）、`mixin/ae/MixinPinsHolder.java`（总开关）、
  `mixin/ae/MixinCraftingCPUCluster.java`（发包携带耗时）、`network/CraftingCompletePacket.java`（+elapsedTimeMillis）
- `ClientProxy.java`（handleCraftingComplete 改调原生 NotificationManager；移除旧横幅渲染订阅）、
  `Config.java`（pin_row_enabled）、删除 `client/render/CraftingNotificationOverlay.java`
- 版本号 `gradle.properties` + `mcmod.info`

---

## 3.14.0 - 展示条重做为原生 pin 置顶行 + 科学计数法修复 + GuideNH 指南修复

> 作者：wztwzt | 更新时间：2026-08-24 | 基于 3.13.0

### 重做：合成产物置顶行（替换 3.10.0 覆盖式展示条）

- **移除**覆盖式展示条（黑色遮罩、遮挡第一行、点击拦截全部成为历史）；
  改用 GTNH rv3 AE2 **原生 pin 系统**（`PinsHandler` + `PacketPinsUpdate` + `VirtualMEPinSlot` 独立行渲染）
- **mixin `PinsHolder`**：crafting pin 行数对「从未设置的玩家」默认 `DISABLED → ONE`——
  标准 ME 终端 / 原生无线终端 / 终端部件**开箱即得 1 行置顶区**；
  玩家手动在终端设置选 DISABLED 仍可关闭（显式 put 不走缺省值）
- **自动扩展行数**（mixin `GuiMEMonitorable`）：pin 产物种类超过当前行容量时本地提升可见行数
  （上限 3 行，如 18 种占两行），减少时自动回落；不回写玩家持久化设置
- 置顶条目显示的是**网络全部存量**（原生 repo 条目），独立行渲染、原网格下移、无任何遮罩
- 右上角「合成完成」横幅保留不变
- 范围说明：二合一终端（自定义布局）本轮未接入 pin；原计划下版本接入，现已**废弃**（用户决策：不需要），详见 `docs/TODO.md`

### 修复：科学计数法指数/尾数错乱（3.13.0 引入）

- `BigNumFormatter.formatSci` 误用 `bitLength()`（二进制位数）当十进制指数，
  且 double 精度不足导致尾数恒 `0.00`（如 `0.00×10^25`）——
  改用十进制位数（`toString().length()-1`），现正确显示 `1.85×10^25`

### 修复：GuideNH 指南从未生效（3.8.0 遗留）

- 根因：只放了 md 资源文件，**从未注册 Guide 实例**（`Guide.builder(...).build()`），
  物品索引（frontmatter `item_ids`）不生效 → 悬停本 mod 物品无「长按 G」提示
- 新增 `client/GuideNHIntegration`（init 注册，GuideNH 缺失静默跳过）
- `quest_detector.md` 补 `item_ids`；新增「无限存储元件」指南页（中英，绑定
  `aeinfinitycell:infinity_storage_cell`）；`crafting_tools.md` 补「合成产物置顶行」说明（中英）

### 变更文件

- `mixin/ae/MixinPinsHolder.java`（新增）、`mixin/nei/MixinGuiMEMonitorable.java`（重写：
  移除展示条注入，新增 pin 自适应）、`mixins.ae2_qof.json`（+MixinPinsHolder）
- 删除 `client/render/RecentCraftedOverlay.java`、`client/render/TerminalLayoutAccessor.java`；
  `ClientProxy` 清理展示条接入
- `client/GuideNHIntegration.java`（新增）、`util/BigNumFormatter.java`（formatSci 修复）
- assets：guidenh 新页/更新（中英）；版本号 `gradle.properties` + `mcmod.info`

---

## 3.13.0 - 无限磁盘悬停统计（总计/物品/流体/源质 + 大数格式化）

> 作者：wztwzt | 更新时间：2026-08-24 | 基于 3.12.0

### 新增：无限存储元件 tooltip 实时统计

- 悬停无限磁盘即显示：**总计行**（`∞ Bytes | 共 N 类 / M 件 ≈N B`）→ **物品** → **流体** → **源质** →
  （EU 有数据时）逐行明细，每行末尾附 AE2 公式字节估算（每类型 8B + 存量折算）
- 大数格式化：默认字母单位链 `K→M→B→T→Qa→Qi…`（如 `12.34M`）；**按住 Ctrl** 切换科学计数法
  （如 `1.23×10^7`）；内部 BigInteger，网络序列化防溢出
- 数据链路：内容存服务端世界存档（客户端无法直读），tooltip 未命中缓存（TTL 2s）时节流发送 C2S
  请求 → 服务端主线程汇总 → S2C 回包渲染；空元件显示提示而非空白
- 新增 `util/BigNumFormatter`、`network/InfinityCellStatsPacket`（C2S/S2C）、
  `client/InfinityCellTooltipCache`；`ItemInfinityStorageCell.addInformation` 接入渲染

---

## 3.12.0 - 吞并 AE2InfinityCell：无限存储元件整体并入本 jar

> 作者：wztwzt | 更新时间：2026-08-24 | 基于 3.11.0

### 合并说明（原 aeinfinitycell-1.0.4，作者 dancing snow，MIT）

- **双 @Mod 同 jar**：本 jar 现同时注册 `ae2_qof` 与 `aeinfinitycell` 两个 mod 身份；
  与独立版 AEInfinityCell **严格互斥**（同 modid 共存必触发 DuplicateModsFoundException）——
  安装本版本前必须移除原 aeinfinitycell jar
- **零迁移无缝接管**：保留原 modid / 物品注册名（`aeinfinitycell:infinity_storage_cell`）/
  包名 `cn.dancingsnow.aeinfinitycell` / 世界存档数据路径——背包与硬盘中的磁盘、
  存档内 per-cell 数据文件全部原样可读
- **并入功能**：物品/流体/源质三通道无限存储；UUID 引用 NBT + 复制共享同一后端库存；
  TileDriveMixin（drive 多通道挂载，改入本项目标准 mixin 体系）；NEI 四通道分页查看器
  （Infinity Cell View）；旧单文件格式自动迁移逻辑；中英 lang 与贴图
- **裁剪**：AppEU（EU 通道）可选集成未随迁（环境无该 mod）——检测到 appeu 时静默跳过并打日志
- **依赖新增**：`ThaumicEnergistics:1.7.53-GTNH`、`Avaritia:1.97`（compileOnly），
  本地 libs 引入 Thaumcraft/eternalsingularity 发布 jar 仅作编译期符号（运行时由整合包提供）
- **合规（MIT）**：CREDITS.md 新增署名条目；`assets/aeinfinitycell/LICENSE` 随 jar 附原 MIT 文本
- 版本标识：aeinfinitycell 显示 `1.0.4-ae2qol`

### 变更文件

- 新增 `cn/dancingsnow/aeinfinitycell/**`（29 类）、`mixin/TileDriveMixin.java`（自原 mod 移包）、
  `assets/aeinfinitycell/**`（贴图/lang/LICENSE）
- `mcmod.info` 双条目；`mixins.ae2_qof.json` +TileDriveMixin；`dependencies.gradle` +TE/Avaritia/libs×2
- `CREDITS.md` 署名；版本号 `gradle.properties` → 3.12.0

---

## 3.11.0 - 新增「ME 任务检测器」：ME 网络物品自动完成 BQ 检索型任务

> 作者：wztwzt | 更新时间：2026-08-24 | 基于 3.10.1

### 新增：ME 任务检测器方块（BetterQuesting 联动）

- 接入 ME 网络的新方块**ME 任务检测器**（`quest_detector`）：网络中存储的物品每秒与
  BetterQuesting **检索型任务**（consume=false）比对，库存达标即由 BQ 官方逻辑自动完成任务——
  任务物品存进网络即算提交，无需手动取出或使用提交站
- **零消耗保证**：仅调用 GTNH BQ fork 特供的只读检测钩子 `IItemTask.retrieveItems()` /
  `IFluidTask.retrieveFluids()`（对齐官方观察站 TileObservationStation 机制）；
  消耗型（consume=true）任务在官方实现入口直接 return，绝不从网络扣走任何物品
- **绑定放置者 UUID**（放置时记录、NBT 持久化）；进度经 `ParticipantInfo.getSharedQuests()`
  自动兼容 BQ 组队共享进度；绑定玩家离线跳过；拆下重放可重新绑定
- 门控与性能：20tick 周期 + 供电/频道激活检查（`IPowerChannelState`，复用 TileIOPort 网格代理）；
  任务需求键按玩家缓存 10 秒重建（矿辞 OreDictionary 展开），网络侧按键 findFuzzy 收集，
  需求/命中双上限保护（4096/2048）
- WAILA/JADE 显示绑定玩家与网络状态（IMC 注册，未装 WAILA 零影响）；
  GuideNH 游戏内指南新增第 15 页（中英双语）
- 配方：铁锭 ×4 + 玻璃 ×2 + 红石 ×2 + 书（3×3 标准合成）

### 安全设计

- BQ 未安装环境零影响：`Loader.isModLoaded("betterquesting")` 守卫置于 tick 首行；
  全部 betterquesting/bq_standard 类型引用隔离在 `quest/QuestDetectLogic` 单文件内
  （BQ 缺失时该类永不执行到 BQ 符号解析，规避 #74 式专用服 NoClassDefFoundError）
- 编译期依赖 `com.github.GTNewHorizons:BetterQuesting:3.8.70-GTNH:dev`（implementation）

### 变更文件

- 新增 `quest/QuestDetectLogic.java`（核心检测逻辑）、`tile/TileQuestDetector.java`、
  `block/BlockQuestDetector.java`、`client/render/RenderBlockQuestDetector.java`、
  `quest/QuestDetectorWailaProvider.java`
- `CommonProxy.java`：方块/TE 注册 + WAILA IMC + 配方；`dependencies.gradle` +BQ dev 依赖
- assets：贴图、lang 中英、GuideNH 中英指南页与索引
- 版本号：`gradle.properties` + `mcmod.info`

---

## 3.10.1 - 修复合成完成展示条渲染位置错误

> 作者：wztwzt | 更新时间：2026-08-24 | 基于 3.10.0

### 修复：展示条画到网格中间而非第一行（用户实测反馈）

- **根因**：GTNH rv3 ME 终端的网络物品网格是 `VirtualMEMonitorableSlot` **虚拟槽**，不在 `inventorySlots`
  中——原 `locateRow()` 遍历 Slot 取最小 `yDisplayPosition` 行，实际定位到 view cell/升级槽等错误行，
  展示条被画在物品网格中间并遮挡真实物品
- **修复**：新增 duck 接口 `mixin/TerminalLayoutAccessor`（由 `MixinGuiMEMonitorable` 实现，
  @Shadow 布局字段），按官方布局公式取第一行：
  - 第一行 x = `offsetRepoX`（容器相对）
  - 第一行 y = `offsetRepoY + pinsRows*18`，其中 `pinsRows = rows - monitorableSlots.length/perRow`
- 覆盖全部 GuiMEMonitorable 系终端子类（标准/合成/样板/接口/无线）；非标准宿主保留旧兜底逻辑；
  展示条格数随终端样式自适应（min(9, perRow)）

### 变更文件

- 新增 `mixin/TerminalLayoutAccessor.java`（duck 接口）
- `mixin/nei/MixinGuiMEMonitorable.java`：@Shadow 五个布局字段 + 实现接口
- `client/render/RecentCraftedOverlay.java`：locateRow 重写（duck 优先 + 兜底保留）
- 版本号：`gradle.properties` + `mcmod.info`
- **勘误（3.13.0 起）**：duck 接口移至 `client/render/` 包——SpongePowered Mixin 禁止直接引用
  mixin 专属包（`com.wztwzt.ae2_qof.mixin.*`）内未注册类，原位置导致启动即崩
  （`IllegalClassLoadError ... cannot be referenced directly`）

---

## 3.10.0 - 合成完成产物终端第一行展示 + 中键下单放宽 + ModularUI tooltip 换行真修复

> 作者：wztwzt | 更新时间：2026-08-24 | 基于 3.9.0

### 新增：合成完成产物展示条（终端第一行，保留 60 秒）

- 合成 CPU 完成订单后，产物自动显示在**标准 ME 终端第一行**（搜索框下方，模仿标记区视觉：半透明底 + 物品图标 + 数量），每条保留 **60 秒**后消失（最后 5 秒渐隐）
- 与右上角横幅并存：横幅即时提醒，第一行持续展示；数据同源（CraftingCompletePacket）
- **点击格子提取一组到背包**（复用提取链路，失败走既有聊天提示）；悬停显示物品名 ×数量
- 同物品新完成时合并数量并刷新保留时间；满 9 格顶掉最旧；仅标准 `GuiMEMonitorable` 显示（合成/样板/接口/无线/二合一终端等子类不显示，`getClass` 精确排除）
- 纯客户端实现（`client/render/RecentCraftedOverlay` + `MixinGuiMEMonitorable` drawScreen/mouseClicked 注入），覆盖期间原第一行物品不可点（过期即恢复）
- 已知取舍：展示条占据第一行的 60 秒内无法点击该行的网络物品

### 修复：NEI 中键下单对「仅有样板」物品无效

- **根因**：`NetworkInventoryCache.put()` 把 `stackSize<=0` 一律当"已移除"删除条目——而 AE2 同步列表中「仅有样板、网络无存量」的物品正是 `stackSize=0 + craftable=true` 形式，永不进缓存 → 客户端 `isCraftable()` 恒 false → 中键在 `MixinPanelWidgetClick.handleCraftRequest` 被拦截不发包。只有网络实际存有物品（count>0）时才工作
- **修复**：`put()`/`putFluid()` 对 `stackSize<=0 且 craftable` 保留条目（count=0）——中键对有样板的物品即可下单；tooltip/书签角标渲染端本就按 `count>0` 才显示数量、craftable 独立显示绿色 +Craft，无需改动；服务端 `RequestCraftingPacket` 早已用 `getCraftingFor` 支持样板下单，未改

### 修复：PH/GTNL/GT 智能倍增按钮 tooltip 换行（真根因）

- **根因（勘误 3.9.0 的判断）**：Java `split("\\n")` 的参数是正则——`\n` 在正则中转义为**真实换行符 LF**，并非字面反斜杠+n！lang 文件里的字面 `\n` 从未被拆开，**3.3.7 的换行"修复"实际从未生效**（`String.replace("\\n","\n")` 非正则才是正确写法）
- **修复**：GT 样板输入机 / PH 双口输入仓 / GTNL 超级样板输入总成三个 ModularUI mixin 统一改用 `TooltipTextButton.langLines(key).split("\n")`（先字面替换再按 LF 拆行）

### 变更文件

- 新增 `client/render/RecentCraftedOverlay.java`
- `mixin/nei/MixinGuiMEMonitorable.java`（+drawScreen/mouseClicked 注入）、`network/CraftingCompletePacket` 客户端处理（ClientProxy.handleCraftingComplete 接入展示条）
- `client/NetworkInventoryCache.java`（craftable-only 保留）、三个 `mixin/gt/*Gui` 换行修复
- `client/render/CraftingNotificationOverlay.getRenderItem` 改 public 共享
- 版本号：`gradle.properties` + `mcmod.info`

---

## 3.9.0 - GTNL 超级样板输入总成(ME) 接入智能倍增

> 作者：wztwzt | 更新时间：2026-08-24 | 基于 3.8.1

### 新增：21504/21505 超级样板输入总成 (ME) 智能倍增

- GTNotLeisure「超级样板输入总成 (ME)」（meta 21504 流体版 / 21505 纯物品版，`SuperCraftingInputHatchME`）GUI 左下角新增「智能倍增」循环箭头开关，勾选后合成 CPU 对其一次性推送 N 轮材料
- **勘误**：原规划中写作"21504 SuperDualInputHatchME"有误——SuperDualInputHatchME 是 GTNL 的补货型机器（22620），不可作合成介质；正确目标为 SuperCraftingInputHatchME
- **实现说明（Why）**：能力层零改动即已支持——该机器 `extends MTEHatchInputBus`，既有 `MixinMTEHatchInputBus` 挂基类注入 `ISmartDoublingMedium` + NBT 持久化（saveNBTData/loadNBTData TAIL 经 super 链生效）；CPU 经能力接口自动走 GT pushPattern N× 分支（其缓冲单堆叠 Integer.MAX_VALUE 无上限、pushPattern 恒成功、isBusy() 恒 false 由 knownBusyMediums 冷却兜底）。唯一缺口是 GUI 开关：新增 `mixin/gt/MixinSuperCraftingInputHatchMEGui` 注入其 ModularUI `createBottomLeftCornerFlow` RETURN 追加同款 ToggleButton（BooleanSyncValue.allowC2S 直写 NBT 字段）
- 兼容：GTNL 为可选依赖，mixin 目标类缺失时静默跳过；未安装零影响；lang 复用现有 `gui.ae2_qof.smart_doubling*`

### 修复：按钮 tooltip 多行失效 + 悬停延迟显示

- **多行失效根因**：MC 的 lang 文件不转义 `\n`（读出来是字面反斜杠+n 两个字符），而各渲染路径拆行方式不一——AE2 `drawTooltip`/手动绘制按真实换行符 `split("\n")` 拆不开字面 `\n` → 全部挤成一行
- **修复**：`TooltipTextButton.getMessage()` 统一把字面 `\n` 替换为真实换行；二合一终端 6 按钮（↑/←/⇄/AM/OV 等）与无线收发器 GUI 6 按钮全部恢复多行
- **智能倍增按钮**：三个 AE2 接口 GUI mixin（ME 接口/GTNL 超级接口/超级二合一接口）的 hint 实参改为预翻译+显式换行替换，不再依赖各版本渲染器内部行为
- **悬停延迟**：自研按钮 tooltip 现在悬停满 **1 秒**才显示（`shouldRenderTooltip` 计时闸门，接入 AE2 自动路径 `handleTooltip` override 与 GuiWireless 手动路径）；移开鼠标即重置计时

### 变更文件

- 新增 `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinSuperCraftingInputHatchMEGui.java`
- `mixins.ae2_qof.json` client 列表注册
- `client/gui/TooltipTextButton.java`（langLines + 1s 延迟闸门）、`merged/GuiMergedTerminal.java`（handleTooltip override）、`wireless/gui/GuiWireless.java`、`mixin/ae/MixinGuiInterface.java` / `MixinGuiSuperInterface.java` / `MixinGuiSuperDualInterface.java`
- 版本号：`gradle.properties` + `mcmod.info`

---

## 3.8.1 - 专用服务器网络包半注册崩溃修复（P0）

> 作者：wztwzt | 更新时间：2026-08-24 | 基于 3.8.0

### 修复（🔴 P0，仅专用服务器必现）

- **#74** 二合一终端在专用服务器上「转移合成表 / 切换处理模式 / 上传装配矩阵」全部踢人：`Undefined message for discriminator 17 in channel ae2apu`
- **根因**：S2C 包 Handler 类内直接引用 client 专属类型——`Minecraft.thePlayer` 的字段声明类型为 `net.minecraft.client.entity.EntityClientPlayerMP`。注册 Handler 时 `SimpleNetworkWrapper.instantiate()` → `Class.newInstance()` 触发类链接验证，验证器解析方法体中该字段引用 → 专用服 JVM 无此类 → `NoClassDefFoundError`
- **半注册**：异常被 `MyMod.preInit` 的 try/catch 吞掉，注册在 discriminator 11（CraftingResponsePacket）处中断，11~25 共 15 个包未注册；已存活的 C2S 仅 0/2/3/4/7/9/10（故 NEI 上传/提取/下单正常，极具迷惑性）；客户端发送 id=17（MergedTerminalActionPacket）即 Undefined 踢人
- **单机为何不复现**：单人/局域网同 JVM 含 client 类，全量注册成功。此 bug 对一切专用服必现
- **修复模式**：10 个 S2C Handler 全部改为「壳 + proxy 分发」——`onMessage` 仅剩 `if (ctx.side == Side.CLIENT) MyMod.proxy.handleXxx(message)`，经 `CommonProxy` 声明类型虚分派到 `ClientProxy` override 实现（归队 `func_152344_a` + 原 client 逻辑整体搬迁）。network 包内零 client 类型引用，编译期保证
- **顺带修复**：CraftingResponsePacket / ReplaceCandidatesPacket 此前未归队客户端主线程（审查遗留 ⚠️），本次随搬迁统一归队；SwapPatternPacket 解码 count 补上界钳制 ≤64（审查 #45 同类遗留，堵 OOM 攻击面）
- **fail-fast 加固**：`MyMod.preInit` 网络注册失败从"吞异常继续运行"改为直接抛出——半注册比启动失败危害大得多
- 消息类字段 private → public（Handler 逻辑迁至 ClientProxy 后需跨包访问）

### 变更文件

- `network/`：ProvidersListS2C、WirelessChannelSync、WirelessHighlight、SwapPattern、CraftingResponse、CraftingComplete、ConfigUpdate、MergedTerminalResult、MergedTerminalBlankCount、ReplaceCandidates 共 10 个包
- `CommonProxy.java`（+10 分发空方法）、`ClientProxy.java`（+10 override 实现）、`MyMod.java`（fail-fast）
- 版本号：`gradle.properties` + `mcmod.info`

---

## 3.8.0 - 全量 Tooltip + GuideNH 游戏内指南集成

> 作者：wztwzt | 更新时间：2026-08-23 | 基于 3.7.0

### 新增：全量物品与按钮 Tooltip

- 物品：二合一终端面板部件、无线终端（含绑定状态显示）、二合一方块、强化 IO 端口补齐悬停说明；无限磁盘硬编码英文改为双语 lang
- GUI 按钮：二合一终端 `↑/←/⇄/AM/OV` 六个按钮、无线收发器 GUI 全部六个按钮新增悬停说明
  （新 `TooltipTextButton` 实现 AE2 ITooltip——AE2 基类自动渲染；原版 GuiContainer 路径手动绘制）
- 智能倍增开关三处按钮此前已带 tooltip，本次无改动

### 新增：GuideNH 游戏内指南（可选依赖）

- 集成 [GuideNH](https://github.com/ABKQPO/GuideNH) 框架：安装该 mod 后悬停本 mod 物品**按住 G** 即可打开对应详细指南页
- 内置中英双语指南 9 页：总览（14 功能一览表）、NEI 集成全家桶、合成通知与重规划、切割刀复制名称、强化 IO 端口、无限磁盘、无线组网、智能倍增、二合一终端三形态
- 零代码集成：资源目录 `assets/ae2_qof/guidenh/_zh_cn|_en_us` 由 GuideNH 自动发现注册；物品绑定经页面 frontmatter `item_ids`
- GuideNH 未安装时零影响；`@Mod dependencies` 声明软依赖 `after:guidenh`

---

## 3.7.0 - 二合一终端多形态（线缆面板部件 + 手持无线）

> 作者：wztwzt | 更新时间：2026-08-23 | 基于 3.6.1

### 新增：样板与接口二合一终端面板（部件形态）

- 新物品「样板与接口二合一终端面板」：可像原版 ME 终端一样安装到 AE2 线缆/泛用线缆（FMP）任意面上
- 外观与原版 ME 终端部件一致（三层显示器造型 + 通道/供电状态灯），需要 ME 通道并消耗 idle 电力
- GUI 与方块形态完全一致（面板编辑 + 接口列表 + 上传/撤回/交换按钮全套），三种形态共用同一容器逻辑
- 样板槽与编辑快照经部件 NBT 存入宿主线缆；扳手拆下返还物品，数据随部件保留

### 新增：无线二合一终端（手持）

- 新物品「无线二合一终端」：随时随地右键打开完整二合一界面，**支持跨维度**（任意地点、任意维度）
- **绑定方式与原版无线终端一致**：放入 ME 安全终端编码槽完成绑定（NBT `encryptionKey`）
- 安全终端被拆除后终端自动失效（打开提示"站点无法定位"，已打开的界面自动关闭）；重绑新站即可恢复
- 免电设计（无需充电）；对网络的存取权限仍由安全终端生物卡强制，绑定不绕过任何权限
- 面板数据（75 格编辑快照）持久化在物品 NBT，关界面即回写

### 技术说明

- 三形态（方块/部件/手持）统一抽象为 `IMergedTerminalHost` 宿主接口，Container/Gui/PatternContainer 业务层零分叉
- 无线定位复用 AE2 LocatableRegistry + `IWirelessTermHandler.hasInfinityRange` 官方跨维度钩子；权限校验链 performCheck 全量复用（本地化错误消息免费）
- 零新增 Mixin；`ContainerTerminalResolver` 天然兼容三形态（anchor 反射沿类层级）

---

## 3.6.1 - 深度审查修复批次（智能倍增/网络/客户端）

> 作者：wztwzt | 更新时间：2026-08-23 | 基于 3.6.0

### 修复（P0/P1）

- **#73** 智能倍增大订单（如 1T）客户端无响应：GT 样板输入机 `isBusy()` 恒 false 导致每 tick 重复推送巨量材料，现倍增推送成功后同 tick 冷却
- **#46** 合成完成通知包未切客户端主线程 → 数据竞争，已归队
- **#47** 无线高亮开关在专用服务器只能开不能关：状态改由包参数携带
- **#45** 网络包数组长度无上界 OOM DoS：4 个包解码钳制（≤64/1024/1024/256）
- **#48** NEI 叠加层开关改纯客户端本地设置，多人登录不再被服务端值覆盖；README 注明 `/apu-overlay` 仅单机/局域网可用

### 修复（P2/P3）

- **#51** 智能倍增探测优化：容量探测指数扩张+区间二分（31 次→个位数 probe）；功率探测有限上界查询（O(P)→通常 O(1)）
- **#52** 渲染热路径：RenderItem 复用 / tooltip 流体识别 3 遍并 1 遍 / 数量格式化记忆化
- **#57** 超大网络供应器列表发送前按 32KB 包预算截断，不再静默失效

### 行为调整

- **#49** NEI tooltip 网络存量缓存改时间窗过期：终端开启时实时刷新；进 NEI 配方界面依然显示；彻底退出终端 5 分钟后自动消失（原"关闭即清"方案经用户反馈回退）

### 复核结论

- **#53** 无线连接器跨维度实际已支持（审查误判），拒绝分支为不可达死代码仅加注释
- **#55** 死代码按仓库规范仅标记不删除

### 已知问题

- #50 无线全局频道无归属权维持现状（用户决策不做）

---

## 2026-08-23 - 全代码库深度审查报告（仅登记问题，无代码变更）

> 作者：wztwzt | 审查时间：2026-08-23 | 版本基线：3.6.0
> 范围：`src/main/java` 全部约 120 个 Java 源文件 + `mixins.ae2_qof.json`；重点为**前十三个功能的实现完整性**、服务端 tick 与 Mixin 注入正确性、客户端渲染热路径性能、网络包安全。
> 方法：三路并行静态审查（① 客户端渲染/NEI 叠加层；② 服务端 Mixin/智能倍增/强化 IO 端口；③ 网络包安全 + 十三功能逐项核对），关键结论均经人工二次复核源码确认。

### 一、十三功能核对结论

**13 项功能全部存在完整代码路径（GUI 按钮 / Mixin / 网络包 / 服务端处理器四环齐备），无缺失项：**

| # | 功能 | 结论 | 关键证据链 |
|---|---|---|---|
| 1 | NEI 样板上传/撤回/交换 | ✅ 完整 | `GuiUploadButtonHandler` 四按钮 + `UploadPatternPacket`/`RecallPatternPacket`/`SwapPatternPacket` + 三策略选择 `ProvidersListS2CPacket` |
| 2 | NEI 取物品/合成下单 | ✅ 完整 | `MixinPanelWidgetClick` + `ExtractItemPacket`（SIMULATE 预检+归还防丢物）/`RequestCraftingPacket` 双路径 |
| 3 | NEI tooltip 存量/可合成 | ✅ 完整 | `NetworkTooltipHandler` + `NetworkInventoryCache`（按 item+damage 键，忽略普通 NBT 差异） |
| 4 | NEI 书签数量叠加 | ✅ 完整 | `MixinPanelWidgetDraw`（PanelWidget.draw TAIL）+ `NetworkInventoryDrawHandler` |
| 5 | 合成完成通知 | ✅ 完整（含缺陷 #46） | `submitJob` 捕获发起者 + `completeJob` 校验密钥后发包 + `CraftingNotificationOverlay` 绘制 |
| 6 | 合成重新规划 Replan | ✅ 完整 | `MixinGuiCraftConfirm` + `ReplanPacket` + `Replanner`（对已完成模拟的 job 重提 beginCraftingJob） |
| 7 | 强化 IO 端口 | ✅ 完整 | `TileExIOPort` + `MixinTileIOPort` @ModifyVariable（long 溢出钳制 + 热加载） |
| 8 | 无限水岩浆磁盘 | ✅ 完整 | `ItemInfinityWaterLavaCell`（AEBaseInfiniteCell + 配方 `"wbw"," "," "` 与 README 一致） |
| 9 | 无线收发器+连接器 | ✅ 完整（含缺陷 #53） | wireless/ 整包；「跨维度」仅收发器链路成立，连接器绑定实际拒绝跨维度 |
| 10 | 石英切割刀复制名称 | ✅ 完整 | `KnifeNameCopyHandler`（HIGHEST 右键事件 + GT 单方块/多方块名解析 + 剪贴板） |
| 11 | F 键搜索填充 | ✅ 完整 | `KeyInputHandler`（NEI IContainerInputHandler，appeng./ae2fc GUI 判定 + 反射定位搜索框） |
| 12 | NEI 叠加层开关 | ⚠️ 含缺陷 #48 | `CommandOverlay` 注册位置导致专用服务器无命令；OV 多人权威归属混乱 |
| 13 | 智能倍增 | ✅ 完整（含缺陷 #44/#51/#58） | 安全边界全对齐 README；注入接管/批量记账/溢出钳制均在 |

**Mixin 注入正确性评估（对照 rv3-beta-977 dev jar 逐一实证）**：@Shadow 字段与方法、@Inject 目标（`executeCrafting` HEAD+cancellable、`submitJob` RETURN、`handleCraftBranchFailure`/`completeJob` TAIL、`DualityInterface.writeToNBT/readFromNBT` TAIL、`ContainerInterface.<init>` RETURN）、反射目标（`TaskProgress.value`/`consumeCraftSession()`/`finalOutput` 私有内部类/`CraftingCpuDiagnostics`/静态 `getServerTick()` 等）**全部命中，零 @Overwrite/@Redirect**；智能路径反射失败与执行异常均有安全降级回原版。HEAD 注入 + cancel 替代 @Overwrite 保留了原方法字节码骨架，PH `MixinInstantComplete` 等其它 mod 注入不受破坏。

### 二、新增风险登记摘要（#44-#58，详见下方登记表）

| 编号 | 摘要 | 等级 |
|---|---|---|
| #44 | 智能倍增 PH 介质 `pushPatternMulti` 返回 0 回退单发时按 N 轮记账（实际仅交 1 轮材料）→ 少产出 + 白扣功率 + 任务假完成 | 🔴 |
| #46 | `CraftingCompletePacket`(S2C) 未切客户端主线程，Netty IO 线程写非线程安全队列 | 🔴 |
| #45 | `RequestProvidersListPacket` 数组长度无上界 → 恶意包 OOM DoS（另有 3 个 S2C 同类） | 🟡 |
| #47 | 无线高亮开关服务端读客户端静态字段 → 专用服务器只能开不能关 | 🟡 |
| #48 | `/apu-overlay` 专用服务器不可用 + OV 开关多人权威归属混乱 | 🟡 |
| #49 | `NetworkInventoryCache.invalidate()` 从未被调用 → 缓存永不过期显示陈旧数据 | 🟡 |
| #50 | 无线全局频道无归属权，任何玩家可删他人频道 | 🟡 |
| #51 | 智能倍增容量二分固定 31 轮 do-while 每次推送重跑 + 全网电力探测 O(P) | 🟢 |
| #52 | 渲染热路径浪费：每帧 new RenderItem / tooltip 流体识别×3 遍×每槽位 / 格式化无 memo | 🟢 |
| #53 | README 功能 9「跨维度」与实现不符（连接器拒绝跨维度死分支） | 🟢 |
| #54 | 8 个文件残留 `System.out.println` 调试日志 | 🟢 |
| #55 | 死代码遗留（lastProviderName/getLastUpdateTick/BUTTON_HALVE_ID/Replanner 等） | 🟢 |
| #56 | `docs/MOD_MAP.md` 为空模板，违反文档驱动规范 | 🟢 |
| #57 | `ProvidersListS2CPacket` 无应用层尺寸预算，超大网络静默失效 | 🟢 |
| #58 | 智能倍增部分提前 return 分支疑似遗漏 `parallelismProvider` 回写（静态审查发现，待复核） | 🟢 |

### 三、修复优先级建议

- **P0（发布前必修）**：#44 记账缺陷、#46 线程归队
- **P1**：#45/#47/#48/#49/#50（安全与专用服正确性）
- **P2**：#51/#52（大网络性能）
- **P3（卫生整改）**：#53/#54/#55/#56/#57/#58

---

## 3.6.0 - 二合一终端面板体验升级批次

> 作者：wztwzt | 更新时间：2026-08-22

### 新增：样板回读（二次编辑）

- 把编码好的样板放回 OUT 槽，自动解码回填面板格子（输入/输出/模式/替代开关一并恢复），无需重新拖放配方
- 数据链路：`ICraftingPatternDetails` 主路径 + GT 终极样板 NBT（`in`/`out` AE2 栈格式）兜底读取
- 流体输入还原为 GT 展示物品（tooltip 带温度/状态），与 NEI 填充时的格子表示一致；`ItemFluidDrop` 包装同样识别
- 样板内 `apu:recipeMap` 保留，回读后再编码/上传仍可识别供应器映射

### 新增：编辑快照持久化

- 关闭终端 GUI 时自动保存面板全部格子（合成 3×3 + 扩展输入 32 + 输出 32）与合成模式到方块 NBT；重新打开完整恢复
- 合成模式经 `@GuiSync(0)` 同步客户端，GUI 背景与按钮布局即时正确
- 存档兼容：纯新增 NBT 字段（`apuSavedGrid` / `apuSavedMode`），旧存档无字段按默认空面板处理

### 新增：PH 编程工具箱 MK.II 适配

- 二合一终端 NEI 转写时，自动把配方中 `stackSize==0` 的不消耗催化剂替换为对应编程电路；工具箱处于兜底模式且配方无催化剂时追加归零电路
- 反射调用 PH API（`holding()` / `addEmptyProgCiruit()` / `ItemProgrammingCircuit.wrap`），PH 未安装时自动跳过、零影响
- 补齐与原生样板终端的能力差异：原生 hook 位于 NEE `GregTech5RecipeProcessor`，二合一终端走自研直通链路绕过了该注入点

### 新增：装配矩阵上传按钮（AM）

- 面板新增 `AM` 按钮（仅合成模式且 GTNL 已安装时显示）：点击将编码好的样板上传至网络中 GTNL 装配矩阵样板库
- 行为对齐 GTNL 原生样板终端按钮：OUT 槽为空先编码；仅接受普通合成样板；矩阵已有相同输出时提示并返还空白样板；否则插入第一个有空位的矩阵并清空 OUT 槽
- 全程反射访问 GTNL，模组未安装时按钮隐藏

### 修复：流体解析严格匹配（「气态氧变液态氧」根因）

- 根因：`findFluidByName()` 旧模糊匹配用 `contains` 双向包含——`"liquidoxygen".contains("oxygen")` 为真，HashMap 无序遍历导致气态氧被随机解析成液态氧写入样板 NBT
- 修复：改为精确匹配 → 完全相等 → 唯一前缀匹配 → 多候选放弃（宁可不解析也不写错流体）
- 附带：识别 ae2fc `ItemFluidDrop` 物品（NBT `Fluid` 键）参与流体解析，重编码不再退化为普通物品序列化

### 修复：数量编辑与物品消失

- 移除数量编辑 999 硬上限；超大输入 clamp 到 `Integer.MAX_VALUE` 防溢出
- **输出格禁止中键编辑**：编辑后 `getAndUpdateOutput()` 配方重算会覆盖/清空输出格（物品消失根因之一），客户端+服务端双重拦截
- `setStackSize` 后补 `slot.onSlotChanged()`
- 中键编辑覆盖层移至背包区域居中显示，交互改为模态弹窗（点击覆盖层以外关闭并吞掉点击，防止误触下方槽位）；标题「编辑数量:」/「重命名:」
- Shift+点加号=乘法、Shift+点减号=除法（右键减号同为除法），独立 ÷ 按钮移除

### 修复：Shift+滚轮 OreDict 替换循环推进

- 原实现向上滚永远跳第 0 个候选、向下滚跳最后一个；改为基于当前物品在候选序列中的索引循环推进
- 新增候选查询包（`RequestReplaceCandidatesPacket` / `ReplaceCandidatesPacket`）供预览扩展使用

### 其他

- `TileMergedTerminal` 实现 `IPowerChannelState`（对齐 TileExIOPort 模式），WAILA 等可正确读取供电/频道状态
- 面板按钮布局调整：上传 `↑`/召回 `←` 贴紧编码按钮两侧，交换 `⇄` 移入处理模式右上 AE 按钮区（合成模式隐藏），`OV` 固定右下

---
# AE2 QoL - Changelog

> 当前版本：3.6.1 | 适配：GTNH 2.9.0-beta-1 | 依赖：AE2 `rv3-beta-977-GTNH`，ae2fc `1.5.88-gtnh`

---

# 功能总览

> 状态图例：✅ 可用 ｜ ⚠️ 已失效（需修复） ｜ 🕐 规划中

| 功能 | 核心类 | 状态 |
|---|---|---|
| NEI 样板上传/撤回/交换（样板终端 GUI 内 4 个按钮） | `client/event/GuiUploadButtonHandler` + `network/UploadPatternPacket`/`RecallPatternPacket`/`SwapPatternPacket` | ✅ 可用 |
| Provider 列表请求 + 三策略自动上传 + 选择界面 | `network/RequestProvidersListPacket` + `network/ProvidersListS2CPacket` + `client/gui/GuiProviderSelect` | ✅ 可用 |
| 配方映射持久化（内置 47+ 条 GT 配方池映射） | `util/RecipeNameUtil` + `common/RecipeMapNameConfig` + `client/ClientRecipeNameUtil` | ✅ 可用 |
| **NEI 配方页 AE 角标**（编码样板图标 + 数量角标） | `mixin/nei/MixinNEIRecipeWidget` | ✅ 可用（3.1.0 改为注入 `draw(II)V`，功能恢复） |
| NEI 书签面板数量/可合成角标 | `mixin/nei/MixinPanelWidgetDraw` + `client/NetworkInventoryDrawHandler` | ✅ 可用 |
| NEI 悬浮 tooltip（青色存量 + 绿色可合成；流体容器直接显示 `mB` 流体量） | `client/nei/NetworkTooltipHandler` | ✅ 可用 |
| NEI Shift+左键提取 / 中键合成下单 | `mixin/nei/MixinPanelWidgetClick` + `network/ExtractItemPacket`/`RequestCraftingPacket`/`CraftingResponsePacket` | ✅ 可用（3.0.1 修复服务器踢出） |
| 合成完成通知（屏幕横幅 + 音效） | `mixin/ae/MixinCraftingCPUCluster` + `network/CraftingCompletePacket` + `client/render/CraftingNotificationOverlay` | ✅ 可用 |
| 合成重新规划（Replan） | `mixin/ae/MixinGuiCraftConfirm` + `network/ReplanPacket` + `util/Replanner` | ✅ 可用 |
| 强化 IO 端口（方块 + 传输倍率） | `block/BlockExIOPort` + `tile/TileExIOPort` + `mixin/ae/MixinTileIOPort` | ✅ 可用 |
| 无限水岩浆磁盘 | `item/ItemInfinityWaterLavaCell` | ✅ 可用 |
| 无线收发器 + 无线连接器（跨维度 + 直连绑定） | `wireless/` 包全套 | ✅ 可用 |
| 石英切割刀 Shift+右键复制方块/AE部件/GT机器名 | `client/event/KnifeNameCopyHandler` | ✅ 可用 |
| F 键将鼠标下物品名填入终端搜索框 | `client/event/KeyInputHandler` | ✅ 可用 |
| 叠加层开关 `/apu-overlay` + GUI OV 按钮 | `client/CommandOverlay` + `client/OverlayConfig` | ✅ 可用 |
| **智能倍增（Smart Doubling）**：ME 接口/样板输入机复选框 + CPU 一次性推送 N 轮（默认不限 0=不限，可配） | `api/ISmartDoublingMedium` + `mixin/ae/MixinDualityInterface` + `mixin/ae/MixinCraftingCPUCluster` + `mixin/ae/MixinGuiInterface`/`MixinContainerInterface` + `mixin/gt/MixinMTEHatchInputBus` | ✅ 可用（3.2.0；3.3.2 兼容 GTNotLeisure 超级接口；3.3.3 支持 GT/SNL/PH 样板输入机；3.3.5 修复实测失效；3.3.6 默认 0=不限；3.3.7 批量记账/功率 O(1) 修复大订单卡死） |
| 统一配置文件 `settings.json` + 热加载 + OP 命令 `/ae2qof reload` + 游戏内配置 GUI（含范围显示 + 名字映射热编辑） | `Config` + `CommandAe2QoL` + `client/gui/ConfigGuiFactory`/`GuiConfigScreen` + `network/ConfigSetPacket`/`ConfigUpdatePacket` | ✅ 可用（3.3.0；3.3.6 新增 GUI 页面；3.3.7 范围显示 + 映射编辑） |
| **F：样板 + 接口二合一终端**（独立有线方块） | `merged/GuiMergedTerminal` + `ContainerMergedTerminal` + `PatternContainer` + `BlockMergedTerminal`/`TileMergedTerminal` + `client/event/MergedTerminalPanelHandler` + `client/gui/MergedPanelLayout` + `network/MergedTerminalActionPacket`/`MergedTerminalResultPacket` + `api/IMergedPatternTerminal` | ✅ 可用（3.4.0 起；3.5.0 改为独立有线方块 + 原生 AE2Things 风格面板，移除两个 mixin；3.5.1 修复 openContext NPE 崩溃/NEI 返回错位/处理样板改终极样板/网络拉空白样板/NEI「+」与 `↓` 读回/隐藏 NEI 面板）；**3.6.0 新增：样板回读二次编辑、编辑快照持久化、PH 编程工具箱适配、装配矩阵上传按钮（AM）、流体解析严格匹配修复、数量上限移除与输出格禁编、覆盖层模态化 |
| **自适应电网系统**（终端+4仓+监控+列表） | `hatch/adaptive/AdaptiveNetTerminal`/`AdaptiveNetHatch`/`AdaptiveNetLaserHatch`/`AdaptiveNetDynamoHatch`/`AdaptiveNetLaserTargetHatch` + `AdaptiveNetwork`/`AdaptiveNetworkManager`/`AdaptiveHatchHelper`/`HatchType`/`HatchListCache`/`GridEnergyStats` + `network/HatchListSyncPacket`/`HatchActionPacket` | ✅ 可用（3.17.0；3.18.0 重构为4仓类型+4-tab UI+权限指纹+自动迁移；3.18.1-fix1 修复监控速率单位/列表dirty/50行上限） |

# 已知风险登记表

> 等级：🔴 高（可能崩溃/丢物/踢人） ｜ 🟡 中（潜在） ｜ 🟢 低。
> 状态：❌ 未修复 ｜ ✅ 已修复 ｜ ⏳ 已兜底（防踢但不防业务异常）。
> 本表用于每次改动后快速回归检查，避免修复引发恶劣问题。

| # | 风险 | 位置 | 等级 | 状态 |
|---|---|---|---|---|
| 1 | 合成通知 `(EntityPlayerMP)` 强转：假玩家/非 MP 玩家发起合成 → `ClassCastException` 打崩服务端线程 | `mixin/ae/MixinCraftingCPUCluster.java:90` | 🔴 | ✅ |
| 2 | 长合成任务期间玩家下线 → `completeJob` 遍历已断线玩家并 `sendTo` → NPE + 内存滞留 | `mixin/ae/MixinCraftingCPUCluster.java:74-90` | 🔴 | ✅ |
| 3 | 提取物品丢失：先 `MODULATE` 从网络扣物品，再塞背包；背包满/部分失败时**剩余物品凭空消失** | `network/ServerTerminalHelper.java:145-156` | 🔴 | ✅ |
| 4 | 样板撤回无所有权校验：共享网络可**窃取/清空**其他玩家接口中的样板 | `network/RecallPatternPacket.java` | 🔴 | ✅ |
| 5 | 全部 C2S 包在 **Netty IO 线程**执行，未 `addScheduledTask` 归队 → 并发访问 grid/container 与 tick 竞争（CME/状态不一致） | 所有 C2S Handler | 🔴 | ✅ |
| 6 | NEI 配方页 AE 角标注入点失效，功能不工作 | `mixin/nei/MixinNEIRecipeWidget.java:39` | 🟡 | ✅ |
| 7 | 样板上传无所有权校验：共享网络可向任意 provider 植入垃圾样板 | `network/UploadPatternPacket.java` | 🟡 | ✅ |
| 8 | GT `RecipeMap.ALL_RECIPE_MAPS` 全量反射扫描无速率限制 → 恶意连发卡服 | `network/RequestProvidersListPacket.java:146` | 🟡 | ✅ |
| 9 | IO 端口传输倍率 `itemsToMove *= rate` 极端配置下 long 溢出（配置上限 Integer.MAX_VALUE） | `mixin/ae/MixinTileIOPort.java:21` | 🟡 | ✅ |
| 10 | 收发器动作无权限校验 + 无 try/catch：可对任意坐标 Tile 发动作，异常踢人 | `network/WirelessActionPacket.java` | 🟡 | ✅ |
| 11 | 合成下单无全局 try/catch：服务端异常直接踢人 | `network/RequestCraftingPacket.java` | 🟡 | ✅ |
| 12 | 提取包无全局 try/catch + 恶意负数 count | `network/ExtractItemPacket.java` | 🟡 | ✅ |
| 13 | 交换样板反射字段 null → NPE；改库后未 `saveChanges` → 输出槽显示与真实内容不同步 | `network/SwapPatternPacket.java` | 🟡 | ✅ |
| 14 | 日志刷屏：流体缓存逐条 `LOG.info` / 书签每次传输 `System.out.println` | `mixin/nei/MixinGuiMEMonitorable.java` / `mixin/nei/MixinDefaultOverlayHandler.java` | 🟢 | ✅ |
| 15 | 所有自定义包 `fromBytes` 解码异常 → FML 断连踢玩家 | 全部 `network/*Packet` | 🔴 | ✅ 已兜底（3.0.1 全包 try-catch） |
| 16 | `CraftingResponsePacket` ItemStack 序列化字节错位 → `DecoderException` 踢人 | `network/CraftingResponsePacket.java` | 🔴 | ✅ 已修复（3.0.1 改 String 传输） |
| 17 | Replan 点击 `lists.clear()` 误清 map 导致 NPE 崩溃 | `util/Replanner.java` | 🔴 | ✅ 已修复（3.0.1） |
| 18 | 合成通知/IO 端口 mixin 在 `server` 列表单机不注入 → 功能无效 | `mixins.ae2_auto_pattern_upload.json` | 🟡 | ✅ 已修复（3.0.1 移入公共列表） |
| 19 | 流体误判：`FluidRegistry.getFluid(itemDamage)` 把 damage 命中流体 ID 的物品（damage=0→水）误判为流体 → 随机物品显示 mB 量 | `client/NetworkInventoryCache.java`（3.0.2 引入，3.1.2 修复） | 🟡 | ✅ 已修复（3.1.2 改类名+NBT 识别，见 3.1.2 条目） |
| 20 | 智能倍增 `@Overwrite executeCrafting` 全量重写 CPU 主循环：移植偏差导致合成丢物/倍率错账；N× 放大 long 溢出 | `mixin/ae/MixinCraftingCPUCluster.java` | 🔴 | ✅ 已修复（3.2.0 逐行移植 + N==1 走原版路径；反射失败安全降级；3.3.5 改 GTLCore 式单次推送，功率/原料不足按可提取轮数钳制 N，异常回退原版） |
| 21 | 智能倍增 `pushPattern` 只返回成功布尔，无实际轮数反馈：部分提取/缓冲时 CPU 与接口记账不一致 → 超产或漏产 | `mixin/ae/MixinCraftingCPUCluster.java` + `MixinDualityInterface.getMaxMultiplier` | 🟡 | ✅ 已修复（3.3.5 GT/PH 按实际推送轮数记账；PH 走 `pushPatternMulti` 返回实际轮数，GT 单次 `pushPattern` 收 N× 一次记账 N 轮） |
| 22 | 配置文件热加载：`settings.json` 语法错误 / 值越界 / 编辑中途被读取 → 解析失败或字段不一致 | `Config.reload` + `ensureFresh`（3.3.0 引入） | 🟢 | ⏳ 已兜底（解析失败保留上次生效值；数值越界 clamp 回默认；mtime 校验限流 1 秒一次） |
| 23 | mixin 冲突：`@Overwrite executeCrafting` 整体替换方法体 → 其它模组（ProgrammableHatches `MixinInstantComplete`）对同一方法的 `@Inject/INVOKE` 找不到注入点崩溃 | `mixin/ae/MixinCraftingCPUCluster.java`（3.2.0 引入 @Overwrite） | 🔴 | ✅ 已修复（3.3.1 改 `@Inject(HEAD)+cancel`，保留原方法字节码结构；仅在存在智能倍增任务时接管 tick） |
| 24 | `@GuiSync(19)` 与 GTNotLeisure `ContainerSuperInterface` 的 `@GuiSync(19) sidelessMode` 同 id 冲突 → `DataSynchronization.collectFields` 遍历类层级发现重复 key 抛 `IllegalStateException` 崩溃 | `mixin/ae/MixinContainerInterface.java`（3.2.0 引入 @GuiSync(19)） | 🔴 | ✅ 已修复（3.3.2 改 `@GuiSync(30)`，高于 AE2 链内 18 且不与 GTNL 19 冲突） |
| 25 | `MixinMTEHatchInputBus` 应用于 GT 输入仓全族（普通总线/补货输入仓/样板输入仓），注入的 NBT 保存/加载方法对全类族生效 | `mixin/gt/MixinMTEHatchInputBus.java`（3.3.3 引入） | 🟢 | ⏳ 已兜底（`instanceof ICraftingProvider` 门控，仅样板输入机启用；开关默认关闭；NBT key `ae2qolSmartDoubling` 带独立前缀不冲突） |
| 26 | GT/PH/GTNL ModularUI 与 GUI 按钮注入点偏移：GTNL 超级二合一接口方块/面板两形态布局相差 18px；PH 面板布局不同 → 按钮不显示或遮住翻页 | `mixin/gt/MixinMTEHatchCraftingInputMEGui.java` + `mixin/gt/MixinDualInputHatchUI.java` + `mixin/ae/MixinGuiSuperDualInterface.java`（3.3.3 引入） | 🟢 | ⏳ 已兜底（GTNL 按 host 形态动态计算偏移；注入点经 javap 逐一验证存在；配置级 `required=false`，注入失败仅警告不崩溃） |
| 27 | 自动上传把 GT/PH 样板输入机当普通 `IInventory`（GT `IMetaTileEntity extends ISidedInventory`），通用库存排样板槽之前 → 编码样板误投进原料缓存槽，多方块收不到配方 | `network/UploadPatternPacket.java` + `network/RequestProvidersListPacket.java` + `network/RecallPatternPacket.java`（3.3.4 修复） | 🟢 | ✅ 已修复（3.3.4 改用 `IInterfaceViewable.getPatterns()` 优先定位专属样板槽；GT/PH 写入后 `markDirty` 持久化，`setInventorySlotContents` 触发机器内部重建与网络同步） |
| 28 | 智能倍增（GT/PH 样板输入机）实测失效：功率门槛 `sum*effectiveN` 不足即 `continue` 跳过介质（无回退）→ CPU 永不推送；`getExtractItems` 严格全量匹配导致 N 静默降为 1 → 勾选后完全无效 | `mixin/ae/MixinCraftingCPUCluster.java`（3.3.3 引入） | 🔴 | ✅ 已修复（3.3.5 改 GTLCore 式：原料不足按 SIMULATE 可提取轮数钳制 N、功率不足逐轮下调 N、N==1 走原版路径、`onExecuteCrafting` try/catch 异常回退原版不拖死 CPU） |
| 29 | 游戏内配置 C2S 包无权限校验：任何玩家可改 `settings.json`（改 `io_port_rate` 刷倍率/改倍增上限） | `network/ConfigSetPacket.java`（3.3.6 引入） | 🟡 | ✅ 已修复（服务端 `canCommandSenderUseCommand(2, "ae2qof")` OP 校验 + key/范围白名单校验，非 OP 直接丢弃） |
| 30 | 智能倍增 0=不限后 `N` 可能等于剩余全部轮数：`remaining` 为 long 但推送侧用 int，>2^31 时 `(int)` 强转溢出为负 → 死循环/错账 | `mixin/ae/MixinCraftingCPUCluster.java` `ae2qol$smartMultiplier`（3.3.6 引入） | 🔴 | ✅ 已修复（`cap = (int) Math.min(cap, Math.min(remaining, Integer.MAX_VALUE))` 封顶；GT 仓 `getMaxMultiplier` 返回 `Integer.MAX_VALUE`，`roundSize*mid` 为 long ≤2^62 不溢出） |
| 31 | 智能倍增 0=不限且下单 10000+ 轮时 `ae2qol$accountSmartPush` 逐轮记账：大 N 下单有可见卡顿 | `mixin/ae/MixinCraftingCPUCluster.java`（3.3.6 行为变更） | 🟢 | ✅ 已修复（3.3.7 批量记账 + 功率钳制 O(1) + int 溢出钳制） |
| 32 | 批量记账后 `waitingFor` 单栈数量可超 `Integer.MAX_VALUE`（long 承载，语义等价） | `mixin/ae/MixinCraftingCPUCluster.java`（3.3.7） | 🟢 | ⏳ 已兜底（long 算术，无溢出；AE 内部以 long 承载堆叠） |
| 33 | O(1) 功率钳制与逐轮递减存在 <0.01 AE 的舍入差 | `mixin/ae/MixinCraftingCPUCluster.java`（3.3.7） | 🟢 | ⏳ 已兜底（保留原版 `requiredPower - 0.01` 兜底判断，行为一致） |
| 34 | `ClientState.removeRememberedProvider` 为新增客户端方法，仅本地生效 | `client/ClientState.java`（3.3.7） | 🟢 | ✅（映射本就仅客户端使用） |
| 35 | 配置页两页切换 `initGui` 重建控件，字段值需保留 | `client/gui/GuiConfigScreen.java`（3.3.7） | 🟢 | ✅ 已核对（TextField 对象复用，跨页不清空） |
| 36 | 二合一终端面板混入依赖 `@Shadow` MCP 名运行时解析（GTNH 去混淆环境正常；若未来 SRG 重映射则面板失效） | `mixin/ae/MixinGuiInterfaceTerminal.java`（3.4.0） | 🟢 | ✅ 已移除（3.5.0 删除该 mixin，改为直接继承 `GuiInterfaceTerminal`，不再依赖 `@Shadow`） |
| 37 | `InterfaceTerminalList` 为私有内部类 → 面板按钮/槽点击需绕过 masterList 手动分发；面板区域与视图口右缘重叠 | `merged/GuiMergedTerminal.java`（3.4.0 起） | 🟢 | ✅ 已兜底（3.5.0 直接 override `mouseClicked`，`isInPanel` 判定优先于列表项；按钮 id 940-953 高于列表项；重叠区面板优先，用户反馈后调整） |
| 38 | 二合一终端上传：编码槽为空或 NBT 无 `apu:recipeMap` 且无后备时静默返回，无提示 | `client/event/MergedTerminalPanelHandler.java` `handleUpload`（3.4.0） | 🟢 | ⏳ 已兜底（不抛异常；编码后上传走 recipeMap 分支；升级走 ICraftingPatternDetails 降级） |
| 39 | NEI 配方填充（`MixinGuiRecipe`）依赖 NEI 配方页 GUI 内部结构，NEI 版本升级可能失效（仅影响 `N` 按钮） | `mixin/nei/MixinGuiRecipe.java`（3.4.0） | 🟡 | ⏳ 已兜底（`required=false` + try/catch，失败仅 N 按钮无效，其余面板功能正常） |
| 40 | 二合一终端编码/清空/×2/模式包无服务端权限校验，但仅作用于玩家自身打开的容器 | `network/MergedTerminalActionPacket.java`（3.4.0） | 🟢 | ⏳ 已兜底（操作限于玩家 own container，无网络侧越权面） |
| 41 | 面板悬垂区绘制采用 `xSize=1000` 放大法：槽位命中依赖 `GuiContainer.getSlotAtPosition` 使用 `guiLeft/guiTop` 字段（不随 xSize 重算），已验证不破坏槽点击 | `merged/GuiMergedTerminal.java` `drawScreen`（3.5.0） | 🟢 | ✅ 已兜底（javap 核对 `func_146978_c`/`getSlotAtPosition` 用字段坐标；`initGui` 按 xSize=209 计算 guiLeft，命中逻辑不受影响） |
| 42 | 面板按钮/滚动条/页码为客户端静态字段，仅随 GUI 打开重置；多容器/多窗口切换时由每帧 `reposition` 从客户端容器刷新覆盖 | `client/event/MergedTerminalPanelHandler.java`（3.5.0） | 🟢 | ✅ 已兜底（drawFG 每帧以 `pc.isCraftingMode()/isInverted()/getActivePage()` 重刷静态，状态不串窗口） |
| 43 | `GuiTabButton` 图标渲染需 `RenderItem`：反射读 `GuiScreen.itemRender`（protected static），失败回退 `new RenderItem()` | `client/event/MergedTerminalPanelHandler.java` `getRenderItem`（3.5.0） | 🟢 | ✅ 已兜底（try/catch + 回退，反射失败仅 tab 图标缺失，不影响按钮功能） |
| 44 | 智能倍增 PH 介质记账缺陷：`useMulti && effectiveN>1` 时按 **1 轮量**提取材料（target 不乘 N），若 `pushPatternMulti` 返回 `accepted==0`（介质忙/缓冲满）则落到下方 `pushPattern` 单发回退分支；该分支因 `effectiveN>1` 走 GT 倍增记账 → 实际只交付 1 轮材料却**扣 N 轮功率、executedTasks+=N、taskValue-=N** → 合成少产出 N-1 轮、白扣功率、任务提前假完成（材料未丢，留在网络存储，但订单数量错误） | `mixin/ae/MixinCraftingCPUCluster.java:764-839`（回退分支判定应为 `!useMulti && effectiveN>1`；useMulti 回退时须走原版逐轮记账） | 🔴 | ✅ 已修复（→ #71，2026-08-23） |
| 45 | 网络包 OOM DoS：`readItemStackArray` 直接 `new ItemStack[buf.readInt()]` 无上界钳制——恶意 C2S 包 len=2^31-1 触发瞬时巨量分配（分配先于读取发生，外层 catch(Throwable) 接不住已打爆的堆压力）；同类 S2C 预分配 `ArrayList<>(readInt())` 见 `ProvidersListS2CPacket` / `WirelessHighlightPacket` / `WirelessChannelSyncPacket`（低危：服务端→自己客户端）。修复：四处解码长度钳制（镜像 `MergedTerminalActionPacket` 超界归零风格）——配方数组 ≤64、供应器/高亮列表 ≤1024、频道列表 ≤256，超界按空容器处理，合法包不受影响 | `network/RequestProvidersListPacket.java:107-114` 等 4 文件 | 🟡 | ✅ 已修复（2026-08-23） |
| 46 | `CraftingCompletePacket`(S2C) Handler 未切客户端主线程：Netty IO 线程直接向非线程安全 `ArrayDeque`（CraftingNotificationOverlay.events）add，渲染线程并发 poll/draw → 数据竞争偶发崩溃/渲染异常；为全部 S2C 包中唯一漏归队者（其余均已 `func_152344_a`）。修复：Handler 业务逻辑包进 `mc.func_152344_a` 归队主线程，镜像项目内其余 S2C 包既有模式 | `network/CraftingCompletePacket.java:56-65` | 🔴 | ✅ 已修复（2026-08-23） |
| 47 | 无线高亮开关失效于专用服务器：`handleToggleHighlight` 在**服务端**读客户端静态字段 `ClientState.highlightEnabled`（仅客户端 WirelessHighlightPacket.Handler 写入），专用 JVM 恒 false → 高亮只能开不能关（单机同 JVM 共享静态字段才碰巧正常）。修复：目标状态由包参数携带——客户端发送 `ACTION_TOGGLE_HIGHLIGHT` 时传 `!ClientState.highlightEnabled`（modeValue 字段在该动作中原本空闲），服务端改用 `msg.modeValue`，无状态、专用服/单机行为一致 | `network/WirelessActionPacket.java:266` + `wireless/gui/GuiWireless.java:339` | 🟡 | ✅ 已修复（2026-08-23） |
| 48 | NEI 叠加层开关双问题：① `/apu-overlay` 注册于 `ClientProxy.serverStarting` → 仅单人/局域网主机存在命令，**专用服务器不可用**（只能用 OV 按钮）；② 多人时 GUI OV 按钮写本地 settings.json，会被登录时 `ConfigUpdatePacket` 的服务端值覆盖 → 开关权威归属混乱（本地 vs 服务端二义性）。修复（按决策仅修②+README 注明①）：NEI 叠加层为纯客户端渲染功能，登录同步不再覆盖客户端本地 `nei_overlay_enabled`——`Config.applyAll` 去掉 overlay 参数（包字段保留作协议兼容），README 注明 `/apu-overlay` 仅单机/局域网可用、专用服用 OV 按钮、开关为纯客户端本地设置 | `network/ConfigUpdatePacket.java` + `Config.java applyAll` + `README.md` | 🟡 | ✅ 已修复（2026-08-23） |
| 49 | 库存缓存生命周期。**最终方案：时间窗过期（5 分钟）**——用户需求模型：① 开终端时显示；② 开终端状态下进 NEI 配方界面（GuiRecipe）依然显示；③ 彻底退出终端后可不显示。技术约束：进 GuiRecipe 底层也会关闭终端容器（MC 单 GUI），"GUI 关闭即清"会误伤场景②，"永不过期"违背③。实现：`hasData()` 统一闸门加 `now - lastUpdateTick <= STALE_MS(5min)` 判断；终端开启期间 postUpdate 持续刷新时间戳永不过期，关闭后保留 5 分钟供查配方，超时 tooltip/书签角标自动消失。曾尝试 onGuiClosed 清缓存被用户反馈回退 | `client/NetworkInventoryCache.java` hasData + STALE_MS | 🟡 | ✅ 已修复（2026-08-23 用户需求澄清后） |
| 50 | 无线全局频道无归属权：任何玩家打开任意收发器 GUI 即可 `ACTION_REMOVE_CHANNEL` 删除他人全局频道并强制拆除发送端连接（共享服务器干扰向量）；频道应有创建者归属或 OP 保护 | `network/WirelessActionPacket.java` `handleRemoveChannel` | 🟡 | ❌ 未修复（2026-08-23 审查发现） |
| 51 | 智能倍增探测开销：ME 接口容量二分探测固定 ~31 轮 do-while 且每次推送重跑；全网电力探测 `extractAEPower(MAX_VALUE, SIMULATE)` 为 O(P) 网格遍历；大网络高频推送时有放大效应。修复：① 容量探测改"指数扩张 + 区间二分"——先倍增 probe 找失败点再在小区间二分，常见小容量场景从 31 次降到个位数次 `simulateAddStack`（顺带修正原实现未实测 1 轮的边界，机器满仓时准确回退逐轮）；② 功率探测查询上界改为 `sum × min(effectiveN, 4096)`——AE2 `simulateExtract` 凑够即停，有限查询通常 O(1)，电量充足时视为"电不是瓶颈"，单次推送封顶 4096 轮、剩余下一 tick 继续 | `mixin/ae/MixinDualityInterface.java:122-133` + `mixin/ae/MixinCraftingCPUCluster.java:690-706` | 🟢 | ✅ 已修复（2026-08-23） |
| 52 | 客户端渲染热路径浪费（叠加层全开 + 大网络时有可感知 GC 抖动）。修复 ①②③：① 合成通知横幅 `new RenderItem()` 每帧分配 → 静态缓存复用（dev 字段名 itemRender 与 SRG 名 field_146296_j 都尝试——原 MergedTerminalPanelHandler 同类实现在生产环境因只试 dev 名实际仍每帧 new）；② tooltip 路径同一 ItemStack 流体识别/NBT 解析 3 遍 → `NetworkInventoryCache.query()` 单次合并返回 count/craftable/fluid；③ `CountFormatter.format` 加单槽记忆化（仅渲染线程调用，无并发）。④ 无线高亮 Tessellator 批次合并**暂缓**：高亮方块数量通常个位数、独立提交开销可忽略，方案收益不抵复杂度 | `client/render/CraftingNotificationOverlay.java:103` + `client/nei/NetworkTooltipHandler.java` + `util/CountFormatter.java` + `client/NetworkInventoryCache.java` | 🟢 | ✅ 已修复（④暂缓）（2026-08-23） |
| 53 | README 功能 9 称无线连接器「支持跨维度」，审查判定实现拒绝跨维度绑定。**复核结论：审查误判，跨维度功能实际已完整支持，无需修改**——① L119 拒绝分支不可达（玩家只能右键自己所在维度的方块，te.getWorldObj() 与 player.worldObj 恒同维度）；② 各维度分别绑定后由 `WirelessBlockLinkManager.processAll` 按 `link.dimension` 用 `DimensionManager.getWorld` 跨维度取 World 建链；③ AE2 `GridConnection` 构造无维度校验（与收发器对同款机制）；④ 存档经主世界 `WirelessWorldData` 持久化 + `restoreFromWorldData` 启动恢复。已给死分支加注释标记 | `wireless/ItemWirelessConnector.java:119` + `README.md` 功能 9 | 🟢 | ✅ 已复核无问题（2026-08-23） |
| 54 | 调试日志残留：8 个文件仍有 `System.out.println`；另多处 handler 用 `t.printStackTrace()`。已清理：8 文件 println 全部删除（grep 归零）、17 处 printStackTrace 换 logger；仅 CommonProxy（6 处）/MyMod（1 处）入口保留——preInit 早期阶段 logger 可能未就绪，属既定保留 | 8 个源文件 + CommonProxy/MyMod | 🟢 | ✅ 已修复（2026-08-23，状态补记） |
| 55 | 死代码遗留（按仓库规范仅标记暂不删除）：`ClientState.lastProviderName` 字段及 `clear()`、`NetworkInventoryCache.getLastUpdateTick()` 与 put 冗余 count 参数、BUTTON_HALVE_ID 死分支、Replanner 吞异常路径等 | `client/ClientState.java` 等多处 | 🟢 | 📌 已按规范标记，不删除（2026-08-23） |
| 56 | `docs/MOD_MAP.md` 为空模板（功能↔源码映射缺失、Mixin 列表未登记），违反文档驱动开发规范 §5.1；新开发者无法按图索骥 | `docs/MOD_MAP.md` | 🟢 | ✅ 已修复（2026-08-23，随 #73 commit 填充主逻辑映射表 + Mixin 列表） |
| 57 | S2C 无应用层尺寸预算：超大网络供应器列表（ids+names+emptySlots 三列表）可超 1.7.10 自定义负载 ≈32KB 上限 → 发送侧失败、上传选择界面静默无响应。修复：服务端发送前按预算（32000 字节，留 FML 头部余量）逐条估算序列化尺寸、截断尾部供应器并记录 warn 日志；recipeMap 占用一并计入预算 | `network/RequestProvidersListPacket.java` handleMessage | 🟢 | ✅ 已修复（2026-08-23） |
| 58 | 智能倍增部分提前 return 分支疑似遗漏 `parallelismProvider.put(details, mediumListCheck)` 回写 → 并行度信息丢失致下 tick 重探测（轻微性能损耗；静态审查标记，修 #44 时一并复核） | `mixin/ae/MixinCraftingCPUCluster.java:790-793` 附近 | 🟢 | ✅ 已复核无问题（→ #72，2026-08-23） |
| 59 | 二合一终端编码产出坏样板：`encodeItemPattern` 对未填满的输出列把 null 槽写成**空 NBT compound** → `UltimatePatternHelper` 解码后 `getAEOutputs()` 含 null → `CraftingGridCache.setPatternsFromCraftingMethods:340 out.copy()` NPE。PH 仓（22179）每 tick `postMEPatternChange` 重扫即每 tick NPE 刷屏 + **AE2 合成缓存重建被中断** → CPU 永远收不到样板。原生终端 `getOutputs()` 跳过空槽故从不触发。修复：编码循环过滤 null/空槽（对齐原生语义） | `merged/PatternContainer.java` `encodeItemPattern`（3.4.0 引入） | 🔴 | ✅ 已修复（2026-08-23 实测 22179 每 tick NPE 后定位） |
| 60 | 二合一终端上传静默失效：`UploadPatternPacket.resolveTerminal/resolveOutputSlot` 仅支持原生 `ContainerPatternTerm(Ex)`，无合并终端分支 → 服务端解析返回 null 直接 return，上传从未执行；撤回因依赖 lastProviderId 连带失效。修复：resolveTerminal 委托 `ContainerTerminalResolver`（已覆盖三种终端），resolveOutputSlot 增加 `IMergedPatternTerminal.getMergedEncodedSlot()` 分支 | `network/UploadPatternPacket.java:146-170`（3.4.0 引入） | 🔴 | ✅ 已修复（2026-08-23） |

> **#59 存量坏样板清理指引**：编码修复仅防新增。若网络中已插入坏样板（症状：22179 每 tick NPE），需打开该 PH 编程样板输入总成 GUI，手动取出坏样板销毁后重新编码上传。
| 61 | 处理配方池反查不准：「钢锭高炉配方被识别成电解机」。同一输入物品常存在于多个 GT 配方池，服务端 `RecipeMapDetector` 按 HashMap 无序遍历仅凭输入反查会随机命中；且编码时服务端反查结果**优先于** NEI 转写捕获的精确 `pendingRecipeMap`（顺序颠倒）。修复：① 编码时 pendingRecipeMap 优先、反查兜底；② 反查命中后用用户填写的输出物与 `GT_Recipe.mOutputs` 校验，输入+输出双匹配才确定，仅输入匹配降为候选 | `merged/PatternContainer.java` `encodeItemPattern` + `util/RecipeMapDetector.java` | 🟡 | ✅ 已修复（2026-08-23 用户实测反馈） |
| 62 | 二合一终端上传↑按钮左侧 3px 点击盲区：按钮容器坐标 [206,218] 横跨面板判定区左边界（x≥209），越界部分点击走原生逻辑静默失效。修复：面板按钮命中检测移出 isInPanel 判定，按 id 白名单（940-955）独立分发，不误抢 AE 原生按钮 | `merged/GuiMergedTerminal.java` `mouseClicked` + `client/event/MergedTerminalPanelHandler.java` `isPanelButton` | 🟢 | ✅ 已修复（2026-08-23） |
| 63 | 上传链路零日志：#54 清理时把上传/撤回全链路的调试输出一并删除且未留 logger 记录，故障排查无迹可循（本次"点击没反应"即无法定位）。修复：关键分支补 `MyMod.LOG.info("[Upload] ...")`——客户端 handleUpload 各 return 分支/策略选择、服务端容器解析失败/供应器查找失败/写入成功各一条，单次点击最多数条不刷屏 | `client/event/MergedTerminalPanelHandler.java` + `network/RequestProvidersListPacket.java` + `network/UploadPatternPacket.java` + `network/ProvidersListS2CPacket.java` | 🟢 | ✅ 已修复（2026-08-23） |
| 64 | **二合一终端服务端终端解析永久失败（#60 的真正根因）**：`ContainerTerminalResolver.resolveTerminal` 对合并终端分支反射读取 AE2 `ContainerInterfaceTerminal.anchor` 字段——但 3.5.0 重构后 `ContainerMergedTerminal` 是独立 `AEBaseContainer` 子类（自有 `anchor` 字段，类型 `IInterfaceTerminal extends IActionHost`），不再继承原生容器 → NoSuchFieldException 被吞 → 返回 null。诊断日志实测：`[Upload] server: terminal resolve failed for ContainerMergedTerminal`。**上传/撤回/供应器列表请求三条链路的服务端环节全部因此静默失败**。修复：反射改为沿容器类层级查找自有 `anchor` 字段（findDeclaredField 逐级向上），命中后强转 IActionHost | `util/ContainerTerminalResolver.java`（3.4.0 引入错误目标，3.5.0 重构后必然失败） | 🔴 | ✅ 已修复（2026-08-23 日志定位） |
| 65 | 操作链路诊断日志大检查：撤回（[Recall] 服务端 8 个分支+结果）、编码（[Encode] 面板空/非样板拦截/空白样板不足/成功含 recipeMap）、交换（[Swap] 输出槽缺失/少于2格）全部补齐 logger 日志，与 [Upload] 统一前缀便于 grep 排查 | `network/RecallPatternPacket.java` + `merged/PatternContainer.java` + `network/SwapPatternPacket.java` | 🟢 | ✅ 已补齐（2026-08-23） |
| 66 | 面板槽点击取物时而无效：自发 `windowClick`（vanilla C07 包）在服务端 `Container.slotClick` 对 SlotFake 系假槽行为不完整，且无客户端本地预测——服务端拒绝时 GUI 永不变化，表现为"点不掉、拖动才消失"。修复：镜像原生 `AEBaseGui.handleClickOrDragFakeSlot`——改发 `PacketClickOrDragFakeSlot`（含 NEI 幽灵物品支持，复用父类 getStackFromHand）+ 客户端本地 putStack 预测。不能落 super 是因为 GuiInterfaceTerminal.mouseClicked 的 masterList 判定会吞掉面板悬垂区点击 | `merged/GuiMergedTerminal.java` `mouseClicked`（3.5.0 引入 windowClick 方案） | 🔴 | ✅ 已修复（2026-08-23 用户实测反馈） |
| 67 | 上传成功后接口列表不实时更新：外部代码写入 provider 样板不会触发接口终端增量推送（原生仅 GUI 内操作走 syncIfaceSlot），需重开 GUI 才能看到。修复：UploadPatternPacket 写入成功后对打开中的合并终端容器调 `scheduleFullUpdate()`（forceNextUpdate 机制与原生一致，下 tick 全量 updateList 推送） | `merged/ContainerMergedTerminal.java` + `network/UploadPatternPacket.java` | 🟡 | ❌ 升级为 #68 |
| 68 | #67 的 scheduleFullUpdate 无效根因：`updateList()` 移植自原生但**缺失样板内容对比**——tracked 分支仅对比名字/在线/可见性/尺寸/优先级，从不比较样板槽内容 → forceNextUpdate 跑完判定"无变化"返回 null 不发包。原生同款限制（外部写入本就不实时）。修复：InvTracker 增加 slotCache 快照 + hasContentChanged() 逐槽对比；updateList 加 checkContents 参数，仅在调度刷新时对比内容并生成 overwrite 条目（常规 tick 不做对比避免逐槽开销） | `merged/ContainerMergedTerminal.java` updateList + InvTracker | 🔴 | ✅ 已修复（2026-08-23） |
| 69 | 新增：面板槽位滚轮调数量。悬停面板输入格滚动滚轮：上滚 +1、下滚 -1、**最小保持 1**（清空用左键取出或中键设 0；输出格禁改）；Shift+滚轮保留 OreDict 替换循环 | `merged/GuiMergedTerminal.java` `mouseWheelEvent` | 🟢 | ✅ 已实现（2026-08-23 用户需求，下限按反馈调整） |
| 70 | 撤回后接口列表不实时更新（#68 的撤回侧遗漏）：RecallPatternPacket 取走样板后未调度刷新。修复：撤回成功且玩家打开合并终端时同样调用 scheduleFullUpdate() | `network/RecallPatternPacket.java` | 🟡 | ✅ 已修复（2026-08-23 用户实测反馈） |
| 71 | 智能倍增 PH 介质记账缺陷（审查登记 #44）：`useMulti` 时按 **1 轮量**提取材料，若 `pushPatternMulti` 返回 `accepted==0`（介质忙/缓冲满）回退 `pushPattern` 单发成功后，因 `effectiveN>1` 走 GT 倍增分支 → 实际只交付 1 轮材料却**扣 N 轮功率、executedTasks+=N、taskValue-=N** → 合成少产出 N-1 轮、白扣 (N-1)×sum 功率、任务提前假完成。修复：倍增记账判定改为 `!useMulti && effectiveN>1`，useMulti 回退走原版逐轮路径（按实际交付的 1 轮记账） | `mixin/ae/MixinCraftingCPUCluster.java` executeCrafting 倍增回退分支 | 🔴 | ✅ 已修复（2026-08-23） |
| 72 | 审查登记 #58 复核结论：智能倍增所有退出路径（3 处提前 return、break 跳出后方法尾部 L941）均已有 `parallelismProvider.put` 回写，静态审查疑虑不成立，无需修改 | `mixin/ae/MixinCraftingCPUCluster.java` | 🟢 | ✅ 已复核无问题（2026-08-23） |
| 73 | 智能倍增大订单（如 1T）客户端无响应：GT `MTEHatchCraftingInputME.isBusy()` 始终返回 false → `knownBusyMediums` 永远不被填充 → do-while 循环每 tick 重复推送 `effectiveN` 轮（可达 Integer.MAX_VALUE），ME 网络每 tick 大额 extractItems + postChange → 客户端被海量物品更新淹没。修复：① `ae2qol$executeCraftingSmart` 开头 `knownBusyMediums.clear()` 重置跨 tick 残留；② GT/PH 路径倍增推送成功后 `knownBusyMediums.add(medium)` 冷却，防止同 tick 重复推送 | `mixin/ae/MixinCraftingCPUCluster.java` executeCraftingSmart + GT/PH 推送分支 | 🔴 | ✅ 已修复（2026-08-23） |
| 74 | **专用服务器网络包半注册**：10 个 S2C Handler 类内直引 client 类型（致命点为 `mc.thePlayer`——其字段声明类型 `EntityClientPlayerMP` 仅客户端存在）。注册时 `SimpleNetworkWrapper.instantiate()` 触发类链接验证 → 解析该字段引用失败 → `NoClassDefFoundError`，被 `MyMod.preInit` try/catch 吞掉后**注册在 id=11 处中断，id≥11 共 15 个包未注册**。症状：专用服上 NEI 上传/提取正常（id<11），但二合一终端「转移合成表/切模式/AM 上传」（id=17 MergedTerminalActionPacket）全部 `Undefined message for discriminator 17` 踢人；单机永不复现（JVM 含 client 类）。修复：① 10 个 Handler 全部壳化——onMessage 仅经 `MyMod.proxy`（声明类型 CommonProxy）分发到 ClientProxy override 实现，network 包零 client 引用（编译期保证）；② 顺带统一 CraftingResponse/ReplaceCandidates 的主线程归队（审查遗留 ⚠️）；③ MyMod 注册 fail-fast（半注册宁可启动失败） | `network/` 全部 10 个 S2C 包 + `CommonProxy.java` + `ClientProxy.java` + `MyMod.java:42-53` | 🔴 | ✅ 已修复（2026-08-24 云面板服实测崩溃定位） |

# 回滚指南

| 目标版本 | 使用 jar | 说明 |
|---|---|---|
| 3.8.1（当前） | `build/libs/AE2-QoL-3.8.1.jar` | 专用服务器 P0 修复：S2C Handler 壳化 + proxy 分发（#74），修复半注册踢人 |
| 3.8.0 | `build/libs/AE2-QoL-3.8.0.jar` | 全量 Tooltip + GuideNH 游戏内指南（**专用服务器存在 #74 踢人 bug，勿用于专用服**） |
| 3.6.1（当前） | `build/libs/AE2-QoL-3.6.1.jar` | 深度审查修复批次：智能倍增冷却/探测优化、网络包安全钳制、S2C 归队、高亮开关专用服修复、渲染热路径优化、缓存时间窗过期 |
| 3.6.0 | `build/libs/AE2-QoL-3.6.0.jar` | 面板体验升级：样板回读二次编辑 / 编辑快照持久化 / PH 编程工具箱适配 / 装配矩阵上传按钮 / 流体解析严格匹配修复 / 数量上限移除与输出格禁编 |
| 3.5.1 | `build/libs/AE2-QoL-3.5.1.jar` | 二合一终端修复：openContext NPE 崩溃 / NEI 返回错位 / 处理样板改终极样板 / 网络拉空白样板 / NEI「+」填充与 `↓` 读回 / 隐藏 NEI 面板 |
| 3.5.0 | `build/libs/AE2-QoL-3.5.0.jar` | F 模块改为独立有线方块「样板与接口终端」+ 原生 AE2Things 风格面板（4×4×2 页 + 滚动条 + 反转），移除两个 mixin |
| 3.4.0 | `build/libs/AE2-QoL-3.4.0.jar` | 样板 + 接口二合一终端（F 模块）+ 配置页「配方参考」子页 |
| 3.3.5 | `build/libs/AE2-QoL-3.3.5.jar` | 修复智能倍增实测失效：功率/原料不足按轮数钳制 N、PH 走 pushPatternMulti、异常回退原版 |
| 3.3.4 | `build/libs/AE2-QoL-3.3.4.jar` | 修复自动上传把样板误投进 GT/PH 样板输入机原料缓存槽 |
| 3.3.3 | `build/libs/AE2-QoL-3.3.3.jar` | 样板输入机（GT/SNL/PH）智能倍增 + 流体显示回归验证 |
| 3.3.2 | `build/libs/AE2-QoL-3.3.2.jar` | 修复与 GTNotLeisure 的同步 id 冲突崩溃 + 超级接口智能倍增 |
| 3.3.1 | `build/libs/AE2-QoL-3.3.1.jar` | 修复与 ProgrammableHatches 的 mixin 冲突崩溃 |
| 3.3.0 | `build/libs/AE2-QoL-3.3.0.jar` | 统一配置文件 + 热加载 + `/ae2qof` OP 命令 |
| 3.2.0 | `build/libs/AE2-QoL-3.2.0.jar` | 智能倍增（Smart Doubling） |
| 3.1.2 | `build/libs/AE2-QoL-3.1.2.jar` | 修复流体误判显示 bug |
| 3.1.1 | `build/libs/AE2-QoL-3.1.1.jar` | 修复汉化乱码 |
| 3.1.0 | `build/libs/AE2-QoL-3.1.0.jar` | 全量安全加固（14 项风险修复） |
| 3.0.2 | `build/libs/AE2-QoL-3.0.2.jar` | 含流体直接显示 + 刷屏日志清理 |
| 3.0.0 | `build/libs/AE2-QoL-3.0.0.jar` | 功能最全（无限磁盘/角标/通知/Replan/IO端口），但含 B/C 已知崩溃问题 |
| 2.14.1 | `build/libs/AE2-QoL-2.14.1.jar` | 稳定基线（无线直连完整版），无 3.x 新功能 |

回退步骤：删除测试包 `mods/AE2-QoL-<旧版本>.jar`，复制目标 jar 为 `mods/AE2-QoL-<目标版本>.jar`，重启客户端。
依赖固定：AE2 `rv3-beta-977-GTNH`、ae2fc `1.5.88-gtnh`、NEI `2.8.19-GTNH`。

---

## 3.5.1 - 二合一终端修复批次

> 作者：wztwzt | 更新时间：2026-08-19

### 修复：openContext 空指针崩溃（`PacketInventoryAction`/`PacketSwitchGuis` 等）

- 根因：`PacketInventoryAction.serverPacketData` 对任何 `instanceof AEBaseContainer` 的 openContainer 无条件调用 `createPrimaryGui()`（`appeng/container/AEBaseContainer.java:1116` 的 `context.getTile()`），而合并终端从未调用 `setOpenContext(...)`（原生终端经 `GuiBridge.updateGui` 设置，我们走 IGuiHandler 打开故未设）→ `getOpenContext()` 为 null → NPE，网络握手致命错误导致退出
- 修复：`ContainerMergedTerminal` 构造函数补 `setOpenContext(new ContainerOpenContext(anchor))` + world/x/y/z/side（`ForgeDirection.UNKNOWN`）；`PrimaryGui.gui` 为 null 无害（`open()` 已空判）。中键对 NEI 面板可合成物品下单现会正常打开「合成数量」子界面

### 修复：查看 NEI 返回后界面整体偏左/主题丢失/空白样板「消失」

- 根因：`GuiMergedTerminal.drawScreen` 末尾临时放大 `xSize=1000`（让面板悬垂区参与 `GuiContainer` 行 361/507 的「点击出界」判定），从 NEI（`GuiRecipe`）返回触发 `displayGuiScreen`→`initGui()` 重新计算 `guiLeft=(width-1000)/2` → 巨负偏左
- 修复：覆写 `initGui()` 在 `super.initGui()` 前复位 `xSize=209`，保证任何重初始化使用正确尺寸；drawScreen 的放大技巧仅在绘制后到下一帧输入阶段生效，不再污染重新初始化

### 修复：处理模式样板编码产出终极样板 + 网络自动扣空白

- 根因：GTNH 原生 `ContainerPatternTerm.encode()`（`ContainerPatternTerm.java:305-311`）处理模式产出 `encodedUltimatePattern`，GT 机器仅识别终极样板；我们始终产出普通 `encodedPattern` → GT 机器不识别
- 修复：`PatternContainer.encodeItemPattern()` 按模式产出 `encodedPattern`（合成）/`encodedUltimatePattern`（处理）；`notPattern()` 增加终极样板判定（二次编码不拦截）；`patternSlotOUT` 类型 `ENCODED_PATTERN` 经继承天然接受终极样板
- 修复：空白样板槽为空时（网络有空白）`Platform.poweredExtraction` 自动扣取 1 张（能量用 `grid.getCache(IEnergyGrid.class)`，`getPowerSource()` 为 null 故直接用网格能量缓存）；`ContainerMergedTerminal.slotClick` 覆写镜像原生 `ContainerPatternTerm.slotClick`——空手左/右键点空空白槽拉取 1 张（`pickupStoredItems/splitStoredItems` 为 `ContainerMEMonitorable` 私有，改为内联 `poweredExtraction`）

### 新增：NEI 配方界面「+」覆盖层对合并终端生效 + 面板 `↓` 读回按钮

- `ClientProxy` 注册 `API.registerGuiOverlay(GuiMergedTerminal.class, "crafting", TerminalCraftingSlotFinder)` + `registerGuiOverlayHandler(..., DefaultOverlayHandler, "crafting")`（与 AE2 原生终端一致），使 NEI 配方页「+」对合并终端可见
- `MixinDefaultOverlayHandler.transferRecipe` HEAD 拦截：`gui instanceof GuiMergedTerminal` 时取消原逻辑（原逻辑对假槽 `FastTransferManager.clickSlot` 不适配本终端），改从 handler 提取配方（`NeiRecipeCapture.extractFrom`）→ 判定合成/处理 → 本地切模式 + 发 `MergedTerminalActionPacket.FILL`
- 面板顶部新增第 5 个 `↓` 按钮（`BUTTON_LOAD_ID=954`，y=57）：把 `patternSlotOUT` 已编码样板经 `ICraftingPatternDetails` 解码读回面板网格（`isCraftable()` 判模式 + FILL）

### 修复：NEI 物品面板不再覆盖终端右侧样板面板

- 根因：NEI 2.8 `ItemsGrid` 逐格调用已注册 `INEIGuiHandler.hideItemPanelSlot(gui,x,y,w,h)`（屏幕坐标）；AE2 自带 `NEIGuiHandler` 只转发 `GuiMEMonitorable` 系，我们的 GUI 继承 `GuiInterfaceTerminal→AEBaseGui` 不在转发范围
- 修复：新增 `client/nei/MergedNeiHandler`（extends `INEIGuiAdapter`），面板矩形 `[guiLeft+209, guiTop, 133, 202]` 与格子相交即隐藏；`ClientProxy` 注册 `API.registerNEIGuiHandler`

---

## 3.5.0 - F 模块重构：独立有线方块 + 原生 AE2Things 风格面板

> 作者：wztwzt | 更新时间：2026-08-18

### 变更：二合一终端改为独立有线方块「样板与接口终端」

- 原 3.4.0 通过 `MixinGuiInterfaceTerminal`/`MixinContainerInterfaceTerminal` 混入原生接口终端实现二合一；3.5.0 改为**独立有线方块**（`merged/BlockMergedTerminal` + `TileMergedTerminal` + `MergedGuiHandler` + `client/render/RenderBlockMergedTerminal`），合成配方 `[铁][玻璃][铁] / [红石][钻石][红石] / [铁][玻璃][铁]`，移除两个 mixin（不再依赖 `@Shadow` MCP 名解析）
- GUI/容器直接继承原生实现：`merged/GuiMergedTerminal extends GuiInterfaceTerminal`、`ContainerMergedTerminal extends AEBaseContainer`，面板逻辑内聚到 `merged/PatternContainer`（移植 AE2Things `PatternContainer`：4×4×2 页槽布局、`isSlotEnabled`/`offsetSlots`、`updateOrderOfOutputSlots`）
- 顶部保留上传(↑)/召回(←)/轮换(⇄)/OV 覆盖按钮（`GuiUploadButtonHandler` 同款样式，竖排放面板左上）

### 变更：面板改为 AE2Things 原生样式

- **原生控件**：`GuiImgButton`（编码/清空/×2/替代/备份替代/反转）、`GuiTabButton`（合成⇄处理 tab，`GuiText.CraftingPattern/ProcessingPattern`）、`GuiScrollbar`（处理模式翻页，`pattern.png` 纹理，非反转不显示）
- **处理模式 4×4×2 页网格**：输入 4 列 × 4 行 × 2 页（滚动条翻页），输出 4 列；**反转按钮**切换输入/输出列方向；合成模式为 3×3 + 结果槽
- **面板位置**：`offsetX+209` 绘制，槽显示 `y + 68` 与纹理孔位精确对齐（参照 AE2Things `PatternPanel` 布局常量）
- **删除**：`N`（NEI 配方填充）按钮与 NEI 联动不再作为面板按钮
- **交互**：面板槽点击走 `playerController.windowClick`（空光标）/ AE 拖拽放置（非空光标，`mouseClickMove` 完成）；滚动条点击与滚轮翻页；面板区域点击优先于接口列表
- **悬垂绘制**：`drawScreen` 用 `xSize=1000` 放大法绘制面板悬垂区，槽位命中依赖 `guiLeft/guiTop` 字段，已验证不破坏点击
- **状态同步**：`MergedTerminalActionPacket` 新增 `SET_INVERTED`/`SET_PAGE`（新增 `int value` 字段），面板模式/替代/反转/页码随包同步服务端容器

### 修复

- 修复 `en_US.lang` 中 `ae2qol.extract.success` 与上一 key 同行（缺换行）的文本丢失问题

### 风险登记（本版新增）

- 见「已知风险登记表」#41-#43：xSize 悬垂绘制槽位命中、面板客户端静态状态、`GuiTabButton` RenderItem 反射回退

---

## 3.4.0 - 样板 + 接口二合一终端（F 模块）

> 作者：wztwzt | 更新时间：2026-08-17

### 功能：改造原生接口终端为「样板 + 接口」二合一

- **思路**：原生 AE2 接口终端（`GuiInterfaceTerminal`/`ContainerInterfaceTerminal`）已经覆盖了接口/样板总成的浏览管理；样板终端（Pattern Terminal）则负责编码。F 模块直接把**样板编码面板**嵌入接口终端 GUI 右侧，实现「看接口 + 编样板」同一界面完成。因直接改造原生 GUI，**有线接口终端与 ae2fc 无线接口终端自动共用同一面板**，无需额外配置。
- **新增文件**：
  - `api/IMergedPatternTerminal.java`：面板契约 + 布局常量（`PANEL_X=149`/`PANEL_Y=56`/`SLOT_SIZE=18`/`INPUT_MAX=27`/`OUTPUT_MAX=9`）+ `mergedSwapOutputs()`。
  - `client/gui/MergedPanelLayout.java`：面板几何计算（activeInputs/activeOutputs、输出/结果/空白/编码行、4 行按钮、机器名行、`isInPanel`）。
  - `client/event/MergedTerminalPanelHandler.java`：按钮创建/摆位/标签刷新 + 全部动作分发（上传/召回/交换/NEI 填充/编码/清空/×2/模式/替代/备份替代/OV）。
  - `mixin/ae/MixinGuiInterfaceTerminal.java`：drawFG TAIL 画面板（GL 关 SCISSOR/DEPTH、半透明底+边框、槽格、机器名）+ mouseClicked HEAD 面板点击拦截（按钮直发 `onButtonClicked`，槽点击 windowClick）。
  - `mixin/ae/MixinContainerInterfaceTerminal.java`：容器侧槽布局/编码/填充/输出轮换 + 新增 `mergedSwapOutputs()`。
  - `mixin/nei/MixinGuiRecipe.java` + `client/NeiRecipeCapture.java`：从 NEI 当前配方页提取输入/输出/是否处理配方。
  - `util/ContainerTerminalResolver.java`：容器 → `IActionHost` 统一解析（含反射读 `ContainerInterfaceTerminal.anchor` 私有字段）。
  - `network/MergedTerminalActionPacket.java`（C2S：ENCODE/CLEAR/DOUBLE/SET_MODE/SET_SUBSTITUTE/SET_BE_SUBSTITUTE/FILL）、`network/MergedTerminalResultPacket.java`（S2C：机器名回显）。
- **网络包共用**：`RequestProvidersListPacket`/`RecallPatternPacket`/`SwapPatternPacket` 经 `ContainerTerminalResolver` 支持合并终端容器。
- **上传链路**：优先读已编码槽 NBT `apu:recipeMap` → `RequestProvidersListPacket(recipeMap, forceGui)` 三策略；无映射则降级 `ICraftingPatternDetails` 读输入/输出再请求。
- **点击拦截方案**：`InterfaceTerminalList` 为私有内部类无法 @Redirect → `@Inject mouseClicked HEAD + ci.cancel()` 手动分发（按钮 → `onButtonClicked`；槽 → `playerController.windowClick`），光标持有物品时的放置由 vanilla `mouseMovedOrUp` 完成；`findSlotAt` 复用 `GuiContainerAccessor.getGuiLeft()/getGuiTop()`。
- **@Shadow 结论**：本工程编译时 AP 对 @Shadow 报 "Cannot find target" 警告为常态（既有 mixin 同样如此且运行正常），refmap 基本为空 → 运行时类为 MCP 名，@Shadow 用正确 MCP 名即可；但 **@Shadow 不能用于继承自父类的 protected 方法**（`isPointInRegion`/`handleMouseClick` 失败），字段无此限制。
- **1.7.10 API 修正**（编译验证）：`CraftingManager.findMatchingRecipe(InventoryCrafting, World)` 返回 `ItemStack`；`maybeStack(int)` 返回 Guava `Optional` 用 `.orNull()`；`SoundHandler` 无 `playSoundEffect` → `mc.thePlayer.playSound("random.click", 1.0F, 1.0F)`。
- **状态同步**：客户端静态字段 `mergedCraftingMode/mergedSubstitute/mergedBeSubstitute`；InitGuiEvent.Post 打开终端时重置并清 `ClientState.mergedMachineName`；包携带状态。
- **已知接受项**：面板 x149..203 与玩家背包右侧 2 列 / 视图口右缘重叠；鼠标拖拽/双击未完整复刻（迭代 1 可接受）；`lastClickSlot/lastClickTime/lastClickButton/ignoreMouseUp/dragSplitting` 驱动部分已接入，完整复刻留待反馈。

### 新增：配置页「配方参考」子页

- 反射 `gregtech.api.recipe.RecipeMap.ALL_RECIPE_MAPS` 的 `unlocalizedName` 枚举**当前整合包全部配方池 UID**（含 GT++/gtpp），经 `RecipeMapNameConfig.resolveSearchKeyword` 解析中文，支持按 UID/中文筛选；新增 `RecipeNameUtil.getAllRecipeMapUids()`。供玩家对照填写「记住的供应器」配方名。

### 风险登记（本版新增）

- 见「已知风险登记表」#36-#40：@Shadow MCP 名解析、列表点击 vs 面板重叠、上传无提示、NEI 捕获依赖、包无越权面。

---

## 3.3.7 - 性能修复 + tooltip 换行 + 配置页范围/映射编辑

> 作者：wztwzt | 更新时间：2026-08-17

### 性能：超大订单卡死修复（1T 量级）

- **根因 1**：功率钳制 `while (N>1 && 电不足) N--` 每次 `extractAEPower(SIMULATE)` 都是一次网格查询，N 达 2^31 量级时直接卡死。
- **根因 2**：`ae2qol$accountSmartPush` 逐轮循环执行 N 次 `postChange` + `waitingFor.add` + `postCraftingStatusChange`，同样 O(N)。
- **修复**：
  - 功率钳制改 **O(1)**：一次 `extractAEPower(Double.MAX_VALUE, SIMULATE)` 取可用电总量，`available/sum` 直接算出可负担最大轮数（收敛结果与原逐轮递减一致；`< sum - 0.01` 的兜底判断保留原版语义）。
  - 批量记账：`accountSmartPush` 改为每输出栈一次，按 `rounds` 缩放总量后各记账一次（`waitingFor` 为 `IItemList` 同物品自动合并，语义等价）；诊断会话按本次 push 消耗 **1 个**（与原版 `pushPattern` 一致，顺带修正原先逐轮多消耗会话的问题）。
  - 原料钳制加 **int 溢出钳制**：`perRound × N ≤ Integer.MAX_VALUE`，防止 GT 缓冲 ItemStack 数量为负导致合成错乱。

### 修复：PH / GT 舱室 tooltip `\n` 换行不生效

- 根因：lang 值中的 `\n` 是字面量（Minecraft lang 不转义），ModularUI `addTooltipLine` 不拆行。
- 修复：`MixinDualInputHatchUI` / `MixinMTEHatchCraftingInputMEGui` 按 `\n` split 逐行 `addTooltipLine`；同步更新 hint 文案为「默认 0=不限，可在配置页修改」（zh/en）。

### 新增：配置页显示可调范围 + 名字映射热编辑

- 配置页标签补范围：`io_port_rate`(1~2147483647)、`smart_doubling_max_rounds`(0=不限/1~2147483647)、`nei_overlay_enabled`(true/false)。
- 新增第二页「名字映射编辑」：
  - **配方名映射 `recipe_names.json`**：配方 key + 中文搜索词 输入框 + 添加/更新/删除（删除按中文搜索词）。
  - **记住的供应器 `remembered_providers.json`**：配方名 + 供应器名 输入框 + 添加/更新/删除。
  - 均为**客户端本地**即时生效并热写入文件（供 NEI 叠加层 / 自动上传使用），无需 OP。
  - **3.3.7 增补**：映射页改为「列表 + 编辑」——左右两个分类切换按钮，左侧滚动列表展示全部已有映射（点击行选中并回填编辑框），右侧编辑框 + 添加/更新、删除(选中=按 key)、删除(按值)；新增 `RecipeNameUtil.removeMappingByKey` 与 `ClientState.removeRememberedProvidersByValue`。
  - **3.3.7 再增补（布局 + 配方参考）**：修复各层文字重叠（标题/切换按钮/列表头/副标题/编辑区全部垂直分离，副标题用 0.8 倍缩放灰色小字居列表上方）；新增第三个子页「配方参考」——反射 `gregtech.api.recipe.RecipeMap.ALL_RECIPE_MAPS` 的 `unlocalizedName` 枚举**当前整合包全部配方池 UID**（含 GT++/gtpp），经 `RecipeMapNameConfig.resolveSearchKeyword` 解析出中文，支持按 UID/中文筛选，供玩家对照填入「记住的供应器」配方名；新增 `RecipeNameUtil.getAllRecipeMapUids()`。

### 风险登记（本版新增）

- `#32`：批量记账后 `waitingFor` 单栈数量可超 `Integer.MAX_VALUE`（long 承载，语义等价）→ 低。
- `#33`：O(1) 功率钳制与逐轮递减存在 <0.01 AE 的舍入差 → 兜底判断保留，行为一致。
- `#34`：`removeRememberedProvider` 新客户端方法仅本地生效 → 低。
- `#35`：配置页两页切换 `initGui` 重建控件，字段值保留 → 已核对。

---

## 3.3.6 - 智能倍增默认不限 + 游戏内配置页面

> 作者：wztwzt | 更新时间：2026-08-17

### 新增：游戏内配置页面

- 「Mods → AE2 QoL → Config」打开配置页（Forge 标准 `guiFactory` 入口，`IModGuiFactory`），可编辑 `io_port_rate` / `smart_doubling_max_rounds` / `nei_overlay_enabled` 并即时应用。
- 改动经 `ConfigSetPacket`（C2S）提交服务端：**OP 校验**（`canCommandSenderUseCommand(2)`）+ key/范围白名单校验，成功后写服务端 `settings.json` 并广播 `ConfigUpdatePacket`（S2C）同步给所有客户端（含本地写盘）。
- 玩家登录时服务端自动推送当前配置（`PlayerLoggedInEvent`，走 FML 总线），配置页面始终显示服务端真实值。
- 新增 `Config.applySetting(key,value)` / `Config.applyAll(io,rounds,overlay)`，与热加载/`/ae2qof reload` 共用同一套校验与写盘逻辑。

### 变更：`smart_doubling_max_rounds` 默认 0 = 不限

- 默认值 `64 → 0`，范围 `1..4096 → 0..Integer.MAX_VALUE`；`0` 表示一次发配**剩余全部轮数**。
- 三个取整点同步支持 0=不限：CPU `ae2qol$smartMultiplier`（0 时跳过配置钳制）、GT 输入仓 `getMaxMultiplier`（0→`Integer.MAX_VALUE`，GT 缓冲本就无上限）、ME 接口 `getMaxMultiplier`（二分上界放宽）。
- 防溢出：`remaining` 为 long，取 N 时以 `Math.min(remaining, Integer.MAX_VALUE)` 封顶，避免 `(int)` 强转负数。
- 实际效果：GT 仓一次全发（受功率/CPU 缓冲钳制）；PH 仓按缓冲空间自取；ME 接口按相邻机器容量上限发配（物理极限，无法字面全发）。
- 旧 `settings.json` 里显式写的 64 仍生效（尊重玩家设置）；仅「未配置」时默认 0。

### 风险登记（本版新增）

- `#29`：配置 C2S 包权限 → OP 校验已修复。
- `#30`：0=不限后 long→int 溢出 → 封顶已修复。
- `#31`：大 N 逐轮记账性能 → 已兜底（N≤1e5 毫秒级；极端配置才需关注）。

---

## 3.3.5 - 修复智能倍增（GT/PH 样板输入机）实测失效

> 作者：wztwzt | 更新时间：2026-08-17

### 背景

3.3.3 为 GT 样板输入总成/输入总线 (ME)（meta 2714/2715）与 ProgrammableHatches 双口输入仓（meta 22130/22179）新增智能倍增，但实测发现：**勾选后完全无效，且无法发配物品**（关闭后恢复原版逐轮推送）。关闭仅影响智能倍增，其余功能正常。

### 根因（两个独立 Bug）

- **Bug 1（无法发配）**：倍增分支的功率门槛写成 `eg.extractAEPower(sum * effectiveN, SIMULATE) < sum*effectiveN - 0.01 → continue`，无任何回退。`extractAEPower` 的 SIMULATE 只返回**部分可提取值**（`Math.min(可提取, 请求)`），一旦网络 AE 不足 N 轮总电，该介质被**永久跳过**，CPU 对整个任务零推送；原版只查 `sum`（1×），所以关闭即恢复
- **Bug 2（无效）**：原料探测用 `getExtractItems(N×, details)` 并要求**严格全量匹配**（候选堆大小 == 请求大小），缓冲稍差一点即判定放不下，`effectiveN` 被静默降为 1——看起来勾选了但实际还是逐轮推送

### 修复方案（对齐 GTLCore 的单次推送模型）

- **原料钳制**：改为 `inventory.extractItems(N×, SIMULATE)` 求每个输入实际可提取轮数，`N = min(N, 各输入可提取轮数)`，**允许部分提取**（不再严格全量匹配）；取不到任何材料时 N 自然降为 1
- **功率钳制**：`while (N>1 且功率不足 sum*N) N--`，N 降到 1 时与原版一样只查 `sum`——**不再整体跳过介质**
- **PH 双口输入仓**：走 `pushPatternMulti(details, ci(1×), N)`，由仓内缓冲空间自取轮数并返回**实际接受轮数** `m`，CPU 按 m 记账（一次推送 m 轮）
- **GT 及其它**：构造 N× 配方缓冲调 `pushPattern`，成功即记账 N 轮
- **记账**：按实际轮数一次性扣电、逐轮消耗诊断会话、逐轮追加 waitingFor/输出、`executedTasks += m`、`remainingOperations--`、任务剩余轮数 `-= m`；余量 ≤ 0 时按原版清理
- **防御**：`ae2qol$onExecuteCrafting` 全链路 try/catch，异常记日志且**不 cancel**——回退到原版 `executeCrafting` 接管本 tick，任何情况下不会拖死 CPU
- **结构不变**：保持 `@Inject(HEAD)+cancel`，保留原方法字节码，ProgrammableHatches `MixinInstantComplete` 的注入点不受影响

### 变更文件

- `mixin/ae/MixinCraftingCPUCluster.java` —— 倍增分支重写：原料/功率钳制、PH `pushPatternMulti` 路径、GT N× 单次推送、按实际轮数记账（`ae2qol$accountSmartPush`）、`onExecuteCrafting` try/catch 兜底

### 回归要点

- GT 2714/2715：任务一次推 N 轮、机器缓冲连续消耗、任务完成时 waitingFor 平衡、不超产不丢物
- PH 22130/22179：`pushPatternMulti` 返回轮数与缓冲一致、开启 PH `fastPatternDualInput` 配置（默认开）
- 材料不足 / 功率不足：按可提取轮数钳制 N，禁止再出现"整体跳过介质导致零推送"
- 关闭智能倍增后行为与 3.3.2 完全一致（走原版路径）

---

## 3.3.4 - 修复自动上传把样板误投进 GT/PH 样板输入机原料缓存槽

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **根因**：AE2 Auto Pattern Upload 自动上传/撤回把「样板输入机」当普通 `IInventory` 处理。GT 机器与 PH 机器（GT `MTEHatchCraftingInputME` meta 2714/2715、PH `PatternDualInputHatch` meta 22130/22179）经 GT `IMetaTileEntity extends ISidedInventory` 实现了 `IInventory`，而其通用库存（原料缓冲槽）排号在专属样板槽之前——自动上传遍历空槽时把编码样板投进了原料缓存槽，多方块收不到配方；样板槽只能手动打开 GUI 放置
- **修复方案**：上传 / 空位统计 / 撤回三处逻辑统一改为**优先使用提供器自带的专属样板槽库存** `appeng.api.util.IInterfaceViewable.getPatterns()`：
  - AE2 接口（`IInterfaceHost extends IInterfaceViewable`）、GT 样板输入机、PH 双口输入仓均实现 `IInterfaceViewable`，其 `getPatterns()`/`rows()`/`rowSize()` 指向样板专用区域
  - `UploadPatternPacket.insertPatternIntoProvider`：先走 `IInterfaceViewable` 分支写入样板槽；GT/PH 写入后调 `MetaTileEntity.markDirty()` 标记存档（`setInventorySlotContents` 已自动触发机器内部样板重建与 ME 网络同步）；AE2 接口仍走 `IInterfaceHost.saveChanges()`，行为不变
  - `RequestProvidersListPacket.estimateEmptySlots`：只统计样板槽空位，避免把原料缓存槽误报为可用空位导致自动选择投递目标
  - `RecallPatternPacket`：撤回限定在 `rows()*rowSize()` 样板区域，不再把缓存槽里的原料误当样板
- **影响范围**：GT「样板输入总成/总线 (ME)」（2714/2715）、ProgrammableHatches「编程样板输入总线」（22130）、「编程样板输入总成 MK.II」（22179）；AE2 ME 接口 / 超级接口原有自动上传行为不变

### 修改文件

- `network/UploadPatternPacket.java` —— `insertPatternIntoProvider` 改用 `IInterfaceViewable` 优先 + 新增 `markProviderDirty`（`IInterfaceHost`→`saveChanges`；`MetaTileEntity`→`markDirty`）
- `network/RequestProvidersListPacket.java` —— `estimateEmptySlots` 改用 `IInterfaceViewable` 统计样板空位
- `network/RecallPatternPacket.java` —— `findProviderInventory` 重构为 `findProvider` + `resolvePatternInventory`/`resolvePatternLimit`，撤回限定在样板槽区域

---

## 3.3.3 - 样板输入机（GT/SNL/PH）智能倍增 + 流体显示修复

> 作者：wztwzt | 更新时间：2026-08-16

### 新功能

- **GT 样板输入机（ME）智能倍增**：`MTEHatchCraftingInputME`（GT 机器「样板输入总成 (ME)」meta 2714 / 「样板输入总线 (ME)」meta 2715）GUI 左下角新增循环箭头开关按钮（ModularUI），勾选后合成 CPU 对挂在其上的样板**一次性推送多轮材料**，机器连做多轮不再逐轮补料
  - 通过 `mixin/gt/MixinMTEHatchInputBus` 为 GT 输入仓全族（`MTEHatchInputBus` 及子类）注入 `ISmartDoublingMedium` 实现，仅 `instanceof ICraftingProvider`（即样板输入机）生效，普通输入总线、补货输入仓、`MTEHatchPatternProvider` 不受影响
  - 开关状态写入机器 NBT（键 `ae2qolSmartDoubling`）随存档持久化，默认关闭
  - 每轮最大轮数沿用配置 `smart_doubling_max_rounds`（默认 64），CPU 侧剩余轮数与原料可取性会进一步裁剪
- **ProgrammableHatches 双口输入仓智能倍增**：`PatternDualInputHatch`（meta 22130 / 22179）同样支持，ModularUI 内 `(7, 62)` 位置新增开关按钮（`populateUI` TAIL 注入）
- **GTNotLeisure 超级二合一 ME 接口（SuperDualInterface）智能倍增**：方块与线缆面板两形态 GUI 左侧新增复选框（方块 `guiTop+134`、面板 `guiTop+116`，位于 fuzzyMode 与翻页之间，按 host 形态自适应偏移）；物品侧 `DualityInterface` 已由 `MixinDualityInterface` 覆盖、容器同步字段已具备，无需重复注入
- **流体显示回归验证**：3.1.2 流体误判修复（`isGtFluidDisplay` 类名识别）并入本次发布，回归确认 GT 流体容器在 NEI 角标与悬浮提示中仍按 `mB` 显示流体量
- **兼容性**：GT（`MTEHatchInputBus`/`MTEHatchCraftingInputME` 等）、ProgrammableHatches、ModularUI2、GTNotLeisure 均为可选依赖（compileOnly + mixin 配置级 `required=false`），任一缺失时对应注入静默跳过，不影响其余功能

### 修改文件

- `mixin/gt/MixinMTEHatchInputBus.java` —— **新增**：GT 输入仓族 `ISmartDoublingMedium` 实现 + NBT 持久化（`saveNBTData`/`loadNBTData` TAIL 注入，`remap=false`）
- `mixin/gt/MixinMTEHatchCraftingInputMEGui.java` —— **新增**：GT 样板输入机 ModularUI 左下角开关按钮（构造器 TAIL 捕获机器引用，规避继承字段 @Shadow 风险；`createBottomLeftCornerFlow` RETURN 注入）
- `mixin/gt/MixinDualInputHatchUI.java` —— **新增**：PH 双口输入仓 `DualInputHatch.populateUI` RETURN 注入开关按钮
- `mixin/ae/MixinGuiSuperDualInterface.java` —— **新增**：GTNL 超级二合一接口 GUI 智能倍增复选框（addButtons/`func_146284_a`/drawFG 三处 TAIL，`remap=false`，按 host 形态计算偏移）
- `mixins.ae2_qof.json` —— 公共列表新增 `gt.MixinMTEHatchInputBus`；客户端列表新增 `ae.MixinGuiSuperDualInterface`、`gt.MixinMTEHatchCraftingInputMEGui`、`gt.MixinDualInputHatchUI`
- `dependencies.gradle` —— 新增 `compileOnly`：GT（`libs/gregtech-5.09.52.594.jar`）、ModularUI2（`libs/modularui2-2.3.73-1.7.10.jar`）、ProgrammableHatches（`libs/programmablehatches-0.2.0p8.jar`）

---

## 3.3.2 - 修复与 GTNotLeisure 的同步字段冲突崩溃 + 超级接口智能倍增

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **崩溃根因**：智能倍增在 `ContainerInterface` 注入的同步字段用了 `@GuiSync(19)`，而 GTNotLeisure 的 `ContainerSuperInterface`（extends `ContainerInterface`）自己声明了 `@GuiSync(19) sidelessMode`。AE2 的 `DataSynchronization.collectFields` 会遍历整个类层级收集 `@GuiSync` 字段，发现同一个 sync id 被声明两次时直接抛 `IllegalStateException`，游戏崩溃。
- **修复方案**：同步 id 从 `@GuiSync(19)` 改为 `@GuiSync(30)`：
  - AE2 `ContainerInterface` 继承链已用 id：`ContainerUpgradeable`=0/1/5/6，`ContainerInterface`=3/4/7~18
  - GTNL `ContainerSuperInterface` 用 19，AE2 无其它子类占用 19~30
  - `@GuiSync(30)` 与两边都不冲突，`DataSynchronization` 不再抛异常，智能倍增同步功能不变

### 新功能

- **GTNotLeisure 超级接口智能倍增**：GTNL 超级接口（Super Interface，即样板总成）GUI 左侧新增「智能倍增」复选框（位于 fuzzyMode 与翻页按钮之间，`guiTop + 152`）。
  - GTNL 超级接口方块基于 AE2 `DualityInterface`（`TileEntitySuperInterface` 直接持真实 duality），本模组的 `MixinDualityInterface`/`MixinContainerInterface` 天然作用于其上，其容器自动获得同步字段与持久化
  - 新增 `MixinGuiSuperInterface` 注入 GTNL `GuiSuperInterface`（extends `GuiUpgradeable`，非 `GuiInterface`），复刻原版 ME 接口的复选框逻辑
  - 勾选后，挂在该超级接口上的样板同样由合成 CPU 一次性推送多轮材料
- **兼容性**：GTNotLeisure 为可选依赖（compileOnly + mixin 配置级 `required=false`），不安装时其余功能不受影响；GTNL 发布包为 SRG 混淆，`actionPerformed` 运行时名为 `func_146284_a`，注入按该名处理
- **ProgrammableHatches**：其样板合成器（`TileMolecularAssemblerInterface`）实现 `ICraftingMachine.acceptsPlans()`，本模组自动按单轮（N==1）处理，不超产

### 修改文件

- `mixin/ae/MixinContainerInterface.java` —— `@GuiSync(19)` → `@GuiSync(30)`（含 javadoc）
- `mixin/ae/MixinGuiSuperInterface.java` —— **新增**：GTNL 超级接口 GUI 智能倍增复选框（addButtons/drawFG/`func_146284_a` 三处 TAIL 注入）
- `mixins.ae2_qof.json` —— 客户端列表新增 `ae.MixinGuiSuperInterface`
- `dependencies.gradle` —— 新增 `compileOnly` GTNotLeisure（`libs/sciencenotleisure-0.2.7-pre1-dev-290.jar`）

---

## 3.3.1 - 修复与 ProgrammableHatches 的 mixin 冲突崩溃

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **崩溃根因**：智能倍增此前用 `@Overwrite` 整体重写 `CraftingCPUCluster.executeCrafting()`，替换了整个方法体。ProgrammableHatches（`programmablehatches-0.2.0p8.jar`）的 `eucrafting.MixinInstantComplete` 也要向同一方法 `@Inject`（`@At("INVOKE")`），因找不到注入点而崩溃。
- **修复方案**：`@Overwrite` → `@Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)` + `ci.cancel()`：
  - HEAD 注入不改动原方法字节码结构（INVOKE 指令原样保留），其它模组对同一方法的注入点仍可正常定位 → 不再崩溃
  - 仅当检测到**存在启用智能倍增的介质任务**（剩余轮数 > 1 且非 craftable）时才接管整个 tick（`ae2qol$hasSmartDoublingTask` 预扫描）
  - 无智能倍增任务时完全不接管，原版 executeCrafting（含其它模组的注入代码）原样执行
  - 接管时内部 N==1 分支仍与原版逐行等价，功能与 3.2.0 完全一致
- **兼容性**：智能倍增开启前/关闭后与 ProgrammableHatches 共存正常；开启期间该 tick 由本模组接管，PH 的注入代码该 tick 不执行（不影响其它 tick）。

### 修改文件

- `mixin/ae/MixinCraftingCPUCluster.java` —— `@Overwrite` 改 `@Inject(HEAD)+cancel` + 新增 `ae2qol$hasSmartDoublingTask` 预扫描 + 原循环体移入 `ae2qol$executeCraftingSmart`（逻辑不变）

---

## 3.3.0 - 统一配置文件 + 热加载 + OP 管理命令

> 作者：wztwzt | 更新时间：2026-08-16

### 新功能

- **统一配置文件** `config/ae2_qof/settings.json`（取代旧 `config/ae2_qof.cfg`）：
  - `io_port_rate`：强化 IO 端口传输倍率（默认 1024，1..Integer.MAX_VALUE）
  - `smart_doubling_max_rounds`：智能倍增最大轮数（默认 64，1..4096）
  - `nei_overlay_enabled`：NEI 叠加层开关（吸收原 `OverlayConfig` 同路径文件，避免覆盖其它字段）
- **热加载**：直接编辑 `settings.json` 保存后约 1 秒自动生效（单机/服务端均可，无需重启）。`MixinTileIOPort`、`MixinDualityInterface.getMaxMultiplier`、`MixinCraftingCPUCluster.executeCrafting` 均接入 mtime 限流校验。
- **OP 命令** `CommandAe2QoL`（`/ae2qof`，权限等级 2）：
  - `/ae2qof reload` —— 立即热重载 `settings.json` + `recipe_names.json`（含 `RecipeMapNameConfig` 缓存刷新）
  - `/ae2qof status` —— 显示当前生效配置值
  - 服务端需 OP；单机/局域网主机默认 OP 可直接使用；`/apu-overlay` 维持无需 OP
- **旧配置迁移**：首次启动检测到旧 `config/ae2_qof.cfg` 时自动把 `exIOPortTransferContentsRate`/`smartDoublingMaxRounds` 数值迁入 `settings.json`，并删除旧 cfg，玩家已有调优值不丢失。

### 修改文件

- `Config.java` —— 重写为 `settings.json` 管理器（JSON 读写 + 数值 clamp + mtime 热加载 + 旧 cfg 迁移）
- `client/OverlayConfig.java` —— 改为委托 `Config`（统一文件、保留其它字段）
- `mixin/ae/MixinTileIOPort.java` —— 传输前 `Config.ensureFresh()`
- `mixin/ae/MixinDualityInterface.java` / `mixin/ae/MixinCraftingCPUCluster.java` —— 计算前 `Config.ensureFresh()`
- `CommonProxy.java` —— `serverStarting` 注册 `CommandAe2QoL`
- `README.md` / `README.en.md` —— 新增智能倍增功能说明 + 配置文件/命令文档
- `gradle.properties` / `mcmod.info` —— 版本 3.3.0

### 新增文件

- `CommandAe2QoL.java` —— `/ae2qof` 管理命令

---

## 3.2.0 - 智能倍增（Smart Doubling）

> 作者：wztwzt | 更新时间：2026-08-16

### 新功能

- **智能倍增**：ME 接口（DualityInterface）新增「智能倍增」复选框。勾选后，CPU 对挂在接口上的样板一次性推送 **N 轮**材料，GT 机器连做 N 次，补料不再逐轮等待。
- N 计算：`N = min(剩余轮数, 配置上限 smartDoublingMaxRounds（默认 64）, 各输入槽可提取量/单轮量)`；接口侧 `getMaxMultiplier` 按面×输入用 `simulateAddStack` 二分探测机器最大可吞轮数，任一面/输入放不下则整体回退 N==1。
- 安全边界（N==1 与逐轮原版路径逐行等价）：
  - craftable 输入、假合成（fake crafting）、流体接口、阻塞模式（BLOCKING）、接口有滞留未推送物品、机器无任何面有 adaptor、GT `ICraftingMachine.acceptsPlans()` 机器 → 一律 N==1
  - 提取前全槽 SIMULATE 探测 N，提取中任一模槽部分提取 → 回退单轮路径（防丢物/超产）
- 能耗按 N×sum 记账；`value -= N`、`waitingFor` 累加 N× 产出、`executedTasks += N`；每轮 `consumeCraftSession()`，产出分批推送（接口次 tick 缓冲）。
- 私有内部类（`TaskProgress`/`finalOutput`/`CraftingCpuDiagnostics`）与 CPU 私有字段经**缓存反射**访问；反射失败自动降级为不启用智能倍增，不影响原版合成。

### 新增文件

- `api/ISmartDoublingMedium.java` — 介质接口（`isSmartDoublingEnabled`/`setSmartDoubling`/`getMaxMultiplier`）
- `api/ISmartDoublingContainer.java` — 容器接口（同步界面开关）
- `mixin/ae/MixinDualityInterface.java` — 实现介质接口；`writeToNBT`/`readFromNBT` 注入持久化开关；`getMaxMultiplier` 全安全边界探测
- `mixin/ae/MixinContainerInterface.java` — `@GuiSync(19)` 布尔开关 + 构造初始化
- `mixin/ae/MixinGuiInterface.java` — 复刻 `patternOptimization` 的 `GuiToggleButton` 复选框（icon 178/194）
- `network/SmartDoublingTogglePacket.java` — C2S 切换，归队 `ServerThreadUtil.addScheduledTask`

### 修改文件

- `mixin/ae/MixinCraftingCPUCluster.java` — `@Overwrite executeCrafting`（逐行移植 + 智能倍增 N 分支）
- `network/ModNetwork.java` — 注册 `SmartDoublingTogglePacket`
- `Config.java` — `smartDoublingMaxRounds = 64`（范围 1..4096）
- `lang/zh_CN.lang` + `lang/en_US.lang` — `gui.ae2_qof.smart_doubling` / `.hint`
- `mixins.ae2_qof.json` — 注册 `MixinDualityInterface`/`MixinContainerInterface`（公共列表）、`MixinGuiInterface`（client 列表）

### 使用说明

在 ME 接口的 GUI 左侧点击「智能倍增」复选框（图标为循环箭头），将该接口设为支持一次推送多轮的介质；机器只吃 N 轮时接口会缓冲补推。全局上限在 `config/ae2_qof.cfg` 的 `smartDoublingMaxRounds` 调整。

---

## 3.1.2 - 修复流体数量随机出现在物品上的显示 bug

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **J（流体误判）**：`NetworkInventoryCache` 用 `FluidRegistry.getFluid(itemDamage)` 判定"是否为流体"。Forge `FluidRegistry` 按注册序分配 ID（水=0、岩浆=1…），任何 damage 恰好命中流体 ID 的物品（最常见 damage=0 → 水）都被误判为流体，导致 NEI 配方角标/书签角标/tooltip 在**随机物品**上显示 `X mB 水/岩浆/…`（"随机"取决于网络缓存里当时有哪些流体），并干扰 NEI 面板 Shift+左键提取/中键合成的取出/合成判定
- **J（识别方式修正）**：ae2fc `ItemFluidPacket` 实际把流体编码在物品 NBT（`"FluidStack"` 复合标签 + `"Amount"` long），`newStack()` 从不写 damage——3.0.2"damage 编码流体 ID"假设错误（damage 仅用于 `getColorFromItemStack` 取渲染色）。现改为：① 按物品类名精确识别 ae2fc `ItemFluidPacket`（不 import ae2fc，保持模组独立）；② 流体直接从 NBT 读取；③ 流体方块物品走 `fluidItemMap` 反查（该 map 此前写了从未读，为死代码，现启用）；④ 其余物品一律按普通物品处理
- **J（回归确认）**：水/岩浆/蒸馏水等纯流体在 NEI 角标/tooltip 仍正确显示流体量（mB）；桶/单元等容器物品仍按容器数量显示；普通物品不再出现流体量

### 技术说明

- 消费方 `MixinNEIRecipeWidget`/`NetworkTooltipHandler`/`NetworkInventoryDrawHandler`/`MixinPanelWidgetClick` 无需改动，统一走修正后的 `getCount`/`isCraftable`/`getFluidStack`
- 对应风险表 #19；开发调研备忘见文末「附：开发调研记录（2026-08-16）」

---

## 3.1.1 - 修复汉化文件乱码

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **汉化文件乱码**：`zh_CN.lang` 曾被双重编码损坏（UTF-8 字节被误按 GBK 解码后再存回 UTF-8），导致全部中文值变乱码、多条条目被挤到同一行、全角标点（`。？：（）`）丢失。已按最初提交 `f288457` 的完好原文重建，恢复全部 67 条中文翻译（添加/刷新/删除、映射、无线收发器/连接器、频道绑定/断开、tooltip 等），并按新 key 前缀 `ae2_qof` 对齐
- **一致性校验**：确认 `en_US` 与 `zh_CN` 的 67 个 key 集合完全一致，代码中引用的全部 key 均存在于语言文件

---

## 3.1.0 - 全量安全加固（14 项已知风险修复）

> 作者：wztwzt | 更新时间：2026-08-16

### 修复

- **A（合成通知强转崩溃）**：`MixinCraftingCPUCluster` 合成通知增加 `instanceof EntityPlayerMP` 判断后再强转，假玩家/非 MP 玩家发起合成不再 `ClassCastException` 打崩服务端线程（风险 #1）
- **A（下线玩家合成通知）**：长合成任务期间玩家下线时跳过已断线玩家（`playerNetServerHandler == null`），不再 `sendTo` NPE + 内存滞留（风险 #2）
- **A（提取丢物）**：`ServerTerminalHelper` 提取物品改为先 `SIMULATE` 计算、背包容量预检，再按上限实际提取；放入失败/背包满时剩余物品经 `injectItems` 归还网络，杜绝凭空消失（风险 #3）
- **A（样板上传/撤回权限）**：`RecallPatternPacket`（EXTRACT）与 `UploadPatternPacket`（INJECT）增加 `ISecurityGrid` 所有权校验；无安全站的网络默认放行（风险 #4/#7）
- **A（C2S 归队服务端线程）**：全部 8 个 C2S 包处理改用 GTNHLib `ServerThreadUtil` 归队到服务端 tick 线程，不再于 Netty IO 线程并发访问 grid/container（风险 #5）
- **C（NEI 配方页 AE 角标修复）**：注入点改为 NEI 2.8.19-GTNH 实际存在的 `draw(II)V`，经 `RecipeHandlerRef` 取配方栈绘制样板图标与数量角标，功能恢复（风险 #6）
- **C（Provider 列表限流）**：`RequestProvidersListPacket` 全量反射扫描 GT 配方池增加 3 秒/玩家冷却 + 结果缓存，防恶意连发卡服（风险 #8）
- **C（IO 端口溢出）**：`MixinTileIOPort` 传输倍率乘法改为溢出安全计算（风险 #9）
- **C（无线动作加固）**：`WirelessActionPacket` 全局 try/catch + 仅允许操作玩家当前打开 GUI 的收发器（风险 #10）
- **C（合成/提取包加固）**：`RequestCraftingPacket`、`ExtractItemPacket` 全局 try/catch + 恶意负数 count 钳制（风险 #11/#12）
- **C（样板交换加固）**：`SwapPatternPacket` 反射字段异常兜底 + 改库后 `saveChanges` 持久化（风险 #13）
- **C（日志清理）**：移除 NEI 书签传输的 `System.out.println`（风险 #14）

### 技术说明

- 本环境编译用的 RFG recompiled Minecraft 中 `MinecraftServer`/`Minecraft` 均无 MCP 名 `addScheduledTask`（1.7.10 Forge 运行时亦无该方法），故服务端线程归队改用 GTNHLib `ServerThreadUtil.addScheduledTask`（其 `MixinMinecraftServer` 每 tick 排空任务队列，AE2 依赖自带 GTNHLib）；客户端 `SwapPatternPacket` 归队使用 SRG 名 `func_152344_a`

---

## 3.0.2 - 流体直接显示（fix）

> 作者：wztwzt | 更新时间：2026-08-15

### 修复

- **I（流体直接显示）**：NEI 角标与 tooltip 中，桶/单元等流体容器物品不再以"容器数量"显示，而是**直接显示流体本身的量**（mB）。`NetworkInventoryCache.getCount`/`isCraftable` 改为：对能识别出流体的容器物品，优先查询流体缓存返回流体量；只有流体缓存无数据时才回退容器物品缓存
- **I（纯流体识别）**：ae2fc `ItemFluidPacket` 的 damage 值直接编码流体注册 ID（`FluidRegistry.getFluid(damage)`）。`getFluidStack` 新增第五条识别路径——通过 damage 查 `FluidRegistry`，使水/岩浆/氢气/氧气等纯流体在 NEI 角标和 tooltip 中正确显示流体量
- **I（容器与流体分离）**：**只有 ae2fc 纯流体 packet（damage 编码流体）才显示流体量（mB）**；水桶/单元等容器物品一律按普通物品返回其在网络中的**容器数量**（AE 里真的有该容器才显示，不再把容器当流体显示总量）
- **I（流体 tooltip 标识）**：流体容器 tooltip 追加流体本地名，格式从 `4.5P AE` 变为 `4.5P mB 蒸馏水`，可合成状态不变
- **刷屏日志清理**：移除 `NetworkInventoryCache.getFluidStack` 两条 `LOG.info`、`getCount` 一条 `LOG.info`、`MixinGuiMEMonitorable` 一条 `Cached fluid` `LOG.info`——之前每次渲染/hover 都打印，严重刷屏（对应风险表 #14 部分修复）

---

## 3.0.1 - 崩溃修复 + 数值扩展 + 流体角标 + 合成表

> 作者：wztwzt | 更新时间：2026-08-15

### 修复

- **H（服务器 Shift+左键提取踢出）**：修复在服务器上 shift+左键从 NEI 面板取 AE 物品时客户端被踢（`DecoderException: readerIndex(11) + length(2) exceeds writerIndex(12)`）。`CraftingResponsePacket` 不再序列化 `ItemStack`（损坏物品/带 NBT 物品在部分场景下字节不对称导致解码越界），改为仅传物品名称字符串；同时给**所有**自定义网络包的 `fromBytes` 加防御性 try-catch，任何解码异常只丢弃该包、不再导致玩家断连
- **C（Replan 崩溃）**：修复点击"重新规划"时因 `Replanner.clearIItemList` 误调用 `lists.clear()` 清空 `IAEStackList` 内部 map 导致的 NPE 崩溃（`GuiCraftConfirm.drawListFG` → `findPrecise`）。现仅清空各类型子表，map 结构保留
- **B（合成通知未生效）**：`MixinCraftingCPUCluster` 与 `MixinTileIOPort` 原位于 mixin json 的 `server` 列表，单机（integrated server）下 MixinBooter 不会应用该列表，导致两处逻辑均未注入。已移入公共 `mixins` 列表并加 `"target": "@env(DEFAULT)"`——顺带修复了 IO 端口倍率从未生效的问题
- **D（强化 IO 端口外观/数值）**：复制 AE2 原生 IO 端口贴图（`ex_io_port`/`ex_io_portBottom`/`ex_io_portSide`），补充 lang 名称（强化 IO 端口/Enhanced IO Port），默认传输倍率 256 → 1024

### 改进

- **H（提取/合成反馈提示）**：shift+左键提取与中键合成现在会在聊天栏给出明确结果提示（成功/无库存/不可合成/背包已满），不再静默
- **E（科学计数法扩展）**：统一新增 `CountFormatter` 工具（K/M/G/T/P/E），替换 NEI 配方角标、书签角标、tooltip 三处仅到 G 的格式化；覆盖 Long 全范围
- **E（流体支持）**：`NetworkInventoryCache` 新增流体缓存（按键为流体名）；`MixinGuiMEMonitorable` 处理 `IAEFluidStack`；tooltip/书签/配方角标对携带 `FluidStack` NBT 的物品（如 ae2fc `ItemFluidPacket`）自动回退查询流体数量
- **G（合成表）**：新增本 mod 方块/物品的原版工作台合成配方——强化 IO 端口、无线收发器、无线连接器；无限水岩浆磁盘：水桶 + 岩浆桶左右放置（中间及其他格为空）→ 无限水岩浆磁盘

---

## 3.0.0 - 无限水岩浆磁盘 + NEI 配方界面 AE 角标 + 合成通知/重规划/强化IO端口

> 作者：wztwzt | 更新时间：2026-08-14

### 新功能

- **无限水岩浆磁盘**：新增物品 `infinity_water_lava_cell`（无限水与岩浆磁盘）。放入 ME 驱动器后提供无限的水与岩浆（每种数量 ≈ 2^52-1 ≈ 4.5e15）。复用 977 原生 `CreativeCellInventory`：构造时读取预置的水桶/岩浆桶配置并转换为流体，数量置为 Long 上限级别，达到近乎无限的效果。idleDrain 2000，可编辑，2 种类型
- **NEI 配方界面 AE 角标**：任意 NEI 配方页（合成/机器配方）的每个物品格上叠加显示 AE 网络数据——可合成物品叠加编码样板小图标（0.4 缩放），有存量的物品在角落显示数量角标（0.6 缩放，K/M/G 格式化）。数据来自 AE 终端 `postUpdate` 填充的 `NetworkInventoryCache`，受 `/apu-overlay` 开关（settings.json `nei_overlay_enabled`）控制。注入点 `NEIRecipeWidget.drawItem` TAIL，覆盖任意配方界面，不依赖 NEI 的 itemPresenceOverlay 配置
- **合成完成通知**：AE 合成 CPU 完成一次合成任务时，客户端屏幕中央弹出通知横幅（物品图标 + 数量 + "合成完成"），自动淡出
- **合成重新规划（Replan）**：服务端指令/客户端请求可对指定合成任务进行重新规划（清空原流程重新规划配方分配）
- **强化版 IO 端口**：新增 `ex_io_port` 方块，继承 AE2 IO 端口行为，支持基于频道状态的增强模式；传输内容量按倍率放大（默认 256 倍，可配置）
- **NEI 配方 tooltip 悬浮提示**：NEI 物品面板悬浮显示 AE 网络数量/可合成信息

### 关键决策

- **F（无线双接口终端）**：调研确认 AE2 977 已原生实现接口终端（`PartInterfaceTerminal`/`WirelessInterfaceTerminalGuiObject`），ae2fc 1.5.88 自带 `ItemWirelessInterfaceTerminal` 可直接打开 977 原生接口终端——该功能已被原生覆盖，放弃约 6000 行完整移植，改为上述 NEI 配方界面 AE 角标增量增强
- **G（创造流体磁盘）**：ae2fc 已自带 `ItemCreativeFluidStorageCell`（无限存储）——但用户实际需要的是"内置水/岩浆的无限供应磁盘"，故实现本 mod 的无限水岩浆磁盘
- **H（AdvItemRepo 线程化刷新）**：977 `ItemRepo` 已重构（`IAEStack<?>` 泛型、无 `dsp` 字段），参考 `AdvItemRepo` 依赖 AE2Things 自有接口无法直接移植，经确认跳过（977 GTNH 分支已做性能优化）

### 新增文件

- `item/ItemInfinityWaterLavaCell.java` — 无限水岩浆磁盘（复用 977 `CreativeCellInventory` + ae2fc `Util.getAEFluidFromItem`）
- `mixin/nei/MixinNEIRecipeWidget.java` — NEI 配方界面 AE 角标
- `network/CraftingCompletePacket.java` — 合成完成 S2C 通知包
- `client/overlay/CraftingNotificationOverlay.java` — 合成完成屏幕通知
- `network/ReplanPacket.java` + `util/Replanner.java` — 合成重新规划
- `block/BlockExIOPort.java` + `tile/TileExIOPort.java` + `render/RenderBlockExIOPort.java` — 强化版 IO 端口
- `client/NetworkTooltipHandler.java` — NEI 配方 tooltip

### 修改文件

- `CommonProxy.java` — 注册 `ItemInfinityWaterLavaCell`、`BlockExIOPort`、`TileExIOPort`
- `mixin/nei/MixinGuiMEMonitorable.java` — 缓存 AE 网络数据（数量 + 可合成）到 `NetworkInventoryCache`
- `mixin/ae/MixinCraftingCPUCluster.java` — 合成完成事件捕获
- `mixin/ae/MixinTileIOPort.java` — 强化 IO 端口倍率
- `mixins.ae2_auto_pattern_upload.json` — 注册新 mixin
- `lang/zh_CN.lang` + `lang/en_US.lang` — 新物品/提示翻译
- `textures/items/infinity_water_lava_cell.png` — 新磁盘贴图
- `gradle.properties` — modVersion = 3.0.0
- `mcmod.info` — version = 3.0.0
- `CHANGELOG.md` — 新增 3.0.0 条目

---

## 2.14.1 - Bug修复：记住供应器丢失 + 绑定消息中文 + WAILA频道数修正 + 书签去反射

> 作者：wztwzt | 更新时间：2026-08-13

### Bug修复

- **记住的供应器丢失（映射中文名消失）**：`ClientState.rememberedProviders` 声明在 `static {}` 块之后，`loadRemembered()` 在静态初始化时执行导致 `rememberedProviders` 为 null，NPE 被吞掉后每次启动都加载失败。将 Map 声明移到 static 块之前，重启后 `remembered_providers.json`（配方池→供应器中文名映射）正常恢复，"自动上传匹配上次供应器"策略重新生效
- **绑定消息服务端英文**：`ItemWirelessConnector` 13 处服务端聊天消息用 `StatCollector.translateToLocal(Formatted)` 在服务端（en_US）翻译成英文。全部改为 `ChatComponentTranslation` + `ChatStyle` 颜色，客户端按中文 locale 渲染
- **WAILA 频道数上限**：`updateChannelCounts()` 原显示 `32 - receiverUsed`，改为 `computeMaxChannels()`——遍历收发器物理连接对端节点（cast `appeng.me.GridNode` 取 `getMaxChannels()`），取最小值为可分发频道上限。普通线缆接入显示 8，密集线缆 32，控制器 MAX，无物理连接回退 32
- **NEI 书签优先级去反射**：`getBookmarkPriorities()` 不再反射访问 `BookmarkGrid.bookmarkItems` 私有字段，改用公开 API `grid.getBookmarkItem(i)` 循环读取（越界返回 null），并输出 `[APU] Bookmark priorities: N` 日志便于确认

### 修改文件

- `client/ClientState.java` — `rememberedProviders` 声明移到 static 块之前
- `wireless/ItemWirelessConnector.java` — 13 处服务端消息改 `ChatComponentTranslation` + `ChatStyle`
- `wireless/WirelessLinkManager.java` — 新增 `computeMaxChannels()` 取物理连接对端最小容量
- `mixin/nei/MixinDefaultOverlayHandler.java` — 书签优先级改用公开 API + 日志
- `gradle.properties` — modVersion = 2.14.1
- `mcmod.info` — version = 2.14.1
- `CHANGELOG.md` — 新增 2.14.1 条目

---

## 2.14.0 - Bug修复：书签优先级恢复 + NEI 中键冲突 + 无线收发器全面修复

> 作者：wztwzt | 更新时间：2026-08-13

### Bug修复

- **NEI 书签优先级恢复**：`MixinDefaultOverlayHandler` 的 `@Overwrite assignIngredients()` 返回值从 `ArrayList` 改为 `List`，与运行时 NEI 方法描述符精确匹配。此前返回值类型参与 JVM 方法描述符导致 Mixin 找不到目标，整个覆盖被静默跳过，书签优先级从未生效
- **NEI 中键合成冲突**：LWJGL 按钮 0=左键/1=右键/2=中键。原代码用 `button == 1` 拦截的是右键，中键落回 NEI 原生拖出逻辑。改为 `button == 2`，仅在物品可合成时拦截，不可合成仍走原生拖出
- **无线收发器方块消失**：`securityBreak()` 原实现直接 `removeTileEntity` + `setBlockToAir`，AE2 在方块接入受保护网络时调用导致方块被销毁。改为仅断开连接、注销频道并标记未连接，不再销毁方块
- **发送端状态不同步**：`WirelessLinkManager` 此前只对接收端调用 `setConnected(true)`，发送端 `isConnected` 永不为 true。新增 `markConnected()` 统一同步两端连接、频道数和方块更新
- **服务器收发器连不上**：块卸载时 `onChunkUnload` 置空 `gridNode` 但不注销，残留失效发送端阻塞重注册。发送端分支现在检测 map 条目为 null/失效/无 gridNode 时强制重注册；接收端分支额外校验 `sender.getGridNode(null) != null`
- **WAILA 频道数恒为 0**：原实现客户端读取服务端专用静态 `WirelessData.getAllFrequencies()`。改为实现 `getNBTData()` 在服务端计算频道数写入 NBT，`getWailaBody` 从 accessor NBT 读取
- **高亮线不可见**：`WirelessHighlightRenderer` alpha 从 0.3–0.45 提升至 0.55–0.70，新增 `GL11.glLineWidth(3.0F)`，包围盒外扩 ±0.06，绘制结束恢复线宽

### 修改文件

- `mixin/nei/MixinDefaultOverlayHandler.java` — @Overwrite 返回值 `ArrayList` → `List`
- `mixin/nei/MixinPanelWidgetClick.java` — 中键按钮值 `button == 1` → `button == 2`
- `wireless/TileWirelessTransceiver.java` — `securityBreak()` 不再销毁方块
- `wireless/WirelessLinkManager.java` — 新增 `markConnected()` 同步两端；发送端失效重注册；接收端 gridNode 校验
- `wireless/TransceiverWailaProvider.java` — `getNBTData()` 服务端计算频道数
- `client/render/WirelessHighlightRenderer.java` — alpha/线宽/包围盒增强
- `gradle.properties` — modVersion = 2.14.0
- `mcmod.info` — version = 2.14.0
- `CHANGELOG.md` — 新增 2.14.0 条目

---

## 2.13.0 - NEI Shift+左键取出物品 + 中键合成下单 + 书签优先级

> 作者：wztwzt | 更新时间：2026-08-12

### 新功能

- **NEI Shift+左键取出物品**：在任意 GUI 中，Shift+左键点击 NEI 书签面板中的物品，从 AE2 网络取出一组物品到背包。若网络中无该物品但可合成，自动跳转合成下单流程
- **NEI 中键合成下单**：在任意 GUI 中，中键点击 NEI 书签面板中可合成的物品，打开 AE2 原版合成确认界面（ContainerCraftAmount）。需要玩家身上携带无线终端且在范围内
- **NEI 书签优先级**：NEI 配方传输时，书签中的物品在矿辞替代选择中获得优先加成（书签列表越靠前优先级越高）

### 兼容性

- 与 AE2Things 兼容：检测 `cir.getReturnValue()` 避免冲突；在 AE2 终端 GUI 中使用 AE2 原版 `PacketInventoryAction`，非 AE2 GUI 中使用自定义数据包

### 技术实现

- MixinPanelWidgetClick：注入 `PanelWidget.handleClick()` HEAD，拦截 Shift+左键和中键点击
- ExtractItemPacket（C2S）：客户端发送目标物品 → 服务端通过无线终端从 AE2 网络提取到背包
- RequestCraftingPacket（C2S）：客户端发送目标物品 → 服务端通过 `Platform.openGUI` 打开 ContainerCraftAmount
- CraftingResponsePacket（S2C）：服务端发送操作结果 → 客户端聊天栏提示
- ServerTerminalHelper：服务端无线终端解析工具，从玩家背包（含 Baubles）查找无线终端
- MixinDefaultOverlayHandler @Overwrite：`assignIngredients()` 添加书签优先级加成

### 新增文件

- `network/ServerTerminalHelper.java` — 无线终端解析工具
- `network/ExtractItemPacket.java` — 取出物品 C2S 数据包
- `network/RequestCraftingPacket.java` — 合成下单 C2S 数据包
- `network/CraftingResponsePacket.java` — 操作结果 S2C 数据包
- `mixin/nei/MixinPanelWidgetClick.java` — NEI 面板点击处理

### 修改文件

- `mixin/nei/MixinDefaultOverlayHandler.java` — @Overwrite `assignIngredients()` 添加书签优先级
- `network/ModNetwork.java` — 注册 3 个新数据包
- `mixins.ae2_auto_pattern_upload.json` — 注册 MixinPanelWidgetClick
- `gradle.properties` — modVersion = 2.13.0
- `CHANGELOG.md` — 新增 2.13.0 条目

---

## 2.12.1 - Bug修复：样板交换支持流体产物 + 只轮转非空槽位

> 作者：wztwzt | 更新时间：2026-08-11

### Bug修复

- **流体产物序列化丢失**：交换后服务端将 `IAEStack<?>` 转为 `ItemStack` 发送给客户端，`IAEFluidStack`（流体）因 `instanceof IAEItemStack` 判定失败被丢弃为 null，导致流体产物在客户端显示为空。改用 NBT 序列化（`FluidStack.writeToNBT` / `loadFluidStackFromNBT`），同时支持 `IAEItemStack` 和 `IAEFluidStack`
- **空槽位参与轮转**：旧逻辑对所有槽位做循环左移，空槽位"吸收"了物品导致显示错位。改为只收集非空槽位轮转，空槽位保持不动。例如 `[空单元, 流体, 空]` → `[流体, 空单元, 空]` 而非 `[流体, 空, 空单元]`

### 修改文件

- `SwapPatternPacket.java` — 序列化从 `List<ItemStack>` 改为 `List<IAEStack<?>>`，type byte 区分物品(1)/流体(2)；交换逻辑改为只轮转非空槽位；客户端直接使用 `IAEStack<?>` 不再经 `ItemStack` 中转

---

## 2.12.0 - 改名 AE2 QoL + 样板交换实时同步 + NEI 叠加层按钮

> 作者：wztwzt | 更新时间：2026-08-11

### 改名

- **模组名称**：从 "AE2 Auto Pattern Upload" 改为 "AE2 QoL"，反映功能范围扩大（样板管理 + 无线访问 + NEI 增强）
- **导出文件名**：从 `ae2_auto_pattern_upload-x.x.x.jar` 改为 `AE2-QoL-x.x.x.jar`

### Bug修复

- **样板交换按钮实时同步**：交换后服务端立即发送 S2C 同步包，客户端收到后更新 `outputSlotsClient`，无需等待编码即可看到产物变化
- **循环交换逻辑**：2个产物直接互换；3+个产物循环左移（123→231→312→123）
- **移除客户端 nonNullCount 预检**：`outputSlotsClient` 在服务端同步前可能全为 null，不再拦截交换请求

### 新功能

- **NEI 叠加层切换按钮**：在样板终端 GUI 新增 OV/-- 按钮（位于 ⇄ 交换按钮右侧），点击切换 NEI 书签面板物品数量/可合成标记的显示/隐藏，替代 `/apu-overlay` 命令

### 按钮布局

```
          OV(996) ←(998)
  ⇄(997)  ↑(999)
         编码按钮
```

### 修改文件

- `SwapPatternPacket.java` — 重写：C2S 触发交换 + S2C 携带新值同步；ByteBufUtils 序列化；2=互换 / 3+=循环左移
- `ModNetwork.java` — SwapPatternPacket 注册为双向（SERVER + CLIENT）
- `GuiUploadButtonHandler.java` — 移除 `countOutputSlots()`；新增 OVERLAY_ID 按钮
- `MyMod.java` — `@Mod` name 改为 "AE2 QoL"
- `mcmod.info` — name/description/version 更新
- `gradle.properties` — modName = AE2 QoL, modVersion = 2.12.0, customArchiveBaseName = AE2-QoL
- `settings.gradle.kts` — rootProject.name = "AE2-QoL"

---

## 2.11.1-fix1 - Bug修复：样板交换按钮读取错误槽位

> 作者：wztwzt | 更新时间：2026-08-11

### 问题

交换按钮点击无效果。`getPatternFromOutputSlot()` 读取 `patternSlotOUT`（编码后样板输出槽），但编辑模式下产出物在 `outputs` 虚拟显示槽（`IAEStackInventory`），`patternSlotOUT` 为空导致 `patternStack=null`。

### 修复

- **SwapPatternPacket**：改为通过反射读取 `outputs`（`IAEStackInventory`），用 `getAEStackInSlot` / `putAEStackInSlot` 交换第0和第1个槽位，调用 `markDirty()` 同步
- **客户端校验**：改为检查 `outputSlotsClient`（公开字段）中非null元素数量 ≥ 2

### 修改文件

- `SwapPatternPacket.java` — 交换 `outputs` IAEStackInventory 而非 pattern NBT
- `GuiUploadButtonHandler.java` — 客户端校验改为 `countOutputSlots()`

---

## 2.11.1 - Bug修复：样板交换按钮逻辑修正 + 位置调整

> 作者：wztwzt | 更新时间：2026-08-10

### Bug修复

- **交换逻辑修正**：从"交换 `in`/`out` 整个列表"改为"交换 `out` 列表内第0和第1个元素"，正确实现主副产物交换
- **按钮位置调整**：从上传按钮左侧移到上传按钮正上方
- **客户端校验**：点击前检查 `out` 列表至少有 2 个元素

### 修改文件

- `SwapPatternPacket.java` — 交换逻辑改为 `outTag.func_150304_a(0, out1)` / `func_150304_a(1, out0)`
- `GuiUploadButtonHandler.java` — 按钮位置 `swapBtnY = uploadBtnY - btnSize - 2`；客户端校验改为检查 `out` 标签

---

## 2.11.0 - 新功能：样板主副产物交换按钮

> 作者：wztwzt | 更新时间：2026-08-10

### 新功能

- **样板交换按钮**：在 AE2 样板终端 GUI 新增 ⇄ 交换按钮（位于 ↑上传 按钮左侧），点击后交换当前样板的输入/输出物品
- 支持 `ContainerPatternTerm` 和 `ContainerPatternTermEx`（ExtendedAE 扩展样板终端）
- 需要输出槽中已有编码样板，且样板包含 `in`/`out` 标签

### 按钮布局

```
  ⇄(997)  ↑(999)  ←(998)
         编码按钮
```

### 新增文件

- `SwapPatternPacket.java` — 客户端→服务端，交换样板 NBT 的 `in`/`out` 标签

### 修改文件

- `ModNetwork.java` — 注册 `SwapPatternPacket`
- `GuiUploadButtonHandler.java` — 添加交换按钮 + 点击事件处理

---

## 2.10.0-fix11 - Bug修复：绑定逻辑不生效（C08数据包未发送）

> 作者：wztwzt | 更新时间：2026-08-09

### 问题

fix10 的绑定逻辑仍然不生效。客户端 `onItemUseFirst` 对绑定目标返回 `true`，导致客户端 `PlayerControllerMP.onPlayerRightClick` 在行371直接 `return true`，**C08PacketPlayerBlockPlacement 永远不会发送**（行389被跳过）。服务端从未收到右键数据包，`activateBlockOrUseItem` 从未被调用，服务端的绑定逻辑为死代码。

### 修复

- **客户端 `onItemUseFirst` 返回 `false`**：让C08数据包正常发送到服务端，服务端正常执行绑定逻辑

### 修改文件

- `ItemWirelessConnector.java` — 客户端 `onItemUseFirst` 返回 `false`

---

## 2.10.0-fix10 - Bug修复：绑定器shift+右键收发器失效 + 绑定逻辑架构修复

> 作者：wztwzt | 更新时间：2026-08-09

### 问题

- **shift+右键收发器绑定频道失效**：fix9新增的 `onItemUseFirst` 返回 `true` 会同时跳过 `onBlockActivated` **和** `onItemUse`，导致绑定逻辑永远不执行
- **ME接口/GT舱室方块链接失效**：同样原因，`onItemUse` 中的方块链接逻辑也无法执行

### 根因

Forge 1.7.10 调用链：`onItemUseFirst → onBlockActivated → onItemUse`，`onItemUseFirst` 返回 `true` 会跳过**全部后续调用**，包括 `onItemUse` 中的绑定逻辑

### 修复

- **绑定逻辑前移**：将全部绑定逻辑（收发器绑定、方块链接、解绑等）从 `onItemUse` 移入 `onItemUseFirst` 服务端分支，`onItemUseFirst` 返回 `true` 时直接执行绑定
- **`onItemUse` 简化**：仅保留 `return true` 兜底（非绑定目标不会到达此处）
- **`doesSneakBypassUse` → `false`**：潜行时不再绕过 `onBlockActivated`，避免误开非绑定目标GUI

### 修改文件

- `ItemWirelessConnector.java` — `onItemUseFirst` 包含全部绑定逻辑；`onItemUse` 简化为 `return true`；`doesSneakBypassUse` → `false`

---

## 2.10.0-fix9 - Bug修复：绑定器无法绑定GT舱室/ME接口 + GUI拦截

> 作者：wztwzt | 更新时间：2026-08-09

### Bug修复

- **绑定器拦截GUI**：新增 `onItemUseFirst()` 方法，在 `Block.onBlockActivated` 之前执行，阻止ME接口/舱室的GUI被打开
- **GT ME舱室/ME接口绑定**：所有 `instanceof IGridHost` 检查改为 `instanceof TileCableBus || instanceof IGridHost`，支持独立方块和线缆部件两种形式
- **doesSneakBypassUse**：改为返回 `true`，让潜行时也走 `onItemUseFirst` 钩子
- **onItemUse 返回值**：所有分支统一返回 `true`，确保不触发 `onBlockActivated`

### 修改文件

- `ItemWirelessConnector.java` — 新增 `onItemUseFirst()`；`isBindingTarget()` 统一判断；`TileCableBus || IGridHost`；`doesSneakBypassUse` → true

---

## 2.10.0-fix8 - Bug修复：物品栏渲染崩溃 + 绑定器UI拦截

> 作者：wztwzt | 更新时间：2026-08-09

### Bug修复

- **物品栏渲染崩溃修复**：`renderInventoryBlock` 改回 Tessellator 方式，去掉 `addTranslation` 偏移；不再调用 `renderStandardBlock`（物品栏渲染时 `IBlockAccess` 为 null 导致 NPE）
- **绑定器UI拦截**：绑定器未绑定频率时右键 IGridHost 方块（如ME接口），返回 `true` 阻止 `onBlockActivated` 打开方块GUI，并提示"请先绑定频道"

### 修改文件

- `RenderBlockTransceiver.java` — `renderInventoryBlock` 改用 Tessellator + `renderFace*` 直接传 icon
- `ItemWirelessConnector.java` — 未绑定频率时对 IGridHost 方块返回 `true` 阻止 GUI 打开

---

## 2.10.0-fix7 - Bug修复：材质偏移 + 频道切换消失 + 绑定器取消 + 高亮无效 + 绑定器适配

> 作者：wztwzt | 更新时间：2026-08-09

### Bug修复

- **材质偏移修复**：`renderInventoryBlock` 改用 `renderer.renderStandardBlock(block, 0, 0, 0)` + `setOverrideBlockTexture(iconBase)`，修复物品栏显示错误纹理的问题
- **切换频道消失修复**：`handleSetFrequency` 改用 `unregister(oldFreq)` 只删除内存 senderMap，不再从 `WirelessWorldData` 持久化中删除频道
- **绑定器取消绑定修复**：`WirelessWorldData` 新增 `removeBlockLink(freq, posKey)` 方法精确按位置删除；绑定器调用带位置参数的版本
- **高亮无效修复**：`WirelessHighlightRenderer` 改注册到 `MinecraftForge.EVENT_BUS`；事件改为 `RenderWorldLastEvent`；颜色改为红色 (255,0,0)
- **绑定器适配增强**：`getGridNodeFromTE` 策略调整 — TileCableBus 先尝试 `part.getGridNode(dir)`，再 `part.getExternalFacingNode()`；IGridHost 先指定面 → UNKNOWN → 6方向遍历

### 修改文件

- `RenderBlockTransceiver.java` — `renderInventoryBlock` 改用 `renderStandardBlock`
- `WirelessActionPacket.java` — `handleSetFrequency` 改用 `unregister(oldFreq)` 不删持久化
- `WirelessWorldData.java` — 新增 `removeBlockLink(freq, posKey)` 方法
- `ItemWirelessConnector.java` — 绑定器调用精确删除；`getGridNodeFromTE` 策略调整
- `ClientProxy.java` — 高亮渲染器改注册到 `MinecraftForge.EVENT_BUS`
- `WirelessHighlightRenderer.java` — 事件改为 `RenderWorldLastEvent`；颜色改为红色
- `WirelessBlockLinkManager.java` — `getGridNode` 同步调整

---

## 2.10.0-fix6 - Bug修复：物品栏图标 + 频道创建 + 绑定器多设备 + 舱室适配

> 作者：wztwzt | 更新时间：2026-08-09

### Bug修复

- **物品栏图标修复**：`renderInventoryBlock` 添加 Tessellator 管理（`startDrawingQuads`/`draw`），修复ISBRH渲染不提交顶点导致图标不显示的问题
- **频道创建修复**：`handleAddChannel` 只注册全局频道到 `WirelessWorldData`，不再覆盖收发器频率；用户从列表点击选择要连接的频率
- **绑定器多设备支持**：`WirelessBlockLinkManager` 从 `Map<String, WirelessBlockLinkData>` 改为 `Map<String, List<WirelessBlockLinkData>>`，同一频率可绑定多个设备
- **绑定器响应速度**：`processAll()` 处理间隔从 20 tick 缩短到 5 tick（0.25秒）
- **绑定器舱室适配**：`getGridNodeFromTE` 先尝试 `UNKNOWN` 方向，失败后遍历6个方向，适配所有 `IGridHost` 设备

### 修改文件

- `RenderBlockTransceiver.java` — `renderInventoryBlock` 添加 Tessellator 管理和居中平移
- `WirelessActionPacket.java` — `handleAddChannel` 改为 `addGlobalChannel`；`handleRemoveChannel` 支持删除指定频道
- `WirelessData.java` — 新增 `addGlobalChannel` 方法；`getAllFrequencies` 从 `WirelessWorldData` 读取；`isFrequencyTaken` 检查持久化数据
- `WirelessBlockLinkManager.java` — 改为 `Map<String, List<WirelessBlockLinkData>>`；`register` 追加不替换；`unregister(freq, positionKey)` 按位置删除；连接键改为 `freq:positionKey`
- `ItemWirelessConnector.java` — `getGridNodeFromTE` 遍历6个方向；绑定器按位置匹配切换绑定
- `WirelessWorldData.java` — `addBlockLink` 按位置去重，支持同频率多设备
- `TileWirelessTransceiver.java` — `updateEntity` 处理间隔缩短到 5 tick

---

## 2.10.0-fix5 - Bug修复：收发器不占频道 + WAILA频道数 + 严格单向 + UI改进

> 作者：wztwzt | 更新时间：2026-08-09

### Bug修复

- **收发器不占用频道**：移除 `REQUIRE_CHANNEL` 标志，收发器作为纯桥梁不消耗AE2频道
- **WAILA频道数显示**：连接后显示实际传输频道数/可用频道数（如 `3/22`），未连接时显示全局频率总数
- **UI按钮右移修复**：按钮宽度55→53，起始位置8→7，消除右侧超出GUI边界
- **绑定器右键机器**：`doesSneakBypassUse` 改为 `false`，防止潜行时绕过绑定逻辑打开机器UI
- **严格单向连接**：添加 `originalSenderPos` 验证，发送端→接收端方向锁定，互换角色后不自动连接
- **UI状态同步**：`setConnected()`/`setPaused()` 添加 `markBlockForUpdate()`，修复GUI显示"未连接"但实际已连接的问题
- **频道名无限制**：移除32频道名上限，支持任意数量频道名
- **频道列表滚动**：添加鼠标滚轮滚动和拖动滚动条，超出显示范围时自动启用

### 修改文件

- `TileWirelessTransceiver.java` — 移除REQUIRE_CHANNEL；添加usedChannels/maxChannels/originalSenderPos字段；setConnected/setPaused加markBlockForUpdate
- `WirelessLinkManager.java` — 验证发送端身份（originalSenderPos）；更新频道计数
- `WirelessActionPacket.java` — 移除32限制；模式切换时存储发送端位置
- `TransceiverWailaProvider.java` — 显示实际传输频道数/可用频道数
- `ItemWirelessConnector.java` — doesSneakBypassUse改为false
- `GuiWireless.java` — 按钮宽度调整；鼠标滚轮+拖动滚动条

---

## 2.10.0-fix4 - Bug修复：物品栏图标 + 绑定器右键 + 无线频道传输 + UI布局

> 作者：wztwzt | 更新时间：2026-08-08

### Bug修复

- **物品栏图标修复**：`BlockWirelessTransceiver.registerBlockIcons()` 设置 `this.blockIcon`，修复物品栏显示缺失材质的问题
- **绑定器右键机器修复**：`ItemWirelessConnector.onItemUse()` 客户端返回 `true`，阻止 `onBlockActivated` 干扰绑定逻辑
- **无线频道传输修复**：`blockProxy.getFlags()` 添加 `GridFlags.REQUIRE_CHANNEL`，与AE2原版 `TileWireless` 一致；创建 `GridConnection` 后调用 `receiverNode.updateState()` 确保节点加入网格
- **UI文字重叠修复**：调整标题和标签 Y 坐标，消除重叠
- **按钮拆分**：Mode 切换按钮拆分为"发送端"（蓝色）和"接收端"（黄色）两个独立按钮
- **接收端颜色**：接收端模式颜色从红色改为黄色

### 修改文件

- `BlockWirelessTransceiver.java` — `registerBlockIcons` 设置 `this.blockIcon`
- `ItemWirelessConnector.java` — `onItemUse` 客户端返回 `true`
- `TileWirelessTransceiver.java` — `blockProxy.getFlags()` 添加 `REQUIRE_CHANNEL`
- `WirelessLinkManager.java` — 创建连接后调用 `receiverNode.updateState()`
- `GuiWireless.java` — 标题Y坐标调整、按钮拆分（Sender/Receiver）、接收端黄色

---

## 2.10.0-fix3 - Bug修复 + 功能增强

> 作者：wztwzt | 更新时间：2026-08-08

### Bug修复

- **绑定器shift+右键修复**：手持绑定器shift+右键收发器不再打开GUI，改为正确绑定频道
- **频道列表同步**：修复客户端频道列表为空导致创建第二个频道时第一个消失的问题
- **断开按钮修复**：点击断开后不再立即重连，添加暂停状态；添加频道/切换模式时自动恢复连接
- **输入限制修复**：移除数字限制，支持输入中文/英文频道名
- **GUI自动聚焦修复**：打开GUI时输入框不再自动获取焦点
- **StackOverflowError修复**：修复validate()中markDirty()导致的无限递归崩溃

### 材质修复

- 方块材质从32x32四象限精灵图裁剪为16x16单WiFi图标
- 物品栏图标正常显示

### 新功能

- **最大32频道限制**：与ME控制器单面频道数一致
- **WAILA频道数显示**：显示"频道数: X/32"，暂停状态显示"已暂停"
- **高亮连接频道按钮**：点击后高亮显示当前频道连接的所有方块边框

### 新增文件

- `network/WirelessChannelSyncPacket.java` — 服务端→客户端频道列表同步包
- `network/WirelessHighlightPacket.java` — 高亮位置同步包
- `client/render/WirelessHighlightRenderer.java` — 方块高亮渲染器（RenderWorldLastEvent）

### 修改文件

- `BlockWirelessTransceiver.java` — onBlockActivated检查绑定器
- `TileWirelessTransceiver.java` — 添加paused/highlightEnabled字段
- `WirelessLinkManager.java` — 检查paused状态
- `WirelessActionPacket.java` — 添加HIGHLIGHT/SYNC频道列表同步，32频道限制
- `ContainerWireless.java` — 频道列表同步
- `GuiWireless.java` — 输入限制修复、高亮按钮、暂停状态显示、频道列表同步
- `TransceiverWailaProvider.java` — 频道数/暂停状态显示
- `ClientState.java` — 高亮渲染状态
- `ClientProxy.java` — 注册高亮渲染器
- `ModNetwork.java` — 注册新数据包
- 语言文件 — 添加highlight/paused/channels_used翻译

---

## 2.10.0-fix2 - 材质渲染 + GUI/线缆修复 + Waila改进 + 消息补全

> 作者：wztwzt | 更新时间：2026-08-08

### 自定义 ISBRH 渲染器

**新增文件：**
- `client/render/RenderBlockTransceiver.java` — 自定义 `ISimpleBlockRenderingHandler`，从32x32精灵图中根据 TileEntity 状态选择正确的子纹理区域渲染每个面

**材质子区域映射：**
| 状态 | 32x32位置 | UV区域 |
|------|-----------|--------|
| 接收端·未连接 | 左上 | u=[min,mid] v=[min,mid] |
| 发送端·未连接 | 左下 | u=[min,mid] v=[mid,max] |
| 发送端·已连接 | 右下 | u=[mid,max] v=[mid,max] |
| 已连接(侧面) | `_light.png` 动画纹理 |
| 已连接(顶面) | `_light_top.png` 动画纹理 |
| 已连接(底面) | `_light_bottom.png` 动画纹理 |

### GUI/线缆修复

**修改文件：**
- `wireless/BlockWirelessTransceiver.java` — 添加 `hasTileEntity(int)` 返回 `true`（修复 GUI 打不开和线缆无法连接）；覆盖 `getRenderType()` 返回自定义渲染器 ID；覆盖 `registerBlockIcons()` 委托给 ISBRH；覆盖 `isOpaqueCube()` 返回 `true`
- `ClientProxy.java` — 注册 `RenderBlockTransceiver` 渲染器

### Waila 注册改进

**修改文件：**
- `CommonProxy.java` — Waila IMC 注册不再静默吞掉异常，改为 `LOG.error()` 记录错误；添加小写 `"waila"` 备用尝试

### 绑定器消息补全

**修改文件：**
- `wireless/ItemWirelessConnector.java` — 右键空气非潜行时提示"请先绑定频道"或"已绑定频道，请右键ME设备"；右键非支持方块时提示"目标方块不支持无线连接"
- `lang/zh_CN.lang` — 新增3个 key：`bind.fail.invalid_target`、`hint.bind_first`、`hint.use_device`
- `lang/en_US.lang` — 同步新增对应英文翻译

---

## 2.10.0-fix1 - 诊断修复：PreInit 崩溃诊断 + 依赖声明

> 作者：wztwzt | 更新时间：2026-08-08

### 依赖声明

**修改文件：**
- `MyMod.java` — `@Mod` 注解添加 `dependencies = "required-after:appliedenergistics2"`，确保 AE2 在本 mod 之前加载

### PreInit 诊断

**修改文件：**
- `CommonProxy.java` — `WirelessBlocks.preInit()` 包裹 try-catch，捕获异常同时通过 `LOG.error()` 和 `t.printStackTrace(System.err)` 双重输出
- `MyMod.java` — `ModNetwork.registerPackets()` 包裹 try-catch，同上双重输出

**改动：**
1. **依赖加载顺序修复**：添加 `required-after:appliedenergistics2` 确保 AE2 的类（IGridHost、IGridNode 等）在我们的 mod preInit 之前可用
2. **PreInit 崩溃诊断**：Log4j 的 `ThrowableProxy` 在 RfbSystemClassLoader 下无法加载 `net.minecraft.block.Block`，导致真正的 preInit 异常被完全遮蔽。通过 try-catch + `System.err` 绕过 Log4j，可捕获并输出真实异常堆栈

---

## 2.10.0 - 无线直连功能 + BlockContainer 崩溃修复

> 作者：wztwzt | 更新时间：2026-08-08

### BlockContainer 崩溃修复

**修改文件：**
- `wireless/BlockWirelessTransceiver.java` — `extends BlockContainer` 改为 `extends Block implements ITileEntityProvider`，绕过 Java 17 环境下 `BlockContainer` 类加载失败的问题；`breakBlock()` 手动调用 `world.removeTileEntity()`

### 无线直连任意 ME 设备

**新增文件：**
- `wireless/link/WirelessBlockLinkData.java` — 直连数据类（坐标、频道、UUID、维度、方向）
- `wireless/link/WirelessBlockLinkManager.java` — 直连管理器，负责扫描、连接、断开无线直连
- `wireless/WirelessBlockEventListener.java` — Forge 方块破坏事件监听，自动清理被破坏方块的无线连接
- `wireless/ItemBlockTransceiver.java` — 收发器物品方块，提供 tooltip 说明

**修改文件：**
- `wireless/ItemWirelessConnector.java` — 新增绑定器交互逻辑：右键 IGridHost 方块建立无线连接；右键已绑定设备解除连接；AE2 线缆部件识别（通过 `TileCableBus.getPart()` 获取特定面的部件）；跨维度限制提示；tooltip 说明
- `wireless/WirelessWorldData.java` — 新增 `wireless_block_links` NBT 存储，支持 block link 持久化
- `wireless/WirelessData.java` — 引用 `WirelessBlockLinkManager`，`unregister()` 时同步清理 block link
- `wireless/TileWirelessTransceiver.java` — `updateEntity()` 中调用 `WirelessBlockLinkManager.processAll()`；新增 `removeAllBlockLinks()` 方法
- `wireless/BlockWirelessTransceiver.java` — `breakBlock()` 中额外清理关联的 block links
- `wireless/WirelessBlocks.java` — 注册 `ItemBlockTransceiver` 替代默认 ItemBlock
- `CommonProxy.java` — 注册 `WirelessBlockEventListener` 事件监听
- `network/WirelessActionPacket.java` — 硬编码字符串替换为 `StatCollector` 翻译

### Tooltip + 汉化

**修改文件：**
- `lang/zh_CN.lang` — 新增 12 个 tooltip key + 5 个新功能 chat 消息 key
- `lang/en_US.lang` — 同步新增对应英文翻译

### 绑定器完整交互逻辑

| 操作 | 目标 | 行为 |
|------|------|------|
| Shift+右键 | 收发器 | 绑定频道到连接器 |
| Shift+右键 | 空气 | 清除绑定 |
| 右键 | 收发器 | 设置为接收端 |
| 右键 | IGridHost 方块 | 建立无线直连（仅同维度） |
| 右键 | 已绑定的 IGridHost | 解除无线直连 |

---

## 2.9.0 - 重大修复：映射持久化 + 无线收发器全面重构

> 作者：wztwzt | 更新时间：2026-08-08

### 映射持久化修复

**新增文件：**
- `client/ClientRecipeNameUtil.java` — 客户端专属 NEI 配方名工具，从 RecipeNameUtil 分离

**修改文件：**
- `util/RecipeNameUtil.java` — 移除 `import codechicken.nei.recipe.IRecipeHandler`；静态初始化 try-catch 防护；`writeTemplate()` 仅在文件不存在时创建；新增 `loadBuiltinDefaults()` 加载 jar 内置 47 条默认映射；`CONFIG_FILE` 保护 null 检查；公开 `CAMEL_CASE_SPLITTER` 和 `mapStringToMapping()` 供 ClientRecipeNameUtil 使用
- `common/RecipeMapNameConfig.java` — `reload()` 和 `resolveSearchKeyword()` 加 `synchronized`；改用 volatile Map 引用原子替换避免竞态
- `mixin/nei/MixinDefaultOverlayHandler.java` — 改用 `ClientRecipeNameUtil.captureFromRecipeHandler()`
- `mixin/nei/MixinRecipeHandlerRef.java` — 同上

**改动：**
1. **NEI 依赖分离**：`RecipeNameUtil` 不再 import 客户端专属的 `IRecipeHandler`，服务器端加载不会因缺少 NEI 类而崩溃（`ExceptionInInitializerError`）
2. **静态初始化防护**：`Loader.instance().getConfigDir()` 调用包裹在 try-catch 中，即使 `Loader` 未就绪也不会导致类初始化失败
3. **配置文件写入保护**：`writeTemplate()` 仅在文件不存在时创建空 `{}`，不会覆盖已有数据
4. **内置默认映射**：启动时从 `apu/recipe_type_names.json` 加载 47 条默认映射作为后备，用户自定义映射覆盖默认值
5. **竞态条件修复**：`RecipeMapNameConfig` 改用 volatile Map 引用原子替换，`reload()` 和 `resolveSearchKeyword()` 加 synchronized

### 无线收发器全面重构

**新增文件：**
- `network/WirelessActionPacket.java` — C2S 网络包，携带 action 枚举（添加频道/删除/切换模式/断开/设置频率）+ 频道名 + 模式值

**修改文件：**
- `wireless/TileWirelessTransceiver.java` — 新增 `wirelessConnection` 字段保存 IGridConnection 引用；`validate()` 自动注册发送端；`invalidate()`/`onChunkUnload()` 先销毁无线连接再注销；新增 `destroyWirelessConnection()` 方法；`updateEntity()` 节流到每 20 tick；null 频率保护
- `wireless/BlockWirelessTransceiver.java` — `breakBlock()` 调用 `twt.destroyWirelessConnection()` + `unregister(freq, world)`；移除冗余 `node.destroy()`（由 `invalidate()` 处理）
- `wireless/WirelessData.java` — `register()` 同步写入 WirelessWorldData；新增 `unregister(freq, world)` 从持久化中移除频道；新增 `saveToWorldData()` 方法
- `wireless/WirelessWorldData.java` — 修复 `getActiveChannels()` 返回防御性拷贝而非可变引用；`get()` 方法改用 `DimensionManager.getWorld(0)` 获取主世界；去重保护
- `wireless/WirelessLinkManager.java` — 完全重写：移除反射 `GridConnection` 构造器，改用 `AEApi.instance().createGridConnection()` 公开 API；连接追踪（`te.getWirelessConnection()` / `te.setWirelessConnection()`）；连接有效性验证；错误日志记录
- `wireless/gui/ContainerWireless.java` — 重写为标准三层架构：服务端逻辑 + `detectAndSendChanges()` 数据同步
- `wireless/gui/GuiWireless.java` — 全面重写：标准 MC GUI 尺寸（176×166）；白边框黑底（#161618）；左侧频道列表（单选，绿底白字）+ 输入框（仅数字）；右侧状态面板（频道/模式/连接状态，颜色编码）；底部 2×2 按钮区（添加/删除/模式/断开）；删除确认弹窗（模态遮罩）；所有操作通过 WirelessActionPacket 发送到服务端
- `wireless/ItemWirelessConnector.java` — 聊天消息全部汉化：`StatCollector.translateToLocalFormatted()` 替换硬编码英文；新增绑定失败提示（非发送端、无频道）
- `network/ModNetwork.java` — 注册 WirelessActionPacket（discriminator 4, SERVER）

**删除文件：** 无

**改动：**
1. **频道持久化**：WirelessData 注册/注销同步写入 WirelessWorldData（WorldSavedData），服务器重启后频道名不丢失
2. **自动恢复注册**：TileEntity 在 `validate()` 时自动检查并恢复发送端注册，无需手动干预
3. **连接追踪与销毁**：TileEntity 保存 IGridConnection 引用，发送端注销或接收端断开时正确调用 `connection.destroy()`，消除幻影连接
4. **公开 API 替代反射**：`AEApi.instance().createGridConnection(nodeA, nodeB)` 替代反射创建 `GridConnection`，更稳定且兼容性更好
5. **服务端 GUI 逻辑**：所有按钮操作通过 `WirelessActionPacket` 发送到服务端，Container 处理业务逻辑后同步数据回客户端
6. **GUI 重设计**：标准 MC GUI 布局，白边框黑底，频道列表 + 状态面板 + 按钮区，删除确认弹窗
7. **性能优化**：`updateEntity()` 从每 tick 处理改为每 20 tick（1秒）处理一次
8. **绑定器汉化**：所有聊天消息使用 lang key 翻译，支持中英文切换

---

## 2.8.0-fix1 - 修复：无线收发器 GUI 背景 + i18n + Waila + Java 1.7 兼容

> 作者：wztwzt | 更新时间：2026-08-07

**修改文件：**
- `wireless/gui/GuiWireless.java` — 背景改用 wireless.png（1024x1024 纹理 Tessellator 缩放），按钮/标题/模式/状态全部改为 `StatCollector.translateToLocal()`
- `wireless/TransceiverWailaProvider.java` — Waila body 改用 `StatCollector.translateToLocal()`，修复 Java 1.7 不支持的 `instanceof TileWirelessTransceiver tile` 语法（改为传统强转）
- `lang/en_US.lang` — 新增 wireless GUI/模式/状态/频道/按钮/连接器翻译键，修复 `item.wireless.connect.name` → `item.wireless_connect.name`
- `lang/zh_CN.lang` — 同步新增中文翻译

**改动：**
1. **GUI 背景修复**：`wireless.png` 纹理为 1024x1024，`drawTexturedModalRect` 只支持 256x256。改用 `Tessellator` 直接绘制缩放 UV，正确显示无线收发器背景
2. **全量 i18n**：GUI 标题、频道列表标签、模式/状态文本、按钮文字全部使用 `StatCollector.translateToLocal()`，支持中英文切换
3. **Waila i18n**：`TransceiverWailaProvider.getWailaBody()` 原先硬编码 "Mode: Sender" 等英文字符串，改为通过 lang key 翻译
4. **Java 1.7 兼容**：`instanceof TileWirelessTransceiver tile` 是 Java 16+ 语法，改为 `instanceof TileWirelessTransceiver` + 强转
5. **连接器 lang 修正**：`setUnlocalizedName("wireless_connect")` 对应 key 应为 `item.wireless_connect.name`（下划线），原先写成 `item.wireless.connect.name`（点号）

---

## 2.8.0-beta1 - 新功能：AE2 无线收发器（测试版）

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `wireless/TileWirelessTransceiver.java` — 无线收发器 TileEntity（IGridHost + IActionHost + IGridBlock）
- `wireless/BlockWirelessTransceiver.java` — 方块注册、GUI打开、breakBlock 注销
- `wireless/WirelessData.java` — 全局频道注册器（单例 Map）
- `wireless/WirelessLinkManager.java` — 收发连接逻辑（反射创建 GridConnection）
- `wireless/WirelessWorldData.java` — WorldSavedData 频道持久化
- `wireless/WirelessBlocks.java` — 方块/物品/实体注册
- `wireless/WirelessGuiHandler.java` — IGuiHandler 实现
- `wireless/ItemWirelessConnector.java` — 无线连接器手持工具
- `wireless/gui/ContainerWireless.java` — 无线收发器 Container
- `wireless/gui/GuiWireless.java` — 无线收发器 GUI（输入框 + 列表 + 按钮）
- 材质文件（textures/blocks, items, gui）

**修改文件：**
- `MyMod.java` — 添加 `@Mod.Instance`
- `CommonProxy.java` — preInit 注册 WirelessBlocks，init 注册 GuiHandler

**改动：**
1. **无线收发器方块**：接入 AE2 网络后可通过 GUI 设置频道和收发模式
2. **发送端/接收端匹配**：同一频道仅允许一个发送端；接收端通过反射创建 GridConnection 虚拟线缆
3. **频道持久化**：WorldSavedData 保存活跃频道列表
4. **无线连接器**：手持工具，绑定频道后右键方块即可单点接入
5. **GUI**：频道输入、添加/删除/模式切换/断开连接操作
6. **已知问题**：连接建立逻辑需要游戏内验证，目前为 beta 阶段

---

## 2.7.0-fix3 - 修复：混入目标改为 PanelWidget.draw()

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `mixin/nei/MixinPanelWidgetDraw.java` — 替代 `MixinItemsGridDraw`，混入 `PanelWidget.draw(II)` TAIL，通过 `self.x` 判断左侧/右侧
- `mixins.ae2_auto_pattern_upload.json` — `MixinItemsGridDraw` → `MixinPanelWidgetDraw`

**删除文件：**
- `mixin/nei/MixinItemsGridDraw.java` — `ItemsGrid` 级别无法获取 panel 的 x 坐标，区分不了左/右面板

**改动：**
1. **修复左侧判断**：`ItemsGrid` 的 `getSlotRect(0,0).x` 在不同子类中与 panel 位置不一致。改为从 `PanelWidget.x` 直接获取面板的屏幕 x 坐标，准确区分左（书签）/右（全物品）
2. **功能完好**：书签面板显示叠加层，全物品面板不显示，数字 0.6 缩放左置，GlScissor 保护，`/apu-overlay` 开关均保留

---

## 2.7.0-fix2 - 修复：仅书签面板 + 数字左置 + 配置文件开关

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `client/OverlayConfig.java` — 配置文件 `settings.json` 读写，每次渲染实时读取
- `client/CommandOverlay.java` — `/apu-overlay` 游戏内切换命令

**修改文件：**
- `mixin/nei/MixinItemsGridDraw.java` — 左侧面板判断（`getSlotRect(0,0).x < screenWidth/2`）、GlScissor 保护
- `client/NetworkInventoryDrawHandler.java` — 数字缩小（0.6x）、放到物品框左边、检查 `OverlayConfig`
- `client/ClientState.java` — 移除 `toggleOverlay()` 方法
- `client/event/KeyInputHandler.java` — 移除 O 键逻辑
- `ClientProxy.java` — 注册 `/apu-overlay` 命令
- `gradle.properties` — 版本号 2.7.0-fix2

**改动：**
1. **仅书签面板**：通过首个 slot 的 x 坐标判断是否为左侧面板，右侧全物品不显示
2. **文字缩放**：数字和 "+" 缩小为 0.6x，放在物品框左侧（不遮挡图标）
3. **GlScissor 禁用**：绘制前禁用裁剪，防止数字被面板边缘裁切
4. **游戏内开关**：`/apu-overlay` 命令切换，或修改 `config/ae2_auto_pattern_upload/settings.json` 中 `nei_overlay_enabled` 字段

---

## 2.7.0-fix1 - 修复：NEI 物品面板叠加显示（第二版）

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `mixin/nei/MixinItemsGridDraw.java` — 混入 `ItemsGrid.draw(II)` TAIL，在完整绘制链路结束后叠加 AE2 数据

**修改文件：**
- `client/NetworkInventoryDrawHandler.java` — 静态工具类，接收 x/y/ItemStack 绘制叠加层
- `mixins.ae2_auto_pattern_upload.json` — MixinItemsGridSlot → MixinItemsGridDraw
- `ClientProxy.java` — 撤销 v2.7.0 的 `addDrawHandler` 注册

**删除文件：**
- `mixin/nei/MixinItemsGridSlot.java` — 废弃方案（inner class @Mixin 不支持 @Inject）

**改动：**
1. **修复叠加不显示**：不再注入 inner class，改为注入 `ItemsGrid.draw()` TAIL。在 `afterDrawItems()` 执行完后、GL 状态正常时遍历 `getMask()` + `getSlotRect()` 画叠加层
2. **渲染方式**：右上角绿色 +（可合成），右下角青色数字（AE2 网络存量）
3. **GL 安全**：在 draw 完整结束后画文字，不受 slot 内部 GL 变换影响

---

## 2.7.0 - 新功能：NEI 网络库存叠加显示

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `client/NetworkInventoryCache.java` — 客户端缓存 AE2 网络库存数据（数量 + 可合成标识）
- `client/NetworkInventoryDrawHandler.java` — NEI 渲染处理器，在物品槽位上叠加显示 AE2 网络存量和可合成标识
- `mixin/nei/MixinGuiMEMonitorable.java` — 拦截 AE2 终端的 `postUpdate`，将网络库存数据写入缓存

**修改文件：**
- `ClientProxy.java` — 注册 `NetworkInventoryDrawHandler` 到 NEI
- `mixins.ae2_auto_pattern_upload.json` — 新增 `MixinGuiMEMonitorable`

**改动：**
1. **NEI 物品数量显示**：打开任意 AE2 终端（终端、样板终端、合成终端）时，NEI 物品栏叠加显示 AE2 网络中的物品数量
2. **可合成标识**：有合成配方的物品在右上角显示绿色 "+" 标识
3. **数量格式化**：自动格式化为 K/M/G 单位（如 1.2K、3.5M）
4. **仅终端打开时有效**：终端关闭后缓存自动失效，数据不再残留

---

## 2.6.0-fix4 - 清理：移除 Ctrl+编码自动上传

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/GuiUploadButtonHandler.java` — 移除 Ctrl+编码自动上传相关代码（onActionPerformedPost、onClientTick、ctrlEncodePending）

**改动：**
1. **移除 Ctrl+编码功能**：该功能不稳定，已移除
2. **保留其他功能**：NEI 配方池自动检测、记忆持久化、撤回修复均保留

---

## 2.6.0-fix3 - 修复：记忆持久化 + Ctrl编码改用事件方案

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/ClientState.java` — 新增 `rememberedProviders` 磁盘持久化（保存/加载到 `config/ae2_auto_pattern_upload/remembered_providers.json`）
- `client/event/GuiUploadButtonHandler.java` — Ctrl+编码自动上传改用 `ActionPerformedEvent.Post` + `TickEvent` 方案（移除 MixinGuiPatternTerm）
- `mixin/MixinGuiPatternTerm.java` — 已删除
- `mixins.ae2_auto_pattern_upload.json` — 移除 MixinGuiPatternTerm

**改动：**
1. **记忆持久化**：`rememberedProviders`（配方池→供应器名映射）保存到磁盘，重启游戏不丢失
2. **移除问题 Mixin**：`MixinGuiPatternTerm` 目标 AE2 类导致 `TileEntitySpecialRenderer` 加载崩溃，改用纯事件方案
3. **Ctrl+编码流程**：`ActionPerformedEvent.Post` 检测 Ctrl+编码 → 设标记 → `TickEvent` 等待输出槽出现样板 → 自动上传

---

## 2.6.0-fix2 - 优化：Shift+编码自动上传 + 删除 Shift 上传强制GUI

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `mixin/MixinGuiPatternTerm.java` — 新增：拦截编码按钮 Shift+点击，已记忆配方池时自动编码+上传
- `client/event/GuiUploadButtonHandler.java` — 删除 Shift+点击上传打开选择页面的功能，上传按钮始终直接上传
- `mixins.ae2_auto_pattern_upload.json` — 注册 MixinGuiPatternTerm

**改动：**
1. **Ctrl+编码自动上传**：当配方池已有记忆的供应器时，Ctrl+点击编码按钮 → 自动编码（放入输出槽）+ 自动上传，省去再点上传按钮
2. **删除 Shift 上传强制 GUI**：上传按钮不再检测 Shift 键，始终执行直接上传逻辑
3. **操作流程简化**：已记忆配方池的机器只需 Shift+编码即可完成全部操作

---

## 2.6.0-fix1 - 修复：撤回功能 + IInterfaceHost 支持 + 清除旧配方池

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `network/RecallPatternPacket.java` — 修复撤回时只匹配 `IInventory`，新增 `IInterfaceHost.getPatterns()` 匹配；撤回时清除样板上的 `apu:recipeMap` 标签
- `network/ProvidersListS2CPacket.java` — 添加上传成功日志
- `client/event/GuiUploadButtonHandler.java` — 添加撤回按钮点击日志

**改动：**
1. **修复 IInterfaceHost 撤回**：撤回时对 ME Interface 等 `IInterfaceHost` 类型的供应器，通过 `host.getPatterns()` 获取样板库存，不再要求 `machine instanceof IInventory`
2. **清除旧配方池标签**：撤回时清除样板上的 `apu:recipeMap` NBT 标签，避免重新编码后继承旧配方池导致搜索框显示错误
3. **添加调试日志**：撤回按钮点击、供应器搜索、样板匹配全流程日志

---

## 2.6.0 - 功能：NEI 配方池自动检测

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `mixin/nei/MixinRecipeHandlerRef.java` — 增强 NEI hook，从 GT NEI Handler 提取 recipeMap.unlocalizedName
- `mixin/nei/MixinDefaultOverlayHandler.java` — 同上，从 DefaultOverlayHandler 提取 recipeMap
- `client/ClientState.java` — 新增 pendingRecipeMap 字段，存储 NEI 捕获的配方池 ID
- `client/event/GuiUploadButtonHandler.java` — 上传时优先使用 NBT/NEI 配方池，写入样板 NBT `apu:recipeMap`
- `network/RequestProvidersListPacket.java` — 新增 directRecipeMap 字段，客户端直接发送配方池 ID 跳过服务端检测

**改动：**
1. **NEI 混入提取配方池**：从 `gregtech.nei.GTNEIDefaultHandler` 的 `recipeMap.unlocalizedName` 字段直接读取配方池 ID（如 `gt.recipe.compressor`），不再依赖输入输出匹配
2. **样板 NBT 持久化**：上传时自动将 recipeMap 写入样板 NBT `apu:recipeMap`，后续上传可直接读取
3. **客户端直接发送配方池**：跳过服务端 328 个配方池的反射检测，减少延迟和误判
4. **fallback 兼容**：未从 NEI 捕获时仍使用旧的输入输出检测逻辑

---

## 2.5.7 - 清理：移除默认映射模板

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `util/RecipeNameUtil.java` — `writeTemplate()` 改为空 JSON

**改动：**
1. **移除 40+ 条预设映射**：默认模板改为空 `{}`，不再预填中文映射
2. **只保留手动添加的映射**：用户通过 GUI 添加的映射（如 `gt.recipe.fluidextractor → 流体提取机`）正常保留
3. **清理方式**：删除 `config/ae2_auto_pattern_upload/recipe_names.json` 重启即可

---

## 2.5.6 - 修复：预填 GT ID + Shift+点击强制选择 + 撤回修复

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/GuiUploadButtonHandler.java` — Shift 检测 + 传递 forceGui 标志
- `network/RequestProvidersListPacket.java` — 新增 forceGui 字段
- `network/ProvidersListS2CPacket.java` — 新增 forceGui 字段 + 重构上传策略
- `common/RecipeMapNameConfig.java` — 新增 extractMachineName 方法
- `client/ClientState.java` — 新增 rememberedProviders 映射记忆

**改动：**
1. **预填 GT 配方池 ID**：搜索框直接填入完整 ID（如 `gt.recipe.compressor`），不再用中文映射
2. **Shift+点击上传**：按住 Shift 点击上传按钮 → 跳过自动上传，强制打开选择页面
3. **撤回修复**：自动上传成功后正确设置 `ClientState.lastProviderId`
4. **Provider 记忆机制**：用户选择 Provider 后记住「配方池 → Provider 名字」，下次自动上传

---

## 2.5.5 - 修复：自动上传方案重构 — 预填搜索 + 记忆 Provider

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/ClientState.java` — 新增 `rememberedProviders` Map 存储配方池→Provider名字映射
- `network/ProvidersListS2CPacket.java` — 重写自动上传逻辑：先查记忆，再开搜索界面
- `client/gui/GuiProviderSelect.java` — 选择 Provider 后保存配方池→名字映射

**改动：**
1. **修复自动匹配永远失败的根本原因**：
   - 旧逻辑：用配方池关键字（如"压缩机"）匹配 Provider 名字 → Provider 名字是"ME 样板供应器"，永远匹配不上
   - 新逻辑：不再用配方池关键字匹配 Provider 名字
2. **引入 Provider 记忆机制**：
   - `ClientState.rememberedProviders`：Map<配方池ID, Provider名字>
   - 用户在搜索界面选择 Provider 后，自动记住「配方池 → Provider 名字」
   - 下次检测到相同配方池时，用记住的名字精确匹配 Provider → 唯一匹配则自动上传
3. **上传策略**：
   - 策略1：只有 1 个有效 Provider → 直接上传
   - 策略2：查已记住的 Provider 名字 → 精确匹配 → 唯一匹配则自动上传
   - 策略3：打开搜索界面，预填配方池关键字（如"压缩机"），用户手动选择
4. **搜索界面行为**：
   - 打开时搜索框预填配方池关键字（如"压缩机"），帮助用户快速找到相关机器
   - 用户手动选择 Provider 后记住，下次自动上传

---

## 2.5.3 - 修复：映射加载 bug + 调试日志

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `util/RecipeNameUtil.java` — 修复 `loadMappings()` 创建模板后不加载映射的 bug
- `common/RecipeMapNameConfig.java` — 添加调试日志
- `network/ProvidersListS2CPacket.java` — 添加调试日志

**改动：**
1. **修复首次加载映射不生效的 bug**：
   - 根本原因：`RecipeNameUtil.loadMappings()` 发现配置文件不存在时调用 `writeTemplate()` 创建模板，然后 `return` — 没有重新读取刚创建的文件
   - 结果：`RAW_MAPPINGS` 和 `LOOKUP_MAPPINGS` 始终为空，所有映射查找都失败
   - 修复：`writeTemplate()` 后不再 `return`，继续执行文件读取逻辑
2. **添加调试日志**（`[APU]` 前缀）：
   - `RecipeMapNameConfig.resolveSearchKeyword`：输出输入、缓存大小、匹配结果
   - `ProvidersListS2CPacket.Handler`：输出 recipeMap、resolvedKeyword、匹配的 provider

---

## 2.5.2 - 修复：统一映射系统 + 修复自动上传/撤回/手动映射

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `common/RecipeMapNameConfig.java` — 完全重写：从 RecipeNameUtil 统一读取映射
- `util/RecipeNameUtil.java` — 模板加入 GT 默认映射 + 添加/删除时同步重载 RecipeMapNameConfig

**改动：**
1. **修复两套映射系统互不通信的问题**：
   - 根本原因：`RecipeNameUtil`（GUI 手动映射）和 `RecipeMapNameConfig`（自动上传）各自管理独立的配置文件，互不通信
   - 旧方案：GUI 手动添加的映射只存在 `RecipeNameUtil`，自动上传的 `RecipeMapNameConfig` 完全不知道
   - 新方案：`RecipeMapNameConfig` 不再自己管理配置文件，改为从 `RecipeNameUtil.getMappingsView()` 读取映射
   - 配置文件统一为 `config/ae2_auto_pattern_upload/recipe_names.json`
2. **添加/删除映射时同步重载**：
   - `RecipeNameUtil.addOrUpdateMapping()` 和 `removeMappingsByCnValue()` 执行后调用 `RecipeMapNameConfig.reload()`
   - 保证手动添加的映射立即对自动上传生效
3. **模板加入 GT 默认映射**：
   - `RecipeNameUtil.writeTemplate()` 从 "example.crafting" 改为 40+ 种 GT 配方池默认映射
   - 首次加载自动生成完整默认配置
4. **RecipeMapNameConfig 重写**：
   - 不再自己管理 `config/apu/recipe_type_names.json`
   - 改为从 `RecipeNameUtil.getMappingsView()` 加载映射到内存缓存
   - 支持 `"compressor"` 和 `"gt.recipe.compressor"` 两种 key 格式

---

## 2.5.1 - 修复：配方池检测输出验证逻辑错误

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `network/RequestProvidersListPacket.java` — 删除有 bug 的输出验证逻辑

**改动：**
1. **修复配方池检测永远返回 null 的问题**：
   - 根本原因：`findRecipeQuery().items(inputs).find()` 找到的配方，其输出可能和样板输出不同（同输入多输出的配方，如编程电路不同导致输出不同）
   - 旧代码：用矿辞匹配验证输出 → 始终失败 → 返回 null → 无法匹配 Provider
   - 新代码：只检查输入匹配 → 成功返回配方池名字 → 触发配置文件关键字映射
2. **不影响配置文件方案**：删除输出验证让 `RecipeMapNameConfig` 的配置文件映射能正常工作

---

## 2.5.0 - 自动上传：配方池检测 + 配置文件关键字映射 + 搜索预填

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `network/RequestProvidersListPacket.java` — 服务端配方池检测 + 矿辞典输出匹配
- `network/ProvidersListS2CPacket.java` — 配方池关键字匹配改为使用 `RecipeMapNameConfig`
- `client/ClientState.java` — lastRecipeMap 字段
- `client/event/GuiUploadButtonHandler.java` — 读取样板内容发送给服务端
- `client/gui/GuiProviderSelect.java` — 搜索框预填 + 唯一匹配自动上传
- `common/RecipeMapNameConfig.java` — 新增：配置文件加载 + 配方池→关键字映射
- `resources/apu/recipe_type_names.json` — 新增：默认配置模板

**改动：**
1. **服务端配方池检测**（`RequestProvidersListPacket.Handler.detectRecipeMap()`）：
   - 客户端读取样板输入/输出 → 发送到服务端
   - 服务端通过反射调用 `RecipeMap.ALL_RECIPE_MAPS` 遍历 GT 配方池
   - 用 `findRecipeQuery().items(inputs).find()` 查找配方
   - 矿辞典输出匹配：用 `OreDictionary.getOreIDs()` 对比 AE2 样板输出与 GT 配方输出
2. **配置文件关键字映射**（`RecipeMapNameConfig`）：
   - 参考 ExtendedAE_Plus 方案，使用 `config/apu/recipe_type_names.json` 配置文件
   - 将硬编码的 30+ 种配方池映射改为可配置的 JSON 文件
   - 格式：`"assembler": "组装机"`, `"macerator": "粉碎机"`
   - 支持别名映射（无冒号的 key）和完整 ID 映射（带冒号的 key）
   - 首次加载自动创建默认配置
   - 支持单机和服务器：配置文件在各自的 `config/apu/` 目录
3. **搜索预填 + 唯一匹配自动上传**：
   - 打开 Provider 选择界面时，自动用 `RecipeMapNameConfig.resolveSearchKeyword()` 查找中文关键字
   - 如果过滤后只剩 1 个有效 Provider → 直接自动上传，不弹界面
   - 如果有多个匹配 → 弹出选择界面，搜索框已预填关键字
   - 示例：编码组装机配方 → 检测到 `assembler` → 查配置得"组装机" → 匹配"组装机" Provider → 自动上传
4. **网络协议变更**：
   - `RequestProvidersListPacket`：新增 `ItemStack[] recipeInputs` 和 `ItemStack[] recipeOutputs` 字段
   - `ProvidersListS2CPacket`：新增 `String recipeMap` 字段

---

## 2.4.1-fix6 - 多方块检测：修复 MTEMultiBlockBase 类名路径错误

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
1. **修复 `isGTMultiBlock()` 类名路径**（根本原因）：
   - 旧代码：`Class.forName("gregtech.api.metatileentity.MTEMultiBlockBase")` — 包路径缺少 `implementations`，永远抛出 `ClassNotFoundException`，导致所有 GT 多方块机器都被识别为单方块
   - 新代码：`Class.forName("gregtech.api.metatileentity.implementations.MTEMultiBlockBase")` — 正确路径
   - 影响：修复前所有多方块机器都走 `getGTSingleBlockName()` 分支，因此只能获取主机名（如"大型蒸汽洗矿机"），无法获取运行模式名（如"洗矿机"）

---

## 2.4.1-fix5 - 多方块模式名：修复接口 default 方法反射调用失败

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
1. **多方块模式名反射修复**（`getGTMultiBlockName()` + 新增 `findMethodInHierarchy()`）：
   - 旧逻辑：`obj.getClass().getMethod("getMachineModeKey")` — 只搜索类和父类，不搜索接口 default 方法，导致 `NoSuchMethodException`
   - 根因：`getMachineModeName()` 和 `getMachineModeKey()` 都是接口 `IControllerWithOptionalFeatures` 的 default 方法，Java 8 的 `Class.getMethod()` 无法找到接口 default 方法
   - 新逻辑：新增 `findMethodInHierarchy()` 方法，BFS 遍历整个继承层次（类→父类→接口→父接口），用 `getDeclaredMethod()` 在每一层查找
   - 查找顺序：先找 `getMachineModeKey()` → 翻译 key；找不到则找 `getMachineModeName()`（某些子类直接 override 返回已翻译名）
2. **移除旧 `getGTStringMethod()` 的多方块调用**：改用新的层次结构查找方法

---

## 2.4.1-fix3 - 石英切割刀：GT前缀修正 + 多方块模式名翻译 + GuiOpenEvent拦截重命名界面

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
1. **单方块前缀修正**（`getGTSingleBlockName()`）：
   - 修正前缀列表：`"基础|进阶|高级|精密|优化|改良|增强"` → `"基础|进阶|精英|史诗|终极"`
   - 正则：`cleaned.replaceAll("^(基础|进阶|精英|史诗|终极)", "")`
2. **重命名界面拦截方案重写**（`onGuiOpen()` 新增方法）：
   - 旧方案：`PlayerInteractEvent.setCanceled(true)` — 无法阻止 AE2 的 `ToolQuartzCuttingKnife.onItemUse()` 在服务端通过 `Platform.openGUI()` 打开 `GuiRenamer`
   - 新方案：新增 `@SubscribeEvent` 监听 `net.minecraftforge.client.event.GuiOpenEvent`（Forge 客户端事件）
   - 拦截逻辑：检测 `event.gui` 类名是否为 `appeng.client.gui.implementations.GuiRenamer` → 检查玩家是否潜行 + 持有石英切割刀 → `event.setCanceled(true)` 阻止 GUI 打开
   - 新增 import：`net.minecraft.client.Minecraft`（获取客户端玩家）
   - 移除旧逻辑：`onPlayerInteract()` 中的 `event.setCanceled(true)` 已删除
3. **Javadoc 更新**：
   - `getGTSingleBlockName()` 注释前缀列表同步修正

---

## 2.4.1-fix2 - 石英切割刀：多方块名称+单方块简化+取消重命名界面

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
1. **多方块名称修正**：格式改为"运行模式名-主机名"（如"简易洗矿池-大型蒸汽洗矿机"）
   - 通过 `getMachineModeName()` 获取运行模式名（翻译 `GT5U.` 开头的 key）
   - 通过 `gt.blockmachines.<mName>.name` 获取主机名
2. **单方块名称简化**：去掉前缀和罗马数字，仅保留配方类型名
   - 去掉前缀："基础""进阶""高级""精密""优化""改良""增强"
   - 去掉尾部罗马数字：I~XII
   - 去掉尾部阿拉伯数字
   - 如"基础冲压机床 III" → "冲压机床"
3. **取消重命名界面**：`event.setCanceled(true)` + `@SubscribeEvent(priority = EventPriority.HIGHEST)` 确保最高优先级取消

---

## 2.4.1-fix1 - 石英切割刀：GT名称本地化修复

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
- `getCustomName()` 返回未本地化 key → 改用 `mName` 构造 `gt.blockmachines.<mName>.name` → `StatCollector.translateToLocal()` 翻译
- 单方块/多方块区分：`MTEMultiBlockBase.isInstance(mte)`
- 剪贴板复制 + 聊天 `(copied)` 标识

---

## 2.4.1 - 石英切割刀：剪贴板 + 单方块简化 + GT 名称修复

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `client/event/KnifeNameCopyHandler.java`

**改动：**
1. **剪贴板复制**：Shift+右键后名称同时写入系统剪贴板，聊天提示末尾加 `(copied)`
2. **GT 名称修复**：
   - `getCustomName()` 对 GT 单方块返回未本地化的 key（如 `extra_start_gt.recipe.wiremill...`）
   - 改为通过 MTE 的 `mName` 字段构造本地化 key `gt.blockmachines.<mName>.name`，再用 `StatCollector.translateToLocal()` 翻译
   - fallback：`IInventory.getInventoryName()` → `blockLocalizedName`
3. **单方块/多方块区分**：通过 `MTEMultiBlockBase.isInstance(mte)` 判断
   - 单方块：仅返回简短中文名（如"线材轧机"）
   - 多方块：附加配方映射名和模式名（如"蒸汽制造商 [配方名 - 模式名]"）
4. **修复重复 import**：移除 `PlayerInteractEvent` 重复导入

---

## 2.4.0 - 石英切割刀名称复制

> 作者：wztwzt（GTNH 适配）| 更新时间：2026-08-06

**新增文件：**
- `client/event/KnifeNameCopyHandler.java` — Forge `PlayerInteractEvent` 事件监听器

**修改文件：**
- `ClientProxy.java` — 添加 `KnifeNameCopyHandler.register()` 注册

**功能：**
- 持有石英切割刀（赛特斯/下界），Shift + 右键方块/AE部件 → 名称写入刀的显示名 + 聊天提示
- 取消默认右键行为（不打开重命名界面）

**目标检测：**
- AE2 线缆部件：`TileCableBus.getPart(side)` → `ICustomNameObject.getCustomName()`
- AE2 方块：`AEBaseTile` → `ICustomNameObject.getCustomName()`
- GT 机器：`ICustomNameObject.getCustomName()` + 反射获取 `getRecipeMap().unlocalizedName` + `getMachineModeName()`
- 其他方块：`block.getLocalizedName()`

**刀检测：** `className.contains("QuartzCuttingKnife")`，覆盖 `ToolCertusQuartzCuttingKnife` 和 `ToolNetherQuartzCuttingKnife`

**Bug 修复：** 事件必须注册到 `MinecraftForge.EVENT_BUS`（Forge 事件），而非 `FMLCommonHandler.instance().bus()`（FML 事件）

---

## 2.3.0 - 多人模式修复 + 名字匹配

> 作者：wztwzt | 更新时间：2026-08-06

**核心问题：** 2.2.x 的 `lastUploadedProviderId` 是 `UploadPatternPacket.Handler` 的 static 字段，单人模式共享 JVM 可用，多人模式下客户端和服务端是不同 JVM，客户端读到的值始终为 0。

**新增文件：**
- `client/ClientState.java` — 客户端状态持有类，存储 `lastProviderName`（String）和 `lastProviderId`（long）

**修改文件：**
- `ProvidersListS2CPacket.java` — 客户端 Handler 重写自动上传策略：
  1. 策略1：只有一个有空槽的 Provider → 直接上传
  2. 策略2：`ClientState.lastProviderName` 匹配 → 找同名且有空槽的 Provider → 唯一匹配则上传
  3. 都不满足 → 打开选择 GUI
- `GuiProviderSelect.java` — `handleSelect()` 中用户手动选择后调用 `ClientState.set(name, id)` 记住名字和 ID
- `UploadPatternPacket.java` — 移除 `Handler.lastUploadedProviderId` static 字段（服务端不再维护状态）
- `RecallPatternPacket.java` — 撤回成功后调用 `ClientState.clear()` 清除客户端记录
- `GuiUploadButtonHandler.java` — 撤回按钮读取 `ClientState.lastProviderId`（原 `UploadPatternPacket.Handler.lastUploadedProviderId`）

---

## 2.2.1 - 撤回清除记录

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `RecallPatternPacket.java` — 撤回成功后设置 `UploadPatternPacket.Handler.lastUploadedProviderId = 0`

**目的：** 误上传到错误 Provider 后，撤回 → 清除记录 → 下次弹选择框重新选

---

## 2.2.0 - 智能自动上传

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `ProvidersListS2CPacket.java` — 客户端 Handler 新增策略2：检查 `lastUploadedProviderId` 是否在有效列表中，是则直接上传

**逻辑：** `validIds.size() == 1` → 直接上传；`validIds.contains(lastId)` → 直接上传；否则弹 GUI

---

## 2.1.0 - 自动上传 + 撤回按钮

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `network/RecallPatternPacket.java` — 撤回协议，服务端从 Provider 反向扫描移除最后一个编码样板，放回输出槽
- `network/ProvidersListS2CPacket.java` — 服务端→客户端 Provider 列表推送协议

**修改文件：**
- `client/event/GuiUploadButtonHandler.java` — 新增撤回按钮 `BUTTON_RECALL_ID=998`（`←`），编码按钮右侧
- `network/RequestProvidersListPacket.java` — 服务端扫描网格所有 `ICraftingProvider`，使用 `ICustomNameObject` 解析名称
- `network/ModNetwork.java` — 注册 `ProvidersListS2CPacket`（discriminator 1, CLIENT）和 `RecallPatternPacket`（discriminator 3, SERVER）

**撤回逻辑：**
1. 服务端检查输出槽是否为空（只能在空时撤回）
2. 获取 Provider 的 `IInventory`，从后往前扫描找到最后一个编码样板
3. 移除并放入输出槽

**Bug 修复：** MC 1.7.10 的 `Container.inventorySlots` 不支持 for-each（编译器报错），移除客户端对该字段的遍历

---

## 2.0.1 - 依赖对齐 + 兼容性修复

> 作者：wztwzt | 更新时间：2026-08-06

**修改文件：**
- `dependencies.gradle` — AE2 从 `rv3-beta-691` 升级到 `rv3-beta-977-GTNH`，ae2fc 从 `1.4.115` 升级到 `1.5.88-gtnh`
- `network/RequestProvidersListPacket.java` — `resolveTerminal()` 从 `(PartPatternTerm) term.getPatternTerminal()` 改为 `(IActionHost) term.getPatternTerminal()`（修复 `NoSuchMethodError`）
- `network/UploadPatternPacket.java` — 同上修复 `resolveTerminal()`

**移除文件/引用：**
- 移除所有 `GuiFluidPatternTerminal`、`GuiFluidPatternTerminalEx`、`ContainerFluidPatternTerminal`、`ContainerFluidPatternTerminalEx`、`FCContainerEncodeTerminal`、`IItemPatternTerminal` 引用（修复 `NoClassDefFoundError`）

**新增功能：**
- `isSupportedPattern()` 新增 `encodedUltimatePattern()` 检查（支持终极编码样板）
- `resolveProviderName()` 优先检查 `ICustomNameObject`（GregTech 适配器兼容）

---

## 2.0.0 - 网络协议重构

> 作者：wztwzt | 更新时间：2026-08-06

**新增文件：**
- `network/ProvidersListS2CPacket.java` — 服务端扫描网格 Provider，发送 id/name/emptySlots 列表给客户端
- `client/gui/GuiProviderSelect.java` — 带搜索/翻页/映射功能的 Provider 选择 GUI
- `util/RecipeNameUtil.java` — 配方名到 Provider 名的映射工具

**修改文件：**
- `network/ModNetwork.java` — 注册 `ProvidersListS2CPacket`（discriminator 1）

**移除：** 所有 debug `System.out.println` 输出

---

## 1.2.4 - 兼容性修复

> 作者：wztwzt | 更新时间：2026-08-06

- 修复与 GTNH 2.9.0 的兼容性问题

---

## 1.2.0 - 初始版本

> 作者：wztwzt | 更新时间：2026-08-06

- 基础 AE2 样板上传功能
- 支持标准编码样板和 ae2fc 流体编码样板

---

## 附：F 功能规划记录（样板 + 接口双页面二合一终端）

> 状态：🕐 规划中 | 作者：wztwzt | 记录时间：2026-08-15

### 功能构想

将**样板编码**与**接口（Interface）管理**合并到同一个终端 GUI，通过页面切换（"样板页" / "接口页"）在一个窗口内完成两类操作，避免玩家在样板终端与接口终端之间反复切换。

### 历史决策

- **3.0.0 调研结论**：AE2 rv3-beta-977-GTNH 已原生实现接口终端（PartInterfaceTerminal / WirelessInterfaceTerminalGuiObject），ae2fc 1.5.88 自带 ItemWirelessInterfaceTerminal 可直接打开原生接口终端——该能力已被原生覆盖。当时放弃约 6000 行完整移植，改为 NEI 配方界面 AE 角标等增量增强。
- **本记录目的**：内容单独保留，作为后续重新发布 F 功能的任务起点。重新开发前需先与使用者对齐页面布局、"写样板 → 自动填机器名"联动、与原生接口终端的差异定位等需求。

### 重新开发时需确认的点

1. 双页面切换的交互方式（Tab / 侧边栏 / 按钮）
2. 样板页与接口页是否需要同步显示同一网络的数据
3. 与 AE2 977 原生接口终端的功能差异（避免重复造轮子）
4. 无线版（复用无线终端）是否纳入

---

## 附：开发调研记录（2026-08-16）

> 作者：wztwzt | 记录时间：2026-08-16

本节记录后续功能（智能倍增、F 模块）的前置调研结论，供后续会话直接接手。

### 1. 智能倍增（Smart Doubling）调研与实现计划

> 状态：✅ **已实现（3.2.0，2026-08-16）**，下文为开发前的调研与计划存档。

#### 需求

ME 接口的样板在 GT 机器上每次只推 1 轮材料，材料补料慢、不便于自动化。目标：让接口**一次性推送 N 轮**材料，机器连做 N 次（N 可配置，默认上限 64）。

#### 调研结论

- AE2 rv3-beta-977-GTNH 与 ae2fc **均没有**"扩展样板供应器（Extended Pattern Provider）"方块；rv3 的样板供应角色 = **ME 接口（`DualityInterface`，1896 行）** + ae2fc 流体接口
- GTNH 已内置 `PatternMultiplierHelper` + `doublePatterns`/`PatternOptimization`（位运算直接改样板 NBT，把 1 轮材料的样板"加倍"）——方向相反，用户**明确不要**这种改样板方案
- 合成执行流（`CraftingCPUCluster.executeCrafting`，每 tick 调用）：
  1. 对每个样板任务（`TaskProgress`，`value` = 剩余轮数）从 CPU 私有 `MECraftingInventory` 提取 **1 轮**构建 `MEInventoryCrafting`（槽位无上限——`setInventorySlotContents` 直接存 `IAEStack`，已验证可放 N× 材料）
  2. 经 `CraftingGridCache.getMediums(details)` 找所有非忙 `ICraftingMedium`
  3. 调 `pushPattern(details, table)`；成功 → `value--`，产出累加进 `waitingFor`（`IItemList`）；非阻塞模式下同 tick 继续循环
- **关键结论：多轮推送必须由 CPU 协同**——剩余轮数与预留材料都归 CPU 私有；供应器自己从网络存储自取会**超产**。且 `pushPattern` 返回 boolean，无法告知 CPU 实际消耗了几轮
- `DualityInterface.pushPattern` 把表推到相邻机器（`InventoryAdaptor.addStack`，放不下的部分缓冲进发送列表，次 tick 再推）

#### 用户决策

1. 载体 = **现有 ME 接口加复选框**（不新增方块）
2. F 模块**维持搁置**，先做智能倍增
3. 默认合并上限 **64 轮**

#### 实现计划（版本 3.2.0）

| 文件 | 内容 |
|---|---|
| `api/ISmartDoublingMedium.java`（新） | 接口：`boolean supportsSmartDoubling()` / `int getMaxMultiplier()` |
| `mixin/ae/MixinDualityInterface.java`（新） | implements 上述接口；`writeToNBT`/`readFromNBT` 注入持久化布尔 `smartDoubling`（ConfigManager 按 `Settings` 枚举建字段，**不能加新枚举**，必须走 data 复合标签）；`getMaxMultiplier`：阻塞模式或机器是 GT `ICraftingMachine.acceptsPlans()` 时返回 1 |
| `mixin/ae/MixinCraftingCPUCluster.java` | `@Overwrite executeCrafting`（逐行移植约 250 行）：建表处按 N× 提取材料；成功分支 `value -= effectiveN`、`waitingFor` 累加 N× 产出、能耗 N×sum；`N = min(剩余轮数, 机器容量估计, 配置上限 64, 防溢出)`，`effectiveN = min(各输入槽 提取量/单轮量)` |
| `mixin/ae/MixinGuiInterface.java`（新） | 复刻 `patternOptimization` 的 `GuiToggleButton` 复选框（smartDoubling 按钮） |
| `mixin/ae/MixinContainerInterface.java`（新） | 新增 `@GuiSync(n)` 布尔字段（`standardDetectAndSendChanges` 反射同步，mixin 加字段可行） |
| `network/SmartDoublingTogglePacket.java`（新） | C2S，归队 `ServerThreadUtil.addScheduledTask`（风险 #5 合规） |
| `Config.java` | `smartDoublingMaxRounds = 64` |
| `lang/zh_CN.lang` + `lang/en_US.lang` | `gui.ae2_qof.smart_doubling` 等 |
| `mixins.ae2_qof.json` | 注册新 mixin |

#### 风险（写代码时对照风险表）

- `@Overwrite executeCrafting` 是关键路径，必须全量回归原版合成行为
- GT `ICraftingMachine.acceptsPlans()` 机器与阻塞模式必须 N=1（否则机器可能吞掉多轮材料或行为异常）
- 流体量按 N× 放大，注意 long 溢出（`waitingFor` 累加 + 消耗扣减）

### 2. F 模块（样板 + 接口双页面二合一终端）调研更新

- 复核确认：AE2 977 原生已有**完整接口终端**（`GuiInterfaceTerminal` 1958 行自定义动态槽 GUI、`ContainerInterfaceTerminal`、`PartInterfaceTerminal`、无线版 `ItemWirelessInterfaceTerminal`/`WirelessInterfaceTerminalGuiObject`），以及 `PartPatternTerminal`/`PartPatternTerminalEx`/`GuiPatternTerm`/`GuiPatternTermEx` 样板终端
- 结论：**维持搁置**（原生已覆盖接口管理；F 的增量价值仅剩"单窗口双页 + 写样板自动填机器名"联动）。重开条件与 4 个确认点见上文「F 功能规划记录」
- 若后续重开，推荐直接扩展 AE2 原生 `GuiInterfaceTerminal`（注入其类添加页面切换 + 嵌入样板编码区），而非 6000 行移植

### 3. 流体显示 bug（3.1.2 修复）备忘

- 根因、识别方式修正、回归点见上文 3.1.2 条目；风险表 #19

---

## 附：后续工作入口（checklist）

> 供下一次会话直接接手，按序执行。

1. **智能倍增（3.2.0）**：✅ **已完成并发布**（`@Overwrite executeCrafting` 逐行移植 + 接口复选框 + 配置上限）。回归要点见风险表 #20/#21 与 3.2.0 条目
2. **F 模块**：维持搁置；若重开，先与使用者对齐上文 4 个确认点
3. **统一配置 + 热加载（3.3.0）**：✅ **已完成并发布**（`config/ae2_qof/settings.json` + `/ae2qof` OP 命令 + 旧 cfg 迁移）。改动面见 3.3.0 条目与风险表 #22
4. **mixin 冲突修复（3.3.1）**：✅ **已完成并发布**（`@Overwrite executeCrafting` → `@Inject(HEAD)+cancel` + 智能倍增任务预扫描）。见 3.3.1 条目与风险表 #23
5. **GTNotLeisure 兼容（3.3.2）**：✅ **已完成并发布**（`@GuiSync(19)`→`@GuiSync(30)` 修复同步 id 冲突崩溃 + `MixinGuiSuperInterface` 超级接口 GUI 复选框；PH 样板合成器按 `acceptsPlans` 单轮处理）。见 3.3.2 条目与风险表 #24
