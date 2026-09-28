# 吞并可行性审计：Applied Energistics: Wireless Nexus 1.0.2（只读，2026-09-28）

> **状态**：本文**只有审计与记录，没有改动任何代码/资源**（用户 2026-09-28 决定：「先只记录事实，暂不动代码」）。
> **一句话结论**：**版权 = LGPL-3.0**（不是 MIT）；**技术上可以完整吞并**（39 个类 / 2,693 行，编译期与运行期依赖都齐备），
> 但**必须履行 LGPL-3.0 §4 的合规义务**，并且要先解决 **4 个硬坑**（同名 modid 双注册、存档契约原样保留、
> late mixin 注册面、`registerBlock` 两参重载的域问题）。

---

## 一、审计对象与证据来源

| 项 | 值 / 证据 |
|---|---|
| 参考源码 | `E:\wzt\MC\modcreater\reference_src_290b3\Applied-Energistics-Wireless-Nexus-1.0.2` |
| 上游仓库 | `github.com/DancingSnow0517/Applied-Energistics-Wireless-Nexus`；其 `master/LICENSE` 实测 HTTP 200（LGPL-3.0） |
| 实例里分发的那份 | `mods\【私货】ae_wireless_nexus-1.0.2.jar`，**114,650 B**，SHA256 `DD3E1AB01831AC95034413656F7043B2ACC76988B4DC3C4AEFE426587F450AC6` |
| 本仓库已有的副本 | `libs/ae_wireless_nexus-1.0.2.jar`，SHA256 **与实例那份完全相同**（逐字节一致）⇒ 之前已有会话把它放进了编译基线 |
| 私货 jar 是否被 GTNH 改过 | **没有**：类清单逐项比对（jar 内 `cn/dancingsnow/*` 顶层类 40 个 vs 源码 39 个），差异只有构建期生成的 `Tags` |
| 作者 / 标识 | `authorList: ["DancingSnow"]`（同 `AE2InfinityCell` 作者）、modid `ae_wireless_nexus`、包 `cn.dancingsnow.ae_wireless_nexus`、版本 1.0.2 |

---

## 二、版权：**GNU LGPL v3（LGPL-3.0，2007-06-29 全文）**

| 证据 | 内容 |
|---|---|
| 参考树根 `LICENSE`（7,652 B） | 首行 `GNU LESSER GENERAL PUBLIC LICENSE / Version 3, 29 June 2007`；SHA256 `E3A994D82E644B03A792A930F574002658412F62407F5FEE083F2555C5F23118` |
| 上游同名文件 | `https://raw.githubusercontent.com/DancingSnow0517/Applied-Energistics-Wireless-Nexus/master/LICENSE` → **HTTP 200，同为 LGPL-3.0 全文** |
| `src/main/resources/LICENSE`（1,068 B，且被打进 jar 根） | **GTNH 模板的未填写 MIT 占位**：`MIT License / Copyright (c) [year] [fullname]`。**没有版权人、没有年份 ⇒ 不构成有效授权**，不能据此当 MIT 用 |
| 上游 `LICENSE-template` | **HTTP 404，不存在** —— 对比 `AE2InfinityCell`（同作者）当时**有** MIT 模板，所以那次判 MIT 成立；**Wireless Nexus 不成立** |
| 逐文件头声明 | `grep -i 'copyright\|licensed under\|SPDX\|LGPL\|MIT License'` 扫全部 `.java`：**一条都没有**（无逐文件声明可推翻仓库根 LICENSE） |

**判定**：本项目应按 **LGPL-3.0** 对待（置信度 95%）。**不存在**把它当 MIT 搬的依据。

### 2.1 LGPL-3.0 下"吞并进我们 MIT 的 jar"意味着什么
把它的代码并进本模组的 jar = LGPL-3.0 所称的 **Combined Work（§4）**。允许这样做，但必须做到：

1. **显著声明**（§4a）：写明"本作品使用了 Applied Energistics: Wireless Nexus（作者 DancingSnow），该部分及其使用受 LGPL-3.0 覆盖"；
2. **随附许可全文**（§4b）：**LGPL-3.0 与 GPL-3.0 两份**（LGPL-3.0 正文明确引用 GPL-3.0）；
3. **运行期版权声明**（§4c）：若程序运行中显示版权声明，需含该部分的版权声明；
4. **提供对应源码**（§4d0）：被吞并部分的 **Minimal Corresponding Source**（含我们的修改）**继续以 LGPL 授权**并公开可得，且保持"用户可替换/重新链接该部分"的形式；
5. **不得限制**对该部分的修改与反向工程（不能写"禁止反编译/禁止修改"，**不能混淆**该部分）。

