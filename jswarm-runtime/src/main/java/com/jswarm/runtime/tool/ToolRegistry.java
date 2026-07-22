// 共享工具注册、授权与幂等执行
package com.jswarm.runtime.tool;

import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.lifecycle.ToolContext;
import com.jswarm.spi.lifecycle.ToolInvocation;
import com.jswarm.spi.lifecycle.ToolInvoker;
import com.jswarm.spi.lifecycle.ToolResult;
import com.jswarm.spi.message.ToolDescriptor;
import com.jswarm.spi.message.ToolSideEffect;
import com.jswarm.spi.run.RunScope;
import com.jswarm.spi.run.RunScopeChecks;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

public final class ToolRegistry {

    private final String agentId;
    private final Map<String, ToolDescriptor> descriptors;
    private final ToolInvoker invoker;
    private final Map<String, CachedResult> idempotentResults = new ConcurrentHashMap<>();

    private ToolRegistry(String agentId, List<ToolDescriptor> tools, ToolInvoker invoker) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        this.agentId = agentId;
        if (tools == null) {
            tools = List.of();
        }
        if (!tools.isEmpty() && invoker == null) {
            throw new IllegalArgumentException("tool invoker is required when tools are registered");
        }
        Map<String, ToolDescriptor> values = new LinkedHashMap<>();
        for (ToolDescriptor descriptor : tools) {
            Objects.requireNonNull(descriptor, "tool descriptor");
            if (OrchestrationTools.routing(descriptor.name())) {
                throw new IllegalArgumentException("reserved orchestration tool name: " + descriptor.name());
            }
            if (values.putIfAbsent(descriptor.name(), descriptor) != null) {
                throw new IllegalArgumentException("duplicate tool name: " + descriptor.name());
            }
        }
        this.descriptors = Map.copyOf(values);
        this.invoker = invoker;
    }

    public static ToolRegistry build(String agentId, List<ToolDescriptor> tools, ToolInvoker invoker) {
        return new ToolRegistry(agentId, tools, invoker);
    }

    public String agentId() {
        return agentId;
    }

    public List<ToolDescriptor> descriptors() {
        return List.copyOf(descriptors.values());
    }

    public ToolDescriptor descriptor(String toolName) {
        return descriptors.get(toolName);
    }

    public ToolResult invoke(ToolInvocation invocation, ToolContext context) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(context, "context");
        ToolDescriptor descriptor = descriptors.get(invocation.toolName());
        if (descriptor == null) {
            return failure(SwarmErrorCode.TOOL_POLICY_DENIED, "Tool is not registered");
        }
        RunScope scope = Objects.requireNonNull(context.scope(), "context.scope");
        if (!authorized(descriptor, scope)) {
            return failure(SwarmErrorCode.TOOL_POLICY_DENIED, "Tool invocation is not authorized");
        }
        if (descriptor.confirmationRequired() && !invocation.confirmed()) {
            return failure(SwarmErrorCode.TOOL_POLICY_DENIED, "Tool confirmation is required");
        }
        try {
            if (scope.deadline().effectiveTimeout(descriptor.timeout()).isZero()) {
                return failure(SwarmErrorCode.MODEL_TIMEOUT, "Tool deadline exceeded");
            }
            RunScopeChecks.beforeToolCall(scope);
            String key = invocation.idempotencyKey();
            if (descriptor.sideEffect() != ToolSideEffect.READ_ONLY && (key == null || key.isBlank())) {
                return failure(SwarmErrorCode.TOOL_IDEMPOTENCY_CONFLICT,
                        "Side-effecting tool requires an idempotency key");
            }
            String cacheKey = descriptor.name() + "|" + descriptor.version() + "|" + key;
            if (descriptor.sideEffect() != ToolSideEffect.READ_ONLY) {
                CachedResult cached = idempotentResults.get(cacheKey);
                if (cached != null && cached.arguments().equals(invocation.arguments())) {
                    return withMetadata(cached.result(), "idempotentReplay", true);
                }
                if (cached != null) {
                    return failure(SwarmErrorCode.TOOL_IDEMPOTENCY_CONFLICT,
                            "Idempotency key was used with different arguments");
                }
            }
            ToolContext effectiveContext = new ToolContext(
                    scope,
                    com.jswarm.spi.time.Deadline.at(
                            Instant.now().plus(scope.deadline().effectiveTimeout(descriptor.timeout()))),
                    scope.cancellation());
            ToolResult result = invoker.execute(invocation, effectiveContext);
            if (result == null) {
                result = failure(SwarmErrorCode.TOOL_FAILURE, "Tool returned null");
            }
            result = enforceResultSize(descriptor, result);
            if (descriptor.sideEffect() != ToolSideEffect.READ_ONLY && result.successful()) {
                idempotentResults.putIfAbsent(cacheKey, new CachedResult(invocation.arguments(), result));
            }
            return result;
        } catch (RuntimeException failure) {
            return new ToolResult("Tool execution failed", SwarmErrorCode.TOOL_FAILURE,
                    Map.of("status", "failed"));
        }
    }

    private boolean authorized(ToolDescriptor descriptor, RunScope scope) {
        String currentAgent = scope.currentAgentId().map(Object::toString).orElse(agentId);
        if (!descriptor.allowedAgentIds().isEmpty() && !descriptor.allowedAgentIds().contains(currentAgent)) {
            return false;
        }
        if (!descriptor.allowedTenantIds().isEmpty()) {
            Object tenant = scope.contextSnapshot().get("tenant_id");
            if (tenant == null || !descriptor.allowedTenantIds().contains(String.valueOf(tenant))) {
                return false;
            }
        }
        if (!descriptor.allowedPrincipals().isEmpty()) {
            Object principal = scope.contextSnapshot().get("principal_id");
            if (principal == null || !descriptor.allowedPrincipals().contains(String.valueOf(principal))) {
                return false;
            }
        }
        return true;
    }

    private static ToolResult enforceResultSize(ToolDescriptor descriptor, ToolResult result) {
        int bytes = result.output().getBytes(StandardCharsets.UTF_8).length;
        if (bytes <= descriptor.maxResultBytes()) {
            return result;
        }
        return new ToolResult("", SwarmErrorCode.TOOL_RESULT_TOO_LARGE,
                Map.of("maxResultBytes", descriptor.maxResultBytes(), "actualBytes", bytes,
                        "truncated", true));
    }

    private static ToolResult withMetadata(ToolResult result, String key, Object value) {
        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata());
        metadata.put(key, value);
        return new ToolResult(result.output(), result.errorCode(), metadata);
    }

    private static ToolResult failure(SwarmErrorCode code, String message) {
        return new ToolResult(message, code, Map.of("status", "rejected"));
    }

    private record CachedResult(String arguments, ToolResult result) {
    }
}
