package com.wztwzt.ae2_qof.hatch.adaptive;

import static gregtech.api.enums.GTValues.V;

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

    /**
     * 4.5.0-fix15：档位 accessor 必须与供电 accessor 同源，否则机器读到的是两个互相矛盾的电压。
     * <p>
     * GT 有两族互不相干的读取路径（均以实例 GT 5.09.54.205 jar 的 javap 核对）：
     * <ul>
     * <li><b>供电族</b>：{@code BaseMetaTileEntity.getInputVoltage()} → {@link #maxEUInput()}（动态）⇒
     * {@code getMaxInputVoltage/getAverageInputVoltage/getMaxInputEu} 全部正确，
     * 所以配方电压与超频一直是对的（这也是"机器看起来能识别高电压"的来源）；</li>
     * <li><b>档位族</b>：{@code MTETieredMachineBlock.getInputTier()} 字节码就是
     * {@code getfield mTier; i2l}（本仓注册档 = 5 = IV，final 字段）⇒
     * {@code MTEMultiBlockBase.getInputVoltageTier()}、调试扫描仪的 "xxxA IV"、
     * WAILA 的能源等级、TenkeiTech 模板总线的"当前电压"全部拿到 IV。</li>
     * </ul>
     * 两族数值相等但档位不同，表现就是扫描仪同一行里 {@code 8,388,608 EU/t (1024A IV) Power Tier: UEV}
     * ——分子对、除数错。普通能源仓两族恒等（都取 mTier），所以从不暴露这个差异。
     * <p>
     * 覆盖后与 GT 自己的通用定义 {@code GTUtility.getTier(getInputVoltage())} 等价
     * （因为 {@link #maxEUInput()} 正是 {@code V[tier]}），不引入任何新语义。
     * <p>
     * <b>未绑定时回落 {@code mTier} 而不是 0</b>：{@code getInputVoltageTier()} 里 0 是"各仓档位不一致"
     * 的哨兵值，且第三方（如 TenkeiTech）会把 {@code <=0} 当成"特殊模式/不限电压"而全量放行配方。
     *
     * @see #maxEUInput() 供电口径，两者必须同源
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
