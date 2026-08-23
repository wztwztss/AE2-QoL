---
navigation:
  title: NEI Integration
  parent: index.md
author: wztwzt
---

# NEI Integration (Features 1/2/3/4/11/12)

Plug the AE network directly into NEI: query, extract, craft and upload without ever opening a terminal.

## Pattern Upload / Recall / Swap (Feature 1)

Injected into both the **native AE2 pattern terminals** and the **Merged Terminal**:

- `↑ Upload`: auto-upload the current encoded pattern to an interface/assembler on the network
  - Remembers "recipe → provider" mapping; same recipes go to the same provider next time
  - A selection screen appears when several providers are available, with search
  - Shift+click forces the selection screen
- `← Recall`: take back the last matching encoded pattern from the network
- `⇄ Swap`: rotate primary/secondary output slots

> Recipe-map detection: one input can hit multiple GT recipe pools. This mod confirms with input+output double matching; the exact map captured from NEI transfer takes priority over server-side reverse lookup.

## Extract / Craft from NEI (Feature 2)

On any NEI item panel:

- **Shift+left-click**: pull that item from the ME network into your inventory (SIMULATE pre-check first; overflow is refunded, nothing lost)
- **Middle-click**: open the crafting amount dialog for craftable items directly

## Tooltip Stock / Craftable (Feature 3)

Hovering any item appends a line to the tooltip:

- <span style="color:cyan">**stock number**</span> + `AE` — total amount in the network
- Green `+ Craft` — the item can be crafted on the network
- ae2fc fluid packets / GT fluid display items show `mB` amounts

Data source: a network snapshot synced whenever any ME terminal is open; kept ~5 minutes after closing.

## Bookmark / Recipe badges (Feature 4)

With the overlay enabled, small stock numbers are drawn at the corner of material slots on the bookmark panel and recipe pages.

## F-key search fill (Feature 11)

Inside an AE2 terminal with NEI open, point at any item and press **F**: its name goes into the terminal search box. Works in native pattern terminals and the Merged Terminal.

## Overlay toggle OV (Feature 12)

Two ways to switch all badges/tooltips:

- The `OV` button inside pattern terminal GUIs
- `/apu-overlay` command (single player / LAN host only; use the OV button on dedicated servers)

Pure client-side setting (`nei_overlay_enabled`), independent per player.
