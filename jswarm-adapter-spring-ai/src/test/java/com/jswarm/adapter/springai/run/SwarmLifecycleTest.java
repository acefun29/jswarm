package com.jswarm.adapter.springai.run;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SwarmLifecycleTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void entryAgentShouldTriggerOnEnter() {
        AtomicReference<String> entered = new AtomicReference<>();
        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(stubModel("done"))
                .onEnter(ctx -> entered.set("a"))
                .build();

        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        SwarmRunner.create(swarm).run("hi");

        assertEquals("a", entered.get());
    }

    @Test
    void normalExitShouldTriggerOnExit() {
        AtomicReference<String> exited = new AtomicReference<>();
        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(stubModel("done"))
                .onExit(ctx -> exited.set("a"))
                .build();

        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        SwarmRunner.create(swarm).run("hi");

        assertEquals("a", exited.get());
    }

    @Test
    void handoffShouldTriggerOnExitAndOnEnter() {
        List<String> events = new ArrayList<>();

        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("agent a instructions")
                .model(handoffToModel("b"))
                .onEnter(ctx -> events.add("enter:a"))
                .onExit(ctx -> events.add("exit:a"))
                .build();

        JAgent b = JAgent.builder("b", "B")
                .description("desc")
                .instructions("agent b instructions")
                .model(stubModel("done"))
                .onEnter(ctx -> events.add("enter:b"))
                .onExit(ctx -> events.add("exit:b"))
                .build();

        Swarm swarm = Swarm.create("s")
                .agent(a).agent(b)
                .entry("a").handoff("a", "b")
                .build();
        SwarmRunner.create(swarm).run("hi");

        assertTrue(events.contains("enter:a"));
        assertTrue(events.contains("exit:a"));
        assertTrue(events.contains("enter:b"));
        assertTrue(events.contains("exit:b"));
    }

    @Test
    void maxTurnsShouldTriggerOnExitInFinally() {
        AtomicReference<String> exited = new AtomicReference<>();
        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(handoffToModel("a"))
                .onExit(ctx -> exited.set("a"))
                .build();

        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm, 1);

        assertThrows(SwarmException.class, () -> runner.run("hi"));
        assertEquals("a", exited.get());
    }

    @Test
    void onExitFailureInFinallyShouldNotMaskPrimaryException() {
        ChatModel model = prompt -> {
            AssistantMessage msg = toolMsg("noop", "{}");
            return ChatResponse.builder()
                    .generations(List.of(new Generation(msg)))
                    .build();
        };
        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(model)
                .onExit(ctx -> { throw new RuntimeException("hook fail"); })
                .build();

        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        SwarmRunner runner = SwarmRunner.create(swarm, 1);

        SwarmException primary = assertThrows(SwarmException.class, () -> runner.run("hi"));
        assertTrue(primary.getMessage().contains("Max turns"));
        assertEquals(1, primary.getSuppressed().length);
        assertEquals("hook fail", primary.getSuppressed()[0].getMessage());
    }

    @Test
    void nullModelExceptionShouldTriggerOnExitInFinally() {
        AtomicReference<String> exited = new AtomicReference<>();
        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(prompt -> { throw new RuntimeException("model fail"); })
                .onExit(ctx -> exited.set("a"))
                .build();

        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> SwarmRunner.create(swarm).run("hi"));
        assertTrue(ex.getMessage().contains("model fail"));
        assertEquals("a", exited.get());
    }

    @Test
    void handoffShouldPreserveMessageHistoryAndReplaceSystemMessage() {
        AtomicReference<String> targetSystemMessage = new AtomicReference<>();

        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("agent_a_instructions")
                .model(handoffToModel("b"))
                .build();

        JAgent b = JAgent.builder("b", "B")
                .description("desc")
                .instructions("agent_b_instructions")
                .model(capturingModel(targetSystemMessage))
                .build();

        Swarm swarm = Swarm.create("s")
                .agent(a).agent(b)
                .entry("a").handoff("a", "b")
                .build();
        SwarmRunner.create(swarm).run("hello user");

        assertTrue(targetSystemMessage.get().contains("agent_b_instructions"));
    }

    @Test
    void onEnterShouldWriteToContextBeforeInstructionsRendered() {
        AtomicReference<String> resolved = new AtomicReference<>();

        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("agent a")
                .model(handoffToModel("b"))
                .onEnter(ctx -> ctx.put("key", "from_onEnter"))
                .build();

        JAgent b = JAgent.builder("b", "B")
                .description("desc")
                .instructions("target {key}")
                .model(capturingModel(resolved))
                .build();

        Swarm swarm = Swarm.create("s")
                .agent(a).agent(b)
                .entry("a").handoff("a", "b")
                .build();
        SwarmRunner.create(swarm).run("hi");

        assertEquals("target from_onEnter", resolved.get());
    }

    private ChatModel stubModel(String text) {
        return prompt -> ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .build();
    }

    private ChatModel handoffToModel(String target) {
        return prompt -> {
            AssistantMessage msg = toolMsg("handoff", "{\"target\":\"" + target + "\"}");
            return ChatResponse.builder()
                    .generations(List.of(new Generation(msg)))
                    .build();
        };
    }

    private ChatModel capturingModel(AtomicReference<String> captured) {
        return prompt -> {
            SystemMessage sm = (SystemMessage) prompt.getInstructions().get(0);
            captured.set(sm.getText());
            return ChatResponse.builder()
                    .generations(List.of(new Generation(new AssistantMessage("done"))))
                    .build();
        };
    }

    private AssistantMessage toolMsg(String name, String args) {
        AssistantMessage.ToolCall tc = mock(AssistantMessage.ToolCall.class);
        when(tc.id()).thenReturn("call-1");
        when(tc.name()).thenReturn(name);
        when(tc.arguments()).thenReturn(args);
        AssistantMessage msg = mock(AssistantMessage.class);
        when(msg.hasToolCalls()).thenReturn(true);
        when(msg.getToolCalls()).thenReturn(List.of(tc));
        when(msg.getText()).thenReturn("");
        return msg;
    }
}
