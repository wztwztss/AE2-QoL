package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AdaptiveNetwork {

    private final UUID owner;
    private final int frequency;
    private AdaptiveNetTerminal terminal;
    private final Set<AdaptiveHatchHelper> helpers = new LinkedHashSet<>();
    private int voltageTier = 0;
    private boolean autoReconnect = true;

    private final int[] hatchTiers = new int[HatchType.COUNT];
    private final int[] hatchAmps = new int[HatchType.COUNT];
    private GridEnergyStats stats = new GridEnergyStats();
    private boolean hatchListDirty = true;
    private int saveCounter = 0;
    private final Set<UUID> activeViewers = new HashSet<>();

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
        markHatchListDirty();
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
        markHatchListDirty();
    }

    public void setHatchAmps(HatchType type, int amps) {
        hatchAmps[type.slotIndex] = Math.max(1, amps);
        markHatchListDirty();
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
        HatchType ht = helper.getHatchType();
        if (ht != null) {
            helper.setVoltageTier(hatchTiers[ht.slotIndex]);
            helper.setAmps(hatchAmps[ht.slotIndex]);
        }
        hatchListDirty = true;
    }

    public void removeHelper(AdaptiveHatchHelper helper) {
        helpers.remove(helper);
        hatchListDirty = true;
    }

    public List<AdaptiveHatchHelper> getAllHelpers() {
        return new ArrayList<>(helpers);
    }

    public boolean isHatchListDirty() {
        return hatchListDirty;
    }

    public void clearHatchListDirty() {
        hatchListDirty = false;
    }

    public void markHatchListDirty() {
        hatchListDirty = true;
    }

    public void addViewer(UUID playerUUID) {
        if (playerUUID != null) {
            activeViewers.add(playerUUID);
            hatchListDirty = true;
        }
    }

    public void removeViewer(UUID playerUUID) {
        if (playerUUID != null) activeViewers.remove(playerUUID);
    }

    public Set<UUID> getActiveViewers() {
        return activeViewers;
    }

    public boolean isEmpty() {
        return terminal == null && helpers.isEmpty();
    }

    public void updateAllHelpers() {
        for (AdaptiveHatchHelper helper : helpers) {
            if (helper.isBound()) {
                HatchType ht = helper.getHatchType();
                if (ht != null) {
                    helper.setVoltageTier(hatchTiers[ht.slotIndex]);
                    helper.setAmps(hatchAmps[ht.slotIndex]);
                }
            }
        }
    }

    public GridEnergyStats getStats() {
        return stats;
    }

    public void replaceStats(GridEnergyStats newStats) {
        this.stats = newStats;
    }

    public void tickStats(long currentEU) {
        stats.tick(currentEU);
        saveCounter++;
        if (saveCounter >= 6000) {
            saveCounter = 0;
            AdaptiveNetworkManager.saveStatsForKey(owner, frequency);
        }
    }

    public void destroy() {
        // 只清理网络自身的引用，不修改仓室的绑定状态。
        // 之前调用 helper.unbind() 会把 networkOwner 设为 null，导致退出存档时
        // NBT 保存丢失绑定信息，重进后显示"未绑定"。
        helpers.clear();
        terminal = null;
    }

    private Integer detectHelperType(AdaptiveHatchHelper helper) {
        HatchType type = helper.getHatchType();
        if (type != null) return type.slotIndex;
        return null;
    }
}
