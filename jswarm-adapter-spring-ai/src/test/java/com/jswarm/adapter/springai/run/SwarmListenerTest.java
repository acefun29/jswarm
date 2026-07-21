package com.jswarm.adapter.springai.run;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SwarmListenerTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void listenerShouldReceiveRunCompleteInSyncRun() {
        List<String> events = new ArrayList<>();
        SwarmRunListener listener = new SwarmRunListener() {
            @Override
            public void onRunComplete(String finalText) {
                events.add("complete:" + finalText);
            }
        };

        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(stubModel("done"))
                .build();

        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        SwarmRunOptions options = SwarmRunOptions.builder().listener(listener).build();
        SwarmRunner.create(swarm, options).run("hi");

        assertEquals(1, events.size());
        assertTrue(events.get(0).contains("done"));
    }

    @Test
    void listenerExceptionShouldNotInterruptRun() {
        SwarmRunListener throwing = new SwarmRunListener() {
            @Override
            public void onAgentEnter(String agentId, String source) {
                throw new RuntimeException("boom");
            }
        };

        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(stubModel("done"))
                .build();

        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        SwarmRunOptions options = SwarmRunOptions.builder().listener(throwing).build();
        String reply = SwarmRunner.create(swarm, options).run("hi");

        assertEquals("done", reply);
    }

    @Test
    void listenerShouldReceiveHandoffEventsSync() {
        List<String> events = new ArrayList<>();
        SwarmRunListener listener = new SwarmRunListener() {
            @Override
            public void onHandoff(String from, String to) {
                events.add("handoff:" + from + "->" + to);
            }
        };

        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("router")
                .model(handoffModel("b"))
                .build();

        JAgent b = JAgent.builder("b", "B")
                .description("desc")
                .instructions("target")
                .model(stubModel("done"))
                .build();

        Swarm swarm = Swarm.create("s")
                .agent(a).agent(b)
                .entry("a").handoff("a", "b")
                .build();
        SwarmRunOptions options = SwarmRunOptions.builder().listener(listener).build();
        SwarmRunner.create(swarm, options).run("hi");

        assertEquals(1, events.size());
        assertTrue(events.get(0).contains("a->b"));
    }

    @Test
    void listenerShouldReceiveAllCallbacksOnToolCallPath() {
        java.util.Set<String> fired = new java.util.concurrent.ConcurrentSkipListSet<>();
        SwarmRunListener collector = new SwarmRunListener() {
            @Override public void onAgentEnter(String a, String s) { fired.add("onAgentEnter"); }
            @Override public void onAgentExit(String a) { fired.add("onAgentExit"); }
            @Override public void onToolCall(String a, String t, String arg) { fired.add("onToolCall"); }
            @Override public void onToolResult(String a, String t, String r) { fired.add("onToolResult"); }
            @Override public void onMessageHistoryUpdated(List<Message> m) { fired.add("onMessageHistoryUpdated"); }
            @Override public void onRunComplete(String f) { fired.add("onRunComplete"); }
        };

        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("use tool")
                .model(toolModel("getWeather", "{\"city\":\"bj\"}", "sunny"))
                .tools(new SimpleTool())
                .build();

        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        SwarmRunOptions options = SwarmRunOptions.builder().listener(collector).maxTurns(3).build();
        SwarmRunner.create(swarm, options).run("weather");

        assertTrue(fired.contains("onAgentEnter"), "onAgentEnter should fire");
        assertTrue(fired.contains("onToolCall"), "onToolCall should fire");
        assertTrue(fired.contains("onToolResult"), "onToolResult should fire");
        assertTrue(fired.contains("onAgentExit"), "onAgentExit should fire");
        assertTrue(fired.contains("onRunComplete"), "onRunComplete should fire");
    }

    @Test
    void listenerShouldReceiveAllCallbacksOnDelegatePath() {
        java.util.Set<String> fired = new java.util.concurrent.ConcurrentSkipListSet<>();
        SwarmRunListener collector = new SwarmRunListener() {
            @Override public void onAgentEnter(String a, String s) { fired.add("onAgentEnter"); }
            @Override public void onAgentExit(String a) { fired.add("onAgentExit"); }
            @Override public void onDelegateStart(String p, String t, String task) { fired.add("onDelegateStart"); }
            @Override public void onDelegateEnd(String p, String t) { fired.add("onDelegateEnd"); }
            @Override public void onToolCall(String a, String t, String arg) { fired.add("onToolCall"); }
            @Override public void onToolResult(String a, String t, String r) { fired.add("onToolResult"); }
            @Override public void onMessageHistoryUpdated(List<Message> m) { fired.add("onMessageHistoryUpdated"); }
            @Override public void onRunComplete(String f) { fired.add("onRunComplete"); }
        };

        JAgent router = JAgent.builder("router", "Router")
                .description("routes")
                .instructions("delegate")
                .model(delegateModel("expert", "calculate"))
                .build();

        JAgent expert = JAgent.builder("expert", "Expert")
                .description("computes")
                .instructions("compute things")
                .model(stubModel("42"))
                .build();

        Swarm swarm = Swarm.create("s")
                .agent(router).agent(expert)
                .entry("router")
                .delegate("router", "expert")
                .build();
        SwarmRunOptions options = SwarmRunOptions.builder().listener(collector).maxTurns(5).build();
        SwarmRunner.create(swarm, options).run("calculate");

        assertTrue(fired.contains("onAgentEnter"), "onAgentEnter should fire");
        assertTrue(fired.contains("onDelegateStart"), "onDelegateStart should fire");
        assertTrue(fired.contains("onDelegateEnd"), "onDelegateEnd should fire");
        assertTrue(fired.contains("onAgentExit"), "onAgentExit should fire");
        assertTrue(fired.contains("onRunComplete"), "onRunComplete should fire");
    }

    @Test
    void optionsListenerShouldBeUsedWhenBothSet() {
        List<String> events = new ArrayList<>();
        SwarmRunListener listener = new SwarmRunListener() {
            @Override
            public void onRunComplete(String finalText) {
                events.add("opts:" + finalText);
            }
        };

        JAgent a = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(stubModel("ok"))
                .build();

        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        SwarmRunOptions options = SwarmRunOptions.builder().listener(listener).build();
        SwarmRunner runner = SwarmRunner.create(swarm, options);

        SwarmRunListener late = new SwarmRunListener() {
            @Override
            public void onRunComplete(String finalText) {
                events.add("late:" + finalText);
            }
        };
        runner.setListener(late);

        runner.run("hi");

        assertEquals(1, events.size());
        assertTrue(events.get(0).contains("late"));
    }

    private static ChatModel stubModel(String reply) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(reply))));
        doReturn(response).when(model).call(any(Prompt.class));
        return model;
    }

    private static ChatModel toolModel(String toolName, String toolArgs, String secondReply) {
        ChatModel model = mock(ChatModel.class);
        AssistantMessage toolMsg = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call1", "function", toolName, toolArgs)))
                .build();
        ChatResponse toolResponse = new ChatResponse(List.of(new Generation(toolMsg)));
        ChatResponse textResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(secondReply))));
        doReturn(toolResponse, textResponse).when(model).call(any(Prompt.class));
        return model;
    }

    private static ChatModel delegateModel(String targetId, String task) {
        return toolModel("delegate",
                "{\"target\":\"" + targetId + "\",\"task\":\"" + task + "\"}", "");
    }

    static class SimpleTool {
        @org.springframework.ai.tool.annotation.Tool(description = "get weather")
        public String getWeather(String city) {
            return city + ": sunny";
        }
    }

    private static ChatModel handoffModel(String targetId) {
        ChatModel model = mock(ChatModel.class);
        AssistantMessage msg = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call1", "function", "handoff", "{\"target\":\"" + targetId + "\"}")))
                .build();
        ChatResponse response = new ChatResponse(List.of(new Generation(msg)));
        doReturn(response).when(model).call(any(Prompt.class));
        return model;
    }
}
