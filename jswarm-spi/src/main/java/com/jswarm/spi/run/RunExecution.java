// 运行入口绑定与错误映射
package com.jswarm.spi.run;

import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.spi.bridge.SwarmContextBridge;
import com.jswarm.spi.error.SwarmErrorMapper;

import java.util.concurrent.Callable;

public final class RunExecution {

    private RunExecution() {
    }

    public static <T> T execute(
            Swarm swarm,
            String startAgentId,
            RunScopeFactory.RunBudgetLimits limits,
            RunPolicy policy,
            SwarmContext context,
            Callable<T> action) {
        RunScope scope = RunScopeFactory.from(swarm, startAgentId, limits, policy, context);
        SwarmContextBridge.ScopeBinding binding = SwarmContextBridge.bind(scope, context);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw SwarmErrorMapper.toRuntimeException(e);
        } catch (Exception e) {
            throw SwarmErrorMapper.toRuntimeException(e);
        } finally {
            scope.markTerminal();
            SwarmContextBridge.restore(binding);
        }
    }

    public static <T> T execute(
            RunScope scope,
            SwarmContext context,
            Callable<T> action) {
        SwarmContext effective = context != null ? context : scope.toSwarmContext();
        SwarmContextBridge.ScopeBinding binding = SwarmContextBridge.bind(scope, effective);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw SwarmErrorMapper.toRuntimeException(e);
        } catch (Exception e) {
            throw SwarmErrorMapper.toRuntimeException(e);
        } finally {
            scope.markTerminal();
            SwarmContextBridge.restore(binding);
        }
    }

    public static RunScopeFactory.RunBudgetLimits limits(int maxTurns, int maxDelegateDepth) {
        return new RunScopeFactory.RunBudgetLimits(maxTurns, maxTurns, Integer.MAX_VALUE, maxDelegateDepth);
    }

    public static RunPolicy policy(int maxRecoveryAttempts, java.time.Duration modelTimeout, boolean delegateStreaming) {
        return new RunPolicy(maxRecoveryAttempts, modelTimeout, delegateStreaming);
    }
}
