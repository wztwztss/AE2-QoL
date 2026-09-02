package com.wztwzt.ae2_qof.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.registry.GameRegistry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.wztwzt.ae2_qof.AE2QoLCreativeTab;
import com.wztwzt.ae2_qof.MyMod;

public class ItemNetworkDataStick extends Item {

    private static final Logger LOG = LogManager.getLogger("AE2QoL");

    public static final String NBT_OWNER = "ae2qolOwner";
    public static final String NBT_FREQUENCY = "ae2qolFreq";

    public ItemNetworkDataStick() {
        setUnlocalizedName("ae2_qof.network_data_stick");
        setTextureName("ae2_qof:network_data_stick");
        setCreativeTab(AE2QoLCreativeTab.INSTANCE);
        setMaxStackSize(1);
    }

    public ItemNetworkDataStick register() {
        GameRegistry.registerItem(this, "network_data_stick", MyMod.MODID);
        return this;
    }

    public static ItemStack createDataStick(UUID owner, int frequency) {
        ItemStack stack = new ItemStack(ItemRegistry.networkDataStick);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(NBT_OWNER, owner.toString());
        nbt.setInteger(NBT_FREQUENCY, frequency);
        stack.setTagCompound(nbt);
        return stack;
    }

    public static void writeData(ItemStack stack, UUID owner, int frequency) {
        if (stack == null || !(stack.getItem() instanceof ItemNetworkDataStick)) {
            LOG.warn("[AE2QoL] writeData: invalid stack, item={}",
                stack != null ? stack.getItem() : "null");
            return;
        }
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }
        nbt.setString(NBT_OWNER, owner.toString());
        nbt.setInteger(NBT_FREQUENCY, frequency);
        LOG.info("[AE2QoL] writeData: owner={}, freq={}", owner, frequency);
    }

    public static boolean hasData(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemNetworkDataStick)) return false;
        NBTTagCompound nbt = stack.getTagCompound();
        boolean result = nbt != null && nbt.hasKey(NBT_OWNER) && nbt.hasKey(NBT_FREQUENCY);
        LOG.info("[AE2QoL] hasData: result={}, nbt={}", result, nbt);
        return result;
    }

    public static UUID getOwner(ItemStack stack) {
        if (!hasData(stack)) return null;
        try {
            return UUID.fromString(stack.getTagCompound().getString(NBT_OWNER));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static int getFrequency(ItemStack stack) {
        if (!hasData(stack)) return 0;
        return stack.getTagCompound().getInteger(NBT_FREQUENCY);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        if (hasData(stack)) {
            UUID owner = getOwner(stack);
            int freq = getFrequency(stack);
            tooltip.add(EnumChatFormatting.GREEN
                + StatCollector.translateToLocal("ae2_qof.item.data_stick.bound"));
            tooltip.add(EnumChatFormatting.GRAY
                + StatCollector.translateToLocal("ae2_qof.item.data_stick.owner")
                + ": " + EnumChatFormatting.WHITE
                + (owner != null ? owner.toString().substring(0, 8) + "..." : "?"));
            tooltip.add(EnumChatFormatting.GRAY
                + StatCollector.translateToLocal("ae2_qof.item.data_stick.frequency")
                + ": " + EnumChatFormatting.WHITE + freq);
        } else {
            tooltip.add(EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("ae2_qof.item.data_stick.empty"));
        }
        tooltip.add(EnumChatFormatting.DARK_GRAY + "───────────────────");
        tooltip.add(EnumChatFormatting.AQUA
            + StatCollector.translateToLocal("ae2_qof.item.data_stick.use.write"));
        tooltip.add(EnumChatFormatting.AQUA
            + StatCollector.translateToLocal("ae2_qof.item.data_stick.use.bind"));
        tooltip.add(EnumChatFormatting.AQUA
            + StatCollector.translateToLocal("ae2_qof.item.data_stick.use.read"));
        tooltip.add(EnumChatFormatting.DARK_GRAY + "ae2qof");
    }

    public static class ItemRegistry {

        public static ItemNetworkDataStick networkDataStick;
    }
}
