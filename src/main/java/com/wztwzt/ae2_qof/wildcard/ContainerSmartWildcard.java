package com.wztwzt.ae2_qof.wildcard;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.wztwzt.ae2_qof.MyMod;

/**
 * 智能通配样板的配置容器（3.22.0 M2-A）。
 *
 * <h2>为什么是「一个真实槽位指向玩家背包」</h2>
 * NEI 的加号只会指向**上一个界面是 {@code GuiContainer}（有 {@code inventorySlots}）**的情况
 * （证据：{@code GuiOverlayButton} 构造器仅在 {@code gui.inventorySlots != null} 时设置 {@code firstGui}）。
 * 参考模组也是这个姿势：右键物品 → 用**背包槽位号**打开界面（其 {@code WildcardGuiHandler}），保存靠槽位复核。
 * 所以我们不做"幽灵物品"，而是把玩家背包里那张样板所在的槽位**直接作为界面里的槽位**：
 * <ul>
 * <li>玩家看到的、写回的都是同一份 ItemStack（并由 {@code detectAndSendChanges} 自动同步客户端）；</li>
 * <li>不需要额外的"客户端伪造 NBT"逻辑（那会被服务端槽位同步覆盖）；</li>
 * <li>NEI 加号能正常指向本界面。</li>
 * </ul>
 *
 * <h2>写入口径</h2>
 * 规则推导在客户端（NEI 数据只在客户端），**写 NBT 一律在服务端**（{@link #applyRules}）：
 * 写完后 {@code clearCache()} 让展开器立即重算（避免“改完不生效”），并打一条 INFO 便于定性。
 */
public class ContainerSmartWildcard extends Container {

    /** 界面里那个样板槽（也是玩家背包里的真实槽位）。 */
    public static final int SLOT_PATTERN = 0;

    private final EntityPlayer player;
    private final int patternSlotIndex;
    private final InventoryPlayer playerInventory;

    public ContainerSmartWildcard(InventoryPlayer playerInventory, int patternSlotIndex) {
        this.playerInventory = playerInventory;
        this.player = playerInventory.player;
        this.patternSlotIndex = patternSlotIndex;

        // 样板槽：直接用玩家背包里的那个槽位（x/y 由 GUI 负责摆放，这里给 0 占位）
        this.addSlotToContainer(new Slot(playerInventory, patternSlotIndex, 0, 0) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemSmartWildcardPattern;
            }
        });
    }

    public int getPatternSlotIndex() {
        return this.patternSlotIndex;
    }

    /** 当前正在配置的样板物品（服务端权威）。 */
    public ItemStack getPatternStack() {
        return this.playerInventory.getStackInSlot(this.patternSlotIndex);
    }

    /**
     * 服务端：把客户端确认后的规则写进样板 NBT。
     *
     * @return 写入是否成功（失败必留日志，不静默）
     */
    public boolean applyRules(SmartWildcardState incoming, net.minecraft.nbt.NBTTagList templateIn,
        net.minecraft.nbt.NBTTagList templateOut) {
        ItemStack stack = this.getPatternStack();
        if (stack == null || !(stack.getItem() instanceof ItemSmartWildcardPattern)) {
            MyMod.LOG.warn(
                "[AE2QoL] 通配样板写回失败：槽位 {} 里已不是通配样板（player={}）",
                this.patternSlotIndex,
                this.player == null ? "?" : this.player.getCommandSenderName());
            return false;
        }
        if (incoming == null) {
            MyMod.LOG.warn("[AE2QoL] 通配样板写回失败：客户端传来的规则为空");
            return false;
        }
        incoming.writeAndBumpRevision(stack);
        // 模板 in/out：NEI 加号推导出的配方本体。注意顺序——先 writeAndBumpRevision 保证 NBT 根存在。
        try {
            if (stack.getTagCompound() != null) {
                if (templateIn != null && templateIn.tagCount() > 0) {
                    stack.getTagCompound()
                        .setTag("in", templateIn);
                    stack.getTagCompound()
                        .setBoolean("crafting", false);
                }
                if (templateOut != null && templateOut.tagCount() > 0) {
                    stack.getTagCompound()
                        .setTag("out", templateOut);
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[AE2QoL] 写入通配样板模板 in/out 失败（规则已写，模板保留原值）", t);
        }
        SmartWildcardExpander.clearCache();
        this.detectAndSendChanges();
        MyMod.LOG.info(
            "[AE2QoL] 通配样板规则已写回：player={} slot={} rules={} blacklist={} whitelist={} circuit={} revision={}",
            this.player == null ? "?" : this.player.getCommandSenderName(),
            this.patternSlotIndex,
            incoming.rules.size(),
            incoming.blacklist.size(),
            incoming.whitelist.size(),
            incoming.circuit,
            incoming.revision);
        return true;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    /** 不允许用 shift 点击把这张样板移走（配置界面里移走会让写回目标消失）。 */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return null;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        // 关闭时再同步一次，确保客户端最后看到的是服务端权威 NBT
        this.detectAndSendChanges();
    }
}
