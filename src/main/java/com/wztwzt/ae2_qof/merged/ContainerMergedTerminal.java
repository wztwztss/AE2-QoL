package com.wztwzt.ae2_qof.merged;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.primitives.Ints;
import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;
import com.wztwzt.ae2_qof.network.MergedTerminalBlankCountPacket;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.parts.IInterfaceTerminal;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IInterfaceViewable;
import appeng.client.gui.IGuiSub;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;
import appeng.container.interfaces.IContainerSubGui;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.InventoryAction;
import appeng.items.misc.ItemEncodedPattern;
import appeng.parts.AEBasePart;
import appeng.parts.misc.PartPatternRepeater;
import appeng.parts.p2p.PartP2PTunnel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorPlayerHand;
import appeng.util.inv.ItemSlot;

/**
 * 样板与接口二合一终端（有线方块）容器。
 * <p>
 * 移植自 ContainerInterfaceTerminal 的接口列表逻辑 + AE2Things PatternContainer 的样板编码面板。
 */
public class ContainerMergedTerminal extends AEBaseContainer implements IContainerSubGui, IMergedPatternTerminal {

    private IGuiSub guiLink;
    private int nextId;
    private boolean forceNextUpdate;
    private final Map<IInterfaceViewable, InvTracker> tracked = new HashMap<IInterfaceViewable, InvTracker>();
    private final Map<Long, InvTracker> trackedById = new HashMap<Long, InvTracker>();
    private PacketInterfaceTerminalUpdate dirty;
    private boolean isDirty;
    private IGrid grid;
    private final IInterfaceTerminal anchor;
    private boolean wasOff;

    /** 上次发送给客户端的空白样板数量（避免每 tick 重复发包） */
    private long lastSentBlankCount = -1;

    public IGrid getGrid() {
        return grid;
    }

    // ===== 样板编码面板 =====

    final PatternContainer patternContainer;
    private int mergedSlotBase = -1;

    public ContainerMergedTerminal(InventoryPlayer inv, IInterfaceTerminal anchor) {
        super(inv, anchor);
        if (anchor == null) throw new AssertionError();
        this.anchor = anchor;
        this.setOpenContext(new ContainerOpenContext(anchor));
        if (anchor instanceof TileEntity te) {
            this.getOpenContext()
                .setWorld(te.getWorldObj());
            this.getOpenContext()
                .setX(te.xCoord);
            this.getOpenContext()
                .setY(te.yCoord);
            this.getOpenContext()
                .setZ(te.zCoord);
            this.getOpenContext()
                .setSide(ForgeDirection.UNKNOWN);
        }
        if (Platform.isServer()) {
            IGridNode node = anchor.getActionableNode();
            if (node != null) this.grid = node.getGrid();
            if (this.grid != null) {
                this.dirty = this.updateList(false);
                if (this.dirty != null) this.isDirty = true;
                else this.dirty = new PacketInterfaceTerminalUpdate();
            } else {
                this.dirty = new PacketInterfaceTerminalUpdate();
            }
        }

        this.patternContainer = new PatternContainer(
            this,
            inv,
            anchor instanceof TileMergedTerminal tmt ? tmt.getPatternInv() : new AppEngInternalInventory(null, 2));
        this.patternContainer.createSlots();

        this.mergedSlotBase = this.inventorySlots.size();
        for (Slot s : this.patternContainer.getSlots()) {
            this.addSlotToContainer(s);
        }

        // 初始化时按当前模式（默认合成）摆好槽位可见性，否则刚打开时 3×3 与 4×4 槽会同时显示
        this.patternContainer.updateOrderOfOutputSlots();

        // 服务端：从 tile 快照恢复上次编辑的面板格子与模式（跨 GUI 会话保留）
        if (Platform.isServer() && anchor instanceof TileMergedTerminal tmt) {
            this.patternContainer.restoreSnapshot(tmt.getSavedGrid(), tmt.getSavedCraftingMode());
            this.syncCraftingMode = tmt.getSavedCraftingMode();
        }

        this.bindPlayerInventory(inv, 14, 3);
    }

    /** 编码模式同步（打开时恢复上次模式），DataSynchronization 自动双端同步 */
    @appeng.container.guisync.GuiSync(0)
    public boolean syncCraftingMode = true;

