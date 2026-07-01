package com.jswarm.adapter.springai.advisor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SwarmMetricsAdvisorTest {

    @Test
    void shouldRecordTimerAndCounterOnAfter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SwarmMetricsAdvisor advisor = new SwarmMetricsAdvisor(registry);

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new org.springframework.ai.chat.prompt.Prompt("test"))
                .build();
        AdvisorChain chain = mock(AdvisorChain.class);

        ChatClientRequest afterBefore = advisor.before(request, chain);
        assertNotNull(afterBefore.context().get("swarm_start_time"));

        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .generations(java.util.List.of(new Generation(new AssistantMessage("ok"))))
                        .build())
                .context(Map.of("swarm_start_time", System.nanoTime() - 50_000_000L))
                .build();

        ChatClientResponse result = advisor.after(response, chain);
        assertNotNull(result);

        double count = registry.counter("jswarm.llm.call.count").count();
        assertTrue(count > 0, "counter should have been incremented");
    }

    @Test
    void getOrderShouldReturn200() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SwarmMetricsAdvisor advisor = new SwarmMetricsAdvisor(registry);
        assertEquals(200, advisor.getOrder());
    }
}
