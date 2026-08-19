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
    private final AppEngInternalInventory patternInv = new AppEngInternalInventory(null, 2);
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

    public PatternContainer(Container container, InventoryPlayer playerInv) {
        this.container = container;
        this.playerInv = playerInv;
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
                if (!pullBlankFromNetwork()) return;
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

        // GT 配方池反查
        try {
            String playerKey = playerInv.player.getUniqueID()
                .toString();
            String recipeMap = RecipeMapDetector.detectRecipeMap(in, out, playerKey);
            if (recipeMap != null && !recipeMap.isEmpty()) {
                output.getTagCompound()
                    .setString("apu:recipeMap", recipeMap);
                String name = RecipeMapNameConfig.resolveSearchKeyword(recipeMap);
                if (name != null && !name.isEmpty()) output.setStackDisplayName(name);
            }
        } catch (Throwable t) {
            System.out.println("[APU] encode machine name error: " + t.getMessage());
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
        final NBTTagCompound c = new NBTTagCompound();
        if (i != null) i.writeToNBT(c);
        return c;
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

    /** 空白样板槽为空时，尝试从 AE 网络扣取 1 张空白样板放入槽中（原生 ContainerPatternTerm.encode 行为）。 */
    private boolean pullBlankFromNetwork() {
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
            if (extracted == null || extracted.getStackSize() <= 0) return false;
            patternSlotIN.putStack(extracted.getItemStack());
            return true;
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
        if (craftingMode) return;
        for (final Slot s : craftingExSlots) {
            ItemStack st = s.getStack();
            if (st != null) st.stackSize = Math.min(st.stackSize * 2, st.getMaxStackSize());
        }
        for (final Slot s : outputExSlots) {
            ItemStack st = s.getStack();
            if (st != null) st.stackSize = Math.min(st.stackSize * 2, st.getMaxStackSize());
        }
    }

    public void fill(ItemStack[] inputs, ItemStack[] outputs, boolean mode) {
        this.craftingMode = mode;
        for (int i = 0; i < CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES; i++) {
            ItemStack s = (inputs != null && i < inputs.length) ? inputs[i] : null;
            if (i < 9) craftingInv.setInventorySlotContents(i, s == null ? null : s.copy());
            craftingExSlots[i].putStack(s == null ? null : s.copy());
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

    // ===== detectAndSendChanges 同步状态 =====

    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            getAndUpdateOutput();
        }
    }

    // ===== Getter =====

    public boolean isCraftingMode() {
        return craftingMode;
    }

    public void setCraftingMode(boolean v) {
        this.craftingMode = v;
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
}
