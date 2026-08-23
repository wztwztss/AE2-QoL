package com.wztwzt.ae2_qof.merged.part;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.api.IMergedTerminalHost;
import com.wztwzt.ae2_qof.merged.BlockMergedTerminal;

import appeng.client.texture.CableBusTextures;
import appeng.core.localization.GuiText;
import appeng.parts.reporting.AbstractPartTerminal;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import cpw.mods.fml.common.network.internal.FMLNetworkHandler;

/**
 * 样板与接口二合一终端 —— 线缆面板部件形态。
 * <p>
 * 与方块形态 {@link com.wztwzt.ae2_qof.merged.TileMergedTerminal} 共享同一套
 * Container/Gui/PatternContainer 业务逻辑（经 {@link IMergedTerminalHost} 抽象）。
 * 数据（样板槽 + 编辑快照）经 part NBT 存进宿主线缆的 TileEntity。
 * <p>
 * 外观与原版 ME 终端部件一致（三层显示器贴图 + 通道/供电状态灯），
 * 需要 ME 通道并消耗 idle 电力（对齐原版终端行为）。
 */
public class PartMergedTerminal extends AbstractPartTerminal implements IMergedTerminalHost, IAEAppEngInventory {

    /** 样板面板的空白(0)/已编码(1)样板槽 */
    private final AppEngInternalInventory patternInv = new AppEngInternalInventory(this, 2);

    /** 面板编辑快照：0-8 合成3×3 | 9-40 扩展输入32 | 41-72 输出32 */
    private final AppEngInternalInventory savedGrid = new AppEngInternalInventory(this, 73);

    private boolean savedCraftingMode = true;

    public PartMergedTerminal(final ItemStack is) {
        super(is);
    }

    // ===== IMergedTerminalHost =====

    @Override
    public AppEngInternalInventory getPatternInv() {
        return this.patternInv;
    }

    @Override
    public AppEngInternalInventory getSavedGrid() {
        return this.savedGrid;
    }

    @Override
    public boolean getSavedCraftingMode() {
        return this.savedCraftingMode;
    }

    @Override
    public void setSavedCraftingMode(boolean v) {
        this.savedCraftingMode = v;
    }

    @Override
    public void markPersistDirty() {
        this.saveChanges();
    }

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public GuiText getName() {
        return GuiText.InterfaceTerminal;
    }

    // ===== 库存回调（IAEAppEngInventory） =====

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation op, ItemStack removedStack,
        ItemStack newStack) {
        this.saveChanges();
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        ItemStack own = com.wztwzt.ae2_qof.CommonProxy.itemPartMergedTerminal != null
            ? new ItemStack(com.wztwzt.ae2_qof.CommonProxy.itemPartMergedTerminal)
            : null;
        if (own != null) {
            return own;
        }
        // 极早期兜底：物品尚未注册完成时回退原版终端图标
        return appeng.api.AEApi.instance()
            .definitions()
            .parts()
            .terminal()
            .maybeStack(1)
            .orNull();
    }

    // ===== NBT / 同步 =====

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.patternInv.writeToNBT(data, "apuPatternInv");
        this.savedGrid.writeToNBT(data, "apuSavedGrid");
        data.setBoolean("apuSavedMode", this.savedCraftingMode);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.patternInv.readFromNBT(data, "apuPatternInv");
        if (data.hasKey("apuSavedGrid")) this.savedGrid.readFromNBT(data, "apuSavedGrid");
        this.savedCraftingMode = data.getBoolean("apuSavedMode");
    }

    // ===== 右键打开 GUI =====

    @Override
    public boolean onPartActivate(final EntityPlayer player, final Vec3 pos) {
        if (!player.worldObj.isRemote && this.tile != null) {
            final int sideOrd = this.getSide() != null ? this.getSide()
                .ordinal() : 0;
            FMLNetworkHandler.openGui(
                player,
                MyMod.instance,
                BlockMergedTerminal.PART_GUI_BASE + sideOrd,
                this.tile.getWorldObj(),
                this.tile.xCoord,
                this.tile.yCoord,
                this.tile.zCoord);
        }
        return true;
    }

    // ===== 外观（与原版 ME 终端部件一致） =====

    @Override
    public CableBusTextures getFrontBright() {
        return CableBusTextures.PartTerminal_Bright;
    }

    @Override
    public CableBusTextures getFrontDark() {
        return CableBusTextures.PartTerminal_Dark;
    }

    @Override
    public CableBusTextures getFrontColored() {
        return CableBusTextures.PartTerminal_Colored;
    }
}
