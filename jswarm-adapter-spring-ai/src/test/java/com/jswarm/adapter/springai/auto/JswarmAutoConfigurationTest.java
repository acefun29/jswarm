package com.jswarm.adapter.springai.auto;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.adapter.springai.run.SwarmRunner;
import com.jswarm.adapter.springai.run.SwarmRunOptions;
import com.jswarm.core.Swarm;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JswarmAutoConfigurationTest {

    @Test
    void configClassShouldBeLoadable() {
        JswarmAutoConfiguration config = new JswarmAutoConfiguration();
        assertNotNull(config);
    }

    @Test
    void shouldCreateSwarmRunOptionsFromProperties() {
        JswarmProperties props = new JswarmProperties();
        props.setMaxTurns(15);
        props.setMaxRecoveryAttempts(3);
        props.setMaxDelegateDepth(5);
        props.setDelegateStreaming(false);
        props.setModelTimeout(Duration.ofSeconds(30));

        JswarmAutoConfiguration config = new JswarmAutoConfiguration();
        SwarmRunOptions options = config.swarmRunOptions(props);

        assertEquals(15, options.maxTurns());
        assertEquals(3, options.maxRecoveryAttempts());
        assertEquals(5, options.maxDelegateDepth());
        assertFalse(options.delegateStreaming());
        assertEquals(Duration.ofSeconds(30), options.modelTimeout());
    }

    @Test
    void shouldCreateSwarmRunnerFromSwarmAndOptions() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("ok"))))
                .build());

        JAgent a = JAgent.builder("a", "A")
                .description("d").instructions("i")
                .model(model)
                .build();
        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();

        SwarmRunOptions options = SwarmRunOptions.defaults();
        JswarmAutoConfiguration config = new JswarmAutoConfiguration();
        SwarmRunner runner = config.swarmRunner(swarm, options);

        assertNotNull(runner);
    }

    @Test
    void loggingAdvisorShouldBeCreatedWhenEnabled() {
        JswarmAutoConfiguration config = new JswarmAutoConfiguration();
        assertNotNull(config.swarmLoggingAdvisor());
    }
}
