package com.wztwzt.ae2_qof.merged;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.client.event.MergedTerminalPanelHandler;
import com.wztwzt.ae2_qof.client.gui.MergedAmountOverlay;
import com.wztwzt.ae2_qof.client.gui.MergedPanelLayout;
import com.wztwzt.ae2_qof.client.gui.MergedRenameOverlay;
import com.wztwzt.ae2_qof.network.ModNetwork;

import appeng.api.AEApi;
import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotRestrictedInput;
import appeng.util.item.AEItemStack;

/**
 * 样板与接口二合一终端 GUI。
 * <p>
 * 继承 GuiInterfaceTerminal，在右侧绘制 AE2Things 原生 4×4 样板面板
 * （面板位于 offsetX+209，槽位 y 方向 +68 重定位与图片孔位对齐）。
 */
public class GuiMergedTerminal extends GuiInterfaceTerminal {

    private static final ResourceLocation BG_CRAFT = new ResourceLocation(
        "ae2_qof",
        "textures/gui/widget/pattern3.png");
    private static final ResourceLocation BG_PROCESS = new ResourceLocation(
        "ae2_qof",
        "textures/gui/widget/pattern.png");

    /** 绘制时临时放大的 GUI 宽度，使面板悬垂区域参与槽位/鼠标交互 */
    private static final int FULL_X_SIZE = 1000;

    /** 当前打开的合并终端实例：NEI 覆盖层/数量弹窗打开期间 currentScreen 不是本 GUI，搜索框写入需用实例 */
    public static GuiMergedTerminal activeInstance;

    /** 中键数量编辑覆盖层（AE2 风格 +/−/×/÷ 按钮 + 文本框） */
    private final MergedAmountOverlay amountOverlay = new MergedAmountOverlay();

    /** Shift+中键重命名覆盖层 */
    private final MergedRenameOverlay renameOverlay = new MergedRenameOverlay();

    public GuiMergedTerminal(InventoryPlayer ip, com.wztwzt.ae2_qof.api.IMergedTerminalHost anchor) {
        super(new ContainerMergedTerminal(ip, anchor));
    }

    private ContainerMergedTerminal getMergedContainer() {
        return (ContainerMergedTerminal) this.inventorySlots;
    }

    public int getGuiLeft() {
        return this.guiLeft;
    }

    public int getGuiTop() {
        return this.guiTop;
    }

    /**
     * 编码成功后把机器名填入终端「机器名」搜索框（searchFieldNames），自动过滤出刚编码的机器。
     * searchFieldNames 为 GuiInterfaceTerminal 的 private final 字段，这里用反射写入。
     * <p>
     * NEI 配方覆盖层（GuiRecipe）与数量弹窗打开期间 {@code currentScreen} 不是本 GUI，
     * 因此优先使用 {@link #activeInstance}，再回退到 currentScreen。
     */
    public static void setSearchFieldText(String text) {
        GuiMergedTerminal gui = activeInstance;
        if (gui == null) {
            net.minecraft.client.gui.GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (screen instanceof GuiMergedTerminal mgt) {
                gui = mgt;
            } else if (screen instanceof GuiInterfaceTerminal git) {
                trySet(git, text);
                return;
            }
        }
        if (gui != null) {
            trySet(gui, text);
        }
    }

