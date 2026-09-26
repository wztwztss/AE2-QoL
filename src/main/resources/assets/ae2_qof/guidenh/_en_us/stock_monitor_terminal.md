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

## Core Features

### 1. Emitter Central Management

- Automatically enumerates all standard Level Emitters (`PartLevelEmitter`) in the current AE2 network
- Displays each emitter's monitor target, threshold, and type (Item/Fluid/Energy)
- Click an entry to open an edit panel and modify the threshold directly
- Energy-type emitters are read-only (AE2 native limitation)
- Modifications require AE2 network BUILD permission

### 2. Cover Central Management

- Automatically discovers all installed Stock Monitor Covers (global registry, cross-dimensional)
- Displays each cover's monitor target, threshold, mode (Below/Above), and online status
- Click an entry to open an edit panel and modify threshold and mode directly
- Covers are automatically removed from the list when dismantled
- Unloaded chunks are marked as offline (gray)

### 3. Dual-Mode Networking

- **Nexus Wireless**: Click the "Connect AE" button to open the Nexus native wireless network selection panel and bind to any wireless network (same operation as covers)
- **Neighbor Direct**: When not bound to a wireless network, automatically enumerates emitters through adjacent AE2 cables/machines
- Does not consume wireless channels (`getRequestedChannels()=0`)

## Usage

1. Craft the Stock Monitor Terminal
2. Place it anywhere in your base (power-free, no energy required)
3. To manage emitters in a wireless network, right-click to open the GUI, click "Connect AE", and select a wireless network to bind
4. For adjacent network emitters only, simply place the terminal next to AE2 cables
5. The upper half of the GUI shows the emitter list, the lower half shows the cover list
6. Click any entry to open an edit panel; modifications are written back automatically

## Crafting

Iron Ingot ×4 + Glass Pane ×2 + Redstone ×2 + Basic Circuit (3×3 recipe).

## Notes

- The terminal itself is power-free and consumes no energy
- Emitter enumeration requires the terminal to be connected to an AE2 network (wireless or adjacent)
- The cover list does not depend on network connection; it reads directly from the global registry
- After modifying a cover's configuration, the cover takes effect in the next detection cycle (10 ticks)
- Energy-type emitters are read-only; thresholds cannot be modified via the terminal
- The terminal does not consume wireless channels; it is only used for network selection and binding

## Related Features

- Stock Monitor Cover — installation and configuration of individual covers
- Universal Maintenance Hatch — parallel/speed/thread settings
