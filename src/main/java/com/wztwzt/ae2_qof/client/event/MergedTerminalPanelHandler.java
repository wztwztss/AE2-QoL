package com.wztwzt.ae2_qof.client.event;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import com.wztwzt.ae2_qof.api.IMergedPatternTerminal;
import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.client.OverlayConfig;
import com.wztwzt.ae2_qof.client.gui.MergedPanelLayout;
import com.wztwzt.ae2_qof.merged.GuiMergedTerminal;
import com.wztwzt.ae2_qof.mixin.GuiContainerAccessor;
import com.wztwzt.ae2_qof.network.MergedTerminalActionPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.RecallPatternPacket;
import com.wztwzt.ae2_qof.network.RequestProvidersListPacket;
import com.wztwzt.ae2_qof.network.SwapPatternPacket;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.localization.GuiText;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 二合一终端面板按钮：创建 AE 原生样式按钮、布局重定位与动作分发。
 * <p>
 * 移植自 AE2Things PatternPanel（原生 GuiImgButton/GuiTabButton/GuiScrollbar），
 * 额外在面板顶部保留上传(↑)/召回(←)/轮换(⇄)/OV 覆盖按钮。
 */
public class MergedTerminalPanelHandler {

    public static final int BUTTON_ENCODE_ID = 940;
    public static final int BUTTON_CLEAR_ID = 941;
    public static final int BUTTON_DOUBLE_ID = 942;
    public static final int BUTTON_SUB_ID = 944;
    public static final int BUTTON_BESUB_ID = 945;
    public static final int BUTTON_UPLOAD_ID = 947;
    public static final int BUTTON_RECALL_ID = 948;
    public static final int BUTTON_SWAP_ID = 949;
    public static final int BUTTON_OVERLAY_ID = 950;
    public static final int BUTTON_INVERT_ID = 951;
    public static final int BUTTON_TAB_CRAFT_ID = 952;
    public static final int BUTTON_TAB_PROCESS_ID = 953;

    /** 客户端面板模式/替代/反转/页码状态（服务端容器同步状态，编码包携带） */
    public static boolean mergedCraftingMode = true;
    public static boolean mergedSubstitute = false;
    public static boolean mergedBeSubstitute = false;
    public static boolean mergedInverted = false;
    public static int mergedActivePage = 0;

    public static GuiImgButton btnEncode;
    public static GuiImgButton btnSubEnabled;
    public static GuiImgButton btnSubDisabled;
    public static GuiImgButton btnBeSubEnabled;
    public static GuiImgButton btnBeSubDisabled;
    public static GuiImgButton btnClear;
    public static GuiImgButton btnDouble;
    public static GuiImgButton btnInvert;
    public static GuiTabButton btnTabCraft;
    public static GuiTabButton btnTabProcess;

    public static GuiButton btnUpload;
    public static GuiButton btnRecall;
    public static GuiButton btnSwap;
    public static GuiButton btnOverlay;

    public static final GuiScrollbar processingScrollBar = new GuiScrollbar();

    /** GuiScreen.itemRender（protected static，用于 GuiTabButton 图标渲染） */
    private static final Field ITEM_RENDER;
    static {
        Field f = null;
        try {
            f = GuiScreen.class.getDeclaredField("itemRender");
            f.setAccessible(true);
        } catch (Throwable t) {}
        ITEM_RENDER = f;
    }

