package com.wztwzt.ae2_qof.wireless.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.WirelessActionPacket;
import com.wztwzt.ae2_qof.wireless.TileWirelessTransceiver;
import com.wztwzt.ae2_qof.wireless.WirelessData;

public class GuiWireless extends GuiContainer {

    private static final int BTN_ADD = 0;
    private static final int BTN_REMOVE = 1;
    private static final int BTN_HIGHLIGHT = 2;
    private static final int BTN_SENDER = 3;
    private static final int BTN_RECEIVER = 4;
    private static final int BTN_DISCONN = 5;

    private static final int BTN_CONFIRM_DELETE = 10;
    private static final int BTN_CANCEL_DELETE = 11;

    private static final int LIST_X = 8;
    private static final int LIST_Y = 38;
    private static final int LIST_WIDTH = 80;
    private static final int LIST_HEIGHT = 88;
    private static final int ROW_HEIGHT = 12;
    private static final int MAX_VISIBLE_ROWS = 7;

    private static final int STATUS_X = 96;

    private static final int BTN_AREA_X = 7;
    private static final int BTN_AREA_Y = 130;
    private static final int BTN_W = 53;
    private static final int BTN_H = 12;
    private static final int BTN_GAP = 2;

    private static final int SCROLLBAR_X = LIST_X + LIST_WIDTH + 2;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MIN_HEIGHT = 12;

    private final TileWirelessTransceiver tile;
    private GuiTextField channelField;
    private final List<String> channels = new ArrayList<String>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;

    private boolean showDeleteConfirm = false;
    private boolean draggingScrollbar = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

    public GuiWireless(EntityPlayer player, TileWirelessTransceiver te) {
        super(new ContainerWireless(player, te));
        this.tile = te;
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        channelField = new GuiTextField(fontRendererObj, guiLeft + LIST_X, guiTop + 24, LIST_WIDTH, 12);
        channelField.setMaxStringLength(32);
        channelField.setFocused(false);
        channelField.setText(tile.getFrequency() != null ? tile.getFrequency() : "");

        int btnX1 = guiLeft + BTN_AREA_X;
        int btnX2 = guiLeft + BTN_AREA_X + BTN_W + BTN_GAP;
        int btnX3 = guiLeft + BTN_AREA_X + (BTN_W + BTN_GAP) * 2;
        int btnY1 = guiTop + BTN_AREA_Y;
        int btnY2 = guiTop + BTN_AREA_Y + BTN_H + BTN_GAP;

        buttonList.add(
            new GuiButton(
                BTN_ADD,
                btnX1,
                btnY1,
                BTN_W,
                BTN_H,
                StatCollector.translateToLocal("gui.ae2_qof.add")));
        buttonList.add(
            new GuiButton(
                BTN_REMOVE,
                btnX2,
                btnY1,
                BTN_W,
                BTN_H,
                StatCollector.translateToLocal("gui.ae2_qof.wireless.remove")));
        buttonList.add(
            new GuiButton(
                BTN_HIGHLIGHT,
                btnX3,
                btnY1,
                BTN_W,
                BTN_H,
                StatCollector.translateToLocal("gui.ae2_qof.wireless.highlight")));
        buttonList.add(
            new GuiButton(
                BTN_SENDER,
                btnX1,
                btnY2,
                BTN_W,
                BTN_H,
                StatCollector.translateToLocal("gui.ae2_qof.wireless.mode.sender")));
        buttonList.add(
            new GuiButton(
                BTN_RECEIVER,
                btnX2,
                btnY2,
                BTN_W,
                BTN_H,
                StatCollector.translateToLocal("gui.ae2_qof.wireless.mode.receiver")));
        buttonList.add(
            new GuiButton(
                BTN_DISCONN,
                btnX3,
                btnY2,
                BTN_W,
                BTN_H,
                StatCollector.translateToLocal("gui.ae2_qof.wireless.disconnect")));

        refreshChannels();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF161618);
        drawHorizontalLine(guiLeft, guiLeft + xSize - 1, guiTop, 0xFFFFFFFF);
        drawHorizontalLine(guiLeft, guiLeft + xSize - 1, guiTop + ySize - 1, 0xFFFFFFFF);
        drawVerticalLine(guiLeft, guiTop, guiTop + ySize - 1, 0xFFFFFFFF);
        drawVerticalLine(guiLeft + xSize - 1, guiTop, guiTop + ySize - 1, 0xFFFFFFFF);

