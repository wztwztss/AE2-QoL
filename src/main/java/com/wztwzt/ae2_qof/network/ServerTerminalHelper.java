package com.wztwzt.ae2_qof.network;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;

import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;

/**
 * 服务端无线终端解析工具。
 * 从玩家背包中找到无线终端，构造 WirelessTerminalGuiObject，执行提取/合成操作。
 */
public final class ServerTerminalHelper {

    private static Field myPlayerField;
    private static Method baublesGetBaubles;

    static {
        try {
            myPlayerField = WirelessTerminalGuiObject.class.getDeclaredField("myPlayer");
            myPlayerField.setAccessible(true);
        } catch (Throwable ignored) {}

        try {
            Class<?> baublesApi = Class.forName("baubles.api.BaublesApi");
            baublesGetBaubles = baublesApi.getMethod("getBaubles", EntityPlayer.class);
        } catch (Throwable ignored) {}
    }

    private ServerTerminalHelper() {}

    public static EntityPlayer getPlayer(WirelessTerminalGuiObject terminal) {
        try {
            if (myPlayerField != null) {
                return (EntityPlayer) myPlayerField.get(terminal);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 在玩家背包中查找可用的无线终端，构造 WirelessTerminalGuiObject。
     */
    public static WirelessTerminalGuiObject resolveTerminal(EntityPlayerMP player) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack == null) continue;

            WirelessTerminalGuiObject terminal = tryCreateTerminal(player, stack, i);
            if (terminal != null) return terminal;
        }

        // 搜索 Baubles（通过反射避免硬依赖）
        try {
            IInventory baubles = getBaublesInventory(player);
            if (baubles != null) {
                for (int i = 0; i < baubles.getSizeInventory(); i++) {
                    ItemStack stack = baubles.getStackInSlot(i);
                    if (stack == null) continue;

                    WirelessTerminalGuiObject terminal = tryCreateTerminal(player, stack, i);
                    if (terminal != null) return terminal;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static IInventory getBaublesInventory(EntityPlayerMP player) {
        try {
            if (baublesGetBaubles != null) {
                return (IInventory) baublesGetBaubles.invoke(null, player);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static WirelessTerminalGuiObject tryCreateTerminal(EntityPlayerMP player, ItemStack stack, int slotIndex) {
        IWirelessTermHandler wh = AEApi.instance()
            .registries()
            .wireless()
            .getWirelessTerminalHandler(stack);
        if (wh == null || !wh.canHandle(stack)) return null;

        try {
            WirelessTerminalGuiObject terminal = new WirelessTerminalGuiObject(
                wh,
                stack,
                player,
                player.worldObj,
                slotIndex,
                0,
                0);
            if (terminal.rangeCheck()) {
                return terminal;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 从 AE2 网络中提取物品。
     * 
     * @return 提取到的物品堆，null 表示失败
     */
    public static IAEItemStack extractItem(WirelessTerminalGuiObject terminal, IAEItemStack target, long count) {
        return extractItem(terminal, target, count, Actionable.MODULATE);
    }

    /**
     * 从 AE2 网络中提取物品（可指定 SIMULATE 模拟 / MODULATE 真实扣减）。
     */
    public static IAEItemStack extractItem(
        WirelessTerminalGuiObject terminal, IAEItemStack target, long count, Actionable mode) {
        if (terminal == null || target == null) return null;

        IMEMonitor<IAEItemStack> itemInv = terminal.getItemInventory();
        if (itemInv == null) return null;

        IAEItemStack request = target.copy();
        request.setStackSize(count);

        IGridNode node = terminal.getActionableNode();
        if (node == null) return null;

        IActionHost host = (IActionHost) node.getMachine();
        EntityPlayer player = getPlayer(terminal);
        if (player == null) return null;

        PlayerSource actionSrc = new PlayerSource(player, host);
        return Platform.poweredExtraction(terminal, itemInv, request, actionSrc, mode);
    }

    /**
     * 从 AE2 网络中提取物品到玩家背包（自动放入背包）。
     * <p>
     * 保护措施：先 SIMULATE 计算实际可提取量并预先检查背包容量，再 MODULATE 扣减；
     * 若扣减后背包放入失败（极端竞争），将物品归还网络，避免“扣物后物品凭空消失”。
     * 
     * @return true 表示成功
     */
    public static boolean extractItemToInventory(WirelessTerminalGuiObject terminal, IAEItemStack target, long count) {
        EntityPlayer player = getPlayer(terminal);
        if (player == null) return false;

        IAEItemStack simulated = extractItem(terminal, target, count, Actionable.SIMULATE);
        if (simulated == null || simulated.getStackSize() <= 0) return false;

        ItemStack mcStack = simulated.getItemStack();
        if (mcStack == null || mcStack.getItem() == null) return false;

        long canFit = canFitInInventory(player, mcStack);
        if (canFit <= 0) return false;

        long extractCount = Math.min(simulated.getStackSize(), canFit);
        if (extractCount <= 0) return false;

        IAEItemStack extracted = extractItem(terminal, target, extractCount, Actionable.MODULATE);
        if (extracted == null || extracted.getStackSize() <= 0) return false;

        ItemStack realStack = extracted.getItemStack();
        if (realStack == null || realStack.getItem() == null) {
            refund(terminal, extracted);
            return false;
        }

        if (!player.inventory.addItemStackToInventory(realStack)) {
            // 背包放不下（极端竞争）：归还网络，避免丢物
            refund(terminal, extracted);
            return false;
        }
        return true;
    }

    /**
     * 计算玩家主背包还能容纳多少该物品（考虑可堆叠槽位与空格子）。
     */
    private static long canFitInInventory(EntityPlayer player, ItemStack stack) {
        long remaining = stack.stackSize;
        int maxStack = stack.getMaxStackSize();
        if (maxStack <= 0) {
            maxStack = 64;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            if (remaining <= 0) {
                break;
            }
            ItemStack inv = player.inventory.mainInventory[i];
            if (inv == null) {
                remaining -= maxStack;
            } else if (inv.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(inv, stack)) {
                remaining -= Math.min(remaining, inv.getMaxStackSize() - inv.stackSize);
            }
        }
        long canFit = stack.stackSize - remaining;
        return canFit > 0 ? canFit : 0;
    }

    /**
     * 将已从网络扣减但未能放入背包的物品归还网络。
     */
    private static void refund(WirelessTerminalGuiObject terminal, IAEItemStack extracted) {
        try {
            IMEMonitor<IAEItemStack> itemInv = terminal.getItemInventory();
            IGridNode node = terminal.getActionableNode();
            if (itemInv == null || node == null || extracted == null) return;
            IActionHost host = (IActionHost) node.getMachine();
            EntityPlayer player = getPlayer(terminal);
            if (player == null) return;
            itemInv.injectItems(extracted, Actionable.MODULATE, new PlayerSource(player, host));
        } catch (Throwable ignored) {}
    }

    /**
     * 将任务归队到服务端 tick 线程执行（基于 GTNHLib ServerThreadUtil）。
     * 服务端未就绪/已停止时静默丢弃，避免 Netty IO 线程抛异常踢人。
     */
    public static void scheduleServerTask(Runnable task) {
        try {
            if (task != null) {
                ServerThreadUtil.addScheduledTask(task);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 在玩家背包中查找无线终端的物品栏索引。
     * 
     * @return 物品栏索引，-1 表示未找到
     */
    public static int findTerminalSlot(EntityPlayerMP player) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack == null) continue;

            IWirelessTermHandler wh = AEApi.instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(stack);
            if (wh != null && wh.canHandle(stack)) {
                return i;
            }
        }
        return -1;
    }
}
