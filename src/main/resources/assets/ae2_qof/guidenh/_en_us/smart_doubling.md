---
navigation:
  title: Smart Doubling
  parent: index.md
author: wztwzt
---

# Smart Doubling

Ends round-by-round refilling on GT processing lines: once enabled, the crafting CPU pushes **N rounds of materials at once** to the machine, which then runs continuously before returning for more.

## Enabling

A **Smart Doubling toggle button** (with its own tooltip) appears in the GUI of:

- **ME Interface** (block/part): the cycle-arrow button
- **GT Pattern Input Hatch**: bottom-left toggle
- **PH Dual Input Hatch**: in-GUI toggle

The toggle is per-medium and off by default.

## How It Works

1. A job reaches a medium with doubling enabled.
2. The CPU computes N — clamped by available ingredients, network power, machine buffer capacity and the configured cap.
3. `N × recipe inputs` are extracted and pushed in one shot; outputs and power are booked for N rounds.
4. Large jobs keep pushing every tick until done — even 1T-scale orders stay responsive.

## Config

`smart_doubling_max_rounds` in `config/ae2_qof/settings.json`:

| Value | Meaning |
|---|---|
| `0` (default) | Unlimited: push as many rounds as possible per shot |
| `> 0` | Per-push round cap |

## Notes

- Crafting-table (craftable) patterns never double.
- Blocking / smart-blocking modes automatically fall back to single-round pushes.
- Fluid interfaces and fake-crafting mode do not double.
