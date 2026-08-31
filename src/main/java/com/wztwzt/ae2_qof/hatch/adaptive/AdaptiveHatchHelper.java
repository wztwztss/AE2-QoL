package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.wztwzt.ae2_qof.item.ItemNetworkDataStick;

public class AdaptiveHatchHelper {

    private UUID networkOwner;
    private int networkFrequency = -1;
    private int currentVoltageTier = 0;
    private HatchType hatchType;

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

    public void setVoltageTier(int tier) {
        this.currentVoltageTier = Math.max(0, Math.min(tier, 15));
    }

    public boolean isBound() {
        return networkOwner != null && networkFrequency >= 0;
    }

    public void bind(UUID owner, int frequency) {
        if (isBound()) {
            AdaptiveNetworkManager.unregisterHatch(this);
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
        this.networkFrequency = -1;
        this.currentVoltageTier = 0;
    }

    public boolean handleDataStickRightClick(EntityPlayer aPlayer) {
        ItemStack heldItem = aPlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemNetworkDataStick)) {
            return false;
        }

        if (ItemNetworkDataStick.hasData(heldItem)) {
            UUID owner = ItemNetworkDataStick.getOwner(heldItem);
            int freq = ItemNetworkDataStick.getFrequency(heldItem);
            if (owner != null) {
                bind(owner, freq);
                if (!aPlayer.worldObj.isRemote) {
                    aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("ae2_qof.adaptive.bind.success")));
                }
            } else {
                if (!aPlayer.worldObj.isRemote) {
                    aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.RED
                            + StatCollector.translateToLocal("ae2_qof.adaptive.bind.fail")));
                }
            }
            return true;
        } else {
            if (!aPlayer.worldObj.isRemote) {
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
