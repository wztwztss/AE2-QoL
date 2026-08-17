package com.wztwzt.ae2_qof.mixin.ae;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;
import com.wztwzt.ae2_qof.common.RecipeMapNameConfig;
import com.wztwzt.ae2_qof.util.RecipeMapDetector;

import appeng.api.AEApi;
import appeng.api.parts.IInterfaceTerminal;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;

/**
 * 为原生接口终端容器注入"样板编码面板"库存与槽：
 * 27 输入格 + 9 输出格 + 1 合成结果 + 1 空白样板 + 1 已编码样板。
 * <p>
 * 有线接口终端与 ae2fc 无线接口终端共用同一 {@code GuiInterfaceTerminal}/
 * {@code ContainerInterfaceTerminal}，因此两端自动同时获得二合一功能。
 * 槽使用原生 {@link Slot}（非 AppEngSlot），避免 {@code repositionSlots} 复位坐标。
 */
@Mixin(ContainerInterfaceTerminal.class)
public abstract class MixinContainerInterfaceTerminal implements IMergedPatternTerminal {

    private final AppEngInternalInventory mergedInputInv = new AppEngInternalInventory(null, INPUT_MAX);
    private final AppEngInternalInventory mergedOutputInv = new AppEngInternalInventory(null, OUTPUT_MAX);
    private final AppEngInternalInventory mergedResultInv = new AppEngInternalInventory(null, 1);
    private final AppEngInternalInventory mergedBlankInv = new AppEngInternalInventory(null, 1);
    private final AppEngInternalInventory mergedEncodedInv = new AppEngInternalInventory(null, 1);

    private Slot mergedResultSlot;
    private Slot mergedBlankSlot;
    private Slot mergedEncodedSlot;
    private int mergedSlotBase = -1;
    private boolean mergedCraftingMode = true;
    private boolean mergedSubstitute = false;
    private boolean mergedBeSubstitute = false;

    @Shadow
    public List inventorySlots;

    @Shadow
    protected Slot addSlotToContainer(Slot slot) {
        throw new UnsupportedOperationException();
    }

    @Shadow(remap = false)
    public InventoryPlayer getPlayerInv() {
        throw new UnsupportedOperationException();
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void ae2qol$initMergedSlots(InventoryPlayer inventoryPlayer, IInterfaceTerminal anchor, CallbackInfo ci) {
        this.mergedSlotBase = this.inventorySlots.size();
        for (int i = 0; i < INPUT_MAX; i++) {
            this.addSlotToContainer(
                new Slot(this.mergedInputInv, i, mergedPanelX(i), mergedGridRowY(i / INPUT_COLS)));
        }
        for (int j = 0; j < OUTPUT_MAX; j++) {
            this.addSlotToContainer(
                new Slot(this.mergedOutputInv, j, mergedPanelX(j), mergedOutputBaseY() + (j / INPUT_COLS) * SLOT_SIZE));
        }
        this.mergedResultSlot = this.addSlotToContainer(new MergedResultSlot(this.mergedResultInv, 0, mergedPanelX(0), mergedOutputBaseY()));
        this.mergedBlankSlot = this.addSlotToContainer(
            new MergedBlankSlot(this.mergedBlankInv, 0, mergedPanelX(0), mergedBlankRowY()));
        this.mergedEncodedSlot = this.addSlotToContainer(
            new MergedEncodedSlot(this.mergedEncodedInv, 0, mergedPanelX(1), mergedBlankRowY()));
    }

    @Inject(method = "detectAndSendChanges", at = @At("HEAD"), remap = false)
    private void ae2qol$recomputeResult(CallbackInfo ci) {
        if (Platform.isServer()) {
            this.mergedRecomputeResult();
        }
    }

    @Override
    public AppEngInternalInventory getMergedInputInv() {
        return this.mergedInputInv;
    }

    @Override
    public AppEngInternalInventory getMergedOutputInv() {
        return this.mergedOutputInv;
    }

    @Override
    public AppEngInternalInventory getMergedResultInv() {
        return this.mergedResultInv;
    }

    @Override
    public AppEngInternalInventory getMergedBlankInv() {
        return this.mergedBlankInv;
    }

    @Override
    public AppEngInternalInventory getMergedEncodedInv() {
        return this.mergedEncodedInv;
    }

    @Override
    public Slot getMergedResultSlot() {
        return this.mergedResultSlot;
    }

    @Override
    public Slot getMergedBlankSlot() {
        return this.mergedBlankSlot;
    }

    @Override
    public Slot getMergedEncodedSlot() {
        return this.mergedEncodedSlot;
    }

    @Override
    public int getMergedSlotBase() {
        return this.mergedSlotBase;
    }

    @Override
    public boolean isMergedCraftingMode() {
        return this.mergedCraftingMode;
    }

    @Override
    public void setMergedCraftingMode(boolean crafting) {
        this.mergedCraftingMode = crafting;
        this.mergedRecomputeResult();
    }

    @Override
    public boolean isMergedSubstitute() {
        return this.mergedSubstitute;
    }

    @Override
    public void setMergedSubstitute(boolean substitute) {
        this.mergedSubstitute = substitute;
    }

    @Override
    public boolean isMergedBeSubstitute() {
        return this.mergedBeSubstitute;
    }

    @Override
    public void setMergedBeSubstitute(boolean beSubstitute) {
        this.mergedBeSubstitute = beSubstitute;
    }

    @Override
    public String mergedEncode() {
        ItemStack encoded;
        ItemStack[] nonEmptyInputs;
        ItemStack[] outputs;
        if (this.mergedCraftingMode) {
            ItemStack[] in = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                in[i] = this.mergedInputInv.getStackInSlot(i);
            }
            if (isEmptyGrid(in)) {
                return null;
            }
            ItemStack result = computeCraftingResult(in);
            if (result == null) {
                return null;
            }
            encoded = createPattern(in, new ItemStack[] { result }, true);
            nonEmptyInputs = collectNonEmpty(this.mergedInputInv, 9);
            outputs = new ItemStack[] { result };
        } else {
            nonEmptyInputs = collectNonEmpty(this.mergedInputInv, INPUT_MAX);
            outputs = collectNonEmpty(this.mergedOutputInv, OUTPUT_MAX);
            if (nonEmptyInputs.length == 0 || outputs.length == 0) {
                return null;
            }
            encoded = createPattern(nonEmptyInputs, outputs, false);
        }
        if (encoded == null) {
            return null;
        }

        ItemStack blank = this.mergedBlankInv.getStackInSlot(0);
        if (blank == null || !isBlankPattern(blank)) {
            return null;
        }
        if (this.mergedEncodedInv.getStackInSlot(0) != null) {
            return null;
        }

        // 消耗一张空白样板
        if (blank.stackSize <= 1) {
            this.mergedBlankInv.setInventorySlotContents(0, null);
        } else {
            blank.stackSize--;
            this.mergedBlankInv.markDirty();
        }

        // 反查 GT 配方池并写入 apu:recipeMap，设置显示名为机器中文名
        String name = null;
        try {
            String playerKey = this.getPlayerInv().player.getUniqueID().toString();
            String recipeMap = RecipeMapDetector.detectRecipeMap(nonEmptyInputs, outputs, playerKey);
            if (recipeMap != null && !recipeMap.isEmpty()) {
                if (encoded.getTagCompound() == null) {
                    encoded.setTagCompound(new NBTTagCompound());
                }
                encoded.getTagCompound()
                    .setString("apu:recipeMap", recipeMap);
                name = RecipeMapNameConfig.resolveSearchKeyword(recipeMap);
            }
        } catch (Throwable t) {
            System.out.println("[APU] encode machine name error: " + t.getMessage());
        }
        if (name != null && !name.isEmpty()) {
            encoded.setStackDisplayName(name);
        }

        this.mergedEncodedInv.setInventorySlotContents(0, encoded);
        return name;
    }

