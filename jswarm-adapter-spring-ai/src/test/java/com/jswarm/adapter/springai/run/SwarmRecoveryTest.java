package com.jswarm.adapter.springai.run;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SwarmRecoveryTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void shouldRecoverFromIllegalToolArguments() {
        ChatModel model = sequenceModel(
                toolMsg("handoff", "bad-json"),
                textMsg("all good"));

        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "hi", model))
                .entry("main").build();
        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).build());

        assertEquals("all good", runner.run("hi"));
    }

    @Test
    void shouldRecoverFromUnknownTool() {
        ChatModel model = sequenceModel(
                toolMsg("unknown_tool", "{}"),
                textMsg("handled"));

        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "hi", model))
                .entry("main").build();
        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).build());

        assertEquals("handled", runner.run("hi"));
    }

    @Test
    void shouldRecoverFromDelegateFailure() {
        ChatModel mainModel = sequenceModel(
                toolMsg("delegate", "{\"target\":\"sub\",\"task\":\"do\"}"),
                textMsg("handled delegate failure"));
        ChatModel subModel = prompt -> { throw new SwarmException("sub fail"); };

        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "hi", mainModel))
                .agent(agent("sub", "hi", subModel))
                .entry("main").delegate("main", "sub").build();
        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).build());

        assertEquals("handled delegate failure", runner.run("hi"));
    }

    @Test
    void shouldRecoverFromToolProviderFailure() {
        ChatModel model = sequenceModel(
                toolMsg("external", "{}"),
                textMsg("handled tool failure"));
        ExternalToolExecutor failing = req -> { throw new RuntimeException("tool exploded"); };

        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "hi", model))
                .entry("main").build();
        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).build(), failing);

        assertEquals("handled tool failure", runner.run("hi"));
    }

    @Test
    void shouldThrowWhenRecoveryExceeded() {
        ChatModel mainModel = sequenceModel(
                toolMsg("delegate", "{\"target\":\"sub\",\"task\":\"do it\"}"),
                toolMsg("delegate", "{\"target\":\"sub\",\"task\":\"do it again\"}"),
                textMsg("never"));
        ChatModel subModel = prompt -> { throw new SwarmException("sub fail"); };

        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "hi", mainModel))
                .agent(agent("sub", "hi", subModel))
                .entry("main").delegate("main", "sub").build();
        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(1).build());

        SwarmException ex = assertThrows(SwarmException.class, () -> runner.run("hi"));
        assertTrue(ex.getMessage().contains("recovery") || ex.getMessage().contains("Recovery"));
    }

    @Test
    void shouldTriggerOnExitWhenRecoveryExceeded() {
        AtomicBoolean exitCalled = new AtomicBoolean();
        ChatModel mainModel = sequenceModel(
                toolMsg("delegate", "{\"target\":\"sub\",\"task\":\"do it\"}"),
                toolMsg("delegate", "{\"target\":\"sub\",\"task\":\"do it again\"}"),
                textMsg("never"));
        ChatModel subModel = prompt -> { throw new SwarmException("sub fail"); };

        JAgent main = JAgent.builder("main", "agent-main")
                .description("agent main")
                .instructions("hi")
                .model(mainModel)
                .onExit(ctx -> exitCalled.set(true))
                .build();

        Swarm swarm = Swarm.create("s")
                .agent(main)
                .agent(agent("sub", "hi", subModel))
                .entry("main")
                .delegate("main", "sub")
                .build();
        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(1).build());

        assertThrows(SwarmException.class, () -> runner.run("hi"));
        assertTrue(exitCalled.get());
    }

    @Test
    void shouldUseExceptionProcessorForCustomRecovery() {
        ChatModel model = sequenceModel(
                toolMsg("external", "{}"),
                textMsg("handled"));
        ExternalToolExecutor failing = req -> { throw new RuntimeException("tool exploded"); };
        ToolExecutionExceptionProcessor processor = DefaultToolExecutionExceptionProcessor.builder()
                .alwaysThrow(false)
                .build();

        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "hi", model))
                .entry("main").build();
        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).exceptionProcessor(processor).build(), failing);

        String result = runner.run("hi");
        assertNotNull(result);
    }

    @Test
    void shouldUseOriginalRecoveryWhenExceptionProcessorNotConfigured() {
        ChatModel model = sequenceModel(
                toolMsg("external", "{}"),
                textMsg("handled"));
        ExternalToolExecutor failing = req -> { throw new RuntimeException("tool exploded"); };

        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "hi", model))
                .entry("main").build();
        SwarmRunner runner = SwarmRunner.create(swarm,
                SwarmRunOptions.builder().maxRecoveryAttempts(2).build(), failing);

        String result = runner.run("hi");
        assertEquals("handled", result);
    }

    private ChatModel sequenceModel(AssistantMessage... msgs) {
        return new ChatModel() {
            int idx;
            @Override
            public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                if (idx >= msgs.length) throw new SwarmException("unexpected call");
                AssistantMessage m = msgs[idx++];
                return ChatResponse.builder()
                        .generations(List.of(new Generation(m)))
                        .build();
            }
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

    private AssistantMessage textMsg(String text) {
        return new AssistantMessage(text);
    }

    private JAgent agent(String id, String instructions, ChatModel model) {
        return JAgent.builder(id, "agent-" + id)
                .description("agent " + id)
                .instructions(instructions)
                .model(model)
                .build();
    }
}
