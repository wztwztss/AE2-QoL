package com.wztwzt.ae2_qof.ph;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.ItemStackGuiData;
import com.cleanroommc.modularui.factory.ItemStackGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.wztwzt.ae2_qof.item.ItemPatternClipboard;

/**
 * 样板剪贴板的管理 GUI（4.3.0，MUI2 / 服务端权威 —— 专用服可用）。
 *
 * <p>
 * 右键空中打开。内容：
 * <ul>
 * <li>四个模式按钮（复制 / 粘贴-覆盖 / 剪切 / 粘贴-追加），选中项高亮，写入物品 NBT
 * （与右键机器的快捷操作共用同一份模式，旧存档的 0/1/2 值含义不变）；</li>
 * <li>剪贴板概要（张数 / 槽位 / 来源）；</li>
 * <li>样板预览列表（每页 9 行：槽位号 + 名称 + 倍率，翻页按钮取模环绕）；</li>
 * <li>清空剪贴板按钮（InteractionSyncHandler，仅服务端执行）。</li>
 * </ul>
 *
 * <p>
 * 数据全部经 MUI2 sync value 从服务端 supplier 拉取：剪贴板本体存在玩家
 * {@code PlayerPersisted}，GUI 只读镜像 + 模式回写，不引入新网络包。
 * 本类只被 {@link ItemPatternClipboard}（PH 守卫之后才会注册）引用，缺 PH 不加载。
 */
public final class PatternClipboardGui {

    private static final int PANEL_W = 220;
    private static final int ROWS = 9;
    private static final int MODE_BUTTON_W = 52;

    private static volatile ItemStackGuiFactory factory;

    private PatternClipboardGui() {}

    /**
     * 双端初始化 GUI 工厂（mod init 阶段调用，PH 守卫之后）。
     *
     * <p>
     * **注意不要手动调用 {@code GuiManager.registerFactory}**：MUI2 的
     * {@code ItemStackGuiFactory} 构造器**自带注册**（字节码实证：构造器尾段直接
     * {@code GuiManager.registerFactory(this)}），再显式注册一次 = "already registered"
     * IllegalArgumentException（4.3.0-fix1 引入、4.4.0-fix1 才发现——每次启动必炸一次，
     * 还会中断 PhIntegration 的 try 块尾部）。构造即注册，本方法只负责持有引用。
     *
     * <p>
     * **必须在双端都调用本方法**：MUI2 的 {@code OpenGuiPacket} 在客户端按工厂名走
     * {@code GuiManager.getFactory(name)} 查**客户端自己的注册表**——只在服务端注册的话，
     * 服务端开屏包一到客户端就查不到工厂，包处理直接抛异常 ⇒ 玩家被踢下线（服务器不崩）。
     * 这正是 4.3.0 首测「复制/粘贴/剪切都强制退出」的根因。
     */
    public static void init() {
        if (factory != null) return;
        synchronized (PatternClipboardGui.class) {
            if (factory != null) return;
            factory = new ItemStackGuiFactory("ae2_qof:pattern_clipboard", PatternClipboardGui::buildUI);
        }
    }

    /** 服务端打开 GUI（物品右键空中时调用）。 */
    public static void open(EntityPlayer player, ItemStack stack) {
        if (!(player instanceof EntityPlayerMP playerMP)) return;
        init();
        com.cleanroommc.modularui.factory.GuiManager.open(factory, new ItemStackGuiData(player, stack), playerMP);
    }

