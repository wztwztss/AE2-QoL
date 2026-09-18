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
 * 样板上传目标选择界面（fix40 重做）。
 *
 * <p>按我们自己的需求分区排版，四个区域纵向排列、互不重叠：
 * <ol>
 *   <li>标题栏：左侧界面名，右侧「共 N 台 · 显示 M」计数；</li>
 *   <li>搜索栏：机器名过滤 + 翻页按钮；</li>
 *   <li>机器列表：每行是「物品图标 + 机器名 + 空槽数」卡片，左键选中、双击直接上传、右键把该机器名
 *       填进下方映射框；</li>
 *   <li>配方映射区：把「配方 ID」关联到「机器名」，供自动上传时按名称匹配目标。这一块是本模组独有的
 *       需求，上一版照着 GTNH-ECO 抄的时候把输入框和按钮挤在同一行，导致互相遮挡，本期重新设计。</li>
 * </ol>
 *
 * 面板宽度、可见行数会随游戏窗口缩放自适应，避免小分辨率下底部被裁掉。
 */
public class GuiProviderSelect extends GuiScreen {

    private static final int BUTTON_PREV = 100;
    private static final int BUTTON_NEXT = 101;
    private static final int BUTTON_RELOAD = 102;
    private static final int BUTTON_ADD = 103;
    private static final int BUTTON_DELETE = 104;
    private static final int BUTTON_CLOSE = 105;
    private static final int BUTTON_UPLOAD = 106;
    private static final int BUTTON_PICK_NAME = 107;

    /** 面板左右内边距。 */
    private static final int PAD = 10;
    /** 单行卡片高度与行距。 */
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 2;
    private static final int SEARCH_H = 18;
    private static final int ACTION_H = 20;
    private static final int FIELD_H = 16;

    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = 7;
    private static final int PANEL_MIN_W = 280;
    private static final int PANEL_MAX_W = 400;

    private final GuiScreen parent;
    private final List<Long> ids;
    private final List<String> names;
    private final List<Integer> emptySlots;
    private final List<Integer> totalSlots;
    private final List<ItemStack> icons;

    private final List<GroupEntry> groups = new ArrayList<GroupEntry>();
    private final List<GroupEntry> filtered = new ArrayList<GroupEntry>();

    private GuiTextField searchBox;
    /** 配方 ID 输入框（映射的 key）。 */
    private GuiTextField keyField;
    /** 机器名输入框（映射的 value，自动上传时按它匹配目标）。 */
    private GuiTextField mappingField;

    /** 本次要上传的配方池标识（可能为空），仅用于界面提示。 */
    private final String recipeMap;
    private final String initialKey;
    private final String initialValue;

    private String query = "";
    private int selected = -1;
    private int scroll = 0;
    private boolean needsRefresh = false;
    private boolean fieldsInitialized = false;
    private String lastAddedMappingName = null;
    private String lastRawRecipeId = null;
    /** 仅当预填关键词确实是一台机器的名字时，才用它预填「目标机器名」框。 */
    private String pendingValuePrefill = null;

    private long lastClickTime = 0L;
    private int lastClickIndex = -1;

    // ===== 布局（随窗口自适应） =====
    private int panelW = PANEL_MIN_W;
    private int panelH = 200;
    private int left = 0;
    private int top = 0;
    private int rows = MIN_ROWS;
    private int headerY = 0;
    private int searchY = 0;
    private int listTopY = 0;
    private int actionY = 0;
    private int mapHeaderY = 0;
    private int mapLabelY = 0;
    private int mapFieldY = 0;
    private int mapBtnY = 0;
    private int hintY = 0;
    private int searchFieldW = 120;
    private int mapFieldW = 120;
    private int mapButtonW = 52;

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
        /** 同上，指向那台的样板槽总数。 */
        int bestTotal;
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

        String key = this.lastRawRecipeId != null && !this.lastRawRecipeId.isEmpty() ? this.lastRawRecipeId
            : (recipeMap != null ? recipeMap : "");
        this.initialKey = key;
        this.initialValue = lookupMappedName(key, null);

