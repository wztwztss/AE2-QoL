package com.wztwzt.ae2_qof.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;

import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.WirelessDistributorActionPacket;
import com.wztwzt.ae2_qof.network.WirelessDistributorRequestPacket;
import com.wztwzt.ae2_qof.network.WirelessDistributorSyncPacket;
import com.wztwzt.ae2_qof.wireless.distributor.ContainerWirelessDistributor;
import com.wztwzt.ae2_qof.wireless.distributor.TileWirelessDistributor;

import appeng.api.util.AEColor;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 无线分配器列表界面。
 *
 * <p>
 * 没有物品槽，列表由服务端 {@link WirelessDistributorSyncPacket} 下发；点击行只做选择，
 * 绑定/解绑由底部按钮显式触发，避免误点把机器并进网络。
 */
@SideOnly(Side.CLIENT)
public class GuiWirelessDistributor extends GuiContainer {

    public static GuiWirelessDistributor active;

    private static final int X_SIZE = 280;
    private static final int Y_SIZE = 212;
    private static final int ROW_HEIGHT = 14;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 58;
    private static final int LIST_WIDTH = 264;
    private static final int LIST_HEIGHT = 84;
    private static final int VISIBLE_ROWS = LIST_HEIGHT / ROW_HEIGHT;

    private final TileWirelessDistributor tile;
    private WirelessDistributorSyncPacket data;
    private GuiTextField filterField;
    private int selected = -1;
    private int scrollRow;
    private boolean requestSent;

    public GuiWirelessDistributor(InventoryPlayer inventoryPlayer, TileWirelessDistributor tile) {
        super(new ContainerWirelessDistributor(inventoryPlayer, tile));
        this.tile = tile;
        this.xSize = X_SIZE;
        this.ySize = Y_SIZE;
    }

    @Override
    public void initGui() {
        super.initGui();
        active = this;
        this.buttonList.clear();
        int left = this.guiLeft;
        int top = this.guiTop;
        this.buttonList.add(new GuiButton(0, left + 8, top + 158, 60, 18, tr("gui.ae2_qof.wireless_distributor.refresh")));
        this.buttonList.add(new GuiButton(1, left + 72, top + 158, 60, 18, tr("gui.ae2_qof.wireless_distributor.scan")));
        this.buttonList.add(new GuiButton(2, left + 136, top + 158, 60, 18, tr("gui.ae2_qof.wireless_distributor.range")));
        this.buttonList.add(new GuiButton(3, left + 200, top + 158, 72, 18, tr("gui.ae2_qof.wireless_distributor.color")));
        this.buttonList.add(new GuiButton(4, left + 8, top + 182, 84, 18, tr("gui.ae2_qof.wireless_distributor.link")));
        this.buttonList.add(
            new GuiButton(5, left + 96, top + 182, 84, 18, tr("gui.ae2_qof.wireless_distributor.unlink")));
        this.buttonList.add(new GuiButton(6, left + 184, top + 182, 40, 18, "<"));
        this.buttonList.add(new GuiButton(7, left + 228, top + 182, 44, 18, ">"));

        this.filterField = new GuiTextField(this.fontRendererObj, left + 52, top + 31, 220, 16);
        this.filterField.setMaxStringLength(64);
        this.filterField.setFocused(false);
        this.filterField.setText("");
        this.requestSent = false;
        this.requestList();
    }

    @Override
    public void onGuiClosed() {
        if (active == this) active = null;
        super.onGuiClosed();
    }

    public static void applySync(WirelessDistributorSyncPacket packet) {
        if (active == null) return;
        active.data = packet;
        active.clampScroll();
        active.selected = -1;
    }

