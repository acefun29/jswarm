package com.jswarm.adapter.lc4j;

import com.jswarm.adapter.lc4j.run.SwarmRunner;
import com.jswarm.core.Agent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.error.SwarmErrorException;
import com.jswarm.core.SwarmException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SwarmContextTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    private static ChatModel stubModel(String text) {
        return new ChatModel() {
            @Override public String chat(String m) { return text; }
            @Override public ChatResponse chat(ChatMessage... msgs) {
                return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
            }
            @Override public ChatResponse chat(List<ChatMessage> msgs) {
                return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
            }
            @Override public ChatResponse chat(ChatRequest req) {
                return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
            }
        };
    }

    private static JAgent agent(String id, String instructions, ChatModel model) {
        return JAgent.builder(id, "agent-" + id)
                .description("test agent " + id)
                .instructions(instructions)
                .model(model)
                .build();
    }

    @Test
    void shouldBindContextDuringRun() {
        AtomicReference<SwarmContext> captured = new AtomicReference<>();
        ChatModel model = new ChatModel() {
            @Override public String chat(String m) { return "done"; }
            @Override public ChatResponse chat(ChatMessage... msgs) {
                captured.set(SwarmContext.current());
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
            @Override public ChatResponse chat(List<ChatMessage> msgs) {
                captured.set(SwarmContext.current());
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
            @Override public ChatResponse chat(ChatRequest req) {
                captured.set(SwarmContext.current());
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
        };

        Swarm swarm = Swarm.create("test").agent(agent("a", "hi", model)).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm);

        SwarmContext ctx = new SwarmContext();
        ctx.put("userId", "u1");
        runner.run("hi", ctx);

        assertNotNull(captured.get());
        assertEquals("u1", captured.get().get("userId"));
    }

    @Test
    void shouldUnbindContextAfterRun() {
        Swarm swarm = Swarm.create("test").agent(agent("a", "hi", stubModel("done"))).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm);

        SwarmContext ctx = new SwarmContext();
        ctx.put("x", 1);
        runner.run("hi", ctx);

        assertNull(SwarmContext.current());
    }

    @Test
    void shouldResolvePlaceholders() {
        AtomicReference<String> captured = new AtomicReference<>();
        ChatModel model = capturingModel(captured);

        Swarm swarm = Swarm.create("test")
                .agent(agent("a", "用户 {user_name} 来自 {city}", model))
                .entry("a")
                .build();
        SwarmRunner runner = SwarmRunner.create(swarm);

        SwarmContext ctx = new SwarmContext();
        ctx.put("user_name", "张三");
        ctx.put("city", "北京");
        runner.run("hi", ctx);

        assertEquals("用户 张三 来自 北京", captured.get());
    }

    @Test
    void shouldNotModifyInstructionsWithoutPlaceholders() {
        AtomicReference<String> captured = new AtomicReference<>();
        ChatModel model = capturingModel(captured);

        Swarm swarm = Swarm.create("test")
                .agent(agent("a", "普通指令无占位符", model))
                .entry("a")
                .build();
        SwarmRunner runner = SwarmRunner.create(swarm);

        SwarmContext ctx = new SwarmContext();
        ctx.put("unused", "value");
        runner.run("hi", ctx);

        assertEquals("普通指令无占位符", captured.get());
    }

    @Test
    void shouldPartialReplaceWhenNotAllKeysMatch() {
        AtomicReference<String> captured = new AtomicReference<>();
        ChatModel model = capturingModel(captured);

        Swarm swarm = Swarm.create("test")
                .agent(agent("a", "你好 {name}，{unknown} 不会被替换", model))
                .entry("a")
                .build();
        SwarmRunner runner = SwarmRunner.create(swarm);

        SwarmContext ctx = new SwarmContext();
        ctx.put("name", "Alice");
        runner.run("hi", ctx);

        assertEquals("你好 Alice，{unknown} 不会被替换", captured.get());
    }

    @Test
    void defaultRunShouldUseEmptyContext() {
        AtomicReference<SwarmContext> captured = new AtomicReference<>();
        ChatModel model = new ChatModel() {
            @Override public String chat(String m) { return "done"; }
            @Override public ChatResponse chat(ChatMessage... msgs) {
                captured.set(SwarmContext.current());
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
            @Override public ChatResponse chat(List<ChatMessage> msgs) {
                captured.set(SwarmContext.current());
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
            @Override public ChatResponse chat(ChatRequest req) {
                captured.set(SwarmContext.current());
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
        };

        Swarm swarm = Swarm.create("test").agent(agent("a", "hi", model)).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm);

        runner.run("hi");

        assertNotNull(captured.get());
        assertTrue(captured.get().asMap().isEmpty());
    }

    @Test
    void runnerShouldThrowForPlainCoreAgent() {
        Swarm swarm = Swarm.create("test")
                .agent(new TestAgent("plain", "Plain", "plain agent"))
                .entry("plain")
                .build();
        SwarmErrorException ex = assertThrows(SwarmErrorException.class, () ->
                SwarmRunner.create(swarm));
        assertEquals(SwarmErrorCode.INVALID_INPUT, ex.code());
        assertTrue(ex.getMessage().contains("capability"));
    }

    private record TestAgent(String id, String name, String description) implements Agent {}

    private static ChatModel capturingModel(AtomicReference<String> captured) {
        return new ChatModel() {
            @Override public String chat(String m) { return "done"; }
            @Override public ChatResponse chat(ChatMessage... msgs) { return capture(Arrays.asList(msgs)); }
            @Override public ChatResponse chat(List<ChatMessage> msgs) { return capture(msgs); }
            @Override public ChatResponse chat(ChatRequest req) { return capture(req.messages()); }
            private ChatResponse capture(List<ChatMessage> msgs) {
                SystemMessage sm = (SystemMessage) msgs.get(0);
                captured.set(sm.text());
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
        };
    }
}
