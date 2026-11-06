package com.wztwzt.ae2_qof.cover.stockmonitor;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;

import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.AeConnector;
import com.wztwzt.ae2_qof.cover.stockmonitor.ae.AeStockReader;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.interfaces.tileentity.IMachineProgress;
import gregtech.common.covers.Cover;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;

public class StockMonitorCover extends Cover {

    /** GUI（MUI sync supplier）最近一次求值后多久仍视为"GUI 打开"（容忍网络抖动）。 */
    private static final int GUI_POLL_STALE_TICKS = 10;
    /** 读取间隔的下限（tick）。 */
    private static final int MIN_READ_INTERVAL = 5;
    /** 读取间隔的上限（tick）。 */
    private static final int MAX_READ_INTERVAL = 1200;

    private final StockMonitorCoverData data = new StockMonitorCoverData();
    private final PhantomInventory phantomInventory = new PhantomInventory();

    // ===== 节流运行态（不持久化；性能修复版） =====
    /** 下次允许真正读网络的 world time（tick），未到期直接沿用缓存判定。 */
    private long nextReadTick = Long.MIN_VALUE;
    /** 最近一次 GUI sync supplier 求值时的 world time；久未求值 ⇒ GUI 已关闭，回落常规频率。 */
    private long guiPollTick = Long.MIN_VALUE;
    /** 批量读复用缓冲：9 个槽位的 monitorTarget（空槽放 null 占位，下标即槽位号）。 */
    private final java.util.ArrayList<appeng.api.storage.data.IAEStack<?>> readTargets = new java.util.ArrayList<>(
        StockMonitorCoverData.MAX_SLOTS);
    /** 批量读结果（下标即槽位号），与 readTargets 配套复用。 */
    private final long[] readResults = new long[StockMonitorCoverData.MAX_SLOTS];
    /**
     * 全局读取相位（tick）。所有盖板共用同一相位，保证同一间隔内只有一次「全网重建」，
     * 同时把这次重建错开 GT 机器 20t 配方完成的节拍。
     * spark 实测：重建（44ms）与机器突发（43ms）同 tick 时该 tick 达 133ms。
     */
    private static final int READ_PHASE_TICKS = 7;

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

    /**
     * GUI 打开检测（性能修复版）：GUI 的 MUI sync value supplier 在容器打开期间每 tick 被
     * 服务端求值一次（ModularContainer.detectAndSendChanges），GUI 的 stock/channel supplier
     * 里回调本方法即可让服务端零开销地知道"有人正看着这块盖板"。
     */
    public void noteGuiPoll() {
        ICoverable ct = coveredTile.get();
        if (ct instanceof net.minecraft.tileentity.TileEntity) {
            net.minecraft.world.World w = ((net.minecraft.tileentity.TileEntity) ct).getWorldObj();
            if (w != null) guiPollTick = w.getTotalWorldTime();
        }
    }

