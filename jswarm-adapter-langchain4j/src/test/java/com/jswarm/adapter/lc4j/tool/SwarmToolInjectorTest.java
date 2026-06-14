package com.jswarm.adapter.lc4j.tool;
import com.jswarm.core.Agent;
import com.jswarm.core.Swarm;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwarmToolInjectorTest {

    private Agent createAgent(String id, String name, String description) {
        return new Agent() {
            @Override
            public String id() { return id; }
            @Override
            public String name() { return name; }
            @Override
            public String description() { return description; }
        };
    }

    @Test
    void generateTools_returnsEmptyList_whenNoTargets() {
        Swarm swarm = Swarm.create("test")
                .agent(createAgent("agent1", "Agent 1", "Description 1"))
                .entry("agent1")
                .build();

        List<ToolSpecification> tools = SwarmToolInjector.generateTools(swarm, "agent1");
        assertTrue(tools.isEmpty());
    }

    @Test
    void generateTools_generatesHandoffTools() {
        Swarm swarm = Swarm.create("test")
                .agent(createAgent("agent1", "Agent 1", "Description 1"))
                .agent(createAgent("agent2", "Agent 2", "Description 2"))
                .entry("agent1")
                .handoff("agent1", "agent2")
                .build();

        List<ToolSpecification> tools = SwarmToolInjector.generateTools(swarm, "agent1");
        assertEquals(1, tools.size());
        ToolSpecification tool = tools.get(0);
        assertEquals("handoff", tool.name());
        assertTrue(tool.description().contains("agent2"));
        assertTrue(tool.description().contains("Description 2"));
        assertNotNull(tool.parameters());
    }

    @Test
    void generateTools_generatesDelegateTools() {
        Swarm swarm = Swarm.create("test")
                .agent(createAgent("agent1", "Agent 1", "Description 1"))
                .agent(createAgent("agent2", "Agent 2", "Description 2"))
                .entry("agent1")
                .delegate("agent1", "agent2")
                .build();

        List<ToolSpecification> tools = SwarmToolInjector.generateTools(swarm, "agent1");
        assertEquals(1, tools.size());
        ToolSpecification tool = tools.get(0);
        assertEquals("delegate", tool.name());
        assertTrue(tool.description().contains("agent2"));
        assertTrue(tool.description().contains("Description 2"));
        assertNotNull(tool.parameters());
    }

    @Test
    void generateTools_mergesMultipleTargetsIntoSingleTool() {
        Swarm swarm = Swarm.create("test")
                .agent(createAgent("agent1", "Agent 1", "Description 1"))
                .agent(createAgent("agent2", "Agent 2", "Description 2"))
                .agent(createAgent("agent3", "Agent 3", "Description 3"))
                .entry("agent1")
                .handoff("agent1", "agent2", "agent3")
                .build();

        List<ToolSpecification> tools = SwarmToolInjector.generateTools(swarm, "agent1");
        assertEquals(1, tools.size());
        ToolSpecification tool = tools.get(0);
        assertEquals("handoff", tool.name());
        assertTrue(tool.description().contains("agent2"));
        assertTrue(tool.description().contains("agent3"));
    }
}