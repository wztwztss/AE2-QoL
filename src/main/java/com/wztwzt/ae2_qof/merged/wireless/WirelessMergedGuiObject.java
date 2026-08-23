package com.wztwzt.ae2_qof.merged.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.wztwzt.ae2_qof.api.IMergedTerminalHost;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.ILocatable;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.core.localization.GuiText;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;

/**
 * 手持无线二合一终端的 GUI 宿主对象（参照 AE2 WirelessTerminalGuiObject 思路）。
 * <p>
 * 不对应任何世界方块：grid 经绑定密钥（encryptionKey）→ LocatableRegistry →
 * 安全终端（ILocatable + IGridHost）解析；面板数据（样板槽 + 编辑快照）持久化在
 * 终端物品的 ItemStack NBT 中，关容器时由 {@link #writeBackToItem()} 回写。
 * <p>
 * 免电设计：extractAEPower 直接返回足额；跨维度访问由物品侧
 * {@code hasInfinityRange} 钩子保证，权限仍由网络安全层强制。
 */
public class WirelessMergedGuiObject implements IMergedTerminalHost, IPortableCell, IAEAppEngInventory {

    /** 物品 NBT 键（与 Part/Tile 形态保持同一套键名） */
    private static final String KEY_PATTERN = "apuPatternInv";
    private static final String KEY_SAVED_GRID = "apuSavedGrid";
    private static final String KEY_SAVED_MODE = "apuSavedMode";

    private final EntityPlayer player;
    private final int inventorySlot;
    private final ItemStack itemStack;

    private final AppEngInternalInventory patternInv = new AppEngInternalInventory(this, 2);
    private final AppEngInternalInventory savedGrid = new AppEngInternalInventory(this, 73);
    private boolean savedCraftingMode = true;

    /** 打开时解析的目标网络存储（station 被拆后不再有效） */
    private IStorageGrid storage;

    public WirelessMergedGuiObject(ItemStack term, EntityPlayer player, int inventorySlot) {
        this.itemStack = term;
        this.player = player;
        this.inventorySlot = inventorySlot;
        loadFromItem();
        resolveNetwork();
    }

    /** 当前绑定的安全终端节点；station 被拆后返回 null（容器据此踢出） */
    @Override
    public IGridNode getActionableNode() {
        try {
            String key = readEncryptionKey();
            if (key == null || key.isEmpty()) return null;
            ILocatable locatable = AEApi.instance()
                .registries()
                .locatable()
                .getLocatableBy(Long.parseLong(key));
            if (locatable instanceof IGridHost host) {
                return host.getGridNode(ForgeDirection.UNKNOWN);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 从终端物品 NBT 读取绑定密钥 */
    private String readEncryptionKey() {
        return this.itemStack.stackTagCompound != null
            ? this.itemStack.stackTagCompound.getString(ItemWirelessMergedTerminal.KEY_ENCRYPTION)
            : "";
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return getActionableNode();
    }

    /** IGridHost 抽象方法：GUI 宿主对象无安全拆除语义，空实现 */
    @Override
    public void securityBreak() {}

    /** IGridHost 抽象方法：宿主对象不参与线缆连接渲染 */
    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.NONE;
    }

    private void loadFromItem() {
        NBTTagCompound tag = this.itemStack.stackTagCompound;
        if (tag == null) return;
        if (tag.hasKey(KEY_PATTERN)) this.patternInv.readFromNBT(tag, KEY_PATTERN);
        if (tag.hasKey(KEY_SAVED_GRID)) this.savedGrid.readFromNBT(tag, KEY_SAVED_GRID);
        this.savedCraftingMode = tag.getBoolean(KEY_SAVED_MODE);
    }

    private void resolveNetwork() {
        try {
            IGridNode node = getActionableNode();
            if (node != null && node.getGrid() != null) {
                IGrid grid = node.getGrid();
                this.storage = (IStorageGrid) grid.getCache(IStorageGrid.class);
            }
        } catch (Throwable ignored) {
            this.storage = null;
        }
    }

    /** 关容器时把面板数据回写终端物品并同步到玩家背包 */
    public void writeBackToItem() {
        NBTTagCompound tag = this.itemStack.stackTagCompound;
        if (tag == null) {
            tag = new NBTTagCompound();
            this.itemStack.stackTagCompound = tag;
        }
        this.patternInv.writeToNBT(tag, KEY_PATTERN);
        this.savedGrid.writeToNBT(tag, KEY_SAVED_GRID);
        tag.setBoolean(KEY_SAVED_MODE, this.savedCraftingMode);
        // 物品是玩家背包内引用，标记背包脏以确保持久化
        if (this.player != null) {
            this.player.inventory.markDirty();
        }
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
        // 无线形态即时回写物品，防中途掉线丢失编辑
        writeBackToItem();
    }

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public GuiText getName() {
        return GuiText.InterfaceTerminal;
    }

    // ===== IPortableCell（ITerminalHost + IEnergySource + IGuiItemObject + IInventorySlotAware） =====

    @Override
    public IMEMonitor<IAEItemStack> getItemInventory() {
        return this.storage != null ? this.storage.getItemInventory() : null;
    }

    @Override
    public IMEMonitor<IAEFluidStack> getFluidInventory() {
        return this.storage != null ? this.storage.getFluidInventory() : null;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) {
        // 免电设计：永远足额
        return amt;
    }

    @Override
    public ItemStack getItemStack() {
        return this.itemStack;
    }

    @Override
    public int getInventorySlot() {
        return this.inventorySlot;
    }

    @Override
    public appeng.api.util.IConfigManager getConfigManager() {
        return null; // 不提供原版终端的配置面板
    }

    // ===== 库存回调 =====

    @Override
    public void saveChanges() {
        markPersistDirty();
    }

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation op, ItemStack removedStack,
        ItemStack newStack) {
        markPersistDirty();
    }
}
