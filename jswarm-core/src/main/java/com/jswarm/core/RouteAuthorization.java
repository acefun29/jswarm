package com.jswarm.core;

import java.util.Set;

public final class RouteAuthorization {

    private RouteAuthorization() {
    }

    public static void authorizeHandoff(Swarm swarm, String sourceAgentId, String targetAgentId) {
        authorize(swarm, sourceAgentId, targetAgentId, swarm.getHandoffTargets(sourceAgentId));
    }

    public static void authorizeDelegate(Swarm swarm, String sourceAgentId, String targetAgentId) {
        authorize(swarm, sourceAgentId, targetAgentId, swarm.getDelegateTargets(sourceAgentId));
    }

    private static void authorize(Swarm swarm, String sourceAgentId, String targetAgentId,
                                  Set<String> allowedTargets) {
        if (targetAgentId == null || targetAgentId.isBlank()) {
            throw new RouteDeniedException(RouteDeniedException.Reason.BLANK_TARGET, sourceAgentId, targetAgentId);
        }
        if (sourceAgentId == null || sourceAgentId.isBlank() || !swarm.contains(sourceAgentId)) {
            throw new RouteDeniedException(RouteDeniedException.Reason.SOURCE_NOT_FOUND, sourceAgentId, targetAgentId);
        }
        if (!swarm.contains(targetAgentId)) {
            throw new RouteDeniedException(RouteDeniedException.Reason.TARGET_NOT_FOUND, sourceAgentId, targetAgentId);
        }
        if (!allowedTargets.contains(targetAgentId)) {
            throw new RouteDeniedException(RouteDeniedException.Reason.EDGE_NOT_AUTHORIZED, sourceAgentId, targetAgentId);
        }
    }
}
