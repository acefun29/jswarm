// LangChain4j 与 Canonical 消息互转
package com.jswarm.adapter.lc4j.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jswarm.core.SwarmException;
import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.spi.message.MessageCodec;
import com.jswarm.spi.message.ToolCall;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Lc4jMessageCodec implements MessageCodec<ChatMessage> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public List<CanonicalMessage> decode(List<ChatMessage> providerMessages) {
        List<CanonicalMessage> messages = new ArrayList<>();
        for (ChatMessage message : providerMessages) {
            if (message instanceof SystemMessage system) {
                messages.add(CanonicalMessage.system(system.text()));
            } else if (message instanceof UserMessage user) {
                messages.add(CanonicalMessage.user(user.singleText()));
            } else if (message instanceof AiMessage assistant) {
                List<ToolCall> calls = assistant.toolExecutionRequests().stream()
                        .map(this::decodeCall)
                        .toList();
                messages.add(CanonicalMessage.assistant(assistant.text(), calls));
            } else if (message instanceof ToolExecutionResultMessage toolResult) {
                messages.add(CanonicalMessage.toolResult(
                        toolResult.id(), toolResult.toolName(), toolResult.text()));
            } else {
                throw new SwarmException("Unsupported LangChain4j message: " + message.getClass().getName());
            }
        }
        return List.copyOf(messages);
    }

    @Override
    public List<ChatMessage> encode(List<CanonicalMessage> messages) {
        List<ChatMessage> encoded = new ArrayList<>();
        for (CanonicalMessage message : messages) {
            switch (message.role()) {
                case SYSTEM -> encoded.add(SystemMessage.from(message.text()));
                case USER -> encoded.add(UserMessage.from(message.text()));
                case ASSISTANT -> encoded.add(AiMessage.from(message.text(), message.toolCalls().stream()
                        .map(this::encodeCall).toList()));
                case TOOL_RESULT -> encoded.add(ToolExecutionResultMessage.from(
                        message.toolCallId(), message.toolName(), message.text()));
            }
        }
        return List.copyOf(encoded);
    }

    public CanonicalMessage decode(AiMessage message) {
        return decode(List.of(message)).get(0);
    }

    private ToolCall decodeCall(ToolExecutionRequest request) {
        return new ToolCall(request.id(), request.name(), request.arguments(), parseArguments(request.arguments()));
    }

    private ToolExecutionRequest encodeCall(ToolCall call) {
        return ToolExecutionRequest.builder()
                .id(call.id())
                .name(call.name())
                .arguments(call.arguments())
                .build();
    }

    private Map<String, String> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = MAPPER.readTree(arguments);
            if (!root.isObject()) {
                return Map.of();
            }
            Map<String, String> values = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) {
                    values.put(entry.getKey(), entry.getValue().textValue());
                }
            });
            return Map.copyOf(values);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
