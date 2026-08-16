package com.wztwzt.ae2_qof.client.event;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.client.OverlayConfig;
import com.wztwzt.ae2_qof.mixin.GuiContainerAccessor;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.RecallPatternPacket;
import com.wztwzt.ae2_qof.network.RequestProvidersListPacket;
import com.wztwzt.ae2_qof.network.SwapPatternPacket;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.implementations.GuiPatternTermEx;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class GuiUploadButtonHandler {

    public static final int BUTTON_UPLOAD_ID = 999;
    public static final int BUTTON_RECALL_ID = 998;
    public static final int BUTTON_SWAP_ID = 997;
    public static final int BUTTON_OVERLAY_ID = 996;
    private GuiButton uploadButton;
    private GuiButton recallButton;
    private GuiButton swapButton;
    private GuiButton overlayButton;

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new GuiUploadButtonHandler());
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.gui;
        if (gui == null) {
            return;
        }

        if (!(gui instanceof GuiPatternTerm) && !(gui instanceof GuiPatternTermEx)) {
            return;
        }

        if (!(gui instanceof GuiContainer)) {
            return;
        }

        GuiContainerAccessor accessor = (GuiContainerAccessor) gui;

        int guiLeft = accessor.getGuiLeft();
        int guiTop = accessor.getGuiTop();
        int ySize = accessor.getYSize();

        int encodeButtonX = guiLeft + 147;
        int encodeButtonY = guiTop + ySize - 142;

        int btnSize = 12;

        int uploadBtnX = encodeButtonX - btnSize;
        int uploadBtnY = encodeButtonY + 2;
        this.uploadButton = new GuiButton(BUTTON_UPLOAD_ID, uploadBtnX, uploadBtnY, btnSize, btnSize, "\u2191");
        event.buttonList.add(this.uploadButton);

        int recallBtnX = encodeButtonX + 18;
        int recallBtnY = encodeButtonY + 2;
        this.recallButton = new GuiButton(BUTTON_RECALL_ID, recallBtnX, recallBtnY, btnSize, btnSize, "\u2190");
        event.buttonList.add(this.recallButton);

        int swapBtnX = uploadBtnX;
        int swapBtnY = uploadBtnY - btnSize - 2;
        this.swapButton = new GuiButton(BUTTON_SWAP_ID, swapBtnX, swapBtnY, btnSize, btnSize, "\u21C4");
        event.buttonList.add(this.swapButton);

        int overlayBtnX = recallBtnX;
        int overlayBtnY = swapBtnY;
        boolean overlayOn = OverlayConfig.isEnabled();
        this.overlayButton = new GuiButton(
            BUTTON_OVERLAY_ID,
            overlayBtnX,
            overlayBtnY,
            btnSize,
            btnSize,
            overlayOn ? "OV" : "--");
        event.buttonList.add(this.overlayButton);
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.button == null) {
            return;
        }

        if (event.button.id == BUTTON_UPLOAD_ID && uploadButton != null && event.button == uploadButton) {
            boolean forceGui = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT);

            ItemStack patternStack = getPatternFromOutputSlot(event.gui);
            if (patternStack == null) {
                return;
            }

            String recipeMap = null;
            if (patternStack.getTagCompound() != null && patternStack.getTagCompound()
                .hasKey("apu:recipeMap")) {
                recipeMap = patternStack.getTagCompound()
                    .getString("apu:recipeMap");
            }

            if (recipeMap == null || recipeMap.isEmpty()) {
                recipeMap = ClientState.pendingRecipeMap;
                if (recipeMap != null && !recipeMap.isEmpty()) {
                    if (patternStack.getTagCompound() == null) {
                        patternStack.setTagCompound(new NBTTagCompound());
                    }
                    patternStack.getTagCompound()
                        .setString("apu:recipeMap", recipeMap);
                }
            }

            if (recipeMap != null && !recipeMap.isEmpty()) {
                ClientState.lastRecipeMap = recipeMap;
                ClientState.pendingRecipeMap = null;
                ModNetwork.CHANNEL.sendToServer(new RequestProvidersListPacket(recipeMap, forceGui));
                event.setCanceled(true);
                return;
            }

            ItemStack[] inputs = new ItemStack[0];
            ItemStack[] outputs = new ItemStack[0];
            try {
                ICraftingPatternDetails details = ((ICraftingPatternItem) patternStack.getItem())
                    .getPatternForItem(patternStack, event.gui.mc.theWorld);
                if (details != null) {
                    appeng.api.storage.data.IAEItemStack[] aeInputs = details.getInputs();
                    appeng.api.storage.data.IAEItemStack[] aeOutputs = details.getOutputs();
                    if (aeInputs != null) {
                        inputs = new ItemStack[aeInputs.length];
                        for (int i = 0; i < aeInputs.length; i++) {
                            inputs[i] = aeInputs[i] != null ? aeInputs[i].getItemStack() : null;
                        }
                    }
                    if (aeOutputs != null) {
                        outputs = new ItemStack[aeOutputs.length];
                        for (int i = 0; i < aeOutputs.length; i++) {
                            outputs[i] = aeOutputs[i] != null ? aeOutputs[i].getItemStack() : null;
                        }
                    }
                }
            } catch (Throwable ignored) {}
            ModNetwork.CHANNEL.sendToServer(new RequestProvidersListPacket(inputs, outputs, forceGui));
            event.setCanceled(true);
        }

        if (event.button.id == BUTTON_RECALL_ID && recallButton != null && event.button == recallButton) {
            long lastId = ClientState.lastProviderId;
            if (lastId != 0) {
                ModNetwork.CHANNEL.sendToServer(new RecallPatternPacket(lastId));
                event.setCanceled(true);
            }
        }

        if (event.button.id == BUTTON_SWAP_ID && swapButton != null && event.button == swapButton) {
            ModNetwork.CHANNEL.sendToServer(new SwapPatternPacket());
            event.setCanceled(true);
        }

        if (event.button.id == BUTTON_OVERLAY_ID && overlayButton != null && event.button == overlayButton) {
            boolean now = !OverlayConfig.isEnabled();
            OverlayConfig.setEnabled(now);
            overlayButton.displayString = now ? "OV" : "--";
            event.setCanceled(true);
        }
    }

    private ItemStack getPatternFromOutputSlot(GuiScreen gui) {
        try {
            net.minecraft.inventory.Container container = null;
            if (gui instanceof GuiPatternTerm term) {
                container = term.inventorySlots;
            } else if (gui instanceof GuiPatternTermEx termEx) {
                container = termEx.inventorySlots;
            }
            if (container == null) {
                return null;
            }

            Slot outputSlot = null;
            if (container instanceof ContainerPatternTerm pt) {
                Field field = ContainerPatternTerm.class.getDeclaredField("patternSlotOUT");
                field.setAccessible(true);
                outputSlot = (Slot) field.get(pt);
            } else if (container instanceof ContainerPatternTermEx pte) {
                Field field = ContainerPatternTermEx.class.getDeclaredField("patternSlotOUT");
                field.setAccessible(true);
                outputSlot = (Slot) field.get(pte);
            }

            if (outputSlot != null) {
                return outputSlot.getStack();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
