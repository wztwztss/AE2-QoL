# Mixin 笔记
> AE2‑QoL‑1.7.10‑GTNH
记录所有Mixin注入点、风险、修改历史、已知副作用。

> 重要提醒
> 1. MC1.7.10 SRG名称容易变动，修改前核对方法签名。
> 2. 修改后jar放入测试环境，查看mixin.log确认注入状态。
> 3. 参考其他模组Mixin实现：`E:\wzt\MC\modcreater\reference_src`

## Mixin清单
> 历史存量 Mixin 待补登记（见 `docs/MOD_MAP.md` Mixin 列表先行索引）；以下为登记起点。

| Mixin 类路径 | 目标类 | 注入点 | 风险/说明 |
|---|---|---|---|
| `src/main/java/com/wztwzt/ae2_qof/mixin/gt/MixinSuperCraftingInputHatchMEGui.java` | GTNL `com.science.gtnl.common.gui.modularui.SuperCraftingInputHatchMEGui`（21504/21505 超级样板输入总成 ME） | `<init>` TAIL 捕获机器引用；`createBottomLeftCornerFlow` RETURN 追加智能倍增 ToggleButton | 3.9.0 新增。GTNL 可选依赖（compileOnly），目标类缺失时静默跳过；开关经 `BooleanSyncValue.allowC2S()` 双向同步直写 `MixinMTEHatchInputBus` 注入的 NBT 字段；注入点签名已在参考源码 `reference_src/GT-Not-Leisure-dev-290/.../SuperCraftingInputHatchMEGui.java:137` 核实 |

| `src/main/java/com/wztwzt/ae2_qof/mixin/nei/MixinGuiMEMonitorable.java` | AE2 `GuiMEMonitorable` | `postUpdate` HEAD（缓存收集）；`drawScreen` TAIL + `mouseClicked` HEAD cancellable（3.10.0 合成完成展示条，remap=true 走 refmap） | 仅标准终端：`getClass()==GuiMEMonitorable.class` 精确排除子类；guiLeft/guiTop 经 `GuiContainerAccessor` 读取；渲染/点击全程 try/catch 不影响终端本体 |

## 已知风险
> 记录容易踩坑、会和其他模组冲突的注入点
>
> - **`split("\\n")` 陷阱**：Java 正则中 `\n` 匹配真实换行符 LF，**不是**字面反斜杠+n。拆 lang 字面 `\n` 必须用 `TooltipTextButton.langLines(key)`（内部 `replace("\\n","\n")` 字面替换）后再 `split("\n")`。3.3.7~3.9.0 的三处 ModularUI mixin 曾因此换行从未生效（3.10.0 修复）。