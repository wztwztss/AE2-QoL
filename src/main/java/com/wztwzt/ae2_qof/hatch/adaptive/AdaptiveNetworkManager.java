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
