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
import com.cleanroommc.modularui.value.sync.LongSyncValue;
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

import com.wztwzt.ae2_qof.item.ItemNetworkDataStick;

public class WirelessEnergyOutputTerminal extends MTEHatchDynamo {

    private UUID ownerUuid;

    public WirelessEnergyOutputTerminal(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public WirelessEnergyOutputTerminal(String aName, int aTier, String[] aDesc, ITexture[][][] aTextures) {
        super(aName, aTier, aDesc, aTextures);
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            StatCollector.translateToLocal("gt.blockmachines.wireless_energy_output_terminal.desc"),
            StatCollector.translateToLocal("gt.blockmachines.wireless_energy_output_terminal.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.wireless_energy_output_terminal.desc.1"),
            StatCollector.translateToLocal("gt.blockmachines.wireless_energy_output_terminal.desc.2"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof"
        };
    }
    @Override
    public ITexture[] getTexture(gregtech.api.interfaces.tileentity.IGregTechTileEntity aBaseMetaTileEntity,
                                 net.minecraftforge.common.util.ForgeDirection side,
                                 net.minecraftforge.common.util.ForgeDirection facing,
                                 int aColorIndex,
                                 boolean aActive,
                                 boolean aRedstone) {
        if (com.wztwzt.ae2_qof.util.ModTextures.isReady()) {
            if (side == facing) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.front("wireless_energy_output") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.UP) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.top("wireless_energy_output") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.DOWN) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.bottom() };
            }
            return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.side("wireless_energy_output") };
        }
        return super.getTexture(aBaseMetaTileEntity, side, facing, aColorIndex, aActive, aRedstone);
    }

    /**
     * 叠加层贴图索引（3.25.0-fix14）：等级提到 MAX 后 `mTier + 1` 可能越界，这里夹取一次。
     */
    private int overlayIndex() {
        int idx = mTier + 1;
        int len = Textures.BlockIcons.OVERLAYS_ENERGY_OFF_WIRELESS.length;
        return idx < len ? idx : len - 1;
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAYS_ENERGY_OFF_WIRELESS[overlayIndex()]) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture,
            TextureFactory.of(Textures.BlockIcons.OVERLAYS_ENERGY_OFF_WIRELESS[overlayIndex()]) };
    }

    @Override
    public boolean isEnetOutput() {
        return true;
    }

    @Override
    public boolean isEnetInput() {
        return false;
    }

    @Override
    public long maxEUInput() {
        return 0;
    }

    /**
     * 3.25.0-fix14：**对 GT 必须报"合法电压"**。
     * <p>GT 的 {@code MTEMultiBlockBase.addDynamoToMachineList} 只认
     * {@code instanceof MTEHatchDynamo} 且 {@code maxAmperesOut() <= 4} 的部件；
     * 原先这里返回 {@code Long.MAX_VALUE}（工具提示显示 `Invalid Voltage Tier`，结构判定直接失败）。
     * 现在按等级报 {@code V[mTier]} —— 与 GT 自己的动力仓 {@code MTEHatchDynamo.maxEUOutput()} 一致。
     * <p>⚠️ 我们的**无线推送不受此限**：{@code onPreTick} 只把本仓内部缓存推给全局电网，不读这个值。
     */
    @Override
    public long maxEUOutput() {
        return V[mTier];
    }

    @Override
    public long maxEUStore() {
        return Long.MAX_VALUE / 2;
    }

    /**
     * 3.25.0-fix14：GT 的结构判定**硬条件**是 {@code maxAmperesOut() <= 4}
     * （见 {@code MTEMultiBlockBase.addDynamoToMachineList}），所以这里必须回到 ≤4；
     * 原先返回 {@code Long.MAX_VALUE} 是"识别不了"的第二个原因（且 UI 里 cast 成 int 会变成 -1）。
     */
    @Override
    public long maxAmperesOut() {
        return 4;
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
            long stored = aBase.getStoredEU();
            if (stored > 0) {
                WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUuid, stored);
                aBase.decreaseStoredEnergyUnits(stored, false);
            }
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

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
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
        syncManager.syncValue("wUH", uuidHigh);
        syncManager.syncValue("wUL", uuidLow);
        syncManager.syncValue("wEU", gridEUSync);
        syncManager.syncValue("wSE", storedEUSync);

        ModularPanel panel = ModularPanel.defaultPanel("wireless_output_terminal", 300, 220);

        Flow column = Flow.column().coverChildren().childPadding(6).top(10).left(10);

        column.child(
            new TextWidget<>(IKey.lang("ae2_qof.gui.wireless_output_terminal.title")).size(280, 16));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            long uuidH = uuidHigh.getLongValue();
            long uuidL = uuidLow.getLongValue();
            if (uuidH == 0 && uuidL == 0) {
                return EnumChatFormatting.RED + "● "
                    + StatCollector.translateToLocal("ae2_qof.gui.wireless_output_terminal.unbound");
            }
            UUID clientUuid = new UUID(uuidH, uuidL);
            return EnumChatFormatting.GREEN + "● "
                + StatCollector.translateToLocal("ae2_qof.gui.wireless_output_terminal.bound")
                + ": " + EnumChatFormatting.WHITE + clientUuid.toString().substring(0, 8);
        })).size(280, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            long stored = storedEUSync.getLongValue();
            return EnumChatFormatting.GREEN
                + StatCollector.translateToLocal("ae2_qof.gui.wireless_output_terminal.stored")
                + ": " + EnumChatFormatting.RESET + formatEU(stored);
        })).size(280, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            long gridEU = gridEUSync.getLongValue();
            return EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("ae2_qof.gui.wireless_output_terminal.grid")
                + ": " + EnumChatFormatting.RESET + formatEU(gridEU);
        })).size(280, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            return EnumChatFormatting.RED
                + StatCollector.translateToLocal("ae2_qof.gui.wireless_output_terminal.unlimited");
        })).size(280, 14));

        panel.bindPlayerInventory();
        panel.child(column);
        return panel;
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
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        return new WirelessEnergyOutputTerminal(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (ownerUuid != null) aNBT.setString("ae2qolWO", ownerUuid.toString());
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
            BigInteger gridEU = BigInteger.ZERO;
            if (ownerUuid != null) gridEU = WirelessNetworkManager.getUserEU(ownerUuid);
            currenttip.add(EnumChatFormatting.YELLOW + "Grid: " + formatEU(gridEU.longValue()) + " EU");
        } catch (Exception ignored) {
        }
    }
}
