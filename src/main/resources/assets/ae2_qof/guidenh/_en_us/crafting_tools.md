---
navigation:
  title: Notify & Replan
  parent: index.md
author: wztwzt
---

# Crafting Notification & Replan (Features 5/6)

Fire-and-forget crafting jobs with completion alerts; re-plan after stock changes without re-ordering.

## Crafting Notification (Feature 5)

After submitting a job to a CPU, a banner slides in at the top-right of the screen when the job **completes**:

- Shows the result icon + `xAmount` + item name
- Plays a level-up sound
- Queued jobs display one by one, then fade after a few seconds

Only the job submitter gets notified; jobs completed while offline are not re-sent.

## Crafted Output Pin Row

After submitting an order, its outputs are automatically **pinned to a dedicated top row** of the terminal:

- Pinned entries show the item's **full network storage** (not just the crafted amount)
- Rendered as its own row; the item grid shifts down — nothing is covered
- Rows auto-extend as more distinct outputs pile up (up to 3); row count can also be fixed in terminal settings
- Supported on **Wireless Terminal / Merged Terminal / standard ME Terminal** (the standard one needs Pins Rows enabled in its settings)

## Replan (Feature 6)

Scenario: materials in the network changed after ordering (restocked / partially consumed) and the vanilla job is stuck waiting.

Action: on the **Craft Confirm screen (GuiCraftConfirm)** a replan entry appears — clicking it re-submits `beginCraftingJob` for the already-simulated job so the CPU re-plans branches against current stock.

- No need to cancel and re-order
- Original amount settings are kept

> Applies while the job is simulated/waiting-for-materials. Meaningless for fully completed orders.
