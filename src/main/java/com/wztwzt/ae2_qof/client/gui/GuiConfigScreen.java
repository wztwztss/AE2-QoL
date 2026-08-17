package com.wztwzt.ae2_qof.client.gui;

import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import com.wztwzt.ae2_qof.Config;
import com.wztwzt.ae2_qof.network.ConfigSetPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * 游戏内配置页面（Mods → AE2 QoL → Config）：
 * 编辑 io_port_rate / smart_doubling_max_rounds / nei_overlay_enabled，
 * 点击「应用」对改动项发送 C2S 包，由服务端校验 OP 权限并广播回所有客户端。
 */
@SideOnly(Side.CLIENT)
public class GuiConfigScreen extends GuiScreen {

    private static final int APPLY_ID = 1;
    private static final int CLOSE_ID = 2;

    private final GuiScreen parent;

    private GuiTextField ioField;
    private GuiTextField roundsField;
    private GuiTextField overlayField;
    private GuiTextField focusField;
    private String statusText;

    public GuiConfigScreen(GuiScreen parent) {
        this.parent = parent;
        this.statusText = "";
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(APPLY_ID, this.width / 2 - 155, this.height - 30, 150, 20, "应用"));
        this.buttonList.add(new GuiButton(CLOSE_ID, this.width / 2 + 5, this.height - 30, 150, 20, "关闭"));

        int left = this.width / 2 - 100;
        int y0 = this.height / 2 - 55;
        this.ioField = new GuiTextField(this.fontRendererObj, left, y0, 200, 20);
        this.ioField.setFocused(false);
        this.ioField.setText(Integer.toString(Config.exIOPortTransferContentsRate));
        this.roundsField = new GuiTextField(this.fontRendererObj, left, y0 + 38, 200, 20);
        this.roundsField.setFocused(false);
        this.roundsField.setText(Integer.toString(Config.smartDoublingMaxRounds));
        this.overlayField = new GuiTextField(this.fontRendererObj, left, y0 + 76, 200, 20);
        this.overlayField.setFocused(false);
        this.overlayField.setText(Boolean.toString(Config.neiOverlayEnabled));
        this.focusField = this.ioField;
        this.focusField.setFocused(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int centerX = this.width / 2;
        this.drawCenteredString(this.fontRendererObj, "AE2 QoL 配置", centerX, this.height / 2 - 85, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "强化 IO 端口传输倍率 (io_port_rate)", this.ioField.xPosition - 10,
            this.ioField.yPosition - 10, 0xA0A0A0);
        this.drawString(this.fontRendererObj, "智能倍增最大轮数 (smart_doubling_max_rounds, 0=不限)",
            this.roundsField.xPosition - 10, this.roundsField.yPosition - 10, 0xA0A0A0);
        this.drawString(this.fontRendererObj, "NEI 叠加层 (nei_overlay_enabled, true/false)",
            this.overlayField.xPosition - 10, this.overlayField.yPosition - 10, 0xA0A0A0);

        this.ioField.drawTextBox();
        this.roundsField.drawTextBox();
        this.overlayField.drawTextBox();

        if (!this.statusText.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, this.statusText, centerX, this.height - 55, 0xFF5555);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.ioField != null) {
            this.ioField.mouseClicked(mouseX, mouseY, mouseButton);
            this.roundsField.mouseClicked(mouseX, mouseY, mouseButton);
            this.overlayField.mouseClicked(mouseX, mouseY, mouseButton);
            this.focusField = this.ioField.isFocused() ? this.ioField
                : (this.roundsField.isFocused() ? this.roundsField : this.overlayField);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            if (this.focusField == this.ioField) {
                this.focusField = this.roundsField;
            } else if (this.focusField == this.roundsField) {
                this.focusField = this.overlayField;
            } else {
                this.focusField = this.ioField;
            }
            this.ioField.setFocused(this.focusField == this.ioField);
            this.roundsField.setFocused(this.focusField == this.roundsField);
            this.overlayField.setFocused(this.focusField == this.overlayField);
            return;
        }
        if (this.focusField != null && this.focusField.isFocused()) {
            this.focusField.textboxKeyTyped(typedChar, keyCode);
        } else if (keyCode == Keyboard.KEY_RETURN) {
            this.applyChanges();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == APPLY_ID) {
            this.applyChanges();
        } else if (button.id == CLOSE_ID) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void applyChanges() {
        String status = this.applySingle("io_port_rate", this.ioField.getText());
        String status2 = this.applySingle("smart_doubling_max_rounds", this.roundsField.getText());
        String status3 = this.applySingle("nei_overlay_enabled", this.overlayField.getText());
        String combined = joinStatus(status, status2, status3);
        this.statusText = combined.isEmpty() ? "已应用（若为多人服务器需 OP 权限）" : combined;
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
}