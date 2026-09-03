package com.wztwzt.ae2_qof.hatch.adaptive;

import static gregtech.api.enums.GTValues.V;

import java.util.UUID;

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
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.render.TextureFactory;

import gregtech.common.misc.WirelessNetworkManager;
import gregtech.api.util.GTUtility;

public class AdaptiveNetLaserTargetHatch extends MTEHatchDynamo {

    protected final AdaptiveHatchHelper helper = new AdaptiveHatchHelper();
    private long lastStoredEU = 0;
    private net.minecraft.world.World world;

    public AdaptiveNetLaserTargetHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
        helper.setHatchType(HatchType.LASER_TARGET);
    }

    public AdaptiveNetLaserTargetHatch(String aName, int aTier, String[] aDesc, ITexture[][][] aTextures) {
        super(aName, aTier, aDesc, aTextures);
        helper.setHatchType(HatchType.LASER_TARGET);
    }

    public AdaptiveHatchHelper getHelper() {
        return helper;
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_target_hatch.desc"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_target_hatch.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_target_hatch.desc.1"),
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
            TextureFactory.of(Textures.BlockIcons.OVERLAYS_ENERGY_OFF_WIRELESS[helper.getCurrentVoltageTier() + 1]) };
    }

    @Override
    public long maxEUOutput() {
        return Long.MAX_VALUE / 2;
    }

    @Override
    public long maxEUStore() {
        return Long.MAX_VALUE / 2;
    }

    @Override
    public long maxAmperesOut() {
        return helper.getCurrentAmps();
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBase) {
        super.onFirstTick(aBase);
        lastStoredEU = aBase.getUniversalEnergyStored();
        if (aBase.isServerSide()) {
            this.world = aBase.getWorld();
            helper.setPosition(aBase.getXCoord(), aBase.getYCoord(), aBase.getZCoord(), aBase.getWorld().provider.dimensionId);
            gregtech.api.metatileentity.MetaTileEntity machineMTE = AdaptiveHatchHelper.findAttachedMachine(aBase);
            if (machineMTE != null) {
                net.minecraft.item.ItemStack machineStack = machineMTE.getStackForm(1L);
                if (machineStack != null) {
                    helper.setMachineInfo((short) machineStack.getItemDamage(), machineStack.getDisplayName());
                } else {
                    helper.setMachineInfo((short) -1,
                        net.minecraft.util.StatCollector.translateToLocal(helper.getHatchType().getTranslationKey()));
                }
            } else {
                gregtech.api.metatileentity.MetaTileEntity mte = (gregtech.api.metatileentity.MetaTileEntity) aBase.getMetaTileEntity();
                if (mte != null) {
                    net.minecraft.item.ItemStack stack = mte.getStackForm(1L);
                    if (stack != null) {
                        helper.setMachineInfo((short) stack.getItemDamage(), stack.getDisplayName());
                    } else {
                        helper.setMachineInfo((short) -1,
                            net.minecraft.util.StatCollector.translateToLocal(helper.getHatchType().getTranslationKey()));
                    }
                }
            }
            if (helper.isBound()) {
                AdaptiveNetworkManager.registerHatch(helper, world);
            }
            transferEU(aBase);
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
    public void onPostTick(IGregTechTileEntity aBase, long aTick) {
        super.onPostTick(aBase, aTick);
        if (aBase.isServerSide() && aTick % 4 == 0) {
            transferEU(aBase);
            lastStoredEU = aBase.getUniversalEnergyStored();
        }
    }

    private void transferEU(IGregTechTileEntity aBase) {
        long stored = aBase.getUniversalEnergyStored();
        if (stored <= 0 || !helper.isBound()) {
            helper.setRealFlowEUt(0);
            return;
        }

        UUID owner = helper.getNetworkOwner();
        if (owner == null) {
            helper.setRealFlowEUt(0);
            return;
        }

        aBase.decreaseStoredEnergyUnits(stored, false);
        WirelessNetworkManager.addEUToGlobalEnergyMap(owner, stored);
        helper.setRealFlowEUt((int) stored);
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
        ModularPanel panel = ModularPanel.defaultPanel("adaptive_laser_target_hatch", 280, 180);

        Flow column = Flow.column().coverChildren().childPadding(6).top(10).left(10);

        column.child(
            new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_laser_target_hatch.title")).size(260, 16));

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
                + " " + EnumChatFormatting.WHITE + maxAmperesOut() + " A";
        })).size(260, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            long gridEU = helper.isBound() && helper.getNetworkOwner() != null
                ? AdaptiveHatchHelper.getGridEULong(helper.getNetworkOwner()) : 0L;
            return EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.grid_energy")
                + " " + EnumChatFormatting.WHITE + formatEU(gridEU);
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
        return new AdaptiveNetLaserTargetHatch(mName, mTier, mDescriptionArray, mTextures);
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
            String tierName = GTUtility.getColoredTierNameFromTier((byte) v);
            currenttip.add(EnumChatFormatting.AQUA + "V:" + tierName + " (" + V[v] + ") | A:" + helper.getCurrentAmps());
            long gridEU = helper.isBound() && helper.getNetworkOwner() != null
                ? AdaptiveHatchHelper.getGridEULong(helper.getNetworkOwner()) : 0L;
            currenttip.add(EnumChatFormatting.AQUA + "Grid: " + formatEU(gridEU));
            if (helper.isBound()) {
                currenttip.add(EnumChatFormatting.GREEN
                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.bound"));
            }
        } catch (Exception ignored) {
        }
    }
}
