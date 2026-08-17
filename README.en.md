# AE2 QoL

> **English** | [简体中文](README.md)

An **AE2 quality-of-life enhancement mod** for GTNH: push NEI recipes into AE pattern terminals with one click, extract AE network items directly from the NEI panel, view each item's stock and craftability in the AE network, and wirelessly transmit AE networks.

Compat: GTNH 2.9.0-beta-1 (Minecraft 1.7.10) | Current version: **3.4.0** | Author: wztwzt

---

## 📦 Installation

1. Put `AE2-QoL-3.4.0.jar` into `.minecraft/mods/`
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

Same appearance as the native AE2 IO port, but **transfers 1024× more items per operation** (adjustable via `io_port_rate` in `config/ae2_qof/settings.json`, hot-reloaded automatically). Recipe: `[Iron][Glass][Iron] / [Redstone][Diamond][Redstone] / [Iron][Glass][Iron]`.

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

- Command: `/apu-overlay` (toggle the AE overlay on NEI recipe pages/bookmarks)
- Or the **OV** button in pattern terminal GUIs
- Persisted to `config/ae2_qof/settings.json` (`nei_overlay_enabled`)

### 13. Smart Doubling

A new **Smart Doubling** checkbox (cycle-arrow icon) on the left of the **ME Interface** GUI. When enabled, the crafting CPU pushes **N rounds** of a pattern's inputs to the interface at once, so the machine processes N rounds before refilling — no more one-round-at-a-time refills, greatly speeding up GT pipelines.

- **N is determined by**: `N = min(remaining craft rounds, smart_doubling_max_rounds, extractable per input slot / per round, power-payable rounds, max rounds the machine can accept)`; GT/PH hatches are probed CPU-side, ProgrammableHatches dual-input hatches self-limit by internal buffer space (`pushPatternMulti`)
- **Default cap**: 0 = unlimited (dispatch all remaining rounds at once; `smart_doubling_max_rounds` in `config/ae2_qof/settings.json`, range 0–2147483647, hot-reloaded automatically or editable via the in-game Config page)
- **Safety boundaries** (falls back to one-round behavior, identical to vanilla): fake crafting, fluid interfaces, blocking/conditional blocking mode, interface with pending un-pushed items, GT machines that accept plans directly (`acceptsPlans`); when materials/power are short, N is **clamped to the extractable rounds** instead of abandoning the push
- **Energy**: charged once for the actually-pushed rounds; outputs and remaining rounds are accounted for the actual count — no overproduction or item loss
- **3.3.5**: fixed GT/PH pattern input machines being "completely ineffective + unable to dispatch items" after enabling Smart Doubling — the power gate wrongly used N× total power with no fallback (skipped the medium), and the material probe required a strict full match, silently degrading N to 1
- **3.3.6**: `smart_doubling_max_rounds` now defaults to 0 = unlimited (GT hatches have unbounded buffer, so all remaining rounds are dispatched at once; PH hatches self-limit by buffer space, ME interfaces cap at the adjacent machine's capacity); added an in-game config page (Mods → AE2 QoL → Config) where OP changes apply instantly and sync to all clients
- **3.3.7**: performance fix — huge orders (e.g. 1T) no longer freeze the game (O(1) power clamp via a single query, batched waiting-output accounting, int-overflow clamp); fixed PH/GT hatch tooltip line breaks; the Config page now shows each setting's range and gained a "Mapping editing" page (recipe name mapping `recipe_names.json` and remembered providers `remembered_providers.json` are both editable client-side with hot disk-write)

---

## 🕐 Planned

- **F: Pattern + Interface dual-page all-in-one terminal** — merging pattern encoding and interface management into one GUI with two switchable pages. Shelved in 3.0.0 because the native AE2 interface terminal already covers it; may be re-released in a later version.

---

## 📄 Other Docs

| Doc | Description |
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