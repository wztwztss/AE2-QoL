package com.wztwzt.ae2_qof.network;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.ImmutableCollection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.wztwzt.ae2_qof.MyMod;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.sync.GuiBridge;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;

/**
 * 服务端无线终端解析工具。
 * 从玩家背包中找到无线终端，构造 WirelessTerminalGuiObject，执行提取/合成操作。
 */
public final class ServerTerminalHelper {

    private static Field myPlayerField;
    private static Method baublesGetBaubles;
    /** 存量判定异常只记一次警告，避免热路径刷屏。 */
    private static boolean stockCheckWarned;

    // ===== fix53-diag：诊断构建专用（仅记录分支，不改变任何判定；正式修复时收敛为“失败即记录”） =====
    private static final Set<String> diagLogged = Collections
        .newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /**
     * fix53-diag：把「世界中键下单」这条链路上原本**静默**的放行/失败分支记录下来，每个分支只记一次。
     * <p>
     * 之所以需要它：该链路有 4 条静默 return 与若干静默 false 出口，源码无法区分运行期命中了哪一条，
     * 只能靠一次埋点观测定位（详见 CHANGELOG 的 fix53-diag 一节）。
     */
    public static void diagOnce(String branch, String detail) {
        try {
            if (diagLogged.add(branch)) {
                MyMod.LOG.info("[AE2QoL][diag] branch {}: {}", branch, detail);
            }
        } catch (Throwable ignored) {}
    }

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
     * <p>
     * 查找顺序与 AE2 世界取物（{@code PlayerInventoryUtil.getFirstWirelessTerminal}）一致：
     * **先饰品栏、再主背包**。顺序必须对齐，否则玩家同时携带两个绑定不同网络的终端时，
     * 本工具选中的终端可能与原版取物用的不是同一个（世界里键取物的存量判定会因此错判）。
     */
    public static WirelessTerminalGuiObject resolveTerminal(EntityPlayerMP player) {
        if (player == null) return null;

        // 先搜 Baubles（通过反射避免硬依赖），与 AE2 原版取物顺序一致
        try {
            IInventory baubles = getBaublesInventory(player);
            if (baubles != null) {
                for (int i = 0; i < baubles.getSizeInventory(); i++) {
                    ItemStack stack = baubles.getStackInSlot(i);
                    if (stack == null) continue;

                    // 饰品栏槽位要换算成 AE2 的虚拟索引，否则打开 GUI 时
                    // Platform.getItemFromPlayerInventoryBySlotIndex 会解析到错误的物品。
                    WirelessTerminalGuiObject terminal = tryCreateTerminal(
                        player,
                        stack,
                        Platform.baublesSlotsOffset + i);
                    if (terminal != null) return terminal;
                }
            }
        } catch (Throwable ignored) {}

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack == null) continue;

