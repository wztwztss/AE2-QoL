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

## Replan (Feature 6)

Scenario: materials in the network changed after ordering (restocked / partially consumed) and the vanilla job is stuck waiting.

Action: on the **Craft Confirm screen (GuiCraftConfirm)** a replan entry appears — clicking it re-submits `beginCraftingJob` for the already-simulated job so the CPU re-plans branches against current stock.

- No need to cancel and re-order
- Original amount settings are kept

> Applies while the job is simulated/waiting-for-materials. Meaningless for fully completed orders.
