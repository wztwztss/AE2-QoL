package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AdaptiveNetworkManager {

    private static final Map<String, AdaptiveNetwork> networks = new HashMap<>();

    public static String makeKey(UUID owner, int frequency) {
        if (owner == null) return "null:" + frequency;
        return owner.toString() + ":" + frequency;
    }

    public static AdaptiveNetwork getOrCreateNetwork(UUID owner, int frequency) {
        String key = makeKey(owner, frequency);
        AdaptiveNetwork network = networks.get(key);
        if (network == null) {
            network = new AdaptiveNetwork(owner, frequency);
            networks.put(key, network);
        }
        return network;
    }

    public static AdaptiveNetwork getNetwork(UUID owner, int frequency) {
        return networks.get(makeKey(owner, frequency));
    }

    public static void removeNetwork(UUID owner, int frequency) {
        String key = makeKey(owner, frequency);
        AdaptiveNetwork network = networks.remove(key);
        if (network != null) {
            network.destroy();
        }
    }

    public static void registerTerminal(AdaptiveNetTerminal terminal) {
        UUID owner = terminal.getNetworkOwner();
        int frequency = terminal.getNetworkFrequency();
        if (owner == null) return;

        AdaptiveNetwork network = getOrCreateNetwork(owner, frequency);
        if (network.getTerminal() == null) {
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
        UUID owner = helper.getNetworkOwner();
        int frequency = helper.getNetworkFrequency();
        if (owner == null) return;

        AdaptiveNetwork network = getOrCreateNetwork(owner, frequency);
        network.addHelper(helper);
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
}
