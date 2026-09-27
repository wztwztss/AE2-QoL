/*
 * 本文件搬运自 AE2PatternGen 1.5（作者 com.github.ae2patterngen，MIT 许可；用户已授权在保留声明的前提下先行复制、再改再优化）。
 * 本仓库的改动：包名改为 com.wztwzt.ae2_qof.apgport；对原模组主类/代理的引用改为本模组的 MyMod；
 * 物品注册入口不会被调用（我们仍用自己的批量样板生成器物品）。其余逻辑保持原样，便于后续按需优化。
 */
package com.wztwzt.ae2_qof.apgport.network;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.wztwzt.ae2_qof.apgport.config.ForgeConfig;

/**
 * Collapses duplicate generate requests emitted by a single GUI interaction.
 */
final class PatternGenerationRequestGate {

    private static final Map<UUID, RecentRequest> RECENT_REQUESTS = new HashMap<UUID, RecentRequest>();

    private PatternGenerationRequestGate() {}

    static synchronized boolean shouldProcess(UUID playerUUID, String fingerprint, long nowMillis) {
        cleanupExpired(nowMillis);

        long duplicateWindow = ForgeConfig.getDuplicateWindowMs();
        RecentRequest previous = RECENT_REQUESTS.get(playerUUID);
        if (previous != null && previous.fingerprint.equals(fingerprint)
            && nowMillis - previous.timestampMillis <= duplicateWindow) {
            return false;
        }

        RECENT_REQUESTS.put(playerUUID, new RecentRequest(fingerprint, nowMillis));
        return true;
    }

    static String fingerprint(String recipeMapId, String outputOreDict, String inputOreDict, String ncItem,
        String blacklistInput, String blacklistOutput, String replacements, int targetTier) {
        return normalize(recipeMapId) + '\u001F'
            + normalize(outputOreDict)
            + '\u001F'
            + normalize(inputOreDict)
            + '\u001F'
            + normalize(ncItem)
            + '\u001F'
            + normalize(blacklistInput)
            + '\u001F'
            + normalize(blacklistOutput)
            + '\u001F'
            + normalize(replacements)
            + '\u001F'
            + targetTier;
    }

    static synchronized void reset() {
        RECENT_REQUESTS.clear();
    }

    private static void cleanupExpired(long nowMillis) {
        long duplicateWindow = ForgeConfig.getDuplicateWindowMs();
        Iterator<Map.Entry<UUID, RecentRequest>> iterator = RECENT_REQUESTS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            RecentRequest request = iterator.next()
                .getValue();
            if (nowMillis - request.timestampMillis > duplicateWindow) {
                iterator.remove();
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class RecentRequest {

        private final String fingerprint;
        private final long timestampMillis;

        private RecentRequest(String fingerprint, long timestampMillis) {
            this.fingerprint = fingerprint;
            this.timestampMillis = timestampMillis;
        }
    }
}