    @Override
    public void onContainerClosed(net.minecraft.entity.player.EntityPlayer player) {
        // 服务端：关闭 GUI 时把当前编辑内容写回 tile 快照
        if (Platform.isServer() && this.anchor instanceof TileMergedTerminal tmt) {
            this.patternContainer.saveSnapshot(tmt.getSavedGrid());
            tmt.setSavedCraftingMode(this.patternContainer.isCraftingMode());
        }
        super.onContainerClosed(player);
    }

    @Override
    public void detectAndSendChanges() {
        // 双端调用：服务端重算结果+样板回读检测；客户端本地同步回读显示
        this.patternContainer.detectAndSendChanges();
        if (Platform.isServer()) {
            this.syncCraftingMode = this.patternContainer.isCraftingMode();
        }
        if (Platform.isClient()) return;
        super.detectAndSendChanges();
        if (this.grid == null) return;
        IGridNode node = this.anchor.getActionableNode();
        if (!node.isActive()) {
            if (!this.wasOff) {
                PacketInterfaceTerminalUpdate p = new PacketInterfaceTerminalUpdate();
                p.setDisconnect();
                p.encode();
                this.wasOff = true;
                NetworkHandler.instance.sendTo(p, (EntityPlayerMP) this.getPlayerInv().player);
            }
            return;
        }
        this.wasOff = false;
        if (this.isDirty) {
            this.dirty.encode();
            NetworkHandler.instance.sendTo(this.dirty, (EntityPlayerMP) this.getPlayerInv().player);
            this.dirty = new PacketInterfaceTerminalUpdate();
            this.isDirty = false;
        } else if (this.anchor.needsUpdate() || this.forceNextUpdate) {
            this.forceNextUpdate = false;
            PacketInterfaceTerminalUpdate p = this.updateList(true);
            if (p != null) {
                p.encode();
                NetworkHandler.instance.sendTo(p, (EntityPlayerMP) this.getPlayerInv().player);
            }
        }
        try {
            long blankCount = getNetworkBlankCount();
            if (blankCount != this.lastSentBlankCount) {
                this.lastSentBlankCount = blankCount;
                com.wztwzt.ae2_qof.network.ModNetwork.CHANNEL.sendTo(
                    new MergedTerminalBlankCountPacket(blankCount),
                    (EntityPlayerMP) this.getPlayerInv().player);
            }
        } catch (Throwable ignored) {}
    }

