---
navigation:
  title: Smart Wildcard Pattern
  parent: index.md
author: wztwzt
---

# Smart Wildcard Pattern

**One pattern covers a whole class of recipes.** For example "1× any ingot → 1× the matching plate":
you place just **one** pattern, and when the machine builds its pattern index the mod expands it into
"1 iron ingot → 1 iron plate", "1 copper ingot → 1 copper plate", … — each one a **legal ordinary pattern**.
That is why every existing pattern buffer accepts it and machines match it normally.

## How it differs from earlier approaches

| | Old way | Smart Wildcard Pattern |
|---|---|---|
| Pattern count | wildcard mod: 1 pattern but hand-written matchers; generators: hundreds of concrete patterns | **one** pattern, rules **derived from NEI** |
| Configuration | typing `ingot*` style matchers, dragging items | press **+** on a recipe in NEI |
| Programmed circuit | had to drag the circuit in as a normal item | **built-in circuit** (1..24), written into the machine's virtual circuit slot automatically |
| Exclusions | only two string levels | **per-candidate "exclude" button** in the preview → that pattern's blacklist |
| Expansion cap | unbounded (expanded on the server's network hook path — a known lag source) | default **512**, configurable, truncation **logged as WARN** |

## Making one (three steps)

1. Obtain the "Smart Wildcard Pattern" item (AE2 QoL creative tab; crafting recipe below).
2. **Right-click** it to open the config screen (three pages + a circuit page):
   - **Rules**: the rules currently stored on this pattern;
   - **Preview**: every concrete candidate this pattern covers, each with an **Exclude** button on the right —
     one click puts it into this pattern's **blacklist**; the preview uses the **same expander** as the machine,
     so anything shown here really is craftable;
   - **Blacklist**: add/remove exclusion strings manually (`*` and `?` supported);
   - **Circuit**: pick 1..24 (or "Clear (inherit)") and record non-consumed items such as molds, extruder shapes or lenses.
3. Open the recipe you want to cover in NEI (e.g. the rolling machine's "1 ingot → 1 plate") and press **+**:
   the screen derives rules **and** the recipe template on the spot; press **Save** and the server writes them into the pattern.

Then drop that pattern into any pattern buffer.

## Supported buffers

- GT **Crafting Input Buffer / Bus (ME)** (2714 / 2715; the slave follows the master)
- **PH family**: Programmable Crafting Input Buffer (22069), MK.II (22179) and this mod's **MK.III (32108)** (144 slots)
- **GTNL Super Crafting Input Hatch** (21504 / 21505)
- **AE2 ME Interface** (block / cable part)

## Programmed circuit

- Choose 1..24 on the **Circuit** page; when a machine reads the pattern this mod writes that number into the
  machine's **virtual circuit slot** (GT's official API: `IConfigurationCircuitSupport.getCircuitSlot()` +
  `GTUtility.getIntegratedCircuit()`).
- Priority: **pattern's own > slot setting > machine circuit**. If the pattern carries no circuit, the mod
  **never touches** the machine's existing circuit slot.
- If one machine holds several patterns with different circuits, the machine-level slot can only hold one value,
  so the last write wins (a limitation of GT's circuit slot itself).

## Config

| Key | Meaning | Default |
|---|---|---|
| `smart_wildcard_expand_cap` | Maximum candidates one wildcard pattern expands into. Over the cap it **truncates and logs a WARN** (never silently drops) | `512` |

## Known limits

- A pattern covers **one recipe class** (not "any recipe"): rules come from the recipe you derived; ore dictionary
  first, falling back to an exact display-name match;
- Fluids that NEI shows as placeholder items are **skipped** (they are never used for ore-dictionary derivation);
- Expansion happens at **index time** (placing/changing a pattern, loading NBT), not every tick, and is capped;
- **Gesture on machine pattern slots**: in the GT Crafting Input Buffer, the PH family and the GTNL Super Crafting Input
  Hatch, **Shift+middle-click** a pattern slot to open a circuit picker (1..24 / Clear (inherit) / record the held item as
  non-consumed). It is written into **that pattern's own NBT**, and when a machine reads the pattern the number is written
  into its virtual circuit slot. AE2 ME Interface slots are AE2's own slots (not MUI2 widgets), so the gesture does not fire
  there — use the **Circuit page** of the pattern instead.
