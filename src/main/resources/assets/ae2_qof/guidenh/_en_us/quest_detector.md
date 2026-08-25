---
navigation:
  title: ME Quest Detector
item_ids:
  - ae2_qof:quest_detector
author: wztwzt
---

# ME Quest Detector

A block device for your ME network: **items stored in the network automatically complete BetterQuesting retrieval-type quests** — no more pulling items into your inventory or using submit stations.

## Usage

1. Craft an "ME Quest Detector" (4 iron ingots + glass + 2 redstone + a book).
2. Place it on the ME network (needs power and a channel); it **binds to the placing player**.
3. Store quest-required items in the network — checked every second, quests complete automatically once requirements are met.

## Behavior

- Only handles **retrieval-type tasks** (non-consuming); consuming tasks are completely unaffected (nothing is ever taken from the network).
- Supports BQ **party-shared progress**: quests shared with teammates of the bound player are detected too.
- The bound player must be online; break and re-place to rebind.
- Pauses without power/channel; has no effect when BetterQuesting is absent.

## WAILA / JADE

Shows the bound player and network status while targeting the device.

## Requirement

- [Better Questing](https://www.curseforge.com/minecraft/mc-mods/better-questing) (GTNH edition 3.8.70+)
