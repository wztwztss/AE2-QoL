# AE2 QoL

> **English** | [简体中文](README.md)

An AE2 quality-of-life mod for **Minecraft 1.7.10 / GT New Horizons**: NEI pattern uploading, network stock and crafting hints, merged terminals, wireless AE links, and GT energy/stock-management tools.

**Author: wztwzt · Current source version: 3.23.2-fix1 · Reference pack: GTNH 2.9.0-beta-3**

## What's new in 3.23.2-fix1 (the Wild-pattern / pattern-generator UIs would not open on a dedicated server)

- **Symptom**: on a dedicated (multiplayer) server, right-clicking the **Smart Wildcard Pattern** or the
  **Smart Pattern Generator** did nothing at all; single-player worked; every other UI of this mod
  (stock monitor terminal, adaptive grid, covers, merged terminal) opened fine.
- **Root cause**: both UIs are ported **MUI1** windows and their `getServerGuiElement` (the server half) *also*
  called `createWindow(...)` to build the full window — and those window classes contain **client-only** code
  (the "edit" buttons added in fix43 do `Minecraft.getMinecraft().displayGuiScreen(new GuiTextInputDialog(...))`).
  On a dedicated server Forge's `SideTransformer` refuses to load client-only classes
  (`NoClassDefFoundError: net/minecraft/client/gui/GuiScreen` / `Attempted to load class bdw for invalid side SERVER`),
  so FML could not obtain the server container and **never sent the open-window packet**.
  Single-player is the CLIENT side, which is why it never showed up there.
- **Fix**: the server half no longer builds any window — it returns an empty, slot-less MUI1 container
  (new `merged/ServerSafeModularContainer`); **the window itself is only built client-side in `getClientGuiElement`**.
  These UIs have no slots at all and use no MUI1 sync, so both sides agree (empty slot set).
  Also downgraded two per-right-click INFO lines to DEBUG and removed a hot-path debug log.
- Client-side behaviour is unchanged (tab candidates, lookup table, the text-input dialog, save-and-reopen).
  You need to upload the new jar to the server yourself.

## What's new in 3.23.2 (removed our own "Ctrl + middle-click copy block NBT" — the pack already ships it)

- **The whole 3.23.0/3.23.1 implementation is gone** (`blockcopy/BlockCopyService`, `network/BlockCopyRequestPacket`,
  `mixin/mc/MixinItemBlockOnItemUse`, its packet registration in `ModNetwork`, and the Ctrl branch in
  `client/PickBlockCompatHandler`).
