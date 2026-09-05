---
navigation:
  title: GT Wireless EU Grid
  parent: index.md
  icon: ae2_qof:wireless_input_hatch
item_ids:
  - ae2_qof:wireless_input_hatch
  - ae2_qof:wireless_output_hatch
author: wztwzt
---

# GT Wireless EU Grid

Wireless **EU energy transmission** via GT hatches. Unrelated to AE networks — purely for powering GT machines. A pair of input/output hatches enables long-distance, cross-dimensional energy transfer.

## Hatch Types

### Wireless Output Hatch (ID 32110)

- **Injects** local EU into the wireless energy network
- Place next to a generator/storage device to receive EU and send it wirelessly
- Binds to the placer's UUID on placement

### Wireless Input Hatch (ID 32111)

- **Pulls** EU from the wireless energy network to local storage
- Place in the energy slot of a multiblock that needs power
- Binds to the placer's UUID on placement

## How It Works

```
Generator → Wireless Output Hatch → [Wireless Grid] → Wireless Input Hatch → Multiblock
```

- The wireless grid is **globally shared** — all hatches bound to the same player share one energy pool
- Output hatches continuously inject EU; input hatches continuously pull EU
- Supports **cross-dimensional** transfer, no cables needed

## Usage

1. Craft a Wireless Output Hatch and a Wireless Input Hatch
2. Place the Output Hatch next to a generator, connected to EU
3. Place the Input Hatch in the energy slot of the multiblock you want to power
4. Energy flows automatically through the wireless grid

## Binding Mechanism

- Each hatch **auto-binds to the placer's UUID** on placement
- Only output and input hatches placed by the same player can transfer to each other
- On multiplayer servers, each player's wireless grid is independent
- Breaking and re-placing rebinds to the new placer

## Crafting

See NEI recipe tab.

## Notes

- The wireless grid itself does not store energy; EU is transmitted in real-time
- Transfer rate is limited by the voltage tier of both hatches
- Input hatches have a small internal buffer for temporary EU storage
- Works best alongside the Adaptive Energy Grid system
- The Universal Maintenance Hatch can also pull EU from the wireless grid