    private void requestList() {
        if (tile == null || requestSent) return;
        requestSent = true;
        ModNetwork.CHANNEL.sendToServer(
            new WirelessDistributorRequestPacket(tile.xCoord, tile.yCoord, tile.zCoord));
        requestSent = false;
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String trf(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    private List<WirelessDistributorSyncPacket.Entry> filteredEntries() {
        List<WirelessDistributorSyncPacket.Entry> result = new ArrayList<WirelessDistributorSyncPacket.Entry>();
        if (data == null) return result;
        String filter = filterField != null ? filterField.getText()
            .trim()
            .toLowerCase(Locale.ROOT) : "";
        for (WirelessDistributorSyncPacket.Entry entry : data.getEntries()) {
            if (filter.isEmpty()) {
                result.add(entry);
                continue;
            }
            String pos = entry.dimension + ":" + entry.x + "," + entry.y + "," + entry.z;
            String status = statusLabel(entry.status);
            if (entry.name.toLowerCase(Locale.ROOT)
                .contains(filter) || pos.contains(filter)
                || status.toLowerCase(Locale.ROOT)
                    .contains(filter)) {
                result.add(entry);
            }
        }
        return result;
    }

    private void clampScroll() {
        int size = filteredEntries().size();
        int max = Math.max(0, size - VISIBLE_ROWS);
        if (scrollRow > max) scrollRow = max;
        if (scrollRow < 0) scrollRow = 0;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            this.requestList();
            return;
        }
        if (button.id == 1) {
            this.sendAction(WirelessDistributorActionPacket.ACTION_SCAN, 0, 0, 0);
            return;
        }
        if (button.id == 2) {
            this.sendAction(WirelessDistributorActionPacket.ACTION_RANGE, 0, 0, 0);
            return;
        }
        if (button.id == 3) {
            this.sendAction(WirelessDistributorActionPacket.ACTION_COLOR, 0, 0, 0);
            return;
        }
        if (button.id == 6) {
            this.scrollRow = Math.max(0, this.scrollRow - VISIBLE_ROWS);
            return;
        }
        if (button.id == 7) {
            this.scrollRow = Math.min(Math.max(0, filteredEntries().size() - VISIBLE_ROWS), this.scrollRow + VISIBLE_ROWS);
            return;
        }

        WirelessDistributorSyncPacket.Entry entry = this.selectedEntry();
        if (entry == null) return;
        if (button.id == 4) {
            this.sendAction(WirelessDistributorActionPacket.ACTION_LINK, entry.x, entry.y, entry.z);
        } else if (button.id == 5) {
            this.sendAction(WirelessDistributorActionPacket.ACTION_UNLINK, entry.x, entry.y, entry.z);
        }
    }

    private void sendAction(int action, int targetX, int targetY, int targetZ) {
        if (tile == null) return;
        ModNetwork.CHANNEL.sendToServer(
            new WirelessDistributorActionPacket(
                action,
                tile.xCoord,
                tile.yCoord,
                tile.zCoord,
                targetX,
                targetY,
                targetZ));
    }

    private WirelessDistributorSyncPacket.Entry selectedEntry() {
        List<WirelessDistributorSyncPacket.Entry> entries = filteredEntries();
        int index = scrollRow + selected;
        if (selected < 0 || index < 0 || index >= entries.size()) return null;
        return entries.get(index);
    }

    private int hoveredRow(int mouseX, int mouseY) {
        int localX = mouseX - this.guiLeft;
        int localY = mouseY - this.guiTop;
        if (localX < LIST_X || localX > LIST_X + LIST_WIDTH) return -1;
        if (localY < LIST_Y || localY >= LIST_Y + VISIBLE_ROWS * ROW_HEIGHT) return -1;
        int row = (localY - LIST_Y) / ROW_HEIGHT;
        int index = scrollRow + row;
        return index < filteredEntries().size() ? row : -1;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (filterField != null) this.filterField.updateCursorCounter();
        this.updateButtons();
    }

    private void updateButtons() {
        boolean hasData = data != null;
        WirelessDistributorSyncPacket.Entry entry = this.selectedEntry();
        for (Object obj : this.buttonList) {
            GuiButton button = (GuiButton) obj;
            if (button.id == 0 || button.id == 1 || button.id == 2 || button.id == 3) {
                button.enabled = hasData;
            } else if (button.id == 6) {
                button.enabled = this.scrollRow > 0;
            } else if (button.id == 7) {
                button.enabled = this.scrollRow + VISIBLE_ROWS < this.filteredEntries()
                    .size();
            } else if (button.id == 4) {
                button.enabled = entry != null
                    && entry.status == WirelessDistributorSyncPacket.STATUS_AVAILABLE
                    && data.getLinkedCount() < TileWirelessDistributor.MAX_SIZE;
            } else if (button.id == 5) {
                button.enabled = entry != null && entry.status == WirelessDistributorSyncPacket.STATUS_LINKED;
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int localX = Mouse.getEventX() * this.width / this.mc.displayWidth - this.guiLeft;
        int localY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1 - this.guiTop;
        if (localX < LIST_X || localX > LIST_X + LIST_WIDTH || localY < LIST_Y || localY > LIST_Y + LIST_HEIGHT) {
            return;
        }
        this.scrollRow += wheel > 0 ? -1 : 1;
        this.clampScroll();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.filterField != null) {
            this.filterField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        int row = this.hoveredRow(mouseX, mouseY);
        if (row >= 0) {
            this.selected = row;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.filterField != null && this.filterField.isFocused()) {
            if (keyCode == 1) {
                this.mc.displayGuiScreen(null);
                return;
            }
            if (keyCode == 28) {
                this.filterField.setFocused(false);
                return;
            }
            if (this.filterField.textboxKeyTyped(typedChar, keyCode)) {
                this.clampScroll();
                this.selected = -1;
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(this.guiLeft, this.guiTop, this.guiLeft + this.xSize, this.guiTop + this.ySize, 0xFF1A1A1A);
        drawRect(this.guiLeft + 1, this.guiTop + 1, this.guiLeft + this.xSize - 1, this.guiTop + this.ySize - 1, 0xFF303030);
        drawRect(
            this.guiLeft + LIST_X - 2,
            this.guiTop + LIST_Y - 2,
            this.guiLeft + LIST_X + LIST_WIDTH,
            this.guiTop + LIST_Y + LIST_HEIGHT,
            0xFF101010);
        if (this.filterField != null) this.filterField.drawTextBox();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRendererObj.drawString(
            tr("gui.ae2_qof.wireless_distributor.title"),
            8,
            7,
            0xFFFFFF);
        this.fontRendererObj.drawString(tr("gui.ae2_qof.wireless_distributor.filter") + ":", 8, 35, 0xA0A0A0);

        if (data == null) {
            this.fontRendererObj.drawString(
                tr("gui.ae2_qof.wireless_distributor.waiting"),
                10,
                LIST_Y + 8,
                0xC0C0C0);
        } else {
            String colorName = AEColor.fromOrdinal(data.getColor())
                .name();
            String range = data.getRadius() == 0 ? "1x1" : "3x3";
            this.fontRendererObj.drawString(
                trf("gui.ae2_qof.wireless_distributor.info", data.getLinkedCount(), TileWirelessDistributor.MAX_SIZE,
                    data.getUsedChannels(), data.getMaxChannels(), colorName, range),
                8,
                18,
                0xB0E0B0);
            this.fontRendererObj.drawString(
                tr("gui.ae2_qof.wireless_distributor.columns"),
                10,
                LIST_Y - 11,
                0x808080);
            this.drawEntryList();
        }
    }

    private void drawEntryList() {
        List<WirelessDistributorSyncPacket.Entry> entries = this.filteredEntries();
        this.clampScroll();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = this.scrollRow + row;
            if (index >= entries.size()) break;
            WirelessDistributorSyncPacket.Entry entry = entries.get(index);
            int y = LIST_Y + row * ROW_HEIGHT;
            if (row == this.selected) {
                drawRect(LIST_X - 1, y - 1, LIST_X + LIST_WIDTH - 1, y + ROW_HEIGHT - 1, 0x5533AA55);
            }
            AEColor color = AEColor.fromOrdinal(entry.color);
            int rgb = (color.whiteVariant & 0xFFFFFF) | 0xFF000000;
            drawRect(LIST_X, y + 2, LIST_X + 10, y + 12, rgb);
            String name = entry.name;
            if (name.length() > 24) name = name.substring(0, 24) + "...";
            this.fontRendererObj.drawString(name, LIST_X + 14, y + 2, 0xFFFFFF);
            String pos = entry.dimension + ":" + entry.x + "," + entry.y + "," + entry.z;
            this.fontRendererObj.drawString(pos, LIST_X + 132, y + 2, 0x909090);
            this.fontRendererObj.drawString(
                statusLabel(entry.status),
                LIST_X + 210,
                y + 2,
                statusColor(entry.status));
        }
    }

    private static String statusLabel(byte status) {
        switch (status) {
            case WirelessDistributorSyncPacket.STATUS_AVAILABLE:
                return tr("gui.ae2_qof.wireless_distributor.status.available");
            case WirelessDistributorSyncPacket.STATUS_LINKED:
                return tr("gui.ae2_qof.wireless_distributor.status.linked");
            case WirelessDistributorSyncPacket.STATUS_EXTERNAL:
                return tr("gui.ae2_qof.wireless_distributor.status.external");
            default:
                return tr("gui.ae2_qof.wireless_distributor.status.unavailable");
        }
    }

    private static int statusColor(byte status) {
        switch (status) {
            case WirelessDistributorSyncPacket.STATUS_AVAILABLE:
                return 0x55FF55;
            case WirelessDistributorSyncPacket.STATUS_LINKED:
                return 0x55CCFF;
            case WirelessDistributorSyncPacket.STATUS_EXTERNAL:
                return 0xFFFF55;
            default:
                return 0x808080;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int row = this.hoveredRow(mouseX, mouseY);
        if (row < 0 || data == null) return;
        int index = this.scrollRow + row;
        List<WirelessDistributorSyncPacket.Entry> entries = this.filteredEntries();
        if (index >= entries.size()) return;
        WirelessDistributorSyncPacket.Entry entry = entries.get(index);
        List<String> lines = new ArrayList<String>();
        lines.add(entry.name);
        lines.add(trf("gui.ae2_qof.wireless_distributor.tooltip.pos", entry.dimension, entry.x, entry.y, entry.z));
        lines.add(
            trf(
                "gui.ae2_qof.wireless_distributor.tooltip.status",
                statusLabel(entry.status),
                AEColor.fromOrdinal(entry.color)
                    .name()));
        lines.add(tr("gui.ae2_qof.wireless_distributor.tooltip.click"));
        this.drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
    }
}
