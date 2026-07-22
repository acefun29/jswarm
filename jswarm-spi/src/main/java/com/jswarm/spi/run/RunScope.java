// 不可变运行作用域
package com.jswarm.spi.run;

import com.jswarm.core.SwarmContext;
import com.jswarm.spi.context.ContextProjection;
import com.jswarm.spi.context.ContextSnapshot;
import com.jswarm.spi.id.AgentId;
import com.jswarm.spi.id.ParentRunId;
import com.jswarm.spi.id.RunId;
import com.jswarm.spi.id.SwarmVersion;
import com.jswarm.spi.time.CancellationToken;
import com.jswarm.spi.time.Deadline;
import com.jswarm.spi.time.TerminalGuard;

import java.util.Objects;
import java.util.Optional;

public final class RunScope {

    private static final ThreadLocal<RunScope> CURRENT = new ThreadLocal<>();

    private final RunId runId;
    private final ParentRunId parentRunId;
    private final SwarmVersion swarmVersion;
    private final AgentId currentAgentId;
    private final int depth;
    private final Deadline deadline;
    private final CancellationToken cancellation;
    private final RunBudget budget;
    private final ContextSnapshot contextSnapshot;
    private final RunPolicy policy;
    private final TerminalGuard terminalGuard;
    private final String traceId;

    private RunScope(
            RunId runId,
            ParentRunId parentRunId,
            SwarmVersion swarmVersion,
            AgentId currentAgentId,
            int depth,
            Deadline deadline,
            CancellationToken cancellation,
            RunBudget budget,
            ContextSnapshot contextSnapshot,
            RunPolicy policy,
            TerminalGuard terminalGuard,
            String traceId) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.parentRunId = parentRunId != null ? parentRunId : ParentRunId.ROOT;
        this.swarmVersion = Objects.requireNonNull(swarmVersion, "swarmVersion");
        this.currentAgentId = currentAgentId;
        this.depth = depth;
        this.deadline = Objects.requireNonNull(deadline, "deadline");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.contextSnapshot = contextSnapshot != null ? contextSnapshot : ContextSnapshot.empty();
        this.policy = policy != null ? policy : RunPolicy.defaults();
        this.terminalGuard = terminalGuard != null ? terminalGuard : new TerminalGuard();
        this.traceId = traceId;
    }

    public static RunScope root(RunRequest request) {
        Objects.requireNonNull(request, "request");
        RunId runId = RunId.generate();
        Deadline deadline = Deadline.fromNow(request.runTimeout());
        return new RunScope(
                runId,
                ParentRunId.ROOT,
                request.swarmVersion(),
                request.startAgentId(),
                0,
                deadline,
                new CancellationToken(),
                request.budget(),
                request.contextSnapshot(),
                request.policy(),
                new TerminalGuard(),
                null);
    }

    public RunScope child(AgentId delegateAgentId) {
        budget.consumeOrThrow(BudgetKind.DEPTH);
        ContextSnapshot projected = ContextProjection.forDelegate(contextSnapshot);
        return new RunScope(
                RunId.generate(),
                ParentRunId.of(runId),
                swarmVersion,
                delegateAgentId,
                depth + 1,
                deadline,
                cancellation,
                budget,
                projected,
                policy,
                terminalGuard,
                traceId);
    }

    public RunScope withAgent(AgentId agentId) {
        return new RunScope(
                runId,
                parentRunId,
                swarmVersion,
                agentId,
                depth,
                deadline,
                cancellation,
                budget,
                contextSnapshot,
                policy,
                terminalGuard,
                traceId);
    }

    public RunScope withContextOverlay(ContextSnapshot overlay) {
        return new RunScope(
                runId,
                parentRunId,
                swarmVersion,
                currentAgentId,
                depth,
                deadline,
                cancellation,
                budget,
                contextSnapshot.withOverlay(overlay.asMap()),
                policy,
                terminalGuard,
                traceId);
    }

    public static RunScope current() {
        return CURRENT.get();
    }

    public static void bind(RunScope scope) {
        if (scope == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(scope);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    public SwarmContext toSwarmContext() {
        SwarmContext ctx = new SwarmContext(contextSnapshot.asMap());
        return ctx;
    }

    public void checkActive() {
        terminalGuard.checkActive();
        cancellation.throwIfCancelled();
        deadline.check();
    }

    public void markTerminal() {
        terminalGuard.markTerminal();
    }

    public RunId runId() {
        return runId;
    }

    public ParentRunId parentRunId() {
        return parentRunId;
    }

    public SwarmVersion swarmVersion() {
        return swarmVersion;
    }

    public Optional<AgentId> currentAgentId() {
        return Optional.ofNullable(currentAgentId);
    }

    public int depth() {
        return depth;
    }

    public Deadline deadline() {
        return deadline;
    }

    public CancellationToken cancellation() {
        return cancellation;
    }

    public RunBudget budget() {
        return budget;
    }

    public ContextSnapshot contextSnapshot() {
        return contextSnapshot;
    }

    public RunPolicy policy() {
        return policy;
    }

    public TerminalGuard terminalGuard() {
        return terminalGuard;
    }

    public Optional<String> traceId() {
        return Optional.ofNullable(traceId);
    }
}