    private static ModularPanel buildUI(ItemStackGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        // 4.4.0-fix2：构建期**绝不能**走 syncManager.getPlayer()（container 尚未建立 ⇒ NPE ⇒
        // 服务器把玩家踢下线；MUI2 面板构建期一律只使用工厂/同步值提供的上下文）。
        // 工厂入参 guiData 自带打开者：ItemStackGuiData.getPlayer() 在构建期可用且对本面板实例恒定。
        final EntityPlayer player = guiData.getPlayer();
        final ItemStack stack = guiData.getItemStack();

        // 页码：服务端会话内状态（buildUI 每次开窗调用一次，闭包活到关窗）
        final int[] page = { 0 };

        // 模式（C2S+S2C，写物品 NBT）
        final IntSyncValue modeSync = new IntSyncValue(
            () -> ItemPatternClipboard.getMode(stack),
            v -> ItemPatternClipboard.setMode(stack, v)).allowC2S();
        syncManager.syncValue("mode", modeSync);

        // 剪贴板概要（S2C，随复制/清空自动刷新）
        final StringSyncValue summarySync = new StringSyncValue(() -> ItemPatternClipboard.clipboardSummary(player));
        syncManager.syncValue("summary", summarySync);

        // 预览行（S2C）：supplier 在服务端按当前页生成文本
        final StringSyncValue[] rowSyncs = new StringSyncValue[ROWS];
        for (int i = 0; i < ROWS; i++) {
            final int idx = i;
            rowSyncs[i] = new StringSyncValue(() -> ItemPatternClipboard.clipboardRow(player, page[0], ROWS, idx));
            syncManager.syncValue("row_" + i, rowSyncs[i]);
        }

        // 翻页（C2S+S2C，取模环绕）
        final IntSyncValue pageSync = new IntSyncValue(() -> page[0], v -> {
            int pages = ItemPatternClipboard.clipboardPages(player, ROWS);
            page[0] = ((v % pages) + pages) % pages;
        }).allowC2S();
        syncManager.syncValue("page", pageSync);

        ModularPanel panel = ModularPanel.defaultPanel("pattern_clipboard", PANEL_W, 250);
        Flow column = Flow.column()
            .coverChildren()
            .childPadding(3);

        // 标题
        column.child(new TextWidget<>(IKey.lang("ae2_qof.gui.pattern_clipboard.title")).size(PANEL_W - 12, 14));

        // 模式按钮行
        Flow modeRow = Flow.row()
            .coverChildren()
            .childPadding(2);
        for (int m = 0; m < ItemPatternClipboard.MODE_COUNT; m++) {
            final int target = m;
            modeRow.child(
                new ButtonWidget<>().size(MODE_BUTTON_W, 16)
                    .background(GuiTextures.BUTTON_CLEAN)
                    .overlay(
                        IKey.dynamic(
                            () -> (modeSync.getValue() == target ? EnumChatFormatting.AQUA : EnumChatFormatting.GRAY)
                                + ItemPatternClipboard.modeName(target)))
                    .onMousePressed(e -> {
                        modeSync.setValue(target);
                        return true;
                    }));
        }
        column.child(modeRow);

        // 模式说明（跟随当前模式变化的提示行）
        column.child(
            new TextWidget<>(
                IKey.dynamic(
                    () -> EnumChatFormatting.DARK_GRAY + StatCollector
                        .translateToLocal("ae2_qof.gui.pattern_clipboard.mode_hint." + modeSync.getValue())))
                            .size(PANEL_W - 12, 26));

        // 剪贴板概要
        column.child(
            new TextWidget<>(IKey.dynamic(() -> EnumChatFormatting.AQUA + summarySync.getValue()))
                .size(PANEL_W - 12, 26));

        // 预览列表（9 行）
        for (int i = 0; i < ROWS; i++) {
            final int idx = i;
            column.child(new TextWidget<>(IKey.dynamic(() -> rowSyncs[idx].getValue())).size(PANEL_W - 12, 12));
        }

        // 翻页 + 清空
        InteractionSyncHandler clearAction = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (!syncManager.isClient()) {
                ItemPatternClipboard.clearClipboard(player);
            }
        });
        syncManager.syncValue("clear", clearAction);

        column.child(
            Flow.row()
                .coverChildren()
                .childPadding(4)
                .child(
                    new ButtonWidget<>().size(60, 16)
                        .background(GuiTextures.BUTTON_CLEAN)
                        .overlay(IKey.lang("ae2_qof.gui.pattern_clipboard.prev"))
                        .onMousePressed(e -> {
                            pageSync.setValue(pageSync.getValue() - 1);
                            return true;
                        }))
                .child(
                    new ButtonWidget<>().size(60, 16)
                        .background(GuiTextures.BUTTON_CLEAN)
                        .overlay(IKey.lang("ae2_qof.gui.pattern_clipboard.next"))
                        .onMousePressed(e -> {
                            pageSync.setValue(pageSync.getValue() + 1);
                            return true;
                        }))
                .child(
                    new ButtonWidget<>().size(PANEL_W - 12 - 128, 16)
                        .background(GuiTextures.BUTTON_CLEAN)
                        .overlay(IKey.lang("ae2_qof.gui.pattern_clipboard.clear"))
                        .syncHandler(clearAction)));

        panel.child(
            Flow.column()
                .full()
                .padding(6)
                .childPadding(3)
                .child(column));
        return panel;
    }
}
