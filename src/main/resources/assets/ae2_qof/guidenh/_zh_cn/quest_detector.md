---
navigation:
  title: ME 任务检测器
  parent: index.md
item_ids:
  - ae2_qof:quest_detector
author: wztwzt
---

# ME 任务检测器

接入 ME 网络的方块设备：**网络里存的物品会自动完成 BetterQuesting 检索型任务**——再也不用手动把任务物品拿出来或跑去提交站。

## 用法

1. 合成「ME 任务检测器」（铁锭 ×4 + 玻璃 + 红石 ×2 + 书）。
2. 放到 ME 网络上（需要供电与频道），放置时**绑定放置者**。
3. 把任务所需物品存进 ME 网络——每秒比对一次，库存达标即自动完成任务。

## 行为细节

- 只处理**检索型任务**（不消耗类）；消耗型任务完全不受影响（绝不从网络扣走任何物品）。
- 支持 BQ **组队共享进度**：绑定玩家的队友间共享的任务同样会被检测。
- 绑定玩家需在线；拆下重放可重新绑定。
- 断电/断频道时暂停检测；未安装 BetterQuesting 时本方块无任何效果。

## WAILA / JADE

指向该方块时显示绑定玩家与网络状态。

## 前置

- [Better Questing](https://www.curseforge.com/minecraft/mc-mods/better-questing)（GTNH 版 3.8.70+）
