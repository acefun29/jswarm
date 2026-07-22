// 工具调用结果
package com.jswarm.spi.lifecycle;

import com.jswarm.spi.error.SwarmErrorCode;

import java.util.Map;

public record ToolResult(
        String output,
        SwarmErrorCode errorCode,
        Map<String, Object> metadata) {

    public ToolResult {
        output = output != null ? output : "";
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public ToolResult(String output) {
        this(output, null, Map.of());
    }

    public boolean successful() {
        return errorCode == null;
    }
}
