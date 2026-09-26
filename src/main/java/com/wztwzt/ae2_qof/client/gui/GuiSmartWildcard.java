package com.wztwzt.ae2_qof.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.client.SmartWildcardClientState;
import com.wztwzt.ae2_qof.network.SmartWildcardRulesPacket;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.wildcard.ContainerSmartWildcard;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardExpander;
import com.wztwzt.ae2_qof.wildcard.SmartWildcardState;

/**
 * 智能通配样板的配置界面（3.22.0 M2-A）。
 *
 * <h2>为什么是经典 {@code GuiContainer} 而不是 Mui2 屏</h2>
 * NEI 的加号只会指向**上一个界面是 {@code GuiContainer}** 的情况（{@code GuiOverlayButton} 构造器只在
 * {@code gui.inventorySlots != null} 时记下 {@code firstGui}）⇒ 用经典容器界面才能让加号指到我们这里。
 *
 * <h2>三页（参考 WildcardPattern 的交互，实现不照抄）</h2>
 * <ol>
 * <li><b>规则</b>：显示当前样板的规则与摘要；NEI 加号推导出的新规则在这里可见；</li>
 * <li><b>覆盖预览</b>：逐条列出本样板覆盖到的具体候选（每行一个「排除」按钮 ⇒ 直接进黑名单，正是用户要的
 * 「生成好之后直接手动排除」）；</li>
 * <li><b>黑名单</b>：文本框添加 + 逐行删除，支持通配串。</li>
 * </ol>
 *
 * <p>预览用的是**与服务器完全同一个展开器**（{@link SmartWildcardExpander}），因此不会出现参考实现
 * 「预览能出、实际出不来」的两套语义问题。
 *
 * <p>页面内所有改动只动客户端工作副本，**点「保存」才发包**由服务端写 NBT（客户端不写 NBT）。
 */
public class GuiSmartWildcard extends GuiContainer {

    private static final int ID_TAB_RULES = 1;
    private static final int ID_TAB_PREVIEW = 2;
    private static final int ID_TAB_BLACKLIST = 3;
    private static final int ID_SAVE = 10;
    private static final int ID_EXCLUDE_BASE = 100;
    private static final int ID_REMOVE_BASE = 300;
    private static final int ID_BLACKLIST_ADD = 400;

    private final ContainerSmartWildcard container;
    private int page = 0;
    private int scroll = 0;

    /** 客户端工作副本（未保存的编辑）。 */
    private SmartWildcardState working;
    /** 展开预览缓存（页面切换/排除后重算）。 */
    private final List<ItemStack> previewItems = new ArrayList<>();
    private String previewSummary = "";
    private GuiTextField blacklistField;
    private String statusLine = "";

    public GuiSmartWildcard(ContainerSmartWildcard container) {
        super(container);
        this.container = container;
        this.xSize = 320;
        this.ySize = 220;
    }

    /** NEI 加号推导完成后由 NEI 注入调用：丢掉旧工作副本并刷新界面（新规则/新模板立即可见）。 */
    public void ae2qol$reloadDerived() {
        this.working = null;
        this.scroll = 0;
        this.statusLine = "\u5df2\u4ece NEI \u63a8\u5bfc\u89c4\u5219";
        initGui();
    }

    private ItemStack patternStack() {
        return this.container.getPatternStack();
    }