- **Why**: vanilla/Forge 1.7.10 never had NBT-carrying pick block (`ForgeHooks.onPickBlock` has no Ctrl/NBT branch,
  `ItemBlock.placeBlockAt` has no `BlockEntityTag` restore), but this pack's bundled **SNL** mod already implements it:
  **Ctrl + middle-click** in creative = 1000-block remote pick of the block **with its complete NBT** (chests and other
  containers included — the item gets a `(+NBT)` lore line) and the data is restored when you place it. Ours hung off
  Forge `InputEvent.MouseInputEvent`, which **cannot be cancelled**, so it could not stop SNL and instead **fought it
  for the same hotbar slot**, overwriting the correct result — exactly what the user reported ("what I get is completely
  wrong, chest contents are not carried over").
- **Kept**: the fix54 AE2 world pick-block resend in `PickBlockCompatHandler` (a separate, already-accepted feature).
- Evidence (bytecode level) is in `CHANGELOG.md` record (50), `docs/mixin_notes.md` known-risk item 9, and the
  deprecated-approach row in `docs/MOD_MAP.md`. **Bottom line: in this pack, middle-click picking (with NBT) belongs to
  SNL — do not reimplement it and do not hook `Minecraft.middleClickMouse`.**

This repository is for personal archival and is not currently offered for distribution. See [CREDITS.md](CREDITS.md) for attribution and licensing records. Feature descriptions are not a claim that every integration has passed in-game testing.

## What's new in 3.20.0-fix38 (output prefix is now existence-driven — the real upstream of "every pattern outputs the same plate")

- **Why 3.20.0-fix37 had no effect**: it fixed the *output-slot replacement test*, but that whole block sits inside
  `if (outStack != null)`, and `outStack` is only computed when the **output prefix is non-empty**. The decisive
  counter-evidence was in the log: the new "replacement slot not found" WARN **never fired**, proving the block was
  never entered.
- **The real cause**: the output prefix was derived by comparing **material names** ("template output material"
  vs "template input material"), but GT plates register **both** `plateIron` and `plateAnyIron`, so resolution could
  yield `AnyIron`, the comparison failed, the prefix came back empty, and **outputs were never rewritten** — every
  concrete pattern kept the template's iron plate.
- **Fix**: stop guessing material names and go **existence-driven** — collect prefix candidates from every
  ore-dictionary name of each template output slot (`plateIron` → `plate`; `plateAnyIron` also falls back to
  `plate`), then per material pick the first prefix whose `prefix + material` actually exists in the ore dictionary
  (`plateAnyCopper` does not exist, so it falls back to `plateCopper`). An explicit output matcher in the rule still
  wins. An empty candidate set now logs a WARN (visible degradation); the window's "output row stays empty" had the
  same root cause and is fixed too.
- **Self-verifying log**: `展开样本：material=Copper prefix=plate in=… out=… 产出out=…` (three lines per JVM), so
  the next round needs no guesswork.

## What's new in 3.20.0-fix37 (every expanded pattern produced the same iron plate)

- **The diagnostics hit it in one reproduction**: after adding a line that prints the number of **distinct outputs**
  plus a read-back of AE2's own crafting table, one run showed that **all 396 expanded patterns on all three
  machines output the same iron plate** (`输出种类=1`), and AE2 did index them — so the problem was our data,
  not AE2.
- **The culprit is one wrong comparison** in `buildConcretePattern`: the output slot was replaced only when
  `templateOutputMaterial == candidateMaterial`, but the template output's material is always `Iron`, so only the
  iron candidate was rewritten and **every other pattern kept the template's iron plate**. That is why all
  ingot→plate recipes collapsed into one, and why the GT hatch "accepted the order but never crafted" (AE planned
  per material while the machine received only iron plates).
- **Fix**: the slot is now identified by "one of its ore-dictionary names starts with this rule's output prefix"
  (`plate` matches `plateIron`/`plateAnyIron`); only the first match is replaced, byproducts stay as templated,
  and a miss logs a rate-limited WARN instead of failing silently.
- The diagnostics stay (distinct-output count + AE crafting-table read-back) but are computed only when the
  rate-limited log actually fires.

## What's new in 3.20.0-fix36 (diagnostic build: distinct outputs + AE crafting-table read-back)

- **Logs only, no behaviour change**: each registration prints the number of **distinct outputs** and the first
  three samples, then reads AE2's own crafting table back on the next tick (entry count, whether sampled outputs
  are indexed, and the medium class). These two lines are what pinpointed the real cause in the next release.

## What's new in 3.20.0-fix35 (never cancel the original method — fixes "both wildcard mods see only one pattern")

- **Good news first**: the 3.20.0-fix34 log shows **our own expansion now succeeds** — `produced=512` (capped) /
  `produced=396` with `matched=4393` — so the ore prefix, index alignment and matcher fixes all took effect.
- **The regression's real cause**: cancelling GT's `provideCrafting` also skips the original WildcardPattern mod's
  expansion, because `CallbackInfo.cancel()` is a **shared flag**. Your testing produced a clean A/B/A:
  3.20.0-fix32 (always cancelled) both broken → 3.20.0-fix33 (we failed to take over) original worked again → 3.20.0-fix34 (we took
  over again) both broken again.
- **Fix**: in the GT hatch, the GTNL hatch and the AE2 ME interface paths we now **only append and never cancel** —
  our expanded patterns are added to the registry with their mappings, while plain slots are left to the original
  methods and to the other mod. The cost is that "the template pattern" is registered once more next to the expanded
  pattern for the same material (harmless).
- Also fixed two diagnostic logs that **had never printed** (their throttle sentinel used `Long.MIN_VALUE`, so
  `now - Long.MIN_VALUE` overflowed negative and the condition never held). `GT 通配样板注册（只追加拿，未 cancel）：…
  本机含原版样板=N …` now appears as intended.

## What's new in 3.20.0-fix34 (wildcard matching always returned false — the last reason it "never worked")

