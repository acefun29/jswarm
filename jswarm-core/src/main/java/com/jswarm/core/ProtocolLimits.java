package com.jswarm.core;

import java.nio.charset.StandardCharsets;

public final class ProtocolLimits {

    public static final int MAX_TARGET_LENGTH = 128;
    public static final int MAX_TASK_LENGTH = 4096;
    public static final int MAX_TOOL_RESULT_BYTES = 32_000;

    private ProtocolLimits() {
    }

    public static String truncateResult(String result) {
        if (result == null) {
            return "";
        }
        byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_TOOL_RESULT_BYTES) {
            return result;
        }
        int limit = MAX_TOOL_RESULT_BYTES;
        while (limit > 0 && (result.charAt(Math.min(limit, result.length() - 1)) & 0xFC00) == 0xDC00) {
            limit--;
        }
        return result.substring(0, Math.min(limit, result.length())) + " [truncated]";
    }

    public static void validateRouteTarget(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new SwarmException("Route target must not be blank");
        }
        if (targetId.length() > MAX_TARGET_LENGTH) {
            throw new SwarmException("Route target exceeds maximum length");
        }
    }

    public static void validateDelegateTask(String task) {
        if (task == null || task.isBlank()) {
            throw new SwarmException("Delegate task must not be blank");
        }
        if (task.length() > MAX_TASK_LENGTH) {
            throw new SwarmException("Delegate task exceeds maximum length");
        }
    }
}
