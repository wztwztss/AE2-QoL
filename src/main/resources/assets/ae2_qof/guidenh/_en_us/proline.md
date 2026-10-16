---
navigation:
  title: Production Line Manager
  parent: index.md
item_ids:
  - ae2_qof:proline_card
author: wztwzt
---

# Production Line Manager

The Stock Monitor Terminal gains a third tab, "Lines": organize the team's loaded GT multiblocks as production-line steps, watch each step's running/idle state, main output, and cumulative throughput.

## Presets (recommended entry)

Top bar →【Presets】→ click a name to create the line (bundled:硅岩/独居石主线/氟碳镧铈主线/钐精粉主线/海晶酸线/湿件线/异氙线), then bind machines or tweak steps via NEI/import.

## Quick start

1. Top bar【+New Line】(requires a ServerUtilities **team**);
2. Click a line row to select → click a step row to open its mini panel;
3. Step panel: **pause/resume (real stop)**, write the held binding card, view candidates and bind/unbind;
4. Bottom summary lists external inputs and final outputs with BigInteger cumulative (persists across restarts) and ~rate (refreshed every 10s), plus the wiki `start[]` boundary expectations.

## Binding a machine (two ways)

- **Candidate picker** (recommended): loaded GT multiblocks in the team's claimed chunks; one machine belongs to one line.
- **Binding card** (fallback): select line+step in the terminal →【Write held binding card】→ `Shift+right-click` the target machine. Survival recipe: 4 paper + 1 iron ingot.

## Power semantics (real stop)

Pausing writes GT `disableWorking()` to bound machines; resuming writes `enableWorking()`. Unbinding or deleting a line always restores work allowance. Unloaded chunks read as offline; the sampler reapplies the pause state when they come back.

## Summary semantics

- Total input = items never produced by any step; total output = items never consumed (branch switching filters the view; recalculation lands in 4.2.0).
- Cumulative counters (BigInteger) persist across restarts; rate uses a 10s window, ring of 180 samples (30 min).
- Sampling only covers "active lines seen in a UI" (≤16, FIFO), single-line budget 3ms, backoff 5 minutes after failures.

## Permissions and red lines

- Create/edit/bind/pause = team members; lines are owned by the creator's team.
- 16 lines per team; 64 steps per line.
- Without ServerUtilities the whole page is disabled (three-state guard logging: enabled / not detected / failed).
