// 共享编排主循环行为测试
package com.jswarm.runtime.run;

import com.jswarm.core.Agent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.runtime.agent.AgentRuntime;
import com.jswarm.runtime.event.RunEvent;
import com.jswarm.runtime.event.RunEventType;
import com.jswarm.spi.bridge.SwarmContextBridge;
import com.jswarm.spi.context.ContextSnapshot;
import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.error.SwarmErrorException;
import com.jswarm.spi.id.AgentId;
import com.jswarm.spi.id.SwarmVersion;
import com.jswarm.spi.lifecycle.ModelGateway;
import com.jswarm.spi.lifecycle.ModelResult;
import com.jswarm.spi.lifecycle.ToolResult;
import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.spi.message.ToolCall;
import com.jswarm.spi.message.ToolDescriptor;
import com.jswarm.spi.run.RunBudget;
import com.jswarm.spi.run.RunRequest;
import com.jswarm.spi.run.RunScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunEngineTest {

    @AfterEach
    void clearBindings() {
        SwarmContext.clear();
        RunScope.clear();
    }

    @Test
    void shouldCompleteTextRunWithExactlyOnceHooks() {
        RecordingAgent agent = new RecordingAgent("a", "hello {name}");
        FakeRuntimeProvider provider = new FakeRuntimeProvider()
                .agent("a", CanonicalMessage.assistant("done"));
        List<RunEvent> events = new ArrayList<>();
        RunEngine engine = RunEngine.create(
                Swarm.create("s").agent(agent).entry("a").build(), provider, events::add);

        RunResult result = execute(engine, RunInput.fresh("hi", "a"),
                scope("a", ContextSnapshot.fromMap(Map.of("name", "Alice")), RunBudget.defaults()));

        assertEquals("done", result.reply());
        assertEquals(1, agent.enterCount.get());
        assertEquals(1, agent.exitCount.get());
        assertEquals("hello Alice", result.history().get(0).text());
        assertEquals(1, events.stream().filter(e -> e.type() == RunEventType.COMPLETED).count());
        assertEquals(0, events.stream().filter(e -> e.type() == RunEventType.FAILED).count());
        assertThrows(UnsupportedOperationException.class,
                () -> result.history().add(CanonicalMessage.user("x")));
    }

    @Test
    void shouldHandoffAndReplaceSystemMessage() {
        RecordingAgent a = new RecordingAgent("a", "system-a");
        RecordingAgent b = new RecordingAgent("b", "system-b");
        FakeRuntimeProvider provider = new FakeRuntimeProvider()
                .agent("a", CanonicalMessage.assistant("", List.of(
                        route("h1", "handoff", Map.of("target", "b")))))
                .agent("b", CanonicalMessage.assistant("from-b"));
        List<RunEvent> events = new ArrayList<>();
        Swarm swarm = Swarm.create("s").agent(a).agent(b).entry("a").handoff("a", "b").build();

        RunResult result = execute(RunEngine.create(swarm, provider, events::add),
                RunInput.fresh("hi", "a"), scope("a", ContextSnapshot.empty(), RunBudget.defaults()));

        assertEquals("b", result.currentAgentId());
        assertEquals("system-b", result.history().get(0).text());
        assertEquals(1, a.enterCount.get());
        assertEquals(1, a.exitCount.get());
        assertEquals(1, b.enterCount.get());
        assertEquals(1, b.exitCount.get());
        assertEquals(1, events.stream().filter(e -> e.type() == RunEventType.HANDOFF).count());
    }

    @Test
    void shouldDelegateWithChildScopeAndReturnToolResult() {
        RecordingAgent a = new RecordingAgent("a", "system-a");
        RecordingAgent b = new RecordingAgent("b", "system-b");
        FakeRuntimeProvider provider = new FakeRuntimeProvider()
                .agent("a",
                        CanonicalMessage.assistant("", List.of(route("d1", "delegate",
                                Map.of("target", "b", "task", "inspect")))),
                        CanonicalMessage.assistant("parent-done"))
                .agent("b", CanonicalMessage.assistant("child-result"));
        List<RunEvent> events = new ArrayList<>();
        Swarm swarm = Swarm.create("s").agent(a).agent(b).entry("a").delegate("a", "b").build();

        RunResult result = execute(RunEngine.create(swarm, provider, events::add),
                RunInput.fresh("hi", "a"), scope("a", ContextSnapshot.empty(), RunBudget.defaults()));

        assertEquals("parent-done", result.reply());
        assertEquals(1, b.delegateEnterCount.get());
        assertEquals(1, b.delegateExitCount.get());
        assertTrue(result.history().stream().anyMatch(message ->
                message.toolCallId() != null && "child-result".equals(message.text())));
        RunEvent started = events.stream()
                .filter(e -> e.type() == RunEventType.DELEGATE_STARTED).findFirst().orElseThrow();
        RunEvent completed = events.stream()
                .filter(e -> e.type() == RunEventType.DELEGATE_COMPLETED).findFirst().orElseThrow();
        assertEquals(started.runId(), completed.runId());
        List<RunEvent> childEvents = events.stream()
                .filter(e -> e.parentRunId().asOptional().isPresent()).toList();
        assertFalse(childEvents.isEmpty());
        assertEquals(1, childEvents.stream().map(RunEvent::runId).distinct().count());
        assertTrue(childEvents.stream().allMatch(e ->
                e.parentRunId().asOptional().orElseThrow().equals(started.runId().value())));
    }

    @Test
    void shouldExecuteAllExternalToolsInBatch() {
        RecordingAgent a = new RecordingAgent("a", "system-a");
        FakeRuntimeProvider provider = new FakeRuntimeProvider()
                .agentWithTools("a", List.of(new ToolDescriptor("one", "", "{}"),
                                new ToolDescriptor("two", "", "{}")),
                        CanonicalMessage.assistant("", List.of(
                                new ToolCall("1", "one", "{}"),
                                new ToolCall("2", "two", "{}"))),
                        CanonicalMessage.assistant("done"));
        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();

        RunResult result = execute(RunEngine.create(swarm, provider),
                RunInput.fresh("hi", "a"), scope("a", ContextSnapshot.empty(), RunBudget.defaults()));

        assertEquals(List.of("one", "two"), provider.invokedTools);
        assertEquals(2, result.history().stream()
                .filter(message -> message.toolCallId() != null).count());
    }

    @Test
    void onExitFailureShouldOnlyEmitFailedTerminal() {
        RecordingAgent a = new RecordingAgent("a", "system-a");
        a.failExit = true;
        FakeRuntimeProvider provider = new FakeRuntimeProvider()
                .agent("a", CanonicalMessage.assistant("done"));
        List<RunEvent> events = new ArrayList<>();
        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();

        assertThrows(IllegalStateException.class, () -> execute(
                RunEngine.create(swarm, provider, events::add), RunInput.fresh("hi", "a"),
                scope("a", ContextSnapshot.empty(), RunBudget.defaults())));

        assertEquals(1, a.exitCount.get());
        assertEquals(0, events.stream().filter(e -> e.type() == RunEventType.COMPLETED).count());
        assertEquals(1, events.stream().filter(e -> e.type() == RunEventType.FAILED).count());
    }

    @Test
    void shouldEmitCancelledTerminal() {
        RecordingAgent a = new RecordingAgent("a", "system-a");
        FakeRuntimeProvider provider = new FakeRuntimeProvider()
                .agent("a", CanonicalMessage.assistant("unused"));
        List<RunEvent> events = new ArrayList<>();
        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        RunScope scope = scope("a", ContextSnapshot.empty(), RunBudget.defaults());
        scope.cancellation().cancel(com.jswarm.spi.time.CancellationReason.USER_REQUEST);

        SwarmErrorException error = assertThrows(SwarmErrorException.class,
                () -> execute(RunEngine.create(swarm, provider, events::add),
                        RunInput.fresh("hi", "a"), scope));

        assertEquals(SwarmErrorCode.CANCELLED, error.code());
        assertEquals(1, events.stream().filter(e -> e.type() == RunEventType.CANCELLED).count());
        assertEquals(0, events.stream().filter(e -> e.type() == RunEventType.FAILED).count());
    }

    @Test
    void shouldFailWhenGlobalTurnBudgetIsExhausted() {
        RecordingAgent a = new RecordingAgent("a", "system-a");
        FakeRuntimeProvider provider = new FakeRuntimeProvider()
                .agentWithTools("a", List.of(new ToolDescriptor("one", "", "{}")),
                        CanonicalMessage.assistant("", List.of(new ToolCall("1", "one", "{}"))),
                        CanonicalMessage.assistant("unused"));
        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        RunBudget budget = RunBudget.builder()
                .maxTurns(1).maxModelCalls(2).maxToolCalls(2).maxDepth(2).build();

        SwarmErrorException error = assertThrows(SwarmErrorException.class,
                () -> execute(RunEngine.create(swarm, provider), RunInput.fresh("hi", "a"),
                        scope("a", ContextSnapshot.empty(), budget)));

        assertEquals(SwarmErrorCode.BUDGET_EXCEEDED, error.code());
    }

    @Test
    void shouldRejectMissingRuntimeCapabilityAtCreation() {
        RecordingAgent a = new RecordingAgent("a", "system-a");
        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        SwarmErrorException error = assertThrows(SwarmErrorException.class,
                () -> RunEngine.create(swarm, agent -> null));
        assertEquals(SwarmErrorCode.INVALID_INPUT, error.code());
    }

    @Test
    void shouldRejectBlankInstructionsAtCreation() {
        RecordingAgent a = new RecordingAgent("a", "");
        Swarm swarm = Swarm.create("s").agent(a).entry("a").build();
        FakeRuntimeProvider provider = new FakeRuntimeProvider()
                .agent("a", CanonicalMessage.assistant("done"));
        provider.instructionsConfigured.put("a", false);
        SwarmErrorException error = assertThrows(SwarmErrorException.class,
                () -> RunEngine.create(swarm, provider));
        assertEquals(SwarmErrorCode.INVALID_INPUT, error.code());
    }

    private static RunResult execute(RunEngine engine, RunInput input, RunScope scope) {
        SwarmContextBridge.ScopeBinding binding = SwarmContextBridge.bind(scope);
        try {
            return engine.run(scope, input);
        } finally {
            SwarmContextBridge.restore(binding);
        }
    }

    private static RunScope scope(String agentId, ContextSnapshot context, RunBudget budget) {
        return RunScope.root(RunRequest.builder()
                .swarmVersion(SwarmVersion.of("s"))
                .startAgentId(AgentId.of(agentId))
                .contextSnapshot(context)
                .budget(budget)
                .runTimeout(Duration.ofMinutes(1))
                .build());
    }

    private static ToolCall route(String id, String name, Map<String, String> arguments) {
        return new ToolCall(id, name, "{}", arguments);
    }

    private static final class RecordingAgent implements Agent {
        private final String id;
        private final String instructions;
        private final AtomicInteger enterCount = new AtomicInteger();
        private final AtomicInteger exitCount = new AtomicInteger();
        private final AtomicInteger delegateEnterCount = new AtomicInteger();
        private final AtomicInteger delegateExitCount = new AtomicInteger();
        private boolean failExit;

        private RecordingAgent(String id, String instructions) {
            this.id = id;
            this.instructions = instructions;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public String description() {
            return id + " description";
        }

        @Override
        public String instructions() {
            return instructions;
        }

        @Override
        public void onEnter(SwarmContext context) {
            enterCount.incrementAndGet();
        }

        @Override
        public void onExit(SwarmContext context) {
            exitCount.incrementAndGet();
            if (failExit) {
                throw new IllegalStateException("exit failed");
            }
        }

        @Override
        public void onDelegateEnter(SwarmContext context, String task) {
            delegateEnterCount.incrementAndGet();
        }

        @Override
        public void onDelegateExit(SwarmContext context, String task, String result) {
            delegateExitCount.incrementAndGet();
        }
    }

    private static final class FakeRuntimeProvider implements com.jswarm.runtime.agent.RuntimeProvider {
        private final Map<String, Deque<CanonicalMessage>> results = new LinkedHashMap<>();
        private final Map<String, List<ToolDescriptor>> tools = new LinkedHashMap<>();
        private final Map<String, Boolean> instructionsConfigured = new LinkedHashMap<>();
        private final List<String> invokedTools = new ArrayList<>();

        private FakeRuntimeProvider agent(String id, CanonicalMessage... messages) {
            return agentWithTools(id, List.of(), messages);
        }

        private FakeRuntimeProvider agentWithTools(
                String id, List<ToolDescriptor> descriptors, CanonicalMessage... messages) {
            results.put(id, new ArrayDeque<>(List.of(messages)));
            tools.put(id, List.copyOf(descriptors));
            instructionsConfigured.put(id, true);
            return this;
        }

        @Override
        public AgentRuntime resolve(Agent agent) {
            Deque<CanonicalMessage> queue = results.get(agent.id());
            if (queue == null) {
                return null;
            }
            ModelGateway gateway = (request, scope) -> new ModelResult(queue.removeFirst());
            return new AgentRuntime(
                    agent.id(),
                    gateway,
                    (invocation, context) -> {
                        invokedTools.add(invocation.toolName());
                        return new ToolResult("result-" + invocation.toolName());
                    },
                    tools.get(agent.id()),
                    true,
                    instructionsConfigured.get(agent.id()));
        }
    }
}
