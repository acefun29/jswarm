// LangChain4j 兼容 facade，编排委托共享 Runtime
package com.jswarm.adapter.lc4j.run;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.ToolProvider;
import com.jswarm.adapter.lc4j.runtime.Lc4jMessageCodec;
import com.jswarm.adapter.lc4j.runtime.Lc4jRuntimeProvider;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import com.jswarm.runtime.event.RunEvent;
import com.jswarm.runtime.event.RunEventType;
import com.jswarm.runtime.run.RunEngine;
import com.jswarm.runtime.run.RunInput;
import com.jswarm.spi.run.RunExecution;
import com.jswarm.spi.run.RunScope;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SwarmRunner {

    private static final Consumer<SwarmEvent> NOOP_EVENT_SINK = event -> {
    };

    private final Swarm swarm;
    private final SwarmRunOptions options;
    private final RunEngine engine;
    private final Lc4jMessageCodec codec = new Lc4jMessageCodec();
    private final AtomicBoolean running = new AtomicBoolean();
    private SwarmRunListener listener;
    private volatile SwarmRunListener activeListener;
    private volatile Consumer<SwarmEvent> activeCoreSink = NOOP_EVENT_SINK;

    private SwarmRunner(
            Swarm swarm,
            SwarmRunOptions options,
            ExternalToolExecutor swarmToolExecutor,
            SwarmRunListener listener) {
        this.swarm = java.util.Objects.requireNonNull(swarm, "swarm");
        this.options = options != null ? options : SwarmRunOptions.defaults();
        this.listener = listener;
        this.engine = RunEngine.create(
                swarm,
                new Lc4jRuntimeProvider(this.options, swarmToolExecutor, this::emitCoreEvent),
                this::dispatchRuntimeEvent);
    }

    @Deprecated
    public void setListener(SwarmRunListener listener) {
        if (running.get()) {
            throw new IllegalStateException("Cannot set listener while run is in progress");
        }
        this.listener = listener;
    }

    public static SwarmRunner create(Swarm swarm) {
        return new SwarmRunner(swarm, SwarmRunOptions.defaults(), null, null);
    }

    public static SwarmRunner create(Swarm swarm, int maxTurns) {
        return new SwarmRunner(swarm,
                SwarmRunOptions.builder().maxTurns(maxTurns).build(), null, null);
    }

    public static SwarmRunner create(Swarm swarm, int maxTurns, ToolProvider toolProvider) {
        return create(swarm, maxTurns, (ExternalToolExecutor) toolProvider);
    }

    public static SwarmRunner create(
            Swarm swarm, int maxTurns, ExternalToolExecutor swarmToolExecutor) {
        return new SwarmRunner(swarm,
                SwarmRunOptions.builder().maxTurns(maxTurns).build(), swarmToolExecutor, null);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options) {
        return new SwarmRunner(swarm, options, null, null);
    }

    public static SwarmRunner create(
            Swarm swarm, SwarmRunOptions options, ToolProvider toolProvider) {
        return create(swarm, options, (ExternalToolExecutor) toolProvider);
    }

    public static SwarmRunner create(
            Swarm swarm, SwarmRunOptions options, ExternalToolExecutor swarmToolExecutor) {
        return new SwarmRunner(swarm, options, swarmToolExecutor, null);
    }

    public static SwarmRunner create(
            Swarm swarm,
            SwarmRunOptions options,
            ExternalToolExecutor swarmToolExecutor,
            SwarmRunListener listener) {
        return new SwarmRunner(swarm, options, swarmToolExecutor, listener);
    }

    public String run(String userMessage) {
        return run(userMessage, new SwarmContext());
    }

    public String run(String userMessage, SwarmContext context) {
        return execute(
                RunInput.fresh(userMessage, swarm.entryAgentId()),
                context,
                NOOP_EVENT_SINK).reply();
    }

    public RunResult runWithHistory(
            String userMessage,
            List<ChatMessage> priorHistory,
            String startAgentId,
            SwarmContext context,
            boolean skipEntryHook) {
        RunInput input = new RunInput(
                userMessage,
                priorHistory != null ? codec.decode(priorHistory) : List.of(),
                startAgentId,
                skipEntryHook,
                false);
        return toLegacy(execute(input, context, NOOP_EVENT_SINK));
    }

    public RunResult continueWithMessages(
            List<ChatMessage> messages,
            String startAgentId,
            SwarmContext context,
            boolean fireEnterHook) {
        RunInput input = new RunInput(
                null,
                messages != null ? codec.decode(messages) : List.of(),
                startAgentId,
                !fireEnterHook,
                false);
        return toLegacy(execute(input, context, NOOP_EVENT_SINK));
    }

    public void runStreaming(
            String userMessage,
            SwarmContext context,
            Consumer<SwarmEvent> sink) {
        Consumer<SwarmEvent> effectiveSink = sink != null ? sink : NOOP_EVENT_SINK;
        effectiveSink.accept(new SwarmEvent.RunStarted("", swarm.entryAgentId()));
        execute(new RunInput(userMessage, List.of(), swarm.entryAgentId(), false, true),
                context, effectiveSink);
    }

    private com.jswarm.runtime.run.RunResult execute(
            RunInput input,
            SwarmContext context,
            Consumer<SwarmEvent> coreSink) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("SwarmRunner is already running");
        }
        activeListener = listener;
        activeCoreSink = coreSink;
        try {
            SwarmContext effective = context != null ? context : new SwarmContext();
            com.jswarm.runtime.run.RunResult result = RunExecution.execute(
                    swarm,
                    input.startAgentId(),
                    RunExecution.limits(options.maxTurns(), options.maxDelegateDepth()),
                    RunExecution.policy(
                            options.maxRecoveryAttempts(),
                            options.modelTimeout(),
                            options.delegateStreaming()),
                    effective,
                    () -> engine.run(RunScope.current(), input));
            fireHistory(codec.encode(result.history()));
            return result;
        } finally {
            activeCoreSink = NOOP_EVENT_SINK;
            activeListener = null;
            running.set(false);
        }
    }

    private RunResult toLegacy(com.jswarm.runtime.run.RunResult result) {
        return new RunResult(result.reply(), result.currentAgentId(), codec.encode(result.history()));
    }

    private void dispatchRuntimeEvent(RunEvent event) {
        SwarmRunListener current = activeListener;
        Map<String, Object> payload = event.payload();
        if (event.type() == RunEventType.AGENT_ENTERED) {
            invokeListener(() -> current.onAgentEnter(
                    event.agentId(), String.valueOf(payload.getOrDefault("source", "ENTRY"))));
            emitCoreEvent(new SwarmEvent.AgentEnter(
                    event.agentId(), String.valueOf(payload.getOrDefault("source", "ENTRY"))));
        } else if (event.type() == RunEventType.AGENT_EXITED) {
            invokeListener(() -> current.onAgentExit(event.agentId()));
            emitCoreEvent(new SwarmEvent.AgentExit(event.agentId()));
        } else if (event.type() == RunEventType.TOOL_CALLED) {
            String toolName = String.valueOf(payload.get("toolName"));
            String arguments = String.valueOf(payload.get("arguments"));
            invokeListener(() -> current.onToolCall(event.agentId(), toolName, arguments));
            emitCoreEvent(new SwarmEvent.ToolCall(event.agentId(), toolName, arguments));
        } else if (event.type() == RunEventType.TOOL_RESULT) {
            String toolName = String.valueOf(payload.get("toolName"));
            String result = String.valueOf(payload.get("result"));
            invokeListener(() -> current.onToolResult(event.agentId(), toolName, result));
            emitCoreEvent(new SwarmEvent.ToolResult(event.agentId(), toolName, result));
        } else if (event.type() == RunEventType.HANDOFF) {
            String from = String.valueOf(payload.get("from"));
            String to = String.valueOf(payload.get("to"));
            invokeListener(() -> current.onHandoff(from, to));
            emitCoreEvent(new SwarmEvent.Handoff(from, to));
        } else if (event.type() == RunEventType.DELEGATE_STARTED) {
            String parent = String.valueOf(payload.get("parent"));
            String target = String.valueOf(payload.get("target"));
            String task = String.valueOf(payload.get("task"));
            invokeListener(() -> current.onDelegateStart(parent, target, task));
            emitCoreEvent(new SwarmEvent.DelegateStarted(parent, target, task));
        } else if (event.type() == RunEventType.DELEGATE_COMPLETED) {
            String parent = String.valueOf(payload.get("parent"));
            String target = String.valueOf(payload.get("target"));
            invokeListener(() -> current.onDelegateEnd(parent, target));
            emitCoreEvent(new SwarmEvent.DelegateFinished(parent, target));
        } else if (event.type() == RunEventType.RECOVERY) {
            String reason = String.valueOf(payload.get("reason"));
            invokeListener(() -> current.onRecovery(event.agentId(), reason));
            emitCoreEvent(new SwarmEvent.RecoveryTriggered(event.agentId(), reason));
        } else if (event.type() == RunEventType.COMPLETED) {
            String reply = String.valueOf(payload.getOrDefault("reply", ""));
            invokeListener(() -> current.onRunComplete(reply));
            emitCoreEvent(new SwarmEvent.RunCompleted(reply));
        } else if (event.type() == RunEventType.FAILED || event.type() == RunEventType.CANCELLED) {
            String error = String.valueOf(payload.getOrDefault("errorCode", "INTERNAL"));
            invokeListener(() -> current.onRunFail(event.agentId(), error));
            emitCoreEvent(new SwarmEvent.RunFailed(event.agentId(), error));
        }
    }

    private void fireHistory(List<ChatMessage> history) {
        SwarmRunListener current = activeListener;
        invokeListener(() -> current.onMessageHistoryUpdated(history));
    }

    private void invokeListener(Runnable callback) {
        if (activeListener == null) {
            return;
        }
        try {
            callback.run();
        } catch (RuntimeException ignored) {
        }
    }

    private void emitCoreEvent(SwarmEvent event) {
        try {
            activeCoreSink.accept(event);
        } catch (RuntimeException ignored) {
        }
    }

    public record RunResult(String reply, String currentAgentId, List<ChatMessage> updatedHistory) {
        public RunResult {
            updatedHistory = List.copyOf(updatedHistory);
        }
    }
}
