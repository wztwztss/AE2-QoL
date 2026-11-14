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
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
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
        return new String[] { StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_hatch.desc"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_hatch.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_hatch.desc.1"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof" };
    }

    @Override
    public ITexture[] getTexture(gregtech.api.interfaces.tileentity.IGregTechTileEntity aBaseMetaTileEntity,
        net.minecraftforge.common.util.ForgeDirection side, net.minecraftforge.common.util.ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (com.wztwzt.ae2_qof.util.ModTextures.isReady()) {
            if (side == facing) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.front("adaptive_net_laser_hatch") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.UP) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.top("adaptive_net_laser_hatch") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.DOWN) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.bottom() };
            }
            return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.side("adaptive_net_laser_hatch") };
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

    /**
     * 4.5.0-fix15：与 {@link AdaptiveNetHatch#getInputTier()} 同因同修（详见该处 javadoc）。
     * <p>
     * 注意本仓的<b>供电电压是 {@code V[tier] * 2}</b>（激光版每安培翻倍），所以"网络档位"与
     * "实际每安培电压所处档位"（{@code GTUtility.getTier(V[tier] * 2) == tier + 1}）差一档。
     * 此处刻意回报<b>网络档位</b>而不是浮上去的档位：
     * <ul>
     * <li>回报值要与玩家在终端里设置的档位一致，否则会出现"设 LuV 却显示 ZPM"的困惑；</li>
     * <li>需要精确电压的调用方本来就该读 {@link #maxEUInput()}，那是唯一权威的供电口径。</li>
     * </ul>
     * 未绑定时同样回落 {@code mTier}，理由见 {@link AdaptiveNetHatch#getInputTier()}。
     */
    @Override
    public long getInputTier() {
        return helper.isBound() ? helper.getCurrentVoltageTier() : mTier;
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
            // 3.32.0-fix4锛歝onsumed 鏄?杩?4 tick 绐楀彛"鐨勫閲忥紝蹇呴』鎹㈢畻鎴?EU/t锛堝惁鍒欏垪琛ㄥ疄娴嬫斁澶?4 鍊嶏級
            helper.updateRealFlowEUt(consumed > 0 ? consumed : 0L);
            BigInteger gridBig = AdaptiveHatchHelper.getGridEUBigInteger(owner);
            long gridEU = gridBig.min(BigInteger.valueOf(Long.MAX_VALUE))
                .longValue();
            helper.setLastGridEU(gridEU);
            helper.setLastGridEUBigInteger(gridBig);
            long maxStore = maxEUStore();
            long halfStore = maxStore / 2;
            if (currentStored < halfStore) {
                // 3.33.0-fix3：与 AdaptiveNetHatch 同修 —— 用「剩余空间」而不是「存量+电网」
                // 当补电上限，避免 gridEU 逼近 Long.MAX_VALUE 时加法环绕成负数导致补电静默跳过
                // （机器疯狂开关机的根因）。数学等价：min(halfStore, c+g) - c == min(halfStore-c, g)。
                // 4.5.0-fix30：不再先钳到 long；直接在 BigInteger 域取 min，消除 9.22E 钳制语义。
                long headroom = halfStore - currentStored; // currentStored < halfStore ⇒ 恒为正
                BigInteger diffBig = gridBig.min(BigInteger.valueOf(headroom));
                if (diffBig.signum() > 0) {
                    aBase.increaseStoredEnergyUnits(diffBig.longValue(), false);
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
        ModularPanel panel = ModularPanel.defaultPanel("adaptive_laser_hatch", 280, 200);

        IntSyncValue boundSync = new IntSyncValue(() -> helper.isBound() ? 1 : 0, v -> {});
        IntSyncValue voltageSync = new IntSyncValue(() -> helper.getCurrentVoltageTier(), v -> {});
        IntSyncValue ampSync = new IntSyncValue(() -> helper.getCurrentAmps(), v -> {});
        StringSyncValue gridEUSync = new StringSyncValue(
            () -> helper.isBound() && helper.getNetworkOwner() != null
                ? AdaptiveHatchHelper.getGridEUBigInteger(helper.getNetworkOwner())
                    .toString()
                : "0",
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

        column.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_laser_hatch.title")).size(260, 16));

        column.child(new TextWidget<>(IKey.dynamic(() -> {
            if (boundSync.getIntValue() == 0) {
                return EnumChatFormatting.RED + "鈼?"
                    + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.unbound");
            }
            return EnumChatFormatting.GREEN + "鈼?" + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.bound");
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
            // 4.5.0-fix30：Grid 行走服务端精确串，不再经过 getGridEULong 的 9.22E 钳制。
            return EnumChatFormatting.AQUA + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.grid_energy")
                + " "
                + EnumChatFormatting.WHITE
                + AdaptiveHatchHelper.formatEUExact(AdaptiveHatchHelper.parseEUString(gridEUSync.getValue()))
                + " EU";
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

    /**
     * 4.3.0-fix5：服务端把实时档位/安培/电网余额写进 WAILA 同步 tag（每次请求约 250ms 一次）。
     * 旧实现只重写客户端 getWailaBody、读本地 helper 字段 —— 那份字段只在区块(重)加载时
     * 从 NBT 恢复一次，悬浮提示永远是旧值（"WAILA 显示不正常"的根因）。
     */
    @Override
    public void getWailaNBTData(net.minecraft.entity.player.EntityPlayerMP player,
        net.minecraft.tileentity.TileEntity te, net.minecraft.nbt.NBTTagCompound tag, net.minecraft.world.World world,
        int x, int y, int z) {
        super.getWailaNBTData(player, te, tag, world, x, y, z);
        AdaptiveHatchHelper
            .writeWailaNBT(tag, helper.getNetworkOwner(), helper.getCurrentVoltageTier(), helper.getCurrentAmps());
    }

    @Override
    public void getWailaBody(ItemStack itemStack, java.util.List<String> currenttip,
        mcp.mobius.waila.api.IWailaDataAccessor accessor, mcp.mobius.waila.api.IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currenttip, accessor, config);
        try {
            AdaptiveHatchHelper.appendWailaBody(currenttip, accessor);
        } catch (Exception ignored) {}
    }
}
