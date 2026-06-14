// ChatMessage 与 JSON 互转，供 SQLite 持久化
package com.jswarm.examples.showcase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShowcaseChatMessageCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShowcaseChatMessageCodec() {
    }

    public static List<ChatMessage> forPersistence(List<ChatMessage> history) {
        List<ChatMessage> out = new ArrayList<>();
        for (ChatMessage message : history) {
            if (message instanceof ToolExecutionResultMessage) {
                continue;
            }
            if (message instanceof AiMessage am) {
                if (am.hasToolExecutionRequests()) {
                    continue;
                }
                if (am.text() == null || am.text().isBlank()) {
                    continue;
                }
            }
            out.add(message);
        }
        return out;
    }

    public static String encode(List<ChatMessage> history) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        for (ChatMessage message : forPersistence(history)) {
            Map<String, String> row = new LinkedHashMap<>();
            if (message instanceof SystemMessage sm) {
                row.put("role", "system");
                row.put("text", sm.text());
            } else if (message instanceof UserMessage um) {
                row.put("role", "user");
                row.put("text", um.singleText());
            } else if (message instanceof AiMessage am) {
                row.put("role", "ai");
                row.put("text", am.text());
            } else {
                row.put("role", "unknown");
                row.put("text", message.toString());
            }
            rows.add(row);
        }
        return MAPPER.writeValueAsString(rows);
    }

    public static List<ChatMessage> decode(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        List<Map<String, String>> rows = MAPPER.readValue(json, new TypeReference<>() {
        });
        List<ChatMessage> history = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String role = row.getOrDefault("role", "");
            String text = row.getOrDefault("text", "");
            switch (role) {
                case "system" -> history.add(SystemMessage.from(text));
                case "user" -> history.add(UserMessage.from(text));
                case "ai" -> {
                    if (text != null && !text.isBlank()) {
                        history.add(AiMessage.from(text));
                    }
                }
                case "tool" -> {
                }
                default -> {
                    if (text != null && !text.isBlank()) {
                        history.add(UserMessage.from(text));
                    }
                }
            }
        }
        return history;
    }
}
