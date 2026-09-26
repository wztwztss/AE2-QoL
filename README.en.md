# AE2 QoL

> **English** | [简体中文](README.md)

An AE2 quality-of-life mod for **Minecraft 1.7.10 / GT New Horizons**: NEI pattern uploading, network stock and crafting hints, merged terminals, wireless AE links, and GT energy/stock-management tools.

**Author: wztwzt · Current source version: 3.21.2 · Reference pack: GTNH 2.9.0-beta-3**

This repository is for personal archival and is not currently offered for distribution. See [CREDITS.md](CREDITS.md) for attribution and licensing records. Feature descriptions are not a claim that every integration has passed in-game testing.

## What's new in 3.21.0 (Stock Monitor Terminal: highlight / teleport + UI + localization)

- **New: two action buttons per row — Highlight and Teleport** (same division of labour as the
  Adaptive Energy Grid terminal). Highlight draws a **glowing box for 10 seconds** (the renderer skips
  other dimensions; a chat hint is given when the target is elsewhere). Teleport places the player on a
  **standable spot next to/above** the target and **works across dimensions** (reusing the adaptive
  terminal's custom `Teleporter` that overrides `placeInPortal`, avoiding the old
  "search/build a nether portal" bug). Both buttons are **client-side callbacks** that only send
  coordinates; the server re-resolves the target (a cover must really be attached there; an emitter
  requires an AE host block) and enforces a **session + AE network BUILD permission** check.
- **UI polish**: rows are now "name on the left (left aligned), Chinese value on the right"; type and
  mode are shown in Chinese; hovering a row shows the full name plus `Ddim [x, y, z]` (covers also show
  the face and online state); title colour tweaks; lists remain scrollable with no row cap.
- **Localization fix**: the item name key `gt.blockmachines.stock_monitor_terminal.name` was missing
  (NEI showed the English "Stock Monitor Terminal"), and `getLocalName()` is now overridden as a second
  safeguard; row abbreviations plus hardcoded `Close`, `Type:`, `(unset)` and `Emitter` were moved to
  language keys.
- Also: the terminal now advances the "highlight auto-clear" queue every tick, so highlights expire on
  time even in saves without the Adaptive Energy Grid terminal.

- **3.20.3 fix: the Stock Monitor Terminal (32107) GUI showed only its two section headers**
  ("stock monitor covers" / "AE standard emitters") — no lists, no connect button, no input fields,
  and no stock readings; it had **never worked**. There were two root causes, the second hidden:
  1. The widgets were created behind `if (isServer)`, but **MUI2 builds every panel on both sides and
     renders the client-side tree**, where that flag is always false — so the status row, the connect
     button and both lists simply did not exist on the client;
  2. The emitter scan used the wrong AE2 API: `Grid.getMachines()` returns a set of **`IGridNode`**
     (`IMachineSet extends IReadOnlyCollection<IGridNode>`), and the old code tested the *node* with
     `instanceof PartLevelEmitter`, which can never match — the list was always empty.
  The panel is now built identically on both sides, the variable-length lists are rendered from a
  server-side snapshot through `GenericListSyncHandler` + `DynamicSyncedWidget` (the same pattern Nexus
  itself uses), edits flow through SyncValues, a fallback network selector was added for the case where
  Nexus is unavailable, and the old "show only 5 rows" cap is gone (scrollable, all rows).

- **3.20.2 fix**: four in-game guide (GuideNH) pages never showed an icon — the log repeated
  `Couldn't find icon item ae2_qof:...`. Those pages' `icon:` / `item_ids:` used invented names, but the
  machines are **GregTech machines**: their real registry name is `gregtech:gt.blockmachines` plus meta
  (the MTE ID), e.g. the Universal Maintenance Hatch is `gregtech:gt.blockmachines:32000`. This release
  rewrites all such ids on 5 pages (including one that never logged an error: the AE2 cutting-knife page,
  whose real id is `appliedenergistics2:item.ToolCertusQuartzCuttingKnife`) in both languages, and
  cross-audits every declared id against this mod's actual registry names.

