// Provider 无关消息
package com.jswarm.spi.message;

import java.util.List;
import java.util.Objects;

public final class CanonicalMessage {

    private final MessageRole role;
    private final String text;
    private final List<ToolCall> toolCalls;
    private final String toolCallId;
    private final String toolName;

    private CanonicalMessage(
            MessageRole role,
            String text,
            List<ToolCall> toolCalls,
            String toolCallId,
            String toolName) {
        this.role = Objects.requireNonNull(role, "role");
        this.text = text != null ? text : "";
        this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        validate();
    }

    public static CanonicalMessage system(String text) {
        return new CanonicalMessage(MessageRole.SYSTEM, text, List.of(), null, null);
    }

    public static CanonicalMessage user(String text) {
        return new CanonicalMessage(MessageRole.USER, text, List.of(), null, null);
    }

    public static CanonicalMessage assistant(String text) {
        return assistant(text, List.of());
    }

    public static CanonicalMessage assistant(String text, List<ToolCall> toolCalls) {
        return new CanonicalMessage(MessageRole.ASSISTANT, text, toolCalls, null, null);
    }

    public static CanonicalMessage toolResult(String callId, String toolName, String text) {
        return new CanonicalMessage(MessageRole.TOOL_RESULT, text, List.of(), callId, toolName);
    }

    private void validate() {
        if (!toolCalls.isEmpty() && role != MessageRole.ASSISTANT) {
            throw new IllegalArgumentException("tool calls require assistant role");
        }
        if (role == MessageRole.TOOL_RESULT) {
            if (toolCallId == null || toolCallId.isBlank()) {
                throw new IllegalArgumentException("tool result call id must not be blank");
            }
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentException("tool result name must not be blank");
            }
        } else if (toolCallId != null || toolName != null) {
            throw new IllegalArgumentException("tool result metadata requires tool result role");
        }
    }

    public MessageRole role() {
        return role;
    }

    public String text() {
        return text;
    }

    public List<ToolCall> toolCalls() {
        return toolCalls;
    }

    public String toolCallId() {
        return toolCallId;
    }

    public String toolName() {
        return toolName;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
