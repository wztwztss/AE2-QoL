package com.wztwzt.ae2_qof.cover.stockmonitor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;

import com.cleanroommc.modularui.utils.item.InvWrapper;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.interfaces.tileentity.IMachineProgress;
import gregtech.common.covers.Cover;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;

import appeng.api.networking.IGrid;
import appeng.api.storage.data.IAEStack;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.AeConnector;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.AeStockReader;

public class StockMonitorCover extends Cover {

    private final StockMonitorCoverData data = new StockMonitorCoverData();
    private final PhantomInventory phantomInventory = new PhantomInventory();

    public StockMonitorCover(CoverContext context, ITexture coverTexture) {
        super(context, coverTexture);
    }

    public StockMonitorCoverData getCoverData() {
        return data;
    }

    /** 获取 PhantomInventory 的 InvWrapper（GUI 用 ModularSlot(handler, slotIndex) 指定槽位） */
    public InvWrapper getPhantomSlot() {
        return new InvWrapper(phantomInventory);
    }

    /** 标记覆盖板数据已变更（供统计终端远程修改后调用） */
    public void markCoverDirty() {
        needsUpdate = true;
        ICoverable ct = coveredTile.get();
        if (ct instanceof net.minecraft.tileentity.TileEntity) {
            ((net.minecraft.tileentity.TileEntity) ct).markDirty();
        }
    }

    // ===== 放置限制：同种板一机一块 =====

    public static boolean isCoverPlaceable(ForgeDirection side, ItemStack coverItem, ICoverable coverable) {
        for (ForgeDirection tSide : ForgeDirection.VALID_DIRECTIONS) {
            if (coverable.getCoverAtSide(tSide) instanceof StockMonitorCover) {
                return false;
            }
        }
        return true;
    }

    // ===== 覆盖板行为（多槽位 OR 判定 fix14）=====

    @Override
    public void doCoverThings(byte aRedstone, long aTickTimer) {
        ICoverable tile = coveredTile.get();
        if (!(tile instanceof IMachineProgress)) return;

        IMachineProgress machine = (IMachineProgress) tile;

        // 1. 获取 IGrid（双通道）
        int x = tile.getXCoord();
        int y = tile.getYCoord();
        int z = tile.getZCoord();
        net.minecraft.world.World world = null;
        if (tile instanceof net.minecraft.tileentity.TileEntity) {
            world = ((net.minecraft.tileentity.TileEntity) tile).getWorldObj();
        }
        AeConnector.GridResult result = AeConnector.getGrid(
            data.getNetworkId(), world, x, y, z, coverSide);

        if (result.grid == null) {
            data.setLastChannel(AeConnector.CHANNEL_NONE);
            for (StockMonitorCoverData.MonitorSlot slot : data.getSlots()) {
                slot.lastStock = 0;
            }
            data.setLastShouldWork(false);
            if (machine.isAllowedToWork()) machine.disableWorking();
            return;
        }

        data.setLastChannel(result.channel);

        // 2. 遍历所有有效槽位，查询库存并 OR 判定
        boolean anyActive = false;
        boolean hasValidSlot = false;
        for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
            StockMonitorCoverData.MonitorSlot slot = data.getSlot(i);
            if (slot.isEmpty() || slot.monitorTarget == null) {
                slot.lastStock = 0;
                continue;
            }
            hasValidSlot = true;
            long count = AeStockReader.readStock(result.grid, slot.monitorTarget);
            slot.lastStock = count;
            if (data.getMode().shouldWork(count, slot.threshold)) {
                anyActive = true;
            }
        }

        if (!hasValidSlot) {
            data.setLastShouldWork(false);
            if (machine.isAllowedToWork()) machine.disableWorking();
        } else {
            // OR 逻辑：任意一槽满足条件即启动
            data.setLastShouldWork(anyActive);
            if (anyActive && !machine.isAllowedToWork()) machine.enableWorking();
            if (!anyActive && machine.isAllowedToWork()) machine.disableWorking();
        }

