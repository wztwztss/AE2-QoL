package com.wztwzt.ae2_qof.hatch.adaptive;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import gregtech.common.misc.spaceprojects.SpaceProjectManager;

public final class AdaptiveTeamHelper {

    private AdaptiveTeamHelper() {}

    public static UUID resolveLeader(UUID viewerUuid) {
        if (viewerUuid == null) return null;
        SpaceProjectManager.checkOrCreateTeam(viewerUuid);
        return SpaceProjectManager.getLeader(viewerUuid);
    }

    public static Set<UUID> resolveMembers(UUID viewerUuid) {
        Set<UUID> resolved = new HashSet<>();
        if (viewerUuid == null) return resolved;
        SpaceProjectManager.checkOrCreateTeam(viewerUuid);
        UUID leader = SpaceProjectManager.getLeader(viewerUuid);
        Collection<UUID> members = SpaceProjectManager.getTeamMembers(leader);
        if (members != null) resolved.addAll(members);
        resolved.add(leader);
        return resolved;
    }

    public static boolean isMemberOf(UUID playerUuid, UUID leaderUuid) {
        if (playerUuid == null || leaderUuid == null) return false;
        if (playerUuid.equals(leaderUuid)) return true;
        return resolveMembers(playerUuid).contains(leaderUuid);
    }
}
