// 运行策略占位，授权由 core RouteAuthorization 执行
package com.jswarm.spi.run;

import java.time.Duration;

public record RunPolicy(
        int maxRecoveryAttempts,
        Duration modelTimeout,
        boolean delegateStreaming) {

    public RunPolicy {
        if (maxRecoveryAttempts < 0) {
            throw new IllegalArgumentException("maxRecoveryAttempts must be >= 0");
        }
        if (modelTimeout == null) {
            modelTimeout = RunDefaults.MODEL_TIMEOUT;
        }
        if (modelTimeout.isNegative() || modelTimeout.isZero()) {
            throw new IllegalArgumentException("modelTimeout must be positive");
        }
    }

    public static RunPolicy defaults() {
        return new RunPolicy(RunDefaults.MAX_RECOVERY_ATTEMPTS, RunDefaults.MODEL_TIMEOUT, true);
    }
}
