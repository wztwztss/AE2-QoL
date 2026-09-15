package com.wztwzt.ae2_qof.cover.stockmonitor;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;

/**
 * 库存检测覆盖板数据（多槽位版 fix14）。
 * 最多 9 个检测槽位，每槽独立设置物品/流体与阈值；模式（低于/高于）全局共用；
 * 判定为 OR 逻辑：任意一槽满足条件即启动机器。空槽（未放置物品）不参与检测。
 */
public class StockMonitorCoverData {

    public static final int MAX_SLOTS = 9;

    public static final String NBT_NETWORK_ID = "NetworkId";
    public static final String NBT_MODE = "Mode";
    public static final String NBT_SLOTS = "Slots";

    // 旧版单槽 NBT 键（向后兼容）
    private static final String NBT_OLD_THRESHOLD = "Threshold";
    private static final String NBT_OLD_PHANTOM = "PhantomItem";
    private static final String NBT_OLD_TARGET = "MonitorTarget";

    /** 单个检测槽位 */
    public static class MonitorSlot {
        public ItemStack phantomStack = null;
        public IAEStack<?> monitorTarget = null;
        public long threshold = 0;
        /** 运行态：上次检测到的该槽位库存数量，不持久化 */
        public long lastStock = 0;

        public boolean isEmpty() {
            return phantomStack == null || phantomStack.getItem() == null;
        }

        public void copyFrom(MonitorSlot other) {
            this.phantomStack = other.phantomStack == null ? null : other.phantomStack.copy();
            this.monitorTarget = other.monitorTarget;
            this.threshold = other.threshold;
            this.lastStock = other.lastStock;
        }
    }

    private String networkId = "";
    private ThresholdMode mode = ThresholdMode.BELOW_THRESHOLD_RUN;
    private final MonitorSlot[] slots = new MonitorSlot[MAX_SLOTS];

    private int lastChannel = 0;
    private boolean lastShouldWork = false;

