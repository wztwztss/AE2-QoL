---
navigation:
  title: Knife Name Copy
  parent: index.md
item_ids:
  - appliedenergistics2:certus_quartz_cutting_knife
  - appliedenergistics2:nether_quartz_cutting_knife
author: wztwzt
---

# Cutting Knife Name Copy (Feature 10)

Right-click with any AE2 **quartz cutting knife** to copy the target's name to the system clipboard — no more hand-copying names for pattern outputs, scripts or docs.

## Supported Targets

- Vanilla blocks (localized name)
- GregTech single-block machines and multi-block controllers (custom/localized machine name)
- AE2 parts and devices (custom name)

## Usage

1. Hold any quartz cutting knife.
2. Right-click the target block.
3. A chat message confirms the copy; paste anywhere with `Ctrl+V`.

## Notes

- Handled as a high-priority event and does not conflict with the vanilla stone-cutting behavior (sneak-right-click stays vanilla)
- Client-side only, no network permission required
