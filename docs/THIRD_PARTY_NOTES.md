# 第三方来源与许可说明（3.23.0 新增部分）

> 本文档补充 `CREDITS.md`，**专门记录 3.23.0「智能通配样板」这批功能**涉及到的第三方来源、许可以及我们刻意采取的规避措施。
> 目的：本仓库将来要发布到 GitHub（开源或不开源），需要能逐条追溯到"用了谁的东西、依据是什么、有没有再分发"。

## 一、结论摘要

| 项目 | 我们做了什么 | 许可状态 | 是否需要额外义务 |
|---|---|---|---|
| **WildcardPatternforGTNH**（早期阶段：只读调研） | 3.22/3.23 阶段**只读其源码做兼容性调研**，借鉴"索引期展开"这一**架构思路**，当时**未复制任何代码**（见下文 §三 的硬证据）；**3.24.x 起改为整窗搬运其界面代码，见下一行** | 其仓库为 **MIT** | 早期无需；搬运后按下一行履行 |
| **WildcardPatternforGTNH** 1.7.10-1.1.0 | 3.24.x/3.25.x：**界面子系统整窗搬运**到本仓库 `wildport/`（25 个文件 / 7,870 行：主窗与复合窗、state/config/entry/generator、三个拖入控件、GUI handler、两个网络包、compat 两个类），并另写 `wildport/bridge/WildcardBridge.java` 与我们的 `SmartWildcardState`/`SmartWildcardExpander` 做双向桥接 | 该项目 LICENSE 为 **MIT** | 已履行：每个搬运文件头保留原项目名与 MIT 声明、并写明「本仓库改了什么」（包名改为 `com.wztwzt.ae2_qof.wildport`、`WildcardPatternMod` 引用改 `MyMod`、GUI id 收口到 `WildportIds`、网络通道改 `_wild` 后缀避免与 `ModNetwork` 重名）；其物品注册入口**不调用**（我们的物品仍是 `ItemSmartWildcardPattern`）；本文档与 CHANGELOG 记录来源与改动 |
| **GTNH-ModularUI (ModularUI 1.3.4)** | 仅**编译期**依赖（`libs/modularui-1.3.4.jar`，SHA256 `9221B07C…`，取自测试实例），用于编译搬运进来的**两套**界面（Wild 通配样板窗口、AE2PatternGen 生成器窗口）；运行期由实例的 `modularui-1.3.4.jar` 提供，我们的 jar 内**不分发**其代码 | LGPL-3.0（编译期引用，非再分发） | 与既有 `libs/` 编译基线（GT/AE2/Thaumcraft…）同一处理方式 |
| **AE2PatternGen** 1.5 | ① 3.23.0：**批量生成器核心改编自其代码**（`recipe/GTRecipeSource` 的 RecipeMap 枚举与筛选思路、`encoder/PatternEncoder` 的样板 NBT 编码、`filter/*` 的过滤器功能面）；② 3.26.0：**界面与配套子系统整窗搬运**到本仓库 `apgport/`（**65 个文件 / 9,983 行**：gui（GuiPatternGen 440 / GuiRecipePicker 678 / GuiPatternStorage 274 / GuiComboBox 177 / ExplicitFilterDropFormatter 199 / GuiPatternDetail 114 / FilterTextFieldWidget / FilterDragChoiceButtonWidget / GuiHandler / ContainerPatternStorage）、filter（9 个）、recipe、encoder、storage（PatternStorage / RecipeCache* / ModVersionHelper）、network（18 个包）、config/util/command） | README 明确写 **MIT** | 已履行：每个搬运/改编文件头保留原项目名、MIT 声明与「本仓库改了什么」（包名改 `com.wztwzt.ae2_qof.apgport`；`AE2PatternGen.instance` → `MyMod.instance`；proxy 三处调用收口到 `ApgStubs`（存储/详情面板记 WARN 待接线）；网络通道改 `_apg` 后缀，避免与 `ModNetwork`/`WildcardNetwork` 重名导致启动崩溃）；其物品注册入口**不调用**（我们的物品仍是 `ItemSmartPatternGenerator`）；本文档与 CHANGELOG 记录(36) 记明来源与改动 |
| **Applied Energistics 2 (GTNH)** | 编译期/运行期**链接**（AE2 是本模组的硬依赖） | **LGPL-3.0** | 不再分发其代码/素材 ⇒ 无 LGPL 义务。**原先复制的一张贴图已移除**（见 §二） |
| **ModularUI 2 (Cleanroom)** | 编译期 `compileOnly` + 运行期由整合包提供；界面用它构建 | **LGPL-3.0** | 同上：不随本模组分发 ⇒ 无义务 |
| **GT5-Unofficial / ProgrammableHatches / GTNL** | 编译期 `compileOnly`，按官方 API 调用；不复制其代码 | 各自许可（GT 为 LGPL-3.0） | 不随本模组分发 ⇒ 无义务 |
| **Applied Energistics: Wireless Nexus 1.0.2**（`ae_wireless_nexus`，作者 DancingSnow） | **当前只是外部依赖**（实例自行安装，本 jar 内**不含**其代码）。2026-09-28 做了**只读吞并可行性审计**，用户决定「先只记录事实，暂不动代码」 | **LGPL-3.0**（仓库根 `LICENSE`；上游同文件交叉验证；**无 MIT 依据**） | **暂无需**（未分发其代码）。一旦吞并，必须履行 LGPL-3.0 §4 的四件套 —— 详见 §六 与 `docs/DESIGN_wireless_nexus_merge_audit.md` |

