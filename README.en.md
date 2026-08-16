# AE2 QoL

> **English** | [简体中文](README.md)

An **AE2 quality-of-life enhancement mod** for GTNH: push NEI recipes into AE pattern terminals with one click, extract AE network items directly from the NEI panel, view each item's stock and craftability in the AE network, and wirelessly transmit AE networks.

Compat: GTNH 2.9.0-beta-1 (Minecraft 1.7.10) | Current version: **3.1.2** | Author: wztwzt

---

## 📦 Installation

1. Put `AE2-QoL-3.1.2.jar` into `.minecraft/mods/`
2. Make sure dependencies are installed: AE2 (`rv3-beta-977-GTNH`), ae2fc (`1.5.88-gtnh`), NotEnoughItems (NEI)
3. Launch the game. Config is generated under `config/`

**Config files** (all in `config/ae2_qof/`):

| File | Purpose |
|---|---|
| `settings.json` | NEI overlay toggle (`nei_overlay_enabled`, also switchable via `/apu-overlay` command or the OV button in pattern terminals) |
| `remembered_providers.json` | Remembered "recipe → provider" mappings for auto-upload (editable, format: recipe name → provider name) |
| `recipe_names.json` | User recipe mapping table (bundles 47+ default GTNH mappings in the jar) |

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

Same appearance as the native AE2 IO port, but **transfers 1024× more items per operation** (adjustable via `exIOPortTransferContentsRate` in `config/<mod>.cfg`). Recipe: `[Iron][Glass][Iron] / [Redstone][Diamond][Redstone] / [Iron][Glass][Iron]`.

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
- Persisted to `config/ae2_qof/settings.json`

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