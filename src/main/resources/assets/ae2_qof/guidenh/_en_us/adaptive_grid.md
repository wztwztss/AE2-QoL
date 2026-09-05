---
navigation:
  title: Adaptive Energy Grid
  parent: index.md
  icon: ae2_qof:adaptive_terminal
item_ids:
  - ae2_qof:adaptive_terminal
  - ae2_qof:adaptive_input_hatch
  - ae2_qof:adaptive_laser_source_hatch
  - ae2_qof:adaptive_dynamo_hatch
  - ae2_qof:adaptive_laser_target_hatch
  - ae2_qof:network_data_stick
author: wztwzt
---

# Adaptive Energy Grid

A GT hatch-based **adaptive energy network**. Configure a network ID via the Data Stick, and all bound hatches share the same voltage/amperage/frequency settings. Hatches auto-match the terminal's configured tier — no manual per-hatch adjustment needed.

## Components

| Component | ID | Function |
|---|---|---|
| Adaptive Terminal | 32100 | Network controller; right-click to open GUI |
| Adaptive Input Hatch | 32102 | Energy input, auto-matches voltage tier & amperage |
| Adaptive Laser Source Hatch | 32103 | Laser output, auto-matches tier |
| Adaptive Dynamo Hatch | 32104 | Power output (EU output), auto-matches tier |
| Adaptive Laser Target Hatch | 32105 | Laser receiver, auto-matches tier |
| Network Data Stick | — | Config tool: write/read/bind network config |

## Terminal GUI (3 Tabs)

### Status

- Shows current network ID and list of bound hatches
- Each hatch shows: type, coordinates, dimension, current V/A, load status
- Click a hatch to **teleport** (permission required) or **highlight** (red outline in world)

### Adaptive Settings

- Set target voltage tier (LV/MV/HV/EV/IV/LuV/ZPM/UV/...)
- Set target amperage (1A/2A/4A/8A/16A)
- All bound hatches auto-adapt to the configured values

### Frequency Settings

- Set laser transmission frequency
- Laser source and target hatches must match frequency to transmit

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
