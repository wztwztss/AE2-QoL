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
import com.cleanroommc.modularui.value.sync.IntSyncValue;
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
import gregtech.api.util.GTUtility;
import gregtech.common.misc.WirelessNetworkManager;

public class AdaptiveNetDynamoHatch extends MTEHatchDynamo {

    protected final AdaptiveHatchHelper helper = new AdaptiveHatchHelper();
    private long lastStoredEU = 0;
    private net.minecraft.world.World world;

    public AdaptiveNetDynamoHatch(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
        helper.setHatchType(HatchType.DYNAMO);
    }

    public AdaptiveNetDynamoHatch(String aName, int aTier, String[] aDesc, ITexture[][][] aTextures) {
        super(aName, aTier, aDesc, aTextures);
        helper.setHatchType(HatchType.DYNAMO);
    }

    public AdaptiveHatchHelper getHelper() {
        return helper;
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gt.blockmachines.adaptive_net_dynamo_hatch.desc"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_dynamo_hatch.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_dynamo_hatch.desc.1"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof" };
    }

    @Override
    public ITexture[] getTexture(gregtech.api.interfaces.tileentity.IGregTechTileEntity aBaseMetaTileEntity,
        net.minecraftforge.common.util.ForgeDirection side, net.minecraftforge.common.util.ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (com.wztwzt.ae2_qof.util.ModTextures.isReady()) {
            if (side == facing) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.front("adaptive_net_dynamo_hatch") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.UP) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.top("adaptive_net_dynamo_hatch") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.DOWN) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.bottom() };
            }
            return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.side("adaptive_net_dynamo_hatch") };
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
            TextureFactory.of(Textures.BlockIcons.OVERLAYS_ENERGY_OFF_WIRELESS[helper.getCurrentVoltageTier() + 1]) };
    }

    /**
     * 3.33.0-fix2：改回真实电压档 {@code V[tier]} —— 与同族 {@code WirelessEnergyOutputTerminal.maxEUOutput()}
     * （3.25.0-fix16 已改为 {@code V[mTier]}，注释原话"与 GT 自己的动力仓一致"）及 GT 原生
     * {@code MTEHatchDynamo.maxEUOutput()} 同口径。
     * <p>为什么必须改：GT 在 {@code MTEMultiBlockBase.addEnergyOutputMultipleDynamos} 里按
     * 「Σ(电压 × 安培)」累加所有动力仓的输出预算，不足即 {@code explodeMultiblock()}（字节码 offset 190-211）。
     * 旧值 {@code Long.MAX_VALUE / 2} 下，安培 ≥2 时乘法即环绕为负（2 → -9.22e18，4 → 0），
     * 预算判负 ⇒ 发电机多方块每 tick 被真引爆；TecTech 机则是负注入 → 白得 EU。
     * <p>用户拍板（2026-10-14）：**安培不设上限**——算术安全由电压口径独立承担：
     * V[tier] ≤ 8.59e9（tier 15），即使安培到 2^31，乘积 ≤ 1.8e19 仍可被 long 承载时的
     * 常规场景（多仓累加才可能逼近上限），且 getCurrentVoltageTier() 有 0..15 钳制，
     * 乘法两端都受控。
     */
    @Override
    public long maxEUOutput() {
        return V[helper.getCurrentVoltageTier()];
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
            if (!helper.isBound()) {
                helper.tryAutoBindFromPlacer(aBase);
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
        // 3.32.0-fix4：本次推送的是"这 4 tick 窗口"累计的缓存量，换算成 EU/t 才是速率
        helper.updateRealFlowEUt(stored);
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
        ModularPanel panel = ModularPanel.defaultPanel("adaptive_dynamo_hatch", 280, 180);

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

        column.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_dynamo_hatch.title")).size(260, 16));

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

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        return new AdaptiveNetDynamoHatch(mName, mTier, mDescriptionArray, mTextures);
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
            int v = helper.getLastVoltageTier();
            int a = helper.getLastAmps();
            String tierName = GTUtility.getColoredTierNameFromTier((byte) v);
            currenttip.add(EnumChatFormatting.AQUA + "V:" + tierName + " (" + V[v] + ") | A:" + a);
            long gridEU = helper.getLastGridEU();
            currenttip.add(EnumChatFormatting.AQUA + "Grid: " + formatEU(gridEU));
            if (helper.isBound()) {
                currenttip
                    .add(EnumChatFormatting.GREEN + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.bound"));
            }
        } catch (Exception ignored) {}
    }
}
