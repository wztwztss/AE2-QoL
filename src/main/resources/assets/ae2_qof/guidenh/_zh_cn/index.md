---
navigation:
  title: AE2 QoL
  recommend: 100
author: wztwzt
---

# AE2 QoL 使用指南

AE2 QoL 为 Applied Energistics 2（GTNH 版）提供一系列效率增强功能。

## 全部功能一览（15 项）

| # | 功能 | 一句话说明 | 详情页 |
|---|---|---|---|
| 1 | 样板上传/撤回/交换 | 任意样板终端 GUI 内一键管理网络接口上的样板 | [NEI 集成](nei_features.md) |
| 2 | NEI 取物/合成下单 | Shift+左键从网络提取物品；中键对可合成物品下单 | [NEI 集成](nei_features.md) |
| 3 | NEI tooltip 存量/可合成 | 悬停任意物品显示其在网络中的存量与可合成状态 | [NEI 集成](nei_features.md) |
| 4 | NEI 书签数量角标 | 书签面板与配方页直接叠加显示网络存量数字 | [NEI 集成](nei_features.md) |
| 5 | 合成完成通知 | CPU 订单完成时屏幕右上角横幅+音效提醒 | [合成工具](crafting_tools.md) |
| 6 | 合成重新规划 Replan | 材料变化后一键重提订单，无需重新下单 | [合成工具](crafting_tools.md) |
| 7 | 强化 IO 端口 | 单次传输量按倍率放大，高吞吐搬运 | [强化 IO 端口](ex_io_port.md) |
| 8 | 无限水岩浆磁盘 | 水/岩浆各约 92 亿 mB 的永不枯竭元件 | [无限磁盘](infinity_cell.md) |
| 9 | 无线收发器+连接器 | ME 网络隔空组网，收发器对支持跨维度 | [无线组网](wireless.md) |
| 10 | 石英切割刀复制名称 | 手持切割刀右键方块/机器/AE 部件，复制名称到剪贴板 | [切割刀](knife.md) |
| 11 | F 键搜索填充 | 在 NEI 上按 F，把鼠标所指物品名填入终端搜索框 | [NEI 集成](nei_features.md) |
| 12 | NEI 叠加层开关 | OV 按钮 / `/apu-overlay` 命令切换所有角标与提示 | [NEI 集成](nei_features.md) |
| 13 | 智能倍增 | CPU 一次性推送 N 轮材料，消除逐轮补料等待 | [智能倍增](smart_doubling.md) |
| 14 | 二合一终端 | 样板编码+接口管理同屏，三种形态（方块/部件/无线） | [二合一终端](merged_terminal.md) |
| 15 | ME 任务检测器 | ME 网络物品自动完成 BQ 检索型任务（不消耗） | [ME 任务检测器](quest_detector.md) |
| 16 | 无限存储元件 | 物品/流体/源质无限存储，悬停统计 + NEI 查看 | [无限存储元件](infinity_storage.md) |

## 快速上手

1. 各功能物品通过对应配方合成获得（见各详情页配方标签）。
2. 悬停任意本 mod 物品，**按住 G** 直接跳转到它的详细说明页。
3. 终端 GUI 内每个按钮都有悬停提示。
4. 安装 GuideNH 后本指南可在游戏内随时查看（指南书或 G 键）。

## 配置文件

`config/ae2_qof/settings.json`：

| 键 | 说明 | 默认 |
|---|---|---|
| `io_port_rate` | 强化 IO 端口单次传输倍率 | 1024 |
| `smart_doubling_max_rounds` | 智能倍增最大轮数（0=不限） | 0 |
| `nei_overlay_enabled` | NEI 叠加层开关 | true |

支持热加载（改完约 1 秒生效），或用 `/ae2qof reload` 立即重载。

## 命令

- `/ae2qof reload` —— 重载配置
- `/ae2qof status` —— 查看当前配置值
