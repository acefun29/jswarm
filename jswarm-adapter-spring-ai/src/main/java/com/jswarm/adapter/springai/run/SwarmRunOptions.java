package com.jswarm.adapter.springai.run;

import com.jswarm.spi.run.RunDefaults;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;

public record SwarmRunOptions(
        int maxTurns,
        int maxRecoveryAttempts,
        int maxDelegateDepth,
        Duration modelTimeout,
        boolean delegateStreaming,
        SwarmRunListener listener,
        List<Advisor> advisors,
        ToolExecutionExceptionProcessor exceptionProcessor) {

    public static SwarmRunOptions defaults() {
        return new SwarmRunOptions(10, 2, 3, RunDefaults.MODEL_TIMEOUT, true, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxTurns = 10;
        private int maxRecoveryAttempts = 2;
        private int maxDelegateDepth = 3;
        private Duration modelTimeout = RunDefaults.MODEL_TIMEOUT;
        private boolean delegateStreaming = true;
        private SwarmRunListener listener;
        private List<Advisor> advisors;
        private ToolExecutionExceptionProcessor exceptionProcessor;

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        public Builder maxRecoveryAttempts(int maxRecoveryAttempts) {
            this.maxRecoveryAttempts = maxRecoveryAttempts;
            return this;
        }

        public Builder maxDelegateDepth(int maxDelegateDepth) {
            this.maxDelegateDepth = maxDelegateDepth;
            return this;
        }

        public Builder modelTimeout(Duration modelTimeout) {
            this.modelTimeout = modelTimeout;
            return this;
        }

        public Builder delegateStreaming(boolean delegateStreaming) {
            this.delegateStreaming = delegateStreaming;
            return this;
        }

        public Builder listener(SwarmRunListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder advisors(List<Advisor> advisors) {
            this.advisors = advisors;
            return this;
        }

        public Builder exceptionProcessor(ToolExecutionExceptionProcessor exceptionProcessor) {
            this.exceptionProcessor = exceptionProcessor;
            return this;
        }

        public SwarmRunOptions build() {
            if (maxTurns <= 0) throw new IllegalArgumentException("maxTurns must be positive, got: " + maxTurns);
            if (maxRecoveryAttempts < 0) throw new IllegalArgumentException("maxRecoveryAttempts must be >= 0, got: " + maxRecoveryAttempts);
            if (maxDelegateDepth <= 0) throw new IllegalArgumentException("maxDelegateDepth must be positive, got: " + maxDelegateDepth);
            Duration timeout = modelTimeout != null ? modelTimeout : RunDefaults.MODEL_TIMEOUT;
            return new SwarmRunOptions(maxTurns, maxRecoveryAttempts, maxDelegateDepth, timeout, delegateStreaming, listener, advisors, exceptionProcessor);
        }
    }
}
