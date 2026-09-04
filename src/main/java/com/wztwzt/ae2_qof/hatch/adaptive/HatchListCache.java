package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HatchListCache {

    public final int totalCount;
    public final List<HatchEntry> entries;
    public final String inputCountText;
    public final String outputCountText;

    public static final Comparator<HatchEntry> EU_T_DESC = (a, b) -> Integer.compare(b.realFlowEUt, a.realFlowEUt);

    public HatchListCache(int totalCount, List<HatchEntry> entries,
                          String inputCountText, String outputCountText) {
        this.totalCount = totalCount;
        entries.sort(EU_T_DESC);
        this.entries = Collections.unmodifiableList(entries);
        this.inputCountText = inputCountText;
        this.outputCountText = outputCountText;
    }

    public static class HatchEntry {
        public final String name;
        public final short metaId;
        public final int machineMetaId;
        public final long eut;
        public final int realFlowEUt;
        public final int tier;
        public final int amps;
        public final int hatchType;
        public final int index;
        public final int x, y, z, dim;
        public final String ownerName;

        public HatchEntry(String name, short metaId, int machineMetaId, long eut, int realFlowEUt, int tier, int amps,
                         int hatchType, int index, int x, int y, int z, int dim,
                         String ownerName) {
            this.name = name != null ? name : "";
            this.metaId = metaId;
            this.machineMetaId = machineMetaId;
            this.eut = eut;
            this.realFlowEUt = realFlowEUt;
            this.tier = tier;
            this.amps = Math.max(1, amps);
            this.hatchType = hatchType;
            this.index = index;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.ownerName = ownerName != null ? ownerName : "";
        }
    }
}
