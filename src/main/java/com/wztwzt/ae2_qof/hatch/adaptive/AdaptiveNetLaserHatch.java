package com.wztwzt.ae2_qof.hatch.adaptive;

import static gregtech.api.enums.GTValues.V;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

import gregtech.common.misc.WirelessNetworkManager;

public class AdaptiveNetLaserHatch extends MTEHatchEnergy {

    private static final Logger LOG = LogManager.getLogger("AE2QoL");
    protected final AdaptiveHatchHelper helper = new AdaptiveHatchHelper();
    private long lastStoredEU = 0;
    private net.minecraft.world.World world;

    public AdaptiveNetLaserHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
        helper.setHatchType(HatchType.LASER_SOURCE);
    }

    public AdaptiveNetLaserHatch(String aName, int aTier, String[] aDesc, ITexture[][][] aTextures) {
        super(aName, aTier, aDesc, aTextures);
        helper.setHatchType(HatchType.LASER_SOURCE);
    }

    public AdaptiveHatchHelper getHelper() {
        return helper;
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_hatch.desc"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_hatch.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_hatch.desc.1"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof"
        };
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAYS_ENERGY_ON_WIRELESS[helper.getCurrentVoltageTier() + 1]) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAYS_ENERGY_ON_WIRELESS[helper.getCurrentVoltageTier() + 1]) };
    }

    @Override
    public long maxEUInput() {
        return V[helper.getCurrentVoltageTier()] * 2;
    }

    @Override
    public long maxEUStore() {
        return Long.MAX_VALUE / 2;
    }

    @Override
    public long maxAmperesIn() {
        return helper.getCurrentAmps();
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBase, long aTick) {
        super.onPreTick(aBase, aTick);
        if (aBase.isServerSide() && helper.isBound() && aTick % 4 == 0) {
            UUID owner = helper.getNetworkOwner();
            if (owner == null) return;
            AdaptiveNetwork network = AdaptiveNetworkManager.getNetwork(owner, helper.getNetworkFrequency());
            if (network != null) {
                HatchType ht = helper.getHatchType();
                if (ht != null) {
                    helper.setVoltageTier(network.getHatchTiers()[ht.slotIndex]);
                    helper.setAmps(network.getHatchAmps()[ht.slotIndex]);
                }
            }
            long currentStored = aBase.getStoredEU();
            long consumed = lastStoredEU - currentStored;
            if (consumed > 0) {
                WirelessNetworkManager.addEUToGlobalEnergyMap(owner, BigInteger.valueOf(-consumed));
            }
            helper.setRealFlowEUt(consumed > 0 ? (int) consumed : 0);
            BigInteger gridEU = WirelessNetworkManager.getUserEU(owner);
            long maxStore = maxEUStore();
            long halfStore = maxStore / 2;
            if (currentStored < halfStore) {
                long target = Math.min(halfStore, currentStored + gridEU.longValue());
                long diff = target - currentStored;
                if (diff > 0) {
                    aBase.increaseStoredEnergyUnits(diff, false);
                }
            }
            lastStoredEU = aBase.getStoredEU();
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBase) {
        super.onFirstTick(aBase);
        if (aBase.isServerSide()) {
            this.world = aBase.getWorld();
            helper.setPosition(aBase.getXCoord(), aBase.getYCoord(), aBase.getZCoord(), aBase.getWorld().provider.dimensionId);
            gregtech.api.metatileentity.MetaTileEntity mte = (gregtech.api.metatileentity.MetaTileEntity) aBase.getMetaTileEntity();
            if (mte != null) {
                net.minecraft.item.ItemStack stack = mte.getStackForm(1L);
                if (stack != null) {
                    helper.setCachedInfo((short) stack.getItemDamage(), stack.getDisplayName());
                } else {
                    helper.setCachedInfo((short) -1,
                        net.minecraft.util.StatCollector.translateToLocal(helper.getHatchType().getTranslationKey()));
                }
            }
            lastStoredEU = aBase.getStoredEU();
            if (helper.isBound()) {
                AdaptiveNetworkManager.registerHatch(helper, world);
            }
        }
    }

    @Override
    public void onRemoval() {
        if (helper.isBound()) {
            AdaptiveNetworkManager.unregisterHatch(helper);
        }
        super.onRemoval();
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (helper.handleDataStickRightClick(aPlayer)) {
            return true;
        }
        openGui(aPlayer);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        ModularPanel panel = ModularPanel.defaultPanel("adaptive_laser_hatch", 280, 200);

        Flow column = Flow.column().coverChildren().childPadding(6).top(10).left(10);

        column.child(
            new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_laser_hatch.title")).size(260, 16));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            if (!helper.isBound()) {
                return EnumChatFormatting.RED + "● "
                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.unbound");
            }
            return EnumChatFormatting.GREEN + "● "
                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.bound");
        })).size(260, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            int v = helper.getCurrentVoltageTier();
            String tierName = GTUtility.getColoredTierNameFromTier((byte) v);
            return EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.voltage")
                + " " + tierName + " (" + V[v] + " EU/t)";
        })).size(260, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            return EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.amperage")
                + " " + EnumChatFormatting.WHITE + maxAmperesIn() + " A";
        })).size(260, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            java.math.BigInteger gridEU = helper.isBound() && helper.getNetworkOwner() != null
                ? WirelessNetworkManager.getUserEU(helper.getNetworkOwner()) : java.math.BigInteger.ZERO;
            return EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.grid_energy")
                + " " + EnumChatFormatting.WHITE + formatEU(gridEU.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue());
        })).size(260, 14));

        panel.bindPlayerInventory();
        panel.child(column);
        return panel;
    }

    private static String formatEU(long eu) {
        if (eu >= 1_000_000_000_000_000_000L) return String.format("%.2fE", eu / 1_000_000_000_000_000_000.0);
        if (eu >= 1_000_000_000_000_000L) return String.format("%.2fP", eu / 1_000_000_000_000_000.0);
        if (eu >= 1_000_000_000_000L) return String.format("%.2fT", eu / 1_000_000_000_000.0);
        if (eu >= 1_000_000_000L) return String.format("%.2fG", eu / 1_000_000_000.0);
        if (eu >= 1_000_000L) return String.format("%.2fM", eu / 1_000_000.0);
        if (eu >= 1_000L) return String.format("%.1fK", eu / 1_000.0);
        return String.valueOf(eu);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        return new AdaptiveNetLaserHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        helper.saveNBT(aNBT);
        aNBT.setLong("ae2qolLS", lastStoredEU);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        helper.loadNBT(aNBT);
        lastStoredEU = aNBT.getLong("ae2qolLS");
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity a, int i, net.minecraftforge.common.util.ForgeDirection s,
        ItemStack stack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity a, int i, net.minecraftforge.common.util.ForgeDirection s,
        ItemStack stack) {
        return false;
    }

    @Override
    public void getWailaBody(ItemStack itemStack, java.util.List<String> currenttip,
        mcp.mobius.waila.api.IWailaDataAccessor accessor, mcp.mobius.waila.api.IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currenttip, accessor, config);
        try {
            int v = helper.getCurrentVoltageTier();
            String tierName = GTUtility.getColoredTierNameFromTier((byte) v);
            currenttip.add(EnumChatFormatting.AQUA + "V:" + tierName + " (" + V[v] + ") | A:" + helper.getCurrentAmps());
            java.math.BigInteger gridEU = helper.isBound() && helper.getNetworkOwner() != null
                ? WirelessNetworkManager.getUserEU(helper.getNetworkOwner()) : java.math.BigInteger.ZERO;
            currenttip.add(EnumChatFormatting.AQUA + "Grid: " + formatEU(gridEU.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue()));
            if (helper.isBound()) {
                currenttip.add(EnumChatFormatting.GREEN
                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.bound"));
            }
        } catch (Exception ignored) {
        }
    }
}
