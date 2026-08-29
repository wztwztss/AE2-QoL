package com.wztwzt.ae2_qof.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.wztwzt.ae2_qof.MyMod;

import appeng.api.config.FuzzyMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api.questing.tasks.IItemTask;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.storage.INameCache;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import bq_standard.tasks.TaskRetrieval;
import cpw.mods.fml.common.Loader;

/**
 * 「ME 任务检测器」核心逻辑（3.11.0）。
 * <p>
 * 机制对齐 GTNH BQ 官方观察站 TileObservationStation：周期性把 ME 网络中的物品以只读方式
 * 喂给检索型任务 {@code TaskRetrieval.retrieveItems}（consume=false 时官方实现自带守卫，
 * 不消耗任何物品；consume=true 的任务在官方实现入口直接 return，天然跳过）。
 * <p>
 * 隔离约定（#74 教训）：本类是唯一允许 import betterquesting/bq_standard 类型的文件；
 * 宿主 TE 仅在 {@link #bqAvailable()} 通过后才调用本类，BQ 缺失时本类永不加载，
 * 保证未安装 BetterQuesting 的环境（含专用服）零影响。
 * <p>
 * 性能：任务需求键缓存 10 秒/玩家重建一次；网络侧按候选键 findFuzzy 收集，
 * 避免大网络全量列表直传；候选与命中数量均有上限保护。
 */
public final class QuestDetectLogic {

    /** 单次检测喂给任务的物品条目上限。 */
    private static final int MAX_AVAILABLE = 2048;
    /** 单个玩家的需求键缓存有效期。 */
    private static final long CACHE_MS = 10_000L;
    /** 需求键缓存上限（矿辞展开保护）。 */
    private static final int MAX_KEYS = 4096;

    private static final class Cache {

        List<IAEItemStack> keys = new ArrayList<>();
        long at;
    }

    private static final Map<UUID, Cache> CACHES = new HashMap<>();

    private QuestDetectLogic() {}

    /** BQ 是否可用（唯一守卫入口；未通过时宿主不得触碰本类其余成员）。 */
    public static boolean bqAvailable() {
        return Loader.isModLoaded("betterquesting");
    }

    /**
     * 对绑定玩家执行一轮「ME 网络库存 → 检索型任务」匹配。
     *
     * @param grid   检测器所在的 ME 网络
     * @param player 绑定玩家（必须在线）
     */
    public static void runDetection(IGrid grid, EntityPlayerMP player) {
        try {
            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null) return;
            IMEMonitor<IAEItemStack> inv = storage.getItemInventory();
            if (inv == null) return;
            IItemList<IAEItemStack> all = inv.getStorageList();

            IQuestDatabase db = QuestingAPI.getAPI(ApiReference.QUEST_DB);
            if (db == null) return;

            UUID playerId = QuestingAPI.getQuestingUUID(player);
            ParticipantInfo pInfo = new ParticipantInfo(player);
            Map<UUID, IQuest> quests = db.filterKeys(pInfo.getSharedQuests());
            if (quests.isEmpty()) return;

            List<IAEItemStack> keys = cachedKeys(quests, pInfo.UUID);
            if (keys.isEmpty()) return;

            List<ItemStack> available = gatherAvailable(all, keys);
            if (available.isEmpty()) return;

            ItemStack[] arr = available.toArray(new ItemStack[available.size()]);
            for (Map.Entry<UUID, IQuest> qe : quests.entrySet()) {
                for (DBEntry<ITask> te : qe.getValue()
                    .getTasks()
                    .getEntries()) {
                    ITask task = te.getValue();
                    if (task instanceof IItemTask) {
                        ((IItemTask) task).retrieveItems(pInfo, qe, arr);
                    }
                }
            }
        } catch (Throwable t) {
            MyMod.LOG.warn("[QuestDetector] detect failed: " + t);
        }
    }

    /** 清理指定玩家缓存（玩家离线时调用点可省略——缓存带过期自愈）。 */
    public static void dropCache(UUID playerId) {
        CACHES.remove(playerId);
    }

    private static List<IAEItemStack> cachedKeys(Map<UUID, IQuest> quests, UUID playerId) {
        long now = System.currentTimeMillis();
        synchronized (CACHES) {
            Cache c = CACHES.get(playerId);
            if (c != null && now - c.at < CACHE_MS) {
                return c.keys;
            }
            Cache fresh = new Cache();
            fresh.at = now;
            try {
                fresh.keys = collectRequiredKeys(quests, playerId);
            } catch (Throwable t) {
                MyMod.LOG.warn("[QuestDetector] rebuild keys failed: " + t);
            }
            CACHES.put(playerId, fresh);
            return fresh.keys;
        }
    }

    /**
     * 从任务书收集检索型任务的需求键：精确条目 + 矿辞展开。
     * consume=true 与已完成的任务不参与。
     */
    private static List<IAEItemStack> collectRequiredKeys(Map<UUID, IQuest> quests, UUID playerId) {
        List<IAEItemStack> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (IQuest quest : quests.values()) {
            for (DBEntry<ITask> te : quest.getTasks()
                .getEntries()) {
                ITask task = te.getValue();
                if (!(task instanceof TaskRetrieval)) continue;
                TaskRetrieval retrieval = (TaskRetrieval) task;
                if (retrieval.consume || retrieval.isComplete(playerId)) continue;
                for (BigItemStack req : retrieval.requiredItems) {
                    try {
                        appendKey(keys, seen, req == null ? null : req.getBaseStack());
                        if (req != null && req.hasOreDict()) {
                            for (ItemStack ore : OreDictionary.getOres(req.getOreDict())) {
                                if (ore == null || ore.getItem() == null) continue;
                                appendKey(keys, seen, ore);
                            }
                        }
                    } catch (Throwable ignored) {}
                    if (keys.size() >= MAX_KEYS) return keys;
                }
            }
        }
        return keys;
    }

    private static void appendKey(List<IAEItemStack> keys, Set<String> seen, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;
        String dedupe = stack.getItem() + "|" + stack.getItemDamage();
        if (!seen.add(dedupe)) return;
        IAEItemStack key = AEItemStack.create(stack);
        if (key != null) {
            keys.add(key);
        }
    }

    /** 按候选键从网络收集实际存在的物品（PERCENT_99 近似忽略 NBT，多给无害由任务侧过滤）。 */
    private static List<ItemStack> gatherAvailable(IItemList<IAEItemStack> all, List<IAEItemStack> keys) {
        List<ItemStack> available = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        for (IAEItemStack key : keys) {
            if (available.size() >= MAX_AVAILABLE) break;
            try {
                for (IAEItemStack found : all.findFuzzy(key, FuzzyMode.PERCENT_99)) {
                    if (found == null || found.getStackSize() <= 0) continue;
                    ItemStack stack = found.getItemStack();
                    if (stack == null || stack.stackSize <= 0) continue;
                    if (!dedupe.add(stack.getItem() + "|" + stack.getItemDamage())) continue;
                    available.add(stack);
                    if (available.size() >= MAX_AVAILABLE) break;
                }
            } catch (Throwable ignored) {}
        }
        return available;
    }

    /** WAILA/JADE 用：解析绑定玩家显示名（BQ NameCache），不可用时返回 null。 */
    public static String resolvePlayerName(UUID owner) {
        try {
            INameCache cache = QuestingAPI.getAPI(ApiReference.NAME_CACHE);
            if (cache == null) return null;
            String name = cache.getName(owner);
            return name == null || name.isEmpty() ? null : name;
        } catch (Throwable t) {
            return null;
        }
    }
}