⇒ 我们自己的代码可以继续 MIT，但**被吞并的 Nexus 文件不能改写成 MIT**；jar 实际是 **"MIT + LGPL-3.0 混合"**。
只要**分发**（给朋友、进整合包、公开）就触发上述义务；用户已选"以后可能公开"⇒ **按最保守口径准备合规材料**。

---

## 三、技术盘点

### 3.1 规模（吞并成本）
| 项 | 数量 |
|---|---|
| Java 源码 | **39 个类 / 2,693 行**（整树 44 个 `.java` 含构建/脚手架） |
| 资源 | 13 个：`assets/ae_wireless_nexus/lang/{en_US,zh_CN}.lang`（**en 30 键 / zh 20 键**）、7 张贴图、2 个 mixin json、`LICENSE`、`mcmod.info` |
| GuideNH 页 | **10 个**（`index / wireless_controller / wireless_connector / wireless_kit / gregtech_hatches` × 中英） |
| 入口 | `AEWirelessNexus`（`@Mod`）+ `CommonProxy` / `ClientProxy` + `Config` |

### 3.2 注册与存档契约（**必须逐字保持一致**，否则旧存档报废）
| 契约 | 值（来源） |
|---|---|
| modid | `ae_wireless_nexus`（`AEWirelessNexus.MODID`） |
| `@Mod.dependencies` | `required-after:appliedenergistics2;required-after:backhand`（**backhand 是硬依赖**） |
| 方块 | `GameRegistry.registerBlock(WIRELESS_CONTROLLER, AEBaseItemBlock.class, "wireless_controller")`、`registerBlock(WIRELESS_CONNECTOR, "wireless_connector")`（`registry/ModBlocks.java`） |
| TileEntity | `ae_wireless_nexus.wireless_controller`、`ae_wireless_nexus.wireless_connector` |
| 世界存档数据 | `WorldSavedData` 键 = **`ae_wireless_nexus.networks`**，内部列表键 `Networks`（`network/WirelessNetworkSavedData.java`） |
| 创造标签 | `CreativeTabs("ae_wireless_nexus")`；GUI 主题 `gregtech:standard` |
| lang 键 | `tile.wireless_controller.name`、`tile.wireless_connector.name`、`itemGroup.ae_wireless_nexus`、`gui.ae_wireless_nexus.*` |

> ⚠️ **坑位（本项目已知坑 #20）**：`ModBlocks` 用了 **两参** `registerBlock(block, name)`，
> 它的域来自 **FML 当前活跃 mod 容器**。吞并后只要仍由它自己的 `@Mod` 类在自己的 `preInit` 里注册，
> 容器就是 `ae_wireless_nexus`，名字不变；但**一旦有人改成从我们的初始化路径调用，方块名会跑到别的域**
> ⇒ 旧存档里的方块全部消失。**实施时必须核对注册时的活跃容器，并加一条断言/日志。**

### 3.3 依赖（运行期全部就位，编译期需补一样）
| 依赖 | 状态 |
|---|---|
| `backhand`（`xonin.backhand.api.core.BackhandUtils`） | 实例有 `backhand-1.8.14.jar`；`javap` 实证 **`getOffhandItem(EntityPlayer)` 存在** ✔ |
| GT5Unofficial / NHCoreMod / AE2 | 同一整合包，编译基线已有（`libs/gregtech-5.09.54.133.jar` 等） ✔ |
| MUI2（`com.cleanroommc.modularui`） | 用 `DynamicSyncedWidget` + `GenericListSyncHandler` + `ListWidget` 构建 `WirelessSelectionPanel`（就是本项目已复用过的那套范式） ✔ |
| **GTNHMixins**（`com.gtnewhorizon.gtnhmixins.*`） | **运行期由 `+unimixins-all-1.7.10-0.3.1.jar` 提供**（实测 `ILateMixinLoader.class` 命中）✔；**编译期本仓库 `libs/` 与 Gradle 缓存里都没有** ⇒ 需要补（见 3.4） |
| `compileOnly` 的 mekanism / rotarycraft | 仅编译期可选集成，按本项目做法可留可去 |