    /** 查询网络内空白样板总数量（与其它编码终端共享的同一份库存） */
    private long getNetworkBlankCount() {
        try {
            if (this.grid == null) return 0;
            IStorageGrid storageGrid = (IStorageGrid) this.grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return 0;
            IMEMonitor<IAEItemStack> monitor = storageGrid.getItemInventory();
            ItemStack blankStack = AEApi.instance()
                .definitions()
                .materials()
                .blankPattern()
                .maybeStack(1)
                .orNull();
            if (blankStack == null) return 0;
            IAEItemStack blank = AEApi.instance()
                .storage()
                .createItemStack(blankStack);
            IAEItemStack found = monitor.getAvailableItem(blank, appeng.util.IterationCounter.fetchNewId());
            return found == null ? 0 : found.getStackSize();
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public void onSlotChange(Slot s) {
        super.onSlotChange(s);
        if (this.patternContainer != null) {
            this.patternContainer.onSlotChange(s);
        }
    }

    /**
     * 与原生 AE2 {@code ContainerPatternTerm.onCraftMatrixChanged} 一致：空实现。
     * <p>
     * 样板面板在 {@link PatternContainer#getAndUpdateOutput()} 中通过临时
     * {@code InventoryCrafting} 填充格子，若此处走 vanilla 实现（调用
     * {@code detectAndSendChanges()}）会造成
     * {@code detectAndSendChanges → getAndUpdateOutput → onCraftMatrixChanged → detectAndSendChanges} 无限递归（栈溢出）。
     */
    @Override
    public void onCraftMatrixChanged(net.minecraft.inventory.IInventory par1IInventory) {
        // 有意留空
    }

    public void scheduleUpdate() {
        this.forceNextUpdate = true;
    }

    /** 空手点击空白样板槽时，从 AE 网络扣取 1 张空白样板（镜像原生 ContainerPatternTerm.slotClick）。 */
    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
        return super.slotClick(slotId, clickedButton, mode, player);
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slotId, long id) {
        if (id >= 0) {
            InvTracker tracker = this.trackedById.get(id);
            if (tracker == null) return;
            ItemStack is = player.inventory.getItemStack();
            if (is != null && !(is.getItem() instanceof ItemEncodedPattern)) return;
            ItemStack slotStack = tracker.patterns.getStackInSlot(slotId);
            AdaptorPlayerHand playerHand = new AdaptorPlayerHand(player);
            switch (action) {
                case PICKUP_OR_SET_DOWN:
                    if (is != null) {
                        for (int i = 0; i < tracker.patterns.getSizeInventory(); i++) {
                            if (Platform.isSameItemPrecise(tracker.patterns.getStackInSlot(i), is)) return;
                        }
                    }
                    if (slotStack == null) {
                        if (is == null) return;
                        if (!tracker.patterns.isItemValidForSlot(slotId, is)) return;
                        tracker.patterns.setInventorySlotContents(slotId, playerHand.removeItems(1, null, null));
                    } else {
                        if (is != null && is.stackSize > 1) return;
                        if (is != null && !tracker.patterns.isItemValidForSlot(slotId, is)) return;
                        tracker.patterns.setInventorySlotContents(slotId, playerHand.removeItems(1, null, null));
                        playerHand.addItems(slotStack.copy());
                    }
                    this.syncIfaceSlot(tracker, id, slotId, tracker.patterns.getStackInSlot(slotId));
                    break;
                case SHIFT_CLICK:
                    InventoryAdaptor playerInv = InventoryAdaptor.getAdaptor(player.inventory, ForgeDirection.UNKNOWN);
                    ItemStack leftover = mergeToPlayerInventory(playerInv, slotStack);
                    if (leftover == null) {
                        tracker.patterns.setInventorySlotContents(slotId, null);
                        this.syncIfaceSlot(tracker, id, slotId, null);
                    }
                    break;
                case MOVE_REGION:
                    InventoryAdaptor playerInv2 = InventoryAdaptor.getAdaptor(player.inventory, ForgeDirection.UNKNOWN);
                    List<Integer> slots = new ArrayList<Integer>();
                    for (int i = 0; i < tracker.patterns.getSizeInventory(); i++) {
                        ItemStack s = tracker.patterns.getStackInSlot(i);
                        if (s == null) continue;
                        if (mergeToPlayerInventory(playerInv2, s) != null) break;
                        tracker.patterns.setInventorySlotContents(i, null);
                        slots.add(i);
                    }
                    if (!slots.isEmpty()) {
                        int[] indices = Ints.toArray(slots);
                        NBTTagList list = new NBTTagList();
                        for (int i = 0; i < slots.size(); i++) list.appendTag(new NBTTagCompound());
                        this.dirty.addOverwriteEntry(id)
                            .setItems(indices, list);
                        this.isDirty = true;
                    }
                    break;
                case CREATIVE_DUPLICATE:
                    if (player.capabilities.isCreativeMode) playerHand.addItems(is);
                    break;
                default:
                    return;
            }
            this.updateHeld(player);
        }
    }

    private void syncIfaceSlot(InvTracker tracker, long id, int slotId, ItemStack stack) {
        int[] indices = new int[] { slotId };
        NBTTagList list = new NBTTagList();
        NBTTagCompound c = new NBTTagCompound();
        if (stack != null) stack.writeToNBT(c);
        list.appendTag(c);
        tracker.markDirty();
        this.dirty.addOverwriteEntry(id)
            .setItems(indices, list);
        this.isDirty = true;
    }

    private ItemStack mergeToPlayerInventory(InventoryAdaptor adaptor, ItemStack stack) {
        if (stack == null) return null;
        for (ItemSlot slot : adaptor) {
            ItemStack s = slot.getItemStack();
            if (Platform.isSameItemPrecise(s, stack)) {
                if (s.stackSize < s.getMaxStackSize()) {
                    s.stackSize++;
                    return null;
                }
            }
        }
        return adaptor.addItems(stack);
    }

    private void fletchRepeaters(IGrid grid, Set<IGrid> grids) {
        for (IGridNode node : grid.getMachines(PartPatternRepeater.class)) {
            PartPatternRepeater repeater = (PartPatternRepeater) node.getMachine();
            if (!repeater.isProvider() || repeater.getPair() == null || !node.isActive()) continue;
            if (repeater.getPair()
                .isProvider()) continue;
            IGridNode pairNode = repeater.getPair()
                .getGridNode();
            if (pairNode == null || !pairNode.isActive()) continue;
            IGrid pairGrid = pairNode.getGrid();
            if (grids.contains(pairGrid)) continue;
            grids.add(pairGrid);
            this.fletchRepeaters(pairGrid, grids);
        }
    }

    private Set<IGrid> collectReachableGrids() {
        Set<IGrid> set = new HashSet<IGrid>();
        set.add(this.grid);
        this.fletchRepeaters(this.grid, set);
        return set;
    }

    private PacketInterfaceTerminalUpdate updateList(boolean checkContents) {
        PacketInterfaceTerminalUpdate p = null;
        Set<Class<? extends IInterfaceViewable>> supported = AEApi.instance()
            .registries()
            .interfaceTerminal()
            .getSupportedClasses();
        Set<IInterfaceViewable> seen = new HashSet<IInterfaceViewable>();
        for (IGrid g : this.collectReachableGrids()) {
            for (Class<? extends IInterfaceViewable> clazz : supported) {
                for (IGridNode node : g.getMachines(clazz)) {
                    IInterfaceViewable viewable = (IInterfaceViewable) node.getMachine();
                    if (this.tracked.containsKey(viewable)) {
                        InvTracker tracker = this.tracked.get(viewable);
                        String rawName = viewable.getRawName();
                        String suffix = viewable.getNameSuffix();
                        if (!Objects.equals(tracker.name, rawName) || !Objects.equals(tracker.suffix, suffix)) {
                            if (p == null) p = new PacketInterfaceTerminalUpdate();
                            p.addRenamedEntry(tracker.id, rawName, suffix);
                            tracker.name = rawName;
                            tracker.suffix = suffix;
                        }
                        boolean online = node.isActive();
                        if (!tracker.online && online) {
                            tracker.online = true;
                            tracker.markDirty();
                            if (p == null) p = new PacketInterfaceTerminalUpdate();
                            p.addOverwriteEntry(tracker.id)
                                .setOnline(true)
                                .setItems(new int[0], tracker.invNbt);
                        } else if (tracker.online && !online) {
                            tracker.online = false;
                            if (p == null) p = new PacketInterfaceTerminalUpdate();
                            p.addOverwriteEntry(tracker.id)
                                .setOnline(false);
                        }
                        boolean visible = getTerminalVisibility(viewable);
                        if (tracker.shouldDisplay != visible) {
                            tracker.shouldDisplay = visible;
                            if (p == null) p = new PacketInterfaceTerminalUpdate();
                            p.addOverwriteEntry(tracker.id)
                                .setTerminalVisible(visible);
                        }
                        if (tracker.rows != viewable.rows() || tracker.rowSize != viewable.rowSize()
                            || tracker.numSlots != viewable.numSlots()) {
                            tracker.rows = viewable.rows();
                            tracker.rowSize = viewable.rowSize();
                            tracker.numSlots = viewable.numSlots();
                            tracker.markDirty();
                            if (p == null) p = new PacketInterfaceTerminalUpdate();
                            p.addOverwriteEntry(tracker.id)
                                .setItems(new int[0], tracker.invNbt)
                                .setSize(tracker.rows, tracker.rowSize, tracker.numSlots);
                        }
                        int priority = viewable.getPriority();
                        if (tracker.priority != priority) {
                            tracker.priority = priority;
                            if (p == null) p = new PacketInterfaceTerminalUpdate();
                            p.addOverwriteEntry(tracker.id)
                                .setPriority(priority);
                        }
                        // 样板内容变化检测：原生 updateList 不对比内容（仅 GUI 内操作走增量推送），
                        // 外部写入（上传包/GT 自动填料/其他玩家）后列表永不更新。仅在调度刷新
                        // （checkContents=true，上传后 scheduleFullUpdate 触发）时对比，避免每 tick
                        // 对全部接口做逐槽比较的开销。
                        if (checkContents && tracker.hasContentChanged()) {
                            tracker.markDirty();
                            if (p == null) p = new PacketInterfaceTerminalUpdate();
                            p.addOverwriteEntry(tracker.id)
                                .setItems(new int[0], tracker.invNbt);
                        }
                        seen.add(viewable);
                    } else {
                        if (p == null) p = new PacketInterfaceTerminalUpdate();
                        InvTracker tracker = new InvTracker(this.nextId++, viewable, node.isActive());
                        p.addNewEntry(tracker.id, tracker.name, tracker.online)
                            .setSuffix(tracker.suffix)
                            .setLoc(tracker.x, tracker.y, tracker.z, tracker.dim, tracker.side.ordinal())
                            .setItems(tracker.rows, tracker.rowSize, tracker.numSlots, tracker.invNbt)
                            .setReps(viewable.getSelfRep(), viewable.getDisplayRep())
                            .setP2POutput(viewable instanceof PartP2PTunnel && ((PartP2PTunnel) viewable).isOutput())
                            .setSupportedStackTypes(tracker.supportedStackTypes)
                            .setPriority(tracker.priority)
                            .setTerminalVisible(tracker.shouldDisplay);
                        p.addOverwriteEntry(tracker.id)
                            .setTerminalVisible(tracker.shouldDisplay);
                        this.tracked.put(viewable, tracker);
                        this.trackedById.put(tracker.id, tracker);
                        seen.add(viewable);
                    }
                }
            }
        }
        for (Iterator<Map.Entry<IInterfaceViewable, InvTracker>> it = this.tracked.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<IInterfaceViewable, InvTracker> entry = it.next();
            if (seen.contains(entry.getKey())) continue;
            InvTracker tracker = entry.getValue();
            if (p == null) p = new PacketInterfaceTerminalUpdate();
            this.trackedById.remove(tracker.id);
            it.remove();
            p.addRemovalEntry(tracker.id);
        }
        return p;
    }

    private static boolean getTerminalVisibility(IInterfaceViewable viewable) {
        if (viewable instanceof IInterfaceHost) {
            IInterfaceHost host = (IInterfaceHost) viewable;
            return host.getInterfaceDuality()
                .getConfigManager()
                .getSetting(Settings.INTERFACE_TERMINAL) == YesNo.YES;
        }
        return viewable.shouldDisplay();
    }

    // ===== IContainerSubGui =====

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return this.getPrimaryGui() != null ? this.getPrimaryGui()
            .getIcon() : null;
    }

