package com.wztwzt.ae2_qof.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import cn.dancingsnow.aeinfinitycell.nei.InfinityCellViewPreview.Page;

/**
 * 无限元件 NEI 预览缓存（3.19.0-fix24，纯客户端）：
 * <ul>
 * <li>内容存服务端存档，客户端只能经网络索取，按 storageId 缓存分页快照；</li>
 * <li>TTL 与请求节流复用 tooltip 统计的 2 秒窗口，避免反复开 NEI 时刷包。</li>
 * </ul>
 * pages 为 null 表示服务端确认无数据；空列表表示元件存在但没有任何内容。
 */
public final class InfinityCellViewCache {

    private static final long TTL_MS = 5_000L;
    private static final int MAX_ENTRIES = 64;

    private static final class Entry {

        List<Page> pages; // null = 服务端无数据
        long at;
    }

    private static final Map<UUID, Entry> DATA = new HashMap<UUID, Entry>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<UUID, Long>();

    private InfinityCellViewCache() {}

    /** 命中返回分页快照（可能为 null 表示已知无数据）；未命中或过期返回 null。 */
    public static synchronized List<Page> get(UUID id) {
        Entry e = DATA.get(id);
        if (e == null) return null;
        if (System.currentTimeMillis() - e.at > TTL_MS) {
            DATA.remove(id);
            return null;
        }
        return e.pages;
    }

    public static synchronized void put(UUID id, List<Page> pages) {
        Entry e = new Entry();
        e.pages = pages;
        e.at = System.currentTimeMillis();
        DATA.put(id, e);
        LAST_REQUEST.remove(id);
        if (DATA.size() > MAX_ENTRIES) {
            evictOld();
        }
    }

    /** 距上次请求超过窗口才允许再次请求。 */
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
        if (DATA.size() > MAX_ENTRIES) {
            DATA.clear();
        }
    }
}
