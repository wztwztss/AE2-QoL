---
navigation:
  title: Wireless Networking
  parent: index.md
  icon: ae2_qof:wireless_transceiver
item_ids:
  - ae2_qof:wireless_transceiver
  - ae2_qof:wireless_connect
author: wztwzt
---

# Wireless Transceiver + Connector

Bridge ME networks without cables. **Transceiver pairs work across dimensions.**

## Components

| Item | Role |
|---|---|
| **Wireless Transceiver** (block) | Channel sender or receiver, configured in its GUI |
| **Wireless Connector** (item) | Bind regular AE devices (cables/machines) to a channel remotely |

## Transceiver Flow

1. Place two wireless transceivers (different dimensions allowed).
2. Open the GUI, type a channel name and press **Add** to create a global channel.
3. On side A select the channel and press **Set as Sender**; on side B pick the same channel and press **Set as Receiver** — connected automatically.
4. **Disconnect** pauses a link (settings kept); **Highlight** shows bound block positions in world.

## Connector Flow

1. Sneak + right-click any transceiver to "load" its channel into the connector.
2. Right-click any AE device (cable/machine) — that grid joins the remote network of the channel.
3. Right-click an already bound device again = unbind.
4. Devices in multiple dimensions can bind to the same channel; the transceiver pair bridges them.

## Channel Management

- Global channels are visible to everyone; deleting a channel tears down all of its senders
- Every button in the GUI (**Add / Remove / Highlight / Sender / Receiver / Disconnect**) has a hover tooltip

## Crafting

See recipe tab (transceiver: iron+gold+redstone; connector: iron+gold+redstone+diamond).
