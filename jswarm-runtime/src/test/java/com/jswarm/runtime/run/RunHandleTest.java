// RunHandle 取消与事件快照测试
package com.jswarm.runtime.run;

import com.jswarm.spi.context.ContextSnapshot;
import com.jswarm.spi.id.AgentId;
import com.jswarm.spi.id.SwarmVersion;
import com.jswarm.spi.run.RunBudget;
import com.jswarm.spi.run.RunRequest;
import com.jswarm.spi.run.RunScope;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RunHandleTest {

    @Test
    void shouldCancelScopeWithoutCancellingFutureContract() {
        RunScope scope = RunScope.root(RunRequest.builder()
                .swarmVersion(SwarmVersion.of("s"))
                .startAgentId(AgentId.of("a"))
                .contextSnapshot(ContextSnapshot.empty())
                .budget(RunBudget.defaults())
                .runTimeout(Duration.ofMinutes(1))
                .build());
        RunHandle handle = new RunHandle(scope, new CompletableFuture<>());

        assertTrue(handle.cancel());
        assertTrue(handle.cancelled());
    }
}
