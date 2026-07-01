package com.jswarm.adapter.springai.advisor;

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

class SwarmLoggingAdvisorTest {

    @Test
    void beforeShouldAddStartTimeToContext() {
        SwarmLoggingAdvisor advisor = new SwarmLoggingAdvisor();
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new org.springframework.ai.chat.prompt.Prompt("test"))
                .build();
        AdvisorChain chain = mock(AdvisorChain.class);

        ChatClientRequest result = advisor.before(request, chain);

        assertNotNull(result);
        assertNotNull(result.context().get("swarm_start_time"));
        assertTrue(result.context().get("swarm_start_time") instanceof Long);
    }

    @Test
    void afterShouldExtractElapsedAndPreserveResponse() {
        SwarmLoggingAdvisor advisor = new SwarmLoggingAdvisor();
        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .generations(java.util.List.of(new Generation(new AssistantMessage("ok"))))
                        .build())
                .context(Map.of("swarm_start_time", System.nanoTime() - 100_000_000L))
                .build();
        AdvisorChain chain = mock(AdvisorChain.class);

        ChatClientResponse result = advisor.after(response, chain);

        assertNotNull(result);
        assertEquals("ok", result.chatResponse().getResult().getOutput().getText());
    }

    @Test
    void getOrderShouldReturn100() {
        SwarmLoggingAdvisor advisor = new SwarmLoggingAdvisor();
        assertEquals(100, advisor.getOrder());
    }
}
