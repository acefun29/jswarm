package com.jswarm.adapter.springai.filter;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.adapter.springai.JAgent;
import com.jswarm.adapter.springai.run.SwarmRunOptions;
import com.jswarm.core.Agent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.error.SwarmErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SwarmFilterTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void executeDelegateShouldResolveContextPlaceholders() {
        AtomicReference<String> captured = new AtomicReference<>();
        ChatModel model = capturingModel(captured);

        JAgent sub = agent("sub", "子Agent：用户 {user_name}", model);
        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "main", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();
        SwarmContext.set(new SwarmContext().put("user_name", "张三"));

        SwarmFilter filter = new SwarmFilter(swarm);
        filter.executeDelegate("main", "sub", "task", null, SwarmRunOptions.defaults());

        assertEquals("子Agent：用户 张三", captured.get());
    }

    @Test
    void executeDelegateShouldThrowForNonJAgent() {
        Agent plain = new Agent() {
            @Override public String id() { return "plain"; }
            @Override public String name() { return "Plain"; }
            @Override public String description() { return "plain agent"; }
        };
        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "main", stubModel("done")))
                .agent(plain)
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
    void delegateShouldCallExternalToolOnceAndReturn() {
        ChatModel model = sequenceModel(
                toolMsg("lookup", "{}"),
                textMsg("processed"));
        JAgent sub = agent("sub", "hi", model);

        Swarm swarm = Swarm.create("s")
                .agent(agent("main", "main", stubModel("done")))
                .agent(sub)
                .entry("main")
                .delegate("main", "sub")
                .build();
        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);

        String result = filter.executeDelegate("main", "sub", "do it",
                (ExternalToolExecutor) (req -> "tool-ok"),
                SwarmRunOptions.builder().maxTurns(5).build());

        assertEquals("processed", result);
    }

    @Test
    void decideShouldReturnHandoffForHandoffTool() {
        Swarm swarm = Swarm.create("s")
                .agent(agent("a", "hi", stubModel("done")))
                .agent(agent("b", "hi", stubModel("done")))
                .entry("a").handoff("a", "b").build();

        AssistantMessage.ToolCall tc = mock(AssistantMessage.ToolCall.class);
        when(tc.name()).thenReturn("handoff");
        when(tc.arguments()).thenReturn("{\"target\":\"b\"}");

        SwarmFilter filter = new SwarmFilter(swarm);
        FilterDecision decision = filter.decide("a", tc);

        assertTrue(decision instanceof FilterDecision.Handoff);
        assertEquals("b", ((FilterDecision.Handoff) decision).targetAgentId());
    }

    @Test
    void decideShouldReturnDelegateForDelegateTool() {
        Swarm swarm = Swarm.create("s")
                .agent(agent("a", "hi", stubModel("done")))
                .agent(agent("b", "hi", stubModel("done")))
                .entry("a").delegate("a", "b").build();

        AssistantMessage.ToolCall tc = mock(AssistantMessage.ToolCall.class);
        when(tc.name()).thenReturn("delegate");
        when(tc.arguments()).thenReturn("{\"target\":\"b\",\"task\":\"do it\"}");

        SwarmFilter filter = new SwarmFilter(swarm);
        FilterDecision decision = filter.decide("a", tc);

        assertTrue(decision instanceof FilterDecision.Delegate);
        FilterDecision.Delegate d = (FilterDecision.Delegate) decision;
        assertEquals("b", d.targetAgentId());
        assertEquals("do it", d.task());
    }

    @Test
    void decideShouldReturnNullForRegularTool() {
        Swarm swarm = Swarm.create("s")
                .agent(agent("a", "hi", stubModel("done")))
                .entry("a").build();

        AssistantMessage.ToolCall tc = mock(AssistantMessage.ToolCall.class);
        when(tc.name()).thenReturn("normalTool");
        when(tc.arguments()).thenReturn("{}");

        SwarmFilter filter = new SwarmFilter(swarm);
        assertTrue(filter.decide("a", tc) instanceof FilterDecision.External);
    }

    private JAgent agent(String id, String instructions, ChatModel model) {
        return JAgent.builder(id, "agent-" + id)
                .description("agent " + id)
                .instructions(instructions)
                .model(model)
                .build();
    }

    private ChatModel stubModel(String text) {
        return prompt -> responseWithText(text);
    }

    private ChatModel capturingModel(AtomicReference<String> captured) {
        return prompt -> {
            SystemMessage sm = (SystemMessage) prompt.getInstructions().get(0);
            captured.set(sm.getText());
            return responseWithText("done");
        };
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
        return msg;
    }

    private AssistantMessage textMsg(String text) {
        return new AssistantMessage(text);
    }

    private ChatResponse responseWithText(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .build();
    }
}
