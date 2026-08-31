package com.wztwzt.ae2_qof.hatch.adaptive;

import static gregtech.api.enums.GTValues.V;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;

import com.wztwzt.ae2_qof.item.ItemNetworkDataStick;

public class AdaptiveNetTerminal extends MTEHatch {

    private static final int SLOT_DYNAMO = 0;
    private static final int SLOT_ENERGY = 1;
    private static final int SLOT_LASER_SOURCE = 2;
    private static final int SLOT_LASER_TARGET = 3;
    private static final int REQUIRED_STACK_SIZE = 64;

    private UUID networkOwner;
    private int networkFrequency = 0;
    private boolean autoReconnect = true;

    private int currentVoltageTier = 0;
    private int[] hatchTiers = new int[HatchType.COUNT];
    private int[] hatchAmps = new int[HatchType.COUNT];

    public AdaptiveNetTerminal(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 4, new String[] { "Adaptive Net Terminal" });
    }

    public AdaptiveNetTerminal(String aName, int aTier, int aInvSlotCount, String[] aDesc, ITexture[][][] aTextures) {
        super(aName, aTier, aInvSlotCount, aDesc, aTextures);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc.1"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_terminal.desc.2"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof"
        };
    }

    public UUID getNetworkOwner() {
        return networkOwner;
    }

    public int getNetworkFrequency() {
        return networkFrequency;
    }

    public int getTargetVoltageTier() {
        return currentVoltageTier;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    private void updateParamsFromSlots() {
        boolean anyValid = false;
        int bestTier = 0;

        for (int i = 0; i < 4; i++) {
            ItemStack stack = mInventory[i];
            if (stack != null && stack.stackSize >= REQUIRED_STACK_SIZE) {
                int damage = stack.getItemDamage();
                IMetaTileEntity imte = GregTechAPI.METATILEENTITIES[damage];
                if (imte instanceof MetaTileEntity) {
                    MetaTileEntity mte = (MetaTileEntity) imte;
                    int tier = (int) mte.getInputTier();
                    hatchTiers[i] = (tier >= 0) ? tier : HatchType.values()[i].defaultTier;
                    if (tier > bestTier) bestTier = (int) tier;
                    anyValid = true;
                    HatchType ht = HatchType.fromSlotIndex(i);
                    if (ht == HatchType.DYNAMO || ht == HatchType.LASER_SOURCE) {
                        hatchAmps[i] = (int) mte.maxAmperesOut();
                    } else {
                        hatchAmps[i] = (int) mte.maxAmperesIn();
                    }
                } else {
                    hatchTiers[i] = HatchType.values()[i].defaultTier;
                    hatchAmps[i] = HatchType.values()[i].defaultAmps;
                }
            } else {
                hatchTiers[i] = 0;
                hatchAmps[i] = 0;
            }
        }

        if (anyValid && bestTier != currentVoltageTier) {
            currentVoltageTier = bestTier;
            applySettings();
        }
    }

    public void applySettings() {
        if (networkOwner != null) {
            AdaptiveNetwork network = AdaptiveNetworkManager.getOrCreateNetwork(networkOwner, networkFrequency);
            network.setVoltageTier(currentVoltageTier);
            network.setAutoReconnect(autoReconnect);
            for (HatchType type : HatchType.values()) {
                network.setHatchTier(type, hatchTiers[type.slotIndex]);
                network.setHatchAmps(type, hatchAmps[type.slotIndex]);
            }
            if (autoReconnect) {
                network.updateAllHelpers();
            }
        }
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBase, long aTick) {
        super.onPreTick(aBase, aTick);
        if (aBase.isServerSide() && aTick % 20 == 0) {
            updateParamsFromSlots();
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBase) {
        super.onFirstTick(aBase);
        if (aBase.isServerSide()) {
            networkOwner = aBase.getOwnerUuid();
            if (networkOwner != null) {
                AdaptiveNetworkManager.registerTerminal(this);
            }
        }
    }

    @Override
    public void onRemoval() {
        if (networkOwner != null) {
            AdaptiveNetworkManager.unregisterTerminal(this);
        }
        super.onRemoval();
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        ItemStack heldItem = aPlayer.getHeldItem();

        if (heldItem != null && heldItem.getItem() instanceof ItemNetworkDataStick) {
            // Shift+right-click with data stick → Write config
            if (aPlayer.isSneaking()) {
                if (networkOwner == null) {
                    networkOwner = aPlayer.getUniqueID();
                    AdaptiveNetworkManager.registerTerminal(this);
                }
                if (networkOwner != null) {
                    ItemNetworkDataStick.writeData(heldItem, networkOwner, networkFrequency);
                    if (!aPlayer.worldObj.isRemote) {
                        aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                            EnumChatFormatting.GREEN
                                + StatCollector.translateToLocal("ae2_qof.data_stick.write.success")));
                    }
                }
                return true;
            }

            // Normal right-click with data stick → Read frequency from stick
            if (ItemNetworkDataStick.hasData(heldItem)) {
                UUID stickOwner = ItemNetworkDataStick.getOwner(heldItem);
                int stickFreq = ItemNetworkDataStick.getFrequency(heldItem);
                if (stickOwner != null) {
                    if (!aPlayer.worldObj.isRemote) {
                        int oldFreq = networkFrequency;
                        networkOwner = stickOwner;
                        networkFrequency = stickFreq;
                        AdaptiveNetworkManager.migrateHatches(stickOwner, oldFreq, stickOwner, stickFreq);
                        AdaptiveNetworkManager.unregisterTerminal(this);
                        AdaptiveNetworkManager.registerTerminal(this);
                        applySettings();
                        aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                            EnumChatFormatting.GREEN
                                + StatCollector.translateToLocal("ae2_qof.data_stick.read.success")
                                + " F:" + stickFreq));
                    }
                    return true;
                }
            }
            // Empty data stick or no data → open GUI
            openGui(aPlayer);
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
        IntSyncValue frequencySync = new IntSyncValue(
            () -> networkFrequency,
            v -> {
                if (v != networkFrequency) {
                    int oldFreq = networkFrequency;
                    networkFrequency = v;
                    if (networkOwner != null) {
                        AdaptiveNetworkManager.migrateHatches(networkOwner, oldFreq, networkOwner, v);
                        AdaptiveNetworkManager.unregisterTerminal(this);
                        AdaptiveNetworkManager.registerTerminal(this);
                        applySettings();
                    }
                }
            }
        ).allowC2S();

        IntSyncValue voltageTierSync = new IntSyncValue(
            () -> currentVoltageTier,
            v -> { currentVoltageTier = v; applySettings(); }
        ).allowC2S();

        IntSyncValue hatchTier0Sync = new IntSyncValue(() -> hatchTiers[0], v -> hatchTiers[0] = v);
        IntSyncValue hatchTier1Sync = new IntSyncValue(() -> hatchTiers[1], v -> hatchTiers[1] = v);
        IntSyncValue hatchTier2Sync = new IntSyncValue(() -> hatchTiers[2], v -> hatchTiers[2] = v);
        IntSyncValue hatchTier3Sync = new IntSyncValue(() -> hatchTiers[3], v -> hatchTiers[3] = v);
        IntSyncValue hatchAmp0Sync = new IntSyncValue(() -> hatchAmps[0], v -> hatchAmps[0] = v);
        IntSyncValue hatchAmp1Sync = new IntSyncValue(() -> hatchAmps[1], v -> hatchAmps[1] = v);
        IntSyncValue hatchAmp2Sync = new IntSyncValue(() -> hatchAmps[2], v -> hatchAmps[2] = v);
        IntSyncValue hatchAmp3Sync = new IntSyncValue(() -> hatchAmps[3], v -> hatchAmps[3] = v);

        IntSyncValue[] hatchTierSyncs = { hatchTier0Sync, hatchTier1Sync, hatchTier2Sync, hatchTier3Sync };
        IntSyncValue[] hatchAmpSyncs = { hatchAmp0Sync, hatchAmp1Sync, hatchAmp2Sync, hatchAmp3Sync };

        syncManager.syncValue("wFr", frequencySync);
        syncManager.syncValue("wVT", voltageTierSync);
        for (int i = 0; i < 4; i++) {
            syncManager.syncValue("wHT" + i, hatchTierSyncs[i]);
            syncManager.syncValue("wHA" + i, hatchAmpSyncs[i]);
        }

        PagedWidget.Controller tabController = new PagedWidget.Controller();

        ModularPanel panel = ModularPanel.defaultPanel("adaptive_net_terminal", 350, 240);

        int TAB_W = 32;
        int TAB_H = 28;
        int CONTENT_X = TAB_W;
        int CONTENT_W = 350 - CONTENT_X - 4;

        Flow tabStrip = Flow.column().pos(0, 4).width(TAB_W).childPadding(0);
        tabStrip.child(new PageButton(0, tabController).tab(GuiTextures.TAB_LEFT, -1)
            .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.status"))).size(TAB_W, TAB_H));
        tabStrip.child(new PageButton(1, tabController).tab(GuiTextures.TAB_LEFT, 0)
            .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.settings"))).size(TAB_W, TAB_H));
        tabStrip.child(new PageButton(2, tabController).tab(GuiTextures.TAB_LEFT, 1)
            .tooltip(t -> t.addLine(IKey.lang("ae2_qof.gui.adaptive_terminal.tab.frequency"))).size(TAB_W, TAB_H));
        panel.child(tabStrip);

        PagedWidget<?> pages = new PagedWidget<>()
            .controller(tabController)
            .pos(CONTENT_X, 4)
            .size(CONTENT_W, 230)
            .addPage(buildStatusTab(frequencySync, voltageTierSync, hatchTierSyncs, hatchAmpSyncs))
            .addPage(buildSettingsTab(hatchTierSyncs, hatchAmpSyncs))
            .addPage(buildFrequencyTab(frequencySync));
        panel.child(pages);

        panel.bindPlayerInventory();
        return panel;
    }

    private Flow buildStatusTab(IntSyncValue frequencySync, IntSyncValue voltageTierSync,
                                IntSyncValue[] hatchTierSyncs, IntSyncValue[] hatchAmpSyncs) {
        Flow tab = Flow.column().coverChildren().childPadding(4);

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.title")).size(300, 16));

        tab.child(new TextWidget<>(IKey.dynamic(() -> {
            return EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.frequency")
                + ": " + EnumChatFormatting.WHITE + networkFrequency;
        })).size(300, 14));

        tab.child(new TextWidget<>(IKey.dynamic(() -> {
            if (networkOwner == null) {
                return EnumChatFormatting.RED + "● "
                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.no_owner");
            }
            return EnumChatFormatting.GREEN + "● "
                + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.owner")
                + ": " + EnumChatFormatting.WHITE + networkOwner.toString().substring(0, 8);
        })).size(300, 14));

        tab.child(new TextWidget<>(IKey.str("")).size(300, 4));

        String[] labels = {
            "ae2_qof.gui.adaptive_terminal.dynamo_hatch",
            "ae2_qof.gui.adaptive_terminal.energy_hatch",
            "ae2_qof.gui.adaptive_terminal.laser_source",
            "ae2_qof.gui.adaptive_terminal.laser_target"
        };

        AdaptiveNetwork network = (networkOwner != null)
            ? AdaptiveNetworkManager.getNetwork(networkOwner, networkFrequency) : null;

        for (HatchType type : HatchType.values()) {
            final HatchType ft = type;
            final int idx = ft.slotIndex;
            tab.child(new TextWidget<>(IKey.dynamic(() -> {
                int loaded = (network != null) ? network.getHatchCount(ft) : 0;
                int tier = hatchTierSyncs[idx].getIntValue();
                int amps = hatchAmpSyncs[idx].getIntValue();
                String loadedStr = (loaded > 0) ? EnumChatFormatting.GREEN + String.valueOf(loaded)
                    : EnumChatFormatting.YELLOW + "0";
                if (amps > 0 && tier >= 0 && tier < V.length) {
                    return EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal(labels[idx])
                        + ": " + EnumChatFormatting.GREEN + amps + "A " + V[tier] + "V"
                        + " (" + EnumChatFormatting.GOLD + V[tier] + EnumChatFormatting.WHITE + ")"
                        + " -" + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.loaded") + ": "
                        + loadedStr;
                }
                return EnumChatFormatting.WHITE
                    + StatCollector.translateToLocal(labels[idx])
                    + ": " + EnumChatFormatting.YELLOW + "ULV 0V 0A"
                    + " -" + StatCollector.translateToLocal("ae2_qof.gui.adaptive_terminal.loaded") + ": "
                    + loadedStr;
            })).size(310, 14));
        }

        return tab;
    }

    private Flow buildSettingsTab(IntSyncValue[] hatchTierSyncs, IntSyncValue[] hatchAmpSyncs) {
        Flow tab = Flow.column().coverChildren().childPadding(4);

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.title")).size(300, 16));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.slots")).size(300, 14));

        String[] slotLabels = {
            "ae2_qof.gui.adaptive_terminal.dynamo_slot",
            "ae2_qof.gui.adaptive_terminal.energy_slot",
            "ae2_qof.gui.adaptive_terminal.laser_source_slot",
            "ae2_qof.gui.adaptive_terminal.laser_target_slot"
        };

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            tab.child(Flow.row()
                .coverChildren()
                .childPadding(6)
                .child(new TextWidget<>(IKey.lang(slotLabels[i])).size(100, 14))
                .child(new ItemSlot().slot(new ModularSlot(inventoryHandler, idx)).size(18))
                .child(new TextWidget<>(IKey.dynamic(() -> {
                    int tier = hatchTierSyncs[idx].getIntValue();
                    int amps = hatchAmpSyncs[idx].getIntValue();
                    if (tier >= 0 && tier < V.length && amps > 0) {
                        return EnumChatFormatting.GREEN + "" + V[tier] + "V A:" + amps;
                    }
                    return EnumChatFormatting.YELLOW + "ULV 0V 0A";
                })).size(120, 14)));
        }

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.hint")).size(300, 14));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.settings.all_slots_required"))
            .size(300, 14));

        return tab;
    }

    private Flow buildFrequencyTab(IntSyncValue frequencySync) {
        Flow tab = Flow.column().coverChildren().childPadding(4);

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.title")).size(300, 16));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.input")).size(300, 14));

        tab.child(new TextFieldWidget().value(frequencySync).formatAsInteger(true)
            .numbersInt(() -> (long) Integer.MIN_VALUE, () -> (long) Integer.MAX_VALUE)
            .setMaxLength(12).size(200, 16));

        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.hint")).size(300, 14));
        tab.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_terminal.frequency.hint2")).size(300, 14));

        return tab;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        return new AdaptiveNetTerminal(mName, mTier, 4, mDescriptionArray, mTextures);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (networkOwner != null) {
            aNBT.setString("ae2qolNO", networkOwner.toString());
        }
        aNBT.setInteger("ae2qolNF", networkFrequency);
        aNBT.setInteger("ae2qolVT", currentVoltageTier);
        aNBT.setBoolean("ae2qolAR", autoReconnect);
        for (int i = 0; i < HatchType.COUNT; i++) {
            aNBT.setInteger("ae2qolHT" + i, hatchTiers[i]);
            aNBT.setInteger("ae2qolHA" + i, hatchAmps[i]);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        String s = aNBT.getString("ae2qolNO");
        if (s != null && !s.isEmpty()) {
            try {
                networkOwner = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        networkFrequency = aNBT.getInteger("ae2qolNF");
        currentVoltageTier = aNBT.getInteger("ae2qolVT");
        autoReconnect = aNBT.getBoolean("ae2qolAR");
        for (int i = 0; i < HatchType.COUNT; i++) {
            hatchTiers[i] = aNBT.getInteger("ae2qolHT" + i);
            hatchAmps[i] = aNBT.getInteger("ae2qolHA" + i);
            if (hatchAmps[i] <= 0) hatchAmps[i] = HatchType.values()[i].defaultAmps;
        }
    }

    @Override
    public boolean canExtractItem(int aIndex, ItemStack aStack, int aSide) {
        return false;
    }

    @Override
    public boolean canInsertItem(int aIndex, ItemStack aStack, int aSide) {
        return true;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int aSide) {
        return new int[] { SLOT_DYNAMO, SLOT_ENERGY, SLOT_LASER_SOURCE, SLOT_LASER_TARGET };
    }

    @Override
    public boolean isItemValidForSlot(int aIndex, ItemStack aStack) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public int getSizeInventory() {
        return 4;
    }

    @Override
    public ItemStack getStackInSlot(int aIndex) {
        if (aIndex >= 0 && aIndex < mInventory.length) return mInventory[aIndex];
        return null;
    }

    @Override
    public ItemStack decrStackSize(int aIndex, int aAmount) {
        if (aIndex >= 0 && aIndex < mInventory.length && mInventory[aIndex] != null) {
            if (mInventory[aIndex].stackSize <= aAmount) {
                ItemStack stack = mInventory[aIndex];
                mInventory[aIndex] = null;
                return stack;
            }
            ItemStack split = mInventory[aIndex].splitStack(aAmount);
            if (mInventory[aIndex].stackSize == 0) mInventory[aIndex] = null;
            return split;
        }
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int aIndex) {
        if (aIndex >= 0 && aIndex < mInventory.length && mInventory[aIndex] != null) {
            ItemStack stack = mInventory[aIndex];
            mInventory[aIndex] = null;
            return stack;
        }
        return null;
    }

    @Override
    public void setInventorySlotContents(int aIndex, ItemStack aStack) {
        if (aIndex >= 0 && aIndex < mInventory.length) {
            mInventory[aIndex] = aStack;
        }
    }

    @Override
    public String getInventoryName() {
        return mName;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public void markDirty() {}

    @Override
    public ITexture[][][] getTextureSet(ITexture[] aTextures) {
        return new ITexture[0][][];
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_SCREEN) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_SCREEN) };
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
}
