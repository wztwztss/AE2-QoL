# AE2 QoL

> **English** | [简体中文](README.md)

An **AE2 quality-of-life enhancement mod** for GTNH: push NEI recipes into AE pattern terminals with one click, extract AE network items directly from the NEI panel, view each item's stock and craftability in the AE network, and wirelessly transmit AE networks.

Compat: GTNH 2.9.0-beta-1 (Minecraft 1.7.10) | Current version: **3.8.0** | Author: wztwzt

---

## 📦 Installation

1. Put `AE2-QoL-3.8.0.jar` into `.minecraft/mods/`
2. Make sure dependencies are installed: AE2 (`rv3-beta-977-GTNH`), ae2fc (`1.5.88-gtnh`), NotEnoughItems (NEI)
3. Launch the game. Config is generated under `config/`

**Config files** (all in `config/ae2_qof/`, **hot-reloadable**: edits take effect within ~1 second without a restart):

| File | Purpose |
|---|---|
| `settings.json` | Unified config: `io_port_rate` (Enhanced IO Port transfer multiplier, default 1024), `smart_doubling_max_rounds` (Smart Doubling max rounds, default 0 = unlimited, range 0–2147483647), `nei_overlay_enabled` (NEI overlay toggle) |
| `remembered_providers.json` | Remembered "recipe → provider" mappings for auto-upload (editable, format: recipe name → provider name) |
| `recipe_names.json` | User recipe mapping table (bundles 47+ default GTNH mappings in the jar) |

**Admin commands (require OP)**:

- `/ae2qof reload` — immediately hot-reloads `settings.json` + `recipe_names.json` (no need to wait the 1s auto-reload)
- `/ae2qof status` — shows the currently active config values
- On a dedicated server these require **OP** (permission level 2); in single-player / LAN the host is OP by default and can use them directly
- `/apu-overlay` still toggles the NEI overlay (no OP required)
- ⚠️ `/apu-overlay` is registered client-side and only exists in single-player / LAN; **on a dedicated server use the OV button inside the terminal GUI instead**
- The NEI overlay toggle is a **purely local client setting** (independent per player on multiplayer, never overwritten by server config sync)

