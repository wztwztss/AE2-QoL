---
navigation:
  title: Merged Terminal
  parent: index.md
  icon: ae2_qof:merged_terminal_part
item_ids:
  - ae2_qof:merged_terminal
  - ae2_qof:merged_terminal_part
  - ae2_qof:wireless_merged_terminal
author: wztwzt
---

# Pattern + Interface Merged Terminal

Merges the **pattern encoding panel** and the **interface management list** into a single GUI — manage interface patterns and encode patterns in one place.

## Three Forms

| Form | Obtained | Notes |
|---|---|---|
| Standalone block | Craft (iron + glass + redstone + diamond + paper) | Place and right-click; standard grid device |
| **Cable part** | Iron ingot + merged terminal block | Mounts on any ME cable face, looks like the native terminal part; requires channel + power |
| **Wireless handheld** | Diamond-block recipe | Open the full GUI anywhere, **any dimension**, no power needed |

All three share the same GUI and features; data is stored separately per form.

## Layout

- Left: interface list (all interfaces/assemblers on the network with pattern usage)
- Right: encoding panel (Crafting 3×3 / Processing 4×4×2 pages)

## Encoding Flow

1. Put ingredients into the panel inputs (use NEI `+` to auto-fill, or drag & drop).
2. In Processing mode use `Substitute` / `Be-Substitute` toggles as needed.
3. Press `Encode` — Processing produces **GT ultimate patterns**, Crafting produces normal encoded patterns; blank patterns are pulled from the network automatically.
4. Press `↑ Upload` — the pattern is sent to a provider with free slots (Shift+click forces the selection screen).
5. `← Recall` takes back the last matching pattern; `⇄ Swap` rotates primary/secondary outputs.

## Pattern Read-back

Put an encoded pattern back into the OUT slot to decode it into the panel — inputs/outputs/mode/substitution all restored. GT ultimate patterns and fluid display items are supported.

## Editing Snapshot

Closing the GUI saves all panel slots and the mode; reopening restores them. Storage per form:
block/part keep it in tile NBT, wireless keeps it in the item NBT.

## Wireless Binding

1. Build an **ME Security Station** and authorize yourself with a biometric card.
2. Put the Wireless Merged Terminal into the station's **encode slot** — done.
3. Right-click it from any dimension to access the bound network.
4. If the station is removed the terminal stops working ("Station can not be located"); rebind at a new station.

> Note: cross-dimensional access never bypasses network security — item access is still enforced by the security station's biometric cards.

## Middle-click Editing

- Middle-click a panel slot: amount editor (+1/+10/+100/+1000 and ×2/×8/×64/×512, Shift switches tier), no upper limit
- Shift+middle-click: rename the item
- Output slots cannot be amount-edited directly
