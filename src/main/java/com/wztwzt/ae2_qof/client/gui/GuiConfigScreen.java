package com.wztwzt.ae2_qof.client.gui;

import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.network.ConfigSetPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.util.RecipeNameUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * 游戏内配置页面（Mods → AE2 QoL → Config）：
 * - 配置页：编辑 io_port_rate / smart_doubling_max_rounds / nei_overlay_enabled，
 *   点击「应用」对改动项发送 C2S 包，由服务端校验 OP 权限并广播回所有客户端。
 * - 映射页：编辑配方名映射（recipe_names.json）与记住的供应器（remembered_providers.json），
 *   客户端本地即时生效并热写入文件。
 */
@SideOnly(Side.CLIENT)
public class GuiConfigScreen extends GuiScreen {

    private static final int APPLY_ID = 1;
    private static final int TO_MAP_ID = 2;
    private static final int CLOSE_ID = 3;
    private static final int BACK_ID = 4;
    private static final int MAP_ADD_ID = 5;
    private static final int MAP_REMOVE_ID = 6;
    private static final int PROV_ADD_ID = 7;
    private static final int PROV_REMOVE_ID = 8;

    private final GuiScreen parent;
    private int page; // 0 = 配置，1 = 映射

    private GuiTextField ioField;
    private GuiTextField roundsField;
    private GuiTextField overlayField;
    private GuiTextField mapKeyField;
    private GuiTextField mapValueField;
    private GuiTextField provKeyField;
    private GuiTextField provValueField;
    private GuiTextField focusField;
    private String statusText;