**In-game config GUI**: pause menu → Mods → AE2 QoL → **Config**. Edit `io_port_rate` / `smart_doubling_max_rounds` (0 = unlimited) / `nei_overlay_enabled` and apply immediately (requires OP on multiplayer; changes sync to all clients and are written to the server's `settings.json`).

---

## ✨ Feature Overview

### 1. NEI Pattern Upload / Recall / Swap (4 buttons in pattern terminal GUI)

In the **AE2 standard/advanced pattern terminal**, 4 buttons appear at the top-right: **↑ Upload** (auto-upload the encoded pattern in the output slot to a network interface/assembler), **← Recall** (fetch the last matching encoded pattern back into the output slot), **⇄ Swap** (swap primary/secondary outputs), **OV** (toggle the AE overlay on NEI recipe pages/bookmarks).

Uploads automatically match providers by recipe (three strategies: unique provider → remembered provider → manual selection GUI). Supports standard encoded patterns, ultimate encoded patterns, and ae2fc fluid encoded patterns.

### 2. NEI Panel Item Extraction / Crafting

- **Shift + Left-click** on an NEI item: extract one stack from the AE network into your inventory (auto-placed); if no stock but craftable, jumps to crafting
- **Middle-click** on an NEI item: open the AE2 craft amount confirmation GUI (requires a crafting recipe for that item in the network)

Results are announced in chat: success / not found / not craftable / inventory full.

### 3. NEI Item Tooltip

- **Cyan count**: the item's stock in the AE network (supports K/M/G/T/P/E scientific notation)
- **Green +Craft**: the item is currently craftable via the AE network
- **Fluid direct display**: fluid containers (buckets, cells, etc.) show the fluid amount itself (e.g. `4.5P mB Distilled Water`) instead of container count

### 4. NEI Bookmark Panel Count Overlay

Items in the NEI bookmark panel on the left show a network stock / craftable marker at their bottom-right.

### 5. Crafting Completion Notification

When an AE crafting CPU finishes a task, a **completion banner** (item icon + count) fades out at the top-right of the screen. Requires holding the corresponding network key (wireless/terminal) in your inventory.

### 6. Crafting Replan

A **Replan** button in the AE2 **craft confirm GUI**: re-allocates the current simulated crafting task's process with one click.

### 7. Enhanced IO Port (`ex_io_port`)

Same appearance as the native AE2 IO port, but **transfers 1024× more items per operation** (adjustable via `io_port_rate` in `config/ae2_qof/settings.json`, hot-reloaded automatically). 

### 8. Infinite Water & Lava Cell

Provides **nearly infinite water and lava** (each ≈ 4.5×10^15) when placed in an ME drive. Recipe: water bucket + lava bucket placed side by side, middle and side slots empty.

### 9. Wireless Transceiver + Wireless Connector

- **Wireless Transceiver** (block): connects an ME network's items/fluids to a wireless channel; right-click to open the GUI for channel & mode setup (sender/receiver)
- **Wireless Connector** (tool): binds ME devices (interfaces, terminals, machines, etc.) to a wireless channel for remote wireless connection

**Usage**: place two transceivers (one sender, one receiver, same channel) → both networks connect wirelessly; **Shift+right-click** the sender with a connector to bind the channel; **right-click** any ME device to join the network, right-click again to unbind. Supports **cross-dimension** connections. The transceiver GUI supports adding/removing channels, switching modes, and highlighting connected blocks (red outline).

### 10. Quartz Knife Name Copy

Hold a **Quartz Knife** and **Shift+right-click** a block/AE part/GT machine in the naming screen → automatically writes the target name into the knife's name and copies it to the clipboard.

### 11. F-Key Search Fill

In AE2 / ae2fc terminal GUIs, hover over an item and press **F** → automatically fills that item's name into the search box.

### 12. NEI Overlay Toggle

- Command: `/apu-overlay` (toggle the AE overlay on NEI recipe pages/bookmarks; single-player / LAN only — on dedicated servers use the OV button)
- Or the **OV** button in pattern terminal GUIs
- Persisted to `config/ae2_qof/settings.json` (`nei_overlay_enabled`, purely local client setting, independent per player)

### 13. Smart Doubling

A new **Smart Doubling** checkbox (cycle-arrow icon) on the left of the **ME Interface** GUI. When enabled, the crafting CPU pushes **N rounds** of a pattern's inputs to the interface at once, so the machine processes N rounds before refilling — no more one-round-at-a-time refills, greatly speeding up GT pipelines.

- **N is determined by**: `N = min(remaining craft rounds, smart_doubling_max_rounds, extractable per input slot / per round, power-payable rounds, max rounds the machine can accept)`; GT/PH hatches are probed CPU-side, ProgrammableHatches dual-input hatches self-limit by internal buffer space (`pushPatternMulti`)
- **Default cap**: 0 = unlimited (dispatch all remaining rounds at once; `smart_doubling_max_rounds` in `config/ae2_qof/settings.json`, range 0–2147483647, hot-reloaded automatically or editable via the in-game Config page)
- **Safety boundaries** (falls back to one-round behavior, identical to vanilla): fake crafting, fluid interfaces, blocking/conditional blocking mode, interface with pending un-pushed items, GT machines that accept plans directly (`acceptsPlans`); when materials/power are short, N is **clamped to the extractable rounds** instead of abandoning the push
- **Energy**: charged once for the actually-pushed rounds; outputs and remaining rounds are accounted for the actual count — no overproduction or item loss
---

### 14. Pattern + Interface all-in-one terminal (standalone block "Pattern & Interface Terminal")

A new wired block that merges the **pattern encoding panel** with the **interface management list** in one GUI — manage interface patterns and encode patterns in the same screen, no need to open a separate Pattern Terminal. Place the block and right-click to open (requires an AE network).

- **Panel style**: native AE2Things style — Crafting/Processing tabs, native icon buttons (Encode/Clear/x2/Substitute/Be-Substitute/Invert), and a 4×4×2-page grid in Processing mode (scrollbar pages; the Invert button flips the input/output column direction); Crafting mode shows a 3×3 grid + result slot
- **Top buttons**: `↑` (Upload — auto-upload the encoded pattern to an interface/assembler on the network; Shift+click forces the provider selection screen) / `←` (Recall — take back the last matching pattern) / `⇄` (Swap — rotate primary/secondary outputs) / `AM` (upload to a GTNL Assembler Matrix; shown in Crafting mode when GTNL is installed) / `OV` (NEI overlay toggle)
- **Usage**: put items directly into the panel to form a recipe, then press Encode; Clear empties the panel; x2 doubles the output ratio; Processing recipes support Substitute and Be-Substitute
- **Pattern read-back** (3.6.0): put an encoded pattern back into the OUT slot to auto-decode it into the panel — inputs/outputs/mode/substitution all restored; supports GT ultimate patterns and fluids (restored as GT display items); provider mapping is preserved for re-editing
- **Editing snapshot** (3.6.0): closing the terminal automatically saves all panel slots and the mode into the block NBT; reopening restores everything
- **PH Programming Toolkit MK.II support** (3.6.0): with the toolkit in your inventory, NEI recipe transfers automatically replace non-consumed catalysts with programming circuits (zero-circuit fallback in fallback mode); zero impact when PH is not installed
- **Middle-click amount editor**: middle-click a panel slot to open an amount editor (+1/+10/+100/+1000 and ×2/×8/×64/×512, toggled with Shift; no upper limit); Shift+middle-click renames items; output slots cannot be edited
- **Interaction**: panel slot clicks and drag-place behave like the native AE2 terminal; the scrollbar supports click and wheel paging; clicks inside the panel take priority over the interface list
- **Cable part form** (3.7.0): a new item, "Pattern & Interface Merged Terminal Part", can be mounted on any face of an AE2 cable (looks like the native ME terminal part; requires a channel and idle power); its GUI is identical to the block form. Craft: iron ingot + merged terminal block
- **Wireless handheld form** (3.7.0): a new item, "Wireless Merged Terminal", opens the full all-in-one GUI anywhere, **across dimensions**; binding works like the vanilla wireless terminal — put it into the ME Security Station encode slot; if the station is removed the terminal stops working; power-free by design; network access permissions are still enforced by the security station's biometric cards; craft: diamond block + redstone + iron ingots etc.

---

## 🕐 Planned

- **21504 SuperDualInputHatchME as a CPU crafting medium research** — research on hooking ProgrammableHatches' SuperDualInputHatch into the crafting CPU as a crafting medium.

---

## 📄 Other Docs

| Doc |描述|
|---|---|
| [Changelog](docs/CHANGELOG.md) | User-facing changelog (by version) |
| [CHANGELOG.md](CHANGELOG.md) | Developer changelog, known issues, rollback guide |
| [CREDITS.md](CREDITS.md) | Code & texture sources, license review |

---

## 🙏 Credits

This mod is adapted from [**AE2-Auto-Pattern-Upload**](https://github.com/GaLicn/AE2-Auto-Pattern-Upload/) (by GaLicn). The original upload and F-key search features were taken directly from the original project and adapted for GTNH 2.9.0. Many thanks to the original author.

During development, code and textures from many other mods were referenced. Main sources are listed below (full details and license review in **`CREDITS.md`**):

### Code References
- **Applied Energistics 2 GTNH** (`Applied-Energistics-2-Unofficial`) — AE network, storage, grid, and crafting logic almost entirely call its API and internal classes; the infinite water/lava cell extends `AEBaseInfiniteCell` and reuses the `CreativeCellInventory` mechanism
- **AE-Wireless-Transceiver** (by 小飘 / mynamexiaopiao) — the entire wireless transceiver block/terminal/connector implementation (permission confirmed via Bilibili)
- **AE2FluidCraft-Rework** (ae2fc) — fluid patterns (`ItemFluidEncodedPattern`), pure fluid identification
- **AE2Things** (asdflj) — reference for enhanced IO port, infinite fluid cell, creative cell concepts
- **NotEnoughItems** (NEI) — NEI recipe-page AE badges, tooltip injection
- **Waila** — highlight support for blocks such as the wireless transceiver
- Others referenced: `GT-Not-Leisure`, `GT5-Unofficial`, `GTLCore`, `Programmable-Hatches`, `ExtendedAE_Plus`, `ExampleMod1.7.10` (GTNH template), etc.

### Texture References
- **AE-Wireless-Transceiver** (by 小飘 / mynamexiaopiao; textures by 麦淇淋 / @麦淇淋) — wireless transceiver block textures, `de.png` / `de1.png` / `widgets.png`, wireless connector textures (permission granted by the authors)
- **Applied Energistics 2** — `guis/states.png`, `gui/wireless.png`, etc. (CC BY-NC-SA 3.0, non-commercial use)
- **AE2Things** — `ex_io_port*.png` enhanced IO port textures, infinite fluid cell concept
- **Minecraft vanilla** — `textures/gui/widgets.png` (runtime reference)
- `logo.png` is self-drawn

### Copyright & Compliance
This repository is **for personal archival only, not for public distribution**. Using referenced code and textures for personal backup purposes greatly reduces copyright risk. Before any public distribution, please re-check the latest LICENSE of the original repositories (see the license review table in `CREDITS.md`).

---

## 🛠 Developer Info

- Build: `./gradlew build -x spotlessJava -x spotlessCheck`
- Developer changelog, known issues, and rollback guide: see **`CHANGELOG.md`**
