// 工具调用请求
package com.jswarm.spi.lifecycle;

public record ToolInvocation(
        String callId,
        String toolName,
        String arguments,
        String idempotencyKey,
        boolean confirmed) {

    public ToolInvocation(String callId, String toolName, String arguments) {
        this(callId, toolName, arguments, callId, false);
    }

    public ToolInvocation(String callId, String toolName, String arguments, String idempotencyKey) {
        this(callId, toolName, arguments, idempotencyKey, false);
    }
}