    @Override
    public void mergedClear() {
        for (int i = 0; i < INPUT_MAX; i++) {
            this.mergedInputInv.setInventorySlotContents(i, null);
        }
        for (int j = 0; j < OUTPUT_MAX; j++) {
            this.mergedOutputInv.setInventorySlotContents(j, null);
        }
        this.mergedResultInv.setInventorySlotContents(0, null);
    }

    @Override
    public void mergedDoubleStacks() {
        for (int i = 0; i < INPUT_MAX; i++) {
            ItemStack s = this.mergedInputInv.getStackInSlot(i);
            if (s != null) {
                s.stackSize = Math.min(s.stackSize * 2, s.getMaxStackSize());
            }
        }
        for (int j = 0; j < OUTPUT_MAX; j++) {
            ItemStack s = this.mergedOutputInv.getStackInSlot(j);
            if (s != null) {
                s.stackSize = Math.min(s.stackSize * 2, s.getMaxStackSize());
            }
        }
        this.mergedRecomputeResult();
    }

    @Override
    public void mergedFill(ItemStack[] inputs, ItemStack[] outputs, boolean crafting) {
        this.mergedCraftingMode = crafting;
        for (int i = 0; i < INPUT_MAX; i++) {
            ItemStack s = (inputs != null && i < inputs.length) ? inputs[i] : null;
            this.mergedInputInv.setInventorySlotContents(i, s == null ? null : s.copy());
        }
        for (int j = 0; j < OUTPUT_MAX; j++) {
            ItemStack s = (outputs != null && j < outputs.length) ? outputs[j] : null;
            this.mergedOutputInv.setInventorySlotContents(j, s == null ? null : s.copy());
        }
        this.mergedResultInv.setInventorySlotContents(0, null);
        this.mergedRecomputeResult();
    }

