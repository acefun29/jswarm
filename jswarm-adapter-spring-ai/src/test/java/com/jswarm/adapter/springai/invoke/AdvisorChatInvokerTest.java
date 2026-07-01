package com.jswarm.adapter.springai.invoke;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.SwarmContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvisorChatInvokerTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void shouldDegradeToChatInvokerWhenAdvisorsNull() {
        ChatModel model = textModel("hello");
        JAgent agent = agent("a", model);
        Prompt prompt = new Prompt("hi");

        ChatResponse response = AdvisorChatInvoker.invoke(agent, prompt, null, null);
        assertEquals("hello", response.getResult().getOutput().getText());
    }

    @Test
    void shouldDegradeToChatInvokerWhenAdvisorsEmpty() {
        ChatModel model = textModel("hello");
        JAgent agent = agent("a", model);
        Prompt prompt = new Prompt("hi");

        ChatResponse response = AdvisorChatInvoker.invoke(agent, prompt, null, List.of());
        assertEquals("hello", response.getResult().getOutput().getText());
    }

    @Test
    void shouldCallAdvisorsInOrderAndTerminalCallsChatModel() {
        ChatModel model = textModel("result");
        JAgent agent = agent("a", model);

        List<String> callOrder = new ArrayList<>();
        AtomicInteger orderCheck = new AtomicInteger(0);

        BaseAdvisor advisor1 = new BaseAdvisor() {
            @Override public String getName() { return "a1"; }
            @Override
            public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
                callOrder.add("a1-before");
                return request;
            }
            @Override
            public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
                callOrder.add("a1-after");
                return response;
            }
            @Override
            public int getOrder() { return orderCheck.incrementAndGet(); }
        };

        BaseAdvisor advisor2 = new BaseAdvisor() {
            @Override public String getName() { return "a2"; }
            @Override
            public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
                callOrder.add("a2-before");
                return request;
            }
            @Override
            public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
                callOrder.add("a2-after");
                return response;
            }
            @Override
            public int getOrder() { return orderCheck.incrementAndGet(); }
        };

        Prompt prompt = new Prompt("hi");
        ChatResponse response = AdvisorChatInvoker.invoke(agent, prompt, null, List.of(advisor1, advisor2));

        assertEquals("result", response.getResult().getOutput().getText());
        assertTrue(callOrder.indexOf("a1-before") < callOrder.indexOf("a1-after"));
        assertTrue(callOrder.indexOf("a2-before") < callOrder.indexOf("a2-after"));
    }

    @Test
    void advisorShouldModifyPromptBeforeCall() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("ok"))))
                .build());
        JAgent agent = agent("a", model);

        BaseAdvisor modifier = new BaseAdvisor() {
            @Override public String getName() { return "mod"; }
            @Override
            public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
                return request.mutate()
                        .context("modified", true)
                        .build();
            }
            @Override
            public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
                return response;
            }
            @Override
            public int getOrder() { return 10; }
        };

        Prompt prompt = new Prompt("hi");
        AdvisorChatInvoker.invoke(agent, prompt, null, List.of(modifier));

        verify(model).call(any(Prompt.class));
    }

    private ChatModel textModel(String text) {
        return prompt -> ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .build();
    }

    private JAgent agent(String id, ChatModel model) {
        return JAgent.builder(id, "agent-" + id)
                .description("agent " + id)
                .instructions("hi")
                .model(model)
                .build();
    }
}
