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

`config/ae2_qof/settings.json`:

| Key | Value | Meaning |
|---|---|---|
| `smart_doubling_max_rounds` | `0` (default) | Unlimited: dispatch as many rounds as possible in one go |
| | `> 0` | Upper bound on the total rounds dispatched at once |
| `smart_doubling_push_cap` | `4096` (default) | **Per-push (per-tick) round cap**: at most this many rounds each tick; the remainder continues next tick |

> Want bigger batches? Raise `smart_doubling_push_cap` (keep `smart_doubling_max_rounds` at 0).
> The 4096 default exists because AE2's power probe stops as soon as it has collected enough — asking for
> hundreds of millions of AE at once forces a walk over every energy storage, and stuffing that many items
> into a machine buffer drowns the client in item updates (that is how 1T-scale orders used to freeze).

## Notes

- Crafting-table (craftable) patterns never double.
- Blocking / smart-blocking modes automatically fall back to single-round pushes.
- Fluid interfaces and fake-crafting mode do not double.
