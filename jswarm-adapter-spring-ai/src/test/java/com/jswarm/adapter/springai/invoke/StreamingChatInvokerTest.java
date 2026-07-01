package com.jswarm.adapter.springai.invoke;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import com.jswarm.core.SwarmException;
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

class StreamingChatInvokerTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void shouldFallbackToSyncWhenNoStreamingModel() {
        List<SwarmEvent> events = new ArrayList<>();
        JAgent agent = syncOnlyAgent("done");

        AssistantMessage result = StreamingChatInvoker.stream(
                agent, new Prompt("hi"), null, events::add);

        assertEquals("done", result.getText());
        assertEquals(1, events.size());
        assertInstanceOf(SwarmEvent.Token.class, events.get(0));
        assertEquals("done", ((SwarmEvent.Token) events.get(0)).text());
    }

    @Test
    void shouldStreamTokensFromFlux() {
        List<SwarmEvent> events = new ArrayList<>();
        JAgent agent = streamingAgent("a", Flux.just(
                chatResponseChunk("Hello"),
                chatResponseChunk(" world"),
                chatResponseChunk("!")
        ));

        AssistantMessage result = StreamingChatInvoker.stream(
                agent, new Prompt("hi"), null, events::add);

        assertNotNull(result);
        List<SwarmEvent> tokens = events.stream()
                .filter(e -> e instanceof SwarmEvent.Token).toList();
        assertEquals(3, tokens.size());
    }

    @Test
    void shouldThrowSwarmExceptionWhenStreamFails() {
        JAgent agent = streamingAgent("a",
                Flux.error(new RuntimeException("connection lost")));

        assertThrows(SwarmException.class, () ->
                StreamingChatInvoker.stream(agent, new Prompt("hi"), null, e -> {}));
    }

    private static JAgent syncOnlyAgent(String reply) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(reply))));
        doReturn(response).when(model).call(any(Prompt.class));
        JAgent agent = mock(JAgent.class);
        when(agent.id()).thenReturn("a");
        when(agent.model()).thenReturn(model);
        when(agent.streamingModel()).thenReturn(null);
        return agent;
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

    private static ChatResponse chatResponseChunk(String text) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
    }
}
