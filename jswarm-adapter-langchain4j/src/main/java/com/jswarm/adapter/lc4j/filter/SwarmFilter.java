package com.jswarm.adapter.lc4j.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.JAgent;
import com.jswarm.adapter.lc4j.invoke.ChatInvoker;
import com.jswarm.adapter.lc4j.invoke.StreamingChatInvoker;
import com.jswarm.adapter.lc4j.run.SwarmRunOptions;
import com.jswarm.adapter.lc4j.tool.SwarmToolInjector;
import com.jswarm.adapter.lc4j.tool.ToolExecutionMerger;
import com.jswarm.core.Agent;
import com.jswarm.core.ProtocolLimits;
import com.jswarm.core.RouteAuthorization;
import com.jswarm.core.RouteDeniedException;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import com.jswarm.core.SwarmException;
import com.jswarm.spi.bridge.SwarmContextBridge;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class SwarmFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Swarm swarm;

    public SwarmFilter(Swarm swarm) {
        this.swarm = swarm;
    }

    public FilterDecision decide(String sourceAgentId, ToolExecutionRequest toolCall) {
        String toolName = toolCall.name();
        if ("handoff".equals(toolName)) {
            String targetId = extractArg(toolCall, "target");
            ProtocolLimits.validateRouteTarget(targetId);
            try {
                RouteAuthorization.authorizeHandoff(swarm, sourceAgentId, targetId);
            } catch (RouteDeniedException e) {
                return FilterDecision.reject(e.reason().name(), e.modelSafeMessage());
            }
            return FilterDecision.handoff(targetId);
        }
        if ("delegate".equals(toolName)) {
            String targetId = extractArg(toolCall, "target");
            String task = extractArg(toolCall, "task");
            ProtocolLimits.validateRouteTarget(targetId);
            ProtocolLimits.validateDelegateTask(task);
            try {
                RouteAuthorization.authorizeDelegate(swarm, sourceAgentId, targetId);
            } catch (RouteDeniedException e) {
                return FilterDecision.reject(e.reason().name(), e.modelSafeMessage());
            }
            return FilterDecision.delegate(targetId, task);
        }
        return FilterDecision.external();
    }

    public String executeDelegate(String sourceAgentId, String targetId, String task,
            ExternalToolExecutor swarmFallback, SwarmRunOptions options) {
        RouteAuthorization.authorizeDelegate(swarm, sourceAgentId, targetId);
        JAgent target = requireJAgent(swarm.getAgent(targetId));
        RunScope parentScope = RunScope.current();
        SwarmContextBridge.ScopeBinding delegateBinding = null;
        if (parentScope != null) {
            RunScope delegateScope = RunScopeChecks.beginDelegate(parentScope, targetId);
            delegateBinding = SwarmContextBridge.bind(delegateScope);
        }
        SwarmContext context = SwarmContext.current();

        List<ChatMessage> subMessages = new ArrayList<>();
        String instructions;
        if (context != null) {
            target.onDelegateEnter(context, task);
            instructions = context.resolve(target.instructions());
        } else {
            instructions = target.instructions();
        }
        subMessages.add(SystemMessage.from(instructions));
        subMessages.add(UserMessage.from(task));

        List<ToolSpecification> subTools =
                SwarmToolInjector.generateExternalToolsOnly(target.externalTools());
        ExternalToolExecutor subExec = ToolExecutionMerger.merge(target.toolExecutor(), swarmFallback);

        try {
            for (int turn = 0; turn < options.maxTurns(); turn++) {
                RunScopeChecks.beforeTurn(RunScope.current());
                ChatRequest subRequest = ChatRequest.builder()
                        .messages(subMessages)
                        .toolSpecifications(subTools)
                        .build();

                AiMessage aiMessage = ChatInvoker.invoke(target, subRequest, options.modelTimeout());

                if (!aiMessage.hasToolExecutionRequests()) {
                    String result = aiMessage.text();
                    if (context != null) {
                        target.onDelegateExit(context, task, result);
                    }
                    return result;
                }

                if (turn == options.maxTurns() - 1) {
                    ToolExecutionRequest toolCall = aiMessage.toolExecutionRequests().get(0);
                    String warning = "Jswarm: maximum turns (" + options.maxTurns()
                            + ") exceeded. Please summarize what you have gathered so far.";
                    subMessages.add(aiMessage);
                    subMessages.add(ToolExecutionResultMessage.from(toolCall, warning));

                    AiMessage finalAi = ChatInvoker.invoke(target,
                            ChatRequest.builder().messages(subMessages).toolSpecifications(subTools).build(),
                            options.modelTimeout());

                    String result;
                    if (finalAi.hasToolExecutionRequests()) {
                        result = "Jswarm: delegate max turns exceeded. The sub-agent could not complete within "
                                + options.maxTurns() + " turns.";
                    } else {
                        result = finalAi.text();
                    }
                    if (context != null) {
                        target.onDelegateExit(context, task, result);
                    }
                    return result;
                }

                ToolCallBatchProcessor.processDelegateTurn(this, targetId, subMessages, aiMessage, subExec);
            }

            throw new SwarmException("Delegate max turns cannot be zero");
        } catch (RuntimeException e) {
            if (context != null) {
                try {
                    target.onDelegateExit(context, task, null);
                } catch (RuntimeException ex) {
                    e.addSuppressed(ex);
                }
            }
            throw e;
        } finally {
            if (delegateBinding != null) {
                SwarmContextBridge.restore(delegateBinding);
            }
        }
    }

    public static String extractArg(ToolExecutionRequest toolCall, String fieldName) {
        String arguments = toolCall.arguments();
        if (arguments == null || arguments.isBlank()) {
            throw new SwarmException("Tool call arguments are empty");
        }
        try {
            JsonNode node = MAPPER.readTree(arguments);
            JsonNode field = node.get(fieldName);
            if (field == null) {
                throw new SwarmException("No '" + fieldName + "' field found in tool call arguments");
            }
            if (!field.isTextual()) {
                throw new SwarmException("Field '" + fieldName + "' must be a string, got: " + field.getNodeType());
            }
            return field.textValue();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new SwarmException("Failed to parse tool call arguments: " + e.getMessage(), e);
        }
    }

    public String executeDelegateStreaming(String sourceAgentId, String targetId, String task,
            ExternalToolExecutor swarmFallback, SwarmRunOptions options,
            Consumer<SwarmEvent> sink) {
        RouteAuthorization.authorizeDelegate(swarm, sourceAgentId, targetId);
        JAgent target = requireJAgent(swarm.getAgent(targetId));
        RunScope parentScope = RunScope.current();
        SwarmContextBridge.ScopeBinding delegateBinding = null;
        if (parentScope != null) {
            RunScope delegateScope = RunScopeChecks.beginDelegate(parentScope, targetId);
            delegateBinding = SwarmContextBridge.bind(delegateScope);
        }
        SwarmContext context = SwarmContext.current();

        List<ChatMessage> subMessages = new ArrayList<>();
        String instructions;
        if (context != null) {
            target.onDelegateEnter(context, task);
            instructions = context.resolve(target.instructions());
        } else {
            instructions = target.instructions();
        }
        subMessages.add(SystemMessage.from(instructions));
        subMessages.add(UserMessage.from(task));

        List<ToolSpecification> subTools =
                SwarmToolInjector.generateExternalToolsOnly(target.externalTools());
        ExternalToolExecutor subExec = ToolExecutionMerger.merge(target.toolExecutor(), swarmFallback);

        try {
            for (int turn = 0; turn < options.maxTurns(); turn++) {
                RunScopeChecks.beforeTurn(RunScope.current());
                ChatRequest subRequest = ChatRequest.builder()
                        .messages(subMessages)
                        .toolSpecifications(subTools)
                        .build();

                AiMessage aiMessage = StreamingChatInvoker.stream(target, subRequest, context,
                        options.modelTimeout(), sink);

                if (!aiMessage.hasToolExecutionRequests()) {
                    String result = aiMessage.text();
                    if (context != null) {
                        target.onDelegateExit(context, task, result);
                    }
                    return result;
                }

                if (turn == options.maxTurns() - 1) {
                    ToolExecutionRequest toolCall = aiMessage.toolExecutionRequests().get(0);
                    String warning = "Jswarm: maximum turns (" + options.maxTurns()
                            + ") exceeded. Please summarize what you have gathered so far.";
                    subMessages.add(aiMessage);
                    subMessages.add(ToolExecutionResultMessage.from(toolCall, warning));

                    AiMessage finalAi = StreamingChatInvoker.stream(target,
                            ChatRequest.builder().messages(subMessages).toolSpecifications(subTools).build(),
                            context, options.modelTimeout(), sink);

                    String result;
                    if (finalAi.hasToolExecutionRequests()) {
                        result = "Jswarm: delegate max turns exceeded. The sub-agent could not complete within "
                                + options.maxTurns() + " turns.";
                    } else {
                        result = finalAi.text();
                    }
                    if (context != null) {
                        target.onDelegateExit(context, task, result);
                    }
                    return result;
                }

                ToolCallBatchProcessor.processDelegateTurn(this, targetId, subMessages, aiMessage, subExec);
            }

            throw new SwarmException("Delegate max turns cannot be zero");
        } catch (RuntimeException e) {
            if (context != null) {
                try {
                    target.onDelegateExit(context, task, null);
                } catch (RuntimeException ex) {
                    e.addSuppressed(ex);
                }
            }
            throw e;
        } finally {
            if (delegateBinding != null) {
                SwarmContextBridge.restore(delegateBinding);
            }
        }
    }

    private JAgent requireJAgent(Agent agent) {
        if (agent instanceof JAgent jAgent) {
            return jAgent;
        }
        throw new SwarmException("SwarmFilter requires JAgent, but got: " + agent.getClass().getName());
    }
}