    private MergedTerminalPanelHandler() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new MergedTerminalPanelHandler());
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.gui;
        if (!(gui instanceof GuiMergedTerminal)) {
            return;
        }
        mergedCraftingMode = true;
        mergedSubstitute = false;
        mergedBeSubstitute = false;
        mergedInverted = false;
        mergedActivePage = 0;
        ClientState.mergedMachineName = null;

        RenderItem renderItem = getRenderItem();

        btnEncode = new GuiImgButton(0, 0, appeng.api.config.Settings.ACTIONS, appeng.api.config.ActionItems.ENCODE);
        btnEncode.id = BUTTON_ENCODE_ID;
        event.buttonList.add(btnEncode);

        btnTabCraft = new GuiTabButton(
            0,
            0,
            new ItemStack(Blocks.crafting_table),
            GuiText.CraftingPattern.getLocal(),
            renderItem);
        btnTabCraft.id = BUTTON_TAB_CRAFT_ID;
        btnTabProcess = new GuiTabButton(
            0,
            0,
            new ItemStack(Blocks.furnace),
            GuiText.ProcessingPattern.getLocal(),
            renderItem);
        btnTabProcess.id = BUTTON_TAB_PROCESS_ID;
        event.buttonList.add(btnTabCraft);
        event.buttonList.add(btnTabProcess);

        btnSubEnabled = new GuiImgButton(
            0,
            0,
            appeng.api.config.Settings.ACTIONS,
            appeng.api.config.ItemSubstitution.ENABLED);
        btnSubEnabled.setHalfSize(true);
        btnSubEnabled.id = BUTTON_SUB_ID;
        btnSubDisabled = new GuiImgButton(
            0,
            0,
            appeng.api.config.Settings.ACTIONS,
            appeng.api.config.ItemSubstitution.DISABLED);
        btnSubDisabled.setHalfSize(true);
        btnSubDisabled.id = BUTTON_SUB_ID;
        event.buttonList.add(btnSubEnabled);
        event.buttonList.add(btnSubDisabled);

        btnBeSubEnabled = new GuiImgButton(
            0,
            0,
            appeng.api.config.Settings.ACTIONS,
            appeng.api.config.PatternBeSubstitution.ENABLED);
        btnBeSubEnabled.setHalfSize(true);
        btnBeSubEnabled.id = BUTTON_BESUB_ID;
        btnBeSubDisabled = new GuiImgButton(
            0,
            0,
            appeng.api.config.Settings.ACTIONS,
            appeng.api.config.PatternBeSubstitution.DISABLED);
        btnBeSubDisabled.setHalfSize(true);
        btnBeSubDisabled.id = BUTTON_BESUB_ID;
        event.buttonList.add(btnBeSubEnabled);
        event.buttonList.add(btnBeSubDisabled);

        btnClear = new GuiImgButton(0, 0, appeng.api.config.Settings.ACTIONS, appeng.api.config.ActionItems.CLOSE);
        btnClear.setHalfSize(true);
        btnClear.id = BUTTON_CLEAR_ID;
        event.buttonList.add(btnClear);

        btnDouble = new GuiImgButton(0, 0, appeng.api.config.Settings.ACTIONS, appeng.api.config.ActionItems.DOUBLE);
        btnDouble.setHalfSize(true);
        btnDouble.id = BUTTON_DOUBLE_ID;
        event.buttonList.add(btnDouble);

        btnInvert = new GuiImgButton(
            0,
            0,
            appeng.api.config.Settings.ACTIONS,
            appeng.api.config.PatternSlotConfig.C_4_16);
        btnInvert.setHalfSize(true);
        btnInvert.id = BUTTON_INVERT_ID;
        event.buttonList.add(btnInvert);

        btnUpload = new GuiButton(BUTTON_UPLOAD_ID, 0, 0, 12, 12, "\u2191");
        btnRecall = new GuiButton(BUTTON_RECALL_ID, 0, 0, 12, 12, "\u2190");
        btnSwap = new GuiButton(BUTTON_SWAP_ID, 0, 0, 12, 12, "\u21c4");
        btnOverlay = new GuiButton(BUTTON_OVERLAY_ID, 0, 0, 12, 12, "OV");
        event.buttonList.add(btnUpload);
        event.buttonList.add(btnRecall);
        event.buttonList.add(btnSwap);
        event.buttonList.add(btnOverlay);

        processingScrollBar.setHeight(70)
            .setWidth(7)
            .setLeft(MergedPanelLayout.PANEL_X + 6)
            .setTop(MergedPanelLayout.PANEL_Y + 9)
            .setRange(0, 1, 1);
        processingScrollBar.setTexture("ae2_qof", "gui/widget/pattern.png", 242, 0);
    }

    /** 每帧由 GUI drawFG 调用：按当前模式摆放按钮并刷新可见性。按钮使用绝对屏幕坐标，需加 guiLeft/guiTop */
    public static void reposition(int baseX, int baseY, boolean crafting, boolean inverted, int activePage) {
        mergedCraftingMode = crafting;
        mergedInverted = inverted;
        mergedActivePage = activePage;

        btnEncode.xPosition = baseX + MergedPanelLayout.ENCODE_BTN_X;
        btnEncode.yPosition = baseY + MergedPanelLayout.ENCODE_BTN_Y;

        btnTabCraft.visible = crafting;
        btnTabProcess.visible = !crafting;
        btnTabCraft.xPosition = baseX + MergedPanelLayout.TAB_BTN_X;
        btnTabCraft.yPosition = baseY + MergedPanelLayout.TAB_BTN_Y;
        btnTabProcess.xPosition = baseX + MergedPanelLayout.TAB_BTN_X;
        btnTabProcess.yPosition = baseY + MergedPanelLayout.TAB_BTN_Y;

        btnInvert
            .set(inverted ? appeng.api.config.PatternSlotConfig.C_4_16 : appeng.api.config.PatternSlotConfig.C_16_4);

        if (crafting) {
            btnSubEnabled.xPosition = baseX + 291;
            btnSubEnabled.yPosition = baseY + 14;
            btnSubDisabled.xPosition = baseX + 291;
            btnSubDisabled.yPosition = baseY + 14;
            btnBeSubEnabled.xPosition = baseX + 291;
            btnBeSubEnabled.yPosition = baseY + 24;
            btnBeSubDisabled.xPosition = baseX + 291;
            btnBeSubDisabled.yPosition = baseY + 24;
            btnClear.xPosition = baseX + 281;
            btnClear.yPosition = baseY + 14;
            btnDouble.xPosition = -9000;
            btnDouble.yPosition = -9000;
            btnInvert.xPosition = -9000;
            btnInvert.yPosition = -9000;
        } else {
            final int offset = inverted ? 18 * -3 : 0;
            btnSubEnabled.xPosition = baseX + 306 + offset;
            btnSubEnabled.yPosition = baseY + 10;
            btnSubDisabled.xPosition = baseX + 306 + offset;
            btnSubDisabled.yPosition = baseY + 10;
            btnBeSubEnabled.xPosition = baseX + 306 + offset;
            btnBeSubEnabled.yPosition = baseY + 69;
            btnBeSubDisabled.xPosition = baseX + 306 + offset;
            btnBeSubDisabled.yPosition = baseY + 69;
            btnDouble.xPosition = baseX + 306 + offset;
            btnDouble.yPosition = baseY + 20;
            btnClear.xPosition = baseX + 296 + offset;
            btnClear.yPosition = baseY + 10;
            btnInvert.xPosition = baseX + 296 + offset;
            btnInvert.yPosition = baseY + 20;
            processingScrollBar.setCurrentScroll(activePage);
        }

        btnSubEnabled.setVisibility(mergedSubstitute);
        btnSubDisabled.setVisibility(!mergedSubstitute);
        btnBeSubEnabled.setVisibility(mergedBeSubstitute);
        btnBeSubDisabled.setVisibility(!mergedBeSubstitute);
        processingScrollBar.setVisible(!crafting);

        int ebx = baseX + MergedPanelLayout.EXTRA_BTN_X;
        btnUpload.xPosition = ebx;
        btnUpload.yPosition = baseY + MergedPanelLayout.EXTRA_BTN_Y0;
        btnRecall.xPosition = ebx;
        btnRecall.yPosition = baseY + MergedPanelLayout.EXTRA_BTN_Y0 + MergedPanelLayout.EXTRA_BTN_STEP;
        btnSwap.xPosition = ebx;
        btnSwap.yPosition = baseY + MergedPanelLayout.EXTRA_BTN_Y0 + MergedPanelLayout.EXTRA_BTN_STEP * 2;
        btnOverlay.xPosition = ebx;
        btnOverlay.yPosition = baseY + MergedPanelLayout.EXTRA_BTN_Y0 + MergedPanelLayout.EXTRA_BTN_STEP * 3;
        btnOverlay.displayString = OverlayConfig.isEnabled() ? "OV" : "--";
    }

    public static void drawScrollbar(AEBaseGui gui) {
        if (!mergedCraftingMode) {
            processingScrollBar.draw(gui);
        }
    }

    /** 返回 true 表示滚动条已消费点击（翻页） */
    public static boolean handleScrollbarClick(AEBaseGui gui, int x, int y) {
        if (mergedCraftingMode) return false;
        GuiContainerAccessor acc = (GuiContainerAccessor) gui;
        int cur = processingScrollBar.getCurrentScroll();
        processingScrollBar.click(gui, x - acc.getGuiLeft(), y - acc.getGuiTop());
        if (cur != processingScrollBar.getCurrentScroll()) {
            changeActivePage(gui, processingScrollBar.getCurrentScroll());
            return true;
        }
        return false;
    }

    /** 返回 true 表示滚动条已消费滚轮（翻页） */
    public static boolean handleScrollbarWheel(AEBaseGui gui, int x, int y, int wheel) {
        if (mergedCraftingMode) return false;
        GuiContainerAccessor acc = (GuiContainerAccessor) gui;
        if (processingScrollBar.contains(x - acc.getGuiLeft(), y - acc.getGuiTop())) {
            int cur = processingScrollBar.getCurrentScroll();
            processingScrollBar.wheel(wheel);
            if (cur != processingScrollBar.getCurrentScroll()) {
                changeActivePage(gui, processingScrollBar.getCurrentScroll());
                return true;
            }
        }
        return false;
    }

    private static void changeActivePage(AEBaseGui gui, int page) {
        mergedActivePage = page;
        ModNetwork.CHANNEL
            .sendToServer(MergedTerminalActionPacket.value(MergedTerminalActionPacket.Action.SET_PAGE, page));
        if (gui.inventorySlots instanceof IMergedPatternTerminal merged) {
            merged.setMergedActivePage(page);
        }
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
                ModNetwork.CHANNEL
                    .sendToServer(MergedTerminalActionPacket.simple(MergedTerminalActionPacket.Action.CLEAR));
                break;
            case BUTTON_DOUBLE_ID:
                ModNetwork.CHANNEL
                    .sendToServer(MergedTerminalActionPacket.simple(MergedTerminalActionPacket.Action.DOUBLE));
                break;
            case BUTTON_TAB_CRAFT_ID:
                mergedCraftingMode = true;
                sendModeToggle();
                break;
            case BUTTON_TAB_PROCESS_ID:
                mergedCraftingMode = false;
                sendModeToggle();
                break;
            case BUTTON_SUB_ID:
                mergedSubstitute = !mergedSubstitute;
                ModNetwork.CHANNEL.sendToServer(
                    MergedTerminalActionPacket
                        .flag(MergedTerminalActionPacket.Action.SET_SUBSTITUTE, mergedSubstitute));
                break;
            case BUTTON_BESUB_ID:
                mergedBeSubstitute = !mergedBeSubstitute;
                ModNetwork.CHANNEL.sendToServer(
                    MergedTerminalActionPacket
                        .flag(MergedTerminalActionPacket.Action.SET_BE_SUBSTITUTE, mergedBeSubstitute));
                break;
            case BUTTON_INVERT_ID:
                mergedInverted = !mergedInverted;
                ModNetwork.CHANNEL.sendToServer(
                    MergedTerminalActionPacket.flag(MergedTerminalActionPacket.Action.SET_INVERTED, mergedInverted));
                if (gui != null && gui.inventorySlots instanceof IMergedPatternTerminal merged) {
                    merged.setMergedInverted(mergedInverted);
                }
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

    private static void sendModeToggle() {
        ModNetwork.CHANNEL.sendToServer(
            MergedTerminalActionPacket.flag(MergedTerminalActionPacket.Action.SET_MODE, mergedCraftingMode));
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof GuiContainer gc && gc.inventorySlots instanceof IMergedPatternTerminal merged) {
            merged.setMergedCraftingMode(mergedCraftingMode);
        }
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

    private static RenderItem getRenderItem() {
        try {
            if (ITEM_RENDER != null) {
                Object v = ITEM_RENDER.get(null);
                if (v instanceof RenderItem) {
                    return (RenderItem) v;
                }
            }
        } catch (Throwable ignored) {}
        return new RenderItem();
    }
}
