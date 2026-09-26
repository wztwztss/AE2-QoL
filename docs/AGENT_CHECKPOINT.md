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
|工作分支|master；本轮起点 `fa612f4`（fix54 部署与推送收尾），工作树干净、与 origin/master 同步|
|启动时间|2026-09-26（Asia/Shanghai，新功能轮：编程样板输入总成 MK.III）|
|本次会话目标|**新增「编程样板输入总成 MK.III」**：ProgrammableHatches「编程样板输入总成」（MTE 22069）的扩容克隆版，样板槽 36 → **144**，样板窗改成 9 列 × 9 可见行的可滚动网格，只在装了 PH 时存在。严格按用户协议推进：先反复提问确认需求（7 项产品决策全部由用户拍板）→ 只读取证（PH 源码 + 实例 jar 字节码 + AE2/GT/MUI2 三层 API）→ 用户说「确认方案，开始修改」后才动代码。**3.20.0 首次实测失败**（可选依赖守卫把 PH 的 modid 误写成包名前缀 `proghatches`，物品从未注册且无日志），已定位并修复为 **3.20.1**（真实 modid + 关键类判据 + 三条分支日志），产物 `build/libs/AE2-QoL-3.20.1.jar`（SHA256 `D0F00177…`）**已部署到 b3 实例**（mods 内仅一份）。**游戏内 9 项验收待用户配合；推送尚未进行（验收通过后再推）。**|
|上一轮（历史）|工具：DeepSeek Harness｜模型：DeepSeek-V4.1-Flash：fix50/51/52+54 三问题定位与修复，正式版 `3.19.0-fix54` 已部署到 b3 实例并推送到 `origin/master`。|

---

## 二、项目总目标

GTNH 2.9.0-beta-3（Minecraft 1.7.10 Forge + Java 17/25）环境下的 AE2 附属功能模组 **AE2 QoL**（modId `ae2_qof`，版本 `3.19.0-fix49`）的交付与质量整改：在兼容原生 AE2（本次已核对的 rv3-beta-1050-GTNH）与格雷科技本体（5.09.54.133）/GTNL/PH 机制的前提下，完成 F1~F22 全部功能的质量检测、缺陷定位与修复，最终产出一个可稳定运行于单机与专用服的发布版本（含合并终端三形态、NEI 样板自动上传、合成完成通知、智能倍增、自适应电网、库存检测覆盖板等）。当前阶段以「先审计、再修复、逐条提交」为推进方式。

---

## 三、全局已完成清单

> 按完成时间倒序排列，均标注产出文件路径。历史结论保留原貌，不等于本版验证结果。

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
  6. **3.20.0 实测失败 → 3.20.1 修正**（用户报「没找到这个物品」）：3.20.0 里可选依赖守卫把 PH 的 **modid**
     误写成它的**包名前缀** `proghatches`（真实值是 `programmablehatches`），守卫恒假 ⇒ 物品从未注册且**无任何日志**。
     证据：日志有 `[AE2QoL] GuideNH guide registered`（init 确实跑过）、无「已注册」也无「注册失败」（卡在守卫第一句）、
     `mixin/programmablehatches: Mixing ph.MixinPatternDualInputHatchAccess ... into ...PatternDualInputHatch`（**mixin 应用成功**，
     排除 mixin 问题）。修复：真实 modid + 「关键类可解析」第二道判据 + 三条分支各打一行日志。
     产物 `build/libs/AE2-QoL-3.20.1.jar`（SHA256 `D0F00177…`）已部署，有缺陷的 3.20.0 改名 `-modid-bug` 入备份。
     教训已写入 skill 第 18/19 条（可选依赖只能取 `@Mod`/`mcmod.info` 的真实 modid；跳过分支必须留日志）。
  7. **待办**：游戏内 9 项验收（清单见 `CHANGELOG.md` 记录 (22)/(23) 第六点）。启动后先看日志里出现的是
     「已注册：id=32108，样板槽=144」还是「未检测到 ProgrammableHatches」还是「注册失败 + 堆栈」，即可一步定性。
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