        buildGroups();
        applyFilter();
    }

    /** 取已保存映射里该配方的中文名，取不到时回退到最近一次配方中文名。 */
    private static String lookupMappedName(String key, String recentName) {
        if (key != null && !key.isEmpty()) {
            try {
                String mapped = RecipeNameUtil.getMappingsView()
                    .get(key);
                if (mapped != null && !mapped.isEmpty()) {
                    return mapped;
                }
            } catch (Throwable ignored) {}
        }
        if (recentName != null && !recentName.isEmpty()) {
            try {
                for (String value : RecipeNameUtil.getMappingsView()
                    .values()) {
                    if (recentName.equals(value)) {
                        return recentName;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return "";
    }

    /** 设置预填搜索关键字（来自配方池映射）。 */
    public void setPresetSearchKey(String key) {
        this.query = key == null ? "" : key;
        if (searchBox != null) {
            searchBox.setText(this.query);
        }
        // 预填关键词可能来自「上次记住的目标机器名」——只有它确实对应列表里某台机器时，
        // 才顺手填进映射框，避免把配方中文名误填成机器名。
        if (this.initialValue == null || this.initialValue.isEmpty()) {
            if (isKnownProviderName(this.query)) {
                this.pendingValuePrefill = this.query;
            }
        }
        selected = -1;
        scroll = 0;
        applyFilter();
    }

    /** 判断给定字符串是否与某台候选机器的展示名匹配（允许省略 @D 维度坐标后缀）。 */
    private boolean isKnownProviderName(String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) {
            return false;
        }
        String c = candidate.trim();
        for (String n : names) {
            if (n == null) {
                continue;
            }
            if (n.equals(c) || n.startsWith(c + " @D")) {
                return true;
            }
        }
        return false;
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
                entry.bestTotal = Math.max(0, total);
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
        String q = query == null ? "" : query.trim()
            .toLowerCase();
        for (GroupEntry entry : groups) {
            if (q.isEmpty() || entry.name.toLowerCase()
                .contains(q)) {
                filtered.add(entry);
            }
        }
        if (selected >= filtered.size()) {
            selected = -1;
        }
        clampScroll();
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, filtered.size() - rows);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    /**
     * 按窗口高度自适应计算面板尺寸与各区域纵坐标。
     * 自上而下依次是：标题 / 搜索 / 列表 / 操作按钮 / 配方映射区 / 底部提示。
     */
    private void computeLayout() {
        panelW = Math.max(PANEL_MIN_W, Math.min(PANEL_MAX_W, this.width - 40));
        if (panelW > this.width - 8) {
            panelW = Math.max(200, this.width - 8);
        }

        final int headerH = 14;
        final int mapBlockH = 8 + 12 + 10 + FIELD_H + 6 + 20 + 6;

        int rowsUnit = ROW_H + ROW_GAP;
        int availH = this.height - 10;
        int fixed = 8 + headerH + SEARCH_H + 4 + 6 + ACTION_H + mapBlockH + 10;

        rows = (availH - fixed) / rowsUnit;
        rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
        // 极端小窗口下即使最少行数也放不下时继续收缩，优先保证底部映射区与按钮可见
        while (rows > 1 && fixed + rows * rowsUnit > availH) {
            rows--;
        }

        int inner = panelW - PAD * 2;
        // 右侧留给 44px 的两个翻页按钮，其余全部给搜索框
        searchFieldW = Math.max(60, inner - 50);
        mapFieldW = Math.max(60, (inner - 24) / 2 - 4);
        mapButtonW = Math.max(60, inner - 162);

        panelH = fixed + rows * rowsUnit;
        if (panelH > this.height - 4) {
            panelH = this.height - 4;
        }

        top = Math.max(2, (this.height - panelH) / 2);
        left = Math.max(2, (this.width - panelW) / 2);

        int y = top + 8;
        headerY = y;
        y += headerH;
        searchY = y;
        y += SEARCH_H + 4;
        listTopY = y;
        y += rows * rowsUnit;
        y += 6;
        actionY = y;
        y += ACTION_H;
        y += 8;
        mapHeaderY = y;
        y += 12;
        mapLabelY = y;
        y += 10;
        mapFieldY = y;
        y += FIELD_H;
        y += 6;
        mapBtnY = y;
        y += 20;
        y += 6;
        hintY = y;

        // 行数会随窗口变化，重新夹一次滚动位置，避免缩窗后列表空白
        clampScroll();
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        computeLayout();

        if (this.searchBox == null) {
            this.searchBox = new GuiTextField(this.fontRendererObj, left + PAD, searchY, searchFieldW, SEARCH_H - 2);
            this.searchBox.setMaxStringLength(64);
        } else {
            this.searchBox.xPosition = left + PAD;
            this.searchBox.yPosition = searchY;
            this.searchBox.width = searchFieldW;
            this.searchBox.height = SEARCH_H - 2;
        }
        this.searchBox.setText(query);

        int fieldW = mapFieldW;
        int keyX = left + PAD;
        int valX = left + PAD + fieldW + 24;
        if (this.keyField == null) {
            this.keyField = new GuiTextField(this.fontRendererObj, keyX, mapFieldY, fieldW, FIELD_H);
            this.keyField.setMaxStringLength(96);
        } else {
            this.keyField.xPosition = keyX;
            this.keyField.yPosition = mapFieldY;
            this.keyField.width = fieldW;
        }
        if (this.mappingField == null) {
            this.mappingField = new GuiTextField(this.fontRendererObj, valX, mapFieldY, fieldW, FIELD_H);
            this.mappingField.setMaxStringLength(64);
        } else {
            this.mappingField.xPosition = valX;
            this.mappingField.yPosition = mapFieldY;
            this.mappingField.width = fieldW;
        }
        // 只在首次打开时预填，避免玩家编辑到一半被重排刷掉。
        if (!fieldsInitialized) {
            fieldsInitialized = true;
            if (initialKey != null && !initialKey.isEmpty()) {
                this.keyField.setText(initialKey);
            }
            String prefillValue = initialValue != null && !initialValue.isEmpty() ? initialValue
                : (pendingValuePrefill == null ? "" : pendingValuePrefill);
            if (!prefillValue.isEmpty()) {
                this.mappingField.setText(prefillValue);
            }
        }

        int navY = searchY - 1;
        this.buttonList.add(new GuiButton(BUTTON_PREV, left + panelW - PAD - 46, navY, 22, SEARCH_H, "<"));
        this.buttonList.add(new GuiButton(BUTTON_NEXT, left + panelW - PAD - 22, navY, 22, SEARCH_H, ">"));

        this.buttonList.add(
            new GuiButton(BUTTON_UPLOAD, left + panelW / 2 - 42, actionY, 84, ACTION_H,
                translate("ae2_qof.select_provider.upload")));
        this.buttonList.add(
            new GuiButton(BUTTON_CLOSE, left + panelW - PAD - 50, actionY, 50, ACTION_H, translate("gui.cancel")));

        int bx = left + PAD;
        this.buttonList.add(new GuiButton(BUTTON_ADD, bx, mapBtnY, 50, 20, translate("gui.ae2_qof.add")));
        this.buttonList.add(new GuiButton(BUTTON_DELETE, bx + 54, mapBtnY, 50, 20, translate("gui.ae2_qof.delete")));
        this.buttonList
            .add(new GuiButton(BUTTON_RELOAD, bx + 108, mapBtnY, 50, 20, translate("gui.ae2_qof.reload")));
        this.buttonList.add(
            new GuiButton(BUTTON_PICK_NAME, bx + 162, mapBtnY, mapButtonW, 20,
                translate("ae2_qof.select_provider.use_selected")));
    }

    private boolean isInsideList(int mouseX, int mouseY) {
        int bottom = listTopY + rows * (ROW_H + ROW_GAP);
        return mouseX >= left && mouseX <= left + panelW && mouseY >= listTopY && mouseY <= bottom;
    }

    private int rowIndexAt(int mouseX, int mouseY) {
        if (!isInsideList(mouseX, mouseY)) {
            return -1;
        }
        int rel = (mouseY - listTopY) / (ROW_H + ROW_GAP);
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
            case BUTTON_PICK_NAME:
                pickSelectedName();
                break;
            case BUTTON_CLOSE:
                // 取消不触碰终端搜索框，保持玩家打开界面前的状态
                this.mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    /**
     * 记住「这个配方池 -> 这台机器」的选择，下次同一配方自动上传时可直接命中，不再弹窗。
     * 优先用本次上传的真实配方（可能来自样板 NBT），其次回退到界面标题里的配方池标识。
     */
    private void rememberTarget(String providerName) {
        if (providerName == null || providerName.isEmpty()) {
            return;
        }
        // 记住/查询必须用同一个 key：自动上传时按 ClientState.lastRecipeMap 查这张表，
        // 所以这里优先用同一个值，再依次回退到界面标题的配方池与 NEI 原始配方 ID。
        String recipeKey = ClientState.lastRecipeMap;
        if (recipeKey == null || recipeKey.isEmpty()) {
            recipeKey = recipeMap;
        }
        if (recipeKey == null || recipeKey.isEmpty()) {
            try {
                recipeKey = RecipeNameUtil.getLastRawRecipeId();
            } catch (Throwable ignored) {}
        }
        if (recipeKey != null && !recipeKey.isEmpty()) {
            ClientState.rememberProvider(recipeKey, providerName);
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
        if (entry.totalSlots > 0 && entry.bestEmpty <= 0) {
            sendClientMessage(translate("ae2_qof.select_provider.full"));
            return;
        }
        handleSelect(entry.id);
    }

    protected void handleSelect(long providerId) {
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) == providerId) {
                ClientState.set(names.get(i), providerId);
                rememberTarget(names.get(i));
                break;
            }
        }
        ModNetwork.CHANNEL.sendToServer(new UploadPatternPacket(providerId));
        restoreParentScreen();
    }

    /** 把选中机器的名字填进「机器名」框，方便一键建立配方→机器映射。 */
    private void pickSelectedName() {
        if (selected < 0 || selected >= filtered.size()) {
            sendClientMessage(translate("ae2_qof.select_provider.pick_first"));
            return;
        }
        if (mappingField != null) {
            mappingField.setText(filtered.get(selected).name);
            mappingField.setFocused(true);
            if (keyField != null) {
                keyField.setFocused(false);
            }
        }
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
        String key = keyField == null ? "" : keyField.getText()
            .trim();
        if (key.isEmpty() && lastRawRecipeId != null && !lastRawRecipeId.isEmpty()) {
            key = lastRawRecipeId;
        }
        String value = mappingField == null ? "" : mappingField.getText()
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
        String key = keyField == null ? "" : keyField.getText()
            .trim();
        String value = mappingField == null ? "" : mappingField.getText()
            .trim();
        boolean removedAny = false;
        if (!key.isEmpty() && RecipeNameUtil.removeMappingByKey(key)) {
            sendClientMessage(String.format(translate("ae2_qof.info.mapping_deleted"), 1));
            removedAny = true;
        } else if (!value.isEmpty()) {
            int removed = RecipeNameUtil.removeMappingsByCnValue(value);
            if (removed > 0) {
                sendClientMessage(String.format(translate("ae2_qof.info.mapping_deleted"), removed));
                removedAny = true;
            }
        } else {
            sendClientMessage(translate("ae2_qof.info.enter_mapping_delete"));
            return;
        }
        if (removedAny) {
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
        if (keyField != null) {
            keyField.updateCursorCounter();
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
        if (wheel == 0) {
            return;
        }
        int mx = org.lwjgl.input.Mouse.getEventX() * this.width / this.mc.displayWidth;
        int my = this.height - org.lwjgl.input.Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (!isInsideList(mx, my)) {
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
        if (keyField != null) {
            keyField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mappingField != null) {
            mappingField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        int idx = rowIndexAt(mouseX, mouseY);
        if (mouseButton == 1) {
            // 右键：清空搜索框，方便快速回到完整列表
            if (searchBox != null && isPointInRegion(
                searchBox.xPosition,
                searchBox.yPosition,
                searchBox.width,
                searchBox.height,
                mouseX,
                mouseY)
                && !searchBox.getText()
                    .isEmpty()) {
                searchBox.setText("");
                query = "";
                scroll = 0;
                applyFilter();
                needsRefresh = true;
                return;
            }
        }
        if (mouseButton == 0 && idx >= 0) {
            long now = System.currentTimeMillis();
            boolean doubleClick = idx == lastClickIndex && now - lastClickTime < 350L;
            lastClickIndex = idx;
            lastClickTime = now;
            selected = idx;
            if (doubleClick) {
                uploadSelected();
                return;
            }
            needsRefresh = true;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 || keyCode == this.mc.gameSettings.keyBindInventory.getKeyCode()) {
            restoreParentScreen();
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
        if (keyField != null && keyField.textboxKeyTyped(typedChar, keyCode)) {
            handled = true;
        }
        if (mappingField != null && mappingField.textboxKeyTyped(typedChar, keyCode)) {
            handled = true;
        }
        if (!handled) {
            if (keyCode == Keyboard.KEY_RETURN) {
                if (keyField != null && keyField.isFocused()) {
                    addMappingFromUI();
                } else {
                    uploadSelected();
                }
                return;
            }
            super.keyTyped(typedChar, keyCode);
        }
    }

    private boolean isPointInRegion(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        // ===== 面板底 =====
        drawRect(left - 2, top - 2, left + panelW + 2, top + panelH + 2, 0xE0101010);
        drawRect(left - 2, top - 2, left + panelW + 2, top + 1, 0xFF4A90D9);

        // ===== 1. 标题栏 =====
        String title = translate("ae2_qof.select_provider");
        this.fontRendererObj.drawStringWithShadow(title, left + PAD, headerY, 0xFFFFFFFF);

        String count = translate("ae2_qof.select_provider.count", filtered.size(), groups.size());
        this.fontRendererObj.drawStringWithShadow(
            count,
            left + panelW - PAD - this.fontRendererObj.getStringWidth(count),
            headerY,
            0xFF9FB6CE);

        String subtitle = recipeMap != null && !recipeMap.isEmpty()
            ? translate("ae2_qof.select_provider.recipe", recipeMap)
            : "";
        if (!subtitle.isEmpty()) {
            int subtitleX = left + PAD + this.fontRendererObj.getStringWidth(title) + 10;
            int countX = left + panelW - PAD - this.fontRendererObj.getStringWidth(count) - 10;
            if (subtitleX + this.fontRendererObj.getStringWidth(subtitle) > countX) {
                subtitle = trim(subtitle, Math.max(0, countX - subtitleX));
            }
            this.fontRendererObj.drawStringWithShadow(subtitle, subtitleX, headerY, 0xFF7F8C9B);
        }

        if (searchBox != null) {
            searchBox.drawTextBox();
            if (searchBox.getText()
                .isEmpty()) {
                this.fontRendererObj.drawStringWithShadow(
                    EnumChatFormatting.DARK_GRAY + translate("ae2_qof.select_provider.search"),
                    searchBox.xPosition + 4,
                    searchBox.yPosition + 5,
                    0xFF808080);
            }
        }

        // ===== 2. 机器列表 =====
        int y = listTopY;
        int end = Math.min(scroll + rows, filtered.size());
        for (int i = scroll; i < end; i++) {
            GroupEntry entry = filtered.get(i);
            int rowY = y + (i - scroll) * (ROW_H + ROW_GAP);
            boolean hovered = mouseX >= left && mouseX <= left + panelW && mouseY >= rowY
                && mouseY <= rowY + ROW_H;
            boolean isSelected = i == selected;
            int bg = isSelected ? 0xFF2E5C8A : (hovered ? 0xFF2A2A32 : 0xFF1B1B22);
            drawRect(left + 1, rowY, left + panelW - 1, rowY + ROW_H, bg);
            drawRect(left + 1, rowY, left + 3, rowY + ROW_H, isSelected ? 0xFF6FB3F0 : 0xFF3A3A44);

            if (entry.icon != null) {
                this.itemRender.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(),
                    entry.icon, left + 6, rowY + 3);
                this.itemRender.renderItemOverlayIntoGUI(this.fontRendererObj, this.mc.getTextureManager(),
                    entry.icon, left + 6, rowY + 3, "");
            } else {
                drawRect(left + 6, rowY + 3, left + 22, rowY + 19, 0xFF3A3A44);
            }

            String capacity = entry.bestEmpty + "/" + entry.bestTotal;
            if (entry.count > 1) {
                capacity = entry.bestEmpty + "/" + entry.bestTotal + "  x" + entry.count;
            }
            int capW = this.fontRendererObj.getStringWidth(capacity);

            this.fontRendererObj.drawStringWithShadow(
                trim(entry.name, panelW - 60 - capW),
                left + 26,
                rowY + 6,
                isSelected ? 0xFFFFFFFF : 0xFFDDDDDD);

            int capColor = entry.bestTotal > 0 && entry.bestEmpty <= 0 ? 0xFFFF6B6B : 0xFF66CC88;
            this.fontRendererObj.drawStringWithShadow(capacity, left + panelW - 10 - capW, rowY + 6, capColor);
        }

        if (filtered.isEmpty()) {
            drawCentredString(
                translate("ae2_qof.select_provider.empty"),
                left + panelW / 2,
                listTopY + 24,
                0xFFFF8888);
        }

        if (filtered.size() > rows) {
            int trackTop = listTopY;
            int trackH = rows * (ROW_H + ROW_GAP);
            int barH = Math.max(12, trackH * rows / filtered.size());
            int maxScroll = filtered.size() - rows;
            int barY = trackTop + (trackH - barH) * scroll / Math.max(1, maxScroll);
            drawRect(left + panelW - 3, trackTop, left + panelW - 1, trackTop + trackH, 0xFF202028);
            drawRect(left + panelW - 3, barY, left + panelW - 1, barY + barH, 0xFF6FB3F0);
        }

        // ===== 3. 配方映射区（本模组自有需求） =====
        drawRect(left + PAD, actionY + ACTION_H + 3, left + panelW - PAD, actionY + ACTION_H + 4, 0xFF44444C);
        this.fontRendererObj.drawStringWithShadow(
            translate("ae2_qof.select_provider.mapping_title"),
            left + PAD,
            mapHeaderY,
            0xFF8FB8E0);

        String keyLabel = translate("ae2_qof.select_provider.mapping_key");
        String valLabel = translate("ae2_qof.select_provider.mapping_value");
        this.fontRendererObj.drawStringWithShadow(keyLabel, left + PAD, mapLabelY, 0xFF909090);
        if (keyField != null) {
            this.fontRendererObj.drawStringWithShadow(
                valLabel,
                keyField.xPosition + keyField.width + 24,
                mapLabelY,
                0xFF909090);
        }
        if (keyField != null) {
            keyField.drawTextBox();
            if (keyField.getText()
                .isEmpty()) {
                this.fontRendererObj.drawStringWithShadow(
                    EnumChatFormatting.DARK_GRAY + translate("ae2_qof.select_provider.mapping_key_hint"),
                    keyField.xPosition + 4,
                    keyField.yPosition + 5,
                    0xFF707070);
            }
            this.fontRendererObj.drawStringWithShadow(
                "->",
                keyField.xPosition + keyField.width + 7,
                keyField.yPosition + 4,
                0xFFAAAAAA);
        }
        if (mappingField != null) {
            mappingField.drawTextBox();
            if (mappingField.getText()
                .isEmpty()) {
                this.fontRendererObj.drawStringWithShadow(
                    EnumChatFormatting.DARK_GRAY + translate("ae2_qof.select_provider.mapping_value_hint"),
                    mappingField.xPosition + 4,
                    mappingField.yPosition + 5,
                    0xFF707070);
            }
        }

        // ===== 4. 底部提示 =====
        drawCentredString(translate("ae2_qof.select_provider.hint"), left + panelW / 2, hintY, 0xFF808080);

        super.drawScreen(mouseX, mouseY, partialTicks);

        // 悬浮说明放在按钮之后，保证盖在最上层
        int hoverIdx = rowIndexAt(mouseX, mouseY);
        if (hoverIdx >= 0) {
            GroupEntry entry = filtered.get(hoverIdx);
            List<String> tip = new ArrayList<String>();
            tip.add(entry.name);
            tip.add(
                EnumChatFormatting.GRAY
                    + translate("ae2_qof.select_provider.slots", entry.bestEmpty, entry.bestTotal));
            if (entry.count > 1) {
                tip.add(EnumChatFormatting.GRAY + translate("ae2_qof.select_provider.duplicates", entry.count));
            }
            this.drawHoveringText(tip, mouseX, mouseY, this.fontRendererObj);
        }
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
