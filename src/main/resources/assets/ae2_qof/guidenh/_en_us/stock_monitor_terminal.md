---
navigation:
  title: Stock Monitor Terminal
  parent: index.md
  icon: gregtech:gt.blockmachines:32107
item_ids:
  - gregtech:gt.blockmachines:32107
  - ae2_qof:stock_monitor_cover
author: wztwzt
---

# Stock Monitor Terminal

A power-free information terminal for centrally viewing and editing AE2 standard Level Emitters and this mod's Stock Monitor Covers. No need to run to each machine to adjust thresholds — manage everything from one terminal.

## Layout (two pages behind a left vertical tab strip, since 3.31.0)

- The **left vertical tabs** split the GUI into **Emitters** and **Covers**; the choice is **remembered** (stored in the terminal's own NBT, so it survives reopening the GUI and relogging)
- Top row: terminal title, connection status, and the Connect AE / Switch Network button (shared by both pages)
- Every row: **target icon** (item icon, or the **real fluid texture**), name, right-hand value (type · mode · threshold), and the Highlight / Teleport buttons
- Row height is 24px, 8 rows fit on screen, and the list scrolls

> Target names are resolved **client-side**, so they stay in your language even on a dedicated server (the same path AE2's own GUIs use).

## Core Features

### 1. Emitter Central Management (Emitters page)

- Automatically enumerates all standard Level Emitters (`PartLevelEmitter`) in the current AE2 network
- Displays each emitter's monitor target (with item icon), type (Item/Energy) and threshold
- Click the name or the value in a row to open an edit panel and change the threshold
- Modifications require AE2 network BUILD permission
- Energy-type emitters: the panel shows a yellow note (this mod adds no further restriction)

### 2. Cover Central Management (Covers page)

- Automatically discovers the Stock Monitor Covers of **the network this terminal is bound to** (global registry, cross-dimensional)
- Displays each cover's monitor target (item icon / real fluid texture), threshold, mode (Below/Above) and online status
- Click an entry to open an edit panel and change threshold and mode; the edit applies to **the detection slot that row shows**
- Covers are removed from the list when dismantled; an unloaded chunk is marked offline (gray [OFF], icon left empty)
- When covers exist on other networks, an empty list says "N hidden from other networks"

### 3. Row actions: Highlight / Teleport

- **Highlight**: draws a 10-second highlight box when the target is in your dimension (blue for covers, purple for emitters); cross-dimension targets get a chat notice instead of an invisible box
- **Teleport**: moves you next to the target, searching for a safe standing spot (it cancels with a notice when none is found); cross-dimension is supported
- Both require the **session check plus BUILD permission** on that AE network (the client only sends coordinates; the server re-resolves the target)

### 4. Dual-Mode Networking

- **Nexus Wireless**: Click the "Connect AE" button to open the Nexus native wireless network selection panel and bind to any wireless network (same operation as covers)
- **Neighbor Direct**: When not bound to a wireless network, automatically enumerates emitters through adjacent AE2 cables/machines
- Does not consume wireless channels (`getRequestedChannels()=0`)

## Usage

1. Craft the Stock Monitor Terminal
2. Place it anywhere in your base (power-free, no energy required)
3. To manage emitters in a wireless network, right-click to open the GUI, click "Connect AE", and select a wireless network to bind
4. For adjacent network emitters only, simply place the terminal next to AE2 cables
5. Switch pages with the left tabs (Emitters / Covers)
6. Click any entry to open an edit panel; modifications are written back automatically

## Crafting

Iron Ingot ×4 + Glass Pane ×2 + Redstone ×2 + Basic Circuit (3×3 recipe).

## Notes

- The terminal itself is power-free and consumes no energy
- Emitter enumeration requires the terminal to be connected to an AE2 network (wireless or adjacent)
- The cover list reads the global registry directly and needs no AE connection, but it is filtered by the network the terminal is bound to
- After modifying a cover's configuration, the cover takes effect in the next detection cycle (10 ticks)
- **Upgrading from an older version**: covers already registered in your save get their target identity backfilled automatically once their chunk loads, after which they show localized names and icons; until then they keep the old record (possibly an English name, no icon). Nothing crashes and no data is lost
- The terminal does not consume wireless channels; it is only used for network selection and binding

## Related Features

- Stock Monitor Cover (item) — installation and configuration of individual covers
- Universal Maintenance Hatch — parallel/speed settings
