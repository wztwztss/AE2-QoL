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
        this.terminalId = 32001; // newMetaEntity 用的构造器，ID 固定
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
