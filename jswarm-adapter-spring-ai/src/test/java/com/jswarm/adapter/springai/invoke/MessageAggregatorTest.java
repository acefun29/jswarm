package com.jswarm.adapter.springai.invoke;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageAggregatorTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void shouldAggregateMultiChunkText() {
        List<SwarmEvent> events = new ArrayList<>();
        JAgent agent = streamingAgent("a", Flux.just(
                chatChunk("Hel"),
                chatChunk("lo"),
                chatChunk(" world!")
        ));

        AssistantMessage result = StreamingChatInvoker.stream(
                agent, new Prompt("hi"), null, events::add);

        assertEquals("Hello world!", result.getText());
        List<SwarmEvent> tokens = events.stream()
                .filter(e -> e instanceof SwarmEvent.Token).toList();
        assertEquals(3, tokens.size());
        assertEquals("Hel", ((SwarmEvent.Token) tokens.get(0)).text());
        assertEquals("lo", ((SwarmEvent.Token) tokens.get(1)).text());
        assertEquals(" world!", ((SwarmEvent.Token) tokens.get(2)).text());
    }

    @Test
    void shouldPreserveToolCallsFromAggregatedResponse() {
        List<SwarmEvent> events = new ArrayList<>();
        JAgent agent = streamingAgent("a", Flux.just(
                chatChunkWithToolCall("handoff", "{\"target\":\"b\"}")
        ));

        AssistantMessage result = StreamingChatInvoker.stream(
                agent, new Prompt("hi"), null, events::add);

        assertTrue(result.hasToolCalls());
        assertEquals("handoff", result.getToolCalls().get(0).name());
        assertEquals("{\"target\":\"b\"}", result.getToolCalls().get(0).arguments());
    }

    @Test
    void shouldHandleEmptyFluxGracefully() {
        JAgent agent = streamingAgent("a", Flux.empty());

        AssistantMessage result = StreamingChatInvoker.stream(
                agent, new Prompt("hi"), null, e -> {});

        assertNotNull(result);
    }

    private static JAgent streamingAgent(String id, Flux<ChatResponse> flux) {
        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
        doReturn(flux).when(streamingModel).stream(any(Prompt.class));
        JAgent agent = mock(JAgent.class);
        when(agent.id()).thenReturn(id);
        when(agent.model()).thenReturn(mock(ChatModel.class));
        when(agent.streamingModel()).thenReturn(streamingModel);
        return agent;
    }

    private static ChatResponse chatChunk(String text) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse chatChunkWithToolCall(String toolName, String args) {
        AssistantMessage msg = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call1", "function", toolName, args)))
                .build();
        return new ChatResponse(List.of(new Generation(msg)));
    }
}
