package com.jswarm.adapter.springai.run;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageCodecTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void encodeDecodeRoundTripShouldPreserveMessages() {
        List<Message> original = new ArrayList<>();
        original.add(new SystemMessage("You are helpful."));
        original.add(new UserMessage("Hello"));
        original.add(new AssistantMessage("Hi there!"));

        String json = ChatMessageCodec.encode(original);
        List<Message> decoded = ChatMessageCodec.decode(json);

        assertEquals(3, decoded.size());
        assertInstanceOf(SystemMessage.class, decoded.get(0));
        assertEquals("You are helpful.", ((SystemMessage) decoded.get(0)).getText());
        assertInstanceOf(UserMessage.class, decoded.get(1));
        assertEquals("Hello", ((UserMessage) decoded.get(1)).getText());
        assertInstanceOf(AssistantMessage.class, decoded.get(2));
        assertEquals("Hi there!", ((AssistantMessage) decoded.get(2)).getText());
    }

    @Test
    void forPersistenceShouldFilterToolRelatedMessages() {
        List<Message> history = new ArrayList<>();
        history.add(new SystemMessage("System"));
        history.add(new UserMessage("User"));
        AssistantMessage toolCallMsg = AssistantMessage.builder()
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall("id1", "function", "testTool", "{}")))
                .build();
        history.add(toolCallMsg);
        history.add(ToolResponseMessage.builder()
                .responses(List.of(
                        new ToolResponseMessage.ToolResponse("id1", "testTool", "result")))
                .build());
        history.add(new AssistantMessage("Final answer"));

        List<Message> filtered = ChatMessageCodec.forPersistence(history);

        assertEquals(3, filtered.size());
        assertInstanceOf(SystemMessage.class, filtered.get(0));
        assertInstanceOf(UserMessage.class, filtered.get(1));
        assertInstanceOf(AssistantMessage.class, filtered.get(2));
        assertEquals("Final answer", ((AssistantMessage) filtered.get(2)).getText());
    }

    @Test
    void decodeShouldReturnEmptyListForNullOrBlank() {
        assertTrue(ChatMessageCodec.decode(null).isEmpty());
        assertTrue(ChatMessageCodec.decode("").isEmpty());
        assertTrue(ChatMessageCodec.decode("   ").isEmpty());
    }

    @Test
    void decodeShouldSkipUnknownRole() {
        String json = "[{\"role\":\"system\",\"text\":\"Sys\"},{\"role\":\"alien\",\"text\":\"Beep\"},{\"role\":\"user\",\"text\":\"Hi\"}]";
        List<Message> decoded = ChatMessageCodec.decode(json);

        assertEquals(2, decoded.size());
        assertInstanceOf(SystemMessage.class, decoded.get(0));
        assertInstanceOf(UserMessage.class, decoded.get(1));
    }

    @Test
    void encodeShouldHandleEmptyList() {
        String json = ChatMessageCodec.encode(List.of());
        assertEquals("[]", json);
    }
}
