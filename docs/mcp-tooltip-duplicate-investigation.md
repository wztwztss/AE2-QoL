# AE 库存 / 可合成 Tooltip 重复显示调查与修复方案

- 日期：2026-09-18
- 状态：原因调查完成；方案待用户批准，尚未修改实现。
- 范围：只调查同一 Tooltip 中 AE 物品/流体存量与可合成提示重复的问题，不扩展修复库存身份、其他 GUI 或服务器逻辑。
- 证据：当前工作区源码及 diff、实际游戏实例 jar 的 javap 字节码、实例配置、已有 FML 日志。未启动游戏复现，未构建、部署或提交。

## 1. 结论

实际实例中的 fix41 同时在 `handleTooltip` 和 `handleItemTooltip` 追加同一行。Chromatic Tooltips Compat 取消 NEI 原渲染方法之后，仍通过自己的 ContextInfoEnricher 事件桥调用 `handleItemTooltip`，最终把两份文本合并到同一 Tooltip。

因此，直接原因是两个追加入口叠加，而不是需要把 AE 库存数量除以二。当前源码只发现一处 `NetworkTooltipHandler` 注册，没有第二处注册的源码证据，也不需要假设重复注册即可完整解释该现象。

旧 fix24 注释认为“Chromatic 存在时 handleItemTooltip 完全不执行”，对本次实际安装版本不成立：被取消的是 NEI 原方法，回调接口仍会由 Chromatic 的兼容事件桥主动调用。

## 2. 本次实际版本和配置

实例根目录：`E:\wzt\MC\PL genmulu\GT_New_Horizons_2.9.0-beta-1_Java_17-25(1)\.minecraft`

| 对象 | 已核对版本 / 状态 |
|---|---|
| 游戏实例 AE2-QoL | `AE2-QoL-3.19.0-fix41.jar`，字节码为双入口，无时间窗去重 |
| Chromatic Tooltips | `chromatictooltips-1.0.29-GTNH.jar` |
| Chromatic Tooltips Compat | `chromatictooltipscompat-1.0.31-GTNH.jar` |
| NEI | `NotEnoughItems-2.8.101-GTNH.jar` |
| 工作区版本号 | `gradle.properties:29` 为 `3.19.0-fix41` |
| 工作区已有修改 | `NetworkTooltipHandler.java` 有未提交修改，注释标为 fix42，加入 1 秒时间窗；本次调查之前已存在 |
| Compat 配置 | `notEnoughItemsEnabled=true`、`gregtechEnabled=true` |

HEAD：`ffe946ae18b8287731f27863dc9018991a1d8ef1`。

已有日志 `logs/fml-client-latest.log:13410–13411` 记录 Chromatic/Compat 版本；`:21167` 记录 `notenoughitems.GuiContainerManagerMixin` 从 `mixins.chromatictooltipscompat.late.json` 混入 `GuiContainerManager`。这些是读取的既有日志，不是本次运行测试。

## 3. 重复追加调用链

```text
NEI GuiContainerManager.renderToolTips
  └─ Compat GuiContainerManagerMixin 在 HEAD 接管
      ├─ 遍历 handleTooltip
      │   └─ QoL 追加 AE 存量 / Craft 行                 [第 1 份]
      └─ Chromatic TooltipHandler.drawHoveringText(stack, 基础文本)
          └─ ContextInfoEnricher.build
              ├─ 复制基础文本（已经含第 1 份）
              └─ 发出 ContextInfoEnricherEvent
                  └─ Compat NEIHandler.onContextInfoEnricherEvent
                      └─ getContextInfoTooltip
                          ├─ 新建另一个列表，先放 Temporary Name
                          ├─ 遍历 handleItemTooltip
                          │   └─ QoL 再追加同一行         [第 2 份]
                          └─ 删除临时标题，再 addAll 到事件文本
```

### 本模组证据

路径前缀：`src/main/java/com/wztwzt/ae2_qof/`。

