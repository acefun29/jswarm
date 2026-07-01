package com.jswarm.adapter.springai.run;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.jswarm.core.SwarmException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChatMessageCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChatMessageCodec() {
    }

    public static List<Message> forPersistence(List<Message> history) {
        List<Message> out = new ArrayList<>();
        for (Message message : history) {
            if (message instanceof ToolResponseMessage) {
                continue;
            }
            if (message instanceof AssistantMessage am) {
                if (am.hasToolCalls()) {
                    continue;
                }
                if (am.getText() == null || am.getText().isBlank()) {
                    continue;
                }
            }
            out.add(message);
        }
        return out;
    }

    public static String encode(List<Message> history) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (Message message : forPersistence(history)) {
            Map<String, String> row = new LinkedHashMap<>();
            if (message instanceof SystemMessage sm) {
                row.put("role", "system");
                row.put("text", sm.getText());
            } else if (message instanceof UserMessage um) {
                row.put("role", "user");
                row.put("text", um.getText());
            } else if (message instanceof AssistantMessage am) {
                row.put("role", "ai");
                row.put("text", am.getText());
            } else {
                row.put("role", "unknown");
                row.put("text", message.toString());
            }
            rows.add(row);
        }
        try {
            return MAPPER.writeValueAsString(rows);
        } catch (Exception e) {
            throw new SwarmException("Failed to encode chat messages", e);
        }
    }

    public static List<Message> decode(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, String>> rows = MAPPER.readValue(json, new TypeReference<>() {
            });
            List<Message> history = new ArrayList<>();
            for (Map<String, String> row : rows) {
                String role = row.getOrDefault("role", "");
                String text = row.get("text");
                if (text == null) text = "";
                switch (role) {
                    case "system" -> {
                        if (!text.isBlank()) {
                            history.add(new SystemMessage(text));
                        }
                    }
                    case "user" -> {
                        if (!text.isBlank()) {
                            history.add(new UserMessage(text));
                        }
                    }
                    case "ai" -> {
                        if (text != null && !text.isBlank()) {
                            history.add(new AssistantMessage(text));
                        }
                    }
                    default -> {}
                }
            }
            return history;
        } catch (Exception e) {
            throw new SwarmException("Failed to decode chat messages", e);
        }
    }
}
