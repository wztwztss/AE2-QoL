---
navigation:
  title: Universal Maintenance Hatch
  parent: index.md
  icon: ae2_qof:universal_maintenance_hatch
item_ids:
  - ae2_qof:universal_maintenance_hatch
author: wztwzt
---

# Universal Maintenance Hatch

A GT hatch for multiblock maintenance slots that provides **three capabilities** in one: maintenance bypass, wireless energy, and circuit-board parallel mapping. Replaces maintenance hatch + energy hatch + parallel setup with a single block.

## Three Capabilities

### 1. Maintenance Bypass

- All multiblocks **never have maintenance issues** (Mixin globally injects `shouldCheckMaintenance()` to return false)
- No more manual wrench repairs; machines always run at peak efficiency
- Globally active as soon as this mod is installed, regardless of whether the hatch is placed

### 2. Wireless Energy

- **Binds to the placer's UUID** on placement
- Periodically pulls EU from the global wireless energy network to local storage
- EU must be injected into the wireless network first (via wireless output hatches, generators, etc.)
- Multiblocks can draw power directly from this hatch

### 3. Circuit Board Parallel Mapping

- Insert a GT circuit board to set the parallel count
- Formula: `parallel = 4^level`
  - LV Circuit = 4 parallel
  - MV Circuit = 16 parallel
  - HV Circuit = 64 parallel
  - EV Circuit = 256 parallel
  - IV Circuit = 1024 parallel
  - LuV Circuit = 4096 parallel
  - ZPM Circuit = 16384 parallel
  - UV Circuit = 65536 parallel

## Usage

1. Craft the Universal Maintenance Hatch
2. Place it in the multiblock's **maintenance slot** (where the maintenance hatch would go)
3. For wireless power, ensure the wireless network has EU and the placer is bound
4. For parallel setup, insert the appropriate GT circuit board in the hatch GUI

## Crafting

Iron Ingot ×4 + Glass ×2 + Redstone ×2 + LV Circuit Board (3×3 recipe, see recipe tab).

## Notes

- Maintenance bypass is a global Mixin — all multiblocks become maintenance-free once this mod is installed
- Wireless energy requires a Wireless Output Hatch to inject EU; the hatch alone does not generate power
- Parallel count is capped by the machine's own maximum parallel; excess is ignored
- The hatch itself needs EU to operate; machines will stop if the wireless network runs dry
