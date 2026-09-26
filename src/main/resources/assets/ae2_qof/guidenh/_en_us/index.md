---
navigation:
  title: AE2 QoL
  recommend: 100
author: wztwzt
---

# AE2 QoL Guide

AE2 QoL brings a set of quality-of-life enhancements to Applied Energistics 2 (GTNH edition).

## All Features (21)

| # | Feature | Summary | Page |
|---|---|---|---|
| 1 | Pattern Upload/Recall/Swap | Manage patterns on network interfaces from any pattern terminal GUI | [NEI Integration](nei_features.md) |
| 2 | NEI Extract / Craft | Shift+left-click to pull items from the network; middle-click to order crafting | [NEI Integration](nei_features.md) |
| 3 | Crafted Output Pin Row | After ordering, outputs auto-pin to a dedicated top row showing full network stock | [Crafting Tools](crafting_tools.md) |
| 4 | NEI Tooltip Stock/Craftable | Hover any item to see its network stock and craftability | [NEI Integration](nei_features.md) |
| 5 | Bookmark count badges | Network stock numbers drawn on bookmark panel and recipe pages | [NEI Integration](nei_features.md) |
| 6 | Crafting notification | Banner + sound when a CPU job completes | [Crafting Tools](crafting_tools.md) |
| 7 | Replan | Re-submit a job after stock changed, no need to re-order | [Crafting Tools](crafting_tools.md) |
| 8 | Enhanced IO Port | Batch transfer at a configurable multiplier | [Enhanced IO Port](ex_io_port.md) |
| 9 | Infinite Water & Lava Cell | ~9.2 billion mB of water and lava, forever | [Infinity Cell](infinity_cell.md) |
| 10 | Wireless Transceiver + Connector | Cable-free network bridging; transceiver pairs work cross-dimensionally | [Wireless](wireless.md) |
| 11 | Cutting Knife name copy | Right-click with the knife to copy block/machine/part names to clipboard | [Knife](knife.md) |
| 12 | F-key search fill | Press F over a NEI item to put its name into the terminal search box | [NEI Integration](nei_features.md) |
| 13 | Overlay toggle | OV button / `/apu-overlay` switches all badges and tooltips | [NEI Integration](nei_features.md) |
| 14 | Smart Doubling | CPU pushes N rounds at once — no more round-by-round refills | [Smart Doubling](smart_doubling.md) |
| 15 | Merged Terminal | Encoding + interface management in one GUI, three forms (block/part/wireless) | [Merged Terminal](merged_terminal.md) |
| 16 | ME Quest Detector | ME network storage auto-completes BQ retrieval quests (non-consuming) | [ME Quest Detector](quest_detector.md) |
| 17 | Infinity Storage Cell | Limitless item/fluid/essentia storage with hover stats & NEI view | [Infinity Storage Cell](infinity_storage.md) |
| 18 | Universal Maintenance Hatch | Maintenance bypass + wireless energy + circuit parallel mapping in one | [Universal Maintenance Hatch](universal_maintenance_hatch.md) |
| 19 | GT Wireless EU Grid | Input/output hatches wirelessly transfer EU, cross-dimensional power | [Wireless EU Grid](wireless_eu_grid.md) |
| 20 | Adaptive Energy Grid | Data Stick configures network; hatches auto-adapt V/A/frequency | [Adaptive Grid](adaptive_grid.md) |
| 21 | Stock Monitor Terminal | Centrally view/edit AE2 level emitters and stock monitor covers, power-free with Nexus wireless | [Stock Monitor Terminal](stock_monitor_terminal.md) |
| 22 | Smart Wildcard Pattern | One pattern covers a whole recipe class: derive rules with NEI's +, built-in circuit, per-candidate exclusions | [Smart Wildcard Pattern](smart_wildcard.md) |

## Quick Start

1. Craft each feature's item (recipes on its page).
2. Hover any item of this mod and **hold G** to jump to its guide page.
3. Every button inside our terminals has a hover tooltip.
4. With GuideNH installed this guide is always available in game.

## Config

`config/ae2_qof/settings.json`:

| Key | Description | Default |
|---|---|---|
| `io_port_rate` | Enhanced IO Port transfer multiplier | 1024 |
| `smart_doubling_max_rounds` | Smart Doubling max rounds (0 = unlimited) | 0 |
| `smart_doubling_push_cap` | Smart Doubling per-push (per-tick) round cap | 4096 |
| `nei_overlay_enabled` | NEI overlay toggle | true |

Hot-reload supported (~1s), or run `/ae2qof reload`.

## Commands

- `/ae2qof reload` — reload config
- `/ae2qof status` — show active values
