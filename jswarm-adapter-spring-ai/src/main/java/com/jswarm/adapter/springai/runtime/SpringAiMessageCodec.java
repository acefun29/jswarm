// Spring AI 与 Canonical 消息互转
package com.jswarm.adapter.springai.runtime;

import com.jswarm.core.SwarmException;
import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.spi.message.MessageCodec;
import com.jswarm.spi.message.ToolCall;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SpringAiMessageCodec implements MessageCodec<Message> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public List<CanonicalMessage> decode(List<Message> providerMessages) {
        List<CanonicalMessage> messages = new ArrayList<>();
        for (Message message : providerMessages) {
            if (message instanceof SystemMessage system) {
                messages.add(CanonicalMessage.system(system.getText()));
            } else if (message instanceof UserMessage user) {
                messages.add(CanonicalMessage.user(user.getText()));
            } else if (message instanceof AssistantMessage assistant) {
                List<ToolCall> calls = assistant.getToolCalls().stream()
                        .map(this::decodeCall)
                        .toList();
                messages.add(CanonicalMessage.assistant(assistant.getText(), calls));
            } else if (message instanceof ToolResponseMessage toolResponse) {
                for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                    messages.add(CanonicalMessage.toolResult(
                            response.id(), response.name(), response.responseData()));
                }
            } else {
                throw new SwarmException("Unsupported Spring AI message: " + message.getClass().getName());
            }
        }
        return List.copyOf(messages);
    }

    @Override
    public List<Message> encode(List<CanonicalMessage> messages) {
        List<Message> encoded = new ArrayList<>();
        int index = 0;
        while (index < messages.size()) {
            CanonicalMessage message = messages.get(index);
            switch (message.role()) {
                case SYSTEM -> encoded.add(new SystemMessage(message.text()));
                case USER -> encoded.add(new UserMessage(message.text()));
                case ASSISTANT -> encoded.add(AssistantMessage.builder()
                        .content(message.text())
                        .toolCalls(message.toolCalls().stream().map(this::encodeCall).toList())
                        .build());
                case TOOL_RESULT -> {
                    List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                    while (index < messages.size()
                            && messages.get(index).role() == com.jswarm.spi.message.MessageRole.TOOL_RESULT) {
                        CanonicalMessage toolResult = messages.get(index);
                        responses.add(new ToolResponseMessage.ToolResponse(
                                toolResult.toolCallId(), toolResult.toolName(), toolResult.text()));
                        index++;
                    }
                    encoded.add(ToolResponseMessage.builder().responses(responses).build());
                    continue;
                }
            }
            index++;
        }
        return List.copyOf(encoded);
    }

    public CanonicalMessage decode(AssistantMessage message) {
        return decode(List.of(message)).get(0);
    }

    private ToolCall decodeCall(AssistantMessage.ToolCall call) {
        return new ToolCall(call.id(), call.name(), call.arguments(), parseArguments(call.arguments()));
    }

    private AssistantMessage.ToolCall encodeCall(ToolCall call) {
        return new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.arguments());
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
            root.forEachEntry((key, value) -> {
                if (value.isTextual()) {
                    values.put(key, value.textValue());
                }
            });
            return Map.copyOf(values);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
