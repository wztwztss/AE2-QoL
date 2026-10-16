package cn.dancingsnow.aeinfinitycell.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cn.dancingsnow.aeinfinitycell.AEInfinityCell;
import cn.dancingsnow.aeinfinitycell.item.ItemInfinityStorageCell;

/**
 * 3.35.0-fix19（审查 P2-13）：无限元件**内容/统计回传的归属校验**。
 *
 * <p>
 * 两个 C2S 请求包（{@code InfinityCellStatsPacket} 统计、{@code InfinityCellViewPacket} 内容分页）
 * 旧实现只凭请求里的 storageId 就把数据回传给请求者——元件 UUID 虽是 122 位随机数（存在物品 NBT 里，
 * 不可枚举），但一旦泄漏（截图/共享/日志），任何人都能拉走该元件的**完整内容**。本校验把"知道 UUID"
 * 收紧为"当前真的把元件拿在场景里"。
 *
 * <p>
 * 校验口径（与合法触发面精确对应）：NEI U 键 / tooltip 的合法请求必然发生在"客户端正打开的 GUI 里
 * 悬停着带 storageId NBT 的元件" ⇒ 服务端对应集合 = **当前打开容器的槽位 ∪ 背包**。打开他人容器
 * 本身就需要访问权限，故该口径不破坏任何合法流程（含"元件放在 ME 驱动器/箱子里悬停"）；而
 * NEI 物品列表面板的物品不带玩家 NBT（{@code getStorageId} 返回 null 直接短路），不会误伤搜索场景。
 */
public final class InfinityCellAccessPolicy {

    private InfinityCellAccessPolicy() {}

    /**
     * 请求玩家当前是否**真实持有**该 UUID 的元件（背包或已打开容器之一）。
     */
    public static boolean mayView(EntityPlayer player, UUID id) {
        if (player == null || id == null) return false;
        ItemStack[] main = player.inventory.mainInventory;
        if (main != null) {
            for (ItemStack s : main) {
                if (isCellWithId(s, id)) return true;
            }
        }
        Container open = player.openContainer;
        if (open != null && open.inventorySlots != null) {
            // 1.7.10 的 Container.inventorySlots 是 raw List（含玩家背包格与容器格）——
            // 正常玩家不在任何 GUI 时它等于 inventoryContainer（自身 36 格），同样覆盖
            for (Object o : open.inventorySlots) {
                if (o instanceof Slot) {
                    ItemStack s = ((Slot) o).getStack();
                    if (isCellWithId(s, id)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isCellWithId(ItemStack stack, UUID id) {
        if (stack == null || !(stack.getItem() instanceof ItemInfinityStorageCell)) return false;
        UUID sid = ItemInfinityStorageCell.getStorageId(stack);
        return id.equals(sid);
    }

    /**
     * 拒绝回传时的限频 WARN（每玩家 60 秒一次）：不给攻击者反馈频率，但"有人在探测"必须可见。
     * 拒绝本身**不回数据**——防探测成功与否不可从响应区分。
     */
    private static final long DENY_WARN_INTERVAL_MS = 60_000L;
    private static final Map<UUID, Long> DENY_WARN_AT =
        new ConcurrentHashMap<UUID, Long>();

    public static void warnDenied(EntityPlayer player, UUID id) {
        try {
            UUID pid = player != null ? player.getUniqueID() : null;
            if (pid == null) return;
            long now = System.currentTimeMillis();
            Long last = DENY_WARN_AT.get(pid);
            if (last != null && now - last < DENY_WARN_INTERVAL_MS) return;
            if (DENY_WARN_AT.size() > 1024) {
                DENY_WARN_AT.clear(); // 有界
            }
            DENY_WARN_AT.put(pid, now);
            AEInfinityCell.LOG.warn(
                "[AE2QoL] 玩家请求了其场景内不存在的无限元件数据，已拒绝（60 秒内同玩家只提示一次）：player={} cell={}",
                player.getCommandSenderName(),
                id);
        } catch (Throwable ignored) {}
    }
}
