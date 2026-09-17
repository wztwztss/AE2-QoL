package com.wztwzt.ae2_qof.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.common.RecipeMapNameConfig;
import com.wztwzt.ae2_qof.merged.GuiMergedTerminal;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.UploadPatternPacket;
import com.wztwzt.ae2_qof.util.RecipeNameUtil;

/**
 * 供应器选择界面。
 *
 * 布局参考 GTNH-ECO 的样板上传面板：每一行是「机器图标 + 机器名 + 类型/空闲槽位」卡片，
 * 可滚轮翻页，选中一行后点“上传”或双击即可投递。相比早期纯按钮版本，玩家能一眼看清
 * 每台机器是什么、还有几个空样板槽，避免把样板传错机器。
 */
public class GuiProviderSelect extends GuiScreen {

    private static final int BUTTON_PREV = 100;
    private static final int BUTTON_NEXT = 101;
    private static final int BUTTON_RELOAD = 102;
    private static final int BUTTON_ADD = 103;
    private static final int BUTTON_DELETE = 104;
    private static final int BUTTON_CLOSE = 105;
    private static final int BUTTON_UPLOAD = 106;

    private static final int PANEL_W = 300;
    private static final int ROW_H = 26;
    private static final int ROW_GAP = 2;
    private static final int VISIBLE_ROWS = 6;

    private final GuiScreen parent;
    private final List<Long> ids;
    private final List<String> names;
    private final List<Integer> emptySlots;
    private final List<Integer> totalSlots;
    private final List<ItemStack> icons;

    private final List<GroupEntry> groups = new ArrayList<GroupEntry>();
    private final List<GroupEntry> filtered = new ArrayList<GroupEntry>();

    private GuiTextField searchBox;
    private GuiTextField mappingField;
    /** 本次要上传的配方池标识（可能为空），仅用于界面提示。 */
    private final String recipeMap;
    private String query = "";
    private int selected = -1;
    private int scroll = 0;
    private boolean needsRefresh = false;
    private String lastAddedMappingName = null;
    private String lastRawRecipeId = null;

    /** 一行卡片：可能是多台同名机器聚合后的结果。 */
    private static class GroupEntry {

        long id;
        String name;
        int totalSlots;
        int emptySlots;
        int count;
        ItemStack icon;
        /** 候选里空槽最多的那台的空槽数，用于决定聚合行指向哪一台。 */
        int bestEmpty;
    }

    public GuiProviderSelect(List<Long> ids, List<String> names, List<Integer> emptySlots) {
        this(null, ids, names, emptySlots, null, null, null);
    }

    public GuiProviderSelect(GuiScreen parent, List<Long> ids, List<String> names, List<Integer> emptySlots) {
        this(parent, ids, names, emptySlots, null, null, null);
    }

    public GuiProviderSelect(GuiScreen parent, List<Long> ids, List<String> names, List<Integer> emptySlots,
        List<Integer> totalSlots, List<ItemStack> icons) {
        this(parent, ids, names, emptySlots, totalSlots, icons, null);
    }

    public GuiProviderSelect(GuiScreen parent, List<Long> ids, List<String> names, List<Integer> emptySlots,
        List<Integer> totalSlots, List<ItemStack> icons, String recipeMap) {
        this.recipeMap = recipeMap;
        this.parent = parent;
        this.ids = ids == null ? new ArrayList<Long>() : new ArrayList<Long>(ids);
        this.names = names == null ? new ArrayList<String>() : new ArrayList<String>(names);
        this.emptySlots = emptySlots == null ? new ArrayList<Integer>() : new ArrayList<Integer>(emptySlots);
        this.totalSlots = totalSlots == null ? new ArrayList<Integer>() : new ArrayList<Integer>(totalSlots);
        this.icons = icons == null ? new ArrayList<ItemStack>() : new ArrayList<ItemStack>(icons);

        String recent = RecipeNameUtil.getLastRecipeName();
        if (recent != null && !recent.isEmpty()) {
            this.query = recent;
        }
        String rawId = RecipeNameUtil.getLastRawRecipeId();
        if (rawId != null && !rawId.isEmpty()) {
            this.lastRawRecipeId = rawId;
        }
        RecipeNameUtil.clearLastRecipeName();

        buildGroups();
        applyFilter();
    }

