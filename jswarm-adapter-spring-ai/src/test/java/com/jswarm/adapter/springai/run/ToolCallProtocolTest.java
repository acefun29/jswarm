package com.jswarm.adapter.springai.run;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.adapter.springai.JAgent;
import com.jswarm.adapter.springai.filter.SwarmFilter;
import com.jswarm.adapter.springai.filter.ToolCallBatchProcessor;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ToolCallProtocolTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
        toolCallSeq = 0;
    }

    @Test
    void unauthorizedHandoffShouldNotCallSourceOnExit() {
        AtomicInteger exitCount = new AtomicInteger();
        ChatModel model = sequenceModel(
                toolMsg("handoff", "{\"target\":\"b\"}"),
                textMsg("done"));

        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(model)
                .onExit(ctx -> exitCount.incrementAndGet())
                .build();
        JAgent b = agent("b", "target", stubModel("never"));

        Swarm swarm = Swarm.create("s").agent(a).agent(b).entry("a").build();
        assertEquals("done", SwarmRunner.create(swarm).run("hi"));
        assertEquals(1, exitCount.get());
    }

    @Test
    void unauthorizedDelegateShouldNotCallSourceOnExit() {
        AtomicInteger exitCount = new AtomicInteger();
        ChatModel model = sequenceModel(
                toolMsg("delegate", "{\"target\":\"sub\",\"task\":\"work\"}"),
                textMsg("done"));

        JAgent main = JAgent.builder("main", "Main")
                .description("desc")
                .instructions("hi")
                .model(model)
                .onExit(ctx -> exitCount.incrementAndGet())
                .build();
        JAgent sub = agent("sub", "sub", stubModel("never"));

        Swarm swarm = Swarm.create("s").agent(main).agent(sub).entry("main").build();
        assertEquals("done", SwarmRunner.create(swarm).run("hi"));
        assertEquals(1, exitCount.get());
    }

    @Test
    void shouldExecuteTwoOrdinaryToolsSerially() {
        List<String> order = new ArrayList<>();
        ChatModel model = sequenceModel(
                multiToolMsg(
                        toolCall("c1", "alpha", "{}"),
                        toolCall("c2", "beta", "{}")),
                textMsg("ok"));

        ExternalToolExecutor exec = req -> {
            order.add(req.name());
            return req.name() + "-result";
        };

        Swarm swarm = Swarm.create("s")
                .agent(explicitExternalAgent("a", "hi", model, exec, "alpha", "beta"))
                .entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm, SwarmRunOptions.defaults(), exec);
        SwarmRunner.RunResult result = runner.runWithHistory("hi", null, "a", new SwarmContext(), false);

        assertEquals("ok", result.reply());
        assertEquals(List.of("alpha", "beta"), order);
        assertHistoryPaired(result.updatedHistory(), "c1", "c2");
    }

    @Test
    void shouldWriteResultForEachToolWhenOneFails() {
        ChatModel model = sequenceModel(
                multiToolMsg(
                        toolCall("c1", "ok_tool", "{}"),
                        toolCall("c2", "fail_tool", "{}"),
                        toolCall("c3", "ok_tool", "{}")),
                textMsg("handled"));

        ExternalToolExecutor exec = req -> {
            if ("fail_tool".equals(req.name())) {
                throw new RuntimeException("boom");
            }
            return req.name() + "-ok";
        };

        Swarm swarm = Swarm.create("s").agent(agent("a", "hi", model)).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm, SwarmRunOptions.defaults(), exec);
        SwarmRunner.RunResult result = runner.runWithHistory("hi", null, "a", new SwarmContext(), false);

        assertEquals("handled", result.reply());
        assertHistoryPaired(result.updatedHistory(), "c1", "c2", "c3");
    }

    @Test
    void mixedRoutingBatchShouldRejectAllCalls() {
        ChatModel model = sequenceModel(
                multiToolMsg(
                        toolCall("c1", "handoff", "{\"target\":\"b\"}"),
                        toolCall("c2", "getWeather", "{\"city\":\"bj\"}")),
                textMsg("recovered"));

        Swarm swarm = Swarm.create("s")
                .agent(agent("a", "hi", model))
                .agent(agent("b", "target", stubModel("never")))
                .entry("a")
                .handoff("a", "b")
                .build();

        SwarmRunner.RunResult result = SwarmRunner.create(swarm).runWithHistory(
                "hi", null, "a", new SwarmContext(), false);

        assertEquals("recovered", result.reply());
        assertHistoryPaired(result.updatedHistory(), "c1", "c2");
        assertTrue(result.updatedHistory().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(m -> m.getResponses().stream())
                .anyMatch(r -> r.responseData().contains("only one routing")));
    }

    @Test
    void doubleHandoffInOneBatchShouldRejectAllCalls() {
        ChatModel model = sequenceModel(
                multiToolMsg(
                        toolCall("c1", "handoff", "{\"target\":\"b\"}"),
                        toolCall("c2", "handoff", "{\"target\":\"c\"}")),
                textMsg("recovered"));

        Swarm swarm = Swarm.create("s")
                .agent(agent("a", "hi", model))
                .agent(agent("b", "b", stubModel("never")))
                .agent(agent("c", "c", stubModel("never")))
                .entry("a")
                .handoff("a", "b")
                .handoff("a", "c")
                .build();

        SwarmRunner.RunResult result = SwarmRunner.create(swarm).runWithHistory(
                "hi", null, "a", new SwarmContext(), false);

        assertEquals("recovered", result.reply());
        assertHistoryPaired(result.updatedHistory(), "c1", "c2");
    }

    @Test
    void delegateSubLoopShouldExecuteMultipleTools() {
        ChatModel mainModel = sequenceModel(
                toolMsg("delegate", "{\"target\":\"sub\",\"task\":\"analyze\"}"),
                textMsg("final"));

        ChatModel subModel = sequenceModel(
                multiToolMsg(
                        toolCall("s1", "lookup", "{}"),
                        toolCall("s2", "summarize", "{}")),
                textMsg("sub-done"));

        ExternalToolExecutor exec = req -> req.name() + "-value";

        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "main", mainModel))
                .agent(agent("sub", "sub", subModel))
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm, SwarmRunOptions.defaults(), exec);
        assertEquals("final", runner.run("hi"));
    }

    @Test
    void streamingShouldCompleteAfterMultiToolBatch() {
        List<SwarmEvent> events = new ArrayList<>();
        AssistantMessage toolBatch = multiToolMsg(
                toolCall("c1", "getWeather", "{\"city\":\"bj\"}"),
                toolCall("c2", "getWeather", "{\"city\":\"sh\"}"));
        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
        doReturn(
                Flux.just(new ChatResponse(List.of(new Generation(toolBatch)))),
                Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("sunny"))))))
                .when(streamingModel).stream(any(Prompt.class));

        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("instructions")
                .model(stubModel(""))
                .streamingModel(streamingModel)
                .tools(new WeatherTools())
                .build();

        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        SwarmRunner.create(swarm).runStreaming("hi", new SwarmContext(), events::add).await();

        SwarmEvent.RunCompleted completed = events.stream()
                .filter(e -> e instanceof SwarmEvent.RunCompleted)
                .map(e -> (SwarmEvent.RunCompleted) e)
                .findFirst()
                .orElseThrow();
        assertEquals("sunny", completed.finalText());
    }

    @Test
    @SuppressWarnings("removal")
    void legacyBatchProcessorShouldRemainBehaviorCompatible() {
        Swarm swarm = Swarm.create("s")
                .agent(agent("a", "source", stubModel("unused")))
                .agent(agent("b", "target", stubModel("unused")))
                .entry("a")
                .handoff("a", "b")
                .build();
        List<Message> messages = new ArrayList<>();
        AssistantMessage assistant = toolMsg("handoff", "{\"target\":\"b\"}");

        ToolCallBatchProcessor.Outcome outcome = ToolCallBatchProcessor.process(
                new SwarmFilter(swarm), "a", messages, assistant, call -> "unused", null, null);

        assertInstanceOf(ToolCallBatchProcessor.Outcome.Handoff.class, outcome);
        assertEquals(2, messages.size());
    }

    private static void assertHistoryPaired(List<Message> history, String... callIds) {
        int assistantIdx = -1;
        List<String> resultIds = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            if (msg instanceof AssistantMessage am && am.hasToolCalls()) {
                assistantIdx = i;
            } else if (msg instanceof ToolResponseMessage tr) {
                tr.getResponses().forEach(r -> resultIds.add(r.id()));
            }
        }
        assertTrue(assistantIdx >= 0);
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i) instanceof ToolResponseMessage) {
                assertTrue(i > assistantIdx);
            }
        }
        assertEquals(List.of(callIds), resultIds);
    }

    private static AssistantMessage.ToolCall toolCall(String id, String name, String args) {
        return new AssistantMessage.ToolCall(id, "function", name, args);
    }

    private static int toolCallSeq;

    private static AssistantMessage toolMsg(String name, String args) {
        return AssistantMessage.builder()
                .toolCalls(List.of(toolCall("call-" + (++toolCallSeq), name, args)))
                .build();
    }

    private static AssistantMessage multiToolMsg(AssistantMessage.ToolCall... calls) {
        return AssistantMessage.builder().toolCalls(List.of(calls)).build();
    }

    private static AssistantMessage textMsg(String text) {
        return new AssistantMessage(text);
    }

    private static ChatModel sequenceModel(AssistantMessage... msgs) {
        return new ChatModel() {
            int idx;

            @Override
            public ChatResponse call(Prompt prompt) {
                return ChatResponse.builder()
                        .generations(List.of(new Generation(msgs[idx++])))
                        .build();
            }
        };
    }

    private static JAgent agent(String id, String instructions, ChatModel model) {
        return JAgent.builder(id, "agent-" + id)
                .description("agent " + id)
                .instructions(instructions)
                .model(model)
                .build();
    }

    private static JAgent explicitExternalAgent(
            String id, String instructions, ChatModel model,
            ExternalToolExecutor executor, String... names) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (String name : names) {
            callbacks.add(new ToolCallback() {
                    private final ToolDefinition definition = ToolDefinition.builder()
                            .name(name).description(name).inputSchema("{}").build();

                    @Override
                    public ToolDefinition getToolDefinition() {
                        return definition;
                    }

                    @Override
                    public ToolMetadata getToolMetadata() {
                        return ToolMetadata.builder().build();
                    }

                    @Override
                    public String call(String toolInput) {
                        return "unused";
                    }
                });
        }
        return new JAgent() {
            @Override public String id() { return id; }
            @Override public String name() { return "agent-" + id; }
            @Override public String description() { return "agent " + id; }
            @Override public String instructions() { return instructions; }
            @Override public ChatModel model() { return model; }
            @Override public List<ToolCallback> externalTools() { return List.copyOf(callbacks); }
            @Override public ExternalToolExecutor toolExecutor() { return executor; }
        };
    }

    private static ChatModel stubModel(String reply) {
        ChatModel model = mock(ChatModel.class);
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))))
                .when(model).call(any(Prompt.class));
        return model;
    }

    static class WeatherTools {
        @org.springframework.ai.tool.annotation.Tool(description = "get weather")
        public String getWeather(String city) {
            return "sunny in " + city;
        }
    }
}