    public StockMonitorCoverData() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots[i] = new MonitorSlot();
        }
    }

    // ===== Network =====

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId == null ? "" : networkId;
    }

    public boolean isBound() {
        return networkId != null && !networkId.isEmpty();
    }

    // ===== Slots =====

    public MonitorSlot getSlot(int index) {
        if (index < 0 || index >= MAX_SLOTS) return slots[0];
        return slots[index];
    }

    public MonitorSlot[] getSlots() {
        return slots;
    }

    /** 是否有至少一个有效检测槽位 */
    public boolean hasAnyMonitorTarget() {
        for (MonitorSlot slot : slots) {
            if (slot.monitorTarget != null) return true;
        }
        return false;
    }

    /** 返回有效（非空）槽位数量 */
    public int getActiveSlotCount() {
        int count = 0;
        for (MonitorSlot slot : slots) {
            if (!slot.isEmpty()) count++;
        }
        return count;
    }

    // ===== Mode (全局) =====

    public ThresholdMode getMode() {
        return mode;
    }

    public void setMode(ThresholdMode mode) {
        this.mode = mode == null ? ThresholdMode.BELOW_THRESHOLD_RUN : mode;
    }

    // ===== Runtime State =====

    public int getLastChannel() {
        return lastChannel;
    }

    public void setLastChannel(int lastChannel) {
        this.lastChannel = lastChannel;
    }

    public boolean isLastShouldWork() {
        return lastShouldWork;
    }

    public void setLastShouldWork(boolean lastShouldWork) {
        this.lastShouldWork = lastShouldWork;
    }

    // ===== Phantom → Target 识别（静态工具）=====

    /**
     * 根据 phantomStack 更新 monitorTarget。
     * 流体容器 → IAEFluidStack；流体物品 → IAEFluidStack；普通物品 → IAEItemStack；空 → null。
     */
    public static IAEStack<?> resolveTarget(ItemStack phantomStack) {
        if (phantomStack == null || phantomStack.getItem() == null) {
            return null;
        }
        FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(phantomStack);
        if (fluid == null) {
            fluid = tryRecognizeFluidItem(phantomStack);
        }
        if (fluid != null) {
            return AEApi.instance().storage().createFluidStack(fluid);
        }
        IAEItemStack itemStack = AEApi.instance().storage().createItemStack(phantomStack);
        itemStack.setStackSize(1);
        return itemStack;
    }

    private static FluidStack tryRecognizeFluidItem(ItemStack stack) {
        try {
            if (stack.getItem() instanceof net.minecraftforge.fluids.IFluidContainerItem) {
                FluidStack fs = ((net.minecraftforge.fluids.IFluidContainerItem) stack.getItem())
                    .getFluid(stack);
                if (fs != null && fs.getFluid() != null) return fs;
            }
            String cls = stack.getItem().getClass().getName();
            if (cls.equals("com.glodblock.github.common.item.ItemFluidPacket")) {
                return readPacketFluid(stack);
            }
            if (cls.equals("com.glodblock.github.common.item.ItemFluidDrop")) {
                return readFluidDrop(stack);
            }
            if (cls.equals("gregtech.common.items.ItemFluidDisplay")) {
                net.minecraftforge.fluids.Fluid fluid = net.minecraftforge.fluids.FluidRegistry
                    .getFluid(stack.getItemDamage());
                if (fluid != null) {
                    return new FluidStack(fluid, FluidContainerRegistry.BUCKET_VOLUME);
                }
            }
            FluidStack nbtFluid = readGenericNbtFluid(stack);
            if (nbtFluid != null) return nbtFluid;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static FluidStack readGenericNbtFluid(ItemStack stack) {
        if (!stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey("FluidStack")) {
            NBTTagCompound fsTag = tag.getCompoundTag("FluidStack");
            if (fsTag != null && !fsTag.hasNoTags()) {
                FluidStack fs = FluidStack.loadFluidStackFromNBT(fsTag);
                if (fs != null && fs.getFluid() != null) return fs;
            }
        }
        if (tag.hasKey("Fluid")) {
            String fluidName = tag.getString("Fluid");
            if (fluidName != null && !fluidName.isEmpty()) {
                net.minecraftforge.fluids.Fluid fluid = net.minecraftforge.fluids.FluidRegistry
                    .getFluid(fluidName);
                if (fluid != null) {
                    int amount = tag.hasKey("Amount") ? tag.getInteger("Amount") : stack.stackSize;
                    if (amount <= 0) amount = 1;
                    return new FluidStack(fluid, amount);
                }
            }
        }
        return null;
    }

    private static FluidStack readPacketFluid(ItemStack stack) {
        try {
            if (!stack.hasTagCompound()) return null;
            NBTTagCompound tag = stack.getTagCompound().getCompoundTag("FluidStack");
            if (tag == null || tag.hasNoTags()) return null;
            return FluidStack.loadFluidStackFromNBT(tag);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static FluidStack readFluidDrop(ItemStack stack) {
        try {
            Class<?> cls = Class.forName("com.glodblock.github.common.item.ItemFluidDrop");
            java.lang.reflect.Method m = cls.getMethod("getFluidStack", ItemStack.class);
            return (FluidStack) m.invoke(null, stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ===== NBT =====

    public void saveToNbt(NBTTagCompound nbt) {
        nbt.setString(NBT_NETWORK_ID, networkId);
        nbt.setByte(NBT_MODE, (byte) mode.ordinal());

        NBTTagCompound slotsTag = new NBTTagCompound();
        for (int i = 0; i < MAX_SLOTS; i++) {
            MonitorSlot slot = slots[i];
            if (slot.isEmpty()) continue;
            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setLong("Threshold", slot.threshold);
            if (slot.phantomStack != null) {
                NBTTagCompound phantomTag = new NBTTagCompound();
                slot.phantomStack.writeToNBT(phantomTag);
                slotTag.setTag("Phantom", phantomTag);
            }
            if (slot.monitorTarget != null) {
                NBTTagCompound targetTag = new NBTTagCompound();
                Platform.writeStackNBT(slot.monitorTarget, targetTag);
                slotTag.setTag("Target", targetTag);
            }
            slotsTag.setTag(String.valueOf(i), slotTag);
        }
        nbt.setTag(NBT_SLOTS, slotsTag);
    }

    public void readFromNbt(NBTTagCompound nbt) {
        networkId = nbt.getString(NBT_NETWORK_ID);
        if (networkId == null) networkId = "";
        mode = ThresholdMode.fromOrdinal(nbt.getByte(NBT_MODE));

        // 重置所有槽位
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots[i] = new MonitorSlot();
        }

        if (nbt.hasKey(NBT_SLOTS)) {
            // 新版多槽位格式
            NBTTagCompound slotsTag = nbt.getCompoundTag(NBT_SLOTS);
            for (int i = 0; i < MAX_SLOTS; i++) {
                String key = String.valueOf(i);
                if (!slotsTag.hasKey(key)) continue;
                NBTTagCompound slotTag = slotsTag.getCompoundTag(key);
                MonitorSlot slot = slots[i];
                slot.threshold = slotTag.getLong("Threshold");
                if (slotTag.hasKey("Phantom")) {
                    slot.phantomStack = ItemStack.loadItemStackFromNBT(slotTag.getCompoundTag("Phantom"));
                }
                if (slotTag.hasKey("Target")) {
                    slot.monitorTarget = Platform.readStackNBT(slotTag.getCompoundTag("Target"));
                }
                // phantom 存在时一律以重建结果为准
                if (slot.phantomStack != null) {
                    slot.monitorTarget = resolveTarget(slot.phantomStack);
                }
            }
        } else {
            // 旧版单槽格式 → 读入 slot[0]
            MonitorSlot slot = slots[0];
            slot.threshold = nbt.getLong(NBT_OLD_THRESHOLD);
            if (nbt.hasKey(NBT_OLD_PHANTOM)) {
                slot.phantomStack = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(NBT_OLD_PHANTOM));
            }
            if (nbt.hasKey(NBT_OLD_TARGET)) {
                slot.monitorTarget = Platform.readStackNBT(nbt.getCompoundTag(NBT_OLD_TARGET));
            }
            if (slot.phantomStack != null) {
                slot.monitorTarget = resolveTarget(slot.phantomStack);
            }
        }
    }

    public void clear() {
        networkId = "";
        mode = ThresholdMode.BELOW_THRESHOLD_RUN;
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots[i] = new MonitorSlot();
        }
        lastChannel = 0;
        lastShouldWork = false;
    }
}
