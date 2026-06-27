package com.jswarm.adapter.springai;

import com.jswarm.adapter.springai.filter.SwarmFilter;
import com.jswarm.adapter.springai.run.SwarmRunOptions;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JAgentFactoryTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    static class AnalystTools {
        @Tool(name = "analyzeData")
        public String analyzeData() {
            return "analysis:ok";
        }
    }

    @Test
    void fromToolsShouldExposeExternalTools() {
        JAgent agent = JAgent.fromTools("analyst", "分析师", "数据分析",
                "你是分析师", stubModel("done"), new AnalystTools());
        assertEquals(1, agent.externalTools().size());
        assertEquals("analyzeData", agent.externalTools().get(0).getToolDefinition().name());
        assertNotNull(agent.toolExecutor());
    }

    @Test
    void fromAiServiceShouldThrowUnsupportedOperation() {
        assertThrows(UnsupportedOperationException.class, () ->
                JAgent.fromAiService("a", "A", "d", Object.class, stubModel("x")));
    }

    @Test
    void builderWithoutToolsShouldHaveEmptyExternalTools() {
        JAgent agent = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(stubModel("done"))
                .build();
        assertTrue(agent.externalTools().isEmpty());
    }

    @Test
    void builderShouldRejectNullModel() {
        assertThrows(IllegalArgumentException.class, () ->
                JAgent.builder("a", "A").description("d").instructions("inst").build());
    }

    @Test
    void builderShouldRejectNullDescription() {
        assertThrows(IllegalArgumentException.class, () ->
                JAgent.builder("a", "A").instructions("inst").model(stubModel("x")).build());
    }

    @Test
    void builderShouldRejectNullInstructions() {
        assertThrows(IllegalArgumentException.class, () ->
                JAgent.builder("a", "A").description("d").model(stubModel("x")).build());
    }

    @Test
    void builderToolsShouldMatchFromTools() {
        AnalystTools tools = new AnalystTools();
        JAgent fromTools = JAgent.fromTools("a", "A", "d", "inst", stubModel("x"), tools);
        JAgent fromBuilder = JAgent.builder("a", "A")
                .description("d")
                .instructions("inst")
                .model(stubModel("x"))
                .tools(tools)
                .build();
        assertEquals(fromTools.externalTools().size(), fromBuilder.externalTools().size());
        assertEquals(fromTools.externalTools().get(0).getToolDefinition().name(),
                fromBuilder.externalTools().get(0).getToolDefinition().name());
    }

    @Test
    void decorateShouldForwardAttributes() {
        JAgent base = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(stubModel("done"))
                .build();
        JAgent decorated = JAgent.decorate(base)
                .onEnter(ctx -> ctx.put("key", "val"))
                .build();
        assertEquals("a", decorated.id());
        assertEquals("A", decorated.name());
        assertEquals("desc", decorated.description());
        assertEquals("hi", decorated.instructions());
    }

    @Test
    void dynamicInstructionsShouldUseProviderWhenContextPresent() {
        SwarmContext.set(new SwarmContext());
        JAgent agent = JAgent.builder("a", "A")
                .description("desc")
                .instructions(ctx -> "dynamic " + ctx.get("key"))
                .model(stubModel("done"))
                .build();
        SwarmContext.current().put("key", "val");
        assertEquals("dynamic val", agent.instructions());
    }

    @Test
    void dynamicInstructionsShouldFallbackToStaticWhenNoContext() {
        JAgent agent = JAgent.builder("a", "A")
                .description("desc")
                .instructions(ctx -> "dynamic")
                .model(stubModel("done"))
                .build();
        assertThrows(IllegalStateException.class, () -> agent.instructions());
    }

    private static ChatModel stubModel(String text) {
        return prompt -> {
            AssistantMessage msg = new AssistantMessage(text);
            return ChatResponse.builder()
                    .generations(List.of(new Generation(msg)))
                    .build();
        };
    }
}