- **3.20.1 fix**: in 3.20.0 the optional-dependency guard used PH's **package prefix** (`proghatches`)
  instead of its real **modid**, so the guard was always false — the item was never registered, could not be
  found in NEI or the creative tab, and **no log line was written at all**. It now uses the real modid
  `programmablehatches`, plus a second "anchor class resolves" check and a log line on the skip path
  (every branch now prints exactly one line, so the outcome is immediately identifiable).

- **New machine: Programmable Crafting Input Buffer MK.III** — an **expanded clone** of
  ProgrammableHatches' Programmable Crafting Input Buffer: pattern slots go from 36 to **144** (4x),
  and the pattern window becomes a **scrollable 9-column x 9-visible-row grid** (scrolling over all 16 rows),
  docked with screen-size clamping. Input structure matches the MK.II (32 item + 32 fluid per order,
  24 isolated buffers).
- **It only exists when ProgrammableHatches is installed**: every PH type is confined to the body of
  `ph/PhIntegration.register()`, whose first statement is `Loader.isModLoaded("proghatches")`.
  Without PH the item is never registered, never shown in the creative tab, and no PH type is loaded
  (the same pattern as the GuideNH integration).
- **Obtaining it**: shaped crafting-table recipe (original buffer + 4x Master Circuit + 4x Advanced Circuit),
  and it also appears in the AE2 QoL creative tab.