- `ClientProxy.java:72–75`：唯一找到的注册调用。
- HEAD 中 `client/nei/NetworkTooltipHandler.java:32–44`：Chromatic 存在时由 `handleTooltip` 调用 `appendNetworkLine`。
- HEAD 中该文件 `:53–58`：`handleItemTooltip` 无条件进入同一追加方法。
- HEAD 中该文件 `:65–98`：一次查询生成一行，再 `currentTip.add(...)`。实例 fix41 jar 字节码与该双入口行为一致。
- 当前未提交源码的对应入口为 `:47–64` 和 `:73–87`，不要将新行号与 HEAD 行号混用。
- `client/NetworkInventoryCache.java:128–135`：按物品/流体读取一个 QueryResult；重复的两行并非这里输出两个结果。
- `mixin/nei/MixinNEIRecipeWidget.java:50–108`：是物品格角标绘制，不是本次重复 Tooltip 文本的追加来源。

### 实际 jar 证据

以下类均通过实例 jar 的 `javap -p -c` 核对，最终判断不依赖网上 main 分支：

- Compat `GuiContainerManagerMixin.tooltip$renderToolTips`：遍历 `handleTooltip`，调用 Chromatic `drawHoveringText`，再 cancel。
- Chromatic `ContextInfoEnricher.build`：复制 `getContextTooltip()`，发送 `ContextInfoEnricherEvent`，返回事件列表。
- Compat `NEIHandler.getContextInfoTooltip`：新建列表，循环 `handleItemTooltip`，移除临时标题。
- Compat `NEIHandler.onContextInfoEnricherEvent`：向已有事件列表 `addAll` 上述结果。

**这里是两个独立列表。** 因而只在 QoL 追加处加 `currentTip.contains(line)`，不能可靠消除这条跨列表重复路径。

## 4. 流体通道是否能保留

已特别核对实际 Compat 1.0.31 的 `NEIHandler.getItemStackFromContext`：

- 普通物品直接返回上下文物品。
- 对纯流体目标，当 GregTech 兼容启用且 GT 已加载时，调用 `GTUtility.getFluidDisplayStack(fluid, true)`，再传入 `handleItemTooltip`。
- 当前 `NetworkInventoryCache.java:182–195` 正好识别 `gregtech.common.items.ItemFluidDisplay`，并按该专用物品的 damage 读取流体注册 ID。

因此，对这套实际版本和当前配置，改用 `handleItemTooltip` 单一入口有明确的物品与流体调用链依据，不是仅对普通物品有效的方案。

版本限制：网上当前 main 的流体适配已不同，本次没有据此扩展兼容代码。更老或未来的 Chromatic 组合、禁用 GT 流体适配等配置，仍需单独验证，不能承诺所有版本无条件兼容。

## 5. 当前未提交的 1 秒时间窗修改

本次没有编辑或撤销这份修改。

当前 `NetworkTooltipHandler.java:42–44,57–61,80–83` 使用静态 `chromaticLastLine`、`chromaticLastWriteAt` 和 `1_000_000_000ns`：如果新行文本等于最近一次 `handleTooltip` 写入的文本，且小于 1 秒，就忽略 `handleItemTooltip` 的追加。

不建议以此作为最终修复，原因是：

1. “相同文字且时间接近”不等于“同一份 Tooltip”。两个不同物品库存同为 64，或都只有 Craft，可能生成完全相同文本。
2. 若新提示仅走 `handleItemTooltip`，但另一提示刚在 `handleTooltip` 写入相同文字，新提示会被误抑制，直至时间窗过期。这是代码层面可推出的条件性风险，尚未声称已经游戏内复现。
3. 静态时间状态横跨 GUI、物品和独立提示构建，不能精准表达一次构建的边界。
4. 它保留了两个入口以及重复查询，只是在末尾猜测是否该忽略第二份。

实例 fix41 jar 没有这些字段和判断。因此不能把工作区注释中的 fix42 当作已经部署或已经验证成功的修复。

## 6. 推荐方案 A：只保留一个追加入口

针对本次准确版本组合，建议只改业务文件 `client/nei/NetworkTooltipHandler.java`：

