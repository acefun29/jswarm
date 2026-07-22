// Micrometer Advisor 契约测试
package com.jswarm.adapter.springai.advisor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SwarmMetricsAdvisorTest {

    @Test
    void shouldCreateWithMeterRegistry() {
        assertNotNull(new SwarmMetricsAdvisor(new SimpleMeterRegistry()));
    }
}