    public GuiConfigScreen(GuiScreen parent) {
        this.parent = parent;
        this.page = 0;
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

        int mapLeft = this.width / 2 - 110;
        int my0 = 64;
        this.mapKeyField = new GuiTextField(this.fontRendererObj, mapLeft, my0 + 10, 220, 18);
        this.mapValueField = new GuiTextField(this.fontRendererObj, mapLeft, my0 + 58, 220, 18);

        int py0 = 158;
        this.provKeyField = new GuiTextField(this.fontRendererObj, mapLeft, py0 + 10, 220, 18);
        this.provValueField = new GuiTextField(this.fontRendererObj, mapLeft, py0 + 58, 220, 18);

        this.focusField = null;
        if (this.page == 0) {
            this.buttonList.add(new GuiButton(APPLY_ID, this.width / 2 - 155, this.height - 30, 100, 20, "应用"));
            this.buttonList.add(new GuiButton(TO_MAP_ID, this.width / 2 - 50, this.height - 30, 100, 20, "映射编辑 →"));
            this.buttonList.add(new GuiButton(CLOSE_ID, this.width / 2 + 55, this.height - 30, 100, 20, "关闭"));
            this.focusField = this.ioField;
            this.focusField.setFocused(true);
        } else {
            this.buttonList.add(new GuiButton(BACK_ID, this.width / 2 - 155, this.height - 30, 100, 20, "← 设置"));
            this.buttonList.add(new GuiButton(MAP_ADD_ID, this.width / 2 - 50, this.height - 78, 100, 20, "配方添加/更新"));
            this.buttonList.add(new GuiButton(MAP_REMOVE_ID, this.width / 2 + 55, this.height - 78, 100, 20, "配方删除(按值)"));
            this.buttonList.add(new GuiButton(PROV_ADD_ID, this.width / 2 - 50, this.height - 30, 100, 20, "供应器添加/更新"));
            this.buttonList.add(new GuiButton(PROV_REMOVE_ID, this.width / 2 + 55, this.height - 30, 100, 20, "供应器删除"));
            this.focusField = this.mapKeyField;
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
            this.drawCenteredString(this.fontRendererObj, "名字映射编辑", centerX, 20, 0xFFFFFF);
            int left = this.mapKeyField.xPosition;
            this.drawCenteredString(this.fontRendererObj, "—— 配方名映射 (recipe_names.json，NEI 搜索词) ——", centerX, 48, 0xFFFFFF);
            this.drawString(this.fontRendererObj, "配方 key（如 compressor）", left - 10,
                this.mapKeyField.yPosition - 11, 0xA0A0A0);
            this.drawString(this.fontRendererObj, "中文搜索词（如 压缩机）", left - 10,
                this.mapValueField.yPosition - 11, 0xA0A0A0);
            this.mapKeyField.drawTextBox();
            this.mapValueField.drawTextBox();

            this.drawCenteredString(this.fontRendererObj, "—— 记住的供应器 (remembered_providers.json，自动上传) ——", centerX, 142, 0xFFFFFF);
            this.drawString(this.fontRendererObj, "配方名", left - 10, this.provKeyField.yPosition - 11, 0xA0A0A0);
            this.drawString(this.fontRendererObj, "供应器名", left - 10, this.provValueField.yPosition - 11, 0xA0A0A0);
            this.provKeyField.drawTextBox();
            this.provValueField.drawTextBox();
        }

        if (!this.statusText.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, this.statusText, centerX, this.height - 102, 0xFF5555);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
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
        } else {
            this.mapKeyField.mouseClicked(mouseX, mouseY, mouseButton);
            this.mapValueField.mouseClicked(mouseX, mouseY, mouseButton);
            this.provKeyField.mouseClicked(mouseX, mouseY, mouseButton);
            this.provValueField.mouseClicked(mouseX, mouseY, mouseButton);
            this.focusField = this.mapKeyField.isFocused() ? this.mapKeyField
                : (this.mapValueField.isFocused() ? this.mapValueField
                    : (this.provKeyField.isFocused() ? this.provKeyField : this.provValueField));
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
        } else if (keyCode == Keyboard.KEY_RETURN) {
            if (this.page == 0) {
                this.applyConfigChanges();
            }
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
            case MAP_ADD_ID:
                this.applyMapAdd();
                break;
            case MAP_REMOVE_ID:
                this.applyMapRemove();
                break;
            case PROV_ADD_ID:
                this.applyProvAdd();
                break;
            case PROV_REMOVE_ID:
                this.applyProvRemove();
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

    private void applyMapAdd() {
        String key = this.mapKeyField.getText() == null ? "" : this.mapKeyField.getText().trim();
        String value = this.mapValueField.getText() == null ? "" : this.mapValueField.getText().trim();
        if (key.isEmpty() || value.isEmpty()) {
            this.statusText = "配方 key 与中文搜索词都不能为空";
            return;
        }
        boolean ok = RecipeNameUtil.addOrUpdateMapping(key, value);
        this.statusText = ok ? "已添加/更新配方映射：" + key + " → " + value : "配方映射更新失败";
    }

    private void applyMapRemove() {
        String value = this.mapValueField.getText() == null ? "" : this.mapValueField.getText().trim();
        if (value.isEmpty()) {
            this.statusText = "请输入要删除的中文搜索词";
            return;
        }
        int removed = RecipeNameUtil.removeMappingsByCnValue(value);
        this.statusText = removed > 0 ? "已删除 " + removed + " 条配方映射" : "未找到中文搜索词 " + value;
    }

    private void applyProvAdd() {
        String key = this.provKeyField.getText() == null ? "" : this.provKeyField.getText().trim();
        String value = this.provValueField.getText() == null ? "" : this.provValueField.getText().trim();
        if (key.isEmpty() || value.isEmpty()) {
            this.statusText = "配方名与供应器名都不能为空";
            return;
        }
        ClientState.rememberProvider(key, value);
        this.statusText = "已添加/更新供应器映射：" + key + " → " + value;
    }

    private void applyProvRemove() {
        String key = this.provKeyField.getText() == null ? "" : this.provKeyField.getText().trim();
        if (key.isEmpty()) {
            this.statusText = "请输入要删除的配方名";
            return;
        }
        boolean removed = ClientState.removeRememberedProvider(key);
        this.statusText = removed ? "已删除供应器映射：" + key : "未找到配方名 " + key;
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
}