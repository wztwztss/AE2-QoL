package com.wztwzt.ae2_qof.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;
import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.client.NeiRecipeCapture;
import com.wztwzt.ae2_qof.client.OverlayConfig;
import com.wztwzt.ae2_qof.client.gui.MergedPanelLayout;
import com.wztwzt.ae2_qof.network.MergedTerminalActionPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.RecallPatternPacket;
import com.wztwzt.ae2_qof.network.RequestProvidersListPacket;
import com.wztwzt.ae2_qof.network.SwapPatternPacket;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.client.gui.implementations.GuiInterfaceTerminal;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 二合一终端面板按钮：创建、布局重定位与动作分发。
 * <p>
 * 按钮点击由 {@code MixinGuiInterfaceTerminal} 在 mouseClicked 拦截后直接调用
 * {@link #onButtonClicked(GuiScreen, int)}（绕过 masterList 点击吞噬），
 * 因此本处理器不依赖 ActionPerformedEvent 事件链。
 */
public class MergedTerminalPanelHandler {

    public static final int BUTTON_ENCODE_ID = 940;
    public static final int BUTTON_CLEAR_ID = 941;
    public static final int BUTTON_DOUBLE_ID = 942;
    public static final int BUTTON_MODE_ID = 943;
    public static final int BUTTON_SUB_ID = 944;
    public static final int BUTTON_BESUB_ID = 945;
    public static final int BUTTON_NEI_ID = 946;
    public static final int BUTTON_UPLOAD_ID = 947;
    public static final int BUTTON_RECALL_ID = 948;
    public static final int BUTTON_SWAP_ID = 949;
    public static final int BUTTON_OVERLAY_ID = 950;

    /** 客户端面板模式/替代开关（服务端容器同步状态，编码包携带） */
    public static boolean mergedCraftingMode = true;
    public static boolean mergedSubstitute = false;
    public static boolean mergedBeSubstitute = false;

    public static GuiButton btnEncode;
    public static GuiButton btnClear;
    public static GuiButton btnDouble;
    public static GuiButton btnMode;
    public static GuiButton btnSub;
    public static GuiButton btnBeSub;
    public static GuiButton btnNei;
    public static GuiButton btnUpload;
    public static GuiButton btnRecall;
    public static GuiButton btnSwap;
    public static GuiButton btnOverlay;

    private MergedTerminalPanelHandler() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new MergedTerminalPanelHandler());
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.gui;
        if (!(gui instanceof GuiInterfaceTerminal)) {
            return;
        }
        mergedCraftingMode = true;
        mergedSubstitute = false;
        mergedBeSubstitute = false;
        ClientState.mergedMachineName = null;

        btnEncode = new GuiButton(BUTTON_ENCODE_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "\u00a7e\u7f16");
        btnClear = new GuiButton(BUTTON_CLEAR_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "\u6e05");
        btnDouble = new GuiButton(BUTTON_DOUBLE_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "\u00d72");
        btnMode = new GuiButton(BUTTON_MODE_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "\u00a7a\u5408");
        btnSub = new GuiButton(BUTTON_SUB_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "\u00a78\u66ff");
        btnBeSub = new GuiButton(BUTTON_BESUB_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "\u00a78\u5907");
        btnNei = new GuiButton(BUTTON_NEI_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "N");
        btnUpload = new GuiButton(BUTTON_UPLOAD_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "\u2191");
        btnRecall = new GuiButton(BUTTON_RECALL_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "\u2190");
        btnSwap = new GuiButton(BUTTON_SWAP_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "\u21c4");
        btnOverlay = new GuiButton(BUTTON_OVERLAY_ID, 0, 0, MergedPanelLayout.BTN, MergedPanelLayout.BTN, "OV");

        event.buttonList.add(btnEncode);
        event.buttonList.add(btnClear);
        event.buttonList.add(btnDouble);
        event.buttonList.add(btnMode);
        event.buttonList.add(btnSub);
        event.buttonList.add(btnBeSub);
        event.buttonList.add(btnNei);
        event.buttonList.add(btnUpload);
        event.buttonList.add(btnRecall);
        event.buttonList.add(btnSwap);
        event.buttonList.add(btnOverlay);
    }

    /** 每帧由 GUI drawFG 调用：按当前布局摆放按钮并刷新标签 */
    public static void reposition(MergedPanelLayout.Layout layout) {
        int x0 = MergedPanelLayout.panelXFor(0);
        int x1 = MergedPanelLayout.panelXFor(1);
        int x2 = MergedPanelLayout.panelXFor(2);
        btnEncode.xPosition = x0;
        btnEncode.yPosition = layout.btnRow1Y;
        btnClear.xPosition = x1;
        btnClear.yPosition = layout.btnRow1Y;
        btnDouble.xPosition = x2;
        btnDouble.yPosition = layout.btnRow1Y;
        btnMode.xPosition = x0;
        btnMode.yPosition = layout.btnRow2Y;
        btnSub.xPosition = x1;
        btnSub.yPosition = layout.btnRow2Y;
        btnBeSub.xPosition = x2;
        btnBeSub.yPosition = layout.btnRow2Y;
        btnNei.xPosition = x0;
        btnNei.yPosition = layout.btnRow3Y;
        btnUpload.xPosition = x1;
        btnUpload.yPosition = layout.btnRow3Y;
        btnRecall.xPosition = x2;
        btnRecall.yPosition = layout.btnRow3Y;
        btnSwap.xPosition = x0;
        btnSwap.yPosition = layout.btnRow4Y;
        btnOverlay.xPosition = x1;
        btnOverlay.yPosition = layout.btnRow4Y;

        btnMode.displayString = mergedCraftingMode ? "\u00a7a\u5408" : "\u00a7a\u5904";
        btnSub.displayString = mergedSubstitute ? "\u00a7a\u66ff" : "\u00a78\u66ff";
        btnBeSub.displayString = mergedBeSubstitute ? "\u00a7a\u5907" : "\u00a78\u5907";
        btnOverlay.displayString = OverlayConfig.isEnabled() ? "OV" : "--";
    }

    /** 由 GUI mouseClicked 拦截后调用（按钮点击） */
    public static void onButtonClicked(GuiScreen screen, int buttonId) {
        GuiContainer gui = screen instanceof GuiContainer ? (GuiContainer) screen : null;
        switch (buttonId) {
            case BUTTON_ENCODE_ID:
                ClientState.mergedMachineName = null;
                ModNetwork.CHANNEL.sendToServer(
                    MergedTerminalActionPacket.encode(mergedCraftingMode, mergedSubstitute, mergedBeSubstitute));
                break;
            case BUTTON_CLEAR_ID:
                ModNetwork.CHANNEL.sendToServer(MergedTerminalActionPacket.simple(MergedTerminalActionPacket.Action.CLEAR));
                break;
            case BUTTON_DOUBLE_ID:
                ModNetwork.CHANNEL.sendToServer(MergedTerminalActionPacket.simple(MergedTerminalActionPacket.Action.DOUBLE));
                break;
            case BUTTON_MODE_ID:
                mergedCraftingMode = !mergedCraftingMode;
                ModNetwork.CHANNEL.sendToServer(
                    MergedTerminalActionPacket.flag(MergedTerminalActionPacket.Action.SET_MODE, mergedCraftingMode));
                break;
            case BUTTON_SUB_ID:
                mergedSubstitute = !mergedSubstitute;
                ModNetwork.CHANNEL.sendToServer(
                    MergedTerminalActionPacket.flag(MergedTerminalActionPacket.Action.SET_SUBSTITUTE, mergedSubstitute));
                break;
            case BUTTON_BESUB_ID:
                mergedBeSubstitute = !mergedBeSubstitute;
                ModNetwork.CHANNEL.sendToServer(
                    MergedTerminalActionPacket.flag(MergedTerminalActionPacket.Action.SET_BE_SUBSTITUTE, mergedBeSubstitute));
                break;
            case BUTTON_NEI_ID:
                handleNeiFill();
                break;
            case BUTTON_UPLOAD_ID:
                if (gui != null) {
                    handleUpload(gui);
                }
                break;
            case BUTTON_RECALL_ID:
                if (ClientState.lastProviderId != 0) {
                    ModNetwork.CHANNEL.sendToServer(new RecallPatternPacket(ClientState.lastProviderId));
                }
                break;
            case BUTTON_SWAP_ID:
                ModNetwork.CHANNEL.sendToServer(new SwapPatternPacket());
                break;
            case BUTTON_OVERLAY_ID:
                boolean now = !OverlayConfig.isEnabled();
                OverlayConfig.setEnabled(now);
                break;
            default:
                break;
        }
    }

    private static void handleNeiFill() {
        NeiRecipeCapture.RecipeData data = NeiRecipeCapture.extractCurrentRecipe();
        if (data == null || !data.valid) {
            return;
        }
        mergedCraftingMode = data.crafting;
        ModNetwork.CHANNEL.sendToServer(MergedTerminalActionPacket.fill(data.inputs, data.outputs, data.crafting));
    }

    private static void handleUpload(GuiContainer gui) {
        boolean forceGui = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (!(gui.inventorySlots instanceof IMergedPatternTerminal merged)) {
            return;
        }
        ItemStack patternStack = merged.getMergedEncodedSlot()
            .getStack();
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
            return;
        }

        ItemStack[] inputs = new ItemStack[0];
        ItemStack[] outputs = new ItemStack[0];
        try {
            ICraftingPatternDetails details = ((ICraftingPatternItem) patternStack.getItem())
                .getPatternForItem(patternStack, Minecraft.getMinecraft().theWorld);
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
    }
}