    @Override
    public void setGuiLink(IGuiSub guiLink) {
        this.guiLink = guiLink;
    }

    // ===== IMergedPatternTerminal =====

    @Override
    public AppEngInternalInventory getMergedInputInv() {
        return patternContainer.getMergedInputInv();
    }

    @Override
    public AppEngInternalInventory getMergedOutputInv() {
        return patternContainer.getMergedOutputInv();
    }

    @Override
    public AppEngInternalInventory getMergedResultInv() {
        return patternContainer.getMergedResultInv();
    }

    @Override
    public AppEngInternalInventory getMergedBlankInv() {
        return patternContainer.getMergedBlankInv();
    }

    @Override
    public AppEngInternalInventory getMergedEncodedInv() {
        return patternContainer.getMergedEncodedInv();
    }

    @Override
    public Slot getMergedResultSlot() {
        return patternContainer.getCraftSlot();
    }

    @Override
    public Slot getMergedBlankSlot() {
        return patternContainer.getPatternSlotIN();
    }

    @Override
    public Slot getMergedEncodedSlot() {
        return patternContainer.getPatternSlotOUT();
    }

    @Override
    public int getMergedSlotBase() {
        return mergedSlotBase;
    }

    @Override
    public boolean isMergedCraftingMode() {
        return patternContainer.isCraftingMode();
    }

