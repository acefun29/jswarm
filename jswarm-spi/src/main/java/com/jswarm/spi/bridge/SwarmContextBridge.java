// SwarmContext 与 RunScope 双向桥接
package com.jswarm.spi.bridge;

import com.jswarm.core.SwarmContext;
import com.jswarm.spi.run.RunScope;

public final class SwarmContextBridge {

    private SwarmContextBridge() {
    }

    public static ScopeBinding bind(RunScope scope) {
        SwarmContext previousContext = SwarmContext.current();
        RunScope previousScope = RunScope.current();
        SwarmContext bridged = scope.toSwarmContext();
        SwarmContext.set(bridged);
        RunScope.bind(scope);
        return new ScopeBinding(previousContext, previousScope);
    }

    public static void restore(ScopeBinding binding) {
        if (binding == null) {
            SwarmContext.clear();
            RunScope.clear();
            return;
        }
        if (binding.previousContext() != null) {
            SwarmContext.set(binding.previousContext());
        } else {
            SwarmContext.clear();
        }
        if (binding.previousScope() != null) {
            RunScope.bind(binding.previousScope());
        } else {
            RunScope.clear();
        }
    }

    public record ScopeBinding(SwarmContext previousContext, RunScope previousScope) {
    }
}
