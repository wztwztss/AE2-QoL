package com.wztwzt.ae2_qof.merged;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.wztwzt.ae2_qof.common.RecipeMapNameConfig;
import com.wztwzt.ae2_qof.merged.slot.SlotPatternFake;
import com.wztwzt.ae2_qof.util.RecipeMapDetector;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotRestrictedInput;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * 样板编码小组件，移植自 AE2Things PatternContainer（原生 4×4 网格布局）。
 * <p>
 * 槽位坐标与 AE 原生样板终端一致（绘制时 y 方向额外偏移 +68）：
 * 3×3 合成格、4×4×2 页扩展输入/输出、空白/已编码样板、合成结果槽。
 */
public class PatternContainer implements IOptionalSlotHost {

    private static final int CRAFTING_GRID = 3;
    private static final int CRAFTING_GRID_PAGES = 2;
    private static final int CRAFTING_GRID_WIDTH = 4;
    private static final int CRAFTING_GRID_HEIGHT = 4;
    private static final int CRAFTING_GRID_SLOTS = CRAFTING_GRID_WIDTH * CRAFTING_GRID_HEIGHT;

    private final AppEngInternalInventory craftingInv = new AppEngInternalInventory(null, 9);
    private final AppEngInternalInventory craftingExInv = new AppEngInternalInventory(
        null,
        CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES);
    private final AppEngInternalInventory outputExInv = new AppEngInternalInventory(
        null,
        CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES);
    private final AppEngInternalInventory patternInv;
    private final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);

    private SlotFakeCraftingMatrix[] craftingSlots;
    private SlotPatternFake[] craftingExSlots;
    private SlotPatternFake[] outputExSlots;
    private SlotRestrictedInput patternSlotIN;
    private SlotRestrictedInput patternSlotOUT;
    private SlotFake craftSlot;

    private final List<Slot> allSlots = new ArrayList<>();
    private final Container container;
    private final InventoryPlayer playerInv;

    private boolean craftingMode = true;
    private boolean substitute = false;
    private boolean beSubstitute = false;
    private boolean inverted = false;
    private int activePage = 0;

    private final ItemStack[] recipeCache = new ItemStack[10];
    private boolean updatingOutput;

    /** 最近一次编码解析出的机器搜索词（玩家映射中文名，未映射时退化为 recipeMap id），供终端显示与自动搜索 */
    private String lastMachineName;

    /** 最近一次编码写入的 apu:recipeMap（合成配方为 "crafting"，处理配方为 GT 配方池 id，可能为 null） */
    private String lastRecipeMap;

    /** 最近一次编码是否为处理配方且 recipeMap 无中文映射（需弹出映射页） */
    private boolean lastNeedsMapping;

    /** NEI 填充时客户端提供的配方池 id（处理配方），编码检测失败时兜底使用 */
    private String pendingRecipeMap;

    public PatternContainer(Container container, InventoryPlayer playerInv, AppEngInternalInventory patternInv) {
        this.container = container;
        this.playerInv = playerInv;
        this.patternInv = patternInv;
    }

    public void createSlots() {
        allSlots.clear();

        patternSlotIN = new SlotRestrictedInput(
            SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
            patternInv,
            0,
            220,
            31,
            playerInv);
        patternSlotOUT = new SlotRestrictedInput(
            SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
            patternInv,
            1,
            220,
            31 + 43,
            playerInv);
        patternSlotOUT.setStackLimit(1);
        allSlots.add(patternSlotIN);
        allSlots.add(patternSlotOUT);

        craftingExSlots = new SlotPatternFake[CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES];
        outputExSlots = new SlotPatternFake[CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES];
        for (int page = 0; page < CRAFTING_GRID_PAGES; page++) {
            for (int y = 0; y < CRAFTING_GRID_HEIGHT; y++) {
                for (int x = 0; x < CRAFTING_GRID_WIDTH; x++) {
                    craftingExSlots[x + y * CRAFTING_GRID_WIDTH + page * CRAFTING_GRID_SLOTS] = new SlotPatternFake(
                        craftingExInv,
                        this,
                        x + y * CRAFTING_GRID_WIDTH + page * CRAFTING_GRID_SLOTS,
                        224,
                        -59,
                        x,
                        y,
                        x + 4);
                    allSlots.add(craftingExSlots[x + y * CRAFTING_GRID_WIDTH + page * CRAFTING_GRID_SLOTS]);
                }
            }
            for (int x = 0; x < CRAFTING_GRID_WIDTH; x++) {
                for (int y = 0; y < CRAFTING_GRID_HEIGHT; y++) {
                    outputExSlots[x * CRAFTING_GRID_HEIGHT + y + page * CRAFTING_GRID_SLOTS] = new SlotPatternFake(
                        outputExInv,
                        this,
                        x * CRAFTING_GRID_HEIGHT + y + page * CRAFTING_GRID_SLOTS,
                        224 + 97,
                        -59,
                        -x,
                        y,
                        x);
                    allSlots.add(outputExSlots[x * CRAFTING_GRID_HEIGHT + y + page * CRAFTING_GRID_SLOTS]);
                }
            }
        }

        craftSlot = new SlotFake(cOut, 0, 224 + 92, -32);
        allSlots.add(craftSlot);

        craftingSlots = new SlotFakeCraftingMatrix[CRAFTING_GRID * CRAFTING_GRID];
        for (int y = 0; y < CRAFTING_GRID; y++) {
            for (int x = 0; x < CRAFTING_GRID; x++) {
                int idx = x + y * CRAFTING_GRID;
                craftingSlots[idx] = new SlotFakeCraftingMatrix(craftingInv, idx, 224 + x * 18, -50 + y * 18);
                allSlots.add(craftingSlots[idx]);
            }
        }
    }

    public List<Slot> getSlots() {
        return allSlots;
    }

    // ===== 模式/页/反转切换：显示/隐藏槽 =====

    public void updateOrderOfOutputSlots() {
        if (craftingMode) {
            craftSlot.xDisplayPosition = craftSlot.getX();
            for (SlotFakeCraftingMatrix s : craftingSlots) {
                s.xDisplayPosition = s.getX();
            }
            for (SlotPatternFake s : outputExSlots) s.setHidden(true);
            for (SlotPatternFake s : craftingExSlots) s.setHidden(true);
        } else {
            craftSlot.xDisplayPosition = -9000;
            for (SlotFakeCraftingMatrix s : craftingSlots) {
                s.xDisplayPosition = -9000;
            }
            for (SlotPatternFake s : outputExSlots) s.setHidden(false);
            for (SlotPatternFake s : craftingExSlots) s.setHidden(false);
            offsetSlots();
        }
    }

    private void offsetSlots() {
        for (int page = 0; page < CRAFTING_GRID_PAGES; page++) {
            for (int y = 0; y < CRAFTING_GRID_HEIGHT; y++) {
                for (int x = 0; x < CRAFTING_GRID_WIDTH; x++) {
                    craftingExSlots[x + y * CRAFTING_GRID_WIDTH + page * CRAFTING_GRID_SLOTS]
                        .setHidden(page != activePage || x > 0 && inverted);
                    outputExSlots[x * CRAFTING_GRID_HEIGHT + y + page * CRAFTING_GRID_SLOTS]
                        .setHidden(page != activePage || x > 0 && !inverted);
                }
            }
        }
    }

    // ===== 合成结果计算 =====

    public ItemStack getAndUpdateOutput() {
        if (updatingOutput) return null;
        updatingOutput = true;
        try {
            return doGetAndUpdateOutput();
        } finally {
            updatingOutput = false;
        }
    }

    private ItemStack doGetAndUpdateOutput() {
        if (!craftingMode) return null;
        boolean sameRecipe = true;
        for (int i = 0; i < craftingInv.getSizeInventory(); i++) {
            if (recipeCache[i] == null && craftingInv.getStackInSlot(i) == null) continue;
            if (!Platform.isSameItemPrecise(recipeCache[i], craftingInv.getStackInSlot(i))) {
                sameRecipe = false;
                break;
            }
        }
        if (!sameRecipe) {
            final InventoryCrafting ic = new InventoryCrafting(container, 3, 3);
            for (int x = 0; x < ic.getSizeInventory(); x++) {
                ic.setInventorySlotContents(x, craftingInv.getStackInSlot(x));
            }
            final ItemStack is = CraftingManager.getInstance()
                .findMatchingRecipe(ic, playerInv.player.worldObj);
            cOut.setInventorySlotContents(0, is);
            for (int i = 0; i < craftingInv.getSizeInventory(); i++) {
                recipeCache[i] = craftingInv.getStackInSlot(i);
            }
            recipeCache[9] = is;
            return is;
        } else if (recipeCache[9] != null) {
            return recipeCache[9];
        }
        return null;
    }

    // ===== 编码 =====

    public void encode() {
        encodeItemPattern();
    }

    private void encodeItemPattern() {
        ItemStack output = patternSlotOUT.getStack();
        final ItemStack[] in = getInputs();
        final ItemStack[] out = getOutputs();

        if (in == null || out == null) return;
        if (output != null && notPattern(output)) return;
        if (output == null) {
            ItemStack blank = patternSlotIN.getStack();
            if (blank != null && isBlankPattern(blank)) {
                blank.stackSize--;
                if (blank.stackSize == 0) {
                    patternSlotIN.putStack(null);
                }
            } else if (blank == null) {
                // 空白槽为空：直接从网络扣 1 张空白样板（共享数量，编码后所有终端显示同步减一）
                if (!consumeBlankFromNetwork()) return;
            } else {
                return;
            }
        }

        // 按模式产出对应样板物品（与 GTNH 原生 ContainerPatternTerm.encode 一致）：
        // 合成 → 普通样板；处理 → 终极样板（GT 机器仅识别终极样板）。
        if (craftingMode) {
            output = AEApi.instance()
                .definitions()
                .items()
                .encodedPattern()
                .maybeStack(1)
                .orNull();
        } else {
            output = AEApi.instance()
                .definitions()
                .items()
                .encodedUltimatePattern()
                .maybeStack(1)
                .orNull();
        }
        if (output == null) return;

        final NBTTagCompound encodedValue = new NBTTagCompound();
        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();
        for (final ItemStack i : in) tagIn.appendTag(createItemTag(i));
        for (final ItemStack i : out) tagOut.appendTag(createItemTag(i));
        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", craftingMode);
        encodedValue.setBoolean("substitute", substitute);
        encodedValue.setBoolean("beSubstitute", beSubstitute);
        output.setTagCompound(encodedValue);
        stampAuthor(output);

        // GT 配方池反查（仅处理配方）：合成配方统一写 apu:recipeMap="crafting"，
        // 避免把工作台配方随机反查成无关的 GT 机器配方池。
        applyRecipeMapMeta(null);
        if (craftingMode) {
            output.getTagCompound()
                .setString("apu:recipeMap", "crafting");
            lastMachineName = "合成";
            lastRecipeMap = "crafting";
        } else {
            try {
                String playerKey = playerInv.player.getUniqueID()
                    .toString();
                String recipeMap = RecipeMapDetector.detectRecipeMap(in, out, playerKey);
                if (recipeMap == null || recipeMap.isEmpty()) {
                    // 兜底：使用 NEI 填充时客户端直接提供的配方池 id（GT 处理配方）
                    recipeMap = pendingRecipeMap;
                }
                if (recipeMap != null && !recipeMap.isEmpty()) {
                    output.getTagCompound()
                        .setString("apu:recipeMap", recipeMap);
                    applyRecipeMapMeta(recipeMap);
                }
            } catch (Throwable t) {
                System.out.println("[APU] encode machine name error: " + t.getMessage());
            }
        }

        patternSlotOUT.putStack(output);
    }

    private ItemStack stampAuthor(ItemStack patternStack) {
        if (patternStack.stackTagCompound == null) {
            patternStack.stackTagCompound = new NBTTagCompound();
        }
        patternStack.stackTagCompound.setString("author", playerInv.player.getCommandSenderName());
        return patternStack;
    }

    private NBTTagCompound createItemTag(final ItemStack i) {
        if (i == null) return new NBTTagCompound();
        // 流体物品：用 AEFluidStack.toNBTGeneric() 写入，GTNH AE2 的 readStackNBT 才能正确解析
        if (isFluidItem(i)) {
            FluidStack fs = getFluidFromItem(i);
            if (fs != null) {
                // GT ItemFluidDisplay：stackSize 乘以 mFluidDisplayAmount 得到总量
                // （NEI 填充时 stackSize 表示流体单位数，mFluidDisplayAmount 是每单位量）
                if (isGTFluidDisplayItem(i)) {
                    fs.amount = fs.amount * i.stackSize;
                }
                appeng.util.item.AEFluidStack aeFluid = appeng.util.item.AEFluidStack.create(fs);
                if (aeFluid != null) {
                    return aeFluid.toNBTGeneric();
                }
            }
        }
        // 普通物品：用 AEItemStack.toNBTGeneric() 写入，与 AE2 原生编码终端一致
        return appeng.util.item.AEItemStack.create(i).toNBTGeneric();
    }

    private boolean notPattern(final ItemStack output) {
        if (output == null) return true;
        final var definitions = AEApi.instance()
            .definitions();
        boolean isPattern = definitions.items()
            .encodedPattern()
            .isSameAs(output);
        isPattern |= definitions.items()
            .encodedUltimatePattern()
            .isSameAs(output);
        isPattern |= definitions.materials()
            .blankPattern()
            .isSameAs(output);
        return !isPattern;
    }

    private boolean isBlankPattern(final ItemStack stack) {
        if (stack == null) return false;
        return AEApi.instance()
            .definitions()
            .materials()
            .blankPattern()
            .isSameAs(stack);
    }

    /** 空白槽为空时，直接从网络扣 1 张空白样板（不放入槽内，网络数量即共享显示） */
    private boolean consumeBlankFromNetwork() {
        try {
            if (!(this.container instanceof ContainerMergedTerminal cmt)) return false;
            IGrid grid = cmt.getGrid();
            if (grid == null) return false;
            IEnergySource energy = (appeng.api.networking.energy.IEnergyGrid) grid
                .getCache(appeng.api.networking.energy.IEnergyGrid.class);
            if (energy == null) return false;
            IStorageGrid storageGrid = (IStorageGrid) grid.getCache(IStorageGrid.class);
            appeng.api.storage.IMEMonitor<IAEItemStack> monitor = storageGrid.getItemInventory();
            ItemStack blankStack = AEApi.instance()
                .definitions()
                .materials()
                .blankPattern()
                .maybeStack(1)
                .orNull();
            if (blankStack == null) return false;
            IAEItemStack blank = AEApi.instance()
                .storage()
                .createItemStack(blankStack);
            IAEItemStack extracted = Platform.poweredExtraction(energy, monitor, blank, cmt.getActionSource());
            return extracted != null && extracted.getStackSize() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private ItemStack[] getInputs() {
        final ArrayList<ItemStack> input = new ArrayList<>();
        if (craftingMode) {
            for (SlotFake craftingSlot : craftingSlots) input.add(craftingSlot.getStack());
        } else {
            for (SlotFake craftingSlot : craftingExSlots) input.add(craftingSlot.getStack());
        }
        if (input.stream()
            .anyMatch(java.util.Objects::nonNull)) {
            return input.toArray(new ItemStack[0]);
        }
        return null;
    }

    private ItemStack[] getOutputs() {
        final ArrayList<ItemStack> output = new ArrayList<>();
        if (craftingMode) {
            final ItemStack out = getAndUpdateOutput();
            if (out != null && out.stackSize > 0) return new ItemStack[] { out };
        } else {
            for (final SlotFake outputSlot : outputExSlots) output.add(outputSlot.getStack());
            if (output.stream()
                .anyMatch(java.util.Objects::nonNull)) {
                return output.toArray(new ItemStack[0]);
            }
        }
        return null;
    }

    // ===== 清空/倍增 =====

    public void clear() {
        for (final Slot s : craftingExSlots) s.putStack(null);
        for (final Slot s : outputExSlots) s.putStack(null);
        for (final Slot s : craftingSlots) s.putStack(null);
        getAndUpdateOutput();
    }

    public void doubleStacks() {
        doubleStacks(0);
    }

    /**
     * 倍增/倍除输入与输出格数量（仅处理模式）。位标志与原生 AE2 编码终端一致：
     * bit0=1 → ×8 否则 ×2；bit1=2 → 除法（÷）否则乘法。
     *
     * @param flags 0=×2，1=×8，2=÷2，3=÷8
     */
    public void doubleStacks(int flags) {
        if (craftingMode) return;
        boolean ctrl = (flags & 1) != 0;
        boolean divide = (flags & 2) != 0;
        int factor = ctrl ? 8 : 2;
        for (final Slot s : craftingExSlots) {
            ItemStack st = s.getStack();
            if (st != null) {
                if (isFluidItem(st)) {
                    multiplyFluidItem(st, factor, divide);
                } else {
                    st.stackSize = divide ? Math.max(1, st.stackSize / factor)
                        : Math.min(st.stackSize * factor, st.getMaxStackSize());
                }
            }
        }
        for (final Slot s : outputExSlots) {
            ItemStack st = s.getStack();
            if (st != null) {
                if (isFluidItem(st)) {
                    multiplyFluidItem(st, factor, divide);
                } else {
                    st.stackSize = divide ? Math.max(1, st.stackSize / factor)
                        : Math.min(st.stackSize * factor, st.getMaxStackSize());
                }
            }
        }
    }

    /** 中键数量编辑：把指定面板槽的物品数量设为 newSize（0 表示清空该槽）。 */
    public boolean setStackSize(Slot slot, int newSize) {
        if (slot == null || newSize < 0) return false;
        boolean panel = false;
        for (Slot s : craftingSlots) {
            if (s == slot) {
                panel = true;
                break;
            }
        }
        if (!panel) {
            for (Slot s : craftingExSlots) {
                if (s == slot) {
                    panel = true;
                    break;
                }
            }
        }
        if (!panel) {
            for (Slot s : outputExSlots) {
                if (s == slot) {
                    panel = true;
                    break;
                }
            }
        }
        if (!panel) return false;
        // 输出格数量由配方决定，编辑后 getAndUpdateOutput 重算会覆盖/清空，禁止修改
        if (isOutputSlot(slot)) return false;
        ItemStack st = slot.getStack();
        if (st == null) return false;
        if (newSize == 0) {
            slot.putStack(null);
            slot.onSlotChanged();
            getAndUpdateOutput();
            return true;
        }
        if (isFluidItem(st)) {
            setFluidItemAmount(st, newSize);
        } else {
            st.stackSize = newSize;
        }
        slot.onSlotChanged();
        getAndUpdateOutput();
        return true;
    }

    /**
     * 填充网格。
     *
     * @param inputs    输入物品列表（合成模式对应 3×3 格子，处理模式对应扩展槽）
     * @param outputs   输出物品列表
     * @param mode      true=合成模式，false=处理模式
     * @param cells     合成模式下每个输入对应的 3×3 格子索引（0-8），null 或越界时退化为顺序填充；
     *                  处理配方传 null（扩展槽按顺序填充）
     * @param recipeMap NEI 填充时客户端识别的配方池 id（处理配方），用于编码兜底与映射判定，可传 null
     */
    public void fill(ItemStack[] inputs, ItemStack[] outputs, boolean mode, int[] cells, String recipeMap) {
        this.craftingMode = mode;
        this.pendingRecipeMap = recipeMap;
        applyRecipeMapMeta(recipeMap);
        for (int i = 0; i < CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES; i++) {
            ItemStack s = (inputs != null && i < inputs.length) ? inputs[i] : null;
            if (i < 9) craftingInv.setInventorySlotContents(i, null);
            craftingExSlots[i].putStack(s == null ? null : s.copy());
        }
        if (mode) {
            if (cells != null && inputs != null) {
                for (int i = 0; i < inputs.length && i < cells.length; i++) {
                    int c = cells[i];
                    if (c >= 0 && c < CRAFTING_GRID_SLOTS && inputs[i] != null) {
                        craftingInv.setInventorySlotContents(c, inputs[i].copy());
                    }
                }
            } else if (inputs != null) {
                for (int i = 0; i < CRAFTING_GRID_SLOTS && i < inputs.length; i++) {
                    craftingInv.setInventorySlotContents(i, inputs[i] == null ? null : inputs[i].copy());
                }
            }
        }
        for (int j = 0; j < CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES; j++) {
            ItemStack s = (outputs != null && j < outputs.length) ? outputs[j] : null;
            outputExSlots[j].putStack(s == null ? null : s.copy());
        }
        updateOrderOfOutputSlots();
        getAndUpdateOutput();
    }

    // ===== 槽变化时同步 =====

    public void onSlotChange(Slot s) {
        // 同步由 detectAndSendChanges 每 tick 处理
    }

    // ===== 编辑快照（跨 GUI 会话保留） =====

    /**
     * 从 tile 快照恢复面板格子与模式（仅服务端容器构造时调用）。
     * 布局：0-8 合成3×3 | 9-40 扩展输入 | 41-72 输出。
     */
    public void restoreSnapshot(AppEngInternalInventory src, boolean mode) {
        for (int i = 0; i < 9; i++) {
            craftingInv.setInventorySlotContents(i, copyOf(src, i));
        }
        int slots = CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES;
        for (int i = 0; i < slots; i++) {
            craftingExSlots[i].putStack(copyOf(src, 9 + i));
            outputExSlots[i].putStack(copyOf(src, 41 + i));
        }
        setCraftingMode(mode);
        updateOrderOfOutputSlots();
    }

    /** 关闭 GUI 时把当前格子内容写回 tile 快照（仅服务端调用） */
    public void saveSnapshot(AppEngInternalInventory dst) {
        for (int i = 0; i < 9; i++) {
            dst.setInventorySlotContents(i, clone(craftingInv.getStackInSlot(i)));
        }
        int slots = CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES;
        for (int i = 0; i < slots; i++) {
            dst.setInventorySlotContents(9 + i, clone(craftingExSlots[i].getStack()));
            dst.setInventorySlotContents(41 + i, clone(outputExSlots[i].getStack()));
        }
    }

    private static ItemStack copyOf(AppEngInternalInventory src, int idx) {
        if (idx >= src.getSizeInventory()) return null;
        ItemStack s = src.getStackInSlot(idx);
        return s == null ? null : s.copy();
    }

    private static ItemStack clone(ItemStack s) {
        return s == null ? null : s.copy();
    }

    // ===== detectAndSendChanges 同步状态 =====

    public void detectAndSendChanges() {
        detectPatternDecode();
        if (Platform.isServer()) {
            getAndUpdateOutput();
        }
    }

    // ===== 样板回读（二次编辑） =====

    /** 上次见到的已编码样板（引用对比，零开销变化检测） */
    private ItemStack lastEncodedPattern;

    /**
     * 每 tick 检测编码样板放入 OUT 槽：解析其内容回填面板格子供二次编辑。
     * 双端各自执行——服务端为权威数据，客户端本地同步保证模式/格子即时刷新。
     * 移植自原生 PartPatternTerminal.onChangeInventory → loadPatternFromItem。
     */
    private void detectPatternDecode() {
        ItemStack cur = patternInv.getStackInSlot(1);
        if (cur == lastEncodedPattern) return;
        lastEncodedPattern = cur;
        if (cur == null) return;
        decodePatternToGrid(cur);
    }

    private void decodePatternToGrid(ItemStack stack) {
        if (!(stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem)) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return;
        try {
            ItemStack[] ins = null;
            ItemStack[] outs = null;
            if (playerInv.player.worldObj != null && stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem cpi) {
                appeng.api.networking.crafting.ICraftingPatternDetails details =
                    cpi.getPatternForItem(stack, playerInv.player.worldObj);
                if (details != null) {
                    ins = toDisplayArray(details.getInputs());
                    outs = toDisplayArray(details.getOutputs());
                }
            }
            if (ins == null || ins.length == 0) {
                // details 为 null（GT 终极样板等）：in/out 为 AE2 栈序列化（含流体栈），
                // 必须走原生 readStackNBT 读取，不能用 vanilla ItemStack 反序列化
                if (!tag.hasKey("in")) return;
                ins = readAeStackList(tag.getTagList("in", 10));
                outs = readAeStackList(tag.getTagList("out", 10));
            }
            if (ins == null || ins.length == 0) return;

            boolean isCrafting = tag.getBoolean("crafting");
            setCraftingMode(isCrafting);
            setSubstitute(tag.getBoolean("substitute"));
            setBeSubstitute(tag.getBoolean("beSubstitute"));
            // 保留样板内的配方池标识，二次编辑后再编码/上传仍可识别
            String recipeMap = tag.hasKey("apu:recipeMap") ? tag.getString("apu:recipeMap") : null;
            fill(ins, outs, isCrafting, null, recipeMap);
        } catch (Throwable ignored) {
        }
    }

    /**
     * details.getInputs() 可能因泛型擦除混入流体栈（GT 终极样板），
     * 元素级安全转换：流体 → ae2fc ItemFluidDrop 展示物品，物品 → 原样。
     */
    private static ItemStack[] toDisplayArray(IAEItemStack[] arr) {
        if (arr == null) return new ItemStack[0];
        ItemStack[] r = new ItemStack[arr.length];
        for (int i = 0; i < arr.length; i++) {
            r[i] = toDisplayItemStack((appeng.api.storage.data.IAEStack<?>) arr[i]);
        }
        return r;
    }

    private static ItemStack toDisplayItemStack(appeng.api.storage.data.IAEStack<?> s) {
        if (s == null) return null;
        if (s instanceof appeng.api.storage.data.IAEFluidStack fs) {
            FluidStack fluid = fs.getFluidStack();
            if (fluid != null) {
                // 首选 GT 流体展示物品（与 NEI 填充/编码时的格子表示一致，tooltip 带温度/状态）
                ItemStack display = makeFluidDisplay(fluid);
                if (display != null) return display;
            }
            // 降级：ae2fc ItemFluidDrop 包装
            appeng.api.storage.data.IAEItemStack ais =
                Platform.stackConvert(fs.copy().setStackSize(Math.max(1, fs.getStackSize())));
            return ais != null ? ais.getItemStack() : null;
        }
        if (s instanceof IAEItemStack is) {
            ItemStack st = is.getItemStack();
            // 上游 stackConvert 已把流体转成 ae2fc ItemFluidDrop 物品（NBT "Fluid" 存流体名），
            // 还原为 GT 展示物品以保持与编码时格子表示一致
            if (st != null && st.stackTagCompound != null && st.stackTagCompound.hasKey("Fluid")) {
                net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry
                    .getFluid(st.stackTagCompound.getString("Fluid"));
                if (f != null) {
                    ItemStack display = makeFluidDisplay(new FluidStack(f, (int) Math.max(1, s.getStackSize())));
                    if (display != null) return display;
                }
            }
            return st;
        }
        return null;
    }

    /** GT 流体展示物品工厂，异常时返回 null 由调用方降级 */
    private static ItemStack makeFluidDisplay(FluidStack fluid) {
        try {
            return gregtech.api.util.GTUtility.getFluidDisplayStack(fluid, true);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** AE2 栈 NBT 列表 → 展示用 ItemStack[] */
    private static ItemStack[] readAeStackList(NBTTagList list) {
        if (list == null || list.tagCount() == 0) return new ItemStack[0];
        ItemStack[] r = new ItemStack[list.tagCount()];
        for (int i = 0; i < list.tagCount(); i++) {
            r[i] = toDisplayItemStack(appeng.util.Platform.readStackNBT(list.getCompoundTagAt(i), true));
        }
        return r;
    }

    // ===== Getter =====

    public boolean isCraftingMode() {
        return craftingMode;
    }

    public void setCraftingMode(boolean v) {
        this.craftingMode = v;
        updateOrderOfOutputSlots();
    }

    public String getLastMachineName() {
        return lastMachineName;
    }

    public String getLastRecipeMap() {
        return lastRecipeMap;
    }

    public boolean isLastNeedsMapping() {
        return lastNeedsMapping;
    }

    /**
     * 由 recipeMap 统一计算编码/填充后的机器名与映射状态：
     * lastMachineName 为中文搜索词（无映射时退化为原始 id），
     * lastNeedsMapping 标识处理配方且无中文映射（需弹映射页）。
     */
    private void applyRecipeMapMeta(String recipeMap) {
        lastMachineName = null;
        lastRecipeMap = null;
        lastNeedsMapping = false;
        if (recipeMap != null && !recipeMap.isEmpty()) {
            lastRecipeMap = recipeMap;
            lastMachineName = RecipeMapNameConfig.resolveSearchKeyword(recipeMap);
            lastNeedsMapping = recipeMap.equals(lastMachineName);
        }
    }

    public boolean isSubstitute() {
        return substitute;
    }

    public void setSubstitute(boolean v) {
        this.substitute = v;
    }

    public boolean isBeSubstitute() {
        return beSubstitute;
    }

    public void setBeSubstitute(boolean v) {
        this.beSubstitute = v;
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean v) {
        this.inverted = v;
    }

    public int getActivePage() {
        return activePage;
    }

    public void setActivePage(int v) {
        this.activePage = v;
    }

    public SlotRestrictedInput getPatternSlotIN() {
        return patternSlotIN;
    }

    public SlotRestrictedInput getPatternSlotOUT() {
        return patternSlotOUT;
    }

    /** 该槽是否为输出格（合成结果格 / 处理模式输出列）。输出格数量由配方决定，禁止中键编辑。 */
    public boolean isOutputSlot(Slot s) {
        if (s == null) return false;
        if (s == patternSlotOUT) return true;
        for (Slot o : outputExSlots) {
            if (o == s) return true;
        }
        return false;
    }

    public SlotFake getCraftSlot() {
        return craftSlot;
    }

    public AppEngInternalInventory getCraftingInv() {
        return craftingInv;
    }

    public AppEngInternalInventory getCraftingExInv() {
        return craftingExInv;
    }

    public AppEngInternalInventory getOutputExInv() {
        return outputExInv;
    }

    public AppEngInternalInventory getPatternInv() {
        return patternInv;
    }

    public AppEngInternalInventory getCOut() {
        return cOut;
    }

    public SlotFakeCraftingMatrix[] getCraftingSlots() {
        return craftingSlots;
    }

    public SlotPatternFake[] getCraftingExSlots() {
        return craftingExSlots;
    }

    public SlotPatternFake[] getOutputExSlots() {
        return outputExSlots;
    }

    public AppEngInternalInventory getMergedInputInv() {
        return craftingMode ? craftingInv : craftingExInv;
    }

    public AppEngInternalInventory getMergedOutputInv() {
        return outputExInv;
    }

    public AppEngInternalInventory getMergedResultInv() {
        return cOut;
    }

    public AppEngInternalInventory getMergedBlankInv() {
        return patternInv;
    }

    public AppEngInternalInventory getMergedEncodedInv() {
        return patternInv;
    }

    // ===== IOptionalSlotHost =====

    @Override
    public boolean isSlotEnabled(int idx) {
        if (idx < 4) {
            return inverted || idx == 0;
        }
        return !inverted || idx == 4;
    }

    // ===== 流体支持 =====

    /** 检测 ItemStack 是否为流体容器（GTNH 的 ItemFluidDisplay / FluidCell 等） */
    public static boolean isFluidItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        // 标准 FluidContainerRegistry
        if (FluidContainerRegistry.getFluidForFilledItem(stack) != null) return true;
        // IFluidContainerItem（Cell、通用流体容器等）
        if (stack.getItem() instanceof net.minecraftforge.fluids.IFluidContainerItem) {
            try {
                FluidStack contents = ((net.minecraftforge.fluids.IFluidContainerItem) stack.getItem()).getFluid(stack);
                if (contents != null && contents.amount > 0) return true;
            } catch (Throwable ignored) {}
        }
        // GT ItemFluidDisplay：类名包含 "ItemFluidDisplay"
        if (isGTFluidDisplayItem(stack)) return true;
        return false;
    }

    /** 判断是否为 GT ItemFluidDisplay（gregtech.common.items.ItemFluidDisplay） */
    private static boolean isGTFluidDisplayItem(ItemStack stack) {
        if (stack.stackTagCompound == null) return false;
        String className = stack.getItem().getClass().getName();
        return className.contains("ItemFluidDisplay");
    }

    /** 从 GT ItemFluidDisplay 的 NBT 中解析 FluidStack（mFluidMaterialName + mFluidDisplayAmount） */
    private static FluidStack getFluidFromGTDisplay(ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null) return null;
        if (!stack.stackTagCompound.hasKey("mFluidMaterialName")) return null;
        String matName = stack.stackTagCompound.getString("mFluidMaterialName");
        int amount = stack.stackTagCompound.getInteger("mFluidDisplayAmount");
        if (amount <= 0) amount = 1000;
        // 解析 Fluid：遍历 FluidRegistry 查找名称匹配
        net.minecraftforge.fluids.Fluid fluid = findFluidByName(matName);
        if (fluid == null) return null;
        return new FluidStack(fluid, amount);
    }

    /** 根据材质名（如 "fluid.oxygen"、"oxygen"）查找 FluidRegistry 中的 Fluid */
    private static net.minecraftforge.fluids.Fluid findFluidByName(String matName) {
        if (matName == null || matName.isEmpty()) return null;
        // 精确匹配
        net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(matName);
        if (f != null) return f;
        // 去掉 "fluid." 前缀再试
        if (matName.startsWith("fluid.")) {
            f = net.minecraftforge.fluids.FluidRegistry.getFluid(matName.substring(6));
            if (f != null) return f;
        }
        // 严格匹配：模糊 contains 会误命中（如 "oxygen" 被 "liquidoxygen" 包含，
        // HashMap 遍历顺序不定导致随机填错流体）。改为完全相等 → 唯一前缀 → 多义放弃。
        String lower = matName.toLowerCase();
        for (net.minecraftforge.fluids.Fluid registered : net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids()
            .values()) {
            if (registered.getName() != null && registered.getName().toLowerCase()
                .equals(lower)) {
                return registered;
            }
        }
        net.minecraftforge.fluids.Fluid unique = null;
        for (net.minecraftforge.fluids.Fluid registered : net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids()
            .values()) {
            String rName = registered.getName();
            if (rName == null) continue;
            String rl = rName.toLowerCase();
            boolean match = rl.startsWith(lower) || lower.startsWith(rl);
            if (match) {
                if (unique != null && unique != registered) return null; // 多义，放弃猜测
                unique = registered;
            }
        }
        return unique;
    }

    /** 从 ItemStack 中提取 FluidStack */
    public static FluidStack getFluidFromItem(ItemStack stack) {
        if (stack == null) return null;
        // 标准 FluidContainerRegistry
        FluidStack fs = FluidContainerRegistry.getFluidForFilledItem(stack);
        if (fs != null) return fs;
        // IFluidContainerItem
        if (stack.getItem() instanceof net.minecraftforge.fluids.IFluidContainerItem) {
            try {
                FluidStack contents = ((net.minecraftforge.fluids.IFluidContainerItem) stack.getItem()).getFluid(stack);
                if (contents != null && contents.amount > 0) return contents;
            } catch (Throwable ignored) {}
        }
        // GT ItemFluidDisplay
        if (isGTFluidDisplayItem(stack)) {
            FluidStack gtFluid = getFluidFromGTDisplay(stack);
            if (gtFluid != null) return gtFluid;
        }
        // ae2fc ItemFluidDrop 等通用流体drop物品：NBT "Fluid" 存流体名，stackSize 即毫升级数
        if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("Fluid")) {
            String fname = stack.stackTagCompound.getString("Fluid");
            net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(fname);
            if (f != null && stack.stackSize > 0) {
                return new FluidStack(f, stack.stackSize);
            }
        }
        return null;
    }

    /** 按 factor 倍增或倍除 ItemStack 中的流体 amount（修改 NBT 中的 FluidStack.amount） */
    private static void multiplyFluidItem(ItemStack stack, int factor, boolean divide) {
        // GT ItemFluidDisplay：直接倍增 stackSize（NEI 用 stackSize 表示流体桶数）
        if (isGTFluidDisplayItem(stack)) {
            stack.stackSize = divide ? Math.max(1, stack.stackSize / factor)
                : Math.min(stack.stackSize * factor, stack.getMaxStackSize());
            return;
        }
        FluidStack fs = getFluidFromItem(stack);
        if (fs == null) return;
        int newAmount = divide ? Math.max(1, fs.amount / factor) : Math.min(fs.amount * factor, Integer.MAX_VALUE);
        if (stack.getItem() instanceof net.minecraftforge.fluids.IFluidContainerItem) {
            fs.amount = newAmount;
            ((net.minecraftforge.fluids.IFluidContainerItem) stack.getItem()).fill(stack, fs, true);
        } else if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("Fluid")) {
            // 通用 NBT Fluid 标签
            fs.amount = newAmount;
            NBTTagCompound fluidTag = new NBTTagCompound();
            fs.writeToNBT(fluidTag);
            stack.stackTagCompound.setTag("Fluid", fluidTag);
        }
    }

    private static void setFluidItemAmount(ItemStack stack, int newAmount) {
        // GT ItemFluidDisplay：newAmount 为总流体量，stackSize = newAmount / mFluidDisplayAmount
        if (isGTFluidDisplayItem(stack) && stack.stackTagCompound != null) {
            int perUnit = stack.stackTagCompound.getInteger("mFluidDisplayAmount");
            if (perUnit <= 0) perUnit = 1000;
            if (newAmount % perUnit == 0) {
                stack.stackSize = Math.max(1, newAmount / perUnit);
            } else {
                // 不能整除时，调整 perUnit 使 stackSize=1 能表示精确量
                stack.stackTagCompound.setInteger("mFluidDisplayAmount", newAmount);
                stack.stackSize = 1;
            }
            return;
        }
        FluidStack fs = getFluidFromItem(stack);
        if (fs == null) return;
        if (stack.getItem() instanceof net.minecraftforge.fluids.IFluidContainerItem) {
            fs.amount = newAmount;
            ((net.minecraftforge.fluids.IFluidContainerItem) stack.getItem()).fill(stack, fs, true);
        } else if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("Fluid")) {
            fs.amount = newAmount;
            NBTTagCompound fluidTag = new NBTTagCompound();
            fs.writeToNBT(fluidTag);
            stack.stackTagCompound.setTag("Fluid", fluidTag);
        }
    }
}
