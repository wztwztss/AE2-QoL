package com.wztwzt.ae2_qof.hatch;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import cpw.mods.fml.common.registry.GameRegistry;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.render.TextureFactory;

public class AE2MaintenanceHatchUniversal extends MTEHatchMaintenance {

    private static final int CIRCUIT_SLOT = 0;

    // 速度上限: MAX(14)=100, 其他按比例递减
    private static final int[] SPEED_MAX = { 0, 0, 2, 5, 8, 13, 18, 25, 33, 41, 51, 62, 73, 86, 100, 100 };
    private static final int[] PARALLEL_MAX = { 1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576,
        4194304, 16777216, 67108864, 268435456, 1073741824 };

    private static final String[] CIRCUIT_KEYS = { "CircuitULV", "CircuitLV", "CircuitMV", "CircuitHV", "CircuitEV",
        "CircuitIV", "CircuitLuV", "CircuitZPM", "CircuitUV", "CircuitUHV", "CircuitUEV", "CircuitUIV",
        "CircuitUMV", "CircuitUXV", "CircuitMAX" };
    private static Item[] CIRCUIT_ITEMS;

    private int userParallel;
    private int userSpeed;

    public AE2MaintenanceHatchUniversal(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public AE2MaintenanceHatchUniversal(String aName, int aTier, String[] aDesc, ITexture[][][] aTextures) {
        super(aName, aTier, aDesc, aTextures, false);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            StatCollector.translateToLocal("gt.blockmachines.hatch.maintenance.universal.desc"),
            StatCollector.translateToLocal("gt.blockmachines.hatch.maintenance.universal.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.hatch.maintenance.universal.desc.1"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof"
        };
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_AUTOMAINTENANCE) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_AUTOMAINTENANCE) };
    }

    private static Item[] getCircuitItems() {
        if (CIRCUIT_ITEMS == null) {
            CIRCUIT_ITEMS = new Item[CIRCUIT_KEYS.length];
            for (int i = 0; i < CIRCUIT_KEYS.length; i++) {
                CIRCUIT_ITEMS[i] = GameRegistry.findItem("dreamcraft", CIRCUIT_KEYS[i]);
            }
        }
        return CIRCUIT_ITEMS;
    }

    private int getCircuitLevel() {
        ItemStack stack = mInventory[CIRCUIT_SLOT];
        if (stack == null) return -1;
        Item item = stack.getItem();
        Item[] items = getCircuitItems();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && item == items[i]) return i;
        }
        return -1;
    }

    public int getCircuitLevelPublic() { return getCircuitLevel(); }
    public int getUserParallel() { return userParallel; }
    public int getUserSpeed() { return userSpeed; }

    public int getMaxParallelForLevel() {
        int lvl = getCircuitLevel();
        return (lvl >= 0 && lvl < PARALLEL_MAX.length) ? PARALLEL_MAX[lvl] : 1;
    }

    public int getMaxSpeedForLevel() {
        int lvl = getCircuitLevel();
        return (lvl >= 0 && lvl < SPEED_MAX.length) ? SPEED_MAX[lvl] : 0;
    }

    public int getEffectiveParallel() {
        return Math.max(1, Math.min(userParallel, getMaxParallelForLevel()));
    }

    public double getEffectiveSpeedBoost() {
        int max = getMaxSpeedForLevel();
        int clamped = Math.max(-max, Math.min(userSpeed, max));
        return 1.0 - clamped / 100.0;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        return new AE2MaintenanceHatchUniversal(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override protected boolean useMui2() { return true; }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        IntSyncValue parallelSync = new IntSyncValue(
            this::getUserParallel,
            v -> this.userParallel = Math.max(1, Math.min(v, getMaxParallelForLevel()))
        ).allowC2S();
        IntSyncValue speedSync = new IntSyncValue(
            this::getUserSpeed,
            v -> { int m = getMaxSpeedForLevel(); this.userSpeed = Math.max(-m, Math.min(v, m)); }
        ).allowC2S();

        ModularPanel panel = ModularPanel.defaultPanel("universal_maintenance_hatch", 260, 180);

        Flow column = Flow.column().coverChildren().childPadding(3).top(7).left(7);

        column.child(new TextWidget<>(IKey.lang("ae2_qof.gui.hatch.title"))
            .size(246, 12));

        column.child(new TextWidget<>(IKey.lang("ae2_qof.gui.hatch.circuit_slot"))
            .size(246, 12));
        column.child(new ItemSlot()
            .slot(new ModularSlot(inventoryHandler, CIRCUIT_SLOT))
            .size(18));

        column.child(paramRow("ae2_qof.gui.hatch.parallel",
            new TextFieldWidget().value(parallelSync).formatAsInteger(true)
                .numbersInt(() -> 1L, () -> (long) getMaxParallelForLevel())
                .setMaxLength(10).size(80, 14),
            IKey.dynamic(() -> "max " + getMaxParallelForLevel())));

        column.child(paramRow("ae2_qof.gui.hatch.speed",
            new TextFieldWidget().value(speedSync).formatAsInteger(true)
                .numbersInt(() -> (long) -getMaxSpeedForLevel(), () -> (long) getMaxSpeedForLevel())
                .setMaxLength(5).size(80, 14),
            IKey.dynamic(() -> "max " + getMaxSpeedForLevel() + "%")));

        panel.bindPlayerInventory();
        panel.child(column);
        return panel;
    }

    private Flow paramRow(String labelKey, TextFieldWidget field, IKey suffix) {
        return Flow.row().coverChildren().childPadding(3)
            .child(new TextWidget<>(IKey.lang(labelKey)).size(55, 14))
            .child(field)
            .child(new TextWidget<>(suffix).size(120, 14));
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("ae2qolPar", userParallel);
        aNBT.setInteger("ae2qolSpd", userSpeed);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        userParallel = aNBT.getInteger("ae2qolPar");
        if (userParallel <= 0) userParallel = 1;
        userSpeed = aNBT.getInteger("ae2qolSpd");
    }

    @Override public boolean allowPullStack(IGregTechTileEntity a, int i, ForgeDirection s, ItemStack stack) { return false; }
    @Override public boolean allowPutStack(IGregTechTileEntity a, int i, ForgeDirection s, ItemStack stack) { return false; }

    @Override
    public void getWailaBody(ItemStack itemStack, java.util.List<String> currenttip,
        mcp.mobius.waila.api.IWailaDataAccessor accessor, mcp.mobius.waila.api.IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currenttip, accessor, config);
        try {
            currenttip.add(EnumChatFormatting.AQUA + "P:" + getEffectiveParallel()
                + " | S:" + (getUserSpeed() >= 0 ? "+" : "") + getUserSpeed() + "%");
        } catch (Exception ignored) {}
    }
}
