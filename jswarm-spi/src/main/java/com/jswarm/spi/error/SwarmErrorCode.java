// 统一错误码
package com.jswarm.spi.error;

public enum SwarmErrorCode {
    POLICY_DENIED(ErrorCategory.POLICY, false),
    INVALID_INPUT(ErrorCategory.PROTOCOL, false),
    MODEL_TIMEOUT(ErrorCategory.TIMEOUT, true),
    MODEL_FAILURE(ErrorCategory.MODEL, false),
    TOOL_FAILURE(ErrorCategory.TOOL, false),
    TOOL_POLICY_DENIED(ErrorCategory.POLICY, false),
    TOOL_RESULT_TOO_LARGE(ErrorCategory.TOOL, false),
    TOOL_IDEMPOTENCY_CONFLICT(ErrorCategory.TOOL, false),
    CANCELLED(ErrorCategory.CANCEL, false),
    BUDGET_EXCEEDED(ErrorCategory.BUDGET, false),
    PROTOCOL_ERROR(ErrorCategory.PROTOCOL, false),
    ILLEGAL_STATE(ErrorCategory.INTERNAL, false),
    INTERNAL(ErrorCategory.INTERNAL, false);

    private final ErrorCategory category;
    private final boolean retryable;

    SwarmErrorCode(ErrorCategory category, boolean retryable) {
        this.category = category;
        this.retryable = retryable;
    }

    public ErrorCategory category() {
        return category;
    }

    public boolean retryable() {
        return retryable;
    }
}