- **Fixed the wildcard matcher**: `SmartWildcardState.matches` used to lowercase the **entire regex string**,
  turning the `\Q…\E` quoting produced by `Pattern.quote` into `\q…\e`; Java then throws
  `PatternSyntaxException: Illegal/unsupported escape sequence` and the `catch` **silently returned false**.
  As a result **every** wildcard match in this mod was false: the expander could never enumerate materials
  (`no-material-matched` → zero patterns → machines only knew the single template pattern, i.e. what you saw as
  "only the iron plate is recognised"), and **blacklists, whitelists and per-rule excludes were dead too**.
  It now escapes metacharacters explicitly and uses `CASE_INSENSITIVE`, and exceptions are no longer silent
  (they log a WARN). Verified before/after with a minimal Java case: `matches(ingot*, ingotIron)` was `false`,
  now `true`.
- Note: 3.20.0-fix33's index alignment and 3.20.0-fix32's ore prefix / template self-heal remain valid (this round's log shows
  `matcher='ingot*'`, `outMatcher='plate*'` and no out-of-range slots); this was the final blocker.
- ⚠️ Regression notice: blacklists and excludes only take effect **from this version on**. If you see materials
  being excluded, check that pattern's global/rule excludes — the old entries are finally doing their job.

## What's new in 3.20.0-fix33 (row / rule-slot / template-index alignment + no interference between the two wildcard mods)

- **Fixed "the + only fills the input row, the output row stays empty, and the machine still crafts the pattern
  itself"**: the real cause was **three mismatched numbering schemes** — a machine-side rule slot is an index into
  the **template input list** (the template can be `[circuit, iron ingot]`), while the UI used to emit one row per
  **rule**, so the circuit row never reached the UI. The template self-heal then rebuilt a shorter template
  (`in=1`), the rule's `slot=1` went **out of range and was dropped** (logs: `槽位越界` + `材料交集为空` +
  `produced=0`), and output entries could not land on the right row. Now one UI row = one template slot: the input
  row gets the matcher for that slot, the **output row shows the prefix derived from that same row's template
  output** (`plate*`), non-rule rows (circuit) keep a blank placeholder row to preserve indices, saving uses the
  **row number as the slot index**, and after a template rebuild the slots are renumbered (and logged).
- **Fixed "with both mods' patterns in one hatch, only one pattern is recognised"**: the original WildcardPattern
  mod and this mod both take over `MTEHatchCraftingInputME.provideCrafting` at HEAD with `ci.cancel()` (proven with
  javap); the old code cancelled **unconditionally** and re-registered everything, flattening the other mod's
  expansion to a single pattern. Now, when a machine holds none of our configured patterns we **do not interfere at
  all** (GT/the original mod handle it), and we only take over when our own patterns are present.
- **New reverse self-heal**: the shared details→slot map is cleaned by the original mod's `removeIf`; the
  `pushPattern` guard now checks our own expansion first, **restores the mapping and lets the original method run**
  instead of permanently rejecting our patterns.
- Note: the 3.20.0-fix32 results still hold (ore prefix now `ingot*`, template self-heal works); this release fixes the
  index-model mismatch and the cross-mod interference they exposed.

## What's new in 3.20.0-fix32 (the wildcard pattern finally expands: wrong ore prefix + deleted native template)

- **The ore-dictionary string produced by "+" was not an ore name at all**: `OrePrefixes.getOreprefixKey()`
  returns GT's **localisation key** (proven with javap: the constant pool literally contains `gt.oreprefix.`,
  so it returns e.g. `gt.oreprefix.ingot`). Building a rule from it yields `gt.oreprefix.ingot*`, which
  **never matches any ore dictionary name** (those look like `ingotIron`) — that truncated `gt.orepr...` in
  the UI was this bug. Rules are now derived from the **real ore dictionary name** (`ingotIron` minus `Iron`
  gives `ingot`), the same convention the expander uses, which also handles multi-segment prefixes such as
  `crushedPurifiedIron`. All three call sites (NEI "+" derivation, the expander, NEI drag-in) were unified,
  and the deriver now logs **every derived rule** for diagnosis.
