package com.jswarm.spi.bridge;

import com.jswarm.core.SwarmContext;
import com.jswarm.spi.context.ContextSnapshot;
import com.jswarm.spi.id.AgentId;
import com.jswarm.spi.id.SwarmVersion;
import com.jswarm.spi.run.RunRequest;
import com.jswarm.spi.run.RunScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwarmContextBridgeTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
        RunScope.clear();
    }

    @Test
    void shouldRestoreNestedBindings() {
        SwarmContext outer = new SwarmContext().put("level", "outer");
        SwarmContext.set(outer);

        RunRequest request = RunRequest.builder()
                .swarmVersion(SwarmVersion.of("s"))
                .startAgentId(AgentId.of("a"))
                .build();
        RunScope scope = RunScope.root(request);
        SwarmContextBridge.ScopeBinding binding = SwarmContextBridge.bind(scope);
        assertEquals(scope, RunScope.current());
        assertNotNull(SwarmContext.current());

        SwarmContextBridge.restore(binding);
        assertSame(outer, SwarmContext.current());
        assertNull(RunScope.current());

        SwarmContext.clear();
    }

    @Test
    void shouldBindProvidedCompatibilityContext() {
        SwarmContext context = new SwarmContext().put("value", "before");
        RunRequest request = RunRequest.builder()
                .swarmVersion(SwarmVersion.of("test"))
                .contextSnapshot(ContextSnapshot.fromMap(context.asMap()))
                .build();
        RunScope scope = RunScope.root(request);
        SwarmContextBridge.ScopeBinding binding = SwarmContextBridge.bind(scope, context);
        try {
            assertSame(context, SwarmContext.current());
            SwarmContext.current().put("value", "after");
        } finally {
            SwarmContextBridge.restore(binding);
        }
        assertEquals("after", context.get("value"));
    }
}