## 二、已采取的规避措施（有据可查）

1. **不再复制任何第三方贴图/素材**：3.23.0 早期版本曾把 AE2 的
   `assets/appliedenergistics2/textures/items/ItemEncodedPattern.png` 复制进本仓库作为图标，
   **现已删除**（`git rm`，提交 `85ce718`），并改为在物品里**按名引用** AE2 自己的贴图：
   ```java
   setTextureName("appliedenergistics2:ItemEncodedPattern");
   ```
   这样本仓库**不再分发**该素材（AE2 是硬依赖，运行时由整合包提供），绿色仍由
   `getColorFromItemStack` 染色实现 —— 与参考模组"染色"的做法一致。
2. **不复制参考模组代码**：全部实现为本仓库自行编写，命名、结构、日志、注释均为本模组风格；
   §三 给出了可复查的审计命令与结果。
3. **`docs/research/` 下的调研报告**：它们是对两个参考模组源码的**分析笔记**，含少量 `文件:行号` 级引用
   （为兼容性取证所必需）。**发布公开仓库时建议排除该目录**（见 §四），以免产生不必要的争议。

## 三、可复查的"未复制代码"审计

在本仓库根目录执行：

```powershell
# 1) 参考源码从未进入本仓库（含历史）
git log --all --name-only --pretty=format: | Select-String 'reference_src|wildcardpattern|AE2PatternGen'   # 预期：无输出

# 2) 参考实现的"特征标识"在我们的源码里必须一个都不出现
foreach ($k in 'WildcardInputComponents','WildcardGeneratedPatternId','CompositeWildcard',
               'WildcardGlobalExcludeMaterials','getAllKnownMaterialNames','WildcardPatternEntry',
               'WildcardPatternGenerator','ItemWildcardPattern','WildcardPatternWindow') {
  git grep -l -- $k -- 'src/*' }
```

**审计结果（2026-09-26）**：9 个特征标识**全部未出现**；参考源码未入库、历史中也不存在。

## 四、已按作者决定执行的事项（2026-09-26）

1. **许可证 = MIT，版权人改为作者本人** ✓ 已执行：`LICENSE` 的版权行由模板作者的
   `Copyright (c) 2021 Johann Bernhardt`（GTNH 模组模板自带；WildcardPatternforGTNH 同为该模板产物，
   所以两份文件一字不差）改为 **`Copyright (c) 2026 wztwzt`**，许可正文保持 MIT 不变。
