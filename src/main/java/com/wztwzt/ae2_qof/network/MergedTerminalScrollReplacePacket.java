package com.wztwzt.ae2_qof.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.wztwzt.ae2_qof.MyMod;
import com.wztwzt.ae2_qof.merged.ContainerMergedTerminal;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 二合一终端面板：Shift+滚轮替换同 OreDict 物品（C2S）。
 * 服务端查询 ME 网络中具有相同 OreDict 名的物品，替换面板槽内容。
 */
public class MergedTerminalScrollReplacePacket implements IMessage {

    private int slotNumber;
    private int direction;

    public MergedTerminalScrollReplacePacket() {
        this.slotNumber = -1;
        this.direction = 0;
    }

    public MergedTerminalScrollReplacePacket(int slotNumber, int direction) {
        this.slotNumber = slotNumber;
        this.direction = direction;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.slotNumber = buf.readInt();
            this.direction = buf.readByte();
        } catch (Throwable t) {
            this.slotNumber = -1;
            this.direction = 0;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.slotNumber);
        buf.writeByte(this.direction);
    }

    public static class Handler implements IMessageHandler<MergedTerminalScrollReplacePacket, IMessage> {

        @Override
        public IMessage onMessage(MergedTerminalScrollReplacePacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            ServerTerminalHelper.scheduleServerTask(() -> {
                try {
                    Container container = player.openContainer;
                    if (!(container instanceof ContainerMergedTerminal cmt)) return;
                    if (message.direction == 0) return;
                    if (message.slotNumber < 0 || message.slotNumber >= container.inventorySlots.size()) return;

                    Slot slot = container.inventorySlots.get(message.slotNumber);
                    if (slot == null) return;
                    // P0-001 安全校验：只允许处理面板虚拟样板格。
                    // 该容器同时包含玩家背包真实槽与真实样板槽，若不校验槽对象身份，
                    // 伪造包可把网络物品表示直接写进真实背包/样板槽，绕过 ME 网络提取形成刷物。
                    if (!cmt.isVirtualPanelSlot(slot)) return;
                    ItemStack current = slot.getStack();
                    if (current == null) return;

                    List<ItemStack> alternatives = findAlternatives(cmt, message.slotNumber);
                    if (alternatives.isEmpty()) return;

                    // 基于当前物品在候选中的位置循环推进（修复原先只跳首/尾的问题）
                    int cur = -1;
                    for (int i = 0; i < alternatives.size(); i++) {
                        ItemStack a = alternatives.get(i);
                        if (com.wztwzt.ae2_qof.util.ItemIdentity.same(a, current)) {
                            cur = i;
                            break;
                        }
                    }
                    int size = alternatives.size();
                    int nextIdx;
                    if (cur < 0) {
                        nextIdx = message.direction > 0 ? 0 : size - 1;
                    } else {
                        nextIdx = message.direction > 0 ? (cur + 1) % size : (cur - 1 + size) % size;
                    }

                    ItemStack replacement = alternatives.get(nextIdx)
                        .copy();
                    replacement.stackSize = current.stackSize;
                    slot.putStack(replacement);
                } catch (Throwable t) {
                    MyMod.LOG.error("Merged terminal scroll replace failed", t);
                }
            });
            return null;
        }
    }

    /**
     * 查询 ME 网络中与指定槽位物品同 OreDict 的候选列表。
     * 供滚轮替换与候选预览共用；返回稳定排序副本，包含当前类型（忽略数量、保留 NBT 身份）。
     * <p>
     * 候选全部取自当前终端 ME 网络的库存列表，因此换入的必定是网络里已存在的物品；
     * 面板虚拟格只是样板的「表示」，本身不持有真实物品，故不存在凭空生成物品的路径。
     */
    public static List<ItemStack> findAlternatives(ContainerMergedTerminal cmt, int slotNumber) {
        List<ItemStack> result = new ArrayList<>();
        try {
            Slot slot = cmt.inventorySlots.get(slotNumber);
            if (slot == null) return result;
            if (!cmt.isVirtualPanelSlot(slot)) return result;
            ItemStack current = slot.getStack();
            if (current == null) return result;

            IGrid grid = cmt.getGrid();
            if (grid == null) return result;

            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return result;

            IItemList<IAEItemStack> items = storageGrid.getItemInventory()
                .getStorageList();
            if (items == null) return result;

            int[] currentOreIds = OreDictionary.getOreIDs(current);
            if (currentOreIds.length == 0) return result;

            java.util.Set<com.wztwzt.ae2_qof.util.ItemIdentity> seen = new java.util.HashSet<>();
            for (IAEItemStack aeStack : items) {
                if (aeStack == null || aeStack.getStackSize() <= 0) continue;
                ItemStack is = aeStack.getItemStack();
                if (is == null || is.getItem() == null) continue;

                int[] otherOreIds = OreDictionary.getOreIDs(is);
                if (otherOreIds.length == 0) continue;

                boolean match = false;
                for (int id : currentOreIds) {
                    for (int otherId : otherOreIds) {
                        if (id == otherId) {
                            match = true;
                            break;
                        }
                    }
                    if (match) break;
                }
                if (match && seen.add(com.wztwzt.ae2_qof.util.ItemIdentity.of(is))) result.add(is.copy());
            }
            // Include the current ghost type even if its physical stock just reached zero.
            if (seen.add(com.wztwzt.ae2_qof.util.ItemIdentity.of(current))) result.add(current.copy());
            result.sort(java.util.Comparator
                .comparingInt((ItemStack stack) -> net.minecraft.item.Item.getIdFromItem(stack.getItem()))
                .thenComparingInt(ItemStack::getItemDamage)
                .thenComparing(stack -> cn.dancingsnow.aeinfinitycell.storage.NbtKey.of(stack.getTagCompound()).toString()));
        } catch (Throwable ignored) {}
        return result;
    }
}
