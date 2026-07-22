// 从 Swarm 与 options 构建 RunScope
package com.jswarm.spi.run;

import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.spi.context.ContextSnapshot;
import com.jswarm.spi.id.AgentId;
import com.jswarm.spi.id.SwarmVersion;

import java.time.Duration;
import java.util.Objects;

public final class RunScopeFactory {

    private RunScopeFactory() {
    }

    public static RunScope from(Swarm swarm, RunBudgetLimits limits, RunPolicy policy, SwarmContext context) {
        return from(swarm, swarm.entryAgentId(), limits, policy, context);
    }

    public static RunScope from(Swarm swarm, String startAgentId, RunBudgetLimits limits, RunPolicy policy, SwarmContext context) {
        Objects.requireNonNull(swarm, "swarm");
        RunBudgetLimits effectiveLimits = limits != null ? limits : RunBudgetLimits.defaults();
        RunPolicy effectivePolicy = policy != null ? policy : RunPolicy.defaults();
        ContextSnapshot snapshot = context != null
                ? ContextSnapshot.fromMap(context.asMap())
                : ContextSnapshot.empty();

        Duration runTimeout = effectivePolicy.modelTimeout().multipliedBy(effectiveLimits.maxTurns());

        RunRequest request = RunRequest.builder()
                .swarmVersion(SwarmVersion.of(swarm.id()))
                .startAgentId(startAgentId != null ? AgentId.of(startAgentId) : AgentId.of(swarm.entryAgentId()))
                .contextSnapshot(snapshot)
                .policy(effectivePolicy)
                .budget(effectiveLimits.toBudget())
                .runTimeout(runTimeout)
                .build();

        return RunScope.root(request);
    }

    public record RunBudgetLimits(
            int maxTurns,
            int maxModelCalls,
            int maxToolCalls,
            int maxDepth) {

        public RunBudgetLimits {
            if (maxTurns <= 0) throw new IllegalArgumentException("maxTurns must be > 0");
            if (maxModelCalls <= 0) throw new IllegalArgumentException("maxModelCalls must be > 0");
            if (maxToolCalls <= 0) throw new IllegalArgumentException("maxToolCalls must be > 0");
            if (maxDepth <= 0) throw new IllegalArgumentException("maxDepth must be > 0");
        }

        public static RunBudgetLimits defaults() {
            return new RunBudgetLimits(
                    RunDefaults.MAX_TURNS,
                    RunDefaults.MAX_TURNS,
                    Integer.MAX_VALUE,
                    RunDefaults.MAX_DELEGATE_DEPTH);
        }

        public RunBudget toBudget() {
            return RunBudget.builder()
                    .maxTurns(maxTurns)
                    .maxModelCalls(maxModelCalls)
                    .maxToolCalls(maxToolCalls)
                    .maxDepth(maxDepth)
                    .build();
        }
    }
}
