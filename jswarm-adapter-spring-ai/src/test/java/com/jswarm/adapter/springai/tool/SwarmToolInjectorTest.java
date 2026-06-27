package com.jswarm.adapter.springai.tool;

import com.jswarm.core.Agent;
import com.jswarm.core.Swarm;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwarmToolInjectorTest {

    @Test
    void generateToolsReturnsEmptyWhenNoTargets() {
        Swarm swarm = Swarm.create("swarm")
                .agent(createAgent("agent-a", "Agent A", "First agent"))
                .entry("agent-a")
                .build();

        List<ToolCallback> tools = SwarmToolInjector.generateTools(swarm, "agent-a", List.of());

        assertTrue(tools.isEmpty());
    }

    @Test
    void generateToolsGeneratesHandoffTool() {
        Swarm swarm = Swarm.create("swarm")
                .agent(createAgent("agent-a", "Agent A", "First agent"))
                .agent(createAgent("agent-b", "Agent B", "Second agent"))
                .entry("agent-a")
                .handoff("agent-a", "agent-b")
                .build();

        List<ToolCallback> tools = SwarmToolInjector.generateTools(swarm, "agent-a", List.of());

        assertEquals(1, tools.size());
        ToolCallback callback = tools.get(0);
        assertEquals("handoff", callback.getToolDefinition().name());
        assertTrue(callback.getToolDefinition().description().contains("agent-b"));
        assertTrue(callback.getToolDefinition().description().contains("Second agent"));
        assertThrows(UnsupportedOperationException.class, () -> callback.call("{}"));
    }

    @Test
    void generateToolsGeneratesDelegateTool() {
        Swarm swarm = Swarm.create("swarm")
                .agent(createAgent("agent-a", "Agent A", "First agent"))
                .agent(createAgent("agent-b", "Agent B", "Second agent"))
                .entry("agent-a")
                .delegate("agent-a", "agent-b")
                .build();

        List<ToolCallback> tools = SwarmToolInjector.generateTools(swarm, "agent-a", List.of());

        assertEquals(1, tools.size());
        ToolCallback callback = tools.get(0);
        assertEquals("delegate", callback.getToolDefinition().name());
        assertThrows(UnsupportedOperationException.class, () -> callback.call("{}"));
    }

    @Test
    void generateToolsMergesMultipleTargetsIntoOneTool() {
        Swarm swarm = Swarm.create("swarm")
                .agent(createAgent("agent-a", "Agent A", "First agent"))
                .agent(createAgent("agent-b", "Agent B", "Second agent"))
                .agent(createAgent("agent-c", "Agent C", "Third agent"))
                .entry("agent-a")
                .handoff("agent-a", "agent-b", "agent-c")
                .build();

        List<ToolCallback> tools = SwarmToolInjector.generateTools(swarm, "agent-a", List.of());

        assertEquals(1, tools.size());
        String desc = tools.get(0).getToolDefinition().description();
        assertTrue(desc.contains("agent-b"));
        assertTrue(desc.contains("agent-c"));
    }

    @Test
    void generateExternalToolsOnlyReturnsJustAgentTools() {
        ToolCallback callback = createToolCallback("myTool", "desc");
        List<ToolCallback> userTools = List.of(callback);

        List<ToolCallback> tools = SwarmToolInjector.generateExternalToolsOnly(userTools);

        assertEquals(1, tools.size());
        assertEquals("myTool", tools.get(0).getToolDefinition().name());
    }

    private Agent createAgent(String id, String name, String description) {
        return new Agent() {
            @Override public String id() { return id; }
            @Override public String name() { return name; }
            @Override public String description() { return description; }
        };
    }

    private ToolCallback createToolCallback(String name, String description) {
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description(description).inputSchema("{}").build();
            }
            @Override public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }
            @Override public String call(String input) { return "ok"; }
        };
    }
}
