package com.jswarm.adapter.lc4j;

import com.jswarm.adapter.lc4j.filter.SwarmFilter;
import com.jswarm.adapter.lc4j.run.SwarmRunOptions;
import com.jswarm.adapter.lc4j.run.SwarmRunner;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JAgentExtensionTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void builderOnEnterShouldTriggerAtRunEntry() {
        AtomicReference<String> enteredId = new AtomicReference<>();
        JAgent agent = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(stubModel("done"))
                .onEnter(ctx -> enteredId.set("a"))
                .build();

        Swarm swarm = Swarm.create("test")
                .agent(agent)
                .entry("a")
                .build();

        SwarmRunner.create(swarm).run("hi");
        assertEquals("a", enteredId.get());
    }

    @Test
    void builderOnDelegateExitShouldReceiveResult() {
        AtomicReference<String> resultRef = new AtomicReference<>();
        JAgent sub = JAgent.builder("sub", "sub")
                .description("sub desc")
                .instructions("hi")
                .model(stubModel("sub-done"))
                .onDelegateExit((ctx, task, result) -> resultRef.set(result))
                .build();

        Swarm swarm = Swarm.create("test")
                .agent(JAgent.builder("main", "main")
                        .description("d")
                        .instructions("hi")
                        .model(stubModel("x"))
                        .build())
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        new SwarmFilter(swarm).executeDelegate("sub", "查询订单", null, SwarmRunOptions.defaults());
        assertEquals("sub-done", resultRef.get());
    }

    @Test
    void decorateShouldForwardModelInstructionsAndTools() {
        JAgent base = JAgent.fromTools("analyst", "分析师", "数据分析",
                "你是分析师", stubModel("done"), new AnalystTools());
        JAgent decorated = JAgent.decorate(base)
                .onEnter(ctx -> {})
                .build();

        assertEquals(base.id(), decorated.id());
        assertEquals(base.name(), decorated.name());
        assertEquals(base.description(), decorated.description());
        assertEquals(base.instructions(), decorated.instructions());
        assertSame(base.model(), decorated.model());
        assertEquals(base.externalTools().size(), decorated.externalTools().size());
        assertEquals(base.externalTools().get(0).name(), decorated.externalTools().get(0).name());
        assertSame(base.toolExecutor(), decorated.toolExecutor());
    }

    @Test
    void decorateFromAiServiceOnEnterShouldTriggerWithoutSubclass() {
        AtomicReference<String> traceId = new AtomicReference<>();
        JAgent base = JAgent.fromAiService("analyst", "分析师", "数据分析",
                AnalystAssistant.class, stubModel("done"));
        JAgent agent = JAgent.decorate(base)
                .onEnter(ctx -> {
                    ctx.put("trace_id", "trace-42");
                    traceId.set("trace-42");
                })
                .build();

        Swarm swarm = Swarm.create("test")
                .agent(agent)
                .entry("analyst")
                .build();

        SwarmRunner.create(swarm).run("hi");
        assertEquals("trace-42", traceId.get());
        assertEquals("你是分析师。用户 {user_name}", agent.instructions());
    }

    @Test
    void dynamicInstructionsShouldEvaluateAtResolveTime() {
        AtomicReference<String> captured = new AtomicReference<>();
        ChatModel model = capturingModel(captured);
        JAgent agent = JAgent.builder("a", "A")
                .description("desc")
                .instructions(ctx -> "branch-" + ctx.get("mode", String.class))
                .model(model)
                .build();

        SwarmContext ctx = new SwarmContext();
        ctx.put("mode", "vip");
        SwarmContext.set(ctx);

        Swarm swarm = Swarm.create("test")
                .agent(agent)
                .entry("a")
                .build();

        new SwarmFilter(swarm).executeDelegate("a", "task", null, SwarmRunOptions.defaults());
        assertEquals("branch-vip", captured.get());
    }

    @Test
    void decorateNullShouldFailFast() {
        assertThrows(NullPointerException.class, () -> JAgent.decorate(null));
    }

    @Test
    void subclassWithSuperBuilderShouldMatchSevenArgConstructor() {
        AtomicReference<String> taskRef = new AtomicReference<>();
        JAgent sub = new SubclassHookAgent("sub", "sub desc", "hi", stubModel("done"),
                (ctx, task) -> taskRef.set(task));

        Swarm swarm = Swarm.create("test")
                .agent(JAgent.builder("main", "main")
                        .description("d")
                        .instructions("hi")
                        .model(stubModel("x"))
                        .build())
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();

        SwarmContext.set(new SwarmContext());
        new SwarmFilter(swarm).executeDelegate("sub", "查询订单", null, SwarmRunOptions.defaults());
        assertEquals("查询订单", taskRef.get());
    }

    static class AnalystTools {
        @Tool(name = "analyzeData", value = "分析数据")
        String analyzeData() {
            return "analysis:ok";
        }
    }

    interface AnalystAssistant {
        @dev.langchain4j.service.SystemMessage("你是分析师。用户 {user_name}")
        String chat(@UserMessage String msg);
    }

    private static class SubclassHookAgent extends DefaultJAgent {
        private final java.util.function.BiConsumer<SwarmContext, String> hook;

        SubclassHookAgent(String id, String description, String instructions, ChatModel model,
                          java.util.function.BiConsumer<SwarmContext, String> hook) {
            super(JAgent.builder(id, "agent-" + id)
                    .description(description)
                    .instructions(instructions)
                    .model(model));
            this.hook = hook;
        }

        @Override
        public void onDelegateEnter(SwarmContext context, String task) {
            hook.accept(context, task);
        }
    }

    private static ChatModel stubModel(String text) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                return ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from(text)).build();
            }
        };
    }

    private static ChatModel capturingModel(AtomicReference<String> captured) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                captured.set(((SystemMessage) req.messages().get(0)).text());
                return ChatResponse.builder()
                        .aiMessage(dev.langchain4j.data.message.AiMessage.from("done"))
                        .build();
            }
        };
    }
}
