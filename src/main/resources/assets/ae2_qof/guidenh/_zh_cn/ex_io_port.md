---
navigation:
  title: 强化 IO 端口
  parent: index.md
  icon: ae2_qof:ex_io_port
item_ids:
  - ae2_qof:ex_io_port
author: wztwzt
---

# 强化 IO 端口

在原版 AE2 IO 端口基础上的强化版本：**单次传输量按可配置倍率放大**，配合存储总线/输入总线实现海量物品的快速搬运。

## 用途

- 大规模农场产物入库、批量熔炼供料等高吞吐场景
- 原版 IO 端口每 tick 只搬少量物品，强化版单次可搬运数千甚至更多

## 配置倍率

编辑 `config/ae2_qof/settings.json`：

```json
{
  "io_port_rate": 1024
}
```

- 默认 `1024`，范围 1 ~ 2147483647
- 支持热加载（保存后约 1 秒生效）
- 游戏内可用 `/ae2qof reload` 立即重载，`/ae2qof status` 查看当前值

## 合成

铁锭 + 玻璃 + 红石 + 钻石 组合合成（见配方标签）。

## 注意

- 倍率过大时注意目标容器/网络的吞吐承受能力
- 与原版 IO 端口使用方式完全一致：贴住目标方块，放入存储元件即可工作
