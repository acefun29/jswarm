package com.jswarm.adapter.lc4j.filter;

import com.jswarm.adapter.lc4j.DefaultJAgent;
import com.jswarm.adapter.lc4j.DelegateExitHook;
import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.adapter.lc4j.run.SwarmRunOptions;
import com.jswarm.core.Agent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.error.SwarmErrorException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SwarmFilterTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    private static JAgent makeAgent(String id, String instructions, ChatModel model) {
        return JAgent.builder(id, "agent-" + id)
                .description("agent " + id)
                .instructions(instructions)
                .model(model)
                .build();
    }

    @Test
    void executeDelegateShouldResolveContextPlaceholders() {
        AtomicReference<String> capturedSystemMessage = new AtomicReference<>();
        ChatModel model = capturingModel(capturedSystemMessage);

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "主Agent", model))
                .agent(makeAgent("sub", "子Agent：用户 {user_name}，状态 {status}", model))
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext ctx = new SwarmContext();
        ctx.put("user_name", "张三");
        ctx.put("status", "VIP");
        SwarmContext.set(ctx);

        SwarmFilter filter = new SwarmFilter(swarm);
        filter.executeDelegate("main", "sub", "查询订单", null, SwarmRunOptions.defaults());

        assertEquals("子Agent：用户 张三，状态 VIP", capturedSystemMessage.get());
    }

    @Test
    void executeDelegateShouldWorkWithoutContextPlaceholders() {
        AtomicReference<String> capturedSystemMessage = new AtomicReference<>();
        ChatModel model = capturingModel(capturedSystemMessage);

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "主Agent", model))
                .agent(makeAgent("sub", "子Agent无占位符指令", model))
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());

        SwarmFilter filter = new SwarmFilter(swarm);
        filter.executeDelegate("main", "sub", "查询订单", null, SwarmRunOptions.defaults());

        assertEquals("子Agent无占位符指令", capturedSystemMessage.get());
    }

    @Test
    void executeDelegateShouldNotThrowWhenNoContextSet() {
        AtomicReference<String> capturedSystemMessage = new AtomicReference<>();
        ChatModel model = capturingModel(capturedSystemMessage);

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "主Agent", model))
                .agent(makeAgent("sub", "子Agent：用户 {user_name}", model))
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmFilter filter = new SwarmFilter(swarm);
        assertDoesNotThrow(() -> filter.executeDelegate("main", "sub", "查询订单", null, SwarmRunOptions.defaults()));
        assertEquals("子Agent：用户 {user_name}", capturedSystemMessage.get());
    }

    @Test
    void executeDelegateShouldThrowForNonJAgent() {
        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "主Agent", stubModel("done")))
                .agent(new TestAgent("plain", "Plain", "plain agent"))
                .entry("main")
                .delegate("main", "plain")
                .build();

        SwarmContext.set(new SwarmContext());

        SwarmFilter filter = new SwarmFilter(swarm);
        SwarmErrorException ex = assertThrows(SwarmErrorException.class, () ->
                filter.executeDelegate("main", "plain", "task", null, SwarmRunOptions.defaults()));
        assertEquals(SwarmErrorCode.INVALID_INPUT, ex.code());
        assertTrue(ex.getMessage().contains("capability"));
    }

    @Test
    void delegateShouldTriggerOnDelegateEnter() {
        AtomicReference<String> taskRef = new AtomicReference<>();
        JAgent sub = new DelegateHookAgent("sub", "sub desc", "hi", stubModel("done"),
                (ctx, t) -> taskRef.set(t),
                (ctx, t, r) -> {});

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        filter.executeDelegate("main", "sub", "查询订单", null, SwarmRunOptions.defaults());

        assertEquals("查询订单", taskRef.get());
    }

    @Test
    void delegateShouldTriggerOnDelegateExitWithResult() {
        AtomicReference<String> resultRef = new AtomicReference<>();
        JAgent sub = new DelegateHookAgent("sub", "sub desc", "hi", stubModel("sub-done"),
                (ctx, t) -> {},
                (ctx, t, r) -> resultRef.set(r));

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        filter.executeDelegate("main", "sub", "查询订单", null, SwarmRunOptions.defaults());

        assertEquals("sub-done", resultRef.get());
    }

    @Test
    void delegateEnterShouldWriteToContextBeforeInstructionsRendered() {
        AtomicReference<String> resolved = new AtomicReference<>();
        JAgent sub = new DelegateHookAgent("sub", "sub desc", "target {key}", capturingModel(resolved),
                (ctx, t) -> ctx.put("key", "from_delegate_enter"),
                (ctx, t, r) -> {});

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        filter.executeDelegate("main", "sub", "查询订单", null, SwarmRunOptions.defaults());

        assertEquals("target from_delegate_enter", resolved.get());
    }

    @Test
    void delegateModelExceptionShouldTriggerOnDelegateExitWithNull() {
        AtomicReference<String> resultRef = new AtomicReference<>();
        JAgent sub = new DelegateHookAgent("sub", "sub desc", "hi", throwingModel(new RuntimeException("model fail")),
                (ctx, t) -> {},
                (ctx, t, r) -> resultRef.set(r));

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        try {
            filter.executeDelegate("main", "sub", "查询订单", null, SwarmRunOptions.defaults());
        } catch (RuntimeException e) {
            assertEquals("model fail", e.getMessage());
        }

        assertNull(resultRef.get());
    }

    @Test
    void delegateShouldCallExternalToolOnceAndReturn() {
        ChatModel model = sequenceModel(
                toolMsg("lookup", "{\"x\":1}"),
                AiMessage.from("tool result processed"));
        JAgent sub = makeAgent("sub", "sub hi", model);

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "main hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        String result = filter.executeDelegate("main", "sub", "do it",
                (ExternalToolExecutor) (req -> "tool-ok"), SwarmRunOptions.builder().maxTurns(5).build());

        assertEquals("tool result processed", result);
    }

    @Test
    void delegateShouldCallExternalToolMultipleTimesAndReturn() {
        ChatModel model = sequenceModel(
                toolMsg("step1", "{}"),
                toolMsg("step2", "{}"),
                AiMessage.from("all steps done"));
        JAgent sub = makeAgent("sub", "sub hi", model);

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "main hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        String result = filter.executeDelegate("main", "sub", "do it",
                (ExternalToolExecutor) (req -> "step-ok"), SwarmRunOptions.builder().maxTurns(10).build());

        assertEquals("all steps done", result);
    }

    @Test
    void delegateShouldHandleToolProviderFailure() {
        ChatModel model = sequenceModel(
                toolMsg("failing_tool", "{}"),
                AiMessage.from("handled failure gracefully"));
        JAgent sub = makeAgent("sub", "sub hi", model);

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "main hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        String result = filter.executeDelegate("main", "sub", "do it",
                (ExternalToolExecutor) (req -> { throw new RuntimeException("tool exploded"); }),
                SwarmRunOptions.builder().maxTurns(5).build());

        assertEquals("handled failure gracefully", result);
    }

    @Test
    void delegateShouldHandleUnknownToolWithoutToolProvider() {
        ChatModel model = sequenceModel(
                toolMsg("ghost_tool", "{}"),
                AiMessage.from("no tools but ok"));
        JAgent sub = makeAgent("sub", "sub hi", model);

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "main hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        String result = filter.executeDelegate("main", "sub", "do it", null,
                SwarmRunOptions.builder().maxTurns(5).build());

        assertEquals("no tools but ok", result);
    }

    @Test
    void delegateShouldReturnContentWhenMaxTurnsExceeded() {
        ChatModel model = sequenceModel(
                toolMsg("work", "{}"),
                AiMessage.from("summary after warning"));
        JAgent sub = makeAgent("sub", "sub hi", model);

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "main hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        SwarmErrorException error = assertThrows(SwarmErrorException.class, () ->
                filter.executeDelegate("main", "sub", "do it",
                        (ExternalToolExecutor) (req -> "work-ok"),
                        SwarmRunOptions.builder().maxTurns(1).build()));

        assertEquals(SwarmErrorCode.BUDGET_EXCEEDED, error.code());
    }

    @Test
    void delegateShouldReturnWarningWhenExtraCallStillReturnsToolCall() {
        ChatModel model = sequenceModel(
                toolMsg("work", "{}"),
                toolMsg("work2", "{}"));
        JAgent sub = makeAgent("sub", "sub hi", model);

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "main hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        SwarmErrorException error = assertThrows(SwarmErrorException.class, () ->
                filter.executeDelegate("main", "sub", "do it",
                        (ExternalToolExecutor) (req -> "ok"),
                        SwarmRunOptions.builder().maxTurns(1).build()));

        assertEquals(SwarmErrorCode.BUDGET_EXCEEDED, error.code());
    }

    @Test
    void delegateShouldThrowOnModelTimeout() {
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
        JAgent sub = makeAgent("sub", "sub hi", slowModel);

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "main hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);

        SwarmRunOptions opts = SwarmRunOptions.builder()
                .maxTurns(5)
                .modelTimeout(java.time.Duration.ofMillis(100))
                .build();

        SwarmException ex = assertThrows(SwarmException.class, () ->
                filter.executeDelegate("main", "sub", "do it", null, opts));
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    void delegateShouldTriggerOnDelegateExitOnMaxTurnsExceeded() {
        AtomicReference<String> exitResult = new AtomicReference<>();
        ChatModel model = sequenceModel(
                toolMsg("work", "{}"),
                AiMessage.from("final summary"));

        JAgent sub = new DelegateHookAgent("sub", "sub desc", "sub hi", model,
                (ctx, t) -> {},
                (ctx, t, r) -> exitResult.set(r));

        Swarm swarm = Swarm.create("test")
                .agent(makeAgent("main", "main hi", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        assertThrows(SwarmErrorException.class, () ->
                filter.executeDelegate("main", "sub", "task",
                        (ExternalToolExecutor) (req -> "ok"),
                        SwarmRunOptions.builder().maxTurns(1).build()));

        assertNull(exitResult.get());
    }

    private static int toolCallSeq;

    private static AiMessage toolMsg(String name, String args) {
        return AiMessage.from(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                .id("call-" + (++toolCallSeq))
                .name(name).arguments(args).build());
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

    private static ChatModel throwingModel(RuntimeException e) {
        return new ChatModel() {
            @Override
            public String chat(String m) {
                throw e;
            }
            @Override
            public ChatResponse chat(ChatRequest req) {
                throw e;
            }
        };
    }

    private record TestAgent(String id, String name, String description) implements Agent {
    }

    private static class DelegateHookAgent extends DefaultJAgent {
        private final java.util.function.BiConsumer<SwarmContext, String> onDelegateEnter;
        private final DelegateExitHook onDelegateExit;

        DelegateHookAgent(String id, String description, String instructions, ChatModel model,
                         java.util.function.BiConsumer<SwarmContext, String> onDelegateEnter,
                         DelegateExitHook onDelegateExit) {
            super(id, "agent-" + id, description, instructions, model, List.of(), null);
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