        // 3. 注册到全局 CoverRegistry（供统计终端集中查看）
        try {
            ICoverable ct = coveredTile.get();
            if (ct instanceof net.minecraft.tileentity.TileEntity) {
                net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) ct;
                com.wztwzt.ae2_qof.terminal.CoverRegistry.get(te.getWorldObj())
                    .upsert(te.getWorldObj().provider.dimensionId, te.xCoord, te.yCoord, te.zCoord,
                        coverSide.ordinal(), data);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCoverRemoval() {
        ICoverable tile = coveredTile.get();
        if (tile instanceof IMachineProgress) {
            ((IMachineProgress) tile).enableWorking();
        }
        try {
            if (tile instanceof net.minecraft.tileentity.TileEntity) {
                net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) tile;
                com.wztwzt.ae2_qof.terminal.CoverRegistry.get(te.getWorldObj())
                    .remove(te.getWorldObj().provider.dimensionId, te.xCoord, te.yCoord, te.zCoord,
                        coverSide.ordinal());
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isRedstoneSensitive(long aTimer) {
        return false;
    }

    @Override
    public int getMinimumTickRate() {
        return 10;
    }

    @Override
    public boolean isDataNeededOnClient() {
        return true;
    }

    @Override
    public boolean letsEnergyIn() { return true; }

    @Override
    public boolean letsEnergyOut() { return true; }

    @Override
    public boolean letsFluidIn(Fluid fluid) { return true; }

    @Override
    public boolean letsFluidOut(Fluid fluid) { return true; }

    @Override
    public boolean letsItemsIn(int slot) { return true; }

    @Override
    public boolean letsItemsOut(int slot) { return true; }

    // ===== GUI =====

    @Override
    public boolean hasCoverGUI() {
        return true;
    }

    @Override
    protected CoverBaseGui<?> getCoverGui() {
        return new com.wztwzt.ae2_qof.cover.stockmonitor.gui.StockMonitorCoverGui(this);
    }

    // ===== NBT 持久化 =====

    @Override
    protected NBTBase saveDataToNbt() {
        NBTTagCompound nbt = new NBTTagCompound();
        data.saveToNbt(nbt);
        return nbt;
    }

    @Override
    protected void readDataFromNbt(NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            data.readFromNbt((NBTTagCompound) nbt);
            phantomInventory.syncFromCoverData();
        }
    }

    // ===== 网络同步（多槽位 fix14）=====
    // phantomStack 由 MUI ModularSlot 自动 C2S 同步，这里只同步 threshold/mode/运行态

    @Override
    public void writeDataToByteBuf(io.netty.buffer.ByteBuf byteBuf) {
        super.writeDataToByteBuf(byteBuf);
        byteBuf.writeBoolean(data.isBound());
        byte[] bytes = data.getNetworkId() == null ? new byte[0]
            : data.getNetworkId().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byteBuf.writeShort(bytes.length);
        byteBuf.writeBytes(bytes);
        byteBuf.writeByte(data.getMode().ordinal());

        // 9 个槽位的 threshold
        for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
            byteBuf.writeLong(data.getSlot(i).threshold);
        }

        // 运行态
        byteBuf.writeInt(data.getLastChannel());
        byteBuf.writeBoolean(data.isLastShouldWork());
        for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
            byteBuf.writeLong(data.getSlot(i).lastStock);
        }
    }

    @Override
    public void readDataFromPacket(com.google.common.io.ByteArrayDataInput byteData) {
        super.readDataFromPacket(byteData);
        boolean bound = byteData.readBoolean();
        short len = byteData.readShort();
        if (len > 0) {
            byte[] bytes = new byte[len];
            byteData.readFully(bytes);
            data.setNetworkId(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } else {
            data.setNetworkId("");
        }
        data.setMode(ThresholdMode.fromOrdinal(byteData.readByte()));

        for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
            data.getSlot(i).threshold = byteData.readLong();
        }

        data.setLastChannel(byteData.readInt());
        data.setLastShouldWork(byteData.readBoolean());
        for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
            data.getSlot(i).lastStock = byteData.readLong();
        }
    }

    // ===== Phantom Inventory（9 槽虚拟槽，不消耗物品）=====

    private class PhantomInventory extends InventoryBasic {

        private boolean updating = false;

        PhantomInventory() {
            super("StockMonitorPhantom", false, StockMonitorCoverData.MAX_SLOTS);
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            super.setInventorySlotContents(slot, stack);
            if (!updating && slot >= 0 && slot < StockMonitorCoverData.MAX_SLOTS) {
                updating = true;
                StockMonitorCoverData.MonitorSlot s = data.getSlot(slot);
                s.phantomStack = stack == null ? null : stack.copy();
                s.monitorTarget = StockMonitorCoverData.resolveTarget(s.phantomStack);
                updating = false;
            }
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack result = super.decrStackSize(slot, amount);
            if (!updating && slot >= 0 && slot < StockMonitorCoverData.MAX_SLOTS) {
                updating = true;
                StockMonitorCoverData.MonitorSlot s = data.getSlot(slot);
                s.phantomStack = getStackInSlot(slot) == null ? null : getStackInSlot(slot).copy();
                s.monitorTarget = StockMonitorCoverData.resolveTarget(s.phantomStack);
                updating = false;
            }
            return result;
        }

        @Override
        public void markDirty() {
            super.markDirty();
            ICoverable tile = coveredTile.get();
            if (tile instanceof net.minecraft.tileentity.TileEntity) {
                ((net.minecraft.tileentity.TileEntity) tile).markDirty();
            }
        }

        public void syncFromCoverData() {
            updating = true;
            for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
                ItemStack phantom = data.getSlot(i).phantomStack;
                super.setInventorySlotContents(i, phantom == null ? null : phantom.copy());
            }
            updating = false;
        }
    }
}
