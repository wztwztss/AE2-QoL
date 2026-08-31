package com.wztwzt.ae2_qof.hatch.wireless;

import static gregtech.api.enums.GTValues.V;

import java.math.BigInteger;
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
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.render.TextureFactory;
import gregtech.common.misc.WirelessNetworkManager;

import com.wztwzt.ae2_qof.item.ItemNetworkDataStick;

public class WirelessEnergyInputTerminal extends MTEHatchEnergy {

    private UUID ownerUuid;
    private long lastStoredEU = 0;
    // 电压档位: 0=ULV(8), 1=LV(32), 2=MV(128), 3=HV(512), 4=EV(2,048),
    //           5=IV(8,192), 6=LuV(32,768), 7=ZPM(131,072), 8=UV(524,288),
    //           9=UHV(2,097,152), 10=UEV(8,388,608), 11=UIV(33,554,432),
    //           12=UMV(134,217,728), 13=UXV(536,870,912), 14=MAX(2,147,483,647)
    private int voltageTier = 0;
    // 电流档位: 0=1A, 1=64A, 2=4,096A, 3=262,144A(262KA),
    //           4=16,777,216A(16MA), 5=1,073,741,824A(1GA)
    private int ampTier = 0;

    private static final long[] AMP_TIERS = { 1, 64, 4096, 262144, 16777216, 1073741824 };
    private static final String[] AMP_LABELS = { "1A", "64A", "4,096A", "262KA", "16MA", "1GA" };
    private static final int VOLTAGE_COUNT = 15;
    private static final int AMP_COUNT = AMP_TIERS.length;

    private static final String[] VOLTAGE_NAMES = {
        "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV", "MAX"
    };

