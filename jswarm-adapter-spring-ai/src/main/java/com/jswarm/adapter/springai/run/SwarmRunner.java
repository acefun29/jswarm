package com.jswarm.adapter.springai.run;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.adapter.springai.JAgent;
import com.jswarm.adapter.springai.ToolProvider;
import com.jswarm.adapter.springai.filter.FilterDecision;
import com.jswarm.adapter.springai.filter.SwarmFilter;
import com.jswarm.adapter.springai.invoke.ChatInvoker;
import com.jswarm.adapter.springai.tool.SwarmToolInjector;
import com.jswarm.adapter.springai.tool.ToolExecutionMerger;
import com.jswarm.core.Agent;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

public final class SwarmRunner {

    private final Swarm swarm;
    private final SwarmRunOptions options;
    private final SwarmFilter filter;
    private final ExternalToolExecutor swarmToolExecutor;
    private SwarmRunListener listener;

    private SwarmRunner(Swarm swarm, SwarmRunOptions options, ExternalToolExecutor swarmToolExecutor,
                        SwarmRunListener listener) {
        this.swarm = swarm;
        this.options = options;
        this.filter = new SwarmFilter(swarm);
        this.swarmToolExecutor = swarmToolExecutor;
        this.listener = listener;
    }

    public void setListener(SwarmRunListener listener) {
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

    public static SwarmRunner create(Swarm swarm, int maxTurns, ExternalToolExecutor swarmToolExecutor) {
        return new SwarmRunner(swarm,
                SwarmRunOptions.builder().maxTurns(maxTurns).build(), swarmToolExecutor, null);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options) {
        return new SwarmRunner(swarm, options, null, null);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options, ToolProvider toolProvider) {
        return create(swarm, options, (ExternalToolExecutor) toolProvider);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options,
                                      ExternalToolExecutor swarmToolExecutor) {
        return new SwarmRunner(swarm, options, swarmToolExecutor, null);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options,
                                      ExternalToolExecutor swarmToolExecutor, SwarmRunListener listener) {
        return new SwarmRunner(swarm, options, swarmToolExecutor, listener);
    }

    public String run(String userMessage) {
        return run(userMessage, new SwarmContext());
    }

    public String run(String userMessage, SwarmContext context) {
        SwarmContext previous = SwarmContext.current();
        SwarmContext.set(context);
        try {
            return runInternal(userMessage);
        } finally {
            if (previous != null) {
                SwarmContext.set(previous);
            } else {
                SwarmContext.clear();
            }
        }
    }

    public record RunResult(String reply, String currentAgentId, List<Message> updatedHistory) {
    }

    public RunResult runWithHistory(String userMessage, List<Message> priorHistory,
                                    String startAgentId, SwarmContext context, boolean skipEntryHook) {
        SwarmContext previous = SwarmContext.current();
        SwarmContext.set(context);
        try {
            return runWithHistoryInternal(userMessage, priorHistory, startAgentId, skipEntryHook);
        } finally {
            if (previous != null) {
                SwarmContext.set(previous);
            } else {
                SwarmContext.clear();
            }
        }
    }

    private String runInternal(String userMessage) {
        return runWithHistoryInternal(userMessage, null, swarm.entryAgentId(), false).reply();
    }

    private RunResult runWithHistoryInternal(String userMessage, List<Message> priorHistory,
                                              String startAgentId, boolean skipEntryHook) {
        String currentAgentId = startAgentId;
        List<Message> currentMessages = null;
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
                Agent agent = swarm.getAgent(currentAgentId);
                JAgent runtimeAgent = requireJAgent(agent);

                List<Message> messages;
                if (currentMessages != null) {
                    messages = currentMessages;
                } else if (priorHistory != null && !priorHistory.isEmpty()) {
                    messages = new ArrayList<>(priorHistory);
                    messages.add(new UserMessage(userMessage));
                    fireOnMessageHistoryUpdated(messages);
                } else {
                    messages = new ArrayList<>();
                    String instructions = agent.instructions();
                    if (instructions == null) {
                        throw new SwarmException("Agent '" + currentAgentId
                                + "' has no instructions configured");
                    }
                    messages.add(new SystemMessage(
                            SwarmContext.current().resolve(instructions)));
                    messages.add(new UserMessage(userMessage));
                }

                List<ToolCallback> tools = SwarmToolInjector.generateTools(
                        swarm, currentAgentId, runtimeAgent.externalTools());
                ExternalToolExecutor exec = ToolExecutionMerger.merge(
                        runtimeAgent.toolExecutor(), swarmToolExecutor);

                ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder()
                        .toolCallbacks(tools)
                        .build();
                Prompt prompt = new Prompt(messages, chatOptions);

                ChatResponse chatResponse = ChatInvoker.invoke(runtimeAgent, prompt, options.modelTimeout());

                if (chatResponse == null || chatResponse.getResult() == null) {
                    throw new SwarmException("LLM returned empty response");
                }
                AssistantMessage assistantMsg = chatResponse.getResult().getOutput();

                if (!chatResponse.hasToolCalls()) {
                    messages.add(assistantMsg);
                    fireOnMessageHistoryUpdated(messages);
                    activeAgentId = null;
                    agent.onExit(SwarmContext.current());
                    fireOnExit(currentAgentId);
                    String reply = assistantMsg.getText() != null ? assistantMsg.getText() : "";
                    fireOnRunComplete(reply);
                    return new RunResult(reply, currentAgentId, messages);
                }

                AssistantMessage.ToolCall toolCall = assistantMsg.getToolCalls().get(0);
                messages.add(assistantMsg);

                for (int i = 1; i < assistantMsg.getToolCalls().size(); i++) {
                    AssistantMessage.ToolCall discarded = assistantMsg.getToolCalls().get(i);
                    messages.add(ToolResponseMessage.builder().responses(List.of(
                            new ToolResponseMessage.ToolResponse(discarded.id(), discarded.name(),
                                    "Jswarm: only one tool call per turn is supported. Please retry."))).build());
                }

                FilterDecision decision;
                try {
                    decision = filter.decide(toolCall);
                } catch (SwarmException e) {
                    ensureCanRecover(recoveryAttempts);
                    recoveryAttempts++;
                    fireOnRecovery(currentAgentId, "Invalid tool args: " + e.getMessage());
                    recoverToolCall(messages, assistantMsg, toolCall,
                            "Jswarm recovery: tool call arguments are invalid. "
                                    + "Please call the tool again using valid JSON arguments. Error: "
                                    + e.getMessage());
                    fireOnMessageHistoryUpdated(messages);
                    currentMessages = messages;
                    userMessage = null;
                    continue;
                }

                if (decision instanceof FilterDecision.Handoff h) {
                    activeAgentId = null;
                    agent.onExit(SwarmContext.current());
                    fireOnExit(currentAgentId);
                    fireOnHandoff(currentAgentId, h.targetAgentId());
                    currentAgentId = h.targetAgentId();
                    Agent to = swarm.getAgent(currentAgentId);
                    to.onEnter(SwarmContext.current());
                    activeAgentId = currentAgentId;
                    fireOnEnter(currentAgentId, "HANDOFF");
                    String targetInstructions = to.instructions();
                    if (targetInstructions == null || targetInstructions.isBlank()) {
                        throw new SwarmException("Agent '" + currentAgentId
                                + "' has no instructions configured");
                    }
                    targetInstructions = SwarmContext.current().resolve(targetInstructions);
                    List<Message> preserved = new ArrayList<>(messages);
                    preserved.set(0, new SystemMessage(targetInstructions));
                    currentMessages = preserved;
                    fireOnMessageHistoryUpdated(currentMessages);
                    continue;
                }

                if (decision instanceof FilterDecision.Delegate d) {
                    fireOnDelegateStart(currentAgentId, d.targetAgentId(), d.task());
                    String result;
                    try {
                        result = filter.executeDelegate(d.targetAgentId(), d.task(),
                                swarmToolExecutor, options);
                    } catch (RuntimeException e) {
                        ensureCanRecover(recoveryAttempts);
                        recoveryAttempts++;
                        result = "Jswarm recovery: delegate to '" + d.targetAgentId()
                                + "' failed. Please handle the user request directly. Error: "
                                + e.getMessage();
                        fireOnRecovery(currentAgentId, "Delegate failed: " + e.getMessage());
                    }
                    fireOnDelegateEnd(currentAgentId, d.targetAgentId());
                    messages.add(assistantMsg);
                    messages.add(ToolResponseMessage.builder().responses(List.of(
                            new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), result))).build());
                    fireOnMessageHistoryUpdated(messages);
                    currentMessages = messages;
                    userMessage = null;
                    continue;
                }

