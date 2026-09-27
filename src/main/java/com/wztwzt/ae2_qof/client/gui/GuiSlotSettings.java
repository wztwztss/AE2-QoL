package com.wztwzt.ae2_qof.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.network.ModNetwork;
import com.wztwzt.ae2_qof.network.SlotSettingsPacket;
import com.wztwzt.ae2_qof.network.SlotSettingsRequestPacket;
import com.wztwzt.ae2_qof.wildcard.SlotSettings;

/**
 * 「样板格设置」弹窗（4.0.0）：**鼠标中键点机器里的样板格**打开。
 *
 * <h2>它做什么（按用户 2026-09-27 定的口径）</h2>
 * <ul>
 * <li><b>电路</b>：1~24 按钮 + 「继承」；本格设置**优先于**样板自带电路与整机设置。
 * 样板自带电路会在插入时由服务端自动填入本格（{@code SlotSettingsStore.autoFillCircuitFromPattern}），
 * 玩家在这里手改即覆盖，且**不再去改机器那个全局电路槽**；</li>
 * <li><b>催化剂</b>：9 个真实物品格（3×3）。玩家放进去的东西在该格 push 时随输入推进机器总线，
 * 配方按 notConsumed 语义不消耗，合成后收回本格（4.0.0 先在 GT 2714 与 MK.III 32108 启用）。</li>
 * </ul>
 *
 * <h2>为什么用 {@link GuiContainer} 而不是普通 GuiScreen（旧电路屏的做法）</h2>
 * 催化剂要"可放入/取出、支持 NEI 拖入"⇒ 必须是真实 {@link Slot}；真实槽位只有在
 * {@code Container + GuiContainer} 组合下才能拿到原版点击/转移逻辑与 NEI 的拖拽覆盖层。
 * 代价只是多一点样板代码，换来的是行为与其它机器界面完全一致。
 *
 * <h2>配色与文字</h2>
 * 按用户 UI 硬性偏好：**浅底深字、不使用 § 颜色码**（旧的 {@code GuiSlotCircuitPicker} 用了
 * {@code EnumChatFormatting}，本屏不再沿用）。
 */
public class GuiSlotSettings extends GuiContainer {

    private static final int ID_CIRCUIT_BASE = 900;
    private static final int ID_INHERIT = 940;
    private static final int ID_CLEAR = 941;
    private static final int ID_CLOSE = 942;

    /** 当前打开的实例（S2C 权威回读要往它身上填）。同一时刻只可能有一个。 */
    private static GuiSlotSettings open;

    private final int machineX;
    private final int machineY;
    private final int machineZ;
    private final int slotIndex;
    private final String patternName;
    private final SlotSettings edit = new SlotSettings();
    private String status = "";

    public GuiSlotSettings(int machineX, int machineY, int machineZ, int slotIndex, String patternName) {
        super(new CatalystContainer());
        this.machineX = machineX;
        this.machineY = machineY;
        this.machineZ = machineZ;
        this.slotIndex = slotIndex;
        this.patternName = patternName == null ? "?" : patternName;
        ((CatalystContainer) this.inventorySlots).bind(this.edit.catalysts, this::onEdited);
    }

    @Override
    public void initGui() {
        super.initGui();
        open = this;
        int startX = (this.width - 220) / 2;
        int startY = (this.height - 200) / 2;
        // 电路 1~24：12 列 × 2 行
        for (int i = 0; i < 24; i++) {
            int col = i % 12;
            int row = i / 12;
            this.buttonList.add(
                new GuiButton(
                    ID_CIRCUIT_BASE + i,
                    startX + 10 + col * 17,
                    startY + 34 + row * 20,
                    16,
                    18,
                    String.valueOf(i + 1)));
        }
        this.buttonList.add(new GuiButton(ID_INHERIT, startX + 10, startY + 76, 60, 18, "继承"));
        this.buttonList.add(new GuiButton(ID_CLEAR, startX + 76, startY + 76, 80, 18, "清除本格"));
        this.buttonList.add(new GuiButton(ID_CLOSE, startX + 162, startY + 76, 48, 18, "关闭"));
        // 9 个催化格摆成 3×3（槽位坐标是相对 GUI 的像素位置）
        ((CatalystContainer) this.inventorySlots).layout(startX + 10, startY + 126);
        // 首次打开：向服务端要一次权威状态（本格设置存在机器 NBT 里，客户端不保证有）
        requestFromServer();
    }

