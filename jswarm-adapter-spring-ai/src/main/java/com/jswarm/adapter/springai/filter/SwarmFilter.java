// Spring AI 路由兼容包装
package com.jswarm.adapter.springai.filter;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.adapter.springai.runtime.SpringAiRuntimeProvider;
import com.jswarm.adapter.springai.run.SwarmRunOptions;
import com.jswarm.core.ProtocolLimits;
import com.jswarm.core.RouteAuthorization;
import com.jswarm.core.RouteDeniedException;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmEvent;
import com.jswarm.core.SwarmException;
import com.jswarm.runtime.run.RunEngine;
import com.jswarm.spi.run.RunExecution;
import com.jswarm.spi.run.RunScope;
import org.springframework.ai.chat.messages.AssistantMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Consumer;

public final class SwarmFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Swarm swarm;

    public SwarmFilter(Swarm swarm) {
        this.swarm = swarm;
    }

    public FilterDecision decide(String sourceAgentId, AssistantMessage.ToolCall toolCall) {
        String toolName = toolCall.name();
        if ("handoff".equals(toolName)) {
            String targetId = extractArg(toolCall.arguments(), "target");
            ProtocolLimits.validateRouteTarget(targetId);
            try {
                RouteAuthorization.authorizeHandoff(swarm, sourceAgentId, targetId);
            } catch (RouteDeniedException e) {
                return FilterDecision.reject(e.reason().name(), e.modelSafeMessage());
            }
            return FilterDecision.handoff(targetId);
        }
        if ("delegate".equals(toolName)) {
            String targetId = extractArg(toolCall.arguments(), "target");
            String task = extractArg(toolCall.arguments(), "task");
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

    public String executeDelegate(
            String sourceAgentId,
            String targetId,
            String task,
            ExternalToolExecutor swarmFallback,
            SwarmRunOptions options) {
        return executeDelegateInternal(
                sourceAgentId, targetId, task, swarmFallback, options, false, event -> {
                });
    }

    public String executeDelegateStreaming(
            String sourceAgentId,
            String targetId,
            String task,
            ExternalToolExecutor swarmFallback,
            SwarmRunOptions options,
            Consumer<SwarmEvent> sink) {
        return executeDelegateInternal(
                sourceAgentId, targetId, task, swarmFallback, options, true,
                sink != null ? sink : event -> {
                });
    }

    private String executeDelegateInternal(
            String sourceAgentId,
            String targetId,
            String task,
            ExternalToolExecutor swarmFallback,
            SwarmRunOptions options,
            boolean streaming,
            Consumer<SwarmEvent> sink) {
        SwarmRunOptions effective = options != null ? options : SwarmRunOptions.defaults();
        RunEngine engine = RunEngine.create(
                swarm,
                new SpringAiRuntimeProvider(effective, swarmFallback, sink));
        RunScope current = RunScope.current();
        if (current != null) {
            return engine.delegate(current, sourceAgentId, targetId, task, streaming);
        }
        SwarmContext context = SwarmContext.current();
        return RunExecution.execute(
                swarm,
                sourceAgentId,
                RunExecution.limits(effective.maxTurns(), effective.maxDelegateDepth()),
                RunExecution.policy(
                        effective.maxRecoveryAttempts(),
                        effective.modelTimeout(),
                        effective.delegateStreaming()),
                context != null ? context : new SwarmContext(),
                () -> engine.delegate(
                        RunScope.current(), sourceAgentId, targetId, task, streaming));
    }

    private static String extractArg(String arguments, String key) {
        if (arguments == null || arguments.isBlank()) {
            throw new SwarmException("Tool call arguments are empty");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(arguments);
        } catch (Exception e) {
            throw new SwarmException("Failed to parse tool call arguments: " + e.getMessage(), e);
        }
        JsonNode field = node.get(key);
        if (field == null) {
            throw new SwarmException("No '" + key + "' field found in tool call arguments");
        }
        if (!field.isTextual()) {
            throw new SwarmException("Field '" + key + "' must be a string, got: " + field.getNodeType());
        }
        return field.textValue();
    }
}
