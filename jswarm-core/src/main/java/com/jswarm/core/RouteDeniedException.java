package com.jswarm.core;

public final class RouteDeniedException extends SwarmException {

    public enum Reason {
        BLANK_TARGET,
        SOURCE_NOT_FOUND,
        TARGET_NOT_FOUND,
        EDGE_NOT_AUTHORIZED
    }

    private final Reason reason;
    private final String sourceAgentId;
    private final String targetAgentId;

    public RouteDeniedException(Reason reason, String sourceAgentId, String targetAgentId) {
        super(buildMessage(reason, sourceAgentId, targetAgentId));
        this.reason = reason;
        this.sourceAgentId = sourceAgentId;
        this.targetAgentId = targetAgentId;
    }

    public Reason reason() {
        return reason;
    }

    public String sourceAgentId() {
        return sourceAgentId;
    }

    public String targetAgentId() {
        return targetAgentId;
    }

    public String modelSafeMessage() {
        return switch (reason) {
            case BLANK_TARGET -> "Route target is required.";
            case SOURCE_NOT_FOUND -> "Current agent cannot initiate routing.";
            case TARGET_NOT_FOUND -> "Target agent does not exist.";
            case EDGE_NOT_AUTHORIZED -> "Routing to the requested agent is not allowed.";
        };
    }

    private static String buildMessage(Reason reason, String sourceAgentId, String targetAgentId) {
        return "Route denied (" + reason + "): source=" + sourceAgentId + ", target=" + targetAgentId;
    }
}
