package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveHatchHelper;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetwork;
import com.wztwzt.ae2_qof.hatch.adaptive.AdaptiveNetworkManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class HatchActionPacket implements IMessage {

    public static final int ACTION_HIGHLIGHT = 0;
    public static final int ACTION_TELEPORT = 1;

    private static final java.util.List<long[]> pendingClears = java.util.Collections
        .synchronizedList(new java.util.ArrayList<long[]>());

    public static void scheduleClear(EntityPlayerMP player, int clearTick) {
        pendingClears.add(new long[] { player.getEntityId(), clearTick });
    }

    public static void tickPendingClears() {
        if (pendingClears.isEmpty()) return;
        int currentTick = MinecraftServer.getServer().getTickCounter();
        java.util.Iterator<long[]> it = pendingClears.iterator();
        while (it.hasNext()) {
            long[] entry = it.next();
            if (currentTick >= (int) entry[1]) {
                it.remove();
                EntityPlayerMP player = findPlayer((int) entry[0]);
                if (player != null) {
                    ModNetwork.CHANNEL.sendTo(
                        new WirelessHighlightPacket(new java.util.ArrayList<int[]>(), false), player);
                }
            }
        }
    }

    private static EntityPlayerMP findPlayer(int entityId) {
        for (Object obj : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) obj;
            if (p.getEntityId() == entityId) return p;
        }
        return null;
    }

    private int action;
    private String hatchOwner;
    private int hatchFrequency;
    private int hatchIndex;

    public HatchActionPacket() {}

    public HatchActionPacket(int action, String hatchOwner, int hatchFrequency, int hatchIndex) {
        this.action = action;
        this.hatchOwner = hatchOwner;
        this.hatchFrequency = hatchFrequency;
        this.hatchIndex = hatchIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.action = buf.readInt();
            boolean hasOwner = buf.readBoolean();
            this.hatchOwner = hasOwner ? readString(buf) : null;
            this.hatchFrequency = buf.readInt();
            this.hatchIndex = buf.readInt();
        } catch (Throwable t) {
            this.action = -1;
            this.hatchOwner = null;
            this.hatchFrequency = 0;
            this.hatchIndex = -1;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        buf.writeBoolean(hatchOwner != null);
        if (hatchOwner != null) {
            writeString(buf, hatchOwner);
        }
        buf.writeInt(hatchFrequency);
        buf.writeInt(hatchIndex);
    }

    private void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private String readString(ByteBuf buf) {
        int len = buf.readShort();
        if (len < 0 || len > 1024) return "";
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<HatchActionPacket, IMessage> {

        @Override
        public IMessage onMessage(HatchActionPacket msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            ServerTerminalHelper.scheduleServerTask(() -> handleServer(player, msg));
            return null;
        }

        private void handleServer(EntityPlayerMP player, HatchActionPacket msg) {
            try {
                if (player.openContainer == null) return;

                UUID uuid;
                try {
                    uuid = UUID.fromString(msg.hatchOwner);
                } catch (IllegalArgumentException e) {
                    return;
                }

                AdaptiveNetwork network = AdaptiveNetworkManager.getNetwork(uuid, msg.hatchFrequency);
                if (network == null) return;

                java.util.List<AdaptiveHatchHelper> helpers = network.getAllHelpers();
                if (msg.hatchIndex < 0 || msg.hatchIndex >= helpers.size()) return;

                AdaptiveHatchHelper helper = helpers.get(msg.hatchIndex);

                int x = helper.getX();
                int y = helper.getY();
                int z = helper.getZ();
                int dim = helper.getDim();

                WorldServer world = MinecraftServer.getServer().worldServerForDimension(dim);
                if (world == null) return;

                TileEntity te = world.getTileEntity(x, y, z);
                if (te == null) return;

                switch (msg.action) {
                    case ACTION_HIGHLIGHT:
                        handleHighlight(player, x, y, z, dim);
                        break;
                    case ACTION_TELEPORT:
                        if (player.getUniqueID().equals(uuid)) {
                            handleTeleport(player, x, y, z);
                        }
                        break;
                    default:
                        break;
                }
            } catch (Throwable t) {
                MyMod.LOG.error("Hatch action failed", t);
            }
        }

        private void handleHighlight(EntityPlayerMP player, int x, int y, int z, int dim) {
            java.util.List<int[]> positions = new ArrayList<>(Collections.singletonList(new int[] { dim, x, y, z }));
            ModNetwork.CHANNEL.sendTo(new WirelessHighlightPacket(positions, true), player);
            scheduleClear(player, MinecraftServer.getServer().getTickCounter() + 100);
        }

        private void handleTeleport(EntityPlayerMP player, int x, int y, int z) {
            player.setPositionAndUpdate(x + 0.5, y + 1, z + 0.5);
        }
    }
}