    /** 弹窗关闭时清掉静态引用（否则 S2C 会往已关闭的屏里填）。 */
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (open == this) open = null;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        try {
            if (button.id == ID_CLOSE) {
                this.mc.displayGuiScreen(null);
                return;
            }
            if (button.id == ID_INHERIT) {
                this.edit.circuit = -1;
                this.edit.circuitExplicit = false;
                sendToServer("继承（清除本格电路）");
                return;
            }
            if (button.id == ID_CLEAR) {
                this.edit.circuit = -1;
                this.edit.circuitExplicit = false;
                for (int i = 0; i < SlotSettings.CATALYST_SLOTS; i++) this.edit.catalysts[i] = null;
                ((CatalystContainer) this.inventorySlots).refresh();
                sendToServer("清除本格设置");
                return;
            }
            if (button.id >= ID_CIRCUIT_BASE && button.id < ID_CIRCUIT_BASE + 24) {
                this.edit.circuit = button.id - ID_CIRCUIT_BASE + 1;
                this.edit.circuitExplicit = true;
                sendToServer("电路 " + this.edit.circuit);
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 格设置弹窗操作失败", t);
            this.status = "error: " + t;
        }
    }

    /** 槽位内容变化（玩家放入/取出/NEI 拖入）后自动写回服务端。 */
    private void onEdited() {
        sendToServer("催化剂位变更");
    }

    private void requestFromServer() {
        try {
            ModNetwork.CHANNEL
                .sendToServer(new SlotSettingsRequestPacket(this.machineX, this.machineY, this.machineZ, this.slotIndex));
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 请求按格设置失败", t);
        }
    }

    private void sendToServer(String why) {
        try {
            ModNetwork.CHANNEL.sendToServer(
                new SlotSettingsPacket(
                    this.machineX,
                    this.machineY,
                    this.machineZ,
                    this.slotIndex,
                    this.edit.circuit,
                    this.edit.circuitExplicit,
                    this.edit.catalysts));
            this.status = "已发送：" + why;
            MyMod.LOG.info(
                "[AE2QoL] 格设置弹窗：已发送 slot={} circuit={} 手改={} 原因={} @ [{}, {}, {}]",
                this.slotIndex,
                this.edit.circuit,
                this.edit.circuitExplicit,
                why,
                this.machineX,
                this.machineY,
                this.machineZ);
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 格设置弹窗发送失败", t);
            this.status = "发送失败：" + t;
        }
    }

