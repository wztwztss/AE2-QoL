---
navigation:
  title: Infinity Storage Cell
  parent: index.md
item_ids:
  - aeinfinitycell:infinity_storage_cell
author: wztwzt
---

# Infinity Storage Cell (Feature 16)

A near-limitless AE2 storage cell for **items, fluids and essentia** — no capacity or type limits.

## Usage

1. Craft or obtain an "Infinity Storage Cell" (originally AE2 Infinity Cell, now merged into this mod).
2. Put it into an ME Drive or ME Chest.
3. Contents live in the **world save** (the cell item only holds a UUID reference); copies of a cell share one backend inventory.

## Hover Stats

Hover the cell in your inventory to see live stats: total / items / fluids / essentia breakdown with byte estimates.

- Letter units by default (`12.34M`); **hold Ctrl** for scientific notation (`1.85×10^25`)
- Data is aggregated server-side (contents live in the world save; the client requests on demand)

## NEI View

Press `U` on the cell in NEI to open **Infinity Cell View** and browse everything in pages.

## Notes

- **Mutually exclusive** with the standalone AE2 Infinity Cell mod: remove the original jar before installing this one; existing cells and save data migrate seamlessly
- AppEU energy channel is not included
