// Canonical 工具描述
package com.jswarm.spi.message;

public record ToolDescriptor(String name, String description, String inputSchema) {

    public ToolDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        description = description != null ? description : "";
        inputSchema = inputSchema != null ? inputSchema : "{\"type\":\"object\"}";
    }
}
