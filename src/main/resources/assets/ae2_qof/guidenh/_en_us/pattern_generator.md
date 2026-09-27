---
navigation:
  title: Pattern Generator
  parent: index.md
author: wztwzt
---

# Pattern Generator

When "one wildcard pattern covers a whole recipe class" cannot express what you need (for example
**several inputs that are each a different material**), use this generator to bulk-produce a pile of
**ordinary patterns** from a **GT RecipeMap**. They are equivalent to hand-encoded patterns and every
pattern buffer accepts them.

## Usage (three steps)

1. Obtain the "Pattern Generator" item (crafted from one AE2 Blank Pattern; also in the AE2 QoL creative tab).
2. **Right-click** it and fill in:
   - **Recipe map**: the RecipeMap id or a fragment of it (`rolling` matches `gt.recipe.rolling`; if a fragment
     matches several maps the first is used **and the rest are listed in the log** — never a silent pick);
   - **Filters** (all "empty = disabled"; `*` and `?` supported; matched against **display names and ore names**):
     - Input/Output exclusion: skip the recipe when matched;
     - Input/Output ore: the recipe must contain a matching input/output;
     - NC item: the recipe must contain that **non-consumed** item (GT uses `stackSize = 0`, e.g. molds, extruder
       shapes, lenses);
   - **Cap**: how many patterns to produce this run (default 512).
3. Press **Generate**: the **server** scans the RecipeMap and produces the patterns; they go **into your inventory**
   (anything that does not fit is dropped at your feet, and you are told).

## How to read the result

Both the chat and the log give **full counts**, never just "generated N":

```
[AE2QoL] 样板生成：player=… stored=52 dropped=0 map=gt.recipe.rolling seen=120 produced=52 skippedFluid=8 filtered=60 truncated=0
```

- `seen` enabled recipes scanned; `produced` actually produced; `skippedFluid` skipped because the recipe
  involves **fluids** (not supported yet); `filtered` removed by your filters; `truncated` cut off by the cap
  (an extra WARN is logged).

## Compared with the Smart Wildcard Pattern

| Situation | Use |
|---|---|
| One recipe class (e.g. "any ingot → the matching plate") and you want **a single pattern slot** | Smart Wildcard Pattern |
| Several inputs of different materials; you need one specific recipe each | This generator (produces ordinary patterns) |
| You want patterns for **every** recipe of a machine at once | This generator |

## Origin and licence

The core of this generator (RecipeMap enumeration and filtering approach, pattern NBT encoding, filter feature
set) is **adapted from AE2PatternGen** (MIT licence); the adapted file keeps its copyright and licence notice in
its header. We did **not** bring over its cache, conflict resolution, virtual storage or its own network layer —
this mod reuses its existing write-back and logging paths instead. See `docs/THIRD_PARTY_NOTES.md`.
