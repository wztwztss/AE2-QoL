package com.wztwzt.ae2_qof.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.common.RecipeMapNameConfig;
import com.wztwzt.ae2_qof.network.ConfigSetPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.util.RecipeNameUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * 游戏内配置页面（Mods → AE2 QoL → Config）：
 * - 配置页：编辑 io_port_rate / smart_doubling_max_rounds / nei_overlay_enabled，
 *   点击「应用」对改动项发送 C2S 包，由服务端校验 OP 权限并广播回所有客户端。
 * - 映射页（三个子页）：展示并编辑配方名映射（recipe_names.json）、记住的供应器
 *   （remembered_providers.json），以及全部 GT 配方池 UID 参考（配方参考）。
 */
@SideOnly(Side.CLIENT)
public class GuiConfigScreen extends GuiScreen {

    private static final int APPLY_ID = 1;
    private static final int TO_MAP_ID = 2;
    private static final int CLOSE_ID = 3;
    private static final int BACK_ID = 4;
    private static final int SECTION_MAP_ID = 9;
    private static final int SECTION_PROV_ID = 10;
    private static final int SECTION_REF_ID = 14;
    private static final int ADD_ID = 11;
    private static final int DEL_KEY_ID = 12;
    private static final int DEL_VALUE_ID = 13;

    private static final int ROW_HEIGHT = 12;
    private static final int LIST_X = 155; // 相对屏幕中心：列表左边缘 = center - 155
    private static final int EDIT_LIST_W = 200;

    private final GuiScreen parent;
    private int page; // 0 = 配置，1 = 映射
    private int subTab; // 0 = 配方名映射，1 = 记住的供应器，2 = 配方参考

    private GuiTextField ioField;
    private GuiTextField roundsField;
    private GuiTextField overlayField;
    private GuiTextField mapKeyField;
    private GuiTextField mapValueField;
    private GuiTextField provKeyField;
    private GuiTextField provValueField;
    private GuiTextField refFilterField;
    private GuiTextField focusField;
    private String statusText;

    private List<Entry<String, String>> mapEntries = new ArrayList<Entry<String, String>>();
    private List<Entry<String, String>> provEntries = new ArrayList<Entry<String, String>>();
    private List<String> refUids = new ArrayList<String>();
    private List<String> refFiltered = new ArrayList<String>();
    private int mapScroll = 0;
    private int provScroll = 0;
    private int refScroll = 0;
    private int mapSelected = -1;
    private int provSelected = -1;