    public WirelessEnergyInputTerminal(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public WirelessEnergyInputTerminal(String aName, int aTier, String[] aDesc, ITexture[][][] aTextures) {
        super(aName, aTier, aDesc, aTextures);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            StatCollector.translateToLocal("gt.blockmachines.wireless_energy_input_terminal.desc"),
            StatCollector.translateToLocal("gt.blockmachines.wireless_energy_input_terminal.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.wireless_energy_input_terminal.desc.1"),
            StatCollector.translateToLocal("gt.blockmachines.wireless_energy_input_terminal.desc.2"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof"
        };
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAYS_ENERGY_ON_WIRELESS[mTier + 1]) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAYS_ENERGY_ON_WIRELESS[mTier + 1]) };
    }

    private long getVoltage() {
        return (voltageTier >= 0 && voltageTier < V.length) ? V[voltageTier] : V[0];
    }

    private long getAmperage() {
        return (ampTier >= 0 && ampTier < AMP_TIERS.length) ? AMP_TIERS[ampTier] : 1;
    }

    @Override
    public long maxEUInput() {
        return getVoltage();
    }

    @Override
    public long maxEUStore() {
        return Long.MAX_VALUE / 2;
    }

    @Override
    public long maxAmperesIn() {
        return getAmperage();
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBase) {
        super.onFirstTick(aBase);
        if (aBase.isServerSide()) {
            ownerUuid = aBase.getOwnerUuid();
            if (ownerUuid != null) {
                WirelessNetworkManager.strongCheckOrAddUser(ownerUuid);
            }
        }
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBase, long aTick) {
        super.onPreTick(aBase, aTick);
        if (aBase.isServerSide() && ownerUuid != null && aTick % 4 == 0) {
            long currentStored = aBase.getStoredEU();
            long consumed = lastStoredEU - currentStored;
            if (consumed > 0) {
                WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUuid, -consumed);
            }
            BigInteger gridEU = WirelessNetworkManager.getUserEU(ownerUuid);
            long newStored = gridEU.min(BigInteger.valueOf(maxEUStore())).longValue();
            long diff = newStored - currentStored;
            if (diff > 0) {
                aBase.increaseStoredEnergyUnits(diff, false);
            } else if (diff < 0) {
                aBase.decreaseStoredEnergyUnits(-diff, false);
            }
            lastStoredEU = aBase.getStoredEU();
        }
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        ItemStack heldItem = aPlayer.getHeldItem();
        if (heldItem != null && ItemNetworkDataStick.hasData(heldItem)) {
            UUID owner = ItemNetworkDataStick.getOwner(heldItem);
            if (owner != null) {
                ownerUuid = owner;
                WirelessNetworkManager.strongCheckOrAddUser(ownerUuid);
                if (!aPlayer.worldObj.isRemote) {
                    aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("ae2_qof.wireless.bind.success")));
                }
                return true;
            }
        } else if (heldItem != null && heldItem.getItem() instanceof ItemNetworkDataStick) {
            ownerUuid = null;
            if (!aPlayer.worldObj.isRemote) {
                aPlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("ae2_qof.wireless.unbind.success")));
            }
            return true;
        }
        openGui(aPlayer);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    private String formatEU(long eu) {
        if (eu >= 1e15) return String.format("%.2fP", eu / 1e15);
        if (eu >= 1e12) return String.format("%.2fT", eu / 1e12);
        if (eu >= 1e9) return String.format("%.2fG", eu / 1e9);
        if (eu >= 1e6) return String.format("%.2fM", eu / 1e6);
        if (eu >= 1e3) return String.format("%.2fK", eu / 1e3);
        return String.valueOf(eu);
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        IntSyncValue voltageSync = new IntSyncValue(
            () -> voltageTier,
            v -> voltageTier = Math.max(0, Math.min(v, VOLTAGE_COUNT - 1))).allowC2S();

        IntSyncValue ampSync = new IntSyncValue(
            () -> ampTier,
            v -> ampTier = Math.max(0, Math.min(v, AMP_COUNT - 1))).allowC2S();

        LongSyncValue uuidHigh = new LongSyncValue(
            () -> ownerUuid != null ? ownerUuid.getMostSignificantBits() : 0L, v -> {});
        LongSyncValue uuidLow = new LongSyncValue(
            () -> ownerUuid != null ? ownerUuid.getLeastSignificantBits() : 0L, v -> {});
        LongSyncValue gridEUSync = new LongSyncValue(
            () -> {
                BigInteger eu = (ownerUuid != null) ? WirelessNetworkManager.getUserEU(ownerUuid) : BigInteger.ZERO;
                return eu.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            },
            v -> {});
        LongSyncValue storedEUSync = new LongSyncValue(
            () -> {
                IGregTechTileEntity te = getBaseMetaTileEntity();
                return te != null ? te.getStoredEU() : 0L;
            },
            v -> {});

        syncManager.syncValue("wVT", voltageSync);
        syncManager.syncValue("wAT", ampSync);
        syncManager.syncValue("wUH", uuidHigh);
        syncManager.syncValue("wUL", uuidLow);
        syncManager.syncValue("wEU", gridEUSync);
        syncManager.syncValue("wSE", storedEUSync);

        ModularPanel panel = ModularPanel.defaultPanel("wireless_input_terminal", 300, 340);

        Flow column = Flow.column().coverChildren().childPadding(4).top(8).left(8);

        // 标题
        column.child(new TextWidget<>(IKey.lang("ae2_qof.gui.wireless_input_terminal.title"))
            .size(284, 16));

        // 绑定状态
        column.child(new TextWidget<>(IKey.dynamic(() -> {
            long uuidH = uuidHigh.getLongValue();
            long uuidL = uuidLow.getLongValue();
            if (uuidH == 0 && uuidL == 0) {
                return EnumChatFormatting.RED + "● "
                    + StatCollector.translateToLocal("ae2_qof.gui.wireless_input_terminal.unbound");
            }
            UUID clientUuid = new UUID(uuidH, uuidL);
            return EnumChatFormatting.GREEN + "● "
                + StatCollector.translateToLocal("ae2_qof.gui.wireless_input_terminal.bound")
                + ": " + EnumChatFormatting.WHITE + clientUuid.toString().substring(0, 8);
        })).size(284, 14));

        // 本地缓存
        column.child(new TextWidget<>(IKey.dynamic(() -> {
            long stored = storedEUSync.getLongValue();
            return EnumChatFormatting.GREEN + "■ "
                + StatCollector.translateToLocal("ae2_qof.gui.wireless_input_terminal.stored")
                + ": " + EnumChatFormatting.RESET + formatEU(stored) + " EU";
        })).size(284, 14));

        // 电网余额
        column.child(new TextWidget<>(IKey.dynamic(() -> {
            long gridEU = gridEUSync.getLongValue();
            return EnumChatFormatting.GOLD + "■ "
                + StatCollector.translateToLocal("ae2_qof.gui.wireless_input_terminal.grid")
                + ": " + EnumChatFormatting.RESET + formatEU(gridEU) + " EU";
        })).size(284, 14));

        // 电压选择行
        column.child(Flow.row().coverChildren().childPadding(4)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.wireless_input_terminal.voltage_select"))
                .size(80, 16))
            .child(new TextFieldWidget().value(voltageSync).formatAsInteger(true)
                .numbersInt(() -> 0L, () -> (long) (VOLTAGE_COUNT - 1))
                .setMaxLength(2).size(32, 16))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                int v = voltageSync.getIntValue();
                String name = (v >= 0 && v < VOLTAGE_NAMES.length) ? VOLTAGE_NAMES[v] : "?";
                return EnumChatFormatting.RED + name
                    + EnumChatFormatting.WHITE + " (" + formatEU(V[v]) + " EU/t)";
            })).size(168, 16)));

        // 电流选择行
        column.child(Flow.row().coverChildren().childPadding(4)
            .child(new TextWidget<>(IKey.lang("ae2_qof.gui.wireless_input_terminal.current_select"))
                .size(80, 16))
            .child(new TextFieldWidget().value(ampSync).formatAsInteger(true)
                .numbersInt(() -> 0L, () -> (long) (AMP_COUNT - 1))
                .setMaxLength(2).size(32, 16))
            .child(new TextWidget<>(IKey.dynamic(() -> {
                int a = ampSync.getIntValue();
                return EnumChatFormatting.RED + AMP_LABELS[a]
                    + EnumChatFormatting.WHITE + " (" + formatEU(AMP_TIERS[a]) + ")";
            })).size(168, 16)));

        // 输出功率
        column.child(new TextWidget<>(IKey.dynamic(() -> {
            long voltage = getVoltage();
            long amps = getAmperage();
            long power = voltage * amps;
                return EnumChatFormatting.RED + "■ "
                    + StatCollector.translateToLocal("ae2_qof.gui.wireless_input_terminal.power")
                    + ": " + EnumChatFormatting.RESET + formatEU(power) + " EU/t"
                    + EnumChatFormatting.WHITE + " (" + formatEU(voltage) + "V x " + amps + "A)";
        })).size(284, 14));

        // 档位对照表
        column.child(new TextWidget<>(IKey.str(
            EnumChatFormatting.WHITE + "─── " + StatCollector.translateToLocal("ae2_qof.gui.wireless_input_terminal.voltage_ref") + " ───"
        )).size(284, 10));

        String[][] voltageRows = {
            { "0:ULV(8)", "1:LV(32)", "2:MV(128)", "3:HV(512)", "4:EV(2K)" },
            { "5:IV(8K)", "6:LuV(32K)", "7:ZPM(131K)", "8:UV(524K)", "9:UHV(2M)" },
            { "10:UEV(8M)", "11:UIV(33M)", "12:UMV(134M)", "13:UXV(536M)", "14:MAX(2G)" }
        };
        for (String[] row : voltageRows) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append("  ");
                sb.append(EnumChatFormatting.WHITE).append(row[i]);
            }
            column.child(new TextWidget<>(IKey.str(sb.toString())).size(284, 9));
        }

        column.child(new TextWidget<>(IKey.str(
            EnumChatFormatting.WHITE + "─── " + StatCollector.translateToLocal("ae2_qof.gui.wireless_input_terminal.current_ref") + " ───"
        )).size(284, 10));

        String[][] ampRows = {
            { "0:1A", "1:64A", "2:4,096A", "3:262KA", "4:16MA", "5:1GA" }
        };
        for (String[] row : ampRows) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append("  ");
                sb.append(EnumChatFormatting.WHITE).append(row[i]);
            }
            column.child(new TextWidget<>(IKey.str(sb.toString())).size(284, 9));
        }

        panel.bindPlayerInventory();
        panel.child(column);
        return panel;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        return new WirelessEnergyInputTerminal(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (ownerUuid != null) aNBT.setString("ae2qolWO", ownerUuid.toString());
        aNBT.setInteger("ae2qolVT", voltageTier);
        aNBT.setInteger("ae2qolAT", ampTier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        String s = aNBT.getString("ae2qolWO");
        if (s != null && !s.isEmpty()) {
            try {
                ownerUuid = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        voltageTier = aNBT.getInteger("ae2qolVT");
        ampTier = aNBT.getInteger("ae2qolAT");
        if (voltageTier < 0 || voltageTier >= VOLTAGE_COUNT) voltageTier = 0;
        if (ampTier < 0 || ampTier >= AMP_COUNT) ampTier = 0;
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
            long stored = 0;
            IGregTechTileEntity te = getBaseMetaTileEntity();
            if (te != null) stored = te.getStoredEU();
            currenttip.add(EnumChatFormatting.AQUA + "Stored: " + formatEU(stored) + " EU");
            if (ownerUuid != null) {
                BigInteger gridEU = WirelessNetworkManager.getUserEU(ownerUuid);
                currenttip.add(EnumChatFormatting.YELLOW + "Grid: " + formatEU(gridEU.longValue()) + " EU");
            }
            long voltage = getVoltage();
            long amps = getAmperage();
            currenttip.add(EnumChatFormatting.LIGHT_PURPLE + "Power: " + formatEU(voltage * amps) + " EU/t");
        } catch (Exception ignored) {
        }
    }
}
