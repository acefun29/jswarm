// Starter 自动配置契约测试
package com.jswarm.starter.auto;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.adapter.springai.run.SwarmRunOptions;
import com.jswarm.adapter.springai.run.SwarmRunner;
import com.jswarm.core.Swarm;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JswarmAutoConfigurationTest {

    @Test
    void shouldCreateOptionsAndRunner() {
        JswarmProperties properties = new JswarmProperties();
        properties.setMaxTurns(15);
        properties.setModelTimeout(Duration.ofSeconds(30));
        JswarmAutoConfiguration configuration = new JswarmAutoConfiguration();
        SwarmRunOptions options = configuration.swarmRunOptions(properties, emptyAdvisors());
        JAgent agent = JAgent.builder("a", "A").description("d").instructions("i")
                .model(prompt -> null).build();
        SwarmRunner runner = configuration.swarmRunner(
                Swarm.create("s").agent(agent).entry("a").build(), options);

        assertEquals(15, options.maxTurns());
        assertEquals(Duration.ofSeconds(30), options.modelTimeout());
        assertNotNull(runner);
    }

    @Test
    void shouldRejectInvalidLimits() {
        JswarmProperties properties = new JswarmProperties();
        properties.setMaxTurns(0);

        assertThrows(IllegalArgumentException.class,
                () -> properties.validate());
    }

    @Test
    void loggingDefaultsAreDisabled() {
        JswarmProperties properties = new JswarmProperties();
        assertFalse(properties.getLogging().isEnabled());
        properties.getLogging().setEnabled(true);
        assertTrue(properties.getLogging().isEnabled());
    }

    private static org.springframework.beans.factory.ObjectProvider<org.springframework.ai.chat.client.advisor.api.Advisor> emptyAdvisors() {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public org.springframework.ai.chat.client.advisor.api.Advisor getObject(Object... args) { return null; }
            @Override public org.springframework.ai.chat.client.advisor.api.Advisor getIfAvailable() { return null; }
            @Override public org.springframework.ai.chat.client.advisor.api.Advisor getIfUnique() { return null; }
            @Override public java.util.stream.Stream<org.springframework.ai.chat.client.advisor.api.Advisor> stream() { return java.util.stream.Stream.empty(); }
            @Override public java.util.Iterator<org.springframework.ai.chat.client.advisor.api.Advisor> iterator() { return List.<org.springframework.ai.chat.client.advisor.api.Advisor>of().iterator(); }
        };
    }
}
