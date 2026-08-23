---
navigation:
  title: AE2 QoL
  recommend: 100
author: wztwzt
---

# AE2 QoL Guide

AE2 QoL brings a set of quality-of-life enhancements to Applied Energistics 2 (GTNH edition).

## All Features (14)

| # | Feature | Summary | Page |
|---|---|---|---|
| 1 | Pattern Upload/Recall/Swap | Manage patterns on network interfaces from any pattern terminal GUI | [NEI Integration](nei_features.md) |
| 2 | NEI Extract / Craft | Shift+left-click to pull items from the network; middle-click to order crafting | [NEI Integration](nei_features.md) |
| 3 | NEI Tooltip Stock/Craftable | Hover any item to see its network stock and craftability | [NEI Integration](nei_features.md) |
| 4 | Bookmark count badges | Network stock numbers drawn on bookmark panel and recipe pages | [NEI Integration](nei_features.md) |
| 5 | Crafting notification | Banner + sound when a CPU job completes | [Crafting Tools](crafting_tools.md) |
| 6 | Replan | Re-submit a job after stock changed, no need to re-order | [Crafting Tools](crafting_tools.md) |
| 7 | Enhanced IO Port | Batch transfer at a configurable multiplier | [Enhanced IO Port](ex_io_port.md) |
| 8 | Infinite Water & Lava Cell | ~9.2 billion mB of water and lava, forever | [Infinity Cell](infinity_cell.md) |
| 9 | Wireless Transceiver + Connector | Cable-free network bridging; transceiver pairs work cross-dimensionally | [Wireless](wireless.md) |
| 10 | Cutting Knife name copy | Right-click with the knife to copy block/machine/part names to clipboard | [Knife](knife.md) |
| 11 | F-key search fill | Press F over a NEI item to put its name into the terminal search box | [NEI Integration](nei_features.md) |
| 12 | Overlay toggle | OV button / `/apu-overlay` switches all badges and tooltips | [NEI Integration](nei_features.md) |
| 13 | Smart Doubling | CPU pushes N rounds at once — no more round-by-round refills | [Smart Doubling](smart_doubling.md) |
| 14 | Merged Terminal | Encoding + interface management in one GUI, three forms (block/part/wireless) | [Merged Terminal](merged_terminal.md) |

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
| `nei_overlay_enabled` | NEI overlay toggle | true |

Hot-reload supported (~1s), or run `/ae2qof reload`.

## Commands

- `/ae2qof reload` — reload config
- `/ae2qof status` — show active values