- **Works with this mod's existing features out of the box**: pattern upload / recall / interface terminal
  all read capacity from `IInterfaceViewable.rows() * rowSize()`, so those three code paths needed no change
  for 144 slots; the inner class is registered with AE2's `InterfaceTerminalRegistry`
  (AE2 looks machines up by **exact class name** — skipping that registration would hide it from both the
  AE2 interface terminal and this mod's pattern terminal).
- **Save compatibility**: NBT keys are identical to the original buffer, so the two items can be swapped
  freely; swapping back to the original buffer with more than 36 patterns makes slots 37+ unreachable
  (data is not lost, it stays in the NBT).
- **Implementation note**: PH hard-codes its capacity in the length of four arrays (its two constructors
  each contain four `bipush 36` instructions). This version replaces those arrays with 144 through an
  interface-style accessor mixin (`mixin/ph/MixinPatternDualInputHatchAccess`) and **does not touch PH
  itself** — PH's own buffer / MK.II / item-only variants keep their 36 slots.

### Previous release: 3.19.0-fix54 (includes the final fix52 fix)

- **In-world pick-block is now confirmed working on GTNH 2.9.0-beta-3.** The root cause was neither our mod
  nor AE2: the pack's **sciencenotleisure (SNL)** injects at the HEAD of vanilla
  `Minecraft.middleClickMouse()` and cancels it (`ClientUtils.onBeforePickBlock` runs its own 1000-block
  range pick and then unconditionally returns `true` when no entity is targeted). As a result GTNHLib's
  `PickBlockEvent` is never posted and **AE2 never sends `PacketPickBlock`**, so the server-side fallback
  (fix52) was never executed. The new `client/PickBlockCompatHandler` takes a **different trigger point**
  (Forge `InputEvent.MouseInputEvent`, unrelated to SNL and to the vanilla method) and re-sends that packet
  with the same preconditions as AE2's `handlePickBlock()`, plus an extra check that a wireless terminal is
  actually carried — otherwise the server would spam `PickBlockTerminalNotFound` on every middle-click.
  **Verified in game**: no stock + pattern opens the craft-amount screen (log `branch E` → `G7`), and once the
  item is in the inventory a further middle-click correctly falls through to vanilla (`branch B`).
- This release also folds in fix52's server-tick-thread deferral and the stock-check fallback fix;
  **all diagnostic instrumentation has been stripped** (failure branches now log a single WARN, normal
  pass-throughs log nothing).

### Previous fix51

- **Fixed "fluids in the Infinity Cell cannot be moved through an IO Port".** AE2's IO port picks only the
  **first** matching storage channel per cell (`TileIOPort.getInv` breaks on the first hit), while our
  Infinity Cell stores items + fluids in one cell — so the fluid channel never entered the transfer loop
  (items kept working, which made this easy to miss). The port now **also fans out the remaining channels**
  for that cell, so fluids move both ways; ordinary item cells, ae2fc fluid cells and other mods' cells are
  untouched (single-channel cells are skipped immediately). **Needs in-game confirmation**: fluid transfer
  in both directions, items unaffected, and an ordinary fluid cell behaving exactly as before.

### Previous fix50

- **Fixed the Universal Maintenance Hatch circuit slot rejecting every item.** GT 5.09.54 wired up a new
  MTE item-validation chain (`MTEItemStackHandler.isItemValid` → `MTEHatchMaintenance.func_94041_b` →
  `IsAutoMaintenanceInput(...)`); because our hatch is always constructed with `aAuto=false`, that chain
  returns `false` for slot 0, so the MUI2 slot widget refused everything. On 5.09.52 the chain did not exist,
  which is why it worked on b1. The fix allows only the tiered circuit boards into that slot and leaves
  everything else untouched. **Needs in-game confirmation**: insert/eject each tier's circuit board, `max`
  values in the GUI follow the tier, contents survive a save/load, and non-circuit items are still rejected.

### Previous fix49

- **All build dependencies realigned to the GTNH 2.9.0-beta-3 live versions**: AE2 `rv3-beta-1050`,
  GregTech `5.09.54.133`, NEI `2.8.130`, AE2FC `1.5.106`, ModularUI2 `2.3.88`, GTNL `0.2.7-pre3`,
  ProgrammableHatches `0.2.0p24`, GuideNH `1.3.29`, NotEnoughEnergistics `1.7.41`,
  BetterQuesting `3.8.84`, Thaumic Energistics `1.7.60`, Avaritia `1.99`.
  The upgrade exposed and fixed 6 real API breaks at compile time (IO/quest-detector render base class,
  pattern-terminal output-slot sync, interface suffix type). **Compiling against the old set was not the same as being aligned.**
- **fix48 · MTE ID handover + automatic save migration**: b3's fissionevolved occupies 32100/32101, so our
  terminals moved to Adaptive Grid **32106** and Stock Monitor **32107**. A read-time migration rewrites the
  old IDs only for NBT carrying our own keys, so placed terminals keep frequency, voltage tier and hatch bindings.
- **fix47 · Middle-click in the world**: with the item neither in the inventory nor in the network, but a
  crafting pattern available, middle-clicking a block opens the "how many to craft" screen; with network stock
  the native pick-up is unchanged; with neither, nothing happens as before.
- **fix46 · Fix transparent machines after enabling the resource pack**: the atlas "register once" switch
  conflicted with `registerIcons()` clearing and rebuilding its list; it now re-registers every call and adds a
  degenerate-UV fallback.
- **fix45/fix44 · v7 textures (option B)**: a Mixin appends the 25 v7 paths to the vanilla block-atlas
  `registerIcons()`, keeping per-face FRONT/TOP/SIDE art; without the pack the machines fall back to stock GT looks.
- **fix43 · Stock Monitor Cover stack limit 1 → 64**, with no change to thresholds, config NBT or installation logic.

### Earlier: fix42 tooltip change

- Prevent duplicate AE stock / `Craft` lines in the inspected Chromatic Tooltips callback chain: generic `handleTooltip` now passes the list through; only `handleItemTooltip` adds the network line.
- Remove the pre-existing working-tree attempt to suppress identical text for one second. Different items with the same count must not suppress each other's tooltips during rapid hovering.
- **No changes to stock queries, fluid identification, cache expiry, number formatting, network packets, Mixins, or dependencies.** Native NEI and the inspected Chromatic bridge share the item callback.
- Since fix41, **client and server must run the same version** (upload-related packet fields changed).
- Inspected bridge versions: **Chromatic Tooltips 1.0.29 / Compat 1.0.31 / NEI 2.8.101-GTNH**. This is not a universal compatibility guarantee for other versions or every GUI path.

See the [investigation](docs/mcp-tooltip-duplicate-investigation.md) for evidence and the [handover](docs/AGENT_CHECKPOINT.md) for actual build, artifact, verification, and outstanding test results. **A successful build is not an in-game pass; this change does not automatically deploy the JAR.**

## Installation and upgrades

1. Stop the game/server and back up the **complete world and configuration**, retaining the previous JAR for rollback. Infinity Cell contents live in the world save; backing up item NBT alone is insufficient.
2. In a matching GTNH installation, replace the old QoL JAR with `AE2-QoL-3.21.2.jar`. Do not retain multiple versions.
3. **Use the same version on client and server.** fix41 changed upload-related packets; upgrading only one side is unsupported.
4. This JAR includes `aeinfinitycell` (bundled metadata remains `1.0.4-ae2qol`). Do not also install the standalone AE2 Infinity Cell JAR. Test old cells in a copied world before migrating.
5. Check startup logs, generated configuration and Mixin loading, then exercise the features you use in a test world. Deployment to the development test instance requires separate approval.

### Environment and dependencies

The mod uses **GTNH fork APIs**, not arbitrary Forge 1.7.10/AE2/NEI combinations. See [dependencies.gradle](dependencies.gradle) and [gradle.properties](gradle.properties) for the full build declarations. A `compileOnly` declaration does not prove that startup without that integration has been tested.

| Component | Build reference (fix49) | Compared instance |
|---|---|---|
| Minecraft / Forge | 1.7.10 / 10.13.4.1614; MCP stable 12 | GTNH 2.9.0-beta-3 |
| AE2 Unofficial | rv3-beta-1050-GTNH | Same |
| AE2FluidCraft-Rework | 1.5.106-gtnh | Same |
| GregTech | 5.09.54.133 | Same |
| ModularUI2 | 2.3.88-1.7.10 | Same |
| NEI | 2.8.130-GTNH | Same |
| NotEnoughEnergistics | 1.7.41 | Same |
| GT Not Leisure | 0.2.7-pre3-dev-290 | Same |
| Programmable Hatches / Wireless Nexus | 0.2.0p24 / 1.0.2 | Same |
| BetterQuesting | 3.8.84-GTNH | Quest API reviewed against 3.8.70; built against 3.8.84 |
| GuideNH | 1.3.29 | Same |
| Thaumic Energistics | 1.7.60-GTNH | Same |
| Avaritia | 1.99 | Same |
| StructureLib | 1.4.42 | Same |

Before fix49 this table still listed beta-1-era versions (AE2 977 / GT 5.09.52.594 / NEI 2.8.19 /
MUI2 2.3.73 / NEE 1.7.14 / GTNL pre1 / PH 0.2.0p8 / BQ 3.8.70) — compile-capable but not aligned with b3.
The upgrade exposed and fixed 6 API breaks.

Other integrations involve CodeChickenLib, Thaumcraft and Eternal Singularity. Chromatic Tooltips is not a
dependency of this mod but the comparison environment for F4/F5 (fix42 inspected Chromatic 1.0.29 /
Compat 1.0.31 / NEI 2.8.101). The build uses Java 17 / Jabel and targets JVM 8 bytecode; use the game Java
version required by your pack's lwjgl3ify/launcher setup.

## Configuration and administration

Main directory: `config/ae2_qof/`.

| File | Purpose |
|---|---|
| `settings.json` | IO rate, smart-doubling limit, NEI display, pin defaults and stock-cover presets |
| `remembered_providers.json` | Remembered recipe → provider associations |
| `recipe_names.json` | User recipe-name/target mappings, used alongside bundled defaults |

Current `settings.json` fields:

| Key | Default | Meaning |
|---|---|---|
| `io_port_rate` | `1024` | Enhanced IO multiplier, 1–2147483647; high rates still have a tick cost |
| `smart_doubling_max_rounds` | `0` | No configured cap; material, energy and medium limits still apply |
| `nei_overlay_enabled` | `true` | Local client preference for NEI network information |
| `pin_row_enabled` | `true` | Default pin behavior; native terminal Pins Rows settings still apply |
| `stock_monitor_presets` | `1万=10000;100万=1000000;10亿=1000000000;清零=0` | Cover buttons; semicolon-separated `label=nonnegative value` entries |

- Relevant code paths check `settings.json` timestamps at intervals of at least one second. This is **not** a guarantee that every JSON file updates every client one second after saving.
- `/ae2qof reload` reloads settings and recipe-name mappings; `/ae2qof status` displays settings. Server administration commands require permission level 2. Not every single-player/LAN participant automatically has it.
- In-game: Mods → AE2 QoL → Config. Gameplay settings undergo server permission checks; the NEI display toggle is local. Do not assume all fields share the same synchronization policy.
- Prefer the terminal **OV** button for NEI display; `/apu-overlay` remains available as an alternative entry point. Use OV in dedicated-server scenarios.
- Confirm changes through the status command, UI and logs. Saving a file alone is not proof of a successful reload.

## Features and usage

The numbering below matches the historical [F1–F22 test script](docs/SINGLEPLAYER_TEST_SCRIPT.md). These are capabilities and usage notes, not a test-pass table.

### F1 · Pattern upload, recall and output swap

Standard/extended pattern terminals provide **↑ Upload, ← Recall, ⇄ Swap and OV**. Standard, GT ultimate and ae2fc fluid encoded patterns are supported.

Provider selection uses a sole candidate, remembered mapping, or manual selection. It does **not** promise first-use automatic recipe-map detection for every GT machine. The selector separates search/paging, machine rows, action buttons and mapping controls; rows include location/free slots, with double-click upload and a selected-machine mapping shortcut.

fix41 introduced dimension/position/part-side locators, failure feedback and checking all available pattern slots. A locator does not guarantee that a replacement block is the original machine. Bundled mappings and NEI names assist searching; historical mapping counts are not a coverage guarantee.

### F2 · NEI extraction and crafting requests

In supported terminal/wireless contexts, **Shift+left-click** attempts to extract a stack to the inventory; **middle-click** opens crafting amount confirmation. A craftable item can be requested with zero stored stock. Server-side permissions, network access and inventory capacity still apply.

### F3 · Crafting-output pin rows

Crafting outputs are pinned in separate rows above the terminal grid, showing total network stock. The current design expands up to three rows. Use native **Pins Rows** settings to adjust/disable them; `pin_row_enabled` controls default behavior. This is not the former overlay drawn over the item grid.

### F4 · NEI network tooltips

Cyan counts show cached network stock with K/M/G/T/P/E suffixes; green `+` and `Craft` indicate craftability. Craftability can appear without stored stock.

- Ordinary items use item stock and the `AE` label. **GT fluid-display stacks and ae2fc pure-fluid representations** use the existing fluid lookup and show `mB` plus the fluid name.
- **Real buckets/cells remain container items for stock queries**, rather than universally being converted to their contents.
- The inspected Chromatic Compat converts a fluid-only context to a GT fluid-display stack before calling the same item handler. fix42 adds no fluid adapter.
- Data comes from the terminal-fed client cache, not a fresh server query on each hover. Disabled display, expired/missing cache, or neither stock nor craftability produces no added line.

### F5 · NEI count overlays

Network counts/craftability badges are drawn on relevant NEI item, bookmark and recipe displays. They share the cache with F4 but are a separate rendering path. fix42 changes only the tooltip text entry point.

### F6 · Crafting-completion notifications

AE2-style banners show the output and elapsed time to the recorded requesting player, with sound and queued display. Manual CPU following is not required for every order. Cancellation, offline players and fluid outputs still need scenario-specific testing.

### F7 · Replan

The crafting-confirmation **Replan** button recalculates the current job. It does not change the machine's recipe or forcibly complete an order.

### F8 · Enhanced IO Port

`ex_io_port` reuses AE2's IO mechanism, scaling transfers by `io_port_rate` (default 1024). Back up large transfers and monitor server tick cost.

### F9 · Infinite Water and Lava Cell

In an ME Drive, the cell supplies water/lava using a creative-cell-style mechanism with a very large finite displayed amount. Check the instance's NEI for the recipe; the display is not ordinary persisted cell inventory.

### F10 · Wireless AE transceivers and connector

Sender/receiver transceivers on a matching channel link ME networks. **Sneak-right-click a sender** with the connector to bind its channel; right-click supported ME devices to link/unlink. Includes channel management, cross-dimension links and block highlighting. Endpoint loading, network state and permissions still matter; cross-dimension support is not automatic chunk loading.

### F11 · Quartz Knife name copying

Hold a Quartz Knife and **sneak-right-click** a supported block, AE part or GT machine to write its name onto the knife and copy it to the clipboard. Name resolution and inventory-synchronization edge cases are noted in the audit.

### F12 · F-key search filling

In supported AE2/ae2fc screens, hover an item and press **F** to fill the search field. Typing into an already-focused search field should not be intercepted.

### F13 · NEI display toggle

The terminal **OV** button controls network information and saves a local preference. It is separate from server gameplay multipliers; players choose their own display setting.

### F14 · Smart Doubling

Enabled supported ME interfaces, GT/GTNL pattern hatches and Programmable Hatches media attempt to push multiple processing rounds at once. A zero configuration cap still respects available material, energy, buffers and simulated medium capacity.

Fallback conditions include fake crafting, blocking and pending items. **Bulk material pushing is not machine parallelism or overclocking.** Cross-medium buffering/accounting has open static-review findings; there is no blanket no-loss/no-overproduction guarantee.

### F15 · Pattern & Interface Merged Terminal

Block, cable-part and wireless-handheld forms combine pattern editing with the interface list:

- Crafting 3×3 / paged processing grids; encode, clear, multiply, substitute/backup substitute and invert.
- Upload, recall, primary-output swap, OV, and a conditional GTNL assembly-matrix AM entry point.
- Pattern read-back, editing snapshots, GT/ae2fc fluid representations and PH Programming Toolkit integration.
- Middle-click amount editing, Shift+middle-click naming and other shortcuts. Limits depend on slots and encoding; not every quantity is unbounded.
- Wireless binding through an ME Security Terminal, cross-dimension access, and binding/permission checks.

### F16 · ME Quest Detector

Checks ME stock for the bound player's/team's BetterQuesting non-consuming retrieval tasks. Consuming submissions are skipped: visibility is not consumption. Network/offline conditions apply; NBT variants and persistent owner binding have review findings.

### F17 · Infinity Storage Cell

Integrates dancing snow's AE2 Infinity Cell for items, fluids and essentia. **Items hold a UUID; contents live in the world save. Copies share the same backend inventory.**

NEI `U` opens paged contents; tooltips show statistics and Ctrl switches scientific notation. AppEU energy is not included. Save-failure/migration findings remain open: back up before upgrades and bulk migration rather than assuming risk-free compatibility.

### F18 · Universal Maintenance Hatch

Provides wireless EU, maintenance behavior and circuit-board parallel mapping. **Maintenance bypass currently applies through a global Mixin, not only to machines fitted with this hatch.** Wireless EU requires an already-funded account. Circuit mapping, cross-recipe consumption and output merging need focused regression testing.

### F19 · GT wireless EU

Wireless Output Hatch **32110** contributes to the wireless EU account; Wireless Input Hatch **32111** draws from it. This is separate from wireless AE item/fluid networking, does not generate free power, and is not Tesla Tower behavior.

### F20 · Adaptive energy grid

Terminal **32106** has five pages: status, settings, frequency, monitoring and hatch list. Companion hatches: input **32102**, laser source **32103**, dynamo **32104**, laser target **32105**.

**Sneak-right-click the terminal** with a Network Data Stick to write configuration → right-click hatches to bind → right-click the terminal to read. Includes team networks, highlighting/permission-controlled teleportation, controller names and statistics. Sampled monitoring data is not proof of correct resource accounting.

### F21 · Stock Monitor Cover

Queries items/fluids through adjacent AE or Nexus wireless binding and outputs control signals according to threshold/mode. Includes a marker slot, quantity presets and synchronized status UI. Mounting, machine behavior and wireless state depend on the actual environment.

### F22 · Stock Statistics Terminal

GT information terminal, ID **32107**, designed to centrally inspect/edit standard AE level emitters and this mod's stock covers, including thresholds, status and location. **Client/server UI construction, emitter enumeration and remote-edit authorization have open review findings.** Registration or compilation alone does not demonstrate complete functionality.

## Known issues and verification scope

A01–A19 in the [fix41 full-function audit](docs/mcp-full-function-audit-fix41.md) are static-review findings, not a claim that every issue was reproduced in-game. They are **not closed by any later round**. Priorities include Infinity Cell saving/migration, cross-recipe conservation, wireless EU, smart doubling, stock-terminal behavior, provider-list budgets and NBT identity.

Every change after fix44 (v7 textures, in-world middle-click pick, MTE ID migration, the b3 dependency upgrade) has **only a successful build and artifact inspection behind it — no in-game acceptance run**. Untested items are not passes. For A13/A14/A15 the code direction has landed (`util/ItemIdentity`), but the audit status column is not back-filled and nothing has been re-tested.

For reports, include both sides' JAR versions, pack/integration versions, GUI and item/fluid, reproduction steps, expected/actual behavior, logs and screenshots. Tooltip regression should cover stock/craftability combinations, rapid switching, fluid displays, real containers, and both native NEI and Chromatic paths.

## Building and development

Use matching dependencies, repository-local `libs/`, and a populated Gradle cache. Missing offline dependencies should be restored at their exact versions, not upgraded merely to make the build pass.

```powershell
$env:JAVA_HOME = 'E:\java17'
$env:GRADLE_USER_HOME = 'C:\Users\29357\.gradle'
.\gradlew.bat build --offline -x spotlessJavaCheck -x spotlessCheck
```

The fix49 round passed an offline Java17 build (`compileJava` and `build` both `BUILD SUCCESSFUL`, with the produced JAR unpacked to confirm the new Mixins are present and their members reobfuscated). The fix42 script [tooltip-fix42-regression.sh](docs/tooltip-fix42-regression.sh) passes 59 assertions using dependency stubs rather than game integration; re-run with `JAVA_HOME=/e/java17 bash docs/tooltip-fix42-regression.sh`. Gradle `test` itself reported `NO-SOURCE`.

Adjust paths and use `./gradlew` on other environments. This is the current Java17 verification command and **explicitly skips Spotless**; it does not establish a formatting-check pass. Jabel permits modern syntax while emitting JVM 8 bytecode. Artifacts are in `build/libs/`; actual test execution and checksums are recorded in the handover.

Before changes, read the [handover](docs/AGENT_CHECKPOINT.md), [development guide](docs/GTNH-开发指南.md), [build/reference guide](docs/GTNH-构建与代码参考.md), and [code style](docs/GTNH-代码风格.md). Do not substitute modern Minecraft APIs or reintroduce RFB `childDelegations` interference.

## Documentation

| Document | Purpose |
|---|---|
| [CHANGELOG.md](CHANGELOG.md) | Root version log and historical fixes |
| [AGENT_CHECKPOINT.md](docs/AGENT_CHECKPOINT.md) | Current implementation, artifacts, untested cases and handover actions |
| [Tooltip investigation](docs/mcp-tooltip-duplicate-investigation.md) | Pre-fix42 callback/version evidence and proposal |
| [Full-function audit](docs/mcp-full-function-audit-fix41.md) | F1–F22 mapping, A01–A19 and priorities |
| [MOD_MAP.md](docs/MOD_MAP.md) | Feature entry points; verify historical mappings against code |
| [Single-player test script](docs/SINGLEPLAYER_TEST_SCRIPT.md) | Historical F1–F22 cases, not proof of this version passing |
| [Mixin notes](docs/mixin_notes.md) | Injection/compatibility notes |
| [CREDITS.md](CREDITS.md) | Code, texture and licensing records |

## Credits and licensing

Adapted from **GaLicn's [AE2-Auto-Pattern-Upload](https://github.com/GaLicn/AE2-Auto-Pattern-Upload/)**, with thanks for the original upload and F-key search implementations.

- **GTNH / Applied Energistics 2, NEI and AE2FluidCraft**: core APIs, terminals, fluid and recipe ecosystem.
- **小飘 (mynamexiaopiao)**: AE-Wireless-Transceiver code; **麦淇淋 (@麦淇淋)**: wireless blocks, connectors and GUI artwork. Permission records are in CREDITS.
- **dancing snow (DancingSnow0517)**: AE2 Infinity Cell; the original MIT license is retained in the JAR.
- **asdflj / AE2Things**: conceptual references. Enhanced IO textures come from **AE2's original BlockIOPort**, not AE2Things.
- **Waila, GT5-Unofficial, GT-Not-Leisure, GTLCore, Programmable-Hatches, ExtendedAE_Plus, ExampleMod1.7.10**, and the other referenced projects.

AE2-sourced textures include non-commercial, attribution and share-alike requirements. Personal archival does not replace third-party permission. Any public release requires renewed review of code, artwork, author permissions and licenses; this README grants no blanket redistribution license.
