package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AdaptiveNetwork {

    private final UUID owner;
    private final int frequency;
    private AdaptiveNetTerminal terminal;
    private final Set<AdaptiveHatchHelper> helpers = new HashSet<>();
    private int voltageTier = 0;
    private boolean autoReconnect = true;

    private final int[] hatchTiers = new int[HatchType.COUNT];
    private final int[] hatchAmps = new int[HatchType.COUNT];

    public AdaptiveNetwork(UUID owner, int frequency) {
        this.owner = owner;
        this.frequency = frequency;
        for (HatchType type : HatchType.values()) {
            hatchAmps[type.slotIndex] = type.defaultAmps;
        }
    }

    public UUID getOwner() {
        return owner;
    }

    public int getFrequency() {
        return frequency;
    }

    public AdaptiveNetTerminal getTerminal() {
        return terminal;
    }

    public void setTerminal(AdaptiveNetTerminal terminal) {
        this.terminal = terminal;
        if (terminal != null) {
            this.voltageTier = terminal.getTargetVoltageTier();
            this.autoReconnect = terminal.isAutoReconnect();
        }
    }

    public int getVoltageTier() {
        return voltageTier;
    }

    public void setVoltageTier(int voltageTier) {
        this.voltageTier = Math.max(0, Math.min(voltageTier, 15));
        updateAllHelpers();
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public int[] getHatchTiers() {
        return hatchTiers;
    }

    public int[] getHatchAmps() {
        return hatchAmps;
    }

    public void setHatchTier(HatchType type, int tier) {
        hatchTiers[type.slotIndex] = Math.max(0, Math.min(tier, 15));
    }

    public void setHatchAmps(HatchType type, int amps) {
        hatchAmps[type.slotIndex] = Math.max(1, amps);
    }

    public int getHatchCount(HatchType type) {
        int count = 0;
        for (AdaptiveHatchHelper h : helpers) {
            Integer hType = detectHelperType(h);
            if (hType != null && hType == type.slotIndex) count++;
        }
        return count;
    }

    public int getTotalHatchCount() {
        return helpers.size();
    }

    public void addHelper(AdaptiveHatchHelper helper) {
        helpers.add(helper);
        helper.setVoltageTier(voltageTier);
    }

    public void removeHelper(AdaptiveHatchHelper helper) {
        helpers.remove(helper);
    }

    public List<AdaptiveHatchHelper> getAllHelpers() {
        return new ArrayList<>(helpers);
    }

    public boolean isEmpty() {
        return terminal == null && helpers.isEmpty();
    }

    public void updateAllHelpers() {
        for (AdaptiveHatchHelper helper : helpers) {
            if (helper.isBound()) {
                helper.setVoltageTier(voltageTier);
            }
        }
    }

    public void destroy() {
        for (AdaptiveHatchHelper helper : helpers) {
            if (helper.isBound()) {
                helper.unbind();
            }
        }
        helpers.clear();
        terminal = null;
    }

    private Integer detectHelperType(AdaptiveHatchHelper helper) {
        HatchType type = helper.getHatchType();
        if (type != null) return type.slotIndex;
        return null;
    }
}
