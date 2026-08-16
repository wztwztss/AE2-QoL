package com.wztwzt.ae2_qof.wireless.link;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

public class WirelessBlockLinkData {

    public int x;
    public int y;
    public int z;
    public String frequency;
    public UUID ownerUuid;
    public int dimension;
    public int direction;

    public WirelessBlockLinkData() {}

    public WirelessBlockLinkData(int x, int y, int z, String frequency, UUID ownerUuid, int dimension, int direction) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.frequency = frequency;
        this.ownerUuid = ownerUuid;
        this.dimension = dimension;
        this.direction = direction;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        tag.setString("frequency", frequency != null ? frequency : "");
        if (ownerUuid != null) {
            tag.setLong("uuidMost", ownerUuid.getMostSignificantBits());
            tag.setLong("uuidLeast", ownerUuid.getLeastSignificantBits());
        }
        tag.setInteger("dimension", dimension);
        tag.setInteger("direction", direction);
        return tag;
    }

    public static WirelessBlockLinkData readFromNBT(NBTTagCompound tag) {
        WirelessBlockLinkData data = new WirelessBlockLinkData();
        data.x = tag.getInteger("x");
        data.y = tag.getInteger("y");
        data.z = tag.getInteger("z");
        data.frequency = tag.getString("frequency");
        if (data.frequency == null) data.frequency = "";
        if (tag.hasKey("uuidMost", Constants.NBT.TAG_LONG) && tag.hasKey("uuidLeast", Constants.NBT.TAG_LONG)) {
            data.ownerUuid = new UUID(tag.getLong("uuidMost"), tag.getLong("uuidLeast"));
        }
        data.dimension = tag.getInteger("dimension");
        data.direction = tag.getInteger("direction");
        return data;
    }

    public String getPositionKey() {
        return dimension + ":" + x + ":" + y + ":" + z;
    }
}
