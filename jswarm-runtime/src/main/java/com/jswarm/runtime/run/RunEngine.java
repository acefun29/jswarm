// Provider-neutral 编排主循环
package com.jswarm.runtime.run;

import com.jswarm.core.Agent;
import com.jswarm.core.ProtocolLimits;
import com.jswarm.core.RouteAuthorization;
import com.jswarm.core.RouteDeniedException;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.runtime.agent.AgentRuntime;
import com.jswarm.runtime.agent.RuntimeProvider;
import com.jswarm.runtime.event.EventDispatcher;
import com.jswarm.runtime.event.RunEventSink;
import com.jswarm.runtime.event.RunEventType;
import com.jswarm.runtime.route.RouteDecision;
import com.jswarm.runtime.state.RunState;
import com.jswarm.runtime.state.RunStateMachine;
import com.jswarm.runtime.tool.OrchestrationTools;
import com.jswarm.spi.bridge.SwarmContextBridge;
import com.jswarm.spi.context.ContextSnapshot;
import com.jswarm.spi.error.SwarmError;
import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.error.SwarmErrorException;
import com.jswarm.spi.lifecycle.ModelRequest;
import com.jswarm.spi.lifecycle.ModelResult;
import com.jswarm.spi.lifecycle.ToolContext;
import com.jswarm.spi.lifecycle.ToolInvocation;
import com.jswarm.spi.lifecycle.ToolResult;
import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.spi.message.MessageRole;
import com.jswarm.spi.message.ToolCall;
import com.jswarm.spi.message.ToolDescriptor;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RunEngine {

    private final Swarm swarm;
    private final Map<String, AgentRuntime> runtimes;
    private final RunEventSink eventSink;

    private RunEngine(Swarm swarm, RuntimeProvider provider, RunEventSink eventSink) {
        this.swarm = Objects.requireNonNull(swarm, "swarm");
        this.eventSink = eventSink != null ? eventSink : RunEventSink.NOOP;
        this.runtimes = validateAndSnapshot(swarm, provider);
    }

    public static RunEngine create(Swarm swarm, RuntimeProvider provider) {
        return new RunEngine(swarm, provider, RunEventSink.NOOP);
    }

    public static RunEngine create(Swarm swarm, RuntimeProvider provider, RunEventSink eventSink) {
        return new RunEngine(swarm, provider, eventSink);
    }

    public RunResult run(RunScope scope, RunInput input) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(input, "input");
        String startAgentId = input.startAgentId() != null ? input.startAgentId() : swarm.entryAgentId();
        if (!runtimes.containsKey(startAgentId)) {
            throw error(SwarmErrorCode.INVALID_INPUT, "Unknown start agent", "agentId", startAgentId);
        }
        if (input.streaming()) {
            for (AgentRuntime runtime : runtimes.values()) {
                if (!runtime.streamingSupported()) {
                    throw error(SwarmErrorCode.INVALID_INPUT,
                            "Streaming capability is not available", "agentId", runtime.agentId());
                }
            }
        }

        EventDispatcher dispatcher = new EventDispatcher(eventSink);
        Frame frame = new Frame(scope, startAgentId, input.streaming(), false, null, dispatcher);
        try {
            enter(frame, input.skipEntryHook());
            prepareHistory(frame, input);
            return loop(frame);
        } catch (RuntimeException failure) {
            fail(frame, failure, true);
            throw failure;
        }
    }

    public String delegate(
            RunScope parentScope,
            String sourceAgentId,
            String targetAgentId,
            String task,
            boolean streaming) {
        Objects.requireNonNull(parentScope, "parentScope");
        RouteAuthorization.authorizeDelegate(swarm, sourceAgentId, targetAgentId);
        EventDispatcher dispatcher = new EventDispatcher(eventSink);
        Frame parent = new Frame(
                parentScope, sourceAgentId, streaming, false, null, dispatcher);
        return executeDelegate(parent, targetAgentId, task);
    }

    private RunResult loop(Frame frame) {
        transition(frame, RunState.MODEL_CALL);
        while (true) {
            RunScopeChecks.beforeTurn(frame.scope);
            frame.turn++;
            AgentRuntime runtime = runtimes.get(frame.agentId);
            List<ToolDescriptor> tools = toolsFor(frame, runtime);
            frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, null,
                    RunEventType.MODEL_CALLED, Map.of());
            RunScopeChecks.beforeModelCall(frame.scope);
            ModelResult modelResult = runtime.modelGateway().invoke(
                    new ModelRequest(frame.agentId, frame.messages, tools, frame.streaming), frame.scope);
            CanonicalMessage assistant = modelResult.message();
            if (assistant.role() != MessageRole.ASSISTANT) {
                throw error(SwarmErrorCode.PROTOCOL_ERROR,
                        "Model result must be an assistant message", "agentId", frame.agentId);
            }
            frame.messages.add(assistant);
            if (!assistant.hasToolCalls()) {
                closeActivation(frame, assistant.text());
                transition(frame, RunState.COMPLETED);
                if (!frame.delegate) {
                    frame.scope.markTerminal();
                    frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, null,
                            RunEventType.COMPLETED, Map.of("reply", assistant.text()));
                }
                return new RunResult(assistant.text(), frame.agentId, frame.messages, RunState.COMPLETED);
            }

            transition(frame, RunState.TOOL_BATCH);
            RouteDecision decision = processToolBatch(frame, runtime, assistant.toolCalls());
            if (decision instanceof RouteDecision.Handoff handoff) {
                handleHandoff(frame, handoff.targetAgentId());
            } else if (decision instanceof RouteDecision.Delegate delegate) {
                handleDelegate(frame, assistant.toolCalls().get(0), delegate);
            } else {
                transition(frame, RunState.MODEL_CALL);
            }
        }
    }

    private RouteDecision processToolBatch(Frame frame, AgentRuntime runtime, List<ToolCall> calls) {
        String protocolError = validateBatch(calls);
        if (protocolError != null) {
            appendBatchError(frame, calls, protocolError);
            recover(frame, protocolError);
            return new RouteDecision.Continue();
        }

        List<ToolCall> routing = calls.stream().filter(call -> OrchestrationTools.routing(call.name())).toList();
        if (routing.size() > 1 || (!routing.isEmpty() && routing.size() != calls.size())) {
            String message = "Jswarm: only one routing tool call is allowed per turn.";
            appendBatchError(frame, calls, message);
            recover(frame, message);
            return new RouteDecision.Continue();
        }
        if (routing.size() == 1) {
            ToolCall call = routing.get(0);
            if (frame.delegate) {
                String message = "Jswarm: routing tools are not allowed inside delegate sub-runs.";
                frame.messages.add(CanonicalMessage.toolResult(call.id(), call.name(), message));
                recover(frame, message);
                return new RouteDecision.Continue();
            }
            RouteDecision decision = decideRoute(frame.agentId, call);
            if (decision instanceof RouteDecision.Reject reject) {
                frame.messages.add(CanonicalMessage.toolResult(call.id(), call.name(), reject.modelSafeMessage()));
                recover(frame, reject.modelSafeMessage());
                return new RouteDecision.Continue();
            }
            if (decision instanceof RouteDecision.Handoff handoff) {
                frame.messages.add(CanonicalMessage.toolResult(call.id(), call.name(),
                        "Jswarm: transferred to agent '" + handoff.targetAgentId() + "'."));
            }
            return decision;
        }

        for (ToolCall call : calls) {
            executeExternalTool(frame, runtime, call);
        }
        return new RouteDecision.Continue();
    }

    private void executeExternalTool(Frame frame, AgentRuntime runtime, ToolCall call) {
        frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, call.id(),
                RunEventType.TOOL_CALLED, Map.of("toolName", call.name(), "arguments", call.arguments()));
        String output;
        try {
            RunScopeChecks.beforeToolCall(frame.scope);
            if (runtime.toolInvoker() == null) {
                throw error(SwarmErrorCode.TOOL_FAILURE,
                        "No tool invoker is available", "toolName", call.name());
            }
            ToolResult result = runtime.toolInvoker().execute(
                    new ToolInvocation(call.id(), call.name(), call.arguments()),
                    new ToolContext(frame.scope, frame.scope.deadline(), frame.scope.cancellation()));
            output = result.output();
        } catch (RuntimeException failure) {
            output = "Jswarm recovery: tool '" + call.name()
                    + "' failed. Please answer directly or try another available tool.";
            frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, call.id(),
                    RunEventType.RECOVERY, Map.of("reason", "tool_failure", "toolName", call.name()));
        }
        output = ProtocolLimits.truncateResult(output);
        RunScopeChecks.recordToolResultBytes(frame.scope, output);
        frame.messages.add(CanonicalMessage.toolResult(call.id(), call.name(), output));
        frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, call.id(),
                RunEventType.TOOL_RESULT, Map.of("toolName", call.name(), "result", output));
    }

    private void handleHandoff(Frame frame, String targetAgentId) {
        transition(frame, RunState.ROUTING);
        String sourceAgentId = frame.agentId;
        closeActivation(frame, null);
        frame.dispatcher.emit(frame.scope, frame.turn, sourceAgentId, null,
                RunEventType.HANDOFF, Map.of("from", sourceAgentId, "to", targetAgentId));
        frame.agentId = targetAgentId;
        frame.scope = frame.scope.withAgent(com.jswarm.spi.id.AgentId.of(targetAgentId));
        RunScope.bind(frame.scope);
        enter(frame, false);
        replaceSystemMessage(frame);
        transition(frame, RunState.MODEL_CALL);
    }

    private void handleDelegate(Frame frame, ToolCall call, RouteDecision.Delegate delegate) {
        transition(frame, RunState.DELEGATE);
        frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, call.id(),
                RunEventType.DELEGATE_STARTED,
                Map.of("parent", frame.agentId, "target", delegate.targetAgentId(), "task", delegate.task()));
        String result;
        try {
            result = executeDelegate(frame, delegate.targetAgentId(), delegate.task());
        } catch (RuntimeException failure) {
            if (frame.recoveryAttempts >= frame.scope.policy().maxRecoveryAttempts()) {
                throw SwarmError.of(
                        SwarmErrorCode.MODEL_FAILURE,
                        "Recovery attempts exceeded",
                        failure).toException();
            }
            frame.recoveryAttempts++;
            result = "Jswarm recovery: delegate to '" + delegate.targetAgentId()
                    + "' failed. Please continue without it.";
            recover(frame, "delegate_failure");
        }
        result = ProtocolLimits.truncateResult(result);
        RunScopeChecks.recordToolResultBytes(frame.scope, result);
        frame.messages.add(CanonicalMessage.toolResult(call.id(), call.name(), result));
        frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, call.id(),
                RunEventType.DELEGATE_COMPLETED,
                Map.of("parent", frame.agentId, "target", delegate.targetAgentId(), "result", result));
        transition(frame, RunState.MODEL_CALL);
    }

    private String executeDelegate(Frame parent, String targetAgentId, String task) {
        SwarmContext parentContext = currentContext();
        ContextSnapshot currentSnapshot = ContextSnapshot.fromMap(parentContext.asMap());
        RunScope childScope = parent.scope.withContextOverlay(currentSnapshot).child(
                com.jswarm.spi.id.AgentId.of(targetAgentId));
        SwarmContextBridge.ScopeBinding binding = SwarmContextBridge.bind(childScope);
        Frame child = new Frame(childScope, targetAgentId,
                parent.streaming && parent.scope.policy().delegateStreaming(), true, task, parent.dispatcher);
        try {
            enter(child, false);
            child.messages.add(CanonicalMessage.system(resolveInstructions(targetAgentId)));
            child.messages.add(CanonicalMessage.user(task));
            return loop(child).reply();
        } catch (RuntimeException failure) {
            fail(child, failure, false);
            throw failure;
        } finally {
            SwarmContextBridge.restore(binding);
        }
    }

    private void enter(Frame frame, boolean skipHook) {
        transition(frame, RunState.ENTERING);
        Agent agent = swarm.getAgent(frame.agentId);
        Activation activation = new Activation(agent, frame.delegate, frame.delegateTask, skipHook);
        frame.activation = activation;
        if (!skipHook) {
            if (frame.delegate) {
                agent.onDelegateEnter(currentContext(), frame.delegateTask);
            } else {
                agent.onEnter(currentContext());
            }
        }
        activation.entered = true;
        frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, null,
                RunEventType.AGENT_ENTERED,
                Map.of("source", frame.delegate ? "DELEGATE" : skipHook ? "RESUME" : "ENTRY"));
    }

    private void closeActivation(Frame frame, String result) {
        Activation activation = frame.activation;
        if (activation == null || activation.closed || !activation.entered) {
            return;
        }
        activation.closed = true;
        if (!activation.skipHook) {
            if (activation.delegate) {
                activation.agent.onDelegateExit(currentContext(), activation.task, result);
            } else {
                activation.agent.onExit(currentContext());
            }
        }
        frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, null,
                RunEventType.AGENT_EXITED, Map.of());
    }

    private void prepareHistory(Frame frame, RunInput input) {
        if (input.priorHistory().isEmpty()) {
            frame.messages.add(CanonicalMessage.system(resolveInstructions(frame.agentId)));
            if (input.userMessage() == null) {
                throw error(SwarmErrorCode.INVALID_INPUT,
                        "User message must not be null", "agentId", frame.agentId);
            }
            frame.messages.add(CanonicalMessage.user(input.userMessage()));
            return;
        }
        frame.messages.addAll(input.priorHistory());
        if (input.userMessage() != null) {
            frame.messages.add(CanonicalMessage.user(input.userMessage()));
        }
    }

    private void replaceSystemMessage(Frame frame) {
        CanonicalMessage system = CanonicalMessage.system(resolveInstructions(frame.agentId));
        for (int i = 0; i < frame.messages.size(); i++) {
            if (frame.messages.get(i).role() == MessageRole.SYSTEM) {
                frame.messages.set(i, system);
                return;
            }
        }
        frame.messages.add(0, system);
    }

    private String resolveInstructions(String agentId) {
        String instructions = swarm.getAgent(agentId).instructions();
        if (instructions == null || instructions.isBlank()) {
            throw error(SwarmErrorCode.INVALID_INPUT,
                    "Agent instructions must not be blank", "agentId", agentId);
        }
        return currentContext().resolve(instructions);
    }

    private RouteDecision decideRoute(String sourceAgentId, ToolCall call) {
        String target = call.argument("target");
        try {
            ProtocolLimits.validateRouteTarget(target);
            if (OrchestrationTools.HANDOFF.equals(call.name())) {
                RouteAuthorization.authorizeHandoff(swarm, sourceAgentId, target);
                return new RouteDecision.Handoff(target);
            }
            String task = call.argument("task");
            ProtocolLimits.validateDelegateTask(task);
            RouteAuthorization.authorizeDelegate(swarm, sourceAgentId, target);
            return new RouteDecision.Delegate(target, task);
        } catch (RouteDeniedException denied) {
            return new RouteDecision.Reject(denied.modelSafeMessage());
        } catch (RuntimeException invalid) {
            return new RouteDecision.Reject("Jswarm recovery: invalid routing arguments.");
        }
    }

    private List<ToolDescriptor> toolsFor(Frame frame, AgentRuntime runtime) {
        List<ToolDescriptor> tools = new ArrayList<>(runtime.tools());
        if (!frame.delegate) {
            tools.addAll(OrchestrationTools.forAgent(swarm, frame.agentId));
        }
        return List.copyOf(tools);
    }

    private void appendBatchError(Frame frame, List<ToolCall> calls, String message) {
        for (ToolCall call : calls) {
            frame.messages.add(CanonicalMessage.toolResult(call.id(), call.name(), message));
        }
    }

    private void recover(Frame frame, String reason) {
        frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, null,
                RunEventType.RECOVERY, Map.of("reason", reason));
    }

    private void transition(Frame frame, RunState next) {
        RunState previous = frame.state.state();
        frame.state.transitionTo(next);
        frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, null,
                RunEventType.STATE_CHANGED, Map.of("from", previous.name(), "to", next.name()));
    }

    private void fail(Frame frame, RuntimeException failure, boolean terminalEvent) {
        try {
            closeActivation(frame, null);
        } catch (RuntimeException hookFailure) {
            if (hookFailure != failure) {
                failure.addSuppressed(hookFailure);
            }
        }
        RunState terminal = cancelled(frame.scope, failure) ? RunState.CANCELLED : RunState.FAILED;
        if (!frame.state.state().terminal()) {
            transition(frame, terminal);
        }
        if (terminalEvent) {
            frame.scope.markTerminal();
            frame.dispatcher.emit(frame.scope, frame.turn, frame.agentId, null,
                    terminal == RunState.CANCELLED ? RunEventType.CANCELLED : RunEventType.FAILED,
                    Map.of("errorCode", errorCode(failure).name()));
        }
    }

    private static boolean cancelled(RunScope scope, RuntimeException failure) {
        return scope.cancellation().isCancelled()
                || failure instanceof SwarmErrorException error && error.code() == SwarmErrorCode.CANCELLED;
    }

    private static SwarmErrorCode errorCode(RuntimeException failure) {
        return failure instanceof SwarmErrorException error ? error.code() : SwarmErrorCode.INTERNAL;
    }

    private static String validateBatch(List<ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return "Jswarm: tool call batch is empty.";
        }
        Set<String> ids = new HashSet<>();
        for (ToolCall call : calls) {
            if (!ids.add(call.id())) {
                return "Jswarm: duplicate tool call id in the same batch.";
            }
        }
        return null;
    }

    private static Map<String, AgentRuntime> validateAndSnapshot(Swarm swarm, RuntimeProvider provider) {
        Objects.requireNonNull(provider, "provider");
        Map<String, AgentRuntime> values = new LinkedHashMap<>();
        for (Agent agent : swarm.listAgents()) {
            AgentRuntime runtime;
            try {
                runtime = provider.resolve(agent);
            } catch (RuntimeException failure) {
                throw error(SwarmErrorCode.INVALID_INPUT,
                        "Agent runtime capability validation failed", "agentId", agent.id(), failure);
            }
            if (runtime == null || !agent.id().equals(runtime.agentId())) {
                throw error(SwarmErrorCode.INVALID_INPUT,
                        "Agent runtime capability is missing", "agentId", agent.id());
            }
            if (!runtime.instructionsConfigured()) {
                throw error(SwarmErrorCode.INVALID_INPUT,
                        "Agent instructions are missing", "agentId", agent.id());
            }
            Set<String> names = new HashSet<>();
            for (ToolDescriptor tool : runtime.tools()) {
                if (OrchestrationTools.routing(tool.name()) || !names.add(tool.name())) {
                    throw error(SwarmErrorCode.INVALID_INPUT,
                            "Agent tool capability is invalid", "agentId", agent.id());
                }
            }
            values.put(agent.id(), runtime);
        }
        return Map.copyOf(values);
    }

    private static SwarmContext currentContext() {
        SwarmContext context = SwarmContext.current();
        if (context == null) {
            throw error(SwarmErrorCode.ILLEGAL_STATE,
                    "SwarmContext is not bound", "context", "missing");
        }
        return context;
    }

    private static SwarmErrorException error(
            SwarmErrorCode code, String message, String key, String value) {
        return SwarmError.of(code, message).withMetadata(key, String.valueOf(value)).toException();
    }

    private static SwarmErrorException error(
            SwarmErrorCode code, String message, String key, String value, Throwable cause) {
        return SwarmError.of(code, message, Map.of(key, String.valueOf(value)), cause).toException();
    }

    private static final class Frame {
        private RunScope scope;
        private String agentId;
        private final boolean streaming;
        private final boolean delegate;
        private final String delegateTask;
        private final EventDispatcher dispatcher;
        private final RunStateMachine state = new RunStateMachine();
        private final List<CanonicalMessage> messages = new ArrayList<>();
        private Activation activation;
        private int turn;
        private int recoveryAttempts;

        private Frame(
                RunScope scope,
                String agentId,
                boolean streaming,
                boolean delegate,
                String delegateTask,
                EventDispatcher dispatcher) {
            this.scope = scope;
            this.agentId = agentId;
            this.streaming = streaming;
            this.delegate = delegate;
            this.delegateTask = delegateTask;
            this.dispatcher = dispatcher;
        }
    }

    private static final class Activation {
        private final Agent agent;
        private final boolean delegate;
        private final String task;
        private final boolean skipHook;
        private boolean entered;
        private boolean closed;

        private Activation(Agent agent, boolean delegate, String task, boolean skipHook) {
            this.agent = agent;
            this.delegate = delegate;
            this.task = task;
            this.skipHook = skipHook;
        }
    }
}
