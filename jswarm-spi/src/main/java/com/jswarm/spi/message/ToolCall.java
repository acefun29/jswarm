// Canonical 工具调用
package com.jswarm.spi.message;

public record ToolCall(String id, String name, String arguments) {

    public ToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("tool call id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool call name must not be blank");
        }
        arguments = arguments != null ? arguments : "{}";
    }
}
