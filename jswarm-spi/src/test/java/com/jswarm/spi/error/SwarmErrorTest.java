package com.jswarm.spi.error;

import com.jswarm.core.RouteDeniedException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class SwarmErrorTest {

    @Test
    void publicMessageShouldNotExposeCauseStack() {
        RuntimeException cause = new RuntimeException("secret internal detail");
        SwarmError error = SwarmError.of(SwarmErrorCode.INTERNAL, "Safe summary", cause);
        assertEquals("Safe summary", error.publicMessage());
        assertFalse(error.eventPayload().containsKey("stackTrace"));
        assertNull(error.eventPayload().get("cause"));
    }

    @Test
    void mapperShouldMapRouteDenied() {
        RouteDeniedException ex = new RouteDeniedException(
                RouteDeniedException.Reason.EDGE_NOT_AUTHORIZED, "a", "b");
        SwarmError error = SwarmErrorMapper.map(ex);
        assertEquals(SwarmErrorCode.POLICY_DENIED, error.code());
        assertFalse(error.retryable());
    }

    @Test
    void mapperShouldMapTimeout() {
        SwarmError error = SwarmErrorMapper.map(new TimeoutException("timed out"));
        assertEquals(SwarmErrorCode.MODEL_TIMEOUT, error.code());
        assertTrue(error.retryable());
    }

    @Test
    void mapperShouldMapTimeoutMessage() {
        SwarmError error = SwarmErrorMapper.map(new RuntimeException("LLM call timed out after 60000ms"));
        assertEquals(SwarmErrorCode.MODEL_TIMEOUT, error.code());
    }
}