            WirelessTerminalGuiObject terminal = tryCreateTerminal(player, stack, i);
            if (terminal != null) return terminal;
        }

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
    public static IAEItemStack extractItem(WirelessTerminalGuiObject terminal, IAEItemStack target, long count,
        Actionable mode) {
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
        } catch (Throwable t) {
            // fix53-diag：这里原本是完全静默的 catch——若 GTNHLib 排任务抛错，功能会无声失效。
            diagOnce("F", "任务排入失败（scheduleServerTask）: " + t);
        }
    }

    /**
     * 在玩家背包中查找无线终端的物品栏索引。
     * <p>
     * 返回的是 AE2 {@code Platform.getItemFromPlayerInventoryBySlotIndex} 能直接解析的「槽位索引」：
     * 主背包 → 原样索引；Baubles 饰品栏 → {@code 100012 + 饰品索引}。
     * 查找顺序与 AE2 世界取物的 {@code PlayerInventoryUtil.getFirstWirelessTerminal} 一致
     * （先 Baubles 再主背包），保证打开下单界面时用的正是原版会用的那个终端。
     *
     * @return 物品栏索引，-1 表示未找到
     */
    public static int findTerminalSlot(EntityPlayerMP player) {
        if (player == null) return -1;

        // Baubles 优先，与 AE2 PlayerInventoryUtil.getFirstWirelessTerminal 顺序一致
        try {
            IInventory baubles = getBaublesInventory(player);
            if (baubles != null) {
                for (int i = 0; i < baubles.getSizeInventory(); i++) {
                    ItemStack stack = baubles.getStackInSlot(i);
                    if (stack == null) continue;
                    if (isWirelessTerminal(stack)) {
                        return Platform.baublesSlotsOffset + i;
                    }
                }
            }
        } catch (Throwable ignored) {}

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack == null) continue;

            if (isWirelessTerminal(stack)) {
                return i;
            }
        }
        return -1;
    }

    /** 是否为 AE2 可识别的无线终端物品。 */
    private static boolean isWirelessTerminal(ItemStack stack) {
        try {
            IWirelessTermHandler wh = AEApi.instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(stack);
            return wh != null && wh.canHandle(stack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 网络中既没有该物品的存量、又存在可用的合成样板时，为玩家打开 AE2 原生的
     * 「要合成多少个」输入界面（gui.craftAmount）。玩家仍需自己填数量并点确认，
     * 这里只是把入口打开，不会代为下单。
     * <p>
     * 调用方必须先确认「网络无存量」；本方法会再次校验样板是否真的存在，
     * 不存在时返回 false 且不做任何界面操作（调用方按原逻辑静默放行）。
     *
     * @param player   目标玩家（服务端实体）
     * @param terminal 已解析出的、在范围内的无线终端
     * @param target   想合成的物品
     * @return true 表示界面已成功打开，调用方应拦截后续原版逻辑
     */
    public static boolean openCraftAmountIfCraftable(EntityPlayerMP player, WirelessTerminalGuiObject terminal,
        IAEItemStack target) {
        if (player == null || terminal == null || target == null) {
            diagOnce("G0", "参数为空");
            return false;
        }

        // 已经在该界面里时不重复打开：界面弹出有网络延迟，点击过快可能在界面出现前
        // 连发几个包，重复打开会把玩家已经填好的数量清空。
        if (player.openContainer instanceof ContainerCraftAmount) {
            diagOnce("G6", "已在下单界面内，视为成功");
            return true;
        }

        IGrid grid = terminal.getGrid();
        if (grid == null) {
            diagOnce("G1", "终端 getGrid() 为空");
            return false;
        }

        ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
        if (craftingGrid == null) {
            diagOnce("G2", "craftingGrid 为空");
            return false;
        }

        ImmutableCollection<ICraftingPatternDetails> patterns = craftingGrid
            .getCraftingFor(target, null, 0, player.worldObj);
        if (patterns == null || patterns.isEmpty()) {
            diagOnce("G3", "合成网格里查不到该物品的样板（patterns 为空）");
            return false;
        }

        // 用已解析终端自身的槽位，保证「校验样板的网络」与「打开界面后玩家操作的网络」是同一个终端；
        // 若玩家身上有多个绑定不同网络的终端，各自查找可能选出不同对象。
        int slotIndex = terminal.getInventorySlot();
        if (slotIndex < 0) {
            slotIndex = findTerminalSlot(player);
        }
        if (slotIndex < 0) {
            diagOnce("G4", "终端槽位非法");
            return false;
        }

        Platform.openGUI(player, null, null, GuiBridge.GUI_CRAFTING_AMOUNT, slotIndex);

        if (player.openContainer instanceof ContainerCraftAmount cca) {
            cca.setItemToCraft(target);
            cca.detectAndSendChanges();
            diagOnce("G7", "界面已打开，slot=" + slotIndex + "，样板数=" + patterns.size());
            return true;
        }
        diagOnce("G5", "openGUI 之后 openContainer 仍是 " + player.openContainer.getClass().getName());
        return false;
    }

    /**
     * 判断 ME 网络中当前是否还有该物品可提取（只做 SIMULATE，不扣减）。
     * 用于「世界中键取物」在开合成界面之前先确认网络确实没货，避免网络还有货时也弹界面。
     *
     * @return true 表示网络里至少还能取出 1 个
     */
    public static boolean hasNetworkStock(WirelessTerminalGuiObject terminal, IAEItemStack target) {
        if (terminal == null || target == null) return false;
        try {
            IMEMonitor<IAEItemStack> itemInv = terminal.getItemInventory();
            if (itemInv == null) return false;

            IAEItemStack request = target.copy();
            request.setStackSize(1);

            EntityPlayer player = getPlayer(terminal);
            if (player == null) return false;

            // 与 AE2 世界取物同口径：只做模拟读取，不经过耗电判定，
            // 避免「终端没电」被误判成「网络没存量」而弹出合成界面。
            IAEItemStack simulated = itemInv.extractItems(request, Actionable.SIMULATE, new PlayerSource(player, null));
            return simulated != null && simulated.getStackSize() > 0;
        } catch (Throwable t) {
            // fix52：原先这里按「有存量」返回 true（把流程让回原版）。问题是调用方据此直接放行，
            // 于是一次判定异常就会让「世界中键下单」永久静默失效，且不留任何痕迹——
            // 这正是该功能此前难以定位的原因（参见审查 P2-027 对静默捕获的要求）。
            // 改为按「无存量」返回 false 并记一条警告：调用方会继续尝试开界面，
            // 而界面只在真的存在可用样板时才会打开，最坏结果只是多弹一次无害的合成界面。
            if (!stockCheckWarned) {
                stockCheckWarned = true;
                MyMod.LOG.warn("[AE2QoL] hasNetworkStock check failed, treating as no stock: {}", t.toString());
            }
            return false;
        }
    }
}
