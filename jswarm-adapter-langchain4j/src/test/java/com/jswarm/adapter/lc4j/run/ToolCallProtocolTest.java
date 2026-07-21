package com.jswarm.adapter.lc4j.run;

import com.jswarm.adapter.lc4j.DefaultJAgent;
import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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
                AiMessage.from("done"));

        JAgent a = lifecycleAgent("a", "hi", null, ctx -> exitCount.incrementAndGet(), model);
        JAgent b = agent("b", "target", stubModel("never"));

        Swarm swarm = Swarm.create("test").agent(a).agent(b).entry("a").build();
        assertEquals("done", SwarmRunner.create(swarm).run("hi"));
        assertEquals(1, exitCount.get());
    }

    @Test
    void unauthorizedDelegateShouldNotCallSourceOnExit() {
        AtomicInteger exitCount = new AtomicInteger();
        ChatModel model = sequenceModel(
                toolMsg("delegate", "{\"target\":\"sub\",\"task\":\"work\"}"),
                AiMessage.from("done"));

        JAgent main = lifecycleAgent("main", "hi", null, ctx -> exitCount.incrementAndGet(), model);
        JAgent sub = agent("sub", "sub", stubModel("never"));

        Swarm swarm = Swarm.create("test").agent(main).agent(sub).entry("main").build();
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
                AiMessage.from("ok"));

        ExternalToolExecutor exec = req -> {
            order.add(req.name());
            return req.name() + "-result";
        };

        Swarm swarm = Swarm.create("test").agent(agent("a", "hi", model)).entry("a").build();
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
                AiMessage.from("handled"));

        ExternalToolExecutor exec = req -> {
            if ("fail_tool".equals(req.name())) {
                throw new RuntimeException("boom");
            }
            return req.name() + "-ok";
        };

        Swarm swarm = Swarm.create("test").agent(agent("a", "hi", model)).entry("a").build();
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
                AiMessage.from("recovered"));

        Swarm swarm = Swarm.create("test")
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
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .anyMatch(m -> m.text().contains("only one routing")));
    }

    @Test
    void doubleHandoffInOneBatchShouldRejectAllCalls() {
        ChatModel model = sequenceModel(
                multiToolMsg(
                        toolCall("c1", "handoff", "{\"target\":\"b\"}"),
                        toolCall("c2", "handoff", "{\"target\":\"c\"}")),
                AiMessage.from("recovered"));

        Swarm swarm = Swarm.create("test")
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
                AiMessage.from("final"));

        ChatModel subModel = sequenceModel(
                multiToolMsg(
                        toolCall("s1", "lookup", "{}"),
                        toolCall("s2", "summarize", "{}")),
                AiMessage.from("sub-done"));

        ExternalToolExecutor exec = req -> req.name() + "-value";

        Swarm swarm = Swarm.create("test")
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
        JAgent a = JAgent.builder("a", "agent-a")
                .description("test")
                .instructions("instructions")
                .model(syncStub(""))
                .streamingModel(streamingSequenceModel(
                        multiToolMsg(
                                toolCall("c1", "getWeather", "{\"city\":\"bj\"}"),
                                toolCall("c2", "getWeather", "{\"city\":\"sh\"}")),
                        AiMessage.from("sunny")))
                .tools(new WeatherTools())
                .build();

        Swarm swarm = Swarm.create("test").agent(a).entry("a").build();
        SwarmRunner.create(swarm).runStreaming("hi", new SwarmContext(), events::add);

        SwarmEvent.RunCompleted completed = events.stream()
                .filter(e -> e instanceof SwarmEvent.RunCompleted)
                .map(e -> (SwarmEvent.RunCompleted) e)
                .findFirst()
                .orElseThrow();
        assertEquals("sunny", completed.finalText());
    }

    private static void assertHistoryPaired(List<ChatMessage> history, String... callIds) {
        int assistantIdx = -1;
        List<String> resultIds = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            if (msg instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                assistantIdx = i;
            } else if (msg instanceof ToolExecutionResultMessage result) {
                resultIds.add(result.id());
            }
        }
        assertTrue(assistantIdx >= 0, "assistant with tool calls should exist");
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i) instanceof ToolExecutionResultMessage) {
                assertTrue(i > assistantIdx, "tool results must follow assistant");
            }
        }
        assertEquals(callIds.length, resultIds.size());
        assertEquals(List.of(callIds), resultIds);
    }

    private static ToolExecutionRequest toolCall(String id, String name, String args) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(args).build();
    }

    private static int toolCallSeq;

    private static AiMessage toolMsg(String name, String args) {
        return AiMessage.from(toolCall("call-" + (++toolCallSeq), name, args));
    }

    private static AiMessage multiToolMsg(ToolExecutionRequest... calls) {
        return AiMessage.builder().toolExecutionRequests(List.of(calls)).build();
    }

    private static ChatModel sequenceModel(AiMessage... msgs) {
        return new ChatModel() {
            private int idx;

            @Override
            public ChatResponse chat(ChatRequest req) {
                return ChatResponse.builder().aiMessage(msgs[idx++]).build();
            }
        };
    }

    private static StreamingChatModel streamingSequenceModel(AiMessage... msgs) {
        return new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                int turn = (int) request.messages().stream()
                        .filter(m -> m instanceof AiMessage ai && ai.hasToolExecutionRequests())
                        .count();
                AiMessage msg = msgs[Math.min(turn, msgs.length - 1)];
                handler.onCompleteResponse(ChatResponse.builder().aiMessage(msg).build());
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

    private static JAgent lifecycleAgent(String id, String instructions,
                                           OnLifecycle onEnter, OnLifecycle onExit,
                                           ChatModel model) {
        return new RecordLifecycleAgent(id, id + "-name", id + " desc", instructions, model, onEnter, onExit);
    }

    private static ChatModel stubModel(String text) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
            }
        };
    }

    private static ChatModel syncStub(String text) {
        return stubModel(text);
    }

    @FunctionalInterface
    private interface OnLifecycle {
        void run(SwarmContext context);
    }

    private static class RecordLifecycleAgent extends DefaultJAgent {
        private final OnLifecycle onEnter;
        private final OnLifecycle onExit;

        RecordLifecycleAgent(String id, String name, String description,
                             String instructions, ChatModel model,
                             OnLifecycle onEnter, OnLifecycle onExit) {
            super(id, name, description, instructions, model, List.of(), null);
            this.onEnter = onEnter;
            this.onExit = onExit;
        }

        @Override
        public void onEnter(SwarmContext context) {
            if (onEnter != null) {
                onEnter.run(context);
            }
        }

        @Override
        public void onExit(SwarmContext context) {
            if (onExit != null) {
                onExit.run(context);
            }
        }
    }

    static class WeatherTools {
        @Tool("get weather")
        String getWeather(String city) {
            return "sunny in " + city;
        }
    }
}
