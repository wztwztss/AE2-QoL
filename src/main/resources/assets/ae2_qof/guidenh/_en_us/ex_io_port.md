---
navigation:
  title: Enhanced IO Port
  parent: index.md
  icon: ae2_qof:ex_io_port
item_ids:
  - ae2_qof:ex_io_port
author: wztwzt
---

# Enhanced IO Port

An enhanced version of the vanilla AE2 IO Port: the **per-operation transfer amount is scaled by a configurable multiplier**, enabling high-throughput item and fluid logistics.

## Use Cases

- Bulk harvesting intake, mass smelter feeding — anywhere vanilla IO throughput is a bottleneck.

## Configuring the Rate

Edit `config/ae2_qof/settings.json`:

```json
{
  "io_port_rate": 1024
}
```

- Default `1024`, range 1 – 2147483647
- Hot-reload (~1s after save)
- In game: `/ae2qof reload` to apply immediately, `/ae2qof status` to inspect

## Crafting

Iron ingots + glass + redstone + diamond (see recipe tab).

## Notes

- Very large rates: mind the throughput limits of the target container/network.
- Usage is identical to the vanilla IO Port: attach it to the target block and insert storage cells.
