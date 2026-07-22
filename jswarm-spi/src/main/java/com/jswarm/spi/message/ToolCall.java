// Canonical 工具调用
package com.jswarm.spi.message;

import java.util.Map;

public record ToolCall(
        String id,
        String name,
        String arguments,
        Map<String, String> parsedArguments) {

    public ToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("tool call id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool call name must not be blank");
        }
        arguments = arguments != null ? arguments : "{}";
        parsedArguments = parsedArguments != null ? Map.copyOf(parsedArguments) : Map.of();
    }

    public ToolCall(String id, String name, String arguments) {
        this(id, name, arguments, Map.of());
    }

    public String argument(String name) {
        return parsedArguments.get(name);
    }
}
