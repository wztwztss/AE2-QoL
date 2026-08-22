package com.wztwzt.ae2_qof.merged;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.parts.IInterfaceTerminal;
import appeng.core.localization.GuiText;
import appeng.me.GridAccessException;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import io.netty.buffer.ByteBuf;

/**
 * 样板与接口二合一终端（有线方块）。仅作为一个标准网格节点挂入 ME 网络，
 * 打开终端时由容器收集网络内所有可用的接口/样板供应器。
 * <p>
 * 空白/已编码样板槽随方块持久化（原版 AE 样板终端行为），避免关闭再打开终端后内容丢失。
 */
public class TileMergedTerminal extends AENetworkTile
    implements IInterfaceTerminal, IAEAppEngInventory, IPowerChannelState {

    /** 样板面板的空白(0)/已编码(1)样板槽，随方块保存/读取 */
    private final AppEngInternalInventory patternInv = new AppEngInternalInventory(this, 2);

    /**
     * 面板编辑快照，关闭 GUI 时保存、打开时恢复：
     * 0-8 合成3×3 | 9-40 扩展输入32 | 41-72 输出32。随方块持久化。
     */
    private final AppEngInternalInventory savedGrid = new AppEngInternalInventory(this, 73);

    /** 快照对应的编码模式 */
    private boolean savedCraftingMode = true;

    /** 供电/频道状态（同步到客户端供 WAILA 等读取） */
    private boolean isPowered = false;

    public AppEngInternalInventory getPatternInv() {
        return patternInv;
    }

    public AppEngInternalInventory getSavedGrid() {
        return savedGrid;
    }

    public boolean getSavedCraftingMode() {
        return savedCraftingMode;
    }

    public void setSavedCraftingMode(boolean v) {
        this.savedCraftingMode = v;
    }

    @Override
    public boolean isPowered() {
        return isPowered;
    }

    public boolean isActive() {
        return isPowered;
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_Power(ByteBuf data) {
        final boolean old = isPowered;
        this.isPowered = data.readBoolean();
        return isPowered != old;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_Power(ByteBuf data) {
        data.writeBoolean(isActive());
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange p) {
        updatePowerState();
    }

    @MENetworkEventSubscribe
    public final void bootingRender(final MENetworkBootingStatusChange c) {
        updatePowerState();
    }

    private void updatePowerState() {
        boolean newState = false;
        try {
            newState = this.getProxy().isActive()
                && this.getProxy()
                    .getEnergy()
                    .extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.0001;
        } catch (final GridAccessException ignored) {}
        if (newState != isPowered) {
            isPowered = newState;
            this.markForUpdate();
        }
    }

    @Override
    public void saveChanges() {
        this.markDirty();
    }

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation op, ItemStack removedStack, ItemStack newStack) {
        this.markDirty();
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_World(NBTTagCompound data) {
        this.patternInv.writeToNBT(data, "apuPatternInv");
        this.savedGrid.writeToNBT(data, "apuSavedGrid");
        data.setBoolean("apuSavedMode", this.savedCraftingMode);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_World(NBTTagCompound data) {
        this.patternInv.readFromNBT(data, "apuPatternInv");
        if (data.hasKey("apuSavedGrid")) this.savedGrid.readFromNBT(data, "apuSavedGrid");
        this.savedCraftingMode = data.getBoolean("apuSavedMode");
    }

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public GuiText getName() {
        return GuiText.InterfaceTerminal;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return super.getGridNode(dir);
    }

    @Override
    public IGridNode getActionableNode() {
        return super.getActionableNode();
    }
}
