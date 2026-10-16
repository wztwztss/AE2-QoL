package com.wztwzt.ae2_qof.proline;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 产线绑定机器坐标引用（设计 §2.1 MachineRef）。
 *
 * <p>
 * 位置即身份（同覆盖板条目先例）：同坐标拆了再放回 = 同一台机器（我们不做 UUID 跟踪，
 * GT 机器也没有持久 UUID——万一方块坐标不变就仍算同机，行为可预期）。
 * {@code lastSeenEpochMs} 是最近一次采样轮扫到它存活的时间，用于"离线/失效"展示（O5）。
 */
public final class MachineRef {

    public final int dim;
    public final int x;
    public final int y;
    public final int z;
    /** 最近一次扫描到它存活的时间。0 = 从未扫到（刚绑定即离线展示）。 */
    public long lastSeenEpochMs;

    public MachineRef(int dim, int x, int y, int z, long lastSeenEpochMs) {
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastSeenEpochMs = lastSeenEpochMs;
    }

    /** 坐标语义 equals/hashCode：lastSeen 不参与（否则每次采样都会被误判成"变了"）。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MachineRef)) return false;
        MachineRef r = (MachineRef) o;
        return dim == r.dim && x == r.x && y == r.y && z == r.z;
    }

    @Override
    public int hashCode() {
        return (dim * 31 + x) * 433494437 + y * 31 + z;
    }

    public NBTTagCompound writeNbt(NBTTagCompound tag) {
        tag.setInteger("Dim", dim);
        tag.setInteger("X", x);
        tag.setInteger("Y", y);
        tag.setInteger("Z", z);
        tag.setLong("Last", lastSeenEpochMs);
        return tag;
    }

    public static MachineRef readNbt(NBTTagCompound tag) {
        return new MachineRef(
            tag.getInteger("Dim"),
            tag.getInteger("X"),
            tag.getInteger("Y"),
            tag.getInteger("Z"),
            tag.getLong("Last"));
    }
}
