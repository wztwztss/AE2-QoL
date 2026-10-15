package com.wztwzt.ae2_qof.hatch.adaptive;

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
     * 3.33.0-fix2锛氭敼鍥炵湡瀹炵數鍘嬫。 {@code V[tier]} 鈥斺€?涓庡悓鏃?{@code WirelessEnergyOutputTerminal.maxEUOutput()}
     * 锛?.25.0-fix16 宸叉敼涓?{@code V[mTier]}锛屾敞閲婂師璇?涓?GT 鑷繁鐨勫姩鍔涗粨涓€鑷?锛夊強 GT 鍘熺敓
     * {@code MTEHatchDynamo.maxEUOutput()} 鍚屽彛寰勩€?     * <p>涓轰粈涔堝繀椤绘敼锛欸T 鍦?{@code MTEMultiBlockBase.addEnergyOutputMultipleDynamos} 閲屾寜
     * 銆屛?鐢靛帇 脳 瀹夊煿)銆嶇疮鍔犳墍鏈夊姩鍔涗粨鐨勮緭鍑洪绠楋紝涓嶈冻鍗?{@code explodeMultiblock()}锛堝瓧鑺傜爜 offset 190-211锛夈€?     * 鏃у€?{@code Long.MAX_VALUE / 2} 涓嬶紝瀹夊煿 鈮? 鏃朵箻娉曞嵆鐜粫涓鸿礋锛? 鈫?-9.22e18锛? 鈫?0锛夛紝
     * 棰勭畻鍒よ礋 鈬?鍙戠數鏈哄鏂瑰潡姣?tick 琚湡寮曠垎锛汿ecTech 鏈哄垯鏄礋娉ㄥ叆 鈫?鐧藉緱 EU銆?     * <p>鐢ㄦ埛鎷嶆澘锛?026-10-14锛夛細**瀹夊煿涓嶈涓婇檺**鈥斺€旂畻鏈畨鍏ㄧ敱鐢靛帇鍙ｅ緞鐙珛鎵挎媴锛?     * V[tier] 鈮?8.59e9锛坱ier 15锛夛紝鍗充娇瀹夊煿鍒?2^31锛屼箻绉?鈮?1.8e19 浠嶅彲琚?long 鎵胯浇鏃剁殑
     * 甯歌鍦烘櫙锛堝浠撶疮鍔犳墠鍙兘閫艰繎涓婇檺锛夛紝涓?getCurrentVoltageTier() 鏈?0..15 閽冲埗锛?     * 涔樻硶涓ょ閮藉彈鎺с€?     */
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
        // 3.35.0-fix4（审查 P1-13）：GT 的 addEUToGlobalEnergyMap 在账相加为负时 return false 且不改账
        //（字节码 signum<0 → iconst_0）——旧代码不查返回值，拒收时电已从仓里扣掉却没进电网 = 静默蒸发。
        // 失败时把电塞回仓（与 util/WirelessEnergyTransfer 的回滚范式统一）并留痕。
        if (!WirelessNetworkManager.addEUToGlobalEnergyMap(owner, stored)) {
            aBase.increaseStoredEnergyUnits(stored, false);
            cn.dancingsnow.aeinfinitycell.AEInfinityCell.LOG.warn(
                "[AE2QoL] 自适应仓向电网推电被拒（电网余额不足以扣负数？），{} EU 已回滚到仓内：owner={}",
                stored,
                owner);
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
     * 3.33.0-fix3锛氱數缃戜綑棰濈殑 BigInteger 绮剧‘鐗堬紙>9.22E 鏃舵樉绀虹湡瀹炲€硷紝濡?15.80E锛夈€?     * 涓?AdaptiveNetHatch 鍚屾锛沇AILA/Grid 琛屽湪 helper 蹇収瓒呰繃 long 涓婇檺鏃惰蛋鏈柟娉曘€?     */
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
        if (abs.compareTo(E) >= 0) { whole = abs.divide(E); suffix = "E"; scale = 1e18; }
        else if (abs.compareTo(P) >= 0) { whole = abs.divide(P); suffix = "P"; scale = 1e15; }
        else if (abs.compareTo(T) >= 0) { whole = abs.divide(T); suffix = "T"; scale = 1e12; }
        else if (abs.compareTo(G) >= 0) { whole = abs.divide(G); suffix = "G"; scale = 1e9; }
        else if (abs.compareTo(M) >= 0) { whole = abs.divide(M); suffix = "M"; scale = 1e6; }
        else if (abs.compareTo(K) >= 0) { whole = abs.divide(K); suffix = "K"; scale = 1e3; }
        else { return (eu.signum() < 0 ? "-" : "") + abs; }
        double frac = abs.mod(BigInteger.valueOf((long) (scale / 100)))
            .doubleValue() / (scale / 100);
        return String.format("%s%.2f%s", eu.signum() < 0 ? "-" : "", whole.doubleValue() + frac / 100.0, suffix);
    }

    /** 3.33.0-fix3锛歐AILA"Grid"琛屽彇鏁?鈥斺€?瓒呰繃 long 涓婇檺璧扮簿纭樉绀猴紝鍚﹀垯缁存寔鏃ц矾寰勩€?*/
    private String formatGridEUForWaila() {
        BigInteger exact = helper.getLastGridEUBigInteger();
        if (exact.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0) {
            return formatEUBigInteger(exact);
        }
        return formatEU(helper.getLastGridEU());
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
            currenttip.add(EnumChatFormatting.AQUA + "Grid: " + formatGridEUForWaila());
            if (helper.isBound()) {
                currenttip
                    .add(EnumChatFormatting.GREEN + StatCollector.translateToLocal("ae2_qof.gui.adaptive_hatch.bound"));
            }
        } catch (Exception ignored) {}
    }
}