    /** 当前生效的读取间隔：盖板 NBT 覆盖 > config 默认；GUI 打开时再压到 config 的 GUI 间隔。 */
    private int effectiveReadInterval(long now) {
        int interval = data.getReadInterval() > 0 ? data.getReadInterval()
            : com.wztwzt.ae2_qof.Config.stockMonitorReadIntervalTicks;
        if (interval < MIN_READ_INTERVAL) interval = MIN_READ_INTERVAL;
        if (interval > MAX_READ_INTERVAL) interval = MAX_READ_INTERVAL;
        // 注意溢出：guiPollTick 初值 MIN_VALUE 时 now-guiPollTick 会回绕成负数，
        // 必须先确认 guiPollTick <= now（即确实发生过轮询）再比较差值。
        if (guiPollTick != Long.MIN_VALUE && guiPollTick <= now && now - guiPollTick <= GUI_POLL_STALE_TICKS) {
            int gui = com.wztwzt.ae2_qof.Config.stockMonitorGuiIntervalTicks;
            if (gui < 2) gui = 2;
            if (gui < interval) interval = gui;
        }
        return interval;
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

    // ===== 覆盖板行为（多槽位 OR 判定 fix14；性能修复版加节流+批量读）=====

    @Override
    public void doCoverThings(byte aRedstone, long aTickTimer) {
        ICoverable tile = coveredTile.get();
        if (!(tile instanceof IMachineProgress)) return;

        IMachineProgress machine = (IMachineProgress) tile;

        net.minecraft.world.World world = null;
        if (tile instanceof net.minecraft.tileentity.TileEntity) {
            world = ((net.minecraft.tileentity.TileEntity) tile).getWorldObj();
        }
        long now = world != null ? world.getTotalWorldTime() : aTickTimer;

        // 0. 节流门控：未到读取周期时直接沿用缓存判定（库存数值最多延迟一个周期）。
        // 读取一次要把整个 AE2 网络的存储列表物化一遍，大网络上开销极高（spark 实测
        // 旧版每 10t×9 槽 ≈ 全服 7.8% CPU），默认 20t 读一次已足够控制机器启停。
        if (now < nextReadTick) {
            return;
        }
        // 全局对齐调度（4.5.0-fix10）：同一间隔的所有盖板落在同一 tick 读取，
        // AE2 的全网重建只付一次；整体再偏移 READ_PHASE_TICKS，避开 GT 机器 20t 配方完成的
        // 同一 tick（spark 实测：重建 44ms 与机器突发 43ms 撞在同一 tick 时该 tick 达 133ms）。
        int interval = effectiveReadInterval(now);
        if (interval < 1) interval = 1;
        long next = (now / interval + 1) * interval + READ_PHASE_TICKS;
        if (next <= now) next += interval;
        nextReadTick = next;

        // 1. 获取 IGrid（双通道）
        int x = tile.getXCoord();
        int y = tile.getYCoord();
        int z = tile.getZCoord();
        AeConnector.GridResult result = AeConnector.getGrid(data.getNetworkId(), world, x, y, z, coverSide);

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

        // 2. 一次性批量读取所有有效槽位（复用缓冲，整个网络列表只物化一次）
        readTargets.clear();
        for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
            StockMonitorCoverData.MonitorSlot slot = data.getSlot(i);
            if (slot.isEmpty() || slot.monitorTarget == null) {
                readTargets.add(null);
                continue;
            }
            readTargets.add(slot.monitorTarget);
        }
        AeStockReader.readStocks(result.grid, readTargets, readResults);

        boolean anyActive = false;
        boolean hasValidSlot = false;
        for (int i = 0; i < StockMonitorCoverData.MAX_SLOTS; i++) {
            StockMonitorCoverData.MonitorSlot slot = data.getSlot(i);
            if (slot.isEmpty() || slot.monitorTarget == null) {
                slot.lastStock = 0;
                continue;
            }
            hasValidSlot = true;
            long count = readResults[i];
            slot.lastStock = count;
            if (data.getMode()
                .shouldWork(count, slot.threshold)) {
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

        // 3. 注册到全局 CoverRegistry（供统计终端集中查看；只在读取 tick 做，注册表自带变更检测）
        try {
            ICoverable ct = coveredTile.get();
            if (ct instanceof net.minecraft.tileentity.TileEntity) {
                net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) ct;
                com.wztwzt.ae2_qof.terminal.CoverRegistry.get(te.getWorldObj())
                    .upsert(
                        te.getWorldObj().provider.dimensionId,
                        te.xCoord,
                        te.yCoord,
                        te.zCoord,
                        coverSide.ordinal(),
                        data);
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
                    .remove(
                        te.getWorldObj().provider.dimensionId,
                        te.xCoord,
                        te.yCoord,
                        te.zCoord,
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
        // 性能修复版：10 → 5，让 GUI 打开时的 5t 快速刷新可达；非读取 tick 的门控只是一次比较
        return 5;
    }

    @Override
    public boolean isDataNeededOnClient() {
        return true;
    }

    @Override
    public boolean letsEnergyIn() {
        return true;
    }

    @Override
    public boolean letsEnergyOut() {
        return true;
    }

    @Override
    public boolean letsFluidIn(Fluid fluid) {
        return true;
    }

    @Override
    public boolean letsFluidOut(Fluid fluid) {
        return true;
    }

    @Override
    public boolean letsItemsIn(int slot) {
        return true;
    }

    @Override
    public boolean letsItemsOut(int slot) {
        return true;
    }

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
            : data.getNetworkId()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byteBuf.writeShort(bytes.length);
        byteBuf.writeBytes(bytes);
        byteBuf.writeByte(
            data.getMode()
                .ordinal());

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