    private static void trySet(Object gui, String text) {
        try {
            Field f = GuiInterfaceTerminal.class.getDeclaredField("searchFieldNames");
            f.setAccessible(true);
            Object tf = f.get(gui);
            if (tf instanceof MEGuiTextField field) {
                field.setText(text == null ? "" : text);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        try {
            PatternContainer pc = getMergedContainer().patternContainer;
            if (pc == null) return;
            // 模式取容器同步字段（打开时恢复上次保存的模式）
            boolean crafting = getMergedContainer().syncCraftingMode;

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.mc.getTextureManager()
                .bindTexture(crafting ? BG_CRAFT : BG_PROCESS);
            if (crafting || pc.isInverted()) {
                this.drawTexturedModalRect(offsetX + 209, offsetY, 0, 0, 133, 93);
            } else {
                this.drawTexturedModalRect(offsetX + 209, offsetY, 0, 93, 133, 93);
            }
            this.drawTexturedModalRect(offsetX + 209, offsetY + 93, 133, 0, 40, 77);
            this.drawTexturedModalRect(offsetX + 209, offsetY + 170, 173, 0, 32, 32);
        } catch (Throwable ignored) {}
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.xSize = 209;
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.xSize = FULL_X_SIZE;
        // Update overlay cursor blink
        amountOverlay.updateScreen();
        renameOverlay.updateScreen();
    }

    /**
     * 面板 TooltipTextButton 悬停满 1s 才显示（#3.9.0 tooltip 延迟）。
     * AEBaseGui.drawScreen 对 buttonList 中每个 ITooltip 调用本方法，此处对
     * 自研按钮先过计时闸门；其余（AE2 原生按钮/槽位）不受影响。
     */
    @Override
    protected void handleTooltip(int mouseX, int mouseY, appeng.client.gui.widgets.ITooltip tooltip) {
        if (tooltip instanceof com.wztwzt.ae2_qof.client.gui.TooltipTextButton btn
            && !btn.shouldRenderTooltip(mouseX, mouseY)) {
            return;
        }
        super.handleTooltip(mouseX, mouseY, tooltip);
    }

    @Override
    public void initGui() {
        activeInstance = this;
        this.xSize = 209;
        super.initGui();
    }

    @Override
    protected void repositionSlots() {
        PatternContainer pc = getMergedContainer().patternContainer;
        for (Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot slot) {
                if (pc != null && pc.getSlots()
                    .contains(slot)) {
                    slot.yDisplayPosition = slot.getY() + MergedPanelLayout.SLOT_Y_REPOSITION;
                } else {
                    slot.yDisplayPosition = this.ySize + slot.getY() - 78 - 7;
                }
            }
        }
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        try {
            PatternContainer pc = getMergedContainer().patternContainer;
            if (pc == null) return;

            // 模式取容器同步字段（打开时恢复上次保存的模式）
            boolean crafting = getMergedContainer().syncCraftingMode;
            MergedTerminalPanelHandler
                .reposition(this.guiLeft, this.guiTop, crafting, pc.isInverted(), pc.getActivePage());

            String name = ClientState.mergedMachineName;
            String text = (name != null && !name.isEmpty()) ? "\u673a\u5668: " + name : "\u673a\u5668: -";
            this.fontRendererObj.drawString(
                text,
                this.guiLeft + MergedPanelLayout.MACHINE_NAME_X,
                this.guiTop + MergedPanelLayout.MACHINE_NAME_Y,
                0xFFE0E0E0);

            MergedTerminalPanelHandler.drawScrollbar(this);

            // 绘制数量编辑覆盖层
            if (amountOverlay.isActive()) {
                amountOverlay.draw(mouseX, mouseY);
            }
            // 绘制重命名覆盖层
            if (renameOverlay.isActive()) {
                renameOverlay.draw(mouseX, mouseY);
            }
            // Shift+滚轮替换候选预览（图标网格）
            drawReplacePreview(mouseX, mouseY);
        } catch (Throwable ignored) {}
    }

    @Override
    public void func_146977_a(Slot s) {
        // 空白样板槽：空槽时绘制网络内空白样板总数量（虚拟视图，与其它编码终端共享、动态变化）
        try {
            if (isPanelSlot(s) && s instanceof SlotRestrictedInput sri
                && sri.getItemType() == SlotRestrictedInput.PlacableItemType.BLANK_PATTERN
                && !s.getHasStack()
                && ClientState.mergedBlankCount > 0) {
                AEItemStack view = (AEItemStack) AEApi.instance()
                    .storage()
                    .createItemStack(
                        AEApi.instance()
                            .definitions()
                            .materials()
                            .blankPattern()
                            .maybeStack(1)
                            .get());
                view.setStackSize(ClientState.mergedBlankCount);
                view.drawInGui(this.mc, s.xDisplayPosition, s.yDisplayPosition);
                view.drawOverlayInGui(this.mc, s.xDisplayPosition, s.yDisplayPosition, true, true, true, true);
                return;
            }
        } catch (Throwable ignored) {}
        if (isPanelSlot(s) || !isInterfaceSlot(s)) {
            super.func_146977_a(s);
        }
    }

    private boolean isPanelSlot(Slot s) {
        try {
            PatternContainer pc = getMergedContainer().patternContainer;
            if (pc == null) return false;
            return pc.getSlots()
                .contains(s);
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean isInterfaceSlot(Slot s) {
        try {
            int base = getMergedContainer().getMergedSlotBase();
            if (base < 0) return false;
            return s.slotNumber < base;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            PatternContainer pc = getMergedContainer().patternContainer;

            // 数量编辑覆盖层优先处理
            if (amountOverlay.isActive()) {
                if (amountOverlay.mouseClicked(mouseX, mouseY, mouseButton)) {
                    // 如果点击了 Set 按钮，发送数量包并关闭
                    if (amountOverlay.isSetRequested()) {
                        int amount = amountOverlay.getAmount();
                        int slotNum = amountOverlay.getSlotNumber();
                        amountOverlay.close();
                        if (amount > 0 && slotNum >= 0) {
                            ModNetwork.CHANNEL.sendToServer(
                                new com.wztwzt.ae2_qof.network.MergedTerminalSetStackPacket(slotNum, amount));
                        }
                    }
                    return;
                }
                // 点击覆盖层外部 → 关闭并吞掉本次点击（模态弹窗，防止穿透到下方槽位）
                amountOverlay.close();
                return;
            }

            // 重命名覆盖层优先处理
            if (renameOverlay.isActive()) {
                if (renameOverlay.mouseClicked(mouseX, mouseY, mouseButton)) {
                    if (renameOverlay.isRenameRequested()) {
                        String newName = renameOverlay.getNewName();
                        int slotNum = renameOverlay.getSlotNumber();
                        renameOverlay.close();
                        if (slotNum >= 0) {
                            ModNetwork.CHANNEL.sendToServer(
                                new com.wztwzt.ae2_qof.network.MergedTerminalRenamePacket(slotNum, newName));
                        }
                    } else if (!renameOverlay.isActive()) {
                        renameOverlay.close();
                    }
                    return;
                }
                // 点击覆盖层外部 → 关闭并吞掉本次点击（模态弹窗，防止穿透到下方槽位）
                renameOverlay.close();
                return;
            }

            // 面板按钮命中检测不受面板边界限制：上传↑按钮横跨面板左边界（容器坐标 206 vs 面板起点 209），
            // 放在 isInPanel 判定外否则按钮左侧 3px 点击静默失效。白名单避免误抢 AE 原生按钮。
            if (pc != null && (mouseButton == 0 || mouseButton == 1)) {
                boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                for (Object o : this.buttonList) {
                    GuiButton b = (GuiButton) o;
                    if (b != null && MergedTerminalPanelHandler.isPanelButton(b.id)
                        && b.mousePressed(this.mc, mouseX, mouseY)) {
                        this.mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
                        MergedTerminalPanelHandler.onButtonClicked(this, b.id, mouseButton, ctrl);
                        return;
                    }
                }
            }

            if (pc != null && isInPanel(mouseX, mouseY)) {
                if (mouseButton == 0 || mouseButton == 1) {
                    if (mouseButton == 0 && MergedTerminalPanelHandler.handleScrollbarClick(this, mouseX, mouseY)) {
                        return;
                    }
                }

                Slot slot = this.findSlotAt(mouseX, mouseY);
                boolean panelSlot = slot != null && isPanelSlot(slot);
                if (slot != null && isPanelSlot(slot)) {
                    boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

                    if (mouseButton == 2) {
                        if (slot.getHasStack()) {
                            // 输出格数量由配方决定，编辑后重算会被覆盖/清空（物品消失根因），禁止编辑
                            if (pc.isOutputSlot(slot)) {
                                return;
                            }
                            if (shift) {
                                // Shift+中键：重命名
                                ItemStack st = slot.getStack();
                                String currentName = "";
                                if (st.stackTagCompound != null && st.stackTagCompound.hasKey("display")) {
                                    NBTTagCompound display = st.stackTagCompound.getCompoundTag("display");
                                    if (display.hasKey("Name")) {
                                        currentName = display.getString("Name");
                                    }
                                }
                                renameOverlay.open(slot.slotNumber, currentName, this.guiLeft, this.guiTop, this.ySize);
                            } else {
                                // 普通中键：数量编辑
                                int currentSize = slot.getStack() != null ? slot.getStack().stackSize : 1;
                                amountOverlay.open(slot.slotNumber, currentSize, this.guiLeft, this.guiTop, this.ySize);
                            }
                        }
                        return;
                    }
                    // 面板槽均为 SlotFake 系：必须走原生 PacketClickOrDragFakeSlot（镜像
                    // AEBaseGui.handleClickOrDragFakeSlot 的发包 + 本地预测）。不能用 vanilla
                    // windowClick——服务端 Container.slotClick 对 fake 槽行为不完整（点击取物
                    // 时而无效）；也不能落 super——GuiInterfaceTerminal.mouseClicked 的
                    // masterList 判定会吞掉面板悬垂区的点击。
                    ItemStack hand = getStackFromHand();
                    if (mouseButton == 1 && hand != null) {
                        // 右键手持时只放 1 个（与原生 handleClickOrDragFakeSlot 一致）
                        ItemStack single = hand.copy();
                        single.stackSize = 1;
                        hand = single;
                    }
                    appeng.core.sync.network.NetworkHandler.instance.sendToServer(
                        new appeng.core.sync.packets.PacketClickOrDragFakeSlot(
                            hand,
                            slot.slotNumber,
                            mouseButton != 1));
                    // 客户端本地预测，与服务端行为一致
                    ItemStack inSlot = slot.getStack();
                    if (mouseButton == 1 && inSlot != null) {
                        if (hand != null && inSlot.isItemEqual(hand) && ItemStack.areItemStackTagsEqual(inSlot, hand)) {
                            hand.stackSize = Math.min(inSlot.stackSize + hand.stackSize, hand.getMaxStackSize());
                        } else if (hand == null) {
                            inSlot.stackSize -= 1;
                            slot.putStack(inSlot.stackSize <= 0 ? null : inSlot);
                            return;
                        }
                    }
                    slot.putStack(hand);
                }
            }
        } catch (Throwable ignored) {}
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        super.mouseMovedOrUp(mouseX, mouseY, which);
    }

    /** 上次发出候选请求的物品（引用对比节流，避免每帧刷包） */
    private ItemStack lastCandidateRequest;

    /**
     * Shift+滚轮替换候选预览：按住 Shift 悬停面板槽位时，
     * 在鼠标附近绘制网络中同类候选的图标网格（白色框标记下一个，蓝色框标记当前）。
     */
    private void drawReplacePreview(int mouseX, int mouseY) {
        if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) return;
        if (this.mc.thePlayer.inventory.getItemStack() != null) return;
        Slot slot = this.findSlotAt(mouseX, mouseY);
        if (slot == null || !isPanelSlot(slot) || !slot.getHasStack()) return;

        // 悬停物品变化才重新请求候选
        ItemStack cur = slot.getStack();
        if (this.lastCandidateRequest != cur) {
            this.lastCandidateRequest = cur;
            ClientState.replaceCandidates = null;
            ModNetwork.CHANNEL
                .sendToServer(new com.wztwzt.ae2_qof.network.RequestReplaceCandidatesPacket(slot.slotNumber));
        }

        java.util.List<ItemStack> cands = ClientState.replaceCandidates;
        if (cands == null || cands.isEmpty()) return;

        final int perRow = 8;
        final int maxShow = 32;
        int count = Math.min(cands.size(), maxShow);
        int cols = Math.min(count, perRow);
        int rows = (count + perRow - 1) / perRow;

        int gx = mouseX - this.guiLeft + 12;
        int gy = mouseY - this.guiTop + 16;
        int w = cols * 18 + 6;
        int h = rows * 18 + 6;

        // 半透明背景框
        this.drawRect(gx - 3, gy - 3, gx + w, gy + h, 0xF0100010);

        int idx = ClientState.replaceCurrentIndex;
        int nextIdx = idx < 0 ? 0 : (idx + 1) % cands.size();

        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        net.minecraft.client.renderer.entity.RenderItem ri = this.itemRender;
        ri.zLevel = 300;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        for (int i = 0; i < count; i++) {
            int cx = gx + (i % perRow) * 18;
            int cy = gy + (i / perRow) * 18;
            if (i == nextIdx) {
                this.drawRect(cx - 1, cy - 1, cx + 17, cy + 17, 0xFFFFFFFF);
            } else if (i == idx) {
                this.drawRect(cx - 1, cy - 1, cx + 17, cy + 17, 0xFF7070D0);
            }
            ri.renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), cands.get(i), cx, cy);
        }
        ri.zLevel = 0;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // 数量编辑覆盖层优先处理键盘
        if (amountOverlay.isActive()) {
            if (amountOverlay.keyTyped(typedChar, keyCode)) {
                // Enter 键 → 发送 Set 数量
                if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                    int amount = amountOverlay.getAmount();
                    int slotNum = amountOverlay.getSlotNumber();
                    amountOverlay.close();
                    if (amount > 0 && slotNum >= 0) {
                        ModNetwork.CHANNEL
                            .sendToServer(new com.wztwzt.ae2_qof.network.MergedTerminalSetStackPacket(slotNum, amount));
                    }
                }
                return;
            }
        }
        // 重命名覆盖层优先处理键盘
        if (renameOverlay.isActive()) {
            if (renameOverlay.keyTyped(typedChar, keyCode)) {
                // Enter 键 → 发送重命名
                if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                    String newName = renameOverlay.getNewName();
                    int slotNum = renameOverlay.getSlotNumber();
                    renameOverlay.close();
                    if (slotNum >= 0) {
                        ModNetwork.CHANNEL
                            .sendToServer(new com.wztwzt.ae2_qof.network.MergedTerminalRenamePacket(slotNum, newName));
                    }
                }
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected boolean mouseWheelEvent(int x, int y, int wheel) {
        if (super.mouseWheelEvent(x, y, wheel)) return true;
        try {
            PatternContainer pc = getMergedContainer().patternContainer;

            // 鼠标滚轮调整面板槽位数量：上滚 +1、下滚 -1、最小保持 1（清空请用左键取出或中键设 0；
            // 对齐 GT 样板输入仓操作习惯）。Shift+滚轮保留给 OreDict 替换循环，输出格数量由配方决定禁止修改。
            if (pc != null && !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
                && isInPanel(x, y)) {
                Slot slot = this.findSlotAt(x, y);
                if (slot != null && isPanelSlot(slot) && slot.getHasStack() && !pc.isOutputSlot(slot)) {
                    int cur = slot.getStack().stackSize;
                    int next = wheel > 0 ? cur + 1 : Math.max(1, cur - 1);
                    ModNetwork.CHANNEL.sendToServer(
                        new com.wztwzt.ae2_qof.network.MergedTerminalSetStackPacket(slot.slotNumber, next));
                    return true;
                }
            }

            // Shift+滚轮：替换同类型物品
            if (pc != null && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
                && isInPanel(x, y)) {
                Slot slot = this.findSlotAt(x, y);
                if (slot != null && isPanelSlot(slot) && slot.getHasStack()) {
                    int direction = wheel > 0 ? 1 : -1;
                    ModNetwork.CHANNEL.sendToServer(
                        new com.wztwzt.ae2_qof.network.MergedTerminalScrollReplacePacket(slot.slotNumber, direction));
                    return true;
                }
            }

            if (pc != null && !getMergedContainer().syncCraftingMode
                && MergedTerminalPanelHandler.handleScrollbarWheel(this, x, y, wheel)) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean isInPanel(int x, int y) {
        return MergedPanelLayout.isInPanel(x - this.guiLeft, y - this.guiTop);
    }

    private Slot findSlotAt(int x, int y) {
        int gx = x - this.guiLeft;
        int gy = y - this.guiTop;
        for (Object o : this.inventorySlots.inventorySlots) {
            Slot slot = (Slot) o;
            if (gx >= slot.xDisplayPosition - 1 && gx < slot.xDisplayPosition + 17
                && gy >= slot.yDisplayPosition - 1
                && gy < slot.yDisplayPosition + 17) {
                return slot;
            }
        }
        return null;
    }
}
