package com.wztwzt.ae2_qof.hatch.adaptive;

public enum HatchType {

    DYNAMO(0, 1, 1),
    ENERGY(1, 1, 1),
    LASER_SOURCE(2, 256, 6),
    LASER_TARGET(3, 256, 6);

    public static final int COUNT = values().length;

    public final int slotIndex;
    public final int defaultAmps;
    public final int defaultTier;

    HatchType(int slotIndex, int defaultAmps, int defaultTier) {
        this.slotIndex = slotIndex;
        this.defaultAmps = defaultAmps;
        this.defaultTier = defaultTier;
    }

    public static HatchType fromSlotIndex(int index) {
        for (HatchType type : values()) {
            if (type.slotIndex == index) return type;
        }
        return null;
    }

    public static int detectTypeFromItemName(String name) {
        if (name == null) return -1;
        String lower = name.toLowerCase();

        boolean hasDynamo = lower.contains("dynamo");
        boolean hasEnergy = lower.contains("energy");
        boolean hasTunnel = lower.contains("tunnel");
        boolean hasLaser = lower.contains("laser");
        boolean isWireless = lower.contains("wireless");

        if (hasTunnel || hasLaser) {
            if (hasDynamo) return LASER_SOURCE.slotIndex;
            if (hasEnergy) return LASER_TARGET.slotIndex;
        }

        if (hasDynamo) return DYNAMO.slotIndex;
        if (hasEnergy) return ENERGY.slotIndex;

        return -1;
    }
}