    private SmartWildcardState workingOrLoad() {
        if (this.working == null) {
            SmartWildcardState fromItem = SmartWildcardState.of(patternStack());
            // NEI 加号刚推导出规则时优先用它（否则读物品上的现有规则）
            SmartWildcardState derived = SmartWildcardClientState.derived();
            this.working = derived != null ? derived : (fromItem != null ? fromItem : new SmartWildcardState());
        }
        return this.working;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        int x = this.guiLeft;
        int y = this.guiTop;

        this.buttonList.add(new GuiButton(ID_TAB_RULES, x + 8, y + 6, 70, 18, "\u89c4\u5219"));
        this.buttonList.add(new GuiButton(ID_TAB_PREVIEW, x + 82, y + 6, 70, 18, "\u9884\u89c8"));
        this.buttonList.add(new GuiButton(ID_TAB_BLACKLIST, x + 156, y + 6, 70, 18, "\u9ed1\u540d\u5355"));
        this.buttonList.add(new GuiButton(ID_SAVE, x + 234, y + 6, 78, 18, "\u4fdd\u5b58"));

        this.blacklistField = null;
        if (this.page == 2) {
            this.blacklistField = new GuiTextField(this.fontRendererObj, x + 10, y + 32, 220, 16);
            this.blacklistField.setMaxStringLength(64);
            this.buttonList.add(new GuiButton(ID_BLACKLIST_ADD, x + 234, y + 31, 78, 18, "\u6dfb\u52a0"));
        }

        SmartWildcardState state = workingOrLoad();
        if (this.page == 1) {
            this.ae2qol$rebuildPreview(state);
            int rowY = y + 34;
            for (int i = 0; i < 10 && i + this.scroll < this.previewItems.size(); i++) {
                ItemStack stack = this.previewItems.get(i + this.scroll);
                String name = stack == null ? "?"
                    : String.valueOf(
                        stack.getItem()
                            .getItemStackDisplayName(stack));
                this.buttonList.add(
                    new GuiButton(
                        ID_EXCLUDE_BASE + i,
                        x + 236,
                        rowY + i * 16,
                        76,
                        14,
                        "\u6392\u9664"));
                this.ae2qol$rowNames.put(ID_EXCLUDE_BASE + i, name);
            }
        } else if (this.page == 2) {
            int rowY = y + 52;
            for (int i = 0; i < 9 && i + this.scroll < state.blacklist.size(); i++) {
                this.buttonList
                    .add(new GuiButton(ID_REMOVE_BASE + i, x + 236, rowY + i * 16, 76, 14, "\u5220\u9664"));
                this.ae2qol$rowNames.put(ID_REMOVE_BASE + i, state.blacklist.get(i + this.scroll));
            }
        }
    }

    /** 每行按钮对应的数据（候选显示名或黑名单条目），供 actionPerformed 使用。 */
    private final java.util.Map<Integer, String> ae2qol$rowNames = new java.util.HashMap<>();