- **Fixed "AE crafts the pattern itself instead of the expanded wildcard patterns"**: the ported Wild code
  **deletes the item's native `in`/`out`** on first initialisation (that is the reference implementation's own
  data model), while this mod's expander **uses native `in`/`out` as the template** — with the template gone,
  expansion always produced zero (23 occurrences of `reason=template-in-out-missing` in testing) and machines
  fell back to the single template pattern. Now: (1) our patterns are no longer stripped (the original
  WildcardPattern mod's items keep their original behaviour); (2) a **template self-heal** rebuilds and writes
  the template back from the Wild row data, so **patterns already broken in your save do not need to be
  reconfigured**.
- **The output row is no longer blank**: when a rule does not specify an output, the prefix derived by the
  expander itself (e.g. `plate`) is displayed as `plate*`, matching the expected "input `ingot*` → output `plate*`".
- Note: the 3.20.0-fix31 fixes did work (the machine side now reaches our code and "+" writes into the window in
  place); this release fixes the two deeper causes they exposed. Only from this release does the expansion
  path actually reach "produce N patterns" for the first time.

## What's new in 3.20.0-fix31 (wildcard pattern not recognised by any hatch + NEI "+" + dedicated Circuit / Non-consumed pages)

- **Fixed "a configured wildcard pattern is not recognised by any hatch"** (in-game: GT Crafting Input Buffer, GTNL
  Super Input Hatch, PH 22069 / MK.II and our own MK.III all failed — the pattern sits in the slot but the machine
  does nothing, with no log trace). Two stacked root causes: (1) `rebuild()` on the GT and GTNL pattern slots had
  **zero callers**, so the expansion list stayed empty and nothing was registered; (2) all four entry points required
  "the item already carries our NBT", which is only written when the Wild window saves — and the negative branch was
  **silent**. Now a single gate (`SmartWildcardGate`: item instance → lazy pull when our NBT is missing → at least one
  rule) is used everywhere, negative branches log a rate-limited WARN, and an empty expansion **falls back to
  registering the template pattern** instead of registering nothing.
- **Fixed the NEI "+" inside the Wild window**: it now writes the derived recipe **in place** — the window's nine rows
  are replaced wholesale, the widgets refresh immediately and the pattern is persisted at once (no more "close and
  reopen"). The write target is pinned to **the pattern the window is editing** (the old code wrote to "the first
  pattern in the inventory", visible as a revision counter going backwards). Recognition was also tightened so only
  the actual Wild window is hijacked, not other GTNH-MUI windows such as the batch pattern generator.
- **Two dedicated pages in the Wild window** (tabs at the top-right: Main / Circuit / Non-consumed):
  **Circuit** lays out 1–24 as 4 columns × 6 rows plus "Clear (inherit)" and applies/highlights immediately;
  **Non-consumed** supports NEI drag-in, "add held", 8 rows per page (icon + name) and per-row delete, all written
  back immediately. The 50 px circuit band added in 3.20.0-fix30 has been **removed** (window height back to 292).
- **Fixed a gesture that had been silently dead since 3.20.0-fix10**: "Shift+middle-click a slot → circuit picker" on MUI2
  slots. The mixin callback was declared as `CallbackInfo` while the target returns `Interactable.Result`, so the
  injection threw and UniMixins swallowed it into a single WARN. It only works from this version on.
- ⚠️ This round does **not** back-fill dedicated sections for 3.20.0-fix27–3.20.0-fix30 (recorded honestly as documentation
  debt); `CHANGELOG.md` and the tail of this mod's `zh_CN.lang` contain **historically corrupted (mojibake)
  segments** that need a separate UTF-8 rewrite pass.

## What's new in 3.26.x – 3.20.0-fix26 (Pattern Generator UI ported from AE2PatternGen + dual entry for the wildcard pattern)

- **The Pattern Generator now uses AE2PatternGen's own UI** (per the request to "just copy it over first, then change it"): its whole
  UI subsystem was ported into `apgport/` — **65 files / 9,983 lines** (main window `GuiPatternGen`, recipe picker `GuiRecipePicker`,
  storage/detail panels, `GuiComboBox`, filter widgets, 9 filters, `GTRecipeSource`, `PatternEncoder`, the cache/storage subsystem,
  18 network packets). Every ported file keeps its **MIT** attribution header.
- **Behaviour changed to ours**: generating **no longer consumes AE2 Blank Patterns**, **no longer writes into its virtual storage**,
  and the patterns go **straight into your inventory** (overflow is dropped at your feet); chat and log report
  encoded / stored / dropped counts in full.
- **The wildcard pattern has two entries again**: plain right-click = the Wild window (rules/exclusions/preview + NEI drag-in +
  **one-click `+` transfer**); **Shift+right-click** = our own four-tab editor (which carries the **built-in circuit 1..24** and the
  **non-consumed items** pages).
- **Clear feedback after a write-back**: the state is pushed to Wild's tags as well and the chat says "close and reopen once to see it".
- **Fixed**: the save guard rejecting brand-new patterns (the root cause of "nothing transfers"); missing widget sizes in the
  generator and wildcard editors; leftover `§` colour codes; the NEI `+` button not recognising GTNH-MUI windows; and **two
  startup-crash-level duplicate network channel names** (now the separate `_wild` / `_apg` channels).

## What's new in 3.25.x (UI ported wholesale from WildcardPatternforGTNH)

- **The UI is now Wild's own** (per the user's request to "just copy the Wild mod over and change it from there"):
  its whole UI subsystem was ported — **25 files / 7,870 lines** (main window 2143, composite window 1871, rule entry
  975, generator 451, three drag-in widgets, GUI handler, network messages, …). Each ported file keeps its **MIT**
  attribution header, and `docs/THIRD_PARTY_NOTES.md` records the provenance.
- **Why this was possible**: it targets a different MUI (`com.gtnewhorizons.modularui`) than the Cleanroom MUI2 we used
  before; we now depend on it at **compile time only** (`libs/modularui-1.3.4.jar`; the modpack provides it at runtime
  and we do **not** redistribute its code).
- **The machine side is unchanged**: right-click → the bridge pushes our config into Wild's tags → Wild's window opens;
  saving in that window → the bridge pulls the config back into our subtree ⇒ **pattern-buffer takeover and index-time
  expansion still use our own expander**.
- **Three real bugs fixed along the way**: ① the `+` derivation **misread item ingredients as fluids** (javap evidence:
  that `IFluidAlternativeStack` interface is implemented by the GT class that holds *every* ingredient) — it now keys on
  GT's fluid placeholder item `ItemFluidDisplay`; ② expansion failures now **dump every rule** (no more guessing at
  `no-material-matched`); ③ NEI drag-in now uses a **coordinate hit test** (the MUI2 hover state is null outside a frame).
