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
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
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

public class AdaptiveNetHatch extends MTEHatchEnergy {

    private static final Logger LOG = LogManager.getLogger("AE2QoL");
    protected final AdaptiveHatchHelper helper = new AdaptiveHatchHelper();
    private long lastStoredEU = 0;
    private net.minecraft.world.World world;

    public AdaptiveNetHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
        helper.setHatchType(HatchType.ENERGY);
    }

    public AdaptiveNetHatch(String aName, int aTier, String[] aDesc, ITexture[][][] aTextures) {
        super(aName, aTier, aDesc, aTextures);
        helper.setHatchType(HatchType.ENERGY);
    }

    public AdaptiveHatchHelper getHelper() {
        return helper;
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gt.blockmachines.adaptive_net_hatch.desc"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_hatch.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_hatch.desc.1"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof" };
    }

    @Override
    public ITexture[] getTexture(gregtech.api.interfaces.tileentity.IGregTechTileEntity aBaseMetaTileEntity,
        net.minecraftforge.common.util.ForgeDirection side, net.minecraftforge.common.util.ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (com.wztwzt.ae2_qof.util.ModTextures.isReady()) {
            if (side == facing) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.front("adaptive_net_hatch") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.UP) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.top("adaptive_net_hatch") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.DOWN) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.bottom() };
            }
            return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.side("adaptive_net_hatch") };
        }
        return super.getTexture(aBaseMetaTileEntity, side, facing, aColorIndex, aActive, aRedstone);
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
        return V[helper.getCurrentVoltageTier()];
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
                // 3.35.0-fix4（审查 P1-13）：负数记账（扣消耗）被 GT 拒绝时电留在电网（偏高而非蒸发），
                // 失败本身无害——但必须留痕：连续拒收说明"消耗上报"失效，账目会持续偏高。
                if (!WirelessNetworkManager.addEUToGlobalEnergyMap(owner, -consumed)) {
                    cn.dancingsnow.aeinfinitycell.AEInfinityCell.LOG
                        .warn("[AE2QoL] 自适应仓消耗上报被拒（电网余额不足以扣负数），{} EU 未扣：owner={}", consumed, owner);
                }
            }
            // 3.32.0-fix4：consumed 是"这 4 tick 窗口"的增量，必须换算成 EU/t（否则列表实测放大 4 倍）
            helper.updateRealFlowEUt(consumed > 0 ? consumed : 0L);
            long gridEU = AdaptiveHatchHelper.getGridEULong(owner);
            helper.setLastGridEU(gridEU);
            long maxStore = maxEUStore();
            long halfStore = maxStore / 2;
            if (currentStored < halfStore) {
                // 3.33.0-fix3：补电上限改用「halfStore - currentStored」（剩余空间）而不是
                // 「currentStored + gridEU」（目标值）。两者数学等价（min(halfStore, c+g) - c
                // == min(halfStore-c, g)），但旧式在 gridEU 逼近 Long.MAX_VALUE（9.22e18，用户实测
                // 已到 9.2E）时加法环绕成负数 ⇒ target 为负 ⇒ diff<0 ⇒ 补电被静默跳过，
                // 只剩"恰好抽干到 0"才补一次 —— 表现为机器疯狂开关机。
                // 新式全部是"小数减小数"，永不溢出；gridEU 已被 getGridEULong 钳到 ≤ Long.MAX_VALUE。
                long headroom = halfStore - currentStored; // currentStored < halfStore ⇒ 恒为正
                long diff = Math.min(headroom, gridEU);
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
            helper.setPosition(
                aBase.getXCoord(),
                aBase.getYCoord(),
                aBase.getZCoord(),
                aBase.getWorld().provider.dimensionId);
            gregtech.api.metatileentity.MetaTileEntity machineMTE = AdaptiveHatchHelper.findAttachedMachine(aBase);
            if (machineMTE != null) {
                net.minecraft.item.ItemStack machineStack = machineMTE.getStackForm(1L);
                if (machineStack != null) {
                    helper.setMachineInfo(machineStack.getItemDamage(), machineStack.getDisplayName());
                } else {
                    helper.setMachineInfo(
                        -1,
                        net.minecraft.util.StatCollector.translateToLocal(
                            helper.getHatchType()
                                .getTranslationKey()));
                }
            } else {
                gregtech.api.metatileentity.MetaTileEntity mte = (gregtech.api.metatileentity.MetaTileEntity) aBase
                    .getMetaTileEntity();
                if (mte != null) {
                    net.minecraft.item.ItemStack stack = mte.getStackForm(1L);
                    if (stack != null) {
                        helper.setMachineInfo(stack.getItemDamage(), stack.getDisplayName());
                    } else {
                        helper.setMachineInfo(
                            -1,
                            net.minecraft.util.StatCollector.translateToLocal(
                                helper.getHatchType()
                                    .getTranslationKey()));
                    }
                }
            }
            lastStoredEU = aBase.getStoredEU();
            if (!helper.isBound()) {
                helper.tryAutoBindFromPlacer(aBase);
            }
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
        ModularPanel panel = ModularPanel.defaultPanel("adaptive_net_hatch", 280, 200);

        IntSyncValue boundSync = new IntSyncValue(() -> helper.isBound() ? 1 : 0, v -> {});
        IntSyncValue voltageSync = new IntSyncValue(() -> helper.getCurrentVoltageTier(), v -> {});
        IntSyncValue ampSync = new IntSyncValue(() -> helper.getCurrentAmps(), v -> {});
        LongSyncValue gridEUSync = new LongSyncValue(
            () -> helper.isBound() && helper.getNetworkOwner() != null
                ? AdaptiveHatchHelper.getGridEULong(helper.getNetworkOwner())
                : 0L,
            v -> {});
        syncManager.syncValue("aBD", boundSync);
        syncManager.syncValue("aVT", voltageSync);
        syncManager.syncValue("aAM", ampSync);
        syncManager.syncValue("aGE", gridEUSync);

        Flow column = Flow.column()
            .coverChildren()
            .childPadding(2)
            .top(6)
            .left(10);

        column.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_hatch.title")).size(260, 16));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            if (boundSync.getIntValue() == 0) {
                return EnumChatFormatting.RED + "● "
                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.unbound");
            }
            return EnumChatFormatting.GREEN + "● " + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.bound");
        })).size(260, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            int v = voltageSync.getIntValue();
            String tierName = GTUtility.getColoredTierNameFromTier((byte) v);
            return EnumChatFormatting.AQUA + StatCollector
                .translateToLocal("ae2_qof.gui.adaptive_hatch.voltage") + " " + tierName + " (" + V[v] + " EU/t)";
        })).size(260, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                "ae2_qof.gui.adaptive_hatch.amperage") + " " + EnumChatFormatting.WHITE + ampSync.getIntValue() + " A";
        })).size(260, 14));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            long gridEU = gridEUSync.getLongValue();
            return EnumChatFormatting.AQUA + StatCollector.translateToLocal(
                "ae2_qof.gui.adaptive_hatch.grid_energy") + " " + EnumChatFormatting.WHITE + formatEU(gridEU) + " EU";
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

    /**
     * 3.33.0-fix3：电网余额的 BigInteger 精确版（>9.22E 时显示真实值，如 15.80E）。
     * WAILA/Grid 行在 helper.lastGridEUBigInteger 超过 long 上限时走本方法，否则走旧路径。
     */
    private static String formatEUBigInteger(BigInteger eu) {
        BigInteger E = BigInteger.valueOf(1_000_000_000_000_000_000L);
        BigInteger P = BigInteger.valueOf(1_000_000_000_000_000L);
        BigInteger T = BigInteger.valueOf(1_000_000_000_000L);
        BigInteger G = BigInteger.valueOf(1_000_000_000L);
        BigInteger M = BigInteger.valueOf(1_000_000L);
        BigInteger K = BigInteger.valueOf(1_000L);
        BigInteger abs = eu.abs();
        BigInteger whole;
        String suffix;
        double scale;
        if (abs.compareTo(E) >= 0) {
            whole = abs.divide(E);
            suffix = "E";
            scale = 1e18;
        } else if (abs.compareTo(P) >= 0) {
            whole = abs.divide(P);
            suffix = "P";
            scale = 1e15;
        } else if (abs.compareTo(T) >= 0) {
            whole = abs.divide(T);
            suffix = "T";
            scale = 1e12;
        } else if (abs.compareTo(G) >= 0) {
            whole = abs.divide(G);
            suffix = "G";
            scale = 1e9;
        } else if (abs.compareTo(M) >= 0) {
            whole = abs.divide(M);
            suffix = "M";
            scale = 1e6;
        } else if (abs.compareTo(K) >= 0) {
            whole = abs.divide(K);
            suffix = "K";
            scale = 1e3;
        } else {
            return (eu.signum() < 0 ? "-" : "") + abs;
        }
        double frac = abs.mod(BigInteger.valueOf((long) (scale / 100)))
            .doubleValue() / (scale / 100);
        return String.format("%s%.2f%s", eu.signum() < 0 ? "-" : "", whole.doubleValue() + frac / 100.0, suffix);
    }

    /**
     * 3.33.0-fix3：WAILA"Grid"行的取数逻辑 —— helper 的 BigInteger 快照超过 long 上限
     * 就走精确显示（如 15.80E），否则维持旧的 long 路径（含负数/0 的原样格式）。
     */
    private String formatGridEUForWaila() {
        BigInteger exact = helper.getLastGridEUBigInteger();
        if (exact.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0) {
            return formatEUBigInteger(exact);
        }
        return formatEU(helper.getLastGridEU());
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        return new AdaptiveNetHatch(mName, mTier, mDescriptionArray, mTextures);
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
            int v = helper.getLastVoltageTier();
            int a = helper.getLastAmps();
            String tierName = GTUtility.getColoredTierNameFromTier((byte) v);
            currenttip.add(EnumChatFormatting.AQUA + "V:" + tierName + " (" + V[v] + ") | A:" + a);
            currenttip.add(EnumChatFormatting.AQUA + "Grid: " + formatGridEUForWaila());
            if (helper.isBound()) {
                currenttip
                    .add(EnumChatFormatting.GREEN + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.bound"));
            }
        } catch (Exception ignored) {}
    }
}
