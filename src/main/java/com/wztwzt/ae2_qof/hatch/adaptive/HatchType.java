package com.wztwzt.ae2_qof.hatch.adaptive;

import gregtech.api.metatileentity.MetaTileEntity;

public enum HatchType {

    DYNAMO(0, 1, 1, "ae2_qof.gui.adaptive_terminal.dynamo_hatch"),
    ENERGY(1, 1, 1, "ae2_qof.gui.adaptive_terminal.energy_hatch"),
    LASER_SOURCE(2, 256, 6, "ae2_qof.gui.adaptive_terminal.laser_source"),
    LASER_TARGET(3, 256, 6, "ae2_qof.gui.adaptive_terminal.laser_target");

    public static final int COUNT = values().length;

    public static final String[] VOLTAGE_NAMES = {
        "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV", "MAX"
    };

    public final int slotIndex;
    public final int defaultAmps;
    public final int defaultTier;
    public final String translationKey;

    HatchType(int slotIndex, int defaultAmps, int defaultTier, String translationKey) {
        this.slotIndex = slotIndex;
        this.defaultAmps = defaultAmps;
        this.defaultTier = defaultTier;
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public static HatchType fromSlotIndex(int index) {
        for (HatchType type : values()) {
            if (type.slotIndex == index) return type;
        }
        return null;
    }

    public static String getTierName(int tier) {
        if (tier >= 0 && tier < VOLTAGE_NAMES.length) return VOLTAGE_NAMES[tier];
        return "?";
    }

    public boolean isValidMTEType(MetaTileEntity mte) {
        switch (this) {
            case DYNAMO:       return mte.maxEUOutput() > 0;
            case ENERGY:       return mte.maxEUInput() > 0;
            case LASER_SOURCE: return mte.maxEUInput() > 0;
            case LASER_TARGET: return mte.maxEUOutput() > 0;
            default: return false;
        }
    }
}