- **Two hazards caught before deployment**: a duplicate network channel name would have **crashed the game on startup**
  (now suffixed `_wild`); and its original save guard silently **discarded our** pattern (now both items are accepted,
  with a WARN when rejected).
- ⚠️ **Stated plainly**: the port currently reaches "compiles + data flow wired as its source intends" — the **UI has not
  been verified in game yet**. The first testable build is **3.20.0-fix19**; results pending.

## What's new in 3.20.0-fix15 (UI rebuilt to your design: four tabs + NEI drag-in)

- **Four tabs**: Rules / Coverage preview / Exclusions & non-consumed / Circuit. MUI2 has **no** `TabWidget`, so it is
  "tab buttons + four page containers + `setEnabledIf(page predicate)`" (the predicate is evaluated every frame, so
  switching is instant and both sides build an identical widget tree).
- **Rules page**: 9 rows × (wide input field + **mode toggle** (on = ore dictionary, off = display name) + amount + → +
  output field + mode + amount + Preview/Filter/x2/Clear), **row height 20px, spacing 4px**; bottom row
  Preview all / Clear page / Reload / Save.
- **Coverage preview page**: search (filters as you type), paging and a per-row "Exclude" that adds to the global
  blacklist; the header reports produced/matched/skipped **in readable Chinese** together with the reason.
- **Exclusions page**: global blacklist (add / clear / delete row) + **per-rule exclusions for rules 1..9** (click a
  number to switch) + **non-consumed items** (via "Add held item" or by **dragging an item onto the "Add (drop)" button**,
  each row deletable).
- **NEI drag into matcher fields**: drag an item from NEI/inventory **straight onto an input or output field** — if it has
  an ore dictionary entry the field becomes `<prefix>*` in ore mode; otherwise it becomes the display name in name mode.
  A drop that misses a field is not consumed (NEI keeps its default) and logs an INFO line saying why.
