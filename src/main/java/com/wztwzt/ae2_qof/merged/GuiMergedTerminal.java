package com.wztwzt.ae2_qof.merged;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.wztwzt.ae2_qof.client.ClientState;
import com.wztwzt.ae2_qof.client.event.MergedTerminalPanelHandler;
import com.wztwzt.ae2_qof.client.gui.MergedPanelLayout;

import appeng.api.parts.IInterfaceTerminal;
import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.container.slot.AppEngSlot;

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

    public GuiMergedTerminal(InventoryPlayer ip, IInterfaceTerminal anchor) {
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

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        try {
            PatternContainer pc = getMergedContainer().patternContainer;
            if (pc == null) return;
            boolean crafting = pc.isCraftingMode();

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
    }

    @Override
    public void initGui() {
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

            boolean crafting = pc.isCraftingMode();
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
        } catch (Throwable ignored) {}
    }

    @Override
    public void func_146977_a(Slot s) {
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
            if (pc != null && isInPanel(mouseX, mouseY)) {
                if (mouseButton == 0) {
                    if (MergedTerminalPanelHandler.handleScrollbarClick(this, mouseX, mouseY)) {
                        return;
                    }
                    for (Object o : this.buttonList) {
                        GuiButton b = (GuiButton) o;
                        if (b != null && b.mousePressed(this.mc, mouseX, mouseY)) {
                            this.mc.thePlayer.playSound("random.click", 1.0F, 1.0F);
                            MergedTerminalPanelHandler.onButtonClicked(this, b.id);
                            return;
                        }
                    }
                }

                Slot slot = this.findSlotAt(mouseX, mouseY);
                if (slot != null && isPanelSlot(slot)) {
                    if (this.mc.thePlayer.inventory.getItemStack() == null) {
                        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                            || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                        this.mc.playerController.windowClick(
                            this.inventorySlots.windowId,
                            slot.slotNumber,
                            mouseButton,
                            shift ? 1 : 0,
                            this.mc.thePlayer);
                        return;
                    }
                    // 光标非空：落入 super，走 AE 拖拽/放置路径（鼠标移动时发包放置）
                }
            }
        } catch (Throwable ignored) {}
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected boolean mouseWheelEvent(int x, int y, int wheel) {
        if (super.mouseWheelEvent(x, y, wheel)) return true;
        try {
            PatternContainer pc = getMergedContainer().patternContainer;
            if (pc != null && !pc.isCraftingMode()
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