    @Override
    public void setMergedCraftingMode(boolean crafting) {
        patternContainer.setCraftingMode(crafting);
        patternContainer.updateOrderOfOutputSlots();
    }

    @Override
    public boolean isMergedSubstitute() {
        return patternContainer.isSubstitute();
    }

    @Override
    public void setMergedSubstitute(boolean s) {
        patternContainer.setSubstitute(s);
    }

    @Override
    public boolean isMergedBeSubstitute() {
        return patternContainer.isBeSubstitute();
    }

    @Override
    public void setMergedBeSubstitute(boolean s) {
        patternContainer.setBeSubstitute(s);
    }

    @Override
    public boolean isMergedInverted() {
        return patternContainer.isInverted();
    }

    @Override
    public void setMergedInverted(boolean inverted) {
        patternContainer.setInverted(inverted);
        patternContainer.updateOrderOfOutputSlots();
    }

    @Override
    public int getMergedActivePage() {
        return patternContainer.getActivePage();
    }

    @Override
    public void setMergedActivePage(int page) {
        patternContainer.setActivePage(page);
        patternContainer.updateOrderOfOutputSlots();
    }

    @Override
    public String mergedEncode() {
        patternContainer.encode();
        return patternContainer.getLastMachineName();
    }

    /** 外部代码写入 provider 样板后调度一次全量列表刷新（forceNextUpdate 机制与原生 ContainerInterfaceTerminal 一致） */
    public void scheduleFullUpdate() {
        this.forceNextUpdate = true;
    }

