package com.jswarm.core;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SwarmBuilderTest {

    private final Agent router = new TestAgent("router", "路由专员", "分发请求");
    private final Agent tech = new TestAgent("tech", "技术支持", "解决技术问题");
    private final Agent sales = new TestAgent("sales", "销售专员", "处理销售咨询");

    @Test
    void shouldBuildSwarmSuccessfully() {
        Swarm swarm = Swarm.create("test")
                .entry("router")
                .agent(router)
                .agent(tech)
                .agent(sales)
                .handoff("router", "tech", "sales")
                .build();

        assertEquals("test", swarm.id());
        assertEquals("router", swarm.entryAgentId());
        assertEquals(3, swarm.listAgents().size());
        assertTrue(swarm.contains("router"));
        assertTrue(swarm.contains("tech"));
        assertEquals(Set.of("tech", "sales"), swarm.getHandoffTargets("router"));
    }

    @Test
    void shouldRejectNullId() {
        assertThrows(SwarmException.class, () -> Swarm.create(null));
        assertThrows(SwarmException.class, () -> Swarm.create(""));
        assertThrows(SwarmException.class, () -> Swarm.create("   "));
    }

    @Test
    void shouldRejectNullAgent() {
        SwarmBuilder builder = Swarm.create("test").entry("a");
        assertThrows(SwarmException.class, () -> builder.agent(null));
    }

    @Test
    void shouldRejectDuplicateAgent() {
        SwarmBuilder builder = Swarm.create("test")
                .entry("a")
                .agent(new TestAgent("a", "A", ""));
        assertThrows(SwarmException.class, () -> builder.agent(new TestAgent("a", "B", "")));
    }

    @Test
    void shouldRejectEmptyAgentList() {
        assertThrows(SwarmException.class, () -> Swarm.create("test").entry("a").build());
    }

    @Test
    void shouldRejectMissingEntry() {
        assertThrows(SwarmException.class, () ->
                Swarm.create("test")
                        .agent(new TestAgent("a", "A", ""))
                        .build());
    }

    @Test
    void shouldRejectEntryAgentNotRegistered() {
        assertThrows(SwarmException.class, () ->
                Swarm.create("test")
                        .entry("nonexistent")
                        .agent(new TestAgent("a", "A", ""))
                        .build());
    }

    @Test
    void shouldRejectHandoffFromNonexistentAgent() {
        assertThrows(SwarmException.class, () ->
                Swarm.create("test")
                        .entry("a")
                        .agent(new TestAgent("a", "A", ""))
                        .handoff("missing", "a")
                        .build());
    }

    @Test
    void shouldRejectHandoffToNonexistentAgent() {
        assertThrows(SwarmException.class, () ->
                Swarm.create("test")
                        .entry("a")
                        .agent(new TestAgent("a", "A", ""))
                        .handoff("a", "missing")
                        .build());
    }

    @Test
    void shouldRejectDelegateFromNonexistentAgent() {
        assertThrows(SwarmException.class, () ->
                Swarm.create("test")
                        .entry("a")
                        .agent(new TestAgent("a", "A", ""))
                        .delegate("missing", "a")
                        .build());
    }

    @Test
    void shouldRejectDelegateToNonexistentAgent() {
        assertThrows(SwarmException.class, () ->
                Swarm.create("test")
                        .entry("a")
                        .agent(new TestAgent("a", "A", ""))
                        .delegate("a", "missing")
                        .build());
    }

    @Test
    void shouldRejectHandoffWithNoTargets() {
        SwarmBuilder builder = Swarm.create("test")
                .entry("a")
                .agent(new TestAgent("a", "A", ""));
        assertThrows(SwarmException.class, () -> builder.handoff("a"));
    }

    @Test
    void shouldRejectDelegateWithNoTargets() {
        SwarmBuilder builder = Swarm.create("test")
                .entry("a")
                .agent(new TestAgent("a", "A", ""));
        assertThrows(SwarmException.class, () -> builder.delegate("a"));
    }

    @Test
    void shouldAllowEmptyHandoffAndDelegate() {
        Swarm swarm = Swarm.create("test")
                .entry("a")
                .agent(new TestAgent("a", "A", ""))
                .build();

        assertTrue(swarm.getHandoffTargets("a").isEmpty());
        assertTrue(swarm.getDelegateTargets("a").isEmpty());
    }

    private record TestAgent(String id, String name, String description) implements Agent {}
}
