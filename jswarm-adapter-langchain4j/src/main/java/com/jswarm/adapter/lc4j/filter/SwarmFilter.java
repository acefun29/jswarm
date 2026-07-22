// LangChain4j 路由兼容包装
package com.jswarm.adapter.lc4j.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jswarm.adapter.lc4j.ExternalToolExecutor;
import com.jswarm.adapter.lc4j.runtime.Lc4jRuntimeProvider;
import com.jswarm.adapter.lc4j.run.SwarmRunOptions;
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
import dev.langchain4j.agent.tool.ToolExecutionRequest;

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
                new Lc4jRuntimeProvider(effective, swarmFallback, sink));
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
                throw new SwarmException("Field '" + fieldName + "' must be a string, got: "
                        + field.getNodeType());
            }
            return field.textValue();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new SwarmException("Failed to parse tool call arguments: " + e.getMessage(), e);
        }
    }
}
