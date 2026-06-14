package com.jswarm.adapter.lc4j.run;

import com.jswarm.adapter.lc4j.DefaultJAgent;
import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SwarmRecoveryTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    private static JAgent agent(String id, String instructions, ChatModel model) {
        return JAgent.builder(id, "agent-" + id)
                .description("agent " + id)
                .instructions(instructions)
                .model(model)
                .build();
    }

    private static AiMessage toolMsg(String name, String args) {
        return AiMessage.from(ToolExecutionRequest.builder().name(name).arguments(args).build());
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

    private static ChatModel stubModel(String text) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
            }
        };
    }

    private static ChatModel throwingModel(RuntimeException e) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                throw e;
            }
        };
    }

    @Test
    void shouldRecoverFromIllegalToolArguments() {
        ChatModel model = sequenceModel(
                toolMsg("handoff", "not-valid-json"),
                AiMessage.from("all good"));

        Swarm swarm = Swarm.create("test")
                .agent(agent("a", "hi", model))
                .entry("a")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).build());

        assertEquals("all good", runner.run("hi"));
    }

    @Test
    void shouldRecoverFromUnknownTool() {
        ChatModel model = sequenceModel(
                toolMsg("unknown_tool", "{\"x\":1}"),
                AiMessage.from("handled"));

        Swarm swarm = Swarm.create("test")
                .agent(agent("a", "hi", model))
                .entry("a")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).build());

        assertEquals("handled", runner.run("hi"));
    }

    @Test
    void shouldRecoverFromDelegateFailure() {
        ChatModel mainModel = sequenceModel(
                toolMsg("delegate", "{\"target\":\"sub\",\"task\":\"do it\"}"),
                AiMessage.from("handled delegate failure"));

        JAgent main = agent("main", "main hi", mainModel);
        JAgent sub = agent("sub", "sub hi", throwingModel(new RuntimeException("sub model fail")));

        Swarm swarm = Swarm.create("test")
                .agent(main).agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).build());

        assertEquals("handled delegate failure", runner.run("hi"));
    }

    @Test
    void shouldRecoverFromToolProviderFailure() {
        ChatModel model = sequenceModel(
                toolMsg("external", "{\"x\":1}"),
                AiMessage.from("handled tool failure"));

        ExternalToolExecutor failing = req -> {
            throw new RuntimeException("tool exploded");
        };

        Swarm swarm = Swarm.create("test")
                .agent(agent("a", "hi", model))
                .entry("a")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).build(),
                failing);

        assertEquals("handled tool failure", runner.run("hi"));
    }

    @Test
    void shouldThrowWhenRecoveryExceeded() {
        ChatModel model = sequenceModel(
                toolMsg("handoff", "bad"),
                toolMsg("handoff", "also-bad"),
                AiMessage.from("never"));

        Swarm swarm = Swarm.create("test")
                .agent(agent("a", "hi", model))
                .entry("a")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(1).build());

        SwarmException ex = assertThrows(SwarmException.class, () -> runner.run("hi"));
        assertTrue(ex.getMessage().contains("Recovery attempts exceeded"));
    }

    @Test
    void shouldThrowOnModelTimeout() {
        ChatModel slowModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
        };

        Swarm swarm = Swarm.create("test")
                .agent(agent("a", "hi", slowModel))
                .entry("a")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().modelTimeout(Duration.ofMillis(100)).build());

        SwarmException ex = assertThrows(SwarmException.class, () -> runner.run("hi"));
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    void shouldTriggerOnExitWhenRecoveryExceeded() {
        AtomicReference<String> exited = new AtomicReference<>();

        ChatModel model = sequenceModel(
                toolMsg("handoff", "bad"),
                toolMsg("handoff", "also-bad"),
                AiMessage.from("never"));

        JAgent a = lifecycleAgent("a", "hi",
                ctx -> {},
                ctx -> exited.set("a"),
                model);

        Swarm swarm = Swarm.create("test")
                .agent(a)
                .entry("a")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(1).build());

        assertThrows(SwarmException.class, () -> runner.run("hi"));
        assertEquals("a", exited.get());
    }

    @Test
    void shouldTriggerOnExitOnTimeout() {
        AtomicReference<String> exited = new AtomicReference<>();

        ChatModel slowModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
        };

        JAgent a = lifecycleAgent("a", "hi",
                ctx -> {},
                ctx -> exited.set("a"),
                slowModel);

        Swarm swarm = Swarm.create("test")
                .agent(a)
                .entry("a")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().modelTimeout(Duration.ofMillis(100)).build());

        assertThrows(SwarmException.class, () -> runner.run("hi"));
        assertEquals("a", exited.get());
    }

    @Test
    void delegateFailureShouldTriggerOnDelegateExitWithNull() {
        AtomicReference<String> exitResult = new AtomicReference<>();

        ChatModel mainModel = sequenceModel(
                toolMsg("delegate", "{\"target\":\"sub\",\"task\":\"do it\"}"),
                AiMessage.from("handled"));

        JAgent main = agent("main", "main hi", mainModel);
        JAgent sub = new DelegateHookAgent("sub", "sub hi",
                throwingModel(new RuntimeException("sub fail")),
                (ctx, t) -> {},
                (ctx, t, r) -> exitResult.set(r));

        Swarm swarm = Swarm.create("test")
                .agent(main).agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).build());

        assertEquals("handled", runner.run("hi"));
        assertNull(exitResult.get());
    }

    private static JAgent lifecycleAgent(String id, String instructions,
                                          OnLifecycle onEnter, OnLifecycle onExit,
                                          ChatModel model) {
        return new RecordLifecycleAgent(id, id + "-name", id + " desc", instructions, model, onEnter, onExit);
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
            super(id, name, description, instructions, model, java.util.List.of(), null);
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

    private static class DelegateHookAgent extends DefaultJAgent {
        private final java.util.function.BiConsumer<SwarmContext, String> onDelegateEnter;
        private final com.jswarm.adapter.lc4j.DelegateExitHook onDelegateExit;

        DelegateHookAgent(String id, String instructions, ChatModel model,
                         java.util.function.BiConsumer<SwarmContext, String> onDelegateEnter,
                         com.jswarm.adapter.lc4j.DelegateExitHook onDelegateExit) {
            super(id, "agent-" + id, id + " desc", instructions, model, java.util.List.of(), null);
            this.onDelegateEnter = onDelegateEnter;
            this.onDelegateExit = onDelegateExit;
        }

        @Override
        public void onDelegateEnter(SwarmContext context, String task) {
            onDelegateEnter.accept(context, task);
        }

        @Override
        public void onDelegateExit(SwarmContext context, String task, String result) {
            onDelegateExit.accept(context, task, result);
        }
    }
}
