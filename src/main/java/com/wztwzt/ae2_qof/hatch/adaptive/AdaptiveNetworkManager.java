package com.wztwzt.ae2_qof.hatch.adaptive;

import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AdaptiveNetworkManager {

    private static final Map<String, AdaptiveNetwork> networks = new HashMap<>();
    private static World serverWorld;

    public static String makeKey(UUID owner, int frequency) {
        if (owner == null) return "null:" + frequency;
        return owner.toString() + ":" + frequency;
    }

    public static AdaptiveNetwork getOrCreateNetwork(UUID owner, int frequency) {
        UUID leader = AdaptiveTeamHelper.resolveLeader(owner);
        if (leader == null) return null;
        String key = makeKey(leader, frequency);
        AdaptiveNetwork network = networks.get(key);
        if (network == null) {
            network = new AdaptiveNetwork(leader, frequency);
            networks.put(key, network);
        }
        return network;
    }

    public static AdaptiveNetwork getNetwork(UUID owner, int frequency) {
        UUID leader = AdaptiveTeamHelper.resolveLeader(owner);
        if (leader == null) return null;
        return networks.get(makeKey(leader, frequency));
    }

    public static void removeNetwork(UUID owner, int frequency) {
        UUID leader = AdaptiveTeamHelper.resolveLeader(owner);
        if (leader == null) return;
        String key = makeKey(leader, frequency);
        AdaptiveNetwork network = networks.remove(key);
        if (network != null) {
            saveStatsToDisk(network);
            network.destroy();
        }
    }

    public static void registerTerminal(AdaptiveNetTerminal terminal) {
        registerTerminal(terminal, null);
    }

    public static void registerTerminal(AdaptiveNetTerminal terminal, World world) {
        UUID owner = terminal.getNetworkOwner();
        int frequency = terminal.getNetworkFrequency();
        if (owner == null) return;

        if (world != null) {
            serverWorld = world;
        }

        AdaptiveNetwork network = getOrCreateNetwork(owner, frequency);
        if (network != null && network.getTerminal() == null) {
            loadStatsFromDisk(network);
            network.setTerminal(terminal);
        }
    }

    public static void unregisterTerminal(AdaptiveNetTerminal terminal) {
        UUID owner = terminal.getNetworkOwner();
        int frequency = terminal.getNetworkFrequency();
        if (owner == null) return;

        AdaptiveNetwork network = getNetwork(owner, frequency);
        if (network != null && network.getTerminal() == terminal) {
            network.setTerminal(null);
            if (network.isEmpty()) {
                removeNetwork(owner, frequency);
            }
        }
    }

    public static void registerHatch(AdaptiveHatchHelper helper) {
        registerHatch(helper, null);
    }

    public static void registerHatch(AdaptiveHatchHelper helper, World world) {
        UUID owner = helper.getNetworkOwner();
        int frequency = helper.getNetworkFrequency();
        if (owner == null) return;

        if (world != null) {
            serverWorld = world;
        }

        AdaptiveNetwork network = getOrCreateNetwork(owner, frequency);
        if (network != null) {
            network.addHelper(helper);
        }
    }

    public static void unregisterHatch(AdaptiveHatchHelper helper) {
        UUID owner = helper.getNetworkOwner();
        int frequency = helper.getNetworkFrequency();
        if (owner == null) return;

        AdaptiveNetwork network = getNetwork(owner, frequency);
        if (network != null) {
            network.removeHelper(helper);
            if (network.isEmpty()) {
                removeNetwork(owner, frequency);
            }
        }
    }

    public static void updateAllHatches(UUID owner, int frequency) {
        AdaptiveNetwork network = getNetwork(owner, frequency);
        if (network != null) {
            network.updateAllHelpers();
        }
    }

    public static void migrateHatches(UUID oldOwner, int oldFreq, UUID newOwner, int newFreq) {
        AdaptiveNetwork oldNetwork = getNetwork(oldOwner, oldFreq);
        if (oldNetwork == null) return;

        for (AdaptiveHatchHelper helper : oldNetwork.getAllHelpers()) {
            helper.migrateTo(newOwner, newFreq);
        }
    }

    private static void loadStatsFromDisk(AdaptiveNetwork network) {
        if (serverWorld == null) return;
        String key = makeKey(network.getOwner(), network.getFrequency());
        GridEnergyWorldData worldData = GridEnergyWorldData.get(serverWorld);
        GridEnergyStats saved = worldData.getStats(key);
        if (saved != null) {
            network.replaceStats(saved);
        }
    }

    private static void saveStatsToDisk(AdaptiveNetwork network) {
        if (serverWorld == null) return;
        String key = makeKey(network.getOwner(), network.getFrequency());
        GridEnergyWorldData worldData = GridEnergyWorldData.get(serverWorld);
        GridEnergyStats stats = network.getStats();
        if (stats != null) {
            worldData.setStats(key, stats);
            worldData.markDirty();
        }
    }

    public static void saveAllStats() {
        if (serverWorld == null) return;
        GridEnergyWorldData worldData = GridEnergyWorldData.get(serverWorld);
        for (AdaptiveNetwork network : networks.values()) {
            String key = makeKey(network.getOwner(), network.getFrequency());
            GridEnergyStats stats = network.getStats();
            if (stats != null) {
                worldData.setStats(key, stats);
            }
        }
        worldData.markDirty();
    }

    /**
     * P1-012：服务器停止时保存统计并清空全部静态引用。
     *
     * 历史实现只保存统计，networks 与 serverWorld 会一直留在 JVM 静态字段里。
     * 单机退回主菜单再进新存档时，旧世界的终端/仓室/网络仍然存在，
     * 新终端可能因旧引用占位而注册失败，或被误判为已连接。
     * 这里在保存后统一断开并清空，保证每个存档从干净状态开始。
     */
    public static void shutdown() {
        try {
            saveAllStats();
        } catch (Throwable t) {
            com.wztwzt.ae2_qof.MyMod.LOG.warn("[AE2QoL] adaptive stats save on shutdown failed", t);
        }
        for (AdaptiveNetwork network : networks.values()) {
            try {
                network.destroy();
            } catch (Throwable ignored) {}
        }
        networks.clear();
        serverWorld = null;
    }

    public static void saveStatsForKey(UUID owner, int frequency) {
        if (serverWorld == null) return;
        AdaptiveNetwork network = getNetwork(owner, frequency);
        if (network == null) return;
        String key = makeKey(network.getOwner(), network.getFrequency());
        GridEnergyWorldData worldData = GridEnergyWorldData.get(serverWorld);
        GridEnergyStats stats = network.getStats();
        if (stats != null) {
            worldData.setStats(key, stats);
            worldData.markDirty();
        }
    }
}
