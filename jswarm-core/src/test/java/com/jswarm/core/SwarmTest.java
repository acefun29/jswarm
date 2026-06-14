package com.jswarm.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SwarmTest {

    private final Swarm swarm = Swarm.create("test-swarm")
            .entry("router")
            .agent(new TestAgent("router", "路由", "路由请求"))
            .agent(new TestAgent("tech", "技术", "技术支持"))
            .agent(new TestAgent("sales", "销售", "销售咨询"))
            .handoff("router", "tech", "sales")
            .delegate("tech", "sales")
            .build();

    @Test
    void shouldReturnCorrectId() {
        assertEquals("test-swarm", swarm.id());
    }

    @Test
    void shouldReturnEntryAgentId() {
        assertEquals("router", swarm.entryAgentId());
    }

    @Test
    void shouldReturnEntryAgent() {
        Agent entry = swarm.entryAgent();
        assertEquals("router", entry.id());
        assertEquals("路由", entry.name());
        assertEquals("路由请求", entry.description());
    }

    @Test
    void shouldGetExistingAgent() {
        Agent agent = swarm.getAgent("tech");
        assertEquals("tech", agent.id());
        assertEquals("技术", agent.name());
    }

    @Test
    void shouldThrowForNonexistentAgent() {
        assertThrows(SwarmException.class, () -> swarm.getAgent("unknown"));
    }

    @Test
    void shouldCheckContains() {
        assertTrue(swarm.contains("router"));
        assertTrue(swarm.contains("tech"));
        assertFalse(swarm.contains("unknown"));
    }

    @Test
    void shouldListAllAgents() {
        List<String> ids = swarm.listAgents().stream().map(Agent::id).toList();
        assertEquals(3, ids.size());
        assertTrue(ids.containsAll(List.of("router", "tech", "sales")));
    }

    @Test
    void shouldReturnHandoffTargets() {
        assertEquals(Set.of("tech", "sales"), swarm.getHandoffTargets("router"));
    }

    @Test
    void shouldReturnDelegateTargets() {
        assertEquals(Set.of("sales"), swarm.getDelegateTargets("tech"));
    }

    @Test
    void shouldReturnEmptySetForUnknownAgent() {
        assertTrue(swarm.getHandoffTargets("unknown").isEmpty());
        assertTrue(swarm.getDelegateTargets("unknown").isEmpty());
    }

    @Test
    void shouldReturnEmptySetForAgentWithNoRoutes() {
        assertTrue(swarm.getHandoffTargets("sales").isEmpty());
        assertTrue(swarm.getDelegateTargets("sales").isEmpty());
    }

    @Test
    void agentCollectionShouldBeUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () ->
                swarm.listAgents().clear());
    }

    @Test
    void handoffTargetsShouldBeUnmodifiable() {
        var targets = swarm.getHandoffTargets("router");
        assertThrows(UnsupportedOperationException.class, () ->
                targets.add("x"));
    }

    @Test
    void delegateTargetsShouldBeUnmodifiable() {
        var targets = swarm.getDelegateTargets("tech");
        assertThrows(UnsupportedOperationException.class, () ->
                targets.add("x"));
    }

    @Test
    void entryAgentShouldBeConsistent() {
        assertEquals(swarm.entryAgentId(), swarm.entryAgent().id());
    }

    @Test
    void defaultInstructionsShouldBeEmpty() {
        Agent agent = new TestAgent("a", "A", "desc");
        assertEquals("", agent.instructions());
    }

    @Test
    void defaultOnEnterShouldNotThrow() {
        Agent agent = new TestAgent("a", "A", "desc");
        assertDoesNotThrow(() -> agent.onEnter(new SwarmContext()));
    }

    @Test
    void defaultOnExitShouldNotThrow() {
        Agent agent = new TestAgent("a", "A", "desc");
        assertDoesNotThrow(() -> agent.onExit(new SwarmContext()));
    }

    @Test
    void testAgentShouldNotNeedLifecycleOverride() {
        Swarm swarm = Swarm.create("test")
                .agent(new TestAgent("plain", "Plain", "plain"))
                .entry("plain")
                .build();
        assertDoesNotThrow(() ->
                swarm.getAgent("plain").onEnter(new SwarmContext()));
    }

    @Test
    void defaultOnDelegateEnterShouldNotThrow() {
        Agent agent = new TestAgent("a", "A", "desc");
        assertDoesNotThrow(() -> agent.onDelegateEnter(new SwarmContext(), "task"));
    }

    @Test
    void defaultOnDelegateExitShouldNotThrow() {
        Agent agent = new TestAgent("a", "A", "desc");
        assertDoesNotThrow(() -> agent.onDelegateExit(new SwarmContext(), "task", "result"));
    }

    private record TestAgent(String id, String name, String description) implements Agent {}
}
