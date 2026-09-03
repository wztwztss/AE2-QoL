package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wztwzt.ae2_qof.item.ItemNetworkDataStick;

public class AdaptiveHatchHelper {

    private static final Logger LOG = LogManager.getLogger("AE2QoL");

    private UUID networkOwner;
    private int networkFrequency = 0;
    private int currentVoltageTier = 0;
    private int currentAmps = 1;
    private HatchType hatchType;
    private int posX, posY, posZ, posDim;
    private short cachedMetaId = -1;
    private String cachedName = "";
    private int realFlowEUt = 0;

    public HatchType getHatchType() {
        return hatchType;
    }

    public void setHatchType(HatchType hatchType) {
        this.hatchType = hatchType;
    }

    public UUID getNetworkOwner() {
        return networkOwner;
    }

    public int getNetworkFrequency() {
        return networkFrequency;
    }

    public int getCurrentVoltageTier() {
        return currentVoltageTier;
    }

    public int getCurrentAmps() {
        return currentAmps;
    }

    public void setVoltageTier(int tier) {
        this.currentVoltageTier = Math.max(0, Math.min(tier, 15));
    }

    public void setAmps(int amps) {
        this.currentAmps = Math.max(1, amps);
    }

    public int getX() { return posX; }
    public int getY() { return posY; }
    public int getZ() { return posZ; }
    public int getDim() { return posDim; }

    public void setPosition(int x, int y, int z, int dim) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.posDim = dim;
    }

    public short getCachedMetaId() { return cachedMetaId; }
    public String getCachedName() { return cachedName; }

    public void setCachedInfo(short metaId, String name) {
        this.cachedMetaId = metaId;
        this.cachedName = name != null ? name : "";
    }

    public int getRealFlowEUt() { return realFlowEUt; }
    public void setRealFlowEUt(int realFlowEUt) { this.realFlowEUt = realFlowEUt; }

    public boolean isBound() {
        return networkOwner != null && networkFrequency >= 0;
    }

    public void bind(UUID owner, int frequency) {
        if (isBound()) {
            LOG.info("[AE2QoL] Hatch rebind: oldOwner={}, oldFreq={} -> newOwner={}, newFreq={}",
                networkOwner, networkFrequency, owner, frequency);
            AdaptiveNetworkManager.unregisterHatch(this);
        } else {
            LOG.info("[AE2QoL] Hatch first bind: owner={}, freq={}, type={}", owner, frequency, hatchType);
        }
        this.networkOwner = owner;
        this.networkFrequency = frequency;
        AdaptiveNetworkManager.registerHatch(this);
    }

    public void unbind() {
        if (isBound()) {
            AdaptiveNetworkManager.unregisterHatch(this);
        }
        this.networkOwner = null;
        this.networkFrequency = 0;
        this.currentVoltageTier = 0;
    }

    public boolean handleDataStickRightClick(EntityPlayer aPlayer) {
        ItemStack heldItem = aPlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemNetworkDataStick)) {
            return false;
        }

        boolean hasData = ItemNetworkDataStick.hasData(heldItem);
        LOG.info("[AE2QoL] Hatch rightclick: player={}, hasData={}", aPlayer.getCommandSenderName(), hasData);

        if (hasData) {
            UUID owner = ItemNetworkDataStick.getOwner(heldItem);
            int freq = ItemNetworkDataStick.getFrequency(heldItem);
            LOG.info("[AE2QoL] Flash drive data: owner={}, freq={}", owner, freq);
            if (owner != null) {
                bind(owner, freq);
                if (!aPlayer.worldObj.isRemote) {
                    LOG.info("[AE2QoL] Hatch bound successfully: owner={}, freq={}, type={}", owner, freq, hatchType);
                    aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("ae2_qof.adaptive.bind.success")));
                }
            } else {
                if (!aPlayer.worldObj.isRemote) {
                    LOG.warn("[AE2QoL] Hatch bind failed: owner is null in flash drive");
                    aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("ae2_qof.adaptive.bind.fail")));
                }
            }
            return true;
        } else {
            if (!aPlayer.worldObj.isRemote) {
                LOG.info("[AE2QoL] Empty flash drive used on hatch");
                aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("ae2_qof.adaptive.bind.empty_stick")));
            }
            return true;
        }
    }

    public void saveNBT(NBTTagCompound aNBT) {
        if (networkOwner != null) {
            aNBT.setString("ae2qolNO", networkOwner.toString());
        }
        aNBT.setInteger("ae2qolNF", networkFrequency);
        aNBT.setInteger("ae2qolVT", currentVoltageTier);
        aNBT.setInteger("ae2qolPX", posX);
        aNBT.setInteger("ae2qolPY", posY);
        aNBT.setInteger("ae2qolPZ", posZ);
        aNBT.setInteger("ae2qolPD", posDim);
        aNBT.setShort("ae2qolMI", cachedMetaId);
        aNBT.setString("ae2qolMN", cachedName);
    }

    public void loadNBT(NBTTagCompound aNBT) {
        String s = aNBT.getString("ae2qolNO");
        if (s != null && !s.isEmpty()) {
            try {
                networkOwner = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        networkFrequency = aNBT.getInteger("ae2qolNF");
        currentVoltageTier = aNBT.getInteger("ae2qolVT");
        posX = aNBT.getInteger("ae2qolPX");
        posY = aNBT.getInteger("ae2qolPY");
        posZ = aNBT.getInteger("ae2qolPZ");
        posDim = aNBT.getInteger("ae2qolPD");
        cachedMetaId = aNBT.getShort("ae2qolMI");
        cachedName = aNBT.getString("ae2qolMN");
        if (cachedName == null) cachedName = "";
    }

    public void migrateTo(UUID newOwner, int newFrequency) {
        if (isBound()) {
            AdaptiveNetworkManager.unregisterHatch(this);
        }
        this.networkOwner = newOwner;
        this.networkFrequency = newFrequency;
        AdaptiveNetworkManager.registerHatch(this);
    }
}
