package com.jswarm.adapter.springai.run;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.Swarm;
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

class SwarmRunnerStreamingTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void shouldEmitEventSequenceForSimpleReply() {
        List<SwarmEvent> events = new ArrayList<>();
        JAgent a = streamingAgent("a", "instructions", Flux.just(
                chatChunk("hello")
        ));

        Swarm swarm = Swarm.create("test").agent(a).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm);
        runner.runStreaming("hi", new SwarmContext(), events::add);

        assertEquals(5, events.size());
        assertInstanceOf(SwarmEvent.RunStarted.class, events.get(0));
        assertEquals("a", ((SwarmEvent.RunStarted) events.get(0)).entryAgentId());
        assertInstanceOf(SwarmEvent.AgentEnter.class, events.get(1));
        assertEquals("a", ((SwarmEvent.AgentEnter) events.get(1)).agentId());
        assertInstanceOf(SwarmEvent.AgentExit.class, events.get(3));
        assertInstanceOf(SwarmEvent.RunCompleted.class, events.get(4));
    }

    @Test
    void shouldEmitHandoffEvents() {
        List<SwarmEvent> events = new ArrayList<>();
        JAgent a = streamingAgent("a", "router instructions",
                handoffFlux("b"));
        JAgent b = streamingAgent("b", "target instructions", Flux.just(
                chatChunk("done")
        ));

        Swarm swarm = Swarm.create("test")
                .agent(a).agent(b)
                .entry("a").handoff("a", "b")
                .build();
        SwarmRunner runner = SwarmRunner.create(swarm);
        runner.runStreaming("help", new SwarmContext(), events::add);

        assertTrue(events.stream().anyMatch(e -> e instanceof SwarmEvent.Handoff));
        SwarmEvent.Handoff h = (SwarmEvent.Handoff) events.stream()
                .filter(e -> e instanceof SwarmEvent.Handoff).findFirst().get();
        assertEquals("a", h.from());
        assertEquals("b", h.to());
    }

    @Test
    void shouldEmitTokenEventsInOrder() {
        List<SwarmEvent> events = new ArrayList<>();
        JAgent a = streamingAgent("a", "instructions", Flux.just(
                chatChunk("one"),
                chatChunk(" two"),
                chatChunk(" three")
        ));

        Swarm swarm = Swarm.create("test").agent(a).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm);
        runner.runStreaming("hi", new SwarmContext(), events::add);

        List<SwarmEvent> tokens = events.stream()
                .filter(e -> e instanceof SwarmEvent.Token).toList();
        assertEquals(3, tokens.size());
        assertEquals("one", ((SwarmEvent.Token) tokens.get(0)).text());
        assertEquals(" two", ((SwarmEvent.Token) tokens.get(1)).text());
        assertEquals(" three", ((SwarmEvent.Token) tokens.get(2)).text());
    }

    @Test
    void streamingToolCallShouldEmitToolEvents() {
        List<SwarmEvent> events = new ArrayList<>();
        JAgent a = JAgent.builder("a", "agent-a")
                .description("test")
                .instructions("instructions")
                .model(syncFallbackModel("")) // model() must return ChatModel, streamingModel via streamingModel()
                .streamingModel(streamingToolModel(
                        "getWeather", "{\"city\":\"bj\"}",
                        Flux.just(chatChunk("sunny"))))
                .tools(new WeatherTool())
                .build();

        Swarm swarm = Swarm.create("test").agent(a).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm);
        runner.runStreaming("weather", new SwarmContext(), events::add);

        assertTrue(events.stream().anyMatch(e -> e instanceof SwarmEvent.ToolCall));
        assertTrue(events.stream().anyMatch(e -> e instanceof SwarmEvent.ToolResult));
    }

    static class WeatherTool {
        @org.springframework.ai.tool.annotation.Tool(description = "Get weather")
        public String getWeather(String city) {
            return city + ": sunny";
        }
    }

    private static JAgent streamingAgent(String id, String instructions, Flux<ChatResponse> flux) {
        StreamingChatModel sm = mock(StreamingChatModel.class);
        doReturn(flux).when(sm).stream(any(Prompt.class));
        ChatModel model = mock(ChatModel.class);
        return JAgent.builder(id, id)
                .description("test")
                .instructions(instructions)
                .model(model)
                .streamingModel(sm)
                .build();
    }

    private static ChatModel syncFallbackModel(String reply) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(reply))));
        doReturn(response).when(model).call(any(Prompt.class));
        return model;
    }

    private static StreamingChatModel streamingToolModel(
            String toolName, String toolArgs, Flux<ChatResponse> secondFlux) {
        StreamingChatModel model = mock(StreamingChatModel.class);

        AssistantMessage toolMsg = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call1", "function", toolName, toolArgs)))
                .build();
        ChatResponse toolResponse = new ChatResponse(List.of(new Generation(toolMsg)));

        doReturn(Flux.just(toolResponse), secondFlux)
                .when(model).stream(any(Prompt.class));
        return model;
    }

    private static Flux<ChatResponse> handoffFlux(String targetId) {
        AssistantMessage msg = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call1", "function", "handoff", "{\"target\":\"" + targetId + "\"}")))
                .build();
        ChatResponse response = new ChatResponse(List.of(new Generation(msg)));
        return Flux.just(response);
    }

    private static ChatResponse chatChunk(String text) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
    }
}
