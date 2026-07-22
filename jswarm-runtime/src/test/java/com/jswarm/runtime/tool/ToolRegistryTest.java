// 工具注册与副作用治理测试
package com.jswarm.runtime.tool;

import com.jswarm.spi.context.ContextSnapshot;
import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.id.AgentId;
import com.jswarm.spi.id.SwarmVersion;
import com.jswarm.spi.lifecycle.ToolContext;
import com.jswarm.spi.lifecycle.ToolInvocation;
import com.jswarm.spi.lifecycle.ToolResult;
import com.jswarm.spi.message.ToolDescriptor;
import com.jswarm.spi.message.ToolSensitivity;
import com.jswarm.spi.message.ToolSideEffect;
import com.jswarm.spi.run.RunBudget;
import com.jswarm.spi.run.RunRequest;
import com.jswarm.spi.run.RunScope;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolRegistryTest {

    @Test
    void shouldRejectDuplicateAndReservedNames() {
        ToolDescriptor first = descriptor("lookup");
        assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.build("agent", List.of(first, descriptor("lookup")), (invocation, context) -> new ToolResult("ok")));
        assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.build("agent", List.of(descriptor("handoff")), (invocation, context) -> new ToolResult("ok")));
    }

    @Test
    void shouldRejectMissingInvokerForRegisteredTools() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolRegistry.build("agent", List.of(descriptor("lookup")), null));
    }

    @Test
    void shouldEnforceTenantAndAgentAuthorization() {
        ToolDescriptor restricted = new ToolDescriptor(
                "lookup", "", "{}", "{}", Set.of("agent"), Set.of("tenant-a"), Set.of(),
                ToolSensitivity.SENSITIVE, Duration.ofSeconds(1), 100, "", ToolSideEffect.READ_ONLY, false);
        ToolRegistry registry = ToolRegistry.build("agent", List.of(restricted), (invocation, context) -> new ToolResult("ok"));
        RunScope scope = scope(ContextSnapshot.fromMap(Map.of("tenant_id", "tenant-b")));

        ToolResult result = registry.invoke(new ToolInvocation("c1", "lookup", "{}"), context(scope));

        assertEquals(SwarmErrorCode.TOOL_POLICY_DENIED, result.errorCode());
    }

    @Test
    void shouldReplaySideEffectOnlyOnceForSameKey() {
        AtomicInteger calls = new AtomicInteger();
        ToolDescriptor sideEffect = new ToolDescriptor(
                "charge", "", "{}", "{}", Set.of(), Set.of(), Set.of(), ToolSensitivity.SENSITIVE,
                Duration.ofSeconds(1), 100, "request_id", ToolSideEffect.SIDE_EFFECTING, false);
        ToolRegistry registry = ToolRegistry.build("agent", List.of(sideEffect), (invocation, context) -> {
            calls.incrementAndGet();
            return new ToolResult("charged");
        });
        RunScope scope = scope(ContextSnapshot.empty());
        ToolInvocation invocation = new ToolInvocation("c1", "charge", "{}", "request-1");

        ToolResult first = registry.invoke(invocation, context(scope));
        ToolResult replay = registry.invoke(invocation, context(scope));

        assertEquals(1, calls.get());
        assertEquals("charged", first.output());
        assertEquals(true, replay.metadata().get("idempotentReplay"));
    }

    @Test
    void shouldRejectDifferentArgumentsForSameIdempotencyKey() {
        ToolDescriptor sideEffect = new ToolDescriptor(
                "charge", "", "{}", "{}", Set.of(), Set.of(), Set.of(), ToolSensitivity.SENSITIVE,
                Duration.ofSeconds(1), 100, "request_id", ToolSideEffect.SIDE_EFFECTING, false);
        ToolRegistry registry = ToolRegistry.build("agent", List.of(sideEffect), (invocation, context) -> new ToolResult("charged"));
        RunScope scope = scope(ContextSnapshot.empty());
        registry.invoke(new ToolInvocation("c1", "charge", "{\"amount\":1}", "request-1"), context(scope));

        ToolResult conflict = registry.invoke(
                new ToolInvocation("c2", "charge", "{\"amount\":2}", "request-1"), context(scope));

        assertEquals(SwarmErrorCode.TOOL_IDEMPOTENCY_CONFLICT, conflict.errorCode());
    }

    @Test
    void shouldRejectOversizedResults() {
        ToolDescriptor descriptor = new ToolDescriptor(
                "lookup", "", "{}", "{}", Set.of(), Set.of(), Set.of(), ToolSensitivity.PUBLIC,
                Duration.ofSeconds(1), 3, "", ToolSideEffect.READ_ONLY, false);
        ToolRegistry registry = ToolRegistry.build("agent", List.of(descriptor), (invocation, context) -> new ToolResult("long"));

        ToolResult result = registry.invoke(
                new ToolInvocation("c1", "lookup", "{}"), context(scope(ContextSnapshot.empty())));

        assertEquals(SwarmErrorCode.TOOL_RESULT_TOO_LARGE, result.errorCode());
    }

    @Test
    void shouldRequireConfirmationForMarkedTools() {
        ToolDescriptor descriptor = new ToolDescriptor(
                "delete", "", "{}", "{}", Set.of(), Set.of(), Set.of(), ToolSensitivity.SENSITIVE,
                Duration.ofSeconds(1), 100, "request_id", ToolSideEffect.SIDE_EFFECTING, true);
        ToolRegistry registry = ToolRegistry.build("agent", List.of(descriptor),
                (invocation, context) -> new ToolResult("deleted"));
        RunScope scope = scope(ContextSnapshot.empty());

        ToolResult denied = registry.invoke(
                new ToolInvocation("c1", "delete", "{}", "request-1"), context(scope));
        ToolResult allowed = registry.invoke(
                new ToolInvocation("c2", "delete", "{}", "request-2", true), context(scope));

        assertEquals(SwarmErrorCode.TOOL_POLICY_DENIED, denied.errorCode());
        assertEquals(null, allowed.errorCode());
    }

    @Test
    void shouldRejectExpiredToolDeadlineBeforeInvocation() {
        ToolDescriptor descriptor = new ToolDescriptor(
                "lookup", "", "{}", "{}", Set.of(), Set.of(), Set.of(), ToolSensitivity.PUBLIC,
                Duration.ofSeconds(1), 100, "", ToolSideEffect.READ_ONLY, false);
        ToolRegistry registry = ToolRegistry.build("agent", List.of(descriptor),
                (invocation, context) -> new ToolResult("ok"));
        RunScope scope = RunScope.root(RunRequest.builder()
                .swarmVersion(SwarmVersion.of("s"))
                .startAgentId(AgentId.of("agent"))
                .contextSnapshot(ContextSnapshot.empty())
                .budget(RunBudget.defaults())
                .runTimeout(Duration.ofMillis(1))
                .build());
        try {
            Thread.sleep(5);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        ToolResult result = registry.invoke(
                new ToolInvocation("c1", "lookup", "{}"), context(scope));

        assertEquals(SwarmErrorCode.MODEL_TIMEOUT, result.errorCode());
    }

    private static ToolDescriptor descriptor(String name) {
        return new ToolDescriptor(name, "", "{}");
    }

    private static ToolContext context(RunScope scope) {
        return new ToolContext(scope, scope.deadline(), scope.cancellation());
    }

    private static RunScope scope(ContextSnapshot snapshot) {
        return RunScope.root(RunRequest.builder()
                .swarmVersion(SwarmVersion.of("s"))
                .startAgentId(AgentId.of("agent"))
                .contextSnapshot(snapshot)
                .budget(RunBudget.defaults())
                .runTimeout(Duration.ofMinutes(1))
                .build());
    }
}
