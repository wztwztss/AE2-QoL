---
navigation:
  title: Adaptive Energy Grid
  parent: index.md
  icon: gregtech:gt.blockmachines:32106
item_ids:
  - gregtech:gt.blockmachines:32106
  - gregtech:gt.blockmachines:32102
  - gregtech:gt.blockmachines:32103
  - gregtech:gt.blockmachines:32104
  - gregtech:gt.blockmachines:32105
  - ae2_qof:network_data_stick
author: wztwzt
---

# Adaptive Energy Grid

A GT hatch-based **adaptive energy network**. Configure a network ID via the Data Stick, and all bound hatches share the same voltage/amperage/frequency settings. Hatches auto-match the terminal's configured tier — no manual per-hatch adjustment needed.

## Components

| Component | ID | Function |
|---|---|---|
| Adaptive Terminal | 32106 | Network controller; right-click to open GUI |
| Adaptive Input Hatch | 32102 | Energy input, auto-matches voltage tier & amperage |
| Adaptive Laser Source Hatch | 32103 | Laser output, auto-matches tier |
| Adaptive Dynamo Hatch | 32104 | Power output (EU output), auto-matches tier |
| Adaptive Laser Target Hatch | 32105 | Laser receiver, auto-matches tier |
| Network Data Stick | — | Config tool: write/read/bind network config |

## Terminal GUI (5 tabs: Status / Adaptive Settings / Frequency / Grid Monitor / Hatch List)

### Status

- Shows the current network ID and the loaded count/voltage/amperage of the four hatch classes
- Use the **Hatch List** tab to Locate a target (left-click highlights, Shift teleports; permission required)

### Adaptive Settings (network hatch slots)

- Set the target voltage tier and target amperage; every bound hatch auto-adapts to them
- Put **64 items of the same hatch class** into each of the four slots (dynamo / energy / laser source /
  laser target) to copy that class's specs onto the whole network
- **Where the specs come from (3.32.0-fix1)**: this mod's own four classes use their **factory rating**
  (1A for energy/dynamo, 256A for laser); TecTech laser source/target hatches use their **rating field**
  (`maxAmperes`), not the runtime method that applies GT's x1.25 rule
- **Manual wins**: tier/amperage you change on the terminal is only recomputed when a slot's contents actually
  change, so it is no longer overwritten periodically
- **Identity is category-based since 3.32.0-fix2**: the slots accept whole hatch families, not just this mod's four
  classes. ENERGY = the `MTEHatchEnergy` family (GT tiered energy hatches, GT wireless energy hatches) plus
  TecTech's multi-amp energy family (4/16/64A energy, wireless energy, laser target); DYNAMO = the
  `MTEHatchDynamo` family (GT tiered dynamo, TecTech multi-amp dynamo, TecTech laser source);
  LASER SOURCE = this mod's laser hatch + TecTech laser source; LASER TARGET = this mod's laser target hatch +
  TecTech laser target. **Machine hulls and GT tanks are rejected** (older builds let a hull through because
  `MTEBasicHull.maxEUInput()` is `V[mTier] > 0`)
- The full list of accepted items with IDs is in the repository document `docs/ADAPTIVE_HATCH_WHITELIST.md`

### Frequency Settings

- Set laser transmission frequency
- Laser source and target hatches must match frequency to transmit

### Hatch List (reworked in 3.32.0)

- **One row per host multiblock instead of one row per hatch.** Host names and icons are resolved client-side
  from the MTE id, so they stay localized on a dedicated server
- Each row leads with a **Running / Idle** tag plus the **measured flow** (what our own hatches actually carry;
  0 when idle); the second line is `N hatch(es) · In/Out · rated power`
- ⚠️ **GT's recipe EUt (`mEUt`) is not shown as the draw**: it is recipe-scoped and **is not cleared when the
  machine stops**; it only appears as "Recipe" on the second line while the host is actually running.
  An idle host reads "Idle · measured 0 EU/t" (since 3.32.0-fix3)
- Rows are sorted by **measured flow**, so machines that are actually working come first and idle ones sink
- Hover a row to expand a per-hatch breakdown (class, tier, amperage, rated/measured, coordinates)
- Hatches whose host cannot be identified **do not disappear**; they are grouped under "Unknown host"
- Locate targets the **host**: left-click highlights, Shift teleports next to it (a safe standing spot is
  searched; if none is found the teleport is cancelled with a notice)
- The **player inventory** sits at the panel bottom (since 3.32.0-fix3 the panel reserves room for it, so it no
  longer covers the list)
- **TecTech laser hatches are included in both statistics and configuration**: only those on hosts that already
  run this network's hatches are counted, and their amperage is **rewritten periodically** (their amperage field
  is not persisted). ⚠️ Known limits: the tier cannot be changed (the item decides it), and the rewrite overrides
  amperage you set by hand in TecTech's own GUI

## Data Stick Usage

| Action | Effect |
|---|---|
| Shift + right-click terminal | Initialize/write network config to the stick |
| Right-click hatch | Bind the hatch to the network in the stick |
| Right-click terminal | Read config from stick and update terminal |

## Quick Start

1. **Place terminal**: Place an Adaptive Terminal, right-click to open GUI
2. **Configure network**: Set voltage tier and amperage in "Adaptive Settings"
3. **Write to stick**: Shift + right-click the terminal to write config to the Data Stick
4. **Bind hatches**: Right-click each hatch with the stick; they auto-join the network
5. **Verify**: Right-click the terminal, check the "Status" tab for all bound hatches
6. **Operate**: Hatches auto-adapt to the configured V/A and start transferring energy

## Multiplayer & Permissions

- Each network is identified by the **placer's UUID**
- Only the network owner can modify settings and teleport
- Team members can share the network (authorized in terminal)
- Supports **cross-dimensional** hatch binding and viewing

## Monitoring

The terminal "Status" tab shows in real-time:

- Each hatch's **real load** (EU/t + V + A)
- Hatch owner (player name)
- Dimension and coordinates
- Estimated time to energy depletion
- Color-coded Tier indicators (different colors per voltage tier)

## Crafting

See NEI recipe tab for each component.

## Notes

- The terminal itself does not transfer energy; it only configures and monitors
- Hatches must be bound to a terminal network before they auto-adapt
- Hatches stop and show a warning when voltage tier mismatches
- Laser transmission requires matching frequency on source and target
- Bindings persist across save/load — no need to rebind after restarting