### 3.4 mixin 与注册面（本项目此前没有 late mixin）
Nexus 用 **GTNHMixins 的 late 阶段**注册 8 个 mixin：

| mixin 目标 | 注入点 | 与我们的关系 |
|---|---|---|
| `BaseMetaTileEntity` | `writeToNBT` RETURN、`readFromNBT` RETURN、`updateEntityProfiled` RETURN、`invalidate` HEAD、`onUnload` HEAD、`onRightclick` HEAD/RETURN（cancellable） | **同目标类**：我们有 `gt.MixinBaseMetaTileEntityIdMigration`（存档 ID 迁移，也走 `readFromNBT` 路径）⇒ **必须逐方法比对**，两者都是 RETURN 注入才有共存空间 |
| `CommonMetaTileEntity` | `buildUI` RETURN（cancellable） | **同目标类**：我们有 `gt.MixinCommonBaseMetaTileEntityMultiblockRegistry` ⇒ 需比对 |
| `Grid` | `getMachines` RETURN（cancellable） | 我们无 `Grid` mixin ✔ |
| `PathGridCache` | `recalcController` RETURN | 我们无 ✔ |
| `ItemMachines` | `placeBlockAt` RETURN | 我们无 ✔ |
| `MTEBasicTank` | `buildUI` HEAD（cancellable） | 我们无 ✔ |
| `MTEHatchInputBusMEGui` | `createBottomRightCornerFlow` RETURN（cancellable） | 我们有 `MixinMTEHatchInputBus`（不同类）；GUI 侧需方法级比对 |
| `MTETieredMachineBlockBaseGui` | `createBottomRightCornerFlow` RETURN（cancellable） | 我们有 `MixinMTEHatchCraftingInputMEGui` / `MixinSuperCraftingInputHatchMEGui` ⇒ 需方法级比对 |

**注册面**：我们产物 MANIFEST 目前只有 `MixinConfigs: mixins.ae2_qof.json`（RFG 按 modid 生成），**没有 late 基础设施**。
两条可行路线：
- **A（照上游）**：引入 `IMixins` / `@LateMixin` / `ILateMixinLoader` / `TargetedMod` 那一套；
  编译期把 `+unimixins-all-1.7.10-0.3.1.jar` 里的 `com/gtnewhorizon/gtnhmixins/**` 抽成一个小 jar 放进 `libs/`，
  用本项目既有的 `compileOnly(project.files("libs/xxx.jar"))` 模式引用（离线构建友好），运行期由整合包提供。
  优点：与上游代码一字不差，且能用 `requiredMods` 做依赖门控。缺点：多一个编译期 `libs/` 产物。
- **B（不引 GTNHMixins）**：把 late 配置直接追加进 MANIFEST 的 `MixinConfigs`（逗号分隔），配置写 `required:false`，
  需要门控就自己写 `IMixinConfigPlugin`。优点：不碰依赖。缺点：与上游写法分叉，门控与 late 时序要自己保证。

### 3.5 资源 / 引导页
- Nexus 的引导页在参考仓库**根目录** `guidenh/`，由它自己的 `build.gradle.kts` 的
  `tasks.named<Jar>("jar") { from("guidenh") { into("assets/${modId}/guidenh") } }` 打进 jar；
  **本仓库不用这套**——我们的页面直接放在 `src/main/resources/assets/ae2_qof/guidenh/...`。
  ⇒ 吞并时把 10 个 md 直接放进 `src/main/resources/assets/ae_wireless_nexus/guidenh/{_zh_cn,_en_us}/ae_wireless_nexus/`，
  **不需要改构建脚本**。
- 贴图/language 直接并入 `assets/ae_wireless_nexus/`（与该模组自有域同路径，不与我们冲突）。
- 注意坑位 #20：引导页里的 `icon:` / `item_ids:` 必须写**真实注册名**（GT 机器要带 meta）；这些页面属于它自己的 modid，原样搬运即可，但发布前仍要做一次全量对照审计。

---

