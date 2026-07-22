package com.jswarm.adapter.lc4j.run;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.adapter.lc4j.ToolProvider;
import com.jswarm.adapter.lc4j.invoke.StreamingChatInvoker;
import com.jswarm.core.Agent;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import com.jswarm.adapter.lc4j.filter.SwarmFilter;
import com.jswarm.adapter.lc4j.filter.ToolCallBatchProcessor;
import com.jswarm.adapter.lc4j.invoke.ChatInvoker;
import com.jswarm.adapter.lc4j.tool.SwarmToolInjector;
import com.jswarm.adapter.lc4j.tool.ToolExecutionMerger;
import com.jswarm.core.ProtocolLimits;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmException;
import com.jswarm.spi.id.AgentId;
import com.jswarm.spi.run.RunExecution;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SwarmRunner {

    private final Swarm swarm;
    private final SwarmRunOptions options;
    private final SwarmFilter filter;
    private final ExternalToolExecutor swarmToolExecutor;
    private SwarmRunListener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private SwarmRunListener activeListener;

    private SwarmRunner(Swarm swarm, SwarmRunOptions options, ExternalToolExecutor swarmToolExecutor, SwarmRunListener listener) {
        this.swarm = swarm;
        this.options = options;
        this.filter = new SwarmFilter(swarm);
        this.swarmToolExecutor = swarmToolExecutor;
        this.listener = listener;
    }

    /**
     * @deprecated Set listener before {@code run()} via factory methods; cannot change during an active run.
     */
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
                SwarmRunOptions.builder().maxTurns(maxTurns).build(),
                null, null);
    }

    public static SwarmRunner create(Swarm swarm, int maxTurns, ToolProvider toolProvider) {
        return create(swarm, maxTurns, (ExternalToolExecutor) toolProvider);
    }

    public static SwarmRunner create(Swarm swarm, int maxTurns, ExternalToolExecutor swarmToolExecutor) {
        return new SwarmRunner(swarm,
                SwarmRunOptions.builder().maxTurns(maxTurns).build(),
                swarmToolExecutor, null);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options) {
        return new SwarmRunner(swarm, options, null, null);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options, ToolProvider toolProvider) {
        return create(swarm, options, (ExternalToolExecutor) toolProvider);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options, ExternalToolExecutor swarmToolExecutor) {
        return new SwarmRunner(swarm, options, swarmToolExecutor, null);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options, ExternalToolExecutor swarmToolExecutor, SwarmRunListener listener) {
        return new SwarmRunner(swarm, options, swarmToolExecutor, listener);
    }

    public String run(String userMessage) {
        return run(userMessage, new SwarmContext());
    }

    public String run(String userMessage, SwarmContext context) {
        return withRunScope(swarm.entryAgentId(), context, () -> runInternal(userMessage));
    }

    public record RunResult(String reply, String currentAgentId, List<ChatMessage> updatedHistory) {
        public RunResult {
            updatedHistory = List.copyOf(updatedHistory);
        }
    }

    public RunResult runWithHistory(String userMessage, List<ChatMessage> priorHistory,
                                    String startAgentId, SwarmContext context, boolean skipEntryHook) {
        return withRunScope(startAgentId, context,
                () -> runWithHistoryInternal(userMessage, priorHistory, startAgentId, skipEntryHook, null));
    }

    public RunResult continueWithMessages(List<ChatMessage> messages, String startAgentId,
                                          SwarmContext context, boolean fireEnterHook) {
        return withRunScope(startAgentId, context,
                () -> runWithHistoryInternal(null, null, startAgentId, !fireEnterHook, messages));
    }

    private <T> T withRunScope(String startAgentId, SwarmContext context, java.util.concurrent.Callable<T> action) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("SwarmRunner is already running");
        }
        activeListener = listener;
        try {
            SwarmContext effective = context != null ? context : new SwarmContext();
            return RunExecution.execute(
                    swarm,
                    startAgentId,
                    RunExecution.limits(options.maxTurns(), options.maxDelegateDepth()),
                    RunExecution.policy(options.maxRecoveryAttempts(), options.modelTimeout(), options.delegateStreaming()),
                    effective,
                    action);
        } finally {
            activeListener = null;
            running.set(false);
        }
    }

    private String runInternal(String userMessage) {
        return runWithHistoryInternal(userMessage, null, swarm.entryAgentId(), false, null).reply();
    }

    private RunResult runWithHistoryInternal(String userMessage, List<ChatMessage> priorHistory,
                                              String startAgentId, boolean skipEntryHook,
                                              List<ChatMessage> resumeMessages) {
        String currentAgentId = startAgentId;
        List<ChatMessage> currentMessages = resumeMessages;
        String activeAgentId = null;
        int recoveryAttempts = 0;

        RuntimeException failure = null;
        try {
            Agent entry = swarm.getAgent(currentAgentId);
            requireJAgent(entry);
            if (!skipEntryHook) {
                entry.onEnter(SwarmContext.current());
            }
            activeAgentId = currentAgentId;
            fireOnEnter(currentAgentId, skipEntryHook ? "RESUME" : "ENTRY");

            for (int turn = 0; turn < options.maxTurns(); turn++) {
                RunScopeChecks.beforeTurn(RunScope.current());
                Agent agent = swarm.getAgent(currentAgentId);
                JAgent runtimeAgent = requireJAgent(agent);

                List<ChatMessage> messages;
                if (currentMessages != null) {
                    messages = currentMessages;
                } else if (priorHistory != null && !priorHistory.isEmpty()) {
                    messages = new ArrayList<>(priorHistory);
                    messages.add(UserMessage.from(userMessage));
                    fireOnMessageHistoryUpdated(messages);
                } else {
                    messages = new ArrayList<>();
                    String instructions = agent.instructions();
                    if (instructions == null) {
                        throw new SwarmException("Agent '" + currentAgentId + "' has no instructions configured");
                    }
                    messages.add(SystemMessage.from(
                            SwarmContext.current().resolve(instructions)));
                    messages.add(UserMessage.from(userMessage));
                }

                List<ToolSpecification> tools = SwarmToolInjector.generateTools(
                        swarm, currentAgentId, runtimeAgent.externalTools());
                ExternalToolExecutor exec = ToolExecutionMerger.merge(runtimeAgent.toolExecutor(), swarmToolExecutor);

                ChatRequest request = ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(tools)
                        .build();

                AiMessage aiMessage = ChatInvoker.invoke(runtimeAgent, request, options.modelTimeout());

                if (!aiMessage.hasToolExecutionRequests()) {
                    messages.add(aiMessage);
                    fireOnMessageHistoryUpdated(messages);
                    activeAgentId = null;
                    agent.onExit(SwarmContext.current());
                    fireOnExit(currentAgentId);
                    String reply = aiMessage.text() != null ? aiMessage.text() : "";
                    fireOnRunComplete(reply);
                    return new RunResult(reply, currentAgentId, messages);
                }

                final String agentIdForTurn = currentAgentId;
                ToolCallBatchProcessor.Outcome outcome = ToolCallBatchProcessor.process(
                        filter,
                        agentIdForTurn,
                        messages,
                        aiMessage,
                        exec,
                        (agentId, call) -> fireOnToolCall(agentId, call.name(), call.arguments()),
                        (name, toolResult) -> fireOnToolResult(agentIdForTurn, name, truncate(toolResult, 200)));

                if (outcome instanceof ToolCallBatchProcessor.Outcome.Handoff h) {
                    activeAgentId = null;
                    agent.onExit(SwarmContext.current());
                    fireOnExit(currentAgentId);
                    fireOnHandoff(currentAgentId, h.targetAgentId());
                    currentAgentId = h.targetAgentId();
                    RunScope scope = RunScope.current();
                    if (scope != null) {
                        RunScope.bind(scope.withAgent(AgentId.of(currentAgentId)));
                    }
                    Agent to = swarm.getAgent(currentAgentId);
                    to.onEnter(SwarmContext.current());
                    activeAgentId = currentAgentId;
                    fireOnEnter(currentAgentId, "HANDOFF");
                    String targetInstructions = to.instructions();
                    targetInstructions = SwarmContext.current().resolve(targetInstructions);
                    List<ChatMessage> preserved = new ArrayList<>(messages);
                    preserved.set(0, SystemMessage.from(targetInstructions));
                    currentMessages = preserved;
                    fireOnMessageHistoryUpdated(currentMessages);
                    continue;
                }

                if (outcome instanceof ToolCallBatchProcessor.Outcome.Delegate d) {
                    fireOnDelegateStart(currentAgentId, d.targetAgentId(), d.task());
                    String result;
                    try {
                        result = filter.executeDelegate(
                                currentAgentId, d.targetAgentId(), d.task(), swarmToolExecutor, options);
                    } catch (RuntimeException e) {
                        ensureCanRecover(recoveryAttempts);
                        recoveryAttempts++;
                        result = "Jswarm recovery: delegate to '" + d.targetAgentId()
                                + "' failed. Please handle the user request directly. Error: " + e.getMessage();
                        fireOnRecovery(currentAgentId, "Delegate failed: " + e.getMessage());
                    }
                    fireOnDelegateEnd(currentAgentId, d.targetAgentId());
                    messages.add(ToolExecutionResultMessage.from(d.routingCall(), ProtocolLimits.truncateResult(result)));
                    fireOnMessageHistoryUpdated(messages);
                    currentMessages = messages;
                    userMessage = null;
                    continue;
                }

                fireOnMessageHistoryUpdated(messages);
                currentMessages = messages;
                userMessage = null;
            }

            throw new SwarmException("Max turns (" + options.maxTurns() + ") exceeded");
        } catch (RuntimeException e) {
            failure = e;
            fireOnRunFail(
                    activeAgentId != null ? activeAgentId : currentAgentId, e.getMessage());
            throw e;
        } finally {
            if (activeAgentId != null) {
                try {
                    swarm.getAgent(activeAgentId).onExit(SwarmContext.current());
                    fireOnExit(activeAgentId);
                } catch (RuntimeException hookEx) {
                    if (failure != null) {
                        failure.addSuppressed(hookEx);
                    } else {
                        throw hookEx;
                    }
                }
            }
        }
    }

    private void fireOnEnter(String agentId, String source) {
        if (activeListener != null) try { activeListener.onAgentEnter(agentId, source); } catch (RuntimeException ignored) {}
    }

    private void fireOnExit(String agentId) {
        if (activeListener != null) try { activeListener.onAgentExit(agentId); } catch (RuntimeException ignored) {}
    }

    private void fireOnToolCall(String agentId, String toolName, String args) {
        if (activeListener != null) try { activeListener.onToolCall(agentId, toolName, args); } catch (RuntimeException ignored) {}
    }

    private void fireOnToolResult(String agentId, String toolName, String result) {
        if (activeListener != null) try { activeListener.onToolResult(agentId, toolName, result); } catch (RuntimeException ignored) {}
    }

    private void fireOnHandoff(String from, String to) {
        if (activeListener != null) try { activeListener.onHandoff(from, to); } catch (RuntimeException ignored) {}
    }

    private void fireOnDelegateStart(String parent, String target, String task) {
        if (activeListener != null) try { activeListener.onDelegateStart(parent, target, task); } catch (RuntimeException ignored) {}
    }

    private void fireOnDelegateEnd(String parent, String target) {
        if (activeListener != null) try { activeListener.onDelegateEnd(parent, target); } catch (RuntimeException ignored) {}
    }

    private void fireOnRecovery(String agentId, String reason) {
        if (activeListener != null) try { activeListener.onRecovery(agentId, reason); } catch (RuntimeException ignored) {}
    }

    private void fireOnRunComplete(String finalText) {
        if (activeListener != null) try { activeListener.onRunComplete(finalText); } catch (RuntimeException ignored) {}
    }

    private void fireOnRunFail(String agentId, String error) {
        if (activeListener != null) try { activeListener.onRunFail(agentId, error); } catch (RuntimeException ignored) {}
    }

    private void fireOnMessageHistoryUpdated(List<ChatMessage> messages) {
        if (activeListener != null) try { activeListener.onMessageHistoryUpdated(messages); } catch (RuntimeException ignored) {}
    }

    public void runStreaming(String userMessage, SwarmContext context, Consumer<SwarmEvent> sink) {
        withRunScope(swarm.entryAgentId(), context, () -> {
            runStreamingInternal(userMessage, sink);
            return null;
        });
    }

    private void runStreamingInternal(String userMessage, Consumer<SwarmEvent> sink) {
        String currentAgentId = swarm.entryAgentId();
        List<ChatMessage> currentMessages = null;
        String activeAgentId = null;
        int recoveryAttempts = 0;
        SwarmContext ctx = SwarmContext.current();

        RuntimeException failure = null;
        try {
            Agent entry = swarm.getAgent(currentAgentId);
            requireJAgent(entry);
            entry.onEnter(ctx);
            activeAgentId = currentAgentId;
            sink.accept(new SwarmEvent.RunStarted("", currentAgentId));
            sink.accept(new SwarmEvent.AgentEnter(currentAgentId, "ENTRY"));

            for (int turn = 0; turn < options.maxTurns(); turn++) {
                RunScopeChecks.beforeTurn(RunScope.current());
                Agent agent = swarm.getAgent(currentAgentId);
                JAgent runtimeAgent = requireJAgent(agent);

                List<ChatMessage> messages;
                if (currentMessages != null) {
                    messages = currentMessages;
                } else {
                    messages = new ArrayList<>();
                    String instructions = agent.instructions();
                    if (instructions == null) {
                        throw new SwarmException("Agent '" + currentAgentId + "' has no instructions configured");
                    }
                    messages.add(SystemMessage.from(ctx.resolve(instructions)));
                    messages.add(UserMessage.from(userMessage));
                }

                List<ToolSpecification> tools = SwarmToolInjector.generateTools(
                        swarm, currentAgentId, runtimeAgent.externalTools());
                ExternalToolExecutor exec = ToolExecutionMerger.merge(runtimeAgent.toolExecutor(), swarmToolExecutor);

                ChatRequest request = ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(tools)
                        .build();

                AiMessage aiMessage = StreamingChatInvoker.stream(runtimeAgent, request, ctx,
                        options.modelTimeout(), sink);

                if (!aiMessage.hasToolExecutionRequests()) {
                    activeAgentId = null;
                    agent.onExit(ctx);
                    sink.accept(new SwarmEvent.AgentExit(currentAgentId));
                    sink.accept(new SwarmEvent.RunCompleted(aiMessage.text()));
                    return;
                }

                final String agentIdForTurn = currentAgentId;
                ToolCallBatchProcessor.Outcome outcome = ToolCallBatchProcessor.process(
                        filter,
                        agentIdForTurn,
                        messages,
                        aiMessage,
                        exec,
                        (agentId, call) -> sink.accept(new SwarmEvent.ToolCall(agentId, call.name(), call.arguments())),
                        (name, toolResult) -> sink.accept(new SwarmEvent.ToolResult(agentIdForTurn, name, truncate(toolResult, 200))));

                if (outcome instanceof ToolCallBatchProcessor.Outcome.Handoff h) {
                    activeAgentId = null;
                    agent.onExit(ctx);
                    sink.accept(new SwarmEvent.AgentExit(currentAgentId));
                    sink.accept(new SwarmEvent.Handoff(currentAgentId, h.targetAgentId()));
                    currentAgentId = h.targetAgentId();
                    Agent to = swarm.getAgent(currentAgentId);
                    to.onEnter(ctx);
                    activeAgentId = currentAgentId;
                    sink.accept(new SwarmEvent.AgentEnter(currentAgentId, "HANDOFF"));
                    String targetInstructions = ctx.resolve(to.instructions());
                    List<ChatMessage> preserved = new ArrayList<>(messages);
                    preserved.set(0, SystemMessage.from(targetInstructions));
                    currentMessages = preserved;
                    continue;
                }

                if (outcome instanceof ToolCallBatchProcessor.Outcome.Delegate d) {
                    sink.accept(new SwarmEvent.DelegateStarted(currentAgentId, d.targetAgentId(), d.task()));
                    String result;
                    try {
                        if (options.delegateStreaming()) {
                            result = filter.executeDelegateStreaming(
                                    currentAgentId, d.targetAgentId(), d.task(), swarmToolExecutor, options, sink);
                        } else {
                            result = filter.executeDelegate(
                                    currentAgentId, d.targetAgentId(), d.task(), swarmToolExecutor, options);
                        }
                    } catch (RuntimeException e) {
                        ensureCanRecover(recoveryAttempts);
                        recoveryAttempts++;
                        result = "Jswarm recovery: delegate to '" + d.targetAgentId()
                                + "' failed. Please handle the user request directly. Error: " + e.getMessage();
                        sink.accept(new SwarmEvent.RecoveryTriggered(currentAgentId,
                                "Delegate failed: " + e.getMessage()));
                    }
                    sink.accept(new SwarmEvent.DelegateFinished(currentAgentId, d.targetAgentId()));
                    messages.add(ToolExecutionResultMessage.from(d.routingCall(), ProtocolLimits.truncateResult(result)));
                    currentMessages = messages;
                    userMessage = null;
                    continue;
                }

                currentMessages = messages;
                userMessage = null;
            }

            throw new SwarmException("Max turns (" + options.maxTurns() + ") exceeded");
        } catch (RuntimeException e) {
            failure = e;
            sink.accept(new SwarmEvent.RunFailed(
                    activeAgentId != null ? activeAgentId : currentAgentId, e.getMessage()));
            throw e;
        } finally {
            if (activeAgentId != null) {
                try {
                    swarm.getAgent(activeAgentId).onExit(ctx);
                    sink.accept(new SwarmEvent.AgentExit(activeAgentId));
                } catch (RuntimeException hookEx) {
                    if (failure != null) {
                        failure.addSuppressed(hookEx);
                    } else {
                        throw hookEx;
                    }
                }
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private void ensureCanRecover(int recoveryAttempts) {
        if (recoveryAttempts >= options.maxRecoveryAttempts()) {
            throw new SwarmException("Recovery attempts exceeded: " + options.maxRecoveryAttempts());
        }
    }

    private void recoverToolCall(
            List<ChatMessage> messages,
            AiMessage aiMessage,
            ToolExecutionRequest toolCall,
            String result) {
        messages.add(aiMessage);
        messages.add(ToolExecutionResultMessage.from(toolCall, result));
    }

    private JAgent requireJAgent(Agent agent) {
        if (agent instanceof JAgent jAgent) {
            return jAgent;
        }
        throw new SwarmException("SwarmRunner requires JAgent, but got: " + agent.getClass().getName());
    }
}
