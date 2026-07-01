package com.jswarm.adapter.springai.auto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JswarmPropertiesTest {

    @Test
    void shouldHaveCorrectDefaults() {
        JswarmProperties props = new JswarmProperties();

        assertEquals(10, props.getMaxTurns());
        assertEquals(2, props.getMaxRecoveryAttempts());
        assertEquals(3, props.getMaxDelegateDepth());
        assertTrue(props.isDelegateStreaming());
        assertNull(props.getModelTimeout());
        assertNotNull(props.getLogging());
        assertFalse(props.getLogging().isEnabled());
    }

    @Test
    void settersShouldOverrideDefaults() {
        JswarmProperties props = new JswarmProperties();

        props.setMaxTurns(20);
        props.setMaxRecoveryAttempts(5);
        props.setMaxDelegateDepth(10);
        props.setDelegateStreaming(false);
        JswarmProperties.Logging logging = new JswarmProperties.Logging();
        logging.setEnabled(true);
        props.setLogging(logging);

        assertEquals(20, props.getMaxTurns());
        assertEquals(5, props.getMaxRecoveryAttempts());
        assertEquals(10, props.getMaxDelegateDepth());
        assertFalse(props.isDelegateStreaming());
        assertTrue(props.getLogging().isEnabled());
    }

    @Test
    void loggingPropertiesShouldWork() {
        JswarmProperties.Logging logging = new JswarmProperties.Logging();
        assertFalse(logging.isEnabled());
        logging.setEnabled(true);
        assertTrue(logging.isEnabled());
    }
}