- **Fixes**: ① only the title/header used to render — the cause was child containers **without an explicit size**, which
  MUI2 lays out as zero height; ② unreadable text — all `§` colour codes removed in favour of the default dark text on
  the light panel; ③ chat spam on every open (old self-test output) — removed; ④ the real
  **`reason=no-material-matched`** bug — rules without a wildcard were used to derive materials and emptied the
  intersection, so the whole pattern expanded to nothing; such rules are now treated as **exact matches**; ⑤
  `createScreen` is now overridden with this mod's id as MUI2 requires, silencing the "or else it will crash" warning.

## What's new in 3.20.0-fix11 (wildcard editor rebuilt on MUI2 + Pattern Generator)

- **Rebuilt UI (MUI2)**: the Smart Wildcard Pattern now uses a Cleanroom ModularUI 2 screen (container-backed, so
  **NEI's + still works**). Instead of "a few buttons and a lot of empty space" it is a **single-page editor**:
  a **9-row rule table** (`input matcher | amount | -> | output matcher | amount | clear | x2`, with `ore:` / `name:`
  prefixes making the mode self-explanatory), a **built-in circuit 1..24** field (empty = inherit from slot/machine;
  an invalid value keeps the old one and logs a WARN), a **global blacklist** (add / clear), a **coverage preview**
  (lists candidates, each with an Exclude button; shows the reason when nothing matches) and **Save**.
- **NEI + button**: pressing + while the editor is open derives rules and the recipe template and **writes them back
  immediately** (chat and log both confirm).
- **New item: Pattern Generator** (cyan pattern icon, crafted from one AE2 Blank Pattern): bulk-produce concrete
  patterns from a **GT RecipeMap** with input/output blacklists, input/output ore filters, an NC (non-consumed) item
  filter and a cap. Results go into your inventory (overflow is dropped at your feet) and both chat and log report
  `seen / produced / skippedFluid / filtered / truncated` — **full counts, never a silent shortfall**. Its core is
  **adapted from AE2PatternGen (MIT)** and keeps that project's copyright and licence notice; see
  `docs/THIRD_PARTY_NOTES.md`.
- **Licence hygiene**: an audit confirmed **no reference-mod code** was copied; the AE2 texture copied into the repo
  earlier (an LGPL asset) has been **removed** in favour of a runtime name reference; the `LICENSE` copyright line is
  now `wztwzt` (still MIT); and research notes about other people's source were moved out of the repo and ignored.

## What's new in 3.20.0-fix10 (Smart Wildcard Pattern)

- **New item: Smart Wildcard Pattern** (crafted from one AE2 Blank Pattern): **one pattern covers a whole recipe class** —
  e.g. "1× any ingot → 1× the matching plate". At pattern-index time it expands into legal ordinary patterns for each
  material, so every buffer accepts it (GT 2714/2715, PH 22069/22179 and this mod's MK.III 32108, GTNL 21504/21505,
  AE2 ME Interface).
- **One step from NEI**: right-click the pattern to open its screen, open a recipe in NEI and press **+** — rules and the
  recipe template are derived on the spot; press Save and the server writes them. Ore dictionary first (GT's
  `OrePrefixes.detectPrefix`), NEI fluid placeholders skipped, programmed circuits recognised by `gt.integrated_circuit`.
- **Visual editing (four pages)**: Rules / Preview (per-candidate **Exclude** button feeds the blacklist) /
  Blacklist (`*` and `?` supported) / Circuit (1..24 plus non-consumed items such as molds, extruder shapes, lenses).
  The preview uses the **same expander** as the machine, so nothing is shown that cannot be crafted.
- **Built-in programmed circuit**: when a machine reads the pattern the circuit number is written into its virtual circuit
  slot (GT's official `IConfigurationCircuitSupport` + `GTUtility.getIntegratedCircuit`); priority is
  **pattern's own > slot > machine**; a pattern without a circuit never touches the machine's existing value.
- **New setting `smart_wildcard_expand_cap`** (default **512**): per-pattern expansion cap; exceeding it **truncates and
  logs a WARN** (never a silent shortfall). Comparable mods expand unbounded on the server's network hook path, a known
  lag source, so this mod caps it deliberately.
- Known limits: expansion happens at index time (placing/changing a pattern, loading NBT) and one pattern covers **one
  recipe class**; see the in-game guide page "Smart Wildcard Pattern" for details.

## What's new in 3.20.0-fix9 (configurable per-push cap for Smart Doubling)

- **New setting `smart_doubling_push_cap`** (default **4096**, range 1..2147483647): the **per-push
  (per-tick) round cap** for Smart Doubling. Raise it for bigger batches (keep
  `smart_doubling_max_rounds` at 0 = unlimited). The 4096 default is the safety value chosen when fixing
  audit items #51 (an oversized power probe walks every energy storage, O(P)) and #73 (1T-scale orders
  drowned the client in item updates); raising it costs linearly more extraction and item updates per tick.
  Editable in `settings.json`, the in-game "Mods → AE2 QoL → Config" page, and visible via `/ae2qof status`.
- **Fixed a diagnostic false positive**: turning the toggle off did not clear the "expect enabled"
  registration, so the CPU could wrongly warn that the server switch was still false (seen while testing 3.20.0-fix8).
- Note: the "tens of thousands at a time" you saw is `4096 rounds × the pattern's output per craft` —
  a designed cap, not a failure.

## What's new in 3.20.0-fix8 (Smart Doubling fixed on dedicated servers)

- **Root cause**: the Smart Doubling toggles for GT/GTNL/PH machines were implemented in **client-only
  mixins** (the `client` section of `mixins.ae2_qof.json`), and their `BooleanSyncValue(...).allowC2S()`
  write needs a **matching sync handler on the server**. A dedicated server has no such injection, so the
  write was silently dropped by MUI2, leaving the machine flag `false` on the server — the CPU then fell
  back to the vanilla one-round path with no log at all. Single-player worked because the client JVM
  applies the client-section mixin globally, so the integrated server ran the transformed method.
- **Fix**: the toggle now sends the **machine coordinates** to the server, which re-resolves the MTE,
  verifies `ISmartDoublingMedium`, writes the flag and `markDirty()`s it, then returns the
  **authoritative state** to the client for display — fully decoupled from MUI2's dual-side panel
  building. The GUI also queries the state when it opens, so the display matches the server after a relog.
- **Diagnostics**: the server logs an INFO line whenever a toggle is applied (machine class, position,
  whether it is a pattern medium), and the CPU logs a WARN if a medium was just asked to enable but the
  server still sees it off — this class of "I ticked it and nothing happened" is no longer silent.
- Unchanged: the CPU push/accounting logic, the NBT key, single-player behaviour, and the existing
  container-based path used by the AE2 ME interface (which already worked server-side).

## Previous releases

- **3.20.0-fix7**: fixed blank row names and uneditable emitter amounts in the Stock Monitor Terminal
  (MUI2 `ButtonWidget` extends `SingleChildWidget`, so a second child silently disposes the first —
  widget text now goes through `overlay(IKey)`; `LevelType` constants are `ITEM_LEVEL`/`ENERGY_LEVEL`).
- **3.20.0-fix6**: the Stock Monitor Terminal's cover list now shows only covers of the network the terminal
  is bound to, and reports how many were hidden from other networks.
- **3.20.0-fix5**: Stock Monitor Terminal highlight (10 s) and cross-dimension teleport buttons plus UI and
  localization fixes.

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

- **3.20.0-fix3 fix: the Stock Monitor Terminal (32107) GUI showed only its two section headers**
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

- **3.20.0-fix2 fix**: four in-game guide (GuideNH) pages never showed an icon — the log repeated
  `Couldn't find icon item ae2_qof:...`. Those pages' `icon:` / `item_ids:` used invented names, but the
  machines are **GregTech machines**: their real registry name is `gregtech:gt.blockmachines` plus meta
  (the MTE ID), e.g. the Universal Maintenance Hatch is `gregtech:gt.blockmachines:32000`. This release
  rewrites all such ids on 5 pages (including one that never logged an error: the AE2 cutting-knife page,
  whose real id is `appliedenergistics2:item.ToolCertusQuartzCuttingKnife`) in both languages, and
  cross-audits every declared id against this mod's actual registry names.

- **3.20.0-fix1 fix**: in 3.20.0 the optional-dependency guard used PH's **package prefix** (`proghatches`)
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
2. In a matching GTNH installation, replace the old QoL JAR with `AE2-QoL-3.20.0-fix9.jar`. Do not retain multiple versions.
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