    public void setPresetSearchKey(String key) {
        this.query = key == null ? "" : key;
        if (searchBox != null) {
            searchBox.setText(this.query);
        }
        selected = -1;
        scroll = 0;
        applyFilter();
    }

    private void buildGroups() {
        Map<String, GroupEntry> map = new LinkedHashMap<String, GroupEntry>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            long id = ids.get(i);
            int empty = i < emptySlots.size() ? emptySlots.get(i) : 0;
            int total = i < totalSlots.size() ? totalSlots.get(i) : empty;
            ItemStack icon = i < icons.size() ? icons.get(i) : null;

            GroupEntry entry = map.get(name);
            if (entry == null) {
                entry = new GroupEntry();
                entry.name = name;
                map.put(name, entry);
            }
            entry.count++;
            entry.totalSlots += Math.max(0, total);
            entry.emptySlots += Math.max(0, empty);
            // 同名机器可能有多台：聚合显示，但上传目标固定指向空槽最多的那一台，
            // 避免旧实现里“同名时随机命中一台”导致的传错机器。
            if (entry.id == 0L || empty > entry.bestEmpty) {
                entry.id = id;
                entry.bestEmpty = Math.max(0, empty);
            }
            if (entry.icon == null) {
                entry.icon = icon;
            }
        }
        groups.clear();
        groups.addAll(map.values());
    }

    private void applyFilter() {
        filtered.clear();
        String q = query == null ? "" : query.trim().toLowerCase();
        for (GroupEntry entry : groups) {
            if (q.isEmpty() || entry.name.toLowerCase().contains(q)) {
                filtered.add(entry);
            }
        }
        if (!q.isEmpty() && filtered.isEmpty()) {
            filtered.addAll(groups);
        }
        if (selected >= filtered.size()) {
            selected = -1;
        }
        clampScroll();
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int left = this.width / 2 - PANEL_W / 2;
        int top = this.height / 2 - 108;

        if (this.searchBox == null) {
            this.searchBox = new GuiTextField(this.fontRendererObj, left + 20, top + 4, PANEL_W - 26, 16);
            this.searchBox.setMaxStringLength(64);
        } else {
            this.searchBox.xPosition = left + 20;
            this.searchBox.yPosition = top + 4;
        }
        this.searchBox.setText(query);

        int navY = top + VISIBLE_ROWS * (ROW_H + ROW_GAP) + 12;
        if (this.mappingField == null) {
            this.mappingField = new GuiTextField(this.fontRendererObj, left, navY + 26, 150, 16);
            this.mappingField.setMaxStringLength(64);
        } else {
            this.mappingField.xPosition = left;
            this.mappingField.yPosition = navY + 26;
        }

        this.buttonList.add(new GuiButton(BUTTON_PREV, left + PANEL_W / 2 - 60, navY, 20, 18, "<"));
        this.buttonList.add(new GuiButton(BUTTON_NEXT, left + PANEL_W / 2 + 40, navY, 20, 18, ">"));
        this.buttonList.add(new GuiButton(BUTTON_UPLOAD, left + PANEL_W / 2 - 30, navY - 22, 80, 18,
            translate("ae2_qof.select_provider.upload")));
        this.buttonList.add(new GuiButton(BUTTON_ADD, left, navY + 26, 46, 16, translate("gui.ae2_qof.add")));
        this.buttonList.add(new GuiButton(BUTTON_RELOAD, left + 50, navY + 26, 56, 16, translate("gui.ae2_qof.reload")));
        this.buttonList.add(new GuiButton(BUTTON_DELETE, left + 110, navY + 26, 46, 16, translate("gui.ae2_qof.delete")));
        this.buttonList.add(
            new GuiButton(BUTTON_CLOSE, left + PANEL_W - 50, navY + 26, 50, 16, translate("gui.cancel")));
    }

    /** 当前页可见的行在屏幕上的 Y 坐标起点。 */
    private int listTop() {
        return this.height / 2 - 108 + ROW_H;
    }

    private int listLeft() {
        return this.width / 2 - PANEL_W / 2;
    }

    private boolean isInsideList(int mouseX, int mouseY) {
        int left = listLeft();
        int top = listTop();
        int bottom = top + VISIBLE_ROWS * (ROW_H + ROW_GAP);
        return mouseX >= left && mouseX <= left + PANEL_W && mouseY >= top && mouseY <= bottom;
    }

    private int rowIndexAt(int mouseX, int mouseY) {
        if (!isInsideList(mouseX, mouseY)) {
            return -1;
        }
        int rel = (mouseY - listTop()) / (ROW_H + ROW_GAP);
        int idx = scroll + rel;
        return idx >= 0 && idx < filtered.size() ? idx : -1;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        switch (button.id) {
            case BUTTON_PREV:
                scrollBy(-1);
                break;
            case BUTTON_NEXT:
                scrollBy(1);
                break;
            case BUTTON_UPLOAD:
                uploadSelected();
                break;
            case BUTTON_RELOAD:
                reloadMappings();
                break;
            case BUTTON_ADD:
                addMappingFromUI();
                break;
            case BUTTON_DELETE:
                deleteMappingFromUI();
                break;
            case BUTTON_CLOSE:
                this.mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    private void scrollBy(int delta) {
        scroll += delta;
        clampScroll();
        needsRefresh = true;
    }

    private void uploadSelected() {
        if (selected < 0 || selected >= filtered.size()) {
            sendClientMessage(translate("ae2_qof.select_provider.pick_first"));
            return;
        }
        GroupEntry entry = filtered.get(selected);
        if (entry.emptySlots <= 0 && entry.totalSlots > 0) {
            sendClientMessage(translate("ae2_qof.select_provider.full"));
            return;
        }
        handleSelect(entry.id);
    }

    protected void handleSelect(long providerId) {
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == providerId) {
                ClientState.set(names.get(i), providerId);
                if (ClientState.lastRecipeMap != null) {
                    ClientState.rememberProvider(ClientState.lastRecipeMap, names.get(i));
                }
                break;
            }
        }
        ModNetwork.CHANNEL.sendToServer(new UploadPatternPacket(providerId));
        restoreParentScreen();
    }

    private void reloadMappings() {
        try {
            RecipeNameUtil.reloadMappings();
        } catch (Throwable t) {
            sendClientMessage(
                StatCollector.translateToLocalFormatted("ae2_qof.error.read_mappings", t.getMessage()));
            return;
        }
        if (lastAddedMappingName != null && !lastAddedMappingName.isEmpty()) {
            query = lastAddedMappingName;
            scroll = 0;
        }
        applyFilter();
        needsRefresh = true;
        sendClientMessage(translate("ae2_qof.info.mappings_reloaded"));
    }

    private void addMappingFromUI() {
        String key = (lastRawRecipeId != null && !lastRawRecipeId.isEmpty()) ? lastRawRecipeId
            : (query == null ? "" : query.trim());
        String value = mappingField == null ? ""
            : mappingField.getText()
                .trim();
        if (key.isEmpty()) {
            sendClientMessage(translate("ae2_qof.info.enter_keyword"));
            return;
        }
        if (value.isEmpty()) {
            sendClientMessage(translate("ae2_qof.info.enter_mapping_name"));
            return;
        }
        if (RecipeNameUtil.addOrUpdateMapping(key, value)) {
            sendClientMessage(String.format(translate("ae2_qof.info.mapping_added"), key, value));
            lastAddedMappingName = value;
            query = value;
            if (searchBox != null) {
                searchBox.setText(value);
            }
            RecipeNameUtil.reloadMappings();
            applyFilter();
            needsRefresh = true;
        } else {
            sendClientMessage(translate("ae2_qof.info.mapping_add_failed"));
        }
    }

    private void deleteMappingFromUI() {
        String value = mappingField == null ? ""
            : mappingField.getText()
                .trim();
        if (value.isEmpty()) {
            sendClientMessage(translate("ae2_qof.info.enter_mapping_delete"));
            return;
        }
        int removed = RecipeNameUtil.removeMappingsByCnValue(value);
        if (removed > 0) {
            sendClientMessage(String.format(translate("ae2_qof.info.mapping_deleted"), removed));
            RecipeNameUtil.reloadMappings();
            applyFilter();
            needsRefresh = true;
        } else {
            sendClientMessage(translate("ae2_qof.info.mapping_not_found"));
        }
    }

    private void sendClientMessage(String msg) {
        if (this.mc != null && this.mc.thePlayer != null && msg != null && !msg.isEmpty()) {
            this.mc.thePlayer.addChatMessage(new ChatComponentText(msg));
        }
    }

    @Override
    public void updateScreen() {
        if (searchBox != null) {
            searchBox.updateCursorCounter();
        }
        if (mappingField != null) {
            mappingField.updateCursorCounter();
        }
        if (needsRefresh) {
            needsRefresh = false;
            initGui();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0 || !isInsideList(org.lwjgl.input.Mouse.getEventX() * this.width / this.mc.displayWidth,
            this.height - org.lwjgl.input.Mouse.getEventY() * this.height / this.mc.displayHeight - 1)) {
            return;
        }
        scrollBy(wheel > 0 ? -1 : 1);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {}
        if (searchBox != null) {
            searchBox.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mappingField != null) {
            mappingField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 0) {
            int idx = rowIndexAt(mouseX, mouseY);
            if (idx >= 0) {
                selected = idx;
                needsRefresh = true;
                return;
            }
        }
        if (mouseButton == 1 && searchBox != null
            && isPointInRegion(
                searchBox.xPosition,
                searchBox.yPosition,
                searchBox.width,
                searchBox.height,
                mouseX,
                mouseY)) {
            if (!searchBox.getText()
                .isEmpty()) {
                searchBox.setText("");
                query = "";
                scroll = 0;
                applyFilter();
                needsRefresh = true;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 || keyCode == this.mc.gameSettings.keyBindInventory.getKeyCode()) {
            restoreParentScreen();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            uploadSelected();
            return;
        }
        boolean handled = false;
        if (searchBox != null && searchBox.textboxKeyTyped(typedChar, keyCode)) {
            String newQuery = searchBox.getText();
            if (!Objects.equals(newQuery, query)) {
                query = newQuery;
                selected = -1;
                scroll = 0;
                applyFilter();
                needsRefresh = true;
            }
            handled = true;
        }
        if (mappingField != null && mappingField.textboxKeyTyped(typedChar, keyCode)) {
            handled = true;
        }
        if (!handled) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    private boolean isPointInRegion(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int left = this.width / 2 - PANEL_W / 2;
        int top = this.height / 2 - 108;

        // 面板底
        drawRect(left - 2, top - 4, left + PANEL_W + 2, top + VISIBLE_ROWS * (ROW_H + ROW_GAP) + 8, 0xB0101010);
        drawRect(left, top, left + PANEL_W, top + 2, 0xFF4A90D9);

        String title = translate("ae2_qof.select_provider");
        this.fontRendererObj.drawStringWithShadow(title, left + 4, top - 14, 0xFFFFFF);
        if (recipeMap != null && !recipeMap.isEmpty()) {
            this.fontRendererObj.drawStringWithShadow(
                net.minecraft.util.EnumChatFormatting.GRAY + translate("ae2_qof.select_provider.recipe", recipeMap),
                left + 4 + this.fontRendererObj.getStringWidth(title) + 8,
                top - 14,
                0xFFAAAAAA);
        }

        if (searchBox != null) {
            searchBox.drawTextBox();
            if (searchBox.getText()
                .isEmpty()) {
                this.fontRendererObj.drawStringWithShadow(
                    EnumChatFormatting.DARK_GRAY + translate("ae2_qof.select_provider.search"),
                    searchBox.xPosition + 4,
                    searchBox.yPosition + 4,
                    0xFF888888);
            }
        }
        drawCentredString(translate("ae2_qof.select_provider.count", filtered.size(), groups.size()),
            left + PANEL_W / 2, top - 14, 0xFFAAAAAA);

        // 列表
        int y = listTop();
        int end = Math.min(scroll + VISIBLE_ROWS, filtered.size());
        for (int i = scroll; i < end; i++) {
            GroupEntry entry = filtered.get(i);
            int rowY = y + (i - scroll) * (ROW_H + ROW_GAP);
            boolean hovered = mouseX >= left && mouseX <= left + PANEL_W && mouseY >= rowY && mouseY <= rowY + ROW_H;
            boolean isSelected = i == selected;
            int bg = isSelected ? 0xFF2E5C8A : (hovered ? 0xFF2A2A32 : 0xFF1B1B22);
            drawRect(left + 1, rowY, left + PANEL_W - 1, rowY + ROW_H, bg);
            drawRect(left + 1, rowY, left + 3, rowY + ROW_H, isSelected ? 0xFF6FB3F0 : 0xFF3A3A44);

            // 图标
            if (entry.icon != null) {
                this.itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(),
                    entry.icon, left + 6, rowY + 5);
                this.itemRender.renderItemOverlayIntoGUI(this.fontRendererObj, this.mc.getTextureManager(),
                    entry.icon, left + 6, rowY + 5, "");
            } else {
                drawRect(left + 6, rowY + 5, left + 22, rowY + 21, 0xFF3A3A44);
            }

            // 机器名（含坐标后缀，便于区分同名机器）
            String label = entry.name;
            this.fontRendererObj.drawStringWithShadow(
                trim(label, PANEL_W - 120),
                left + 28,
                rowY + 4,
                isSelected ? 0xFFFFFF : 0xFFDDDDDD);

            // 类型/容量
            String capacity = entry.emptySlots + " / " + entry.totalSlots;
            int capColor = entry.totalSlots > 0 && entry.emptySlots <= 0 ? 0xFFCC5555 : 0xFF66CC88;
            this.fontRendererObj.drawStringWithShadow(
                capacity,
                left + PANEL_W - 10 - this.fontRendererObj.getStringWidth(capacity),
                rowY + 14,
                capColor);

            if (entry.count > 1) {
                String dup = "x" + entry.count;
                this.fontRendererObj.drawStringWithShadow(
                    EnumChatFormatting.GRAY + dup,
                    left + PANEL_W - 10 - this.fontRendererObj.getStringWidth(dup),
                    rowY + 3,
                    0xFFAAAAAA);
            }
        }

        if (filtered.isEmpty()) {
            drawCentredString(translate("ae2_qof.select_provider.empty"), left + PANEL_W / 2,
                listTop() + 30, 0xFFAA5555);
        }

        // 滚动条
        if (filtered.size() > VISIBLE_ROWS) {
            int trackTop = listTop();
            int trackH = VISIBLE_ROWS * (ROW_H + ROW_GAP);
            int barH = Math.max(12, trackH * VISIBLE_ROWS / filtered.size());
            int maxScroll = filtered.size() - VISIBLE_ROWS;
            int barY = trackTop + (trackH - barH) * scroll / Math.max(1, maxScroll);
            drawRect(left + PANEL_W + 1, trackTop, left + PANEL_W + 4, trackTop + trackH, 0xFF202028);
            drawRect(left + PANEL_W + 1, barY, left + PANEL_W + 4, barY + barH, 0xFF6FB3F0);
        }

        // 映射输入
        if (mappingField != null) {
            mappingField.drawTextBox();
            String mappingLabel = translate("gui.ae2_qof.mapping_label");
            this.fontRendererObj.drawString(
                mappingLabel,
                left + mappingField.width + 4,
                mappingField.yPosition + 4,
                0xFFAAAAAA);
        }

        // 底部提示
        drawCentredString(
            translate("ae2_qof.select_provider.hint"),
            left + PANEL_W / 2,
            mappingField != null ? mappingField.yPosition + 22 : this.height / 2,
            0xFF888888);

        // 选中项悬浮说明
        if (selected >= 0 && selected < filtered.size()) {
            GroupEntry entry = filtered.get(selected);
            if (entry.icon != null) {
                List<String> tip = new ArrayList<String>();
                tip.add(entry.name);
                tip.add(EnumChatFormatting.GRAY + translate("ae2_qof.select_provider.slots", entry.emptySlots, entry.totalSlots));
                if (entry.count > 1) {
                    tip.add(EnumChatFormatting.GRAY + translate("ae2_qof.select_provider.duplicates", entry.count));
                }
                this.drawHoveringText(tip, mouseX, mouseY, this.fontRendererObj);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String trim(String text, int maxWidth) {
        if (text == null) return "";
        if (this.fontRendererObj.getStringWidth(text) <= maxWidth) return text;
        String cut = text;
        while (cut.length() > 1 && this.fontRendererObj.getStringWidth(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private void drawCentredString(String text, int centerX, int y, int color) {
        if (text == null) return;
        this.fontRendererObj.drawStringWithShadow(
            text,
            centerX - this.fontRendererObj.getStringWidth(text) / 2,
            y,
            color);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void restoreParentScreen() {
        if (this.mc != null) {
            this.mc.displayGuiScreen(this.parent);
            String rawId = RecipeNameUtil.getLastRawRecipeId();
            if (rawId != null && !rawId.isEmpty()) {
                String resolved = RecipeMapNameConfig.resolveSearchKeyword(rawId);
                if (resolved != null && !resolved.equals(rawId)) {
                    GuiMergedTerminal.setSearchFieldText(resolved);
                }
            }
        }
    }

    protected String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    protected String translate(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }
}