    public GuiConfigScreen(GuiScreen parent) {
        this.parent = parent;
        this.page = 0;
        this.subTab = 0;
        this.statusText = "";
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - 110;
        int y0 = 40;

        this.ioField = new GuiTextField(this.fontRendererObj, left, y0 + 10, 220, 18);
        this.ioField.setText(Integer.toString(Config.exIOPortTransferContentsRate));
        this.roundsField = new GuiTextField(this.fontRendererObj, left, y0 + 58, 220, 18);
        this.roundsField.setText(Integer.toString(Config.smartDoublingMaxRounds));
        this.overlayField = new GuiTextField(this.fontRendererObj, left, y0 + 106, 220, 18);
        this.overlayField.setText(Boolean.toString(Config.neiOverlayEnabled));

        int editX = this.width / 2 + 50;
        int fieldW = this.width - editX - 20;
        this.mapKeyField = new GuiTextField(this.fontRendererObj, editX, 68, fieldW, 18);
        this.mapValueField = new GuiTextField(this.fontRendererObj, editX, 106, fieldW, 18);
        this.provKeyField = new GuiTextField(this.fontRendererObj, editX, 68, fieldW, 18);
        this.provValueField = new GuiTextField(this.fontRendererObj, editX, 106, fieldW, 18);
        this.refFilterField = new GuiTextField(this.fontRendererObj, this.width / 2 - LIST_X + 28, 58,
            this.width - 40 - 28, 16);
        this.refFilterField.setMaxStringLength(64);

        this.focusField = null;
        if (this.page == 0) {
            this.buttonList.add(new GuiButton(APPLY_ID, this.width / 2 - 155, this.height - 30, 100, 20, "应用"));
            this.buttonList.add(new GuiButton(TO_MAP_ID, this.width / 2 - 50, this.height - 30, 100, 20, "映射编辑 →"));
            this.buttonList.add(new GuiButton(CLOSE_ID, this.width / 2 + 55, this.height - 30, 100, 20, "关闭"));
            this.focusField = this.ioField;
            this.focusField.setFocused(true);
        } else {
            this.refreshEntries();
            int centerX = this.width / 2;
            this.buttonList.add(new GuiButton(BACK_ID, centerX - 155, this.height - 30, 100, 20, "← 设置"));
            this.buttonList.add(new GuiButton(SECTION_MAP_ID, centerX - 144, 20, 92, 16, "配方名映射"));
            this.buttonList.add(new GuiButton(SECTION_PROV_ID, centerX - 46, 20, 92, 16, "记住的供应器"));
            this.buttonList.add(new GuiButton(SECTION_REF_ID, centerX + 52, 20, 92, 16, "配方参考"));
            this.buttonList.add(new GuiButton(CLOSE_ID, centerX + 55, this.height - 30, 100, 20, "关闭"));
            if (this.subTab != 2) {
                int btnW = (fieldW - 6) / 2;
                this.buttonList.add(new GuiButton(ADD_ID, editX, 132, btnW, 20, "添加/更新"));
                this.buttonList.add(new GuiButton(DEL_KEY_ID, editX + btnW + 6, 132, btnW, 20, "删除(选中)"));
                this.buttonList.add(new GuiButton(DEL_VALUE_ID, editX, 156, fieldW, 20, "删除(按值)"));
                this.focusField = this.subTab == 0 ? this.mapKeyField : this.provKeyField;
            } else {
                this.focusField = this.refFilterField;
                this.refreshRefFilter();
            }
            this.focusField.setFocused(true);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int centerX = this.width / 2;

        if (this.page == 0) {
            this.drawCenteredString(this.fontRendererObj, "AE2 QoL 配置", centerX, 20, 0xFFFFFF);
            int left = this.ioField.xPosition;
            this.drawString(this.fontRendererObj, "强化 IO 端口传输倍率 (io_port_rate)  范围 1~2147483647",
                left - 10, this.ioField.yPosition - 11, 0xA0A0A0);
            this.drawString(this.fontRendererObj, "智能倍增最大轮数 (smart_doubling_max_rounds, 0=不限)  范围 0~2147483647",
                left - 10, this.roundsField.yPosition - 11, 0xA0A0A0);
            this.drawString(this.fontRendererObj, "NEI 叠加层 (nei_overlay_enabled, true/false)",
                left - 10, this.overlayField.yPosition - 11, 0xA0A0A0);
            this.ioField.drawTextBox();
            this.roundsField.drawTextBox();
            this.overlayField.drawTextBox();
        } else {
            this.drawCenteredString(this.fontRendererObj, "名字映射编辑", centerX, 6, 0xFFFFFF);
            if (this.subTab == 2) {
                this.drawRefTab();
            } else {
                this.drawEditTab();
            }
        }

        if (this.page == 0 || this.subTab != 2) {
            if (!this.statusText.isEmpty()) {
                this.drawCenteredString(this.fontRendererObj, this.statusText, centerX, this.height - 102, 0xFF5555);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int editListY() {
        return 58;
    }

    private int editListH() {
        return Math.min(150, this.height - 92);
    }

    private void drawEditTab() {
        int centerX = this.width / 2;
        int listX = centerX - LIST_X;
        int listY = this.editListY();
        int listH = this.editListH();

        boolean activeMap = this.subTab == 0;
        List<Entry<String, String>> entries = activeMap ? this.mapEntries : this.provEntries;
        int scroll = activeMap ? this.mapScroll : this.provScroll;
        int selected = activeMap ? this.mapSelected : this.provSelected;

        // 列表头（分类说明）
        this.drawCenteredString(this.fontRendererObj,
            activeMap ? "配方名映射 (recipe_names.json，NEI 搜索词)"
                : "记住的供应器 (remembered_providers.json，自动上传)",
            listX + EDIT_LIST_W / 2, 38, 0xFFFFFF);

        // 副标题（0.8 倍缩小灰色小字，居中于列表框上方，不与其它元素重叠）
        String hint = "点击列表行选中并回填编辑框，改动即时写盘";
        int hintW = this.fontRendererObj.getStringWidth(hint);
        GL11.glPushMatrix();
        GL11.glTranslatef(listX + EDIT_LIST_W / 2, 48, 0.0F);
        GL11.glScalef(0.8F, 0.8F, 1.0F);
        this.fontRendererObj.drawStringWithShadow(hint, (int) (-hintW * 0.4F), 0, 0x808080);
        GL11.glPopMatrix();

        // 列表
        this.drawRect(listX - 2, listY - 2, listX + EDIT_LIST_W + 2, listY + listH + 2, 0x90000000);
        int visible = listH / ROW_HEIGHT;
        int maxScroll = Math.max(0, entries.size() - visible);
        for (int i = 0; i < visible && scroll + i < entries.size(); i++) {
            int idx = scroll + i;
            int rowY = listY + i * ROW_HEIGHT;
            Entry<String, String> e = entries.get(idx);
            if (idx == selected) {
                this.drawRect(listX, rowY, listX + EDIT_LIST_W, rowY + ROW_HEIGHT, 0xA0FFFFFF);
            }
            String label = this.fontRendererObj.trimStringToWidth(
                e.getKey() + " \u2192 " + e.getValue(), EDIT_LIST_W - 8);
            this.drawString(this.fontRendererObj, label, listX + 4, rowY + 2, idx == selected ? 0x000000 : 0xFFFFFF);
        }
        this.drawCenteredString(this.fontRendererObj,
            "共 " + entries.size() + " 条   " + (scroll > 0 ? "↑" : "  ") + (scroll < maxScroll ? "↓" : "  "),
            listX + EDIT_LIST_W / 2, listY + listH + 2, 0x808080);

        // 编辑区
        GuiTextField keyField = activeMap ? this.mapKeyField : this.provKeyField;
        GuiTextField valueField = activeMap ? this.mapValueField : this.provValueField;
        this.drawString(this.fontRendererObj,
            activeMap ? "配方 key（如 compressor）" : "配方名",
            keyField.xPosition, keyField.yPosition - 11, 0xA0A0A0);
        this.drawString(this.fontRendererObj,
            activeMap ? "中文搜索词（如 压缩机）" : "供应器名",
            valueField.xPosition, valueField.yPosition - 11, 0xA0A0A0);
        keyField.drawTextBox();
        valueField.drawTextBox();
    }

    private void drawRefTab() {
        int centerX = this.width / 2;
        int listX = centerX - LIST_X;
        int listW = this.width - 40;
        int listY = 82;
        int listH = Math.min(240, this.height - 90 - listY);

        this.drawCenteredString(this.fontRendererObj, "全部 GT 配方池参考（供「记住的供应器」配方名对照）",
            centerX, 38, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "筛选:", listX, 62, 0xA0A0A0);
        this.refFilterField.drawTextBox();

        this.drawRect(listX - 2, listY - 2, listX + listW + 2, listY + listH + 2, 0x90000000);
        int visible = listH / ROW_HEIGHT;
        int maxScroll = Math.max(0, this.refFiltered.size() - visible);
        for (int i = 0; i < visible && this.refScroll + i < this.refFiltered.size(); i++) {
            int idx = this.refScroll + i;
            int rowY = listY + i * ROW_HEIGHT;
            String label = this.fontRendererObj.trimStringToWidth(this.refFiltered.get(idx), listW - 8);
            this.drawString(this.fontRendererObj, label, listX + 4, rowY + 2, 0xFFFFFF);
        }
        this.drawCenteredString(this.fontRendererObj,
            "共 " + this.refUids.size() + " 条  显示 " + this.refFiltered.size()
                + "   " + (this.refScroll > 0 ? "↑" : "  ") + (this.refScroll < maxScroll ? "↓" : "  "),
            centerX, listY + listH + 2, 0x808080);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.page == 0) {
            this.ioField.mouseClicked(mouseX, mouseY, mouseButton);
            this.roundsField.mouseClicked(mouseX, mouseY, mouseButton);
            this.overlayField.mouseClicked(mouseX, mouseY, mouseButton);
            this.focusField = this.ioField.isFocused() ? this.ioField
                : (this.roundsField.isFocused() ? this.roundsField : this.overlayField);
        } else if (this.subTab == 2) {
            this.refFilterField.mouseClicked(mouseX, mouseY, mouseButton);
            this.focusField = this.refFilterField;
        } else {
            int hit = this.hitRow(mouseX, mouseY);
            if (hit >= 0) {
                if (this.subTab == 0) {
                    this.mapSelected = hit;
                    this.mapKeyField.setText(this.mapEntries.get(hit).getKey());
                    this.mapValueField.setText(this.mapEntries.get(hit).getValue());
                } else {
                    this.provSelected = hit;
                    this.provKeyField.setText(this.provEntries.get(hit).getKey());
                    this.provValueField.setText(this.provEntries.get(hit).getValue());
                }
                return;
            }
            this.mapKeyField.mouseClicked(mouseX, mouseY, mouseButton);
            this.mapValueField.mouseClicked(mouseX, mouseY, mouseButton);
            this.provKeyField.mouseClicked(mouseX, mouseY, mouseButton);
            this.provValueField.mouseClicked(mouseX, mouseY, mouseButton);
            this.focusField = this.subTab == 0
                ? (this.mapKeyField.isFocused() ? this.mapKeyField : this.mapValueField)
                : (this.provKeyField.isFocused() ? this.provKeyField : this.provValueField);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (this.focusField != null && this.focusField.isFocused()) {
            this.focusField.textboxKeyTyped(typedChar, keyCode);
            if (this.focusField == this.refFilterField) {
                this.refreshRefFilter();
                this.refScroll = 0;
            }
        } else if (keyCode == Keyboard.KEY_RETURN) {
            if (this.page == 0) {
                this.applyConfigChanges();
            } else if (this.subTab != 2) {
                this.applyAdd();
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || this.page != 1) {
            return;
        }
        int dir = wheel > 0 ? -1 : 1;
        if (this.subTab == 0) {
            this.mapScroll = clampScroll(this.mapScroll + dir, this.mapEntries.size(), this.editListH());
        } else if (this.subTab == 1) {
            this.provScroll = clampScroll(this.provScroll + dir, this.provEntries.size(), this.editListH());
        } else {
            this.refScroll = clampScroll(this.refScroll + dir, this.refFiltered.size(), 240);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case APPLY_ID:
                this.applyConfigChanges();
                break;
            case TO_MAP_ID:
                this.page = 1;
                this.statusText = "";
                this.initGui();
                break;
            case BACK_ID:
                this.page = 0;
                this.statusText = "";
                this.initGui();
                break;
            case SECTION_MAP_ID:
                this.subTab = 0;
                this.statusText = "";
                this.initGui();
                break;
            case SECTION_PROV_ID:
                this.subTab = 1;
                this.statusText = "";
                this.initGui();
                break;
            case SECTION_REF_ID:
                this.subTab = 2;
                this.statusText = "";
                this.initGui();
                break;
            case ADD_ID:
                this.applyAdd();
                break;
            case DEL_KEY_ID:
                this.applyDeleteKey();
                break;
            case DEL_VALUE_ID:
                this.applyDeleteValue();
                break;
            case CLOSE_ID:
            default:
                this.mc.displayGuiScreen(this.parent);
                break;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---------------------------------------------------------------- 配置页

    private void applyConfigChanges() {
        String status = this.applySingle("io_port_rate", this.ioField.getText());
        String status2 = this.applySingle("smart_doubling_max_rounds", this.roundsField.getText());
        String status3 = this.applySingle("nei_overlay_enabled", this.overlayField.getText());
        String combined = joinStatus(status, status2, status3);
        this.statusText = combined.isEmpty() ? "已应用（多人服务器需 OP 权限）" : combined;
    }

    private String applySingle(String key, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "请输入 " + key + " 的值";
        }
        if (!isParseable(key, trimmed)) {
            return key + " 输入无效";
        }
        ModNetwork.CHANNEL.sendToServer(new ConfigSetPacket(key, trimmed));
        return "";
    }

    private boolean isParseable(String key, String value) {
        try {
            if ("nei_overlay_enabled".equals(key)) {
                String lower = value.toLowerCase(Locale.ROOT);
                return lower.equals("true") || lower.equals("false");
            }
            Integer.parseInt(value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private String joinStatus(String a, String b, String c) {
        StringBuilder sb = new StringBuilder();
        for (String s : new String[] { a, b, c }) {
            if (!s.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(s);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 映射页

    private void refreshEntries() {
        this.mapEntries.clear();
        for (Entry<String, String> e : RecipeNameUtil.getMappingsView()
            .entrySet()) {
            this.mapEntries.add(e);
        }
        this.mapEntries.sort(Entry.comparingByKey());
        this.provEntries.clear();
        for (Entry<String, String> e : ClientState.rememberedProviders.entrySet()) {
            this.provEntries.add(e);
        }
        this.provEntries.sort(Entry.comparingByKey());
        this.mapScroll = clampScroll(this.mapScroll, this.mapEntries.size(), this.editListH());
        this.provScroll = clampScroll(this.provScroll, this.provEntries.size(), this.editListH());
        if (this.refUids.isEmpty()) {
            this.refUids = RecipeNameUtil.getAllRecipeMapUids();
            this.refFiltered = new ArrayList<String>(this.refUids);
        }
    }

    private void refreshRefFilter() {
        String q = this.refFilterField.getText() == null ? "" : this.refFilterField.getText().trim()
            .toLowerCase(Locale.ROOT);
        this.refFiltered.clear();
        for (String uid : this.refUids) {
            if (q.isEmpty() || uid.toLowerCase(Locale.ROOT)
                .contains(q)) {
                this.refFiltered.add(refRow(uid));
            } else {
                String zh = RecipeMapNameConfig.resolveSearchKeyword(uid);
                if (zh != null && !zh.equals(uid) && zh.toLowerCase(Locale.ROOT)
                    .contains(q)) {
                    this.refFiltered.add(refRow(uid));
                }
            }
        }
    }

    private String refRow(String uid) {
        String zh = RecipeMapNameConfig.resolveSearchKeyword(uid);
        if (zh != null && !zh.equals(uid)) {
            return uid + " \u2192 " + zh;
        }
        return uid;
    }

    private int hitRow(int mouseX, int mouseY) {
        int listX = this.width / 2 - LIST_X;
        int listY = this.editListY();
        int listH = this.editListH();
        if (mouseX < listX || mouseX >= listX + EDIT_LIST_W || mouseY < listY || mouseY >= listY + listH) {
            return -1;
        }
        List<Entry<String, String>> entries = this.subTab == 0 ? this.mapEntries : this.provEntries;
        int scroll = this.subTab == 0 ? this.mapScroll : this.provScroll;
        int idx = scroll + (mouseY - listY) / ROW_HEIGHT;
        return idx >= 0 && idx < entries.size() ? idx : -1;
    }

    private int clampScroll(int scroll, int count, int listH) {
        int visible = Math.max(1, listH / ROW_HEIGHT);
        return Math.max(0, Math.min(scroll, Math.max(0, count - visible)));
    }

    private void applyAdd() {
        String key = this.currentKey();
        String value = this.currentValue();
        if (key.isEmpty() || value.isEmpty()) {
            this.statusText = this.subTab == 0 ? "配方 key 与中文搜索词都不能为空" : "配方名与供应器名都不能为空";
            return;
        }
        if (this.subTab == 0) {
            boolean ok = RecipeNameUtil.addOrUpdateMapping(key, value);
            this.statusText = ok ? "已添加/更新配方映射：" + key + " → " + value : "配方映射更新失败";
        } else {
            ClientState.rememberProvider(key, value);
            this.statusText = "已添加/更新供应器映射：" + key + " → " + value;
        }
        this.refreshEntries();
    }

    private void applyDeleteKey() {
        String key = this.currentKey();
        if (key.isEmpty()) {
            this.statusText = this.subTab == 0 ? "请输入要删除的配方 key" : "请输入要删除的配方名";
            return;
        }
        boolean removed;
        if (this.subTab == 0) {
            removed = RecipeNameUtil.removeMappingByKey(key);
            this.statusText = removed ? "已删除配方映射：" + key : "未找到配方 key " + key;
            this.mapSelected = -1;
        } else {
            removed = ClientState.removeRememberedProvider(key);
            this.statusText = removed ? "已删除供应器映射：" + key : "未找到配方名 " + key;
            this.provSelected = -1;
        }
        this.refreshEntries();
    }

    private void applyDeleteValue() {
        String value = this.currentValue();
        if (value.isEmpty()) {
            this.statusText = this.subTab == 0 ? "请输入要删除的中文搜索词" : "请输入要删除的供应器名";
            return;
        }
        int removed;
        if (this.subTab == 0) {
            removed = RecipeNameUtil.removeMappingsByCnValue(value);
            this.statusText = removed > 0 ? "已删除 " + removed + " 条配方映射" : "未找到中文搜索词 " + value;
        } else {
            removed = ClientState.removeRememberedProvidersByValue(value);
            this.statusText = removed > 0 ? "已删除 " + removed + " 条供应器映射" : "未找到供应器名 " + value;
        }
        this.refreshEntries();
    }

    private String currentKey() {
        GuiTextField f = this.subTab == 0 ? this.mapKeyField : this.provKeyField;
        return f.getText() == null ? "" : f.getText().trim();
    }

    private String currentValue() {
        GuiTextField f = this.subTab == 0 ? this.mapValueField : this.provValueField;
        return f.getText() == null ? "" : f.getText().trim();
    }
}