    private void ae2qol$rebuildPreview(SmartWildcardState state) {
        this.previewItems.clear();
        this.previewSummary = "";
        try {
            ItemStack stack = patternStack();
            if (stack == null) return;
            SmartWildcardExpander.Result result = SmartWildcardExpander.expand(stack, this.mc.theWorld);
            this.previewSummary = result.describe();
            this.previewItems.addAll(result.patterns);
        } catch (Throwable t) {
            this.previewSummary = "preview failed: " + t;
            MyMod.LOG.warn("[AE2QoL] 通配样板预览失败", t);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        try {
            switch (button.id) {
                case ID_TAB_RULES:
                    this.page = 0;
                    this.scroll = 0;
                    initGui();
                    return;
                case ID_TAB_PREVIEW:
                    this.page = 1;
                    this.scroll = 0;
                    initGui();
                    return;
                case ID_TAB_BLACKLIST:
                    this.page = 2;
                    this.scroll = 0;
                    initGui();
                    return;
                case ID_SAVE:
                    this.ae2qol$save();
                    return;
                case ID_BLACKLIST_ADD:
                    if (this.blacklistField != null
                        && workingOrLoad().addBlacklist(this.blacklistField.getText())) {
                        this.blacklistField.setText("");
                        this.statusLine = "\u5df2\u6dfb\u52a0";
                        initGui();
                    } else {
                        this.statusLine = "\u7a7a\u4e32\u6216\u5df2\u5b58\u5728";
                    }
                    return;
                default:
                    break;
            }
            if (button.id >= ID_EXCLUDE_BASE && button.id < ID_EXCLUDE_BASE + 10) {
                String token = this.ae2qol$rowNames.get(button.id);
                if (token != null && workingOrLoad().addBlacklist(token)) {
                    this.statusLine = "\u5df2\u6392\u9664\uff1a" + token;
                    this.ae2qol$rebuildPreview(workingOrLoad());
                }
                return;
            }
            if (button.id >= ID_REMOVE_BASE && button.id < ID_REMOVE_BASE + 9) {
                String token = this.ae2qol$rowNames.get(button.id);
                if (workingOrLoad().removeBlacklist(token)) {
                    this.statusLine = "\u5df2\u79fb\u9664\uff1a" + token;
                    initGui();
                }
                return;
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 通配样板界面操作失败", t);
            this.statusLine = "error: " + t;
        }
    }

    private void ae2qol$save() {
        try {
            SmartWildcardState state = workingOrLoad();
            ModNetwork.CHANNEL.sendToServer(
                new SmartWildcardRulesPacket(
                    state,
                    SmartWildcardClientState.derivedTemplateIn(),
                    SmartWildcardClientState.derivedTemplateOut()));
            this.statusLine = "\u5df2\u53d1\u9001\u4fdd\u5b58\uff08rules=" + state.rules.size() + "\uff09";
            MyMod.LOG.info(
                "[AE2QoL] 通配样板规则已发送：rules={} blacklist={} whitelist={}",
                state.rules.size(),
                state.blacklist.size(),
                state.whitelist.size());
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 通配样板保存失败", t);
            this.statusLine = "\u4fdd\u5b58\u5931\u8d25\uff1a" + t;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        if (this.page == 1 && !this.previewItems.isEmpty()) {
            this.scroll = Math.max(0, Math.min(Math.max(0, this.previewItems.size() - 10), this.scroll + (wheel > 0 ? -1 : 1)));
            initGui();
        } else if (this.page == 2) {
            int size = workingOrLoad().blacklist.size();
            this.scroll = Math.max(0, Math.min(Math.max(0, size - 9), this.scroll + (wheel > 0 ? -1 : 1)));
            initGui();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.blacklistField != null) this.blacklistField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.blacklistField != null && this.blacklistField.isFocused()) {
            this.blacklistField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.blacklistField != null) this.blacklistField.updateCursorCounter();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(this.guiLeft, this.guiTop, this.guiLeft + this.xSize, this.guiTop + this.ySize, 0xC0101010);
        drawRect(this.guiLeft + 4, this.guiTop + 4, this.guiLeft + this.xSize - 4, this.guiTop + 26, 0x40FFFFFF);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        SmartWildcardState state = workingOrLoad();
        int y = 32;
        if (this.page == 0) {
            this.fontRendererObj.drawStringWithShadow(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("ae2_qof.wildcard.gui.rules_header"),
                10,
                y,
                0xFFFFFF);
            y += 12;
            if (state.rules.isEmpty()) {
                this.fontRendererObj.drawStringWithShadow(
                    EnumChatFormatting.YELLOW + StatCollector.translateToLocal("ae2_qof.wildcard.gui.no_rules"),
                    10,
                    y,
                    0xFFFFFF);
            } else {
                for (SmartWildcardState.Rule rule : state.rules) {
                    if (rule == null) continue;
                    this.fontRendererObj
                        .drawStringWithShadow("#" + rule.slot + " " + (rule.oreDictMode ? "ore:" : "name:") + rule.matcher, 10, y, 0xFFFFFF);
                    y += 11;
                }
            }
            y += 6;
            String derived = SmartWildcardClientState.derivedSummary();
            if (derived != null && !derived.isEmpty()) {
                this.fontRendererObj.drawStringWithShadow(EnumChatFormatting.GREEN + "NEI: " + derived, 10, y, 0xFFFFFF);
            }
        } else if (this.page == 1) {
            this.fontRendererObj.drawStringWithShadow(
                EnumChatFormatting.AQUA + "\u8986\u76d6\u5019\u9009 " + this.previewItems.size() + " \u9879",
                10,
                y,
                0xFFFFFF);
            y += 12;
            for (int i = 0; i < 10 && i + this.scroll < this.previewItems.size(); i++) {
                ItemStack stack = this.previewItems.get(i + this.scroll);
                String name = stack == null ? "?"
                    : String.valueOf(
                        stack.getItem()
                            .getItemStackDisplayName(stack));
                this.fontRendererObj.drawStringWithShadow(name, 10, y + i * 16 + 3, 0xFFFFFF);
            }
            this.fontRendererObj.drawStringWithShadow(EnumChatFormatting.GRAY + this.previewSummary, 10, 200, 0xFFFFFF);
        } else {
            this.fontRendererObj.drawStringWithShadow(
                EnumChatFormatting.AQUA + "\u9ed1\u540d\u5355 " + state.blacklist.size() + " \u9879",
                10,
                y,
                0xFFFFFF);
            y += 20;
            for (int i = 0; i < 9 && i + this.scroll < state.blacklist.size(); i++) {
                this.fontRendererObj.drawStringWithShadow(state.blacklist.get(i + this.scroll), 10, y + i * 16 + 3, 0xFFFFFF);
            }
            if (this.blacklistField != null) this.blacklistField.drawTextBox();
        }
        if (this.statusLine != null && !this.statusLine.isEmpty()) {
            this.fontRendererObj.drawStringWithShadow(EnumChatFormatting.YELLOW + this.statusLine, 10, 192, 0xFFFFFF);
        }
    }
}
