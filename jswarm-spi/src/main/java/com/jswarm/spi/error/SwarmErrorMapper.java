// 通用异常到 SwarmError 映射
package com.jswarm.spi.error;

import com.jswarm.core.RouteDeniedException;
import com.jswarm.core.SwarmException;

import java.util.concurrent.TimeoutException;

public final class SwarmErrorMapper {

    private SwarmErrorMapper() {
    }

    public static SwarmError map(Throwable throwable) {
        if (throwable instanceof SwarmErrorException see) {
            return see.error();
        }
        if (throwable instanceof RouteDeniedException rde) {
            return SwarmError.of(SwarmErrorCode.POLICY_DENIED, rde.modelSafeMessage(), throwable)
                    .withMetadata("reason", rde.reason().name());
        }
        if (throwable instanceof TimeoutException) {
            return SwarmError.of(SwarmErrorCode.MODEL_TIMEOUT, "Model call timed out", throwable);
        }
        if (throwable instanceof InterruptedException) {
            return SwarmError.of(SwarmErrorCode.CANCELLED, "Run interrupted", throwable);
        }
        if (throwable instanceof SwarmException se) {
            SwarmError mapped = mapMessage(se.getMessage());
            if (mapped != null) {
                return new SwarmError(mapped.code(), mapped.publicMessage(), mapped.metadata(), se);
            }
        }
        String message = throwable != null && throwable.getMessage() != null
                ? throwable.getMessage()
                : "Internal error";
        SwarmError mapped = mapMessage(message);
        if (mapped != null) {
            return new SwarmError(mapped.code(), mapped.publicMessage(), mapped.metadata(), throwable);
        }
        return SwarmError.of(SwarmErrorCode.INTERNAL, "An internal error occurred", throwable);
    }

    public static RuntimeException toRuntimeException(Throwable throwable) {
        if (throwable instanceof SwarmErrorException) {
            return (RuntimeException) throwable;
        }
        if (throwable instanceof TimeoutException) {
            return SwarmError.of(SwarmErrorCode.MODEL_TIMEOUT, "Model call timed out", throwable).toException();
        }
        if (throwable instanceof InterruptedException) {
            return SwarmError.of(SwarmErrorCode.CANCELLED, "Run interrupted", throwable).toException();
        }
        if (throwable instanceof RouteDeniedException rde) {
            return SwarmError.of(SwarmErrorCode.POLICY_DENIED, rde.modelSafeMessage(), throwable).toException();
        }
        if (throwable instanceof RuntimeException re) {
            return re;
        }
        return new SwarmException(throwable.getMessage(), throwable);
    }

    private static SwarmError mapMessage(String message) {
        if (message == null) {
            return null;
        }
        String lower = message.toLowerCase();
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return SwarmError.of(SwarmErrorCode.MODEL_TIMEOUT, "Model call timed out");
        }
        if (lower.contains("interrupted")) {
            return SwarmError.of(SwarmErrorCode.CANCELLED, "Run interrupted");
        }
        if (lower.contains("budget exceeded") || lower.contains("max turns")) {
            return SwarmError.of(SwarmErrorCode.BUDGET_EXCEEDED, "Run budget exceeded");
        }
        if (lower.contains("route denied") || lower.contains("not allowed") || lower.contains("not authorized")) {
            return SwarmError.of(SwarmErrorCode.POLICY_DENIED, "Operation not allowed");
        }
        if (lower.contains("protocol") || lower.contains("tool call")) {
            return SwarmError.of(SwarmErrorCode.PROTOCOL_ERROR, "Protocol error");
        }
        if (lower.contains("must not be blank") || lower.contains("invalid")) {
            return SwarmError.of(SwarmErrorCode.INVALID_INPUT, "Invalid input");
        }
        return null;
    }
}