## 四、吞并步骤（草案 · **待批准后**才实施）
1. **代码**：把 39 个类**原样**搬入 `src/main/java/cn/dancingsnow/ae_wireless_nexus/**`（保留包名/类名/modid，保存档无缝；
   与 3.12.0 吞并 `aeinfinitycell` 同一手法），保留其 `@Mod` 作为本 jar 内的**第二个 mod**（`mcmod.info` 同步加条目）。
2. **资源**：lang / 贴图 / 10 个引导页并入 `assets/ae_wireless_nexus/`；`Config` 保持独立配置文件。
3. **mixin**：按 3.4 选 A 或 B，加入 8 个 late mixin；**先做与既有 GT mixin 的逐方法比对**再启用。
4. **许可合规包**（LGPL §4）：`assets/ae_wireless_nexus/LICENSE` 放 **LGPL-3.0 全文**、另附 **GPL-3.0 全文**；
   `CREDITS.md` + `docs/THIRD_PARTY_NOTES.md` 写明作者、许可、我们所做的修改；源码保持公开、该部分不混淆。
5. **实例**：吞并后必须让用户**删除** `mods\【私货】ae_wireless_nexus-1.0.2.jar`，否则同名 modid/方块双注册。
6. **版本**：按用户规则「加功能才升 0.1」⇒ 应为 **3.24.0**（当前链 3.23.2；实施前再确认一次）。
7. **验收**：旧存档直接进入 ⇒ 无线控制器/连接器仍在、已命名无线网络与绑定关系保留、GT 仓室按钮在位。

---

## 五、风险与待决策
| # | 风险/决策 | 说明 |
|---|---|---|
| R1 | **LGPL 合规成本** | 公开分发就必须备齐 §4 的四件套；被吞并部分**永远保持 LGPL**，不能声明整包 MIT |
| R2 | **同名 modid 双注册** | 只要用户忘了删独立 mod，就会双注册；部署脚本要加检查 |
| R3 | **存档契约** | 方块名/TileEntity 名/`ae_wireless_nexus.networks` 必须一字不改；两参 `registerBlock` 的活跃容器必须验证 |
| R4 | **mixin 共存** | 两个同目标类（`BaseMetaTileEntity` / `CommonMetaTileEntity`）需要逐方法审计；`required=true` 的 late 配置在目标缺失时会**启动崩溃**，需按 3.4 门控 |
| R5 | **编译期依赖** | 路线 A 需要在 `libs/` 放 GTNHMixins 抽取件（离线构建友好）；路线 B 要自写 plugin |
| D1 | **要不要做** | 用户动机是"减少一个 mod 文件"；若只为此，收益（少一个 jar）与成本（LGPL 合规 + mixin 共存风险）需要权衡 |
| D2 | **替代路线** | ① 保持独立依赖（零义务）；② 找 DancingSnow 要 MIT/双许可（同作者，可行性不低）；③ 只按行为自行重写 |

---

## 六、附：本轮用到的取证命令（可复查）
```powershell
# 1) 许可事实（本地）
Get-Content 'E:\wzt\MC\modcreater\reference_src_290b3\Applied-Energistics-Wireless-Nexus-1.0.2\LICENSE' -TotalCount 3
Get-FileHash '...\Applied-Energistics-Wireless-Nexus-1.0.2\LICENSE' -Algorithm SHA256
# 2) 上游交叉验证
#    raw.githubusercontent.com/DancingSnow0517/Applied-Energistics-Wireless-Nexus/master/LICENSE        → 200（LGPL-3.0）
#    .../master/src/main/resources/LICENSE                                                                 → 200（未填写 MIT 占位）
#    .../master/LICENSE-template                                                                           → 404
# 3) 私货 jar 是否被改过（类清单比对）
& jar tf '...\mods\【私货】ae_wireless_nexus-1.0.2.jar' | Select-String '^cn/dancingsnow/.*\.class$'
# 4) 运行期依赖核查（gtnhmixins / backhand）
#    +unimixins-all-1.7.10-0.3.1.jar 内含 com/gtnewhorizon/gtnhmixins/ILateMixinLoader.class
#    backhand-1.8.14.jar 内含 xonin/backhand/api/core/BackhandUtils.class（javap 确认 getOffhandItem）
# 5) mixin 注入点
#    javap -v / 源码 grep @Inject(method=…)（见 §3.4 表）
```
