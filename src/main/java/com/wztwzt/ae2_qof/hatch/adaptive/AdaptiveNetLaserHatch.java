package com.wztwzt.ae2_qof.hatch.adaptive;

import static gregtech.api.enums.GTValues.V;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

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

public class AdaptiveNetLaserHatch extends MTEHatchEnergy {

    protected final AdaptiveHatchHelper helper = new AdaptiveHatchHelper();

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
        return V[helper.getCurrentVoltageTier()] * 16L;
    }

    @Override
    public long maxAmperesIn() {
        return 256;
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBase) {
        super.onFirstTick(aBase);
        if (aBase.isServerSide() && helper.isBound()) {
            AdaptiveNetworkManager.registerHatch(helper);
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
        ModularPanel panel = ModularPanel.defaultPanel("adaptive_laser_hatch", 280, 180);

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
            String tier = (v >= 0 && v < V.length) ? String.valueOf(V[v]) : "?";
            return EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.voltage")
                + " " + EnumChatFormatting.WHITE + tier + " EU/t";
        })).size(260, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            return EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.amperage")
                + " " + EnumChatFormatting.WHITE + maxAmperesIn() + " A";
        })).size(260, 14));

        panel.bindPlayerInventory();
        panel.child(column);
        return panel;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        return new AdaptiveNetLaserHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        helper.saveNBT(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        helper.loadNBT(aNBT);
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
            String tier = (v >= 0 && v < V.length) ? (V[v] + " EU/t") : "?";
            currenttip.add(EnumChatFormatting.AQUA + "V:" + tier + " | A:" + maxAmperesIn());
            if (helper.isBound()) {
                currenttip.add(EnumChatFormatting.GREEN
                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.bound"));
            }
        } catch (Exception ignored) {
        }
    }
}