1. `handleTooltip(...)` 只返回 `currentTip`，不再添加 AE 提示。
2. `handleItemTooltip(...)` 作为唯一业务追加入口，保留库存查询、流体显示、Craft 标记、总开关和缓存有效期规则。
3. 经用户批准后，删除此次替代方案不再需要的 Chromatic 存在标记、1 秒时间窗字段及相关判断、失去用途的导入；可以保留当前 `buildNetworkLine` 辅助方法，不重构其他功能。
4. 纠正涉及本问题的旧注释，明确 Chromatic 的二次回调桥；不新增 Mixin、不增加第三方硬依赖、不修改 Chromatic/NEI jar 或配置。
5. 实施时同步功能映射与必要的变更说明；版本迭代、构建部署和提交按用户确认的实施范围执行。本轮只交方案。

两种环境都使用同一个入口：

- 普通 NEI：在 `handleTooltip` 没有产生接管提示时，原生流程正常构建物品名，再调用 `handleItemTooltip`。
- 当前 Chromatic Compat：经 ContextInfoEnricher 的兼容桥调用 `handleItemTooltip`。

优点：从源头移除重复，不跨帧、不依赖 1 秒窗口，也不会把合法重复的其他模组提示全局去重。

## 7. 其他方案对比

| 方案 | 评价 |
|---|---|
| A：单一 `handleItemTooltip` 入口 | 推荐；当前实例有完整调用链证据，改动最小 |
| B：当前 1 秒文本去重 | 不推荐；可能误抑制独立提示，时间窗不是构建边界 |
| C：追加前 `contains(line)` / 全局文本去重 | 本问题有跨列表合并，局部检查不够；全局去重还可能影响其他模组 |
| D：按实际渲染请求建立兼容上下文、保留双入口 | 只有确认需要支持缺少事件桥的其他版本时才考虑；本次会增加不必要复杂度 |
| E：只在检测到 Chromatic 时禁用 `handleItemTooltip` | 会丢失只经 item 回调产生提示的场景，且“已安装”不等于“对应兼容已启用”，不推荐 |

## 8. 用户批准后的验证清单

以下均为待执行，不是已通过结果。

- 普通物品：有库存不可合成 / 零库存可合成 / 有库存且可合成，各只显示一份正确内容。
- 流体：GT 显示流体、AE2FC 流体表示，覆盖上述三种状态；确认单位和名称不变。
- 真实桶/单元保持现有物品语义，不因去重改成查询容器内液体。
- 两个库存相同、可合成状态相同的不同物品之间快速切换，提示立即出现且不重复。
- NEI 物品列表、书签、配方输入/输出、AE 终端与合并终端、普通背包/容器。
- 支持范围内的嵌套预览，以及 Shift/Ctrl 等提示模式；检查是否存在仅经 item 回调的界面。
- 关闭叠加功能、无缓存、缓存过期时不显示；缓存更新后数量及时变化。
- 当前 Chromatic 配置，以及普通 NEI 环境下均保留物品名和其他模组提示。
- Java17 构建与诊断；批准部署后确认真正加载新 jar，不能只改源码就宣称游戏内已修复。

## 9. 指纹与只读边界

调查开始时业务文件 SHA-256：

`a8320288f9a44c9efbec2c70dee4261ce596790d40cd6f9c15e7d92e3f7ce1c7`

| 实例 jar | SHA-256 |
|---|---|
| AE2-QoL fix41 | `d8948109b76ed1015f068e041679b80609afd05cdd433bea6ccc7b28dfbe12e7` |
| Chromatic 1.0.29 | `79f8ea6228439947facb9673a8c0c35e09d9645591d7a18b3cbe32616f74d832` |
| Compat 1.0.31 | `5de8ea18e1d28108841930f7ff5049bc08040b323774aa02902753b89a16e6b0` |
| NEI 2.8.101 | `e113f79d8daf96a11589914da749b9299db7bed2edfd398f32cd2c96cc21e2cc` |

本轮仅新增本文档作为调查交付；保留所有原有未提交修改。没有执行实现修复、构建、游戏运行、jar 替换或 Git 提交。