    @Override
    public String mergedEncodeRecipeMap() {
        return patternContainer.getLastRecipeMap();
    }

    @Override
    public boolean mergedEncodeNeedsMapping() {
        return patternContainer.isLastNeedsMapping();
    }

    @Override
    public String mergedLastMachineName() {
        return patternContainer.getLastMachineName();
    }

    @Override
    public void mergedClear() {
        patternContainer.clear();
    }

    @Override
    public void mergedDoubleStacks(int flags) {
        patternContainer.doubleStacks(flags);
    }

    @Override
    public void mergedFill(ItemStack[] inputs, ItemStack[] outputs, boolean crafting, int[] cells, String recipeMap) {
        patternContainer.fill(inputs, outputs, crafting, cells, recipeMap);
    }

    @Override
    public void mergedSetStackSize(int slotNumber, int newSize) {
        if (!Platform.isServer()) return;
        if (slotNumber < 0 || slotNumber >= this.inventorySlots.size()) return;
        patternContainer.setStackSize(this.inventorySlots.get(slotNumber), newSize);
    }

    @Override
    public void mergedRecomputeResult() {
        patternContainer.getAndUpdateOutput();
    }

    @Override
    public void mergedSwapOutputs() {
        AppEngInternalInventory out = getMergedOutputInv();
        ItemStack[] items = new ItemStack[OUTPUT_MAX];
        int count = 0;
        for (int j = 0; j < OUTPUT_MAX; j++) {
            ItemStack s = out.getStackInSlot(j);
            if (s != null) items[count++] = s;
        }
        if (count < 2) return;
        ItemStack first = items[0];
        for (int i = 0; i < count - 1; i++) items[i] = items[i + 1];
        items[count - 1] = first;
        int idx = 0;
        for (int j = 0; j < OUTPUT_MAX; j++) {
            ItemStack s = out.getStackInSlot(j);
            if (s != null) out.setInventorySlotContents(j, items[idx++]);
        }
        patternContainer.getAndUpdateOutput();
    }

    // ===== InvTracker =====

    private static final class InvTracker {

        private final long id;
        private boolean shouldDisplay;
        private String name;
        private String suffix;
        private final IInventory patterns;
        private int rows;
        private int rowSize;
        private int numSlots;
        private final int x, y, z, dim;
        private int priority;
        private final ForgeDirection side;
        private boolean online;
        private final IAEStackType<?>[] supportedStackTypes;
        private NBTTagList invNbt;
        private final ItemStack[] slotCache;

        InvTracker(long id, IInterfaceViewable viewable, boolean online) {
            this.id = id;
            this.shouldDisplay = getTerminalVisibility(viewable);
            this.name = viewable.getRawName();
            this.suffix = viewable.getNameSuffix();
            this.patterns = viewable.getPatterns();
            this.rowSize = viewable.rowSize();
            this.rows = viewable.rows();
            this.numSlots = viewable.numSlots();
            DimensionalCoord loc = viewable.getLocation();
            this.x = loc.x;
            this.y = loc.y;
            this.z = loc.z;
            this.dim = loc.getDimension();
            this.side = viewable instanceof AEBasePart ? ((AEBasePart) viewable).getSide() : ForgeDirection.UNKNOWN;
            this.online = online;
            this.supportedStackTypes = viewable.getSupportedStackTypes();
            this.priority = viewable.getPriority();
            this.invNbt = new NBTTagList();
            this.slotCache = new ItemStack[Math.max(1, viewable.numSlots())];
            this.markDirty();
        }

        void markDirty() {
            this.invNbt = new NBTTagList();
            for (int i = 0; i < this.numSlots; i++) {
                ItemStack stack = this.patterns.getStackInSlot(i);
                if (i < this.slotCache.length) {
                    this.slotCache[i] = stack == null ? null : stack.copy();
                }
                this.invNbt.appendTag(stack != null ? stack.writeToNBT(new NBTTagCompound()) : new NBTTagCompound());
            }
        }

        /** 对比当前样板槽与上次快照，检测外部写入导致的内容变化 */
        boolean hasContentChanged() {
            for (int i = 0; i < this.numSlots && i < this.slotCache.length; i++) {
                ItemStack cur = this.patterns.getStackInSlot(i);
                ItemStack old = this.slotCache[i];
                boolean same = cur == old
                    || (cur != null && old != null && net.minecraft.item.ItemStack.areItemStacksEqual(cur, old));
                if (!same) return true;
            }
            return false;
        }
    }
}
