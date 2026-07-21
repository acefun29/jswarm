package com.jswarm.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouteAuthorizationTest {

    private final Swarm swarm = Swarm.create("test")
            .agent(new TestAgent("router", "Router", "route"))
            .agent(new TestAgent("tech", "Tech", "tech"))
            .agent(new TestAgent("sales", "Sales", "sales"))
            .entry("router")
            .handoff("router", "tech", "sales")
            .delegate("tech", "sales")
            .build();

    @Test
    void shouldAllowAuthorizedHandoff() {
        assertDoesNotThrow(() -> RouteAuthorization.authorizeHandoff(swarm, "router", "tech"));
    }

    @Test
    void shouldAllowAuthorizedDelegate() {
        assertDoesNotThrow(() -> RouteAuthorization.authorizeDelegate(swarm, "tech", "sales"));
    }

    @Test
    void shouldRejectHandoffWhenEdgeMissing() {
        RouteDeniedException ex = assertThrows(RouteDeniedException.class,
                () -> RouteAuthorization.authorizeHandoff(swarm, "router", "router"));
        assertEquals(RouteDeniedException.Reason.EDGE_NOT_AUTHORIZED, ex.reason());
    }

    @Test
    void shouldRejectDelegateWhenEdgeMissing() {
        RouteDeniedException ex = assertThrows(RouteDeniedException.class,
                () -> RouteAuthorization.authorizeDelegate(swarm, "sales", "tech"));
        assertEquals(RouteDeniedException.Reason.EDGE_NOT_AUTHORIZED, ex.reason());
    }

    @Test
    void shouldRejectBlankTarget() {
        RouteDeniedException ex = assertThrows(RouteDeniedException.class,
                () -> RouteAuthorization.authorizeHandoff(swarm, "router", "  "));
        assertEquals(RouteDeniedException.Reason.BLANK_TARGET, ex.reason());
    }

    @Test
    void shouldRejectUnknownSource() {
        RouteDeniedException ex = assertThrows(RouteDeniedException.class,
                () -> RouteAuthorization.authorizeHandoff(swarm, "ghost", "tech"));
        assertEquals(RouteDeniedException.Reason.SOURCE_NOT_FOUND, ex.reason());
    }

    @Test
    void shouldRejectUnknownTarget() {
        RouteDeniedException ex = assertThrows(RouteDeniedException.class,
                () -> RouteAuthorization.authorizeHandoff(swarm, "router", "ghost"));
        assertEquals(RouteDeniedException.Reason.TARGET_NOT_FOUND, ex.reason());
    }

    private record TestAgent(String id, String name, String description) implements Agent {
    }
}
