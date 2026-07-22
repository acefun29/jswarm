package com.jswarm.adapter.lc4j.run;

import com.jswarm.adapter.lc4j.DefaultJAgent;
import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.error.SwarmErrorException;
import com.jswarm.core.SwarmException;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SwarmLifecycleTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void entryAgentShouldTriggerOnEnter() {
        AtomicReference<String> entered = new AtomicReference<>();
        JAgent a = trackingAgent("a", "hi", entered, null, stubModel("done"));

        Swarm swarm = Swarm.create("test").agent(a).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm);
        runner.run("hi");

        assertEquals("a", entered.get());
    }

    @Test
    void normalExitShouldTriggerOnExit() {
        AtomicReference<String> exited = new AtomicReference<>();
        JAgent a = trackingAgent("a", "hi", null, exited, stubModel("done"));

        Swarm swarm = Swarm.create("test").agent(a).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm);
        runner.run("hi");

        assertEquals("a", exited.get());
    }

    @Test
    void handoffShouldTriggerOnExitAndOnEnter() {
        List<String> events = new ArrayList<>();

        JAgent a = lifecycleAgent("a", "hi",
                ctx -> events.add("enter:a"),
                ctx -> events.add("exit:a"),
                handoffToModel("b"));

        JAgent b = lifecycleAgent("b", "hi",
                ctx -> events.add("enter:b"),
                ctx -> events.add("exit:b"),
                stubModel("done"));

        Swarm swarm = Swarm.create("test")
                .agent(a).agent(b)
                .entry("a")
                .handoff("a", "b")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm);
        runner.run("hi");

        assertTrue(events.contains("enter:a"));
        assertTrue(events.contains("exit:a"));
        assertTrue(events.contains("enter:b"));
        assertTrue(events.contains("exit:b"));
    }

    @Test
    void onEnterShouldWriteToContextBeforeInstructionsRendered() {
        AtomicReference<String> resolved = new AtomicReference<>();

        JAgent a = lifecycleAgent("a", "hi {key}",
                ctx -> ctx.put("key", "from_onEnter"),
                null,
                handoffToModel("b"));

        JAgent b = lifecycleAgent("b", "target {key}",
                null, null,
                capturingModel(resolved));

        Swarm swarm = Swarm.create("test")
                .agent(a).agent(b)
                .entry("a")
                .handoff("a", "b")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm);
        runner.run("hi");

        assertEquals("target from_onEnter", resolved.get());
    }

    @Test
    void maxTurnsShouldTriggerOnExitInFinally() {
        AtomicReference<String> exited = new AtomicReference<>();
        JAgent a = lifecycleAgent("a", "hi",
                ctx -> {},
                ctx -> exited.set("a"),
                handoffToModel("a"));

        Swarm swarm = Swarm.create("test").agent(a).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm, 1);

        assertThrows(SwarmException.class, () -> runner.run("hi"));
        assertEquals("a", exited.get());
    }

    @Test
    void onExitFailureInFinallyShouldNotMaskPrimaryException() {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                return ChatResponse.builder().aiMessage(toolMsg("noop", "{}")).build();
            }
        };
        JAgent a = JAgent.builder("a", "A")
                .description("d")
                .instructions("hi")
                .model(model)
                .tools(new LoopTools())
                .onExit(ctx -> { throw new RuntimeException("hook fail"); })
                .build();

        Swarm swarm = Swarm.create("test").agent(a).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm, 1);

        SwarmErrorException primary = assertThrows(SwarmErrorException.class, () -> runner.run("hi"));
        assertEquals(SwarmErrorCode.BUDGET_EXCEEDED, primary.code());
        assertEquals(1, primary.getSuppressed().length);
        assertEquals("hook fail", primary.getSuppressed()[0].getMessage());
    }

    @Test
    void nullModelExceptionShouldTriggerOnExitInFinally() {
        AtomicReference<String> exited = new AtomicReference<>();
        JAgent a = lifecycleAgent("a", "hi",
                ctx -> {},
                ctx -> exited.set("a"),
                throwingModel(new RuntimeException("model fail")));

        Swarm swarm = Swarm.create("test").agent(a).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> runner.run("hi"));
        assertEquals("model fail", ex.getMessage());
        assertEquals("a", exited.get());
    }

    @Test
    void handoffShouldPreserveMessageHistoryAndReplaceSystemMessage() {
        AtomicReference<String> targetSystemMessage = new AtomicReference<>();

        JAgent a = lifecycleAgent("a", "agent_a_instructions",
                ctx -> {},
                ctx -> {},
                handoffToModel("b"));

        JAgent b = trackingAgent("b", "agent_b_instructions",
                null, null,
                capturingModel(targetSystemMessage));

        Swarm swarm = Swarm.create("test")
                .agent(a).agent(b)
                .entry("a")
                .handoff("a", "b")
                .build();

        SwarmRunner runner = SwarmRunner.create(swarm);
        runner.run("hello user");

        assertTrue(targetSystemMessage.get().contains("agent_b_instructions"));
    }

    @Test
    void handoffToolDescriptionShouldContainNoReturnHint() {
        Swarm swarm = Swarm.create("test")
                .agent(lifecycleAgent("a", "hi", ctx -> {}, ctx -> {}, handoffToModel("b")))
                .agent(lifecycleAgent("b", "hi", ctx -> {}, ctx -> {}, stubModel("done")))
                .entry("a")
                .handoff("a", "b")
                .build();

        List<ToolSpecification> tools =
                com.jswarm.adapter.lc4j.tool.SwarmToolInjector.generateTools(swarm, "a");
        String desc = tools.get(0).description();

        assertTrue(desc.contains("you will not receive control back"));
        assertTrue(desc.contains("Use delegate instead"));
    }

    @Test
    void delegateToolDescriptionShouldContainConstraints() {
        Swarm swarm = Swarm.create("test")
                .agent(lifecycleAgent("a", "hi", ctx -> {}, ctx -> {}, stubModel("done")))
                .agent(lifecycleAgent("b", "hi", ctx -> {}, ctx -> {}, stubModel("done")))
                .entry("a")
                .delegate("a", "b")
                .build();

        List<ToolSpecification> tools =
                com.jswarm.adapter.lc4j.tool.SwarmToolInjector.generateTools(swarm, "a");
        String desc = tools.get(0).description();

        assertTrue(desc.contains("Nested delegation is not allowed"));
        assertTrue(desc.contains("Handoff is not allowed inside a delegated task"));
    }

    private static JAgent lifecycleAgent(String id, String instructions,
                                          OnLifecycle onEnter, OnLifecycle onExit,
                                          ChatModel model) {
        return new RecordLifecycleAgent(id, id + "-name", id + " desc", instructions, model, onEnter, onExit);
    }

    private static JAgent trackingAgent(String id, String instructions,
                                         AtomicReference<String> entered,
                                         AtomicReference<String> exited,
                                         ChatModel model) {
        return lifecycleAgent(id, instructions,
                entered != null ? ctx -> entered.set(id) : null,
                exited != null ? ctx -> exited.set(id) : null,
                model);
    }

    private static ChatModel stubModel(String text) {
        return new ChatModel() {
            @Override
            public String chat(String m) {
                return text;
            }

            @Override
            public ChatResponse chat(ChatRequest req) {
                return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
            }
        };
    }

    private static ChatModel handoffToModel(String target) {
        return new ChatModel() {
            @Override
            public String chat(String m) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChatResponse chat(ChatRequest req) {
                ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("handoff")
                        .arguments("{\"target\": \"" + target + "\"}")
                        .build();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(toolCall))
                        .build();
            }
        };
    }

    private static ChatModel capturingModel(AtomicReference<String> captured) {
        return new ChatModel() {
            @Override
            public String chat(String m) {
                return "done";
            }

            @Override
            public ChatResponse chat(ChatRequest req) {
                SystemMessage sm = (SystemMessage) req.messages().get(0);
                captured.set(sm.text());
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
        };
    }

    private static ChatModel throwingModel(RuntimeException ex) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                throw ex;
            }
        };
    }

    private static int toolCallSeq;

    private static AiMessage toolMsg(String name, String args) {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("call-" + (++toolCallSeq))
                .name(name).arguments(args).build());
    }

    static class LoopTools {
        @Tool
        String noop() {
            return "ok";
        }
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

    @FunctionalInterface
    private interface OnLifecycle {
        void run(SwarmContext context);
    }
}
