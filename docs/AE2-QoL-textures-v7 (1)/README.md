# AE2-QoL 材质 v6 · 分面

| 面 | 文件 | 来源 |
|---|---|---|
| 正面 FRONT | `{name}.png` | 图2/3/4 与统一舱室 |
| 顶面 TOP | `{name}_top.png` | v5 十字双仓 / 无线机箱 |
| 侧面 SIDE | `{name}_side.png` 与 `casing_side.png` | 图一无限输入双仓 |

四个自适应舱（A3 输入 / A6 输入 / A4 输出 / A5 输出）共用暗井边框；输入=闪电，输出=三叉外扩符。

模组若尚未分面注册，需要把 GT 机器的 top/side/front 分别指向这些 PNG。未做游戏内验证。

## universal_maintenance_hatch — 万能维护仓
正面：线程修改仓语言（蓝框+#格+青辉）。顶：十字双仓。侧：图一共用。

## adaptive_net_terminal — 自适应电网终端
正面：OP 仓语言（黄条+屏+RGB键）。顶：十字双仓。侧：图一共用。

## adaptive_net_hatch — 自适应电网输入仓
四舱统一暗井。输入：绿色闪电。

## adaptive_net_laser_hatch — 激光源仓
四舱统一暗井。输出：青色外扩爆发符（闪电的成对符号）。顶：无线机箱。

## adaptive_net_dynamo_hatch — 动力仓
四舱统一暗井。输出：金色外扩爆发符。顶：无线机箱。

## adaptive_net_laser_target — 激光靶仓
四舱统一暗井。输入：青色闪电。顶：无线机箱准星。

## wireless_energy_input — 无线EU输入终端
正面：图二虹彩能量场（原创像素）。顶：无线机箱。侧：图一共用。

## wireless_energy_output — 无线EU输出终端
正面：与 A7 成对的虹彩场，能量向中心暗孔汇入。
