package com.gali.ae2_auto_pattern_upload.network;

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
        return Platform.poweredExtraction(terminal, itemInv, request, actionSrc, Actionable.MODULATE);
    }

    /**
     * 从 AE2 网络中提取物品到玩家背包（自动放入背包）。
     * 
     * @return true 表示成功
     */
    public static boolean extractItemToInventory(WirelessTerminalGuiObject terminal, IAEItemStack target, long count) {
        EntityPlayer player = getPlayer(terminal);
        if (player == null) return false;

        IAEItemStack extracted = extractItem(terminal, target, count);
        if (extracted == null) return false;

        ItemStack mcStack = extracted.getItemStack();
        if (mcStack == null) return false;

        return player.inventory.addItemStackToInventory(mcStack);
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