    /** 服务端权威状态回填（由 {@code SlotSettingsSyncPacket.Handler} 调用）。 */
    public static void applyServerState(int x, int y, int z, int slot, int circuit, boolean explicit,
        ItemStack[] catalysts) {
        GuiSlotSettings screen = open;
        if (screen == null) return;
        if (screen.machineX != x || screen.machineY != y || screen.machineZ != z || screen.slotIndex != slot) return;
        screen.edit.circuit = circuit;
        screen.edit.circuitExplicit = explicit;
        for (int i = 0; i < SlotSettings.CATALYST_SLOTS; i++) {
            screen.edit.catalysts[i] = catalysts != null && i < catalysts.length ? catalysts[i] : null;
        }
        ((CatalystContainer) screen.inventorySlots).refresh();
        screen.status = "已读取本格设置";
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int startX = (this.width - 220) / 2;
        int startY = (this.height - 200) / 2;
        // 浅底（0xF2F2F2）深字（0x202020），不用 § 颜色码
        drawRect(startX, startY, startX + 220, startY + 200, 0xF2F2F2);
        drawRect(startX, startY, startX + 220, startY + 1, 0x808080);
        drawRect(startX, startY + 199, startX + 220, startY + 200, 0x808080);
        drawRect(startX, startY, startX + 1, startY + 200, 0x808080);
        drawRect(startX + 219, startY, startX + 220, startY + 200, 0x808080);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int startX = (this.width - 220) / 2;
        int startY = (this.height - 200) / 2;
        int left = startX + 10;
        this.fontRendererObj.drawString("样板格 " + this.slotIndex + " 的设置", left, startY + 8, 0x202020);
        this.fontRendererObj.drawString("目标：" + this.patternName, left, startY + 20, 0x505050);
        this.fontRendererObj.drawString("电路（本格优先于样板自带与整机）", left, startY + 34 - 12, 0x202020);
        this.fontRendererObj.drawString("本格电路：" + (this.edit.circuit >= 1 ? this.edit.circuit : "继承"), left, startY + 98, 0x202020);
        this.fontRendererObj
            .drawString("催化剂 9 格（合成时推进总线、合成后收回本格）", left, startY + 112, 0x202020);
        if (!this.status.isEmpty()) {
            this.fontRendererObj.drawString(this.status, left, startY + 186, 0x8A1B1B);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ================= 9 个真实催化格：Container + IInventory 适配 =================

    /** 直接读写 {@code SlotSettings.catalysts} 的 9 格适配器（不额外持有副本，避免两处不同步）。 */
    public static class CatalystInventory implements IInventory {

        private ItemStack[] stacks;
        private final Runnable onChanged;

        public CatalystInventory(ItemStack[] stacks, Runnable onChanged) {
            this.stacks = stacks == null ? new ItemStack[SlotSettings.CATALYST_SLOTS] : stacks;
            this.onChanged = onChanged;
        }

        public void setStacks(ItemStack[] newStacks) {
            this.stacks = newStacks == null ? new ItemStack[SlotSettings.CATALYST_SLOTS] : newStacks;
        }

        @Override
        public int getSizeInventory() {
            return SlotSettings.CATALYST_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            return index >= 0 && index < stacks.length ? stacks[index] : null;
        }

        @Override
        public ItemStack decrStackSize(int index, int count) {
            ItemStack stack = getStackInSlot(index);
            if (stack == null) return null;
            ItemStack split;
            if (stack.stackSize <= count) {
                split = stack;
                stacks[index] = null;
            } else {
                split = stack.splitStack(count);
            }
            markDirty();
            return split;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int index) {
            ItemStack stack = getStackInSlot(index);
            stacks[index] = null;
            return stack;
        }

        @Override
        public void setInventorySlotContents(int index, ItemStack stack) {
            if (index < 0 || index >= stacks.length) return;
            stacks[index] = stack;
            markDirty();
        }

        @Override
        public String getInventoryName() {
            return "ae2qol_catalyst";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public void markDirty() {
            if (onChanged != null) onChanged.run();
        }

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return true;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int index, ItemStack stack) {
            return true;
        }
    }

    /** 只负责摆放这 9 格（3×3），不掺入玩家背包（弹窗不需要）。 */
    public static class CatalystContainer extends Container {

        private CatalystInventory inventory;
        private final Slot[] slots = new Slot[SlotSettings.CATALYST_SLOTS];

        public void bind(ItemStack[] stacks, Runnable onChanged) {
            this.inventory = new CatalystInventory(stacks, onChanged);
            for (int i = 0; i < SlotSettings.CATALYST_SLOTS; i++) {
                int col = i % 3;
                int row = i / 3;
                Slot slot = new Slot(this.inventory, i, 0, 0) {

                    @Override
                    public void onSlotChanged() {
                        super.onSlotChanged();
                        if (inventory != null) inventory.markDirty();
                    }
                };
                this.slots[i] = slot;
                this.addSlotToContainer(slot);
            }
        }

        /** 服务端状态回填后刷新（原地改数组即可，这里只做一次空跑触发）。 */
        public void refresh() {
            if (this.inventory != null) this.inventory.markDirty();
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer player, int index) {
            return null;
        }

        /** 由 GUI 在 layout 时把 9 格摆到 3×3 位置（槽位的 x/y 是相对 GUI 的像素坐标）。 */
        public void layout(int startX, int startY) {
            for (int i = 0; i < this.slots.length; i++) {
                Slot slot = this.slots[i];
                if (slot == null) continue;
                slot.xDisplayPosition = startX + (i % 3) * 22;
                slot.yDisplayPosition = startY + (i / 3) * 22;
            }
        }
    }

    /** 供 {@code InventoryPlayer} 相关调用满足签名（本弹窗不使用玩家背包）。 */
    public static class NoopInventory implements IInventory {

        @Override
        public int getSizeInventory() {
            return 0;
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            return null;
        }

        @Override
        public ItemStack decrStackSize(int index, int count) {
            return null;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int index) {
            return null;
        }

        @Override
        public void setInventorySlotContents(int index, ItemStack stack) {}

        @Override
        public String getInventoryName() {
            return "ae2qol_noop";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return 0;
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return false;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int index, ItemStack stack) {
            return false;
        }
    }
}