        String title = StatCollector.translateToLocal("tile.wireless_transceiver.name");
        fontRendererObj.drawStringWithShadow(title, guiLeft + 8, guiTop + 4, 0xFF666666);

        fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.ae2_qof.wireless.freq") + ":",
            guiLeft + LIST_X,
            guiTop + 14,
            0xFFAAAAAA);

        if (channelField != null) {
            channelField.drawTextBox();
        }

        drawRect(
            guiLeft + LIST_X,
            guiTop + LIST_Y,
            guiLeft + LIST_X + LIST_WIDTH,
            guiTop + LIST_Y + LIST_HEIGHT,
            0xFF000000);
        drawHorizontalLine(guiLeft + LIST_X, guiLeft + LIST_X + LIST_WIDTH - 1, guiTop + LIST_Y, 0xFFAAAAAA);
        drawHorizontalLine(
            guiLeft + LIST_X,
            guiLeft + LIST_X + LIST_WIDTH - 1,
            guiTop + LIST_Y + LIST_HEIGHT - 1,
            0xFFAAAAAA);
        drawVerticalLine(guiLeft + LIST_X, guiTop + LIST_Y, guiTop + LIST_Y + LIST_HEIGHT - 1, 0xFFAAAAAA);
        drawVerticalLine(
            guiLeft + LIST_X + LIST_WIDTH - 1,
            guiTop + LIST_Y,
            guiTop + LIST_Y + LIST_HEIGHT - 1,
            0xFFAAAAAA);

        int startIdx = scrollOffset;
        int endIdx = Math.min(startIdx + MAX_VISIBLE_ROWS, channels.size());
        for (int i = startIdx; i < endIdx; i++) {
            int row = i - startIdx;
            int itemY = guiTop + LIST_Y + 1 + row * ROW_HEIGHT;
            int itemYBottom = itemY + ROW_HEIGHT - 1;

            if (i == selectedIndex) {
                drawRect(guiLeft + LIST_X + 1, itemY, guiLeft + LIST_X + LIST_WIDTH - 1, itemYBottom, 0xFF26B938);
                fontRendererObj.drawString(channels.get(i), guiLeft + LIST_X + 3, itemY + 2, 0xFFFFFFFF);
            } else {
                fontRendererObj.drawString(channels.get(i), guiLeft + LIST_X + 3, itemY + 2, 0xFFE0E0E0);
            }
        }

        if (channels.size() > MAX_VISIBLE_ROWS) {
            int totalRows = channels.size();
            int scrollbarHeight = Math
                .max(SCROLLBAR_MIN_HEIGHT, (int) ((float) MAX_VISIBLE_ROWS / totalRows * LIST_HEIGHT));
            int maxScroll = totalRows - MAX_VISIBLE_ROWS;
            int scrollbarY = guiTop + LIST_Y;
            if (maxScroll > 0) {
                scrollbarY = guiTop + LIST_Y
                    + (int) ((float) scrollOffset / maxScroll * (LIST_HEIGHT - scrollbarHeight));
            }
            int sbx = guiLeft + SCROLLBAR_X;
            drawRect(sbx, guiTop + LIST_Y, sbx + SCROLLBAR_WIDTH, guiTop + LIST_Y + LIST_HEIGHT, 0xFF333333);
            drawRect(sbx, scrollbarY, sbx + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, 0xFF888888);
        }

        int sx = guiLeft + STATUS_X;
        int sy = guiTop + LIST_Y;

        String freqLabel = StatCollector.translateToLocal("gui.ae2_qof.wireless.freq") + ": ";
        String freqVal = tile.getFrequency() != null && !tile.getFrequency()
            .isEmpty() ? tile.getFrequency()
                : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gui.ae2_qof.wireless.none");
        fontRendererObj.drawString(freqLabel, sx, sy, 0xFFAAAAAA);
        fontRendererObj.drawString(freqVal, sx + fontRendererObj.getStringWidth(freqLabel), sy, 0xFFFFFFFF);

        String modeLabel = StatCollector.translateToLocal("gui.ae2_qof.wireless.mode") + ": ";
        boolean isSender = tile.isMode();
        String modeVal = StatCollector.translateToLocal(
            isSender ? "gui.ae2_qof.wireless.mode.sender"
                : "gui.ae2_qof.wireless.mode.receiver");
        fontRendererObj.drawString(modeLabel, sx, sy + 14, 0xFFAAAAAA);
        fontRendererObj.drawString(
            modeVal,
            sx + fontRendererObj.getStringWidth(modeLabel),
            sy + 14,
            isSender ? 0xFF3366CC : 0xFFFFCC00);

        String connLabel = StatCollector.translateToLocal("gui.ae2_qof.wireless.status") + ": ";
        boolean connected = tile.isConnected();
        boolean paused = tile.isPaused();
        String connVal;
        int connColor;
        if (paused) {
            connVal = StatCollector.translateToLocal("gui.ae2_qof.wireless.status.paused");
            connColor = 0xFFFFAA00;
        } else if (connected) {
            connVal = StatCollector.translateToLocal("gui.ae2_qof.wireless.status.connected");
            connColor = 0xFF26B938;
        } else {
            connVal = StatCollector.translateToLocal("gui.ae2_qof.wireless.status.disconnected");
            connColor = 0xFFFF5555;
        }
        fontRendererObj.drawString(connLabel, sx, sy + 28, 0xFFAAAAAA);
        fontRendererObj.drawString(connVal, sx + fontRendererObj.getStringWidth(connLabel), sy + 28, connColor);

        if (showDeleteConfirm) {
            drawDeleteConfirmDialog(mouseX, mouseY);
        }
    }

    private void drawDeleteConfirmDialog(int mouseX, int mouseY) {
        drawRect(0, 0, this.width, this.height, 0x80000000);

        int dlgW = 120;
        int dlgH = 60;
        int dlgX = (this.width - dlgW) / 2;
        int dlgY = (this.height - dlgH) / 2;

        drawRect(dlgX, dlgY, dlgX + dlgW, dlgY + dlgH, 0xFF161618);
        drawHorizontalLine(dlgX, dlgX + dlgW - 1, dlgY, 0xFFFFFFFF);
        drawHorizontalLine(dlgX, dlgX + dlgW - 1, dlgY + dlgH - 1, 0xFFFFFFFF);
        drawVerticalLine(dlgX, dlgY, dlgY + dlgH - 1, 0xFFFFFFFF);
        drawVerticalLine(dlgX + dlgW - 1, dlgY, dlgY + dlgH - 1, 0xFFFFFFFF);

        String confirmTitle = StatCollector.translateToLocal("gui.ae2_qof.wireless.confirm_delete");
        int titleW = fontRendererObj.getStringWidth(confirmTitle);
        fontRendererObj.drawStringWithShadow(confirmTitle, dlgX + (dlgW - titleW) / 2, dlgY + 8, 0xFFFFFFFF);

        int yesX = dlgX + 15;
        int noX = dlgX + dlgW - 55;
        int btnY = dlgY + 35;
        int btnW = 40;
        int btnH = 14;

        boolean yesHover = mouseX >= yesX && mouseX <= yesX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean noHover = mouseX >= noX && mouseX <= noX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        drawRect(yesX, btnY, yesX + btnW, btnY + btnH, yesHover ? 0xFF26B938 : 0xFF000000);
        drawHorizontalLine(yesX, yesX + btnW - 1, btnY, 0xFFAAAAAA);
        drawHorizontalLine(yesX, yesX + btnW - 1, btnY + btnH - 1, 0xFFAAAAAA);
        drawVerticalLine(yesX, btnY, btnY + btnH - 1, 0xFFAAAAAA);
        drawVerticalLine(yesX + btnW - 1, btnY, btnY + btnH - 1, 0xFFAAAAAA);
        String yesText = StatCollector.translateToLocal("gui.yes");
        int yesTw = fontRendererObj.getStringWidth(yesText);
        fontRendererObj.drawString(yesText, yesX + (btnW - yesTw) / 2, btnY + 3, 0xFFFFFFFF);

        drawRect(noX, btnY, noX + btnW, btnY + btnH, noHover ? 0xFFFF5555 : 0xFF000000);
        drawHorizontalLine(noX, noX + btnW - 1, btnY, 0xFFAAAAAA);
        drawHorizontalLine(noX, noX + btnW - 1, btnY + btnH - 1, 0xFFAAAAAA);
        drawVerticalLine(noX, btnY, btnY + btnH - 1, 0xFFAAAAAA);
        drawVerticalLine(noX + btnW - 1, btnY, btnY + btnH - 1, 0xFFAAAAAA);
        String noText = StatCollector.translateToLocal("gui.no");
        int noTw = fontRendererObj.getStringWidth(noText);
        fontRendererObj.drawString(noText, noX + (btnW - noTw) / 2, btnY + 3, 0xFFFFFFFF);
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (showDeleteConfirm) {
            handleDeleteConfirmClick(btn);
            return;
        }

        switch (btn.id) {
            case BTN_ADD: {
                String text = channelField.getText()
                    .trim();
                if (!text.isEmpty()) {
                    sendAction(WirelessActionPacket.ACTION_ADD_CHANNEL, text, false);
                    if (!channels.contains(text)) {
                        channels.add(text);
                    }
                    selectedIndex = channels.indexOf(text);
                }
                break;
            }
            case BTN_REMOVE: {
                showDeleteConfirm = true;
                break;
            }
            case BTN_SENDER: {
                if (!tile.isMode()) {
                    sendAction(WirelessActionPacket.ACTION_SET_MODE, null, true);
                }
                break;
            }
            case BTN_RECEIVER: {
                if (tile.isMode()) {
                    sendAction(WirelessActionPacket.ACTION_SET_MODE, null, false);
                }
                break;
            }
            case BTN_DISCONN: {
                sendAction(WirelessActionPacket.ACTION_DISCONNECT, null, false);
                break;
            }
            case BTN_HIGHLIGHT: {
                sendAction(WirelessActionPacket.ACTION_TOGGLE_HIGHLIGHT, null, false);
                break;
            }
        }
    }

    private void handleDeleteConfirmClick(GuiButton btn) {
        if (btn.id == BTN_CONFIRM_DELETE) {
            String channelToRemove = (selectedIndex >= 0 && selectedIndex < channels.size())
                ? channels.get(selectedIndex)
                : null;
            sendAction(WirelessActionPacket.ACTION_REMOVE_CHANNEL, channelToRemove, false);
            if (selectedIndex >= 0 && selectedIndex < channels.size()) {
                channels.remove(selectedIndex);
            }
            selectedIndex = -1;
            channelField.setText("");
            showDeleteConfirm = false;
        } else if (btn.id == BTN_CANCEL_DELETE) {
            showDeleteConfirm = false;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (showDeleteConfirm) {
            int dlgW = 120;
            int dlgH = 60;
            int dlgX = (this.width - dlgW) / 2;
            int dlgY = (this.height - dlgH) / 2;
            int yesX = dlgX + 15;
            int noX = dlgX + dlgW - 55;
            int btnY = dlgY + 35;
            int btnW = 40;
            int btnH = 14;

            if (mouseX >= yesX && mouseX <= yesX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                String channelToRemove = (selectedIndex >= 0 && selectedIndex < channels.size())
                    ? channels.get(selectedIndex)
                    : null;
                sendAction(WirelessActionPacket.ACTION_REMOVE_CHANNEL, channelToRemove, false);
                if (selectedIndex >= 0 && selectedIndex < channels.size()) {
                    channels.remove(selectedIndex);
                }
                selectedIndex = -1;
                channelField.setText("");
                showDeleteConfirm = false;
                return;
            }
            if (mouseX >= noX && mouseX <= noX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                showDeleteConfirm = false;
                return;
            }
            return;
        }

        if (channelField != null) channelField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);

        int sbx = guiLeft + SCROLLBAR_X;
        if (mouseX >= sbx && mouseX <= sbx + SCROLLBAR_WIDTH
            && mouseY >= guiTop + LIST_Y
            && mouseY <= guiTop + LIST_Y + LIST_HEIGHT
            && channels.size() > MAX_VISIBLE_ROWS) {
            draggingScrollbar = true;
            dragStartY = mouseY;
            dragStartScroll = scrollOffset;
            return;
        }

        int listAbsX = guiLeft + LIST_X;
        int listAbsY = guiTop + LIST_Y;
        if (mouseX >= listAbsX && mouseX <= listAbsX + LIST_WIDTH
            && mouseY >= listAbsY
            && mouseY <= listAbsY + LIST_HEIGHT) {
            int row = (mouseY - listAbsY - 1) / ROW_HEIGHT;
            int idx = row + scrollOffset;
            if (idx >= 0 && idx < channels.size()) {
                selectedIndex = idx;
                channelField.setText(channels.get(idx));
                sendAction(WirelessActionPacket.ACTION_SET_FREQUENCY, channels.get(idx), false);
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingScrollbar && channels.size() > MAX_VISIBLE_ROWS) {
            int deltaY = mouseY - dragStartY;
            int totalRows = channels.size();
            int maxScroll = totalRows - MAX_VISIBLE_ROWS;
            int scrollRange = LIST_HEIGHT
                - Math.max(SCROLLBAR_MIN_HEIGHT, (int) ((float) MAX_VISIBLE_ROWS / totalRows * LIST_HEIGHT));
            if (scrollRange > 0) {
                int newScroll = dragStartScroll + (int) ((float) deltaY / scrollRange * maxScroll);
                scrollOffset = Math.max(0, Math.min(maxScroll, newScroll));
            }
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        if (draggingScrollbar && !Mouse.isButtonDown(0)) {
            draggingScrollbar = false;
        }
        int scroll = Mouse.getDWheel();
        if (scroll != 0 && channels.size() > MAX_VISIBLE_ROWS) {
            int maxScroll = channels.size() - MAX_VISIBLE_ROWS;
            if (scroll > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else {
                scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (showDeleteConfirm) return;

        if (channelField != null && channelField.isFocused()) {
            if (channelField.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (channelField != null) channelField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (channelField != null && !showDeleteConfirm) {
            channelField.drawTextBox();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void sendAction(int action, String channel, boolean modeValue) {
        ModNetwork.CHANNEL
            .sendToServer(new WirelessActionPacket(action, tile.xCoord, tile.yCoord, tile.zCoord, channel, modeValue));
    }

    private void refreshChannels() {
        channels.clear();
        channels.addAll(
            WirelessData.instance()
                .getAllFrequencies());
        if (tile.getFrequency() != null && !tile.getFrequency()
            .isEmpty()) {
            selectedIndex = channels.indexOf(tile.getFrequency());
            if (selectedIndex >= 0) {
                if (selectedIndex < scrollOffset) {
                    scrollOffset = selectedIndex;
                } else if (selectedIndex >= scrollOffset + MAX_VISIBLE_ROWS) {
                    scrollOffset = selectedIndex - MAX_VISIBLE_ROWS + 1;
                }
            }
        }
    }

    public void syncChannelList(List<String> serverChannels) {
        channels.clear();
        channels.addAll(serverChannels);
        if (tile.getFrequency() != null && !tile.getFrequency()
            .isEmpty()) {
            selectedIndex = channels.indexOf(tile.getFrequency());
            if (selectedIndex >= 0) {
                if (selectedIndex < scrollOffset) {
                    scrollOffset = selectedIndex;
                } else if (selectedIndex >= scrollOffset + MAX_VISIBLE_ROWS) {
                    scrollOffset = selectedIndex - MAX_VISIBLE_ROWS + 1;
                }
            }
        }
    }
}
