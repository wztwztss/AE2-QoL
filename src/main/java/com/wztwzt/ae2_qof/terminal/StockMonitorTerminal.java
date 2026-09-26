package com.wztwzt.ae2_qof.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;

/**
 * 库存统计终端（M6）：集中查看/修改 AE2 标准发信器与本模组库存检测覆盖板。
 * 免电信息终端。
 *
 * fix12: 所有 public 方法加 try-catch 防御层，避免 init 阶段其他 mod 遍历 MTE
 *        调用本类方法时抛异常逃逸到 FML/Log4j（Log4j 用 RFB 加载堆栈里的
 *        ISidedInventory 会二次崩溃）。同时重写 getStackForm 不依赖 getBaseMetaTileEntity。
 */
public class StockMonitorTerminal extends MTEHatch {

    private String networkId = "";
    /** fix12: 保存自己的 ID，getStackForm 不依赖 getBaseMetaTileEntity()（init 阶段可能为 null） */
    private final int terminalId;

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId == null ? "" : networkId;
    }

    public boolean isBound() {
        return networkId != null && !networkId.isEmpty();
    }

    public StockMonitorTerminal(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 1, new String[] { "Stock Monitor Terminal" });
        this.terminalId = aID;
    }

    public StockMonitorTerminal(String aName, int aTier, int aInvSlotCount, String[] aDescription,
            ITexture[][][] aTextures) {
        super(aName, aTier, aInvSlotCount, aDescription, aTextures);
        this.terminalId = 32107; // newMetaEntity 用的构造器，ID 固定（fix48：原 32101 已让给 fissionevolved）
    }

    /**
     * fix12: 重写 getStackForm，直接用保存的 terminalId，不调用 getBaseMetaTileEntity()。
     * 父类默认实现 this.getBaseMetaTileEntity().getMetaTileID() 在 init 阶段 base 可能为 null。
     */
    @Override
    public ItemStack getStackForm(long aAmount) {
        try {
            return new ItemStack(GregTechAPI.sBlockMachines, (int) aAmount, terminalId);
        } catch (Throwable t) {
            System.err.println("[AE2QoL] StockMonitorTerminal.getStackForm FAILED: " + t);
            t.printStackTrace(System.err);
            return null;
        }
    }

    @Override
    public ItemStack getMachineCraftingIcon() {
        return getStackForm(1L);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTile) {
        try {
            return new StockMonitorTerminal(mName, mTier, 1, mDescriptionArray, mTextures);
        } catch (Throwable t) {
            System.err.println("[AE2QoL] StockMonitorTerminal.newMetaEntity FAILED: " + t);
            t.printStackTrace(System.err);
            return null;
        }
    }

    // ===================== 3.21.0：汉化 + 高亮/传送所需的会话与网络 =====================

    /**
     * 显示名：GT 的机器名走 {@code IMetaTileEntity.getLocalName()} →
     * {@code StatCollector.translateToLocal("gt.blockmachines.<mName>.name")}。
     * 这里显式覆写一次，保证不依赖 GT 内部取名字的具体路径（语言文件里同名键同时补上）。
     */
    @Override
    public String getLocalName() {
        try {
            String localized = StatCollector.translateToLocal("gt.blockmachines.stock_monitor_terminal.name");
            if (localized != null && !localized.isEmpty()) return localized;
        } catch (Throwable ignored) {}
        return "Stock Monitor Terminal";
    }

    /**
     * 「正打开本终端界面」的玩家集合（等价自适应电网终端的 activeViewers / 审查 P1-011）：
     * 高亮/传送请求只有在会话内才被接受，避免任意玩家凭构造包探测或传送到他人设备。
     */
    private final java.util.Set<java.util.UUID> activeViewers = java.util.Collections
        .newSetFromMap(new java.util.concurrent.ConcurrentHashMap<java.util.UUID, Boolean>());

    public void registerActiveViewer(EntityPlayer player) {
        if (player != null) activeViewers.add(player.getUniqueID());
    }

    public boolean isActiveViewer(java.util.UUID id) {
        return id != null && activeViewers.contains(id);
    }

    /** 终端当前生效的 AE 网络：优先 Nexus 无线绑定，其次邻接连接。 */
    public appeng.api.networking.IGrid resolveGrid() {
        try {
            net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) getBaseMetaTileEntity();
            if (te == null) return null;
            if (isBound() && com.wztwzt.ae2_qof.cover.stockmonitor.ae.WirelessAeConnector.isNexusAvailable()) {
                try {
                    java.util.UUID netId = java.util.UUID.fromString(getNetworkId());
                    appeng.api.networking.IGrid grid = com.wztwzt.ae2_qof.cover.stockmonitor.ae.WirelessAeConnector
                        .getGridForNetwork(netId, te.getWorldObj());
                    if (grid != null) return grid;
                } catch (IllegalArgumentException ignored) {}
            }
            return com.wztwzt.ae2_qof.cover.stockmonitor.ae.NeighborAeConnector
                .findGrid(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord, ForgeDirection.UNKNOWN);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 终端所连 AE 网络的 BUILD 权限（终端完全没连网时放行）。
     * 覆盖板远程编辑与高亮/传送共用这一条判据（原实现在 GUI 里，3.21.0 移到终端本体，
     * 让网络包也能复用同一套鉴权）。
     */
    public boolean hasBuildPermission(EntityPlayer player) {
        appeng.api.networking.IGrid grid = resolveGrid();
        if (grid == null) return true;
        try {
            appeng.api.networking.security.ISecurityGrid security = grid
                .getCache(appeng.api.networking.security.ISecurityGrid.class);
            if (security == null || !security.isAvailable()) return true;
            return security.hasPermission(player, appeng.api.config.SecurityPermissions.BUILD);
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 推进"高亮自动清除"队列（10 秒）。
     * 该队列由 {@link com.wztwzt.ae2_qof.network.HatchActionPacket} 维护，原本只在自适应电网终端
     * 的 tick 里推进——那样"没装自适应终端"的存档里高亮会永不消失。这里补上推进点。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        try {
            if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
                com.wztwzt.ae2_qof.network.HatchActionPacket.tickPendingClears();
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public String[] getDescription() {
        try {
            return new String[] {
                StatCollector.translateToLocal("ae2_qof.terminal.stock_monitor.desc"),
                StatCollector.translateToLocal("ae2_qof.terminal.stock_monitor.desc.0"),
                StatCollector.translateToLocal("ae2_qof.terminal.stock_monitor.desc.1"),
                EnumChatFormatting.GRAY + "[" + StatCollector.translateToLocal("ae2_qof.modname") + "]",
                EnumChatFormatting.DARK_GRAY + "ae2qof"
            };
        } catch (Throwable t) {
            System.err.println("[AE2QoL] StockMonitorTerminal.getDescription FAILED: " + t);
            return new String[] { "Stock Monitor Terminal", "[ae2qof]" };
        }
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        try {
            return new ITexture[] { aBaseTexture,
                TextureFactory.of(Textures.BlockIcons.OVERLAY_SCREEN) };
        } catch (Throwable t) {
            System.err.println("[AE2QoL] StockMonitorTerminal.getTexturesActive FAILED: " + t);
            return new ITexture[] { aBaseTexture };
        }
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        try {
            return new ITexture[] { aBaseTexture,
                TextureFactory.of(Textures.BlockIcons.OVERLAY_SCREEN) };
        } catch (Throwable t) {
            System.err.println("[AE2QoL] StockMonitorTerminal.getTexturesInactive FAILED: " + t);
            return new ITexture[] { aBaseTexture };
        }
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        try {
            if (aBaseMetaTileEntity.isClientSide()) return true;
            openGui(aPlayer);
            return true;
        } catch (Throwable t) {
            System.err.println("[AE2QoL] StockMonitorTerminal.onRightclick FAILED: " + t);
            t.printStackTrace(System.err);
            return false;
        }
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        try {
            return StockMonitorTerminalGui.build(this, guiData, syncManager, uiSettings);
        } catch (Throwable t) {
            System.err.println("[AE2QoL] StockMonitorTerminal.buildUI FAILED: " + t);
            t.printStackTrace(System.err);
            return null;
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        try {
            aNBT.setString("NetworkId", networkId == null ? "" : networkId);
        } catch (Throwable t) {
            System.err.println("[AE2QoL] StockMonitorTerminal.saveNBTData FAILED: " + t);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        try {
            networkId = aNBT.getString("NetworkId");
            if (networkId == null) networkId = "";
        } catch (Throwable t) {
            System.err.println("[AE2QoL] StockMonitorTerminal.loadNBTData FAILED: " + t);
            networkId = "";
        }
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
            ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
            ItemStack aStack) {
        return false;
    }

    @Override
    public boolean canExtractItem(int aIndex, ItemStack aStack, int aSide) {
        return false;
    }

    @Override
    public boolean canInsertItem(int aIndex, ItemStack aStack, int aSide) {
        return false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int p_94128_1_) {
        return new int[0];
    }

    @Override
    public boolean isItemValidForSlot(int p_94041_1_, ItemStack p_94041_2_) {
        return false;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public int getSizeInventory() { return 0; }

    @Override
    public ItemStack getStackInSlot(int p_70301_1_) { return null; }

    @Override
    public ItemStack decrStackSize(int p_70298_1_, int p_70298_2_) { return null; }

    @Override
    public ItemStack getStackInSlotOnClosing(int p_70304_1_) { return null; }

    @Override
    public void setInventorySlotContents(int p_70299_1_, ItemStack p_70299_2_) {}

    @Override
    public String getInventoryName() { return "StockMonitorTerminal"; }

    @Override
    public boolean hasCustomInventoryName() { return false; }

    @Override
    public int getInventoryStackLimit() { return 64; }

    @Override
    public boolean isUseableByPlayer(EntityPlayer p_70300_1_) { return true; }

    @Override
    public void markDirty() {}
}
