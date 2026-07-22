package com.jswarm.spi.run;

import com.jswarm.spi.error.SwarmErrorException;
import com.jswarm.spi.id.AgentId;
import com.jswarm.spi.id.SwarmVersion;
import com.jswarm.spi.run.RunScopeFactory.RunBudgetLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunBudgetTest {

    @Test
    void shouldConsumeTurnBudget() {
        RunBudget budget = RunBudget.builder().maxTurns(2).maxModelCalls(10).maxToolCalls(10).maxDepth(3).build();
        budget.consumeOrThrow(BudgetKind.TURN);
        budget.consumeOrThrow(BudgetKind.TURN);
        assertEquals(0, budget.remainingTurns());
        assertThrows(SwarmErrorException.class, () -> budget.consumeOrThrow(BudgetKind.TURN));
    }

    @Test
    void shouldRollbackFailedConsume() {
        RunBudget budget = RunBudget.builder().maxTurns(1).build();
        assertTrue(budget.tryConsume(BudgetKind.TURN));
        assertFalse(budget.tryConsume(BudgetKind.TURN));
        assertEquals(0, budget.remainingTurns());
    }

    @Test
    void shouldShareBudgetAcrossChildScope() {
        RunRequest request = RunRequest.builder()
                .swarmVersion(SwarmVersion.of("s"))
                .startAgentId(AgentId.of("a"))
                .budget(RunBudget.builder().maxTurns(5).maxDepth(2).build())
                .build();
        RunScope parent = RunScope.root(request);
        RunScope child = parent.child(AgentId.of("b"));
        parent.budget().consumeOrThrow(BudgetKind.TURN);
        assertEquals(4, child.budget().remainingTurns());
        assertSame(parent.budget(), child.budget());
    }

    @Test
    void shouldEnforceDepthOnNestedChild() {
        RunRequest request = RunRequest.builder()
                .swarmVersion(SwarmVersion.of("s"))
                .startAgentId(AgentId.of("a"))
                .budget(RunBudget.builder().maxDepth(1).build())
                .build();
        RunScope parent = RunScope.root(request);
        RunScope child = parent.child(AgentId.of("b"));
        assertEquals(1, child.depth());
        assertThrows(SwarmErrorException.class, () -> child.child(AgentId.of("c")));
    }

    @Test
    void factoryLimitsShouldMapToBudget() {
        RunBudgetLimits limits = new RunBudgetLimits(7, 8, 9, 2);
        RunBudget budget = limits.toBudget();
        assertEquals(7, budget.remainingTurns());
        assertEquals(8, budget.remainingModelCalls());
    }
}
