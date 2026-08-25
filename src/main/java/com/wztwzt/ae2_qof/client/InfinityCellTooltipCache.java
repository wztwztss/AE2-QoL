package com.wztwzt.ae2_qof.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 无限磁盘 tooltip 统计缓存（3.13.0，纯客户端）：
 * <ul>
 * <li>统计数据 TTL 2 秒，过期视为未命中；</li>
 * <li>请求节流：同 id 每 2 秒至多发一次 C2S，避免悬停抖动刷包。</li>
 * </ul>
 * stats 布局见 {@link com.wztwzt.ae2_qof.network.InfinityCellStatsPacket#collectStats}；
 * null 表示服务端确认无数据（短缓存防止反复请求）。
 */
public final class InfinityCellTooltipCache {

    private static final long TTL_MS = 2_000L;

    private static final class Entry {

        long[] stats; // null = 服务端无数据
        long at;
    }

    private static final Map<UUID, Entry> DATA = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

    private InfinityCellTooltipCache() {}

    /** 命中返回统计数据（可能为 null 表示已知无数据）；过期返回 null。 */
    public static synchronized long[] get(UUID id) {
        Entry e = DATA.get(id);
        if (e == null) return null;
        if (System.currentTimeMillis() - e.at > TTL_MS) {
            DATA.remove(id);
            return null;
        }
        return e.stats;
    }

    public static synchronized void put(UUID id, long[] stats) {
        Entry e = new Entry();
        e.stats = stats;
        e.at = System.currentTimeMillis();
        DATA.put(id, e);
        LAST_REQUEST.remove(id);
        if (DATA.size() > 256) {
            evictOld();
        }
    }

    /** 距上次请求超过 TTL 才允许再次请求。 */
    public static synchronized boolean shouldRequest(UUID id) {
        Long last = LAST_REQUEST.get(id);
        long now = System.currentTimeMillis();
        if (last != null && now - last < TTL_MS) {
            return false;
        }
        LAST_REQUEST.put(id, now);
        return true;
    }

    private static void evictOld() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Entry>> it = DATA.entrySet()
            .iterator();
        while (it.hasNext()) {
            if (now - it.next()
                .getValue().at > TTL_MS) {
                it.remove();
            }
        }
        if (DATA.size() > 256) {
            DATA.clear();
        }
    }
}