                fireOnToolCall(currentAgentId, toolCall.name(), toolCall.arguments());
                String result;
                try {
                    result = exec.execute(toolCall);
                } catch (RuntimeException e) {
                    ensureCanRecover(recoveryAttempts);
                    recoveryAttempts++;
                    result = "Jswarm recovery: tool '" + toolCall.name()
                            + "' failed. Please answer directly or try another available tool. Error: "
                            + e.getMessage();
                    fireOnRecovery(currentAgentId,
                            "Tool '" + toolCall.name() + "' failed: " + e.getMessage());
                }
                fireOnToolResult(currentAgentId, toolCall.name(), truncate(result, 200));
                messages.add(assistantMsg);
                messages.add(ToolResponseMessage.builder().responses(List.of(
                        new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), result))).build());
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

    private void recoverToolCall(List<Message> messages, AssistantMessage aiMsg,
                                  AssistantMessage.ToolCall toolCall, String recoveryText) {
        messages.add(aiMsg);
        messages.add(ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), recoveryText))).build());
    }

    private void ensureCanRecover(int recoveryAttempts) {
        if (recoveryAttempts >= options.maxRecoveryAttempts()) {
            throw new SwarmException("Max recovery attempts (" + options.maxRecoveryAttempts() + ") exceeded");
        }
    }

    private JAgent requireJAgent(Agent agent) {
        if (agent instanceof JAgent j) {
            return j;
        }
        throw new SwarmException("Agent '" + agent.id() + "' is not a JAgent");
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private void fireOnEnter(String agentId, String source) {
        if (listener != null) try { listener.onAgentEnter(agentId, source); } catch (RuntimeException ignored) {}
    }

    private void fireOnExit(String agentId) {
        if (listener != null) try { listener.onAgentExit(agentId); } catch (RuntimeException ignored) {}
    }

    private void fireOnToolCall(String agentId, String toolName, String args) {
        if (listener != null) try { listener.onToolCall(agentId, toolName, args); } catch (RuntimeException ignored) {}
    }

    private void fireOnToolResult(String agentId, String toolName, String result) {
        if (listener != null) try { listener.onToolResult(agentId, toolName, result); } catch (RuntimeException ignored) {}
    }

    private void fireOnHandoff(String from, String to) {
        if (listener != null) try { listener.onHandoff(from, to); } catch (RuntimeException ignored) {}
    }

    private void fireOnDelegateStart(String parent, String target, String task) {
        if (listener != null) try { listener.onDelegateStart(parent, target, task); } catch (RuntimeException ignored) {}
    }

    private void fireOnDelegateEnd(String parent, String target) {
        if (listener != null) try { listener.onDelegateEnd(parent, target); } catch (RuntimeException ignored) {}
    }

    private void fireOnRecovery(String agentId, String reason) {
        if (listener != null) try { listener.onRecovery(agentId, reason); } catch (RuntimeException ignored) {}
    }

    private void fireOnRunComplete(String finalText) {
        if (listener != null) try { listener.onRunComplete(finalText); } catch (RuntimeException ignored) {}
    }

    private void fireOnRunFail(String agentId, String error) {
        if (listener != null) try { listener.onRunFail(agentId, error); } catch (RuntimeException ignored) {}
    }

    private void fireOnMessageHistoryUpdated(List<Message> messages) {
        if (listener != null) try { listener.onMessageHistoryUpdated(messages); } catch (RuntimeException ignored) {}
    }
}
