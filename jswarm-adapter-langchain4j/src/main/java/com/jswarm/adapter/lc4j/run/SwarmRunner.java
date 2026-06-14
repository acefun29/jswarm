package com.jswarm.adapter.lc4j.run;

import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.adapter.lc4j.ToolProvider;
import com.jswarm.core.Agent;
import com.jswarm.core.SwarmContext;
import com.jswarm.adapter.lc4j.filter.FilterDecision;
import com.jswarm.adapter.lc4j.filter.SwarmFilter;
import com.jswarm.adapter.lc4j.invoke.ChatInvoker;
import com.jswarm.adapter.lc4j.tool.SwarmToolInjector;
import com.jswarm.adapter.lc4j.tool.ToolExecutionMerger;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmException;
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

public final class SwarmRunner {

    private final Swarm swarm;
    private final SwarmRunOptions options;
    private final SwarmFilter filter;
    private final ExternalToolExecutor swarmToolExecutor;

    private SwarmRunner(Swarm swarm, SwarmRunOptions options, ExternalToolExecutor swarmToolExecutor) {
        this.swarm = swarm;
        this.options = options;
        this.filter = new SwarmFilter(swarm);
        this.swarmToolExecutor = swarmToolExecutor;
    }

    public static SwarmRunner create(Swarm swarm) {
        return new SwarmRunner(swarm, SwarmRunOptions.defaults(), null);
    }

    public static SwarmRunner create(Swarm swarm, int maxTurns) {
        return new SwarmRunner(swarm,
                SwarmRunOptions.builder().maxTurns(maxTurns).build(),
                null);
    }

    public static SwarmRunner create(Swarm swarm, int maxTurns, ToolProvider toolProvider) {
        return create(swarm, maxTurns, (ExternalToolExecutor) toolProvider);
    }

    public static SwarmRunner create(Swarm swarm, int maxTurns, ExternalToolExecutor swarmToolExecutor) {
        return new SwarmRunner(swarm,
                SwarmRunOptions.builder().maxTurns(maxTurns).build(),
                swarmToolExecutor);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options) {
        return new SwarmRunner(swarm, options, null);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options, ToolProvider toolProvider) {
        return create(swarm, options, (ExternalToolExecutor) toolProvider);
    }

    public static SwarmRunner create(Swarm swarm, SwarmRunOptions options, ExternalToolExecutor swarmToolExecutor) {
        return new SwarmRunner(swarm, options, swarmToolExecutor);
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

    private String runInternal(String userMessage) {
        String currentAgentId = swarm.entryAgentId();
        List<ChatMessage> currentMessages = null;
        String activeAgentId = null;
        int recoveryAttempts = 0;

        RuntimeException failure = null;
        try {
            Agent entry = swarm.getAgent(currentAgentId);
            requireJAgent(entry);
            entry.onEnter(SwarmContext.current());
            activeAgentId = currentAgentId;

            for (int turn = 0; turn < options.maxTurns(); turn++) {
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
                    activeAgentId = null;
                    agent.onExit(SwarmContext.current());
                    return aiMessage.text();
                }

                ToolExecutionRequest toolCall = aiMessage.toolExecutionRequests().get(0);

                FilterDecision decision;
                try {
                    decision = filter.decide(toolCall);
                } catch (SwarmException e) {
                    ensureCanRecover(recoveryAttempts);
                    recoveryAttempts++;
                    recoverToolCall(messages, aiMessage, toolCall,
                            "Jswarm recovery: tool call arguments are invalid. Please call the tool again using valid JSON arguments. Error: " + e.getMessage());
                    currentMessages = messages;
                    userMessage = null;
                    continue;
                }

                if (decision instanceof FilterDecision.Handoff h) {
                    activeAgentId = null;
                    agent.onExit(SwarmContext.current());
                    currentAgentId = h.targetAgentId();
                    Agent to = swarm.getAgent(currentAgentId);
                    to.onEnter(SwarmContext.current());
                    activeAgentId = currentAgentId;
                    String targetInstructions = to.instructions();
                    targetInstructions = SwarmContext.current().resolve(targetInstructions);
                    List<ChatMessage> preserved = new ArrayList<>(messages);
                    preserved.set(0, SystemMessage.from(targetInstructions));
                    currentMessages = preserved;
                    continue;
                }

                if (decision instanceof FilterDecision.Delegate d) {
                    String result;
                    try {
                        result = filter.executeDelegate(d.targetAgentId(), d.task(), swarmToolExecutor, options);
                    } catch (RuntimeException e) {
                        ensureCanRecover(recoveryAttempts);
                        recoveryAttempts++;
                        result = "Jswarm recovery: delegate to '" + d.targetAgentId()
                                + "' failed. Please handle the user request directly. Error: " + e.getMessage();
                    }
                    messages.add(aiMessage);
                    messages.add(ToolExecutionResultMessage.from(toolCall, result));
                    currentMessages = messages;
                    userMessage = null;
                    continue;
                }

                String result;
                try {
                    result = exec.execute(toolCall);
                } catch (RuntimeException e) {
                    ensureCanRecover(recoveryAttempts);
                    recoveryAttempts++;
                    result = "Jswarm recovery: tool '" + toolCall.name()
                            + "' failed. Please answer directly or try another available tool. Error: " + e.getMessage();
                }
                messages.add(aiMessage);
                messages.add(ToolExecutionResultMessage.from(toolCall, result));
                currentMessages = messages;
                userMessage = null;
            }

            throw new SwarmException("Max turns (" + options.maxTurns() + ") exceeded");
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            if (activeAgentId != null) {
                try {
                    swarm.getAgent(activeAgentId).onExit(SwarmContext.current());
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
