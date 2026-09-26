package com.wztwzt.ae2_qof.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.SmartWildcardSlotCircuitPacket;

/**
 * 「机器样板槽上的 Shift+中键」弹出的电路选择屏（3.22.0 M3 手势）。
 *
 * <h2>它做什么</h2>
 * 列出 1~24 号编程电路（点一下就写进**这张样板自己的 NBT**）、一个「清除（继承）」、
 * 一个「手持物品记为不消耗」（铸模/模头/透镜那类），以及「关闭」。
 *
 * <h2>为什么写进样板、而不是直接改机器电路槽</h2>
 * 按用户口径的优先级 **样板自带 &gt; 槽位 &gt; 整机**：这里设定的是"样板自带"那一层，
 * 样板跟着走；机器读取样板时由 {@code SmartWildcardCircuit} 再把它写进机器虚拟电路槽。
 *
 * <h2>为什么用普通 GuiScreen</h2>
 * 这一屏只是一个一次性选择器（选完即关），不需要容器同步；用普通屏幕可以避免引入 MUI2 面板的复杂度，
 * 也就不会影响它下面那层机器界面的状态。
 */
public class GuiSlotCircuitPicker extends GuiScreen {

    private static final int ID_BASE = 700;
    private static final int ID_CLEAR = 740;
    private static final int ID_ADD_NONCONSUMED = 741;
    private static final int ID_CLOSE = 742;

    private final int machineX;
    private final int machineY;
    private final int machineZ;
    private final int slotIndex;
    private final String patternName;
    private String status = "";

    public GuiSlotCircuitPicker(int machineX, int machineY, int machineZ, int slotIndex, String patternName) {
        this.machineX = machineX;
        this.machineY = machineY;
        this.machineZ = machineZ;
        this.slotIndex = slotIndex;
        this.patternName = patternName == null ? "?" : patternName;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int startX = this.width / 2 - 120;
        int startY = this.height / 2 - 60;
        for (int i = 0; i < 24; i++) {
            int col = i % 8;
            int row = i / 8;
            this.buttonList
                .add(new GuiButton(ID_BASE + i, startX + col * 30, startY + row * 22, 28, 20, String.valueOf(i + 1)));
        }
        int bottom = startY + 3 * 22 + 8;
        this.buttonList.add(new GuiButton(ID_CLEAR, startX, bottom, 110, 20, "\u6e05\u9664\uff08\u7ee7\u627f\uff09"));
        this.buttonList
            .add(new GuiButton(ID_ADD_NONCONSUMED, startX + 116, bottom, 124, 20, "\u624b\u6301\u7269\u8bb0\u4e3a\u4e0d\u6d88\u8017"));
        this.buttonList.add(new GuiButton(ID_CLOSE, startX + 60, bottom + 26, 120, 20, "\u5173\u95ed"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        try {
            if (button.id == ID_CLOSE) {
                this.mc.displayGuiScreen(null);
                return;
            }
            if (button.id == ID_CLEAR) {
                send(-1, false);
                return;
            }
            if (button.id == ID_ADD_NONCONSUMED) {
                send(-2, true);
                return;
            }
            if (button.id >= ID_BASE && button.id < ID_BASE + 24) {
                send(button.id - ID_BASE + 1, false);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 电路选择屏操作失败", t);
            this.status = "error: " + t;
        }
    }

    private void send(int circuit, boolean addHeld) {
        try {
            ModNetwork.CHANNEL
                .sendToServer(new SmartWildcardSlotCircuitPacket(this.machineX, this.machineY, this.machineZ, this.slotIndex, circuit, addHeld));
            this.status = addHeld ? "\u5df2\u8bb0\u4e3a\u4e0d\u6d88\u8017\u7269\u54c1" : ("\u5df2\u53d1\u9001\uff1a\u7535\u8def " + (circuit < 1 ? "\u6e05\u9664" : circuit));
            MyMod.LOG.info(
                "[AE2QoL] 样板槽手势：已发送写回请求 slot={} circuit={} addHeld={} @ [{}, {}, {}]",
                this.slotIndex,
                circuit,
                addHeld,
                this.machineX,
                this.machineY,
                this.machineZ);
            this.mc.displayGuiScreen(null);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 样板槽手势发送失败", t);
            this.status = "\u53d1\u9001\u5931\u8d25\uff1a" + t;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int top = this.height / 2 - 60;
        this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.AQUA + "\u7f16\u7a0b\u7535\u8def\uff081~24\uff09", this.width / 2, top - 30, 0xFFFFFF);
        this.drawCenteredString(
            this.fontRendererObj,
            EnumChatFormatting.GRAY + "\u76ee\u6807\uff1a" + this.patternName + "  \uff08\u673a\u5668\u69fd\u4f4d " + this.slotIndex + "\uff09",
            this.width / 2,
            top - 18,
            0xFFFFFF);
        if (!this.status.isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, EnumChatFormatting.YELLOW + this.status, this.width / 2, top + 116, 0xFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