    @Override
    public void mergedSwapOutputs() {
        ItemStack[] items = new ItemStack[OUTPUT_MAX];
        int count = 0;
        for (int j = 0; j < OUTPUT_MAX; j++) {
            ItemStack s = this.mergedOutputInv.getStackInSlot(j);
            if (s != null) {
                items[count++] = s;
            }
        }
        if (count < 2) {
            return;
        }
        ItemStack first = items[0];
        for (int i = 0; i < count - 1; i++) {
            items[i] = items[i + 1];
        }
        items[count - 1] = first;
        int idx = 0;
        for (int j = 0; j < OUTPUT_MAX; j++) {
            ItemStack s = this.mergedOutputInv.getStackInSlot(j);
            if (s != null) {
                this.mergedOutputInv.setInventorySlotContents(j, items[idx++]);
            }
        }
        this.mergedRecomputeResult();
    }

    @Override
    public void mergedRecomputeResult() {
        if (!this.mergedCraftingMode) {
            if (this.mergedResultInv.getStackInSlot(0) != null) {
                this.mergedResultInv.setInventorySlotContents(0, null);
            }
            return;
        }
        ItemStack[] in = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            in[i] = this.mergedInputInv.getStackInSlot(i);
        }
        ItemStack res = computeCraftingResult(in);
        ItemStack cur = this.mergedResultInv.getStackInSlot(0);
        if (!ItemStack.areItemStacksEqual(res, cur)) {
            this.mergedResultInv.setInventorySlotContents(0, res);
        }
    }

    // ===== 布局坐标 =====

    private static int mergedPanelX(int col) {
        return PANEL_X + col * SLOT_SIZE;
    }

    private static int mergedGridRowY(int row) {
        return PANEL_Y + row * SLOT_SIZE;
    }

    private static int mergedOutputBaseY() {
        return PANEL_Y + OUTPUT_ROW_BASE * SLOT_SIZE + 4;
    }

    private static int mergedBlankRowY() {
        return mergedOutputBaseY() + 3 * SLOT_SIZE + 4;
    }

    // ===== 内部工具 =====

    private boolean isEmptyGrid(ItemStack[] grid) {
        for (ItemStack s : grid) {
            if (s != null) {
                return false;
            }
        }
        return true;
    }

    private ItemStack[] collectNonEmpty(AppEngInternalInventory inv, int limit) {
        int count = 0;
        for (int i = 0; i < limit; i++) {
            if (inv.getStackInSlot(i) != null) {
                count++;
            }
        }
        ItemStack[] result = new ItemStack[count];
        int idx = 0;
        for (int i = 0; i < limit; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null) {
                result[idx++] = s;
            }
        }
        return result;
    }

    private ItemStack computeCraftingResult(ItemStack[] in) {
        try {
            final InventoryCrafting ic = new InventoryCrafting(new Container() {

                @Override
                public boolean canInteractWith(EntityPlayer p) {
                    return false;
                }
            }, 3, 3);
            for (int i = 0; i < 9 && i < in.length; i++) {
                ic.setInventorySlotContents(i, in[i]);
            }
            return CraftingManager.getInstance()
                .findMatchingRecipe(ic, null);
        } catch (Throwable t) {
            return null;
        }
    }

    private ItemStack createPattern(ItemStack[] in, ItemStack[] out, boolean crafting) {
        ItemStack pattern = AEApi.instance()
            .definitions()
            .items()
            .encodedPattern()
            .maybeStack(1)
            .orNull();
        if (pattern == null) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList tagIn = new NBTTagList();
        for (ItemStack s : in) {
            NBTTagCompound c = new NBTTagCompound();
            if (s != null) {
                s.writeToNBT(c);
            }
            tagIn.appendTag(c);
        }
        NBTTagList tagOut = new NBTTagList();
        for (ItemStack s : out) {
            NBTTagCompound c = new NBTTagCompound();
            if (s != null) {
                s.writeToNBT(c);
            }
            tagOut.appendTag(c);
        }
        tag.setTag("in", tagIn);
        tag.setTag("out", tagOut);
        tag.setBoolean("crafting", crafting);
        tag.setBoolean("substitute", this.mergedSubstitute || this.mergedBeSubstitute);
        pattern.setTagCompound(tag);
        return pattern;
    }

    private boolean isBlankPattern(ItemStack stack) {
        return AEApi.instance()
            .definitions()
            .materials()
            .blankPattern()
            .isSameAs(stack);
    }

    // ===== 专用槽 =====

    private static class MergedResultSlot extends Slot {

        MergedResultSlot(AppEngInternalInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    }

    private static class MergedBlankSlot extends Slot {

        MergedBlankSlot(AppEngInternalInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return stack != null && AEApi.instance()
                .definitions()
                .materials()
                .blankPattern()
                .isSameAs(stack);
        }
    }

    private static class MergedEncodedSlot extends Slot {

        MergedEncodedSlot(AppEngInternalInventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            if (stack == null) {
                return false;
            }
            if (AEApi.instance()
                .definitions()
                .items()
                .encodedPattern()
                .isSameAs(stack)) {
                return true;
            }
            return stack.getItem() instanceof appeng.items.misc.ItemEncodedPattern
                || stack.getItem() instanceof com.glodblock.github.common.item.ItemFluidEncodedPattern;
        }
    }
}