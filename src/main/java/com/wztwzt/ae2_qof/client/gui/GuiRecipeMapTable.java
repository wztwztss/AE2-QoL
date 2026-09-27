package com.wztwzt.ae2_qof.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.generator.RecipeMapNames;

/**
 * 「机器 ↔ 编码 对照表」（3.20.0-fix44）：批量样板生成器里点 [对照表] 打开。
 *
 * <h2>它解决什么</h2>
 * 以前要手打技术关键字（{@code gt.recipe.rolling}）才能选机器。本页把全部 RecipeMap 列成
 * **中文名 | 英文名 | 关键字** 的表格，**点整行 = 填入关键字并返回**，让你不必记编码。
 *
 * <h2>细节与口径</h2>
 * <ul>
 * <li>搜索框用**原版 {@link GuiTextField}** ⇒ 中文/输入法可正常使用（自绘 MUI 界面里 IME 不可用，
 * 见 {@link GuiTextInputDialog} 的说明）；</li>
 * <li>表格**每页 10 行**，底部"上一页 / 下一页 / 返回"，顶部显示"共 N 条 · 第 x/y 页"；</li>
 * <li>排序与匹配都来自 {@link RecipeMapNames}（匹配覆盖 关键字/中文名/英文名 三者任一）；</li>
 * <li>浅底深字、不使用 § 颜色码（用户 UI 偏好）。</li>
 * </ul>
 */
public class GuiRecipeMapTable extends GuiScreen {

    private static final int ROWS_PER_PAGE = 10;
    private static final int ROW_H = 14;

    private final Consumer<String> onPick;
    private GuiTextField search;
    private String filter = "";
    private int page = 0;
    private List<String> rows = new ArrayList<>();

    public GuiRecipeMapTable(Consumer<String> onPick) {
        this.onPick = onPick;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int cx = this.width / 2;
        int left = cx - 190;
        int top = this.height / 2 - 110;
        this.search = new GuiTextField(this.fontRendererObj, left + 40, top + 2, 200, 14);
        this.search.setMaxStringLength(64);
        this.search.setText(this.filter);
        this.search.setFocused(true);
        this.buttonList.add(new GuiButton(1, left + 120, top + 122, 60, 18, "上一页"));
        this.buttonList.add(new GuiButton(2, left + 184, top + 122, 60, 18, "下一页"));
        this.buttonList.add(new GuiButton(3, left + 300, top + 122, 60, 18, "返回"));
        refresh();
    }

    private void refresh() {
        try {
            this.rows = RecipeMapNames.matchIds(this.filter);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 对照表：匹配 RecipeMap 失败（列表按空处理）", t);
            this.rows = new ArrayList<>();
        }
        int pages = Math.max(1, (this.rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        if (this.page >= pages) this.page = pages - 1;
        if (this.page < 0) this.page = 0;
    }

    private int rowCount() {
        int from = this.page * ROWS_PER_PAGE;
        return Math.max(0, Math.min(ROWS_PER_PAGE, this.rows.size() - from));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        try {
            if (keyCode == 1) { // Esc
                this.mc.displayGuiScreen(null);
                return;
            }
            if (this.search != null && this.search.textboxKeyTyped(typedChar, keyCode)) {
                this.filter = this.search.getText();
                this.page = 0;
                refresh();
                return;
            }
            super.keyTyped(typedChar, keyCode);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 对照表按键处理失败", t);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 3) {
            this.mc.displayGuiScreen(null);
            return;
        }
        int pages = Math.max(1, (this.rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        if (button.id == 1) this.page = Math.max(0, this.page - 1);
        if (button.id == 2) this.page = Math.min(pages - 1, this.page + 1);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            if (this.search != null) this.search.mouseClicked(mouseX, mouseY, mouseButton);
            int idx = rowAt(mouseX, mouseY);
            if (idx < 0) return;
            int absolute = this.page * ROWS_PER_PAGE + idx;
            if (absolute < 0 || absolute >= this.rows.size()) return;
            String id = this.rows.get(absolute);
            MyMod.LOG.info("[AE2QoL] 对照表：选中 RecipeMap={}（中文名={}）", id, RecipeMapNames.chineseName(id));
            if (this.onPick != null) this.onPick.accept(id);
            this.mc.displayGuiScreen(null);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 对照表点击处理失败", t);
        }
    }

    /** 命中第几行（0 起）；未命中返回 -1。 */
    private int rowAt(int mouseX, int mouseY) {
        int left = this.width / 2 - 190;
        int top = this.height / 2 - 110;
        int tableTop = top + 24;
        if (mouseX < left || mouseX > left + 380) return -1;
        int idx = (mouseY - tableTop) / ROW_H;
        if (idx < 0 || idx >= rowCount()) return -1;
        return idx;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int cx = this.width / 2;
        int left = cx - 190;
        int top = this.height / 2 - 110;
        int pages = Math.max(1, (this.rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        this.drawString(this.fontRendererObj, "机器 ↔ 编码 对照表（点整行 = 填入关键字并返回）", left, top - 14, 0x202020);
        this.drawString(this.fontRendererObj, "搜索", left, top + 5, 0x202020);
        this.drawString(
            this.fontRendererObj,
            "共 " + this.rows.size() + " 条 · 第 " + (this.page + 1) + "/" + pages + " 页",
            left + 250,
            top + 5,
            0x505050);

        int tableTop = top + 24;
        // 表头
        drawRect(left, tableTop, left + 380, tableTop + ROW_H, 0xD8D8D8);
        this.drawString(this.fontRendererObj, "中文名", left + 4, tableTop + 3, 0x202020);
        this.drawString(this.fontRendererObj, "英文名", left + 124, tableTop + 3, 0x202020);
        this.drawString(this.fontRendererObj, "关键字", left + 250, tableTop + 3, 0x202020);
        // 数据行
        for (int i = 0; i < rowCount(); i++) {
            String id = this.rows.get(this.page * ROWS_PER_PAGE + i);
            int y = tableTop + ROW_H * (i + 1);
            int idx = i;
            boolean hover = idx == rowAt(mouseX, mouseY);
            drawRect(
                left,
                y,
                left + 380,
                y + ROW_H,
                hover ? 0xCFE4FF : ((i % 2 == 0) ? 0xF2F2F2 : 0xE8E8E8));
            this.drawString(this.fontRendererObj, clip(RecipeMapNames.chineseName(id), 118), left + 4, y + 3, 0x202020);
            this.drawString(this.fontRendererObj, clip(RecipeMapNames.englishName(id), 122), left + 124, y + 3, 0x303030);
            this.drawString(this.fontRendererObj, clip(id, 128), left + 250, y + 3, 0x505050);
        }
        if (this.search != null) {
            drawRect(
                this.search.xPosition - 1,
                this.search.yPosition - 1,
                this.search.xPosition + this.search.width + 1,
                this.search.yPosition + this.search.height + 1,
                0xF2F2F2);
            this.search.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** 按像素宽粗略截断（默认字体约 6px/字符）。 */
    private static String clip(String text, int pixelWidth) {
        if (text == null) return "";
        int max = Math.max(1, pixelWidth / 6);
        return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
