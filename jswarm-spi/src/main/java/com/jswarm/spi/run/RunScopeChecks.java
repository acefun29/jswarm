// RunScope 运行期检查辅助
package com.jswarm.spi.run;

import com.jswarm.spi.id.AgentId;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class RunScopeChecks {

    private RunScopeChecks() {
    }

    public static void beforeTurn(RunScope scope) {
        if (scope == null) {
            return;
        }
        scope.checkActive();
        scope.budget().consumeOrThrow(BudgetKind.TURN);
    }

    public static void beforeModelCall(RunScope scope) {
        if (scope == null) {
            return;
        }
        scope.checkActive();
        scope.budget().consumeOrThrow(BudgetKind.MODEL_CALL);
    }

    public static void beforeToolCall(RunScope scope) {
        if (scope == null) {
            return;
        }
        scope.checkActive();
        scope.budget().consumeOrThrow(BudgetKind.TOOL_CALL);
    }

    public static void recordToolResultBytes(RunScope scope, String result) {
        if (scope == null || result == null) {
            return;
        }
        long bytes = result.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > 0) {
            scope.budget().consumeOrThrow(BudgetKind.BYTES, bytes);
        }
    }

    public static Duration effectiveModelTimeout(RunScope scope, Duration configured) {
        if (scope == null) {
            return configured != null ? configured : RunDefaults.MODEL_TIMEOUT;
        }
        Duration base = configured != null ? configured : scope.policy().modelTimeout();
        return scope.deadline().effectiveTimeout(base);
    }

    public static RunScope beginDelegate(RunScope parent, String targetAgentId) {
        if (parent == null) {
            return null;
        }
        return parent.child(AgentId.of(targetAgentId));
    }
}
