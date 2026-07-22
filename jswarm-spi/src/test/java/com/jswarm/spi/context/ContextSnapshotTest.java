package com.jswarm.spi.context;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContextSnapshotTest {

    @Test
    void snapshotShouldBeIsolatedFromSourceMap() {
        Map<String, Object> source = new HashMap<>();
        source.put("user", "alice");
        ContextSnapshot snapshot = ContextSnapshot.fromMap(source);
        source.put("user", "bob");
        source.put("secret", "x");
        assertEquals("alice", snapshot.get("user"));
        assertFalse(snapshot.contains("secret"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.asMap().put("k", "v"));
    }

    @Test
    void delegateProjectionShouldHideSecrets() {
        ContextSnapshot parent = ContextSnapshot.builder()
                .put("name", "alice", Sensitivity.PUBLIC)
                .put("token", "secret-value", Sensitivity.SECRET)
                .put("role", "admin", Sensitivity.INTERNAL)
                .build();
        ContextSnapshot projected = ContextProjection.forDelegate(parent);
        assertEquals("alice", projected.get("name"));
        assertEquals("admin", projected.get("role"));
        assertNull(projected.get("token"));
    }

    @Test
    void resolveShouldReplacePlaceholders() {
        ContextSnapshot snapshot = ContextSnapshot.builder()
                .put("user_name", "Bob")
                .build();
        assertEquals("Hello Bob!", snapshot.resolve("Hello {user_name}!"));
    }
}