2. **`docs/research/` 不随公开仓库发布** ✓ 已执行：四份调研报告已移出仓库，保存在仓外私有目录
   `E:\wzt\MC\modcreater\_ae2qol_private_research\`，并在 `.gitignore` 加入 `docs/research/` 规则防止再次被提交。
   ⚠️ **历史遗留提醒**：这四份文件此前已经进入 git 历史；公开仓库若沿用现有历史仍可检出它们
   ⇒ 发布时应从**过滤后的历史**导出（例如 `git filter-repo --path docs/research --invert-paths`），
   或另起一个干净仓库再推。发布前请把这一条当作检查项。
3. **批量生成器改编 AE2PatternGen 的代码**（其 README 声明 MIT）✓ **已执行**：改编后的
   `generator/SmartPatternGenerator.java` 文件头保留了原项目名称、出处与 MIT 许可声明；
   `CHANGELOG` 记录 (32) 与本文档均注明「哪一部分改编自它、我们改了什么」
   （去掉了它的缓存/冲突解决/虚拟存储/自建网络子系统，改用本仓库的写回与日志口径）。
3. **`CREDITS.md` 建议补两行**指向本文档，并列出 WildcardPatternforGTNH / AE2PatternGen 的 MIT 与出处链接（待你确认许可后我一并加上）。
4. 发布前再跑一次 §三 的审计命令，作为发布检查项。

## 五、致谢（与法律无关，但应写）

- **WildcardPatternforGTNH**（MIT）—— 通配样板"一张覆盖一类"的整体架构与"索引期展开"思路源自它的实现；
  本模组据此重写，并按本仓库原则补上了展开上限、非静默日志与 NEI 加号自动推导。
- **AE2PatternGen**（MIT）—— "按过滤器批量生成样板"的功能设计参照了它。
- **AE2 / ModularUI 2 / GT5-Unofficial / ProgrammableHatches / GTNL** —— 本模组赖以运行的依赖，按其公开 API 调用。

---

## 六、Applied Energistics: Wireless Nexus —— **LGPL-3.0**（2026-09-28 审计，未吞并）

### 6.1 许可结论（有据可查）
| 证据 | 结果 |
|---|---|
| 参考树根 `LICENSE`（7,652 B） | **GNU LGPL v3（2007-06-29 全文）**；SHA256 `E3A994D82E644B03A792A930F574002658412F62407F5FEE083F2555C5F23118` |
| 上游 `master/LICENSE` | HTTP 200，**同为 LGPL-3.0 全文** |
| `src/main/resources/LICENSE`（也被打进 jar 根） | GTNH 模板的**未填写** MIT 占位（`Copyright (c) [year] [fullname]`）⇒ **不构成有效授权** |
| 上游 `LICENSE-template` | **HTTP 404（不存在）** —— 对比同作者的 `AE2InfinityCell` 当时有 MIT 模板，故那次判 MIT 成立，**本次不成立** |
| 逐文件头 | 全量 `.java` 无任何 copyright/license/SPDX 声明 |

⇒ **应按 LGPL-3.0 对待，不存在当 MIT 搬的依据。**（作者同为 `DancingSnow`，但**不能**用 `AE2InfinityCell` 的 MIT 结论推广到本模组。）

### 6.2 若将来吞并，必须履行的 LGPL-3.0 §4（Combined Work）四件套
1. **显著声明**：写明使用了该 Library、且该部分及其使用受 LGPL-3.0 覆盖；
2. **随附 LGPL-3.0 与 GPL-3.0 全文**；
3. 运行期若显示版权声明，需含该部分的版权声明；
4. 提供被吞并部分的 **Corresponding Source（含我们的修改，继续以 LGPL 授权、公开可得）**，
   并保持用户可替换/重新链接该部分；**不得限制修改与反向工程、不得混淆该部分**。

⇒ 我们自己的代码可继续 MIT，但被吞并部分**不得改写成 MIT**；整个 jar 是 **MIT + LGPL-3.0 混合**。

### 6.3 技术可行性（细节见 `docs/DESIGN_wireless_nexus_merge_audit.md`）
- 规模小：**39 个类 / 2,693 行** + 13 个资源 + 10 个 GuideNH 页；私货 jar 与上游源码**逐类一致**（只多构建期 `Tags`），未被改过。
- 运行期依赖齐备：`backhand-1.8.14`（`BackhandUtils.getOffhandItem` 已 javap 确认）、`+unimixins-all` 提供 GTNHMixins。
- 4 个硬坑：① 同名 modid 双注册（实例那份独立 mod 必须先删）；② 存档契约（方块名/TileEntity 名/`ae_wireless_nexus.networks`）必须一字不改，
  且注意 `registerBlock` **两参重载**取的是"当前活跃 mod 容器"（本项目坑位 #20）；③ 本项目**此前没有 late mixin 注册面**，需按路线 A/B 补；
  ④ 两个同目标类 mixin（`BaseMetaTileEntity` / `CommonMetaTileEntity`）需逐方法审计共存。
