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
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
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
        return new String[] { StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_target_hatch.desc"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_target_hatch.desc.0"),
            StatCollector.translateToLocal("gt.blockmachines.adaptive_net_laser_target_hatch.desc.1"),
            EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
            EnumChatFormatting.DARK_GRAY + "ae2qof" };
    }

    @Override
    public ITexture[] getTexture(gregtech.api.interfaces.tileentity.IGregTechTileEntity aBaseMetaTileEntity,
        net.minecraftforge.common.util.ForgeDirection side, net.minecraftforge.common.util.ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (com.wztwzt.ae2_qof.util.ModTextures.isReady()) {
            if (side == facing) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.front("adaptive_net_laser_target") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.UP) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.top("adaptive_net_laser_target") };
            }
            if (side == net.minecraftforge.common.util.ForgeDirection.DOWN) {
                return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.bottom() };
            }
            return new ITexture[] { com.wztwzt.ae2_qof.util.ModTextures.side("adaptive_net_laser_target") };
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

    /**
     * 4.5.0-fix15：输出侧对称修法，与 {@link AdaptiveNetHatch#getInputTier()} 同因
     * （详见该处 javadoc）。
     * <p>
     * <b>本仓不能像动力仓那样等价于 {@code GTUtility.getTier(getOutputVoltage())}</b>：
     * 这里的 {@link #maxEUOutput()} 返回的是 {@code Long.MAX_VALUE / 2}（激光靶仓按"能接多少"设计，
     * 不是按档位），拿它反推档位会得到 MAX+。所以档位只能直接取网络档位。
     * <p>
     * 未绑定时回落 {@code mTier}（本仓注册档 = 6 = LuV）：{@code getOutputTier()} 的 0 会被
     * {@code MTEIndustrialLaserEngraver} 之类的消费方当成真实索引（{@code VN[laserTier + 1]}）。
     */
    @Override
    public long getOutputTier() {
        return helper.isBound() ? helper.getCurrentVoltageTier() : mTier;
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
        // 3.35.0-fix4（审查 P1-13）：GT 的 addEUToGlobalEnergyMap 在账相加为负时 return false 且不改账
        // （字节码 signum<0 → iconst_0）——旧代码不查返回值，拒收时电已从仓里扣掉却没进电网 = 静默蒸发。
        // 失败时把电塞回仓（与 util/WirelessEnergyTransfer 的回滚范式统一）并留痕。
        if (!WirelessNetworkManager.addEUToGlobalEnergyMap(owner, stored)) {
            aBase.increaseStoredEnergyUnits(stored, false);
            cn.dancingsnow.aeinfinitycell.AEInfinityCell.LOG
                .warn("[AE2QoL] 自适应仓向电网推电被拒（电网余额不足以扣负数？），{} EU 已回滚到仓内：owner={}", stored, owner);
            return;
        }
        // 3.32.0-fix4锛氭湰娆℃帹閫佺殑鏄?杩?4 tick 绐楀彛"绱鐨勭紦瀛橀噺锛屾崲绠楁垚 EU/t 鎵嶆槸閫熺巼
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
        ModularPanel panel = ModularPanel.defaultPanel("adaptive_laser_target_hatch", 280, 180);

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

        column.child(new TextWidget<>(IKey.lang("ae2_qof.gui.adaptive_laser_target_hatch.title")).size(260, 16));

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
